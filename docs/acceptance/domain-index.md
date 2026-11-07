# 批次 49 域索引（域 → 计数 → 行级复核 → 出口）

> 台账 sha256：`264425f5796f0597e7f3ed8d9b7fe359f112d774eae1e0ab7256c1e9ea9f6ba8`；run-id：`2026-11-08T02:45:00Z-batch49-ledger-freeze`。
> 修复环（2026-11-08）重锚：F-057 维持 FAIL、F-055/F-063 证据改为 file 出处、复核按新 sha 重绑。
> 本文件是 `docs/acceptance/verdicts.md` 的域级导航，不单独构成验收结论。

| 域 | 检查主题 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED | 行级复核 | 域出口 |
|---|---|---:|---:|---:|---:|---:|---|---|
| D0 验收基础设施 | 验收基础设施 | 100 | 100 | 0 | 0 | 0 | 100/100 | acceptance_report --require-complete exit 1（NOT_ACCEPTANCE_COMPLETE + REVIEW_CHECKS_SHARED 18 组）；lint exit 1（144 EVIDENCE_REUSED）/ warn-only exit 0；trace exit 0；acceptance_init --check exit 0；d0 --group all 本批未跑 |
| A 功能旅程 | 功能旅程 | 90 | 65 | 0 | 25 | 0 | 65/65 | blocked-runbook A 域；缺用例/大样本/产品口径；批次49 A-019/A-021/A-064/A-067 追加设备/模拟证据 |
| B 数据完整性 | 数据完整性 | 90 | 88 | 0 | 2 | 0 | 88/88 | blocked-runbook B 域 §B-1/B-2；mask=13 状态夹具 |
| C 视觉与渲染 | 视觉与渲染 | 90 | 88 | 0 | 2 | 0 | 88/88 | blocked-runbook C 域 §C-1/C-2；C-008/C-088 仍阻塞 |
| D 模型链路 | 模型链路 | 90 | 82 | 0 | 8 | 0 | 82/82 | blocked-runbook §1 Provider；D-089 已关闭（批次 48 设备取证）；D-082..086/088/090 仍 BLOCKED |
| E 复习与算法 | 复习与算法 | 90 | 81 | 0 | 9 | 0 | 81/81 | blocked-runbook E 域 §E-1..E-3；并发 7 行 + DST 1 行保持 BLOCKED；批次49 E-009/E-044/E-080/E-081 追加标定/确认/校准/冲突证据 |
| F 性能与稳定 | 性能与稳定 | 90 | 57 | 1 | 32 | 0 | 57/57 | blocked-runbook F 域；批次49 2h soak 将 F-055（FAIL）与 F-063（BLOCKED）转 PASS；F-057 因超限路径未命中（FATAL=0）维持 FAIL；F-056 阈值/PSS 有界仍 BLOCKED；arm64/阈值行按用户裁定豁免注记，verdict 维持 BLOCKED，不伪造 PASS |
| G 安全与隐私 | 安全与隐私 | 90 | 83 | 0 | 7 | 0 | 83/83 | blocked-runbook G 域；G-089 已关闭（批次 47）；离线 advisory |
| H 合规与文案 | 合规与文案 | 90 | 69 | 2 | 19 | 0 | 69/69 | blocked-runbook H 域；KD-84 已 closed；KD-85 与热区授权仍 open；H-018/H-062 维持 FAIL |
| I 可用性与无障碍 | 可用性与无障碍 | 90 | 53 | 0 | 37 | 0 | 53/53 | blocked-runbook I 域 §9.5；TalkBack/IME/真机/独立复核签字 |
| J 发布工程 | 发布工程 | 90 | 29 | 13 | 48 | 0 | 29/29 | blocked-runbook J 域；13 FAIL 全保留；arm64/真机行豁免注记维持 BLOCKED |
| FIX 修复与加固 | 修复与加固 | 100 | 15 | 0 | 85 | 0 | 15/15 | blocked-runbook FIX 域；FIX-041..045 豁免注记维持 BLOCKED |
| GATE 门禁与终裁 | 门禁与终裁 | 100 | 90 | 6 | 4 | 0 | 90/90 | blocked-runbook GATE 域；AUD-08 v2 门 FAIL（① 10/24=0.4167 <0.9 未过；③ 0.0385≤0.05 过；切残 fragmentedQuestions=3≠0）；GATE-096..098/100 维持 BLOCKED（金标 v2 为 G1 model-labeled） |
| **合计** | — | **1200** | **900** | **22** | **278** | **0** | **900/900** | 无域级 PASS |

## 证据落点

- 逐项终裁：`docs/acceptance/verdicts.md`
- 行级复核 JSON：`docs/acceptance/verdicts/rows/<ID>.json`（PASS/FAIL 行）
- 机器报告：`docs/acceptance/acceptance-report.json`
- 门禁原始输出：`docs/acceptance/evidence/2026-11-08-batch-49/gate/`
- 三份哈希清单：`docs/acceptance/evidence/2026-11-08-batch-49/{REVIEWS,GATES}.sha256` + `docs/research/evidence/2026-11-08-batch-49/EVIDENCE.sha256`
- 批次 49 域证据：`docs/research/evidence/2026-11-08-batch-49/`（calibration / calibration-page / p2-conflict / device / jvm）
- 证据总索引：`docs/research/evidence/2026-11-08-batch-49/00-index.md`
- BLOCKED runbook：`docs/acceptance/blocked-runbook.md`
- 终裁：`docs/acceptance/adjudication.md`
