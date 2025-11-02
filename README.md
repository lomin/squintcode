# Dual Squint/ClojureScript Project

This project supports both **Squint** (for compiling to LeetCode-compatible JavaScript) and **ClojureScript** (for REPL-driven development and testing).

## Project Structure

```
squintcode/
├── src/
│   └── squintcode/
│       ├── macros.cljc        # Shared macros (works in both Squint and ClojureScript)
│       └── fizzbuzz.cljs      # Main source code
├── test/
│   └── squintcode/
│       ├── test_runner.cljs    # Test runner entry point
│       └── fizzbuzz_test.cljs  # Unit tests
├── out/                   # Build outputs
├── squint.edn            # Squint configuration
├── deps.edn              # ClojureScript dependencies and aliases
├── build-squintcode.sh   # Squint → LeetCode build script
└── package.json          # npm scripts
```

## Prerequisites

- Node.js and npm
- [Clojure CLI tools](https://clojure.org/guides/install_clojure) (`clj` command)
- [Bun](https://bun.sh/) for bundling
- [Calva](https://calva.io/) extension for VSCode (optional, for REPL)

## Squint Usage (LeetCode Builds)

Squint compiles ClojureScript-like code to optimized JavaScript suitable for LeetCode.

### Build for LeetCode

```bash
npm run build
```

This will:
1. Compile `src/squintcode/fizzbuzz.cljs` with Squint
2. Bundle all dependencies with Bun
3. Remove export statements
4. Output a single file: `out/squintcode.js`

The output file can be directly pasted into LeetCode.

### Manual Squint Compilation

```bash
npx squint compile src/squintcode/fizzbuzz.cljs
```

Output goes to `out/squintcode/fizzbuzz.mjs` (as configured in `squint.edn`).

## ClojureScript Usage (Development & Testing)

ClojureScript provides a full REPL experience with hot-reloading and testing support.

### Start a REPL

```bash
clj -M:repl
```

This starts a Node.js-based ClojureScript REPL. You can then:

```clojure
(require '[squintcode.fizzbuzz :refer [fizzBuzz]])
(fizzBuzz 15)
;; => ["1" "2" "Fizz" "4" "Buzz" ... "FizzBuzz"]
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
npm run build    # Build with Squint for LeetCode
```

## Available clj Aliases

```bash
clj -M:repl         # Start ClojureScript REPL
clj -M:test         # Run tests once
clj -M:test-watch   # Continuous test runner
```

## Example Workflow

### For LeetCode Problems

1. Write your solution in `src/squintcode/`
2. Build: `npm run build`
3. Copy contents of `out/squintcode.js` to LeetCode

### For Development with Tests

1. Start Calva REPL in VSCode
2. Write code in `src/`
3. Write tests in `test/`
4. Run tests with `clj -M:test-watch` or via Calva
5. Iterate in the REPL

## Notes

- The project uses multi-segment namespaces (e.g., `squintcode.fizzbuzz`) which is the ClojureScript convention.
- File paths must match namespace structure: `squintcode.fizzbuzz` → `src/squintcode/fizzbuzz.cljs`.
- Squint output is optimized for size and compatibility with LeetCode's JavaScript runtime.
- ClojureScript tests use [cljs-test-runner](https://github.com/Olical/cljs-test-runner), the canonical test runner for ClojureScript projects.
- Tests run in Node.js by default and automatically discover all test namespaces.
- Watch mode (`clj -M:test-watch`) automatically re-runs tests when files change - perfect for TDD.
