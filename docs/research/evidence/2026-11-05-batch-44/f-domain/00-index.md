# F 域证据索引（批次 44，2026-11-04/05 实跑）

> 本文件是导航与口径说明，**不是台账证据**；台账 F 行只引用同目录下的
> `*.device.json` / `*.cmd.log` / `TEST-*.xml` / 原始设备输出。F 域在模拟器上
> 实跑；真机/arm64 仍 BLOCKED，不用模拟器数字冒充真机结论。

## 1. 身份与源快照

| 项 | 值 |
|---|---|
| 记录时 HEAD | `542b9bbac49081aca808c616a5dc538965d1767e`（`git rev-parse HEAD`） |
| 工作树 | 558 条脏（共享树，本域**零 git 写**；热区修改来自其他轨） |
| 主测量设备 | `emulator-5566`（`sdk_gphone64_x86_64`，API 34，x86_64，PAGE_SIZE 4096，boot_id `5e1a5d59-…`） |
| 主 APK | `com.tingyun.smartmistakebook.localfirst` sha256 `b8503f04…`（`app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk`，构建 2026-11-04 22:56；HEAD 提交 23:08，共享树有未提交改动，故不声称与 HEAD 逐字节相同） |
| app.test APK | `aab6af01…`（构建 18:44） |
| export.test APK | `08ea2641…`（构建 08:45，与批次 43 同一份） |
| data.test APK | `c9943728…`（构建 18:37） |
| database.test APK | `32235e63…`（构建 18:37；跑在 emulator-5562） |
| 运行环境 | Windows/Git Bash；adb root；无 Gradle（除 `:core:database` 既有 APK 直接 instrument，未构建）；无源码/测试代码改动 |
| 负载 | 5566 启动段 loadavg 0.4–1.6；5562 DB 门段 1.0–8.6（该机为降级环境，见 §6） |
| 记录时间 | 2026-11-04 15:23–16:38（启动/导出/预览/进程死亡/后台/ANR）；2h soak 15:55 起，16:37 崩溃后 16:38 停止 |

**为什么用 5566 而不是 5562 做基线**：5562 的冷启 10–13s、首启知识库解析 118s、
StrictMode 报 `cleanupOrphans` 主线程 DiskReadViolation 6.7s（见 §6）；5566 冷启
3.0–3.9s，与批次 43 的 5584 基线同量级。5562 的降级产物保留在
`env-5562-degraded/`，不作为 F 基线。

## 2. 方法（每条台账行只描述该行断言，不重复整条命令）

### 2.1 冷启 / warm-up / 首帧

`f-startup.sh emulator-5566 7 5 <outdir>`：

- 每轮 `dumpsys gfxinfo pkg reset` → `am force-stop` → 2s → `am start -W`；
  7 冷启；随后 HOME → `am start -W` 5 热启。
- 解析 `TotalTime`/`LaunchState`；逐轮保存 `gfxinfo-coldN.txt`；
  另抓 `logcat -d -s ActivityTaskManager` 的 `Displayed` 行与 `dumpsys meminfo`。
- 统计：冷启 p50 3302ms / p95 3939ms / max 3939ms；热启 p50 232ms；
  前 2 轮均值 3470ms → 后 5 轮 3308.6ms（warm-up 收敛 161.4ms）。
- 首帧：7 轮 gfxinfo 均显示 janky（100% 或 80%），p50 300–2150ms。
- 目标对照：`PerformanceTargets.kt:18` 冷启 2000ms、`:27` 首屏 500ms 是
  **真机口径**；本域只登记模拟器基线，F-002/F-020 因此 UNVERIFIED，不把
  模拟器超线写成产品 FAIL。

### 2.2 导出 / 预览

`f-run-instr.sh <serial> <runner> <target> <class#method> <tag> <outdir>`：

- `am instrument -w -r -e class …` 跑已安装用例；同时设备侧 50ms 轮询
  `/proc/<pid>/smaps_rollup` 的 `Pss:`，取首样本/峰值/样本数；
