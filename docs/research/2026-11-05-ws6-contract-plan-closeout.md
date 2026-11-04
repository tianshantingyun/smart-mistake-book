<!-- 体积自查：WS6（计划项 45–50）本批唯一新增记录件；只读核对 + 仓内文档落笔，零 Gradle、零 adb、零 git 写（只有 `git status/diff/rev-parse/hash-object/apply --check/merge-tree` 只读形态）。 -->

# 批次 43 · WS6 收口：契约 P0 冲突清单 / 计划收尾盘点 / MASTER 行 / 决策台账 / KD 全量审计 / 批次 44 残余输入

| 字段 | 值 |
|---|---|
| evidence-id | `B43-WS6-CONTRACT-PLAN-01` |
| head | `0fef943641f53560b92ce2afec4da22bf52f5e86`（本轮 `git rev-parse HEAD`） |
| 执行者 | 文档/计划工程（WS6 子代理），共享工作树 |
| 本线本批写入（仅这些） | 本文件 + `docs/research/2026-11-05-contract-p0-conflict-list.json` + `docs/known-defects.md` 新开 KD-84；另 `docs/REFACTOR-MASTER-LINE.md` / `docs/agent-first-refactor-decisions-2026-09-23.md` 各追加一节（项 47/48） |
| 未执行 | 任何 Gradle、任何 adb/`am instrument`、任何 `git apply`/`merge`/`add`/`commit`、任何 Provider 调用 |
| 输入 | 计划 `.zcode/plans/plan-2026-11-05-batch-43.md`（全文实读）；批次 42 记录 §9.2/§9.3/§9.5；R6 `docs/research/2026-11-05-plan-closure.md`；R5 `2026-11-05-contract-merge-protocol.md`；`docs/known-defects.md`（审计前 83 条标题机器解析，本批新开后 84 条）；`docs/REFACTOR-MASTER-LINE.md`；`docs/agent-first-refactor-decisions-2026-09-23.md` |

> **口径**：**实跑** = 本轮本机执行并读输出；**实读** = 本轮读仓内源码/文档原文（给行号）；**转录** = 引既有件原文（给 `file:line`）。状态词：完成 / 部分 / 未执行（UNVERIFIED）。

---

## 0. 计数口径（本批计划实列 78，不是 74）

本轮按行首 `^\d+\.\s` 独立解析计划（实跑）：

```text
count 78  first [(1,27),(2,28)]  last [(77,125),(78,126)]  missing []  dups []
```

即 §2 实列 **78** 个连续编号（`plan:26-126`），实账 **6 + 78 + 20 = 104**；计划 `:1`、`:24`、`:131` 三处仍写 74/100。**本线不改计划文件**；「74/100」登记为**计划口径缺陷**（批次 41 记 55 vs 62、批次 42 记 74 vs 90，本批 74 vs 78——连续第三批）。R6 §0.1 同判（`2026-11-05-plan-closure.md:27-40`）。

WS6 项号映射（按计划实列，非自述 74）：**45 契约 P0 → 46 收尾盘点 → 47 MASTER 行 → 48 决策台账 → 49 KD 全量审计 → 50 批次 44**，六项无缺号。

---

## 1. 项 45：契约 P0 —— `docs/current` 未清空 → 出逐文件冲突清单

### 1.1 判定（实跑）

| 检查 | 命令 | 结果 |
|---|---|---|
| 工作树脏面 | `git status --porcelain -- docs/current` | **7 条 ` M`**（README / architecture-contract / data-schema / implementation-status / model-task-contracts / product-contract / release-gates） |
| 暂存区 | `git diff --cached --name-only -- docs/current \| wc -l` | **0** |
| 改动规模 | `git diff --stat -- docs/current` | **99 insertions(+), 5 deletions(-)**，7 文件 |
| patch 目标是否脏 | `git hash-object docs/current/visual-generation-contract.md` vs `git rev-parse HEAD:...` | 两值同为 `71cc5493…` → **目标文件干净** |
| patch 可应用性 | `git apply --check --verbose docs/research/2026-11-04-contract-v1.1-p0-applicable.patch` | **EXIT=0**（只打印 `docs/current/visual-generation-contract.md`） |

**结论：不应用 patch**（计划项 45 的前件「`docs/current` 清空」不成立），落盘逐文件冲突清单 `docs/research/2026-11-05-contract-p0-conflict-list.json`（8 条目，schema `contract-p0-conflict-list/1`，`python -I -X utf8 -c json.load` 通过）。

