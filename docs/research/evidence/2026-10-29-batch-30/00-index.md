# 批次 30 设备证据索引（执行 2026-10-29 02:25–02:41 +08:00）

- 计划：`.zcode/plans/plan-2026-10-29-batch-30-device-persist-fixes.md`（先完整读）
- 判据：`docs/research/2026-10-29-batch-30-device-criteria.md`（T1；本轨按其 §5 串行）
- 规范：`docs/research/2026-10-26-device-evidence-convention.md` v1（R4）
- 记录：`docs/research/2026-10-29-batch-30-record.md`（本轨不写记录，记录员后写）
- 证据目录：`docs/research/evidence/2026-10-29-batch-30/`
- 本目录体积：11 个 `.md` / 88 KiB（R4 §3.2 实测；含 1 份跨轨文件，见 §1.1）
- HEAD：`a806525e`；工作树 dirty（他线 D-01/D-02/解析器在飞；本轨**零写入主源/测试**）
- 执行者：设备验证轨（subagent，批次 30）
- 时间口径：`executed-at` = 主机时钟（+08:00）；logcat 时间戳为设备 UTC（= 主机 −8 h）
- 纪律：零 `connectedAndroidTest`、零 `adb root`、零 git 写操作；Gradle 仅
  `:app:assembleStrictOfflineDebug` / `:app:assembleStrictOfflineDebugAndroidTest` /
  `:core:data:assembleDebugAndroidTest` / `:core:database:assembleDebugAndroidTest`
- **结论先行**：7 个套件绿（隔离 13、flags 2、painter 24、PDF 16、迁移矩阵 15、提交 8、仓库 3），
  **2 个套件红**（parity 1/11、备份 8/34），**1 个部分红**（69→70 迁移 1/2）；**冷恢复设备面 UNVERIFIED**。
  两个红均**未放宽断言**：parity = A（原型门未标定阈值，被 KD-78 ④ 的 AA-off 修复触发）；
  备份 = C（D-01 schema 70 未同步备份计划/裁剪器）+ B（一条测试期望仍写 69）。
- **本批验收「五套件全绿」「备份 34 例不回退」均不成立**。

## 1. 环境 / 产物身份

| 字段 | 值 |
|---|---|
| serial | `emulator-5562`（test_device；未触碰 `-5566`/`-5584`） |
| API / ABI / PAGE_SIZE | 34 / x86_64 / 4096 |
| fingerprint | `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys` |
| 沉降 | uptime 14060 s、load 0.00（无需再等） |

