# `setf` — generalized assignment: one contract, many hosts

**Status:** implemented, green on Clojure/JVM, ClojureScript and Squint from a
single byte-identical test suite.

This document is the design of record. It records what the library is, why each
decision was made, what was measured to make it, what was tried and rejected, and
what is still open. Where a decision was made on the user's judgement rather than
on a measurement, it says so.

---

## 1. What it is

`setf!` gives Clojure Common Lisp-style generalized assignment that compiles to
**direct host operations**. One portable call site; no runtime dispatch; no
allocation; no protocol layer.

```clojure
(elt a 1)                   ; JVM  (.get ^java.util.List t0 t1)
(setf! (elt a 1) 99)        ; JVM  (.set ^java.util.List t0 t1 v)
(incf! (elt a 1))           ; JS   t0[t1] = (t0[t1] + 1)
(gethash m "k")             ; JS   t0.get(t1)
(setf! (gethash m "k") 7)    ; JS   t0.set(t1, v)
(get! o x)                  ; JS   t0.x
(setf! (get! o x) 42)        ; JS   t0.x = v
(decf! (elt a 1) 10)
```

Generated bindings are shown as `t0`, `t1`, `v` throughout for readability; the
real names are `gensym`s, so they can never capture a caller's local.

The library exists to make that true on *any* host, not just the three here. The
point is not the three emittable forms; it is that the shared logic is genuinely
host-agnostic and a new host costs one directory.

## 2. The requirements it was designed against

These were binding from the start. Most of the architecture falls out of them.

1. **No closed host list in the shared library.** No `HOSTS` table, no
   `#?(:clj … :cljs … :squint …)` branching inside shared code.
2. The shared library's purpose is **portability**; beyond that it need not serve
   one host particularly well.
3. **All compatible hosts must implement the whole contract.** No partial hosts,
   no "this host does not support that place".
4. On one host, **different structures sharing an accessor must all work** —
   `ArrayList` and `LinkedList`, `Array` and `Uint32Array`.
5. The shared logic must be publishable in **its own repository**, with
   host-specific code in separate namespaces, artifacts and repositories.
6. Heuristics, in priority order: **(a) optimal performance**; **(b) tie-break —
   push as much logic as possible into the shared namespace** without reducing
   performance. The split line is exactly where host-specific emission becomes
   unavoidable.

Requirement 6(b) is the load-bearing one for the shape of the code. In practice:
`setf.contract` carries the whole model, including arity checking and the curried
read; the JS backends are almost entirely the six emitter bodies plus one-line
macro delegates; the JVM backend is larger only because it asks the compiler for
a `:field` receiver's class and carries the Reflector fallback.

## 3. Requirements that shaped it, and were settled early

**Mutable host collections only.** `ArrayList`/`HashMap` on the JVM;
`Array`/`Map`/`Uint32Array` in JS. Persistent collections are out of scope. This
removes the entire question of `set!`-equivalent semantics for persistent
structures, which is where a naive `setf` design usually falls apart.

**A fixed set of built-in places.** No `define-setf-expander`, no
`define-modify-macro`. Users write `setf!` over the places the library defines,
not over arbitrary accessor forms.

**Resolution is syntactic and macro-time.** Place resolution keys on the
**accessor symbol**, not on the runtime type. The accessor may dispatch internally;
the macro never looks at a value.

**`first` and `peek` are not places.** They were removed from an earlier design:
they are sequence operations, not places, and their presence made the vocabulary
look open when it is not.

## 4. Architecture

```
setf/
  shared/setf/contract.cljc      the whole model. Names no host.
  backends/jvm/setf/api.clj      six functions. Clojure/JVM.
  backends/cljs/setf/api.cljc    macro half
  backends/cljs/setf/api.cljs    runtime half
  backends/squint/setf/api.cljc  one file -- Squint reads no other macro format
  test/setf/api_test.cljc        byte-identical across hosts
  example/portable/app.cljc      the portable cost of the library, in full
```

### 4.1 The shared namespace owns the model

`setf.contract` holds:

