# KF-30 三条残余语义 · 裁定记录（2026-10-09）

> 背景：`docs/research/2026-10-04-stage3c-part1-completion-record.md:37`（3C 前半 §4-2）登记的 KF-30 三条残余语义，
> 原文结论「正解 = 未来 schema 增加 accepted-confidence 列，不在本批」。2026-10-09 先由 K2 侦察摸清落点与改动面
> （`docs/research/2026-10-09-k2-plan.md` 的选批依据），同日就三条语义与用户逐条拷问（grilling 轮）取得裁定。
>
> **状态：已裁、未实施。** 落地批次另排；实施前读本记录（台账纪律：待裁值不得自行取值）。
> 台账折入待办（`docs/agent-first-refactor-decisions-2026-09-23.md` 为另一条会话在飞文件，暂以本记录承载）。

## 1. 事实基线（已核 file:line）

- 落点全在 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeOrganizationRepository.kt`：
  常量 `:95`（`CLASSIFICATION_ACCEPTANCE_CONFIDENCE = 0.78`；用户保护 1.0 / 原子 0.72 / 关系 0.90；
  预算 章 3 / 知识点 8 / 关系 4）、自动接受门 `acceptOrganizationLocally:999-1047`（置信过滤 `:1023`、
  预算 `:1030-1036`）、合并 `mergeAutomaticClassifications:922-971`、写入单点
  `buildConfirmationCommand:1223-1236`（带 `acceptanceSource`、**无 confidence**）。
  **注意别找错文件**：`StudySchedulingCalibration.kt` 是排程/FSRS 校准，与 KF-30 无关。
- **替换判据本身是「严格更高者胜、等值保留存量」**：`(存量 + 本轮).sortedByDescending(confidence).distinctBy(维度+折叠小写名)`（`:956-959`，KDoc `:906-914`）；同名身份 = 维度 + 折叠小写名（`:979-984`）。
- **(a)(b) 的共同根因 = 存量行置信度被伪造**：自动接受过的存量行一律估成 0.78（`:943-944`）——
  于是恰好 0.78 的新提议"同值永不覆盖"（(a)），而 0.79 的新提议可顶掉曾以 0.99 接受的老标签（(b)）。
- **(c)**：预算争用才挤出、未满则全留（`withinClassificationBudget:987-997`，KDoc `:903`「omission is never a
  deletion signal」）。
- 应用**从未发布** ⇒ 现实"存量行"只存在于开发/测试库；回填口径的现实代价≈0，但规则必须一次写对。

## 2. 裁定（用户 2026-10-09）

| # | 语义 | 裁定 |
|---|---|---|
| Q1 | 同名 auto 标签的替换判据（(a)+(b) 合流） | **维持「严格更高者胜、等值保留存量」**，判据一行不改；把**真实接受置信度落库**即消解 (a)(b) |
| Q2 | 升级前已接受行的回填口径 | 写 **`NULL`（未知）**，参与比较时**按门值**——任何**严格更高且 ≥门值**的新提议都能替换/刷新它。**不冻结**（用户否掉"只能用户手改"：会成一潭死水；并明确「禁止把这种问题交给用户」）。升级时刻行为与今天一致；此后每轮自动整理重提同名标签即用**真实分数刷新**（系统自纠）。**本项为 interim 口径，正解挂 R2** |
| Q3 | 预算未满时不同名 auto 标签的留存（(c)） | **维持 add-only**（不改取向）；正解挂 R1 |

用户路径保护语义（1.0、不参与预算竞争、生产不可达的纵深防御分支）与 (c) 的既有实现**都不动**。

## 3. 两个研究项（用户要求：都要深度研究，**且必须结合外部高质资料**）

- **R1 · 标签准确性自证/自纠**：系统自己发现并清掉"模型看走眼"造成的错标签——不靠 add-only 永久留存、
  不靠用户手改（用户原话：「不要把这种问题交给用户判断，他自己应该准确」）。
- **R2 · 未知历史置信度的正确处置**：在"当年过了多少分不可知"的前提下，怎么做出正确的替换/刷新判定。
  候选方向：重新评定、用证据重构置信度、置信度校准等（**未验证，待研究**）。Q2 的 NULL 口径只是 interim。
- **研究方法约束（用户原话：「我说的深度研究都需要结合外部高质资料」）**：按 AGENTS §11 / `ascetic-breaker`——
  项目内既有实现 → GitHub 高质相似项目 → 官方文档 → 社区经验；交叉验证（多来源、真独立；单一来源标"待验证"，
  不得当事实）。研究产出必须是可落地的方案 + 证据等级标注。

## 4. 落地口径（KF-30 实施批，**尚未开工**）

1. **加列**：`problem_classification_binding` 增 `accepted_confidence`（nullable；`ProblemEntities.kt:570` 现 9 列）
   + 记录（`StudyDatabaseRecords.kt:469`）+ 两个映射（`RoomProblemOrganizationStore.kt:515/:541`）。
2. **迁移**：`STUDY_DATABASE_VERSION` **63 → 64**（非破坏；导出 `64.json`；定串/契约测试同步——
   含 `KernelWave0SchemaContractTest` 的版本字面量与迁移矩阵自动覆盖）。
3. **写侧**：自动路径写真实分数（`:1023` 处已有）；用户/离线路径无模型分数 → 写 **NULL**，权威性由
   `acceptanceSource` 表达（保持既有保护语义）。
4. **读/合并侧**：`mergeAutomaticClassifications` 读真实值参与排序；NULL 按门值参与比较（Q2 interim）。
   `sameAcceptedFact:476-478` 与命令指纹 `:1327-1340` 是否纳入该值——实施期定并写进记录
   （判据：重复确认的幂等语义不得被无意改变）。
5. **版本口径**：**默认零算法版本 bump**（判据 = 既有账本重放是否逐位不变；升级时刻分类集合不变 ⇒ 预期不变）；
   实施期实测若有变化，按台账纪律 bump `PROJECTOR`/`ATTRIBUTION` 并报告（K1 先例：v12→v13 / v4→v5）。
6. **风险**：分类集合变化会影响绑定 → 可能触发 `BINDING_CHANGED` → 全量重放；升级演练已由 K2 覆盖
   （`docs/research/2026-10-09-k2-plan.md` 批 1）。

## 5. 顺序建议（已与用户确认）

先落「列 + 合并读真值」（按上述保守口径，本身即比今天诚实，且是 R1/R2 的前置——没有真实置信度，
自纠机制没有信号可用）；**R1+R2 作为一个"结合外部高质资料"的研究批另排**（精化，不阻塞列的落地）。
