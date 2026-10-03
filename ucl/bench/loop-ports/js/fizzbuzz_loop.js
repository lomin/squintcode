// node_modules/squint-cljs/src/squint/core.js
var M3_C1 = 3432918353 | 0;
var M3_C2 = 461845907 | 0;
function truth_(x) {
  return x != null && x !== false;
}
function mod(x, y) {
  return (x % y + y) % y;
}

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

// out/build/js/squintcode/fizzbuzz_loop.mjs
var fizzBuzz = function(n) {
  const answer_1 = new Array(n).fill("");
  const i_2 = 0;
  if (i_2 >= n) {
  } else {
    let i_3 = i_2;
    while (true) {
      answer_1[i_3] = fizz_buzz_word(i_3 + 1);
      const i_4 = i_3 + 1;
      if (i_4 >= n) {
      } else {
        let G__5 = i_4;
        i_3 = G__5;
        continue;
      }
      ;
      break;
    }
  }
  ;
  return answer_1;
};