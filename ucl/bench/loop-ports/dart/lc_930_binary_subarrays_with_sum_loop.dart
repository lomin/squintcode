import "dart:typed_data" as d_typed_data;

dynamic squintcode_lc_930_binary_subarrays_with_sum_loop$numSubarraysWithSum(dynamic nums$1, dynamic goal$1, ){
final d_typed_data.Int32List freq$1=ucl_runtime$make_fixnum_vector((1 + (nums$1 as List<int>).length), );
(freq$1[0]=1);
final ucl_runtime$IntCell s$1=ucl_runtime$IntCell(0, );
final int len12305$1=(nums$1 as List<int>).length;
if((0 >= len12305$1)){
return 0;
}
int i12306$2=0;
int acc12304$2=0;
do {
final int x$1=((nums$1 as List<int>)[i12306$2]);
s$1.v=(s$1.v + x$1);
final bool test12308$1=(s$1.v >= (goal$1 as int));
int x12311$1;
if(test12308$1){
x12311$1=(freq$1[(s$1.v - (goal$1 as int))]);
}else{
x12311$1=0;
}
int acc12304$3;
if(test12308$1){
acc12304$3=(acc12304$2 + x12311$1);
}else{
acc12304$3=acc12304$2;
}
final int t12312$1=s$1.v;
final int v12313$1=((freq$1[t12312$1]) + 1);
(freq$1[t12312$1]=v12313$1);
final int i12306$3=(1 + i12306$2);
if((i12306$3 >= len12305$1)){
return acc12304$3;
}
i12306$2=i12306$3;
acc12304$2=acc12304$3;
continue;
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
  dynamic numSubarraysWithSum(dynamic nums_1, dynamic goal_1) => squintcode_lc_930_binary_subarrays_with_sum_loop$numSubarraysWithSum(nums_1, goal_1);
}
