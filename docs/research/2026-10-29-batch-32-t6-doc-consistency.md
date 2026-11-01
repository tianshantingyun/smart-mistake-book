# 批次 32 T6 文档回改与 AUD-08 溯源（2026-10-29）

> **口径**：本轨是**台账/文档回改轨**。未运行 Gradle，未编译，未执行任何 JVM/仪器化/设备用例，未做 git 写操作。
> 代码文件只改了 **KDoc 注释**（零可执行语句、零行为变化）；未编译 → 注释语法/整包编译 **UNVERIFIED**。
>
> **快照时点**：2026-10-29（本轨开工后实测；T1 判据锚点部分按 **10:10** 现树复读）。HEAD
> `0b4bdbbea918ba6b8c8e1f3b190b8be502ec627a`；`git status --porcelain | wc -l` = **713**（他线在飞，非本轨数字）。
>
> **并行轨漂移（必须读）**：批次 32 T2 在本轨作业中途（09:58）重写了 parity 面——`THRESHOLD_VERSION`
> `calibrated-r1` → **`calibrated-r2`（数值一个不动）**、N09 改为跨栅格模型断言、新增 N13、测试 15 → **16 例**。
> 本轨首轮按 09:5x 前状态写的 T1 锚点已作废，已按 10:10 现树重读并回改；若 T2 继续改，锚点仍会漂移，
> 使用前必须重跑 `grep -n`。
>
> 计划对照：`.zcode/plans/plan-2026-10-29-batch-32-parity-schema71.md:29`（任务 10 五项）。
> 基线对照：`docs/research/2026-10-29-batch-32-baseline.md` §6/§7/§8。

---

## 0. 结论速览

| 计划任务 10 的子项 | 本轨处置 | 状态 |
|---|---|---|
| 台账反向虚报（把已有证据写成"目录不存在/未跑"） | KD-77 标题 + Symptom 前提回改（镜像已装盘、旧包冒烟自述已跑但 logcat 未落仓）；KD-80 新增批次 32 状态块，重申历史块已被设备轨取代 | **已改** |
| KD-80 状态 | 现树 schema 常量已升 **71**（他线未提交），但 `71.json` 缺失、`SmbkSelectionPlan` 仍 **70** → 备份版本门会复现 KD-80 形态；**KD-80 保持 open**，S=70 的 34/34 不得用于关 S=71 | **已登记** |
| 契约 §8.1 LIVE 语义 | 批次 31 已写"LIVE 永不淘汰、只淘汰未固定且 STAGING 的中间态"；本轨**复核与现树 SQL 同词**，补 `INITIAL` 排除与批次 32 T3 的 **STAGING 租约**锚点 | **原本已正确，复核补注** |
| T1 判据的 `1.000` 与行锚 | 标注 `1.000` 为旧源期望并给出跨栅格欧氏距离推导；注明现树（批次 32 T2）改为模型断言；回改 §2.2/§2.7/§2.8/§3 的行锚 | **已改（10:10 现树）** |
| 测试 KDoc 的 `connectedAndroidTest` 写法 | 批次 31 记录点名的 2 个 KDoc 改为 `assembleDebugAndroidTest` + `install -r -t` + `am instrument`；grep 另发现 1 个同形 KDoc（`AssessmentItemVerificationStatusInstrumentedTest`）一并改；"提交时未执行"的反向句改为"E-09 实跑 9 例、第 10 例未上设备" | **已改（仅注释）** |
| AUD-08 的 WAL 溯源 | 未改工具代码；登记 **KD-81**（已知限制 + 可复跑检测门 + 本轨实跑读数），契约 §8 与 `tools/split_golden/README.md` 同步检测步骤 | **已登记，工具代码未改** |

---

## 1. 改动清单（文件 + 改后行）

