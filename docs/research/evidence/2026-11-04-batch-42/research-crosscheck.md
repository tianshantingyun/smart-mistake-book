# 批次 42 研究交叉复核：R1–R6 对批次 40/41 研究件（只读）

| 字段 | 值 |
|---|---|
| evidence-id | `E-42-RESEARCH-CROSSCHECK-01`（旧标识 `B42-RESEARCH-CROSSCHECK-01` 保留为别名） |
| convention-version | 1 |
| batch | 2026-11-04-batch-42 |
| step | 计划 §1 R1–R6 交叉复核（`.zcode/plans/plan-2026-11-04-batch-42.md:14-21`） |
| executed-at | 2026-11-03（本机时间；文档名按计划批次 42 的 2026-11-04） |
| head | `faab17f58b01eab78d5c08a9f843b3af3df2fecf`（与批次 42 基线 §1.1 同值；远端本件未复证） |
| device-serial | **N/A**（零设备） |
| apk-sha256 | **N/A**（零构建） |
| exit-code | §7 探针：①② 假绿复现未记录 shell exit code（输出 `G2: PASS … EXIT=0` 见 ② 原文）；③④⑤ `EXIT=0`；⑥ `GREP_EXIT=1`（`core/export/src/main` 零 AIGC 引用） |
| raw-log | 无（探针全部 inline heredoc，未落盘） |
| record-ref | 计划 §1/§4；批次 42 基线 `docs/research/2026-11-04-batch-42-baseline.md`；批次 41 记录 §9.3 |
| sanitized | 是（无密钥/账号/Cookie；仅仓内相对路径） |
| 执行者 | 研究交叉复核员（子代理） |
| 授权范围 | 只读：计划/记录/研究件/源码/`tools/` + `git`/`grep`/`sed`/`python -I` 只读命令 |
| 未执行 | 任何 Gradle、任何设备/模拟器/`am instrument`、任何 git 写、任何源码改动、任何研究件落笔（六份 `2026-11-04-*.md` 本轮仍不存在） |
| verdict | **R1「已够用但被计划 §4 文件范围硬冲突」；R5「已够用（窗口/清单）+1 项补充」；R2/R3/R4/R6「需新研究」**；另登记 **7 条冲突**（C1/C2/C3/C5/C6 为高），并独立复现 F7/F10 两个假绿 |

> 本文件是本 ask 的**唯一写入**。口径：每条结论都给出本轮实读的 `path:line` 或本轮实跑的命令与输出；未跑的一律标 `UNVERIFIED`。**不把批次 41 记录 §9.3 的条目当“当前缺陷前提”**——F7/F10 本轮用探针复核，F8/F11/F12 本轮读码复核（§3）。

---

## 0. 本轮实读范围

| 类别 | 具体件 |
|---|---|
| 计划 | `.zcode/plans/plan-2026-11-04-batch-42.md`（154 行全文；§1 R1–R6 `:16-21`，WS1 `:26-37`，WS2 `:40-51`，WS3 `:55-62`，WS4 `:66-72`，WS5 `:76-80`，WS8 `:100-110`，§4 硬约束 `:146-150`，§5 验收 `:154`） |
| 上批记录 | `docs/research/2026-11-03-batch-41-record.md` §9.3（F7/F8/F10/F11/F12 移交 `:431-438`）、§4（设备证据口径 `:245-305`）、§2.2（轨间冲突 `:189-193`） |
| 本批基线 | `docs/research/2026-11-04-batch-42-baseline.md`（全文；§2.2 调用链 `:117-133`、§3 F7–F12 现状 `:144-199`、§5.1 夹具 `:232-237`、§6 冲突 `:269-284`） |
| 批次 40 记录 | `docs/research/2026-11-02-batch-40-record.md` §8（#5/#6/#7/#16）、§9 |
| R1 研究件 | `docs/research/2026-11-03-instrumentation-pdf-verification.md`（R9，全文）、`docs/research/evidence/2026-11-03-batch-41/ws2-core-export-seam-patch.md`（全文） |
| R2 研究件 | `docs/research/2026-11-03-low-load-gating.md`（R5，全文）、`docs/research/evidence/2026-11-03-batch-41/ws4-isolation.md`（全文） |
| R3 研究件 | `docs/research/2026-11-01-kd76-stats.md`（全文）、`docs/research/2026-11-01-kd76-timing-calibration.md`（全文）、`docs/research/2026-11-01-kd76-ready-timeout-calibration.md`（门口径段）、`docs/research/evidence/2026-11-03-batch-41/step04-kd76-single-round.md` |
| R4 研究件 | `docs/research/2026-11-03-backup-format-compat.md`（全文） |
| R5 研究件 | `docs/research/2026-11-02-kd72-lock-window.md`（全文）、`docs/research/2026-10-27-lockfile-execution-plan.md`（§0–§2）、`docs/research/2026-10-26-lockfile-generation.md`（P1/P9 与结论段）、`docs/research/2026-10-27-lockfile-clean-tree-check.md`（复跑/顺序段） |
| R6 研究件 | `docs/research/2026-11-03-keeper-run-feasibility.md`（全文）、`docs/research/2026-11-03-16kb-page-testing.md`（全文） |
| 源码/工具 | `MistakePdfExporter.kt`、`MistakePdfEligibility.kt`、`MistakePdfDelivery.kt`、`AiGeneratedImageCaption.kt`、`Kd76ReportVerdict.kt`、`VisualJsConnectStageCalibrationInstrumentedTest.kt`、`ReviewCapacityPreviewReader.kt`、`ClosureRuleTables.kt`、`SmbkArchiveCodec.kt`、`SmbkArchiveCodecTest.kt`；`tools/ci/{isolation_pair_report.py,run_isolation_pair.sh,t4_control_marker.py,summarize_kd76.py,check_backup_plan_sync.py,check_lockfiles.py,check_dependency_pins.py,check_r04_keep_rules.py,check_release_manifest.py}` |

---

## 1. 逐题判定（6 行）

| # | 研究题（计划 `:line`） | 判定 | 一句话依据 | 可复用底座 | 必须新做的部分 |
|---|---|---|---|---|---|
| **R1** | PDF 导出链注入位置与缓存键升版（`:16`） | **已够用，但与计划 §4 硬冲突** | 注入顺序、三处指纹升版、草稿失效、打印子集重注入、fail-closed 与 10 条判据已在 R9 §7.6 + 接缝草案 §3–§5 写全；但落地点不止 `MistakePdfExporter`（C1） | R9 `:238-250`、`:418-431`；接缝草案 `:99-340` | 只补“最小 hunk 变体”与文件范围裁定；研究内容本身不需重写 |
| **R2** | isolation 因果实验设计（`:17`） | **需新研究** | 低负载门/配对协议/统计上限已够（R5 §5）；但“两臂构建身份可区分”“T4 生产侧控制轮”“F7 缺方法判负”三件没有任何研究件回答，且计划“同 APK sha”与 F8 自相矛盾（C2/C6） | 低负载 R5 §5.1–§5.4；WS4 §5–§7 | 两臂身份与安装级校验、T4 字节回退控制轮、缺方法判 INVALID、报告 schema、因果口径 |
| **R3** | 20 轮冷启动统计执行形状（`:18`） | **需新研究（统计口径已够用）** | nearest-rank / n=20 / 删失 / G0–G3 / anti-flaky 十二条已裁定（kd76-stats §2–§5）；执行形状缺 boot 身份口径、reboot 间隔、进程残留检查，且 HEAD 用 `BOOT_COUNT` 而非计划写的 `boot_id`（C3/C4） | `2026-11-01-kd76-stats.md` §2.3/§3.3/§5；`kd76-timing-calibration.md` §3.2/§4.2 | boot 身份与门、reboot→run 间隔、进程冻结/残留、逐轮 INVALID 计 n、单批 vs 两批、29.5 s 分段 |
| **R4** | 历史 v4 备份包夹具合法构造（`:19`） | **需新研究** | 现有“v4 夹具”是**用当前写侧（closure 5）生成容器后重写 manifest、载荷是字面量假文件**，不是可恢复的历史包（N4）；冻结字节 + digest golden 完全无载体 | `ClosureRuleTables.v4`、四方门 v4 空包 digest golden、`backup-format-compat.md` §4–§5 | 真实 v72 载荷 + 实测行数 + v4 规则 digest + 冻结字节/sha golden + JVM/设备/负向用例；计划“v5 包去 error_cause 表”写法需纠正（C5） |
| **R5** | 多模块锁文件写入窗口与可复现性（`:20`） | **已够用（+1 项补充）** | 窗口 W1–W7、最小等待清单、`lockAllDependencies --write-locks` 命令、负控、`:benchmark` 缺口、Keeper 排序均已给（KD-72 件）；缺“隔离副本”执行形状与两次生成一致性判据 | `2026-11-02-kd72-lock-window.md` §1–§4；`2026-10-27-lockfile-execution-plan.md` §1–§2 | 隔离副本范围/缓存/绝对路径；两次生成 sha 对比；隔离副本证据的可采性边界；刷新当前树读数 |
| **R6** | 发版前置的机器可核清单（`:21`） | **需新研究** | R8 mapping/16KB/签名的**素材**已在 Keeper 与 16KB 件；但没有“命令→期望输出→产物→通过条件”的清单，mapping 保留策略、供应链 license 快照、release 冒烟判据均无载体；`docs/current/release-gates.md` 仍是散文且热区 7 M | Keeper 件 §0/§3.3/§4；16KB 件 §2/§6/§9 | 清单化 + 4 个缺口（mapping 保留、license 快照、release 冒烟、arm64 BLOCKED runbook）+ 构建绑定 |

