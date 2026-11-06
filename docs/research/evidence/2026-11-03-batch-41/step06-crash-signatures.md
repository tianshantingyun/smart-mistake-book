<!-- 体积自查：约 85 行；R4 上限 400 行 / 65536 B -->
# E-41-D06 九类崩溃签名扫描（本轮全部设备 logcat）

| 字段 | 值 |
|---|---|
| evidence-id | E-41-D06 |
| convention-version | 1 |
| batch | 2026-11-03-batch-41 |
| step | 设备轨步骤六（计划 ask 6；计划 §2 WS5 37） |
| gate-or-kd | 崩溃签名九类（批次 40 E-40-05 §3 同口径） |
| verdict | 九类签名 **全部 0**；但本轮的 0 **不能**解读为"健康"：isolation 套件全跳过、KD-76 单轮未保留全量 logcat、p10 未运行 |
| executed-at | 2026-11-03T01:20+08:00（UTC 17:20Z） |
| operator | 设备轨 |
| head | `62522930`；源快照 `src-sha-after-fix.txt` |
| device-serial | emulator-5584 |
| apk-sha256 | 见 E-41-D01 §3（本轮 logcat 来自 localFirst 与 strictOffline 两套包） |
| exit-code | 扫描脚本 exit=0 |
| raw-log | `build/device-track-batch41/crash-scan.txt`；扫描脚本 `build/device-track-batch41/scan_crash_signatures.sh` |
| record-ref | 批次 40 记录 §8 #7；E-40-05 §3–§4 |
| sanitized | 是 |

## 1. 扫描范围

- 文件集：`build/device-track-batch41/**/*-logcat.txt`，共 **17** 个（schema 七套件 + drill 阶段 A/B + PDF 五段 + isolation 三段）。
- 命令：`bash build/device-track-batch41/scan_crash_signatures.sh`（逐文件 `grep -c -F` 后求和；非零项再 `grep -Hn` 归属）。

## 2. 结果

| 签名 | 命中 |
|---|---|
| `FATAL EXCEPTION` | 0 |
| `ANR in` | 0 |
| `NoSuchMethodError` | 0 |
| `NoClassDefFoundError` | 0 |
| `UnsatisfiedLinkError` | 0 |
| `SIGSEGV` | 0 |
| `CANNOT LINK` | 0 |
| `failed to map segment` | 0 |
| `death was not confirmed` | 0 |

- drill 阶段 A 的 `shortMsg=Process crashed.` 是 `Process.killProcess(myPid())` 的设计路径，logcat 无 `FATAL EXCEPTION` 栈；与本表 0 命中一致。

## 3. 为什么 0 不能当健康证明（必须同时读）

- `Start proc .*:visual_js` / `warm-up finished` / `warm-up failed` / `watchdog hard deadline` 在本轮 logcat 中**均为 0**——因为 isolation 套件 15 例全部 `-4` 未执行（E-41-D05 §2），根本没有起 `:visual_js` 进程。worker 生命周期账本本轮**无证据**，不是"干净"。
- KD-76 单轮脚本只把 `KD76_*` 行与 am 输出收进 XML，未落全量 logcat；该轮的崩溃签名不在本扫描覆盖内。
- `death was not confirmed` 在批次 40 来自 p10 设计路径；本 ask 未运行 p10，故 0 是"未覆盖"而非"未发生"。
- 因此本文件的结论仅限：**本轮已采集的 17 份 logcat 中，九类签名 0 命中**。

## 4. 未验证

- 28 套件全量签名扫描、worker 账本、`ps -A` 无孤儿进程检查（isolation 未跑，套件后无进程快照）→ UNVERIFIED。
- 16KB 页 / `mapping.txt` 不在本 ask。
