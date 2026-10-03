import "dart:typed_data" as d_typed_data;

dynamic squintcode_lc_2762_continuous_subarrays$continuousSubarrays(dynamic nums$1, ){
return squintcode_lc_2762_continuous_subarrays$count_steady_stretches((nums$1 as List<int>), 2, );
}


dynamic squintcode_lc_2762_continuous_subarrays$count_steady_stretches(dynamic nums$1, dynamic gap$1, ){
final int n$1=(nums$1 as List<int>).length;
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
final int t12241$1=maxt$1.v;
(maxq$1[t12241$1]=r$1);
final int t12242$1=mint$1.v;
(minq$1[t12242$1]=r$1);
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


class Solution {
  dynamic continuousSubarrays(dynamic nums_1) => squintcode_lc_2762_continuous_subarrays$continuousSubarrays(nums_1);
  dynamic count_steady_stretches(dynamic nums_1, dynamic gap_1) => squintcode_lc_2762_continuous_subarrays$count_steady_stretches(nums_1, gap_1);
}
