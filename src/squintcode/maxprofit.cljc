(ns squintcode.maxprofit
  #?(:cljs (:require-macros [squintcode.macros :as cl]))
  #?(:clj  (:require [squintcode.macros :as cl])))

(defn infinity []
  #?(:clj  Double/POSITIVE_INFINITY
     :cljs js/Infinity))

(defn maxProfit [prices]
  (cl/aloop prices
            [min-price (infinity)
             max-profit 0]
            (if it
              (recur (min min-price it)
                     (max max-profit (- it min-price)))
              max-profit)))


(comment
  (maxProfit [7,1,5,3,6,4])
  (maxProfit [7,6,4,3,1])
  (comment))