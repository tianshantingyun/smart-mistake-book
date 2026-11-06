<!-- 体积自查：本文件为批次 43 R5 唯一写入；只读研究，零 Gradle、零 adb、零 git 写（仅 `git apply --check` / `git merge-file -p` / `git merge-tree` 只读形态 + 仓库外 /tmp 临时文件）。 -->

# R5：`docs/current` 7 个 M 的归属判定与并入协议（批次 43）

> 计划：`.zcode/plans/plan-2026-11-05-batch-43.md` §1 R5（`:21`）+ §2 项 45（`:82`「契约 P0 patch 在 `docs/current` 清空时应用；否则出逐文件冲突清单」）。
> 上一批：`docs/research/2026-11-04-batch-42-record.md` §6.1-7（「契约并入 BLOCKED：`docs/current` 仍 7 M」）、§9.2/§9.3/§9.5。
> 纪律：`docs/current` 属热区，本文件**零改**该目录任何字节；所有合并动作只出**协议与命令**，不执行。

## 0. 结论摘要（三段，先给答案）

1. **归属已定，不是匿名漂移**：7 个 M 中 **6 个文件与分支 `codex/plans-2026-10-20` 的提交 `32026050`（2026-10-20 19:59:26）逐字节相同**；`README.md` 是「`32026050` 的 10 行 + 工作树孤儿 6 行（任何提交都不含）」两层。该分支已推送、tip 仍是 `32026050`，同名物理工作树 `~/.codex/worktrees/plans-publication` 现存在且停在该提交。→ **归属：`codex/plans-2026-10-20`（plans-publication）历史分支线；main 从未接收过它。**
2. **「7 M 阻塞契约 patch」是错口径**：`2026-11-04-contract-v1.1-p0-applicable.patch` 只改 `docs/current/visual-generation-contract.md`，而**该文件本身是干净的、不在 7 M 之内**；本轮实跑 `git apply --check` = **exit 0**。真正的阻塞是「热区禁改」纪律，不是「7 M 冲突」。反向铁律：该 patch 对**分支版** vgc 应用会 **hunk #2 FAILED**（exit 1），所以顺序必须是「先并入 main 版 vgc 再打 patch」，绝不能在分支版上应用。
3. **并入必须分层，不能一次性 `git merge`**：3-way 干跑（`git merge-tree 1d8bb937 HEAD origin/codex/plans-2026-10-20`）显示 7 个 docs/current 文件**只有 vgc 一处 add/add 冲突**，但**全仓共 7 个冲突文件 / 25 处冲突**，其中 `docs/research/2026-10-17-6c2-content-selection-design.md`（3 处）与 4 个 `docs/superpowers/*` 计划件直接落在 6C-2 裁定与本批计划的依赖链上。另：`git merge` 会因工作树里这 7 个文件是脏的直接拒绝（"local changes would be overwritten"）。

## 1. 本轮实读/实跑（可重跑，逐条给命令）

