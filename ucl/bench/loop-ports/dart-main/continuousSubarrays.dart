dynamic Function() benchCase() { final a = List<int>.generate(100000, (i) => rnd(i, 5) + 1); return () => Solution().continuousSubarrays(a); }
