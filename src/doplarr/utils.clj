(ns doplarr.utils
  (:require
   [taoensso.timbre :refer [fatal]]
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as cske]
   [clojure.core.async :as a]
   [fmnoise.flow :as flow :refer [then else]]
   [hato.client :as hc]
   [clojure.string :as str]
   [doplarr.config :as config]))

(defn deep-merge [a & maps]
  (if (map? a)
    (apply merge-with deep-merge a maps)
    (apply merge-with deep-merge maps)))

(defn http-request [method url key & [params]]
  (let [chan (a/promise-chan)
        put (partial a/put! chan)]
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

(defn profile-name-id [profiles name]
  (->> profiles
       (filter #(= name (:name %)))
       first
       :id))

(defn profile-id-name [profiles id]
  (->> profiles
       (filter #(= id (:id %)))
       first
       :name))

(defn request-and-process-body [request-fn process-fn & request-args]
  (a/go
    (->> (a/<! (apply request-fn request-args))
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
   (symbol (str "doplarr.backends." (name (config/available-backed-for-media
                                           media)))
           f)))