| # | 动作 | 命令（逐字） | 结果 |
|---|---|---|---|
| E1 | 7 M 现状 | `git status --porcelain -- docs/current` | 7 条 ` M`；`git diff --cached --name-only -- docs/current \| wc -l` = **0**（索引干净，全在工作树） |
| E2 | 改动规模 | `git diff --stat -- docs/current` | **99 insertions(+), 5 deletions(-)**，7 文件 |
| E3 | 逐文件内容指纹 | `git hash-object docs/current/<f>` vs `git rev-parse 32026050:docs/current/<f>` | 6 文件 **IDENTICAL**；`README.md` DIFF；`visual-generation-contract.md` DIFF（但干净） |
| E4 | 分支线定位 | `git log -1 --format='%h %ad %s' --date=short origin/codex/plans-2026-10-20` | `32026050 2026-10-20 docs(plans): 同步统一生图、产品补齐与 UI 改版计划` |
| E5 | 分叉面 | `git rev-list --left-right --count origin/codex/plans-2026-10-20...main` | **1 / 255**（分支独有 1 提交，main 独有 255） |
| E6 | 合并基 | `git merge-base origin/codex/plans-2026-10-20 main` | `1d8bb937ebca78b376a155f2b55a074f7ff3a180` |
| E7 | 两侧改动面 | `git diff --name-only <MB> main -- docs/current` / `… origin/codex/… -- docs/current` | main 侧 **2**（`figure-generation-prompt-policy.md`、`visual-generation-contract.md`）；分支侧 **9**（含上面 7 M 全部） |
| E8 | 3-way 干跑 | `git merge-tree 1d8bb937 HEAD origin/codex/plans-2026-10-20` | **exit 0**；输出含 `<<<<<<<` **25** 处 / `>>>>>>>` 25 处；7 个 docs/current 有 `result` 行（=分支版 blob），vgc 只有 `our`/`their` 无 `result` |
| E9 | 逐文件 3-way 复核 | 对「两侧都改」的 11 文件跑 `git merge-file -p ours base theirs > /dev/null` | 冲突 **7** 个（exit>0），干净 **4** 个（含 `figure-generation-prompt-policy.md` exit 0）；冲突数合计 **25**，与 E8 的标记数一致 |
| E10 | patch 可行性（工作树） | `git apply --check --verbose docs/research/2026-11-04-contract-v1.1-p0-applicable.patch` | **EXIT=0**（"Checking patch docs/current/visual-generation-contract.md..." 无错） |
| E11 | patch 对分支版 vgc | 把 `de770fe5…` 内容落到 `/tmp/r5vgc/docs/current/` 后 `patch -p1 --dry-run < …patch` | **hunk #1 succeeded at 154 (offset -25)；hunk #2 FAILED at 205；1 out of 2 hunks FAILED；PATCH_EXIT=1** |
| E12 | 代码事实核对 | 见 §3 各行"核对"列（`sed`/`grep` 直读源码） | 逐条列出 |
| E13 | 物理工作树 | `git worktree list` | `…/worktrees/plans-publication/smart mistake book  32026050 [codex/plans-2026-10-20]` 存在 |

**未执行**：任何 `git apply`（真应用）、任何 `git merge`/`checkout`/`add`/`commit`、任何 Gradle、任何 adb。E8/E9 是 git 的**只读**合并干跑（E8 用 deprecated `--trivial-merge` 三参形态，官方文档明写「does not read from or write to either the working tree or index」）；E11 的 `patch --dry-run` 与临时树全在仓库外 `/tmp`。

## 2. 归属判定（7 文件 × 内容层）

### 2.1 逐文件指纹

| 文件 | 工作树 blob | `32026050` blob | 判定 |
|---|---|---|---|
| `README.md` | `801c235edad3…` | `77d078b1bb15…` | **两层**（见 2.2） |
| `architecture-contract.md` | `aeb9fd884758…` | `aeb9fd884758…` | **逐字节相同** |
| `data-schema.md` | `ff11c756ed10…` | `ff11c756ed10…` | **逐字节相同** |
| `implementation-status.md` | `d05c75906a84…` | `d05c75906a84…` | **逐字节相同** |
| `model-task-contracts.md` | `8226919d1662…` | `8226919d1662…` | **逐字节相同** |
| `product-contract.md` | `7029de96bf2b…` | `7029de96bf2b…` | **逐字节相同** |
| `release-gates.md` | `a315eece7a81…` | `a315eece7a81…` | **逐字节相同** |

### 2.2 `README.md` 的两层

