dynamic squintcode_lc_560_subarray_sum_equals_k_loop$subarraySum(dynamic nums$1, dynamic k$1, ){
final Map doto$7937_$AUTO_$1=ucl_runtime$make_hash_table();
(doto$7937_$AUTO_$1[0]=1);
final Map freq$1=doto$7937_$AUTO_$1;
final ucl_runtime$IntCell s$1=ucl_runtime$IntCell(0, );
final int len12158$1=(nums$1 as List<int>).length;
if((0 >= len12158$1)){
return 0;
}
int i12159$2=0;
int acc12157$2=0;
do {
final int x$1=((nums$1 as List<int>)[i12159$2]);
s$1.v=(s$1.v + x$1);
final int x12161$1=(ucl_runtime$get_or_default((s$1.v - (k$1 as int)), freq$1, 0, ) as int);
final int acc12157$3=(acc12157$2 + x12161$1);
final int t12162$1=s$1.v;
final num v12163$1=((ucl_runtime$get_or_default(t12162$1, freq$1, 0, ) as num) + 1);
(freq$1[t12162$1]=v12163$1);
final int i12159$3=(1 + i12159$2);
if((i12159$3 >= len12158$1)){
return acc12157$3;
}
i12159$2=i12159$3;
acc12157$2=acc12157$3;
continue;
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


class ucl_runtime$IntCell extends Object {
int v;

ucl_runtime$IntCell(this.v, ):super();
}


class Solution {
  dynamic subarraySum(dynamic nums_1, dynamic k_1) => squintcode_lc_560_subarray_sum_equals_k_loop$subarraySum(nums_1, k_1);
}
