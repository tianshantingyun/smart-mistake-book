# D-0 绑定裁定三元组 · 写侧契约（2026-10-07）

> 依据：台账裁决 27（为 `bindings[]` 增加裁定状态 + 来源 + 时间戳，本文为改述；
> `docs/agent-first-refactor-decisions-2026-09-23.md` **工作树版** :2315-2318——该段属 KB 线
> 未提交改动，HEAD 版尚无此段，引用以工作树为准）；实施计划
> `docs/research/2026-10-07-d0-binding-verdict-plan.md`。
>
> 状态：**内核侧载体已落**（批 1 codec/记录/契约 + 批 2 端到端 drill，零 schema），
> **真实包尚未写入任何三元组**——字段填充属 KB 线（写入窗口 D-5）。本文档是给 KB 写侧的
> 契约：键名/类型/词表/all-or-none/schemaVersion=3、CSV→字段映射、两条交接与登记。
> **词表与键集以本文档 + Kotlin codec 为准**；两者不一致时以 codec 的实测行为为准并回来改本文档。

## 1. 字段在哪、长什么样

位置：教学材料侧车 `materials[].bindings[]` 的每个绑定对象上，三个**可选**键。

| 键 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `verdict` | string（枚举，见 §3） | 三元组同现或同缺 | 越界即拒 |
| `verdictSource` | string（枚举，见 §3） | 同上 | 越界即拒 |
| `judgedAtEpochMillis` | integer（epoch millis） | 同上 | **≥ 0**；必须 JSON 整数（字符串/布尔拒） |

- **all-or-none**：三键同现、或三键同缺。缺一/缺二 = 整包在解码期拒
  （报 `binding verdict fields must be all present or all absent`）。
  理由：半写的三元组让读者分不清「从未裁定」与「裁定了但字段路上丢了」，后者不可表示。
- 三元组描述**绑定本身**被如何裁定；不给材料增加任何评测/评分/排程权威。
- 未裁定的绑定整组缺席（不是 null）：`"verdict": null` 这类写法会被
  `requiredEnum` 的"必须是字符串"挡下（`JsonNull` 不是字符串），不构成"未裁定"的合法写法；
  三种键在 v3 下只有「三键都是非空字符串 + 整数时间戳」或「三键都不在」两种形态。

实现（内核侧，已落）：
- 键集与版本门：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/ReviewedTeachingMaterialSidecarJsonCodec.kt:50-61`
  （`CURRENT_SCHEMA_VERSION=3`、accepted `{1,2,3}`、`bindingVerdictKeys` 三键、`requiredBindingKeys` 两键），
  绑定解析在 `:172-223`（v1/v2 分支 `:176-184` 走原样 `requireOnlyKeys("knowledgeNodeId","role")`；
  v3 分支 `:185-222` 查未知键 → 缺必需键 → all-or-none → 时间戳 → 枚举）。
- 记录字段：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabaseRecords.kt:273-275`
  （三个带默认值的可空字段）。
- 一致性校验（非 codec 路径也要过）：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeTeachingMaterialContract.kt:159-180`。

## 2. schemaVersion：写三元组必须 `schemaVersion: 3`

- 侧车 `schemaVersion` 是**内容格式版本**，与 DB schema 无关（本轮零 schema，`LearningCoreVersions` 不动）。
- codec 现接受 `{1, 2, 3}`，当前版本 3（`ReviewedTeachingMaterialSidecarJsonCodec.kt:50-57`）。
- **v1/v2 下三元组三个键都是未知键 → 整包拒**（严格性逐字不变，`:176-184`）：
  「v2 包里混进三元组」不是"旧读者忽略新字段"，而是解码失败。
  跨版本含义：v3 包在**旧 codec**（只收 1/2）上会整包拒——App 未发布故无现实风险；
  写侧只要写三元组，就必须把同一份侧车的 `schemaVersion` 一起写成 3（写 2 会被自己的门拒）。
- **扩词表（§3）需再 bump `schemaVersion`**：新取值会被当前 v3 reader 当未知取值拒，
  语义上等于一次格式变更，必须走版本门而不是悄悄改词表。
- 唯一键集门在 Kotlin codec（见 §5.1）：Python 侧 promote 门目前不看绑定键集。

## 3. 词表（编译期枚举 + 本契约钉死）

`verdict ∈ {KEEP, REBIND, NONE}`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ProblemCatalog.kt:219-223`）

