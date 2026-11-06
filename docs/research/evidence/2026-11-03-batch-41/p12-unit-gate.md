<!-- 体积自查：122 行；R4 上限 400 行 / 65536 B -->
# E-41-P12 单测门（两次独立运行均 6 红；3 红根因定位到工作区工具产物）

| 字段 | 值 |
|---|---|
| evidence-id | E-41-P12 |
| convention-version | 1 |
| batch | 2026-11-03-batch-41 |
| step | 流程（计划 §3「单测门 1」；本 ask 编号 P12） |
| gate-or-kd | 单测门：各模块 `testDebugUnitTest`（JVM） |
| verdict | **FAIL**：Run A（00:20）6 红；Run B（01:41–01:43）同样 6 红。其中 **3 红 = 工作区工具产物**（可复现、清掉即消失，非产品码），**3 红 = KB 词面 golden 漂移**（非本批面） |
| executed-at | Run A：结束 2026-11-03T00:20:30+08:00（`BUILD FAILED in 2m 10s`，起点未记录）<br>Run B：2026-11-03T01:40:30–01:43:06+08:00（仅 XML，无日志） |
| operator | Run A：门禁轨子代理；Run B：**未标识的并行轨**（本文件只读其 XML，不归因）；本文件由证据轨汇编 |
| head | `62522930bc8bae51be1523c954f39449e054b6af`（证据轨本轮 `git rev-parse HEAD` 实测）。**Run A 的日志未记录 HEAD**，Run B 无日志；"运行时 HEAD = 该值"属未经证明的假设 |
| worktree | Run A 自记 584 条；证据轨本轮实测 595 条 |
| device-serial | n/a（JVM） |
| apk-sha256 | n/a（JVM） |
| exit-code | Run A：**未记录 exit**（日志末行 `BUILD FAILED`）；Run B：**无日志、无 exit 记录** |
| raw-log | `build/batch41-unit.log`（302 行）；Run B 的逐类结果在 `core/*/build/test-results/testDebugUnitTest/TEST-*.xml`；根因复算输出 `build/evidence-batch41-p13/schema-contract-{with,skip}-mimosa.out`（`*.log`/`build/` 均 gitignored） |
| record-ref | 计划 §5 验收「单测门本批面 0 新红」；批次 40 记录 §6.4、§9.2 |
| sanitized | 是（§6；hostname 已不摘录） |

## 1. 目的与判据

判据：计划 §5「单测门本批面 0 新红」。本文件回答两件事：**(a) 到底几红；(b) 每条红归谁**。

## 2. 命令

- Run A 的日志**没有命令行头**（`build/batch41-unit.log:1` 直接是 `Reusing configuration cache.`），因此**命令不可得**。可从任务面确认：一次 Gradle 调用里出现 12 个模块的 `testDebugUnitTest`（`core:markdown` 为 NO-SOURCE），末尾 `BUILD FAILED in 2m 10s`（`:300`）。
- Run B 无日志，只有 XML；命令不可得。
- 本证据轨**未运行 Gradle**（计划纪律：门禁阶段单独运行且无并发）。同一时段另有并行轨在跑 Gradle（Run B），本文件只读其结果。

## 3. 结果摘要

| 运行 | 模块 | 用例数（日志） | 失败 | 失败类 |
|---|---|---|---|---|
| A 00:20 | `:core:database` | 235 | **3** | `ExportedSchemaContractTest`×3 |
| A 00:20 | `:core:data` | 1622 | **3** | `ProductionLexicalLegExportTest` / `Stage1LexicalLabTest` / `Stage2LexicalScoresExportTest` |
| A 00:20 | 其余 10 模块 | 日志未给总计数 | 0 | — |
| B 01:40–01:43 | `core:database` | 51 个测试类 XML | **3**（同类同名） | 同上 |
| B 01:40–01:43 | `core:data` | 189 个测试类 XML | **3**（同类同名） | 同上 |
| B | `core:export`/`core:ui`/`feature:capture`/`feature:library`/`feature:profile`/`feature:review`/`feature:tutor` | 有 XML，0 失败 | 0 | — |

- Run B 未见 `core/model`、`core/domain`、`app`、`core:markdown` 的结果 XML，故 **Run B 不覆盖全 12 模块**。
- 两轮 6 条失败的方法名完全一致 → 可复现，不是偶发。

## 4. 六条红的原文（Run B XML，`<failure message>` 逐条）

**A 组：`core:database` / `ExportedSchemaContractTest`（3）**

```text
everyExportedSchemaIsCompleteAndVersioned
  java.lang.AssertionError: Schema filename must be a version:
  sess_dwf-dwfrun-7bc8ab3b-5898-4709-b4b7-6e090eb40ab5-actor_22_1.json
placeholderSchemaHashIsExplicitlyScopedToV16Only
  kotlinx.serialization.MissingFieldException: Field 'database' is required for type with
  serial name '...core.database.SchemaFile', but it was missing at path: $
everySchemaMasterTableHashMatchesItsIdentityHash
  kotlinx.serialization.MissingFieldException: (同上)
```

**B 组：`core:data` / 词面 golden（3）**

