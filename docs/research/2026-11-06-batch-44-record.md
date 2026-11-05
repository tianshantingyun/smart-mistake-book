<!-- 体量自查：落盘后 wc -lc 实测 452 行 / 47,288 B（≤65,536 B 目标）。 -->

# 批次 44 记录（2026-11-05/06）：验收替代批——1200 项台账、十域证据与门禁实值

> **来源与边界**：本文件由**批次 44 记录员**落盘，是本次 ask 的**唯一交付写入**（`docs/research/2026-11-06-batch-44-record.md`；临时命令输出在 gitignored `build/b44-rec/`）。未改台账、未改工具、未改源码/热区、未做 git 写、未跑设备。
>
> **本轮亲手实跑（逐字见 §3.4）**：`git rev-parse/status/ls-files`、台账 1200 行逐行复算、`acceptance_init --check`、`acceptance_report`（普通/`--require-complete`）、`acceptance_lint`（普通/`--strict-warnings`）、`trace_requirements`、历史假绿夹具回放、验收工具 pytest（63 passed）、`h_compliance_gate --all` 与 5 个 `--only`、`g_security_gate --all`、`j_release_verify aab16k/signing`、`check_backup_plan_sync`、`git apply --check`、`metamorphic_checks --demo`、单测 XML 逐模块复算、两份 sha256 清单复算、源码/证据实读。
>
> **本轮未跑**：任何 Gradle（双门日志是转录）、任何 adb/设备、真实 Provider、Play Console、人工金标、arm64。§4 设备事实**全部来自 device-track 证据文件**，不是本轮运行。
>
> **状态口径**：完成 = 载体在树 + 有可指认证据（本轮实读/实跑，或证据文件可指认）；部分 = 主链在树但有明确缺口；未执行 = 无载体或无执行证据；BLOCKED = 外部条件缺失或纪律禁止；UNVERIFIED = 无法判定，**不得写通过**。

- 计划：`.zcode/plans/plan-2026-11-05-batch-44-acceptance.md`（§1 验收机制 100、§2 十域 900、§3 修复 100、§4 门禁终裁 100）
- 上一批：`docs/research/2026-11-05-batch-43-record.md` §9（KD-76 open、isolation 不定案、p09 未收敛、发版不成立）
- 证据总索引：`docs/research/evidence/2026-11-06-batch-44/00-index.md`；十域证据根 `docs/research/evidence/2026-11-05-batch-44/`；台账/终裁 `docs/acceptance/`
- 终裁草案：`docs/acceptance/adjudication-draft.md`（**草案，不是签字**）

## 0. 口径与基线

### 0.1 项数口径（1200 = 100 + 900 + 100 + 100）

本轮用 `python -I tools/ci/acceptance_init.py --check`（**exit 0**）与逐行解析双重核对：`docs/acceptance/ledger.md` 有 **1200 行数据行、ID 无缺号无重号**，域分布 = D0 100 + A–J 各 90 + FIX 100 + GATE 100。计划 §5 的 1200 与本批实账一致；**没有**批次 41–43 那种“计划数 ≠ 实列数”的缺口。

但“1200 项逐项有 verdict”只等于**枚举完整**，不等于验收通过：当前非 PASS 共 **532 行**（FAIL 21 + BLOCKED 73 + UNVERIFIED 438），且 300 行（D0/FIX/GATE）结构性未判定（§1.1/§1.4）。

### 0.2 基线事实（本轮实读/实跑）

| 项 | 值 | 证据 |
|---|---|---|
| HEAD | `542b9bbac49081aca808c616a5dc538965d1767e`（`542b9bba`） | 本轮 `git rev-parse HEAD` |
| 工作树 | `porcelain` **575**；`-uall` **4399**；暂存 **0** | 本轮 `git status --porcelain` / `-uall` / `git diff --cached --name-only \| wc -l` |
| 工作树漂移 | 总索引整理时 `-uall=4390` → 本轮 4399（+9 未跟踪产物）；`porcelain` 仍 575 | `00-index.md:81`；本轮实测 |
| 台账 | `docs/acceptance/ledger.md` sha256 **`02ba5307…e65`**，460,671 B，mtime `2026-11-05 07:31:26 +0800` | 本轮 `sha256sum` / `stat` |
| 盘上报告 | `acceptance-report.json` sha256 `244cff7b…34a0`，233,221 B；**绑定旧台账 `e139b03e…`** | 本轮 `sha256sum`；报告 `ledger_sha256` |
| DB schema | 73（`STUDY_DATABASE_VERSION = 73`） | `core/database/.../StudyDatabase.kt:142`（本轮实读） |
| 6C-2 行为面 | `ALLOWED_MASKS = linkedSetOf(9, 13, 15)`；`isCreatable = isClosed && mask in ALLOWED_MASKS`；`requireCreatable()` 拒绝 mask∉ALLOWED；0/8 刻意不在集合 | `core/domain/.../BackupSelection.kt:90,140-146,166`（本轮实读） |
| 热区工作树 | `core/domain` 2 条（1 M + 1 未跟踪锁）、`core/ui` 1 条（锁）、`feature/*` 7 条、`docs/current` **7 M**、`settings.gradle.kts` 0、Application/Root 0 | 本轮 `git status --porcelain -- …` |
| 本批证据跟踪态 | `docs/acceptance`、两个证据根 `git ls-files` 均 **0** | 本轮实测 |
| 本批提交 | **0**（共享工作树，记录员不做 git 写） | 本轮 `git diff --cached` = 0 |

### 0.3 三个“不同源”时点（本批最重要的口径事实）

1. **台账已改，报告未重生成**：台账 mtime `07:31:26`，A/F 两域各比旧报告少 1 个 PASS（旧报告 A 66 / F 56，现 A 65 / F 55），现 `A-003`、`F-050` 两行为 UNVERIFIED（`adjudication-draft.md` §B1 同判）；盘上 `verdicts.md` / `acceptance-report.json` 仍绑 `e139b03e…`、记 670/21/73/436。当前可复算值是 **668/21/73/438**，且 `acceptance_report.py` 现在 **exit 1**（101 条 `PLACEHOLDER_CMD`）。
2. **域索引计数过期**：`a-domain/00-index.md` 仍写 66 PASS、报告 exit 0；`f-domain/00-index.md` 仍写 56 PASS/22 UNVERIFIED。当前台账是 A 65/24、F 55/23。
3. **哈希清单过期**：`E-FILES-SHA256.txt` 对 `ledger.md` 校验失败（清单里的 `e139b03e…` 已不是现字节）；`DIR-SHA256.txt` 校验 **exit 1**，13 个修复环新增文件未列入、0 个内容不符。G 目录 `hashes.sha256` 30/30 OK。

**后果**：任何引用“670 PASS / 报告 exit 0 / 台账 e139b03e”的段落都是引用旧字节。本记录一律以**当前台账 `02ba5307…` + 本轮实跑**为准。

### 0.4 不可变清单与热区（本批执行口径）

