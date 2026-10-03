(ns squintcode.maxprofit
  (:require [ucl.api :as ucl]))

(ucl/defun maxProfit (prices)
  (declare (type simple-vector prices))
  (let [n (ucl/length prices)]
    (loop [i 0
           min-price ucl/double-float-positive-infinity
           max-profit 0]
      (if (< i n)
        (let [price (ucl/elt prices i)]
          (recur (inc i)
                 (ucl/min min-price price)
                 (ucl/max max-profit (- price min-price))))
        max-profit))))

(comment
  (maxProfit (ucl/make-array 6 :initial-contents [7 1 5 3 6 4]))
  (maxProfit (ucl/make-array 5 :initial-contents [7 6 4 3 1])))