| 文件 | 改后行 | 改动 |
|---|---|---|
| `docs/known-defects.md` | `:2883` | KD-77 标题：删掉"本机无 android-23 平台/系统镜像，未执行 v21 APK 冒烟"，改为"镜像已装盘，旧包冒烟 logcat 未落仓、修复后 APK 未跑" |
| `docs/known-defects.md` | `:2887` | KD-77 新增"标题/Symptom 前提回改"段：镜像/AVD 在盘；T7 旧包冒烟是**自述**、logcat 未落仓 → 运行时结论未复核；反向表述作废 |
| `docs/known-defects.md` | `:2990` | KD-80 新增批次 32 状态块：schema 71 常量在树、`71.json` 缺失、`SmbkSelectionPlan` 仍 70 → 版本门复现风险；KD-80 open |
| `docs/known-defects.md` | `:2996` | 新增 **KD-81**：AUD-08 快照 `source.db.sha256` 不含 `-wal`/`-shm`，含受控实验、本轨 sha/stat 读数、检测命令与关闭条件 |
| `docs/current/visual-generation-contract.md` | `:190` | §8.1 追加批次 32 复核锚点：evict SQL `status=STAGING` 正向条件（`TutorVisualDao.kt:210-235`）、`kind ∈ {NULL,LATEST}` 排除 `INITIAL`、批次 32 T3 的 owner/created_at 租约三选一 |
| `docs/current/visual-generation-contract.md` | `:208` | §8 AUD-08 段追加"快照身份边界"：`source.db.sha256` 只覆盖主库字节、不含 WAL；登记 KD-81 |
| `tools/split_golden/README.md` | `:86-99` | 已知限制补 KD-81 编号与"检测（复跑前必跑）"：主库 + `-wal`/`-shm` 一起记账、WAL 非空未入 manifest 或三件 mtime 不同即判身份不完整 |
| `docs/research/2026-10-29-batch-31-device-criteria.md` | `:50-63` | §0-4 标注 `1.000` 是旧源期望；现树（批次 32 T2）改为跨栅格模型断言（P06 1.2625 / P07 0.608282），并给出 `hypot` 推导 |
| 同上 | `:18-24` | 顶部新增"10:10 现树漂移"：parity 16 例、`calibrated-r2`、N09 模型断言、`:102` 行锚 |
| 同上 | `:171` | §2.2 `onlyLiveSnapshots…` 行锚 `:90` → `:102`（10:10 现树；符号为准） |
| 同上 | `:286` | §2.7 `pdfWriteFailureIsCoded` 行锚 `:447` → `:448` |
| 同上 | `:301-304` | §2.8 `PARITY_GATE` 行锚 `:88` → `:100`；目标 15 → 注明现树 16 |
| 同上 | `:307-317` | §2.8 阈值表行锚 `:234/:242/:243/:244-245/:246/:658-710` → `:262/:270/:271/:272-273/:274/:687-742`，版本名注明 r1→r2 |
| 同上 | `:328-339` | §2.8 F5 锚 `:717-719` → `:749/:759`；负控清单加 N13，函数锚 → `:309/:368/:450/:466/:500` |
| 同上 | `:344-362` | §2.8 logcat 命令注明"批次 31 证据为 r1/15；现树 r2/16" |
| 同上 | `:590-643` | §4.3 dex 门补 `n13CentroidBlindSpotIsCoveredByBboxOrShift`、仓库 4 个新方法、`snapshotDigest…`、`drawCommandsDigest…`、`repositoryPlanRetainsAStagingIntermediateOnlyBecauseItIsPinned`；注释计数 parity 15→16、仓库 4→8、结构门 4→5、提交 9→11 |
| 同上 | `:745-752` | §4.5 辅助套件计数：提交 9→11、仓库 4→8、结构门 4→5（10:07 现树） |
| 同上 | `:370`、`:466` | KD-80 关闭条件引用 `docs/known-defects.md:2963` → `:2977`（台账增行后现树行号） |
| 同上 | `:923` | §7.1 的 `1.000` 标注"旧源期望，现树改为模型断言，见 §0-4" |
| `core/database/.../TutorVisualCommitInstrumentedTest.kt` | `:28-33` | KDoc：删除 `connectedDebugAndroidTest` 命令与"提交时未执行"；改为 assemble/install/am instrument + E-09 9 例；现树 11 例（T3 10:07 同步，2 个 digest 负例未上设备） |
| `core/data/.../visual/TutorVisualCommitRepositoryInstrumentedTest.kt` | `:28-34` | KDoc：删除 `connectedDebugAndroidTest` 命令；改为 assemble/install/am instrument + E-09 4 例；现树 8 例（T3 在 10:04 同步计数，+4 例未上设备） |
| `core/database/.../AssessmentItemVerificationStatusInstrumentedTest.kt` | `:29-33` | KDoc：同形 `connectedDebugAndroidTest` 命令改为 assemble + install + `am instrument`（grep 枚举出的第三处） |