| 产物 | sha256（宿主=设备） |
|---|---|
| `app-strictOffline-debug.apk` | `1429ee0e41a20c269825172bc16cf5cf006e1525ff413748f2219fa946b04ad3` |
| `app-strictOffline-debug-androidTest.apk` | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` |
| `data-debug-androidTest.apk` | `cb88851b828115f280061eb1b256cfa2f04111047576a8917ab62617ae4b1951` |
| `database-debug-androidTest.apk` | `c07669639e269eee7c64bfb2264aa44890cee56e9f7d172a455c3b74fa38d49b` |

三包均与批次 29 旧包不同；dex 符号门两包 `DEX OK`；构建前后 779 个源文件 sha256 快照 `diff` 为空。
详见 `step00-environment.md`。

### 1.1 同目录跨轨文件（非本次设备轨）

| 文件 | 来源 | E-ID | 说明 |
|---|---|---|---|
| `step-aud08-page-ruling.md` | AUD-08 生产者半边（计划 §1 任务 10；生成时刻 2026-10-28T17:11:02Z） | E-A08-T1…T3 | 切分金标门判定，门状态 **FAIL**（①0.5714 vs 0.9）；**不属于**本批设备 7 绿 + 2 红 + 1 部分红口径。该文件 §2/§3 用仓库绝对路径（盘符开头，3 处），按 R4 §5.3 应由该轨改 `<repo>`；**本轨只登记、不改他轨文件**。 |

## 2. 步骤与结论

| step | 内容 | 用例数 | 结果 | 判定 |
|---|---|---|---|---|
| `step01-isolation.md` | 隔离 | 13 | `OK (13 tests)`，Time 60.277 | **PASS**；spawn↔ready 17↔17 逐 pid；failed=0；kill 四类=17 |
| `step02-flags.md` | 跨侧 flags | 2 | `OK (2 tests)` | **PASS**；KD-78 ④ 九项全满足 → **④ 可关闭** |
| `step03-painter.md` | painter | 24 | `OK (24 tests)` | **PASS**（批次 29 的 1283 红未复现） |
| `step04-pdf.md` | PDF | 16 | `OK (16 tests)` | **PASS**；KD-79 台账最低口径满足；D3 四缺口 UNVERIFIED |
| `step05-parity.md` | 一致性原型门 | 11 | `Tests run: 11, Failures: 1` | **FAIL**：P07 `F4_GEOMETRY` centroid 0.5181>0.5 |
| `step06-backup.md` | 备份 | 34 | `Tests run: 34, Failures: 8` | **FAIL**：7×裁剪器 schema 69 vs DB 70；1×期望仍写 69 |
| `step07-persistence-migration.md` | 迁移 1→70 / 69→70 / 提交 / 仓库 | 15 / 2 / 8 / 3 | `OK (15)` / `Failures 1` / `OK (8)` / `OK (3)` | 矩阵 PASS；69→70 **A 类测试断言**；提交/CAS PASS |
| `step08-signatures.md` | 9 类签名 + 生命周期 + 专项 | 11 份 logcat | 全 0 | PASS（含 1 处判据行数偏差登记） |

逐套件的 `step07-persistence-migration.md` 之外，冷恢复设备用例不存在（`grep -rn VisualSnapshotRestore`
在三个 androidTest 源集 = 0 命中）→ **UNVERIFIED**。

## 3. 红例归因（判据 §7.5 七字段，均未放宽）

### R1 — parity `thePrototypeGateJudgesEveryPositiveFixture` / P07（**A**）

- 失败：`P07 must not be red: F4_GEOMETRY reasons=[text bboxDev=1px centroid=0.5181329158118464px]`
  （`VisualDrawOpsParityInstrumentedTest.kt:106`）；复跑同值，确定性。
- 根因链（同设备前后一手 raw log）：KD-78 ④ 修复把回放 `textPaint.flags` 显式置 0（AA 关，
  `VisualDrawOpsPainter.kt:54`）。批次 29（修复前，AA 开）P07 `maskS=1434 centroid=0.44 → F2_AA`；
  本批（修复后，AA 关）P07 `maskS=1224 centroid=0.52 → F4_GEOMETRY`。PDF 侧掩膜 1431 不变
  （PDFium 仍 AA）。屏掩膜 −210 px 与"文本 AA 关"逐项一致。
- 门阈值是 R2 §4.2 **初始未标定值**（`VisualDrawOpsParityMetrics.kt:185,847-848`；
  `docs/research/2026-10-28-screen-pdf-parity-gate.md:206,214`），R2 自己把文本 AA 标为"不可比"
  （同文档 `:71,204`；`VisualDrawOpsPdfPainter.kt:47`）。
- 处置：**零改动**；阈值/判决面交 R2 门所有者裁定后重跑。**不得**把期望改成 0.518（那是放宽）。
- 门分辨力未损：`PARITY_CONTROL`=10 全 `red=true`，`DESENSITIZED`=0，`F5`=0。

### R2 — 备份 suite 8 红（**7×C + 1×B**）

- **C（7 例）**：`BackupSnapshotTrimException: 快照 schema=70，裁剪器只支持 schema=69`。
  `StudyDatabase.kt:139` 已 = 70；`SmbkSelectionPlan.kt:39` 仍 = 69；门在
  `BackupSnapshotTrimmer.kt:267-271`。计划 §1 任务 3 要求 D-01"备份资产枚举同步"（计划 `:19`），
  未完成 → schema-70 库的备份/恢复全路径 fail-closed。归属 D-01（T2）。
- **B（1 例）**：`schema68ArchiveRestoresAndMigratesTo69` 期望 69、实际 70
  （`BackupRestoreInstrumentedTest.kt:588-591`）。权威值 = `STUDY_DATABASE_VERSION`（70）；
  应改成权威常量（非"改实测值"，不属放宽）。归属 D-01（T2）。
- **非 A/D**：同设备、新 APK、sha256/dex 已核；批次 29 同套件 `OK (34 tests)`。

### R3 — `TutorVisualSnapshotMigration_69_70InstrumentedTest` 1/2（**A**）

- `expected:<CREATE UNIQUE INDEX[]> but was:<CREATE UNIQUE INDEX \`index_…\` ON \`tutor_visual_snapshot\` (\`program_id\`, \`state_sha256\`, \`draw_commands_sha256\`)>`
  （`TutorVisualSnapshotMigration_69_70InstrumentedTest.kt:206`）。
