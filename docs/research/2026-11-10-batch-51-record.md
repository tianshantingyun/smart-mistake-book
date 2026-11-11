# 批次 51 记录（2026-11-10）

> 只追加，不改他人小节。每节注明子线、产出、实跑与未跑。

## R1 · 模型切题评测口径与提示词（研究）

- 产出：`docs/research/2026-11-10-split-metric-prompt.md`；探针原样输出
  `docs/research/evidence/2026-11-10-batch-51/r1/probe-output.txt`。
- 回答了计划项 4 的四个问题（a 单题页 PASS 计分 / b NEED_MORE_IMAGE·RECAPTURE 折算 /
  c 提示词五模式 / d 不在 acceptance 调参），给出**口径 v3 规格**与**先红后绿用例**。
- 三条会改变预期的读数（均本会话实算）：
  1. 口径 v3（单题页只在"被接受的不拆分判定"下记一致）把批次 50 ① 从 `7/24=0.2917`
     压到 `6/24=0.2500`；批次 48 `10/24` 不变。**纠正假阳性，不是提分**。
     设备导出无 `decision` 列时回落 v2 规则并计 `singleQuestionUnverifiedCount`
     （历史 `4/7` 不变；严格规则只在带判定的 harness 导出上生效）。
  2. 弃答（NEED_MORE_IMAGE/RECAPTURE）在题数口径下记 `null` 并单列，进 ① 按不一致计
     （批次 50 弃答 `2/24=8.33%`，通道不可评 `5/24=20.8%`）。
  3. acceptance 8 页的 ① 是 8/8 全或无；建议新 v3 切片 **n≥20**（18/20=0.9000 恰过）
     ——样本设计，不动 0.9/0.05。
- 提示词侧硬约束（官方文档实读）：Chat Completions 的 `response_format.type` 只有
  `text`/`json_object`，**没有 `json_schema`**；JSON 模式要求提示词含 "json" 且有示例，
  当前 567 字符提示词两者皆无（示例缺失）→ 顶层 `type` 键回显的最可能来源。
- 实跑：当前口径探针（单题页 + ERROR ⇒ 记一致，① = 2/2）、批次 48/50 新旧口径复算、
  6 条先红后绿用例（红 4 绿 2）、`test_split_gate.py` 61 tests OK、
  `check_eval_label_leakage.py --paths tools/split_golden` exit 0。
- 未跑：Gradle/设备/任何 provider 调用（红线：Gradle 不并发；研究阶段零出网）；
  Kotlin 提示词用例只给规格、未运行（红态来自源码逐字阅读）。
- 未改：阈值、分母、金标 v1/v2、解析器白名单、任何产品代码与开关。

## G1 · 3D 改绑（shipped pack）

- 输入：`docs/research/evidence/2026-11-09-batch-50/d1/errors.csv`（audit `B50-D1-2026-11-08`，43 条）。
  处置：39 条 REBIND→target；1 条 NONE 经主会话裁决覆盖为 REBIND
  （`ext-phy-2cea5a0fdb-045`：节点 `平抛运动与斜面的结合分析` 的 boundary 逐字含
  「与圆弧面有关的抛体运动」、alias 含「平抛无碰撞进入圆弧轨道的处理（分解速度）」，
  材料正文含「恰好沿切线方向进入」⇒ NONE 前提被否证；按「已处置、待新审计验证」记）；
  3 条 NONE（仅题干/章节标题罗列）→ DROP_MATERIAL。逐条见
  `evidence/2026-11-10-batch-51/3d-rebind/{dispositions,per-item-before-after}.csv`。
- 改绑：`rebind_materials.rebind()` 40 行 `rebound=40 skipped=0 bad_to=0`；
  `drop_materials --write` 3 条；改绑后孤点 1 个（`一种药物中间体-h-的合成路线如下`，
  纯题干残渣、无材料）按 `delete_points --write` 删除并写 DELETE 退役台账。
- 包：主包 sha `10ede1bf…` → `aaf81be8…`；节点 3,280→3,279；材料/绑定 64,853→64,850
  （无悬空、无零绑定）；manifest version 28→29、contentVersion `ce8edb8f…`→`5f164dab…`；
  staging == shipped 逐字节。`diff_pack_staging` 92 处 = 40 绑定 + 3 材料 + 1 点 + 48 节点别名，
  主题树 0 增删/0 换序。
- 影响面闭合：别名按 `rebuild_aliases` 同一判据只改受影响 48 节点（+24/-25，排除 6 个改前
  已存在漂移节点）；台账同步 `material_rebind +40`、`material_bindings` 2 行、`point_delete +1`、
  `alias_map +24/-26`、`chapter_by_node -1`（全 LF）。详见 `3d-rebind/README.md` /
  `alias-closure.json` / `table-deltas.json`。
- 完整性门（实跑）：`check_pack_sync.py` exit 0；`check_asset.py` 11/11 exit 0；
  `run_kb_checks.py` gates/consistency/roundtrip/manifest/dense/judgment_drift 全 OK exit 0。
  dense 由 `export_bge_int8.py --skip-export`（44,117 行）→`pack_dense_asset.py`
  （`.vec` sha `ed866a62…`）→`gen_dense_device_fixture.py` 重生成，未手写 hash；
  `DenseRecallAssembly.VECTOR_ASSET_SHA256` 与布局/节点数 golden 取自实测。
- 单测：`:core:data:compileDebugKotlin` EXIT=0；18 个定向类 77 tests / 0 failures / 0 errors
  EXIT=0（8 KB 装载 + 5 dense + 5 检索/词面；`test-results.json`）。
- **回归读数（交 D-3）**：`stage3_device_sim.py` 端侧融合 116/129（批次 50 基线 117/129，-1）；
  离线 refFusedD1 118/129；丢失命中含 case#97 `超重与失重的判断方法`（词面 rank 2、融合 rank 0）。
  本阶段只记录，未改口径/阈值。
- 未做：设备/仪表化测试；全表 `material_rebind --write`（被 10 条改前已有失效行挡住，
  本次用同一 `rebind()` 的 40 行 scoped 输入）；新一轮 3D 盲判/复核。

## D-2 结构审计 + 抽样框冻结 + D-3 五红/召回（子线 3d-d2d3）

- 产出：`docs/research/evidence/2026-11-10-batch-51/3d-d2d3/`（README + 脚本 + 台账）；
  抽样框快照 `docs/research/evidence/2026-11-10-batch-51/3d-audit/frame/overfull_nodes.snapshot.csv`。
- **抽样框冻结**：快照 sha256 `ce7d6823…c69d9d`，15,892,450 bytes / 62,944 行 / 2,481 节点
  （源 mtime 2026-11-08，v28 时代）；`audit_d2_b51.py` 读前断言快照与 staging 原件同 sha，
  收工复核仍未变。**未重写 `build/kb-staging/overfull_nodes.csv`**；v29 复算另落
  `d2-overfull-recompute-v29-nodes.csv`（2,483 节点 / 62,969 条，与工具自跑读数逐位一致），
  差异登记 `frame-divergence.json`（掉 1 / 进 3 / 50 节点计数变化）——**D-1 若换框须先记新 sha/audit id**。
- **D-2 四项（零包写入/零绑定改动）**：节点近重复 0 组 0 对；候选区 2 组维持「保留长正文、退休未执行」；
  材料级 173 对 / 50 簇 / 拟退休 1 / SKIP 20（保护 37,536）；超配冻结框 2,481/62,944/53,020（RETAIN_NO_CAP，
  分诊 49/1,009/1,423），v29 复算 2,483/62,969/53,037；容量 91 行 22/12/44/13 逐行登记；type 三值外 0。
  工具测试 26 passed。新增登记：别名容量饱和 2,487/3,279 节点片段 >16（含纯名称+别名 1,284）。
- **D-3 五红**：5 类 12 tests / 0 failures / 0 errors（`five-red.log` + 5 XML，EXIT=0）。
- **D-3 召回未达 117**：端侧 116/129 = 0.8992248062015504（MRR 0.7174），JVM 融合 116/129
  （MRR 0.7211，MISS 13）；24 章逐章 diff 只有 1 章下降（物理必修第一册·第四章·牛顿运动定律 1/1→0/1），
  其余 23 章逐位相同。**未改阈值/索引/融合/内容**。
- **归因（隔离实跑）**：单题 case#97 翻转由该节点词面计数 7→4 单独造成（固定 v29 稠密分与候选域，
  只换回计数 7 ⇒ rank 7→4 命中）。链条：P1 正确改绑材料（title「超重与失重的判断（看加速度方向）」）
  → 别名闭合按「别名=材料标题」+1（40→41）→ 片段 70→72，16 片段位置平衡采样整体换批，
  丢掉含「加速/加速度/速度」的片段（v28 命中 7 特征 → v29 4）→ 词面 min-max 0.6364→0.3333、
  fused 0.8182→0.6667。KD-24 已逐字预言（`docs/kb-batch22-2026-10-26.md:190`）。
- **为什么不实修 117**：删别名会破坏「别名=材料标题」且 `rebuild_aliases --write` 会回加（不可持久）；
  改索引触 KD-24 移交边界（不改 `INDEX_VERSION`/不建第二套词面实验，且需三组数对照）；
  调 α 属调参。三条都需主会话/用户裁定，本线不静默执行——如实交付 116 + 可复算归因。
- 未跑：设备仪表化（本机 `adb` 不在 PATH，未起模拟器）；2h soak；全量单测。
  副作用如实登记：`stage3_device_sim.py` 重生成 `dense-device-order-reference.txt`（01:26，晚于 P1 01:07），
  重生成后稠密 3 类 14 tests 全绿（`d3/dense-tests.log`）。

## 3D 抽样准备 · B51-D1（子线 3d-audit）

- 产出：`docs/research/evidence/2026-11-10-batch-51/3d-audit/`（sampler/render/check/compile/recompute
  + JUDGE_PROMPT；`sample.csv`/`sample.jsonl`/`sample.sha256`/`manifest.json`；`judge_input/batch-01..32.json`
  + `compact-01..32.txt` + `shard_manifest.json`；`shards/batch-01..32.md`；`node_index.tsv`）。
- **audit id**：`B51-D1-2026-11-10`（不复用 `B50-D1-2026-11-08`）。
- **pack scope sha256**（改后 v29 实算；主包 + 索引 + 25 个 sidecar 按批次 50 同一口径汇总）：
  `31da98b597a2a32949d376b418b9ecad6b5494e3c068df6c36c8515ebc0bd820`；主包 `aaf81be8…`；
  节点 3,279 / 材料=绑定 64,850 / 悬空绑定 0 / 材料 slug 重复 0。
- **抽样框**：P2 冻结快照 `3d-audit/frame/overfull_nodes.snapshot.csv`
  （sha256 `ce7d6823787204640a646854561ea85cbbf4f19c48a0809754a3b0f419c69d9d`，62,944 行 / 2,481 节点；
  与 `frame/frame_manifest.json` 登记值逐字一致，脚本读前硬校验）。层规则：当前 v29 绑定所落 node_id
  ∈ 快照集合 → overfull（62,956）/ 其余 → normal（1,894）；快照节点在当前包缺失 0 个。快照为 v28 时代、
  层归属按指令沿用冻结框（v29 复算 2,483 节点只作 D-2 差异登记，不替换）。
- **样本**：层内 `sha256(seed|layer|material_slug)` 升序 + `start=sha256(seed|layer|start)[:8] mod floor(N/n)`
  系统选样（与批次 50 同规则）；`seed=sha256(pack_scope_sha|audit_id)=a4ee6c1f…cfe950`；
  overfull 1,300 + normal 300 = 1,600，固定、一次判读。冻结 sha：`sample.csv` `77eb9969…9eb1c8`、
  `sample.jsonl` `a994e5b6…0d30ef`（`sample.sha256` 同步落盘）。
- **分片**：32 片 × 50 条；分片顺序 `sha256(audit_id|'shard'|material_slug)` 升序（确定性混层），
  判定输入（`judge_input/batch-NN.json` 与 `shards/batch-NN.md`）不含 layer / pool / 全局序号；
  `shards/batch-NN.md` 写清机器输入路径 + sha256、`node_index.tsv`、`JUDGE_PROMPT.md` sha256、
  输出路径 `verdicts_raw/batch-NN.tsv` 与逐条输出要求（TSV 表头、50 行、引文逐字、REBIND 同科等）。
- **工具实跑**（本会话；全部 `python -I -X utf8`）：
  - `sample_d1.py` → audit id / scope sha / layers 62,956+1,894 / sample 1,600 / 32 shards / frame sha 如上；
  - `sample_d1.py --check` → `{"match": true, "expected": 1600, "actual": 1600}` exit 0；
  - `render_compact.py` → 32 md + 32 compact，exit 0；
  - 自检（只读）：sample.csv 1,600 行、layer 计数 overfull 1300 / normal 300；sample.jsonl 1,600 行且
    material_slug/i 唯一；32 片每片 50 条、并集=样本集；分片 JSON 无 layer/pool/i 字段；shard_manifest
    逐片 sha 一致；`node_index.tsv` 3,279 节点（同名 slug 5 组按 subject 区分）。
  - `check_raw.py` 冒烟（临时目录、非证据）：全 CORRECT 50 行 → problems 0 exit 0；
    构造坏引文 / 不存在 target / 跨科同名 slug → 精确报 3 个问题 exit 2，且同科同名 slug
    （BIOLOGY→基因工程）未被误报——验证 `(subject, slug)` 解析修正。
- **未跑**：`compile_judgments.py` / `recompute.py`（需 32 批真实判定，当前为抽样准备阶段）；
  Gradle / 设备（本阶段不需要，且红线禁并发 Gradle）。
- **脚本复用与适配**（批次 50 原件 → 本目录）：`sample_d1.py` 改 audit id / 冻结框源 / 输出目录，
  加冻结守卫与 `sample.sha256`、分片按 `sha256(audit_id|'shard'|slug)` 混层；`render_compact.py`
  增写 `shards/*.md`；`check_raw.py` 节点索引改 `(subject, slug)` 解析（包内 5 组同名 slug）；
  `compile_judgments.py` 改 audit id、`judged_at` 取原始件 mtime；`recompute.py` 逐字拷入；
  `JUDGE_PROMPT.md` 规则逐字复用、仅改标题/版本注记。
- **未改**：包 / KB 台账 / 门阈值 / 判定口径规则；`sample_d1.py` 冻结守卫：样本已存在时默认拒跑，
  `--force` 仅允许工具故障重算且样本键必须与已冻结 `sample.csv` 逐键一致。

## 3D 盲判分片 5/32 · batch-05（子线 3d-judge）

- 输入：`docs/research/evidence/2026-11-10-batch-51/3d-audit/judge_input/batch-05.json`
  （sha256 `6bd8c21755c67637057bc5583f77e4c70a85de9061c43cfc6c09e37cd2829db6`，50 条；与分片 md 登记值一致）
  + `node_index.tsv`；判定口径 `JUDGE_PROMPT.md`（sha256 `2600904e…4a7d81`）。盲层：未读 layer、未看金标/既往判定。
- 产出：`docs/research/evidence/2026-11-10-batch-51/3d-audit/verdicts_raw/batch-05.tsv`
  （sha256 `647d5c11…b687332c`；6 列表头逐字，50 行，顺序=输入）。
- 自检实跑：`python -I -X utf8 check_raw.py verdicts_raw/batch-05.tsv` →
  `{"file": "verdicts_raw\\batch-05.tsv", "rows": 50, "problems": 0}`，EXIT=0。
- 判定分布：CORRECT 45 / REBIND 5 / NONE 0；confidence high 39 / medium 11；引文均 ≤48 字且为材料五字段逐字子串。
- REBIND 5 条（target 均经 `(subject, slug)` 同科核对、存在且≠现绑）：
  1. `chem-fx-qi-ti-de-chu-za-fang-fa-f28x04`（气体除杂）→ `写出除去下列气体中混有的杂质-括号内为杂质-可采取的方法`
     （name=除杂的试剂选择与操作方法；现绑为闻气体的方法）；
  2. `chem-kb-subgroup-zero-valence-config`（副族/0 族价层电子规律）→ `原子结构与元素周期表的关系`（现绑 VSEPR）；
  3. `math-hj-s12-judge-set`（集合包含关系判定充要条件）→ `充分条件与必要条件的判断`（现绑为求参数）；
  4. `phys-fx-block-plank-solve-key`（滑块木板解题关键）→ `板块模型`（现绑临界问题，boundary 为动量/能量）；
  5. `ext-mat-347cb02bf5-002c`（(1±i)² 与分式速记）→ `复数的四则运算`（现绑虚数单位幂的周期性）。
- 疑点登记（不改判定）：`phys-hj2-li-de-tushi-shiyitu` 现绑 `模型示意图` 的名称=材料题名，但 boundary
  为圆环轨道内容（疑错置）；同科另有 boundary 契合的 `力的图示与示意图`。按「材料主知识点=现绑节点名」
  判 CORRECT(medium)，近重复/错置交 D-2，不静默改绑。
- 未跑：Gradle / 设备（本片仅判定，不需要；红线禁并发 Gradle）；未改门阈值 / 包 / 样本 / 口径。


## 3D 盲判分片 12/32 · batch-12（子线 3d-judge）

- 输入：`docs/research/evidence/2026-11-10-batch-51/3d-audit/judge_input/batch-12.json`
  （sha256 `8e5ab46a499e0188d57cc5c05eb627119d1c3cb47af321cdb929a60f30a73cbc`，50 条；自算值=分片 md 登记值）
  + `node_index.tsv`；判定口径 `JUDGE_PROMPT.md`（sha256 `2600904e…4a7d81`）。盲层：未读 layer 字段（输入件不含该字段）。
- 产出：`docs/research/evidence/2026-11-10-batch-51/3d-audit/verdicts_raw/batch-12.tsv`
  （sha256 `16d0c1af…15c1a14e`；6 列表头逐字，50 行，顺序=输入）。
- 自检实跑：`python -I -X utf8 check_raw.py verdicts_raw/batch-12.tsv` →
  `{"file": "verdicts_raw\batch-12.tsv", "rows": 50, "problems": 0}`，EXIT=0（首跑 1 条引文非逐字已修正：材料 12 的 `**几何法**` 星号被漏抄）。
