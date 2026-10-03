(ns ucl.structs-test
  "defstruct, slot-value, defmethod, with-slots, and the LeetCode fixtures."
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.leetcode]
            [ucl.api :as ucl]))

(ucl/defun squares (n)
  (declare (type fixnum n))
  (let [v (ucl/make-array n :element-type '(signed-byte 53))]
    (loop [i 0]
      (if (< i n)
        (do (ucl/setf (ucl/elt v i) (* i i)) (recur (inc i)))
        v))))

(ucl/defstruct (Squares (:constructor Squares (n &optional (offset 0)
                                                &aux (table (squares n)))))
  (table nil :type sb53-vector)
  (offset 0)
  (hits 0 :type fixnum))

(ucl/defmethod lookup ((self Squares) i)
  "The square of i, plus the offset."
  (ucl/with-slots (table offset hits) self
    (ucl/incf hits)
    (+ offset (ucl/elt table i))))

(ucl/defmethod reset-hits ((self Squares))
  (ucl/with-slots (hits) self
    (let [old hits]
      (ucl/setf hits 0)
      old)))

(ucl/defstruct Pair (left 1) (right 2))

(deftest defstruct-test
  (testing "a BOA constructor with &aux and &optional"
    ;; Called as a function: the portable form. LeetCode's `new Squares(..)`
    ;; reaches the same body on the JS hosts; on the JVM `new` would reach
    ;; the deftype's slot-positional constructor instead.
    (let [s (Squares 4)
          t (Squares 3 100)
          u (Squares 2 1)]
      (is (= 9 (lookup s 3)))
      (is (= 104 (lookup t 2)))
      (is (= 2 (lookup u 1)))
      (is (= 1 (ucl/slot-value u 'offset)))))
  (testing "slot-value is a place"
    (let [s (Squares 2)]
      (ucl/setf (ucl/slot-value s 'offset) 5)
      (ucl/incf (ucl/slot-value s 'offset) 2)
      (is (= 7 (ucl/slot-value s 'offset)))))
  (testing "the default keyword constructor"
    (let [p (make-Pair :right 9)]
      (is (= 1 (ucl/slot-value p 'left)))
      (is (= 9 (ucl/slot-value p 'right))))))

(deftest with-slots-test
  (testing "with-slots reads and writes slots, and returns through methods"
    (let [s (Squares 3)]
      (lookup s 1)
      (lookup s 2)
      (is (= 2 (reset-hits s)))
      (is (= 0 (ucl/slot-value s 'hits)))))
  (testing "a local of the same name shadows the slot"
    (let [s (Squares 2 10)]
      (is (= 3 (ucl/with-slots (offset) s
                 (let [offset 3] offset))))
      (is (= 10 (ucl/with-slots (offset) s offset))))))

(deftest leetcode-fixture-test
  (testing "LeetCode's ListNode and TreeNode are built with new"
    (let [l (new ListNode 1 (new ListNode 2 nil))
          t (new TreeNode 1 nil (new TreeNode 2 nil nil))]
      (is (= 2 (ucl/slot-value (ucl/slot-value l 'next) 'val)))
      (is (nil? (ucl/slot-value t 'left)))
      (ucl/setf (ucl/slot-value l 'next) nil)
      (is (nil? (ucl/slot-value l 'next)))
      (is (= 2 (ucl/slot-value (ucl/slot-value t 'right) 'val))))))
