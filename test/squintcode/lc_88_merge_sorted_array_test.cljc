(ns squintcode.lc-88-merge-sorted-array-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [squintcode.lc-88-merge-sorted-array :as loop-version]
            [squintcode.lc-88-merge-sorted-array-seq :as seq-version]))

(defn arr [v] (ucl/make-array (count v) :element-type 'fixnum :initial-contents v))

(defn merged [f a m b n]
  (let [a (arr a)] (f a m (arr b) n) (vec a)))

(deftest merge-test
  (doseq [[label f] [["loop/recur" loop-version/merge] ["merge" seq-version/merge]]]
    (testing (str label ": LeetCode examples")
      (is (= [1 2 2 3 5 6] (merged f [1 2 3 0 0 0] 3 [2 5 6] 3)))
      (is (= [1] (merged f [1] 1 [] 0)))
      (is (= [1] (merged f [0] 0 [1] 1))))
    (testing (str label ": edges")
      (is (= [-1 0 0 3 3 3] (merged f [0 3 3 0 0 0] 3 [-1 0 3] 3)))
      (is (= [1 2 3 4 5 6] (merged f [4 5 6 0 0 0] 3 [1 2 3] 3))))))