- 判定分布：CORRECT 45 / REBIND 5 / NONE 0；confidence high 44 / medium 4 / low 2；引文均 ≤78 字且为材料五字段逐字子串。
- REBIND 5 条（target 均经 `(subject, slug)` 同科核对、存在且≠现绑）：
  1. `ext-phy-1dbc2880eb-031c`（均匀带电球壳场强/补偿法）→ `求解非点电荷电场强度的四种方法`（medium；现绑为场强定义）；
  2. `ext-che-4112b48508-001b`（晶胞概念+均摊法）→ `晶胞组成的计算-均摊法`（low；现绑 boundary 明确“不含晶胞中粒子数目的计算”）；
  3. `chem-hj2-jishi-fuhao-guifan-jisuan`（计算符号/单位规范）→ `根据化学方程式计算的步骤`（low；现绑为守恒法，同科无“计算书写规范”专点）；
  4. `ext-bio-64a3002892-134`（酶切片段长度判正反向连接）→ `目的基因插入方向的鉴定（PCR与酶切）`（high；现绑为限制酶本身）；
  5. `ext-mat-4308c78446-026`（常见抽象函数模型）→ `常见抽象函数模型与特征式`（high；现绑 boundary 为赋值法求值）。
- 低置信登记（交复判）：第 1、2、3 项 medium/low；第 2、3 项现绑节点对材料仍有部分覆盖，属“材料跨两个点”的边界判断。
- 盲判偏差自报：为核对 TSV 列格式，读过 `verdicts_raw/batch-01.tsv` 前 5 行（仅表头/tab 结构）；该 5 条 slug 与本片 50 条无交集，未据其内容调整任何判定。此外未看金标、未看其它分片判定内容。
- 未跑：Gradle / 设备（本片仅判定）；未改门阈值 / 包 / 样本 / 口径 / check_raw.py。

## 3D D-1 汇总复算与门报告（子线 3d-audit）

- 输入：`docs/research/evidence/2026-11-10-batch-51/3d-audit/`（32 片 `verdicts_raw/`、`sample.csv`/`sample.jsonl`、`manifest.json`、`JUDGE_PROMPT.md`）。
- 实跑：`check_raw.py` 32 片 → `batches 32 rows 1600 problems 0`；`compile_judgments.py` EXIT=0；`recompute.py` EXIT=0 `all_match=true`；`recompute_detail.py` EXIT=0 `all_match=true`。
  输入 sha 复核（本会话实算 = manifest）：主包 `aaf81be8…`、框 `ce7d6823…`、`sample.csv` `77eb9969…`、`sample.jsonl` `a994e5b6…`。
- **读数**：未加权 1501/1600 = 0.938125；加权点 0.9403574；主判据（FPC）单侧 95% Wilson 下界 **0.9291236 < 0.95 → FAIL**（差 2.0876pp）；no-FPC 0.9289924、deff=1.05 0.9288237 亦均 <0.95。
- **护栏（预注册 no-FPC ≥0.90）**：overfull 0.9290572 过；**normal 0.8979148 未过**（差 0.2085pp）；FPC 并列 normal 0.9005926 过（+0.0593pp），不改口径/阈值、不据此翻绿。
- 错误 99 = 89 REBIND + 10 NONE（overfull 77=69R+8N；normal 22=20R+2N）；B50 的 43 条错误材料中 **4 条**进入本批样本且均判 CORRECT，**两轮错误 slug 交集才是 0**、两轮样本仅 68/1600 重叠（交集清单见 `evidence/2026-11-10-batch-51/fix-loop/b50-b51-overlap.json`）→ **非配对前后比较**；且这 4 条由与 B50 改绑判定相同的 `deepseek-flash` 复测，不是独立验证，不声称 43 条改绑已被本批验证。
- **判定：3D D-1 FAIL**（主判据 + 预注册护栏均未过；low 全放宽的主判据仍 0.9340784 < 0.95）。未写 PASS。
- 产出：`3d-audit/REPORT.md` / `judgments.csv` / `errors.csv` / `stats.json` / `stats_recomputed.json` / `stats_detail.json` / `recompute_detail.py`（只读复算器）。
- 未跑：κ 双判、人工专家复核、Gradle/设备（本阶段不需要）。未改：门阈值、样本、判定、pack/KB。

## 3E G2 每日累计配额（KD-46 修复 · 零 schema 方案 A，子线 3E 配额修复）

- 前置协作：`git status --porcelain -- core/domain/.../core/domain core/data/.../study core/data/.../androidTest/study` → 目标文件多为 `M`（前批/他线既有改动）。本轨只加**增量 hunk**，未整文件覆盖；未触碰 `knowledge-production/wusan/materials.jsonl`、`tools/kb_build/*`、`app/`、`feature/`、备份/清单/机器块。
- 研究落点：`docs/research/2026-11-10-daily-scan-quota.md` 方案 A（零 schema、从 `learner_chat_evidence` 派生当日已花费）。
- **语义**：日键 = 学习日（04:00 日界，与今日错题计划同键）；已作答 = `source_kind='KNOWLEDGE_QUIZ'` 且 `created_at ∈ [学习日 04:00, +24h)` 的 DISTINCT `knowledge_node_id`（**含**拒绝观察行）；已花费 = 已作答 − 今日第一档可出题节点；剩余 = `max(0, 10 − |已花费|)`；当日已复习的扫描点**回放进并集但不占名额**；账本读失败 → `remaining=0` fail-closed（`CancellationException` 原样上抛）。
- **零 schema**：只新增一条只读 SQL + 端口方法 + Room 实现；`core/database/schemas` 与备份清单/机器块/`trimOrder`/`CLOSURE_VERSION` 未动；6C-2 的 mask 9/13/15 未触碰；`plan_fingerprint`/`plannerVersion` 未 bump（队列不落库）。
- **先红后绿（本轨实跑）**：
  - 红：`./gradlew :core:data:testDebugUnitTest --tests "...RoomBackedStudyExperienceRepositoryTest.scanQuota*"` → **EXIT=1**，7 例中 5 红（同日累计/跨日边界/剩余名额/被拒行/失败关死），2 绿为守卫；红的断言原文见证据 README §3.1。
  - 绿：同命令 → **EXIT=0**，7 例全 PASS；`:core:domain:test --rerun` → **EXIT=0**（含新增回放用例）。
  - 全量：`:core:data:testDebugUnitTest --rerun` → **EXIT=1**，`1709 tests completed, 4 failed, 1 skipped`；4 红 = 批次 49/50 既有基线（`OpenAiCompatibleModelGatewayTest`×3 + `profileStrengthsDropForgottenAndPrerequisiteSuppressedNodes`，逐名同组，见 `evidence/2026-11-09-batch-50/postfix-gate.md:118-119` 与 `2026-11-08-batch-49/unit-gate.md:85-88`），**无新增红**。
  - 设备用例源码：`:core:data:compileDebugAndroidTestKotlin --rerun-tasks` → **EXIT=0**（28 tasks executed，只编译未上设备）；旧 C8 用例 `scanQuotaIsPerPlanNotADailyCumulativeTotal` 已改写为 `scanQuotaIsDailyCumulativeAcrossPlanCallsInTheSameStudyDay`（删 `snapshotAfterMastering`、经真实写口落 `KNOWLEDGE_QUIZ` 证据、断言回放/当日并集 ≤10）。
- **未跑/未验证（如实）**：设备 `connectedDebugAndroidTest`（C8）**NOT RUN**；C5（预算先绑住/预算变更）未实现；证据查询 P95 未量；**3E 门「无条件 PASS」未声明**（需第 19 项设备实跑）；KD-46 台账、`adjudication.md` 3E 行、行级 review JSON、台账机器块未改（留验收阶段）。
- 残留：R（当日重排把扫描点拉进第一档 ⇒ 上界 10+R）、R2（作答未落行 ⇒ 只少计）、R3（非扫描来源的当日测验占名额并回放 ⇒ 当日**计划过**的去重节点可 >10，实际复习仍以名额为上界）、时钟回拨/恢复/清库。
- 产出：`docs/research/evidence/2026-11-10-batch-51/3e/README.md`、`logs/`（4 条命令日志 + JUnit XML + 逐例 summary）、`code-diff.patch`。

## G3 · AUD-08 切分口径 v3 与提示词修复（子线「切分口径修复」）

- 范围：计划项 20（口径）与 21（提示词）；**未做**项 22 dev 调参、项 23 新 acceptance 切片、项 24 判门（红线：acceptance 切片不动；本轨零 provider 调用）。未改 0.9/0.05 阈值、①/③ 分母、`fragmentedQuestions==0` 硬红线、金标 v1/v2、解析器白名单。
- 协作前置：`git status --porcelain -- tools/split_golden core/data/.../model core/model/.../model docs/research/evidence/2026-11-10-batch-51` → 仅 `tools/split_golden/check_split_gate.py` 为前批既有 `M`；本轨保留其原 hunk，只在 `report["measure"]` 后追加 v3 字段（未整文件覆盖）。其余目标文件开工前均未改。未触碰 `knowledge-production/wusan/materials.jsonl`、`tools/kb_build/*`、`app/`、`feature/`。
- 口径 v3（`tools/split_golden/measure_split_accuracy.py`）：逐页 `pageClass`（ACCEPTED_SPLIT / SPLIT_UNUSABLE / ACCEPTED_NO_SPLIT / ABSTAIN / NOT_EVALUABLE_CHANNEL / MISSING_APP）；单题页只有被接受的 PASS 记一致；弃答题数记 `null` 并单列 `abstain`（逐页 `questionCountForMetric=null`，不是 0 框）；通道失败单列 `notEvaluable`；缺页记不一致；整份导出无 `decision/outcomeStatus` 时回落 v2 并计 `singleQuestionUnverifiedCount`（`caliber="v2-fallback"`）。`countExact` 为 v3，`countExactV2` 保留旧值对照；分母不变。`tools/split_golden/rulings.py` 增 `appDecision`/`pageClass` 加法字段（validator 已过）。
- 同一样本重算（只作对照，不作 acceptance；命令与逐页见 `evidence/2026-11-10-batch-51/split/口径变更.md`、`recompute-caliber-v3.txt`）：
  - 批次 50 全部 24 页 `7/24=0.2917 → 6/24=0.2500`（仅 `011800` 改判：ERROR/type 键拒收，旧口径误记一致）；acceptance 8 页 `3/8 → 2/8`；③ `1/78` 不变。
  - 批次 48 全部 24 页 `10/24` 不变；acceptance 8 页 `5/8` 不变。
  - 设备导出（无判定字段）v1 `4/7`、v2 `4/24` 均不变，`singleQuestionUnverified=2`。
- 提示词（`OpenAiModelTaskAdapters.kt:161-197`）：六条规则（题号枚举/边缘裁切/多栏/整页框/区域契约/单题页 PASS）+ 三层键白名单 + 一个形状示例 + 3 位小数；1710 字符，sha256 `2fa068970407c2fc9dad7ec09aa4b99f6010c8fb705bcfad88cef2e212e854c4`（探针 `split/prompt-v3-probe.txt`）。`type` 键修复在提示词侧；`OpenAiModelResponseParsers.toAssessment` 保持硬白名单并加注释，回归用例钉住「顶层 type 仍整轮拒收」。
- 先红后绿（实跑）：
  - Python：新增 7 条 `CaliberV3Test`，实现前 `FAILED (failures=4, errors=2)` exit 1（`split/red-python-caliber-v3.txt`）；实现后全量 `Ran 68 tests ... OK` exit 0（`split/green-python-split-gate.txt`、`split/final-python-split-gate.txt`）。
  - Kotlin：`./gradlew.bat :core:data:testDebugUnitTest --tests "*Assessment*" --console=plain` 实现前 `15 tests completed, 3 failed`、`BUILD FAILED` exit 1（`split/red-kotlin-assessment.txt`）；实现后同命令 `BUILD SUCCESSFUL` exit 0；最终再用 `--rerun` 强制实跑（`1 executed`）仍 `BUILD SUCCESSFUL` exit 0，`OpenAiCaptureAssessmentAdapterTest` 9 tests / 0 failures（`split/green-kotlin-assessment.txt`、`split/final-kotlin-assessment.txt` 与 JUnit XML）。
  - 旁证：`check_split_gate.py` 对批次 50 导出 `FAIL`（① 6/24、③ 1/78，exit 1）、对设备导出 `FAIL`（① 4/7，exit 1）；`check_eval_label_leakage.py --paths tools/split_golden` error 0/warning 0 exit 0。
- **未跑/未验证**：零 provider 调用；提示词对 ① 的实际效果 UNVERIFIED（`type` 示例能否消除回显也无实验）；dev 16 页调参与新 acceptance 切片未做（未动 acceptance）；`tools/ci/check_acceptance_once.py` 未实现；未生成 v3 金标；全量 `:core:data:testDebugUnitTest` 未跑（只跑 `*Assessment*`）。
- 未改：阈值、分母、硬红线、金标、acceptance 切片、6C-2、JS/UI/KB 他线文件。

## G3 · AUD-08 dev 16 页调参（子线「切分 dev 调参」，计划项 22）

- 范围：只跑 dev 16 页（v2 `split.dev`，52 题）；acceptance 未运行、未读图像/金标框（脚本只读 `split` 块断言交集 0）；未改阈值/分母/硬红线/金标/6C-2/JS/UI/KB；切题只走模型识别（无 CV/几何）。
- 通道：`GoldenSplitEvalChannelHarness`（真实 `requestBody`/`parseCompletion`/`isUsableSplitRegionSet`；`deepseek-flash`，`temperature=0.1`，key sha8 `cd10efbc`）；出网副本按 `EgressImageBudget`；harness 批次 51 增量 `responseRaw` 全文留痕（`GoldenSplitEvalChannelHarness.kt:60,193,217,255`）。
- 4 个提示词版本 + 1 次同版复测（每轮 dev 16 页，真模型；裁决 = `build/batch51-split/run_split_eval.sh <run-id>`）：
  - v3（1710 字符，`2fa0689…`）：① 1/16、② 9/52、③ 1/52，弃答 8 页（「题号数=区域数」把页边残段逼成 NEED_MORE_IMAGE）。
  - v3.1（1955，`2c698d1b…`）：残段计题 + 收窄弃答；① 9/16、② 26/52、③ 3/52；过度分题 5 页（相邻页/横拍逐行）。
  - v3.2（2137，`591f7c63…`）：限定本页纸面/合并残段/横拍规则；① 13/16、② 33/52、③ 2/52。
  - v3.3（2204，`7f92b456…`，最终）：① 14/16、② 36/52、③ 2/52；同版复测 ① 12/16、② 31/52、③ 3/52（单轮噪声 ±2 页，③ 跨 0.05 线两侧）。
- 最终提示词 = v3.3（`split/prompt-final.txt`，sha 与两轮 16 页记录逐字一致）；dev 最高 0.875 < 0.9，**未写 PASS**。剩余失败：005144/005301（两页跨版照，金标按每页各自计题，与提示词「同一道题跨页不算多题」冲突）；011017 为采样波动。
- 结构限制（金标冻结未改）：004511/011649 金标框相对原始像素帧转置（转置帧 best IoU 0.874/0.888、0.817 vs 原始帧 0.12–0.41），004511 的 ③ 伪切残由此派生；011017 金标 box2 高 0.030 < 产品最小 0.04。
- 回归：`OpenAiCaptureAssessmentAdapterTest` 9 tests / 0 failures / 0 errors（`split/dev-runs/final-junit-summary.txt`）。
- 未验证：acceptance 未跑、门未判；单臂/单模型/单温度、单轮采样无置信区间；通道为 harness（直连/PIL/无设备 DB 落库），不外推设备产品路径准确率。
- 产出：`docs/research/evidence/2026-11-10-batch-51/split/dev-tuning.md`、`prompt-v31/v32/final.txt`、`dev-runs/dev-v3-01|dev-v31-01|dev-v32-01|dev-v33-01|dev-v33-02/`（逐页回包 + manifest + export + metrics）、`final-kotlin-assessment-tests.txt`、`final-junit-summary.txt`。改动代码：`OpenAiModelTaskAdapters.kt:161-211`（提示词）、`GoldenSplitEvalChannelHarness.kt:60,193,217,255`（responseRaw）。

## G3 · AUD-08 acceptance 一次性运行与判门（子线「切分 acceptance 判门」，计划项 23–24）

- 范围：acceptance 8 页**唯一一次**运行（`acceptance-b51-01`）+ dev 16 页合并 24 页过
  `tools/split_golden/check_split_gate.py`（先 `--help` 确认入口）。未改阈值/分母/金标/提示词/
  产品代码/6C-2/JS/UI/KB；切题只走模型识别（无 CV/几何）。跑前冻结 `acceptance/acceptance-protocol.md`
  （写入早于运行），run 目录仅 1 个、脚本对已有 manifest 拒绝重跑。
- 配置：金标 v2 sha `03b1eecc…1e18e54`（冻结校验 PASS，G1）；acceptance 切片 8 页/26 题 sha `c9138ba2…`；
  提示词 v3.3 sha `7f92b456…`（从 Kotlin 源重抽 == `prompt-final.txt`，run 内 8/8 页一致）；
  `deepseek-flash`、temperature 0.1、key 仅 sha8 `cd10efbc`；Gradle `--no-daemon` 串行（运行前 daemon 空闲）。
- 实跑：`bash build/batch51-split/run_split_acceptance.sh acceptance-b51-01` → `GRADLE_EXIT=0`、`MEASURE_EXIT=0`；
  `python -I build/batch51-split/merge_exports.py …` → 24 页/75 框；`check_split_gate.py` 两次（主门/敏感性）。
- **acceptance 单切片（8 页/26 题，口径 v3）**：① **7/8 = 0.8750**（<0.9）、② 17/26 = 0.6538、③ **1/26 = 0.0385**、
  弃答 0、通道不可评 0。失败页 `004952`（金标 2 vs 应用 3，多切一题 + 该页 1 处切残）；`004353/011358`
  题数一致但 ② 为 0/2、0/5（边界偏差，② 不设门）；`011800` 单题 PASS 被 v3 正确记一致。
