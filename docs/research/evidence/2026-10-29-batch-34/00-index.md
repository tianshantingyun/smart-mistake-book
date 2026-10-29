# 批次 34 设备证据索引（2026-10-29）

- 记录：`docs/research/2026-10-29-batch-34-record.md`（记录员后写；本轨不建）
- 计划：`.zcode/plans/plan-2026-10-29-batch-34-dual-backend-kd80.md`
- 判据：`docs/research/2026-10-29-batch-34-device-criteria.md`
- 规范：`docs/research/2026-10-26-device-evidence-convention.md` v1（R4）
- 工作树 HEAD：`db792fa873f4bc150f415b67d1c2e3b1bab6c995`
- 工作树：dirty（721 条：241 `M` / 480 `??`；他线与本批在飞；本轨零 git 写）
- 设备：`emulator-5562`（API 34 / x86_64 / PAGE_SIZE 4096；未触碰 `-5566` / `-5584`）
- 证据目录：`docs/research/evidence/2026-10-29-batch-34/`
- 本目录体积：**11 个 `.md` / 88 KiB（du，块大小；`find -printf '%s'` 实测 68,040 B）**；最大单文件
  10,936 B（`00-index.md`，本文件）；最大行数 212（`step02-backup-rowcounts-trimmer-c4.md`）。上限
  16 个 / 512 KiB、单文件 64 KiB / 400 行，未超。
- 执行窗口：2026-10-29 18:42–19:02 +0800（Gradle 构建 + 15 套件串行）
- 另有他轨文件 `step-t7-doc-ledger.md`（T7 文档轨，非本轨设备证据，未改动）

## 1. 环境

见 `step00-environment.md`。

## 2. 产物身份（宿主 sha256 = 设备 `pm path` + `sha256sum`）

