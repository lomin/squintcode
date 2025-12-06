(ns hooks.defclass
  (:require [clj-kondo.hooks-api :as api]))

(defn defclass [{:keys [node]}]
  ;; Transform (defclass ClassName [slots...]
  ;;             (:init (args...) body...)
  ;;             (:method name (args...) body...))
  ;; into something clj-kondo understands
  ;;
  ;; Strategy:
  ;; 1. defrecord for the type (validates slots, creates factory fn)
  ;; 2. fn forms that contain the bodies (validates symbol references)
  ;;    wrapped in (fn [] ...) so they're not "executed"
  (let [[_ class-name slots & specs] (:children node)
        slot-syms (:children slots)

        ;; Find :init and :method specs
        init-spec (first (filter #(and (api/list-node? %)
                                       (= :init (some-> % :children first api/sexpr)))
                                 specs))
        method-specs (filter #(and (api/list-node? %)
                                   (= :method (some-> % :children first api/sexpr)))
                             specs)

        ;; Parse :init -> (:init (args...) body...)
        [_ init-args & init-body] (when init-spec (:children init-spec))
        init-arg-syms (when init-args (:children init-args))

        ;; Parse methods
        methods (for [method method-specs
                      :let [[_ _method-name method-args & method-body] (:children method)
                            method-arg-syms (when method-args (:children method-args))]]
                  {:args method-arg-syms :body method-body})

        ;; Collect all args that need to be bound
        all-arg-syms (concat init-arg-syms (mapcat :args methods))

        ;; Collect all bodies
        all-bodies (concat init-body (mapcat :body methods))

        ;; Create a fn that binds slots and args, then contains all bodies
        ;; (fn [slot1 slot2 arg1 arg2 ...] body1 body2 ...)
        ;; This validates symbol references without "executing" the code
        validation-fn (when (seq all-bodies)
                        (api/list-node
                         (list* (api/token-node 'fn)
                                (api/vector-node (vec (concat slot-syms all-arg-syms)))
                                all-bodies)))]

    {:node (api/list-node
            (concat
             [(api/token-node 'do)
              ;; Define the type (use deftype, not defrecord, to match macro)
              (api/list-node
               [(api/token-node 'deftype)
                class-name
                slots])]
             ;; Validation fn (analyzes bodies for symbol references)
             (when validation-fn [validation-fn])))}))
