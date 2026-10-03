(ns ucl.leetcode
  "LeetCode's own classes, as strict test fixtures (D19). ucl.api declares
   ListNode and TreeNode as cljs.core names (H27); this supplies their values.
   Like LeetCode's, each throws when called without `new`. Never part of a
   submission.")

(defn- strict! [self ctor nm]
  (when-not ^boolean (.isPrototypeOf (.-prototype ctor) self)
    (throw (js/TypeError. (str "Class constructor " nm " cannot be invoked without 'new'")))))

(defn- ListNode* [val next]
  (this-as self
    (strict! self ListNode* "ListNode")
    (set! (.-val self) (if (undefined? val) 0 val))
    (set! (.-next self) (if (undefined? next) nil next))
    self))

(defn- TreeNode* [val left right]
  (this-as self
    (strict! self TreeNode* "TreeNode")
    (set! (.-val self) (if (undefined? val) 0 val))
    (set! (.-left self) (if (undefined? left) nil left))
    (set! (.-right self) (if (undefined? right) nil right))
    self))

(set! (.-ListNode js/cljs.core) ListNode*)
(set! (.-TreeNode js/cljs.core) TreeNode*)