- **层 1（10 行，可溯源）**：`32026050` 新增的 6 段索引（`visual-generation-contract` 行、`figure-generation-prompt-policy` 行、followup wiring、product-completion planning、UI planning authority、2026-10-19 redesign 等）。
- **层 2（6 行，无提交来源）**：`2026-10-20 scope-limited follow-up` / `Existing-page usability details` / `Source-based continuation` 三段。实跑 `git log --all --oneline -S "UF-13–19"` = **零命中**；`git log --all -S "2026-10-20 scope-limited follow-up"` 亦零命中 → **只存在于工作树**。这三段引用的审计件本身**已入库**（`docs/research/2026-10-20-{existing-ui-logic-audit,ui-usability-friction-audit,ui-flow-followup-audit}.md`，同属 `2163810d` 2026-10-21）。→ **归属：UI/审计线（`2163810d` 同族会话）的工作树孤儿编辑**，无提交、无归属记录。

### 2.3 「他线在飞」的准确定性

- 分支 `codex/plans-2026-10-20` **已推送**（`origin/` 同名引用存在），tip 仍为 `32026050`，**14 天无新提交**（对比今日 2026-11-03）。
- 物理工作树仍在：`~/.codex/worktrees/plans-publication`，停在同一提交（E13）。
- 因此准确表述是：**这是一条已推送、未并入 main、内容以未提交 M 形态落在 main 工作树里的历史分支线**。「仍在活跃开发」无证据（`UNVERIFIED`，本文件不这么写）。
- 批次 36–42 的记录把它统一登记为「他线在飞 diff」，**从未判定归属**（批次 36 baseline `:41`、批次 37 baseline `:39` 只记集合，不记来源）。本文件是该集合第一次被钉到具体提交。

## 3. 逐文件内容判定（采纳 / 改写 / 拒绝，逐条给理由）

> 判定三类：**A 采纳**（新增"计划/未来"段，不与现状矛盾）；**B 采纳但须改写**（含已过时事实）；**C 须主会话裁定**（改写的是**当前行为/门语义**断言，不是"计划"）。
> 「核对」列的引码均为本轮直读。

