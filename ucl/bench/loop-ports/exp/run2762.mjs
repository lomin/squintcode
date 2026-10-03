import { readFileSync } from 'node:fs';
const f = new Function(readFileSync(process.argv[2], 'utf8') + '\nreturn continuousSubarrays;')();
const a = Array.from({length: 100000}, (_, i) => ((i * 7919) % 5) + 1);
let r = f(a); const ws = [];
for (let k = 0; k < 50; k++) { const t = process.hrtime.bigint(); r = f(a); ws.push(Number(process.hrtime.bigint() - t) / 1000); }
ws.sort((x, y) => x - y); console.log(Math.round(ws[25]), r);
