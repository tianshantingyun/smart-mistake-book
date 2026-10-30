# KB v23 内容更新：68 条 REOPEN（41 flip + 27 no-op）+ 35 建点 + R6 29 条换行 · 预注册（2026-10-30）

> **性质**：出数前冻结。§1–§6 写死，执行轮只允许在 §7 追加结果；不得回改 §1–§6。
> **写作时点**：2026-10-30；HEAD `4f11cd5432e6c259038fc92bd2e975aee8d304b4`（`git rev-parse HEAD` 实跑）。
> **权威执行清单**：`build/agent-batch26/exec-plan.md`（63,538 B / 647 行；sha256 `9e45d571b66d14fdf76614fa22b9fda445c6eebb5fbbd316b0067896fdf28b4b`；下称 exec-plan）。
> **本文件是本次唯一新增写盘**。未改源码、未写包、未跑 Gradle/设备/判官。

---

## 0. 写前守卫与写作口径

### 0.1 porcelain 守卫（写前实跑，2026-10-30）

```bash
git status --porcelain -- docs/research/2026-10-30-kb-v23-content-update-prereg.md   # 空（新文件）
git status --porcelain -- core/data/src/main/resources/knowledge \
  tools/kb_build/tables tools/kb_coverage/tables/material_judgments \
  knowledge-production/judgment-verdicts/reopen-2026-10-17.jsonl.csv                 # 空
git status --porcelain | wc -l                                                       # 707
git rev-parse --short HEAD                                                          # 4f11cd54
```

- **目标文件不存在 → 本次是新建，只写一次**；后续结果按“只追加”纪律写 §7。
- **核心写面（release 包家族 / 别名表 / 五片判定表 / 终裁快照）全 CLEAN**。
- 写作前整树 **707 条脏项** = `221 M + 486 ??`（`git status --porcelain | awk '{print $1}' | sort | uniq -c` 实跑），属他线 UI/文档轨；本批不触碰、不裹挟、不回滚。写入本文件后为 **708**（本文件是唯一新增 `??`）。逐条归属见 `build/agent-batch26/baseline/identity.md` §3。

### 0.2 数字来源

本文件所有计数均由本会话直接对当前文件实算，不引用旧报告的口径；命令与结果见各节。**未被本会话实算的数字**只允许两种形态：① 明确标为“预期（算术）”；② 明确标 `UNVERIFIED`。

---

## 1. 更新内容（冻结）

### 1.1 “68 条”的口径

**68 = `build/agent-batch25/adjudication/114-402-final.csv` 中 `track=114` 且 `final_decision=REOPEN_NOW` 的数据行数。**

本会话实算（`csv.DictReader`，`utf-8-sig`）：

```text
total 516, track=114 114
Counter({('REJECT','NOT_APPLIED'):42, ('BUILD','REOPEN_NOW'):35,
         ('MERGE','REOPEN_NOW'):33, ('MERGE','NOT_APPLIED'):3,
         ('BUILD','NOT_APPLIED'):1})
REOPEN total 68
subject: MATH 33 / PHYSICS 18 / CHEMISTRY 11 / BIOLOGY 6
```

| 项 | 值 | 依据 |
|---|---:|---|
| `track=114` 总行 | 114 | 实算 |
| `REOPEN_NOW` | **68** | 实算；终裁摘要 `114-402-final-summary.md` §0 同值 |
| ├ `existing_decision=BUILD` | **35** | 实算 |
| └ `existing_decision=MERGE` | **33** | 实算 |
| `NOT_APPLIED` | 46 = REJECT 42 + BUILD 1 + MERGE 3 | 实算；不在本批写面 |

- 终裁 CSV 的 sha256 为 `1ce35ca1e5da715ccf6423f42e74805140ded10a461559d710f362c06c7040b1`（终裁摘要文首登记值；本次未重算该文件哈希，登记为转引）。
- `REOPEN_NOW` 只是“批准重开”标记；执行动作与逐条字段以 exec-plan §5 的 Table A/B/C 为准。

### 1.2 真实动作不是 68 次 flip

68 行在**运行时判定源**里的现状（本会话按 `chunk_id` 连接 `knowledge-production/judgment-verdicts/reopen-2026-10-17.jsonl.csv` 实算，68/68 命中）：

| 动作 | 条数 | 构成 | 含义 |
|---|---:|---|---|
| **FLIP_MATERIAL** | **41** | BUILD 35 + MERGE 6 | 判定行 `action=SKIP` → **`MATERIAL`**（不是 `NEW`；`materialize.py` 把 `NEW` 判非法，见 exec-plan §3.2） |
| **NOOP_APPLIED** | **27** | 全部 MERGE | 判定行已是 `action=MATERIAL`，且同 slug 材料**已在 release 包**；`materialize` 幂等跳过，不重写、不改绑 |
| **CREATE_POINT** | **35** | 全部 BUILD | 从 `tools/kb_build/tables/new_points_manual.csv` **过滤后**的 35 个 `(subject, slug)`；不是整表 |
| **REBIND** | **0** | — | 27 条 MATERIAL 的绑定已指向正确目标；`material_rebind.csv` 无这些 slug 的行（exec-plan §2.2） |

逐项实算交叉表：

```text
Counter({('BUILD','SKIP'):35, ('MERGE','MATERIAL'):27, ('MERGE','SKIP'):6})
action for 68: SKIP 41 / MATERIAL 27
midx: SKIP '' 41 / MATERIAL '' 25 / MATERIAL 'b' 2
```

> 这条与 `114-402-final-summary.md` §2 “判定源 action 均为 SKIP”的表述**不一致**；差异不是文件漂移（快照 sha 仍为 `57aa7614…`），而是终裁摘要只统计了 114 个 chunk 的提案侧，没有按运行时判定行核账。本预注册以**判定源实算**为准，并以 exec-plan §2.1 的同口径读数为交叉证据。

#### 1.2.1 6 条 MERGE flip（SKIP → MATERIAL）

