# 批次 39 设备证据索引（2026-11-01）

- 记录：`docs/research/2026-11-01-batch-39-record.md`（记录员落笔位；本索引由设备轨提交）
- 计划：`.zcode/plans/plan-2026-11-01-batch-39-wiring-calibration.md`
- 工作树 HEAD：`a0641bb7392f380b5716afe95dd3c371dd0c7670`（T2/T3/T4 落码以未提交态在树，工作树开工 737 条）
- 证据目录：`docs/research/evidence/2026-11-01-batch-39/`
- 本目录体积：**10** 个 `.md` / 内容 **57104 B（55.7 KiB）**（`du -sk` = 68 KiB；全 `docs/research/evidence` = 1482 KiB < 8 MiB 预算；上限 16 个/512 KiB）——初稿"9 个 / 40956 B（52 KiB）"只计 9 个 step 文件、漏计索引自身，落盘员复核更正（§9，R4 §3.2 命令实测，终测含 §4.1/§9 增补）
- 规范版本：`docs/research/2026-10-26-device-evidence-convention.md` v1

## 1. 环境

见 `step00-environment.md`。设备 emulator-5562（AVD test_device / API 34 / x86_64 / PAGE_SIZE 4096），5566/5584 他线禁碰、5596 API 23 未跑本轨。

## 2. 产物身份（四包，E-01 §4；设备端 sha 逐字节核对一致）

| 产物 | sha256 | versionName/变体 |
|---|---|---|
| app 主包 | `c149a2c34ca0a235b0e76044a4fba6db1b1767461dd17b872854b9b7fb76e231` | strictOffline debug |
| app androidTest | `e20d923a8e5d76190ac24f2c7e7d82a55448bba6944520c59683e9e41178b17d` | strictOffline debug androidTest |
| data androidTest | `5b8ba39c83b625f3880210f5df2f249d9728e61a96a32a827f72b977101153c3` | debug androidTest |
| database androidTest | `c5fae932c013859e5bb259c6520a6c788a769f4c4784b19d53dbf485728bc1a4` | debug androidTest |

71.json schema 资产 = 378961 B，sha256 `4a13d9d0b5ee561f088142267afe0a97890745ff66d57ae2b1191bef47881d94`（E-01 §5）；dex 符号门 app 16 / data 41 / database 10 全 OK（E-01 §6）。

## 3. 步骤与结论

| E-ID | 步骤 | 门/KD | 设备 | 判定 | 证据 |
|---|---|---|---|---|---|
| E-00 | 环境与设备身份 | 信息性 | 5562 | PASS | `step00-environment.md:1` |
| E-01 | 重建重装 + 源快照门（含 core/model）+ asset/dex 门 | 批次 39 设备硬门 | 5562 | PASS | `step01-rebuild-install-gates.md:1` |
| E-02 | 新设备例（V-01④ / N 向量 / X-02 / X-03 / A-10 协调器级） | D-04/M-03/X-02/X-03/A-10/V-01④ | 5562 | **PARTIAL**（④ 4 例内绿、N 4/4、X-02 6/6、A-10 1/1；X-03 0/1 A 类夹具） | `step02-new-vectors.md:1` |
| E-03 | app 套件首跑（7 套件） | 批 38 底册不回退 | 5562 | PARTIAL（5 套件绿；isolation 6 红、v01app 1 红） | `step03-app-suites-first-run.md:1` |
| E-04 | core:data 套件（12 套件） | 批 38 底册不回退 + KD-80 四设备位 | 5562 | PASS（全绿 158 例） | `step04-data-suites.md:1` |
| E-05 | core:database 套件（5 套件） | 批 38 底册不回退 | 5562 | PASS（4 套件绿 + matrix 复跑 15/15；首跑窗误停登记） | `step05-database-suites.md:1` |
| E-06 | 红例逐条归因（复跑 + 判别轮） | 步骤四 | 5562 | **PARTIAL**（v01app 4/4 绿取代首跑红；x03 两窗同红 A 类坐实；isolation 三窗红未收敛→阻塞上报） | `step06-reruns-and-attribution.md:1` |
| E-07 | 崩溃签名 27 窗 × 8 类 + 生命周期收口 | 步骤四 | 5562 | PASS（216 项全 0；含 §9 本轨误停更正节） | `step07-crash-signatures.md:1` |
| E-08 | KD-76 seam 状态与校准出数条件 | KD-76 | — | **UNVERIFIED**（seam 已落码；设备校准用例 0 引用→出数不可执行） | `step08-kd76-seam-status.md:1` |

