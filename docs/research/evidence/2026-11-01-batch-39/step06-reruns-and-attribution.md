# E-06 步骤四·红例逐条归因（复跑轮 + 判别轮）

| 字段 | 值 |
|---|---|
| evidence-id | E-06 |
| convention-version | 1 |
| batch | 2026-11-01-batch-39 |
| step | 步骤四·红例归因（isolation 三窗 + v01app/a09/x03 复跑判别） |
| gate-or-kd | 步骤四（红例归因序 A→B→D→C；判据 §五） |
| verdict | **PARTIAL**：v01app 4/4 绿（复跑轮取代首跑红，D 归因成立）；a09 复跑绿（会话内服务路径判别）；x03 两窗逐字节同红（A 类夹具缺陷确定性坐实）；**isolation 三窗 6/7/5 红、复跑未绿 → FAIL 未达不回退门，归因 D 主导 + T4 关联本轨不可证伪，阻塞上报主会话** |
| executed-at | 2026-11-01T13:37–13:55+08:00（复跑/判别轮；isolation 三窗 10:27 / 13:41 / 13:50） |
| operator | 设备验证轨（批次 39 工作流子代理） |
| head | `a0641bb7392f380b5716afe95dd3c371dd0c7670` |
| worktree | 737 条（仅记录） |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | app androidTest `e20d923a…`（三轮同包）+ data androidTest `5b8ba39c…`（x03） |
| exit-code | 各轮 adb 层 0 |
| raw-log | `build/device-track-batch39/suite-{isolation,isolation-rerun,isolation-rerun2,v01app-rerun,a09-rerun,x03-rerun}.log` + 同名 `-logcat.txt` |
| record-ref | `docs/research/2026-11-01-batch-39-record.md`（记录员落笔位） |
| sanitized | 是（00-index §6 统一扫描） |

## 1. 判定总表

| 套件 | 首跑 | 复跑 | 判定 |
|---|---|---|---|
| `V01VisualChainInstrumentedTest` | 3/4（③ binder died） | **4/4 OK**（同 APK 同命令全量复跑） | **PASS（以复跑轮为准）**；首跑红 D 归因成立（判据批 38 E-06 同型：执行时长突刺） |
| `VisualJsAdvertiseThenExecuteInstrumentedTest` | OK (3) | **OK (3)** | PASS——本会话服务路径在轻重两负载下均绿（判别位） |
| `StrictOfflineRelayoutDeviceVectorInstrumentedTest` | 0/1（A 类夹具） | 0/1（**逐字节同红**：expected `visual:operation-x03-relayout-chain:818cfa45f2e123bc` / actual `visual-program-2760ca…22b46`） | **FAIL（A 类夹具缺陷，确定性）**——修复归落码批，修复后按"新增用例必须重跑"复跑；本轨不改测试（E-02 §5 源码归因链不变） |
| `VisualJsIsolationInstrumentedTest` | 9/15 | 8/15 | 第三轮（沉降对照 loadavg 0.66→窗内 3.34）**10/15** | **FAIL（三窗未绿，未达不回退门）**——§2 |

## 2. isolation 三窗归因（D 主导；T4 关联登记为本轨不可证伪）

**红形事实（三窗同根）**：
- 每窗红例集合不同（高方差）：窗1 6 红 / 窗2 7 红（含 KD-66 门例） / 窗3 5 红；三窗红例并集 10 个不同方法，重叠仅 3——无稳定复现子集，非确定性缺陷形。
- 红形单一：全部为"帧执行超 1500 ms 服务硬期限 → 服务端 watchdog 杀 worker → 宿主见 binder died/HARD_DEADLINE_EXCEEDED/status=-3（binder 层错误，非协议码——`VisualJsProtocol.kt:35-39` 协议面为 0..4）→ 同测试内后续断言级联"。
- watchdog kill 计数：窗1=10、窗2=10、窗3=**6**（批 38 E-06 判例：全绿轮设计基数=5）。窗3 沉降后（窗前 loadavg 0.66）kill 回落至基数+1，但窗内仍攀升（窗后 3.34）——本轨隔离套件自身 ~19 次 worker spawn/窗即为主要负载源，另加宿主 4 模拟器他线在飞。
- 同会话反例（非全局劣化）：a09 两窗绿、p09/p10/p0405 绿、v01app 两窗绿（第二窗 4/4）、QuickJs 引擎 27 例绿、data/database 全部 199 例绿——失败聚集于隔离套件自身的重 churn 形状（watchdog 注入/死亡重建/冷启动 7.6 s）叠加负载。

**D 支持**：①方差形+负载相关性（kill 计数与负载同落）；②红形全部为时长突刺家族、签名 0；③同树其余 24+ 套件 199+ 例全绿，引擎本体（QuickJs 27）绿；④批 38 同套件同设备同形红（2 例）被复跑绿取代——本套件对负载敏感是既有判例。

**T4 关联（B 他线在飞）——本轨不可证伪，如实登记**：引擎文件 `VisualJsClientEngine.kt` 携带未提交 T4 计时缝 diff（+156，特征化实读）。机制侧反证：kill 由**服务进程**内 watchdog 发出（`VisualJsService.kt` 侧 `killing :visual_js worker: watchdog hard deadline`），T4 只改宿主文件、只增 6 处 volatile 打点，不可能拖慢服务端 JS 执行；失败形为服务端时长超限，非宿主提交延迟。但本轨禁改他线在飞文件、禁 git 写，**无法做"去 T4 控制轮"**，故 T4 因果只能由主会话裁定（可选项：主会话授权控制窗 / 接受 D 归因 / 推迟重跑）。

**上报主会话的裁定请求**：isolation 15/15 绿是 R2 §7.6"seam 落码批设备绿绑定其快照"的前提；本轨三轮未取得，T4 缝的设备绿绑定**当前不成立**。建议：a) 低负载窗（他线套件收口后）重跑一轮；b) 或授权 T4-reverted 控制轮；c) 或按 D 归因接受并登记风险。

## 3. 复跑轮命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class com.tingyun.smartmistakebook.visualjs.<套件名> \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner   # 全量复跑，非红例过滤
$A -s $S shell logcat -v threadtime -d
# 沉降对照：第三轮前设备空闲 ≥9 min（loadavg 3.2–4.0 → 0.66 实测后起跑）
```

## 4. 脱敏与去噪

- 本文件不含密钥/凭据/邮箱/物理序列号/用户目录；原始全量在 `build/device-track-batch39/`。