| 值 | 语义（与 `content_audit_*.csv` 现行口径一致） |
|---|---|
| `KEEP` | 绑定成立，维持现绑节点 |
| `REBIND` | 该改绑：材料应移到另一个知识点 |
| `NONE` | **不是"无问题"，也不是"自动解绑"**：审计判现绑不成立且未给出唯一改绑目标。CSV 实测两类用法：① 材料不是教学内容（题干残片／依赖未收录的图）→ 建议释放（10-02/10-06 轮为主）；② 材料是教学内容但落点未定（跨节点混合需拆条／两读并存／包内无承接节点）→ 留人工（09-25 轮为主）。写侧原值落账；读侧不得把 `NONE` 当"应删该绑定"的指令，后续轮次可覆盖 |

`verdictSource ∈ {MODEL_AUDIT, USER_DECISION, MIGRATION}`（`ProblemCatalog.kt:226-231`）

| 值 | 语义 |
|---|---|
| `MODEL_AUDIT` | 审计工序读正文的语义裁定（现有 CSV 裁定链属这一档） |
| `USER_DECISION` | 人工裁定 |
| `MIGRATION` | 迁移期由脚本补登（例如把历史裁定折进包时） |

> 近名轴提醒：`core/model` 另有一枚 `BindingAcceptanceSource`
> （`ProblemCatalog.kt:269-276`，`LOCAL_POLICY_ACCEPTED`/`USER_CORRECTED`/…）——那是
> **学生侧/本地策略接受一条绑定**的来源轴，与 `verdictSource`（**审计裁定**的来源）不是
> 一回事，写侧不要混用，更不要把它折进三元组。

`judgedAtEpochMillis`：裁定发生时间，epoch millis，与既有 `reviewedAtEpochMillis` 同口径。

## 4. CSV → 字段映射（实读落表）

### 4.1 `content_audit_*.csv` → 主映射（审计裁定）

四张轮次表（列集逐字相同，8 列
`subject,slug,material_slug,current_node_slug,suggested_node_slug,verdict,evidence,slice`）：

| 表 | 行 | KEEP | REBIND | NONE |
|---|---|---|---|---|
| `tools/kb_build/tables/content_audit_2026-09-25.csv` | 409 | 330 | 56 | 23 |
| `tools/kb_build/tables/content_audit_2026-10-02.csv` | 496 | 383 | 111 | 2 |
| `tools/kb_build/tables/content_audit_2026-10-04.csv` | 1188 | 954 | 234 | 0 |
| `tools/kb_build/tables/content_audit_2026-10-06.csv` | 4272 | 3491 | 761 | 20 |

（行数与分布为 2026-10-07 本契约实读复算；09-25 一轮的分布与
`docs/kb-stage4-report-2026-09-25.md:5,15` 逐字一致，10-02 一轮与
`docs/kb-bind-audit-round2-2026-10-02.md:294` 逐字一致。）

映射（写侧照此落账）：

