# 2027 版《53 知识清单》扫描件 · 全量收口报告

- 日期：2026-09-28
- **「本轮」指哪一段**（依据：我读的文件 mtime，全部为北京时间）：
  - 2026-09-27 23:47:52 提交 `d3ddfbf4`（「机械门判坏的页自动进重转队列」）→ 23:48:06 重转队列被重写（35 行）→ 23:52:40–00:18:58 队列 35 页的稿被逐页重写（我逐页读 mtime）→ 00:19:31 补齐目录机械门快照 `2027-53-fill/grade.json` → 00:19:33 转写库（`knowledge-production/2027-53-transcripts/` 最新 mtime）→ 00:19:34 页级账本 `transcription_pages.csv` → 00:19:37 `extraction_state.csv`。（mtime 用法②：这一串只用来读**同一批产物的相对先后**，不当作绝对时间真值。）
  - 所以**本轮 = 2026-09-27 23:44–2026-09-28 00:20 这一段：队列重建 → 重转 35 页 → 并入 → 重建账本 → 三道门**。
  - 材料里还带着更早的两批（派工 351 页、66 个审计片），它们在盘上的产物更老（补齐页产物多落在 09-26 02:00–10:00，审计片 mtime 落在 09-26 11:05–14:34）。材料把它们一并算作本轮，本报告分开标注日期。
- **先把后面的说法定义清楚**（后文全靠它们才读得通）：
  - **「材料」** = 本轮运行交给本报告撰写者的那份机械结果文本（351 条逐页清单、各门退出码、入块库输出、boundary 备注等）。它是**本轮运行的自报结果**：凡我在盘上复核到独立证据的，文中标「**我实测**」；只有材料一方支撑的，文中写「材料记…」，并列进附录的「本次没有复跑」清单。**与盘上实测冲突时以盘上实测为准并就地标注**。
    - **材料不在盘上**：它是直接交给我的文本，**我没有它的文件路径、也回看不到原件**（所以它的原文我没法给读者复现）。我从它**解析出的唯一副本**是我这次落盘的 351 条清单 `tmp/_dispatch.json`（不是原件本身）。
  - **「题面」** = 本轮派工本报告的那条指令文本（同样**不在盘上**）。本报告只在 3.3 用到这个词——「成品包 22 门」这个叫法出自它。
  - **「上一版报告」** = **同一路径文件在本轮之前的那一版**（`knowledge-production/2027-53-scan-report.md` 的旧内容），已被本版覆盖，且**不在 git 里**——我实测 `git ls-files knowledge-production/2027-53-scan-report.md` 无输出、`git status` 对它显示 `??`，所以**它没有第二来源可查**；凡引用它的地方文中都写「上一版报告记…」并标明我未复核。
  - **「补齐目录」** = `knowledge-production/2027-53-fill/`。**我实测 504 页页产物**（MATH 49 / CHEMISTRY 184 / PHYSICS 58 / BIOLOGY 213；按 `pNNNN.jsonl` 计数）。它的构成见 1.2。
  - **「转写库 / 账本 / 块库」** 三个词分别指 `2027-53-transcripts/`、`tools/kb_coverage/tables/transcription_pages.csv`、`tools/kb_coverage/tables/extracted_chunks.jsonl`。
  - **mtime 怎么用（分三档）**：本报告用 mtime 的口径写在 3.5 末条——**①否定方向**（mtime 早于本轮 ⇒ 不认领）、**②相对先后**（同一批文件的先后，用于定义「本轮」与判断「稿晚于裁定表」）、**③单列侧证**（标明「只是侧证」）。正文用到 mtime 的地方都标了是哪一档。
- **一句话结论（三道门量的不是同一样东西，别混读）**：
  - **页级机械门**（量页产物）：补齐目录 **504／504 过闸**（我实测，退出码 0）；材料记的「派工 351 页过闸 351／未过闸 0」「重转后过闸 35/35」在这把尺子上成立。
  - **转写质量门**（账本口径，量全部 1205 页）：**退出码 1，35 页仍报问题**——这 35 页就是本轮的 35 页重转队列，问题不再是文本残迹，而是**它们的判决还没被撤回**（见 3.1、4.1）。
  - **`extraction_state --verify`：0 问题**（我实测，退出码 0）——它量的是**扫描线的抽取账本**，只说明「这本账完整」，**不说明内容过关**。
  - **成品包门 `kb_build.gate`：退出码 0 全绿**（我实测）——它量的是**既有成品知识包**，**不含**本轮扫描内容。
  - 成品包全绿 **不等于** 本轮扫描内容过关；**本次扫描内容尚未写入知识库**（第五节）。

---

## 一、覆盖率

### 1.1 四册总页数、原 847、本轮补齐（按科）

| 学科 | 总页数（我读 manifest） | 本轮派工（材料） | 过闸（**材料口径**） | 未过闸（**材料口径**） | 其中第一轮没过、定点修过（rounds=1） | 原 847（材料只给总数） |
|---|---|---|---|---|---|---|
| BIOLOGY | 215 | 213 | 213 | 0 | 5 | （材料未给） |
| CHEMISTRY | 235 | 134 | 134 | 0 | 4 | （材料未给） |
| MATH | 552 | 2 | 2 | 0 | 2 | （材料未给） |
| PHYSICS | 203 | 2 | 2 | 0 | 0 | （材料未给） |
| **合计** | **1205** | **351** | **351** | **0** | **11** | **847** |

- 四册总页数读自 `build/2027-53-pages/manifest.json`（552/235/215/203）。
- 「派工 351 页（过闸 351、未过闸 0）」「另有此前已过闸 5 页未重做」出自材料。**按科拆分是我对材料给出的 351 条逐页清单解析计数得出**：351 条、**无重复页**、按科 213/134/2/2；`rounds=1`（第一轮没过、派定点修修过一轮后过闸）共 **11 页** = BIOLOGY p131/p200/p202/p204/p209、CHEMISTRY p231/p233/p234/p235、MATH p550/p551。
- **派工清单覆盖的页段**（我把材料那 351 条按科展开，同一口径）：BIOLOGY = p2–p215 中除 p15（试点页）以外的每一页（213 页）；CHEMISTRY = p28、p68、p70、p95、p97–p98、p105–p154、p157–p168、p170–p235（134 页）；MATH = p550–p551（2 页）；PHYSICS = p202–p203（2 页）。
- **单读这张表会误判现状**：表里「过闸／未过闸」两列是**补齐阶段**的页级记录（材料口径）。**现状以 3.1 与 4.1 为准**——账本口径下仍有 35 页 fail。
- **原 847 页材料只给总数、没有按科拆分**，我也没有从盘上复原出唯一口径，所以本表按科留空。**我搜过「847」的下落，只找到两处工具文档**：`tools/kb_coverage/collect_audits.py:6`、`tools/kb_coverage/apply_transcript_audits.py:6` 都写「S4 的换人审计（847 页）」。**这条搜索不是全仓穷尽**：我跑的是 `grep -rn "847" --include=*.md --include=*.py --include=*.json knowledge-production/*.md tools/kb_coverage/*.md tools/kb_coverage/*.py docs/*.md`（只覆盖这几个目录与后缀，没扫其他目录、也没扫数据/二进制文件），所以准确说法是「**我搜过的范围内只有这两处**」，不是「仓内只有两处」。同一句里 `collect_audits.py:6` 把「S3 补齐」写作 **358 页**——与材料的 351 不一致；这两个数是否同一口径，我**没有盘上证据**，不据它换算。
  - 「847 + 351 + 5 + 2 = 1205」这个算式（2 = 第四节第 3 条的 UNCERTAIN 页）在算术上自洽，但**材料没有这么说**，我也没有证据证明 847 恰等于四册里不属于另外三个集合的全部页——**只记下这个观察，不当结论**（本文件上一版曾因同类求和推算而勘误撤回，这次不重犯）。

