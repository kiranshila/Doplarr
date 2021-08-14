(ns doplarr.arr-utils
  (:require
   [config.core :refer [env]]
   [hato.client :as hc]))

(defn http-request [method url key & params]
  (let [response (hc/request
                  (apply merge
                         {:method method
                          :url url
                          :as :json
                          :headers {"X-API-Key" key}}
                         params))]
    (when (:debug env)
      (clojure.pprint/pprint response))
    response))

(defn rootfolder [base-url key]
  (->> (http-request :get (str base-url "/rootfolder") key)
       :body
       first
       :path))

(defn quality-profile-data [profile]
  {:name (:name profile)
   :id (:id profile)})
