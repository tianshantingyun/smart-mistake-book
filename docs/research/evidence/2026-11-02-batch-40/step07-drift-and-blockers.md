<!-- 体积自查：约 110 行；R4 上限 400 行 / 65536 B -->
# E-40-07 执行窗口漂移、阻塞项与总体裁决

| 字段 | 值 |
|---|---|
| evidence-id | E-40-07 |
| convention-version | 1 |
| batch | 2026-11-02-batch-40 |
| step | 步骤五收口（跨项裁决） |
| gate-or-kd | 判据 §45–§51 共通门“设备绿绑定源快照” |
| verdict | **BLOCKED**（当前树不能复现设备绿；另有 p09 红、PDF/KD-76 入口缺失） |
| executed-at | 2026-11-02T10:57+08:00 |
| operator | 设备验证轨 |
| head | 起始 `a3f367df` → 执行中 `dee81ea7`（2026-11-02T10:45:47+08:00） |
| worktree | `git status --porcelain` 起始 653 条；源漂移 169 行 / 60 文件 |
| device-serial | emulator-5584（API 34 / x86_64 / 4096） |
| apk-sha256 | app `1acb9907…5983`；appTest `565fc85b…7c7b`；dataTest `34e3435d…3179`；dbTest `f12ef06a…6f92` |
| exit-code | 见各 step；本文件为裁决 |
| raw-log | `build/device-track-batch40/{src-snapshot-drift.diff,src-sha-before.txt,src-sha-current.txt,src-sha-final.txt,target-dirty-status.txt}` |
| record-ref | `docs/research/2026-11-02-batch-40-record.md` |
| sanitized | 是 |

## 1. HEAD 与工作树在设备窗口内漂移

| 时点 | 事件 | 证据 |
|---|---|---|
| 10:11 | 起始 HEAD `a3f367df`；`STUDY_DATABASE_VERSION=73`，plan=73，73.json 在树 | `env.txt`；构建前 grep |
| 10:13 | 四包构建成功；源快照 before/after diff=0（1277 文件） | E-40-01 §3 |
| 10:20 | 四包安装；设备侧 sha 与宿主一致 | E-40-01 §4 |
| 10:27–10:56 | 设备套件串行执行（28 套件 + 错因 + 备份 + drill） | E-40-02b/05 |
| **10:33:32** | KB 32B 的文件级还原（`rollback_32b_unadopted.py`）把 `StudyDatabase.kt` 覆盖回 `STUDY_DATABASE_VERSION=71`；`src-sha-before` `ab50bbd8…` → `src-sha-final` `7210f974…`，mtime 10:33:32；KSP 生成物 10:35:28 已是 `RoomOpenDelegate(71,"714c0ecb…")` | `ls -l --time-style=full-iso`；`src-sha-before/final.txt`；`grep RoomOpenDelegate` |
| **10:45:47** | KB 线提交 `dee81ea7`（“批次32B 结案——…回滚至 v26”）；HEAD 变更；`git show --stat` 仅 2 个 docs 文件（60 insertions），**未触碰** `StudyDatabase.kt` | `git log -1`；`git show --stat dee81ea7` |
| 10:51 | 当前树：DB=71，plan=73，73.json=73；`ERROR_CAUSE_MIGRATION_72_73`/`ErrorCause*Entity` 在 `StudyDatabase.kt` 零命中 | `grep` |
| 10:57 | 源漂移 diff=169 行，涉及 60 文件（KB/dense/moe/StudyDatabase 等） | `src-snapshot-drift.diff` |

**结论**：设备绿绑定的是 **10:13 建造的四包 APK + 当时 73 源快照**；当前 HEAD/工作树已不是该状态。复核轮已按主会话裁定 rebase 到 71（73=92 表）并接线，KSP 已生成 rebased `73.json` 并回填 identityHash；但设备绿仍未在 rebased 树上重跑。按判据“任何漂移→该窗作废重跑”，本窗不能对当前树作通过声明。

## 2. 单项状态汇总