| chunk_id | 目标节点 slug | 材料 slug（预期） |
|---|---|---|
| `52d6492d23-017` | `带电粒子在交变电磁场中的运动` | `ext-phy-52d6492d23-017` |
| `a0d0498ecf-060` | `半偏法测电表内阻` | `ext-phy-a0d0498ecf-060` |
| `0b7d959106-013` | `平口单峰函数` | `ext-mat-0b7d959106-013` |
| `85f4a70f9e-042` | `导数的几何意义` | `ext-mat-85f4a70f9e-042` |
| `526388500e-074` | `功` | `ext-phy-526388500e-074` |
| `a9a2f4c2c2-014` | `基因表达与性状的关系` | `ext-bio-a9a2f4c2c2-014` |

#### 1.2.2 27 条 NO-OP（含 2 条 `midx='b'`）

两条多行块**必须按 `midx='b'` 取行**，不得取首行：

| chunk_id | `midx='b'` 的语义 | 已落材料 | 目标节点 |
|---|---|---|---|
| `289deb9ecd-1134` | `等差中项` | `ext-mat-289deb9ecd-1134b` | `等差数列的有关概念` |
| `cb623abc4b-005` | `神经-体液-免疫调节网络的比较` | `ext-bio-cb623abc4b-005b` | `生命活动调节方式的三种模型` |

其余 25 条 MATERIAL 的逐条材料 slug/标题见 exec-plan §5 Table B；本次只冻结“27 条全部已落包、0 条改绑”这一事实。

#### 1.2.3 CREATE_POINT = 35 的“过滤”含义

- `new_points_manual.csv` 当前 **1,940 数据行**；其中 **419 行**的 `(subject, slug)` 不在 release 包（本会话实算，与 exec-plan §4.2 同值）。**直接跑 `create_points --write` 会建 419 个点，越界。**
- 本批只允许 35 个 `(subject, slug)`，来源为 exec-plan Table A。
- 35 个里 **34 行已在表中**（`source_locator=批次13 重开提案 2026-10-17`），**缺 1 行 `抽屉原理`**；执行轮先追加该行（7 列、CRLF、boundary 以 `定位：` 开头），再按 35 个键过滤建点。
- 本会话独立核对：按 `slug` 命中 31/35，按 `slug` 或 `name=` 字段命中 **34/35**，唯一未命中即 `抽屉原理`；与 exec-plan §4.2 一致。
- 35 个 slug 在现包 **0 命中**；建点预期 `selected 35 / created 35 / errors []`（算术预期，非读数）。

### 1.3 R6：29 条换行形态修复

