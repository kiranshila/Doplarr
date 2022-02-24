(ns doplarr.backends.sonarr.impl
  (:require
   [clojure.core.async :as a]
   [doplarr.state :as state]
   [doplarr.utils :as utils]
   [fmnoise.flow :as flow :refer [then]]))

(def base-url (delay (str (:sonarr/url @state/config) "/api/v3")))
(def api-key  (delay (:sonarr/api @state/config)))

(defn GET [endpoint & [params]]
  (utils/http-request :get (str @base-url endpoint) @api-key params))

(defn POST [endpoint & [params]]
  (utils/http-request :post (str @base-url endpoint) @api-key params))

(defn PUT [endpoint & [params]]
  (utils/http-request :put (str @base-url endpoint) @api-key params))

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

(defn rootfolders []
  (utils/request-and-process-body
   GET
   utils/process-rootfolders
   "/rootfolder"))

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

(defn status [details season]
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

(defn generate-seasons [request-seasons total-seasons]
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

(defn request-payload [payload details]
  (let [seasons (-> (generate-request-seasons details (:season payload))
                    (generate-seasons (count (:seasons details))))]
    (if (:id payload)
      (assoc details
             :seasons seasons
             :quality-profile-id (:quality-profile-id payload))
      (-> payload
          (assoc :monitored true
                 :seasons seasons
                 :add-options {:ignore-episodes-with-files true
                               :search-for-missing-episodes true})
          (dissoc :season
                  :format)))))
