// out/build/js/ucl/api.mjs
var most_positive_fixnum = 2147483647;

// out/build/js/squintcode/probe_hof.mjs
var fold = function(f, init, xs) {
  const n_1 = xs.length;
  let i_2 = 0;
  let acc_3 = init;
  while (true) {
    if (i_2 < n_1) {
      let G__4 = i_2 + 1;
      let G__5 = f(acc_3, xs[i_2]);
      i_2 = G__4;
      acc_3 = G__5;
      continue;
    } else {
      return acc_3;
    }
    ;
    ;
    break;
  }
  ;
};
var each = function(f, xs) {
  const n8679_1 = xs.length;
  let i_2 = 0;
  while (true) {
    if (i_2 < n8679_1) {
      f(xs[i_2]);
      let G__3 = i_2 + 1;
      i_2 = G__3;
      continue;
    } else {
      return null;
    }
    ;
    ;
    break;
  }
  ;
};
var sumLoop = function(nums) {
  const n_1 = nums.length;
  let i_2 = 0;
  let acc_3 = 0;
  while (true) {
    if (i_2 < n_1) {
      let G__4 = i_2 + 1;
      let G__5 = acc_3 + nums[i_2];
      i_2 = G__4;
      acc_3 = G__5;
      continue;
    } else {
      return acc_3;
    }
    ;
    ;
    break;
  }
  ;
};
var sumFold = function(nums) {
  return fold((function(acc, x) {
    return acc + x;
  }), 0, nums);
};
var countLoop = function(nums, k) {
  const n_1 = nums.length;
  let i_2 = 0;
  let acc_3 = 0;
  while (true) {
    if (i_2 < n_1) {
      let G__4 = i_2 + 1;
      let G__5 = nums[i_2] >= k ? acc_3 + 1 : acc_3;
      i_2 = G__4;
      acc_3 = G__5;
      continue;
    } else {
      return acc_3;
    }
    ;
    ;
    break;
  }
  ;
};
var countFold = function(nums, k) {
  return fold((function(acc, x) {
    if (x >= k) {
      return acc + 1;
    } else {
      return acc;
    }
    ;
  }), 0, nums);
};
var profitLoop = function(prices) {
  const n_1 = prices.length;
  let i_2 = 0;
  let lo_3 = most_positive_fixnum;
  let best_4 = 0;
  while (true) {
    if (i_2 < n_1) {
      const p_5 = prices[i_2];
      let G__6 = i_2 + 1;
      let G__7 = lo_3 < p_5 ? lo_3 : p_5;
      let G__8 = Math.max(best_4, p_5 - lo_3);
      i_2 = G__6;
      lo_3 = G__7;
      best_4 = G__8;
      continue;
    } else {
      return best_4;
    }
    ;
    ;
    break;
  }
  ;
};
var profitEach = function(prices) {
  let lo_1 = most_positive_fixnum;
  let best_2 = 0;
  each((function(p) {
    lo_1 = lo_1 < p ? lo_1 : p;
    return best_2 = Math.max(best_2, p - lo_1);
  }), prices);
  return best_2;
};
var profitEach2 = function(prices) {
  let lo_1 = most_positive_fixnum;
  let best_2 = 0;
  each((function(p) {
    lo_1 = lo_1 < p ? lo_1 : p;
    const d_3 = p - lo_1;
    return best_2 = best_2 > d_3 ? best_2 : d_3;
  }), prices);
  return best_2;
};