**同时更正批次 42 的表述**：批次 42 记录 §6.1-7 写「7 M → 契约并入 BLOCKED」，在文本层面**不成立**——7 M 与 patch 目标不相交，patch 本轮 `--check` 仍 exit 0。真正的阻塞是计划 §4「`docs/current` 热区禁改」这条**纪律**，不是冲突。R5 §4 同判（`2026-11-05-contract-merge-protocol.md:87-91`），本轮独立复跑得出同一结论。

### 1.2 逐文件归属与分类（本轮 `git hash-object` / `git rev-parse 32026050:` / `--numstat` 实跑）

| 文件 | HEAD blob | 工作树 blob | `32026050` blob | 行数 | 类 | 处置 |
|---|---|---|---|---|---|---|
| `README.md` | `9e8ba25a…` | `801c235e…` | `77d078b1…` | 16+/0− | A | defer（层 2 六行无提交来源，必须先单独提交） |
| `architecture-contract.md` | `7aada75a…` | `aeb9fd88…` | `aeb9fd88…` | 21+/0− | B | defer（「coordinator planned」已被 `core/domain/FigureGenerationCoordinator.kt` 推翻） |
| `data-schema.md` | `1268bba7…` | `ff11c756…` | `ff11c756…` | 10+/0− | B | defer（正文写 Room 66/67、target 68/18；现树 73/19） |
| `implementation-status.md` | `5fd1a5db…` | `d05c7590…` | `d05c7590…` | 2+/0− | A | defer |
| `model-task-contracts.md` | `fc450766…` | `8226919d…` | `8226919d…` | 10+/1− | B | defer（删除行正确；「planned GENERATE_FIGURE」措辞过时） |
| `product-contract.md` | `b0e70a30…` | `7029de96…` | `7029de96…` | 15+/2− | **C** | defer（−2 改写当前行为断言，须主会话裁定） |
| `release-gates.md` | `515bb02e…` | `a315eece…` | `a315eece…` | 25+/2− | **C** | defer（−2 是放宽式门语义改写，须裁定） |
| `visual-generation-contract.md` | `71cc5493…` | `71cc5493…` | `de770fe5…` | 0 | **C**（patch 目标） | ours（分支版会把契约回退约九个批次） |

### 1.3 合并干跑（只读，复现 R5 的冲突面）

`git merge-tree 1d8bb937 HEAD origin/codex/plans-2026-10-20` → **exit 0**、`<<<<<<<` **25** 处、冲突文件 **7** 个：

| path | 冲突段 |
|---|---:|
| `docs/current/visual-generation-contract.md` | 7（add/add，ours `71cc5493…` / theirs `de770fe5…`） |
| `docs/research/2026-10-17-6c2-content-selection-design.md` | 3（**6C-2 裁定载体**） |
| `docs/research/2026-10-19-ui-ux-audit.md` | 1 |
| `docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md` | 1 |
| `docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md` | 4 |
| `docs/superpowers/plans/2026-10-19-ui-ux-redesign-plan.md` | 5 |
| `docs/superpowers/specs/2026-10-19-ui-ux-redesign-design.md` | 4 |
| **合计** | **25**（与标记数一致） |

7 个脏 `docs/current` 文件在该干跑里 **0 冲突**（main HEAD 已含分支同内容，README 只差未提交层）。**顺序铁律**（转录 R5 §4 E11，本轮未复核）：patch hunk #2 只能落在 main 版；落到分支版 `de770fe5` 会 `hunk #2 FAILED`。

---

## 2. 项 46：计划收尾盘点已落盘（不重复造件）

- 载体：`docs/research/2026-11-05-plan-closure.md`（407 行，`B43-R6-PLAN-CLOSURE-01`）**已在工作树**（当前未跟踪，`git status` 显示 `??`），含 PRD/6C/4E/5B/5b/5c 逐项判定（可做/热区/外部阻塞/已关闭）、KD 17 条 open 逐条判定、§8 本批可闭合 14 组、§9 三类唯一出口。
- 本轮独立核对其基数（实跑/实读）：74 行 = 9（PRD）+ 7（6C）+ 34（4E）+ 12（5B）+ 6（5b）+ 6（5c）；判定合计 **可做 30 / 热区 26 / 外部阻塞 17 / 已关闭 1**（`2026-11-05-plan-closure.md:75-90`）。
- **不新增第二份盘点**：R6 已覆盖项 46 的字面（落盘 + 四值判定），再造一份只会产生两份会漂移的账。本文件只补 R6 未覆盖的两处——KD 台账审计（§4）与批次 44 残余输入（§5）。
- R6 自身边界（转录）：写作时各 WS 并行，本批 78 项执行结果全部 UNVERIFIED；它不依赖 R1–R5 结论（`:388`）。

---

