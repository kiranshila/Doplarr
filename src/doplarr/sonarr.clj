(ns doplarr.sonarr
  (:require
   [config.core :refer [env]]
   [doplarr.arr-utils :refer [http-request rootfolder quality-profile-data]]))

(defn endpoint [] (str (:sonarr-url env) "/api"))

(defn quality-profiles []
  (map quality-profile-data
       (:body (http-request
               :get
               (str (endpoint) "/profile")
               (:sonarr-api env)))))

(defn determine-quality-profile []
  (or (:sonarr-quality-id env)
      (->> (quality-profiles)
           (sort-by :id)
           first
           :id)))

(defn default-options []
  {:profileId (determine-quality-profile)
   :monitored true
   :seasonFolder true
   :rootFolderPath (rootfolder (endpoint) (:sonarr-api env))
   :addOptions {:searchForMissingEpisodes true}})

(defn search [search-term]
  (:body (http-request
          :get
          (str (endpoint) "/series/lookup")
          (:sonarr-api env)
          {:query-params {:term search-term}})))

(defn started-aquisition? [series]
  (contains? series :path))

(defn aquired-season? [series season]
  (and (started-aquisition? series)
       (get-in series [:seasons season :monitored])))

(defn missing-seasons [series]
  (if (started-aquisition? series)
    (map :seasonNumber (filter (complement :monitored) (rest (:seasons series))))
    (rest (range (:seasonCount series)))))

(defn aquired-all-seasons? [series]
  (empty? (missing-seasons series)))

(defn aquired-specials? [series]
  (get-in series [:seasons 0 :monitored]))

(defn request-all [series]
  (http-request
   :post
   (str (endpoint) "/series")
   (:sonarr-api env)
   {:form-params (merge series (default-options))
    :content-type :json}))

(defn request-season [series season]
  (let [started? (started-aquisition? series)
        series (if started?
                 (assoc-in series [:seasons season :monitored] true)
                 (merge (->> (for [ssn (range (inc (:seasonCount series)))]
                               {:seasonNumber ssn
                                :monitored (= ssn season)})
                             (into [])
                             (assoc series :seasons))
                        (default-options)))]
    (http-request
     (if started? :put :post)
     (str (endpoint) "/series")
     (:sonarr-api env)
     {:form-params series
      :content-type :json})))

(defn request-specials [series]
  (http-request
   :post
   (str (endpoint) "/series")
   (:sonarr-api env)
   {:form-params (merge
                  (assoc-in series [:seasons 0 :monitored] true)
                  (default-options))
    :content-type :json}))
