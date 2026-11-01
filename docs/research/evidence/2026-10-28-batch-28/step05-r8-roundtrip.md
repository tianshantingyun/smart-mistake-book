# E-05 R-04 复试：release（R8）隔离往返——阻塞维持（当前树复确认，F1 修复已在包内）

| 字段 | 值 |
|---|---|
| evidence-id | E-05 |
| convention-version | 1 |
| batch | 2026-10-28-batch-28 |
| step | 四（工单"R8 下隔离往返复跑（R-02，接线修复后应可过）"） |
| gate-or-kd | R-04（清单 :232，v2 判据；"R8 隔离往返"按清单行义归 R-04，R-02 行义=arm64 本机不可采证——`2026-10-26-batch-24-4e2-admission-checklist.md:230/:232`）+ 批次 27 E-03 的 supersedes 复核 |
| verdict | **阻塞（维持批次 27 判定，当前树复确认）**：① R8 改名→test dex 类解析崩溃（`NoClassDefFoundError: Intrinsics/Trace`，测试体执行前）；② instrument 签名门（SecurityException，重签后过）；③ BAL/未导出拒启**本批未复试**（避免 adb root 设备态变更，批次 27 E-03 §3.3 证据沿用）。可交付半边：libquickjs.so 双 ABI 在包 ✓ + R8 类名审计 ✓。**工单前提"接线修复后应可过"不成立**：F1 已在 release 包内，三重阻塞与之正交 |
| executed-at | 2026-10-28T07:44:00+08:00 ～ 07:52:00+08:00 |
| operator | 设备验证轨（subagent，批次 28） |
| head | 8116ad98（proguard/build 配置 git 干净实测=解除路径零实施） |
| worktree | dirty（他线在飞 + 本轨两测试面更正） |
| device-serial | emulator-5554（test_device / API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | release `e90ce7f3…c337b09`（155,732,659 B，CN=Batch28 Local Smoke）+ 重签 test 包 `build/device-track-batch28/app-test-resigned-b28.apk`（1,585,880 B，内容同 debug test 包、仅签名替换） |
| apk-version | 0.2.0-offline |
| exit-code | 尝试 1 EXIT=1（SecurityException）；尝试 2 EXIT=0 但 `INSTRUMENTATION_RESULT: shortMsg=Process crashed.` + `INSTRUMENTATION_CODE: 0` |
| raw-log | `build/device-track-batch28/`：`step05-roundtrip-attempt{1,2}.log`、`step05-roundtrip-logcat.txt`、`resign.log`、`keystore-gen.log`、`build-release.log`（412 任务，2m16s） |
| record-ref | 批次 28 记录（记录员后写） |
| sanitized | 是（本文件 §6） |

## 1. 目的与判据

工单四要求复跑"R8 下隔离往返"并预期"接线修复后应可过"。开工只读复核已确立（本批基线，引用均实读）：批次 27 E-03（`evidence/2026-10-27-batch-27/step03-r04-release-roundtrip.md`）以三重独立实测判阻塞；解除路径三选一**仍归主会话**（批次 27 记录 `:209` 待裁清单；准入清单 `:37`"R-04 解除路径三选一…归主会话裁定/硬件窗口"）；本树 `app/proguard-rules.pro` 与 `app/build.gradle.kts` git 干净=keep 规则/`testBuildType` 零实施。本步按"复跑出证据"执行：R-04 v2 判据（release 上跑隔离套件至少往返+watchdog 两例 + `libquickjs.so` 在包）在当前树（含 F1 修复的 release 包）上重试，产出新鲜的拒绝证据，不改构建凑绿。

## 2. 命令（可复跑）

### 2.1 可交付半边（主机侧）

```bash
unzip -l app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk | grep quickjs
#   924936  lib/arm64-v8a/libquickjs.so
#   911248  lib/x86_64/libquickjs.so          → 非空 ✓（双 ABI，与批次 27 同字节数）
mkdir build/device-track-batch28/reldex && unzip -o -q <release.apk> "classes*.dex" -d reldex
for d in reldex/classes*.dex; do dexdump.exe "$d"; done | grep -oE "Class descriptor.*'Lcom/tingyun/smartmistakebook[^']*'" | sort -u
```

### 2.2 设备舞步（尝试 1 → 重签 → 尝试 2）

```bash
# 一次性本地 key 生成（口令随机、不落仓，证据写作 <local-smoke-b28>）：
keytool -genkeypair -v -keystore build/device-track-batch28/release-smoke-b28.jks \
  -alias batch28smoke -keyalg RSA -keysize 2048 -validity 30 \
  -storepass "<local-smoke-b28>" -keypass "<local-smoke-b28>" -dname "CN=Batch28 Local Smoke, O=Local, C=CN"
RELEASE_KEY_ALIAS=batch28smoke RELEASE_STORE_FILE=<repo>/build/device-track-batch28/release-smoke-b28.jks \
RELEASE_KEY_PASSWORD=<local-smoke-b28> RELEASE_STORE_PASSWORD=<local-smoke-b28> \
  ./gradlew --console=plain :app:assembleStrictOfflineRelease     # EXIT=0, 2m16s

$A -s emulator-5554 uninstall com.tingyun.smartmistakebook.offline.test   # Success
$A -s emulator-5554 uninstall com.tingyun.smartmistakebook.offline        # Success
$A -s emulator-5554 install -r -t <release.apk>                           # Success
$A -s emulator-5554 install -r -t <debug androidTest.apk>                 # Success
# 尝试 1（往返单例）：
$A -s emulator-5554 shell am instrument -w -r -e class \
  com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner   # EXIT=1 SecurityException
# 重签（纯产物操作，零源码/构建改动）：
apksigner.bat sign --ks build/device-track-batch28/release-smoke-b28.jks --ks-key-alias batch28smoke \
  --ks-pass pass:"<local-smoke-b28>" --key-pass pass:"<local-smoke-b28>" \
  --out build/device-track-batch28/app-test-resigned-b28.apk <debug androidTest.apk>  # EXIT=0
$A -s emulator-5554 uninstall com.tingyun.smartmistakebook.offline.test && $A -s emulator-5554 install -r -t build/device-track-batch28/app-test-resigned-b28.apk   # Success
# 尝试 2（同单例）：
$A -s emulator-5554 shell am instrument -w -r -e class …#workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater …   # shortMsg=Process crashed.
```

