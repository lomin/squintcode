(ns hooks.ucl-forms
  "What ucl's definers define, for clj-kondo (I54): their Common Lisp lambda
   lists and declarations as the defn clj-kondo knows. Hand-written; the
   generated ucl_api.clj holds the operators themselves.")

(defn- lambda-vars
  "The variables of a lambda list: `(a &optional (b 1) &aux c)` -> [a b c]."
  [ll]
  (->> ll
       (remove #{'&optional '&aux '&key '&rest})
       (map #(if (seq? %) (first %) %))
       vec))

(defn- code
  "A body without its declarations, which are not Clojure."
  [body]
  (remove #(and (seq? %) (= 'declare (first %))) body))

(defmacro defun [fname lambda-list & body]
  (list* 'defn fname (lambda-vars lambda-list) (code body)))

(defmacro defmethod [mname [[self _struct] & more] & body]
  (list* 'defn mname (into [self] (lambda-vars more)) (code body)))

(defmacro defstruct [name-and-options & _slots]
  (let [[nm & options] (if (seq? name-and-options) name-and-options [name-and-options])
        ctors (keep (fn [o] (when (and (seq? o) (= :constructor (first o))) (second o))) options)
        ctors (if (some #(and (seq? %) (= :constructor (first %))) options)
                ctors
                [(symbol (str "make-" nm))])]
    (cons 'do (map (fn [c] (list 'defn c '[& args])) ctors))))

(defmacro defruntime [opts]
  (concat '(do (def most-positive-fixnum 2147483647)
               (def most-negative-fixnum -2147483648)
               (def double-float-positive-infinity 0)
               (def double-float-negative-infinity 0))
          (when (contains? opts :min-inline)
            '((defn min "(min real...) -- returns the least argument." [& reals])
              (defn max "(max real...) -- returns the greatest argument." [& reals])))))