## 3. 项 47 / 48：台账落点

| 项 | 落点 | 内容要点 |
|---|---|---|
| 47 | `docs/REFACTOR-MASTER-LINE.md`（批次 43 · WS6 行，插在 WS3 行之后） | 契约 P0 冲突清单 + 7 M 归属、项 46 指向 R6、KD 审计 84 条/17 open、KD-84 新开、批次 44 残余输入；只登记载体与判定，不代断言门禁/设备 |
| 48 | `docs/agent-first-refactor-decisions-2026-09-23.md`（`## 批次 43 …` 节） | 契约 P0 不落正文的理由与冲突分类、KD 审计裁定、KD-84 新开理由、批次 44 分派；附边界（热区零改动、零 Gradle/零设备/零 git 写） |

两项都只写「本线实跑/实读的事实 + 本线的处置」，其他 WS 的 KD 关闭/维持按各自 Progress 块转录，不代裁。

---

## 4. 项 49：KD 台账全量审计

### 4.1 机器解析（实跑）

```text
python -I -X utf8  … re.match(r'^## (KD-\d+) \((.*)$') …
total 83   open 16   closed 67   odd 0
```

- **closed 67**：`KD-1…23、25、26、30、31、33…60、62、64、66、69、70、74、75、77、79、80、81、83`（按标题 `resolved/fixed/closed` 标记，未逐条复验关闭质量）。
- **open 16**：`KD-24,27,28,29,32,61,63,65,67,68,71,72,73,76,78,82`。
- 与 R6 §1（`open 17 / closed 66`）的差 = **KD-75** 已由 WS1 于本批关闭（`docs/known-defects.md:2879` 标题、`:2908` Progress 块）。

### 4.2 open 17 条逐条处置（16 既有 + 本批新开 1）

| KD | 行 | 本批证据/现状（实读或转录） | 处置 | 唯一出口 / 前置 |
|---|---:|---|---|---|
| KD-24 | 875 | KB 线词面；本批无 KB 证据 | 维持 open | KB 线（他线） |
| KD-27 | 1063 | CI `check` job 冻结；本批未触 CI | 维持 open | CI 基础设施（他线） |
| KD-28 | 1104 | 标题即「接受边界」，Disposition 明写不收敛 | 维持（接受边界） | 无；**标题 hygiene 未改**（§4.4 F3） |
| KD-29 | 1118 | 标题「登记」，Disposition「暂不处理」 | 维持（接受边界） | 用户改取舍则重开 |
| KD-32 | 1180 | 两处峰值已修，余项无本批证据 | 维持 open → 批次 44 设备内存项 | 批次 44 |
| KD-61 | 2447 | low；写门覆盖不足 | 维持 open → 批次 44 | 批次 44 |
| KD-63 | 2542 | low；清标记时机窗口 | 维持 open → 批次 44 | 批次 44 |
| KD-65 | 2575 | low；纯 KDoc | 维持 open → 批次 44（一行） | 批次 44 |
| KD-67 | 2639 | 产品面在 `feature/*`（热区） | 维持 open | 主会话授权 feature |
| KD-68 | 2666 | 同上（`feature/review`） | 维持 open | 主会话授权 feature |
| KD-71 | 2747 | 修复已落，缺设备复跑 | 维持 open → 批次 44 | 批次 44 设备 |
| KD-72 | 2784 | 两次隔离副本 `--write-locks` 失败（批次 42 §6.1-6） | 维持 open | 先解配置面变体歧义（W1–W6） |
| KD-73 | 2828 | Fix direction 二选一未决 | 维持 open → 批次 44 | 批次 44 |
| KD-76 | 2912 | **本批 WS2**：两批各 n=19/20、各有一轮产品红、A6 **NOT EVALUABLE**（`docs/research/evidence/2026-11-05-batch-43/ws2-kd76-a6.md:129,160`）；WS3 isolation 仍 UNCONVERGED | **维持 open** | 产品进程首请求（`Application` 热区）/ C4 毫秒 / 四段链 |
| KD-78 | 3044 | ①② 未核销；载体在 `core/model` 非热区 | 维持 open → 批次 44 | 批次 44 |
| KD-82 | 3262 | known limitation，不排期 | 维持 open | 广告解冻批再裁 |
| **KD-84** | **3304** | **本批新开**：部分页打印 `writeSubset` 丢文档级 `/AIGC`（KD-75 C8 分出；代码 `MistakePdfDelivery.kt:146,215`；设备用例以 `known gap` 钉死） | **新开 open** | 重注入或取消部分页打印（二选一），须独立读者读回 |

**KD-75（closed，`docs/known-defects.md:2879,2908`）本轮只做载体核对（实读/实跑）**：

