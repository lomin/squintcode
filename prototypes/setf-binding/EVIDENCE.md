# EVIDENCE — `setf!` backend binding probes

**PROTOTYPE — throwaway. Not production, not wired to the real build.**
Date: 2026-10-02. Branch: `main`. Nothing committed; root deps and `src/`/`test/` untouched.

**Decided so far:** Q9 = `setf!` is a macro. Q12 = backend selection is explicit per target's build
setup. Q13/Q14 = `:field` writes specialize at expansion time when the type is statically
identifiable. Q15 = `PLACES` is closed by design. Q16 = the indexed place is `elt`. Q17 = a place name
denotes semantics. Q18 = derived forms are `incf!`/`decf!` only. Q19 = no type vocabulary ships.
Q20 = `:field` on the JVM is decided once at backend authoring time. See §10.

---

## 1. What this directory contains

```
prototypes/setf-binding/
  EVIDENCE.md          <- this file
  README.md            <- how to re-run
  probe1/              <- compiler probes: namespace + macro resolution matrix
  probe3/              <- Design B end-to-end: one shared contract, three backends, three real targets
  probe4/              <- robustness: incomplete backend, mock 4th host, path collisions, many shapes
  setf-binding.html    <- interactive logic prototype over the findings
```

## 2. Environment actually used (verified, not assumed)

| Tool | Version |
|---|---|
| Node | v24.16.0 |
| npm / npx | 12.2.0 |
| Java | openjdk 21.0.11 |
| Clojure CLI | 1.12.5.1664 |
| Babashka | 1.12.218 |
| **Clojure** | **1.12.0** (`probe3/deps.edn`) |
| **ClojureScript** | **1.12.42** |
| **Squint** | **0.14.210** (reused from `.probe-squint/node_modules`; the project itself is still on 0.12.193) |

Probes 3 and 4 carry their own `deps.edn`; nothing in the repo's build changed.

> Gotchas that cost real time: Squint always writes to `:output-dir` from `squint.edn` and ignores
> ad-hoc overrides; Squint must be handed **all** source files in one invocation; `cljs.main -t node`
> emits a bootstrap-only top file while the compiled namespace lives under `-d <dir>/<ns path>/`.

## 3. The question

Can a **shared, host-agnostic namespace** own the logic and API, while each host supplies only its own
emission, such that the portable application code is byte-identical across Clojure / ClojureScript /
Squint, and adding a new host touches **nothing** in the shared library?

## 4. Probe 1 — namespace and macro resolution matrix

All cases are one namespace name supplied by different file extensions, consumed by a shared
`.cljc`/`.cljs` file.

| # | Shape | Clojure / JVM | ClojureScript 1.12.42 | Squint 0.14.210 |
|---|---|---|---|---|
| 1 | ns in `.clj` only, has runtime var | ✅ loads `:from-clj` | ❌ `could not locate nb2/api.cljs, nb2/api.cljc` | ⚠️ `.clj` **ignored**; emitted `a.where()` as a runtime call |
| 2 | ns in `.clj` only, **macro-only** | ✅ | ❌ `No such namespace: cx.api` | ❌ (file not read) |
| 3 | ns in `.cljc` only | ✅ | ✅ | ✅ |
| 4 | ns in `.cljs` only | ❌ ignored | ✅ | ✅ |
| 5 | ns in **both** `.clj` + `.cljs` | uses `.clj` | uses `.cljs` | compiles **both**; runtime exports come from `.cljs` |
| 6 | **Implicit** macro load, `.cljc` macros, called **through an alias** | n/a | ❌ warning only → `mx.api.where.call(null)` | ✅ macro inlined at compile time |
| 7 | **Implicit** macro load, `.clj` macros | n/a | ❌ warning → `a.where()` runtime call | ❌ (`.clj` not read) |
| 8 | **Implicit** macro load, `$macros` convention (`api$macros.clj`) | n/a | ❌ `Use of undeclared Var mx2.api/where` | n/a |
| 9 | **Explicit** `:require-macros` / `:refer-macros` | n/a | ✅ `"CY-macro-via-api.cljc"` inlined | ✅ `"CY-macro-via-api.cljc"` inlined |

