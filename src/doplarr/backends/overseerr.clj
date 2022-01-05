(ns doplarr.backends.overseerr
  (:require
   [doplarr.backends.overseerr.impl :as impl]
   [doplarr.utils :as utils]))

(defn search [term media-type]
  (utils/request-and-process-body
   impl/GET
   (partial impl/process-search-result (name media-type))
   (str "/search?query=" term)))

(defn additional-options [result])

(defn request-embed [] ["" "4K"])

(defn request [payload])
