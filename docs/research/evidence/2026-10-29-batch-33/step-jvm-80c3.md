# 批次 33 补证：KD-80 的 80-C3 JVM 半边（主会话实跑）

> 时点：2026-10-29。执行者：主会话（非工作流轨）。目的：KD-80 关闭条件 80-C3 要求"裁剪器设备 9 例 + JVM `SmbkSelectionPlanTest`"，工作流轨因纪律不跑 Gradle，本文件补齐 JVM 半边的仓内命令日志。

## 1. 命令与结果（实跑，非转录）

```bash
cd "D:/smart mistake book"
./gradlew.bat :core:data:testDebugUnitTest \
  --tests "*SmbkSelectionPlanTest" --tests "*VisualCheckpointRetentionTest" \
  --rerun-tasks
```

- 退出码：**0**
- 构建：`BUILD SUCCESSFUL in 2m 7s`
- `--rerun-tasks` 保证不是 UP-TO-DATE 复用（强制重跑）。

结果 XML（`core/data/build/test-results/testDebugUnitTest/`）：

| 测试类 | tests | skipped | failures | errors |
|---|---|---|---|---|
| `SmbkSelectionPlanTest` | 12 | 0 | 0 | 0 |
| `VisualCheckpointRetentionTest` | 17 | 0 | 0 | 0 |

## 2. 对 KD-80 的效力

- **80-C3（JVM 半边）→ 满足**：`SmbkSelectionPlanTest` 12/12 绿；裁剪器设备半边在本批 `step03` 已 `OK (9 tests)`。
- **80-C1 → 满足**：本批最终 APK 上 `BackupRestoreInstrumentedTest` `OK (34 tests)`（`step03`）。
- **80-C4 → 满足**：`BackupVisualReferenceInstrumentedTest` `OK (5 tests)`（`step04`）。
- **80-C2 → 仍为部分**：源码/`71.json` 的 90 表逐名比对静态 `RESULT=PASS`，但设备侧 `rowCounts` 的 90 个键**没有在日志里逐名打印**（`E-03 §4` 自述），关闭条件要求"设备侧逐名"，故不满足。

**结论：KD-80 保持 open，唯一剩余项是"设备侧 rowCounts 90 键逐名枚举"。** 本条不把 80-C2 的静态 PASS 当作设备 PASS。

## 3. 复核口径

- 本文件由主会话写入，记录的是主会话自己执行的命令与读取的 XML 计数；不是工作流轨的转录。
- XML 属构建产物（`build/` 被 gitignore），故本文件保留命令以便复跑；未来若需再次取证，直接重跑上面命令。
