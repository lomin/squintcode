(ns squintcode.lc-977-squares-of-a-sorted-array
  (:require [ucl.api :as ucl]))

;; LeetCode 977. The largest square is at one end or the other: fill the
;; result from its end, taking the larger end each time, O(n).

(ucl/defun sortedSquares (nums)
  (declare (type fixnum-vector nums))
  (let [n (ucl/length nums)
        result (ucl/make-array n :element-type 'fixnum)]
    (ucl/let ((i 0) (j (dec n)) (k (dec n)))
      (declare (type fixnum i j k))
      (loop []
        (when (>= k 0)
          (let [a (* (ucl/elt nums i) (ucl/elt nums i))
                b (* (ucl/elt nums j) (ucl/elt nums j))]
            (if (> a b)
              (do (ucl/setf (ucl/elt result k) a) (ucl/incf i))
              (do (ucl/setf (ucl/elt result k) b) (ucl/decf j)))
            (ucl/decf k)
            (recur)))))
    result))