**整文件纳入**：`docs/known-defects.md`、`docs/current/visual-generation-contract.md`、
`docs/research/2026-10-29-batch-31-device-criteria.md` 开工时干净（`git status --porcelain` 无输出），本轨改动后变 ` M`；
三个测试 KDoc 同样由干净变 ` M`。`tools/split_golden/README.md` 开工时已是 ` M`（他线改 GATE/measure/check
与 README 其他段），本轨只加"已知限制/检测"一段，提交时**整文件纳入并注明含他线改动**。
按纪律提交时不得只挑本轨片段；其中 `TutorVisualCommitRepositoryInstrumentedTest.kt` 另含批次 32 T3 在
10:04 加的新用例与计数（非本轨）。本文件为 `??` 新文件。

---

## 2. 台账反向虚报清点

「反向虚报」= 已知证据存在却写成"目录不存在 / 未跑"。逐条对照现树：

| 位置 | 旧句 | 现树事实 | 处置 |
|---|---|---|---|
| KD-77 标题（`:2883` 改前） | "本机无 android-23 平台/系统镜像，未执行 v21 APK 冒烟" | 批次 30 T7 已装 `platforms;android-23` + 三套镜像 + AVD 在盘；旧包冒烟为 T7 **自述**、logcat 未落仓 | **改标题 + 补回改段**（`:2887`） |
| KD-80 批次 31 T5 块（`:2979` 附近） | "`docs/research/evidence/2026-10-29-batch-31/` 不存在 / 34/34 本批未跑" | 该目录已有 **15 份**证据；`BackupRestoreInstrumentedTest` 设备 `OK (34 tests)`（E-01） | 已被同条 `:2979` 块显式取代；本轨新增 `:2990` 块再次钉住，**不改写历史块** |
| KD-71 批次 30 T12 块 | "两文件无本批改动 / 本批没有证据目录" | `QuickJsVisualExecutionEngine.kt` +20、JVM 测 +58；batch-30 目录 11 份 | 已被同块"复核修复轮更正"取代；不改写 |
| KD-76 批次 29 T6 / KD-78 批次 29 T6 | "`evidence/2026-10-28-batch-29/` 实测不存在" | 该目录已落盘；批次 29 设备轨块取代 | 已被后续块取代；不改写 |
| KD-79 批次 30 T12 块 | "证据目录不存在 / 未出数 / PDF 13 例" | 目录 11 份；PDF 现树 **16 例**、设备 `OK (16 tests)` | 已被设备复跑关闭块取代；不改写 |

> 口径：历史 Progress 块按"补记不改写"惯例保留，**只在其后追加取代块**；"现树事实"以最新块 + 本文件为准。
> 本轨没有把任何 `UNVERIFIED` 改成通过，也没有把 KD-80 写成 closed。

---

## 3. KD-80 状态（schema 71 中间态）

本轨实读（未运行 Gradle/KSP）：

| 项 | 实测 | 出处/命令 |
|---|---|---|
| `STUDY_DATABASE_VERSION` | **71**（工作树未提交，属批次 32 schema 轨） | `StudyDatabase.kt:139` |
| 70→71 迁移 | 已定义并注册（`object : Migration(70, 71)`） | `VisualSnapshotIdentityMigration_70_71.kt:53`；`StudyDatabase.kt:411` |
| `71.json` | **不存在** | `test -f …/71.json` → `NO`；目录 `sort -n` 最高 `70.json` |
| `SmbkSelectionPlan` | **70** / `11a8a701…` / 90 表 / 21 软引用 / CLOSURE 3 | `SmbkSelectionPlan.kt:40/:43/:538/:554` |
| 备份版本门 | 跟随 `SmbkSelectionPlan.SCHEMA_VERSION` | `BackupSnapshotTrimmer.kt:268-271` |