- **6C-2 三项**（用户已确认）：mask=8 不提供、mask=0 禁止、派生索引恒裁。本批**只读确认 + 复用批次 43 不变量测试**，行为零改动（`BackupSelection.kt` 无 diff；`SixC2RulingInvariantTest` 在单测门 3/3 通过）。
- **热区禁改**：`core/domain`、`core/ui`、`feature/*`、`docs/current`、`settings.gradle.kts`、Application/Root；`core/export` 只做已批准 hunk。本批新增/修改载体均在 `tools/ci`、`docs/acceptance`、`docs/research`、`app/src/main/.../SchedulingSettingsScreen.kt`（后者是 P1 崩溃修复，非热区）。
- **纪律**：Gradle 只在门禁阶段串行；禁 `:app:connectedLocalFirstDebugAndroidTest`；外部项给 runbook 并标 BLOCKED。

## 1. 1200 项逐项汇总（按域）

> 本节按域汇总，不逐行贴 1200 行。每域给“计数 + 覆盖什么 + 代表项 + 不可签原因”；逐行原文在 `docs/acceptance/ledger.md`，逐行投影在 `docs/acceptance/verdicts.md`（旧 run-id）。

### 1.1 十三域计数（本轮逐行复算，非读 summary）

| 域 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED | 域裁定（本记录） |
|---|---:|---:|---:|---:|---:|---|
| D0 验收基础设施 | 100 | 0 | 0 | 0 | **100** | **UNVERIFIED**（工具可跑，自身 100 行证据列全空） |
| A 功能旅程 | 90 | 65 | 0 | 1 | 24 | **BLOCKED**（A-010 无 Provider；A-003 中断恢复无用例） |
| B 数据完整性 | 90 | 88 | 0 | 0 | 2 | **UNVERIFIED**（mask=13 partial/corrupt 两格缺夹具） |
| C 视觉与渲染 | 90 | 77 | 1 | 0 | 12 | **FAIL**（C-065 设备 XML `failures=1`） |
| D 模型链路 | 90 | 81 | 0 | 9 | 0 | **BLOCKED**（D-082…090 真实 Provider 无密钥） |
| E 复习与算法 | 90 | 80 | 0 | 0 | 10 | **UNVERIFIED**（80 个 PASS 全被 `PLACEHOLDER_CMD` 判违规） |
| F 性能与稳定 | 90 | 55 | 2 | 10 | 23 | **FAIL**（F-055/F-057 2h soak 真崩溃） |
| G 安全与隐私 | 90 | 82 | 0 | 2 | 6 | **BLOCKED**（G-087 无离线 advisory；G-088 锁文件放行态） |
| H 合规与文案 | 90 | 66 | 5 | 12 | 7 | **FAIL**（H-018/H-061/H-062 真缺口；H-052/H-066 判词过期） |
| I 可用性与无障碍 | 90 | 53 | 0 | 5 | 32 | **BLOCKED**（TalkBack 缺宿主驱动；语感/视觉残余） |
| J 发布工程 | 90 | 21 | 13 | 34 | 22 | **FAIL**（发版硬阻塞 13 条） |
| FIX 修复与加固 | 100 | 0 | 0 | 0 | **100** | **UNVERIFIED**（计划 §3 在本批未发生） |
| GATE 门禁与终裁 | 100 | 0 | 0 | 0 | **100** | **UNVERIFIED**（0 个终裁 JSON、0 条独立复核） |
| **合计** | **1200** | **668** | **21** | **73** | **438** | **不可签“批次 44 验收通过”** |

### 1.2 D0 100：机制“可跑”，自身“未验收”

- 15 类载体在树：`docs/acceptance/{ledger.md,dod.md,runbook.md,ledger.schema.json}`、`tools/ci/{acceptance_report,acceptance_lint,acceptance_init,trace_requirements,metamorphic_checks,screenshot_baseline,a11y_audit,acceptance_hash_manifest,acceptance_model}.py`、`tools/ci/acceptance_ci.patch`、`docs/acceptance/verdicts/`。
- 本轮实跑证明机制有牙齿：假绿夹具 report **exit 1**、lint 对 summary/skipped/UNVERIFIED-as-PASS **exit 1**、`--require-complete` **exit 1**、工具 pytest **63 passed**。
- 但 D0-001…100 的**证据列全部是 `-`**，verdict 全 UNVERIFIED。**机制自证 ≠ 机制被验收**；这 100 行需要逐行回填 `cmd:`/`test:` 后重跑。

### 1.3 A–J 900：每域覆盖与代表项

| 域 | 代表 PASS 面（可指认） | 代表非 PASS 项 | 证据根 |
|---|---|---|---|
| A | r1-empty/manual/shared、r2-tutor、旋转/后台/杀进程探针；app-suite 144 例中 127 评估、2 红、17 跳 | A-010 BLOCKED（Provider）；A-003 UNVERIFIED（无进程中断/Activity 重建用例）；A-050 365MiB 超时 | `a-domain/`（167 文件） |
| B | 迁移矩阵 1..73（database 319/319）；备份 48+4+3；备份四方门 PASS；mask 不变量 `{9,13,15}` | B-077/B-078（mask=13 partial/corrupt 无夹具）；1..72 每版数据行未实例化 | `b-domain/`（98） |
| C | 106 例设备用例 105 绿；Node 编译 94 passed；主题/字号蜕变；截图自一致 | C-065 `theRestoreLegAlone…` XML `failures=1`（疑似夹具零面积路径） | `c-domain/`（86） |
| D | 28 份 JVM XML + 6 份 cmd log（EXIT=0）：prompt 金样、schema、解析失败分类、预算/egress、工具环 | D-082…090 真实 Provider 全 BLOCKED；**无 00-index** | `d-domain/`（34） |
| E | 15 类/161 例 + 20 类/150 例 JVM；独立 Java 探针 298,277 次断言 OK | 80 个 PASS 的 `.cmd.log` 只记摘要命令（`<E-domain classes …>`），report 判 `PLACEHOLDER_CMD` | `e-domain/`（48） |
| F | 冷启 7+5、导出/预览、进程死亡、后台限制；soak 采样 43 轮 | F-055/F-057 soak 2504s FATAL；F-050 产品进程 PSS 不可归因；arm64 10 条 BLOCKED | `f-domain/`（191） |
| G | 静态/依赖/日志/权限/深链/intent/剪贴板面；G 门 `74 passed, 0 failed` | G-087 无离线 CVE 库；G-088 锁文件 `NOT_LANDED` 放行 + 13 未跟踪锁；6 条未验证 | `docs/acceptance/evidence/G/`（35） |
| H | 显式文案、隐私草案、data safety 草案、排版扫描 | H-018/H-061/H-062 真 FAIL（DOCX 无 `/AIGC`、KD-84、KD-85）；H-052/H-066 复跑已 PASS（判词过期）；12 条外部 BLOCKED | `docs/acceptance/evidence/H/`（115） |
| I | 4 根页语义树 STRUCTURAL=PASS；200%/暗色/横屏结构 PASS；焦点 6 标签 | TalkBack 全路径 BLOCKED（缺宿主硬件驱动）；对比度与 9 条语感 UNVERIFIED | `i-domain/`（94） |
| J | mapping 893,641 行、R8 5 例命中、16KB 静态 14/14、retrace、跨签名负控 | 13 FAIL：脏树、产物落后 HEAD 32 提交、`RELEASE_*` 4/4 缺失、临时/过期证书、AAB ABI 4≠2 且缺 `libquickjs.so`、versionCode 恒 2 | `j-domain/`（83） |

### 1.4 FIX 100 / GATE 100：两域整体未发生