- 清单：`build/agent-batch25/r6-items.json`，24,275 B，sha256 `cbfdfb48d30b5003425e1b0be410c8d63271991d216d9255b9bce6cd13898b2f`（本会话实算）。
- 计数（实算）：`summaryNewline 13` + `nodeBoundaryNewline(within80) 6` + `materialBoundaryNewline 10` = **29**；扫描总数 `nodeBoundaryNewlineTotal 14`，写面只取 within80 的 6 条。
- 修复规则沿用 v22 预注册 §1.1（该节 §1–§6 冻结、未执行完成即回滚，规则仍有效）：
  1. `\ne` 转义（summary 13 + material 9）：把“真实换行 + `e`”恢复为字面 `\` + `n`，不增删其余字符；
  2. 结构换行（node 6 + material 1）：真实换行替换为**空格或分号**，**不得直接删除换行拼接**；
  3. 其余文本、标题、绑定、别名零改（S3 不在本批，见 §6.2）。
- 证据与逐条 slug：`docs/research/2026-10-29-kb-v22-content-update-prereg.md` §1.1（冻结清单）；本批不重新定义清单。

### 1.4 薄料轨（blocked，本轮零写）

| 项 | 登记 |
|---|---|
| 薄料行 | 行 23「**必备实验仪器的原理及使用**」；材料 `phy-basic-instruments-principle-use`（`moe-2025-teaching-support-v2-22.json`，`role=PRIMARY`；本会话实读绑定为 `kb:moe-2025-four-subjects-v1:physics:atomic:必备实验仪器的原理及使用`） |
| 本轮动作 | **blocked，零写**：不改材料、不加绑定、不写 sidecar、不动 `material_bindings.csv` |
| 判红点（已定位） | `tools/kb_build/build.py:585-587`：`if len(bindings) > 1: errors.append("…多于 1 条绑定")`；任何 SUPPORTING 第二绑定都会在 build 校验期被判红 |
| 载入器限制 | `tools/kb_build/tables.py:163-173`：`load_material_bindings()` 返回 `dict[str, str]`，同材料出现第二条即 `ValueError("合同只允许恰好 1 条 PRIMARY")` |
| 写入器限制 | `tools/kb_build/build.py:239-260`：`_apply_material_bindings()` 只写单元素 `PRIMARY`，会整体覆盖 `material["bindings"]` |
| promote 侧实测 | `tools/kb_build/promote.py:47-69` 的 `run_checks` 只跑 gates + consistency + roundtrip；`gate.py` 只检查“零绑定知识点/零绑定材料”（`gate.py:547`、`:558`），`check_pack_contract.py` 的 F9 只对 `material_bindings.csv` 的“节点缺/空/欠账”三桶与基线比数（`:420-528`），**都没有“恰一 PRIMARY / 上限 16 / SUPPORTING 合法”的二次绑定校验**。因此当前 promote 不会替 build 兜住多绑定 |
| 最小改动方案（仅登记，未实施） | ① `tables.py:163-173` 改为“每材料 1..16 条 `(point_slug, role)`”或新增追加表；② `build.py:239-260` 保留既有绑定、追加 `SUPPORTING`，不再整体覆盖；③ `build.py:585-587` 把“>1 即红”改为“>16 或 PRIMARY≠1 才红”；④ 在 `run_checks` 增加同一契约门，补 promote 侧缺口 |
| 结论 | 工具链未具备“产出非 PRIMARY 绑定”的入口；本轮维持维护项。材料轨收益 = 口径完成度，不是检索分数 |

### 1.5 执行清单与写面边界

- **唯一逐条清单 = exec-plan**。其 §5 Table A（35 BUILD 四字段 + 正文来源）、Table B（33 MERGE 目标/现状/材料 slug）、Table C（6 条 flip 的 type/title/boundary 建议）为本批执行面。
- 本预注册只冻结**数量、口径、判据、回滚面**；逐条文本质量由执行轮按 `check_exam_leak.py` 与 `gate.py:340-390` 双重把关。
- 不含 S3 别名（判负关闭）；不含 402 同键（全部 KEEP_ALL，零动作）；不含 46 条 NOT_APPLIED。

---

## 2. 判读口径

### 2.1 主判读：内容更新 = 非劣（四条，写死）

| # | 判据 | 求值口径 |
|---|---|---|
| 1 | **主集非劣** | 设备融合 Recall@5 **≥ 117/129**；低于即不采纳 |
| 2 | **24 章逐章不劣** | 24 章逐章 Recall@5 与 A1 比较；任一章**严格更低**即不采纳；equal/improved 均通过 |
| 3 | **零漂移对称差 0** | 同路 device↔JVM 的 fused 与 lexical 两条 MISS query 集合 symmetric difference 必须 **0**；非 0 = 装置缺陷，本臂**不判读**（不是 FAIL，也不是采纳/回滚结论） |
| 4 | **负向守卫** | 按章 cluster 配对 bootstrap `--reps 20000 --seed 0`；**A = v23、B = A1**，配对差 = A − B；要求 95% CI **上界 ≥ 0**（只排除“显著更差”，不要求显著更高） |

- 判据原文与 A1 采纳时的逐条求值：`docs/kb-acceptance-2026-10-17.md:694`；A1 三规则成立记录同文件 `:574`。
- 预算：沿用 `p50Budget=150ms` / `p95Budget=250ms`；`overBudgetSamples>0` 只作装置健康观测，不单独作为采纳条件。
- 零漂移 `NOT_COMPUTABLE`（JVM 账本缺失/镜像红）按“不判读”处理；显著性产物缺失时判据 4 记 `NOT_RUN`，不得写“无显著差异”。
- JVM 只作零漂移/装置保真，不单独判分；设备读数为 emulator，不是真机。

### 2.2 为什么是“非劣”，不是“严格更高”

本批是**内容补齐/结构补缺口**（建 35 点、补 41 条材料、修 29 条文本形态），不是新检索机制。严格更高会把裁定交给单题翻转，等价于面向结果调参。非劣四条覆盖内容更新真正要防的失败：索引截断、别名互斥、跨章回退、装置漂移；同时不把“没有涨分”误判为失败。

**不构成先例**：若后续把本批当机制臂重测，仍须另立预注册并走“严格更高”三规则。

### 2.3 三个边界单点（只报不判）

金标 sha 未变（§4.4），故沿用 v22 预注册 §2.4 冻结的三个单点；执行轮 §7 必须报四路 hit/名次（或 `absent`），但**不得**以单点翻转作为采纳/回滚依据：

| 单点 | 学科 / 章节 | 金标 query | A1 四路状态（冻结引用） |
|---|---|---|---|
| `等和线` | MATH / 数学必修第二册·第六章·平面向量 | 在△ABC中 AP=xAB+yAC，x+y=1 时 P 在直线 BC 上，那 x+y=1/2 时 P 跑到哪儿去了？ | device fused / device lexical / JVM fused / JVM lexical 均 `rank=absent` |
| `活结与死结模型` | PHYSICS / 物理必修第一册·第三章·相互作用 | 一幅画用绳子穿过可在横杆上滑动的小环吊着，两边拉力相等吗？若绳直接打结固定在墙上呢？ | 四路均 `rank=absent` |
| `力的分解` | PHYSICS / 物理必修第一册·第三章·相互作用 | 一盏灯被两根绳子吊着，每根绳的拉力都比灯的重力大，为什么？重力该怎么分？ | 四路均 `rank=absent` |

- 三个单点的 A1 四路状态是 v22 预注册的冻结引用，本会话**未重跑**；执行轮必须用本批新包重出，不能把旧读数当本批结果。
- 不得因单点翻转改别名/阈值/参数；确需调整另立预注册。

### 2.4 报告义务

§7 至少报告：设备融合/词面主集与 MRR、24 章逐章表（lower/equal/higher 计数）、零漂移两路 symdiff、bootstrap 的 delta/CI/p、三个单点四路状态、包/材料/别名/dense 的实测计数与 sha。任何一项未跑，写 `NOT_RUN` / `UNVERIFIED`，不得留空冒充通过。

---

## 3. 装置门（双门 = 0）

| # | 命令 | 通过条件 |
|---|---|---|
| 1 | `./gradlew :core:data:compileDebugUnitTestKotlin --console=plain` | **exit 0** |
| 2 | `./gradlew :core:data:assembleDebugAndroidTest --console=plain` | **exit 0** |

- 本门是**单测编译门 + androidTest 装配门**，与 v21/v22 预注册 §3 同口径；若执行轮另跑 `testDebugUnitTest`，结果另记，不替代本门。
- **红则不写包、不跑设备、不判读**：任一门红时停在 §1.5 的第一次写盘之前；不得先写 staging、不得先跑 dense、不得用旧账本代替本批判读。
- 两门日志落盘到 `build/agent-batch26/`，§7 引用；不得只写“已跑”。
- 他线在飞文件导致门红：等待或另裁，不得回滚他线文件。
- **本预注册写作时点未跑 Gradle**：本节是门定义，不是通过声明。

---

## 4. 冻结输入（本会话实算，2026-10-30）

### 4.1 包与版本

| 项 | 值 |
|---|---|
| 包身份 | `moe-2025-four-subjects-v1`；manifest **version=21**、`contentVersion=9f9e99ceec47cf8c`；manifest sha256 `c4d3875049dab731bae7dfdeb960a3b35473cf706e48d71a1c8b7ff8deced3e0` |
| 主包 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` sha256 `a719248448f314d1a6e6bfc74e3a101f07960d08d6985ade3d1c97f3efe6da36`；4,510,607 B；**3,245 节点 / 64,973 材料 / 25 sidecar**（本会话实算） |
| 25 sidecar | 聚合 sha256 `ed1c6d4f6d9d331a0982408c2f60a660a8490cbad5c2a74d438604d12057355e`；逐文件 pre-sha 见 §5.5 |
| 最近一卷 | `moe-2025-teaching-support-v2-25.json` sha256 `a15df33956713db1f65335b006f21eec34f424ae1a9cb9403b3d7900e16b05b9`；111 条材料（实算） |
| sidecar 索引 | `moe-2025-teaching-support-v2-index.json` sha256 `2da0b9c5702c708723e4129fba3eda6e34875271ea07422f2aa9fb9981145b92` |
| 版本目标 | **v21 → v23**；**v22 跳过**。v22 已于批次25 `promote` 落盘（contentVersion `efda0a36…`、主包 `1db011e1…`）后按预注册回滚（`docs/kb-batch25-2026-10-29.md:399`），其版本号已进入历史，不得复用 |
| 版本推进机制 | `promote.py:110-112`：`new_version = max(staging.version, release.version) + 1`。seed 后 staging/release 均为 v21，默认只会得 **22**。执行轮必须在 seed 后把 **staging manifest 的 `version` 显式置为 22**（并同步重算 staging `contentVersion`），promote 才会落 **23**。**只动 gitignored staging；release manifest 仍由 promote 唯一写。** 未执行此跳过而产出 v22 = 与本预注册冲突，停止并另裁 |
| 冲突登记 | exec-plan §0/§8.2 的版本预期是 **v22**；本预注册按本次 ask 冻结为 **v23**（跳过历史已用的 v22）。执行轮以本节为准，不得两边各取一半 |
| 新 contentVersion | 由 `promote` 落盘时重算；**写前不可知**，§7 登记 |

