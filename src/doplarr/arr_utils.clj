(ns doplarr.arr-utils
  (:require
   [clojure.tools.logging :as log]
   [clojure.core.async :as a]
   [hato.client :as hc]))

(defn http-request [method url key & [params]]
  (let [chan (a/chan)]
    (hc/request
     (merge
      {:method method
       :url url
       :as :json
       :async? true
       :headers {"X-API-Key" key}}
      params)
     #(do
        (a/put! chan %)
        (a/close! chan))
     #(log/error % (ex-data %)))
    chan))

(defn rootfolder [base-url key]
  (let [chan (a/promise-chan)]
    (a/pipeline
     1
     chan
     (map (comp :path first :body))
     (http-request
      :get
      (str base-url "/rootfolder")
      key))
    chan))

(defn quality-profile-data [profile]
  {:name (:name profile)
   :id (:id profile)})
