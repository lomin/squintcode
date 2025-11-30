# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is a multi-platform ClojureScript project that supports three compilation targets:
- **Clojure** (JVM) - for testing on the JVM with java.util collections
- **ClojureScript** (CLJS) - for REPL-driven development and standard ClojureScript testing
- **Squint** - for compiling to optimized JavaScript suitable for LeetCode submissions

All source files use `.cljc` extension with reader conditionals to support all three platforms.

The build and test system is orchestrated by **Babashka** (`bb.edn`), providing a single source of truth for all build tasks.

## Commands

All commands are run through Babashka. The `package.json` npm scripts delegate to `bb` tasks for convenience.

### Testing

```bash
# Run ALL tests on all three platforms (Squint → Clojure → ClojureScript)
bb test              # or: npm test

# Run tests for a single platform
bb test-squint       # or: npm run test:squint
bb test-clj          # or: npm run test:clj
bb test-cljs         # or: npm run test:cljs
```

**Test execution order**: Squint → Clojure → ClojureScript (fails fast on first platform failure)

**Squint test environment**: Tests are compiled, bundled with esbuild, and run with Node.js in an environment **identical to LeetCode's JavaScript runtime**.

### Building

```bash
# Build all problems for LeetCode
bb build             # or: npm run build

# Build a single problem
bb build-one <problem>   # or: npm run build:one <problem>
# Example: bb build-one fizzbuzz

# Clean build artifacts
bb clean             # or: npm run clean
```

### Development REPL

```bash
# Start ClojureScript REPL (not managed by babashka)
clj -M:repl
```

### Clojure REPL Evaluation

The command `clj-nrepl-eval` is installed on your path for evaluating Clojure code via nREPL.

**Discover nREPL servers:**

```bash
clj-nrepl-eval --discover-ports
```

**Evaluate code:**

```bash
clj-nrepl-eval -p <port> "<clojure-code>"

# With timeout (milliseconds)
clj-nrepl-eval -p <port> --timeout 5000 "<clojure-code>"
```

The REPL session persists between evaluations - namespaces and state are maintained.
Always use `:reload` when requiring namespaces to pick up changes.

## Architecture

### Babashka Task System

The entire build and test process is defined in `bb.edn` with the following task organization:

#### Task Categories

**Clean Tasks**:
- `-clean-js` - Remove JavaScript build artifacts (`.js`, `.bundle.js`, `.mjs`)
- `-clean-cljs-cache` - Remove ClojureScript cache directories
- `clean` - Run all clean tasks

**Compilation Tasks**:
- `-compile-macros` - Compile `macros.cljc` (required dependency for all Squint code)
- `-compile-squint-sources` - Compile all source `.cljc` files (excluding macros and tests)

**Test Tasks**:
- `test-squint` - Run Squint tests in LeetCode-identical environment
- `test-clj` - Run Clojure (JVM) tests
- `test-cljs` - Run ClojureScript tests
- `test` - Run all tests in order (depends on all three)

**Build Tasks**:
- `build-one <problem>` - Build single problem for LeetCode
- `build-all` - Build all problems
- `build` - Alias for `build-all`

#### Task Dependencies

Tasks use `:depends` to ensure correct execution order:

```
test-squint: clean → compile-macros → compile-squint-sources → test-squint
test-clj: (independent, no clean)
test-cljs: (independent, no clean)
test: aggregates all three (test-squint cleans first)

build: clean → compile-macros → build-all
build-one: (compiles macros internally)
```

**Key principle**: Macros must **always** be compiled first before any Squint source or test files.

### Squint Test Execution (LeetCode-Identical)

The `test-squint` task ensures tests run in **exactly the same environment** as LeetCode submissions:

1. **Compile macros**: `npx squint compile src/squintcode/macros.cljc`
2. **For each test file**:
   - Compile source file (if exists): `npx squint compile src/squintcode/<problem>.cljc`
   - Compile test file: `npx squint compile test/squintcode/<problem>_test.cljc`
   - Bundle test with esbuild: `npx esbuild <test>.mjs --outfile=<test>.bundle.js --format=esm --bundle --tree-shaking=true --platform=node`
   - Remove export statements (strip ES module exports)
   - Run with Node.js: `node out/<problem>_test.js`
   - Capture exit code (0 = pass, 1 = fail)

**Why this matters**: The Squint tests use the same compilation and bundling pipeline as LeetCode submissions, ensuring test behavior matches production behavior.

### Build Pipeline (Squint → LeetCode)

The `build-one` and `build-all` tasks create LeetCode-ready JavaScript files:

```
source.cljc
    ↓
[Squint compile]
    ↓
out/squintcode/source.mjs (ES modules)
    ↓
[esbuild bundle --format=esm]
    ↓
out/source.bundle.js (with exports)
    ↓
[Remove export statements]
    ↓
out/source.js (final, LeetCode-ready)
```

