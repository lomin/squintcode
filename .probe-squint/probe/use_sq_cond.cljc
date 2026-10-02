(ns probe.use-sq-cond
  (:require-macros [probe.macros-sq-cond :refer [aref2]]))

(def a #js [10 20 30])
(println "sq-reader-cond macro =>" (aref2 a 1))
