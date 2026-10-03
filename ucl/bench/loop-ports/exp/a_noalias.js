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
  const acc24153_9 = 0;
  
  const len24155_11 = nums.length;
  const i24156_12 = 0;
  if (i24156_12 >= len24155_11) {
    return acc24153_9;
  } else {
    const r_13 = 0;
    let i24156_14 = i24156_12;
    let r_15 = r_13;
    let acc24153_16 = acc24153_9;
    while (true) {
      const x_17 = nums[i24156_14];
      while (true) {
        if (minh_7 < mint_8 && x_17 - nums[minq_3[minh_7]] > gap) {
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
        if (maxh_5 < maxt_6 && nums[maxq_2[maxh_5]] - x_17 > gap) {
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
        if (maxh_5 < maxt_6 && nums[maxq_2[maxt_6 - 1]] <= x_17) {
          maxt_6 = maxt_6 - 1;
          continue;
        } else {
        }
        ;
        break;
      }
      ;
      while (true) {
        if (minh_7 < mint_8 && nums[minq_3[mint_8 - 1]] >= x_17) {
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
      const acc24153_18 = acc24153_16 + (r_15 - left_4 - -1);
      const i24156_19 = i24156_14 + 1;
      if (i24156_19 >= len24155_11) {
        return acc24153_18;
      } else {
        const r_20 = r_15 + 1;
        let G__21 = i24156_19;
        let G__22 = r_20;
        let G__23 = acc24153_18;
        i24156_14 = G__21;
        r_15 = G__22;
        acc24153_16 = G__23;
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