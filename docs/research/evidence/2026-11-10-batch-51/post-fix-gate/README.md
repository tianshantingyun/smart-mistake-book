# 批次 51 环后强门（post-fix gate，2026-11-11）

> 角色：门禁执行员（本 ask 只复跑强门、修复涉及模块定向单测、`acceptance_report` 并记录 exit；未改源码/测试/台账/阈值/门定义/金标）。
> 执行时点：2026-11-11 03:02–03:07 +0800。
> HEAD：`8dd29e963975ff44703f638064322be0e7494f98`（main）；共享脏树 `git status --porcelain` = **1633**、`--porcelain -uall` = **6468**（开工实读；收工 6475，+7 恰为本目录 7 个证据文件）。
> 串行纪律：Gradle 单客户端逐条执行（`build/run-batch51-strong-postfix.sh` 内 7 条任务串行，跑完一条再起下一条）；未运行 `connected*AndroidTest`；未调用 adb。开工前 `Get-CimInstance` 实读仅 1 个 `java.exe`（PID 62320），命令行是 `D:\smb-ui-forest-20261027\.gradle` 的 Gradle daemon，无 GradleWrapperMain/构建客户端；本仓构建使用 `$PWD/.gradle`。
> 说明：`build/batch51-strong-postfix.log` 盘上此前不存在；本轮首次生成。脚本 `build/run-batch51-strong-postfix.sh` 为新增（`build/` 被 `.gitignore:15 **/build/` 忽略），任务集与 `build/run-batch50-strong-postfix.sh:14-22` 逐字一致（门定义不变）。

## 0. 结论与 exit

| 门 | 命令 | exit | 摘要 |
|---|---|---:|---|
| 强门（post-fix） | `bash build/run-batch51-strong-postfix.sh` | **0** | 7/7 任务 exit=0；`GRADLE_EXIT=0`；`^e: ` = **0**；`^> Task .* FAILED` = **0**；67 条 Kotlin 测试源告警 |
| 修复环足迹定向单测 | 见 §2 | **0** | **126 passed**（`tools/figure_eval` 84 + 验收工具 42）；分跑 84/42 亦均 exit 0 |
| `acceptance_report` | `python -I tools/ci/acceptance_report.py --out …/acceptance-report.json` | **1** | `state=FAIL`；counts **937/10/253/0**；`violations=[]`；937/937 PASS 行有效复核 |
| `acceptance_report --require-complete` | 同脚本 `--require-complete --out …/acceptance-report-require-complete.json` | **1** | `NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=10 BLOCKED=253)` + `REVIEW_CHECKS_SHARED`（**20 组**，最大一组 72 行） |

**不得读成**：强门 exit=0 只证明 androidTest 源码编译与打包，不执行任何测试（`:core:ui` 为 `NO-SOURCE`，`assembleLocalFirstDebug` 为 `UP-TO-DATE` 未重打包）；`acceptance_report` 两个 exit=1 是本批真实未收口（10 FAIL / 253 BLOCKED），不是本次复跑的机械故障。

## 1. 强门（post-fix）：PASS，exit=0

```bash
cd "D:/smart mistake book"
bash build/run-batch51-strong-postfix.sh
# SCRIPT_EXIT=0
```

- 日志：`build/batch51-strong-postfix.log`（**1002 行**，sha256 `c67a341928eb1d9acdc3fee6aa51ee23e9e5f4625a417de565506fc8cc35272d`），起止 `03:02:54 → 03:04:37 +0800`。
- 脚本：`build/run-batch51-strong-postfix.sh`（sha256 `05bf163c012500051be7d31bc9ed7d182e641573a26bc18a13be71308b541710`）。
- 日志头 `:1-9`：HEAD `8dd29e96…`、dirty 1633/6468、`openjdk 21.0.11`、修复环代码 sha256
  （`mva_gate.py 9abff892…`、`mva_gate_run.py e63e5f16…`、`test_mva_gate.py 988238bd…`）。