- **合并门（exit 实测）**：
  - 主门 dev-v33-02 + acceptance（冻结提示词最后一场 dev 观测；跑前写死，不按分数挑）：导出 sha `e8ea2e46…`，
    ① **19/24 = 0.7917**、③ **4/78 = 0.0513**、`fragmentedQuestions=4`（004511/004854/004952/005855），
    **exit 1 → FAIL**；不一致页 004511/004952/005144/005301/011017；② 48/78。
  - 敏感性 dev-v33-01 + acceptance：导出 sha `eaf17fa5…`，① **21/24 = 0.8750**、③ 3/78 = 0.0385、
    `fragmentedQuestions=3`，**exit 1 → FAIL**（① 未过 + 硬红线未满足）；② 53/78。
  - 结论：**AUD-08 FAIL**（①<0.9；主门 ③ 亦 >0.05；切残硬红线非 0）；门后自动化不启用（裁定 37 / 4C③）。
    未写 PASS，未改阈值或分母。
- **偏差（如实登记）**：本库无「从未看过的新 acceptance 切片」（唯一冻结金标 v2 仅 24 页，无 v3/未用页池）；
  同一 8 页已在批次 48（`acceptance-once-01`）与批次 50（`acceptance-b50-01`）各消费一次 ⇒ 本轮为**第三次观测**，
  held-out 不可恢复，**不得记 citable PASS**。`check_split_gate.py` 生成的 scope 文案仍写「第二次观测」
  （批次 50 语境），本轮不改他线 M 的门脚本，已在 `acceptance/README.md` §5.2 取代声明。其余限制：
  G1 系统抽样金标、0 张双栏页、单臂/单模型/单温度单轮采样、通道为 harness 不外推设备产品路径。
- 未验证：设备路径复验、跨模型/跨温度、人工复核均未做；本轮只跑
  `:core:data:testDebugUnitTest --tests …GoldenSplitEvalChannelHarness`（8 页全 OK），未跑全量单测门。
- 产出：`docs/research/evidence/2026-11-10-batch-51/split/acceptance/`（protocol、切片、run/acceptance-b51-01
  逐页记录/export/metrics、gate/merged-24 + 两份门报告/证据/裁定/stdout）。脚本（gitignored build/）：
  `build/batch51-split/{prepare_acceptance_slice.py,run_split_acceptance.sh,merge_exports.py}`。

## G4 · MVA JS 臂：设备适配器 / 作者链 / 事实提取器（子线「MVA 管线构建」，计划项 25–28）

- 范围：JS 臂从「0 可评样本」推进到可复跑产物链（程序 → 真引擎 → SceneV1/DrawOps/度量/PNG
  → fact_expectations）。未改门阈值、冻结夹具、`check_fact_expectations` 语义、产品主代码、
  6C-2、JS 广告状态；未实现 C 通道/IMG 臂（见「未验证」）。开工前/收工前各查一次
  `git status --porcelain -- <目标>`；新增文件均为本轨；未整文件抢占他线 M 文件
  （`figure_eval_gate.py` 的 M 为 AUD-08 线，未触碰）。
- 新增：
  - `tools/figure_eval/fact_extract.py`（DrawOps 回放 + A1 解码器 + `UNDECODABLE`/未知 key fail-closed +
    SceneV1 `node.id→op 索引` 角色绑定）；
  - `tools/figure_eval/test_fact_extract.py`（26 例；先红：`ModuleNotFoundError`，后绿 26 passed）；
  - `tools/figure_eval/mva_facts_run.py`（设备产物 → 逐题事实/可评性读数；`gate_readable` 只在 120/120 可评时为真）；
  - `core/data/src/androidTest/.../visual/MvaJsArmAdapterInstrumentedTest.kt`（真引擎 + 记录型 measurer +
    `VisualDrawOpsParser/Painter` 出 PNG；per-program 引擎；产物 scene/drawops/metrics/record/png/manifest）；
  - `core/data/src/test/.../model/MvaFigureAuthorHarness.kt`（产品 `FigurePromptAssembler` 正文 +
    产品 `toCapturedFigureProposal` 解析 + 直连 HTTPS；env 门控、8 路并发、可续跑）。
- 实跑（关键读数）：
  - `python -m pytest tools/figure_eval/test_fact_extract.py test_mva_gate.py test_figure_eval_gate.py -q`
    → **70 passed**；生产编译器探针回归 10/10 (case,task) 与预期一致，`removed_handwriting` 保持 UNDECODABLE。
  - 设备链（emulator-5590，sdk_gphone64_x86_64/SDK34）：手写合法夹具 → `RENDERED`、latency 161 ms、
    draw sha `d8a923a2…`、png sha `5299bdc6…`、stats nodes3/pathCommands4/labels1/ops2；提取器对设备
    DrawOps 取值 `ratio=1.0 / ["1:1"] / count=2`，`check_fact_expectations` → accepted=true；PNG 目视有线段+标签。
  - 作者链 120 题（`deepseek-flash`，temperature 0.1）：**OK 104/120**（86.7%），INVALID_PROPOSAL 10、
    NOT_GENERATE 5（NEED_SOURCE，全在去手写/源图重建）、NOT_JSON 1；分组：flow/mech/prop 20/20，
    natural 19/20，source_rebuild 18/20，handwriting_removal 7/20。
  - 设备渲染 104 个作者 OK 程序：**RENDERED 0/104**，104/104 `INVALID_SCENE`；首错码
    SCENE_FIELD_UNKNOWN 72、SCENE_PATH 15、SCENE_FIELD_TYPE 12、SCENE_SDK 3、SCENE_STYLE 2。
    `mva_facts_run.py` → `evaluable=0/120, evaluable_coverage=0.0, gate_readable=false` ⇒ **NOT_EVALUABLE**。
- **产品侧阻断（本轮硬证据）**：`FigurePromptAssembler.JS_STATIC_SAMPLE`（`FigurePromptAssembler.kt:256-271`，
  path 节点 `stroke:'#26354a'` @`:267-268`）与生产编译器不一致——`visual-compiler.js:452 requireStroke`
  把 `stroke` 要求为对象，且 stroke-only 无颜色源（fill 必填）。把样例原样交给 `visual-compiler.js`：
  `{"compiles":false,"error":"SCENE_FIELD_TYPE: scene.root.children[0].stroke must be an object"}`、EXIT=1。
  作者程序 80/104 把 stroke 写成字符串、87/104 带 `strokeWidth`、54/104 给 label 用 `text` 字段；
  模型照着错误样例写 ⇒ 编译全红。**未改产品提示词**（超出本轨最小改动；修复方向与测试要求已写入证据 README）。
- 未验证：C 通道（VLM 判官/校准件）未实现（label_absent 14 条 critical 仍 UNDECODABLE）；A2 角色解码器
  未实现（`bind_scene_nodes` 只做载体）；IMG 臂 BLOCKED（无图像 provider，栅格上无 1e-9 结构化事实）；
  `mva_gate.py` 未改（通道规则仍 `label_absent→A|B`）；MVA 门未判（无 120 题完整事实读数）。
- 证据：`docs/research/evidence/2026-11-10-batch-51/mva/`（README、SHA256SUMS、`adapter-fixture/`、
  `author-smoke/`、`author-blind/`、`device-smoke/`、`device-blind/`、`facts-blind/`、`product-sample-probe/`）。

## G4 · MVA 门执行（子线「MVA 跑分判门」，计划项 29）

- 范围：把上一轨的「0 可评样本」推进到**真实门读数**：作者链 120 → 设备渲染 → 确定性事实 →
  VLM 盲判（C 通道 + 注入错值校准）→ `mva_gate` 六道门。阈值 90/95/90/0、p95 30 s/120 s 与
  分母 120 未改。**结论：FAIL**（不是 PASS；也不是"不可评"——失败由上界独立确立，见下）。
- 产品侧修复（本轮唯一产品代码改动）：`FigurePromptAssembler.JS_STATIC_SAMPLE` 改为可编译样例
  （path 带 `fill` + `stroke:{width}` 对象 + 真实 `label` 用 `metrics[0].contentId`），契约正文补
  fill/stroke/label 必填字段、`contentId` 来源、commands 数组与 SDK 参数边界。先红：
  `test_prompt_sample_compiles.py` 报 `SCENE_FIELD_TYPE: ...stroke must be an object`；后绿：
  `1 passed`，探针 `{"compiles":true,"ops":3,"pathOps":2,"labelOps":1}`。dev 24 编译率
  **6/20 → 18/19**；120 题 98 个 OK 程序 **95 编译通过**（96.9%），95/98 用 `metrics[i].contentId`。
- 适配器对齐：`MvaJsArmAdapterInstrumentedTest` 的 contentId 从 `c<index>` 改为生产同形
  `c<index>-<digest8>`（消灭"设备能过、生产 `SCENE_REF_CONTENT`"的分叉）。
- 新增工具：`mva_vlm_judge.py`（盲判 + 校准 + SSRF 边界）、`mva_gate_run.py`（三类产物合成门记录）、
  `author_program_compile_check.mjs`（离设备编译分诊）、`prompt_sample_compile_probe.mjs` 与两份测试；
  `mva_gate.py` 最小修复（`python -I` 下 `sys.path`，计划点名命令原会 `ModuleNotFoundError`）。
- 实跑读数（`docs/research/evidence/2026-11-10-batch-51/mva-gate/`）：
  - 作者（deepseek-flash，temp 0.1，8 并发）：**OK 98/120**（INVALID_PROPOSAL 17、NOT_GENERATE 5）；
    分组 OK：prop/mech/flow/natural 各 20、source_rebuild 16、**handwriting 2**。
  - 设备（emulator-5590，SDK34）：98 程序 → **RENDERED 95**、REJECTED 3（与编译分诊一致：
    `SCENE_PATH_NOT_M_START` 2、`SCENE_NODE_COUNT` 1）；PNG 95 张目视非空白。
  - 事实：A1-only `mva_facts_run` → evaluable 47、accepted 25；VLM 校准 **6/6 读出、3/3 正确图接受、
    3/3 注入错值被抓**；正式判分 20 题/32 事实全 OK（direction 24/24、phase_order 5、wave_count 3）。
    合并后 **evaluable 59/120（0.4917）**、**首次=最终 32/120（0.2667）**。
  - 六门：首次 0.2667 <0.90 **FAIL**；最终 0.2667 <0.95 **FAIL**；每组 flow 2/20、handwriting 0/20、
    mechanics 0/20、natural 14/20、proportion 1/20、source 15/20，**6/6 组 FAIL**；关键错误接受 0
    （无自我接受阶段，结构性 0，不作独立能力证据）；JS 首图 p95 端到端 **72.9551 s**（32 个合格
    样本：作者 `elapsed_ms` + 渲染；>30 s，**FAIL**），渲染侧 35.15 ms 只作 `render_only` 并列读数、
    不参与门判；修复环已把 `mva_gate_run.py` 的 p95 从“仅适配器”改为“请求→首图”并重算
    `gate/`（`readings_digest.json` 的 `gate.p95_ms.met=false`）；IMG 臂 **BLOCKED**（无 provider）。
  - **FAIL 的上界（与未判事实无关）**：RENDERED 95 < 首次阈值 108 与最终阈值 114；
    handwriting 作者 OK 2、source 16，均 <每组阈值 18；mechanics 15 题被 A-only numeric 阻塞。
- 结构性阻断（下一步）：A2 角色解码器（A-only numeric 27 条 critical、已渲染 24 条）；
  A1 解码器在真实作者布局上的缺口（`step_order` 7、`point_x/y` 各 5、`segment_length_ratio` 4、
  `ascending_order` 1）；作者成功率（handwriting 2/20、source 16/20）；IMG 臂外部 BLOCKED。
- 方法学偏差（随读数引用）：120 集已被上一轨观测并据此修 prompt ⇒ 按设计 §6 转**回归集**，
  启用判定需新的未见盲测集（仓内没有）——本轮读数是回归集读数，不能作启用 PASS 依据；
  作者 harness 直连 HTTPS/无 ledger/8 并发/每题一次（无重试环，"最终"与"首次"同值）；
  设备适配器未走 app 隔离进程与生产度量器（contentId 形状已对齐）；判官单模型/单温度/无人工复核；
  **判官与作者链同模型（均 deepseek-flash，仅 temperature 0.1/0.0 不同）⇒ 32/120 与 6 组读数属
  同源自证，不作独立能力证据**（门 FAIL 不依赖判官：RENDERED 95<108）；120 集事实为
  model_annotated（无人工专家）。
- 未验证：app `VisualJsClientEngine`/`:visual_js` 隔离进程/AIDL 队列；生产 `AndroidVisualContentMeasurer`
  （math 不支持）；跨模型/温度、重试/修复环、soak；新未见 120 集；`policyVersion` 未升版（残余风险）。
- 证据：`docs/research/evidence/2026-11-10-batch-51/mva-gate/`（README、SHA256SUMS 774 文件（不含 `.mimosa/` hook 状态）、
  `author-blind-2/`、`device-blind-2/out/`、`facts-without-judge/`、`judge-blind-2/`、`calibration/`、
  `gate/`、`preflight-cli/`、`logs/`）。

## JS 产品化 · MVA 未过 → JS 保持关闭、math 显式降级复验、KD-76/71/73/78/82 登记（计划任务 30–32）

- **门判**：MVA = **FAIL**（引用同批 `mva-gate/README.md` §6/§7：首次 32/120=0.2667、RENDERED
  上界 95/120=0.7917<0.90、6/6 组 FAIL、端到端 p95 72.9551 s>30 s、IMG 臂 BLOCKED）。
  按用户裁定 **JS 不注入、能力不广告**——本子线零代码改动即正确终态。
- **装配/广告现树复核**：三参 `SmartMistakeBookApplication.kt:387-391`；工厂
  `DefaultFigureGenerationCoordinator.kt:886-891` 两参默认 null；6 处 `IMAGE_MODEL_ONLY`
  （`ConfiguredCleanImageGenerator.kt:95`、`RoomDetailedSolutionRepository.kt:256`、
  `RoomMistakeEditRepository.kt:258`、`FigurePromptAssembler.kt:124`、`AttachedImageGenerator.kt:144`、
  `TutorFigureGenerator.kt:372`）；能力快照零改动。
- **math**：维持显式降级（painter 诊断含 `explicitly degraded`，`VisualDrawOpsPainter.kt:194-202`；
  预览同码拒绝；度量期失败降级为原文 text + `degraded=true`，`VisualContentMeasuring.kt:455-466`）
  ——有码拒绝/原文透传，**不静默丢字**；未实现盒回放。
- **KD-78 残留**：门与回放兜底全在树；`node tools/visual_compiler/run_tests.mjs` 94/0；
  KD-78 ① 的**产品样例残留**（`JS_STATIC_SAMPLE` stroke 字符串 + path 无 fill）已由 G4 线
  批次 51 修复（先红 `SCENE_FIELD_TYPE` → 绿 `{"compiles":true}`）。
- **KD 登记**（`docs/known-defects.md`，各条末尾追加批次 51 块）：KD-76 **open**（并在该块登记
  「MVA 未过」）；KD-78 维持 closed（残留复验）；KD-71/73/82 均 **open**（KD-73 静态门本轮绿：
  0 unresolved / 23,859 refs，pins quickjs 1.0.15 + stdlib 2.3.21；KD-82 JVM 7/0）。
- **实跑与 exit**：`:core:data:testDebugUnitTest`（6 过滤类）BUILD SUCCESSFUL/EXIT=0，97 tests 0 失败；
  `:app:testLocalFirstDebugUnitTest --tests "com.tingyun.smartmistakebook.visualjs.*"` SUCCESSFUL/EXIT=0，
  50 tests 0 失败；`check_stdlib_compat.py` EXIT=0；`check_dependency_pins.py` EXIT=0；
  `node tools/visual_compiler/run_tests.mjs` EXIT=0。合计 **147 tests / 0 failures**（JUnit XML 已复制）。
- **未跑**：设备 `am instrument`（math 降级设备面沿用批次 50 `OK (1 test)`，本批 UNVERIFIED）；
  KD-76 校准、KD-71 G4、KD-73 设备/R8 半边、KD-82 产品裁定；arm64/真机；soak/release（他子线）。
- **协作**：开工 `git status --porcelain -- <目标>`；他线在飞的
  `app/src/test/.../visualjs/VisualContentMeasuringTest.kt`（M）未改、其 16/0 只作含该增量的现树读数。
- 证据：`docs/research/evidence/2026-11-10-batch-51/js/`（README、`logs/`、`junit/`、`test-results.json`）。

## 设备全量 · 数据安全 / 3E 设备复验 / 新 UI / R8 release 冒烟 / 2h soak（子线「设备全量」，计划任务 33–35）

- **绑定**：HEAD `8dd29e96…`；开工 `git status --porcelain` 683 行 / `-uall` 5418；设备
  `emulator-5590`（ui_motion_20261107 / API 34 / x86_64 / PAGE 4096 / boot_id `31ecd364…`）与
  `emulator-5592`（ui_teaching_20261108 / boot_id `b24f1996…`）——均**模拟器非真机**；
  APK sha 见证据 `device/03-apk-shas.txt`（debug `38cb991c…`、core:data test 首版 `2ae00f06…` /
  修夹具后 `2516876f…`、core:database test `5a8ca2b6…`、feature:library test `63ffb00c…`、
  release `b6fbd877…`）。
- **四方门**：`python -I tools/ci/check_backup_plan_sync.py` → **EXIT=0 / PASS**（99 表 / 2 视图 /
  trimOrder 91 / masks [9,13,15] / digest `8567faae…`；未改 6C-2 mask）。
- **数据安全（5590）**：迁移 1..75 `OK (20 tests)`（1,446.843s；与 soak/R8 构建并发导致 I/O 等待高，
  非语义差异）；备份 34 `OK (34)`；trimmer `OK (10)`；rowCounts `OK (2)`；drill mask 15/9/13
  `OK (3)`、corrupted `OK (1)`、kill-stage `shortMsg=Process crashed.`（**设计性进程死亡**）、
  restore-from-handoff `OK (1)`。