### 1.2 补齐目录 504 页的构成（我实测的集合关系，全部可复算）

| 批次 | 页数 | 按科 | 依据 |
|---|---|---|---|
| 本轮派工 | 351 | BIO 213 / CHE 134 / MATH 2 / PHY 2 | 材料给的 351 条清单（我解析） |
| 上一轮的重转队列 | 115 | MATH 38 / CHE 46 / PHY 31 | **从 git 取回**：`git show d3ddfbf4^:tools/kb_coverage/tables/retranscribe_queue.csv`（105 行 → 115 页） |
| 本轮重转队列 | 35 | CHE 2 / MATH 8 / PHY 25 | `tools/kb_coverage/tables/retranscribe_queue.csv`（现行，35 行） |
| 此前已过闸、未重做 | 5 | CHE p69/p94/p96、MATH p549/p552 | **集合差**：补齐目录 − 上面三批 = 恰好这 5 页（我实算） |
| **并集（= 补齐目录现存量）** | **504** | MATH 49 / CHE 184 / PHY 58 / BIO 213 | 351 + 115 + 35 + 5 − **2 处重叠** = 504（我实算） |

- **两处重叠**（同一页出现在两批里）：**CHEMISTRY p217**（在派工清单、也在本轮队列）、**MATH p22**（在上一轮队列、也在本轮队列）。除此之外四批互不重叠。
- **「行 → 页」的换算是怎么做的**（读者要能照着复算）：队列表与裁定表的 `pages` 字段都支持三种写法 `"15"` / `"15-28"` / `"15,17"`（写法约定见 `tools/kb_coverage/apply_transcript_audits.py:6` 的 docstring）。我按这三种写法把每行展开成页号后计数：**105 行 → 115 页**取的是**展开后去重**的页数；**296 行 → 906 页次**取的是**展开后不去重**的页次（同一页被两行各写一次就算两次）。所以「115 是页、906 是页次」，两者都不能与各自的行数直接相除；本报告没再对它们做别的换算。（我用一次性临时脚本做这次展开，没有落成仓内工具；复算按上面三种写法展开 `pages` 列即可。）
- 四册 1205 页在账本上**都已转写**（3.1 的「已转写 1205」）；其中 504 页有补齐目录的页产物（上表并集），其余 **701 页的稿来自更早的转写**，本报告不追它的批次。
- 「另有此前已过闸 5 页未重做」这 5 页：我逐页核过 —— 都在补齐目录、账本里 **gate=pass / ACCEPT**、且不在派工清单、不在本轮队列；它们页产物的 mtime 是 09-26 02:25:36。**光凭 mtime 分不开它和派工批**（派工批产物多落在 09-26 02:00–10:00，02:25:36 正落在这个区间里），所以把它们认成「此前已完成」靠的是上面那条集合差，不是时间。**这 5 页是「此前已完成」的性质，不是本轮成果。**
- 试点（S2）5 页 **BIOLOGY p1/p15、CHEMISTRY p2/p27、MATH p162** 与上表那 5 页**不是同一批**：试点页**不在补齐目录**里（我实测 5 页皆不在），账本上都是 pass/ACCEPT。

### 1.3 本轮结束时的转写 / 块库现状

- 转写：账本报「页数 1205｜已转写 1205｜缺失 0」——**这句只说账本上每页都有条目，不等于每页内容完整**；内容完整与否由审计与三道门分别量。
- 块库（我实测，**累积值**，不等于本轮写入量）：`tools/kb_coverage/tables/extracted_chunks.jsonl` 里这四本共 **8007** 块（MATH 1998 / CHEMISTRY 2935 / BIOLOGY 2054 / PHYSICS 1020）。该文件 mtime **23:16:35**，**本轮没写过它**（见 3.4）。
- **本报告里并存三组「块 / 条」数，口径互不相同，别混读**（用哪个数取决于你要问什么）：
  - **8007**（MATH 1998 / CHE 2935 / BIO 2054 / PHY 1020）= **块库的现行存量**（我实测：把 `extracted_chunks.jsonl` 的每一块按 `rel_path` 归到这四本计数）——回答「今天库里有多少块」。
  - **1472 / 551 / 313 / 125**（MATH / PHYSICS / CHEMISTRY / BIOLOGY，写在 `extraction_state.csv` 那四行的 `output_ref` 里，见 4.5）= 该行**建行那次运行「新追加」的块数**（代码原文：`output_ref = f"视觉转写 {added} 块（…未判定）"`，`store_53_transcripts.py:206`）——回答「建行当时这一本追加了多少块」；该脚本「已有行不动」，所以这四个数**不随块库继续增长而更新**，是 2026-09-27 00:46:26 的快照。
  - **626 / 2725 / 2042**（材料给的 `points`，见 3.4）= 那一次 store 运行**读到的行数**（代码：`points: len(lines)`，`:213`），既不是块数也不是页数——回答「那次运行读了多少行」。
  - 三者的差额从哪来，我**没有逐条对账、不解释差数**。能说的只有机制：块库文件 mtime（09-27 23:16:35）晚于那四行的建行时间（09-27 00:46:26），说明**建行之后块库还被追加过**——所以「8007 比 1472/551/313/125 大」方向上是说得通的；具体差多少、差在哪几本，我没有核。

---

## 二、审计判出什么、重转了什么

### 2.1 审计的规模与判决（两个口径不能相减）

