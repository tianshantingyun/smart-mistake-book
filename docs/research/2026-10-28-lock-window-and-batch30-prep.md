# 锁窗口再判定 + 批次 30 前置 seam + R-04 解除路径可行性（批次 29 R3）

> 线：R3 锁与前置复核｜日期：2026-10-28｜落点：本文件（`docs/research/`）
>
> **本文只做三件事**：① 按批次 29 基线第九项（`docs/research/2026-10-28-batch-29-baseline.md:224-240`）的窗口判定，
> 给出锁文件重生成的**可执行分支**（本时点落在"不可行"支 ⇒ 最小等待清单）；② 复核批次 30 前置
> （持久化四表 v70+/19+、身份+CAS、冷恢复）**需要本批先落的接口**，列出本批必须留出的 seam（只列不实现）；
> ③ 复核 R-04 解除路径三选一在公开资料上的可行性。
>
> **不重复论证**：KD-72 的机制/官方口径/历史证据一律引 `docs/known-defects.md:2746-2768`；锁执行的分步命令一律引
> `docs/research/2026-10-27-lockfile-execution-plan.md`（下称 **LP**）；R-04 的三重实测一律引
> `docs/research/evidence/2026-10-27-batch-27/step03-r04-release-roundtrip.md`（下称 **E-03**）与
> `docs/research/evidence/2026-10-28-batch-28/step05-r8-roundtrip.md`（下称 **E-05**）。
>
> **本轮实际执行过的**：只读 git（`rev-parse`/`rev-list`/`status`/`diff --cached`/`ls-files`/`cat-file`/`show`）、
> `find`、`grep`、`python tools/ci/check_lockfiles.py`（exit 0）、`python tools/ci/check_dependency_pins.py`（exit 0）、
> `javap`（本地 AGP 9.3.0 jar 的 DSL 类）、`curl`（developer.android.google.cn 官方文档镜像）。
> **未运行 Gradle、未运行任何测试、未上设备、未做任何 git 写操作、未改任何 build 文件。**

---

## 0. 结论速览

1. **锁窗口：仍不可行（分支 B）。** 本轮复跑 LP §1 的 J1–J4：J1（六文件入库）**红**、J2（暂存区无锁）**绿**、
   J3（干净树）**红**（674 → 676 条，间隔数分钟两次实测，他线在飞、计数在动）、跟踪锁 **0**、盘上 14 个锁全部未跟踪、可见未跟踪锁条目 13。⇒ 只产出
   **最小等待清单**（§1.4），不产出执行序列；执行序列在其窗口打开时按 LP §2 分支 A 原样执行（§1.5）。
2. **批次 30 在本批必须拿到的 seam 共 8 条**（§2）：其中 3 条必须在本批 T2/T3 内定形（canonical encode / 版本码 / PDF 目标几何），
   2 条是"版本常量与身份"的单一源问题，1 条（环境指纹载体）与裁定 5 的落点存在**范围矛盾**需主会话一句话定（§2 S3），
   2 条是"不得丢字段/不得绕全局"的形态约束。
3. **R-04 三选一：技术面均有公开依据支撑，差异在代价与口径。** 关键新证据：AGP 9.3 的
   `VariantDimension.testProguardFiles` 官方语义是**"test code 要被处理以套用与主代码相同的混淆"**（§3.2），
   即"可测 release 变体"不必以 `-dontobfuscate` 为代价——与此前 E-03 §6 第 2 项的表述不同；但"在本仓 AGP 9.3 + app 模块 AIDL
   + 隔离进程"这一组合下端到端是否成立 **UNVERIFIED（本轮不能跑 Gradle）**。

---

## 1. 一、锁文件重生成的可执行分支

### 1.1 判定程序（LP §1 原样复跑，逐条给实测）

```bash
git status --porcelain=v1 -- settings.gradle.kts core/ui/build.gradle.kts \
  feature/tutor/build.gradle.kts core/markdown/build.gradle.kts core/export/build.gradle.kts .gitignore
git cat-file -e HEAD:core/markdown/build.gradle.kts && echo J1b-md=OK || echo J1b-md=MISSING
git show HEAD:settings.gradle.kts | grep -q '":core:markdown"' && echo J1b-include=OK || echo J1b-include=MISSING
git show HEAD:core/ui/build.gradle.kts | grep -q 'richtext.commonmark' && echo J1c-richtext=RED || echo J1c-richtext=OK
git show HEAD:core/export/build.gradle.kts | grep -q 'kotlinx.serialization.json' && echo J1c-serialization=OK || echo J1c-serialization=RED
git diff --cached --name-only | grep -i 'lockfile'      # 期望无输出（grep exit 1）
git status --porcelain=v1 | wc -l
git ls-files '*gradle.lockfile' | wc -l
git status --porcelain=v1 | grep -i lockfile
python tools/ci/check_lockfiles.py ; python tools/ci/check_dependency_pins.py
```

