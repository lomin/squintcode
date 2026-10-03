// ../leetcode/node_modules/squint-cljs/src/squint/core.js
var M3_C1 = 3432918353 | 0;
var M3_C2 = 461845907 | 0;
function fix(q) {
  if (q >= 0) {
    return Math.floor(q);
  }
  return Math.ceil(q);
}
function quot(n, d) {
  const rem = n % d;
  return fix((n - rem) / d);
}

// out/build/js/squintcode/lc_1295_find_numbers_with_even_number_of_digits.mjs
var digit_count = function(x) {
  let x_1 = x;
  let d_2 = 1;
  while (true) {
    if (x_1 >= 10) {
      x_1 = quot(x_1, 10);
      d_2 = d_2 + 1;
      continue;
    }
    ;
    break;
  }
  ;
  return d_2;
};
var findNumbers = function(nums) {
  const n_1 = nums.length;
  let i_2 = 0;
  let c_3 = 0;
  while (true) {
    if (i_2 < n_1) {
      let G__4 = i_2 + 1;
      let G__5 = (digit_count(nums[i_2]) & 1) === 0 ? c_3 + 1 : c_3;
      i_2 = G__4;
      c_3 = G__5;
      continue;
    } else {
      return c_3;
    }
    ;
    ;
    break;
  }
  ;
};