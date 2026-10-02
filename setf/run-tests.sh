#!/usr/bin/env bash
# Run the `setf` suite on every host this repository supports.
#
# Each target gets its OWN backend directory on its source path and nothing else,
# which is how backend selection works: same namespace, different file, one root
# per target. Mutual exclusion of those roots is the build-config invariant.
set -euo pipefail
cd "$(dirname "$0")"

CLJS_VERSION="${CLJS_VERSION:-1.12.42}"
SQUINT="${SQUINT:-$(cd .. && pwd)/.probe-squint/node_modules/.bin/squint}"

hr () { printf '\n%s\n%s\n%s\n' "======================================================" "$1" "======================================================"; }

hr "CLOJURE / JVM"
clojure -Sdeps '{:paths ["shared" "test" "backends/jvm"]}' -M -e \
  "(require 'setf.api-test 'clojure.test) (clojure.test/run-tests 'setf.api-test)"

hr "CLOJURESCRIPT $CLJS_VERSION"
rm -rf out-cljs-test
clojure -Sdeps "{:paths [\"shared\" \"test\" \"backends/cljs\"]
                 :deps {org.clojure/clojurescript {:mvn/version \"$CLJS_VERSION\"}}}" \
  -M -m cljs.main -t node -d out-cljs-test -c setf.runner
node -e "
global.global = global;
require('./out-cljs-test/goog/bootstrap/nodejs.js');
require('./out-cljs-test/cljs_deps.js');
require('./out-cljs-test/setf/runner.js');
setf.runner._main();"

hr "SQUINT"
rm -rf out
# Squint reads no macro format other than .cljc, and must be handed every source
# file in one invocation -- a subset silently emits imports for modules that were
# never compiled.
"$SQUINT" compile shared/setf/contract.cljc \
                  test/setf/api_test.cljc \
                  backends/squint/setf/api.cljc \
                  ../src/squintcode/macros.cljc
node -e "import('./out/setf/api_test.mjs').then(() => console.log('SQUINT TESTS RAN'))"

hr "EXAMPLE APP"
rm -rf out out-cljs
"$SQUINT" compile shared/setf/contract.cljc example/portable/app.cljc backends/squint/setf/api.cljc
node -e "import('./out/portable/app.mjs').then(m => m._main())"
clojure -Sdeps '{:paths ["shared" "example" "backends/jvm"]}' -M -e \
  "(require 'portable.app) (portable.app/-main)"
clojure -Sdeps '{:paths ["shared" "example" "backends/cljs"]
                 :deps {org.clojure/clojurescript {:mvn/version "1.12.42"}}}' \
  -M -m cljs.main -t node -d out-cljs -c portable.app
node -e "
global.global = global;
require('./out-cljs/goog/bootstrap/nodejs.js');
require('./out-cljs/cljs_deps.js');
require('./out-cljs/portable/app.js');
portable.app._main();"

hr "ALL HOSTS AGREE"