- **FIX-001…100**：按 20 个主题 ×（红测试/绿修复/回归/证据/不可修登记）生成；**全部 UNVERIFIED**，没有任何“先红后绿”值守记录。树内实际有 3 处修复（§5.2），但没有登记进 FIX 域。
- **GATE-001…100**：10 域 ×（独立复核/对抗/主会话终裁/双门/设备绑定/证据归档/哈希/…）；**全部 UNVERIFIED**。`docs/acceptance/verdicts/` 只有 `README.md` 与 `_template.json`，**0 个真实终裁 JSON**；台账方法列有 64 行要求 `review:` 文件，实际存在 0 个（本轮 `ls` 实测）。
- 有反例尝试产物的域：**A/B/C/J 共 4/10**（`a-domain/adversarial-attempts.md`、`b-adversarial-mask-policy.log`、`c-adversarial-*.log`、`j-domain/00-index.md` §5）；D/F/G/H/I 未见独立对抗复核文件；E 的 298,277 次随机断言是算法探针，不等同 GATE 要求的复核。

## 2. 各域事实

> 本节只写“证据能支撑什么、不能支撑什么”。设备数字均来自证据文件（转录），不是本轮 adb。

### 2.1 A 功能旅程（65/0/1/24）

- 设备：`emulator-5584`、app `84cae864…`、boot `5a34f877…`；r1-empty 1/1、r1-manual 2/2、r1-shared 6/6、r2-tutor 7/7 均 PASS（`a-domain/00-index.md` §2）。
- app-suite：**144 tests / 127 evaluated / 2 fail / 17 skip → FAIL**（`device-track/app/app-suite.summary.txt`，本轮实读）。17 个 skip 不计通过。
- 对抗尝试 10 条（`a-domain/adversarial-attempts.md`）：8 条被门挡住，1 条抓到真缺陷（进程内 revoke 权限会杀进程），1 条**主动撤回**（100 个 id 编码被当成大页数渲染，改为 UNVERIFIED）。
- A-003 现为 UNVERIFIED：`r1-shared.xml` 只覆盖分享失败重试与批量导入，**没有**进程中断/Activity 重建恢复用例；A-009 的杀进程另算。

### 2.2 B 数据完整性（88/0/0/2）

- 迁移：`FullMigrationMatrixInstrumentedTest#everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback` 对 **1..73 每个版本**建库→Room 迁移→断言 `libraryCatalogCount == 0`；`tests=1 failures=0 errors=0 skipped=0`（`b-migration-matrix.xml`，5562）。**一个 testcase 循环 72 个源版本**，不是 72 个独立用例；台账 72 行共用该 XML，lint 的 `EVIDENCE_REUSED` 属预期。
- 备份：`b-backup-restore` 48/48、`b-backup-drill2` 4/4、`b-backup-create` 3/3；`b-backup-plan-gate.log` 四方一致。
- 本轮复跑 `check_backup_plan_sync.py` → **PASS / exit 0**，含 `v4 fixture: payload database bytes == golden`、`closureDigest/rowCountsSha256` 重算一致、`allowedMasks == [9, 13, 15]`。
- 残余：B-077/B-078（mask=13 的 partial/corrupt 无夹具）；逐版本只证 schema 可迁移，**未在 1..72 每版实例化数据行**。

### 2.3 C 视觉与渲染（77/1/0/12）

- 设备 106 例、105 绿；唯一红 = **C-065** `theRestoreLegAloneReplaysOfflineWithZeroDispatchBookings`：类级轮与单例轮各 1 次 `AssertionError: the restored document must leave ink`（`c-restore-single-rerun.xml`：`tests="1" failures="1"`，本轮实读）。
- 定位：夹具把 `snapshotInput(fill="#112233")` 写成 `M(0,0) L(10,10)`、`stroke=null` 的开放两点路径；按 fill 闭合后面积为零，Skia 不产生墨水。**可能是测试夹具缺陷，不是“恢复链整体失效”**；同类的另外两例与 V01 冻结命令回放均绿。
- 口径修正：台账骨架 C-001…030 原写 `line/rect/circle/arc/arrow/axis` 六原语（`acceptance_init.py:270-271` 生成器猜测），与 `VisualDrawOps.kt:95-139` 实际的 **Save/Concat/Clip/DrawPath/Label/Restore** 冲突；C 域已重写为六原语 × 五性质（`c-domain/00-index.md` §2）。
- 其他：Node 编译 94 passed；主题切换/字号重排蜕变 exit 0；截图 light-vs-dark、light-vs-relaunch exit 0。

### 2.4 D 模型链路（81/0/9/0）

- 28 份 `TEST-*.xml` + 6 份 `*.cmd.log`（`EXIT=0`）：prompt 金样、请求 schema、解析失败分类、预算/egress、工具环、paper-edit、knowledge quiz、重试/取消。
- **D-082…D-090 九条真实 Provider 全 BLOCKED**：无密钥；出口 `docs/acceptance/runbook.md:162`。
- **无 `00-index.md`**（总索引 G3）：34 份证据在册、台账有引用，但没有本域导航件。

### 2.5 E 复习与算法（80/0/0/10）——机械失效

- 覆盖：`core:domain` 15 类/161 例 + `core:data` 20 类/150 例 JVM；独立 Java 探针 `Random(44)` 对 6 组不变量做 **298,277** 次断言，`PROBE_RESULT=OK`、`EXIT=0`（`e-domain/00-index.md` §2）。
- **但 80 个 PASS 全部被 report 判 `PLACEHOLDER_CMD`**：`.cmd.log` 第一行是 `--tests <E-domain classes incl StudyDayMathTest>` 这类摘要，不是逐字命令；101 条违规覆盖 80 个唯一 id，全部 E 域。XML 存在，但**命令溯源不成立**。
- 这是设计选择（索引自述“method 只描述断言，不重复整条命令”）与 DoD 的 `cmd` 证据定义冲突。不能靠调低规则洗绿；要么重录逐字命令，要么降级为单类证据（PASS 不成立）。

### 2.6 F 性能与稳定（55/2/10/23）

- 主测量机 `emulator-5566`，app `b8503f04…`；冷启 7 轮 p50 **3302 ms** / p95 3939 ms，热启 p50 232 ms；批量导出 `elapsedMs=516/553/538`；单题 A4 导出记录 820/487/597 ms；PDF 预览 0.359/0.332/0.378 s。
- 内存：冷启稳态 `TOTAL PSS=156,818 KB`；批量导出 PSS 峰值 47.5–54.4 MB；产品 UI 导出测试宿主 314–317 MB（**不能**当产品导出内存）。
- **2h soak FAIL**：第 39 轮/2504 s，monkey 点复习安排滑杆端点，`SchedulingOptions.copy` 收到 0.7..0.97 外的值 → `IllegalArgumentException` @ `StudyExperienceRepository.kt:737`，进程 25402 FATAL；脚本重启后跑到 2723 s 人工停止。`soak.cmd.log`：`DURATION_ACTUAL_S=2723`、`CYCLES=43`、`PSS_MAX_KB=197048`、`CRASHED=1`、`FIRST_CRASH_ELAPSED_S=2504`。
- 口径：模拟器只作基线，不把 3302 ms 对照 `PerformanceTargets.kt:18` 的 2000 ms 真机目标判产品 FAIL；F-050 产品进程 PSS 不可归因。

### 2.7 G 安全与隐私（82/0/2/6）