> 结论：**6 题里没有一题可以“照抄现有件直接开写”**；R1/R5 的底座最厚（R1 卡在权限，R5 卡在执行形状），R2/R3/R4/R6 都要补关键章。

---

## 2. 各题“已有 / 缺什么 / 怎么写”的可执行边界

### 2.1 R1：注入位置与缓存键升版

**已有（可直接引用，不要再造一套）**

- 顺序：`render → size 判限 → 注入 → pageCount → digest → publish`；注入必须在 `validatedPageCount` 之前（让 PDFium 真的接受增量段），且必须在 `writeDigest` 之前（摘要/完整性/发布物绑定注入后字节）——接缝草案 `:238-274`，R9 `:238-250`。
- 缓存短路在渲染**之前**：`store.findExisting(input)`（`MistakePdfExporter.kt:120`）→ 旧无标记包会直接返回，`/AIGC` 注入没有机会发生；因此 item 22（旧包不回退）只能靠**缓存键升版**。
- 三处指纹：单题 `mistake-export-v3`（`MistakePdfEligibility.kt:186`）、批量 `mistake-batch-v3`、卷面编辑 `mistake-paper-edit-v1`；草稿 `FORMAT_VERSION=1`（接缝草案 §3.3 `:148-208`）。
- 打标判据：`PdfAigcContentPolicy.shouldLabel(hasAiGeneratedImage, hasModelParsedText)`；AI 图角色判据 `isAiGeneratedExportImage(role)`（`AiGeneratedImageCaption.kt:30-38`）。
- 打印：整份 `copyWhole` 原样复制保留 `/AIGC`（`MistakePdfDelivery.kt:208`）；部分页 `writeSubset` 重绘新 `PdfDocument`（`:215`、`:227`、`:259`）会丢元数据，需重注入（接缝草案 §3.5 `:276-321`）。
- 失败闭包：注入器 typed error → `MistakePdfExportException(GENERATED_PDF_INVALID)`；注入后再判 `maxPdfBytes`；不发布半成品（接缝草案 §3.4 `:244-269`、§5 `:344-365`）。

**缺什么（研究侧只缺“权限/变体”，不缺设计）**

1. **文件范围裁定**（C1）：接缝草案 §2.1 推荐把 `core/data/.../pdf/` 7 个 main + 6 个 test + 1 夹具整体移入 `core/export`，并改 `app/src/androidTest` 6 个 import；这比“只改 `MistakePdfExporter` 的最小 hunk”大一个数量级。计划 §4 `:146` 只允许 exporter。两者不能同时成立。
2. **最小 hunk 变体的能力边界**：若只改 exporter，可完成注入、页数/摘要绑定、fail-closed、负向、真实导出链（items 13/15/16/17/18/19/20/21），但**无法**完成 14（键升版）与 22（旧包不回退），因为键在 `MistakePdfEligibility.kt:186`，且 `PreparedPdfStore` 的目录名/`verifyIntegrity`/`reopenVerified` 全部依赖 `input.inputSha256`（`MistakePdfExporter.kt:64-72`、`:382`）。
3. **布尔来源**：`MistakePdfExportInput`（`:318-355`）当前**没有** `hasModelParsedText` 字段；要传它必须改资格层/批量/卷面编辑/草稿 DTO（接缝草案 §4 `:325-340`）。
4. **误标陷阱**：`isAiGeneratedExportImage(null) == true`（`AiGeneratedImageCaption.kt:30-38`）；若在 exporter 里无条件调用，纯文本导出会被打标——负向用例必须显式覆盖。
5. **DOCX 不在本接缝内**：KD-75 的 DOCX 半成品另立条目（接缝草案 `:339-340`），R1 不得把 DOCX 混进 PDF 判据。

**判定**：研究内容**已够用**；R1 研究件的增量应写成“**最小 hunk 变体 + 权限申请清单**”，而不是重写注入设计。若主会话不批第二组文件，R1 必须把 14/22 标 `BLOCKED`，不能写成“已覆盖”。

### 2.2 R2：isolation 因果实验设计

**已有（可直接引用）**

- 低负载门：L-1–L-7、5 s/5 min（或 10 s×30 差分）、地板标定、fail-closed、`UNGATED ≠ PASS`（R5 §4–§5）。
- 配对协议：每臂前后门、`A B A B A B` 交替、同 APK sha/同 serial/同宿主/同时间带、跑中不作负载门（R5 §5.1 `:213-222`）。
- 统计上限：n=15、k=3 时完美分离 p=0.10；k=4 才 p=0.0286；n=15 不存在支持“某用例因 T4 而红”的统计证据（R5 §5.3 `:228-234`）。
- A3 修正：kill 判据改结构性不变量（4 个必须杀各 ≥1、必须不杀为 0、总数 ≥5、偏离只 WARN）（R5 §5.4 `:236-243`）。
- 现有实跑：6 run×15/15 PASS，但 5/6 post-gate INVALID → `UNCONVERGED`；原因是 post 窗首样本被套件收尾污染（WS4 §5.3 `:191-222`、§9 `:278-296`）。

**缺什么（三件硬缺口）**

1. **两臂构建身份**：`run_isolation_pair.sh:51` 只有一个 `COMPONENT`；两臂在同一个 `for arm` 里调用同一条 `am instrument`（`:217-222`），差异只有 `-e smb.t4.arm`。脚本头自述“不构建、不安装 APK，只校验 sha”（`:25`）。因此当前 OFF 臂只是**测试侧钩子未注册**，生产 6 处打点仍在（WS4 §6 `:224-245`）。研究必须给出：两个可区分构建身份（不同 package/component 或不同 class list）的构建/安装/校验流程，或明确“拒绝配对”。
2. **T4 生产侧控制轮**：需要“回退 6 处写点 → 构建 → 记录 marker（构建日志 + 引擎 sha + 行级 sha）→ 安装 → 跑臂 → 还原 → sha 复核”；`t4_control_marker.py` 全文件无 `adb`/`apk` 字样（本轮 `grep` exit=1），`verify_marker` 只对源码树重算（`:123-149`），不能证明“装上去的是不是控制包”。研究必须把“已安装 APK sha + 类清单/锚点”纳入 marker 校验。
3. **F7 缺方法判负**：`parse_instrument_log` 只收录出现过的 test（`:106-124`）；SKIP/UNKNOWN 守卫只遍历已存在向量（`:298-307`）；比较用并集 + `.get(method, UNKNOWN)`（`:349-358`）；`identical = not discordant`（`:384`）。本轮探针复现：OFF 臂只有 A、ON 臂有 A/B，全部绿 → `PAIRED_RED_SET_IDENTICAL / paired=True / reasons=[]`（N1）。研究必须钉死“每臂方法集合 = 预注册 15 个；缺任一 = 该臂 INVALID”，且在比较前判定。

