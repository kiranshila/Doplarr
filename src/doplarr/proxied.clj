(ns doplarr.proxied
  (:require
   [doplarr.discord :as discord]
   [doplarr.overseerr :as ovsr]
   [clojure.core.async :as a]
   [com.rpl.specter :as s]))

(def search-fn {:series ovsr/search-series
                :movie ovsr/search-movie})

(defn make-request [interaction]
  (let [uuid (str (java.util.UUID/randomUUID))
        id (:id interaction)
        user-id (:user-id interaction)
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
        ; Wait for selection
        (when-some [selection-interaction (a/<! (discord/await-interaction chan token))]
          (let [selection-id (Integer/parseInt (s/select-one [:payload :values 0] selection-interaction))
                selection (ovsr/selection-to-embedable (nth results selection-id))
                season-id (when (= request-type :series)
                                        ; Optional season selection for TV shows
                            (a/<! (discord/update-interaction-response token (discord/select-season selection uuid)))
                            (when-some [season-interaction (a/<! (discord/await-interaction chan token))]
                              (Integer/parseInt (s/select-one [:payload :values 0] season-interaction))))]
            (clojure.pprint/pprint selection)
                                        ; Verify request
            (a/<! (discord/update-interaction-response token (discord/request selection uuid :season season-id)))
                                        ; Wait for the button press, we don't care about the actual interaction
            (a/<! (discord/await-interaction chan token))
                                        ; Send public followup and actually perform request
            (a/<! (discord/followup-repsonse token (discord/request-alert selection :season season-id)))
            (ovsr/request
             selection
             {:season season-id})
            (discord/update-interaction-response token {:content "Requested!"
                                                        :components []})))))))