Row 6 was re-tested in probe 3/4 and is **partially wrong as originally recorded** — see **F7**.

---

## 5. Probe 3 — Design B, end to end on three real targets

### Shape

```
probe3/
  shared/setf/contract.cljc        <- host-agnostic. No host named anywhere in the file.
  app/portable/app.cljc            <- one portable app, identical logic on every target
  backends/jvm/setf/api.clj        <- (:clj path only)
  backends/cljs/setf/api.cljc      <- (:cljs path only) macro half
  backends/cljs/setf/api.cljs      <- (:cljs path only) runtime half
  backends/sq/setf/api.cljc        <- (:squint path only) single .cljc
```

All three backends declare the **same namespace, `setf.api`**, in different files. Backend selection
is done purely by which source root a target's build puts on its path. The shared library never
mentions a host, a file extension, or a build tool.

The whole host-specific surface is one map:

```clojure
{:indexed {:read  (fn [refs srcs] ...)      :write (fn [refs srcs v] ...)}
 :keyed   {:read  (fn [refs srcs] ...)      :write (fn [refs srcs v] ...)}
 :field   {:read  (fn [refs srcs] ...)      :write (fn [refs srcs v] ...)}}
```

`refs` are the generated binding symbols; `srcs` are the original argument forms, handed over so a
backend can recognise its own constructors or read a type hint without the shared library knowing
what any host is.

The shared library owns everything else: the place vocabulary (`elt` / `gethash` / `get!`), each
place's argument kinds (runtime-evaluated vs compile-time slot), argument binding so every runtime
argument is evaluated exactly once left-to-right, arity checking, unknown-place rejection, incomplete-
backend rejection, and the derived `incf!`.

### Result — all three targets produce the identical value

```
Clojure/JVM   APP-RESULT [100 7 42]
ClojureScript APP-RESULT [100 7 42]
Squint        APP-RESULT [100 7 42]
```

### Recorded emission

**ClojureScript 1.12.42** (`out-cljs/portable/app.js`, `:optimizations :none`) — no runtime dispatch,
no allocation, direct operators:

```js
var t0_593 = a;  var t1_594 = (1);  var v_595 = (99);
(t0_593[t1_594] = v_595);
var t0_596 = a;  var t1_597 = (1);  var v_598 = (1);
(t0_596[t1_597] = ((t0_596[t1_597]) + (1)));
var t0_599 = m;  var t1_600 = "k";  var v_601 = (7);
t0_599.set(t1_600, v_601);
var t0_602 = o;  var v_603 = (42);
(t0_602.x = v_603);
```

**Squint 0.14.210** (`out/portable/app.mjs`) — same shape:

```js
const t0_4 = a_1;  const t1_5 = 1;  const v_6 = 99;
(t0_4[t1_5] = v_6);
const t0_7 = a_1;  const t1_8 = 1;  const v_9 = 1;
(t0_7[t1_8] = (t0_7[t1_8] + 1));
const t0_10 = m_2; const t1_11 = "k"; const v_12 = 7;
t0_10.set(t1_11, v_12);
const t0_13 = o_3; const v_14 = 42;
t0_13.x = v_14;
```

**Clojure/JVM** (macroexpansion):

```clojure
(setf! (elt a 1) 99)   => (let [t0 a t1 1 v 99] (.set ^java.util.List t0 t1 v) v)
(incf! (elt a 1))      => (let [t0 a t1 1 v 1] (.set ^java.util.List t0 t1 (+ (.get ^java.util.List t0 t1) 1)) v)
(setf! (gethash m "k") 7)=> (let [t0 m t1 "k" v 7] (.put ^java.util.Map t0 t1 v) v)
(setf! (get! (api/make-obj) x) 42) => (let [t0 (api/make-obj) v 42] (set! (.x ^Point t0) v) v)
(setf! (get! o x) 42)  => (let [t0 o v 42] (clojure.lang.Reflector/setInstanceField t0 "x" v) v)
```

