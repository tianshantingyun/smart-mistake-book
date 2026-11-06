# 批次 42 基线核对（只读，2026-11-03）

| 字段 | 值 |
|---|---|
| evidence-id | B42-BASELINE-01 |
| executed-at | 2026-11-03T04:45+08:00（= 2026-11-02T20:45Z）；文档名按计划批次 42 的 2026-11-04 |
| head | `faab17f58b01eab78d5c08a9f843b3af3df2fecf` |
| origin/main | 本地 `refs/remotes/origin/main` = 同值；**远端实测失败**（`git ls-remote` 网络不可达，见 §1.2） |
| 执行者 | 基线核对员（子代理），共享工作树，只读 |
| 工具 | git、Python 3.13.14、JDK 21.0.11、adb/emulator（SDK `C:\Android\Sdk`） |
| 未执行 | 任何 Gradle 任务、任何 git 写、任何设备测试、任何 `am instrument` |
| verdict | 基线成立；计划 §0 有 **3 条硬冲突**（远端不可证、WS2 文件范围自相矛盾、WS6 与 2026-10-23 已实施裁定相反）+ 1 条不变量冲突，见 §六 |

核对口径：本文件只写本轮**实读/实跑**的东西。计划 §0 与批次 41 记录 §9.1 的单测门/强门是**转录**，本轮未重跑，一律标 UNVERIFIED，不按通过计。

---

## 结论摘要

| # | 核对项 | 结果 | 判据 |
|---|---|---|---|
| 一 | HEAD / 远端 / 工作树 / DB 73 / 请求 19 | ✅ HEAD、schema 73、请求 19 一致；⚠️ 远端相等本轮不可证；工作树 539 条且会话内漂移 | §1 |
| 二 | 热区与 `MistakePdfExporter` 调用链 | ✅ 热区 5 M 与计划 §0 完全一致；`core/export` 主码零 M；调用链行号已定位 | §2 |
| 三 | F7/F8/F10/F11/F12 代码现状 | ✅ 五处缺陷**全部仍在**（HEAD 未修）；F7、F10 已用实跑探针复现假绿 | §3 |
| 四 | 设备 / AVD / pypdf | ✅ 3 台模拟器在线（API 34 / x86_64 / 4096）；8 个 AVD；pypdf 6.16.2 可用 | §4 |
| 五 | 备份 fixture / SelectionPlan / 6C-2 §9.11 | ✅ 无备份字节夹具；v4 仍为代码内自证；三项裁定已于 2026-10-23 裁完并实施 | §5 |
| 六 | 与计划 §0 不符 | ⚠️ 逐条见 §6（3 硬冲突 + 4 口径补充） | §6 |

---

## 一、HEAD、远端、工作树、schema、请求版本

### 1.1 HEAD

```text
$ git rev-parse HEAD
faab17f58b01eab78d5c08a9f843b3af3df2fecf
$ git log --oneline -3
faab17f5 docs: 批次41 主会话收口（门禁实跑/20 笔提交/高发现移交）（1 文件）
7d853f20 docs: 批次41 台账回填（设备证据更正 F5；KD-75 仍 open）（4 文件）
2bda2d1e docs: 批次41 记录/基线/WS1 子件 + schema 契约隐藏目录修复（4 文件）
$ git branch -vv | grep '^\*'
* main  faab17f5 [origin/main] docs: 批次41 主会话收口…
```

### 1.2 远端（本轮**未**证实）

```text
$ git rev-parse origin/main
faab17f58b01eab78d5c08a9f843b3af3df2fecf      # 本地 remote-tracking ref，commit 时间 2026-11-03 04:23:15 +0800
$ git ls-remote origin refs/heads/main
error: RPC failed; curl 28 Failed to connect to github.com:443 after 21084 ms: Could not connect to server
fatal: expected flush after ref listing
```

两次尝试（含 60 s `timeout` 包裹）均 `curl 28`。**结论：`HEAD == origin/main` 本轮只由本地 ref 支撑，远端实际值 UNVERIFIED**；计划 §0 的该条不因本文件而被记为“实测通过”。

### 1.3 工作树（时点值，且在漂移）

