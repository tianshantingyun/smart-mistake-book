# S18 尾 · FTS 搜索路径修正 完成记录（2026-10-06/07）

> 依据：`docs/research/2026-10-06-s18-fts-search-plan.md`（提交 `59fd8a32`，含 §7 实施期口径裁定）、
> 诊断文档 `docs/research/2026-10-05-s18-count-path-prefinding.md`（3C 后半批 1 移交）、
> 排序报告 `docs/research/2026-10-06-s18-fts-ranking-report.md`。
> 范围：**计数/分面路径驱动修正**（零 schema，语义等价）+ **宽命中排序三档量化与条件等价重写**。
> 执行：动态工作流两次运行（首跑在批 2 先行条件处因基础设施 world-read 被拒 errored → 脚本加固
> 「世界读取重试→降级为无法核验、门不抛断」→ Amend 复用批 1 缓存续跑；续跑在综合门 DB 全套 halted
> ——已知边际门——由协调方重跑取证并补完 app 全套 + R8 与收尾）。

## 1. 交付与提交

| 批 | 提交 | 内容 |
|---|---|---|
| 计划 | `59fd8a32` | 实施计划（含 §7 实施期口径裁定：EQP 判据字面→意图迁移、夹具等价替换、设备无 FTS5 → bm25 登记待裁定） |
| 批 1 | `d1a0fbf3` | **计数与三个 FTS 分面查询改 `library_search_fts CROSS JOIN …` 顺序锁**（FTS 最左；`WHERE`/`MATCH`/全部筛选与绑定表达式**逐字不变**，仅 join 关键字与顺序；Room 编译期接受；零 schema）。等价主门（13 组筛选矩阵 × count+三分面 × 三方逐列逐行）+ EQP 结构门（含改前旧计划红样例）+ 计时门写进 `LibraryFtsCountPathInstrumentedTest`；JVM 冻结副本契约 `LibraryFtsCountPathQueryCopyContractTest` |
| 批 2 | `3e494705` | **宽命中排序三档量化 + 零 schema 等价重写**：把 9 个列命中标志 + 3 个额外 token 标志从 ORDER BY 的**逐行相关 EXISTS** 改为**集合级命中集 LEFT JOIN**（`MATERIALIZE hit_*` + docid 自动覆盖索引）；**排序语义一字未改**。同序主门（改前 SQL 冻结副本逐列逐行，14 组 + 并列键/权重小夹具）+ EQP 结构门（红样例）+ 计时档位门写进 `LibraryFtsRankingPathInstrumentedTest`；JVM 冻结副本守卫 `LibraryFtsRankingQueryContractTest`；报告 `2026-10-06-s18-fts-ranking-report.md` |
| 收口修复轮 | `504c344a` | 复核 12 条 low 处置：排序守卫钉 9 权重/筛选片段/二级键（双向红证）、计数门 EQP 加合取④（`VIRTUAL TABLE INDEX n≠0`）+ 固定 `INDEX 0` 红样例、10k 档补宽松 backstop（P95<2000ms×CI；实测 144ms，≈14×/≈55× 余量）、KDoc 对齐（终轮数字/DAO 排序描述）、报告 §2.2 算术自洽修正与探针类删除复现性说明、计划 §7 口径裁定入稿 |

## 2. 逐条落证

### 2.1 计数与分面路径（批 1）
- **驱动修正**：改后设备计划 = `MATERIALIZE library_catalog`（一次性子程序）→ 主程序 `SCAN library_search_fts
  VIRTUAL TABLE INDEX 11:`（**第一个循环**）→ `SEARCH content USING INTEGER PRIMARY KEY` →
  `SEARCH catalog USING AUTOMATIC PARTIAL COVERING INDEX (problem_revision_id=?)`；改前 = `SCAN catalog`（驱动）
  → 最内层逐行 `SCAN library_search_fts`。四条查询（count + 三分面）同形。
