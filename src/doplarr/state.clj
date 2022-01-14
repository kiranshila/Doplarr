(ns doplarr.state
  (:require
   [clojure.core.cache.wrapped :as cache]))

(def cache (cache/ttl-cache-factory {} :ttl 900000))

(def discord (atom nil))

(def config (atom nil))
