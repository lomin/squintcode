(ns squintcode.fizzbuzz-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [squintcode.fizzbuzz :refer [fizzBuzz fizzBuzz2]]
            [squintcode.fizzbuzz-loop :as loop-version]))

(deftest fizzbuzz-basic-test
  (doseq [f [fizzBuzz fizzBuzz2 loop-version/fizzBuzz]]
    (testing "FizzBuzz with n=15"
      (let [result (f 15)]
        (is (= 15 (ucl/length result)) "Should return 15 elements")
        (is (= "1" (ucl/elt result 0)) "1 should be '1'")
        (is (= "2" (ucl/elt result 1)) "2 should be '2'")
        (is (= "Fizz" (ucl/elt result 2)) "3 should be 'Fizz'")
        (is (= "4" (ucl/elt result 3)) "4 should be '4'")
        (is (= "Buzz" (ucl/elt result 4)) "5 should be 'Buzz'")
        (is (= "Fizz" (ucl/elt result 5)) "6 should be 'Fizz'")
        (is (= "FizzBuzz" (ucl/elt result 14)) "15 should be 'FizzBuzz'")))))

(deftest fizzbuzz-edge-cases
  (doseq [f [fizzBuzz fizzBuzz2 loop-version/fizzBuzz]]
    (testing "FizzBuzz with n=1"
      (let [result (f 1)]
        (is (= 1 (ucl/length result)))
        (is (= "1" (ucl/elt result 0)))))
    (testing "FizzBuzz with n=5"
      (let [result (f 5)]
        (is (= 5 (ucl/length result)))
        (is (= "Buzz" (ucl/elt result 4)))))))
