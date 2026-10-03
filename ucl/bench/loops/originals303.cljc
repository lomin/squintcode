(ns squintcode.originals303
  #?(:cljs (:require-macros [squintcode.macros :as cl])))
;; verbatim copy of lc_303's build-prefix-sum, made public so it can be called directly
(defn build-prefix-sum [nums]
  (cl/aloop nums [i 1
                  sum 0
                  ps (cl/with (cl/make-array (inc (cl/length nums)) :initial-element 0))]
            (if it
              (recur (inc i) (cl/setf (cl/aref ps i) (+ sum it)))
              ps)))
