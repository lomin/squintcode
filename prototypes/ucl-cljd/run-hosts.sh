#!/usr/bin/env bash
# Run example/ucl_example/fizzbuzz.cljc -- one unchanged source -- on all four
# hosts and require identical output.
set -euo pipefail
cd "$(dirname "$0")"
export PATH="$HOME/.local/dart-sdk/bin:$PATH"
SQUINT="${SQUINT:-$(cd ../.. && pwd)/.probe-squint/node_modules/.bin/squint}"
declare -A got

got[jvm]=$(clojure -Sdeps '{:paths ["shared" "backends/jvm" "example"]}' -M -e \
  "(do (set! *warn-on-reflection* true) nil) (require 'ucl-example.fizzbuzz) (ucl-example.fizzbuzz/-main)" 2>jvm-stderr.txt)

rm -rf out-cljs
clojure -Sdeps '{:paths ["shared" "backends/cljs" "example"]
                 :deps {org.clojure/clojurescript {:mvn/version "1.12.42"}}}' \
  -M -m cljs.main -t node -d out-cljs -c ucl-example.fizzbuzz >/dev/null
got[cljs]=$(node -e "global.global=global;
require('./out-cljs/goog/bootstrap/nodejs.js'); require('./out-cljs/cljs_deps.js');
require('./out-cljs/ucl_example/fizzbuzz.js'); ucl_example.fizzbuzz._main();")

rm -rf out-squint && mkdir -p out-squint
ln -sfn "$(dirname "$(dirname "$SQUINT")")" out-squint/node_modules
(cd out-squint && echo '{:paths ["../shared" "../backends/squint" "../example"] :output-dir "js" :extension ".mjs"}' > squint.edn \
  && "$SQUINT" compile ../shared/ucl/contract.cljc ../backends/squint/ucl/api.cljc ../example/ucl_example/fizzbuzz.cljc >/dev/null)
got[squint]=$(cd out-squint && node -e "import('./js/ucl_example/fizzbuzz.mjs').then(m => m._main())")

clojure -M:cljd compile >/dev/null
got[cljd]=$(dart run 2>/dev/null)

for h in jvm cljs squint cljd; do printf '%-7s %s\n' "$h" "${got[$h]//$'\n'/ | }"; done
if [[ "${got[jvm]}" == "${got[cljs]}" && "${got[cljs]}" == "${got[squint]}" && "${got[squint]}" == "${got[cljd]}" ]]; then
  echo "ALL FOUR HOSTS AGREE"
else
  echo "HOSTS DISAGREE"; exit 1
fi
