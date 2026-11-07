### G6 arms (dec)

| arm | f | n | median Δ (Y) | ≥1 KC? | #Δ>0 | dev median | guard | pass | reason | bootstrap 95% |
|---|---|---|---|---|---|---|---|---|---|---|
| G6/A1 | 0.15 | 6 | -0.0333 | no | 3 | +0.0000 (3/7) | False | False | UNDERPOWERED | [-0.1167, +0.1167] |
| G6/A2 | 0.25 | 6 | +0.0500 | yes | 4 | +0.0167 (4/8) | False | False | UNDERPOWERED | [-0.0667, +0.1500] |
| G6/A3 | 0.35 | 8 | +0.0167 | no | 4 | +0.0333 (5/8) | False | False | median_delta_below_one_kc | [-0.0667, +0.0667] |
| G6/A4 | 0.50 | 8 | +0.0333 | yes | 5 | +0.0333 (5/7) | False | False | sign_consistency_below_8_of_10 | [-0.0333, +0.1333] |

adopted_f = None; verdict = KEEP_0.50 (no arm passed the pre-registered gate); no_equal_time_cells = 22

### weight arms (dec)

| arm | coordinate | ×  | n | median Δ (Y) | ≥1 KC? | #Δ>0 | dev median | guard | pass | reason | bootstrap 95% |
|---|---|---|---|---|---|---|---|---|---|---|---|
| W/due/x0.75 | due | 0.750 | 6 | +0.1000 | yes | 4 | +0.1000 (4/5) | False | False | UNDERPOWERED | [-0.1000, +0.2167] |
| W/due/x0.875 | due | 0.875 | 6 | +0.0833 | yes | 5 | +0.1000 (4/6) | False | False | UNDERPOWERED | [-0.0833, +0.1333] |
| W/due/x1.125 | due | 1.125 | 7 | +0.0667 | yes | 6 | +0.0667 (4/7) | False | False | UNDERPOWERED | [+0.0333, +0.1000] |
| W/due/x1.25 | due | 1.250 | 8 | +0.0833 | yes | 6 | +0.0333 (5/8) | False | False | sign_consistency_below_8_of_10 | [-0.0333, +0.1333] |
| W/weak/x0.75 | weak | 0.750 | 2 | +0.1000 | yes | 1 | -0.0333 (0/1) | False | False | UNDERPOWERED | [-0.1000, +0.3000] |
| W/weak/x0.875 | weak | 0.875 | 6 | +0.0167 | no | 3 | +0.0667 (3/5) | False | False | UNDERPOWERED | [-0.0833, +0.1167] |
| W/weak/x1.125 | weak | 1.125 | 8 | +0.0833 | yes | 6 | +0.1333 (6/7) | False | False | sign_consistency_below_8_of_10 | [-0.0333, +0.1333] |
| W/weak/x1.25 | weak | 1.250 | 10 | +0.0833 | yes | 7 | +0.0000 (4/9) | False | False | sign_consistency_below_8_of_10 | [-0.0000, +0.1500] |
| W/exam/x0.875 | exam | 0.875 | 10 | +0.0000 | no | 3 | +0.0000 (1/10) | False | False | median_delta_below_one_kc | [+0.0000, +0.0333] |
| W/exam/x1.125 | exam | 1.125 | 10 | +0.0000 | no | 4 | +0.0000 (4/10) | False | False | median_delta_below_one_kc | [+0.0000, +0.0333] |
| W/exam/x1.25 | exam | 1.250 | 10 | +0.0333 | no | 6 | +0.0000 (2/10) | False | False | median_delta_below_one_kc | [+0.0000, +0.0500] |
| W/kc-drop/x0.75 | kc-drop | 0.750 | 9 | +0.0667 | yes | 6 | +0.0333 (2/4) | False | False | sign_consistency_below_8_of_10 | [-0.1333, +0.1333] |
| W/kc-drop/x0.875 | kc-drop | 0.875 | 8 | +0.0667 | yes | 5 | +0.0667 (3/6) | False | False | sign_consistency_below_8_of_10 | [+0.0000, +0.1667] |
| W/diversity/x0.75 | diversity | 0.750 | 8 | +0.0667 | yes | 7 | +0.0000 (4/9) | False | False | sign_consistency_below_8_of_10 | [+0.0000, +0.1333] |
| W/diversity/x0.875 | diversity | 0.875 | 5 | +0.1000 | yes | 4 | +0.0167 (3/6) | False | False | UNDERPOWERED | [-0.0333, +0.2667] |
| W/diversity/x1.125 | diversity | 1.125 | 7 | -0.0333 | no | 3 | +0.0167 (2/4) | False | False | UNDERPOWERED | [-0.1667, +0.2000] |
| W/diversity/x1.25 | diversity | 1.250 | 4 | +0.0500 | yes | 2 | +0.1333 (4/4) | False | False | UNDERPOWERED | [-0.1000, +0.2000] |

passing = 0; adopted = None; verdict = KEEP_ALL_CURRENT_WEIGHTS

### threshold readings (pooled dev+holdout, n=20)

| truth | policy | day | n | coverage (median) | min | max | misjudge median | structural_zero |
|---|---|---|---|---|---|---|---|---|
| W-decay | T0 | 30 | 20 | 1 | 0 | 2 | 0 | False |
| W-decay | T0 | 60 | 20 | 2 | 1 | 4 | 0 | False |
| W-decay | T0 | 90 | 20 | 5 | 2 | 7 | 0 | False |
| W-decay | T0 | 120 | 20 | 6 | 3 | 9 | 0 | False |
| W-decay | T1 | 30 | 20 | 2 | 0 | 4 | 0 | False |
| W-decay | T1 | 60 | 20 | 3.5 | 1 | 8 | 0 | False |
| W-decay | T1 | 90 | 20 | 7 | 4 | 12 | 0 | False |
| W-decay | T1 | 120 | 20 | 9 | 4 | 11 | 0 | False |
| W-decay | T2 | 30 | 20 | 1 | 0 | 2 | 0 | False |
| W-decay | T2 | 60 | 20 | 2 | 1 | 4 | 0 | False |
| W-decay | T2 | 90 | 20 | 5 | 2 | 9 | 0 | False |
| W-decay | T2 | 120 | 20 | 6 | 3 | 10 | 0 | False |
| W-slip | T0 | 30 | 20 | 1 | 0 | 3 | 0 | True |
| W-slip | T0 | 60 | 20 | 2.5 | 0 | 6 | 0 | True |
| W-slip | T0 | 90 | 20 | 4 | 0 | 8 | 0 | True |
| W-slip | T0 | 120 | 20 | 6 | 2 | 11 | 0 | True |
| W-slip | T1 | 30 | 20 | 2 | 0 | 5 | 0 | True |
| W-slip | T1 | 60 | 20 | 5 | 1 | 9 | 0 | True |
| W-slip | T1 | 90 | 20 | 8 | 5 | 11 | 0 | True |
| W-slip | T1 | 120 | 20 | 9 | 3 | 12 | 0 | True |
| W-slip | T2 | 30 | 20 | 1 | 0 | 3 | 0 | True |
| W-slip | T2 | 60 | 20 | 2.5 | 0 | 6 | 0 | True |
| W-slip | T2 | 90 | 20 | 5 | 1 | 8 | 0 | True |
| W-slip | T2 | 120 | 20 | 7 | 2 | 11 | 0 | True |
