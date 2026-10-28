# 批次 28 设备证据索引（2026-10-28 开工，设备执行 2026-10-28 07:20–07:52 +08:00）

- 记录：`docs/research/2026-10-28-batch-28-record.md`（**后写**：批次收口时由记录步创建；
  本索引 §3/§4 与各 step 的 E-ID/path:line 即其设备证据回链源）
- 计划：`.zcode/plans/plan-2026-10-28-batch-28-scenev1-device-painter.md`（先完整读）
- 工作树 HEAD：`8116ad98`（批次 27 收口提交；全程未变动）
- 命名说明：本目录为批次 28 新目录，按 R4 §2.2 用 `stepNN-`；工单"stepNNb 追加落盘"的
  语义（追加不覆盖 + 对批次 27 同面判定标 supersedes）由下表 supersedes 列与本节登记落实
- 证据目录：`docs/research/evidence/2026-10-28-batch-28/`
- 规范版本：`docs/research/2026-10-26-device-evidence-convention.md` v1（R4）
- 执行者：设备验证轨（subagent，批次 28）
- 本轨仓内源码更正（先红后绿测试面，产品源零改动）：`core/data/src/androidTest/.../visual/`
  下两文件（`QuickJsSceneCompilerInstrumentedTest.kt` 断言 1.0→0.5 四源归因；
  `VisualDrawOpsPainterInstrumentedTest.kt` 夹具 bitmapPxPerDu 解耦），整文件纳入登记于
  E-02 §4/§5、E-03 §4/§5
- 时间口径：`executed-at` 用主机时钟（+08:00）；设备时钟 UTC（=主机 −8h），logcat 时间戳
  原文引用 UTC 值

## 1. 环境

| serial | AVD | API | ABI | PAGE_SIZE | 用途 |
|---|---|---|---|---|---|
| emulator-5554 | test_device | 34 | x86_64 | 4096 | 本批全部五步（SceneV1 四套件/painter/备份/R-04 复试） |
| emulator-5556 | ps16k_x86_64 | 36 | x86_64 | 16384 | **只读核**（四值核对，未安装未执行） |

- emulator-5584 属他线全程未触碰；5554 开工沉降态（up ~24 h、load 0.11）。
- 全程零 `connectedAndroidTest`、零 `adb root`（区别于批次 27 的 R-04 步）、零 git 写操作；
  Gradle 仅 `assemble*` 五个任务（debug 三件套 + 两轮 core:data androidTest 重建 + release）。
- 设备恢复：R-04 复试舞步后 debug 三件套（v3）重装，设备侧 sha256 与产物逐字节一致，
  无存活 worker（`step05` §6）。

## 2. 产物身份（sha256 前缀…完整值见 step00 §3）

| 产物 | sha256 | 字节 | 备注 |
|---|---|---|---|
| app debug APK | `9ad2cd739ce12ed4…` | 189,406,722 | 含 F1/F2 修复 |
| app debug androidTest APK | `cde7e27b9a6c0ab9…` | 1,585,488 | **与批次 27 逐字节一致** |
| core:data androidTest APK v1 | `5421a0355f2c9105…` | 197,648,091 | 编译器红轮用 |
| core:data androidTest APK v2 | `855a25a8ef9fb4c0…` | 197,648,091 | 编译器断言更正后（E-02） |
| core:data androidTest APK v3 | `a24d2c854d3250f1…` | 197,648,091 | painter 夹具更正后；**收口在装版本** |
| app release APK（一次性本地 key） | `e90ce7f3348a8f41…` | 155,732,659 | `CN=Batch28 Local Smoke` 非生产证书 |

构建：debug 三件套 exit=0（29 s）；core:data androidTest v2/v3 exit=0；release exit=0（2m16s，
口令一律 `<local-smoke-b28>`，keystore 在 `build/` 不入仓，`.kspass` 已删除）。

## 3. 步骤与结论

