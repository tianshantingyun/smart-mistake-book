# E-05 备份抽验复跑：BackupRestoreInstrumentedTest 34 例（PASS，不回退）

| 字段 | 值 |
|---|---|
| evidence-id | E-05 |
| convention-version | 1 |
| batch | 2026-10-28-batch-29 |
| step | 五（备份抽验，不回退） |
| gate-or-kd | 备份门（T1 判据表第 6 项补充面） |
| verdict | **PASS**：`OK (34 tests)`，与批次 27/28 的 34/34 一致；0 跳过 |
| executed-at | 2026-10-28T22:37:00+08:00 前后（设备 UTC 14:37） |
| operator | 设备验证轨（subagent，批次 29） |
| head | `3d9372e5` |
| worktree | dirty（他线在飞；本步零改动） |
| device-serial | emulator-5562（test_device / API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | core:data androidTest `4d152b5a…fa72cd81`（设备侧逐字节一致） |
| apk-version | 0.2.0-offline |
| exit-code | 宿主 `EXIT=0`（非判据；判据 = summary） |
| raw-log | `build/device-track-batch29/step05-backup.log`、`step05-backup-logcat.txt` |
| record-ref | 批次 29 记录（后写） |
| sanitized | 是 |

## 1. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch29/step05-backup.log 2>&1; echo "EXIT=$?"
$A -s $S logcat -v threadtime -d > build/device-track-batch29/step05-backup-logcat.txt
```

## 2. 结果

```text
Time: 19.152
OK (34 tests)
INSTRUMENTATION_CODE: -1
```

| 指标 | 值 | 判据 |
|---|---|---|
| tests | 34/34 | 与批次 27/28 一致 |
| failures / errors | 0 / 0 | 期望 0 |
| `assumption`/`Skipped` 行 | 0（`grep -c` 实测） | 无跳过 |
| 9 类崩溃签名 | 0 | 期望 0 |

- 本步在 R1/R2 两个缺陷存在的情况下仍全绿：备份面不构造真实 `visual_js` worker、
  不经 `PdfDocument.writeTo`（这两面不在本套件路径上），故与两条红轨正交。
- Time 19.152 vs 批次 28 45.831：时间不是判据；差异归新冷启设备与缓存态，不改判定。

## 3. 判定

**PASS**：备份/恢复/迁移面在批次 29 树上不回退。未改测试、未跳过、未放宽。
