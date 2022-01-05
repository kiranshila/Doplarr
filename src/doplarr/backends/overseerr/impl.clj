(ns doplarr.backends.overseerr.impl
  (:require
   [taoensso.timbre :refer [fatal]]
   [com.rpl.specter :as s]
   [clojure.core.async :as a]
   [config.core :refer [env]]
   [fmnoise.flow :as flow :refer [then else]]
   [doplarr.utils :as utils]))

(def base-url (delay (str (:overseerr/url env) "/api/v1")))
(def api-key  (delay (:overseerr/api env)))

(def poster-path "https://image.tmdb.org/t/p/w500")

(def status [:unknown :pending :processing :partially-available :available])

(defn GET [endpoint & [params]]
  (utils/http-request :get (str @base-url endpoint) @api-key params))

(defn POST [endpoint & [params]]
  (utils/http-request :post (str @base-url endpoint) @api-key params))

(defn backend-4k? [media-type]
  (a/go
    (->> (a/<! (GET (str "/settings/" (if (= media-type "tv") "sonarr" "radarr"))))
         (then #(->> (:body %)
                     (map :is4k)
                     (some identity)))
         (else #(fatal % "Exception on checking Overseeerr 4K backend support")))))

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
                  (s/submap [:title :id :year])])))

(defn num-users []
  (a/go
    (->> (a/<! (GET "/user" {:query-params {:take 1}}))
         (then #(s/select-one [:body :pageInfo :results] %))
         (else #(fatal % "Exception on querying Overseerr users")))))

(defn all-users []
  (a/go
    (->> (a/<! (GET "/user" {:query-params {:take (a/<! (num-users))}}))
         (then #(->> (s/select-one [:body :results] %)
                     (map :id)
                     (into [])))
         (else #(fatal % "Exception on querying Overseerr users")))))

(defn discord-id [ovsr-id]
  (a/go
    (->> (a/<! (GET (str "/user/" ovsr-id)))
         (then #(s/select-one [:body :settings :discordId] %))
         (else #(fatal % "Exception on querying Overseerr discord id")))))

(defn discord-users []
  (a/go-loop [ids (a/<! (all-users))
              users {}]
    (if (empty? ids)
      users
      (let [id (first ids)]
        (recur (rest ids) (assoc users (a/<! (discord-id id)) id))))))

(defn details
  ([selection] (details (:id selection) (:mediaType selection)))
  ([id media-type]
   (a/go
     (->> (a/<! (GET (str "/" media-type "/" id)))
          (then :body)
          (else #(fatal % "Error requesting details on selection from Overseerr"))))))

(defn series-status [selection & {:keys [is4k]}]
  (when-let [info (:mediaInfo selection)]
    (status (dec ((if is4k :status4k :status) info)))))

(defn season-status [selection & {:keys [season is4k]}]
  (when-let [ss (series-status selection :is4k is4k)]
    (if (= ss :partially-available)
      (when-let [seasons (seq (:seasons (:mediaInfo selection)))]
        (status (dec ((if is4k :status4k :status) (nth seasons (dec season))))))
      ss)))

(defn movie-status [selection & {:keys [is4k]}]
  (when-let [info (:mediaInfo selection)]
    (status (dec ((if is4k :status4k :status) info)))))

(defn selection-to-request [selection & {:keys [season is4k]}]
  (cond-> {:mediaType (:mediaType selection)
           :mediaId (:id selection)
           :is4k is4k}
    (= "tv" (:mediaType selection)) (assoc :seasons (if (= -1 season)
                                                      (into [] (range 1 (inc (:seasonCount selection))))
                                                      [season]))))

(defn selection-to-embedable [selection]
  (as-> selection s
    (assoc s :seasonCount (:numberOfSeasons s))
    (assoc s :description (:overview s))
    (assoc s :remotePoster (str poster-path (:posterPath s)))))

(defn post-process-selection [selection]
  (a/go
    (let [details (a/<! (details selection))
          fourK-backend? (a/<! (backend-4k? (:mediaType selection)))]
      (selection-to-embedable (merge details selection {:backend-4k fourK-backend?})))))

(defn request [body & {:keys [ovsr-id]}]
  (a/go
    (->> (a/<! (POST "/request" {:form-params body
                                 :content-type :json
                                 :headers {"X-API-User" (str ovsr-id)}}))
         (then (constantly nil)))))

(defn partial-seasons? []
  (a/go
    (->> (a/<! (GET "/settings/main"))
         (then #(->> (:body %)
                     :partialRequestsEnabled))
         (else #(fatal % "Exception testing for partial seasons")))))