## 4. 覆盖对照（判据 §串行执行 → 结论）

| 判据步骤 | 结果 | 备注 |
|---|---|---|
| 一·重建重装 + dex 符号门 + 源快照门（含 core/model） | ✅ E-01 | 快照 1227 文件 diff=0；BUILD_EXIT=0；4×Success；asset/dex 门全过 |
| 二·D-04 预览行+16MiB 向量 | ✅ E-02 | N5/N6/N7+取消重抛 4/4 绿（核心判据：拒在分配前、快照照常提交、恰达不拒、取消重抛） |
| 二·X-02 设备向量 | ✅ E-02 | 6/6 绿（五负向量+正向 witness） |
| 二·X-03 设备向量 | ⛔ E-02/E-06 | 0/1，**A 类夹具缺陷**（两 id 空间错配，源码归因链+两窗确定性红）；修复归落码批后复跑 |
| 二·A-10 协调器级 | ✅ E-02 | 1/1 绿（同 operationId 先 JS 后 IMAGE 第 3 份拒） |
| 二·V-01 ④（条件：seam 落地） | ✅ E-02/E-03 | 缝在树（T3），④ 例首轮即绿；全套件 4/4（复跑轮） |
| 二·KD-76 校准（计时 seam 数据采集） | ⛔ E-08 | UNVERIFIED——seam 已落码但设备校准用例未落码（androidTest 0 引用）；READY_TIMEOUT 维持 10 s |
| 三·24 套件不回退 | **23/24 ✅，1 ⛔** | 23 套件绿（220 例，计数逐一相符）；**`VisualJsIsolationInstrumentedTest` 三窗 6/7/5 红 FAIL——D 主导 + T4 关联本轨不可证伪，阻塞上报（E-06 §2）** |
| 四·崩溃签名与 worker 生命周期收口；红例归因 | ✅ E-07 + E-06 | 27 窗签名全 0；红例逐条归因（A×1 / D×2 / 未收敛×1） |

**设备总账（更正于初稿复核时点）**：28 套件 / 236 例执行。绿终判 = **26 套件 220 例**（含首次取得设备证据的新例 12 个：N 向量 4 + X-02 6 + A-10 协调器级 1 + V-01④ 1；既有 208）；红终判 = 2 套件 16 例（isolation 15 例 FAIL 未收敛 + X-03 1 例 FAIL A 类夹具）。0 跳过；27 窗崩溃签名 0；matrix 窗无签名。

### 4.1 计划 16 项任务结论（落盘员按计划 §1 映射；✅绿=关闭建议 / ⛔红=归因 / ◐部分 / ➖本证据集未覆盖）

