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

// out/build/js/squintcode/lc_560_subarray_sum_equals_k.mjs
var subarraySum = function(nums, k) {
  const n_1 = nums.length;
  const freq_2 = /* @__PURE__ */ new Map([[0, 1]]);
  let i_3 = 0;
  let running_sum_4 = 0;
  let result_5 = 0;
  while (true) {
    if (i_3 < n_1) {
      const running_sum_6 = running_sum_4 + nums[i_3];
      const result_7 = result_5 + get_or_default(running_sum_6 - k, freq_2, 0);
      puthash(running_sum_6, freq_2, get_or_default(running_sum_6, freq_2, 0) + 1);
      let G__8 = i_3 + 1;
      let G__9 = running_sum_6;
      let G__10 = result_7;
      i_3 = G__8;
      running_sum_4 = G__9;
      result_5 = G__10;
      continue;
    } else {
      return result_5;
    }
    ;
    ;
    break;
  }
  ;
};