- **66 个片**（材料；我实测 `tools/kb_coverage/tables/audit_slices/` = 66 份裁定文件：MATH 40 / PHYSICS 15 / CHEMISTRY 10 / BIOLOGY 1）。这 66 个文件的 mtime 落在 **2026-09-26 11:05:19–14:34:15**（我读 mtime 的最早/最晚），即**审计本身不是本轮做的**。
- **片是什么、覆盖到哪**：片 = 转写库里**当时存在的** 14 页网格区间文件（`range_*.jsonl`）；脚本对「已有片文件的区间」不再重审（增量），审计时还会跳过试点页。所以 **66 片覆盖 906 个页次（我实测）≠ 四册 1205 页**，且按科很不均（BIOLOGY 只有 1 片）。
- **把片原件按页摊开**（我实测：296 行裁定，展开成 906 个页次）：**ACCEPT 731 页次 / RETRANSCRIBE 170 页次 / UNCERTAIN 5 页次**。「行 → 页次」的展开规则见 1.2 那条（`pages` 支持 `"15"` / `"15-28"` / `"15,17"` 三写法；这里是**不去重**的页次计数）。
- **收拢后的裁定表不是片原件的简单汇总，两个数不能相减**：`transcript_audits.csv` 现在 **658 行**（我实测：ACCEPT 621 / RETRANSCRIBE 35 / UNCERTAIN 2；mtime **23:44:38**），而「页次」与「行」**不是同一单位**——收拢工具把两类东西并进一张表（审计片的行 + 补齐页的清点行，后者一页一行，见 `tools/kb_coverage/collect_audits.py` 的 docstring），并有归并规则（ACCEPT 覆盖 RETRANSCRIBE、同判决取清点数更大的一份）。**我没有做逐页对账，不声称 906 与 658 能换算。**
- 这三档判决的当前页数（我实测账本）：ACCEPT 1168 / RETRANSCRIBE 35 / UNCERTAIN 2。
- **「658 行」与「账本 1205 行」怎么对上**（我实算，读者可复算）：把这 658 行按 1.2 的规则展开并**去重**后，**恰好覆盖四册 1205 页、没有一页出现两次**，逐页结果是 ACCEPT 1168 页 / RETRANSCRIBE 35 页 / UNCERTAIN 2 页——与账本的 1205 行**一页对一页**。所以：账本的 `verdict` 是按 (学科, 页) 从这张表取的（`tools/kb_coverage/transcription_ledger.py:126` 的 `audit_map` → `:232` 的 `build_rows`），**取不到就是空 verdict，不存在「默认 ACCEPT」**；本轮也不存在这样的页（1205 页全部被裁定表覆盖）。账本 `ACCEPT 1168` 因此就是**裁定表里 ACCEPT 覆盖的 1168 页**，不是由行数或别的什么推出来的。

### 2.2 本轮重转：35 页，全部重转，页级机械门 35/35 过

- **为什么会有这批队列**：上一轮收口后在账本上剩 35 页 fail，但它们**既不在队列里、也没人重转**——因为此前只有「审计判 RETRANSCRIBE」的页有进队列的通路，机械门判坏的页没有。提交 `d3ddfbf4`（09-27 23:47:52）新增 `tools/kb_coverage/queue_defective_pages.py` 补上这条通路，并按提交信息记下这 35 页的成因：**控制字符 26 / 非法转义 4 / `$` 不成对 3 / 计数闸 2**（26+4+3+2 = 35）。
- **队列 35 页**（我实测 `retranscribe_queue.csv`，mtime 23:48:06）：**CHEMISTRY p217、p51；MATH p22、p378、p391、p460、p522、p527、p528、p544；PHYSICS p5、p10、p12、p13、p77、p80、p82、p83、p84、p87、p88、p90、p96、p97、p101、p105、p106、p107、p108、p110、p111、p120、p157、p195、p196**（CHE 2 / MATH 8 / PHY 25）。队列 reason 列的原文形态是「机械门判坏：control_char_damage / invalid_escape / dollar_unbalanced」与「机械门判坏：清点数远超写出量（疑似整块漏）」。
  - 其中 **PHYSICS p82 / p120 / p195** 在上一版报告里是 UNCERTAIN（5 页那批），本轮已成 RETRANSCRIBE 并重转（我实测：三页在队列里、裁定表里是 RETRANSCRIBE、稿在 00:06 前后被重写）。
- **重转发生了什么**（我实测）：这 35 页在补齐目录的稿**全部被重写，mtime 落在 23:52:40–00:18:58**（都晚于裁定表 mtime 23:44:38，即「稿比判决新」）；**没有一页缺页产物**。（mtime 用法②：这里只读**同一批文件的相对先后**；而且「稿比判决新」不是我自造的判据——撤回工具自己就是这么判的：`tools/kb_coverage/refresh_verdicts.py:68` 用 `f.stat().st_mtime <= audit_mtime` 把稿不新的页排除掉。）
- **重转后过闸 35/35**（材料）——**这条我复核成立**，但要说清是哪把尺子：
  - 页级机械门：我复跑 `PYTHONPATH=tools python tools/kb_coverage/grade_pilot.py --dir knowledge-production/2027-53-fill` → 退出码 **0**，末行「**过闸 504／504 页**」；补齐目录自己的快照 `2027-53-fill/grade.json`（mtime 00:19:31）也写着 `pages 504 / passed 504 / failed 0`，这 35 页在快照里 **gate 全为 pass**。
  - 我对这 35 页现稿另跑了一次控制字符扫描（`re.compile(r'[\x00-\x08\x0b\x0c\x0e-\x1f]')`）：**0 处命中**——即判它们重转的那类残迹确实没了。
- **但「35/35 过闸」不能读成「这 35 页已经复核干净」**：本轮**没有在重转之后再派一次换人读图审计**（证据：审计片 mtime 仍停在 09-26 11:05–14:34；裁定表 mtime 23:44:38 早于重转；撤回工具自己给这次撤回准备的依据措辞就是「重转后机械门复判通过（稿晚于裁定表；**未再换人审计**）」，见 `tools/kb_coverage/refresh_verdicts.py:39` 的 `EVIDENCE` 常量）。**35 页的实质复核是缺的**，已列入第四节。

### 2.3 审计查出的典型缺陷类别（跨轮，供理解判据）

