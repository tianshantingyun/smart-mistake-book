# 未收口门与阻塞登记（批次 49 修复环）

## 1. 12 模块单测门：仍 9 红（exit 1）

命令（与 `unit-gate.md` §1 同口径，12 模块；本机实跑）：

```bash
./gradlew :core:model:testDebugUnitTest :core:domain:testDebugUnitTest \
  :core:database:testDebugUnitTest :core:data:testDebugUnitTest \
  :core:export:testDebugUnitTest :core:ui:testDebugUnitTest \
  :core:markdown:testDebugUnitTest :feature:capture:testDebugUnitTest \
  :feature:review:testDebugUnitTest :feature:tutor:testDebugUnitTest \
  :feature:library:testDebugUnitTest :feature:profile:testDebugUnitTest \
  --continue --console=plain
# BUILD FAILED in 1m 48s; GRADLE_EXIT=1
```

原始日志：`repair/unit-gate-after.log`（314 行）。

| 模块 | tests | fail | err | skip |
|---|---:|---:|---:|---:|
| `core:model` | 655 | 0 | 0 | 0 |
| `core:domain` | 741 | 0 | 0 | 1 |
| `core:database` | 248 | **4** | 0 | 0 |
| `core:data` | 1688 | **4** | 0 | 1 |
| `core:export` | 174 | 0 | 0 | 0 |
| `core:ui` | 56 | 0 | 0 | 0 |
| `core:markdown` | 0 | 0 | 0 | 0 |
| `feature:capture` | 148 | 0 | 0 | 0 |
| `feature:review` | **106** | **0** | 0 | 0 |
| `feature:tutor` | 261 | **1** | 0 | 0 |
| `feature:library` | 82 | 0 | 0 | 0 |
| `feature:profile` | 6 | 0 | 0 | 0 |
| **合计** | **4165** | **9** | 0 | **2** |

9 红逐名（与 `unit-gate.md` §3 同一组，未新增）：

- `feature:tutor`：`TutorExportSheetTest.theWordFormatNoticeStatesItsRealLimitsAndPdfHasNone`
- `core:database`：`KernelWave0SchemaContractTest.the study database version matches the latest exported schema`、`LibraryFtsCountPathQueryCopyContractTest.crossJoinSqlCopiesMatchTheDaoQueries`、`LibraryFtsCountPathQueryCopyContractTest.scale50kCrossJoinSqlCopiesMatchTheDaoQueries`、`LibraryFtsRankingQueryContractTest.rankingAndFilterLiteralsMatchTheFrozenReferenceCopy`
- `core:data`：`OpenAiCompatibleModelGatewayTest` 3 例、`RoomBackedStudyExperienceRepositoryTest.profileStrengthsDropForgottenAndPrerequisiteSuppressedNodes`

**为什么本环不修**：9 红全部落在 `core:database` / `core:data` / `feature:tutor` 的 HEAD 既有语义
（schema 73→75、FTS 冻结副本、模型能力核验、知识先修映射），与本批热区
（`feature/review`+`app`）无关；修它们要改他线的断言或实现（`unit-gate.md` §4 已逐条归因）。
按“若他线在飞则停项/出补丁并登记，不整文件抢占”，本环只登记阻塞，不代改。
**本环自身面全绿**：`feature:review` 106/0（含新增 5 个校准决策用例）、`core:domain` 741/0
（含 P1-10 模拟上线门）。

## 2. acceptance 终裁门：exit 1（22 FAIL / 278 BLOCKED）

见 `repair/acceptance-gate-explanation.md`。900 个 PASS 行全部有有效复核（invalid=0，
violations=0）；exit 1 来自真实 FAIL/BLOCKED，不是证据链违规。22 FAIL 的归属：
F-057（本批回退）、H-018/H-062（AIGC 合规他线）、13 条 J 发布工程（共享脏树/无发布钥匙）、
7 条 GATE（终裁门自身/AUD-08）。278 BLOCKED 的出口已由 blocked-runbook 覆盖，条件未取得
前不改 PASS。

## 3. KD-67 真 Room 端到端：仍未跑（阻塞）

本轮已补：**决策消费**（Root 的 `selectKnowledgeReviewQuestion(CALIBRATION)` 返回值不再丢弃，
由会话状态机持久化并显示；JVM 5 例 + 设备 2 例钉住）与设备 UI 9/9。

仍缺：**真实 selector → presenter → 状态流 + 真 Room 确认/升级的端到端**。设备用例从
`KnowledgeReviewSessionScreen` 公开入口注入 `clarificationState`；要走真实链路需要：

1. 真 Room 库中预置该会话的 `assessment_item_snapshot`（UNKNOWN）+ 学习账本；
2. 先调 `repository.selectKnowledgeReviewQuestion(...)` 让 presenter 进入 Asking；
3. 再走答案键更正写入口生成 `UserAssertion` 回执，调
   `KnowledgeQuizItemVerificationUpgrader.upgrade` 做单列 CAS，断言 `USER_ASSERTED`。

这需要跨 `core:data` 的写入口与 `app` 的依赖装配，超出本轮 `feature/review`+`app` 热区；
且 `RoomStudyDatabase.kt` 不新增 DAO 方法的硬约束下，最小夹具也要复用
`AnswerKeyCorrectionInstrumentedTest` 的整条账本写入链。登记为阻塞，不在本轮伪造。
