(ns doplarr.config
  (:require
   [config.core :refer [env]]
   [taoensso.timbre :refer [info fatal]]
   [doplarr.config.specs :as specs]
   [clojure.spec.alpha :as spec]
   [clojure.set :as set]))

(defn validate-config []
  (if (spec/valid? ::specs/config env)
    (info "Configuration is valid")
    (do (fatal "Error in configuration"
               :info
               (->> (spec/explain-data ::specs/config env)
                    ::spec/problems
                    (into [])))
        (System/exit -1))))

(def backend-media {:radarr [:movie]
                    :sonarr [:series]
                    :overseerr [:movie :series]
                    :readarr [:book]
                    :lidarr [:music]})

(def media-backend
  (-> (for [[key types] backend-media]
        (for [type types]
          [key type]))
      (->> (mapcat identity)
           (group-by second))
      (update-vals (partial map first))))

(defn available-backends []
  (cond-> #{}
    (:radarr/url env) (conj :radarr)
    (:sonarr/url env) (conj :sonarr)
    (:overserr/url env) (conj :overseerr)
    (:readarr/url env) (conj :readarr)
    (:lidarr/url env) (conj :lidarr)))

(defn available-media []
  (into #{} (flatten (map backend-media (available-backends)))))

(defn available-backed-for-media [media]
  (first
   (set/intersection
    (available-backends)
    (into #{} (media-backend media)))))
