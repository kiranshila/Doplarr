(ns doplarr.arr-utils
  (:require [hato.client :as hc]))

(defn http-request [method url key & params]
  (hc/request
   (apply merge
          {:method method
           :url url
           :as :json
           :headers {"X-API-Key" key}}
          params)))

(defn rootfolder [base-url key]
  (->> (http-request :get (str base-url "/rootfolder") key)
       :body
       first
       :path))
