<!-- 体积自查：95 行；R4 上限 400 行 / 65536 B -->
# E-41-P11 修复环后强门（5 次尝试：4 次宿主工具链失败 + 第 5 次 exit=0）

| 字段 | 值 |
|---|---|
| evidence-id | E-41-P11 |
| convention-version | 1 |
| batch | 2026-11-03-batch-41 |
| step | 流程 P11（计划 §3「环后强门 1」；批次 40 记录 §12.1 同形门） |
| gate-or-kd | 强门：4 个模块 androidTest 编译 + 两 flavor app androidTest 编译 + `:app:assembleLocalFirstDebug` |
| verdict | **PASS（exit=0）**，但只证明"打包成功 + 编译输入未变"：androidTest Kotlin 编译任务**全部 UP-TO-DATE**（真相见 §3），不执行任何测试，且运行在 **584 条脏工作树**上 |
| executed-at | 2026-11-03T00:31:51+08:00 → 00:36:51+08:00（第 5 次 00:36:26 起，21 s） |
| operator | 门禁轨（批次 41 工作流子代理）；本文件由证据轨**转录 + 复核**，未由证据轨重跑（计划：不运行 Gradle） |
| head | `62522930bc8bae51be1523c954f39449e054b6af` |
| worktree | 运行日志自记 `dirty_paths: 584`；证据轨本轮实测 `git status --porcelain \| wc -l` = **595**（运行后他线继续写入，非本门结果） |
| device-serial | n/a（JVM/构建门，无设备） |
| apk-sha256 | **未记录**：`:app:packageLocalFirstDebug` 在 00:36 执行并覆盖了 APK，随后 00:46 设备轨重建又覆盖一次；唯一有记录的 sha 是 00:46 产物 `5793300c5f453a45715e9846f1e16756dac1300e04dd68d026e6f7957bccd796`（E-41-D01 §3），**不能反推到 00:36 那次** |
| exit-code | 第 1–4 次 = **1**（均为宿主工具链，不是代码）；第 5 次 = **0** |
| raw-log | `build/batch41-strong.log`（399 行）；`build/batch41-strong-attempt1-powershell5.log`(17)、`-attempt2-pwsh7-acl.log`(13)、`-attempt3-cmdshadow.log`(9)、`-attempt4-unquoted-opts.log`(10)；均 gitignored |
| record-ref | `.zcode/plans/plan-2026-11-03-batch-41.md` §3（12 项命名流程的第 11 项）；批次 40 记录 §12.1「强门」 |
| sanitized | 是（§6；仓库路径写作 `<repo>`，本机账号名已替换） |

## 1. 目的与判据

判据只有一条：**计划 §5 的强门应是 exit=0**。门面文件（`build/batch41-strong.log:1-5`）声明的任务集：

```text
:core:data:compileDebugAndroidTestKotlin :core:database:compileDebugAndroidTestKotlin
:core:ui:compileDebugAndroidTestKotlin :core:export:compileDebugAndroidTestKotlin
:app:compileLocalFirstDebugAndroidTestKotlin :app:compileStrictOfflineDebugAndroidTestKotlin
:app:assembleLocalFirstDebug --continue
```

本文件**不**把该门读成"本批功能通过"：它不执行测试，不含 `androidTest` 运行，不含 JVM 单测。

## 2. 五次尝试与失败归因（计划纪律：失败分本批/他线/环境）

| # | 起止（+08:00） | exit | 失败签名（原文摘录） | 归因 |
|---|---|---|---|---|
| 1 | 00:31:51 | 1 | `tools/android-env.ps1:255` `表达式或语句中包含意外的标记"?"`（`$x ? a : b` 三元需 PS7） | **宿主工具链**（PowerShell 5.1 解析器） |
| 2 | 00:32:53 | 1 | `tools/android-workspace-security.ps1:216` `Android workspace ACL grants write access to unauthorized identities`：`TIANSHAN\<redacted:account>` | **宿主环境**（工作区 ACL 守卫拒绝沙箱账号） |
| 3 | 00:35:19 | 1 | `error: too many arguments. Expected 1 argument but got 10.`（`cmd.exe` 被 PATH 上的 npm `cmd` shim 顶替） | **宿主环境**（PATH 遮蔽） |
| 4 | 00:35:58 | 1 | `错误: 找不到或无法加载主类 mistake` / `ClassNotFoundException: mistake`（`GRADLE_OPTS=-Duser.home=<repo>.` 未加引号，路径空格被拆分） | **调用方参数**（引号） |
| 5 | 00:36:26 | **0** | `BUILD SUCCESSFUL in 21s` / `289 actionable tasks: 2 executed, 287 up-to-date` / `EXIT=0` | 成功 |

第 5 次的环境行（`build/batch41-strong.log:5`，路径已替换）：

```text
env: GRADLE_USER_HOME=<repo>/.gradle ANDROID_HOME=C:\Android\Sdk
     ANDROID_USER_HOME=<repo>/.android TEMP=<repo>/.tmp
     GRADLE_OPTS='-Duser.home="<repo>."'
```

**判读**：前四次的 exit=1 全部发生在 Gradle 启动之前或参数解析阶段，日志里没有任何 Kotlin 编译任务被执行；不得把它们记成"代码红"或"门失败"。第 5 次成功说明同一任务集在该树上可编译。