**后果**：v71 库一旦创建而清单未同步，备份/恢复会复现 `快照 schema=71，裁剪器只支持 schema=70` 的 fail-closed（KD-80 本体形态）。
**裁决**：`80-C1`（34/34）与 `80-C2`（清单逐名一致）已核销的是 **S=70** 口径；**不得**拿来关闭 S=71。
`80-C3` 仍 PARTIAL（JVM 仓内执行缺命令归属）、`80-C4` 仍 UNVERIFIED（34 例对视觉四表引用零断言）。
→ **KD-80 保持 open**；S=71 必须等 `71.json` 生成 + 常量回填后，按 T1 判据 §3.2 的 `S=71` 脚本与备份 34/34 重跑。

---

## 4. 契约 §8.1 LIVE 语义（复核，非新裁定）

- 批次 31 已把 §8.1 写成"超限只淘汰**未被固定、`kind ∈ {NULL, LATEST}`、且仍为 STAGING** 的中间态；**LIVE 永不淘汰**"。
- 本轨只读对照现树：`TutorVisualDao.kt:210-235` 的 evict SQL 以 `status = :stagingStatus`（`:213`）为正向条件，并二次 `NOT EXISTS` 消息/题面引用；`INITIAL` 因 `kind ∈ {NULL, LATEST}` 不在候选集。
- **本轨开工时缺、现已落码（T3，09:57）**：evict SQL 已补 `owner_request_id`/`created_at` 租约三选一
  （`TutorVisualDao.kt:210-235`）。§8.1 括号按 10:10 现树写成"已补"；若 T3 继续改主语义，以源码为准。

---

## 5. T1 判据：`1.000` 与行锚

### 5.1 `1.000` 的推导（为什么原期望错）

- `centroidDeltaPx` 的实现是 `hypot(pdfCentroid − screenCentroid)`（`VisualDrawOpsParityMetrics.kt:830-831`），即**屏栅格与 PDF 栅格之间的欧氏距离**，不是同侧平移前后的差值。
- 基线屏↔PDF 质心偏移 `|v| ≈ 0.32`；PDF 侧整块 +1 px 后读数 = `|v + (1,0)| = 1.2625`（设备 E-08 实测），**不是 1.000**。
- 因此旧断言 `assertEquals(1.0, centroidDeltaPx)` 的前提错了。**现树（批次 32 T2，10:00）**已改为
  跨栅格模型断言：P06 `1.2624767812258337`（容差 1e-6）、P07 `0.608282132823731`（容差 0.02），
  并断言判决为 `F4_GEOMETRY`（`VisualDrawOpsParityInstrumentedTest.kt:309-365/:902-905`）。
- P07 的 +1 px 用同一模型反解约 `0.608 < 0.75`，所以 1 px 位移不能靠质心抓，实际由 `bboxDev=2 > 1` 抓（这与设备读数一致）。

### 5.2 行锚回改（本轨 `grep -n` 实测）

| 位置 | 旧 | 新（10:10 现树） | 依据 |
|---|---|---|---|
| §2.2 `onlyLiveSnapshots…` | `:90` | `:102` | `grep -n "fun onlyLiveSnapshots…"` = 102 |
| §2.7 `pdfWriteFailureIsCoded` | `:447` | `:448` | `grep -n` = 448 |
| §2.8 `PARITY_GATE` | `:88` | `:100` | `grep -n "PARITY_GATE scope"` = 100 |
| §2.8 阈值版本 | `:234` | `:262` | `grep -n THRESHOLD_VERSION` = 262 |
| §2.8 `maxBbox` | `:242` | `:270` | `grep -n maxBboxDeviationPx =` = 270 |
| §2.8 `maxCentroid` | `:243` | `:271` | 271 |
| §2.8 墨量比 | `:244-245` | `:272-273` | 272/273 |
| §2.8 `maxProfileTv` | `:246` | `:274` | 274 |
| §2.8 TEXT 判决顺序 | `:658-710` | `:687-742` | `grep -n rawTextVerdict` = 687；`return PASS` = 742 |
| §2.8 负控函数 | `:298/:338/:354/:388` | `:309/:368/:450/:466/:500`（N09/N13/N10/N11/N12） | `grep -n "fun n"` |
| §2.2 元用例锚 | `:228`/`:265` | `:238`/`:275`；`inspectV70Shape` 已不在现树 | `grep -n "fun index"` / `grep -n inspectV70Shape` |
| §3 KD-80 原文引用 | `known-defects.md:2963` | `:2977` | 台账增行后现树行号（`grep -n "Reopen/关闭条件"`） |

