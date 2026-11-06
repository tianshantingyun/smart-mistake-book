# 批次 48 域索引（域 → 计数 → 行级复核 → 出口）

> 台账 sha256：`28e96a8d8cfde03f5072bcd360e17ca64d3def918cd84d2722ca21b3b3b76375`；run-id：批次 48（D 域设备取证 `2026-11-06T18:40:12Z-batch48-d-domain-device`；WS4 门禁复跑 2026-11-07）。
> 本文件是 `docs/acceptance/verdicts.md` 的域级导航，不单独构成验收结论。

| 域 | 检查主题 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED | 行级复核 | 域出口 |
|---|---|---:|---:|---:|---:|---:|---|---|
| D0 验收基础设施 | 验收基础设施 | 100 | 100 | 0 | 0 | 0 | 100/100 | acceptance_report --require-complete exit 1（NOT_ACCEPTANCE_COMPLETE + REVIEW_CHECKS_SHARED 18 组）；lint exit 1（144 EVIDENCE_REUSED）/ warn-only exit 0；trace exit 0；d0 --group all 本批未跑（批次 47 已知 h-061_fail 陈旧预期遗留） |
| A 功能旅程 | 功能旅程 | 90 | 65 | 0 | 25 | 0 | 65/65 | blocked-runbook A 域 §A-1..A-5；缺用例/大样本/产品口径 |
| B 数据完整性 | 数据完整性 | 90 | 88 | 0 | 2 | 0 | 88/88 | blocked-runbook B 域 §B-1/B-2；mask=13 状态夹具 |
| C 视觉与渲染 | 视觉与渲染 | 90 | 88 | 0 | 2 | 0 | 88/88 | blocked-runbook C 域 §C-1/C-2；C-008/C-088 仍阻塞 |
| D 模型链路 | 模型链路 | 90 | 82 | 0 | 8 | 0 | 82/82 | blocked-runbook §1 Provider；D-089 已关闭（批次 48 E-48-D-DEV-01 设备取证）；D-082 上游请求 id 绑定、D-083..086/088 失败注入、D-090 无 key 复跑仍 BLOCKED |
| E 复习与算法 | 复习与算法 | 90 | 81 | 0 | 9 | 0 | 81/81 | blocked-runbook E 域 §E-1..E-3；并发/真事务/DST |
| F 性能与稳定 | 性能与稳定 | 90 | 55 | 2 | 33 | 0 | 55/55 | blocked-runbook F 域；F-055/F-057 soak 残余 2 FAIL；arm64 行按用户裁定豁免注记（verdict 维持 BLOCKED，不伪造 PASS） |
| G 安全与隐私 | 安全与隐私 | 90 | 83 | 0 | 7 | 0 | 83/83 | blocked-runbook G 域；G-089 已关闭（批次 47 verification-metadata.xml）；离线 advisory |
| H 合规与文案 | 合规与文案 | 90 | 69 | 2 | 19 | 0 | 69/69 | blocked-runbook H 域；KD-84 已 closed（批次 47，H-061）；KD-85 与热区授权仍 open |
| I 可用性与无障碍 | 可用性与无障碍 | 90 | 53 | 0 | 37 | 0 | 53/53 | blocked-runbook I 域 §9.5；TalkBack/IME/真机/独立复核签字 |
| J 发布工程 | 发布工程 | 90 | 29 | 13 | 48 | 0 | 29/29 | blocked-runbook J 域；13 FAIL 全保留；arm64/真机行豁免注记维持 BLOCKED（干净树/生产签名仍是结构前提） |
| FIX 修复与加固 | 修复与加固 | 100 | 15 | 0 | 85 | 0 | 15/15 | blocked-runbook FIX 域（85 行，FIX-T04–T20 映射）；FIX-041..045 豁免注记维持 BLOCKED |
| GATE 门禁与终裁 | 门禁与终裁 | 100 | 90 | 6 | 4 | 0 | 90/90 | blocked-runbook GATE 域；AUD-08 v2 门 FAIL（① 10/24=0.4167 <0.9 未过；③ 0.0385≤0.05 过；切残 fragmentedQuestions=3≠0，WS2 gate-report-v2-01.json）；GATE-096..098/100 维持 BLOCKED（金标 v2 为 G1 model-labeled，非 G3/G4/G5 人工金标） |
| **合计** | — | **1200** | **898** | **23** | **279** | **0** | **898/898** | 无域级 PASS |

## 证据落点

- 逐项终裁：`docs/acceptance/verdicts.md`
- 行级复核 JSON：`docs/acceptance/verdicts/rows/<ID>.json`（PASS/FAIL 行）
- 机器报告：`docs/acceptance/acceptance-report.json`
- 门禁原始输出：`docs/acceptance/evidence/2026-11-07-batch-48/gate/`
- 三份哈希清单：`docs/acceptance/evidence/2026-11-07-batch-48/{REVIEWS,GATES}.sha256` + `docs/research/evidence/2026-11-07-batch-48/EVIDENCE.sha256`
- 批次 48 域证据：`docs/research/evidence/2026-11-07-batch-48/`（golden-v2 / splitter / d-domain）
- BLOCKED runbook：`docs/acceptance/blocked-runbook.md`（批次 47 WS3 建立，批次 48 WS3 更新）
- 终裁：`docs/acceptance/adjudication.md`
