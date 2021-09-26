(ns doplarr.backends.sonarr.specs
  (:require [clojure.spec.alpha :as spec]
            [doplarr.backends.specs :as bs]))

(spec/def ::payload (spec/keys :req-un [::bs/title
                                        ::bs/tvdb-id
                                        ::bs/root-folder-path
                                        ::bs/seasons
                                        ::bs/monitored
                                        ::bs/add-options
                                        ::bs/quality-profile-id
                                        ::bs/language-profile-id]
                               :opt-un [::bs/id]))
