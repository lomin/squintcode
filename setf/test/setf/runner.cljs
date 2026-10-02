(ns setf.runner
  "Entry point for running the `setf` suite on ClojureScript."
  (:require [cljs.test :as t]
            setf.api-test))

(defn -main []
  (t/run-tests 'setf.api-test))

(set! *main-cli-fn* -main)