| 判据 | 状态 | 证据 |
|---|---|---|
| §45 PDF 设备往返 | **BLOCKED**：设备用例不在树（`grep PdfAigc` in androidTest = 0） | E-40-02a |
| §46 schema 73 | **PARTIAL**：APK 快照全绿（matrix 16/16、backup 34/34 等）；当前树已按裁定 rebase 到 92 表并接线，KSP 已生成 92 表/110 FK、identityHash 已回填 `1d086050…` | E-40-02b §4 |
| §47 24+ 套件 | **FAIL**：27/28 绿、237 例；p09 1 红（T4/D 候选，未定案） | E-40-05 |
| §48 签名/worker | **PASS（附 p10 设计 dnc=1）**：九类签名其余 0；spawn 29/warm 29/fail 0/unpaired 0；无孤儿 | E-40-05 |
| §49 KD-76 | **BLOCKED**：无单轮入口，不得用单进程 20 轮冒充 | E-40-03 |
| §50 isolation | **PARTIAL**：单臂 15/15 绿；低负载门未建立、A3 分布不符、控制轮未做 | E-40-04 |
| §51 KD-71 | **PASS（条件式）**：三文件哈希同值、3×27 绿、签名 0；G4 未做 | E-40-06 |

## 3. 单测门/强门（非设备项，但影响总验收）

- 本会话扫描 533 个现有 unit-test XML（排除 `.worktrees/.artifacts/.jez`），唯一失败：
  `core/database` `MaterialFeatureScaleTest`：`v26 材料数 expected:<65021> but was:<64839>`（1 failed / 1 test）。
- **该快照已过期（复核更正）**：上述 XML 的 mtime 为 09:38–09:41（当时树=DB 73），`MaterialFeatureScaleTest.kt` 已随 KB 32B 回滚删除（`find core -name MaterialFeatureScaleTest.kt` 无输出），不能再代表当前红集。复核轮 rebase 后：`STUDY_DATABASE_VERSION=73` 且 rebased `73.json` 尚未由 KSP 生成（最高导出=72），`KernelWave0SchemaContractTest.kt:36-56` 的“版本常量 == 最高导出”断言会保持红——这是“提了版本但没导出”的诚实信号，门禁 KSP 生成后应转绿（identityHash 仍需回填）。
- 归因 **B（KB L2 他线）** 仅适用于 `MaterialFeatureScaleTest` 那一行，不适用于当前树的 schema 头红；与题目“单测门第 3 轮 exit=1；强门未跑”一致。
- 强门未跑；总验收“双门 exit=0”**未达成**，设备轨不得替主会话宣称完成。

## 4. 必须由主会话处理的前置

1. **73 基线（已裁定）**：主会话 2026-11-02 复核轮裁定 **rebase 到 71、不恢复 L2**；73 = 71(90) + 错因两表 = **92 表**，直连 `MIGRATION_71_73`，`72.json` 作为 orphan 保留。KSP 已生成 rebased `73.json`（92 表/110 FK）。
2. **WS2 73 注册（已接线并回填）**：`STUDY_DATABASE_VERSION=73`、`ErrorCauseObservationEntity`/`ErrorCauseBindingEntity`、`errorCauseDao()`、`MIGRATION_71_73` 与 `ERROR_CAUSE_MIGRATION_72_73` 已在树；`IDENTITY_HASH` 已回填 KSP 生成的 `1d086050204a0da1ed6b6e3299dd9733`。
3. **同号 73 冲突（已裁定）**：本线占 73（92 表）；`codex/ui-green-pages-2026-10-27` 工作树的 97 表/`4d2c8e0f…` 在合并时重编号到 74 并重跑 KSP。
4. 重新执行 E-40-01 的构建→源快照 diff=0→四包安装→sha/asset/dex 门；再跑 §46/§47 的设备套件。
5. 处理 p09：用例加固（确定性阻塞帧）或产品队列语义修复；不得以重跑绿替代。
6. 若要关闭 §45/§49：补 `PdfAigcDeviceRoundTripInstrumentedTest` 与 KD-76 单轮入口（测试代码改动）。

## 5. 本批设备轨可保留的结论

- 在 **10:13 APK** 上：schema 73 迁移矩阵 16/16、备份 34 例、rowcounts/trimmer/c4/drill、错因账本 1 例、27/28 套件、KD-71 3×27 均绿；签名/worker 账本干净。
- 这些是“APK 快照绿”，不是“当前树绿”；当前树与单测门均红。
- 设备为模拟器（API 34 / x86_64），不外推真机。
