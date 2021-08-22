(ns doplarr.overseerr
  (:require
   [com.rpl.specter :as s]
   [clojure.core.async :as a]
   [config.core :refer [env]]
   [doplarr.arr-utils :as utils]))

(def base-url (delay (str (:overseerr-url env) "/api/v1")))
(def api-key  (delay (:overseerr-api env)))
(def rootfolder (delay (utils/rootfolder @base-url @api-key)))

; Test if overseerr is enabled
; Find discord user in overseer
; Somehow determine if the user is able to actually perform the request
; Feed that information back to to the bot
; Make screen for permission error

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
  (let [chan (a/promise-chan)]
    (a/pipeline
     1
     chan
     (s/traverse-all [:body :results (s/filterer :mediaType (s/pred= media-type))])
     (GET (str "/search?query=" term))) ; This is a hack, due to Overseerr not playing well with "properly" encoded spaces
    chan))

(defn search-movie [term]
  (search term "movie"))

(defn search-series [term]
  (search term "tv"))

(defn result-to-request [user-id result]
  {:mediaType (:mediaType result)
   :mediaId (:id result)
   :userId user-id})

(defn request [body]
  (a/go
    (POST
      "/request"
      {:form-params body
       :content-type :json}))
  nil)