| step | 工单步 | 门/清单行 | 判定 | supersedes | 证据 |
|---|---|---|---|---|---|
| step00 | 环境/构建/产物身份 | — | PASS（信息性） | — | `step00-environment.md` |
| step01 | 一·app 侧（隔离 13 + Race 2）+ 二（判据 1/2/5） | SceneV1 设备门 / KD-66 复验 / KD-76 ⑤ | **PASS**：`OK (13 tests)`（批次 27 10 红→全绿）+ `OK (2 tests)`；签名 18 项全 0；17/17 spawn→ready；17/17 kill 归因零意外杀 | `2026-10-27-batch-27/step05…` 的隔离 10 红判定 | `step01-scenev1-app-suites.md:10`（summary `:51-:52`、输出 `:64`、配对 `:75`） |
| step02 | 一·core:data 侧（编译器 6 + 引擎 26）+ 二（判据 3/4） | SceneV1 设备门（编译器套件设备首跑） | **PASS（含先红后绿）**：编译器首跑 5/6（:144 红=测试期望缺陷，四源归因）→更正→`OK (6 tests)`；引擎 `OK (26 tests)`（9 红全转绿）；协作期门未触 | `2026-10-27-batch-27/step05…` 的引擎 9 红判定 | `step02-scenev1-coredata-suites.md:10`（红 `:49`/`:60`、绿 `:50-:51`、归因 §4 `:64-:70`） |
| step03 | 三 painter 设备冒烟（17 例） | R1 §4 设备判据（T2 移交） | **PASS（含先红后绿）**：首跑 16/17（红=夹具建位图顺序缺陷，painter PAINTER_BOUNDS 门未达）→更正→`OK (17 tests)`；e2e `aLegalSceneGoesFromCompileToColoredBitmap` 与确定性 ×2 绿 | —（painter 首次设备证据） | `step03-painter-smoke.md:10`（红 `:55-:56`、绿 `:45`、归因 §4） |
| step04 | 五 备份抽验（不回退） | 备份门 | **PASS**：`OK (34 tests)`，与批次 27 一致 | —（复跑确认） | `step04-backup-rerun.md:10`（`:43`、输出 `:51`） |
| step05 | 四 R8 下隔离往返（R-04 复试） | R-04（清单 :232；R-02 行义=arm64 不可采证） | **阻塞维持**：签名门复现 + 重签后 R8 崩溃复现（`NoClassDefFoundError: Intrinsics/Trace`，测试体前）；BAL 通道未复试（沿批次 27 证据）；libquickjs 双 ABI ✓ + 12 类名审计 ✓；**"接线修复后应可过"被实测否证**（F1 在包内，阻塞与之正交） | `2026-10-27-batch-27/step03-r04-release-roundtrip.md` 的阻塞判定（当前树复确认） | `step05-r8-roundtrip.md:10-11`（签名门 `:76`、崩溃 `:85-:89`、前提对账 §4） |

T1 判据表核销（工单二）：1 隔离 13 ✓（E-01）；2 Race 2 ✓（E-01）；3 编译器 6 ✓（E-02，含
协作期门）；4 引擎 26 ✓（E-02）；5 签名 0 + worker ready + kill 归因 ✓（E-01 §4，本批
17/17 而非 T1 预估 16——实际计数）；6 备份 34/34 ✓（E-04）；7 Node 台 89/89 EXIT=0 ✓
（E-01 §2 前置，设备轨实跑）。

## 4. KD-74 覆盖（四类 + T1 + 产物身份）

| KD-74 要求 | E-ID | 状态 | 备注 |
|---|---|---|---|
| 每条命令的 `am instrument` summary / `INSTRUMENTATION_CODE` | E-01/E-02/E-03/E-04/E-05 | 有 | 7 次 instrument 全录（含 R-04 两次拒绝形态） |
| logcat 崩溃签名全量 grep（含 0 命中） | E-01 §3、E-02 §3、E-03 §3、E-04 §3 | 有 | 9 签名 ×6 份 logcat（isolation/race/compiler/engine/painter/backup）全 0；R-04 尝试 2 的 FATAL 为该步证据本体（已归因） |
| 16KB getprop/getconf + ELF | E-00 §2 | 有（getconf）/UNVERIFIED（ELF） | 5556 只读核 16384 ✓；llvm-readelf 工具缺口沿 R4 §8.3（本批无 16KB 安装面） |
| 备份/迁移用例输出 | E-04 | 有 | 34/34，无跳过 |
| T1（VisualJs 隔离）修复后设备复跑 | E-01 | 有 | 13/13 全绿（本批核心交付） |
| APK/mapping sha256 | E-00 §3 | 有 | 6 产物身份 + dex 套件符号防旧包跑新门；mapping 未涉（无 R8 keep 断言） |

## 5. 缺口与未落盘

1. **R-04 解除路径**（keep 规则/可测 release 变体/判据改口径）：维持归主会话（E-05 §4）；
   解除前 release 往返不可执行。
2. **BAL 通道未复试**（E-05 §3.4）：避免 adb root 设备态变更；批次 27 E-03 §3.3 证据沿用。
3. arm64/真机/低内存、生产 release 签名、真实 Provider：沿既有缺口，本机不可采证。
4. painter 的 stroke 颜色/dash 奇数缺口（T2 移交 ①）与 measure/replay flags 指纹（③）：
   本步未落 known-defects/契约（T3 归属），设备证据面已由 E-03 17 例覆盖。
