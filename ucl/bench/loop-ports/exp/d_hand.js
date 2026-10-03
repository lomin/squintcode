// the port's semantics, hand-written as the original's shape: test at the top, lo a loop variable
var maxProfit = function(prices) {
  const len = prices.length;
  let i = 0, lo = 0, acc = 0, first = true;
  while (true) {
    if (i < len) {
      const p = prices[i];
      lo = first ? p : (p < lo ? p : lo);
      const x = p - lo;
      acc = first ? x : (x > acc ? x : acc);
      first = false;
      i = i + 1;
      continue;
    } else { return acc; }
  }
};