| 文件 | 改动形态 | 内容分类 | 现状核对（源码） | 判定 |
|---|---|---|---|---|
| `README.md` 层 1 | +10（全文件 +16 含层 2） | 索引指针 | 指向的 `visual-generation-contract.md`/`figure-generation-prompt-policy.md` 均在树且已由 `2163810d` 入库 | **A** |
| `README.md` 层 2 | +6 | 索引指针（审计件） | 3 个审计件均已入库；文本自带 "unverified/pending" 限定 | **A'（采纳，但须在提交说明里写明「无提交来源」）** |
| `architecture-contract.md` | +21 / −0 | 「Planned visual runtime (4E, not implemented)」+ 依赖图 + 「Unified figure orchestration」 | `settings.gradle.kts:21-27` 仍无 `:core:visual`/`:core:visual-js`（"planned"成立）；但 `core/domain/.../FigureGenerationCoordinator.kt:25` **已存在接口**、`core/data` 已有 `FigureGenerationCoordinatorTest` → "planned" 对模块成立、对 coordinator 端口**已不成立** | **B**（保留，补一句"coordinator 端口已在 core:domain 落地"） |
| `data-schema.md` | +10 / −0 | 「Planned visual records」+「Unified generation operation records」 | 追加段写「Room 66/67 … **target 68/18**」；实测 DB = **73**（`core/database/.../StudyDatabase.kt:142 internal const val STUDY_DATABASE_VERSION = 73`）、请求 schema = **19**（`core/model/.../ModelTasks.kt:566 const val PAPER_EDIT_SCHEMA_VERSION = 19`）。同一过时句在**已入库**的 `visual-generation-contract.md:182` 也仍在（契约 patch 的 C-10 正是替换它） | **B（必须改写）**：直接并入会向 `docs/current` 注入 5 个版本号级别的错误事实 |
| `implementation-status.md` | +2 / −0 | 2026-10-18 路线更新抬头 | 自称"既有日期/提交/测试/缺陷作为历史事实保留"，与现行事实不冲突 | **A** |
| `model-task-contracts.md` | +10 / **−1** | ① 删除非流式任务清单里的 `visual generate/review`；② 新增「Planned GENERATE_FIGURE extension (4E)」 | ① 正确：`ModelTaskKind`（`core/model/.../ModelTasks.kt:11-28`）为 CAPTURE_ASSESS/PARSE、PROBLEM_CLASSIFY/RELATE、TUTOR_PLAN/RESPOND/LOBBY/EVALUATE、REVIEW_RERANK、LEARNING_SUMMARIZE、IMAGE_PIPELINE_CLASSIFY、KNOWLEDGE_QUIZ、PAPER_EDIT，**无** visual generate/review。② 措辞过时：`GENERATE_FIGURE` **已是生产广告工具**，只广告 `IMAGE_MODEL`（`core/data/.../OpenAiModelProtocol.kt:122-131` 默认 `figureMethods = setOf(IMAGE_MODEL)`、`:371-372`、`:387-390` jsAdvertised 分支） | **①A；②B**（删除保留；「planned extension」须改写为「已接线、JS 方法未广告」） |
| `product-contract.md` | +15 / **−2** | ① 未动；② 原地改写「Models propose local actions; **only the user can** save/update mastery…」→「typed tools + TutorPermissionPolicy，NOTEBOOK_WRITE/本地动作 ASK，MASTERY_UPDATE/ADVISORY_WRITE AUTO_VISIBLE + MasteryWriteGate」；③ 新增「Planned teaching figures (4E)」「Planned product-completion corrections (2026-10-19)」 | ②**事实正确且旧句已为假**：`core/domain/.../TutorPermissionPolicy.kt:13` 档位表、`:99-105`（`MASTERY_UPDATE`/`ADVISORY_WRITE -> AUTO_VISIBLE`）、`:24-26`（`MasteryWriteGate` 是 MASTERY_UPDATE 的**唯一信任边界**） | **②C（改写当前契约断言，须主会话明示追认，不得静默带入）**；③A |
| `release-gates.md` | +25 / **−2** | ① 「no dead buttons… **four root pages**」→「**any shipped root page**」；② 「models **cannot** auto-save or write mastery」→「ASK actions never execute without their required user action；AUTO_VISIBLE evidence writes pass host policy/MasteryWriteGate…」；③ 新增 4 段 Planned gate（4E/产品补齐/UI 可用性） | ① 收紧且等价：`app/.../SmartMistakeBookRoot.kt:154-159` `rootDestinations` 恰为 4 条（Review/Tutor/Library/Profile），「any shipped root page」⊇「four root pages」；② 同 product-contract②，旧句与代码矛盾（AUTO_VISIBLE 档真实存在） | **①A；②C（这是**放宽式**门语义改写——虽与代码一致，仍须裁定）；③A** |

### 3.1 采纳/拒绝的合并判据（可机器执行）

一段 hunk 只有同时满足三条才可**直接**并入：

1. **不触碰现行数字**：不得出现 `Room 6x`、`schema 1x`、`68/18`、`17` 请求 schema 之外的新旧版本号，或与 `StudyDatabase.kt:142`(=73)/`ModelTasks.kt:566`(=19) 不一致的版本主张。
2. **不改写既有事实句**：`−` 行（删除行）为 0。任何 `−N` 都必须有 C 类裁定。
3. **新增段必须以 `Planned`/`not implemented`/`not shipped` 自限**（7 M 的 add-only 段基本满足）。

按此判据，7 文件中**只有 `README.md` 与 `implementation-status.md` 的 `−` 行为 0**；`model-task-contracts.md`（−1）、`product-contract.md`（−2）、`release-gates.md`（−2）各自带删除行，逐条归 C。

## 4. patch 落点真相（推翻批次 42 的「7 M 阻塞」表述）