```text
$ git status --porcelain | wc -l          -> 539
$ git status --porcelain | cut -c1-2 | sort | uniq -c
    437 ??
    102  M
$ git diff --cached --name-only | wc -l   -> 0（暂存空）
```

- **同一会话内从 530 涨到 539**（两次相隔约数分钟，均为 `git status --porcelain | wc -l`）。说明他线仍在同一工作树写文件；本文件任何计数都只是**时点值**。
- M 按顶层分布：`core` 44、`docs` 40、`tools` 10、`app` 5、`.gitignore` 1、`dynamic_gui_architecture_plan.md` 1、`knowledge-production` 1。
- ?? 按顶层分布：`docs` 22、`core` 19、`tools` 16、`knowledge-production` 13、`feature` 6、`app` 5，其余为 `.agent_audit_crops*`、`%TEMP%`、`tmp*` 等工具产物。

### 1.4 DB schema 73 与请求 schema 19

```text
$ python -I -c "…json.load(open('core/database/schemas/…/73.json'))…"
version 73 entities 92 views 2 hash 1d086050204a0da1ed6b6e3299dd9733
$ grep -n "CURRENT_SCHEMA_VERSION\s*=" core/model/.../ModelTasks.kt
567:        const val CURRENT_SCHEMA_VERSION = PAPER_EDIT_SCHEMA_VERSION
$ grep -n "PAPER_EDIT_SCHEMA_VERSION\s*=" core/model/.../ModelTasks.kt
566:        const val PAPER_EDIT_SCHEMA_VERSION = 19
```

与计划 §0「DB schema 73（92 表）、请求 schema 19」一致（92 entities / 2 views / hash `1d086050…`）。

### 1.5 备份四方一致性门（本轮实跑，非转录）

```text
$ python -I tools/ci/check_backup_plan_sync.py
  OK   allowedMasks == block maskPolicy.closedMasks：code=[9, 13, 15] block=[15, 13, 9]
  OK   digest: recomputed==test golden==block golden：5c690755…aad05a
  OK   v4: 91 keys and delta == {+error_cause, -material_feature}：keys=91
  OK   v4: frozen closure/prefix/trim limit：closure=4 prefix=smbk-selection-v4 trimLimit=83
  OK   v4: empty-full digest golden：recomputed=ee9a1b4d…fac06b006 pinned=ee9a1b4d…fac06b006
PASS 四方一致（code / §9.11 / golden / 73.json）        # exit=0
```

---

## 二、热区状态与 `MistakePdfExporter` 调用链

### 2.1 热区与计划 §0 完全一致（5 M）

```text
$ git status --porcelain -- core/domain core/ui core/export feature/ settings.gradle.kts
 M core/domain/src/main/kotlin/…/core/domain/TutorJudgedReview.kt
 M core/export/src/test/kotlin/…/MistakePdfEligibilityTest.kt
 M core/export/src/test/kotlin/…/MistakePdfPagePlannerTest.kt
 M core/export/src/test/kotlin/…/MistakePdfPlannerGoldenTest.kt
 M core/ui/src/main/java/…/core/ui/Theme.kt
?? core/domain/gradle.lockfile  (另 8 个模块锁 + feature/tutor/.../nul)
```

- **`core/export` 主码无 M**（`git diff --stat -- core/export` 只有 3 个测试文件：+85/−4）。计划 §0「`core/export` 主码未变」成立。
- `MistakePdfExporter.kt` 最后一次被提交改动的记录：`git log -1 --date=iso -- …/MistakePdfExporter.kt` → `bf81d8f6 2026-11-01 16:30:19 +0800`，与计划 §0「自 11-01 16:30 未变」吻合。
- 3 个 `core/export` 测试 M 是**他线**内容（新增“同一 revision 多张 CLEAN_IMAGE 取最新”“练习卷不泄露已选项/禁用控件”等用例）。本批若接管 exporter，**不得**把这些 hunk 记入本批提交。

### 2.2 `MistakePdfExporter` 完整调用链（行号）

