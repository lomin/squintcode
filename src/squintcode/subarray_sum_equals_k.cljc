(ns squintcode.subarray-sum-equals-k
  #?(:squint (:require-macros [squintcode.macros :refer [aloop dict gethash setf]]))
  #?(:cljs (:require-macros [squintcode.macros :refer [aloop dict gethash setf]]))
  #?(:clj  (:require [squintcode.macros :refer [aloop dict gethash setf]])))

(defn incf [m k default]
  (if-let [v (gethash m k)]
    (do (setf (gethash m k) (inc v)) m)
    (do (setf (gethash m k) default) m)))

(defn count-matching-subarrays [sum-freq running-sum' k]
  (gethash sum-freq (- running-sum' k) 0))

(defn subarraySum [num-seq k]
  (aloop num-seq num
         [running-sum 0
          result 0
          prefix-sum-frequencies (dict 0 1)]
         (if num
           (let [running-sum' (+ running-sum num) ]
             (recur running-sum'
                    (+ result (count-matching-subarrays prefix-sum-frequencies running-sum' k))
                    (incf prefix-sum-frequencies running-sum' 1)))
           result)))

(comment
  (subarraySum (into-array  [1,-1,1]) 2)
  (subarraySum (into-array [1,-1,1,1]) 0)
  )


(comment
  "
    def subarraySum(nums, k):
            running_sum = 0
            hash_table = collections.defaultdict(lambda:0)
            total = 0
            hash_table[0] = 1
            for x in nums:
                running_sum += x
                sum = running_sum - k
                total += hash_table[sum]
                hash_table[running_sum] += 1
            return total
   "
   )
