# 批次 49 强门：四模块 + 两 flavor androidTest 编译（逐任务 `--rerun`）+ `assembleLocalFirstDebug`

| 字段 | 值 |
|---|---|
| evidence-id | batch49-strong-gate |
| batch | 2026-11-08-batch-49 |
| step | 计划 §P3-16「定向/全量/强门」中的**强门**；本 ask 只做强门（不跑单测/设备） |
| gate | 4 模块 androidTest 编译 + app 两 flavor androidTest 编译（逐任务 `--rerun`）+ `:app:assembleLocalFirstDebug --continue` |
| verdict | **PASS（exit=0）**：7 个任务逐项 exit=0，总 `GRADLE_EXIT=0`；只证明**编译/打包**，不执行任何测试 |
| executed-at | 2026-11-08T01:45:35+08:00 → 01:47:49+08:00（2 min 14 s） |
| head | `ba7b5624ea0c2bc3f09dcee247f5852e4f428122` |
| worktree | 运行日志自记 `dirty entries (porcelain -uall)` = **3943**；运行后本轮实测同值 3943 |
| operator | 门禁子代理（无并发代理；Gradle 串行，单客户端） |
| device-serial | n/a（构建门，无 adb/设备调用） |
| raw-log | `build/batch49-strong.log`（1116 行；sha256 `813fa2cab28020a353716ffbdb85f282f928184cd49dfb2f2b19fe769578f3f2`） |
| runner | `build/run-batch49-strong.sh`（与 batch47/48 同形，仅日志落点换批次 49） |
| apk | `app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk`：190 611 130 B，mtime `11-08 01:47`，sha256 `ace60e07187822e2ff45bb81936d5b47c4d2003b815d5cbb57eceabba9175b55`；`output-metadata.json`：`applicationId=com.tingyun.smartmistakebook.localfirst`、`versionName=0.2.0-localfirst` |
| sanitized | 是（未摘录任何密钥；仓库路径写作 `<repo>`） |

## 1. 判据与命令

判据：强门任务集逐项退出码 0（与批次 41/47/48 强门同形）。执行入口：

```bash
cd "<repo>"
bash build/run-batch49-strong.sh   # 返回 0；脚本内逐任务落日志并记录每项 exit
```

脚本内任务序（`build/run-batch49-strong.sh`，与 `build/run-batch48-strong.sh` 的任务数组逐字一致）：

```text
:core:data:compileDebugAndroidTestKotlin --rerun
:core:database:compileDebugAndroidTestKotlin --rerun
:core:ui:compileDebugAndroidTestKotlin --rerun
:core:export:compileDebugAndroidTestKotlin --rerun
:app:compileLocalFirstDebugAndroidTestKotlin --rerun
:app:compileStrictOfflineDebugAndroidTestKotlin --rerun
:app:assembleLocalFirstDebug --continue
```

「四模块」= `core:data` / `core:database` / `core:ui` / `core:export`（沿用批次 41–48 强门任务集）；「两 flavor」= `app` 的 `localFirst` / `strictOffline`（`app/build.gradle.kts` 的 `productFlavors`，:50-58）。未使用 `connectedLocalFirstDebugAndroidTest`，未调用 adb。

## 2. 逐任务结果

| # | 任务 | run 行 | 任务行（执行态） | BUILD 行 | actionable tasks | exit |
|---|---|---|---|---|---|---|
| 1 | `:core:data:compileDebugAndroidTestKotlin --rerun` | 5 | 63（执行；无 UP-TO-DATE） | 76 `BUILD SUCCESSFUL in 29s` | 1 executed / 27 up-to-date（:77） | **0**（:79） |
| 2 | `:core:database:compileDebugAndroidTestKotlin --rerun` | 80 | 139（执行） | 193 `BUILD SUCCESSFUL in 26s` | 1 executed / 27 up-to-date（:194） | **0**（:196） |
| 3 | `:core:ui:compileDebugAndroidTestKotlin --rerun` | 197 | 251 `NO-SOURCE` | 261 `BUILD SUCCESSFUL in 7s` | 25 up-to-date（:262） | **0**（:264） |
| 4 | `:core:export:compileDebugAndroidTestKotlin --rerun` | 265 | 309（执行） | 320 `BUILD SUCCESSFUL in 7s` | 1 executed / 17 up-to-date（:321） | **0**（:323） |
| 5 | `:app:compileLocalFirstDebugAndroidTestKotlin --rerun` | 324 | 547（执行） | 564 `BUILD SUCCESSFUL in 13s` | 1 executed / 165 up-to-date（:565） | **0**（:567） |
| 6 | `:app:compileStrictOfflineDebugAndroidTestKotlin --rerun` | 568 | 797（执行） | 814 `BUILD SUCCESSFUL in 20s` | 3 executed / 163 up-to-date（:815） | **0**（:817） |
| 7 | `:app:assembleLocalFirstDebug --continue` | 818 | 1108（执行） | 1111 `BUILD SUCCESSFUL in 26s` | 5 executed / 221 up-to-date（:1112） | **0**（:1114） |

