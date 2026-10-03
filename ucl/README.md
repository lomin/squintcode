# `ucl` — Uncommon Lisp

**Status:** v1 implemented (2026-10-03). Design agreed in a grilling session,
validated on a fourth host, ClojureDart, by a spike (§14), then built: the
library, its suite on three hosts, every solution ported, `macros.cljc` and the
predecessor `setf/` deleted (D27). Implementation-time decisions are I1–I9
(§11); what implementing taught is H27–H33 (§10) and §15.

```bash
ucl/run-tests.sh           # the ucl suite on Clojure, ClojureScript, Squint
bb test                    # that, then every solution on all three hosts
bb build                   # LeetCode submissions: Squint, safety 0
```

This document is the design of record. It says what `ucl` is, the rules every
decision is measured against, each decision and what decided it, the
measurements behind them, what was rejected, and what was got wrong on the way.
Decisions are numbered `D1`–`D28` after the grilling question that settled them;
a gap in the numbering is a question that was folded into another.

---

## 1. What it is

`ucl` lets you write **high-performance code once and run it unchanged on every
host that implements its contract** — today Clojure (JVM), ClojureScript and
Squint. Its vocabulary is borrowed from Common Lisp, because Common Lisp already
succeeded at being a standard, its names mostly do not collide with Clojure's,
and a Common Lisp programmer should find it obvious.

It replaces `src/squintcode/macros.cljc`, which does the same job today and
proves the performance claim — Squint-compiled submissions land in the fastest
1–5% on LeetCode — but is not open to new hosts: every macro branches on the
platform internally.

```clojure
(ns squintcode.lc-930-binary-subarrays-with-sum
  (:require [ucl.api :as ucl]))

(ucl/defun numSubarraysWithSum (nums goal)
  (declare (type simple-vector nums))
  (let [n    (ucl/length nums)
        freq (ucl/make-array (inc n) :element-type 'fixnum)]
    (ucl/setf (ucl/elt freq 0) 1)
    (loop [i 0 running-sum 0 result 0]
      (if (< i n)
        (let [running-sum (+ running-sum (ucl/elt nums i))
              want        (- running-sum goal)
              result      (if (>= want 0) (+ result (ucl/elt freq want)) result)]
          (ucl/incf (ucl/elt freq running-sum))
          (recur (inc i) running-sum result))
        result))))
```

`bb build` turns that into this submission -- the whole file, nothing of `ucl`
left in it:

```js
var numSubarraysWithSum = function(nums, goal) {
  const n_1 = nums.length;
  const freq_2 = new Int32Array(n_1 + 1);
  freq_2[0] = 1;
  let i_3 = 0; let running_sum_4 = 0; let result_5 = 0;
  while (true) {
    if (i_3 < n_1) {
      const running_sum_6 = running_sum_4 + nums[i_3];
      const want_7 = running_sum_6 - goal;
      const result_8 = want_7 >= 0 ? result_5 + freq_2[want_7] : result_5;
      freq_2[running_sum_6] = freq_2[running_sum_6] + 1;
      ...
```

On the JVM the same source compiles to direct `int[]` access, with no
reflection.

## 2. The rules every decision is measured against

**Hard constraint.** Every host implements the **whole** contract — no partial
hosts — and the shared code **never branches on host**: no host list, no
`#?(:clj … :cljs …)` in `ucl.contract`.

**Heuristics, in priority order:**

1. **Performance.** Generated code must be as fast as hand-written host code.
   LeetCode on Squint is the proving ground.
2. **Shared logic.** Push as much logic as possible into `ucl.contract`. The
   split line is exactly where host-specific emission becomes unavoidable.
3. **Common Lisp idiom.** Names, argument order and semantics follow ANSI Common
   Lisp. **Alexandria counts as Common Lisp** (it is the quasi-standard library).
   Where the standard is silent, follow what **SBCL and ECL agree on**.

## 3. Architecture

```
ucl/
  shared/ucl/contract.cljc        the whole model, names no host; defapi, defruntime
  backends/jvm/ucl/api.clj        emit map, run-time half, (contract/defapi …)
  backends/js/ucl/js_emit.cljc    the JS family's emit map and run-time helpers (I1)
  backends/cljs/ucl/api.cljc      ClojureScript flavor + (contract/defapi …)   macro half
  backends/cljs/ucl/api.cljs                                                   run-time half
  backends/squint/ucl/api.cljc    Squint flavor + (contract/defapi …)          one file
  testkit/<host>/ucl/test.*       ucl.test: one test vocabulary on every host (I4)
  testkit/<host>/ucl/leetcode.*   strict ListNode / TreeNode fixtures (D19)
  test/ucl/*_test.cljc            the suite -- byte-identical on every host
  run-tests.sh                    run it on all three hosts; any failure fails
  bench/                          every measurement in this document
```

Source roots per host -- exactly one backend each:

| host | roots |
|---|---|
| Clojure/JVM | `shared` `backends/jvm` `testkit/jvm` |
| ClojureScript | `shared` `backends/js` `backends/cljs` `testkit/cljs` |
| Squint | `shared` `backends/js` `backends/squint` `testkit/squint` |
| Squint, submission | `shared` `backends/js` `backends/squint` -- never the test kit |

- **A backend is an emit map** (D26). `ucl.contract/defapi` generates every API
  name — arities, docstrings, macro-vs-function behaviour — from the shared
  definition, so a backend cannot forget a name. The Squint backend must contain
  the literal text `defmacro` (a comment suffices): Squint only loads a file for
  macros if that word appears in it (H6). A test guards the marker.
- **The generated macros call the contract through the alias `contract`**, which
  every backend must declare (`(:require [ucl.contract :as contract])`). That
  alias resolves where the backend's macros are compiled, whatever a host names
  its macro-time namespace — ClojureDart's is a shadow `<ns>$host` (H20).
- **Every contract fn a macro calls is tagged `^:macro-support`.** ClojureDart
  needs it to make the fn available at expansion time (H20); everywhere else it
  is inert metadata. It is host knowledge in shared code, but not a branch.
- **Backends exclude the `clojure.core` names they define**:
  `(:refer-clojure :exclude [make-array min max defstruct defmethod])`.
