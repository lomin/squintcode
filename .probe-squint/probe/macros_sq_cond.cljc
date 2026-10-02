(ns probe.macros-sq-cond)

(defmacro aref2 [arr idx]
  `#?(:squint (aget ~arr ~idx)
      :clj (nth ~arr ~idx)
      :cljs (nth ~arr ~idx)))