| CSV 列 | 字段 | 说明 |
|---|---|---|
| `verdict` | `verdict` | **逐字**：`KEEP`/`REBIND`/`NONE` 与枚举同名，正是为了直落 |
| （无此列） | `verdictSource` | 这四轮都是读正文的语义裁定 → `MODEL_AUDIT` |
| （无此列） | `judgedAtEpochMillis` | **表里没有逐行时间戳**，只能取轮次日（文件名日期 00:00 UTC）：09-25→`1790294400000`、10-02→`1790899200000`、10-04→`1791072000000`、10-06→`1791244800000` |
| `suggested_node_slug` | （不落三元组） | 只在 `REBIND` 非空（`content_audit_2026-09-25.protocol.md:18`）；改绑动作属 `material_rebind.csv` 那条链，不是三元组字段 |
| `material_slug` + `current_node_slug` | 定位绑定 | 三元组挂在「该材料绑到 `current_node_slug` 的那条绑定」上——**前提是该绑定仍在**（见下） |

**`NONE` 行的落账读法（本轮独立复核发现的口径偏差，已修正）**：`verdict` 逐字直落，所以
CSV 的 `NONE` 语义就是字段的 `NONE` 语义。四轮实读：09-25 的 23 行 `NONE` 证据**全部**是
「留人工／需人工拆分／落点留人工／需人工决定建点·改绑·并点／交人工裁定」——**不是"该绑定
应删除"**；10-02 的 2 行是题干残片、10-06 的 20 行里 19 行是题干残片／依赖未收录的图，另 1 行
（`math-ncifangcha-gongshi`）是纯公式卡、包内无承接节点（属 §3 的用法②）。两类都落在 §3 的
`NONE` 定义里，但**读侧不得把 `NONE` 当解绑指令**；09-25 的 23 行里已有 **6 行**（按"最终效力
轮"计：10-02 2 行、10-04/10-06 4 行）在后续轮次被改成 `KEEP`/`REBIND`，证明 `NONE` 不是终局。

**落账前提：被判的那条绑定必须还在现包里**。三元组描述"这条绑定被如何裁定"，所以
`(material_slug, current_node_slug)` 必须**仍是**该材料在当前包里的绑定。实测（19 卷真实
`moe-2025-teaching-support-v2-*.json` 的绑定集 × 四张表）：6,365 行里 **445 行**的
`(material, current_node_slug)` 已不存在——09-25 **63** 行（REBIND 56 / KEEP 6 / NONE 1）、
10-02 **148** 行（REBIND 109 / KEEP 39）、10-04 **234** 行（**全部 REBIND**）、10-06 **0** 行
（改绑已执行、或后续轮次把材料改到了别处）。这类行**不可落账**（裁定针对的绑定状态已消失），
**也不得把裁定搬到新绑定上**（那是发明裁定）：写侧必须把它们记进"未落账/需重裁"清单
（当前不存在这张清单，登记 §7.4），或让后续轮次的重新裁定按覆盖规则自然取代。

**多轮重复裁定必须先定覆盖规则（写侧待办）**：实测 237 条材料在 >1 轮里被裁定，
其中 175 组 `(material_slug, current_node_slug)` 被重复裁定（09-25↔10-06 重叠 54 条材料，
10-02↔10-06 重叠 98 条）。三元组只有一个位置，必须定「后轮覆盖前轮」（`judgedAtEpochMillis`
即轮次日，天然可比较）还是「拒写冲突并报出」。**当前仓库没有"生效裁定"合并表**——
`merge_content_audit_verdicts.py` 只并 09-25 一轮的 8 个切片，后三轮没有生成器；
写侧要落账必须先产出这样一张按 `(material, 节点)` 去重、带轮次时间戳的表。

### 4.2 `material_rebind.csv` → 已执行改绑（**不能写 `REBIND`**）

表：`tools/kb_build/tables/material_rebind.csv`，4 列
`material_slug,from_node_slug,to_node_slug,evidence`（`tools/kb_build/rebind_materials.py:28-29`），
实测 **1585 行 / 1569 条不同材料**。语义 = **已执行的改绑动作**（由
`tools/kb_coverage/apply_rebind_verdicts.py:18` 追加 REBIND 行，`rebind_materials.py --write` 生效），
它没有 verdict 列——**行是"改绑动作"，不是"该改绑"这个裁定状态**。

