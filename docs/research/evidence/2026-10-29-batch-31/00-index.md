# 批次 31 设备证据索引（执行 2026-10-29 06:20–06:54 +08:00）

- 计划：`.zcode/plans/plan-2026-10-29-batch-31-persistence-hardening.md`（先完整读）
- 判据：`docs/research/2026-10-29-batch-31-device-criteria.md`（T1；本轨按其 §1.3 计数冻结与 §4 串行执行）
- 规范：`docs/research/2026-10-26-device-evidence-convention.md` v1（R4）
- 记录：`docs/research/2026-10-29-batch-31-record.md`（本轨不写记录，记录员后写）
- 证据目录：`docs/research/evidence/2026-10-29-batch-31/`
- 本目录体积：**15 个 `.md` / 120 KiB**（R4 §3.2 实测；上限 16 个 / 512 KiB，未超；最大单文件 12706 B（`00-index.md`）；最大行数 176（`step00-environment.md`）；限 64 KiB / 400 行）
- HEAD：`59d4a03f92ab27314d25384a777f41829b3421a2`；工作树 dirty（T3/T4 在飞；本轨**零写入主源/测试/热区**）
- 执行者：设备验证轨（subagent，批次 31）
- 时间口径：`executed-at` = 主机时钟（+08:00）；logcat 时间戳为设备 UTC（本机实测约 −8 h，另有 +2 s 偏差）
- 纪律：零 `connectedAndroidTest`、零 `adb root`、零 git 写操作；Gradle 仅 `:app:assembleStrictOfflineDebug` /
  `:app:assembleStrictOfflineDebugAndroidTest` / `:core:data:assembleDebugAndroidTest` /
  `:core:database:assembleDebugAndroidTest`；未跑任何单元测试任务

## 结论先行

- **11 个套件绿**：备份 34、69→70 迁移 4、迁移矩阵 15、painter 24、隔离 13、flags 2、PDF 16、
  提交 9、仓库 4、结构门 4、裁剪器 9。
- **1 个套件红**：一致性原型门 `Tests run: 15, Failures: 1`（N09-P06）。
  归因 **A（原型门负控自身期望值算错）**：门级判据已红（`red=true`），失败只发生在"质心必须恰好 1.000"
  的等式上；该等式把"屏↔PDF 跨栅格质心距离"当成"同侧位移"。**未放宽任何断言、未改他线文件、未复跑绿轮**。
- **KD-80 不可关闭**：80-C1 PASS、80-C2 PASS、80-C3 PARTIAL（设备 9/9 绿；JVM 12 例本轨 UNVERIFIED）、
  **80-C4 UNVERIFIED**（34 例对视觉四表引用零断言）。KD-80 保持 open。
- **`calibrated-r1` 绿轮 UNVERIFIED**：parity 套件红；N09-P07 因中止未执行。一致性门仍是原型门。

## 1. 环境 / 产物身份

| 字段 | 值 |
|---|---|
| serial | `emulator-5562`（`test_device`；未触碰 `-5566` / `-5584`） |
| API / ABI / PAGE_SIZE | 34 / `x86_64` / 4096 |
| fingerprint | `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys` |
| 沉降 | uptime 28479.31 s、loadavg 0.02 0.02 0.00 |
| 构建 | `BUILD SUCCESSFUL in 37s`，`EXIT=0` |
| 源快照 | 构建前后 859 文件清单 sha256 相同（`74141d32…5e42`），无"构建早于源改动" |
| dex 门 | 三包 `DEX OK`（app 5 / data 19 / database 14 个符号；对 `dex-gate.sh` 复核计数） |
| schema 资产门 | V=70，APK assets 70 个 json，含 `70.json` |
| 计数复读（构建前） | 34 / 4 / 15 / 24 / 13 / 2 / 16 / 15；补充 9 / 4 / 4；trimmer 9 |

