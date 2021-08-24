(ns doplarr.overseerr
  (:require
   [com.rpl.specter :as s]
   [clojure.core.async :as a]
   [config.core :refer [env]]
   [fmnoise.flow :as flow :refer [then else]]
   [doplarr.arr-utils :as utils]))

(def base-url (delay (str (:overseerr-url env) "/api/v1")))
(def api-key  (delay (:overseerr-api env)))

(def tmdb-poster-path "https://image.tmdb.org/t/p/original")

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

(defn all-users []
  (a/go
    (->> (a/<! (GET "/user"))
         (then (comp :results :body))
         (else utils/fatal-error))))

(defn user-discord-id [id]
  (a/go
    (->> (a/<! (GET (str "/user/" id "/settings/notifications")))
         (then (comp :discordId :body))
         (else utils/fatal-error))))

(defn discord-users []
  (a/go-loop [ids (map :id (a/<! (all-users)))
              users {}]
    (if (empty? ids)
      users
      (let [id (first ids)]
        (recur (rest ids) (assoc users (a/<! (user-discord-id id)) id))))))

(defn search [term media-type]
  (a/go
    (->> (a/<! (GET (str "/search?query=" term)))
         (then (fn [resp] (s/select-one [:body
                                         :results
                                         (s/filterer :mediaType (s/pred= media-type))
                                         (s/transformed s/ALL #(assoc % :year (.getYear
                                                                               (java.time.LocalDate/parse
                                                                                (if (empty? (:releaseDate %))
                                                                                  "0000-01-01"
                                                                                  (:releaseDate %))))))]
                                        resp)))
         (else utils/fatal-error))))

(defn search-movie [term]
  (search term "movie"))

(defn search-series [term]
  (search term "tv"))

(defn result-to-request [user-id result]
  {:mediaType (:mediaType result)
   :mediaId (:id result)
   :userId user-id})

(defn selection-to-embedable [selection]
  (as-> selection s
    (assoc s :description (:overview s))
    (assoc s :remotePoster (str tmdb-poster-path (:posterPath s)))))

(defn request [body & {:keys [season]}]
  (a/go
    (->> (a/<! (POST "/request" {:form-params body
                                 :content-type :json}))
         (then (constantly nil)))))
