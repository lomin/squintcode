// out/build/js/squintcode/lc_303_range_sum_query_immutable.mjs
var build_prefix_sum = function(nums) {
  const n_1 = nums.length;
  const ps_2 = new Int32Array(n_1 + 1);
  let i_3 = 0;
  let sum_4 = 0;
  while (true) {
    if (i_3 < n_1) {
      const sum_5 = sum_4 + nums[i_3];
      ps_2[i_3 + 1] = sum_5;
      let G__6 = i_3 + 1;
      let G__7 = sum_5;
      i_3 = G__6;
      sum_4 = G__7;
      continue;
    } else {
      return ps_2;
    }
    ;
    ;
    break;
  }
  ;
};
var NumArray = function(nums) {
  const self31069_1 = this;
  if (NumArray.prototype.isPrototypeOf(self31069_1)) {
    const prefix_sum_2 = build_prefix_sum(nums);
    self31069_1.prefix_sum = prefix_sum_2;
    return self31069_1;
  } else {
    return new NumArray(nums);
  }
  ;
};
NumArray.prototype.sumRange = (function(left, right) {
  const this$_1 = this;
  return this$_1.prefix_sum[right + 1] - this$_1.prefix_sum[left];
});
var sumRange = function(this$, left, right) {
  return this$.sumRange(left, right);
};