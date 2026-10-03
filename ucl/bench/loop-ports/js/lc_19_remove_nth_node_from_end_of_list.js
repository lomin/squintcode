// node_modules/squint-cljs/src/squint/core.js
var M3_C1 = 3432918353 | 0;
var M3_C2 = 461845907 | 0;
function truth_(x) {
  return x != null && x !== false;
}

// out/build/js/squintcode/lc_19_remove_nth_node_from_end_of_list.mjs
var move_right_n_forward = function(head, n) {
  while (true) {
    if (truth_(truth_(head) ? n > 0 : head)) {
      let G__1 = head.next;
      let G__2 = n - 1;
      head = G__1;
      n = G__2;
      continue;
    } else {
      return head;
    }
    ;
    break;
  }
};
var move_left_n_from_end = function(left, right) {
  while (true) {
    if (truth_(right)) {
      let G__1 = left.next;
      let G__2 = right.next;
      left = G__1;
      right = G__2;
      continue;
    } else {
      return left;
    }
    ;
    break;
  }
};
var bypass = function(node) {
  return node.next = (() => {
    const G__27518_1 = node;
    const G__27518_2 = G__27518_1 == null ? null : G__27518_1.next;
    if (G__27518_2 == null) {
      return null;
    } else {
      return G__27518_2.next;
    }
    ;
  })();
};
var removeNthFromEnd = function(head, n) {
  const dummy_1 = new ListNode(0, head);
  bypass(move_left_n_from_end(dummy_1, move_right_n_forward(head, n)));
  return dummy_1.next;
};