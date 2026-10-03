// Early exit from a loop, as ucl/loop could compile `(return i)` on JS
// (Squint and ClojureScript both emit plain try/throw; V8 runs both).
// find(a, s, x): the first index >= s whose element exceeds x, else -1.

// pos: the exit rewritten statically -- a plain return (Q3 option a)
export function pos(a, s, x) {
  const n = a.length; let i = s;
  while (i < n) { if (a[i] > x) return i; i++; }
  return -1;
}

// pre: throw one preallocated sentinel carrying the value (no stack)
const EXIT = { v: 0 };
export function pre(a, s, x) {
  try {
    const n = a.length; let i = s;
    while (i < n) { if (a[i] > x) { EXIT.v = i; throw EXIT; } i++; }
    return -1;
  } catch (e) { if (e === EXIT) return e.v; throw e; }
}

// fresh: throw a fresh plain object per exit (no stack)
const TAG = {};
export function fresh(a, s, x) {
  try {
    const n = a.length; let i = s;
    while (i < n) { if (a[i] > x) throw { tag: TAG, v: i }; i++; }
    return -1;
  } catch (e) { if (e.tag === TAG) return e.v; throw e; }
}

// error: throw a fresh Error per exit (captures a stack trace)
class Exit extends Error { constructor(v) { super(); this.v = v; } }
export function error(a, s, x) {
  try {
    const n = a.length; let i = s;
    while (i < n) { if (a[i] > x) throw new Exit(i); i++; }
    return -1;
  } catch (e) { if (e instanceof Exit) return e.v; throw e; }
}

// split: the loop has no try; a wrapper function catches (the preallocated sentinel)
function splitLoop(a, s, x) {
  const n = a.length; let i = s;
  while (i < n) { if (a[i] > x) { EXIT.v = i; throw EXIT; } i++; }
  return -1;
}
export function split(a, s, x) {
  try { return splitLoop(a, s, x); } catch (e) { if (e === EXIT) return e.v; throw e; }
}
