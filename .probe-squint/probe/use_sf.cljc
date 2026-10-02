(ns probe.use-sf
  (:require-macros [probe.sf :refer [setf! get!*]]))

(def a #js [1 2 3])

(setf! (aref a 1) 99)
(println "expander via separate ns =>" (get!* (aref a 1)) (get!* (aref a 0)))
