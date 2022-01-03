(ns doplarr.config.specs
  (:require [clojure.spec.alpha :as spec]
            [clojure.string :as str]))

(spec/def ::valid-url (spec/and string?
                                (complement #(str/ends-with? % "/"))))

; Backend endpoints
(spec/def :sonarr/url ::valid-url)
(spec/def :radarr/url ::valid-url)

; Backend API keys
(spec/def :sonarr/api string?)
(spec/def :radarr/api string?)

; Discord bot token
(spec/def :discord/token string?)

; Optional settings
(spec/def :discord/role-id string?)
(spec/def :discord/max-results pos-int?)

(spec/def :radarr/quality-profile string?)

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
                               :opt [:discord/role-id
                                     :discord/max-results
                                     :radarr/quality-profile])
                    #(some (partial contains? %) [:sonarr/url
                                                  :radarr/url])
                    (matched-keys :sonarr/url :sonarr/api)
                    (matched-keys :radarr/url :radarr/api)))
