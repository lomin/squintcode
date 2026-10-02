(ns portable.app
  "PROTOTYPE (probe 4) — a portable app that uses EVERY place in the vocabulary,
   so an incomplete backend cannot hide."
  (:require [setf.api :as api]
            #?@(:squint []
                :clj   [[setf.api :refer [setf! incf!]]]
                :cljs  [[setf.api :refer-macros [setf! incf!]]]))
  #?(:squint (:require-macros [setf.api :refer [setf! incf!]]])))

(defn run []
  (let [a (api/make-arr 3)
        m (api/make-map)
        o (api/make-obj)]
    (setf! (elt a 1) 99)
    (incf! (elt a 1))
    (setf! (gethash m "k") 7)
    (setf! (get! o x) 42)
    (api/dump a m o)))