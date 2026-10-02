(ns setf.api
  "PROTOTYPE — ClojureScript backend, RUNTIME half (.cljs).")

(defn make-arr [n] (array n))
(defn make-map [] (js/Map.))
(defn make-obj [] #js {:x 0})
(defn dump [a m o]
  [(aget a 1) (.get m "k") (.-x o)])