| # | 任务 | 判定 | 结论（绿→关闭建议；红→归因） | 证据 |
|---|---|---|---|---|
| 1 | D-04 生产接线 | ◐（第 1 轮复核修复更正 2026-11-01；原 ✅ 绿仅对设备判据面成立） | 设备判据面证据维持为真：previewAssetId 落库且穿越真实重开同值、`source_type=LOCAL_VISUAL_PREVIEW` 共享常量断言绿；C4 ride-along 8/8（含 D-04 三新例）；生产链 restore→渲染→persist→引用设备位走通（设备侧经测试缝）。**但任务命题=生产装配缝（计划 §1 任务 1"快照提交路径调用…"），生产 main 零调用方（本轮实跑 grep）**：`renderPreview`/`TutorVisualPreviewRendererFactory.create` 于生产 main 排除渲染器自身后 0 命中（唯一 main 引用 `VisualChainTestSeams.kt:74`，该文件 KDoc 自钉"src/main 零引用"硬约束）；`commitAssistantTurnWithVisual` 生产 main 排除定义后仅 KDoc 1 处；`FigureGenerationCoordinationFactory.create` 生产装配仅三参（`SmartMistakeBookApplication.kt:364-368`；工厂 4/5 参 `visualEngine`/`visualFrameInputs` 默认 null）→ previewAssetId 生产恒 null。**本行不构成关闭建议**；关闭裁定归主会话时须按"端口+向量绿、生产装配未接"重限定 | E-02 §4（断言位 `V01VisualChainInstrumentedTest.kt:383-393` 本会话实读复核）；E-04 §2 C4；E-01 §6 dex 门；§10.2 |
| 2 | M-03 16MiB 接线 | ✅ 绿 | **关闭建议**：N5/N6/N7+取消重抛 4/4（拒在分配前、快照照常提交、恰达不拒、取消重抛，判据 §3.1–3.3 一一对应） | E-02 §3 |
| 3 | P-11 errorKind 半边 | ◐ 落码在树（第 1 轮复核修复更正 2026-11-01；原记 ➖"热区维持"只对设备轨自身零改动成立，作批级任务 3 结论=欠报） | 现树四文件 M 恰为本批 P-11 落码（本轮 `git diff --numstat` 实测：RoomTutorToolRunner.kt +49/−10、RoomTutorToolRunnerTest.kt +33/−0、TutorToolTrace.kt +7/−0、TutorToolTraceTest.kt +9/−0；四 diff 逐 hunk 读毕零外来改动）：UNAVAILABLE 分支按 `UNATTRIBUTED_DIAGNOSTIC_PREFIX`（`VisualJsFailureClassification.kt:141` internal const 同源复用）判别独立结果码 `figure_engine_unattributed` + 学生话两处（`TutorToolTrace.kt`）+ 两新测试（KDoc 自证批次 35 R2 设计 §2.3）——errorKind 半边已落、非停项；errorKind 混合形余面（裸串写点位）未动；JVM 执行 UNVERIFIED（索引零 Gradle 口径不变）；落码轨申报与开工前树态干净性归主会话按其申报记录裁定 | §10.1 |
| 4 | V-01 ④ PDF 腿 | ✅ 绿（附前提） | ④ 例设备绿（首轮绿 + 复跑 4/4，断言只用缝公共返回值 + `PdfRenderer` 公共 API）。**关闭建议附登记**：R2 §7.6 缝设备绿绑定因 isolation 三窗红当前不成立（E-06 §2），关闭裁定归主会话 | E-02 §4；E-03 §2；E-06 §1/§2 |
| 5 | KD-76 计时 seam 落码 | ◐ 落码半边完成 | seam 已落码（本会话复跑 grep：仅引擎 main + app JVM 测试两文件，androidTest 0 引用）；**设备绿绑定未取得**（isolation 三窗红）→ 阻塞上报在案，绑定裁定归主会话 | E-08 §1–2（本轮复跑一致）；E-06 §2 |
| 6 | KD-76 校准出数 | ⛔ UNVERIFIED | **归因**：T5 载体（经 seam 的设备校准用例）未落码 → 出数不可执行；非设备/环境问题。READY_TIMEOUT 维持 10 s 零改动，KD-76 open 维持；落码后按"新增用例必须重跑"进套件单 | E-08 §2–3 |
| 7 | A-10 协调器级跨 method | ✅ 绿 | **关闭建议**：同 operationId 先 JS 后 IMAGE 第 3 份拒（协调器级、真 ledger）1/1 + 预算边界 3/3 | E-02 §3；E-04 §2 a10 |
| 8 | X-02 设备向量 | ✅ 绿 | **关闭建议**：缺源/错图/SHA 变化/授权不足/预算不足 + 正向 witness 6/6 | E-02 §3 |
| 9 | X-03 设备向量 | ⛔ 红 | **归因 A（夹具缺陷，确定性）**：期望值锚错 id 空间——夹具输入无 id 字段（`StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt:165-173`）而断言把协调器派生式（`V01VisualChainInstrumentedTest.kt:208`）与仓储内容派生式（`TutorVisualCommitRepository.kt:222-230`）划等号（断言位 `:219-223`；归因链本会话逐处实读复核）；两窗逐字节同红。修复归落码批，修复后按新增用例重跑；X-03 判据语义（①档/0 派发）无设备证据 | E-02 §5；E-06 §1 |
| 10 | Q-04 设备半边（真机渲染金样） | ➖ 未覆盖 | 本批 27 窗清单无金样套件；全部 x86_64/API 34 模拟器口径，真机缺口维持（§5.6）；载体归落码批，出数归设备轨后续批 | E-07 §1；§5.6 |
| 11 | KD-72 锁窗口复核 | ✅ 完成（台账轨） | W1–W7 实跑零动作，分支 B 维持（干净树重生成锁窗口不满足），KD-72 open 维持；已在 KD Progress 落块 | `docs/known-defects.md:2796`（T11 块，本会话实读） |
| 12 | A-12 余量与 A-11 响应半边回归确认 | ◐ 回归绿在案，语义映射待核 | 回归面绿：a09 两窗 3/3、flags 2/2、p09/p10/p0405 各绿；A-11/A-12 与具体套件的语义映射无判据文档可核（`2026-11-01-preview-wiring.md` grep 0 命中）→ 本索引不做映射判定，归台账/主会话 | E-03 §2；E-06 §1 |
| 13 | 台账回填 | ◐ 部分 | KD-76/72 滚动已落块（T11 `:2796` / T13 `:2913`，均台账维护员所写）；**记录文件未落**（记录员落笔位，§7）；契约 `:208` 刷新条件（热区清空）未触发；基于本索引 E-06 阻塞/E-08 seam 状态的回填归主会话 | `docs/known-defects.md:2796/:2913`；§7 |
| 14 | V-02 IMAGE→JS 机械回退裁定登记 | ➖ 未覆盖 | 本证据集无 V-02 裁定记录；台账任务归其轨 | — |
| 15 | 热区条件轨 | ➖ 条件未触发 | 热区（feature/core/ui 等）本批维持禁改（共享纪律），无条件落码发生 | 计划 §2 |
| 16 | S-02/S-09 行核验 | ➖ 未覆盖 | 台账任务，本证据集无对应记录 | — |

