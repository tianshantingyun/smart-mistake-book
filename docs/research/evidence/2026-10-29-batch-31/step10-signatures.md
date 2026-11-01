# E-10 崩溃签名 / 装配级 logcat / worker 生命周期汇总（12 份 logcat 全 0 命中）

| 字段 | 值 |
|---|---|
| evidence-id | E-10 |
| convention-version | 1 |
| batch | 2026-10-29-batch-31 |
| step | 四（判据 §5.1–§5.4） |
| gate-or-kd | 全部设备结论的崩溃面；KD-78 ④-L1/L2 |
| verdict | **PASS**：12 份 logcat 的 9 类签名全部 0；装配级恒抛 0；PDF native 异常 230 > 0；无残留 worker |
| executed-at | 2026-10-29T06:31–06:43+0800（随 E-01…E-09 同步采集） |
| operator | 设备验证轨（subagent，批次 31） |
| head | `59d4a03f` |
| worktree | dirty（本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | 四包（E-00 §3） |
| apk-sha256 | 见 E-00 §3 |
| apk-version | `0.2.0-offline`（app）/ 测试包空 |
| exit-code | 扫描命令 `EXIT=0`（grep 计数收尾；0 命中时 `grep -c` 自身返回 1，已用 `|| true` 记录） |
| raw-log | `build/device-track-batch31/step{01..12}-*-logcat.txt`（12 份） |
| record-ref | `docs/research/2026-10-29-batch-31-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

判据 §5.1：八套件（本批扩到 12 份 logcat）的 9 类崩溃签名全 0。§5.2：备份无
`BackupSnapshotTrimException` / `expected:<69>`，69→70 无 `ComparisonFailure`。§5.3：隔离 worker
spawn↔ready 1:1、kill 四类 = spawn、无残留。§5.4：三份 logcat 的 paint flags 装配级恒抛 0；PDF
native `java.io.IOException: simulated write failure` > 0。

## 2. 命令（可复跑）

```bash
cd 仓库根目录
for f in step01-backup-logcat.txt step02-migration-69-70-logcat.txt \
         step03-migration-matrix-logcat.txt step04-painter-logcat.txt \
         step05-isolation-logcat.txt step06-flags-logcat.txt \
         step07-pdf-logcat.txt step08-parity-logcat.txt \
         step09-visual-commit-logcat.txt step10-visual-repo-logcat.txt \
         step11-structure-gates-logcat.txt step12-trimmer-logcat.txt; do
  printf "%-40s " "$f"
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed" \
    "build/device-track-batch31/$f" || true
done

grep -c "BackupSnapshotTrimException" build/device-track-batch31/step01-backup.log
grep -c "expected:<69>"               build/device-track-batch31/step01-backup.log
grep -c "ComparisonFailure"           build/device-track-batch31/step02-migration-69-70.log
grep -c "java.io.IOException: simulated write failure" build/device-track-batch31/step07-pdf-logcat.txt
for f in step05-isolation-logcat.txt step06-flags-logcat.txt step04-painter-logcat.txt; do
  printf "%s=" "$f"
  grep -c "measure paint flags .* do not match the environment fingerprint" \
    "build/device-track-batch31/$f" || true
done
```

## 3. 结果：9 类签名（12 份 logcat）

| logcat | 命中 |
|---|---|
| `step01-backup-logcat.txt` | 0 |
| `step02-migration-69-70-logcat.txt` | 0 |
| `step03-migration-matrix-logcat.txt` | 0 |
| `step04-painter-logcat.txt` | 0 |
| `step05-isolation-logcat.txt` | 0 |
| `step06-flags-logcat.txt` | 0 |
| `step07-pdf-logcat.txt` | 0 |
| `step08-parity-logcat.txt` | 0 |
| `step09-visual-commit-logcat.txt` | 0 |
| `step10-visual-repo-logcat.txt` | 0 |
| `step11-structure-gates-logcat.txt` | 0 |
| `step12-trimmer-logcat.txt` | 0 |

## 4. 专项判据

| 判据 | 命令 | 实测 | 判定 |
|---|---|---|---|
| 备份裁剪器 fail | `grep -c BackupSnapshotTrimException step01-backup.log` | 0 | PASS |
| 备份期望漂移 | `grep -c "expected:<69>" step01-backup.log` | 0 | PASS |
| 69→70 DDL 文本断言 | `grep -c ComparisonFailure step02-…log` | 0 | PASS |
| PDF native 写失败 | `grep -c "java.io.IOException: simulated write failure"` | **230** | PASS（> 0，证明 fail-open 路径被走到） |
| 装配级 paint flags 恒抛 | 三份 logcat 各 `grep -c` | 0 / 0 / 0 | PASS |
| worker spawn / ready | `grep -c` | 17 / 17（pid 集合 `diff` 空） | PASS |
| `warm-up failed` | `grep -c` | 0 | PASS |
| kill 四类 | 5 + 4 + 7 + 1 | 17 = spawn | PASS |
| 事后残留 | `ps -A -o PID,NAME \| grep visual_js` | 空 | PASS |

## 5. 失败 / 跳过 / 异常

无崩溃、无 ANR、无链接/装载错误、无 `death was not confirmed`。
**说明**：`grep -c` 在 0 命中时退出码为 1，本文件的"0"是计数结果，不是命令失败；
已用 `|| true` 避免中断循环。

## 6. 脱敏与去噪

- 删除：无（12 份 logcat 全量留在 `build/`，本轮只落计数）。
- 保留：全部签名计数（含 0）、native 异常计数、生命周期计数。
- 扫描：R4 §4.4 → 0 命中。
- 二进制/截图：无。

## 7. 判定

PASS：十二份 logcat 的九类签名全 0；专项计数全部符合判据（含 PDF 的正向 > 0 要求）。
**注意**：签名全 0 不改变 E-08 的 FAIL 判定——parity 的红是断言失败，不是崩溃。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch31/step{01..12}-*-logcat.txt`
- 行数/大小：最小 86 行（repo）/ 最大 20576 行（PDF，2712169 B）
- 说明：`build/` 被 `.gitignore:15` 忽略。