| 产物 | 字节 | sha256（宿主＝设备） |
|---|---|---|
| `app-strictOffline-debug.apk` | 189525382 | `a3973a7a584e425ebaa1dc1e07f0cb96fb799fc80b2fe5111963691434ea42cc` |
| `app-strictOffline-debug-androidTest.apk` | 1586361 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` |
| `data-debug-androidTest.apk` | 197763509 | `34151d4d9571daffc27e6a370172afb6775b7e730742dc3484ffd5fdb2e7b952` |
| `database-debug-androidTest.apk` | 196823330 | `4e54f0de476712add5bc839dec16a419b3927a4fdfc27bf41beca4e290c7da2d` |

四包 `install -r -t` 均 `Success`，设备侧 `pm path` + `sha256sum` 与宿主逐字节一致。详见 E-00。

## 2. 步骤与结论

| E-ID | 步骤 | 门/KD | 用例数 | 判定 | 证据（本批目录内） |
|---|---|---|---|---|---|
| E-00 | 环境 / 构建 / 安装 / dex+assets 门 | 判据 §4.0–§4.3 | — | PASS（信息性） | `step00-environment.md:1` |
| E-01 | 备份 `BackupRestoreInstrumentedTest` | KD-80 80-C1 | 34 | **PASS**（8 条旧红全绿） | `step01-backup.md:1` |
| E-02 | 69→70 `TutorVisualSnapshotMigration_69_70InstrumentedTest` | 迁移 A 类修复绿轮 | 4 | PASS | `step02-migration-69-70.md:1` |
| E-03 | 迁移矩阵 1→70 | 迁移矩阵门 | 15 | PASS（70 个版本点） | `step03-migration-matrix.md:1` |
| E-04 | painter `VisualDrawOpsPainterInstrumentedTest` | KD-78 ③/④ | 24 | PASS | `step04-painter.md:1` |
| E-05 | 隔离 `VisualJsIsolationInstrumentedTest` | KD-78 ④-P3/P4 | 13 | PASS（17↔17 pid） | `step05-isolation.md:1` |
| E-06 | flags `VisualMeasureReplayPaintFlagsInstrumentedTest` | KD-78 ④-P2 | 2 | PASS | `step06-flags.md:1` |
| E-07 | PDF `VisualDrawOpsPdfPainterInstrumentedTest` | KD-79 79-P1/P2/P3/N2 | 16 | PASS（native 230） | `step07-pdf.md:1` |
| E-08 | 一致性原型门 `VisualDrawOpsParityInstrumentedTest` | 原型门 / `calibrated-r1` | 15 | **FAIL**（1：N09-P06，A 类） | `step08-parity.md:1` |
| E-08b | 同一包复跑（D 排除） | 原型门 | 15 | **FAIL（逐位同值）** | `step08b-parity-rerun.md:1` |
| E-09 | 提交 9 / 仓库 4 / 结构门 4 | 计划任务 3–5 设备面 | 17 | PASS | `step09-persistence-structure.md:1` |
| E-10 | 9 类签名 + 专项 logcat + worker 生命周期 | 判据 §5 | 12 份 logcat | PASS（全 0；PDF 230>0） | `step10-signatures.md:1` |
| E-11 | 裁剪器 `BackupSnapshotTrimmerInstrumentedTest` | KD-80 80-C3（设备） | 9 | PASS | `step11-trimmer.md:1` |
| E-12 | KD-80 四项关闭门 | **KD-80** | — | **PARTIAL → open** | `step12-kd80.md:1` |

## 3. KD-80 覆盖

| KD-80 要求（`docs/known-defects.md`） | E-ID | 状态 | 备注 |
|---|---|---|---|
| 80-C1 备份 34/34 设备绿 | E-01 | **有 / 满足** | `OK (34 tests)`，8 条旧红逐条 `STATUS_CODE=0` |
| 80-C2 表清单与 `70.json` 逐名一致 | E-12 §4 | **有 / 满足** | `RESULT=PASS`、`EXIT=0`、90 表 / 2 视图 / 双向差集空 |
| 80-C3 裁剪器/选择计划单测绿 | E-11 + 门禁轨 | **部分** | 设备 9/9；JVM `SmbkSelectionPlanTest` 12 例本轨未执行 |
| 80-C4 旧行与视觉四表引用保持 | E-12 §6 | **缺** | `grep -rln tutor_visual …/backup/` = 0 → UNVERIFIED |
| APK sha256 / 设备身份 | E-00 | 有 | 四包 |

**关闭规则 `C1 ∧ C2 ∧ C3 ∧ C4` 未满足 → KD-80 保持 open。**

## 4. 判据偏差与红例归因

| 项 | 判据/工单口径 | 本轮实测 | 处置 |
|---|---|---|---|
| parity 用例数 | 11（批次 30） | **15**（T4 +N09–N12） | 按复读 15 执行；非旧包 |
| 69→70 用例数 | 2（批次 30） | **4**（T3 +2 元用例） | 按复读 4 执行；非旧包 |
| schema | 70；71 为条件分支 | 70，无 `71.json` | 1→70；71 分支不适用 |
| N09-P06 | 期望质心 = 1.000 | 1.2624767812258337（两次复跑逐位相同） | **A**：跨栅格距离 vs 同侧位移；门级已红、未放宽、未改他线 |
| KD-80 关闭 | 工单只点名 C1/C2 | C3 部分、C4 缺 | KD-80 open；不拿前两项冒充全关闭 |

## 5. 缺口与未落盘

- **parity 绿轮**：待 T4 修 N09 负控（暴露 pdf 侧逐轴质心、断言同侧平移 1.0）后复跑，新建 `step08b`；
  本批 `calibrated-r1` 仍 UNVERIFIED。
- **KD-80 80-C3 JVM 半边**：`SmbkSelectionPlanTest` 12 例未由本轨跑（Gradle 单测任务不在本轨允许面）；
  需门禁轨出 E-file 或记录员按"工单转录"处理。
- **KD-80 80-C4**：34 例零断言；补断言属测试落码（他线/记录员裁定），本轨只登记。
- **不在本判据面**：真机 / arm64 / 16 KB 页 / 生产签名 / 真实 Provider；JS 不广告（MVA 未过）；
  `core/export` 与 PDF `/AIGC`（KD-75）不做。
- **PDF 四缺口**：ENOSPC、静默短写、页数回读不符、`renameTo` 失败仍无设备用例 → UNVERIFIED（沿用 KD-79 残留）。

## 6. 脱敏扫描（R4 §4.4）

```text
$ E=docs/research/evidence/2026-10-29-batch-31
$ grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' "$E" || echo "0 hits: secrets"
$ grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken)[[:space:]]*[:=][[:space:]]*[^ <]' "$E" || echo "0 hits: credential assignments"
$ grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$E" || echo "0 hits: emails"
$ grep -RInE 'C:.Users.[^ ]+|/home/[^/ ]+|/Users/[^/ ]+' "$E" || echo "0 hits: home paths"
```

结果（2026-10-29 复核实跑）：`secrets 0`、`credential assignments 0`、`emails 0`、`home paths **1**`——
唯一命中是本文件 `:109` 命令块里的正则模式串**自身**，**不是真实主目录**；
逐条确认后保留（删掉会让扫描命令不可复跑）。`step00-environment.md` 内的原始命令已把
快照/包名占位符换成真实路径与包名，本目录不再有模板占位符。
`<...>` 残留检查：仅 `expected:<69>` / `expected:<1.0>` 这类 **JUnit 失败消息原文**，以及 R4 §8.3
规定必须原样记录的 `<empty>`（`getprop` 空值），都不是模板占位符。
未写入任何密钥、凭据、邮箱、用户主目录、真实学习者内容；APK/日志本体留在 `build/` 不入仓。

## 7. 回链检查

- [x] 每个设备数字都有 E-ID + `path:line`（记录员按本索引 §2 引用）
- [x] `docs/known-defects.md` 的 KD-80 Progress 引用本索引（2026-10-29 复核修复轮回填：`docs/known-defects.md` 批次 31 设备轨回填块引用本文件 §2/§8）
- [x] 每个 E-file 都有 `sanitized` 字段；真实命中 0（唯一 `home paths` 命中为本索引 §6 自指正则文本，已逐条确认）
- [x] 没有把 `build/` 路径当作关闭证据
- [x] 未改任何热区文件；未做 git 写操作；未跑 `connectedAndroidTest`

## 8. 结论与建议（KD-80 / 一致性门标定）

### 8.1 KD-80：C1/C2 可核销，整体**不建议关闭**

| 项 | 结论 | 依据 |
|---|---|---|
| 80-C1 备份 34/34 | **PASS，可核销** | `OK (34 tests)`；8 条旧红逐例 `codes=['1','0']`、`stack=`=0（E-01） |
| 80-C2 清单与 `70.json` 逐名一致 | **PASS，可核销** | 本文件复核实跑：90/90/90、双向差集空、`IDENTITY_HASH` 逐字相同、`RESULT=PASS`（E-12 §4） |
| 80-C3 裁剪器/选择计划单测 | **PARTIAL** | 设备 `OK (9 tests)`；JVM `SmbkSelectionPlanTest` 12 例本轨未跑（超 assemble* 允许面）→ 需门禁轨 E-file |
| 80-C4 旧行与视觉四表引用保持 | **UNVERIFIED** | `grep -rln tutor_visual …/backup/` = 0（本文件复核），34 例对该面零断言 |

**建议**：记录员可写"80-C1/80-C2 已核销"；**KD-80 保持 open**，不得写 closed。
剩余两项的归属：80-C3 JVM = 门禁轨 E-file；80-C4 = 测试落码（补一条显式四表行数/引用断言）或记录员如实登记 UNVERIFIED。

### 8.2 一致性门标定（`calibrated-r1`，原型门）：红例归因 **A**，绿轮 UNVERIFIED

| 判定点 | 结论 | 依据 |
|---|---|---|
| P07 文本质心差归因 | **AA/字体，非几何**（成立） | 设备读数 P07 `centroid=0.52` → `F2_AA`、P06 `0.32` → `F2_AA`（`step08-parity.md` §5.2 引 `:218`/`:220`） |
| 阈值重标定 | 0.75 在树、版本 `calibrated-r1` | `VisualDrawOpsParityMetrics.kt:182,240-243`；15 条 `PARITY_GATE` 均 `product-gate=no`+`thresholds=calibrated-r1`；分类优先级按 R2 §2.4 的**硬几何护栏**形态实现（`:641-660`），避免 N04 被 F2 抢占而关掉门 |
| 负控是否仍有分辨力 | **门级仍红** | `PARITY_CONTROL` 14/14 `red=true`（N10/N11/N12 与 N09-P06 全红），无 `PARITY_GATE_DESENSITIZED` |
| 套件判定 | **FAIL 1/15** | N09-P06 的**追加等式** `assertEquals(1.0, centroidDeltaPx)` 失败：实测 `1.2624767812258337`，两次逐位相同（E-08/E-08b） |
| 归因 | **A（负控期望值算错）** | `centroidDeltaPx = hypot(pdfCentroid−screenCentroid)`（`VisualDrawOpsParityMetrics.kt:798-802`）是跨栅格距离；`shift(pdf, dx=1)` 后读数应为 `|v+(1,0)|`，不是 1.0；基线向量反解与观测自洽（P06 0.32→1.2625；N12 0.5181→3.5601） |
| 产品面 | **零缺陷证据** | 同族 N12 只断言 `centroid > 阈值` 故绿（`:381-392`）；N09 严格等式是全族唯一例外；产品源零改动 |
| 绿轮 | **UNVERIFIED** | N09-P07 因中止未执行；套件仍红；`calibrated-r1` 不得写成"设备已验证" |

**建议（不代改他线）**：由 T4 在 `TextParityMetrics` 暴露 pdf 侧逐轴质心，把 N09 等式改为**同侧**平移不变性
（`|pdfCentroidX(shifted) − pdfCentroidX(base)| == 1.0`），保留 `assertRed` 与 `centroid > 阈值` 两条；
修后复跑新建 `step08c`，全绿后才可把 `calibrated-r1` 标为设备已验证。**一致性门仍是原型门（`product-gate=no`）**。

> **复核修复轮实际采纳（2026-10-29，只读记录）**：没有新增逐轴度量，而是**撤掉**该等式——
> N09 只断言 `assertRed` + `verdict == F4_GEOMETRY`，质心/bbox/bestShift 只进证据日志；P06 的
> `centroid > 阈值` 断言一并撤掉（P07 反解 0.608 < 0.75，不能作为许可证）。改动在
> `VisualDrawOpsParityInstrumentedTest.kt` 与 `VisualDrawOpsParityMetrics.kt` 的 KDoc/注释；
> **新测试未上设备 → `calibrated-r1` 绿轮仍 UNVERIFIED**，N09-P07 仍未执行。
