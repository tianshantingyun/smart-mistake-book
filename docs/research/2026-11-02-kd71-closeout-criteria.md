# KD-71 设备核销判据（批次 40 / WS6 item 36，2026-11-02）

> 输入：R8 `docs/research/2026-11-02-kd71-race-review.md`（全读）。本文把 R8 §5 的 G1–G6 落成**可直接执行、可判红**的核销清单，并记录本轮能核与不能核的边界。
> **零 Gradle、零设备、零 git 写**：本轮不产出新的设备证据，因此 **KD-71 不关闭**；关闭动作只能由批次 40 设备轨按本文 G1–G6 执行后裁定。

## 0. 结论先行

| 判据 | 本轮状态 | 说明 |
|---|---|---|
| G1 源绑定 | ✅ **现树三文件与批次 39 快照逐字节一致** | 本轮实跑 sha256，见 §1 |
| G2 套件与逐例 | ⛔ 未执行 | 需 Gradle 构建 + 设备 `am instrument` |
| G3 环境与崩溃签名 | ⛔ 未执行 | 依赖 G2 的同一轮 logcat |
| G4 变异/回退控制 | ⛔ 未执行（不阻塞关闭） | JVM 侧即可；R8 视为"强烈建议" |
| G5 关闭后守恒 | ⛔ 未执行 | 已含在 G2 套件内 |
| G6 证据落盘 | ⛔ 未产出 | 模板见 §4 |

**裁定规则**：G1–G3 + G5 全绿且 G6 落盘 → 可写 "KD-71 closed（批次 40 设备轨）"；G1–G3 任一红或缺失 → 保持 open，不得以批次 39 旧绿代替。G4 未做不阻塞，但关闭文案必须写"红向敏感性未实证"。

## 1. G1 源绑定（本轮已核）

```bash
sha256sum \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngineInstrumentedTest.kt \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngineJvmTest.kt
```

本轮输出（2026-11-02，HEAD `a3f367df`）：

```text
7e6135be8e5d24155da3a549f9483027c799e767926b209a5e2bf1b6311a08b5  QuickJsVisualExecutionEngine.kt
6597f20f5ea3721b8379911c42e991262372274e56717ef18af0ca4462bf5022  QuickJsVisualExecutionEngineInstrumentedTest.kt
8b23975f171003f0ac7dedef19de27afa284b4178b54deaa993f4d0968e822e5  QuickJsVisualExecutionEngineJvmTest.kt
```

与 `build/device-track-batch39/src-sha-before.txt:356,212,581` 及 `src-sha-final.txt` 三值一致；`build/device-track-batch39/src-snapshot.diff` 为 **0 行**（`wc -l` 实跑）。因此批次 39 E-04 的 27 例绿仍绑定现树字节。

**但 G1 只证明"旧证据未失效"，不证明"批次 40 已复跑"**。设备轨仍须在开窗前重录 `src-sha-before.txt`、闭窗后录 `src-sha-final.txt`，并确认 `git status --porcelain -- <三文件>` 为空、`src-snapshot.diff` 为空。

## 2. G2 套件与逐例（设备轨执行）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe
S=<serial>
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch40/kd71-engine.log
```

判定（全部满足才算绿）：

1. 末尾 `OK (N tests)`，且 `N == grep -c '@Test' <InstrumentedTest.kt>`。现树实跑 `grep -c '@Test'` = **27**。
2. `INSTRUMENTATION_STATUS_CODE: 0` 计数 == 27；`INSTRUMENTATION_STATUS_CODE: -` 计数 == 0。
3. 两条 KD-71 用例逐条可见且 `STATUS_CODE 0`：
   - `closeDuringAnInFlightTransactionWaitsAndRejectsIt`
   - `concurrentCloseAndExecuteRoundsNeverReportAnUnexpectedOutcome`（32 轮）
4. 不得用 `connectedAndroidTest` 合并口径代替；不得放宽"execute 必须 `ENGINE_CLOSED`"与"close 后不得再执行"的断言。仅允许观察窗口常量（现 200 ms）随设备变慢调整。
5. 同一 APK 连跑 **3 次**，3 次全绿才写"稳定绿"；只跑 1 次写"单轮绿"。

## 3. G3 环境与崩溃签名（同轮采集）

记录：`serial / AVD / API / ABI / page-size`、core:data androidTest APK `sha256`、`am instrument` 退出码、整轮 logcat。

```bash
$A -s "$S" logcat -v threadtime -d > build/device-track-batch40/kd71-engine-logcat.txt
grep -nE "FATAL EXCEPTION|ANR in com\.tingyun" build/device-track-batch40/kd71-engine-logcat.txt
```

期望：**0 条**。`am instrument` 命令进程自身的 `AndroidRuntime` 启动/退出行不算被测应用签名（批次 39 实测 5 条均属命令进程，R8 §3.1）。

## 4. G6 证据落盘模板

文件：`docs/research/evidence/2026-11-02-batch-40/stepXX-kd71-engine.md`

```markdown
# E-XX · KD-71 引擎套件设备复跑

