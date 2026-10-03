(ns squintcode.fizzbuzz-loop
  (:require [ucl.api :as ucl]
            [squintcode.fizzbuzz :refer [fizz-buzz-word]]))

;; LeetCode 412, written with ucl/loop (README D62); compare squintcode.fizzbuzz.

(ucl/defun fizzBuzz (n)
  (declare (type fixnum n))
  ;; element type string: Dart checks that the result is a List<String>
  (let [answer (ucl/make-array n :element-type 'string :initial-element "")]
    (ucl/loop for i below n
              do (ucl/setf (ucl/elt answer i) (fizz-buzz-word (inc i))))
    answer))
