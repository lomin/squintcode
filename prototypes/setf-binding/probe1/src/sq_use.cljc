(ns sq-use
  (:require [nb2.api :as nb2]
            [nb3.api :as nb3])
  (:require-macros [nb4.api :refer [m]]))

(defn run []
  {:nb2-var   nb2/v
   :nb2-macro (nb2/m)
   :nb3-var   nb3/v
   :nb3-macro (nb3/m)
   :nb4-macro (m)})

(defn -main [] (println "SQ-PROBE" (pr-str (run))))
