(ns doplarr.core
  (:require
   [doplarr.sonarr :as sonarr]
   [doplarr.radarr :as radarr]
   [discljord.messaging :as m]
   [discljord.connections :as c]
   [discljord.events :as e]
   [config.core :refer [env]]
   [clojure.core.async :as a]
   [clojure.core.cache.wrapped :as cache]
   [clojure.string :as str])
  (:gen-class))

(defonce state (atom nil))
(defonce cache (cache/ttl-cache-factory {} :ttl (:cache-expiration (:bot env))))

;; Slash command setup
(def request-command
  {:name "request",
   :description "Requests a series or movie",
   :options
   [{:type 1,
     :name "series",
     :description "Requests a series",
     :options
     [{:type 3, :name "term", :description "Search term", :required true}]}
    {:type 1,
     :name "movie",
     :description "Requests a movie",
     :options
     [{:type 3, :name "term", :description "Search term", :required true}]}]})

;; Discljord setup
(defn register-commands [guild-id]
  (m/bulk-overwrite-guild-application-commands!
   (:messaging @state)
   (:id @state)
   guild-id
   [request-command]))

(defn interaction-response [interaction-id interaction-token type & {:keys [ephemeral? content components embeds]}]
  (m/create-interaction-response!
   (:messaging @state)
   interaction-id
   interaction-token
   type
   :data
   (cond-> {}
     ephemeral? (assoc :flags 64)
     content (assoc :content content)
     components (assoc :components components)
     embeds (assoc :embeds embeds))))

(defn followup-repsonse [interaction-token & {:keys [ephermeral? content components embeds]}]
  (m/create-followup-message!
   (:messaging @state)
   (:id @state)
   interaction-token
   (cond-> {}
     ephermeral? (assoc :flags 64)
     content (assoc :content content)
     components (assoc :components components)
     embeds (assoc :embeds embeds))))

(defn update-interaction-response [interaction-token & {:keys [content components embeds]}]
  (m/edit-original-interaction-response!
   (:messaging @state)
   (:id @state)
   interaction-token
   :content content
   :components components
   :embeds embeds))

(defn delete-interaction-response [interaction-token]
  (m/delete-original-interaction-response!
   (:messaging @state)
   (:id @state)
   interaction-token))

(def interaction-types {1 :ping
                        2 :application-command
                        3 :message-component})

(def component-types {1 :action-row
                      2 :button
                      3 :select-menu})

(defn application-command-interaction-option-data [app-com-int-opt]
  [(keyword (:name app-com-int-opt))
   (into {} (map (juxt (comp keyword :name) :value)) (:options app-com-int-opt))])

(defn interaction-data [interaction]
  {:id (:id interaction)
   :type (interaction-types (:type interaction))
   :token (:token interaction)
   :payload
   {:component-type (component-types (get-in interaction [:data :component-type]))
    :component-id (get-in interaction [:data :custom-id])
    :name (get-in interaction [:data :name])
    :values (get-in interaction [:data :values])
    :options (into {} (map application-command-interaction-option-data) (get-in interaction [:data :options]))}})

(defn request-button [uuid enabled?]
  {:type 2
   :style 1
   :disabled (not enabled?)
   :custom_id (str "request:" uuid)
   :label "Request"})

(defn generate-select-menu-option [index result]
  {:label (:title result)
   :description (:year result)
   :value index})

(defn generate-search-response [results uuid]
  (if (empty? results)
    {:content "Search result returned no hits"}
    {:content "Choose one of the following results:"
     :components [{:type 1
                   :components [{:type 3
                                 :custom_id (str "select:" uuid)
                                 :options (into [] (map-indexed generate-select-menu-option results))}]}]}))

(def request-thumbnail
  {:series "https://thetvdb.com/images/logo.png"
   :movie "https://i.imgur.com/44ueTES.png"})

(defn selection-embed [selection request-type & {:keys [season]}]
  (cond-> {:title (:title selection)
           :description (:overview selection)
           :image {:url (:remotePoster selection)}
           :thumbnail {:url (request-thumbnail request-type)}}
    season (assoc :fields [{:name "Season"
                            :value (if (= season -1)
                                     "All"
                                     season)}])))

(defn request [selection uuid & {:keys [season]}]
  (let [request-type (get-in @cache [uuid :type])]
    {:content (str "Request this " (name request-type) " ?")
     :embeds [(selection-embed selection request-type :season season)]
     :components [{:type 1 :components [(request-button uuid true)]}]}))