- **3E 每日累计设备复验**：首跑 `Tests run: 4, Failures: 1` —— 唯一红
  `scanQuotaIsDailyCumulativeAcrossPlanCallsInTheSameStudyDay`，异常
  `IllegalArgumentException: Chat evidence weight 0.5 exceeds the 0.35 cap`（LearningState.kt:514）。
  根因 = **测试夹具**写 `weight=0.5`，与生产答错写口 `MasteryWriteGate.negativeWeight()=0.35` 及
  `ChatEvidenceSubmitted.MAX_CHAT_EVIDENCE_WEIGHT=0.35` 不一致（**生产路径不越界**）。本轨把设备夹具
  `StudyReviewPlanner3EScanInstrumentedTest.kt:700` 与 JVM 夹具
  `RoomBackedStudyExperienceRepositoryTest.kt:740` 的 `0.5` 改为 `0.35`（只改夹具字面量，不改生产、
  不放宽断言）→ 重建 core:data test APK → **OK (4 tests)**（6.376s）。JVM 同口径类 40 例 / 1 红：
  3E 相关 11 例全 pass（同日累计、跨日重置、预算先绑住不多取、被拒观察行计入、读失败 fail-closed）；
  唯一红是**与本轨改动无关**的 `profileStrengthsDropForgottenAndPrerequisiteSuppressedNodes`
  （`profile.knowledgePrerequisites[...]` 为 null，RoomBackedStudyExperienceRepositoryTest.kt:1969）
  ——主会话单测门的红，本轨未修、按实登记；单独点跑该用例仍确定性失败（排除顺序/并发偶发）。
- **新 UI**：KD-67 `OK (3)`；6B-3 + 5c 错因面板 `OK (24)`；5c 真库接缝 + JS painter `OK (25)`；
  5b 容量 / 新题预览 / AIGC 标签 / 能力屏 `OK (17)`。
- **R8 release 冒烟**：`:app:assembleLocalFirstRelease -Psmbk.versionCode=101177` BUILD SUCCESSFUL
  （minify + shrink，本地签名；mapping 140,191,204 B / R8 9.3.16）；5592 `uninstall`→`install` Success，
  versionCode **101177** / 0.2.0-localfirst；冷启 1,629ms；四 tab + 复习安排 / 数据与隐私 /
  存储备份导出三页渲染；crash 0 / FATAL 0 / ANR 0。**如实**：release 首启播种知识库，
  DB 实测 399,163,392 B（非空库；批次 50 为「空库」口径，本轮不同）。
- **2h soak**：release APK 101177 在 5592，monkey 驱动真实 UI；`soak/soak-summary.txt` →
  **OUTCOME=PASS / EXIT=0**：7200s、115 轮、pid 13400 全程未重启、TOTAL PSS 62,691→108,819 KB
  （只记录不设门）、crash_delta 0 / dropbox_delta 0 / monkey_rc_nonzero 0。为排查首启播种数据
  执行过 `adb root`，故 `smaps_rollup` 的 Pss 本轮可读（pss_samples=115；批次 50 为 0）。
- **协作**：开工 `git status --porcelain -- <目标>`；只改 2 个测试夹具字面量（M 文件、非整文件回写）；
  KB 在飞文件、门脚本、台账、金标、生产代码零改动。
- **未跑/未验证**：真机 / arm64 / 16KB 运行时、干净树复算、JVM 全量门 / lint / 强门、core:export
  设备用例、任何 connected Gradle 设备任务、Play / 备案 / 生产签名（外部阻塞）。
- 证据：`docs/research/evidence/2026-11-10-batch-51/device/`（README、`00–27-*` 日志、
  `50–58-release-*` 截图/XML、`soak/`、`35-soak-script.sh`、`40/41-*.sh`）。

## 发布线 · release 构建 / 16KB·ABI·签名·mapping / 干净树尝试 / 外部阻塞（子线「发布构建」，计划任务 36–38）

- **绑定**：HEAD `8dd29e96…`；开工 `git status --porcelain` **683** 行 / `-uall` **5443**；开工前
  `git status --porcelain -- app/build.gradle.kts tools/ci/j_release_verify.py tools/ci/aab_manifest_version.py
  tools/ci/release_version_code.py tools/ci/check_release_manifest.py app/src/main/AndroidManifest.xml app/src/debug`
  为 **0**（目标路径无他线在飞改动）。开工时唯一活动 Gradle daemon 是 `D:\smb-ui-forest-20261027` 的**空闲**
  daemon；本线全部 Gradle 调用串行执行，开工与关键节点 `jps` 观察未见其他构建。
- **versionCode**：`release_version_code.py` → commit_count **1177** / shallow false → **N=101177**；
  缺 `-P`/非整数/越界/缺签名变量 4 条红 + 正常 2 条绿（逐条 exit 记录）；三处读数一致
  （APK output-metadata、AAB proto manifest string==compiled、`version` 门 `--expected-code 101177`）。
- **出包**：`assemble/bundle × {strictOffline,localFirst} Release -Psmbk.versionCode=101177`
  **BUILD SUCCESSFUL 2m59s**（481 tasks / 40 executed / 441 up-to-date）；localFirst APK 当时 `UP-TO-DATE`
  （同日 07:38 冒烟产物），删除输出后强制重打包 **11s / 5 executed** 以消除归属歧义。
  产物：APK 156,229,823 / 156,230,405 B（sha `56e41d9c…` / `b6fbd877…`）；
  AAB 125,559,900 / 125,560,406 B（sha `600a8bb0…` / `5221905e…`）；
  mapping 140,191,204 B / 967,527 行 / R8 9.3.16（两 flavor 同 sha `80f7127a…`）。
- **校验（全绿）**：`apksigner` v1+v2 true（v3 false=minSdk 23 默认），DN `OU=NOT-PLAY-PRODUCTION`、
  证书 SHA-256 `2c8415b2…`；`jarsigner -verify` exit 0 + `keytool -printcert -jarfile` 同指纹；
  `zipalign -v -c -P 16 4` → `Verification successful`；`apk16k` 14/14 `.so` STORED、偏移 0 mod 16384、
  `elf_errors: []`；`aab16k` alignment **2**、ABI `{arm64-v8a,x86_64}`、7 个 native 库（含 libquickjs）；
  AAB proto versionCode **101177** 两 flavor；AAB 内嵌 `proguard.map` 与盘上 mapping 同 sha；
  `r8` 门 5 样本 dex 命中 + 负控 0；`check_release_manifest.py` exit 0（9 checked + 4 debug，5 组件）；
  `identity`（无 clean 要求）exit 0：revision=HEAD、behind=0、ZIP 完整。
- **负控（全红）**：version 差 1 / 缺 expected、AAB manifest 差 1、`apk16k --selftest`（3 条 p_align=0x1000）、
  debug APK vs release AAB 的 `signing`（身份不同）；判据单测 **33 passed**。
- **identity 如实**：`--require-clean --require-head` **exit 1**（dirty_paths 5475 ≠ 0）——脏树真实状态，
  不是脚本坏；APK 内 revision 只是 HEAD sha，不代表所编代码。
- **干净树（BLOCKED）**：本地 clone `D:/smbk-clean-b51-8dd29e96/src`（HEAD、status 0、non-shallow、
  未 stash/未改主树），只写 `sdk.dir`。首次 `--offline` 触发 wrapper 发行版下载（`gradlew.bat:78` 固定
  project-local `.gradle`；`--offline` 不覆盖 wrapper），停止；播种 wrapper dist + `modules-2`（817M）后
  离线 dry-run **BUILD SUCCESSFUL 1m2s（0 下载/0 No cached version）**；完整构建
  **BUILD FAILED 1m24s**：`:core:export:compileReleaseKotlin` → `Unresolved reference
  'AI_GENERATED_DOCUMENT_NOTICE'`（`DocxMistakeRenderer.kt:268`），定义文件
  `AiGeneratedDocumentNotice.kt` 在工作树是 **未跟踪 `??`**、HEAD 无 → **HEAD 自身不可编译**。
  clone 只含 HEAD、不含 683 条未提交改动，**不能为当前候选包背书**；出口=主会话提交必需未跟踪文件后重跑。
- **外部 BLOCKED**：Play 账号/Play Console、生产 keystore/上传密钥（本地钥 DN 明写 NOT-PLAY-PRODUCTION）、
  App 备案号（应用内占位「尚未取得」）、公网隐私政策 URL、图像 provider（MVA IMG 臂）。
  逐项所需动作/判据见 `release/external-blockers.md`；另登记 arm64/16KB 运行时 NOT_RUN、
  `bundletool-all` 官方交叉验证 NOT_RUN。
- **发布判定**：候选包“当前脏树 + 本地测试钥”的字节合规成立；**不可**称发布就绪/干净树可复现/生产签名。
- **证据**：`docs/research/evidence/2026-11-10-batch-51/release/`（`release-51.md`、`external-blockers.md`、
  55 文件 `EVIDENCE.sha256`、`clean-clone-*` 日志）。

## 验收台账 · 批次51 行映射与终态复算（验收台账员）

- 范围：只改 `docs/acceptance/ledger.md`、行级 review JSON、哈希清单与验收证据；
  未改产品代码、门阈值、金标、6C-2、JS 广告状态、KB 在飞文件。
- 台账：改前 `9044a779…`（933 PASS / 13 FAIL / 254 BLOCKED / 0 UNVERIFIED）→
  改后 **`1161bfc2dda774ce32606cfc764d428d26044a581a6a1c93f5f038425320fe48`**
  （**937 / 10 / 253 / 0**）；1200 行结构不变，`acceptance_init.py --check` exit 0。
- **4 个新增 PASS（全部有本批实跑）**：
  - **F-058**（2h soak 重复运行）：批次50 emulator-5590 / APK `1bf98b00` 与批次51
    emulator-5592 / APK `b6fbd877` 各 7200s / 115 轮；两窗 crash_delta=0、dropbox_delta=0、
    monkey 非零 0；`f058-soak-repeat-audit.log` EXIT=0 + 两个 device JSON。
  - **J-002**（产物 revision=HEAD）：`j_release_verify.py identity --require-head` 实跑 EXIT=0；
    revision `8dd29e96…` 是 commit、behind=0；J-001 仍 FAIL（dirty_paths 5489）。
  - **J-048**（vc 跨构建单调）、**J-052**（历史 AAB 与现 APK 可比）：批次50 APK/AAB
    `101160` → 批次51 APK/AAB `101177`（commit_count 1160→1177）。
- **27 行证据刷新、verdict 不变**：A-023/A-028、B-001、E-017/E-065/E-075/E-080、
  F-055/F-057/F-063、H-024/H-036/H-053、J-001/J-019/J-020/J-021/J-043、
  FIX-061/FIX-064、GATE-091/092/096/097/098/099/100。逐行映射见
  `evidence/2026-11-10-batch-51/acceptance/mapping-b51.md`。
- **四门不改判**：3D D-1 FAIL（FPC Wilson 下界 0.9291236<0.95；normal 0.8979148<0.90）；
  3E 设备 OK(4) 但台账无门行、不代签无条件 PASS；MVA FAIL（32/120=0.2667、6/6 组、
  端到端 p95 72.9551s、IMG 臂 BLOCKED）；AUD-08 FAIL（① 19/24=0.7917、③ 4/78=0.0513、
  fragmented=4）。
- **本轨实跑与 exit**：`ledger_audit_b51.py` 0；`pytest tools/tests/test_j_release_verify.py
  tools/tests/test_release_version_code.py` 0（33 passed）；`rebind_reviews_b51.py` 0
  （947 份 review JSON，PASS 行复算 0 错）；`acceptance_report --require-complete` **1**
  （`NOT_ACCEPTANCE_COMPLETE` + `REVIEW_CHECKS_SHARED`）；普通 report **1**（`state=FAIL`、
  `violations=[]`、`pass_with_review=937/937`、`invalid=0`、`row_specific=659`）；
  `acceptance_lint` **1**（162 × `EVIDENCE_REUSED`，无新增码）；`--warn-only` 0；
  `trace_requirements` 0（gaps=0）；三份哈希清单 0 mismatch（REVIEWS 948、验收层 2、台账员层 26）。
- **未跑/未验证**：未重跑 Gradle/设备/soak/Provider/MVA judge/AUD-08 切分门；未生成
  `verdicts.md`/`domain-index.md` 的批次51 投影（仍为批次50 版）；无第二人/第二模型语义复核
  （`independent_agent=false`）。**不得签“批次 51 验收通过”或“四门通过”。**
- 证据：`docs/research/evidence/2026-11-10-batch-51/acceptance/`（README、mapping、报告/lint/trace、
  审计日志、脚本、JUnit XML、SHA256SUMS）；验收层
  `docs/acceptance/evidence/2026-11-10-batch-51/`（device JSON、REVIEWS/EVIDENCE 清单）。

## 修复环（高/中）· 批次51 findings 逐条处置（子线「修复环」，2026-11-11）

- 输入：独立复核 findings 24 条（最后一条在输入中截断，按 3E README/台账证据补全为「3E 批次级
  无条件 PASS 未签字」）。**本环没有把任何门从 FAIL 改成 PASS**；3D/AUD-08/MVA 仍 FAIL，
  3E 仍无批次签字，发布仍 BLOCKED。完整逐条表与命令 exit 见
  `evidence/2026-11-10-batch-51/fix-loop/REPAIR-REPORT.md`。
- **已修复/已更正（7 组）**：
  - **3D 事实错误**：B50 的 43 条错误材料中 **4 条**进入 B51 样本且均判 CORRECT，
    **两轮错误 slug 交集才是 0**；4 条由同一 `deepseek-flash` 复测、非独立验证。
    `REPORT.md` §6.2/§7、本记录 3D 小节已改；只读证据 `fix-loop/b50-b51-overlap.json`
    （4 条逐条层/verdict/confidence；`verify_b50_b51_overlap.py` EXIT=0）。
  - **MVA p95 机器可读门**：原 `mva_gate_run.py` 把适配器渲染延迟（35.15 ms）当
    「请求→首图」，`met=true`。先红（`test_mva_gate.py -k p95` → 2 failed）后绿
    （18 passed；全套 84 passed）：代码改为 `author_elapsed_ms + render_latency_ms`，
    `mva_preflight.json`/`readings_digest.json` 重算为 **72.9551 s、met=false**；
    渲染侧 35.15 ms 只作 `render_only` 并列读数。阈值 30 s 未改。
  - **MVA 同源与关键错误可引用性**：README §7.2/§8、digest `independence.same_model=true`
    与 `critical_errors.interpretation` 已登记（判官与作者均 `deepseek-flash`；32/120 与
    6 组为同源自证；0 关键错误是结构性结果）。
  - **行级 review JSON 自相矛盾**：J-002/J-048/J-052 的 notes 把批次50 历史 FAIL 判词与
    批次51 机械翻 PASS 分开记录，新增 `prior_verdict=FAIL`、`verdict_flip.semantic_review=false`；
    `make_manifests_b51.py` REVIEWS 948/0 mismatch；`acceptance_report --require-complete`
    仍 EXIT=1、state=FAIL、937/10/253/0（预期红，非本环引入）。
  - **发布干净树**：`release-51.md` §7 固定修复环只读事实——HEAD `8dd29e96…` 上
    `git cat-file -e HEAD:<AiGeneratedDocumentNotice.kt>` exit **128**、文件 `??`、
    sha256 `f75ac862…`；仍 BLOCKED，提交属主会话。
  - **3E 签字状态**：`3e/README.md` §0/§6 明确设备 `OK (4 tests)` 已补跑，但台账无 3E 门行，
    行级投影 E-075 PASS + A-023 BLOCKED；批次级无条件 PASS **NOT SIGNED**。
  - **AUD-08 项23**：`split/acceptance/README.md` §5.1 明确「从未看过的新切片」未完成，
    本批只是同一 8 页第三次观测、本批唯一 run；不得记 citable PASS。
- **未修复/阻塞（如实）**：无门 PASS（3D 0.9291236<0.95、normal 0.8979148<0.90；AUD-08
  19/24、4/78、frag=4；MVA 32/120、RENDERED 95<108）；acceptance 无未见切片；3D κ NOT RUN；
  发布干净树/生产签名/外部五项 BLOCKED；D-3 召回 116/129（目标 117，需主会话裁定）；
  独立/对抗复核仍缺（本环反例扫描已标「非独立」）；台账 1200 行结构使 3E 无法加门行。
- **本环实跑 exit**：overlap 0；3D `recompute.py`/`recompute_detail.py`/`sample_d1.py --check`
  0/0/0（1600/1600 match）；MVA 修前 pytest 1（2 failed）、修后 18 passed/全套 84 passed；
  `mva_gate_run.py` 0（p95 72.9551 s）；digest patch 0；MVA SHA256SUMS 774/0 mismatch；
  release EVIDENCE 56/0；review notes 0；manifests 0；`acceptance_report --require-complete` 1（预期）。
- 未跑：Gradle/设备（无 Kotlin 改动；串行红线）、AUD-08 重跑（一次性承诺）、MVA 真实作者/判官调用
  （只重算已有产物）、soak、发布构建。低危未改：冻结框 v28、验收投影、提示词 sha 口径、3E patch 过期。

## 环后强门（post-fix）· 批次51 复跑（子线「环后强门」，2026-11-11）

- 范围：只跑三项——强门、修复环足迹定向单测、`acceptance_report`；未改源码/测试/台账/阈值/门定义/金标。
  HEAD `8dd29e96…`；开工脏树 `porcelain` 1633 / `-uall` 6468，收工 6475（+7 全为本子线证据文件）。
  Gradle 串行（7 条任务逐条、无重叠）；开工前仅见他仓 `D:\smb-ui-forest-20261027` 的闲置 daemon，
  无 GradleWrapperMain/构建客户端。
- **强门 PASS（exit 0）**：`bash build/run-batch51-strong-postfix.sh` → 7/7 exit 0、`GRADLE_EXIT=0`、
  `^e: ` 0、FAILED 任务 0；日志 `build/batch51-strong-postfix.log` 1002 行、sha256 `c67a3419…`、
  起止 03:02:54→03:04:37。`:core:ui` NO-SOURCE；`assembleLocalFirstDebug` UP-TO-DATE（APK 未重打包，
  sha `38cb991c…`、mtime 11-10 07:22）。强门只证明 androidTest 编译/打包，不执行测试。
- **修复环足迹定向单测 exit 0**：`REPAIR-REPORT.md` 改动清单无 Kotlin；`app core feature tools` 的
  `*.kt` 在 01:30–03:10 mtime 扫描 0 命中。`pytest tools/figure_eval` **84 passed**；
  `pytest tools/tests/test_acceptance_report.py test_acceptance_hash_manifest.py test_acceptance_lint.py`
  **42 passed**；合并最终盘上状态 **126 passed / exit 0**（日志 sha `248b556d…`）。