| 环节 | 位置 |
|---|---|
| 生产装配（唯一实例） | `app/src/main/kotlin/…/SmartMistakeBookApplication.kt:426` `MistakePdfExporter(this)`；`:436` `renderPdf = mistakePdfExporter::prepare`；`:430` 传入 `MistakeExportJobRunner` |
| 编排调用 | `core/export/…/MistakeExportJobRunner.kt:168` `val prepared = renderPdf(input)`（DOCX 分支 `:181`） |
| `prepare` 主体 | `core/export/…/MistakePdfExporter.kt:114-144` |
| **缓存短路（先于渲染）** | `MistakePdfExporter.kt:120-123` `store.findExisting(input)?.let { … return@withLock existing … }` |
| 渲染 | `:128` `DeterministicMistakePdfRenderer.render(input, temporaryPdf)` |
| 尺寸/页数校验 | `:129-133`（字节上限）、`:134` → `validatedPageCount` `:165-181`（PdfRenderer） |
| **digest 落盘（注入点之后必须重算的那一步）** | `:135` `store.writeDigest(temporaryDirectory, sha256File(temporaryPdf))` |
| publish / 目录命名 | `:136` → `PreparedPdfStore.publish :358-380`；`targetDirectoryFor :382-385`（目录名 = `input.inputSha256`） |
| `verifyIntegrity` | `:64-72`（文件名、父目录名 == `inputSha256`、长度区间、`sha256` 格式、目录恰含两文件、记录 digest、实际 sha 全部相等） |
| `reopenVerified` | `:151-163` → `findExact :320-345`（同样要求父目录名 == token.inputSha256） |
| **缓存键来源** | `core/export/…/MistakePdfEligibility.kt:185-209`：`inputSha256 = exportFingerprint("mistake-export-v3", …)`，字面前缀在 **`:186`**；`exportFingerprint` 定义 `:438-449` |
| 分享 URI 的额外约束 | `core/export/…/MistakePdfDelivery.kt:408-423`：文件必须是 `…/mistake_pdf_exports/<inputSha256>/mistake.pdf` |
| 部分页打印 | `MistakePdfDelivery.kt:146` 调用 → `PreparedPdfPrintWriter.writeSubset :215`（WS2 item 21 的落点） |

### 2.3 对 WS2 的两条硬约束（读码得出的，不是推断）

1. **缓存键不在允许改的文件里**：`inputSha256` 由 `MistakePdfEligibility.kt:185-210` 计算，`core/export` 其他文件全部依赖它作目录名（`:66-67`、`:88-91`、`:383`、`:410`）与 token 价值（`MistakePdfDelivery.kt:419`）。计划 §4 只允许改 `MistakePdfExporter`，**无法**完成「缓存键/指纹升版」。
2. **只改 `prepare` 挡不住旧无标记包**：`:120-123` 在渲染之前就按 `findExisting` 直接返回已缓存产物，`/AIGC` 注入只在渲染后才有机会发生。因此 WS2 item 22（旧无标记包不回退）需要**至少**在缓存键里带入 AIGC 标志位，即触及 `MistakePdfEligibility.kt`。

补充事实：`core/export/src/main` 22 个 Kotlin 文件 `AIGC` 命中数**全为 0**（`grep -rc`），确认 KD-75 当前零接线；`AiGeneratedImageCaption.kt` 存在，是 item 20「显式图注逐位不变」的回归对象。

---

## 三、F7/F8/F10/F11/F12 代码现状（HEAD 全部未修）

### 3.1 F7（高）`isolation_pair_report.py`：OFF 臂缺失方法被当“未红”

- 解析只收录日志里出现过的 test：`:106-124` `parse_instrument_log` 返回 `dict[str,str]`，缺方法**不产生条目**。
- 唯一的 SKIP/UNKNOWN 守卫 `:298-306` 只遍历**已存在**的向量值，缺方法不会进入该守卫。
- 比较时用默认值兜底：`:349-350` 取两臂方法并集，`:356`/`:358` `.get(method, UNKNOWN)`；`UNKNOWN` 不在 `RED`（`:58`）→ 记 0 红。
- 判词假绿：`:384-388` `identical = not discordant` → `PAIRED_RED_SET_IDENTICAL`。

**本轮实跑探针（只读，importlib 载入工具模块）**：

```text
OFF 臂仅含方法 A（3 轮），ON 臂含 A、B（3 轮），全部绿
verdict= PAIRED_RED_SET_IDENTICAL paired= True reasons= []
OFF round1 keys= ['A']      ON round1 keys= ['A','B']
```

