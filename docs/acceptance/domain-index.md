# 批次 50 域索引（域 → 计数 → 行级复核 → 出口）

> 台账 sha256：`9044a7794dda196c6fc22c8e989a33099482ef26399e13ffbcbe28c0f045cb97`；run-id：`2026-11-09T00:00:00Z-batch50-ledger`。
> 本文件是 `docs/acceptance/verdicts.md` 的域级导航，不单独构成验收结论。

| 域 | 检查主题 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED | 行级复核 | 域出口 |
|---|---|---:|---:|---:|---:|---:|---|---|
| D0 验收基础设施 | 验收基础设施 | 100 | 100 | 0 | 0 | 0 | 100/100 | acceptance_report --require-complete exit 1（NOT_ACCEPTANCE_COMPLETE + REVIEW_CHECKS_SHARED）；lint exit 1（EVIDENCE_REUSED，诊断面）/ warn-only exit 0；trace exit 0；acceptance_init --check exit 0；d0 --group all 本批未跑 |
| A 功能旅程 | 功能旅程 | 90 | 65 | 0 | 25 | 0 | 65/65 | blocked-runbook A 域；批次50 将 5c 错因面板/6B-3 改卷 UI/5b 容量设备证据追加到 A-028/A-037，A-031 等 25 行仍缺独立空态/大数据/权限用例 |
| B 数据完整性 | 数据完整性 | 90 | 88 | 0 | 2 | 0 | 88/88 | blocked-runbook B 域 §B-1/B-2；**schema 75 设备矩阵已重跑并换绑**（迁移 1..75 20 例、备份 34、trimmer 10、rowCounts 2、drill 4 绿）；B-077/B-078（mask=13 partial/corrupt）仍缺夹具 |
| C 视觉与渲染 | 视觉与渲染 | 90 | 88 | 0 | 2 | 0 | 88/88 | blocked-runbook C 域 §C-1/C-2；C-008/C-088 仍阻塞（本批未动） |
| D 模型链路 | 模型链路 | 90 | 82 | 0 | 8 | 0 | 82/82 | blocked-runbook §1 Provider；D-082..086/088/090 仍 BLOCKED（本批未动） |
| E 复习与算法 | 复习与算法 | 90 | 81 | 0 | 9 | 0 | 81/81 | blocked-runbook E 域 §E-1..E-3；并发 7 行 + DST 1 行保持 BLOCKED；批次50 追加 3E/5b/5c/KD-67 证据到 E-017/E-065/E-070/E-075/E-080/E-081（E-070 仍 BLOCKED） |
| F 性能与稳定 | 性能与稳定 | 90 | 57 | 1 | 32 | 0 | 57/57 | blocked-runbook F 域；批次50 release APK 2h soak（7200s/115 轮）刷新 F-055/F-059/F-063；F-057 因超限路径未命中（FATAL=0）维持 FAIL；F-056/F-058 仍 BLOCKED；arm64/16KB 运行时未验 |
| G 安全与隐私 | 安全与隐私 | 90 | 83 | 0 | 7 | 0 | 83/83 | blocked-runbook G 域；G-089 已关闭（批次 47）；本批未动 |
| H 合规与文案 | 合规与文案 | 90 | 74 | 0 | 16 | 0 | 74/74 | blocked-runbook H 域；批次50 AIGC（显式角标/DOCX/PDF/KD-85/KD-84）与隐私未成年人专章将 H-008/H-018/H-025/H-043/H-062 转 PASS；H-024/H-036/H-053 外部 BLOCKED/FAIL；H-063/H-070/H-075/H-076/H-077/H-080/H-081/H-089/H-090 仍为人工/外部项 |
| I 可用性与无障碍 | 可用性与无障碍 | 90 | 53 | 0 | 37 | 0 | 53/53 | blocked-runbook I 域 §9.5；TalkBack/IME/真机/独立复核签字（本批未动） |
| J 发布工程 | 发布工程 | 90 | 37 | 6 | 47 | 0 | 37/37 | blocked-runbook J 域；批次50 本地 release key/AAB 结构/versionCode 注入将 J-022/J-024/J-025/J-027/J-033/J-034/J-041/J-051 转 PASS；J-001/J-002/J-020/J-021/J-048/J-052 仍 FAIL（干净树/生产钥匙/跨构建单调）；arm64/16KB 运行时/Play 账号仍 BLOCKED |
| FIX 修复与加固 | 修复与加固 | 100 | 35 | 0 | 65 | 0 | 35/35 | blocked-runbook FIX 域；批次50 FIX-T04/T05/T07/T17 关闭态门将 FIX-016..020/021..025/031..035/081..085 转 PASS；其余修复单仍 BLOCKED（真实 Provider/arm64/生产签名/Play 等外部项） |
| GATE 门禁与终裁 | 门禁与终裁 | 100 | 90 | 6 | 4 | 0 | 90/90 | blocked-runbook GATE 域；AUD-08 批次50 单次跑 FAIL（① 7/24=0.2917 <0.9；③ 0.0128≤0.05；硬红线 fragmentedQuestions=1≠0）；GATE-096..098/100 维持 BLOCKED（金标 G1 model-labeled） |
| **合计** | — | **1200** | **933** | **13** | **254** | **0** | **933/933** | 无域级 PASS；`--require-complete` exit 1 |

## 证据落点

- 逐项终裁：`docs/acceptance/verdicts.md`
- 行级复核 JSON：`docs/acceptance/verdicts/rows/<ID>.json`（PASS/FAIL 行）
- 机器报告：`docs/acceptance/acceptance-report.json`
- 门禁原始输出：`docs/acceptance/evidence/2026-11-09-batch-50/gate/`
- 批次 50 证据：`docs/research/evidence/2026-11-09-batch-50/`（3e/d1/d2/d3/mva/split-gate/5b/5c/6b-3/js/kd67/aigc/privacy/release/device/g0）
- 哈希清单：`docs/acceptance/evidence/2026-11-09-batch-50/REVIEWS.sha256`、`.../GATES.sha256`、`docs/research/evidence/2026-11-09-batch-50/EVIDENCE.sha256`
- BLOCKED runbook：`docs/acceptance/blocked-runbook.md`
- 终裁：`docs/acceptance/adjudication.md`（批次50 段由主会话收口时追加）
