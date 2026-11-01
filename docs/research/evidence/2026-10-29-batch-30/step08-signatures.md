# E-08 9 类崩溃签名 / worker 生命周期 / 专项 logcat 计数（全部 0 签名；1 处判据偏差）

| 字段 | 值 |
|---|---|
| evidence-id | E-08 |
| convention-version | 1 |
| batch | 2026-10-29-batch-30 |
| step | 八（签名与专项 logcat；判据 §5.5） |
| gate-or-kd | 判据 §5.5；KD-78 ④-L1/L2/L3；KD-79-L1/L2 |
| verdict | **PASS（签名/生命周期/flags/PDF native）**；**PARITY_GATE 行数判据不成立**（实测 11 vs 判据写 1，因 `@Before` 每例打印；登记为判据漂移） |
| executed-at | 2026-10-29T02:28–02:40+0800 |
| operator | 设备验证轨（subagent，批次 30） |
| head | `a806525e` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | 见 `step00-environment.md` §2 |
| apk-version | 0.2.0-offline |
| exit-code | 采样命令 `grep` 的 0 命中返回 1（正常），不影响判定 |
| raw-log | `build/device-track-batch30/*-logcat.txt`（11 份） |
| record-ref | 批次 30 记录（后写） |
| sanitized | 是 |

## 1. 9 类崩溃签名（判据 §5.5）

```bash
for f in build/device-track-batch30/*-logcat.txt; do printf "%s: " "$(basename $f)";
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed" "$f"
done
```

| logcat | 命中 |
|---|---|
| `step01-isolation-logcat.txt` | 0 |
| `step02-flags-logcat.txt` | 0 |
| `step03-painter-logcat.txt` | 0 |
| `step04-pdf-logcat.txt` | 0 |
| `step05-parity-logcat.txt` | 0 |
| `step05-parity-rerun-logcat.txt` | 0 |
| `step06-backup-logcat.txt` | 0 |
| `step07-migration-matrix-logcat.txt` | 0 |
| `step08-migration-69-70-logcat.txt` | 0 |
| `step09-visual-commit-db-logcat.txt` | 0 |
| `step10-visual-repo-logcat.txt` | 0 |

**11 份全部 0。** 注意：这 9 类签名全 0 **不**代表套件全绿（备份/parity 有断言红，但不是崩溃）。

## 2. 隔离 worker 生命周期（判据 §2.2/§5.5）

```text
Start proc .*:visual_js                      17
visual_js warm-up finished                   17        # pid 集合与 spawn 逐值相同
visual_js warm-up failed                      0
hard deadline (1500 ms) for request           5
terminate(generation                          4
the host unbound the visual_js worker         7
due to start instr                            1
                                   四类之和 = 17 = spawn
ps -A -o PID,NAME | grep visual_js          （空，无存活 worker）
```

- AMS `isolated not needed` 行（12 条）为系统回收，按判据 §2.2-5 不计入杀因。
- 无未分类 `VisualJsService` 杀因。

## 3. KD-78 ④ logcat 面

```text
grep -c "measure paint flags .* do not match the environment fingerprint"
  step01-isolation-logcat.txt: 0
  step02-flags-logcat.txt:     0
  step03-painter-logcat.txt:   0
```

三份均 0 → ④-L1 成立；批次 29 的 12 条同款恒抛未复现。

## 4. KD-79 logcat 面

```text
grep -c "java.io.IOException: simulated write failure" step04-pdf-logcat.txt
230
```

`> 0` → 失败路径确实走到，native 吞异常仍发生，修复把它转有码异常（KD-79-L1 成立）。
条数随 chunk 浮动，**不作固定值断言**（判据 §4 注）。

## 5. 一致性门 logcat（判据 §2.5）

```text
PARITY_GATE scope=PROTOTYPE product-gate=no ci-gate=no release-gate=no   11
PARITY_CONTROL                                                          10
PARITY_CONTROL 中非 red=true 行数                                         0
PARITY_GATE_DESENSITIZED                                                 0
PARITY_SYSTEMATIC_DRIFT                                                  0
PARITY_F5                                                                0
PARITY_DIAGNOSTIC_WRITTEN                                                1   # P07 红轮诊断 PNG
PARITY_ENV …|replayTextPaintFlags=0|…
```

- **判据偏差（登记，不修码）**：判据 §2.5-2 写"有且仅有一轮 `PARITY_GATE`"，实测 **11**。
  原因是 `@Before announcePrototypeGate()` 在**每个测试方法**前打印（`VisualDrawOpsParityInstrumentedTest.kt:79-86`），
  11 例即 11 行。该条应按"每例一行"或"每套件一轮"重新表述；**不是门失去分辨力**。
- `PARITY_CONTROL`=10 且全 `red=true`：N01–N07 负控（N01 含 P01/P02/P03/P05 四个子控）全红，
  N08 在用例内直接断言 `F4_COLOR.isRed`、不打印该行——与判据 §2.5-4 一致。
- `PARITY_DIAGNOSTIC_WRITTEN`=1 与红轮一致（绿轮才要求 0）。

## 6. 未验证项

- **UNVERIFIED**：判据 §2.5-2 的"一轮"口径待判据文档回改；本文件不改判据。
- 签名扫描是正则计数的静态证据，不能替代崩溃堆栈分析；本批无崩溃行可分析。

## 脱敏与去噪（R4 §4.4）

- 扫描：本批目录四条正则各 **0 命中**（命令与结果见 `00-index.md` §8）；本文件无密钥/凭据/PII。
- 去噪：保留 11 份 logcat 的逐份计数与门读数；未删除任何签名行（全部 0）。
- 二进制/截图：无。全量见头部 `raw-log`（`build/`，不入仓、不作关闭依据）。
