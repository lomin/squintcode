(ns squintcode.fizzbuzz
  #?(:cljs (:require-macros [squintcode.macros :as cl]))
  #?(:clj  (:require [squintcode.macros :as cl])))

(defn fizz-buzz-pred [a b]
  (zero? (mod b a)))

(defn fizzBuzz [n]
  (cl/forv [i (range 1 (inc n))]
           (condp fizz-buzz-pred i
             15 "FizzBuzz"
             3  "Fizz"
             5  "Buzz"
             (str i))))

(comment
  (fizzBuzz 4))