- **the place vocabulary** and each place's argument *kinds* — `RUNTIME`
  (evaluated once at run time) vs `SLOT` (a name consumed at expansion time and
  never evaluated);
- **single-evaluation binding**: every runtime argument is bound exactly once,
  left to right, so a compound index expression cannot run twice;
- **the expansion algorithm**, including arity checking, unknown-place rejection,
  slot validation, and rejection of incomplete backends.

It runs inside each host's macro host and names no host, no file extension, and no
build tool. `grep -i 'clj\|cljs\|squint\|jvm' shared/` returns nothing.

### 4.2 A backend is six functions

```clojure
{:indexed {:read  (fn [refs srcs] ...)      :write (fn [refs srcs v] ...)}
 :keyed   {:read  (fn [refs srcs] ...)      :write (fn [refs srcs v] ...)}
 :field   {:read  (fn [refs srcs] ...)      :write (fn [refs srcs v] ...)}}
```

- `refs` are the generated binding symbols — the place's evaluated arguments.
- `srcs` are the **original argument forms**, handed over so a backend can
  recognise one of its own constructors, or read a type hint, without the shared
  namespace knowing that any host exists.

`srcs` is the single contract change made during implementation, and it is
host-agnostic: only source forms travel that way.

Each backend also defines the macros as one-line delegates to the shared engine,
plus the constructors its clients use (`make-arr`, `make-map`, `make-obj`).

A backend may build its emitter map per expansion. The JVM backend does, because
its `:field` pair reads the compiler's `&env` to learn a local's class. The
contract never sees `&env`; it still receives six functions.

### 4.3 Backend selection is a source-root convention

Every backend declares the **same namespace, `setf.api`**, in a different file:

```clojure
:paths ["shared" "your-app" "backends/cljs"]   ; ClojureScript
:paths ["shared" "your-app" "backends/jvm"]   ; Clojure
:paths ["shared" "your-app" "backends/squint"] ; Squint
```

**Mutual exclusion of those roots is the invariant, and nothing enforces it.**
Two backends on one classpath: the first path entry silently wins, no warning, no
error (see F11). This is a build-configuration rule, not something a library can
check — and it is the accepted cost of the design.

## 5. The API

| form | meaning |
|---|---|
| `(elt coll i)` | the element at index `i` |
| `(gethash m k)` | the value at key `k` |
| `(get! o field)` | the field `field` |
| `(setf! (elt coll i) v)` | assign the element at index `i` |
| `(setf! (gethash m k) v)` | assign the value at key `k` |
| `(setf! (get! o field) v)` | assign the field `field` |
| `(incf! place)` / `(incf! place delta)` | add, in place |
| `(decf! place)` / `(decf! place delta)` | subtract, in place |

Every form evaluates to the value it just stored. Every runtime argument is
evaluated **exactly once**, left to right, however compound it is — on the write
path and on the read path both.

`elt` / `gethash` / `get!` are *place names*, and each is an **accessor pair**:
one name, both directions. `(elt coll i)` reads; `(setf! (elt coll i) v)` writes.
`setf!` consumes the place form in head position and never expands it, so the two
directions never interfere. A place name is a **macro**, not a function, so that
a read can attach whatever the host needs to emit its fastest access — a JVM
`get!` reads the compiler's knowledge of the receiver's class.

A place name is matched by its name alone, so `(setf! (api/elt a 1) v)` and
`(setf! (elt a 1) v)` are the same place.

A macro cannot be passed by value, so `(map elt xs)` does not compile. Given every
argument but the receiver, a place name instead **returns a function**:
`(map (elt 1) colls)`, `(map (gethash "k") maps)`, `(map (get! x) objs)`. The
fixed arguments are evaluated once per application. On the JS hosts and for
`elt`/`gethash` on the JVM this is as fast as the direct form; a curried `get!`
on the JVM is reflective (§7).

Place names denote *semantics*, not the function they are named after, and are not
resolved against `clojure.core` — `clojure.core/elt` does not exist. Each host
emits its fastest form with those semantics: a JVM `elt` is
`(.get ^java.util.List coll i)`, O(1), not an O(n) sequence walk.

## 6. What a client pays

One require clause, two branches:

```clojure
(:require #?(:clj  [setf.api :as api :refer [setf! incf! decf! elt gethash get!]]
             :cljs [setf.api :as api :refer-macros [setf! incf! decf! elt gethash get!]]))
```

That is the entire per-target cost, and it is the *measured* price of the macro
design. It does **not** grow with the number of hosts, and it contains no host
registry — an unknown host still compiles. It is only the Clojure-family quirk
that macros are referred with `:refer` and the JS family with `:refer-macros`.

Two rules learned the hard way:

- **Keep it to one `:require` entry per namespace.** Two entries for the same
  namespace make Squint emit the same `import * as` twice, which is invalid ESM
  and which esbuild rejects (F8).
- **`:squint` needs no branch of its own** because Squint activates the `:cljs`
  reader feature (F10). Ordering `:squint` first is still correct whenever a
  `:squint` branch *does* exist, because reader conditionals are first-match.

## 7. Performance

### JS hosts

Direct operators, no dispatch, no allocation:

```js
t0[t1];                                         // elt a 1
(t0_609[t1_610] = v_611);                      // setf! (elt a 1) 99
(t0_612[t1_613] = ((t0_612[t1_613]) + (1)));    // incf! (elt a 1)
t0.get(t1);                                     // gethash m "k"
t0_618.set(t1_619, v_620);                      // setf! (gethash m "k") 7
(t0_621.x = v_622);                             // setf! (get! o x) 42
```

Single-evaluation is visible in the output: a `(dec 1)` index and a `(+ 1 1)`
value each appear exactly once.

### JVM

`elt` and `gethash` emit `^java.util.List` / `^java.util.Map` **interface calls
with hints supplied by the backend** — client code carries no type:

```clojure
(elt a 1)           => (let [t0 a t1 1] (.get ^java.util.List t0 t1))
(setf! (elt a 1) 99)  => (let [t0 a t1 1 v 99] (.set ^java.util.List t0 t1 v) v)
(setf! (gethash m "k") 7) => (let [t0 m t1 "k" v 7] (.put ^java.util.Map t0 t1 v) v)
```

The read and the write are the same operation with the same hint, from the same
emitter family — which is what keeps `incf!` from being reachable on strictly fewer
structures than `setf!` (F13).

It works on every `java.util.List` and `java.util.Map`, at the structure's own
cost: O(1) on `ArrayList` and `HashMap`, O(n) on `LinkedList`, O(log n) on
`TreeMap`. `nth` would have been the wrong choice: it requires `Indexed`, which
excludes `LinkedList`, and it made the derived forms reachable on strictly fewer
structures than `setf!` (F13).

`get!` emits a real `GETFIELD`/`PUTFIELD` whenever the receiver's class is known
at expansion time, and falls back to `clojure.lang.Reflector` otherwise. The
backend keeps no table of its own; it asks the compiler, in this order:

1. a hint on the receiver form itself — `(get! ^Point p x)`;
2. the class the compiler has for a local, read from `&env` — a hinted fn
   parameter, or a `let` whose init expression has a known type;
3. the return-type hint of the called fn, resolved through the namespace —
   `(get! (api/make-obj) x)`, because `make-obj` is declared `^java.awt.Point`.

```clojure
(setf! (get! (api/make-obj) x) 42)   => (let [t (api/make-obj) v 42] (set! (.x ^Point t) v) v)
(fn [^Point p] (setf! (get! p x) 42)) => (let [t p v 42] (set! (.x ^Point t) v) v)
(setf! (get! o x) 42)                => (let [t o v 42] (Reflector/setInstanceField t "x" v) v)
(get! o x)                           => (let [t o] (Reflector/getInstanceField t "x"))
```

Reads and writes fall back the same way. This is the library's only reflective
path. It exists because Clojure cannot express a fast field access without a
static type — see §8.

### Measured: what a JVM field write costs

Same workload every time — write the public `int x` of a `java.awt.Point`,
3,000,000 ops per tactic after 300,000 warmup, two runs. **Indicative only; this
is not a JMH benchmark and rests on one field shape.**