**词表语义先决（本批独立复核发现的原映射错误，已修正）**：`REBIND` 的含义是「**该**改绑」
（`ProblemCatalog.kt:206` "marks it as due for a different knowledge point"，§3），描述的是
**被判定的那条绑定**（仍指向 `from_node_slug` 的状态）。改绑一旦执行，材料已绑到
`to_node_slug`，那条被判定的绑定在现包里**已经不存在**——把 `REBIND` 写在新绑定上等于说
"新绑定该改绑到别处"，与事实相反。实测（同 §4.1 的绑定集）：09-25 表的 56 条 REBIND 行里
**56 条**的 `current_node_slug` 已不再被该材料绑定、**56 条**的 `suggested_node_slug` 已
被该材料绑定；`material_rebind.csv` 的 **1585 行 `from_node_slug` 全部**已不再被绑定、
1568 行的 `to_node_slug` 已是现绑。即：这张表 100% 是"已执行"，没有一条还能承载 `REBIND`。

映射（写侧照此落账）：

| 输入 | 字段 | 说明 |
|---|---|---|
| （行存在） | `verdict`：**不许写 `REBIND`** | 新绑定（`to_node_slug`）的诚实写法只有两种：**不写三元组**（保持"未裁定"，改绑事实留在本表 `evidence`），或写 `verdict=KEEP`（绑定成立）+ `verdictSource=MIGRATION`（迁移期补登，从 `from_node_slug` 改来的理由留在 CSV 的 `evidence`） |
| （无此列） | `verdictSource` | 若走 `KEEP` 路线 → `MIGRATION`（这是脚本补登，不是新一轮读正文裁定）；`MODEL_AUDIT` 只属于"读正文判出 REBIND"的那次裁定本身 |
| （无此列） | `judgedAtEpochMillis` | **该表没有时间戳列**：不许填 `now()`（时间戳会随每次重跑漂移、不可复算）。写侧必须从轮次/git 历史取"该行进入本表的时间" |
| `from_node_slug` | （历史目标） | 它是"被判错绑时的位置"，**不能**作为三元组落点（那条绑定已不在现包里） |

**「不写」与「`KEEP`+`MIGRATION`」二选一由 D-5 写入窗口钉死并回写本节**（当前未定，
登记 §7.5）。若写侧想要"这条绑定经历过一次改绑"的历史语义，那是新词表值/新字段的事，
需要再 bump `schemaVersion`（§2），不能借用 `REBIND`。

**`to_node_slug` 也已不是现绑的 17 行同样不可落账**（本轮复核复算：`from_gone` 1585 /
`to_bound` **1568** / `to_gone` **17**，占 1.1%）。这 17 行的材料仍在包内、当前绑在别的
节点 slug 上（未逐行追因；已排除"节点改名"解释——17/17 的 `to_node_slug` 仍存在于现行节点清单，
`point_rename.csv` 无对应改名映射）。它们连"新绑定"都没有落点，与 §4.1 的 445 行同法记进
未落账/需重裁清单（§7.4），**不得照 `evidence` 猜一个落点**。

**两源合并必须先排序**（实读重叠）：1569 条材料里 729 条也出现在审计轮次中——其中
457 条在审计里是 `REBIND`、**316 条是 `KEEP`**、9 条是 `NONE`（三档**非互斥**：53 条材料
跨 ≥2 档，故 457+316+9=782 > 729，不是划分）；只有 386 组
`(material, from_node)` 与审计行的 `(material, current_node)` 重合。也就是说
「改绑表」与「审计表」看的是不同时间的同一个绑定，**谁覆盖谁必须按时间定，不能按文件顺序**。

### 4.3 其它裁定件（不进 v3 主映射）

- `tools/kb_build/tables/binding_suspects_reviewed.csv`（204 行，列
  `subject,material,current_node_slug,verdict,node_slug,evidence,slice`，REBIND 183 / KEEP 21）：
  与 `content_audit_*` 同形态，可作 `MODEL_AUDIT` 来源；但它没有轮次日期列，时间戳同样要外部取。