- **Macro-time code must not reach a submission.** Squint compiles the contract
  and the JS emitter to modules too, and the backend imports them; esbuild's tree
  shaking drops them only if every top-level `def` has a literal value. A table
  of quoted lists compiles to `list(..)` calls and is kept (H31) -- so such
  tables are functions. `bb build` fails if a submission contains any of it.
- **Backend selection is a source-root convention.** Every backend declares the
  same namespace, `ucl.api`; each target's build puts exactly one backend
  directory on its path. Two on one path: the first silently wins (H11). This is
  a build invariant nothing can enforce.
- **Clients always use the alias**, `(:require [ucl.api :as ucl])`, and write
  `ucl/elt`, `ucl/setf` — never refer names (D21, D25). Names that clash with
  `clojure.core` (`min`, `max`, `make-array`, design-problem method names) are
  safe only this way on Squint (H9). **That one plain require works on all four
  hosts with no reader conditional** (§14): ClojureScript via implicit macro
  loading — the runtime half `ucl/api.cljs` does `(:require-macros [ucl.api])`.
- **Squint: latest release only** (0.14.211 at the time of writing). 0.12.193 is
  unsupported; the current suite does not even load on it (H12).

## 4. Vocabulary (v1)

v1 contains **exactly what the existing solutions need** (D8). A capability no
solution uses costs every host and buys nothing; it is added when one does.
Common Lisp names in the chapters v1 touches are reserved for their Common Lisp
meaning.

| form | meaning | decision |
|---|---|---|
| `(ucl/make-array n :element-type t :initial-element x :initial-contents [...] :adjustable t :fill-pointer k)` | construct a vector | D7, D9, D10 |
| `(ucl/elt seq i)` | read element `i` — the only array accessor | D17 |
| `(ucl/length seq)` | length (respects the fill pointer) | D6 |
| `(ucl/vector-push-extend x v)` | append; `v` must be adjustable with a fill pointer | D6, D9 |
| `(ucl/make-hash-table :initial-contents {k v})` | construct a hash table | D22 |
| `(ucl/gethash key table [default])` | read; key first, as in Common Lisp | D2, D6 |
| `(ucl/slot-value obj 'slot)` | read a slot; the quoted name is consumed at compile time | D6 |
| `(ucl/setf place v)` `(ucl/incf place [d])` `(ucl/decf place [d])` | assign / read-modify-write any of the places above | D6 |
| `(ucl/min a b …)` `(ucl/max a b …)` | inline in call position; functions as values (`(reduce ucl/max …)`) | D21 |
| `ucl/most-positive-fixnum` `ucl/most-negative-fixnum` | ±2³¹ bounds (D10) | D23 |
| `ucl/double-float-positive-infinity` `…-negative-…` | the SBCL/ECL extension names | D23 |
| `ucl/defun` + `(declare (type …))` | function with type declarations | D18 |
| `ucl/defstruct` `ucl/defmethod` `ucl/with-slots` | classes | D18 |

**Loops are Clojure's own `loop`/`recur`** with `elt`/`length` (D20). They
compile to a plain `while` on every host and need no contract code. `aloop` and
`forv` are retired; a Common Lisp `LOOP` subset (`ucl/loop`) may be added later.

**Numeric equality is Clojure's `==`** (inline `===` on Squint). Squint compiles
`=` to a variadic deep-equality call even for two numbers (H13).

**Every form evaluates each runtime argument exactly once, left to right**, and
generated bindings are `gensym`s, so a caller's local is never captured. But a
`let` in expression position is an IIFE on Squint, and an IIFE in a hot loop
costs 8x (§9.6, H22). So nothing is bound that need not be:

- A backend declares, per operation, whether its host code already evaluates
  each argument once and in order (`:read-once?`, `:write-once?`). JS `a[i]`,
  `a[i] = v` and a helper call all do; the contract then passes the forms
  through unbound.
- Otherwise only *expressions* are bound; symbols and literals are used as is.
- `min`/`max` on JS: an inline ternary when every argument is a symbol or
  literal, `Math.min`/`Math.max` otherwise -- never a `let` (§9.6).

### Not in v1

Multiple values (`multiple-value-bind`), `mulmod`, hash-table iteration and
deletion, `ucl/loop`, `aref` (reserved), CLOS `defclass`, `:test 'equal`.

## 5. Arrays and types

### Representation (D9, D10)

| `make-array` call | JVM | JS (Squint, ClojureScript) |
|---|---|---|
| default, `:element-type t` | `Object[]` | `Array` |
| `:element-type 'fixnum` | `int[]` | `Int32Array` |
| `:element-type '(signed-byte 53)` | `long[]` | `Float64Array` |
| `:adjustable t :fill-pointer k` | `java.util.ArrayList` | `Array` |

This is Common Lisp's own split: a simple vector is fixed-size and may be
specialized; only an adjustable vector with a fill pointer accepts
`vector-push-extend`.

- **`fixnum` is signed 32-bit on every host** (`most-positive-fixnum` = 2³¹−1).
  Common Lisp leaves fixnum width to the implementation and makes storing an
  out-of-type value undefined, so this is legitimate; identical width keeps every
  host's results identical, and `Int32Array` is the fastest JS array (§9.1).
- **`(signed-byte 53)` exists for the problems whose answer is a Java `long`**
  (values to 10⁹, n to 10⁵, sums to 10¹⁴). `Float64Array` holds integers exactly
  to 2⁵³; the name states exactly where the hosts stop agreeing. An `Int32Array`
  would wrap such a sum silently (§9.1).

### What `elt` accepts (D16, D17)

Host arrays (whatever `make-array` returns on that host, and the native arrays
LeetCode passes) **and the host's literal vector**. Persistent collections are
otherwise not vectors in the contract.

`elt` is Common Lisp's *generic* sequence accessor, so it may dispatch — but the
dispatch disappears wherever it matters:

| host | declared type | undeclared |
|---|---|---|
| Squint | `x[i]` | `x[i]` — a vector literal *is* a JS array |
| JVM | direct `int[]` / `long[]` / `Object[]` access; `.get` on a known `List` or vector literal | `nth`, with a compile-time warning (D4) |
| ClojureScript | `aget` | a helper with one `vector?` branch |