## 3. 第 5 次的结果摘要

| 指标 | 值 | 判据 | 依据 |
|---|---|---|---|
| Gradle 退出码 | 0 | 期望 0 | `build/batch41-strong.log:399` `EXIT=0` |
| 构建结论 | `BUILD SUCCESSFUL in 21s` | 期望成功 | `:396` |
| 任务统计 | 289 actionable；2 executed / 287 up-to-date | 无判据 | `:397` |
| 实际执行的任务 | `:app:validateSigningLocalFirstDebug`、`:app:packageLocalFirstDebug`、`:app:assembleLocalFirstDebug` | — | `:383-385` |
| androidTest Kotlin 编译状态 | 6 个任务**全部未真正编译**：`core:data`/`core:database`/`core:export`/`app:localFirst`/`app:strictOffline` = UP-TO-DATE，`core:ui` = NO-SOURCE | 期望"编译通过" | `:318,330,369,377,379,380` |
| 编译错误 | 0（`grep -cE "^e: "` = 0；有既有 `w:` 警告） | 期望 0 | 全文 grep |
| 测试执行 | **0**（门不含 `*Test` 任务） | — | 任务集 |

- 21 s / 287 up-to-date 说明该树此前已被同形门构建过，本次只重打了 APK；**冷构建未被本证据覆盖**。
- **门的最强项其实是"UP-TO-DATE"而不是"重新编译"**：00:36 与随后 00:46 的设备轨构建（`build/device-track-batch41/build.log:312,340,399`）中，androidTest Kotlin 编译任务都没重跑。Gradle 的 up-to-date 判定基于输入内容哈希，所以它的含义是"当前输入与上次成功编译时相同"；但这不等于本轮亲眼看到编译器成功。最后一次真实编译的时刻**未记录**。
- 旁证：00:33:14 有 1 个 androidTest 源文件被写（`BackupSelectionRestoreInstrumentedTest.kt`），而 00:36 的 `core:data:compileDebugAndroidTestKotlin` 仍判 UP-TO-DATE——只能解释为那次写入的内容哈希未变（等价重写/文件级还原），或写入落在 Gradle 输入快照之后；**无法逐字节证明**，故只登记不裁定。

## 4. APK 与源绑定（本文件最重要的一条限制）

1. **APK**：00:36 的 `:app:packageLocalFirstDebug` 重写了 `app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk`；00:46 设备轨 `:app:assembleLocalFirstDebug` 再次重写（E-41-D01 §2 的 23 s 构建）。证据轨本轮实测该文件 mtime = `11-03_00:46:22`、字节 183602999、sha256 `5793300c…`。**00:36 的字节已不存在**，因此本门没有可绑定的 APK sha。
2. **源**：门禁运行时刻**没有做源快照**。最接近的快照是设备轨 00:46 的 `build/device-track-batch41/src-sha-before.txt`（1287 文件，sha256 `297b2070ce287d73cace1dbf794b5eee4d05d8d183b9b5e362cacc5f46a11734`），紧随其后的 `src-sha-after.txt` 同值、`src-snapshot.diff` 为 0 行。
3. **两时点之间的源码差**：证据轨按 mtime 扫描 `app/src core/model/src core/data/src core/database/src core/domain/src`（00:15–00:50 窗），只有 1 个文件被写：`core/data/src/androidTest/.../BackupSelectionRestoreInstrumentedTest.kt`（00:33:14），早于第 5 次门。由于该门的 androidTest 编译任务判 UP-TO-DATE，**不能**说这次写入"被本门编译过"，也不能反推"内容未变"；只能登记为**未解释**。mtime 本身也不能证明内容等价，故"门所编译的源码 = 00:46 快照"属**未证明**。

## 5. 结论与边界

- 可证：在 `62522930 + 584 条脏工作树` 上，强门任务集退出码为 0；四次失败全部是宿主工具链/调用参数问题，与代码无关。
- 不可证（UNVERIFIED）：该门的 APK 身份、该门的源快照、冷构建、以及"本批改动没有引入编译问题"之外的任何运行期结论。
- 不得据此写"本批双门通过"：单测门同期为红（见 `p12-unit-gate.md`）。

## 6. 脱敏与去噪

```bash
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' docs/research/evidence/2026-11-03-batch-41 || echo "0 hits: secrets"
grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken)[[:space:]]*[:=][[:space:]]*[^ <]' docs/research/evidence/2026-11-03-batch-41 || echo "0 hits: credential assignments"
grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' docs/research/evidence/2026-11-03-batch-41 || echo "0 hits: emails"
grep -RInE 'C:.Users.[^ ]+|/home/[^/ ]+|/Users/[^/ ]+' docs/research/evidence/2026-11-03-batch-41 || echo "0 hits: home paths"
```

- 扫描结果见 `00-index.md` §8（本目录统一扫描一次）。
- 去噪：原始 log 未改；本文件只摘与判定相关的行，省略处未静默挖段。
- 已替换：本机账号名 → `<redacted:account>`；仓库绝对路径 → `<repo>`；`C:\Android\Sdk` 为 SDK 路径非用户目录，保留。
