(ns doplarr.proxied
  (:require
   [doplarr.discord :as discord]
   [doplarr.overseerr :as ovsr]
   [clojure.core.async :as a]
   [fmnoise.flow :as flow :refer [then else flet]]
   [com.rpl.specter :as s]))

(def search-fn {:series ovsr/search-series
                :movie ovsr/search-movie})

(defn timeout-bail [token error]
  (discord/update-interaction-response token discord/timed-out-response)
  error)

(def quota-response {:content "You have already reached your request quota"
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
                    selection (ovsr/selection-to-embedable (nth results selection-id))
                    season-id (when (= request-type :series)
                                        ; Optional season selection for TV shows
                                (a/<! (discord/update-interaction-response token (discord/select-season selection uuid)))
                                (flet [season-interaction (->> (a/<! (discord/await-interaction chan token))
                                                               (else timeout-bail))]
                                      (Integer/parseInt (s/select-one [:payload :values 0] season-interaction))))]
                                        ; Verify request
                (a/<! (discord/update-interaction-response token (discord/request selection uuid :season season-id)))
                                        ; Wait for the button press, we don't care about the actual interaction
                (flet [_ (->> (a/<! (discord/await-interaction chan token))
                              (else timeout-bail))]
                                        ; Send public followup and actually perform request if the user exists
                      (if-let [ovsr-id ((a/<! (ovsr/discord-users)) user-id)]
                        (flet [_ (->> (a/<! (ovsr/request
                                             (ovsr/result-to-request ovsr-id selection)
                                             {:season season-id}))
                                      (then #(discord/update-interaction-response token {:content "Requested!"
                                                                                         :components []}))
                                      (else (fn [_] ;Spooky weird macro nonsense, this *has* to be unary otherwise this breaks, for some reason
                                              (a/<! (discord/update-interaction-response token quota-response))
                                              (ex-info "Quota-error" {}))))]
                              (a/<! (discord/followup-repsonse token (discord/request-alert selection :season season-id))))
                        (discord/update-interaction-response token missing-user-response)))))))))