"Declared" means anything the backend can learn at expansion time: a
`(declare (type …))`, a local the compiler typed (a `let` bound to
`ucl/make-array`), a `:tag` on the form, or -- on the JVM -- a nested macro form
whose expansion carries a `:tag` (H30). At safety ≥ 1 every JS access goes
through a checking helper instead (§8).

Data literals therefore work where they appear in practice: as
`:initial-contents [...]`, as the input of a converter the caller chooses
(`into-array`, `list->linked`), and passed straight to a function that only
reads with `elt`.

### Declaring types (D13, D15, D18)

```clojure
(ucl/defun build-prefix-sum (nums)
  (declare (type fixnum-vector nums))
  ...)
```

Types are exact Common Lisp type specifiers, plus three abbreviations the
contract ships, as Common Lisp's own `deftype` would define them:

| abbreviation | expands to |
|---|---|
| `fixnum-vector` | `(simple-array fixnum (*))` |
| `sb53-vector` | `(simple-array (signed-byte 53) (*))` |
| `simple-vector` | `(simple-array t (*))` — Common Lisp's own name |

plus `(or null X)` for a nullable reference. A declaration is a promise: JS
ignores it; the JVM uses it to emit direct access. On the JVM a nested macro
learns a local's declared class through `&env` (H1).

## 6. Hash tables and keys (D12, D22)

`(ucl/make-hash-table :initial-contents {0 1})` — Common Lisp's constructor,
extended with a keyword the way CLHS allows implementations to (SBCL adds
`:synchronized`, `:weakness`). Mirrors `make-array`'s `:initial-contents`.

The default test is `eql`, under which `:a` is `eql` to `:a` and integers of
equal value are `eql`. Two hosts break that, differently: ClojureScript keywords
are distinct objects (H14) and a `js/Map` compares by identity; on the JVM an
`int[]` read yields an `Integer`, which a `HashMap` of `Long` keys misses. So
**each host implements a key emitter**: literal keys are normalized at compile
time, non-literal keys get the cheapest runtime normalization the host needs —

| host | non-literal key |
|---|---|
| Squint | nothing (keywords are strings) |
| JVM | `(ucl.api/hash-key k)`: an `Integer` becomes a `long`; skipped for literals and `long`/`String` locals |
| ClojureScript | `(ucl.api/hash-key k)`: a keyword becomes its name; a literal keyword is its name at compile time |

Keys come back out normalized; that matters only once iteration exists.

On JS, `gethash` and its `setf` compile to two tiny helpers,
`get-or-default(k, m, d)` and `puthash(k, m, v)`: one function call each, no
`let`, so no IIFE in expression position; V8 inlines them (§9.6). The read is
exact Common Lisp: only a missing key (`undefined` from `Map.get`) yields the
default -- a key stored with nil is present. On the JVM, `.getOrDefault` has
the same semantics.

A missing key returns `default` (nil if omitted). `(incf (gethash k h))` on a
missing key adds 1 to NIL: SBCL and ECL signal a type-error at default safety,
so `ucl` does too at safety ≥ 1 (D2, D11). The idiom is `(ucl/incf (ucl/gethash k h 0))`.

## 7. Classes (D18, D19)

There are two kinds of classes in LeetCode, and they are handled differently.

**Your own classes** (`NumArray`, `LRUCache`, …) use `defstruct`. Its BOA
("By Order of Arguments", CLHS 3.4.6) constructor is exactly LeetCode's
positional constructor, and `&aux` computes slots from arguments:

```clojure
(ucl/defstruct (NumArray (:constructor NumArray
                           (nums &aux (prefix-sum (build-prefix-sum nums)))))
  (prefix-sum nil :type fixnum-vector))

(ucl/defmethod sumRange ((this NumArray) left right)
  (ucl/with-slots (prefix-sum) this
    (- (ucl/elt prefix-sum (inc right))
       (ucl/elt prefix-sum left))))
```

- **JS:** `defmethod` places the body once, on `NumArray.prototype.sumRange` —
  what LeetCode calls. The generic function `sumRange` is a thin wrapper that
  calls `this.sumRange(…)`, so a test calling `(sumRange obj 0 2)` exercises
  exactly LeetCode's path. `NumArray` is a plain constructor function, callable
  with and without `new`, both running the same body; it tests `this` with
  `NumArray.prototype.isPrototypeOf(this)`, not `instance?`, which Squint
  expands to an IIFE plus a truthiness call. An omitted `&optional` argument is
  `undefined` and selects the default, as in LeetCode's own classes.
- **JVM:** a mutable `deftype` field is private (H10), and a `deftype` cannot
  gain methods after it is defined. So `defstruct` emits a `deftype` that
  implements **one small interface per slot name** -- `ucl.slots.S_next` with
  `next()` and `set_next(v)` -- and `defmethod` attaches a protocol via
  `extend-type`. Because every struct with a `next` slot implements the same
  interface, `slot-value` needs no type information at all: no reflection, no
  fallback, no warning (I2). Measured method cost: ≈ 3.5 ns per call more than a
  method inside the `deftype` (§9.4) -- JVM only, a test host.
- **Call constructors as functions** -- `(NumArray nums)` -- in portable code.
  On the JVM, `new` is a special form that reaches the deftype's slot-positional
  constructor, which cannot run a BOA lambda list. `new` is how LeetCode calls,
  and that happens on JS only.
- `with-slots` resolves bare slot names to `slot-value` in shared code,
  respecting local shadowing.
- `defmethod` dispatches statically on the first argument's struct only — no
  multiple dispatch, no `&optional`/`&rest` in v1.
- **Method names often clash with `clojure.core`** (`get`, `next`, `pop`,
  `peek`, `remove`, `empty`). `defmethod` defines a function in your namespace,
  so such a name needs `(:refer-clojure :exclude [...])` — Common Lisp needs
  `shadow` for the same names.

