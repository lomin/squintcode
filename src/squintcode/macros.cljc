(ns squintcode.macros
  (:refer-clojure :exclude [make-array]))

;; Extend IIndexed protocol for Uint32Array in ClojureScript
;; This allows (nth typed-array index) to work
#?(:squint (comment "nothing to do")
   :cljs
   (do
     (extend-type js/Uint32Array
       IIndexed
       (-nth
         ([coll n]
          (aget coll n))
         ([coll n not-found]
          (if (and (>= n 0) (< n (.-length coll)))
            (aget coll n)
            not-found)))
       ICounted
       (-count [coll]
         (.-length coll)))))

(defn- normalize-key
  "Convert keywords to strings at compile time for JS Map compatibility.
   - Keywords -> strings (e.g., :a -> \"a\")
   - Everything else -> unchanged
   This ensures ClojureScript and Squint both use string keys for consistent behavior."
  [k]
  (if (keyword? k)
    (name k)
    k))

(defmacro dict
  "Create a mutable map efficiently.
   - CLJ  : returns a java.util.HashMap with k/vs inserted via .put
   - CLJS: returns a JS Map with k/vs inserted via .set (keywords converted to strings at compile time)"
  [& kvs]
  (when (odd? (count kvs))
    (throw (ex-info "dict requires an even number of arguments (k v ...)"
                    {:args kvs})))
  (let [pairs (partition 2 kvs)]
    (if (:ns &env)                                ;; CLJS/Squint expansion
      `(doto (js/Map.)
         ~@(for [[k v] pairs]
             `(.set ~(normalize-key k) ~v)))
      `(doto (java.util.HashMap.)                 ;; CLJ expansion
         ~@(for [[k v] pairs]
             `(.put ~k ~v))))))

(comment (dict :a 1))

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

