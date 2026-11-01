# E-04 步骤三·core:data 套件（备份组 + 视觉组 + A-10 预算边界，12 套件全绿）

| 字段 | 值 |
|---|---|
| evidence-id | E-04 |
| convention-version | 1 |
| batch | 2026-11-01-batch-39 |
| step | 步骤三·既有套件不回退（core:data 模块 12 套件） |
| gate-or-kd | 批 38 底册不回退（34/2/9/8/12/16/24/16/1/6/27/3）+ KD-80 四设备位维持 |
| verdict | **PASS**：12 套件全绿，计数与底册逐一相符，0 跳过，签名 0 |
| executed-at | 2026-11-01T10:30–10:54+08:00 |
| operator | 设备验证轨（批次 39 工作流子代理） |
| head | `a0641bb7392f380b5716afe95dd3c371dd0c7670` |
| worktree | 737 条（仅记录） |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | data androidTest `5b8ba39c83b625f3880210f5df2f249d9728e61a96a32a827f72b977101153c3` |
| exit-code | 各套件 adb 层 0 |
| raw-log | `build/device-track-batch39/suite-{backup,rowcounts,trimmer,c4,repo,parity,painter,pdf,ordinal,reconciler,engine,a10}.log` + 同名 `-logcat.txt` |
| record-ref | `docs/research/2026-11-01-batch-39-record.md`（记录员落笔位） |
| sanitized | 是（00-index §6 统一扫描） |

## 1. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class com.tingyun.smartmistakebook.core.data.{backup|visual}.<套件名> \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s $S shell logcat -v threadtime -d
```

## 2. 结果摘要（12/12 绿）

| 套件 | 结果 | 判据计数 |
|---|---|---|
| `BackupRestoreInstrumentedTest` | **OK (34 tests)** | 34 相符（KD-80 C1 位维持） |
| `BackupRowCountsCoverageInstrumentedTest` | **OK (2 tests)** | 2 相符（KD-80 C2 设备位维持） |
| `BackupSnapshotTrimmerInstrumentedTest` | **OK (9 tests)** | 9 相符（KD-80 C3 设备位维持） |
| `BackupVisualReferenceInstrumentedTest`（C4） | **OK (8 tests)** | 8 相符（含 D-04 三新例 ride-along 不回退） |
| `TutorVisualCommitRepositoryInstrumentedTest` | **OK (12 tests)** | 12 相符 |
| `VisualDrawOpsParityInstrumentedTest` | **OK (16 tests)** | 16 相符 |
| `VisualDrawOpsPainterInstrumentedTest` | **OK (24 tests)** | 24 相符 |
| `VisualDrawOpsPdfPainterInstrumentedTest` | **OK (16 tests)** | 16 相符 |
| `FigureGenerationLocalAttemptOrdinalInstrumentedTest` | **OK (1 test)** | 1 相符 |
| `FigureGenerationReconcilerInstrumentedTest` | **OK (6 tests)** | 6 相符 |
| `QuickJsVisualExecutionEngineInstrumentedTest` | **OK (27 tests)** | 27 相符 |
| `FigureDispatchBudgetBoundaryInstrumentedTest`（A-10） | **OK (3 tests)** | 3 相符（`aSeventhOperationDispatchIsDeniedWithTheRealCount` 在内） |

## 3. 窗口异常登记（不改判定）

- `trimmer` 窗口：末段两例在宿主 I/O 高负载下显著变慢（窗口 10:34:46 起 ~12 分钟；进程存活且逐例推进——TestRunner started/finished 序列完整，最终 OK (9 tests)），非挂死非失败。
- `engine` 套件 27 例全程绿——同树引擎文件（T4 计时缝在飞 M）之上 QuickJs 全家族回归无红。

## 4. 脱敏与去噪

- 本文件不含密钥/凭据/邮箱/物理序列号/用户目录；原始全量在 `build/device-track-batch39/`。
