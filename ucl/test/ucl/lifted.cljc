(ns ucl.lifted
  "Sequence functions and ucl/loop in expression position. Each of these is
   an IIFE on Squint today (D51, §13); once the lifting pass runs, run-tests.sh
   checks that none is. Solution-shaped: plain Dart at safety 0. Their values,
   and the evaluation order around each, are tested in ucl.lifting-test."
  (:require [ucl.api :as ucl]))

(ucl/defun found-flag (nums k)
  "In an if test."
  (declare (type fixnum-vector nums) (type fixnum k))
  (if (ucl/find k nums) 1 0))

(ucl/defun one-more-than-count (nums k)
  "An argument of an operator."
  (declare (type fixnum-vector nums) (type fixnum k))
  (+ 1 (ucl/count k nums)))

(ucl/defun odd-survivors (nums k)
  "A sequence function as another's sequence: the inner one is bound by the
   outer expansion's own let, after its predicate."
  (declare (type fixnum-vector nums) (type fixnum k))
  (ucl/count-if (fn [x] (odd? x)) (ucl/remove k nums)))

(ucl/defun rows-containing (rows k)
  "A curried form inlined into a predicate: the expansion puts a find in its
   loop's if test -- a call written nowhere in the source."
  (declare (type simple-vector rows) (type fixnum k))
  (ucl/count-if (ucl/find k) rows))

(ucl/defun keys-present (nums ks)
  "A literal fn whose body is a loop, in the outer loop's test."
  (declare (type fixnum-vector nums ks))
  (ucl/count-if (fn [k] (ucl/find k nums)) ks))

(ucl/defun branch-local (nums k)
  "Each branch lifts its own, and only the branch taken runs."
  (declare (type fixnum-vector nums) (type fixnum k))
  (if (> k 0)
    (+ 10 (ucl/count k nums))
    (- (ucl/count-if (fn [x] (< x 0)) nums))))

(ucl/defun guarded (nums k)
  "and: the find runs only when the guard holds."
  (declare (type fixnum-vector nums) (type fixnum k))
  (if (and (> k 0) (ucl/find k nums)) k -1))

(ucl/defun shrinking-counts (nums)
  "In a loop body, before its recur, reading the loop's own variable."
  (declare (type fixnum-vector nums))
  (loop [i 0 acc 0]
    (if (< i (ucl/length nums))
      (recur (inc i) (+ acc (ucl/count-if (fn [x] (> x i)) nums)))
      acc)))

(ucl/defun sum-plus-max (nums)
  "Two ucl/loops, one a plain let init, one an argument."
  (declare (type fixnum-vector nums))
  (let [total (ucl/loop for x across nums sum x of-type fixnum)]
    (+ total (ucl/loop for x across nums maximize x of-type fixnum))))

(ucl/defun reversed-then-read (v)
  "Left to right: the elt after must see what nreverse did."
  (declare (type fixnum-vector v))
  (+ (ucl/position 3 (ucl/nreverse v)) (ucl/elt v 0)))

(ucl/defun assigned-first (nums)
  "Left to right: the setf before the count happens before it."
  (declare (type fixnum-vector nums))
  (ucl/let ((c 0))
    (declare (type fixnum c))
    (+ (ucl/setf c 2) (ucl/count-if (fn [x] (> x c)) nums))))

(ucl/defun read-before (nums)
  "Left to right: c is read before the count assigns it."
  (declare (type fixnum-vector nums))
  (ucl/let ((c 1))
    (declare (type fixnum c))
    (+ c (ucl/count-if (fn [x] (ucl/setf c 100) (> x 0)) nums))))
