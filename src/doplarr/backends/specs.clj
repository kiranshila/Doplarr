(ns doplarr.backends.specs
  (:require [clojure.spec.alpha :as spec]))

; Generic
(spec/def ::title string?)
(spec/def ::year int?)
(spec/def ::id pos-int?)
(spec/def ::root-folder-path string?)
(spec/def ::quality-profile-id pos-int?)
(spec/def ::quality-profile string?)
(spec/def ::language-profile-id pos-int?)
(spec/def ::language-profile string?)
(spec/def ::monitored boolean?)
(spec/def ::overview string?)
(spec/def ::poster string?)

; Movie
(spec/def ::tmdb-id pos-int?)
(spec/def ::search-for-movie boolean?)

; Series
(spec/def ::season-number int?)
(spec/def ::season (spec/keys :req-un [::season-number ::monitored]))
(spec/def ::seasons (spec/coll-of ::season))
(spec/def ::tvdb-id pos-int?)
(spec/def ::ignore-episodes-with-files boolean?)
(spec/def ::search-for-missing-episodes boolean?)

; Books
(spec/def ::foreign-book-id pos-int?)
(spec/def ::author string?)

; Searching
(spec/def ::result (spec/keys :req-un [::title
                                       (or ::year ::author)
                                       (or ::id ::tvdb-id ::tmdb-id ::foreign-book-id)]))

; Doplarr Internals
(spec/def ::status (or #{:unauthorized :unknown :pending :processing :partially-available :available}
                       nil?))
(spec/def ::name string?)
(spec/def ::option (spec/keys :req-un [::name ::id]))
(spec/def ::options (spec/coll-of ::option))
(spec/def ::additional-options (spec/keys :req-un [::name ::options]))
(spec/def ::uuid string?)
(spec/def ::request-format string?)
(spec/def ::request-formats (spec/coll-of ::request-format))
(spec/def ::media-type keyword?)
(spec/def ::request-embed (spec/keys :req-un [::title ::overview ::poster ::request-formats ::media-type]
                                     :opt-un [::quality-pofile ::language-profile ::season]))
