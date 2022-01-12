(ns doplarr.core
  (:require
   [taoensso.timbre :refer [info fatal debug] :as timbre]
   [taoensso.timbre.tools.logging :as tlog]
   [discljord.connections :as c]
   [discljord.messaging :as m]
   [discljord.events :as e]
   [config.core :refer [env]]
   [doplarr.config :as config]
   [doplarr.state :as state]
   [doplarr.interaction-state-machine :as ism]
   [doplarr.discord :as discord]
   [clojure.core.async :as a]
   [clojure.string :as str])
  (:gen-class))

; Pipe tools.logging to timbre
(tlog/use-timbre)

#_(timbre/merge-config! {:min-level [[#{"discljord.messaging.*"} :trace]
                                     [#{"*"} :debug]]})

; Multimethod for handling incoming Discord events
(defmulti handle-event!
  (fn [event-type _]
    event-type))

; A new interaction was received (slash command or component)
(defmethod handle-event! :interaction-create
  [_ data]
  (debug "Received interaction")
  (let [interaction (discord/interaction-data data)]
    (case (:type interaction)
      ; Slash commands start our request sequence
      :application-command (ism/start-interaction! interaction)
      ; Message components continue the request until they are complete or failed
      :message-component (ism/continue-interaction! interaction))))

; Once we receive a ready event, grab our bot-id
(defmethod handle-event! :ready
  [_ {{id :id} :user}]
  (info "Discord connection successful")
  (swap! state/discord assoc :bot-id id))

(defmethod handle-event! :guild-create
  [_ {:keys [id]}]
  (info "Connected to guild")
  (let [media-types (config/available-media)
        messaging (:messaging @state/discord)
        bot-id (:bot-id @state/discord)
        [{command-id :id}] (discord/register-commands media-types bot-id messaging id)]
    (when (:role-id env)
      (discord/set-permission bot-id messaging id command-id))))

(defmethod handle-event! :default
  [event-type data]
  (debug "Got unhandled event" event-type data))

(defn start-bot! []
  (let [event-ch (a/chan 100)
        token (:discord/token env)
        connection-ch (c/connect-bot! token event-ch :intents #{:guilds})
        messaging-ch (m/start-connection! token)
        init-state {:connection connection-ch
                    :event event-ch
                    :messaging messaging-ch}]
    (reset! state/discord init-state)
    (try (e/message-pump! event-ch handle-event!)
         (catch Exception e (fatal e "Exception thrown from event handler"))
         (finally
           (m/stop-connection! messaging-ch)
           (a/close!           event-ch)))))

; Program Entry Point
(defn -main
  [& _]
  (config/validate-config)
  (start-bot!)
  (shutdown-agents))
