// Each (kernel, kind) gets its OWN compiled function, so every call site is
// monomorphic -- as in a real single-solution submission.
const KINDS = {
  Int32Array:   'new Int32Array(N)',
  Float64Array: 'new Float64Array(N)',
  'Array.fill': 'new Array(N).fill(0)',            // HOLEY_SMI: what make-arr emits today
  'Array.push': '(()=>{const a=[];for(let i=0;i<N;i++)a.push(0);return a})()', // PACKED
  Uint32Array:  'new Uint32Array(N)',
};
const MOD = 1000000007;
const KERNELS = {
  // LC 303/560/1480-style prefix sums. nums[i] <= 1e4, n = 1e5: max 1e9 fits int32.
  'prefix-sum small (1e5 x 1e4)': {n: 1e5, body: (A) => `
      const p = ${A.replace(/N/g,'(N+1)')};
      for (let i = 0; i < N; i++) p[i+1] = p[i] + nums[i];
      return p[N];`, input: n => Array.from({length:n}, (_, i) => (i*7919) % 10001)},
  // LC 2104/2281/2389-style: nums[i] <= 1e9, n = 1e5: sum up to 1e14 -- NOT int32.
  'prefix-sum large (1e5 x 1e9)': {n: 1e5, body: (A) => `
      const p = ${A.replace(/N/g,'(N+1)')};
      for (let i = 0; i < N; i++) p[i+1] = p[i] + nums[i];
      return p[N];`, input: n => Array.from({length:n}, (_, i) => 1e9 - (i % 1000))},
  // LC 70/91/509-style counting DP modulo 1e9+7, n = 1e6.
  'dp mod 1e9+7 (1e6)': {n: 1e6, body: (A) => `
      const dp = ${A};
      dp[0] = 1; dp[1] = 1;
      for (let i = 2; i < N; i++) dp[i] = (dp[i-1] + dp[i-2]) % ${MOD};
      return dp[N-1];`, input: () => null},
  // LC 930/1/347-style frequency counting: 1e6 increments into 1e5 buckets.
  'frequency count (1e6 -> 1e5)': {n: 1e5, body: (A) => `
      const c = ${A};
      for (let i = 0; i < nums.length; i++) c[nums[i]]++;
      return c[nums[0]];`, input: () => Array.from({length:1e6}, (_, i) => (i*7919) % 100000)},
  // LC 1143/72-style 2D DP, flattened, 2000 x 2000.
  'LCS 2D dp (2000x2000)': {n: 2001*2001, body: (A) => `
      const W = 2001, dp = ${A};
      for (let i = 1; i < W; i++) for (let j = 1; j < W; j++)
        dp[i*W+j] = nums[i-1] === nums[2000+j-1] ? dp[(i-1)*W+j-1] + 1
                  : Math.max(dp[(i-1)*W+j], dp[i*W+j-1]);
      return dp[W*W-1];`, input: () => Array.from({length:4000}, (_, i) => (i*31) % 26)},
  // LC 204-style sieve, n = 5e6.
  'sieve (5e6)': {n: 5e6, body: (A) => `
      const s = ${A}; let c = 0;
      for (let i = 2; i < N; i++) if (s[i] === 0) { c++; for (let j = i*i; j < N; j += i) s[j] = 1; }
      return c;`, input: () => null},
};

const RUNS = 15;
const results = {};
for (const [kname, k] of Object.entries(KERNELS)) {
  const nums = k.input(k.n);
  const fns = {};
  for (const [kind, alloc] of Object.entries(KINDS))
    fns[kind] = new Function('N', 'nums', k.body(alloc));
  const times = Object.fromEntries(Object.keys(KINDS).map(x => [x, []]));
  const answers = {};
  for (let r = 0; r < RUNS; r++)
    for (const kind of Object.keys(KINDS)) {     // interleaved, so drift hits all kinds
      const t = process.hrtime.bigint();
      answers[kind] = fns[kind](k.n, nums);
      times[kind].push(Number(process.hrtime.bigint() - t) / 1e6);
    }
  const median = a => a.sort((x, y) => x - y)[a.length >> 1];
  results[kname] = Object.fromEntries(Object.keys(KINDS).map(x => [x, {ms: median(times[x]), ans: answers[x]}]));
}
const kinds = Object.keys(KINDS);
console.log('node', process.version, '| median of', RUNS, 'interleaved runs, ms (answer)');
console.log('kernel'.padEnd(32) + kinds.map(k => k.padStart(22)).join(''));
for (const [kname, r] of Object.entries(results))
  console.log(kname.padEnd(32) + kinds.map(k => `${r[k].ms.toFixed(2)} (${r[k].ans})`.padStart(22)).join(''));
