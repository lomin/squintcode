(ns setf.api
  "ClojureScript host backend for `setf` -- runtime half.")

(defn make-arr
  "A mutable indexed sequence of length `n`, every slot 0 -- as on every host.
   `elt` itself works on any object with an integer-keyed `[]`: `Array`,
   `Uint32Array`, and anything else the host indexes that way."
  [n]
  (.fill (js/Array. n) 0))

(defn make-map
  "A mutable keyed collection."
  []
  (js/Map.))

(defn make-obj
  "A mutable object with public fields."
  []
  #js {:x 0 :y 0})
