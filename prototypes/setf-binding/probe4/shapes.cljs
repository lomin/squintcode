(ns shapes
  "PROTOTYPE (probe 4) — same accessor, different JS structures."
  (:require [setf.api :refer-macros [setf! incf!]]))

(defn -main []
  (let [a (array 0 0 0)
        u (js/Uint32Array. 3)
        m (js/Map.)
        o (js-obj "x" 0)]
    (setf! (elt a 1) 10)
    (incf! (elt a 1) 5)
    (setf! (elt u 2) 7)
    (setf! (gethash m "k") 1)
    (setf! (get! o x) 30)
    (println "Array        " (aget a 1))
    (println "Uint32Array  " (aget u 2))
    (println "Map          " (.get m "k"))
    (println "js-obj.x     " (.-x o))))
