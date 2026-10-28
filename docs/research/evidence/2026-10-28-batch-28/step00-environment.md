# E-00 环境与设备身份（批次 28）

| 字段 | 值 |
|---|---|
| evidence-id | E-00 |
| convention-version | 1 |
| batch | 2026-10-28-batch-28 |
| verdict | PASS（信息性，不参与 KD-74 覆盖判定） |
| executed-at | 2026-10-28T07:20:00+08:00 ～ 07:52:00+08:00 |
| operator | 设备验证轨（subagent，批次 28） |
| head | 8116ad98（批次 27 记录提交；本批开工 HEAD，未变动） |
| worktree | dirty（683 条，他线在飞 + 本批 T2/T3 落层；修复面 visualjs/core:data visual git 干净，painter 三件为未跟踪新文件） |
| sanitized | 是（本文件无用户数据；目录级扫描见 `00-index.md` §6） |

## 1. 主机工具

| 工具 | 路径/版本 | 备注 |
|---|---|---|
| adb | 1.0.41（Version 37.0.1-15733141），`C:/Android/Sdk/platform-tools/adb.exe` | 实测 `adb version` |
| build-tools | 37.0.0（aapt2/apksigner/dexdump/zipalign） | 实测调用 |
| keytool | jdk-21.0.11.10-hotspot | 仅用于本批一次性本地 key（§4） |
| node | v26.4.0 | Node 台编译器测试（E-01 §2.0） |
| MSYS_NO_PATHCONV | 1（全程 export） | 工单要求 |

## 2. 设备

| serial | AVD | API | ABI | `getconf PAGE_SIZE` | `ro.product.cpu.page.size` | `ro.product.page_size` / `ro.boot.hardware.cpu.pagesize` | 用途 |
|---|---|---|---|---|---|---|---|
| emulator-5554 | test_device | 34 | x86_64 | 4096 | `<empty>` | `<empty>` / `<empty>` | 本批全部五步（SceneV1 四套件 / painter / 备份 / R-04 复试） |
| emulator-5556 | ps16k_x86_64 | 36 | x86_64 | 16384 | `16384` | `<empty>` / `<empty>` | **只读核**（`emu avd name`/SDK/ABI/PAGE_SIZE 四值；未安装未执行，同批次 27 口径） |

- emulator-5584 属他线，全程未触碰（开工时三台在线，`adb devices -l` 实测）。
- 5554 开工沉降态：uptime 86738.78 s（约 24 h）、loadavg 0.11、`sys.boot_completed=1`（`/proc/uptime`、`/proc/loadavg`、`getprop` 实读）。
- 设备指纹：`google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys`（5554）；`google/sdk_gphone16k_x86_64/emu64xa16k:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys`（5556）。

## 3. 构建与产物身份

构建命令（工单 (1)，全部为 `assemble` 类任务，零 `connectedAndroidTest`）：

```bash
./gradlew --console=plain :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest :core:data:assembleDebugAndroidTest
# EXIT=0，BUILD SUCCESSFUL in 29s（build/device-track-batch28/build-debug.log）
# 修复后重建两轮（见 E-02/E-03 先红后绿）：
#   :core:data:assembleDebugAndroidTest v2 EXIT=0（build-data-test-v2.log）
#   :core:data:assembleDebugAndroidTest v3 EXIT=0（build-data-test-v3.log）
# release（R-04 复试用）：
./gradlew --console=plain :app:assembleStrictOfflineRelease
# EXIT=0，BUILD SUCCESSFUL in 2m 16s（build-release.log）；签名 = 本批一次性本地 key（§4）
```

| 产物 | 字节 | sha256 | versionName | 备注 |
|---|---|---|---|---|
| app debug APK | 189,406,722 | `9ad2cd739ce12ed4ee391aa16ae46136e9358ba87a5a68de12a8be57947329aa` | 0.2.0-offline | 含 F1/F2 修复（经 core:data 依赖打入） |
| app debug androidTest APK | 1,585,488 | `cde7e27b9a6c0ab90db06f05e6f62465c729e220593b262980f374cc9becb293` | — | **与批次 27 逐字节一致**（app androidTest 源零改动） |
| core:data androidTest APK v1 | 197,648,091 | `5421a0355f2c91050c3920e93746dd61dfa83e8b94fa42e055fa646622c44cbe` | — | 首跑（编译器套件红轮）用 |
| core:data androidTest APK v2 | 197,648,091 | `855a25a8ef9fb4c09691e9917a411e761ac403aaa4e2f7b7b914a156c6ec344b` | — | 编译器断言更正后（E-02） |
| core:data androidTest APK v3 | 197,648,091 | `a24d2c854d3250f10b6f1da7779af3385fa1771f81107b55237cdaf51fd79857` | — | painter 夹具更正后（E-03）；**收口在装版本** |
| app release APK（一次性本地 key） | 155,732,659 | `e90ce7f3348a8f41749caa23a9110c372b534f2441ed313a7e4ffae32c337b09` | 0.2.0-offline | `CN=Batch28 Local Smoke`，非生产证书（E-05） |

安装与设备侧身份（工单 (3)，`adb install -r -t` ×三包均 `Success`）：

- 三个 debug 包安装后设备侧 `pm path` → `sha256sum` 与上表逐字节一致（首装 v1；R-04 复试后恢复装 v3，恢复态 sha256 同样实测一致）。
- dex 套件符号（防旧包跑新门，工单 (2)）：app androidTest dex 字符串池含 `Lcom/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest;`、`…VisualJsHandshakeRaceInstrumentedTest;` 及全部 13 用例内部类（dexdump 实测）；core:data androidTest dex 含 `QuickJsSceneCompilerInstrumentedTest`、`QuickJsVisualExecutionEngineInstrumentedTest`、`VisualDrawOpsPainterInstrumentedTest`、`VisualDrawOpsPainter`（v3 后亦含两处更正，dexdump 实测）。

## 4. 一次性本地 key（release 构建/R-04 复试/重签）

- keystore：`build/device-track-batch28/release-smoke-b28.jks`（alias `batch28smoke`，CN=Batch28 Local Smoke，30 天，`build/` 不入仓）。
- 口令：开机随机生成，仅存 `build/device-track-batch28/.kspass`（gitignored 实测，已收口删除），**不落任何仓内文件与日志**；证据中一律写作 `<local-smoke-b28>`。
- 用途仅两处：release 构建签名（`RELEASE_*` 环境变量，`app/build.gradle.kts:111-114` 读取）与 debug androidTest APK 重签（E-05 §3）。生产 `RELEASE_*` 仍缺失，生产签名 UNVERIFIED（沿批次 26/27 口径）。

## 5. 数据保护

- 设备用途：专用测试模拟器（5554/5556），无真实学习者数据；测试只走夹具与备份用例自带临时库。
- 测试后检查：debug 三件套（v3）重装、设备侧 sha256 与产物一致；5556 未触碰；全程唯一设备态变更 = R-04 复试的卸载/重装舞步（已恢复）+ 无 `adb root`（本批未用 root 通道，区别于批次 27）。

## 6. 数据保护结论

未触及真实数据；恢复已核对（三包 sha256 实测一致）。
