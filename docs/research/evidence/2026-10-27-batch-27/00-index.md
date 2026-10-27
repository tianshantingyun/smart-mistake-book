# 批次 27 设备证据索引（2026-10-27 开工，设备执行跨 2026-10-28 03:0x–03:5x +08:00）

- 记录：`docs/research/2026-10-27-batch-27-record.md`（**后写**：批次收口时由记录步创建；
  本索引 §3/§4 与各 step 的 E-ID/path:line 即其设备证据回链源）
- 计划：`.zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md`（先完整读，§1"做"设备轨列
  P-08 dumpsys 审计 / A-11 负向 / R-02(R8 隔离往返，按清单行义归 R-04) / warm-up 装载成本实测 /
  隔离回归五步）
- 工作树 HEAD：`caa0726a02f1862371a3b2770c8c29663ba7fa02`（批次 26 记录提交）；开工即脏
  （698 条，他线在飞 + 批次 27 T1–T6 落层）；本轨新增仓内文件 = 本目录 7 个 `.md`；
  零产品/测试源代码改动、零 git 写操作、零 `connectedAndroidTest`
- 执行者：设备验证轨（subagent，批次 27）
- 证据目录：`docs/research/evidence/2026-10-27-batch-27/`
- 规范版本：`docs/research/2026-10-26-device-evidence-convention.md` v1（R4）
- 时间口径：`executed-at` 用主机时钟（+08:00）；设备系统时钟为 UTC（=主机 −8h），设备侧
  logcat 时间戳原文引用 UTC 值，不换算

## 1. 环境

| serial | AVD | API | ABI | PAGE_SIZE | 用途 |
|---|---|---|---|---|---|
| emulator-5554 | test_device | 34 | x86_64 | 4096 | 本批全部五步（隔离套件/P-08/A-11/R-04 尝试/warm-up 采样/备份抽验） |
| emulator-5556 | ps16k_x86_64 | 36 | x86_64 | 16384 | **只读核**（`emu avd name`/SDK/页大小三值；本批未安装未执行） |

- emulator-5584 属他线，全程未触碰。5554 开工沉降态（up 19:44、load 0.17）；全程唯一一次
  设备态变更 = `adb root`→`adb unroot`（R-04 步内 root 通道尝试，§step03；已恢复 shell uid）。
- 设备恢复：release 舞步后已卸载 release 与重签 test 包、重装 debug 主包 + debug androidTest
  包 + core:data androidTest 包，三个 install 均 `Success`，设备侧 sha256 与产物一致
  （`step00` §5）。

## 2. 产物身份

| 产物 | sha256（前 16 位…完整值见 step00 §4） | 备注 |
|---|---|---|
| app debug APK | `43adac58c0b6c28e…30940a58` | 189,399,345 B；隔离套件/P-08/A-11/采样/备份用 |
| app debug androidTest APK | `cde7e27b9a6c0ab9…cc9becb293` | 1,585,488 B；**与批次 26 逐字节一致**（本批 app androidTest 源零改动） |
| core:data androidTest APK | `64264e505f16171f…1a78d8fd8` | 197,639,024 B；含 T1/T3 新引擎用例（归因步使用） |
| app release APK（一次性本地 key） | `950b7eee31c46561…d2d26c7cf` | 155,732,032 B；cert `CN=Batch27 Local Smoke` SHA-256 `1ac91105…1beea`；**非生产证书** |

构建：debug 三件套 exit=0（43 s）；release exit=0（2 m 57 s，`RELEASE_*` 用一次性本地 key，
口令全文一律写作 `<local-smoke-b27>`，keystore 在 `build/` 不入仓）。

## 3. 步骤与结论

| step | 工单步 | 门/清单行 | 判定 | 证据 |
|---|---|---|---|---|
| step00 | 环境/构建/产物身份 | — | PASS（信息性） | `step00-environment.md` |
| step01 | 一 P-08 隔离启动面审计 | P-08（清单 ：131，R2 G-1） | **PASS（0 Provider 暴露 + 短路实证）**，命令漂移 1 项登记 | `step01-p08-isolation-surface.md` |
| step02 | 二 A-11 隔离进程网络/filesDir 负向 | A-11（按 T4 注记=P-08 验收 ④） | **观测级成立**（isolated_app 域 + 无网络组 + DAC/MCS 双拒）；进程内 Socket/filesDir 写负向未执行（无 runner，如实登记） | `step02-a11-isolated-negative.md` |
| step03 | 三 R-02/R-04 release 隔离往返 | R-04（v2 判据） | **阻塞**（三重实证：R8 改名→test dex 类解析崩溃；instrument 签名门；BAL/未导出拒启）；libquickjs.so 核 ✓；修复选项归主会话 | `step03-r04-release-roundtrip.md` |
| step04 | 四 warm-up 装载成本实测 | KD-76 缺口（P-07 承接） | **出数**：控制组 n=10 p50=686 ms / p95=725 ms；churn 组 n=16 中位 675.5 ms；arm64/真机 UNVERIFIED | `step04-warmup-cost.md` |
| step05 | 五 隔离套件与备份抽验 | 隔离面 + 备份门（不回退） | **红**：Isolation 13 例 10 红（全部同因 = 本批 T1 compile 接线缺陷，设备首曝）；Race 2/2 绿；备份 34/34 绿（不回退）；core:data 引擎套件 26 例 9 红（同因归因） | `step05-isolation-suite-backup.md` |