> 行锚只对**本快照**负责；代码/台账再增删后必须重跑 `grep -n`，不得把本表当冻结常量。

---

## 6. 测试 KDoc 的 `connectedAndroidTest` 写法

批次 31 记录 §5.2 #11 点名两处；本轨 `grep -rn` 枚举又发现一处同形命令（`AssessmentItemVerificationStatusInstrumentedTest.kt:29-33`），按"改一处先枚举牵动面"一并改：

| 文件 | 改后 | 依据 |
|---|---|---|
| `TutorVisualCommitInstrumentedTest.kt:28-33` | `assembleDebugAndroidTest` + `install -r -t` + `am instrument`；E-09 实跑 9 例、现树 11 例（T3 10:07 同步，digest 负例未上设备） | `step09-persistence-structure.md:10/:74`（`OK (9 tests)`） |
| `TutorVisualCommitRepositoryInstrumentedTest.kt:28-34` | 同上；E-09 4 例、10:04 现树 8 例（+plan→command evict、租约保留、per-program/global 事务内重判 4 例未上设备） | `step09…:10/:77`（`OK (4 tests)`） |
| `AssessmentItemVerificationStatusInstrumentedTest.kt:29-33` | 同上 | 计划 `:39` 的"禁 connectedAndroidTest" |

**自查陷阱（已修）**：首轮把字面 `@Test` 写进 `TutorVisualCommitInstrumentedTest` 的 KDoc 注释，导致 `grep -c "@Test"` 从 10 变 **11**，直接破坏设备轨的计数冻结规则。已改为"测试方法计数 10"，复跑 `grep -c` = **10**、`5`。

**剩余提到但未改的 KDoc**（不是命令指令，留作后续评审）：
`GoldenRetrievalInstrumentedTest.kt:468/473` 用 `connectedDebugAndroidTest` 解释"为什么落点不是仓库树"；
`TutorVisualStructureGatesInstrumentedTest.kt:37` 已写"不用 connectedAndroidTest"。
本轨未改它们，避免扩大范围；若纪律要求字面零出现，应另立一项统一处理。

---

## 7. AUD-08 的 WAL 溯源（KD-81）

**工具现状（读码）**：`tools/split_golden/export_split_eval.py:72-73` 的 `_sha256(path)` 只读主库文件字节；
`:221-222` 的 manifest `source.db` 只有 `{file, sha256, bytes}`；全文 `grep -in "wal"` = **0 命中**。
已知限制已登记在 `tools/split_golden/README.md:86-89`（受控实验：主库 sha 同为 `946b896c…`，带 WAL 1 页 2 框 / 无 WAL 0 页）。

**本轨选择"登记 + 加检测"而非改工具**（工具改动不属本轨范围）：新增 KD-81，检测命令可复跑：

```bash
SNAP=tmp/aud08-db-5562/smart-mistake-book.db
for f in "$SNAP" "$SNAP-wal" "$SNAP-shm"; do
  [ -e "$f" ] && sha256sum "$f" && stat -c '%n %s %y' "$f"
done
# FAIL：-wal 存在且 size>0，而报告只有主库 sha；或三件 mtime 不同
```

**本轨实跑输出**：

```text
eb4d327147afa075ce28d143be5bc5aaf791eb59d202ca3ce8697b6351d8f4dc  tmp/aud08-db-5562/smart-mistake-book.db
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  tmp/aud08-db-5562/smart-mistake-book.db-wal
fd4c9fda9cd3f9ae7c962b0ddf37232294d55580e1aa165aa06129b8549389eb  tmp/aud08-db-5562/smart-mistake-book.db-shm
smart-mistake-book.db 12775424 2026-10-29 05:26:18.701635400 +0800
smart-mistake-book.db-wal 0 2026-10-29 05:38:47.396713600 +0800
smart-mistake-book.db-shm 32768 2026-10-29 08:26:14.163243400 +0800
```

