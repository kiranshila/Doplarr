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

(defn query-for-option-or-request [pending-opts system uuid]
  (a/go
    (let [{:doplarr/keys [cache backends]} system
          {:discord/keys [messaging bot-id]} system
          {:keys [media-type token payload]} (get @cache uuid)
          bot-id @bot-id]
      (if (empty? pending-opts)
        (let [embed (a/<! ((get-in backends [media-type :request-embed]) payload))]
          @(m/edit-original-interaction-response! messaging bot-id token (discord/request embed uuid)))
        (let [[op options] (first pending-opts)]
          @(m/edit-original-interaction-response! messaging bot-id token (discord/option-dropdown op options uuid)))))))

(defmulti process-event! (fn [event _ _ _ _] event))

(defmethod process-event! "result-select" [_ interaction uuid system _]
  (a/go
    (let [{:doplarr/keys [cache backends]} system
          {:keys [results media-type]} (get @cache uuid)
          result (nth results (discord/dropdown-result interaction))
          add-opts (a/<! ((get-in backends [media-type :additional-options]) result))
          pending-opts (->> add-opts
                            (filter #(seq? (second %)))
                            (into {}))
          ready-opts (apply (partial dissoc add-opts) (keys pending-opts))]
                                        ; Start setting up the payload
      (swap! cache assoc-in [uuid :payload] result)
      (swap! cache assoc-in [uuid :pending-opts] pending-opts)
                                        ; Merge in the opts that are already satisfied
      (swap! cache update-in [uuid :payload] merge ready-opts)
      (query-for-option-or-request pending-opts system uuid))))

(defmethod process-event! "option-select" [_ interaction uuid system option]
  (let [{:doplarr/keys [cache]} system
        selection (discord/dropdown-result interaction)
        cache-val (swap! cache update-in [uuid :pending-opts] #(dissoc % (keyword option)))]
    (swap! cache assoc-in [uuid :payload (keyword option)] selection)
    (query-for-option-or-request (get-in cache-val [uuid :pending-opts]) system uuid)))

(defn continue-interaction! [system interaction]
  (let [[event uuid option] (str/split (s/select-one [:payload :component-id] interaction) #":")
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
          (process-event! event interaction uuid system option))))))
