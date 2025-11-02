(ns squintcode.maxprofit
  #?(:cljs (:require-macros [squintcode.macros :refer [aloop]]))
  #?(:clj  (:require [squintcode.macros :refer [aloop]])))

(defn infinity []
  #?(:clj  Double/POSITIVE_INFINITY
     :cljs js/Infinity))

(defn maxProfit [prices]
  (aloop prices p
         [min-price (infinity)
          max-profit 0]
         (if p
           (recur (min min-price p)
                  (max max-profit (- p min-price)))
           max-profit)))


(comment
  (maxProfit [7,1,5,3,6,4])
  (maxProfit [7,6,4,3,1])
  (macroexpand '(aloop prices p
                       [min-price (infinity)
                        max-profit 0]
                       (if p
                         (recur (min min-price p)
                                (max max-profit (- p min-price)))
                         max-profit)))
  (comment))