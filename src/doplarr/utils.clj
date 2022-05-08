(ns doplarr.utils
  (:require
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as cske]
   [clojure.core.async :as a]
   [clojure.string :as str]
   [doplarr.config :as config]
   [doplarr.state :as state]
   [fmnoise.flow :as flow :refer [else then]]
   [hato.client :as hc]
   [taoensso.timbre :refer [fatal trace]]
   [clojure.set :as set]))

(defn deep-merge [a & maps]
  (if (map? a)
    (apply merge-with deep-merge a maps)
    (apply merge-with deep-merge maps)))

(defn http-request [method url key & [params]]
  (let [chan (a/promise-chan)
        put (fn [v]
              (trace "HTTP Response " v)
              (a/put! chan v))]
    (trace "Performing HTTP request" method url params)
    (hc/request
     (deep-merge
      {:method method
       :url url
       :as :json-string-keys
       :coerce :always
       :async? true
       :headers {"X-API-Key" key}}
      params)
     put
     put)
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

(defn id-from-name [profiles name]
  (->> profiles
       (filter #(= name (:name %)))
       first
       :id))

(defn name-from-id [profiles id]
  (->> profiles
       (filter #(= id (:id %)))
       first
       :name))

(defmacro log-on-error [expr msg]
  `(try
     ~expr
     (catch Exception e#
       (fatal e# ~msg)
       (throw e#))))

(defn request-and-process-body [request-fn process-fn & request-args]
  (a/go
    (->> (log-on-error
          (a/<! (apply request-fn request-args))
          "Exception from HTTP request")
         (then #(process-fn (:body %)))
         (else #(fatal %)))))

(defn canonical-option-name [option]
  (-> (name option)
      (str/replace #"-" " ")
      (#(if (str/ends-with? % "id")
          (str/trim (subs % 0 (- (count %) 2)))
          (str/trim %)))))

(defn media-fn
  "Resolves a function `f` in the backend namespace matching the available backend for a given `media`"
  [media f]
  (requiring-resolve
   (symbol (str "doplarr.backends." (name (config/available-backend-for-media media @state/config)))
           f)))

(defn process-rootfolders [resp]
  (->> (from-camel resp)
       (map #(select-keys % #{:path :id}))
       (map #(set/rename-keys % {:path :name}))))

(defn process-tags [resp]
  (->> (from-camel resp)
       (map #(set/rename-keys % {:label :name}))
       (#(conj % {:name "No Tag" :id -1}))))
