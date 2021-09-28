(ns doplarr.backends.sonarr
  (:require
   [orchestra.core :refer [defn-spec]]
   [config.core :refer [env]]
   [doplarr.utils :as utils]
   [doplarr.backends.sonarr.impl :as impl]
   [clojure.spec.alpha :as spec]
   [doplarr.backends.sonarr.specs :as specs]
   [doplarr.backends.specs :as bs]
   [clojure.core.async :as a]))

(defn-spec search any?
  [term string?]
  (utils/request-and-process-body
   impl/GET
   #(mapv utils/process-search-result %)
   "/series/lookup"
   {:query-params {:term term}}))

(defn-spec request any?
  [payload ::specs/payload]
  (utils/request-and-process-body
   (if (:id payload) impl/PUT impl/POST)
   (constantly nil)
   "/series"
   {:form-params (utils/to-camel payload)
    :content-type :json}))

(defn-spec additional-options
  ::bs/additional-options
  [result ::bs/result]
  (a/go
    (let [quality-profiles (a/<! (impl/quality-profiles))
          language-profiles (a/<! (impl/language-profiles))
          details (a/<! (impl/get-from-tvdb (:tvdb-id result)))
          seasons (->> (:seasons details)
                       (filter #(pos? (:season-number %)))
                       (map #(let [ssn (:season-number %)]
                               (hash-map :id ssn :name (str ssn)))))
          {:keys [default-language-profile
                  default-quality-profile
                  partial-seasons]} env]
      {:season (cond
                 (= 1 (count seasons)) (->> seasons
                                            first
                                            :id)
                 (false? partial-seasons) -1
                 :else (conj seasons {:name "All Seasons" :id -1}))
       :quality-profile-id (cond
                             (= 1 (count quality-profiles)) (->> quality-profiles
                                                                 first
                                                                 :id)
                             default-quality-profile (->> quality-profiles
                                                          (filter #(= default-quality-profile (:name %)))
                                                          first
                                                          :id)
                             :else quality-profiles)
       :language-profile-id (cond
                              (= 1 (count language-profiles)) (->> language-profiles
                                                                   first
                                                                   :id)
                              default-language-profile (->> language-profiles
                                                            (filter #(= default-language-profile (:name %)))
                                                            first
                                                            :id)
                              :else language-profiles)})))

(defn-spec request-embed ::bs/request-embed
  [{:keys [title quality-profile-id language-profile-id tvdb-id season]} ::specs/payload]
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