- patch 只含 2 个 hunk，**唯一目标文件是 `docs/current/visual-generation-contract.md`**（`git apply --check --verbose` 只打印该路径），而该文件在 `git status` 中**干净**。
- 因此「`docs/current` 7 M → 并入 BLOCKED」（批次 42 记录 §6.1-7、§1.2 项 71）在**文本层面不成立**：7 M 与 patch 目标不相交。真正约束是计划 §4「热区 `docs/current` 禁改」这条**纪律**，本质是"要不要解冻热区"，不是"会不会冲突"。
- 但 E11 给出**反向硬约束**：patch 的 hunk #2（`@@ -205,12 +205,14 @@`，KD-81 快照身份句 → C-6 关闭口径）**只能落在 main 版 vgc**；落到分支版（`de770fe5`）直接 FAILED。原因是 main 在 `2163810d`/`9e584360` 等提交里把该文件推进了 30 增 3 删，行号与上下文都变了。→ **顺序铁律：先让 main 版 vgc 保持在位（或先打 patch），再谈文件级合并；任何"先合并分支、后打 patch"的次序都会使 patch 需要重新标定。**

## 5. 并入协议（不覆盖他线；四选项 + 推荐）

### 5.1 四个可选路径

| 选项 | 做法 | 代价 | 风险 |
|---|---|---|---|
| **A 真合并分支** | `git merge origin/codex/plans-2026-10-20` | 一次性收编 70 个分支文件 | **7 文件 25 处冲突**（含 6C-2 设计件 + 4 个 superpowers 计划件）；**工作树 7 个脏文件使 merge 直接拒绝**；README 层 2 的 6 行会被覆盖丢失 |
| **B 逐文件 3-way（推荐）** | 在**临时 worktree** 里 `git merge-file` 三方合成，人工只裁 C 类 hunk，再按块提交 | 需人工 3 处裁定（product-contract②、release-gates②、model-task-contracts②） | 需要临时 worktree 且不得在共享树里做 git 写 |
| **C 只取 6 文件** | `git checkout 32026050 -- docs/current/{architecture-contract,data-schema,implementation-status,model-task-contracts,product-contract,release-gates}.md` + README 分层处理 | 最小面；不碰冲突集 | 会把 `data-schema.md` 的 68/18 过时句一起带进来（必须同批改写）；`−` 行静默带入 |
| **D 维持登记（现状）** | 不动，继续在每批记录里登记 7 M | 零风险 | 契约正文长期无法推进；每批重复记账 |

### 5.2 推荐：**分三级、严格先后**（本文件给出的协议，未执行）

> 前提：本批（43）与后续批次的纪律里 `docs/current` 是热区 → **任何一级都要主会话解冻窗口**，本文件只给命令。

**级 0（前置，必须最先做）：锁定基线、拒绝脏树合并**

```bash
git -C "D:/smart mistake book" status --porcelain -- docs/current      # 期望：仍是 7 条 M
git -C "D:/smart mistake book" diff --cached --name-only -- docs/current | wc -l   # 期望：0
git -C "D:/smart mistake book" hash-object docs/current/*.md              # 落盘，与 §2.1 逐位比对
```

若任一条不符 → 停止（说明工作树被别人改过，本文件的指纹失效）。

**级 1（推荐先做）：patch 单独落地（与 7 M 完全解耦）**

```bash
git apply --check --verbose docs/research/2026-11-04-contract-v1.1-p0-applicable.patch   # 期望 EXIT=0
git apply --verbose           docs/research/2026-11-04-contract-v1.1-p0-applicable.patch
python -I tools/ci/check_backup_plan_sync.py        # 契约 C-10 的 73/92/trim 84/closure 5 与四方门同源
grep -n "不含 \`-wal\`" docs/current/visual-generation-contract.md   # 期望 0 命中（C-6 关闭口径）
```

这一级**不需要**先清空 7 M（目标文件干净、不相交）。它与批次 42 的「BLOCKED」判定的差别只在纪律解释，不在事实。

**级 2（级 1 之后，逐文件，块 ≤6 路径）：分块收编 7 M**

1. 建临时 worktree（不污染共享树）：
   `git worktree add /tmp/wt-r5-merge --detach HEAD`（**仅在主会话批准 git 写时**）
