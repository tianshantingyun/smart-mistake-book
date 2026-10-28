# E-04 备份抽验复跑：BackupRestoreInstrumentedTest 34 例（不回退面）

| 字段 | 值 |
|---|---|
| evidence-id | E-04 |
| convention-version | 1 |
| batch | 2026-10-28-batch-28 |
| step | 五（备份抽验，不回退） |
| gate-or-kd | 备份门（不回退面；T1 判据 6 补充项） |
| verdict | **PASS**：`OK (34 tests)`，与批次 27 一致，不回退 |
| executed-at | 2026-10-28T07:47:30+08:00 前后（设备时钟 UTC 23:47） |
| operator | 设备验证轨（subagent，批次 28） |
| head | 8116ad98 |
| worktree | dirty（他线在飞 + 本轨两测试面更正） |
| device-serial | emulator-5554（test_device / API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | core:data androidTest v3 `a24d2c85…fd79857`（设备侧 sha256 实测一致） |
| apk-version | 0.2.0-offline |
| exit-code | 0 |
| raw-log | `build/device-track-batch28/`：`step04-backup.log`、`step04-backup-logcat.txt` |
| record-ref | 批次 28 记录（记录员后写） |
| sanitized | 是（本文件 §6） |

## 1. 目的与判据

批次 27 备份 34/34 绿（`evidence/2026-10-27-batch-27/step05-isolation-suite-backup.md`）。本批 SceneV1 修复 + painter 落层后复跑同套件：期望 `OK (34 tests)` 不回退（T1 判据表第 6 项补充面）。跳过数须与批次 27 同口径核对。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch28/step04-backup-logcat.txt
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests | **`OK (34 tests)`** / Time 45.831 | 期望 34 全绿（批次 27 同值） |
| failures / errors | 0 / 0 | 期望 0 |
| 9 类崩溃签名 | 9×0（logcat 实扫） | 期望全 0 |

## 4. 关键输出（有界）

```text
Time: 45.831
OK (34 tests)
INSTRUMENTATION_CODE: -1
```

## 5. 失败 / 跳过 / 异常

无失败。跳过：输出流未见 assume 跳过行（与批次 27 同套件同口径；该套件无 16KB assume 面）。

## 6. 脱敏与去噪

- 删除：无。保留：summary 全文、签名计数（含 0）。扫描：见 `00-index.md` §6。二进制/截图：无。

## 7. 判定

**PASS**：34/34 与批次 27 逐值一致，备份/恢复/迁移面在 SceneV1 修复与 painter 落层后无回退。本步为补充不回退证据（KD-70 内存例的 20 例套件不在本步范围，批次 27 已绿且本批未触内存归属面）。

## 8. 原始全量（不入仓）

- `build/device-track-batch28/step04-backup.log` / `step04-backup-logcat.txt`。
- `build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