- **`acceptance_report` exit 1（真实未收口，与修复环读数一致）**：普通报告 `state=FAIL`、
  counts **937/10/253/0**、`pass_with_review=937/937`、`violations=[]`、`total=1200`、
  ledger sha `1161bfc2…`；`--require-complete` exit 1：`NOT_ACCEPTANCE_COMPLETE
  (UNVERIFIED=0 FAIL=10 BLOCKED=253)` + `REVIEW_CHECKS_SHARED`（20 组，最大 72 行）。不是脚本机械故障。
- **边界**：四门状态未变（3D/AUD-08/MVA 仍 FAIL，3E 仍无批次签字），不得读成「批次完成/四门通过」。
  未跑：设备/adb、10 模块 Kotlin 单测、AUD-08 重跑、MVA 真实作者/判官、`check_backup_plan_sync.py`、
  `check_split_gate.py`、`mva_gate.py` CLI、soak、release 构建（不在本 ask 三项点名检查内）。
- 证据：`evidence/2026-11-10-batch-51/post-fix-gate/`（README、4 份测试日志、2 份报告 JSON/stdout、
  SHA256SUMS 8/8 OK）。

---

# 批次 51 收口记录（§0–§8，2026-11-11，记录员）

> 本节在全部子线收口、修复环与环后强门之后追加；**上方各子线节原文未改**。子线节保留过程与一手读数，本节做跨线对账与终态。
> 计划：`.zcode/plans/plan-2026-11-10-batch-51-final.md`（117 行，已完整读取）。
> 基线：`docs/research/evidence/2026-11-10-batch-51/00-baseline.md`（开工 HEAD `8dd29e96`、619 脏行/3846 路径、8 处承接约束 C1–C8）。
> 终裁：`docs/acceptance/adjudication.md` §「批次 51 报告重生成终裁」B51-0…B51-6（本节落盘时同批追加）。
> 证据根：`docs/research/evidence/2026-11-10-batch-51/`（3d-rebind/3d-audit/3d-d2d3/3e/split/mva/mva-gate/js/device/release/acceptance/fix-loop/post-fix-gate/r1）
> 与 `docs/acceptance/evidence/2026-11-10-batch-51/`（`REVIEWS.sha256` 948 项、`EVIDENCE.sha256` 2 项）。
> 本文件结构：§0 口径 → §1 阶段对账 → §2 四门结果 → §3 JS/产品面 → §4 发布 → §5 门禁/设备 → §6 复核/修复 → §7 遗留 → §8 提交清单。

---

## 0. 口径与声明

### 0.1 判定与四门读法

- **verdict 四值**：PASS=行判据在当前证据下成立；FAIL=判据不成立且缺陷已登记；BLOCKED=解除条件缺失（**不是通过**）；UNVERIFIED=本环境无法复核（本批 UNVERIFIED=0）。
- **四门结论必须按门定义读，不能合并成一句“四门通过”**：
  1. **3D = FAIL**。D-1 主判据（单侧 95% Wilson 下界 ≥0.95）**0.9291236 < 0.95**；预注册 no-FPC 护栏 normal **0.8979148 < 0.90**；D-2 四项为“清零或登记”；D-3 五红 12/12 绿，但召回 **116/129**（未保持 117/129）。**不得读成“3D 通过”或“改绑已修复 3D”**。
  2. **3E = 设备复验成立、批次级无条件 PASS 未签字**（`3e/README.md` §0/§6、`fix-loop/REPAIR-REPORT.md` §2.6）。设备 `OK (4 tests)` 有日志；但台账 1200 行没有 3E 门行、A-023 仍 BLOCKED，**不计无条件 PASS**。
  3. **MVA = FAIL（有真实可评样本的 FAIL，不是 NOT_EVALUABLE）**。首次=最终 **32/120=0.2667**；RENDERED **95/120** 上界已使首次门（需 108）与最终门（需 114）必然 FAIL；6/6 组 FAIL；端到端 p95 **72.9551 s > 30 s**；IMG 臂 BLOCKED。
  4. **AUD-08 = FAIL**。合并门 ① **19/24=0.7917 < 0.9**、③ **4/78=0.0513 > 0.05**、`fragmentedQuestions=4`；`check_split_gate.py` **exit 1**。
- **计划完成定义核对**：本批“3D/3E/AUD-08 至少两门真实 PASS”**未达成（真实 PASS = 0 门）**；“MVA 有真实可评样本与门值”达成但门 FAIL；“数据安全在 75 上设备复验”达成；“发布件可复现/干净树”未达成；“独立 + 对抗复核各至少 1 份产物”无独立签字件；“推送后远端 = 本地”未做（基线 `git ls-remote` 25s 超时）。**不得签“批次完成/发版水平”。**
- **本批未改任何门阈值**：3D 0.95/0.90、3E 每日名额 10、MVA 90/95/90/0 与 p95 30 s/120 s、AUD-08 0.9/0.05/切残 0，均与计划/上一批一致。

### 0.2 终态台账与迁移

| 量 | 批次 50 终态 | 批次 51 终态 | 变化 |
|---|---:|---:|---:|
| PASS | 933 | **937** | +4 |
| FAIL | 13 | **10** | −3 |
| BLOCKED | 254 | **253** | −1 |
| UNVERIFIED | 0 | 0 | — |
| 合计 | 1200 | 1200 | 0 |

- 台账：`docs/acceptance/ledger.md` sha256 `1161bfc2dda774ce32606cfc764d428d26044a581a6a1c93f5f038425320fe48`（本记录员实测；数据行 1200，文件 1231 行含表头/说明）。
- **10 FAIL 逐行（本记录员按台账六列复算）**：**F-057**；**J-001/J-020/J-021**；**GATE-032/GATE-038/GATE-042/GATE-091/GATE-092/GATE-099**。域：F 1、J 3、GATE 6。
- **253 BLOCKED 域分布（本记录员复算）**：A 25、B 2、C 2、D 8、E 9、F 31、G 7、H 16、I 37、J 47、FIX 65、GATE 4（合计 253）。
- **4 个新增 PASS**（验收轨自报，本记录员读证据）：F-058（2h soak 重复窗口）、J-002（产物 revision=HEAD）、J-048（vc 跨构建单调）、J-052（历史 AAB 与现 APK vc 可比）。**J-002/J-048/J-052 是机械复算 PASS**（`review_kind=mechanical-recompute`、`independent_agent=false`），不是第二人/第二模型的语义复核；修复环已补 `prior_verdict=FAIL` 与 `verdict_flip.semantic_review=false`。
- **27 行证据刷新、verdict 不变**（A/B/E/F/H/J/FIX/GATE），映射见 `evidence/2026-11-10-batch-51/acceptance/mapping-b51.md`。

### 0.3 四门与发布口径（引用时必须带）

1. **3D D-1 是 model-labeled 暂定值**：`labeler_kind=model`、`human_expert=false`、单模型 `deepseek/deepseek-flash`；判定一致性 **κ = NOT RUN**，按 R2 §4.5 不得用于门级签字。本轮即使读数达标也不得签 PASS。
2. **3D 两轮不可作配对前后比较**：新 audit id `B51-D1-2026-11-10` → 新 seed → 新样本；两轮样本仅 **68/1600** 重叠，包从 v28 换到 v29。B50 的 43 条错误材料中 **4 条**进入本批样本且均判 CORRECT，**两轮错误 slug 交集才是 0**；这 4 条由同一 `deepseek-flash` 复测，**不是独立验证**（`fix-loop/b50-b51-overlap.json`）。
3. **3D 采样框沿用 v28 冻结快照** `ce7d6823…`（62,944 行/2,481 节点），绑定内容为 v29；D-2 另登记 v29 复算差异（2,483 节点/62,969 条）。计划项 11 的“新抽样框快照”只做到“新快照文件 + 新 audit id”，**框本身未换**。
4. **3E 的承诺是“每日实际复习（有 KNOWLEDGE_QUIZ 落账）的扫描点 ≤10（无重排时严格）”**，不是“每日下发/计划 ≤10”；残留 R（当日重排）、R2（作答未落行）、R3（非扫描来源的当日测验）使当日“计划过的”去重节点可 >10。
5. **MVA 的 120 集已转回归集**：前序轨用 120 集观测到产品样例缺陷并据此修 prompt ⇒ 本轮读数只能作回归集读数，**不能作启用 PASS 依据**；判官与作者链同模型（deepseek-flash，仅温度不同）⇒ 32/120 与 6 组读数是**同源自证**；`critical_errors_accepted=0` 是结构性结果，不是能力证据。
6. **AUD-08 没有“从未看过的新切片”**：金标 v2 只有 24 页（dev 16 + acceptance 8），同一 8 页已先后在批次 48、50、51 三次观测；本轮是**第三次观测、本批唯一一次 run**，held-out 不可恢复；即使达标也不得记 citable PASS。
7. **发布签名 = 本地测试身份**：keystore DN `OU=NOT-PLAY-PRODUCTION`、证书 SHA-256 `2c8415b2…5eba`；两包由**脏工作树**构建，APK 内嵌 `revision=HEAD` 不代表所编代码；干净 clone 构建在 HEAD 上**不可编译**（未跟踪 `AiGeneratedDocumentNotice.kt`）。
8. **台账升级不改四门**：937 个 PASS 是行级验收面，不是门值；`--require-complete` 仍 exit 1。

### 0.4 本记录员本轮实跑 / 未跑（2026-11-11）

**实跑（本会话命令与读数，输出落 gitignored `build/b51-record/`）**：

| # | 命令 | exit | 关键读数 |
|---:|---|---:|---|
| 1 | `git rev-parse HEAD` | 0 | `8dd29e963975ff44703f638064322be0e7494f98` |
| 2 | `git status --porcelain \| wc -l` / `-uall` | 0 | **1633** / **6477** |
| 3 | `sha256sum docs/acceptance/ledger.md` | 0 | `1161bfc2…20fe48`（数据行 1200，本记录员 Python 复算） |
| 4 | `python -I tools/ci/acceptance_report.py --out build/b51-record/report.json` | **1** | `state=FAIL`；**937/10/253/0**；`violations=[]`；`pass_with_review=937`、`without=0`、`invalid=0`、`row_specific=659`；head `8dd29e96`、dirty **6477** |
| 5 | 同脚本 `--require-complete --out …/report-require-complete.json` | **1** | `NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=10 BLOCKED=253)` + `REVIEW_CHECKS_SHARED`（20 组，最大 72 行） |
| 6 | `python -I tools/ci/acceptance_lint.py --ledger … --out build/b51-record/lint.json` | **1** | total 1200；violations **162**，全部 `EVIDENCE_REUSED`；点名批次 51 证据 **0** 条、点名批次 50 证据 **22** 条 |
| 7 | `python -I tools/ci/trace_requirements.py --ledger … --out build/b51-record/trace.json` | **0** | total 1200、gaps **0** |
| 8 | `python -I tools/ci/acceptance_init.py --check` | **0** | 无输出（1200 行结构无漂移） |
| 9 | 台账六列逐行复算（Python） | — | **937/10/253/0**；10 FAIL 名单与 253 BLOCKED 域分布见 §0.2 |
| 10 | `sha256sum -c docs/acceptance/evidence/2026-11-10-batch-51/REVIEWS.sha256`（仓根） | **0** | **948/948 OK**（947 行级 JSON + `verdict.schema.json`） |
| 11 | `sha256sum -c docs/acceptance/evidence/2026-11-10-batch-51/EVIDENCE.sha256` | **0** | **2/2 OK**（`soak-b51.device.json`、`j002-identity-require-head.json`） |
| 12 | `sha256sum -c docs/research/evidence/2026-11-10-batch-51/acceptance/SHA256SUMS` | **0** | **26/26 OK** |
| 13 | `sha256sum -c docs/research/evidence/2026-11-10-batch-51/mva-gate/SHA256SUMS` | **0** | **774/774 OK** |
| 14 | `sha256sum -c docs/research/evidence/2026-11-10-batch-51/release/EVIDENCE.sha256` | **0** | **56/56 OK** |
| 15 | 读 `3d-audit/stats.json` | — | `verdict=FAIL (main)`、`lower_95=0.9291236`、`per_layer_guardrail_pass=false`、1501/89/10；overfull 0.9290572（pass）/ normal 0.8979148（fail） |
| 16 | 读 `split/acceptance/gate/gate-report-b51-primary.json` | — | `gate.state=FAIL`、`passed=false`；① 0.7916667 `met=false`；③ 0.0512821 `met=false`；hardRedLines.fragmentedQuestions=4 |
| 17 | 读 `mva-gate/gate/readings_digest.json` | — | first/final 32/120=0.2667 `met=false`；6/6 组 `met=false`；p95 72,955.1 ms `met=false`（render_only 35.15 ms）；`same_model=true` |
| 18 | `grep` soak summary | — | `OUTCOME=PASS`、7200s、115 轮、`crash_delta=0 dropbox_delta=0 monkey_rc_nonzero=0` |
| 19 | `tail device/26-instrument-3e-rerun.log` | — | `OK (4 tests)`、`Time: 6.376`、`EXIT=0` |
| 20 | `sha256sum` 三个 release 产物 | 0 | APK `b6fbd877…ed46f`、AAB `5221905e…67449`、mapping `80f7127a…c84bf`；mapping 967,527 行 |
| 21 | `git cat-file -e HEAD:…/AiGeneratedDocumentNotice.kt` | **128** | 文件只在磁盘、不在 HEAD；`??`；sha256 `f75ac862…2ac74a1` |
| 22 | `sha256sum core/data/.../moe-2025-four-subjects-v1.json` | 0 | `aaf81be884933b9105db737feee3eb3d84ff440c53f0e2d01c806aafd3790032` |
| 23 | `cmp docs/acceptance/acceptance-report.json …/acceptance/acceptance-report.json` | 0 | **逐字节相同** |
| 24 | `grep` 3E 实现与测试名 | — | `studyDayStartEpochMillis`/`readChatEvidenceKnowledgeNodeIds`/`fullLibraryScanDailyCap` 在树；`scanQuotaIsDailyCumulative…` 在 JVM 与设备测试 |
| 25 | `python -I tools/ci/check_backup_plan_sync.py` | **0** | `PASS 四方一致（code / §9.14 机器块 / golden / 75.json）`；99 表/2 视图/trimOrder 91/masks [9,13,15]/digest `8567faae…`；v4 fixture 3/3 字节一致 |

**未跑（不得读成通过）**：任何 Gradle 任务（红线：不与其他 agent 并发；本记录员零 Gradle）、任何 `am instrument`/设备 UI、2h soak、真实 Provider 调用、VLM 判官、AUD-08 切分门重跑（一次性红线）、MVA 门重跑。下文 Gradle/设备/soak/发布读数均标为**归档读数（读自证据文档、日志、XML、JSON）**。

### 0.5 证据边界（必须与结论一起读）

1. **行级复核是机械复算**：937 个 PASS 行全部有行级 JSON，`pass_without_review=0`、`pass_with_invalid_review=0`、`review_validation_errors=[]`；本记录员**逐文件扫描 947/947**：`review_kind=mechanical-recompute`、`independence.independent_agent=false`，是脚本复算，不是第二人/第二模型语义复核。`pass_with_row_specific_review=659/937`，20 组 ≥5 行共用（本记录员复算 202 行，最大 72 行）。
2. **哈希清单本批全绿**：REVIEWS 948/948、验收层 EVIDENCE 2/2、acceptance 目录 26/26、mva-gate 774/774、release 56/56（本记录员逐项 `sha256sum -c`）；与批次 50 的 4 处清单滞后不同，本批未发现清单漂移。复现者须**在仓根**执行 REVIEWS 清单（清单内路径相对仓根）。
3. **证据复用告警仍在**：`acceptance_lint` 162 条 `EVIDENCE_REUSED` 全部来自旧批次（22 条点名批次 50，**0 条点名批次 51**）；本批新证据未被台账行共用，但 shared checks 仍使 `--require-complete` 阻断。
4. **投影未更新**：`docs/acceptance/verdicts.md`（`42f58ee1…`）与 `docs/acceptance/domain-index.md`（`9f1a0da3…`）仍是**批次 50 投影**、引用旧台账 sha `9044a779…`；计数以 `ledger.md` + `acceptance-report.json` 为准。
5. **共享脏树**：HEAD 未提交本批代码；工作树含他线在飞（ReviewReminder/visualjs 未跟踪设备用例/KB 工具等）。发布产物是脏树构建，`apk` 内嵌 `revision=HEAD` 不代表所编代码。
6. **设备边界**：两台均 x86_64 / API 34 / PAGE_SIZE 4096 **模拟器**，非真机；无 arm64、无 16KB 运行时。
7. **门脚本入库状态**：`tools/figure_eval/mva_gate.py`、`mva_gate_run.py`、`mva_vlm_judge.py`、`fact_extract.py` 等仍为 `??` 未跟踪；`tools/split_golden/check_split_gate.py` 与 `measure_split_accuracy.py` 为 ` M`。从 HEAD 干净树复算 MVA/AUD-08 门做不到。

---

## 1. 阶段对账（计划 45 项）

图例：✓=完成且有证据在盘；✓*=完成但带如实保留/部分达成；✗=未执行或目标未达成；BLOCKED=外部条件缺失。

