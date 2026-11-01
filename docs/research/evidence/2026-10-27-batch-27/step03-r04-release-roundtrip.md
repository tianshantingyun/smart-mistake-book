# E-03 R-04 release（R8）隔离往返——**阻塞**（三重实测）+ 可交付半边

| 字段 | 值 |
|---|---|
| evidence-id | E-03 |
| convention-version | 1 |
| batch | 2026-10-27-batch-27 |
| step | 三（R-02/R-04：strictOffline release/minified 隔离 execute 往返，AIDL+JS 真跑） |
| gate-or-kd | R-04（清单 ：228，v2 判据；R-02 行已按 T5 注记把该面归 R-04）+ KD-69 侧证 |
| verdict | **阻塞**：release 变体仪器化往返在本仓现状下不可能（R8 改名→test dex 类解析崩溃；instrument 签名门；服务非 exported/BAL 拒启——三重独立实测）。可交付半边已做：`libquickjs.so` 双 ABI 在包 ✓ + R8 类名审计 ✓。**且 compile 接线缺陷（step05）修复前，任何变体都无 `Rendered` 往返可跑。** 解除路径归主会话裁定（§6） |
| executed-at | 2026-10-28T03:43:00+08:00～03:48:00+08:00 |
| operator | 设备验证轨（subagent，批次 27） |
| head | caa0726a02f1862371a3b2770c8c29663ba7fa02 |
| worktree | dirty（他线在飞） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk`（一次性本地 key）+ `build/device-track-batch27/app-test-resigned-b27.apk`（重签 test 包） |
| apk-sha256 | release `950b7eee31c46561d33a34cbd202c7b01c0b753e308adb830c02fead2d26c7cf`（设备侧一致）；重签 test 包内容与 `cde7e27b…` 同源（仅签名替换，apksigner） |
| apk-version | 0.2.0-offline |
| exit-code | 尝试 1 退出码 1（SecurityException）；尝试 2 退出码 0 但 `INSTRUMENTATION_RESULT: shortMsg=Process crashed`；root 启动服务 255（BAL 拒） |
| raw-log | `build/device-track-batch27/`：`step04-release-roundtrip-attempt{,2}.log`、`step04-release-logcat{,2}.txt`、`install-release*.log`、`build-release.log`（506 行） |
| record-ref | 批次 27 记录（由记录员后写） |
| sanitized | 是（§7） |

## 1. 目的与判据

R-04 v2 判据（清单 ：228）：release（R8）变体 APK 上跑 `VisualJsIsolationInstrumentedTest`
至少"往返 + watchdog"两例，且 `unzip -l <release.apk> | grep libquickjs.so` 非空。本步实测
判据的可达性边界，产出全部实测拒绝证据；不做任何构建/规则改动来"凑绿"。

## 2. 命令（可复跑）

### 2.1 可交付半边（PASS）

```bash
unzip -l app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk | grep quickjs
#   924936  lib/arm64-v8a/libquickjs.so
#   911248  lib/x86_64/libquickjs.so          → 非空 ✓（双 ABI）
C:/Android/Sdk/build-tools/37.0.0/apksigner.bat verify --print-certs <release.apk>
#   V2 Signer: certificate DN: CN=Batch27 Local Smoke, O=Local, C=CN（一次性本地 key）
# R8 类名审计（主机侧，dexdump）：
for d in classes*.dex; do dexdump.exe "$d"; done | grep -oE "Class descriptor.*'Lcom/tingyun/smartmistakebook[^']*'"
```

### 2.2 往返尝试（两次，均真实设备执行）

```bash
$A -s emulator-5554 uninstall com.tingyun.smartmistakebook.offline.test   # + uninstall 主包
$A -s emulator-5554 install -r -t <release.apk>                            # Success
$A -s emulator-5554 install -r -t <debug androidTest.apk>                  # Success
$A -s emulator-5554 shell am instrument -w -r -e class \
  com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# 尝试 1 → SecurityException（签名门）
C:/Android/Sdk/build-tools/37.0.0/apksigner.bat sign --ks build/device-track-batch27/release-smoke-b27.jks \
  --ks-pass pass:<local-smoke-b27> --key-pass pass:<local-smoke-b27> \
  --out build/device-track-batch27/app-test-resigned-b27.apk <debug androidTest.apk>   # EXIT=0
# 重签（仅签名替换，内容同源）→ reinstall → 尝试 2 → Process crashed
# 附：直接拉起服务的通道（为取 release worker warm-up 证据）
$A -s emulator-5554 shell am start-service -n com.tingyun.smartmistakebook.offline/com.tingyun.smartmistakebook.visualjs.VisualJsService
# uid 2000：Error: Requires permission not exported from uid 10209
$A -s emulator-5554 root && $A -s emulator-5554 shell am start-service -n …VisualJsService
# uid 0：Error: app is in background uid null（Background start not allowed）
$A -s emulator-5554 unroot   # 恢复 shell
```

## 3. 结果摘要（三重独立实测阻塞）

### 3.1 第一重：instrument 签名门（尝试 1 实测）

```text
INSTRUMENTATION_STATUS: Error=Permission Denial: starting instrumentation
  ComponentInfo{com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner}
  from pid=13558, uid=2000 not allowed because package com.tingyun.smartmistakebook.offline.test
  does not have a signature matching the target com.tingyun.smartmistakebook.offline