| 判据 | 期望 | 本轮实测（2026-10-28，HEAD `3d9372e5`） | 判定 |
|---|---|---|---|
| J1a 六文件工作树干净 | 空输出 | **5 个 ` M`**（`.gitignore`/`core/export`/`core/ui`/`feature/tutor`/`settings.gradle.kts`）+ **`?? core/markdown/build.gradle.kts`** | **红** |
| J1b core/markdown 入库 | 两个 OK | `fatal: path ... exists on disk, but not in 'HEAD'` ⇒ `J1b-md=MISSING`、`J1b-include=MISSING` | **红** |
| J1c HEAD 侧声明 | richtext=OK、serialization=OK | `J1c-richtext=RED`、`J1c-serialization=RED` | **红** |
| J2 暂存区无锁 | 无输出 | 无输出（`grep` exit 1） | **绿** |
| J3 干净树 | 0 | **674**（首次）→ **676**（第二次，`201 ' M' + 475 '??'`；他线在飞，计数在动） | **红** |
| 跟踪锁 | 0 | `git ls-files '*gradle.lockfile'` = **0** | — |
| 盘上锁文件 | 14 | `find . -name '*gradle.lockfile' -not -path './.git/*' -not -path '*/build/*'` = **14**（13 模块锁 + `settings-gradle.lockfile`） | — |
| git 可见未跟踪锁条目 | 13 | 13 条 `?? …/gradle.lockfile`（`core/markdown/gradle.lockfile` 被折叠进 `?? core/markdown/`） | — |
| J4 预检 | pins exit 0 / lockfiles exit 0 | `check_dependency_pins.py` → **EXIT=0**（quickjs 1.0.15 / stdlib 2.3.21，解析图仍是旧 `artifact-dependencies.xml`）；`check_lockfiles.py` → **EXIT=0**（skip 模式，输出"13 个未跟踪锁文件…不能原样提交；缺锁 project：:benchmark"） | **绿（但只证明钉版门）** |

**判定：分支 B —— 窗口不可行。** J1 与 J3 同时红，且跟踪锁为 0（锁门未生效这一事实本身要求"先立锁再谈锁门"）。
与基线第九项结论一致，判定不变（唯一变化是计数仍在动）；另记一处状态：`git rev-list --left-right --count origin/main...HEAD` = **`0  0`**，
即批次 28 的推送重试已落（`git rev-parse origin/main` 与 HEAD 同为 `3d9372e5f6e8001fc16c4d11b2e3d8a5459fb1d5`，本地 remote-tracking 读数），窗口判定不依赖远端，记录备查。

### 1.2 KD-72 现状（引用，不重复论证）

`docs/known-defects.md:2762` 列明的四条未闭合条件，本轮逐条对照仍未变：
① 锁必须在**干净、待提交的树**上重生成（J1/J3 仍红）；② `:benchmark` 缺锁、`core/markdown` 模块与锁不能单独入库；
③ 一次负控（人为抬版/加依赖在解析期红）**从未执行**；④ 现有 13 条 git 可见锁条目（盘上 14 个文件）**不得原样 `git add`**
（`:2760`：这批锁生成于他线未提交的构建改动之上，与 HEAD 解析图不一致）。**KD-72 保持 open，本批不改树。**

### 1.3 最小等待清单（分支 B 的唯一产物）

每项写明：等什么、当前实测、谁解除、解除后紧接着跑什么。**任一满足只消掉对应等待，不是全部解锁条件。**

