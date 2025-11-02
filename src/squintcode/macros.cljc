(ns squintcode.macros)

;; forv macro that returns a vector (expands to vec (for ...))
(defmacro forv [& args]
  `(vec (for ~@args)))