- **控制字符替换反斜杠**：审计用控制字符扫描在 847 页语料里查出 **213 处 / 42 页**（`\varphi` → `0x0B+arphi` 这类「命令首字符被替换成控制字符」），当时门的四类判据**都看不见它** → 之后补了 `control_char_damage` 判据（提交 `71505ad9`，09-26 13:43；我核了该提交的信息：`tools/kb_build/gate.py` 新增该判据与一条新的 boundary 残迹门项）。
- 这类残迹**本轮被清掉了一批**：35 页队列里 26 页是这一类（见 2.2），重转后现稿扫描 0 处。**但「213 处 / 42 页」与本轮「35 页」不是同一把尺子**（前者是审计口径 847 页语料里的处数/页数，后者是并入后 1205 页账本的 fail 页数），不划等号。
- **实质缺陷（机械门看不见、只有换人读图能判）**的形态，可从上一轮队列的 reason 列读到例子：CHEMISTRY p10 知识图谱里 Cu(OH)₂ 被写成 Ca(OH)₂；p101 实验浓度 0.5 mol·L⁻¹ 被写成 0.05 mol·L⁻¹；p102 阿伦尼乌斯公式的符号说明整段写错。
  - **这三条例子的出处要说清**：上一轮队列在盘上**已被本轮覆盖**（现存的 `retranscribe_queue.csv` 只有本轮 35 行）。我读的是**我用 `git show d3ddfbf4^:tools/kb_coverage/tables/retranscribe_queue.csv` 取回并落盘的副本** `tmp/_old_queue.csv`——想照字面核对，请对这份副本，不要在盘上找旧队列文件。

---

## 三、三道门（各自结果）

| 门 | 材料给的退出码 | 我这次复跑 | 结论 |
|---|---|---|---|
| 转写质量门 `check_transcripts` | **1** | 退出码 **1**（同一结果） | **未全绿**：35 页报问题（全部由「判决未撤回」引起，见 3.1） |
| `extraction_state --verify` | 0（0 问题） | 退出码 **0** | 账本完整 |
| 成品包门 `kb_build.gate` | **0** | 退出码 **0** | 全绿（**23 项**指标全 0，见 3.3） |

### 3.1 转写质量门（`check_transcripts`）——**退出码 1**

我复跑的原文（命令：`PYTHONPATH=tools python -m kb_coverage.check_transcripts`）：

```
页数 1205｜已转写 1205｜缺失 0
档位： {'审计-轻': 483, '审计-全': 687, '重转-全协议': 35}
闸门： {'pass': 1170, 'fail': 35}

★ 问题 35 条（按类：{'gate': 35}）
```

- **档位三档**（规则在 `tools/kb_coverage/transcription_ledger.py` 的 `compute_plan`）：**审计-轻** = 已有转写、机械信号过且页型是叙述/封面；**审计-全** = 其余已有转写的页；**重转-全协议** = 机械面不过或判决为 RETRANSCRIBE。483 / 687 / 35 就是按这个规则分出来的页数；「重转-全协议 35」与「闸门 fail 35」**按定义相等**，不是两份独立证据。
- **35 页 = 2.2 的队列 35 页**（我按账本逐页列：CHEMISTRY 2 / MATH 8 / PHYSICS 25，与队列逐页一致）。
- **本轮与上一轮的关键差别在「问题类」**：上一版报告记的是 `{'gate': 2, 'defects': 33}`，本轮是 **`{'gate': 35}`**——即这 35 页**已经没有文本残迹类问题**，全部是 `compute_gate` 里那一条「`verdict == RETRANSCRIBE` ⇒ fail」。
- **为什么判决还在**（我读码确认的口径）：账本的 `items_min` / `verdict` 取自裁定表（`tools/kb_coverage/transcription_ledger.py:126` `audit_map` → `:232` `build_rows`），而 `compute_gate` 的第一条硬判据就是「判决是 RETRANSCRIBE ⇒ fail」（`transcription_ledger.py:173`）。这 35 行的判决仍是 RETRANSCRIBE（裁定表 mtime 23:44:38，早于 23:52 起的重转），所以账本在 00:19:34 重建后**仍把这 35 页算 fail**。
- **撤回这一步做了没有：没有**。我以只读方式跑了撤回工具的报告档（`PYTHONPATH=tools python -m kb_coverage.refresh_verdicts`，**未加 `--write`**）：退出码 0，输出「**可撤回判决 34 页**」＋「（未写盘；加 --write 生效）」。也就是说：**34 页满足「重转过 + 机械门复判通过」两条判据，只差落盘**；剩下 1 页是 **MATH p22**（见下）。
- **MATH p22 为什么不在那 34 页里**（我复算）：它在账本行里的 `items_min=143`（这个数取自裁定表），而它现稿里 `编号 0 / 公式 2 / 图 0`（写成量 2），条数闸 `143 > max(2,1)*3` → 不过；它现稿**没有文本残迹**（`field_text_defects` 为空），所以它卡的是**条数口径不一致**，不是残迹。
  - **`max(…,1)` 和 `×3` 的出处**（判据不是本报告写的，是账本代码原文）：`tools/kb_coverage/transcription_ledger.py:190` 先算 `written = 编号 + 公式数 + 图块数`（**复合分母**，行上注释写了为什么用复合分母），`:192` 是 `if claimed > max(written, 1) * 3: return "fail"`——`max(…,1)` 是**写成量为 0 时的分母下限**（不让分母变成 0），`×3` 是代码里的**容差倍数**（代码与注释**没有给这个倍数的推导**，只在注释里用「45 > 9×3 机械上不可能过」举过例）。同段还有一条独立下限 `:197`（`chars < claimed*5` 也不过），MATH p22 卡的**不是**这条——它账本行字数 3029，远高于 143×5。
  - 所以「这个比较本身对不对」属于**判据设计问题**：我只能指出代码就是这么写的；至于 143 与 0 哪个该进 `items_min`，是 4.7 的人工裁定题。同页的页产物 `knowledge-production/2027-53-fill/MATH/p0022.counts.json` 现在写的是 **`items_min:0 / items_numbered:145`**，notes 里逐条论证了为什么把 items_min 记 0（145 行是索引条目、不是「最小式/条」；并列了同类目录页的先例），补齐目录的机械门（读页产物）因此判它 **pass**（`grade.json` 里 MATH p22 = `items_min 0 / items_numbered 145 / gate pass`）。
  - **同一页两个口径打架：裁定表说 143，页产物说 0。** 这两个数哪个作准，我**没有从盘上找到可指认的依据**——这是**人工裁定题**（列进第四节）。
- 其余**不在本轮清单**的 fail 页：**没有**——35 页全部就是本轮队列（上一版报告里「35 页里只有 2 页属于本轮清单」的结论**已随本轮重转失效**，本轮队列 = 全部 fail 页）。

### 3.2 `extraction_state --verify`——0 问题

我复跑（`PYTHONPATH=tools python -m kb_coverage.extraction_state --verify`）退出码 0，输出原文：

```
账本完整：覆盖全部 inventory、状态合法、EXTRACTED 均有 output_ref
```

### 3.3 成品包门——退出码 0（全绿）

