(ns doplarr.radarr
  (:require
   [config.core :refer [env]]
   [doplarr.arr-utils :as utils]
   [fmnoise.flow :as flow :refer [then else]]
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

(defn search [term]
  (a/go
    (->> (a/<! (GET "/movie/lookup" {:query-params {:term term}}))
         (then :body)
         (else utils/fatal-error))))

(defn quality-profiles []
  (a/go
    (->> (a/<! (GET "/qualityProfile"))
         (then #(->> (:body %)
                     (map utils/quality-profile-data)))
         (else utils/fatal-error))))

(defn request [movie & {:keys [profile-id]}]
  (a/go
    (->> (a/<! (POST
                 "/movie"
                 {:form-params (merge movie
                                      {:qualityProfileId profile-id
                                       :monitored true
                                       :minimumAvailability "announced"
                                       :rootFolderPath (a/<! @rootfolder)
                                       :addOptions {:searchForMovie true}})
                  :content-type :json}))
         (then (constantly nil)))))

(defn movie-status [movie & _]
  (cond
    (and (:hasFile movie)
         (:isAvailable movie)
         (:monitored movie)) :available
    (and (not (:hasFile movie))
         (:isAvailable movie)
         (:monitored movie)) :processing
    :else nil))
