(ns probe.use-two-macro-ns
  (:require-macros [probe.macros-a :refer [ma]]
                   [probe.macros-b :refer [mb]]))

(println "multi-ns require-macros =>" (ma 1) (mb 3))
