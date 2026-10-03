import "dart:typed_data" as d_typed_data;

dynamic squintcode_lc_930_binary_subarrays_with_sum$numSubarraysWithSum(dynamic nums$1, dynamic goal$1, ){
final int n$1=(nums$1 as List<int>).length;
final d_typed_data.Int32List freq$1=ucl_runtime$make_fixnum_vector((1 + n$1), );
(freq$1[0]=1);
dynamic i$1=0;
dynamic running_sum$1=0;
dynamic result$1=0;
do {
if(((i$1 as num) < n$1)){
final num running_sum$2=((running_sum$1 as num) + ((nums$1 as List<int>)[(i$1 as int)]));
final num want$1=(running_sum$2 - (goal$1 as num));
dynamic result$2;
if((want$1 >= 0)){
result$2=((result$1 as num) + (freq$1[(want$1 as int)]));
}else{
result$2=result$1;
}
final int v12230$1=((freq$1[(running_sum$2 as int)]) + 1);
(freq$1[(running_sum$2 as int)]=v12230$1);
i$1=(1 + (i$1 as num));
running_sum$1=running_sum$2;
result$1=result$2;
continue;
}
return result$1;
} while(true);
}


d_typed_data.Int32List ucl_runtime$make_fixnum_vector(dynamic n$1, ){
return d_typed_data.Int32List((n$1 as int), );
}


class Solution {
  dynamic numSubarraysWithSum(dynamic nums_1, dynamic goal_1) => squintcode_lc_930_binary_subarrays_with_sum$numSubarraysWithSum(nums_1, goal_1);
}
