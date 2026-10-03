# LeetCode Solutions in Multi-Platform Clojure

Solve LeetCode problems using ClojureScript-like syntax that compiles to optimized JavaScript, with full test coverage across three platforms.

## Features

- ✅ **Squint** - Compiles to LeetCode-compatible JavaScript
- ✅ **Clojure (JVM)** - Tests with native Java collections
- ✅ **ClojureScript** - Interactive REPL development
- ✅ **[`ucl`](ucl/README.md)** - a Common Lisp-shaped contract (`make-array`, `elt`, `setf`, `gethash`, `defstruct`, ...) that compiles to hand-written-quality code on every host
- ✅ **One source, no reader conditionals** - solutions and tests are identical on all three hosts
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
bb test-ucl          # ucl library suite on all hosts
bb test-squint       # Squint tests only (LeetCode-identical environment)
bb test-clj          # Clojure JVM tests only
bb test-cljs         # ClojureScript tests only
```

**Test order**: ucl suite, then Squint → Clojure → ClojureScript (fails fast).
A ClojureScript compiler warning fails the run.

### Building

```bash
bb build             # Build all problems
bb build-one <name>  # Build single problem (e.g., bb build-one fizzbuzz)
bb clean             # Clean all build artifacts
```

## Development Workflow

### Test-Driven Development (Recommended)

```bash
# Terminal 1: Watch and run ClojureScript tests
clj -M:cljs:test-watch

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
# Start ClojureScript REPL (or clj -M:jvm for a Clojure REPL)
clj -M:cljs:repl
```

Then in the REPL:

```clojure
;; Load your solution
(require '[squintcode.fizzbuzz :refer [fizzBuzz]])

;; Test it interactively
(vec (fizzBuzz 15))
;; => ["1" "2" "Fizz" "4" "Buzz" ... "FizzBuzz"]
```

### IDE Integration (Calva for VSCode)

1. Open project in VSCode with [Calva](https://calva.io/) installed
2. Press `Ctrl+Alt+C Ctrl+Alt+J` (Mac: `Cmd+Option+C Cmd+Option+J`)
3. Select "deps.edn" → the `:cljs` and `:repl` aliases
4. Evaluate code with `Ctrl+Alt+C E` or run tests with `Ctrl+Alt+C T`

## Project Structure

```
.
├── bb.edn, bb/tasks/lc.clj   # Babashka tasks (build system)
├── deps.edn                  # Clojure/ClojureScript config; aliases :jvm / :cljs
├── squint.edn                # Squint compiler config
├── src/squintcode/*.cljc     # Solutions
├── test/squintcode/*_test.cljc  # Their tests -- one file for every host
├── ucl/                      # The ucl library (README.md is its design)
└── out/*.js                  # LeetCode-ready JavaScript
```

## Adding a New Problem

1. **Solution**: `src/squintcode/twosum.cljc`

```clojure
(ns squintcode.twosum
  (:require [ucl.api :as ucl]))

(ucl/defun twoSum (nums target)
  (declare (type simple-vector nums))
  (let [n    (ucl/length nums)
        seen (ucl/make-hash-table)]
    (loop [i 0]
      (when (< i n)
        (let [x (ucl/elt nums i)
              j (ucl/gethash (- target x) seen)]
          (if (some? j)
            (ucl/make-array 2 :initial-contents [j i])
            (do (ucl/setf (ucl/gethash x seen) i)
                (recur (inc i)))))))))
```

2. **Test**: `test/squintcode/twosum_test.cljc`

```clojure
(ns squintcode.twosum-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [squintcode.twosum :refer [twoSum]]))

(defn arr [v] (ucl/make-array (count v) :initial-contents v))

(deftest twosum-test
  (testing "Basic case"
    (let [r (twoSum (arr [2 7 11 15]) 9)]
      (is (= 0 (ucl/elt r 0)))
      (is (= 1 (ucl/elt r 1))))))
```

3. **Run tests**: `bb test`
4. **Build**: `bb build-one twosum` → `out/twosum.js`
5. **Submit**: copy `out/twosum.js` to LeetCode

The vocabulary, types and rules are in [ucl/README.md](ucl/README.md) §4–§8
and in [CLAUDE.md](CLAUDE.md).

## Multi-Platform Testing

All tests run on three platforms to ensure correctness:

1. **Squint** - Tests JavaScript output in Node.js (identical to LeetCode)
2. **Clojure** - Tests logic with JVM and Java collections
3. **ClojureScript** - Tests with Google Closure Compiler optimizations

Each platform uses different underlying data structures, but the same source and test code run everywhere: `ucl` chooses the host representation, and `ucl.test` is one test vocabulary on every host.

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
- **Watch mode**: Use `clj -M:cljs:test-watch` for rapid feedback

## Troubleshooting

**"command not found: bb"**
- Install babashka: https://babashka.org/#installation

**"npx: command not found"**
- Install Node.js: https://nodejs.org/

**"Cannot find module"**
- Run `npm install` to install dependencies

**Tests fail on one platform only**
- On the JVM, `ucl WARNING: ... of unknown type` means a missing `(declare (type ...))`
- See ucl/README.md §10 (host facts) for known host differences

## Learn More

- [CLAUDE.md](CLAUDE.md) - Detailed architecture and development guide
- [Babashka](https://book.babashka.org/) - Task runner documentation
- [Squint](https://github.com/squint-cljs/squint) - ClojureScript-like compiler
- [ClojureScript](https://clojurescript.org/) - REPL and advanced features

## License

ISC
