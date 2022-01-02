(ns doplarr.backends.readarr.impl
  (:require
   [orchestra.core :refer [defn-spec]]
   [config.core :refer [env]]
   [doplarr.utils :as utils]
   [clojure.core.async :as a]
   [doplarr.backends.specs :as bs]))

(def base-url (delay (str (:readarr-url env) "/api/v1")))
(def api-key  (delay (:readarr-api env)))

(defn GET [endpoint & [params]]
  (utils/http-request :get (str @base-url endpoint) @api-key params))

(defn POST [endpoint & [params]]
  (utils/http-request :post (str @base-url endpoint) @api-key params))

(def rootfolder (delay (a/<!! (utils/request-and-process-body GET #(get (first %) "path") "/rootfolder"))))

(defn filter-books [search-results]
  (->> search-results
       (map #(select-keys % [:title :foreign-book-id :author]))
       (map #(assoc % :author (get-in % [:author :author-name])))))

(defn quality-profiles []
  (utils/request-and-process-body
   GET
   #(map utils/process-profile %)
   "/qualityprofile"))

(defn metadata-profiles []
  (utils/request-and-process-body
   GET
   #(map utils/process-profile %)
   "/metadataprofile"))

(defn get-from-id [id])
