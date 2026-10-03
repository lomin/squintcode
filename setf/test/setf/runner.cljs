(ns setf.runner
  "Entry point for running the `setf` suite on ClojureScript."
  (:require [cljs.test :as t]
            setf.api-test))

(defmethod t/report [::t/default :end-run-tests] [m]
  ;; cljs.test only prints a failure; without this the process exits 0.
  (when-not (t/successful? m)
    (set! (.-exitCode js/process) 1)))

(defn -main []
  (t/run-tests 'setf.api-test))

(set! *main-cli-fn* -main)