**决定性口径冲突**

- 计划 R2 写“**同 APK sha 的 OFF/ON 配对**”（`:17`），F8 又要求两臂**不同构建身份**（批次 41 §9.3 `:434`）。两者不能同时为真：同 APK + 测试侧 flag 只能证明“红集在低负载窗内可复现”，不能支撑生产 T4 因果；真正的 OFF 臂必须是另一份构建，因而 sha 必然不同。研究必须在开头二选一，并让报告字段同时记录 `treatment_sha` 与 `control_sha`（C2）。
- 计划 item 38 写“仅当配对与控制轮同时成立才可写因果”（`:69`）。即使两者都成立，n=15/k=3 也没有单例级统计证据（R5 §5.3）。研究必须把“因果”限定为**机制级**（字节可核的控制构建 + 红集差异方向），不写 p 值因果（C6）。

### 2.3 R3：20 轮冷启动的执行形状

**已有（可直接引用）**

- 统计量：nearest-rank `p95 = sorted[ceil(0.95n)-1]`；n=20 时 p95=19/20，容忍一个最大值；n<20 不得报 p95（kd76-stats §2.1–§2.3 `:43-78`）。
- 删失：FAIL = 值 ≥ 预算，进 G1，不当缺失值；INVALID（seam/夹具缺陷）整批作废，不混入分位数（§2.5 `:98-106`）。
- 门结构：G0 形状 → G1 零未归因失败 → G2 `p95 ≤ READY_TIMEOUT/2` → G3 max 报数；G1/G2 合取（§3.3 `:135-144`）。
- 冷启动方法：`adb reboot` → `sys.boot_completed=1` 后**不沉降**即跑（timing-calibration `:132`、`:142`；ready-timeout-calibration `:26`、`:57`）。
- anti-flaky 十二条：A1 归因先行、A2 单钟、A3 无扣除、A4 相对门、A5 失败不丢、A6 两轮独立、A7 不重试洗绿、A8 同轮元数据、A9 披露 100 ms 轮询、A10 预注册、A11 冷启动不预热、A12 屏幕/睡眠可观测（§5 `:226-241`）。
- 29.5 s：只按预算包络相加；不加 p95；不做单例端到端（§4 `:184-197`）。

**缺什么（执行形状，全部无载体）**

1. **boot 身份口径**：计划 item 25 明确写 `/proc/sys/kernel/random/boot_id`（`:57`）；HEAD 的 `bootCount()` 用的是 `Settings.Global.BOOT_COUNT`（`VisualJsConnectStageCalibrationInstrumentedTest.kt:944-955`），KDoc 自己说“20 次 reboot 必须 20 个不同值”。两者语义不同（boot_id 是每次启动唯一 UUID；BOOT_COUNT 是计数器，可被 `settings put`/恢复出厂改动）。研究必须选一个作为主身份，并定义 `-1`（读不到）与“重复 boot”的判据（C3）。
2. **reboot→run 间隔 / settle**：timing-calibration 写“不沉降即跑”，但单轮证据 `KD76_ENV` 的 `loadBefore=6.49` 是 reboot 后系统启动残余（step04 §4 `:66-69`）。研究必须给：boot_completed 后固定等待 N 秒还是立即跑、N 的依据、是否把 load 只记录不作门；不能“看到 6.49 就删点”。
3. **进程冻结/残留**：每轮前必须证明 `:visual_js`/测试进程不存在（`ps -A` 快照）；`force-stop`/进程冻结（SIGSTOP）产生的“冷启动”**不是** reboot 冷启动，bootCount/boot_id 会暴露，必须单列为非主口径；不得合并进 20 轮主样本。
4. **逐轮脚本形状**：一轮一次独立 `am instrument`、`-e smb.kd76.round N`、每轮校验 boot 变化；空 sha / 异常轮标 INVALID **并计入 n 上限**，不静默剔除。当前 `summarize_kd76.py:363` 用 `{row.apk_sha256 for row in selected if row.apk_sha256}` 过滤空值，`:368` 只在“全部为空”时报错；`boot_counts` 只统计不入 `reasons`（`:365`、`:415-416`）；本轮探针：20 轮中第 20 轮无 sha、全程无 bootCount → **`G2: PASS`、`reasons=[]`**（N2）。
5. **单批 vs 两批**：计划 item 29 允许“一次 20 轮，或分两批各 10 合并”（`:59`）；kd76-stats A6 要求“两批独立冷启动”（`:75-76`）。单批 n=20 在分位数上与合并等价，但**不满足两轮独立**；研究必须显式二选一并写进证据，不能两边都占（C4）。
6. **M 与 READY_TIMEOUT**：计划 item 30 “不改 10 s 阈值，除非数据支持”（`:59`）；研究必须把“数据支持”写成预注册判据（p95 > 5 s 或任一 ready 吃满 10 s），不能事后看数再决定。
7. **29.5 s 分段**：沿用 timing-calibration §4.2 的四段方案（S1/S2 seam 实测、S3 用 KD-66 witness、S4 用 `abandonForTest`），只报上界相加，不拼单例。

### 2.4 R4：历史 v4 备份包夹具

**已有（可复用，但都不足以替代真实包）**

- v4 冻结规则表：`ClosureRuleTables.v4` = 91 键 / 83 裁剪 / `smbk-selection-v4` / added 两错因表 / dropped `knowledge_search_material_feature`（`ClosureRuleTables.kt:67-95`）。
- v4 空库全选 digest golden：`ee9a1b4d…fac06b006`，四方门本轮实跑 `exit=0` 并打印 recomputed==pinned（`check_backup_plan_sync.py:58-60`；N10）。
- 投影协议：v4→v5 只允许“新增表补 0、被删表验空”，行数对账三层（backup-format-compat §4.2 `:287-296`、§5.2 `:329-359`）。
- 设备侧路径：`createDatabaseFromExportedSchema(..., version = 72)` 已用于 v72→73 迁移测试（`FullMigrationMatrixInstrumentedTest.kt:2040-2082`，转引自 backup-format-compat `:474`）。
- 负向/读侧判据：closure 4 接受、closure 3/6 拒绝、digest 用 v4 算法、被删表非空拒绝（backup-format-compat §9 `:452-480`）。

**缺什么（这是本批最“空”的一题）**