- 落 `*.instr.txt`（原始 instrumentation 输出）、`*.pss.txt`、`*.cmd.log`
  （带 `EXIT=0`）、`*.device.json`（serial + apk_sha256 + 结果）。
- 轮次：
  - 60 题批量导出（`MistakePdfExporterInstrumentedTest#batchExport…`）3 轮：
    套件 Time 1.216/1.240/1.277s，`MistakeBatchPerf elapsedMs=516/553/538`；
  - 长文档 + 超页限负向（`#longDocumentPaginatesAndHardLimitFailsClosed`）2 轮：
    0.367/0.380s；
  - 产品路径单题 A4（`MistakeExportBackgroundInstrumentedTest#exportCompletes…`）
    3 轮：套件 7.97/7.77/8.12s；导出记录 `created→finished` 820/487/597ms；
  - PDF 预览（`MistakePdfDeliveryRegressionInstrumentedTest#previewPageZero…`）
    3 轮：0.359/0.332/0.378s；
  - D-04 视觉预览（`TutorVisualPreviewAssemblyInstrumentedTest#switchOn…`）2 轮：
    0.581/0.581s。
- 批量 5s 内联断言 3/3 通过；长文档 `PAGE_LIMIT_EXCEEDED` 负向 2/2 通过。

### 2.3 内存

- 冷启稳态：`dumpsys meminfo` `TOTAL PSS=156818 KB`（约 153.1 MiB）。
- 导出/预览：50ms PSS 采样峰值——批量导出 47.5–54.4MB、长文档 42.7–42.9MB、
  PDF 预览 27.8–44.1MB、D-04 65.8–66.3MB。
- 产品 UI 导出测试宿主峰值 314–317MB：含 Compose 测试宿主/夹具，**不能**写成
  “产品导出内存”；按导出记录窗口切片中位数 286–289MB，窗口前后无可分辨抬升。
- 200MB 目标没有定义进程/相位（批次 43 §3.1(4) 同判），F-047/F-048 因此
  UNVERIFIED，不拿宿主数字凑阈值。

### 2.4 2h soak

`f-soak.sh emulator-5566 7200 <outdir>`：`svc power stayon true`；
每轮 `monkey -p pkg --throttle 1000 --pct-syskeys 0 --pct-appswitch 0
--pct-anyevent 0 -v 300`，随后采样 pid/PSS/`dumpsys meminfo`/loadavg/
dropbox app 计数/crash buffer 行数；循环至 7200s。产物
`soak-samples.csv`、`soak-monkey-N.log`、`soak-stats.txt`。

**本轮结果（FAIL）**：第 39 轮（2504s）monkey 触发
`SchedulingSettingsScreen.kt:74` 的 Slider 回调，`SchedulingOptions.copy`
收到 0.7..0.97 之外的值，抛
`IllegalArgumentException: Desired retention must be within the supported 0.7..0.97 range`
（`StudyExperienceRepository.kt:737`），进程 25402 崩溃；dropbox app 计数
12→14、crash buffer 73 行、monkey rc=69，脚本重启进程后继续到 2723s，
因崩溃已使 2h 干净基线不成立，人工停止以冻结证据。证据
`soak-crash-logcat.txt`、`soak-crash-dropbox.txt`、`soak-samples.csv` 第 39 行。

### 2.5 ANR / 崩溃

- 本轮前 dropbox 中 app 条目 12 条，全部为 2026-11-03/04 历史崩溃；
  本轮窗口内 `logcat -b crash` 0 行，dropbox 计数仍 12。
- 用 `dumpsys dropbox --print` 与 `logcat -b crash -d` 做前后对照，不用
  “测试 OK” 代替崩溃签名检查。

### 2.6 进程死亡

- `StorageOperationLifecycleInstrumentedTest` 2/2：`SavedStateHandle(RUNNING=true)`
  恢复后 `busy=false`、提示“中断”；Activity recreate 只执行一次。
