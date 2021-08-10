(ns doplarr.radarr
  (:require
   [config.core :refer [env]]
   [doplarr.arr-utils :refer [http-request rootfolder]]))

(def endpoint (str (:url (:radarr env)) "/api/v3"))

(defn search [search-term]
  (:body (http-request
          :get
          (str endpoint "/movie/lookup")
          (:api-key (:radarr env))
          {:query-params {:term search-term}})))

(defn request [movie]
  (http-request
   :post
   (str endpoint "/movie")
   (:api-key (:radarr env))
   {:form-params (merge movie {:qualityProfileId 1
                               :monitored true
                               :minimumAvailability "announced"
                               :rootFolderPath (rootfolder endpoint (:api-key (:radarr env)))
                               :addOptions {:searchForMovie true}})
    :content-type :json}))
