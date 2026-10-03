// node_modules/squint-cljs/src/squint/core.js
var M3_C1 = 3432918353 | 0;
var M3_C2 = 461845907 | 0;
function truth_(x) {
  return x != null && x !== false;
}
function mod(x, y) {
  return (x % y + y) % y;
}

// out/build/js/squintcode/utils.mjs
var assoc_arr_BANG_ = function(arr, k, v) {
  arr[k] = v;
  return arr;
};

// out/build/js/squintcode/fizzbuzz.mjs
var fizz_buzz_pred = function(a, b) {
  return mod(b, a) === 0;
};
var fizz_buzz_word = function(x) {
  const pred__26486_1 = fizz_buzz_pred;
  const expr__26487_2 = x;
  if (truth_(pred__26486_1(15, expr__26487_2))) {
    return "FizzBuzz";
  } else {
    if (truth_(pred__26486_1(3, expr__26487_2))) {
      return "Fizz";
    } else {
      if (truth_(pred__26486_1(5, expr__26487_2))) {
        return "Buzz";
      } else {
        return String(x);
      }
    }
  }
  ;
};
var fizzBuzz = function(n) {
  const answer_1 = new Array(n).fill("");
  let i_2 = 0;
  while (true) {
    if (i_2 < n) {
      answer_1[i_2] = fizz_buzz_word(i_2 + 1);
      let G__3 = i_2 + 1;
      i_2 = G__3;
      continue;
    } else {
      return answer_1;
    }
    ;
    ;
    break;
  }
  ;
};
var fizzBuzz2 = function(n) {
  let x_1 = 1;
  let result_2 = new Array(n).fill("");
  while (true) {
    if (x_1 <= n) {
      let G__3 = x_1 + 1;
      let G__4 = assoc_arr_BANG_(result_2, x_1 - 1, fizz_buzz_word(x_1));
      x_1 = G__3;
      result_2 = G__4;
      continue;
    } else {
      return result_2;
    }
    ;
    ;
    break;
  }
  ;
};