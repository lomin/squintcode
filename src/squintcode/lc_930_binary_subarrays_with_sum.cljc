(ns squintcode.lc-930-binary-subarrays-with-sum
  #?(:cljs (:require-macros [squintcode.macros  :as cl]))
  #?(:clj  (:require [squintcode.macros :as cl])))


(defn incf-array [arr k]
  (cl/setf (cl/aref arr k)
           (inc (cl/aref arr k)))
  arr)

(defn init-prefix-sum-frequencies [length]
  (let [arr (cl/make-array (inc length)
                           :element-type 'integer
                           :initial-element 0)]
    (cl/setf (cl/aref arr 0) 1)
    arr))

(defn count-matching-subarrays [prefix-sum-frequencies running-sum' goal]
  (let [want (- running-sum' goal)]
    (if (>= want 0)
      (cl/aref prefix-sum-frequencies want)
      0)))

"/**
 * @param {number[]} nums
 * @param {number} goal
 * @return {number}
 */"
(defn numSubarraysWithSum [nums goal]
  (cl/aloop nums
            [running-sum 0
             result 0
             prefix-sum-frequencies (init-prefix-sum-frequencies (cl/length ::aloop))]
            (if it
              (let [running-sum' (+ running-sum it)]
                (recur running-sum'
                       (+ result (count-matching-subarrays prefix-sum-frequencies running-sum' goal))
                       (incf-array prefix-sum-frequencies running-sum')))
              result)))


(comment
; expecting: 4
  (numSubarraysWithSum (cl/make-array 5 :initial-contents [1,0,1,0,1]) 0)
; expecting: 15 
  (numSubarraysWithSum (cl/make-array 5 :initial-contents [0,0,0,0,0]) 0))