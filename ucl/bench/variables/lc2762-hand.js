// Hand-written JavaScript baseline, for comparison with the ucl build.
/**
 * @param {number[]} nums
 * @return {number}
 */
var continuousSubarrays = function(nums) {
  const n = nums.length, mx = new Int32Array(n), mn = new Int32Array(n);
  let mh = 0, mt = 0, nh = 0, nt = 0, left = 0, total = 0;
  for (let r = 0; r < n; r++) {
    const x = nums[r];
    while (mt > mh && nums[mx[mt - 1]] <= x) mt--;
    while (nt > nh && nums[mn[nt - 1]] >= x) nt--;
    mx[mt++] = r; mn[nt++] = r;
    while (nums[mx[mh]] - nums[mn[nh]] > 2) {
      if (mx[mh] < mn[nh]) left = mx[mh++] + 1; else left = mn[nh++] + 1;
    }
    total += r - left + 1;
  }
  return total;
};
