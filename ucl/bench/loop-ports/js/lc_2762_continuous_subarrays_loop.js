// out/build/js/squintcode/lc_2762_continuous_subarrays_loop.mjs
var count_steady_stretches = function(nums, gap) {
  const n_1 = nums.length;
  const maxq_2 = new Int32Array(n_1);
  const minq_3 = new Int32Array(n_1);
  let left_4 = 0;
  let maxh_5 = 0;
  let maxt_6 = 0;
  let minh_7 = 0;
  let mint_8 = 0;
  const acc29878_9 = 0;
  const len30295_10 = nums.length;
  const i30296_11 = 0;
  if (i30296_11 >= len30295_10) {
    return acc29878_9;
  } else {
    let i30296_12 = i30296_11;
    let acc29878_13 = acc29878_9;
    while (true) {
      const x_14 = nums[i30296_12];
      const r_15 = i30296_12;
      while (true) {
        if (minh_7 < mint_8 && x_14 - nums[minq_3[minh_7]] > gap) {
          left_4 = minq_3[minh_7] + 1;
          minh_7 = minh_7 + 1;
          continue;
        } else {
        }
        ;
        break;
      }
      ;
      while (true) {
        if (maxh_5 < maxt_6 && nums[maxq_2[maxh_5]] - x_14 > gap) {
          left_4 = maxq_2[maxh_5] + 1;
          maxh_5 = maxh_5 + 1;
          continue;
        } else {
        }
        ;
        break;
      }
      ;
      while (true) {
        if (maxh_5 < maxt_6 && nums[maxq_2[maxt_6 - 1]] <= x_14) {
          maxt_6 = maxt_6 - 1;
          continue;
        } else {
        }
        ;
        break;
      }
      ;
      while (true) {
        if (minh_7 < mint_8 && nums[minq_3[mint_8 - 1]] >= x_14) {
          mint_8 = mint_8 - 1;
          continue;
        } else {
        }
        ;
        break;
      }
      ;
      maxq_2[maxt_6] = r_15;
      minq_3[mint_8] = r_15;
      maxt_6 = maxt_6 + 1;
      mint_8 = mint_8 + 1;
      const acc29878_16 = acc29878_13 + (r_15 - left_4 - -1);
      const i30296_17 = i30296_12 + 1;
      if (i30296_17 >= len30295_10) {
        return acc29878_16;
      } else {
        let G__18 = i30296_17;
        let G__19 = acc29878_16;
        i30296_12 = G__18;
        acc29878_13 = G__19;
        continue;
      }
      ;
      ;
      break;
    }
  }
  ;
};
var continuousSubarrays = function(nums) {
  return count_steady_stretches(nums, 2);
};