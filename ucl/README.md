# `ucl` — Uncommon Lisp

**Status:** v1 implemented (2026-10-03). Design agreed in a grilling session,
validated on a fourth host, ClojureDart, by a spike (§14), then built: the
library, its suite on three hosts, every solution ported, `macros.cljc` and the
predecessor `setf/` deleted (D27). Implementation-time decisions are I1–I9
(§11); what implementing taught is H27–H33 (§10) and §15.

**Variables** (2026-10-03, a second grilling session): `ucl/let`, `ucl/let*`,
assignable parameters and `ucl/dotimes`, so that a loop's state needs neither
`recur` arguments nor an IIFE (§4.1, D29–D37, I10–I13, H34–H38). The terms are
in the glossary (§16).

**ClojureDart** (2026-10-03, a third grilling session): a fourth host, for
tests and for LeetCode's Dart submissions -- standalone Dart, no ClojureDart
runtime, as fast as hand-written Dart (§3, §5, §7, §9.9, D38–D43, I14–I22,
H39–H54).

**Higher-order functions** (2026-10-03, a probe): can a solution pass a `fn` to
a function and stay fast? On V8 only with one closure that assigns nothing; on
Dart never (§9.10, H55–H58). Nothing in ucl changed; §13 records what would.

**Sequence functions** (2026-10-03, a fourth grilling session): Common Lisp's
sequence functions over vectors, each inlining a literal `fn` at expansion.
The query slice is built -- `count`, `find`, `position` (with `-if`,
`-if-not`), `reduce`, `every`, `some`, `notany`, `notevery`; the rest is
designed (§4.2, D44–D52, §9.11, §9.13, I40–I44, H70–H72).

**`ucl/loop` and blocks** (2026-10-03, a fifth grilling session): Common
Lisp's `LOOP` over vectors, numbers and hash tables, and its block model --
`block`, `return-from`, `return` -- with every exit compiled statically.
Blocks and `ucl/loop` are built (I23–I28), but for hash-table iteration,
`loop-finish` and parallel `for … and` (§13); §4.3, D53–D62, §9.12, H59–H62.

```bash
ucl/run-tests.sh           # the ucl suite on Clojure, ClojureScript, Squint, ClojureDart
bb test                    # that, then every solution on all four hosts
bb build                   # LeetCode submissions: JavaScript (Squint) and Dart, safety 0
```

This document is the design of record. It says what `ucl` is, the rules every
decision is measured against, each decision and what decided it, the
measurements behind them, what was rejected, and what was got wrong on the way.
Decisions `D1`–`D28` are numbered after the first grilling session's question
that settled them (a gap is a question folded into another); `D29`–`D37` come
from the second, on variables; `D38`–`D43` from the third, on ClojureDart;
`D53`–`D62` from the fifth, on `ucl/loop` and blocks.

---

## 1. What it is

`ucl` lets you write **high-performance code once and run it unchanged on every
host that implements its contract** — today Clojure (JVM), ClojureScript,
Squint and ClojureDart. Its vocabulary is borrowed from Common Lisp, because Common Lisp already
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
   LeetCode is the proving ground: Squint's JavaScript and ClojureDart's Dart
   submissions.
2. **Shared logic.** Push as much logic as possible into `ucl.contract`. The
   split line is exactly where host-specific emission becomes unavoidable.
3. **Common Lisp idiom.** Names, argument order and semantics follow ANSI Common
   Lisp. **Alexandria counts as Common Lisp** (it is the quasi-standard library).
   Where the standard is silent, follow what **SBCL and ECL agree on**.

## 3. Architecture

```
ucl/
  shared/ucl/contract.cljc        the whole model, names no host; defapi, defruntime
  shared/ucl/loop.cljc            ucl/loop's expansion, a vocabulary of the registry (I23)
  backends/jvm/ucl/api.clj        emit map, run-time half, (contract/defapi …)
  backends/js/ucl/js_emit.cljc    the JS family's emit map and run-time helpers (I1)
  backends/cljs/ucl/api.cljc      ClojureScript flavor + (contract/defapi …)   macro half
  backends/cljs/ucl/api.cljs                                                   run-time half
  backends/squint/ucl/api.cljc    Squint flavor + (contract/defapi …)          one file
  backends/cljd/ucl/api.cljd      (contract/defapi …) -- macros only (I14)
  backends/cljd/ucl/dart_emit.cljc  the ClojureDart emit map; macro-host state (I15)
  backends/cljd/ucl/runtime.cljd  run-time half: typed-list constructors, cells, checks
  cljd-project/                   the ClojureDart project template builds copy (I16)
  testkit/<host>/ucl/test.*       ucl.test: one test vocabulary on every host (I4)
  testkit/<host>/ucl/leetcode.*   strict ListNode / TreeNode fixtures (D19)
  test/ucl/*_test.cljc            the suite -- byte-identical on every host
  test/ucl/kernels.cljc           LeetCode-shaped functions; their Squint build must have no
                                  IIFE, their Dart build no ClojureDart runtime (I19)
  test-jvm/ucl/*_test.clj         expansion-error tests (the JVM can expand a form at run time)
  run-tests.sh                    run it on all four hosts; any failure fails
  bench/                          every measurement in this document
```

Source roots per host -- exactly one backend each:

| host | roots |
|---|---|
| Clojure/JVM | `shared` `backends/jvm` `testkit/jvm` |
| ClojureScript | `shared` `backends/js` `backends/cljs` `testkit/cljs` |
| Squint | `shared` `backends/js` `backends/squint` `testkit/squint` |
| Squint, submission | `shared` `backends/js` `backends/squint` -- never the test kit |
| ClojureDart | `shared` `backends/cljd` `testkit/cljd` |
| ClojureDart, submission | `shared` `backends/cljd`, plus `testkit/cljd/ucl/leetcode.cljc` so a bare `ListNode` compiles; the bundler leaves it bare (D39) |

- **ClojureDart's `ucl.api` holds macros only** (I14). ClojureDart expands
  macros before special forms and emits a bare `let*` inside every fn (H40), so
  a fn compiled there would expand against ucl's `let*`; the contract's `let*`
  returns a binding vector unchanged, which ends that expansion. The emit map
  is `ucl.dart-emit`, the run-time half `ucl.runtime`, which expansions name
  fully qualified.
- **The contract defines every fn before its first use.** ClojureDart's
  `declare` is a no-op on its macro host (H41); the variable walker, which is
  mutually recursive, is one `walk` whose parts are a `letfn`.

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
  `(:refer-clojure :exclude [make-array min max defstruct defmethod let dotimes])`.
  The JVM backend, whose own code needs Clojure's `let`, spells it
  `clojure.core/let` throughout: after `defapi`, a bare `let` in that namespace
  is ucl's, and a reload would silently compile the backend against it.
  (`let*` is a special form, not a var: `ucl/let*` is a macro only when called
  qualified, which clients always do.)
- **Macro-time code must not reach a submission.** Squint compiles the contract
  and the JS emitter to modules too, and the backend imports them; esbuild's tree
  shaking drops them only if every top-level `def` has a literal value. A table
  of quoted lists compiles to `list(..)` calls and is kept (H31) -- so such
  tables are functions. `bb build` fails if a submission contains any of it.
  A Dart submission holds, by construction, only the definitions the bundler
  reaches from the solution; it fails on any reach into the ClojureDart
  runtime (D38, D39).
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
| `(ucl/setf place v)` `(ucl/incf place [d])` `(ucl/decf place [d])` | assign / read-modify-write any of the places above, or a variable | D6, D29 |
| `(ucl/let ((var init) …) (declare …) body…)` `(ucl/let* …)` | bind variables, in parallel / in order | D29, D31, D32 |
| `(ucl/dotimes (var count [result]) (declare …) body…)` | `var` from 0 below `count`, then `result` | D33 |
| `(ucl/block name body…)` `(ucl/return-from name [v])` `(ucl/return [v])` | leave a block with a value; `ucl/dotimes` is a block `nil`, `ucl/defun`/`ucl/defmethod` a block named after themselves | D55, D56 |
| `(ucl/loop clause…)` | Common Lisp's `LOOP` over vectors and numbers | D53–D62 |
| `(ucl/min a b …)` `(ucl/max a b …)` | inline in call position; functions as values (`(reduce ucl/max …)`) | D21 |
| `ucl/most-positive-fixnum` `ucl/most-negative-fixnum` | ±2³¹ bounds (D10) | D23 |
| `ucl/double-float-positive-infinity` `…-negative-…` | the SBCL/ECL extension names | D23 |
| `ucl/defun` + `(declare (type …))` | function with type declarations; a parameter the body assigns is a variable | D18, D36 |
| `ucl/defstruct` `ucl/defmethod` `ucl/with-slots` | classes | D18 |
| `(ucl/princ-to-string x)` | an integer's decimal digits, or a string itself | I21 |

**Loops are Clojure's own `loop`/`recur`** with `elt`/`length` (D20), plus
`ucl/dotimes` (D33). They compile to a plain `while` on every host. `aloop` and
`forv` are retired. Common Lisp's `LOOP` is `ucl/loop` (§4.3).

**A loop's state lives in variables** (§4.1): an inner loop is a statement that
assigns them, and a loop's value is bound with `ucl/let`, never with `let`.

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

### 4.1 Variables (D29–D36)

A **variable** is a name ucl binds that `setf`, `incf` and `decf` can assign: one
bound by `ucl/let`/`ucl/let*`, or a `ucl/defun`/`ucl/defmethod` parameter the
body assigns (the `defmethod` instance excepted). Every other name is a
**local** -- Clojure's `let`, `loop`, `fn`, the `ucl/dotimes` counter -- and
assigning one is a compile-time error naming it (D31).

```clojure
(ucl/let ((left 0) (total 0))             ; parallel, as Common Lisp's let
  (declare (type fixnum left) (type (signed-byte 53) total))
  (ucl/dotimes (r n total)
    (loop []                              ; a "while" loop: a statement
      (when (too-wide? left r)
        (ucl/incf left)
        (recur)))
    (ucl/incf total (- r left -1))))
```