- 我复跑 `PYTHONPATH=tools python -m kb_build.gate`：退出码 0，末行「**全部通过：缺陷归零**」，**23 项指标全部为 0**（我数输出里的 `OK` 行 = 23）。
- **门项数说明**：材料与题面（两个词的定义见文首）都写「成品包 **22** 门」，但当前 `gate` 实际产出 **23 项**——多出来的第 23 项是 `boundary 含文本残迹`（输出里的原文：`OK boundary 含文本残迹（非法转义 / $ 不成对 / shell 展开） 0`），由提交 `71505ad9` 加进 `tools/kb_build/gate.py`。所以「22 门」是加判据之前的叫法，现在的门是 **23 项、全绿**。
- **注意**：这门量的是**既有成品知识包**，不含本次扫描转写内容（见第五节）。

### 3.4 并入与入块库

- **并入转写库**（`merge_page_transcripts`）：材料记 **merge 退出码 0**；我**没有复跑**（写盘动作）。侧证（mtime 用法③：只作旁证，不当复跑结果）：`knowledge-production/2027-53-transcripts/` 里最新的片文件 mtime = **2026-09-28 00:19:33**，紧接在账本（00:19:34）之前——与「本轮写过转写库」相符。
- **入块库**（`store_53_transcripts.py`，材料里的 JSON 是 `"dry_run": false`，即**材料称本轮真写过盘**）：材料给的逐科输出为
  - PHYSICS：points 626 / **chunks 0** / lines_skipped 0 / **pages_blocked 533**
  - CHEMISTRY：points 2725 / chunks 0 / lines_skipped 0 / pages_blocked 25
  - BIOLOGY：points 2042 / chunks 0 / lines_skipped 0 / pages_blocked 0
  - **MATH：材料里被截断了**（材料是标准输出的末尾片段），**MATH 的数字材料没有给**，我没有用别的数去补它。
- **这四个字段是什么意思**（定义**读自代码**，不是我猜的）：`points` = 这次运行**读到的行数**（`summary` 里 `points: len(lines)`，`tools/kb_coverage/store_53_transcripts.py:213`）；`chunks` = 这次运行**新追加**到块库的块数（代码里的 `added`：只有按内容 sha256 指纹去重后**库里此前没有**的块才计数，`:177-190`、`:215`）；`lines_skipped` = JSON 解析失败被跳过的行（`:159`）；`pages_blocked` = 被挡下的**行数**——一行只要「无页码」或「该页账本 gate != pass」就被挡（`:165`、`:168`），**字段名里的 pages 有误导性，它其实是按条计**。
  - **`chunks 0` 与 `points 626` 不矛盾**：前者数「这些行切出来的块里**库里还没有的**那些」，后者数「读进来、过了页闸的行」。按代码，某一科 `chunks 0` 的含义就是**这次没有新内容被追加**（该内容此前已按指纹入过库）；材料没写这一层，我**也没有逐行复算** 626 行里每行的块去向。
  - 内容去向：只有新块会 append 进 `extracted_chunks.jsonl`（`:219-221`，且仅在非 dry-run 时）；被挡的行**不写任何存储**。
- **我对这段的只读核对**（不声称材料错，只说我读到了什么）：
  - 块库文件 `extracted_chunks.jsonl` 的 mtime 是 **23:16:35**——**本轮（23:44 以后）没有写过它**（mtime 用法①：这份 mtime 早于本轮，所以我**不能认领**它是本轮写过的），与材料「chunks 全 0」方向一致（0 新增 ⇒ 不需要重写）。
  - 入块库的过滤规则是**只收账本 `gate=pass` 的页**（`tools/kb_coverage/store_53_transcripts.py:29` 的 docstring 明写「只有 `gate=pass` 的页才入块库」，判定在 `:167`，代码不另写一套判据）。而账本现在仍把这 35 页判 fail（3.1）——**机制上，重转后的 35 页内容此刻进不了块库**。
  - 材料记 PHYSICS 533 / CHEMISTRY 25 条被挡。**我能核的只是方向**：这 35 页在账本上是 fail，按 `:167` 的判据它们的行**必然**被挡；但「该挡多少条」**我没有算**（材料没给这 35 页的逐页行数，我也没有逐行复算），所以这个「一致」**只是方向上的、不是数字上的**——数字对不对，我没有核。
  - 这四本在 `extraction_state.csv` 里的行仍是 **CHUNKED**（见第五节），`output_ref` 是「视觉转写 N 块（2027版53扫描件，未判定）」，`updated_at` 停在 **2026-09-27 00:46:26**。**文件 mtime 是 00:19:37（在本轮窗口内）而行的 updated_at 不是，这两件事不矛盾**：该脚本每次运行都会把整张表重写一遍（`:225-229`），所以**文件** mtime 会变；但**已有行按 rel_path 去重、内容不动**（docstring 的「幂等」一节 + `:204` 的 `if rel not in state_seen`），所以这四行的**行内字段没有被改写**，updated_at 仍是建行那次的 00:46:26。我这条结论读的是**行内容**（不是只靠 mtime），00:19:37 那次写入是脚本重写整表造成的。

### 3.5 边界修复与晋升（与扫描线互不依赖）

- **这一节为什么还在报告里**：材料把它列在本轮的机械结果里（「apply/build/diff/promote 四步退出码 0」「已写 `tools/kb_build/tables/boundary_map.csv`（20 行覆盖）」），所以保留；但本报告**只把它记成「材料声称的本轮一段」**——**内容在包里可核，归属不可核**（见下）。
- 我实测（只读）：`boundary_map.csv` = **20 行**（MATH 18 / CHEMISTRY 1 / PHYSICS 1）；四张修复片 `boundary_fixes_01..04.csv` 各 5 行 = 20 行。材料给的 5 条示例与表逐条对得上（字数为我读表算的字符串长度）：
  - MATH 双曲线焦点三角形 139 → **221** 字 〔补全尾〕
  - MATH 抛物线焦点三角形 139 → **174** 字 〔补全尾〕
  - PHYSICS 近代物理知识体系 137 → **163** 字 〔补全尾〕
  - CHEMISTRY 热重法测定物质组成 124 → **125** 字 〔修定界符〕
  - 材料第 1 行被截断的「139 → **180** 字 〔补全尾〕」= **MATH 焦点弦定比分点问题**：`boundary_fix_work_order.csv` 里这一行 `body_len=139`、`action=补全尾（有材料证据）`，`boundary_map.csv` 里同一 slug 的新正文 180 字。
