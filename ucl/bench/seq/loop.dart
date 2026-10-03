dynamic squintcode_lc_1295_find_numbers_with_even_number_of_digits$digit_count(dynamic x$1, ){
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


dynamic squintcode_lc_1295_find_numbers_with_even_number_of_digits$findNumbers(dynamic nums$1, ){
final int n$1=(nums$1 as List<int>).length;
dynamic i$1=0;
dynamic c$1=0;
do {
if(((i$1 as num) < n$1)){
final dynamic i$1tmp=(1 + (i$1 as num));
if((0 == ((squintcode_lc_1295_find_numbers_with_even_number_of_digits$digit_count(((nums$1 as List<int>)[(i$1 as int)]), ) as int) & 1))){
c$1=(1 + (c$1 as num));
}else{
c$1=c$1;
}
i$1=i$1tmp;
continue;
}
return c$1;
} while(true);
}


class Solution {
  dynamic digit_count(dynamic x_1) => squintcode_lc_1295_find_numbers_with_even_number_of_digits$digit_count(x_1);
  dynamic findNumbers(dynamic nums_1) => squintcode_lc_1295_find_numbers_with_even_number_of_digits$findNumbers(nums_1);
}