| # | 任务 | exit | 任务态（日志行） | BUILD 行 |
|---|---|---:|---|---|
| 1 | `:core:data:compileDebugAndroidTestKotlin --rerun` | 0 | 执行（`:56`） | `:62` `BUILD SUCCESSFUL in 49s` |
| 2 | `:core:database:compileDebugAndroidTestKotlin --rerun` | 0 | 执行（`:113`） | `:159` `BUILD SUCCESSFUL in 14s` |
| 3 | `:core:ui:compileDebugAndroidTestKotlin --rerun` | 0 | `NO-SOURCE`（`:205`） | `:207` `BUILD SUCCESSFUL in 2s` |
| 4 | `:core:export:compileDebugAndroidTestKotlin --rerun` | 0 | 执行（`:243`） | `:246` `BUILD SUCCESSFUL in 3s` |
| 5 | `:app:compileLocalFirstDebugAndroidTestKotlin --rerun` | 0 | 执行（`:461`） | `:470` `BUILD SUCCESSFUL in 9s` |
| 6 | `:app:compileStrictOfflineDebugAndroidTestKotlin --rerun` | 0 | 执行（`:691`） | `:700` `BUILD SUCCESSFUL in 14s` |
| 7 | `:app:assembleLocalFirstDebug --continue` | 0 | `assembleLocalFirstDebug UP-TO-DATE`（`:994`） | `:997` `BUILD SUCCESSFUL in 2s` |

- 逐任务 `=== exit:` 行：`:65`、`:162`、`:210`、`:249`、`:473`、`:703`、`:1000`；末行 `GRADLE_EXIT=0`（`:1002`）。
- `grep -c '^e: '` = **0**；`grep -c '^> Task .* FAILED'` = **0**；`grep -c 'BUILD SUCCESSFUL'` = **7**、`BUILD FAILED` = **0**。
- 67 条 `^w: ` 均为 Kotlin 测试源既有告警（`Unnecessary non-null assertion`×37、Compose `createComposeRule`×8、
  `readAttemptP0` deprecated×7 等），无 `^e: `，不影响门。
- APK：`app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk`，200 499 969 B，mtime `11-10 07:22`，
  sha256 `38cb991cf680ce40874da2912c5adb08addcb4884fa1b3cf052b13bf07b1a8b6`。本次 `assemble` 为 `UP-TO-DATE`，
  **未重新打包**；该 APK 不是修复环之后的新产物。

## 2. 修复涉及模块定向单测（主口径）：exit=0，126 passed

依据：`fix-loop/REPAIR-REPORT.md:194-210` 的改动清单只含 Python 工具、证据 JSON/MD 与文档；
`app core feature tools` 下 `*.kt` 在修复环窗口（2026-11-11 01:30–03:10）mtime 扫描 **0 命中**：

```bash
find app core feature tools -name "*.kt" -newermt "2026-11-11 01:30" ! -newermt "2026-11-11 03:10" | wc -l
# → 0
```

故修复环本身没有 Kotlin 模块可跑；定向单测覆盖其 Python 足迹（`tools/figure_eval` 的 p95/门判改动与验收工具链）：

| 套件 | 命令 | exit | 读数 | 日志 |
|---|---|---:|---|---|
| Figure/MVA 全模块 | `python -m pytest tools/figure_eval -q` | 0 | **84 passed in 0.24s** | `pytest-figure-eval.txt` |
| 验收工具链 | `python -I -m pytest tools/tests/test_acceptance_report.py tools/tests/test_acceptance_hash_manifest.py tools/tests/test_acceptance_lint.py -q` | 0 | **42 passed in 5.30s** | `pytest-acceptance-tools.txt` |
| **合并（最终盘上状态）** | `python -I -X utf8 -m pytest tools/figure_eval tools/tests/test_acceptance_report.py tools/tests/test_acceptance_hash_manifest.py tools/tests/test_acceptance_lint.py -q` | **0** | **126 passed in 5.38s** | `pytest-targeted-final.txt`（sha256 `248b556d8e65d1b3fd5f9774b6c27efa485ae203af1306c6299224969ef27f68`） |

