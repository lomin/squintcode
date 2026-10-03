import "dart:typed_data" as d_typed_data;

dynamic squintcode_lc_303_range_sum_query_immutable_loop$NumArray(dynamic nums$1, ){
final dynamic prefix_sum$1=squintcode_lc_303_range_sum_query_immutable_loop$build_prefix_sum(nums$1, );
return squintcode_lc_303_range_sum_query_immutable_loop$NumArray_struct(prefix_sum$1, );
}


class squintcode_lc_303_range_sum_query_immutable_loop$NumArray_struct extends Object {
var prefix$UNDERSCORE_sum;

squintcode_lc_303_range_sum_query_immutable_loop$NumArray_struct(this.prefix$UNDERSCORE_sum, ):super();

dynamic sumRange(dynamic left$1, dynamic right$1, ){
return (((this.prefix$UNDERSCORE_sum[(1 + (right$1 as num))]) as num) - ((this.prefix$UNDERSCORE_sum[left$1]) as num));
}
}


dynamic squintcode_lc_303_range_sum_query_immutable_loop$build_prefix_sum(dynamic nums$1, ){
final d_typed_data.Int32List ps$1=ucl_runtime$make_fixnum_vector((1 + (nums$1 as List<int>).length), );
final ucl_runtime$IntCell s$1=ucl_runtime$IntCell(0, );
final int len12181$1=(nums$1 as List<int>).length;
if((0 >= len12181$1)){
}else{
int i12182$2=0;
do {
final int x$1=((nums$1 as List<int>)[i12182$2]);
final int i$1=(i12182$2 + 1);
s$1.v=(s$1.v + x$1);
final int v12184$1=s$1.v;
(ps$1[i$1]=v12184$1);
final int i12182$3=(1 + i12182$2);
if((i12182$3 >= len12181$1)){
}else{
i12182$2=i12182$3;
continue;
}
break;
} while(true);
}
return ps$1;
}


class ucl_runtime$IntCell extends Object {
int v;

ucl_runtime$IntCell(this.v, ):super();
}


d_typed_data.Int32List ucl_runtime$make_fixnum_vector(dynamic n$1, ){
return d_typed_data.Int32List((n$1 as int), );
}


dynamic squintcode_lc_303_range_sum_query_immutable_loop$sumRange(dynamic self12185$1, dynamic left$1, dynamic right$1, ){
return ((self12185$1 as squintcode_lc_303_range_sum_query_immutable_loop$NumArray_struct).sumRange(left$1, right$1, ));
}


class Solution {
  dynamic build_prefix_sum(dynamic nums_1) => squintcode_lc_303_range_sum_query_immutable_loop$build_prefix_sum(nums_1);
}

class NumArray {
  final squintcode_lc_303_range_sum_query_immutable_loop$NumArray_struct _self;
  NumArray(dynamic nums_1) : _self = squintcode_lc_303_range_sum_query_immutable_loop$NumArray(nums_1);
  dynamic sumRange(dynamic left_1, dynamic right_1) => _self.sumRange(left_1, right_1);
}
