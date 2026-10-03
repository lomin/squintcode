dynamic squintcode_lc_1295_find_numbers_with_even_number_of_digits_seq$digit_count(dynamic x$1, ){
final ucl_runtime$IntCell x$2=ucl_runtime$IntCell((x$1 as int), );
final ucl_runtime$IntCell d$1=ucl_runtime$IntCell(1, );
do {
if((x$2.v >= 10)){
x$2.v=(x$2.v ~/ 10);
d$1.v=(d$1.v + 1);
continue;
}else{
}
break;
} while(true);
return d$1.v;
}


class ucl_runtime$IntCell extends Object {
int v;

ucl_runtime$IntCell(this.v, ):super();
}


dynamic squintcode_lc_1295_find_numbers_with_even_number_of_digits_seq$findNumbers(dynamic nums$1, ){
final int e11297$1=(nums$1 as List<int>).length;
int i11299$1=0;
int c11298$1=0;
do {
if((i11299$1 < e11297$1)){
final int el11300$1=((nums$1 as List<int>)[i11299$1]);
i11299$1=(1 + i11299$1);
if((0 == ((squintcode_lc_1295_find_numbers_with_even_number_of_digits_seq$digit_count(el11300$1, ) as int) & 1))){
c11298$1=(1 + c11298$1);
}else{
c11298$1=c11298$1;
}
continue;
}
return c11298$1;
} while(true);
}


class Solution {
  dynamic digit_count(dynamic x_1) => squintcode_lc_1295_find_numbers_with_even_number_of_digits_seq$digit_count(x_1);
  dynamic findNumbers(dynamic nums_1) => squintcode_lc_1295_find_numbers_with_even_number_of_digits_seq$findNumbers(nums_1);
}