- 覆盖 p95 端到端修复：`tools/figure_eval/test_mva_gate.py` 已含在 84 passed 内（修复环先红 2 failed → 后绿 18 passed 的同一套件）。
- 未跑：本批 Kotlin 改动模块的扩展 10 模块单测（不在本 ask 范围）；强门只做编译。

## 3. `acceptance_report`：exit=1（真实未收口，读数不变）

```bash
python -I tools/ci/acceptance_report.py --out …/post-fix-gate/acceptance-report.json
# ACCEPTANCE_REPORT_EXIT=1
python -I tools/ci/acceptance_report.py --require-complete \
  --out …/post-fix-gate/acceptance-report-require-complete.json
# ACCEPTANCE_REPORT_REQUIRE_COMPLETE_EXIT=1
```

| 项 | 普通报告 | `--require-complete` |
|---|---|---|
| exit | **1** | **1** |
| state / ok | `FAIL` / false | `FAIL` / false |
| counts | **PASS 937 / FAIL 10 / BLOCKED 253 / UNVERIFIED 0** | 同左 |
| PASS 行复核 | `pass_with_review=937/937`、`pass_with_invalid_review=0`、`pass_without_review=0`、`review_validation_errors=[]` | 同左；`acceptance_complete=false` |
| violations | `[]` | `NOT_ACCEPTANCE_COMPLETE`（`UNVERIFIED=0 FAIL=10 BLOCKED=253`）+ `REVIEW_CHECKS_SHARED`（20 组非 ledger_row checks 被 ≥5 行共用；最大组 72 行） |
| 绑定 | `head=8dd29e96…`、`ledger_sha256=1161bfc2…`、`total=1200` | 同左；`dirty_paths=6471` |

- 与修复环读数的差异为 0：修复环报 `937/10/253/0` + 两项阻断，本轮复跑逐项一致；台账 sha 未变。
- `--require-complete` 的 exit 1 是判门设计（仍有 10 FAIL / 253 BLOCKED），不是脚本故障。

## 4. 边界 / 未跑

- 强门不执行测试；`core:ui` `NO-SOURCE`；`assemble` `UP-TO-DATE` 不证明新 APK。
- 四门状态未因本 ask 改变：3D、AUD-08、MVA 仍 FAIL，3E 仍无批次级签字；**不得签「批次 51 完成 / 四门通过」**。
- 未运行：设备 instrumented / adb、全量或 10 模块 Kotlin 单测、AUD-08 acceptance 重跑（一次性承诺）、
  MVA 真实作者/判官调用、`check_backup_plan_sync.py`、`check_split_gate.py`、`mva_gate.py` CLI、soak、release 构建
  ——均不在本 ask 的三项点名检查内；不把「未跑」记成「通过」。
- 本 ask 未改源码/测试/台账/门阈值/金标；只新增 `build/run-batch51-strong-postfix.sh`（gitignored）、
  `build/batch51-strong-postfix.log`（gitignored）、本目录证据与记录小节。

## 5. 文件

| 文件 | 内容 |
|---|---|
| `pytest-figure-eval.txt` | `tools/figure_eval` 84 passed / exit 0 |
| `pytest-acceptance-tools.txt` | 验收工具链 42 passed / exit 0 |
| `pytest-targeted-final.txt` | 合并 126 passed / exit 0（sha `248b556d…`） |
| `acceptance-report.json` / `.stdout.txt` | 普通报告：exit 1、state FAIL、937/10/253/0、violations=[] |
| `acceptance-report-require-complete.json` / `.stdout.txt` | 终裁门：exit 1、两项阻断 |
| `../fix-loop/REPAIR-REPORT.md` | 修复环改动清单（本 ask 的定向单测依据） |
| `build/batch51-strong-postfix.log`（仓外忽略目录） | 强门原始日志 1002 行 / sha `c67a3419…` |
