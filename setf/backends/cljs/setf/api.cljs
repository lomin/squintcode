(ns setf.api
  "ClojureScript host backend for `setf` -- runtime half.")

(defn make-arr
  "A mutable indexed sequence. Any object with an integer-keyed `[]` works:
   `Array`, `Uint32Array`, and anything else the host indexes that way."
  [n]
  (array n))

(defn make-map
  "A mutable keyed collection."
  []
  (js/Map.))

(defn make-obj
  "A mutable object with public fields."
  []
  #js {:x 0 :y 0})
