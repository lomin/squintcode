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
  const acc29335_9 = 0;
  const len29752_10 = nums.length;
  const i29753_11 = 0;
  if (i29753_11 >= len29752_10) {
    return acc29335_9;
  } else {
    const r_12 = 0;
    let i29753_13 = i29753_11;
    let acc29335_15 = acc29335_9;
    while (true) {
      const r_14 = i29753_13;
      const x_16 = nums[i29753_13];
      while (true) {
        if (minh_7 < mint_8 && x_16 - nums[minq_3[minh_7]] > gap) {
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
        if (maxh_5 < maxt_6 && nums[maxq_2[maxh_5]] - x_16 > gap) {
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
        if (maxh_5 < maxt_6 && nums[maxq_2[maxt_6 - 1]] <= x_16) {
          maxt_6 = maxt_6 - 1;
          continue;
        } else {
        }
        ;
        break;
      }
      ;
      while (true) {
        if (minh_7 < mint_8 && nums[minq_3[mint_8 - 1]] >= x_16) {
          mint_8 = mint_8 - 1;
          continue;
        } else {
        }
        ;
        break;
      }
      ;
      maxq_2[maxt_6] = r_14;
      minq_3[mint_8] = r_14;
      maxt_6 = maxt_6 + 1;
      mint_8 = mint_8 + 1;
      const acc29335_17 = acc29335_15 + (r_14 - left_4 - -1);
      const i29753_18 = i29753_13 + 1;
      if (i29753_18 >= len29752_10) {
        return acc29335_17;
      } else {
        let G__20 = i29753_18;
        let G__22 = acc29335_17;
        i29753_13 = G__20;
        acc29335_15 = G__22;
        continue;
      }
      ;
      ;
      break;
    }
    ;
  }
  ;
};
var continuousSubarrays = function(nums) {
  return count_steady_stretches(nums, 2);
};