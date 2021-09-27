(ns doplarr.discord
  (:require
   [config.core :refer [env]]
   [discljord.messaging :as m]
   [com.rpl.specter :as s]))

(defn request-command [media-types]
  {:name "request"
   :description "Request media"
   :default_permission (boolean (not (:role-id env)))
   :options
   (into [] (for [media media-types]
              {:type 1
               :name media
               :description (str "Request " media)
               :options [{:type 3
                          :name "query"
                          :description "Query"
                          :required true}]}))})

(defn content-response [content]
  {:content content
   :components []})

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
(defn register-commands [media-types bot-id messaging guild-id]
  (m/bulk-overwrite-guild-application-commands!
   messaging bot-id guild-id
   [(request-command media-types)]))

(defn set-permission [bot-id messaging guild-id command-id]
  (m/edit-application-command-permissions!
   messaging bot-id guild-id command-id
   [{:id (:role-id env) :type 1 :permission true}]))

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

(defn option-dropdown [name options uuid]
  (dropdown (str "Which " name "?")
            (str "option-select:" uuid)
            (map #(hash-map :label (:name %) :value (:id %)) options)))

(defn dropdown-result [interaction]
  (Integer/parseInt (s/select-one [:payload :values 0] interaction)))

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
