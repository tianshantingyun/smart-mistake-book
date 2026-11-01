# 批次 25 T5：台账/契约状态同步——产出核对清单（2026-10-27）

- 执行轨：T5（台账维护员；只改 docs，不跑 Gradle、不做 git 写操作、不碰热区）
- 计划：`.zcode/plans/plan-2026-10-27-batch-25-race-warmup-queue.md`（T5）；权威裁定源：`docs/research/2026-10-26-batch-24-record.md` §9 主会话收口（`:396` 起第二块——基线 10.2-9 明确 `:389` 的第一个 `## 9` 是记录员验证，不是裁定）
- 落点：`docs/known-defects.md`（整文件 M，见 §6）、`docs/current/visual-generation-contract.md`（:72 单行内两处 + 标题括注）、两个证据索引的行号勘误；本清单
- 时点：HEAD `d783ae84`（未变）；本轨开工即脏树（202 M + 471 ??，他线在飞），与本批落点重叠的脏文件在 §6 逐个注明归属

## 1. 任务一：KD-66/76 与契约 :62/:64/:72 按批次 24 设备证据 + 本批 T1/T2/T3 落地更新

### 1.1 批次 24 设备证据的台账落点（核对了什么、改了什么）

| 证据 | 台账落点 | 本轨动作 |
|---|---|---|
| E-01/E-01b（隔离 6/6 ×2，`evidence/2026-10-26-batch-24/step01-visualjs-isolation.md:124`、`step01b-visualjs-isolation-rerun.md:102`） | KD-66 批次 24 设备轨 Progress（`known-defects.md:2607`）；契约 :72 | **已在盘（批次 24 复核 #2/#18 已回填）**：本轨逐处复核标记齐全，无新增残留（见 §2 漂移①） |
| E-02（KD-70 20/20、P2 绿，`step02-quickjs-oom.md:141`） | KD-70（标题 + 收口 Progress） | **本轨改**：标题 open→closed、收口 Progress 落盘（§2 漂移②） |
| E-03（release 冷启动 ×2，`step03-r8-release-smoke.md:174`） | KD-69（标题 + 收口 Progress） | **本轨改**：标题 open→closed、收口 Progress 引 E-03（§2 漂移③） |
| E-04（16KB 3/3，`step04-page-size-16k.md:107`） | KD-74 关闭段补引 | **本轨改**（§3） |

### 1.2 本批 T1/T2/T3 落地（树内实读，2026-10-27）

本轨按文件与 KDoc 自述转录（代码属 T1/T2/T3 轨的落点，本轨未改任何代码；执行证据待设备/门禁轨，共享纪律下本轨也不跑）：

| 轨 | 树内事实（`git status --porcelain -- <路径>` 实读） | 台账回填 |
|---|---|---|
| T1 | `M app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt`；`?? app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsHandshakeRaceInstrumentedTest.kt`（KDoc 自述：钉 markConnected/markDead 交错不变量） | KD-66 新增「批次 25 T1 补充」段（`known-defects.md:2611`）：确定性交错用例已落代码层、执行证据待设备/门禁轨、不改变 hard+500 ms 关闭门 |
| T2 | `M VisualJsService.kt`（onCreate warm-up + 单调 `ready` + warm-up 失败 killSelf）；`M VisualJsProtocol.kt`（`PROTOCOL_VERSION` 1→2）；`M app/src/main/aidl/.../IVisualJsService.aidl`（`isReady()`）；`M core/data/.../QuickJsVisualExecutionEngine.kt`（`warmUpNativeRuntime`） | 契约 :72 warm-up 子句改写 + 标题括注（§1.3）；KD-76 新增「批次 25 T2/T3 Progress」（`known-defects.md:2827`）：①方向代码层已落，③④⑤设备判据未动、⑤加载态（LOADING/有界重试）未落（本轨 grep `LOADING` 于 VisualJsClientEngine.kt 零命中）、**KD-76 保持 open、设备验证前 JS 不广告** |
| T3 | `?? app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsFrameQueue.kt`（1 在飞 + ≤2 待绘 + latest-wins + BUSY 保帧 + 跨 program FIFO；旧 `executeMutex` 整段串行消除——`grep executeMutex` 仅剩"旧实现"注释） | 契约 :64 的落地状态段——**由 T3 线在契约 :72 内自行回填**（本轨开工后该行已被 T3 线改写，含"队列/背压已按 §64 落客户端层（批次 25 T3…）"）；本轨未重复回填，只在其后补 warm-up 子句与标题括注 |