### 4.2 判定源与清单

| 项 | 值 |
|---|---|
| 终裁 CSV | `build/agent-batch25/adjudication/114-402-final.csv`；**516 数据行 / 517 物理行（含表头）**；sha256 `1ce35ca1…c7040b1`（终裁摘要登记，转引） |
| 判定快照 | `knowledge-production/judgment-verdicts/reopen-2026-10-17.jsonl.csv`；**31,272 数据行 / 31,273 物理行**；sha256 `57aa76144f5a2eed1e72b87e71d8fd6d4e24586675541652df8c744013d3cb3f`（本会话未重算，基线 §3 同值；**推荐零改**，见 §5.2） |
| 运行时判定源 | `tools/kb_coverage/tables/material_judgments/{MATH,PHYSICS,CHEMISTRY,BIOLOGY,UNASSIGNED}.csv`；总 135,085 行；逐文件 pre-sha 见 §5.1 |
| 执行清单 | `build/agent-batch26/exec-plan.md` sha256 `9e45d571b66d14fdf76614fa22b9fda445c6eebb5fbbd316b0067896fdf28b4b` |
| R6 清单 | `build/agent-batch25/r6-items.json` sha256 `cbfdfb48d30b5003425e1b0be410c8d63271991d216d9255b9bce6cd13898b2f` |
| 建点表 | `tools/kb_build/tables/new_points_manual.csv`；1,940 数据行；sha256 `2c08b4d29cfd046bcd94167c83984a88b44e09e43435f69bb1683a6151e72f55` |

### 4.3 别名账本

| 项 | 值 |
|---|---|
| `alias_exceptions.csv` | 35 数据行；工作树 sha256 `7a8e13edbf5d1e83dddd5f563eede33b710d10c180ae749fcbe0e8dec1a2d01e`（CRLF；归一化 LF 后 `1cba77fe…`，与 v22 预注册登记值一致） |
| `alias_map.csv` | 37,553 数据行；工作树 sha256 `c253eafa9e794b98a5711b8db90f5f9ecf6ba0dd6ff295ec31810b3f1577041d`（CRLF；归一化 LF 后 `f8398c61…` = HEAD blob） |
| `alias_dropped.csv` | 22,559 数据行；工作树 sha256 `84040e23256bfae0e31531d4e8f04cb3d022b31654bf6bedae0cce2b297eafdf`（CRLF；归一化后 `12e58073…`） |
| 别名总量 | **40,798** = 3,245 canonical + 37,553 额外别名（与 `.vec.json` `aliasVectors=40798` 一致） |
| 本批预期 | 默认标题口径 **+6**（6 条 MERGE flip 各贡献 1；35 条 BUILD 材料标题=节点名，按冗余丢弃）。范围 +0…+41，以 `rebuild_aliases` dry 实读为准；**不是承诺** |

### 4.4 dense、金标、A1、判官

