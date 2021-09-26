(ns doplarr.backends.sonarr
  (:require
   [config.core :refer [env]]
   [doplarr.utils :as utils]
   [doplarr.backends.sonarr.impl :as impl]
   [clojure.spec.alpha :as spec]
   [doplarr.backends.sonarr.specs :as specs]
   [doplarr.backends.specs :as bs]
   [clojure.core.async :as a]))

(defn search [term]
  (utils/request-and-process-body
   impl/GET
   #(mapv utils/process-search-result %)
   "/series/lookup"
   {:query-params {:term term}}))
(spec/fdef search
  :args (spec/cat :term string?))

(defn request [payload]
  (utils/request-and-process-body
   (if (:id payload) impl/PUT impl/POST)
   (constantly nil)
   "/series"
   {:form-params (utils/to-camel payload)
    :content-type :json}))
(spec/fdef request
  :args (spec/cat :payload ::specs/payload))

(defn additional-options [result]
  (a/go
    (let [quality-profiles (a/<! (impl/quality-profiles))
          language-profiles (a/<! (impl/language-profiles))
          details (a/<! (impl/get-from-tvdb (:tvdb-id result)))
          seasons (->> (:seasons details)
                       (filterv #(pos? (:season-number %)))
                       (mapv #(let [ssn (:season-number %)]
                                (hash-map :id ssn :name (str ssn)))))
          {:keys [default-language-profile
                  default-quality-profile
                  partial-seasons]} env]
      {:season (cond
                 (= 1 (count seasons)) (->> seasons
                                            first
                                            :id)
                 (false? partial-seasons) -1
                 :else seasons)
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
(spec/fdef additional-options
  :args (spec/cat :result ::bs/result))
