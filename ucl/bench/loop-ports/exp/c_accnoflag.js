// out/build/js/squintcode/maxprofit_loop.mjs
var maxProfit = function(prices) {
  let lo_1 = 0;
  const acc26034_2 = 0;
  const first26035_3 = true;
  const lo_first26036_4 = true;
  const vec26037_5 = prices;
  const len26038_6 = vec26037_5.length;
  const i26039_7 = 0;
  if (i26039_7 >= len26038_6) {
    return acc26034_2;
  } else {
    let i26039_8 = i26039_7;
    let acc26034_9 = acc26034_2;
    let first26035_10 = first26035_3;
    let lo_first26036_11 = lo_first26036_4;
    while (true) {
      const p_12 = vec26037_5[i26039_8];
      const x26042_13 = p_12;
      if (lo_first26036_11) {
        lo_1 = x26042_13;
      } else {
        if (x26042_13 < lo_1) {
          lo_1 = x26042_13;
        } else {
          lo_1 = lo_1;
        }
      }
      ;
      const lo_first26036_14 = false;
      const x26043_15 = p_12 - lo_1;
      const acc26034_16 = x26043_15 > acc26034_9 ? x26043_15 : acc26034_9;
      const first26035_17 = false;
      const i26039_18 = i26039_8 + 1;
      if (i26039_18 >= len26038_6) {
        return acc26034_16;
      } else {
        let G__19 = i26039_18;
        let G__20 = acc26034_16;
        let G__21 = first26035_17;
        let G__22 = lo_first26036_14;
        i26039_8 = G__19;
        acc26034_9 = G__20;
        first26035_10 = G__21;
        lo_first26036_11 = G__22;
        continue;
      }
      ;
      ;
      break;
    }
  }
  ;
};