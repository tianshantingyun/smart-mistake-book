# 智能错题本 · 全量完成总路线（MASTER LINE）

- 日期：2026-09-28
- 最近计划维护：2026-10-25（批次 20：4E 收官 + 五研究线 + beta 准备件——WG-10 三旁路收编独立复核+交付 HTTP transport 旁路观测测试（拒绝=0/授予=1+fail-closed）；WG-02 转写源图证据（CapturedFigureReference 绑定源页/裁切/草稿修订/源 SHA，@EncodeDefault(NEVER) 保旧文档指纹稳定）；WG-03 移除 TEXT_ONLY 冒充捷径（NO_FIGURE/AWAITING_VERIFICATION 独立终态不伪报）+ 类型化 FigureArtifactRef 挂接 + AWAITING_VERIFICATION 服务层双分支修复（独立 UI 态/放弃路径热区遗留）；T4 保守分支 VisualExecutionEngine 端口+fake+17 契约用例（零依赖、JS 仍 UNAVAILABLE 不广告，R1/R4 RELRO 判读矛盾悬置待收口）；T5 PRD-09 harness 可先行半边（120 盲测+24 开发集 SHA-256 冻结、review_source 必填、预算清单费用记未知、fake 端到端报告、18 项机制测试实跑全绿；真实质量 UNVERIFIED）；T6 本地诊断闭环（崩溃/协程/ANR JSONL 环形账本三重封顶+ApplicationExitInfo 对账+SAF 诊断包导出，零外发零新依赖，设备端到端含飞行模式导出实证）；T7 仅 G3 导出图注落地（**G1/G2 P0 热区未落待下批，T7 不声称完成/已合规**）；T8 abiFilters=arm64-v8a+x86_64（21 条 .so 16KB 实测、省 23.1MiB、**32 位设备不可装**机型面收窄显式化）；T9 LongRangeSampleLedger 只读台账（零 UI 零启用）；T10 3E 设备用例 1 绿 2 红→**KD-64**；五研究 R1–R5 落盘（R5 仅提案待裁）；设备五组命令+exit+用例数落盘批次记录 §3.12；修复环 F1/F2/F3 处置完成；单测门第 3 轮 exit=1→修复环复跑 exit=0、**强门未跑**；零 schema 零版本 bump——见§12 与批次记录 `docs/research/2026-10-25-batch-20-record.md`）
- 上次维护：2026-10-24（批次 19 收尾：6C-2 收口尾——T1 三裁定+机器实测数字回写设计件 §9.9/§9.11/§7（openMasks={0,8} 契约不变）、T2 六项加固（写侧拒负 rowCounts/摘要 assetPolicy 四处同口径/deleteAllData 清待重建标记/恢复交换前 S3 软引用重查/I5 五站点 SQL 单源化/子集文案按 mask 实示）、T3 KD-61 收窄（图生成对账接门+关库前硬性 quiesce+DataStore 独立 scope，open（收窄））；5B 两半边——G6 排程半边（窗口内预算线性爬升，新常量 EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION=0.5 **PENDING_CALIBRATION** 待 Wave X 校准；3E 扫描到期压力接既有 EXAM 通道；ReviewPlannerV2 零改动零 bump）、G8 回收域层半边（答卷身份幂等键/出卷答案键链模型/客观题本地判键不可信→待核验/LOCAL_CHECKED 回灌端口，零 schema 零 UI 生产零消费）；设备：D1 换机演练 6/0（mask 15/9/13 三条+损坏包停交换前+真实杀进程两阶段）、备份回归 56/56、迁移 15/15、R8 冒烟+本地任务线 5/5+网络观测=0；**主会话收口（2026-10-25）**：单测门复跑本批全绿（core:data 1061 例中 3 失败均为他线 KB/dense 在飞金标漂移，点名归因不改）、`BackgroundWriterQuiesceTest` 强制重跑 4/0、强门 exit=0；提交 `0f4fd012`（46 文件，`SmartMistakeBookRoot.kt` 按复核发现#7 排除）已推送 `311f7de1..0f4fd012`；KD-63 新登记（deleteAllData 标记时机窗口）——见§12 与批次记录§7）
- 更早维护：2026-10-23（批次 18 收尾：6C-2 内容选择备份 v3 端到端落树——域闭包/仅 9·13·15、schema 69 机器清单（86 表/2 视图/92 FK、78 项裁剪序、20 软引用、closureVersion=2）、快照裁剪器（FK 前置/单事务/FTS rebuild/条件 VACUUM）、manifest v3（selection schema 2、86 表 rowCounts、digest）、恢复交换前逐表对拍+派生索引恒裁+重启后按科重建、设置页勾选与恢复前确认、KD-55 有界读入；最终树单测门/强门 exit=0（主会话实跑）；设备复验 备份 53/53（裁剪 9、成包 3、恢复 8、回归 33）、迁移 15/15、UI 11/11、365MiB 1/1；全选恒裁派生、v3 缺 selection 拒绝、恢复回执 selection 已实测；KD-55 fixed、KD-61 保持 open；提交 c87695f6 已推送（23794770..c87695f6）——见§12）
- 更早维护：2026-10-22（批次 17 收尾复验：单测门 11 模块 exit=0、强门 exit=0（主会话实跑）；设备复验通过（PRD-05 7/7 含 good/旋转、迁移 1→69/68→69 15/15、保存/重开 13/13、重绘/对账 5/5、详情 5/5、导出 17/17；PRD-06/08 守卫默认跳过、opt-in 2/2）；schema 69、WG-08/06/07 代码级、6C-2 重算、KD-61 保持 open；真实 Provider/365MiB+visual 仍 UNVERIFIED；提交 ecb1b3e6 已推送（30f7a287..ecb1b3e6）——见§12）
- 更早维护：2026-10-21（批次 16 收尾实跑：单测门 11 模块 3,029/0、强门 exit=0；设备 64/0，含 PRD-05 自评/undo 24/0、6B-2 原生编辑 8/8、PRD-06 能力快照 1/1、批次 15 备份回归 27/0；PRD-05 数据/UI/接线落地、WG-04a/04b + 请求 schema 18、6B-3 白名单改卷、KD-43/47/62 关闭、KD-61 留；WG-06/07/08 设计件落盘，schema 69 留批次 17——见§12）
- 更早维护：2026-10-20（批次 15 收尾实跑：单测门 11 模块 exit=0、强门 exit=0；设备 112/0，含 365MiB 大库恢复 1/0（39.045s）、备份生命周期 3/0+恢复/AUD-05 27/0、独立快照 2/0、答案键纠错 11/0、原题编辑 5/0、library 21/0、分享 8/0、app 18/0、迁移矩阵 14/14；KD-53/54/56/57/58 关闭，KD-43 部分，KD-61/62 登记；WG-05 收口，WG-02/04 顺延——见§12）
- 更早维护：2026-10-19（十四轨批回填：4E.1 波次 1 落地（IMAGE_MODEL only）/A8 手动建题关闭/6B-2 卷面编辑/4C A7 分享入口/KD-53·54 进展/KD-56~60 登记；另同日他线：实际产品研究、PRD-01～11 补缺排程、阶段5开发/启用分界及在飞源码复核——见§12）
- 上次阶段证据回填：2026-10-18（十三轨批：6B-1 导出半边 / 4C 收藏 facet+手动转写（schema 67）+去重可见回执 / KD-51 重建分块 / KD-44 自动结算 / KD-17·46~51 关账 / KD-52~55 登记；另同日他线新增 **4E 统一生图与 JS 后端**路线修订与 WG 接线清单——见 4E 行与 §7）；此前维护：2026-10-17（十二轨批回填：3E 第二档/6C-1 设置迁移/AUD-09 净化作业持久化落地、T9 三条裁定实施（判词锚定/冷却限通道/可观察性）、KD-39~42 关闭、KD-44~51 登记；详见 §7）
- 定位：**全软件完成目标的唯一总入口**。所有分线文档从这里进；任何阶段开工前先读本文件对应行。
- 状态图例：✅ 已完成 ｜ 🏃 进行中（**勿动**） ｜ ⬜ 未开始

> 2026-10-19后续计划修订：按实际产品研究补入原题/答案键纠错、原生导出、基础复习、恢复与生图质量门。开发与真实数据启用分开，最新排期见§12；历史完成记录及3D数值门保留。

## 1. 总目标

把软件从现状重构到以下终态（全部裁定见决策台账）：智能体即应用（五栏 + 悬浮球总闸 + 会话按
入口隔离）、掌握库算法可信（β-二项表示 + 校准能训 + 版本可回退 + 规模化不痛）、错题本与录入
统一、所有生图入口由模型按共享规则选择JS或图像模型、导出=练习卷自由排版、复习=卡片流+判题限定、学习档案只读、文档世界与代码
一致。**完成 ≠ 本路线走完即可宣称——数值层的最终可信仍以 Wave X 的真实数据为准。**

**覆盖范围（不得含糊）**：知识库有效坐标系仍限 **四科**（MATH / PHYSICS / CHEMISTRY / BIOLOGY）。
2026-10-13 包实读：**随 App 成品 v13 为 3,741 原子节点，staging 为 3,356 节点**（两者不混算；
旧 3,761 为历史时点值）。语英政史地只有 2020 样例的 19 个节点，实质无坐标系。
候选并点与退役未晋升前，产品能力按成品声明；两包均声明 PARTIAL。
终态里的「掌握库算法可信」只对四科成立；五科的表现按 D-Q6-2 明示「暂未支持掌握分析」
（该文案尚未落地，登记为阶段 3D 的一项）。

## 2. 文档体系（按层，自上而下读取）

| 层 | 文档 | 角色 | 状态 |
|---|---|---|---|
| 0 权威 | `docs/agent-first-refactor-decisions-2026-09-23.md` | **决策台账**：全部产品裁定 | 持续追加 |
| 0 权威 | `docs/superpowers/specs/2026-09-25-agent-first-refactor-design.md` | 设计规格（五栏/会话/工具面/作废清单） | 定稿 |
| 0 权威 | **本文件** | 总路线（唯一入口） | 本提交 |
| 0 权威 | `docs/requirements-fused-final-2026-10-11.md` | **需求定稿 R1–R11**（融合内核审计，2026-10-10 两轮确认） | 定稿 |
| 1 阶段 | `docs/superpowers/specs/2026-09-25-agent-first-refactor-phasing.md` | 阶段划分与依赖 | 已含 3A/3B/3C、4A/4B |
| 1 阶段 | `docs/research/2026-10-11-new-stages-spec.md` | **新阶段实施方向 spec（3E/4C/4D/5B/6B/6C）** | 定稿 |
| 1 补修 | 本文件 §7 + 各分线的 AUD 任务段 | 2026-10-13 审计任务归位；仅补已有链路和验收，不另起总路线 | ⬜ |
| 2 内核 | `docs/research/2026-09-28-kernel-execution-steps.md` | 内核线执行步骤（12 步） | 定稿 |
| 2 内核 | `docs/research/2026-09-28-kernel-remediation-roadmap.md` | 内核波次手册（Wave 0–X） | 定稿 |
| 2 内核 | `docs/research/2026-09-28-algorithm-fix-plan.md` | KF-01~34 实施级修复 | 定稿 |
| 2 内核 | `docs/research/2026-09-28-kernel-scale-precision-audit.md` | P/F/Q/S 补充缺口 | 定稿 |
| 3 子系统 | `docs/current/visual-generation-contract.md` + `docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md` | 当前绘图契约 + 4E 实施计划；4B 生图/导出旧完成记录保留 | 方案确定，待实施 |
| 4 证据 | `docs/architecture-research-2026-09-22.md` | 全量研究（§7b–7f 外部对标） | 定稿 |
| 4 证据 | `docs/research/2026-09-25-mastery-mechanism-review.md` | 阶段 0 研究（9 项必须补） | 定稿 |
| 4 证据 | `docs/research/2026-09-27-kernel-deep-review.md` | 四层病灶 A/B/C/D | 定稿 |
| 4 证据 | [全软件审计](research/2026-10-13-software-completeness-audit.md) | 已接线、隐藏缺口、证据等级与同日验证快照 | 只读证据 |

## 3. 统一分阶段线（总表）

