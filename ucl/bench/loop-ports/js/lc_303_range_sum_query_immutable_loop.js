// out/build/js/squintcode/lc_303_range_sum_query_immutable_loop.mjs
var build_prefix_sum = function(nums) {
  const ps_1 = new Int32Array(nums.length + 1);
  let s_2 = 0;
  const len31533_3 = nums.length;
  const i31534_4 = 0;
  if (i31534_4 >= len31533_3) {
  } else {
    let i31534_5 = i31534_4;
    while (true) {
      const x_6 = nums[i31534_5];
      const i_7 = i31534_5 + 1;
      s_2 = s_2 + x_6;
      ps_1[i_7] = s_2;
      const i31534_8 = i31534_5 + 1;
      if (i31534_8 >= len31533_3) {
      } else {
        let G__9 = i31534_8;
        i31534_5 = G__9;
        continue;
      }
      ;
      break;
    }
  }
  ;
  return ps_1;
};
var NumArray = function(nums) {
  const self31688_1 = this;
  if (NumArray.prototype.isPrototypeOf(self31688_1)) {
    const prefix_sum_2 = build_prefix_sum(nums);
    self31688_1.prefix_sum = prefix_sum_2;
    return self31688_1;
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