| # | 计划项 | 结果 | 证据 / 说明 |
|---:|---|---|---|
| 1 | P0 HEAD/工作树/热区快照 | ✓ | `00-baseline.md`：HEAD `8dd29e96`、619 行/3846 路径、6 处先认账 C1–C8 |
| 2 | P0 热区快照（JS/UI/KB 在飞） | ✓ | `00-baseline.md §2`：KB `materials.jsonl`、`tools/kb_build`、`tools/kb_coverage` 在飞；JS/UI `app`/`feature` 在飞清单 |
| 3 | P0 设备/AVD/凭据可用性（不打印密钥） | ✓ | `00-baseline.md §4`：emulator-5590/5592、11 个 AVD、仅文本 provider（无图像键）、只记键名 |
| 4 | R1 切分口径与提示词 | ✓ | `docs/research/2026-11-10-split-metric-prompt.md`；探针 `r1/probe-output.txt` |
| 5 | R2 每日累计配额 | ✓ | `docs/research/2026-11-10-daily-scan-quota.md`（方案 A 零 schema） |
| 6 | R3 MVA 事实提取 + VLM judge | ✓ | `docs/research/2026-11-10-mva-facts-judge.md` |
| 7 | 43 条错误逐条处置 | ✓ | `3d-rebind/dispositions.csv`（43 行：39 R + 1 覆盖 + 3 DROP）、`per-item-before-after.csv` |
| 8 | 改绑应用到 shipped pack + staging | ✓ | 40 行 `rebound=40/skipped=0/bad_to=0`；3 条 DROP；pack `10ede1bf…`→`aaf81be8…`；staging 逐字节等于 release |
| 9 | pack 身份/清单/完整性门 | ✓ | `check_pack_sync.py` 0、`check_asset.py` 11/11 0、`run_kb_checks.py` 全 OK 0；manifest v28→v29；contentVersion `ce8edb8f…`→`5f164dab…` |
| 10 | 编译 + 定向单测 | ✓ | `:core:data:compileDebugKotlin` EXIT=0；18 类 **77 tests / 0 fail / 0 error** EXIT=0 |
| 11 | 新抽样框快照 + 新 audit id | ✓* | audit `B51-D1-2026-11-10`；快照文件 `3d-audit/frame/overfull_nodes.snapshot.csv` sha `ce7d6823…`。**框仍是 v28 冻结框**（62,944/2,481），v29 复算差异另登记（`frame-divergence.json`） |
| 12 | 32 分片盲判 + check_raw | ✓ | 32 片×50=1,600；`check_raw.py` 32 片全 `problems=0`；`judgments.csv` 引文逐字 1,600/1,600 |
| 13 | 分层加权 + Wilson 汇总 | ✓ | `stats.json`/`recompute.py`/`recompute_detail.py` 全 `all_match=true`；主判据 0.9291236、护栏 normal 0.8979148 |
| 14 | 3D 门报告 | ✓ | `3d-audit/REPORT.md` 如实 **FAIL (main)**（不写 PASS） |
| 15 | D-2 结构审计（近重复/超配/容量） | ✓ | `3d-d2d3/`：近重复 0/0；候选 2 组裁决保留长正文（退休未执行）；超配 RETAIN_NO_CAP + 2,481 行台账；容量 91 行 22/12/44/13；type 三值外 0；工具 26 passed |
| 16 | D-3 五红清零 + 召回 117/129 | **✗** | 五红 5 类 12 例 **12/0/0/0**；但召回 **116/129 = 0.8992**（-1；case#97 单题翻转，归因 KD-24 别名位移）；24 章 diff 只有 1 章下降 |
| 17 | KD-46 每日累计（零 schema） | ✓ | 只读 SQL + 端口 + Room 实现；无 schema 76/备份清单/机器块/trimOrder/CLOSURE 改动；6C-2 mask 未触碰 |
| 18 | 单测：同日累计/跨日/预算 | ✓ | 先红 5/7 → 后绿 7/7；`:core:domain:test --rerun` 0；`:core:data` 全量 1709/4 红（均为既有基线，无新增） |
| 19 | 3E 设备真实验证 → 无条件 PASS | ✓* | 设备 `OK (4 tests)`（`device/26-…log`，6.376s）；**批次级无条件 PASS 未签字**（台账无门行、A-023 BLOCKED） |
| 20 | AUD-08 口径修正（v3） | ✓ | `measure_split_accuracy.py` 页类/弃答/通道不可评/null 题数；批次 50 ① 7/24→6/24（纠正假阳性） |
| 21 | 提示词重写（题号/裁切/多栏/整页/键） | ✓ | v3.3 2,204 字符 sha `7f92b456…`；6 条规则 + 三层键白名单 + 示例；type 键硬白名单保留 |
| 22 | dev 16 页调参 | ✓ | 4 版 + 同版复测：v3 ①1/16 → v3.1 9/16 → v3.2 13/16 → v3.3 **14/16（0.875）**；复测 12/16；<0.9 |
| 23 | 新 acceptance 切片（从未看过）只跑一次 | ✓* | 本批唯一 run `acceptance-b51-01`（8 页/26 题）；**仓内无“从未看过”切片**，同一 8 页为**第三次观测**；计划项 23 未真正完成 |
| 24 | 判门 ①≥0.9 / ③≤0.05 / frag=0 | ✓* | `check_split_gate.py` **exit 1 → FAIL**（① 19/24、③ 4/78、frag 4）；如实判红 |
| 25 | JS 臂设备适配器 | ✓ | `MvaJsArmAdapterInstrumentedTest`：真引擎 + 度量 + PNG + RenderScene；98 程序 → RENDERED **95** |
| 26 | 作者链（文本模型产出程序） | ✓ | `MvaFigureAuthorHarness`（产品 `FigurePromptAssembler` 正文 + 产品解析 + 直连 HTTPS）：**OK 98/120** |
| 27 | 事实提取器（DrawOps→facts） | ✓* | `fact_extract.py` 26 例先红后绿；A1-only 可评 47/120；`step_order/point_x/y/segment_length_ratio/ascending_order` 在真实布局上有缺口（记 UNDECODABLE） |
| 28 | VLM judge + 校准 | ✓ | `mva_vlm_judge.py`（盲判 + 注入错值校准 + SSRF 边界）；校准 6/6 读出、3/3 正确接受、3/3 错值拒绝；正式 20 题 OK 20/20 |
| 29 | 120 盲测跑分 + 门判（90/95/90/0、p95） | ✓* | 门 **FAIL**：首次/最终 32/120、6/6 组 FAIL、p95 72.9551s、IMG 臂 BLOCKED；上界 95<108 独立成立 |
| 30 | MVA 通过才注入 + 广告 | ✓ | MVA FAIL ⇒ **零代码改动**：三参装配、工厂 JS 两参 null、6 处 `IMAGE_MODEL_ONLY`、能力快照零改动 |
| 31 | math 标签实现或显式降级 | ✓* | 维持显式降级（painter 诊断 `explicitly degraded`；度量期失败→原文 text + `degraded=true`）；本批**设备面未跑** |
| 32 | KD-76/71/73/82 核销 | ✓ | 均保持 open；KD-78 维持 closed（残留复验）；KD-73 静态门本轮 0/exit 0；KD-82 JVM 7/0 |
| 33 | schema 75 设备迁移/备份/trimmer/rowCounts/drill | ✓ | 迁移 **OK(20)**（1,446.843s）、备份 **OK(34)**、trimmer **OK(10)**、rowCounts **OK(2)**、drill mask **OK(3)** + corrupted **OK(1)** + kill-stage 设计性崩溃 + handoff **OK(1)** |
| 34 | 新 UI 设备用例（5b/5c/6B-3/KD-67） | ✓ | KD-67 **OK(3)**；6B-3+5c **OK(24)**；5c 真库+JS painter **OK(25)**；5b/AIGC 标签/能力屏 **OK(17)** |
| 35 | 2h soak + R8 release 冒烟 | ✓ | soak **PASS**（7200s/115 轮/pid 未重启/0 新 crash/0 dropbox）；release vc 101177 冷启 1,629ms、crash 0/FATAL 0/ANR 0 |
| 36 | release 构建（vc/AAB 签名/16KB/mapping） | ✓ | vc 101177 三处一致；APK v1+v2、AAB jarsigner 0；zipalign `-P 16` successful、apk16k 14/14、aab16k alignment 2；mapping 140,191,204 B |
| 37 | 干净树构建尝试（本地 clone） | **✗ BLOCKED** | clone 干净、离线配置成功；完整构建在 `:core:export:compileReleaseKotlin` 失败（`AI_GENERATED_DOCUMENT_NOTICE` 未解析）——HEAD 自身不可编译；本记录员复核 `git cat-file -e` **exit 128** |
| 38 | Play/备案/隐私 URL 外部 runbook | ✓* | `release/external-blockers.md` 五项逐项动作；Play 账号/生产钥/备案号/公网 URL/图像 provider 均 **BLOCKED**；无商店素材产物 |
| 39 | 证据入批节目录 | ✓ | 15 个子目录 + 基线；见 §0 证据根 |
| 40 | 台账 + 行级 review JSON + 哈希清单 | ✓ | 1200 行 937/10/253/0；947 份 review JSON 换绑；REVIEWS 948/948、验收层 2/2、acceptance 26/26 |
| 41 | `acceptance_report --require-complete` | ✓* | **exit 1**：`NOT_ACCEPTANCE_COMPLETE` + `REVIEW_CHECKS_SHARED`（如实未收口，不是脚本故障） |
| 42 | 独立复核（只读复算） | ✓* | 修复环输入引用“独立复核 findings 24 条”（`fix-loop/REPAIR-REPORT.md:4`）；**仓内无独立签名的复核报告文件**，不能核为第二主体产物 |
| 43 | 对抗复核（找反例） | ✓* | 修复环 §4 反例扫描 6 条（3D 事实/MVA p95/同源/判词/发布/3E），**自标“不是独立复核”** |
| 44 | 修复环（高/中）+ 环后强门 | ✓* | 8 修 / 9 阻塞 / 4 低危未改 / 3 正向核实；环后强门 **7/7 exit 0**（只编译/打包）；定向 pytest **126 passed** |
| 45 | 记录 + 终裁（发版判定逐项） | ✓ | 本节 + `adjudication.md` B51-0…B51-6 |

**对账结论**：45 项中 **✓ 31 项、✓* 12 项、✗ 1 项（#16 D-3 召回未达 117）、✗/BLOCKED 1 项（#37 干净树）**。0 项洗绿；0 项门值改动；0 项外部项伪造。**四门真实 PASS = 0。**

---

## 2. 四门结果

### 2.1 3D（D-1 / D-2 / D-3）

| 子项 | 判据 | 实测 | 判定 | 证据 |
|---|---|---|---:|---|
| **D-1 主判据** | 分层加权精确率单侧 95% Wilson 下界 ≥0.95 | **0.9291236**（加权点 0.9403574，SE 0.0063028，n_eff 1411.8253） | **FAIL**（差 −2.0876pp） | `3d-audit/stats.json`、`REPORT.md §4.2` |
| **D-1 护栏** | 每层 no-FPC 下界 ≥0.90 | overfull **0.9290572 通过**；**normal 0.8979148 未通过**（差 −0.2085pp；FPC 0.9005926 并列，不改判） | **FAIL** | `stats.json#per_layer`、`REPORT.md §4.3` |
| **D-1 总判** | 主判据 ∧ 护栏 | `verdict="FAIL (main)"`、`overall_pass=false` | **未过门** | `stats.json#verdict` |
| **D-1 标注** | 人工专家 | `labeler_kind=model`、`human_expert=false`、`blind_to_layer=true`；κ **NOT RUN** | **model-labeled 暂定值** | `stats.json#metadata`、`REPORT.md §7` |
| **D-1 错误** | 错误清单 | **99 条 = 89 REBIND + 10 NONE**（overfull 77=69R+8N；normal 22=20R+2N）；学科 CHEM 40/BIO 25/PHY 19/MATH 15 | 登记 | `errors.csv` |
| **D-1 可评性** | 无 NOT_EVALUABLE/BLOCKED | 1,600/1,600 有判定；32 片 `problems=0` | 完整 | `verdicts_raw/`、`check_raw` 实跑 |
| **D-2 近重复** | 0 组/0 对 | 节点级 **0/0**；候选区 2 组裁决“保留长正文、退休另一条（**未执行**）”；材料级候选 173 对/50 簇/拟退休 1/SKIP 20 | 清零/登记 | `d2-near-dup-scan.json`、`d2-near-dup-dispositions.csv` |
| **D-2 超配** | 清零或登记 | 冻结框 **2,481 节点/62,944 条/超出 53,020**（最大 146）；v29 复算 2,483/62,969/53,037；**RETAIN_NO_CAP** + 2,481 行台账；分诊 49/1,009/1,423 | 登记 | `d2-overfull-ledger-frozen-frame.csv`、`frame-divergence.json` |
| **D-2 容量 91 条** | 逐行处置 | 22 同节点/12 异节点/44 未入包/13 节点退役；批次 50 废止补丁仍 **NOT_APPLIED** | 登记 | `d2-capacity-dispositions.csv` |
| **D-2 历史 type** | 三值外 = 0 | 64,850 条全 3 值（35,484/25,436/3,930），**三值外 0** | 单源成立 | `d2-type-distribution.csv` |
| **D-3 五红** | 12/12 绿 | 5 类 12 例 **12/0/0/0**，EXIT=0 | 清零 | `d3/five-red/TEST-*.xml` |
| **D-3 召回** | 保持 117/129 | 端侧 **116/129=0.8992**（MRR 0.7174）；JVM 融合 116/129（MRR 0.7211，MISS 13）；词面 101/129；**1 章下降**（牛顿运动定律 1/1→0/1，case#97） | **未保持（−1）** | `d3/sim-v29.txt`、`d3/jvm-fused-metrics-v29.txt` |

**归因（子线已交付，未实修）**：case#97 由该节点词面计数 7→4 单独造成（固定 v29 稠密分与候选域，只换回计数 7 ⇒ 命中）；链条 = P1 正确改绑 → 别名闭合 +1 → 片段 70→72 → 16 片段位置平衡采样整体换批 → 丢含“加速/加速度/速度”的片段。实修三条路径（删别名/改索引/调 α）均触既有红线或属调参，**需主会话/用户裁定**。

### 2.2 3E（队列/预算/每日累计）

| 判据 | 读数 | 证据 |
|---|---|---|
| 语义 | 日键=学习日 04:00；已作答=`KNOWLEDGE_QUIZ` 且 `created_at∈[学习日 04:00,+24h)` 的 DISTINCT 节点（含拒绝行）；已花费=已作答−今日第一档可出题节点；剩余=`max(0,10−已花费数)`；回放不占名额；读失败 fail-closed | `3e/README.md §1`、`3e/logs/` |
| 零 schema | 无新表/列/迁移/76.json/备份清单/机器块/trimOrder/CLOSURE；6C-2 未触碰 | `3e/README.md §2` |
| 先红后绿 | 红：7 例 5 红 EXIT=1；绿：同命令 7/7 EXIT=0；`:core:domain:test --rerun` 0 | `3e/README.md §3`、`3e/logs/` |
| 全量回归 | `:core:data:testDebugUnitTest --rerun` **1709 / 4 failed / 1 skipped**（4 红为批次 49/50 既有，无新增） | `3e/logs/03-jvm-data-full.log` |
| 设备复验 | `StudyReviewPlanner3EScanInstrumentedTest` 首跑 1 红（夹具 `weight=0.5>0.35` 上限）→ 改夹具字面量（设备+JVM 两处）→ **OK (4 tests)** / 6.376s | `device/20-…log`、`25-…build.log`、`26-…log` |
| 设备 4 例覆盖 | 全库捞回 10（全在今日并集外）/ F8 预算先绑 / **同日第二次累计 ≤10** / 无考试日指纹一致 | `device/26-…log` |
| 残留 | R（当日重排⇒上界 10+R）、R2（作答未落行⇒只少计）、R3（非扫描来源当日测验占名额并回放⇒计划过节点可 >10）、时钟回拨/恢复/清库；查询 P95 未量；C5 未实现 | `3e/README.md §5–§6` |
| **门判** | **设备复验成立；批次级无条件 PASS NOT SIGNED**（台账 1200 行无 3E 门行；行级 E-075 PASS + A-023 BLOCKED） | `3e/README.md §0/§6`、`fix-loop/REPAIR-REPORT.md §2.6` |

### 2.3 MVA（视觉质量，JS 臂有真实样本）

| 项 | 读数 | 证据 |
|---|---|---|
| 作者链 | `deepseek-flash` temp 0.1，8 并发：**OK 98/120**（INVALID_PROPOSAL 17、NOT_GENERATE 5）；分组 OK：proportion/mechanics/flow/natural 各 20、source 16、**handwriting 2** | `mva-gate/author-blind-2/`、`README.md §3` |
| 设备渲染 | emulator-5590：98 程序 → **RENDERED 95 / REJECTED 3**；95 张 PNG 非空白；确定性检查 render/geometry/text 95/95 | `mva-gate/device-blind-2/out/`、`README.md §4` |
| 事实提取（A1） | rendered 95 → evaluable 47、accepted 25；缺口：step_order 7、point_x/y 各 5、segment_length_ratio 4、ascending_order 1（A2 numeric 另有 27 条 critical） | `facts-without-judge/`、`README.md §5.1` |
| VLM judge（C 通道） | 校准 **6/6 读出、3/3 正确接受、3/3 注入错值拒绝**；正式 20 题 OK 20/20、回答 32 条事实（direction 24 + phase_order 5 + wave_count 3） | `calibration/`、`judge-blind-2/`、`README.md §5.2` |
| 合并可评性 | **59/120 = 0.4917**；首次=最终 **32/120 = 0.2667**（无重试环） | `gate/readings_digest.json` |
| 门判 | 首次 0.2667 <0.90 **FAIL**；最终 0.2667 <0.95 **FAIL**；每组 flow 2、handwriting 0、mechanics 0、natural 14、proportion 1、source 15 → **6/6 FAIL**；p95 **72.9551 s >30 s FAIL**（render_only 35.15 ms 并列）；IMG 臂 **BLOCKED** | `gate/readings_digest.json`、`README.md §6` |
| 上界 | RENDERED **95/120=0.7917 <0.90**（首次需 108）；最终需 114 ⇒ **门 FAIL 不依赖未判事实/判官** | `README.md §7.1` |
| 关键错误 | 0（无“自我接受后交付”阶段，结构性结果）；`citable=true` 只表示有样本可机械读出，**不作能力证据** | `readings_digest.json#gate.critical_errors` |
| 独立性 | 判官与作者均 `deepseek-flash`（仅温度 0.1/0.0）⇒ 32/120 与 6 组为**同源自证** | `readings_digest.json#independence` |

### 2.4 AUD-08（切分）

