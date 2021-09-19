(ns doplarr.sonarr
  (:require
   [com.rpl.specter :as s]
   [clojure.core.async :as a]
   [config.core :refer [env]]
   [doplarr.arr-utils :as utils]))

(def base-url (delay (str (:sonarr-url env) "/api")))
(def api-key  (delay (:sonarr-api env)))
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

(defn PUT [endpoint & [params]]
  (utils/http-request
   :put
   (str @base-url endpoint)
   @api-key
   params))

(defn search [search-term]
  (let [chan (a/promise-chan)]
    (a/pipeline
     1
     chan
     (map :body)
     (GET "/series/lookup" {:query-params {:term search-term}}))
    chan))

(defn quality-profiles []
  (let [chan (a/promise-chan)]
    (a/pipeline
     1
     chan
     (map (comp (partial map utils/quality-profile-data) :body))
     (GET "/profile"))
    chan))

(defn request-options [profile-id]
  (a/go
    {:profileId profile-id
     :monitored true
     :seasonFolder true
     :rootFolderPath (a/<! @rootfolder)
     :addOptions {:searchForMissingEpisodes true}}))

(defn started-aquisition? [series]
  (contains? series :path))

(defn request-all [series profile-id]
  (a/go
    (let [started? (started-aquisition? series)
          series (if started?
                   (s/multi-transform
                    (s/multi-path
                     [:seasons
                      s/ALL
                      (comp pos? :seasonNumber)
                      :monitored
                      (s/terminal-val true)]
                     [:profileId
                      (s/terminal-val profile-id)])
                    series)
                   (merge series (a/<! (request-options profile-id))))]
      ((if started? PUT POST)
       "/series"
       {:form-params series
        :content-type :json})))
  nil)

(defn request-season [series season profile-id]
  (a/go
    (let [started? (started-aquisition? series)
          series (if started?
                   (s/multi-transform
                    (s/multi-path
                     [:seasons
                      s/ALL
                      (comp (partial = season) :seasonNumber)
                      :monitored
                      (s/terminal-val true)]
                     [:profileId
                      (s/terminal-val profile-id)])
                    series)
                   (merge (s/setval [:seasons
                                     s/ALL
                                     (comp (partial not= season) :seasonNumber)
                                     :monitored]
                                    false series)
                          (a/<! (request-options profile-id))))]
      ((if started? PUT POST)
       "/series"
       {:form-params series
        :content-type :json})))
  nil)

(defn request [series & {:keys [season profile-id]}]
  (if (= -1 season)
    (request-all series profile-id)
    (request-season series season profile-id)))

(defn post-process-series [series]
  (a/go
    (if-let [id (:id series)]
      (merge series (:body (a/<! (GET (str "/series/" id)))))
      series)))

(defn season-status [series & {:keys [season]}]
  (let [ssn (->> (:seasons series)
                 (filter (comp (partial = season) :seasonNumber))
                 first)]
    (when-let [stats (:statistics ssn)]
      (when (:monitored ssn)
        (cond
          (> 100.0 (:percentOfEpisodes stats)) :processing
          :else :available)))))
