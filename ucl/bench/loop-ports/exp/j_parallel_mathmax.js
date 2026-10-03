// d_hand, but the profit uses the previous low (as the original): no dependency chain
var maxProfit = function(prices) {
  const len = prices.length;
  let i = 0, lo = 2147483647, acc = 0;
  while (true) {
    if (i < len) {
      const p = prices[i];
      const x = p - lo;
      acc = Math.max(acc, x);
      lo = p < lo ? p : lo;
      i = i + 1;
      continue;
    } else { return acc; }
  }
};