| tactic | ns/op (run 1 / run 2) | vs floor |
|---|---|---|
| `(.x p)` — direct field **read** | 2.35 / 2.12 | 0.45–0.49× |
| `p.x = v` — generated Java store (**the floor**) | 4.78 / 4.68 | 1.0× |
| **`(set! (.x ^Point p) v)` — Clojure's own store** | **5.96 / 5.88** | **1.25×** |
| cached `MethodHandle.invokeExact` | 7.10 / 4.81 | 1.03–1.49× |
| `Reflector/setInstanceField` — the fallback | 43.9 / 42.6 | ~9× |
| `(set! (.x p) v)` — no type hint | 58.7 / 57.0 | ~12× |
| cached MethodHandle via `invokeWithArguments` | 1336 / 1340 | ~280× |

Three conclusions, all of which changed the design:

1. **Clojure can express a direct field store.** `(set! (.x ^Point p) v)`
   compiles to `PUTFIELD` and is 1.25× of a generated-Java store and ~7× faster
   than Reflector, in pure Clojure. No code generation required.
2. **The type must be statically known.** Without the hint the same form is
   *slower* than Reflector, because Clojure's reflective field resolution is
   slower than `Reflector`'s. The hint is the whole difference between 5.9 ns and
   58.7 ns.
3. **MethodHandles are a dead end here.** `invokeExact` is as fast as a direct
   store, but it is signature-polymorphic and Clojure cannot emit the required
   call-site descriptor. The only form reachable from Clojure is
   `invokeWithArguments`, which measures ~30× *worse* than Reflector.

`Reflector` at ~43 ns is ~9× a direct store — alarming-sounding, and much less
alarming than "reflection is slow" suggests. That is the price of the one case
where the JVM cannot know the class.

## 8. Decision log

Each decision, what was chosen, and what decided it. "User judgement" means the
choice was made on reasoning, with no measurement available either way.

| # | Decision | Decided by |
|---|---|---|
| **Q1** | Mutable host collections only; persistent collections out of scope | user judgement |
| **Q2** | Fixed set of built-in places plus `setf!`; no user-definable expanders | user judgement |
| **Q3** | Place resolution keys on the accessor symbol, not the runtime type | user judgement |
| **Q4** | Host-agnostic namespace plus a separate per-host namespace. Rejected: one giant "is it Clojure?" namespace | user judgement |
| **Q5** | Parallel development; existing `src/`/`test/` untouched | user judgement |
| **Q7** | Resolution is syntactic and macro-time, not type-based | agent, confirmed |
| **Q9** | **`setf!` is a macro.** Runtime dispatch would have cost the direct emission and the static field splice | evidence |
| **Q12** | **Backend selection is explicit in each target's build setup.** Mutual exclusion is an accepted, unenforced build invariant | user judgement |
| **Q13** | `:field` writes specialize at expansion time when the type is statically identifiable, else fall back | evidence |
| **Q14** | JVM resolves the receiver's class from (a) a hint on the receiver form, (b) the compiler's class for a local (`&env`), (c) the resolved fn's return-type hint, else (d) Reflector | evidence |
| **Q15** | **The place vocabulary is closed by design.** The library's value is the contract, not a place abstraction outside it | user judgement |
| **Q16** | The indexed place is **`elt`**; `nth` leaves the vocabulary entirely | user judgement |
| **Q17** | **A place name denotes semantics**; each host emits its fastest form with those semantics | user judgement |
| **Q18** | Derived forms are `incf!` and `decf!` only. `push!`, `pop!`, `rotatef!` removed | user judgement |
| **Q19** | **No type vocabulary ships.** No tags, no build knobs | user judgement |
| **Q20** | `:field` on the JVM is decided once, at backend authoring time | user judgement |

### Notes on the load-bearing ones

**Q9 — macro over runtime function.** A runtime `setf!` would have made the
consumer require uniform and eliminated every reader conditional. Against it: the
hot path gains dispatch, and `o.x = v` can no longer be spliced, because a JS
engine cannot fold a field write through a protocol call. The macro arm's cost
turned out to be one two-branch require that does not grow with host count, which
was not known until it was measured end to end.

