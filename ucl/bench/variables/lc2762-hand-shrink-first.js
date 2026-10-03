// Hand-written, shrinking the window before the push (the first variables version's order).
var continuousSubarrays = function(nums) {
  const gap = 2, n = nums.length, mx = new Int32Array(n), mn = new Int32Array(n);
  let left = 0, mh = 0, mt = 0, nh = 0, nt = 0, total = 0;
  for (let r = 0; r < n; r++) {
    const x = nums[r];
    while (nh < nt && x - nums[mn[nh]] > gap) { left = mn[nh] + 1; nh++; }
    while (mh < mt && nums[mx[mh]] - x > gap) { left = mx[mh] + 1; mh++; }
    while (mh < mt && nums[mx[mt - 1]] <= x) mt--;
    while (nh < nt && nums[mn[nt - 1]] >= x) nt--;
    mx[mt] = r; mn[nt] = r; mt++; nt++;
    total += r - left + 1;
  }
  return total;
};
