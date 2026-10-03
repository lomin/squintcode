const [mod, fn, input] = process.argv.slice(2);
const M = await import(mod);
const data = input === 'bits'
  ? Array.from({length: 3e4}, (_, i) => ((i * 7919) % 7) < 3 ? 1 : 0)
  : Array.from({length: 1e5}, (_, i) => (i * 7919) % 10001);
const call = input === 'bits' ? () => M[fn](data, 2) : () => M[fn](data);
let t = process.hrtime.bigint(); const ans = call(); const cold = Number(process.hrtime.bigint() - t) / 1e3;
for (let i = 0; i < 300; i++) call();
const ts = [];
for (let r = 0; r < 41; r++) { t = process.hrtime.bigint(); for (let k = 0; k < 20; k++) call(); ts.push(Number(process.hrtime.bigint() - t) / 20e3); }
ts.sort((a, b) => a - b);
console.log(JSON.stringify({cold, warm: ts[20], ans}));
