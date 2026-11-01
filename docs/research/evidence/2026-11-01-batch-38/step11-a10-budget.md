# E-11 A-10 预算边界设备套件 3 例（含跨 method 计数不重置例）

| 字段 | 值 |
|---|---|
| evidence-id | E-11 |
| convention-version | 1 |
| batch | 2026-11-01-batch-38 |
| step | 步骤二·A-10 跨 method（`FigureDispatchBudgetBoundaryInstrumentedTest`，本批步骤二点名的跨 method 设备位 + 步骤三 A-10 3 复跑合并执行） |
| gate-or-kd | 批次 38 §1 E.18/V-02 配套（清单 A-10 行 `:198`："同 operationId 换 method 计数不重置"设备断言位 + 终答位子门） |
| verdict | PASS（3/3 绿） |
| executed-at | 2026-11-01T05:2x+08:00（step25） |
| operator | 设备验证轨（批次 38 工作流子代理） |
| head | `dcfdc5b5` |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | data androidTest（窗口 B，E-01 §4） |
| apk-sha256 | `e86fce38…9bcc0` |
| exit-code | 0 |
| raw-log | `build/device-track-batch38/step25-a10-budget.log` + `-logcat.txt` |
| record-ref | `docs/research/2026-11-01-batch-38-record.md` 设备节 |
| sanitized | 是（00-index §6） |

## 1. 目的与判据

本批 V-02 方法切换落地的设备侧预算守卫三例（判据 = 批次 37 判据 §2.2 逐字沿袭，不放宽）：
1. `aSeventhOperationDispatchIsDeniedWithTheRealCount`（`:50`）——第 7 次分派以真实计数拒绝；
2. **`switchingMethodDoesNotResetTheOperationDispatchCount`（`:84`）——跨 method（换 method 不换 operationId）时持久账本计数不重置**（本批步骤二"跨 method"点名位）；
3. `theChatFinalAnswerSlotSurvivesFigureBudgetExhaustion`（`:122`）——耗尽→终答位保留子门（5 次图像派发 Granted → 第 6 次 BUDGET_EXHAUSTED 且消息含"上限 5 次"与"另为讲题终答保留 1 次"→ `reserveModelTaskRemoteDispatch(expectedStatus=QUEUED)` applied=true、logicalDispatchCount=6、budgetExhausted=false；不得以另两例绿替代）。

夹具 `seedModelTask` 落库 QUEUED（批次 37 修复形态，禁止绕过端口直改 DB）。设备 3/3 绿与 JVM 侧 `FigureMethodSwitchTest`（V-02 轨件，非本轨门）互补。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class com.tingyun.smartmistakebook.core.data.visual.FigureDispatchBudgetBoundaryInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s $S logcat -v threadtime -d
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 3 | =构建前计数 3 |
| failures / skipped | 0 / 0 | 期望 0 |
| summary | **OK (3 tests)**，Time 2.577 s | OK (3) |
| 签名命中（九类） | 0 | 期望 0 |

## 4. 关键输出（有界）

```text
INSTRUMENTATION_STATUS: test=aSeventhOperationDispatchIsDeniedWithTheRealCount …
INSTRUMENTATION_STATUS: test=switchingMethodDoesNotResetTheOperationDispatchCount …
INSTRUMENTATION_STATUS: test=theChatFinalAnswerSlotSurvivesFigureBudgetExhaustion …
OK (3 tests)
```

## 5. 失败 / 跳过 / 异常

无。

## 6. 脱敏与去噪

- 删除：无；保留：签名计数（0）。
- 扫描：00-index §6 → 0 hits。

## 7. 判定

**PASS**：3/3 绿（含跨 method 计数不重置位与终答位子门），在 data APK `e86fce38…` 上确实执行通过；清单 A-10 行"跨 method 合计最终图无直接协调器级测试"的覆盖缺口仍以 JVM `FigureMethodSwitchTest`（V-02 轨）+ 本设备位共同守卫，协调器级混合合计向量的覆盖登记维持（不在本轨扩例）。
