(ns doplarr.backends.specs
  (:require [clojure.spec.alpha :as spec]))

(spec/def ::title string?)
(spec/def ::year int?)
(spec/def ::id int?)
(spec/def ::tmdb-id int?)
(spec/def ::tvdb-id int?)
(spec/def ::root-folder-path string?)
(spec/def ::quality-profile-id (spec/and int? pos?))

(spec/def ::result (spec/keys
                    :req-un [::title ::year]
                    :opt-un [::id ::tvdb-id ::tmdb-id]))
