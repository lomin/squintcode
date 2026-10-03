// LeetCode 2762 on Dart (README §9.9): where the ucl build's time goes.
//   ucl            out/lc_2762_continuous_subarrays.dart as built with nums declared
//                  simple-vector (before D42): cells, dynamic elements, ClojureDart's `and`
//   locals         the same, every IntCell replaced by a plain int local
//   typed          locals, plus nums read as List<int>
//   and            typed, plus `&&` where ClojureDart emits a `late final bool` (H48)
//   fixnum-vector  the build with nums declared fixnum-vector (D42)
//   no-late        the build once the bundler declares forked locals without `late` (I22)
//   hand           hand-written Dart
// n = 10^5 values in 1..5. Run each variant in its own process:
//   dart run lc2762.dart <variant>                       (JIT)
//   dart compile exe lc2762.dart -o /tmp/b && /tmp/b <variant>   (AOT)
import "dart:typed_data" as d_typed_data;
import "dart:typed_data";
import "dart:math";

dynamic squintcode_lc_2762_continuous_subarrays$continuousSubarrays(dynamic nums$1, ){
return squintcode_lc_2762_continuous_subarrays$count_steady_stretches((nums$1 as List), 2, );
}


dynamic squintcode_lc_2762_continuous_subarrays$count_steady_stretches(dynamic nums$1, dynamic gap$1, ){
final int n$1=(nums$1 as List).length;
final d_typed_data.Int32List maxq$1=ucl_runtime$make_fixnum_vector(n$1, );
final d_typed_data.Int32List minq$1=ucl_runtime$make_fixnum_vector(n$1, );
final ucl_runtime$IntCell left$1=ucl_runtime$IntCell(0, );
final ucl_runtime$IntCell maxh$1=ucl_runtime$IntCell(0, );
final ucl_runtime$IntCell maxt$1=ucl_runtime$IntCell(0, );
final ucl_runtime$IntCell minh$1=ucl_runtime$IntCell(0, );
final ucl_runtime$IntCell mint$1=ucl_runtime$IntCell(0, );
final ucl_runtime$IntCell total$1=ucl_runtime$IntCell(0, );
int r$1=0;
do {
if((r$1 < n$1)){
final dynamic x$1=((nums$1 as List)[r$1]);
do {
final bool and$6958_$AUTO_$1=(minh$1.v < mint$1.v);
late final bool $if_$1;
if(and$6958_$AUTO_$1){
$if_$1=(((x$1 as num) - (((nums$1 as List)[(minq$1[minh$1.v])]) as num)) > (gap$1 as int));
}else{
$if_$1=and$6958_$AUTO_$1;
}
if($if_$1){
left$1.v=(1 + (minq$1[minh$1.v]));
minh$1.v=(minh$1.v + 1);
continue;
}else{
}
break;
} while(true);
do {
final bool and$6958_$AUTO_$2=(maxh$1.v < maxt$1.v);
late final bool $if_$2;
if(and$6958_$AUTO_$2){
$if_$2=(((((nums$1 as List)[(maxq$1[maxh$1.v])]) as num) - (x$1 as num)) > (gap$1 as int));
}else{
$if_$2=and$6958_$AUTO_$2;
}
if($if_$2){
left$1.v=(1 + (maxq$1[maxh$1.v]));
maxh$1.v=(maxh$1.v + 1);
continue;
}else{
}
break;
} while(true);
do {
final bool and$6958_$AUTO_$3=(maxh$1.v < maxt$1.v);
late final bool $if_$3;
if(and$6958_$AUTO_$3){
$if_$3=((((nums$1 as List)[(maxq$1[(maxt$1.v - 1)])]) as num) <= (x$1 as num));
}else{
$if_$3=and$6958_$AUTO_$3;
}
if($if_$3){
maxt$1.v=(maxt$1.v - 1);
continue;
}else{
}
break;
} while(true);
do {
final bool and$6958_$AUTO_$4=(minh$1.v < mint$1.v);
late final bool $if_$4;
if(and$6958_$AUTO_$4){
$if_$4=((((nums$1 as List)[(minq$1[(mint$1.v - 1)])]) as num) >= (x$1 as num));
}else{
$if_$4=and$6958_$AUTO_$4;
}
if($if_$4){
mint$1.v=(mint$1.v - 1);
continue;
}else{
}
break;
} while(true);
final int t10558$1=maxt$1.v;
(maxq$1[t10558$1]=r$1);
final int t10559$1=mint$1.v;
(minq$1[t10559$1]=r$1);
maxt$1.v=(maxt$1.v + 1);
mint$1.v=(mint$1.v + 1);
total$1.v=(total$1.v + ((r$1 - left$1.v) - -1));
r$1=(1 + r$1);
continue;
}
return total$1.v;
} while(true);
}


