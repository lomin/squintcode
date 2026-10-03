dynamic squintcode_maxprofit$maxProfit(dynamic prices$1, ){
final int n$1=(prices$1 as List<int>).length;
dynamic i$1=0;
dynamic min_price$1=ucl_api$double_float_positive_infinity$v1;
dynamic max_profit$1=0;
do {
if(((i$1 as num) < n$1)){
final int price$1=((prices$1 as List<int>)[(i$1 as int)]);
i$1=(1 + (i$1 as num));
dynamic min_price$1tmp;
if(((min_price$1 as num) < price$1)){
min_price$1tmp=min_price$1;
}else{
min_price$1tmp=price$1;
}
final dynamic a12324$1=max_profit$1;
final num b12325$1=(price$1 - (min_price$1 as num));
if(((a12324$1 as num) > b12325$1)){
max_profit$1=a12324$1;
}else{
max_profit$1=b12325$1;
}
min_price$1=min_price$1tmp;
continue;
}
return max_profit$1;
} while(true);
}


dynamic ucl_api$double_float_positive_infinity$v1=double.infinity;


class Solution {
  dynamic maxProfit(dynamic prices_1) => squintcode_maxprofit$maxProfit(prices_1);
}
