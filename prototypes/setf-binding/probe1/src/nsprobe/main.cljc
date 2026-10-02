(ns nsprobe.main
  (:require [nsprobe.api :as api]))

(defn run []
  {:runtime-marker api/marker
   :macro-string   (api/where-am-i)})

(defn -main []
  (println "PROBE1" (pr-str (run))))
