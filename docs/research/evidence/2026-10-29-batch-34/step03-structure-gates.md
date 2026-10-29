# E-03 结构门 5（现树源，首次上设备）

| 字段 | 值 |
|---|---|
| evidence-id | E-03 |
| convention-version | 1 |
| batch | 2026-10-29-batch-34 |
| step | 二（结构门 5） |
| gate-or-kd | 结构门（5 例） |
| verdict | PASS |
| executed-at | 2026-10-29T18:46:21+08:00 → 18:46:24+08:00 |
| operator | 设备验证轨（子代理） |
| head | `db792fa873f4bc150f415b67d1c2e3b1bab6c995` |
| worktree | 721 条（241 `M` / 480 `??`） |
| device-serial | `emulator-5562` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` |
| apk-sha256 | `f75d6fc8b036942a8408b21dff1b6626bc1882e16bd399a48fdb87e70e27b116` |
| apk-version | （空） |
| exit-code | 0 |
| raw-log | `build/device-track-batch34/step03-structure-gates.log`（79 行）（本机，不入仓） |
| record-ref | `docs/research/2026-10-29-batch-34-record.md` §4 |
| sanitized | 是（§6） |

## 1. 目的与判据

批次 33 的设备绿轮对应旧源；该文件 KDoc 在 HEAD 已改。本步在**重建后的 database 测试包**上
复跑现树 5 例，目标 `OK (5 tests)`，0 失败/错误/跳过；构建前源计数 = 5（`grep -c '@Test'`）。

## 2. 命令（可复跑）

```bash
cd "D:/smart mistake book"; export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe
S=emulator-5562
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.TutorVisualStructureGatesInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch34/step03-structure-gates.log
$A -s "$S" logcat -v threadtime -d \
  > build/device-track-batch34/step03-structure-gates-logcat.txt
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| 构建前 `@Test` 计数 | 5 | 与设备报告数比对 |
| 实际 summary | `OK (5 tests)` | 目标字符串 |
| failures / errors / skipped | 0 / 0 / 0 | 期望 0 |
| `INSTRUMENTATION_CODE` | `-1`（不作判据） | — |

## 4. 关键输出（有界）

```text
Time: 2.579
OK (5 tests)
INSTRUMENTATION_CODE: -1
```

dex 门已确认包内含 `TutorVisualStructureGatesInstrumentedTest` 与
`snap-staging-keeper` / `snap-pinned-staging` / `snap-linked-staging` / `snap-abandoned-staging`
（E-01 §4）；设备报告 5 = 源计数 5，排除"旧包/少跑一例"。

## 5. 失败 / 跳过 / 异常

无。

## 6. 脱敏与去噪

- 删除：逐用例进度点。
- 保留：Time、`OK (5 tests)`、`INSTRUMENTATION_CODE`。
- 扫描：`00-index.md` §6 → 0 命中。

## 7. 判定

**PASS**。现树 5 例在新 database 测试包上全绿，设备报告数等于构建前源计数。