总计：`:1115` `=== GATE RESULT: PASS ... ===`；`:1116` `GRADLE_EXIT=0`。脚本进程自身 `SCRIPT_EXIT=0`（后台任务 exit code 0）。

- 编译错误：`grep -c "^e: "` = **0**；`w:` 警告 66 条（均为既有 deprecation/可空性警告，非失败）。
- 6 次任务构建各出现一条 `Deprecated Gradle features were used ... incompatible with Gradle 10`（:70/:187/:255/:314/:558/:808），属警告。
- 无 `FROM-CACHE`（0 处），即 `--rerun` 下编译任务真实执行，而非缓存命中。
- 第 1 次运行重算了 configuration cache（`:6` 原因：`feature/library/build.gradle.kts` 已变更），末次 assemble 复用缓存（`:1113`）。

## 3. 本门证明 / 不证明

**证明**：在 HEAD `ba7b5624` + 3943 条脏工作树上，上述 7 个任务均 exit=0；`core:data`、`core:database`、`core:export`、`app` 两 flavor 的 androidTest Kotlin 源被**实际重新编译**（非 UP-TO-DATE/非 FROM-CACHE）；`localFirstDebug` APK 打包成功。app 编译范围包含本批新增设备用例 `app/src/androidTest/.../SchedulingCalibrationSectionInstrumentedTest.kt`（编译警告 `:551`/`:801` 可证其进入编译）。

**不证明**：

1. **不执行任何测试**：无 `connected*`、无 `*Test` 任务；androidTest 只编译不运行。
2. **`:core:ui:compileDebugAndroidTestKotlin` = NO-SOURCE**（`:251`），该模块没有 androidTest Kotlin 源，不能记为「编译验证」。
3. 编译任务的**上游依赖**可能 UP-TO-DATE，属 Gradle 正常增量语义；本门只强制目标编译任务重跑。
4. 未安装/启动 APK，未验证运行时行为。
5. 门跑在**共享脏树**上，不能据此把通过归给某一条工作线；也不能替代 `acceptance_report --require-complete`（未跑）。
6. 单测（12 模块 JVM）、设备 2h soak、KD-67 确认卡、校准页、冲突页设备证据：**均未由本 ask 运行**。

## 4. 串行/环境纪律

- 启动前进程检查：仅 1 个 `java.exe` = Gradle 9.6.1 daemon（PID 27116），其日志末尾为 idle 周期性 health check（`01:44:46`），无 Gradle 客户端进程；本门 7 个任务在同一脚本内**串行**执行（一次一个 Gradle 客户端）。
- 环境：Temurin JDK `21.0.11`（日志 `:4`）；`GRADLE_USER_HOME=<repo>/.gradle`；wrapper `gradle-9.6.1`；`local.properties` 的 `sdk.dir` 已存在（值不摘录）。
- 未执行 `connectedLocalFirstDebugAndroidTest`；未调用 adb。

## 5. 树稳定性

- HEAD 运行前后一致（`ba7b5624...`）；`dirty entries` 起点（日志 `:3`）与终点（本轮实测）均为 **3943**。
- 门窗口（01:45:30–01:48:10）内 `app/src`、`core/*/src`、`feature/**` 被写的文件共 15 个，全部位于 `feature/*/build/.transforms/**`（Gradle 自身产物），**无源码写入**；即编译输入在运行窗口内未被改动。

## 6. 可重跑

```bash
cd "<repo>"
bash build/run-batch49-strong.sh
echo "EXIT=$?"   # 期望 0；逐任务 exit 见 build/batch49-strong.log 的 "=== exit:" 行
```
