// the port exactly (order and first-flags), but maximize through Math.max on the expression
var maxProfit = function(prices) {
  const len = prices.length;
  let i = 0, lo = 0, acc = 0, first = true;
  while (true) {
    if (i < len) {
      const p = prices[i];
      lo = first ? p : (p < lo ? p : lo);
      acc = first ? p - lo : Math.max(acc, p - lo);
      first = false;
      i = i + 1;
      continue;
    } else { return acc; }
  }
};