| # | 阶段 | 内容 | 读档 | 状态 | 退出门 |
|---|---|---|---|---|---|
| 0 | 收口 | 插眼 6 研究、插眼 1 复合考前加权、插眼 5 持久确认卡 | mastery-mechanism-review | ✅ 已完成 | 已完成 |
| 1 | 地基 | K1 会话事实模型收敛 + D-K4 统一底层 + Q3 首装后台化 + Q5 死重删除 + 权限三档 | spec §2.3/§3.1b/§4.1、fix-plan KF 相关 | ✅ 已完成（2026-09-29，提交 `bd873eed`） | 旧行可读、迁移矩阵绿、唯一槽不破 |
| 裁决 | 3A 前 | 9 项"必须补" + 路线图待裁项，逐项给证据+推荐 | mastery-mechanism-review §2.5、roadmap 待裁表 | ✅ 已完成（2026-09-30，台账「3A 前裁决门 · 收口」，净 16+1 项） | 已落台账 |
| 2 | 智能体一条交互面 | C1/C2 + 致命 6 + 严重 7 + 工具面泛化 + S2/S5 + D-Q9 模式地基（F1–F8 UI 侧） | spec §2.2b、phasing 阶段 2 | ✅ 已完成（2026-09-29，提交 `3e0cb955`；出口门实测与遗留清单见台账「阶段 2 出口门」） | A1–A6 缺陷关闭、真机走查 |
| 3A | 算法表示与数值重做 | Wave 0 地基 → Wave 1 数据质量 → Wave 2 拟合可信 → Wave 3 表示重做 → Wave 4 规模化（3A 尾：S12–S16/S1/S3/S4/S7/S10/S19） | execution-steps Step 4–8、roadmap Wave 0–4 的 3A 部分 | ✅ **Wave 0 ✅ + Wave 1 ✅ + Wave 2 ✅ + Wave 3 ✅ + Wave 4 ✅（2026-10-02 收口）**。Wave 3：β-二项 + 记忆卡 + E 判据（projector-v9/v10、v56）、KF-08/KF-11/KF-16、P5–P7（台账「批次 2/3 完成记录」）。**Wave 4 四批**（`docs/research/2026-10-02-wave4-completion-record.md`）：排程放大 S12–S16（5000 候选 **1054→168ms**、选卡逐位等价、零 bump）、投影热路径 S1/S3/S4/S7/S10 + **裁决 26**（看答案 lapse KC 卡）+ **KF-17** 错峰（**projector-v11 / evidence-v5 / attribution-v3**，`learning-core-v11`）、重放路径 S5/S8（50k 重放 **1076→176ms**、零 bump）、KB 索引 **S19 诊断判"不加索引"**（覆盖内部索引已在用，显式索引实测慢 4.7×）+ **S21 安装期预热** + 安装链批量删除分块（零 bump）。门：全量 JVM **2127/0**、core:database 仪器化 **188/0**（ciSlowRunner 同 CI）、检索仪器化 **2/2**、app 三屏 **5/0**、R8 冒烟净 | 各波退出门（合成数据恢复绿、手算轨迹、迁移矩阵 1→56 绿、定向仪器化绿、R8 冒烟净、5000 候选基准 <1s、检索 p95 门绿） |
| 3B | 掌握库结构 | M1–M7 + KF-32 改绑重放真正实现 + 插眼 8 细设计 + 投影版本回退实战 | fix-plan、roadmap Wave 4 的 3B 部分 + Wave 5 的 KF-32（3B 收尾） | ✅ **六批全部提交（2026-10-03 收口，`docs/research/2026-10-03-stage3b-completion-record.md`）**。B1 M5 死面（schema 57）、B2 M1 fixture 退场（58）、B3 写通道并一 + V1 退场 + 选择器接线、**B4 KF-32 改绑→重放**（`BINDING_CHANGED`；**projector-v12 / attribution-v4 / ledger-v3**，`learning-core-v12`；schema 59）、**B5** M2 hint 接线 + advisory 一等工具 + 插眼 8 桶代号、**B6** 投影回退工具化 + 真机演练（9/0）。终门：全量 JVM **2174/0**、core:database 仪器化 **200/0/0**（性能门冷启严格口径、矩阵 1→59）、app 三屏 **5/0**、R8 冒烟净（PID 6882 / logcat 零 FATAL） | 每批独立复核（P0/P1 全处置：B6 P1 呈现态超前→恢复前显式拒绝）；剩余风险见完成记录 §6 |
| **补修** | **已有链路维护（按原归属执行）** | AUD-01/02→3B；AUD-03→4A；AUD-05→既有备份；AUD-06→4B；AUD-14→阶段2；AUD-15工程红门、AUD-20首装资源 | §7及各分线AUD段 | 🏃 **2026-10-17 更新（十二轨批，提交 `463a5dea`/`472394ea`）**：KD-39/40/41/42 关闭（回执对账/状态页说明/损坏行覆盖重写/staging 中断提示；KD-42 设备核按独立核对纠正——提示实际已显示）；AUD-11 三条裁定实施（判词锚定+归属落账、冷却限 MODEL_CHAT、可观察性先行）；AUD-06/14/20 剩余项照旧；新登记 KD-44~51（两条 medium：空 KC 集判词永久阻塞、冷启动 KB 搜索重建饿死连接池）。**2026-10-18 十三轨批（提交 `8335613f`）**：KD-17（事实核清：入口自 L4 起已存在，补两条回归钉住）/KD-44（pseudo 自动结算，零用户参与）/KD-46/48/49/50/51 关闭；KD-47 部分落地（枚举/回执 KDoc 如实化，UI 文案留后续）；新登记 KD-52（A8 手动题只有 schema 半边、无生产者，medium）/KD-53（100MB 单条目上限 vs 365MB 库→自建备份不可校验/恢复，medium）/KD-54（VACUUM INTO 601s 饿死写，medium）/KD-55（docx 图片 TOCTOU，low）。**2026-10-19 十四轨批（提交 `759148ba`）**：KD-52 关闭（A8 生产者落地 + 设备全链 PASS）、KD-55 关闭（docx 图片 sha/长度复核）；KD-53 进展（校验侧设备关闭、恢复侧未达=KD-56）、KD-54 进展（让路仅覆盖两仓库、设备 9 条/90s 未关账）、KD-47 再核（日志已改、UI 文案仍在他线在飞文件内）；新登记 KD-56（high：大库恢复 main 线程 `Connection pool is closed` 崩溃+回滚）/KD-57（分享重试死路）/KD-58（attachedImages 静默失败）/KD-59·60（low）；原阶段完成记录保留 | 每项先复现对应跨层场景再修并复验；修现有链不等于阶段5新增功能开工 |
| **3D** | **坐标系可信** | D-0/D-0b 沿用既有任务；D-1 语义绑定；D-2 结构及覆盖映射；D-3 最终入口检索（AUD-04）；D-4 前置图（AUD-19，覆盖依赖）；D-5 同版本晋升（AUD-07）；研究补库驱动 AUD-18 | KB 未竟清单新增 §8、批次9报告、§7任务表 | 🏃 D-0 内核已落；D-1 批次9按 95.15% 点估计判过（非总体下界保证）；D-2/D-3/D-5 尚未共同收口。候选14空点、22失效覆盖目标仍需处理，阶段5真实数据启用继续挂起；受控研发按§12 | 硬门仍为 D-0/D-0b/D-1/D-2/D-3/D-5；门数由检查器现算（同日24门），同一晋升包全绿；D-4不挡开工 |
| **读侧** | **读侧语义闭合**（**新增，2026-10-01 裁决 28**） | **修 `MasteryStatus` 快照与活判据的分叉：已忘知识点不再进 strengths；`ReviewPlanner` 跳过判据与 E 判据同源；KF-16 压制接线；`STALE` 死枚举收口** | 台账裁决 28 | ✅ **已完成（2026-10-02 收口；提交 `c5829217`，记录 `docs/research/2026-10-01-read-side-closure-record.md`）**：`effectiveStatus` 读侧唯一出口；REVIEW_PLANNER v8 / V2 v4+指纹 v9 / SELECTOR v7；验收门三条逐条落证 | 「曾掌握、久不作答」不再出现在 strengths；KF-16 有生产调用点 |
| **切片** | **薄纵向切片** | AUD-16：真实模型、有数据形态的录入→讲题/作答→判定→档案→再次复习，含恢复/改绑/导出/换机 | §7.1及§7.2、2026-10-11走查、同日审计 | 🏃 **2026-10-14/15 更新：整圈首次走通到判定**——录入/分题/入库/复习已真机走通；**判定链两个断点已修并设备复验**（KD-35 重试连续 4 次不崩、requestId 尾缀 `:0→:3` 递增；KD-36 提交后 `attempt_submission`/`review_log(LOCAL_CHECKED)`/会话推进收据各 1 行、会话 IN_PROGRESS(0)→COMPLETED(1)，提交 `0bdd651b`）；**档案仍为空且属既有设计**（讲题判定通道不写知识点归属，`learner_knowledge_mastery_state`=0 行）。上批遗留收口：模型链路 KD-31/33 已修（`64c79456`/`e3de72f8`）、内存天花板 KD-32 三处支配者已削（`42096510`，保持 open）、采样设备证据补齐（`f0b76d21`，AUD-10 结）。**剩余**：KD-37（「重新判定」死路）、KD-38（讲题标签超 32 字符整轮永久失败，未验证触发频率）已入下一批；识别精度对照未做。**2026-10-16 九轨批**：KD-37/38 修法已落地（`038d50c0`：快照时间戳容忍比较 + 标签预算截断/可重试；KD-37 instrumented 2/2）——**设备段被 opencode zen 周配额阻断**，「失败→重新判定→成功」UI 端到端与 KD-38 前后对照**未达**；T3 切片补走唯一正面结论=job 建立后被杀可完整续跑（5/5 页 READY、无重复行），staging 阶段被杀静默丢弃另立 KD-42；第二次复习/异常恢复/导出走查未达（同因）。**2026-10-17 十二轨批**：设备段仍 429 阻断——KD-37 UI 端到端与 KD-38 前后对照**再次未达**（不重试不换凭据）；无模型项有实证：KD-42 中断提示**设备已见**（独立核对纠正走查自述）、T6 净化作业行在真库出现（`user_version=66`）、feature:library 49/0；**新观测 KD-51**（冷启动 KB 搜索特性重建饿死连接池：单条 SQL 等 270s、冷启 ANR、采集卡「正在安全保存」，三次 reboot 后仍复现）。**2026-10-18 十三轨批**：设备段**未能判定**（模拟器 DNS 全面不可用，非 429 非成功；重启两次含 `-dns-server` 未恢复）——模型项仍未达；**KD-51 改后对照成立**（人为构造强制全量安装：0 条 `unable to grant`，改前单条 270.2s；采集保存不再被饿死）；新增 KD-53/54（备份栈两条 medium）；KD-17 两条回归设备复跑 17/0 + 6/0。**2026-10-19 十四轨批**：**网络根因定位并修复**——模拟器内建 DNS 代理（10.0.2.3:53）无响应（上游 UDP/53 全通；`-dns-server`/换 AVD/private_dns 均无效），以 `iptables nat OUTPUT DNAT 10.0.2.3:53→8.8.8.8:53` 恢复；**模型项仍未达**（事故：`:app:connectedLocalFirstDebugAndroidTest` 的 AGP 卸载步骤删掉 app 数据，含模型配置，无凭据可重播种）；无模型项有实证：**A8 手动建题全链 PASS**（建题→入库→详情→导出 PDF）、分享入口「接收分享」页如实拒绝、KD-53 校验侧通过、T1 双入口 fail-closed；**新缺陷 KD-56**（大库恢复 main 线程崩溃+回滚，两次复现）。记录：`docs/research/2026-10-11-thin-vertical-slice-walkthrough.md`、`docs/research/2026-10-14-4c-stage1-record.md`、`docs/research/2026-10-16-verdict-chain-and-nine-track-batch-record.md`、`docs/research/2026-10-16-batch-12-twelve-track-record.md`、`docs/research/2026-10-17-batch-13-nine-track-record.md`、`docs/research/2026-10-18-batch-14-eight-track-record.md`；提交 `707ca3de`/`79b7b01e`/`f7462299`/`0bdd651b`/`f0b76d21`/`038d50c0`/`463a5dea`/`472394ea`/`8335613f`/`759148ba` | 每一步记录样本数、请求/事实ID、期望与实际；模型不可达即 UNVERIFIED；成功标准不是仅无崩溃 |
| 3C | 坐标系自愈 | KF-29 监测 → KF-30/KF-07 → KF-31 自动重审（数据达标才开）（3D 产出「该不该改」，3C 产出「自动改」；**D-1 过门 = 自动落账许可门**——C2/C13 全自动化去确认的落点；**K5 深度补研 = 任务非门**（触发点 = C2/C13 自动化开工前；任务书见台账裁定 39））+ S9/S17/S18/S20 规模项 | fix-plan P3 组、roadmap Wave 5 | 🏃 **后半完成（2026-10-05/06；综合门 app 全套 exit=1 未取证，见 `docs/research/2026-10-05-stage3c-part2-completion-record.md`）**：前半 `1d297efc`（KF-29/KF-30/KF-07 完成 + KF-31 未启 + schema 62，见 `docs/research/2026-10-04-stage3c-part1-completion-record.md`）。后半三批：`8d7e24b2`（S17 性能量化——`PerformanceGateTest` 空实现改真实 1 万行夹具 + `observeActiveMistakes` 专属门 p95=49ms/5000 行，退化-红/还原-绿）、`d78815c3`（S9 p95=12ms **未物化未加索引**；S18 5 万行真实夹具实测 count 4056ms/facet 78870ms 超预算 → **schema 63** 复合索引，修复后 46ms/62ms；**未做 mastery/标签物化**）、`85e6b6ae`（S20 观察侧 conflate + 后继映射缓存，零 schema）。综合门：全量 JVM exit=0、DB 全套 exit=0、**app 全套首轮 exit=1 定案环境锁争用**（3 例 SQLITE_BUSY；单独重跑 53/0/0）、R8 冒烟净（PID 19503 / logcat 零四类崩溃标记 / APK sha256 `5715236f…`）。**KF-31 未启**（KF-29 样本量仍 0）。**S18 尾（2026-10-06/07，`docs/research/2026-10-06-s18-fts-completion-record.md`）**：`d1a0fbf3`（计数/三分面改 CROSS JOIN 顺序锁；100 命中 102ms vs 改前 14959ms；等价 13 组矩阵）、`3e494705`（宽命中排序改集合级命中集 LEFT JOIN；10k 19208→144ms；同序对照）、`504c344a`（收口修复轮）；综合门 JVM/**DB 230/0/0**/app **53/0/0**/R8 净；bm25 换库已裁：不换（F6，2026-10-03/04；重开条件见 `docs/kb-outstanding-research-2026-10-02.md:1193-1196`）。**K1（2026-10-08/09，`docs/research/2026-10-08-k1-completion-record.md`）**：④-6 检索固定开销**已闭合（去两条固定 COUNT）**——召回索引完整性判定每进程每科一次、缓存命中零 DAO 调用（`0b1b5a7f`）；**50k FTS 规模已补测**（count/ranking 两记录类，`0b1b5a7f`）；**批 2：KF-32 空绑定重放改挂 pseudo（`PROJECTOR` v12→v13 / `ATTRIBUTION` v4→v5，零 schema）**；**K2（2026-10-09，`docs/research/2026-10-09-k2-completion-record.md`）：存量库升级演练（v13 首开=先归档再重放，JVM 逐位 + 真 Room 四断言）与 ④-6 探针落档（探针已在库，s19 §6 复测；K2 当次复跑见记录）**；**K3（2026-10-10，`docs/research/2026-10-10-k3-completion-record.md`）：KF-30 落地——分类接受置信度落库（`accepted_confidence` 列 + schema 64 + 写侧真值/NULL + 合并读真值；判据一行未改，(a)(b) 消解；零算法 bump）；R1/R2 深度研究（须结合外部高质资料）后置**；**K5（2026-10-11，`docs/research/2026-10-11-k5-completion-record.md`）：R1/R2 研究结论落地——未知存量改判显式策略门（`UNKNOWN_INCUMBENT_COMPARISON=GATE`，判定附 `incumbent_known`/`incumbent_unknown` 理由；判据一行未改）+ 分类候选集落库（schema 65，含被拒候选）+ margin「存疑」信号（只派生不改写）+ 校准审计件（代理金标=用户纠正；无样本如实报）；K5-C 待排、margin 阈值待校准** | 监测有数据、错绑可自愈、规模项量化与按需物化落地、FTS 搜索路径修正（性能结论受模拟器环境口径限制；bm25 已裁不换（F6）；50k 已补测；④-6 已闭合） |
| **3E** | **全库薄弱点扫描（内核侧）**（**新增，2026-10-11 需求定稿 R5-F7**） | **改 spec §1.3/§3.2 范围定稿（二轮确认的产品决策）+ `StudyReviewPlannerService.kt:541-556` 队列装配 + `KnowledgeReviewQueue` 第二范围档（全库扫描，不受今日队列限制）；每日捞回量由 F8 预算 + F17 配额封顶**。残留边界：无绑定题的知识点无时间信号（定稿 R5 已注明） | 需求定稿 R5、new-stages-spec §3E | 🏃 **2026-10-17 第一批落地（`463a5dea`）**：第二档 = 全库「有绑定题的活跃知识点 ∩ 可出题材料」− 今日候选池，与今日队列并集按分取前 N；**N = 每日 10 个**（2026-10-16 裁定，`FULL_LIBRARY_SCAN_DAILY_CAP`）+ 时间预算沿用 20 分钟双封顶；spec §0/§1/§3.2/§5-P3 同批改写；单测（离开今日并集仍可捞回/封顶/无绑定题不在范围）已过门。**口径注（KD-46）**：「每日 10」实为「每次排计划 ≤10」（队列不落库，low 登记） | 弱掌握点离开今日错题并集仍可被捞回（单测 ✓，2026-10-24 批次19 复跑 22/0；真实数据复验待设备，批次 19 设备侧未做 UNVERIFIED）；F8/F17 封顶生效（时间预算 + 名额 ✓） |
| 4A | 错题本与录入 | L1–L7（标题单源/归类收敛/筛选排序/录入统一/待处理入录入界面/导出后台化/删 TRASHED） | spec §2.2、phasing 4A | ✅ **四批全部提交（2026-10-03 收口，`docs/research/2026-10-03-stage4a-completion-record.md`）**。L1–L7 全做：批 1 `857f4d09`（TRASHED 退场/标题单源/筛选排序/S17；schema 60）、批 2 `7f109251`（归类收敛/待处理入录入界面）、批 3 `f4ba32c5`（单题与批量统一为「录入」）、批 4 `e89727ee`（导出后台化 A 形态/导出成果入口；schema 61）。终门：全量 JVM **2234/0**、DB 仪器化 **208/0/0**（矩阵 1→61）、feature/library 36/0、app 全量 **53/0/0**、R8 冒烟见完成记录；真机走查三入口通过 | 三条退出门逐条落证（列表详情同源/录入一条流/导出不随页丢）；每批独立复核 P0/P1 全处置；导出人工走查与 S17 性能登记 UNVERIFIED |
| **4C** | **错题本与录入二期** | 先完成 AUD-03 多题真实提交；AUD-08 切分金标及切残红线；既有整卷/PDF链核验与主路径化；A5/A7/A8入口、题级去重、收藏 | 新阶段spec §4C及§审计补充、§7 | 🏃 **① 真机核验通过（2026-10-13/14）**：新包「开始分题」边界评估 54.88s SUCCEEDED（decision=PASS、relations=NEXT_QUESTION×6、boundary_after_status 页 0–5 全 NEXT_QUESTION、旧抛点 logcat 0 次）；根因=跨页关系请求（整窗多页）复用单页分题契约、模型回 25/19 个 questionRegions 越 2–12（curl 复现 + 契约复刻判定）；修法=关系判定专属提示词 + 分题规则下移到切分消费侧（`707ca3de`）；**2026-10-14/15 补**：关系轮 issue 形状耦合收口 + 三协议族流式截断标记（`f0b76d21`）。**② 金标集 v1 已冻结**：24 抽样 → 7 页/16 框入集（sha `f9e92c47…`、validator 0、证据等级=模型标注无人工专家）；**度量口径已修正并落盘脚本**（`tools/split_golden/measure_split_accuracy.py`：单题金标页「不拆分即正确」，阈值未动）→ **题数一致率 0.5714（门 0.9）、切残率 0（门 0.05）→ 未过门 → ③ 门后自动化继续不启用**；「漏」集中在 5/7 页无应用输出（真实缺口）。页级 CAPTURE_ASSESS 仍有 2/7 不可重试 INVALID_RESPONSE（复核登记）。**AUD-03 已修法落地（2026-10-16，`09f0c85c`）**：逐题提交 + SAVED 回执（打开首题→提交成功才置 SAVED→自动进下一题；不再预标 SAVED），core:data 仪器化 3/3、设备三 3/3；**设备走查未达（配额）**；回执写在提交事务外另立 KD-39（medium，AUD-25）；**2026-10-18 十三轨批（`8335613f`）**：**C11 收藏**（列表行开关 + `LibraryFacetKind.FAVORITE` facet + 两条查询链，schema 67；详情开关留后续）、**B5 去重可见化**（`reusedExistingProblem` 如实回执 + 卡片两态；判据未动）、**A8 手动转写**只落 schema 半边（校验器按 USER_TRANSCRIPTION 放行；**无生产者**——KD-52/AUD-28，全链验收未达）；A5/A7/A8 入口其余未开工。**2026-10-19 十四轨批（`759148ba`）**：**A8 手动建题生产者落地并设备全链 PASS**（录入屏「手动建题」→ 空源草稿 → 入库 → 详情 → 导出不崩；KD-52 关闭；连带修掉空白照片段落误放行的既有回归）；**A7 系统分享进 App 落地**（MainActivity ACTION_SEND/SEND_MULTIPLE → 接收页；成功路径 `SharedCaptureEntryInstrumentedTest` 5/0、字节层 8/8 设备；失败态出口缺陷=KD-57）；A5 连拍/A6 核验剩余未开工。记录 `docs/research/2026-10-14-4c-stage1-record.md`、`docs/research/2026-10-17-batch-13-nine-track-record.md`、`docs/research/2026-10-18-batch-14-eight-track-record.md`，提交 `707ca3de`/`f7462299`/`f0b76d21`/`8335613f`/`759148ba`。**2026-10-20 收尾**：PRD-03 原题修订（CAS/revision/旧 basis 失效/详情“修改这道题”与最小编辑页；`ProblemRevisionEditDatabaseInstrumentedTest` 5/0、`MistakeRevisionEditInstrumentedTest` 2/0、library 编辑链 9/0 设备通过）；KD-57 分享重试/Expired（成功/明确放弃才撤授权、10 分钟窗口派生 Expired、返回键释放；JVM 6/0、设备 8/0：再试成功 + Expired 可达，KD-57 关闭）——两者已按设备验收 | 所选题逐题实际入库后才标已录入；中断可继续；切残不得自动入库；题级去重生效（✓ 判据未动 + 可见回执） |
| 4B | 智能体生图与文档导出 | A1–A6 生图工具面 + B1–B6 导出排版（练习卷参数化；Typst 二期研究） | imagegen-and-export-design | ✅ **四批全部提交（2026-10-03/04 收口，`docs/research/2026-10-03-stage4b-completion-record.md`）**。批 1 `7a44bf40`（第 8 枚 `GENERATE_FIGURE` + A3 门单源 + A5/A6）、批 2 `92764af2`（A2 图持久化/幂等 + A4 三态记账 + 隐式 AI 标识）、批 3 `d2317f5d`（B1/B2 练习卷参数化 + B4 三链路/金样 + B5 研究不立项 + B6 干净图入导出）、批 4 `c11193a8`（B3 payload 白名单 + 确认卡 + 导出 sheet + 后台导出接线；`MistakePdfLayout` 迁 core:model）。零 schema（61 不动）、零算法 bump（台账 §3.18）；提示词 bump 自记（plan v18 / respond v25 / lobby v17）。终门：全量 JVM **2344/0/0**、DB 仪器化 **210/0/0**、app 全量 **53/0/0**、R8 冒烟净（PID 20321 / logcat 零 FATAL） | A/B 用例绿逐条落证；真机走查：生图段因模拟器无模型凭据不可达，按 4A 口径登记 **UNVERIFIED**（仪器化证据覆盖）；每批独立复核（批 4 P1×2 修复后定向复验） |
| **4D** | **图像净化与几何重绘** | B7语义提取/重建→4E共享生图判定与后端→图题核对；AUD-09 去手写覆盖与持久作业；AUD-10 crop/净化内存及采样精度；识别/重绘金标门；A11/B4 | 新阶段spec §4D及§审计补充、4B设计§审计补修 | 🏃 **2026-10-17 净化作业持久化落地（`463a5dea`，schema 66）**：`clean_redraw_task` 表（身份含源图 sha 指纹）+ 同指纹跳过不重复付费 + 失败落码 + 启动对账重试（只接手早于本进程的行）+ 晚到结果 SUPERSEDED 保护；`CleanRedrawTaskRunnerTest` 17/0、设备 `CleanRedrawTaskInstrumentedTest` 2/2、迁移矩阵 13/0。**未收口**：B7 语义重绘链（需模型）、识别/重绘金标门、触发范围回归走查；**设备核未达**（模型 429 + DB 饥饿，KD-51） | 识别/重绘与切分分别定义门；文本题/图题覆盖；失败与杀进程能恢复（持久化 ✓ 设备观察未达）；图题等价与采样精度通过 |
| **4E** | **统一生图与JS后端** | 所有入口共用prompt/coordinator/ledger；模型选JS或IMAGE_MODEL；隔离QuickJS、共享场景/布局/Canvas；公共operation/attempt、程序/快照/引用；PDF/SVG、旧图兼容 | current/figure-generation-prompt-policy + current/visual-generation-contract + superpowers/plans/2026-10-18-flexible-js-drawing-implementation | 🏃 **2026-10-19 波次 1 落地（批次 14，`759148ba`）**：统一 policy/coordinator/ledger 端口 + `DefaultFigureGenerationCoordinator`/`FigurePromptAssembler`/`RoomFigureDispatchLedger` 落树；图像通道只接受绑定 operation/method/源证据/配置的**已预留 dispatch context**（POST 前复核、旧无预留入口 fail-closed）；讲题 GENERATE_FIGURE/保存净化/后台重绘三入口统一转调（**IMAGE_MODEL only，JS UNAVAILABLE 不广告；模型侧 method 字段=WG-02/04 下一波**）；fake 后端 wiring 测试过、无预留图像 HTTP 调用=0。**未接**：WG-02/04/06/07/08 全量接线、QuickJS 运行库、真实 Provider 质量；桌面 33 帧/13 检查、API34 隔离探针 6 项仍仅为研究证据。**2026-10-20 批次15 收尾**：WG-05 历史 attachedImages 收口——cache-only 读取、可读原因、显式“重新生成”走共享 coordinator/账本；生产经 `AttachedImageResolutionPort` 能力对象穿过既有 resolver 参数接线（Application/Root/Destinations/TutorChatConversation）；KD-59/60 关闭、KD-58 关闭（core:ui 41/0 含卡片 8 例；设备未点击）；WG-02/04 仍顺延下一批。**2026-10-21 批次16（WG-04，代码级）**：WG-04a SSE 终态门——`delta.tool_calls` 按 index 聚合、`finish_reason=tool_calls` 终态、512KiB/64KiB 产品预算、有状态 UTF-8、截断/坏帧 fail-closed；不支持聚合的工具回合强制 `stream=false`。WG-04b 请求 schema 18——`TutorVisualContext` + `@EncodeDefault(NEVER)`、`TutorToolCall` 增 method/program/imageOperation/imagePrompt、原生 schema 与 Route B 同一解析点，只广告 IMAGE_MODEL、JS 仍 UNAVAILABLE。**收尾实跑：单测门 3,029/0、设备能力快照/声明过滤 1/1；真实 Provider 未验；WG-06/07/08 设计件已落（schema 69 留批次17 WG-08），实施未开始。**2026-10-22 批次17（WG-08/06/07，代码级 + 设备）**：schema 69 两审计表 + 四追加列 + 迁移（68→69 设备 15/15）；operation/attempt 仅审计镜像、预算权威不变；`VisualRole` 四角色单源；保存同事务复制、详情分区、显式重绘 current-revision CAS/晚到 SUPERSEDED、PDF/DOCX 角色过滤；设备复验通过（PRD-05 7/7、迁移 15/15、保存/重开 13/13、重绘/对账 5/5、详情 5/5、导出 17/17，见记录 §8）；真实 Provider 未验。**2026-10-25 批次 20（WG-02/03/10 代码级 + 设备 fake 链）**：WG-02 CAPTURE_PARSE 需图返回同型 proposal、`CapturedFigureReference` 类型化绑定（源页/裁切/草稿修订/源 SHA，@EncodeDefault(NEVER) 指纹稳定）；WG-03 TEXT_ONLY 冒充捷径移除（NO_FIGURE/AWAITING_VERIFICATION 独立终态、不伪报已产图）+ FigureArtifactRef 类型化挂接 + AWAITING_VERIFICATION 服务层双分支修复（独立 UI 态/放弃路径热区遗留）；WG-10 复核确认三旁路已收编（出网点唯一=coordinator）+交付 HTTP transport 旁路观测测试（MockWebServer 拒绝=0/授予=1+fail-closed 兜底）；T4 保守分支：core:domain `VisualExecutionEngine` 端口+fake+17 契约用例（零依赖、JS 仍 UNAVAILABLE 不广告；R1/R4 对 quickjs-kt 1.0.15 发布 AAR 16KB/RELRO 判定相反→复核按官方豁免判据裁 R4 正确，消费形态悬置，真实库接入前必须收口）；设备 fake 链回归 52/52、五 APK 装配 exit=0（命令/exit/用例数落盘批次记录 §3.12）** | 全入口同policy、双方法选择/切换/6派发、API23/arm64/16KB/R8、隔离硬终止、迁移备份GC、六类样例/无障碍/共享导出、真实模型质量 |
| 5 | 复习栏 | 5a三键/撤销/无模型路径、5b知识点/预算、5c讲题/错因；先接PRD-02答案键纠错和PRD-03原题版本 | §7.2、§12、产品闭环补缺计划 | 🏃 **2026-10-21 批次16 代码级落地 5a/PRD-05**：三键 Good/Hard/Again（无 Easy，原档 0.8/0.7/1.0 与调度档分列、自评永非 INDEPENDENT）、稳定 request id 自评提交、attempt 级 undo（无 schema VOID 哨兵，DB 仍 68；更正+会话回退+`scheduling_eligible=0` 同事务）、无模型已存答案/跳过/请求讲题；数据层 + UI + Root/Destinations 接线均有源码。**收尾实跑：单测门 3,029/0、设备自评/undo/重放 24/0；UI 合同 JVM、无设备点击；真实数据启用仍挂起（3D 未共同收口）**。**2026-10-22 批次17**：settler 改为按 `practiceUnitId` 定位队列项、`currentOrdinal` 判队首（版本只作 CAS），undo 后请求讲题不再 fail-closed；新增 7 条隔离库设备 UI（三键/双击/旋转/中断/undo→重评/无模型已存答案）；D1 首轮 7 例中 good 键与旋转 2 例红（自评后 reveal 快照冲突），修复已落树（可机判题复用机判快照身份），**修复后设备复跑 7/7 通过（含 good 键/旋转），设备验收已验证；见记录 §8** | 3D全部硬门后启用；5a先验本地闭环，5b另依3E；三批范围及数值门不减少 |
| **5B** | **考前与纸质闭环** | G5选择器到出题工具；G6既有考试日历的范围/爬升/扩容扩展；G8/G14回收判定回灌；复用前置PRD-02/AUD-11答案键纠错，扩整卷与改编证据引用 | 新阶段spec §5B、§7 | 🏃 **2026-10-24 批次19 两半边（均代码级，主会话收口后提交 0f4fd012）**：**G6 排程半边**——考试窗口内每日复习预算线性爬升（`StudyReviewPlannerService.examCapacityBudgetSeconds`，新常量 `EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION=0.5` **PENDING_CALIBRATION**：插眼 1 只裁形状/窗口 14 天/重要性档，幅度待 Wave X 校准，已登记决策台账+算法台账）；3E 扫描到期压力接既有 EXAM 通道（仅窗口内读全库扫描、复用 EXAM_WEIGHT 不加新权重）；无考试日/窗口外逐位不变（ramp≤0 原值返回+读门 false+扫描压力仅 ramp>0 的结构保证；指纹级测试主会话复跑 6/0）；`ReviewPlannerV2` 零改动零 bump；importance 字段未接线（当前全按高档 1.0）。**G8 回收域层半边**——答卷身份（指纹+题+学习日幂等键：重传不重复计分、不同卷/不同日不撞键）、出卷答案键链模型+仓储接口+内存实现、客观题本地判纯函数（键不可信→待核验、不写数值掌握，三重锁死）、LOCAL_CHECKED 证据回灌端口+编排（仅本地判档可提交；判词通道只留接口）；11 新文件 37 用例（主会话复跑全绿）、零 schema 零 UI、生产零消费。G5 选择器台账待裁不动；导出侧键链接线/UI 入口/G14/真实落库/端口并发幂等验收=接线批次遗留 | 日期/范围→出题→作答→判定→证据回流；不把已有日历当无入口重建（✓ 复用既有日历与 EXAM 通道；半边完成，出卷→作答→回灌闭环未通） |
| 6 | 档案与设置 | AUD-12：已有学情页扩全量画像及薄弱点复习出口；R8雷达/周报/进度/内核自检；只读档案与设置总闸 | 新阶段spec §审计补充、定稿R8、§7 | ⬜ 终态扩展未开工；已有证据画像、保持率与考试设置已接线 | 全库/盲区诚实呈现、无学习事实不伪造统计；只读薄弱点可导航进入复习；原排除项不新增 |
| **6B** | **可编辑文档导出** | docx、结构化模型编辑、自然语言改卷；沿用既有PDF保存/打开/系统分享；AUD-06缓存契约为前置 | 新阶段spec §6B、4B设计§审计补修 | 🏃 **2026-10-18 导出半边落地（`8335613f`）**：手写 OOXML 渲染器（消费既有 `MistakePdfExportInput` 块序列=单一文档模型，零第三方依赖）+ 干净题面图嵌入 PNG + 格式选择（TutorExportSheet）+ 保存/分享（新 MIME + FileProvider）+ worker format 字段（缺键=旧任务 PDF、坏值整条拒且不重试）；`core:export` 13/13（含 CRLF/转义/结构金样）+ 设备链**未达**（docx 入口在模型打开的 sheet 里，模型不可达）。**2026-10-19 6B-2 卷面编辑（批次 14，`759148ba`）**：卷面模型增删题/调序/改文字/改卷面备注（**未编辑路径与正常批量导出同一快照与指纹=逐位不变**；编辑后重算 `inputSha256`、保留来源题面文档指纹/entryId/图块完整性）+ 编辑入口（TutorExportSheet 编辑态）+ 重渲染复用 PDF/docx；设备链未达（编辑入口同样只在模型打开的 sheet 里）。**未开工**：自然语言改卷（6B-3）。**2026-10-20 收尾**：PRD-04 原生单题/批量 PDF/DOCX 格式 sheet + I1 接线（format 随 WorkManager 输入/渲染分流/记录身份；JVM 3/0；library 仪器化 4/0 + app 导出回归 3/0 设备通过；真实接收应用打开仍待单独走查）。**2026-10-21 批次16（代码级）**：**6B-2 原生编辑入口**——新增 `MistakePaperEditRoute`（不依赖模型），导出成果与错题详情接“编辑卷面”；未编辑沿用批量导出同一快照/指纹，编辑后重算 `inputSha256`、不反写原题；Root 路由已接。**6B-3 有界自然语言改卷**——`PaperEditCommand` 七命令白名单 + 严格解析器 + 原子 applier（只改文档副本、非法/越界整批拒绝并保留草稿、应用后重算指纹）；`TutorExportEditSheet` 入口，模型端口未注入时隐藏（app 尚未注入，真实模型 UNVERIFIED）。**收尾实跑：6B-2 设备 8/8；6B-3 白名单/解析/原子 applier JVM 通过；模型端口未在 app 注入，真实模型 UNVERIFIED**。**2026-10-22 批次17（WG-07）**：DOCX 与 PDF 同过滤——题目区排除 `TUTOR_EXPLANATION`、解析区只收讲解图且仅 `includeSolution=true`，缺必图整份 fail-closed、不写半份；卷面副本/草稿保留题图角色与解析图（`includeSolution=false` 时仍在卷面题内，默认编辑稿不聚合）；旧单 CLEAN_IMAGE 的 DOCX 字节保持一致（JVM 已跑 16/0）；设备导出 17/17 | 两渲染器共享模型与内容指纹（✓ 块序列单源）；编辑→重渲染→分享（编辑半边 ✓，设备未达）；不重复建设PDF交付链（✓ 复用同构交付） |
| **6C** | **备份与恢复扩展** | AUD-13：在既有.smbk/验证/恢复与归档恢复上增加内容选择、设置迁移、换机兼容；AUD-05现有备份一致性先修 | 新阶段spec §6C及§审计补充、§7 | 🏃 **2026-10-17 6C-1 落地（`463a5dea`）**：manifest `formatVersion` 1→2（app 版本/内容版本戳/`hasSettings`/`settingsDigest`；v1 兼容读、>2 拒）；包内 `settings.json`（保持率/考试/优化参数/提醒偏好；排除 Key/租约/投递历史）+ 恢复侧交换前版本校验、交换后写入（失败标 partial）；`BackupRestoreInstrumentedTest` 25/0（设备）。**未开工**：内容勾选（行级子集 = 6C-2，需独立设计）、换机演练、回收站 UI 面沿用现状。**2026-10-20 批次15 收尾**：KD-56 恢复重排（365MiB 设备 1/0，39.045s）、KD-54 独立快照（设备 2/0）、KD-53 校验+恢复（27/0 含 AUD-05）——KD-53/54/56 已关闭；KD-61/62 登记。**2026-10-22 批次17 T8（只设计）**：按 schema 69 重算 6C-2 机器清单——86 表/2 视图/92 条 FK、类别闭包 5 条、裁剪顺序 78 项、软引用 20 条、`closureVersion=2`、空库全选 digest golden；共享 operation/attempt 按宿主与资产引用分行；三项裁定（仅图片 mask=8、空选择 mask=0、派生索引恒裁）保持 OPEN，未实现、未勾 6C-2。**2026-10-23 批次18 6C-2 落地（代码级 + 工单门禁/设备材料）**：域选择/闭包（4 类别位、5 条闭包、只允许 9/13/15；0/8 与未闭合组合在 UI/域/仓储/编解码四层拒绝且不补齐）；schema 69 机器清单（86 表/2 视图/92 FK、类别 30/35/6/1、ALWAYS 7、DERIVED 2、SPLIT 5、trimOrder 78、软引用 20、资产根 4、closureVersion=2、空库全选 digest golden 命中）；快照裁剪器（foreign_keys 在 BEGIN 前、单事务、I1–I7、FTS rebuild、有删除或真子集 freelist>0 才 VACUUM、空间预检）；manifest v3（selection schemaVersion=2、86 表 rowCounts、trimmedTableCount、digest；v1/v2 缺省 null=全量，v3 缺 selection 读侧拒绝）；恢复侧 Phase 2c.1 交换前逐表 COUNT 对拍（mask=15 也比）、Phase 2c.2 派生索引恒裁、Phase 7b 待重建标记、重启后按科重建（失败保留标记）；设置页四类别勾选、恢复前确认与回执 selection；KD-55 有界读入。最终树单测门/强门 exit=0；设备复验 53/53（9+3+8+33）、迁移 15/15、UI 11/11、365MiB 1/1；全选恒裁派生、v3 缺 selection 拒绝、恢复回执 selection 已实测。**未完成/保留**：设计 §9.9 与机器块仍标 OPEN（文档债）；恢复侧 S3 软引用重跑未实现；写侧不拒负 rowCounts；BackupSelectionSummary 缺 assetPolicy；KD-61 保持 open；真实 Provider/PRD-09 UNVERIFIED；本批未提交。**2026-10-24 批次19（6C-2 收口尾）**：T1 文档债回写——§9.9/§9.11 三裁定落定+机器块批次 18 实测数字（JSON 可解析、测试契约 openMasks={0,8} 不变）；T2 六项加固——写侧拒负 rowCounts（与读侧同文案）、`BackupSelectionSummary` 补 assetPolicy 且回执/UI 四处同口径、`deleteAllData` 清 `restore-index-rebuild-pending.json`、恢复交换前 S3 软引用重查（新 `RestoreSoftReferenceRecheck`，两侧规则逐项对拍无误拒合法包）、I5 五站点 SQL 单源化（`CanonicalAssetReferenceSites` 双常量 ← DAO @Query ← trimmer）、子集文案按 mask 实示（"题+图（不含掌握库、会话）"）；D1 换机演练设备门全过——mask 15/9/13 三条（86 表 COUNT 对拍+夹带审计/设置往返/Key 与租约不入包/提醒不复活旧授权/两科派生索引自重建）+损坏包停交换前+真实杀进程两阶段（进程死亡→交接件落盘→新进程恢复 mask=9 全过），备份回归 56/56（批次 18 的 53 例不回退+T2 新增 3 例）。**收口（2026-10-25 主会话）**：单测门复跑本批全绿（core:data 1061 例中 3 失败=他线 KB/dense 在飞金标漂移，点名归因不改）、`BackgroundWriterQuiesceTest` 强制重跑 4/0、强门 exit=0、`BackupSelectionUiInstrumentedTest` 设备复跑 OK(11)；提交 0f4fd012 已推送（311f7de1..0f4fd012）；KD-63 新登记（deleteAllData 清标记时机窄崩溃窗口） | 选项依赖闭合（设置面 ✓；内容面 6C-2）、设置往返 ✓、DB→文件完整性（AUD-05 修法已落）、换机与跨重置恢复（D1 实测 ✓，批次 19 三条 mask+杀进程）；保密字段不进入包 ✓ |
| 7 | 收尾 | AUD-15文档与运行证据对齐；AUD-20性能/首装最终测量；全量门禁；Q4迁移链重置与跨重置备份恢复 | §7、原spec §5/§7/§8、6C产物 | ⬜ 现有红门修复提前进行，最终收尾仍最后做 | 本次HEAD/run证据：JVM、完整Lint、工具、同包KB、设备、Release/性能分别可追溯；未测不算通过 |
| X | 数据依赖（内测后） | AUD-17：可预测长程样本台账、污染处理、离线对照；srs-benchmark模拟、常数校准、经验先验、FSRS-7评估 | 内核fix-plan§审计补修、roadmap Wave X、§7 | ⬜ 校准须≥400可预测长程样本；采集和计数准备放阶段5/5B | 400原始记录不等于达标；排除MODEL_JUDGED/REVEAL、首条/同日重复；验证损失采纳和版本回退有报告 |