- 手工：5566 上 `am force-stop` → start（pid 23637）→ HOME → `am kill`
  （pid 空）→ start（pid 23758，TotalTime 3219ms），`uiautomator dump` 恢复
  首页（“下午好，同学”/“今日复习”/“开始今日复习”）；5584 复跑一次
  （pid 5468→5584，TotalTime 7711ms，该机 229MB 测试库导致慢启）。

### 2.7 后台限制 / 低电 / 低存 / 多任务

- `cmd deviceidle force-idle` 后冷启 3455ms；`settings put global low_power 1`
  + `cmd power set-mode 1` 后冷启 3342ms；`am set-standby-bucket pkg rare`
  后冷启 3247ms；HOME→Settings→APP_SWITCH→HOME→start 热启 474ms。
- 低存储**未模拟**（会破坏共享模拟器）；只记录 `/data` 36% used，F-084
  UNVERIFIED，升级路径见 §7。

## 3. 计数与 verdict

| 项 | 值 |
|---|---|
| 台账 F 行 | **90**（F-001…F-090，无缺号/重号） |
| verdict | **56 PASS / 22 UNVERIFIED / 2 FAIL / 10 BLOCKED**（F-055/F-057 因 2h soak 崩溃判 FAIL；其余域外阻塞见 runbook） |
| 设备轮次 | 启动 7+5、导出 3+2+3、预览 3+2、DB 门 6 例、进程死亡 2 例、后台 4 场景 |
| 新增/修改产品代码 | 0（本域只测量；未改热区、未改 `core/export` 产品代码） |
| Gradle | 0 次（F 域测量走已安装 APK + adb；DB 门用既有 database.test APK） |

## 4. 主题到证据的映射（台账行本身是 source of truth）

| 主题 | 行 | 主要证据 |
|---|---|---|
| 冷启动 | F-001…009 | `startup.device.json`、`startup.cmd.log`、`startup-cold.txt`、`startup-hot.txt` |
| warm-up | F-010…018 | `startup-stats.json`、`startup-cold.txt`、`logcat-displayed.txt` |
| 首帧 | F-019…027 | `gfxinfo.txt`、`gfxinfo-cold1..7.txt`、`logcat-displayed.txt` |
| 导出耗时 | F-028…036 | `export.cmd.log`、`export-batch{1,2,3}`、`export-long{1,2}`、`export-product{1,2,3}`、`TEST-export-*.xml` |
| 预览耗时 | F-037…045 | `preview.cmd.log`、`preview-pdf{1,2,3}`、`preview-visual{1,2}`、`TEST-preview-*.xml` |
| 内存峰值 | F-046…054 | `memory.cmd.log`、`meminfo-after-startup.txt`、`export-batch1.pss.txt`、`memory-export-window.json` |
| 长时 2h | F-055…063 | `soak-samples.csv`、`soak-monkey-N.log`、`soak-stats.txt`、`soak.device.json`、`soak-crash-logcat.txt`、`soak-crash-dropbox.txt` |
| ANR/崩溃 | F-064…072 | `dropbox-after.txt`、`logcat-crash.txt`、`anr-crash.cmd.log` |
| 进程死亡 | F-073…081 | `process-death-manual.log`、`process-death-ui.xml`、`process-death-repeat-5584.log`、`TEST-process-death-unit.xml` |
| 后台/低电/多任务 | F-082…090 | `background-restrictions.log`、`background-restrictions-2.log`、`background.cmd.log` |

## 5. UNVERIFIED（逐条理由与升级路径）