### 1.3 契约 :72 的本轨改动（两处，均在同一行内）

1. 标题括注：`终止层落地状态（2026-10-26 批次 22 T1 落层；批次 23/24 设备复跑回填；批次 25 T2 warm-up/ready 与 T3 队列/背压代码落层（设备验证前不改变广告门）；…）`。
2. warm-up 子句：原文「冷启动首个 execute 的 1500 ms watchdog 误杀（E-01d）仍需 warm-up/ready，本批未修（…）」改为「…缺口已登记 KD-76…；warm-up/ready 已按 KD-76 ①方向落服务端代码层（批次 25 T2，仅代码落层、无生产装配、设备复跑 UNVERIFIED）：…`isReady()`（`PROTOCOL_VERSION` 1→2…）…；KD-76 的设备判据…仍未验证，**KD-76 保持 open、设备验证前 JS 不广告**」。
3. :62/:64 本身是处方句，其"当前状态/偏差"登记在 :72 内（启动面审计未 dumpsys→P-08；队列→T3 已回填），两行处方文本本轨未改。

## 2. 任务二：批次 24 复核指出的状态漂移逐条修

| # | 漂移（出处） | 本轨处置 | 证据 |
|---|---|---|---|
| ① | KD-66 旧 5s/1s/8.5s 与 UNVERIFIED 文本（批次 24 记录 §5 #2，标"已修"） | **逐处复核，未发现无标记残留**：旧值只存在于带时点标记的块内——`:2583`（"批次 22 时点；已被批次 24 Progress 取代…**8.5 s 不是当前值**"）、`:2596`/`:2607`（"supersedes 上方…UNVERIFIED"）、契约 :72（"批次 23 时点"+"旧 UNVERIFIED 作废"）；命令见 §5 | `git diff` 前后该四处原文（grep "8\.5\|5000\|UNVERIFIED"，§5 命令 V-2/V-3） |
| ② | KD-70 仍写 open（基线 10.2-1：批次 24 记录 §9 `:403` 已裁定关闭） | 标题改 `closed 2026-10-27（批次 24 主会话收口裁定，记录 §9）`；三处历史"待主会话裁定"句尾加时点标注；新增收口 Progress（`:2713`）：引 E-02 判定行 + **精确 `mallocSize` 未打印（EXACT VALUE UNVERIFIED，断言只证 ≥15,099,495）**、x86_64/4 KB 单 ABI、98 ms/10 s 协作预算风险，缺口按裁定"另立"归 P-02/R-03 与 R4 `2026-10-27-arm64-16kb-coverage.md` | `known-defects.md:2686`（标题）、`:2713`（收口） |
| ③ | KD-69 仍写 open、未引 E-03（同上，裁定 `:404`） | 标题改 `closed 2026-10-27（批次 24 主会话收口裁定，记录 §9）`；`:2678` 句尾加时点标注；新增收口 Progress（`:2680`）：引 E-03（release ×2 ok/COLD、9 类签名 0，`step03-r8-release-smoke.md:174`）+ E-04（internal，`step04-r8-smoke.md:142`）+ 裁定原文；覆盖缺口（生产签名 UNVERIFIED、release 中文 OCR 未跑、`usage.txt` 未穷举、`ExportPdfWorker` 未单独触发）如实保留 | `known-defects.md:2666`/`:2678`/`:2680` |
| ④ | 00-index 行号漂移 | 两处都修（见 §4） | 批次 23 索引 `:67`；批次 24 索引 §4 `:77`、§7 `:130-131` |
| ⑤ | KD-76 Impact "每个新装/重启用户"外推（批次 24 记录 `:137`，§5 #8） | 收窄为证据口径：失败形状已被 E-01d 实捕一次（一台冷启动模拟器、负载 5.25、attach 4.6–7.5 s，`step01d-visualjs-isolation-coldboot.md:61-72`）；"仍可能"有证据、"每个用户"是外推；不得当每例必现、也不得因沉降态复绿而排除 | `known-defects.md:2819`（Impact 段） |

## 3. 任务三：KD-74 关闭段按批次 24 复跑补引用

