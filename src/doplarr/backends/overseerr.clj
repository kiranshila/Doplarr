(ns doplarr.backends.overseerr
  (:require
   [doplarr.backends.overseerr.impl :as impl]
   [doplarr.utils :as utils]
   [config.core :refer [env]]
   [clojure.core.async :as a]))

(defn search [term media-type]
  (let [type (impl/media-type media-type)]
    (utils/request-and-process-body
     impl/GET
     (partial impl/process-search-result type)
     (str "/search?query=" term))))

; In Overseerr, the only additional option we'll need is which season,
; if the request type is a series
(defn additional-options [result media-type]
  (a/go
    (let [details (a/<! (impl/details (:id result) media-type))
          {:keys [partial-seasons]} env]
      (when (= media-type :series)
        (let [seasons (impl/seasons-list details)
              backend-partial-seasons? (a/<! (impl/partial-seasons?))]
          {:season (cond
                     (= 1 (count seasons)) (:id (first seasons))
                     (false? partial-seasons) -1
                     (false? backend-partial-seasons?) -1
                     :else (impl/seasons-list details))
           :season-count (count seasons)})))))

(defn request-embed [{:keys [title id season]} media-type]
  (a/go
    (let [fourk (a/<! (impl/backend-4k? media-type))
          details (a/<! (impl/details id media-type))]
      {:title title
       :overview (:overview details)
       :poster (str impl/poster-path (:poster-path details))
       :media-type media-type
       :request-formats (cond-> [""] fourk (conj "4K"))
       :season season})))

(defn request [payload media-type]
  (a/go
    (let [{:keys [format id season season-count discord-id]} payload
          {:keys [default-id]} env
          details (a/<! (impl/details id media-type))
          ovsr-id ((a/<! (impl/discord-users)) discord-id)
          status (impl/media-status details media-type
                                    :is-4k? (= format :4K)
                                    :season season)
          body (cond-> {:mediaType (impl/media-type media-type)
                        :mediaId id
                        :is4k (= format :4K)}
                 (= :series media-type)
                 (assoc :seasons
                        (if (= -1 season)
                          (into [] (range 1 (inc season-count)))
                          [season])))]
      (cond
        (contains? #{:unauthorized :pending :processing :available} status) status
        (and (nil? ovsr-id) (nil? default-id)) :unauthorized
        :else (a/<! (impl/POST "/request" {:form-params body
                                           :content-type :json
                                           :headers {"X-API-User" (str (or ovsr-id default-id))}}))))))
