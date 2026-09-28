# Stage-7 报告：验证债与评测债收口（2026-09-28）

> **性质**：用户裁定"先收口验证债"后的第一批。四条裁定见 `docs/agent-first-refactor-decisions-2026-09-23.md`
> 之外的第六份 Stage 报告；本报告只记**本轮实跑/实查**，未验证项一律标 UNVERIFIED 并写触发条件。
>
> **一句话结论**：知识库架构重构的**结构部分**（R1/R2/R4a）确已落地，但"完成"此前**没有凭据**——
> CI 的 KB 检验长期红、`docs/status.md`（唯一随仓库的逐门读数）被 `if: success()` 冻在 2026-09-21。
> 本轮把这条链修到**能出凭据**：两处真缺陷已修（CI 缺 PDF 后端、路径守卫的跨平台缺陷），
> 第三条（已提交权威表 5 行坏 slug）与第四条（artifact 依赖测试在干净检出上结构性不可过）
> 归因清楚、属他线/他设计，未动；本地重生成的 status.md 给出 **23/23 逐门读数 + 权威表 sha**。
> 同时补上三条真机腿（换件闭环）与检索不确定性工具（**"0.7444 过没过 0.75"在本设计下不可分辨**）。

---

## 1. WP1 门记录口径：CI 为什么红、status.md 为什么死

**证据起点**：`gh run view 36337107924`（本仓库 main 最后一次推送的 CI 运行）——
`check` job 的 `Knowledge build toolchain tests` 步骤 `FAILED (failures=7, errors=27, skipped=1)`；
`instrumented` job 也红（`feature:library` 的 `MistakeExportInstrumentedTest` 断言失败）。
`.github/workflows/android-check.yml` 的 commit-back 条件是 `if: success()` ⇒ **任何一处红都让
`docs/status.md` 不再被写回**。仓库里那份停在 `74626e8` / 2026-09-21，KB 段与 gate 行数实测 **= 0**。

**四条成因（逐条有 CI 日志原文）**：

| # | 成因 | 影响面 | 处置 |
|---|---|---|---|
| ① | 已提交的 `chapter_map.csv` 有 5 行 slug 写成了 name（`ValueError: chapter_map.csv: 5 行的 slug 在知识包中不存在`） | **17 个测试**（凡经 `tables.py:98` 校验的模块连坐） | **未动**：修法已在另一会话工作树里（本地已删那 5 行）、未提交；改它会在共享树上冲突 |
| ② | artifact 依赖测试在干净检出上**结构性不可过**：`test_kb_transcription_ledger.*` / `test_kb_check_transcripts.RealArtifactTest` 要 `scan_render_pages.py` 的 manifest（`SystemExit: 缺 manifest`）；`test_kb_materialize.PlanTest` 要扫描件块清单里的块（产物在 `build/`，按设计不入库） | ~12 个测试 | **未动**：属 kb-scan / 入库线的 fixture 设计；建议按"缺 artifact ⇒ 显式 skip 并写理由"处理（现在的形态是"永久红"，会训练人忽略红） |
| ③ | `pypdf` 未装：`tools/curriculum_coverage/extractor.py` 导入期即要求它，`tests.test_curriculum_coverage` 整个模块加载失败 | 1 个模块（16 用例） | **已修**：CI 加 `pip install pypdf` 一步 |
| ④ | 路径守卫测试用了 `C:/Windows/System32/drivers/etc/hosts`——在 Linux 上退化成**相对路径** ⇒ `FileNotFoundError` 而不是预期的 `ValueError` | 1 个测试 | **已修**：探针改 `tempfile.gettempdir()`，并加"探针确实在仓库外"的自检 |

**口径修复（同批）**：门数分母从写死的 `/22` 改为从 `gate.evaluate()` **现算**（实际 23；
真跑会打印 `23/22`）；`run_kb_checks.py` / `status-template.md` / `check_asset.py` 里 3 处
`22` 字面量一并清掉，改为"以 `gate.evaluate()` 为准"；`gate.py` 里与"12→0"矛盾的陈旧注释订正。
**新增一行读数溯源**：status.md 的 KB 段现在带**权威表 sha256**——因为本轮实测到
"同一张 `chapter_map.csv`，本地读到工作树那一版（绿）、CI 读到已提交那一版（红）"，
读一个聚合数字而不带表的版本，等于不可复核。