class ucl_runtime$IntCell extends Object {
int v;

ucl_runtime$IntCell(this.v, ):super();
}


d_typed_data.Int32List ucl_runtime$make_fixnum_vector(dynamic n$1, ){
return d_typed_data.Int32List((n$1 as int), );
}




dynamic loc_continuousSubarrays(dynamic nums$1, ){
return loc_count_steady_stretches((nums$1 as List), 2, );
}


dynamic loc_count_steady_stretches(dynamic nums$1, dynamic gap$1, ){
final int n$1=(nums$1 as List).length;
final d_typed_data.Int32List maxq$1=ucl_runtime$make_fixnum_vector(n$1, );
final d_typed_data.Int32List minq$1=ucl_runtime$make_fixnum_vector(n$1, );
int left$1v=0;
int maxh$1v=0;
int maxt$1v=0;
int minh$1v=0;
int mint$1v=0;
int total$1v=0;
int r$1=0;
do {
if((r$1 < n$1)){
final dynamic x$1=((nums$1 as List)[r$1]);
do {
final bool and$6958_$AUTO_$1=(minh$1v < mint$1v);
late final bool $if_$1;
if(and$6958_$AUTO_$1){
$if_$1=(((x$1 as num) - (((nums$1 as List)[(minq$1[minh$1v])]) as num)) > (gap$1 as int));
}else{
$if_$1=and$6958_$AUTO_$1;
}
if($if_$1){
left$1v=(1 + (minq$1[minh$1v]));
minh$1v=(minh$1v + 1);
continue;
}else{
}
break;
} while(true);
do {
final bool and$6958_$AUTO_$2=(maxh$1v < maxt$1v);
late final bool $if_$2;
if(and$6958_$AUTO_$2){
$if_$2=(((((nums$1 as List)[(maxq$1[maxh$1v])]) as num) - (x$1 as num)) > (gap$1 as int));
}else{
$if_$2=and$6958_$AUTO_$2;
}
if($if_$2){
left$1v=(1 + (maxq$1[maxh$1v]));
maxh$1v=(maxh$1v + 1);
continue;
}else{
}
break;
} while(true);
do {
final bool and$6958_$AUTO_$3=(maxh$1v < maxt$1v);
late final bool $if_$3;
if(and$6958_$AUTO_$3){
$if_$3=((((nums$1 as List)[(maxq$1[(maxt$1v - 1)])]) as num) <= (x$1 as num));
}else{
$if_$3=and$6958_$AUTO_$3;
}
if($if_$3){
maxt$1v=(maxt$1v - 1);
continue;
}else{
}
break;
} while(true);
do {
final bool and$6958_$AUTO_$4=(minh$1v < mint$1v);
late final bool $if_$4;
if(and$6958_$AUTO_$4){
$if_$4=((((nums$1 as List)[(minq$1[(mint$1v - 1)])]) as num) >= (x$1 as num));
}else{
$if_$4=and$6958_$AUTO_$4;
}
if($if_$4){
mint$1v=(mint$1v - 1);
continue;
}else{
}
break;
} while(true);
final int t10558$1=maxt$1v;
(maxq$1[t10558$1]=r$1);
final int t10559$1=mint$1v;
(minq$1[t10559$1]=r$1);
maxt$1v=(maxt$1v + 1);
mint$1v=(mint$1v + 1);
total$1v=(total$1v + ((r$1 - left$1v) - -1));
r$1=(1 + r$1);
continue;
}
return total$1v;
} while(true);
}


