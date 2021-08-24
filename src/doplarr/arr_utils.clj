(ns doplarr.arr-utils
  (:require
   [clojure.tools.logging :as log]
   [clojure.core.async :as a]
   [fmnoise.flow :as flow :refer [then else]]
   [hato.client :as hc]))

(defn fatal-error [ex]
  (log/fatal ex)
  (System/exit -1))

(defn http-request [method url key & [params]]
  (let [chan (a/chan)
        put-close #(do
                     (a/put! chan %)
                     (a/close! chan))]
    (hc/request
     (merge
      {:method method
       :url url
       :as :json
       :async? true
       :headers {"X-API-Key" key}}
      params)
     put-close
     put-close)
    chan))

(defn rootfolder [base-url key]
  (a/go
    (->> (a/<! (http-request
                :get
                (str base-url "/rootfolder")
                key))
         (then (comp :path first :body))
         (else fatal-error))))

(defn quality-profile-data [profile]
  {:name (:name profile)
   :id (:id profile)})
