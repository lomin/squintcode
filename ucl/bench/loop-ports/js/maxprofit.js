// out/build/js/ucl/api.mjs
var double_float_positive_infinity = Infinity;

// out/build/js/squintcode/maxprofit.mjs
var maxProfit = function(prices) {
  const n_1 = prices.length;
  let i_2 = 0;
  let min_price_3 = double_float_positive_infinity;
  let max_profit_4 = 0;
  while (true) {
    if (i_2 < n_1) {
      const price_5 = prices[i_2];
      let G__6 = i_2 + 1;
      let G__7 = min_price_3 < price_5 ? min_price_3 : price_5;
      let G__8 = Math.max(max_profit_4, price_5 - min_price_3);
      i_2 = G__6;
      min_price_3 = G__7;
      max_profit_4 = G__8;
      continue;
    } else {
      return max_profit_4;
    }
    ;
    ;
    break;
  }
  ;
};