| # | 等待项（判据 = §1.1 对应项） | 当前实测 | 解除责任 | 解除后的下一步 |
|---|---|---|---|---|
| W1 | **六文件入库**（J1a/J1b/J1c）：`settings.gradle.kts`、`core/ui/build.gradle.kts`、`feature/tutor/build.gradle.kts`、`core/export/build.gradle.kts`、`.gitignore` 干净；`core/markdown` 模块与 `":core:markdown"` include 已在 HEAD；**HEAD 侧**已去掉 `richtext.commonmark`、已引入 `kotlinx.serialization.json` | 6/6 不满足（`core/export` 属 KD-75 热区；`settings.gradle.kts` 属 X-01 热区） | 热区解锁（feature/core 五模块与 core/export 收口后） | 进 J1 复判 |
| W2 | **树收口**（J3）：`git status --porcelain \| wc -l` = 0，或主会话**书面**放宽口径（放宽须逐条记录，LP §1 J3 注释） | **674 → 676**（201 ` M` + 475 `??`；他线在飞，同一会话内已变动，故只记量级与趋势） | 五条他线 + docs 收口 | 进 §1.4 执行序列 |
| W3 | **暂存区无锁**（J2） | 已满足（无输出） | 常态维持；他线一旦暂存锁，按 R3 §4.1.1 协调，**不得直接 unstage** | — |
| W4 | **旧锁处置**：14 个盘上锁全部是批 23 旧产物，**任何分支下都不得 `git add`** | 全部未跟踪、与 HEAD 不符 | 门禁轨在分支 A 的 A1 步删除 | A1 → A2 重生成 |
| W5 | **`:benchmark` 缺锁**：`settings.gradle.kts:33` 已 `include(":benchmark")`，盘上无 `benchmark/gradle.lockfile` | `check_lockfiles.py` 明点"缺锁 project：:benchmark（DEFAULT 模式下不会报错）" | 分支 A 的 A2 生成时自然覆盖；A3.4 校验 | A3.4 |
| W6 | **一次负控**（KD-72 ③） | 从未执行 | 分支 A 的 A5（负控 5a–5c + 恢复） | A5 → A8 收口 |
| W7 | **Gradle 环境预检**（J4 后半，本轮未跑） | `java -version` / SDK 37 / build-tools 37 **UNVERIFIED**（本纪律禁跑 Gradle；LP §1 J4 的"机器更换才从严核"条款仍适用） | 门禁轨 | A0 自查 |

### 1.4 窗口打开后的执行与回滚（引用，不重写）

- **完整序列**：LP §2 分支 A 的 **A0 前置自查 → A1 清 14 个陈旧锁 → A2 `lockAllDependencies --write-locks`（与 CI step 同参）
  → A3 产物审计（15 锁/四条内容不变量/benchmark/配置面）→ A4 `check_lockfiles.py` → A5 负控 5a–5c + 恢复
  → A6 提交分块（≤6 路径/块）→ A7 推送 + CI 双 step → A8 KD-72 收口**（`docs/research/2026-10-27-lockfile-execution-plan.md:86-302`）。
- **回滚**：同一文件 §2.A 回滚矩阵（`:303-313`）；负控阶段的恢复安全性前置在 A5 首（`:191-203`，红则停、负控改期）。
- **新鲜度警告（本轮新增）**：LP 的 T0 快照锚在 HEAD `405c8373`（LP `:19`），今为 `3d9372e5`；J3 计数 LP 记 674，本轮首测 674、复测 676（在动），
  故**不能据此认为状态未变**——执行前必须原样重跑 LP §1 的 J1–J4 并以其输出为准（LP `:40` 已要求"每次执行前先跑"）。
- **本批边界**：计划 §2 明列"锁文件生成（窗口判定驱动）"为**不做**；本批落点（core/data visual + tools/visual_compiler + docs）
  与六个 build 文件、`.gitignore` **零重叠**，故本批不存在"顺手改锁"的合法路径。

---

## 2. 二、批次 30 前置：本批必须留出的 seam（只列不实现）

**批次 30 的范围与验收锚**（引 `docs/research/2026-10-28-mva-remaining-order.md:120-129`）：
B1（D-01）四表迁移，**DB 取 70+、请求 schema 取 19+**；现状实测 DB = **69**（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:134`）、
`TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION` = **18**（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546-547`），
且 `core/database` 现为 8 M + `?? gradle.lockfile`（开工即脏）。
B2（D-02）visualId=programId 不可变 + CAS + staging/LIVE；B4（D-05）冷恢复不执行 JS；B3（D-03）checkpoint（可裁半）。
契约侧的列定义与语义：`docs/current/visual-generation-contract.md:166`（program 列）、`:168`（snapshot 列，含
`sceneSchema/renderSchema`、`timeMs/seed`、`runtime/compiler/font 版本`、`viewport/fontScale/theme`）、`:172`（身份不可变）、
`:184`（同事务 + staging/LIVE）、`:190`（未知版本只读）、`:192`（导出冻结 `{programHash,state,timeMs,seed,layoutEnvironment,RenderScene}`）。

