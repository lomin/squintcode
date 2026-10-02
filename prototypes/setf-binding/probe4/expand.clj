(ns expand
  "PROTOTYPE (probe 4) — prints the emission of a backend for the same four
   portable use sites. Used for every backend so the runs are comparable."
  (:require [setf.api :as api]))

(def forms
  ['(setf.api/setf! (elt a 1) 99)
   '(setf.api/incf! (elt a 1))
   '(setf.api/setf! (gethash m "k") 7)
   '(setf.api/setf! (get! o x) 42)])

(defn -main [& _]
  (doseq [f forms]
    (println (pr-str f))
    (println "   =>" (pr-str (macroexpand-1 f)))))