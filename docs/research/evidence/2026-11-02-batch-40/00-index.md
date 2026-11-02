<!-- 体积自查：92 行 / 9116 B；R4 上限 400 行 / 65536 B -->
# 批次 40 设备证据索引（2026-11-02）

- 记录：`docs/research/2026-11-02-batch-40-record.md`（**已落盘**，375 行；由记录员 2026-11-02 落盘并保留复核修复轮更正段；本目录各 step 的 `record-ref` 已可回链）
- 计划：`.zcode/plans/plan-2026-11-02-batch-40-5x.md`
- 工作树 HEAD：`dee81ea7`（执行窗内由 `a3f367df` 漂移；见 E-40-07）
- 证据目录：`docs/research/evidence/2026-11-02-batch-40/`
- 本目录体积：12 个 `.md` / 128 KiB（R4：单批 ≤16 个 / ≤512 KiB；单文件 ≤400 行 / 65536 B）
- 规范版本：`docs/research/2026-10-26-device-evidence-convention.md` v1
- 执行设备：emulator-5584（AVD `kb_test_device`，API 34，x86_64，page-size 4096）
- 脱敏：目录级 §4.4 扫描（凭据/令牌/密钥/主目录类关键词）**0 命中**；仅模拟器 serial，无物理序列号

## 1. 环境

| 项 | 值 | 依据 |
|---|---|---|
| 设备 | emulator-5584 / `kb_test_device` / API 34 / x86_64 / page-size 4096 | E-40-01 头部、E-40-06 §G3 |
| 包名 | `com.tingyun.smartmistakebook.offline` + `.offline.test` / `com.tingyun.smartmistakebook.core.data.test` / `com.tingyun.smartmistakebook.core.database.test` | E-40-01 §4 |
| 构建窗 | 2026-11-02T10:11–10:22+08:00（四包 mtime 10:17；APK 绑定 10:13 源快照） | E-40-01 §2/§4 |
| 执行窗 | 10:27–10:56+08:00（28 套件 + 错因 + 备份 + drill 串行） | E-40-05 §1、E-40-02b §1 |
| 宿主并发 | 4 个模拟器在线（5562/5566/5584/5596）；他线持续写源/构建 → 无 5 分钟干净窗 | E-40-03 §4、E-40-04 §4 |

## 2. 产物身份（四包，宿主 = 设备逐字节一致）

| 产物 | 字节 | sha256 | versionName |
|---|---|---|---|
| app 主包 | 185746540 | `1acb9907558a8a50254f7f52f4deedcb874a521b032f6afa39d1630a91835983` | `0.2.0-offline` |
| app androidTest | 1668087 | `565fc85b1edadc79e6497261976acbf8e03e1f11326fa4a6e6de9abb9a357c7b` | — |
| data androidTest | 196071631 | `34e3435d9fa5ef0b7f18dc0b02cd229d35c9913e2565174c5b619c77bf103179` | — |
| database androidTest | 209357634 | `f12ef06abfb523c79b40b7e2170f006ed9d462cbb2d3cbba0ff8f6d059668f92` | — |

- 安装 4×`Success`；设备侧 `pm path` + `sha256sum` 与宿主同值（E-40-01 §4）。
- 上述 sha 是本目录全部设备绿的**唯一绑定**；当前 HEAD/工作树已不可复现（E-40-07 §1）。

## 3. 步骤与结论

