// LeetCode 412 on Dart (README §9.9): the ucl build against hand-written Dart,
// and the differences between them one at a time. n = 10^4.
//   dart run fizzbuzz.dart [variant...]   or compile with `dart compile exe`
// ucl output as bundled
dynamic ucl(dynamic n$1, ){
final List<String> answer$1=(List<String>.filled((n$1 as int), "", ));
int i$1=0;
do {
if((i$1 < (n$1 as int))){
final int x$1=(1 + i$1);
late final String $if_$1;
if((0 == (x$1.remainder(15, )))){ $if_$1="FizzBuzz"; }
else if((0 == (x$1.remainder(3, )))){ $if_$1="Fizz"; }
else if((0 == (x$1.remainder(5, )))){ $if_$1="Buzz"; }
else{ $if_$1=(x$1.toString()); }
(answer$1[i$1]=$if_$1);
i$1=(1 + i$1);
continue;
}
return answer$1;
} while(true);
}
// ucl output, % instead of remainder
dynamic uclMod(dynamic n$1, ){
final List<String> answer$1=(List<String>.filled((n$1 as int), "", ));
int i$1=0;
do {
if((i$1 < (n$1 as int))){
final int x$1=(1 + i$1);
late final String $if_$1;
if((0 == (x$1 % 15))){ $if_$1="FizzBuzz"; }
else if((0 == (x$1 % 3))){ $if_$1="Fizz"; }
else if((0 == (x$1 % 5))){ $if_$1="Buzz"; }
else{ $if_$1=(x$1.toString()); }
(answer$1[i$1]=$if_$1);
i$1=(1 + i$1);
continue;
}
return answer$1;
} while(true);
}
// ucl output, % and a typed int parameter
dynamic uclModInt(int n$1, ){
final List<String> answer$1=(List<String>.filled(n$1, "", ));
int i$1=0;
do {
if((i$1 < n$1)){
final int x$1=(1 + i$1);
late final String $if_$1;
if((0 == (x$1 % 15))){ $if_$1="FizzBuzz"; }
else if((0 == (x$1 % 3))){ $if_$1="Fizz"; }
else if((0 == (x$1 % 5))){ $if_$1="Buzz"; }
else{ $if_$1=(x$1.toString()); }
(answer$1[i$1]=$if_$1);
i$1=(1 + i$1);
continue;
}
return answer$1;
} while(true);
}
List<String> hand(int n) {
  final answer = List<String>.filled(n, "");
  for (int i = 0; i < n; i++) {
    final x = i + 1;
    answer[i] = x % 15 == 0 ? "FizzBuzz" : x % 3 == 0 ? "Fizz" : x % 5 == 0 ? "Buzz" : x.toString();
  }
  return answer;
}
// hand, but remainder
List<String> handRem(int n) {
  final answer = List<String>.filled(n, "");
  for (int i = 0; i < n; i++) {
    final x = i + 1;
    answer[i] = x.remainder(15) == 0 ? "FizzBuzz" : x.remainder(3) == 0 ? "Fizz" : x.remainder(5) == 0 ? "Buzz" : x.toString();
  }
  return answer;
}

void main(List<String> args) {
  final fns = <String, dynamic Function(int)>{
    'ucl': (n) => ucl(n), 'uclMod': (n) => uclMod(n), 'uclModInt': (n) => uclModInt(n),
    'hand': hand, 'handRem': handRem,
  };
  final names = args.isEmpty ? fns.keys.toList() : args;
  const n = 10000, reps = 2000;
  var sink = 0;
  for (final name in names) {
    final f = fns[name]!;
    for (var w = 0; w < 200; w++) { sink += (f(n) as List).length; }
    final times = <int>[];
    for (var round = 0; round < 7; round++) {
      final sw = Stopwatch()..start();
      for (var r = 0; r < reps; r++) { sink += (f(n) as List).length; }
      times.add(sw.elapsedMicroseconds);
    }
    times.sort();
    print('${name.padRight(10)} median ${(times[3] / reps).toStringAsFixed(1)} us/call  min ${(times[0] / reps).toStringAsFixed(1)}');
  }
  if (sink == 42) print(sink);
}
