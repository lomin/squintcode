(ns squintcode.macros
  (:refer-clojure :exclude [make-array]))

(defn- normalize-key
  "Convert keywords to strings at compile time for JS Map compatibility.
   - Keywords -> strings (e.g., :a -> \"a\")
   - Everything else -> unchanged
   This ensures ClojureScript and Squint both use string keys for consistent behavior."
  [k]
  (if (keyword? k)
    (name k)
    k))

(defmacro make-hash-table
  "Create a mutable map efficiently.
   - CLJ  : returns a java.util.HashMap with k/vs inserted via .put
   - CLJS : returns a JS Map with k/vs inserted via .set (keywords converted to strings at compile time)
   - Squint: returns a JS Map with k/vs inserted via .set (keywords converted to strings at compile time)"
  [& kvs]
  (when (odd? (count kvs))
    (throw (ex-info "new-map requires an even number of arguments (k v ...)"
                    {:args kvs})))
  (let [pairs (partition 2 kvs)]
    (if (:ns &env)                                ;; CLJS/Squint expansion
      `(doto (js/Map.)
         ~@(for [[k v] pairs]
             `(.set ~(normalize-key k) ~v)))
      `(doto (java.util.HashMap.)                 ;; CLJ expansion
         ~@(for [[k v] pairs]
             `(.put ~k ~v))))))

(comment (make-hash-table :a 1))

