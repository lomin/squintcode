(ns ucl.kernels
  "LeetCode-shaped functions written with variables. Besides being tested in
   ucl.variables-test, their safety-0 Squint output must contain no IIFE
   (run-tests.sh checks): that is what variables are for (D29, D35)."
  (:require [ucl.api :as ucl]))

(ucl/defun count-steady-stretches (nums gap)
  "LeetCode 2762 for any gap: the subarrays whose max - min <= gap. Two
   monotonic deques of indices; inner loops bound in ucl/let."
  (declare (type simple-vector nums) (type fixnum gap))
  (let [n    (ucl/length nums)
        maxq (ucl/make-array n :element-type 'fixnum)
        minq (ucl/make-array n :element-type 'fixnum)]
    (ucl/let ((left 0) (maxh 0) (maxt 0) (minh 0) (mint 0) (total 0))
      (declare (type fixnum left maxh maxt minh mint) (type (signed-byte 53) total))
      (ucl/dotimes (r n total)
        (let [x (ucl/elt nums r)]
          (loop []
            (when (and (< minh mint) (> (- x (ucl/elt nums (ucl/elt minq minh))) gap))
              (ucl/setf left (inc (ucl/elt minq minh)))
              (ucl/incf minh)
              (recur)))
          (loop []
            (when (and (< maxh maxt) (> (- (ucl/elt nums (ucl/elt maxq maxh)) x) gap))
              (ucl/setf left (inc (ucl/elt maxq maxh)))
              (ucl/incf maxh)
              (recur)))
          (ucl/setf maxt (loop [j maxt]
                           (if (and (> j maxh) (<= (ucl/elt nums (ucl/elt maxq (dec j))) x))
                             (recur (dec j))
                             j)))
          (ucl/let ((j mint))
            (declare (type fixnum j))
            (loop []
              (when (and (> j minh) (>= (ucl/elt nums (ucl/elt minq (dec j))) x))
                (ucl/decf j)
                (recur)))
            (ucl/setf mint j))
          (ucl/setf (ucl/elt maxq maxt) r
                    (ucl/elt minq mint) r)
          (ucl/incf maxt)
          (ucl/incf mint)
          (ucl/incf total (- r left -1)))))))

(ucl/defun first-index-at-least (xs target)
  "A compound init: the loop's value is bound in ucl/let without an IIFE."
  (declare (type simple-vector xs) (type fixnum target))
  (ucl/let ((i (loop [i 0]
                 (cond (== i (ucl/length xs)) -1
                       (>= (ucl/elt xs i) target) i
                       :else (recur (inc i))))))
    (declare (type fixnum i))
    i))

(ucl/defun digit-sum (n)
  "An assigned parameter."
  (declare (type fixnum n))
  (ucl/let ((s 0))
    (declare (type fixnum s))
    (loop []
      (when (> n 0)
        (ucl/incf s (rem n 10))
        (ucl/setf n (quot n 10))
        (recur)))
    s))
