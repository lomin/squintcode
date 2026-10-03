#!/usr/bin/env bash
# Run the ucl suite on every host: Clojure/JVM, ClojureScript, Squint.
#
# Backend selection is by source root: each host gets shared/, its own
# backends/<host> (plus backends/js for the two JS hosts) and testkit/<host>,
# and nothing else. Any failure on any host exits non-zero.
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(cd .. && pwd)"
SQUINT="${SQUINT:-$ROOT/node_modules/.bin/squint}"
CLJS_VERSION="${CLJS_VERSION:-1.12.42}"
OUT=out
HOSTS="${*:-jvm cljs squint}"   # e.g. ./run-tests.sh squint
want () { [[ " $HOSTS " == *" $1 "* ]]; }

hr () { printf '\n%s\n%s\n%s\n' "======================================================" "$1" "======================================================"; }

test_nses () { (cd test && find . -name '*_test.cljc' | sed -e 's|^\./||' -e 's|\.cljc$||' -e 's|/|.|g' -e 's|_|-|g' | sort); }
NSES=$(test_nses)

hr "STATIC CHECKS"
# H6: Squint loads a file for macros only if the word appears in it.
grep -q 'defmacro' backends/squint/ucl/api.cljc || { echo "squint backend lost its defmacro marker"; exit 1; }
echo "squint defmacro marker present"

if want jvm; then
hr "CLOJURE / JVM"
# test-jvm/: tests that need to expand a form at run time (ucl's expansion errors)
JVM_NSES="$NSES $(cd test-jvm && find . -name '*_test.clj' | sed -e 's|^\./||' -e 's|\.clj$||' -e 's|/|.|g' -e 's|_|-|g' | sort)"
clojure -Sdeps '{:paths ["shared" "backends/jvm" "testkit/jvm" "test" "test-jvm"]}' -M -e "
(require 'clojure.test $(for n in $JVM_NSES; do printf "'%s " "$n"; done))
(let [r (apply clojure.test/run-tests '[$JVM_NSES])]
  (shutdown-agents)
  (System/exit (if (clojure.test/successful? r) 0 1)))"
fi

if want cljs; then
hr "CLOJURESCRIPT $CLJS_VERSION"
rm -rf "$OUT/cljs" && mkdir -p "$OUT/cljs/src/ucl"
{
  echo "(ns ucl.cljs-runner (:require [cljs.test] $(for n in $NSES; do printf '[%s] ' "$n"; done)))"
  echo "(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]"
  echo "  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))"
  echo "(defn -main [] (cljs.test/run-tests $(for n in $NSES; do printf "'%s " "$n"; done)))"
} > "$OUT/cljs/src/ucl/cljs_runner.cljs"
clojure -Sdeps "{:paths [\"shared\" \"backends/js\" \"backends/cljs\" \"testkit/cljs\" \"test\" \"$OUT/cljs/src\"]
                 :deps {org.clojure/clojurescript {:mvn/version \"$CLJS_VERSION\"}}}" \
  -M -m cljs.main -t node -d "$OUT/cljs/js" -c ucl.cljs-runner 2>&1 | tee "$OUT/cljs/build.log"
# A failed macro resolution is only a WARNING in ClojureScript (H3): any
# warning fails the build.
if grep -q 'WARNING' "$OUT/cljs/build.log"; then echo "ClojureScript build has warnings"; exit 1; fi
node -e "
global.global = global;
require('./$OUT/cljs/js/goog/bootstrap/nodejs.js');
require('./$OUT/cljs/js/cljs_deps.js');
require('./$OUT/cljs/js/ucl/cljs_runner.js');
ucl.cljs_runner._main();"
fi

squint_compile () {   # squint_compile <dir> <safety> <files...> -- every file in ONE invocation (H18)
  local dir="$1" safety="$2"; shift 2
  rm -rf "$dir" && mkdir -p "$dir"
  local up; up=$(realpath --relative-to="$dir" .)
  echo "{:paths [\"$up/shared\" \"$up/backends/js\" \"$up/backends/squint\" \"$up/testkit/squint\" \"$up/test\"]
         :output-dir \"js\" :extension \".mjs\" $( [ -n "$safety" ] && echo ":ucl/safety $safety" )}" > "$dir/squint.edn"
  (cd "$dir" && "$SQUINT" compile $(for f in "$@"; do echo "$up/$f"; done) > compile.log 2>&1) \
    || { cat "$dir/compile.log"; exit 1; }
  if grep -qi 'error' "$dir/compile.log"; then cat "$dir/compile.log"; exit 1; fi
}

SOURCES="shared/ucl/contract.cljc backends/js/ucl/js_emit.cljc backends/squint/ucl/api.cljc
         testkit/squint/ucl/test.cljc testkit/squint/ucl/leetcode.cljc $(cd test && find . -name '*.cljc' | sed 's|^\./|test/|')"

if want squint; then
hr "SQUINT"
squint_compile "$OUT/squint" "" $SOURCES
{
  echo "const t = await import('./js/ucl/test.mjs');"
  for n in $NSES; do echo "await import('./js/$(echo "$n" | tr . / | tr - _).mjs');"; done
  echo "t.run_all_BANG_();"
} > "$OUT/squint/runner.mjs"
node "$OUT/squint/runner.mjs"

hr "SQUINT AT SAFETY 0 (submission builds)"
squint_compile "$OUT/squint0" 0 $SOURCES
if grep -rqE 'elt_checked|elt_set_checked|check_number|push_checked' "$OUT/squint0/js/ucl/"*_test.mjs; then
  echo "safety 0 output still contains checks"; exit 1
fi
echo "no checks emitted at safety 0"
# D29, D35: variables exist so that a loop's state needs no IIFE. The kernels are
# LeetCode-shaped; their submission build must contain none.
if grep -q '(() =>' "$OUT/squint0/js/ucl/kernels.mjs"; then
  echo "ucl/kernels compiles to an IIFE at safety 0:"; grep -n -B2 -A2 '(() =>' "$OUT/squint0/js/ucl/kernels.mjs"; exit 1
fi
echo "no IIFE in the kernels at safety 0"
fi

hr "ALL HOSTS PASSED"