```

debug 签名的 test 包 vs 本地 key 签名的 release 主包 → AMS 直接拒启。处置：用同一一次性
本地 key 重签 test APK（**纯产物操作，零源码/构建改动**，test 包在 `build/` 不入仓）。

### 3.2 第二重：R8 混淆 vs test dex 类名（主机 dexdump + 尝试 2 崩溃实测）

主机侧（批次 27 新 release APK dexdump）：`com.tingyun.smartmistakebook` 下仅 12 个类保留
类名——`SmartMistakeBookApplication`/`MainActivity`/`ReviewReminderReceiver`/
`BatchImportDriverWorker`/`ExportPdfWorker`/`OrphanAssetGcWorker` + 3 个 FileProvider +
`StudyDatabase(_Impl)` + **`visualjs.VisualJsService`（唯一存活的 visualjs 类）**。
`IVisualJsService`/`IVisualJsCallback`/`VisualJsWire`/`VisualJsProtocol`/
`VisualJsClientEngine` 与 core.domain 全部请求/结果类型**均被 R8 改名**（本仓 proguard 规则
仅 Room/MLKit 两条 keep，`app/proguard-rules.pro:8-19`；无 `-dontobfuscate`）。

设备侧（重签后尝试 2，签名门已过、instrument 真实启动）：

```text
INSTRUMENTATION_RESULT: shortMsg=Process crashed.
10-27 19:40:26.176 13710 E AndroidRuntime: FATAL EXCEPTION: main
10-27 19:40:26.176 13710 E AndroidRuntime: java.lang.NoClassDefFoundError: Failed resolution of:
  Lkotlin/jvm/internal/Intrinsics;
10-27 19:40:26.177 13710 E AndroidJUnitRunner: java.lang.NoClassDefFoundError: Failed resolution of:
  Landroidx/tracing/Trace;
```

app 进程在 test 类首触即崩（R8 后的 app dex 无法解析 test dex 按原名引用的共享类型）——
**在任何测试体执行之前**。绑定式夹具（先 `bindService` 后解析 AIDL 接口）也到不了
`asInterface`；即便到得了，`IVisualJsService` 同样已改名。即：
**debug 构建的 androidTest APK 与 R8 release 主包结构不兼容**（AGP 默认 `testBuildType=debug`
下的已知限制在本仓的实测落地）。

### 3.3 第三重：无 instrument 的独立 worker 通道也不存在（实测拒绝）

| 通道 | 实测 |
|---|---|
| `am start-service`（shell, uid 2000） | `Error: Requires permission not exported from uid 10209`（服务按设计 non-exported，`AndroidManifest.xml:70`） |
| `am start-service`（root, uid 0，`adb root` 后） | `Error: app is in background uid null` + AMS 行 `Background start not allowed … from pid=13762 uid=0 pkg=com.android.shell`（API 34 后台启动限制对 shell/root 调用方同样生效） |
| 产品自身触发 | `VisualJsClientEngine` 在 main 源码零调用点（4E.2 UI 未接线），无生产路径可执行 JS |

`adb root`/`unroot` 已恢复（`id` = uid 2000 shell）。

### 3.4 结论

R-04 v2 的 release 往返判据在本仓现状下**不可执行**（三重独立阻塞），本轨不改构建凑绿。
可交付半边：`libquickjs.so` 双 ABI 在包（§2.1）✓、R8 类名审计 ✓、release 构建本身
exit=0。设备已恢复 debug 态（step00 §5）。

## 4. 与 KD-69 的关系

本步不充当 KD-69 的 release 冒烟（批次 26 E-04 已有冷启动 ×2 证据，本批 release 构建仅为
R-04 判据可达性测试而构建，未做冷启动冒烟——工单四步未列）；KD-69 状态不变。

## 5. 失败 / 跳过 / 异常

尝试 1/2 的"失败"即本步证据本体（两次均真实执行、原始输出全存 `build/`）。另：release
worker 的 warm-up 级证据（fork→ready）因 §3.3 两路拒启**未取得**——release 侧的运行时
装载面维持 UNVERIFIED（连同 §3.2 的类名事实一并登记）。

## 6. 解除路径（需主会话裁定，本轨不擅自改）

1. **keep 规则**：对测试面（`com.tingyun.smartmistakebook.visualjs.**`、AIDL 接口、
   core.domain 请求/结果类型）加 keep——削弱 R8 混淆面，属发布语义变更。
2. **可测 release 变体**：`testBuildType`/独立测试变体 + `-dontobfuscate`——同样削弱。
3. **判据口径变更**：R-04 v2 的"release 上跑 VisualJsIsolationInstrumentedTest"改为
   "release 产物级审计（libquickjs/类名/映射表）+ warm-up 级运行证据"——需清单改文本。
4. **前提**：无论选哪条，step05 的 compile 接线缺陷必须先修复，否则任何变体都无
   `Rendered` 往返。

## 7. 脱敏与去噪

- 删除：安装进度行；Gradle 全量日志留 `build/`。
- 保留：三次实测拒绝全文、dexdump 类名清单、libquickjs 条目、证书 DN/指纹。
- 一次性 keystore 口令写作 `<local-smoke-b27>`，不落明文。
- 扫描：见 `00-index.md` §6。二进制/截图：无。

## 8. 判定

**阻塞（R-04 release 往返）**，证据三重独立且全部实测（§3.1–3.3）；可交付半边 PASS
（§2.1）。未覆盖：release 侧 warm-up 运行证据 UNVERIFIED（§5）；生产签名 UNVERIFIED；
arm64 release 沿既有缺口。
