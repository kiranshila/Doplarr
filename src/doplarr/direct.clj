(ns doplarr.direct
  (:require
   [doplarr.discord :as discord]
   [doplarr.sonarr :as sonarr]
   [doplarr.radarr :as radarr]
   [com.rpl.specter :as s]
   [clojure.core.async :as a]))

(def search-fn {:series sonarr/search
                :movie radarr/search})

(def profiles-fn {:series sonarr/quality-profiles
                  :movie radarr/quality-profiles})

(def request-fn {:series sonarr/request
                 :movie radarr/request})

(defn select-profile [profiles uuid]
  (discord/dropdown "Which quality profile?"
                    (str "select_profile:" uuid)
                    (map #(hash-map :label (:name %) :value (:id %)) profiles)))

(defn make-request [interaction]
  (let [uuid (str (java.util.UUID/randomUUID))
        id (:id interaction)
        token (:token interaction)
        search (:options (:payload interaction))
        request-type (first (keys search))
        request-term (s/select-one [request-type :term] search)
        chan (a/chan)]
    ; Send the in-progress response
    (discord/interaction-response id token 5 :ephemeral? true)
    ; Create this command's channel
    (swap! discord/cache assoc uuid chan)
    (a/go
      (let [results (->> ((search-fn request-type) request-term)
                         a/<!
                         (into [] (take @discord/max-results)))]
        ; Results selection
        (a/<! (discord/update-interaction-response token (discord/search-response results uuid)))
        (when-some [selection-interaction (a/<! (discord/await-interaction chan token))]
          (let [selection-id (Integer/parseInt (s/select-one [:payload :values 0] selection-interaction))
                profiles (->> ((profiles-fn request-type))
                              a/<!
                              (into []))]
            ; Profile selection
            (a/<! (discord/update-interaction-response token (select-profile profiles uuid)))
            (when-some [profile-interaction (a/<! (discord/await-interaction chan token))]
              (let [selection (nth results selection-id)
                    profile-id (Integer/parseInt (s/select-one [:payload :values 0] profile-interaction))
                    profile (s/select-one [s/ALL (comp (partial = profile-id) :id) :name] profiles)
                    season-id (when (= request-type :series)
                                ; Optional season selection for TV shows
                                (a/<! (discord/update-interaction-response token (discord/select-season selection uuid)))
                                (when-some [season-interaction (a/<! (discord/await-interaction chan token))]
                                  (Integer/parseInt (s/select-one [:payload :values 0] season-interaction))))]
                ; Verify request
                (a/<! (discord/update-interaction-response token (discord/request selection uuid :season season-id :profile profile)))
                ; Wait for the button press, we don't care about the actual interaction
                (a/<! (discord/await-interaction chan token))
                ; Send public followup and actually perform request
                (a/<! (discord/followup-repsonse token (discord/request-alert selection :season season-id :profile profile)))
                ((request-fn request-type)
                 selection
                 {:season season-id
                  :profile-id profile-id})
                (discord/update-interaction-response token {:content "Requested!"
                                                            :components []})))))))))