**Q12 — namespace-scoped selection.** Rejected alternative: compilation-scoped
backend injection (one shared facade file, a var set by the build). It does not
solve the consumer's macro-visibility problem at all, and it adds hidden global
state to compilation. It was ruled out on evidence and never built.

**Q15 / Q16 — closed vocabulary, and why `elt`.** A host that needs a capability
the contract does not cover — a slice range, say — writes its own code; it does
not need this library. And `elt` rather than `aref`, because `aref` names a host
concept ("array reference") while `elt` names the semantics (element at index).
`clojure.core/elt` does not exist, so there is no collision, and there would not
be one anyway: the place name is consumed by the macro in head position and never
resolved as a function.

**Q21 — a place name reads as well as writes.** The vocabulary originally shipped
write-only: `elt` existed only as a token in `setf!`'s head position, and clients
read back with `nth` / `.get` / `.-x`. That is a hole in a *generalized* assignment
library — the same name should name both directions of the same place — and it
forced portable client code to name host accessors anyway, which is exactly what
this library exists to stop.

The read side cost almost nothing, because the half already existed: `incf!` and
`decf!` need a `:read` emitter to compute a new value, so every backend was already
required to supply one. `expand-read` reuses it, and reuses `plan-binds`, so the
read path gets the once-only evaluation rule for free. Per backend: three two-line
macros. Per shared library: one function.

The rejected alternative was a runtime `fn` named `elt` alongside the macro. It
would have made `(map elt xs)` legal, and it would have thrown away the type hint
on every JVM read — a measured regression against the one criterion that outranks
all others. A macro is what `setf!` already is, so this costs nothing new.

**Q17 — semantics, not the function it is named after.** Under the literal
reading, a JVM `elt` would be O(n) on any `java.util.List`. Under this reading a
List has an O(1) indexed primitive, so `(.get ^java.util.List coll i)` *is* `elt`
made as fast as this host can make it.

**Q18 — why `push!`/`pop!` had to go.** They are not read-modify-write on an
existing place; they need a length-or-append capability that no place in a closed
vocabulary expresses. They were the first things that would have broken Q15.
`rotatef!` composes two `setf!` calls with no better emission.

**Q19 — why no type tag.** A macro can read `(meta sym)` on all three hosts
(F16–F18), and `^:setf/indexed` is provably portable and inert. So a type tag
*was* technically available. It was rejected because the tag would apply to
exactly one place on one host — `:field` on the JVM — because `elt` and `gethash`
take their type from a backend-supplied interface hint. It would have added a
second closed vocabulary that every host must grow in lockstep, and bought 5.9 ns
versus 43 ns only for a foreign class held in a variable — and that case is
covered anyway, because on the JVM a macro *can* see a binding-site hint through
`&env` (Q14 b).

## 9. Evidence

Everything below was measured by compiling and running, not reasoned about. The
prototype that produced it is retained under `prototypes/setf-binding/`.

**F1 — `.cljc` is the only macro-namespace format all three targets read.**
Squint does not read `.clj` at all.

**F2 — *withdrawn; see F7. Do not use this one.***

**F3 — ClojureScript's macro-resolution failure is a WARNING, not an error.**
`WARNING: Use of undeclared Var mx.api/where`, compilation continues, and the
output is a broken run-time call. A portable build can go green and fail only at
run time. This is the most dangerous finding in the set.

**F4 — A namespace may be split `.cljc` (macros) + `.cljs` (runtime).** Accepted
by both ClojureScript and Squint; this is the shape the two JS backends use.

**F5 — Macro visibility is orthogonal to where the backend is selected.** Both
candidate designs make `setf!` a macro, so moving the backend changes nothing for
the consumer. This is why Design A was dropped without being built.

**F6 — Q12 could not be decided before Q9.** Retained as a process finding.

**F7 — Squint resolves macros reached through an alias, but not bare macro
symbols.**

| require clause | call | Squint 0.14.210 |
|---|---|---|
| `(:require [setf.api :as api])` | `(api/setf! (elt a 1) 99)` | expands |
| `(:require [setf.api :as api])` | `(setf! (elt a 1) 99)` | emits a run-time call |