**热区约束（硬）**：`core/domain` 开工即脏 M、`core/database` 他线 M、`core/ui`/`core/export`/`settings.gradle.kts` 禁改。
因此下列 seam **只能落在 `core/data` 的 visual 包**（本批落点）或作为"形态约束"（不改代码，只把 API 钉成可被后续消费的形状）。

| # | seam（本批必须留出的接口/形态） | 现状（本轮实读） | 批次 30 用它做什么 | 不留会造成的具体失败 |
|---|---|---|---|---|
| **S1** | **DrawOps 的 canonical 编码器**：`text → VisualDrawOpsDocument → text` 可**逐字节往返**，且与编译器输出同一 canonical 形态（字段序/数字格式） | 只有单向解码：`VisualDrawOpsValidator.validate(text, frozenContentIds, viewport)`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsValidator.kt:49-70`）；**无 encoder**，全仓 visual 测试无"往返/byte-identical"用例（`grep -rln "roundtrip\|往返\|byte-identical" core/data/src/{test,androidTest}/…/visual/` → 0 文件） | 快照列"验证后的场景与 RenderScene/SHA"（契约 `:168`）要求可**再序列化**并算 SHA；E-05 一致性门的"指令序列序列化相等"判据（清单 `:179`）也依赖它 | 快照只能存不得重算的裸文本 → 无法证明"读回 = 写入"（D-05 冷恢复的第一道校验），也无法做屏/PDF 判据 |
| **S2** | **"版本不支持"的机器可判码**：`drawSchemaVersion != 1` 必须与"JSON 坏/类型坏"分属不同 code | 现回落成 `DRAWOPS_TYPE`、消息 `drawSchemaVersion must be exactly 1, got N`（`VisualDrawOpsValidator.kt:82-84`），调用方无法按码分支 | D-05"未知版本只读"（契约 `:190`）需要在**不执行**的前提下把"未知版本"与"损坏"分开处理；前者读预览/文字、后者拒读 | 未知版本会被当成损坏数据拒读（丢可用预览）或反之被当可重放（错画）——契约 `:190` 明禁"把未知 JSON 强行交 painter" |
| **S3** | **回放/度量环境身份的持久化载体**：一个可在 `core/data` 侧读到、可存成字符串、含 Paint flags 的指纹值 | 指纹只在 app：`VisualMeasureEnvironment.fingerprint()` = `typefaceId\|sizeSp\|density\|fontScale\|locale`（`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualContentMeasuring.kt:151-152`），**无 flags 项**；度量 Paint 只设 `textSize`（`:416-418`）；`core` 侧只有 label 内的 `sizeSp/fontScale/heightDu`（`core/data/.../VisualDrawOpsPainterPure.kt:163-173`） | 快照列 `runtime/compiler/font 版本` + `viewport/fontScale/theme`（`:168`）与导出 `layoutEnvironment`（`:192`）需要在**冷恢复时逐字比对**；不匹配才降级（`:121`"字体/renderer 精确匹配…不匹配读预览/文字"） | 冷恢复无法判定"同环境"→ 要么一律重建（违背不执行 JS）要么一律信任（换字体仍称同快照，契约 `:121` 明禁） |
| **S4** | **冻结输入的纯函数化**：painter 与编译器只依赖显式入参（document/环境/目标几何），不读全局资源 | `VisualDrawOpsPainter.draw(document, canvas, pxPerDu, labelContent)`（`VisualDrawOpsPainter.kt:69-75`）已是显式入参；但 `Rendered` 不携 programId/viewport/环境（`core/domain/.../VisualExecutionEngine.kt:241-246` 只有 sceneJson/sceneSchemaVersion/contentIds/drawCommandsJson/drawSchemaVersion），这些只在**请求**里（`:56-72`） | 快照写入需要"请求字段 + 环境 + 目标几何"三元组一次冻结；冷恢复要能用**同一三元组**重放出同一结果 | 冻结后无法复现：同一 scene 在不同环境/栏宽下重放结果不同，却都被当作同一快照 |
| **S5** | **PDF 目标几何显式化**：`ptPerDu` 之外还要能表达**页面偏移/页尺寸**，而非只接受一个等比 scale | `draw()` 入口 `canvas.setMatrix(rootMatrix)`（KDoc `VisualDrawOpsPainter.kt:33`；实现 `:84-87`：`rootMatrix.setScale(pxPerDu, pxPerDu)` → `canvas.setMatrix(rootMatrix)`）——**替换**调用方既有变换；KDoc 已注明"PDF 页面基础变换与 painter 的组合归回放/PDF 批（`ptPerDu` 只替换 `pxPerDu`，契约 `:192`）" | 契约 `:192`"PDF 只按目标栏宽计算 ptPerDu"；快照/导出的 `layoutEnvironment` 要记录栏宽与页几何 | PDF 页有边距时几何整体错位；栏宽变化时无法复算（记录里没有可复算的输入） |
| **S6** | **label 内容的稳定绑定**：`contentId` 必须由 `(kind, payload, 环境指纹, 降级态)` 决定，可跨进程/跨重启复用 | contentId = `CONTENT_ID_PREFIX + index + "-" + digest.take(N)`（`app/src/main/kotlin/.../VisualContentMeasuring.kt:69`），`contentDigest` = `sha256(DIGEST_VERSION\|kind\|degraded\|环境指纹\|payload)`（`:335-350`，`DIGEST_VERSION="visual-content-measure-v1"` `:350`）——**已含环境指纹**，但该函数是 app 模块 `internal` | 快照存 label 的冻结布局（行范围/baseline/行高），冷恢复按 contentId 取回原生 artifact；contentId 不稳定就无法"只重放不重测" | 冷恢复退化成重新度量（需设备字体栈）或无法把 DrawOps 的 label 与冻结内容对上 → 只能整图降级 |
| **S7** | **hitRegions 往返不丢、且不进 ops 词表** | DTO 已带 `hitRegions: List<VisualDrawHitRegion>`（`core/data/.../VisualDrawOps.kt:170`）；painter 不读（`VisualDrawOpsPainter.kt` KDoc"hitRegions 不绘制"）；本批裁定 1 允许非空并透传 | 快照的"验证后的场景"要供 4E.2 交互层消费；hitRegions 属交互事实，与绘制事实同源冻结 | 往返丢 hitRegions → 恢复后交互区消失，且不可重建（源码/环境可能已变） |
| **S8** | **schema 常量单一源**：`drawSchemaVersion` 不得继续三处镜像 | 三处：引擎 `private const val DRAW_SCHEMA_VERSION = 1`（`QuickJsVisualExecutionEngine.kt:905`）、painter companion `const val DRAW_SCHEMA_VERSION = 1`（`VisualDrawOpsPainter.kt:303-307`）、校验器字面量 `!= 1`（`VisualDrawOpsValidator.kt:82`） | 快照列要写 `renderSchema`；D-05 要按版本分派；本批/批次 30 都要 bump 说明 | 三处任一漏改 ⇒ 快照写 A 版本、回放按 B 版本拒 ⇒ 老快照集体失效（或更坏：静默混用两套语义） |

**明确不属于 seam 的部分**：四表建表/外键/索引、备份资产枚举、CAS SQL、checkpoint/GC 预算、staging/LIVE 状态机——
全部在批次 30 的 `core/database` 面自行落地，本批不预设形状（避免"新增必须指认消灭的失败"被违反：这些机制没有本批的消费方）。

**范围矛盾需主会话一句话定（S3）**：裁定 5 的"度量侧环境指纹加入 Paint flags 项"落点是
`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualContentMeasuring.kt`（该文件 git 干净、不属任何他线 M），
但**不在本批落点清单**内（计划 §2 落点为 core/data visual、tools/visual_compiler、docs）。
两条合法解法，本线不预选：**(i)** 把 app visualjs 明确并入本批落点（只改指纹一行 + 跨侧测试）；**(ii)** 本批只在
`core/data` visual 侧定义"回放环境"值（含 flags 字段）并由 painter 消费，度量侧在批次 30/31 接。二者都能消灭
"换字体/换 flags 后仍声称同快照"的失败，差别是批次边界。

---

## 3. 三、R-04 解除路径三选一的公开资料可行性

### 3.1 事实基线（引证据，不重复论证）

E-03 §3.1–3.3 / E-05 三重复现：① **R8 改名 → 测试体执行前崩**（`NoClassDefFoundError: Lkotlin/jvm/internal/Intrinsics;`
+ `Landroidx/tracing/Trace;`）；② **instrument 签名门**（AMS 原文："package …test does not have a signature matching
the target …"）；③ **非导出服务** shell/root 均拒启（`Requires permission not exported from uid …` / `Background start
not allowed`）。可交付半边：`libquickjs.so` 双 ABI 在包（924,936 / 911,248 B）。
**方法学事实（本轮的判定关键）**：批次 27/28 的做法是"装 release 主包 + 装 debug `androidTest` 包（重签）"
（E-03 §2.2：`install -r -t <debug androidTest.apk>` → `apksigner sign --ks … <debug androidTest.apk>`）。
E-03 §3.2 已自行点出原因："**debug 构建的 androidTest APK 与 R8 release 主包结构不兼容（AGP 默认 `testBuildType=debug`）**"。

### 3.2 机制依据（本轮取到的公开/一手材料）

| 事实 | 来源（本轮实读） |
|---|---|
| **`testProguardFiles` 存在且语义 = "处理 test code 以套用与主代码相同的混淆"** | ① 本地 AGP 9.3.0 `gradle-api-9.3.0.jar` 的 `com/android/build/api/dsl/VariantDimension.class`：`javap -p` 输出含 `getTestProguardFiles()`、`testProguardFile(java.lang.Object)`、`testProguardFiles(java.lang.Object...)`；② 官方 DSL 参考（developer.android.com 镜像站点 `developer.android.google.cn/reference/tools/gradle-api/9.3/com/android/build/api/dsl/VariantDimension`）原文："Adds proguard rule files to be used when processing test code. **Test code needs to be processed to apply the same obfuscation as was done to main code.**" |
| **`testBuildType` 可把仪器化测试指向非 debug 变体；默认 debug** | 官方文档 `developer.android.google.cn/studio/test/advanced-test-setup`（API 9.3 文档集）原文："By default, all instrumentation tests run against the debug build type. You can change this to another build type by using the `testBuildType` property in your module-level build.gradle file."；本地一手：`javap com/android/build/api/dsl/TestedExtension.class` → `getTestBuildType/setTestBuildType`，且 `ApplicationExtension extends CommonExtension, ApkExtension, TestedExtension` |
| 本仓现状：`testBuildType` **零实施** | `grep -n "testBuildType" app/build.gradle.kts` → 无输出；E-05 头注亦记"`app/proguard-rules.pro` 与 `app/build.gradle.kts` git 干净 = keep 规则/`testBuildType` 零实施" |
| 本仓已有 **debug 签名 + 开 R8** 的变体 | `app/build.gradle.kts:69-76` `create("internal") { isDebuggable = true; signingConfig = debug; isMinifyEnabled = true; proguardFiles(…release 同集) }`；`benchmark`（`:58-67`）同型但不可调试；`release`（`:77-85`）走 `signingConfigs.release`（`:110-125` 由 `RELEASE_*` 环境变量注入，缺则静默跳过、由 `validateReleaseSigning` 兜底） |
| 本仓 keep 规则**不含** visualjs/AIDL/引擎 | `grep -n "visualjs\|quickjs\|IVisualJs" app/proguard-rules.pro` → 无命中（文件内只有 Room 一条 + ML Kit 三条 keep） |

### 3.3 逐项可行性

**（a）keep 规则（在 `app/proguard-rules.pro` 对 `com.tingyun.smartmistakebook.visualjs.**`、AIDL 接口、引擎请求/结果类型加 `-keep`）**
- 可行性：**技术可行**（R8 keep 规则是官方机制，本仓已有两条同类先例，见上表末行）。
- 但**单靠它不闭合 R-04**：它解决 ①（名字可解析），不解决 ②——签名门要求 test 包与目标包**同证书**；用生产 key 就仍需
  有生产签名材料（本仓无，`RELEASE_*` 由环境变量注入）。若配"一次性本地 key 的 release 包"（批 27 已如此做），则②可过。
- 代价（发布语义）：整条 visualjs/AIDL/引擎边界在**生产 release** 保持原名 → 混淆面收窄；且被 keep 的路径不再是"改名场景"，
  对 R8 风险的覆盖是"R8 收缩+优化"而非"R8 改名"。是否可接受属发布语义裁定，非技术判断。
- 判定：**可行但需主会话接受混淆面代价**；端到端 UNVERIFIED（未跑 Gradle/设备）。

**（b）可测 release 变体（让 androidTest APK 针对被测的 minified 变体构建，由 AGP 用**同一 mapping** 混淆 test code）**
- 两种子形态，代价不同：
  - **b1（保留"release"字面口径）**：`testBuildType = "release"` + 用 `RELEASE_*` 指向一次性本地 key（或先跑 `validateReleaseSigning` 的替代通道）
    + 用 `testProguardFiles` 只 keep **测试入口/测试类名**（JUnit 反射按名加载）→ 构建 `assembleStrictOfflineReleaseAndroidTest` 并 `am instrument`
    对应 test 包。**主包混淆面不削弱**（test APK 里被 keep 的名字本来不在主包里）。
  - **b2（零新变体但口径漂移）**：`testBuildType = "internal"`（已存在、minified+debuggable+debug 签名）→ 主包与 test 包同一 debug 证书，
    ②自然消失；但被测的是 **internal** 而非 **release**，R-04 判据文本要改（与 (c) 交叉）。
- 代价：`testBuildType` 是**模块级全局**开关，会把现有设备轨（`assembleStrictOfflineDebugAndroidTest`，E-05 step00 用法）一起改向；
  若要保留两条轨，需要一条属性门（如 `findProperty(...)` 默认 debug）——这是新增配置面，须指认它消灭的失败（= 同时保留日常 debug 轨与 R-04 轨）。
- 判定：**公开资料支持，且是三者中最贴合"可测 release 变体"原意的路径**；但"AGP 9.3 + app 模块 AIDL + 隔离进程 + AGP 会把 test dex 按同一 mapping 改写"
  这一组合的端到端事实 **UNVERIFIED**（本轮不能构建；需一次 `assemble…AndroidTest` + `am instrument` 实测）。
- **与此前结论的差异（须登记）**：E-03 §6 第 2 项把该路径写成"`testBuildType`/独立测试变体 **+ `-dontobfuscate`**——同样削弱"。
  按本轮官方 DSL 原文，`-dontobfuscate` **不是必要条件**；混淆面的削弱只限于 test 包里被 keep 的名字。E-03 该句应视为
  "当时的保守表述"，不作为否决该路径的依据。

**（c）判据改口径（把 R-04 v2 的"release 上跑隔离往返两例"改为"release 产物级审计 + warm-up 级运行证据"）**
- 可行性：**纯政策项**，技术上永远可执行（改清单 `:228` 文本与 E-05 判据列）；公开资料无法也**不应**替代主会话裁定。
- 代价：放弃"release 变体上真实往返"这一唯一直接证据，退化到"产物内 .so 在包 + 类名审计 + 非 release 的 warm-up"；
  R8 后 JNI 面维持 **UNVERIFIED**，且与 KD-69/清单 §1.4 的"R8 后 JNI 面"表述冲突需同步改。
- 判定：**可行但降低证据等级**；只有在前两条都被主会话否决时才应选。

### 3.4 本线建议（建议，不是裁定）

优先级 **b1 > b2 > a > c**：b1 同时闭合①②且不削弱主包混淆面，且判据字面不变；a 可作为 b1 的补充（若实测发现 AGP 未覆盖某反射点）；
c 最后。**前提不变**：E-03 §6 第 4 条——任何路径都要先有可跑的 `Rendered` 往返（批次 28 修复轮后设备复验 UNVERIFIED）。
另需注意 ③ 大概率不是仪器化路径的阻塞（**推断**：instrumentation 把测试跑在目标 app 进程内，同 uid `bindService` 到非导出服务不受"not exported"限制；与 shell/root 拉服务是两回事；本轮未实测）；③ 只挡"无 instrument 的独立 worker 采证"通道。

### 3.5 来源等级与未验证（如实）

| 材料 | 等级 | 说明 |
|---|---|---|
| AGP 9.3.0 `VariantDimension.testProguardFiles` / `TestedExtension.testBuildType` 存在 | **官方一手（本地构件）** | `javap` 直读 jar 字节码，可复跑 |
| `testProguardFiles` 语义 ="test code 套用主代码同一混淆"；`testBuildType` 默认 debug | **官方（文档站点镜像）** | 经 `developer.android.google.cn`（官方文档镜像）取得；`developer.android.com` 与 `issuetracker.google.com` 本轮**连接超时不可达**（`WebFetch` 两次均 connect timeout 10 s；`curl https://developer.android.com/build/shrink-code` → `(28) Connection timed out after 20009 ms`），故 AGP issue #126429384 的实时状态**本轮未能复核**（前批登记为官方 tracker，见 `docs/research/2026-10-28-mva-remaining-order.md:224`） |
| 签名门（test 包须与目标包同证书） | **官方行为 + 本仓设备实测** | 官方文档页本轮未检索到明文（instrumented-tests 页 `signature` 0 命中）；依据是本仓 AMS 原文（E-03 §3.1） |
| "按 b1 构建的 test APK 能在本仓 AGP 9.3 上跑通隔离往返" | **UNVERIFIED** | 需 `assemble…AndroidTest` + `am instrument` 实测；本轮禁跑 Gradle/设备 |
| 社区旁证（testProguardFiles/专用 testable 变体为通行解法） | **单一来源，待验证** | 前批登记（mva-remaining-order `:224` 的 tir38/Stack Overflow 两条），本轮网络受限未复读 |

