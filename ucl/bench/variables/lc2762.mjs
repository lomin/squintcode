// LeetCode 2762 builds compared (§9.8): node ucl/bench/variables/lc2762.mjs <file.js>
// Each file defines continuousSubarrays as a LeetCode submission does.
import fs from 'fs';
const [file] = process.argv.slice(2);
const f = new Function(fs.readFileSync(file, 'utf8') + '; return continuousSubarrays;')();
let seed = 42; const rnd = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
const n = 100000;
const cases = {
  random_1e9: Array.from({ length: n }, () => 1 + Math.floor(rnd() * 1e9)),
  walk: (() => { let v = 5e8; return Array.from({ length: n }, () => (v += Math.floor(rnd() * 5) - 2)); })(),
  sorted: Array.from({ length: n }, (_, i) => Math.floor(i / 40)),
  small_vals: Array.from({ length: n }, () => 1 + Math.floor(rnd() * 4)),
};
const out = [];
const t0 = performance.now(); f(cases.walk); const cold = performance.now() - t0;
for (const [c, a] of Object.entries(cases)) {
  for (let w = 0; w < 200; w++) f(a);
  const t = performance.now(); for (let i = 0; i < 300; i++) f(a);
  out.push(`${c} ${((performance.now() - t) / 300 * 1000).toFixed(0)}`);
}
console.log(out.join('  '), ' cold', (cold * 1000).toFixed(0));
