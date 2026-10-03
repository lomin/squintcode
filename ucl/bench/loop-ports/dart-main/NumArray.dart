dynamic Function() benchCase() { final a = List<int>.generate(10000, (i) => rnd(i, 20001) - 10000);
  return () { final o = NumArray(a); var s = 0; for (var q = 0; q < 10000; q++) s += o.sumRange(q % 5000, 5000 + (q % 4999)) as int; return s; }; }
