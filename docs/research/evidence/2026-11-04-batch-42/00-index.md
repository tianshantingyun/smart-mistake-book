<!-- 体积自查：163 行 / 17,370 B；R4 上限 400 行 / 65536 B。目录预算：11 个 .md + 1 个 rulings JSON（上限 16 个 .md） -->
# 批次 42 证据索引（2026-11-04）

- 计划：`.zcode/plans/plan-2026-11-04-batch-42.md`（本批 100 项 = 研究 6 + 实施 74 + 流程 20）
- 首项输入：批次 41 记录 `docs/research/2026-11-03-batch-41-record.md` §9.3（F7/F8/F10/F11/F12）
- 本文件范围：本目录内 E 文件的**元字段整理、编号映射、绑定与可证性边界**
- 规范：字段口径 = `evidence-id / verdict / executed-at / head / device-serial / apk-sha256 / exit-code / raw-log / record-ref / sanitized`（本 ask 指定）
- 整理者只做**只读核对 + 文本整理**：未运行 Gradle、未改源码/热区、未做 git 写；仅复跑了两项只读检查（§7-1/§7-2）

## 0. 编号映射（先读这一节）

| E-ID | 文件 | 计划落点 | 判定（本索引转录，不代替主会话裁定） |
|---|---|---|---|
| E-42-WS2-01 | `ws2-kd75-closeout.md` | §2 WS2 13–24 | KD-75 **维持 open**；落码在树，设备/JVM 用例本轨 UNVERIFIED |
| E-42-WS3-01 | `ws3-kd76-20-rounds.md` | §2 WS3 25–33 | 主口径 **PASS（单批 n=20，p95=1889 ms）**；KD-76 仍 open（A6/C4） |
| E-42-WS4-01 | `ws4-isolation-causality.md` | §2 WS4 34–41 | **UNCONVERGED / 不写因果**（1/3 窗 PASS + 15/15 `-4` + 无 OFF 控制臂） |
| E-42-WS5-01 | `ws5-kd83-v4-fixture.md` | §2 WS5 42–47 | **KD-83 关闭**（合成合法 v4 夹具口径） |
| E-42-WS7-01 | `ws7-preview-assembly.md` | §2 WS7 56–60 | **PARTIAL / BLOCKED**：默认关闭注入点在树，生产接线 BLOCKED |
| E-42-WS9-01 | `ws9-contract-admission.md` | §2 WS9 71–75 | 5 交付物落盘；契约未应用、人工金标 BLOCKED、门值 FAIL 维持 |
| E-42-AUD08-01 | `step-aud08-gate-recompute-b42.md` | §2 WS9 74 | **FAIL（exit 1）**：0.5714 < 0.9；阈值零改动 |
| E-42-DEVICE-01 | `device-track-batch42.md` | §2 WS2/3/4/5/6/10 + §3 设备轨 | **PARTIAL**：WS2 PASS、WS3 n=20 PASS、WS4 UNCONVERGED、WS5 3/3、WS6 1/4、WS10 243/241/2/15 |
| E-42-WS10-01 | `ws10-device-full.md` | §2 WS10 76–80 | **PARTIAL**：32 套件 258 例 → 执行 243 / 绿 241 / 红 2 / 跳 15 |
| E-42-RESEARCH-CROSSCHECK-01 | `research-crosscheck.md` | §1 R1–R6 交叉复核 | 7 条冲突（C1/C2/C3/C5/C6 高）+ 独立复现 F7/F10 假绿 |
| （机器产物） | `rulings/20261103T044703Z-10c61be3.json` | WS9 74 | `validate_rulings` PASS；门状态 FAIL；不单独作 E 文件 |

- 旧标识保留为别名：`WS2-2026-11-04-kd75-closeout`、`B42-WS5-KD83-V4-FIXTURE-01`、`WS10-42`、`B42-RESEARCH-CROSSCHECK-01`。
- **本目录不覆盖全部计划项**：WS1（F7/F8/F10/F11/F12 修复）与 WS8（发版前置）在本目录**没有独立 E 文件**；WS6 设备证据只在 E-42-DEVICE-01 §5。缺口见 §6。

## 1. 环境与源绑定（整理时的实测值 + 各 E 文件自述）

