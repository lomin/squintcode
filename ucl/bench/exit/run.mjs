// node run.mjs <variant> <workload>; prints one JSON line, µs.
// short  10^5 calls, each exits after a few steps (10^5 exits)
// long   one call, exits at the last of 10^6 elements (1 exit)
// none   one call over 10^6 elements, never exits (try present, no throw)
import * as k from './kernels.mjs';
const [,, variant, workload] = process.argv;
const f = k[variant];
const N = workload === 'short' ? 100000 : 1000000;
const a = new Array(N); let s = 12345;
for (let i = 0; i < N; i++) { s = (s * 1103515245 + 12345) & 0x7fffffff; a[i] = s % 1000000000; }
a[N - 1] = 1000000001;   // < 2^30: a V8 Smi, as LeetCode values are
const run = {
  short: () => { let r = 0; for (let i = 0; i < N; i++) r += f(a, i, a[i]); return r; },
  long:  () => f(a, 0, 1000000000),
  none:  () => f(a, 0, 1000000001),
}[workload];
let t = process.hrtime.bigint(); let r = run(); const cold = Number(process.hrtime.bigint() - t) / 1000;
const ws = []; for (let i = 0; i < 100; i++) { t = process.hrtime.bigint(); r = run(); ws.push(Number(process.hrtime.bigint() - t) / 1000); }
ws.sort((x, y) => x - y);
console.log(JSON.stringify({ variant, workload, cold: Math.round(cold), warm: Math.round(ws[50]), r }));