1. **现有 v4 测试不是可恢复的真实 v4 包**：`SmbkArchiveCodecTest.kt:1486-1510` 先用当前写侧 `SmbkArchiveCodec.create(... databaseSchemaVersion = 72 ...)` 生成容器（selection 为当前 closure 5），载荷是一个临时文件、内容字面量 `"database-content"`（`:1487`），然后把 manifest 里的 selection 重写成 `v4SelectionFixture(mask=15, rowCounts=v4EmptyRowCounts())`（`:1499-1502`、`:1624-1646`）。这证明的是“读侧规则表 + 投影”在 JVM 上成立，**不证明**一份真实 v4 包（v72 载荷 + 真实行）可恢复，也没有冻结字节。
2. **合法构造协议**：需要先用 `72.json` / `createDatabaseFromExportedSchema(..., 72)` 造一个真实 v72 库（user_version=72、91 表键空间、`knowledge_search_material_feature` 缺失或为 0、无 `error_cause_*`），写入可指认的共享表行；再按 v4 的 91 键**实测** rowCounts，用 `ClosureRuleTables.v4` 算 digest，写 `formatVersion=3 + selection.closureVersion=4` 的 manifest，与真实载荷打包；最后冻结字节并记录 `sha256` + v4 digest golden。
3. **“不伪造行”的判据**：rowCounts 必须由载荷 `COUNT(*)` 实测（不得全 0、不得手写）；载荷必须是 v72 形状（不得把 v73 库删两张表后把 `user_version` 改成 72 冒充）；被删表必须声明 0 且实测 0/缺失；新增表在迁移前不存在、迁移后为 0；fixture 生成一次后冻结，测试期不得重算/重打包。
4. **落位与体积**：放 `core/data/src/androidTest/assets/`（现有 `dense/`、`golden/`）还是 `src/test/resources/`；`core:data` 的 androidTest 已挂 `core/database/schemas` 资产（`core/data/build.gradle.kts:35-36`，转引自 R9 `:56`），新增 `.smbk` 不能与 schema 资产混放；APK 体积与测试 APK 增量要记录。
5. **三层用例**：JVM 读冻结字节 → `Valid` + 投影 92 键 + 新增表 0 + 被删表 0；设备恢复到 73 → DAO 可读、无半行、`error_cause_*` 空；负向（截断/改一行计数/把 digest 换成 v5）→ 交换前拒、原库不动。
6. **门**：把 fixture 的 `sha256` 与 v4 digest 写进 `check_backup_plan_sync.py`（或新 gate），防“有人顺手重打包/重算”；当前四方门只钉了 v4 **空包** digest，没有 fixture 级 golden。

**计划写法要纠正**：计划 item 42 写“从当前 v5 包构造 v4 夹具（去 error_cause 表 + closureVersion=4）”（`:77`）。这句话至少缺三件：v5 载荷是 schema 73（不是 72）、去表不等于 v72 形状、rowCounts 必须实测。照字面做会得到一份“能过 codec、但恢复路径没被真正走过”的包，正好违反“不伪造行”（C5）。

### 2.5 R5：锁文件写入窗口与可复现性

**已有（可直接引用）**

- 窗口判据 W1–W7 与分支 A/B；本轮读数：`settings.gradle.kts`/5 个触面 build 文件干净、暂存 0、跟踪锁 0、盘上 14 个未跟踪锁、`:benchmark` 缺锁（KD-72 件 §0–§1 `:7-48`）。
- 最小等待清单：树收口（W2）、`.gitignore`（W1 唯一残项）、Keeper D4 排序、`:benchmark` 锁、负控（W6）、Gradle 轨（W7）（§2 `:50-68`）。
- 生成命令：`./gradlew --no-configuration-cache --no-parallel --console=plain lockAllDependencies --write-locks`；审 diff 看 14 个 included project；跑 `check_lockfiles.py`；负控抬版/加依赖（§2.2 `:58-68`）。
- 风险：Gradle 9.6.x 锁定后 classpath 顺序（#38057，9.6.1 未修）、`:core:data` 的构建期派生 AAR、KGP 并行解析 race（lockfile-generation `:26`、`:509-513`）；P1 冒烟必须覆盖顺序风险。
- 本轮复核：根 `build.gradle.kts:37-38` 有 `dependencyLocking { lockAllConfigurations() }`；`gradle.properties:2-3` 是 configuration-cache + parallel；`settings.gradle.kts:19-34` 14 个 include；build 触面文件全干净、0 跟踪锁、14 个未跟踪锁（N7/N10）。

**缺什么（只差“隔离副本”这一项）**

1. **隔离副本的执行形状**：复制范围（建议 `git archive HEAD` + 必要的未跟踪源；**不复制** `build/`、`.gradle/`、`local.properties` 的密钥；`local.properties` 只保留 `sdk.dir`）、Gradle user home/cache（复用本机缓存；网络不可达时 `--offline` 的可失败点）、绝对路径（`settings.gradle.kts`、`local.properties`）、`--project-cache-dir`、`--no-configuration-cache --no-parallel`。
2. **可复现判据**：同一副本连续两次 `--write-locks`，14 个锁文件 sha256 必须逐字节一致；再与一次独立副本的结果对比；不一致要归因（顺序/时间戳/绝对路径），不能挑一份提交。
3. **可采性边界**：隔离副本生成的锁只对“build 文件 + version catalog + included project 集合与目标提交一致”有效；当前 build 触面干净（N7/N10）是前提，但 **Keeper 一旦落地会改插件 classpath 与 app 锁**（KD-72 件 §2.1-3），所以隔离副本只能是**预演**，不能据此关闭 KD-72；最终锁必须在 Keeper 裁定后、目标树上重生成。
4. **当前树读数刷新**：KD-72 件写的是 2026-11-02 的 564 条；本批基线是 539 条且会话内漂移（基线 §1.3）。研究件必须重跑 `git status --porcelain | wc -l` 并标时点，不能引用旧计数。
5. **不提交**：14 个未跟踪锁一律不得 `git add`；隔离副本里的锁只能作为证据/diff，正式提交由主会话按窗口判定。

**判定**：R5 主体**已够用**；新增研究只需覆盖“隔离副本 + 两次一致性 + 可采性”，不要重写窗口判据。

### 2.6 R6：发版前置的机器可核清单

**已有（素材）**

- R8/mapping：Keeper 0.19.0（AGP 9.3）三件事、`automaticR8RepoManagement=false`、K1–K6、mapping 门 v1/v2（test APK `Lj$/…` 定义数 = 0、主/测 dex 交集 = 0、test L8 中间 dex 为空）、签名 4 变量 UNSET、`validateReleaseSigning` 挂载（Keeper 件 §0 `:10-16`、§3.3 `:160-180`、§4.1–§4.3 `:215-258`）。
- 16KB：官方 2027-02-01 线、AGP ≥8.5.1、`zipalign -v -c -P 16 4`、成品 APK 14 个 `.so` 全 STORED 且偏移对齐、`ps16k_x86_64` 可跑、arm64 本机 `PANIC`/不可用（16KB 件 §0 `:14-30`、§2.1、§6、§7 `:400-412`、§9 `:424-430`）。
- 现有机器门：`check_release_manifest.py`（debug 组件泄漏 + FileProvider 路径）、`check_dependency_pins.py`（quickjs 1.0.15 / stdlib 2.3.21）、`check_stdlib_compat.py`（KD-73）、`check_r04_keep_rules.py`（两文件 `-dontobfuscate` 0 hits）；本轮四门全 `exit=0`（N10）。
- `docs/current/release-gates.md` 是**散文清单**（`:3-24` closed beta、`:26-34` public beta），不是机器可核；且是热区 7 M。

**缺什么（4 个缺口 + 清单化）**

