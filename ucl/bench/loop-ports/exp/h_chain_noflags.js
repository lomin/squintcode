// the port's order (low first, then profit), no flags
var maxProfit = function(prices) {
  const len = prices.length;
  let i = 0, lo = 2147483647, acc = 0;
  while (true) {
    if (i < len) {
      const p = prices[i];
      lo = p < lo ? p : lo;
      const x = p - lo;
      acc = x > acc ? x : acc;
      i = i + 1;
      continue;
    } else { return acc; }
  }
};
