// Native bulk operations against the element loop ucl would expand to (README D45), Dart.
//   dart run native.dart <variant> <n>                         (JIT)
//   dart compile exe native.dart -o /tmp/nat && /tmp/nat <variant> <n>   (AOT)
import 'dart:typed_data';

void quicksort(List<int> a, int lo, int hi) {
  while (hi - lo > 16) {
    final m = (lo + hi) >> 1;
    final x = a[lo], y = a[m], z = a[hi];
    final p = x < y ? (y < z ? y : (x < z ? z : x)) : (x < z ? x : (y < z ? z : y));
    var i = lo, j = hi;
    while (i <= j) {
      while (a[i] < p) i++;
      while (p < a[j]) j--;
      if (i <= j) { final t = a[i]; a[i] = a[j]; a[j] = t; i++; j--; }
    }
    if (j - lo < hi - i) { quicksort(a, lo, j); lo = i; } else { quicksort(a, i, hi); hi = j; }
  }
  for (var i = lo + 1; i <= hi; i++) { final v = a[i]; var j = i - 1; while (j >= lo && v < a[j]) { a[j + 1] = a[j]; j--; } a[j + 1] = v; }
}
void mergesort(List<int> a, List<int> t, int n) {
  for (var w = 1; w < n; w *= 2) {
    for (var lo = 0; lo < n; lo += 2 * w) {
      final mid = lo + w < n ? lo + w : n, hi = lo + 2 * w < n ? lo + 2 * w : n;
      var i = lo, j = mid, k = lo;
      while (i < mid && j < hi) t[k++] = a[j] < a[i] ? a[j++] : a[i++];
      while (i < mid) t[k++] = a[i++];
      while (j < hi) t[k++] = a[j++];
    }
    for (var k = 0; k < n; k++) a[k] = t[k];
  }
}

void main(List<String> args) {
  final v = args[0], n = int.parse(args[1]);
  final reps = n <= 1000 ? 2000 : 1;
  int rnd(int i) => ((i * 7919 + 13) % 100003) - 50000;
  final srcI = Int32List(n); for (var i = 0; i < n; i++) srcI[i] = rnd(i);
  final srcL = List<int>.of(srcI);
  final dstI = Int32List(n), dstL = List<int>.filled(n, 0);
  final work = Int32List(n), workL = List<int>.filled(n, 0), tmp = Int32List(n);
  final s = n >> 2, e = n - (n >> 2);
  final variants = <String, int Function()>{
    'fill-loop-i32': () { for (var i = 0; i < n; i++) dstI[i] = 7; return dstI[n - 1]; },
    'fill-native-i32': () { dstI.fillRange(0, n, 7); return dstI[n - 1]; },
    'fill-loop-list': () { for (var i = 0; i < n; i++) dstL[i] = 7; return dstL[n - 1]; },
    'fill-native-list': () { dstL.fillRange(0, n, 7); return dstL[n - 1]; },
    'replace-loop-i32': () { for (var i = s; i < e; i++) dstI[i] = srcI[i]; return dstI[s]; },
    'replace-native-i32': () { dstI.setRange(s, e, srcI, s); return dstI[s]; },
    'replace-loop-list': () { for (var i = s; i < e; i++) dstL[i] = srcL[i]; return dstL[s]; },
    'replace-native-list': () { dstL.setRange(s, e, srcL, s); return dstL[s]; },
    'subseq-loop-i32': () { final r = Int32List(e - s); for (var i = s; i < e; i++) r[i - s] = srcI[i]; return r[0]; },
    'subseq-native-i32': () => srcI.sublist(s, e)[0],
    'subseq-loop-list': () { final r = List<int>.filled(e - s, 0); for (var i = s; i < e; i++) r[i - s] = srcL[i]; return r[0]; },
    'subseq-native-list': () => srcL.sublist(s, e)[0],
    'copy-only-i32': () { work.setRange(0, n, srcI); return work[0]; },
    'sort-native-i32': () { work.setRange(0, n, srcI); work.sort(); return work[0]; },
    'sort-native-cmp-i32': () { work.setRange(0, n, srcI); work.sort((a, b) => a - b); return work[0]; },
    'sort-inline-i32': () { work.setRange(0, n, srcI); quicksort(work, 0, n - 1); return work[0]; },
    'stable-inline-i32': () { work.setRange(0, n, srcI); mergesort(work, tmp, n); return work[0]; },
    'sort-native-list': () { workL.setRange(0, n, srcL); workL.sort(); return workL[0]; },
    'sort-native-cmp-list': () { workL.setRange(0, n, srcL); workL.sort((a, b) => a - b); return workL[0]; },
    'sort-inline-list': () { workL.setRange(0, n, srcL); quicksort(workL, 0, n - 1); return workL[0]; },
  };
  final f = variants[v]!;
  double sample() { final sw = Stopwatch()..start(); for (var k = 0; k < reps; k++) f(); return sw.elapsedMicroseconds / reps; }
  for (var i = 0; i < 20; i++) sample();
  final ws = [for (var i = 0; i < 100; i++) sample()]..sort();
  print('{"v":"$v","n":$n,"warm":${ws[50].toStringAsFixed(3)},"r":${f()}}');
}
