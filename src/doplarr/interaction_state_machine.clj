(ns doplarr.interaction-state-machine
  (:require
   [config.core :refer [env]]
   [doplarr.discord :as discord]
   [clojure.core.async :as a]
   [com.rpl.specter :as s]
   [discljord.messaging :as m]
   [clojure.string :as str]))

(def channel-timeout 600000)

(defn start-interaction! [system interaction]
  (a/go
    (let [uuid (str (java.util.UUID/randomUUID))
          id (:id interaction)
          token (:token interaction)
          payload-opts (:options (:payload interaction))
          media-type (first (keys payload-opts))
          query (s/select-one [media-type :query] payload-opts)
          {:discord/keys [messaging bot-id]} system
          bot-id @bot-id]
                                        ; Send the ack for delayed response
      @(m/create-interaction-response! messaging id token 5 :data {:ephemeral? true})
                                        ; Search for results
      (let [results (->> (a/<! ((get-in system [:doplarr/backends media-type :search]) query))
                         (take (:max-results env 10))
                         (into []))]
                                        ; Setup ttl cache entry
        (swap! (:doplarr/cache system) assoc uuid {:results results
                                                   :media-type media-type
                                                   :token token
                                                   :last-modified (System/currentTimeMillis)})
                                        ; Create dropdown for search results
        @(m/edit-original-interaction-response! messaging bot-id token (discord/search-response results uuid))))))

(defmulti process-event! (fn [event _ _ _] event))

(defmethod process-event! "result-select" [_ interaction uuid system]
  (a/go
    (let [{:doplarr/keys [cache backends]} system
          {:discord/keys [messaging bot-id]} system
          bot-id @bot-id
          {:keys [results media-type token]} (get @cache uuid)
          result (nth results (discord/dropdown-result interaction))
          add-opts (a/<! ((get-in backends [media-type :additional-options]) result))
          pending-opts (->> add-opts
                            (filter #(vector? (second %)))
                            (into {}))
          ready-opts (apply (partial dissoc add-opts) (keys pending-opts))]
                                        ; Start setting up the payload
      (swap! cache assoc-in [uuid :payload] result)
                                        ; Merge in the opts that are already satisfied
      (swap! cache update-in [uuid :payload] merge ready-opts)
      (if (empty? pending-opts)
        nil ; Go to final request screen
        nil ; Query user for all pending options
        ))))
(defn continue-interaction! [system interaction]
  (let [[event uuid] (str/split (s/select-one [:payload :component-id] interaction) #":")
        now (System/currentTimeMillis)
        {:keys [token id]} interaction
        {:discord/keys [messaging bot-id]} system
        {:doplarr/keys [cache]} system
        bot-id @bot-id]
    ; Send the ack
    @(m/create-interaction-response! messaging id token 6)
    ; Check last modified
    (let [{:keys [token last-modified]} (get @cache uuid)]
      (if (> (- now last-modified) channel-timeout)
        ; Update interaction with timeout message
        @(m/edit-original-interaction-response! messaging bot-id token (discord/content-response "Request timed out, please try again"))
        ; Move through the state machine to update cache side effecting new components
        (do
          (swap! cache assoc-in [uuid :last-modified] now)
          (process-event! event interaction uuid system))))))