## 4. 顺序刚性

- **0 → 1 已完成**：地基完成后一切 UI 才立得住。
- **裁决 → 3A**：已裁完（2026-09-30，16+1 项落台账），不再阻塞。
- **3A → 3B → 5**：重放骑在表示重做上；复习判题骑在掌握库上。
- **2 → 4B → 4E**：保留已完成工具面；GENERATE_FIGURE与转写/净化共用生图契约，由模型选JS/IMAGE_MODEL，全部实际模型派发进入共享ledger，不暗增请求。
- **4A → 4B**：排版挂在导出链路上。
- **4A → 4C、4B → 4D（新增，2026-10-11；2026-10-18接线修订）**：录入二期骑在录入统一上；B7/A10等新生图分支均依赖4E.1共享判定/后端及独立质量门，既有图像API作为后端复用。
- **3E → 5b（2026-10-19明确消费边界）**：3E 先定范围（改 spec §1.3/§3.2 + 队列装配），阶段 5 知识点卡片流后接——两条线动同一队列文件，先定后接防撞。
- **4C → 5B（新增）**：G8 回收链用切分器。
- **3E → 5B（新增）**：全库扫描是考前爬升与自适应出题的地基。
- **5 → 5B → 6（新增）**：纸质闭环骑在复习栏上；6 的设置整理与 6C 备份入口同区（6 先或同批，开工时注记先后）。
- **6B 依赖 4B（已完成）、可与 5B 并行（2026-10-11 放宽）**：docx/编辑/改卷不骑复习栏判定；原"5B→6B"过约束已撤。**6C 收尾排最后**。
- **D-4 → R6-E6 / G6 覆盖（新增）**：前置图密度决定 E6 前置补救与 G6 考纲匹配的覆盖面（覆盖依赖，非硬门）。
- **阶段5按§7.2拆批**：5a/5b/5c可按契约先做受控研发/隔离测试；真实数据启用均须3D硬门，5b另依3E。5a不为早开门新增临时题级证据体系，拆批不豁免退出门。
- **7 最后**：迁移链重置必须在所有 schema 改动完成后。
- **可并行**：阶段 2 与内核裁决/3A 可穿插；4A 与 3B/3C 后半可并行；**每次只并行一条**（共享工作树）。
- **3D → 阶段5真实数据启用硬门（2026-10-19修订）**：D-0/D-0b/D-1/D-2/D-3/D-5同包全过才能开放新增学习写入。允许契约完成后的开发与隔离测试先行；3D未收敛时真实启用挂起。不得用空归属或隐藏掌握页绕门，全量重放仍会派生KC归属。
- **平行工作流（新增）**：3D 坐标系可信 / 读侧语义闭合 / 薄纵向切片，三者与 3A 尾、3B、3C 并行，
  不阻塞内核线；各自产出报告与验收，不占代码线顺序。

