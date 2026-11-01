# E-02 KD-66 关闭门（hard+500 ms 设备用例）——三次独立运行全绿

| 字段 | 值 |
|---|---|
| evidence-id | E-02 |
| convention-version | 1 |
| batch | 2026-10-27-batch-26 |
| step | 二（KD-66 hard+500 ms 用例真跑） |
| gate-or-kd | KD-66（关闭门：issue #240 形状在 hard+500 ms 内返回拒绝、且后续请求可用） |
| verdict | **PASS（3 次独立运行全绿；R1 §4.5"两轮独立绿"达成，关闭判定归主会话/记录员）** |
| executed-at | 2026-10-27T07:19:00+08:00（第 1 轮）/ 07:21:00+08:00（第 2/3 轮） |
| operator | 设备验证轨（subagent，批次 26） |
| head | 405c83739cfa5385aa7652ef61f3488f953c66ce |
| worktree | dirty（他线在飞） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | 同 E-01（`758c0caf…` / `cde7e27b…`；设备侧哈希一致） |
| exit-code | 0（adb 进程退出码；判据是 summary） |
| raw-log | `build/device-track-batch26/step01-isolation.log`（第 1 轮，套件内）、`step02-kd66-run2.log`（23 行）、`step02-kd66-run3.log`（23 行）、`step02-kd66-run2-logcat.txt`（724 行）、`step02-kd66-run3-logcat.txt`（709 行） |
| record-ref | 批次 26 记录（由记录员后写） |
| sanitized | 是（§6；扫描见 00-index §6） |

## 1. 目的与判据

任务书步骤二：T1 落码的 KD-66 hard+500 ms 关闭门用例真跑；**绿则 KD-66 达关闭门（记录回链后
关闭）；红则如实记录**。

- 用例：`VisualJsIsolationInstrumentedTest.aHungWorkerReleasesTheCallerWithinHardPlus500ms`
  （`VisualJsIsolationInstrumentedTest.kt:373`，批次 26 T1 落码、设备执行归本轨）。
- 关闭判据（`docs/known-defects.md:2615` T1 Progress 实读 + R1 `docs/research/2026-10-27-kd66-hard-500ms-design.md:128`）：
  用例绿 + **两轮独立运行均绿**（单轮绿不作为关闭依据）+ known-defects 记录回链。
- 断言集（源码实读 `:373-435`，本轨未改动）：A1 有界拒绝（`Rejected`）；A2
  `HARD_DEADLINE_EXCEEDED`；A4 诊断含 "died"（归因 worker 死亡路径、先于计时，排除宿主
  2500 ms 超时兜底）；A5 `elapsed ≤ HARD_PLUS_500_GATE_MILLIS`（`:415`；常量
  `SERVICE_HARD_DEADLINE_MILLIS + 500L` `:776-777`，500 ms 为门自带余量、不追加容差）；
  A6 死亡后重建；A7 `connectionCountForTest() == 2` 且新程序 `Rendered`。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# 第 1 轮 = E-01 套件轮（同一次 am instrument 内执行，见 step01 §2 命令）
