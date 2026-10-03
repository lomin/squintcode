// Runtime higher-order functions on V8 (out/probe_hof.js, a safety-0 Squint build).
//   node run.mjs <fn> [solo|poly2|mega]
// solo   only <fn> runs in this process (LeetCode: one solution per process)
// poly2  fold/each first warmed with 2 different closures, then <fn> timed
// mega   fold/each first warmed with 5 different closures, then <fn> timed
import { readFileSync } from 'node:fs';
const [,, fn, mode = 'solo'] = process.argv;
const src = readFileSync(new URL('./probe_hof.js', import.meta.url), 'utf8');
const m = new Function(src + '\nreturn {fold, each, sumLoop, sumFold, countLoop, countFold, profitLoop, profitEach, profitEach2};')();
const n = 100000;
const xs = Array.from({ length: n }, (_, i) => (i * 7919) % 10007);
const warmers = [
  (a, x) => a + x, (a, x) => a ^ x, (a, x) => (a > x ? a : x), (a, x) => a + (x & 1), (a, x) => a - x,
];
const k = mode === 'poly2' ? 2 : mode === 'mega' ? 5 : 0;
for (let r = 0; r < 200; r++) for (let w = 0; w < k; w++) { m.fold(warmers[w], 0, xs); m.each((x) => warmers[w](0, x), xs); }
const run = { sumLoop: () => m.sumLoop(xs), sumFold: () => m.sumFold(xs),
  countLoop: () => m.countLoop(xs, 5000), countFold: () => m.countFold(xs, 5000),
  profitLoop: () => m.profitLoop(xs), profitEach: () => m.profitEach(xs), profitEach2: () => m.profitEach2(xs) }[fn];
let t = process.hrtime.bigint(); let r = run(); const cold = Number(process.hrtime.bigint() - t) / 1000;
const ws = []; for (let i = 0; i < 200; i++) { t = process.hrtime.bigint(); r = run(); ws.push(Number(process.hrtime.bigint() - t) / 1000); }
ws.sort((a, b) => a - b);
console.log(JSON.stringify({ fn, mode, cold: Math.round(cold), warm: Math.round(ws[100]), r }));
