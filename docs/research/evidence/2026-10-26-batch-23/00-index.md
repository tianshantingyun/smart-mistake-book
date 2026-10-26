# 批次 23 设备证据索引（2026-10-26）

- 记录：`docs/research/2026-10-26-batch-23-record.md`（由记录员/主会话维护）
- 计划：`.zcode/plans/plan-2026-10-26-batch-23-device-gates-lockfiles.md`
- 工作树 HEAD：`f0a145f850931887385c4bf2e1f798a0e8f2e391`
- 证据目录：`docs/research/evidence/2026-10-26-batch-23/`
- 本目录体积：15 个 `.md` / 124 KiB（`du -sk` = 124；内容 95,850 B / 1,745 行；§3.2 上限 512 KiB / 16 个）
- 规范版本：`docs/research/2026-10-26-device-evidence-convention.md` v1
- 执行者：设备验证轨（subagent）；只跑 Gradle `assemble*` 与裸 `am instrument`，未跑 `connectedAndroidTest`
- 落盘复核：证据落盘员（2026-10-26T16:56–17:06+08:00）重算 6 个产物 sha256/字节数、重扫 12 类签名（E-08）、用官方 LLVM `llvm-readobj` 复核 ELF（E-09）、在线复核两台设备指纹、重跑体量与脱敏扫描；未跑 Gradle、未改产品源

## 1. 环境

见 `step00-environment.md`。设备：

| serial | AVD | API | ABI | PAGE_SIZE | 用途 |
|---|---|---|---|---|---|
| emulator-5554 | test_device | 34 | x86_64 | 4096 | 隔离进程 / QuickJS / R8 / 备份迁移 / KD-67 |
| emulator-5556 | ps16k_x86_64 | 36 | x86_64 | **16384** | 16KB 门 |

## 2. 产物身份

| 产物 | 本机路径 | 字节 | sha256 | versionName |
|---|---|---|---|---|
| app debug APK | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` | 189,384,478 | `e5ec3a89ec93ca1289f232082371e04a37c77a1c78c5c4c8ac0debc5720bb441` | 0.2.0-offline |
| app debug androidTest APK | `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` | 1,568,217 | `fa696029ad7ae64ea5a980e2301c402f9eadf33c45ad9fa27e8e08f9245c0bb2` | — |
| app internal（R8）APK | `app/build/outputs/apk/strictOffline/internal/app-strictOffline-internal.apk` | 169,301,163 | `da8df2f8a80f0b18a10a8773232a29150071ae6930a349c94ae9cb58681f0e5b` | 0.2.0-offline |
| core:data androidTest APK | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | 197,668,020 | `5ef6ff9b46165517c0196f6b2926e8a07b64cf1fcfa8e62c5c82eb6901e72e75` | — |
| core:database androidTest APK | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` | 196,721,621 | `feb1b3601939487297b37af2c95a480b8b00995ba74d7c0da859f073aaa6df4e` | — |
| internal mapping.txt（不提交本体） | `app/build/outputs/mapping/strictOfflineInternal/mapping.txt` | 87,899,041 | `0ee9ed57ff66fec27ff79673cc56732aa8cce53ef2b3471d38cb0961cd98c3f4` | — |

设备侧复核：`emulator-5554` 上安装的 `base.apk` sha256 = `da8df2f8…0e5b`，与 internal APK 一致（E-04 §3）。

本轮复核（2026-10-26T16:56+08:00）：上表 6 个产物的字节数（`stat -c '%s'`）与 sha256（`sha256sum`）逐项重算，**全部一致**：debug `e5ec…bb441`、internal `da8d…f0e5b`、data test `5ef6…2e75`、database test `feb1…df4e`、mapping `0ee9…c3f4`。

## 3. 步骤与结论

