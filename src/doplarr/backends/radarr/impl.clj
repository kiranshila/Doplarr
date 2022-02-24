(ns doplarr.backends.radarr.impl
  (:require
   [doplarr.state :as state]
   [doplarr.utils :as utils]))

(def base-url (delay (str (:radarr/url @state/config) "/api/v3")))
(def api-key  (delay (:radarr/api @state/config)))

(defn GET [endpoint & [params]]
  (utils/http-request :get (str @base-url endpoint) @api-key params))

(defn POST [endpoint & [params]]
  (utils/http-request :post (str @base-url endpoint) @api-key params))

(defn quality-profiles []
  (utils/request-and-process-body
   GET
   #(map utils/process-profile %)
   "/qualityProfile"))

(defn rootfolders []
  (utils/request-and-process-body
   GET
   utils/process-rootfolders
   "/rootfolder"))

(defn tags []
  (utils/request-and-process-body
   GET
   utils/process-tags
   "/tag"))

(defn get-from-tmdb [tmdb-id]
  (utils/request-and-process-body
   GET
   (comp utils/from-camel first)
   "/movie/lookup"
   {:query-params {:term (str "tmdbId:" tmdb-id)}}))

(defn status [details]
  (cond
    (and (:has-file details)
         (:is-available details)
         (:monitored details)) :available
    (and (not (:has-file details))
         (:is-available details)
         (:monitored details)) :processing
    :else nil))

(defn request-payload [payload]
  (-> payload
      (select-keys [:title :tmdb-id :quality-profile-id :root-folder-path])
      (assoc :monitored true
             :add-options {:search-for-movie true})))