| 项 | 值 | 依据 |
|---|---|---|
| HEAD | `faab17f58b01eab78d5c08a9f843b3af3df2fecf`（`faab17f5`） | 本轮 `git rev-parse HEAD`；与计划 §0、`build/batch42-strong.log` 头、E-42-WS5/WS9/RESEARCH 头表同值 |
| 工作树 | `git status --porcelain` = **651** 条；`git diff HEAD --binary \| sha256sum` = `3d93df8e16b47bed0ea5c2bddfffc2c4607d175da5ba2445b21d376dfabc4c5b` | 本轮实测（共享工作树，他线在飞；不归因） |
| 设备轨源快照 | `d89cd1a4fe3f51c98fa2eb30290f5151e3236e44a93676ff704d0321a43fc876`（15:18 时点，649 条脏路径） | E-42-DEVICE-01 §0；与上行的 651/`3d93df8e…` **不同时刻，不可互推** |
| DB schema | 73（92 表）；`STUDY_DATABASE_VERSION = 73` | `core/database/src/main/kotlin/.../StudyDatabase.kt:142`（本轮实读）；计划 §0；本批未改 schema |
| 研究件 | R1–R6 六份 + 基线/盘点/WS8/准入/裁定等共 **12** 份 `docs/research/2026-11-04-*.md` | 本轮 `ls docs/research/2026-11-04-*.md`（含 `6c2-rulings`、`aud08-human-gold-sampling-plan`、`batch-42-baseline`、`batch-42-inventory`、`release-gates-draft`、`ws8-release-prefight`） |
| 目录状态 | 本目录 12 个文件（11 `.md` + 1 `rulings/*.json`）均为**未跟踪**（`git ls-files docs/research/evidence/2026-11-04-batch-42/` 为空） | 本轮实测；提交由主会话按块处理 |

## 2. 产物身份（逐轨绑定；**跨轨不可合并**）

### 2.1 各设备轨 APK（同一个"批次"里其实有三份 app）

| 轨 | app sha256 | 测试包 sha256 | 设备 | 时点 |
|---|---|---|---|---|
| E-42-WS3-01 | `35e10339…bff1f8` | app.test `0e906482…4e3aca` | emulator-5584 | 07:01–07:22 |
| E-42-WS4-01 | `fe9452aa…669e3e` | `d300d885…c9a721`（两臂同一份） | emulator-5562 | 07:42–09:10 |
| E-42-DEVICE-01 | `ec135844…e19bb5` | app.test `a2532e16…1e9687b`；export.test `0d34532c…1bbeca0`；data.test `a3d0d4e2…538e66`；database.test `a7a44373…09c5` | emulator-5584 / emulator-5566 | 15:18–17:10 |
| E-42-WS10-01 | `35e10339…bff1f8` | app.test `0e906482…`；data.test `d88ec38f…0552b2`；database.test `7b4df3ea…f10d0` | emulator-5584 | 13:28–14:05 |
| E-42-WS5-01 | —（只装 data.test） | data.test `d88ec38f…0552b2`（设备 `base.apk` 与宿主同值） | emulator-5584 | 09:20–09:36 |

- **同一 app sha 只出现了两次**（WS3 与 WS10 的 `35e10339…`）；WS4 `fe9452aa…`、DEVICE `ec135844…` 是另外两次构建。任何"批次 42 设备全绿"的说法都必须指名是哪份 APK。
- WS3 与 DEVICE 各自跑了独立的 20 轮 KD-76（不同 APK、不同 boot 序列）→ **不得相加成 n=40**。

### 2.2 冻结夹具与门产物（本轮 `sha256sum` 复核）

| 产物 | 字节 | sha256 | 复核 |
|---|---|---|---|
| `tools/backup_fixtures/v4/closure4-schema72-mask15.smbk` | 28,108 | `24941ed731f8b55436c0fca58f11d3f047c462fcd9026c3d1f2eeddb22f763ad` | 本轮实测 = E-42-WS5-01 §3 golden，**逐位相同** |
| `tools/backup_fixtures/v4/v4-pack.golden.json` | 8,769 | `3a110cbbc0592ce82a57e796a7a42d2ca3985dc1b9a6590431f6c0295a9f338a` | 本轮实测 = E-42-WS5-01 §2 复现行，逐位相同 |
| AUD-08 金标 `split_golden_v1.json` | — | `f9e92c47…e1e4c9f`（E-42-AUD08-01 §2 自述） | 本轨**未复算** |
| AUD-08 应用导出 `app-split.json` | 1,482 | `9b49b0c4…4c2fd6`（同上） | 本轨**未复算** |

## 3. 步骤与判定（每行只转录对应 E 文件；未复跑设备）