| E-ID | 步骤 | 门/KD | 设备 | 判定 | 证据（本批目录内相对路径:行） |
|---|---|---|---|---|---|
| E-00 | 环境 / 设备 / 产物身份 | — | 两台 | PASS（信息性） | `step00-environment.md:113` |
| E-01 | VisualJs 隔离复跑（首轮） | 设备功能门 / KD-66 | emulator-5554 | FAIL（2/5） | `step01-visualjs-isolation.md:115` |
| E-01b | 同上复跑（supersedes E-01） | 设备功能门 / KD-66 | emulator-5554 | FAIL（1/5） | `step01b-visualjs-isolation-rerun.md:94` |
| E-01c | client abandon 单方法复现 | 设备功能门 / KD-66 旁证 | emulator-5554 | FAIL（1/1） | `step01c-abandon-confirmed.md:107` |
| E-01d | 冷启动后立即复跑（环境控制） | 设备功能门 / KD-66 | emulator-5554 | FAIL（5/5，加载态；不作产品判定） | `step01d-visualjs-isolation-coldboot.md:81` |
| E-01e | 冷启动沉降后复跑（产品证据） | 设备功能门 / KD-66 | emulator-5554 | FAIL（1/5，与 E-01b 一致） | `step01e-visualjs-isolation-postboot-settled.md:85` |
| E-02 | QuickJS 全量（KD-70 P1–P5） | KD-70 | emulator-5554 | FAIL（19/20，P2 红） | `step02-quickjs-oom.md:112` |
| E-03 | 16KB 页 + quickjs/LiteRt 装载 | 16KB / KD-74 | emulator-5556 | PASS（运行时）；官方 ELF 工具项见 E-09 | `step03-page-size-16k.md:113` |
| E-04 | R8 internal 冷启动×2 + Worker + 中文 OCR | KD-69 | emulator-5554 | PASS | `step04-r8-smoke.md:142` |
| E-05 | 备份/恢复核心类 | 备份门 | emulator-5554 | PASS（34/34） | `step05-backup-restore.md:70` |
| E-06 | 迁移：演练 + 全矩阵 + kill-stage | 迁移门 | emulator-5554 | PASS（15/15 + 5/6 + 设计崩溃） | `step06-migration.md:153` |
| E-07 | KD-67 生产落库 CAS | KD-67 | emulator-5554 | PASS（5/5） | `step07-kd67-cas.md:82` |
| E-08 | 12 类崩溃签名全量重扫（17 个 logcat） | KD-74 C1② | 5554 / 5556 | PASS（17/17；2 条非应用命中） | `step08-signature-rescan.md:130` |
| E-09 | 16KB ELF 官方 LLVM 工具复核 | 16KB / KD-74 C1③ | 主机只读 | PASS（14/14 × 2 APK） | `step09-elf-llvm-readobj.md:119` |

### 3.1 关键结论（便于记录员转录）

- **T1（KD-66）**：dup 修复成立——隔离 UID 成立、AIDL 往返 `RESULT_OK`、过期 generation no-op、watchdog 在 1500 ms 硬期限 SIGKILL 且新进程/新 generation 无 BUSY、死亡后 client 重建一次成功。**但 5 例仍 1 红**：`abandon()` 在 `unlinkDeath` 之后才等 `link.death`（`VisualJsClientEngine.kt:570-571`），服务已 SIGKILL 而客户端 1 s 内收不到 death → `unavailable=true`。单方法稳定复现（E-01c），并在 4 次独立运行中稳定出现（E-01b/E-01c/E-01d/E-01e）。冷启动对照：重启后立即跑 5/5 红（隔离进程 attach 4.6–7.5 s > 5 s 客户端超时、JS 1.5 s 硬期限被加载态触发，E-01d），沉降约 2 min 后回到 1/5 红（E-01e）——加载态超时与结构性失败已分离。**设备功能门仍红，JS 继续不广告。**
- **KD-70**：P1 在设备成立（`MEMORY_LIMIT` + `16777216` + `out of memory`）；P2 实际 `INVALID_PROGRAM: [UNATTRIBUTED] (raw=null, mallocSize=177216, mallocLimit=16777216)`，堆证据不足，**KD-70 判据仍未达成**。
- **KD-69**：`strictOffline internal`（minify=true，设备 APK sha 与产物一致）两次冷启动 ok/COLD、零崩溃签名；`BatchImportDriverWorker` 与 `OrphanAssetGcWorker` 真实执行 SUCCESS；一次中文 OCR 真实完成（Dynamite 本地中文模块、Hani 模型、`OCR process succeeded`）并把 `LOCAL_OCR` revision 写入 Room。**Reopen condition 满足。**
- **16KB**：真实 16384 内核上 3/3 通过，`PAGE_SIZE=16384` 打印；`zipalign -c -P 16 -v 4` exit 0；生产 APK 的 64 位 `.so` 按官方 ELF 判据（含 RELRO 独占 LOAD 豁免）全部 PASS。**官方工具补证（E-09）**：`llvm-readobj` LLVM 22.1.8，debug/internal 各 14 个 64 位 `.so` 全 PASS（5 个 RELRO 整倍数、9 个按官方豁免），与 E-03 §5 的 Python 结果逐值一致；`llvm-readelf` 别名二进制仍缺，等价性待主会话裁定。arm64 运行时未测。
- **签名全量（E-08）**：17/17 logcat、12×17=204 格；仅 `step01d` 2 条（GMS `ANR in` PID 2538、Primes `UnsatisfiedLinkError` PID 3289），均发生在测试开始前且非被测进程；旧 `signature-matrix.txt` 只覆盖 13 个文件，未覆盖 01d/01e/06d/06e，已由 E-08 补齐。
- **备份/迁移**：备份 34/34；迁移演练（opt-in）5/6（1 个 kill-stage 按参数跳过）；全迁移矩阵 15/15；kill-stage 设计内 `Process crashed.`。
- **KD-67**：新仪器化用例 5/5（CAS 单列、真实行数、缺行不插入、状态收紧、并发唯一赢家）。