The `^java.util.List` / `^java.util.Map` hints are supplied by the **backend** — client code never
carries a type. The `:field` case is decided once at backend authoring time (Q20): resolved → real
`PUTFIELD`, unresolved → Reflector.

### The portable require block — the entire per-target cost

```clojure
(ns portable.app
  (:require
   #?(:clj  [setf.api :as api :refer [setf! incf!]]
      :cljs [setf.api :as api :refer-macros [setf! incf!]])))
```

Two reader-conditional branches, and **no `:squint` branch at all** (F10). This is the complete,
measured price of making `setf!` a macro. It is not a host *registry* — nothing in it says which
emitter to use, and an unknown host still compiles; it is only the Clojure-family quirk that
macros are referred with `:refer` and the JS family with `:refer-macros`.

---

## 6. Probe 4 — robustness

| Case | Result |
|---|---|
| **Complete mock 4th host** | Works. Primitives `fetch`/`store!`/`lookup`/`assoc!`/`slot`/`put-slot!` appear nowhere else. `shared/setf/contract.cljc` byte-identical to probe 3's copy. |
| **Incomplete backend** (`:field` pair omitted) | Rejected at expansion: ``setf!/contract: backend does not implement :write for kind :field. This backend is INCOMPLETE — it is not compatible.`` |
| **No backend on the path** | Hard, immediate, well-named failure: `Could not locate setf/api__init.class, setf/api.clj or setf/api.cljc on classpath.` |
| **Unknown place** | ``setf!/contract: unknown place `frobnicate`. Known: (elt gethash get!)`` |
| **Wrong arity** | ``setf!/contract: `elt` expects 2 arguments, got 1`` |
| **Slot argument** | Spliced unevaluated; no binding is generated for it, so `(get! o (do-side-effect))` never evaluates `do-side-effect`. |
| **Cold load** | Every run above is a cold JVM / cold Node process. Nothing is cached between them. |
| **Two backends of the same ns on one classpath** | ⚠️ **The first path entry silently wins.** No warning, no error. Reversing the path order reverses the backend. See **F11**. |

Mock-4th-host emission, same four portable use sites:

```clojure
(setf! (elt a 1) 99)    => (let [t0 a t1 1 v 99] (store! t0 t1 v) v)
(incf! (elt a 1))       => (let [t0 a t1 1 v 1] (store! t0 t1 (+ (fetch t0 t1) 1)) v)
(setf! (gethash m "k") 7)=> (let [t0 m t1 "k" v 7] (assoc! t0 t1 v) v)
(setf! (get! o x) 42)    => (let [t0 o v 42] (put-slot! t0 (quote x) v) v)
```

### Requirement 4 — one accessor, many structures, one emission

JVM (`shapes.clj`):

```
ArrayList        15      ; (.set l 1 10) then (+ (.get l 1) 5)
LinkedList       25      ; the same pair, `incf!` included
HashMap          1       ; (.put m "k" 1)
TreeMap          2       ; (.put m "k" 2)
Point.x          30      ; public field
Rectangle.width  99      ; a different class, a different field
```

ClojureScript (`shapes.cljs`) — `Array` and `Uint32Array` both go through the identical
`t0_595[t1_596] = v_597;` emission.

Both emitters for `:indexed` on the JVM are `java.util.List` interface calls with a type hint, so
`setf!` and `incf!` reach the same structures. An earlier version read with `elt`/`nth` and wrote with
`.set`, which made `incf!` unreachable on `LinkedList` for no reason — see **F13**, now resolved.

---

## 7. Findings

**F1 — `.cljc` is the only macro-namespace format all three targets read.**
Squint does not read `.clj` at all.

**F2 — CORRECTED by F7. Do not use this one.**