`known-defects.md:2784`（KD-74 批次 24 T3 收口 Progress）："关闭不等于设备门全绿"句内三状态加 **"均为批次 23 时点状态"** 标签，并就地补批次 24 复跑引用——T1 abandon 红→E-01/E-01b 转绿（6/6，`step01:124`/`step01b:102`）；KD-70 P2 红→E-02 转绿（20/20，`step02:141`，且已于 2026-10-27 裁定关闭，记录 §9 `:403`）；arm64 16KB 运行时至本轨时点仍未执行（跟踪去向：P-02/R-03 + R4）。关闭判定本身不动（R4 §6.3 红绿中性）。

## 4. 任务二④：00-index 行号漂移（两处）

1. **批次 23 索引**（`evidence/2026-10-26-batch-23/00-index.md:67`）：KD-74 引用 `:2744`/`:2750` 是落盘时点值，批次 24/25 插入后漂移——按 2026-10-27 实读改为 `:2774`（条目标题）/`:2780`（Fix direction），原时点值保留在括注内（step 文件不改历史行，索引是映射文件可更新——沿用批次 24 索引 §9 的同款裁定）。
2. **批次 24 索引**（`evidence/2026-10-26-batch-24/00-index.md`）：
   - `:131`（§7 回链检查）：原 `:2607`/`:2676`/`:2705` 与标签 **KD-70/KD-69 错位**（`:2676` 实为 KD-69 的 E-03 回填、`:2705` 实为 KD-70 的批次 24 Progress）——勘误为 KD-66→`:2607`（未漂移）、KD-70→`:2709`、KD-69→`:2678`（后两者因本轨在 KD-69 条目插入收口 Progress 各 +4 行），并在行内保留错位说明。
   - `:77`（§4）与 `:130`（§7）的 KD-74 `:2768`/`:2778` → 现值 `:2774`/`:2784`（记录时点值保留括注）。
   - §9 待办清单内的历史行号未改（该节是已完成的待办记录，文末已有"回链完成"注记）。

## 5. 本轨实跑的验证命令与结果（全部本轮执行）

```bash
# V-1 漂移定位：KD 标题与收口段的最终行号
grep -n "^## KD-66 \|^## KD-69 \|^## KD-70 \|^## KD-74 \|^## KD-76 \|^## KD-77 " docs/known-defects.md
#   → :2573 open / :2666 closed / :2686 closed / :2774 closed / :2811 open / :2831 open（他线 KD-77）
grep -n "Progress（2026-10-27 主会话收口裁定" docs/known-defects.md   # → :2680（KD-69）、:2713（KD-70）

# V-2 漂移①复核：旧值/UNVERIFIED 是否全带时点标记
sed -n '2573,2615p' docs/known-defects.md | grep -n "8\.5\|5000\|5_000\|≤1000\|UNVERIFIED"
#   → :2583/`:2596`/`:2607` 等命中全部位于"批次 22/23 时点、已被…取代"标记块内；无裸残留
grep -n "UNVERIFIED\|8\.5\|5000 ms" docs/current/visual-generation-contract.md
#   → 契约仅 :72 命中，均为"旧 UNVERIFIED 作废"或批次 25 T2/T3 的"设备复跑 UNVERIFIED"如实表述

# V-3 契约改动逐字核对
grep -o "warm-up/ready 已按 KD-76 ①方向落服务端代码层（批次 25 T2[^；]*" docs/current/visual-generation-contract.md  # → 命中
grep -o "队列/背压已按 §64 落客户端层（批次 25 T3[^）]*）" docs/current/visual-generation-contract.md               # → 命中（T3 线回填）
grep -o "批次 25 T2 warm-up/ready 与 T3 队列/背压代码落层（设备验证前不改变广告门）" docs/current/visual-generation-contract.md  # → 命中

# V-4 T1/T2/T3 树内事实
git status --porcelain -- app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs \
  app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual app/src/main/aidl
#   → M VisualJsClientEngine.kt / VisualJsProtocol.kt / VisualJsService.kt / QuickJsVisualExecutionEngine.kt / IVisualJsService.aidl；
#     ?? VisualJsHandshakeRaceInstrumentedTest.kt / VisualJsFrameQueue.kt
grep -n "isReady" app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/IVisualJsService.aidl   # → :38 boolean isReady();
grep -n "LOADING" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt  # → 0 命中（⑤加载态未落，如实登记）
grep -n "executeMutex" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt  # → 仅剩"旧 executeMutex"注释（整段串行已消除）

# V-5 权威裁定与引用目标存在性
grep -n "KD-70 关闭\|KD-69 关闭\|KD-66 保持" docs/research/2026-10-26-batch-24-record.md  # → :403/:404/:405（§9 第二块，:396 起）
grep -n "^| P-02\|^| R-03" docs/research/2026-10-26-batch-24-4e2-admission-checklist.md   # → :124/:226（收口 Progress 的缺口跟踪去向真实存在）
grep -n "2744\|2768" docs/research/evidence/2026-10-26-batch-23/00-index.md               # → :67（已改为"落盘时点…漂移至…"格式）

# V-6 终检：diff 体量与一致性
git diff --stat -- docs/known-defects.md docs/current/visual-generation-contract.md \
  docs/research/evidence/2026-10-26-batch-23/00-index.md docs/research/evidence/2026-10-26-batch-24/00-index.md
#   → contract 1 行（:72 单行内改动）；known-defects 26 insertions/10 deletions（+他线 KD-77 的 10 行，见 §6）；
#     两个索引各 1 行/2 行改动
```

