(ns squintcode.fizzbuzz-test
  (:require [cljs.test :refer-macros [deftest is testing] :refer [run-tests]]
            [squintcode.fizzbuzz :refer [fizzBuzz]]))

(deftest fizzbuzz-basic-test
  (testing "FizzBuzz with n=15"
    (let [result (fizzBuzz 15)]
      (is (= 15 (count result)) "Should return 15 elements")
      (is (= "1" (nth result 0)) "1 should be '1'")
      (is (= "2" (nth result 1)) "2 should be '2'")
      (is (= "Fizz" (nth result 2)) "3 should be 'Fizz'")
      (is (= "4" (nth result 3)) "4 should be '4'")
      (is (= "Buzz" (nth result 4)) "5 should be 'Buzz'")
      (is (= "Fizz" (nth result 5)) "6 should be 'Fizz'")
      (is (= "FizzBuzz" (nth result 14)) "15 should be 'FizzBuzz'"))))

(deftest fizzbuzz-edge-cases
  (testing "FizzBuzz with n=1"
    (let [result (fizzBuzz 1)]
      (is (= 1 (count result)))
      (is (= "1" (first result)))))

  (testing "FizzBuzz with n=5"
    (let [result (fizzBuzz 5)]
      (is (= 5 (count result)))
      (is (= "Buzz" (last result))))))

(comment  (run-tests 'squintcode.fizzbuzz-test))