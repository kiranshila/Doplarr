(ns doplarr.config.specs
  (:require [clojure.spec.alpha :as spec]))

; Backend endpoints
(spec/def ::sonarr-url string?)
(spec/def ::radarr-url string?)
(spec/def ::overseerr-url string?)
(spec/def ::readarr-url string?)

; Backend API keys
(spec/def ::sonarr-api string?)
(spec/def ::radarr-api string?)
(spec/def ::overseerr-api string?)
(spec/def ::readarr-api string?)

; Discord bot token
(spec/def ::bot-token string?)

; Additional required keys
(spec/def ::readarr-quality-profile string?)
(spec/def ::readarr-metadata-profile string?)

; Optional settings
(spec/def ::partial-seasons boolean?)
(spec/def ::default-radarr-quality-profile string?)
(spec/def ::default-sonarr-quality-profile string?)
(spec/def ::default-language-profile string?)
(spec/def ::role-id string?)
(spec/def ::max-results pos-int?)

; Complete Config
(spec/def ::config (spec/keys :req-un [(or (and ::sonarr-url ::sonarr-api)
                                           (and ::radarr-url ::radarr-api)
                                           (and ::overseerr-url ::overseerr-api)
                                           (and ::readarr-url ::readarr-api ::readarr-quality-profile ::readarr-metadata-profile))
                                       ::bot-token]
                              :opt-un [::partial-seasons
                                       ::default-radarr-quality-profile
                                       ::default-sonarr-quality-profile
                                       ::default-language-profile
                                       ::role-id
                                       ::max-results]))
