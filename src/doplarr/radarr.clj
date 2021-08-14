(ns doplarr.radarr
  (:require
   [config.core :refer [env]]
   [doplarr.arr-utils :refer [http-request rootfolder]]))

(def endpoint (str (:radarr-url env) "/api/v3"))

(defn search [search-term]
  (:body (http-request
          :get
          (str endpoint "/movie/lookup")
          (:radarr-api env)
          {:query-params {:term search-term}})))

(defn quality-profiles []
  (http-request
   :get
   (:str endpoint "qualityProfile")
   (:radarr-api env)))

(defn request [movie]
  (http-request
   :post
   (str endpoint "/movie")
   (:radarr-api env)
   {:form-params (merge movie {:qualityProfileId 1
                               :monitored true
                               :minimumAvailability "announced"
                               :rootFolderPath (rootfolder endpoint (:radarr-api env))
                               :addOptions {:searchForMovie true}})
    :content-type :json}))
