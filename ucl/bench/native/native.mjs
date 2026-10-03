// Native bulk operations against the element loop ucl would expand to (README D45).
//   node native.mjs <variant> <n>
// Each variant runs alone in its process; for small n a batch of calls is timed.
const [,, V, N_] = process.argv;
const n = Number(N_);
const reps = n <= 1000 ? 2000 : 1;          // calls per timed sample
const rnd = (i) => ((i * 7919 + 13) % 100003) - 50000;
const srcI = new Int32Array(n); for (let i = 0; i < n; i++) srcI[i] = rnd(i);
const srcA = Array.from(srcI);
const dstI = new Int32Array(n), dstA = new Array(n).fill(0);
const work = new Int32Array(n), workA = new Array(n).fill(0), tmp = new Int32Array(n);

function quicksort(a, lo, hi) {             // what an inline expansion would emit: `<` inlined
  while (hi - lo > 16) {
    const m = (lo + hi) >>> 1;
    let x = a[lo], y = a[m], z = a[hi];
    const p = x < y ? (y < z ? y : (x < z ? z : x)) : (x < z ? x : (y < z ? z : y));
    let i = lo, j = hi;
    while (i <= j) {
      while (a[i] < p) i++;
      while (p < a[j]) j--;
      if (i <= j) { const t = a[i]; a[i] = a[j]; a[j] = t; i++; j--; }
    }
    if (j - lo < hi - i) { quicksort(a, lo, j); lo = i; } else { quicksort(a, i, hi); hi = j; }
  }
  for (let i = lo + 1; i <= hi; i++) { const v = a[i]; let j = i - 1; while (j >= lo && v < a[j]) { a[j + 1] = a[j]; j--; } a[j + 1] = v; }
}
function mergesort(a, t, n) {               // stable-sort's inline expansion: bottom-up merge
  for (let w = 1; w < n; w *= 2) {
    for (let lo = 0; lo < n; lo += 2 * w) {
      const mid = Math.min(lo + w, n), hi = Math.min(lo + 2 * w, n);
      let i = lo, j = mid, k = lo;
      while (i < mid && j < hi) t[k++] = a[j] < a[i] ? a[j++] : a[i++];
      while (i < mid) t[k++] = a[i++];
      while (j < hi) t[k++] = a[j++];
    }
    for (let k = 0; k < n; k++) a[k] = t[k];
  }
}
const s = n >> 2, e = n - (n >> 2);         // a :start/:end range
const variants = {
  'fill-loop-i32':   () => { for (let i = 0; i < n; i++) dstI[i] = 7; return dstI[n - 1]; },
  'fill-native-i32': () => { dstI.fill(7); return dstI[n - 1]; },
  'fill-loop-arr':   () => { for (let i = 0; i < n; i++) dstA[i] = 7; return dstA[n - 1]; },
  'fill-native-arr': () => { dstA.fill(7); return dstA[n - 1]; },
  'replace-loop-i32':   () => { for (let i = s; i < e; i++) dstI[i] = srcI[i]; return dstI[s]; },
  'replace-native-i32': () => { dstI.set(srcI.subarray(s, e), s); return dstI[s]; },
  'replace-loop-arr':   () => { for (let i = s; i < e; i++) dstA[i] = srcA[i]; return dstA[s]; },
  'subseq-loop-i32':   () => { const r = new Int32Array(e - s); for (let i = s; i < e; i++) r[i - s] = srcI[i]; return r[0]; },
  'subseq-native-i32': () => srcI.slice(s, e)[0],
  'subseq-loop-arr':   () => { const r = new Array(e - s); for (let i = s; i < e; i++) r[i - s] = srcA[i]; return r[0]; },
  'subseq-native-arr': () => srcA.slice(s, e)[0],
  'sort-native-i32':     () => { work.set(srcI); work.sort(); return work[0]; },
  'sort-native-cmp-i32': () => { work.set(srcI); work.sort((a, b) => a - b); return work[0]; },
  'sort-inline-i32':     () => { work.set(srcI); quicksort(work, 0, n - 1); return work[0]; },
  'stable-inline-i32':   () => { work.set(srcI); mergesort(work, tmp, n); return work[0]; },
  'sort-native-cmp-arr': () => { for (let i = 0; i < n; i++) workA[i] = srcA[i]; workA.sort((a, b) => a - b); return workA[0]; },
  'sort-inline-arr':     () => { for (let i = 0; i < n; i++) workA[i] = srcA[i]; quicksort(workA, 0, n - 1); return workA[0]; },
  'copy-only-i32':       () => { work.set(srcI); return work[0]; },
};
const f = variants[V]; if (!f) throw new Error('unknown variant ' + V);
const sample = () => { const t = process.hrtime.bigint(); let r; for (let k = 0; k < reps; k++) r = f(); return [Number(process.hrtime.bigint() - t) / 1000 / reps, r]; };
const [cold, r0] = sample();
for (let i = 0; i < 20; i++) sample();
const ws = []; let r; for (let i = 0; i < 100; i++) { const [t, x] = sample(); ws.push(t); r = x; }
ws.sort((a, b) => a - b);
console.log(JSON.stringify({ v: V, n, cold: +cold.toFixed(3), warm: +ws[50].toFixed(3), r }));
