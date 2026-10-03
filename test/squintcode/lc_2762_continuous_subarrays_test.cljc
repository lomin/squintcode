(ns squintcode.lc-2762-continuous-subarrays-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [squintcode.lc-2762-continuous-subarrays
             :refer [count-steady-stretches continuousSubarrays]]))

(defn arr [v] (ucl/make-array (count v) :element-type 'fixnum :initial-contents v))   ; LeetCode's int[]

(defn brute
  "Every (start, end) pair, checked directly."
  [v gap]
  (count (for [i (range (count v))
               j (range i (count v))
               :let [s (subvec v i (inc j))]
               :when (<= (- (apply max s) (apply min s)) gap)]
           1)))

(deftest leetcode-examples-test
  (is (== 8 (continuousSubarrays (arr [5 4 2 4]))))
  (is (== 6 (continuousSubarrays (arr [1 2 3])))))

(deftest steady-stretches-test
  (testing "the water pump example"
    (is (== 7 (count-steady-stretches (arr [30 50 40 80]) 20))))
  (testing "edge cases"
    (is (== 0 (count-steady-stretches (arr []) 5)))
    (is (== 1 (count-steady-stretches (arr [1000]) 0)))
    (is (== 3 (count-steady-stretches (arr [0 1000 0]) 999)))
    (is (== 6 (count-steady-stretches (arr [0 1000 0]) 1000)))
    (is (== 10 (count-steady-stretches (arr [4 4 4 4]) 0)))))

(deftest against-brute-force-test
  (doseq [[v gap] [[[3 1 4 1 5 9 2 6 5 3 5 8 9 7 9] 3]
                   [[1 3 2 5 4 7 6 9 8] 1]
                   [[9 8 7 6 5 4 3 2 1] 2]
                   [[1 2 3 4 5 6 7 8 9] 4]
                   [[5 5 1 1 5 5 1 1] 3]
                   [[0 7 3 7 0 3 3 7 0] 4]]]
    (is (== (brute v gap) (count-steady-stretches (arr v) gap)))))

(deftest large-answer-test
  (testing "n(n+1)/2 for 200000 equal readings exceeds a fixnum"
    (is (== 20000100000
            (count-steady-stretches (ucl/make-array 200000 :element-type 'fixnum :initial-element 7) 0)))))
