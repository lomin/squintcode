(ns ucl.leetcode
  "LeetCode's own classes, as test fixtures (D19). On LeetCode they are
   classes a solution names bare: (new ListNode 0 head). Never part of a
   submission: the bundler leaves references to them bare, for LeetCode's own.

   A bare symbol a namespace neither defines nor refers does not resolve on
   ClojureDart, and it refers only cljd.core's fields, never its classes. So,
   on the macro host, this namespace wraps the compiler's resolver: a bare
   ListNode or TreeNode that resolves to nothing else is this namespace's
   class (H45) -- what ucl.api's cljs.core declaration does on ClojureScript
   (H27). A test namespace requires ucl.leetcode before the solution.")

(deftype ListNode [^:mutable val ^:mutable next] :type-only true)
(deftype TreeNode [^:mutable val ^:mutable left ^:mutable right] :type-only true)

#?(:cljd/clj-host
   (def ^:macro-support bare-names-installed
     (let [v #'cljd.compiler/resolve-non-local-symbol]
       (when-not (:ucl/leetcode (meta @v))
         (alter-var-root
          v (fn [resolve]
              (with-meta
                (fn [sym type-vars]
                  (or (resolve sym type-vars)
                      (when (and (nil? (namespace sym)) (#{"ListNode" "TreeNode"} (name sym)))
                        (resolve (with-meta (symbol "ucl.leetcode" (name sym)) (meta sym))
                                 type-vars))))
                {:ucl/leetcode true}))))
       true)))