| 关闭主张 | 本轮能核到的载体 | 结论 |
|---|---|---|
| C2 模型解析文本判据 | `MistakePdfEligibility.kt:157` 派生、`MistakePdfExporter.kt:241` 使用、`PdfAigcContentPolicy.kt:46` 判据（`grep` 实跑） | 载体存在 |
| C4 代际升版 | `PdfAigcContentPolicy.kt:30` `GENERATION = 2`；`MistakePdfEligibility.kt:460-461` `mistake-export-v4-aigc-gen-${GENERATION}`；`MistakePdfExporter.kt:201` 哨兵 | 载体存在 |
| C1/C2/C3 设备 pypdf 读回 | Progress 块自述三份产物（`real-export-labeled.pdf` 53,271 B / `model-text-labeled.pdf` 42,900 B / `pure-user-unlabeled.pdf` 40,406 B）与 run 目录 | **2026-11-05 修复环订正**：`build/wsdevice-batch43/ws1/pdfs/` 现有 `*-5570.pdf`（2,514,765 B / 2,492,555 B）与两份 0 B；独立 pypdf 实读见 `ws1-kd75-c2.md` §2。台账 42,900 B 不可复现，已在 KD-75 Progress ⑨ 订正 |
| 门禁 | Progress 块自述 `:core:model:test` 644 例、`:core:export:testDebugUnitTest` 172 例 0 失败 | **转录，未复跑**（本线零 Gradle） |

→ **不推翻关闭**（代码载体在、WS1 自述有设备与独立读者实测），但把「批次 43 证据目录缺 WS1 E 文件」登记为交付缺口（§4.4 F1）。**2026-11-05 修复环已补**：`docs/research/evidence/2026-11-05-batch-43/ws1-kd75-c2.md`（C1–C8 对账 + 本轮 pypdf 实读 + 0 B 文件警示）。

### 4.3 关闭质量边界（本轮不重判）

67 条关闭项的判定依据是**标题标记 + 文内关闭段**，不是本轮复验。R6 同口径（`:90`）。本审计能保证的是：① 83→84 条标题无缺号/无重号；② 每条 open 都有处置；③ 新增条目有具体失败与出口。

### 4.4 审计发现

| # | 发现 | 证据 | 严重度 | 处置 |
|---|---|---|---|---|
| F1 | **KD-75 关闭缺批次 43 E 文件**：证据目录只有 research-crosscheck / ws2 / ws3 / ws4 / ws5 五件，无 WS1（KD-75）件 | 本轮 `ls docs/research/evidence/2026-11-05-batch-43/` | 中（可追溯） | **已补（2026-11-05 修复环）**：新增 `ws1-kd75-c2.md`；并订正「全仓未命中三份 PDF」——PDF 在 `build/`（gitignored），不在仓内证据目录 |
| F2 | 计划口径 74 vs 实列 78（第三批同族） | `plan:1,24,131` vs 本轮解析 `count 78` | 中（口径） | 记录按 78 对账；**2026-11-05 修复环已订正计划为 78 / 104**；建议模板加机器校验（R6 F1 同判） |
| F3 | KD-28/29/82 标题仍写 `open`，Disposition 已是「接受边界/不处理/不排期」 | `KD:1104,1118,3262` 标题实读 | 低（hygiene） | **本线不改标题**：改标题等于改台账语义（open = 仍未修，accepted = 不修），须主会话一次裁定「用哪个词」；本次审计按「维持（接受边界）」记账并在本表标注 |
| F4 | KD-83 段末曾挂 KD-75 正文（S10，批次 42 登记） | 批次 43 WS6 实读时仍在 | 低 | **已修**：KD-83 段内不再挂 KD-75 正文；C8 另立 KD-84（`:3306`） |
| F5 | 部分页打印丢 `/AIGC` 在 KD-75 关闭后**无台账承接**（Fix direction 允许「明确登记」，但登记必须有条目） | KD-75 `:2908` ⑥ C8「另立条目受理」 | 中（合规） | **新开 KD-84**（`:3304`），只承接 C8，不为 KD-75 关闭背书 |
| F6 | DOCX `docProps/custom.xml` 对称缺口（R1 U9）同样「另立条目」但本批无任何代码/设备证据 | KD-75 Fix direction 末句；WS1 Progress「DOCX…仍按 R1 U9 另立条目」 | 低 | **不新开**：无可指认的本批载体，只登记在 KD-75 关闭边界；若批次 44/合规线要做 DOCX 导出再开条目 |

---

## 5. 项 50：批次 44 —— 草案已存在，本线补「残余项输入」

