// Appended to a Dart submission by each-dart.sh, with dart-main/<problem>.dart
// (its input and call, as run.mjs). LeetCode's ListNode, and a timer.
class ListNode { dynamic val; ListNode? next; ListNode([this.val = 0, this.next]); }
int rnd(int i, int m) => (i * 7919) % m;
void main(List<String> args) {
  final run = benchCase();
  final sw = Stopwatch()..start();
  var r = run(); final cold = sw.elapsedMicroseconds;
  final ws = <int>[];
  for (var k = 0; k < 50; k++) { sw.reset(); r = run(); ws.add(sw.elapsedMicroseconds); }
  ws.sort();
  print('{"cold":$cold,"warm":${ws[25]},"r":$r}');
}
