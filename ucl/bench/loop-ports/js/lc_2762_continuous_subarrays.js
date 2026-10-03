// out/build/js/squintcode/lc_2762_continuous_subarrays.mjs
var count_steady_stretches = function(nums, gap) {
  const n_1 = nums.length;
  const maxq_2 = new Int32Array(n_1);
  const minq_3 = new Int32Array(n_1);
  let left_4 = 0;
  let maxh_5 = 0;
  let maxt_6 = 0;
  let minh_7 = 0;
  let mint_8 = 0;
  let total_9 = 0;
  let r_10 = 0;
  while (true) {
    if (r_10 < n_1) {
      const x_11 = nums[r_10];
      while (true) {
        if (minh_7 < mint_8 && x_11 - nums[minq_3[minh_7]] > gap) {
          left_4 = minq_3[minh_7] + 1;
          minh_7 = minh_7 + 1;
          continue;
        }
        ;
        break;
      }
      ;
      while (true) {
        if (maxh_5 < maxt_6 && nums[maxq_2[maxh_5]] - x_11 > gap) {
          left_4 = maxq_2[maxh_5] + 1;
          maxh_5 = maxh_5 + 1;
          continue;
        }
        ;
        break;
      }
      ;
      while (true) {
        if (maxh_5 < maxt_6 && nums[maxq_2[maxt_6 - 1]] <= x_11) {
          maxt_6 = maxt_6 - 1;
          continue;
        }
        ;
        break;
      }
      ;
      while (true) {
        if (minh_7 < mint_8 && nums[minq_3[mint_8 - 1]] >= x_11) {
          mint_8 = mint_8 - 1;
          continue;
        }
        ;
        break;
      }
      ;
      maxq_2[maxt_6] = r_10;
      minq_3[mint_8] = r_10;
      maxt_6 = maxt_6 + 1;
      mint_8 = mint_8 + 1;
      total_9 = total_9 + (r_10 - left_4 - -1);
      let G__12 = r_10 + 1;
      r_10 = G__12;
      continue;
    } else {
      return total_9;
    }
    ;
    ;
    break;
  }
  ;
};
var continuousSubarrays = function(nums) {
  return count_steady_stretches(nums, 2);
};