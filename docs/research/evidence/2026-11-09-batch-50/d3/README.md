# D-3 证据（批次 50）：5 红清零 + 召回实修 + 117/129 保持

> 结论：**D-3 通过（在有明确判定的口径内）**。五条既有红 12/12 绿；dense 与包同源
> （check_asset 11/11）；在召回路径上修掉一个真实范围泄漏（父节点绕过 subject+可信+非退役
> 过滤），先红后绿；设备融合主集 **117/129 = 0.9069767441860465**、MRR 0.7239018087855298、
> 24 章逐章与批次 30 v24 基线**逐字节相同**（diff rc=0）；**未改任何阈值、未放宽任何断言**。

## 0. 身份

| 项 | 值 | 复算 |
|---|---|---|
| 包 | v28，contentVersion `ce8edb8f89d91fe7`，sha256 `10ede1bf449de38f…` | `python -X utf8 docs/research/evidence/2026-11-09-batch-50/d2/audit_d2.py` 的 `identity` |
| dense `.vec` | sha256 `70329115240fe6be…`，44,121 行；旁车 packSha256 == 包 sha | `check_asset.txt` |
| 金标 | `golden_queries_v2.json` sha256 `eabef102…`，129 条 / 24 章 | 测试内 sha 复核 + 每次运行打印 |
| 设备 | `emulator-5590`（API 34 / x86_64），`am instrument` 直跑 | 见下 |

## 1. 五条既有红：清零态（本批开工即绿，未回退重演）

清单定义：`docs/kb-dense-rebuild-2026-10-01.md:50-64`（GoldenRetrievalJvmTest /
ProductionLexicalLegExportTest / Stage1LexicalLabTest / Stage2LexicalScoresExportTest /
DenseTokenizerParityTest）。

```
./gradlew.bat :core:data:testDebugUnitTest \
  --tests "*GoldenRetrievalJvmTest*" --tests "*ProductionLexicalLegExportTest*" \
  --tests "*Stage1LexicalLabTest*" --tests "*Stage2LexicalScoresExportTest*" \
  --tests "*DenseTokenizerParityTest*" --console=plain --rerun
# → BUILD SUCCESSFUL in 2m 13s（修复前）/ 1m 3s（修复后）
```

| 类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| GoldenRetrievalJvmTest | 2 | 0 | 0 | 0 |
| ProductionLexicalLegExportTest | 1 | 0 | 0 | 0 |
| Stage1LexicalLabTest | 1 | 0 | 0 | 0 |
| Stage2LexicalScoresExportTest | 1 | 0 | 0 | 0 |
| DenseTokenizerParityTest | 7 | 0 | 0 | 0 |

XML 存证：`TEST-*.xml`；摘要：`five-red-tests-green.txt`；修复后日志：`five-red-tests-after-fix.log`。
**如实边界**：历史红的成因（90→130 判官扩集/包换版）已在批次 13 重钉，本批未在 HEAD 上重演红态；
本表证明的是「当前树 12/12 绿」，不是「本批修复让五红转绿」。

## 2. dense 与包同源

```
python -X utf8 tools/dense_build/check_asset.py
# → RC=0；11/11 OK：sidecar / packSha256 / vocab / tokenizer / vectorSha256 /
#   vectorHeader(512×44121) / vectorCount / vectorBytes / layout(44,121 ids 逐条) /
#   layoutSha256 / modelManifest
```
全文：`dense-asset-check.txt`。

## 3. 召回实修（真实缺陷 + 先红后绿）

**缺陷**（读码定位）：`RoomKnowledgeBaseStore.readSubjectKnowledgeRecallCandidates` 的
matched 查询带 `subject + CURATED/SOURCE_GROUNDED/USER_CONFIRMED + status != 'RETIRED'`
三道过滤（`ProblemOrganizationDao.kt:264-271`），但随后取父节点用的是
`readKnowledgeNodesByIds`（`ProblemOrganizationDao.kt:400-406`，KDoc 明写「**刻意不过滤
RETIRED**：这是历史解释路径」）。于是召回会把**已退役父主题**与**MODEL_CANDIDATE 父主题**
随 active 子节点一起返回——违反「退役内容不进新工作/召回」的既有分界
（`ProblemOrganizationDao.kt:220-224` 的注释），也与 pre-reg 冻结条目
`docs/research/2026-10-22-dense-union-prereg.md:56`（补集实体必须按既有作用域过滤）
同一条事实相符。

