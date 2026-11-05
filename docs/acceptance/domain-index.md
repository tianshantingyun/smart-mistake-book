# 批次 46 域索引（域 → 计数 → 行级复核 → 出口）

> 台账 sha256：`19ea6b57c5bbe73281232e170fb491a1360b6c1c87e0c9205e15c3f075f33e7b`；run-id：`2026-11-06T09:00:00Z-batch46-line-review`。
> 本文件是 `docs/acceptance/verdicts.md` 的域级导航，不单独构成验收结论。
>
> **批次 46 修复环（2026-11-05）**：表中“行级复核 100%”只表示每个 PASS 行有
> `rows/<ID>.json` 且逐项复算通过；其中 **318 行的非 `ledger_row` checks 与其他行
> 逐字节相同**（24 组 ≥5 行，覆盖 156 行），`pass_with_row_specific_review=575/888`。域级“行级复核”
> 不等于行级判别力，也不等于第二人/第二模型语义复核。

| 域 | 检查主题 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED | 行级复核 | 域出口 |
|---|---|---:|---:|---:|---:|---:|---|---|
| D0 验收基础设施 | 验收基础设施 | 100 | 100 | 0 | 0 | 0 | 100/100 | acceptance_report/lint/trace/d0_self_acceptance 复跑；行级复核 100% |
| A 功能旅程 | 功能旅程 | 90 | 65 | 0 | 25 | 0 | 65/65 | runbook §9；大数据/权限/中断设备用例缺口 |
| B 数据完整性 | 数据完整性 | 90 | 88 | 0 | 2 | 0 | 88/88 | runbook §9；mask=13 夹具缺口 |
| C 视觉与渲染 | 视觉与渲染 | 90 | 88 | 0 | 2 | 0 | 88/88 | runbook §5/§9；C-008/C-088 仍阻塞 |
| D 模型链路 | 模型链路 | 90 | 81 | 0 | 9 | 0 | 81/81 | runbook §9；真实 Provider 密钥 |
| E 复习与算法 | 复习与算法 | 90 | 81 | 0 | 9 | 0 | 81/81 | runbook §9.1；并发/DST 用例缺口 |
| F 性能与稳定 | 性能与稳定 | 90 | 48 | 9 | 33 | 0 | 48/48 | runbook §9.1；arm64 真机与历史基线 |
| G 安全与隐私 | 安全与隐私 | 90 | 82 | 1 | 7 | 0 | 82/82 | runbook §9；供应链元数据与离线 advisory |
| H 合规与文案 | 合规与文案 | 90 | 68 | 3 | 19 | 0 | 68/68 | runbook §9/§13；KD-84/KD-85 与热区授权 |
| I 可用性与无障碍 | 可用性与无障碍 | 90 | 53 | 0 | 37 | 0 | 53/53 | runbook §6/§9；TalkBack/IME/真机 |
| J 发布工程 | 发布工程 | 90 | 29 | 13 | 48 | 0 | 29/29 | runbook §9.2；签名、arm64、成对产物、Play |
| FIX 修复与加固 | 修复与加固 | 100 | 15 | 0 | 85 | 0 | 15/15 | runbook §13 FIX-T04–T20 |
| GATE 门禁与终裁 | 门禁与终裁 | 100 | 90 | 6 | 4 | 0 | 90/90 | runbook §9；AUD-08 与人工金标 |
| **合计** | — | **1200** | **888** | **32** | **280** | **0** | **888/888** | 无域级 PASS |

## 证据落点

- 逐项终裁：`docs/acceptance/verdicts.md`
- 行级复核 JSON：`docs/acceptance/verdicts/rows/<ID>.json`（PASS/FAIL 行）
- 机器报告：`docs/acceptance/acceptance-report.json`
- 门禁原始输出：`docs/acceptance/evidence/2026-11-06-batch-46/gate/`
- 三份哈希清单：`docs/acceptance/evidence/2026-11-06-batch-46/{EVIDENCE,REVIEWS,GATES}.sha256`
- 批次 46 域证据：`docs/research/evidence/2026-11-06-batch-46/`
- 终裁：`docs/acceptance/adjudication.md`