**F3 — ClojureScript's macro-resolution failure is a WARNING, not an error.**
`WARNING: Use of undeclared Var mx.api/where`, compilation continues, and the output is
`mx.api.where.call(null)`. A portable build can go green and fail only at run time. Any macro-based
design must make the explicit form mandatory on CLJS.

**F4 — A namespace may be split `.cljc` (macros) + `.cljs` (runtime).**
Accepted by both ClojureScript and Squint. This is the shape the two JS-family backends use.

**F5 — Macro visibility is orthogonal to A-vs-B backend binding.**
Both designs make `setf!` a macro, so where the backend is selected does not change the consumer's
problem. This is why compilation-scoped injection was dropped without being built.

**F6 — Q12 cannot be decided before Q9.** Still true; see §9.

**F7 — Squint resolves macros *reached through an alias*, but not *bare* macro symbols.**
Measured with the same file shape on 0.14.210:

| require clause | call | Squint 0.14.210 |
|---|---|---|
| `(:require [setf.api :as api])` | `(api/setf! (elt a 1) 99)` | ✅ expanded |
| `(:require [setf.api :as api])` | `(setf! (elt a 1) 99)` | ❌ emitted `setf_BANG_(elt(a_1, 1), 99)` |

ClojureScript expands neither without `:refer-macros`. So probe 1's row 6 was right about the
*alias-qualified* case and wrong as a blanket claim.

**F8 — Squint emits duplicate `import * as` when a namespace appears in two `:require` entries.**
`(:require [setf.api :as api] [setf.api :refer-macros [setf!]])` produces the same
`import * as setf_DOT_api from './setf/api.mjs';` twice. That is **syntactically invalid ESM** and
esbuild rejects it (`The symbol "setf_DOT_api" has already been declared`). One `:require` entry per
namespace is mandatory.

**F9 — One entry — `(:require [setf.api :as api :refer-macros [setf! incf!]])` — works on BOTH
ClojureScript and Squint**, and makes both `api/setf!` and bare `setf!` available. This is what
collapses the portable require to two branches.

**F10 — Squint activates the `:cljs` reader feature.**
`#?(:clj … :cljs …)` therefore covers Squint too; no `:squint` branch is needed. (Ordering `:squint`
first is still correct whenever a `:squint` branch *does* exist, because it is first-match.)

**F11 — Two same-namespace backends on one classpath: the first path entry silently wins.**
No warning, no error, and the outcome depends on ordering that is invisible in most build configs.
Namespace-scoped selection is safe **only** if the source roots are genuinely mutually exclusive per
target — this is a build-configuration invariant, not something the library can enforce.

**F12 — Failure modes are loud and precise in the right direction.**
Missing backend → immediate namespace-resolution error. Incomplete backend → contract error naming
the exact kind and op. Unknown place → error listing the known places. These are all macro-expansion
time, i.e. build time.

**F13 — `setf!` and `incf!` have different reach on the same host. RESOLVED.**
`setf!` needs only `:write`; `incf!` needs `:write` *and* `:read`. A host whose `:read` emitter is
narrower than its `:write` emitter silently gives `incf!` a smaller set of usable structures. On the
JVM this happened: `(.set coll i v)` is a `java.util.List` method and works on `LinkedList`, while
`nth` does not. Fixed by making both halves List calls with a hint — now verified, `incf!` works on
`LinkedList` (25). The general lesson stands: **pick a read and a write emitter from the same
family**, or a derived form will quietly be narrower than the one it is derived from.

**F14 — `set!` on a hinted field is a real field store in Clojure, and it needs no MethodHandle.**
`(set! (.x ^Point p) v)` compiles to `PUTFIELD`; measured at 1.25× of a generated-Java direct store
and ~7× faster than Reflector. Without the hint the same form measures 58.7 ns — *worse* than
Reflector — so the type hint is the whole difference. Verified with `*warn-on-reflection*` that both
`(.get ^java.util.List l i)` and `(.set ^java.util.List l i v)` emit direct calls.

**F15 — The type hint is the only portable-code cost of JVM specialization.**
`^java.awt.Point` on a local in a `.cljc` compiles on all three targets, and ClojureScript and Squint
emit byte-identical code with and without it. The hint is ordinary metadata, not a host construct.

