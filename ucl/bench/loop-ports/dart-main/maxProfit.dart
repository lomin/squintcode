dynamic Function() benchCase() { final p = List<int>.generate(100000, (i) => rnd(i, 10007)); return () => Solution().maxProfit(p); }