- `content_audit_anchors.csv`（6 行，列 `material_slug,registered_node_slug,register_ref,note`）：
  **没有 verdict 列**（登记册 I-04 锚点），不映射。
- `build/kb-staging/binding_verdicts.csv`（未入库产物，`build/` 被 `.gitignore:14` 忽略；
  实测 589 行，列 `material_slug,verdict,target`，取值**小写** `keep` 320 / `bind` 232 /
  `unbind` 37）：
  **legacy 小写词表，与 v3 枚举不同名**。禁止静默翻译（`unbind` 是否等于 `NONE` 需要裁定）——
  要用它必须显式落一条映射裁定并记进本契约。

## 5. 两条交接

### 5.1 KB 写侧需在 promote 门加 binding 键集守卫

**现状（实读）**：唯一会因"绑定多一个键"而拒包的，是 Kotlin codec
（`ReviewedTeachingMaterialSidecarJsonCodec.kt:185-189`）。
Python 晋升门 `kb_build.promote`（`tools/kb_build/promote.py:1-30` 的链路：23 道内容门
`gate.evaluate` → 契约镜像 `check_pack_contract.evaluate` → `roundtrip.run`）**没有一道校验绑定键集**：
`gate.py:329-333` 只读 `b["knowledgeNodeId"]` 做计数；`roundtrip.py` 只证"可规范重放"；
`check_pack_contract.py` 只查材料↔节点一致性，且那条是**报告型计数不参与红/绿**（`:393`）。
实测 grep `unknown key|extra keys|unexpected key` 在 `tools/kb_build/`、`tools/kb_coverage/` 零命中。

**缝隙**：「staging 里手写/工具写坏一个绑定键 → promote 全绿 → 装机时 codec 整包拒 →
主页横幅常驻」。**要求**：在 promote 链路里加 binding 键集守卫（放 `check_pack_contract.py`
的契约镜像一节最省事，因为它是"Kotlin 门的 Python 下限"）：

1. schemaVersion ≤ 2：每个绑定键集**恰好** `{knowledgeNodeId, role}`；
2. schemaVersion == 3：键集 ⊆ `{knowledgeNodeId, role, verdict, verdictSource, judgedAtEpochMillis}`，
   且 `{knowledgeNodeId, role}` 必在，三键 all-or-none；
3. 取值词表与 §3 一致（`verdict`/`verdictSource`），`judgedAtEpochMillis` 为非负整数。

（这项属 KB 线，本批只交接、不改 `tools/kb_*`。）

**同族第二条缝隙（批 2 drill 实测）**：材料侧车**必须自带来源**。
codec 允许材料引用包内来源（`pack.sources + sidecar.sources`），但调和器的材料契约只拿
`command.teachingSources` 去匹配（`RoomKnowledgeContentReconciler.planMaterials`，
`RoomKnowledgeContentReconciler.kt:371-376`）——用包内来源的材料会在调和期被计入
`skipped`（"Every teaching material needs a reviewed source"）、材料不进库，而 Python 门照样全绿。
写侧落账时材料来源要走侧车 `sources`（真实侧车一直是这么写的），不要借包内课程来源。

### 5.2 真实包落账必须走 D-5 写入窗口（`materialize.py` 现硬编码 v2）

- `tools/kb_coverage/materialize.py:173` 与 `:203` 硬编码 `"schemaVersion": 2`
  （新卷与空卷两处）。**直接让现有工具写入带三元组的绑定会产出 v2 侧车 → 整包拒**。
  落账前必须先把 `materialize.py` 的输出版本升到 3（或走 D-5 写入窗口的专门通道）。