即 OFF 臂从未跑过 B，报告仍判“红集相同、配对成立”。用例侧：`tools/tests/test_isolation_pair_report.py` 15 例（`:90-230`），**没有**“OFF 缺方法”的形状（计划 WS1 item 2 要补的就是它）。

### 3.2 F8（高）两臂同一 COMPONENT；T4 verify 只比源码树

- `tools/ci/run_isolation_pair.sh:51` 单一 `TARGET_PACKAGE/COMPONENT`（`com.tingyun.smartmistakebook.offline.test/…AndroidJUnitRunner`）。
- `:201-222` 两臂在同一个 `for arm` 里用**同一条** `am instrument … "$COMPONENT"`，臂差异只有 `:221` 的 `-e smb.t4.arm "$arm"`；脚本头 `:24-25` 自述“不改源码、不构建、不安装 APK（脚本只校验 sha）”。
- `tools/ci/t4_control_marker.py:50` `ENGINE_PATH = app/src/main/…/VisualJsClientEngine.kt`；`:87-120` marker 由**源码树**字节 + 构建日志 marker 得出；`:123-149` `verify_marker` 再次只对源码树重算（engine_sha、anchor 行号/sha），**没有任何 adb / 已安装 APK / APK sha 比对**（全文件无 `adb`、`apk` 字样）。
- 批次 41 结论「“T4 排除”仍禁止」在 HEAD 上依然成立。

### 3.3 F10（中）`summarize_kd76.py`：空 per-round sha 不判 INVALID；bootCount 无门

- 元数据按 round 收进 `meta`：`:165-176`；sha 缺失时 `sha=""`（`:173`）。
- sha 集合做“是否混淆”检查时**过滤空值**：`:363` `shas = {row.apk_sha256 for row in selected if row.apk_sha256}`；`:366-369` 仅在 `len(shas)>1` / `not shas` 时报错，**单条空 sha 不触发任何 reason**（`:383` 报告 `shas[0]`）。
- `boot_counts` 只统计与渲染：`:365`、`:415-416`，**从不进入 `reasons`**，无“boot_id 必须变化/唯一”的门。

**本轮实跑探针**（20 份合成 round XML，第 20 轮无 `apkSha256`、全部无 `bootCount`）：

```text
verdict= PASS evaluable= True n= 20
reasons= []
apk_sha256= aaaaaaaaaaaa
round20 sha=
boot_counts= []
```

即“20 轮里有一轮没记 APK 身份、全程不记 bootCount”仍输出 **PASS**。计划 WS3 item 27/28 要补的门在 HEAD 上不存在。

### 3.4 F11（中）阈值可被外部覆盖 + 旧单进程 20 轮入口仍可发 PASS

- `app/src/main/…/visualjs/Kd76ReportVerdict.kt:78-84`：`decide(sampleCount, g1Red, p95Millis, requiredSamples = REQUIRED_SAMPLES, gateMillis = GATE_MILLIS)` —— 两个阈值是**参数**，KDoc `:75-76` 只说“生产调用不得传更宽的值”，代码不拦。
- 调用点当前未传覆盖值（`VisualJsConnectStageCalibrationInstrumentedTest.kt:797-801`、`:822-826`），所以风险是“接口允许”，不是“现在已放宽”。
- 旧入口仍在且**没有** `@Ignore`：`VisualJsConnectStageCalibrationInstrumentedTest.kt:144-145` `@Test fun coldStartConnectStagesMeetTheReadyHalfBudgetAcrossTwoBatches()`，单进程内 `runBatch("A")`/`runBatch("B")`（`:384` 起），失败断言在 `:191-199`，`buildReport` 判词在 `:797`/`:822`。计划 WS1 item 7（改名 legacy + `@Ignore`/重定向）在 HEAD 上未做。

### 3.5 F12（中）预览归零与通道漏算