| E-ID | 覆盖 | 判定 | 锚点 |
|---|---|---|---|
| E-42-WS2-01 | KD-75 关闭判据 C1–C8 对账 + `MistakePdfExporter` 最小 hunk | KD-75 **open**；C2 未实现、C4 与字面不一致 | §2 判据表；§4 残余风险 |
| E-42-WS3-01 | 20 轮 reboot 冷启动（n=20，单批） | **G2 PASS**；G1 green；p95=1889 ms；KD-76 仍 open | §2 boot 表；§4 判词 |
| E-42-WS4-01 | 低负载门 + 配对报告 + T4 标记 + 6 run×15 例 | **UNCONVERGED**；15/15 `-4`；OFF 控制臂 BLOCKED | §3 门表；§5 run 表；§7 条件表 |
| E-42-WS5-01 | 真实 v72 载荷夹具 + JVM 先红后绿 + 设备 3/3 | **KD-83 关闭**（合成夹具口径） | §2 build/verify；§5 JVM；§6 设备 |
| E-42-WS7-01 | D-04 注入点（默认关闭）+ patch 草案 | PARTIAL / 生产接线 **BLOCKED** | §2 判据；§5 阻塞 |
| E-42-WS9-01 | 契约 patch / 准入清单 / 抽样计划 / 门复算 / 台账 | 5 交付物；契约未应用；人工金标 BLOCKED | §1 表；§2 命令 |
| E-42-AUD08-01 | AUD-08 门判定 | **FAIL（exit 1）**；阈值零改动 | §4 指标；§5 逐页 |
| E-42-DEVICE-01 | 设备轨汇总（WS2/3/4/5/6/10） | PARTIAL（见 §0 行） | §1–§6 |
| E-42-WS10-01 | 32 套件 / 备份矩阵 / 崩溃签名 / worker / 性能 | PARTIAL：243 执行 / 241 绿 / 2 红 / 15 跳 | §1 套件表；§6 发现 |
| E-42-RESEARCH-CROSSCHECK-01 | R1–R6 对既有研究 | 6 题判定 + 7 冲突；F7/F10 探针复现 | §1 判定表；§4 冲突表 |

## 4. 仅本批可证项（位绑定；写出这张表是本 ask 的核心）

| 面 | 本批可写 | **只**由本批可证（绑定） | 不得外推 / UNVERIFIED |
|---|---|---|---|
| KD-76 20 轮 | n=20 单批、p95=1889 ms、G1 green、20 个不同 boot_id | APK `35e10339…` + emulator-5584 + `build/kd76-runs/ws3-batch42-20/` 的 20 份 XML | 两批独立（A6）、C4 死亡窗、arm64/真机、产品进程首请求；**单批 ≠ 两批** |
| KD-76 第二次 20 轮 | n=20、p95=1969 ms | APK `ec135844…` + 同 serial 另一 boot 序列（r2 目录） | 与上一行**不可合并**；同属单批 |
| KD-75 设备面 | AI 图链打标/纯用户负向/幂等/旧包重建/打印整份 vs 部分页 5/5 | app `ec135844…` + app.test `a2532e16…` + 两个 PDF 文件 sha（DEVICE §1） | 模型解析文本 C2、字面 v3→v4、`PdfRenderer≠pypdf`、DOCX |
| KD-83 | 冻结 v4 字节 → 72→73 恢复、旧行不丢、新表 0、N1/N2 拒收 | 夹具 `24941ed7…`（本轮复核）+ data.test `d88ec38f…` / `a3d0d4e2…` | 历史真实用户 v4 包（不存在）；其他 API/ROM；带图 v4 包 |
| isolation | 报告工具在真实数据上判 **UNCONVERGED**、T4 标记能绑已安装 APK 并数出 6 写点、低负载门 1/3 PASS | 5562（`fe9452aa…`）+ 5566（`ec135844…`）+ 5584（`35e10339…`）三次独立复现 | **因果、T4 排除、两臂等价**全部禁止；15 例零执行 |
| AUD-08 | 门 FAIL、①=0.5714、③=0.0000 | 金标 `f9e92c47…` × 导出 `9b49b0c4…` 这一份样本（G2） | 当前设备库准确率、155 页总体、人工金标正确率 |
| 6C-2 三裁定 | 设备 1/4（3 红为夹具断言目标错） | data.test `a3d0d4e2…`；`ProblemDao.kt:318-319` | mask=0 / mask=9 恢复语义、跨版本三选择矩阵 |
| WS10 全量 | 32 套件 / 258 例 / 243 执行 / 241 绿 / 2 红 / 15 跳 | app `35e10339…` + 5566 四包（DEVICE §6 另证） | "28 套件全绿"、isolation 通过、批次 42 工作树新改用例 |
| 工具修复（F7/F10） | 缺方法物化为 UNKNOWN；空 sha/缺 boot 判 INVALID；`pytest 41 passed` | 本轮复跑 `pytest`（§7-2）+ `tools/ci/*.py` 代码现状 | 运行时零触发（ws4 §6：`missing_methods` 两臂为空）；设备行为 |
| F11/F12 代码面 | `decide` 三参数、无外部阈值覆盖；`NOT_PLANNED` 显式态 | 本轮实读 `Kd76ReportVerdict.kt:80-102`、`ReviewCapacityPreviewReader.kt:138-141,163-177` | JVM 用例执行（门禁阶段）、Preview 端到端 |

