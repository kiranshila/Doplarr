(ns doplarr.backends.radarr.specs
  (:require
   [clojure.spec.alpha :as spec]
   [doplarr.backends.specs :as bs]))

(spec/def ::payload (spec/keys :req-un [::bs/title
                                        ::bs/tmdb-id
                                        ::bs/root-folder-path
                                        ::bs/quality-profile-id]))