| 指标 | 读数 | 门 | 判定 |
|---|---:|---|---|
| acceptance 单切片 ① | **7/8 = 0.8750**（8 页/26 题，口径 v3） | ≥0.9 | 未过 |
| acceptance 单切片 ③ | **1/26 = 0.0385** | ≤0.05 | 过 |
| acceptance 单切片 ② | 17/26 = 0.6538 | 只度量 | — |
| 合并主门 ① | **19/24 = 0.7917**（dev-v33-02 + acceptance） | ≥0.9 | **未过** |
| 合并主门 ③ | **4/78 = 0.0513** | ≤0.05 | **未过** |
| 切残硬红线 | **4**（004511/004854/004952/005855） | =0 | **未满足** |
| 敏感性门（dev-v33-01） | ① 21/24=0.8750、③ 3/78=0.0385、frag 3 | — | 亦 FAIL |
| **门判** | `check_split_gate.py` **exit 1**；`gate.state=FAIL`、`passed=false`；门后自动化不启用 | | **FAIL** |

- 失败页：`004952`（金标 2 vs 应用 3，多切一题 + 1 处切残）、`004353/011358`（题数一致但 ② 边界偏差）、`011800`（单题页 PASS 被 v3 正确记一致）。
- **观测次数如实**：本轮是同一 8 页的**第三次观测**（`acceptance/README.md §5.1`）；`gate-report-b51-primary.json` 的 scope 文案仍写“第二次观测”（批次 50 语境；修复环只更正了 gate-evidence md，JSON 未改 — F22）。无“从未看过的新切片”，计划项 23 未真正完成。

---

## 3. JS / 产品面

**终态：MVA FAIL ⇒ JS 不注入、能力不广告；math 维持显式降级；本子线代码改动 = 0；KD-76/71/73/82 保持 open，KD-78 维持 closed。**

| 项 | 现树事实 | 证据 |
|---|---|---|
| visualEngine 注入 | **未注入**：生产装配仍三参 `FigureGenerationCoordinationFactory.create(database, configurationStore, networkRequestsAllowed)`；工厂 `visualEngine=null`、`visualFrameInputs=null` 默认 | `SmartMistakeBookApplication.kt:387-391`、`DefaultFigureGenerationCoordinator.kt:886-891` |
| 能力广告 | **未广告**：6 处 `IMAGE_MODEL_ONLY`（`ConfiguredCleanImageGenerator.kt:95`、`RoomDetailedSolutionRepository.kt:256`、`RoomMistakeEditRepository.kt:258`、`FigurePromptAssembler.kt:124`、`AttachedImageGenerator.kt:144`、`TutorFigureGenerator.kt:372`）；能力快照零改动 | `js/README.md §1.3`；JVM `FigurePromptCapabilityInjectionTest` 7/0 |
| MVA 产品提示词修复 | `JS_STATIC_SAMPLE` 原 `stroke` 字符串 + path 无 `fill` → 真实编译器报 `SCENE_FIELD_TYPE`；修为 `fill` + `stroke:{width}` 对象 + label 必填 + `contentId` 约定；先红（`compiles:false`）后绿（`compiles:true, ops:3`） | `mva-gate/README.md §2`、`test_prompt_sample_compiles.py` |
| 修复效果 | dev 24 编译率 6/20 → **18/19**；120 题 98 个 OK 程序 **95 编译通过（96.9%）**，95/98 用 `metrics[i].contentId` | `mva-gate/README.md §2/§3` |
| math 标签 | 维持显式降级：painter `PAINTER_LABEL_MATH` 诊断含 `explicitly degraded`；预览同码拒绝；度量期失败→原文 text + `degraded=true`（不静默丢字）；**本批设备面未跑** | `VisualDrawOpsPainter.kt:194-202`、`VisualContentMeasuring.kt:455-466`、`js/README.md §2` |
| KD-78 | 维持 closed：生成门/回放兜底同码；`node tools/visual_compiler/run_tests.mjs` 94/0；产品样例残留已修（见上） | `js/README.md §3`、`docs/known-defects.md:3139` |
| KD-76 / KD-71 / KD-73 / KD-82 | 均 **open**；KD-76 登记“MVA 未过”；KD-73 静态门 `0 unresolved / 23,859 refs`、pins quickjs 1.0.15 + stdlib 2.3.21；KD-82 JVM 7/0（known limitation） | `js/README.md §4`、`docs/known-defects.md` |
| 定向单测 | `:core:data` 6 过滤类 + `:app` visualjs：**147 tests / 0 failures / 0 errors / 0 skipped**，两次 Gradle EXIT=0；Node 94/0 | `js/junit/`、`js/test-results.json` |

**残余（必须随产品面结论引用）**：MVA 读数只能作回归集读数、不能作启用 PASS 依据；`FigurePromptAssembler` 的 `policyVersion` 未随正文改动升版（残余风险，需产品裁定）；math 盒回放未实现（显式降级是产品裁定，不是待办占位）；JS 设备面本批 UNVERIFIED。

---

## 4. 发布（逐项判定）

> 依据：`release/release-51.md`、`release/external-blockers.md`、`device/README.md`；本记录员实算了 APK/AAB/mapping 三个 sha 与 `git cat-file` 事实。
> 本地 release key 明确非 Play 生产身份；Play 账号/备案号/公开 URL/图像 provider 为外部项，标 **BLOCKED**，未伪造。

| # | 项 | 实测 | 判定 |
|---:|---|---|---|
| 1 | **可签名 AAB** | 双 flavor AAB `jarsigner -verify` exit 0；`keytool -printcert` 指纹 = APK 证书 `2c8415b2…5eba`；signing 门 exit 0（APK/AAB 同身份、证书未过期、0 tracked keystore） | **PASS（本地测试 key）**；**生产签名 BLOCKED**（DN `OU=NOT-PLAY-PRODUCTION`） |
| 2 | **16KB** | `zipalign -v -c -P 16 4` → `Verification successful`；apk16k 14/14 `.so` STORED、`offset%16384=0`、`elf_errors: []`；aab16k `alignment: 2`（PAGE_ALIGNMENT_16K）、ABI `{arm64-v8a,x86_64}`、7 库全在 | **静态 PASS**；**运行时 UNVERIFIED**（仅 4096 页 x86_64 模拟器；无 arm64 真机） |
| 3 | **versionCode** | `101177 = 100000 + git rev-list --count HEAD(1177)`；APK output-metadata / AAB proto manifest / `version` 门三处 **101177**；`validateVersionCode` 缺参/非整数/越界 fail-closed；verifier `--expected-code` 双 flavor exit 0 | **PASS（注入与一致性）**；J-048/J-052 已按机械复算转 PASS；J-001 仍 FAIL（脏树）；下次发版必须 **>101177** |
| 4 | **mapping** | `mapping.txt` **140,191,204 B / 967,527 行** / R8 9.3.16；本记录员实算 sha `80f7127a…c84bf`；与 AAB 内嵌 `proguard.map` 同 sha；`r8` 门 exit 0（5 样本命中 + 负控 0） | **PASS** |
| 5 | **干净树** | clone `D:/smbk-clean-b51-8dd29e96/src`（HEAD、status 0、非 shallow）离线配置成功；完整构建 `:core:export:compileReleaseKotlin` FAILED（`AI_GENERATED_DOCUMENT_NOTICE` 未解析）；本记录员 `git cat-file -e HEAD:<同路径>` **exit 128**、文件 `??`、sha `f75ac862…` | **BLOCKED**（HEAD 自身不可编译；须主会话提交必需未跟踪文件后重跑） |
| 6 | **AIGC** | 本批**未重跑** `h_compliance_gate`；沿用批次 50：H-052/H-061/H-062 PASS、H-053 门红（占位符/外部未取得）；本批设备复跑 `AiContentLabelsInstrumentedTest` 在 OK(17) 聚合内 | **PASS（沿用批次 50 证据）**；**H-053 外部 BLOCKED**（CAC 备案/上线编号未回填） |
| 7 | **隐私** | 应用内《隐私政策》全文页与 data safety 清单在树；备案号/运营主体/联系方式仍渲染“尚未取得”；本批未新增公网 URL；H-024/H-036/H-053 行证据刷新、verdict 不变 | **PARTIAL**：**H-024 公开 URL BLOCKED、H-036 Play 表单 BLOCKED** |
| 8 | **Play 账号 / 生产签名 / 备案号** | 无 Play 账号、无生产 keystore、无备案号；本地 key 明示非 Play | **BLOCKED（外部）** |
| 9 | **商店素材/上架清单** | 证据目录未发现商店素材/上架清单产物；`external-blockers.md` 只列解锁动作 | **BLOCKED（外部）/未产出** |

**发版判定**：**不成立**。当前脏工作树 + 本地测试钥可稳定产出两组 APK/AAB，versionCode/签名/16KB/ABI/AAB 版本字段/mapping/manifest 门按既有判据通过、负控按预期红；但**干净树、生产签名、Play 账号、备案号、公网隐私 URL、图像 provider、H-053 回填均未取得**，且 3D/AUD-08/MVA 三门仍 FAIL。**不得对外称“发布就绪/可上架/干净树可复现”。**

---

## 5. 门禁 / 设备

### 5.1 门禁（本会话实跑 + 归档读数）

| 门 | 命令（简） | exit | 关键值 | 轮次 |
|---|---|---:|---|---|
| `acceptance_report` | `python -I tools/ci/acceptance_report.py --out …` | **1** | **937/10/253/0**；`state=FAIL`；`violations=[]`；`pass_with_review=937`；invalid/without 0；row_specific 659；shared 20 组 | **本会话实跑** |
| `--require-complete` | 同脚本 | **1** | `NOT_ACCEPTANCE_COMPLETE (0/10/253)` + `REVIEW_CHECKS_SHARED` 20 组 | **本会话实跑** |
| `acceptance_lint` | `--ledger …` | **1** | total 1200；**162** 条 `EVIDENCE_REUSED`（b51 0 条 / b50 22 条） | **本会话实跑** |
| `trace_requirements` | `--ledger …` | **0** | 1200 行、gaps **0** | **本会话实跑** |
| `acceptance_init --check` | — | **0** | 无输出 | **本会话实跑** |
| 哈希清单 | 5 份 `sha256sum -c` | **0** | REVIEWS 948/948、验收 EVIDENCE 2/2、acceptance 26/26、mva-gate 774/774、release 56/56 | **本会话实跑** |
| 备份四方门 | `python -I tools/ci/check_backup_plan_sync.py` | **0** | `PASS 四方一致`；99 表/2 视图/trimOrder 91/masks [9,13,15]/digest `8567faae…`；v4 fixture 3/3 字节一致 | **本会话实跑** |
| 强门（post-fix） | `bash build/run-batch51-strong-postfix.sh` | **0** | 7/7 任务 exit 0；`GRADLE_EXIT=0`；`^e: ` 0；FAILED 0；日志 sha `c67a3419…` | 归档（`post-fix-gate/README.md §1`） |
| 修复环足迹定向 pytest | 3 套件合并 | **0** | **126 passed**（figure_eval 84 + 验收工具 42）；日志 sha `248b556d…` | 归档（`§2`） |
| 3D 判定/复算 | `check_raw`×32、`compile_judgments`、`recompute`、`recompute_detail` | **0** | 32 片/1,600 行/0 problems；`all_match=true` | 归档（`3d-audit/REPORT.md §8`） |
| AUD-08 判门 | `check_split_gate.py` | **1** | ① 19/24、③ 4/78、frag 4 → FAIL | 归档（本会话**未重跑**） |
| MVA 门/判官/提取 | `mva_gate_run.py` / `mva_gate.py` / `mva_vlm_judge.py` | 0（工具）/ 门 FAIL | 32/120、6/6 组、p95 72.9551s、IMG BLOCKED | 归档 |
| MVA 工具单测 | `pytest tools/figure_eval -q` | **0** | **84 passed**（修复环后） | 归档（`fix-loop/REPAIR-REPORT.md §5`） |
| JS 静态/回归 | Node 94/0；JVM 147/0；stdlib/pins 0 | **0** | 见 §3 | 归档（`js/README.md §5`） |
| release 判据自检 | `pytest test_j_release_verify.py test_release_version_code.py` | **0** | **33 passed** | 归档（`acceptance/README.md §3`） |

**不得读成通过**：强门 exit 0 只证明 androidTest 源码编译与打包（`:core:ui` `NO-SOURCE`、`assembleLocalFirstDebug` `UP-TO-DATE` 未重打包）；`acceptance_report` 两个 exit 1 是批次级未收口（10 FAIL/253 BLOCKED）；**本批未跑 10 模块全量 Kotlin 单测门**（只有 3E 轨的 `:core:data` 全量 1709/4 红与各线定向类）。

### 5.2 设备（emulator-5590 / emulator-5592，x86_64 / API 34 / PAGE_SIZE 4096，模拟器非真机）

| 面 | 结果 | 证据 |
|---|---|---|
| 快照绑定 | HEAD `8dd29e96` + 脏树（开工 683 行 / `-uall` 5418）；APK：debug `38cb991c…`、core:data test `2516876f…`（修夹具后）、core:database `5a8ca2b6…`、feature:library `63ffb00c…`、release `b6fbd877…` | `device/00-snapshot.txt`、`03-apk-shas.txt` |
| 四方备份门 | `check_backup_plan_sync.py` **EXIT=0 / PASS**；99 表/2 视图/trimOrder 91/masks [9,13,15]/digest `8567faae…` | `device/07-…log` |
| 迁移 1..75 | **OK (20 tests)**，1,446.843s（与 soak/R8 并发导致 I/O 等待，非语义差异） | `device/05-…log` |
| 备份 34 / trimmer 10 / rowCounts 2 | **OK(34)** / **OK(10)** / **OK(2)** | `device/08–10-…log` |
| drill | mask 9/13/15 **OK(3)**；corrupted **OK(1)**；kill-stage 设计性 `Process crashed.`；handoff **OK(1)** | `device/11–14-…log` |
| 3E 每日累计 | 首跑 1 红（夹具 weight 0.5>0.35）→ 修夹具 → **OK (4 tests)** / 6.376s | `device/20,25,26-…` |
| 新 UI | KD-67 **OK(3)**；6B-3+5c **OK(24)**；5c 真库+JS painter **OK(25)**；5b/AIGC/能力屏 **OK(17)** | `device/21–24-…log` |
| release 冒烟 | `:app:assembleLocalFirstRelease -Psmbk.versionCode=101177` BUILD SUCCESSFUL；5592 安装 vc **101177** / 0.2.0-localfirst；冷启 1,629ms；四 tab + 三深层页；crash 0/FATAL 0/ANR 0 | `device/50–58-*` |
| 2h soak | **PASS**：7200s / 115 轮 / pid 13400 未重启 / `crash_delta=0 dropbox_delta=0 monkey_rc_nonzero=0` / TOTAL PSS 62,691→108,819 KB（只记录不设门） | `device/soak/soak-summary.txt` |
| 如实口径 | release 首启自动播种知识库，soak 跑在已播种库（DB 399,163,392 B），**非空库**；`adb root` 仅为采样可读，不改变判据 | `device/README.md §5–§6` |

### 5.3 本轮新增的登记项（如实）

1. **KD-46 台账文本漂移（低危）**：`docs/known-defects.md:2070` 仍写“fixed 2026-10-18（口径按计划默认=每次排计划 ≤10）”，本批实现已改“每日累计”。3E README 明确未改 KD 台账（留验收阶段），修复环也未改；**代码与测试是每日累计，KD 文本是旧口径**。
2. **AUD-08 门 JSON 的观测次数文案**：`gate-report-b51-primary.json#scope.reasons` 仍写“本批是第二次观测”（批次 50 语境），人读证据 md 与 README §5.1 已更正为**第三次**；JSON 由共享门脚本生成，未改（F22）。
3. **投影旧版**：`verdicts.md`/`domain-index.md` 未重生成（批次 50 版，引用旧台账 sha），计数以 `ledger.md` + 报告为准。
4. **新增正向读数**：`acceptance_lint` 162 条 `EVIDENCE_REUSED` 中**点名批次 51 的 0 条**（批次 50 为 22 条）——本批新证据未被行级共用；但 20 组 shared checks 仍阻断 `--require-complete`。

---

## 6. 复核 / 修复

### 6.1 独立复核与修复环（`fix-loop/REPAIR-REPORT.md`）

- **输入**：独立复核 findings 24 条（最后一条在输入中截断，按 3E README/台账证据补全为“3E 批次级无条件 PASS 未签字”）。**仓内没有独立签名的复核报告文件**，本节只能确认“findings 被引用并逐条处置”，不能核为第二主体产物。
- **处置分布**：**已修复 8**（3D 事实错误 F6/F16、MVA p95 端到端 F18、MVA 同源披露 F3/F17、MVA 关键错误可引用性 F10、行级 review JSON 自相矛盾 F19、AUD-08 项23 F22）／**如实阻塞 9**（无门 PASS、acceptance 切片复用、κ NOT RUN、发布干净树、独立/对抗复核、D-3 召回、3E 未签字）／**低危未改 4**（冻结框 v28、验收投影、提示词 sha 口径、3E patch 过期）／**正向核实 3**。
- **关键修复的实跑**：
  - 3D 事实错误：`verify_b50_b51_overlap.py` EXIT=0，输出 4 条进入 B51 样本且 4/4 CORRECT、错误交集 0、样本交集 68；`3d-audit/REPORT.md §6.2/§7` 与记录 3D 小节已改。
  - MVA p95：先红 `test_mva_gate.py -k p95` → 2 failed；后绿 18 passed；`mva_gate_run.py` 改为 `author_elapsed_ms + render_latency_ms`；`readings_digest.json` 重算 **72.9551 s / met=false**（`p95_ms_pre_fixloop` 保留旧 35.15 ms）。
  - 行级 review JSON：J-002/J-048/J-052 分离“历史 FAIL 判词”与“本批机械翻转”，加 `prior_verdict=FAIL`、`verdict_flip.semantic_review=false`；REVIEWS 948/0 mismatch。
  - 发布：固定 `git cat-file -e` exit 128 事实；干净树仍 BLOCKED。
- **修复环没有把任何门从 FAIL 改成 PASS**；3D/AUD-08/MVA 仍 FAIL，3E 仍无批次签字。

### 6.2 对抗复核（修复环 §4，**不是独立复核**）

反例扫描 6 条并已修掉：3D 报告“43 条交集 0”事实错误；MVA p95 机器门误用 35 ms；MVA 判官/作者同源未点名；J-002/J-048/J-052 判词自相矛盾；发布“签名/16KB 全绿=可发布”的反例（HEAD 不可编译）；3E “设备 OK(4)=已通过”的反例。**修复环自标“不是独立复核”；计划项 43 只做到“非独立反例扫描”。**

