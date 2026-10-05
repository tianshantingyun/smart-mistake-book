# 知识库未竟清单 · 深度研究报告

- 日期：2026-10-02 ｜ 仓库 HEAD：`a96bc8ce`（写作期间内核线推进：`890d966f` → `a96bc8ce`；工作树另有他线在飞改动，见 §6.4）
- 读者：项目负责人。本文回答四件事：**还剩什么、为什么重要、怎么做、依赖谁与怎么验收**。
- 素材：`build/agent-outstanding/baseline.md` + 六份事实卡（`build/agent-outstanding/group-1.md`…`build/agent-outstanding/group-6.md`）。
- 证据纪律：每条数字给「本会话实跑命令 + 输出」或「file:line」或「事实卡出处」。本会话未复算的沿用事实卡并标注其命令；两者都没有的标 `UNVERIFIED`。
- 约定：写「本会话实读/实跑/实算」= 报告作者本轮亲自核过（命令见 §7）；写「卡片」= 事实卡在本次工作流内跑过、本报告未复跑；只给 `file:line` 的 = 引代码/文档原文。
- 本会话复算环境：`D:\smart mistake book`，git bash，Python 3.13.14，模拟器 `emulator-5554` 未在本会话启动（设备类数字一律引事实卡并注明）。
- 口径优先级：基线 §0 已订正口径 > 本会话实测 > 文档旧值。凡与基线/文档冲突处，一律在 §1.4 与对应条目里写明「订正」。

---

## 1. 现状基线

### 1.1 成品包 v9（数字 + 复算命令）

制品：`moe-2025-four-subjects-v1`，version **9**，内容戳 `f1f6a470c840b365`（`core/data/src/main/resources/knowledge/moe-2025-update-manifest.json`，本会话实读：`version = 9` / `contentVersion = f1f6a470c840b365` / `packId = moe-2025-four-subjects-v1`）。

| 指标 | 值 | 复算命令（仓库根；多行脚本全文见 §7） |
|---|---|---|
| 知识点 | 3,866 | `R2` |
| topic | 398 | `R2` |
| 别名 | 37,327 | `R2` |
| 卷侧车 | 19 | `R3` |
| 教学材料 | 50,383 | `R3` |
| 材料 type：三值内 | CONCEPT_EXPLANATION 29,478 / METHOD_MODEL 18,017 / MISCONCEPTION_GUIDE 2,221 | `R3` |
| 材料 type：三值外（旧口径） | **667**（REPRESENTATION_GUIDE 491 / DERIVATION 150 / WORKED_EXAMPLE 22 / COMPLETE_SOLUTION 4） | `R3` |
| `reviewedAtEpochMillis` 不同值 | 42（0 条缺失；范围 2026-07-24～2026-10-02） | `R3`（取值范围引 `build/agent-outstanding/group-1.md:279-281`） |
| dense 向量 | 41,193 = 3,866 节点 + 37,327 别名（dim 512、int8） | `R5` |
| dense 与包同源 | `.vec` 旁车 `packSha256` = `647675cd98f52ca8…` = 主包 sha256（本会话实算一致） | `R5` |

存量质量数字（全部本会话复算或引实测卡）：

| 指标 | 值 | 复算命令 / 出处 |
|---|---|---|
| 超配节点（≥4 条 / >4 条 / =4 条） | **2,754 / 2,518 / 236** | `R4` |
| 超配节点持有材料 / 超出前 4 的绑定 | 47,538 / **37,466** | `R4` |
| 单节点材料最大值 | 139（BIOLOGY「基因工程」） | `R4` |
| 零材料节点 | 0（3,866 节点全部有绑定） | `R4` |
| 有绑定节点数 | 3,866（≥4 的 2,754、>4 的 2,518） | `R4` |
| 三值外旧 type | 667（见上表） | `R3` |
| 容量拦截 | `knowledge-production/candidate-merge/capacity_blocked.csv` **91 行**，91 个 slug 与现包逐条比对 **0 条已入包** | `R7`（行数）+ 本会话逐条比对脚本 |
| 薄料台账 | `knowledge-production/node-material-gaps-2026-09.csv` **661 行**（全部 `tier=thin`、`material_count=1`）；live 重算 = 零材料 **0** / 仅 1 条 **571** | `R7`（行数）；live 值见 `build/agent-outstanding/group-1.md:224-227`（命令 `PYTHONPATH=tools python -m kb_build.report_material_gaps`） |
| 机械 SKIP 块 | `tools/kb_coverage/tables/mechanical_skips.csv` **107,160 行** | `R7` |
| 判定表 | `tools/kb_coverage/tables/material_judgments.csv` **86,414 行**：MATERIAL 38,929（全部三值内）/ SKIP 47,485（旧 type 482 行全在此） | `R8` |
| 课标覆盖台账 | COVERED **352** / PARTIAL **73** / NOT_COVERED **236** / UNDECIDED **0**，分母 **661**；逐科 MATH 130/308=0.4221、PHYSICS 71/138=0.5145、CHEMISTRY 51/73=0.6986、BIOLOGY 100/142=0.7042 | `R6` |
| 拆分提案 | 182 条候选 / 25 个模块（MATH 28/3、BIOLOGY 154/22）；`reviewState=NOT_STARTED`、`decisions=0` | `R9` |
| 活体错绑嫌疑 | `audit_bindings_by_alias` 实测 **75 条**（已裁定豁免 21 条）；磁盘 `tools/kb_build/tables/binding_suspects.csv` **0 行** | `R10` |
| D-1 绑定候选 | `audit_content_bindings` 实测 **496 条**（靶区 161 节点） | `R10` |
| 53 知识清单材料 | 50,383 条中 **5,053 条** `sourceLocator` 含「53知识清单」 | `R11` |
| 2027-53 转写 | 账本 1,205 页全 `gate=pass`；verdict ACCEPT 1,203 / UNCERTAIN 2（PHYSICS p41/p79） | `R12` |

### 1.2 门与 CI 的真实覆盖

**绿的部分（本会话实跑）**
- 内容门：`PYTHONPATH=tools python tools/kb_build/gate.py` → 打印 23 项，**全部为 0，退出码 0**（本会话逐行计数 23 项）。注意：`python tools/kb_build/gate.py` **原样跑不通**（本会话实测 `ModuleNotFoundError: No module named 'kb_build'`），必须带 `PYTHONPATH=tools`——基线 §5 的命令写法需订正。
- 契约/一致/往返/清单/dense：`python tools/ci/run_kb_checks.py` → `gates / consistency / roundtrip / manifest / dense` **5 节全 OK**，退出码 0。
- 判定自检：`PYTHONPATH=tools python -m kb_coverage.check_slice_verdicts` → 「已检 232 个判定文件 / 70,072 块：MATERIAL 23,292、SKIP 46,780、NEW 提案 218 / 不过 0 个」，退出码 0。
- 裁定表复算：`PYTHONPATH=tools python -m kb_build.merge_content_audit_verdicts --check` → 「复算一致：409 行（表 == 8 个切片去重排序后的并）」「REBIND 引文对不上材料正文的行：1 / 56」，退出码 0。
- 转写门：`PYTHONPATH=tools python -m kb_coverage.check_transcripts` → 「页数 1205｜已转写 1205｜缺失 0」「闸门：{'pass': 1205}」，退出码 0（**订正基线「转写门仍 exit 1」**，见 §1.4）。
- 本地 Python 套件：本会话实跑 `PYTHONPATH=tools python -m unittest discover -s tools/tests -t tools` → **`Ran 586 tests in 128.638s` / `OK` / 退出码 0**（与 `build/agent-outstanding/group-5.md:119` 的 `Ran 586 tests … OK` 一致）。⇒ CI 的 13 条红是「干净检出缺 `build/` 产物」与 fixture 差异，不是代码回归。

**红的部分（本会话实跑/实读）**
- CI：`gh run list --limit 6` → 最新 main push run `36965281642`（2026-10-02T04:36Z）结论 **failure**；`gh run view --job 110707577008 --log` 本会话复读 → `FAILED (failures=6, errors=7, skipped=1)`。红因是**干净检出缺 `build/` 产物**（7 条 ERROR 全是 `缺 manifest：build/2027-53-pages/manifest_slim.json`）与 2 个 fixture 测试（6 条 FAIL），不是代码回归（`build/agent-outstanding/group-5.md:113-119`）。因 step 7 红，其后 8–19 步全 skipped。
- `docs/status.md` 是 2026-09-28 的 `local-manual` 快照（本会话读头部：Commit SHA `a2e8347f…` / Triggered By `local-manual`）；其「权威表 sha256」6 条中 **2 条已与工作树不一致**（本会话实算：`tools/kb_build/tables/chapter_by_source.csv` 页面 `caa77eacaef3` vs 实况 `8efce39f8e61`；`tools/kb_build/tables/alias_map.csv` 页面 `8bc62dd5824a` vs 实况 `2210f01b2c72`），因两表随 `d63f517c` 改动而 status 未重生成。
- 门**覆盖不到**的东西（组⑥核心）：23 项全是机械判据，没有一项读材料 type、没有一项判断「材料是否与绑定节点对题」（`build/agent-outstanding/group-6.md:134`）；内容戳（`tools/kb_build/update_manifest.py:256-266`）只回答「是哪一版」。**「全绿」与「口径正确」无关系**：同一份包内躺着 667 条协议外旧 type 材料。
- 材料级检查现状（本会话新发现）：`PYTHONPATH=tools python -m kb_coverage.check_exam_leak` → **退出码 1**：MATERIAL 行 38,929 中「例题派生」命中 **206**（涉及 164 个 node_slug）——按判据 119 条「正文含答案/故选等判定语」、85 条「正文是短题干且无结论」、1 条「标题是题号/来源」、1 条「正文含成套选项 A/B/C/D」。该检查具备 exit 0/1 守卫语义（`tools/kb_coverage/check_exam_leak.py:17`）但**未被 tools/kb_build/gate.py / run_kb_checks / CI 调用**（`build/agent-outstanding/group-6.md:137`）。

### 1.3 运行时接线真值表（库→学生，逐条实读本会话）

| 面 / 入口 | 代码锚点 | 真实行为 | 证据 |
|---|---|---|---|
| 有题轮 KNOWLEDGE_READ | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RoomTutorTeachingReferenceRepository.kt:20-33,120-145`；`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/KnowledgePort.kt:133`；`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorTasks.kt:357` | 有 subject ⇒ 正常取材料；取数上界 1,024 条/整批；**无 per-node 条数门**；按 `roleRank→typePriority→title→materialId` 排序后按 **20,000 字符预算**逐条装填，条数不限 | 本会话实读 `:126-134,137-145`、两常量实读 |
| 无题轮（大厅）KNOWLEDGE_READ | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/TutorToolContext.kt:26-30`；`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt:194-204,226-239` | 大厅输入无 subject（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorLobbyTasks.kt:15-73`）⇒ subject=null ⇒ 恒回空范围：`ok=true`、`resultCount=0`、「本轮无可读范围…不必重复查询」 | 本会话实读两处 |
| 大厅工具声明 | `feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorLobbyModelTaskPolicy.kt:94-97` | **仍声明全量 5 个工具**（注释自述「同页声明全量五个」）；KNOWLEDGE_READ 在授权矩阵可执行（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolLoop.kt:408-410`），空范围话术已含「不必重复查询」 | 本会话实读 `:94-97` |
| 大厅披露集合 | `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelEgress.kt:403-406` | `TUTOR_LOBBY_DISCLOSURE` 只有 STUDENT_TUTOR_MESSAGE + TUTOR_CONVERSATION_CONTEXT，**不含 SUBJECT_KNOWLEDGE_BASE**（有题轮含；`:386-387`）⇒ 走逐次清单的外部 provider 就算补了 subject 也会撞披露精确相等校验 | 本会话实读 |
| MASTERY_UPDATE 回执 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt:917-923,944-951`；`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolTrace.kt:44-51` | 接受/被拒两种结果都**不含写了哪个节点**（只有 direction/weight 或拒因）；学生痕迹字段只有 tool/resultCount/ok/errorKind | `build/agent-outstanding/group-2.md:77-81`（本会话未逐行复读） |
| 材料正文展示（重教卡/前置补救卡） | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomBackedStudyExperienceRepository.kt:438-439,451-461,473-497`；`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyFixtureSource.kt:26-43` | `teachingArtifact()` 只读 fixture source，生产默认 `EmptyStudyFixtureSource` ⇒ **恒 null** ⇒ 今天生产**没有向学生展示材料正文的路径** | `build/agent-outstanding/group-2.md:155-156`（卡片实读） |
| 覆盖/缺口 `knowledgeCoverage` | 生成链 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomBackedStudyExperienceRepository.kt:244,273-278,321-326,832`；`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudySnapshotBuilder.kt:44,136`；域 `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/StudyExperienceRepository.kt:189` | 数据生成链完整；**app/feature 零消费者**（grep 本会话：生产命中只在 core） | 本会话 grep + 卡片 |
| grounding 闭环 | 生产端 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiProblemOrganizationProtocol.kt:218-227`→`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeOrganizationRepository.kt:371-386`（落库并 `applied=false`）；解析端 `observePendingKnowledgeGroundingRequests` / `resolveKnowledgeGrounding` / `applyReviewedKnowledgePack` | 请求**只产不读**：生产调用者 0（本会话 grep 只命中 core:database 内部转发）；学生侧 UI 停在「暂未整理完整」 | 本会话 grep + `build/agent-outstanding/group-2.md:101-108` |
| 知识研究与审校管线 | `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeResearch.kt:141-155,231-301`；data 实现 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RoomKnowledgeResearchReviewQueue.kt` / `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RemoteKnowledgeResearchSourceVerifier.kt` | 接口与实现齐备、**零生产驱动方**（构造点只在测试）；`KnowledgeResearchSearchGateway` 一个实现都没有 | `build/agent-outstanding/group-2.md:177-184` |
| 知识库浏览入口 | 本会话 grep：`readSubjectKnowledgeNodes` 唯一 `src/main` 生产调用是 `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt:151`（空搜索特征回退） | **无浏览入口**；刚需屏只有「有学习记录」的掌握总览（`app/src/main/kotlin/com/tingyun/smartmistakebook/LearningMasteryScreen.kt:59-60,73-78`）；DAO 无 OFFSET、limit≤256 | 本会话 grep（订正基线的「零生产调用者」表述，见 §1.4） |
| 材料选择排序权威 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RoomTutorTeachingReferenceRepository.kt:70-79`（7 值 reTeachPriority） | 667 条旧 type 正在参与端侧重教排序；`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/KnowledgeTeachingMaterialDao.kt:45-58` 按 type CASE 排序 | `build/agent-outstanding/group-1.md:147-150` |

### 1.4 与基线/文档冲突：订正表（采用值 + 依据）