- `core/data/…/study/ReviewCapacityPreviewReader.kt:136`：`rebalanceBacklogCount = if (plan.rebalance == null) 0 else facts.size` —— “未规划”与“积压为 0”同值。
- `:140`：`totalGranted = plan.examPicked.size` —— 只算考试通道；再平衡通道在 `Scheduler.kt:132-147`（`rebalance.picked`，上限 `remainingSlots`，`:124-128`）被完全排除。
- **新增冲突（本轮发现）**：item 9 若把再平衡并入 `totalGranted`，会撞 `ReviewCapacityPreviewReader.kt:199-201` 的 init 不变量 `require(subjects.isEmpty() || totalGranted <= dailyCapacity)`（再平衡可用到 `maxDailyCapacity`，见 `:195-197`）。落地前必须先裁定“放宽不变量 / 新增独立字段”，不能直接改一行。
- 术语需对齐：计划 item 9 写作“due-review 通道”，但代码里只有**考试通道**与 **MissedDay 再平衡通道**（`Scheduler.kt:132-147`）；全仓 `.kt` 无 `dueReview` 标识符（`grep -rn dueReview …` 0 命中）。实施前须先确认 item 9 指的是 `rebalance.picked`。

---

## 四、设备、AVD、pypdf

```text
$ export PATH="/c/Android/Sdk/platform-tools:/c/Android/Sdk/emulator:$PATH"
$ adb devices
emulator-5562  device
emulator-5566  device
emulator-5584  device
$ emulator -list-avds
api23_smoke / api23_smoke32 / api23_smoke64 / audit_user_visible_20261016
kb_test_device / ps16k_arm64 / ps16k_x86_64 / test_device        （8 个）
```

| 设备 | SDK | ABI | PAGE_SIZE | boot_id |
|---|---|---|---|---|
| emulator-5562 | 34 | x86_64 | 4096 | `a0ebed2d-65fb-4421-9f93-d15d125ccfbf` |
| emulator-5566 | 34 | x86_64 | 4096 | `8f54d45c-cd12-4e96-95a2-1656b3b382d1` |
| emulator-5584 | 34 | x86_64 | 4096 | `12abf474-f06c-4702-893c-979ea249fdf5` |

- 三台均为 4096 页（非 16KB）；`ps16k_x86_64` / `ps16k_arm64` AVD 存在但**未启动**。arm64 硬件仍缺 → 计划的 16KB arm64 项维持 BLOCKED。`ro.kernel.qemu.avd_name` 三台均空（模拟器未报 AVD 名，只能按 serial 记）。
- `emulator-5584` 已安装包只有 `com.tingyun.smartmistakebook.core.data.test`（`adb shell pm list packages | grep tingyun`），**未装 app**；本批设备轨需重新 install。
- `adb`/`emulator` 不在默认 PATH，需显式加 `C:\Android\Sdk\platform-tools`（与批次 41 记录一致）。
- pypdf：`python -I -c "import pypdf,sys;print(pypdf.__version__)"` → **6.16.2**（Python 3.13.14）。JDK 21.0.11 Temurin 可用。
- 设备侧未执行任何 `am instrument`（本轨只读）。

---

## 五、备份夹具、SelectionPlan 现状、6C-2 §9.11 与三项裁定

### 5.1 夹具目录：无备份字节夹具

- `core/data/src/androidTest/assets/` = `dense/`、`golden/`（4 文件：`golden_queries_v1/v2.json` + 两个 `.sha256`）；`core/data/src/test/resources/` = `dense/`、`model/`、`visual/`。**没有** `.smbk` / `backup-*.zip` 夹具。
- v4（closure 4）覆盖仍是 **JVM 内构造**：`core/data/src/test/kotlin/…/backup/SmkbArchiveCodecTest.kt:1486`（v4 规则表校验并投影 91 键）、`:1528`（v5 算法算出的 digest 被拒）、`:1570`（v5 键空间/不支持 closure 被拒）；夹具构造函数 `:1624 v4SelectionFixture`、`:1620 v4EmptyRowCounts`。
- 即批次 41 的 F14（“v4 夹具由同套规则自证、无历史真实包”）在 HEAD 上不变；计划 WS5 42/43（冻结字节 + digest golden）尚无载体。
- 可用材料：`ClosureRuleTables.v4`（`ClosureRuleTables.kt:71-93`，closure=4）与四方门里**已钉住**的 v4 空库全选 digest `ee9a1b4d…fac06b006`、91 键、`smbk-selection-v4`、trimLimit 83 —— 构造合法 v4 包不需要伪造行。

### 5.2 SelectionPlan 现状（机器可核）