1. **清单本身**：每条给 `命令 / 期望输出或 exit / 产物路径 / 通过条件 / 绑定（source snapshot + APK sha + 构建时间）`；机器可核 = 可重跑、可失败、输出可贴证据。
2. **R8 mapping 保留策略**：`mapping.txt` 与哪个版本/构建绑定（versionName/versionCode + APK sha + 构建时间）、保留几版、放哪（不得进 VCS）、校验和怎么记；Keeper 件只给 mapping 门，不给保留策略。
3. **签名清单**：只列 4 个环境变量名（`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD`/`RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD`）与 `validateReleaseSigning` 的触发面；不得写值；证书 SHA-256 同值判据（Keeper 件 §4.2/Step 3）。当前 4/4 UNSET 是**环境阻塞**，不是代码缺陷。
4. **供应链 license/更新度快照**：`grep -rln "license|LICENSE|SPDX|sbom" tools/ci` **零命中**（§7-⑥）——没有现成工具。研究必须给只读方法（解析 `gradle/libs.versions.toml` + 本机 Gradle 缓存里的 POM/`LICENSE`，输出坐标/版本/license/维护快照），并明确“研究阶段零 Gradle”时只能给方法与骨架，实际快照归门禁阶段。
5. **release APK 冒烟判据**：安装、冷启、核心三链路（capture→library→review/backup）谁跑、怎么判、失败怎么归因；当前 `release-gates.md` 只有“both flavors assemble/lint/unit”这类句子，没有可执行形状。
6. **构建绑定**：本轮 `check_release_manifest.py` 与 `check_dependency_pins.py` 都在读**旧构建中间产物**（`app/build/intermediates/...`）却 `exit=0`（N9）；研究必须要求“先干净重建/记录 APK sha 与构建时间，再跑门”，否则绿是上一轮的绿。
7. **arm64/16KB BLOCKED runbook**：计划 item 63 写“x86_64 宿主不可运行的证明 + arm64 等待清单”（`:102`）。16KB 件明确 `ps16k_arm64` 本机未启动、`PANIC` 是单一来源待验证（§7 `:400-412`）；因此“证明”只能是“本机无 arm64 加速 + 预期 PANIC 的来源标注”，不能伪造一条 PANIC 日志；arm64 必须标 `BLOCKED` 并给真机/arm64 宿主的 runbook。
8. **`docs/current/release-gates.md` 是热区 7 M**：R6 只能出 `docs/research/2026-11-04-release-readiness.md` 草案与可应用 patch，不能改契约正文（计划 §4 `:147`）。

---

## 3. HEAD 新事实与本轮独立复核（不是转录）

> 本节全部是本轮实读/实跑；与批次 41 记录/基线的差异逐条标出。

| # | 事实 | 证据（本轮） | 对研究题的影响 |
|---|---|---|---|
| **N1** | **F7 假绿复现**：OFF 臂只有方法 A（3 轮），ON 臂有 A/B（3 轮），全部 PASS → `verdict=PAIRED_RED_SET_IDENTICAL paired=True reasons=[]`，`OFF keys=['A']`、`ON keys=['A','B']` | `python -I` 探针（§7-①），基于 `isolation_pair_report.py:106-124,298-307,349-358,384-386` | R2 必须把“缺方法”判 INVALID；在修好前该报告不能作配对通过证据 |
| **N2** | **F10 假绿复现**：20 份合成 round XML，第 20 轮无 `apkSha256`、全程无 `bootCount` → `samples n=20/20 \| G1 green \| G2: PASS (M.p95=700)`、`reasons=[]`、`bootCount(s)` 不打印 | `python -I` 探针（§7-②），基于 `summarize_kd76.py:363-375,394` | R3 必须加“每轮 sha 非空 + boot 身份唯一”门；当前脚本会发 PASS |
| **N3** | HEAD 的 boot 身份是 `Settings.Global.BOOT_COUNT`（API 24+，读不到记 -1），**不是**计划写的 `/proc/sys/kernel/random/boot_id`；KDoc 自述 “reboot 模式必须 20 个不同值” | `VisualJsConnectStageCalibrationInstrumentedTest.kt:944-955`；`grep boot_id` 全文件 0 命中 | R3 必须先裁身份口径（C3）；`settings put` / 恢复出厂可改 BOOT_COUNT，不能把“值不同”当唯一证据 |
| **N4** | 现有 “v4 夹具” 是 **当前写侧容器 + 字面量假载荷 + manifest 重写**：`database-content` 临时文件、`databaseSchemaVersion=72`、selection 被替换为 v4 零行 | `SmbkArchiveCodecTest.kt:1486-1510`（`:1487` 载荷、`:1499-1502` 重写）、`:1624-1646`（v4SelectionFixture） | R4 必须做真实 v72 载荷；现有测试不能当“v4 包已验” |
| **N5** | `isAiGeneratedExportImage(null) == true` | `AiGeneratedImageCaption.kt:30-38` | R1 负向用例必须防“纯文本导出被误标”；exporter 只在确实有图时调用 |
| **N6** | `core/export/src/main` 对 `PdfAigc/AIGC` **零引用**（`grep` exit=1）；`core/export` 主码无 M，3 个测试 M 是他线内容 + 1 个未跟踪锁 | `grep -rn "PdfAigc\|AIGC" core/export/src/main`；`git status --porcelain -- core/export` | R1 落码会新增 `MistakePdfExporter.kt` 的 M；提交时不得把他线测试 hunk/锁算进来 |
| **N7** | 工作树：`docs/current` **7 M**、`core/domain` 1 M、`core/ui` 1 M、`feature/*` 5 个未跟踪锁 + `nul`、`tools/ci` 干净、`tools/tests` 有他线 M/?? | `git status --porcelain -- docs/current core/domain core/ui feature settings.gradle.kts`；`-- tools/ci tools/tests` | 六份研究件只能落 `docs/research/`；不得写 `docs/current`；`tools/ci` 可安全接管 |
| **N8** | `.mimosa/hook-state` 出现在 `core/data/src/test/kotlin/.../knowledge/`（被 `.gitignore:29` 忽略，仍在盘上） | `ls -d …/.mimosa/hook-state` + `git check-ignore -v` | 批次 41 F4 同类风险：任何递归扫描 src/test 的用例可能把它当输入；单测门前要复查（H31） |
| **N9** | `check_release_manifest.py` / `check_dependency_pins.py` 均 `exit=0`，但读的是**旧构建中间产物**（`app/build/intermediates/...`），不绑定当前源码/APK | 本轮 `python -I` 输出（§7-④/⑤）；`check_release_manifest.py:1-20` 自述“Run after assemble…” | R6 必须要求“干净重建 + APK sha/构建时间绑定”后再跑，否则绿是旧绿 |
| **N10** | 备份四方门 `exit=0`；lock 门 `exit=0`（13 个未跟踪锁、`:benchmark` 缺锁、KD-72 open）；pins 门 `exit=0`；keep 门 `exit=0` | §7-③/④/⑤ 的实跑输出 | R4/R5/R6 的现有门状态是“跳过/旧产物绿”，不是“已落地” |

---

## 4. 冲突登记（7 条；C1/C2/C3/C5/C6 为高）

> 判定口径：以 **HEAD 源码/工具实读**为准；计划条文与 HEAD 不一致时，研究侧不擅自改计划，只登记并要求主会话裁定。