- **Why.** Clojure locals are immutable, so a loop's state could only travel in
  `recur` arguments, and an inner loop's result only as a value. A `loop` or
  `let` in expression position is an IIFE on Squint (H22, 2–2.5× on LeetCode
  2762, §9.8) and a one-shot `fn` on the JVM (H36). Variables make the inner
  loop a statement.
- **Binding** (D32): `ucl/let` binds in parallel -- every init sees the outer
  names -- and `ucl/let*` in order. `x` or `(x)` starts as nil, which a variable
  declared `fixnum` or `(signed-byte 53)` may not (a compile-time error, as
  SBCL warns). Inits run left to right.
- **Return-position assignment** (D35). An init that is a `loop`, `let`, `do`,
  `if`, `when`, `cond`, `case`, `ucl/dotimes`, ... is not bound as a value: a
  fresh variable is declared, the init runs as a statement, and each of its
  return positions assigns that variable. `(ucl/setf x <loop>)` on a variable
  does the same (I10). So `(ucl/let ((j (loop …))) …)` has no IIFE on any host.
- **Representation** (D30): Squint, a plain `let` assigned in place; ClojureScript,
  the JVM and ClojureDart, a one-field cell (ClojureScript and ClojureDart
  cannot assign a local, H35, H39). The JVM cell is a primitive `long` when the
  variable is declared `fixnum` or `(signed-byte 53)`, else an `Object` with a
  compile-time warning (D34); ClojureDart's is an `int` cell (`IntCell`) when
  so declared. A cell that does not escape costs nothing: V8, the JVM's JIT
  and the Dart VM remove it (§9.8, §9.9).
- **Checks** (D34): at safety ≥ 1 a store into a variable declared `fixnum` or
  `(signed-byte 53)` -- its init included -- signals when the value is outside
  the type.
- **One walker** (shared with `with-slots`) rewrites a variable's reads and its
  uses as a place. It knows Clojure's binding forms (`let`, `loop`, `fn`,
  `letfn`, `doseq`, `for`, `if-let`, `catch`, ...), ucl's (`ucl/let`,
  `ucl/dotimes`, `with-slots`), and that `case` constants are not evaluated. It
  does not expand user macros (§13).

### 4.2 Sequence functions (D44–D52) -- the query slice built, the rest designed

A **sequence function** is one of Common Lisp's functions over sequences: CLHS
Chapter 17 and `every`, `some`, `notany`, `notevery` (§5.3). In ucl a sequence
is a vector. They raise the level of a solution without a runtime
higher-order function, which §9.10 measured as costly: each one inlines its
function argument into the loop it expands to.

```clojure
(ucl/count-if (fn [x] (> x k)) nums)        ; the loop body is (> (ucl/elt nums i) k)
(ucl/reduce ucl/max nums)                   ; (ucl/max acc x): D21's inline ternary
(ucl/map 'vector (ucl/reduce ucl/max) rows) ; a curried form: two nested loops, no fn
```

- **Scope** (D44): every sequence function that needs no data type ucl lacks,
  whole, keywords included -- the rule `ucl/loop` follows, an exception to D8.
  On vectors that is all 43 of Chapter 17, plus the `every` family; a result
  type must be a vector type, and strings are not sequences until ucl has
  characters. Whole means `:test-not` too (deprecated, but in every lambda list
  and in SBCL and ECL), and `(setf (subseq v s e) x)` as a place.
- **`eql` on strings** (D52) compares by value on every host. CLHS leaves
  `(eql "Foo" "Foo")` implementation-dependent; JavaScript and Dart compare by
  value already, and the JVM following them keeps every host's result
  identical (as D10 argues).
- **Expansion** (D45): one shared expansion per function over ucl's own forms
  (`ucl/let`, `loop`/`recur`, `elt`, `length`). A backend operation only where
  a host's native bulk operation measures faster -- candidates `fill`,
  `replace`, `copy-seq`/`subseq`, `sort` (`TypedArray.fill`/`set`/`sort`,
  Dart `fillRange`/`setRange`, `System/arraycopy`). The expansions live in
  their own contract source; the API stays `ucl.api` and its one alias.
- **Function arguments** (D46, D47) -- `count-if`'s predicate, `reduce`'s
  function, `:key`, `:test`: a literal `fn` (`#(…)` too) is written into the
  loop, its parameters bound to the elements, so they take the vector's type;
  a symbol is called in function position -- a `ucl/defun` directly, an API
  macro like `ucl/max` inline, a local holding a fn as a call through it. Any
  other form is evaluated once into a fresh local and called through it, as
  Common Lisp evaluates it once. Each costs exactly the loop written by hand:
  ucl chooses no slower path, so nothing warns; Dart refuses what reaches its
  runtime (H55).
- **Curried form** (D48, D50): a sequence function given every argument but its
  sequence is a literal `fn` of that sequence -- `setf`'s curried read
  (`09b676a`) and `macros.cljc`'s `(length)` revived. The omitted argument is
  the sequence wherever it sits: `(ucl/elt i)`, `(ucl/sort pred)`,
  `(ucl/reverse)`. With two sequences it is the one written, else the last:
  `(ucl/replace src)` takes the target, `(ucl/search needle)`,
  `(ucl/mismatch a)`, `(ucl/merge 'vector a pred)` the second. `map`,
  `map-into` and `concatenate`, whose sequences are `&rest`, have none. A
  sequence function inlines a curried ucl form like a literal `fn`. The bare
  name as a value is a stub that signals: these are macros.
- **Fresh vectors** (D49): `remove`, `subseq`, `reverse`, ... return the
  declared type of their sequence, learned as `elt` learns it (§5): a
  `fixnum-vector` stays an `Int32Array`/`int[]`/`Int32List`. Undeclared, a
  general vector, with the JVM's warning (D4).
- **Expression position** (D51): return-position assignment (D35) learns the
  sequence functions, so a `ucl/let` init or a `setf` of a variable is a
  statement. Anywhere else -- an `if` test, an argument -- it is an IIFE on
  Squint, 8% around a 100-element loop, up to 48% around a 4-element one
  that reads outer variables (§9.11), and `bb build` warns. A lifting
  compiler pass is deferred (§13).

**Build order.** First the query slice -- `count`, `find`, `position` (each
with `-if`, `-if-not`), the `every` family and `reduce`, keywords included:
no allocation, no native operation, and every novel part (D46–D48, D51)
exercised. In parallel, the native bulk operations D45 needs are measured
(`fill`, `replace`, `sort`). Each function ships with kernels in
`test/ucl/kernels.cljc` under I12 and I19, and one ported solution measured
against its `loop`/`recur` version.

**Built: the query slice** (`shared/ucl/seq.cljc`, suite
`test/ucl/sequences_test.cljc`). Its kernels compile to the loop written by
hand -- the predicate in the ternary, no IIFE, no ClojureDart runtime -- and a
`ucl/let` init runs as a statement. What implementing settled is I41–I44:
the registry entry applies only to a call through ucl's alias (I41); a
literal `fn`'s body takes its continuation into its tail (I42); `eql` and a
run-time failure are two backend operations (I43); and an empty `reduce`
without `:initial-value` decides an operator's value at compile time (I44).

The ported solution (`bench/seq/`): LeetCode 1295 as `loop`/`recur`
(`lc_1295_…`) and with `ucl/count-if` (`lc_1295_…_seq`), both as `bb build`
submits them, n = 10⁵, median µs: V8 1003 against 1029 (7 processes; the
JavaScript is identical but for two `const` aliases); Dart JIT 360 against
362; Dart AOT 686 against **383** -- the expansion's counters are hinted
`int` (I20), the hand-written `loop`'s are `dynamic`.

### 4.3 `ucl/loop` and blocks (D53–D62) -- built, but hash-table iteration (§13)

`ucl/loop` is Common Lisp's `LOOP` (CLHS 6.1) for what ucl has: vectors,
numbers, hash tables. It exists to make a solution short without making it
slower: its clauses expand to the `while` loop with assigned variables that
§9.8 measured as hand-written.

```clojure
(ucl/defun maxProfit (prices)
  (declare (type fixnum-vector prices))
  (ucl/loop for p across prices
            minimize p into lo of-type fixnum
            maximize (- p lo)))

(ucl/defun numSubarraysWithSum (nums goal)
  (declare (type fixnum-vector nums))
  (let [freq (ucl/make-array (inc (ucl/length nums)) :element-type 'fixnum)]
    (ucl/setf (ucl/elt freq 0) 1)
    (ucl/loop for x across nums
              sum x into s of-type fixnum
              when (>= s goal) sum (ucl/elt freq (- s goal))
              do (ucl/incf (ucl/elt freq s)))))
```

- **Scope** (D53): every clause of CLHS 6.1 that needs no data type ucl
  lacks -- the rule the sequence functions follow (D44), an exception to D8.
  `ucl/loop` grows with the data model: `across` takes strings once ucl has
  characters, with no change to `ucl/loop`.

  | CLHS 6.1 | clauses | |
  |---|---|---|
  | 6.1.1 | simple `(loop form*)`, `named` | in |
  | 6.1.1.7 | destructuring | out: its patterns are conses |
  | 6.1.2.1.1 | `for` arithmetic: `from upfrom downfrom to upto below downto above by` | in |
  | 6.1.2.1.2–3 | `for … in`, `for … on` | out: lists |
  | 6.1.2.1.4 | `for x = e [then e2]` | in |
  | 6.1.2.1.5 | `for x across v` | in |
  | 6.1.2.1.6 | `being the hash-keys`/`hash-values of h [using …]` | in (D58) |
  | 6.1.2.1.7 | package symbols | out: packages |
  | 6.1.2.2 | `with` | in, without destructuring |
  | 6.1.3 | `count sum maximize minimize`, `into`, `of-type` | in |
  | 6.1.3 | `collect append nconc` | out: they build lists |
  | 6.1.4 | `while until repeat always never thereis`, `loop-finish` | in |
  | 6.1.5–6 | `do`, `return`, `if when unless else end`, `it` | in |
  | 6.1.7 | `initially`, `finally` | in |

- **Expansion** (D54): one shared expansion in the contract, over ucl's own
  forms -- `ucl/let` and `loop`/`recur` -- with no backend operation but the
  hash-table iterator (D58). A `for` name is a local, stepped by `recur`:
  assigning it is a compile-time error, which Common Lisp, leaving it
  undefined, allows. A `with` or `into` name is a variable. The variable
  walker learns `ucl/loop`'s and `block`'s bindings, as it knows
  `ucl/dotimes`'.
