// Runtime higher-order functions on Dart (probe_hof.dart, the safety-0 submission build).
//   dart run run.dart <fn> [solo|poly2|mega]                       (JIT)
//   dart compile exe run.dart -o /tmp/hof && /tmp/hof <fn> [mode]   (AOT)
import 'probe_hof.dart';

void main(List<String> args) {
  final fn = args[0], mode = args.length > 1 ? args[1] : 'solo';
  final n = 100000;
  final xs = List<int>.generate(n, (i) => (i * 7919) % 10007);
  final s = Solution();
  final warmers = <Function>[
    (a, x) => a + x, (a, x) => a ^ x, (a, x) => a > x ? a : x, (a, x) => a + (x & 1), (a, x) => a - x];
  final k = mode == 'poly2' ? 2 : mode == 'mega' ? 5 : 0;
  for (var r = 0; r < 200; r++) {
    for (var w = 0; w < k; w++) {
      s.fold(warmers[w], 0, xs);
      s.each((x) => warmers[w](0, x), xs);
    }
  }
  final run = <String, dynamic Function()>{
    'sumLoop': () => s.sumLoop(xs), 'sumFold': () => s.sumFold(xs),
    'countLoop': () => s.countLoop(xs, 5000), 'countFold': () => s.countFold(xs, 5000),
    'profitLoop': () => s.profitLoop(xs), 'profitEach': () => s.profitEach(xs),
    'profitEach2': () => s.profitEach2(xs)}[fn]!;
  var sw = Stopwatch()..start();
  var r = run();
  final cold = sw.elapsedMicroseconds;
  final ws = <int>[];
  for (var i = 0; i < 200; i++) {
    sw = Stopwatch()..start();
    r = run();
    ws.add(sw.elapsedMicroseconds);
  }
  ws.sort();
  print('{"fn":"$fn","mode":"$mode","cold":$cold,"warm":${ws[100]},"r":$r}');
}
