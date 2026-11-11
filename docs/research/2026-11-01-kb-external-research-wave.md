# KB 外部研究波（2026-11-01）：生产级做法与可落地清单

> 性质：外部研究（WebFetch/搜索，≥2 独立来源交叉；单一来源标「待验证」）。用途：为批次 32（五波 ~150 段）定任务。
> 本仓事实基线：v26 包（65,021 材料 / 3,280 节点 / 40,841 别名）、金标 129 冻结、融合 117/129、八门 7/8（judgment_drift 518 红）、首启导入 51.6s、API23/34/36-16KB 矩阵、遥测仅 logcat。

## 一、检索质量（对应 12 条 MISS：9 排序 / 2 融合封顶 / 1 词汇）

| # | 做法 | 外部依据 | 判定 | 本仓落点 |
|---|---|---|---|---|
| L1 | **词面腿 BM25 式重排**：按 feature 的 df 加权（IDF）+ 长度归一，替代 COUNT(DISTINCT) 的「特征多就霸榜」 | SQLite FTS5 bm25 文档；Manning IR 教材（`dl.acm.org/doi/10.1561/1500000019`） | **采纳为臂**（k1=1.2/b=0.75 用文档默认，不网格搜索） | `ProblemOrganizationDao.kt` 取数 + `RoomKnowledgeBaseStore` Kotlin 重排；导入期计算 df |
| L2 | **材料标题+摘要级索引、节点聚合（max）**：把 65,021 条材料的标题/摘要作为节点检索特征 | Anthropic contextual retrieval（BM25+contextual chunk 使 top-20 失败率降 49%）；ColBERT/DPR 的 passage 级粒度 | **采纳为臂**（唯一对「词汇缺口」有直接内容证据：`等和线` 的材料摘要已含「x+y…P…BC…平行…等和线」） | `KnowledgeSearchIndexBuilder`（导入期建特征）+ store 聚合；须预注册特征配额与导入时延预算 |
| L3 | **查询类型路由（概念/方法/计算）→ 材料类型加权** | 中文数学知识点分类已验（atmk；ACL 2002 中文线索词）；「分类→材料类型加权」的收益无外部证据 | **采纳为低风险小臂**（非命中查询零变化；线索表与常数预注册） | `KnowledgeSearchFeatureExtractor.fromQuestion` + DAO |
| L4 | 候选并集 + RRF | OpenSearch 六数据集：RRF 比分数融合低 3.86%（NDCG@10）；Bruch 等证明凸组合优于 RRF | **不采纳**（RRF 已实测证伪；并集已零增益） | — |
| L5 | 轻量 cross-encoder（top-32） | bge-reranker-base 278M ≈ 280MB int8；MobileRAG 指端侧瓶颈是内存/功耗 | **暂缓**（p95 77ms 预算不可行） | — |
| — | HyDE / multi-query / LLM 改写 | HyDE 仅零样本有生成模型时有效；未找到中文教育题面直接证据 | **不采纳**（端侧无生成模型） | — |

**评估纪律强化（研究结论）**：129 条已是测试集，不能再当开发集——新臂一律预注册、一次性判读；建议未来按章预留 6 章 never-inspect；负向集用同科硬负例（非随机负例）；配对 bootstrap 20k/seed0 保留，逐章只作守卫不宣称显著（Smucker 2007；query 少时分歧大）。

## 二、内容质量与溯源

