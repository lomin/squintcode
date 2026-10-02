(ns nsprobe.api)

(def marker :from-clj)

(defmacro where-am-i [] (str "macro-ns-ext=.clj"))
