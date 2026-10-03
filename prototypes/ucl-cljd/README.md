# Spike: does the `ucl` design carry to a fourth host?

Throwaway prototype answering one question: can `ucl`, as designed in
`ucl/README.md`, express `src/squintcode/fizzbuzz.cljc` on **ClojureDart** — a
host the design never considered — with nothing but a new backend directory?

**Answer: yes.** One unchanged source file,
`example/ucl_example/fizzbuzz.cljc`, runs on Clojure/JVM, ClojureScript, Squint
and ClojureDart with identical output, compiling to direct host operations on
each. Findings are recorded in `ucl/README.md` §14.

This is not `ucl` v1. It implements only what fizzbuzz needs: `elt`, `setf`,
`make-array` (all three element types) and `defun` + `declare`.

```
shared/ucl/contract.cljc          the contract: places, types, defun, defapi
backends/{jvm,cljs,squint,cljd}/  one emit map + (contract/defapi …) each
example/ucl_example/fizzbuzz.cljc the solution -- one plain require, no #?
src/host/                         ClojureDart entry point + the H25 hint probe
run-hosts.sh                      run all four hosts, require identical output
```

## Running

Needs the Dart SDK (tested with 3.13.5 at `~/.local/dart-sdk`), Clojure, Node,
and the probe Squint at `../../.probe-squint`. The first ClojureDart build runs
`clojure -M:cljd init`, which fetches Dart packages.

```bash
./run-hosts.sh
# jvm     fizzbuzz: 1,2,Fizz,4,Buzz,Fizz,7,8,Fizz,Buzz,11,Fizz,13,14,FizzBuzz | typed: 7
# cljs    …same…
# squint  …same…
# cljd    …same…
# ALL FOUR HOSTS AGREE
```

`run-hosts.sh` exits non-zero if any host's output differs. The JVM runs with
`*warn-on-reflection*`; a reflection warning would mean `ucl` failed to emit a
direct access.
