#!/usr/bin/env bash
# Run the `setf` suite on every host this repository supports.
#
# Each target gets its OWN backend directory on its source path and nothing else,
# which is how backend selection works: same namespace, different file, one root
# per target. Mutual exclusion of those roots is the build-config invariant.
#
# Any test failure on any host exits non-zero, and the example app's output must
# be identical on every host.
set -euo pipefail
cd "$(dirname "$0")"

CLJS_VERSION="${CLJS_VERSION:-1.12.42}"
SQUINT="${SQUINT:-$(cd .. && pwd)/.probe-squint/node_modules/.bin/squint}"

hr () { printf '\n%s\n%s\n%s\n' "======================================================" "$1" "======================================================"; }

clj () {   # clj <backend> <expr>
  clojure -Sdeps "{:paths [\"shared\" \"test\" \"example\" \"backends/$1\"]}" -M -e "$2"
}

cljs_build () {   # cljs_build <out-dir> <main-ns>
  rm -rf "$1"
  clojure -Sdeps "{:paths [\"shared\" \"test\" \"example\" \"backends/cljs\"]
                   :deps {org.clojure/clojurescript {:mvn/version \"$CLJS_VERSION\"}}}" \
    -M -m cljs.main -t node -d "$1" -c "$2"
}

cljs_run () {   # cljs_run <out-dir> <ns-path> <js-call>
  node -e "
global.global = global;
require('./$1/goog/bootstrap/nodejs.js');
require('./$1/cljs_deps.js');
require('./$1/$2.js');
$3;"
}

squint_build () {   # every source file in ONE invocation -- see README §13
  rm -rf out
  "$SQUINT" compile "$@"
}

hr "CLOJURE / JVM"
clj jvm "(require 'setf.api-test 'clojure.test)
         (let [r (clojure.test/run-tests 'setf.api-test)]
           (shutdown-agents)
           (System/exit (if (clojure.test/successful? r) 0 1)))"

hr "CLOJURESCRIPT $CLJS_VERSION"
cljs_build out-cljs-test setf.runner
cljs_run out-cljs-test setf/runner "setf.runner._main()"

hr "SQUINT"
# Squint reads no macro format other than .cljc. The test macros come from this
# repository's squintcode.macros; an assertion failure exits 1.
squint_build shared/setf/contract.cljc test/setf/api_test.cljc \
             backends/squint/setf/api.cljc ../src/squintcode/macros.cljc
node -e "import('./out/setf/api_test.mjs').then(() => console.log('SQUINT TESTS RAN'))"

hr "EXAMPLE APP (must print identically on every host)"
squint_build shared/setf/contract.cljc example/portable/app.cljc backends/squint/setf/api.cljc
cljs_build out-cljs portable.app

declare -A got
got[squint]=$(node -e "import('./out/portable/app.mjs').then(m => { m._main(); m._main_hof(); })")
got[jvm]=$(clj jvm "(require 'portable.app) (portable.app/-main) (portable.app/-main-hof)")
got[cljs]=$(cljs_run out-cljs portable/app "portable.app._main(); portable.app._main_hof()")

for host in squint jvm cljs; do
  printf '%-7s %s\n' "$host" "${got[$host]//$'\n'/$'\n        '}"
done

if [[ "${got[squint]}" == "${got[jvm]}" && "${got[jvm]}" == "${got[cljs]}" ]]; then
  hr "ALL HOSTS AGREE"
else
  hr "HOSTS DISAGREE"
  exit 1
fi