- **Blocks** (D56): Common Lisp's block model, in the contract. `block`,
  `return-from` and `return`; `ucl/loop` and `ucl/dotimes` establish a block
  named `nil`, `ucl/defun` and `ucl/defmethod` one named after the function.
  `(return-from twoSum (pair i j))` inside two `ucl/dotimes` leaves the
  function.
- **Exits are static** (D55). An exit is accepted only where the expansion can
  make it the end of its block by restructuring: in statement or return
  position, through `if`, `when`, `unless`, `cond`, `case`, `do`, `let`,
  `ucl/let` and nested ucl blocks. `A (when c (return x)) B` becomes
  `A (if c <exit with x> (do B <next iteration>))`. An exit inside a loop
  ends it by not recurring: when the loop is the block's last form the exit's
  value is the loop's; when something follows the loop -- the rest of an
  outer loop's body -- the exit assigns a result variable and a flag, and the
  code after the loop tests the flag (I25). Any loop: `ucl/dotimes`,
  `ucl/loop`, a sequence function's, a Clojure `loop`/`recur`. Anywhere else
  -- an argument, a binding's init, an `if` test, an `fn` -- it is a
  compile-time error naming the place. This is a second
  boundary beside D53's: **a form ucl cannot compile to the host's own loop
  without run-time cost is a compile-time error.** Exceptions would carry an
  exit anywhere, but cost 1.7× to 40× on the submission hosts (§9.12).
- **Accumulators** (D57). One without `into` is internal: a `recur` parameter,
  typed by the host as today's solutions' sums are. `count` is a `fixnum`, as
  SBCL and ECL declare it (H62). An `into` name is a variable under D34: `of-type`
  declares it; undeclared it is SBCL's and ECL's `number` -- a boxed cell with
  a warning on the JVM, a dynamic `Cell` on Dart. Two words on a named
  accumulator, as SBCL code is written.
- **Hash-table iteration** (D58) enters the contract through `being the
  hash-keys`/`hash-values`; the iterator is the one host-specific part. The
  order is unspecified and changing the table during the iteration undefined,
  both as in Common Lisp; the JVM keeps `HashMap`, whose order differs from
  the insertion order JS `Map` and Dart's `Map` keep, so a test that relies on
  an order fails there. `maphash` waits for a solution that needs it (D8).
- **Expression position** (D59): return-position assignment (D35) learns
  `ucl/loop` and `block`, so a `ucl/let` init or a `setf` of a variable is a
  statement; anywhere else an IIFE on Squint that `bb build` warns on, as D51.
- **Conformance** (D60): every `ucl/loop` and block case of the suite runs on
  SBCL and ECL too, and `ucl/run-tests.sh` fails unless all six agree. Where
  SBCL and ECL disagree and the standard is silent, the form is rejected (D2).
- **Rejections** (D61) name the clause or form, the reason -- a data type ucl
  lacks, or an exit that cannot be static -- and what to write instead:
  "`for … in` iterates a list; ucl has no lists: use `for … across`".
- **Proof** (D62): every solution with a loop is ported to `ucl/loop` beside
  its original -- `<problem>_loop.cljc`, `squintcode.<problem>-loop`, the same
  LeetCode names, so `bb build` makes both submissions. One test file states
  the cases once and checks both. A port has no IIFE (I12) and reaches no
  ClojureDart runtime (I19), and is measured against its original on V8, Dart
  JIT and Dart AOT (as §9.7): it may not be slower. That also measures the
  exit flag (D56) and the hash-table iteration step (D58).

### Not in v1

Multiple values (`multiple-value-bind`), `mulmod`, hash-table deletion,
`maphash`, `aref` (reserved), CLOS `defclass`, `:test 'equal`. Hash-table
iteration is designed (§4.3, D58).

## 5. Arrays and types

### Representation (D9, D10)

| `make-array` call | JVM | JS (Squint, ClojureScript) | Dart (ClojureDart) |
|---|---|---|---|
| default, `:element-type t` | `Object[]` | `Array` | `List<dynamic>` |
| `:element-type 'fixnum` | `int[]` | `Int32Array` | `Int32List` |
| `:element-type '(signed-byte 53)` | `long[]` | `Float64Array` | `Int64List` (I17) |
| `:element-type 'string` | `Object[]` | `Array` | `List<String>` |
| `:adjustable t :fill-pointer k` | `java.util.ArrayList` | `Array` | growable `List<E>` |

- **`string` exists for Dart** (I21). Dart reifies generic types: LeetCode's
  harness wants fizzbuzz's `List<String>`, and a `List<dynamic>` of strings is
  not one. The JVM and JS upgrade it to `t`, as Common Lisp implementations do.

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
ignores it; the JVM uses it to emit direct access; Dart casts to it.