2. 对每个文件做三方合成，保留 main 版为 ours：
   ```bash
   git -C /tmp/wt-r5-merge merge-file -p \
     <(git show main:docs/current/data-schema.md) \
     <(git show 1d8bb937:docs/current/data-schema.md 2>/dev/null) \
     <(git show origin/codex/plans-2026-10-20:docs/current/data-schema.md) \
     > /tmp/wt-r5-merge/docs/current/data-schema.md
   ```
3. 只对 **A 类文件**（`README.md`、`implementation-status.md`）直接采纳合成结果；
4. **B 类**先改写过时句（`data-schema.md` 的 68/18 → 73/19；`model-task-contracts.md` 的 planned 措辞；`architecture-contract.md` 补 coordinator 已落地），再采纳；
5. **C 类三处**（`product-contract.md` −2、`release-gates.md` −2、`model-task-contracts.md` −1 的删除门）**必须**附一条裁定记录（`docs/agent-first-refactor-decisions-2026-09-23.md` 批次 43 节）后才提交；
6. 提交块（每块 ≤6 路径，示例）：B-A `README.md` + `implementation-status.md`；B-B `architecture-contract.md` + `data-schema.md` + `model-task-contracts.md`；B-C `product-contract.md` + `release-gates.md` + 裁定记录。

**级 3（可选，且需重新评估）：分支 `codex/plans-2026-10-20` 的整体收编**

只有当出现下列任一情况才值得：① 分支要继续被使用；② `docs/superpowers/*` 与 `docs/research/2026-10-17-6c2-*` 的冲突要一并解决。届时按 §6 的清单逐条裁，**且必须先解决 6C-2 设计件的 3 处冲突**（该文件是 2026-10-24 用户裁定的载体，批次 42 §9.2 已因越权回滚过一次）。

### 5.3 「不覆盖他线改动」的四条保证

1. **只做加法优先**：7 个 docs/current 文件合计 104 行改动中 **99 行是追加、仅 5 行删除**；5 行删除逐条落 C 类裁定，无裁定不并入。
2. **不碰他线文件**：级 1/级 2 的路径集合严格限于 `docs/current/{这 7 个}` + 契约 patch 的目标文件；`core/domain`、`core/ui`、`feature/*`、`settings.gradle.kts`、`Application/Root` 一律不出现。
3. **README 层 2 先固化**：层 2 的 6 行无提交来源，级 2 前必须先单独提交（否则任何 `git merge`/`checkout` 都会把它冲掉——E8 显示合并结果是 `77d078b1`，即层 1 版本）。
4. **逐 hunk 归属标注**：提交说明里对 5 行删除逐条写「原句为何为假 + 引码行号」，供 anybody 复算。

## 6. 冲突清单格式（计划项 45 的交付物）

### 6.1 字段定义（机器可核）

| 字段 | 取值 | 判据 |
|---|---|---|
| `path` | 仓库相对路径 | `git diff --name-only` 输出 |
| `layer` | `docs/current` / `docs/research` / `docs/superpowers` / `other` | 路径前缀 |
| `base_blob` | 40 位 sha 或 `MISSING` | `git rev-parse <MB>:<path>`；`MISSING` ⇒ add/add 类 |
| `ours_blob` / `theirs_blob` | 40 位 sha | `git rev-parse main:<path>` / `git rev-parse origin/codex/plans-2026-10-20:<path>` |
| `conflict_kind` | `add/add` / `content` / `clean` | base 缺失且两侧皆有 ⇒ `add/add`；`git merge-file` exit 0 ⇒ `clean` |
| `conflict_hunks` | 整数 | `git merge-file -p …` 的退出码（= 冲突段数），交叉核对 `<<<<<<<` 计数 |
| `owner` | 文本 | 本文件 §2/§3 的归属结论；未知写 `UNKNOWN` |
| `class` | `A` / `B` / `C` | §3.1 判据 |
| `disposition` | `ours` / `theirs` / `manual` / `defer` | 处置结果 |
| `verify_cmd` | 一段可执行命令 | 处置后必须能跑出确定输出 |
| `verified` | `true` / `false` | 只在**处置后**实跑过才可为 `true` |

