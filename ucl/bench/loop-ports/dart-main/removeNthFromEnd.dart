dynamic Function() benchCase() => () { ListNode? h; for (var i = 0; i < 100000; i++) h = ListNode(i, h); return (Solution().removeNthFromEnd(h, 5000) as ListNode).val; };
