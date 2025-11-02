# Dual Squint/ClojureScript Project

This project supports both **Squint** (for compiling to LeetCode-compatible JavaScript) and **ClojureScript** (for REPL-driven development and testing).

## Project Structure

```
squintcode/
├── src/
│   └── squintcode/
│       ├── macros.cljc        # Shared macros (works in both Squint and ClojureScript)
│       ├── fizzbuzz.cljs      # Example: FizzBuzz problem
│       └── maxprofit.cljs     # Example: Max Profit problem
├── test/
│   └── squintcode/
│       ├── test_runner.cljs    # Test runner entry point
│       └── fizzbuzz_test.cljs  # Unit tests
├── out/                   # Build outputs
├── squint.edn            # Squint configuration
├── deps.edn              # ClojureScript dependencies and aliases
├── build-all.sh          # Build all problems
├── build-one.sh          # Build a single problem
├── build-squintcode.sh   # Backwards compatibility (calls build-all.sh)
└── package.json          # npm scripts
```

## Prerequisites

- Node.js and npm
- [Clojure CLI tools](https://clojure.org/guides/install_clojure) (`clj` command)
- [Bun](https://bun.sh/) for bundling
- [Calva](https://calva.io/) extension for VSCode (optional, for REPL)

## Squint Usage (LeetCode Builds)

Squint compiles ClojureScript-like code to optimized JavaScript suitable for LeetCode.

### Build All Problems

```bash
npm run build
# or
./build-all.sh
```

This will compile **all** `.cljs` files in `src/squintcode/` and output individual files to `out/`:
- `out/fizzbuzz.js`
- `out/maxprofit.js`
- etc.

Each output file can be directly pasted into LeetCode.

### Build a Single Problem

```bash
npm run build:one <problem-name>
# or
./build-one.sh <problem-name>
```

Example:
```bash
./build-one.sh fizzbuzz
```

This will:
1. Compile `src/squintcode/fizzbuzz.cljs` with Squint
2. Bundle all dependencies with Bun
3. Remove export statements
4. Output a single file: `out/fizzbuzz.js`

### Manual Squint Compilation

```bash
npx squint compile src/squintcode/<problem>.cljs
```

Output goes to `out/squintcode/<problem>.mjs` (as configured in `squint.edn`).

## ClojureScript Usage (Development & Testing)

ClojureScript provides a full REPL experience with hot-reloading and testing support.

### Start a REPL

```bash
clj -M:repl
```

This starts a Node.js-based ClojureScript REPL. You can then:

```clojure
;; Load and test FizzBuzz
(require '[squintcode.fizzbuzz :refer [fizzBuzz]])
(fizzBuzz 15)
;; => ["1" "2" "Fizz" "4" "Buzz" ... "FizzBuzz"]

;; Load and test MaxProfit
(require '[squintcode.maxprofit :refer [maxProfit]])
(maxProfit [7 1 5 3 6 4])
;; => 5
```

### Run Tests in the REPL

To run tests directly in the REPL:

```clojure
;; Load the test namespace and cljs.test
(require '[cljs.test :refer [run-tests]]
         '[squintcode.fizzbuzz-test])

;; Run the tests
(run-tests 'squintcode.fizzbuzz-test)
```

To re-run tests after making changes:

```clojure
;; Reload the namespace to pick up changes
(require '[squintcode.fizzbuzz-test] :reload)

;; Run the tests again
(run-tests 'squintcode.fizzbuzz-test)
```

For multiple test namespaces:

```clojure
(run-tests 'squintcode.fizzbuzz-test
           'squintcode.another-test)
```

### Run Tests Once

```bash
clj -M:test
```

Uses [cljs-test-runner](https://github.com/Olical/cljs-test-runner) (the canonical ClojureScript test runner) to automatically discover and run all tests in the `test/` directory via Node.js.

### Continuous Test Runner

```bash
clj -M:test-watch
```

Watches `src/` and `test/` directories for changes and automatically re-runs tests. This is the **recommended way** for test-driven development:
- Runs tests immediately on startup
- Watches for file changes
- Automatically re-runs tests when you save files
- Press `Ctrl+C` to stop

## Using with Calva (VSCode)

### Connect to REPL

1. Open the project in VSCode
2. Press `Ctrl+Alt+C Ctrl+Alt+J` (or `Cmd+Option+C Cmd+Option+J` on Mac)
3. Select "deps.edn"
4. Choose the `:repl` alias

Calva will start a ClojureScript REPL connected to your editor.

### Running Tests in Calva

With the REPL connected:
1. Open `test/squintcode/fizzbuzz_test.cljs`
2. Press `Ctrl+Alt+C T` to run tests in the current namespace
3. Or use `Ctrl+Alt+C Ctrl+Alt+T` to run all tests

### Evaluating Code

- `Ctrl+Alt+C E`: Evaluate current form
- `Ctrl+Alt+C Space`: Evaluate top-level form
- Load current file: `Ctrl+Alt+C Enter`

## Macro Setup

Macros are defined in `src/squintcode/macros.cljc`.

**Important**: The macro file uses `.cljc` extension to work with both Squint and ClojureScript.

## Available npm Scripts

```bash
npm run build           # Build all problems for LeetCode
npm run build:all       # Build all problems (same as above)
npm run build:one NAME  # Build a single problem (e.g., npm run build:one fizzbuzz)
npm run clean           # Clean all build artifacts from out/
```

Note: `npm run build` automatically cleans old `.js` files before building.

## Available clj Aliases

```bash
clj -M:repl         # Start ClojureScript REPL
clj -M:test         # Run tests once
clj -M:test-watch   # Continuous test runner
```

## Example Workflow

### For LeetCode Problems

1. Write your solution in `src/squintcode/<problem>.cljs`
2. Build all: `npm run build` or build one: `npm run build:one <problem>`
3. Copy contents of `out/<problem>.js` to LeetCode

Example:
```bash
# Create a new problem
vim src/squintcode/twosum.cljs

# Build just that problem
./build-one.sh twosum

# Copy out/twosum.js to LeetCode
```

### For Development with Tests

1. Start Calva REPL in VSCode or use `clj -M:repl`
2. Write code in `src/squintcode/`
3. Write tests in `test/squintcode/`
4. Run tests with `clj -M:test-watch` or via Calva
5. Iterate in the REPL
6. Build for LeetCode when ready

## Notes

- The project uses multi-segment namespaces (e.g., `squintcode.fizzbuzz`) which is the ClojureScript convention.
- File paths must match namespace structure: `squintcode.fizzbuzz` → `src/squintcode/fizzbuzz.cljs`.
- Squint output is optimized for size and compatibility with LeetCode's JavaScript runtime.
- ClojureScript tests use [cljs-test-runner](https://github.com/Olical/cljs-test-runner), the canonical test runner for ClojureScript projects.
- Tests run in Node.js by default and automatically discover all test namespaces.
- Watch mode (`clj -M:test-watch`) automatically re-runs tests when files change - perfect for TDD.
