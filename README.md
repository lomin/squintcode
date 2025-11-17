# LeetCode Solutions in Multi-Platform Clojure

Solve LeetCode problems using ClojureScript-like syntax that compiles to optimized JavaScript, with full test coverage across three platforms.

## Features

- ✅ **Squint** - Compiles to LeetCode-compatible JavaScript
- ✅ **Clojure (JVM)** - Tests with native Java collections
- ✅ **ClojureScript** - Interactive REPL development
- ✅ **Common Lisp-style macros** - Array manipulation (`make-array`, `aref`, `setf`)
- ✅ **Mutable data structures** - HashMap/Map operations optimized for performance
- ✅ **Babashka build system** - Fast, unified task orchestration

## Prerequisites

- [Babashka](https://babashka.org/) - Fast Clojure scripting runtime
- [Clojure CLI tools](https://clojure.org/guides/install_clojure) - For REPL and ClojureScript tests
- [Node.js](https://nodejs.org/) - For running compiled JavaScript
- [Bun](https://bun.sh/) or npm - For JavaScript bundling

## Quick Start

```bash
# Install dependencies
npm install

# Run all tests (Squint → Clojure → ClojureScript)
bb test

# Build all problems for LeetCode
bb build

# Build a single problem
bb build-one fizzbuzz
```

## Available Commands

### Testing

```bash
bb test              # Run all tests on all platforms
bb test-squint       # Squint tests only (LeetCode-identical environment)
bb test-clj          # Clojure JVM tests only
bb test-cljs         # ClojureScript tests only
```

**Test order**: Squint → Clojure → ClojureScript (fails fast)

### Building

```bash
bb build             # Build all problems
bb build-one <name>  # Build single problem (e.g., bb build-one fizzbuzz)
bb clean             # Clean all build artifacts
```

## Development Workflow

### Test-Driven Development (Recommended)

```bash
# Terminal 1: Watch and run ClojureScript tests (if configured)
clj -M:test-watch

# Terminal 2: Edit code in your favorite editor
# Tests re-run automatically on save
```

Or simply run tests after each change:

```bash
# Run all tests (fastest feedback)
bb test

# Or run just one platform during development
bb test-cljs        # Interactive development
bb test-squint      # LeetCode-identical environment
```

### REPL-Driven Development

```bash
# Start ClojureScript REPL
clj -M:repl
```

Then in the REPL:

```clojure
;; Load your solution
(require '[squintcode.fizzbuzz :refer [fizzBuzz]])

;; Test it interactively
(fizzBuzz 15)
;; => ["1" "2" "Fizz" "4" "Buzz" ... "FizzBuzz"]

;; Load and run tests
(require '[cljs.test :refer [run-tests]]
         '[squintcode.fizzbuzz-test])
(run-tests 'squintcode.fizzbuzz-test)
```

### IDE Integration (Calva for VSCode)

1. Open project in VSCode with [Calva](https://calva.io/) installed
2. Press `Ctrl+Alt+C Ctrl+Alt+J` (Mac: `Cmd+Option+C Cmd+Option+J`)
3. Select "deps.edn" → `:repl` alias
4. Evaluate code with `Ctrl+Alt+C E` or run tests with `Ctrl+Alt+C T`

## Project Structure

```
.
├── bb.edn                    # Babashka tasks (build system)
├── deps.edn                  # ClojureScript config
├── squint.edn                # Squint compiler config
├── package.json              # npm scripts
├── src/squintcode/
│   ├── macros.cljc                           # All macros (Common Lisp-style)
│   ├── fizzbuzz.cljc                         # Example: FizzBuzz
│   ├── maxprofit.cljc                        # Example: Max Profit
│   ├── lc_560_subarray_sum_equals_k.cljc     # LeetCode 560
│   ├── lc_930_binary_subarrays_with_sum.cljc # LeetCode 930
│   └── *.cljc                                # Your solutions here
├── test/squintcode/
│   ├── *_test.cljc                           # Multi-platform tests
│   ├── lc_560_subarray_sum_equals_k_test.cljc
│   └── lc_930_binary_subarrays_with_sum_test.cljc
└── out/
    ├── squintcode/*.mjs      # Intermediate ES modules
    └── *.js                  # LeetCode-ready JavaScript files
```

## Adding a New Problem

1. **Create solution file**: `src/squintcode/twosum.cljc`

```clojure
(ns squintcode.twosum
  #?(:clj (:require [squintcode.macros :as cl]))
  #?(:squint (:require-macros [squintcode.macros :as cl]))
  #?(:cljs (:require-macros [squintcode.macros :as cl])))

(defn twoSum [nums target]
  ;; Your solution here using cl/aref, cl/make-array, etc.
  )
```

2. **Create test file**: `test/squintcode/twosum_test.cljc`

```clojure
(ns squintcode.twosum-test
  (:require #?@(:squint []
                :clj [[clojure.test :refer [deftest is testing run-tests]]]
                :cljs [[cljs.test :refer-macros [deftest is testing] :refer [run-tests]]])
            #?(:squint ["assert" :as assert])
            [squintcode.twosum :refer [twoSum]])
  #?(:squint (:require-macros [squintcode.macros :refer [deftest is testing run-tests]])))

(deftest twosum-test
  (testing "Basic case"
    (is (= [0 1] (twoSum [2 7 11 15] 9)))))

;; Run tests (required for Squint)
#?(:squint (run-tests))
```

3. **Run tests**:

```bash
bb test
```

4. **Build for LeetCode**:

```bash
bb build-one twosum
# Output: out/twosum.js
```

5. **Submit**: Copy contents of `out/twosum.js` to LeetCode

## Common Lisp-Style Macros

This project provides familiar Common Lisp operations:

### Arrays

```clojure
;; Create array
(cl/make-array 5)                                  ; empty array of size 5
(cl/make-array 5 :initial-contents [1 2 3 4 5])   ; with initial values

;; Read element
(cl/aref arr 2)  ; get element at index 2

;; Modify element
(cl/setf (cl/aref arr 2) 99)  ; set element at index 2 to 99

;; Iterate over array (using macros from squintcode.macros)
(cl/aloop arr elem [sum 0]
  (if elem
    (recur (+ sum elem))
    sum))
```

### Hash Tables

```clojure
;; Create hash table
(cl/dict :a 1 :b 2 :c 3)

;; Get value
(cl/gethash ht :a)         ; returns value or nil
(cl/gethash ht :x 0)       ; returns value or default (0)

;; Set value
(cl/setf (cl/gethash ht :d) 4)
```

### Other Operations

```clojure
;; Append to array (mutates in place)
(cl/push-end arr 42)

;; Array comprehension
(cl/forv [i (range 0 10)] (* i 2))  ; => [0 2 4 6 8 10 12 14 16 18]
```

## Multi-Platform Testing

All tests run on three platforms to ensure correctness:

1. **Squint** - Tests JavaScript output in Node.js (identical to LeetCode)
2. **Clojure** - Tests logic with JVM and Java collections
3. **ClojureScript** - Tests with Google Closure Compiler optimizations

Each platform uses different underlying data structures but the same test code works everywhere thanks to reader conditionals.

## Why Babashka?

- **Fast startup** - Tasks execute nearly instantly
- **Single source of truth** - All build logic in `bb.edn`
- **Cross-platform** - Works on Linux, macOS, Windows
- **Composable** - Task dependencies ensure correct execution order
- **Simple** - Direct task invocation without npm wrapper overhead

## Tips

- **Test before building**: `bb test` catches errors early across all platforms
- **Use Squint tests**: They run in the same environment as LeetCode (Node.js)
- **Check file sizes**: Large builds may timeout on LeetCode
- **Watch mode**: Use `clj -M:test-watch` if configured for rapid development feedback

## Troubleshooting

**"command not found: bb"**
- Install babashka: https://babashka.org/#installation

**"npx: command not found"**
- Install Node.js: https://nodejs.org/

**"Cannot find module"**
- Run `npm install` to install dependencies

**Tests fail on one platform only**
- Check reader conditionals (`:squint` must come before `:cljs`)
- Verify platform-specific code is correct

## Learn More

- [CLAUDE.md](CLAUDE.md) - Detailed architecture and development guide
- [Babashka](https://book.babashka.org/) - Task runner documentation
- [Squint](https://github.com/squint-cljs/squint) - ClojureScript-like compiler
- [ClojureScript](https://clojurescript.org/) - REPL and advanced features

## License

ISC