**修复**（2 文件，最小 hunk）：
- `ProblemOrganizationDao.kt` 新增 `readRecallParentKnowledgeNodes(ids, subject)`：与召回 SQL
  同三道过滤；
- `RoomKnowledgeBaseStore.kt` 父节点改用该查询（`parentIds` 为空时短路，不发 SQL）。

**先红后绿**（设备仪器化，真实行为失败）：

```
# 红：临时改回 readKnowledgeNodesByIds
adb shell am instrument -w -e class \
  com.tingyun.smartmistakebook.core.database.KnowledgeRecallParentScopeInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner
# → Tests run: 1, Failures: 1
#    AssertionError: ...已退役父节点不得随 active 子节点被披露；
#    ids=[atomic:candidate-child, atomic:active-child, atomic:retired-child,
#         topic:active, topic:candidate, topic:retired]
# 绿：恢复修复后同一命令 → OK (1 test)
```
存证：`recall-scope-red.txt` / `recall-scope-green.txt`；新增用例
`core/database/src/androidTest/.../KnowledgeRecallParentScopeInstrumentedTest.kt`。

**回归**：
- `KnowledgeSearchIndexCompletenessInstrumentedTest`（同召回路径，7 例）→ `OK (7 tests)`，
  `recall-completeness-regression.txt`；
- 五红复跑 12/12 绿（§1）；
- `./gradlew.bat :core:database:testDebugUnitTest` → 248 tests / **4 failures**，全部为
  与本次改动无关的既有套件（`KernelWave0SchemaContractTest` 1、`LibraryFtsCountPathQueryCopyContractTest` 2、
  `LibraryFtsRankingQueryContractTest` 1），失败文本是 schema 头/冻结 SQL 副本漂移；
  **未在回退树上重演这 4 条**，故只登记为「非本改动文件面」，不记为通过。

## 4. 117/129 保持（设备 + JVM 双读数）

**设备**（`GoldenRetrievalInstrumentedTest`，判官零改，`am instrument` 直跑，81s/76s 两轮）：

| 路线 | Recall@5 | MRR | p50 / p95 / max | overBudget | 逐章 |
|---|---|---|---|---|---|
| 融合 | **117/129 = 0.9069767441860465** | 0.7239018087855298 | 38 / 58 / 94 ms | 0 | 24 章与基线 diff rc=0 |
| 词面 | 101/129 = 0.7829457364341085 | 0.6178294573643413 | 6 / 11 / 15 ms | — | — |

- `denseLegLive=true`；预算门 p95≤250ms / p50≤150ms 内。
- 逐章对比：`device-chapters-vs-batch30.diff`（**rc=0**，24 行逐字节相同）。
- 账本：`device-fused-misses.txt`（12 条，与基线同集）、`device-lexical-misses.txt`（28 条）。
- 原始 logcat：`device-golden-logcat-final.txt`。

**JVM 融合镜像**（同一份金标/包，假编码器喂离线查询向量）：`117/129`、MRR `0.7239018087855298`、
24 章 vs 批次30 v24 **rc=0**（`jvm-fused-metrics-after-fix.txt`、`jvm-chapters-after-fix-vs-batch30.diff`）。

**口径防混**：批次 50 基线文档写「MRR 0.7323 / 词面 102/129」是批次 19 A1 时点值；当前包 v28 的
两路实测为 **MRR 0.7239 / 词面 101/129**（与批次 30 v24 逐位一致）。本报告只用本批实测值。

## 5. 未改项与已知边界

- 阈值/断言：**零改动**（`DenseFusion.ALPHA=0.5`、Recall 地板、p50/p95 门、金标 sha 均未动）。
- 12 条 MISS 仍为已知边界：`等和线` 转内容侧；`活结与死结模型`/`力的分解` 并集臂已在批次 21
  实测零增益并逐字回滚（`docs/research/2026-10-22-dense-union-prereg.md §7.10`），不重开。
- 未跑：真机（只有 emulator-5590）；2h soak；跨模块全量单测（仅跑 core:data 定向 + core:database 全量）。
