dynamic squintcode_probe_hof$each(dynamic f$1, dynamic xs$1, ){
final int n10524$1=(xs$1 as List<int>).length;
int i$1=0;
do {
if((i$1 < n10524$1)){
((f$1 as Function)(((xs$1 as List<int>)[i$1]), ));
i$1=(1 + i$1);
continue;
}
return null;
} while(true);
}


dynamic squintcode_probe_hof$sumFold(dynamic nums$1, ){
return squintcode_probe_hof$fold((dynamic acc$1, dynamic x$1, ){
return ((acc$1 as num) + (x$1 as num));
}, 0, (nums$1 as List<int>), );
}


dynamic squintcode_probe_hof$countLoop(dynamic nums$1, dynamic k$1, ){
final int n$1=(nums$1 as List<int>).length;
dynamic i$1=0;
dynamic acc$1=0;
do {
if(((i$1 as num) < n$1)){
final dynamic i$1tmp=(1 + (i$1 as num));
if((((nums$1 as List<int>)[(i$1 as int)]) >= (k$1 as int))){
acc$1=(1 + (acc$1 as num));
}else{
acc$1=acc$1;
}
i$1=i$1tmp;
continue;
}
return acc$1;
} while(true);
}


dynamic squintcode_probe_hof$countFold(dynamic nums$1, dynamic k$1, ){
return squintcode_probe_hof$fold((dynamic acc$1, dynamic x$1, ){
if(((x$1 as num) >= (k$1 as int))){
return (1 + (acc$1 as num));
}
return acc$1;
}, 0, (nums$1 as List<int>), );
}


dynamic squintcode_probe_hof$profitLoop(dynamic prices$1, ){
final int n$1=(prices$1 as List<int>).length;
dynamic i$1=0;
dynamic lo$1=ucl_api$most_positive_fixnum$v1;
dynamic best$1=0;
do {
if(((i$1 as num) < n$1)){
final int p$1=((prices$1 as List<int>)[(i$1 as int)]);
i$1=(1 + (i$1 as num));
dynamic lo$1tmp;
if(((lo$1 as num) < p$1)){
lo$1tmp=lo$1;
}else{
lo$1tmp=p$1;
}
final dynamic a10525$1=best$1;
final num b10526$1=(p$1 - (lo$1 as num));
if(((a10525$1 as num) > b10526$1)){
best$1=a10525$1;
}else{
best$1=b10526$1;
}
lo$1=lo$1tmp;
continue;
}
return best$1;
} while(true);
}


class ucl_runtime$IntCell extends Object {
int v;

ucl_runtime$IntCell(this.v, ):super();
}


dynamic ucl_api$most_positive_fixnum$v1=2147483647;


dynamic squintcode_probe_hof$profitEach(dynamic prices$1, ){
final ucl_runtime$IntCell lo$1=ucl_runtime$IntCell((ucl_api$most_positive_fixnum$v1 as int), );
final ucl_runtime$IntCell best$1=ucl_runtime$IntCell(0, );
(squintcode_probe_hof$each((dynamic p$1, ){
final int a10527$1=lo$1.v;
final int b10528$1=(p$1 as int);
if((a10527$1 < b10528$1)){
lo$1.v=a10527$1;
}else{
lo$1.v=b10528$1;
}
final int a10529$1=best$1.v;
final int b10530$1=((p$1 as int) - lo$1.v);
int setval$2;
if((a10529$1 > b10530$1)){
setval$2=a10529$1;
}else{
setval$2=b10530$1;
}
best$1.v=setval$2;
return setval$2;
}, (prices$1 as List<int>), ));
return best$1.v;
}


dynamic squintcode_probe_hof$profitEach2(dynamic prices$1, ){
final ucl_runtime$IntCell lo$1=ucl_runtime$IntCell((ucl_api$most_positive_fixnum$v1 as int), );
final ucl_runtime$IntCell best$1=ucl_runtime$IntCell(0, );
(squintcode_probe_hof$each((dynamic p$1, ){
final int a10531$1=lo$1.v;
final int b10532$1=(p$1 as int);
if((a10531$1 < b10532$1)){
lo$1.v=a10531$1;
}else{
lo$1.v=b10532$1;
}
final int d$1=((p$1 as int) - lo$1.v);
final int a10533$1=best$1.v;
int setval$2;
if((a10533$1 > d$1)){
setval$2=a10533$1;
}else{
setval$2=d$1;
}
best$1.v=setval$2;
return setval$2;
}, (prices$1 as List<int>), ));
return best$1.v;
}


dynamic squintcode_probe_hof$sumLoop(dynamic nums$1, ){
final int n$1=(nums$1 as List<int>).length;
dynamic i$1=0;
dynamic acc$1=0;
do {
if(((i$1 as num) < n$1)){
final dynamic i$1tmp=(1 + (i$1 as num));
acc$1=((acc$1 as num) + ((nums$1 as List<int>)[(i$1 as int)]));
i$1=i$1tmp;
continue;
}
return acc$1;
} while(true);
}


dynamic squintcode_probe_hof$fold(dynamic f$1, dynamic init$1, dynamic xs$1, ){
final int n$1=(xs$1 as List<int>).length;
dynamic i$1=0;
dynamic acc$1=init$1;
do {
if(((i$1 as num) < n$1)){
final dynamic i$1tmp=(1 + (i$1 as num));
acc$1=(f$1 as Function)(acc$1, ((xs$1 as List<int>)[(i$1 as int)]), );
i$1=i$1tmp;
continue;
}
return acc$1;
} while(true);
}


class Solution {
  dynamic countFold(dynamic nums_1, dynamic k_1) => squintcode_probe_hof$countFold(nums_1, k_1);
  dynamic countLoop(dynamic nums_1, dynamic k_1) => squintcode_probe_hof$countLoop(nums_1, k_1);
  dynamic each(dynamic f_1, dynamic xs_1) => squintcode_probe_hof$each(f_1, xs_1);
  dynamic fold(dynamic f_1, dynamic init_1, dynamic xs_1) => squintcode_probe_hof$fold(f_1, init_1, xs_1);
  dynamic profitEach(dynamic prices_1) => squintcode_probe_hof$profitEach(prices_1);
  dynamic profitEach2(dynamic prices_1) => squintcode_probe_hof$profitEach2(prices_1);
  dynamic profitLoop(dynamic prices_1) => squintcode_probe_hof$profitLoop(prices_1);
  dynamic sumFold(dynamic nums_1) => squintcode_probe_hof$sumFold(nums_1);
  dynamic sumLoop(dynamic nums_1) => squintcode_probe_hof$sumLoop(nums_1);
}