ClojureScript expands neither without `:refer-macros`. This corrected an earlier,
over-broad claim that Squint "finds macros implicitly".

**F8 — Squint emits duplicate `import * as` when a namespace appears in two
`:require` entries.** Syntactically invalid ESM; esbuild rejects it. One entry per
namespace is mandatory.

**F9 — One entry, `[setf.api :as api :refer-macros [...]]`, works on BOTH
ClojureScript and Squint**, and makes both `api/setf!` and bare `setf!` available.
This is what collapses the portable require to two branches.

**F10 — Squint activates the `:cljs` reader feature**, so `#?(:clj … :cljs …)`
covers Squint too.

**F11 — Two same-namespace backends on one classpath: the first path entry wins,
silently.** No warning, no error; the outcome depends on ordering that is
invisible in most build configurations. Accepted as an invariant, not a bug to fix.

**F12 — Failure modes are loud, precise, and at build time.** Missing backend →
immediate namespace-resolution error. Incomplete backend → a contract error naming
the exact kind and operation. Unknown place → an error listing the known places.
Bad slot → rejected by name.

**F13 — A read and a write emitter from different families silently narrow a
derived form.** `(.set coll i v)` is a `List` method and works on `LinkedList`;
`nth` does not. `incf!` needs both, so it was reachable on strictly fewer
structures than `setf!`. Fixed by making both halves `List` calls; `incf!` now
works on `LinkedList`. **General lesson: pick a read and a write emitter from the
same family.**

**F14 — `set!` on a hinted field is a real `PUTFIELD` in Clojure** and needs no
MethodHandle. See §7.

**F15 — A type hint is inert on the other two hosts.** `^java.awt.Point` on a local
in a `.cljc` compiles on all three, and ClojureScript and Squint emit
byte-identical code with and without it.

**F16 — No transformer is required.** A macro reads `(meta sym)` on its argument
form directly, on all three macro hosts.

**F17 — A portable tag really is portable.** `^:setf/indexed` survives `.cljc`
compilation on ClojureScript and Squint with no warning and no emission change.

**F18 — A host-specific tag also compiles silently, on all three.**
`^java.util.ArrayList` produces no warning on ClojureScript or Squint and is
erased from the output. The compiler will not catch a leaked host class in
client code; the constraint has to be held by discipline.

**F19 — `clojure.core/elt` does not exist.** `(ns-resolve 'clojure.core 'elt)` →
`nil`.

**F20 — `*warn-on-reflection*` cannot detect the Reflector fallback.**

```
(.get o 0) with o unhinted           -> Reflection warning
(Reflector/setInstanceField o "x" v) -> SILENT
(set! (.x o) v) with o unhinted       -> Reflection warning
```

Reflector is an *explicit* call the macro chose, not an implicitly-reflective call
site. Any loudness has to come from somewhere else, which is part of why Q19
shipped without detection machinery.

## 10. Rejected alternatives

**Compilation-scoped backend injection.** One shared facade file; a var holding
the backend set by the build. Rejected on F5: it does not change the consumer's
macro-visibility problem at all, and it adds hidden global state to compilation.
Strictly worse than namespace-scoped selection.

**A runtime protocol (`-set-at!`).** Uniform consumers, but dispatch on every
operation and no static field splice. Rejected at Q9, once the macro arm's cost
had been measured and found small and bounded.

**A type vocabulary (`^:setf/…`).** Technically available (F16–F18). Rejected:
it applies to one place on one host, adds a second closed vocabulary every host
must grow in lockstep, and buys 5.9 ns versus 43 ns in one case.

**A build knob to make the Reflector path detectable.** Proposed, then withdrawn
when it was pointed out that the emitter choice is six decisions made when the
backend is written — not a per-call-site state. And F20 shows the compiler-based
version would not have worked anyway.

**Strict `:field` on the JVM** — error unless the class is known. This is the
only option that honours "never call reflection" literally, and it makes `:field`
mean "a field of a type this backend defines". Portable code writing a field on a
foreign object would compile on the JS hosts and fail on the JVM. That is a host
gap, which requirement 3 rules out.

