(ns doplarr.backends.radarr.impl
  (:require
   [config.core :refer [env]]
   [doplarr.utils :as utils]
   [doplarr.backends.radarr.specs :as specs]
   [clojure.core.async :as a]
   [clojure.spec.alpha :as spec]
   [doplarr.backends.specs :as bs]))

(def base-url (delay (str (:radarr-url env) "/api/v3")))
(def api-key  (delay (:radarr-api env)))

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

(defn status [result]
  (cond
    (and (:has-file result)
         (:is-available result)
         (:monitored result)) :available
    (and (not (:has-file result))
         (:is-available result)
         (:monitored result)) :processing
    :else nil))
(spec/fdef status
  :args (spec/cat :result ::bs/result)
  :ret ::bs/status)

(defn request-payload [result quality-profile-id]
  (-> result
      (select-keys [:title :tmdb-id])
      (assoc :quality-profile-id quality-profile-id
             :monitored true
             :root-folder-path @rootfolder
             :add-options {:search-for-movie true})))
(spec/fdef request-payload
  :args (spec/cat :result ::bs/result
                  :quality-profile-id ::bs/quality-profile-id)
  :ret ::specs/payload)
