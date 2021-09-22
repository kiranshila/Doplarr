(ns doplarr.discord
  (:require
   [config.core :refer [env]]
   [discljord.messaging :as m]
   [clojure.core.cache.wrapped :as cache]
   [com.rpl.specter :as s]))

(defonce state (atom nil))
(defonce cache (cache/ttl-cache-factory {} :ttl 900000)) ; 15 Minute cache expiration, coinciding with the interaction token

(def channel-timeout 600000)

(def request-command
  {:name "request"
   :description "Request a series or movie"
   :default_permission false
   :options
   [{:type 1
     :name "series"
     :description "Request a series"
     :options
     [{:type 3
       :name "term"
       :description "Search term"
       :required true}]}
    {:type 1
     :name "movie"
     :description "Request a movie",
     :options
     [{:type 3
       :name "term"
       :description "Search term"
       :required true}]}]})

(defn content-response [content]
  {:content content
   :components []})

(def interaction-types {1 :ping
                        2 :application-command
                        3 :message-component})

(def component-types {1 :action-row
                      2 :button
                      3 :select-menu})

(def max-results (delay (:max-results env 10)))

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
   :user-id (s/select-one [:member :user :id] interaction)
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

(defn request-4k-button [uuid enabled?]
  {:type 2
   :style 1
   :disabled (not enabled?)
   :custom_id (str "request-4k:" uuid)
   :label "Request 4K"})

(defn select-menu-option [index result]
  {:label (or (:title result) (:name result))
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
              (str "result-select:" uuid)
              (map-indexed select-menu-option results))))

(defn select-profile [profiles uuid]
  (dropdown "Which quality profile?"
            (str "profile-select:" uuid)
            (map #(hash-map :label (:name %) :value (:id %)) profiles)))

(defn selection-embed [selection & {:keys [season profile]}]
  {:title (:title selection)
   :description (:overview selection)
   :image {:url (:remotePoster selection)}
   :thumbnail {:url (request-thumbnail (if season :series :movie))}
   :fields (filterv
            identity
            [(when profile
               {:name "Profile"
                :value profile})
             (when season
               {:name "Season"
                :value (if (= season -1)
                         "All"
                         season)})])})

(defn request [selection uuid & {:keys [season profile]}]
  {:content (str "Request this " (if season "series" "movie") " ?")
   :embeds [(selection-embed selection :season season :profile profile)]
   :components [{:type 1 :components (filterv identity [(request-button uuid true)
                                                        (when (:backend-4k selection)
                                                          (request-4k-button uuid true))])}]})

(defn request-alert [selection & {:keys [season profile]}]
  {:content "This has been requested!"
   :embeds [(selection-embed selection :season season :profile profile)]})

(defn select-season [series uuid]
  (dropdown "Which season?"
            (str "season-select:" uuid)
            (conj (map #(hash-map :label (str "Season: " %) :value %)
                       (range 1 (inc (:seasonCount series))))
                  {:label "All Seasons" :value "-1"})))

(defn dropdown-index [interaction]
  (Integer/parseInt (s/select-one [:payload :values 0] interaction)))
