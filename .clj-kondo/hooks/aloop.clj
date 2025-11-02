(ns hooks.aloop
  (:require [clj-kondo.hooks-api :as api]))

(defn aloop [{:keys [node]}]
  (let [[_ arr-expr elem-var state-bindings & body] (:children node)]
    ;; Transform (aloop arr elem [bindings] body)
    ;; into (let [elem (nth arr 0 nil)] (loop [bindings] body))
    ;; elem gets first element type from array for type checking
    (when (and elem-var (api/vector-node? state-bindings))
      {:node (api/list-node
              [(api/token-node 'let)
               (api/vector-node
                [elem-var
                 (api/list-node
                  [(api/token-node 'nth)
                   arr-expr
                   (api/token-node 0)
                   (api/token-node nil)])])
               (api/list-node
                (list*
                 (api/token-node 'loop)
                 state-bindings
                 body))])})))
