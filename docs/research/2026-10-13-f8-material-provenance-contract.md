# F8·③ 材料侧 AI/provenance 字段 · 写侧契约（2026-10-13）

> 依据：台账裁决 F8「AI 标识」——**① UI 最小形态（归智能体线）+ ③ 材料 AI/provenance
> 字段随下次包重建（归 KB 线）**；法规口径以官方《人工智能生成合成内容标识办法》为准
> （`docs/agent-first-refactor-decisions-2026-09-23.md` **工作树版** :2502；该段属未提交
> 改动，引用以工作树为准）。承 D-0 绑定裁定三元组的同族体例：
> `docs/research/2026-10-07-d0-binding-verdict-contract.md`（本文的版本门、all-or-none、
> 「词表与键集以 codec 实测行为为准」三条纪律照搬）。
>
> 状态：**字段载体与写侧已落**（Kotlin codec v4 + Python 单点注入 + 一次性注入器 +
> 契约镜像下限），**staging 候选族已全量升 v4 并带键**（23 卷 / 59,017 条材料，实测见 §5.4），
> **成品目录未 promote**（发布窗口动作，见 §6.3）。
> 本文档是给所有写侧/读侧的口径；**词表与键集以本文档 + Kotlin codec 为准**，两者不一致时
> 以 codec 的实测行为为准并回来改本文档。

## 1. 字段在哪、长什么样

位置：教学材料侧车 `materials[]` 的**每个材料对象**上，三个**必填**键。

| 键 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `contentAttribute` | string（闭词表，见 §3） | **v4 必填** | 越界即整包拒 |
| `contentProvider` | string | **v4 必填** | trim 后非空（写入侧口径：未上架前 = 该卷 `packId`） |
| `contentId` | string | **v4 必填** | **必须等于该材料自己的 `slug`**（键在但取值说谎 → 整包拒） |

- **必填组（不是可选三元组）**：v4 下三键缺一即整包拒（报
  `Teaching-material sidecar is missing keys: [...]`）。与 D-0 的绑定裁定三元组有意不同——
  那条是「可选 all-or-none」（描述一次裁定，没裁定就不该有字段），本案是法规的
  「文件必须带标识」，所以**没有"未标识"的合法 v4 形态**；半写的三键（缺一/缺二）除了
  "缺键"外没有第二种读法，因此不需要额外的 all-or-none 报错分支（D-0 那条分支是为了
  区分"从未裁定"与"裁定了但字段丢了"，v4 下前者不存在）。
- 键序：落盘键序固定为 `slug…reviewedAtEpochMillis, contentAttribute, contentProvider,
  contentId, bindings`（三键插在 trailing `bindings` 之前）。写入侧与注入器逐条复核该键序
  （`new_content.MATERIAL_SIDECAR_KEYS` 是唯一常量：作者 13 键 + 三键，见 §5.1）。
- 三键描述**材料正文**的来源，不给材料增加任何评测/评分/排程权威（材料仍只作为
  已给学生题目的讲解上下文）。

