(ns squintcode.lc-2762-continuous-subarrays
  (:require [ucl.api :as ucl]))

;; LeetCode 2762: Continuous Subarrays -- count the subarrays whose
;; max - min <= 2. `count-steady-stretches` is the general form, for any gap
;; (the "water pump" variant: readings 0..1000, gap 0..1000, n <= 2*10^5).
;;
;; Approach: sliding window with two monotonic deques of indices, kept in
;; Int32Arrays with head/tail pointers -- maxq holds decreasing values (its
;; head is the window max), minq increasing values (its head is the min).
;; Each subarray ending at r with start >= left is steady, so r adds
;; r - left + 1.
;;
;; When x = nums[r] arrives it is pushed onto both deques, popping from the
;; back whatever it dominates. Then, while the heads differ by more than gap,
;; the head with the smaller index leaves the window, and left moves past it.
;;
;; The window state lives in ucl/let variables, so each inner loop is a
;; statement that updates it -- a plain `while` on every host (ucl D29).
;;
;; The answer is at most n(n+1)/2 ~ 2*10^10: a (signed-byte 53), exact as a
;; JS number and a long on the JVM.
;;
;; Time: O(n) -- every index is pushed and popped at most once per deque.
;; Space: O(n)

(ucl/defun count-steady-stretches (nums gap)
  (declare (type fixnum-vector nums) (type fixnum gap))
  (let [n    (ucl/length nums)
        maxq (ucl/make-array n :element-type 'fixnum)
        minq (ucl/make-array n :element-type 'fixnum)]
    (ucl/let ((left 0) (maxh 0) (maxt 0) (minh 0) (mint 0) (total 0))
      (declare (type fixnum left maxh maxt minh mint) (type (signed-byte 53) total))
      (ucl/dotimes (r n total)
        (let [x (ucl/elt nums r)]
          ;; shrink: x is the new extreme, so drop what is too far from it
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
          ;; push r, keeping maxq decreasing and minq increasing
          (loop []
            (when (and (< maxh maxt) (<= (ucl/elt nums (ucl/elt maxq (dec maxt))) x))
              (ucl/decf maxt)
              (recur)))
          (loop []
            (when (and (< minh mint) (>= (ucl/elt nums (ucl/elt minq (dec mint))) x))
              (ucl/decf mint)
              (recur)))
          (ucl/setf (ucl/elt maxq maxt) r
                    (ucl/elt minq mint) r)
          (ucl/incf maxt)
          (ucl/incf mint)
          (ucl/incf total (- r left -1)))))))

(ucl/defun continuousSubarrays (nums)
  (declare (type fixnum-vector nums))
  (count-steady-stretches nums 2))

(comment
  ;; expecting 7, then 8 (LeetCode example 1)
  (count-steady-stretches (ucl/make-array 4 :initial-contents [30 50 40 80]) 20)
  (continuousSubarrays (ucl/make-array 4 :initial-contents [5 4 2 4])))
