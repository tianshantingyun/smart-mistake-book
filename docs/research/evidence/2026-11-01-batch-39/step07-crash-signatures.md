# E-07 步骤四·崩溃签名与 worker 生命周期收口

| 字段 | 值 |
|---|---|
| evidence-id | E-07 |
| convention-version | 1 |
| batch | 2026-11-01-batch-39 |
| step | 步骤四·崩溃签名逐窗扫描 + 生命周期对账 |
| gate-or-kd | 步骤四（判据 §五红例归因总规则 + R4 §8.2） |
| verdict | **PASS（签名维度）**：27 个套件窗口 × 8 类签名全 0；红例均为断言红非崩溃；生命周期 kill 均为服务端 watchdog 设计杀或负载窗环境杀（E-03 §3 / E-06 归因） |
| executed-at | 2026-11-01T10:24–12:00+08:00（逐窗采集，末次扫描 11:5x） |
| operator | 设备验证轨（批次 39 工作流子代理） |
| head | `a0641bb7392f380b5716afe95dd3c371dd0c7670` |
| device-serial | emulator-5562 |
| exit-code | 扫描循环退出码 0 |
| raw-log | `build/device-track-batch39/suite-*-logcat.txt`（27 窗） |
| record-ref | `docs/research/2026-11-01-batch-39-record.md`（记录员落笔位） |
| sanitized | 是（00-index §6 统一扫描） |

## 1. 扫描命令（可复跑，逐窗）

```bash
cd build/device-track-batch39
for w in v01app nvec x02 x03 a10x a09 p09 p10 p0405 flags isolation backup rowcounts trimmer c4 \
         repo parity painter pdf ordinal reconciler engine a10 gates m6970 m7071 dbcommit; do
  f="suite-$w-logcat.txt"; [ -f "$f" ] || continue
  for s in "FATAL EXCEPTION" "ANR in " "NoSuchMethodError" "NoClassDefFoundError" \
           "UnsatisfiedLinkError" "CANNOT LINK" "failed to map segment" "SIGSEGV"; do
    printf '%s %s=%s\n' "$w" "$s" "$(grep -cF -- "$s" "$f" | tr -d '\r')"
  done
done
```

## 2. 结果（27 窗 × 8 类 = 216 项，全部为 0）

v01app/nvec/x02/x03/a10x/a09/p09/p10/p0405/flags/isolation/backup/rowcounts/trimmer/c4/repo/parity/painter/pdf/ordinal/reconciler/engine/a10/gates/m6970/m7071/dbcommit ——每窗八类逐项 **0**（含零命中的命令与计数，无"无异常"替代写法）。matrix 窗见 E-05（含卡死窗与复跑窗）。

## 3. worker 生命周期对账

- **设计杀**：P-10/P-04-05/隔离套件内 watchdog/重建用例的 `killing :visual_js worker: … accepted / watchdog hard deadline` 行均与各自用例断言一致（各套件绿轮为证）。
- **非设计杀（首跑红窗）**：isolation 窗 7 个 worker pid（16561/16812/16845/16867/16889/16909/16928）与 v01app 窗 1 个（15646）的 watchdog hard deadline 杀——全部对应断言红例的执行突刺，归因 D 环境（E-03 §3、E-02 §3）；无签名（§2 全 0），无 ANR，无 lmk 行进入证据窗口。
- **异常结束**：matrix 首跑窗在 v9 迭代处 I/O 饥饿卡死（D 状态 31+ 分钟无新库文件），经 force-stop 收口——见 E-05 §3，无崩溃签名。

## 4. 脱敏与去噪

- 本文件不含密钥/凭据/邮箱/物理序列号/用户目录；原始 logcat 全量在 `build/device-track-batch39/`。

## 9. 更正（2026-11-01T12:05+08:00，追加不改历史）

§3 中"matrix 首跑窗在 v9 迭代处 I/O 饥饿卡死（D 状态 31+ 分钟无新库文件）"**判定错误，现更正为**：首跑窗的 71 版本循环在正常推进（`migration-matrix-v39-306943495749427.db` mtime=设备时 03:47 = 宿主 11:47，晚于本轨各次 `ls | tail` 探针的 v8/v9 读数——`tail` 按**字母序**取尾，`v39` 排序在 `v9` 之前，"v9 卡死"是字母序探针错觉；线程 D 态为磁盘密集正常形态）。本轨在误判下于 11:52 对该窗 force-stop（操作误判，登记于此）；该窗无崩溃签名（§2 全 0 不受影响），首跑窗无判定，套件判定以复跑窗（E-05）为准。教训登记：进度探针必须按 mtime 排序或取最大版本号数值，不得用字母序 tail。

