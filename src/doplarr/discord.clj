(ns doplarr.discord
  (:require
   [config.core :refer [env]]
   [com.rpl.specter :as s]
   [clojure.string :as str]
   [taoensso.timbre :refer [fatal]]
   [doplarr.utils :as utils]
   [fmnoise.flow :as flow :refer [else]]
   [discljord.messaging :as m]))

(defn request-command [media-types]
  {:name "request"
   :description "Request media"
   :default_permission (boolean (not (:role-id env)))
   :options
   (into [] (for [media media-types]
              {:type 1
               :name (name media)
               :description (str "Request " (name media))
               :options [{:type 3
                          :name "query"
                          :description "Query"
                          :required true}]}))})

(defn content-response [content]
  {:content content
   :embeds []
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

(defn request-button [format uuid]
  {:type 2
   :style 1
   :disabled false
   :custom_id (str "request:" uuid ":" format)
   :label (str/trim (str "Request " format))})

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

(defn option-dropdown [option options uuid]
  (dropdown (str "Which " (utils/canonical-option-name option) "?")
            (str "option-select:" uuid ":" (name option))
            (map #(hash-map :label (:name %) :value (:id %)) options)))

(defn dropdown-result [interaction]
  (Integer/parseInt (s/select-one [:payload :values 0] interaction)))

(defn request-embed [{:keys [media-type title overview poster season quality-profile language-profile]}]
  {:title title
   :description overview
   :image {:url poster}
   :thumbnail {:url (media-type request-thumbnail)}
   :fields (filterv
            identity
            [(when quality-profile
               {:name "Profile"
                :value quality-profile})
             (when language-profile
               {:name "Language Profile"
                :value language-profile})
             (when season
               {:name "Season"
                :value (if (= season -1) "All" season)})])})

(defn request [embed-data uuid]
  {:content (str "Request this " (name (:media-type embed-data)) " ?")
   :embeds [(request-embed embed-data)]
   :components [{:type 1 :components (for [format (:request-formats embed-data)]
                                       (request-button format uuid))}]})

(defn request-alert [requestable]
  {:content "This has been requested!"
   :embeds [(request-embed requestable)]})

;; Discljord Utilities
(defn register-commands [media-types bot-id messaging guild-id]
  (->> @(m/bulk-overwrite-guild-application-commands!
         messaging bot-id guild-id
         [(request-command media-types)])
       (else #(fatal % "Error in registering commands"))))

(defn set-permission [bot-id messaging guild-id command-id]
  (->> @(m/edit-application-command-permissions!
         messaging bot-id guild-id command-id
         [{:id (:role-id env) :type 1 :permission true}])
       (else #(fatal % "Error in setting command permissions"))))
