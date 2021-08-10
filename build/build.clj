(ns build
  (:require
   [clojure.tools.build.api :as b]
   [hf.depstar :as depstar]))

(defn uberjar [params]
  (b/delete {:path "target"})
  (-> (merge {:jar "Doplarr.jar"
              :aot true
              :sync-pom true
              :target-dir "target"
              :main-class "doplarr.core"}
             params)
      (depstar/aot)
      (depstar/pom)
      (assoc :jar-type :uber)
      (depstar/build)))
