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

(defn details [id media-type]
  (a/go
    (->> (a/<! (GET (str (if (= "tv" media-type)
                           "/tv/"
                           "/movie/")
                         id)))
         (then :body)
         (else utils/fatal-error))))

(defn season-requested? [selection season & {:keys [is4k]}]
  (when-let [info (:mediaInfo selection)]
    ; Seasons exist, check if the selected season exists
    (let [seasons (:seasons info)]
      (if (empty? seasons)
        true
        (= 5 ((if is4k :status4k :status) (nth seasons (dec season))))))))

(defn movie-requested? [selection & {:keys [is4k]}]
  (when-let [info (:mediaInfo selection)]
    (= 5 ((if is4k :status4k :status) info))))

(defn selection-requested? [selection & {:keys [season is4k]}]
  (case (:mediaType selection)
    "tv" (season-requested? selection season :is4k is4k)
    "movie" (movie-requested? selection :is4k is4k)))

(defn selection-to-request [selection & {:keys [season is4k]}]
  (cond-> {:mediaType (:mediaType selection)
           :mediaId (:id selection)
           :is4k is4k}
    (= "tv" (:mediaType selection)) (assoc :seasons (if (= -1 season)
                                                      (into [] (range 1 (:seasonCount selection)))
                                                      [season]))))

(defn selection-to-embedable [selection]
  (as-> selection s
    (assoc s :seasonCount (:numberOfSeasons s))
    (assoc s :description (:overview s))
    (assoc s :remotePoster (str poster-path (:posterPath s)))))

(defn request [body user-id]
  (a/go
    (->> (a/<! (POST "/request" {:form-params body
                                 :content-type :json
                                 :headers {"X-API-User" (str user-id)}}))
         (then (constantly nil)))))