dynamic typ_continuousSubarrays(dynamic nums$1, ){
return typ_count_steady_stretches((nums$1 as List<int>), 2, );
}


dynamic typ_count_steady_stretches(dynamic nums$1, dynamic gap$1, ){
final int n$1=(nums$1 as List<int>).length;
final d_typed_data.Int32List maxq$1=ucl_runtime$make_fixnum_vector(n$1, );
final d_typed_data.Int32List minq$1=ucl_runtime$make_fixnum_vector(n$1, );
int left$1v=0;
int maxh$1v=0;
int maxt$1v=0;
int minh$1v=0;
int mint$1v=0;
int total$1v=0;
int r$1=0;
do {
if((r$1 < n$1)){
final int x$1=((nums$1 as List<int>)[r$1]);
do {
final bool and$6958_$AUTO_$1=(minh$1v < mint$1v);
late final bool $if_$1;
if(and$6958_$AUTO_$1){
$if_$1=(((x$1 as int) - (((nums$1 as List<int>)[(minq$1[minh$1v])]) as int)) > (gap$1 as int));
}else{
$if_$1=and$6958_$AUTO_$1;
}
if($if_$1){
left$1v=(1 + (minq$1[minh$1v]));
minh$1v=(minh$1v + 1);
continue;
}else{
}
break;
} while(true);
do {
final bool and$6958_$AUTO_$2=(maxh$1v < maxt$1v);
late final bool $if_$2;
if(and$6958_$AUTO_$2){
$if_$2=(((((nums$1 as List<int>)[(maxq$1[maxh$1v])]) as int) - (x$1 as int)) > (gap$1 as int));
}else{
$if_$2=and$6958_$AUTO_$2;
}
if($if_$2){
left$1v=(1 + (maxq$1[maxh$1v]));
maxh$1v=(maxh$1v + 1);
continue;
}else{
}
break;
} while(true);
do {
final bool and$6958_$AUTO_$3=(maxh$1v < maxt$1v);
late final bool $if_$3;
if(and$6958_$AUTO_$3){
$if_$3=((((nums$1 as List<int>)[(maxq$1[(maxt$1v - 1)])]) as int) <= (x$1 as int));
}else{
$if_$3=and$6958_$AUTO_$3;
}
if($if_$3){
maxt$1v=(maxt$1v - 1);
continue;
}else{
}
break;
} while(true);
do {
final bool and$6958_$AUTO_$4=(minh$1v < mint$1v);
late final bool $if_$4;
if(and$6958_$AUTO_$4){
$if_$4=((((nums$1 as List<int>)[(minq$1[(mint$1v - 1)])]) as int) >= (x$1 as int));
}else{
$if_$4=and$6958_$AUTO_$4;
}
if($if_$4){
mint$1v=(mint$1v - 1);
continue;
}else{
}
break;
} while(true);
final int t10558$1=maxt$1v;
(maxq$1[t10558$1]=r$1);
final int t10559$1=mint$1v;
(minq$1[t10559$1]=r$1);
maxt$1v=(maxt$1v + 1);
mint$1v=(mint$1v + 1);
total$1v=(total$1v + ((r$1 - left$1v) - -1));
r$1=(1 + r$1);
continue;
}
return total$1v;
} while(true);
}


dynamic and_continuousSubarrays(dynamic nums$1, ){
return and_count_steady_stretches((nums$1 as List<int>), 2, );
}