| # | 项 | 基线/文档口径 | 本会话/事实卡实测 | 采用 | 依据 |
|---|---|---|---|---|---|
| 1 | D-1 绑定候选数 | 325（`docs/agent-first-refactor-decisions-2026-09-23.md:2304,2318`） | **496**（靶区 161） | **496** | 本会话实跑 `R10`；差因：包从 3,570→3,866 点后重算（`build/agent-outstanding/group-1.md:323-325`） |
| 2 | 近重复前缀包含对 | 28 对（基线 §2 组③；closeout:165） | **18 对**（同名组 11 组吻合） | **18 对** | 本会话实跑 `python tools/kb_build/scan_duplicate_nodes.py` 首行 |
| 3 | 薄料 | 661（台账文件） | 台账文件确为 661 行；live = 零材料 0 / 仅 1 条 **571** | 两者分列：文件 661、缺口 **571** | 本会话数行 + `R7`；live 命令引 `build/agent-outstanding/group-1.md:224-227` |
| 4 | 转写门 | 「35 页未清判、门仍 exit 1」（基线 §2 组⑤） | 1,205/1,205 pass，**exit 0**；剩 2 页 UNCERTAIN | **exit 0** | 本会话实跑 `R12` |
| 5 | 仪器化三门本机不可跑（KD-25） | 「本机不可跑」 | 可跑且三过（BUILD SUCCESSFUL 4m48s、XML tests=3 failures=0） | **可跑** | `build/agent-outstanding/group-5.md:140-145`（含与 `docs/kb-dense-rebuild-2026-10-01.md:343` 的自相矛盾） |
| 6 | 门数 | 多处写「22 门」 | **23 门** | **23** | 本会话 gate 输出 23 行全 0（`R13`） |
| 7 | 基线 §5 复算命令 | `python tools/kb_build/gate.py` 原样 | `ModuleNotFoundError` | `PYTHONPATH=tools python tools/kb_build/gate.py` | 本会话实跑 |
| 8 | 「17 unmatched」 | `docs/kb-point-intake-2026-10-01.md:19,101` | 现有产物只有 plan json `bad=9` 与 `build/agent-input/reland_worklist_unmatched.csv` 532 行 | **UNVERIFIED（不可复现）** | `build/agent-outstanding/group-1.md:331-333` |
| 9 | 「8 条元素及其化合物」 | 文档写 8 | 产物只有 **7** 条（砷/硼/钛/铅/银/锌/镍） | **7** | `build/agent-outstanding/group-1.md:334-335` |
| 10 | 绑定链工具名 | `material_rebind.py` | 全仓不存在；实际 = `tools/kb_coverage/apply_rebind_verdicts.py` + `tools/kb_build/rebind_materials.py` | 实际两工具 | `build/agent-outstanding/group-1.md:336-338` |
| 11 | `tools/kb_build/tables/binding_suspects.csv` 0 行 | 读作「无嫌疑」 | 0 行 ≠ 无嫌疑；live 扫描 **75 条** | **75 条待裁定** | 本会话实跑 `R10` |
| 12 | 25 个模块「永久 UNDECIDED」 | 基线 §2 组③ | 台账 v2 全表 UNDECIDED=0；风险是「分母不完整/待原子化」 | 表述订正 | `build/agent-outstanding/group-3.md:168` |
| 13 | 教材元数据目录 | register 称 ACQUIRED_REVIEWED（3229 条） | 文件不在盘、`.artifacts/` 未跟踪 | **UNVERIFIED** | `build/agent-outstanding/group-3.md:169` |
| 14 | 覆盖候选数 1300 | `docs/research/coverage-baseline-design-2026-09-15.md:168` | `knowledge-production/knowledge-coverage-candidates-2025-v1.json` = **1325** 条 | **1325** | `build/agent-outstanding/group-3.md:170` |
| 15 | 安全扫描读数 | KD-14 旧「0 high/0 medium/3 low」 | 最新封存 5 high/0 medium/7 low，`runStatus=inconclusive` | **不得声称安全**；以封存件为准 | `build/agent-outstanding/group-5.md:163-169` |
| 16 | dense README 旧数 | 基线只列 `core/data/src/main/assets/dense/README.md:22,45` | `:45` 是 28,931（现值 41,193）；`:22` 过期点是 **n=290**（现 fixture count=330）；另漏 `tools/dense_build/README.md:204`、`tools/dense_build/check_asset.py:114` | 见 ⑥-6 清账 | `build/agent-outstanding/group-6.md:152-153` |
| 17 | 运行时「每节点 ≤4 条」 | 工具/台账曾按此裁 | **无此门**：整批 1,024 取数 + 20,000 字符预算 | **以运行时为准** | 本会话实读 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RoomTutorTeachingReferenceRepository.kt:120-145` |

---

## 2. 未竟清单

> 编排说明：每条七项（事实 / 对学生的影响 / 怎么做 / 依赖 / 验收判据 / 预计工作量）。跨组重复已合并并注明；与基线冲突处按 §1.4 订正。
> 组①⑥ 部分条目互为一体：①-2 ↔ ⑥-4、①-3 ↔ ⑥-3、①-4 ↔ ⑥-1、①-1 ↔ ⑥-7，前向条目里写全，另一处只留指针。

### 组① 内容可信度收口

#### ①-1 D-1 绑定语义裁定（候选实测 496 条，验收门「语义精确率 ≥0.95」）

- **事实**：本会话实跑 `PYTHONPATH=tools python -m kb_build.audit_content_bindings` → 「靶区节点 161 个」「候选行 **496**」：foreign-title-match 298 / own-title-alias-only 143 / own-subject-detached 45 / own-title-detached 4 / registered-anchor 6；按科 CHEMISTRY 282 / PHYSICS 182 / MATH 26 / BIOLOGY 6；按片 slice-02 49、slice-03 3、slice-04 17、slice-05 6、slice-07 21、slice-08 5、**不在切片 395**。**与既有裁定的实际重合远小于切片内条数**（订正）：既有 409 行裁定表（`tools/kb_build/tables/content_audit_2026-09-25.csv`，切片分布 slice-05 157 / slice-02 64 / slice-08 51 / slice-06 40 / slice-07 34 / slice-01 31 / slice-04 18 / slice-03 14）与本轮 496 候选按 `material_slug` 交集只有 **23** 条（按 material+current_node 20 条、按 subject+current_node 19 条；本会话实测）；本轮切片内 101 条中 23 条有既有裁定、78 条是新增；且既有表里的 slice-01（31）与 slice-06（40）在本轮候选中为 0 条。⇒ 可复用的裁定远少于 101 条，返工量按 496 条计（本会话实测）。候选表 `verdict` 列由 `tools/kb_build/audit_content_bindings.py:318` 写死空串，`--write` 会覆写同路径裁定表（`tools/kb_build/tables/content_audit_2026-09-25.protocol.md:9-10` 已警告）。已裁定表实读：`tools/kb_build/tables/content_audit_2026-09-25.csv` 409 行，KEEP 330 / REBIND 56 / NONE 23；`tools/kb_build/tables/material_rebind.csv` 1,246 行；`tools/kb_build/tables/binding_suspects_reviewed.csv` 204 行（REBIND 183 / KEEP 21）。执行链：`tools/kb_coverage/apply_rebind_verdicts.py` → `tools/kb_build/rebind_materials.py`；报告模式实测「改绑 0；跳过 1,246；材料总数 50,383；悬空绑定 0」⇒ 改绑表全量已生效。验收门口径：精确率 ≥0.95（`docs/agent-first-refactor-decisions-2026-09-23.md:2352`）；报告须写明「标注者为模型、单一来源、未做人类双复核」（`:2357-2358`）。
- **对学生的影响**：绑定=掌握度的坐标系。误绑一条 = 一批证据落到错误知识点，掌握度/复习排期/重教全部指错；当前全库绑定正确率仍未测量，`bindings[]` 也没有置信度字段（`:2305-2307`），判定无处落账。
- **怎么做**：①冻结 496 候选与 161 靶区（只读）；②按超配/薄料/普通三层重切切片，逐条读正文裁定，产物落 WP2 同构表（勿对既有 409 行表跑 `--write`）；③REBIND 走 `tools/kb_coverage/apply_rebind_verdicts.py --write` → `rebind_materials --write`；④复算 `merge_content_audit_verdicts --check`（exit 0）。
- **依赖**：裁定（是否维持 ≥0.95 与分层抽样口径，见 F10）；外部（无人类专家金标可用）。
- **验收判据**：语义精确率 ≥0.95；候选表与裁定表分离且各自可复算；`--check` exit 0；`rebind_materials` 报 0 待写、0 悬空。
- **预计工作量**：大。496 条分层抽样 + 逐条读正文，按每代理 40–60 行计约 8–12 个代理轮次；另加 75 条别名嫌疑（①-10）。

#### ①-2 近重复节点（同名 11 组 / 22 节点；前缀包含 18 对，订正 28）

- **事实**：本会话实跑 `python tools/kb_build/scan_duplicate_nodes.py` → 首行「规范化完全同名组：**11 组**」「前缀包含对：**18 对**」（逐组 2 点 = 22 节点；样例「棱切球（与各棱相切的球）」vs「棱切球」、「装置气密性检查」vs「装置气密性检查的方法」）。`knowledge-production/candidate-merge/near_duplicate_review.csv` **2 行**（本会话数行；CHEMISTRY 乙烯… / MATH 特殊的棱柱和棱锥）。扫描器**全仓无调用者**（`build/agent-outstanding/group-1.md:80-82` 的 grep；本会话复跑 `grep -rn scan_duplicate_nodes --include=*.py` 0 命中），且是「导入即执行」脚本、无 `main()`、不落任何 CSV（`build/agent-outstanding/group-6.md:74-75`）。⚠ 其关联产物（`knowledge-production/candidate-merge/near_duplicate_review.csv` / `knowledge-production/candidate-merge/duplicate_dropped.csv`）的生成逻辑在当前**未提交工作树**（`tools/kb_build/merge_candidate_verdicts.py` +40 行），随该线提交（见 §6）。
- **对学生的影响**：同一知识点两条节点 ⇒ 材料在两者间来回搬，掌握度被拆成两份，检索/复习出现「同名双点」。
- **怎么做**：①逐组裁定合并/保留并写理由（合法细分如「梅涅劳斯定理」vs「梅涅劳斯定理和塞瓦定理」）；②同名 11 组优先，18 对按「空壳 vs 合法细分」逐对判；③2 组按合规规范复核互补性；④扫描器**产品化**（`main()` + `--out` 写 CSV + 测试）或归档并标注「一次性」。
- **依赖**：裁定（合并口径：机械同名 vs 合法细分；跨册同名涉章节归位）。
- **验收判据**：11 组 / 18 对每组有裁定记录；合并后 `promote --dry-run` 全绿（门 23/23、材料数不变、无悬空）；2 组清零或有结论；扫描器要么有测试与产物、要么有归档标注。
- **预计工作量**：小–中（1–2 代理轮）；扫描器产品化 0.5–1 人时。

#### ①-3 超配节点与节点粒度（2,754 / 2,518 / 37,466；最大 139；运行时无 per-node 上限）

> 与 ⑥-3（`tools/kb_build/overfull_nodes.py` 跑不通）合并为本条；⑥-3 不再单列。

- **事实**：本会话只读复算（`R4`）：3,866 节点全部有绑定；**≥4 条 2,754 / >4 条 2,518 / =4 条 236**；>4 节点持有 **47,538** 条，超出前 4 的绑定 **37,466**；>10 1,595 / >20 828 / >50 114 / >100 6；最大 **139 = BIOLOGY「基因工程」**；零材料节点 0。运行时无条数门：取数上界 1,024（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/KnowledgePort.kt:133`）、全局字符预算 **20,000**（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorTasks.kt:357`）、排序 `roleRank→typePriority→title→materialId`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RoomTutorTeachingReferenceRepository.kt:126-134`），该文件注释自述「没有条数上限之后…预算通常装得下（一题 5 个中位节点约 3,800 字符，预算 20,000）」。工具腐烂：`tools/kb_build/overfull_nodes.py` 本会话实跑 → `KeyError: 'materials'`（`:75` 把卷索引 glob 进 load，改法工具自述 `:17-21` 走 `pack_io.sidecar_paths()`）；自述数字 `:11-13` 仍是 1,670/23,825/17,145（旧）；机制描述 `:9-10` 仍是「排前面的 4 条进预算，其余一条也拿不到」（与运行时代码相反）。上游生成器仍按 4 裁（`tools/kb_build/merge_candidate_verdicts.py:165` `MAX_VISIBLE = 4`）。
- **计数口径提示（口径敏感）**：`R4` 按 **(subject, slug)** 聚合、且每条材料只取其**首个绑定**（本会话确认 50,383 条材料全部恰好 1 条绑定）。包内有 9 个跨科同名 slug（如「基因工程」「核酸」「糖类」「蛋白质」「内能」），若按 slug 单键聚合会得到 ≥4 = 2,757 / >4 = 2,522 / 最大 140 的不同数字；本报告一律采用 (subject, slug) 口径。
- **对学生的影响**：139 条材料的节点里只有按排序靠前的部分进 20k 预算；排序与质量无关；同时 571 个「只有 1 条材料」的节点在同一批排序里可能被挤——学生问 A 点可能拿不到最该看的材料。工具 docstring 传播的错误机制（「第 5 条起永远看不见」）会诱导「反正用不上」的错裁决。
- **怎么做**：①先修 `tools/kb_build/overfull_nodes.py`（load 走 `pack_io.sidecar_paths()`、更新自述数字与机制、加合成夹具测试，见批次 0）；②用修复后工具产出施工清单 CSV（每超配节点全部绑定各一行，预期 47,538 行）；③按三档分层处置、**不按 ≤4 裁**：该拆点（超配且混多考点）/ 该合并（近重复）/ 该删冗余跑题；④改绑走 `tools/kb_build/tables/material_rebind.csv` 链。
- **依赖**：F3 处置口径；工具修复（批次 0，零裁定）。
- **验收判据**：`PYTHONPATH=tools python -m kb_build.overfull_nodes` exit 0，首行打印「2,518 个超配…超出 37,466」；新增测试绿；`--write` 行数 = 47,538；每个超配节点有裁决记录；`promote --dry-run` 全绿。
- **预计工作量**：大（2,518 个超配节点分层 + 施工清单；与 91 条容量拦截同批）。

#### ①-4 667 条三值外旧 type（端侧按 7 值排序参与调度）

> 与 ⑥-1（`tools/kb_coverage/materialize.py` 白名单 7 值）合并为本条；⑥-1 不再单列。

- **事实**：本会话复算（`R3`）：50,383 条中 **667** 条三值外——REPRESENTATION_GUIDE 491 / DERIVATION 150 / WORKED_EXAMPLE 22 / COMPLETE_SOLUTION 4。端侧确实按 type 排序：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/KnowledgeTeachingMaterialDao.kt:45-58`（CASE 0…6、ELSE 7）+ `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RoomTutorTeachingReferenceRepository.kt:70-79` 的 7 值 `reTeachPriority`。写入侧白名单**分裂**：`tools/kb_coverage/materialize.py:50-51` 仍 7 值，`tools/kb_coverage/merge_text_judgments.py:44-45` 已 3 值（协议 v1.1）；协议 `docs/kb-chunk-judgment-protocol.md:86` 规定只许 3 值。卡片实测：内存调 `materialize.plan()`，4 种旧 type 的 `errors` 均为 `[]`（放行），填 `BOGUS_TYPE` 才拒（`build/agent-outstanding/group-6.md:16`）。当前判定表已收敛：MATERIAL 38,929 行全 3 值；旧 type 482 行全在 SKIP（`R8`，本会话实测）；重判队列 `tools/kb_coverage/tables/rejudge_queue.csv` 698 行（519/150/25/4）。protocol 文档 `:95-101` 的「698 行 MATERIAL 用旧 type」「merge 白名单现含 7 值」均已过期。
- **对学生的影响**：667 条旧 type 在包内参与重教排序，其中 REPRESENTATION_GUIDE（优先级 5）与 COMPLETE_SOLUTION（6，最低）在预算争用中天然靠后；且协议已禁用的形态仍可能被选中进提示词。materialize 是**潜在放行漏洞**（不是当下在漏，见订正）。
- **怎么做**：①批次 0 把 `tools/kb_coverage/materialize.py:50-51` 收成 3 值（逐字与 merge 一致），更新 protocol `:95-101`；②定迁移映射（裁定 F7）：REPRESENTATION_GUIDE/DERIVATION→CONCEPT_EXPLANATION、WORKED_EXAMPLE→METHOD_MODEL、COMPLETE_SOLUTION 另定；③走重判闭环消耗 698 行：`make_judgment_slices` → 判定 → `merge_text_judgments` → `apply_rejudged_materials`，以 3 值重判替换包内 667 条；④type 门（包内 `materials[].type ∈ 3 值`）在 F7 定了处置后再接——直接接当前**必红**（667 命中），这是有意的「让欠账可见」。
- **依赖**：F7（重判替换 vs 冻结豁免）。
- **验收判据**：`tools/kb_coverage/materialize.py` TYPES 与协议 3 值逐字一致；包内三值外计数 = 0（或有具名豁免清单 + 计数门）；`materialize --dry-run` 0 error；门 23/23 不破。
- **预计工作量**：白名单收紧 + 测试 0.5–1 人时；重判替换 2–4 个代理批次。

#### ①-5 91 条容量拦截（前提「节点满」已被运行时代码推翻；0/91 已入包）

- **事实**：`knowledge-production/candidate-merge/capacity_blocked.csv` **91 行**（本会话数行；91 个不同 slug / 71 个节点；`existing_materials` 4–53）。生成前提在 `tools/kb_build/merge_candidate_verdicts.py:165`（`MAX_VISIBLE = 4`）与 `:209-216`（≥4 即拦，reason 写「新料不可见」）——运行时已无条数门，所以「新料不可见」不成立（取决于排序与字符量）。本会话把 91 个 slug（node_slug/slug 两列）与 19 卷逐条比对：**0 条已入包**。
- **对学生的影响**：91 条补料被一条已被推翻的规则挡在库外——学生本该看到的补充材料直接缺失；「缺口已补」的账目失真。
- **怎么做**：重新逐条三选一：①直接落（判定源改 MATERIAL → `merge_text_judgments --resync` → `materialize --write`）；②先按粒度清理目标节点（改绑/删冗余）再落；③放弃并写明理由。工具：`merge_text_judgments`、`tools/kb_coverage/materialize.py`、`tools/kb_coverage/apply_rebind_conflicts.py`、`tools/kb_build/drop_materials.py`。
- **依赖**：F3（与超配 2,518 同一处置口径）。
- **验收判据**：91 行每行有裁决记录（落/清理后落/弃）；落下的材料在包内可查、绑定正确；未落的写明原因；`knowledge-production/candidate-merge/summary.json` 的 `capacity_blocked` 不再由已废止规则产生。
- **预计工作量**：中（91 条 × 71 个节点核查 + 落库流程）。

#### ①-6 32 条 low 置信归属复核（执行已完成，剩 1 条不符）

- **事实**：产物实际路径 `build/agent-input/rebind_conflicts_verdict.csv`（不是基线写的 `rebuild_conflicts_verdict.csv`）；本会话数行 **102 行**，confidence = low **32** / high 70。卡片逐行对照：32 条 low 材料 **32/32 在包**，**31/32 现绑 == decided_node**；唯一不符 `ext-bio-02a053bbf3-003`（decided「有丝分裂」，现绑「细胞不能无限长大的原因」，材料标题《细胞不能无限长大的原因与模型分析》）。执行链见 `docs/kb-organic-intake-closeout-2026-09-30.md:90-104`。
- **对学生的影响**：low = 两种读法都有依据（两轮取同一块不同子节 / 两侧节点名同义）；错一条即材料挂错知识点。执行链已把 31/32 落到位，但**裁决本身没有人类/独立复核**。
- **怎么做**：全量复核 32 条（成本低）：读材料正文 + 两侧节点边界；重点核实 1 条不符——先确认是否后续轮次按内容改绑，若是则补记录，否则走 `tools/kb_build/tables/material_rebind.csv` 链改回。
- **依赖**：抽样比例（建议全量 32——每代理一轮即可）；外部（人类专家不可得，复核仍由模型做）。
- **验收判据**：32 条逐条有复核记录；不符 1 条给出处置（改绑或保留 + 理由）；`rebind_materials` 报 0 待写。
- **预计工作量**：小（约 1 个代理轮）。

#### ①-7 薄料账本已陈旧（台账 661 行 vs live 571；275 行仍 thin、384 行已 ≥2 条）

- **事实**：台账 `knowledge-production/node-material-gaps-2026-09.csv` 本会话数行 661，全部 `tier=thin`、`material_count=1`（按科 PHYSICS 269 / MATH 197 / CHEMISTRY 175 / BIOLOGY 20）。live 重算（`build/agent-outstanding/group-1.md:224-227`）：缺口共 **571**（零材料 0 / 仅 1 条 571；BIO 141 / CHEM 133 / MATH 158 / PHYS 139）。逐行对照现包：2 个 slug 已不在包、**275 行仍是 thin**、**384 行已 ≥2 条**。生成器 `tools/kb_build/report_material_gaps.py:18-19,34`。
- **对学生的影响**：571 个知识点只有一条材料可讲；若把陈旧台账当权威（派补料单、验收对账），会对着已加厚/已消失的节点派活，缺口账目失真。
- **怎么做**：①`PYTHONPATH=tools python -m kb_build.report_material_gaps --write` 重算台账（写生产台账需在允许范围内执行）；②补料按 thin 清单（块池干净块或新来源），走判定源 → `merge_text_judgments --resync` → `materialize --write`；③验收口径：zero 恒 0、thin 单调递减（当前 0 / 571）。
- **依赖**：环境（`--write` 写生产台账需许可）；外部（补料来源；部分节点块池无干净来源，见 `KNOWN_GAPS`）。
- **验收判据**：`report_material_gaps` 输出 zero=0；thin 计数不增；台账与现包逐行一致（0 陈旧行）。
- **预计工作量**：中（重算 + 登记本轮；571 条补料本身是长期工作）。

#### ①-8 8 个撤回空点 + 9 条 bad 提案（「17 unmatched」不可复现）

- **事实**：8 个撤回点（`常见组合函数的图象`、`物理选择题的常用解题方法与技巧`、`胚芽鞘生长情况的判断方法`、`卤素的提取`、`微型工业流程选择题解题策略`、`碳元素在自然界中的循环`、`碳酸与碳酸盐`、`能量图像分析`）卡片实测 8/8 不在包；`tools/kb_build/tables/point_delete.csv` 尾部 8 行 reason=「建点后块过薄/图未提取，写不出材料——按建点闭环规则撤回」。未建提案：本会话实读 `build/agent-input/block_proposals_plan.json` → `plan 304 / rebind 37 / bad 9`；bad 9 = 7 条元素化学（砷/硼/钛/铅/银/锌/镍及其化合物的性质）+ 乙烯制环氧乙烷的工艺选择 + 平面的概念与表示（清单引 `build/agent-outstanding/group-1.md:255-258`）。文档写「17 unmatched + 9 bad」「8 条元素化学」——现有产物**无法复现 17**（标 `UNVERIFIED`），元素化学实测 7 条。
- **对学生的影响**：8 个空点=学生问到这些考点时零材料（补需换来源，如图 OCR）；7 条元素化学提案=正式区确缺的考点（砷/硼/钛/银/锌/镍/铅）一直缺席。
- **怎么做**：①元素化学 7 条：若认可「X 及其化合物」考点，用 `plan_block_proposals` 清洗命名 → 追加 parent 校验（`tools/kb_coverage/append_new_points.py`）→ `create_points --write` → 材料重落；否则明确不建并写进文档；②其余 2 条 bad 逐条定处置；③8 空点评估新来源或维持撤回。
- **依赖**：裁定（是否认可元素及其化合物类考点、空点是否补）；外部（图 OCR、新来源）。
- **验收判据**：9 bad 每条有处置记录；8 空点有「补源/维持撤回」结论；若建点则材料落包且零材料节点保持 0；`tools/kb_build/gate.py` 23 门不破。
- **预计工作量**：中（9 条建点/裁决 + 8 空点来源评估）。

#### ①-9 材料时效/版本（42 个时间戳值、无时效检查、来源新旧混用）

- **事实**：本会话复算（`R3`）：全包 50,383 条材料 `reviewedAtEpochMillis` **42 个不同值、0 条缺失**；范围 1,784,908,800,000（2026-07-24）～ 1,790,911,715,772（2026-10-02），最多的值 1,790,875,653,147（17,889 条）、1,784,908,800,000（10,524 条）。无时效门：`grep -rn reviewedAtEpochMillis tools` 只命中 `tools/curriculum_coverage/promotion.py`（课标审校生命周期）与材料写入侧；`tools/kb_build/gate.py` 23 门里没有任何时效门（`build/agent-outstanding/group-1.md:282-285`）。同一批材料 `sourceLocator` 跨「桌面资料 27版五三 / 2026年新高考资料 / 2026/2027 一轮复习讲义 / 53知识清单」等多个来源与年份。
- **对学生的影响**：材料可能引用旧课标/旧教材/旧考纲的表述（旧版实验结论、已删考点）；过时不可发现、不可审计。
- **怎么做**：①定口径（F15：来源版次/年份 vs reviewedAt；阈值 N 年）；②把 42 个时间戳归一为来源版次台账（`knowledge-production/source-register-2025-v1.json` 补版次/时间字段）；③加分层警告或门（先出报告，再进 `tools/kb_build/gate.py`）。
- **依赖**：F15；外部（各来源的版次/时间信息，实体书需核对）。
- **验收判据**：时效字段覆盖 50,383/50,383；超期清单可复算且数字与台账一致；门（或报告）能显示过期材料数。
- **预计工作量**：中–大（口径 + 42 组来源登记 + 门）。

#### ①-10 活体错绑嫌疑 75 条（基线只写「`tools/kb_build/tables/binding_suspects.csv` 0 行」）

- **事实**：本会话实跑 `PYTHONPATH=tools python -m kb_build.audit_bindings_by_alias` → 「**错绑嫌疑 75 条**（已裁定豁免 21 条）」；按科 CHEMISTRY 30 / BIOLOGY 25 / PHYSICS 13 / MATH 7；样例：〈互斥事件与对立事件〉现挂「概率的基本性质」、〈诱导契合学说〉现挂「酶的作用和本质」、〈DNA的粗提取与鉴定〉现挂「生物学实验与技术方法」等。逻辑 = 嫌疑表减 `tools/kb_build/tables/binding_suspects_reviewed.csv` 豁免（`tools/kb_build/audit_bindings_by_alias.py:92-97`）；磁盘 `tools/kb_build/tables/binding_suspects.csv` 本会话实测 **0 行**——「0 行」≠「无嫌疑」。
- **对学生的影响**：75 条材料标题就是另一个节点的名字却挂在当前节点（上位概念收下位材料合法，需逐条判）——误挂则学生问 A 时看到 B 的内容。
- **怎么做**：`--write` 落 `tools/kb_build/tables/binding_suspects.csv` → 逐条读正文裁定（KEEP/REBIND/NONE）→ `tools/kb_coverage/apply_rebind_verdicts.py --write` → `rebind_materials --write`。
- **依赖**：逐条裁定（可与 D-1 同批流水线）。
- **验收判据**：75 条逐条有裁定记录；REBIND 全量生效；复扫嫌疑数下降且 reviewed 表增长。
- **预计工作量**：中（1–2 个代理轮）。

### 组② 学生侧接线（库到学生）

#### ②-1 无题轮（大厅）结构性读不到库；大厅仍向模型声明全量工具

- **事实**：大厅输入契约没有科目字段（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorLobbyTasks.kt:15-73`）；工具上下文对大厅取不到 subject（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/TutorToolContext.kt:26-30`，本会话实读）；`KNOWLEDGE_READ` 无 subject 时恒回空范围 `ok=true`、`resultCount=0`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt:194-204,226-239`，本会话实读话术含「不必重复查询」）；大厅仍声明全量 5 个工具（`feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorLobbyModelTaskPolicy.kt:94-97`，本会话实读）；披露集合不含 `SUBJECT_KNOWLEDGE_BASE`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelEgress.kt:403-406`，本会话实读）；`CURRENT_QUESTION_HELP` 允许 `KNOWLEDGE_READ`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolLoop.kt:408-410`），工具环上限 5 轮；`docs/tutor-surface-unification.md` 全文 grep `KNOWLEDGE_READ` **0 命中**（卡片）。
- **对学生的影响**：学生在智能体首页问概念问题（「什么是二次函数」），模型拿不到任何知识库材料；若按工具面去查，会消耗 5 轮工具预算中的 1 轮并让学生看到一行「本轮无可读范围」。库里的 3,866 点 / 50,383 条材料在这条路径上等于不存在。
- **怎么做**：①先裁定走 (a) 本地能判科时把该轮升级为「有题轮」（Respond 已有 subject，不动披露集合）还是 (b) 给大厅加 subject 并扩披露集合（动 manifest schema 与 `ModelEgress` 分档）；②止血（不依赖裁定）：在大厅提示词加「本轮没有科目上下文，不要申请 KNOWLEDGE_READ / MASTERY_READ」；③(b) 的落点：`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorLobbyTasks.kt` 加 subject（空值抹平 + schema 推进）、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/TutorToolContext.kt:30` 读它、`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelEgress.kt:403-423` 扩集合 + 旧行读回分档、策略指纹计入新字段。
- **依赖**：F11；环境（instrumented 清单/设备用例本机未跑）。
- **验收判据**：大厅轮在本地已判科前提下 `KNOWLEDGE_READ` 返回 ≥1 个真实节点；外部 provider 的 `disclosedData` 精确等于当轮口径；旧 schema 行升级后仍可解码。
- **预计工作量**：止血 0.5 agent-day；(a) 2–3 agent-days；(b) 3–5 agent-days。
- **UNVERIFIED**：真实模型在大厅是否真的会调用 KNOWLEDGE_READ（需外部 provider 真跑，未实测）；「浪费一轮」是代码可达性推断，不是实测计数。

#### ②-2 无知识库浏览入口；只有「有学习记录的节点」总览

- **事实**：本会话 grep：`readSubjectKnowledgeNodes` 唯一 `src/main` 生产调用是 `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt:151`（空搜索特征回退），app/feature/domain/data **无调用者**（订正「零生产调用者」，见 §1.4）；唯一知识类学生屏是掌握总览（`app/src/main/kotlin/com/tingyun/smartmistakebook/LearningMasteryScreen.kt:59-60,73-78`），只含有证据的节点；另一入口是复习出题（`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:482-510`），只取「有材料可出题」的节点；DAO 无 OFFSET、limit≤256（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemOrganizationDao.kt:118-135`；`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt:116`）。
- **对学生的影响**：学生无法「看看知识库里有什么」：不能按科目浏览知识点、看边界说明或材料摘要；库的覆盖面（哪些点已审校、哪些是缺口）完全不可见。
- **怎么做**：①裁定产品形态（只读浏览层级；是否展示材料正文——与 KD-29 标识耦合）；②新增只读分页 API（domain `listSubjectKnowledgeNodes(subject, offset, limit)`；DAO 加 OFFSET 或改键集分页；明确是否过滤 `verification_status`）；③UI 新增 destination 或在掌握屏加入口；④验收含「不产生任何模型 egress」。
- **依赖**：产品口径（F12 相邻）；环境（屏测需设备）。
- **验收判据**：存在 UI 路径列出某科目知识节点且与学习证据无关；同一数据两次分页无重复无遗漏；该路径不触发出网。
- **预计工作量**：3–5 agent-days。

