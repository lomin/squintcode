dynamic squintcode_lc_560_subarray_sum_equals_k$subarraySum(dynamic nums$1, dynamic k$1, ){
final int n$1=(nums$1 as List<int>).length;
final Map doto$7937_$AUTO_$1=ucl_runtime$make_hash_table();
(doto$7937_$AUTO_$1[0]=1);
final Map freq$1=doto$7937_$AUTO_$1;
dynamic i$1=0;
dynamic running_sum$1=0;
dynamic result$1=0;
do {
if(((i$1 as num) < n$1)){
final num running_sum$2=((running_sum$1 as num) + ((nums$1 as List<int>)[(i$1 as int)]));
final num result$2=((result$1 as num) + (ucl_runtime$get_or_default((running_sum$2 - (k$1 as num)), freq$1, 0, ) as num));
final num v12336$1=((ucl_runtime$get_or_default(running_sum$2, freq$1, 0, ) as num) + 1);
(freq$1[running_sum$2]=v12336$1);
i$1=(1 + (i$1 as num));
running_sum$1=running_sum$2;
result$1=result$2;
continue;
}
return result$1;
} while(true);
}


dynamic ucl_runtime$get_or_default(dynamic k$1, dynamic m$1, dynamic d$1, ){
final dynamic? v$1=((m$1 as Map)[k$1]);
final bool or$6840_$AUTO_$1=(v$1 != null);
bool $if_$1;
if(or$6840_$AUTO_$1){
$if_$1=or$6840_$AUTO_$1;
}else{
$if_$1=((m$1 as Map).containsKey(k$1, ));
}
if($if_$1){
return v$1;
}
return d$1;
}


Map ucl_runtime$make_hash_table(){
return Map();
}


class Solution {
  dynamic subarraySum(dynamic nums_1, dynamic k_1) => squintcode_lc_560_subarray_sum_equals_k$subarraySum(nums_1, k_1);
}
