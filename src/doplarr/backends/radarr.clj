(ns doplarr.backends.radarr
  (:require
   [config.core :refer [env]]
   [clojure.spec.alpha :as spec]
   [doplarr.utils :as utils]
   [doplarr.backends.radarr.impl :as impl]
   [doplarr.backends.radarr.specs :as specs]
   [doplarr.backends.specs :as bs]
   [clojure.core.async :as a]))

(defn search [term]
  (utils/request-and-process-body
   impl/GET
   #(into [] (map utils/process-search-result %))
   "/movie/lookup"
   {:query-params {:term term}}))
(spec/fdef search
  :args (spec/cat :term string?))

(defn request [payload]
  (utils/request-and-process-body
   impl/POST
   (constantly nil)
   "/movie"
   {:form-params (utils/to-camel payload)
    :content-type :json}))
(spec/fdef request
  :args (spec/cat :payload ::specs/payload))

(defn additional-options [result]
  (a/go
    (let [quality-profiles (a/<! (impl/quality-profiles))
          {:keys [default-quality-profile
                  partial-seasons]} env]
      {:quality-profile-id (cond
                             (= 1 (count quality-profiles)) (->> quality-profiles
                                                                 first
                                                                 :id)
                             default-quality-profile (->> quality-profiles
                                                          (filter #(= default-quality-profile (:name %)))
                                                          first
                                                          :id)
                             :else quality-profiles)})))
(spec/fdef additional-options
  :args (spec/cat :result ::bs/result))
