(ns squintcode.lc-977-squares-of-a-sorted-array-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [squintcode.lc-977-squares-of-a-sorted-array :as loop-version]
            [squintcode.lc-977-squares-of-a-sorted-array-seq :as seq-version]))

(defn arr [v] (ucl/make-array (count v) :element-type 'fixnum :initial-contents v))

(deftest sorted-squares-test
  (doseq [[label f] [["two ends" loop-version/sortedSquares] ["map + sort" seq-version/sortedSquares]]]
    (testing (str label ": LeetCode examples")
      (is (= [0 1 9 16 100] (vec (f (arr [-4 -1 0 3 10])))))
      (is (= [4 9 9 49 121] (vec (f (arr [-7 -3 2 3 11]))))))
    (testing (str label ": edges")
      (is (= [25] (vec (f (arr [-5])))))
      (is (= [1 4 9] (vec (f (arr [-3 -2 -1])))))
      (is (= [1 4 9] (vec (f (arr [1 2 3]))))))))