### Bugs the prototype hit, recorded because they are instructive

1. `loop` termination: `(if ks …)` on a seq — `(rest [a b])` eventually yields `()`, which is **truthy**
   in Clojure, so the loop spun forever. Symptom was `OutOfMemoryError` during macroexpansion, i.e.
   the worst possible place for an infinite loop.
2. `(str "t" n)` produces a **String**, and `list` emits a string literal, not a symbol. `t0` came out
   as `"t0"`. Symptom: `Unable to resolve symbol`.
3. A `let` binding vector of vectors — `[[t0 a] [t1 1]]` — destructures; it does not bind two pairs.
   Symptom: `Unable to resolve symbol: t0`.

All three failed loudly within one expansion. None produced a silently wrong program. That is itself
evidence for macro-time (syntactic) resolution: the failure surfaces at build time, not at run time.

---

## 8. Advantages / costs observed

**Namespace-scoped explicit selection (Design B, as built).**

*Advantages, all measured:*
- The shared library names no host. `grep` for `clj|cljs|squint` in `shared/` returns nothing.
- A new host is a new directory. The mock 4th host shares no primitive with any real host and needed
  **zero** edits outside its own directory.
- Emission is direct operators on all three targets — no dispatch, no allocation, no wrapper.
- Errors are build-time and name the exact missing thing.

*Costs, all measured:*
- One `#?(:clj … :cljs …)` in every consumer's `ns` form (F9). Two branches; it does not grow with the
  number of hosts, but it is a Clojure-family artifact inside otherwise portable code.
- A namespace may be split `.cljc` + `.cljs` on the JS family (F4).
- Backend selection is a **classpath/source-root invariant** with no runtime enforcement (F11).
- `:field` on the JVM falls back to `Reflector/setInstanceField` (43 ns) unless the receiver's type
  is statically identifiable, in which case the backend emits a `PUTFIELD` (5.9 ns) — Q13/Q14.

**Compilation-scoped backend injection (Design A) — dropped on evidence, not built.**
F5: it does not change the consumer's macro-require problem at all, and it adds hidden global state
to compilation. Strictly worse than what is above.

---

## 9. What is now established, and what is still open

**Established.** Design B works end to end on three real targets with byte-identical portable logic,
produces direct emission everywhere, rejects missing and incomplete backends loudly, and admits a
brand-new host with no change to the shared library. The only thing that leaked into portable code is
a two-branch reader conditional for macro visibility.

**Still not established.**
- **No JMH, and only one field shape.** Probe 5 measured a single public `int` field on one class.
  Boxed fields, `long`/`double`, final fields, private fields, and a cold-JIT profile shaped like an
  actual run are all unmeasured. Do not treat §11 as a benchmark.
- **Q14 is now built** — see the decision log. The contract hands emitters the original argument
  forms; the JVM backend resolves classes from its own constructor table and emits a real
  `PUTFIELD`. The residual Reflector path is deliberate (Q20), not an unfinished edge.
- **Squint 0.12.193 was not tested.** All Squint results are 0.14.210; the project pins 0.12.193.
  F7/F8/F9/F10 must be re-verified there before anything is claimed about the project's own build.
- **`push!` / `pop!` / `rotatef!` and other derived forms untested** beyond `incf!`.
- **Clojure 1.12.0 was the JVM version used**; the repo's own pin may differ.
- **Probe 1's matrix was not re-run** after the `elt` rename; it predates it and is unaffected, but it
  is also unverified on the current toolchain.

## 10. Decision log

**Q9 — DECIDED: `setf!` is a macro (compile-time expansion).**
Chosen after the probe 3/4 evidence, with the macro arm's cost measured (one two-branch reader
conditional in the consumer's `ns` form, which does not grow with the number of hosts — F9/F10)
and the runtime-function arm's cost unmeasured (hot-path dispatch plus loss of static `.-field`
splicing). Q12 is now unblocked.

