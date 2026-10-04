(ns squintcode.lc-26-remove-duplicates-from-sorted-array-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [squintcode.lc-26-remove-duplicates-from-sorted-array :as loop-version]
            [squintcode.lc-26-remove-duplicates-from-sorted-array-seq :as seq-version]))

(defn arr [v] (ucl/make-array (count v) :element-type 'fixnum :initial-contents v))

(defn kept [f v]
  (let [a (arr v) k (f a)] (subvec (vec a) 0 k)))

(deftest remove-duplicates-test
  (doseq [[label f] [["loop/recur" loop-version/removeDuplicates] ["remove-duplicates" seq-version/removeDuplicates]]]
    (testing (str label ": LeetCode examples")
      (is (= [1 2] (kept f [1 1 2])))
      (is (= [0 1 2 3 4] (kept f [0 0 1 1 1 2 2 3 3 4]))))
    (testing (str label ": edges")
      (is (= [7] (kept f [7])))
      (is (= [-3 -1 5] (kept f [-3 -1 5])))
      (is (= [2] (kept f [2 2 2 2]))))))
