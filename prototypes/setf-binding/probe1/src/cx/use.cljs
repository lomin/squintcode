(ns cx.use
  (:require [cx.api :refer [where]]))

(defn run [] {:m (where)})
(defn -main [] (println "CX-IMPLICIT-REFER" (pr-str (run))))
