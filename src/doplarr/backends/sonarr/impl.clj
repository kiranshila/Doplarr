(ns doplarr.backends.sonarr.impl
  (:require
   [clojure.core.async :as a]
   [orchestra.core :refer [defn-spec]]
   [config.core :refer [env]]
   [fmnoise.flow :as flow :refer [then]]
   [doplarr.utils :as utils]
   [doplarr.backends.sonarr.specs :as specs]
   [doplarr.backends.specs :as bs]))

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

(defn-spec status ::bs/status
  [details any? season int?]
  (if (= -1 season)
    nil ; FIXME All season status
    (let [ssn (->> (:seasons details)
                   (filter (comp (partial = season) :season-number))
                   first)]
      (when-let [stats (:statistics ssn)]
        (when (:monitored ssn)
          (cond
            (> 100.0 (:percent-of-episodes stats)) :processing
            :else :available))))))

(defn-spec generate-seasons ::bs/seasons
  [request-seasons set? total-seasons pos-int?]
  (into []
        (conj
         (for [season (range 1 (inc total-seasons))]
           {:season-number season
            :monitored (contains? request-seasons season)})
         {:season-number 0 :monitored false})))

(defn generate-request-seasons
  [details season]
  (if (= -1 season)
    (into #{} (range 1 (inc (count (:seasons details)))))
    (if (:id details)
      (conj (->> (:seasons details)
                 (keep #(when (:monitored %) (:season-number %)))
                 (into #{}))
            season)
      #{season})))

(defn-spec request-payload ::specs/request-payload
  [payload ::specs/prepared-payload details any?]
  (let [seasons (-> (generate-request-seasons details (:season payload))
                    (generate-seasons (count (:seasons details))))]
    (if (:id payload)
      (assoc details
             :seasons seasons
             :quality-profile-id (:quality-profile-id payload))
      (-> payload
          (assoc :monitored true
                 :seasons seasons
                 :root-folder-path @rootfolder
                 :add-options {:ignore-episodes-with-files true
                               :search-for-missing-episodes true})
          (dissoc :season
                  :format)))))