| # | 冲突 | 证据（本轮实读/实跑） | 影响 | 研究侧处置 |
|---|---|---|---|---|
| **C1** | 计划 §4「`core/export` 只做 `MistakePdfExporter` 最小 hunk」 vs WS2 13/14/22 需要改指纹/输入/草稿/打印 | 计划 `:146`；接缝草案 §2.1 `:43-63`（整体移 7+6+1 文件）、§3.1–§3.3 `:99-208`、§3.5 `:276-321`；`MistakePdfEligibility.kt:186`（v3 前缀）、`:318-355`（无 `hasModelParsedText`）、`MistakePdfExporter.kt:120`（缓存短路）、`:382`（目录名 = inputSha256） | 只改 exporter **无法**完成 14（键升版）与 22（旧包不回退）；照计划写会漏 AC | R1 改写为“最小 hunk 变体 + 权限清单”；未获批前 14/22 标 `BLOCKED`；不得把“注入成功”写成“缓存已失效” |
| **C2** | 计划 R2「同 APK sha 的 OFF/ON 配对」 vs F8「两臂必须绑定不同构建身份」 | 计划 `:17`；批次 41 §9.3 `:434`；`run_isolation_pair.sh:51`（单 COMPONENT）、`:217-222`（同一条 `am instrument`，仅 `-e smb.t4.arm`） | 同 APK 只能证明“红集可复现”，不能证明生产 T4 因果；两条要求不可能同时满足 | R2 开头二选一：①同 APK + 测试侧臂 = 只写可复现；②两份构建 = 写 `treatment_sha`/`control_sha`，因果按机制级；不得混写 |
| **C3** | 计划 item 25 写 `boot_id` vs HEAD 用 `Settings.Global.BOOT_COUNT`；item 27/28 的 INVALID/boot 门在脚本中不存在 | 计划 `:57-58`；`VisualJsConnectStageCalibrationInstrumentedTest.kt:944-955`；`summarize_kd76.py:363-375`；N2 探针 | 20 轮“reboot 可证”目前**不成立**：BOOT_COUNT 可被改、脚本也不校验 | R3 先裁身份（boot_id 或 BOOT_COUNT），再给“N 个不同值 + 非 -1 + 混入即 INVALID”的门；实现前不得写“boot 可证” |
| **C4** | 计划 item 29 允许“单批 20 轮” vs kd76-stats A6 要求“两批独立冷启动” | 计划 `:59`；`2026-11-01-kd76-stats.md:75-76`（两批各 10 合并；单批 20 不满足 A6） | 单批 n=20 的分位数等价，但“两轮独立”不成立；若照单批写，关闭证据的独立性口径会退 | R3 明确写：主口径取两批各 10；若设备成本只允许单批，证据必须标“单批，不满足 A6”，不得称两轮独立 |
| **C5** | 计划 item 42「从 v5 包构造 v4 夹具（去 error_cause 表 + closureVersion=4）」 vs “不伪造行” | 计划 `:77`；`SmbkArchiveCodecTest.kt:1486-1510`（假载荷 + manifest 重写）；`ClosureRuleTables.kt:67-95`；backup-format-compat §4.2 `:287-296` | 照字面做会得到 schema 73 载荷、删表冒充 v72、rowCounts 全 0 的“能过 codec、恢复路径没走过”的包 | R4 改为“从 72.json/`createDatabaseFromExportedSchema(...,72)` 造真实 v72 载荷 + 实测行数 + v4 digest + 冻结字节”；计划措辞需主会话更正 |
| **C6** | 计划 item 38「配对与控制轮同时成立才可写因果」 vs n=15/k=3 无单例统计证据 | 计划 `:69`；`2026-11-03-low-load-gating.md:228-234`（k=3 完美分离 p=0.10；k=4 才 0.0286） | 把“因果”写成“配对+控制轮 ⇒ 单例因果”会超出统计上限 | R2 把因果限定为**机制级**（字节可核的控制构建 + 红集方向），不写 p 值/单例因果；统计只用于“窗口稳定性筛查” |
| **C7** | KD-72「窗口关闭、不得生成/提交锁」 vs 计划 item 67「隔离副本尝试 `--write-locks`」 | `2026-11-02-kd72-lock-window.md:76-78`；计划 `:106`；N7/N10（树 539+、14 未跟踪锁、0 跟踪锁） | 隔离副本容易被误读为“窗口已开”；若副本 build 文件与最终提交不一致，锁仍会过期 | R5 明确：隔离副本只作**预演/可复现性验证**，不关闭 KD-72；最终锁必须在 Keeper 裁定后、目标树上重生成；证据里标“pre-run, not close” |

> 另：基线 §6.1 C3（WS6 48/49 与 2026-10-23 已实施裁定相反）不在本件六题范围，**本件未复核**，仍以基线登记为准。

---

## 5. 硬约束清单（H1–H32）

> 每条都指认它消灭的具体失败（全局 §12.2）；指认不出的条目已删。

### 通用（六份研究件都适用）

| # | 约束 | 消灭的具体失败 | 落点/验收 |
|---|---|---|---|
| **H1** | 每份研究件必须有：§0 结论表（结论/类型/证据三列）、来源清单（URL + 版本 + license + 维护快照）、**UNVERIFIED 段**；四类陈述分写（仓内事实 `path:line` / 外部 URL / 计算值 `命令` / 假设） | 结论与来源脱钩、把推断写成事实（批次 40 §8 #3 的“93 表虚报”同型） | 六份 `2026-11-04-*.md` |
| **H2** | 单一来源一律标“待验证”，不得用于支撑“可行/完成/无冲突”；摘要级来源不得当事实；跨引擎/跨主体才算多来源 | 用单一博客/摘要定论（全局 §11.3） | 每份来源清单 |
| **H3** | 与 HEAD 冲突的旧结论必须重读并登记；不得照抄批次 41 §9.3 当“当前未修”前提（F7/F10 本轮已探针复核，F8/F11/F12 读码复核） | 以过期缺陷为前提写研究/改代码（批次 41 记录 §9.3 的时点值） | R1/R2/R3；§3 N1–N10 |
| **H4** | 研究阶段零 Gradle、零设备、零 git 写、零源码改动；Gradle 只在门禁阶段串行、无并发 | 研究污染构建/树态（批次 41 §3.3 的并发瑕疵） | 全部六份；本件 §6 |
| **H5** | 每份研究件必须给可执行验收判据（命令或断言形态），并逐条指认“它消灭的具体失败” | 结论无法验收、新增无失败指认（全局 §12.2） | 六份 §验收 |

### R1

| # | 约束 | 消灭的具体失败 | 落点/验收 |
|---|---|---|---|
| **H6** | 先写文件范围裁定：只改 `MistakePdfExporter` 时 14/22 **不可达**，必须标 `BLOCKED`；若申请第二组文件，逐文件给“为什么非它不可”与最小 hunk | 用“已注入”冒充“缓存已失效/旧包不回退”（C1） | `MistakePdfEligibility.kt:186`；接缝草案 §2.1/§4 |
| **H7** | 注入顺序：render → 注入 → 再判 `maxPdfBytes` → `validatedPageCount` → `writeDigest` → `publish`；摘要/`verifyIntegrity`/`reopenVerified`/`copyWhole` 全部绑定注入后字节 | 摘要与发布物不一致、PDFium 没接受增量段却发布（KD-75 U3） | `MistakePdfExporter.kt:128-136`、`:64-72`、`:382`；接缝草案 §3.4 |
| **H8** | 缓存短路在 render 前（`:120`）：旧无标记包只能靠键升版失效；升版必须同时覆盖单题/批量/卷面编辑/草稿，旧 token 自然失败（fail-closed） | 旧无 `/AIGC` 包被直接返回，用户拿到无标识文件 | `MistakePdfEligibility.kt:186`、`MistakePdfBatchEligibility.kt:96`、`MistakePdfPaperEdit.kt:448`、`MistakePaperDraftStore.kt:227` |
| **H9** | 纯用户内容不注入；`isAiGeneratedExportImage(null)==true`，只在“确实有图”时调用；负向用例必须覆盖纯文本/`USER_TRANSCRIPTION` 等非 AI provenance | 纯文本导出被误标 AI（伪标识），或含模型文本的文档漏标 | `AiGeneratedImageCaption.kt:30-38`；接缝草案 §5.10 |
| **H10** | 部分页打印 `writeSubset` 重绘新 `PdfDocument`（`:227`、`:259`）必须重注入；整份 `copyWhole`（`:208`）字节原样；DOCX 不在本接缝 | 部分页打印丢 `/AIGC`；把 DOCX 半成品混进 PDF 判据 | 接缝草案 §3.5；`MistakePdfDelivery.kt:208/215/259` |
| **H11** | injector typed error → `GENERATED_PDF_INVALID`；注入后再判大小；不发布半成品/无 `.partial` 残留；失败码可断言、不绑 message | 注入失败仍发布、或留下半成品（KD-75 fail-closed） | 接缝草案 §3.4/§5.8；`MistakePdfExporter.kt:131-136` |

### R2