**LeetCode's `int[]` input is a `fixnum-vector`** (D42): a vector of fixnums,
whatever container the host passes -- a JS `Array`, a Dart `List<int>`.
Declared `simple-vector`, Dart reads its elements as `dynamic` (§9.9).
`simple-vector` is for any other input. On the JVM a nested macro
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
| ClojureDart | nothing: a Dart `Map` compares with `==`, an `Int32List` read is an `int`, a keyword is canonical (H47) |

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
- **ClojureDart:** a `deftype`, methods inside the class (D40). A Dart class
  cannot gain methods once defined, but ClojureDart expands every top-level
  form of a namespace twice -- macro host, then Dart (H42): in the first pass
  each `defmethod` records its body, in the second `defstruct` emits every
  method into the class. The generic function calls the method directly
  (Dart's dynamic dispatch when two structs share the name). So a method must
  be defined in its struct's namespace. When a constructor is named like the
  struct the class is `<Name>_struct`, since ClojureDart turns a call of a
  class name into `new`. ucl names fields and methods itself: ClojureDart
  leaves `-` in a `set!` of a field and in method names (H43). A Dart
  submission's `class NumArray` wraps the struct: its constructor runs the BOA
  constructor, its methods delegate (D39).
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
  - ClojureDart: `deftype`s; the test kit wraps the compiler's resolver so a
    bare `ListNode` falls back to them (H45);
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
| ClojureDart | the same property, read where ClojureDart runs macros -- its JVM macro host (`clojure -J-Ducl.safety=0 -M:cljd …`) |

On ClojureDart, Dart checks every index itself (`RangeError`) at any safety;
the other checks are `ucl.runtime` helpers like the JS ones. An `Int32List`
store would wrap silently, so `elt-set-checked` checks the value.

What the checks are, per host:

| check (safety ≥ 1) | JS hosts | JVM |
|---|---|---|
| `elt` index in bounds | `elt-checked` / `elt-set-checked` helpers | the host's own (`ArrayIndexOutOfBounds`) |
| store into `fixnum` / `(signed-byte 53)` vector | the helper checks the value | `(int v)` range-checks; `unchecked-int` at 0 |
| `incf`/`decf` of a non-number | `check-number` | `check-number` |
| `vector-push-extend` on a non-adjustable vector | `push-checked` | the host's own |
| `:initial-contents` length ≠ dimension | `check-contents` (literal: at compile time) | same |
| store into a variable declared `fixnum` / `(signed-byte 53)` (D34) | `check-fixnum` / `check-sb53` | same |

Submission builds (`bb build`, `bb build-one`) use 0; tests use the default.
`ucl/run-tests.sh` compiles the suite at safety 0 as well and fails if any
check survives (Squint), or if the kernels reach the ClojureDart runtime.
`bb build` analyzes every Dart submission and fails on any error (D43).
ClojureScript caches compiled namespaces, and changing the setting does not
invalidate that cache — clean before switching (the `bb` tasks do).

**The JVM warns at compile time whenever it falls back** because a type is
unknown -- `elt`, `setf` of `elt`, `length` and `vector-push-extend` on a
receiver of unknown type compile to `nth` / a dispatching helper / `count`;
a variable with no declared type gets a boxed `Object` cell (D34).
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
`forv` adds `dotimes`' binding. That is the 12–26%. → D20. (The binding was not the cause -- §15.)

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

### 9.8 Variables (`bench/variables/`)

How a variable could be stored -- a 200k-element running sum, µs:

| host | storage | µs |
|---|---|---|
| JVM | `loop`/`recur` (no variable) | 90–141 |
| JVM | one-element `long-array` | 75–103 |
| JVM | `deftype` cell, `^long` field (D30) | **77–102** |
| JVM | `volatile!` | 2392–2434 |
| V8 | `let` assigned in place (Squint, D30) | **167–180** |
| V8 | object with one field (ClojureScript `deftype`, D30) | **185** |
| V8 | one-element array | 588–595 |
| V8 | Squint `volatile!` / `vreset!` | 340–348 |

A volatile boxes every write and fences it on the JVM, and is three core calls
per access on Squint. A cell that never escapes is removed by the JIT.

LeetCode 2762 (Continuous Subarrays), n=10⁵, three builds of the same
monotonic-deque algorithm; median of 7 fresh processes, µs warm:

| build | random values | random walk | sorted | values 1..4 |
|---|---|---|---|---|
| hand-written JavaScript (push, then shrink) | 1300 | 1472 | 756 | 1336 |
| ucl, one flat `loop`/`recur` (before variables; shrinks first) | 1232 | 1702 | 1037 | 1643 |
| ucl, variables, push then shrink | 1312 | 1576 | 791 | 1463 |
| **ucl, variables, shrink then push** (the solution) | **1110** | **1428** | 866 | **1302** |

The flat build -- the only IIFE-free way to write it before variables -- re-checks
every queue condition on each step. With variables the generated code is the
hand-written loop (four `while`s, `let`s assigned in place). Written naturally
without variables, each inner `loop` bound in `let` was an IIFE: 2–2.5× the
hand-written time. The remaining differences are the algorithm's order, not
the code generation: the same order hand-written and through ucl measure alike.

A LeetCode-like mix (`lc2762-mix.mjs`: 60 arrays, mostly random values up to
10⁹), median of 7 processes, ms per pass:

| build | first pass | warm |
|---|---|---|
| flat | 40.2 | 28.0 |
| hand-written | 32.9 | 26.4 |
| variables, push then shrink | 34.7 | 26.6 |
| **variables, shrink then push** | **31.1** | **24.4** |

**LeetCode's runtime cannot rank these.** Identical submissions of one build
measured 74 and 88 ms (flat) and 96 and 70 ms (variables, shrink first) on
2026-10-03; every build reached "Beats 100%" at least once. A difference under
~25% needs the local benchmark.

`ucl/dotimes` against the same loop written with `loop`/`recur` (412 fizzBuzz,
n=10⁴): identical JavaScript, 43–46 µs both (D33, §15).

### 9.9 Dart (`bench/dart/`)

Dart SDK 3.13.5, arm64; median of 7–9 rounds after warm-up, each variant in
its own process; JIT is `dart run`, AOT `dart compile exe`.

**On LeetCode** (2026-10-03):

| submission | runtime | beats |
|---|---|---|
| 412 fizzbuzz, ucl | 3 ms | 76% |
| 412 fizzbuzz, ucl, untyped `class Solution` (D39) | 3 ms | 76% |
| 412 fizzbuzz, hand-written | 2 ms | 100% |
| 2762, ucl (`simple-vector` input, before D42) | 473 ms | 100% |
| 2762, hand-written | 487 ms | 100% |

LeetCode's Dart runtime is mostly its harness (~470 ms for 2762); it cannot
see the differences below. fizzbuzz's 2 against 3 ms is its 1 ms resolution:
locally all fizzbuzz variants -- ucl, `%` for `.remainder`, a typed `int`
parameter, hand-written -- measure 67–79 µs (n=10⁴), dominated by `toString`.

**Casts on `dynamic` parameters** (H25, `casts.dart`, a 10⁵-element sum): 75.3
against 75.2 µs JIT, 71.1 against 71.7 µs AOT. The VM removes them.

**LeetCode 2762, n=10⁵** (values 1..5), ms per call -- the ucl build, then one
difference to hand-written Dart removed at a time:

| build | JIT | AOT |
|---|---|---|
| ucl, `nums` declared `simple-vector` | 2.02 | 3.99 |
| …each `IntCell` replaced by an `int` local | 1.98 | 4.39 |
| …plus `nums` read as `List<int>` | 1.91 | 3.25 |
| …plus `&&` for ClojureDart's `and` (H48) | **1.55** | **3.03** |
| hand-written | 1.60–1.67 | 2.89–3.02 |
| ucl, `nums` declared `fixnum-vector` (D42) | 1.93–1.99 | 3.13–3.25 |
| …with only `late final` declared `final` | 1.62 | 2.89 |
| **ucl, built today: `fixnum-vector`, no `late` (I22)** | **1.69** | **2.97** |

Cells cost nothing (the VM removes a non-escaping one). Element type costs
≈25% AOT -- D42 removed it. The rest was not `and` but the `late` on the local
ClojureDart forks it into (H54): dropped by the bundler, the ucl build
measures as hand-written Dart.

### 9.10 Runtime higher-order functions (`bench/hof/`)

The question: can a solution be written at a higher level by passing a `fn`
to a function, without losing ucl's speed? `probe_hof.cljc` defines `fold` and
`each` as `ucl/defun`s and writes three kernels with them and, as today, with
`loop`/`recur`. Both are submission builds (safety 0), n = 10⁵, and every
variant returns the same answer. V8: each case alone in 7 fresh Node 24.16.0
processes, the median of 200 warm runs. Dart 3.13.5, arm64: 5 processes.
"1 closure": only the measured closure ever reaches `fold`/`each`, as when a
solution calls it once. "2–5": two or five other closures went through first,
as when a solution calls it with several lambdas.

To build on Dart at all, `f` had to be declared `function`, a type the contract
lacks: a probe-only patch hinted it as Dart's `Function` (H55). The closure's
own parameter needed a raw `^int` hint, since a `fn` has no `declare`:
untyped, it is a `num` stored into an `int` cell, and `dart analyze` refuses it.

V8, warm µs:

| kernel | `loop`/`recur` | `fold`/`each`, 1 closure | 2–5 closures |
|---|---|---|---|
| sum | 83 | **81** | 518–527 |
| count ≥ k (the closure captures `k`) | 109 | **105** | 539–541 |
| maxProfit (the closure assigns `ucl/let` variables) | 114 | 822 | 810–813 |
| …the same, `d` bound so `max` is a ternary (`profitEach2`) | 114 | 250 | — |

Dart, warm µs, JIT / AOT (the number of closures made no difference):

| kernel | `loop`/`recur` | `fold`/`each` |
|---|---|---|
| sum | 66 / 400 | 1270 / 1140 |
| count ≥ k | 90 / 462 | 1335 / 1332 |
| maxProfit | 99 / 430 | 1472 / 1232 |

Hand-written Dart (`hand.dart`), the sum, JIT / AOT: a `for` loop 65 / 64; a
fold over `int Function(int, int)` 502 / 278; over `dynamic Function(dynamic,
dynamic)` 502 / 497; over bare `Function`, as ucl builds it, 1211 / 982.

The maxProfit closure on V8, one difference at a time (`closure-vars.mjs`):
as Squint emits it 830; with `var` for `let` 640; with a ternary for
`Math.max` 248; the variables in one object instead 283. The loop: 114.

- **V8 inlines a closure only while one reaches the call** (H57): `fold` then
  costs nothing. A second closure through the same `fold` makes it a real
  call: 5×.
- **A closure that assigns `ucl/let` variables** keeps them in V8's context,
  not in registers: 2.2× at best. `Math.max` on them -- which I6 emits when an
  argument is an expression -- costs 3× more (H58). In a loop the same
  `Math.max` is the faster form (§9.6).
- **Dart does not inline a closure call** (H56), however many closures and
  however typed: 4–8× hand-written, 3× (AOT) to 15–20× (JIT) as ucl builds
  it. LeetCode's Dart runtime, mostly its harness (§9.9), would not show a
  millisecond, but it is not free.

So a function that receives `fn`s at run time is cheap only on V8 and only with
one closure that assigns nothing. A sequence function that inlines a literal
`fn` at expansion -- the pattern by which `ucl/min` inlines in call position
and is a function as a value (D21, H8) -- would generate the loop itself and
pay none of this. It is not built (§13).

### 9.11 An IIFE around a whole loop (`bench/iife/`)

What a sequence function in expression position costs on Squint (D51): an
outer loop over `r` counts the elements > k in `xs[r..r+W)` -- an inline
`count-if` -- hand-written as a statement and as Squint's IIFE, and both again
with the inner loop reading outer assigned `let`s, which the IIFE captures.
n = 10⁵, each case alone in 7 fresh Node 24.16.0 processes, the median of
medians, µs; every pair returns the same total.

| inner loop | statement | IIFE | statement, reads outer `let`s | IIFE, captures them |
|---|---|---|---|---|
| W = 100 | 13852 | 14922 (+8%) | 11284 | 12791 (+13%) |
| W = 4 | 785 | 1055 (+34%) | 743 | 1102 (+48%) |

The IIFE costs ≈3–10 ns per call, not per element: small around a long loop,
a third to a half around a short one, more when captured variables move from
registers into its context (H58). (The two statement forms differ by up to
19% in the wrong direction -- a code-layout effect; compare within a pair.)

### 9.12 Exits through exceptions (`bench/exit/`)

Could `(return x)` be legal anywhere -- a throw caught at the block -- instead
of only where it can be made static (D55)? A loop finding the first index
from `s` whose element exceeds `x`, hand-written per host as ucl would
compile each choice: `pos` returns statically; `pre` throws one preallocated
object; `fresh` a new stackless one per exit (on the JVM `me.lomin/ex`'s
`ex/exit`: a `RuntimeException` with `writableStackTrace` false); `error` an
`Error` / `ex-info`, which records a stack trace. **short**: 10⁵ calls, each
exiting after a few steps; **none**: one scan of 10⁶ elements that never
exits -- the `try` is there, nothing is thrown. Warm medians, each variant
alone in fresh processes (V8: Node 24.16.0, 7; Dart 3.13.5 arm64: 5; JVM
OpenJDK 21: 3), µs. The JS covers Squint and ClojureScript, which both emit a
plain `try`/`throw`.

| host | short: `pos` | `pre` | `fresh` | `error` | none: `pos` | `pre` |
|---|---|---|---|---|---|---|
| V8 | 2311 | 11579 (5.0×) | 12109 | 329869 | 1210 | **2040 (1.7×)** |
| Dart JIT | 1941 | 77698 (40×) | 77802 | 90826 | 830 | 821 |
| Dart AOT | 4691 | 121421 (26×) | 124762 | 129570 | 1983 | 1990 |
| JVM | 1414 | 1900 (1.3×) | 3142 (2.2×) | 817849 | 449 | 457 |

- **The JVM** exits for 5 ns (preallocated) to 17 ns (`ex/exit`) when nothing
  records a stack trace (H61) -- but it is a test host.
- **V8 pays for the `try`**, thrown or not: 1.7× on a loop that never exits;
  then ≈ 93 ns per exit (H59). A `try` moved into a wrapper function removed
  it on one workload, not on the other.
- **Dart pays for the throw**: ≈ 0.8 µs (JIT) to 1.2 µs (AOT) per exit,
  whatever is thrown -- it records a stack trace for any object (H60). An
  inner search loop with an exit, run once per element at n = 10⁵, adds
  ≈ 100 ms.

So an exception is cheap only on the host that submits nothing; D55 compiles
exits statically or not at all.

### 9.13 Native bulk operations (`bench/native/`)

D45 lets a sequence function use a host's native bulk operation where it
measures faster than the element loop. Each candidate against the loop ucl
would expand to, on LeetCode's two hosts: a typed vector (`Int32Array` /
`Int32List`) and a general one (`Array` / `List<int>`), n = 100 and 10⁵, a
`:start`/`:end` range of half the vector for `replace` and `subseq`. V8: 7
fresh Node 24.16.0 processes; Dart 3.13.5 arm64: 5 processes, JIT and AOT.
Median µs per call; every pair returns the same result.

