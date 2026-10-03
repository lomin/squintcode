// the port's shape exactly, but lo as a recur-style loop variable rather than an outer let
var maxProfit = function(prices) {
  const vec = prices; const len = vec.length; const i0 = 0;
  if (i0 >= len) { return 0; } else {
    let i = i0, acc = 0, first = true, lofirst = true, lo = 0;
    while (true) {
      const p = vec[i];
      const lo2 = lofirst ? p : (p < lo ? p : lo);
      const x = p - lo2;
      const acc2 = first ? x : (x > acc ? x : acc);
      const i2 = i + 1;
      if (i2 >= len) { return acc2; } else { i = i2; acc = acc2; first = false; lofirst = false; lo = lo2; continue; }
    }
  }
};