#### ②-3 `MASTERY_UPDATE` 不回显写了哪个节点（批次 0 可做）

- **事实**：接受形态的模型可见结果只有方向与权重（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt:917-923`），被拒形态只给 `result.reason`（`:944-951`）；学生痕迹 `TutorToolTraceEntry` 字段只有 tool/resultCount/ok/errorKind（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolTrace.kt:44-51`），行文案「$label · 已记录」（`:185`）；实跑 `TutorToolRunnerTest` 42/0、`TutorToolTraceTest` 12/0（`build/agent-outstanding/group-2.md:8-13`，本会话未复跑）。
- **对学生的影响**：学生看到「掌握记录 · 已记录」但不知道记到哪个知识点；模型把代号解析错/写进无关节点时，工具结果与学生痕迹都不显示目标，没人能当场发现。
- **怎么做**：①runner 在 accepted/rejected 的 `summaryMarkdown` 带代号+节点名（`knowledgeNode`/`resolved` 就在作用域内）；②`TutorToolTraceEntry` 加可选节点标签（旧 JSON 缺字段解码为 null，`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolTrace.kt:71,101-106` 已按此兼容）；③行文案与 `TutorConversationSurface` 渲染跟进。
- **依赖**：无阻塞项（纯本地、不进模型输入、不碰出网）；只有痕迹 JSON 前后兼容要守。
- **验收判据**：接受与被拒两种情况下，模型结果与学生痕迹都能读出目标知识点；旧 trace JSON 读回不抛异常；两个测试类保持绿。
- **预计工作量**：0.5–1 agent-day。

#### ②-4 grounding 请求只产不读；学生「没学过」没有闭环

- **事实**：生产者有：题目整理时模型输出 `groundingRequests`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiProblemOrganizationProtocol.kt:218-227`），`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeOrganizationRepository.kt:371-386` 落库并**直接返回 `applied=false`**；UI 停在「暂未整理完整 / 再整理一次」（`feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeOrganizationSection.kt:395-402,668-684`）。解析闭环三个 API 生产调用者 **0**（本会话 grep：只命中 core:database 自身转发）：`observePendingKnowledgeGroundingRequests`、`resolveKnowledgeGrounding`、`applyReviewedKnowledgePack`。唯一被读的 pending 面是摘要（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomBackedStudyExperienceRepository.kt:273-278`），喂给无消费者的 `knowledgeCoverage`。
- **对学生的影响**：整理撞上「库里没有这个知识点」→ 请求落库后**永远没人处理**；题目停在「暂未整理完整」，学生只能反复点「再整理一次」；知识库不会因学生遇到的新内容而生长——「有机生长」的入口是断的。
- **怎么做**：①裁定闭环口径（本地自动解析 vs 离线人工审校 vs 先只做显式丢弃）；②实现驱动：读 pending → 决策 → `resolveKnowledgeGrounding` → 重走 organization apply；本地解析失败要显式落「丢弃+原因」，不许静默消失。
- **依赖**：F13；外部（若走研究管线，依赖 ②-7 与来源/许可）；环境（DB 用例需设备）。
- **验收判据**：一条 pending 能走到 resolved 或带明确原因被丢弃；题目可整理成功；「再整理一次」能收敛；pending 列表解析后单调递减。
- **预计工作量**：3–6 agent-days（本地解析口径）＋②-7。

#### ②-5 `knowledgeCoverage`（含 pendingGaps）进了快照但无 App 消费者