## 5. 每阶段收尾约定（下一个模型不用重新摸）

1. 更新本文件对应行状态（⬜ → 🏃 → ✅）+ 在台账追加该阶段完成记录（含测试证据）。
2. 显式文件列表提交 + 推送。
3. 若中途发现新缺口：先入对应分线文档（fix-plan/补充卷/4B 设计），再回本文件补行——**不另起
   新总路线文档**。

## 6. 进行中注意事项

1. **阶段1/2、3A/3B的原范围已有完成记录**；历史号段54/55/56不再作为在飞占号。
   当前schema文件已到65；开工前读取当前版本常量与台账占号，不按旧交接段预留号。
   完成记录与AUD补修状态分列，不能由历史绿灯声称当前缺陷已经修复。
2. 共享工作树：提交显式文件列表；勿切分支、勿 amend/rebase。
3. Mimosa：扫描器长期 inconclusive——不得据此声称任何安全结论；SQL 一律参数绑定。
4. 硬纪律（详见 roadmap §0）：动投影公式必 bump `PROJECTION_COMPOSITE`；加模型输入字段必
   strip 空载体 + 4 用例；动 schema 必非破坏迁移；"待裁"值不得自行取值。
5. **2026-10-01 计划评审修订**（台账「修订批」裁决 26–30）：新增 **3D 坐标系可信**（含 D-0 前置
   绑定可信度字段）、**读侧语义闭合**、**薄纵向切片**；「看答案」补 KC 归因。**3D 是阶段 5 的硬门**。
   另更正三处事实：金标 Recall@5 **0.6444**（非 0.5444）、金标集现役 **v2 / 130 条**、
   **Wave 3 全部批次已提交**（仅余三项收尾验证门）。