dynamic and_count_steady_stretches(dynamic nums$1, dynamic gap$1, ){
final int n$1=(nums$1 as List<int>).length;
final d_typed_data.Int32List maxq$1=ucl_runtime$make_fixnum_vector(n$1, );
final d_typed_data.Int32List minq$1=ucl_runtime$make_fixnum_vector(n$1, );
int left$1v=0;
int maxh$1v=0;
int maxt$1v=0;
int minh$1v=0;
int mint$1v=0;
int total$1v=0;
int r$1=0;
do {
if((r$1 < n$1)){
final int x$1=((nums$1 as List<int>)[r$1]);
do {
if(((minh$1v < mint$1v)) && ((((x$1 as int) - (((nums$1 as List<int>)[(minq$1[minh$1v])]) as int)) > (gap$1 as int)))){
left$1v=(1 + (minq$1[minh$1v]));
minh$1v=(minh$1v + 1);
continue;
}else{
}
break;
} while(true);
do {
if(((maxh$1v < maxt$1v)) && ((((((nums$1 as List<int>)[(maxq$1[maxh$1v])]) as int) - (x$1 as int)) > (gap$1 as int)))){
left$1v=(1 + (maxq$1[maxh$1v]));
maxh$1v=(maxh$1v + 1);
continue;
}else{
}
break;
} while(true);
do {
if(((maxh$1v < maxt$1v)) && (((((nums$1 as List<int>)[(maxq$1[(maxt$1v - 1)])]) as int) <= (x$1 as int)))){
maxt$1v=(maxt$1v - 1);
continue;
}else{
}
break;
} while(true);
do {
if(((minh$1v < mint$1v)) && (((((nums$1 as List<int>)[(minq$1[(mint$1v - 1)])]) as int) >= (x$1 as int)))){
mint$1v=(mint$1v - 1);
continue;
}else{
}
break;
} while(true);
final int t10558$1=maxt$1v;
(maxq$1[t10558$1]=r$1);
final int t10559$1=mint$1v;
(minq$1[t10559$1]=r$1);
maxt$1v=(maxt$1v + 1);
mint$1v=(mint$1v + 1);
total$1v=(total$1v + ((r$1 - left$1v) - -1));
r$1=(1 + r$1);
continue;
}
return total$1v;
} while(true);
}



dynamic v2_continuousSubarrays(dynamic nums$1, ){
return v2_count_steady_stretches((nums$1 as List<int>), 2, );
}


