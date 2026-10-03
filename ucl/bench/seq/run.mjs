// LeetCode 1295 (README §4.2): the loop/recur original against the ucl/count-if port,
// both as bb build submits them (loop.js, seq.js). node run.mjs <loop|seq>
import { readFileSync } from 'node:fs';
const v = process.argv[2];
const f = new Function(readFileSync(new URL(`./${v}.js`, import.meta.url), 'utf8') + '\nreturn findNumbers;')();
const xs = Array.from({ length: 100000 }, (_, i) => 1 + ((i * 7919) % 100000));
let t = process.hrtime.bigint(); let r = f(xs); const cold = Number(process.hrtime.bigint() - t) / 1000;
const ws = []; for (let i = 0; i < 200; i++) { t = process.hrtime.bigint(); r = f(xs); ws.push(Number(process.hrtime.bigint() - t) / 1000); }
ws.sort((a, b) => a - b);
console.log(JSON.stringify({ v, cold: Math.round(cold), warm: Math.round(ws[100]), r }));