6. **3D 的如实标注纪律**：绑定语义抽检的标注者是**模型**（无人类专家），报告必须写明
   「标注者为模型、单一来源、未做人类双复核」——不得表述为人类金标或等价物。

---

## 7. 2026-10-13审计任务归位与执行批次

### 7.1 任务位置、依赖与退出门（AUD-01～AUD-31）

依据：[2026-10-13全软件审计](research/2026-10-13-software-completeness-audit.md)。任务按未收口登记，已见并行代码改动的部分先核验证据再更新状态；源码确认、等价模型、真实设备和真实模型证据分开登记。本次只修改计划，没有修复软件，也没有代签新的产品裁决。下表为唯一归位索引，具体文件/用例写在对应分线，原KF/S/R编号保留。

| 任务 | 归属 / 优先级 | 需要处理的事 | 前置 / 触发 | 必须取得的验收证据 |
|---|---|---|---|---|
| AUD-01 | 3B维护 / P1先修 | 🟡 **2026-10-16 修法落地（`09f0c85c`）**：身份纳入会话/日分量（保住「同次重试 1 笔」）+ outbox 冲突显式处置；五类用例全绿 + 设备三实跑（3/3、2/2、5/5、17/17、2/2）；**设备走查未达（配额）**。原：小测生成、尝试、提交幂等身份及outbox重放 | 现有链即可修；阶段5前复验 | 同次重试1笔；同KC隔日同选项2笔；无唯一键冲突或旧事实覆盖 |
| AUD-02 | 3B维护 / P1先修 | 🟡 **2026-10-16 修法落地（`09f0c85c`）**：题面持久化——恢复读回原题原选择、不自动新出题/再出网；**损坏行重试死路未闭合（KD-41 登记）**。原：小测题面、选项、选择、生成请求一起恢复 | 与AUD-01身份契约对齐 | 已选未交后杀进程恢复原题原选择；不自动新出题/再出网；损坏状态有继续出口 |
| AUD-03 | 4A补修→4C前置 / P1先修 | 🟡 **2026-10-16 修法落地（`09f0c85c`）**：逐题提交 + SAVED 回执 + 自动进下一题（不再预标 SAVED）；仪器化 3/3 + 设备三 3/3 + feature:library 7/7（收尾补跑，夹具 producerVersion 修复）；**设备走查未达（配额）**；回执事务外窗口另立 KD-39（AUD-25）。原：切分多题真实提交与已录入状态 | 不等整卷自动化开工 | 选多题全部逐题入库；保存首题/返回/杀进程可接下一题；未提交不得标SAVED |
| AUD-04 | 3D D-3 / P1 | dense排序保留到讲题/归类最终消费 | 固定生产候选与评测口径 | 终端loader注入结果接受dense收益；纯词面/融合对照；主集≥0.90且逐章≥0.80，不用JVM量表代替最终门 |
| AUD-05 | 既有备份维护→6C前置 / P1先修 | 🟡 **2026-10-16 修法落地（`09f0c85c`）**：快照后按快照库反查引用资产并校验齐备（缺一即拒绝成包/恢复）、problemCount 取快照库；单测 + 仪器化；**设备未达**。原：数据库快照、资产清单、复制、校验一致性 | 现有备份即可修，不等6C扩展 | 并发录入/生图/GC时备份DB引用文件全在；快照计数一致；缺文件必须拒恢复 |
| AUD-06 | 4B补修→6B前置 / P1先修 | 2026-10-15 已补修可见备注、净化图URI及字节摘要的缓存键，批量保留每题图和备注；见[补修记录](research/2026-10-15-user-visible-fixes.md)；真实用户操作全链继续复验 | 与图资产持久引用复用 | 同模板改可见心得/净化图必换产物；未使用字段不误失效；相同内容命中 |
| AUD-07 | 3D D-2/D-5 / P1 | 14空点、22覆盖失效目标、表包/测试基线与晋升dense同版本 | 单写者窗口、D-0侧车折入 | 真实修缺口而非削门；内容门现算全绿；覆盖映射无悬空；成品/侧车/向量/清单同指纹 |
| AUD-08 | 4C / P1 | 切分金标、切残/漏题/多题/跨页验收门 | 门定义可独立；全链核验依AUD-03；自动入库前过门 | 门说明、数据集与逐题裁定可复跑；切残不得自动入库；失败页可补拍/继续 |
| AUD-09 | 4D / P1 | 🟡 **2026-10-17 持久化落地（`463a5dea`，schema 66）**：净化作业表（身份含源图 sha）+ 同指纹跳过不重复付费 + 失败落码 + 启动对账重试 + 晚到结果 SUPERSEDED；`CleanRedrawTaskRunnerTest` 17/0 + 设备 2/2 + 矩阵 13/0。**未收口**：触发范围回归、识别/重绘金标门、设备核（429 + KD-51 阻断）。原：净化触发范围、持久任务、失败/杀进程续跑及图题等价门 | 既有edits通道；AUD-08切分门独立 | 文本手写/图题/几何标注逐类验（未做）；终态可见（行状态机 ✓）；同操作不重复计费（同指纹跳过 ✓）；不合格图不替代正确题面 |
| AUD-10 | 4D / P1，KD-32承接 | crop/净化统一采样已见在飞改动；核覆盖并补精度、坐标、设备证据 | 先对照当前diff/测试；保留旧大图场景 | 新旧图混合批次无OOM；裁剪定位/指纹自洽；原图与采样图识别精度对照 |
| AUD-11 | 3B语义维护→5a/5c复验 / P1 | 🟡 **2026-10-17 三条裁定实施（`463a5dea`）**：① 判词粒度 = **锚定 + 归属落账**（整题判决要求 ≥1 条 accepted 证据锚在本题 KC 集；外部节点证据 → NO_VERDICT；快照记驱动证据）——遗留 KD-44（空 KC 集题永久阻塞 + 文案误导）；② 冷却 = **限 MODEL_CHAT**（小测真实作答不再被模型写挡住；反向亦然；UI 文案按真实拒因；校准分桶加通道维）；③ 答案键 = **可观察性先行**（IGNORE 可观察 + 审计口，纠正语义留 5B；KD-43）。测量与裁定依据：`docs/research/2026-10-15-aud-11-verdict-granularity-cooldown-answer-key.md`。**2026-10-20 批次15（未提交）**：PRD-02 代码级落地（schema 68 + `EVIDENCE_CORRECTION` VOID/REPLACE + 两轴键门 + 小测报告入口；`ChatEvidenceCorrectionReplayTest` 9/0、`AnswerKeyTrustPolicyTest` 5/0、`KernelWave0SchemaContractTest` 8/0）；instrumented/设备未跑，KD-43 保持 open（读侧键版本排序、CAS 生产未传入） | 先构造反例和分层测量（✓ T9）；语义变更按裁定实施（✓） | 错答案键可纠正/撤销/重放（**未做**；2026-10-19前移PRD-02，5B复用扩展；2026-10-20 代码级已有、设备未验）；前置懂了不当整题做对（✓ 锚定，单测）；同KC真实新作答不会无说明被忽略（✓ 冷却限通道） |
| AUD-12 | 6 / P2 | 已有学情页扩全库画像与薄弱点复习导航 | 阶段5闭环、3E扫描 | 无学习事实不伪造掌握；可解释支持范围；只读行导航到对应复习，不直接改事实 |
| AUD-13 | 6C / P2 | 🟡 **2026-10-17 6C-1（`463a5dea`）**：设置迁移落地（`settings.json`：保持率/考试/优化参数/提醒偏好；排除 Key/租约/投递历史；交换前版本校验 + 交换后写入、失败标 partial）+ manifest v2（app/内容版本戳）；`BackupRestoreInstrumentedTest` 25/0。**未做**：内容勾选（6C-2）、换机演练、旧包不兼容可读拒绝的端到端。原：个性化内容选择及保持率/考试/提醒设置迁移 | AUD-05（✓ 修法已落）；沿用.smbk/归档UI | 选项依赖闭合（设置面 ✓；内容面 6C-2）；设置往返 ✓；旧包兼容读 ✓ / >2 可读拒绝 ✓；API Key/授权租约不打包 ✓ |
| AUD-14 | 阶段2模型维护 / P1 | 本批已修 KD-30、AGF-01 动作交接、AGF-03 同实例取消；AGF-02 工具检查点/持久回执与幂等恢复需独立设计；协议/重试剩余项继续核验 | 先对照共享工作区diff/测试；Agent小修记录、KD-33；现授权/6次派发纪律不变 | 同请求语义跨路由对拍；截断可重试而预算不重置；合法tool轮可回填；中断后按持久检查点续跑；按协议分别报告 |
| AUD-15 | 工程维护及7 / P1先收红门 | 🟡 **2026-10-17 补（`463a5dea`）**：KD-40 关闭（生成器补「绑定时刻 = 生成时刻」说明 + 模板/单测同步）；KD-38 剩余解析期截断与流式前缀补齐（`472394ea`，不切代理对）。2026-10-16 本批（`6a69c82b`/`09f0c85c`）：`DenseTokenizer` 1 处 NewApi 改码点扫描（等价用例 7/0/0）、状态页诚实化（9 模块 lint + 证据新鲜度 + `JOB_STATUS` 无缺省 + run_context 绑定 + 三值 Overall；14 例单测）；`:feature:library:lintDebug`/`:core:data:lintDebug` **未由本批执行（未测）**。原：审计基线6个Lint错误、19项工具红；2026-10-15 已消除导出成果页5处NewApi，见[补修记录](research/2026-10-15-user-visible-fixes.md) | 随补修推进；最终收尾再次取证 | 不抬minSdk/削门掩盖红；所有模块纳入；旧classes/APK/XML不能报本次OK；未测门不报PASS |
| AUD-16 | 薄纵向切片 / P1持续验 | **2026-10-18 补记（十三轨批设备段）**：设备段**未能判定**——模拟器 DNS 全面不可用（非 429 非成功；重启两次含 `-dns-server` 未恢复）；KD-51 改后对照成立（强制全量安装 0 条连接饥饿）；KD-17 两条回归 17/0 + 6/0；T1 docx/T2 走查未达（模型门 + 库空）。2026-10-17 补记（十二轨批设备段）：KD-42 中断提示**设备已见**；T6 净化作业行真库出现（`user_version=66`）；feature:library 49/0、`CleanRedrawTaskInstrumentedTest` 2/2；KD-51 新观测；KD-37 UI 端到端/KD-38 前后对照**再次未达**（429）。原：真实模型及有数据的全圈、第二次复习、异常恢复与换机 | 可先记基线；成功门需相关AUD修复和可用已授权模型 | 155照片数据集按实际科目分批清点；任务/修订/证据ID可追溯；失败点如实写，不能只验空态 |
| AUD-17 | X，采集准备放5a/5B / P2 | 400可预测长程样本计数、来源与污染处理、校准对照 | 先有真实闭环；不制造样本凑数 | 原始/排除/可预测数分列；MODEL_JUDGED/REVEAL/首条/同日重复按实现排除；不劣采纳与回退实测 |
| AUD-18 | 3D受控研究 / P2 | grounding本地缺口到研究、审校、发布及关闭的生产驱动 | 复用controlled-knowledge-research设计；受D-5发布纪律约束 | 合法请求可驱动；拒绝来源不晋升；版本更新后关闭；无搜索能力显式停留，不假称已补库 |
| AUD-19 | 3D D-4 / P2覆盖项 | 前置图应有关系的覆盖、正确性与教学补救 | 沿用R10，非阶段5硬门 | 分科目标集合/抽验理由；无环/悬空；真实前置缺口能补救；不以盲目加边数验收 |
| AUD-20 | 阶段1维护及7 / P1测量 | 首装解析/安装资源、失败重试和真实性能测量 | KD-32剩余；当前成品/候选分别测 | 冷首装/热首开/失败重试计时和堆采样留存；真实设备启动/搜索/峰值门实测，NOT_MEASURED不算完成 |
| AUD-21 | 切片·判定链 / P1先修 | ✅ **2026-10-14/15 已完成并设备复验**（`0bdd651b`；双线各自修法均已入树：本线「身份层+兜底层」、Agent 小修线「命令绑定当前组合」） | — | 「重试」在该分支不再崩溃 ✓（crash buffer 全空、进程未重启）；新尝试不覆盖旧请求 ✓（尾缀 `:0→:3`）；重试后可见明确状态 ✓ |
| AUD-22 | 切片·判定链 / P1先修 | ✅ **2026-10-14/15 已完成并设备复验**（`0bdd651b`；含本线「结算读对会话 + 可见 + 锁逃生」与 Agent 小修线「选择提交读已完成计划」） | — | 设备完成一次讲题判定提交 ✓（`attempt_submission`/`review_log`(LOCAL_CHECKED)/推进收据各 1 行，会话 IN_PROGRESS(0)→COMPLETED(1)）；提交失败有可重试出口 ✓；提交链负向测试 ✓（`TutorChoiceSubmissionFailureTest` 4 例 + instrumented 解锁例） |
| AUD-23 | 切片·判定链 / P1先修 | ✅ **2026-10-16 修法落地（`038d50c0`）**：时间戳容忍比较（`sameEvidenceSnapshotIdentity`——同 id 且除时间戳外全等即复用旧行；身份/内容/归属真变仍抛）；KD-37 instrumented 2/2（设备一实跑）；**设备级 UI 端到端未达（配额）**。原：KD-37：「重新判定」出口确定性死路（快照身份稳定而时间戳每次新造 → `assessment_evidence_snapshot` 永久冲突） | — | 「失败 → 重新判定 → 成功」设备路径**未达（配额阻断）**；快照身份有测试钉住 ✓；不再出现"唯一出口必然失败"（代码层 ✓） |
| AUD-24 | 切片·讲题链 / P2 | ✅ **2026-10-16 修法落地（`038d50c0`）**：解析期截断（32/96/64，构造器不放松）+ 提示词声明并 bump（plan v19 / respond v26）+ `ModelTextBudgetExceededException` → 可重试 `OUTPUT_BUDGET_EXCEEDED`；JVM 用例（复核实跑）；**设备前后对照未达（配额）、触发频率仍未取样**；代理对切断与台账未登记 v19/v26 照录 KD-38 内。原：KD-38：讲题动作标签超 32 字符 → IllegalArgumentException → 整轮计划永久 INVALID_RESPONSE | — | 「超长标签不判死」用例通过 ✓；设备同一题失败率前后对照**未测（如实记为未测）** |
| AUD-25 | 切片·切分回执 / P2 | ✅ **2026-10-17 关闭（`463a5dea`）**：按 Fix direction ② 落地——读侧对账补偿（`reconcileCommittedQuestionDrafts` 同一写事务补写 SAVED 回执；复核页装配/逐题续做/启动各跑一次）；instrumented `SplitImportReceiptReconciliationInstrumentedTest` 5/0（设备实跑，含「提交后被杀 → 重启追平」）。原：KD-39：SAVED 回执写在提交事务**之外**——「题已入库、台账仍 PENDING」且无补偿路径 | — | 「提交后被杀 → 重启后台账追平」用例 ✓（instrumented 5/0） |
| AUD-26 | 切片·讲题判定 / P2 | ✅ **2026-10-18 关闭（`8335613f`）**：按裁定实施 pseudo 锚定自动结算（空 KC 集题判词结算到 `pseudo:*`、归属指向伪绑定行、队列推进；零用户参与；自纠=改绑后重放改挂真实节点）。原：KD-44：判词锚定让「KC 集为空或只含伪节点」的题永远拿不到判定 | — | 空 KC 集题正常结算 ✓（SettlerTest + 设备 `TutorJudgedReviewSettleInstrumentedTest` 2/0）；真·无判词仍 NO_VERDICT ✓ |
| AUD-27 | 设备·冷启动可用性 / P1 | ✅ **2026-10-18 关闭（`8335613f`）**：重建改「读相 + 分块短写事务 + 块间可取消」，锚点最后写语义保持。原：KD-51：冷启动知识库搜索特性重建占死 SQLite 连接池（单条 SQL 等 270s） | — | 强制全量安装 0 条 `unable to grant`（改前 270.2s）✓；快路径冷启 1075ms、采集保存不再被饿死 ✓（补记：indexed>=reviewed 门不筛 verification_status，无已知生产路径，见 KD-51 内） |
| AUD-28 | 4C·手动建题 / P1 | ✅ **2026-10-19 关闭（`759148ba`）**：A8 生产者落地（录入屏「手动建题」+ 空源草稿链 + 读取/提交空源分支）；设备全链 PASS（建题→入库→详情→导出 PDF）。原：KD-52：A8 只落 schema 半边（无创建入口、读取路径 fail-closed 硬崩） | — | 手动题全链提交 ✓（`created=true` 且 provenance=USER_TRANSCRIPTION；`ManualTranscriptionCaptureInstrumentedTest` 3/0 + `MistakePdfMissingSourceInstrumentedTest` 1/0 设备） |
| AUD-29 | 6C·备份可用性 / P1 | ✅ **2026-10-20 收尾关闭**：365MiB 自建包设备校验+恢复 1/0（39.045s，恢复后数据可读）；AUD-05 缺引用资产拒恢复门在 `BackupRestoreInstrumentedTest` 27/0 中真实触发；KD-56/KD-53 均关闭。原：KD-53 单条目解压上限 100MB < 大库体积 | — | 校验 ✓；恢复 ✓；AUD-05 专用门 ✓ |
| AUD-30 | 6C·备份写饥饿 / P2 | ✅ **2026-10-20 收尾关闭**：365MiB 设备对照成包期并发写 0 失败、最坏 <5s；`BackupCreateWriteGateInstrumentedTest` 2/0（独立快照期间写入口直接放行、Room checkpoint/snapshot 0 调用）；KD-54 关闭。残余写门覆盖/回退 checkpoint 问题登记 KD-62 | — | 备份期间无分钟级写饥饿（设备对照）✓ |
| AUD-31 | 6C·备份恢复可用性 / P0 | ✅ **2026-10-20 收尾关闭**：KD-56 fixed；`BackupLargeDatabaseScaleInstrumentedTest` 365MiB 1/0（39.045s）恢复不崩、数据可读；`BackupRestoreLifecycleInstrumentedTest` 3/0。残余非门控写/750ms 宽限登记 KD-61。**2026-10-24 批次19（KD-61 处置，open（收窄））**：图生成僵尸对账接既有写门（`FigureGenerationReconciler` 构造改收 `BackupWriteGate`）；恢复/清库关库前硬性 quiesce——取消整个 applicationScope+限时 2s join 全部子协程（`BackgroundWriterQuiesce`），四个 DataStore 工厂拆独立 scope 保 Phase 8 设置写，钩子异常中止恢复且不关库（交换前失败分支）；旁路四类残余（热区直写面/WorkManager 在途 SQL/UI 写/quiesce 超时路径）登记于 KD-61 条目。`FigureGenerationReconcilerTest` 7/0（修复环独立 K2JVMCompiler+JUnitCore 同版工具链）；`BackgroundWriterQuiesceTest` 4 例 1 竞态红未复跑（单测门第 3 轮 exit=1）。原：main 线程 `SQLException: Connection pool is closed` 崩溃 + 启动回滚（两次复现） | 批次 14 设备实测（首次在真实大库上执行 swap） | 大库自建备份在设备上完成恢复且不崩、数据可读 ✓ |

