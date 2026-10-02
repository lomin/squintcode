(ns mx2.use
  (:require [mx2.api :as a]))

(defn run [] {:v a/v :m (a/where)})
(defn -main [] (println "MX2-IMPLICIT" (pr-str (run))))
