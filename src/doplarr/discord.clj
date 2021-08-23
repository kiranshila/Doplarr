(ns doplarr.discord
  (:require
   [config.core :refer [env]]
   [discljord.messaging :as m]
   [clojure.core.cache.wrapped :as cache]
   [clojure.core.async :as a]
   [com.rpl.specter :as s]
   [clojure.string :as str]))

(defonce state (atom nil))
(defonce cache (cache/ttl-cache-factory {} :ttl 900000)) ; 15 Minute cache expiration, coinciding with the interaction token

(def channel-timeout 600000)

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

(def timed-out-response {:content "Request timed out, please try again"})

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

(defn await-interaction [chan token]
  (a/go
    (a/alt!
      (a/timeout channel-timeout) (do
                                    (update-interaction-response token timed-out-response)
                                    (throw (Exception. "Interaction timed out")))
      chan ([v] v))))

(defn continue-request [interaction]
  (let [[_ uuid] (str/split (s/select-one [:payload :component-id] interaction) #":")]
    (interaction-response (:id interaction) (:token interaction) 6)
    (a/offer! (get @cache uuid) interaction)))
