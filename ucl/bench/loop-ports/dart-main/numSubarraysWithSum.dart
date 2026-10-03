dynamic Function() benchCase() { final a = List<int>.generate(30000, (i) => rnd(i, 3) == 0 ? 1 : 0); return () => Solution().numSubarraysWithSum(a, 50); }
