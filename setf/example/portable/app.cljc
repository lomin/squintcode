(ns portable.app
  "A portable client of `setf`. The logic below is identical on every host.

   The only per-target cost of the whole library is this require block: Clojure
   refers macros with `:refer`, the JS family with `:refer-macros`. Squint
   activates the `:cljs` reader feature, so two branches cover all three hosts.

   Keep it to ONE `:require` entry per namespace -- two entries make Squint emit
   the same `import * as` twice, which is invalid ESM."
  (:require
   #?(:clj  [setf.api :as api :refer [setf! incf! decf! elt gethash get!]]
      :cljs [setf.api :as api :refer-macros [setf! incf! decf! elt gethash get!]])))

(defn run
  "Every place in the vocabulary, and every API form."
  []
  (let [a (api/make-arr 3)
        m (api/make-map)
        o (api/make-obj)]
    ;; elt -- indexed access. Every runtime argument is evaluated exactly once.
    (setf! (elt a 1) 99)
    (incf! (elt a 1))          ;; 100
    (decf! (elt a 1) 10)       ;; 90

    ;; gethash -- keyed access
    (setf! (gethash m "k") 7)

    ;; get! -- field access; `x` is a slot, never evaluated
    (setf! (get! o x) 42)

    ;; every runtime argument is evaluated exactly once, left to right:
    ;; `(dec 1)` and `(+ 1 1)` each run once, not twice
    (setf! (elt a (dec 1)) (+ 1 1))

    ;; A place name is also the reader, so the same names read and write:
    {:a  (elt    a 1)
     :a0 (elt    a 0)
     :m  (gethash m "k")
     :o  (get!   o x)}))

(defn -main [] (println "RESULT" (pr-str (run))))
