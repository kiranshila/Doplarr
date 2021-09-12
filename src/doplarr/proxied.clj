(ns doplarr.proxied
  (:require
   [doplarr.discord :as discord]
   [doplarr.overseerr :as ovsr]
   [clojure.core.async :as a]
   [fmnoise.flow :as flow :refer [then else flet]]
   [com.rpl.specter :as s]
   [clojure.string :as str]))

(def search-fn {:series ovsr/search-series
                :movie ovsr/search-movie})

(defn timeout-bail [token error]
  (discord/update-interaction-response token discord/timed-out-response)
  error)

(defn request-response [content]
  {:content content
   :components []})

(def missing-user-response {:content "You do not have an associated account on Overseerr"
                            :components []})

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
        (flet [selection-interaction (->> (a/<! (discord/await-interaction chan token))
                                          (else timeout-bail))]
              (let [selection-id (Integer/parseInt (s/select-one [:payload :values 0] selection-interaction))
                    selection-raw (nth results selection-id)
                    details (a/<! (ovsr/details (:id selection-raw) (:mediaType selection-raw)))
                    fourK-backend? (a/<! (ovsr/backend-4k? (:mediaType selection-raw)))
                    selection (ovsr/selection-to-embedable (merge details selection-raw {:backend-4k fourK-backend?}))
                    season-id (when (= request-type :series)
                                        ; Optional season selection for TV shows
                                (a/<! (discord/update-interaction-response token (discord/select-season selection uuid)))
                                (flet [season-interaction (->> (a/<! (discord/await-interaction chan token))
                                                               (else timeout-bail))]
                                      (Integer/parseInt (s/select-one [:payload :values 0] season-interaction))))]
                                        ; Verify request
                (a/<! (discord/update-interaction-response token (discord/request selection uuid :season season-id)))
                                        ; Wait for the button press
                (flet [request-interaction (->> (a/<! (discord/await-interaction chan token))
                                                (else timeout-bail))
                       request-4k? (str/starts-with? (s/select-one [:payload :component-id] request-interaction) "request-4k")]
                                        ; Send public followup and actually perform request if the user exists
                      (if-let [ovsr-id ((a/<! (ovsr/discord-users)) user-id)]
                        (flet [_ (->> (a/<! (ovsr/request
                                             (ovsr/result-to-request selection :season season-id :is4k request-4k?)
                                             ovsr-id))
                                      (then (fn [_] (discord/update-interaction-response token {:content "Requested!"
                                                                                                :components []})))
                                      (else (fn [e]
                                              (let [{:keys [status body] :as data} (ex-data e)
                                                    msg (second (re-matches #"\{\"message\":\"(.+)\"\}" body))] ; Not sure why this JSON didn't get parsed
                                                (if (= status 403)
                                                  (a/<! (discord/update-interaction-response token (request-response msg)))
                                                  (throw (ex-info "Non 403 error on request" data)))))))]
                              (a/<! (discord/followup-repsonse token (discord/request-alert selection :season season-id))))
                        (discord/update-interaction-response token missing-user-response)))))))))
