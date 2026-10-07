# 3D · D-0「绑定可信度字段」内核侧实施计划（2026-10-07）

> 依据：台账裁决 27（`docs/agent-first-refactor-decisions-2026-09-23.md:2315-2318`：「为 `bindings[]` 增加
> **裁定状态 + 来源 + 时间戳**，使语义判定可落账、可回归。消灭：判定只能停在一次性 CSV 里」）；
> KB 未竟报告 §6.1 `:823`（「**字段实现属内核线**，本报告不另派」）与批次 2 `:727`（KB 先产 CSV 承接）；
> 阶段 5 门核验 `docs/research/2026-10-04-stage5-gate-check.md:12`（D-0 ❌ 未实现 = 3D 硬门第一阻塞点）。
> **关键路径**：3D 全部验收门是阶段 5 的硬门，D-0 是 3D 第一项且明确归内核线。

## 1. 关键事实基线（已核 file:line）

- **codec**（`core/data/.../knowledge/ReviewedTeachingMaterialSidecarJsonCodec.kt`）：`CURRENT_SCHEMA_VERSION=2`（`:43`），
  只收 {1,2}（`:55-57`）；binding 解析 `requireOnlyKeys("knowledgeNodeId","role")`（`:142`）——**精确键集**；
  JSON 严格（`ignoreUnknownKeys=false`）。**版本分支与可选键都有现成先例**：`toSource` 按 schemaVersion 分支
  （`:152-175`）、包解析里 `parentSlug` 的手工可选键（`BundledKnowledgePackResources.kt:326-334`）。
- **落库面**：绑定表 `knowledge_teaching_material_node_binding` 仅 `material_id, knowledge_node_id, role` 三列
  （schema 63）；调和器 `replaceMaterialBindings` 只搬运这三列 → 字段只加在 record 上则**落库即被丢弃**；
  要落库必须 schema 64 + 迁移。**当前无任何消费方**（app/feature 零命中；core 只用 materialId/knowledgeNodeId/role）。
- **写侧（KB 工具，他线）**：`materialize.py` 两处硬编码 `schemaVersion: 2`（`:173,:203`）；至少 6 处手工构造
  binding dict；**Python 23 门/契约/roundtrip 都不校验 binding 键集**（唯一键集门在 Kotlin codec）→
  「包 promote 绿、App 装不上」的口径缝隙。
- **裁定链现状**：判定全部停在 CSV（`content_audit_*.csv` 8 列、`material_rebind.csv`、
  `binding_suspects_reviewed.csv`、`binding_verdicts.csv` 等）；CSV→字段的映射契约不存在于任何文档。

## 2. 设计（每项都指认它消灭的失败）

1. **侧车 schemaVersion 2 → 3**（accepted {1,2,3}）：v1/v2 语义与严格性**逐字不变**（binding 仍恰好 2 键）；
   **v3 下 binding 可带可选三元组** `verdict` / `verdictSource` / `judgedAtEpochMillis`——**all-or-none**，
   枚举校验、时间戳 ≥0；未知键在 v3 仍拒。消灭：判定无处落账。
2. **词表**（编译期枚举 + 契约文档钉死）：`verdict ∈ {KEEP, REBIND, NONE}`；
   `verdictSource ∈ {MODEL_AUDIT, USER_DECISION, MIGRATION}`；`judgedAtEpochMillis` 用 epoch millis
   （与既有 `reviewedAtEpochMillis` 同口径）。扩词表需再 bump schemaVersion（写进契约）。
3. **领域记录**：`KnowledgeTeachingMaterialNodeBindingRecord` 加 3 个**带默认值**的可空字段（既有 ~12 处构造点不破）；
   `KnowledgeTeachingMaterialContract` 加三元组一致性校验。
4. **只落侧车、不落库（零 schema）**：D-0 目的是包级「落账 + 可回归」；当前无消费方，落库是**无失败可消灭的新增**
   （AGENTS §12.2）→ 不加 schema 64，「DB 落库（当出现消费方时）」登记为后续扩展。
5. **不改真实包、不碰 KB 工具**：真实包字段填充与 Python 键集门属 KB 线（写入窗口 D-5）；本轮交付**写侧契约文档**交接。

## 3. 批设计与验收

### 批 1 · codec v3 + 领域记录 + 严格性测试（零 schema）
- 实现上述 1–3；测试：① v3 夹具（已审计 + 未审计绑定）解析字段值正确 ② **v2 + 新键仍拒**（严格性不破）
  ③ v3 + 未知键拒 ④ 三元组 all-or-none 违规拒（缺一/缺二）⑤ 枚举越界/时间戳负值拒
  ⑥ 既有严格性测试（`ReviewedKnowledgePackJsonCodecTest`）保持绿。
- 门：全量 JVM + `core:data` 定向仪器化（包解析类）。

### 批 2 · 端到端 drill + 写侧契约文档 + 登记
- **drill（仪器化）**：合成 v3 包形状（真实包结构 + 带裁定三元组的绑定）走**真实 installer/reconciler 路径**，
  断言：解析→包校验→调和全绿、字段在包级可见、**DB 端如实不含**（把「落库即丢弃」写成断言而非事故）。
- **契约文档** `docs/research/2026-10-07-d0-binding-verdict-contract.md`：键名/类型/词表/all-or-none/schemaVersion=3、
  **CSV→字段映射**（`content_audit_*.csv` 的 verdict→`verdict`、来源→`MODEL_AUDIT`、审计运行时间→时间戳；
  `material_rebind.csv` → REBIND）、两条交接：**KB 写侧需在 promote 门加 binding 键集守卫**、真实包落账走 D-5 窗口。
- 登记：DB 落库（待消费方）、Python 键集守卫（KB 线）、字段真实填充（KB 写入窗口）。
- 门：全量 JVM + DB 定向仪器化（drill 类）。

### 批 3 · 综合门 + 收尾
- 全量 JVM + DB 全套 + app 全套（**DB 与 app 不得背靠背，先设备清场**）+ R8 冒烟；
  完成记录 + 主线 **3D 行**更新（D-0 内核侧已落 / 包内落账待 KB）+ 阶段 5 门核验文档加一行更新 + 台账折入待办。

## 4. 版本与迁移预算
- **零 schema**（侧车 schemaVersion 3 是**内容格式版本**，非 DB 版本）；`LearningCoreVersions` 不动。

## 5. 纪律与门
- 每批「实施 → 正式门 → 独立只读复核 → 修复轮 → 显式清单提交」；门显式分支 exit code；世界读取"重试→降级"；
  不碰他线在飞文件（`tools/kb_build/*` 等）。
- 执行方式：动态工作流（每相位直挂 Agent/脚本）。

## 6. 风险与 UNVERIFIED
- **跨版本语义**：v3 包在旧 codec（只收 1/2）上会整包拒——App 未发布故无现实风险，但契约必须写明
  "codec 是唯一键集门"并交接 Python 守卫。
- **真实包落账后置**：D-0 的 3D 验收门要等 KB 写入窗口把裁定写进包（届时按契约 v3）；本轮只到
  "载体 + 端到端 drill + 契约"。
- 合成 v3 夹具不得混入真实包目录（资源面只读）；drill 用测试内构造的 sidecar 文本。
- 词表若与 KB 侧 CSV 口径不符（如 `NONE` 语义），以契约为准并在批 2 核对一次现有 CSV 的取值分布。
