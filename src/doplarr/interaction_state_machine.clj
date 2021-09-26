(ns doplarr.interaction-state-machine
  (:require
   [config.core :refer [env]]
   [doplarr.discord :as discord]
   [clojure.core.async :as a]
   [com.rpl.specter :as s]
   [clojure.string :as str]))

(defn start-interaction [interaction]
  (let [uuid (str (java.util.UUID/randomUUID))
        id (:id interaction)
        token (:token interaction)
        payload-opts (:options (:payload interaction))
        request-type (first (keys payload-opts))
        query (s/select-one [request-type :term] payload-opts)]
    (discord/interaction-response id token 5 :ephemeral? true)
    (a/go
      (let [results (->> (a/<! (((search-fn @backend) request-type) query))
                         (into [] (take @discord/max-results)))]
        (swap! discord/cache assoc uuid {:results results
                                         :request-type request-type
                                         :token token
                                         :last-modified (System/currentTimeMillis)})
        (discord/update-interaction-response token (discord/search-response results uuid))))))

(defn continue-interaction [interaction]
  (let [[event uuid] (str/split (s/select-one [:payload :component-id] interaction) #":")
        now (System/currentTimeMillis)]
    ; Send the ack
    (discord/interaction-response (:id interaction) (:token interaction) 6)
    ; Check last modified
    (let [{:keys [token last-modified]} (get @discord/cache uuid)]
      (if (> (- now last-modified) discord/channel-timeout)
        ; Update interaction with timeout message
        (discord/update-interaction-response token (discord/content-response "Request timed out, please try again."))
        ; Move through the state machine to update cache side effecting new components
        (do
          (swap! discord/cache assoc-in [uuid :last-modified] now)
          (process-event event interaction uuid))))))
