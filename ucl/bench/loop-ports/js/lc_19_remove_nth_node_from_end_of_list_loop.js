// node_modules/squint-cljs/src/squint/core.js
var M3_C1 = 3432918353 | 0;
var M3_C2 = 461845907 | 0;
function truth_(x) {
  return x != null && x !== false;
}

// out/build/js/squintcode/lc_19_remove_nth_node_from_end_of_list_loop.mjs
var removeNthFromEnd = function(head, n) {
  const dummy_1 = new ListNode(0, head);
  let left28058_2 = null;
  const right_3 = head;
  const i_4 = 0;
  if (i_4 >= n) {
    const l_5 = dummy_1;
    const r_6 = right_3;
    let l_7 = l_5;
    let r_8 = r_6;
    while (true) {
      if (truth_(r_8)) {
        const l_9 = l_7.next;
        const r_10 = r_8.next;
        let G__11 = l_9;
        let G__12 = r_10;
        l_7 = G__11;
        r_8 = G__12;
        continue;
      } else {
        left28058_2 = l_7;
      }
      ;
      break;
    }
  } else {
    let right_13 = right_3;
    let i_14 = i_4;
    while (true) {
      const right_15 = right_13.next;
      const i_16 = i_14 + 1;
      if (i_16 >= n) {
        const l_17 = dummy_1;
        const r_18 = right_15;
        let l_19 = l_17;
        let r_20 = r_18;
        while (true) {
          if (truth_(r_20)) {
            const l_21 = l_19.next;
            const r_22 = r_20.next;
            let G__23 = l_21;
            let G__24 = r_22;
            l_19 = G__23;
            r_20 = G__24;
            continue;
          } else {
            left28058_2 = l_19;
          }
          ;
          break;
        }
      } else {
        let G__25 = right_15;
        let G__26 = i_16;
        right_13 = G__25;
        i_14 = G__26;
        continue;
      }
      ;
      break;
    }
  }
  ;
  let left_27 = left28058_2;
  left_27.next = left_27.next.next;
  return dummy_1.next;
};