(ns doplarr.backends.specs
  (:require [clojure.spec.alpha :as spec]))

; Generic
(spec/def ::title string?)
(spec/def ::year int?)
(spec/def ::id pos-int?)
(spec/def ::root-folder-path string?)
(spec/def ::quality-profile-id pos-int?)
(spec/def ::language-profile-id pos-int?)
(spec/def ::monitored boolean?)

; Movie
(spec/def ::tmdb-id pos-int?)

; Series
(spec/def ::season-number int?)
(spec/def ::season (spec/keys :req-un [::season-number ::monitored]))
(spec/def ::seasons (spec/coll-of ::season))
(spec/def ::tvdb-id pos-int?)
(spec/def ::ignore-episodes-with-files boolean?)
(spec/def ::search-for-missing-episodes boolean?)
(spec/def ::add-options (spec/keys :req-un [::ignore-episodes-with-files ::search-for-missing-episodes]))

; Searching
(spec/def ::result (spec/keys :req-un [::title ::year
                                       (or ::id ::tvdb-id ::tmdb-id)]))

; Doplarr Internals
(spec/def ::status #{:unknown :pending :processing :partially-available :available})
(spec/def ::name string?)
(spec/def ::option (spec/keys :req-un [::name ::id]))
(spec/def ::options (spec/coll-of ::option))
(spec/def ::additional-options (spec/keys :req-un [::name ::options]))
