(ns mxs.use
  (:require [mxs.api :as a]))

(defn run [] {:v a/v :m (a/where)})
(defn -main [] (println "IMPLICIT-CLJC-MACRO" (pr-str (run))))

;; variant with an EXPLICIT :require-macros, for comparison
(ns mxs.use-x
  (:require [mxs.api :as a])
  (:require-macros [mxs.api :refer [where]]))

(defn run-x [] {:v a/v :m (where)})
(defn -main-x [] (println "EXPLICIT-CLJC-MACRO" (pr-str (run-x))))