实现（读写两侧，均已落）：
- 键集与版本门：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/ReviewedTeachingMaterialSidecarJsonCodec.kt:61-70`
  （`LEGACY=1`、`PREVIOUS=2`、`VERDICT=3`、`CURRENT=4`、accepted `{1,2,3,4}`）、
  `:78-79`（三键与既有 13 键）、`:146-156`（v4 键集分支 + 取值校验）、
  `:204-213`（`contentId == slug` / 词表 / provider 形状）。
- 词表：`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ProblemCatalog.kt:243-245`
  （`KnowledgeMaterialContentAttribute.AI_SYNTHESIZED`）。
- 写侧单点：`tools/kb_build/material_provenance.py`（口径本体）、
  `tools/kb_coverage/materialize.py:187,196,299`、
  `tools/kb_build/apply_authored_materials.py:193-200`、
  `tools/kb_build/new_content.py:64`。
- 一次性注入器：`tools/kb_build/backfill_material_provenance.py`。
- Python 契约镜像（promote 读它判红绿）：`tools/kb_build/check_pack_contract.py:556-566,590`。

## 2. schemaVersion：v3 → v4

- 侧车 `schemaVersion` 是**内容格式版本**，与 DB schema 无关（本轮零 schema；不动
  `core/database`）。
- codec 现接受 `{1, 2, 3, 4}`，当前版本 4（`ReviewedTeachingMaterialSidecarJsonCodec.kt:61-70`）。
- **v≤3 不许含三键**：v1/v2/v3 走同一个 13 键键集分支（`:151`），三键是未知键 → 整包拒
  （报 `contains unknown keys: [contentAttribute, contentId, contentProvider]`）。
  「v2 包里混进三键」不是"旧读者忽略新字段"而是解码失败——与 D-0 §2 同一条纪律。
- **v4 必含三键**：`:146-148` 把 13 键 + 三键一起交给 `requireOnlyKeys`（既查未知键也查缺键）。
- **v4 延用 v3 的绑定轴**：v3 的绑定裁定三元组在 v4 下照旧允许、键集规则逐字不变
  （`:223` 的分支条件是 `schemaVersion < VERDICT_SCHEMA_VERSION(=3)`，v3/v4 同路）。
- 跨版本含义：v4 包在**旧 codec**（只收 1/2/3）上整包拒——App 未发布故无现实风险；
  写侧只要写三键，就必须把同一份侧车的 `schemaVersion` 一起写成 4（写 3 会被自己的门拒）。
- **扩词表（§3）需再 bump `schemaVersion`**：新取值会被当前 v4 reader 当未知取值拒，
  语义上等于一次格式变更（`ProblemCatalog.kt:236-241` 的 KDoc 已写明）。
- `contentProvider` 的**取值**替换（上架前核定正式服务提供者编码）**不是**格式变更：
  键集与类型都不动，因此**不 bump 版本**，只重走出记录入 + promote（见 §6.2）。

## 3. 词表与取值（机械可派生真值）

`contentAttribute ∈ {AI_SYNTHESIZED}`（`ProblemCatalog.kt:243-245`）

| 值 | 语义 | 为什么是它 |
|---|---|---|
| `AI_SYNTHESIZED` | 正文由 AI 生成合成（模型判定 + 改写产出） | 包内材料的 `derivationKind` 全部是 `REVIEWED_SYNTHESIS`（模型判定通道改写、重判替换、作者型材料都由模型/判定链产出），这是机械可推导的真值；**扩词表**（例如将来出现人工撰写材料）要新值 + bump schemaVersion，不许把非 AI 材料写成这个值 |

`contentProvider`：稳定服务提供者编码。**未上架前的取值 = 该卷 `packId`**
（`moe-2025-four-subjects-v1` / `moe-2020-foundation-v1`）——这是此刻唯一"机械可派生且不说谎"的
标识；上架前核定（备案/商店主体编码）后**整族替换**（见 §2 与 §6.2）。写入侧从
`doc["packId"]` 取，不引入任何新输入；codec 只做形状校验（trim 后非空），不钉具体值——
这正是为了上架前替换时零格式变更。

`contentId`：**= 材料 `slug`**。slug 是材料在包内唯一的稳定编号（codec 由它派生
`materialId`/`stableCode`），取它才能让 codec **交叉核对**（`:204-213`）：键在但编号说谎的包
照样被拒。这也让 F8 三键全部可由既有字段推出，作者侧零新增输入。

## 4. 法规依据（逐条引用）

《人工智能生成合成内容标识办法》（2025-09-01 施行；来源：`gov.cn/zhengce/zhengceku/202503/content_7014286.htm`
与 `cac.gov.cn/2025-03/14/c_1743654684782215.htm`——本批由委派方从官方网站核实后给出，
本文逐条引用其要点）：

| 条 | 要点 | 与本字段/本项目的关系 |
|---|---|---|
| 第 3 条 | 显式标识 / 隐式标识的定义 | 本字段属**隐式标识**（文件元数据层）；显式标识是另一条线（见 §6.4） |
| 第 4 条 | 文本应在起始/末尾/中间适当位置加文字提示或通用符号，交互场景在界面/文字周边显著提示；提供下载/复制/导出功能时文件须含显式标识 | **本批不落**（属显式标识与导出出口，见 §6.4，随 KD-29 登记） |
| 第 5 条 | 文件元数据隐式标识应含「生成合成内容属性信息、服务提供者名称或者编码、内容编号等制作要素信息」；鼓励数字水印 | **本条是 v4 三键的直接依据**：`contentAttribute`（属性）/`contentProvider`（提供者编码）/`contentId`（内容编号）。数字水印未落（§7.4） |
| 第 8 条 | 用户服务协议应说明标识的方法与样式 | 未落（产品合规面，随 KD-29，见 §6.4） |
| 第 9 条 | 用户申请无显式标识内容的处理与日志留存 | 未落（同上） |
| 第 14 条 | 施行日期（2025-09-01） | 已施行；本批是该办法在**随包材料**上的落点 |

> **不声称合规**：本批只把第 5 条的元数据三要素落到随包材料上（隐式标识的一部分）。
> 显式标识、导出出口、用户协议、申请处理与日志留存的**全面合规面**未落，随 KD-29
> 「上架前重开」登记（`docs/known-defects.md` 的 KD-29 条目；本批不改它的状态）。

## 5. 落地

### 5.1 写侧单点注入（新材料出生即 v4）

- 口径本体：`tools/kb_build/material_provenance.py`——键名、词表、派生
  （`provenance_fields(pack_id, slug)`）、键序（`sidecar_material_keys`）、整卷升级
  （`ensure_doc`）、族检查（`doc_problems` / `index_problems` / `family_names` /
  `family_problems`）。四个调用点共用一份（消除的失败：写侧各自手抄三键 → 与 codec 的
  钉死键集漂移 → promote 未必红、装机整包拒）。
- 判定通道：`materialize.py` 追加材料时按 `**prov.provenance_fields(doc["packId"], slug)`
  落三键（`:299`）；新卷/空卷占位用 `prov.SCHEMA_VERSION`（`:187,221`）。
- **装载即升级**：`_sidecar_state()` 读入每一卷后立即 `prov.ensure_doc`（`:196`）——本模块
  写出的卷必须自洽（卷声明 v4 且卷内**既有**材料一并补齐三键），否则会产出"卷 v4、老材料
  没键"的自相矛盾卷（codec 整包拒）。副作用是顺带的修复能力：任何走 `_sidecar_state` 的
  写入工具（`materialize`、`apply_rejudged_materials`、`merge_candidate_verdicts` 的读取面）
  都不会再把"半升级的卷"写回去。
- 作者型材料：`apply_authored_materials.py` 同一份派生 + 落盘前逐条复核
  `tuple(emitted) == nc.MATERIAL_SIDECAR_KEYS`（`:197-200`）。
- 作者入口**不变**：`knowledge-production/**/materials.jsonl` 仍是 13 键；
  §3 的三键全部机械可派生，要作者手抄只会多一处抄错的机会。
  `new_content.MATERIAL_SIDECAR_KEYS`（`:64`）是"作者 13 键 ↔ 侧车 16 键"的同步常量，
  由 `tools/tests/test_kb_material_provenance.py` 钉死。
- 只写 staging：全部走 `pack_io.dump_json`（工作目录），成品目录仍只由 `promote.py` 写
  （`tools/tests/test_kb_path_guard.py` 的纪律不变）。

### 5.2 一次性注入器（既有 23 卷）

`tools/kb_build/backfill_material_provenance.py`：

    python tools/kb_build/backfill_material_provenance.py --dry-run
    python tools/kb_build/backfill_material_provenance.py --write

- 覆盖族：索引列出的 22 卷（`moe-2025-teaching-support-v2-01..22`）+ **直挂的历史样例卷**
  `moe-2020-teaching-support-v1.json`（不在索引里，由 Kotlin
  `BundledKnowledgePackResources.teachingSidecarsByPack` 直挂；Python 侧没有第二处镜像，
  所以族枚举在 `material_provenance.LEGACY_SIDECAR_NAMES` 里显式登记，漏了它就漏一族材料）。
- v1 → v4 的**来源条目**升级（v4 走 v2+ 来源键集，必须带四枚政策键）：按契约唯一合法值补齐
  ——`REFERENCE_ONLY` 的许可形态下 `contentUsePolicy` **只允许** `REVIEWED_SYNTHESIS_ONLY`
  （`core/database/.../KnowledgeBaseImportContract.kt:112-119` 的强制规则，不是选择）；
  `licenseExpression`/`licenseUri`/`attributionText` 取 `null`（v1 条目没有这三样事实，
  不编造文案；契约允许 non-direct-reuse 分支取 null，`:135-147`）。许可状态不是
  `REFERENCE_ONLY` 的 v1 来源**拒**（推不出真值不猜）。
- 幂等 + fail-closed：已合规的卷零改动（重跑逐字节一致）；半写三键 / 越界取值 /
  `contentId ≠ slug` / 版本不认识 / v1 来源推不出政策 → **整批拒**（不留半升级的族）；
  写盘后复算（全 v4 + 材料 100% 带键 + 索引一致）不达标即抛。

### 5.3 Python 下限（promote 门）

`check_pack_contract.material_provenance_gate()`（`check_pack_contract.py:556-566`）进了
`evaluate()` 的 consistency 节（`:590`），与 F9 的 material_bindings 同判红/绿、同被
`promote.run_checks` 读取。口径**只与 codec 同宽**：v2/v3 卷不因"没升 v4"变红（codec 照收、
装机不受影响），v4 卷必须键齐值正，索引与磁盘必须对得上。消灭的失败是
「staging 里某卷写坏三键 → promote 全绿 → 装机时 codec 整包拒 → 主页横幅常驻」——
codec 对越界是整包拒、没有单条跳过，下限必须与它同宽。
覆盖面**只限材料层的 F8 规则**（键集/版本/三键取值/索引一致）：codec 的其它材料级规则
（空卷至少 1 条材料、来源键集、绑定键集等）不在本批下限内——其中绑定键集一条登记在
D-0 契约 §5.1/§7.2，其余仍是既有的"promote 不查、装机才拒"缝隙。

### 5.4 交付态与对拍（实测，2026-10-14）

- **通道选择对拍（先对拍后选）**：`materialize --write` 到 scratch（只给包 + 索引，从判定表 +
  块池全量重建）与现盘逐文件对拍——**相同 0 / 不同 22**：卷数 19 vs 22，各卷材料集互不相交
  （卷 01 实有 3,235 / 建有 1,919、交集 0；卷 20–22 没重建出来），索引不一致。
  判定表不是现盘内容的完整来源（改绑、重判替换、作者型材料都不在表里）→ **重生成会整体
  回退**，故落地通道 = 一次性注入器（就地升级，不动材料正文/绑定/时间戳）。
  对拍脚本与日志：`build/f8-scratch/compare_materialize.py`、`compare.log`（`build/` 不入库）。
- **注入结果（独立复算，直读磁盘）**：族 23 卷、材料 **59,017**、带齐三键 **59,017/59,017**、
  v4 卷 **23/23**、材料键序不符 **0**、索引一致 `[]`、族下限问题 `[]`；`--write` 重跑
  **写入 0 卷**（幂等）。
- **与成品目录（注入前镜像）比对**（归一化剥掉三键/版本后）：逐字相同 49,834、逐字不同
  9,176、仅成品有 0、仅 staging 有 7；**差异字段分布 = `bindings` 9,176 处、别无他**——
  即 staging 与成品的既有差异全部是**别轮已生效的改绑**（staging 领先成品，属两段拓扑的
  预期态，D-5），本批注入**没有改动任何材料正文/绑定/时间戳**。
- **门（2026-10-14 实跑，贴退出码）**：
  - `python tools/kb_build/promote.py --dry-run` → **exit 0**（`gates` / `consistency` /
    `roundtrip` / `manifest` 四节全 OK；consistency 含本批新增的 `material_provenance` 节，0 问题）；
  - `gradlew.bat -p . :core:data:testDebugUnitTest --tests "*Sidecar*" --rerun` → **exit 0**；
    三个侧车用例类 21 条全绿（`ReviewedTeachingMaterialSidecarBindingVerdictTest` 10、
    `ReviewedTeachingMaterialSidecarProvenanceTest` 10、`TeachingSidecarIndexTest` 1）；
  - F8 相关 Python：`test_kb_material_provenance.py` / `test_kb_materialize.py` /
    `test_kb_apply_authored_materials.py` / `test_kb_new_content.py` / `test_kb_build.py`
    → **exit 0**（79 passed、62,467 subtests passed）。
- **全量 Python 套件的现状（如实说）**：`python -m unittest discover -s tools/tests -t tools`
  → `Ran 722 tests`、**FAILED (failures=5)**：`test_rebind_idempotent_on_shipped`、
  `test_dropping_an_alias_row_turns_red`、`test_dropping_a_prereq_row_turns_red`、
  `test_dropping_a_point_merge_row_turns_red`、`test_current_tables_agree_with_pack`。
  这 5 条都在表↔包（别名/前置/并点/改绑表）路径上，本批没有改任何表、包或
  `check_table_consistency`；判据证据：① 真 staging（本批的晋升候选）上各节问题数全 0
  （含新节），② 这几张表都有他轮未提交改动（`git diff --stat`：alias_map 18 行 /
  prereq_map 27 行 / point_merge +1 行 / material_rebind +15 行），
  ③ 那些用例拿**成品目录**的包与**工作树**的表比（两段拓扑下本就会分叉，D-5 预期态）。
  本批为**归因推断**（没有动它们、也不在改动路径上），未做"回退本批代码再跑"的基线实验
  （共享工作树不宜 stash）。登记 §7.6。

## 6. 边界登记（如实说，不声称合规）

1. **不落 DB**：三键不进 `KnowledgeTeachingMaterialRecord`、不进 Room 表、不加列
   （本批不动 `core/database`）。codec 校验后即弃——材料侧车文件本身就是"隐式标识"的
   载体（法规要求的是文件带标识），App 内暂无消费方。将来要在 UI 上展示/在导出物里携带，
   是"出现消费方再开 schema/列"的另一批（与 D-0「落库即丢弃」同族）。
2. **`contentProvider` 上架前核定**：当前值 = 包标识（`packId`）；上架前必须核定正式服务
   提供者编码并整族替换。替换是**内容变更**（键集/类型不变 → 不 bump schemaVersion），
   要走"注入器（或专门替换脚本）+ promote"。
3. **成品目录未 promote**：本批交付态是 **staging 候选族**（§5.4）；`core/data/src/main/
   resources/knowledge/` 仍是 v1/v2 族（codec 向后兼容照收）。推进成品 = 发布窗口动作
   （`promote.py`，会同时 bump 台账 version/contentVersion），本批未做（staging 还含
   别轮在飞的改绑，见 §5.4）。
4. **显式标识与导出出口（第 4 条）、用户协议（第 8 条）、无显式标识申请处理与日志（第 9 条）
   未落**——属产品全面合规面，随 KD-29「上架前重开」登记；①UI 最小形态归智能体线（本批不评）。
   本批**不声称合规**。
5. **v1 历史样例卷的来源政策键取值是派生值**（§5.2），不是新一轮来源裁定；若要改这些来源的
   真实授权形态，走来源登记流程重写。

## 7. 登记（未落项）

1. **成品 promote**（§6.3）：F8 字段进随包资源是发布窗口动作。
2. **真机/仪器化走查**：本批只跑了 Python 门 + Kotlin 单测；`BundledKnowledgeBaseInstaller`
   在 v4 族上的端到端 drill（v4 版 `BindingVerdictEndToEndDrillInstrumentedTest` 同族）
   未跑（无模拟器）。
3. **D-0 §5.1 的 binding 键集 Python 守卫**仍未落（那是绑定键集，不是本批的材料键集；
   登记在 D-0 契约 §7.2）。本批只落了材料侧的下限（§5.3）。
4. **数字水印（第 5 条"鼓励"）未落**。
5. **KD-29 状态**：本批不改（§6.4）；「上架前重开」时按本文档 §5/§6 逐条对照。
6. **全量 Python 套件的 5 条失败**（§5.4）：在表↔包路径上，本批未触碰；归属他轮的在飞表改动
   （别名/前置/并点/改绑）与"成品包落后于 staging"的两段拓扑预期态。**未做基线实验**
   （共享工作树不 stash），故本文只写"归因推断 + 三条判据"，不写"证明与本批无关"。
   这 5 条应由持有那些表的轮次在各自收口时处理。