### 7.2 阶段5拆批与四层完成证据

- **5a 题级复习闭环**：学生三键、遮答案/判题限定、题级评分undo、备注回响；先完成AUD-01/02，AUD-11的反例与证据来源规则随本批验。退出门为三档产生不同证据、撤销还原调度、答案暴露/辅助作答不伪装独立、题面换版不写错版。
- **5b 知识点卡片和节奏**：同栏第二入口、专项小测、F8预算/F9追赶/F17配额/F18模式/F16重教，依赖3E。保留既有保持率/考试入口；验收全库捞回、预算封顶、跳过/不足材料出口和跨会话再次作答。
- **5c 讲题与自动标注增强**：错因自动写/检索/聚类/撤销，E11历史检索、多解/解析复用/订正摘要、M2与只在需要时询问备注；承接AUD-11粒度/答案键反例，前置补救覆盖跟D-4开放。备注原文不出网；自动化许可继续按D-1及裁定39执行。
- **三个子批的真实数据启用均受3D硬门约束；3E队列依赖由5b承担**。PRD-02/03契约确定后可先做受控开发/隔离测试，不在用户库预写学习记录。阶段6/5B依赖完整阶段5时仍须三个子批共同收口。
- 状态分四栏登记：**代码接线 / 模块命令 / 设备有数据全链 / 真实模型质量**，每栏列HEAD或包指纹、run、样本、结果与未验证项。原✅保留历史范围；任一新增AUD未完成仍为⬜；不能把四栏合并为一枚完成勾。