| 项 | 值 |
|---|---|
| `.vec` | sha256 `79a22270b54180f6ba5346a4f56d87eb16e5f335107ac9877cbb142fd4515446`；26,041,347 B；44,043 vectors = 3,245 canonical + 40,798 alias-list（本会话实算） |
| `.vec.json` | sha256 `82c5906a55dc7b971f67f45509f8593c275addc9dfc07191bb6b6426f1bec17a`；`sha256=79a22270…`、`bytes=26041347`、`packSha256=a7192484…`、`format.count=44043`、`aliasVectors=40798`、`canonicalVectors=3245`（本会话实读） |
| 重打目标 | `.vec` 与新旁车 sha **重打后实测登记**，§4 不预设；`packSha256` 必须等于 v23 主包实测 sha |
| 钉值 | `DenseRecallAssembly.kt:108` `VECTOR_ASSET_SHA256 = "79a22270…"`；文件 sha256 `a32fa8e7b702ec52cdb8e5e37dea5b257698a3534bb27c6f65141b6b976f4ba2` |
| 金标 | `tools/kb_coverage/tables/golden_queries_v2.json` sha256 **`eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102`**；`core/data/src/androidTest/assets/golden/golden_queries_v2.json` 同 sha（本会话实算）；129 题 / 24 章 |
| A1 账本（设备融合） | `build/agent-batch19/a1/device-metrics.txt` sha256 `a332e2aa6051e49f221879fe6cdbfad263a7550f94b3f6d853ccd59895059c0b`；117/129、MRR 0.7322997416020672、p50=38 / p95=51 / max=84ms（登记值，本会话未重跑） |
| A1 账本（设备词面） | 同文件 `:32-35`：102/129、MRR 0.6206718346253232 |
| A1 收束 | `build/agent-batch19/a1-final.md` sha256 `ab2f7e75501692f0dc4f15721e6be50a91b2d17d04c3159e643ed02e4ff8b4a1` |
| 判官两件 | 设备 `GoldenRetrievalInstrumentedTest.kt` sha256 `c2280255a9b0db2bc77ac1213e977be755a431efe80cfdf5b3c0c6e7bb7a4786`；JVM `GoldenRetrievalJvmTest.kt` 工作树 sha256 `62c232fcbf3ffd11fcdedaaa7a4340981bbc22b8d6a2e0ad39614ef78f5ccd95`（本会话实算；JVM 为 `M`，属他线路径注，照读不改） |
| 零漂移镜像（非判官） | `RetrievalBenchmark.kt` sha256 `8c2a2ef05b1c96bb31bbef206e1d432ec615c2ab36f7c06c86076ec3d824e5be` |

### 4.5 R1 取数语义（冻结，零改）

| 项 | 值 |
|---|---|
| DAO | `KnowledgeTeachingMaterialDao.readBestTeachingMaterialPerNode`；文件 sha256 `0a29d8634d76eaa46d7f74c82d48565286b0013bf392a706f9af9c7021e5a047` |
| 调用点 | `RoomKnowledgeBaseStore.kt:393`；文件 sha256 `7dc150c8eac95acc8c0bf6b5a15c456d75ee4b52fd4975b152fc3d7781a5af2a` |
| 语义 | 每节点恰好一条；角色序 `PRIMARY → SUPPORTING → 其他`；本批**不改 SQL、不改函数、不改调用点** |

---

## 5. 回滚

### 5.1 触碰清单与 pre-sha（全值；回滚目标 = 当前工作树字节）

> **EOL 说明**：CSV 与部分文本夹具在工作树为 CRLF；上表/下表给的是**工作树字节** sha。跨 EOL 归一化复算会得到不同值，回滚核验一律以工作树字节为准。`git checkout --` 在 `core.autocrlf` 下恢复同一工作树字节。

**A. 判定源（必触碰：41 行 flip）**

| # | 路径 | 预期动作 | pre-sha256 |
|---|---|---|---|
| 1 | `tools/kb_coverage/tables/material_judgments/MATH.csv` | 24 行 `SKIP→MATERIAL` | `027ee7e894026748e9cdecfe639f34c51a5f1a7ac524b25aa00af7f93b3b0827` |
| 2 | `tools/kb_coverage/tables/material_judgments/PHYSICS.csv` | 5 行 | `bc574d223e32182f554415de64f8dab3247b2f06dbca67c4d9344f0b3d5eca11` |
| 3 | `tools/kb_coverage/tables/material_judgments/CHEMISTRY.csv` | 8 行 | `7f14c7e8aaea21088cac9af94884fc2c1b380f2fdfbc7a3a06d90e1cc6f5be07` |
| 4 | `tools/kb_coverage/tables/material_judgments/BIOLOGY.csv` | 4 行 | `4448d5fa9f6f8cec4183c2699796cccc66eb391045bb77ceb92be77ba8d44261` |
| 5 | `tools/kb_coverage/tables/material_judgments/UNASSIGNED.csv` | 0 行（零改） | `28ed802b9a11ffefb46c881ea9b47814246d1bef3273d75e7fbaf69935322def` |

> 分布 24/5/8/4 = 41，与 exec-plan §6.1 一致（本会话按 6 条 MERGE flip + 35 BUILD 的学科归属复算）。`material_judgments` 五片当前 CLEAN；`judgments.write_all` 固定 12 列/CRLF，未改行应逐字节不变。

**B. 包家族（promote 触碰）**

| # | 路径 | 预期动作 | pre-sha256 |
|---|---|---|---|
| 6 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | 35 点 + 别名回写 + R6 node boundary 6，promote v23 | `a719248448f314d1a6e6bfc74e3a101f07960d08d6985ade3d1c97f3efe6da36` |
| 7 | `core/data/src/main/resources/knowledge/moe-2025-update-manifest.json` | version 23 + 新 contentVersion | `c4d3875049dab731bae7dfdeb960a3b35473cf706e48d71a1c8b7ff8deced3e0` |
| 8 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-index.json` | promote 重写（预期字节不变） | `2da0b9c5702c708723e4129fba3eda6e34875271ea07422f2aa9fb9981145b92` |
| 9 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-01…25.json`（25 个） | R6 summary 13 + material boundary 10；promote 重写；41 条新材料预计全进 v2-25 | 聚合 `ed1c6d4f6d9d331a0982408c2f60a660a8490cbad5c2a74d438604d12057355e`；逐文件见 §5.5；v2-25 单件 `a15df33956713db1f65335b006f21eec34f424ae1a9cb9403b3d7900e16b05b9` |

**C. 别名与建点表**

