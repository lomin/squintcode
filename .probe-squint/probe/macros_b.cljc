(ns probe.macros-b)

(defmacro mb [x]
  `(* ~x 2))