- **性能（1 万行夹具，生产端口口径）**：**100 命中 count P95=102ms**（预算 500ms×CI）、**1 万命中 P95=104ms**；
  同夹具改前 SQL 单次 **14959ms**。两者近同价 → `MATERIALIZE` 为一次性物化，病根（逐行回探）已消灭。
- **等价性**：13 组筛选矩阵 ×（count+三分面）× 三方（生产端口 / 改前 SQL 原文 / 改后 SQL 副本）逐列逐行全等；
  `git diff` 仅 4 个 hunk 全在 FROM/JOIN（27+/15-），`WHERE` 之后片段逐字相等。
- **设备基线（仓内留档）**：framework SQLite **3.39.2**；**无 FTS5 模块**（`fts5=false` 探针）→ 项目走 FTS4。

### 2.2 宽命中排序（批 2）
- **三档量化（改前基线）**：100 命中 P95=146ms / 1k=784ms / **10k=19208ms** / 并发均值 10574ms（→ 千级超预算，
  触发条件成立；改前基线来自临时探针类，已删除、复现性受限——报告 §2.1 已如实标注）。
- **等价重写**：集合级命中集 LEFT JOIN（`hit_docid IS NOT NULL` 取代逐行 `EXISTS`）；排序公式、9 个权重、
  筛选片段、二级排序键**逐字未改**；`hit_docid` 唯一（FTS 拒绝重复 docid）→ LEFT JOIN 不放大行数。
- **改后**：100=85ms / 1k=92ms / **10k=144ms** / 并发均值 896ms（10k 档 ≈133× 提速）。
- **同序主门**：改前 SQL 冻结副本逐列逐行（14 组：100/1k × 两排序 × offset + subject + 组合筛选；10k × offset）
  + 并列键/列权重小夹具 + 空结果反空转守卫；全部绿。
- **未擅改语义**：换 `bm25`/`rank`（FTS5 专有）属**换库 + 用户可见排序语义变更** → 登记待用户裁定。

## 3. 门

| 门 | 结果 |
|---|---|
| 批 1 门 | 全量 JVM `--rerun-tasks` + DB 仪器化（计数类/迁移类/分页类/PerformanceGateTest）**全绿（第 1 轮）** |
| 批 2 门 | 全量 JVM `--rerun-tasks` + DB 仪器化（排序类/PerformanceGateTest/计数类/两值排序/分页类）**全绿（第 1 轮）** |
| 收口修复轮门 | 全量 JVM **243 任务全绿**；三类仪器化 **13/0/0**（2026-10-07T00:20:56Z） |
| 综合门 · 全量 JVM | **exit=0** |
| 综合门 · DB 全套 | 首跑 **exit=1**：唯一失败 = **已知边际门** `LibraryCatalogScalePerformanceInstrumentedTest.catalogFacetsPlanAndLatency`（SECTION facet p95=**220ms**，样本 204–223ms vs 本地预算 200ms；走 catalog 侧 facet，与本批 FTS 改写无关）→ 单独重跑绿 → **全量重跑 230/0/0**（2026-10-07T00:50:44Z） |
| 综合门 · app 全套 | 设备清场后（force-stop + 无进程确认）**53/0/0**（00:55:06Z） |
| 综合门 · R8 冒烟 | `:app:assembleLocalFirstRelease` ✓ → 装机 ✓ → monkey 启动 ✓（包名 `com.tingyun.smartmistakebook.localfirst`）→ PID **9019** → logcat 四类崩溃标记 **0** → 截图正常 |

## 4. 独立复核登记

- **批 1 / 批 2 结论：均 approve-with-notes**（批 1 的完整 note 明细未随提交留档；批 2 见下）。
- **批 2 复核（12 条 low，全部已处置）**：修复轮收掉 4 条（守卫钉权重、计数门补非零索引合取 + INDEX 0 红样例、
  10k backstop、KDoc 对齐）；登记 7 条（50k 规模未覆盖、设备原始证据未留仓、排序 EQP 门版本脆弱、P95 两套口径
  并存、改前基线探针类已删、额外 token 跨代形态只冻结不变量、新合取对其它 SQLite 版本的敏感性）；
  **已核无发现**：驱动修正成立（diff 逐行）、等价性非空转（13 组矩阵、期望计数独立推导）、门未被删/放宽、
  Room/KSP 生成文本与门测副本同源、批 2 等价性（复核另跑本地 SQLite 3.50.4 48 例 0 不一致）、冻结副本忠实、
  红样例可证伪、并列键覆盖、bm25 未擅改。