### 6.3 环后强门（`post-fix-gate/README.md`）

- 强门 **7/7 exit 0**（`:core:data/:core:database/:core:ui(NO-SOURCE)/:core:export/:app` 两 flavor androidTest 编译 + `:app:assembleLocalFirstDebug`；日志 1002 行 sha `c67a3419…`）。**只编译/打包，不执行测试**。
- 定向 pytest **126 passed**（figure_eval 84 + 验收工具 42；合并日志 sha `248b556d…`）；修复环窗口 `app core feature tools` 的 `.kt` mtime 扫描 0 命中（修复环无 Kotlin 改动）。
- `acceptance_report` 复跑与修复环读数一致：exit 1、937/10/253/0、`violations=[]`、20 组 shared checks。**不得读成收口。**

### 6.4 本记录员的复核发现（登记，不改 verdict）

1. 台账/报告/清单三方一致：ledger sha `1161bfc2…`、报告 `937/10/253/0`、REVIEWS 948/948；报告副本与 `docs/acceptance/evidence/2026-11-10-batch-51/acceptance-report.json` **逐字节相同**。
2. 门值与证据文档逐项一致：3D 0.9291236/0.8979148、AUD-08 19/24 & 4/78 & frag 4、MVA 32/120 & 72.9551s、soak PASS、3E OK(4)。
3. **新发现（低危）**：KD-46 台账旧口径（§5.3-1）；AUD-08 门 JSON 观测次数文案（§5.3-2）；`verdicts.md`/`domain-index.md` 仍批次 50 版（§5.3-3）。
4. **本批未跑**：10 模块全量单测门、lint/detekt、设备复跑、soak、release 重建、任何四门重跑；§5 的相应读数为归档值。
5. **独立/对抗复核无独立签字件**：修复环输入 24 条 findings 与 §4 反例扫描均不能替代“第二主体独立复核”。

---

## 7. 遗留（全部如实，含 BLOCKED/UNVERIFIED）

1. **批次验收不通过**：`acceptance_report` exit 1；10 FAIL / 253 BLOCKED 未消除；`--require-complete` 两阻断（FAIL=10、20 组 shared checks）。**不得签“批次 51 验收通过”或“发版水平”。**
2. **四门真实 PASS = 0**：3D FAIL（主判据+护栏）、3E 无批次签字、MVA FAIL、AUD-08 FAIL；计划“至少两门真实 PASS”未达成。
3. **3D D-1 FAIL**：主判据 **0.9291236 < 0.95**；normal 护栏 **0.8979148 < 0.90**；99 错；κ **NOT RUN**、model-labeled；**不得把 0.9403 点估计当可用门值**。
4. **3D 改绑效果未获独立证明**：B50 43 条中仅 4 条进入 B51 样本且同模型复测全 CORRECT；两轮错误交集 0 不等于“改绑已修复”；两轮样本仅 68/1600 重叠、不是配对比较。
5. **3D D-3 召回未保持**：116/129（目标 117/129，−1）；case#97 单题翻转归因 KD-24 别名位移；三条实修路径均需主会话/用户裁定。
6. **D-2 只登记未治理**：超配 2,481 节点/62,944 条 RETAIN_NO_CAP；容量 91 行逐行登记、废止补丁 NOT_APPLIED；候选区 2 组与材料级 1 条退休未执行；别名饱和 2,487/3,279 节点 >16（KD-24 open）。
7. **3E 批次级无条件 PASS 未签字**：设备 OK(4) 成立，但台账无 3E 门行、A-023 BLOCKED；残留 R/R2/R3、时钟回拨/恢复/清库；查询 P95 未量、C5 未实现。
8. **KD-46 文档漂移**：`docs/known-defects.md:2070` 仍写“每次排计划 ≤10”的旧修复口径（代码已是每日累计）；低危但引用时必须说明。
9. **MVA FAIL（非不可评）**：32/120、6/6 组、p95 72.9551s、RENDERED 95<108；A2 角色解码器（27 条 critical）、A1 真实布局缺口、handwriting 作者 2/20、source 16/20 均为结构性阻断。
10. **MVA 方法学边界**：120 集已转回归集（无新的未见 120 集）；判官与作者同源；无人工复核；`policyVersion` 未升版；设备适配器未走生产 `VisualJsClientEngine`/隔离进程。
11. **IMG 臂 BLOCKED**：无图像 provider（只有文本/对话 key）；不得用现有键冒充图像后端；IMG 臂质量结论不存在。
12. **AUD-08 FAIL**：① 19/24、③ 4/78、frag 4；**无“从未看过”的新 acceptance 切片**（同一 8 页第三次观测）；金标 G1 系统抽样/单模型盲标；0 张双栏页；结构限制（004511/011649 坐标帧转置、011017 框高 0.030<0.04）。
13. **干净树发布 BLOCKED**：HEAD `8dd29e96` 缺未跟踪 `core/export/.../AiGeneratedDocumentNotice.kt`（sha `f75ac862…`），`:core:export` 编译失败；当前 APK/AAB 非干净树产物；主会话提交后须重 clone + 重出包 + 重跑发布门。
14. **生产签名/Play/备案/隐私 URL BLOCKED**：本地 key `OU=NOT-PLAY-PRODUCTION`；J-020/J-021 仍 FAIL；H-024/H-036/H-053 BLOCKED；备案号/主体/联系方式/模型备案写“尚未取得”；商店素材未产出。
15. **16KB 运行时 UNVERIFIED**：两台设备均 x86_64/4096 页/API 34；无 arm64 真机；`aab16k --selftest` 不存在（批次 50 记录 R7 U-10 缺口，本批未复核）。
16. **AIGC 未重跑合规门**：本批沿用批次 50 的 H-052/061/062 PASS 与 H-053 红；`h_compliance_gate --all` 本批 **NOT RUN**。
17. **行级复核机械 + shared checks**：937/937 有效但 `independent_agent=false`；659/937 行级特有；20 组 202 行共用（最大 72）；J-002/J-048/J-052 为机械翻 PASS。
18. **独立/对抗复核无独立签字件**：修复环输入 24 条 findings 与 §4 反例扫描自标非独立；计划项 42/43 未以可核产物达成。
19. **全量单测门未跑**：本批无 10 模块 `testDebugUnitTest --continue --rerun` 读数；`core:data` 全量 1709/4 红均为既有；强门只编译打包。
20. **设备/soak 边界**：仅两台 x86_64 模拟器；无真机/TalkBack/IME/对比度人工签字；soak PASS 只等价于“7200s 无新增 crash/dropbox、monkey 全 0”，不等于内存/电池稳定；soak 跑在首启已播种库（399 MB）。
21. **投影与发布版本**：`verdicts.md`/`domain-index.md` 仍批次 50 版；AUD-08 门 JSON 观测次数文案旧；versionName 仍 `0.2.0`（本批只注入 versionCode）。
22. **远端未验证**：本批无提交/推送；基线 `git ls-remote origin refs/heads/main` 25s 超时，**不得称“远端 = 本地”**。

---

## 8. 提交清单建议

**范围声明**：以下按各子线自报改动文件汇总，**不是全树 diff**；工作树当前 `git status --porcelain` **1633 行**、`-uall` **6477 路径**，含他线在飞文件（ReviewReminder/visualjs 未跟踪设备用例/KB 工具/根目录散件等）**不列入本清单**。提交时严禁 `git add -A` / `git add .`；以显式路径重列。`local.properties`（gitignored）、`build/**`、`.tmp/**`、`docs/research/evidence/**/*.log`（`*.log` 被 `.gitignore` 忽略）等不入库或需 owner 决定 `git add -f`。

> **状态时点**：HEAD `8dd29e963975ff44703f638064322be0e7494f98`（main），**批次 51 尚未提交**。`??`=未跟踪新增；`M`=已跟踪修改。提交会改变 `dirty_paths`，**不改变终裁**。

| 块 | 路径（组） | 状态 | 说明 |
|---|---|---|---|
| 1 3D 改绑/审计 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json`、`moe-2025-update-manifest.json`（M）；`docs/research/evidence/2026-11-10-batch-51/3d-rebind/**`、`3d-audit/**`、`3d-d2d3/**`（??） | M/?? | pack v29；D-1 FAIL；D-2 登记；D-3 召回 116/129；dense 资产/侧车已随 pack 重生成（见 `3d-rebind/README.md`） |
| 2 3E | `core/database/.../dao/ChatEvidenceDao.kt`、`ModelTaskDatabasePort.kt`、`RoomStudyDatabase.kt`（M）；`core/domain/.../KnowledgeReviewQueue.kt`、`KnowledgeReviewFullLibraryScan.kt`、`StudyExperienceRepository.kt`（M）；`core/data/.../study/StudyReviewPlannerService.kt`（M）；`core/data/src/test/.../RoomBackedStudyExperienceRepositoryTest.kt`、`core/domain/src/test/.../KnowledgeReviewQueueTest.kt`、`core/data/src/androidTest/.../StudyReviewPlanner3EScanInstrumentedTest.kt`（M）；`…/3e/**`（??） | M/?? | 零 schema；设备 OK(4)；KD-46 台账文本未改（§5.3-1） |
| 3 AUD-08 | `core/data/.../model/OpenAiModelTaskAdapters.kt`、`core/data/src/test/.../GoldenSplitEvalChannelHarness.kt`、`tools/split_golden/measure_split_accuracy.py`、`tools/split_golden/check_split_gate.py`（M）；`…/split/**`（??）；`docs/research/2026-11-10-split-metric-prompt.md`（??） | M/?? | 提示词 v3.3；口径 v3；acceptance 第三次观测；门 FAIL |
| 4 MVA | `core/data/.../model/FigurePromptAssembler.kt`（M）；`core/data/src/androidTest/.../visual/MvaJsArmAdapterInstrumentedTest.kt`（??）；`tools/figure_eval/{fact_extract.py,mva_facts_run.py,mva_vlm_judge.py,mva_gate.py,mva_gate_run.py,author_program_compile_check.mjs,prompt_sample_compile_probe.mjs,test_fact_extract.py,test_mva_gate.py,test_mva_vlm_judge.py,test_prompt_sample_compiles.py}`（??）；`…/mva/**`、`…/mva-gate/**`（??）；`docs/research/2026-11-10-mva-facts-judge.md`（??） | M/?? | 产品样例修复；门 FAIL；工具未入库（C5） |
| 5 JS/产品面 | `docs/known-defects.md`（M）；`…/js/**`（??） | M/?? | 本子线代码改动 0；MVA 未过 → 不注入/不广告；KD 登记；`docs/current/visual-generation-contract.md` 的 M 属批次 50 math 降级（非本批自报，不列入本块） |
| 6 设备/数据安全 | `…/device/**`（??）；两处夹具字面量 `weight=0.5→0.35`（M，文件在块 2 已列） | ??/M | 迁移/备份/trimmer/rowCounts/drill、新 UI、release 冒烟、soak |
| 7 发布 | `…/release/**`（??） | ?? | 本批无发布代码改动（`app/build.gradle.kts` 未改）；候选包为脏树+本地钥；干净树 BLOCKED |
| 8 验收台账 | `docs/acceptance/ledger.md`（M）；`docs/acceptance/verdicts/rows/**`（M，947 份）；`docs/acceptance/acceptance-report.json`（M）；`docs/acceptance/evidence/2026-11-10-batch-51/**`（??）；`…/acceptance/**`（??） | M/?? | 937/10/253/0；REVIEWS 948；报告与证据副本逐字节相同；`verdicts.md`/`domain-index.md` 未重生成 |
| 9 记录/终裁 | `docs/research/2026-11-10-batch-51-record.md`（??）；`docs/acceptance/adjudication.md`（M，B51 终裁）；`docs/research/2026-11-10-{split-metric-prompt,daily-scan-quota,mva-facts-judge}.md`（??）；`docs/research/evidence/2026-11-10-batch-51/00-baseline.md`（??） | ??/M | 与终裁同时落盘 |

- **建议提交顺序**：块 1–2（KB/3E 数据层）→ 块 3–4（切分/MVA 工具）→ 块 5（JS/KD）→ 块 6–7（设备/发布证据）→ 块 8（验收，绑台账 sha `1161bfc2…`）→ 块 9（记录/终裁）。
- **禁止**：`git add -A`；他线在飞（`ReviewReminder*`、`app/.../visualjs/*InstrumentedTest` 未跟踪件、`knowledge-production/wusan/materials.jsonl`、`tools/kb_build/*`、`tools/kb_coverage/*`、`feature/**` 在飞 M、根目录散件）；`local.properties`；`build/**`；`.tmp/**`；`docs/research/evidence/**/*.log`（除非 owner 决定 `git add -f`）。
- **提交前复核**：重跑 §5.1 的 `acceptance_report`（预期 exit 1、937/10/253/0）、`--require-complete`（预期 exit 1）、`trace_requirements`（0 gaps）；确认哈希清单 5 份仍全绿；**先提交 `core/export/.../AiGeneratedDocumentNotice.kt` 等“HEAD 引用、工作树未跟踪”必需文件，再做干净 clone 构建**；推送后 `git ls-remote origin refs/heads/main` 实测成功才可写“远端 = 本地”。
- **提交不改变终裁：NOT ACCEPTED 维持；发版不成立。**

---

## 9. 收口后主会话补验（2026-11-11，提交后）

> 本节在主会话提交并推送 `3660daff` 之后追加；上方子线节与 §0–§8 原文未改。
> 目的：验证「干净树可构建」这一条是否因补提交 `AiGeneratedDocumentNotice.kt` 而解除。

### 9.1 推送

- `3660daff` 经后台重试第 16 次 `PUSH_OK`，`origin/main = 3660daff`（本地=远端）。
- 期间 github:443 多次 `Connection reset` / 连接超时；非仓库问题。

### 9.2 干净 clone 定向编译：通过

- clone `D:/smbk-clean-b51-8dd29e96/src` 快进到 `3660daff`，`git status --porcelain` **0 行**，
  `AiGeneratedDocumentNotice.kt` 已在版本内。
- `./gradlew.bat --offline :core:export:compileReleaseKotlin` → **BUILD SUCCESSFUL in 49s**
  （证据 `release/clean-clone-export-b51.log`）。**§4 #5 的「HEAD 缺未跟踪文件」子项解除。**

### 9.3 干净 clone 完整 release 构建：仍 BLOCKED（暴露更深的自洽性缺口）

- `:app:assembleLocalFirstRelease -Psmbk.versionCode=101178`（一次性本地测试钥，DN
  `OU=NOT-PLAY-PRODUCTION`，不入库）：
  1. 首次 FAILED `:core:database:compileReleaseKotlin`：`RoomStudyDatabase.kt` 的
     `paperReviews`/`freezeAnswerManifest`/`readLatestLiveTutorVisualSnapshot` 等 **overrides nothing**
     —— 对应接口成员只在工作树的 `StudyDatabasePort.kt` + `TutorVisualPort.kt`（未提交）。
     主会话补提交 23 行接口声明（`d2b87009`）。
  2. 重跑 FAILED `:core:data:compileReleaseKotlin`：
     - `StudySnapshotBuilder.kt:154` 向 `ReviewPlanBundle.toOverview` 传 `capacity`，但 HEAD 的
       `StudyExperienceMappers.kt` 尚无该参数（5b 容量预览的 mapper 半边未提交）；
     - `RoomTutorToolRunner.kt` 的 `.associate`/`.candidates` 与召回口改形（B35 wrapper）不一致
       （对应未提交改动在别的文件）。
- 证据：`release/clean-clone-assemble-b51.log`（`^e:` 行与 BUILD FAILED 原文）。
- **第三层（补 5b/B35 两文件后，`661ac211`）**：仍 FAILED `:core:data` ——
  `RoomBackedStudyExperienceRepository.kt:929/938/956` 使用 `KnowledgeQuizQuestionSlot` 与
  `slot`/`itemId` 参数，HEAD 的 `KnowledgeQuizItemStore` 尚无这些成员（KD-67 第二道校准题的
  数据半边未提交）。
- **停止条件**：已连续补 3 层（export 文件 / DB 端口 23 行 / 5b+B35 48 行），每层都只是把下一个
  未提交特性暴露出来。这不是「再补一个文件」的问题，而是**HEAD 的已提交调用点引用了一组跨线
  未提交特性**（5b 容量、B35 召回、KD-67 校准题数据层）。主会话在此停止逐个补文件，转 owner
  裁定：要么把这些特性连同调用点成组提交，要么回退已提交的调用点。**干净树 release 构建
  维持 BLOCKED。**
- **结论**：`3660daff`/`d2b87009`/`661ac211` 的 HEAD **仍非自洽**；干净树 release 构建继续 **BLOCKED**。
- **第四层（补 KD-67 数据层后，`4f82da12`）**：`core:data` 已编译通过，失败推进到 `:app`
  `SmartMistakeBookRoot.kt`：`onClarificationNewQuestion`/`onClarificationCancel`/
  `onRequestCalibrationQuiz` 参数不存在、`KnowledgeCalibrationDecision` 未解析。对应签名在
  `feature/review/KnowledgeReviewSessionScreen.kt`/`ViewModel.kt` 与未跟踪的
  `KnowledgeCalibrationDecision.kt`。
- **边界（主会话停手）**：`feature/review/*` 与 `app/.../SmartMistakeBookRoot.kt` 在开工快照里
  就是脏的，属 UI/JS 会话在飞热区；按协作红线不整文件抢占、不替其提交。**干净树 release
  构建的剩余阻断在 app/UI 层，须 UI 会话把 KD-67 确认卡/校准题 UI 半边提交后再重跑。**
- 主会话本轮已完成：`core:export`/`core:database`/`core:data` 三层的自洽性修复（4 笔），
  并实证 `core:data` 可编译；未触碰 UI 热区、未改任何门值。
  解除条件不是再补一两个文件，而是把 5b 容量预览与 B35 召回改形两组**跨线未提交特性**连同其调用点
  一起提交（或回退已提交的调用点）——属其他线 owner 的决定，主会话未擅自继续提交。
- 本节使用的签名钥是**一次性本地测试钥**，证书身份与批次 51 脏树产物（`2c8415b2…`）不同；
  两包不可互比，也不构成生产签名证据。
- 主会话本轮未改任何门值、未重跑四门、未改终裁：**NOT ACCEPTED 维持；发版不成立。**