## 4. 关键结论（便于记录员转录）

1. **P-08 出数（首个设备证据）**：合并清单 6 个 Provider 全部 default 进程、无
   `android:process=":visual_js"`（aapt2 实测）；`dumpsys package providers` 与
   `dumpsys activity providers` grep `visual_js` 均 **0 命中**；16 个 worker 进程日志面
   sqlite=0 / room=0 / startup=0，主进程对照面有 Room 打开 + 189 条 StrictMode——Application
   短路（`SmartMistakeBookApplication.kt:287-304`）结构性可见。**R2 G-1 ① 的字面命令
   `pidof com.tingyun.smartmistakebook:visual_js` 在 API 34 对存活 worker 也返回空**
   （实测 4 种 pidof 变体全空；可用等价命令 = `ps -A -o PID,NAME | grep visual_js` +
   AMS `Start proc` 行），登记为清单命令修正项。
2. **A-11 观测级成立**：worker 实测 `u:r:isolated_app:s0:c512,c768`、Uid 99034（应用 UID
   10xxx）、补充组仅 `1065 3009`（无 3001–3005 网络组）；filesDir 面 = 目录 `0700 u0_a214` +
   worker 隔离 UID → POSIX 拒、MCS 类别集**不支配**（worker 域 `{c512,c768}` ⊉ 文件
   `{c214,c256,c512,c768}`，`l1 dom l2` 不成立）→ SELinux 拒（原"不交"措辞与实测类别集
   矛盾，更正见 step02 §9）。进程内 `Socket`/`filesDir 写`
   负向用例未执行（无进程内 runner；不加产品 seam），按工单"负向用例**或**观测"以观测落盘。
3. **R-04 阻塞（三重实测）**：release 变体的 `VisualJsIsolationInstrumentedTest` 往返在
   本仓现状下不可能——① R8 混淆把 AIDL/协议/引擎类型全部改名（dexdump 实测仅 12 个
   manifest 组件类保留名），签名匹配后的 instrument 在 app 进程首触即
   `NoClassDefFoundError: Lkotlin/jvm/internal/Intrinsics` 崩溃；② instrument 签名门
   （debug 签 test 包 vs 本地 key 签 release 主包）；③ 非 exported 服务 shell 拒启（uid 2000）、
   root 通道 BAL 拒启（uid 0）。可交付半边已做：release APK `libquickjs.so` 双 ABI 在包 ✓。
   解除路径（keep 规则 / 可测 release 变体 / 换判据口径）需主会话裁定，本轨不擅自改构建。
   **且 compile 接线缺陷修复前，任何变体都无 `Rendered` 往返可跑**（见 5）。
4. **warm-up 装载成本出数（KD-76 缺口闭合的实测半边）**：控制组（沉降态、单用例×10 次、
   每次 1 个新 worker）p50=686 ms / p95=725 ms / max 738 ms；套件 churn 组 n=16 中位
   675.5 ms、churn 尾部 1259–1746 ms。口径 = AMS `Start proc` → `warm-up finished`
   （logcat threadtime 毫秒）。x86_64/API 34 模拟器；arm64/真机/低内存 UNVERIFIED。