- G 门 `--all`：**74 passed / 0 failed / exit 0**（本轮实跑）；覆盖备份、剪贴板/截图、深链、egress、文件暴露、intent、日志、权限、明文/依赖、密钥。
- G-087 BLOCKED：无离线 advisory 库、未联网取证 → **不得称“无漏洞”**。
- G-088 BLOCKED：`check_lockfiles` 处于 `NOT_LANDED` 放行态、13 个未跟踪锁 → 不计通过。
- 6 条 UNVERIFIED：崩溃 trace 不含密钥（未触发采集）、真实外发（无 Provider）、分享 URI 穿越（源码守卫已断言但无法执行 Kotlin 构造）、配置页截图、剪贴板敏感标志（代码未设 `EXTRA_IS_SENSITIVE`，需产品裁定）、`verification-metadata.xml` 不存在。

### 2.8 H 合规与文案（66/5/12/7）

- 台账 5 条 FAIL：`H-018`（DOCX 无文档级 `/AIGC`；KD-84 部分页打印丢标；KD-85 模型改卷文本漏标）、`H-052`、`H-061`（KD-84）、`H-062`（KD-85）、`H-066`。
- 本轮 `--only` 复跑：**H-052 exit 0（PASS）**、**H-066 exit 0（PASS）**、**H-018/H-061/H-062 exit 1（FAIL 维持）**。即 5 条里 2 条判词过期，3 条真缺口。
- `--all` 的原始 FAIL 数**多于**台账 5 条（H-008/H-024/H-025/H-036/H-039…H-043/H-051/H-053/H-075…H-081/H-089/H-090 等），因为台账把外部依赖映射为 BLOCKED、把语感映射为 UNVERIFIED。**门的 raw verdict ≠ 台账 verdict**，引用必须带口径。
- 12 条 BLOCKED：Play/隐私政策 URL/青少年模式/关于页/CAC 备案；7 条 UNVERIFIED：人工视觉、i18n 抽取与 RTL、日期 Locale、locale 切换、中文语感。

### 2.9 I 可用性与无障碍（53/0/5/32）

- 设备探针：4 根页语义树 `STRUCTURAL=PASS`、`VIOLATIONS=[]`；fontScale 2.0 / 暗色 / 横屏结构 PASS；焦点 6 个不同标签 PASS；对比度 `UNVERIFIED`（未给颜色）。
- **TalkBack BLOCKED**：服务已绑定、`touchExplorationEnabled=true`，但用例停在宿主硬件输入驱动握手（`The opt-in emulator hardware driver did not acknowledge 1`）；仓内无该驱动。朗读语感按计划 §6 永久 UNVERIFIED。
- 32 条 UNVERIFIED 中 9 条（I-082…090）是人类语感/审美残余；其余为对比度像素、裁剪/溢出、焦点顺序、中文 IME、传感器旋转等自动化不可判定项。

### 2.10 J 发布工程（21/13/34/22）

- 产物：release APK `724ce718…`（155,816,991 B）、AAB `d0734105…`（mtime **2026-09-30**）、mapping `c5b2d170…`（893,641 行）。
- 签名：`j-signing.log` `RELEASE_KEY_ALIAS/KEY_PASSWORD/STORE_FILE/STORE_PASSWORD` **4/4 缺失**、`tracked_keystores=[]`；APK 证书 `CN=WS8 Ephemeral Local Only, OU=NotForRelease`，有效期至 **2026-11-05 11:03**；AAB 证书 `CN=ZCode Release Smoke`，`A9:E7:6C:AD…`，**2026-10-30 已过期**。
- 16KB/AAB：本轮复跑 `j_release_verify.py aab16k --aab … --abis arm64-v8a,x86_64 --require-libs libquickjs.so` → **exit 1**，两条错误：`ABI set [arm64-v8a, armeabi-v7a, x86, x86_64] != abiFilters [arm64-v8a, x86_64]`、`required native library missing: libquickjs.so`。
- 版本：四份产物 versionCode 全为 2；来源只有 `app/build.gradle.kts:33-34` 手写两行。
- 13 FAIL：J-001/J-002（脏树、产物落后 HEAD 32 提交）、J-020/J-021/J-024/J-025/J-027（生产钥匙/指纹/同一身份/有效期/文档契约）、J-033/J-034/J-041（AAB ABI/缺 quickjs/含 32 位）、J-048/J-051/J-052（vc 不单调/无生成机制/历史 AAB 不可比）。
- 34 BLOCKED：Play Console 账号、素材、data safety、封闭测试、arm64/16KB 设备、成对 vc 产物、回滚包。

### 2.11 6C-2 行为面（本轮实读）

- `ALLOWED_MASKS = linkedSetOf(9, 13, 15)`（`BackupSelection.kt:166`）；`isCreatable = isClosed && mask in ALLOWED_MASKS`（`:90`）；`requireCreatable()` 对 `!isClosed` 与 `mask !in ALLOWED_MASKS` 分别 `require`（`:140-146`）。
- `BackupSelection.kt` 在工作树**无 diff**（`git diff --stat` 空）；行为未漂移。
- 不变量测试 `SixC2RulingInvariantTest` 在单测门 **3 tests / 0 failures / 0 errors**（`core/data/build/test-results/.../TEST-…SixC2RulingInvariantTest.xml`，timestamp `2026-11-04T20:29:17Z`），但本批**没有新增**不变量测试（计划只要求“只可加”，实际未加）。

## 3. 门禁实值

> 本节只写有日志/有输出的运行。标“（本轮实跑）”的是我亲手执行；双门是**转录** `build/batch44-{unit,strong}.log`（`build/` gitignored，本轮未跑 Gradle）。

### 3.1 双门（转录 + 本轮 XML 复算）

| 门 | 命令/日志 | 结果 |
|---|---|---|
| 单测门 | `build/batch44-unit.log`（2026-11-05 04:24–04:30 +0800） | phase1 `GRADLE_EXIT=0`、`158 actionable tasks: 11 executed, 147 up-to-date`；phase2 强制 `:core:data:testDebugUnitTest --rerun` → `GRADLE_EXIT_PHASE2=0`、`31 tasks: 1 executed` |
| 单测逐例（本轮复算） | 11 个模块 `build/test-results` XML 求和 | **4028 tests / 0 failures / 0 errors**；core/data 1647、database 248、domain 708、export 172、model 644、ui 52、capture 148、library 73、profile 6、review 72、tutor 258；`core:markdown` NO-SOURCE |
| 单测覆盖缺口 | 日志任务枚举 | **不含 `:app:testDebugUnitTest`**（grep 0 命中）；`app/build/test-results` 无 `SchedulingRetentionSliderValueTest` XML |
| 强门 | `build/batch44-strong.log`（04:34:33–04:36:14） | 6 次 `--rerun` androidTest Kotlin 编译 + `:app:assembleLocalFirstDebug --continue`，逐任务 `exit: 0`；末行 **`GATE RESULT: PASS at 2026-11-05 04:36:14`** |
| 强门判读 | 同上 | `226 actionable tasks: 3 executed, 223 up-to-date` → **增量绿，不是干净单跑**；HEAD `542b9bba`、dirty 575 |

### 3.2 验收三件套 + 回放 + 自测（本轮实跑）