## 5. 同批其他文件与原始产物（非本目录 E 文件，仅索引/登记，不背书）

- 研究/记录：`docs/research/2026-11-04-{pdf-export-injection,isolation-causality,kd76-20-rounds,v4-pack-fixture,lockfile-window,release-readiness}.md`（R1–R6）、`contract-v1.1-p0-applicable.patch`、`aud08-human-gold-sampling-plan.md`、`release-gates-draft.md`、`ws8-release-prefight.md`、`6c2-rulings.md`、`batch-42-baseline.md`、`batch-42-inventory.md`。
- 原始日志（gitignored，不作关闭证据）：`build/kd76-runs/ws3-batch42-{20,r2}/`、`build/device-track-batch42/`（311 文件）、`build/ws4-isolation-b42/`（72）、`build/ws9-batch42/`、`build/v4-repro/`、`build/kd76-verdict-probe/`、`build/ws8-batch42/`（release/签名/供应链/锁窗口）。
- 门禁日志（已存在，**无本目录 E 文件**）：`build/batch42-unit.log`（14:55）、`build/batch42-strong.log`（15:07–15:12）。见 §6-4、§7-1。

## 6. 缺口与未验证（汇总，不得填平）

1. **批次 42 记录未创建**：`docs/research/2026-11-04-batch-42-record.md` 不存在（WS11 item 83 未落）；本目录的 `record-ref` 只能指计划/基线/批次 41 记录。
2. **WS1 无独立 E 文件**：F7/F8/F10/F11/F12 的"先红后绿"证据分散在 `tools/tests/*`（本轮复跑 41 passed）、ws4 §2.1、ws3 §7、代码现状；没有一份 E 文件逐条对账 12 项。
3. **WS8 无本目录 E 文件**：只有 `docs/research/2026-11-04-ws8-release-prefight.md` + `build/ws8-batch42/`；本索引未逐项核对。
4. **单测门是红的**：`build/batch42-unit.log` 尾部 `BUILD FAILED`，4 例红（3 KB 词面 + 1 `KernelWave0SchemaContractTest`）；无 E 文件、无显式 exit code 行。强门日志有 `BUILD SUCCESSFUL` 与 `exit 0: :app:assembleLocalFirstDebug --continue`。
5. **E-42-WS10-01 无 HEAD/源快照**：只绑 APK sha；其 app.test 是 01:00 构建，不含批次 42 工作树改动。
6. **设备侧缺口**：KD-76 两批独立/C4/arm64；isolation OFF 控制臂；WS6 mask=0/9；真机/16KB；LMK。
7. **外部阻塞（BLOCKED）**：真实 Provider 调用、生产签名密钥（4/4 环境变量 UNSET）、arm64 硬件、人工金标标注员（≥2 人）。
8. **E-42-AUD08-01 的表是整理追加**：正文由 `check_split_gate.py --evidence-out` 生成，重跑会覆盖元字段表。
9. **E-42-WS2-01 的 standalone 结果无落盘日志**：48/48 只在会话内，不可复核。
10. **APK 与当前树不一致**：DEVICE/WS10 的 app.test 早于批次 42 的部分测试改动；任何"当前源码已验证"的说法不成立。

## 7. 本次整理实跑的两项只读检查（可重跑）

```bash
# 1) v4 夹具与 golden 字节复核（输出与 E-42-WS5-01 §2/§3 逐位相同）
sha256sum tools/backup_fixtures/v4/closure4-schema72-mask15.smbk \
          tools/backup_fixtures/v4/v4-pack.golden.json

# 2) 三条工具测试复跑（ws4 §2.1 的 41 passed 复现）
python -m pytest tools/tests/test_isolation_pair_report.py \
  tools/tests/test_t4_control_marker.py tools/tests/test_summarize_kd76.py -q
# → 41 passed in 0.76s；PYTEST_EXIT=0
```

