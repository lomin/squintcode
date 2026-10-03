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

;; ---------------------------------------------------------------------------
;; Places as values.
;;
;; A place name is a macro, so it cannot be passed to `map` on its own -- a macro
;; is not a function. The 1-arity form is the way out: it fixes the trailing
;; argument and hands back an ordinary function, so a place becomes a first-class
;; thing you can map, filter and reduce over.
;;
;; This is the one thing the macro design costs, and this is how it is paid back.
;; On the JVM a curried `get!` is reflective; see the note in the JVM backend.
;; ---------------------------------------------------------------------------

(defn run-hof
  "Every place name used in a higher-order position."
  []
  (let [xs [(api/make-obj) (api/make-obj) (api/make-obj)]
        as [(api/make-arr 3) (api/make-arr 3)]
        ms [(api/make-map) (api/make-map)]]

    ;; Values are strings, not keywords: a Squint keyword IS a string, so a
    ;; keyword would print differently there and the hosts could not be compared.
    (dotimes [i 3] (setf! (get! (nth xs i) x) (* 10 (inc i))))
    (setf! (elt (first as) 1) 1)
    (setf! (elt (second as) 1) 2)
    (setf! (gethash (first ms) "k") "v")
    (setf! (gethash (second ms) "k") "w")

    ;; Each of these is a plain function value, built by the macro.
    (let [read-x    (get! x)
          read-at-1 (elt 1)
          read-k    (gethash "k")]

      ;; `map` over places. Identical source on every host.
      {:xs    (mapv read-x xs)
       :as    (mapv read-at-1 as)
       :ms    (mapv read-k ms)
       ;; a reader shared by two different receivers, which is the whole point
       :sum   (+ (read-at-1 (first as)) (read-at-1 (second as)))
       ;; and the writer stays a macro, wrapped to make a mapper
       :doubles (mapv (fn [a] (incf! (elt a 1) 10)) as)
       :after  (mapv read-at-1 as)})))

(defn -main-hof [] (println "HOF" (pr-str (run-hof))))
