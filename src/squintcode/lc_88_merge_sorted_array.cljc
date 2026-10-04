(ns squintcode.lc-88-merge-sorted-array
  (:refer-clojure :exclude [merge])
  (:require [ucl.api :as ucl]))

;; LeetCode 88. Fill nums1 from its end: the larger of the two last
;; unmerged elements goes last, so nothing unread is overwritten.

(ucl/defun merge (nums1 m nums2 n)
  (declare (type fixnum-vector nums1 nums2) (type fixnum m n))
  (ucl/let ((i (dec m)) (j (dec n)) (k (dec (+ m n))))
    (declare (type fixnum i j k))
    (loop []
      (when (>= j 0)
        (if (and (>= i 0) (> (ucl/elt nums1 i) (ucl/elt nums2 j)))
          (do (ucl/setf (ucl/elt nums1 k) (ucl/elt nums1 i))
              (ucl/decf i))
          (do (ucl/setf (ucl/elt nums1 k) (ucl/elt nums2 j))
              (ucl/decf j)))
        (ucl/decf k)
        (recur)))
    nil))