(defmacro gethash
  "Get value from hash table, similar to Common Lisp's gethash.

   Platform-specific behavior:
   - CLJ  : uses .get on java.util.HashMap (supports any key type)
   - CLJS/Squint : uses .get on JS Map (literal keywords converted to strings at compile time)

   Usage:
   (gethash ht key)          ; returns value or nil
   (gethash ht key default)  ; returns value or default if not found"
  ([ht key]
   (if (:ns &env)
     `(.get ~ht ~(normalize-key key))
     `(.get ^java.util.Map ~ht ~key)))
  ([ht key default]
   (if (:ns &env)
     (let [normalized-key (normalize-key key)]
       ;; JS Map returns undefined for missing keys, check with .has
       `(if (.has ~ht ~normalized-key)
          (.get ~ht ~normalized-key)
          ~default))
     ;; HashMap returns null for missing keys
     `(let [v# (.get ^java.util.Map ~ht ~key)]
        (if (nil? v#) ~default v#)))))

;; postwalk: depth-first post-order traversal
;; Visits children first, then applies f to the parent
(defn postwalk [f form]
  (let [walked (cond
                 (seq? form)
                 (map #(postwalk f %) form)

                 (vector? form)
                 (vec (map #(postwalk f %) form))

                 :else
                 form)]
    (f walked)))

(defmacro make-array
  "Create an array, optionally with initial contents.

   Usage:
   (make-array 5)                                    ; array of size 5
   (make-array 5 :initial-contents [1 2 3 4 5])     ; array with initial values
   (make-array 5 :initial-contents '(1 2 3 4 5))    ; also works with lists"
  [size & {:keys [initial-contents]}]
  (if initial-contents
    (if (:ns &env)
      ;; JavaScript: create array from initial contents
      `(cljs.core/into-array ~initial-contents)
      ;; Clojure: create array from initial contents
      `(into-array Object ~initial-contents))
    (if (:ns &env)
      ;; JavaScript: create empty array of given size
      `(js/Array. ~size)
      ;; Clojure: create empty array of given size
      `(clojure.core/make-array java.lang.Object ~size))))

;; forv macro that returns a vector
;; Optimized for range expressions: pre-allocates array and uses dotimes
(defmacro forv [[binding range-expr] body]
  (let [[_ start end] range-expr]  ; destructure (range start end), ignore 'range symbol
    `(let [start# ~start
           end# ~end
           size# (- end# start#)
           arr# (make-array size#)]
       (dotimes [idx# size#]
         (let [~binding (+ start# idx#)]
           (aset arr# idx# ~body)))
       arr#)))

;; aloop macro for optimized array iteration with O(1) access
;; Usage: (aloop arr elem [state-var1 init1 ...] body)
;; In body, use (recur ...) which implicitly increments the index
(defmacro aloop [arr-expr elem-var state-bindings & body]
  (let [idx (gensym "idx")
        replace-recur (fn [form]
                        (if (and (seq? form) (= 'recur (first form)))
                          `(recur (inc ~idx) ~@(rest form))
                          form))]
    `(let [arr# ~arr-expr
           len# (count arr#)]
       (loop [~idx 0
              ~@state-bindings]
         (let [~elem-var (when (< ~idx len#) (nth arr# ~idx))]
           ~@(map #(postwalk replace-recur %) body))))))

(defmacro push-end
  "Add an element to the end of a collection.

   Platform-specific behavior:
   - CLJ    : adds to java.util.List via .add (mutates in place)
   - Squint/CLJS : expands to (.push xs val) (mutates JavaScript array in place)

   NOTE: Both Squint and ClojureScript use .push for JavaScript arrays.
   This macro uses mutation for performance on JS targets.

   WARNING: Does not guarantee immutability. You may get a modified version
   of the xs you passed, depending on the platform."
  [xs val]
  (if (:ns &env)
    ;; ClojureScript or Squint - both use .push for JavaScript arrays
    `(.push ~xs ~val)
    ;; Clojure - use .add for Java Lists
    `(.add ^java.util.List ~xs ~val)))

(defmacro aref
  "Access element at index in an array/list, similar to Common Lisp's aref.

   Platform-specific behavior:
   - CLJ         : uses (aget arr idx) for Java arrays
   - Squint/CLJS : expands to (aget arr idx) for JavaScript array access

   Returns the element at the specified index.

   Usage: (aref arr 0) ; get first element"
  [arr idx]
  (if (:ns &env)
    ;; ClojureScript or Squint - use aget for JavaScript arrays
    `(aget ~arr ~idx)
    ;; Clojure - use aget for Java arrays
    `(aget ~arr ~idx)))

;; Test framework macros for Squint
(defmacro deftest [name & body]
  `(do
     (~'js* "// ~{}\n" ~name)
     (println (str "\n" ~(str name) ":"))
     (try
       ~@body
       (catch ~'js/Error e#
         (println (str "  ✗ Error: " (.-message e#)))
         (~'js/process.exit 1)))))

(defmacro testing [description & body]
  `(do
     (println (str "  " ~description))
     ~@body))

(defmacro is
  ([form]
   `(is ~form nil))
  ([form msg]
   (let [assert-equal? (and (seq? form) (= '= (first form)))
         [expected actual] (when assert-equal? (rest form))]
     (if assert-equal?
       `(try
          (~'assert.equal ~actual ~expected)
          (println (str "    ✓ " ~(or msg (str form))))
          (catch ~'js/Error e#
            (println (str "    ✗ " ~(or msg (str form))))
            (println (str "      " (.-message e#)))
            (~'js/process.exit 1)))
       `(if ~form
          (println (str "    ✓ " ~(or msg (str form))))
          (do
            (println (str "    ✗ " ~(or msg (str form))))
            (~'js/process.exit 1)))))))

(defmacro run-tests [& _args]
  `(println "\nTests completed successfully! ✓"))

;; setf: Common Lisp-style generalized assignment
;; Supports setting array elements: (setf (aref arr idx) val)
;; Supports setting hash table entries: (setf (gethash ht key) val)
(defmacro setf [place value]
  (if (seq? place)
    (let [sym (first place)
          sym-name (if (symbol? sym) (name sym) nil)]
      (cond
        ;; Setting array element: (setf (aref arr idx) val) => (aset arr idx val)
        (or (= 'aref sym) (= "aref" sym-name))
        (let [[_ arr idx] place]
          `(aset ~arr ~idx ~value))

        ;; Setting hash table entry: (setf (gethash ht key) val) => (.put ht key val) or (.set ht key val)
        (or (= 'gethash sym) (= "gethash" sym-name))
        (let [[_ ht key] place]
          (if (:ns &env)
            ;; JS Map .set returns the map, so we need to return the value
            ;; Normalize literal keywords to strings at compile time
            `(do (.set ~ht ~(normalize-key key) ~value) ~value)
            ;; HashMap .put returns old value, so we need to return the new value
            `(do (.put ^java.util.Map ~ht ~key ~value) ~value)))

        ;; Unknown place form
        :else
        `(set! ~place ~value)))
    ;; Regular variable assignment: (setf var val) => (set! var val)
    `(set! ~place ~value)))