**Q12 — DECIDED: backend selection is explicit in each target's build setup.**
Namespace-scoped selection is the accepted mechanism: the same namespace `setf.api` lives in a
different file per target, and each target's source root contains exactly one of them. Mutual
exclusion is accepted as a **build-config invariant the shared library cannot enforce** — F11
(collision is silent, first path wins) is a known and accepted cost, not a bug to be fixed. The
alternative — one shared file that branches on the host — is the closed host list requirement 1
forbids.

**Q13 — DECIDED: `:field` writes specialize at expansion time whenever the type is statically
identifiable; otherwise the backend falls back.**
The mechanism was corrected twice by evidence before it was settled, and the final mechanism is the
one nobody guessed up front: plain Clojure `(set! (.x ^Point p) v)`. Measured at 1.25× of a
generated-Java direct store and ~7× faster than Reflector, with no code generation.

**Q14 — DECIDED: the JVM backend resolves the receiver's static type from (a) a type hint at the
use site if present, else (b) the return type of a constructor/factory the backend itself defines,
else (c) Reflector.**
All expansion-time, all inside the JVM backend, shared contract untouched. Portable code stays clean
because (b) covers every object the library itself constructs; the hint is an optional accelerator
for foreign objects. Verified that `^java.awt.Point` metadata is inert on ClojureScript and Squint
(byte-identical emission).

**Q15 — DECIDED: `PLACES` is closed by design.**
The shared library's value is the *contract*, not a place abstraction outside it. A host that needs
a capability the contract does not cover — a slice range, say — is free to write its own code and
does not need this library. There is deliberately no extension point, and no registration mechanism.
This closes the second open list: hosts are open-closed by construction (Q12), places are closed on
purpose.

**Q16 — DECIDED: the indexed place is `elt`, and `nth` leaves the vocabulary entirely.**
`aref` names a host concept ("array reference"); `elt` names the semantics (element at index).
The vocabulary is now `elt` / `gethash` / `get!`.

**Q17 — DECIDED: a place name denotes semantics; each host emits its fastest form with those
semantics.** It does NOT oblige a host to literally call the function it is named after. The JVM
`elt` emitter is `(.get ^java.util.List coll i)` / `(.set ^List coll i v)` — O(1) on ArrayList and
LinkedList — because a List has an O(1) indexed primitive, so that IS `elt` made as fast as this host
can make it. The generic-O(n) caveat applies only to hosts or structures with no O(1) indexed
primitive. Under the literal reading, every indexed assignment on an ArrayList would become O(n).

Consequence, applied: the JVM `:indexed` pair is now symmetric `java.util.List` calls with a type
hint, verified reflection-free under `*warn-on-reflection*`. F13 is resolved and `incf!` now works on
`LinkedList`.

**Q18 — DECIDED: derived forms are `incf!` and `decf!` only; `push!`, `pop!` and `rotatef!` are
removed.** Both survivors are read-modify-write on a place that already exists, so they need no new
place and no new capability. The three dropped forms were the first things that would have broken the
closed vocabulary (Q15): `push!`/`pop!` need a length-or-append capability that no place expresses,
and `rotatef!` composes two `setf!` calls with no better emission.

## 11. Probe 5 — what does a JVM `:field` write actually cost?

**PROTOTYPE.** `probe5/FieldTactics.java` + `probe5/field_bench.clj`. Same workload every time: write
the public int field `x` of a `java.awt.Point`. 3,000,000 ops per tactic after 300,000 warmup, single
JVM, no JMH. **Indicative only — this is not a benchmark.** Two runs shown:

```
  read           2.35 / 2.12 ns/op    (.x p)
  setH           5.96 / 5.88 ns/op    (set! (.x ^Point p) v)      Clojure's OWN field store
  direct         4.78 / 4.68 ns/op    p.x = v                      generated Java -- THE FLOOR
  mhExact        7.10 / 4.81 ns/op    MethodHandle.invokeExact
  reflect        43.9 / 42.6 ns/op    Reflector/setInstanceField   -- what we emit TODAY
  setU           58.7 / 57.0 ns/op    (set! (.x p) v), NO type hint
  mhCached(clj) 1336 / 1340 ns/op     MethodHandle.invokeWithArguments
```