- **成品包里这 20 条与权威表逐条一致（20/20）**：我复跑 `PYTHONPATH=tools python tmp/_pack_probe.py`，退出码 0，输出 `boundary_map rows: 20 pack matches: 20 mismatches: []`。
- **两层要分开读**：**内容在不在包里 —— 可核，成立**；**是不是「本轮」写进包的 —— 不可核**：`boundary_map.csv` 的 mtime 是 **2026-09-25 03:17:46**，成品包 `moe-2025-*.json` 的 mtime 是 **2026-09-25 14:46/15:39**，都**早于**本轮（本轮 23:44 起）。
- **mtime 这三档怎么用（本报告的统一口径，正文用到的地方都标了档号）**：mtime 只能说明「文件最后一次被写过是什么时候」，复制/检出/同步都可能改写它。所以本报告按用途分三档，**强度不同、不能互相借用**：
  - **① 否定方向**（3.5 本节、3.4、第五节）：mtime 早于本轮 ⇒ **我不能认领**该文件是本轮写盘的产物；同样也**不能仅凭 mtime 断言「本轮绝对没碰过它」**。
  - **② 相对先后**（定义「本轮」的那串时间线、以及「稿晚于裁定表」）：只读**同一批产物内部**的先后，不把 mtime 当绝对时间真值；而「稿晚于裁定表」这条不是我自造——它是撤回工具自己的判据（`tools/kb_coverage/refresh_verdicts.py:68`）。
  - **③ 单列侧证**（例如「本轮确实写过转写库」）：标明「只是侧证」，**不与主证据混用**——那一步我没有复跑，就不把它当复跑结果用。
  - 三档能信到什么程度：① 只能否定、③ 只能旁证、② 能在同一批文件内定先后；**三档都不构成「文件内容正确」的证据**。
- 另跑 `PYTHONPATH=tools python -m kb_build.promote --dry-run`（**不写盘**）：退出码 0，四节全绿 —— `gates` / `consistency` / `roundtrip` / `manifest：version→5，压平 0 条`，末行「（dry-run：门全绿，未写盘；将写入 version 5）」。这一节的读数我按输出原文照录，**未展开其内部口径**（要准确定义请读 `tools/kb_build/promote.py`）。

---

## 四、还差什么

1. **未过闸页**：材料记 **0**（未过闸清单 `[]`）——那是**补齐阶段**的页级记录。我按页级判据现跑**补齐目录 504 页**：**过闸 504／504，退出码 0**（`grade_pilot.py`）。**但账本口径仍有 35 页 fail**，全部是本轮队列页（3.1）——**过闸/不过闸取决于量的是页产物还是账本，两者本轮不一致**：
   - **CHEMISTRY p217**：在**本轮派工清单**里（材料记 pass）、页产物也过闸，但账本记 fail/RETRANSCRIBE。它与其余 33 页一样属于「重转了、判决没撤回」那一类（我实测：稿 mtime 00:10:56）。
   - **MATH p22**：账本 fail 的**唯一**原因不是残迹，而是**条数口径不一致**（裁定表 143 vs 页产物 0/145，见 3.1）。**这一页以哪边为准，是人工裁定题**。
2. **撤回判决这一步没做（本轮最大的记账缺口）**：只读复跑显示 **34 页可撤回**（`refresh_verdicts`，未加 `--write`）。工具打印的下一步只有一句原文：**「下一步：apply_transcript_audits --write 重建队列 → 并入 → 账本 → 两道门」**（`tools/kb_coverage/refresh_verdicts.py:116`）——**工具没有点名是哪两道门，本报告不替它补名**。全文出现四把尺子（页级机械门 / 转写质量门 / `extraction_state --verify` / 成品包门），按「账本重建后直接受判决影响」来读，应当是**页级机械门**与**转写质量门**（另两把一个只看状态机、一个只看既有成品包）——**这是我的读法，不是工具的原话**。这一步不做，则：账本 35 页 fail 不消、`check_transcripts` 依旧退出码 1、**重转后的 35 页内容也进不了块库**（3.4 的机制）。
3. **UNCERTAIN 2 页未处理**：**PHYSICS p41、p79**（我实测账本 `verdict=UNCERTAIN`）。两页**都不在重转队列、不在派工清单、在补齐目录没有页产物**（我实测 `p0041.jsonl`/`p0079.jsonl` 不存在），账本里 gate=pass。**保留待人工判断**。
   - 与上一版报告的差别：那一版记 UNCERTAIN 5 页（含 PHYSICS p82/p120/p195）；**这三页本轮已改判 RETRANSCRIBE 并重转**（2.2），所以现在剩 2 页。
4. **审计覆盖不全，且重转后没有二次审计**：
   - 审计覆盖 = **66 片、906 个页次**（我实测），**不等于四册 1205 页**；按科不均（BIOLOGY 只有 1 片）。**哪些页从没被换人审计过，报告没有逐页清单，也没有补审**。
   - 本次重转的 35 页只有**页级机械面**复核（外加我对残迹的复扫），**没有再换人读图复核**（2.2）；撤回工具自己准备的依据措辞就是「未再换人审计」。
5. **未做的判定与 materialize**：四本 PDF 在 `extraction_state.csv` 里状态全部是 **CHUNKED**（「视觉转写 N 块（2027版53扫描件，未判定）」，MATH 1472 / PHYSICS 551 / CHEMISTRY 313 / BIOLOGY 125 块，`updated_at` 2026-09-27 00:46:26），**一条 EXTRACTED 都没有**；**没有做语义判定、没有 materialize、没有进成品包、没有跑学生侧消费链路**。
6. **材料未给 / 我没有复跑的**：
   - 「原 847 页」的按科拆分、以及它与 351 / 358（docstring 口径）/ 5 / 2 的换算关系（1.1）；
   - 入块库逐科输出里 **MATH 一段被截断**（3.4）；
   - **merge、入块库（非 dry-run）、边界 apply/build/diff/promote 四步、队列重建（`queue_defective_pages`）、撤回判决（`refresh_verdicts --write`）、收拢裁定（`collect_audits --write`）、重建账本（`transcription_ledger --write`）我都没有复跑**（都是写盘动作），只有材料一方说法；我复跑的替代/侧证已就地写明（`promote --dry-run`、成品包 20/20 比对、转写覆盖率、逐页复扫、mtime）。
   - 叙述/表格页的完整性只由「清点 + 机械门」保证；审计的做法是**逐页以页图为准做清点 + 抽查**（不是逐字比对）。审计代理是否真的一页页看过图，我**没有独立验证**。
7. **只能由人定的几件事（本报告不替用户决定）**：
   - MATH p22 的条数口径（裁定表 143 还是页产物 0/145）、要不要补重转；
   - 34 页判决撤回由谁在什么时候落盘（落盘后要重跑账本与「两道门」才看得到效果；「两道门」具体指哪两道，见 4.2 的说明——工具原文没点名）；
   - 35 页重转要不要补一次换人读图复核（本轮没有）；
   - UNCERTAIN 2 页先人工判还是先重转、判不了的那页怎么处置；
   - 审计未覆盖页要不要补审、按什么范围补。
   - （给不出估算的：从语义判定 → materialize → 成品包 → 学生侧这一段的工作量与时间，本报告不做估计。）