### 7.3 当前排程和不变量

1. 已有链补修与工程红门优先：AUD-01/02/03/05/06/14/15；AUD-04/07按KB单写者窗口推进，AUD-16持续记录真实断点。
2. 3E按原依赖推进；5a/b/c按§12区分开发/启用，3D门不过不开放新增学习写入，5b另等3E；4C/4D先补纠错和门，再开放自动化。
3. AUD-12/13/17/18/19/20在各原归属内承接，不建立第二总路线。Q4迁移链重置最后做；6C收尾仍最后，但现有备份正确性AUD-05不能拖到6C。
4. 未定产品数值/权重不在本次维护中代签。图像和切分的门定义是独立设计交付，输出阈值、分层样本、严重错误判定、统计口径和失败路由；沿用已有绑定0.95、检索0.90/0.80，不能互换。
5. 2026-10-13审计快照：JVM 2486/0、双Debug组装通过；Lint 6错误、工具691中19失败/错误、候选14空点仍红。本次计划修改不代表软件修复；后续实现需取新证据。
6. **共享工作区在飞交接（本轮末次检查）**：AndroidCanonicalAssetVault已出现三路径统一采样，三家wire协议已出现截断分流，BatchSplitRecognizer/采集模型及sync_rebind_decisions也有未提交改动。AUD-08/10/14/07开工先对照这些diff及其新验证，只补剩余项；未提交改动不能直接当完成，不重置或覆盖他线文件。

## 8. 2026-10-18 绘图路线实施次序

4E先建立共用prompt/proposal/coordinator/ledger并接所有生图入口；复用现有图像后端验通，再并列纳入过门的JS后端。JS工程仍按运行库/隔离→场景/排版→工具→存储→UI/导出依赖实施。4B历史勾选只代表旧图像API/导出成果，不代表4E完成；4D的B7/A10均消费共用管线，A11确定性预处理继续复用，方法由模型按要求选择。所有正式任务仍未勾选。

按真实能力交付：4E.1统一全部入口、源证据和静态后端/PDF，4E.2扩本地交互/原生表单，4E.3扩复杂源与保真质量；阶段不按用途指定方法。保留复核要求：SSE工具参数终态、Frame/checkpoint分离、题面/讲解导出角色、过期watchdog防护、离线本地执行与版本降级，见[复核报告](research/2026-10-18-flexible-js-drawing-plan-review.md)，其旧固定路线已被最新共享提示词取代。

## 9. 2026-10-18 最新统一生图裁定

JS与图像模型均是生图方法，所有转写/净化/重绘/讲解图入口统一判定、生成、验收和记录，由模型按共享提示词灵活选择；不再B7固定JS、A10固定edits。4E.1先接公共coordinator/ledger/prompt，再并列启用过门的后端；4E.2/3只是交互/保真扩展。方法切换在同operation预算内允许，不新增逐图确认。详见[统一提示词](current/figure-generation-prompt-policy.md)。旧阶段/审查中的固定路线及“JS不计生图”结论被本条取代，实际旧实现记录保留。

## 10. 全入口生图接线排期（2026-10-18追加）