| operation, n = 10⁵ | V8 loop → native | Dart JIT loop → native | Dart AOT loop → native |
|---|---|---|---|
| `fill`, typed | 63 → **8.2** | 41 → 49 | 57 → 165 |
| `fill`, general | 61 → 64 | 45 → 66 | 61 → 61 |
| `replace`, typed (`set`/`setRange`) | 41 → **4.5** | 33 → **4** | 41 → **4** |
| `replace`, general (`setRange`) | 36 → — | 41 → 113 | 61 → 146 |
| `subseq`, typed (`slice`/`sublist`) | 84 → **41** | 46 → **9** | 47 → **9** |
| `subseq`, general | 131 → **72** | 235 → 215 | 254 → 209 |

At n = 100 the same picture, except V8's `set` (49 against 19 ns: the
`subarray` view) and Dart AOT's `fillRange` (172 against 58 ns).

Sorting 10⁵ fixnums, ms (n = 100: the same order):

| | V8 | Dart JIT | Dart AOT |
|---|---|---|---|
| native, no comparator (`Int32Array.sort()` / `Int32List.sort()`) | **4.5** | 24.3 | 34.3 |
| native, comparator `(a, b) => a - b` | 11.4 | 14.1 | 23.6 |
| inline quicksort, `<` inlined | 6.4 | 5.6 | 17.9 |
| inline bottom-up merge sort, `<` inlined (stable) | 7.2 | **3.7** | **4.9** |

A native sort that calls a comparator loses to the inline expansion on both
hosts: a closure call per comparison (H56, H57). Only V8's typed sort without
a comparator wins, and only for numbers in ascending order. Dart's own sort is
the slowest choice even without a comparator (H71). → I40.

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

Found while adding variables (Squint 0.14.211, ClojureScript 1.12.42, Clojure
1.12, Node 24):

- **H34** — Squint compiles a `let` binding tagged `^:mutable` to a JS `let`
  that `set!` assigns; untagged, it is a `const`, and `set!` fails at run time.
  A `loop` in statement position is a plain `while`; in expression position, an
  IIFE (H22).
