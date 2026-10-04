(ns squintcode.lc-88-merge-sorted-array-seq
  (:refer-clojure :exclude [merge])
  (:require [ucl.api :as ucl]))

;; LeetCode 88 with sequence functions (README §4.2): merge the first m
;; elements of nums1 with nums2, then copy the result back into nums1.

(ucl/defun merge (nums1 m nums2 n)
  (declare (type fixnum-vector nums1 nums2) (type fixnum m n))
  (ucl/replace nums1 (ucl/merge '(vector fixnum) (ucl/subseq nums1 0 m) (ucl/subseq nums2 0 n)
                                (fn [a b] (< a b))))
  nil)