- 迁移 DDL 用 `CREATE UNIQUE INDEX IF NOT EXISTS`（`VisualSnapshotMigration_69_70.kt:112-115`），
  `sqlite_master` 返回的 SQL 已无 `IF NOT EXISTS`；测试的 `substringBefore(" IF NOT EXISTS")`
  不裁剪，整条 DDL 与裸前缀比较失败。**UNIQUE 索引与三列键实际存在且正确**（见 `but was:`）。
- 处置：测试断言应改（存在 + 键列 + UNIQUE 或 `startsWith`），**不是放宽**；归属 T2（`??` 新文件）。

## 4. KD 裁决（只按本批证据）

| KD | 结论 | 依据 |
|---|---|---|
| KD-78 ④ | **可关闭（④ 单项）** | ④-P1…P4 ∧ ④-N1/N2 ∧ ④-L1…L3 全满足（`step02-flags.md` §3）；红→绿同设备跨批：批次 29 同断言 `expected 0 but was 1283`，本批 painter 24/24、flags 2/2、隔离 13/13 绿 |
| KD-78 整条 | 保持 open | ①② 生成侧缺口不在本批设备面，按关闭条件（`docs/known-defects.md:2890`）四项另行核销 |
| KD-79 | **可关闭（按 KD 自身关闭条件）** | 79-P1+P2+L1+L2 满足；计划 C.6 另需 P3+N2 亦满足。关闭条件原文 `docs/known-defects.md:2925`（判据文档标 `:2921` 为行号漂移，见 §5） |
| KD-79 的 D3 | **不得声称覆盖** | ENOSPC/静默短写/页数不符/`renameTo` 失败设备面无用例 → UNVERIFIED；计划任务 6 的“磁盘满模拟”未做 |
| 一致性门 | **仍是原型门** | `scope=PROTOTYPE product-gate=no ci-gate=no release-gate=no`；本批红，不得引用为产品/发版结论 |

### 4.1 关闭建议（绿则给建议；红则给归因）

- **KD-78 ④（建议关闭）**：`2026-10-29 批次 30 关闭 ④：painter 24/24、跨侧 flags 2/2、隔离 13/13 绿；warm-up finished=17=Start proc、failed=0；三份 logcat flags 失配 0；同设备跨批红→绿。证据 research/evidence/2026-10-29-batch-30/step02-flags.md §3（E-02）、step01-isolation.md §3（E-01）、step03-painter.md §2（E-03）。` 整条 KD-78 保持 open（①② 另核销）。
- **KD-79（建议关闭）**：`2026-10-29 批次 30 关闭 KD-79：API 34 PDF 16/16 绿，pdfWriteFailureIsCoded 绿（PAINTER_PDF_IO 可达），logcat 230 条 native 模拟 IOException；证据 research/evidence/2026-10-29-batch-30/step04-pdf.md §3（E-04）、step08-signatures.md §4（E-08）。` 附注：KD 原文/计划写 13 例，现树 16 例（C.6 新增 3 例）——关闭按语义条件（有码异常可达 + native 打印仍在），不按计数；D3 四缺口仍 UNVERIFIED，不得写成已覆盖。
- **红例归因（不放行）**：parity P07 = A（原型门未标定阈值 × ④ 的 AA-off），交 R2 门所有者裁定后重跑；备份 8 红 = C×7（D-01 schema 70 未同步备份计划/裁剪器）+ B×1（期望仍写 69），归 D-01；69→70 = A（测试断言按裸前缀比 `sqlite_master` 的 DDL），归 T2。

## 5. 判据漂移（登记，不改判据）