| 事实 | 位置 |
|---|---|
| `allowedMasks = setOf(9, 13, 15)` | `SmbkSelectionPlan.kt:79` |
| `rejectedMasks = (0..FULL_MASK) - allowedMasks` | `:82` |
| 只允许 9/13/15 的自检 | `:593`、`:634` `isCreatableMask`、`:638-640` `requireCreatable` 拒绝 0/8 |
| closure 5 / digest 前缀 `smbk-selection-v5` | `:70`、`:73` |
| 92 表 / trimOrder 84 / 派生表恒裁 | `:213`、`:224`、`:581-586` |
| 归档侧同样拒绝非 9/13/15 | `SmbkArchiveCodec.kt:436-438`、`:1206-1209` |

四方位门本轮实跑 exit=0，上述值全部一致（§1.5）。

### 5.3 6C-2 设计件：§9.11 与三项裁定的真实状态

- §9.11「机器可读清单 v3」：`docs/research/2026-10-17-6c2-content-selection-design.md:1647`（标记 `6c2-plan-v3:begin/end`，`closureVersion=3` 起步、现行读 v3）。
- 三项裁定表：§9.9 `:1592-1615`；待裁定原表 §6.1 `:519-540`。
- **裁定已于 2026-10-23 由用户作出并已实施**（`:1592-1596` 裁定记录）：
  - **①=a** 不提供 mask=8（“仅图片”组合）；
  - **②=a** 禁止 mask=0（空选择）；
  - **③=a+c** 派生索引恒裁 + 恢复完成后按科后台重建；
  - 允许组合只有 **9 / 13 / 15**；0/8 与未闭合组合在域、清单、裁剪器、仓储、UI 各层拒绝；`BackupSelectionTest` / `BackupSelectionUiInstrumentedTest` 把这些拒绝钉成用例（`:1597-1601`）。
- 其余 5 项（类别文案、无图子集、治理台账归类、合并恢复、VACUUM 耗时）仍**未决**，`:1610-1615` 明确“本轨不代签”。

---

## 六、与计划不符处（逐条）

> 计划文件：`.zcode/plans/plan-2026-11-04-batch-42.md`。

### 6.1 硬冲突（不裁定就无法按计划落地）

| # | 计划条文 | 现状证据 | 后果 / 需要什么 |
|---|---|---|---|
| C1 | §0「HEAD = `faab17f5` = `origin/main`」 | `git ls-remote` 两次 `curl 28` 失败（§1.2）；只有本地 remote-tracking ref 等于 HEAD | 本批不能把“与远端同步”写成实测通过；推送前需网络恢复后重跑 `ls-remote` |
| C2 | §4「`core/export` 只做 `MistakePdfExporter` 最小 hunk」 vs WS2 13/14/22 | 缓存键在 `MistakePdfEligibility.kt:185-209`（`"mistake-export-v3"` 在 `:186`）；`findExisting` 在渲染前短路（`MistakePdfExporter.kt:120-123`） | 仅改 exporter **无法**升版缓存键、也无法阻止旧无标记包被直接返回。需裁定：允许第二个文件（`MistakePdfEligibility.kt`）的最小 hunk，或改设计（如在 exporter 内派生 artifact key，但那会动 token/目录名不变量 `:66-67`/`:88-91`/`MistakePdfDelivery.kt:419`） |
| C3 | WS6 48「仅图片组合允许，纯 CLEAN_IMAGE 拒绝」；49「空选择 = 合法的仅身份/设置包」 | 2026-10-23 用户裁定 **①=a 不提供 mask=8、②=a 禁止 mask=0**，已实施并被 JVM/设备用例钉住（`SmbkSelectionPlan.kt:79/593/634-640`、`SmbkArchiveCodec.kt:436/1206`、设计件 §9.9 `:1592-1615`） | 计划等于**推翻已实施裁定**。按纪律不得由执行者代签：必须先由用户重裁；未重裁前 48/49 记 BLOCKED，只可做“现状核对/影响面清单” |

### 6.2 口径补充（不阻塞，但会改变记账）

