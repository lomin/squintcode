(ns ucl.numbers-test
  "min, max, the numeric constants, and defun declarations."
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]))

(ucl/defun clamp (x lo hi)
  (declare (type fixnum x lo hi))
  (ucl/max lo (ucl/min x hi)))

(deftest extrema-test
  (testing "in call position"
    (is (= 1 (ucl/min 3 1 2)))
    (is (= 3 (ucl/max 3 1 2)))
    (is (= 4 (ucl/max 4)))
    (let [a 5 b 9]
      (is (= 5 (ucl/min a b)))
      (is (= 14 (ucl/max (+ a b) (- b a))))))
  (testing "as values"
    (is (= 8 (reduce ucl/max 0 [3 8 2])))
    (is (= [1 2] (mapv ucl/min [1 5] [4 2]))))
  (testing "a defun with type declarations"
    (is (= 5 (clamp 9 0 5)))
    (is (= 0 (clamp -3 0 5)))))

(deftest constants-test
  (is (= 2147483647 ucl/most-positive-fixnum))
  (is (= -2147483648 ucl/most-negative-fixnum))
  (is (< 1e308 ucl/double-float-positive-infinity))
  (is (> -1e308 ucl/double-float-negative-infinity))
  (is (= 3 (ucl/min ucl/double-float-positive-infinity 3))))

(deftest princ-to-string-test
  (testing "an integer's decimal digits, a string itself"
    (is (= "15" (ucl/princ-to-string 15)))
    (is (= "-7" (ucl/princ-to-string (- 3 10))))
    (is (= "Fizz" (ucl/princ-to-string "Fizz")))))