可执行清单见[4E计划第10节](superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md#10-全入口接线任务与后续阶段承接)，WG编号是接线验收身份，全部待实施：

| 承担阶段 | 接线任务 | 收口要求 |
|---|---|---|
| 4E.1公共底座 | WG-01装配/后端/ledger，WG-08持久化/恢复 | 实际共享实例与账本、无装配环、两方法引用/备份/恢复可用 |
| 4C/4D消费4E.1 | WG-02转写/源图，WG-03净化/去手写，WG-06保存/详情/重绘 | 入口→proposal→coordinator→产物→当前revision全链；不把TEXT_ONLY当无需生成的硬规则 |
| 4E.1讲题与兼容 | WG-04工具/消息，WG-05旧attachedImages/显式重生成 | 六类呈现可消费；历史打开0外发，显式重生成走共同判定 |
| 4E.1导出 | WG-07 PDF/DOCX/打印/分享 | 共用已验证成果，DOCX嵌入真实图，默认题目区不含答案图；导出0模型派发 |
| 阶段5/5B，有图分支 | WG-09复习/出题/改编/回收承接 | 消费共享能力并满足本阶段原前置门；无图分支不被额外阻塞 |
| 各阶段完成门 | WG-10三处旧图像调用迁移、真实Provider与证据台账 | 代码装配/模块检查/设备全链/真实质量分栏；未验证项不冒充完成 |

4E.2在共享接线上扩交互/原生表单，4E.3扩复杂源/保真；两者不重新分配用途到固定后端。旧4B已完成记录不能抵扣WG新增任务。

## 11. 后续计划文件与剩余工作入口（2026-10-18同步）

[后续执行清单](../.zcode/plans/plan-2026-10-18-followup-figure-wiring.md)已把WG任务分配到4C/4D、5a/5b/5c、5B、6B-2/3、6C-2/换机及7；[新阶段spec](research/2026-10-11-new-stages-spec.md)与[6C-2设计](research/2026-10-17-6c2-content-selection-design.md#7-统一生图接入后的闭包补充2026-10-18)同步承接。此文件仍是唯一总路线，子计划不另设产品裁定或复写完成状态。

后面要做的工作按本表/§7当前状态分为：①已有链缺陷与工程/设备证据补齐；②3D坐标系/检索/同包晋升收口和3C自动化剩余；③4C录入余项与4D/4E统一生图；④原门满足后的5a/b/c复习闭环和5B出题回收；⑤6B编辑/自然语言改卷、6C内容选择/换机，以及6档案设置扩展；⑥7全量收尾与迁移重置；⑦X合格真实样本驱动校准。3E第一批、6B-1导出半边、6C-1设置迁移已有记录，后续只补剩余项及设备证据，不重做。

同步不代表实现完成：4E最新在飞进度见§12末尾，JS及统一全链仍未验收；阶段5开发/启用规则已由§12修订，真实数据启用仍挂起；G15随WG-07交付，学情确定性图表继续原生绘制，有图分支才增加共享生图能力依赖。

## 12. 2026-10-19 实际产品研究后的后续排期

依据：[研究报告](research/2026-10-19-product-readiness-research.md)、[处理设计](superpowers/specs/2026-10-19-product-readiness-design.md)、[实施清单](superpowers/plans/2026-10-19-product-readiness-followup-plan.md)。本节更新未来排程，不改写上表旧日期/提交的事实。PRD为跨阶段任务身份，WG仍是统一生图唯一接线清单。

| 先后 | 原归属 | 新增/提前的工作 | 启用/完成门 |
|---|---|---|---|
| A | 3B维护、4C、既有6C | PRD-02答案键来源/纠正/撤销/重放（KD-43从5B提前）；PRD-03已存原题修订；PRD-07大库备份/写饥饿。**2026-10-20 收尾**：PRD-02 设备 24/0（含身份粘性自动补偿/UNKNOWN REPLACE/跨会话隔离，KD-43 关闭）、PRD-03 设备 5/0+2/0、PRD-07 设备 365MiB 1/0+生命周期 3/0+独立快照 2/0。**2026-10-23 批次18**：6C-2 内容选择 v3 的写侧裁剪/空间预检/恢复校验落树，365MiB 全选/子集各 1/1（D1 材料）；当前最终树测试例数已增加、未复跑；KD-54/56 关闭状态不变，KD-61 仍 open | 原事实留存、有效证据全重放一致、≥365MB往返与并发写过门（✓ 设备；AUD-05 27/0 已触发） |
| B | 4C、4B/6B-1、阶段2 | PRD-04原生PDF/DOCX格式选择和KD-55；PRD-06空态/手动题/能力；PRD-08草稿反例。**2026-10-20 收尾**：PRD-04 格式 sheet（library 4/0 + app 3/0 设备）；PRD-08 草稿 CAS+版本 JVM 8/0（真实进程重建未跑）；PRD-06 空态半边设备通过、能力半边顺延。**2026-10-21 批次16**：PRD-06 能力半边代码级落地——`TutorCapabilitySnapshot` 作为唯一广告源（native schema / Route B / admission / 执行端同源），`ADD_TO_REVIEW_PLAN` 不广告/不准入（旧卡仍如实说“不能自动做”），`GENERATE_FIGURE` 仅图像后端就绪时广告；**收尾：单元/设备能力快照 1/1，PRD-06 能力半边落地** | 无Key本地任务可达、0模型派发、未实现工具不广告；草稿风险先复现（✓ JVM；真实进程未验；单测门 3,029/0） |
| C | 5a，随后5b/5c | PRD-05无模型三键与undo；受控研发/测试先行。**2026-10-21 批次16（代码级）**：三键/undo/无模型已存答案数据层+UI+接线落树；无 schema VOID 哨兵（DB 68），更正+会话回退+`review_log.scheduling_eligible=0` 同事务；自评永非独立、原档/调度档分列 | 真实数据启用保留完整3D门；5b另依3E，不新增QUESTION_ONLY过渡体系；**收尾：单测门 3,029/0、设备 24/0；真实数据启用仍挂起** |
| D | 4D/4E | WG全入口接线+PRD-09真实图义/成功率/延迟成本门。**2026-10-21 批次16（代码级）**：WG-04a SSE tool_calls 终态门 + 预算/fail-closed；WG-04b 请求 schema 18（TutorVisualContext、method/program/imageOperation/imagePrompt、JS 仍不广告）；WG-06/07/08 设计件落盘（69 留批次17）。**2026-10-22 批次17（代码级 + 设备）**：WG-08 schema 69 两审计表/四追加列/迁移、operation/attempt 仅审计、GC/备份按 attempt 产物 pin、启动僵尸 RUNNING 对账；WG-06 保存同事务复制/详情分区/显式重绘 CAS+SUPERSEDED；WG-07 PDF/DOCX 角色过滤与解析区图；单测/强门 exit=0、设备复验通过（PRD-05 7/7 等，见记录 §8） | 两方法同策略、先静态纵向再全入口；工程门与真实质量分别通过（批次16 单测门 3,029/0；批次17 单测门第 3 轮/强门第 1 轮 exit=0 为脚本自报；真实 Provider 未验） |
| E | 5B、6B-2/3、6C-2、6 | PRD-10接纠错/修订/共享图/内容闭包。**2026-10-21 批次16（代码级）**：6B-2 原生“编辑卷面”入口（未编辑同快照/指纹，编辑后重算、不反写原题）；6B-3 白名单改卷命令解析+原子 applier（只改文档副本、整批拒绝保留草稿）；模型端口尚未在 app 注入。**2026-10-22 批次17**：6C-2 机器清单按 69 重算（closureVersion=2、86 表/2 视图/92 FK、78 项裁剪顺序、20 条软引用；三项待裁定保持 OPEN，未实现）；WG-07 解析图/角色过滤落 PDF 与 DOCX，卷面副本保留视觉引用。**2026-10-23 批次18（6C-2 落地）**：域选择/闭包（仅 9/13/15，0/8 与未闭合拒绝）、schema 69 机器清单、快照裁剪器、manifest v3、恢复交换前逐表对拍+派生索引恒裁+重启后按科重建、设置页勾选与恢复前确认、KD-55 有界读入；单测/强门第1轮 exit=0（工单材料），D1 设备材料见 6C 行；设计件 §9.9 仍 OPEN、恢复 S3/写侧负计数等低危未闭合，故记“代码级完成、验收部分 UNVERIFIED” | 各原阶段业务门；5B扩回收不重建纠错，6B不反写原题（6B-2 设备 8/8；6B-3 仅 JVM/无模型注入；6C-2 代码级完成，最终树设备未复跑） |
| F | 7/X | PRD-11学生任务矩阵、最终工程门/迁移重置与合格样本。**2026-10-25 批次 20 补修行（beta 准备件）+ 阶段 7 前置件注记**：16KB/arm64 构建面收口（abiFilters=arm64-v8a+x86_64，ZIP/ELF 层实测合规、省 ~23.1MiB；代价=32 位设备不可安装，产品已呈报）、beta 本地反馈闭环落地（崩溃/协程/ANR 本地账本+退出对账+SAF 诊断包导出，零外发零新依赖零 schema）、AI 生成内容显式标识 G3（PDF/DOCX 导出图注；**G1/G2 P0 热区未落待下批**）、PRD-09 harness 可先行半边（fake 驱动；真实质量 UNVERIFIED）——阶段 7 前置件（16KB/反馈闭环/标识）半数落位；仍缺：G1/G2、ps16k 模拟器装载冒烟、API23/arm64 真机矩阵、R1/R4 RELRO 消费形态收口、真实 Provider（批次记录 §6） | 未测项保持UNVERIFIED，文档完成不等于产品完成 |

AUD-11历史表内“纠正留5B”记录的是当时实现范围；**最新未来排期改由PRD-02前置实现**。原题修改与文档副本编辑分开；本地比较不等于答案键可信，未核验/争议键首批只保留会话反馈，不自动扩张数值掌握证据。已有旧证据不批量删除。AUTO_VISIBLE继续由宿主门验证，与ASK动作分别执行。

4E.1/2/3、原3D阈值与X门不变。上述新增完整退出门尚未通过，已有在飞实现按§12.1复用补缺；本轮只完成研究与计划修订。后续执行入口同时更新到[承接计划](../.zcode/plans/plan-2026-10-18-followup-figure-wiring.md)。

### 12.1 最终工作树复核：已有实现先复用

2026-10-19研究期间共享源码继续变化。上表带2026-10-18提交的记录保留历史含义；下列静态发现更新后续任务基线，**不等于本轮运行测试或关闭KD/WG**：

- 4C/A8：startManualDraft、无照片命令及采集按钮已有在飞实现。PRD-06改为复用并验无源读取/提交/恢复；KD-52不再按“完全没有生产者”重新设计。
- 6B：Tutor编辑后导出回调已有，6B-2不再笼统视作无代码；原生列表/详情仍默认格式导出，PRD-04补原生PDF/DOCX选择。DocxImageSource已有同字节hash/长度检查，还须核验必需图失败策略与真实交付。
- 6C：数据库1GiB/其他单条100MiB/累计2GiB预算已有；PRD-07先复用并验≥365MB往返。Room写连接快照与旧fallback仍需处理，不能据KD-53局部改动关闭KD-54。
- 4E：已有统一policy/coordinator和派发账本相关代码；当前执行器仅支持IMAGE_MODEL，JS明确UNAVAILABLE。按WG逐项对照接线再补缺；双方法、全入口、恢复/质量证据仍待验。

完整范围与限制见[研究§1.1](research/2026-10-19-product-readiness-research.md#11-最终核验时的工作树增量)。后续开工重新核HEAD/schema与文件占用，不能照研究起点重复实现。

## 13. 2026-10-19 UI使用逻辑与视觉改版

用户补充要求：现有UI存在不符合使用逻辑与视觉美感的问题，必须纳入后续计划。依据[实际页面审查](research/2026-10-19-ui-ux-audit.md)、[UI改版设计](superpowers/specs/2026-10-19-ui-ux-redesign-design.md)、[UI实施清单](superpowers/plans/2026-10-19-ui-ux-redesign-plan.md)，覆盖UX-01～14与UI-00～11。

导航明确为五个功能分区、四个底部入口（复习/智能体/错题本/我的）；学习档案与设置在我的分组。“五栏”不是要求五个底栏按钮。旧四栏文档中的讲题名称、知识点筛选、复习入选原因等不重新覆盖后续裁定；本次落实已推迟的排版与编排。

| 优先顺序 | UI任务 | 对接现有工作 | 退出证据 |
|---|---|---|---|
| A | UI-00/01/02基线、视觉令牌、导航状态；UI-03/04列表/录入 | PRD-01/06/08，4C已有能力 | 第一屏能找题/继续任务，空态有下一步；对比度、返回与草稿 |
| B | UI-05原题编辑/纠错；UI-09导出；UI-10我的/恢复 | PRD-02/03/04/07，不依赖3D或JS才可做布局 | 版本/纠错回执、两格式交付、恢复状态与业务一致 |
| C | UI-06复习卡片；UI-07智能体 | PRD-05及原3D/3E门；智能体布局可独立先行 | 自评/曝光/撤销不误记，配置返回不丢稿，长文不抢滚动 |
| D | UI-08公式/图/表单 | WG-04/06/07/09、PRD-09；已有内容先修，新能力按4E开放 | 图义/可读性分验，同产物多入口一致，消费0派发 |
| 横贯 | UI-11适配、无障碍、任务走查 | PRD-11、阶段7 | 同数据前后图、小屏/大字/TalkBack、完整状态与真实回执 |

先改流程和信息层级，再统一视觉，最后补设备与用户走查；不以加圆角/换颜色替代任务闭合。新编辑/评分/恢复功能只在相应业务验收后可用；壳、原型和静态截图不记软件完成。此次未修改产品源码，UI实施状态保持待验收。

### 13.1 2026-10-20 仅修现有UI逻辑漏洞

[补充源码审查](research/2026-10-20-existing-ui-logic-audit.md)确认UL-01～09，任务进入[UI计划第5节](superpowers/plans/2026-10-19-ui-ux-redesign-plan.md)。优先修附件/卷面旧读串目标、编辑重建丢稿、完成误报保存、保存中假取消和答案开关矛盾，再修列表失败状态、恢复回执和归档弹窗互斥。

范围只修现有行为，不新增功能、业务规则或流程，不增UI功能包，不把这些本地修复挂到JS/3D上线之后。原题编辑与原生格式选择已有源码，复用并补回归，不按早期“缺入口”描述重建。当前只是源码审查与计划增补，设备复现、修复及产品验收尚未执行。

### 13.2 2026-10-20 现有页面使用阻力细化

[UF-01～12审查](research/2026-10-20-ui-usability-friction-audit.md)基于专用模拟器审查包与源码，覆盖数据库占用命名、维护/危险操作分组、配置状态、考试日期、导出参数/编辑/交付、选题搜索、档案浏览、批量取消、本卷移除措辞与长页返回。进入[UI计划第6节](superpowers/plans/2026-10-19-ui-ux-redesign-plan.md)及[设计第8节](superpowers/specs/2026-10-19-ui-ux-redesign-design.md)，不新开功能包。

本批是既有UX任务的具体化，UL逻辑先修；不新增业务规则、页面、自动保存/外发或额外确认。审查包已具备复习空态录入入口，旧观察保留历史含义；包与当前HEAD未完整映射，非空/大字/交付仍待设备验证，12项改进尚未实施。

### 13.3 2026-10-20 编辑、拆分续做与图片展示续查

[UF-13～19源码审查](research/2026-10-20-ui-flow-followup-audit.md)进入[UI计划第7节](superpowers/plans/2026-10-19-ui-ux-redesign-plan.md)与[设计第9节](superpowers/specs/2026-10-19-ui-ux-redesign-design.md)：UI-04先修显式任务漂移、准备中非法操作、缺草稿假收尾和数量/图标含义，再修编辑清空回弹；UI-08/11修切图首帧旧位图和Ready后本地解码空白。

沿原12个UI工作包、PRD-06/08/09与WG消费层承接，不增业务功能/页面/模型调用/自动保存/额外确认，不放宽原校验或仓库条件。JS/IMAGE_MODEL同产物消费规则保持一致，纯本地修复可先行。七项仅完成源码审查与计划细化，设备复现和实现未完成，不能据此关闭UI/PRD/WG退出门。
