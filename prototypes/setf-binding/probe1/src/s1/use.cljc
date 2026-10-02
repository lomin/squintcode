(ns s1.use
  (:require [s1.api :as a])
  (:require-macros [s1.api :refer [where]]))

(defn run [] {:v a/v :m (where)})
(defn -main [] (println "S1-EXPLICIT" (pr-str (run))))
