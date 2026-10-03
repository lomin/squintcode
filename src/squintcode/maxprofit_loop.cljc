(ns squintcode.maxprofit-loop
  (:require [ucl.api :as ucl]))

;; LeetCode 121, written with ucl/loop (README §4.3, D62); compare
;; squintcode.maxprofit. Selling at p earns p minus the lowest price so far.

(ucl/defun maxProfit (prices)
  (declare (type fixnum-vector prices))
  (ucl/loop for p across prices
            minimize p into lo of-type fixnum
            maximize (- p lo) of-type fixnum))
