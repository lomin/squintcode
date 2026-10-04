(ns squintcode.lc-344-reverse-string-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [squintcode.lc-344-reverse-string :as loop-version]
            [squintcode.lc-344-reverse-string-seq :as seq-version]))

(defn chars-of [v] (ucl/make-array (count v) :element-type 'string :initial-contents v))   ; char[]

(defn reversed [f v]
  (let [s (chars-of v)] (f s) (vec s)))

(deftest reverse-string-test
  (doseq [[label f] [["loop/recur" loop-version/reverseString] ["nreverse" seq-version/reverseString]]]
    (testing (str label ": LeetCode examples")
      (is (= ["o" "l" "l" "e" "h"] (reversed f ["h" "e" "l" "l" "o"])))
      (is (= ["h" "a" "n" "n" "a" "H"] (reversed f ["H" "a" "n" "n" "a" "h"]))))
    (testing (str label ": edges")
      (is (= ["x"] (reversed f ["x"])))
      (is (= ["b" "a"] (reversed f ["a" "b"]))))))
