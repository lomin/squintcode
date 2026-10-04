(ns squintcode.lc-1351-count-negative-numbers-in-a-sorted-matrix-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [squintcode.lc-1351-count-negative-numbers-in-a-sorted-matrix :as loop-version]
            [squintcode.lc-1351-count-negative-numbers-in-a-sorted-matrix-seq :as seq-version]))

(defn arr [v] (ucl/make-array (count v) :element-type 'fixnum :initial-contents v))

(defn grid [rows] (ucl/make-array (count rows) :initial-contents (mapv arr rows)))   ; int[][]

(deftest count-negatives-test
  (doseq [[label f] [["staircase" loop-version/countNegatives] ["count-if" seq-version/countNegatives]]]
    (testing (str label ": LeetCode examples")
      (is (== 8 (f (grid [[4 3 2 -1] [3 2 1 -1] [1 1 -1 -2] [-1 -1 -2 -3]]))))
      (is (== 0 (f (grid [[3 2] [1 0]])))))
    (testing (str label ": edges")
      (is (== 1 (f (grid [[-1]]))))
      (is (== 6 (f (grid [[-1 -2 -3] [-4 -5 -6]]))))
      (is (== 3 (f (grid [[5 1 -2] [0 -1 -3]])))))))
