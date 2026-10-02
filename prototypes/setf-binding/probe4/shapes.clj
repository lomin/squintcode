(ns shapes
  "PROTOTYPE (probe 4) — requirement 4: on ONE host, different structures that
   share the accessor must all work through the SAME emission."
  (:require [setf.api :refer [setf! incf!]]))

;; The `:indexed` place is `elt` — indexed access. On this host that is a pair of
;; java.util.List interface calls, so ArrayList and LinkedList both work for BOTH
;; `setf!` and `incf!`. (An earlier version read with `nth` and wrote with `.set`;
;; that asymmetry made `incf!` unreachable on LinkedList for no reason.)

(defn -main [& _]
  (let [al (java.util.ArrayList. ^java.util.Collection (vec (repeat 3 0)))
        ll (java.util.LinkedList. ^java.util.Collection (vec (repeat 3 0)))
        hm (java.util.HashMap.)
        tm (java.util.TreeMap.)
        p1 (java.awt.Point. 0 0)
        p2 (java.awt.Rectangle. 1 2 3 4)]
    (setf! (elt al 1) 10)
    (incf!  (elt al 1) 5)
    (setf! (elt ll 1) 20)
    (incf!  (elt ll 1) 5)
    (setf! (gethash hm "k") 1)
    (setf! (gethash tm "k") 2)
    (setf! (get! p1 x) 30)
    (setf! (get! p2 width) 99)
    (println "ArrayList       " (.get al 1))
    (println "LinkedList      " (.get ll 1))
    (println "HashMap         " (.get hm "k"))
    (println "TreeMap         " (.get tm "k"))
    (println "Point.x         " (.x ^java.awt.Point p1))
    (println "Rectangle.width " (.-width ^java.awt.Rectangle p2))))