- **口径裁定（计划 §7）**：EQP 判据字面→意图迁移（设备 3.39.2 不展平该视图；新判据对旧计划仍红）——属口径迁移
  非放宽门。

## 5. 遗留与 UNVERIFIED

1. **换 `bm25`/`rank` 待用户裁定**：设备 framework SQLite 无 FTS5 模块，属换库 + 排序语义变更，未擅动。
   - 2026-10-08 K1 更新：**已裁：不换**（F6，2026-10-03/04；依据与两条重开条件见 `docs/kb-outstanding-research-2026-10-02.md:1193-1196`）。
2. **50k 规模未覆盖**：本批量化在 10k 夹具（count/facet 一次 `MATERIALIZE` 成本随目录行数线性）；50k 下
   count/facet 计时未测。
   - 2026-10-08 K1 更新：**50k 已补测**——count/ranking 两记录类（100 命中仍为门，1 万/5 万命中与排序各档只记录），读数与建库成本见 `docs/research/2026-10-08-k1-completion-record.md` §3.2。
3. **设备原始证据未留仓**：connected XML 无 system-out，EQP 原文与计时 samples 仅存于报告/KDoc 文本。
4. **排序 EQP 门对计划形态较严**（恰好 12 条 `MATERIALIZE hit_*` + `AUTOMATIC COVERING INDEX`）——换 API/SQLite
   版本可能假红（CI 同镜像暂无风险）；计数门合取④同理（未在 3.39.2 之外实测）。
5. **P95 口径两套并存**（新门 `((n-1)*95)/100` 略严于 `PerformanceGateTest` 的 `(n*0.95).toInt()`）。
6. **改前基线复现性**：宽命中排序的改前数字来自已删除的临时探针类（不可复跑）；微探针成本分解算术不自洽
   （已改为"仅量级参考"）。
7. **KDoc/报告数字轮次**：计数门 KDoc 已对齐终轮（102/104/14959ms）。
8. **环境口径**：全部数字来自 2 核模拟器 debug 构建 + framework SQLite 3.39.2（未 ANALYZE），`ciSlowRunner ×4`；
   不当作真机/release 性能背书。
9. **KF-31 仍未启**（数据前置）；**3D 硬门仍挡阶段 5**（D-0 未实现 / D-1 无 ≥0.95 / D-2·D-3 未做）。
10. **台账折入待办**：`docs/agent-first-refactor-decisions-2026-09-23.md` 为他线在飞文件，未改动；待折入
    3C 前半 / 3C 后半 / S18 尾三条完成记录与阶段 5 挂起记录。
11. **工作树他线在飞物**（`docs/*`、`tools/*`、`%TEMP%/` 字面量目录等）——提交一律显式清单（本批全程如此）。

## 6. 剩余风险

- **FTS 搜索路径的性能结论绑定设备 SQLite 规划器行为**（3.39.2 + 自动部分覆盖索引）；升级 bundled SQLite 或换
  API 时，EQP 门可能假红/需重新审视计划。
- **宽命中排序的新形态依赖 LEFT JOIN 子查询不被展平**（当前 3.39.2 不展平；若未来展平，门会暴露形态漂移）。
- **bm25 路线若被采纳**，需评估换库成本（bundled SQLite / FTS5）与排序行为变化对用户的影响。
- 边际门（SECTION facet 200ms 本地预算）在机器负载高时会红——3C 后半已登记，本批再次实证（首跑 220ms → 安静
  机器绿）；CI ×4=800ms 为决定轮。
