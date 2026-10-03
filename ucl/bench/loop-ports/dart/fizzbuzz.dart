dynamic squintcode_fizzbuzz$fizz_buzz_pred(dynamic a$1, dynamic b$1, ){
return (0 == ((b$1 as num) % (a$1 as num)));
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


dynamic squintcode_fizzbuzz$fizzBuzz(dynamic n$1, ){
final List<String> answer$1=(List<String>.filled((n$1 as int), "", ));
dynamic i$1=0;
do {
if(((i$1 as num) < (n$1 as int))){
final dynamic v12349$1=squintcode_fizzbuzz$fizz_buzz_word((1 + (i$1 as num)), );
(answer$1[(i$1 as int)]=(v12349$1 as String));
i$1=(1 + (i$1 as num));
continue;
}
return answer$1;
} while(true);
}


dynamic squintcode_fizzbuzz$fizzBuzz2(dynamic n$1, ){
dynamic x$1=1;
dynamic result$1=(List<String>.filled((n$1 as int), "", ));
do {
if(((x$1 as num) <= (n$1 as int))){
final dynamic x$1tmp=(1 + (x$1 as num));
result$1=squintcode_utils$assoc_arr$BANG_(result$1, ((x$1 as num) - 1), squintcode_fizzbuzz$fizz_buzz_word(x$1, ), );
x$1=x$1tmp;
continue;
}
return result$1;
} while(true);
}


dynamic squintcode_utils$assoc_arr$BANG_(dynamic arr$1, dynamic k$1, dynamic v$1, ){
((arr$1 as List)[(k$1 as int)]=v$1);
return (arr$1 as List);
}


class Solution {
  dynamic fizzBuzz(dynamic n_1) => squintcode_fizzbuzz$fizzBuzz(n_1);
  dynamic fizzBuzz2(dynamic n_1) => squintcode_fizzbuzz$fizzBuzz2(n_1);
  dynamic fizz_buzz_pred(dynamic a_1, dynamic b_1) => squintcode_fizzbuzz$fizz_buzz_pred(a_1, b_1);
  dynamic fizz_buzz_word(dynamic x_1) => squintcode_fizzbuzz$fizz_buzz_word(x_1);
}
