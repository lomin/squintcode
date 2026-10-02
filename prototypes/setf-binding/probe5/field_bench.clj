(ns field-bench
  "PROTOTYPE (probe 5) — what does a JVM `:field` write actually cost?

   Same workload every time: write the public int field `x` of a
   java.awt.Point. Tactics, cheapest-looking first:

     read      (.x pt)                 the direct field READ the JVM backend
                                       already emits. The floor -- but only a
                                       read: Clojure has no `set!` on a public
                                       field, so a direct field STORE is not
                                       expressible in Clojure at all.
     direct    FieldTactics.direct     a real field store, via generated Java.
                                       The floor for a WRITE.
     setH      (set! (.x ^Point p) v)  Clojure's OWN direct field store. It
                                       compiles to PUTFIELD -- no Java needed.
     setU      (set! (.x p) v)         the same, without a type hint: Clojure
                                       must resolve the field reflectively.
     mhExact   FieldTactics.mhExact    a cached MethodHandle invoked exactly.
                                       What a type-hint-specialized backend
                                       would emit for a known type.
     mhBound   FieldTactics.mhBound    same handle, bound to the receiver first.
     reflect   Reflector/setInstanceField   what the backend emits today; it
                                       re-does getDeclaredField on every call.

   Bounded: fixed iteration counts, no JMH, no harness. Single JVM, single
   machine, indicative only. Not a benchmark."
  (:import (java.lang.invoke MethodHandles)
           (java.util.concurrent ConcurrentHashMap)))

(def ^:const N (int 3000000))
(def ^:const WARMUP (int 300000))

;; ---- tactics ---------------------------------------------------------------
(defn read-direct [^java.awt.Point p _] (.x p))

;; THE POINT: Clojure can express a direct field STORE after all --
;; (set! (.x p) v) with a type hint on p. This compiles to PUTFIELD.
(defn write-set-hinted [^java.awt.Point p v] (set! (.x p) v))

;; Without the hint Clojure has to resolve the field reflectively.
(defn write-set-unhinted [p v] (set! (.x p) v))

(def ^:private mh-cache (ConcurrentHashMap.))

(defn- setter-for [^Class c nm]
  (or (.get mh-cache [c nm])
      (let [mh (.unreflectSetter (MethodHandles/lookup) (.getDeclaredField c nm))
            _ (.putIfAbsent mh-cache [c nm] mh)]
        mh)))

(defn write-mh-cached! [^java.awt.Point p v]
  ;; Clojure cannot call the signature-polymorphic invokeExact, so this uses the
  ;; varargs form. It is therefore an UPPER BOUND on what FieldTactics/mhExact
  ;; does -- it pays for the argument array as well.
  (.invokeWithArguments ^java.lang.invoke.MethodHandle (setter-for java.awt.Point "x")
                         (object-array [p (int v)])))

(defn write-reflect! [p v]
  (clojure.lang.Reflector/setInstanceField p "x" v))

;; ---- harness ---------------------------------------------------------------
(defn bench [label f ^java.awt.Point p]
  (dotimes [_ WARMUP] (f p (int 1)))
  (let [start (System/nanoTime)
        _     (dotimes [i N] (f p (int (bit-and i 0xFFFF))))
        end   (System/nanoTime)]
    (let [per-op (/ (double (- end start)) N)]
      (println (format "  %-11s %7.2f ns/op" label per-op))
      per-op)))

(defn -main [& _]
  (println (str "field-bench: " N " ops per tactic, after " WARMUP " warmup"))
  (println "  target: java.awt.Point.x (public int)")
  (println)
  (let [p (java.awt.Point. 0 0)]
    (FieldTactics/direct p 1)
    (FieldTactics/mhExact p 1)
    (write-set-hinted p (int 1))
    (write-set-unhinted p (int 1))
    (write-mh-cached! p (int 1))
    (write-reflect! p (int 1))
    (read-direct p 1)
    (assert (= 1 (.x p)) "tactics disagree")
    (println "  sanity ok: all tactics read back the same value")
    (println)
    (let [r (bench "read"         read-direct          p)
          h (bench "setH"         write-set-hinted     p)
          u (bench "setU"         write-set-unhinted   p)
          d (bench "direct"       FieldTactics/direct  p)
          e (bench "mhExact"      FieldTactics/mhExact p)
          c (bench "mhCached(clj)" write-mh-cached!     p)
          x (bench "reflect"      write-reflect!       p)]
      (println)
      (println "  ratios against Reflector (today's emission):")
      (println (format "    setH (set!)   %6.2fx" (/ h x)))
      (println (format "    setU (no hint)%6.2fx" (/ u x)))
      (println (format "    direct        %6.2fx" (/ d x)))
      (println (format "    mhExact       %6.2fx" (/ e x)))
      (println (format "    mhCached(clj) %6.2fx" (/ c x)))
      (println)
      (println "  ratios against a direct field store (the floor):")
      (println (format "    read          %6.2fx" (/ r d)))
      (println (format "    setH (set!)   %6.2fx" (/ h d)))
      (println (format "    mhExact       %6.2fx" (/ e d)))
      (println (format "    reflect       %6.2fx" (/ x d))))))
