(ns doplarr.config
  (:require
   [config.core :refer [env]]))

(def bot-requirements #{:bot-token
                        :role-id})

(def direct-requirements #{:sonarr-url
                           :sonarr-api
                           :radarr-url
                           :radarr-api})

(def overseerr-requirements #{:overseerr-url
                              :overseerr-api})

;; Default to overseerr if both are configured
(defn backend []
  (cond
    (every? env overseerr-requirements) :overseerr
    (every? env direct-requirements) :direct
    :else nil))

(defn validate-env []
  (and (every? env bot-requirements)
       (keyword? (backend))))
