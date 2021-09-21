(ns doplarr.radarr
  (:require
   [config.core :refer [env]]
   [doplarr.arr-utils :as utils]
   [clojure.core.async :as a]))

(def base-url (delay (str (:radarr-url env) "/api/v3")))
(def api-key  (delay (:radarr-api env)))
(def rootfolder (delay (utils/rootfolder @base-url @api-key)))

(defn GET [endpoint & [params]]
  (utils/http-request
   :get
   (str @base-url endpoint)
   @api-key
   params))

(defn POST [endpoint & [params]]
  (utils/http-request
   :post
   (str @base-url endpoint)
   @api-key
   params))

(defn search [search-term]
  (let [chan (a/promise-chan)]
    (a/pipeline
     1
     chan
     (map :body)
     (GET "/movie/lookup" {:query-params {:term search-term}}))
    chan))

(defn quality-profiles []
  (let [chan (a/promise-chan)]
    (a/pipeline
     1
     chan
     (map (comp (partial map utils/quality-profile-data) :body))
     (GET "/qualityProfile"))
    chan))

(defn request [movie & {:keys [profile-id]}]
  (a/go
    (POST
      "/movie"
      {:form-params (merge movie
                           {:qualityProfileId profile-id
                            :monitored true
                            :minimumAvailability "announced"
                            :rootFolderPath (a/<! @rootfolder)
                            :addOptions {:searchForMovie true}})
       :content-type :json})))

(defn movie-status [movie & _]
  (cond
    (and (:hasFile movie)
         (:isAvailable movie)
         (:monitored movie)) :available
    (and (not (:hasFile movie))
         (:isAvailable movie)
         (:monitored movie)) :processing
    :else nil))
