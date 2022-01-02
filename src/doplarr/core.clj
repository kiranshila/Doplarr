(ns doplarr.core
  (:require
   [clojure.core.cache.wrapped :as cache]
   [integrant.core :as ig]
   [doplarr.config :as config]
   [doplarr.interaction-state-machine :as ism]
   [discljord.messaging :as m]
   [discljord.connections :as c]
   [discljord.events :as e]
   [config.core :refer [env]]
   [clojure.core.async :as a]
   [doplarr.discord :as discord])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;; Backend public interfaces
(def backends [:radarr :sonarr :overseerr :readarr])
(def backend-fns [:search :request :additional-options :request-embed])

(def media-backends {:movie [:overseerr :radarr]
                     :series [:overseerr :sonarr]
                     :book [:readarr]})

(defn derive-backend! [backend]
  (derive (keyword "backend" (name backend)) :doplarr/backend))

; Generate Parent-Child Relationships
(run! derive-backend! backends)

; System configuration
(def config
  (-> (into {} (for [b backends]
                 [(keyword "backend" (name b)) {:ns b}]))
      (assoc :doplarr/backends
             (into {} (for [[media backends] media-backends
                            :let [backend (first (keep (config/available-backends) backends))]]
                        [media (ig/ref (keyword "backend" (name backend)))]))
             :doplarr/cache {:ttl 900000}
             :discord/events {:size 100}
             :discord/bot-id (promise)
             :discord/gateway {:event (ig/ref :discord/events)}
             :discord/messaging nil)))

(defmethod ig/init-key :doplarr/backend [_ {:keys [ns]}]
  (zipmap backend-fns (for [f backend-fns
                            :let [ns (str "doplarr.backends." (name ns))
                                  sym (symbol ns (name f))]]
                        (requiring-resolve sym))))

(defmethod ig/init-key :doplarr/backends [_ m]
  m)

(defmethod ig/init-key :doplarr/cache [_ {:keys [ttl]}]
  (cache/ttl-cache-factory {} :ttl ttl))

(defmethod ig/init-key :discord/bot-id [_ p]
  p)

(defmethod ig/init-key :discord/events [_ {:keys [size]}]
  (a/chan size))

(defmethod ig/init-key :discord/gateway [_ {:keys [event]}]
  (c/connect-bot! (:bot-token env) event :intents #{:guilds}))

(defmethod ig/init-key :discord/messaging [_ _]
  (m/start-connection! (:bot-token env)))

(defmethod ig/halt-key! :discord/events [_ chan]
  (a/close! chan))

(defmethod ig/halt-key! :discord/messaging [_ chan]
  (m/stop-connection! chan))

;;;;;;;;;;;;;;;;;;;;;;;; Gateway event handlers
(defmulti handle-event!
  (fn [_ event-type _]
    event-type))

(defmethod handle-event! :interaction-create
  [system _ data]
  (let [interaction (discord/interaction-data data)]
    (case (:type interaction)
      :application-command (ism/start-interaction! system interaction)
      :message-component (ism/continue-interaction! system interaction))))

(defmethod handle-event! :ready
  [{:discord/keys [bot-id]} _ {{id :id} :user}]
  (deliver bot-id id))

(defmethod handle-event! :guild-create
  [{:discord/keys [bot-id messaging] :as system} _ {:keys [id]}]
  (let [media-types (keys (:doplarr/backends system))
        [{command-id :id}] @(discord/register-commands media-types @bot-id messaging id)]
    (when (:role-id env)
      (discord/set-permission @bot-id messaging id command-id))))

(defmethod handle-event! :default
  [_ _ _])

(defn start-bot! []
  (let [{:discord/keys [events] :as system} (ig/init config)]
    (try (e/message-pump! events (partial handle-event! (select-keys system [:doplarr/backends
                                                                             :doplarr/cache
                                                                             :discord/messaging
                                                                             :discord/bot-id])))
         (finally (ig/halt! system)))))

(defn -main
  [& _]
  (when-let [config-error (config/validate-config)]
    (ex-info "Error in configuration" {:spec-error config-error})
    (System/exit -1))
  (start-bot!)
  (shutdown-agents))
