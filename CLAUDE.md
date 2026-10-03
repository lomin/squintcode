# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

LeetCode solutions written once in `.cljc` and run on four hosts:

- **Squint** — compiles to the standalone JavaScript submitted to LeetCode
- **ClojureDart** — compiles to the standalone Dart submitted to LeetCode
- **Clojure (JVM)** — a test host
- **ClojureScript** — a test host and REPL

Solutions are written against **`ucl`** ("Uncommon Lisp", `ucl/`): a
Common Lisp-shaped, host-agnostic contract for high-performance code. A solution
has one plain `(:require [ucl.api :as ucl])` and **no reader conditionals**.
`ucl/README.md` is the design of record — read it before changing `ucl/`.

Build and test are orchestrated by **Babashka** (`bb.edn`, `bb/tasks/lc.clj`).

## Commands

```bash
bb test              # everything: ucl suite, then solutions on Squint -> Clojure -> ClojureScript -> ClojureDart
bb test-ucl          # the ucl library suite on all four hosts (ucl/run-tests.sh)
bb test-squint       # solutions on Squint, bundled exactly as a submission
bb test-clj          # solutions on Clojure (JVM)
bb test-cljs         # solutions on ClojureScript -- any compiler WARNING fails the run
bb test-cljd         # solutions on ClojureDart (cljd.test on `dart test`)

bb build             # every problem -> out/<problem>.js and out/<problem>.dart (LeetCode-ready)
bb build-one <name>  # one problem, e.g. bb build-one fizzbuzz
bb clean             # remove out/ and ClojureScript caches

ucl/run-tests.sh [jvm|cljs|squint|cljd]   # ucl suite, optionally one host
bb ucl/ansi/translate.clj                 # regenerate test/ucl/ansi_sequences_test.cljc from the ANSI test suite
```

REPLs: `clj -M:jvm` (Clojure) or `clj -M:cljs:repl` (ClojureScript). The
`:jvm` / `:cljs` aliases put that host's ucl backend on the path — never use
both at once.

### Clojure REPL Evaluation

The command `clj-nrepl-eval` is installed on your path for evaluating Clojure code via nREPL.

```bash
clj-nrepl-eval --discover-ports
clj-nrepl-eval -p <port> "<clojure-code>"
clj-nrepl-eval -p <port> --timeout 5000 "<clojure-code>"
```

The REPL session persists between evaluations. Always use `:reload` when
requiring namespaces to pick up changes.

## Writing a solution

```clojure
(ns squintcode.lc-930-binary-subarrays-with-sum
  (:require [ucl.api :as ucl]))

(ucl/defun numSubarraysWithSum (nums goal)
  (declare (type fixnum-vector nums))          ; LeetCode's int[]: a vector of fixnums
  (let [n    (ucl/length nums)
        freq (ucl/make-array (inc n) :element-type 'fixnum)]   ; Int32Array / int[]
    (ucl/setf (ucl/elt freq 0) 1)
    (loop [i 0 running-sum 0 result 0]
      (if (< i n)
        (let [running-sum (+ running-sum (ucl/elt nums i))]
          (ucl/incf (ucl/elt freq running-sum))
          (recur (inc i) running-sum result))
        result))))
```

Rules:

- **Always use the alias** (`ucl/elt`, `ucl/min`), never `:refer` — names that
  clash with `clojure.core` (`min`, `max`, `make-array`) break on Squint if referred.
- **Loops are `loop`/`recur` or `ucl/dotimes`** with `ucl/elt` and `ucl/length`.
  `aloop`/`forv` no longer exist; they were measured slower.
- **A loop's state lives in variables** (`ucl/let`, `ucl/let*`, or a parameter
  the body assigns): `ucl/setf`/`ucl/incf` assign them, so an inner loop is a
  statement — `(loop [] (when test … (recur)))` is a "while". Bind a loop's
  value with `ucl/let`, never `let`: a `loop`/`let` in expression position is an
  IIFE on Squint (8×); `ucl/let` turns it into a statement. Assigning a plain
  Clojure local is a compile-time error.