1. 判据 §2.5-2"`PARITY_GATE` 有且仅有一轮"实测 **11**：`@Before` 每例打印（`VisualDrawOpsParityInstrumentedTest.kt:79-86`）。
2. 判据 §2.5-5"`PARITY_F5` ≤2"：本批 0，成立。
3. 判据 §2.5-6"绿轮 `PARITY_DIAGNOSTIC_WRITTEN`=0"：本批红轮 =1，符合失败诊断语义。
4. 判据文档 §4 把 KD-79 关闭条件标为 `docs/known-defects.md:2921`；本轨实测该行是 **Impact**，Reopen/关闭条件在 **`:2925`**（`:2921` 行号漂移，证据按 `:2925` 引用）。
5. 现树 PDF 套件 **16 例**（C.6 新增 3 例后），KD-79 原文与计划写 **13 例**——计数漂移；关闭按语义条件不按计数（见 §4.1）。

## 6. 未验证项（不虚构）

- **UNVERIFIED**：一致性门绿轮（阈值裁定后需重跑）；正例 P08 因 P07 先 `fail()` 中断而未被判决。
- **UNVERIFIED**：备份 34 例在 D-01 同步后的绿轮；`BackupSelection*`/`BackupSnapshotTrimmer*` 等其它
  备份套件本批未跑。
- **UNVERIFIED**：冷恢复（D-05/T4）设备面——无用例；JVM 12 例不在本轨权限内执行。
- **UNVERIFIED**：KD-79 的 ENOSPC / 静默短写（设备面）/ 页数回读不符 / `renameTo` 失败。
- **UNVERIFIED**：KD-78 ④ 的逻辑负例（把常量改回 1283）未执行。
- 跨设备/arm64/真机/16 KB 页/生产签名/真实 Provider 不在本轨道。

## 7. raw log

全部原始日志在 `build/device-track-batch30/`（gitignored，非证据）：
`step01`–`step10` 各 `.log` + `-logcat.txt`、`apk-host-sha256.txt`、`apk-device-sha256.txt`、
`source-snapshot-before.txt`、`source-snapshot-after-build.txt`。

## 8. R4 合规（体量 / 脱敏 / 入仓，2026-10-29 本轨实跑）

- **体量**：11 个 `.md`，最大 `00-index.md`（12.9 KiB / 161 行），目录 **88 KiB**；R4 §3.1 上限为单文件 64 KiB/400 行、单批 512 KiB/16 个 `.md` → 全部满足。
- **脱敏扫描（R4 §4.4 四条正则，目录级）**：

| 扫描 | 结果 |
|---|---|
| 密钥/令牌（`sk-`/`AIza`/`ghp_`/`Bearer`） | **0 命中** |
| 凭据赋值（key/password/secret/token 形式） | **0 命中** |
| 邮箱 | **0 命中** |
| 主目录前缀（Windows / macOS / Linux） | **0 命中** |

- **补充扫描（非 R4 必检，如实登记）**：
  - 手机号（11 位、`1[3-9]` 前缀）→ **3 命中**，均为 §3 R1 行的同一长小数（另见 `step05-parity.md` §2 的两次复述），**非 PII**。
  - 仓库绝对路径（盘符开头）→ **3 命中**，仅在跨轨文件 `step-aud08-page-ruling.md:21,22,27`；已在该文件归属说明（§1.1）登记，本轨不改他轨文件。
- **入仓**：11 份均 `git check-ignore` 无命中（`.md` 可跟踪）；`build/device-track-batch30/` 被 `.gitignore:15` 忽略，只作本机全量，**不作关闭依据**。

## 9. 回链检查

- [x] 每个 step 头部有 `raw-log` 与 `sanitized` 字段；`step01`–`step08` 各附“脱敏与去噪”节
- [x] 每个红例有第一项目帧 + 四源核对 + A/B/C/D 分类 + 不放宽声明
- [x] 没有把 `build/` 路径当关闭依据（只出现在 `raw-log`）
- [ ] 批次 30 记录 §4 设备行回链本索引（记录员后写；本轨不写记录）
- [ ] `docs/known-defects.md` 的 KD-78 ④ / KD-79 Progress 回填（主会话按 §4.1 建议裁定后写）
