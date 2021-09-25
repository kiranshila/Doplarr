(ns doplarr.overseerr
  (:require
   [com.rpl.specter :as s]
   [clojure.core.async :as a]
   [config.core :refer [env]]
   [fmnoise.flow :as flow :refer [then else]]
   [doplarr.arr-utils :as utils]))

(def base-url (delay (str (:overseerr-url env) "/api/v1")))
(def api-key  (delay (:overseerr-api env)))

(def poster-path "https://image.tmdb.org/t/p/w500")

(def status [:unknown :pending :processing :partially-available :available])

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

(defn backend-4k? [media-type]
  (a/go
    (->> (a/<! (GET (str "/settings/" (if (= media-type "tv") "sonarr" "radarr"))))
         (then #(->> (:body %)
                     (map :is4k)
                     (some identity)))
         (else utils/fatal-error))))

(defn search [term media-type]
  (a/go
    (->> (a/<! (GET (str "/search?query=" term)))
         (then (fn [resp] (s/select-one [:body
                                         :results
                                         (s/filterer :mediaType (s/pred= media-type))
                                         (s/transformed s/ALL #(assoc % :year (.getYear
                                                                               (java.time.LocalDate/parse
                                                                                (if (empty? (or (:firstAirDate %)
                                                                                                (:releaseDate %)))
                                                                                  "0000-01-01"
                                                                                  (or (:firstAirDate %)
                                                                                      (:releaseDate %)))))))]
                                        resp)))
         (else utils/fatal-error))))

(defn num-users []
  (a/go
    (->> (a/<! (GET "/user" {:query-params {:take 1}}))
         (then #(s/select-one [:body :pageInfo :results] %))
         (else utils/fatal-error))))

(defn all-users []
  (a/go
    (->> (a/<! (GET "/user" {:query-params {:take (a/<! (num-users))}}))
         (then #(->> (s/select-one [:body :results] %)
                     (map :id)
                     (into [])))
         (else utils/fatal-error))))

(defn discord-id [ovsr-id]
  (a/go
    (->> (a/<! (GET (str "/user/" ovsr-id)))
         (then #(s/select-one [:body :settings :discordId] %))
         (else utils/fatal-error))))

(defn discord-users []
  (a/go-loop [ids (a/<! (all-users))
              users {}]
    (if (empty? ids)
      users
      (let [id (first ids)]
        (recur (rest ids) (assoc users (a/<! (discord-id id)) id))))))

(defn search-movie [term]
  (search term "movie"))

(defn search-series [term]
  (search term "tv"))

(defn details
  ([selection] (details (:id selection) (:mediaType selection)))
  ([id media-type]
   (a/go
     (->> (a/<! (GET (str "/" media-type "/" id)))
          (then :body)
          (else utils/fatal-error)))))

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
         (else utils/fatal-error))))