- Use `ucl/min`/`ucl/max`, not `clojure.core`'s: Squint's are slow runtime calls.
- Numeric equality: `==`, not `=` (Squint's `=` is a deep-equality call).
- **A solution must compile to plain Dart**: nothing in it may call the
  ClojureDart runtime, or `bb build` fails naming the call. Fine: arithmetic,
  `quot` `rem` `mod` (Dart's `%`: wrong for a negative divisor), comparisons, bit ops, `and`/`or`/`not`, `cond`/`case`,
  `loop`/`recur`, local `fn`s, ucl's vocabulary. Not in a solution: `=`, `str`
  (use `ucl/princ-to-string`), `count`, `nth`, `get`, keyword or vector
  literals as values, seq and collection fns, multi-arity fns. Tests may use
  anything. A result LeetCode types `List<String>` needs `:element-type 'string`
  (Dart checks generic types).
- Vocabulary (exact Common Lisp names and argument order): `make-array`, `elt`,
  `length`, `vector-push-extend`, `make-hash-table`, `(gethash key table [default])`,
  `(slot-value obj 'slot)`, `setf`, `incf`, `decf`, `min`, `max`,
  `(let ((var init)…) (declare …) …)`, `let*`, `(dotimes (i n [result]) …)`,
  `(loop clause…)` (Common Lisp's LOOP for vectors and numbers: `for … across`,
  `for … from/below/to/by`, `sum`/`count`/`maximize`/`minimize` `[into v
  of-type T]`, `when`/`unless`/`else`, `while`/`until`/`repeat`, `always`/`never`/
  `thereis`, `with`, `finally`; no list clauses), `(block name …)`,
  `(return-from name [v])`, `(return [v])` (`dotimes` is a block
  nil, `defun` one named after it; an exit compiles only in statement or return
  position), `defun` + `(declare (type …))`, `defstruct` (BOA constructors, `&optional`, `&aux`),
  the sequence functions `count` `find` `position` (each with `-if`, `-if-not`),
  `reduce`, `every`, `some`, `notany`, `notevery`, `fill`, `replace`, `copy-seq`,
  `subseq`, `reverse`, `nreverse`, `sort`, `stable-sort` (keywords as in CLHS; a literal
  `fn` argument is inlined; called short of its sequence, a function of it),
  `defmethod`, `with-slots`, `princ-to-string`, `most-positive-fixnum`,
  `double-float-positive-infinity`.
  Types: `fixnum` (32-bit everywhere), `(signed-byte 53)`, `fixnum-vector`,
  `sb53-vector`, `simple-vector`. Declare a variable's type: on the JVM an
  undeclared one is boxed.
- Declare LeetCode's `int[]` input `fixnum-vector` -- it is a vector of fixnums,
  whatever container the host passes (a JS `Array`, a Dart `List<int>`): Dart
  types its elements `int`, the JVM reads an `int[]`. `simple-vector` is for
  any other input (strings, nodes). Details: `ucl/README.md` §4–§8, terms in
  its glossary (§16).
- Design problems (`NumArray`, `LRUCache`): `ucl/defstruct` + `ucl/defmethod`,
  in the same namespace (ClojureDart puts the methods inside the class).
  A method name that clashes with `clojure.core` (`get`, `next`, `pop`) needs
  `(:refer-clojure :exclude [...])`.
- LeetCode's own classes (`ListNode`, `TreeNode`): `(new ListNode 0 head)`,
  bare, no require; read and write fields with `ucl/slot-value`. Pass every
  constructor argument.

## Writing a test

```clojure
(ns squintcode.twosum-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.leetcode]                    ; only if the solution uses ListNode/TreeNode -- before the solution
            [ucl.api :as ucl]
            [squintcode.twosum :refer [twoSum]]))

(defn arr [v] (ucl/make-array (count v) :element-type 'fixnum :initial-contents v))   ; LeetCode's int[]

(deftest twosum-test
  (testing "LeetCode example"
    (is (= [0 1] (vec (twoSum (arr [2 7 11 15]) 9))))))
```

`ucl.test` is the same vocabulary on every host (clojure.test underneath on
JVM/ClojureScript, ucl's own runner on Squint), so test files need no reader
conditionals either. `signals-error?` tests that a form throws. Call design-problem
constructors as functions — `(NumArray nums)` — in tests: on the JVM `new` cannot
run a BOA constructor.

## Architecture

```
src/squintcode/*.cljc        solutions
test/squintcode/*_test.cljc  their tests
ucl/                         the library -- see ucl/README.md
  shared/ucl/contract.cljc     all host-agnostic logic; names no host
  shared/ucl/loop.cljc         ucl/loop (Common Lisp's LOOP), a registry vocabulary
  backends/{jvm,js,cljs,squint,cljd}/  per-host emitters
  testkit/{jvm,cljs,squint,cljd}/  ucl.test + LeetCode fixtures (never in a submission)
  cljd-project/                the ClojureDart project template builds are made from
  test/ucl/                    the library's own suite (+ test-jvm/: expansion errors)
bb/tasks/lc.clj              test and build tasks
bb/tasks/dart.clj            bundles ClojureDart output into a Dart submission
```

**Backend selection is by source root**: every backend declares the same
namespace `ucl.api`; each host's build puts exactly one backend on its path
(`deps.edn` aliases `:jvm`/`:cljs`, `squint.edn` paths). Two on one path:
the first silently wins.

### Squint pipelines

- **Tests** (`bb test-squint`): every source, test and ucl file compiled in ONE
  Squint invocation (Squint needs that to load macros), a runner importing every
  test module bundled with esbuild exactly as a submission is, run with node.
- **Submissions** (`bb build`): compiled in `out/build/` with its own
  `squint.edn` carrying `:ucl/safety 0` (no runtime checks), no test kit;
  `esbuild --format=esm --bundle --tree-shaking=true`, then the trailing
  `export {…}` is stripped → `out/<problem>.js`. The build fails if any ucl
  macro-time or test-kit code reached the bundle, and warns on any IIFE.
- **Must** be `--format=esm`, not `--format=iife`: LeetCode does not accept the
  `(() => { … })()` wrapper.
- Squint is pinned to the latest release (0.14.211); 0.12.x is not supported.

### ClojureDart pipelines

ClojureDart (pinned `247b3c2`, Dart SDK 3.13.5 at `$DART_SDK`, default
`~/.local/dart-sdk`) builds a project: `ucl/cljd-project/` is the template,
the sources are copied into its `src/` (tools.deps no longer takes paths
outside a project).

- **Tests** (`bb test-cljd`): `out/cljd-test`, `cljd.test` run by `dart test`.
- **Submissions** (`bb build`): `out/cljd-build` at `-J-Ducl.safety=0`, then
  `bb/tasks/dart.clj` tree-shakes each solution's Dart: it fails on any
  reference into `cljd/core.dart`, leaves `ListNode`/`TreeNode` bare for
  LeetCode's, declares ClojureDart's forked locals without `late` (a run-time
  check that costs ≈18% in hot loops), and adds an untyped `class Solution`
  (one method per function) or, for a design problem, a class named like the
  struct that wraps it. Every submission must then pass `dart analyze`.
- Compile errors print their cause chain (`ucl/cljd-project/report.clj`);
  `DYNAMIC WARNING` marks dynamic member access -- expected in tests, a missing
  declaration in a solution.

### Safety

Tests run at safety 1: out-of-bounds `elt`, a store outside `fixnum`, `(incf nil)`
and similar signal, as in SBCL. Submissions run at safety 0. Set per build:
Squint `:ucl/safety` in `squint.edn`, ClojureScript compiler option
`:ucl/safety`, JVM `-Ducl.safety=0`.

## Gotchas

- **Reader conditionals** (only in host-specific files — never in solutions or
  `ucl/shared`): `:squint` must come **before** `:cljs`, since Squint also
  matches `:cljs`. ClojureDart's macro host reads `:cljd/clj-host`; its Dart
  side reads `:cljd` only.
- **ClojureDart in `ucl/shared`**: define every fn before its first use
  (ClojureDart's `declare` does nothing on its macro host), and tag every fn a
  macro calls `^:macro-support`. ClojureDart's `ucl.api` may hold macros only.
- **Squint's macro interpreter (SCI)** limits code in `ucl/shared` and
  `ucl/backends/js`: no syntax-quoted reader conditionals in macro bodies; no
  quoted symbol inside `or` (`(or x 't)` fails — use `if`); and the Squint backend
  file must contain the word `defmacro` or Squint will not load its macros
  (a comment carries it; `ucl/run-tests.sh` checks).
- **Top-level defs in `ucl/shared` and `ucl/backends/js` must be literals.** A
  `def` of a quoted list or a set compiles to a call that tree shaking keeps, so
  it would ride into every submission. Use a function.
- **ClojureScript treats a macro that fails to resolve as a warning**, not an
  error; that is why the test tasks fail on any warning.
- **JVM fallback warnings** (`ucl WARNING: elt on x of unknown type`): the
  receiver's type is unknown, so the JVM compiles a dynamic path. Expected in
  test code; in a solution, add a `(declare (type …))`.