**Key steps**:
1. Compile macros first (dependency)
2. Compile source file
3. Bundle with esbuild (tree-shaking enabled)
4. Remove ES module exports (make standalone)
5. Output to `out/<problem>.js`

**IMPORTANT**: The esbuild bundling step MUST use `--format=esm`, not `--format=iife`:
- `--format=esm` produces clean code with `export { ... }` at the end, which we strip out
- `--format=iife` wraps everything in `(() => { ... })()` which LeetCode doesn't accept
- The final output must be standalone JavaScript with no module wrappers or exports

### Multi-Platform Macro System

**Critical constraint**: Squint can only load macros from **ONE namespace** in the `:require-macros` form.

All macros are consolidated in `src/squintcode/macros.cljc`:
- Test framework macros: `deftest`, `is`, `testing`, `run-tests`
- Common Lisp-style macros: `make-array`, `aref`, `setf`, `gethash`, `dict`
- Utility macros: `push-end`, `forv`, `aloop`, `postwalk`

**Note**: The namespace uses `(:refer-clojure :exclude [make-array])` to avoid warnings about shadowing `clojure.core/make-array`. This works without reader conditionals on all three platforms.

**Why**: Squint's namespace handling differs from ClojureScript. Multiple namespaces in `:require-macros` will not work:

```clojure
;; ❌ DOES NOT WORK in Squint
#?(:squint (:require-macros [namespace-a :refer [macro1]]
                             [namespace-b :refer [macro2]]))

;; ✅ WORKS - all macros in one namespace
#?(:squint (:require-macros [squintcode.macros :refer [macro1 macro2]]))
```

### Test File Structure

All test files are `.cljc` files with reader conditionals for platform-specific behavior.

**Option 1: Import with `:refer` (unqualified usage)**
```clojure
(ns squintcode.example-test
  (:refer-clojure :exclude [make-array])
  (:require #?@(:squint []
                :clj [[clojure.test :refer [deftest is testing run-tests]]]
                :cljs [[cljs.test :refer-macros [deftest is testing] :refer [run-tests]]])
            #?(:squint ["assert" :as assert]))
  #?(:clj (:require [squintcode.macros :refer [aref push-end]]))
  #?(:squint (:require-macros [squintcode.macros :refer [deftest is testing run-tests aref push-end]]))
  #?(:cljs (:require-macros [squintcode.macros :refer [aref push-end]])))

;; Usage: unqualified
(let [arr (make-array 5 :initial-contents [1 2 3 4 5])]
  (setf (cl/aref arr 2) 99))
```

**Option 2: Import with `:as` (namespace-qualified usage)**
```clojure
(ns squintcode.example-test
  (:refer-clojure :exclude [make-array])
  (:require #?@(:squint []
                :clj [[clojure.test :refer [deftest is testing run-tests]]]
                :cljs [[cljs.test :refer-macros [deftest is testing] :refer [run-tests]]])
            #?(:squint ["assert" :as assert]))
  #?(:clj (:require [squintcode.macros :as cl]))
  #?(:squint (:require-macros [squintcode.macros :as cl :refer [deftest is testing run-tests]]))
  #?(:cljs (:require-macros [squintcode.macros :as cl])))

;; Usage: namespace-qualified
(let [arr (cl/make-array 5 :initial-contents [1 2 3 4 5])]
  (cl/setf (cl/aref arr 2) 99))
```

**Key patterns**:
- Squint: Macros via `:require-macros`, everything from `squintcode.macros`
- CLJS: Test macros via `:refer-macros`, other macros via `:require-macros`
- CLJ: All macros via regular `:require`
- Both `:refer` and `:as` work on all platforms
- The `setf` macro is smart enough to recognize both `(cl/aref ...)` and `(cl/aref ...)` forms

### Reader Conditional Ordering

**Important**: When using reader conditionals, `:squint` must come **before** `:cljs`.

Squint defines both `:squint` and `:cljs` features. Reader conditionals match the first applicable feature, so:

```clojure
;; ✅ CORRECT - :squint comes first
#?(:squint (squint-specific-code)
   :cljs (cljs-specific-code)
   :clj (clj-specific-code))

;; ❌ WRONG - Squint will execute CLJS code path
#?(:cljs (cljs-specific-code)
   :squint (squint-specific-code))
```

### Macro Implementation Patterns

#### Pattern 1: Using `&env` to detect platform (works for runtime detection)

Use `&env` to detect ClojureScript/Squint vs Clojure at macro expansion time:

```clojure
(defmacro make-array [size & {:keys [initial-contents]}]
  (if (:ns &env)
    ;; ClojureScript or Squint - :ns key is present in &env
    `(js/Array. ~size)
    ;; Clojure - &env is empty map
    `(java.util.ArrayList. ~size)))
```