## 5. 缺口与未落盘

1. **isolation 三窗红（本批最大未决）**：D 主导归因（方差形、负载相关、红形单一、其余 199+ 例绿）但 T4-reverted 控制轮本轨不可执行（禁改他线在飞/禁 git 写）——裁定请求见 E-06 §2；R2 §7.6 的 seam 设备绿绑定**当前不成立**。
2. **X-03 夹具缺陷**：期望值锚错 id 空间（源码归因 `StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt:219` vs `TutorVisualCommitRepository.kt:222-229`）；修复+复跑归落码批；X-03 判据语义（①档判定/0 派发三断言位）无设备证据。
3. **KD-76 校准出数**：设备校准用例未落码（E-08）；落码后按新增用例重跑进套件单。
4. **本轨操作误停登记**：matrix 首跑窗被本轨误判"v9 卡死"force-stop（实为字母序探针错觉、正常推进中）；复跑窗 15/15 绿取代，教训与更正见 E-07 §9。
5. **V-03/V-04 轨 3 套件**（CommitRace 3 / RebuildRestore 3 / SavedProblemRetention 2）按判据归其轨套件单，本轨未执行（同批 38 口径）。
6. arm64/16KB/真机口径维持既有缺口（全部数字 x86_64/API 34 模拟器口径）。