| E-ID | 文件 | 覆盖 | 判定 | 证据锚 |
|---|---|---|---|---|
| E-40-01 | `step01-rebuild-install-gates.md` | 重建重装 + 源快照（含 core/model）+ dex 符号 + 73.json asset | PASS（构建窗） | §3 diff=0/1277 文件；§4 四包 sha；§6 dex |
| E-WS6-01 | `step01-kd73-stdlib-compat.md` | KD-73 stdlib 静态门 | PARTIAL（设备/R8 半边未做） | §1 静态扫描 |
| E-A08-4C | `step-aud08-gate-4c.md` | AUD-08 门（4C 轮） | FAIL（门值维持） | §4 指标 |
| E-A08-D72 | `step-aud08-gate-device72.md` | AUD-08 门（设备库 v72 快照） | FAIL（门值维持） | §2 产物身份 |
| E-40-02a | `step02a-pdf-aigc-roundtrip.md` | §45 PDF /AIGC 设备往返 | **BLOCKED** | §1 grep exit=1；§2 D45-1 FAIL |
| E-40-02b | `step02b-schema73-migration-backup.md` | §46 schema 73 矩阵 + 备份 + 错因账本 | **PARTIAL**（APK 快照绿；当前树已 rebase 接线，KSP 已生成 92 表/110 FK，hash 已回填 `1d086050…`） | §1 套件表；§2 S73 逐条；§4 rebase |
| E-40-03 | `step03-kd76-calibration.md` | §49 KD-76 诚实复跑 | **BLOCKED**（无单轮入口） | §1 grep；§2 守卫 |
| E-40-04 | `step04-isolation.md` | §50 isolation 低负载/配对/控制轮 | **PARTIAL**（单臂 15/15；A3 不符、控制轮未做） | §1 `OK (15 tests)`；§2 A3 |
| E-40-05 | `step05-suites-crash-worker.md` | §47 28 套件 + §48 签名/worker | **PARTIAL/FAIL**（27/28 绿；p09 红） | §1 逐套件；§2 p09；§3 签名 |
| E-40-06 | `step06-kd71-closeout.md` | §51 KD-71 关闭核销 | PASS（条件式；G4 未做） | §G1–G5 |
| E-40-07 | `step07-drift-and-blockers.md` | 执行窗漂移 + 总体阻塞 | **BLOCKED** | §1 漂移表；§2 汇总 |

- 另有 `rulings/`：两份 AUD-08 门判定 JSON 快照（`20261101T234146Z-2318b8db.json`、`20261101T234207Z-76aba6d0.json`），对应 E-A08-4C / E-A08-D72，非独立证据。
- 关键数字：28 套件 **237 例**（27 绿 / p09 1 红）；schema 73 设备 matrix 16/16、backup 34/34、rowcounts 2/2、trimmer 9/9、c4 8/8、drill 6（4 直跑 + 2 两阶段）、错因账本 1/1；KD-71 3×`OK (27 tests)`。

## 4. 四项结论（关闭建议 / 归因）

| 面 | 判定 | 能否关闭 | 归因 | 依据 |
|---|---|---|---|---|
| **PDF 注入**（KD-75，§45） | 红 / BLOCKED | **否**：维持 open | **本批 WS1 实现缺口**（设备用例未落码；`core/export` 对注入器零接线），非环境、非他线 | `grep -rln PdfAigc app/src/androidTest core/data/src/androidTest core/export/src/androidTest` → 无输出 exit=1；`grep -rn "PdfAigcInjector\|PdfAigcLabeler" core/export/src` → exit=1；D45-1 FAIL，D45-2..D45-9 未执行 |
| **schema 73**（§46） | 设备快照绿 / 当前树 rebase 已生成导出物（PARTIAL） | **仅可按快照记录，不可按当前树关闭** | **他线**：KB 32B 在 **10:33:32** 的文件级还原把 `StudyDatabase.kt` 覆盖回 71（`dee81ea7` 10:45:47 只提交 2 个 docs 文件，未改该源文件）；**复核轮已裁定 rebase 到 71（92 表）**并接线，KSP 已生成 92 表/110 FK、identityHash 已回填 `1d086050…` | 当前 `StudyDatabase.kt:142` = 73；两实体/DAO/`MIGRATION_71_73` 已注册；`SmbkSelectionPlan` 92 表/hash `1d086050…`；设备 matrix `OK (16 tests)`、backup `OK (34 tests)` 绑定 10:13 APK |
| **KD-76**（§49） | 红 / BLOCKED | **否**：维持 open | **本批测试代码缺口**：无单轮入口（单 `@Test` × 20 轮/单进程），且 P1 干净窗被同宿主他线构建占用 | `grep -nE "getArguments\|kd76Rounds\|BOOT_COUNT\|reboot"` 仅命中 KDoc `:86/87/89/142`；`CALIBRATION_ROUNDS=20`、`BATCH_SIZE=10`（`:912-913`）；本批无主口径 M 值，不得引用批次 39 的 9813 ms |
| **isolation**（§50） | 黄 / PARTIAL | **否**：不得写“通过/T4 排除” | **证据缺口 + 判据口径冲突**：单臂 15/15 绿、A4 干净；A3 分布与判据集合不符；无 OFF/ON 配对、无 T4 控制轮、低负载门未建立 | `OK (15 tests)` Time 60.848；watchdog kill=5 = aHungWorker 1 / aSecondConcurrent 1 / theClientRebuildsOnceAfterADeath **2** / theWatchdogKills 1；另 terminate accepted=5（aHungWorker 1、theClientRebuildsAtMostOnce 2、anExplicitRetry 1、aColdStart 1）；`suite-isolation-logcat.txt:667,699,731,760,1029,1087,1119,1180,1234,1266` |