| # | 约束 | 消灭的具体失败 | 落点/验收 |
|---|---|---|---|
| **H12** | 两臂身份必须在**已安装 APK** 级可区分（package/component/class list/sha）；报告同时记 `treatment_sha`/`control_sha`；同 APK + 测试侧 flag 只能写“可复现” | F8：两臂同一 COMPONENT，OFF 臂不是真 OFF（`run_isolation_pair.sh:51/217-222`） | `run_isolation_pair.sh`；`t4_control_marker.py` 需加 adb/APK sha/类清单 |
| **H13** | T4 控制轮必须字节可回退、可复核：回退 6 写点 → 构建 → marker（构建日志 + 引擎 sha + 行级 sha + 已安装 APK sha/类清单）→ 跑 → 还原 → sha 复核 | “源码树比对比不出装上去的包”（F8 第二半） | `t4_control_marker.py:87-149`（行号转引自基线 §3.2）；批次 40 I50-6 |
| **H14** | 红集：每臂方法集合 = 预注册 15；缺任一 = 该臂 INVALID；`-4`/skip/UNKNOWN 不算过；不重跑到绿；报告必须写“n=15/k=3 不支持单例因果” | F7 假绿（N1）；`-4` 被当绿（批次 41 §4.5） | `isolation_pair_report.py:106-124,298-307,349-358,384-386` |
| **H15** | 门协议：每臂前后门、`A B A B A B`、同 serial/ABI/page size/宿主/时间带；post-gate settle 必须**预注册**（5/6 INVALID 的收尾污染），不得看结果后调；loadavg 只记录 | 用无效窗比较红集、用 settle 调到过（WS4 §5.3/§9） | `low-load-gating.md:213-243`；`run_isolation_pair.sh --post-settle` |

### R3

| # | 约束 | 消灭的具体失败 | 落点/验收 |
|---|---|---|---|
| **H16** | 先裁 boot 身份：`/proc/sys/kernel/random/boot_id` 或 `Settings.Global.BOOT_COUNT`；门 = “20 个不同值、非 -1、混入即 INVALID”；force-stop/进程冻结轮单列，不混入主样本 | C3：BOOT_COUNT 可被改、脚本不校验；force-stop 冒充 reboot | `VisualJsConnectStageCalibrationInstrumentedTest.kt:944-955`；`run_kd76_rounds.sh` |
| **H17** | 逐轮 INVALID 计入 n 上限：空 sha、缺 boot、混 sha/fingerprint、XML failure、非 SAMPLE → INVALID，不剔除；`summarize_kd76` 不得因此 PASS | F10 假绿（N2）；只统计成功轮导致乐观 | `summarize_kd76.py:363-375,394` |
| **H18** | 执行形状写死：reboot→`sys.boot_completed=1`→（固定的 settle 或立即跑，二选一并给依据）→ 进程快照（无 `:visual_js`/测试进程）→ 一次 `am instrument` + `-e smb.kd76.round N` → 逐轮校验 boot 变化；两批各 10 或单批 20 必须显式选 | 把“同 boot 内 force-stop 20 次”当 20 轮；把 `loadBefore=6.49` 当产品延迟 | `step04-kd76-single-round.md:37-69`；`kd76-stats.md:75-76` |
| **H19** | M 与 READY_TIMEOUT：不改 10 s，除非预注册判据（p95 > 5 s 或任一 ready 吃满 10 s）成立；29.5 s 只报预算包络/分段上界，不拼单例、不加 p95 | 事后看数改阈值；把 29.5 s 当实测 | `kd76-timing-calibration.md:130-136`、`:184-197` |

### R4

| # | 约束 | 消灭的具体失败 | 落点/验收 |
|---|---|---|---|
| **H20** | v4 fixture 必须“真实 v72 载荷 + 实测行数 + v4 digest + 冻结字节”：载荷 user_version=72、91 表键空间、`material_feature` 缺失或 0、无 `error_cause_*`；rowCounts 由 `COUNT(*)` 实测；digest 用 `ClosureRuleTables.v4`；不得用 v5 载荷删表/改 user_version 冒充，不得全 0 | “能过 codec、恢复路径没走过”的假夹具（N4/C5）；伪造行 | `SmbkArchiveCodecTest.kt:1486-1510` 替换；`ClosureRuleTables.kt:67-95` |
| **H21** | fixture 冻结：字节 sha256 + v4 digest golden 入 `check_backup_plan_sync.py`（或新 gate）；测试期不得重打包/重算；落位与 APK 增量记录 | 有人顺手重打包/重算，fixture 漂移；golden 与实际字节脱钩 | `check_backup_plan_sync.py:58-60`；`core/data/build.gradle.kts:35-36` |
| **H22** | 三层用例：JVM 读字节 → `Valid` + 投影 92 键 + 新增表 0 + 被删表 0；设备恢复到 73 → DAO 可读、无半行、`error_cause_*` 空；负向（截断/改计数/v5 digest）→ 交换前拒、原库不动 | 只有 JVM 自证、没有恢复级证据（批次 41 F3/F14） | `backup-format-compat.md:452-480`；`FullMigrationMatrixInstrumentedTest.kt:2040-2082` |

### R5

| # | 约束 | 消灭的具体失败 | 落点/验收 |
|---|---|---|---|
| **H23** | 隔离副本只作预演：复制 build 文件/version catalog/源，不复制 `build/`、`.gradle/`；复用本机缓存；`--no-configuration-cache --no-parallel`；两次 `--write-locks` 的 14 个锁 sha256 逐字节一致；证据标 “pre-run, not close” | 把副本锁当正式锁、或两次生成不一致却挑一份提交 | `2026-11-02-kd72-lock-window.md:58-78`；`lockfile-generation.md:26` |
| **H24** | 最终锁必须在 Keeper 裁定后、目标树上重生成；`:benchmark` 必须覆盖；14 个未跟踪锁不得 `git add`；窗口判定刷新当前树计数 | Keeper 落地后锁过期；`:benchmark` 缺锁让 `check_lockfiles.py` 红（C7） | `check_lockfiles.py` 输出；`settings.gradle.kts:19-34`；N7 |

### R6

| # | 约束 | 消灭的具体失败 | 落点/验收 |
|---|---|---|---|
| **H25** | 清单必须机器可核：命令 / 期望输出或 exit / 产物路径 / 通过条件 / 绑定（source snapshot + APK sha + 构建时间）；每条可失败 | 发版清单停留在散文（`docs/current/release-gates.md:3-24`）；旧产物绿（N9） | `2026-11-04-release-readiness.md` |
| **H26** | 签名只列变量名与触发面，不写值；4/4 UNSET 标环境 `BLOCKED`；证书 SHA-256 同值判据；mapping 保留策略（版本绑定、保留版数、校验和、不进 VCS） | 密钥入库/泄漏；mapping 与版本脱钩后无法符号化崩溃 | Keeper 件 §4.2/Step 3；`app/build.gradle.kts:144-166`（转引） |
| **H27** | 16KB：静态面（`zipalign -P 16`、成品 14 `.so`、偏移对齐）可复跑；运行时 `ps16k_x86_64` 单列；arm64 本机 `BLOCKED` + runbook（真机/arm64 宿主），不得伪造 PANIC；不得把 x86_64 结果外推 arm64 | 用 x86_64 冒充 arm64；把单一来源 PANIC 当实测 | 16KB 件 §2.1/§7/§9；计划 `:102` |
| **H28** | 供应链：license/更新度快照必须给只读方法与输出格式（现在无工具，`grep` 0 命中）；研究阶段零 Gradle 时只给骨架，实际快照归门禁；release 冒烟给安装/冷启/核心三链路的可失败判据 | 供应链无证据、发版冒烟无可执行定义 | N9；§7-⑥；`check_dependency_pins.py:1-8` |

### 治理与门禁

