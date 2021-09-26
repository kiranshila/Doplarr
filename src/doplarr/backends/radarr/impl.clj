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

(def rootfolder (delay (a/<!! (utils/request-and-process-body GET #(get (first %) "path") "/rootfolder"))))

(defn search [term]
  (utils/request-and-process-body
   GET
   #(into [] (map utils/process-search-result %))
   "/movie/lookup"
   {:query-params {:term term}}))

(defn quality-profiles []
  (utils/request-and-process-body
   GET
   #(into [] (map utils/process-quality-profile %))
   "/qualityProfile"))

(defn status [movie & _]
  (cond
    (and (:has-file movie)
         (:is-available movie)
         (:monitored movie)) :available
    (and (not (:has-file movie))
         (:is-available movie)
         (:monitored movie)) :processing
    :else nil))

(defn request-payload [result quality-profile-id]
  (merge result
         {:quality-profile-id quality-profile-id
          :monitored true
          :rootFolderPath @rootfolder
          :add-options {:search-for-movie true}}))

(spec/fdef request-payload :args (spec/cat :arg ::bs/result :arg ::bs/quality-profile-id))

(defn request [payload]
  (utils/request-and-process-body
   POST
   (constantly nil)
   "/movie"
   {:form-params (utils/to-camel payload)
    :content-type :json}))

(spec/fdef request :args (spec/cat :arg ::specs/payload))

(def payload {:title "Making 'The Matrix'"
              :tmdb-id 684431
              :quality-profile-id 1
              :root-folder-path (a/<!! (rootfolder))})
