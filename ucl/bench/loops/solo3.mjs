// One (case, variant) per fresh process -- as LeetCode runs one submission.
const [caseName, variant] = process.argv.slice(2);
const O = 'aloop', R = 'recur';
const inputs = {
  n412:   1e4,
  nums303: Array.from({length: 1e4}, (_, i) => ((i * 7919) % 200001) - 100000),
  prices: Array.from({length: 1e5}, (_, i) => (i * 7919) % 10001),
  nums560: Array.from({length: 2e4}, (_, i) => ((i * 7919) % 2001) - 1000),
  bits:   Array.from({length: 3e4}, (_, i) => ((i * 7919) % 7) < 3 ? 1 : 0),
};
const cases = {
  '412 fizzBuzz (forv)':          {[O]: ['./out/squintcode/fizzbuzz.mjs', 'fizzBuzz'],  [R]: ['./out/squintcode/faithful_recur.mjs', 'fizzBuzz'],  args: [inputs.n412]},
  '412 fizzBuzz2 (aloop range)':  {[O]: ['./out/squintcode/fizzbuzz.mjs', 'fizzBuzz2'], [R]: ['./out/squintcode/faithful_recur.mjs', 'fizzBuzz2'], args: [inputs.n412]},
  '303 prefix sum (aloop+with)':  {[O]: ['./out/squintcode/originals303.mjs', 'build_prefix_sum'], [R]: ['./out/squintcode/faithful_recur.mjs', 'build_prefix_sum'], args: [inputs.nums303]},
  '121 maxProfit':                {[O]: ['./out/squintcode/maxprofit.mjs', 'maxProfit'], [R]: ['./out/squintcode/faithful_recur.mjs', 'maxProfit'], args: [inputs.prices]},
  '560 subarraySum (hash map)':   {[O]: ['./out/squintcode/lc_560_subarray_sum_equals_k.mjs', 'subarraySum'], [R]: ['./out/squintcode/faithful_recur.mjs', 'subarraySum'], args: [inputs.nums560, 7]},
  '930 numSubarraysWithSum':      {[O]: ['./out/squintcode/lc_930_binary_subarrays_with_sum.mjs', 'numSubarraysWithSum'], [R]: ['./out/squintcode/faithful_recur.mjs', 'numSubarraysWithSum'], args: [inputs.bits, 2]},
};
if (caseName === '--list') { console.log(Object.keys(cases).join('\n')); process.exit(0); }
const c = cases[caseName];
const [mod, fn] = c[variant];
const f = (await import(mod))[fn];
const call = () => f(...c.args);
let t = process.hrtime.bigint(); const ans = call(); const cold = Number(process.hrtime.bigint() - t) / 1e3;
for (let i = 0; i < 300; i++) call();
const ts = [];
for (let r = 0; r < 31; r++) { t = process.hrtime.bigint(); for (let k = 0; k < 10; k++) call(); ts.push(Number(process.hrtime.bigint() - t) / 10e3); }
ts.sort((a, b) => a - b);
const digest = typeof ans === 'object' ? JSON.stringify(Array.from(ans)).length + ':' + JSON.stringify(Array.from(ans)).slice(0, 40) : String(ans);
console.log(JSON.stringify({cold, warm: ts[15], digest}));