| 字段 | 值 |
|---|---|
| evidence-id | E-XX |
| verdict | PASS / FAIL |
| executed-at | |
| head | |
| worktree | `git status --porcelain -- <三文件>` 为空 |
| device-serial / avd / api / abi / page-size | |
| apk sha256 | |
| exit-code | |
| raw-log | `build/device-track-batch40/kd71-engine.log` |
| record-ref | `docs/research/2026-11-02-kd71-closeout-criteria.md` |

## G1 三哈希
（before/final 与现树三行）

## G2 逐例表
（27 行：test name | STATUS_CODE；两条 KD-71 用例单独标出）

## G3 签名
（FATAL/ANR 计数 = 0；命令进程行单独说明）

## 残余口径
（G4 是否执行；#240 的 close 先于 execute 返回属 KD-66，不写"close 返回即无 JS 在执行"）
```

台账回填：`docs/known-defects.md` KD-71 追加 Progress 块，写 `closed` 时必须回链该文件与 G1 三哈希；同时保留 R8 §4 的 R1–R7 残余边界（尤其 #240/KD-66、close 返回后仍可能交付 close 前已判定的 `Rendered` 是**已接受语义**）。

## 5. G4 变异/回退控制（建议，不阻塞）

二选一，JVM 侧即可：

- 把 `closed` 判定移出登记临界区（还原旧窗口）→ JVM 用例 `close landing between the closed precheck and registration still rejects the execution` 必须红；
- 删除 `liveEngine.set(js)` 之后的 `abandoned.get()` 复查 → 设备用例 `closeDuringAnInFlightTransactionWaitsAndRejectsIt` 在 `hardDeadline` 足够大时必须拿到 `Rendered`（红）。

必须记录变异 diff、红输出、还原后复跑绿。未做则关闭文案写"红向敏感性未实证"。

## 6. 不在本条范围

- **#240**：close 可先于 `execute()` 返回且该 execute 永不成功；强弃 daemon 线程可能卡在 `jsMutex`。归 **KD-66 进程级 `killProcess`**，不阻塞 KD-71，但关闭文案不得写"close 返回即无 JS 在执行"。
- **`:visual_js` 隔离进程 + AIDL 客户端链路**上的等价性：R8 §7 未验；本条只覆盖 `core:data` 引擎类。
- **M-03 4MP 分支**无专门用例：测试缺口，归 M-03。

## 7. 来源

| 类型 | 来源 | 用途 |
|---|---|---|
| 研究 | `docs/research/2026-11-02-kd71-race-review.md:36-86,90-210,279-286` | 修复形态、G1–G6、残余风险、未验证项 |
| 代码 | `core/data/.../visual/QuickJsVisualExecutionEngine.kt:149-166,187-193,208-209,247,251-267,297-334` | 单锁/强弃/线性化/有界等待 |
| 端口 | `core/domain/.../VisualExecutionEngine.kt:37-38,44-45` | close 幂等、关闭后 `ENGINE_CLOSED` |
| 测试 | `QuickJsVisualExecutionEngineInstrumentedTest.kt`（27 `@Test`）；`QuickJsVisualExecutionEngineJvmTest.kt`（3 `@Test`） | G2/G4 用例数 |
| 设备证据 | `docs/research/evidence/2026-11-01-batch-39/step04-data-suites.md:10,15-16,46` | 批次 39 27 例绿、设备/APK |
| 本轮实跑 | `sha256sum` 三文件、`grep -c '@Test'`、`wc -l build/device-track-batch39/src-snapshot.diff` | G1 现树绑定 |
