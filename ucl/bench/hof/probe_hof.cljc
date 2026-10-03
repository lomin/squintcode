;; Built as a temporary src/squintcode problem with `bb build-one probe_hof`;
;; needs ucl to accept the type `function` (contract canonical-type -> :function,
;; Dart type-hint -> Function), a probe-only patch. probe_hof.js / .dart are the
;; builds measured by run.mjs / run.dart.
(ns squintcode.probe-hof
  (:require [ucl.api :as ucl]))

;; Probe: runtime higher-order functions against hand-written loops.

;; --- the higher-order functions -------------------------------------------

(ucl/defun fold (f init xs)
  (declare (type fixnum-vector xs) (type function f))
  (let [n (ucl/length xs)]
    (loop [i 0 acc init]
      (if (< i n)
        (recur (inc i) (f acc (ucl/elt xs i)))
        acc))))

(ucl/defun each (f xs)
  (declare (type fixnum-vector xs) (type function f))
  (ucl/dotimes (i (ucl/length xs))
    (f (ucl/elt xs i))))

;; --- A: sum ---------------------------------------------------------------

(ucl/defun sumLoop (nums)
  (declare (type fixnum-vector nums))
  (let [n (ucl/length nums)]
    (loop [i 0 acc 0]
      (if (< i n) (recur (inc i) (+ acc (ucl/elt nums i))) acc))))

(ucl/defun sumFold (nums)
  (declare (type fixnum-vector nums))
  (fold (fn [acc x] (+ acc x)) 0 nums))

;; --- B: closure over a parameter: count elements >= k ---------------------

(ucl/defun countLoop (nums k)
  (declare (type fixnum-vector nums) (type fixnum k))
  (let [n (ucl/length nums)]
    (loop [i 0 acc 0]
      (if (< i n)
        (recur (inc i) (if (>= (ucl/elt nums i) k) (inc acc) acc))
        acc))))

(ucl/defun countFold (nums k)
  (declare (type fixnum-vector nums) (type fixnum k))
  (fold (fn [acc x] (if (>= x k) (inc acc) acc)) 0 nums))

;; --- C: closure that assigns ucl/let variables: maxProfit -----------------

(ucl/defun profitLoop (prices)
  (declare (type fixnum-vector prices))
  (let [n (ucl/length prices)]
    (loop [i 0 lo ucl/most-positive-fixnum best 0]
      (if (< i n)
        (let [p (ucl/elt prices i)]
          (recur (inc i) (ucl/min lo p) (ucl/max best (- p lo))))
        best))))

(ucl/defun profitEach (prices)
  (declare (type fixnum-vector prices))
  (ucl/let ((lo ucl/most-positive-fixnum) (best 0))
    (declare (type fixnum lo best))
    (each (fn [^int p]
            (ucl/setf lo (ucl/min lo p))
            (ucl/setf best (ucl/max best (- p lo))))
          prices)
    best))

(ucl/defun profitEach2 (prices)
  (declare (type fixnum-vector prices))
  (ucl/let ((lo ucl/most-positive-fixnum) (best 0))
    (declare (type fixnum lo best))
    (each (fn [^int p]
            (ucl/setf lo (ucl/min lo p))
            (let [d (- p lo)]
              (ucl/setf best (ucl/max best d))))
          prices)
    best))