- **总裁决**：本批设备绿只绑定 10:13 四包；当前树已按复核轮裁定 rebase 到 71（73=92 表）并接线，KSP 已生成 rebased `73.json` 并回填 identityHash `1d086050…`，但设备绿仍不可复现。不得据此宣称双门通过：现有单测 XML（`core/database/build/test-results/testDebugUnitTest/TEST-...MaterialFeatureScaleTest.xml`，tests=1 / failures=1）是 **09:38–09:41（DB=73 树）的陈旧快照**，其源测试已随 KB 回滚删除，不能代表当前红集；强门未跑（E-40-07 §3）。

## 5. KD-74 覆盖

| KD-74 要求 | E-ID | 状态 | 备注 |
|---|---|---|---|
| 每条 `am instrument` summary / `INSTRUMENTATION_CODE` | E-40-02b/05/06 | 有 | 逐套件 `OK (N tests)` / p09 `FAILURES!!!` |
| logcat 九类签名全量 grep（含 0 命中） | E-40-05 §3 | 有 | 仅 p10 设计性 `death was not confirmed=1` |
| 16KB `getprop`/`getconf` + `readelf -l` 页对齐 | — | 缺 | 本批未测 → **UNVERIFIED**（不得写通过） |
| 备份/迁移用例输出 | E-40-02b | 有 | 含 drill 两阶段披露 |
| T1 修复后设备复跑 | — | 不适用 | 本批无 T1 修复；E-40-01 §2 是 WS2 测试编译补参，不是 T1 |
| APK / mapping sha256 | E-40-01 §4 | 部分 | 四包 sha 有；`mapping.txt` 未记录 |

## 6. 缺口与未落盘

1. `PdfAigcDeviceRoundTripInstrumentedTest` 不存在 → D45-2..D45-9 无设备证据；`core/export` 未接注入器。
2. KD-76 无 instrumentation 单轮入口；无 20 次 reboot 逐样本与外部聚合；无 P1–P4 干净窗。
3. isolation 无 OFF/ON 配对红集、无字节可核 T4 控制轮；A3 判据“总数=5”与“集合严格相等”冲突，待主会话裁定。
4. 16KB 页未测；`mapping.txt` 未记。
5. 当前树已按主会话裁定 rebase 到 71（73 = 92 表）并接线；rebased `73.json` 已由 KSP 生成（92 表/110 FK），identityHash 已回填 `1d086050…`。仍须重走 E-40-01 构建→快照→安装门并重跑 §46/§47（设备绿只绑定 10:13 APK）。同号 73 与 `codex/ui-green-pages-2026-10-27` 工作树的 97 表冲突已裁定：本线占 73，UI 线合并时重编号到 74（E-40-02b §5）。
6. p09 红（候选 T4/D 时序窗）未定案；未重跑洗绿。
7. `docs/research/2026-11-02-batch-40-record.md` **已由批次 40 记录员落盘**（375 行；保留复核修复轮的更正段 §7；设备数字仍只绑定 10:13 APK，不绑定当前树）。
8. `raw log` 位于 `build/device-track-batch40/`（gitignored，不作关闭证据）；本目录只留计数与签名摘录。

## 7. 回链检查

- [x] record 已补建；设备数字按 E-ID 回链（`docs/research/2026-11-02-batch-40-record.md`；绑定 10:13 APK，不绑定当前树）
- [x] `docs/known-defects.md` 已追加批次 40 Progress（KD-71/KD-75/KD-76；isolation 记在 KD-66 回归观察块）
- [x] 每个 E-file 都有 `sanitized` 字段；目录级扫描 0 命中
- [x] 未把 `build/` 路径当作关闭证据（仅作 raw log 位置）