**Why this works**: ClojureScript compiler adds `:ns` key to `&env` during macro expansion. Clojure does not.

**When to use**: Use this pattern for macros that need to emit different code based on whether the macro is being expanded in a ClojureScript/Squint context vs Clojure context. This is the standard approach when you need runtime platform detection.

#### Pattern 2: Reader conditionals at defmacro level (required for SCI compatibility)

**IMPORTANT**: Squint's SCI (Small Clojure Interpreter) cannot parse syntax-quoted reader conditionals (`` `#?(...) ``) inside macro bodies. When a macro needs to return platform-specific code at macro expansion time, you must use reader conditionals at the `defmacro` level instead:

```clojure
;; ❌ DOES NOT WORK in Squint - SCI cannot parse syntax-quoted reader conditionals
(defmacro aref [arr idx]
  `#?(:squint (aget ~arr ~idx)
      :clj (nth ~arr ~idx)
      :cljs (nth ~arr ~idx)))

;; ❌ DOES NOT WORK - wrapping in `do` doesn't help
(defmacro aref [arr idx]
  `(do #?(:squint (aget ~arr ~idx)
          :clj (nth ~arr ~idx)
          :cljs (nth ~arr ~idx))))

;; ✅ WORKS - reader conditionals at defmacro level
#?(:clj
   (defmacro aref [arr idx]
     `(nth ~arr ~idx))
   :default
   (defmacro aref [arr idx]
     `(aget ~arr ~idx)))
```

**Why this matters**:
- The pattern `` `#?(:squint ... :clj ... :cljs ...) `` compiles fine with regular Clojure/ClojureScript
- But when Squint's SCI tries to **load and parse** the macro definition at compile-time, it fails with parse errors
- Using reader conditionals at the `defmacro` level creates separate macro definitions per platform
- Each platform gets a clean macro definition without problematic syntax-quoted reader conditionals
- SCI can successfully parse and load these platform-specific macro definitions

**When to use**: Use this pattern when your macro needs to return code that is fundamentally different per platform and you need Squint/SCI compatibility. This is essential for macros like `aref` and `length` that expand to completely different forms (e.g., `aget` vs `nth`, `.-length` vs `count`).

### Common Lisp-Style Array Operations

This project implements Common Lisp-style array manipulation:

- `make-array` macro: Create arrays with optional initial contents
  ```clojure
  (make-array 5)                                  ; empty array of size 5
  (make-array 5 :initial-contents [1 2 3 4 5])   ; array with initial values
  (make-array 5 :initial-contents '(1 2 3 4 5))  ; works with lists too
  ```

- `aref` macro: Read array elements
  ```clojure
  (cl/aref arr 2)  ; get element at index 2
  ```

- `setf` macro: Common Lisp-style generalized assignment for arrays
  ```clojure
  (setf (cl/aref arr 2) 99)  ; set element at index 2 to 99
  ```

**Complete example** (works on all three platforms):
```clojure
(let [arr (make-array 5 :initial-contents '(1 2 3 4 5))]
  (setf (cl/aref arr 2) 99)  ; modify index 2
  arr)  ; => [1 2 99 4 5]
```

### Other Mutation Macros

- `push-end` macro: **Mutates in place** on all platforms for performance
  - Squint/CLJS: Uses `.push` on JavaScript arrays
  - Clojure: Uses `.add` on java.util.ArrayList
  - Returns the mutated collection

- `dict` macro: Create mutable hash tables
  - Squint/CLJS: Creates `js/Map` with string keys (keywords converted at compile-time)
  - Clojure: Creates `java.util.HashMap`

- `gethash` macro: Get value from hash table with optional default
  - Squint/CLJS: Uses `.get` on `js/Map`
  - Clojure: Uses `.get` on `java.util.HashMap`

## Key Differences Between Platforms

### Squint vs ClojureScript

1. **Macro Loading**: Squint requires all macros in one namespace
2. **SCI Execution**: Squint uses Small Clojure Interpreter (SCI) to execute macros at compile-time
3. **Output**: Squint produces standalone JavaScript, ClojureScript produces modules with Google Closure Compiler
4. **REPL**: Squint has no REPL, ClojureScript has full REPL support

### Platform-Specific Collections

Test code uses different collection types per platform, but each collection type is mutable.

## Common Issues and Solutions

### Issue: Macros not expanding in Squint tests

**Symptom**: Generated JavaScript shows function calls like `deftest(...)` instead of expanded code.

**Cause**: Multiple namespaces in `:require-macros` or test macros not in `squintcode.macros`.

**Solution**: Ensure all macros are loaded from single namespace:
```clojure
#?(:squint (:require-macros [squintcode.macros :refer [deftest is testing ...]]))
```

### Issue: Test file syntax errors in Squint

**Symptom**: "Unmatched delimiter" or parsing errors.

**Cause**: Reader conditional placement or extra parens in `ns` form.

**Solution**: Each reader conditional should be a complete top-level form:
```clojure
(ns example
  (:require ...)
  #?(:clj (:require [clojure.test :refer [deftest]]))     ;; Complete form
  #?(:squint (:require-macros [squintcode.macros ...])))  ;; Complete form
```

### Issue: Module not found when running Squint tests

**Symptom**: `Cannot find module '/path/to/out/squintcode/foo.mjs'`

**Cause**: Source files not compiled before tests.

**Solution**: Run `bb test-squint` which handles dependency compilation automatically.

### Issue: Babashka task fails with "command not found"

**Symptom**: `npx: command not found` or similar

**Cause**: Node.js/npm not installed or not in PATH.

**Solution**: Install Node.js and ensure `npx` is available in your PATH.

### Issue: SCI parse error with syntax-quoted reader conditionals in macros

**Symptom**: "EOF while reading, expected ) to match (" or similar parse errors when running Squint tests. The macros compile fine with `npx squint compile`, and CLJ/CLJS tests pass, but Squint tests fail.

**Cause**: Squint's SCI (Small Clojure Interpreter) cannot parse syntax-quoted reader conditionals (`` `#?(...) ``) inside macro bodies. When SCI tries to load the macro definition for expansion, it fails to parse this pattern.

**Solution**: Use reader conditionals at the `defmacro` level instead of inside the macro body:

```clojure
;; ❌ BAD - SCI cannot parse this
(defmacro aref [arr idx]
  `#?(:squint (aget ~arr ~idx)
      :clj (nth ~arr ~idx)))

;; ✅ GOOD - reader conditionals at defmacro level
#?(:clj
   (defmacro aref [arr idx]
     `(nth ~arr ~idx))
   :default
   (defmacro aref [arr idx]
     `(aget ~arr ~idx)))
```

See **Macro Implementation Patterns** section for more details.

## File Organization

```
/Users/steven/development/leetcode/
├── bb.edn                        # Babashka task configuration (primary build system)
├── deps.edn                      # ClojureScript dependencies and REPL config
├── squint.edn                    # Squint compiler configuration
├── package.json                  # npm scripts (delegates to bb)
├── run-clj-tests.clj             # Clojure test auto-discovery script
├── src/squintcode/
│   ├── macros.cljc               # ALL macros (consolidated for Squint)
│   ├── fizzbuzz.cljc             # Example problem implementations
│   ├── maxprofit.cljc
│   └── subarray_sum_equals_k.cljc
├── test/squintcode/
│   ├── fizzbuzz_test.cljc        # Multi-platform tests
│   ├── aref_test.cljc
│   ├── push_end_test.cljc
│   ├── setf_test.cljc
│   ├── hash_table_test.cljc
│   └── subarray_sum_equals_k_test.cljc
└── out/
    ├── squintcode/               # Intermediate .mjs files
    └── *.js                      # Final LeetCode-ready JavaScript files
```

## Development Workflow

1. Write code in `.cljc` files with appropriate reader conditionals
2. Add tests in `test/squintcode/`
3. Run `bb test` to verify all platforms work
4. For LeetCode submissions, use `bb build-one <problem>` to generate standalone JS

## Adding New Problems

1. Create `src/squintcode/<problem>.cljc` with your solution
2. Create `test/squintcode/<problem>_test.cljc` with tests
3. Run `bb test` to verify all platforms pass
4. Run `bb build-one <problem>` to generate LeetCode submission file
5. Copy `out/<problem>.js` to LeetCode

## Babashka Task Dependencies

Understanding the dependency graph helps when debugging or extending the build system:

```
clean
  ├── -clean-js (removes *.js, *.bundle.js, *.mjs)
  └── -clean-cljs-cache (removes cache dirs)

test
  ├── test-squint
  │     ├── clean
  │     │   ├── -clean-js
  │     │   └── -clean-cljs-cache
  │     ├── -compile-macros
  │     └── -compile-squint-sources
  │           └── -compile-macros
  ├── test-clj (independent)
  └── test-cljs (independent)

build / build-all
  ├── clean
  │   ├── -clean-js
  │   └── -clean-cljs-cache
  └── -compile-macros

build-one
  (no explicit dependencies, compiles macros internally)
```

**Note**: Private tasks (prefixed with `-`) are hidden from `bb tasks` output but available as dependencies.

## Performance Considerations

- **Squint tests**: Each test is compiled and bundled independently. For large test suites, this can be slow. Consider batching related tests into single files.
- **Parallel builds**: Currently, `build-all` runs sequentially. Could be parallelized with `:parallel true` in task dependencies if needed.
- **Incremental compilation**: Babashka tasks don't track file changes. Use ClojureScript watch mode (`clj -M:test-watch`) for TDD workflow.
