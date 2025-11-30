(ns squintcode.fizzbuzz
  #?(:cljs (:require-macros [squintcode.macros :as cl]))
  (:require #?(:clj [squintcode.macros :as cl])
            [squintcode.utils :refer [assoc-arr!]]))

(defn fizz-buzz-pred [a b]
  (zero? (mod b a)))

(defn fizzBuzz [n]
  (cl/forv [i (range 1 (inc n))]
           (condp fizz-buzz-pred i
             15 "FizzBuzz"
             3  "Fizz"
             5  "Buzz"
             (str i))))

(defn fizzBuzz2 [n]
  (cl/aloop
   (range 1 (inc n))
   [result (cl/make-array (cl/length self))]
   (if it
     (recur (assoc-arr! result (dec it) (condp fizz-buzz-pred it
                                          15 "FizzBuzz"
                                          3  "Fizz"
                                          5  "Buzz"
                                          (str it))))
     result)))

(comment
  (fizzBuzz 4)
  (fizzBuzz2 4))