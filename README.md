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
bb build-one <name>  # Build single problem (e.g., bb build-one twosum)
bb clean             # Clean all build artifacts
```

### npm Shortcuts

All `bb` commands are also available via npm:

```bash
npm test             # Same as bb test
npm run build        # Same as bb build
npm run build:one    # Same as bb build-one
```

## Development Workflow

### Option 1: Test-Driven Development (Recommended)

```bash
# Terminal 1: Watch and run ClojureScript tests
clj -M:test-watch

# Terminal 2: Edit code in your favorite editor
# Tests re-run automatically on save
```

### Option 2: REPL-Driven Development

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

### Option 3: Calva (VSCode)

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
│   ├── macros.cljc           # All macros (Common Lisp-style)
│   ├── fizzbuzz.cljc         # Example solutions
│   └── *.cljc                # Your solutions here
├── test/squintcode/
│   └── *_test.cljc           # Multi-platform tests
└── out/
    └── *.js                  # LeetCode-ready JavaScript files
```

## Adding a New Problem

1. **Create solution file**: `src/squintcode/twosum.cljc`

```clojure
(ns squintcode.twosum
  #?(:cljs (:require-macros [squintcode.macros :refer [aref]]))
  #?(:clj  (:require [squintcode.macros :refer [aref]])))

(defn twoSum [nums target]
  ;; Your solution here
  )
```

2. **Create test file**: `test/squintcode/twosum_test.cljc`

```clojure
(ns squintcode.twosum-test
  (:require #?@(:squint []
                :clj [[clojure.test :refer [deftest is testing]]]
                :cljs [[cljs.test :refer-macros [deftest is testing]]])
            [squintcode.twosum :refer [twoSum]])
  #?(:squint (:require-macros [squintcode.macros :refer [deftest is testing]])))

(deftest twosum-test
  (testing "Basic case"
    (is (= [0 1] (twoSum [2 7 11 15] 9)))))
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
(make-array 5)                                  ; empty array of size 5
(make-array 5 :initial-contents [1 2 3 4 5])   ; with initial values

;; Read element
(cl/aref arr 2)  ; get element at index 2

;; Modify element
(setf (cl/aref arr 2) 99)  ; set element at index 2 to 99

;; Iterate over array
(aloop arr elem [sum 0]
  (if elem
    (recur (+ sum elem))
    sum))
```

### Hash Tables

```clojure
;; Create hash table
(dict :a 1 :b 2 :c 3)

;; Get value
(gethash ht :a)         ; returns value or nil
(gethash ht :x 0)       ; returns value or default (0)

;; Set value
(setf (gethash ht :d) 4)
```

### Other Operations

```clojure
;; Append to array (mutates in place)
(push-end arr 42)

;; Array comprehension
(forv [i (range 0 10)] (* i 2))  ; => [0 2 4 6 8 10 12 14 16 18]
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
- **DRY** - npm scripts simply delegate to `bb` tasks
- **Composable** - Task dependencies ensure correct execution order

## Tips

- **Use watch mode** during development: `clj -M:test-watch`
- **Test before building**: `bb test` catches errors early
- **Check file sizes**: Large builds may timeout on LeetCode
- **Profile with Squint tests**: They run in the same environment as LeetCode

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
