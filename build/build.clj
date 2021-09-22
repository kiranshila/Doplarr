(ns build
  (:require
   [org.corfield.build :as bb]))

(defn uber [_]
  (bb/clean nil)
  (bb/uber {:uber-file "target/doplarr.jar"
            :main 'doplarr.core}))