| # | 路径 | 预期动作 | pre-sha256 |
|---|---|---|---|
| 10 | `tools/kb_build/tables/alias_map.csv` | 重建（37,553 → 预期 37,559，默认 +6） | `c253eafa9e794b98a5711b8db90f5f9ecf6ba0dd6ff295ec31810b3f1577041d` |
| 11 | `tools/kb_build/tables/alias_dropped.csv` | 重建（预期行数不变 22,559） | `84040e23256bfae0e31531d4e8f04cb3d022b31654bf6bedae0cce2b297eafdf` |
| 12 | `tools/kb_build/tables/alias_exceptions.csv` | 本批 **零改**（S3 不写；只读输入）；若字节变化即缺陷并回滚 | `7a8e13edbf5d1e83dddd5f563eede33b710d10c180ae749fcbe0e8dec1a2d01e` |
| 13 | `tools/kb_build/tables/new_points_manual.csv` | +1 行 `抽屉原理`（1,940→1,941） | `2c08b4d29cfd046bcd94167c83984a88b44e09e43435f69bb1683a6151e72f55` |

**D. dense 资产与模型清单**

| # | 路径 | 预期动作 | pre-sha256 |
|---|---|---|---|
| 14 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec` | 重打（新 sha 实测登记） | `79a22270b54180f6ba5346a4f56d87eb16e5f335107ac9877cbb142fd4515446` |
| 15 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec.json` | 重打旁车 | `82c5906a55dc7b971f67f45509f8593c275addc9dfc07191bb6b6426f1bec17a` |
| 16 | `tools/dense_build/model-manifest.json` | 仅当重打更新指纹/计数时 | `907d82b2b790c3c7fd8b6b1bc19ffb4a46262ad235d84905a04e9b0487266fae` |
| 17 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-v1.5-vocab.txt` | 零改（登记 pre-sha 供核验） | `45bbac6b341c319adc98a532532882e91a9cefc0329aa57bac9ae761c27b291c` |

**E. 10 个 dense 夹具（重出；逐字回滚面）**

| # | 路径 | pre-sha256 |
|---|---|---|
| 18 | `core/data/src/androidTest/assets/dense/encoder-parity-cases.tsv` | `c101c39c33d237a5b463e047d481443c33154883530befd006666a3a3545b584` |
| 19 | `core/data/src/androidTest/assets/dense/encoder-parity-vectors.f32` | `b0550446214616284a6578d84517a49be911abd14f54bb14c5ffe9e7fa78458e` |
| 20 | `core/data/src/androidTest/assets/dense/encoder-parity.json` | `62421cd54d6d7fd4b6214666698da11902d07cbb7b131ee4a1cbc557537cb64a` |
| 21 | `core/data/src/test/resources/dense/dense-device-order-reference.txt` | `d431ea24b7be1831c1de4cad21a53af19b0e9fe3740a475732e09ecef56df058` |
| 22 | `core/data/src/test/resources/dense/dense-fusion-reference.txt` | `c2ab7b1c321ebcd9c4886f6fde019633f2827d07d12f0901a9942431ae29ec5f` |
| 23 | `core/data/src/test/resources/dense/dense-scan-reference.txt` | `f9fbdcb3b1bc74d3c6f2238d6b20f32cc01fffcfc7a716c7a7038b33b8071969` |
| 24 | `core/data/src/test/resources/dense/golden-query-vectors.i8` | `6411754368c3a769136704320f3e434e300cbdf18a8308f29925a71ff12284c3` |
| 25 | `core/data/src/test/resources/dense/tokenizer-parity-cases.txt` | `e6cb53a367637376ec3809da8ae8e7afdf6839c34c12fa56738ac48fa1ed9f0b` |
| 26 | `core/data/src/test/resources/dense/tokenizer-parity-stages.txt` | `8a136cfb4813c87450668f71f1b167b53b3660b7898c14dbe30cdd4e91d01875` |
| 27 | `core/data/src/test/resources/dense/tokenizer-parity-summary.json` | `366d575a93375d745778bb51b2dad2b54cc2f4237c8fb4f7fef4e67cb83ed704` |

**F. 钉值（只按实测重钉，不放宽容差）**

| # | 路径 | 当前钉值 | pre-sha256 |
|---|---|---|---|
| 28 | `core/data/src/main/kotlin/.../knowledge/dense/DenseRecallAssembly.kt:108` | `VECTOR_ASSET_SHA256 = "79a22270…"` | `a32fa8e7b702ec52cdb8e5e37dea5b257698a3534bb27c6f65141b6b976f4ba2` |
| 29 | `core/data/src/test/kotlin/.../knowledge/dense/DenseRecallRerankerTest.kt` | 候选总数 `1074` | `807d3a506c2fb3c77a8b49860c3a85e0e74c600d925af042c4b68f73de0f5b5e` |
| 30 | `core/data/src/test/kotlin/.../knowledge/ProductionLexicalLegExportTest.kt` | `V1_D1_MAIN=0.7906976744186046`、`V1_D1_MRR=0.6206718346253232`、hits=102、`FROZEN_GOLDEN_SHA256=eabef102…` | `947965b0987b0eb1200a51f932fb9d78716da8128674dee240eae02f38db50f0` |
| 31 | `core/data/src/test/kotlin/.../knowledge/Stage1LexicalLabTest.kt` | 词面锚点（仅计数变化时重钉） | `3b23a68ff26cff959bcb4e8c59279087bcc1e61373845b2d73a3a99b81576692` |
| 32 | `core/data/src/test/kotlin/.../knowledge/Stage2LexicalScoresExportTest.kt` | 词面锚点（仅计数变化时重钉） | `79e48cde4389edad28f4bf18d7c72d7a131e869f0d22fb59cd3728dfa5bad55b` |

> 第 28–32 项的“当前钉值”由本会话实读源码/常量确认；执行轮只允许按重打后的实测值重钉，**不得**为过门而放宽容差或改断言语义。

### 5.2 零触碰（写死）

- **判官两件**（设备 `c2280255…`、JVM `62c232fc…`）与**零漂移镜像** `8c2a2ef0…`：零改。
- **金标** `eabef102…`（129/24）：零改；两个副本 sha 必须相同。
- **判定快照** `reopen-2026-10-17.jsonl.csv`（`57aa7614…`）：**推荐零改**（保住终裁哈希链）。若主会话明确要求镜像 41 行，必须**行级替换**并登记新 sha；不得整表重写制造 31k 行 diff。
- **S3 候选/别名例外**：本批零追加；`alias_exceptions.csv` 只读不写，**若字节变化即缺陷并回滚**。
- **46 条 NOT_APPLIED**、**402 同键**、**薄料材料/绑定**：零改。
- **R1 取数语义**（DAO/调用点）：零改。
- 写作前他线 **707 条脏项**（本文件之外）与 `build/` 下非本批产物：零触碰、不回滚。

### 5.3 写前备份与核验

1. 写前把 §5.1 A–F 的 **tracked** 文件按工作树字节备份到 `build/agent-batch26/backup/`，并落 `sha256sum` 全表；gitignored 的 staging 另行快照。
2. 写前重跑 `git status --porcelain -- <§5.1 路径>`；执行轮若发现新增脏项，先登记归属，不得把“他线脏”当本批改动。
3. 本预注册写作时点实测：§5.1 全部路径 **CLEAN**（`tools/dense_build/gen_device_parity_fixture.py` 的既存 `M` 不在本批触碰面，登记为他线）。

### 5.4 回滚 / 采纳程序

**回滚（任一判据失败或双门红）**

1. 按 §5.1 逐字恢复 tracked 文件到 pre-sha（CLEAN 用 `git checkout --`；如执行轮产生新的脏文件，用备份覆盖）；staging 用写前快照覆盖。
2. 核验：manifest `version=21` / `contentVersion=9f9e99ceec47cf8c`；主包 `a7192484…`；25 sidecar 聚合 `ed1c6d4f…`；`alias_exceptions` 35 行 / `7a8e13ed…`；`alias_map` 37,553 行 / `c253eafa…`；`alias_dropped` 22,559 行 / `84040e23…`；`.vec` `79a22270…`（44,043）；`.vec.json` `82c5906a…`；`DenseRecallAssembly.VECTOR_ASSET_SHA256=79a22270…`；金标/判官 sha 不变；五片判定表恢复各自 pre-sha。
3. 核验 `new_points_manual.csv` 回到 1,940 行 / `2c08b4d2…`；`readBestTeachingMaterialPerNode` 与调用点未改；材料 64,973、未绑 0、全 PRIMARY、多绑定 0。
4. 复跑 §3 双门；回滚后仍红则登记为他线/基线问题，不归因本批。
5. §7 写明回滚原因与逐文件 sha 核验结果；不得把“已回滚”写成“未采纳但保留改动”。

**采纳**

1. 保留 v23 包、新 `.vec`、新别名账本与重出的 dense 夹具；按实测重钉第 28–32 项。
2. 在代码/账本注释写明新旧值与量源（日志路径/执行轮记录），保留 §4 的 pre-sha 表作审计链。
3. §7 登记 stateAfter（manifest version/contentVersion、主包/侧车/索引/资产/别名/夹具 sha、节点/材料/别名/dense 计数）。
4. 本批的非劣口径不得替代机制臂的严格更高；后续复用须另立预注册。

### 5.5 25 sidecar pre-sha（逐文件，本会话实算）

> 聚合 sha 的构造：按 `v2-01…v2-25` 顺序以 `\n` 连接 `"<basename> <sha256>"`（行间换行、末尾无换行）后再 sha256；当前聚合 = `ed1c6d4f…`。构造不同会得到不同聚合值。

| # | 文件 | sha256 |
|---|---|---|
| 01 | `moe-2025-teaching-support-v2-01.json` | `6719bcc65c9fa025747e9d23052c66714db837716dde9ba1de9f910fc52404e4` |
| 02 | `moe-2025-teaching-support-v2-02.json` | `337d6ecef966654ddff2b90e916fa03677efc3dcfbe976aee41a8131a2a1310b` |
| 03 | `moe-2025-teaching-support-v2-03.json` | `23e08d3a63c8ab4a17216cb1ea3e856a01c33a95f15b05eb02ecfa88d540a7ad` |
| 04 | `moe-2025-teaching-support-v2-04.json` | `fa7378a77683f684e62075e630315d74c46ff06dda77151e6408ee92432adb71` |
| 05 | `moe-2025-teaching-support-v2-05.json` | `5d2fc2623a5efc0f8849fc99d3cea967c813263e9ee879a54f6dbf776fe96fb8` |
| 06 | `moe-2025-teaching-support-v2-06.json` | `83169541da5228889bd418093adf2e078a86acd77c543e359dbb00cf4220860e` |
| 07 | `moe-2025-teaching-support-v2-07.json` | `b028f97be1a581008636160a63ba1f4fd0040ccec2befae65c19f3a7261176ae` |
| 08 | `moe-2025-teaching-support-v2-08.json` | `0a18acc8ed221db21af277fc6b70b1a902b47d3284254e78a812c490a0d776fe` |
| 09 | `moe-2025-teaching-support-v2-09.json` | `6d430b34b379aaab201a2038a89d9006807dc033582b347019a7cb496c124cf1` |
| 10 | `moe-2025-teaching-support-v2-10.json` | `dbe1bef32e875b2366880afae7ef6ce96e06dafca95f2f77a9576be82a19d748` |
| 11 | `moe-2025-teaching-support-v2-11.json` | `de218526103a840b595b03071e9d4196798c9e490f147b123b3beb9ae9c98da1` |
| 12 | `moe-2025-teaching-support-v2-12.json` | `12dc6a55f835232c42369149b9c921b5714eb18e981d0eb9f439d4c5d00ff95f` |
| 13 | `moe-2025-teaching-support-v2-13.json` | `ce38b4d4cf75cfde7a22230f16c37c967eec6397f68e0284ae405ed39ddc5353` |
| 14 | `moe-2025-teaching-support-v2-14.json` | `1d790d3cc8068d7ed5a36babcab6f1e8209d71706821e685cf0abdd59c895c4f` |
| 15 | `moe-2025-teaching-support-v2-15.json` | `e30a257f08ac94ac8721bda66f40d10ebac10ed0d5780106360c891d04ad43af` |
| 16 | `moe-2025-teaching-support-v2-16.json` | `7612aa625a6097b7b655eab4cae4329762b2ac6125498e96fdd211d0ae7aa86b` |
| 17 | `moe-2025-teaching-support-v2-17.json` | `98a76e355c167a5b9758c09cac8010adb2504fb835e0767cf09b25b71f5246c8` |
| 18 | `moe-2025-teaching-support-v2-18.json` | `8ae8b5183dd083f4a047b0abe96f1b72bfb73852c63eb3f00ddf84b0adfcc897` |
| 19 | `moe-2025-teaching-support-v2-19.json` | `3224da13ff1336eef6dc10bc85c71ae628e76f117b3de557733d2aacf98faff8` |
| 20 | `moe-2025-teaching-support-v2-20.json` | `c0064dead7ef985416051f383ca131f63a24dfb89776ef3dff01f95185c0e2f4` |
| 21 | `moe-2025-teaching-support-v2-21.json` | `dd9607445f2b699181e57b41ab055e456b77509a4f5f555d9f42966525e859f0` |
| 22 | `moe-2025-teaching-support-v2-22.json` | `88b393fa3a37f9430b7b3459798d3f78de28d53d2a3811fb785dfc72752caf37` |
| 23 | `moe-2025-teaching-support-v2-23.json` | `bfe6ddb100c89ab743f5d61a053dbe06247a8db0669e7cae6f8e497502f16703` |
| 24 | `moe-2025-teaching-support-v2-24.json` | `d5c685414c7110a7bf22161663217dab7d38bb37eeb017882d23dea844f2e064` |
| 25 | `moe-2025-teaching-support-v2-25.json` | `a15df33956713db1f65335b006f21eec34f424ae1a9cb9403b3d7900e16b05b9` |

---

## 6. 预期与边界（不承诺）

### 6.1 v22 教训（写死）

- **v22 的 S3 别名净负向已回滚**：离线 stage3 同口径实测 **词面 102→101、融合 117→114**、融合逐章最小 **0.5→0.0**；S3（262 条 / 174 节点）对金标集为负向，已判负关闭（`docs/kb-batch25-2026-10-29.md:399-400`）。本批**不含 S3**。
- 本轮 68 条 REOPEN 的性质是**补结构缺口 / 重绑**，不是加别名：判定来源逐行自带 `c1_material / c2_benefit / c3_executable` 证据（终裁 CSV 列 + 终裁摘要 §1），且 27 条 MATERIAL 已在包内、0 条改绑。**这降低了“纯文本噪声”风险，但不消除净负向可能**——新增 35 点会改变 canonical 向量与词面特征，新增 41 条材料可能挤压节点侧特征预算。
- **仍可能净负向**；三规则兜底：①主集 ≥117/129；②24 章逐章不劣；③负向守卫 95% CI 上界 ≥0。任一不成立即回滚（§5.4），不做“只留部分行”的事后挑选，不为金标调参。
- **不承诺任何具体分数**（不承诺 117→118、不承诺 MRR 提升、不承诺别名 +6 兑现）。所有 after 计数都是算术预期；§7 必须以落盘实测覆盖。

### 6.2 结构与内容边界

1. **结构预期（算术，不是读数）**：节点 3,245 → **3,280（+35）**；材料 64,973 → **65,014（+41）**；别名 40,798 → **40,804（默认 +6，范围 +0…+41）**；`new_points_manual.csv` 1,940 → **1,941**；判定表 MATERIAL 53,531 → **53,572**、SKIP 81,554 → **81,513**（以上均转引 exec-plan §8 的算术；本会话只实算了基线 3,245 节点 / 64,973 材料 / 1,940 行与 68 条动作拆分，**未重算** 53,531/81,554/135,085 与 after 计数）。
2. **正文质量**：41 条 flip 中 10 条池文本碎片化（非空行 ≤2 字占比 >50%），必须按 `note` 原句 + `heading` 重组；6 条 MERGE flip 中有 3 条池文本是题解/系谱题。`check_exam_leak.py` 与 `gate.py:340-390` 双重把关，命中不得静默豁免。
3. **建点范围**：只允许过滤后的 35 个 `(subject, slug)`；**禁止直接跑整表**（会建 419 点）。
4. **staging 漂移**：不 seed 就写会把 v22 回滚内容重新带回 release；seed 后必须复核主包 `a7192484…`、manifest v21，再执行 §4.1 的 version=22 跳过步。
5. **KB25-01（open）**：`promote --dry-run` 是否真的不写盘尚未锁定；本批把 dry 前后主包 sha 设为硬门（§5.3/§5.4），不把“dry 绿”当“写盘安全”。
6. **CI 基线已红**：HEAD `4f11cd54` 的 `android-check`（run `40956219818`）在 `instrumented` + `check` 均 failure，属他线编译问题；本批本地全绿也不能声称 CI 绿（`build/agent-batch26/baseline/identity.md` §6）。
7. **装置边界**：设备读数为 emulator，不是真机；JVM 只作零漂移/保真，不判分；离线 expectation 与设备 MRR 存在已登记 ULP 两源，本批不改口径。
8. **无第二评测集**：129 题 / 24 章是唯一冻结金标；不外推泛化，不新增/改写题目，金标 sha 必须保持 `eabef102…`。
9. **版本边界**：v23 只用于本批；promote 任何一环红则保持 v21，不产生半包/半升级。v22 不复用；若执行轮无法完成 version=22→23 的跳过，**停止并另裁**，不得静默落 v22。
10. **薄料零写**：行 23 维持维护项；材料轨收益 = 口径完成度，不是检索分数（§1.4）。

---

## 7. 结果

（留空。执行轮在本节**只追加**实测结果，§1–§6 一字不动。）
