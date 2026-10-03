// Does ClojureDart's `dynamic` parameter, cast at every use (H25), cost
// anything? A 10^5-element sum (README §9.9).
//   dart run casts.dart dyn|typed   or compile with `dart compile exe`
import 'dart:typed_data';
// prefix-sum style kernel: what ClojureDart emits (dynamic params, casts at use) vs typed
dynamic dyn(dynamic a, dynamic n) {
  int s = 0; int i = 0;
  while (i < (n as int)) { s = s + ((a as List)[i] as int); i = 1 + i; }
  return s;
}
int typed(Int32List a, int n) {
  int s = 0;
  for (int i = 0; i < n; i++) { s += a[i]; }
  return s;
}
void main(List<String> args) {
  final a = Int32List(100000); for (var i = 0; i < a.length; i++) a[i] = i & 1023;
  final f = args[0] == 'dyn' ? (int n) => dyn(a, n) as int : (int n) => typed(a, n);
  var sink = 0;
  for (var w = 0; w < 300; w++) sink += f(a.length);
  final times = <int>[];
  for (var r = 0; r < 7; r++) { final sw = Stopwatch()..start(); for (var k = 0; k < 1000; k++) sink += f(a.length); times.add(sw.elapsedMicroseconds); }
  times.sort(); print('${args[0].padRight(6)} median ${(times[3]/1000).toStringAsFixed(1)} us/call');
  if (sink == 42) print(sink);
}
