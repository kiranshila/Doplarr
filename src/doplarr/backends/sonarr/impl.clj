(ns doplarr.backends.sonarr.impl
  (:require
   [clojure.core.async :as a]
   [config.core :refer [env]]
   [fmnoise.flow :as flow :refer [then else]]
   [doplarr.utils :as utils]))

(def base-url (delay (str (:sonarr-url env) "/api/v3")))
(def api-key  (delay (:sonarr-api env)))

(defn GET [endpoint & [params]]
  (utils/http-request :get (str @base-url endpoint) @api-key params))

(defn POST [endpoint & [params]]
  (utils/http-request :post (str @base-url endpoint) @api-key params))

(defn PUT [endpoint & [params]]
  (utils/http-request :put (str @base-url endpoint) @api-key params))

(def rootfolder (delay (a/<!! (utils/request-and-process-body GET #(get (first %) "path") "/rootfolder"))))

(defn quality-profiles []
  (utils/request-and-process-body
   GET
   #(map utils/process-profile %)
   "/qualityProfile"))

(defn language-profiles []
  (utils/request-and-process-body
   GET
   #(map utils/process-profile %)
   "/languageProfile"))

(defn get-from-tvdb [tvdb-id]
  (utils/request-and-process-body
   GET
   (comp utils/from-camel first)
   "/series/lookup"
   {:query-params {:term (str "tvdbId:" tvdb-id)}}))

(defn get-from-id [id]
  (utils/request-and-process-body
   GET
   utils/from-camel
   (str "/series/" id)))

(defn execute-command [command & {:as opts}]
  (a/go
    (->> (a/<! (POST "/command" {:form-params (merge {:name command} opts)
                                 :content-type :json}))
         (then (constantly nil)))))

(defn search-season [series-id season]
  (a/go
    (->> (a/<! (execute-command "SeasonSearch" {:seriesId series-id
                                                :seasonNumber season})))
    (then (constantly nil))))

(defn search-series [series-id]
  (a/go
    (->> (a/<! (execute-command "SeriesSearch" {:seriesId series-id})))
    (then (constantly nil))))

(defn status [series season]
  (let [ssn (->> (:seasons series)
                 (filter (comp (partial = season) :seasonNumber))
                 first)]
    (when-let [stats (:statistics ssn)]
      (when (:monitored ssn)
        (cond
          (> 100.0 (:percentOfEpisodes stats)) :processing
          :else :available)))))
