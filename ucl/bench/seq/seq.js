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

// out/build/js/squintcode/lc_1295_find_numbers_with_even_number_of_digits_seq.mjs
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
  const e14213_1 = nums.length;
  let i14215_2 = 0;
  let c14214_3 = 0;
  while (true) {
    if (i14215_2 < e14213_1) {
      const el14216_4 = nums[i14215_2];
      const x_5 = el14216_4;
      let G__6 = i14215_2 + 1;
      let G__7 = (digit_count(x_5) & 1) === 0 ? c14214_3 + 1 : c14214_3;
      i14215_2 = G__6;
      c14214_3 = G__7;
      continue;
    } else {
      return c14214_3;
    }
    ;
    ;
    break;
  }
  ;
};