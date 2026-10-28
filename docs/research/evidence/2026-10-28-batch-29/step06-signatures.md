# E-06 崩溃签名面：8 份 logcat × 9 类签名（PASS 全 0；worker 收口 FAIL 同隔离根因）

| 字段 | 值 |
|---|---|
| evidence-id | E-06 |
| convention-version | 1 |
| batch | 2026-10-28-batch-29 |
| step | 六（9 类崩溃签名对全部 logcat 计数；异常抓栈） |
| gate-or-kd | T1 §6.5；KD-74 签名门 |
| verdict | **签名 PASS（全 0）**；**worker 生命周期 FAIL**（spawn 14 / ready 0；杀因不可归因），与隔离 12 红同根因 R1 |
| executed-at | 2026-10-28T22:38:00+08:00 前后（设备 UTC 14:38） |
| operator | 设备验证轨（subagent，批次 29） |
| head | `3d9372e5` |
| worktree | dirty |
| device-serial | emulator-5562（test_device / API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | 见 00-index §2（三包设备侧 sha256 一致） |
| apk-version | 0.2.0-offline |
| exit-code | 扫描命令全部 exit 0（grep 无命中返回 1，见下） |
| raw-log | `build/device-track-batch29/step0*-logcat.txt`（8 份） |
| record-ref | 批次 29 记录（后写） |
| sanitized | 是 |

## 1. 命令（可复跑）

```bash
cd "D:/smart mistake book"
SIG='FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed'
for f in step01-painter step02-isolation step02-race step03-compiler step03-flags step04-pdf step04-parity step05-backup; do
  echo "$f => $(grep -cE "$SIG" build/device-track-batch29/$f-logcat.txt)"
done
# 隔离逐类
for s in "FATAL EXCEPTION" "ANR in " "NoSuchMethodError" "NoClassDefFoundError" \
         "UnsatisfiedLinkError" "SIGSEGV" "CANNOT LINK" "failed to map segment" "death was not confirmed"; do
  printf '%-24s %s\n' "$s" "$(grep -cF "$s" build/device-track-batch29/step02-isolation-logcat.txt)"
done
# 存活 worker
C:/Android/Sdk/platform-tools/adb.exe -s emulator-5562 shell "ps -A -o PID,NAME | grep visual_js" || echo "(none)"
```

## 2. 签名计数（8 份 logcat）

| logcat | 9 类签名 |
|---|---|
| step01-painter | 0 |
| step02-isolation | 0 |
| step02-race | 0 |
| step03-compiler | 0 |
| step03-flags | 0 |
| step04-pdf | 0 |
| step04-parity | 0 |
| step05-backup | 0 |

隔离逐类（T1 §6.4 九类）：FATAL 0 / ANR 0 / NoSuchMethod 0 / NoClassDef 0 / UnsatisfiedLink 0 /
SIGSEGV 0 / CANNOT LINK 0 / failed to map 0 / death not confirmed 0。

## 3. 异常抓栈（不属 9 类，但本步记录）

1. `VisualMeasurementEnvironmentException: measure paint flags 1283 … paintFlags=0`
   （isolation logcat 12 次；栈到 `VisualContentMeasuring.kt:460` / `:491` / `VisualJsService.kt:149`）。
   → R1，见 step01 §4、step02 §3.1。
2. `java.io.IOException: simulated write failure`（pdf logcat 230 次；栈
   `PdfDocument.nativeWriteTo` → `PdfDocument.writeTo`，被 native `ExceptionClear` 吞掉）。
   → R2，见 step04 §2.1。
3. 两处 JUnit `AssertionError`（painter :267、flags :40），已原样记录。

## 4. worker 生命周期（隔离 logcat）

| 指标 | 实测 |
|---|---|
| `Start proc .*:visual_js` | 14 |
| `warm-up finished` | **0** |
| `visual_js warm-up failed` | 12 |
| `killing :visual_js worker` | 13 |
| `hard deadline (1500 ms) for request` | 0 |
| `terminate(generation` | 0 |
| `the host unbound the visual_js worker` | 1 |
| `due to start instr` | 0 |
| 四类杀因之和 | **1 ≠ 14**（13 个杀因不可归入四类：12 warm-up fail + 1 AMS `isolated not needed`） |
| 存活 worker | 空（PASS） |

- Race 面：`Start proc .*:visual_js` = 0（符合 fake binder 判据）。
- worker 未出现 FATAL/SIGSEGV：service 在 warm-up 异常后主动 `Process.killProcess`（SIGKILL），
  所以 9 类签名仍为 0；但"零意外杀/全归因"判据不成立。

## 5. 判定

**签名面 PASS（8×0）**；**worker 收口 FAIL**，归因同 R1（本批 T5 产品缺陷），
不是环境崩溃、不是 JNI/装载问题。**未**为过门而缩小签名清单或改动扫描范围。