- **改绑工具会留下"指向旧目标的裁定"**：`rebind_materials.py:76` 只改
  `knowledgeNodeId`（`:77-84` 再做去重），不碰三元组。所以若绑定已带三元组，
  之后跑一次改绑会得到「新目标 + 针对旧目标的 `KEEP/REBIND`」这种失真的组合；
  去重逻辑还可能**整条丢掉**带三元组的重复绑定。
  **写侧规则（建议，落账前钉死）**：改绑一条带三元组的绑定时，必须显式重写三元组
  ——要么清空（回到"未裁定"）、要么由裁定链给出针对新目标的新裁定；
  不允许让 `rebind_materials.py` 原样搬字段。
- **装机快路径会让"只写侧车、不 bump manifest"的落账静默失效**（本轮复核发现）：
  `BundledKnowledgeBaseInstaller.kt:43-51` 在 `recorded.contentVersion == manifest.contentVersion`
  时直接返回，**不解析也不调和**；逐包同口径（`:98-101`）。把三元组写进真实侧车但
  不同时 bump `moe-2025-update-manifest.json` 的 `contentVersion`，字段在真机上等于没发生。
  落账清单必须把「改侧车 + bump contentVersion」当成一个动作。
- 真实包落账后，3D 的验收门（阶段 5 前）才可能真正通过；本轮交付只到
  **载体 + 端到端 drill + 本契约**。

## 6. 内核侧已立的证据（给写侧的回执）

- **codec v3 + 严格性**：`ReviewedTeachingMaterialSidecarBindingVerdictTest`
  （v3 三元组解析/all-or-none/词表/负时间戳/未知键拒 + v1/v2 键集逐字不变）。
- **端到端 drill**（本批新增，仪器化）：
  `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/BindingVerdictEndToEndDrillInstrumentedTest.kt`
  —— 合成 v3 侧车文本（真实 `moe-2025-four-subjects-v1` 包体 + 真实原子点 id +
  一条合成教学活动来源；同一材料上「已审计 KEEP/MODEL_AUDIT + 未审计」混排）走真实
  `BundledKnowledgeBaseInstaller.reconcile` → `applyKnowledgeContentUpdate` →
  `replaceMaterialBindings`，断言：
  ① 解析→包校验→调和全绿（进度表 `skippedCount==0`）；
  ② 字段在包级可见（`KnowledgeBasePack.teachingMaterialBindings` 上三元组逐字段对；
     未审计绑定三字段 null）；
  ③ **DB 端如实不含**：绑定表列集恰好 `material_id/knowledge_node_id/role`、
     `SELECT verdict` 直接 `no such column`、App 读回路径三字段皆 null。
- **落库边界**：绑定表只有三列（`KnowledgeTeachingMaterialEntities.kt:63-91`），
  写库映射只搬三列（`RoomKnowledgeBaseStore.kt:738-748`），调和器
  `replaceMaterialBindings` 也只写三列（`RoomKnowledgeContentReconciler.kt:391-407`）。
  「落库即丢弃」是**已裁定的边界**（无消费方 → 零 schema），不是事故。

## 7. 登记（未落项）

1. **DB 落库**：三元组不落 `knowledge_teaching_material_node_binding`（待消费方出现时再开 schema）。
2. **Python 键集守卫**：promote 链路的 binding 键集门（§5.1）——KB 线。
3. **字段真实填充**：真实包/侧车尚无任何三元组（§5.2）——KB 线写入窗口 D-5。
4. **"生效裁定"合并表**：多轮重复裁定覆盖规则（§4.1，237 条材料跨轮）+ 「判决绑定已不存在」
   的 445 行未落账/需重裁清单（§4.1）——写侧落账前必须先产出，否则这 445 行会被静默丢掉
   或被搬到错误的绑定上。
5. **`material_rebind.csv` 的落账形态**：「不写三元组」与「`KEEP`+`MIGRATION`」二选一（§4.2），
   以及该表**时间戳来源**（表里无时间戳列）——D-5 写入窗口钉死后回写 §4.2。
6. **`REBIND` 的历史语义**（若确需）：需要新词表值/新字段 → 再 bump `schemaVersion`（§2）。
