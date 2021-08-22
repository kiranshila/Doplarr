(ns doplarr.core
  (:require
   [doplarr.discord :as discord]
   [doplarr.config :as config]
   [doplarr.direct :as direct]
   [doplarr.proxied :as proxied]
   [discljord.messaging :as m]
   [discljord.connections :as c]
   [discljord.events :as e]
   [config.core :refer [env]]
   [clojure.core.async :as a])
  (:gen-class))

(def backend (delay (config/backend)))

(def backend-request {:direct #'doplarr.direct/make-request
                      :proxied #'doplarr.proxied/make-request})

;;;;;;;;;;;;;;;;;;;;;;;; Gateway event handlers
(defmulti handle-event
  (fn [event-type _]
    event-type))

(defmethod handle-event :interaction-create
  [_ data]
  (let [interaction (discord/interaction-data data)]
    (case (:type interaction)
      :application-command ((backend-request @backend) interaction) ; These will all be requests as that is the only top level command
      :message-component (discord/continue-request interaction))))

(defmethod handle-event :ready
  [_ {{id :id} :user}]
  (swap! discord/state assoc :id id))

(defmethod handle-event :guild-create
  [_ {:keys [id]}]
  (let [guild-id id
        [{command-id :id}] @(discord/register-commands guild-id)]
    (discord/set-permission guild-id command-id)))

(defmethod handle-event :default
  [_ _])

;;;;;;;;;;;;;;;;;;;;; Bot startup and entry point
(defn run []
  (let [event-ch (a/chan 100)
        connection-ch (c/connect-bot! (:bot-token env)  event-ch :intents #{:guilds})
        messaging-ch (m/start-connection! (:bot-token env))
        init-state {:connection connection-ch
                    :event event-ch
                    :messaging messaging-ch}]
    (reset! discord/state init-state)
    (try (e/message-pump! event-ch handle-event)
         (finally
           (m/stop-connection! messaging-ch)
           (a/close!           event-ch)))))

(defn -main
  [& _]
  (when-not (config/validate-env)
    (println "Error in configuration")
    (System/exit -1))
  (run)
  (shutdown-agents))
