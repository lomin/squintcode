(ns portable.hinted
  (:require
   #?(:clj  [setf.api :as api :refer [setf!]]
      :cljs [setf.api :as api :refer-macros [setf!]])))
(defn run []
  (let [^java.awt.Point p (api/make-obj)]
    (setf! (get! p x) 42)
    (api/make-obj)))
