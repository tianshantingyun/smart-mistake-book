# KB B33 B2 标题-only（153 条短式）预注册 —— **裁定停止，转 B2b 登记**（2026-11-02）

> **性质**：出数前冻结。§1–§5 写死；执行轮只允许在文末追加 §7，不得回改 §1–§5。
> **写作时点**：2026-11-03T07:38:18+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）；§0.1 的 porcelain/HEAD 快照取自 06:51:21（**557** 项）；07:39:21 复跑 **560** 项、HEAD 未变（他线仍在写）。文件名日期 `2026-11-02` 是批次日。
> **HEAD / origin/main**：`faab17f58b01eab78d5c08a9f843b3af3df2fecf`（本会话最近一次 `git rev-parse`；工作树在他线推进中，执行轮须重跑 §0.1）。
> **上游**：`docs/research/2026-11-02-kb-r3-dedupe-summary-quality.md`（下称 **R3**，A2 标题-only 臂）；`docs/kb-batch32b-2026-11-02.md:92-103`（205 行标题的批内读数）；**本会话收到的裁定（2026-11-03）**：取 (b) 收紧版——只应用「重建后别名集不变」的标题短式，会新增别名的节点整体剔除；剩余 <120 即停止并改立 B2b。
> **本步唯一新增写盘**：本文件。**未改任何内容包**（B2 在本批不落地任何标题），未跑 Gradle/设备/JVM。§0.3/§2 的读数是本会话用真实 `kb_build.rebuild_aliases.rebuild()` 与 `python -I` 实跑。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                   # 快照 2026-11-03T06:51:21+0800（写作 07:38:18，HEAD 未变）
git rev-parse HEAD                             # faab17f58b01eab78d5c08a9f843b3af3df2fecf
git status --porcelain | wc -l                 # 06:51 快照 557；07:39 复跑 560（他线仍在写，HEAD 未变）
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   快照：438 ?? / 14 D / 105 M；D 全部是他线 core/data/.../pdf/*
git diff --cached --name-only | wc -l          # 0
ls docs/research/2026-11-02-kb-b33-b2-title-only-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- 557 项脏项属他线与已写面；本预注册不触碰、不裹挟、不回滚。
- **本批不写内容包**：§5.1 的 `git status --porcelain -- <内容路径>` 在 B2 结束时必须仍为空。

### 0.2 输入（sha 钉死）

| 输入 | 值 |
|---|---|
| `build/agent-batch32b/rewrite/title-shortforms-new.csv` | 205 行；`APPLIED` **153** / `NOOP` **49** / `REVERTED` **3** |
| 三列旧值（`old_title`）与 v26 包的对拍 | **205/205 slug 都在 v26 包内**；**1 条 old_title 漂移**：`ext-phy-477ad6128f-022`（CSV 记 len 0「已在当前包内退役」，但该 slug 在 v26 **仍存在**，当前标题 37 字） |
| 3 条 REVERTED | `ext-phy-477ad6128f-022`（v26 存在，37 字）、`ext-phy-b1c9ef1623-003`（40 字）、`ext-che-6299d8c952-025`（27 字）——**保持原样** |
| `build/agent-batch32b/rewrite/title-overrides.json` | 本臂**不读**（只读 CSV 的 APPLIED 行；两者若冲突以 CSV 为准，执行轮先对拍） |
| 已发布别名表 `tools/kb_build/tables/alias_map.csv` | sha256 `5e8ed8baec36b30212fd81cbf4e7e9280d8d3346489f7035a0920c7317682451`；37,568 行 |
| 包内节点别名总数 | **40,841**（主包 `aliases[]` 求和） |

### 0.3 本会话实算（离线，非设备读数）

| 项 | 值 |
|---|---:|
| `rebuild(pre)` 别名候选总数 | **37,566** |
| `rebuild(post, all 153)` 别名候选总数 | **37,689**（净 **+123**） |
| 受影响的节点 | **20**（全部为 `+N / -0`，无删除） |
| 153 条 APPLIED 落在的节点数 | **20**（与上面完全同一集合；每节点 1–32 条改写） |
| 按裁定「整节点剔除」后剩余可应用 | **0 / 153**（< 120） |
| 205 集合规率（v26 树，全部 153 应用后） | **201 / 205 = 98.05%** |
| 全包合规率（v26 树） | 前 **44,174 / 65,021 = 67.94%**；全 153 应用后 **44,326 / 65,021 = 68.18%** |
| 全 153 应用的预测 `contentVersion` | `1931af69435e8dd2`（**本批不使用**，仅作 B2b 参考） |
| 本批实际包身份 | `version=26` / `contentVersion=aea886e42b36dcb2` / 主包 `96246c78…` / `.vec` `70329115…`（**全部不变**） |

### 0.4 裁定与结论（写死）

- 收到的裁定取 **(b) 收紧版**：先算 `rebuild(pre)` 与 `rebuild(post)` 的差集，定位到**节点**；凡在该节点上的标题改写**一律不应用**（不是只剔除新增别名的那一条）；剔除后剩余 **< 120** 即**停止本臂**，改立 **B2b**。
- 本会话实测：差集 = **20 个节点**，153 条 APPLIED **全部**落在这 20 个节点上 ⇒ 剩余 **0** ⇒ **B2 停止**。
- **本批不应用任何标题短式**；§1–§3 记录裁定的执行细则、测量与停止结论；§4 登记 B2b。

---

## 1. 裁定的执行细则（冻结，逐条落点）

| # | 裁定原文 | 本文件落点 | 状态 |
|---:|---|---|---|
| 1 | 用 `rebuild()` 对 pre/post 各算一次，取差集定位到节点；该节点上的改写**整体剔除**；剔除清单逐条落盘 `build/agent-batch33/b2/excluded.json`（subject / node / old_title / new_title / 新增别名数） | §2.1–§2.2 + §1.3 的 JSON 格式 | **已完成测量；excluded.json 的 20 节点清单见 §2.2**（执行轮落盘） |
| 2 | 门禁改为**一致性**：`rebuild_aliases(pre) == rebuild_aliases(post)` 逐字节相等；`alias_map.csv` sha 不变（`5e8ed8ba…`）；`run_kb_checks` / `alignment` 绿 | §2.1 的 alias-set sha；§3.2 的门 | 一致性**不成立**（20/123）⇒ 触发停止；`run_kb_checks`/`alignment` 本会话**未跑** |
| 3 | 剔除后剩余 <120 ⇒ 停止，改立 B2b（全部 153 + 别名重建 +123 + dense 重打重钉 + 设备双路 + 三规则）；B2b 不在本批强行插入，登记为下一波首项 | §3.1（停止）+ §4（B2b 登记） | **已触发**（0 < 120） |
| 4 | `ext-mat-1a1cc76f2d-032`（短式后仍含 `^`）直接回退原标题并登记；合规率按 **v26 树的 201/205** 记录（不是 32B 的 202/205），并写明差异原因 | §3.3–§3.4 | 已登记（该行所在节点也在 20 个剔除节点内） |
| 5 | 被剔除的 20 节点 / 123 别名在批报与台账各一行：它们是「最该修的长标题」，推迟到 B2b，不许标成已完成或可忽略 | §5.3 | 已登记 |

### 1.1 剔除判定（冻结）

1. 节点键 = `(subject.upper(), knowledgePoint.slug)`，由材料的 `bindings[].knowledgeNodeId`（`kb:<pack>:<subject>:atomic:<slug>`）解析得到。
2. `rebuild(pre)` 用**当前**侧车标题；`rebuild(post)` 只把 153 条 APPLIED 材料的标题替换为新值；两者用**同一** pack、**同一** `alias_exceptions.csv`。
3. 差集 = `{节点 | candidates_pre(node) != candidates_post(node)}`；差集内节点的**所有** APPLIED 改写一律不应用。
4. 剩余 = 153 − 差集节点上的改写数；`剩余 < 120 ⇒ STOP`（本会话 = 0）。

### 1.2 本批的写面：**零内容文件**

- 不写 sidecar、不写 `alias_map.csv`、不写主包、不写 dense、不改 manifest。
- 只新增本文件；执行轮若落盘 `excluded.json` 与台账，也只写 `build/agent-batch33/b2/` 与文档，不碰内容包。

### 1.3 `excluded.json` 的冻结格式（执行轮落盘）

```json
[
  {
    "subject": "PHYSICS",
    "node": "共点力的动态平衡",
    "rewrites": [
      {"slug": "...", "old_title": "...", "new_title": "...", "new_len": 12}
    ],
    "added_aliases": 25,
    "added_alias_names": ["..."]
  }
]
```

- `added_aliases` / `added_alias_names` 是**节点级**的 `candidates_post - candidates_pre`；同一节点下每一行共享这两个字段（裁定要求「新增别名数」，按节点计）。
- 条目按 `(subject, node)` 排序；`rewrites` 按 slug 排序；UTF-8 无 BOM、`indent=1`。

---

## 2. 别名不变性（测量，冻结）

### 2.1 反事实重建

- 工具：`tools/kb_build/rebuild_aliases.py` 的 `rebuild(pack, titles=…, exceptions=…)`（`bound_titles` 的来源改为本会话构造的 `(subject, slug) → 标题集合`）。
- 结果：`candidates_pre` 共 **37,566** 个别名，`candidates_post` 共 **37,689** 个，**净 +123 / −0**；**20 个节点**不同。
- 别名集规范化 sha（按 `(subject, slug)` 排序、每节点别名排序后 JSON 序列化）：
  - pre：`5f19f11fac6413c51da827e4a62070db4161f8f72003ff56b95dc26efc2d16b9`
  - post：`ebc79d8b07d7988f3427e6b8558e7f3cdc230e3dccc936f2d1c690929609ac49`（**不同**）⇒ 裁定第 2 条的「逐字节相等」**不成立**。

### 2.2 20 个剔除节点（逐条）

| # | subject | node | APPLIED 条数 | 新增别名 |
|---:|---|---|---:|---:|
| 1 | BIOLOGY | 性状显隐性的判断 | 7 | 5 |
| 2 | CHEMISTRY | v2o5常用作so2转化为so3的催化剂 | 1 | 1 |
| 3 | CHEMISTRY | 原子结构易混易错知识点 | 3 | 3 |
| 4 | CHEMISTRY | 同分异构现象和同分异构体 | 12 | 10 |
| 5 | CHEMISTRY | 电解原理易错点 | 2 | 2 |
| 6 | CHEMISTRY | 金属材料和金属的冶炼易错点 | 4 | 4 |
| 7 | CHEMISTRY | 钠和钠的氧化物易错知识点 | 3 | 3 |
| 8 | CHEMISTRY | 铝及其化合物的易错点 | 4 | 3 |
| 9 | MATH | 充分性与必要性位置颠倒理解错误致错 | 9 | 5 |
| 10 | MATH | 函数图象的翻折变换 | 7 | 6 |
| 11 | MATH | 判别式法-主要用于含有二次的分式函数-形如 | 2 | 1 |
| 12 | MATH | 利用奇偶性求值-将待求函数值或不等式利用奇偶性转化为已知区间上的函数值求解 | 5 | 5 |
| 13 | MATH | 利用导数研究单调性的易错点 | 8 | 7 |
| 14 | MATH | 忽视-漏-空集致错 | 10 | 10 |
| 15 | MATH | 数列的单调性与最值问题 | 23 | 15 |
| 16 | MATH | 方程组法-主要解决已知与-的方程-求解析式 | 3 | 3 |
| 17 | MATH | 由求时忽略对-检验致错 | 7 | 7 |
| 18 | PHYSICS | 共点力的动态平衡 | 32 | 25 |
| 19 | PHYSICS | 摩擦力的常见认识误区 | 8 | 6 |
| 20 | PHYSICS | 验证机械能守恒定律的误差分析与注意事项 | 3 | 2 |
| — | — | **合计** | **153** | **123** |

### 2.3 为什么会新增（机制）

`rebuild_aliases.title_usable`（`tools/kb_build/rebuild_aliases.py:70-105`）要求别名 ≤24 字、无逗号/句末标点、不以题面/指令开头、不以虚词结尾、括号配对、无 LaTeX 定界符。153 条标题的旧值大多因**超长或带出处括注**被拒；短式后通过形状门，于是成为该节点的**新别名**。这是标题短式的直接后果，不是工具缺陷。

### 2.4 必须写进 B2b 的一条警告

`rebuild(pre)` 的候选总数是 **37,566**，而**已发布**的包内别名条目总数是 **40,841**（`alias_map.csv` 37,568 行）。两者**不是同一集合**（本会话逐节点比对：3,280 个节点全部不同）。因此：

- B2b 的「别名重建（+123）」**只能**定义为「把 20 个节点的 123 个新增别名**合并**进现有 `alias_map.csv`」，**不能**直接 `rebuild_aliases --write`——后者会按标题+例外整表重写，变更面远大于 +123，`vectorCount` 也不是 `44,121 + 123`。
- 这条口径必须在 B2b 自己的预注册里二选一并冻结（delta 合并 / 整表重写）；本文件不替 B2b 裁定。

---

## 3. 判读（本批）

### 3.1 结论：**B2 STOPPED**

- 裁定第 3 条的触发条件成立：剔除后剩余 **0 < 120**。
- 本批**不应用任何标题短式**；包内容与 B2 写前逐字节相同；因此：
  - 特征快照 diff = **0**（无变更，见 §3.2）；
  - vec-ids diff = **0**（无变更）；
  - 三方一致保持写前值（§3.5）；
  - 标题合规率**不因本批改变**（仍是 49/205 的 205 集合规；§3.3 记录的是「若全 153 应用」的对照值 201/205）。

### 3.2 判据（本批不构成「通过」，只是零变更核验）

| 判据 | 本批读数 | 说明 |
|---|---|---|
| 特征快照 diff | **0**（未写内容） | 不是「臂通过」，是「臂未执行」 |
| vec-ids diff | **0**（未写内容） | 同上 |
| `rebuild(pre) == rebuild(post)` | **不成立**（20 节点 / +123） | 这正是停止的理由 |
| `alias_map.csv` sha | `5e8ed8ba…`（未写） | 写前 = 写后 |
| `run_kb_checks` / `alignment` | **NOT_RUN** | 本会话未跑；B2b 的前置 |

### 3.3 标题合规率（v26 树，冻结）

- 规则：`len(title) ≤ 24`、无 `$ \ ^ _`、无 `。！？；`。
- **205 集**：写前 **49/205**（= 49 条 NOOP 自身合规；153 APPLIED 与 3 REVERTED 均不合规）；若全 153 应用（B2b）→ **201/205 = 98.05%**。
- **全包**：写前 **44,174/65,021 = 67.94%**；全 153 应用 → **44,326/65,021 = 68.18%**。
- **与批次32B 的 202/205 差异（必须写进批报）**：32B 的复算脚本对缺失 slug 回退到 `new_title`，把 v32 候选里已退役的 `ext-phy-477ad6128f-022` 当作合规；该 slug 在 **v26 仍存在**（37 字标题），所以 v26 树上的分母/分子是 **205 / 201**，不是 **205 / 202**。本文件按 v26 记 201。

### 3.4 `ext-mat-1a1cc76f2d-032`（F22 残留）

- 短式后 **23 字**但仍含 `^`（`含 (-1)^n 的恒成立参数按奇偶分离求最值`），违反「无 LaTeX」口径。
- 按裁定：**回退原标题并登记**，不把 `^` 从门里排除。该行所在节点 = `MATH / 数列的单调性与最值问题`，**本身就在 §2.2 的 20 个剔除节点内**，所以 B2 不应用它；B2b 也必须先回退这一条再计入合规率。

### 3.5 三方一致（本批为写前值）

| 身份 | 值 |
|---|---|
| A 内容 | `version=26` / `contentVersion=aea886e42b36dcb2` |
| B 主包 | `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f` |
| C dense | `packSha256=96246c78…`（== B）/ `vectorCount=44,121` / `.vec` `70329115…` |

---

## 4. B2b 登记（下一波首项，**不在本批**）

### 4.1 内容（冻结）

- 全部 **153** 条标题短式（含 `^` 行先回退）＋ **20 节点 / +123 别名**（按 §2.4 的口径二选一）。
- **dense 必须重打并重钉**：别名进入向量集，`vectorCount` 从 **44,121** 变为新值（delta 口径下 = **44,244**；本会话已核：123 个新别名与 20 个节点现有别名**零重叠**，所以合并后恰好 +123；整表重写则以重打实测为准），`.vec` / `.vec.json` 的 sha 全部变化，`packSha256` 跟随主包新 sha。
- **设备双路 + 三条规则**（主集不劣 / 24 章不劣 / 零漂移 0）——标题与别名改了检索表面，B2b 不再享有「零差异」豁免。

### 4.2 前置（冻结）

1. **dense 8 步链**完整跑通并重钉（`tools/dense_build/`；`dense_asset.py` 现 sha `2dafbfa9…`）。
2. **同代判官**：`GoldenRetrievalInstrumentedTest` / `GoldenRetrievalJvmTest` / 金标三件 sha 与执行轮一致；判官换版则 B 臂必须同轮重测。
3. **别名口径**：delta 合并 vs 整表重写——B2b 预注册必须二选一；本文件不选。
4. **六元组冻结**（R3 §3.1）：`pack_sha256, .vec_sha256 + vectorCount, alias_map_sha256, golden_queries_sha256, 代码树 sha256, 判官/设备版本`。

### 4.3 复现命令（B2b 用；本批不跑）

```bash
# 反事实重建（只打印，不写）
PYTHONPATH=tools python -m kb_build.rebuild_aliases
# 别名重建后核对（整表口径）
PYTHONPATH=tools python -m kb_build.rebuild_aliases --write
# 包契约 / 对齐
python tools/ci/run_kb_checks.py
python tools/kb_coverage/alignment.py --check
# dense 重打链（8 步，见 tools/dense_build/）
```

### 4.4 不在本批

- B2b **不在本批强行插入**；本节只登记为下一波首项，含前置与复现命令。

---

## 5. 回滚 / 触碰

### 5.1 B2 本批：**零内容触碰**

| 路径 | 期望 | 写前 sha | 写后 |
|---|---|---|---|
| 16 个含 APPLIED 的 sidecar | **零改**（B2 停止） | 见 §5.2（B2b 预登记表） | 必须相同 |
| `tools/kb_build/tables/alias_map.csv` | 零改 | `5e8ed8baec36b30212fd81cbf4e7e9280d8d3346489f7035a0920c7317682451` | 必须相同 |
| 主包 / dense / manifest / index | 零改 | 主包 `96246c78…`；dense `.vec` `70329115…` / `.vec.json` `6612a28d…`；manifest `370b0979…`；index `2da0b9c5…` | 必须相同 |

- B2 的回滚 = **核验以上文件 sha 与写前一致**；不一致即说明有人越过裁定写了内容 → 停、复位、按六元组复算。

### 5.2 B2b 预登记触碰清单（**不是本批**；pre-sha 供下一波复用）

| # | 路径 | pre-sha256 |
|---:|---|---|
| 1 | `.../moe-2025-teaching-support-v2-01.json` | `feddca192e10c26bbc1b1663c6681095bfe7196f2073a5574c8c98a3d73661f3` |
| 2 | `.../moe-2025-teaching-support-v2-10.json` | `dbe1bef32e875b2366880afae7ef6ce96e06dafca95f2f77a9576be82a19d748` |
| 3 | `.../moe-2025-teaching-support-v2-11.json` | `de218526103a840b595b03071e9d4196798c9e490f147b123b3beb9ae9c98da1` |
| 4 | `.../moe-2025-teaching-support-v2-12.json` | `12dc6a55f835232c42369149b9c921b5714eb18e981d0eb9f439d4c5d00ff95f` |
| 5 | `.../moe-2025-teaching-support-v2-13.json` | `ce38b4d4cf75cfde7a22230f16c37c967eec6397f68e0284ae405ed39ddc5353` |
| 6 | `.../moe-2025-teaching-support-v2-14.json` | `1d790d3cc8068d7ed5a36babcab6f1e8209d71706821e685cf0abdd59c895c4f` |
| 7 | `.../moe-2025-teaching-support-v2-15.json` | `e30a257f08ac94ac8721bda66f40d10ebac10ed0d5780106360c891d04ad43af` |
| 8 | `.../moe-2025-teaching-support-v2-16.json` | `7612aa625a6097b7b655eab4cae4329762b2ac6125498e96fdd211d0ae7aa86b` |
| 9 | `.../moe-2025-teaching-support-v2-17.json` | `98a76e355c167a5b9758c09cac8010adb2504fb835e0767cf09b25b71f5246c8` |
| 10 | `.../moe-2025-teaching-support-v2-18.json` | `8ae8b5183dd083f4a047b0abe96f1b72bfb73852c63eb3f00ddf84b0adfcc897` |
| 11 | `.../moe-2025-teaching-support-v2-19.json` | `3224da13ff1336eef6dc10bc85c71ae628e76f117b3de557733d2aacf98faff8` |
| 12 | `.../moe-2025-teaching-support-v2-20.json` | `c0064dead7ef985416051f383ca131f63a24dfb89776ef3dff01f95185c0e2f4` |
| 13 | `.../moe-2025-teaching-support-v2-21.json` | `dd9607445f2b699181e57b41ab055e456b77509a4f5f555d9f42966525e859f0` |
| 14 | `.../moe-2025-teaching-support-v2-22.json` | `88b393fa3a37f9430b7b3459798d3f78de28d53d2a3811fb785dfc72752caf37` |
| 15 | `.../moe-2025-teaching-support-v2-23.json` | `bfe6ddb100c89ab743f5d61a053dbe06247a8db0669e7cae6f8e497502f16703` |
| 16 | `.../moe-2025-teaching-support-v2-24.json` | `d5c685414c7110a7bf22161663217dab7d38bb37eeb017882d23dea844f2e064` |
| 17 | `tools/kb_build/tables/alias_map.csv` | `5e8ed8baec36b30212fd81cbf4e7e9280d8d3346489f7035a0920c7317682451` |
| 18 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f` |
| 19 | `.../dense/bge-small-zh-int8.vec` | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` |
| 20 | `.../dense/bge-small-zh-int8.vec.json` | `6612a28d9dd71e140e91f4300c142f8ca8dae027ca58afde88e0f7d3f749dc4b` |
| 21 | `.../moe-2025-update-manifest.json` | `370b0979ec011806258439877d837a33e6f0ea3dcd7db502b201de40d8853608` |

### 5.3 被剔除的 20 节点 / 123 别名（台账各一行，冻结）

- 它们是**最该修的长标题**（旧标题多含出处括注、>24 字），因为会改变检索表面（新增别名）而推迟到需要设备测量的 B2b。
- **不许**标成「已完成」「可忽略」或「已修」；批报与 `known-defects` 台账各写一行，指向本节与 §4。
- `ext-mat-1a1cc76f2d-032`（`^` 残留）同样写入台账：B2b 先回退，再计入 201/205 的对照。

---

## 6. 未做 / 未验证（写作时点）

- **未写任何内容包**：B2 停止，标题/别名/主包/dense/manifest 均未改。
- **未跑 Gradle/设备/JVM**：§3.2 的零变更核验是「未执行」的结果，不是臂通过。
- **`run_kb_checks` / `alignment` 未跑**：裁定第 2 条要求它们为绿，本会话未执行（B2 已停止，转 B2b 前置）。
- **`excluded.json` 未落盘**：20 节点清单在 §2.2；执行轮按 §1.3 格式落盘。
- **rebuild 的例外表**：`tools/kb_build/tables/alias_exceptions.csv`（35 条有效例外）参与了 pre/post 两次计算；其 sha 未单独记录（执行轮补）。
- **B2b 的别名口径未裁定**：delta 合并 vs 整表重写（§2.4），留给 B2b 预注册。
- **`vectorCount` 新值未实测**：delta 口径下推算 44,244；整表口径下未知；以重打读数为准。

---

## 7. 执行轮追加位（只追加，不改 §1–§5）

> 执行轮在此追加：写前/写后 porcelain 与 HEAD；`excluded.json` 落盘路径与 sha；§5.1 的零变更核验（16 sidecar + alias_map + 主包 + dense + manifest，逐文件 `sha256sum`）；`run_kb_checks` / `alignment` 的 exit code（B2 停止时不跑则记 `NOT_RUN`）；§2.1 的 alias-set sha pre/post 复算；§2.2 的 20 节点表复核；B2b 的登记确认（不改本文件 §1–§5）。未跑项一律 `NOT_RUN` / `UNVERIFIED`。

---

## 7. 执行轮记录（2026-11-03 07:5x；§1–§5 未改）

### 7.1 写前状态

| 项 | 值 |
|---|---|
| HEAD | `faab17f58b01eab78d5c08a9f843b3af3df2fecf`（未变） |
| porcelain | **560**（写作时 557→560 他线仍在写；本轮只新增 `?? tools/kb_build/tables/material_retire.csv` 属他线） |
| 内容包身份 | `version=26` / `contentVersion=aea886e42b36dcb2` / 主包 `96246c78…` / `.vec` `70329115…` / `.vec.json` `6612a28d…` |
| §5.2 pre-sha 表 | **20/20 逐字节命中**（`sha256sum -c`，见 §7.4） |

### 7.2 别名不变性实算（本会话独立重跑，不复用 §0.3 的读数）

工具：`build/agent-batch33/b2/measure_alias_delta.py`（只读；调 `kb_build.rebuild_aliases.rebuild()`，
`titles` 换成"APPLIED 行替换后"的集合，`exceptions` = `tables/alias_exceptions.csv` 35 条）。

| 项 | 本会话实测 | §0.3 冻结值 | 一致 |
|---|---:|---:|:--:|
| `candidates_pre` | **37,566** | 37,566 | ✓ |
| `candidates_post` | **37,689** | 37,689 | ✓ |
| 净差 | **+123 / −0** | +123 | ✓ |
| 差集节点数 | **20** | 20 | ✓ |
| 有删除的节点 | **0** | 0 | ✓ |
| 剔除条数 / 新增别名 | **153 / 123** | 153 / 123 | ✓ |
| §2.2 逐节点 (subject, 条数, 新增) 多重集 | 与本表 **完全相同** | — | ✓ |

- 旁证：`PYTHONPATH=tools python -m kb_build.rebuild_aliases`（**不带 `--write`**）输出
  `别名：40841 → 40846；变更节点 5`，与 `3,280 节点 + 37,566 候选 = 40,846` 自洽；
  `alias_map.csv` = `5e8ed8ba…`（37,568 行）未变。日志 `build/agent-batch33/b2/rebuild-dryrun.log`。
- **§2.1 的两个 alias-set sha 未能复现**：`5f19f11f…` / `ebc79d8b…` 的聚合口径（"JSON 序列化"的
  具体嵌套/键形）未落盘。本会话试了 15 种口径（pairs/dict/嵌套/逐行/逐别名/CRLF/indent 等），
  无一种命中 ⇒ 记 **UNVERIFIED**。该值不影响结论：差集是**同一实现**对 pre/post 两份 titles 的
  逐节点集合比较（`candidates_pre(node) != candidates_post(node)`），20 个节点不一致已逐条列出。

### 7.3 裁定执行：**B2 STOPPED**

按 §0.4 第 3 条（剔除后剩余 <120 ⇒ 停止）：剩余 **0 < 120** ⇒ **B2 停止，不写任何标题**。
`excluded.json` 已按 §1.3 冻结格式落盘：

- 路径 `build/agent-batch33/b2/excluded.json`，sha256 `a17d83baa00448d7bdecfb44543ced625aa641d05aa505e25f33e4d7698bd99e`
- 20 条，键集恰为 `subject / node / rewrites[{slug, old_title, new_title, new_len}] / added_aliases / added_alias_names`；
  按 `(subject, node)` 排序、`rewrites` 按 slug 排序、UTF-8 无 BOM、`indent=1`。
- **与 §2.2 表的唯一差异**：`node` 用**包内原始主名**（如 `V2O5常用作SO2转化为SO3的催化剂。`、
  `判别式法：主要用于含有二次的分式函数，形如：`、`忽视（漏）空集致错`），§2.2 表用的是
  小写/去标点的规范化写法；按 (subject, 条数, 新增别名) 多重集比对**完全一致**。

### 7.4 §5.1 零变更核验（`sha256sum -c`，exit 0）

`build/agent-batch33/b2/expected-sha256.txt`（24 条：16 个含 APPLIED 的 sidecar + 主包 + index +
2020 两件 + manifest + `.vec` + `.vec.json` + `alias_map.csv`）→ **24/24 OK，exit 0**
（`build/agent-batch33/b2/zero-change.log`）。其余 9 个 sidecar（v2-02…09、25）由
`git status --porcelain -- core/data/src/main/resources/knowledge/` **为空**覆盖（30 个文件 == HEAD）。

`alias_exceptions.csv`（35 条生效，sha `7a8e13ed…`）参与两次 rebuild，未被写。

### 7.5 门

| 门 | 命令 | 结果 |
|---|---|---|
| `run_kb_checks` | `python tools/ci/run_kb_checks.py` | **exit 0**（30 文件 OK / consistency / roundtrip / manifest / dense / judgment_drift 全 OK） |
| `alignment` | `PYTHONPATH=tools python tools/kb_coverage/alignment.py --check` | **exit 0**（MATH 308/308、PHYSICS 138/138、CHEMISTRY 73/73、BIOLOGY 142/142；台账完整） |

注：§4.3 给的 `python tools/kb_coverage/alignment.py --check` 省了 `PYTHONPATH=tools`，直跑会
`ModuleNotFoundError: kb_build`（本会话实撞）；须按 §4.3 上一行的 `PYTHONPATH=tools` 前缀执行。

### 7.6 特征 / vec diff（零变更核验，**不是**臂通过）

`python -I build/agent-batch33/content/export_invariants.py --diff-against build/agent-batch33/content`
→ 三件 `IDENTICAL`、`DIFF_RESULT IDENTICAL`、exit 0：
`features-baseline.txt`（`f460819a…`）、`vec-ids-baseline.txt`（`dedc652d…`）、`materials-sample-20.txt`（`b0bb8c18…`）。
按 §3.2 的口径登记：这是"本批未执行"的结果，**不是** B2 通过了不变性判据。

### 7.7 执行轮发现的跨臂事实（必须上报）

- **B1（摘要-only）的晋升结果已不在树里**：本会话开工时主包仍是 `96246c78…`，但 6 个 sidecar +
  manifest 已于 **07:29:12–07:29:14**（mtime 实读）被外部写回 v26 字节
  （v2-02 `ba443288…`、manifest `370b0979…` = HEAD 形态），`git status` 对知识目录为**空**。
  B1 报告里记录的是 promote 后的 `version=27 / ae939632…`（`build/agent-batch33/b33-b1-summary/promote.log`、
  `gate1.log` 可复现当时的读数），因此这是**B1 落地后被回滚**，不是 B1 未生效。
- 对本臂的影响：**无**。B1 只改 `summaryMarkdown`，不改 `title`；别名候选只读材料 `title`，
  故 37,566/37,689/20/+123 与 B1 前后无关（本会话已在 v26 树上独立复算，与 §0.3 逐项一致）。
- 反而使 §5.1/§3.5 的"写前值"重新成立（v26 / `aea886e42b36dcb2`）。

### 7.8 B2b 登记确认

§4 的登记不变：全部 153 条 + 20 节点/123 别名、dense 重打重钉、别名口径二选一未裁定。
§5.3 的 20 节点/123 别名与 `ext-mat-1a1cc76f2d-032`（`^` 残留）仍需在批报与台账各一行，
**不许**标成已完成。本执行轮未写任何内容文件，未进入 B2b。

### 7.9 未跑 / 未验证

- 设备旁证、dense 重打、B2b 前置（§4.2）：`NOT_RUN`（不在本批）。
- §2.1 的 `5f19f11f…` / `ebc79d8b…`：`UNVERIFIED`（口径未落盘，见 §7.2）。
- **B1 回滚的根因未定位**：只确认时间窗与 mtime，未找到执行者；`NOT_RUN`。

证据目录：`build/agent-batch33/b2/`（`excluded.json` / `measure.log` / `measure_alias_delta.py` /
`rebuild-dryrun.log` / `run_kb_checks.log` / `alignment.log` / `expected-sha256.txt` / `zero-change.log`）。

