(ns doplarr.config
  (:require
   [config.core :refer [env]]
   [doplarr.config.specs :as specs]
   [clojure.spec.alpha :as spec]))

(defn validate-config []
  (spec/explain-data ::specs/config env))

(defn available-backends []
  (let [{:keys [sonarr-url radarr-url overseerr-url]} env]
    (cond-> #{}
      sonarr-url (conj :sonarr)
      radarr-url (conj :radarr)
      overseerr-url (conj :overseerr))))
