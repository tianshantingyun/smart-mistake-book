# E-12 9 类崩溃签名扫描（13 份 logcat 全 0）

| 字段 | 值 |
|---|---|
| evidence-id | E-12 |
| convention-version | 1 |
| batch | 2026-10-29-batch-32 |
| step | 五（判据 §7.6） |
| gate-or-kd | 全部设备结论的崩溃面 |
| verdict | PASS（13 份 logcat × 9 类签名 = 0） |
| executed-at | 2026-10-29T11:30:15+08:00（随各步采集后统一扫描） |
| operator | 设备验证轨（subagent，批次 32） |
| head | `0b4bdbbe` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | 四包（见 E-01 §2） |
| apk-sha256 | 见 E-01 §2 |
| apk-version | `0.2.0-offline`（app） |
| exit-code | 扫描命令收尾 `EXIT=0`；单条 `grep -c` 零命中时自身返回 1（已用 `|| true` 思路记录，不是命令失败） |
| raw-log | `build/device-track-batch32/step{01..13}-*-logcat.txt`（13 份） |
| record-ref | `docs/research/2026-10-29-batch-32-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

13 份 logcat 的 9 类崩溃/装载签名必须全部 0：
`FATAL EXCEPTION`、`ANR in `、`NoSuchMethodError`、`NoClassDefFoundError`、`UnsatisfiedLinkError`、
`SIGSEGV`、`CANNOT LINK`、`failed to map segment`、`death was not confirmed`。

## 2. 命令（可复跑）

```bash
for f in build/device-track-batch32/*-logcat.txt; do
  printf "%s: " "$(basename "$f")"
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed" "$f" || true
done
```

## 3. 结果摘要

| logcat | 命中 |
|---|---|
| `step01-backup-logcat.txt` | 0 |
| `step02-migration-69-70-logcat.txt` | 0 |
| `step03-migration-matrix-logcat.txt` | 0 |
| `step04-migration-70-71-logcat.txt` | 0 |
| `step05-structure-gates-logcat.txt` | 0 |
| `step06-visual-commit-logcat.txt` | 0 |
| `step07-visual-repo-logcat.txt` | 0 |
| `step08-parity-logcat.txt` | 0 |
| `step09-trimmer-logcat.txt` | 0 |
| `step10-painter-logcat.txt` | 0 |
| `step11-pdf-logcat.txt` | 0 |
| `step12-isolation-logcat.txt` | 0 |
| `step13-flags-logcat.txt` | 0 |

## 4. 关键输出（有界）

```text
step01-backup-logcat.txt: 0
...
step13-flags-logcat.txt: 0
```

## 5. 失败 / 跳过 / 异常

无。签名全 0 不改变 E-07 的 FAIL 判定——该红是断言失败，不是崩溃。

## 6. 脱敏与去噪

- 删除：无。
- 保留：全部计数（含 0）。
- 扫描：R4 §4.4 → 0 hits。
- 二进制/截图：无。

## 7. 判定

PASS：13 份 logcat 的 9 类签名全 0。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch32/step{01..13}-*-logcat.txt`（13 份；最小 95 行/13,670 B，最大 20,576 行/2,712,089 B）
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
