# B 域（数据完整性）schema 75 标注 — 批次 50 / G0

> **结论先行（批次50 收口更新）**：本文件写作时 ledger 的 B 行是 schema 73 历史结论；**收口轮已按台账工具链把
> B-001..B-072/B-073..B-090（除 B-077/B-078）换绑到 schema 75 设备证据**（迁移 1..75 20 例、备份 34、
> trimmer 10、rowCounts 2、drill 4 绿），ledger sha256 由 `264425f5…f6ba8` 变为
> `9044a779…5cb97`。**保留本节作为 73 历史口径记录；当前 B 行结论以 ledger 与
> `docs/acceptance/evidence/2026-11-09-batch-50/` 的证据 token 为准。**
>
> **写作时结论（历史）**：`docs/acceptance/ledger.md` 的 B-001..B-090 全部 PASS 是 **schema 73 时点的历史结论**，
> **不构成 schema 75 的通过证据**。schema 75 的 B 域证据在本文件写作时 **UNVERIFIED / NOT RUN**；
> 必须用 `docs/research/evidence/2026-11-09-batch-50/g0/04-device-rerun-commands.md` 的设备复跑
> 产出新日志并按台账流程换绑后，才可对 75 主张任何 B 行通过。

## 1. 为什么是「历史」

本会话只读核对（`docs/acceptance/ledger.md`）：

- B 域共 **90 行**（B-001..B-090）。
- 每行的证据 token 全部指向 `docs/research/evidence/2026-11-05-batch-44/`：282 个
  `cmd/test/device/review` token，**0 个**指向 2026-11-06 之后或 schema 75 的批次。
- 其中 **73 行**的检查描述明写 `v73`（B-001..B-072 的迁移行是「vN → v73」）；
  其余 17 行（B-073..B-090：mask/损坏包/CAS/事务恢复）描述里没写版本，但证据仍是同一
  batch-44（schema 73）设备轮次。版本没写不等于对 75 成立。
- 台账当前的 ledger sha256 为
  `264425f5796f0597e7f3ed8d9b7fe359f112d774eae1e0ab7256c1e9ea9f6ba8`
  （本会话 `sha256sum docs/acceptance/ledger.md` 实测），与 `domain-index.md:3` 冻结值一致。

## 2. 为什么不直接改 `ledger.md` 的 verdict

`tools/ci/acceptance_report.py` 会把每份行级复核 JSON 的 `ledger_sha256` 与**当前
`ledger.md` 字节哈希**比对（`acceptance_report.py:224-231`），哈希由
`acceptance_report.py:346` 对整文件计算。改 `ledger.md` 任意一个字节都会让
`docs/acceptance/verdicts/rows/` 下的 922 份复核 JSON 全部失配，进而让
`acceptance_report` / `acceptance_lint` 直接报错——那是把「标注历史」变成「伪造/破坏台账」。

因此本标注写在**不参与该哈希绑定**的文件里；B 行 verdict 保持 PASS（作为 73 目标的历史记录），
语义解释以本文件为准。等 75 设备证据齐备后，应由台账工具链统一换绑/改判，而不是手改行文本。

## 3. 75 需要的证据（与 G0 命令一一对应）

| B 行范围 | 73 时点覆盖 | 75 复跑命令（本轮 NOT RUN） | 75 证据落点 |
|---|---|---|---|
| B-001..B-072 | 迁移 v1..v73 矩阵 | `g0/04` §2 `FullMigrationMatrixInstrumentedTest#everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback`（1..75） | `g0/device-migration-1-75.log` |
| B-073..B-081 | 备份 mask 9/13/15 的 clean/partial/corrupt | `g0/04` §3 `BackupRestoreInstrumentedTest`（34 例）+ §4 trimmer（10 例） | `g0/device-backup-34.log`、`g0/device-trimmer.log` |
| B-082 | v4（schema 72）包恢复 | `g0/04` §7 `BackupV4PackRestoreInstrumentedTest`（断言已升到当前 75） | `g0/device-selection-v4-restore.log` |
| B-083 | v5（closure 5）包 → v73 | 需在 75 上重跑 selection restore；`g0/04` §7 | 同上 |
| B-084..B-090 | 损坏包/CAS/事务回滚 | `g0/04` §3（`BackupRestoreInstrumentedTest` 内对应用例） | `g0/device-backup-34.log` |
| 行数覆盖（rowCounts） | 73.json / 92 表 | `g0/04` §5 `BackupRowCountsCoverageInstrumentedTest`（静态已升 75/99） | `g0/device-rowcounts.log` |
| 换机演练（drill） | schema 73 全量/子集往返 | `g0/04` §6 `DeviceMigrationDrillInstrumentedTest`（需 `smb_allow_data_reset=true`） | `g0/device-drill-optin.log` |

## 4. 引用纪律

- 引用 B 行 PASS 时必须同时写证据批次日；批次 50 收口后 B 行证据已换绑到 75 设备矩阵，
  不得再引用 batch-44 的 v73 证据当 75 通过。
- 在 75 证据落盘前，任何「B 域已对 75 验收」的表述都是假的；应写
  `75: UNVERIFIED（旧 PASS 为 v73 历史）`。
- 75 证据落盘后，仍不得把 batch-44 的 PASS 直接换绑成 75 证据；必须按台账工具链重新复核
  （≥2 类独立证据 + 行级 review JSON 重算 `ledger_sha256`）。**批次50 收口已按此执行**：
  B 行证据改为批次50 设备 JSON/日志，行级 JSON 重算新 ledger sha，`acceptance_report` 0 违规。

## 5. 本文件的事实来源

- `docs/acceptance/ledger.md`（B 行文本、证据 token、ledger sha）。
- `docs/acceptance/domain-index.md:11`（B 域 90 行、88 PASS / 2 BLOCKED 的域级计数）。
- `tools/ci/acceptance_report.py:224-231`、`:346`（ledger sha 绑定规则）。
- `docs/research/evidence/2026-11-09-batch-50/g0/03-static-preconditions.txt`（75.json / 测试类计数 / 设备）。
- `docs/research/evidence/2026-11-09-batch-50/g0/04-device-rerun-commands.md`（复跑命令）。