## 6. 脱敏扫描（§4.4 命令本轮实跑）

```text
0 hits: secrets / 0 hits: credential assignments / 0 hits: emails / 0 hits: home paths
10 个 .md / 57104 B（含索引）；git check-ignore 不命中（exit=1，.md 直存 evidence 目录）
```

（初稿行"9 个 .md / 40956 B"为扫描时只含 step 文件的读数；落盘员复核按 §4.4 全部四条命令重实跑，0 命中维持。）

- [x] 每个 E-file 有 `sanitized` 字段且扫描 0 命中
- [x] 全部证据为 `.md` 且在 `docs/research/evidence/` 下；`build/` 路径只出现在 raw-log 字段
- [x] 无"工单转录"行被用作判定；每个数字可回链 E-ID + path:line
- [x] step 文件只追加不改历史（E-07 更正走 §9 追加节；E-05 §4 为收窗追加节，首写时声明"待补"）

## 7. 回链检查

- [ ] 记录（`2026-11-01-batch-39-record.md`）由记录员落笔，应引用本索引 E-ID——落盘员复核时点（§9）：**文件不存在**，落笔位未落
- [ ] `docs/known-defects.md` KD-76/KD-82 Progress 由主会话按本索引回填（KD-76 维持 open，见 E-08）——落盘员复核时点：KD-76 尾块=台账维护员 T13（`:2913`，登记"seam 本体未落 0 命中"，早于 T4 落码，被 E-08 推进）；KD-82 尾块=批次 38（`:3179`）；主会话按本索引的回填未发生
- 本索引为映射权威；本轨零 git 写、零热区编辑、仅 assemble* 构建（E-01）。
- （第 1 轮复核修复 2026-11-01）上条 KD-76 seam 漂移的台账本体回填已由本轮落（`known-defects.md` KD-76 批次 39 复核修复 Progress 块 + 清单 I.2/I.5 时点更正注记，见 §10.3）；记录文件（`2026-11-01-batch-39-record.md`）仍不存在，记录回链仍归记录员/主会话。

## 8. 落盘时点树态登记（与执行时点的差异，均他线所为、本轨零编辑）

- **HEAD 前移**：执行全程绑定 `a0641bb7…`；落盘时点 `git rev-parse` = `684adb6e…`（他线提交，不改工作树字节，源快照绑定不受影响——同批 38 索引 §8 判例口径）。
- **构建窗后源漂移（raw-log `src-snapshot-postbuild.diff`，46 文件）**：dense/knowledge 资源与装配、`core/domain/BatchImportRepository.kt`、`DeviceMigrationDrillInstrumentedTest.kt`、若干 JVM test 文件——**与本轨 28 套件的源面零交集**（app/src 与 core/data visual main/androidTest、core/database 全未动，逐路径核对）；四包设备端 sha 绑定不受影响。

## 9. 落盘员复核（2026-11-01 本 ask 实测；只追加不改 §1–§8 既有行）

本节为记录员（证据落盘员）接手落盘时的独立复核记录，全部为本会话实跑/实读：

**实测命令与结果**：

- 体积（R4 §3.2 命令）：`find … -name '*.md' -printf '%s %p\n' | sort -n` → 10 文件合计 **57104 B**（终测含本节与 §4.1 增补）；`du -sk` 本目录 = **68** KiB；全 evidence 目录 = **1482** KiB（< 8 MiB 预算）。初稿"9 个 / 40956 B / 52 KiB"只计 9 个 step 文件，已更正（头部字段、§6）。
- 脱敏扫描（R4 §4.4 四条命令重实跑）：`0 hits: secrets / credential assignments / emails / home paths`（维持）。
- `git check-ignore -v docs/research/evidence/2026-11-01-batch-39/00-index.md`（附 step01）→ **exit=1**（不忽略，可跟踪）。
- 树态：`git rev-parse HEAD` = `684adb6e…`、`git status --porcelain | wc -l` = **772**——与 §8 登记一致；本证据目录 `??` 未跟踪（零 git 写维持）。
- 记录回链：`docs/research/2026-11-01-batch-39-record.md` **不存在**（`ls` 无匹配）。