## 3. 结果摘要（当前树三重状态复核）

### 3.1 R8 改名（主机 dexdump，当前 release 包）

`com.tingyun.smartmistakebook` 下保留类名恰 12 个——`SmartMistakeBookApplication`、`MainActivity`、`ReviewReminderReceiver`、`BatchImportDriverWorker`、`ExportPdfWorker`、`OrphanAssetGcWorker`、`BatchImportFileProvider`、`MistakeDocxFileProvider`、`MistakePdfFileProvider`、`StudyDatabase`、`StudyDatabase_Impl`、**`visualjs.VisualJsService`**。`IVisualJsService`/`IVisualJsCallback`/协议/引擎类全部改名（同批次 27 E-03 §3.2 清单；`app/proguard-rules.pro` 仅 Room/MLKit keep，未变）。

### 3.2 尝试 1：instrument 签名门（与批次 27 同型）

```text
INSTRUMENTATION_STATUS: Error=Permission Denial: starting instrumentation
  ComponentInfo{com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner}
  from pid=16643, uid=2000 not allowed because package com.tingyun.smartmistakebook.offline.test
  does not have a signature matching the target com.tingyun.smartmistakebook.offline
```

### 3.3 尝试 2：重签后 instrument 真启，app 进程测试体执行前崩溃

```text
INSTRUMENTATION_RESULT: shortMsg=Process crashed.
INSTRUMENTATION_CODE: 0
10-27 23:48:11.958 16751 16751 E AndroidRuntime: FATAL EXCEPTION: main
10-27 23:48:11.958 16751 16751 E AndroidRuntime: java.lang.NoClassDefFoundError: Failed resolution of: Lkotlin/jvm/internal/Intrinsics;
10-27 23:48:11.962 16751 16751 E AndroidJUnitRunner: java.lang.NoClassDefFoundError: Failed resolution of: Landroidx/tracing/Trace;
```

与批次 27 E-03 §3.2 **同签名**（Intrinsics + androidx.tracing.Trace，R8 后 app dex 无法解析 debug test dex 按原名引用的共享类型）。

### 3.4 BAL/未导出通道（未复试）

`am start-service` 直拉通道本批未复试：需 `adb root`→`unroot` 设备态变更（本批纪律从简），批次 27 E-03 §3.3 的实测拒绝（uid 2000 non-exported 拒启、uid 0 BAL 拒启）在该通道无代码变更的前提下仍为有效证据。

## 4. 与工单前提的对账

工单四括注"（R-02，接线修复后应可过）"的两个前提均不成立，且为**工单继承的口径问题**（非新事实）：

1. **行义**："R8 下隔离往返"按准入清单行义属 **R-04**（release R8 变体 QuickJS JNI 面）；R-02 行义=arm64 运行时，本机 x86_64 宿主原理性不可采证（清单 `:230`，批次 27 基线 §7.1/§10.1 已裁定"按清单行义同步"）。本步按 R-04 行义执行。
2. **"接线修复后应可过"**：F1 修复确已编入本 release 包（`QuickJsVisualExecutionEngine.kt` 同源），但批次 27 E-03 已证三重阻塞（R8 改名崩溃/签名门/BAL）在**任何测试体执行之前**，与 compile 接线正交；本步在含修复的包上复现同型崩溃 → 前提被实测否证。往返仍未到达任何 `execute` 调用。

## 5. 脱敏与去噪

- 删除：安装进度行；Gradle 全量留 `build/`。
- 保留：三次实测拒绝全文、12 类名清单、libquickjs 条目、崩溃 stack 首行。
- 口令：一律 `<local-smoke-b28>`；`.kspass` 已删除（收口动作，`step00` §4）。
- 扫描：见 `00-index.md` §6。二进制/截图：无。

## 6. 判定与恢复

**阻塞（R-04 维持）**：当前树（含 F1）上三重阻塞面中两重新鲜复现（签名门 + R8 崩溃），第三重沿批次 27 证据未复试；可交付半边 PASS（双 ABI + 类名审计）。R-04 v2 判据在本仓现状下仍不可执行，解除路径（keep 规则 / 可测 release 变体 / 判据改口径）**维持归主会话**；本轨零构建改动（一次性本地 key 与重签均为 `build/` 内产物操作）。

设备恢复：release 与重签 test 包卸载，debug 三件套（v3）重装，三包设备侧 sha256 与产物实测一致（`step00` §5）；`ps -A -o PID,NAME | grep visual_js` 无存活 worker（GREP_EXIT=1）。

## 7. 原始全量（不入仓）

- `build/device-track-batch28/step05-roundtrip-attempt1.log` / `attempt2.log` / `step05-roundtrip-logcat.txt` / `resign.log` / `keystore-gen.log` / `build-release.log` / `reldex/`。
- `build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
