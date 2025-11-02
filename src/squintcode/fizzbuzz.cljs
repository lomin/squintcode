(ns squintcode.fizzbuzz
  (:require-macros [squintcode.macros :refer [forv]]))

(defn fizz-buzz-pred [a b]
  (zero? (mod b a)))

(defn fizzBuzz [n]
  (forv [i (range 1 (inc n))]
    (condp fizz-buzz-pred i
      15 "FizzBuzz"
      3  "Fizz"
      5  "Buzz"
      (str i))))

(comment
  (fizzBuzz 4)
  )