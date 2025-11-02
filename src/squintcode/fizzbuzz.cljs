(ns squintcode.fizzbuzz
  (:require-macros [squintcode.macros :refer [forv]]))

(defn fizzBuzz [n]
  (forv [i (range 1 (inc n))]
    (condp (fn [a b] (zero? (mod b a))) i
      15 "FizzBuzz"
      3  "Fizz"
      5  "Buzz"
      (str i))))

(comment
  (fizzBuzz 4)
  )