5. warm-up 装载成本 p50/p95 复测：非本批判据（批次 27 E-04 已出数）；17/17 ready 配对已
   足够 T1 判据 5。

## 6. 脱敏与去噪

```bash
E=docs/research/evidence/2026-10-28-batch-28
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' "$E" || echo "0 hits: secrets"
grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken)[[:space:]]*[:=][[:space:]]*[^ <]' "$E" || echo "0 hits: credential assignments"
grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$E" || echo "0 hits: emails"
grep -RInE 'C:.Users.[^ ]+|/home/[^/ ]+|/Users/[^/ ]+' "$E" || echo "0 hits: home paths"
find "$E" -type f -name '*.md' | wc -l
find "$E" -type f -name '*.md' -printf '%s\n' | awk '{s+=$1} END {print s " B total"}'
du -sk "$E"
```

结果见本文件末尾"扫描实跑记录"。一次性 keystore 口令全文一律写作 `<local-smoke-b28>`；
keystore 与已删除的 `.kspass` 均在 `build/`（不入仓）。物理设备 serial、真实学习者数据、
截图/二进制：均无。

**落盘扫描实跑结果（2026-10-28 设备轨，命令即上块）**：secrets 0 / credential
assignments 0 / emails 0 / home paths **1**（= `00-index.md:91` 扫描命令行自身正则文本，
非真实路径，逐条确认为自匹配，与批次 27 同型）；追加核 `grep local-smoke-b28 | grep -v
"<local-smoke-b28>"` → 0（无明文口令）。体量：**7 个 `.md` / 52600 B / `du -sk` 65 KiB**
（初记 50,306 B 为初扫时点值；§8 清单判据结论补记后复测终值 52600 B，2026-10-28 证据落盘员）
（单文件最大 `step05` 9,803 B / 121 行；R4 上限 64 KiB/400 行、单批 512 KiB/16 个均未触；
全 `docs/research/evidence/` 仍 < 8 MiB 预算）。

## 7. 复跑入口（最小）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# 隔离套件（修复后判据 OK (13 tests)）
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# Race / 编译器 / 引擎 / painter / 备份：同型命令换 class（core:data 三套件用
# com.tingyun.smartmistakebook.core.data.test runner），全量见各 step §2
# 存活 worker 观测（pidof 对隔离进程恒空，用 ps）
$A -s emulator-5554 shell "ps -A -o PID,NAME | grep visual_js"
```

构建命令见 `step00` §3；R-04 复试舞步见 `step05` §2。

## 8. 清单行判据结论（P-01 / R-02 / R-04；供记录步与台账轨引用；行现状刷新归台账轨，本索引不代改清单行）

- **P-01（隔离 suite 6/6 设备绿=下限门，`2026-10-26-batch-24-4e2-admission-checklist.md:124`）：维持成立、无回退**。批次 24 六例证据不变；六法已核实全部含于现行 13 例（批次 24 E-file 六名 `evidence/2026-10-26-batch-24/step01-visualjs-isolation.md:32-:38` ↔ 本批 `VisualJsIsolationInstrumentedTest` fun 名逐一对应），本批 F1 修复树上 13/13 全绿（E-01，`step01-scenev1-app-suites.md:51`）；其现状列"仍不覆盖 hard+500 ms"已过时（清单附录 `:481` 自认）——`aHungWorkerReleasesTheCallerWithinHardPlus500ms` 在本批 13 例内转绿（`step01-scenev1-app-suites.md:93`）。
- **R-02（arm64 运行时，清单 `:230`）：UNVERIFIED 维持（本机 x86_64 宿主原理性不可采证）**。本批零 arm64 面、零新增证据；工单四"R8 下隔离往返"标注 R-02 属标签失配，按清单行义归 R-04（对账 `step05-r8-roundtrip.md:100-:104`）。
- **R-04（release R8 QuickJS JNI 面，清单 `:232`）：阻塞维持（v2 判据在含 F1 的 release 包上仍不可执行）**。签名门复现（`step05-r8-roundtrip.md:76`）+ 重签后 R8 类解析崩溃（`:85-:89`）两重新鲜复现；BAL 通道未复试（沿批次 27 E-03 §3.3）；可交付半边 PASS（libquickjs 双 ABI + 12 类名审计，`step05-r8-roundtrip.md:71`）；工单前提"接线修复后应可过"被实测否证。解除路径三选一维持归主会话。