| # | 命令 | 结果 |
|---|---|---|
| 1 | `python -I tools/ci/acceptance_init.py --check` | **exit 0**（1200 行无漂移） |
| 2 | `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md` | **exit 1**；`ledger_sha256=02ba5307…`；`counts={PASS:668, FAIL:21, BLOCKED:73, UNVERIFIED:438}`；`state=FAIL`；`violations=101`（全 `PLACEHOLDER_CMD`，80 个唯一 id，全 E 域）；`pass_with_review=0` |
| 3 | 同命令 `--require-complete` | **exit 1**（NOT_ACCEPTANCE_COMPLETE） |
| 4 | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md` | **exit 0**；`violations=0`、`warnings=29`（全 `EVIDENCE_REUSED`） |
| 5 | 同命令 `--strict-warnings` | **exit 1**；29 条 `EVIDENCE_REUSED` 升为违规 |
| 6 | `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md` | **exit 1**；`gaps=60`（50 `MISSING_EVIDENCE_PATH` + 10 `DEVICE_EVIDENCE_MISSING`）；60 行**全部 UNVERIFIED**（本轮逐 id 对账） |
| 7 | 历史假绿回放：`acceptance_report.py --ledger fake_green_batch41/42 --partial` | 两例均 **exit 1**（41：`PASS_NEEDS_TWO_INDEPENDENT_EVIDENCE`/`CMD_LOG_SKIP_MARKER`；42：`SUMMARY_AS_EVIDENCE`/`UNVERIFIED_AS_PASS`） |
| 8 | `python -I -m pytest tools/tests/test_acceptance_{report,lint,init}.py test_trace_requirements.py test_j_release_verify.py -q` | **63 passed in 19.53s** |
| 9 | `python -I tools/ci/metamorphic_checks.py --demo` | **exit 0** |
| 10 | `git apply --check tools/ci/acceptance_ci.patch` | **exit 0**（提案可落地，未应用） |
| 11 | `sha256sum -c E-FILES-SHA256.txt` | 34/35 OK；**`docs/acceptance/ledger.md` FAILED**（清单绑旧 `e139b03e…`） |
| 12 | `acceptance_hash_manifest.py --verify DIR-SHA256.txt` | **exit 1**；13 个修复环新增文件 `UNLISTED_FILE`、0 个内容不符 |

### 3.3 领域门（本轮实跑）

| 门 | 命令 | 结果 |
|---|---|---|
| B 备份四方门 | `python -I tools/ci/check_backup_plan_sync.py` | **PASS / exit 0**；v4 golden 字节/摘要/行数一致、`allowedMasks == [9, 13, 15]` |
| G 安全门 | `python -I tools/ci/g_security_gate.py --all` | **74 passed / 0 failed / exit 0** |
| H 合规门（全量） | `python -I tools/ci/h_compliance_gate.py --all` | **exit 1**；H-018/H-061/H-062 命中 FAIL，H-052/H-066 已转 PASS；另有外部/语感项 raw FAIL（口径见 §2.8） |
| H 单条复跑 | `--only H-052 / H-066 / H-018 / H-061 / H-062` | **0 / 0 / 1 / 1 / 1** |
| J AAB 16KB | `j_release_verify.py aab16k --aab … --abis arm64-v8a,x86_64 --require-libs libquickjs.so` | **exit 1**（ABI 4≠2；缺 `libquickjs.so`） |
| J 签名 | `j_release_verify.py signing --root . --apk … --aab …` | **exit 0**，但 `env_missing` 4/4、`tracked_keystores=[]` |

### 3.4 本轮亲手实跑的只读检查（逐字命令 + 关键输出）

```text
git rev-parse HEAD                          -> 542b9bbac49081aca808c616a5dc538965d1767e
git status --porcelain | wc -l              -> 575
git status --porcelain -uall | wc -l        -> 4399
git diff --cached --name-only | wc -l       -> 0
git ls-files docs/acceptance | wc -l        -> 0
git ls-files docs/research/evidence/2026-11-06-batch-44 | wc -l -> 0
git ls-files docs/research/evidence/2026-11-05-batch-44 | wc -l -> 0
sha256sum docs/acceptance/ledger.md         -> 02ba5307…e65（460,671 B）
```

- 台账复算脚本（逐行 `split('|')`，按 `ID` 前缀归域）：`rows 1200`、`PASS 668 / FAIL 21 / BLOCKED 73 / UNVERIFIED 438`、逐域与 §1.1 一致、`dup ids 0`。
- 报告 violations：`101`、`codes={'PLACEHOLDER_CMD': 101}`、`unique ids=80`、`domains={'E': 80}`、`pass_with_review=0`、`pass_with_two_kinds=668`。
- 单测 XML 复算：`TOTAL 4028 fail 0 err 0`；模块明细见 §3.1。
- 6C-2：`BackupSelection.kt:90/140-146/166` 实读；`git diff --stat -- BackupSelection.kt` 空。
- C-065：`c-restore-single-rerun.xml` 头 `tests="1" failures="1" errors="0" skipped="0"`。
- F soak：`soak.cmd.log` `CRASHED=1`、`FIRST_CRASH_ELAPSED_S=2504`；`soak-crash-logcat.txt:2-9` FATAL @ `SchedulingSettingsScreen.kt:74` → `StudyExperienceRepository.kt:737`。
- J：`j-aab16k.log` 两条 errors；`j-signing.log` 4/4 missing；`j-apksigner.log` `CN=WS8 Ephemeral Local Only`；`j-version-artifacts.log` 四份 `versionCode='2'`。
- 证据哈希：G `hashes.sha256` 30/30 OK；`E-FILES-SHA256.txt` 34/35；`DIR-SHA256.txt` exit 1 / 13 unlisted。
- 终裁文件：`ls docs/acceptance/verdicts/*.json` → 仅 `_template.json`；台账 `review:` 方法行 64 条。

### 3.5 门禁判读边界

- 双门是**增量绿**：单测 phase1 11 executed、phase2 1 executed；强门 3/226 executed。不能证明“当前源码全量重新可测”；被 up-to-date 掩盖的编译/测试红不可见。
- 单测门**不含 `:app`**：P1 修复的 `SchedulingRetentionSliderValueTest` 无任何执行证据。
- `acceptance_report.py` 现在 exit 1 是**机制正确工作**（抓到 E 域占位命令），不是工具坏了；不能为了让它 exit 0 而放宽规则。
- `trace` 的 60 gap 全部落在 UNVERIFIED 行，说明“缺证据”与“未判定”一致，不是 PASS 行缺证据。

## 4. 设备证据（仅证据文件可证；本轮未跑 adb）

### 4.1 产物身份（跨轨不可合并）

| 轨 | app APK sha256 | 设备 | 时点 |
|---|---|---|---|
| A / C / G | `84cae864…dab5b` | 5584（boot `5a34f877…`） | 11-04 08:39–13:35Z；G 11-05 01:07–01:11+08 |
| F / I | `b8503f04…df5ac` | 5566（F，boot `5e1a5d59…`）/ 5584（I） | 11-04 15:23–16:38 / 19:01Z |
| 设备复基线（device-track） | `f534b0dd…7f7de` | 5584（boot `831e5d06…`） | 11-04 20:43–21:52Z |
| J（release） | `724ce718…342b44`（AAB `d0734105…`） | 5584 / 5562 | 11-05 03:19–03:40+08 |

- device-track 8 个包本地 sha 与设备端 `sha256sum` **逐包相等**（`witness-device-apk-sha.log`），boot_id 全程未变 → “设备上跑的就是这些字节”成立。
- **不可合并**：`84cae864` / `b8503f04` / `f534b0dd` 是三次不同构建；把 A 的 65 PASS 与 F 的 55 PASS 相加成“120 通过”无效。i-domain 与 device-track 拍同一批页面，但 APK 不同、文件哈希全不同 → 两次独立运行。

### 4.2 device-track 结果（`device-track/00-index.md` + 各 summary）

| label | tests | evaluated | fail | skip | verdict |
|---|---:|---:|---:|---:|---|
| smoke-root | 10 | 10 | 0 | 0 | PASS |
| app-suite | 144 | 127 | 2 | 17 | **FAIL** |
| database | 319 | 319 | 0 | 0 | PASS（含 1..73 迁移矩阵） |
| export | 30 | 30 | 0 | 0 | PASS |
| capture | 44 | 44 | 1 | 0 | **FAIL** |
| library | 109 | 109 | 1 | 0 | **FAIL** |
| tutor | 122 | 122 | 2 | 0 | **FAIL** |
| data | 94 | 87 | 1 | 6 | **FAIL** |
| data-backup-suite | 85 | 84 | 0 | 1 | FAIL（recorder 口径；skip 由两阶段杀进程覆盖） |

- R1–R7 七条红：R1 通知权限用例缺外部 `pm revoke` 前置（重跑 2/2 PASS，装置缺陷）；R2 第 4 个 program 应 BUSY（批次 40–43 同报文，已知红）；R3 文案期望旧串；R4/R5 已知红（`docs/known-defects.md:1102`）；R6 滚动性能用例 2/2 红、**未定性**；R7 `BatchImportRepositoryInstrumentedTest` 进程崩溃（后台协程晚于 `@After close()` 触库，**测试隔离缺陷**，不是产品 DB 结论）。
- 15 例 `VisualJsIsolationInstrumentedTest` 因低负载门未传/INVALID 跳过，**不计通过**；TalkBack 全路径 BLOCKED（缺宿主硬件驱动）。

### 4.3 探针（非 instrument）

- 语义树 ×4 根页 `STRUCTURAL=PASS`、`VIOLATIONS=[]`；200%/暗色/横屏结构 PASS；焦点 6 标签 PASS；对比度 UNVERIFIED。
- 截图：沉浸态两次 `MAE=0.0/changed=0`；非沉浸态 `changed_ratio=0.00216`（状态栏时钟超 0.001 阈值）；跨 APK 基线 `MAE=0.408/0.00366`，只登记不判回归。
- 冷启 5 轮 `4596/2778/2824/2752/2751 ms`（p50 2752）；`TOTAL PSS=172,851 KB`；无 app ANR；release 包安装成功、`versionCode=2/0.2.0-offline`、冷启 945 ms、临时证书。

### 4.4 设备结论边界

- “设备全绿”不成立：4 个套件 FAIL、7 条复现红（R1–R7）、17+6 skip、TalkBack BLOCKED、isolation 15 skip。
- “迁移/备份全绿”只在 `database 319/319` + `data-backup-suite 84/84` 口径成立；不含逐版本数据行、365MiB scale（未跑）。
- 真机/arm64/16KB 设备：BLOCKED（宿主 x86_64，`ps16k_arm64` 不可起）。

## 5. 复核与修复

### 5.1 本轮发现

| # | 发现 | 证据 | 严重度 | 处置 |
|---|---|---|---|---|
| S1 | 台账已改、报告未重生成：670/436 → 668/438，report exit 1 | 本轮 `sha256sum` + report 实跑；`A-003`/`F-050` 现为 UNVERIFIED | 高（口径） | 以当前台账为准；报告/verdicts 需按新 run-id 重生成（未做） |
| S2 | E 域 80/80 PASS 违反 `PLACEHOLDER_CMD` | `e-domain-domain-jvm.cmd.log:1`；report 101 违规/80 id | 高（证据） | E 域记 UNVERIFIED；重录逐字命令后才能改判 |
| S3 | FAIL 集不稳定：H-052/H-066 已复跑 PASS | 本轮 `--only` exit 0；台账仍 FAIL | 高（判词） | 登记“判词过期”，不原样开修复单 |
| S4 | F-055/F-057 崩溃真，修复在树但未跑 | `soak-crash-logcat.txt:2-9`；`SchedulingSettingsScreen.kt:39-53` 已夹回 `[0.7,0.97]`；无 app test XML | 高（P1） | 修复保留；补 `:app:testDebugUnitTest` 前不得称已修 |
| S5 | C-065 红疑似夹具零面积 | `c-restore-single-rerun.xml` failures=1；路径 `M(0,0) L(10,10)` + fill-only | 中（定性） | 交 FIX 域；不判“恢复整体失效” |
| S6 | 300 行结构性未判定（D0/FIX/GATE） | 台账逐行复算 | 高（流程） | 不签批次；D0/FIX/GATE 需回填 |
| S7 | 独立复核 = 0 | 0 个 verdict JSON；`pass_with_review=0` | 高（DoD） | 任何 PASS 只是机械审计，不是 DoD §1.5 |
| S8 | 证据哈希清单过期 | `E-FILES` 1 处 FAILED；`DIR` 13 unlisted | 中（可追溯） | 修复环后重生成清单（未做） |
| S9 | 域索引计数过期（A 66、F 56、报告 exit 0） | `a-domain/00-index.md` §5；`f-domain/00-index.md` §3 | 中（口径） | 引用以台账为准；索引待重写 |
| S10 | 双门增量绿且不含 `:app` | `batch44-unit.log` / `batch44-strong.log` | 中（门禁） | 记录“非全量”；发版前需干净全量 |
| S11 | 4 个 APK 跨轨不可合并 | §4.1 | 中（口径） | 所有结论带 APK sha |
| S12 | 计划 §0 有 7 条前提错误/需改写 | `research-crosscheck.md` §1 | 中（研究） | 见 §5.3 |
| S13 | 6C-2 只读确认、未加新不变量测试 | `BackupSelection.kt` 无 diff；`SixC2RulingInvariantTest` 3/3 | 低（覆盖） | 行为未漂移；测试沿用批次 43 |
| S14 | 发版就绪不成立，7 条 P0 | §7.1 | 高（发版） | 判“不就绪” |
| S15 | 证据未跟踪、日志被忽略 | 三根 `git ls-files=0`；`.gitignore:31` | 高（归档） | 提交前决定证据入库策略 |

### 5.2 三处树内修复与登记状态

| 修复 | 落点 | 消灭的具体失败 | 登记状态 |
|---|---|---|---|
| 复习安排滑杆夹回 band | `app/src/main/.../SchedulingSettingsScreen.kt:39-53`（`RETENTION_MIN/MAX` + `retentionFromSlider`）+ `app/src/test/.../SchedulingRetentionSliderValueTest.kt` | Float→Double 端点越出 0.7..0.97 → `SchedulingOptions` `require` 抛 FATAL（soak 2504s） | **未登记 FIX 域、未执行测试**（单测门不含 `:app`） |
| H-052 免责声明文案对齐 | `docs/ai-content-disclosure-copy.md:9` | 文案称 PDF 未写隐式标识，与已注入 `/AIGC` 冲突 | 台账仍 FAIL；本轮 `--only` 已 PASS |
| H-066 诊断包失败文案不拼接异常 | `app/.../SecondaryScreens.kt:826` | 把 `failure.message` 拼进用户可见文案 | 台账仍 FAIL；本轮 `--only` 已 PASS |

> 这三处都**没有**“先红后绿”记录，也没有进入 `FIX-001…100`。FIX 域全 UNVERIFIED 是真实状态，不是漏填。

### 5.3 研究交叉复核（计划 §0 七条）

`research-crosscheck.md`（本轮实读）逐条判定：

- A **部分成立须改**：16KB 强制点是 **2027-02-01**，不是 2026；检查面还缺 `GNU_RELRO`、`zipalign -c -P 16`、AAB `PAGE_ALIGNMENT_16K`。
- B **半对半错**：官方只点名 Compose Preview Screenshot Testing（experimental）+ 第三方工具，**没有**点名 Paparazzi/Roborazzi。
- C **前提事实错误**：本批前 `tools/ci` 无截图/像素差脚本；本批新建 `screenshot_baseline.py`，“不引新依赖”仍成立（PIL 已有先例）。
- D **结论成立、构件名错**：官方是 `ui-test-junit4-accessibility` + Compose 1.8.0+ `enableAccessibilityChecks()`，不是 espresso-accessibility。
- E **成立**：两篇 arXiv 编号与主题对得上。
- F **来源真实但不可采用**：FlakeEcho 需 EdXposed + API 30 + AGP 8.0，本仓 AGP 9.3.0，只能当方法论。
- G **日期/标准号成立，条款 UNVERIFIED**：GB 45438-2025 实施 2025-09-01；`/AIGC` 只对 PDF 成立，条款原文未取得。

### 5.4 复发门（“若再出现如何被门抓住”）

| 失败形态 | 抓住它的门 | 强度 |
|---|---|---|
| summary 当证据、UNVERIFIED 标 PASS、跳过门计通过 | `acceptance_lint.py` 三类违规；假绿夹具 41/42 均 exit 1 | 强（本轮实跑） |
| PASS 只有一类证据 | `acceptance_report.py` `PASS_NEEDS_TWO_INDEPENDENT_EVIDENCE`；668/668 有两类 | 强 |
| 占位命令冒充可复现 | `PLACEHOLDER_CMD`（本轮抓到 E 域 80 行） | 强（本轮实跑） |
| 证据文件不存在/设备未绑定 | `MISSING_EVIDENCE_PATH` / `DEVICE_EVIDENCE_MISSING`（60 gap） | 强（本轮实跑） |
| 终裁自证 | `acceptance_report` 要求 reviewer ∉ authors + 绑 ledger hash；`acceptance_lint` 拒 self-review | 强（单测覆盖） |
| 证据被替换 | `acceptance_hash_manifest.py --verify`（缺文件/哈希不符/清单外多文件） | 中（本轮 DIR 清单已抓 13 unlisted） |
| 6C-2 行为漂移 | `SixC2RulingInvariantTest` 3 例 + `allowedMasks` 精确相等 + 四方门 | 强（3/3 绿） |
| 发版产物陈旧/临时证书 | `j_release_verify` identity/signing/aab16k/version 负控 | 强（本轮 aab16k exit 1） |

## 6. 遗留、阻塞与 UNVERIFIED

### 6.1 阻塞（外部或需主会话裁定）

1. **真实 Provider**（D-082…090、A-010、G-036）：无密钥；出口 `docs/acceptance/runbook.md:162`。
2. **生产签名/AAB/Play Console**（J-017…090 多数）：`RELEASE_*` 4/4 缺失；现有 APK 证书 2026-11-05 11:03 到期、AAB 证书 2026-10-30 已过期；AAB 陈旧且缺 `libquickjs.so`。
3. **arm64/16KB 真机**（F/J 各 10 条）：宿主 x86_64，arm64 AVD 不可起。
4. **人工金标**（AUD-08）：无标注员/裁定者；批次 43 门值 FAIL 维持。
5. **TalkBack 全路径**（I-007 等）：缺宿主硬件输入驱动。
6. **G-087 离线 CVE**：无 advisory 库、未联网取证 → 不得称“无漏洞”。
7. **G-088/KD-72 锁文件**：`NOT_LANDED` 放行 + 13 未跟踪锁。
8. **H 外部项**（H-024/H-025/H-036/H-039…H-043/H-051/H-053/H-081）：隐私政策 URL、Play 账号、青少年模式、CAC 备案、关于页（热区）。
9. **J 成对 vc/回滚**（J-055…J-069）：无成对产物与同钥匙。
10. **批次 43 未决项**：KD-76 open、isolation 不定案、p09 未收敛——本批**未改变**，不得用本批 PASS 数回填。

### 6.2 UNVERIFIED（不得写通过）

- **D0 100 / FIX 100 / GATE 100**：证据列空/无红绿记录/无终裁 JSON。
- **E 域 80 个 PASS**：`PLACEHOLDER_CMD` 机械失效。
- **A-003**：无进程中断/Activity 重建用例；**F-050**：产品进程 PSS 不可归因。
- **C-065**：失败真，但“恢复整体失效”未证；夹具缺陷待裁定。
- **H-052/H-066**：判词过期，需重判并回填。
- **I 域 32 条**：对比度像素、裁剪/溢出、焦点顺序、中文 IME、传感器旋转、9 条人类语感。
- **G 域 6 条**：崩溃 trace、真实外发、分享 URI、配置页截图、剪贴板敏感标志、依赖校验元数据。
- **F 域 23 条**：arm64 10 条 BLOCKED 之外的模拟器不可判定项（首帧真机口径、产品 PSS、低存储等）。
- **发版**：干净树全量构建、构建确定性、AAB 重新生成、Play 上传、生产签名、16KB 运行时（arm64）。
- **人类残余**：屏幕阅读器朗读语感、中文文案语感、视觉审美、横屏信息密度——永久 UNVERIFIED，不由本记录签字。

### 6.3 本记录明确不做的裁定

- 不签“批次 44 验收通过”；不签任何域 PASS；不把 668 PASS 当产品放行。
- 不把 H-052/H-066 的 PASS 写回台账（未做 git 写/台账写）。
- 不把 C-065 判成“产品恢复链坏了”；不把 R7 判成“产品 DB 长连接缺陷”。
- 不把批次 44 的设备绿写成 KD-76/isolation/p09 的解决。

### 6.4 下一批建议

1. **先冻结台账**：以 `02ba5307…` 为基线，重跑 report/lint/trace，重生成 `verdicts.md` + `acceptance-report.json`，记录新 run-id；在此之前不签任何行。
2. **E 域重录逐字命令**：让 report `violations=0`；否则 E 域 80 行永久 UNVERIFIED。
3. **H 域重判**：H-052/H-066 改 PASS 并补门输出；H-018/H-061/H-062 进 FIX 域（DOCX `/AIGC`、KD-84、KD-85）。
4. **F-055/F-057**：把 `:app:testDebugUnitTest` 纳入单测门，跑 `SchedulingRetentionSliderValueTest`，补红→绿证据；再跑 2h soak。
5. **C-065**：修夹具（有面积路径或加 stroke）或证产品缺陷；先红后绿。
6. **D0/FIX/GATE 回填**：D0 逐行跑声明命令；FIX 每项补红绿；GATE 每域 1 份独立复核 + 1 条对抗反例，产出 `docs/acceptance/verdicts/*.json`。
7. **证据归档**：决定 `docs/acceptance` 与两个证据根是否入库；`*.log` 被 `.gitignore:31` 忽略，干净克隆无法复算。
8. **发版**：先解决 7 条 P0（§7.1），再谈“就绪”。

## 7. 提交清单建议（每块 ≤6 路径；排除热区/锁文件/build）

> **本记录员未做任何 git 写**；以下只是建议块。**不得提交**：`build/*`（含双门日志）、14+ 个 `gradle.lockfile`、`.mimosa/`、热区文件（`core/domain/TutorJudgedReview.kt`、`core/ui/*`、`docs/current/*` 7 M、`feature/review/...Test.kt`）。

| 块 | 路径（≤6） | 备注 |
|---|---|---|
| B1 | `tools/ci/acceptance_report.py`、`acceptance_lint.py`、`acceptance_init.py`、`acceptance_model.py`、`ledger.schema.json`（在 docs/acceptance） | D0 核心三件套 + 模型 |
| B2 | `tools/ci/trace_requirements.py`、`acceptance_hash_manifest.py`、`acceptance_ci.patch` | 追溯/哈希/CI 提案 |
| B3 | `tools/ci/metamorphic_checks.py`、`screenshot_baseline.py`、`a11y_audit.py`、`a11y_probe.py` | 蜕变/截图/无障碍工具 |
| B4 | `tools/ci/am_instrument_recorder.py`、`a_journey_probe.py`、`j_release_verify.py`、`g_security_gate.py`、`h_compliance_gate.py`、`check_backup_plan_sync.py` | 域门与记录器 |
| B5 | `tools/tests/test_acceptance_report.py`、`test_acceptance_lint.py`、`test_acceptance_init.py`、`test_trace_requirements.py`、`test_j_release_verify.py`、`test_am_instrument_recorder.py` | 工具单测（63 passed） |
| B6 | `docs/acceptance/ledger.md`、`dod.md`、`runbook.md`、`verdicts/README.md`、`verdicts/_template.json`、`adjudication-draft.md` | 台账/DoD/runbook/草案（ledger 是 460KB，需确认入库策略） |
| B7 | `docs/research/evidence/2026-11-06-batch-44/00-index.md`、`acceptance-report.json`、`acceptance/*.json`（6 个） | 总索引 + 验收机制证据 |
| B8 | `docs/research/evidence/2026-11-06-batch-44/device-track/00-index.md`、`source-snapshot.txt`、`apk-shas.txt`、`witness-device-apk-sha.log` | 设备身份/绑定 |
| B9 | `docs/research/evidence/2026-11-05-batch-44/a-domain/00-index.md`、`adversarial-attempts.md`、`b-domain/00-index.md`、`c-domain/00-index.md`、`e-domain/00-index.md`、`f-domain/00-index.md` | 域索引 1 |
| B10 | `docs/research/evidence/2026-11-05-batch-44/j-domain/00-index.md`、`docs/research/2026-11-06-{batch-44-baseline,metamorphic-relations,release-acceptance,screenshot-baseline,a11y-acceptance}.md` | 域索引 2 + 研究件 |
| B11 | `docs/research/evidence/2026-11-06-batch-44/research-crosscheck.md`、`E-FILES-SHA256.txt`、`DIR-SHA256.txt`、`docs/research/2026-11-06-batch-44-record.md` | 复核 + 清单 + 本记录 |
| — | **热区，另裁**：`core/domain/TutorJudgedReview.kt`、`core/ui/*`、`docs/current/*`、`feature/review/...Test.kt`、`app/.../SchedulingSettingsScreen.kt`（P1 修复，需授权/单独提交） | 计划 §6 明禁或需授权 |
| — | **明确不入**：`build/batch44-{unit,strong}.log`（gitignored）、`docs/acceptance/evidence/{G,H}` 中的 `.mimosa` hook-state（含本机 Temp 路径）、`docs/acceptance/evidence/H/acceptance_report.json`（旧台账 `d787ab4f…`，596/8/52/544，不得混引） | 逐路径排除 |

### 7.1 发版就绪判定：**不成立**

七条 P0（任一成立即不就绪）：

1. **用户可达崩溃**：复习安排滑杆端点 → `SchedulingOptions` 越界 FATAL（soak 2504s）；树内已修但**未执行测试**。
2. **工作树不干净/产物落后**：`porcelain=575`、`-uall=4399`；release APK revision 落后 HEAD 32 提交。
3. **生产签名缺失**：`RELEASE_*` 4/4 缺失、`tracked_keystores=[]`。
4. **签名身份不一致/临时证书**：APK `CN=WS8 Ephemeral Local Only`（2026-11-05 11:03 到期）；AAB 证书已过期；AAB 与 APK 指纹不同。
5. **AAB 内容不合格**：ABI 4≠2、缺 `libquickjs.so`（本轮 aab16k exit 1）。
6. **versionCode 恒 2、无生成机制**：四份产物全为 2。
7. **AIGC 隐式标识未覆盖交付格式**：H-018/H-061/H-062（DOCX 无 `/AIGC`、KD-84、KD-85）。
8. **质量门未绿**：report exit 1、trace exit 1、300 行未判定、0 独立复核。

**结论：不成立，且不因 668 PASS 而改变**——PASS 是机械审计口径，不是产品放行口径。

## 8. 回链

- 计划：`.zcode/plans/plan-2026-11-05-batch-44-acceptance.md`（§0 研究、§1 D0、§2 十域、§3 修复、§4 门禁、§5 计数、§6 硬约束、§7 验收）。
- 上一批：`docs/research/2026-11-05-batch-43-record.md` §9（KD-76 open、isolation 不定案、p09 未收敛、AUD-08 FAIL、发版不成立）。
- 台账/DoD/runbook：`docs/acceptance/ledger.md`（`02ba5307…`）、`dod.md`（§1.5 独立复核、§5 边界）、`runbook.md`（§2 三门、§3 回放、§9 BLOCKED）。
- 终裁草案：`docs/acceptance/adjudication-draft.md`（B1 不同源、B2 E 域、B3 FAIL 集；§5 发版 10 条）。
- 证据总索引：`docs/research/evidence/2026-11-06-batch-44/00-index.md`（§0 映射、§3 四 APK、§4 复算、§6 G1–G12、§7 哈希、§8 脱敏）。
- 十域：`docs/research/evidence/2026-11-05-batch-44/{a,b,c,d,e,f,i,j}-domain/`、`docs/acceptance/evidence/{G,H}/`；设备轨 `docs/research/evidence/2026-11-06-batch-44/device-track/`。
- 研究件：`docs/research/2026-11-06-{batch-44-baseline,metamorphic-relations,release-acceptance,screenshot-baseline,a11y-acceptance}.md`；交叉复核 `research-crosscheck.md`。
- 门禁日志（gitignored，不作归档证据）：`build/batch44-unit.log`、`build/batch44-strong.log`；本轮临时输出 `build/b44-rec/`。
- 6C-2：`core/domain/.../BackupSelection.kt:90,140-146,166`；`core/data/src/test/.../SixC2RulingInvariantTest.kt`（3/3 绿）。
- **本记录不替代主会话裁定**：当前可写结论只有——**1200 项已枚举、逐项有 verdict；但验收不可签字**（668/21/73/438，532 非 PASS，300 行结构性未判定，0 独立复核，report exit 1）。

---

<!-- 体量自查：wc -lc = 452 行 / 47,288 B（≤65,536 B）。 -->
