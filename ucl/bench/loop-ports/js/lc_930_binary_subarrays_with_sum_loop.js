// out/build/js/squintcode/lc_930_binary_subarrays_with_sum_loop.mjs
var numSubarraysWithSum = function(nums, goal) {
  const freq_1 = new Int32Array(nums.length + 1);
  freq_1[0] = 1;
  let s_2 = 0;
  const acc32810_3 = 0;
  const len32919_4 = nums.length;
  const i32920_5 = 0;
  if (i32920_5 >= len32919_4) {
    return acc32810_3;
  } else {
    let i32920_6 = i32920_5;
    let acc32810_7 = acc32810_3;
    while (true) {
      const x_8 = nums[i32920_6];
      s_2 = s_2 + x_8;
      const test32923_9 = s_2 >= goal;
      const acc32810_10 = test32923_9 ? acc32810_7 + freq_1[s_2 - goal] : acc32810_7;
      freq_1[s_2] = freq_1[s_2] + 1;
      const i32920_11 = i32920_6 + 1;
      if (i32920_11 >= len32919_4) {
        return acc32810_10;
      } else {
        let G__12 = i32920_11;
        let G__13 = acc32810_10;
        i32920_6 = G__12;
        acc32810_7 = G__13;
        continue;
      }
      ;
      ;
      break;
    }
  }
  ;
};