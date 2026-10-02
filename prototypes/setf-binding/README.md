# `setf!` backend binding — PROTOTYPE

**Throwaway. Not production, not wired to the real build.** Nothing here is committed.

## What this is

Evidence for the open grilling question about *where a host backend may be selected*.
Two things are established here: what each **compiler can see**, and whether a shared,
host-agnostic namespace can own the logic while each host supplies only its own emission.

## Files

| File | What it is |
|---|---|
| `EVIDENCE.md` | **Start here.** Versions, exact commands, observed results, findings F1–F13, limits. |
| `setf-binding.html` | Interactive prototype. Double-click to open — no server, no install. Each verdict appears twice: `OBSERVED` (a real compile) and `MODEL` (this page's rule engine). Disagreements are the interesting cells. |
| `probe1/` | Executable compiler probes: the namespace + macro resolution matrix. |
| `probe3/` | **Design B end to end.** `shared/setf/contract.cljc` (host-agnostic) + `app/portable/app.cljc` (one portable app) + `backends/{jvm,cljs,sq}/setf/api.*` — three files, same namespace name, one per target. |
| `probe4/` | Robustness: incomplete backend, mock 4th host, path collisions, unknown place, bad arity, many structures per accessor. |

## Run probe 3 — three targets, one portable app

```bash
cd prototypes/setf-binding/probe3

# Clojure / JVM  (paths from probe3/deps.edn: shared, app, backends/jvm)
clojure -M -e "(require 'portable.app) (portable.app/-main)"
# => APP-RESULT [100 7 42]

# Squint 0.14.210  (reuses the repo's .probe-squint install; project deps untouched)
rm -rf out
../../../.probe-squint/node_modules/.bin/squint compile \
  shared/setf/contract.cljc app/portable/app.cljc backends/sq/setf/api.cljc
head -3 out/portable/app.mjs          # emitted code; note the single `import * as`
node -e "import('./out/portable/app.mjs').then(m => m._main())"

# ClojureScript 1.12.42
rm -rf out-cljs
clojure -Sdeps '{:paths ["shared" "app" "backends/cljs"]
                 :deps {org.clojure/clojurescript {:mvn/version "1.12.42"}}}' \
  -M -m cljs.main -t node -d out-cljs -c portable.app
cat out-cljs/portable/app.js          # emitted code
node -e "global.global=global;
         require('./out-cljs/goog/bootstrap/nodejs.js');
         require('./out-cljs/cljs_deps.js');
         require('./out-cljs/portable/app.js');
         portable.app._main();"
```

## Run probe 4 — robustness

```bash
cd prototypes/setf-binding/probe4

# emission of whichever backend is on the path
clojure -Sdeps '{:paths ["shared" "." "backends/mock4"]}'   -M -m expand   # mock 4th host
clojure -Sdeps '{:paths ["shared" "." "backends/partial"]}' -M -m expand   # rejected: INCOMPLETE
clojure -Sdeps '{:paths ["shared" "."]}' -M -e "(require 'setf.api)"       # rejected: not found

# two backends, one classpath -> first path wins, silently
clojure -Sdeps '{:paths ["shared" "." "backends/mock4" "backends/partial"]}' -M -m expand
clojure -Sdeps '{:paths ["shared" "." "backends/partial" "backends/mock4"]}' -M -m expand

# many structures behind one accessor
clojure -Sdeps '{:paths ["shared" "." "backends/jvm"]}' -M -m shapes
rm -rf out-cljs
clojure -Sdeps '{:paths ["shared" "." "backends/cljs"]
                 :deps {org.clojure/clojurescript {:mvn/version "1.12.42"}}}' \
  -M -m cljs.main -t node -d out-cljs -c shapes
```

## Run probe 1 — the resolution matrix

```bash
cd prototypes/setf-binding/probe1
clojure -M -e "(require 'nsprobe.main) (println (pr-str (nsprobe.main/run)))"
clojure -M -m cljs.main -t node -d out-cljs5 -o out-cljs5/cy.js -c cy.use
grep -n 'CY-macro' out-cljs5/cy/use.js
../../../.probe-squint/node_modules/.bin/squint compile \
  src/cy/api.cljc src/cy/api.cljs src/cy/use.cljs
cat out/cy/use.mjs
```

### Gotchas that cost real time

- **Squint always writes to `:output-dir` in `squint.edn`.** Ad-hoc overrides are ignored.
- **Squint must be given every source file at once.** A subset silently emits imports for modules
  that were never compiled.
- **Squint emits duplicate `import * as` if a namespace appears in two `:require` entries**, and
  esbuild then rejects the bundle. One entry per namespace.
- **Squint does not expand a bare macro symbol** with only `(:require [ns :as n])`. The *alias-
  qualified* call `(n/macro …)` does expand. See `EVIDENCE.md` F7.
- `cljs.main -t node` emits a bootstrap-only top file; the compiled namespace is under
  `-d <dir>/<ns path>/`. Read that, not the top file.
- An `OutOfMemoryError` during macroexpansion means an infinite loop in the macro, not a memory
  problem. `(rest coll)` on a vector eventually returns `()`, which is truthy.

## Headline result

One shared namespace owns the whole API and the whole expansion algorithm and names no host. Each
host contributes a single map of emitters. All three real targets produce `APP-RESULT [100 7 42]`
from byte-identical portable code, and the emission is direct operators (`t[i] = v`, `m.set(k, v)`,
`o.x = v`) with no runtime dispatch. The one thing that leaks into portable code is a two-branch
reader conditional for macro visibility. See `EVIDENCE.md` §5 and §7.
## Run probe 5 — JVM field-write cost

```bash
cd prototypes/setf-binding/probe5
javac -d classes FieldTactics.java
clojure -Sdeps '{:paths ["." "classes"]}' -M -e "(require 'field-bench) (field-bench/-main)"
```

Bounded, indicative, not a benchmark. See `EVIDENCE.md` §11 for the numbers and what they mean.
