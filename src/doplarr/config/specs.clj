(ns doplarr.config.specs
  (:require
   [expound.alpha :as expound]
   [clojure.spec.alpha :as spec]))

; Backend endpoints
(spec/def :sonarr/url string?)
(spec/def :radarr/url string?)
(spec/def :overseerr/url string?)

; Backend API keys
(spec/def :sonarr/api string?)
(spec/def :radarr/api string?)
(spec/def :overseerr/api string?)

; Discord bot token - the only really "required" item
(spec/def :discord/token string?)

;  --- Optional settings
(spec/def :discord/max-results (spec/int-in 1 26))
(spec/def :discord/requested-msg-style #{:none :plain :embed})

; Radarr optionals
(spec/def :radarr/quality-profile string?)
(spec/def :radarr/rootfolder string?)

; Sonarr optionals
(spec/def :sonarr/quality-profile string?)
(spec/def :sonarr/language-profile string?)
(spec/def :sonarr/rootfolder string?)
(spec/def :sonarr/season-folders boolean?)

; Overseerr optionals
(spec/def :overseerr/default-id pos-int?)

; Doplarr optionals
(spec/def ::partial-seasons boolean?)
(spec/def ::log-level #{:trace :debug :info :warn :error :fatal :report})

(defn when-req [pred spec]
  (spec/nonconforming
   (spec/or :passed (spec/and pred spec)
            :failed (complement (partial spec/valid? pred)))))

(defmacro matched-keys [& ks]
  `(when-req #(some (partial contains? %) ~(vec ks)) (spec/keys :req ~(vec ks))))

(spec/def ::has-backend #(some (partial contains? %) [:sonarr/url :radarr/url :overseerr/url]))
(expound/defmsg ::has-backend "config must contain at least one of the following backends: sonarr, radarr, overseerr
If you have configured one, make sure to check spelling. A valid configuration contains both the api key and url")

; Complete configuration
(spec/def ::config (spec/and
                    (spec/keys :req [:discord/token]
                               :opt [:discord/max-results
                                     :discord/requested-msg-style
                                     :radarr/quality-profile
                                     :sonarr/quality-profile
                                     :sonarr/language-profile
                                     :sonarr/season-folders
                                     :overseerr/default-id
                                     :sonarr/rootfolder
                                     :radarr/rootfolder]
                               :opt-un [::partial-seasons
                                        ::log-level])
                    ::has-backend
                    (matched-keys :sonarr/url :sonarr/api)
                    (matched-keys :radarr/url :radarr/api)
                    (matched-keys :overseerr/url :overseerr/api)))
