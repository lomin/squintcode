(ns mx.use
  (:require [mx.api :as a]))

(defn run [] {:v a/v :m (a/where)})
(defn -main [] (println "IMPLICIT-CLJ-MACRO" (pr-str (run))))