### 6.2 本轮实测冲突清单（E8/E9 结果原值）

`docs/current` 面：

| path | conflict_kind | ours | theirs | hunks | owner | class | disposition |
|---|---|---|---|---|---|---|---|
| `docs/current/visual-generation-contract.md` | **add/add** | `71cc5493…` | `de770fe5…` | **7** | main 侧 = 批次 21–32 契约演进（`9e584360` 等）；分支侧 = `32026050`/`2163810d` | C（且 patch 依赖 main 版，见 §4） | **ours**（分支版会把契约回退 9 个批次） |
| `docs/current/figure-generation-prompt-policy.md` | clean（两侧新增内容相同） | — | — | 0 | body 同源 | A | theirs=ours |

`docs/current` 外（若走选项 A/级 3 才相关）：

| path | conflict_kind | hunks | 说明 |
|---|---|---|---|
| `docs/research/2026-10-17-6c2-content-selection-design.md` | content | **3** | **6C-2 裁定载体**（批次 18/42 §9.11 机器块）；冲突处理须先确认 6C-2 三项不变量不被改写 |
| `docs/research/2026-10-19-ui-ux-audit.md` | content | 1 | UI 审计 |
| `docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md` | content | 1 | 4E 实施计划（WG-01–10 来源） |
| `docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md` | content | 4 | PRD-01–11 |
| `docs/superpowers/plans/2026-10-19-ui-ux-redesign-plan.md` | content | 5 | UI-00–11 |
| `docs/superpowers/specs/2026-10-19-ui-ux-redesign-design.md` | content | 4 | UI 设计 |
| **合计** | — | **25** | 与 E8 的 `<<<<<<<` 计数 **25** 一致 |

干净（无需处置）的 4 个两侧都改文件：`docs/current/figure-generation-prompt-policy.md`、`docs/research/2026-10-18-flexible-js-drawing-plan-review.md`、`docs/research/2026-10-19-product-readiness-research.md`、`docs/superpowers/specs/2026-10-19-product-readiness-design.md`。

### 6.3 JSON 形态（可直接作为项 45 的落盘载体）

```json
{
  "schema": "contract-merge-conflict-list/1",
  "head": "0fef9436",
  "theirs_ref": "origin/codex/plans-2026-10-20@32026050",
  "merge_base": "1d8bb937ebca78b376a155f2b55a074f7ff3a180",
  "generated_by": "git merge-tree 1d8bb937 HEAD origin/codex/plans-2026-10-20 (read-only) + git merge-file -p",
  "entries": [
    {
      "path": "docs/current/visual-generation-contract.md",
      "layer": "docs/current",
      "base_blob": "MISSING",
      "ours_blob": "71cc549303e7e8bcd2a1f94ac1ee478523ab4cea",
      "theirs_blob": "de770fe5bcba49143a07567eba81f39caaebc897",
      "conflict_kind": "add/add",
      "conflict_hunks": 7,
      "owner": "main=batch21..32 contract evolution; theirs=codex/plans-2026-10-20",
      "class": "C",
      "disposition": "ours",
      "verify_cmd": "git diff --quiet 71cc549303e7e8bcd2a1f94ac1ee478523ab4cea -- docs/current/visual-generation-contract.md",
      "verified": false
    }
  ]
}
```

## 7. 未验证 / BLOCKED

