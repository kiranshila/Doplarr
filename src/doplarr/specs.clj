(ns doplarr.specs
  (:require
   [clojure.spec.alpha :as spec]))

(spec/def ::result-name string?)
(spec/def ::result-year int?)
(spec/def ::result-id int?)

(spec/def ::search-result (spec/keys :req []))
