(ns doplarr.config.specs
  (:require [clojure.spec.alpha :as spec]
            [clojure.string :as str]))

(spec/def ::valid-url (spec/and string?
                                (complement #(str/ends-with? % "/"))))

; Backend endpoints
(spec/def :sonarr/url ::valid-url)
(spec/def :radarr/url ::valid-url)
(spec/def :overseerr/url ::valid-url)

; Backend API keys
(spec/def :sonarr/api string?)
(spec/def :radarr/api string?)
(spec/def :overseerr/api string?)

; Discord bot token
(spec/def :discord/token string?)

;  --- Optional settings
(spec/def ::log-level keyword?)
(spec/def :discord/role-id int?)
(spec/def :discord/max-results #(and (pos-int? %)
                                     (<= % 25)))

; Radarr optionals
(spec/def :radarr/quality-profile string?)
(spec/def :sonarr/quality-profile string?)

; Sonarr optionals
(spec/def :sonarr/language-profile string?)

; Overseerr optionals
(spec/def :overseerr/default-id pos-int?)

(spec/def ::partial-seasons boolean?)

(defn when-req [pred spec]
  (spec/nonconforming
   (spec/or :passed (spec/and pred spec)
            :failed (complement (partial spec/valid? pred)))))

(defmacro matched-keys
  [& ks]
  `(when-req #(some (partial contains? %) ~(vec ks)) (spec/keys :req ~(vec ks))))

; Complete configuration
(spec/def ::config (spec/and
                    (spec/keys :req [:discord/token]
                               :opt [::log-level
                                     :discord/role-id
                                     :discord/max-results
                                     :radarr/quality-profile
                                     :sonarr/quality-profile
                                     :sonarr/language-profile
                                     :overseerr/default-id
                                     ::partial-seasons])
                    #(some (partial contains? %) [:sonarr/url
                                                  :radarr/url
                                                  :overseerr/url])
                    (matched-keys :sonarr/url :sonarr/api)
                    (matched-keys :radarr/url :radarr/api)
                    (matched-keys :overseerr/url :overseerr/api)))