dynamic v2_count_steady_stretches(dynamic nums$1, dynamic gap$1, ){
final int n$1=(nums$1 as List<int>).length;
final d_typed_data.Int32List maxq$1=v2rt_make_fixnum_vector(n$1, );
final d_typed_data.Int32List minq$1=v2rt_make_fixnum_vector(n$1, );
final v2rt_IntCell left$1=v2rt_IntCell(0, );
final v2rt_IntCell maxh$1=v2rt_IntCell(0, );
final v2rt_IntCell maxt$1=v2rt_IntCell(0, );
final v2rt_IntCell minh$1=v2rt_IntCell(0, );
final v2rt_IntCell mint$1=v2rt_IntCell(0, );
final v2rt_IntCell total$1=v2rt_IntCell(0, );
int r$1=0;
do {
if((r$1 < n$1)){
final int x$1=((nums$1 as List<int>)[r$1]);
do {
final bool and$6958_$AUTO_$1=(minh$1.v < mint$1.v);
late final bool $if_$1;
if(and$6958_$AUTO_$1){
$if_$1=((x$1 - ((nums$1 as List<int>)[(minq$1[minh$1.v])])) > (gap$1 as int));
}else{
$if_$1=and$6958_$AUTO_$1;
}
if($if_$1){
left$1.v=(1 + (minq$1[minh$1.v]));
minh$1.v=(minh$1.v + 1);
continue;
}else{
}
break;
} while(true);
do {
final bool and$6958_$AUTO_$2=(maxh$1.v < maxt$1.v);
late final bool $if_$2;
if(and$6958_$AUTO_$2){
$if_$2=((((nums$1 as List<int>)[(maxq$1[maxh$1.v])]) - x$1) > (gap$1 as int));
}else{
$if_$2=and$6958_$AUTO_$2;
}
if($if_$2){
left$1.v=(1 + (maxq$1[maxh$1.v]));
maxh$1.v=(maxh$1.v + 1);
continue;
}else{
}
break;
} while(true);
do {
final bool and$6958_$AUTO_$3=(maxh$1.v < maxt$1.v);
late final bool $if_$3;
if(and$6958_$AUTO_$3){
$if_$3=(((nums$1 as List<int>)[(maxq$1[(maxt$1.v - 1)])]) <= x$1);
}else{
$if_$3=and$6958_$AUTO_$3;
}
if($if_$3){
maxt$1.v=(maxt$1.v - 1);
continue;
}else{
}
break;
} while(true);
do {
final bool and$6958_$AUTO_$4=(minh$1.v < mint$1.v);
late final bool $if_$4;
if(and$6958_$AUTO_$4){
$if_$4=(((nums$1 as List<int>)[(minq$1[(mint$1.v - 1)])]) >= x$1);
}else{
$if_$4=and$6958_$AUTO_$4;
}
if($if_$4){
mint$1.v=(mint$1.v - 1);
continue;
}else{
}
break;
} while(true);
final int t10558$1=maxt$1.v;
(maxq$1[t10558$1]=r$1);
final int t10559$1=mint$1.v;
(minq$1[t10559$1]=r$1);
maxt$1.v=(maxt$1.v + 1);
mint$1.v=(mint$1.v + 1);
total$1.v=(total$1.v + ((r$1 - left$1.v) - -1));
r$1=(1 + r$1);
continue;
}
return total$1.v;
} while(true);
}


class v2rt_IntCell extends Object {
int v;

v2rt_IntCell(this.v, ):super();
}


d_typed_data.Int32List v2rt_make_fixnum_vector(dynamic n$1, ){
return d_typed_data.Int32List((n$1 as int), );
}




dynamic v3_continuousSubarrays(dynamic nums$1, ){
return v3_count_steady_stretches((nums$1 as List<int>), 2, );
}