| 行 | 原因 | 升级路径 |
|---|---|---|
| F-002 | 2000ms 冷启目标是真机口径；模拟器 3.0–3.9s 不作产品判定 | arm64 真机跑 `StartupBenchmark`/`am start -W` |
| F-009/F-018/F-027 | 无同设备同构建历史基线 | 同机同 APK 连跑两批，保留两批 JSON |
| F-011/F-012 | 无 warm-up 数值目标/阈值 | 先由产品定义 warm-up 口径与目标 |
| F-020 | 500ms 首屏目标是真机口径；模拟器 7/7 轮 janky | 真机 Macrobenchmark `FrameTimingMetric` |
| F-038/F-039 | 无产品预览时延目标 | 产品定义预览 SLA 后复跑 |
| F-047/F-048 | 200MB 目标未定义进程/相位；导出宿主峰值不可归因 | 干净安装 + UI 手工导出 + 50ms PSS 切片 |
| F-054 | 无同设备历史 PSS 基线 | 同机同 APK 两批对照 |
| F-056/F-058/F-063 | 无 2h 数值阈值；只跑单窗 | 定义阈值并重复第二窗 |
| F-072 | 无历史 ANR/crash 基线 | 同机同 APK 两批 dropbox 对照 |
| F-074 | 无进程死亡恢复时延目标 | 产品定义恢复 SLA |
| F-081 | 无历史进程死亡基线 | 同机同 APK 两批对照 |
| F-083/F-084/F-085/F-090 | 无后台限制阈值；低存储只做到 98% 启动（PID 存在、FATAL/ENOSPC 0，UI 未确认）；每场景 1 次；无历史基线 | 专用模拟器把 `/data` 填到 98% 后走完整导出/预览并确认失败文案 + 重复 3 次 |

## 5.1 FAIL（本轮发现的真实缺陷，未在 F 域修复）

| 行 | 缺陷 | 证据 | 处置 |
|---|---|---|---|
| F-055/F-057 | 2h soak 第 39 轮（2504s）崩溃：`SchedulingSettingsScreen.kt:74` Slider 回调把 0.7..0.97 外的值传给 `SchedulingOptions.copy`，`StudyExperienceRepository.kt:737` 的 `require` 抛 `IllegalArgumentException`；进程重启 | `soak-crash-logcat.txt`、`soak-crash-dropbox.txt`、`soak-samples.csv` 第 39 行、`soak.device.json` | 进 FIX 域：在 Slider `onValueChange` 夹取到 `0.7..0.97` 或改 `valueRange`/步进；先加红测（端点/浮点越界），再修复；F 域不碰 `core/domain` 热区 |

## 6. 环境观察（不冒充 verdict）

1. **5562 降级**：首启 `KnowledgeInstall: packs fully parsed in 118660 ms`，
   冷启 10–13s（`Status: timeout`），`StrictMode DiskReadViolation ~6687ms`
   落在 `BackupRestoreStartupRecovery.recoverOnStartup` →
   `RestoreGenerationSupport.cleanupOrphans`（`SmartMistakeBookApplication.kt:334`、
   `RestoreRecoveryCoordinator.kt:249,376-388`）。该机不作为 F 基线；产物在
   `env-5562-degraded/`。这提示启动路径存在同步文件 I/O，是否产品问题需
   干净安装 + 真机复现，**本域不据此判 FAIL**。
2. **5566 首启知识库安装 64.6s**（`packs fully parsed in 25008 ms` + 索引重建，
   `install finished in 64661 ms`）；安装期间首帧已在 3.63s 出现，属于后台任务。
3. **DB 性能门**：`PerformanceGateTest` 在 5562 上 6/6、452.6s、搜索页
   p95=127ms（目标 350ms）；批次 43 在 5584 为 81.978s、p95=50ms。差异主要
   来自 5562 降级环境，不写成回归。
4. **2h soak**：见 `soak-stats.txt`；第 39 轮（2504s）崩溃一次，dropbox app
   计数 12→14、crash buffer 73 行，进程重启后继续到 2723s 人工停止；
   PSS 峰值 196.9MB（崩溃前 158–197MB 区间）。这不是“无崩溃”基线，
   F-055/F-057 判 FAIL。
5. **导出/预览**：批量 60 题 64 页 516–553ms，产品单题 487–820ms，PDF 预览
   0.332–0.378s，D-04 0.581s；与批次 43 同量级，未见回归信号。