- **H35** — ClojureScript refuses `set!` on a local ("Can't set! local var or
  non-mutable field"). `(js* "~{} = ~{}" x v)` would assign it but is a hack
  (rejected, §12); `(set! (.-v cell) v)` on a `deftype` with a `^:mutable`
  field is ordinary property assignment.
- **H36** — Clojure compiles a `loop` in expression position as a one-shot
  `fn` (an extra class, `core$expr$fn__141`), whose value comes back boxed; in
  statement or return position it is inline.
- **H37** — A `volatile!` costs ≈ 25× a primitive local on the JVM and ≈ 2× on
  Squint; a non-escaping one-field cell costs nothing on either (§9.8).
- **H38** — A protocol method whose signature carries a `^long` parameter hint
  makes Clojure compile callers as a primitive invoke (`IFn$OLO`) that the
  protocol function does not implement: `ClassCastException` at the call.

Found while adding ClojureDart (`247b3c2`, Dart 3.13.5):

- **H39** — ClojureDart cannot assign a local: `set!` works on a `^:mutable`
  `deftype` field only ("Cannot assign to non-mutable"). A variable is a cell.
- **H40** — ClojureDart expands macros before special forms, and its compiler
  emits a bare `let*` around every fn body. In a namespace that defines a
  `let*` macro -- `ucl.api` -- every fn expands against it. A macro that returns
  its `&form` unchanged ends expansion, and the special form compiles.
- **H41** — ClojureDart's `declare` is a no-op, and its macro host evaluates
  each `^:macro-support` def as it reads it: a forward reference does not
  resolve there.
- **H42** — ClojureDart compiles a namespace in two passes, macro host then
  Dart, and expands every top-level form in both: in the second, a macro sees
  what later forms recorded in the first. Its source calls the two passes "a
  temp hack … at the moment"; ucl's methods depend on it (D40), and the suite
  breaks loudly if it changes -- the generic function calls a method the class
  lacks.
- **H43** — ClojureDart munges `-` in a field read (`prefix_sum`) but not in a
  `set!` of the field, nor in a `deftype` method's name; a class name must be a
  Dart identifier.
- **H44** — `(. a "[]=" i v)` is typed `void`: an assignment cannot be used as a
  value.
- **H45** — A bare symbol resolves through the namespace's own defs and its
  mappings, and `:refer-clojure` maps only `cljd.core`'s fields, never classes.
  Wrapping `cljd.compiler/resolve-non-local-symbol` on the macro host makes a
  bare `ListNode` resolve (the test kit's analog of H27).
- **H46** — ClojureDart compiles `mod` to Dart's `%`, which is Euclidean:
  `(mod 5 -3)` is 2, Clojure's is -1. `rem` is `.remainder`.
- **H47** — A ClojureDart vector is a Dart `List` (`[]`, `.length`, `is List`);
  a keyword literal is a canonical `const` compared with `==`.
- **H48** — `(and a b)` in a test compiles to a `late final bool` temporary and
  an `if`, not `&&`. The cost is the `late` (H54), not the shape: without it,
  the shape measures as `&&` (§9.9).
- **H49** — The macro host reads with features `#{:cljd :cljd/clj-host :clj}`,
  the Dart side with `:cljd` only: `#?(:cljd/clj-host …)` is macro-host code.
  `clojure.string` through an alias does not resolve on the macro host.
- **H50** — Every `clojure.core` call ClojureDart does not inline is a call
  into `cljd/core.dart` (45k lines): `=`, `str`, `count`, `nth`, `get`,
  `compare`, `int`, `identity`, keyword and vector literals, any seq or
  persistent-collection fn, multi-arity and variadic fns. Arithmetic, `quot`
  `rem` `mod` `abs`, comparisons, bit ops, `min`/`max`, `and`/`or`/`not`,
  `cond`/`case`, `loop`/`recur`, `dotimes`, local `fn`s, `throw` compile inline.
- **H51** — ClojureDart brackets each top-level definition with `// BEGIN` /
  `// END` comments and reaches every other one, even in its own library,
  through an import prefix: a tree shaker needs no Dart parser (D39).
- **H52** — LeetCode's Dart harness accepts top-level functions beside `class
  Solution`, `dynamic` method signatures, and a `dart:typed_data` import; a
  returned `List<String>` passes its check. Its runtime is mostly the harness
  (§9.9).
- **H53** — tools.deps deprecates `:paths` outside the project directory, even
  through a symlink; ClojureDart's compile-error report drops the cause
  (`cljd-project/report.clj` prints the chain).
- **H54** — ClojureDart declares a local bound to an `if`, `let` or `loop` in
  expression position as `late final T x;` and assigns it in each branch
  (`final-locus` in its compiler). `late` makes Dart check the assignment at
  run time, which the JIT keeps in a hot loop: ≈18% on 2762 (§9.9). Declared
  `T x;`, Dart's definite-assignment analysis proves it at compile time -- in
  all seven solutions -- and refuses to compile what it cannot prove.

Found by the higher-order-function probe (§9.10; Node 24.16.0, Dart 3.13.5):

- **H55** — ClojureDart compiles a call of an untyped local, `(f a b)`, to a
  three-way test: `f is Function`, else `IFn$iface`, else `IFn.extensions` --
  the last two in the ClojureDart runtime, so a Dart submission cannot carry
  it. Hinted `Function`, it is a plain `(f as Function)(a, b)`. A `fn` literal
  is a plain Dart closure, typed `dynamic` in each parameter.
- **H56** — The Dart VM does not inline a closure call in a loop, JIT or AOT,
  however the function type is written: a 10⁵-element fold through `int
  Function(int, int)` costs 278 µs AOT against a loop's 64. A call through
  bare `Function` is a dynamic invocation, ≈2× more again.
- **H57** — V8 inlines the closure a function calls while only one closure
  reaches that call site; once two others have, the call stays a call: ≈5× on
  a 10⁵-element fold.
- **H58** — A `let` a closure assigns lives in V8's context: every access is a
  memory access with a TDZ check (`var` measures 25% faster), and `Math.max`
  over such values costs ≈3× a ternary (830 against 248 µs). Why `Math.max`
  suffers there and not in a loop (§9.6) is unmeasured.

Found by the exit benchmark (§9.12; Node 24.16.0, Dart 3.13.5, OpenJDK 21,
SBCL 2.2.9, ECL 21.2.1):

- **H59** — V8: a `try` around a loop costs ≈ 1.7× on a 10⁶-element scan
  that never throws (1210 against 2040 µs), and each throw ≈ 93 ns, whether
  the thrown object is preallocated or fresh. An `Error`, which captures a
  stack, ≈ 3.3 µs.
- **H60** — The Dart VM: a `try` costs nothing; a throw costs ≈ 0.76 µs (JIT)
  to 1.2 µs (AOT), the same for a preallocated object, a fresh one and an
  `Error`.
- **H61** — JVM: a `RuntimeException` built with `writableStackTrace` false
  (`me.lomin/ex`'s `ex/exit`) costs ≈ 17 ns per throw fresh, ≈ 5 ns
  preallocated; an `ex-info` ≈ 8 µs, filling in its stack trace. A `try`
  costs nothing.
- **H62** — SBCL and ECL type a `LOOP` accumulator without `of-type`: `sum`,
  `maximize`, `minimize` as `number`, `count` as `fixnum`
  (`(macroexpand-1 '(loop … sum x into s count x into c))`).

Found while building `ucl/loop` (SBCL 2.2.9, ECL 21.2.1):

- **H63** — ECL refuses a `count` and a `sum` into the default accumulator
  ("Specified data type NUMBER is not a subtype of REAL"), which CLHS 6.1.3
  allows and SBCL accepts: such a form is no conformance case (D60).
- **H64** — SBCL and ECL agree where CLHS is vague: an empty `maximize`
  returns 0, and `finally` sees each `for` name's last value -- the stepped
  value that failed its end test, a later clause not stepped
  (`for i from 1 to 3 for j = (* i 10)` ends with `i` 4, `j` 30).

Found by the native bulk-operation measurement (§9.13; Node 24.16.0, Dart 3.13.5):

- **H70** — `Array.prototype.sort()` without a comparator sorts by the
  elements' strings: `[10, 9, 1]` becomes `[1, 10, 9]`. A typed array's
  sorts numerically. A `fixnum-vector` may be either container (D42), so a
  native call on one must hold for both.
- **H71** — Dart: `Int32List.setRange` and `sublist` copy memory, 5–10× an
  element loop; `fillRange`, and `setRange` on a `List<int>`, are slower than
  the loop (3× AOT for `fillRange`); `List.sort`, with or without a
  comparator, is 4–7× slower than an inline merge sort.

Found while building the query slice (Squint 0.14.211):

- **H72** — Squint compiles a call of an inline operator with no arguments,
  `(-)`, to `()`: invalid JavaScript, which fails the module, not the call.
  Clojure and Common Lisp signal an arity error at run time.

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
| D29 | A loop's state lives in **variables**; `setf`/`incf`/`decf` assign a variable as a place | user judgement, evidence (§9.8) |
| D30 | Squint: a `^:mutable` local; ClojureScript and JVM: a one-field `deftype` cell (primitive `long` when declared). No volatiles, no `js*` | evidence (§9.8, H35, H37), user judgement |
| D31 | Only ucl binds variables; assigning a local is a compile-time error | user judgement |
| D32 | `ucl/let` (parallel) and `ucl/let*` (sequential), Common Lisp syntax with a `declare` head | user judgement |
| D33 | `ucl/dotimes (var count [result])` -- one contract expansion over `loop`/`recur`, no backend operation; no `ucl/loop` yet | evidence (§9.8), user judgement |
| D34 | An undeclared JVM variable is an `Object` cell with a warning; declared integer variables are checked on store at safety ≥ 1 | user judgement |
| D35 | A compound init of `ucl/let` runs as a statement whose return positions assign the variable | user judgement |
| D36 | `ucl/defun` / `ucl/defmethod` parameters the body assigns are variables (not the `defmethod` instance) | user judgement |
| D37 | Terms in a glossary (§16; `GLOSSARY.md` until it moved into this README); decisions stay in this log | user judgement |
| D38 | ClojureDart is a fourth host for tests **and** LeetCode Dart submissions; a submission is standalone Dart, and a solution may not reach the ClojureDart runtime -- `bb build` fails if it does; the contract grows when a solution needs more (D8) | user judgement, evidence (H50) |
| D39 | The bundler generates what LeetCode calls, untyped: `class Solution` (one method per function) and, per design problem, a class that runs the BOA constructor and delegates | evidence (H52, §9.9) |
| D40 | A struct's methods live inside its Dart class, through ClojureDart's two passes | user judgement, evidence (H42) |
| D41 | The prototype's contract changes ship to all hosts: define before use, `let*` passes a binding vector through, a typed `dotimes` counter | evidence (H40, H41) |
| D42 | LeetCode's `int[]` input is declared `fixnum-vector`; `simple-vector` is for anything else | user judgement, evidence (§9.9) |
| D43 | The bundler declares ClojureDart's forked locals without `late`, and `bb build` fails unless every submission analyzes clean; the fix is also proposed upstream | user judgement, evidence (H54, §9.9) |
| D44 | Sequence functions: every one that needs no data type ucl lacks, whole (`:test-not`, `(setf subseq)` included) -- `ucl/loop`'s rule, an exception to D8 | user judgement |
| D45 | Sequence functions expand over ucl's own forms; a backend operation only where a host's native bulk operation measures faster | user judgement |
| D46 | A literal `fn` argument is inlined; a symbol is called in function position | user judgement, evidence (§9.10) |
| D47 | Any other function argument is evaluated once into a local and called through it; no warning | user judgement |
| D48 | A sequence function given every argument but its sequence is a literal `fn` of it (curried form), and is inlined as one; the bare name as a value signals | user judgement |
| D49 | A fresh vector has its sequence's declared element type; undeclared, a general vector with the JVM warning | user judgement |
| D50 | With two sequences, the curried form omits the one written, else the last; `&rest` sequences have none | user judgement |
| D51 | A sequence function in expression position: a statement through D35, otherwise an IIFE that `bb build` warns on; lifting deferred to compiler passes | user judgement, evidence (§9.11) |
| D52 | `eql` on strings compares by value on every host | CLHS leaves it to the implementation; identical results (as D10) |
| D53 | `ucl/loop`: every clause of CLHS 6.1 that needs no data type ucl lacks; out: `for … in`/`on`, `collect`/`append`/`nconc`, destructuring, package iteration -- an exception to D8 | user judgement |
| D54 | `ucl/loop` is one contract expansion over `ucl/let` and `loop`/`recur`; `for` names are locals, `with` and `into` names variables; the walker learns `ucl/loop` and `block` | user judgement |
| D55 | An exit is accepted only where it can be made static; anywhere else a compile-time error: a form ucl cannot compile to the host's own loop without run-time cost is rejected | user judgement, evidence (§9.12) |
| D56 | Common Lisp's block model in the contract: `block`, `return-from`, `return`; `ucl/loop` and `ucl/dotimes` establish `nil`, `ucl/defun`/`ucl/defmethod` their name; an exit through a nested loop by a result variable and a flag | heuristics 3, 2, 1 (decided by the agent at the user's request) |
| D57 | A `ucl/loop` accumulator without `into` is a `recur` parameter; `count` is a `fixnum`; an `into` name is a variable under D34 (`of-type`, else `number`) | heuristic 3 (H62); D34 kept by the user |
| D58 | Hash-table iteration enters the contract through `ucl/loop`'s `being the hash-keys`/`hash-values`; the iterator is a backend operation; order unspecified; `maphash` waits (D8) | heuristics 3, 2 (agent) |
| D59 | Return-position assignment learns `ucl/loop` and `block`; elsewhere an IIFE `bb build` warns on, as D51 | heuristic 1 (agent) |
| D60 | Every `ucl/loop` and block case also runs on SBCL and ECL; the suite fails unless all six agree; a form on which they disagree where the standard is silent is rejected | heuristic 3, D2 (agent) |
| D61 | A rejection names the clause or form, the reason, and what to write instead | agent |
| D62 | Every solution with a loop is ported to `ucl/loop` beside its original (`<problem>_loop.cljc`, same LeetCode names, shared test cases); a port passes I12/I19 and may not be slower | user judgement |

### Implementation decisions (I1–I21)

Made while building v1, not in the grilling session; each is reversible.

| # | Decision | Why |
|---|---|---|
| I1 | One JS emit map, `backends/js/ucl/js_emit.cljc`, shared by Squint and ClojureScript through a small flavor map | the two maps were identical but for array literals, locals, keys, arity checks; the contract may not name a host family (§13 item) |
| I2 | JVM structs implement one interface per slot name | `slot-value` on any struct, typed or not, with no reflection; D4's `slot-value` fallback disappears |
| I3 | JS `gethash` and its `setf` are helper calls, `get-or-default` / `puthash` | exact CL semantics at the cost of the inexact `??` (§9.6), and no IIFE |
| I4 | `ucl.test` (`deftest is testing signals-error?`) on every host; on JVM/CLJS it delegates to clojure.test | test files carry no reader conditional -- D5 kept clojure.test, this only names it once |
| I5 | LeetCode's globals resolve per host: Squint `globalThis`, ClojureScript `cljs.core` names, JVM default-package classes | D19 says `(new ListNode ..)` bare; this is how each host gets there (H27, H28) |
| I6 | JS `min`/`max`: ternary for symbol/literal arguments, else `Math.min`/`Math.max`; JVM: `:inline` fns | never a `let` (§9.6); one var serves call and value on the JVM |
| I7 | Solutions declare LeetCode's `number[]` as `simple-vector`; tests build inputs with `make-array` -- **superseded by D42** | a declaration is a promise about representation, and LeetCode passes a plain JS Array |
| I8 | `setf/` deleted with `macros.cljc`; its tests of `aloop`, `aref`, `push-end`, `dict` retired | D27; the `ucl` suite covers the same behaviour under the new names |
| I9 | Project Squint pinned to 0.14.211; `deps.edn` aliases `:jvm` / `:cljs` select the backend | latest-only rule; backend selection by source root |
| I10 | `(setf var <compound>)` uses return-position assignment too, on variables only | the 2762 kernel's last IIFE; a variable has no subforms whose evaluation order it could change, an `elt` place has |
| I11 | A variable is a place kind, `(%var name type)`, which only the walker writes | `setf`/`incf`/`decf` reuse the place machinery: evaluate-once, checks, return values |
| I12 | `run-tests.sh` fails on an IIFE in `test/ucl/kernels.cljc`'s safety-0 build; `bb build` warns on one in any submission | the claim of D29 is structural; a submission may knowingly bind a `try` |
| I13 | Expansion errors are tested on the JVM only (`test-jvm/`) | the contract is shared; only the JVM expands a form at test run time |
| I14 | ClojureDart's `ucl.api` holds macros only; the emit map is `ucl.dart-emit`, the run-time half `ucl.runtime` | H40 |
| I15 | `dart_emit.cljc` keeps macro-host-only state -- the safety property, the struct and method registry -- under `#?(:cljd/clj-host …)` | H49; the Dart side never needs it |
| I16 | ClojureDart builds run in a project made from `ucl/cljd-project/`, the sources copied into its `src/`; Dart packages persist between runs | H53 |
| I17 | `(signed-byte 53)` vectors are `Int64List` on Dart | a `Float64List` reads back `double`, which is not a Dart `int`; `Int64List` holds the whole range exactly |
| I18 | ClojureDart writes are `(do a[i]=v v)`, write-once only at safety ≥ 1 (a helper) | H44 |
| I19 | `run-tests.sh` fails if the kernels' safety-0 Dart references `cljd.core` | D38 is structural, like I12 |
| I20 | The `dotimes` counter is hinted through a `:types :local-hint` op: `int` on Dart, `number` on ClojureScript, nothing on Squint and the JVM | Clojure refuses a hint on a local bound to a primitive literal |
| I21 | `princ-to-string` and `:element-type 'string` | fizzbuzz on Dart: `str` reaches the runtime (H50), and the result must be a `List<String>` |
| I22 | `bb build` analyzes each submission with LeetCode's `ListNode`/`TreeNode` beside it; warnings (unnecessary casts) pass | D43; it also catches any bundler bug |
| I23 | An expander registry: a ucl form that expands to a loop registers `{:applies? :expand}` by name -- the contract its own (`dotimes`), a vocabulary file its entries through `defapi`'s `:vocabularies`, reaching the backend map as `:expanders`. Return-position assignment (D35) and the walker consult it; an expander must not read `&env`, since an enclosing form may expand it | `ucl/loop` (D59) and the sequence functions (D51) both need D35 and the walker without editing them; the walker expanding an unknown form before walking it keeps its bindings' shadowing right |
| I24 | `expand-counted-loop` -- `dotimes`' loop without its parsing (and, with D56, without its block) -- is the counted iteration every vocabulary expands to | a sequence function's inlined `fn` must not see a `nil` block of ucl's own (D56) |
| I25 | A block is compiled eagerly by the form that establishes it (`ucl/block`, `ucl/defun`, `ucl/defmethod`, `ucl/dotimes`), outside in: an exit in return position is its value; in statement position the rest of the body moves into the branch that does not exit -- unless it would be copied (several branches complete normally), shadowed (a `let`), or repeated (a loop; a literal rest joins the loop's normal ends instead). Otherwise the exit assigns an internal result and flag and the rest is guarded. A block nothing exits from is its body unchanged | the exit `(when c (return-from f i))` in a loop compiles to JavaScript's `return i` -- read in the kernels' build; every existing submission builds identical |
| I26 | `defapi` defines a macro for each of `vocabulary-names` -- a literal list every vocabulary adds its names to -- that expands through its registry entry (`expand-vocabulary`), without `:applies?` | one place for the API surface (D26) of `ucl/loop` and the sequence functions; agreed with the sequence-functions session |
| I27 | `ucl/loop` expands to one Clojure `loop` whose parameters are the `for` names, the internal counters, the default accumulator and `maximize`/`minimize` first-flags; each iteration runs the main clauses in order, then each `for` clause's step and end test, as Common Lisp orders them -- the same tests run once on the first values before the loop. A conditional binds its test (`it`) and a then/else local, and guards each clause under it; a value is read once, a `with`/`into` name assigned with `ucl/setf`. The epilogue (`finally`, the value) is copied to every end test, where every name it reads is bound -- no variable carries a value out of the loop | the 930 kernel's JavaScript is the hand-written loop: one `while`, one ternary per guarded `sum`, no IIFE; SBCL and ECL print the same `finally` values (H64) |
| I28 | An expansion writes ucl's forms through the alias the caller wrote `ucl/loop` with (`ucl/elt`, `ucl/let`, `ucl/block`), and they expand later where the environment is right (I23) | they resolve exactly as the caller's own code; no host needs a fully qualified macro name |
| I29 | Conformance (D60) is `ucl/conformance.clj`: every `(is (= expected form))` in a `deftest` named `*-conformance-test` is translated to Common Lisp -- ucl's names through the alias, vectors, numbers, booleans and a table of Clojure functions; anything else fails the run -- evaluated per case on SBCL and ECL, and its printed value compared with `expected`. The four hosts run the same assertions as tests, so all six agree. `ucl/run-tests.sh` runs it as a fifth host, `cl` | the cases are written once, where they already are; any vocabulary (the sequence functions too) joins by naming a deftest |
| I40 | D45 per operation (§9.13): JS `fill` is `.fill` (either container); JS `replace` and Dart `replace` copy natively when both vectors are typed (`set`/`setRange`, checked at run time), else loop; `subseq`/`copy-seq` are `.slice`/`.sublist`; `sort`/`stable-sort` expand to one shared bottom-up merge sort with the predicate inlined, except JS with `<` (or `#'<`) on a `fixnum-vector`/`sb53-vector` that is a typed array at run time, which calls the native sort; Dart `fill` and everything on the JVM expand to loops | the measurement: native wins only where it copies memory or sorts typed numbers without a comparator; a run-time `ArrayBuffer.isView` check because a declared `fixnum-vector` may be a plain `Array` (H70) |
| I41 | A sequence function's registry entry applies only to a call through a namespace other than Clojure's own, with every argument: entries are keyed by bare name, and `(count v)` or `(reduce + xs)` in a `ucl/let` body is Clojure's. A curried form is a function value, bound, not tailed | the walker and D35 must not expand Clojure's namesakes; clients always use the alias (D25) |
| I42 | A literal `fn`'s body is written into the loop with its continuation -- the `if` of a test, the `recur` of a fold -- pushed into the tail of its `let`s and `do`s, unless a name the body binds occurs in the continuation's code; then the body stays an expression | written as it is, an `if` test or a `recur` argument ending in `let` is an expression-position `let`: an IIFE per element on Squint (H22) |
| I43 | Two backend operations, `:seqfn :eql` (Squint `===`, ClojureScript `keyword-identical?`, JVM `Util/equiv`, Dart `==`) and `:seqfn :fail` (the hosts' existing `fail` helpers, an `ex-info` on the JVM) | D52's value equality per host; a run-time error without reaching a host's runtime library on Dart |
| I44 | An empty `reduce` without `:initial-value` calls its function with no arguments; for an operator that is decided at compile time: `+` is 0, `*` is 1, `-`, `/`, comparisons, `min` and `max` signal at safety ≥ 1 (nil at 0) | H72; ucl's `min`/`max` are macros on some hosts and refuse no arguments when expanded |
| I45 | Found against the ANSI test suite: `:allow-other-keys` (leftmost wins) admits other keys, whose values are still evaluated; with other keys present its value must be a literal, else a compile-time error; every keyword value is evaluated, a repeated key's too; `'f` and `#'f` (`(var f)`) call the global `f`; a literal nil `:key` is identity | CLHS 3.4.1.4, 3.4.1.4.1, 1.4.1.5, 17.2.1; before, `'identity` was called as a Clojure symbol -- a map lookup, silently wrong |

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
- **Leave inner-loop state in `recur`** (D29) — the flat state machine it forces
  re-checks every condition per step: 1.25–1.6× hand-written on 2762.
- **A flattening compiler** (D29) — a ucl loop macro rewriting nested loops into
  one `loop`/`recur` keeps locals immutable but generates that same slow shape.
- **Volatiles** (D30) — 25× on the JVM, 2× on Squint (H37).
- **`js*` assignment on ClojureScript** (D30) — as fast as Squint, but a hack.
- **Rewriting mutating code into `loop`/`recur` on the JVM** (D30) — a hard
  compiler problem; the cell is already free.
- **Mutability inferred by a walker over any `let` in a `defun`** (D31) — magic,
  and a plain `let` would change meaning silently.
- **Clojure's `[x 0 y 1]` binding syntax for `ucl/let`** (D32) — `ucl/defun`
  already takes Common Lisp syntax and `declare`.
- **`ucl/loop` now, or `do`/`do*`** (D33) — once state lives in variables, a
  loop form buys readability only; no solution needs it yet.
- **A backend operation for `dotimes`** (D33) — Squint's native `for` and
  `loop`/`recur`'s `while` measure the same.
- **Inferring a JVM variable's type from its init** (D34) — `(x 0)` then
  `(setf x 1.5)` would truncate on the JVM and not on JS: different results per
  host.
- **Parameters immutable, rebound with `ucl/let`** (D36) — one rule fewer, but
  the same code; Common Lisp assigns parameters.
- **ClojureDart as a test host only** (D38) — the contract would be checked on
  Dart, but the user wanted Dart submissions too.
- **Bundling a slice of `cljd/core.dart`** (D38) — a Dart-source tree shaker
  over a 45k-line protocol-heavy runtime, for calls fast solutions avoid anyway.
- **A solution that reaches the runtime is simply not built as Dart** (D38) —
  silent partial coverage.
- **LeetCode's typed signature in the solution's `ns` metadata, or from an
  `ftype` declaration** (D39) — LeetCode accepted the untyped wrapper.
- **Methods as functions per struct with an `is`-dispatching generic** (D40) —
  the generic is emitted at a name's first `defmethod` and cannot learn of a
  later struct: two structs sharing a method name would fail on ClojureDart
  only, a partial host.
- **Protocols and `extend-type`, as on the JVM** (D40) — dispatch through the
  ClojureDart runtime, which a submission cannot carry.
- **Hinting `simple-vector` as `List<int>` on Dart** (D42) — a vector of
  strings or nodes would fail the cast.
- **Rewriting `and` for ClojureDart** (D43) — the cost was the `late`, not
  `and`; and `(. a "&&" b)` would evaluate `b` eagerly if ClojureDart hoists it.
- **Patching ClojureDart's compiler at build time** (D43) -- tested and
  submitted code would be identical, but a third dependency on its internals,
  changing everything it generates.
- **All of `LOOP`** (D53) -- its list, package and destructuring clauses need
  data types ucl lacks; "full" would mean full Common Lisp.
- **`ucl/loop` clauses as solutions need them** (D53, D8) -- a boundary nobody
  can predict; each new solution would hit a missing clause.
- **`collect` into an adjustable vector** (D53) -- a clause that compiles but
  means something else than in Common Lisp.
- **Exits by exceptions, everywhere or as a fallback** (D55) -- 1.7× on V8 for
  the `try` alone, 26–40× per exit on Dart (§9.12); as a fallback, identical
  code with a cost decided by where an exit happens to sit.
- **`ucl/loop` alone establishing a block, or only the innermost loop
  targetable** (D56) -- `ucl/dotimes`, which Common Lisp gives the same block,
  would refuse a `return`: the "some parts work" a subset must avoid.
- **Inferring an `into` accumulator's type** (D57) -- D34's reason: the result
  would depend on what the body assigns.
- **Replacing the original solutions** (D62) -- the user keeps both, so the
  ports are measured against them.

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
  - `with-slots`, `ucl/let` and assignable parameters walk their body without
    expanding macros: a user macro that binds a slot's or variable's name is not
    seen as shadowing it, and an assignment a macro generates is not seen (an
    assigned parameter then stays a local, and the `setf` fails to compile --
    loudly, never silently).
  - Return-position assignment covers `if if-not when when-not cond case do let
    let* loop ucl/dotimes with-slots`; any other init (`try`, `and`, a user
    macro) is bound as a value -- an IIFE on Squint where that host needs one.
  - A `ucl/let` in expression position is still a `let` there: an IIFE on
    Squint. Bind it as an init of an enclosing `ucl/let` instead.
  - `ucl/defun`/`ucl/defmethod` accept required parameters only, so "assignable
    `&optional`/`&aux` parameters" (D36) has nothing to apply to yet.
  - LeetCode 19's `bypass` contains one IIFE (a `some->` in a `setf` value), off
    the hot path; `bb build` reports it.
  - The JVM fixtures accept only the all-slots constructor (`(new ListNode 1
    nil)`, not `(new ListNode 1)`).
- **ClojureDart limits** (D38–D43):
  - Tests run ClojureDart's output with `late`; submissions without (D43).
    Proposed upstream; once ClojureDart declares such locals without `late`,
    the bundler step goes.
  - A method must be defined in its struct's namespace (D40).
  - Methods depend on ClojureDart's two passes (H42) and `ListNode` on its
    resolver (H45): undocumented internals, pinned at `247b3c2`.
  - In a Dart submission: no `&optional` constructor, no keyword constructor
    (`make-Foo`), and no `ucl/min`/`ucl/max` as a value -- they compile to
    multi-arity or variadic fns, which are runtime objects (H50). The build
    says so.
  - Clojure's `mod` differs on a negative divisor (H46); no solution relies on it.
  - ClojureDart prints "Useless ^:const" for `most-positive-fixnum`.
- **`prototypes/ucl-cljd`** is the spike that preceded the backend; it runs its
  own copy of the early contract.
- **Squint internals relied on:** `(:var->ident &env)` (H1) and the `defmacro`
  marker (H6). Latest-Squint-only plus the suite makes a break loud.
- **Higher-order functions** (§9.10). A function that takes a `fn` at run time
  needs two things ucl lacks: `function` as a declarable type (else the Dart
  call reaches the ClojureDart runtime, H55) and a `lambda` whose `declare`
  types its parameters (else Dart refuses an untyped parameter stored into a
  typed variable). Even with both it costs on Dart (H56) and, past one
  closure, on V8 (H57). The candidates for raising a solution's level of
  abstraction are expansion-time: Common Lisp's sequence functions (`reduce`,
  `count-if`, `map-into`, ...) inlining a literal `fn` and falling back to a
  function otherwise (D21's pattern), and a `ucl/loop` subset (D33). Neither
  is built; the variable walker must know them, as it knows `ucl/dotimes`
  (above).
- **Sequence functions, v1 limits.** Keyword arguments must be literal keywords
  (a macro decides at expansion which it was given), and so must
  `:allow-other-keys` when other keys are given (I45); a `:key` that is nil
  only at run time fails as a call of nil. `:start` greater than
  `:end` is not signalled -- the range is empty -- while CLHS requires an
  error; an `:end` past the length is signalled by `elt` at safety ≥ 1. A
  curried form inlined into a predicate is a loop in an `if` test: an IIFE on
  Squint (D51). Not built yet: everything after the query slice (D44), with
  the native operations of I40.
- **Compiler passes** (D51). A sequence function or a `ucl/loop` in
  expression position is an IIFE on Squint (§9.11). A pass over a `ucl/defun`
  body could compute it into a temporary before its statement -- lifting with
  it whatever that statement evaluates first, and stopping at a branch or an
  unexpanded user macro. Deferred: kept simple until a solution shows an IIFE
  on a hot path, then designed once for both vocabularies.
- **`ucl/loop` and blocks** (D53–D62):
  - Not built yet: hash-table iteration (`being the hash-keys`, D58),
    `loop-finish`, and parallel stepping (`for … and …`). Each is rejected
    with a message saying so.
  - The cost of an exit through a nested loop (one flag test per outer
    iteration) and of a hash-table iteration step on each host is reasoned,
    not measured; the ports measure both (D62).
  - An exit is static only (D55): `(return x)` inside an `fn`, an argument, a
    binding's init or a test does not compile; nor inside `and`/`or`,
    `when-let` and the other Clojure forms the block compiler does not know
    (I25). Exit from a statement, or test with `thereis`/`always`/`never`.
  - `across` waits for strings and characters in the contract.
  - Hash-table iteration order differs on the JVM (D58).
  - More conformance cases: the ANSI test suite (Paul Dietz;
    ansi-test.common-lisp.dev, MIT-style licence) has 743 LOOP tests with
    their expected values, by CLHS section; those within D53 -- most of
    `loop10.lsp`'s 101 numeric accumulations -- could be imported as
    conformance cases.
- **No JMH.** JVM numbers rest on simple shapes.
- **The repository split** (`ucl`, `ucl-jvm`, `ucl-cljs`, `ucl-squint`) is laid
  out, not performed.

## 14. Validation: ClojureDart, a fourth host

*Superseded by the backend (D38–D43): ClojureDart is now a full host. Kept as
the record of the question the spike answered.*

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
| §9.2: "`forv` adds `dotimes`' binding. That is the 12–26%." | Squint's `dotimes` compiles to a native `for` that measures the same as `loop`/`recur` (§9.8). Whatever made `forv` slower, it was not that binding; the cause is unmeasured. |
| Variables grilling: "the contract can reject a `ucl/loop` in expression position" | A macro does not know its position; the rule can only be documented. |
| Variables grilling: "assignable parameters include `&optional` and `&aux`" (D36) | `ucl/defun`/`ucl/defmethod` take required parameters only (§13). |
| v1's `defmethod` with a declared `fixnum` parameter | Put `^long` into the protocol signature; every call threw (H38). No solution declared one; the variables suite found it. |
| The first variables kernel of 2762 | Still had one IIFE: `(setf maxt (loop …))`. Found by the I12 check; fixed by I10. |
| ClojureDart grilling: "with methods inside the class, a design-problem submission needs no wrapper" | The constructor still does: LeetCode calls `NumArray(nums)`, and the class's own constructor takes the slots. The wrapper delegates (D39). |
| ClojureDart grilling: "the 2762 Dart build is 20–38% slower than hand-written -- a problem" | Locally, yes; on LeetCode both beat 100% (473 against 487 ms). Its Dart runtime is mostly the harness. |
| "ClojureDart's `and` costs ≈18% under the JIT" (H48, recorded as an open item) | The `late` on the local it forks into did (H54); every `if` in expression position paid it. Measured by removing one difference at a time, then fixed (D43). |
| The prototype's typed `dotimes` counter | Overloaded `:hint`'s parameter count with `:local`: the JVM backend would have thrown, and Clojure refuses a hint there anyway. Became `:local-hint` (I20) before it shipped. |
| Before the probe: "a function V8 inlines -- one closure, one call site -- costs close to nothing" | Only when the closure assigns nothing: one that assigns `ucl/let` variables costs 2–7× even inlined (§9.10, H58). |
| `ucl/loop`'s first suite: eight of 58 expected values, written by hand | Wrong -- `for i from 10 above 1 by 3` sums 21, not 22; `return it` returns the test's value `T` -- and the implementation right. SBCL and ECL, running the same cases (I29), said so before any of it was committed. |
| D55 as designed: "an exit inside an inner Clojure `loop` is a compile-time error" | Any loop can be left statically: it ends by not recurring, with a flag when code follows it (I25). Found while implementing; the restriction was never needed. |
| `ucl/loop` grilling: "`minimize p of-type fixnum into lo`" | The type follows `into`: `minimize p into lo of-type fixnum`. SBCL refused the first; found by running §4.3's examples on SBCL and ECL before writing them down. |

The pattern is unchanged from `setf`: every serious error was an inference made
where a compile or a measurement would have answered the question.

## 16. Glossary

The language of `ucl`. A term is defined here once; the sections above use it
in this sense. Implementation belongs in the sections, not here.

### Hosts and the contract

**Contract**:
The host-agnostic vocabulary and its meaning; every host implements all of it.
_Avoid_: core, spec

**Host**:
A language a ucl program runs on: Squint, ClojureScript, Clojure (JVM) or
ClojureDart.
_Avoid_: platform, target, dialect

**Submission**:
The standalone file a host's build produces for LeetCode: JavaScript from
Squint, Dart from ClojureDart. It contains the solution and nothing of ucl's
macro time, test kit or a host's runtime library.
_Avoid_: bundle, build, artifact

**Backend**:
A host's implementation of the contract.
_Avoid_: driver, adapter

### Bindings

**Variable**:
A name bound by ucl that the program can assign with `setf`, `incf` or
`decf`: one bound by `ucl/let` or `ucl/let*`, by a `ucl/loop` `with` or
`into` clause, or a `ucl/defun` / `ucl/defmethod` parameter.
_Avoid_: mutable local, var, cell, atom

**Local**:
A name bound by Clojure itself (`let`, `loop`, `fn`), or a counter ucl steps
itself (`ucl/dotimes`' counter, a `ucl/loop` `for` name); it can never be
assigned.
_Avoid_: variable (for these), immutable variable, iteration variable (the
CLHS's name for a `for` name)

**Place**:
A form that names a storage location `setf` can write: a variable, or an
`elt`, `gethash` or `slot-value` form.
_Avoid_: lvalue, reference, accessor

**Block**:
A named stretch of code that `return-from` leaves with a value: one
established by `block`, by `ucl/loop` and `ucl/dotimes` (named `nil`, which
`return` leaves), or by `ucl/defun` / `ucl/defmethod` (named after the
function).
_Avoid_: scope, label

**Exit**:
Leaving a block before its end with `return` or `return-from`.
_Avoid_: break, early return, non-local return

### Positions

**Statement position**:
Where a form's value is discarded: any form of a body but the last.

**Return position**:
Where a form's value becomes the value of the enclosing form: the last form of
a body, or either branch of an `if` in return position.
_Avoid_: tail position

**Expression position**:
Where a form's value is used by another form: an argument, or the init of a
`let` binding.

### Sequences

**Sequence**:
A vector: Common Lisp's sequence is a list or a vector, and ucl has no lists.
_Avoid_: seq (Clojure's lazy sequence), collection

**Sequence function**:
One of Common Lisp's functions over sequences -- those of CLHS Chapter 17 and
`every`, `some`, `notany`, `notevery` -- as ucl provides them.
_Avoid_: seq fn, collection function, higher-order function

**Curried form**:
A sequence function called with every argument but its sequence; it is a
function of that sequence.
_Avoid_: partial application, section
