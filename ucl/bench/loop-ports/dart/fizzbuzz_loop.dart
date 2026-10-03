dynamic squintcode_fizzbuzz_loop$fizzBuzz(dynamic n$1, ){
final List<String> answer$1=(List<String>.filled((n$1 as int), "", ));
if((0 >= (n$1 as int))){
}else{
int i$2=0;
do {
final dynamic v12360$1=squintcode_fizzbuzz$fizz_buzz_word((1 + i$2), );
(answer$1[i$2]=(v12360$1 as String));
final int i$3=(i$2 + 1);
if((i$3 >= (n$1 as int))){
}else{
i$2=i$3;
continue;
}
break;
} while(true);
}
return answer$1;
}


dynamic squintcode_fizzbuzz$fizz_buzz_word(dynamic x$1, ){
final Function pred$12347_$1=squintcode_fizzbuzz$fizz_buzz_pred;
final dynamic test$1=pred$12347_$1(15, x$1, );
if(((false != test$1) && (null != test$1))){
return "FizzBuzz";
}
final dynamic test$2=pred$12347_$1(3, x$1, );
if(((false != test$2) && (null != test$2))){
return "Fizz";
}
final dynamic test$3=pred$12347_$1(5, x$1, );
if(((false != test$3) && (null != test$3))){
return "Buzz";
}
return (x$1.toString());
}


dynamic squintcode_fizzbuzz$fizz_buzz_pred(dynamic a$1, dynamic b$1, ){
return (0 == ((b$1 as num) % (a$1 as num)));
}


class Solution {
  dynamic fizzBuzz(dynamic n_1) => squintcode_fizzbuzz_loop$fizzBuzz(n_1);
}
