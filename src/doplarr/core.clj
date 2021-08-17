(ns doplarr.core
  (:require
   [com.rpl.specter :as s]
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
(defonce cache (cache/ttl-cache-factory {} :ttl 900000)) ; 15 Minute cache expiration, coinciding with the interaction token

(def channel-timeout 600000)

;; Slash command setup
(def request-command
  {:name "request"
   :description "Requests a series or movie"
   :default_permission false
   :options
   [{:type 1
     :name "series"
     :description "Requests a series"
     :options
     [{:type 3
       :name "term"
       :description "Search term"
       :required true}]}
    {:type 1
     :name "movie"
     :description "Requests a movie",
     :options
     [{:type 3
       :name "term"
       :description "Search term"
       :required true}]}]})

(def max-results (delay (:max-results env 10)))

(def search-fn {:series sonarr/search
                :movie radarr/search})

(def profiles-fn {:series sonarr/quality-profiles
                  :movie radarr/quality-profiles})

(def request-fn {:series sonarr/request
                 :movie radarr/request})

(def timed-out-response {:content "Request timed out, please try again"})

(def interaction-types {1 :ping
                        2 :application-command
                        3 :message-component})

(def component-types {1 :action-row
                      2 :button
                      3 :select-menu})

(def request-thumbnail
  {:series "https://thetvdb.com/images/logo.png"
   :movie "https://i.imgur.com/44ueTES.png"})

;; Discljord setup
(defn register-commands [guild-id]
  (m/bulk-overwrite-guild-application-commands!
   (:messaging @state)
   (:id @state)
   guild-id
   [request-command]))

(defn set-permission [guild-id command-id]
  (m/edit-application-command-permissions!
   (:messaging @state)
   (:id @state)
   guild-id
   command-id
   [{:id (:role-id env)
     :type 1
     :permission true}]))

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

(defn application-command-interaction-option-data [app-com-int-opt]
  [(keyword (:name app-com-int-opt))
   (into {} (map (juxt (comp keyword :name) :value)) (:options app-com-int-opt))])

(defn interaction-data [interaction]
  {:id (:id interaction)
   :type (interaction-types (:type interaction))
   :token (:token interaction)
   :payload
   {:component-type (component-types (get-in interaction [:data :component-type]))
    :component-id (s/select-one [:data :custom-id] interaction)
    :name (s/select-one [:data :name] interaction)
    :values (s/select-one [:data :values] interaction)
    :options (into {} (map application-command-interaction-option-data) (get-in interaction [:data :options]))}})

(defn request-button [uuid enabled?]
  {:type 2
   :style 1
   :disabled (not enabled?)
   :custom_id (str "request:" uuid)
   :label "Request"})

(defn select-menu-option [index result]
  {:label (:title result)
   :description (:year result)
   :value index})

(defn dropdown [content id options]
  {:content content
   :components [{:type 1
                 :components [{:type 3
                               :custom_id id
                               :options options}]}]})

(defn search-response [results uuid]
  (if (empty? results)
    {:content "Search result returned no hits"}
    (dropdown "Choose one of the following results"
              (str "select:" uuid)
              (map-indexed select-menu-option results))))

(defn selection-embed [selection & {:keys [season profile]}]
  {:title (:title selection)
   :description (:overview selection)
   :image {:url (:remotePoster selection)}
   :thumbnail {:url (request-thumbnail (if season :series :movie))}
   :fields (filterv
            identity
            [{:name "Profile"
              :value profile}
             (when season
               {:name "Season"
                :value (if (= season -1)
                         "All"
                         season)})])})

(defn request [selection uuid & {:keys [season profile]}]
  {:content (str "Request this " (if season "series" "movie") " ?")
   :embeds [(selection-embed selection :season season :profile profile)]
   :components [{:type 1 :components [(request-button uuid true)]}]})

(defn request-alert [selection & {:keys [season profile]}]
  {:content "This has been requested!"
   :embeds [(selection-embed selection :season season :profile profile)]})

(defn select-season [series uuid]
  (dropdown "Which season?"
            (str "select_season:" uuid)
            (conj (map #(hash-map :label (str "Season: " %) :value %)
                       (range 1 (inc (:seasonCount series))))
                  {:label "All Seasons" :value "-1"})))

(defn select-profile [profiles uuid]
  (dropdown "Which quality profile?"
            (str "select_profile:" uuid)
            (map #(hash-map :label (:name %) :value (:id %)) profiles)))

(defn await-interaction [chan token]
  (a/go
    (a/alt!
      (a/timeout channel-timeout) (do
                                    (update-interaction-response token timed-out-response)
                                    nil)
      chan ([v] v))))

(defn make-request [interaction]
  (let [uuid (str (java.util.UUID/randomUUID))
        id (:id interaction)
        token (:token interaction)
        search (:options (:payload interaction))
        request-type (first (keys search))
        request-term (s/select-one [request-type :term] search)
        chan (a/chan)]
    ; Send the in-progress response
    (interaction-response id token 5 :ephemeral? true)
    ; Create this command's channel
    (swap! cache assoc uuid chan)
    (a/go
      (let [results (->> ((search-fn request-type) request-term)
                         a/<!
                         (into [] (take @max-results)))]
        ; Results selection
        (a/<! (update-interaction-response token (search-response results uuid)))
        (when-some [selection-interaction (a/<! (await-interaction chan token))]
          (let [selection-id (Integer/parseInt (s/select-one [:payload :values 0] selection-interaction))
                profiles (->> ((profiles-fn request-type))
                              a/<!
                              (into []))]
            ; Profile selection
            (a/<! (update-interaction-response token (select-profile profiles uuid)))
            (when-some [profile-interaction (a/<! (await-interaction chan token))]
              (let [selection (nth results selection-id)
                    profile-id (Integer/parseInt (s/select-one [:payload :values 0] profile-interaction))
                    profile (s/select-one [s/ALL (comp (partial = profile-id) :id) :name] profiles)
                    season-id (when (= request-type :series)
                                ; Optional season selection for TV shows
                                (a/<! (update-interaction-response token (select-season selection uuid)))
                                (when-some [season-interaction (a/<! (await-interaction chan token))]
                                  (Integer/parseInt (s/select-one [:payload :values 0] season-interaction))))]
                ; Verify request
                (a/<! (update-interaction-response token (request selection uuid :season season-id :profile profile)))
                ; Wait for the button press, we don't care about the actual interaction
                (a/<! (await-interaction chan token))
                ; Send public followup and actually perform request
                (a/<! (followup-repsonse token (request-alert selection :season season-id :profile profile)))
                ((request-fn request-type)
                 selection
                 {:season season-id
                  :profile-id profile-id})
                (update-interaction-response token {:content "Requested!"
                                                    :components []})))))))))

(defn continue-request [interaction]
  (let [[_ uuid] (str/split (s/select-one [:payload :component-id] interaction) #":")]
    (interaction-response (:id interaction) (:token interaction) 6)
    (a/offer! (get @cache uuid) interaction)))

;;;;;;;;;;;;;;;;;;;;;;;; Gateway event handlers
(defmulti handle-event
  (fn [event-type event-data]
    event-type))

(defmethod handle-event :interaction-create
  [_ data]
  (let [interaction (interaction-data data)]
    (case (:type interaction)
      :application-command (make-request interaction) ; These will all be requests as that is the only top level command
      :message-component (continue-request interaction))))

(defmethod handle-event :ready
  [event-type {{id :id} :user}]
  (swap! state assoc :id id))

(defmethod handle-event :guild-create
  [event-type {:keys [id]}]
  (let [guild-id id
        [{command-id :id}] @(register-commands guild-id)]
    (set-permission guild-id command-id)))

(defmethod handle-event :default
  [event-type event-data])

;; Bot startup and entry point
(defn run []
  (let [event-ch (a/chan 100)
        connection-ch (c/connect-bot! (:bot-token env)  event-ch :intents #{:guilds})
        messaging-ch (m/start-connection! (:bot-token env))
        init-state {:connection connection-ch
                    :event event-ch
                    :messaging messaging-ch}]
    (reset! state init-state)
    (try (e/message-pump! event-ch handle-event)
         (finally
           (m/stop-connection! messaging-ch)
           (a/close!           event-ch)))))

(defn check-config-entry [entry]
  (when (nil? (entry env))
    (throw (Exception. (str "Double check the configuration of" entry)))))

(defn validate-config []
  (let [entries [:sonarr-url
                 :sonarr-api
                 :radarr-url
                 :radarr-api
                 :bot-token
                 :role-id]]
    (doseq [entry entries]
      (try
        (check-config-entry entry)
        (catch Exception e
          (println e)
          (System/exit -1))))))

(defn -main
  [& _]
  (validate-config)
  (run)
  (shutdown-agents))
