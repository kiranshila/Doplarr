(ns doplarr.backends.radarr
  (:require
   [clojure.core.async :as a]
   [doplarr.backends.radarr.impl :as impl]
   [doplarr.state :as state]
   [doplarr.utils :as utils]
   [fmnoise.flow :refer [then]]
   [taoensso.timbre :refer [warn]]))

(defn search [term _]
  (utils/request-and-process-body
   impl/GET
   #(map utils/process-search-result %)
   "/movie/lookup"
   {:query-params {:term term}}))

(defn additional-options [_ _]
  (a/go
    (let [quality-profiles (a/<! (impl/quality-profiles))
          {:keys [radarr/quality-profile]} @state/config
          default-profile-id (utils/profile-name-id quality-profiles quality-profile)]
      (when (and quality-profile (nil? default-profile-id))
        (warn "Default quality profile in config doesn't exist in backend, check spelling"))
      {:quality-profile-id
       (cond
         default-profile-id             default-profile-id
         (= 1 (count quality-profiles)) (:id (first quality-profiles))
         :else quality-profiles)})))

(defn request-embed [{:keys [title quality-profile-id tmdb-id]} _]
  (a/go
    (let [quality-profiles (a/<! (impl/quality-profiles))
          details (a/<! (impl/get-from-tmdb tmdb-id))]
      {:title title
       :overview (:overview details)
       :poster (:remote-poster details)
       :media-type :movie
       :request-formats [""]
       :quality-profile (utils/profile-id-name quality-profiles quality-profile-id)})))

(defn request [payload _]
  (a/go
    (let [status (impl/status (a/<! (impl/get-from-tmdb (:tmdb-id payload))))]
      (if status
        status
        (->> (a/<! (impl/POST "/movie" {:form-params (utils/to-camel (impl/request-payload payload))
                                        :content-type :json}))
             (then (constantly nil)))))))
