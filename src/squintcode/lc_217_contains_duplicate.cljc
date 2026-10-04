(ns squintcode.lc-217-contains-duplicate
  (:require [ucl.api :as ucl]))

;; LeetCode 217. A hash table of the values seen; the first one seen again
;; answers true.

(ucl/defun containsDuplicate (nums)
  (declare (type fixnum-vector nums))
  (let [seen (ucl/make-hash-table)]
    (ucl/dotimes (i (ucl/length nums) false)
      (let [x (ucl/elt nums i)]
        (when (ucl/gethash x seen)
          (ucl/return true))
        (ucl/setf (ucl/gethash x seen) true)))))