解读：`-wal` 为 **0 B**（sha256 是空文件摘要），但三件 mtime 不同（05:26/05:38/08:26）——
**不是同一时刻取的快照**；因此不能据此声明"设备库确无分题数据"。工具侧把 `-wal`/`-shm`
纳入 manifest（或 checkpoint 完成证据）之前，报告 sha 只能读作"主库字节摘要"。

---

## 8. 命令与输出（本轨实跑）

### 8.1 环境与 schema

```bash
git rev-parse HEAD
# 0b4bdbbea918ba6b8c8e1f3b190b8be502ec627a
git status --porcelain | wc -l
# 713
grep -n "STUDY_DATABASE_VERSION =" core/database/src/main/kotlin/.../StudyDatabase.kt
# 139:internal const val STUDY_DATABASE_VERSION = 71
test -f core/database/schemas/.../71.json && echo YES || echo NO
# NO
grep -n "SCHEMA_VERSION\|IDENTITY_HASH\|allTables.size ==\|softReferences.size ==" \
  core/data/src/main/kotlin/.../backup/SmbkSelectionPlan.kt
# 40: const val SCHEMA_VERSION: Int = 70
# 43: const val IDENTITY_HASH: String = "11a8a701bae767a8a3c5bc810e4743a4"
# 538: require(allTables.size == 90)
# 554: require(softReferences.size == 21)
grep -n "object : Migration(70, 71)" core/database/src/main/kotlin/.../VisualSnapshotIdentityMigration_70_71.kt
# 53: object : Migration(70, 71) {
```

### 8.2 测试 KDoc 与计数

```bash
grep -c "@Test" core/database/src/androidTest/.../TutorVisualCommitInstrumentedTest.kt
# 11（10:07 现树；本轨开工时 10，期间 T3 加了 drawCommandsDigest 负例并同步 KDoc）
grep -c "@Test" core/data/src/androidTest/.../TutorVisualCommitRepositoryInstrumentedTest.kt
# 8（10:04 现树；本轨开工时 5，期间批次 32 T3 加了 3 例并同步 KDoc）
grep -rn "connectedAndroidTest\|connectedDebugAndroidTest" --include=*.kt core/database/src/androidTest core/data/src/androidTest
# 命中：AssessmentItemVerificationStatus(:29，改为禁用表述)、TutorVisualStructureGates(:37，不用)、
#       TutorVisualCommit(:29，禁用)、TutorVisualCommitRepository(:29，禁用)、
#       GoldenRetrieval(:468/:473，解释性引用，未改)
```

### 8.3 T1 判据锚点

```bash
grep -n "THRESHOLD_VERSION\` |\|maxBboxDeviationPx\` |\|maxCentroidDeltaPx\` |\|minMassRatio\|maxProfileTv\` |\|TEXT 判决顺序" \
  docs/research/2026-10-29-batch-31-device-criteria.md
# :300-:310 → :262/:270/:271/:272-273/:274/:687-742
grep -n "onlyLiveSnapshotsCanBeLinkedAndCasUsesChangedRowCounts\|pdfWriteFailureIsCoded\|VisualDrawOpsParityInstrumentedTest.kt:100\|docs/known-defects.md:2977" \
  docs/research/2026-10-29-batch-31-device-criteria.md
# :171(:102) / :286(:448) / :301(:100) / :370(:2977) / :466(:2977)
grep -n "THRESHOLD_VERSION = \|maxBboxDeviationPx = \|maxCentroidDeltaPx = \|minMassRatio = \|maxMassRatio = \|maxProfileTv = \|private fun rawTextVerdict" \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityMetrics.kt
# 262 / 270 / 271 / 272 / 273 / 274 / 687
grep -n "PARITY_GATE scope\|fun n09OnePixel\|fun n13Centroid\|fun n10WholePage\|fun n11ShapeWrong\|fun n12WholeBlock" \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityInstrumentedTest.kt
# 100 / 309 / 368 / 450 / 466 / 500
grep -c "@Test" core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityInstrumentedTest.kt
# 16
```