| # | 做法 | 外部依据 | 本仓落点 |
|---|---|---|---|
| C1 | **sourceUri 占位修复 + 导入契约门**：49 源/206 行/207 处 example.edu → 真实 URI 或显式「来源不明（占位）」类；契约层拒收占位域/空 license | OpenStax 全库 CC BY-NC-SA；Khan CC BY-NC-SA（网站广告=商业）；《生成式AI服务管理办法》第 4/7 条；《AI 生成合成内容标识办法》第 5 条（元数据隐式标识） | `source-register` JSON + `KnowledgeBaseImportContract` + 审计脚本 |
| C2 | **近重复三段式去重**：精确指纹 → char 3-gram MinHash LSH（128 perm，阈值 0.65–0.75，**同 node 才聚类**）→ 嵌入余弦 0.90–0.95 | RefinedWeb（5-gram/9000 hashes/b=20×r=450）；datatrove（14×8，阈值 ~0.72）；SemDeDup | `dedupe_judged_materials.py`（现仅五元组精确判重；v27 已误伤锚点，须锚点保护） |
| C3 | **退化摘要回炉 + 标题归一**：summary ≤8 字/复述标题 → 用正文首句重写；>24 字/含标点标题 → 「对象+限定」短式（语义等价审查） | FactCC（摘要一致性判定）；本仓 audit_quality 残句正则 | 判定表 + `rebuild_aliases` 链路 |
| C4 | **合成内容抽检门（校准集）**：300–500 条分层金标，10% 双标，报 Cohen's κ（<0.61 须改 rubric）；每波抽 5–10% | MT-Bench（GPT-4 评委与人类一致 >80%）；κ 分档（PMC3900052）；158 条需 95%CI±5%≈384 条样本 | `tools/kb_coverage/calibration/` + 复核 CSV；reason code 绑定冻结 rubric |
| C5 | **薄料/选修显式化**：可教单元最小自足集（概念=定义+条件+反例；方法=特征+步骤+边界；误区=错法+成因+纠正）；不足标 THIN；235 选修标「无源」不伪造来源 | ISO/IEC 25012（分维验收）；Squirrel AI 细粒度（单一厂商，待验证） | coverage JSON + build 门 + `docs/knowledge-source-governance.md` |
| C6 | **A 层止损**：60,512 块维持封层；定向抽取 2 轮仅 5 条材料 → 停；NOT_FOUND 记 reason+来源可得性 | Dagster 重试只救失败不救无源；Temporal failed-only 重开 | 预注册 + `node-material-gaps` |

## 三、交付体验与运维工程

| # | 做法 | 外部依据 | 本仓落点 |
|---|---|---|---|
| E1 | **渐进导入分层**：phase1 提交节点/材料即可查，phase2 特征索引后台补齐；状态落 `ContentInstallState`，查询按 completeness 降级 | Play vitals（冷启 ≥5s 过慢）；NN/g（1s/10s 阈值）；51.6s 远超 10s，必须「先可交互 + 进度」 | `BundledKnowledgeBaseInstaller` + 新增 completeness 状态 |
| E2 | **遥测指标面**：本地环形缓冲 + 直方图桶（阶段耗时/首查时延/p50/p95/p99/空结果率/覆盖率）；默认关闭、同意后上报 | SRE 四信号；Firebase 采集开关语义 | 新建 telemetry 层 + 聚合脚本 |
| E3 | **并发读压测**：WAL 多读单写，16 线程并发查询 + p95 断言 | SQLite `whentouse`；Android 单实例 p95 减半 | `core/database` androidTest |
| E4 | **备份恢复演练**：WAL checkpoint、-wal/-shm、恢复后索引重建核验 | Android WAL/NORMAL；SQLite backup API | 备份/恢复仪器化 |
| E5 | **16KB native 对齐**：真正的风险点是 LiteRT `libLiteRt.so` 的 16KB 对齐（非 SQLite） | Android 16KB 指南（Play 2027-02-01 强制） | `core/data/build.gradle.kts` + 对齐核验 |
| E6 | **预置数据库**：收益仅覆盖全新安装、70+ 迁移耦合、资产 59MB | Room prepopulate 官方；RoomAssetHelper 停滞 | **降为备选**，本批不做 |
| E7 | **块级判定账本 + claim-check 载荷**：`pending→judged/failed` + attempts/last_error/payload_path/content_sha；每块落 JSON+sha，上下文只传路径 | LangGraph checkpoint；Temporal 幂等键/51,200 events 上限；Azure Claim Check | `tools/kb_coverage/judge_ledger.py` + `judgment-verdicts/` |

## 四、批次 32 五波与任务映射（约 150 段 / 45 项任务）

- **32A 检索质量波**：judgment_drift 518 归因修复 → L1/L2/L3 三臂（各自预注册+双路+三规则）→ 最终基线 → 验收 v1.5。
- **32B 内容质量波**：C1 sourceUri+契约门、C2 MinHash 去重（锚点保护）、C3 摘要/标题归一、C4 校准集 κ、C5 薄料/选修显式、C6 A 层止损 → v28 合并测量。
- **32C 交付体验波**：render v3 第二材料槽+类型分流、每章交付审计、全库渲染抽样、预算压力、E1 渐进导入（设计+实现+设备验证）、升级链。
- **32D 工程运维波**：E2 指标面、E3 并发压测、E4 备份恢复、E5 16KB 对齐、E7 判定账本+claim-check、runbook v2、验收 v1.5 终章。

**未验证/待验证清单**：HyDE 中文教育场景、位置偏置对 judge 的影响幅度、SimHash 阈值二手来源、16KB .so 对齐现状、预置 DB 的 createFromAsset 复制语义。
