(ns doplarr.system
  (:require [integrant.core :as ig]
            [doplarr.config :as config]))

(def backends [:radarr :sonarr :overseerr])
(def backend-fns [:search :request :additional-options])

(def media-backends {:movie [:overseerr :radarr]
                     :series [:overseerr :sonarr]
                    ; :book [:readarr]
                    ; :music [:lidarr]
                     })

(defn derive-backend! [backend]
  (derive (keyword "backend" (name backend)) :doplarr/backend))

; Generate Parent-Child Relationships
(run! derive-backend! backends)

(def config
  (into {} (for [b backends]
             [(keyword "backend" (name b)) {:ns b}])))

(defmethod ig/init-key :doplarr/backend [_ {:keys [ns]}]
  (zipmap backend-fns (for [f backend-fns
                            :let [ns (str "doplarr.backends." (name ns))
                                  sym (symbol ns (name f))]]
                        (requiring-resolve sym))))