### 8.4 AUD-08

见 §7 的 `sha256sum` / `stat` 输出（实跑）。

### 8.5 改动规模

```bash
git diff --stat -- docs/known-defects.md docs/current/visual-generation-contract.md \
  docs/research/2026-10-29-batch-31-device-criteria.md tools/split_golden/README.md \
  core/database/src/androidTest/.../TutorVisualCommitInstrumentedTest.kt \
  core/data/src/androidTest/.../TutorVisualCommitRepositoryInstrumentedTest.kt \
  core/database/src/androidTest/.../AssessmentItemVerificationStatusInstrumentedTest.kt
# 7 files changed, 468 insertions(+), 98 deletions(-)
# 注意：其中两个测试文件的 +227/+54 行大部分是批次 32 T3 的新用例，不是本轨；
# 本轨在这两个文件里只改 KDoc 注释（各 ~6 行）。
```

---

## 9. UNVERIFIED / 未执行（如实）

- **未运行 Gradle / 未编译**：三个测试文件的 KDoc 只改注释，但整包编译与 androidTest 编译 **UNVERIFIED**。
- **未上设备**：所有 `OK (…)` 数字均为**复读** E-01/E-09/E-12 等既有证据，不是本轨重跑。
- **schema 71 未验证**：`71.json` 缺失、常量未回填；`1→71` 迁移矩阵、备份 34/34（S=71）、KD-80 的 S=71 分支均 **UNVERIFIED**。
- **AUD-08 工具未改**：`-wal` 未进 manifest；KD-81 只有登记与检测命令，没有把限制消除。
- **KD-80/KD-71/72/73/77/78** 状态全部维持原判，本轨未关闭任何 KD。
- **一致性门仍是原型门**：`product-gate=no`，不得引用为产品/发版结论。
- 真实 Provider 无凭据 → 模型质量 **UNVERIFIED**；JS 仍 `UNAVAILABLE`、不广告。

---

## 10. 与批次 31 记录的差异

- 批次 31 记录 §5.2 #11 只点名 2 个 `connectedDebugAndroidTest` KDoc；本轨 grep 枚举到第 3 个同形命令（`AssessmentItemVerificationStatusInstrumentedTest`）并一并改为 `am instrument` 形式。
- 批次 31 记录的"KD-80 证据目录不存在/34-34 未跑"已被其设备轨块取代；本轨只在 KD-80 追加现树状态，不重写历史。
- 批次 32 T2 在 09:58 把 parity 从 `calibrated-r1`/15 例/N09 只判红 改为 `calibrated-r2`/16 例/N09 跨栅格模型断言；
  本轨首轮 T1 锚点（`:242/:250…`、`:298-335`）因此立即过期，已按 10:10 现树重读回改。这是**并行轨漂移**，不是本轨回改错误。
- 同一时段（09:59）`TutorVisualSnapshotMigration_69_70InstrumentedTest` 也被改写：`inspectV70Shape` 已不在文件内，
  `onlyLive…`/两个元用例行锚整体后移（`:98→:102`、`:228→:238`、`:265→:275`）。T1 判据 §2.2 已加"现树漂移"注记，
  **旧顺序/旧断言只对批次 31 快照成立**；本轨没有替它重写语义。
- 批次 32 T3 在 10:04–10:07 给 `TutorVisualCommitRepositoryInstrumentedTest` 增加到 8 例、给
  `TutorVisualCommitInstrumentedTest` 增加到 11 例，并同步了两处 KDoc 计数；本轨首轮写的"5 / 10"因此过期，
  已按现树更正。**两处 KDoc 的最终计数含 T3 的改动**，本轨只负责删除 `connectedAndroidTest` 命令与"未执行"错误句。
- T1 判据的阈值行锚在批次 31 T4 落码后下移 8 行、又在批次 32 T2 后下移约 20 行；批次 31 T12 记录里的 `:234` 一组已过期，本轨按 10:10 现树回改。
- 本轨新增 KD-81（AUD-08 WAL 溯源）；批次 31 记录 §7 #5 只写"WAL 未纳入快照 sha"，没有编号条目。
