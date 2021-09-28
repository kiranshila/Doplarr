(ns doplarr.backends.radarr
  (:require
   [orchestra.core :refer [defn-spec]]
   [config.core :refer [env]]
   [doplarr.utils :as utils]
   [doplarr.backends.radarr.impl :as impl]
   [doplarr.backends.radarr.specs :as specs]
   [doplarr.backends.specs :as bs]
   [clojure.core.async :as a]))

(defn-spec search any?
  [term string?]
  (utils/request-and-process-body
   impl/GET
   #(map utils/process-search-result %)
   "/movie/lookup"
   {:query-params {:term term}}))

(defn-spec request any?
  [payload ::specs/prepared-payload]
  ; Use collected information to finalize request payload
  ; Check status to see if we *can* request
  ; Send request and response and nil (non-exceptional)
  ; Or send status
  (a/go
    (let [details (a/<! (impl/get-from-tmdb (:tmdb-id payload)))
          status (impl/status details)
          request-payload (impl/request-payload payload)]
      (if status
        status
        (a/<! (utils/request-and-process-body
               impl/POST
               (constantly nil)
               "/movie"
               {:form-params (utils/to-camel payload)
                :content-type :json}))))))

(defn-spec additional-options any?
  [result ::bs/result]
  (a/go
    (let [quality-profiles (a/<! (impl/quality-profiles))
          {:keys [default-quality-profile]} env]
      {:quality-profile-id (cond
                             (= 1 (count quality-profiles)) (->> quality-profiles
                                                                 first
                                                                 :id)
                             default-quality-profile (->> quality-profiles
                                                          (filter #(= default-quality-profile (:name %)))
                                                          first
                                                          :id)
                             :else quality-profiles)})))

(defn-spec request-embed any?
  [{:keys [title quality-profile-id tmdb-id]} ::specs/prepared-payload]
  (a/go
    (let [quality-profiles (a/<! (impl/quality-profiles))
          details (a/<! (impl/get-from-tmdb tmdb-id))]
      {:title title
       :overview (:overview details)
       :poster (:remote-poster details)
       :media-type :movie
       :request-formats [""]
       :quality-profile (:name (first (filter #(= quality-profile-id (:id %)) quality-profiles)))})))
