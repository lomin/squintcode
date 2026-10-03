// as e, but lo the outer let the port assigns
var maxProfit = function(prices) {
  let lo = 0;
  const vec = prices; const len = vec.length; const i0 = 0;
  if (i0 >= len) { return 0; } else {
    let i = i0, acc = 0, first = true, lofirst = true;
    while (true) {
      const p = vec[i];
      if (lofirst) { lo = p; } else { if (p < lo) { lo = p; } }
      const x = p - lo;
      const acc2 = first ? x : (x > acc ? x : acc);
      const i2 = i + 1;
      if (i2 >= len) { return acc2; } else { i = i2; acc = acc2; first = false; lofirst = false; continue; }
    }
  }
};
