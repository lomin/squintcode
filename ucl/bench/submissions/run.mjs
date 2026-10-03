import { readFileSync } from 'node:fs';
const [,, variant, problem] = process.argv;
class ListNode { constructor(val, next) { this.val = val === undefined ? 0 : val; this.next = next === undefined ? null : next; } }
globalThis.ListNode = ListNode;
const files = { fizzBuzz: 'fizzbuzz', maxProfit: 'maxprofit', NumArray: 'lc_303_range_sum_query_immutable',
  subarraySum: 'lc_560_subarray_sum_equals_k', numSubarraysWithSum: 'lc_930_binary_subarrays_with_sum',
  removeNthFromEnd: 'lc_19_remove_nth_node_from_end_of_list' };
const src = readFileSync(`./${variant}/${files[problem]}.js`, 'utf8');
const f = new Function('ListNode', src + `\nreturn ${problem};`)(ListNode);
const rnd = (i, m) => ((i * 7919) % m);
const cases = {
  fizzBuzz: () => f(10000),
  maxProfit: (() => { const p = Array.from({length: 100000}, (_, i) => rnd(i, 10007)); return () => f(p); })(),
  NumArray: (() => { const a = Array.from({length: 10000}, (_, i) => rnd(i, 20001) - 10000);
    return () => { const o = new f(a); let s = 0; for (let q = 0; q < 10000; q++) s += o.sumRange(q % 5000, 5000 + (q % 4999)); return s; }; })(),
  subarraySum: (() => { const a = Array.from({length: 20000}, (_, i) => rnd(i, 2001) - 1000); return () => f(a, 7); })(),
  numSubarraysWithSum: (() => { const a = Array.from({length: 30000}, (_, i) => rnd(i, 3) === 0 ? 1 : 0); return () => f(a, 50); })(),
  removeNthFromEnd: () => { let h = null; for (let i = 0; i < 100000; i++) h = new ListNode(i, h); return f(h, 5000); },
};
const run = cases[problem];
const t0 = process.hrtime.bigint(); let r = run(); const cold = Number(process.hrtime.bigint() - t0) / 1000;
const ws = []; for (let k = 0; k < 50; k++) { const t = process.hrtime.bigint(); r = run(); ws.push(Number(process.hrtime.bigint() - t) / 1000); }
ws.sort((a, b) => a - b);
console.log(JSON.stringify({ cold, warm: ws[25], r: typeof r === 'object' ? (r && (r.length ?? r.val)) : r }));