6. **进程死亡**：5566 重启 3219ms、5584 重启 7711ms（后者 229MB 测试库）；
   两者 pid 均变化、首页恢复、无新崩溃。
7. **崩溃复现要点**：`SchedulingSettingsScreen.kt:74` 的 Slider
   `onValueChange` 未夹取；Compose Slider 在端点/浮点步进下可能给出
   0.6999999 或 0.9700001。现有 `SchedulingSettingsInstrumentedTest.kt:39`
   只 `assertIsDisplayed()`，没有拖端点，故此前未暴露。修复应在 UI 层夹取
   （不碰 `core/domain` 热区），并补端点红测。

## 7. 复跑

```bash
# 启动/首帧（约 1 分钟）
bash docs/research/evidence/2026-11-05-batch-44/f-domain/f-startup.sh \
  emulator-5566 7 5 docs/research/evidence/2026-11-05-batch-44/f-domain

# 单轮仪器化（示例：60 题批量导出）
bash docs/research/evidence/2026-11-05-batch-44/f-domain/f-run-instr.sh \
  emulator-5566 com.tingyun.smartmistakebook.core.export.test \
  com.tingyun.smartmistakebook.core.export.test \
  'com.tingyun.smartmistakebook.core.export.MistakePdfExporterInstrumentedTest#batchExportRendersBeyondSingleQuestionPageLimitWithinItsOwnBound' \
  export-batch1 docs/research/evidence/2026-11-05-batch-44/f-domain

# 2h soak（会占用设备 2 小时）
bash docs/research/evidence/2026-11-05-batch-44/f-domain/f-soak.sh \
  emulator-5566 7200 docs/research/evidence/2026-11-05-batch-44/f-domain

# 证据整理 + 台账预览/应用
python -I docs/research/evidence/2026-11-05-batch-44/f-domain/f-make-evidence.py
python -I docs/research/evidence/2026-11-05-batch-44/f-domain/f-update-ledger.py --preview
python -I docs/research/evidence/2026-11-05-batch-44/f-domain/f-update-ledger.py --apply
python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md
python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md
```

低存储升级路径（专用设备，勿在共享模拟器执行）：`pm clear` 后
`fallocate -l <fill> /data/local/tmp/fill.bin` 把 `/data` 用到 95%，再走
导出/预览/启动；记录 `df -h /data` 与失败文案。arm64 升级路径：换 arm64
真机后重跑启动/导出/预览/内存四组，并保留 APK sha。

## 8. 新增物与它们消灭的具体失败

| 新增 | 消灭的具体失败 |
|---|---|
| `f-startup.sh` | 启动/首帧只能靠回忆或单次截图，无法复算 p50/p95 与 warm-up 收敛 |
| `f-run-instr.sh` | 只有 “OK (n tests)”，没有 serial/APK sha 绑定、没有 PSS 峰值、没有 exit 日志 |
| `f-soak.sh` | 没有长时稳定性窗口；崩溃/ANR/内存增长只能靠人工盯 |
| `f-make-evidence.py` | 台账 PASS 只有口头结论，没有 device JSON / exit=0 日志 / JUnit XML |
| `f-update-ledger.py` | 手改台账可能覆盖其他域的行，或把骨架 UNVERIFIED 静默改成 PASS |

## 9. 边界

- 模拟器 x86_64 基线只代表本环境；真机/arm64 的冷启、首帧、内存、2h soak
  仍 BLOCKED。
- 自动化不能替代人类语感/审美；F 域不包含此类项，未用 UNVERIFIED 冒充 PASS。
- `TEST-*.xml` 是从 `am instrument -w -r` 原始输出整理的 JUnit 表示，原始
  `*.instr.txt` 同目录保留；它们不是 Gradle 产物。
- 台账 F 行的 `device:` JSON 均带 `serial` 与 `apk_sha256`；`file/hash` 只作辅助。