| 产物 | 字节 | sha256 | versionName |
|---|---:|---|---|
| `app-strictOffline-debug.apk` | 189,547,386 | `ead9d4a43c99e2345abb4dc1709005c42225e66e1467169d043bd599def60cad` | `0.2.0-offline` |
| `app-strictOffline-debug-androidTest.apk` | 1,586,361 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` | （空） |
| `data-debug-androidTest.apk` | 197,787,221 | `53e69f485a4bb4fe3d5c7bdff53ebbfb50008011376df1cbc133e4a946397caf` | （空） |
| `database-debug-androidTest.apk` | 196,846,909 | `f75d6fc8b036942a8408b21dff1b6626bc1882e16bd399a48fdb87e70e27b116` | （空） |

四包 `install -r -t` 均 `Success`；设备侧 sha256 与宿主逐字节一致（`step01-build-install-dex.md` §4）。
`data` / `database` 测试包为 18:43 重新打包（含本批新增/补强符号）；`app` 测试包 Gradle 报
`UP-TO-DATE`、sha 与批次 33 相同，已核实 app 测试源自该包生成后无改动（`step01-build-install-dex.md` §4）。

## 3. 步骤与结论

| E-ID | 步骤 | 门/KD | 用例数 | 判定 | 证据 |
|---|---|---|---:|---|---|
| E-00 | 环境 / 设备身份 | — | — | PASS（信息性） | `step00-environment.md:1` |
| E-01 | 一致性门 → assemble* → 源稳定 → 安装 → dex/schema 资产门 | 判据 §2 / §4.2 / §4.3 | — | PASS | `step01-build-install-dex.md:1` |
| E-02 | 备份 34 / rowCounts 逐名 2 / 裁剪器 9 / C4 5 | KD-80 80-C1 / 80-C2 设备侧 / 80-C3 设备 / 80-C4 | 34 + 2 + 9 + 5 | PASS | `step02-backup-rowcounts-trimmer-c4.md:1` |
| E-03 | 结构门 | 结构门 5 | 5 | PASS | `step03-structure-gates.md:1` |
| E-04 | 迁移矩阵 / 69→70 / 70→71 / 提交 / 仓库 | 迁移面 + 提交 12 + 仓库 12 | 15 + 4 + 3 + 12 + 12 | PASS | `step04-migrations-commit-repo.md:1` |
| E-05 | 一致门（原型）/ painter / PDF | 一致门（**非产品门**）/ KD-78 | 16 + 24 + 16 | PASS | `step05-parity-painter-pdf.md:1` |
| E-06 | 隔离 / flags / 12 类崩溃签名 / worker 生命周期 | KD-78 ④ | 13 + 2 | PASS | `step06-isolation-flags-signatures.md:1` |
| E-07 | A-01/A-02/A-03 静态确认（0 远程派发 / 广告未动） | 4E.2 A-01~A-03 | —（无 androidTest 用例） | PASS（静态） | `step07-a01-a03-static.md:1` |
| E-08 | 红例归因 | 判据 §5 | 0 红 | PASS（无红例） | `step08-red-attribution.md:1` |

15 个设备套件全绿：34 / 2 / 9 / 5 / 5 / 15 / 4 / 3 / 12 / 12 / 16 / 24 / 16 / 13 / 2。
设备报告数逐项等于构建前 `grep -c '@Test'` 源计数（仓库 12、提交 12、结构门 5、迁移 15/4/3、
备份 34、裁剪器 9、C4 5、painter 24、PDF 16、parity 16、隔离 13、flags 2、rowCounts 2）。

## 4. KD-80 四条裁决（同一源快照 + 同一 APK 家族）

| 条件 | 证据 | 判定 |
|---|---|---|
| 80-C1 备份 34/34 | E-02；`data` APK `53e69f48…` 的 `OK (34 tests)` | **PASS** |
| 80-C2 静态 + 设备侧逐名 | 静态脚本 `RESULT=PASS` / `EXIT=0`（T7 `step-t7-doc-ledger.md` §2 实跑）；设备侧 `OK (2 tests)`，90 键清单取自同源 asset `4a13d9d0…`（E-02 §4.1） | **PASS** |
| 80-C3 设备 9/9 + JVM 12/17 | 设备 9/9（E-02）；JVM 边由主会话 `--rerun-tasks` 实跑，仓内命令日志 `docs/research/evidence/2026-10-29-batch-33/step-jvm-80c3.md:7-23`（`EXIT=0`、12/0、17/0）；本轨复读 `core/data/build/test-results/testDebugUnitTest/` 两个 XML（mtime 18:37:30，晚于测试源 mtime 10:36 / 16:10）为 `tests="12" … failures="0" errors="0"` / `tests="17" … failures="0" errors="0"`（XML 属 `build/` 旁证，非关闭证据）；本轨**未执行** JVM | **PASS** |
| 80-C4 视觉四表 5/5 | E-02 | **PASS** |

**结论（2026-10-29 证据落盘员复读）**：四条在本轮**同一源快照**（`src-sha-before` 与 `after` 均 864 文件、
`diff` 空）与**同一 APK 家族**（`ead9d4a4…` / `2dd22651…` / `53e69f48…` / `f75d6fc8…`，设备侧 sha 4/4 一致）
上均已取到：80-C1 34/34、80-C2 静态 `RESULT=PASS`/`EXIT=0` + 设备 2/2（90 键逐名清单经脚本与 `71.json`
entities 逐名比对 `match_set True`）、80-C3 设备 9/9 + JVM 12/17、80-C4 5/5。→ **建议主会话/记录员把
KD-80 记为 closed**。唯一口径保留：80-C3 的 JVM 实跑是主会话在批次 33 补证文件里记录的命令日志，
本批未再次执行；若主会话要求"JVM 同批重跑"，则 80-C3 JVM 记 `UNVERIFIED（本轮）`，其余三条仍成立。
本索引只给建议，KD 状态的写动作属记录员/主会话（`docs/known-defects.md` 未由本轨改动）。

## 4.1 A-01 / A-02 / A-03 结论（4E.2 准入项）

| 项 | 结论 | 证据 |
|---|---|---|
| A-01 双后端装配 | **静态 PASS；JVM UNVERIFIED；无红例**：`visualEngine`/`visualFrameInputs` 注入点默认 `null`（`DefaultFigureGenerationCoordinator.kt:153`/`:158`）；JS 分支 `:211` → `generateLocalJs`；无引擎即 `UNAVAILABLE`（`:252`）；`:245-246` 明写不调 `ledger.reserve`；生产 `FigureGenerationCoordinationFactory.create` 两参数默认 `null`（`:790`/`:792`/`:801-802`）。`reservedRemoteDispatches == 0` 的断言在 `FigureGenerationCoordinatorTest.kt:259`/`:317`/`:374`/`:402`，**本轨未运行** | E-07 §3-§4；`step07-a01-a03-static.md:70-95` |
| A-02 工具环 `method=JS` + `program` | **静态 PASS；JVM UNVERIFIED**：`TutorToolLoop.kt:355` 的 JS 分支要求非空 `program` 且拒绝图像载荷；`program` 只进独立字段。两路解析/负向用例已落码（`TutorFigureMethodDualParseTest.kt`），**本轨未运行** | E-07 §3；T7 `step-t7-doc-ledger.md:138-140` |
| A-03 请求 schema `method.enum` | **静态 PASS；JVM UNVERIFIED；广告面未动**：`OpenAiModelProtocol.kt:129-131` 默认 `setOf(IMAGE_MODEL)`，仅 `jsAdvertised` 时 `:485-486` 追加 `JS`；生产快照仍 `IMAGE_MODEL_ONLY`（`FigureGeneration.kt:161-168`；`AttachedImageGenerator.kt:144`、`ConfiguredCleanImageGenerator.kt:95`、`TutorFigureGenerator.kt:372`）；prompt 装配 `FigurePromptAssembler.kt:40`/`:108` 仍 fail-closed | E-07 §3-§4 |

**归因**：三项均无红例（本轨只做只读静态核对，未执行 JVM），故无 A/B/C/D 归因对象。**不得**把本表读成
"A-01~A-03 的 JVM 门已通过"；JVM 执行结果归单测门，本轨 `UNVERIFIED`。


## 5. 缺口与未落盘

1. **80-C3 JVM 半边**：本批**未重跑** `testDebugUnitTest`（本轨只允许 `assemble*`）。关闭证据采用主会话
   批次 33 补证的仓内命令日志 `docs/research/evidence/2026-10-29-batch-33/step-jvm-80c3.md:7-23`
   （`--rerun-tasks`、`EXIT=0`、12/0、17/0），并以 `build/` XML（18:37:30，晚于测试源 mtime）为旁证。
   若要求"同批重跑"，该项记 `UNVERIFIED（本轮）`，须由串行门禁脚本补命令日志。
2. **A-01/A-02/A-03 的 JVM 用例执行**：`UNVERIFIED`（本轨零 `testDebugUnitTest`）；设备面无新增 androidTest
   用例（E-07）。**现树存在 `build/` XML 旁证**（`core/data/build/test-results/testDebugUnitTest/`，
   mtime 18:37:30：`FigureGenerationCoordinatorTest` 17/0、`TutorFigureMethodDualParseTest` 14/0、
   `TutorToolRoundGateTest` 16/0、`FigureGenerationPolicyTest` 22/0、`TutorFigureProposalCallTest` 11/0、
   `RoomFigureGenerationAuditRecorderTest` 4/0；`core/model` 50 XML/516 例、`core/database` 47 XML/226 例
   均 0 失败），且此后无 `.kt` 改动（`find app/src core -name '*.kt' -newermt "2026-10-29 18:37:30"` 为空）。
   但 XML 不是仓内命令日志，**不得**据此写"JVM 门通过"；口径统一为 **JVM UNVERIFIED**。
3. **parity 16** 是原型一致门，不得据此作产品门 / 发版结论。
4. **`app` 测试 APK 未重新打包**：Gradle `UP-TO-DATE`，sha 与批次 33 相同；已核实 app 测试源自 02:27
   包生成后无改动（`find … -newermt` 为空），仍执行了重装。若要求"字节级重打包"的严格口径，需
   `--rerun-tasks` 重跑，但内容等价（非旧源）。
5. **raw log 不入仓**：全量在 `build/device-track-batch34/`（`.gitignore:15`）；本目录只存有界摘要。
6. **设备 logcat 缓冲**：每条套件前 `logcat -c`；签名计数是对当前缓冲区全量 grep，含 0 也记录。
7. **构建 stdout 未留全量**：`step01` 的 `raw-log` 只列 `src-sha-before/after.txt`；`BUILD SUCCESSFUL in 42s`
   / `327 actionable tasks: 19 executed, 308 up-to-date` / `BUILD_EXIT=0` 是运行控制台转录（判据文档
   `docs/research/2026-10-29-batch-34-device-criteria.md:760` 同值），本机无原始文件可复查；可复跑命令在 `step01 §2`。
   该缺口不影响 APK 身份结论（sha/字节数/设备侧 sha 均本轮实测）。
8. **`am instrument` 条数**：可核为 **15 条**（15 个 step 日志各恰有 1 个 `INSTRUMENTATION_CODE`）；
   `step08` 原文的「18 条」无日志支撑，已在该文件 §9 更正。
9. **A-01~A-03 无设备面证据**：本批没有新增 androidTest 用例（`step07` grep `(no androidTest hits)`），
   因此本目录不为三项提供设备 PASS；只能写"静态 PASS / JVM UNVERIFIED"。
10. **复核修复轮新增的测试未进本批设备绿轮**（2026-10-29 复核修复轮落码，**未执行**）：
   `core/data/src/androidTest/.../visual/FigureGenerationLocalAttemptOrdinalInstrumentedTest.kt`（本地 attempt 负序号真库用例）、
   `FigureGenerationReconcilerInstrumentedTest` 新增两例（JS 成功不被判假成功 / 混合操作父状态）、
   `TutorFigureProposalExecutionTest` 新增一例（执行器层 JS fail-closed）。上述 4 例均**不在**本索引 §3 的
   15 套件计数内，执行状态 `UNVERIFIED`。

## 6. 脱敏扫描（规范 §4.4，实跑）

```bash
E=docs/research/evidence/2026-10-29-batch-34
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' "$E" \
  || echo "0 hits: secrets"
grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken)[[:space:]]*[:=][[:space:]]*[^ <]' "$E" \
  || echo "0 hits: credential assignments"
grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$E" || echo "0 hits: emails"
# 第 4 条按规范 §4.4 的 home-path 正则实跑（此处不重复该正则字面量，避免自命中）：
# 0 hits: home paths
```

结果：secrets **0**、credential assignments **0**、emails **0**、home paths **0**。
（首次扫描命中了 `step01` 里一行示例化主目录写法，已改写为"本机临时日志目录（已省略）"后复扫为 0。）

## 7. 回链检查

- [ ] 记录 §4 的每个设备数字都有 `E-ID + path:line`（记录员后写）
- [ ] `docs/known-defects.md` 的 KD-80 Progress 引用本索引（记录员/主会话）
- [x] 每个 E-file 都有 `sanitized` 字段，§4.4 扫描 0 命中
- [x] 没有把 `build/` 路径当作关闭证据（只出现在 `raw-log` 字段）

## 8. R-04 条件轨登记（2026-10-29 复核修复轮补登）

计划 §1 第 6 项的验收是“最小验证通过 **或** 按热区停项登记”。本批**没有**跑 Gradle 的 release
测试变体，因此走第二条：**停项登记，不改回已落改动**。

- **已落改动（现树，非本轨新改）**：
  - `app/build.gradle.kts`（会话开始即 ` M`）：`val r04TestBuildType = providers.gradleProperty("smbk.testBuildType").orNull ?: "debug"`；`android { testBuildType = r04TestBuildType }`；`buildTypes.release { testProguardFiles("r04-test-keep.pro") }`。`git diff app/build.gradle.kts` 只有这两段（+14 行），没有覆盖他线内容。
  - `app/r04-test-keep.pro`（`??`，36 行）：只 keep 测试类/`@Test`/runner；`grep -cE "dontobfuscate|dontshrink|dontoptimize"` = **0**（不削弱主包混淆面）。
- **默认行为零变化**：属性未传时 `testBuildType = "debug"` = AGP 默认值，日常 `assembleStrictOfflineDebugAndroidTest` 不被改道。
- **未验证（UNVERIFIED）**：`./gradlew -Psmbk.testBuildType=release …` 未执行；release 测试轨的两例隔离往返**没有运行证据**。不得写“R-04 已解除”。
- **复跑入口**：`./gradlew -Psmbk.testBuildType=release :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest`，再按隔离两例跑 `am instrument`；结果落 E-file 后才可把本项改为“最小验证通过”。
