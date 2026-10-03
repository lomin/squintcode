// Why a closure that assigns captured variables is slow on V8 (README §9.10, H58):
// the probe's profitEach hand-varied one difference at a time. n = 10^5.
//   node closure-vars.mjs <squint|noReturn|varDecl|ternary|cellObj>
// squint    profitEach as Squint emits it: captured `let`s, Math.max
// noReturn  the same, the closure returning nothing
// varDecl   the same with `var` (no TDZ checks)
// ternary   `let`s, a ternary for Math.max (what profitEach2 emits)
// cellObj   the variables in one object, Math.max
const n = 100000, xs = Array.from({ length: n }, (_, i) => (i * 7919) % 10007);
function each(f, xs) { const n = xs.length; let i = 0; while (true) { if (i < n) { f(xs[i]); i = i + 1; continue; } else return null; } }
const V = {
  squint: (p) => { let lo = 2147483647; let best = 0; each(function (x) { lo = lo < x ? lo : x; return best = Math.max(best, x - lo); }, p); return best; },
  noReturn: (p) => { let lo = 2147483647; let best = 0; each(function (x) { lo = lo < x ? lo : x; best = Math.max(best, x - lo); }, p); return best; },
  varDecl: (p) => { var lo = 2147483647; var best = 0; each(function (x) { lo = lo < x ? lo : x; return best = Math.max(best, x - lo); }, p); return best; },
  ternary: (p) => { let lo = 2147483647; let best = 0; each(function (x) { lo = lo < x ? lo : x; const d = x - lo; return best = best > d ? best : d; }, p); return best; },
  cellObj: (p) => { const c = { lo: 2147483647, best: 0 }; each(function (x) { c.lo = c.lo < x ? c.lo : x; return c.best = Math.max(c.best, x - c.lo); }, p); return c.best; },
};
const run = V[process.argv[2]];
let r; for (let i = 0; i < 20; i++) r = run(xs);
const ws = []; for (let i = 0; i < 200; i++) { const t = process.hrtime.bigint(); r = run(xs); ws.push(Number(process.hrtime.bigint() - t) / 1000); }
ws.sort((a, b) => a - b); console.log(process.argv[2].padEnd(9), Math.round(ws[100]), r);
