# 验收 Definition of Done（批次 44）

> 本文件定义“什么算完成”。它与 `docs/acceptance/ledger.md` 的 1200 行一一对应：
> 台账行写检查，本文件写判据；`tools/ci/acceptance_report.py` 与
> `tools/ci/acceptance_lint.py` 把判据变成 exit code。
>
> **当前状态**：本批交付的是验收基础设施与 1200 行骨架，不是产品完成声明。
> 骨架初始 verdict 全为 `UNVERIFIED`；不得据此声称任何功能“通过”。

## 1. 完成的六个面

### 1.1 功能（happy path）

- 有可复跑的主路径命令/测试/设备步骤；
- 断言的业务结果（不是“无异常”）；
- 结果可定位到台账行 id。

### 1.2 失败路径

- 错误可见、业务语言可读；
- 失败后数据安全（不静默丢写、不半提交）；
- 至少一条负向断言或故障注入；
- 失败路径与主路径共用同一身份/幂等规则。

### 1.3 边界

覆盖受影响面的空态、0/1/极大值、权限拒绝、旋转、后台、杀进程、低电/低存、
时区/DST、并发/重试/取消/进程重建。边界检查必须是可执行断言，不是描述。

### 1.4 证据

一条 `PASS` 至少需要 **2 类独立证据**：

| 类 | 形态 | 最低要求 |
|---|---|---|
| `cmd` | 命令日志 | 文件存在、exit 可读且为 0、无 `-4`/SKIP/AssumptionViolated |
| `test` | JUnit XML | `tests>0`、`failures=0`、`errors=0` |
| `device` | 设备输出 JSON/日志 | 文件存在；JSON 含 `serial` 与 `apk_sha256` |
| `review` | 独立复核 JSON | 含 `reviewer` + `run_id`，reviewer 不在 authors 内 |

`file` / `hash` 是辅助证据，**不计入**独立证据类。summary、批次记录、复盘文档
不得作为证据（lint `SUMMARY_AS_EVIDENCE`）。

### 1.5 独立复核

- reviewer 不是该行作者；
- 复核文件绑定 `run_id` 与台账 `ledger_sha256`；
- 复核结论只针对该行检查，不写成总体通过；
- 独立复核不能替代命令/设备证据。

### 1.6 对抗复核

每个域至少一次“故意找反例”：

- 构造一个应当失败/被拒的输入，确认门真的拒绝；
- 或对同一结论换一条独立路径复算；
- 反例尝试与结果写入 `docs/acceptance/verdicts/` 的 review JSON；
- 无反例尝试的域不得整体 `PASS`（GATE 域逐域检查）。

## 2. verdict 判据

| verdict | 何时使用 | 禁止 |
|---|---|---|
| `PASS` | 六个面均有当前证据，report exit 0，lint 无违规 | 用 summary/旧日志/UNVERIFIED 支撑 |
| `FAIL` | 有可复现的失败证据 | 把未跑到写成 FAIL |
| `BLOCKED` | 外部条件缺失（真实 Provider 密钥、生产签名、arm64、人工金标） | 把 BLOCKED 当 PASS |
| `UNVERIFIED` | 还没跑、只到设计/占位、或人类语感/审美不可自动判定 | 标成 PASS |

`UNVERIFIED` 与 `BLOCKED` 都必须给出 next step（runbook 或复跑命令）。空 verdict
不允许；台账 1200 行必须逐行有值。

## 3. 门禁与终裁

1. `python -I tools/ci/acceptance_init.py --check`：1200 行、ID、域计数无漂移。
2. `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md`：
   PASS 证据审计；exit 1 即阻断。
3. `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md`：
   反作弊；exit 1 即阻断。
4. `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md`：
   缺口清单；缺口未清零前不得宣布验收完成。
5. 单测门 + 强门 + 设备证据：Gradle 只在门禁阶段串行运行；设备证据绑定
   APK sha 与源快照。
6. 主会话终裁：签字字段、run-id、台账 sha256 三者齐全。

## 4. 拒绝伪造清单

- reviewer 不得给自己签字（`SELF_REVIEW`）；
- 证据不得引用 summary/批次记录（`SUMMARY_AS_EVIDENCE`）；
- 不得把 `-4`、`INSTRUMENTATION_STATUS_CODE: -4`、`AssumptionViolated`、
  `SKIP` 的轮次计为通过（`SKIPPED_GATE_AS_PASS`）；
- 不得把 `UNVERIFIED` 改写成 `PASS`（`UNVERIFIED_AS_PASS`）；
- 截图证据必须绑 `serial` 与 `apk_sha256`；
- 命令日志必须含可读 exit；
- 证据路径必须存在；不存在的路径不能“先记上”；
- 不得为让夹具变绿而放宽阈值或删除断言。

## 5. 验收替代的边界

- 自动化可替代：重复路径、边界、失败注入、像素差、语义树结构、证据审计。
- 自动化**不能**替代：屏幕阅读器语感、中文文案审美、真实用户理解成本、
  真实 Provider 生成质量、人工金标正确率。
- 上述不能替代项一律 `UNVERIFIED`，并在 `docs/acceptance/runbook.md` 写明
  残余与建议的人工抽检方式；不得由代理签字通过。

## 6. 与台账的映射

- 功能旅程 → A 域 90 行；
- 数据完整性 → B 域 90 行（72 迁移 + 18 备份/损坏）；
- 视觉 → C 域 90 行；
- 模型 → D 域 90 行；
- 复习算法 → E 域 90 行；
- 性能 → F 域 90 行；
- 安全 → G 域 90 行；
- 合规 → H 域 90 行；
- 无障碍 → I 域 90 行；
- 发布 → J 域 90 行；
- 修复 → FIX 100 行（20 区域 × 红/绿/回归/证据/登记）；
- 门禁 → GATE 100 行（10 域 × 10 检查）；
- D0 → 本文件与工具本身的 100 行。