### 5.1 现状（实跑）

- `.zcode/plans/plan-2026-11-05-batch-44-acceptance.md` **已在树且干净**（96 行，`git status` 无输出）：标题「批次 44（2026-11-05/06）：10× 深度验收批——1000 项检查 + 验收替代机制」。
- 因此项 50 的「草案」**不需要本线再造第二份**；再造会与主会话这份 1000 项验收计划并行漂移。**本线交付的是它缺的输入**：R6 §9.5 的 26 项残余实现项 + 5 条 KD 小修（`2026-11-05-plan-closure.md:347-359`）。

### 5.2 残余项输入（建议并入批次 44 的 §2/§3，不要另起一份计划）

| 组 | 项 | 数量 | 与本批 43 的关系 |
|---|---|---:|---|
| 4E 设备向量 | 4E-A3/A9/A15/A16/A22/A25/A27/A28（含 API 23） | 8 | 本批 WS4 只做既有套件复基线，不含新向量 |
| 4E 数据侧/扩测 | 4E-A7/A13/A19/A20/A21 | 5 | 无本批载体 |
| 4E 许可入仓 | 4E-A32（快照在 WS10-66，缺入仓载体） | 1 | 本批只到快照 |
| PRD/5B/5b 复验 | PRD-A1/A2/A4/A5/A6/A7 + 5B-A7/A8/A9 + 5b-A3/A4 | 11 | 无本批载体 |
| 6C 文档 | 6C-A4（§9.9 两项） | 1 | 纯文档，可并入验收台账 |
| KD 小修 | KD-61/63/65/71/73 | 5 | low/中，逐条出口见 §4.2 |
| **合计** | | **26 + 5** | |

**进不了批次 44 的**：热区 26 项（须主会话逐文件授权）、外部阻塞 17 项（用户裁定/外部资源/他线/顺序）、KD-67/68（热区）、KD-72（配置面）。它们不是「排期问题」，是「没有第二条路」。

### 5.3 批次 44 自身的前置（如实）

- 批次 43 的各 WS 执行状态在本文件写作时**尚未收口**（各 E 文件自述或未落盘）；批次 44 的 1000 项验收若以批次 43 为基线，必须先有批次 43 记录。
- 本线零 Gradle/零设备，无法为批次 44 预填任何 PASS。

---

## 6. UNVERIFIED / 未执行（不得写成通过）

| 项 | 状态 | 原因 |
|---|---|---|
| 契约 P0 patch 真应用 / 7 M 真合并 / README 层 2 独立提交 | **未执行** | `docs/current` 热区 + 本线零 git 写；只做 `--check` 与只读干跑 |
| patch 对分支版的 `hunk #2 FAILED` | **转录** | 引 R5 §4 E11，本轮未复跑 `patch --dry-run` |
| KD-75 的设备 `OK (7 tests)` 与 pypdf 三份读回 | **转录** | 台账 Progress 块自述；仓内无 WS1 E 文件、`find` 未命中产物 |
| KD-76 / isolation 设备结论 | **转录** | 引 WS2/WS3 E 文件判定行（`ws2-kd76-a6.md:129,160`、`ws3-isolation-selfwitness.md:109,152`），本线未跑设备 |
| KD-75 `:core:model:test` 644 例、`:core:export` 172 例 | **转录** | 本线零 Gradle |
| 67 条已关闭 KD 的关闭质量 | **未重判** | 只解析标题标记；重判不在 WS6 范围 |
| 批次 44 任何执行 | **UNVERIFIED** | 计划在树，未开工 |

---

## 7. 回链

- 计划：`.zcode/plans/plan-2026-11-05-batch-43.md:80-126`（WS6 45–50，实列号见 §0）；§4 硬约束（热区/`core/export` 最小 hunk/外部阻塞 runbook）。
- 前情：批次 42 记录 §9.2（6C-2 回滚）、§9.3（S4 两层裁定）、§9.5（收口裁定）；R5 `2026-11-05-contract-merge-protocol.md` §2/§4/§6；R6 `2026-11-05-plan-closure.md` §0/§1/§9.5。
- 本线产物：`docs/research/2026-11-05-contract-p0-conflict-list.json`；`docs/known-defects.md:3304`（KD-84）；`docs/REFACTOR-MASTER-LINE.md` 批次 43 WS6 行；`docs/agent-first-refactor-decisions-2026-09-23.md` 批次 43 节。
- 不可变清单：`plan:5`；`docs/research/2026-11-04-6c2-rulings.md`。本线未触碰 `core/domain`、`core/ui`、`feature/*`、`docs/current`、`settings.gradle.kts`、`Application/Root`。