---

## 4. 本轮未验证项（如实）

- **UNVERIFIED**：`java -version`、SDK 37 / build-tools 37、Gradle 9.6.1 可用性（J4 后半，禁跑 Gradle）。
- **UNVERIFIED**：锁分支 A 的任何实际产物（15 锁、负控红、CI step）——本轮只做判定，未执行。
- **UNVERIFIED**：b1/b2 两种 `testBuildType` 路径在本仓的端到端可行性（未构建、未上设备）。
- **UNVERIFIED**：AGP issue #126429384 的当前状态与正文（网络不可达）。
- **UNVERIFIED**：`developer.android.com` / `issuetracker.google.com` 直连；本轮所有官方文档均经 `.cn` 镜像站点取得（内容为同一官方文档集，但"镜像 = 官方"这一点属站点性质判断，未独立复核）。
- 本轮未改任何工作树文件（本文件为唯一写盘产物）；未运行任何测试/构建/设备命令；未做任何 git 写操作。

## 5. 可重跑命令（本轮实际执行）

```bash
git rev-parse --short HEAD                                   # 3d9372e5
git rev-list --left-right --count origin/main...HEAD         # 0	0
git status --porcelain=v1 -- settings.gradle.kts core/ui/build.gradle.kts \
  feature/tutor/build.gradle.kts core/markdown/build.gradle.kts \
  core/export/build.gradle.kts .gitignore                    # 5 M + ?? core/markdown/build.gradle.kts
git cat-file -e HEAD:core/markdown/build.gradle.kts          # fatal: exists on disk, not in HEAD
git show HEAD:settings.gradle.kts | grep -c '":core:markdown"'   # 0
git show HEAD:core/ui/build.gradle.kts | grep -c 'richtext.commonmark'      # 1（RED）
git show HEAD:core/export/build.gradle.kts | grep -c 'kotlinx.serialization.json'  # 0（RED）
git diff --cached --name-only | grep -i lockfile             # 无输出，exit 1
git status --porcelain=v1 | wc -l                            # 674（首次）/ 676（复测）
git status --porcelain=v1 | cut -c1-2 | sort | uniq -c        # 201 M / 475 ??
git ls-files '*gradle.lockfile' | wc -l                      # 0
git status --porcelain=v1 | grep -i lockfile | wc -l         # 13
grep -n ":benchmark" settings.gradle.kts                     # :33
find . -name '*gradle.lockfile' -not -path './.git/*' -not -path '*/build/*' | wc -l   # 14
python tools/ci/check_lockfiles.py                           # EXIT=0（13 未跟踪锁 / :benchmark 缺锁）
python tools/ci/check_dependency_pins.py                     # EXIT=0（quickjs 1.0.15 / stdlib 2.3.21）
grep -n "STUDY_DATABASE_VERSION" core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt  # :134 = 69
grep -n "TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = " core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt  # :546 = 18
grep -n "testBuildType" app/build.gradle.kts                 # 无输出（零实施）
grep -n "visualjs\|quickjs\|IVisualJs" app/proguard-rules.pro # 无输出（无 keep）
grep -n "DRAW_SCHEMA_VERSION" -r core/data/src/main --include=*.kt   # 引擎 :905 / painter :303-307 / 校验器字面量 :82
grep -rln "roundtrip\|往返\|byte-identical" core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/ \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/   # 0 文件
javap -p -classpath <AGP9.3/gradle-api-9.3.0.jar> com.android.build.api.dsl.VariantDimension | grep -i proguard
javap -classpath <AGP9.3/gradle-api-9.3.0.jar> com.android.build.api.dsl.ApplicationExtension | head -3
curl -sSL "https://developer.android.google.cn/reference/tools/gradle-api/9.3/com/android/build/api/dsl/VariantDimension?hl=en"  # 含 testProguardFiles 语义原文
curl -sSL "https://developer.android.google.cn/studio/test/advanced-test-setup?hl=en"   # 含 testBuildType 原文
```
