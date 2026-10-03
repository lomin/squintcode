// out/build/js/squintcode/maxprofit_loop.mjs
var maxProfit = function(prices) {
  let lo_1 = 2147483647;
  const acc33361_2 = -2147483648;
  const len33446_3 = prices.length;
  const i33447_4 = 0;
  if (i33447_4 >= len33446_3) {
    return acc33361_2;
  } else {
    let i33447_5 = i33447_4;
    let acc33361_6 = acc33361_2;
    while (true) {
      const p_7 = prices[i33447_5];
      lo_1 = lo_1 < p_7 ? lo_1 : p_7;
      const acc33361_8 = Math.max(acc33361_6, p_7 - lo_1);
      const i33447_9 = i33447_5 + 1;
      if (i33447_9 >= len33446_3) {
        return acc33361_8;
      } else {
        let G__10 = i33447_9;
        let G__11 = acc33361_8;
        i33447_5 = G__10;
        acc33361_6 = G__11;
        continue;
      }
      ;
      ;
      break;
    }
  }
  ;
};