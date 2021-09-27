(ns doplarr.system
  (:require
   [doplarr.config :as config]))

(def backends [:radarr :sonarr :overseerr])
(def backend-fns [:search :request :additional-options])

(def media-backends {:movie [:overseerr :radarr]
                     :series [:overseerr :sonarr]})

(defn derive-backend! [backend]
  (derive (keyword "backend" (name backend)) :doplarr/backend))

; Generate Parent-Child Relationships
(run! derive-backend! backends)

