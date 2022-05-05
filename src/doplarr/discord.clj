(ns doplarr.discord
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [com.rpl.specter :as s]
   [discljord.messaging :as m]
   [doplarr.utils :as utils]
   [fmnoise.flow :as flow :refer [else]]
   [taoensso.timbre :refer [fatal]]))

(defn request-command [media-types]
  {:name "request"
   :description "Request media"
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
   :flags 64
   :embeds []
   :components []})

(def interaction-types {1 :ping
                        2 :application-command
                        3 :message-component})

(def component-types {1 :action-row
                      2 :button
                      3 :select-menu})

(def MAX-OPTIONS 25)
(def MAX-CHARACTERS 100)

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
   :channel-id (:channel-id interaction)
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

(defn page-button [uuid option page label]
  {:type 2
   :style 1
   :custom_id (str "option-page:" uuid ":" option "-" page)
   :disabled false
   :label (apply str (take MAX-CHARACTERS label))})

(defn select-menu-option [index result]
  {:label (apply str (take MAX-CHARACTERS (or (:title result) (:name result))))
   :description (:year result)
   :value index})

(defn dropdown [content id options]
  {:content content
   :flags 64
   :components [{:type 1
                 :components [{:type 3
                               :custom_id id
                               :options options}]}]})

(defn search-response [results uuid]
  (if (empty? results)
    {:content "Search result returned no hits"
     :flags 64}
    (dropdown "Choose one of the following results"
              (str "result-select:" uuid)
              (map-indexed select-menu-option results))))

(defn option-dropdown [option options uuid page]
  (let [all-options (map #(set/rename-keys % {:name :label :id :value}) options)
        chunked (partition-all MAX-OPTIONS all-options)
        ddown (dropdown (str "Which " (utils/canonical-option-name option) "?")
                        (str "option-select:" uuid ":" (name option))
                        (nth chunked page))]
    (cond-> ddown
      ; Create the action row if we have more than 1 chunk
      (> (count chunked) 1) (update-in [:components] conj {:type 1 :components []})
      ; More chunks exist
      (< page (dec (count chunked))) (update-in [:components 1 :components] conj (page-button uuid (name option) (inc page) "More"))
      ; Past chunk 1
      (> page 0) (update-in [:components 1 :components] conj (page-button uuid (name option) (dec page) "Less")))))

(defn dropdown-result [interaction]
  (Integer/parseInt (s/select-one [:payload :values 0] interaction)))

(defn request-embed [{:keys [media-type title overview poster season quality-profile language-profile rootfolder]}]
  {:title title
   :description overview
   :image {:url poster}
   :thumbnail {:url (media-type request-thumbnail)}
   :fields (filterv
            identity
            ; Some overrides to make things pretty
            [(when quality-profile
               {:name "Profile"
                :value quality-profile})
             (when language-profile
               {:name "Language Profile"
                :value language-profile})
             (when season
               {:name "Season"
                :value (if (= season -1) "All" season)})
             (when rootfolder
               {:name "Root Folder"
                :value rootfolder})])})

(defn request [embed-data uuid]
  {:content (str "Request this " (name (:media-type embed-data)) " ?")
   :embeds [(request-embed embed-data)]
   :flags 64
   :components [{:type 1 :components (for [format (:request-formats embed-data)]
                                       (request-button format uuid))}]})

(defn request-performed-plain [payload media-type user-id]
  {:content
   (str "<@" user-id "> your request for the "
        (name media-type) " `" (:title payload) " (" (:year payload) ")"
        "` has been received!")})

(defn request-performed-embed [embed-data user-id]
  {:content (str "<@" user-id "> has requested:")
   :embeds [(request-embed embed-data)]})

;; Discljord Utilities
(defn register-commands [media-types bot-id messaging guild-id]
  (->> @(m/bulk-overwrite-guild-application-commands!
         messaging bot-id guild-id
         [(request-command media-types)])
       (else #(fatal % "Error in registering commands"))))
