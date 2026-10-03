dynamic squintcode_lc_19_remove_nth_node_from_end_of_list_loop$removeNthFromEnd(dynamic head$1, dynamic n$1, ){
final ListNode dummy$1=ListNode(0, head$1, );
final ucl_runtime$Cell left12387$1=ucl_runtime$Cell(null, );
if((0 >= (n$1 as int))){
dynamic l$2=dummy$1;
dynamic r$2=head$1;
do {
if(((false != r$2) && (null != r$2))){
final dynamic l$3=l$2.next;
final dynamic r$3=r$2.next;
l$2=l$3;
r$2=r$3;
continue;
}else{
left12387$1.v=l$2;
}
break;
} while(true);
}else{
dynamic right$2=head$1;
int i$2=0;
do {
final dynamic right$3=right$2.next;
final int i$3=(i$2 + 1);
if((i$3 >= (n$1 as int))){
dynamic l$5=dummy$1;
dynamic r$5=right$3;
do {
if(((false != r$5) && (null != r$5))){
final dynamic l$6=l$5.next;
final dynamic r$6=r$5.next;
l$5=l$6;
r$5=r$6;
continue;
}else{
left12387$1.v=l$5;
}
break;
} while(true);
}else{
right$2=right$3;
i$2=i$3;
continue;
}
break;
} while(true);
}
final ucl_runtime$Cell left$1=ucl_runtime$Cell(left12387$1.v, );
left$1.v.next=left$1.v.next.next;
return dummy$1.next;
}


class ucl_runtime$Cell extends Object {
var v;

ucl_runtime$Cell(this.v, ):super();
}


class Solution {
  dynamic removeNthFromEnd(dynamic head_1, dynamic n_1) => squintcode_lc_19_remove_nth_node_from_end_of_list_loop$removeNthFromEnd(head_1, n_1);
}
