// node exp/run.mjs <file.js>: maxProfit's input, as run.mjs; one file per process.
import { readFileSync } from 'node:fs';
const f = new Function(readFileSync(process.argv[2], 'utf8') + '\nreturn maxProfit;')();
const p = Array.from({length: 100000}, (_, i) => ((i * 7919) % 10007));
let r = f(p); const ws = [];
for (let k = 0; k < 50; k++) { const t = process.hrtime.bigint(); r = f(p); ws.push(Number(process.hrtime.bigint() - t) / 1000); }
ws.sort((a, b) => a - b); console.log(Math.round(ws[25]), r);
