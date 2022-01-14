(ns doplarr.backends.overseerr.impl
  (:require
   [clojure.core.async :as a]
   [clojure.set :as set]
   [com.rpl.specter :as s]
   [doplarr.state :as state]
   [doplarr.utils :as utils]
   [fmnoise.flow :as flow :refer [else then]]
   [taoensso.timbre :refer [fatal]]))

(def base-url (delay (str (:overseerr/url @state/config) "/api/v1")))
(def api-key  (delay (:overseerr/api @state/config)))

(def poster-path "https://image.tmdb.org/t/p/w500")

(def status [:unknown :pending :processing :partially-available :available])

(defn GET [endpoint & [params]]
  (utils/http-request :get (str @base-url endpoint) @api-key params))

(defn POST [endpoint & [params]]
  (utils/http-request :post (str @base-url endpoint) @api-key params))

(defn parse-year [result]
  (.getYear (java.time.LocalDate/parse
             (if (empty? (or (:first-air-date result)
                             (:release-date result)))
               "0000-01-01"
               (or (:first-air-date result)
                   (:release-date result))))))

(defn process-search-result [media-type-str body]
  (->> (utils/from-camel body)
       (s/select [:results
                  s/ALL
                  (s/selected? :media-type (s/pred= media-type-str))
                  (s/view #(assoc % :year (parse-year %)))
                  (s/submap [:title :id :year :name])])
       (map #(set/rename-keys % {:name :title}))))

(defn media-type [kw]
  (if (= :series kw)
    "tv"
    (name kw)))

(defn details [id media]
  (a/go
    (->> (a/<! (GET (str "/" (media-type media) "/" id)))
         (then (comp :body utils/from-camel))
         (else #(fatal % "Error requesting details on selection from Overseerr")))))

(defn seasons-list [details]
  (conj
   (for [season (:seasons details)
         :let [ssn (:season-number season)]
         :when (> ssn 0)]
     {:name (str ssn)
      :id ssn})
   {:name "All Seasons" :id -1}))

(defn backend-4k? [media]
  (a/go
    (->> (a/<! (GET (str "/settings/" (if (= (media-type media) "tv") "sonarr" "radarr"))))
         (then #(->> (utils/from-camel (:body %))
                     (map :is-4k)
                     (some identity)))
         (else #(fatal % "Exception on checking Overseeerr 4K backend support")))))

(defn partial-seasons? []
  (a/go
    (->> (a/<! (GET "/settings/main"))
         (then #(->> (utils/from-camel (:body %))
                     :partial-requests-enabled))
         (else #(fatal % "Exception testing for partial seasons")))))

(defn media-status [details media-type & {:keys [is-4k? season]}]
  (when-let [info (:media-info details)]
    (let [primary-status (status (dec ((if is-4k? :status-4k :status) info)))]
      (case media-type
        :movie primary-status
        :series (if (or (= -1 season)
                        (not= primary-status :partially-available))
                  primary-status
                  (when-let [seasons (seq (:seasons info))]
                    (status (dec ((if is-4k? :status-4k :status) (nth seasons (dec season)))))))))))

(defn num-users []
  (a/go
    (->> (a/<! (GET "/user" {:query-params {:take 1}}))
         (then #(->> (utils/from-camel %)
                     (s/select-one [:body :page-info :results])))
         (else #(fatal % "Exception on querying Overseerr users")))))

(defn all-users []
  (a/go
    (->> (a/<! (GET "/user" {:query-params {:take (a/<! (num-users))}}))
         (then #(->> (utils/from-camel %)
                     (s/select-one [:body :results])
                     (map :id)
                     (into [])))
         (else #(fatal % "Exception on querying Overseerr users")))))

(defn discord-id [ovsr-id]
  (a/go
    (->> (a/<! (GET (str "/user/" ovsr-id)))
         (then #(->> (utils/from-camel %)
                     (s/select-one [:body :settings :discord-id])))
         (else #(fatal % "Exception on querying Overseerr discord id")))))

(defn discord-users []
  (a/go-loop [ids (a/<! (all-users))
              users {}]
    (if (empty? ids)
      users
      (let [id (first ids)]
        (recur (rest ids) (assoc users (a/<! (discord-id id)) id))))))
