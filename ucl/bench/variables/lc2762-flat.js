// The flat build (one loop/recur state machine, before variables), as submitted.
var count_steady_stretches = function(nums, gap) {
  const n_1 = nums.length;
  const maxq_2 = new Int32Array(n_1);
  const minq_3 = new Int32Array(n_1);
  let r_4 = 0;
  let left_5 = 0;
  let maxh_6 = 0;
  let maxt_7 = 0;
  let minh_8 = 0;
  let mint_9 = 0;
  let total_10 = 0;
  while (true) {
    if (r_4 < n_1) {
      const x_11 = nums[r_4];
      if (minh_8 < mint_9 && x_11 - nums[minq_3[minh_8]] > gap) {
        let G__12 = r_4;
        let G__13 = minq_3[minh_8] + 1;
        let G__14 = maxh_6;
        let G__15 = maxt_7;
        let G__16 = minh_8 + 1;
        let G__17 = mint_9;
        let G__18 = total_10;
        r_4 = G__12;
        left_5 = G__13;
        maxh_6 = G__14;
        maxt_7 = G__15;
        minh_8 = G__16;
        mint_9 = G__17;
        total_10 = G__18;
        continue;
      } else {
        if (maxh_6 < maxt_7 && nums[maxq_2[maxh_6]] - x_11 > gap) {
          let G__19 = r_4;
          let G__20 = maxq_2[maxh_6] + 1;
          let G__21 = maxh_6 + 1;
          let G__22 = maxt_7;
          let G__23 = minh_8;
          let G__24 = mint_9;
          let G__25 = total_10;
          r_4 = G__19;
          left_5 = G__20;
          maxh_6 = G__21;
          maxt_7 = G__22;
          minh_8 = G__23;
          mint_9 = G__24;
          total_10 = G__25;
          continue;
        } else {
          if (maxh_6 < maxt_7 && nums[maxq_2[maxt_7 - 1]] <= x_11) {
            let G__26 = r_4;
            let G__27 = left_5;
            let G__28 = maxh_6;
            let G__29 = maxt_7 - 1;
            let G__30 = minh_8;
            let G__31 = mint_9;
            let G__32 = total_10;
            r_4 = G__26;
            left_5 = G__27;
            maxh_6 = G__28;
            maxt_7 = G__29;
            minh_8 = G__30;
            mint_9 = G__31;
            total_10 = G__32;
            continue;
          } else {
            if (minh_8 < mint_9 && nums[minq_3[mint_9 - 1]] >= x_11) {
              let G__33 = r_4;
              let G__34 = left_5;
              let G__35 = maxh_6;
              let G__36 = maxt_7;
              let G__37 = minh_8;
              let G__38 = mint_9 - 1;
              let G__39 = total_10;
              r_4 = G__33;
              left_5 = G__34;
              maxh_6 = G__35;
              maxt_7 = G__36;
              minh_8 = G__37;
              mint_9 = G__38;
              total_10 = G__39;
              continue;
            } else {
              if ("else") {
                maxq_2[maxt_7] = r_4;
                minq_3[mint_9] = r_4;
                let G__40 = r_4 + 1;
                let G__41 = left_5;
                let G__42 = maxh_6;
                let G__43 = maxt_7 + 1;
                let G__44 = minh_8;
                let G__45 = mint_9 + 1;
                let G__46 = total_10 + (r_4 - left_5 - -1);
                r_4 = G__40;
                left_5 = G__41;
                maxh_6 = G__42;
                maxt_7 = G__43;
                minh_8 = G__44;
                mint_9 = G__45;
                total_10 = G__46;
                continue;
              } else {
                return null;
              }
            }
          }
        }
      }
      ;
    } else {
      return total_10;
    }
    ;
    ;
    break;
  }
  ;
};
var continuousSubarrays = function(nums) {
  return count_steady_stretches(nums, 2);
};