Against Reflector (today): setH **0.14×**, direct **0.11×**, mhExact 0.11–0.16×, setU 1.34×,
mhCached(clj) **30×**.
Against the floor: read 0.45–0.49×, setH **1.25×**, mhExact 1.03–1.49×, reflect **9.1–9.2×**.

### What this establishes

1. **Clojure can express a direct field store.** `(set! (.x ^Point p) v)` compiles to `PUTFIELD`
   and measures **1.25× of a generated-Java direct store** and **~7× faster than Reflector** —
   in pure Clojure, with no code generation and no MethodHandle. *This corrects an earlier claim of
   mine that `:field` "cannot be made fast in pure Clojure". That was an untested inference and it
   was wrong.*
2. **The type must be statically known to the macro.** `(set! (.x p) v)` without a hint measures
   58.7 ns — *worse than Reflector*, because Clojure's reflective field resolution is slower than
   `Reflector`'s. The hint is the whole difference between 5.9 ns and 58.7 ns.
3. **A cached MethodHandle is near-direct too** (1.03–1.49× of the floor), but Clojure cannot emit
   the signature-polymorphic `invokeExact`, so the only form reachable from Clojure is
   `invokeWithArguments`, which measures **~30× worse than Reflector**. Given (1), MethodHandles
   are now a dead end for this problem.
4. **Reflector is not catastrophic**: 43 ns is ~9× a direct store. "Reflection is slow" was an
   intuition, not a measurement.
5. **A type hint is inert on the other two targets.** `^java.awt.Point` on a local in a `.cljc`
   compiles on all three; ClojureScript and Squint emit byte-identical code with and without it
   (`probe3/apphint/portable/hinted.cljc`). It is ordinary metadata, not a host-specific construct.

## 12. Probe 6 — can a macro see a type hint, and is the hint portable?

**PROTOTYPE.** `/tmp/tagprobe` — one macro that prints `(meta x)` on its argument, called from a
`.cljc` file with two different tags. Real compiles on all three targets.

| tag at the use site | JVM | ClojureScript 1.12.42 | Squint 0.14.210 |
|---|---|---|---|
| `^:setf/indexed` | sees `#:setf{:indexed true}` | sees the same, compiles silently | sees the same, compiles silently |
| `^java.util.ArrayList` | sees `{:tag java.util.ArrayList}` | sees the same, compiles silently | sees the same, compiles silently |

**F16 — No transformer is required.** A macro reads `(meta sym)` on its argument form directly. All
three macro hosts deliver the metadata intact. There is no "type-hint transformer" construct to
build.

**F17 — A portable tag is genuinely portable.** `^:setf/indexed` is just a namespaced keyword. It
survives `.cljc` compilation on ClojureScript and Squint with no warning and no emission change, and
is readable by the macro on all three. So client code *can* carry type information without naming a
host class.

**F18 — A host-specific tag also compiles, silently, on all three.** `^java.util.ArrayList` produces
no warning on ClojureScript or Squint and is erased from the output. So the compiler will not catch
a leaked host class in client code — the constraint has to be held by discipline, not by the build.
This makes the portable tag more valuable, not less.

**F19 — `clojure.core/elt` does not exist.** `(ns-resolve 'clojure.core 'elt)` → `nil`. No collision
with `clojure.core`, and none would matter anyway: the place name is consumed by the macro in head
position and never resolved as a function.

**F20 — `*warn-on-reflection*` cannot detect the Reflector fallback.**
Measured:

```
(.get o 0) with o unhinted          -> Reflection warning
(Reflector/setInstanceField o "x" v) -> SILENT
(set! (.x o) v) with o unhinted      -> Reflection warning
```

