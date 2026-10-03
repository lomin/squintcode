(ns squintcode.lc-2762-continuous-subarrays-loop
  (:require [ucl.api :as ucl]))

;; LeetCode 2762, written with ucl/loop (README D62); compare
;; squintcode.lc-2762-continuous-subarrays, whose comment explains the two
;; monotonic deques. Each subarray ending at r with start >= left is steady,
;; so r adds r - left + 1.

(ucl/defun count-steady-stretches (nums gap)
  (declare (type fixnum-vector nums) (type fixnum gap))
  (let [n    (ucl/length nums)
        maxq (ucl/make-array n :element-type 'fixnum)
        minq (ucl/make-array n :element-type 'fixnum)]
    (ucl/loop with left of-type fixnum = 0
              with maxh of-type fixnum = 0 and maxt of-type fixnum = 0
              with minh of-type fixnum = 0 and mint of-type fixnum = 0
              for x across nums
              for r from 0
              ;; shrink: x is the new extreme, so drop what is too far from it
              do (ucl/loop while (and (< minh mint) (> (- x (ucl/elt nums (ucl/elt minq minh))) gap))
                           do (ucl/setf left (inc (ucl/elt minq minh)))
                              (ucl/incf minh))
                 (ucl/loop while (and (< maxh maxt) (> (- (ucl/elt nums (ucl/elt maxq maxh)) x) gap))
                           do (ucl/setf left (inc (ucl/elt maxq maxh)))
                              (ucl/incf maxh))
                 ;; push r, keeping maxq decreasing and minq increasing
                 (ucl/loop while (and (< maxh maxt) (<= (ucl/elt nums (ucl/elt maxq (dec maxt))) x))
                           do (ucl/decf maxt))
                 (ucl/loop while (and (< minh mint) (>= (ucl/elt nums (ucl/elt minq (dec mint))) x))
                           do (ucl/decf mint))
                 (ucl/setf (ucl/elt maxq maxt) r
                           (ucl/elt minq mint) r)
                 (ucl/incf maxt)
                 (ucl/incf mint)
              ;; at most n(n+1)/2 ~ 2*10^10: a (signed-byte 53)
              sum (- r left -1) of-type (signed-byte 53))))

(ucl/defun continuousSubarrays (nums)
  (declare (type fixnum-vector nums))
  (count-steady-stretches nums 2))
