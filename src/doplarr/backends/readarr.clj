(ns doplarr.backends.readarr
  (:require
   [config.core :refer [env]]
   [clojure.core.async :as a]
   [orchestra.core :refer [defn-spec]]
   [doplarr.utils :as utils]
   [doplarr.backends.specs :as bs]
   [doplarr.backends.readarr.impl :as impl]))

(defn-spec search any?
  [term string?]
  (utils/request-and-process-body
   impl/GET
   #(->> (utils/from-camel %)
         impl/filter-books)
   "/book/lookup"
   {:query-params {:term term}}))

(defn-spec additional-options any?
  [result ::bs/result]
  (a/go
    (let [quality-profiles (a/<! (impl/quality-profiles))
          {:keys [default-readarr-quality-profile]} env]
      {:quality-profile-id (if (= 1 (count quality-profiles))
                             (->> quality-profiles
                                  first
                                  :id)
                             (->> quality-profiles
                                  (filter #(= default-readarr-quality-profile (:name %)))
                                  first
                                  :id))})))

(defn request [])

#_(defn request-embed [{:keys [title]}]
    (a/go
      (let [details (a/! (impl/get-from-id))])))