**`push!`, `pop!`, `rotatef!`.** See Q18.

**A generated-Java or MethodHandle fast path for `:field`.** Measured to be
unnecessary: plain Clojure already reaches 1.25× of the floor, and the
MethodHandle route that Clojure can actually express is 30× worse than what it
would replace.

## 11. Verification

`test/setf/api_test.cljc` is byte-identical across hosts. It covers every place,
every API form, return values, single-evaluation of arguments, hygiene of the
generated bindings, `make-arr`'s initial contents, namespace-qualified place
names, curried readers, slot splicing, arity rejection, unknown places, slot
validation, and (JVM) how a `:field` receiver's class is resolved.

```bash
./run-tests.sh          # all three hosts, plus the example app on each
```

The script exits non-zero if any assertion fails on any host, or if the example
app does not print identically on all three. Current state:

```
CLOJURE / JVM      Ran 18 tests, 67 assertions.  0 failures, 0 errors.
CLOJURESCRIPT      Ran 12 tests, 47 assertions.  0 failures, 0 errors.
SQUINT             all assertions pass
EXAMPLE APP        RESULT and HOF output identical on all three hosts
```

Two honest caveats about the suite:

- The **expansion tests run on the JVM only.** `macroexpand-1` is an ordinary
  function in Clojure but a compile-time macro in ClojureScript and Squint, so a
  bad form fails the build rather than throwing something a `try` can catch. The
  logic they cover lives in the shared namespace, so covering it once is
  meaningful — but their absence on the JS hosts is not evidence that the
  rejection is untested there, it is untested there.
- The Squint run uses this repository's own `squintcode.macros` for `deftest`/`is`,
  because the library deliberately has no test-framework dependency.

The open-closed property is exercised by a mock fourth host in the prototype
directory, whose primitives (`fetch` / `store!` / `lookup` / `assoc!` / `slot` /
`put-slot!`) appear nowhere else and which required no change to the shared
file.

## 12. Splitting into separate repositories

The layout is already the split. Each becomes its own repo with no code change,
only a `git mv` and its own build file:

| repo | contents |
|---|---|
| `setf` | `shared/` + `test/` + `example/` |
| `setf-jvm` | `backends/jvm/` |
| `setf-cljs` | `backends/cljs/` |
| `setf-squint` | `backends/squint/` |

Adding a **new host** means adding one directory. Nothing in `shared/` changes.
That is the property the whole design exists to guarantee.

## 13. Operational notes

Toolchain facts that cost real time and are easy to re-trip:

- **Squint writes only to `:output-dir` in `squint.edn`** and ignores ad-hoc
  overrides, so results land somewhere other than where you look.
- **Squint must be handed every source file in one invocation.** A subset
  silently emits imports for modules that were never compiled.
- **Two `:require` entries for one namespace make Squint emit invalid ESM** (F8).
- `cljs.main -t node` emits a bootstrap-only top file; the compiled namespace
  lives under `-d <dir>/<ns path>/`.
- **`OutOfMemoryError` during macroexpansion means an infinite loop in the
  macro**, not a memory problem. `(rest coll)` on a vector eventually returns
  `()`, which is truthy in Clojure; the loop needs `(seq ks)`.
- A macro that returns a *plan map* instead of the code compiles to garbage.
- `(str "t" n)` produces a String, and `list` emits it as a string literal — use
  `(symbol …)` for generated names. And make them `gensym`s: a fixed name like
  `t0` captures a caller's local of the same name.
- A `let` binding vector of vectors destructures; it does not bind two pairs.
  Interleave into a flat vector.
- **Squint's `is` is node's `assert.equal`, i.e. `==`.** `==` on two distinct JS
  arrays is *reference* equality, so an assertion comparing two collections that
  print identically fails on Squint and passes on Clojure and ClojureScript. The
  shared test suite therefore compares one value per assertion.
- **ClojureScript does not intern keyword literals into a stable identity.** Two
  occurrences of `:a` compile to two distinct `new cljs.core.Keyword(...)`
  objects, so `(identical? :a :a)` is **false** and a `js/Map` written with one
  occurrence cannot be read with the other. Verified in isolation, with no `setf!`
  involved. Use string keys for a keyed place on the JS hosts.