(defn- postwalk
  "Depth-first post-order traversal.
   Visits children first, then applies f to the parent.
   Recursively walks sequences and vectors, applying f to each node after walking its children."
  [f form]
  (let [walked (cond
                 (seq? form)
                 (map #(postwalk f %) form)

                 (vector? form)
                 (vec (map #(postwalk f %) form))

                 :else
                 form)]
    (f walked)))

(defmacro make-array
  "Create an array, optionally with initial contents or element type.

   Usage:
   (make-array 5)                                    ; array of size 5
   (make-array 5 :initial-contents [1 2 3 4 5])     ; array with initial values
   (make-array 5 :initial-contents '(1 2 3 4 5))    ; also works with lists
   (make-array 5 :initial-element 0)                ; array filled with 0s
   (make-array 5 :element-type 'integer)            ; typed integer array (Uint32Array in JS)
   (make-array 5 :element-type 't)                  ; generic array (default)
   (make-array 5 :element-type 'integer :initial-element 42) ; typed array with initial value

   Options:
   - :initial-contents - sequence of initial values (mutually exclusive with :initial-element)
   - :initial-element  - single value to fill array (mutually exclusive with :initial-contents)
   - :element-type     - 'integer for typed arrays (JS: Uint32Array, CLJ: ArrayList),
                         't or nil for generic arrays (default)"
  [size & {:keys [initial-contents initial-element element-type]}]
  ;; Validate mutually exclusive options
  (when (and initial-contents initial-element)
    (throw (ex-info ":initial-contents and :initial-element are mutually exclusive"
                    {:initial-contents initial-contents
                     :initial-element initial-element})))

  (let [is-integer-type? (= element-type ''integer)
        is-js? (:ns &env)]

    (cond
      ;; Case 1: initial-contents provided
      initial-contents
      (if is-js?
        (if is-integer-type?
          ;; JS typed array from contents
          `(js/Uint32Array.from ~initial-contents)
          ;; JS regular array from contents
          `(into-array ~initial-contents))
        ;; Clojure: always use ArrayList
        `(java.util.ArrayList. ~initial-contents))

      ;; Case 2: initial-element provided
      initial-element
      (if is-js?
        (if is-integer-type?
          ;; JS typed array filled with initial-element
          `(let [arr# (js/Uint32Array. ~size)]
             (.fill arr# ~initial-element)
             arr#)
          ;; JS regular array filled with initial-element
          `(let [arr# (js/Array. ~size)]
             (.fill arr# ~initial-element)
             arr#))
        ;; Clojure: ArrayList filled with initial-element
        ;; Use repeatedly to create independent instances for mutable values
        `(java.util.ArrayList. (repeatedly ~size (fn [] ~initial-element))))

      ;; Case 3: no initial data, just size and optional element-type
      :else
      (if is-js?
        (if is-integer-type?
          ;; JS typed array (initialized to 0s by default)
          `(js/Uint32Array. ~size)
          ;; JS regular array (empty slots)
          `(js/Array. ~size))
        ;; Clojure: ArrayList filled with nils
        `(java.util.ArrayList. (repeat ~size nil))))))

#?(:clj
   (defmacro aref
     "Access element at index in an array, similar to Common Lisp's aref."
     ([] `nth)
     ([arr idx]
      `(nth ~arr ~idx)))
   :default
   (defmacro aref
     "Access element at index in an array, similar to Common Lisp's aref."
     ([] `nth)
     ([arr idx]
      `(aget ~arr ~idx))))

#?(:clj
   (defmacro length
     ([] `(fn [arr#] (count arr#)))
     ([arr] `(count ~arr)))
   :default
   (defmacro length
     ([] `count)
     ([arr] `(.-length ~arr))))

(comment
  (aref (make-array 5 :initial-contents [1 2 3 4 5]) 2)
  (setf (aref (make-array 5 :initial-contents [1 2 3 4 5]) 2) "hello")
  (push-end (make-array 5 :initial-contents [1 2 3 4 5]) 2))

(defmacro aloop
  "Optimized array iteration with O(1) access and implicit index management.

   Usage:
   (aloop arr elem [state-var1 init1 ...] body)

   The macro provides:
   - elem-var: bound to current array element (or nil when past end)
   - state-bindings: additional loop state variables
   - (recur ...): implicitly increments the index

   Example:
   (aloop (make-array 3 :initial-contents [1 2 3]) x [sum 0]
     (if x
       (recur (+ sum x))
       sum))  ; returns 6"
  [arr-expr elem-var state-bindings & body]
  (let [idx (gensym "idx")
        arr (gensym "arr")
        len (gensym "len")
        replace-recur (fn [form]
                        (if (and (seq? form) (= 'recur (first form)))
                          `(recur (inc ~idx) ~@(rest form))
                          form))]
    `(let [~arr ~arr-expr
           ~len (count ~arr)]
       (loop [~idx 0
              ~@state-bindings]
         (let [~elem-var (when (< ~idx ~len) (aref ~arr ~idx))]
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
    `(let [xs# ~xs] (.push xs# ~val) xs#)
    ;; Clojure - use .add for Java Lists
    `(let [xs# ~xs]
       (cond-> xs#
         (.isArray (class xs#)) (->> (java.util.Arrays/asList) (new java.util.ArrayList))
         :always (doto (.add ^java.util.List ~val))))))


;; Test framework macros for Squint
(defmacro deftest
  "Define a test case for Squint.
   Wraps the body in a try-catch block and prints test results."
  [name & body]
  `(do
     (~'js* "// ~{}\n" ~name)
     (println (str "\n" ~(str name) ":"))
     (try
       ~@body
       (catch ~'js/Error e#
         (println (str "  ✗ Error: " (.-message e#)))
         (~'js/process.exit 1)))))

(defmacro testing
  "Group related assertions with a descriptive label.
   Prints the description and executes the body."
  [description & body]
  `(do
     (println (str "  " ~description))
     ~@body))

(defmacro is
  "Assert that a form evaluates to true.
   Special handling for equality assertions (= expected actual).

   Usage:
   (is (= 5 (+ 2 3)))           ; equality assertion
   (is (> x 0))                 ; boolean assertion
   (is (= 5 result) \"custom message\")  ; with custom message"
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

(defmacro run-tests
  "Print a success message indicating all tests have completed.
   Compatible with clojure.test/run-tests interface (ignores arguments)."
  [& _args]
  `(println "\nTests completed successfully! ✓"))

(defn- -set-at [env arr idx value]
  (if (:ns env)
    `(aset ~arr ~idx ~value)
    `(.set ~arr ~idx ~value)))

(defmacro setf [place value]
  "setf: Common Lisp-style generalized assignment
   Supports setting array elements: (setf (aref arr idx) val)
   Supports setting hash table entries: (setf (gethash ht key) val)"
  (if (seq? place)
    (let [sym (first place)
          sym-name (if (symbol? sym) (name sym) nil)]
      (cond
        ;; Setting array element
        (or (= 'aref sym) (= "aref" sym-name))
        (let [[_ arr idx] place]
          (-set-at &env arr idx value))

        ;; Setting hash table entry
        (or (= 'gethash sym) (= "gethash" sym-name))
        (let [[_ ht key] place]
          (if (:ns &env)
            ;; JS Map .set returns the map, so we need to return the value
            ;; Normalize literal keywords to strings at compile time
            `(let [value# ~value] (.set ~ht ~(normalize-key key) value#) value#)
            ;; HashMap .put returns old value, so we need to return the new value
            `(let [value# ~value] (.put ^java.util.Map ~ht ~key value#) value#)))

        ;; Unknown place form
        :else
        `(set! ~place ~value)))
    ;; Regular variable assignment: (setf var val) => (set! var val)
    `(set! ~place ~value)))

(defmacro forv
  "Optimized array comprehension for range expressions.
   Pre-allocates an array and uses dotimes for efficient iteration.

   Usage:
   (forv [i (range 0 5)] (* i 2))  ; returns array [0 2 4 6 8]

   Note: Returns an array (not a vector) for performance."
  [[binding range-expr] body]
  (let [[_ start end] range-expr  ; destructure (range start end), ignore 'range symbol
        arr-sym (gensym)
        idx-sym (gensym)]
    `(let [start# ~start
           end# ~end
           size# (- end# start#)
           ~arr-sym (make-array size#)]
       (dotimes [~idx-sym size#]
         (let [~binding (+ start# ~idx-sym)]
           ~(-set-at &env arr-sym idx-sym body)))
       ~arr-sym)))


(comment
  (macroexpand '(forv [i (range 1 5)] i))
  (forv [i (range 1 5)] i))