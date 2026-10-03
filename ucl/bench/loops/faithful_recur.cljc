(ns squintcode.faithful-recur
  "loop/recur rewrites of every aloop/forv in src/. Same bodies, same helpers --
   only the loop construct differs."
  #?(:cljs (:require-macros [squintcode.macros :as cl]))
  (:require [squintcode.utils :refer [assoc-arr!]]
            [squintcode.fizzbuzz :refer [fizz-buzz-pred]]
            [squintcode.maxprofit :refer [infinity]]
            [squintcode.lc-560-subarray-sum-equals-k :as lc560]
            [squintcode.lc-930-binary-subarrays-with-sum :as lc930]))

;; 412 fizzBuzz -- forv
(defn fizzBuzz [n]
  (let [arr (cl/make-array n)]
    (loop [idx 0]
      (if (< idx n)
        (let [i (+ 1 idx)]
          (cl/setf (cl/aref arr idx) (condp fizz-buzz-pred i 15 "FizzBuzz" 3 "Fizz" 5 "Buzz" (str i)))
          (recur (inc idx)))
        arr))))

;; 412 fizzBuzz2 -- aloop over (range ...)
(defn fizzBuzz2 [n]
  (let [end (inc n)]
    (loop [i 1 result (cl/make-array n)]
      (if (< i end)
        (recur (inc i) (assoc-arr! result (dec i) (condp fizz-buzz-pred i 15 "FizzBuzz" 3 "Fizz" 5 "Buzz" (str i))))
        result))))

;; 303 build-prefix-sum -- aloop with (with ...)
(defn build-prefix-sum [nums]
  (let [n (cl/length nums)
        ps (cl/make-array (inc n) :initial-element 0)]
    (loop [j 0 i 1 sum 0]
      (if (< j n)
        (recur (inc j) (inc i) (cl/setf (cl/aref ps i) (+ sum (cl/aref nums j))))
        ps))))

;; 121 maxProfit
(defn maxProfit [prices]
  (let [n (cl/length prices)]
    (loop [j 0 min-price (infinity) max-profit 0]
      (if (< j n)
        (let [it (cl/aref prices j)]
          (recur (inc j) (min min-price it) (max max-profit (- it min-price))))
        max-profit))))

;; 560 subarraySum
(defn subarraySum [num-seq k]
  (let [n (cl/length num-seq)]
    (loop [j 0 running-sum 0 result 0 prefix-sum-frequencies (cl/dict 0 1)]
      (if (< j n)
        (let [running-sum' (+ running-sum (cl/aref num-seq j))]
          (recur (inc j) running-sum'
                 (+ result (lc560/count-matching-subarrays prefix-sum-frequencies running-sum' k))
                 (lc560/incf prefix-sum-frequencies running-sum' 1)))
        result))))

;; 930 numSubarraysWithSum
(defn numSubarraysWithSum [nums goal]
  (let [n (cl/length nums)]
    (loop [j 0 running-sum 0 result 0
           prefix-sum-frequencies (lc930/init-prefix-sum-frequencies n)]
      (if (< j n)
        (let [running-sum' (+ running-sum (cl/aref nums j))]
          (recur (inc j) running-sum'
                 (+ result (lc930/count-matching-subarrays prefix-sum-frequencies running-sum' goal))
                 (lc930/incf-array prefix-sum-frequencies running-sum')))
        result))))
