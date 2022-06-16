(ns doplarr.config
  (:require
   [clojure.set :as set]
   [clojure.spec.alpha :as spec]
   [com.rpl.specter :as s]
   [doplarr.config.specs :as specs]
   [expound.alpha :as expound]
   [taoensso.timbre :refer [fatal info]]))

(def valid-keys
  #{; Sonarr
    :sonarr/url
    :sonarr/api
    :sonarr/quality-profile
    :sonarr/language-profile
    :sonarr/rootfolder
    :sonarr/season-folders
    ; Radarr
    :radarr/url
    :radarr/api
    :radarr/quality-profile
    :radarr/rootfolder
    ; Overseerr
    :overseerr/url
    :overseerr/api
    :overseerr/default-id
    ; Discord
    :discord/token
    :discord/max-results
    :discord/requested-msg-style
    ; Doplarr
    :partial-seasons
    :log-level})

(defn trimr* [s c]
  (loop [index (.length s)]
    (if (zero? index)
      ""
      (if (= c (.charAt s (unchecked-dec index)))
        (recur (unchecked-dec index))
        (.. s (subSequence 0 index) toString)))))

(def redacted-str "REDACTED")

(defn assoc-present [m k v]
  (if (contains? m k)
    (assoc m k v)
    m))

(defn redact-secrets [config]
  (letfn [(redact [m k] (assoc-present m k redacted-str))]
    (-> config
        (redact :sonarr/api)
        (redact :radarr/api)
        (redact :overseerr/api)
        (redact :discord/token))))

(defn valid-config [env]
  (let [config (s/select-one
                [(s/submap valid-keys)
                 (s/transformed [s/MAP-VALS nil?] (constantly s/NONE))
                 (s/transformed [s/ALL #(= "url" (name (first %)))] #(vector (first %) (trimr* (second %) \/)))]
                env)]
    (if (spec/valid? ::specs/config config)
      (info "Configuration is valid")
      (do
        (fatal "Error in  configuration")
        (expound/expound ::specs/config (redact-secrets config))
        (flush)
        (System/exit -1)))
    config))

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

(defn available-backends [env]
  (cond-> #{}
    (:radarr/url env) (conj :radarr)
    (:sonarr/url env) (conj :sonarr)
    (:overseerr/url env) (conj :overseerr)
    (:readarr/url env) (conj :readarr)
    (:lidarr/url env) (conj :lidarr)))

(defn available-media [env]
  (into #{} (flatten (map backend-media (available-backends env)))))

(defn available-backend-for-media [media env]
  (first
   (set/intersection
    (available-backends env)
    (into #{} (media-backend media)))))
