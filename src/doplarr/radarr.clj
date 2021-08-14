(ns doplarr.radarr
  (:require
   [config.core :refer [env]]
   [doplarr.arr-utils :refer [http-request rootfolder quality-profile-data]]))

(defn endpoint [] (str (:radarr-url env) "/api/v3"))

(defn search [search-term]
  (:body (http-request
          :get
          (str (endpoint) "/movie/lookup")
          (:radarr-api env)
          {:query-params {:term search-term}})))

(defn quality-profiles []
  (map quality-profile-data
       (:body (http-request
               :get
               (str (endpoint) "/qualityProfile")
               (:radarr-api env)))))

(defn determine-quality-profile []
  (or (:radarr-quality-id env)
      (->> (quality-profiles)
           (sort-by :id)
           first
           :id)))

(defn request [movie]
  (http-request
   :post
   (str (endpoint) "/movie")
   (:radarr-api env)
   {:form-params (merge movie {:qualityProfileId (determine-quality-profile)
                               :monitored true
                               :minimumAvailability "announced"
                               :rootFolderPath (rootfolder (endpoint) (:radarr-api env))
                               :addOptions {:searchForMovie true}})
    :content-type :json}))
