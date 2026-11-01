# E-10 批次 39 收尾轮·X-03 设备复跑

| 字段 | 值 |
|---|---|
| evidence-id | E-10 |
| convention-version | 1 |
| batch | 2026-11-01-batch-39（收尾轮 2026-11-02） |
| step | 收尾轮·步二 |
| gate-or-kd | X-03 设备向量门 |
| verdict | PASS |
| executed-at | 2026-11-02T00:56:00+08:00 |
| operator | 设备验证轨（子代理） |
| head | `f10a1e4e` |
| worktree | 514 M/??（开窗时，仅记录） |
| device-serial | `emulator-5562` |
| device-avd | `test_device` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `<repo>/core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `f7e961470b99d6fe5641b798687cd67593ff4b6b7a5251ac4168f0e06699e3a2` |
| apk-version | （androidTest，versionName 空） |
| exit-code | adb-exit=0；`INSTRUMENTATION_CODE: -1`（正常结束） |
| raw-log | `build/device-track-batch39-tail/suite-x03.log`（34 行）、`suite-x03-logcat.txt` |
| record-ref | `docs/research/evidence/2026-11-01-batch-39/00-index.md` §11 |
| sanitized | 是 |

## 1. 命令

```bash
adb -s emulator-5562 logcat -c
adb -s emulator-5562 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.StrictOfflineRelayoutDeviceVectorInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
```

## 2. 首跑红与 A 类夹具修正（修复前证据）

首跑（同一 APK 前身）失败于 `:310`（修复前行号）：

```text
java.lang.AssertionError: 两帧必须按次序真实到达引擎（拖动换视口 + 播放推进时间轴）
expected: java.util.Arrays$ArrayList<[[300, 200, 1000], [300, 200, 61000]]>
   but was: java.util.ArrayList<[[300, 200, 1000], [300, 200, 61000]]>
```

根因（`javap` 实证）：`listOf(300, 200, 1_000L)` 的公共类型被推断为 `Long`，编译为
`Boxing.boxLong` 三处；`CountingVisualEngine.executedFrames` 是 `Int/Int/Long` 装箱
（`List<Any>`），元素级 `Integer(300).equals(Long(300)) == false`。这是夹具类型缺陷，
不是产品回归。

修正（只统一数值装箱类型，同值同序，不放宽断言）：

```kotlin
// StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt:316-323（修正后）
counting.executedFrames.map { frame -> frame.map { (it as Number).toLong() } },
```

## 3. 判定（全部满足）

```text
grep -cE '^OK \(1 test\)$' suite-x03.log                       → 1
grep -c 'INSTRUMENTATION_STATUS_CODE: 0' suite-x03.log         → 1
grep -cE 'INSTRUMENTATION_STATUS_CODE: -(1|2|3)' suite-x03.log → 0
9 类崩溃签名（suite-x03-logcat.txt）                            → 全 0
```

`Time: 5.112`，`OK (1 test)`，无 `FAILURES!!!`。

## 4. 三断言位（单 @Test，无跳闸 ⇒ 绿即三处无条件断言均已执行）

文件 `core/data/src/androidTest/.../visual/StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt`（行号为修正后时点）：

1. `:337-341` `readParentDispatchCount(reopened, PARENT_REQUEST_ID) == 0`
2. `:342-347` operation `status=SUCCEEDED`、`reserved_remote_dispatches=0`、`used_remote_dispatches=0`
3. `:348-356` 2 行 attempt 均 `method=JS`、`status=SUCCEEDED`、`reservation_id IS NULL`

## 5. A 类修复位（主会话 f10a1e4e + 本轮类型修正）

- `:224-228` `assertNotEquals(链身份, 持久行 id)`（两套派生空间）
- `:229-240` `TutorVisualIdentity.deriveProgramId` 六字段复算 == 持久行 id
- `:251-262` ①档 `RelayoutAllowed`，`allowed.sourceJs == persisted.sourceJs`
- `:316-323` 本轮类型归一（见 §2）

## 6. 边界

X-03 跑在 `data-debug-androidTest.apk`（`f7e96147…`）上；该包内部 DB 版本态与备份/裁剪套件的红例归因（E-12）不影响本用例：X-03 只读视觉链四表与图生成台账，不读 `user_version`。
