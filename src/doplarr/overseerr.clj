(ns doplarr.overseerr
  (:require
   [com.rpl.specter :as s]
   [clojure.core.async :as a]
   [config.core :refer [env]]
   [doplarr.arr-utils :as utils]
   [clojure.set :as set]))

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
  (let [chan (a/promise-chan)]
    (a/pipeline
     1
     chan
     (map (comp :results :body))
     (GET "/user"))
    chan))

(defn user-discord-id [id]
  (let [chan (a/promise-chan)]
    (a/pipeline
     1
     chan
     (map (comp :discordId :body))
     (GET (str "/user/" id "/settings/notifications")))
    chan))

(defn discord-users []
  (a/go-loop [ids (map :id (a/<! (all-users)))
              users {}]
    (if (empty? ids)
      users
      (let [id (first ids)]
        (recur (rest ids) (assoc users (a/<! (user-discord-id id)) id))))))

(defn search [term media-type]
  (a/go
    (s/select-one [:body
                   :results
                   (s/filterer :mediaType (s/pred= media-type))
                   (s/transformed s/ALL #(assoc % :year (.getYear (java.time.LocalDate/parse (:releaseDate % "0000-01-01")))))]
                  (a/<! (GET (str "/search?query=" term))))))

(defn search-movie [term]
  (search term "movie"))

(defn search-series [term]
  (search term "tv"))

(defn result-to-request [user-id result]
  {:mediaType (:mediaType result)
   :mediaId (:id result)
   :userId user-id})

(defn selection-to-embedable [selection]
  (->> (assoc selection :description (:overview selection))
       (#(assoc % :remotePoster (str tmdb-poster-path (:posterPath %))))))

(defn request [body]
  (a/go
    (let [resp (a/<!
                (POST
                  "/request"
                  {:form-params body
                   :throw-exceptions? false
                   :content-type :json}))]
      (when (= (:status resp) 403)
        resp))))
