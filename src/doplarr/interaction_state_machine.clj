(ns doplarr.interaction-state-machine
  (:require
   [config.core :refer [env]]
   [doplarr.discord :as discord]
   [taoensso.timbre :refer [info fatal]]
   [clojure.core.async :as a]
   [com.rpl.specter :as s]
   [fmnoise.flow :refer [then else]]
   [discljord.messaging :as m]
   [clojure.string :as str]
   [doplarr.state :as state]
   [doplarr.utils :as utils]))

(def channel-timeout 600000)

(defn start-interaction! [interaction]
  (a/go
    (let [uuid (str (java.util.UUID/randomUUID))
          id (:id interaction)
          token (:token interaction)
          payload-opts (:options (:payload interaction))
          media-type (first (keys payload-opts))
          query (s/select-one [media-type :query] payload-opts)
          {:keys [messaging bot-id]} @state/discord]
                                        ; Send the ack for delayed response
      (->> @(m/create-interaction-response! messaging id token 5 :data {:ephemeral? true})
           (else #(fatal % "Error in interaction ack")))
                                        ; Search for results
      (info "Performing search for" (name media-type) query)
      (let [results (->> (a/<! ((utils/media-fn media-type "search") query))
                         (take (:max-results env 10))
                         (into []))]
                                        ; Setup ttl cache entry
        (swap! state/cache assoc uuid {:results results
                                       :media-type media-type
                                       :token token
                                       :last-modified (System/currentTimeMillis)})
                                        ; Create dropdown for search results
        (->> @(m/edit-original-interaction-response! messaging bot-id token (discord/search-response results uuid))
             (else #(fatal % "Error in creating search responses")))))))

(defn query-for-option-or-request [pending-opts uuid]
  (a/go
    (let [{:keys [messaging bot-id]} @state/discord
          {:keys [media-type token payload]} (get @state/cache uuid)]
      (if (empty? pending-opts)
        (let [embed (a/<! ((utils/media-fn media-type "request-embed") payload))]
          (->> @(m/edit-original-interaction-response! messaging bot-id token (discord/request embed uuid))
               (else #(fatal % "Error in sending request embed"))))
        (let [[op options] (first pending-opts)]
          (->> @(m/edit-original-interaction-response! messaging bot-id token (discord/option-dropdown op options uuid))
               (else #(fatal % "Error in creating option dropdown"))))))))

(defmulti process-event! (fn [event _ _ _] event))

(defmethod process-event! "result-select" [_ interaction uuid _]
  (a/go
    (let [{:keys [results media-type]} (get @state/cache uuid)
          result (nth results (discord/dropdown-result interaction))
          add-opts (a/<! ((utils/media-fn media-type "additional-options") result))
          pending-opts (->> add-opts
                            (filter #(seq? (second %)))
                            (into {}))
          ready-opts (apply (partial dissoc add-opts) (keys pending-opts))]
                                        ; Start setting up the payload
      (swap! state/cache assoc-in [uuid :payload] result)
      (swap! state/cache assoc-in [uuid :pending-opts] pending-opts)
                                        ; Merge in the opts that are already satisfied
      (swap! state/cache update-in [uuid :payload] merge ready-opts)
      (query-for-option-or-request pending-opts uuid))))

(defmethod process-event! "option-select" [_ interaction uuid option]
  (let [selection (discord/dropdown-result interaction)
        cache-val (swap! state/cache update-in [uuid :pending-opts] #(dissoc % (keyword option)))]
    (swap! state/cache assoc-in [uuid :payload (keyword option)] selection)
    (query-for-option-or-request (get-in cache-val [uuid :pending-opts]) uuid)))

(defmethod process-event! "request" [_ _ uuid format]
  (let [{:keys [messaging bot-id]} @state/discord
        {:keys [payload media-type token]} (get @state/cache uuid)]
    (letfn [(msg-resp [msg] (->> @(m/edit-original-interaction-response! messaging bot-id token (discord/content-response msg))
                                 (else #(fatal % "Error in message response"))))]
      (->>  (a/<!! ((utils/media-fn media-type "request")
                    (assoc payload :format (keyword format))))
            (then (fn [status]
                    (case status
                      :unauthorized (msg-resp "You do not have an associated account in the request backend")
                      :pending (msg-resp "This has already been requested and the request is pending")
                      :processing (msg-resp "This is currently processing and should be available soon!")
                      :available (msg-resp "This selection is already available!")
                      (msg-resp "Request performed!"))))
            (else (fn [e]
                    (let [{:keys [status body] :as data} (ex-data e)]
                      (if (= status 403)
                        (->> @(m/edit-original-interaction-response! messaging bot-id token (discord/content-response (body "message")))
                             (else #(fatal % "Error in sending request failure response")))
                        (->> @(m/edit-original-interaction-response! messaging bot-id token (discord/content-response "Unspecified error on request, check logs"))
                             (then #(fatal data "Non 403 error on request"))
                             (else #(fatal % "Error in sending error response")))))))))))

(defn continue-interaction! [interaction]
  (let [[event uuid option] (str/split (s/select-one [:payload :component-id] interaction) #":")
        now (System/currentTimeMillis)
        {:keys [token id]} interaction
        {:keys [messaging bot-id]} @state/discord]
    ; Send the ack
    (->> @(m/create-interaction-response! messaging id token 6)
         (else #(fatal % "Error sending response ack")))
    ; Check last modified
    (let [{:keys [token last-modified]} (get @state/cache uuid)]
      (if (> (- now last-modified) channel-timeout)
        ; Update interaction with timeout message
        (->> @(m/edit-original-interaction-response! messaging bot-id token (discord/content-response "Request timed out, please try again"))
             (else #(fatal % "Error in sending timeout response")))
        ; Move through the state machine to update cache side effecting new components
        (do
          (swap! state/cache assoc-in [uuid :last-modified] now)
          (process-event! event interaction uuid option))))))