**交付**：本地重生成 `docs/status.md`：**23/23 逐门读数**（含 `chapter_uncovered_units`
**首次有记录**，= 0）、`492/492` 工具测试、六张权威表的 sha。
**诚实标注**：它是 `local-manual` 触发的快照，**不是 CI 产物**；CI 的 commit-back 仍被 ①②挡着。

**登记**：`docs/known-defects.md` **KD-27**（含三条成因、修法与 reopen 条件）。

## 2. WP2 转写门：只有归属结论，未清判

`tools/kb_coverage/tables/retranscribe_queue.csv` 36 行（35 页：CHEM 2 / MATH 8 / PHYS 25），
`python -m kb_coverage.check_transcripts` 退出码 1（`2027-53-scan-report.md:111-119`：`pass 1170 / fail 35`），
其中 34 页可撤回但未落盘、MATH p22 两口径打架、PHYS p41/p79 为 UNCERTAIN。
**归属**：证据指向 kb-scan 线（提交信息 `fix(kb-scan): …`、脏文件都在 `tools/kb_coverage/`），
**本轮未动**。两条硬事实：① 这道门**不在** CI 的 23 道门里（`run_kb_checks.py` 只跑
gates/consistency/roundtrip/manifest/dense 五节），所以它红**不影响** status.md；② 按纪律不替
另一条线撤销判决（34 页的 `--write` 与 p22 的口径冲突都需要该线的裁定）。
**下一步（交裁）**：34 页撤回要不要落盘、p22 两口径取哪个、2 页 UNCERTAIN 怎么判。

## 3. WP3 真机腿：三条全过（2026-09-28 换件在设备侧闭环）

触发条件（`compileDebugAndroidTestKotlin` 由红转绿）在 Stage-7 开工第一分钟就满足了，
于是立刻跑（全量输出落 `build/stage7/device-dense-golden.log`）：

| 腿 | 读数 | 与换件前对比 |
|---|---|---|
| `DenseEncoderParityInstrumentedTest` | n=290 **min 0.9995842786898838** / median 0.9997850489425515 / p95 0.9998405938495096，不达标 **0** | **逐位相同**（128 窗口对质量零影响，与"290/290 行逐字节相同"的离线证明一致） |
| `GoldenRetrievalInstrumentedTest` | 主集 **0.7444444（67/90）**、MRR **0.6109259259**、逐章最小 **0.4444**、`denseLegLive=true`、p95 204ms（预算 250）/ p50 78ms（预算 150） | **逐位相同**（含两个 0.4444 弱章） |
| `DenseFirstUseCostInstrumentedTest` | 单条编码 **p50 38ms / p95 52ms**；对拍探针 290 条 **p50 32.3ms**；openEncoder 34ms / firstOrder 1036ms / secondOrder 97ms | 512 窗口件同机同探针 **p50 140–223ms** ⇒ **端侧 ≈4–5.8×**（宿主 4.32–4.42×） |

⇒ 两条记录（`core/data/src/main/assets/dense/README.md` 的验收表、`docs/kb-stage6-report-2026-09-26.md` §9.2）
里此前写的 **UNVERIFIED（外部阻塞）已按实测改写**；回退件仍在（`015b2315…`）。
**仍未解**：端侧线程轴（2/4/8 的真实差值）——本机模拟器在宿主争用下不是可靠仪器（§8.2），未给出结论。

## 4. WP4 金标 v2：规则与工具已落地，**判官一字未动**

**外部依据**（2026-09-28 检索）：2026 检索评测常规 = 金标 **≥100 条** + **配对显著性检验** +
版本化查询与**语料快照** + 先 Recall@K 再看 nDCG/MRR；融合侧 2026 共识是 **RRF 默认**、
分数归一化+线性加权"可调但分布漂移时脆"（我们用 min-max + α=0.5，**留档观察、本轮不改**
——没有任何被证伪的失败指向它）。

