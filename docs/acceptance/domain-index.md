# 批次 45 域索引（域 → 计数 → 复核 → 出口）

> 台账 sha256：`887e3ba649eb17a70c0f6f7af8a657d606398a10925c6116818265387af689aa`；run-id：`2026-11-05T06:31:41Z-batch45-acceptance-report`。
> 本文件是 `docs/acceptance/verdicts.md` 的域级导航，不单独构成验收结论。

| 域 | 检查主题 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED | 域级复核 | 域出口 |
|---|---|---:|---:|---:|---:|---:|---|---|
| D0 验收基础设施 | 验收基础设施 | 100 | 93 | 2 | 0 | 5 | — | acceptance_report / lint / trace 复跑；补逐行终裁 JSON 与冲突规则 |
| A 功能旅程 | 功能旅程 | 90 | 65 | 0 | 1 | 24 | UNVERIFIED | runbook §9；补大数据/权限/中断设备用例 |
| B 数据完整性 | 数据完整性 | 90 | 88 | 0 | 0 | 2 | UNVERIFIED | 补 mask=13 夹具；迁移矩阵已在册 |
| C 视觉与渲染 | 视觉与渲染 | 90 | 77 | 1 | 0 | 12 | FAIL | runbook §5；C-065 复跑；单类证据补第二类 |
| D 模型链路 | 模型链路 | 90 | 81 | 0 | 9 | 0 | UNVERIFIED | runbook §9；真实 Provider 密钥 |
| E 复习与算法 | 复习与算法 | 90 | 80 | 0 | 0 | 10 | UNVERIFIED | 补并发/DST/设备事务用例；行级复核绑定 |
| F 性能与稳定 | 性能与稳定 | 90 | 55 | 2 | 10 | 23 | UNVERIFIED | runbook §9.1；arm64 真机与历史基线 |
| G 安全与隐私 | 安全与隐私 | 90 | 82 | 0 | 2 | 6 | BLOCKED | runbook §9；离线 advisory、锁文件落地 |
| H 合规与文案 | 合规与文案 | 90 | 66 | 5 | 12 | 7 | UNVERIFIED | runbook §9 / §13 FIX-T16–T18；热区授权与外部材料 |
| I 可用性与无障碍 | 可用性与无障碍 | 90 | 53 | 0 | 5 | 32 | UNVERIFIED | runbook §6 / §9；TalkBack/IME/真机 |
| J 发布工程 | 发布工程 | 90 | 21 | 13 | 34 | 22 | — | runbook §9.2；签名、arm64、成对产物、Play |
| FIX 修复与加固 | 修复与加固 | 100 | 15 | 0 | 85 | 0 | — | runbook §13 FIX-T04–T20 |
| GATE 门禁与终裁 | 门禁与终裁 | 100 | 89 | 7 | 3 | 1 | — | runbook §9；人工金标与对抗终裁 |
| **合计** | — | **1200** | **865** | **30** | **161** | **144** | 无域级 PASS | — |

## 证据落点

- 逐项终裁：`docs/acceptance/verdicts.md`
- 机器报告：`docs/acceptance/acceptance-report.json`
- 门禁原始输出：`docs/acceptance/evidence/2026-11-06-batch-45/`
- 十域复核 JSON：`docs/acceptance/verdicts/{A..J}-review.json`
- 批次 45 域证据：`docs/research/evidence/2026-11-06-batch-45/`
- 终裁：`docs/acceptance/adjudication.md`