**LeetCode's classes** (`ListNode`, `TreeNode`) are LeetCode's, and must be
constructed with `new`: on LeetCode they are ES5 constructor functions, and
calling one without `new` in a strict-mode module throws. So:

- solutions build them with `(new ListNode 0 head)` and name them bare, as
  LeetCode's globals, with no require;
- the **test kit ships strict fixtures**, resolved per host the way LeetCode's
  global is:
  - Squint: real JS classes, installed on `globalThis`; they throw without `new`;
  - ClojureScript: strict constructor functions; `ucl.api` declares `ListNode`
    and `TreeNode` as `cljs.core` names so the bare symbol resolves with no
    warning (H27);
  - JVM: `deftype*` classes in the **default package**, which every namespace
    resolves by bare name (H28), implementing the per-slot interfaces;
- a test namespace requires `ucl.leetcode` before the solution (the JVM must
  have the class before it compiles `new ListNode`);
- a solution that omits `new` fails the ordinary shared suite: it throws on JS
  and does not compile on the JVM;
- every constructor argument must be passed: the JVM class has only the
  all-slots constructor;
- fixtures are never part of a submission -- `bb build` checks.

## 8. Safety (D2, D4, D11, D14)

Generated code is **checked by default**, behaving like SBCL at its default
safety: per operation, `ucl` signals where SBCL signals (an out-of-bounds `elt`,
`incf` of NIL, a store outside a typed array's element type, a wrong
receiver). At **safety 0** those checks are omitted and behaviour is undefined,
exactly as `(declare (optimize (safety 0)))` in SBCL.

Safety is set **per build, through each host's own compiler options**; unset
means 1:

| host | setting |
|---|---|
| Squint | `{:ucl/safety 0}` in `squint.edn` — Squint copies every key into the macros' `&env` (H5) |
| ClojureScript | a compiler option, read via `cljs.env/*compiler*` |
| JVM | the system property `-Ducl.safety=0` |

What the checks are, per host:

| check (safety ≥ 1) | JS hosts | JVM |
|---|---|---|
| `elt` index in bounds | `elt-checked` / `elt-set-checked` helpers | the host's own (`ArrayIndexOutOfBounds`) |
| store into `fixnum` / `(signed-byte 53)` vector | the helper checks the value | `(int v)` range-checks; `unchecked-int` at 0 |
| `incf`/`decf` of a non-number | `check-number` | `check-number` |
| `vector-push-extend` on a non-adjustable vector | `push-checked` | the host's own |
| `:initial-contents` length ≠ dimension | `check-contents` (literal: at compile time) | same |

Submission builds (`bb build`, `bb build-one`) use 0; tests use the default.
`ucl/run-tests.sh` compiles the suite at safety 0 as well and fails if any
check survives.
ClojureScript caches compiled namespaces, and changing the setting does not
invalidate that cache — clean before switching (the `bb` tasks do).

**The JVM warns at compile time whenever it falls back** because a type is
unknown -- `elt`, `setf` of `elt`, `length` and `vector-push-extend` on a
receiver of unknown type compile to `nth` / a dispatching helper / `count`.
Nothing else would point at those paths. `slot-value` never falls back (I2).
In test code the warning is expected: a test's locals are rarely declared.

**ClojureScript warnings are build errors.** A macro that fails to resolve is
only a warning there, so a "green" build can contain broken run-time calls (H3).

## 9. Measurements

All with Node 24.16.0 (LeetCode runs Node 22.14.0 — same V8 generation), Squint
0.14.210, OpenJDK 21.0.11, Clojure 1.12.0. Indicative, not JMH. Every script is
in `bench/`.

### 9.1 Array kinds on LeetCode-shaped kernels (`bench/array-kinds.js`)

Median of 15 interleaved runs, ms. Each (kernel, kind) is its own compiled
function so every call site is monomorphic.

| kernel (typical problems) | `Int32Array` | `Float64Array` | `new Array(n).fill(0)` | `[]` + push |
|---|---|---|---|---|
| prefix sum, small values (303, 560, 1480) | **0.26** | 0.48 | 0.57 | 1.10 |
| prefix sum, values to 10⁹ (2104, 2281, 2389) | 0.24 ⚠ **wrong answer** | **0.45** | 1.12 | 1.30 |
| counting DP mod 10⁹+7 (70, 91, 509) | **5.9** | 14.8 | 9.4 | 16.5 |
| frequency count (1, 347, 930) | **1.7** | 1.8 | 2.0 | 2.6 |
| 2D DP 2000×2000 (72, 1143) | **11** | 11–17 | 23 | 47 |
| sieve 5·10⁶ (204) | **30** | 48 | 62 | 90 |

`Int32Array` is fastest or tied everywhere (→ `fixnum`, D10) and silently wraps
a 10¹⁴ sum (→ `(signed-byte 53)` as `Float64Array`, D10). `Uint32Array`, used by
`macros.cljc` for `'integer`, is as fast as `Int32Array` but wraps negatives.

### 9.2 Loop styles, all six loops in `src/` (`bench/loops/`)

Each variant alone in 7 fresh Node processes (one solution per process, as on
LeetCode), median. The `loop`/`recur` rewrite keeps the original body and helper
calls; all six give identical answers.

| problem / loop | `aloop`/`forv` warm µs | `loop`/`recur` warm µs | `aloop`/`forv` cold µs | `loop`/`recur` cold µs |
|---|---|---|---|---|
| 412 fizzBuzz (`forv`), n=10⁴ | 145 | **116** | 2513 | **1621** |
| 412 fizzBuzz2 (`aloop` over `range`) | 125 | **111** | 1817 | **1616** |
| 303 prefix sum (`aloop` + `with`), n=10⁴ | 37 | **33** | 833 | **520** |
| 121 maxProfit, n=10⁵ | 728 | **633** | 6441 | **5932** |
| 560 subarraySum (hash map), n=2·10⁴ | **622** | 643 | **2365** | 3006 |
| 930 numSubarraysWithSum, n=3·10⁴ | 115 | **102** | 1433 | **984** |

`aloop` adds, per iteration, the `it` ternary and a `squint_core.truth_` call;
`forv` adds `dotimes`' binding. That is the 12–26%. → D20.

