(ns doplarr.backends.overseerr
  (:require
   [doplarr.backends.specs :as bs]
   [doplarr.backends.overseerr.impl :as impl]
   [doplarr.backends.overseerr.specs :as specs]
   [clojure.spec.alpha :as spec]))

(defn search [term])
(spec/fdef search
  :args (spec/cat :term string?))

(defn request [payload])
(spec/fdef request
  :args (spec/cat :payload ::specs/payload))

(defn additional-options [result])
(spec/fdef additional-options
  :args (spec/cat :result ::bs/result))

(defn request-formats [] ["" "4K"])
