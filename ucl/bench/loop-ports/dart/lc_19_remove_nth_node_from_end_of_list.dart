dynamic squintcode_lc_19_remove_nth_node_from_end_of_list$bypass(dynamic node$1, ){
dynamic some_$GT_$7956_$AUTO_$2;
if((null == node$1)){
some_$GT_$7956_$AUTO_$2=null;
}else{
some_$GT_$7956_$AUTO_$2=node$1.next;
}
dynamic setval$1;
if((null == some_$GT_$7956_$AUTO_$2)){
setval$1=null;
}else{
setval$1=some_$GT_$7956_$AUTO_$2.next;
}
node$1.next=setval$1;
return setval$1;
}


dynamic squintcode_lc_19_remove_nth_node_from_end_of_list$move_left_n_from_end(dynamic left$2, dynamic right$2, ){
dynamic left$1=left$2;
dynamic right$1=right$2;
do {
if(((false != right$1) && (null != right$1))){
left$1=left$1.next;
right$1=right$1.next;
continue;
}
return left$1;
} while(true);
}


dynamic squintcode_lc_19_remove_nth_node_from_end_of_list$move_right_n_forward(dynamic head$2, dynamic n$2, ){
dynamic head$1=head$2;
dynamic n$1=n$2;
do {
dynamic $if_$1;
if(((false != head$1) && (null != head$1))){
$if_$1=(0 < (n$1 as num));
}else{
$if_$1=head$1;
}
if(((false != $if_$1) && (null != $if_$1))){
head$1=head$1.next;
n$1=((n$1 as num) - 1);
continue;
}
return head$1;
} while(true);
}


dynamic squintcode_lc_19_remove_nth_node_from_end_of_list$removeNthFromEnd(dynamic head$1, dynamic n$1, ){
final ListNode dummy$1=ListNode(0, head$1, );
(squintcode_lc_19_remove_nth_node_from_end_of_list$bypass(squintcode_lc_19_remove_nth_node_from_end_of_list$move_left_n_from_end(dummy$1, squintcode_lc_19_remove_nth_node_from_end_of_list$move_right_n_forward(head$1, n$1, ), ), ));
return dummy$1.next;
}


class Solution {
  dynamic bypass(dynamic node_1) => squintcode_lc_19_remove_nth_node_from_end_of_list$bypass(node_1);
  dynamic move_left_n_from_end(dynamic left_2, dynamic right_2) => squintcode_lc_19_remove_nth_node_from_end_of_list$move_left_n_from_end(left_2, right_2);
  dynamic move_right_n_forward(dynamic head_2, dynamic n_2) => squintcode_lc_19_remove_nth_node_from_end_of_list$move_right_n_forward(head_2, n_2);
  dynamic removeNthFromEnd(dynamic head_1, dynamic n_1) => squintcode_lc_19_remove_nth_node_from_end_of_list$removeNthFromEnd(head_1, n_1);
}