---

## 五、明确声明：**尚未写入知识库**

- 本轮**没有**把这次扫描件的内容写进知识库。判断依据（我实测）：
  - 四本 PDF 在 `tools/kb_coverage/tables/extraction_state.csv` 里状态全部是 **CHUNKED**（已切块、待判定），**一条 EXTRACTED 都没有**；块库里的块只是切块产物，没有绑定到知识点、没有进成品包。`extraction_state --verify` 退出码 0 只说明这本账完整，**不说明内容过关**。
  - 成品包里那批 `moe-2025-*.json` 的 mtime 停在 **2026-09-25 14:46/15:39**，早于本轮（本轮 23:44 起）——按 3.5 的 mtime 第①档（否定方向）：**我没有任何证据表明本轮写过成品包**。块库文件 mtime 23:16:35、材料记 chunks 全 0，也指向「本轮没有新增入库内容」（MATH 那一科的保留意见见下一条）。
  - 与扫描线沾边的既有包改动只有 **20 条 `boundary` 边界修复**（3.5）：它改的是**既有**知识点的边界字段，**不包含本次扫描转写的任何内容**；而且这批的**写入归属不可核**（表与包都停在 09-25）。
- 所以本轮的产物是：**转写库（账本上 1205 页都有条目；内容完整性的保留意见见 1.3／4.4）+ 补齐目录 504 页页产物（504／504 过闸）+ 重转后的 35 页新稿（页级过闸、残迹 0、**判决未撤回**）+ 队列 35 页 / 裁定表 658 行 / 页级账本 1205 行 + 块库累积 8007 块（三科「本轮 0 新增」有材料支撑，**MATH 一科没有**——见 3.4 的字段说明与 1.3 的三组数字对照；该科只能靠块库文件 mtime 作旁证，用的是第①档的否定用法：早于本轮 ⇒ 不能认领本轮写过）+ 修好边界的知识包（20 条在包，归属不可核）**。扫描件内容的入库（判定 → materialize → 成品包 → 学生侧）是**下一轮**的事。

---

## 附：数据来源与可溯性

**材料给的（本轮运行的机械结果）**：补齐派工 351 页（过闸 351、未过闸 0、另有 5 页此前已过闸未重做）、审计 66 个片、重转队列 35 页（重转后过闸 35/35）、351 条逐页结果、未过闸清单 `[]`、merge 退出码 0、check_transcripts 退出码 1、入块库逐科输出（MATH 段被截断）、extraction_state --verify 问题 0 个、成品包门退出码 0、边界修复四步退出码 0、`boundary_map.csv` 20 行覆盖。

**我这次跑过的命令（含读数）**：

- `PYTHONPATH=tools python -m kb_coverage.check_transcripts` → 退出码 **1**，1205 页 / pass 1170 / fail 35 / 问题 35 条（按类 `{'gate': 35}`）
- `PYTHONPATH=tools python -m kb_coverage.extraction_state --verify` → 退出码 **0**，「账本完整：覆盖全部 inventory、状态合法、EXTRACTED 均有 output_ref」
- `PYTHONPATH=tools python -m kb_build.gate` → 退出码 **0**，《知识库内容质量门》23 项全 0，末行「全部通过：缺陷归零」（`grep -c "^  OK"` = 23）
- `PYTHONPATH=tools python -m kb_build.promote --dry-run` → 退出码 **0**，gates / consistency / roundtrip / manifest（version→5、压平 0 条）全绿（未写盘）
- `PYTHONPATH=tools python tools/kb_coverage/grade_pilot.py --dir knowledge-production/2027-53-fill` → 退出码 **0**，「**过闸 504／504 页**」
- `PYTHONPATH=tools python -m kb_coverage.refresh_verdicts`（**只读报告档，未加 `--write`**）→ 退出码 0，「可撤回判决 **34** 页」＋「（未写盘；加 --write 生效）」
- `PYTHONPATH=tools python tmp/_pack_probe.py` → 退出码 0，`boundary_map rows: 20 pack matches: 20 mismatches: []`
- 逐页/逐表复算（只读）：账本 1205 行的 gate/verdict/plan 计数；**35 页队列与 35 页 fail 的逐页比对（完全一致）**；派工清单与账本对账（350 pass/ACCEPT + CHEMISTRY p217 fail/RETRANSCRIBE）；**裁定表 658 行按页展开（去重后恰好 1205 页、无页重复；ACCEPT 1168 / RETRANSCRIBE 35 / UNCERTAIN 2）**；MATH p22 的条数与残迹复算（`items_min=143 / 编号 0 / 公式 2 / 图 0 / field_text_defects 空`）；35 页现稿的控制字符扫描（**0 处**）；四册 `extraction_state` 行；补齐目录与四批页的集合运算（见 1.2）；`847` 的搜索（命令与范围见 1.1，**非全仓穷尽**）；`git ls-files` 检查报告文件与 `tmp/_pack_probe.py`（两者都**未入版本控制**）。
- **我这次落盘的临时副本**（分析用，不是权威源，也不在版本控制里）：`tmp/_dispatch.json`（材料 351 条清单的解析副本——材料原件不在盘上，见文首）、`tmp/_old_queue.csv`（`git show d3ddfbf4^:…` 取回的上一轮队列 115 页，供 1.2 与 2.3 核对）、`tmp/_fill_pages.json`（补齐目录的页号集合）。
- 读盘（只读）：`build/2027-53-pages/manifest.json`（552/235/215/203 = 1205）；`knowledge-production/2027-53-fill/grade.json`（pages 504 / passed 504 / failed 0；MATH p22 = items_min 0 / items_numbered 145 / pass）；`2027-53-fill/MATH/p0022.counts.json`（items_min 0 / items_numbered 145 及论证）；`2027-53-fill/<科>/pNNNN.jsonl` 的 mtime（队列 35 页与 5 页「此前已完成」的日期）；`tools/kb_coverage/tables/audit_slices/`（66 份；摊开 296 行 = ACCEPT 731 / RETR 170 / UNCERTAIN 5 页次）；`transcript_audits.csv`（658 行 = ACCEPT 621 / RETR 35 / UNCERTAIN 2；mtime 23:44:38）；`retranscribe_queue.csv`（35 行，mtime 23:48:06）；`transcription_pages.csv`（1205 行，gate pass 1170 / fail 35；mtime 00:19:34）；`extracted_chunks.jsonl`（四本累积 8007 块；mtime 23:16:35）；`extraction_state.csv`（四本 CHUNKED；mtime 00:19:37）；`knowledge-production/2027-53-transcripts/` 最新 mtime 00:19:33；`tools/kb_build/tables/boundary_map.csv`（20 行）、`boundary_fixes_0{1..4}.csv`（各 5 行）、`boundary_fix_work_order.csv`（5 条示例的 `body_len` / `action`）；成品包目录 `core/data/src/main/resources/knowledge` 各文件 mtime。
- **从 git 取回的**（本轮盘上已被覆盖）：`git show d3ddfbf4^:tools/kb_coverage/tables/retranscribe_queue.csv` → 上一轮队列 **115 页**（MATH 38 / CHEMISTRY 46 / PHYSICS 31，105 行），用于 1.2 的集合差；`git show d3ddfbf4`（提交信息：35 页 fail 的成因 26/4/3/2、裁定表 658 行 = ACCEPT 621 / RETRANSCRIBE 35 / UNCERTAIN 2）；`git show --stat 71505ad9`（控制字符判据与第 23 项门）。
- 读码（只用于给本文的口径下定义）：`tools/kb_coverage/transcription_ledger.py`（`compute_gate` :163，其中「`verdict == RETRANSCRIBE` ⇒ fail」在 :173；`compute_plan` :212；`audit_map` :126、`build_rows` :232 取裁定表的 items_min/verdict）；`tools/kb_coverage/check_transcripts.py`（六条判据，闸门复用账本）；`tools/kb_coverage/refresh_verdicts.py`（两条撤回判据 :42 `mechanical_ok`、`EVIDENCE` :39、报告档不写盘、`main` :53 打印的下一步）；`tools/kb_coverage/store_53_transcripts.py`（只收 `gate=pass` 页 :29/:167；写 chunks/inventory/state 三个存储；输出字段 `points/chunks/lines_skipped/pages_blocked` :213-215）；`tools/kb_coverage/collect_audits.py:6` 与 `tools/kb_coverage/apply_transcript_audits.py:6`（docstring：847 页 / 358 页的出处、两种来源、同页冲突即拒绝）；`tools/kb_build/promote.py:214`（`--dry-run` 不写盘）；`tools/kb_build/gate.py`（各项判据，含 `boundary 含文本残迹` :451）。
- **本次没有复跑、只有材料一方说法的**：`merge_page_transcripts`、`store_53_transcripts`（非 dry-run）、边界 `apply/build/diff/promote` 四步、`queue_defective_pages`、`refresh_verdicts --write`、`collect_audits --write`、`transcription_ledger --write`——都是**写盘动作**，我**一律没有执行**。