dynamic v3_count_steady_stretches(dynamic nums$1, dynamic gap$1, ){
final int n$1=(nums$1 as List<int>).length;
final d_typed_data.Int32List maxq$1=v2rt_make_fixnum_vector(n$1, );
final d_typed_data.Int32List minq$1=v2rt_make_fixnum_vector(n$1, );
final v2rt_IntCell left$1=v2rt_IntCell(0, );
final v2rt_IntCell maxh$1=v2rt_IntCell(0, );
final v2rt_IntCell maxt$1=v2rt_IntCell(0, );
final v2rt_IntCell minh$1=v2rt_IntCell(0, );
final v2rt_IntCell mint$1=v2rt_IntCell(0, );
final v2rt_IntCell total$1=v2rt_IntCell(0, );
int r$1=0;
do {
if((r$1 < n$1)){
final int x$1=((nums$1 as List<int>)[r$1]);
do {
final bool and$6958_$AUTO_$1=(minh$1.v < mint$1.v);
bool $if_$1;
if(and$6958_$AUTO_$1){
$if_$1=((x$1 - ((nums$1 as List<int>)[(minq$1[minh$1.v])])) > (gap$1 as int));
}else{
$if_$1=and$6958_$AUTO_$1;
}
if($if_$1){
left$1.v=(1 + (minq$1[minh$1.v]));
minh$1.v=(minh$1.v + 1);
continue;
}else{
}
break;
} while(true);
do {
final bool and$6958_$AUTO_$2=(maxh$1.v < maxt$1.v);
bool $if_$2;
if(and$6958_$AUTO_$2){
$if_$2=((((nums$1 as List<int>)[(maxq$1[maxh$1.v])]) - x$1) > (gap$1 as int));
}else{
$if_$2=and$6958_$AUTO_$2;
}
if($if_$2){
left$1.v=(1 + (maxq$1[maxh$1.v]));
maxh$1.v=(maxh$1.v + 1);
continue;
}else{
}
break;
} while(true);
do {
final bool and$6958_$AUTO_$3=(maxh$1.v < maxt$1.v);
bool $if_$3;
if(and$6958_$AUTO_$3){
$if_$3=(((nums$1 as List<int>)[(maxq$1[(maxt$1.v - 1)])]) <= x$1);
}else{
$if_$3=and$6958_$AUTO_$3;
}
if($if_$3){
maxt$1.v=(maxt$1.v - 1);
continue;
}else{
}
break;
} while(true);
do {
final bool and$6958_$AUTO_$4=(minh$1.v < mint$1.v);
bool $if_$4;
if(and$6958_$AUTO_$4){
$if_$4=(((nums$1 as List<int>)[(minq$1[(mint$1.v - 1)])]) >= x$1);
}else{
$if_$4=and$6958_$AUTO_$4;
}
if($if_$4){
mint$1.v=(mint$1.v - 1);
continue;
}else{
}
break;
} while(true);
final int t10558$1=maxt$1.v;
(maxq$1[t10558$1]=r$1);
final int t10559$1=mint$1.v;
(minq$1[t10559$1]=r$1);
maxt$1.v=(maxt$1.v + 1);
mint$1.v=(mint$1.v + 1);
total$1.v=(total$1.v + ((r$1 - left$1.v) - -1));
r$1=(1 + r$1);
continue;
}
return total$1.v;
} while(true);
}


int hand(List<int> nums) {
  final n = nums.length;
  final maxq = Int32List(n), minq = Int32List(n);
  int left = 0, maxh = 0, maxt = 0, minh = 0, mint = 0, total = 0;
  for (int r = 0; r < n; r++) {
    final x = nums[r];
    while (minh < mint && x - nums[minq[minh]] > 2) { left = minq[minh] + 1; minh++; }
    while (maxh < maxt && nums[maxq[maxh]] - x > 2) { left = maxq[maxh] + 1; maxh++; }
    while (maxh < maxt && nums[maxq[maxt - 1]] <= x) maxt--;
    while (minh < mint && nums[minq[mint - 1]] >= x) mint--;
    maxq[maxt++] = r; minq[mint++] = r;
    total += r - left + 1;
  }
  return total;
}

void main(List<String> args) {
  final rnd = Random(42);
  final nums = List<int>.generate(100000, (_) => 1 + rnd.nextInt(5));   // many long stretches
  final fns = <String, int Function(List<int>)>{
    'ucl': (a) => squintcode_lc_2762_continuous_subarrays$continuousSubarrays(a) as int,
    'locals': (a) => loc_continuousSubarrays(a) as int,
    'hand': hand,
    'no-late': (a) => v3_continuousSubarrays(a) as int,
    'fixnum-vector': (a) => v2_continuousSubarrays(a) as int,
    'and': (a) => and_continuousSubarrays(a) as int,
    'typed': (a) => typ_continuousSubarrays(a) as int,
  };
  final f = fns[args[0]]!;
  var sink = 0;
  for (var w = 0; w < 300; w++) sink += f(nums);
  final times = <int>[];
  for (var r = 0; r < 9; r++) { final sw = Stopwatch()..start(); for (var k = 0; k < 100; k++) sink += f(nums); times.add(sw.elapsedMicroseconds); }
  times.sort();
  print('${args[0].padRight(7)} median ${(times[4] / 100).toStringAsFixed(0)} us/call  (answer ${f(nums)})');
}