**引用位逐处实读复核（与 step 文件引用的差异仅一处行漂）**：

- `StudyDatabase.kt` `STUDY_DATABASE_VERSION = 71` 现锚 **`:139`**（step01/ask 串引 `:138`，行漂 +1，以符号为准）。
- `TutorVisualCommitRepository.kt:222-230` `programIdFor` 内容六字段派生 + KDoc"对外 visualId 与落库 program_id 的同一个值"（`:221`）——与 E-02 §5 归因链一致。
- `StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt:165-173`（夹具输入无 id 字段）、`:219-223`（跨 id 空间断言位）——与 E-02 §5 一致。
- `V01VisualChainInstrumentedTest.kt:208`（协调器派生式 KDoc）、`:383-393`（D-04 落库断言）、`:420-431`（`VisualChainTestSeams.writeSnapshotPdf` 缝调用）——与 E-02 §4 一致。
- `VisualJsProtocol.kt:35-39` `STATUS_*` 0..4——与 E-06 §2"-3 为 binder 层非协议码"一致。
- `FullMigrationMatrixInstrumentedTest.kt:36-51` 重例 v1..71 循环——与 E-05 §3.1 一致。
- E-08 grep 复跑：`connectStageTimestampsForTest` 仅引擎 main + `app/src/test/.../ConnectStageTimestampsTest.kt` 两文件，androidTest 0 引用——与 E-08 一致。

**台账实况（本会话实读 `docs/known-defects.md`，只读）**：KD-72 批 39 T11 块在 `:2796`（W1–W7 零动作）；KD-76 批 39 T13 块在 `:2913`（其"seam 本体未落"表述早于 T4 落码，被 E-08 推进，回填归主会话）；KD-82 尾块 `:3179`（批次 38）。`docs/known-defects.md` 为他线 M，本轨零编辑。

**结论**：落盘完整（00-index + step00–08 共 10 文件，各 E-ID 一份 E-file），脱敏/体量/ignore/引用四门复核通过；红项归因与关闭建议见 §4.1。

## 10. 第 1 轮复核修复（2026-11-01；复核发现处置，零 Gradle/零设备/零 git 写）

本节由第 1 轮复核修复轮追加（非落盘员、非设备轨）。第 1 轮复核（非低发现 5 条）指名的台账-树态矛盾按下述处置落地；全部依据为本轮实跑命令与实读源码（命令与输出逐条在案）：

**§10.1 发现 1（§4.1 #3 欠报）**：计划任务 3（P-11 errorKind 半边）原记"➖ 热区维持/本轨零改动"。设备轨自身零改动属实，但现树四文件 M 恰为本批 P-11 落码：`git diff --numstat` 实测 RoomTutorToolRunner.kt +49/−10、RoomTutorToolRunnerTest.kt +33/−0、TutorToolTrace.kt +7/−0、TutorToolTraceTest.kt +9/−0（合计 +98/−10，与落码轨申报一致）；四份 diff 逐 hunk 读毕均为 P-11 内容、零外来改动——UNAVAILABLE 分支按 `UNATTRIBUTED_DIAGNOSTIC_PREFIX`（`VisualJsFailureClassification.kt:141` internal const，同模块同源复用）判别，独立结果码 `figure_engine_unattributed` + 学生句（`tutorToolRefusalText` 新分支）+ 两新测试（KDoc 自证批次 35 R2 设计 §2.3）。§4.1 #3 已改判 ◐"落码在树"（JVM 执行仍 UNVERIFIED，本索引零 Gradle 口径不变）；落码轨开工前时点树态干净性本轮无法独立复核，按其申报记录归主会话裁定。

