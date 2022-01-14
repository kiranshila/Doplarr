(ns doplarr.backends.radarr.impl
  (:require
   [clojure.core.async :as a]
   [doplarr.state :as state]
   [doplarr.utils :as utils]))

(def base-url (delay (str (:radarr/url @state/config) "/api/v3")))
(def api-key  (delay (:radarr/api @state/config)))

(defn GET [endpoint & [params]]
  (utils/http-request :get (str @base-url endpoint) @api-key params))

(defn POST [endpoint & [params]]
  (utils/http-request :post (str @base-url endpoint) @api-key params))

(def rootfolder (delay (a/<!! (utils/request-and-process-body GET #(get (first %) "path") "/rootfolder"))))

(defn quality-profiles []
  (utils/request-and-process-body
   GET
   #(map utils/process-profile %)
   "/qualityProfile"))

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
      (select-keys [:title :tmdb-id :quality-profile-id])
      (assoc :monitored true
             :root-folder-path @rootfolder
             :add-options {:search-for-movie true})))
