// out/build/js/squintcode/lc_930_binary_subarrays_with_sum.mjs
var numSubarraysWithSum = function(nums, goal) {
  const n_1 = nums.length;
  const freq_2 = new Int32Array(n_1 + 1);
  freq_2[0] = 1;
  let i_3 = 0;
  let running_sum_4 = 0;
  let result_5 = 0;
  while (true) {
    if (i_3 < n_1) {
      const running_sum_6 = running_sum_4 + nums[i_3];
      const want_7 = running_sum_6 - goal;
      const result_8 = want_7 >= 0 ? result_5 + freq_2[want_7] : result_5;
      freq_2[running_sum_6] = freq_2[running_sum_6] + 1;
      let G__9 = i_3 + 1;
      let G__10 = running_sum_6;
      let G__11 = result_8;
      i_3 = G__9;
      running_sum_4 = G__10;
      result_5 = G__11;
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