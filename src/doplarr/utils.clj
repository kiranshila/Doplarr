(ns doplarr.utils
  (:require
   [clojure.tools.logging :as log]
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as cske]
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
       :as :json-string-keys
       :coerce :always
       :async? true
       :headers {"X-API-Key" key}}
      params)
     put-close
     put-close)
    chan))

(defn from-camel [m]
  (cske/transform-keys csk/->kebab-case-keyword m))

(defn to-camel [m]
  (cske/transform-keys csk/->camelCaseString m))

(defn process-search-result [result]
  (-> result
      (select-keys ["title" "year" "id" "tvdbId" "tmdbId"])
      from-camel))

(defn process-profile [profile]
  (->> (select-keys profile ["id" "name"])
       from-camel))

(defn request-and-process-body [request-fn process-fn & request-args]
  (a/go
    (->> (a/<! (apply request-fn request-args))
         (then #(process-fn (:body %)))
         (else #(fatal-error (ex-info (str "Error on request from " request-fn)
                                      {:args request-args
                                       :exception (ex-data %)}))))))