未跑（按共享纪律，本轨不执行）：任何 Gradle/JVM/仪器化/设备用例；`git fetch`；AIDL 编译。**T1/T2/T3 代码的编译与行为在本轨 UNVERIFIED**——本轨只转录文件与 KDoc 自述。

## 6. 与他线的边界与整文件归属注明（共享纪律：目标文件变脏则整文件纳入并注明）

| 文件 | 开工时状态 | 本轨开工后出现的外来改动 | 归属与处置 |
|---|---|---|---|
| `docs/known-defects.md` | 干净（HEAD `d783ae84`） | ① 他线在文件尾追加 **KD-77**（API 23 冒烟评估，10 行，`git diff` 可见）——**本轨未触碰，提交时整文件纳入并注明 KD-77 来自 API-23 评估线**；② T3 线改契约（非本文件）；③ 截至 V-5 复核，无其他线写入本文件 | 本轨改动 = KD-66 T1 段、KD-69/70 标题+标注+收口、KD-74 补引、KD-76 收窄+T2/T3 段 |
| `docs/current/visual-generation-contract.md` | 干净 | **T3 线在 :72 内回填队列/背压落地状态**（本轨开工后第 2 次 Edit 遇"file modified"即由此来；重读后确认其内容与树内代码一致，予以保留） | 本轨改动 = :72 标题括注 + warm-up 子句 |
| `docs/research/evidence/2026-10-26-batch-23/00-index.md`、`...-batch-24/00-index.md` | 干净 | 无 | 本轨行号勘误（§4）；step 文件未动 |
| `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` | M（批次 25 T4 附录 B 在飞） | T4 活跃落点，**本轨不改** | 移交两处行号漂移：`:124` 引 "KD-70 `:2682`"（现 `:2686`）、`:15` 引 "KD-76 `:2805`"（现 `:2811`）；且 `:15` "P-07 warm-up/ready 未落层"在 T2 落层后需 T4/主会话按其口径复判（设备验证前不得广告的结论不变） |
| `docs/research/2026-10-27-batch-25-baseline.md` 等他线未跟踪记录 | `??` | 无 | 禁改（他线未跟踪记录）；其引用的 `:2664`/`:2682` 等行号是各自写作时点值，不作回改 |

## 7. 未验证 / 残余（如实）

1. **T1/T2/T3 代码行为 UNVERIFIED**：编译、JVM/仪器化/设备执行均未发生（本轨与共享纪律）；台账/契约中的 T1/T2/T3 表述全部标注"代码层，未跑 Gradle/设备"。
2. **KD-76 未关闭**（设备验证前不关闭——批次纪律）；KD-66 未关闭（hard+500 ms 门未动）；两条的关闭门文本未改。
3. **KD-70/KD-69 收口 Progress 引用的裁定行号**（记录 §9 `:396`/`:403`/`:404`）为本轮 grep 实读；若批次 25 记录后续插入该文件，行号会再漂移（引用时以"§9 第二块 + 裁定条文字"双锚为准）。
4. **契约 :72 单行过长**：本轨延续现有单行体例，未做重排（重排会破坏全部 `:72` 外部引用，超出本轨范围）。
5. T4 清单的两处行号漂移与 P-07 口径复判未处理（见 §6 移交行）。
