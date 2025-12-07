(ns hooks.aloop
  (:require [clj-kondo.hooks-api :as api]))

(defn- with-form?
  "Check if node is a (with ...) or (namespace/with ...) call"
  [node]
  (and (api/list-node? node)
       (let [children (:children node)
             first-child (first children)]
         (and first-child
              (api/token-node? first-child)
              (= "with" (name (api/sexpr first-child)))))))

(defn- unwrap-with
  "If node is a with form, return the inner expression. Otherwise return node."
  [node]
  (if (with-form? node)
    (second (:children node))
    node))

(defn- process-bindings
  "Process binding pairs, separating with-bindings from loop-bindings.
   Returns {:let-bindings [...] :loop-bindings [...]}
   All bindings go into let-bindings (with 'with' unwrapped).
   Only non-with bindings go into loop-bindings (re-bound to their names)."
  [bindings-node]
  (let [children (:children bindings-node)
        pairs (partition 2 children)]
    (loop [pairs pairs
           let-bindings []
           loop-bindings []]
      (if-let [[name-node val-node] (first pairs)]
        (if (with-form? val-node)
          ;; with binding: add to let-bindings (unwrapped), skip in loop
          (recur (rest pairs)
                 (conj let-bindings name-node (unwrap-with val-node))
                 loop-bindings)
          ;; regular binding: add to let-bindings, re-bind in loop
          (recur (rest pairs)
                 (conj let-bindings name-node val-node)
                 (conj loop-bindings name-node name-node)))
        {:let-bindings let-bindings
         :loop-bindings loop-bindings}))))

(defn aloop [{:keys [node]}]
  (let [[_ arr-expr state-bindings & body] (:children node)]
    ;; Transform (aloop arr [bindings] body)
    ;; into (let [_self arr, self _self, it (nth arr 0 nil), ...let-bindings...]
    ;;        (loop [...loop-bindings...] body))
    ;; Anaphoric macro: binds 'it' to current element, 'self' to the collection
    ;; Bindings wrapped in (with ...) go only in let, not in loop
    (when (api/vector-node? state-bindings)
      (let [{:keys [let-bindings loop-bindings]} (process-bindings state-bindings)]
        {:node (api/list-node
                [(api/token-node 'let)
                 (api/vector-node
                  (concat
                   [(api/token-node '_self)
                    arr-expr
                    (api/token-node 'self)
                    (api/token-node '_self)
                    (api/token-node 'it)
                    (api/list-node
                     [(api/token-node 'nth)
                      arr-expr
                      (api/token-node 0)
                      (api/token-node nil)])]
                   let-bindings))
                 (api/list-node
                  (list*
                   (api/token-node 'loop)
                   (api/vector-node loop-bindings)
                   body))])}))))