- 两项都由本索引整理者在本轮执行并读到输出；**其余判定均为转录**，未复跑设备/Gradle。

## 8. 本批新增发现（本索引登记，供记录/台账引用）

| # | 发现 | 证据 | 归因/处置 |
|---|---|---|---|
| N1 | 单测门实际 **4 红**，超出计划 §0/批次 41 转录的"仅 3 例 KB 词面红"：多出 `KernelWave0SchemaContractTest > the study database version matches the latest exported schema`（`:44`，字面量 `71` vs `STUDY_DATABASE_VERSION=73`） | `build/batch42-unit.log:246-258`；`KernelWave0SchemaContractTest.kt:44-55`；`StudyDatabase.kt:142`；基线 §284 已把旧的"3 红"标为转录/UNVERIFIED | 测试字面量陈旧（批次 32 后 schema 已到 73），**非本批产品回归**；这是本批首次真实重跑，基线口径应更新为"4 红"。是否早先被 UP-TO-DATE 遮住属推断，未证 |
| N2 | 同批设备轨用了 **3 份不同 app APK**（`35e10339…`、`fe9452aa…`、`ec135844…`） | §2.1 表；各 E 文件头 | 任何跨轨合并/对比必须先对齐 APK；本索引不合并 |
| N3 | KD-76 有**两次**独立 20 轮（WS3 `35e10339…`、DEVICE `ec135844…`），p95 分别为 1889 / 1969 ms | E-42-WS3-01 §4；E-42-DEVICE-01 §2 | 两次都可作单批证据，**不得相加**；A6 仍缺 |
| N4 | E-42-WS10-01 未记录 HEAD/源快照，仅绑四包 sha | E-42-WS10-01 §0 | 该文件所有结论只对绑定 APK 成立 |
| N5 | 工具修复的运行时覆盖有限：ws4 报告 `missing_methods` 两臂为空，F7 物化路径**未被真实数据触发** | E-42-WS4-01 §6 | F7 的绿只能引 `tools/tests`（本轮 41 passed），不能称"设备已验证" |
| N6 | WS8 预检结论与计划 item 63 相反：x86_64 宿主**能**跑 16KB（`getconf PAGE_SIZE=16384`、native 库真实装载） | `docs/research/2026-11-04-ws8-release-prefight.md` §0-4 | 计划措辞需主会话更正；本索引**未复跑**该设备结论 |
| N7 | p09 红是批次 40 同方法同报文复现 | E-42-WS10-01 §1.1；E-42-DEVICE-01 §6 | 非本批新增红，未定性 |

## 9. 脱敏扫描（本目录统一跑一次，2026-11-03，整理后）

```bash
E=docs/research/evidence/2026-11-04-batch-42
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' "$E"
grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken)[[:space:]]*[:=][[:space:]]*[^ <]' "$E"
grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$E"
grep -RInE 'C:.Users.[^ ]+|/home/[^/ ]+|/Users/[^/ ]+' "$E"
```

| 扫描 | 结果（含本文件自身） | 处置 |
|---|---|---|
| secrets | **2 命中，均假阳性** | ①`ws9-contract-admission.md:109` 的 `model-task-contracts` 里 `sk-contracts` 命中 `sk-…`；②本文件下表的复述文本自身。逐字确认为文件名片段 |
| credential assignments | 0 命中 | — |
| emails | 0 命中 | — |
| home paths | **2 命中，均自匹配** | 本文件 §9 代码块的正则文本（`:147`）与本表复述（`:155`）；无真实路径 |
| 人工通读 | 已过 | 无学习者内容、无凭据、无真机序列号；仅模拟器 serial 与仓内相对路径 |

## 10. 回链与维护

- 计划：`.zcode/plans/plan-2026-11-04-batch-42.md` §1/§2/§3/§4/§5；批次 41 记录 §9.3（F7/F8/F10/F11/F12）。
- 基线：`docs/research/2026-11-04-batch-42-baseline.md`；盘点：`docs/research/2026-11-04-batch-42-inventory.md`。
- 本目录每个 E 文件均含 §0 字段表的 10 个字段；缺失项按上文写 `N/A`/`未记录`，不填猜测值。
- 本索引不替代主会话裁定：KD-75 open；KD-76 open（单批 PASS）；KD-83 关闭（合成夹具口径）；isolation **UNCONVERGED**；WS6 设备 1/4；AUD-08 FAIL 维持；发版就绪**未成立**。