```text
ProductionLexicalLegExportTest.exportProductionV1LexicalScoresAsFusionInput
  MRR 应为 0.6130490956072351 expected:<...> but was:<0.6233850129198968>
Stage1LexicalLabTest.stage1ArmsScoreFrozenGoldenSetAndWriteArtifacts
  应为 0.5736434108527132 expected:<...> but was:<0.5813953488372093>
Stage2LexicalScoresExportTest.exportArmAFts5ScoresAsFusionInput
  臂 A 主集命中数应为 100/129 expected:<100> but was:<101>
```

## 5. A 组根因（证据轨本轮独立复现，非推断）

**结论**：A 组 3 红**不是产品码回归**，是 `.mimosa/` 钩子状态文件被写进了 Room schema 目录，而 `ExportedSchemaContractTest` 用 `File("schemas").walkTopDown()` 递归扫描整棵树、不排除隐藏目录。

**证据链**：

1. 现场：`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/.mimosa/hook-state/` 下有 2 个 218 B 的 JSON：
   - `..._actor_5_1.json`（mtime `11-02 15:57:07`，Run A 之前已存在）
   - `..._actor_22_1.json`（mtime `11-03 00:24:16`，Run A 之后写入；Run B 的报错正是点名它）
   两者顶层键为 `touched/bashMutation/reportedFindings/...`，**没有 `database` 键**——正是 `MissingFieldException` 的来源。
2. 忽略状态：`git check-ignore -v <该文件>` → `.gitignore:29:.mimosa/`，即**不会进仓**，是工作区/工具产物，不是受版本管理的 schema。
3. 计数：`find core/database/schemas -name "*.json" | wc -l` = **75**，而版本化 schema 只有 73 个（`1.json`…`73.json`）。
4. 静态复算（本轨执行，**非 Gradle**）：把该测试的 3 条断言用 Python 复刻，同一 `schemas` 根下跑两次：

```bash
python -I build/evidence-batch41-p13/schema_contract_replica.py \
  core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase            # 含 .mimosa
python -I build/evidence-batch41-p13/schema_contract_replica.py \
  core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase --skip-mimosa
```

```text
（含 .mimosa）      → KeyError: 'database'（对应 Kotlin MissingFieldException）；exit=1
（--skip-mimosa）   → json_files=73 / placeholder_hash_versions=[16] / violations=0；exit=0
```

输出落盘：`build/evidence-batch41-p13/schema-contract-with-mimosa.out`、`schema-contract-skip-mimosa.out`。

5. 因此：**73 个真实 schema 全部满足三条断言**（版本号=文件名、identityHash 32 hex、每实体有 createSql/主键、setupQueries 有 room_master_table、placeholder 仅 v16、identityHash 与 master 插入一致）；红完全由 2 个钩子 JSON 造成。

**这仍然是一条真实缺陷（测试脆弱性）**：任何工具在 `core/database/schemas/` 下写 JSON（本仓的 Mimosa 钩子就会）即可让该契约测试变红，而报错文案指向"schema 文件名不是版本号"，对读者是误导。是否修（排除 `.mimosa`/隐藏目录）**不在本证据文件裁定**，需主会话决定；本文件只提供可复现判据。

## 6. B 组归因（未在本批范围内修复）

- 三条都是 KB 词面 golden 的**实测值小幅高于冻结值**（MRR 0.6234 vs 0.6130；Stage1 0.5814 vs 0.5736；命中 101 vs 100）。与批次 40 记录 §9.2 登记的"KB 词面测试、归 KB 线"同族，但**数值不同**（批次 40 记的是 3 个同类测试的名字，未记具体数值）。
- 本批未改 `core/data/.../knowledge/`。归属：**他线（KB）**；本文件不对"是否应更新 golden"下结论（那是 KB 线的预注册基线问题）。
- `git status` 显示 `core/data/src/test/.../knowledge/` 下无本批改动。

## 7. 缺口与不可复现点

1. **Run A 的 exit code 未记录**：只有 `BUILD FAILED` 末行。Gradle 失败态必然非零，但**没有独立退出码记录**，故 `exit-code` 字段按"未记录"处理，不写 0/1。
2. **Run B 的日志与命令不存在**：只有 XML；无法给出任务集与 exit。
3. Run A 的日志把 A 组两条报为 `MissingFieldException at ExportedSchemaContractTest.kt:170/:180`，而当前 HEAD 的该文件只有 **157 行**（`wc -l` 实测），行号超出文件长度 → **Run A 所编译的源码版本不等于当前树**；当前工作树对该文件也无 `M` 标记（本轮 `git status` 实测）。该矛盾**未解**（可能是文件级还原写入了等价内容，但无快照可证）。
4. 两轮运行期间没有源快照；不得把本文件的结果绑定到任何源码哈希，只能绑定 HEAD `62522930` + 上述 XML/日志。
5. 未覆盖：`core/model`/`core/domain`/`app` 的 Run B 结果、`--continue` 语义下的完整任务图。

## 8. 脱敏与去噪

- 未摘录 XML 的 `hostname`、用户目录；原始日志与 XML 保持在 `build/`（gitignored）。
- 统一扫描命令与结果见 `00-index.md` §8。