## 4. KD-74 覆盖（四类 + T1 + 产物身份）

| KD-74 要求（`docs/known-defects.md:2709` 条目，`:2715` Fix direction） | E-ID | 状态 | 备注 |
|---|---|---|---|
| 每条命令的 `am instrument` summary / `INSTRUMENTATION_CODE` | E-01…E-09 | 有 | 每个 step §3/§4 均含 summary 与 `INSTRUMENTATION_CODE` |
| logcat 12 类签名全量 grep（含 0 命中） | E-08（E-01…E-07 各自记录） | **有（17/17）** | 旧 `signature-matrix.txt` 只覆盖 13 个文件；E-08 补齐 01d/01e/06d/06e，2 条命中均为测试前 GMS/Primes 系统组件 |
| 16KB `getprop`/`getconf` + 产物 `readelf -l` 页对齐 | E-03、E-09 | **有（官方 LLVM 工具）** | `getconf`=16384、`getprop` 原值、zipalign exit 0；`llvm-readobj` LLVM 22.1.8 对 debug/internal 各 14 个 64 位 `.so` 全 PASS；`llvm-readelf` 别名二进制缺失，等价性待主会话一句裁定 |
| 备份/迁移用例输出 | E-05、E-06 | 有 | 34/34；15/15；演练 5/6 + 设计崩溃 |
| T1 修复后设备复跑 | E-01、E-01b、E-01c、E-01d、E-01e | 有 | dup 修复路径绿；剩余 abandon 红已单方法复现；冷启动/沉降对照齐全 |
| APK/mapping sha256 | E-00（E-09 复核） | 有 | 五个 APK + mapping；本轮 6 个产物哈希逐项重算一致；mapping 本体不提交 |

**KD-74 结论（证据落盘轨，2026-10-26）**：C1/C2/C3/C5 的证据已落盘（C1③ 的"`llvm-readobj` 等价 `llvm-readelf`"待主会话一句裁定）。C1③ 由 E-09 的官方 LLVM `llvm-readobj` 补证；`llvm-readelf` 别名二进制仍缺，按 R4 §6.4 其等价性需主会话裁定。**C4/C6 目前不可能满足**：`docs/research/2026-10-26-batch-23-record.md` 尚未生成，无法在记录 §4 回链，也无法在记录写 "KD-74 closed"。**结论：KD-74 保持 open，本文件与 KD 条目都不写 closed**；待记录落地、C4/C6 勾满后再由主会话关闭。设备红绿与本条无关：T1 仍 1/5 红（abandon 结构性缺陷）、KD-70 P2 仍红（E-01b/E-01c、E-02）。

## 5. 缺口与未落盘