(defn request-alert [selection uuid & {:keys [season]}]
  (let [request-type (get-in @cache [uuid :type])]
    {:content "This has been requested!"
     :embeds [(selection-embed selection request-type :season season)]}))

(defn select-season [series uuid]
  {:content "Which season?"
   :components [{:type 1
                 :components [{:type 3
                               :custom_id (str "select_season:" uuid)
                               :options (conj (map #(hash-map :label (str "Season: " %) :value %) (sonarr/missing-seasons series))
                                              {:label "All Seasons" :value "-1"})}]}]})

(defn start-request [interaction]
  (let [uuid (str (java.util.UUID/randomUUID))
        id (:id interaction)
        token (:token interaction)
        search (:options (:payload interaction))
        request-type (first (keys search))
        request-term (get-in search [request-type :term])]
    ; Create the cache entry with the data we have so far
    (swap! cache assoc-in [uuid :token] token)
    (swap! cache assoc-in [uuid :type] request-type)
    ; Send the in-progress response
    (interaction-response id token 5 :ephemeral? true)
    ; Fetch the request
    (let [perform-search (case request-type
                           :series sonarr/search
                           :movie radarr/search)
          filter-aquired (case request-type
                           :series sonarr/aquired-all-seasons?
                           :movie :monitored)
          results (->> (perform-search request-term)
                       (filter (complement filter-aquired))
                       (take (:max-results (:bot env)))
                       (into []))]
      ; Update the cache with these results
      (swap! cache assoc-in [uuid :results] results)
      ; Generate the results selector and update the thing
      (update-interaction-response token (generate-search-response results uuid)))))

(defn component-ack [interaction-id interaction-token]
  (interaction-response interaction-id interaction-token 6))

(defn perform-request [uuid]
  (let [selection (get-in @cache [uuid :selection])
        season (get-in @cache [uuid :season])
        type (get-in @cache [uuid :type])]
    (case type
      :movie (radarr/request selection)
      :series (if (= -1 season)
                (sonarr/request-all selection)
                (sonarr/request-season selection season)))))

(defn continue-request [interaction]
  (let [[action uuid] (str/split (get-in interaction [:payload :component-id]) #":")
        token (get-in @cache [uuid :token])
        request-type (get-in @cache [uuid :type])]
    (case action
      "select" (let [selection-id (Integer/parseInt (get-in interaction [:payload :values 0]))
                     selection (get-in @cache [uuid :results selection-id])]
                 (swap! cache assoc-in [uuid :selection] selection)
                 (case request-type
                   :series (update-interaction-response token (select-season selection uuid))
                   :movie (update-interaction-response token (request selection uuid))))

      "select_season" (let [selection (get-in @cache [uuid :selection])
                            season-id (Integer/parseInt (get-in interaction [:payload :values 0]))]
                        (swap! cache assoc-in [uuid :season] season-id)
                        (update-interaction-response token (request selection uuid :season season-id)))
      "request" (let [selection (get-in @cache [uuid :selection])
                      season (get-in @cache [uuid :season])]
                  (followup-repsonse token (request-alert selection uuid {:season season}))
                  (perform-request uuid)
                  (update-interaction-response token {:content "Requested!"
                                                      :components []}))
      "cancel" (delete-interaction-response (:token interaction)))
    (component-ack (:id interaction) (:token interaction))))

;; Gateway event handlers
(defmulti handle-event
  (fn [event-type event-data]
    event-type))

(defmethod handle-event :interaction-create
  [_ data]
  (let [interaction (interaction-data data)]
    (case (:type interaction)
      :application-command (start-request interaction) ; These will all be requests as that is the only top level command
      :message-component (continue-request interaction))))

(defmethod handle-event :ready
  [event-type {{id :id} :user}]
  (swap! state assoc :id id))

(defmethod handle-event :guild-create
  [event-type {:keys [id]}]
  (register-commands id))

(defmethod handle-event :default
  [event-type event-data])

;; Bot startup and entry point
(defn run []
  (let [event-ch (a/chan 100)
        connection-ch (c/connect-bot! (:token (:bot env))  event-ch :intents #{:guilds})
        messaging-ch (m/start-connection! (:token (:bot env)))
        init-state {:connection connection-ch
                    :event event-ch
                    :messaging messaging-ch}]
    (reset! state init-state)
    (try (e/message-pump! event-ch handle-event)
         (finally
           (m/stop-connection! messaging-ch)
           (a/close!           event-ch)))))

(defn -main
  [& _]
  (run)
  (shutdown-agents))
