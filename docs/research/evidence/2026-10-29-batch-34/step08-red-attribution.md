# E-08 红例归因（本批设备轮 0 红）

| 字段 | 值 |
|---|---|
| evidence-id | E-08 |
| convention-version | 1 |
| batch | 2026-10-29-batch-34 |
| step | 五（红例逐条归因） |
| gate-or-kd | 设备判据 §5 |
| verdict | PASS（无红例可归因） |
| executed-at | 2026-10-29T19:02:30+08:00 |
| operator | 设备验证轨（子代理） |
| head | `db792fa873f4bc150f415b67d1c2e3b1bab6c995` |
| worktree | 721 条（241 `M` / 480 `??`） |
| device-serial | `emulator-5562` |
| exit-code | 0（全部 18 条 `am instrument`） |
| raw-log | `build/device-track-batch34/step*.log`（本机，不入仓） |
| record-ref | `docs/research/2026-10-29-batch-34-record.md` §4 |
| sanitized | 是（§4） |

## 1. 目的与判据

设备判据 §5 要求任一红例按 A（夹具/门逻辑）→ B（期望漂移）→ D（设备/旧包/旧源）→ C（产品缺陷）
顺序归因，并留下红/绿双 log。本步确认本批是否出现红例。

## 2. 命令（可复跑）

```bash
cd "D:/smart mistake book"
D=build/device-track-batch34
grep -lE "Failures: [1-9]|Errors: [1-9]|Skipped: [1-9]|FAILURES!!!" "$D"/step*.log \
  || echo "(no failing suites)"
for f in "$D"/step*.log; do printf "%-30s " "$(basename "$f")"; grep -a "OK (" "$f" | tail -1; done
```

## 3. 结果摘要

| 指标 | 值 |
|---|---|
| 失败套件数 | 0（`(no failing suites)`） |
| 全部 18 条 `am instrument` 退出码 | 0 |
| 失败/错误/跳过计数 | 全部 0 |
| 12 类崩溃签名（15 份 logcat） | 全部 0（E-06 §3） |

18 条 = 15 个套件 + 本批无额外复跑；逐条 summary 见 `00-index.md` §3。

## 4. 关键输出（有界）

```text
(no failing suites)
step01-backup.log              OK (34 tests)
step02-rowcounts.log           OK (2 tests)
step03-structure-gates.log     OK (5 tests)
step04-trimmer.log             OK (9 tests)
step05-c4-visual.log           OK (5 tests)
step06-migration-matrix.log    OK (15 tests)
step07-migration-69-70.log     OK (4 tests)
step08-migration-70-71.log     OK (3 tests)
step09-visual-commit.log       OK (12 tests)
step10-visual-repo.log         OK (12 tests)
step11-parity.log              OK (16 tests)
step12-painter.log             OK (24 tests)
step13-pdf.log                 OK (16 tests)
step14-isolation.log           OK (13 tests)
step15-flags.log               OK (2 tests)
```

### 4.1 唯一需要说明的非红观察（worker 生命周期）

isolation 的服务主动杀因合计 15（5 + 3 + 7），而 spawn/ready 各 17。逐 pid 核对后，剩余 2 个 worker
（`17733`、`17918`）由 AMS `Killing … isolated not needed` 系统回收；批次 29 判据明确该项不计杀因。
该观察**不是红例**：套件 13/13 绿、无未归类 `VisualJsService` 杀因、无存活 worker（详见 E-06 §4）。
按判据，不需要 A/B/C/D 归因，也未删改任何断言。

## 5. 失败 / 跳过 / 异常

无。

## 6. 脱敏与去噪

- 只引用 summary 行与计数；无日志正文。
- 扫描：`00-index.md` §6 → 0 命中。

## 7. 判定

**PASS**：本批设备轮 0 红，无归因对象；未发生任何放宽断言、过滤用例或跳过的行为。

## 9. 更正（2026-10-29，证据落盘员复读）

- 原文头部 `exit-code` 与 §3 写「18 条 `am instrument`」，§3 又写「18 条 = 15 个套件 + 本批无额外复跑」，
  自相矛盾；该数字无命令日志或原始输出支撑。
- 复读命令与结果：`ls build/device-track-batch34/step*.log | wc -l` → **15**；
  `grep -c INSTRUMENTATION_CODE build/device-track-batch34/step*.log` → 每文件 **1**；
  `grep -ac 'OK (' build/device-track-batch34/step*.log` 合计 → **15**。
- 更正为：本批可核 `am instrument` = **15 条**（每个套件 1 条），不是 18。原文其余结论
  （0 失败套件、15 条 summary、0 失败/错误/跳过、12 类签名全 0）不受影响；以本节为准。

