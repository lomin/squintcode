// An IIFE around a whole inner loop on V8 (README §9.11, D51): what a sequence
// function in expression position costs on Squint. An outer loop over r counts
// the elements > k in xs[r..r+W) -- an inline count-if -- as a statement (stmt),
// as Squint's IIFE (iife), and both again with the inner loop reading outer
// assigned lets, which the IIFE then captures (stmt-lo, iife-cap).
// usage: node bench.mjs <case> <W>     case in stmt | iife | iife-cap | stmt-lo
const CASE = process.argv[2];
const W = Number(process.argv[3]);
const N = 100000;
const xs = new Array(N);
for (let i = 0; i < N; i++) xs[i] = (i * 7919) % 10007;

function stmt(xs, k) {
  const R = xs.length - W;
  let total = 0;
  let r = 0;
  while (r < R) {
    let c = 0;
    let j = r;
    while (j < r + W) { if (xs[j] > k) c = c + 1; j = j + 1; }
    total = total + c;
    r = r + 1;
  }
  return total;
}

function iife(xs, k) {
  const R = xs.length - W;
  let total = 0;
  let r = 0;
  while (r < R) {
    total = total + (() => {
      let c = 0;
      let j = r;
      while (true) {
        if (j < r + W) {
          if (xs[j] > k) c = c + 1;
          j = j + 1;
          continue;
        } else {
          return c;
        }
      }
    })();
    r = r + 1;
  }
  return total;
}

// IIFE reads outer assigned lets `total` and `lo` -> they become context-allocated
function iifeCap(xs, k) {
  const R = xs.length - W;
  let total = 0;
  let lo = 1 << 30;
  let r = 0;
  while (r < R) {
    const x = xs[r];
    lo = lo < x ? lo : x;
    total = total + (() => {
      let c = total & 0;
      let j = r;
      while (true) {
        if (j < r + W) {
          if (xs[j] > k + (lo & 0)) c = c + 1;
          j = j + 1;
          continue;
        } else {
          return c;
        }
      }
    })();
    r = r + 1;
  }
  return total + lo;
}

function stmtLo(xs, k) {
  const R = xs.length - W;
  let total = 0;
  let lo = 1 << 30;
  let r = 0;
  while (r < R) {
    const x = xs[r];
    lo = lo < x ? lo : x;
    let c = total & 0;
    let j = r;
    while (j < r + W) { if (xs[j] > k + (lo & 0)) c = c + 1; j = j + 1; }
    total = total + c;
    r = r + 1;
  }
  return total + lo;
}

const fns = { stmt, iife, "iife-cap": iifeCap, "stmt-lo": stmtLo };
const f = fns[CASE];
if (!f) throw new Error("unknown case " + CASE);
const K = 5000;

let sink = 0;
for (let i = 0; i < 20; i++) sink += f(xs, K);
const times = [];
let result;
for (let i = 0; i < 100; i++) {
  const t0 = process.hrtime.bigint();
  result = f(xs, K);
  const t1 = process.hrtime.bigint();
  times.push(Number(t1 - t0) / 1000);
  sink += result;
}
times.sort((a, b) => a - b);
console.log(JSON.stringify({ case: CASE, W, median: times[50], result, sink: sink % 7 }));