### 9.3 `min`/`max` (`bench/loops/inline_variants.cljc`)

121 maxProfit, n=10⁵, each variant alone in 5 fresh processes:

| variant | warm µs | cold µs |
|---|---|---|
| `loop`/`recur`, `clojure.core` `min`/`max` | 1190–1290 | 5500–7400 |
| `aloop`, `clojure.core` `min`/`max` | 610–640 | 5500–6700 |
| `loop`/`recur`, inline comparison | **202–208** | **890–1350** |
| `aloop`, inline comparison | **204–212** | 1200–1350 |

Squint's `min`/`max` are variadic with NaN checks and a `for…of` loop
(`squint-cljs/core.js`); whether V8 inlines them depends on the call-site shape,
so *identical* code measured 470 µs or 1200 µs depending on what else had run.
With an inline comparison both loop styles are equal and 3× faster warm, 4–6×
cold. → D21.

### 9.4 JVM method dispatch (`bench/jvm-method-dispatch.clj`)

50 M calls, median of 5 rounds, trivial body:

| how the method is reached | ns/call |
|---|---|
| protocol fn, method inside `deftype` | 3.5 |
| protocol fn, method attached by `extend-type` (`ucl`'s JVM `defmethod`) | 7.0 |
| one call site seeing 3 objects of one type, inside `deftype` | 4.5 |
| one call site seeing 3 struct types via `extend-type` | 19.5 |

A LeetCode design problem makes ≤ 10⁵ method calls: ≤ 0.35 ms, JVM only. → D18.

### 9.5 Inherited from `setf` (`bench/curried-readers.clj`)

(The script needs the `setf/` tree, deleted in v1 -- run it from commit `09b676a`.)

A JVM field **write**: `(set! (.x ^Point p) v)` is a real `PUTFIELD` at 1.25×
generated Java; `Reflector/setInstanceField` ≈ 9×; an unhinted `set!` ≈ 12×.
A curried field reader on the JVM (reflective) ≈ 15–23× a direct read.

### 9.6 Expression-position costs on Squint (during implementation)

maxProfit-shaped loop, n=10⁵, 5 fresh processes each, µs:

| `min`/`max` compiled as | warm | cold |
|---|---|---|
| inline ternary `(p<lo)?p:lo` | ~150 | 700–990 |
| `Math.min` / `Math.max` | **~123** | 1100–1500 |
| ternary inside a `let` (an IIFE on Squint) | ~1100 | ~3200 |

subarraySum-shaped loop (LeetCode 560), n=2·10⁴, `gethash` with a default:

| compiled as | warm | cold |
|---|---|---|
| `m.get(k) ?? d` (not exact: a stored nil reads as the default) | 540–700 | 1340–1510 |
| helper `get_or_default(k, m, d)` (exact) | 640–750 | 1390–1820 |
| `let` + `undefined` check (an IIFE) | 800–870 | 2740–4000 |

The IIFE is what to avoid; the exact helper costs no more than the inexact
operator. → I3, I6.

### 9.7 The submissions, before and after (`bench/submissions/run.mjs`)

Every solution's LeetCode build under `macros.cljc` (Squint 0.12.193) against
its `ucl` build (Squint 0.14.211, safety 0). Each variant alone in 7 fresh Node
processes, loaded as a script as LeetCode loads it; median µs. Every pair
returns the same answer.

| problem, input | warm before → after | cold before → after |
|---|---|---|
| 412 fizzBuzz, n=10⁴ | 154 → **53** | 1899 → **1132** |
| 303 NumArray, n=10⁴ + 10⁴ queries | 72 → **45** | 1218 → **981** |
| 121 maxProfit, n=10⁵ | 675 → **122** | 4549 → **1631** |
| 560 subarraySum, n=2·10⁴ | 602 → 605 | 2356 → **1975** |
| 930 numSubarraysWithSum, n=3·10⁴ | 121 → **100** | 1133 → **914** |
| 19 removeNthFromEnd, 10⁵ nodes | 866 → 874 | 8273 → 7960 |

Faster or equal everywhere; the equal pairs are within noise. Bundles shrank
from 2.0–3.5 KB to 0.7–1.5 KB.

## 10. Host facts

Everything here was established by compiling and running, not by reading docs.

- **H1** — Macros see locals on every host, under different keys: the JVM in
  `&env` itself, ClojureScript in `(:locals &env)`, Squint in
  `(:var->ident &env)` — an internal, undocumented key; a `^ints` hint on a fn
  parameter is visible there as `{:tag ints}`. Each backend implements its own
  locals lookup.
- **H2** — On the JVM, a macro reads a local's inferred class via
  `Compiler$LocalBinding`, and a called fn's return-type hint via the resolved
  var. Clojure's own local type inference makes `(let [t p] (.x t))` direct when
  `p` is typed.
- **H3** — ClojureScript's macro-resolution failure is a WARNING, not an error:
  compilation continues and emits a broken run-time call.
- **H4** — Squint macros can read `js/process.env` at compile time.
- **H5** — Squint copies arbitrary `squint.edn` keys (e.g. `:ucl/safety`) into
  the macros' `&env`.
- **H6** — Squint loads a `.cljc` for macros only if its text contains
  `defmacro` (`lib/compiler.node.js`). A file whose macros are all generated by a
  macro from another namespace needs the word in a comment.
- **H7** — A shared macro that generates a backend's macros (`defapi`) works on
  all three hosts; on ClojureScript the contract namespace is then loaded only
  on the macro (JVM) side.
- **H8** — On Squint a name can be both a macro and a function **through an
  alias**: `(n/max a b)` inlines, `(reduce n/max 0 xs)` gets the function.
- **H9** — On Squint, referring a name that shadows `clojure.core`
  (`:refer-clojure :exclude [max]` + `:refer-macros [max]`) silently compiles a
  call to an undefined `max`. Non-clashing names do refer (setf's F9).
- **H10** — A mutable `deftype` field is private on the JVM ("No matching field
  found"); an immutable one is `public final`.
- **H11** — Two same-namespace backends on one classpath: the first path entry
  wins, silently.
- **H12** — Squint 0.12.193 cannot load the `setf` suite: quoted symbols compile
  to an undefined `cljs.core.symbol(…)`, and `:refer-macros` is ignored.
- **H13** — Squint compiles `+ - * / inc dec < == zero? pos? neg?` and bit ops
  inline; `=` (variadic deep equality), `min`, `max`, `quot`, `rem`, `mod`,
  `abs`, `not`, `odd?`, `even?` are runtime calls.
- **H14** — ClojureScript does not give keyword literals a stable identity:
  `(identical? :a :a)` is false, so a `js/Map` written with one occurrence cannot
  be read with another.
- **H15** — `*warn-on-reflection*` cannot see an explicit
  `clojure.lang.Reflector/…` call.
- **H16** — Tagged literals (`#ucl/vector [...]` via `data_readers.cljc`) work on
  the JVM and ClojureScript but not Squint ("No reader function for tag"). `#js`
  can be registered on the JVM but is an unqualified tag, reserved for Clojure.
  On Squint a plain `[1 2 3]` already is a JS array.
- **H17** — One `:require` entry per namespace: two make Squint emit a duplicate
  `import * as`, invalid ESM.
- **H18** — Squint must be handed every source file in one invocation, and
  writes only to the `:output-dir` in `squint.edn`.
- **H19** — Squint's `is` (from `macros.cljc`) is node's `assert.equal` (`==`),
  i.e. reference equality on arrays — why `ucl` ships its own Squint test macros
  that compare structurally (D5).

ClojureDart (`247b3c2`, Dart 3.13.5), found by the §14 spike:

- **H20** — ClojureDart evaluates macros on the JVM, in a shadow namespace
  `<ns>$host`. A fn a macro calls must be tagged `^:macro-support` (documented).
  A macro-generating macro whose output names a helper fully qualified
  (`ucl.contract/expand-read`, as syntax-quote produces) fails with
  `ClassNotFoundException: ucl.contract` — the host namespace is
  `ucl.contract$host`. Calling through the backend's alias works.
- **H21** — ClojureDart compiles code read with feature `:cljd` only; its macro
  host also reads `:clj`, so in a conditional `:clj` must come last.
- **H22** — Squint compiles a `let` in expression position to an IIFE,
  `(() => { … })()`. Everything a macro wraps in `let` pays for it there.
- **H23** — A ClojureDart namespace can only name Dart libraries it imports:
  an expansion containing `dart:typed_data/Int32List` fails in a client that did
  not import it ("Unknown symbol"). The backend imports the library and exposes
  small constructor fns instead — the same runtime-half pattern ClojureScript
  uses.
- **H24** — ClojureDart's `&env` holds locals directly as keys (like the JVM),
  plus `:closed-overs` and `:nses`.
- **H25** — ClojureDart keeps a top-level fn's parameters `dynamic` even with a
  hand-written `^int` hint, casting at the point of use. A `ucl/defun` with
  `(declare (type fixnum n))` therefore compiles to exactly what hand-written
  ClojureDart does.
- **H26** — Squint's macro interpreter fails on a quoted symbol inside `or`:
  `(or x 't)` → "Unable to resolve symbol: t"; `(if x x 't)` works. Shared
  contract code must avoid the pattern.

Found while implementing v1 (Squint 0.14.211, ClojureScript 1.12.42):

- **H27** — ClojureScript: a symbol added to `cljs.core`'s `:defs` in the
  compiler state -- from a macro namespace, when it loads -- resolves bare in
  every later namespace with no warning, compiling to `cljs.core.ListNode`. A
  macro can also read any custom compiler option:
  `(get-in @cljs.env/*compiler* [:options :ucl/safety])`.
- **H28** — Clojure: `(deftype* ListNode ListNode [..] ..)`, with an unqualified
  class name, defines a class in the default package, and `(new ListNode ..)`
  resolves it from any namespace, as a JS global would.
- **H29** — Squint compiles `defn` and `fn` to `function`, so `this-as` works,
  and a multi-arity dispatcher passes `this` on with `.call`. `instance?`
  expands to an IIFE plus `truth_`; a test tagged `^boolean` compiles to a bare
  `if`.
- **H30** — Clojure: a macro can `macroexpand` a nested form at expansion time
  and read the `:tag` its expansion carries; that is how `(ucl/length
  (ucl/make-array ..))` knows its class.
- **H31** — esbuild's tree shaking keeps a top-level `var` whose initializer is
  a call: a quoted list (`list(..)`) or a set literal (`new Set(..)`). Such a def
  in a module the backend imports rides along into every submission.
- **H32** — Squint auto-imports a namespace that a macro expansion names fully
  qualified (`ucl.api/elt-checked` → `import * as ucl_DOT_api`), whatever alias
  the client used.
- **H33** — Clojure's syntax-quote qualifies a symbol it cannot resolve with the
  current namespace: a JVM-side macro emitting `undefined?` (ClojureScript only)
  produces `ucl.js-emit/undefined?`. Emit host-only names unqualified (`~'undefined?`).

## 11. Decision log

"Evidence" means a measurement or probe decided it; "user judgement" means it
was decided on reasoning, with the trade-offs on the table.

| # | Decision | Decided by |
|---|---|---|
| D1 | A host-primitive contract, not just assignment; Common Lisp is the model | user judgement |
| D2 | Follow Common Lisp; where it is silent, SBCL+ECL agreement (e.g. `incf` of NIL signals) | user judgement |
| D4 | The JVM warns at compile time on every fallback; no knob | user judgement |
| D5 | `ucl` ships its own Squint test macros; JVM/CLJS keep `clojure.test` | user judgement |
| D6 | Copy Common Lisp exactly — names, argument order, optional args, multiple values (when added) | user judgement |
| D7 | The constructor is `make-array` (there is no `make-vector` in ANSI CL) | user judgement |
| D8 | v1 scope = what the existing solutions need | user judgement |
| D9 | JVM: simple vector → primitive array; adjustable + fill pointer → `ArrayList` | user judgement |
| D10 | `fixnum` = 32-bit everywhere; `(signed-byte 53)` = `long[]`/`Float64Array` | evidence (§9.1) |
| D11 | Checked by default, unchecked at safety 0 — SBCL's model | user judgement |
| D12 | Per-host key emitter; literal keys normalized at compile time | evidence (H14) |
| D13 | Types via declarations; safety per build, not per function | user judgement |
| D14 | Safety through each host's own compiler options; unset = 1 | evidence (H5) |
| D15 | Common Lisp type specifiers + `fixnum-vector`/`sb53-vector`/`simple-vector` | user judgement |
| D16 | Array operations take host arrays, not persistent collections | user judgement |
| D17 | `elt` is the only accessor; accepts the host's literal vector; `aref` reserved | user judgement |
| D18 | `defstruct` (BOA + `&aux`) + `defmethod` + `with-slots` + `defun`/`declare` | user judgement, evidence (§9.4) |
| D19 | LeetCode classes via `new`; strict fixtures in the test kit | user judgement |
| D20 | Loops are Clojure `loop`/`recur`; `aloop`/`forv` retired | evidence (§9.2) |
| D21 | Inline `min`/`max`; `==` for numbers; clients always use the alias | evidence (§9.3, H8, H9) |
| D22 | `make-hash-table :initial-contents {…}` | user judgement |
| D23 | `most-positive-fixnum` etc. + SBCL/ECL infinity names | user judgement |
| D24 | Name `ucl` (Uncommon Lisp); namespaces `ucl.contract` / `ucl.api` | user judgement |
| D25 | Alias `ucl` | user judgement |
| D26 | `defapi` generates every backend's API surface | evidence (H6, H7) |
| D27 | Big-bang migration from `macros.cljc` | user judgement |
| D28 | This README is the design of record; benchmarks are committed | user judgement |

### Implementation decisions (I1–I9)

Made while building v1, not in the grilling session; each is reversible.

| # | Decision | Why |
|---|---|---|
| I1 | One JS emit map, `backends/js/ucl/js_emit.cljc`, shared by Squint and ClojureScript through a small flavor map | the two maps were identical but for array literals, locals, keys, arity checks; the contract may not name a host family (§13 item) |
| I2 | JVM structs implement one interface per slot name | `slot-value` on any struct, typed or not, with no reflection; D4's `slot-value` fallback disappears |
| I3 | JS `gethash` and its `setf` are helper calls, `get-or-default` / `puthash` | exact CL semantics at the cost of the inexact `??` (§9.6), and no IIFE |
| I4 | `ucl.test` (`deftest is testing signals-error?`) on every host; on JVM/CLJS it delegates to clojure.test | test files carry no reader conditional -- D5 kept clojure.test, this only names it once |
| I5 | LeetCode's globals resolve per host: Squint `globalThis`, ClojureScript `cljs.core` names, JVM default-package classes | D19 says `(new ListNode ..)` bare; this is how each host gets there (H27, H28) |
| I6 | JS `min`/`max`: ternary for symbol/literal arguments, else `Math.min`/`Math.max`; JVM: `:inline` fns | never a `let` (§9.6); one var serves call and value on the JVM |
| I7 | Solutions declare LeetCode's `number[]` as `simple-vector`; tests build inputs with `make-array` | a declaration is a promise about representation, and LeetCode passes a plain JS Array |
| I8 | `setf/` deleted with `macros.cljc`; its tests of `aloop`, `aref`, `push-end`, `dict` retired | D27; the `ucl` suite covers the same behaviour under the new names |
| I9 | Project Squint pinned to 0.14.211; `deps.edn` aliases `:jvm` / `:cljs` select the backend | latest-only rule; backend selection by source root |

Carried over from `setf` and still in force: resolution of a place is syntactic
and macro-time, by name; every runtime argument is evaluated exactly once;
backend selection by source root; mutable host collections only.

## 12. Rejected alternatives

- **Keep `setf` as narrow assignment, with a second contract later** (D1) — the
  higher macros (`forv`, classes) would have needed host code outside any
  contract, which is `macros.cljc`'s problem.
- **Emulate multiple values eagerly** — an allocation on every read. They will be
  syntactic (`multiple-value-bind` recognizes a place at compile time) when added.
- **`fixnum` at each host's natural width** (D10) — correct Common Lisp, but on
  JS it must be `Float64Array` to stay correct, giving up 1.5–2.5×.
- **Always-checked or always-unchecked code** (D11) — the first taxes every
  submission, the second makes tests miss what SBCL would catch.
- **`defun` with `(declare (optimize (safety 0)))` per function** (D13) — safety
  is per build. Type declarations did come back via `defun` with D18.
- **An environment variable for safety** (D14) — one mechanism for all hosts, but
  the user preferred each host's own configuration.
- **Short keyword type flags (`^:ucl/fixnum-vector`), or host hints (`^ints`)**
  (D15) — new vocabulary, or the client naming the JVM's representation.
- **Automatic conversion of vector literals at call sites** (D17) — the solutions
  already choose a converter explicitly; nothing needs guessing.
- **Tagged literals / `#js` on every host** (H16) — Squint does not support
  custom tags; `#js` on the JVM relies on an unqualified tag.
- **Today's `defclass`** (D18) — host-branched in full (fails heuristic 2), and
  cannot express an outside write on the JVM (H10).
- **A real CLOS subset** (D18) — no positional constructor; generic-function
  dispatch is either run-time cost or heavy static analysis.
- **`aloop`/`forv`** (D20) — 12–26% slower than `loop`/`recur` in five of six
  solutions, tie in the sixth; and custom syntax.
- **A Common Lisp `=`** (D21) — duplicates `==` and invites confusion with
  collection `=`.
- **Hand-written per-backend delegates** (D26) — ~20 lines repeated per host.
- **Incremental or slice-by-slice migration** (D27) — two vocabularies coexist.

## 13. Open items

- **Resolved by §14:** one plain `(:require [ucl.api :as ucl])` works on all
  four hosts. (ClojureScript exposing declarations to macros mattered only for
  call-site literal conversion, which D17 dropped.) ClojureScript does expose
  them -- `(:locals &env)` carries a param's `:tag` -- and the JS emitter uses
  it to skip the `vector?` branch.
- **Resolved by I1:** the ClojureScript and Squint emit maps are one, in
  `backends/js`.
- **v1 limits found while implementing:**
  - ClojureScript checks no arity on struct constructors (they are function
    values, so an omitted `&optional` argument is allowed).
  - On the JS hosts a constructor named like the struct cannot be combined with
    other constructors.
  - `with-slots` walks its body without expanding macros: a user macro that
    binds a slot's name is not seen as shadowing it.
  - The JVM fixtures accept only the all-slots constructor (`(new ListNode 1
    nil)`, not `(new ListNode 1)`).
- **The ClojureDart spike predates v1.** `prototypes/ucl-cljd` still runs its
  own copy of the early contract; a ClojureDart backend for v1 would start from
  this contract plus the spike's H20–H25.
- **Not yet shown on ClojureDart:** D12 key normalization, D14 safety via
  compiler options (where would a ClojureDart macro read it?), D18 classes, D21
  inline `min`/`max`. The spike covered `elt`, `setf`, `make-array`, `defun`.
- **Squint internals relied on:** `(:var->ident &env)` (H1) and the `defmacro`
  marker (H6). Latest-Squint-only plus the suite makes a break loud.
- **No JMH.** JVM numbers rest on simple shapes.
- **The repository split** (`ucl`, `ucl-jvm`, `ucl-cljs`, `ucl-squint`) is laid
  out, not performed.

## 14. Validation: ClojureDart, a fourth host

`prototypes/ucl-cljd/` is a spike answering one question: can this design
express `src/squintcode/fizzbuzz.cljc` on ClojureDart — a host it never
considered — with nothing but a new backend directory?

**Yes.** One unchanged source file, using a single plain
`(:require [ucl.api :as ucl])` and no reader conditionals, runs on Clojure/JVM,
ClojureScript, Squint and ClojureDart with identical output. The spike
implements only `elt`, `setf`, `make-array` (all three element types) and
`defun` + `declare`.

| | JVM | ClojureScript / Squint | ClojureDart |
|---|---|---|---|
| `(ucl/make-array n)` | `(object-array n)` | `new Array(n)` | `List.filled(n, null)` |
| `… :element-type 'fixnum` | `(int-array n 0)` | `new Int32Array(n)` | `Int32List` via a backend fn (H23) |
| `(ucl/setf (ucl/elt a i) v)` | `aset` on a hinted array, no reflection | `a[i] = v` | `a[i] = v` |
| `(ucl/elt a 0)` | `aget` on a hinted array | `a[0]` | `a[0]`, typed `int` on an `Int32List` |
| `(declare (type fixnum n))` | `^long n` | — | same as a hand-written `^int` (H25) |

The new backend is 30 lines: an emit map, two runtime constructors, one
`defapi` line. **The shared contract needed no ClojureDart-specific branch** —
but the spike did change it, in host-agnostic ways the three original hosts
also benefit from:

1. `defapi`'s generated macros call the contract through the backend's alias
   instead of a fully qualified name (H20).
2. Every contract fn is tagged `^:macro-support` (H20) — inert metadata elsewhere.
3. Only non-trivial arguments are bound (H22) — found by reading the Squint
   output, and a real performance fix on the LeetCode host.
4. `(or x 't)` became `(if x x 't)` (H26) — a Squint bug, not a ClojureDart one.

Run it: `prototypes/ucl-cljd/run-hosts.sh`.

## 15. Corrections

A design document that hides its wrong turns is not trustworthy. Each of these
was a claim made without compiling or measuring first.

| Claim | Correction |
|---|---|
| `setf`: generated bindings were hygienic | They were fixed names `t0`, `t1`, `v`, and captured caller locals. Now `gensym`s. |
| `setf`: "JS `make-arr` builds an array with holes" | `(array n)` builds the one-element array `[n]`. |
| `setf`: the JVM constructor table recognised `make-obj` | It matched any fn *named* `make-obj` in any namespace. |
| `setf`: "a macro cannot see a binding-site hint" | On the JVM it can, through `&env` (H2). |
| `setf`: "green on all three hosts" | JVM and ClojureScript failures exited 0; "ALL HOSTS AGREE" was unconditional. |
| `setf`: "curried `get!` measured ~110×" | The benchmark did not compile. Fixed and run: 15–23×. |
| "Common Lisp's one-dimensional constructor is `make-vector`" (raised in grilling) | ANSI CL has no `make-vector`; it is Scheme/Emacs Lisp. CL uses `make-array`. |
| "`fixnum` as 32-bit is enough for LeetCode" (first D10 recommendation) | The `long`-answer problems overflow it silently; added `(signed-byte 53)`. |
| "`aloop` generates the fastest JavaScript" | A first, 2-of-6 measurement showed `aloop` 2× faster on 121 — a JIT artifact of `squint_core.min`. Measured across all six loops in isolated processes, `loop`/`recur` is equal or faster. |
| "`aloop` is slower" (the agent's first 2-of-6 claim) | Also premature: that run did not isolate processes and missed the `range` path and `forv`. §9.2 is the full measurement. |
| "Tests calling a generic function don't test LeetCode's path" | They do, if the JS generic function only calls the prototype method (§7). |
| "Bind every runtime argument once" (carried over from `setf`) | Correct, but binding a symbol or literal is pure cost: an IIFE per `elt` read on Squint (H22). Found only by reading the spike's generated JS. |
| §5 said an undeclared `elt` on the JVM is `.get ^java.util.List` | That throws on an undeclared `Object[]`. Implemented as `nth`, with the D4 warning. |
| §7 said a BOA constructor runs "with and without `new`" | True on the JS hosts only; on the JVM `new` reaches the deftype's own constructor (§7). |
| v1's first `incf` on a JVM `gethash` | Passed the new-value form to a write that uses its value twice, so the increment ran twice. Caught by the suite; `incf` now binds the value whenever a backend's write is not evaluate-once. |
| v1's first submissions | Carried contract code -- a table of quoted lists -- through tree shaking (H31). Caught only by reading a bundle; `bb build` now fails on it. |

The pattern is unchanged from `setf`: every serious error was an inference made
where a compile or a measurement would have answered the question.
