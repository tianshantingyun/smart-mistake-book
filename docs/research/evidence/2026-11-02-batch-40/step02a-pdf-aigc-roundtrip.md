<!-- 体积自查：约 90 行；R4 上限 400 行 / 65536 B -->
# E-40-02a PDF /AIGC 设备往返（真导出→注入→PdfRenderer）

| 字段 | 值 |
|---|---|
| evidence-id | E-40-02a |
| convention-version | 1 |
| batch | 2026-11-02-batch-40 |
| step | 步骤二A（WS1 设备往返；判据 §45） |
| gate-or-kd | 判据 §45 D45-1…D45-9；KD-75 |
| verdict | **BLOCKED**（设备用例不在树；D45-1 直接失败；未执行 D45-2…D45-9） |
| executed-at | 2026-11-02T10:30+08:00（静态检查） |
| operator | 设备验证轨 |
| head | `a3f367dfaccd960f951a66468a1cdda2f0b21f47` |
| worktree | 653 条（他线在飞） |
| device-serial | emulator-5584（已装四包，未执行本项） |
| apk-sha256 | app 主包 `1acb9907…5983`；data test `34e3435d…3179`（见 E-40-01 §4） |
| exit-code | 静态 grep 命中 0；无设备命令可执行 |
| raw-log | `build/device-track-batch40/dex/apptest.symbols.txt`、`dex/data.symbols.txt`、`build2.log` |
| record-ref | `docs/research/2026-11-02-batch-40-record.md` |
| sanitized | 是 |

## 1. 判据要求的设备用例：不存在

```text
$ grep -rln "PdfAigc" app/src/androidTest core/data/src/androidTest core/export/src/androidTest
（无输出，exit=1）
```

- `app/src/androidTest` 无任何 `PdfAigcDeviceRoundTripInstrumentedTest`（或等价类）。
- app 测试包 dex 中 `PdfAigc` 符号计数 = **0**（`dex/apptest.symbols.txt`）。
- data 测试包 dex 中 `PdfAigc` 符号 = **18**，但全部是 `core:data` main 库类（`PdfAigcInjector` 等）随包携带，不是测试类；没有任何 `*InstrumentedTest` 的 `@Test` 方法。
- `core/export/src` 对 `PdfAigcInjector` / `PdfAigcLabeler` 零引用（`grep -rn` exit=1），导出链未接线。

## 2. 判据逐条状态

| 判据 | 状态 | 依据 |
|---|---|---|
| D45-1 存在性/形状 | **FAIL** | 设备用例不在树，`@Test` 数 = 0，无法产生 `OK (N tests)` |
| D45-2 真导出 | 未执行 | 无用例可构造 `MistakePdfEligibility.check` → `MistakePdfExporter.prepare` |
| D45-3 注入与读回 | 未执行 | 同上 |
| D45-4 PdfRenderer | 未执行 | 同上 |
| D45-5 独立读者 | 未执行 | 无 labeled.pdf 可拉取；pypdf/PyMuPDF 未调用 |
| D45-6 fail-closed 设备负向 | 未执行 | 无用例 |
| D45-7 xref 形态披露 | 未执行 | 无真实导出 PDF 的 `startxref` 可记录 |
| D45-8 判据边界 | 未执行（设备面） | JVM 侧有 `PdfAigcContentPolicyTest`，见 §3 |
| D45-9 链级判据 | **未达成** | `core/export` 零引用注入器；KD-75 维持 open |

## 3. 已有的 JVM 覆盖（不是设备证据）

`core/data/build/test-results/testDebugUnitTest/TEST-*PdfAigc*.xml`（09:38，**本会话未重跑**）：

| 套件 | tests | failures | skipped |
|---|---|---|---|
| PdfAigcMalformedTest | 22 | 0 | 0 |
| PdfAigcSchemaTest | 7 | 0 | 0 |
| PdfAigcTableInjectionTest | 8 | 0 | 0 |
| PdfAigcStreamInjectionTest | 5 | 0 | 0 |
| PdfAigcRoundTripTest | 2 | 0 | 0 |
| PdfAigcContentPolicyTest | 4 | 0 | 0 |

合计 **48 例**（题面写 47：table 套件现为 8 例，非 7）。这些只证明 JVM 字节层，不替代 D45-2/D45-4/D45-5 的设备往返，也不证明 Android `PdfRenderer` 能打开真实导出字节。

## 4. 结论与边界

- 本项**不能**按判据写 PASS；按纪律写 **BLOCKED**。
- 缺口是“设备用例未落码”，不是环境或他线；设备轨未自行新增该用例（避免把验证轨变成 WS1 实现轨，且需先定义真导出夹具与独立读者路径）。
- KD-75 维持 open；导出链（`core/export` 调用注入器）仍是独立的未达成半边。
- 若主会话要求设备轨补写该用例，最小落点为 `app/src/androidTest/.../PdfAigcDeviceRoundTripInstrumentedTest.kt`（app 同时依赖 core:export 与 core:data），并需重跑 E-40-01 的构建/快照/安装门。
