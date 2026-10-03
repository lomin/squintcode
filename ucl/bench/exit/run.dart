// Early exit from a loop on Dart; the JS kernels.mjs, transcribed.
//   dart run run.dart <variant> <workload>   (or the AOT binary)
// short  10^5 calls, each exits after a few steps (10^5 exits)
// long   one call, exits at the last of 10^6 elements (1 exit)
// none   one call over 10^6 elements, never exits (try present, no throw)
import 'dart:convert';

int pos(List<int> a, int s, int x) {
  final n = a.length; var i = s;
  while (i < n) { if (a[i] > x) return i; i++; }
  return -1;
}

class _Exit { int v = 0; }
final _exit = _Exit();
int pre(List<int> a, int s, int x) {
  try {
    final n = a.length; var i = s;
    while (i < n) { if (a[i] > x) { _exit.v = i; throw _exit; } i++; }
    return -1;
  } on _Exit catch (e) { return e.v; }
}

class _Fresh { final int v; _Fresh(this.v); }
int fresh(List<int> a, int s, int x) {
  try {
    final n = a.length; var i = s;
    while (i < n) { if (a[i] > x) throw _Fresh(i); i++; }
    return -1;
  } on _Fresh catch (e) { return e.v; }
}

class _ExitError extends Error { final int v; _ExitError(this.v); }
int error(List<int> a, int s, int x) {
  try {
    final n = a.length; var i = s;
    while (i < n) { if (a[i] > x) throw _ExitError(i); i++; }
    return -1;
  } on _ExitError catch (e) { return e.v; }
}

int _splitLoop(List<int> a, int s, int x) {
  final n = a.length; var i = s;
  while (i < n) { if (a[i] > x) { _exit.v = i; throw _exit; } i++; }
  return -1;
}
int split(List<int> a, int s, int x) {
  try { return _splitLoop(a, s, x); } on _Exit catch (e) { return e.v; }
}

void main(List<String> args) {
  final variant = args[0], workload = args[1];
  final f = {'pos': pos, 'pre': pre, 'fresh': fresh, 'error': error, 'split': split}[variant]!;
  final n = workload == 'short' ? 100000 : 1000000;
  final a = List<int>.filled(n, 0, growable: true);
  var s = 12345;
  for (var i = 0; i < n; i++) { s = (s * 1103515245 + 12345) & 0x7fffffff; a[i] = s % 1000000000; }
  a[n - 1] = 1000000001;
  final run = {
    'short': () { var r = 0; for (var i = 0; i < n; i++) r += f(a, i, a[i]); return r; },
    'long': () => f(a, 0, 1000000000),
    'none': () => f(a, 0, 1000000001),
  }[workload]!;
  final sw = Stopwatch()..start();
  var r = run();
  final cold = sw.elapsedMicroseconds;
  final ws = <int>[];
  for (var i = 0; i < 100; i++) { sw.reset(); r = run(); ws.add(sw.elapsedMicroseconds); }
  ws.sort();
  print(jsonEncode({'variant': variant, 'workload': workload, 'cold': cold, 'warm': ws[50], 'r': r}));
}
