(ns doplarr.backends.overseerr
  (:require
   [doplarr.backends.overseerr.impl :as impl]
   [doplarr.utils :as utils]
   [clojure.core.async :as a]))

(defn search [term media-type]
  (let [type (impl/media-type media-type)]
    (utils/request-and-process-body
     impl/GET
     (partial impl/process-search-result type)
     (str "/search?query=" term))))

; In Overseerr, the only additional option we'll need is which season,
; if the request type is a series
(defn additional-options [result media-type]
  (a/go
    (let [type (impl/media-type media-type)
          details (a/<! (impl/details (:id result) type))]
      (when (= media-type :series)
        {:season (impl/seasons-list details)}))))

(defn request-embed [] ["" "4K"])

(defn request [payload])
