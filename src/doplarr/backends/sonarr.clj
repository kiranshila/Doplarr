(ns doplarr.backends.sonarr
  (:require
   [taoensso.timbre :refer [warn fatal]]
   [config.core :refer [env]]
   [doplarr.utils :as utils]
   [fmnoise.flow :refer [then else]]
   [doplarr.backends.sonarr.impl :as impl]
   [clojure.core.async :as a]))

(defn search [term _]
  (utils/request-and-process-body
   impl/GET
   #(mapv utils/process-search-result %)
   "/series/lookup"
   {:query-params {:term term}}))

(defn additional-options [result _]
  (a/go
    (let [quality-profiles (a/<! (impl/quality-profiles))
          language-profiles (a/<! (impl/language-profiles))
          details (a/<! (impl/get-from-tvdb (:tvdb-id result)))
          seasons (->> (:seasons details)
                       (filter #(pos? (:season-number %)))
                       (map #(let [ssn (:season-number %)]
                               (hash-map :id ssn :name (str ssn)))))
          {:keys [sonarr/language-profile
                  sonarr/quality-profile
                  partial-seasons]} env
          default-profile-id (utils/profile-name-id quality-profiles quality-profile)
          default-language-id (utils/profile-name-id language-profiles language-profile)]
      (when (and quality-profile (nil? default-profile-id))
        (warn "Default quality profile in config doesn't exist in backend, check spelling"))
      (when (and language-profile (nil? default-language-id))
        (warn "Default language profile in config doesn't exist in backend, check spelling"))
      {:season (cond
                 (= 1 (count seasons)) (:id (first seasons))
                 (false? partial-seasons) -1
                 :else (conj seasons {:name "All Seasons" :id -1}))
       :quality-profile-id (cond
                             quality-profile default-profile-id
                             (= 1 (count quality-profiles)) (:id (first quality-profiles))
                             :else quality-profiles)
       :language-profile-id (cond
                              language-profile default-language-id
                              (= 1 (count language-profiles)) (:id (first language-profiles))
                              :else language-profiles)})))

(defn request-embed [{:keys [title quality-profile-id language-profile-id tvdb-id season]}]
  (a/go
    (let [quality-profiles (a/<! (impl/quality-profiles))
          language-profiles (a/<! (impl/language-profiles))
          details (a/<! (impl/get-from-tvdb tvdb-id))]
      {:title title
       :overview (:overview details)
       :poster (:remote-poster details)
       :media-type :series
       :season season
       :request-formats [""]
       :quality-profile (:name (first (filter #(= quality-profile-id (:id %)) quality-profiles)))
       :language-profile (:name (first (filter #(= language-profile-id (:id %)) language-profiles)))})))

(defn request [payload]
  (a/go (let [details  (a/<! (if-let [id (:id payload)]
                               (impl/get-from-id id)
                               (impl/get-from-tvdb (:tvdb-id payload))))
              status (impl/status details (:season payload))
              request-payload (impl/request-payload payload details)]
          (if status
            status
            (->> (a/<! ((if (:id payload) impl/PUT impl/POST) "/series" {:form-params (utils/to-camel request-payload)
                                                                         :content-type :json}))
                 (then (fn [_]
                         (when-let [id (:id payload)]
                           (if (= -1 (:season payload))
                             (impl/search-series id)
                             (impl/search-season id (:season payload)))))))))))
