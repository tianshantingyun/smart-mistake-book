# 批次 32 设备证据索引（2026-10-29）

- 记录：`docs/research/2026-10-29-batch-32-record.md`（记录员后写）
- 计划：`.zcode/plans/plan-2026-10-29-batch-32-parity-schema71.md`
- 判据：`docs/research/2026-10-29-batch-32-device-criteria.md`（T1；本轨按其 §1.3 计数冻结与 §7 串行执行）
- 规范：`docs/research/2026-10-26-device-evidence-convention.md` v1（R4）
- 工作树 HEAD：`0b4bdbbea918ba6b8c8e1f3b190b8be502ec627a`
- 工作树：dirty（240 M / 480 `??`，均为他线在飞；本轨零主源/测试/热区写入）
- 设备：`emulator-5562`（AVD `test_device`；API 34 / x86_64 / PAGE_SIZE 4096；未触碰 `-5566`/`-5584`）
- 证据目录：`docs/research/evidence/2026-10-29-batch-32/`
- 本目录体积：**15 个 `.md` / 96 KiB（du，块大小；实际字节 64,683）**；最大单文件 8,235 B（本索引）；最大行数 121（`step01-build-install.md`）。上限 16 个 / 512 KiB、单文件 64 KiB / 400 行，未超。
- 执行时间：2026-10-29 11:13–11:35 +0800（设备 logcat 为 UTC，约 −8 h）

## 1. 环境

见 `step00-environment.md`。

## 2. 产物身份

