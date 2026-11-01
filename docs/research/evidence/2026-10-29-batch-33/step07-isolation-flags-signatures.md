# E-07 隔离 13 + flags 2 + 9 类崩溃签名 + worker 生命周期

| 字段 | 值 |
|---|---|
| evidence-id | E-07 |
| convention-version | 1 |
| batch | 2026-10-29-batch-33 |
| step | 判据五 |
| gate-or-kd | KD-78 ④（隔离/flags）、判据 §5.6（签名扫描） |
| verdict | PASS |
| executed-at | 2026-10-29T15:12:55+08:00（隔离）/ 15:12:58（flags）；签名扫描 15:13 |
| operator | 设备验证轨（子代理） |
| head | `6abd807fa73d9084ef629d54015bd4f725c94784` |
| worktree | dirty（仅记录） |
| device-serial | `emulator-5562` |
| device-avd | `test_device` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `<repo>/app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` |
| apk-sha256 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` |
| apk-version | （空） |
| exit-code | 0（两步） |
| raw-log | `step13-isolation.log`（191 行）、`step14-flags.log`（37）；17 份 `*-logcat.txt` |
| record-ref | `docs/research/2026-10-29-batch-33-record.md` §4 |
| sanitized | 是（§6） |

## 1. 目的与判据

- 隔离 13/13、flags 2/2 不回退。
- 全部 logcat 的 9 类崩溃签名计数为 0；worker 没有 FAILURE/RETRY/CANCELLED 或框架错误。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
run() { step="$1"; cls="$2"
  $A -s "$S" logcat -c
  $A -s "$S" shell am instrument -w -r -e class "$cls" \
    com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
    > "build/device-track-batch33/$step.log" 2>&1
  $A -s "$S" logcat -v threadtime -d > "build/device-track-batch33/$step-logcat.txt"; }

run step13-isolation com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest
run step14-flags com.tingyun.smartmistakebook.visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest

# 9 类签名
D=build/device-track-batch33
for f in "$D"/*-logcat.txt; do
  printf "%-42s " "$(basename "$f")"
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed" "$f"
done
```

- 退出码：两步 0；签名 grep 对每个文件输出 `0`。

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---:|---|
| 隔离 | `OK (13 tests)` | 13 |
| flags | `OK (2 tests)` | 2 |
| skipped / ignored | 0 | 0 |
| 9 类签名（17 份 logcat 逐份） | 全部 0 | 0 |
| `Worker result FAILURE` | 0 | 0 |
| `Worker result RETRY` | 0 | 0 |
| `Worker result CANCELLED` | 0 | 0 |
| `E/WM-` / `W/WM-` | 0 / 0 | 0 |
| `ForegroundServiceStartNotAllowedException` | 0 | 0 |
| 应用 worker 结果 | `OrphanAssetGcWorker` SUCCESS ×2、`BatchImportDriverWorker` SUCCESS ×2 | 无失败 |

## 4. 关键输出（有界）

```text
step13-isolation: OK (13 tests)
step14-flags:     OK (2 tests)

Worker result SUCCESS for Work [ … OrphanAssetGcWorker,orphan-asset-gc ]
Worker result SUCCESS for Work [ … BatchImportDriverWorker,batch-import-driver ]
```

17 份 logcat 的 9 类签名逐份计数均为 `0`（见 §2 命令）。

## 5. 失败 / 跳过 / 异常

无。注意：`logcat -c` 后仍可见 07:xx 的旧行（包括 Google 自有 worker 的 SUCCESS 行），
说明缓冲区清理不彻底；签名与 worker 计数是对**当前缓冲区全量**的 grep，仍成立，但不等价于
逐套件隔离窗口（见索引 §5.5）。

## 6. 脱敏与去噪

- 删除：第三方/系统重复行（未入摘录）。
- 保留：两条 summary、worker 结果行、9 类签名计数。
- 扫描：0 命中；未包含包名以外的个人数据。
- 二进制/截图：无。

## 7. 判定

PASS。隔离/flags 全绿；9 类崩溃签名与 worker 失败面全 0。未覆盖：进程被系统杀死的场景
（`death was not confirmed` 计数为 0，但未构造该场景）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch33/step13-isolation*`、`step14-flags*` 及全部 `*-logcat.txt`
- 生成命令：见 §2
- 行数/大小：191 / 37 行；logcat 合计约 24k 行（PDF logcat 20,572 行占大头）