**§10.2 发现 3（§4.1 #1 关闭建议越界）**：任务 1 命题=生产装配缝（计划 §1 任务 1"快照提交路径调用 …"）。设备判据面证据维持为真，但生产装配零调用方（本轮实跑）：`grep -rnE "TutorVisualPreviewRenderer|renderPreview|previewAssetId"` 于 app/src/main + core/data|domain main、排除渲染器自身 → 0 调用点（唯一 main 引用 `VisualChainTestSeams.kt:74`，该文件 KDoc 硬约束 1 自钉"仅 androidTest 引用、src/main 零引用"，配 `tools/ci/check_visual_test_seams.py` 机检）；`grep commitAssistantTurnWithVisual` 生产 main 排除定义 → 仅 `VisualCheckpointRetention.kt:132` KDoc 1 处；`FigureGenerationCoordinationFactory.create` 生产装配仅三参（`SmartMistakeBookApplication.kt:364-368`；工厂签名 4/5 参 `visualEngine`/`visualFrameInputs` 默认 null，`DefaultFigureGenerationCoordinator.kt:881-893`）→ previewAssetId 生产恒 null。§4.1 #1 已改 ◐并明示"不构成关闭建议"；清单 D-04 行结论"生产接线仍未落"经同轮复核维持为真。

**§10.3 发现 2+4（台账 seam 状态滞留）**：known-defects.md T13 块（`:2913`）②"seam 本体未落 0 命中"与清单附录 I.2/I.5 同判均为 T13 落笔时点值，被同日 T4 落码推进：本轮 `grep -rn "connectStageTimestampsForTest|ConnectStageTimestamps" --include=*.kt app core` → `VisualJsClientEngine.kt`（:97/:548/:1019/:1284/:1288/:1397/:1400）+ `app/src/test/.../ConnectStageTimestampsTest.kt` 两文件，androidTest 0 引用——与 E-08 §1–§2 判定一致（seam 落码半边完成；设备校准用例未落码、出数不可执行；READY_TIMEOUT 维持 10 s；KD-76 open）。回填：known-defects.md 增批次 39 复核修复 Progress 块（T13 块后）；清单 I.2/I.5 增时点更正注记。T13/I 原记录不回改（当时为真，落笔时间戳在案）。

**§10.4 发现 5（X-03 夹具断言）**：归因链本轮逐处独立复核成立——夹具输入 `TutorVisualProgramInput` 六字段无 id（`StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt:165-173`）；仓储落库 id=`TutorVisualIdentity.deriveProgramId` 六内容字段长度前缀 sha256（core/database `TutorVisualIdentity.kt:34-63`，`visual-program-<64hex>`）；断言 `:219-223` 把协调器派生式 `visual:{operationId}:{sha16}`（`DefaultFigureGenerationCoordinator.kt:843-848`）与之划等号，结构性不可能成立；`:216-218` requireNotNull 通过已证"快照引用与持久行 id 一致"，该断言冗余。step02 §5 失败原文 expected/actual 两值与两派生式逐字符同形。**处置：不动夹具**——批次已把"修复+复跑"作为同一欠账指定归落码批（§4.1 #9/§5.2："修复后按新增用例重跑"），本轮零 Gradle 零设备，单落修复半会使已记录的 0/1 红窗（step02 §5、E-06 逐字节同红）与树脱钩；欠账维持 ⛔，由落码批修复+复跑同窗完成。

**本节改动文件**：`00-index.md`（§4.1 #1/#3 行内更正、§7 一条追加、本节）、`docs/known-defects.md`（KD-76 增复核修复 Progress 块）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（D-04 行注记 + I.2/I.5 注记）。known-defects.md 与清单为他线 M 共享脏文件，本轮改动均为追加式注记，未动他线既有行。