| 产物 | 字节 | sha256（宿主＝设备） | versionName |
|---|---|---|---|
| `app-strictOffline-debug.apk` | 189532939 | `fb235c0aedf33da3c1c0c12932b9951e188f61799a92bd29a968225c0a3a438e` | `0.2.0-offline` |
| `app-strictOffline-debug-androidTest.apk` | 1586361 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` | （空） |
| `data-debug-androidTest.apk` | 197769972 | `de27b081942695474091500d0d7f4a53e8b70e5b3106f1c152f3e1ae2325f15c` | （空） |
| `database-debug-androidTest.apk` | 196829660 | `90be7c2bc54da6f68067c421e1cc0cc8538f892dab77e1514a0d3f8d89115dda` | （空） |

四包 `install -r -t` 均 `Success`；设备侧 `pm path` + `sha256sum` 与宿主逐字节一致（E-01 §2）。

## 3. 步骤与结论

| E-ID | 步骤 | 门/KD | 用例数 | 判定 | 证据（本批目录内） |
|---|---|---|---|---|---|
| E-00 | 环境 / 设备身份 | — | — | PASS（信息性） | `step00-environment.md:1` |
| E-01 | 重建 / 重装 / dex / schema assets | 判据 §1.3/§3.4/§7 | — | PASS | `step01-build-install.md:1` |
| E-02 | 一致门（原型门，`calibrated-r2`） | 一致门（**非产品门**） | 16 | **PASS**（15 负控全红） | `step02-parity.md:1` |
| E-03 | 迁移 69→70→71 | schema 71 迁移面 | 4 | PASS | `step03-migration-69-70.md:1` |
| E-04 | 70→71 专用（身份负向） | 计划任务 6 | 3 | PASS | `step04-migration-70-71.md:1` |
| E-05 | 迁移矩阵 1→71 | 迁移矩阵 / Stage 2 | 15 | PASS | `step05-migration-matrix.md:1` |
| E-06 | 备份（含 8 条旧红） | KD-80 80-C1 | 34 | PASS | `step06-backup.md:1` |
| E-07 | 持久化结构门 | 结构门 | 5 | **FAIL（1）** | `step07-structure-gates.md:1` |
| E-08 | 提交 / 仓库 | 预算/evict/pin/租约/digest | 11 + 8 | PASS | `step08-commit-repo.md:1` |
| E-09 | 裁剪器（设备） | KD-80 80-C3 | 9 | PASS | `step09-trimmer.md:1` |
| E-10 | 回归 painter / PDF | KD-78 ③/④；KD-79 | 24 + 16 | PASS | `step10-painter-pdf.md:1` |
| E-11 | 隔离 / flags + worker 生命周期 | KD-78 ④ | 13 + 2 | PASS（17↔17） | `step11-isolation-flags.md:1` |
| E-12 | 9 类崩溃签名 | 判据 §7.6 | 13 份 logcat | PASS（全 0） | `step12-signatures.md:1` |
| E-13 | 红例归因 | 判据 §8 | 1 | **A 类，未修** | `step13-red-attribution.md:1` |

**结论先行**：12 个套件/步骤绿（16/4/3/15/34/11/8/9/24/16/13/2），**1 个套件红**（结构门 4/5，
红例 `evictionDeletesOnlyUnpinnedStagingIntermediates`，归因 **A**：用例自身“先断言删除、再读取该行”矛盾；
产品路径正确，未放宽断言、未改红例）。

## 4. schema 71 三段

| 段 | 判据 | 实测 | 判定 |
|---|---|---|---|
| Stage 1 生成物 | `71.json` + Impl + 常量同值 | `version=71`、`identityHash=714c0ecb85ee5edf74f0013740148138`、90 entities / 2 views；`RoomOpenDelegate(71,"714c…")`；`SCHEMA_VERSION=71`、`IDENTITY_HASH` 同值 | PASS |
| Stage 2 迁移 1→71 | V=71、assets 71、矩阵 15/15 | 全部成立（E-05） | PASS |
| Stage 3 表清单 | 90 表 / 2 视图 / 82 / 5 / 21；双向差集空、version/hash 同源 | `RESULT=PASS`、`EXIT=0`（§6.3 脚本，S=71） | PASS |

## 5. 缺口与未落盘

1. **schema 71 生成物与迁移源码未跟踪（`??`）**：`71.json`、`VisualSnapshotIdentityMigration_70_71.kt`（迁移实现）、`TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest.kt` 在设备轨时点均不在 `git ls-files`（`git status --porcelain` 显示 `??`；设备侧已作为 assets/dex 生效并可复核）。**复核修复轮（2026-10-29）已对这三条执行 `git add`**：现 `git status --porcelain` 为 `A `，`git ls-files` 三条均命中，文件内容未改。提交时仍需确认同批纳入。
2. **E-07 结构门 1 红**：归因 A；**复核修复轮（2026-10-29）已按 E-13 §4 做最小修复**——`:221` 的读侧断言改读一条未列入淘汰候选、因而仍存在的 STAGING 行（`snap-staging-keeper`），未删断言、未放宽、未 `@Ignore`。**修复后的绿轮尚未重跑**；重跑前批次验收“结构门全绿”仍不成立。
3. **JVM `SmbkSelectionPlanTest` 12 例未跑**（设备轨只跑 `assemble*`；不跑单测任务）→ 80-C3 只能 PARTIAL。
4. **KD-80 80-C4 仍 UNVERIFIED**：34 例无 `tutor_visual` 行/引用断言（E-06 §7）。
5. **一致门仍是原型门**：E-02 的 16/16 绿不得写成产品门/发版结论。
6. **判据文档计数漂移**：§5.1 写 4、§5.2 写 10/5；现树复读为 **5 / 11 / 8**（§1.3 以复读为准）。§5.4 写“预算未入事务、evict 无租约”亦已被现树取代（`RoomStudyDatabase.kt:2165-2181`、`TutorVisualDao.kt:216-225`，E-08 §7）。
7. **触发器 backstop 未落**：本批不声称 DB 级触发器生效。
8. **真实 Provider 无凭据**：模型质量 UNVERIFIED；不在本轨范围。

## 6. 回链检查

- [x] 每个设备数字都有 E-ID + 文件（本索引 §2–§4）
- [ ] record §4 引用本索引（记录员后写）
- [x] 每个 E-file 都有 `sanitized` 字段且 R4 §4.4 扫描 0 命中
- [x] 未把 `build/` 路径当作关闭证据（只出现在 `raw-log`）
- [x] 零 `connectedAndroidTest`、零 `adb root`、零 git 写操作；Gradle 仅 `assemble*`

## 7. 判据结论（`calibrated-r2` / schema 71）

### 7.1 parity `calibrated-r2`：**绿 → 建议关闭本批标定纠正项**

| 判据 | 实测 | 结论 |
|---|---|---|
| 套件 summary | `OK (16 tests)`，0 失败/错误/跳过（E-02 §3） | 绿 |
| 负控分辨力 | `PARITY_CONTROL` 15 条，非 `red=true` = 0（E-02 §3） | 绿 |
| 方向相关声明 | `thresholds=calibrated-r2`×16、`blindSet=…(direction-dependent)`×16、`displacement-exits=bestShift(main)+bboxDev(aux)`×16（E-02 §3） | 绿 |
| P06 / P07 | 1.2624767812258337（与常量逐位同）/ 0.608282132824767（差 1.04e-12，≤0.75 为质心盲区，红由 `bboxDev=2` 承担）（E-02 §4） | 绿 |
| 旧标定串 | `calibrated-r1` = 0、`blindWindow=` = 0（E-02 §3） | 绿 |

关闭建议：本批“删除 1 px=1.000 错误期望、重建 `calibrated-r2` 许可证”成立，可关闭该纠正项。
**边界**：仍是原型门（`product-gate=no`），不构成产品/发版结论；不覆盖跨设备/跨 API/fontScale。

### 7.2 schema 71：**绿 → 建议关闭“生成→回填→验证”**

| 段 | 实测 | 结论 |
|---|---|---|
| Stage 1 | `71.json` version=71、identityHash=`714c0ecb…8138`（setupQueries 含同值）、90 entities/2 views；Impl 与 `SmbkSelectionPlan.kt:44/51` 同值 | 绿 |
| Stage 2 | 69→70 4/4、70→71 3/3（身份负向）、1→71 矩阵 15/15、assets 71 json | 绿 |
| Stage 3 | 90 表/2 视图、双向差集空、`RESULT=PASS`/`EXIT=0` | 绿 |

关闭建议成立，**但条件**：`71.json` 与 70→71 迁移实现/测试仍 `??` 未跟踪，提交时必须显式纳入（§5-1）；
触发器 backstop 本批不声称；KD-80 80-C4 仍 UNVERIFIED（§5-4）。

### 7.3 本批唯一红：结构门（非 parity / 非 schema 71）

E-07 `Tests run: 5, Failures: 1`，红例 `evictionDeletesOnlyUnpinnedStagingIntermediates` 归因 **A**
（用例先断言删除、后读取同一行；产品 SQL 与读侧均正确，E-13 §2–§3）。未修、未放宽，归属 T5；
批次验收“结构门全绿”未成立，不得以本索引 §7.1/§7.2 的绿掩盖。