1. **官方 ELF 判据**：`llvm-readelf` 别名与 NDK 均不存在（E-00 §1）；E-09 用同发行版 LLVM 22.1.8 的 `llvm-readobj --program-headers` 重做，debug/internal 各 14/14 PASS。→ 只剩"`llvm-readobj` 是否等价 `llvm-readelf`"的主会话一句裁定；不等价则需补装别名或 NDK。
2. **arm64 16KB 运行时**：ps16k AVD 是 x86_64；arm64 设备的 16KB 装载/RELRO 未执行。→ 需要 arm64 16KB AVD/真机。
3. **T1 abandon 缺陷**：本轨只验证，不修；建议主会话登记新 KD（`VisualJsClientEngine.abandon()` 在 `unlinkDeath` 后等待 death → 宿主阶段超时路径永久 fail-closed），并决定是改代码还是改契约/测试 seam。
4. **KD-70 P2**：需要能稳定推满 16 MiB 堆的夹具，或按 Fix direction ② 先裁定等价判据；本批不得放宽 90%。
5. **KD-69 静态穷举**：只核了 Room + 三个 ML Kit registrar；"无第三个反射可达的被裁无参构造器"未穷举。`ExportPdfWorker` 未单独触发（触发的是两个启动 Worker）。
6. **T1 门的加载态敏感性**：冷启动后立即跑 5/5 红（attach 4.6–7.5 s > 5 s、JS 1.5 s 硬期限被加载态触发），沉降 2 min 后回到 1/5 红（E-01d/E-01e）。门本身未做"加载态下放宽/重试"的裁定；本轨只如实记录两种状态。
7. **测试 APK 的 32 位 ML Kit `.so`**：`armeabi-v7a`/`x86` 的 `libmlkit_google_ocr_pipeline.so` `LOAD align=0x1000`；生产 App 已用 `abiFilters` 裁掉，若放开 ABI 会重新引入。
8. **构建日志编码异常**：Windows 非 ASCII 用户名使 `aidl.exe` 生成的 Java 头注释含 GBK 字节，javac 打印 8 条不可映射字符错误但构建仍成功；未改构建文件，建议主会话决定是否在 `app/build.gradle.kts` 固定 javac 编码/清理 AIDL 注释。
9. **他线设备**：`emulator-5562`、`emulator-5584` 本轨未使用。
10. **记录文件缺失（KD-74 C4/C6 的硬阻塞）**：`docs/research/2026-10-26-batch-23-record.md` 不存在。R4 §6.2 C4 要求"record 的每个设备数字都有 E-ID + path:line"、C6 要求"记录写明 KD-74 closed 并链接本索引"，两项都必须有记录才能勾。→ 由记录员/主会话在记录落地后补；在此之前 KD-74 不得关闭。
11. **旧矩阵覆盖缺 4 个文件**：`build/device-track-batch23/signature-matrix.txt` 只有 13 个 logcat（缺 01d/01e/06d/06e）。E-08 已补齐为 17/17；旧矩阵本身是 `build/` 材料，不复改。

## 6. 脱敏与去噪

```bash
E=docs/research/evidence/2026-10-26-batch-23
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' "$E" || echo "0 hits: secrets"
grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken)[[:space:]]*[:=][[:space:]]*[^ <]' "$E" || echo "0 hits: credential assignments"
grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$E" || echo "0 hits: emails"
grep -RInE 'C:.Users.[^ ]+|/home/[^/ ]+|/Users/[^/ ]+' "$E" || echo "0 hits: home paths"
```

结果：

- secrets：0 命中
- credential assignments：0 命中
- emails：0 命中
- home paths：2 命中，均为扫描命令自身的正则文本（`00-index.md:99`、`step00-environment.md:106`），不含真实用户路径；人工复核确认无主目录泄露。
- 本轮重跑时间：2026-10-26T17:02+08:00（E-08/E-09 写入后），结果同上；15 个 `.md` 均 `git check-ignore` 无输出（可跟踪）。
- OCR 夹具为合成内容；未使用真实学习者数据。
- 二进制/截图：无。

## 7. 回链检查

- [x] 每个设备数字都有 `E-ID + path:line`（§3、§3.1）
- [x] `docs/known-defects.md` 的 KD-74 Progress 引用本索引（2026-10-26 由证据落盘轨写入；**状态保持 open**，不写 closed）
- [x] 每个 E-file 有 `sanitized` 字段；扫描结果见 §6
- [x] 没有把 `build/` 路径当作关闭证据（只出现在 `raw-log` 字段）
- [x] 文件均在 `docs/research/evidence/` 下，`git check-ignore -v` 无输出（15/15 可跟踪）
- [ ] 记录 §4 的设备行引用本索引（**阻塞**：`docs/research/2026-10-26-batch-23-record.md` 尚未生成，C4 不能勾）

## 8. 复跑入口（最小）

```bash
# T1
MSYS_NO_PATHCONV=1 C:/Android/Sdk/platform-tools/adb.exe -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# KD-70
MSYS_NO_PATHCONV=1 C:/Android/Sdk/platform-tools/adb.exe -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
# 16KB
MSYS_NO_PATHCONV=1 C:/Android/Sdk/platform-tools/adb.exe -s emulator-5556 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.NativeLibraryPageSizeInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
```