5. **隔离套件红 = 本批 T1 缺陷（设备首曝，非环境）**：`QuickJsVisualExecutionEngine.kt:751`
   把 scene/metrics/expect 的 JSON **文本**未加引号直插 JS 源（`__visualCompile($sceneJson,…)`），
   编译器收到的实参是 JS 对象值而非契约要求的三个 JSON 文本（`visual-compiler.js:1079-1081`
   的 `typeof !== 'string'` 门）→ `SCENE_FIELD_TYPE: __visualCompile expects three JSON text
   arguments` → 全部折 INVALID_SCENE。Isolation 13 例 10 红（3 绿 = 两个注入夹具 + BUSY 负向，
   均不达 compile）；core:data 引擎套件 26 例 9 红（同因，首断言 `:626 assertTrue(first is
   Rendered)`）；Race 2/2 与备份 34/34 不受影响。**16/16 worker warm-up 全部 ready、
   9 类崩溃签名全 0**——缺陷只在 compile 阶段接线，不在引擎装载/服务/隔离面。
   `workerIsIsolated…` 用例的 `workerUid() != myUid` 断言在失败前已实际执行并通过（失败点
   `:134` 的 Rendered 断言）。修复归批次修复步（主会话路由）；本轨零源码改动。

## 5. 缺口与未落盘

1. 隔离套件 10 红的修复与复跑（归批次修复步 + 修复后设备轨复验；本轨只出红与归因）。
2. R-04 release 往返阻塞（三重实证 + 修复选项，主会话裁定后才可再试）。
3. A-11 进程内 Socket/filesDir 写负向用例（需要隔离进程内 runner 或产品 seam——都属构建/
   产品决策）。
4. arm64/真机/低内存面的 warm-up 与隔离证据（沿 R-01/R-02/KD-76 缺口，本机不可采证）。
5. 生产 release 签名 UNVERIFIED（一次性本地 key，同批次 26 口径）。
6. `queryContentProviders(processName=":visual_js")` 的字面进程内调用未执行（无进程内
   runner）；以 PMS provider 表 dump（`dumpsys package providers`，0 命中）作为同事实的
   系统级证据，见 step01 §3。

## 6. 脱敏与去噪

```bash
E=docs/research/evidence/2026-10-27-batch-27
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' "$E" || echo "0 hits: secrets"
grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken)[[:space:]]*[:=][[:space:]]*[^ <]' "$E" || echo "0 hits: credential assignments"
grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$E" || echo "0 hits: emails"
grep -RInE 'C:.Users.[^ ]+|/home/[^/ ]+|/Users/[^/ ]+' "$E" || echo "0 hits: home paths"
find "$E" -type f -name '*.md' | wc -l
find "$E" -type f -name '*.md' -printf '%s\n' | awk '{s+=$1} END {print s " B total"}'
du -sk "$E"
```

结果见本文件末尾"扫描实跑记录"。一次性 keystore 口令全文一律写作 `<local-smoke-b27>`；
keystore 本体在 `build/`（不入仓）。物理设备 serial、真实学习者数据、截图/二进制：均无。

**2026-10-28 落盘扫描实跑结果**（命令即上块，可复现；`:NN` 为设备轨首测值）：secrets 0 /
credential assignments 0 / emails 0 / home paths **1**（=扫描命令行自身的正则文本，非真实
路径，逐条确认为自匹配；首测位于 `:107`，落盘员补记录回链行后现为 `:111`）；`<local-smoke-b27>`
占位外无明文口令（追加 0 命中核：`grep local-smoke-b27 | grep -v
"<local-smoke-b27>"` → 0）。体量：**7 个 `.md`**（单文件最大
`step05-isolation-suite-backup.md` 11,222 B / 176 行；R4 上限 64 KiB/400 行、单批
512 KiB/16 个均未触）/ 目录 `du -sk` = 80 KiB；全 `docs/research/evidence/` < 400 KiB
< 8 MiB 预算。**落盘员复核重测（2026-10-28，含 step02 §9 更正与索引记录回链行之后）**：
合计 **65,830 B**（设备轨首测记 62,826 B，为末次编辑前读数；以本次 `find -printf %s`
合计为准）。

**记录步三测（2026-10-28，批次记录员按本节原命令复跑）**：合计 **66,120 B**、7 个 `.md`
（较落盘员复核重测 +290 B——增量为本索引回链与 step 更正行的追加内容，非新文件；单文件
最大仍 `step05` 11,222 B，R4 上限未触）。漂移链 62,826 → 65,830 → 66,120 全部登记，
**以本次读数为当前现值**（体量为记账面，判定不受影响）。

## 7. 复跑入口（最小）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# 隔离套件（13 例；当前预期 10 红同因 compile 接线，修复后应全绿）
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# Race
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsHandshakeRaceInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# 备份
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
# core:data 引擎套件（归因复跑）
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
# 存活 worker 观测（ps 是 API 34 上唯一可靠进程名查询；pidof 对隔离进程恒空，见 step01 §3.4）
$A -s emulator-5554 shell "ps -A -o PID,NAME | grep visual_js"
```

构建命令见 `step00` §3；warm-up 采样复跑见 `step04` §2。