**交付**：
1. `docs/kb-golden-v2-protocol.md`：v2 撰写规则（40 条 = 10 新章×3 + 4 弱章×2–3，**写死在生成前**）、
   冻结清单、重锚顺序，以及一条**此前没人点名的连锁**——新查询会波及
   离线 `int8-queries.npy` → 设备对拍 fixture（290 行里含全部查询行）→ `stage3-device-expectation.json`，
   四处 shas 互相钉住必须一趟重生成；**半冻结的判官比不冻结更危险**，故本轮不动判官。
2. `tools/kb_coverage/retrieval_significance.py` + 5 条测试（`tests.test_retrieval_significance`，全过）：
   逐题 bootstrap 与**按章 cluster bootstrap**（金标是"10 章×9 题"整群抽样，按章才是诚实口径），
   以及 A/B 配对检验（按章配对重采样 + 双侧 p）；账本格式不符/题面不在金标集内 ⇒ **直接退出**。
3. **当前 90 条集的实测不确定性**（真机 MISS 账本）：

   | 口径 | 值 |
   |---|---|
   | 主集 Recall@5 | 0.7444（67/90） |
   | 95% CI（逐题 bootstrap） | [0.6556, 0.8333] |
   | **95% CI（按章 cluster）** | **[0.6111, 0.8667]** |

   ⇒ **预注册线 0.75 落在区间内**：此前"预注册线未过"的说法更正为"点估计低于线，
   但 0.0056 的差远小于该设计的噪声带（区间宽 ±0.13），**不可分辨**"。

4. **待办（一次做完，不许半冻）**：40 条草稿已由子代理起草（`build/stage7/golden-v2-draft.json`，
   含"未接触检索结果"声明），合入前逐条过 `docs/kb-golden-v2-protocol.md` §5 检查表，
   然后按 §3 清单一趟生成 npy / fixture / 期望并**先出基线再定线**。

## 5. WP5 小尾巴与登记

- `docs/known-defects.md` 新增三条：**KD-27**（CI 红与 status.md 冻结，见 §1）、
  **KD-28**（`kb_build` 的 9 科白名单判为**接受边界**：四科封顶下无任何可观测失败，
  且该文件在另一会话 in-flight 的 `core/database` 里；reopen 条件写死）、
  **KD-29**（**AI 标识未落**——用户裁定暂不处理、登记为已知边界，明写**不声称合规**；
  外部依据：《人工智能生成合成内容标识办法》2025-09-01 施行）。
- `docs/knowledge-memory-retrieval-design.md` 加"**快照**"标注（正文一字未改），
  并给出当前事实的四处指向；判据用实测 grep 写死。

## 6. 未关的账（诚实清单）

1. **CI 仍红**（①②不在本会话可改范围）⇒ status.md 的 CI commit-back 仍冻结；
   本轮的 status.md 是 `local-manual` 快照。
2. **转写门仍红**（35 页判决未撤回；不在 CI 门内）——需该线/用户裁定。
3. **`instrumented` job 红的另一条**：`feature:library.MistakeExportInstrumentedTest` 断言失败，
   非知识库线，未动。
4. **端侧线程轴未解**（模拟器在争用下不可靠）；**base 档真机数不存在**（已按比值判死不落地）。
5. **金标 v2 未冻结**（§4.4），判官仍是 v1（sha `7c004b76…`）。
6. **安全**：Mimosa 扫描仍未取得完整结论（`scanner_enobufs`）——**不声称项目安全**；
   本轮两处 `random.Random(seed)` 告警已按"可复现性必要设计"就地标注理由，不改。

## 7. 复核方式

```bash
gh run view 36337107924 --json jobs            # ①四条的 CI 出处
python tools/ci/generate_status.py             # ②重生成逐门读数（本地，local-manual）
python -m unittest discover -s tools/tests -t tools   # ③492 用例
python tools/kb_coverage/retrieval_significance.py \
  --misses build/stage7/golden-fused-misses-device-2026-09-28.txt   # ④§4.3 的区间
adb pull /sdcard/Download/golden-fused-misses-*.txt build/          # 真机 MISS 账本（带运行戳）
```
