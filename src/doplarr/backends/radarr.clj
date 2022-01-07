(ns doplarr.backends.radarr
  (:require
   [taoensso.timbre :refer [warn]]
   [config.core :refer [env]]
   [fmnoise.flow :refer [then]]
   [doplarr.utils :as utils]
   [doplarr.backends.radarr.impl :as impl]
   [clojure.core.async :as a]))

(defn search [term _]
  (utils/request-and-process-body
   impl/GET
   #(map utils/process-search-result %)
   "/movie/lookup"
   {:query-params {:term term}}))

(defn additional-options [_ _]
  (a/go
    (let [quality-profiles (a/<! (impl/quality-profiles))
          {:keys [radarr/quality-profile]} env
          default-profile-id (utils/profile-name-id quality-profiles quality-profile)]
      (when (and quality-profile (nil? default-profile-id))
        (warn "Default quality profile in config doesn't exist in backend, check spelling"))
      {:quality-profile-id
       (cond
         default-profile-id             default-profile-id
         (= 1 (count quality-profiles)) (:id (first quality-profiles))
         :else quality-profiles)})))

(defn request-embed [{:keys [title quality-profile-id tmdb-id]}]
  (a/go
    (let [quality-profiles (a/<! (impl/quality-profiles))
          details (a/<! (impl/get-from-tmdb tmdb-id))]
      {:title title
       :overview (:overview details)
       :poster (:remote-poster details)
       :media-type :movie
       :request-formats [""]
       :quality-profile (utils/profile-id-name quality-profiles quality-profile-id)})))

(defn request [payload]
  (a/go
    (let [status (impl/status (a/<! (impl/get-from-tmdb (:tmdb-id payload))))]
      (if status
        status
        (->> (a/<! (impl/POST "/movie" {:form-params (utils/to-camel (impl/request-payload payload))
                                        :content-type :json}))
             (then (constantly nil)))))))
