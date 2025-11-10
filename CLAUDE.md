# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is a multi-platform ClojureScript project that supports three compilation targets:
- **Clojure** (JVM) - for testing on the JVM with java.util collections
- **ClojureScript** (CLJS) - for REPL-driven development and standard ClojureScript testing
- **Squint** - for compiling to optimized JavaScript suitable for LeetCode submissions

All source files use `.cljc` extension with reader conditionals to support all three platforms.

## Commands

### Testing

```bash
# Run ALL tests on all three platforms (Clojure, Squint, ClojureScript)
./test-all-platforms.sh

# Clojure tests only (auto-discovers test namespaces)
clojure -M run-clj-tests.clj

# ClojureScript tests only
clj -M:test

# ClojureScript tests with watch mode (recommended for TDD)
clj -M:test-watch

# Single Squint test
./test-one.sh <test-name>   # e.g., ./test-one.sh fizzbuzz_test

# All Squint tests
./test-all.sh
```

### Building

```bash
# Build all problems for LeetCode
npm run build

# Build a single problem
npm run build:one <problem>   # e.g., npm run build:one fizzbuzz

# Clean build artifacts
npm run clean
```

### Development REPL

```bash
# Start ClojureScript REPL
clj -M:repl
```

## Architecture

### Multi-Platform Macro System

**Critical constraint**: Squint can only load macros from **ONE namespace** in the `:require-macros` form.

All macros are consolidated in `src/squintcode/macros.cljc`:
- Test framework macros: `deftest`, `is`, `testing`, `run-tests`
- Common Lisp-style macros: `make-array`, `aref`, `setf`
- Utility macros: `push-end`, `new-map`, `forv`, etc.

**Note**: The namespace uses `(:refer-clojure :exclude [make-array])` to avoid warnings about shadowing `clojure.core/make-array`. This works without reader conditionals on all three platforms (Squint simply ignores it since it doesn't have `clojure.core/make-array`).

**Why**: Squint's namespace handling differs from ClojureScript. When you try to require macros from multiple namespaces like this:

```clojure
;; ❌ DOES NOT WORK in Squint
#?(:squint (:require-macros [namespace-a :refer [macro1]]
                             [namespace-b :refer [macro2]]))
```

Only one namespace will be loaded and macros won't expand correctly. Instead:

```clojure
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
  (setf (aref arr 2) 99))
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
- The `setf` macro is smart enough to recognize both `(aref ...)` and `(cl/aref ...)` forms

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

### Platform-Specific Collections

Test code uses different collection types per platform:

- **Squint/CLJS**: `#js [...]` for JavaScript arrays
- **Clojure**:
  - `(into-array [1 2 3])` for Java arrays (used with `aref`)
  - `java.util.ArrayList` for mutable lists (used with `push-end`)

Example from tests:
```clojure
#?(:squint
   (let [arr #js [1 2 3]]
     (is (= 1 (aref arr 0))))

   :clj
   (let [arr (into-array [1 2 3])]
     (is (= 1 (aref arr 0)))))

;; For push-end with ArrayList
#?(:squint
   (let [arr #js [1 2 3]]
     (push-end arr 4)
     (is (= 4 (.-length arr))))

   :clj
   (let [lst (java.util.ArrayList. [1 2 3])]
     (push-end lst 4)
     (is (= 4 (.size lst)))))
```

### Macro Implementation Patterns

Use `&env` to detect ClojureScript/Squint vs Clojure at macro expansion time:

```clojure
(defmacro aref [arr idx]
  (if (:ns &env)
    ;; ClojureScript or Squint - :ns key is present in &env
    `(aget ~arr ~idx)
    ;; Clojure - &env is empty map, also uses aget for Java arrays
    `(aget ~arr ~idx)))
```

**Why this works**: ClojureScript compiler adds `:ns` key to `&env` during macro expansion. Clojure does not. In this case, both platforms use `aget` since it works for both JavaScript arrays and Java arrays.

### Build System

Squint compilation happens in two phases:

1. **Compilation**: `npx squint compile <file>` produces `.mjs` files in `out/`
2. **Bundling** (for LeetCode): Bun bundles dependencies and removes exports

The `test-all.sh` script compiles all source files first to ensure dependencies are available:

```bash
# Compile all source files
for src_file in src/squintcode/*.cljc; do
  npx squint compile "$src_file" > /dev/null 2>&1 || true
done

# Then compile and run tests
for test_file in test/squintcode/*_test.cljc; do
  ./test-one.sh "$test_name"
done
```

## Key Differences Between Platforms

### Squint vs ClojureScript

1. **Macro Loading**: Squint requires all macros in one namespace
2. **SCI Execution**: Squint uses Small Clojure Interpreter (SCI) to execute macros at compile-time
3. **Output**: Squint produces standalone JavaScript, ClojureScript produces modules with Google Closure Compiler
4. **REPL**: Squint has no REPL, ClojureScript has full REPL support

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
  (aref arr 2)  ; get element at index 2
  ```

- `setf` macro: Common Lisp-style generalized assignment for arrays
  ```clojure
  (setf (aref arr 2) 99)  ; set element at index 2 to 99
  ```

**Complete example** (works on all three platforms):
```clojure
(let [arr (make-array 5 :initial-contents '(1 2 3 4 5))]
  (setf (aref arr 2) 99)  ; modify index 2
  arr)  ; => [1 2 99 4 5]
```

### Other Mutation Macros

- `push-end` macro: **Mutates in place** on all platforms for performance
  - Squint/CLJS: Uses `.push` on JavaScript arrays
  - Clojure: Uses `.add` on java.util.ArrayList
  - Returns different values per platform (array length vs boolean)

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

**Solution**: `test-all.sh` now compiles all source files first. For manual runs:
```bash
npx squint compile src/squintcode/foo.cljc
npx squint compile test/squintcode/foo_test.cljc
node out/squintcode/foo_test.mjs
```

## File Organization

- `src/squintcode/` - Source code (`.cljc` files)
- `test/squintcode/` - Test files (`.cljc` files)
- `out/` - Compiled output (`.mjs` for Squint, `.js` for LeetCode bundles)
- `src/squintcode/macros.cljc` - **All macros consolidated here** for Squint compatibility

## Development Workflow

1. Write code in `.cljc` files with appropriate reader conditionals
2. Add tests in `test/squintcode/`
3. Run `./test-all-platforms.sh` to verify all platforms work
4. For LeetCode submissions, use `npm run build:one <problem>` to generate standalone JS
