(ns doplarr.arr-utils
  (:require
   [clojure.tools.logging :as log]
   [clojure.core.async :as a]
   [fmnoise.flow :as flow :refer [then else]]
   [hato.client :as hc]))

(defn fatal-error [ex]
  (log/fatal ex)
  #_(System/exit -1))

(defn deep-merge [a & maps]
  (if (map? a)
    (apply merge-with deep-merge a maps)
    (apply merge-with deep-merge maps)))

(defn http-request [method url key & [params]]
  (let [chan (a/chan)
        put-close #(do
                     (a/put! chan %)
                     (a/close! chan))]
    (hc/request
     (deep-merge
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
