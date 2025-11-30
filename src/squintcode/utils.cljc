(ns squintcode.utils
  #?(:cljs (:require-macros [squintcode.macros :as cl]))
  #?(:clj  (:require [squintcode.macros :as cl])))

(defn assoc-arr! [arr k v]
  (cl/setf (cl/aref arr k) v)
  arr)