| # | 计划条文 | 现状 | 处置建议 |
|---|---|---|---|
| M1 | §0 未给工作树计数 | 539 条（437 ?? / 102 M），且同一会话内 530→539 漂移 | 任何“工作树 N 条”标时点；提交前逐路径核对，不得按批次 41 的 600 条引用 |
| M2 | §0「热区现仅 5 M …`core/export` 3 个测试」 | 完全一致（§2.1）；但那 3 个测试 M 是他线新增用例（+85/−4） | 本批接管 exporter 时只做最小 hunk，且不得把他线测试 hunk 计入本批提交 |
| M3 | WS1 9「`totalGranted` 计入 due-review 通道」 | 预览不变量 `ReviewCapacityPreviewReader.kt:199-201` 要求 `totalGranted <= dailyCapacity`（考试通道口径），再平衡可用到 `maxDailyCapacity`（`:195-197`、`Scheduler.kt:124-128`） | 先裁定“放宽不变量 vs 新字段（如 `totalGrantedIncludingRebalance`）”；否则改一行必红 |
| M4 | §0「单测门仅 3 例 KB 红；强门 exit=0」 | 来自批次 41 记录 §9.1（主会话实跑）+ 本批 §0 转录；本轨本轮**零 Gradle** | 记为转录/UNVERIFIED；批次 42 门禁阶段必须重跑后才有本批结论 |

### 6.3 与本轮无关但影响本批排期的既有事实

- `docs/current` 7 M（`README`、`architecture-contract`、`data-schema`、`implementation-status`、`model-task-contracts`、`product-contract`、`release-gates`）——与计划 §0 一致，契约正文仍不能落笔，WS9 71 只能出 patch。
- `tools/ci/` 本轮**零 M/零 ??**（`git status --porcelain -- tools/ci` 空），F7/F8/F10 三个修复点可直接接管，不会与他线 hunk 冲突。
- `emulator-5584` 未装 app（只有 data test 包），WS2/WS3/WS4/WS5 的设备轨都要先重建、重装并绑定 APK sha。

---

## 七、本轮 UNVERIFIED 清单（不得写通过）

1. `origin/main` 远端实际值（`git ls-remote` 网络失败）。
2. 任何 Gradle 编译 / 单测门 / 强门（本轨零 Gradle）；计划 §0 与记录 §9.1 的门禁结果均为转录。
3. F11（Kotlin 判词参数）、F12（Kotlin 预览）**未跑**——只有读码证据；F7/F10 有本轮实跑探针（假绿已复现），F8 只有读码证据。
4. 任何设备行为：本轮只 `adb devices` / `getprop` / `pm list packages`，未 `am instrument`、未 install。
5. 16KB arm64 运行时（无 arm64 硬件，`ps16k_arm64` AVD 未启动）。
6. v4 真实历史包的字节行为（无冻结夹具，仅代码内构造）。
7. KD-75 真实导出链（`core/export/src/main` 22 文件 AIGC 命中 0，确认未接线——这是“未接线”的正面证据，不是“已验证”）。

---

## 八、回链

- 计划：`.zcode/plans/plan-2026-11-04-batch-42.md` §0/§1/§2（WS1–WS6/§4/§5）。
- 上批记录：`docs/research/2026-11-03-batch-41-record.md` §9.3（F7/F8/F10/F11/F12 移交）、§4（设备证据口径）、§9.1（门禁转录来源）。
- 上批基线：`docs/research/2026-11-03-batch-41-baseline.md`（格式与核对口径沿用）。
- 6C-2 设计件：`docs/research/2026-10-17-6c2-content-selection-design.md` §6.1 `:519-540`、§9.9 `:1592-1615`、§9.11 `:1647`、§9.12 `:2319`。
- 代码落点：`core/export/…/MistakePdfExporter.kt`、`MistakePdfEligibility.kt`、`MistakePdfDelivery.kt`、`MistakeExportJobRunner.kt`；`core/data/…/study/ReviewCapacityPreviewReader.kt`、`…/study/ReviewCapacityScheduler.kt`；`core/data/…/backup/SmbkSelectionPlan.kt`、`ClosureRuleTables.kt`、`SmbkArchiveCodec.kt`；`app/src/main/…/visualjs/Kd76ReportVerdict.kt`；`app/src/androidTest/…/visualjs/VisualJsConnectStageCalibrationInstrumentedTest.kt`；`tools/ci/{isolation_pair_report.py,run_isolation_pair.sh,t4_control_marker.py,summarize_kd76.py}`。