- **事实**：生产链完整（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomBackedStudyExperienceRepository.kt:244,273-278,321-326,832`；`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudySnapshotBuilder.kt:44,136`；域 `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/StudyExperienceRepository.kt:189`；gap 形状 `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/StudyKnowledgeCoverage.kt:51-65`）；App 确实收集快照（`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:188`）但只用 status/review/profile/catalog；本会话 grep `knowledgeCoverage|StudyKnowledgeCoverageOverview|StudyKnowledgeCoverageGap` 在 app/feature **0 命中**。
- **对学生的影响**：学生看不到「本科目已审校多少主题/知识点」，也看不到「库里缺哪些点」；②-4 的 pending 队列即使有数据也无人可见。
- **怎么做**：二选一：(a) 最小展示——在 `LearningMasteryScreen` 加「覆盖」区，读 `experience.knowledgeCoverage`（入口已在 `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:800`），只显示 reviewedSubjects 计数 + pendingGaps 计数与最近条目；(b) 若无产品需求，按第 12.2 条删除整条链（Repository/Builder/域/Mapper），避免「为不存在的消费者维护机制」。
- **依赖**：F12（产品要不要把覆盖/缺口露给学生）。
- **验收判据**：(a) 学生屏出现覆盖与缺口读数且与 DB 一致；(b) 字段与生产者全部删除、无引用残留。
- **预计工作量**：(a) 1–2 agent-days；(b) 0.5 agent-day。

#### ②-6 AI 生成内容标识（KD-29）未实现

- **事实**：登记为已知边界、用户裁定「暂不处理」（`docs/known-defects.md:1102-1112`，重开条件在 `:1111-1112`）；`docs/kb-stage7-report-2026-09-28.md:106-107` 明写**不声称合规**（外部依据《人工智能生成合成内容标识办法》2025-09-01 施行；来源等级：两处二级网页一致、**官方原文当次未取到**）。实现 0：grep `ai生成|AI 生成|人工智能生成|AIGC|生成合成` 在 app/feature/core **0 命中**；随包材料 schema 无 AI 字段（单条键 = slug/subject/type/title/…/bindings）；来源登记无 AI 字段。**限定（订正基线）**：两处材料展示（重教开场卡 `feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/ReviewSessionScreen.kt:472-514`、前置补救卡 `:525+`）都以 `teachingArtifact()` 为前提，而生产恒 null ⇒ **今天生产没有向学生展示材料正文的路径**；当前真实暴露面 = 随包 50,383 条 AI 整理材料的元数据无 AI 字段 + 模型回复。
- **对学生的影响**：当前主要是合规面（随包内容与模型回复无标识）；未来一旦重教/补救路径在生产启用或任何材料展示面新增，学生读到的就是「AI 从教辅整理」的讲解而无任何说明。
- **怎么做**：①裁定重开 KD-29；②最小形态（不碰内容管线）：在两张卡加固定说明文案并在任何后续材料展示面复用同一常量；③内容侧形态：材料 schema 加 AI/provenance 字段 → codec/importer/包重建 + 版本 bump（依赖 23 门 + 5 节）。
- **依赖**：F8；外部（法规口径待官方原文核实）；内容侧需内容管线窗口。
- **验收判据**：所有学生可见的材料面都带 AI 标识；或 KD-29 保持 open 且明写「未标识是已知取舍」（现状满足「如实登记」但不满足合规）。
- **预计工作量**：UI 最小形态 1 agent-day；内容侧 3–5 agent-days。

#### ②-7 知识研究/审校管线接口已建但无驱动方

- **事实**：domain 侧接口/协调器齐备（`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeResearch.kt:141-155,231-301`）；data 侧实现 `createRoomKnowledgeResearchReviewQueue` / `createRemoteKnowledgeResearchSourceVerifier` **全仓 0 调用者**；审校/落包端口生产调用者 0；`KnowledgeResearchCoordinator(` 构造点全仓只有测试（`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeResearchTest.kt:63,107,207,238,270`）；`KnowledgeResearchSearchGateway` 一个实现都没有；`docs/architecture-research-2026-09-22.md:190` 记「建了没接」。
- **对学生的影响**：「学生遇到库里没有的知识点 → 离线检索可靠来源 → 人工审校 → 落包」的通道没有驱动方：缺口永远停在 pending（与 ②-4 叠加成双重断链），知识库只能靠离线人工重打包更新。
- **怎么做**：①裁定是否要做离线研究通道（产品+合规）；②补搜索源实现；③给协调器一个驱动方（离线 CLI/调试屏/CI 任务）；④审校闭环 `readPendingKnowledgeResearchReviewBundles → decideKnowledgeResearchReviewBundle → applyApprovedKnowledgeResearchPack`（返回 `KnowledgeGroundingResolutionRecord`，即 ②-4 的解析面）。
- **依赖**：F13 + 产品/许可裁定；环境（DB 用例需设备）。
- **验收判据**：一条 pending 审校 bundle 能被决定并落包；落包产生的 resolutions 使对应 grounding 请求消失；无隐式后台外发。
- **预计工作量**：5–10 agent-days（含搜索源实现）。**建议推迟**，见 §5。

### 组③ 覆盖验收与教材

#### ③-1 课标对照台账的真实数字、完整性门与判定通道

- **事实**：本会话逐条聚合 `knowledge-production/kb-coverage-alignment-2026-v2.json`（`R6`）→ COVERED 352 / PARTIAL 73 / NOT_COVERED 236 / UNDECIDED 0，分母 661；逐科 MATH 130/308=0.4221、PHYSICS 71/138=0.5145、CHEMISTRY 51/73=0.6986、BIOLOGY 100/142=0.7042（与基线一致）。完整性门：`PYTHONPATH=tools python -m kb_coverage.alignment --check` exit 0、`tools.tests.test_kb_alignment` 5 tests OK（`build/agent-outstanding/group-3.md:14-15`，本会话未复跑）。判定通道：全部 661 条 `reviewState=AI_REVIEWED`、reviewer=Qwen3.8-27B，**无一条人工审校**。旧数字残留：`docs/kb-problem-register-2026-09-15.md:616` 仍写「数学 118/308」；全仓 docs 未找到现行 130/308（`build/agent-outstanding/group-3.md:18`）。
- **对学生的影响**：对外宣称「覆盖 X/Y」时，判定通道是 AI 语义判定而非人工审校——不标通道会让人误判证据等级；公布旧数字（118）会让复算者对不上账。
- **怎么做**：①把 `kb-problem-register` 的 118/308 就地订正为 130/308（零裁定）；②发布话术固定为「覆盖 2025 课标内容要求 X/Y 条，UNDECIDED=0，判定通道=AI（Qwen3.8-27B）」；③若要更强证据，抽检 N 条人工复核。
- **依赖**：抽检比例（裁定）；数字订正零裁定。
- **验收判据**：`grep -rn "118/308" docs/` 为 0；发布文案同时含分子/分母/UNDECIDED/判定通道；`alignment --check` 与 `test_kb_alignment` 保持绿。
- **预计工作量**：订正 0.5 小时；抽检 50 条约 0.5–1 天。

#### ③-2 236 条 NOT_COVERED 的构成：189 条选修 / 47 条主线（本会话复算）

- **事实**：本会话按 module 名逐条分类 → MATH 139 = **125 选修 + 14 必修/选必**；PHYSICS 53 = **42 + 11**；CHEMISTRY 16 = **7 + 9**；BIOLOGY 28 = **15 + 13**；合计 **189 + 47 = 236**。47 条主线缺口示例：生物「概述某些基因中碱基序列不变但表型改变的表观遗传现象」、化学「体系与能量」、物理「了解串、并联电路电阻的特点」；其中态度型示例：生物「形成'环境保护需要从我做起'的意识」、化学「科学态度与安全意识」。
- **对学生的影响**：真正落在高考主线的缺口只有 47 条；把 189 条选修全补一遍对刷题提分几乎无感，却会消耗最多工时；态度型条目补成「知识点」没有教学意义。
- **怎么做**：①导出 47 条为 CSV 逐条打「知识型/态度型/超纲」标签；②知识型走既有补料链（建点→材料→重判 status）；③态度型与选修在发布口径里单列，不计入「主线覆盖」。
- **依赖**：F14（补哪些、态度型是否移出分母）。
- **验收判据**：47 条每条有「补/不补」记录；补的 status 变更且 target 在包中；发布口径按新分母重算。
- **预计工作量**：分类 0.5 天；补料按裁定规模 1–3 天。

#### ③-3 182 条拆分提案 0 裁定；25 个模块的分母不完整

- **事实**：本会话实读 `knowledge-production/curriculum-warning-decomposition-proposals-2025-v1.json` → **182 条 candidates / 25 个模块**（MATH 3 模块 8+7+13=28；BIOLOGY 22 模块=154，全部 DESCRIPTIVE 选修模块）；裁定文件 `knowledge-production/curriculum-warning-human-review-decisions-2025-v1.json` → `reviewState=NOT_STARTED`、`decisions=[]`、reviewer=null（本会话实读）；提案 `reviewBoundary.promotionAllowed=false`。门实测 `candidateCount 182 / approved 0 / rejected 0 / pending 182 / promotionReady false`（`build/agent-outstanding/group-3.md:48`）；`tools.tests.test_curriculum_coverage` 16 tests OK。这 25 个模块在台账里**各只有 1 条模块级 statement**，即分母被低估（1/模块 vs 182 条原子要求）。
- **对学生的影响**：25 模块中 22 个是生物选修、3 个是数学建模与校本课程——对高考主线影响小；但「覆盖 X/Y」在选修段不可辩护（分母不完整）。**订正**：不是「状态字段永久 UNDECIDED」（台账全表 UNDECIDED=0），是「分母不完整/待原子化」。
- **怎么做**：①把 182 条候选（candidateId/displayName/evidencePhrase 已备）交人工逐条 APPROVE/REJECT，写回 reviewer/reviewedAt/decisions；②每轮跑 `promotion_cli.status_main` 看 pending；③完成后 `scope_main --check` 再 `--write` 产 reviewed-scope。
- **依赖**：人工逐条审（4–7 小时）；F14（是否纳入原子化分母）。
- **验收判据**：`reviewState=COMPLETED`、`pendingCount=0`、`promotionReady=true`；reviewed-scope 与 182 条一一对应。
- **预计工作量**：4–7 小时人工 + 0.5 天流程。**建议推迟**，见 §5。

#### ③-4 教材入库授权 A/B/C 仍待裁定（官方条款禁止转载改编并点名 App）

- **事实**：三选项与官方条款：`docs/kb-textbook-source-verification-2026-09-21.md:102-110`（A 不申请许可 / B 申请许可 / C 只做覆盖核对）；官方原文 `:70-83`——人教社「仅供个人学习使用，未经授权不得另做他用。」；国家平台《网站声明》「未经许可任何媒体、互联网站、**App应用**、商业机构、个人不得转载…也不得改编…」并给出许可渠道（4001910910 / ncetbgs@moe.edu.cn）。登记为待裁定（`docs/kb-problem-register-2026-09-15.md:1663`；`docs/kb-textbook-toc-align/SUMMARY.md:49-54`）；全仓检索**未发现任何后续裁定记录**；W-7 的做法事实上按 A 执行（只读目录、不留存原文）。
- **对学生的影响**：教材原文不进库 → 知识点边界表述只能靠教辅/课标；但学生可见的章节目录对齐不依赖此裁定（W-7 只读目录即可）。
- **怎么做**：用户三选一；选 A 把验收口径从「教材原文入库」改为「目录/知识点覆盖已对齐 2019 人教版 A版」（零额外成本）；选 B 走 4001910910 / ncetbgs@moe.edu.cn 申请书面许可；选 C 用国家平台 A版目录产出《教材覆盖对照表》。
- **依赖**：F1（产品/法务取舍）。
- **验收判据**：裁定入台账；对应产物落地（口径改写 / 书面许可 / 对照表）。
- **预计工作量**：裁定分钟级；A≈0.5 天；C≈1–2 天。

#### ③-5 教材元数据目录（3,229 条）在盘不可复现（UNVERIFIED）

- **事实**：`knowledge-production/source-register-2025-v1.json:117,140` 称 `ACQUIRED_REVIEWED`、指纹 `92390A2220C63D1A…`、sourceLocator 称「已取得并校验四个分片，共 3229 条电子教材元数据」；审计脚本期望 `.artifacts/research/smartedu-tch-material-version.json` + 分片（`tools/audit-smartedu-textbook-catalog.ps1:16-30`）；实测 `.artifacts/research/` 只有课标 PDF、`find` 无命中、`.artifacts/` 未被 git 跟踪 ⇒ **无法复算**。
- **对学生的影响**：无直接可见影响；但若选 C（用平台目录做覆盖对照）会缺数据基础，对照表只能人工逐页读目录重做。
- **怎么做**：重跑登记流程取回 4 个分片 + version 文件，再跑 `tools/audit-smartedu-textbook-catalog.ps1 -ExpectedModuleVersion 987894174 -RequireNineSubjects`；取不回则把 `acquisitionState` 降级为待取并在 note 注明。
- **依赖**：环境（网络/镜像可达性）。
- **验收判据**：脚本输出 `valid=true`、`totalResourceRecords≈3229`、`missingSubjects=[]`；或降级记录可查。
- **预计工作量**：0.5 天（通道可用时）。

#### ③-6 W-7 四科目录对齐报告已出；20 条待人工确认；跨册错位数字核实

- **事实**：报告 `docs/kb-textbook-toc-align/CHEMISTRY.md`、`docs/kb-textbook-toc-align/BIOLOGY.md`、`docs/kb-textbook-toc-align/PHYSICS.md`、`docs/kb-textbook-toc-align/MATH.md`、`docs/kb-textbook-toc-align/SUMMARY.md`（提交 `d477e221`，22 册 / 340 节）；跨册 化学 6 / 生物 7 / 物理 19（11 跨册）/ 数学 8（`docs/kb-textbook-toc-align/SUMMARY.md:18-21`）；真正脱靶节 化学 14/60、生物 16/82、物理 19/125、数学 8/73+两章（`:25`）；20 条待人工确认（四份报告末尾合计，`build/agent-outstanding/group-3.md:95` 逐行统计）；包内可复算实例：化学「电离平衡」实际 slug=`化学必修第一册·第一章·物质分类与转化·电离平衡`、显示名「电离」；数学主题树章节点实测为「数学必修第一册·第三章」这类只含章序号的名称（章节点带官方章名 0/18）；数学 209 点中 54 条疑似错位（≈26%）。
- **对学生的影响**：跨册错位会让学生在「这一章」里学到别的册的节；章节点全是「第X章」这类无官方章名的名称，学生目录里看不到教材章名。
- **怎么做**：①先裁 F2 口径；②按口径②只修跨册错位 + 章名/章层 + 生物 2 处旧章名 + 数学 54 条单独核；工具：`tools/kb_build/tables/chapter_*.csv`、`tools/kb_build/propose_chapter_by_node.py`（2048 行提案）、`tools/kb_build/gate.py` 23 门、`tools/ci/run_kb_checks.py`。
- **依赖**：F2。
- **验收判据**：②口径下跨册错位 0；章节点带官方章名；生物 2 处旧章名修正；`tools/kb_build/gate.py` 23 门全 0；`tools/ci/run_kb_checks.py` 5 节 OK。
- **预计工作量**：②口径 3–5 天（含 20 条人工确认）；①口径 ≥2 周。

#### ③-7 章节树口径 ①/② 仍待裁定

- **事实**：两案原文 `docs/kb-textbook-toc-align/SUMMARY.md:56-61`——①补章节点 化学8/生物6/物理3/数学2 + 章名 + 拆合并 + 补缺失节；②只修跨册错位 + 章名/章层 + 生物旧名 + 数学 54 条。待裁定登记（`docs/kb-problem-register-2026-09-15.md:1684`）；四份分册均写明「需要用户先定」（docs/kb-textbook-toc-align/BIOLOGY.md:299、docs/kb-textbook-toc-align/CHEMISTRY.md:282、docs/kb-textbook-toc-align/MATH.md:321、docs/kb-textbook-toc-align/PHYSICS.md:436）；2026-09-21 之后文档**无后续裁定记录**。
- **对学生的影响**：口径不定，错位与无章名的节点名就一直留在学生目录里；定了②才能用最小改动先消掉「打开一册看到别册的节」。
- **怎么做**：用户在 F2 中二选一（四科代理与报告作者都推荐②）；选定后按 ③-6 步骤执行。
- **依赖**：F2。
- **验收判据**：裁定入台账；选定案的差异清单被逐条关闭。
- **预计工作量**：裁定本身分钟级（决定 ③-6 的工时）。

#### ③-8 章表/数学节表两处已知错误未修（且基准文件不在版本控制）

- **事实**：生物旧章名 `kb_tools/standard_chapter_names.json:88`=「内环境与稳态」（官方「人体的内环境与稳态」）、`:98`=「生态环境的保护」（官方「人与环境」）；数学节表 `kb_tools/chapter_structure_full.json:96` 多出「10.4 统计与概率的应用」、`:161-164` 第七章缺「7.3 离散型随机变量的数字特征」致号位错位、`:166-169` 第八章只有 2 节且节名不同；该文件**被生产工具当权威结构用**（`tools/kb_build/propose_chapter_by_node.py:4,26`），产物 `tools/kb_build/tables/chapter_by_node.csv` 现有 2048 行提案；`.gitignore:44` 忽略 `kb_tools/`，两个 JSON 均未被 git 跟踪（`git ls-files kb_tools/` 只有 `kb_tools/kw_chapter_map_gen.py`）；`kb_tools/standard_chapter_names.json` 全仓无代码引用。
- **对学生的影响**：错表会被用来给知识点定章，错误顺着 2048 行提案进入学生可见的章节归属；基准文件不在 git 里，换台机器复算不了 W-7 的结论。
- **怎么做**：①按 W-7 证据修正 3 类错误；②把两个 JSON 纳入版本控制或迁到 `tools/kb_build/tables/`；③重跑 `propose_chapter_by_node` 并对照 W-7 报告复核。
- **依赖**：修错零裁定（与 F2 解耦）；迁位需确认无工具路径耦合。
- **验收判据**：修正后章名与官方目录逐字一致；重跑无 7.3/10.4 异常；两个 JSON 进 git。
- **预计工作量**：0.5–1 天。

#### ③-9 KD-28 九科白名单两份仍在（已判接受边界）

- **事实**：两份清单（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeBaseImportContract.kt:17-26`；`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/SubjectKind.kt:4-14`）；2026-09-28 判「接受为边界，不收敛」（`docs/known-defects.md:1090-1100`），理由：四科范围（用户 2026-09 已裁「九科补完永久取消」）、多出 5 科恒为空集、无可观测失败；Reopen 条件=出现第五科或需要按科放行。
- **对学生的影响**：当前四科数据下无观测失败；将来按科开关/新增科目时会漂移。
- **怎么做**：维持现状；把 reopen 条件写进「新增科目」变更检查单（先证漂移再改）。
- **依赖**：无（边界已接受）。
- **验收判据**：新增第五科时两份清单一致性检查通过（先证漂移）。
- **预计工作量**：0（维持）。

#### ③-10 「全覆盖高考考点」口径已废弃，但随包侧车仍有 19 条旧标签

- **事实**：废弃结论 `docs/research/coverage-baseline-design-2026-09-15.md:11`（替换为「覆盖…内容要求的 X / Y 条」）；残留实测（`build/agent-outstanding/group-3.md:156-157`）：`grep -rho "高考考点补缺（联网核对）" core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-*.json | wc -l` → **19**（v2-01:2、v2-02:5、v2-03:7、v2-05:5），均为随包侧车材料的 `sourceLocator`。
- **对学生的影响**：locator 是溯源字段，不直接展示；但对外导出/审计材料来源时，旧标签与现行口径矛盾。
- **怎么做**：把这 19 条 locator 重写为现行口径；走 `promote --dry-run` 验表↔包，再按发布流程重打包并重跑双门。
- **依赖**：无裁定；改包属另一会话的生产改动（注意写入窗口纪律）。
- **验收判据**：grep 计数 0；`tools/kb_build/gate.py` 23 门全 0；`tools/ci/run_kb_checks.py` 5 节 OK；包 version 递增。
- **预计工作量**：0.5 天（含重打包）。

### 组④ 检索质量

#### ④-1 D12 目标 vs 当前实测：融合 0.7308（95/130）、dense 单路 0.6923、词面腿 0.6308

- **事实**：目标=主集 ≥0.90 且逐章 ≥0.80（D12，`docs/kb-architecture-refactor-decisions-2026-09-21.md:20`；裁决 29 保持 0.90）。本会话实读 `build/stage3-device-expectation.json`：`refFusedD1.main = 0.7307692307692307`（hits 95 / total 130）、MRR 0.5832、chapterMin 0.3333；`refDenseD1.main = 0.6923076923076923`（90/130）；`refLexicalOnlyD1.main = 0.6307692307692307`（82/130）；golden v2 sha `89c1d5b5…`（130 条 / 20 章）；dense 资产 sha `8649afa6…`；词面腿 `build/production-lexical-leg.tsv` sha `fb4b1346…`。设备侧真 SQL 也量到同一数（卡片：`GoldenRetrievalInstrumentedTest` tests=1 failures=0，logcat `主集 Recall@5 = 0.7307692307692307`、MRR 0.5823、p95 81ms/p50 57ms、overBudgetSamples=0、MISS 35/130）。逐章 ≥0.80 仅 **6/20** 章；最低 0.3333（平面向量 1/3、计数原理 1/3）。
- **对学生的影响**：学生用自然语言提问时约 27%（35/130）在真实链路上进不了前 5 个知识点；弱章（平面向量/计数原理/数列 0.4545）最容易「讲题讲到相邻知识点、材料读不到」。
- **怎么做**：①先拿 F4 口径裁定；②若继续，按裁决 29 的真实工作项逐项实验（扩别名 / 改检索排序 / dense 档位与融合参数），每项必须 JVM 镜像（`GoldenRetrievalJvmTest`）+ 真 SQL（`GoldenRetrievalInstrumentedTest`）双侧出数；③显著性用 `tools/kb_coverage/retrieval_significance.py`（按章 cluster）；④守零漂移纪律（>5pp 判缺陷）。
- **依赖**：F4（D12 目标口径）。
- **验收判据**：真 SQL 主集 Recall@5 ≥0.90 **且** 20 章最低 ≥0.80（或裁定改口径后按新口径重写）；JVM 镜像 vs 真 SQL 12 个质量数逐项一致；19 例回归 19/19、地板（0.64/0.56）不破。
- **预计工作量**：大（多轮检索实验 + 复核 + 零漂移对账）。

#### ④-2 D-3 剩余一步「实修召回」（前两步已核实完成）

- **事实**：D-3 定义「先修 5 条既有红 → 重打 dense → 实修召回」（`docs/superpowers/specs/2026-09-25-agent-first-refactor-design.md:336-337`）。第 1 步（五红清零）卡片重跑通过：`./gradlew.bat :core:data:testDebugUnitTest --tests "*ProductionLexicalLegExportTest*" … --rerun` → BUILD SUCCESSFUL 1m33s、5 类测试 failures/errors 全 0（`build/agent-outstanding/group-4.md:46`，本会话未复跑）。第 2 步（dense 与包同源）本会话复核：`.vec` 旁车 `packSha256` == 主包 sha256 `647675cd…`（本会话实算一致），向量 `ids` 41,193 行（`R5`）。第 3 步**未做**：生产召回 SQL 一字未动（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemOrganizationDao.kt:178` 仍是 `ORDER BY COUNT(DISTINCT feature.search_feature) DESC`；S19 明判「不改召回 SQL 形状」，`docs/research/2026-10-02-s19-retrieval-diagnosis.md` §3）；全仓未找到任何「实修召回」的施工中产物或计划更新（`build/agent-outstanding/group-4.md:187-190`，观察结论而非实测）。
- **对学生的影响**：与 ④-1 同因：检索质量未收口，而 3D 是阶段 5 开工的硬门（`docs/REFACTOR-MASTER-LINE.md:90`）。
- **怎么做**：与 ④-1 同一工作流；开工前先把口径写死（目标线、回归地板、判分账本、MISS 对照），每步走「金标出数 → 真机复核 → 零漂移」链。
- **依赖**：F4 + 排期。
- **验收判据**：D-3 三条中前两条已核实通过；第三条须有「达标」或「用户正式改口径」的结论（当前两条皆无）。
- **预计工作量**：大。

#### ④-3 显著性口径：0.75/0.7444 是 v1/90 结论；v2/130 上 0.90 在按章 CI 之外

- **事实**：本会话实跑 `python tools/kb_coverage/retrieval_significance.py --golden tools/kb_coverage/tables/golden_queries_v1.json --misses build/stage7/golden-fused-misses-device-2026-09-28.txt` → 「金标集 n=90，10 章」「A 路 recall=0.7444（67/90）」「95% CI（逐题）[0.6556, 0.8333]」「95% CI（按章 cluster）**[0.6111, 0.8667]**」⇒ 0.75 落在区间内、不可分辨。当前 v2/130：本会话用 `build/stage3-device-expectation.json` 的 `perCase` 逐章命中数做按章 cluster bootstrap（20 章、20,000 次、seed 0、取 vals[499]/vals[19499]）→ 点估计 0.7308，95% CI **[0.6346, 0.8288]**（卡片同法得 [0.6350, 0.8291]，末位差来自 bootstrap 实现细节）；两者都**不含 0.90**，0.90 完全在区间之外。
- **对学生的影响**：无直接行为影响；但它决定报告怎么写——不能再用「差一点点、在噪声内」解释当前未达标，必须承认差距（≈0.17）是系统性的。
- **怎么做**：任何「达标/未达标」表述都附点估计 + n + 分母 + 按章 CI；把设备 v2 MISS 账本拉回落盘（`adb pull /sdcard/Download/golden-fused-misses-1790919837814.txt build/golden-fused-misses.txt`）；口径若重议，把 v2 CI 一并写进裁定材料。
- **依赖**：F4。
- **验收判据**：报告/裁决里不得出现「0.90 与 0.7308 不可分辨」这类表述；每次口径判定必须带 CI。
- **预计工作量**：小。

#### ④-4 FTS5 是否上生产：未决——返回形态已改、排序引擎未换、7 项未证实

- **事实**：返回形态已改（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt:161` `return (matched + parents)`，2026-09-24）；排序引擎未换（生产 SQL 仍 `COUNT(DISTINCT feature.search_feature) DESC`；全仓 grep `BundledSQLiteDriver`/`sqlite-bundled` 零命中；无生产 FTS 表）。`docs/kb-fts5-production-plan.md:274-285` §7 列 **7 项未证实**（运行时可注册、`.so` 体积口径、Room3 `@Fts5` 覆盖、扫描数、INDEX_VERSION=2 断言内容、新地板值、20k 合成点基线）；计划写的 `STUDY_DATABASE_VERSION` 51→52 已过期（实际 `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:123` = **57**，施工前需重基线）。收益口径：生产形下 FTS5 排序**无增益**（0.5333 vs 0.5444），增益只在改返回形态后出现（臂 A matched 口径 0.6556）。
- **对学生的影响**：词面腿排序不带 IDF/长度归一 → 自然语言问句被公共 2/3-gram 多的大节点挤掉（Stage-1 的 MISS 形态），直接贡献当前 35/130 的 MISS。
- **怎么做**：先拿 F6 裁定；若换：按计划 §3 步骤 0（bundled 驱动可行性探针）→ 1（切驱动）→ 2（建表）→ 3（INDEX_VERSION 2 + 锚点）→ 4（召回切换）→ 5-7；或先补 JVM 侧 FTS 镜像，把 FTS5 排序在判官 v2 上重出数再裁。
- **依赖**：F6 + 环境（模拟器/真机，本会话已证模拟器可用）。
- **验收判据**：真 SQL 主集 ≥ 现基线且逐章不降；JVM 镜像与真 SQL 零漂移；计划 §7 的 7 项逐项关闭或明确「不做」入账。
- **预计工作量**：中大（含驱动切换的冲击面：迁移矩阵/备份/图书馆 FTS4/schema dump）。

#### ④-5 dense 大档（Qwen3-Embedding-0.6B）取舍：证据链已足，正式裁定未落

- **事实**：Stage-2 离线 Qwen3 融合 0.7778、单路 0.7889，逐章最小 0.2222/0.4444 ⇒ 主判据未过（`docs/kb-stage2-report-2026-09-23.md:174-175`）；Stage-5 否决 Qwen3（无实测端侧件 + 导出路径成熟度未验证）→ 换 `bge-base-zh-v1.5`（同族 768 维），质量过但延迟硬线不达；Stage-6 宿主代理口径 base keepint8 win128 @4 = **4075.5ms** vs 小档 **590.4ms** = **6.90× > 3×** ⇒ 不落地；真机矩阵被共享工作树阻断未跑。口径更正：`docs/kb-golden-v2-protocol.md:22-25`——「Qwen3 无端侧 int8 路线」不再成立（uint8 ONNX 已公开），但结论不变，理由改成「**延迟不允许**」。正式裁定仍缺（`docs/kb-stage3-report-2026-09-24.md:5`）。
- **对学生的影响**：换大档买到的主要是主集、不是弱章（两档共同瓶颈章都是物理·相互作用）；不换则弱章召回维持现状；若强上，会拖慢每轮讲题的端侧编码。
- **怎么做**：推荐路径 = 用户一句话以 Stage-6 证据**关闭大档**并把理由收口进台账；若要重开，先重定「延迟预算 + 档位」口径，再按 Stage-6 同口径重测（宿主 4 线程/窗口 128 + 真机硬门 ≥0.999 + 质量线重定）。
- **依赖**：F5 + 环境（真机延迟测量；当前无物理设备）。
- **验收判据**：关闭 → 结论 + 理由入台账；重开 → 真机质量与 p50/p95 双达标（线在重开时写死）。
- **预计工作量**：关闭 = 小；重开 = 大。

#### ④-6 每轮固定读数开销 ≈12.6ms（S19 遗留；数字本身 UNVERIFIED）

- **事实**：登记 `docs/research/2026-10-02-s19-retrieval-diagnosis.md:56`（桌面 SQLite 3.50.4 探针：`countReviewed` 2.7ms + `countIndexed` 9.9ms ≈ 12.6ms/次）；`docs/research/2026-10-02-wave4-completion-record.md:73` 明写「本批只登记…另立单项」。本会话读代码确认结构未变：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt:214-231` 的 `ensureKnowledgeSearchIndex` 在锚点版本相等时仍**每次**跑两条 COUNT，由 `:153` 每次调用。**12.6ms 数字本会话未重跑**（S19 探针脚本未入库）⇒ 标 `UNVERIFIED`；「每次召回跑两条 COUNT」结构已核实为真。
- **对学生的影响**：每次检索/讲题固定多付十几毫秒（设备更高；本会话设备 p50=57ms/p95=81ms 内含这块），属纯浪费的预算占用。
- **怎么做**：①先把探针脚本入库（当前是工具缺口），复测 12.6ms；②改为「每进程每科一次」或写侧失效驱动；③以 `KnowledgeContextRetrievalInstrumentedTest` 的 p50/p95 复核。
- **依赖**：无裁定（工程改动）；排期。
- **验收判据**：召回 p50/p95 不回归；每轮不再执行两条 COUNT（用查询计划/计数断言验证）。
- **预计工作量**：小–中（多个写入口的失效接线）。

#### ④-7 Wave X 真实样本 ≥400：仍阻塞（外部：用户题目数据集）

- **事实**：`docs/REFACTOR-MASTER-LINE.md:55` Wave X 状态 **⬜**，条件「真实样本 ≥400 条」；数据来源已裁：用户提供题目数据、AI 自行使用（裁决 29）；配套薄纵向切片（裁决 30，状态「并行推进」）。本会话与卡片搜索：仓库内未见用户提供的题目数据集，也未见薄切片走查报告/样本清点产物 ⇒ 通道未启动。
- **对学生的影响**：校准（Anki 硬门 ≥400 条 review）、经验先验、FSRS-7 评估、门常数校准等全部延后；学生侧掌握度/排程的个性化迟迟不能用真实数据检验。
- **怎么做**：由用户提供题目数据集（外部输入）；AI 按裁决 30 跑薄切片一圈并产出「走查报告 + 样本条数清点」；随后走 roadmap 附录 A 的 srs-benchmark 离线模拟。
- **依赖**：外部（用户题目数据集 / 真实使用样本）。
- **验收判据**：≥400 条可预测 review 样本落账；薄切片报告明确回答「当前树能不能让学生走完一整圈、断在哪」。
- **预计工作量**：外部依赖解除后中等。

#### ④-8 端侧测量边界：tflite 宿主对拍可跑且已绿；真机延迟不可测；线程轴未量

- **事实**：卡片实跑 `build/tflite-venv/Scripts/python.exe tools/dense_build/check_tflite_parity.py --max-len 128` → **exit 0**，`ALL n=330 min=0.999554 median=0.999790 p95=0.999849`（门 0.999）（`build/agent-outstanding/group-4.md:162-163`，本会话未复跑 ⇒ 该读数标 `UNVERIFIED`；本会话复算的只有资产常量：`.vec` sha `8649afa6…`、模型 `core/data/src/main/assets/dense/bge-small-zh-v1.5-int8.tflite` 61,608,136 B、ONNX sha `4d3b3135…`，见 `R5`）。设备实测（模拟器，卡片）：p95 81ms/p50 57ms/overBudgetSamples=0/denseLegLive=true。真机（物理设备）延迟不可测：本会话 `android_list_devices` 未跑；卡片记仅 `emulator-5554`、无物理设备在线；端侧线程 {2,4,8} 与窗口 {512,128} 的真机差值未量；模拟器读数**不能**当真机判定。
- **对学生的影响**：大档取舍（④-5）缺真机数；线程调优没有实测支撑；报告里任何「端侧延迟」表述都必须带口径。
- **怎么做**：拿到物理设备后按 Stage-6 口径重跑 `DenseFirstUseCostInstrumentedTest`（首用耗时 + p50/p95）与 `DenseEncoderParityInstrumentedTest`（≥0.999），并补线程 {2,4,8} × 窗口 {512,128} 矩阵；在此之前宿主/模拟器数一律标口径。
- **依赖**：环境（物理设备）。
- **验收判据**：真机 p50/p95 + 线程差值表落盘；对拍 ≥0.999。
- **预计工作量**：设备到位后小–中。

### 组⑤ 治理与环境

#### ⑤-1 2027-53 转写：35 页已清判，只剩 2 页 UNCERTAIN 待人工（订正基线）

- **事实**：本会话实跑 `PYTHONPATH=tools python -m kb_coverage.check_transcripts` → **退出码 0**：「页数 1205｜已转写 1205｜缺失 0」「闸门：{'pass': 1205}」「全部通过」。收口依据提交 `ae6c0fc9`（2026-09-28）已在历史中；旧 35 页队列 `tools/kb_coverage/tables/retranscribe_queue.csv` 是「派工名单」不是未办清单（逐页对账 35 个页实例全部 `gate=pass`＋`verdict=ACCEPT`）；剩 **PHYSICS p41/p79** `verdict=UNCERTAIN`（不在队列、在补齐目录无页产物）；MATH p22 已特判 ACCEPT（索引/目录页计数判据修正）。**基线旧口径「35 页未清判…转写门仍 exit 1」为 2026-09-28 00:19 快照，已被同日 `ae6c0fc9` 取代。**
- **对学生的影响**：转写线不再是待办；但这批内容进入学生可见面前仍缺「换人读图复核」——叙述页完整性目前只由「清点＋机械门」保证；2 页 UNCERTAIN 的内容对错未定。
- **怎么做**：①人工判读 `PHYSICS p41/p79`（对着页图判「可用/需重转」，结论写回 `tools/kb_coverage/tables/transcript_audits.csv`，UNCERTAIN 需人工改判后 `python -m kb_coverage.transcription_ledger --write` 重建账本）；②若要更强保证：对 483 页「审计-轻」叙述页做分层抽检（换人读图）；③每次改判后重跑 `check_transcripts`。
- **依赖**：2 页判读（裁定）；环境（页图在 `build/`、按 `.gitignore:14` 不入库，读图依赖本机）。
- **验收判据**：`check_transcripts` 退出码 0 且账本 `UNCERTAIN` 计数归零（或在报告里写明处置结论）。
- **预计工作量**：p41/p79 判读 0.5–1 人日；补 483 页抽检每页 5–10 分钟 → 约 40–80 人时。

#### ⑤-2 2027 版《53知识清单》来源登记缺口：5,053 条内容已入包，正式登记册里没有这个来源

- **事实**：本会话实读 `knowledge-production/source-register-2025-v1.json` → 32 条来源，**无**《53知识清单》（title 检索「知识清单」零命中）；本会话实测现包 v9 的 50,383 条材料中 **5,053 条** `sourceLocator` 含「53知识清单」（CHEMISTRY 2,103 / MATH 828 / PHYSICS 931 / BIOLOGY 1,191）。`knowledge-production/scan-registry-2026-09.json` 407 条扫描决定中含该来源者 0 条（`build/agent-outstanding/group-5.md:41`）。版次信息只落在 gitignored 的 `build/2027-53-pages/manifest.json`（MATH/PHYSICS/CHEMISTRY/BIOLOGY = 552/203/235/215 页；无字节数/SHA-256/ISBN）。包内来源记录：`sourceType=AUTHORIZED_EDUCATION_MATERIAL`、`publisher="教辅汇编（桌面原始资料）"`、`edition="2026/2027版"`、`sourceUri="https://www.example.edu/desktop-kb-source"`（**示例域名，非真实来源地址**）、`licenseStatus=REFERENCE_ONLY`、`contentUsePolicy=REVIEWED_SYNTHESIS_ONLY`。入包提交 `06efe831`。
- **对学生的影响**：随包分发的 5,053 条材料来自一个在正式登记册里不存在的来源，身份/版次/授权链只有示例域名的 URI 与笼统标题——一旦被质疑「依据哪一版、哪份授权」，现在答不上来。
- **怎么做**：①在 register 增加该来源条目（可按四科 4 条或 1 条合并）：publisher/ISBN、版次、页数、PDF 字节数与 SHA-256、精确物理路径、三条政策字段；②把 `build/2027-53-pages/manifest.json` 的 PDF 身份补进知识生产侧登记；③修包内 `sourceUri` 的示例域名与 publisher 的笼统称谓；④工具：`pwsh -NoProfile -File tools/audit-knowledge-source-register.ps1 -ProjectRoot .`、`tools/audit-knowledge-source-rights.ps1`（注意 PowerShell 编码问题，见 ⑤-8）。
- **依赖**：裁定（教辅是否强制入正式登记册；使用政策最终口径）；外部（出版方/版次信息若需核对需看实体书）。
- **验收判据**：登记册含该来源且字段齐全（版次/指纹/许可/定位）；两个 audit 脚本无新增告警/继续 exit 0。
- **预计工作量**：0.5–1 人日（填表＋4 本 PDF 指纹核对）。

#### ⑤-3 OpenStax/Siyavula 审校为 0、2025 课标正文 ACQUIRED_UNREVIEWED，来源生产门未就绪

- **事实**：`knowledge-production/open-teaching-human-review-decisions-2026-v1.json`：`reviewState=NOT_STARTED`、`decisions=0`、`inventoryCandidateCount=666`、`automaticKnowledgePackMutationAllowed=false`；inventory `boundary.state=AI_LOCATOR_INVENTORY_REQUIRES_HUMAN_REVIEW`、`rawTeachingTextIncluded=false`、`formalKnowledgeCoverageContribution=0`；OpenStax 原文禁入模型上下文的依据 `docs/knowledge-source-governance.md:73`。源登记 `acquisitionState`（32 条）：`ACQUIRED_UNREVIEWED 19 / METADATA_VERIFIED 9 / ACQUIRED_REVIEWED 3 / PENDING_ACQUISITION 1`；**9 条 `candidate:moe:2025:*:curriculum-text` 全部 ACQUIRED_UNREVIEWED**。生产就绪门实跑：`pwsh -NoProfile -File tools/audit-knowledge-source-register.ps1 -ProjectRoot "D:\smart mistake book" -RequireProductionReady` → **退出码 1**（缺九科 current-text evidence / reviewed multi-source teaching references / method references / worked-example references），不带 `-RequireProductionReady` 时 exit 0 但 `sourceProductionReady=False`。开放教材审计：`python tools/audit-open-teaching-epubs.py --check` → **退出码 1**（缺 Siyavula EPUB）。
- **对学生的影响**：「九科全覆盖」在当前证据下不可用（来源门未过）；开放教材交叉核验零进展，学生侧不会有任何来自 OpenStax/Siyavula 的补充内容，也没有对应风险（因为禁入模型上下文）。
- **怎么做**：①先把 9 科 2025 课标正文做成逐条映射并完成人工审校（覆盖门的硬前置，与 ③-1 互为上下游）；②开放教材若推进：按治理文档逐书登记＋人工审校，EPUB 先拉回 `.artifacts/research/open-teaching-references/`；③OpenStax 一律 `DERIVED_CONTENT_ONLY`。
- **依赖**：外部（官方原文获取、EPUB 许可与文件）+ 裁定（是否投入开放教材审校）。
- **验收判据**：`-RequireProductionReady` 退出码 0、`reviewedCurrentTextSubjects` 覆盖九科；`tools/audit-open-teaching-epubs.py --check` 退出码 0；开放教材决策 `reviewState != NOT_STARTED`。
- **预计工作量**：九科课标逐条审校是数十人日量级；EPUB 审校本轮不建议排期。

#### ⑤-4 机械 SKIP 107,160 块从未抽检（验收门已定，无执行产物）

- **事实**：本会话数行 `tools/kb_coverage/tables/mechanical_skips.csv` = **107,160**；分类前几名：题目派生（含成套选项）68,705 / 题目派生（含答案判定语）30,503 / 广告宣传 2,480 / 同内容指纹重复（只留首次）1,131 / 题目派生（标题是题号/来源）992 / 「过短（<40 字）」若干档合计数千（`build/agent-outstanding/group-5.md:91-92`）。验收门已定（`docs/agent-first-refactor-decisions-2026-09-23.md:2437`：分层抽检、报误杀率、报告落 `docs/research/`、误杀率高于阈值则重开该判据的分流）；全仓搜「误杀」只命中决策/设计/分期 3 个文档，`docs/research/` 无对应报告。
- **对学生的影响**：池子里 57.4% 的料被机械判为「题目/广告/过短/重复」后静默丢弃；若「讲解+例题混合块」被误杀，学生就少一份讲解材料，且没有任何抽样数据说明误杀率。
- **怎么做**：①一次性抽样脚本：按 note 分层、每类 `random.Random(<seed>).sample(..., N)`（建议 N=30）取块，回 `tools/kb_coverage/tables/extracted_chunks.jsonl` 按 `chunk_id` 取原文；②人工逐条判「该 SKIP 判得对不对」，统计每类误杀率；报告落 `docs/research/`；③验收门：每类判据给出误杀率与样本量；误杀率高于阈值时按决策文档重开该判据的分流（改 `tools/kb_coverage/make_judgment_slices.py` 的 prescreen 规则）。
- **依赖**：N 与误杀阈值（建议 30/类、10%；工程值，开工前可一并拍板）；环境（块池文件在盘）。
- **验收判据**：`docs/research/` 出现抽检报告且给出四类判据各自的误杀率；无误杀超标的判据被重开。
- **预计工作量**：4 类 ×30 条 ≈ 120 条人工判读，1–2 人日（不含重开分流的返工）。

#### ⑤-5 CI KD-27 仍红，但成因已变（chapter_map 与 pypdf 已修）

- **事实**：本会话 `gh run list` → 最新 main push `36965281642` 结论 **failure**；卡片 `gh run view --job 110707577008 --log` 抓到 `Ran 585 tests … FAILED (failures=6, errors=7, skipped=1)`（本会话复读同一行）；7 条 ERROR = `test_kb_transcription_ledger.RealArtifactTest` 6 条 + `test_kb_check_transcripts.RealArtifactTest` 1 条（报错均为 `SystemExit: 缺 manifest：build/2027-53-pages/manifest_slim.json`）；6 条 FAIL = `test_kb_content_audit_verdicts.ShippedTableTest` 2 条 + `test_kb_materialize.PlanTest` 4 条。同 job 第 8–19 步因 step 7 红全部 skipped；第 22 步 commit-back skipped ⇒ `docs/status.md` 仍是 2026-09-28 的 local-manual 版（本会话读头部确认）。基线所述「chapter_map 5 行坏 slug」已不在（修复提交 `95236e0c`，`load_chapter_map()` 实测输出 1401 行）；「pypdf 未安装」也已修（workflow `:59-60`）。本地整套 Python 测试全绿（`build/agent-outstanding/group-5.md:119`：`Ran 586 tests in 163.468s / OK`；本会话复跑结果见 §7）。
- **对学生的影响**：无直接界面影响；但「门全绿」失去随仓库的自动凭据——任何以 CI 绿为前提的发布/完成声明都不可用；长期红会训练人忽略红。
- **怎么做**：①把 artifact 依赖测试改成「缺 artifact ⇒ 显式 skip 并写理由」，或把 53 扫描产物做成 CI 可见 fixture（`build/` 不入库，需换存放位置）；②修 `test_kb_materialize.PlanTest`（4 条）与 `test_kb_content_audit_verdicts.ShippedTableTest`（2 条）的 fixture 依赖；③复跑验证：本地全绿后推 main，再 `gh run list --limit 1` 看结论。
- **依赖**：fixture 放哪、是否允许 skip（SKIP 要写理由）；CI 往返约 1 小时/次。
- **验收判据**：最新 main push 的 check job 结论 success，且 `docs/status.md` 被 CI 写回（commit-back 不再 skipped）。
- **预计工作量**：0.5–1 人日改测试 + CI 往返。

#### ⑤-6 仪器化三门本机可跑且三门全过（订正「本机不可跑」口径）

- **事实**：卡片本会话实跑 `./gradlew.bat :core:data:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=…DenseEncoderParityInstrumentedTest,…GoldenRetrievalInstrumentedTest,…DenseFirstUseCostInstrumentedTest"` → `BUILD SUCCESSFUL in 4m 48s`、退出码 0、XML `tests="3" failures="0" errors="0"`；logcat：`DenseEncoderParity` n=330 min=0.99955/p95=0.99985；`GoldenRetrieval` p95=101ms/p50=63ms；`DenseFirstUseCost` firstOrder=353ms（`build/agent-outstanding/group-5.md:140-144`；本会话未复跑）。使 Golden 转绿的分片删除修复是 `f4cf4e31`（2026-10-02），本轮三过是其修复后的首个实测。文档内部自相矛盾：`docs/kb-dense-rebuild-2026-10-01.md:343` 与 `docs/kb-point-intake-2026-10-01.md:108-109` 仍写「本机不可跑仪器化（KD-25，UNVERIFIED）」。注意：CI 侧 instrumented job 仍红（run `36965281642` 的 instrumented job 败在 `reactivecircus/android-emulator-runner@v2` 步骤），本轮未归因。
- **对学生的影响**：无直接界面影响；但这三门是 dense 编码一致性、金标检索、首用开销的硬门，本地可跑意味着「不知道端侧检索是否正常」不再是可接受的借口。
- **怎么做**：①把命令写进验收脚本/文档（注意：README 里 `connectedDebugAndroidTest --tests "…"` 在本仓库 Gradle/AGP 组合下被拒，必须用 `-Pandroid.testInstrumentationRunnerArguments.class=`）；②把两处「本机不可跑」口径改成「可跑＋命令＋最近读数」；③真机延迟仍属未覆盖项。
- **依赖**：环境（模拟器在线；真机另需物理设备）；裁定（CI instrumented job 的红是否另行归因）。
- **验收判据**：上述命令退出码 0 且 XML `failures=0`（卡片已满足）；三门最近一次读数落进台账。
- **预计工作量**：复跑约 5 分钟；口径修正 0.2 人日。

#### ⑤-7 安全扫描仍 inconclusive；最新封存读数 5 high / 0 medium / 7 low（与 KD-14 旧读数不同）

- **事实**：卡片实读最新封存扫描 `~/.mimosa/security-scans/project-c079ff08106a86cc56edfee2/scan-2026-09-30T13-26-54.425Z-13f05c58edc9/`（deep，sealed `sha256:de8454e6…`）：`runStatus=inconclusive`、`completeness=partial`、`threatModel=partial`、`validation=completed`、`gaps=["部分分析阶段未能完整覆盖"]`；`findings.json` totals = `{high:5, medium:0, low:7, info:0, businessLogic:0}`；5 条 high 全是静态 advisory 的 path-traversal（含 tracked 的 `tools/dense_build/patch_litert_api_aar.py:97`），每条带 `proofGaps=["静态 advisory 需要人工确认真实数据流和可利用性。"]`。与 KD-14 旧登记「0 high / 0 medium / 3 low」（`docs/known-defects.md:539`）**不能沿用**；KD-14 规则仍有效：**不得声称「安全审计通过」**，封存件为准。
- **对学生的影响**：无直接界面影响；但「项目安全」不能作为对外表述。5 条 high 若为真，影响的是工具链脚本（参数注入→路径穿越），不直接暴露学生数据；若为假也需人工「确认/排除」，不能默认忽略。
- **怎么做**：①对 5 条 high 逐条人工确认数据流（tracked 的 `tools/dense_build/patch_litert_api_aar.py` 优先）；②关闭 `inconclusive` 需要主机能跑 Semgrep/PyCG（KD-14 记的缺口），当前环境不满足，按已知主机边界处理但每次发布声明必须引用；③复核口径：新的封存扫描以 `coverage.json` 的 `runStatus` 与 `findings.json` 的 `totals` 为准。
- **依赖**：环境（扫描器运行时——known host environment boundary，本机不可控）；裁定（是否接受该边界、是否处理 5 条 high）。
- **验收判据**：5 条 high 全部有「确认/排除」结论；或出现 `runStatus=complete` 的新封存扫描。
- **预计工作量**：5 条甄别 0.5 人日；关闭 inconclusive 依赖外部运行时（不建议排期）。

#### ⑤-8 （新发现）发布链的 6 个 PowerShell 审计脚本在默认 Windows PowerShell 5.1 下因编码必崩

- **事实**：卡片实跑 `powershell -NoProfile -ExecutionPolicy Bypass -File tools/audit-knowledge-source-register.ps1 -ProjectRoot "D:\smart mistake book"` → 退出码 1：`ConvertFrom-Json : 传入的对象无效… (1502)`（脚本 `:134`）；根因：PS 5.1 的 `Get-Content -Raw` 在本机（中文 GBK 代码页）按 ANSI 读 UTF-8 文件 → 乱码 → 解析失败；加 `-Encoding UTF8` 或改用 pwsh 7 后通过（register audit 退出码 0、parsed sources: 32）。同类脚本同样崩：`tools/audit-knowledge-source-rights.ps1:16` 等；全仓 grep 显示 **7 个**审计脚本都用 `Get-Content -Raw`（无 `-Encoding`）：`tools/audit-curriculum-coverage-draft.ps1:70`、`tools/audit-knowledge-coverage-ledger.ps1:102-103`、`tools/audit-knowledge-packs.ps1:79`、`tools/audit-knowledge-source-register.ps1:134`、`tools/audit-knowledge-source-rights.ps1:16`、`tools/audit-smartedu-lesson-activity-catalog.ps1:32`、`tools/audit-smartedu-textbook-catalog.ps1:32,67`。治理文档给的发布门就是直接 `& .\tools\audit-….ps1`——在本机默认 shell 下这些门**跑不起来**（报错退出，不是门红）。
- **对学生的影响**：无直接界面影响；但发布门在本机不可执行，「来源/权利/覆盖」三类治理检查实际处于「没跑」而不是「通过」状态。
- **怎么做**：给 6 个脚本的 `Get-Content -Raw` 统一加 `-Encoding UTF8`（或在调用处统一用 `pwsh`）；改完各跑一遍并把退出码记进台账。
- **依赖**：环境（本机默认 PowerShell 版本/代码页）；工具改动属治理脚本（需提交）。
- **验收判据**：Windows PowerShell 5.1 与 pwsh 7 下脚本行为一致（同一输入同一结论），无编码相关异常。
- **预计工作量**：6 处一行改动 + 复跑 ≈ 0.5 人日。

### 组⑥ 门与工具腐烂

#### ⑥-1 `tools/kb_coverage/materialize.py` type 白名单 7 值 → 与 ①-4 合并，见 ①-4

#### ⑥-2 `tools/kb_build/tables/boundary_map.csv`(20 行) 与 `tools/kb_build/tables/material_bindings.csv`(598 行) 不在表↔包对账里，却以「权威表」sha 展示

- **事实**：`tools/kb_build/check_pack_contract.py:362-378` 的对账清单只含 alias_map / chapter_map / chapter_by_node / prereq_map + point_rename/delete/merge/relocation，**不含** boundary_map / material_bindings；`tools/kb_build/tables.py:1-13` 称这两张是「权威表：人工定稿层」，唯一施加者是 `tools/kb_build/build.py:60-63,214-256`（已停用，见 ⑥-5）。本会话数行：boundary_map **20** / material_bindings **598**（对照包内 3,866 boundary / 50,383 材料）。卡片逐行比对：boundary_map **20/20 与包完全一致**（它是补丁表）；material_bindings **仅 113/598 一致**，**407 条「目标节点在包内存在、但材料现绑定不是它」（裁定未生效）**，78 条目标节点已不存在（例 `phys-ohmmeter-component-features→电阻`）。`docs/status.md:92` 以「权威表 sha256」展示 6 张表——本会话实算：chapter_map/boundary_map/prereq_map/material_bindings 与工作树一致，**chapter_by_source 与 alias_map 不一致**（见 §1.2）。
- **对学生的影响**：当下包没变，风险在「下一次按表动包」：若谁跑 `tools/kb_build/build.py --write`，407 条绑定会被表口径一次性改写；反向地，维护者若相信 status 页的「权威」展示，会把一张 81% 未生效的表当成现状。
- **怎么做**：①先裁 F9（这 598 行是「已裁定待执行的欠账」还是「已过期的历史表」）；②在 `check_table_consistency` 增两表语义并接入 `run_kb_checks`：boundary_map 查行可定位 + 值生效（当前 20/20 应直接过）；material_bindings 查 material_slug 在包、目标点存在、与包内绑定一致（不一致按裁定计为「未生效欠账」，把数字钉在门输出里）；③重生成 `docs/status.md`（`python3 tools/ci/generate_status.py`）并说明哪些表受门保护。
- **依赖**：F9（不裁定则这门只能做成「报警」而不能清零）。
- **验收判据**：`PYTHONPATH=tools python -m kb_build.check_pack_contract` 对两表 0 问题（或「未生效欠账」计数明确且单调下降）；status 页 6 个 sha 与工作树一致且标注哪些表受门保护。
- **预计工作量**：增两表对账 + 测试 0.5–1 天；执行器与清账取决于裁定。

#### ⑥-3 `tools/kb_build/overfull_nodes.py` 跑不通、自述数字过期 → 与 ①-3 合并，见 ①-3

#### ⑥-4 `tools/kb_build/scan_duplicate_nodes.py` 全仓无调用者 → 与 ①-2 合并，见 ①-2

#### ⑥-5 `tools/kb_build/build.py` 只靠约定停用，`--write` 仍在

- **事实**：`tools/kb_build/build.py:634-664` 仍解析 `--write`（`:636`）、写盘实现 `:608-614`（只写 `build/kb-staging/`，不写成品——这一点是安全的）；停用只存在于文字里（`:14`「整体停用」；`tools/build-knowledge-pack.py:7`）；代码里**没有任何守卫**（无旗标、无异常、无 CI 排除，本会话 grep `.github/workflows/*.yml` 无调用）；**3 个工具的错误提示仍在教操作者跑它**：`tools/kb_build/regroup_aggregates.py:276`、`tools/kb_build/resolve_points.py:68`、`tools/kb_build/wusan_plan.py:64`（「先跑 PYTHONPATH=tools python -m kb_build.build --write」）；`tools/kb_build/build.py:60-63` 一跑就会施加全量权威表（chapter/alias/boundary/prereq/material_bindings）——结合 ⑥-2 的 407 条未生效，一次误跑会用旧表口径整体重写 staging。
- **对学生的影响**：误跑 → staging 被旧口径整表覆盖（别名清空未列表、boundary 剥原文、407 条绑定改写），若随后 promote 会把陈旧口径带进成品。
- **怎么做**：①停用从约定变代码：删除 `--write`，或保留但加显式危险旗标（如 `--i-accept-deprecated-build`）并在 `build()` 内对非测试调用 raise（测试用注入夹具不受影响）；②修 3 处误导提示（改指 `kb_build.promote` 或说明停用、staging 缺失应从成品 seed：`pack_io.seed_staging`）；③若确有「从表重建 staging」的合法用途，在 `tools/kb_build/build.py` 头部写明唯一用途与禁令。
- **依赖**：工程取舍（保留唯一用途 vs 彻底封死）。
- **验收判据**：`PYTHONPATH=tools python -m kb_build.build --write` 不再落盘（exit≠0 或明确拒绝）；全仓 grep 不到推荐用法；`tools/tests/test_kb_build_generator.py` 仍绿。
- **预计工作量**：0.5 人时。

#### ⑥-6 旧数字清单（逐点核对，全部给出实测现值）

- **事实**（左侧为文档/工具旧值，右侧为本会话或卡片实测现值）：

| 位置 | 文中旧值 | 实测现值 |
|---|---|---|
| `tools/dense_build/check_asset.py:7` | 3,572 节点 + 25,359 别名 | 3,866 + 37,327 = **41,193** 向量（本会话 `R5`；`tools/dense_build/check_asset.py` 实跑输出 `count=41193`，`build/agent-outstanding/group-6.md:111`） |
| `tools/dense_build/check_asset.py:114` | ids 28,931 条 | 41,193 |
| `tools/dense_build/README.md:106,135`（另 `:20,44,145,204`） | 40,319 = 3,570 + 36,749；n=290 | 41,193 = 3,866 + 37,327；fixture count=330 |
| `core/data/src/main/assets/dense/README.md:45` / `:22` | 28,931 行 / 对拍 n=290 | 41,193 / fixture **n=330**（query 130 + surface 200；n=330 下对拍未重测 → UNVERIFIED） |
| `tools/kb_build/overfull_nodes.py:11-13` | 1,670 / 23,825 / 17,145 / 3,572 | 2,518（>4）/ 47,538 / 37,466 / 3,866（本会话 `R4`） |
| `docs/kb-chunk-judgment-protocol.md:97-101` | 16,625 行；698 行 MATERIAL 旧 type；merge 白名单「现含 7 值」 | 86,414 行；MATERIAL 旧 type **0** 行（482 条旧 type 全在 SKIP）；merge 已是 3 值（本会话 `R8`） |
| 多处「22 门」 | 22 道 | **23 道**（本会话 `R13`） |
| `docs/kb-problem-register-2026-09-15.md:616` | 数学 118/308 | 130/308（本会话 `R6`） |
| `docs/kb-textbook-source-verification-2026-09-21.md` W-2 段 | 「1,232 个薄料节点」「3,572 个知识点」 | live 571 薄料 / 3,866 点 |
| 基线 §5 命令 | `python tools/kb_build/gate.py` | 需 `PYTHONPATH=tools`（本会话实跑） |

- **对学生的影响**：不直接可见，但维护决策会按错数字排工（按 1,670 超配排期实际 2,518；按 40,319 估 dense 覆盖实际 41,193）；错误数字进研究报告会二次传播。
- **怎么做**：①按上表逐点更新；历史口径（如 28,931、Stage-5 旧值）保留但显式标注「历史/当时口径」；②`core/data/src/main/assets/dense/README.md:22` 要么在 n=330 fixture 下重跑宿主对拍并更新，要么标「n=290 为 v1 fixture 读数」；③可选加文档数字自查 grep（命中 3,572/25,359/3,570/36,749/40,319/28,931/1,670/「22 门」即提示）。
- **依赖**：环境（n=330 对拍需 `build/tflite-venv` 与模型件）；清账本身无依赖。
- **验收判据**：全仓 grep 旧数只在显式标注「历史」的段落出现；报告引用的每个数字都能对应到一条复算命令。
- **预计工作量**：清账 1–2 人时；加自查门 +2 人时。

#### ⑥-7 材料语义质量无门：「全绿」只证明机械形状与哈希未变

> 与 ①-1（D-1）同源；此处只保留「门覆盖」结论与新增实测。

- **事实**：`tools/kb_build/gate.py` 的 23 项指标全部是机械判据（名称形态、重复名、绑定缺失、别名一致、前置声明、boundary 原文/残迹、LaTeX/转义/控制字符、章节定位、topic 顺序/命名/层级）；`grep -n '"type"|materialType' tools/kb_build/gate.py tools/kb_build/check_pack_contract.py` → 0 命中。本会话实跑：同一份包里躺着 667 条协议外旧 type 材料，而门 23/23、契约/roundtrip/manifest/dense 全 OK。近语义的检查都是离线、留给人的（`tools/kb_build/audit_content_bindings.py:10-11`、`tools/kb_build/audit_quality.py:8-9`）；唯一写成门语义的 `check_exam_leak` 实测**未被调用且当前 exit 1**（206 命中，见 §1.2、本会话新发现）。
- **对学生的影响**：把一条材料换成「通顺但与节点无关的废话」，23 门 + 契约 + roundtrip **全绿**，没有任何抽样/抽检流程接住它——学生会在讲题回答里看到一条对不上题的材料当依据。
- **怎么做**：①**裁定**（F10）：要不要为材料语义设门、口径与阈值（可参照 ≥0.95；或先只做定期抽检不设硬门）；②零裁定的机械部分：把 `check_exam_leak`（先清 206 命中或降为报告形态）与 ①-4 的 type 门接进 `tools/ci/run_kb_checks.py`，并把材料绑定存在性纳入 `check_pack_contract`（现在契约检查完全不看材料）；③语义抽检做成可复跑流程（固定抽样框、判定协议、产物表、验收数字），与 D-1 复用同一套工具。
- **依赖**：F10；外部无。
- **验收判据**：白纸黑字写下「当前有哪些材料级门、各自阈值」；对人工构造的「通顺废话」样本，流程必须能报出它（本会话未构造/未跑 → 该点 UNVERIFIED）；机械检查在 `run_kb_checks` 里有明确 OK/FAIL。
- **预计工作量**：机械接线 2–4 人时；语义抽检流程本身是独立项目（与 D-1 合并计）。

---

## 3. 需要用户裁定的分叉

> 每条给出：选项 / 我的推荐 / 推荐的理由与代价 / 另一支的后果。共 **15** 条。

### F1 教材入库授权
- **选项**：A 不申请许可（只作人工核对参照，验收口径改为「目录/知识点覆盖已对齐 2019 人教版 A版」）／ B 走 4001910910 / ncetbgs@moe.edu.cn 申请书面许可 ／ C 只做覆盖核对（用平台 A版目录产《教材覆盖对照表》）。
- **推荐**：A。
- **理由与代价**：官方两条独立来源（人教社 / 国家平台）都禁止转载改编且点名 App（`docs/kb-textbook-source-verification-2026-09-21.md:70-83`）；W-7 事实上已按 A 执行（只读目录不留原文），学生可见的章节目录对齐不依赖原文。代价：教材原文不能作为材料来源，边界表述仍靠教辅/课标。
- **另一支的后果**：B 申请周期与结果不受控，可能长期挂空、阻塞 ③-6 的验收口径；C 需要 SmartEdu 目录数据（**当前不可复算**，见 ③-5）或人工逐页读目录，多花 1–2 天但产出对外可引用的对照表。

### F2 章节树口径
- **选项**：① 1:1 对齐（补章节点 化学8/生物6/物理3/数学2 + 章名 + 拆合并 + 补缺失节）／ ② 只修跨册错位 + 章名/章层 + 生物旧名 + 数学 54 条。
- **推荐**：②。
- **理由与代价**：①≥2 周，且会继承 `kb_tools/chapter_structure_full.json` 的 3 类已知错误（③-8）；②3–5 天，先消掉「打开一册看到别册的节」这类学生可见缺陷。代价：章节点与教材不 1:1，接受「知识重组」口径并在文档写明。
- **另一支的后果**：①会把错误节表传播进 2048 行提案，且工时被教材细对齐吞掉，学生侧短期看不到任何改善。

### F3 超配 2,518 + 91 条容量拦截的处置口径
- **选项**：①放弃 ≤4 规则、全部直接落 ／ ②按粒度清理后落（拆点/合并/删冗余三档）／ ③维持 ≤4 拦截。
- **推荐**：②（先清理后落），对 91 条逐条走三选一。
- **理由与代价**：运行时无条数门，直接落也能进预算（20k 通常装得下 5 个中位节点），所以「不可见」不是理由；但 139 条材料的节点里排序靠后的偏泛材料会挤占提示词位置与预算，清理才有质量收益。代价：2,518 个节点的分层裁决是最大工时项（大）。
- **另一支的后果**：①=让 37,466 条超配绑定按标题排序参与竞争，弱质量材料可能挤掉该出现的材料；③=维持一条已被运行时推翻的规则，91 条补料永远缺席，账目继续失真。

### F4 D12 检索目标口径
- **选项**：①继续追 0.90（保持裁决 29）／ ②按 CI 重议阈值 ／ ③暂不设阈值。
- **推荐**：①，但把 D-3 第 3 步拆成有序实验序列（扩别名 → 改排序 → 融合/档位），每轮出按章 CI。
- **理由与代价**：0.7308 的按章 CI 上界 ≈0.83 < 0.90，差距是系统性的，不能靠噪声解释；且 3D 是阶段 5 硬门，降低阈值等于把未验证的检索带进承重面。代价：大工作量（多轮实验 + 双侧出数）。
- **另一支的后果**：②会重新定义验收线，须同时重写「达标」话术与阶段 5 的进入条件；③让阶段 5 在无检索质量约束下开工，学生第一次看到的判定/掌握可能引用错材料。

### F5 dense 大档取舍
- **选项**：①关闭大档（以 Stage-6 证据收口）／ ②重开（先重定延迟预算与档位口径，再按 Stage-6 同口径重测）。
- **推荐**：①。
- **理由与代价**：6.90× > 3× 的宿主延迟硬线；口径已从「没有端侧路径」更正为「延迟不允许」，结论不变。代价：弱章召回维持现状（两档共同瓶颈章都是物理·相互作用，换大档主要买主集）。
- **另一支的后果**：②会拖慢每轮讲题的端侧编码，且需要真机（当前无物理设备）才能给出可信判定。

### F6 FTS5 是否上生产
- **选项**：①换（按计划 §3 实施）／ ②不换（保持 COUNT(DISTINCT) 排序）／ ③先补 JVM 侧 FTS 镜像，在判官 v2 上重出数再裁。
- **推荐**：③ → 若出数显示词面腿增益显著再走 ①。
- **理由与代价**：生产形下 FTS5 排序无增益（0.5333 vs 0.5444），增益只在改返回形态后出现；而驱动切换的冲击面大（迁移矩阵/备份/图书馆 FTS4/schema dump），计划里 7 项未证实 + DB 版本已过期（56）。代价：推迟换引擎，弱章词面腿的 IDF/长度归一问题继续存在。
- **另一支的后果**：①在未重基线、7 项未证实的情况下动驱动，风险落在安装与迁移链；②等于长期接受公共 2/3-gram 大节点挤掉自然语言问句。

### F7 667 条旧 type 处置口径
- **选项**：①重判替换（走 698 行重判队列，用 3 值重判）／ ②冻结为「历史 type」并具名豁免 + 计数门。
- **推荐**：①。
- **理由与代价**：协议已禁 4 值，type 参与端侧重教排序；重判替换能一次性消除口径分裂。代价：2–4 个代理批次（698 行逐条重判）。
- **另一支的后果**：②让包内长期保留 667 条协议外材料，且每次讨论 type 都要先解释豁免；排序口径继续分裂。

### F8 KD-29 AI 生成内容标识
- **选项**：①现在做 UI 最小形态（一张卡一句固定文案 + 用例）／ ②继续登记为边界 ／ ③内容侧 schema（材料带 AI/provenance 字段 + 重打包）。
- **推荐**：①，③随下一次包重建顺带。
- **理由与代价**：生产展示面当前不可达（`teachingArtifact()` 恒 null），但重教/补救路径一旦启用就会暴露；最小形态 1 天，低风险、可复用文案常量。代价：只覆盖 UI 面，随包材料元数据仍无 AI 字段。
- **另一支的后果**：②在上架/对外分发前必须重开（`docs/known-defects.md:1111-1112` 的重开条件），届时可能被动；③要动内容管线与包版本，工时 3–5 天且需与写入窗口协调。

### F9 `tools/kb_build/tables/material_bindings.csv` 598 行的性质
- **选项**：①「已裁定、待执行」的欠账（需要受门控的执行器把 407 条落进包并清账）／ ②「已过期的历史表」（重生成或归档，并在 status 页去掉权威展示）。
- **推荐**：①，其中目标已不存在的 78 条按历史清理另计；给执行器加门控（边界存在性 + 材料绑定对拍 + 单调下降）。
- **理由与代价**：81% 未生效的表若被当权威，下一次 `tools/kb_build/build.py --write` 会一次性改写 407 条；把它当欠账并把数字钉在门输出里，风险可见可控。代价：需要写执行器 + 对账门（0.5–1 天 + 裁定）。
- **另一支的后果**：②会让 407 条已裁定口径永久丢弃（或需人工重做），且 status 页要撤掉两张表的权威展示。

### F10 材料语义门
- **选项**：①设硬门（语义精确率 ≥0.95，接 CI）／ ②定期抽检 + 报告，不设硬门 ／ ③暂不做。
- **推荐**：②起步，先清 206 条 exam-leak 命中与 D-1 的 496 条，再据实测决定是否升 ①。
- **理由与代价**：现阶段没有人类金标（裁决 29 明确标注者为模型、单一来源）；硬门会把模型判定固化成发布条件，误杀/漏杀的代价都不小。代价：语义质量仍是「定期体检」而非持续护栏。
- **另一支的后果**：①若判定通道本身有偏，硬门会制造假安全感；③则「全绿≠对题」的风险无人接住。

### F11 大厅（无题轮）读库路径
- **选项**：①(a) 本地能判科时把该轮升级为「有题轮」／ ②(b) 给大厅加 subject 并扩披露集合 ／ ③只做止血不读库。
- **推荐**：③（立即）+ ①（随后）；②留作后续。
- **理由与代价**：①不动披露集合与 manifest schema，Respond 已有 subject，2–3 天；(b) 要动 `ModelEgress` 的精确相等校验与旧 schema 分档，3–5 天且放宽披露面，需要单独的风险论证。代价：大厅在本地判不出科时仍读不到库。
- **另一支的后果**：②最快让大厅全量可用，但要改披露契约（外部 provider 清单核对、旧行读回），是数据外发面的变更；③长期维持「首页问概念零材料」。

### F12 `knowledgeCoverage` 去留
- **选项**：①最小展示（覆盖计数 + pendingGaps 计数与最近条目）／ ②删除整条链。
- **推荐**：①。
- **理由与代价**：②-4 的闭环需要可见面，学生「没学过/库里缺」的信号目前只存在于内存与日志；最小展示 1–2 天。代价：多一个产品面要维护（数字必须与 DB 一致）。
- **另一支的后果**：②按第 12.2 条删掉无消费者机制，但 grounding 闭环即使做成也没有任何学生可见的回响，覆盖口径也无法在端上自证。

### F13 grounding 闭环口径
- **选项**：①本地自动解析（映射到最近/父节点或 pseudo，失败显式丢弃+原因）／ ②挂离线人工审校（与研究管线联动）／ ③先只做显式丢弃（消灭静默）。
- **推荐**：①；②作为 ① 失败样本的后续通道。
- **理由与代价**：离线人工审校需要驱动方与审校人（②-7 至今无驱动方、无人类专家），短期不可达；①能让「再整理一次」收敛。代价：自动解析会引入新的错绑风险，必须与 D-1 判定口径/绑定可信度字段（D-0）联动并留证据。
- **另一支的后果**：②让 pending 继续堆积（只是从「永远 pending」变成「永远等待审校」）；③不解决学生侧不收敛的体验。

### F14 覆盖发布口径
- **选项**：①把 189 条选修与态度型移出分母，单列「主线覆盖（必修+选必）」／ ②维持 661 分母并加长注释。
- **推荐**：①（同时保留全量口径并列公布，沿用 publishedRule 的三件套：分子/分母/UNDECIDED）。
- **理由与代价**：47 条主线缺口才是可行动项；现行 661 分母含 189 条选修与态度型，单看 0.53 会被误读为「一半没做」。代价：要维护两套数字与口径说明，且历史对比需换算。
- **另一支的后果**：②保持单一数字但对外解释成本高，且补料排期会被选修段（对提分无感）绑架。

### F15 材料时效口径
- **选项**：①按来源版次/年份定「有效版本」（跨课标/教材版本即需复核，年数阈值 N 后定）／ ②按 `reviewedAt` 做「最近复核期」 ／ ③只登记字段、不设阈值（先出报告）。
- **推荐**：①（来源版次为主口径，`reviewedAt` 作复核时间辅助）。
- **理由与代价**：材料过时的根因是「依据了旧版课标/教材」，不是「很久没被重看」；按版次判能直接对准失效原因，也是 ①-9 台账能落地的形态。代价：要把 42 个时间戳与 `source-register` 对账（中–大工作量），部分教辅版次需实体书核对。
- **另一支的后果**：②会把「旧版依据但近期重贴过」的材料判为新鲜，漏掉真正的时效风险；③只留字段不设阈值，时效风险依然不可见（但至少可审计）。

---

## 4. 建议派单

> 共享工作树纪律：不切分支、不 amend/rebase；显式文件列表提交；开工前先确认工作树里他线在飞改动（见 §6），只碰自己范围内的文件。
> 每批末尾给验收门与复算命令；「并行子代理」是建议的并发上限（同一批内任务相互独立）。

### 批次 0 · 零裁定、可直接开工的小修（并行 3–4 个子代理）

**范围（每项都独立、可单独提交）**
1. **工具修复**：`tools/kb_build/overfull_nodes.py` 的 load 改走 `pack_io.sidecar_paths()`、更新自述数字（2,518/47,538/37,466/3,866）与机制描述、加合成夹具测试（断言不读索引）。
2. **白名单收紧**：`tools/kb_coverage/materialize.py:50-51` TYPES → 3 值（与 `tools/kb_coverage/merge_text_judgments.py:44-45` 逐字一致）；更新 `docs/kb-chunk-judgment-protocol.md:95-101` 的过期段落。
3. **扫描器产品化**：`tools/kb_build/scan_duplicate_nodes.py` 加 `main()`/`--out`/`--json`、落 CSV、加测试（本会话实测 11 组/18 对可作夹具基线）。
4. **生成器封口**：`tools/kb_build/build.py` 的 `--write` 加显式危险旗标（默认拒绝落盘）；修 `tools/kb_build/regroup_aggregates.py:276`、`tools/kb_build/resolve_points.py:68`、`tools/kb_build/wusan_plan.py:64` 三处误导提示。
5. **学生侧小修**：`MASTERY_UPDATE` 回显节点名（runner + `TutorToolTraceEntry` + 行文案 + 用例）；大厅提示词加「无科目不要申请 KNOWLEDGE_READ / MASTERY_READ」（止血行）。
6. **发布链脚本**：6 个 PS 审计脚本的 `Get-Content -Raw` 加 `-Encoding UTF8`，在 PS 5.1 与 pwsh 7 下各跑一遍记录退出码。
7. **CI 回绿**：7 条 artifact 依赖 ERROR 改「缺 artifact ⇒ 显式 skip + 理由」（或把 53 产物做成 CI 可见 fixture）；修 `test_kb_materialize.PlanTest`(4) 与 `test_kb_content_audit_verdicts.ShippedTableTest`(2) 的 fixture 依赖。
8. **文档清账（零裁定部分）**：⑥-6 表逐点订正（含 tools/kb_build/gate.py 命令写法、118/308、25 模块表述、dense README 的 n=290 标注、W-2 段的 1,232/3,572）；重生成 `docs/status.md`。
9. **只读产出（不改包）**：`check_exam_leak` 的 206 条命中清单 + 按节点汇总落 `docs/research/`（作为 F10/①-1 的输入）；`material_bindings` 407 条未生效欠账清单落盘（作为 F9 输入）。

**工具**：仓库现有 Python 工具 + gradle 单测；不碰生产包、不跑 `--write` 类内容写入。

**验收门（本批全绿）**
- `PYTHONPATH=tools python tools/kb_build/gate.py` → 23 项全 0；
- `python tools/ci/run_kb_checks.py` → 5 节全 OK；
- `PYTHONPATH=tools python -m unittest discover -s tools/tests -t tools` → 全绿；
- `PYTHONPATH=tools python -m kb_build.overfull_nodes` → exit 0；
- `./gradlew.bat :core:data:testDebugUnitTest --tests "*RoomTutorToolRunnerTest*"` 与 `./gradlew.bat :core:model:test --tests "*TutorToolTraceTest*"` → 绿；
- `gh run list --limit 1` → 最新 main push 结论 success。

**复算命令**：见 §7 的 R13–R17。

### 批次 1 · 结构完整性的「测量 + 门」（依赖批次 0；并行 2）

**范围**
- 用修好的 `overfull_nodes --write` 产出 2,518 节点的施工清单（预期 47,538 行），并按排序口径标注「哪 4 条会活、其余按序竞争预算」。
- `check_pack_contract` 增 boundary_map 值生效 + material_bindings 未生效欠账计数两条对账（先以「计数 + 报告」形态接入 `run_kb_checks`，不阻断）。
- 材料 type 计数门（基线豁免形态：打印 667 并递减告警），供 F7 决策后翻硬门。
- 12.6ms 探针脚本入库并复测（S19 遗留，④-6）。
- `docs/status.md` 的 6 个 sha 与工作树对齐 + 标注受门保护表。

**工具**：修后的 `tools/kb_build/overfull_nodes.py`、`tools/kb_build/check_pack_contract.py`、`tools/ci/run_kb_checks.py`、`tools/ci/generate_status.py`、新入库的 12.6ms 探针脚本。

**依赖**：批次 0；F9 只影响「执行器」不影响「测量」。

**验收门**：`check_pack_contract` 输出两表计数（boundary 0 问题、material_bindings 407 具名）；overfull CSV 行数 = 47,538；探针数字落盘；`run_kb_checks` 5 节 OK。

**复算命令**：§7 的 R18–R20。

### 批次 2 · D-1 语义裁定流水线（依赖 F10；并行 2–3）

**范围**
- 冻结 496 候选与 161 靶区；先做切片内 101 条（其中仅 23 条与既有 409 行裁定重合，其余按新候选裁定），再扩到 395 条切片外。
- 75 条别名嫌疑 + 32 条 low 复核同批走（同一判定协议、同一裁定表形态）。
- 与内核线 D-0（绑定可信度字段）对接：判定结果落账形态需确定（该字段属 3D 的 D-0，若内核线未开工，本批先产 CSV 并由 §6 的接口约定承接）。

**工具**：`tools/kb_build/audit_content_bindings.py`、`tools/kb_build/audit_bindings_by_alias.py`、`tools/kb_coverage/apply_rebind_verdicts.py`、`tools/kb_build/rebind_materials.py`、`tools/kb_build/merge_content_audit_verdicts.py`。

**依赖**：F10 口径；D-0 字段落点（与内核线协调）。

**验收门**：语义精确率 ≥0.95；`merge_content_audit_verdicts --check` exit 0；`rebind_materials` 报 0 待写、0 悬空；报告写明「模型标注、单一来源、无人类双复核」。

**复算命令**：§7 的 R10、R21。

### 批次 3 · 结构清理执行（依赖 F3/F7 + 批次 2 裁定；并行 2）

**范围**
- 667 条旧 type 的重判替换（698 行重判队列闭环）。
- 91 条容量拦截逐条落/清/弃。
- 近重复 11 组 + 18 对合并或保留；2 组互补性复核。
- 8 空点与 9 bad 的建点/放弃决定落地（建点后重算零材料节点与别名，保持门全绿）。
- 每次内容写入守写入窗口纪律（D-5）：快照指纹 → 表 → promote → 23 门全绿；**凡门不绿不许削门**。

**工具**：`tools/kb_coverage/make_judgment_slices.py`、`tools/kb_coverage/merge_text_judgments.py`、`tools/kb_coverage/apply_rejudged_materials.py`、`tools/kb_coverage/materialize.py`、`tools/kb_build/merge_points.py`、`tools/kb_build/create_points.py`、`tools/kb_build/promote.py`、`tools/kb_build/gate.py`、`tools/ci/run_kb_checks.py`。

**依赖**：F3、F7、批次 2 的裁定结果。

**验收门**：`tools/kb_build/gate.py` 23/23；`run_kb_checks` 5 节 OK；`check_slice_verdicts` 232 片「不过 0 个」；零材料节点保持 0；材料数只增不减（改绑路径）或按弃料记录递减。

**复算命令**：§7 的 R13、R15、R18、R22。

### 批次 4 · 学生侧闭环（依赖 F11/F12/F13；并行 1–2）

**范围**
- 大厅：先落止血，再按 F11 选 (a)/(b) 实现。
- grounding 闭环驱动 + `knowledgeCoverage` 最小展示（或删链）。
- 研究/审校管线按 F13/§5 的决定决定是否开工。

**工具**：`RoomTutorToolRunner` / `TutorToolTrace`（Kotlin）、`RoomMistakeOrganizationRepository`、`SmartMistakeBookRoot` / `LearningMasteryScreen`（Compose）、gradle JVM 与 instrumented 任务。

**依赖**：F11/F12/F13；设备用例需模拟器/真机。

**验收门**：大厅带 subject 时 `KNOWLEDGE_READ` resultCount>0；pending 请求能走进 resolved 或显式丢弃；「再整理一次」收敛；不产生额外模型 egress；JVM + instrumented 用例绿。

**复算命令**：§7 的 R16、R23。

### 批次 5 · 检索实修召回（依赖 F4/F5/F6；并行 2）

**范围**
- 按裁决 29 的工作项做有序实验：扩别名 → 检索排序（含 FTS5 决策）→ dense 档位与融合参数；每轮 JVM 镜像 + 真 SQL 双侧出数 + 按章 CI。
- 先把设备 v2 MISS 账本 `adb pull` 回落盘。
- ④-6 的接线（每进程每科一次）在本批顺带做。

**工具**：`GoldenRetrievalJvmTest` / `GoldenRetrievalInstrumentedTest`、`tools/kb_coverage/retrieval_significance.py`、`tools/dense_build/check_tflite_parity.py`、`RoomKnowledgeBaseStore`（12.6ms 接线）。

**依赖**：F4/F5/F6。

**验收门**：主集与逐章口径达标或用户正式改口径；19 例回归 19/19、地板 0.64/0.56 不破；JVM 镜像 vs 真 SQL 12 个质量数逐项一致。

**复算命令**：§7 的 R24–R25。

### 批次 6 · 覆盖与治理清账（依赖 F1/F2/F14；并行 2–3）

**范围**
- 教材授权落地（A/B/C 对应产物）；章节口径执行（③-6/③-8）；SmartEdu 目录取回或降级。
- 53 知识清单来源登记 + 包内示例域名 URI 修正（与写入窗口协调）。
- 转写 p41/p79 判读 + MAT H p22 记录；可选 483 页分层抽检。
- 机械 SKIP 分层抽检（4×30）报告。
- 5 条 high 的确认/排除；OpenStax/Siyavula 与九科课标审校按 F14/§5 决定是否开工。

**工具**：`tools/audit-*.ps1`（6 个）、`tools/curriculum_coverage/review.py` + `tools/curriculum_coverage/promotion.py`、`tools/audit-open-teaching-epubs.py`、`tools/kb_coverage/check_transcripts.py` + `tools/kb_coverage/transcription_ledger.py`、新写的一次性 SKIP 抽样脚本。

**依赖**：F1/F2/F14；部分需网络/实体书/人工。

**验收门**：③-1/③-6/③-8/⑤-1/⑤-2/⑤-4/⑤-7 各自的验收判据逐条落证；`tools/kb_build/gate.py` 与 `run_kb_checks` 保持绿。

**复算命令**：§7 的 R12、R26–R29。

---

## 5. 建议明确不做或推迟的

1. **182 条课标拆分提案（25 模块）——推迟，不排入近期批次**。理由：22/25 是生物选修与数学建模/校本课程，对高考主线提分几乎无感；要人逐条审 4–7 小时。若要做，先由 F14 决定它是否进分母，否则做了也不改对外口径。
2. **知识研究/审校离线管线（②-7，5–10 agent-days）——推迟**。理由：`KnowledgeResearchSearchGateway` 一个实现都没有；离线通道涉及外部来源与许可（F1 的 B 选项未裁）；无审校人。先做 F13 的本地解析闭环，把「研究管线」留给有审校资源之后。
3. **OpenStax / Siyavula 开放教材审校——明确不做（本轮）**。理由：原文禁入模型上下文（`docs/knowledge-source-governance.md:73`）；EPUB 不在盘（`tools/audit-open-teaching-epubs.py --check` exit 1）；对四科高考主线无产品收益。
4. **dense 大档重开——不做**。理由：6.90× > 3× 延迟硬线；口径已更正为「延迟不允许」；真机不可测时重开无法验收。
5. **关闭安全扫描的 `inconclusive`、深挖 CI instrumented job 红——暂缓**。理由：前者是 known host environment boundary（Semgrep/PyCG 不可用），后者未归因且不影响本报告结论；只保留「5 条 high 逐条确认/排除」这一可完成项。
6. **九科白名单收敛（KD-28）——维持现状**。理由：已判接受边界、四科范围下无可观测失败；只把 reopen 条件写进新增科目检查单。
7. **教材原文入库——不做（除用户选 F1-B）**。理由：官方条款明确禁止转载改编并点名 App；学生可见的目录对齐不需要原文。
8. **全量「知识库浏览 + 材料正文展示」——推迟**。理由：正文展示与 KD-29 标识耦合；先做 F12 的只读节点预览（不含材料正文），等 F8 落地后再放开正文。
9. **「全覆盖高考考点」话术——不做**。理由：已在 `docs/research/coverage-baseline-design-2026-09-15.md:11` 废弃，替换为「覆盖 2025 课标内容要求 X/Y 条」；只剩 19 条随包 locator 待清理（③-10）。
10. **CI 全量 artifact 依赖测试的「无条件绿」——不做**。理由：缺 artifact 必须显式 skip 并写理由，不能用「什么都跳过」换绿。

---

## 6. 与既有计划的接口（不重复计账）

### 6.1 3D / 裁决 27 的 D-0…D-3（本报告就是它的证据更新）

- **3D 定位**：新增阶段「坐标系可信」，**硬约束=3D 全部验收门必须在阶段 5（复习栏）开工前通过**；若阶段 5 到点而 3D 未收敛：**不得开工阶段 5**，挂起并写台账（`docs/REFACTOR-MASTER-LINE.md:46,66-68`；裁决 27，`docs/agent-first-refactor-decisions-2026-09-23.md:2290-2327`）。
- **D-0 绑定可信度字段（前置）**：为 `bindings[]` 增加裁定状态/来源/时间戳，使语义判定可落账、可回归。本报告的 ①-1/①-10/①-6 产出的判定结果应落进该字段；**字段实现属内核线，本报告不另派**。
- **D-0b 块池去向核实**：台账记已完成（118,272＝机械 SKIP 107,160＋分流前已判键 11,112）。**块池判定 232/232 已完成**（本会话实跑 `check_slice_verdicts`：已检 232 个判定文件 / 70,072 块 / 不过 0 个）；基线 §0 已作废「223/232、待判 9 片」的中间快照，`docs/REFACTOR-MASTER-LINE.md:46` 与台账旧行的「待判 9 片/2,675 块」不再作为待办引用。D-0b 的真实遗留只有一项：**机械 SKIP 57.4% 从未抽检**，由本报告 ⑤-4 承接。
- **D-1 语义绑定核验**：候选口径已从文档 325 订正为实测 496（§1.4 #1）；验收门 ≥0.95 与「标注者为模型、单一来源、无人类双复核」的如实标注义务按裁决 29 执行（`:2352,2357-2358`）。
- **D-2 结构完整性**：台账原文写「近重复 28 对 / 超配 2,189 / 726 条历史 type」（`:2308-2309,2320`）——三个数字均已被实测取代（18 对 / 2,754 / 667），**引用时以本报告 §1 为准**；本报告 ①-2/①-3/①-4/①-5 即 D-2 的施工清单。
- **D-3 检索质量**：前两步（5 红清零、dense 与包同源）已完成并复核；剩「实修召回」，保持 0.90 目标——本报告 ④-1/④-2 即该条；口径裁定走 F4。
- **D-5 写入窗口纪律**：单写者窗口；staging 起点指纹 → 建点/落料/别名 → 23 门全绿 → promote；**凡门不绿不许削门**。本报告所有涉及改包的批次（批次 2/3/6）必须遵守。

### 6.2 裁决 28（读侧语义闭合，已完成）

已于 2026-10-02 收口（提交 `c5829217`，`docs/research/2026-10-01-read-side-closure-record.md`）。它与本报告组②不重叠：裁决 28 修的是「读侧快照 vs 活判据」（strengths 误收、`ReviewPlanner` 双钟、KF-16 压制、`STALE` 死枚举），本报告组②修的是「KB→学生面的接线」（大厅读库、浏览、grounding、覆盖）。两者共享的是阶段 5 的出口门。

### 6.3 阶段 4A/5/6 里的知识库相关项

- **阶段 4A（错题本与录入，⬜）**：L1–L7 与 KB 的交集是材料绑定链（`tools/kb_build/tables/material_rebind.csv` / `rebind_materials`）；内核线 3B 的 **KF-32** 正在实现「改绑→BINDING_CHANGED 补偿事件→全量重放」（`docs/research/2026-10-02-stage3b-plan.md:17,109,128`）。本报告的 D-1 改绑产出（批次 2/3）**必须走该事件链**，否则重放账本与包不一致——这是两条线的显式接口，本报告不重复派 KF-32 的活。
- **阶段 5（复习栏，⬜）**：知识点复习卡片流 + M2 UI 侧接线 + 判题限定；**进入条件=3D 全部门通过**。本报告批次 2–5 是 3D 的实际内容；阶段 5 不能先于它们开工。
- **阶段 6（学习档案/设置/悬浮球，⬜）**：只读档案的掌握读数承重在 KB 坐标系上；依赖同 3D。视觉插眼 4 要「先问用户」（`docs/REFACTOR-MASTER-LINE.md:53`），与本报告无交集。
- **阶段 5 的「五科没有掌握」文案**：`docs/REFACTOR-MASTER-LINE.md:14-17` 记该文案尚未落地、登记为阶段 3D 的一项——本报告未见对应施工产物，归入 3D 的收尾清单（不另立条目）。

### 6.4 他会话在飞线（不重复计）

- **S19/S21（内核线 Wave 4 · W4-4）**：已收口——S19 判「不加索引」（覆盖索引已在用，显式索引实测慢 4.7×），S21 安装期预热 + 安装链四条批量删除分块（`docs/research/2026-10-02-wave4-completion-record.md:16,44-67`）。本报告**只保留其遗留项**：每轮两条 COUNT 的 ≈12.6ms（④-6，探针未入库、数字待复测）与真机长尾观察；**不重开「加索引」**。
- **候选区裁决线**：`tools/kb_build/merge_candidate_verdicts.py` 的 norm_key/duplicate_dropped/near_duplicate_review 改动**在当前未提交工作树**（本会话 `git diff --stat` 实读：+40 行；配套测试 +23 行），`knowledge-production/candidate-merge/near_duplicate_review.csv` 2 组随该线落地。本报告只在 ①-2/①-5/①-8 引用其结果（2 组 / 91 条 / plan 304+37+9），**不另派「候选区重裁」批次**；该线提交后需回填 §1.4 的相关口径。
- **内核线 3B（在飞）**：写作期间已从 `890d966f`（3B 实施计划）推进到 **`a96bc8ce`（3B B1——M5 死面删除，schema 57）**；本会话收尾复查 `git status --porcelain`：工作树只剩 **5 个已跟踪改动**——`docs/agent-first-refactor-decisions-2026-09-23.md`（+225 行，台账在飞批）、`docs/superpowers/specs/2026-09-25-agent-first-refactor-design.md`（+60）、`docs/superpowers/specs/2026-09-25-agent-first-refactor-phasing.md`（+11）、`tools/kb_build/merge_candidate_verdicts.py`（+40）与其测试（+23）（`git diff --stat` 实读）。⇒ 3B 的代码改动已提交，未提交面收敛为「台账/规格 + 候选区工具」两类。本报告涉及的改动**不得碰这些文件**，批次 2/3 的改绑落包需与该线约定写入窗口。
- **薄纵向切片（裁决 30）**：状态「并行推进」，驱动数据=用户提供的题目数据集；本报告 ④-7 只登记其输出（走查报告 + 样本条数清点）为 Wave X 的前置，不重复派工。

---

## 7. 复算命令

> 全部在仓库根 `D:\smart mistake book` 下、git bash 中运行（Windows；Python 3.13）。带 `PYTHONPATH=tools` 的必须照写（否则报 `ModuleNotFoundError: No module named 'kb_build'`）。
> 标记：**[实测]** = 本报告作者本会话跑过并给出输出；**[卡片]** = 六份事实卡在本会话工作流内跑过，本报告未复跑。

**R1 包身份** `[实测]`
```bash
python -c "import json,io;d=json.load(io.open('core/data/src/main/resources/knowledge/moe-2025-update-manifest.json',encoding='utf-8'));print(d['packId'],d['version'],d['contentVersion'])"
# 输出：moe-2025-four-subjects-v1 9 f1f6a470c840b365
```

**R2 点数 / topic / 别名** `[实测]`
```bash
python - <<'PY'
import json,io
p=json.load(io.open('core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json',encoding='utf-8'))
pts=topics=aliases=0
for s in p['subjects']:
    for t in s['topics']:
        topics+=1
        for kp in t['knowledgePoints']:
            pts+=1; aliases+=len(kp.get('aliases') or [])
print('topics',topics,'points',pts,'aliases',aliases)
PY
# 输出：topics 398 points 3866 aliases 37327
```

**R3 材料 / type / 时间戳** `[实测]`
```bash
python - <<'PY'
import json,io,os
from collections import Counter
base='core/data/src/main/resources/knowledge/'
idx=json.load(io.open(base+'moe-2025-teaching-support-v2-index.json',encoding='utf-8'))
tot=0; tc=Counter(); ts=set()
for path in idx['sidecars']:
    d=json.load(io.open(base+os.path.basename(path),encoding='utf-8'))
    for m in d.get('materials') or []:
        tot+=1; tc[m.get('type')]+=1; ts.add(m.get('reviewedAtEpochMillis'))
print('sidecars',len(idx['sidecars']),'materials',tot)
print(dict(tc)); print('outside3',sum(v for k,v in tc.items() if k not in ('CONCEPT_EXPLANATION','METHOD_MODEL','MISCONCEPTION_GUIDE')))
print('distinct ts',len(ts))
PY
# 输出：sidecars 19 materials 50383；三值外 667；distinct ts 42
```

**R4 超配节点** `[实测]`
```bash
python - <<'PY'
import json,io,os
from collections import Counter
base='core/data/src/main/resources/knowledge/'
idx=json.load(io.open(base+'moe-2025-teaching-support-v2-index.json',encoding='utf-8'))
cnt=Counter()
for path in idx['sidecars']:
    d=json.load(io.open(base+os.path.basename(path),encoding='utf-8'))
    for m in d.get('materials') or []:
        nid=(m.get('bindings') or [{}])[0].get('knowledgeNodeId') or ''
        parts=nid.split(':')
        cnt[(parts[2] if len(parts)>=5 else '?', parts[-1])]+=1
gt4=[k for k,v in cnt.items() if v>4]; ge4=[k for k,v in cnt.items() if v>=4]
print('nodes',len(cnt),'>=4',len(ge4),'>4',len(gt4),'==4',len([k for k,v in cnt.items() if v==4]))
print('held',sum(cnt[k] for k in gt4),'excess',sum(cnt[k]-4 for k in gt4))
print('max',max(cnt.items(), key=lambda x:x[1]))
PY
# 输出：nodes 3866 >=4 2754 >4 2518 ==4 236；held 47538 excess 37466；max (('biology','基因工程'),139)
```

**R5 dense 资产与同源** `[实测]`
```bash
python - <<'PY'
import json,io,hashlib
d=json.load(io.open('core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec.json',encoding='utf-8'))
print('vectorCount',d['corpus']['vectorCount'],'atomicNodes',d['corpus']['atomicNodes'],'aliasVectors',d['corpus']['aliasVectors'])
print('vecJson packSha256',d['packSha256'])
h=hashlib.sha256(open('core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json','rb').read()).hexdigest()
print('main pack sha256',h,'match',h==d['packSha256'])
print('vec sha256',d['sha256'])
PY
# 输出：41193 / 3866 / 37327；两个 sha256 相等（647675cd…）；.vec sha 8649afa6…
```

**R6 课标覆盖聚合** `[实测]`
```bash
python - <<'PY'
import json,io
from collections import Counter
d=json.load(io.open('knowledge-production/kb-coverage-alignment-2026-v2.json',encoding='utf-8'))
tot=Counter()
for s in d['subjects']:
    c=Counter(x.get('status') for x in s['statements']); tot.update(c)
    print(s['subject'], dict(c), 'n=',len(s['statements']), round(c['COVERED']/len(s['statements']),4))
print('ALL', dict(tot), sum(tot.values()))
PY
# 输出：MATH 130/308 0.4221；PHYSICS 71/138 0.5145；CHEMISTRY 51/73 0.6986；BIOLOGY 100/142 0.7042；ALL 352/73/236 共 661
```

**R7 关键表行数** `[实测]`
```bash
python - <<'PY'
import csv
for f in ('tools/kb_build/tables/boundary_map.csv','tools/kb_build/tables/material_bindings.csv',
          'knowledge-production/candidate-merge/capacity_blocked.csv',
          'knowledge-production/node-material-gaps-2026-09.csv',
          'tools/kb_coverage/tables/mechanical_skips.csv',
          'tools/kb_coverage/tables/rejudge_queue.csv',
          'knowledge-production/candidate-merge/near_duplicate_review.csv',
          'build/agent-input/rebind_conflicts_verdict.csv'):
    print(sum(1 for _ in csv.reader(open(f,encoding='utf-8')))-1, f)
PY
# 输出：20 / 598 / 91 / 661 / 107160 / 698 / 2 / 102
```

**R8 判定表统计** `[实测]`
```bash
python - <<'PY'
import csv
from collections import Counter
rows=list(csv.DictReader(open('tools/kb_coverage/tables/material_judgments.csv',encoding='utf-8')))
print('rows',len(rows),Counter(r['action'] for r in rows))
m=[r for r in rows if r['action']=='MATERIAL']
print('MATERIAL',len(m),Counter(r['type'] for r in m))
old=[r for r in rows if r['type'] not in ('CONCEPT_EXPLANATION','METHOD_MODEL','MISCONCEPTION_GUIDE','')]
print('non-3-value',len(old),Counter(r['action'] for r in old))
PY
# 输出：86414 {'SKIP': 47485, 'MATERIAL': 38929}；MATERIAL 38929 全 3 值；non-3-value 482 全 SKIP
```

**R9 拆分提案与裁定状态** `[实测]`
```bash
python - <<'PY'
import json,io
p=json.load(io.open('knowledge-production/curriculum-warning-decomposition-proposals-2025-v1.json',encoding='utf-8'))
n=0; total=0
for s in p['subjects']:
    for m in s['modules']:
        n+=1; total+=len(m.get('candidates',[]))
        print(s['subject'], m.get('slug'), len(m.get('candidates',[])))
print('modules',n,'candidates',total)
h=json.load(io.open('knowledge-production/curriculum-warning-human-review-decisions-2025-v1.json',encoding='utf-8'))
print(h.get('reviewState'), len(h.get('decisions') or []))
PY
# 输出：25 modules / 182 candidates；NOT_STARTED 0
```

**R10 D-1 候选与别名嫌疑** `[实测]`
```bash
PYTHONPATH=tools python -m kb_build.audit_content_bindings       # 靶区 161 / 候选 496
PYTHONPATH=tools python -m kb_build.audit_bindings_by_alias      # 错绑嫌疑 75 条（豁免 21 条）
PYTHONPATH=tools python -m kb_build.merge_content_audit_verdicts --check   # exit 0：「复算一致：409 行」
PYTHONPATH=tools python -m kb_build.rebind_materials             # 报告模式：改绑 0；跳过 1246；悬空 0
PYTHONPATH=tools python - <<'PY'
# 496 候选 vs 既有 409 行裁定的重合（①-1 的「23 条」）
import csv
from kb_build import audit_content_bindings as A
facts=A.load_facts(); rows=A.verdict_rows(facts, A.default_scope(facts))
old=list(csv.DictReader(open('tools/kb_build/tables/content_audit_2026-09-25.csv',encoding='utf-8')))
cm={r['material_slug'] for r in rows}; om={r['material_slug'] for r in old}
print('material_slug ∩',len(cm&om))
print('material+current_node ∩',len({(r['material_slug'],r['current_node_slug']) for r in rows}&{(r['material_slug'],r['current_node_slug']) for r in old}))
print('subject+current_node ∩',len({(r['subject'],r['slug']) for r in rows}&{(r['subject'],r['current_node_slug']) for r in old}))
PY
# 输出：material_slug ∩ 23；material+current_node ∩ 20；subject+current_node ∩ 19
```

**R11 53 知识清单材料数** `[实测]`
```bash
python - <<'PY'
import json,io,os
base='core/data/src/main/resources/knowledge/'
idx=json.load(io.open(base+'moe-2025-teaching-support-v2-index.json',encoding='utf-8'))
tot=c=0
for path in idx['sidecars']:
    d=json.load(io.open(base+os.path.basename(path),encoding='utf-8'))
    for m in d.get('materials') or []:
        tot+=1; c+= '53知识清单' in (m.get('sourceLocator') or '')
print(tot,c)
PY
# 输出：50383 5053
```

**R12 转写门与账本** `[实测]`
```bash
PYTHONPATH=tools python -m kb_coverage.check_transcripts
# 输出：页数 1205｜已转写 1205｜缺失 0 / 闸门 {'pass': 1205} / 全部通过；exit 0
```

**R13 两道主门** `[实测]`
```bash
PYTHONPATH=tools python tools/kb_build/gate.py        # 23 项全 0
python tools/ci/run_kb_checks.py                      # gates/consistency/roundtrip/manifest/dense 5 节 OK
python tools/kb_build/gate.py                         # 反例：ModuleNotFoundError（不要这样写）
```

**R14 判定切片自检** `[实测]`
```bash
PYTHONPATH=tools python -m kb_coverage.check_slice_verdicts
# 输出：已检 232 个判定文件 / 70072 块：MATERIAL 23292、SKIP 46780、NEW 提案 218 / 不过 0 个
```

**R15 Python 全套测试** `[实测]`
```bash
PYTHONPATH=tools python -m unittest discover -s tools/tests -t tools
# 本会话输出：Ran 586 tests in 128.638s / OK；退出码 0
```

**R16 学生侧 JVM 用例** `[卡片]`
```bash
./gradlew :core:data:testDebugUnitTest --tests "com.tingyun.smartmistakebook.core.data.study.RoomTutorToolRunnerTest" --console=plain
./gradlew :core:model:test --tests "com.tingyun.smartmistakebook.core.model.TutorToolTraceTest" --console=plain
# 卡片输出：42/0 与 12/0
```

**R17 CI 现状** `[实测]`
```bash
gh run list --limit 6
gh run view --job 110707577008 --log | grep "FAILED (failures="
# 输出：最新 main push failure；FAILED (failures=6, errors=7, skipped=1)
```

**R18 超配工具（修复前为反例）** `[实测]`
```bash
PYTHONPATH=tools python -m kb_build.overfull_nodes     # 现状：KeyError: 'materials'（tools/kb_build/overfull_nodes.py:75）
```

**R19 表↔包对账** `[实测]`
```bash
PYTHONPATH=tools python -m kb_build.check_pack_contract   # 现只对账 alias/chapter/chapter_by_node/prereq + 点表
```

**R20 status 页 sha 对照** `[实测]`
```bash
python - <<'PY'
import hashlib
for f in ('chapter_map','chapter_by_source','alias_map','boundary_map','prereq_map','material_bindings'):
    p=f'tools/kb_build/tables/{f}.csv'
    print(f, hashlib.sha256(open(p,'rb').read()).hexdigest()[:12])
PY
# 与 docs/status.md:92 展示的 6 个 sha 比对；当前 chapter_by_source 与 alias_map 不一致
```

**R21 D-1 裁定表复算** `[实测]`
```bash
PYTHONPATH=tools python -m kb_build.merge_content_audit_verdicts --check
# 输出：复算一致：409 行；REBIND 引文对不上材料正文的行：1 / 56
```

**R22 材料缺口重算（live 571）** `[卡片]`
```bash
PYTHONPATH=tools python -m kb_build.report_material_gaps
# 输出：缺口共 571 个：零材料 0、仅 1 条材料 571
```

**R23 仪器化三门（本机可跑）** `[卡片]`
```bash
./gradlew.bat :core:data:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.data.knowledge.dense.DenseEncoderParityInstrumentedTest,com.tingyun.smartmistakebook.core.data.knowledge.GoldenRetrievalInstrumentedTest,com.tingyun.smartmistakebook.core.data.knowledge.dense.DenseFirstUseCostInstrumentedTest"
# 卡片输出：BUILD SUCCESSFUL in 4m48s；XML tests=3 failures=0
```

**R24 检索离线三数（设备期望值）** `[实测，读文件]`
```bash
python -c "import json;d=json.load(open('build/stage3-device-expectation.json',encoding='utf-8'));print({k:d[k] for k in ('refFusedD1','refDenseD1','refLexicalOnlyD1')})"
# refFusedD1.main 0.7307692307692307（95/130）；refDenseD1 0.6923；refLexicalOnlyD1 0.6308
```

**R25 显著性（v1/90 口径 + v2 按章 CI）** `[实测]`
```bash
python tools/kb_coverage/retrieval_significance.py --golden tools/kb_coverage/tables/golden_queries_v1.json --misses build/stage7/golden-fused-misses-device-2026-09-28.txt
# 输出：n=90/10 章，recall 0.7444（67/90），按章 cluster CI [0.6111, 0.8667]
python - <<'PY'
import json, random
from collections import defaultdict
d=json.load(open('build/stage3-device-expectation.json',encoding='utf-8'))
by=defaultdict(lambda:[0,0])
for c in d['perCase']['fused']:
    by[c['chapter']][1]+=1
    if c['rank'] and 1<=c['rank']<=5: by[c['chapter']][0]+=1
chs=sorted(by); rng=random.Random(0); vals=[]
for _ in range(20000):
    h=t=0
    for _ in range(len(chs)):
        c=rng.choice(chs); h+=by[c][0]; t+=by[c][1]
    vals.append(h/t)
vals.sort()
print('point', sum(by[c][0] for c in chs)/sum(by[c][1] for c in chs), 'CI', vals[499], vals[19499])
PY
# 输出：point 0.7307692307692307；CI [0.6346, 0.8288]（0.90 在区间外）
# v2 设备 MISS 账本拉回（可选）：adb pull /sdcard/Download/golden-fused-misses-1790919837814.txt build/golden-fused-misses.txt
```

**R26 dense 宿主对拍** `[卡片]`
```bash
build/tflite-venv/Scripts/python.exe tools/dense_build/check_tflite_parity.py --max-len 128
# 卡片输出：exit 0；ALL n=330 min=0.999554 median=0.999790 p95=0.999849（门 0.999）
```

**R27 来源登记审计（注意 PowerShell 编码）** `[卡片]`
```bash
pwsh -NoProfile -File tools/audit-knowledge-source-register.ps1 -ProjectRoot "D:\smart mistake book"                     # exit 0
pwsh -NoProfile -File tools/audit-knowledge-source-register.ps1 -ProjectRoot "D:\smart mistake book" -RequireProductionReady  # exit 1（九科 current-text 缺）
powershell -NoProfile -ExecutionPolicy Bypass -File tools/audit-knowledge-source-register.ps1 -ProjectRoot "D:\smart mistake book"  # 5.1 下 exit 1（ConvertFrom-Json 编码）
python tools/audit-open-teaching-epubs.py --check                                                                       # exit 1（缺 EPUB）
```

**R28 材料级检查（当前红）** `[实测]`
```bash
PYTHONPATH=tools python -m kb_coverage.check_exam_leak
# 输出：MATERIAL 行 38929；例题派生命中 206（164 节点）：119 答案语 / 85 短题干 / 1 题号标题 / 1 成套选项；exit 1
```

**R29 安全扫描封存件** `[卡片]`
```bash
python - <<'PY'
import json,os
b=os.path.expanduser('~/.mimosa/security-scans/project-c079ff08106a86cc56edfee2/scan-2026-09-30T13-26-54.425Z-13f05c58edc9')
cov=json.load(open(os.path.join(b,'coverage.json'),encoding='utf-8'))
print(cov['runStatus'], cov['completeness'], cov['phases']['threatModel']['status'], cov['phases']['pathAnalysis']['status'])
print(json.load(open(os.path.join(b,'findings.json'),encoding='utf-8'))['totals'])
PY
# 输出：inconclusive partial partial not_applicable；{'high':5,'medium':0,'low':7,...}
```

---

### 附：本报告里标 UNVERIFIED / 未复跑的项（不藏）

1. 「17 unmatched」（`build/agent-outstanding/group-1.md` 的不可复现项）：现有产物找不到来源，**UNVERIFIED**。
2. 材料时效 12.6ms：代码结构已核实（每次召回两条 COUNT），**数字本身未复跑**（探针脚本未入库）；④-6。
3. n=330 fixture 下的 dense 宿主/真机对拍读数：本会话未跑（需 tflite venv），**UNVERIFIED**；卡片给了 n=330 的宿主对拍 exit 0 与设备读数。
4. 真机（物理设备）延迟、端侧线程 {2,4,8}/窗口 {512,128} 真机差值：无物理设备，**未测**。
5. v2 设备 MISS 账本未 `adb pull` 回落盘（数据以 `build/stage3-device-expectation.json` 的 perCase 为准）；④-3 的 v2 CI 本会话已用该文件 perCase 逐章命中数复算 → [0.6346, 0.8288]（见 R25 第二段）。
6. 教材元数据目录（3,229 条、指纹 9239…）在盘不可复现：**UNVERIFIED**；③-5。
7. 「审计代理是否真的逐页看过页图」：扫描报告自述没有独立验证，本报告也未验证；⑤-1。
8. 「真实模型在大厅是否真的调用 KNOWLEDGE_READ 浪费一轮」：代码可达性推断，非实测计数；②-1。
9. 「换一条通顺废话材料，现有流程能否报出」：未构造样本、未跑；⑥-7 的验收判据之一。
10. 重判队列 698 行 vs 包内 667 条的差额 31 条去向：未核（可读 `tools/kb_coverage/apply_rejudged_materials.py` 的 `--dry-run` 报告）；①-4。
11. 「实修召回是否有在飞实现」：全仓未见施工产物/计划更新，按现状判断为「未开工」——观察结论，非实测；④-2。
12. CI instrumented job 失败原因：未深挖其日志；⑤-6。

---

## 分叉裁决记录（2026-10-03/04，按"只管 KB 后端"的授权推进）

- **F6 FTS5：裁决「不换」，本分叉关闭。**
  依据：v10 包 + 金标 v2（129 题）口径下重出数——FTS5 臂主集 Recall@5 **0.6512 (84/129)** vs 词面腿生产形 **0.6357 (82/129)**，增益 **+1.5pp**；
  而换引擎的代价侧未变：施工方案自列 7 项未证实、DB 版本已从计划的 51/52 漂到 **61**、图书馆检索仍用 FTS4（`library_search_fts`，迁移面不可回归）。
  结论：收益不足以抵消迁移与回归面。**重开条件**：① 词面腿成为瓶颈且 D12 目标进入攻坚（F4 ① 的实验序列走到"改排序"仍不够）；② 或 LibrarySearchFts 因别的原因升到 FTS5（迁移面成本被别的需求摊掉）。
- **F3 超配/容量拦截、F7 旧 type、F9 bindings 欠账、F10 语义门**：按报告推荐执行（F10 取 ②：定期抽检+报告，先清榜再议硬门）。
- **F1 教材授权**：取最保守的 A（原文不入库、只作核对参照）。
- **F11/F12/F13/F8**：不在本线范围（学生侧/产品面），留档待认领。
