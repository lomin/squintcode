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

(ucl/defun first-index-at-least-exit (xs target)
  "An exit from the function out of a loop (D55, D56): static, no IIFE."
  (declare (type fixnum-vector xs) (type fixnum target))
  (ucl/dotimes (i (ucl/length xs))
    (when (>= (ucl/elt xs i) target)
      (ucl/return-from first-index-at-least-exit i)))
  -1)

(ucl/defun pair-with-sum (xs target)
  "An exit through two nested loops: the result and flag path (D56)."
  (declare (type fixnum-vector xs) (type fixnum target))
  (ucl/let ((hits 0))
    (declare (type fixnum hits))
    (ucl/dotimes (i (ucl/length xs))
      (ucl/dotimes (j i)
        (when (== target (+ (ucl/elt xs i) (ucl/elt xs j)))
          (ucl/return-from pair-with-sum (+ (* 1000 j) i))))
      (ucl/incf hits))
    (- -1 hits)))

(ucl/defun num-subarrays-with-sum-loop (nums goal)
  "ucl/loop (D53): LeetCode 930 as README §4.3 writes it."
  (declare (type fixnum-vector nums) (type fixnum goal))
  (let [freq (ucl/make-array (inc (ucl/length nums)) :element-type 'fixnum)]
    (ucl/setf (ucl/elt freq 0) 1)
    (ucl/loop for x across nums
              sum x into s of-type fixnum
              when (>= s goal) sum (ucl/elt freq (- s goal))
              do (ucl/incf (ucl/elt freq s)))))
;; Sequence functions (D44–D51): each in return position or a ucl/let init,
;; where it is a statement.

(ucl/defun count-greater (nums k)
  (declare (type fixnum-vector nums) (type fixnum k))
  (ucl/count-if (fn [x] (> x k)) nums))

(ucl/defun largest (nums)
  (declare (type fixnum-vector nums))
  (ucl/reduce ucl/max nums))

(ucl/defun first-odd-index (nums)
  (declare (type fixnum-vector nums))
  (ucl/let ((i (ucl/position-if (fn [x] (odd? x)) nums)))
    (if (nil? i) -1 i)))

(ucl/defun all-in-range? (nums lo hi)
  (declare (type fixnum-vector nums) (type fixnum lo hi))
  (ucl/every (fn [x] (and (<= lo x) (<= x hi))) nums))

(ucl/defun window-odd-counts (nums w)
  "A sequence function as a ucl/let init inside a hot loop: a statement."
  (declare (type fixnum-vector nums) (type fixnum w))
  (ucl/let ((total 0))
    (declare (type fixnum total))
    (ucl/dotimes (s (- (ucl/length nums) w -1) total)
      (ucl/let ((c (ucl/count-if (fn [x] (odd? x)) nums :start s :end (+ s w))))
        (declare (type fixnum c))
        (ucl/incf total c)))))
