(ns ucl.lifting-test
  "Cases for the lifting pass (D51, §13): values and evaluation order of
   loop-shaped forms in expression position. They pass with today's IIFEs;
   the pass must keep every one."
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [ucl.lifted :as l]))

(defn arr [v] (ucl/make-array (count v) :element-type 'fixnum :initial-contents v))
(defn general [v] (ucl/make-array (count v) :initial-contents v))

(deftest lifted-values-test
  (testing "an if test, an argument"
    (is (== 1 (l/found-flag (arr [1 2 3]) 3)))
    (is (== 0 (l/found-flag (arr [1 2 3]) 9)))
    (is (== 3 (l/one-more-than-count (arr [3 1 3]) 3))))
  (testing "nested: a sequence function as another's sequence"
    (is (== 2 (l/odd-survivors (arr [1 2 3 5 1]) 1))))
  (testing "a curried form inlined into a predicate"
    (is (== 2 (l/rows-containing (general [(arr [1 3]) (arr [2]) (arr [3])]) 3))))
  (testing "a literal fn whose body is a loop"
    (is (== 2 (l/keys-present (arr [1 2 3]) (arr [2 9 3])))))
  (testing "branches"
    (is (== 12 (l/branch-local (arr [1 2 1]) 1)))
    (is (== -2 (l/branch-local (arr [-1 2 -3]) 0))))
  (testing "and"
    (is (== 2 (l/guarded (arr [1 2]) 2)))
    (is (== -1 (l/guarded (arr [1 2]) 0)))
    (is (== -1 (l/guarded (arr [1 2]) 5))))
  (testing "in a loop body, reading the loop variable"
    (is (== 6 (l/shrinking-counts (arr [1 2 3])))))
  (testing "two ucl/loops"
    (is (== 13 (l/sum-plus-max (arr [1 5 2]))))))

(deftest lifted-order-test
  (testing "left to right around the lifted form"
    (is (== 4 (l/reversed-then-read (arr [3 1 2]))))
    (is (== 4 (l/assigned-first (arr [1 2 3 4]))))
    (is (== 3 (l/read-before (arr [1 2]))))))

;; Effects observed through an atom: tests may use anything.

(defn contents-of [v] (loop [i 0 acc []] (if (< i (ucl/length v)) (recur (inc i) (conj acc (ucl/elt v i))) acc)))

(ucl/defun guarded-calls (nums k calls)
  (declare (type fixnum-vector nums) (type fixnum k))
  (if (and (> k 0) (ucl/find-if (fn [x] (swap! calls inc) (== x k)) nums)) k -1))

(ucl/defun branch-calls (nums k calls)
  (declare (type fixnum-vector nums) (type fixnum k))
  (if (> k 0)
    (ucl/count-if (fn [x] (swap! calls + 1) (== x k)) nums)
    (ucl/count-if (fn [x] (swap! calls + 100) (< x 0)) nums)))

(ucl/defun argument-order (nums log)
  (declare (type fixnum-vector nums))
  (+ (do (swap! log conj :before) 0)
     (ucl/count-if (fn [x] (swap! log conj x) (odd? x)) nums)
     (do (swap! log conj :after) 0)))

(ucl/defun counts-per-row (rows k)
  "A curried form passed as a value: a function, never lifted."
  (declare (type simple-vector rows) (type fixnum k))
  (mapv (ucl/count k) (contents-of rows)))


(deftest lifted-effects-test
  (testing "and short-circuits: no call when the guard fails"
    (let [calls (atom 0)]
      (is (== -1 (guarded-calls (arr [1 2]) 0 calls)))
      (is (== 0 @calls)))
    (let [calls (atom 0)]
      (is (== 2 (guarded-calls (arr [1 2]) 2 calls)))
      (is (== 2 @calls))))
  (testing "only the branch taken runs"
    (let [calls (atom 0)]
      (is (== 1 (branch-calls (arr [1 2]) 1 calls)))
      (is (== 2 @calls))))
  (testing "arguments run left to right, the lifted one in its place"
    (let [log (atom [])]
      (is (== 2 (argument-order (arr [1 2 3]) log)))
      (is (= [:before 1 2 3 :after] @log))))
  (testing "a curried form as a value stays a function"
    (is (= [2 0] (counts-per-row (general [(arr [3 3]) (arr [1])]) 3)))))
