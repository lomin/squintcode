// A closure call per element in hand-written Dart (README §9.10, H56). n = 10^5.
//   dart run hand.dart <loop|typed|dyn|arity|arityInt>                       (JIT)
//   dart compile exe hand.dart -o /tmp/hand && /tmp/hand <variant>           (AOT)
// loop      a for loop
// typed     fold over int Function(int, int), int accumulator
// dyn       fold over bare Function, dynamic accumulator (ucl's build, H55)
// arity     fold over dynamic Function(dynamic, dynamic)
// arityInt  arity, the closure casting to int instead of num
int loopSum(List<int> xs) { var a = 0; for (var i = 0; i < xs.length; i++) a += xs[i]; return a; }
int foldTyped(int Function(int, int) f, int init, List<int> xs) { var a = init; for (var i = 0; i < xs.length; i++) a = f(a, xs[i]); return a; }
dynamic foldDyn(Function f, dynamic init, List<int> xs) { dynamic a = init; for (var i = 0; i < xs.length; i++) a = f(a, xs[i]); return a; }
dynamic foldArity(dynamic Function(dynamic, dynamic) f, dynamic init, List<int> xs) { dynamic a = init; for (var i = 0; i < xs.length; i++) a = f(a, xs[i]); return a; }
void main(List<String> args) {
  final xs = List<int>.generate(100000, (i) => (i * 7919) % 10007);
  final run = <String, dynamic Function()>{
    'loop': () => loopSum(xs),
    'typed': () => foldTyped((int a, int x) => a + x, 0, xs),
    'dyn': () => foldDyn((dynamic a, dynamic x) => (a as num) + (x as num), 0, xs),
    'arity': () => foldArity((dynamic a, dynamic x) => (a as num) + (x as num), 0, xs),
    'arityInt': () => foldArity((dynamic a, dynamic x) => (a as int) + (x as int), 0, xs),
  }[args[0]]!;
  for (var i = 0; i < 20; i++) run();
  final ws = <int>[]; dynamic r;
  for (var i = 0; i < 200; i++) { final sw = Stopwatch()..start(); r = run(); ws.add(sw.elapsedMicroseconds); }
  ws.sort(); print('${args[0].padRight(9)} ${ws[100]} $r');
}
