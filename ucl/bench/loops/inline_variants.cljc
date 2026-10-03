(ns squintcode.inline-variants
  #?(:cljs (:require-macros [squintcode.macros :as cl])))
(defn loop-core-minmax [prices]
  (let [n (cl/length prices)]
    (loop [i 0 lo js/Infinity best 0]
      (if (< i n) (let [p (cl/aref prices i)] (recur (inc i) (min lo p) (max best (- p lo)))) best))))
(defn aloop-core-minmax [prices]
  (cl/aloop prices [lo js/Infinity best 0]
    (if it (recur (min lo it) (max best (- it lo))) best)))
(defn loop-inline [prices]
  (let [n (cl/length prices)]
    (loop [i 0 lo js/Infinity best 0]
      (if (< i n)
        (let [p (cl/aref prices i) d (- p lo)]
          (recur (inc i) (if (< p lo) p lo) (if (> d best) d best)))
        best))))
(defn aloop-inline [prices]
  (cl/aloop prices [lo js/Infinity best 0]
    (if it
      (let [d (- it lo)] (recur (if (< it lo) it lo) (if (> d best) d best)))
      best)))
