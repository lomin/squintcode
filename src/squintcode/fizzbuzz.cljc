(ns squintcode.fizzbuzz
  (:require [ucl.api :as ucl]
            [squintcode.utils :refer [assoc-arr!]]))

(defn fizz-buzz-pred [a b]
  (zero? (mod b a)))

(defn fizz-buzz-word [x]
  (condp fizz-buzz-pred x
    15 "FizzBuzz"
    3  "Fizz"
    5  "Buzz"
    (ucl/princ-to-string x)))

(ucl/defun fizzBuzz (n)
  (declare (type fixnum n))
  ;; element type string: Dart checks that the result is a List<String>
  (let [answer (ucl/make-array n :element-type 'string :initial-element "")]
    (loop [i 0]
      (if (< i n)
        (do (ucl/setf (ucl/elt answer i) (fizz-buzz-word (inc i)))
            (recur (inc i)))
        answer))))

(ucl/defun fizzBuzz2 (n)
  (declare (type fixnum n))
  (loop [x 1
         result (ucl/make-array n :element-type 'string :initial-element "")]
    (if (<= x n)
      (recur (inc x) (assoc-arr! result (dec x) (fizz-buzz-word x)))
      result)))

(comment
  (fizzBuzz 4)
  (fizzBuzz2 4))