# 第 2/3 轮 = 单用例聚焦运行（每轮前 logcat -c）
CLS='com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#aHungWorkerReleasesTheCallerWithinHardPlus500ms'
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r -e class "$CLS" \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch26/step02-kd66-run2-logcat.txt
# 第 3 轮同上 → step02-kd66-run3.log / step02-kd66-run3-logcat.txt
```

三轮均在沉降态设备（第 1 轮 load 0.03；第 2/3 轮同机连续执行）。**未重试、未放宽**：每轮
独立 `am instrument` 进程；用例内无重试（R1 §4.5 重试策略第 1 层）；门常量未动。

## 3. 结果摘要

| 轮次 | 形态 | summary | 耗时 | logcat `hard deadline (1500 ms)` 行 | 判定 |
|---|---|---|---|---|---|
| 1 | 套件内（13 例之一） | `OK (13 tests)` | 59.33 s（全类） | 1（请求 `747412e7-…`） | **绿** |
| 2 | 单用例聚焦 | `OK (1 test)` | 3.142 s | 1 | **绿** |
| 3 | 单用例聚焦 | `OK (1 test)` | 3.081 s | 1 | **绿** |

A5 计时断言（`elapsed ≤ SERVICE_HARD_DEADLINE_MILLIS + 500 = 2000 ms`）三轮均通过——
断言消息只在失败时打印，故无 elapsed 数值输出；**A5 通过本身即"宿主在 hard+500 ms 内返回"
的证据**。logcat 侧服务端锚点（第 1 轮，`step01-isolation-logcat.txt:697`）：warm-up finished
37.620 → hard deadline kill 39.419（挂死请求提交后 ~1500 ms，watchdog 按时杀）；死亡后 32 ms
重建（`:709`），新进程 40.163 warm-up finished，40.331 用例绿（A6/A7 通过）。

聚焦轮锚点（`step02-kd66-run2-logcat.txt:619/666-667/678/699`）：warm-up finished 21:19.115 →
kill 21:20.779（请求 `71ec7659-…`）→ 32 ms 后重建（pid 7209）→ 21:21.421 新进程 ready；run3 同构
（kill 请求 `18a15171-…`）。**三轮 kill 行请求 ID 各不相同、每轮恰好 1 行**——与用例"单次注入"
结构一致，无隐藏重试。

## 4. 关键输出（有界）

```text
# run2 尾部
Time: 3.142
OK (1 test)
INSTRUMENTATION_CODE: -1

# run3 尾部
Time: 3.081
OK (1 test)
INSTRUMENTATION_CODE: -1
```

## 5. 失败 / 跳过 / 异常

无。三轮零失败、零跳过、零崩溃签名；R1 §4.5 第 3 层归因树未触发（无需区分"按时杀迟返回/
迟杀/无 kill 行"）。

## 6. KD-66 判定位置（本轨边界，如实）

- **设备侧关闭门已达成**：用例绿 ×3 独立运行 ≥ R1 §4.5 要求的两轮独立绿；断言集 A1–A7 完整
  执行（源码 `:391-434`），无一放宽。
- **关闭动作不在本轨**：KD-66 状态改写（open→closed）+ 记录回链归记录员/主会话（与批次 25
  9.2 同口径："本节只给判据对照，不代写 KD 状态"）。本轨不改 `docs/known-defects.md`。
- 附带边界（如实）：#240 上游状态未变（T1 条目实读，仍 OPEN）；本门证明的是**进程层防线**
  （watchdog 硬杀 + 调用方有界返回 + 后续可用），不是上游修复。

## 7. 脱敏与去噪

- 保留：summary、耗时、kill 行时间轴与请求 ID、产物哈希引用。
- 扫描：见 `00-index.md` §6。二进制/截图：无。

## 8. 判定

**PASS（KD-66 设备侧关闭门达成）**：`aHungWorkerReleasesTheCallerWithinHardPlus500ms` 三次
独立运行全绿（第 1 轮套件内 + 第 2/3 轮聚焦），R1 §4.5 两轮独立绿判据满足。**建议主会话/
记录员：KD-66 可关闭，回链本 E-file（E-01 套件轮 + E-02 三轮表）**；JS 广告纪律不变（不因
KD-66 单点关闭而改变）。

## 9. 原始全量（不入仓）

- 路径：`build/device-track-batch26/step02-kd66-run2.log`（23 行）、`step02-kd66-run3.log`（23 行）、
  `step02-kd66-run2-logcat.txt`（724 行）、`step02-kd66-run3-logcat.txt`（709 行）；第 1 轮原始
  日志见 E-01 §8
- 生成命令：见 §2。`build/` 被 `.gitignore` 忽略；关闭判据只认本文件。
