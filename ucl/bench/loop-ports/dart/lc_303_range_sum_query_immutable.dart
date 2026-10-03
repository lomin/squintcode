import "dart:typed_data" as d_typed_data;

dynamic squintcode_lc_303_range_sum_query_immutable$NumArray(dynamic nums$1, ){
final dynamic prefix_sum$1=squintcode_lc_303_range_sum_query_immutable$build_prefix_sum(nums$1, );
return squintcode_lc_303_range_sum_query_immutable$NumArray_struct(prefix_sum$1, );
}


class squintcode_lc_303_range_sum_query_immutable$NumArray_struct extends Object {
var prefix$UNDERSCORE_sum;

squintcode_lc_303_range_sum_query_immutable$NumArray_struct(this.prefix$UNDERSCORE_sum, ):super();

dynamic sumRange(dynamic left$1, dynamic right$1, ){
return (((this.prefix$UNDERSCORE_sum[(1 + (right$1 as num))]) as num) - ((this.prefix$UNDERSCORE_sum[left$1]) as num));
}
}


dynamic squintcode_lc_303_range_sum_query_immutable$build_prefix_sum(dynamic nums$1, ){
final int n$1=(nums$1 as List<int>).length;
final d_typed_data.Int32List ps$1=ucl_runtime$make_fixnum_vector((1 + n$1), );
dynamic i$1=0;
dynamic sum$1=0;
do {
if(((i$1 as num) < n$1)){
final num sum$2=((sum$1 as num) + ((nums$1 as List<int>)[(i$1 as int)]));
final num t12108$1=(1 + (i$1 as num));
(ps$1[(t12108$1 as int)]=(sum$2 as int));
i$1=(1 + (i$1 as num));
sum$1=sum$2;
continue;
}
return ps$1;
} while(true);
}


d_typed_data.Int32List ucl_runtime$make_fixnum_vector(dynamic n$1, ){
return d_typed_data.Int32List((n$1 as int), );
}


dynamic squintcode_lc_303_range_sum_query_immutable$sumRange(dynamic self12109$1, dynamic left$1, dynamic right$1, ){
return ((self12109$1 as squintcode_lc_303_range_sum_query_immutable$NumArray_struct).sumRange(left$1, right$1, ));
}


class Solution {
  dynamic build_prefix_sum(dynamic nums_1) => squintcode_lc_303_range_sum_query_immutable$build_prefix_sum(nums_1);
}

class NumArray {
  final squintcode_lc_303_range_sum_query_immutable$NumArray_struct _self;
  NumArray(dynamic nums_1) : _self = squintcode_lc_303_range_sum_query_immutable$NumArray(nums_1);
  dynamic sumRange(dynamic left_1, dynamic right_1) => _self.sumRange(left_1, right_1);
}
