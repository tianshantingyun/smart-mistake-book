# E-02 A-09 广告-执行一致性·注入式设备套件（首跑 1 红 → 同 APK 复跑全绿）

| 字段 | 值 |
|---|---|
| evidence-id | E-02 |
| convention-version | 1 |
| batch | 2026-11-01-batch-38 |
| step | 步骤二·新增设备例 1（A-09 注入式，`VisualJsAdvertiseThenExecuteInstrumentedTest`） |
| gate-or-kd | 批次 38 §1 B/E·A-09（清单 `:197`："ads JS → 执行成功，而非 UNAVAILABLE；0 远程派发"） |
| verdict | PARTIAL→**PASS（以复跑轮为准）**：首跑 1/3 红（环境归因 D），复跑 3/3 绿（同一 APK sha） |
| executed-at | 首跑 2026-11-01T04:13+08:00（step21）；复跑 2026-11-01T04:2x+08:00（step21b） |
| operator | 设备验证轨（批次 38 工作流子代理） |
| head | `dcfdc5b5`（同 E-01） |
| worktree | 786 条（仅记录） |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | app androidTest（E-01 §3 同包） |
| apk-sha256 | `33e1d6a24c5b785af7752e585d9408847dc7991dc1962b85349a0297d75161fa`（首跑与复跑同一份，安装后设备端 sha 复核一致） |
| apk-version | strictOffline debug androidTest |
| exit-code | 首跑 adb 退 0（JUnit 层 FAILURES）；复跑退 0（OK） |
| raw-log | `build/device-track-batch38/step21-a09-ads.log`（31 行）+ `step21-a09-ads-logcat.txt`；`step21b-a09-ads-rerun.log` + logcat（本机，不入仓） |
| record-ref | `docs/research/2026-11-01-batch-38-record.md` 设备节 |
| sanitized | 是（00-index §6） |

## 1. 目的与判据

A-09 注入式三例（构建前计数 3，新文件 untracked）：① 注入"生产默认 IMAGE_MODEL_ONLY + JS"快照 → GENERATE_FIGURE 的 JS 调用真在本机执行成功（coordinator GENERATED + Program 产物）且 0 远程派发四断言位（审计 operation 0/0+SUCCEEDED、attempt `method='JS' AND reservation_id IS NULL` 恰一行、账本 `dispatch_count` 写前写后 0、产物恒 Program）；② 生产装配负控（三参 create 不注入引擎）→ UNAVAILABLE 且 0 审计行；③ 生产默认快照（JS 不在广告面）→ policy REJECTED（"本机未接通 JS 方法，不得假装可用"）且不执行、0 审计行、账本零触碰。判据 = 套件 KDoc 断言面（`VisualJsAdvertiseThenExecuteInstrumentedTest.kt:52-112`），不放宽。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class com.tingyun.smartmistakebook.visualjs.VisualJsAdvertiseThenExecuteInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s $S logcat -v threadtime -d   # 两轮各采一次（step21 / step21b）
```

- 工作目录：仓库根；退出码：两轮 adb 层均 0。
- 复跑轮 = 完整套件重跑（同 APK、同命令），不是 `-e class ...#method` 红例过滤（禁止清单遵行）。

## 3. 结果摘要

| 指标 | 首跑 step21 | 复跑 step21b | 判据 |
|---|---|---|---|
| tests run | 3 | 3 | =构建前计数 3 |
| failures | **1** | **0** | 期望 0 |
| skipped/assumption | 0 | 0 | 期望 0 |
| `OK (N tests)` | FAILURES!!! Tests run: 3, Failures: 1 | **OK (3 tests)**，Time 8.708 s | OK (3) |
| 签名命中（FATAL/ANR/NoSuchMethod/NoClassDef/UnsatisfiedLink/SIGSEGV/CANNOT LINK/map segment） | 0 | 0 | 期望 0 |
| watchdog kill 次数 | 1（pid 10780，非设计杀） | 0 | — |

## 4. 首跑红例（有界摘录）

```text
Error in anAdvertisedJsSnapshotRendersThroughTheCoordinatorWithZeroRemoteDispatchWitnesses
  (...VisualJsAdvertiseThenExecuteInstrumentedTest):
java.lang.AssertionError: actual: status=FAILED diagnostics=HARD_DEADLINE_EXCEEDED:
  visual_js worker died: binder died expected:<GENERATED> but was:<FAILED>
```

logcat 同窗（设备时钟 UTC）：

```text
10-31 20:13:26.184  ActivityManager: Start proc 10780:…:visual_js…/u0i196 for service …VisualJsService
10-31 20:13:30.521  10780 VisualJsService: visual_js warm-up finished; worker ready for requests
10-31 20:13:32.380  10780 VisualJsService: killing :visual_js worker: watchdog hard deadline
10-31 20:13:32.403  ActivityManager: Process …:visual_js… (pid 10780) has died: vis FGS
10-31 20:13:32.647  10713 TestRunner: java.lang.AssertionError: …（上句）
```

两负控同窗通过（`theProductionAssembly…` UNAVAILABLE、`theDefaultImageModelOnly…` REJECTED 断言诊断原文命中）。

## 5. 失败归因（批次 37 §五 序：A 夹具 → B 他线 → D 旧包/环境 → C 产品回归）

- **D 旧包/环境（裁定）**：服务侧 L2 看门狗（1500 ms，`VisualJsService.kt:528` killSelf("watchdog hard deadline")；L2 语义 `:40`）在 ready 后 1.86 s 杀掉 worker——纯 JS 工作（圆 + label 的小程序）在该时刻未在 1500 ms 内完成。判 D 非 C 的证据链：①同 APK、同机制、同形状的执行在同一分钟带内大量通过（本套件两负控、后续 step19 V-01 三例含同链 label 执行、step22 P-09 四帧全 Rendered）；②隔离套件同窗同签名两红（E-06），红例分布与具体程序内容无关（纯几何程序也红）；③**同一 APK sha 上整套装件复跑全绿**（step21b OK (3 tests)）；④主机负载背景：4 台模拟器同机，loadavg 7.36（MSYS 视角），CPU 51%/内存充足（E-00 §2）——突发宿主 CPU 争用拖慢 guest 线程为唯一可观测环境因子。⑤非 A：夹具在红/绿两轮之间零改动；非 B：`VisualContentMeasuring`（他轨 M-01/M-03 面）被排除——纯几何失败用例不触度量路径（E-06 同证）。
- **设计行为确认**：kill 本身是 L2 fail-closed 设计（慢程序被杀 → 宿主如实收 HARD_DEADLINE_EXCEEDED），不是崩溃；九类崩溃签名 0。
- 残余登记：环境归因由"同 APK 绿复跑 + 同形状大量通过 + 非确定性分布"支撑；不能完全排除极低频瞬态停顿的其他成因——首跑红例原文按原样保留于本文件与本批 raw-log。

## 6. 脱敏与去噪

- 删除：无（摘录即判定相关行）；保留：Start proc/has died/watchdog 行与断言原文。
- 扫描：00-index §6 统一记录 → 0 hits。
- 二进制/截图：无。

## 7. 判定

**PASS（以 step21b 复跑轮为准）**：复跑 3/3 绿，正例四断言位（审计 0/0+SUCCEEDED、attempt JS 形状恰一行、账本零触碰、产物 Program）与两负控（UNAVAILABLE/REJECTED 各自机制）在指定设备与 APK 上确实执行通过；首跑红例按 D（环境）归因登记并被复跑取代。未覆盖项：无（构建前计数 3 全覆盖）。