| 项 | 状态 | 说明 |
|---|---|---|
| 真应用 patch / 真合并 / 真提交 | **未执行** | 本轨只读；`docs/current` 热区 + 「不做 git 写」双重约束 |
| 7 M 的**真实作者意图** | **UNVERIFIED** | 只有指纹级归属（= `32026050`）。谁在 2026-11-01 14:07 把它们写成工作树状态，无证据（7 文件 mtime 同一秒 = 批量操作，无 reflog 对应项） |
| README 层 2 六行的作者 | **UNVERIFIED** | 任何提交都不含该文本；只能按引用件推断为 `2163810d` 同族会话 |
| 分支线是否仍在活跃开发 | **UNVERIFIED** | tip 14 天未变、工作树存在；未做任何作者/时间线查询 |
| `data-schema.md` 改写后的正确措辞 | **BLOCKED** | 需要契约 C-10 已落地（级 1）才能引用 73/19；否则两处会互相矛盾 |
| C 类三处的裁定 | **BLOCKED（需主会话）** | product-contract −2、release-gates −2、model-task-contracts −1；本文件只给「原句为假」的代码证据，不做裁定 |
| 25 处冲突的实际解决 | **BLOCKED** | 级 3 范畴；依赖 6C-2 设计件的处置次序 |

## 8. 来源

外部（官方文档，本轮抓取正文核对）：

1. `git merge-tree` 三参 deprecated 形态「reads three tree-ish, and outputs trivial merge results and conflicting stages to the standard output in a semi-diff format」「does not read from or write to either the working tree or index」— <https://git-scm.com/docs/git-merge-tree>（**采纳**：E8 用的是该形态，只读性有官方依据）。
2. `git apply --check`「Instead of applying the patch, see if the patch is applicable to the current working tree and/or the index file and detects errors. Turns off 'apply'.」— <https://git-scm.com/docs/git-apply>（**采纳**：E10 的语义依据；注意官方**未**逐字规定非零退出码，本文件只写实测值）。
3. `<https://git-scm.com/docs/git-merge>`、`<https://git-scm.com/docs/git-merge-file>`：`merge-file` 退出码 = 冲突段数、`merge` 遇本地修改会拒绝——**仅引用，未逐字抓取**（标「待验证」；本文对它们的判据只用 E9 实测退出码 0–7 与 E8/E9 的一致性，不依赖文档措辞）。

仓库内（本轮实读，全部给了行号）：

- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:142`（`STUDY_DATABASE_VERSION = 73`）
- `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:11-28`（`ModelTaskKind` 无 visual generate/review）、`:566`（`PAPER_EDIT_SCHEMA_VERSION = 19`）
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/TutorPermissionPolicy.kt:13`、`:24-26`、`:99-105`
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/FigureGenerationCoordinator.kt:25`
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelProtocol.kt:122-131`、`:371-372`、`:387-390`
- `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:154-159`（4 个根目的地）
- `settings.gradle.kts:21-27`（无 `:core:visual*`）
- `docs/research/2026-11-04-contract-v1.1-p0-applicable.patch`（2 hunk）与 `docs/current/visual-generation-contract.md:182,208`（patch 的两处锚点）
- 归属的历史锚：`docs/research/2026-11-01-batch-39-tail-baseline.md:63`（`~/.codex/worktrees/plans-publication` = `32026050`）；`docs/research/2026-10-30-batch-36-baseline.md:41`、`docs/research/2026-10-31-batch-37-baseline.md:39`（7 M 集合 + vgc 自身干净，首次记录）

## 9. 回链

- 计划项：`.zcode/plans/plan-2026-11-05-batch-43.md:21`（R5）、`:82`（项 45）。
- 前情：`2026-11-04-batch-42-record.md` §6.1-7（BLOCKED 表述，本文件 §4 更正其范围）、§9.2（6C-2 回滚 → §5.2 级 3 的次序约束）、§9.3。
- 契约条款：`2026-11-02-contract-v1.1-tightening-draft.md`（C-1…C-10）、`2026-11-03-contract-v1.1-p0-seams.md`（P0 三处落点与判据）。
- 同批其他研究件：R1 `2026-11-05-kd75-model-text.md`、R2 `2026-11-05-kd76-a6-protocol.md`、R3 `2026-11-05-isolation-selfwitness.md`、R4 `2026-11-05-clean-tree-release.md`、R6 `2026-11-05-plan-closure.md`。
