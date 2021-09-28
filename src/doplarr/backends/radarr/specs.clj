(ns doplarr.backends.radarr.specs
  (:require
   [clojure.spec.alpha :as spec]
   [doplarr.backends.specs :as bs]))

(spec/def ::prepared-payload (spec/keys :req-un [::bs/title
                                                 ::bs/tmdb-id
                                                 ::bs/quality-profile-id]))

(spec/def ::add-options (spec/keys :req-un [::search-for-movie]))

(spec/def ::request-payload (spec/keys :req-un [::bs/title
                                                ::bs/tmdb-id
                                                ::bs/root-folder-path
                                                ::bs/monitored
                                                ::bs/root-folder-path
                                                ::add-options
                                                ::bs/quality-profile-id]))

(spec/def ::has-file boolean?)
(spec/def ::is-available boolean?)

(spec/def ::details (spec/keys :req-un [::has-file ::is-available ::bs/monitored]))
