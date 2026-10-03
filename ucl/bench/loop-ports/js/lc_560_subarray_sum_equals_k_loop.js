// out/build/js/ucl/api.mjs
var get_or_default = function(k, m, d) {
  const v_1 = m.get(k);
  if (void 0 === v_1) {
    return d;
  } else {
    return v_1;
  }
  ;
};
var puthash = function(k, m, v) {
  m.set(k, v);
  return v;
};

// out/build/js/squintcode/lc_560_subarray_sum_equals_k_loop.mjs
var subarraySum = function(nums, k) {
  const freq_1 = /* @__PURE__ */ new Map([[0, 1]]);
  let s_2 = 0;
  const acc32172_3 = 0;
  const len32269_4 = nums.length;
  const i32270_5 = 0;
  if (i32270_5 >= len32269_4) {
    return acc32172_3;
  } else {
    let i32270_6 = i32270_5;
    let acc32172_7 = acc32172_3;
    while (true) {
      const x_8 = nums[i32270_6];
      s_2 = s_2 + x_8;
      const acc32172_9 = acc32172_7 + get_or_default(s_2 - k, freq_1, 0);
      puthash(s_2, freq_1, get_or_default(s_2, freq_1, 0) + 1);
      const i32270_10 = i32270_6 + 1;
      if (i32270_10 >= len32269_4) {
        return acc32172_9;
      } else {
        let G__11 = i32270_10;
        let G__12 = acc32172_9;
        i32270_6 = G__11;
        acc32172_7 = G__12;
        continue;
      }
      ;
      ;
      break;
    }
  }
  ;
};