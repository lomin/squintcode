(ns s1.api)
;; S1: ONE .cljc namespace holding both a macro and a runtime value. No extension clash.
(def v :s1-runtime-from-cljc)
(defmacro where [] "S1-macro-from-cljc")
