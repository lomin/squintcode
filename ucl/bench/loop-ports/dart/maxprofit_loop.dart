dynamic squintcode_maxprofit_loop$maxProfit(dynamic prices$1, ){
final ucl_runtime$IntCell lo$1=ucl_runtime$IntCell(2147483647, );
final int len12131$1=(prices$1 as List<int>).length;
if((0 >= len12131$1)){
return -2147483648;
}
int i12132$2=0;
int acc12130$2=-2147483648;
do {
final int p$1=((prices$1 as List<int>)[i12132$2]);
final int a12135$1=lo$1.v;
if((a12135$1 < p$1)){
lo$1.v=a12135$1;
}else{
lo$1.v=p$1;
}
final int x12134$1=(p$1 - lo$1.v);
int acc12130$3;
if((acc12130$2 > x12134$1)){
acc12130$3=acc12130$2;
}else{
acc12130$3=x12134$1;
}
final int i12132$3=(1 + i12132$2);
if((i12132$3 >= len12131$1)){
return acc12130$3;
}
i12132$2=i12132$3;
acc12130$2=acc12130$3;
continue;
} while(true);
}


class ucl_runtime$IntCell extends Object {
int v;

ucl_runtime$IntCell(this.v, ):super();
}


class Solution {
  dynamic maxProfit(dynamic prices_1) => squintcode_maxprofit_loop$maxProfit(prices_1);
}
