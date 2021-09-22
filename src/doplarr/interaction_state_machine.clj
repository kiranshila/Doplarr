(ns doplarr.interaction-state-machine
  (:require
   [doplarr.overseerr :as ovsr]
   [doplarr.discord :as discord]
   [clojure.core.async :as a]
   [com.rpl.specter :as s]
   [fmnoise.flow :as flow :refer [then else]]
   [clojure.string :as str]
   [doplarr.config :as config]
   [doplarr.sonarr :as sonarr]
   [doplarr.radarr :as radarr]))

(def backend (delay (config/backend)))

(def search-fn {:overseerr {:series #'ovsr/search-series
                            :movie #'ovsr/search-movie}
                :direct    {:series #'sonarr/search
                            :movie #'radarr/search}})

(def profiles-fn {:overseerr {:series #(a/go nil)
                              :movie #(a/go nil)}
                  :direct {:series #'sonarr/quality-profiles
                           :movie #'radarr/quality-profiles}})

(def process-selection-fn {:overseerr {:series #'ovsr/post-process-selection
                                       :movie #'ovsr/post-process-selection}
                           :direct {:series #'sonarr/post-process-series
                                    :movie (fn [movie] (a/go movie))}})

(def request-selection-fn {:overseerr #'ovsr/selection-to-request
                           :direct (fn [selection & _] selection)})

(def account-id-fn {:overseerr #(a/go ((a/<! (ovsr/discord-users)) %))
                    :direct (fn [_] (a/go 1))}) ; Dummy id to get around account check

(def request-fn {:overseerr {:series #'ovsr/request
                             :movie #'ovsr/request}
                 :direct    {:series #'sonarr/request
                             :movie #'radarr/request}})

(def content-status-fn {:overseerr {:series #'ovsr/season-status
                                    :movie #'ovsr/movie-status}
                        :direct    {:series #'sonarr/season-status
                                    :movie #'radarr/movie-status}})

(defn start-interaction [interaction]
  (let [uuid (str (java.util.UUID/randomUUID))
        id (:id interaction)
        token (:token interaction)
        payload-opts (:options (:payload interaction))
        request-type (first (keys payload-opts))
        query (s/select-one [request-type :term] payload-opts)]
    (discord/interaction-response id token 5 :ephemeral? true)
    (a/go
      (let [results (->> (a/<! (((search-fn @backend) request-type) query))
                         (into [] (take @discord/max-results)))]
        (swap! discord/cache assoc uuid {:results results
                                         :request-type request-type
                                         :token token
                                         :last-modified (System/currentTimeMillis)})
        (discord/update-interaction-response token (discord/search-response results uuid))))))

(defmulti process-event (fn [event _ _] event))

(defmethod process-event "result-select" [_ interaction uuid]
  (a/go
    (let [{:keys [results request-type token]} (get @discord/cache uuid)
          selection-id (discord/dropdown-index interaction)
          profiles (->> (a/<! (((profiles-fn @backend) request-type)))
                        (into []))
          selection (a/<! (((process-selection-fn @backend) request-type) (nth results selection-id)))]
      (case request-type
        :series (case @backend
                  :overseerr (if (a/<! (ovsr/partial-seasons?))
                               (discord/update-interaction-response token (discord/select-season selection uuid))
                               (do
                                 (swap! discord/cache assoc-in [uuid :season] -1)
                                 (discord/update-interaction-response token (discord/request selection uuid :season -1))))
                  :direct (discord/update-interaction-response token (discord/select-season selection uuid)))
        :movie  (case @backend
                  :overseerr (discord/update-interaction-response token (discord/request selection uuid))
                  :direct (discord/update-interaction-response token (discord/select-profile profiles uuid))))
      (swap! discord/cache assoc-in [uuid :profiles] profiles)
      (swap! discord/cache assoc-in [uuid :selection] selection))))

(defmethod process-event "season-select" [_ interaction uuid]
  (let [{:keys [token selection profiles]} (get @discord/cache uuid)
        season (discord/dropdown-index interaction)]
    (case @backend
      :overseerr (discord/update-interaction-response token (discord/request selection uuid :season season))
      :direct (discord/update-interaction-response token (discord/select-profile profiles uuid)))
    (swap! discord/cache assoc-in [uuid :season] season)))

(defmethod process-event "profile-select" [_ interaction uuid]
  (let [{:keys [token profiles season selection]} (get @discord/cache uuid)
        profile-id (discord/dropdown-index interaction)
        profile (s/select-one [s/ALL (comp (partial = profile-id) :id) :name] profiles)]
    (discord/update-interaction-response token (discord/request selection uuid :season season :profile profile))
    (swap! discord/cache assoc-in [uuid :profile-id] profile-id)))

(defmethod process-event "request" [_ interaction uuid]
  (a/go
    (let [{:keys [token selection season profile request-type profile-id is4k]} (get @discord/cache uuid)
          user-id (:user-id interaction)
          backend-id (a/<! ((account-id-fn @backend) user-id))]
      (if (nil? backend-id)
        (discord/update-interaction-response token (discord/content-response "You do not have an associated account on Overseerr"))
        (case (((content-status-fn @backend) request-type) selection :season season :is4k is4k)
          :pending (discord/update-interaction-response token (discord/content-response "This has been requested, and the request is pending."))
          :processing (discord/update-interaction-response token (discord/content-response "This is currently processing and should be available soon."))
          :available (discord/update-interaction-response token (discord/content-response "This is already available!"))
          (->> (a/<! (((request-fn @backend) request-type)
                      ((request-selection-fn @backend) selection :season season :is4k (boolean is4k))
                      :season season
                      :ovsr-id backend-id
                      :profile-id profile-id))
               (then (fn [_]
                       (discord/update-interaction-response token (discord/content-response "Requested!"))
                       (discord/followup-repsonse token (discord/request-alert selection :season season :profile profile))))
               (else (fn [e]
                       (let [{:keys [status body] :as data} (ex-data e)
                             msg (second (re-matches #"\{\"message\":\"(.+)\"\}" body))] ; Not sure why this JSON didn't get parsed
                         (cond
                           (= status 403) (discord/update-interaction-response token (discord/content-response msg))
                           :else (throw (ex-info "Non 403 error on request" data))))))))))))

(defmethod process-event "request-4k" [_ interaction uuid]
  (swap! discord/cache assoc-in [uuid :is4k] true)
  (process-event "request" interaction uuid))

(defn continue-interaction [interaction]
  (let [[event uuid] (str/split (s/select-one [:payload :component-id] interaction) #":")
        now (System/currentTimeMillis)]
    ; Send the ack
    (discord/interaction-response (:id interaction) (:token interaction) 6)
    ; Check last modified
    (let [{:keys [token last-modified]} (get @discord/cache uuid)]
      (if (> (- now last-modified) discord/channel-timeout)
        ; Update interaction with timeout message
        (discord/update-interaction-response token (discord/content-response "Request timed out, please try again."))
        ; Move through the state machine to update cache side effecting new components
        (do
          (swap! discord/cache assoc-in [uuid :last-modified] now)
          (process-event event interaction uuid))))))
