(ns ucl.blocks-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [ucl.kernels :as k]))

(defn arr [v] (ucl/make-array (count v) :element-type 'fixnum :initial-contents v))

(ucl/defun pair-summing-to (nums target)
  (declare (type fixnum-vector nums) (type fixnum target))
  (ucl/dotimes (i (ucl/length nums))
    (ucl/dotimes (j i)
      (when (== target (+ (ucl/elt nums i) (ucl/elt nums j)))
        (ucl/return-from pair-summing-to [j i]))))
  nil)

(ucl/defun first-negative (nums)
  (declare (type fixnum-vector nums))
  (ucl/dotimes (i (ucl/length nums) -1)
    (when (neg? (ucl/elt nums i))
      (ucl/return i))))

(ucl/defstruct (Finder (:constructor Finder (xs)))
  (xs nil :type fixnum-vector))

(ucl/defmethod index-of ((this Finder) x)
  (ucl/with-slots (xs) this
    (ucl/dotimes (i (ucl/length xs))
      (when (== x (ucl/elt xs i))
        (ucl/return-from index-of i))))
  -1)

(deftest block-test
  (testing "a block's value is its last form, or what leaves it"
    (is (== 3 (ucl/block b 1 2 3)))
    (is (== 2 (ucl/block b 1 (ucl/return-from b 2) 3)))
    (is (nil? (ucl/block nil (ucl/return) 3))))
  (testing "an exit in statement position skips the rest of the block"
    (let [log (atom [])
          run (fn [x]
                (ucl/block b
                  (swap! log conj :a)
                  (when (> x 0) (ucl/return-from b :exited))
                  (swap! log conj :b)
                  :normal))]
      (is (= :exited (run 1)))
      (is (= [:a] @log))
      (is (= :normal (run 0)))
      (is (= [:a :a :b] @log))))
  (testing "both branches exit"
    (is (= :neg (ucl/block b (if (neg? -1) (ucl/return-from b :neg) (ucl/return-from b :pos)) :never))))
  (testing "cond and case clauses exit"
    (let [classify (fn [x]
                     (ucl/block c
                       (cond (neg? x) (ucl/return-from c :neg)
                             (zero? x) nil
                             :else (ucl/return-from c :pos))
                       :zero))
          name-of (fn [x]
                    (ucl/block c
                      (case x 1 (ucl/return-from c :one) 2 (ucl/return-from c :two) nil)
                      :other))]
      (is (= [:neg :zero :pos] (map classify [-5 0 5])))
      (is (= [:one :two :other] (map name-of [1 2 3])))))
  (testing "two branches complete normally: the rest is not copied, and still runs once"
    (let [log (atom [])
          run (fn [x]
                (ucl/block b
                  (if (> x 0)
                    (do (when (> x 10) (ucl/return-from b :big)) (swap! log conj :pos))
                    (swap! log conj :non-pos))
                  (swap! log conj :rest)
                  :done))]
      (is (= :big (run 20)))
      (is (= :done (run 5)))
      (is (= :done (run -5)))
      (is (= [:pos :rest :non-pos :rest] @log))))
  (testing "the rest is not moved into a let that shadows its names"
    (let [x :outer]
      (is (= :outer (ucl/block b
                      (let [x :inner] (when (= x :never) (ucl/return-from b x)))
                      x)))
      (is (= :inner (ucl/block b
                      (let [x :inner] (when (= x :inner) (ucl/return-from b x)))
                      x)))))
  (testing "an inner block of the same name has its own exits"
    (is (= :after (ucl/block b
                    (ucl/block b (ucl/return-from b :inner) :never)
                    :after)))))

(deftest loop-exit-test
  (testing "ucl/dotimes is a block nil"
    (is (== 2 (first-negative (arr [3 1 -4 -1]))))
    (is (== -1 (first-negative (arr [3 1 4])))))
  (testing "return-from the function, out of two nested loops"
    (is (= [1 3] (pair-summing-to (arr [5 4 9 7]) 11)))
    (is (nil? (pair-summing-to (arr [1 2]) 10))))
  (testing "return leaves the innermost nil block only"
    (let [log (atom [])]
      (ucl/dotimes (i 3)
        (ucl/dotimes (j 3)
          (when (== j 1) (ucl/return))
          (swap! log conj [i j])))
      (is (= [[0 0] [1 0] [2 0]] @log))))
  (testing "an exit through a nested loop also leaves the rest of the outer body"
    (let [log (atom [])]
      (is (= [1 2] (ucl/block outer
                     (ucl/dotimes (i 3)
                       (ucl/dotimes (j 3)
                         (when (== 3 (+ i j)) (ucl/return-from outer [i j])))
                       (swap! log conj i))
                     :never)))
      (is (= [0] @log))))
  (testing "an exit from a Clojure loop/recur \"while\" loop"
    (is (== 4 (ucl/block nil
                (loop [i 0]
                  (when (< i 10)
                    (when (== i 4) (ucl/return i))
                    (recur (inc i))))
                :never))))
  (testing "an exit that assigns a variable first"
    (is (== 6 (ucl/let ((total 0))
                (declare (type fixnum total))
                (ucl/dotimes (i 10)
                  (ucl/incf total i)
                  (when (> total 5) (ucl/return)))
                total))))
  (testing "a block bound to a variable is a statement (D35)"
    (is (== 3 (ucl/let ((found (ucl/dotimes (i 10 -1) (when (== i 3) (ucl/return i)))))
                (declare (type fixnum found))
                found))))
  (testing "a method's block is named after the method"
    (is (== 2 (index-of (Finder (arr [5 6 7])) 7)))
    (is (== -1 (index-of (Finder (arr [5 6 7])) 8))))
  (testing "the kernel: an exit from a function out of a loop"
    (is (== 2 (k/first-index-at-least-exit (arr [1 3 5 7]) 4)))
    (is (== -1 (k/first-index-at-least-exit (arr [1 3]) 4)))))

(deftest pair-kernel-test
  (testing "the kernel: an exit through two nested loops"
    (is (== 1003 (k/pair-with-sum (arr [5 4 9 7]) 11)))
    (is (== -3 (k/pair-with-sum (arr [1 2]) 10)))))
