dynamic Function() benchCase() { final a = List<int>.generate(20000, (i) => rnd(i, 2001) - 1000); return () => Solution().subarraySum(a, 7); }