Versions used for every measurement: Node 24.16.0, openjdk 21.0.11, Clojure
1.12.0, ClojureScript 1.12.42, Squint 0.14.210.

## 14. Known limitations and open questions

- **No JMH.** The field-write numbers rest on one public `int` field on one class.
  Boxed, `long` and `double` fields, final fields, private fields, and a cold-JIT
  profile shaped like a real run are all unmeasured.
- **The Reflector fallback is silent.** Correct and measured, but a 7× slower path
  that no tool will ever point at. Accepted under Q19/Q20.
- **Squint 0.12.193 has never been run.** Every Squint result is 0.14.210; this
  repository pins 0.12.193 and plans to move to latest. F7–F10 and F16–F18 should
  be re-confirmed on whatever version is adopted, since they are the findings the
  portable require clause rests on.
- **Naming beyond `elt` is inherited, not chosen.** `gethash` and `get!` came
  from this repository's existing macros. They are now the entire public
  vocabulary and have never been grilled.
- **Packaging is asserted, not exercised.** The repos in §12 do not exist yet;
  the split has been reasoned about and laid out, not performed.
- `incf!`/`decf!` are the only derived forms, and no evidence exists yet for any
  other one.
- **A place name cannot be passed by value** — `(map elt colls)` does not compile
  — but the curried form `(elt 1)` returns a function. On the JVM a curried `get!`
  is reflective, measured 15–23× a direct field read by `bench/hof_bench.clj`.
  Write `(fn [^Point p] (get! p x))` where that matters.

## 15. Appendix — corrections made during the session

Recorded because a design document that hides its own wrong turns is not
trustworthy. Each of these was a claim made without compiling it first.

| Claim | Correction |
|---|---|
| "Squint finds macros implicitly" | Too broad. Squint expands *alias-qualified* macro calls but not bare symbols with only a `:as` require (F7). |
| "`:field` cannot be made fast in pure Clojure — it requires generating Java" | False. `(set! (.x ^Point p) v)` is a `PUTFIELD` at 1.25× of the floor. It was inferred, never compiled, and cost a whole decision cycle. |
| "Turn on `*warn-on-reflection*` and treat it as an error to make the fallback visible" | Would have missed the exact path it was meant to catch (F20). |
| "Add a build knob to detect when the type is unknown" | The emitter choice is six decisions made at backend authoring time, not a per-call-site state. Withdrawn. |
| `incf!` returned the value it stored | It returned the *delta*. Found by the test suite, not by inspection. Fixed in the engine so all forms return what they stored. |
| A slot argument is a compile-time name | Never checked. A list slot reached a backend and produced a `ClassCastException`. Now rejected by name in the shared namespace. |
| "The vocabulary is complete because all three places can be written" | The read side was missing. A *generalized* assignment library whose place names cannot be read is half a library, and the gap pushed portable clients back to naming `nth` / `.get` / `.-x` by hand. Closed in Q21, reusing the `:read` emitter every backend already had for `incf!`. |

| Generated bindings were hygienic | They were fixed names `t0`, `t1`, `v`. `(let [t0 7] (setf! (elt a 0) t0))` stored the array into itself. Now `gensym`s. |
| "JS `make-arr` builds an array with holes" | `(array n)` builds the one-element array `[n]`. The tests worked around a bug they had misdiagnosed. Now length `n`, filled with 0 on every host. |
| JVM constructor table recognised `make-obj` | It matched any fn *named* `make-obj` in any namespace, and hinted a map as `Point`. Replaced by asking the compiler (Q14). |
| "A macro cannot see a binding-site hint" | On the JVM it can, through `&env`. The write path was emitting Reflector for a hinted fn parameter. |
| "Green on all three hosts" | JVM and ClojureScript failures exited 0, and "ALL HOSTS AGREE" was printed unconditionally. Both are now real gates. |
| "Curried `get!` measured ~110×" | The benchmark did not compile. Fixed and run: 15–23×. |

The pattern is consistent: every serious error in this design was an inference
made where a compile would have answered the question.