`Reflector` is an *explicit* call the macro chose, not an implicitly-reflective call site, so there is
nothing for the compiler to warn about. This refutes the diagnostic half of the Q19 recommendation as
originally put to the user. Loudness has to come from somewhere else.

**Q19 — DECIDED: no type vocabulary ships. Q20 — `:field` on the JVM is decided ONCE, at backend
authoring time: fast when the backend can resolve the receiver's class, Reflector otherwise.**
No tag, no build knob, no vocabulary, nothing client-side. The user's point settled the machinery
question — the emitter choice is six decisions made when the backend is written, not a per-call-site
setting — but one wall remains and it is a property of Clojure, not of the design: **a fast field
store cannot be expressed without a static type**. `(set! (.x ^Object p) v)` does not compile;
`(set! (.x p) v)` compiles but measured 58.7 ns, *worse* than Reflector's 43. So for a foreign class
held in a variable there is no emitter to "implement correctly once". Reflector is that case, and it
is measured, correct, and confined to one place on one host.

Option C (strict: error unless the class is known) was rejected because it makes `:field` mean "a
field of a type this backend defines", which is a host gap — portable code writing a field on a
foreign object would compile on CLJS and Squint and fail on the JVM (requirement 3).

### Built and verified

`plan-binds` now also returns the **original argument forms**, and every emitter receives them
alongside the refs — `(fn [refs srcs])` for read, `(fn [refs srcs v])` for write. This is the one
change to the shared contract, and it is host-agnostic: nothing but source forms travels that way.
It is what lets a backend recognise its own constructors without the shared library knowing a host
exists.

JVM `:field` now resolves the receiver's class at expansion time from its own
`CONSTRUCTOR-TYPES` table, or from a type hint if one happens to be present, and emits accordingly:

```
(setf! (get! (api/make-obj) x) 42)  => (let [t0 (api/make-obj) v 42] (set! (.x t0) v) v)   ; PUTFIELD
(setf! (get! o x) 42)              => (let [t0 o v 42]
                                          (clojure.lang.Reflector/setInstanceField t0 "x" v) v)
(setf! (get! ^java.awt.Point o x) 42) => (let [t0 o v 42] (set! (.x t0) v) v)              ; PUTFIELD
```

The first is the case that needs nothing from the client. The second is a foreign object in a
variable. The third is the hint, which the library never asks for and which is inert on the other two
hosts (F15).

All three targets still produce `APP-RESULT [100 7 42]`; probe 4's mock 4th host, incomplete-backend
rejection, and `LinkedList` + `incf!` (25) all still pass.

## 13. Status

Settled: Q9, Q12, Q13, Q14, Q15, Q16, Q17, Q18 — see §10. All three targets still produce
`APP-RESULT [100 7 42]` after the `elt` rename and the symmetric List pair.

**Q18 — DECIDED: derived forms are `incf!` and `decf!` only. `push!` / `pop!` are removed.**
Both surviving forms are read-modify-write on a place that already exists, so they need no new place
and no new capability. `push!`/`pop!` would have required a length-or-append capability, which no
place in the closed vocabulary expresses — they were the first thing that would have broken Q15.
`rotatef!` is dropped with them; it needs both read and write of two positions with no better
emission than composing `setf!`.

**Squint version:** all Squint evidence is 0.14.210, and the project will move to the latest Squint.
That aligns the evidence with the target rather than leaving a re-verification gap. F7–F10 and F16–F18
are the findings that must hold on whatever version is adopted.

Not settled:

1. **Naming beyond `elt`.** `gethash` and `get!` were inherited from this repo's existing macros, not
   chosen for a standalone library. They are now the only public vocabulary in the design and nobody
   has grilled them.
2. **Repository and packaging shape.** Which parts go in which repo, and what the publish boundary
   is. It follows from Q12/Q15 but has never been stated.
3. **A broader probe 5** before §11 is treated as design input rather than one more data point.
4. **Squint on the project's pinned 0.12.193** has never been run; the project will move to latest, so
   F7–F10 and F16–F18 need re-confirming on whatever version is adopted.