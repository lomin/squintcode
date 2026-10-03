// LeetCode-like mix (§9.8): 60 arrays, mostly random values up to 1e9.
//   node ucl/bench/variables/lc2762-mix.mjs <file.js>
import fs from 'fs';
const [file] = process.argv.slice(2);
const f = new Function(fs.readFileSync(file, 'utf8') + '; return continuousSubarrays;')();
let seed = 7; const rnd = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
// LeetCode-like: many arrays, sizes 1..1e5, values 1..1e9 mostly random, some in small ranges
const tests = [];
for (let t = 0; t < 60; t++) {
  const n = t % 6 === 0 ? 100000 : 1 + Math.floor(rnd() * 20000);
  const range = [1e9, 1e9, 1e9, 5, 3, 1000][t % 6];
  tests.push(Array.from({ length: n }, () => 1 + Math.floor(rnd() * range)));
}
const t0 = performance.now(); let s = 0; for (const a of tests) s += f(a); const first = performance.now() - t0;
const t1 = performance.now(); for (let k = 0; k < 20; k++) for (const a of tests) s += f(a); const warm = (performance.now() - t1) / 20;
console.log(`first-pass ${first.toFixed(2)} ms  warm ${warm.toFixed(2)} ms`);