**术语（本报告用到的）**：

- **页级机械门**：页产物内可机械复算的闸门（截断 / 过短 / 条数闸 / 文本残迹 / `$` 成对 / 非法转义），判据只有一份实现（`grade_pilot.py` 复用 `transcription_ledger.compute_gate` 与 `kb_build.gate.field_text_defects`）。它**不判内容对不对**。
- **片 / range 片**：转写库里的一个 14 页区间文件；审计一个片出一个裁定文件（`audit_slices/audit_<学科>-<区间>.csv`）。
- **清点 / `items_min`**：转写前先数「这一页有几个最小式/条」，这个数进账本、当条数闸的分母。
- **档位（审计-轻 / 审计-全 / 重转-全协议）**：一页走哪种审计或重做路径，规则见 `transcription_ledger.compute_plan`。
- **重转（RETRANSCRIBE）**：判决为「有实质缺陷、整页重做」；重转队列就是要重做的名单。
- **撤回旧判决**：重转成功后把该页的 RETRANSCRIBE **改写**成 ACCEPT（依据写进 evidence），由 `refresh_verdicts.py --write` 做；判据是「稿晚于裁定表 + 机械门复判通过」，**不含**再次换人审计。
- **UNCERTAIN**：审计「拿不准」的判决，保留待人工判断。
- **CHUNKED / EXTRACTED / materialize**：状态机两档——CHUNKED = 已切块、待语义判定；EXTRACTED = 已判定并入库。materialize 是「把块绑定到知识点、写进成品包」那一步；本轮没做。

---

## 附：收口终态（2026-09-28 复算，主循环亲测）

本节由主循环在最后一轮收口后**逐条重跑**得到（不是转述某一轮运行的自报）。上面正文里的数字是
各轮运行写报告时的快照，以下终态为准：

| 项 | 终态 | 复算命令 |
|---|---|---|
| 转写完成 | **1205 / 1205 页**（缺失 0；数学 552、物理 203、化学 235、生物 215） | `transcription_ledger --write` |
| 页级机械门 | **1205 / 1205 过闸** | `check_transcripts`（输出「全部通过」） |
| 入块库 | 累计 **6,296 块**（本轮新增 867，四科 `pages_blocked` 均为 0） | `store_53_transcripts.py` |
| 提取状态账本 | **0 问题** | `extraction_state.verify()` |
| 成品包 | **23 门全绿**（含新增 `boundary_text_defect`） | `kb_build.gate` |

### 收口过程中发现并修复的编排缺陷（都不是数据/模型问题，逐条留了判据与用例）

1. **账本每页只取最后一条记录**（一页有 5–15 块）→ 字/条中位数掉到 3.4、**322 页假 fail**、
   入块库**错拦 2500+ 条记录**。修成同页拼接；再测 pass 1056 → 1170。
2. **S5 按"机械门已过"跳过队列页**——审计判的是机械门看不见的实质缺陷，115 页里 114 页机械面本来就过，
   于是 **104 页被静默跳过**。修成"队列即名单"。
3. **重转成功后不撤旧判决**（判了 RETRANSCRIBE 就永远 fail、入库永远拦）→ 新增 `refresh_verdicts`；
   时间锚点取**队列文件**（派工时刻），因为裁定表会在派工前被重写。
4. **"机械门判坏"的页没有进队列的通路**（只有审计判决有）→ 新增 `queue_defective_pages`，
   按类别写 RETRANSCRIBE（控制字符 26 / 非法转义 4 / `$` 不成对 3 / 计数闸 2）。
5. **判据错两处**：目录页的结构性误判（分母加入「索引行」`条目名……473`；MATH p22 复判 143 ≤ 147×3 通过）；
   以及此前的复合分母（编号+公式+图块）。
6. **并入被一行坏 JSON 带倒** → 并入器改宽容 + 新增 `fix_jsonl_escapes` 自愈（只双写非法的那个反斜杠）。
7. **`report()` 256 条上限**把整轮判死（曾用它逐页打进度）→ 改按批/按科聚合。

### 仍未覆盖（如实）

- 叙述页的完整性只由「清点 + 机械门」保证，**没有逐页换人对账**（847 页审计是清点式抽查）；
- **2 页 UNCERTAIN** 未重转，保留待人工判断；
- **没有写知识库**：停在块库/CHUNKED，未判定、未 materialize、未跑学生侧链路。