| # | 约束 | 消灭的具体失败 | 落点/验收 |
|---|---|---|---|
| **H29** | 热区禁改：`core/domain`、`core/ui`、`feature/*`、`docs/current`、`settings.gradle.kts`、Application/Root；`core/export` 只按 C1 裁定做 `MistakePdfExporter` 最小 hunk；不改 schema 73、不改 `availableMethods`、不广告 JS | 越权改热区/版本号（计划 §4 `:146-148`；N7） | 提交前 `git status` 逐路径 |
| **H30** | 设备/证据口径：任何设备结论必须带 serial/API/ABI/page size/APK sha/源快照；`OK (n tests)` 在有 skip 时不算过；解析 `-r` 流按顺序扫描，`-4` 单列 | 设备绿绑错包、汇总行当通过（批次 41 §4.2/§4.5） | 六份 R2/R3/R4 证据段 |
| **H31** | 单测门前复查 `.mimosa/hook-state`：`core/data/src/test/.../knowledge/.mimosa/` 在盘上（被忽略）；递归扫描器必须排除隐藏目录 | 批次 41 F4 同型：schema 契约测试把工具状态当输入，3 红 | `git check-ignore`（N8）；门禁阶段 `core:database`/`core:data` 跑前检查 |
| **H32** | 禁止的结论词（未到门禁前）：F7/F8 已修、isolation 通过、T4 排除、KD-75 关闭、KD-76 PASS、v4 恢复已验证、发版就绪；只能用 完成/部分/UNVERIFIED | 把“载体在树”写成“已验证”（批次 41 记录口径） | 六份 §验收 + 记录 |

---

## 6. 未运行 / UNVERIFIED（不得写通过）

1. 任何 Gradle 编译、单测门、强门、锁生成、release 构建——本轮零 Gradle。
2. 任何设备/模拟器/`am instrument`/install——本轮零 adb。
3. F7/F10 探针是**合成输入**（我构造的 OFF/ON 日志与 20 份 XML），证明的是工具逻辑，不是设备行为。
4. `ClosureRuleTables.v4` 的运行时 digest 行为、`createDatabaseFromExportedSchema(...,72)` 的实际建库——未执行。
5. 真实 v4 包的构造/恢复、真实 PDF 注入/PDFium 接受度、真实 20 轮 boot 身份——未执行。
6. `docs/current/release-gates.md` 的热区 M 内容未逐行比对（只读标题与条目）；契约正文本轮不落笔。
7. 外部来源本轮**未新增检索**：本件只做交叉复核，不替代六份研究件的外部证据核查；引用研究件里的单一来源仍按“待验证”处理。
8. 六份 `docs/research/2026-11-04-*.md` 本轮**不存在**；本件判断的是“现有材料够不够”，不是它们最终写成什么样。

---

## 7. 本轮执行的检查（可重跑）

```bash
# ① F7 假绿复现（OFF 缺方法 B；全部 PASS）
python -I - <<'PY'
import importlib.util, sys
spec = importlib.util.spec_from_file_location("ipr", "tools/ci/isolation_pair_report.py")
m = importlib.util.module_from_spec(spec); sys.modules["ipr"] = m; spec.loader.exec_module(m)
def inst(methods):
    out=[]
    for name in methods:
        out += ["INSTRUMENTATION_STATUS: class=com.x.VisualJsIsolationInstrumentedTest",
                f"INSTRUMENTATION_STATUS: test={name}", "INSTRUMENTATION_STATUS_CODE: 0"]
    return "\n".join(out + ["OK (%d tests)" % len(methods)])
runs=[m.RunInput(arm="OFF",round_no=r,window="PASS",instrument=inst(["A"]),logcat="") for r in (1,2,3)]
runs+=[m.RunInput(arm="ON",round_no=r,window="PASS",instrument=inst(["A","B"]),logcat="") for r in (1,2,3)]
rep=m.build_report(runs,"test-side-only")
print("verdict=",rep["verdict"],"paired=",rep["paired"],"reasons=",rep["reasons"])
print("OFF keys=",sorted(rep["arms"]["OFF"]["1"]),"ON keys=",sorted(rep["arms"]["ON"]["1"]))
PY
# 输出：verdict= PAIRED_RED_SET_IDENTICAL paired= True reasons= []
#       OFF keys= ['A'] ON keys= ['A', 'B']

# ② F10 假绿复现（第 20 轮无 sha、全程无 bootCount）
T=$(mktemp -d)
python -I - "$T" <<'PY'
import sys, pathlib
d=pathlib.Path(sys.argv[1]); sha="a"*64
for i in range(1,21):
    f = "" if i==20 else f" apkSha256={sha}"
    body=(f"KD76_META round={i} fingerprint=emu-5584{f}\n"
          f"KD76_SAMPLE round={i} status=SAMPLE bind=500 warmup=200 readyGap=16 firstByte=228 total=944{f}\n")
    (d/f"round-{i:02d}.xml").write_text(f'<testsuite failures="0" errors="0"><system-out>{body}</system-out></testsuite>',encoding="utf-8")
PY
python -I tools/ci/summarize_kd76.py "$T"; echo "EXIT=$?"; rm -rf "$T"
# 输出：samples: n=20/20 | G1: green ... G2: PASS (M.p95=700 ms <= 5000 ms) ... EXIT=0

# ③ 备份四方门（本件实跑）
python -I tools/ci/check_backup_plan_sync.py; echo "EXIT=$?"
# 输出末行：PASS 四方一致（code / §9.11 / golden / 73.json）  EXIT=0

# ④ 锁门 / 钉版门 / keep 门（本件实跑）
python -I tools/ci/check_lockfiles.py; echo "EXIT=$?"
# OK: dependency locking 尚未落地 ... 13 个未跟踪锁 ... 缺锁 project：:benchmark ... EXIT=0
python -I tools/ci/check_dependency_pins.py; echo "EXIT=$?"
# OK: quickjs checksums, upstream POM requirement and resolved runtime pins match  EXIT=0
python -I tools/ci/check_r04_keep_rules.py; echo "EXIT=$?"
# OK: -dontobfuscate 0 hits in each of 2 proguard rule file(s)  EXIT=0

# ⑤ release manifest 门（读旧中间产物，不绑定当前源码）
python -I tools/ci/check_release_manifest.py; echo "EXIT=$?"
# manifest gate OK: 9 checked + 4 debug merged manifest(s) ...  EXIT=0  ← 旧构建产物

# ⑥ 结构事实
grep -rn "PdfAigc\|AIGC" core/export/src/main --include=*.kt; echo "GREP_EXIT=$?"   # 无输出 / 1
grep -rln "license\|LICENSE\|SPDX\|sbom" tools/ci tools/*.py 2>/dev/null | wc -l    # 0（无供应链工具）
git status --porcelain -- core/export tools/ci docs/current core/domain core/ui feature settings.gradle.kts
git ls-files '*lockfile' | wc -l                    # 0
git status --porcelain | grep -ci lockfile          # 14
git status --porcelain -- gradle/ build.gradle.kts settings.gradle.kts app/build.gradle.kts \
  core/*/build.gradle.kts feature/*/build.gradle.kts gradle.properties local.properties   # 空
ls -d core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/.mimosa/hook-state
git check-ignore -v "core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/.mimosa/hook-state"
# .gitignore:29:.mimosa/  → 该目录在盘上、被忽略、不在 git status
```

---

## 8. 给主会话（一句话）

六题里只有 **R1（内容已够用、卡在计划 §4 文件范围）** 与 **R5（窗口/清单已够用、只差隔离副本预演）** 可以直接进写；**R2/R3/R4/R6 必须先补研究**。最要紧的三条：① **C1**——只改 `MistakePdfExporter` 做不到缓存键升版与旧包不回退，WS2 14/22 要么获批第二组文件、要么标 `BLOCKED`；② **C2/C3**——计划“同 APK sha 配对”与 F8“两臂不同构建身份”、`boot_id` 与 HEAD 的 `BOOT_COUNT` 都是硬矛盾，研究开头必须先裁；③ **C5/N4**——现有 “v4 夹具” 是假载荷 + manifest 重写，R4 必须从真实 v72 载荷重建，照计划“v5 包去表”写会得到一份恢复路径没走过的包。
