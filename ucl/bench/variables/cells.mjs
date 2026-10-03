// How a JS variable could be stored, on a 200k-element running sum (§9.8).
//   node ucl/bench/variables/cells.mjs
import * as sc from '../../../node_modules/squint-cljs/core.js';
const a = Int32Array.from({ length: 200000 }, (_, i) => i % 1000);
function Cell(v) { this.v = v; }   // what a ClojureScript deftype with a ^:mutable field compiles to
const fns = {
  let_mutable(a) { let s = 0; for (let i = 0; i < a.length; i++) s = s + a[i]; return s; },
  deftype_cell(a) { const s = new Cell(0); for (let i = 0; i < a.length; i++) s.v = s.v + a[i]; return s.v; },
  array_1(a) { const s = [0]; for (let i = 0; i < a.length; i++) s[0] = s[0] + a[i]; return s[0]; },
  squint_volatile(a) { const s = sc.volatile_BANG_(0); for (let i = 0; i < a.length; i++) sc.vreset_BANG_(s, sc.deref(s) + a[i]); return sc.deref(s); },
};
for (const [k, f] of Object.entries(fns)) {
  for (let w = 0; w < 300; w++) f(a);
  const t = performance.now(); for (let i = 0; i < 300; i++) f(a);
  console.log(k, ((performance.now() - t) / 300 * 1000).toFixed(0) + ' us');
}
