# E-04 warm-up 装载成本实测（KD-76 缺口的实测半边）

| 字段 | 值 |
|---|---|
| evidence-id | E-04 |
| convention-version | 1 |
| batch | 2026-10-27-batch-27 |
| step | 四（warm-up 装载成本实测，多样本 p50/p95） |
| gate-or-kd | KD-76（`docs/known-defects.md:2819`，缺口"warm-up 实测装载成本/收益归批次 27 设备轨"）；P-07 承接 |
| verdict | **出数**：控制组 n=10 p50=686 ms / p95=725 ms / max 738 ms；套件 churn 组 n=16 中位 675.5 ms（churn 尾 1259–1746 ms）。16+10 全部到达 ready。arm64/真机/低内存/无 warm-up 对照 UNVERIFIED（§6） |
| executed-at | 2026-10-28T03:14:00+08:00（套件轮，n=16 来源）～03:41:00+08:00（控制组，n=10） |
| operator | 设备验证轨（subagent，批次 27） |
| head | caa0726a02f1862371a3b2770c8c29663ba7fa02 |
| worktree | dirty（他线在飞） |
| device-serial | emulator-5554（沉降态；套件轮与其后采样轮之间无 reboot） |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | debug 主包 + debug androidTest 包（43adac58…/cde7e27b…） |
| apk-sha256 | 见 step00 §4（设备侧一致） |
| apk-version | 0.2.0-offline |
| exit-code | 0（采样轮 10 次 instrument 全部 `OK (1 test)`） |
| raw-log | `build/device-track-batch27/`：`warmup2-logcat.txt`、`warmup2-sample-{1..10}.log`、`step01-isolation-logcat.txt` |
| record-ref | 批次 27 记录（由记录员后写） |
| sanitized | 是（§7） |

## 1. 口径定义

**装载耗时 := AMS `Start proc <pid>:…:visual_js…` 行时刻 → 同 pid `visual_js warm-up
finished` 行时刻**（logcat threadtime，毫秒分辨率；设备 UTC 时钟）。内容 = 进程 fork/attach +
`QuickJs.create`（libquickjs.so 装载 + 空程序 evaluate）+ 内容度量器构造与字体探针
（`VisualJsService.kt:141-159`）+ ready 置位。即"ready 前装载耗时"的完整观测口径（宿主
`isReady` 轮询的服务端事实源）。**不含**：宿主 bind/握手排队（未测，无客户端侧计时 seam）。

配对方法：每 worker spawn 唯一 pid，`Start proc` 行从消息体取 worker pid，`warm-up finished`
行取 logcat 前缀 pid，按 pid 配对（Python，`datetime` 差值）。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# 控制组：单用例 ×10 次（每次恰好 1 个新 worker），一次 logcat -c、结束后一次 dump
$A -s emulator-5554 logcat -c
for i in 1..10; do
  $A -s emulator-5554 shell am instrument -w -r -e class \
    com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight \
    com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
    > build/device-track-batch27/warmup2-sample-$i.log
done
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch27/warmup2-logcat.txt
# 配对/分位（Python，见 §1 配对方法；p 线性插值法）
```

选 `aSecondConcurrent…` 做采样载体的原因：当前该用例**绿**（BUSY 断言在 compile 门之前）、
每次运行恰好 spawn 1 个 worker、含 watchdog 硬杀（顺带复核 kill 路径）。

## 3. 结果摘要

### 3.1 控制组（沉降态，n=10，2026-10-28T03:36–03:41+08:00）

| 统计 | ms |
|---|---|
| 样本（升序） | 652, 655, 678, 678, 680, 692, 694, 703, 709, 738 |
| min / max | 652 / 738 |
| **p50** | **686** |
| **p95** | **725** |
| mean | 688 |

10/10 worker 到达 ready；10 次 instrument 全部 `OK (1 test)`（采样载体用例绿，红例隔离）。

### 3.2 套件 churn 组（n=16，来源 = E-05 隔离套件全量轮 logcat）

```text
升序 ms：582, 606, 611, 627, 630, 633, 670, 675, 676, 677, 680, 689, 706, 1259, 1312, 1746
中位 = 675.5；13/16 ≤ 706；churn 尾部 3 样本（1259/1312/1746）落在挂死注入用例
（watchdog 自旋占核）同窗——负控设计内负载，非环境故障
```

### 3.3 与判据的关系

- KD-76 ③ 的"冷态 warm-up(~1.9 s)"（批次 26 E-03，负载 0.75）与本批控制组 686 ms 同量级
  观测面、不同负载时点，不互相替代。
- **收益半边未测**：无 warm-up 的对照（1500 ms 窗口内的冷装载首请求）不可在现码上构造
  （warm-up 已是结构性前置）——"收益"只能由批次 23 E-01d 的历史红例（冷启动首请求被
  1500 ms 误杀）反证，本轮无新对照数据，如实登记。
- **决策含义（供主会话）**：宿主 `READY_TIMEOUT_MILLIS=10 s`（KD-76 ⑤ 客户端 ready 预算）
  对实测 p95 725 ms 的余量 ≈ 14×；churn 尾部 1746 ms 余量 ≈ 5.7×——设备校准缺口
  （READY_TIMEOUT=10 s 是否放宽/收紧）当前无数据压力，但仅 x86_64 模拟器口径。

## 4. 关键输出（有界）

```text
# 控制组样本行样例（UTC）
10-27 19:36:12.314 25481 I ActivityManager: Start proc 25481:…:visual_js…/u0iNN for service {…}
10-27 19:36:13.0xx 25481 I VisualJsService: visual_js warm-up finished; worker ready for requests
# （服务端不打印时长；耗时由两行时间戳差得出，未改产品日志面）
```

## 5. 失败 / 跳过 / 异常

第一轮采样流程缺陷（每轮 `logcat -c` 只留末轮日志）作废重跑（第二轮修为单次 clear+末尾
dump，按 pid 配对）；作废轮的 instrument 输出仍在 `build/`（`warmup-sample-{1..10}.log`），
未入证据。

## 6. 缺口与边界（如实）

1. x86_64 / API 34 模拟器口径；**arm64、真机、低内存/高负载条件 UNVERIFIED**（沿 R-01/
   R-02/KD-76 既有缺口）。
2. 收益半边无新对照数据（§3.3）。
3. 29.5 s worst-case 链、产品进程/UI 首请求面 UNVERIFIED（批次 26 E-03 既有缺口，本步不覆盖）。
4. 宿主侧 bind→ready 的客户端可感延迟未测（无计时 seam；服务端口径见 §1）。

## 7. 脱敏与去噪

- 删除：Gradle/安装进度行（无）；logcat 全量留 `build/`。
- 保留：全部样本值、统计量、配对口径、作废轮登记。
- 扫描：见 `00-index.md` §6。二进制/截图：无。

## 8. 判定

**PASS（出数）**：KD-76 的"warm-up 实测装载成本"缺口获得首批多样本实测（控制组 p50=686 /
p95=725 ms，churn 组中位 675.5 ms）；16+10 worker 100% 到达 ready。该缺口是否随本批关闭
由主会话/记录员按 §6 边界裁定；KD-76 本体维持 open（JS 不广告不变）。
