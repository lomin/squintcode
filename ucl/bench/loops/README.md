# Loop benchmarks (D20, D21)

Evidence for retiring `aloop`/`forv` (D20) and for inline `min`/`max` (D21).

- `faithful_recur.cljc` — a `loop`/`recur` rewrite of every `aloop`/`forv` in
  `src/squintcode/`, keeping each original body and helper call, so only the
  loop construct differs.
- `originals303.cljc` — `lc_303`'s private `build-prefix-sum`, copied verbatim
  and made public so it can be called directly.
- `inline_variants.cljc` — `maxProfit` with `squint_core.min`/`max` vs an
  inline comparison, in both loop styles.
- `solo3.mjs` — runs ONE (problem, variant) per fresh Node process, as LeetCode
  runs one submission. `solo.mjs` is the same harness for single functions.

These scripts compare against `src/squintcode/macros.cljc`, which D27 deletes.
They are kept as the record of how the measurement was made; to rerun them,
check out a commit that still has `macros.cljc`.

Setup used (Squint 0.14.210, Node 24.16.0): copy `macros.cljc`, `utils.cljc`,
`fizzbuzz.cljc`, `maxprofit.cljc`, `lc_560_*.cljc`, `lc_930_*.cljc` and the files
here into one `src/squintcode/`, compile them all in one Squint invocation, then:

```bash
node solo3.mjs --list
node solo3.mjs '121 maxProfit' aloop     # or: recur
node solo.mjs ./out/squintcode/inline_variants.mjs loop_inline prices
```
