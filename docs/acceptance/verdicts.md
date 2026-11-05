# 批次 44 逐项终裁表（1200 项）

> 生成命令：`python -I docs/research/evidence/2026-11-06-batch-44/acceptance/make_acceptance_report.py`（生成器）
> 本报告 run-id：`2026-11-04T22:17:48Z-batch44-acceptance-report`　台账 `docs/acceptance/ledger.md` sha256：`e139b03e8b9a005af73d658333f7721907b7e6976718106934c00e096a3eb675`
> 逐行数据源：`docs/acceptance/ledger.md`（verdict 与证据列原文投影，未改写）；证据文件与 run 绑定由 `acceptance_crosscheck.py` 复核。

## 0. 结论先行：本批**不可**作总体通过签字

| 项 | 结果 |
|---|---|
| 台账行数 | 1200（D0 100 / A–J 各 90 / FIX 100 / GATE 100） |
| 计数 | PASS 670 / FAIL 21 / BLOCKED 73 / UNVERIFIED 436 |
| 终裁门 `--require-complete` | **exit 1**（NOT_ACCEPTANCE_COMPLETE） |
| 追溯门 `trace_requirements` | **exit 1**（260 条 gap） |
| 独立复核覆盖 | 证据列含 `review:` token 的行 **0**；方法列要求复核文件的行 64 条且文件全部不存在；`docs/acceptance/verdicts/*.json` 终裁文件 0 个 |

因此：`PASS` 在本报告里的含义限定为「该行证据列通过 `acceptance_report.py` 的≥2 类独立证据审计」，**不等于**该行已通过 DoD §1.5 独立复核与 GATE 终裁。主会话只能逐行签字，不能签「批次 44 验收通过」。

## 1. 统计

| 域 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED |
|---|---:|---:|---:|---:|---:|
| D0 验收基础设施 | 100 | 0 | 0 | 0 | 100 |
| A 功能旅程 | 90 | 66 | 0 | 1 | 23 |
| B 数据完整性 | 90 | 88 | 0 | 0 | 2 |
| C 视觉与渲染 | 90 | 77 | 1 | 0 | 12 |
| D 模型链路 | 90 | 81 | 0 | 9 | 0 |
| E 复习与算法 | 90 | 80 | 0 | 0 | 10 |
| F 性能与稳定 | 90 | 56 | 2 | 10 | 22 |
| G 安全与隐私 | 90 | 82 | 0 | 2 | 6 |
| H 合规与文案 | 90 | 66 | 5 | 12 | 7 |
| I 可用性与无障碍 | 90 | 53 | 0 | 5 | 32 |
| J 发布工程 | 90 | 21 | 13 | 34 | 22 |
| FIX 修复与加固 | 100 | 0 | 0 | 0 | 100 |
| GATE 门禁与终裁 | 100 | 0 | 0 | 0 | 100 |
| **合计** | **1200** | **670** | **21** | **73** | **436** |

## 2. 四门实跑（本轮，可复跑）

| 门 | 命令 | exit | 结论 | 日志 |
|---|---|---:|---|---|
| acceptance_init | `python -I tools/ci/acceptance_init.py --check` | 0 | 1200 行 / ID 集合 / 域计数通过（工具成功时静默，只留 exit；独立重算见 docs/research/evidence/2026-11-06-batch-44/acceptance/ledger-shape.log：rows=1200、issues=[]、match=True） | `docs/research/evidence/2026-11-06-batch-44/acceptance/init-check.log` |
| acceptance_report | `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md` | 0 | violations=0；PASS 670 行均 ≥2 类独立证据；acceptance_complete=false | `docs/research/evidence/2026-11-06-batch-44/acceptance/report.stdout.log` |
| acceptance_report --require-complete | `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md --require-complete` | 1 | NOT_ACCEPTANCE_COMPLETE（仍有 UNVERIFIED/FAIL） | `docs/research/evidence/2026-11-06-batch-44/acceptance/report-require-complete.log` |
| acceptance_lint | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md` | 0 | violations=0；warnings=29（全部 EVIDENCE_REUSED） | `docs/research/evidence/2026-11-06-batch-44/acceptance/lint.stdout.log` |
| trace_requirements | `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md` | 1 | gaps=260；归一化后剩 111 行（57+10 UNVERIFIED 待产证据 / 44 PASS 待改方法列） | `docs/research/evidence/2026-11-06-batch-44/acceptance/trace.stdout.log` |
| tool self-tests | `python -I -m pytest tools/tests/test_acceptance_report.py tools/tests/test_acceptance_lint.py tools/tests/test_acceptance_init.py tools/tests/test_trace_requirements.py -q` | 0 | 30 passed（JUnit XML 同步落盘 tool-selftests.xml，含批次 41/42 假绿负控用例） | `docs/research/evidence/2026-11-06-batch-44/acceptance/tool-selftests.log` |
| false-green replay drill | `python -I tools/ci/acceptance_report.py --ledger tools/tests/fixtures/acceptance/fake_green_batch4{1,2}.ledger.md --partial` | 1 | 批次 41/42 已知假绿夹具均 exit 1（PASS_NEEDS_TWO_INDEPENDENT_EVIDENCE、SKIPPED_GATE_AS_PASS、SUMMARY_AS_EVIDENCE、UNVERIFIED_AS_PASS） | `docs/research/evidence/2026-11-06-batch-44/acceptance/replay-drill.log` |

复跑方式：`python -I tools/ci/acceptance_init.py --check`、`python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md --require-complete`、`python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md`、`python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md`。

一次性重放（含每条 exit 标记与本报告 ledger sha 绑定）：`docs/research/evidence/2026-11-06-batch-44/acceptance/gates-final.log`。

### 2.1 trace 260 条 gap 的真实性分类

| gap 码 | 解析器误报 | 真实缺口 | 说明 |
|---|---:|---:|---|
| DEVICE_EVIDENCE_MISSING | 0 | 10 | GATE 行声明设备执行但无 device 证据，无假绿 |
| MISSING_EVIDENCE_PATH | 0 | 50 | 占位复核文件真的不存在（FIX/GATE 行），无假绿 |
| MISSING_TEST_PATH | 65 | 0 | `test: path#方法名` 的 `#方法名` 未被剥离；文件实际存在（如 PaperEditPromptGoldenTest.kt） |
| MISSING_TOOL_PATH | 75 | 0 | `python -I -m pytest` 把 `-m` 当脚本路径 |
| PASS_WITHOUT_TEST_LANDING | 16 | 44 | 方法列没有 `test:`/`device:` 落点。其中 16 条是解析器误报（全角 `；` 截断）；44 条是 I 域真实缺口：方法只写 `python -I tools/ci/a11y_probe.py …` 或 `a11y_audit.py`，其中 42 条证据列已有 device JSON、2 条为静态主题审计，属台账方法列未按 `device:` 约定书写 |

归一化重算（剥离 `#方法名`、忽略 `-m`、按 `；` 分块、认 `device:` 证据）后，剩余 111 行有缺口，分布为 {"PASS_WITHOUT_TEST_LANDING": {"PASS": 44}, "MISSING_EVIDENCE_PATH": {"UNVERIFIED": 57}, "DEVICE_EVIDENCE_MISSING": {"UNVERIFIED": 10}}：

- `MISSING_EVIDENCE_PATH` 57 行 + `DEVICE_EVIDENCE_MISSING` 10 行：全为 **UNVERIFIED** 行，即复核/终裁文件与设备轮次尚未产生，没有把未跑写成 PASS；
- `PASS_WITHOUT_TEST_LANDING` 44 行：全为 **PASS** 的 I 域行，方法是 `python -I tools/ci/a11y_probe.py …` / `a11y_audit.py`，证据列已有 `device:`/`test:`；这是台账方法列未按 `device:` 约定书写造成的追溯缺口，**不是**证据缺失，但也不能当作 trace 门已过——需域负责人改方法列或工具认该形态后复跑。

**待主会话决定**：`tools/ci/trace_requirements.py` 的三处解析缺陷属本报告范围外（工具目录不在本次写权限内），需单独提交修复；在修复前该门不能清零。

## 3. 非 PASS 项逐条原因（530 条）

### 3.1 FAIL（21 条）

| id | 检查 | 原因 | 证据 |
|---|---|---|---|
| C-065 | 退化降级：恢复文档回放必须留下墨水（restore 不是静默空转） | 设备用例 theRestoreLegAloneReplaysOfflineWithZeroDispatchBookings 断言失败：java.lang.AssertionError: the restored document must leave ink (restore is not a silent no-op)（c-domain/c-restore-single-rerun.log；XML failures=1） | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-single-rerun.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-single-rerun.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-single-rerun.log |
| F-055 | 长时 2h：基线 | 2h soak 在 2504s 发生 FATAL EXCEPTION（Desired retention 越界），进程重启，2723s 人工停止，未完成 2h（f-domain/soak.cmd.log、soak.device.json） | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.device.json; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak-crash-logcat.txt; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak-crash-dropbox.txt; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak-samples.csv |
| F-057 | 长时 2h：超限 | 2h 窗口内命中 ≥1 次 FATAL EXCEPTION（SchedulingSettingsScreen.kt:74 → SchedulingOptions 越界），超限路径成立（f-domain/soak-crash-logcat.txt） | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.device.json; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak-crash-logcat.txt; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak-crash-dropbox.txt |
| H-018 | AIGC 隐式：全部交付格式与打印子集都有文件级隐式标识 | h_compliance_gate --only H-018 exit=1：DOCX 无文档级 /AIGC；KD-84 部分页丢标；KD-85 模型改卷文本漏标（docs/acceptance/evidence/H/logs/h-018.log） | cmd:docs/acceptance/evidence/H/logs/h-018.log#exit=1; file:docs/known-defects.md; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DocxMistakeRenderer.kt |
| H-052 | 免责声明：文案与 PDF 隐式标识现状一致 | 免责声明文案仍称 PDF 尚未写入文件级隐式信息，而导出链已注入 /AIGC（h-052.log exit=1） | cmd:docs/acceptance/evidence/H/logs/h-052.log#exit=1; file:docs/ai-content-disclosure-copy.md; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporter.kt |
| H-061 | 导出水印：部分页打印子集保留文档级 /AIGC | KD-84 open：writeSubset 逐页重绘，Info 字典不随页复制（h-061.log exit=1） | cmd:docs/acceptance/evidence/H/logs/h-061.log#exit=1; file:docs/known-defects.md |
| H-062 | 导出水印：模型改卷产生的文本导出时带 /AIGC | KD-85 open：toDraft 只透传来源 hasModelParsedText（h-062.log exit=1） | cmd:docs/acceptance/evidence/H/logs/h-062.log#exit=1; file:docs/known-defects.md |
| H-066 | 错误文案：诊断包失败文案不拼接原始异常 message | SecondaryScreens.kt:826 把 failure.message 直接拼进用户可见文案（h-066.log exit=1） | cmd:docs/acceptance/evidence/H/logs/h-066.log#exit=1; file:app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt |
| J-001 | 干净树前提：porcelain(-uall) 为 0 | 工作树脏 4248 行（j-identity.log dirty_paths=4248）；--require-clean 被包装器拒绝（j-neg-clean-tree.log 为负控） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-identity.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-identity.xml |
| J-002 | 产物 revision 等于 HEAD 且落后 0 提交 | release APK revision faab17f5… 落后 HEAD 542b9bba… 32 个提交（j-identity.log commits_behind_head=32） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-identity.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-neg-head.log#exit=0 |
| J-020 | release APK/AAB 使用生产钥匙 | release 未用生产钥匙：APK 证书 CN=WS8 Ephemeral Local Only；RELEASE_* 4/4 缺失（j-apk-cert.log、j-signing.log） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-signing.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-apk-cert.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-release-manifest.log#exit=0 |
| J-021 | 签名指纹等于已登记发布指纹 | 签名指纹不等于已登记发布指纹：APK 证书 9dc07e9f…056e20，AAB 证书 A9:E7:6C:AD…（j-apksigner.log、j-keytool.log） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-apksigner.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-keytool.log#exit=0 |
| J-024 | AAB 与 APK 使用同一签名身份 | APK 证书 CN=WS8 Ephemeral Local Only 与 AAB 证书 CN=ZCode Release Smoke 身份不同（j-apk-cert.log、j-keytool.log） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-keytool.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-apksigner.log#exit=0 |
| J-025 | AAB 证书有效期覆盖发布窗口 | AAB 证书有效期至 2026-10-30，已过期（j-keytool.log） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-keytool.log#exit=0 |
| J-027 | 文档契约一致：keystore.properties 与环境变量 | 文档只写 keystore.properties，实际 RELEASE_* 4/4 缺失、0 个已跟踪 keystore （j-signing.log） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-signing.log#exit=0; file:app/build.gradle.kts |
| J-033 | AAB 的 ABI 集合等于 abiFilters | AAB ABI 集合 [arm64-v8a, armeabi-v7a, x86, x86_64] != abiFilters [arm64-v8a, x86_64]（j-aab16k.log errors，EXIT=1） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-aab16k.log#exit=1; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-bundletool-validate.log#exit=0 |
| J-034 | AAB 含 libquickjs.so | AAB 缺 libquickjs.so（j-aab16k.log errors: required native library missing） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-aab16k.log#exit=1 |
| J-041 | 负向：AAB 不含 32 位 .so | AAB 含 32 位 .so（armeabi-v7a/x86），负向断言不成立（j-aab16k.log abi_set） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-aab16k.log#exit=1; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-bundletool-validate.log#exit=0 |
| J-048 | versionCode 跨构建单调 | 四份产物 versionCode 全为 2（aab 亦为 2），跨构建无单调证据（j-version-artifacts.log） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-version-artifacts.log#exit=0 |
| J-051 | 版本号生成或注入机制（非手改两行） | 版本号是 app/build.gradle.kts:33-34 手写两行，无生成/注入机制（j-version-source.log grep 证据） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-version-source.log#exit=0; file:app/build.gradle.kts |
| J-052 | 回归：历史 AAB 与现 APK 的 vc 可比 | 历史 AAB 与现 APK 同为 versionCode=2 且 AAB mtime 2026-09-30，陈旧不可比（j-artifact-mtimes.log、j-bundletool-manifest.log） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-version-artifacts.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-artifact-mtimes.log#exit=0 |

### 3.2 BLOCKED（73 条）

| id | 检查 | 原因 | 证据 |
|---|---|---|---|
| A-010 | 旅程「讲题」：主路径 | 真实 Provider/凭据缺失（runbook §9）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md; test:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.log |
| D-082 | 真实 Provider：端到端主路径（真实 key + 真实上游） | 无真实 Provider 密钥（runbook docs/acceptance/runbook.md:162） | file:docs/acceptance/runbook.md |
| D-083 | 真实 Provider：真实 401/429/5xx 失败报文与重试恢复 | 无真实 Provider 密钥（runbook docs/acceptance/runbook.md:162） | file:docs/acceptance/runbook.md |
| D-084 | 真实 Provider：真实上下文/图片尺寸上限边界 | 无真实 Provider 密钥（runbook docs/acceptance/runbook.md:162） | file:docs/acceptance/runbook.md |
| D-085 | 真实 Provider：真实网络取消（取消后上游不再计费/不落半条任务） | 无真实 Provider 密钥（runbook docs/acceptance/runbook.md:162） | file:docs/acceptance/runbook.md |
| D-086 | 真实 Provider：真实限流后的重试与账本计数 | 无真实 Provider 密钥（runbook docs/acceptance/runbook.md:162） | file:docs/acceptance/runbook.md |
| D-087 | 真实 Provider：真实计费/配额账本与本地 6 次派遣的关系 | 无真实 Provider 密钥（runbook docs/acceptance/runbook.md:162） | file:docs/acceptance/runbook.md |
| D-088 | 真实 Provider：真实失败报文诊断（含学生内容的原文只进受控缝隙） | 无真实 Provider 密钥（runbook docs/acceptance/runbook.md:162） | file:docs/acceptance/runbook.md |
| D-089 | 真实 Provider：证据绑定 run-id、APK sha 与密钥来源（不回显密钥） | 无真实 Provider 密钥（runbook docs/acceptance/runbook.md:162） | file:docs/acceptance/runbook.md |
| D-090 | 真实 Provider：BLOCKED 口径——无 key 时不得计为通过，只能登记 BLOCKED + runbook | 无真实 Provider 密钥（runbook docs/acceptance/runbook.md:162） | file:docs/acceptance/runbook.md |
| F-006 | 冷启动：arm64（BLOCKED） | 无 arm64 真机或镜像（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| F-015 | warm-up：arm64（BLOCKED） | 无 arm64 真机或镜像（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| F-024 | 首帧：arm64（BLOCKED） | 无 arm64 真机或镜像（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| F-033 | 导出耗时：arm64（BLOCKED） | 无 arm64 真机或镜像（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| F-042 | 预览耗时：arm64（BLOCKED） | 无 arm64 真机或镜像（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| F-051 | 内存峰值：arm64（BLOCKED） | 无 arm64 真机或镜像（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| F-060 | 长时 2h：arm64（BLOCKED） | 无 arm64 真机或镜像（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| F-069 | ANR/崩溃签名：arm64（BLOCKED） | 无 arm64 真机或镜像（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| F-078 | 进程死亡恢复：arm64（BLOCKED） | 无 arm64 真机或镜像（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| F-087 | 后台限制/低电低存/多任务：arm64（BLOCKED） | 无 arm64 真机或镜像（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| G-087 | 已知 CVE 比对 | 无离线 advisory 库、未联网取证（出口：docs/acceptance/runbook.md） | - |
| G-088 | 锁文件覆盖（KD-72） | check_lockfiles 处于 NOT_LANDED 放行态、13 个未跟踪锁，按纪律不计通过（出口：docs/acceptance/runbook.md） | - |
| H-008 | AIGC 显式：讲题与配图界面显示“AI 生成”角标 | G1/G2 接线未落地，feature/tutor 与 core/ui 热区禁改（出口：docs/acceptance/runbook.md） | cmd:docs/acceptance/evidence/H/logs/h-008.log#exit=1; file:docs/ai-content-disclosure-copy.md; file:docs/agent-first-refactor-decisions-2026-09-23.md |
| H-024 | 隐私政策：已发布隐私政策 URL 可公开访问 | 需开发者域名与 Play 后台，外部未产出（出口：docs/acceptance/runbook.md） | cmd:docs/acceptance/evidence/H/logs/h-024.log#exit=1; file:PRIVACY-DATA-FLOW.md |
| H-025 | 隐私政策：文案引用的未成年人个人信息保护章节在隐私政策中存在 | 隐私政策未发布，专章随政策落地（出口：docs/acceptance/runbook.md） | cmd:docs/acceptance/evidence/H/logs/h-025.log#exit=1; file:docs/ai-content-disclosure-copy.md; file:docs/current/privacy-and-data-safety.md |
| H-036 | data safety：Play Console data safety 表单已提交 | 需开发者账号与 Play Console，外部未产出（出口：docs/acceptance/runbook.md） | cmd:docs/acceptance/evidence/H/logs/h-036.log#exit=1; file:docs/current/privacy-and-data-safety.md |
| H-039 | 青少年：产品侧监护人同意机制存在并接线 | G7 第 3 项，落地批次待排（出口：docs/acceptance/runbook.md） | cmd:docs/acceptance/evidence/H/logs/h-039.log#exit=1; file:docs/agent-first-refactor-decisions-2026-09-23.md |
| H-040 | 青少年：年龄确认 UI 存在 | G7 第 3 项，落地批次待排（出口：docs/acceptance/runbook.md） | cmd:docs/acceptance/evidence/H/logs/h-040.log#exit=1; file:docs/agent-first-refactor-decisions-2026-09-23.md |
| H-041 | 青少年：设置页“家长须知”入口存在 | G7 第 8 项，feature/profile 热区未空出（出口：docs/acceptance/runbook.md） | cmd:docs/acceptance/evidence/H/logs/h-041.log#exit=1; file:docs/agent-first-refactor-decisions-2026-09-23.md |
| H-042 | 青少年：累计使用时长提醒与 22:00 至 6:00 静默时段开关存在 | G7 第 4 项，落地批次待排（出口：docs/acceptance/runbook.md） | cmd:docs/acceptance/evidence/H/logs/h-042.log#exit=1; file:docs/agent-first-refactor-decisions-2026-09-23.md |
| H-043 | 青少年：隐私政策含未成年人个人信息专章 | 隐私政策未发布，专章未产出（出口：docs/acceptance/runbook.md） | cmd:docs/acceptance/evidence/H/logs/h-043.log#exit=1; file:docs/current/privacy-and-data-safety.md |
| H-051 | 免责声明：三档文案已嵌入应用关于页或首启弹窗 | 应用当前无关于页，嵌入需动热区，落地批次待排（出口：docs/acceptance/runbook.md） | cmd:docs/acceptance/evidence/H/logs/h-051.log#exit=1; file:docs/ai-content-disclosure-copy.md |
| H-053 | 免责声明：模型名称、备案号、上线编号占位符已回填 | 需 CAC 备案名单与属地登记，外部未产出（出口：docs/acceptance/runbook.md） | cmd:docs/acceptance/evidence/H/logs/h-053.log#exit=1; file:docs/ai-content-disclosure-copy.md |
| H-081 | i18n：英文商店详情与隐私政策本地化已产出 | 发布工程外部依赖，未产出英文上架材料（出口：docs/acceptance/runbook.md） | cmd:docs/acceptance/evidence/H/logs/h-081.log#exit=1; file:docs/current/privacy-and-data-safety.md |
| I-007 | TalkBack 语义：朗读文本内容回传需要外部硬件驱动，仓内无该驱动 | 见 runbook 无障碍段（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md; device:docs/research/evidence/2026-11-05-batch-44/i-domain/talkback-review.device.json |
| I-061 | 错误可读：启动失败、导出失败等错误态需故障注入或真实 Provider | 无故障注入入口（出口：docs/acceptance/runbook.md） | test:docs/research/evidence/2026-11-05-batch-44/i-domain/i-error-fail-closed.xml; file:docs/acceptance/runbook.md |
| I-070 | 键盘：第三方中文输入法交互不在自动化范围 | 需要真实 IME 与人工操作（见 runbook）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| I-075 | 横竖屏：负向——传感器旋转在无窗口模拟器不生效，因此未采用旋转设置冒充横屏 | 见 runbook（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| I-079 | 横竖屏：真机传感器旋转与折叠屏形态需实机覆盖 | 无真机（见 runbook）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-006 | 干净树构建阻塞与出口 | 共享树未提交且验收阶段不跑 Gradle（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-017 | APK 路线 mapping 上传 Play Console | 无 Play Console 账号（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-019 | 生产签名四变量齐全 | cmd: python -I tools/ci/j_release_verify.py signing（出口：docs/acceptance/runbook.md） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-signing.log#exit=0; file:docs/acceptance/runbook.md |
| J-035 | 运行时页大小为 16384 | device: adb shell getconf PAGE_SIZE（出口：docs/acceptance/runbook.md） | device:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-pagesize.device.json; file:docs/acceptance/runbook.md |
| J-037 | arm64 模拟器或真机启动 | x86_64 宿主不能起 arm64 AVD（runbook §9.2）（出口：docs/acceptance/runbook.md） | device:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-pagesize.device.json; file:docs/acceptance/runbook.md |
| J-038 | arm64 APK 安装与冷启 | 无 arm64 设备（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-039 | arm64 运行时 16KB 与 native 装载 | 无 arm64 设备（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-042 | arm64 设备证据绑定 serial/boot/apk sha | 无 arm64 设备（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-043 | 宿主架构不支持 arm64 的阻塞登记 | 两台在线设备均 x86_64（runbook §9.2）（出口：docs/acceptance/runbook.md） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-probe.log#exit=0; file:docs/acceptance/runbook.md |
| J-055 | 同签名 vc N 到 N+1 升级 | 无成对 vc 产物（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-056 | 升级后数据保留 | 无成对 vc 产物（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-057 | 降级被 INSTALL_FAILED_VERSION_DOWNGRADE 拒绝 | 无低 vc 产物（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-061 | 成对产物阻塞与出口 | 无成对 vc 与同钥匙（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-065 | 上一版可覆盖安装 | 无上一版成对产物（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-068 | 回滚证据：成对包与数据面 | 无成对包与生产签名（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-069 | 回滚阻塞与出口 | 无成对包与生产签名（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-073 | 商店图标 512x512 | 无 Play Console 账号与素材（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md; file:docs/research/2026-10-25-store-launch-materials-checklist.md |
| J-074 | 特色图片 1024x500 | 无 Play Console 账号与素材（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md; file:docs/research/2026-10-25-store-launch-materials-checklist.md |
| J-075 | 手机与平板截图 | 无 Play Console 账号与素材（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-076 | 简短与完整描述 | 无 Play Console 账号与文案定稿（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-077 | 隐私政策 URL | 无托管 URL 与账号（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md; file:docs/current/privacy-and-data-safety.md |
| J-078 | Data safety 表单映射 | 无账号（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md; file:docs/current/privacy-and-data-safety.md |
| J-079 | AIGC 分发表单与说明材料 | 无账号与备案信息（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md; file:docs/research/2026-10-25-store-launch-materials-checklist.md |
| J-080 | 内容分级与适龄定位 | 无 Play Console 账号（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-081 | 商店素材 runbook 覆盖 | 素材清单存在但未备料（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md; file:docs/research/2026-10-25-store-launch-materials-checklist.md |
| J-082 | 封闭测试轨道创建 | 无 Play Console 账号（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-083 | 测试账号清单 | 无 Play Console 账号（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-084 | 反馈渠道 | 无 Play Console 账号（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-085 | 测试轮次与退出条件 | 无 Play Console 账号（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-086 | vc 递增上传 | 无账号且 vc 不单调（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-087 | Play App Signing 指纹登记 | 无生产签名与账号（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-088 | 16KB 上传警告清零 | 无账号且 AAB 陈旧（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-089 | 版本说明与发布说明 | 无 Play Console 账号（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |
| J-090 | 封闭测试 runbook 覆盖 | 轨道未建立（runbook §9.2）（出口：docs/acceptance/runbook.md） | file:docs/acceptance/runbook.md |

### 3.3 UNVERIFIED（436 条）

| id | 检查 | 原因 | 证据 |
|---|---|---|---|
| D0-001 | 台账 ledger.md：文件存在、UTF-8 可读、含 1200 行数据行 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| D0-002 | 台账 ledger.md：ID 唯一且属于 13 个域前缀 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| D0-003 | 台账 ledger.md：D0 100 + A–J 各 90 + FIX 100 + GATE 100 计数成立 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| D0-004 | 台账 ledger.md：每行六列齐全，检查/方法不得含竖线 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| D0-005 | 台账 ledger.md：verdict 仅 PASS/FAIL/BLOCKED/UNVERIFIED | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| D0-006 | 台账 ledger.md：证据 token 可按 kind:path#attrs 解析 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| D0-007 | 台账 ledger.md：重新生成不覆盖已有 verdict（只补缺失行） | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| D0-008 | DoD dod.md：功能完成定义：主路径可复跑 | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-009 | DoD dod.md：失败路径定义：错误可见且数据安全 | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-010 | DoD dod.md：边界定义：空态/大数据/权限拒绝/进程死亡 | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-011 | DoD dod.md：证据定义：≥2 类独立证据 | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-012 | DoD dod.md：独立复核定义：reviewer 非作者且绑 run-id | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-013 | DoD dod.md：对抗复核定义：每域至少 1 条反例尝试 | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-014 | DoD dod.md：DoD 与台账 verdict 的映射表存在 | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-015 | 报告 acceptance_report.py：PASS 必须有 ≥2 类独立证据（cmd/test/device/review） | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| D0-016 | 报告 acceptance_report.py：所有证据路径必须存在 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| D0-017 | 报告 acceptance_report.py：cmd 日志 exit 可读且为 0 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| D0-018 | 报告 acceptance_report.py：test XML 的 failures/errors 为 0 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| D0-019 | 报告 acceptance_report.py：review 终裁含 reviewer/run_id 且非自证 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| D0-020 | 报告 acceptance_report.py：非法 verdict / 重复 ID / 缺行 → exit 1 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| D0-021 | 报告 acceptance_report.py：输出 JSON 并返回稳定 exit code | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| D0-022 | lint acceptance_lint.py：summary/批次记录不得当证据 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md | - |
| D0-023 | lint acceptance_lint.py：UNVERIFIED 不得标 PASS | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md | - |
| D0-024 | lint acceptance_lint.py：跳过的门不得计通过（-4/SKIP/AssumptionViolated） | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md | - |
| D0-025 | lint acceptance_lint.py：空证据 PASS 必须违规 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md | - |
| D0-026 | lint acceptance_lint.py：同一证据跨过多行 PASS 必须告警 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md | - |
| D0-027 | lint acceptance_lint.py：历史假绿夹具回放必须 exit 1 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md | - |
| D0-028 | lint acceptance_lint.py：自证复核必须违规 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md | - |
| D0-029 | 逐项终裁 verdicts/：目录存在且有读取说明 | 复核/终裁文件不存在：docs/acceptance/verdicts/README.md（文件未生成，故无独立复核结论可引用） | - |
| D0-030 | 逐项终裁 verdicts/：每个 PASS 有对应 verdict JSON | 复核/终裁文件不存在：docs/acceptance/verdicts/README.md（文件未生成，故无独立复核结论可引用） | - |
| D0-031 | 逐项终裁 verdicts/：verdict JSON 绑定 run-id | 复核/终裁文件不存在：docs/acceptance/verdicts/README.md（文件未生成，故无独立复核结论可引用） | - |
| D0-032 | 逐项终裁 verdicts/：reviewer 与 authors 不相交 | 复核/终裁文件不存在：docs/acceptance/verdicts/README.md（文件未生成，故无独立复核结论可引用） | - |
| D0-033 | 逐项终裁 verdicts/：evidence 列表含 sha256 | 复核/终裁文件不存在：docs/acceptance/verdicts/README.md（文件未生成，故无独立复核结论可引用） | - |
| D0-034 | 逐项终裁 verdicts/：不可伪造字段（run_id/ledger_sha256）存在 | 复核/终裁文件不存在：docs/acceptance/verdicts/README.md（文件未生成，故无独立复核结论可引用） | - |
| D0-035 | 逐项终裁 verdicts/：JSON 可被 schema 校验 | 复核/终裁文件不存在：docs/acceptance/verdicts/README.md（文件未生成，故无独立复核结论可引用） | - |
| D0-036 | 追溯 trace_requirements.py：需求 id 能定位到台账行 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md | - |
| D0-037 | 追溯 trace_requirements.py：方法里的测试路径必须存在 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md | - |
| D0-038 | 追溯 trace_requirements.py：证据路径必须存在 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md | - |
| D0-039 | 追溯 trace_requirements.py：`<...>` 占位符进入缺口清单 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md | - |
| D0-040 | 追溯 trace_requirements.py：设备方法必须有 device 证据 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md | - |
| D0-041 | 追溯 trace_requirements.py：PASS 行必须有 test/device 落点 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md | - |
| D0-042 | 追溯 trace_requirements.py：输出 JSON 与稳定 exit code | 证据列空、未回填；方法声明：cmd: python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md | - |
| D0-043 | 蜕变 metamorphic_checks.py：渲染关系：reflow 后规范化文本不变 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/metamorphic_checks.py --demo | - |
| D0-044 | 蜕变 metamorphic_checks.py：导出关系：追加条目页数不减且前缀保留 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/metamorphic_checks.py --demo | - |
| D0-045 | 蜕变 metamorphic_checks.py：检索关系：追加无关文档不改变既有分数 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/metamorphic_checks.py --demo | - |
| D0-046 | 蜕变 metamorphic_checks.py：调度关系：追加逾期复习不缩短间隔 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/metamorphic_checks.py --demo | - |
| D0-047 | 蜕变 metamorphic_checks.py：每条关系有可执行变换与必守断言 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/metamorphic_checks.py --demo | - |
| D0-048 | 蜕变 metamorphic_checks.py：关系失败输出反例而不只是 false | 证据列空、未回填；方法声明：cmd: python -I tools/ci/metamorphic_checks.py --demo | - |
| D0-049 | 蜕变 metamorphic_checks.py：缺真实夹具时 exit 2，不得判 PASS | 证据列空、未回填；方法声明：cmd: python -I tools/ci/metamorphic_checks.py --demo | - |
| D0-050 | 截图 screenshot_baseline.py：截图采集记录 serial 与时间 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/screenshot_baseline.py compare --baseline build/a.png --current build/b.png | - |
| D0-051 | 截图 screenshot_baseline.py：基线文件记录 sha256 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/screenshot_baseline.py compare --baseline build/a.png --current build/b.png | - |
| D0-052 | 截图 screenshot_baseline.py：PNG 解码支持 8-bit RGB/RGBA 非隔行 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/screenshot_baseline.py compare --baseline build/a.png --current build/b.png | - |
| D0-053 | 截图 screenshot_baseline.py：像素差 MAE 与变化像素比例可计算 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/screenshot_baseline.py compare --baseline build/a.png --current build/b.png | - |
| D0-054 | 截图 screenshot_baseline.py：阈值必须显式给出 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/screenshot_baseline.py compare --baseline build/a.png --current build/b.png | - |
| D0-055 | 截图 screenshot_baseline.py：超阈值 exit 1 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/screenshot_baseline.py compare --baseline build/a.png --current build/b.png | - |
| D0-056 | 截图 screenshot_baseline.py：报告 JSON 含 baseline/current 哈希 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/screenshot_baseline.py compare --baseline build/a.png --current build/b.png | - |
| D0-057 | 无障碍 a11y_audit.py：uiautomator 语义树可导出/离线解析 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/a11y_audit.py audit --dump build/window_dump.xml | - |
| D0-058 | 无障碍 a11y_audit.py：可点击节点触摸目标 ≥48dp | 证据列空、未回填；方法声明：cmd: python -I tools/ci/a11y_audit.py audit --dump build/window_dump.xml | - |
| D0-059 | 无障碍 a11y_audit.py：可点击节点必须有 text 或 content-desc | 证据列空、未回填；方法声明：cmd: python -I tools/ci/a11y_audit.py audit --dump build/window_dump.xml | - |
| D0-060 | 无障碍 a11y_audit.py：对比度缺颜色输入时标 UNVERIFIED | 证据列空、未回填；方法声明：cmd: python -I tools/ci/a11y_audit.py audit --dump build/window_dump.xml | - |
| D0-061 | 无障碍 a11y_audit.py：WCAG 对比度 <4.5 判 FAIL | 证据列空、未回填；方法声明：cmd: python -I tools/ci/a11y_audit.py audit --dump build/window_dump.xml | - |
| D0-062 | 无障碍 a11y_audit.py：违规 exit 1 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/a11y_audit.py audit --dump build/window_dump.xml | - |
| D0-063 | 无障碍 a11y_audit.py：报告 JSON 含节点计数与违规码 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/a11y_audit.py audit --dump build/window_dump.xml | - |
| D0-064 | CI 挂载 acceptance_ci.patch：patch 只是提案，不改实际 workflow | 证据列空、未回填；方法声明：file: tools/ci/acceptance_ci.patch | - |
| D0-065 | CI 挂载 acceptance_ci.patch：挂载 report 步骤 | 证据列空、未回填；方法声明：file: tools/ci/acceptance_ci.patch | - |
| D0-066 | CI 挂载 acceptance_ci.patch：挂载 lint 步骤 | 证据列空、未回填；方法声明：file: tools/ci/acceptance_ci.patch | - |
| D0-067 | CI 挂载 acceptance_ci.patch：挂载 trace 步骤 | 证据列空、未回填；方法声明：file: tools/ci/acceptance_ci.patch | - |
| D0-068 | CI 挂载 acceptance_ci.patch：失败阻断后续步骤 | 证据列空、未回填；方法声明：file: tools/ci/acceptance_ci.patch | - |
| D0-069 | CI 挂载 acceptance_ci.patch：与现有 workflow 无写冲突 | 证据列空、未回填；方法声明：file: tools/ci/acceptance_ci.patch | - |
| D0-070 | CI 挂载 acceptance_ci.patch：git apply --check 可验证 | 证据列空、未回填；方法声明：file: tools/ci/acceptance_ci.patch | - |
| D0-071 | 运行手册 runbook.md：复跑命令按域列出 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| D0-072 | 运行手册 runbook.md：PASS/FAIL/BLOCKED/UNVERIFIED 判据 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| D0-073 | 运行手册 runbook.md：证据归档路径与命名 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| D0-074 | 运行手册 runbook.md：设备证据必须绑 APK sha 与源快照 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| D0-075 | 运行手册 runbook.md：不可自动化的残余人工项声明 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| D0-076 | 运行手册 runbook.md：无外部条件时的 BLOCKED 出口 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| D0-077 | 终裁协议：主会话签字字段 | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-078 | 终裁协议：run-id 与台账 sha256 绑定 | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-079 | 终裁协议：不可伪造字段清单 | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-080 | 终裁协议：独立复核 + 对抗复核双签 | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-081 | 终裁协议：冲突时保留双方判词 | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-082 | 终裁协议：终裁文件归档路径 | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-083 | 拒绝伪造清单：reviewer 不得给自己签字 | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-084 | 拒绝伪造清单：证据不得引用 summary/批次记录 | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-085 | 拒绝伪造清单：截图证据必须绑 serial + APK sha | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-086 | 拒绝伪造清单：命令日志必须含可读 exit | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-087 | 拒绝伪造清单：设备证据必须含 serial | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-088 | 拒绝伪造清单：UNVERIFIED 不得转写为 PASS | 证据列空、未回填；方法声明：file: docs/acceptance/dod.md | - |
| D0-089 | 证据哈希清单：所有 E 文件有 sha256 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_hash_manifest.py --root docs/research/evidence/2026-11-05-batch-44 --out build/acceptance/evidence-sha256.txt --self-hash | - |
| D0-090 | 证据哈希清单：清单自身有 sha256 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_hash_manifest.py --root docs/research/evidence/2026-11-05-batch-44 --out build/acceptance/evidence-sha256.txt --self-hash | - |
| D0-091 | 证据哈希清单：重算命令可复跑 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_hash_manifest.py --root docs/research/evidence/2026-11-05-batch-44 --out build/acceptance/evidence-sha256.txt --self-hash | - |
| D0-092 | 证据哈希清单：缺失哈希即 fail | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_hash_manifest.py --root docs/research/evidence/2026-11-05-batch-44 --out build/acceptance/evidence-sha256.txt --self-hash | - |
| D0-093 | 证据哈希清单：路径使用仓库相对 POSIX 形式 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_hash_manifest.py --root docs/research/evidence/2026-11-05-batch-44 --out build/acceptance/evidence-sha256.txt --self-hash | - |
| D0-094 | 证据哈希清单：清单不含密钥/令牌 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_hash_manifest.py --root docs/research/evidence/2026-11-05-batch-44 --out build/acceptance/evidence-sha256.txt --self-hash | - |
| D0-095 | 历史回放演练：批次 41/42 假绿夹具存在 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_lint.py --ledger tools/tests/fixtures/acceptance/fake_green_batch42.ledger.md | - |
| D0-096 | 历史回放演练：report 对夹具 exit 1 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_lint.py --ledger tools/tests/fixtures/acceptance/fake_green_batch42.ledger.md | - |
| D0-097 | 历史回放演练：lint 对夹具 exit 1 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_lint.py --ledger tools/tests/fixtures/acceptance/fake_green_batch42.ledger.md | - |
| D0-098 | 历史回放演练：回放命令写入 runbook | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_lint.py --ledger tools/tests/fixtures/acceptance/fake_green_batch42.ledger.md | - |
| D0-099 | 历史回放演练：回放结果归档 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_lint.py --ledger tools/tests/fixtures/acceptance/fake_green_batch42.ledger.md | - |
| D0-100 | 历史回放演练：不得为让夹具变绿而放宽阈值 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_lint.py --ledger tools/tests/fixtures/acceptance/fake_green_batch42.ledger.md | - |
| A-005 | 旅程「拍照→分题→入库」：大数据 | 无大体积成功入库用例；只有 6 文件批量与 31 文件拒绝边界 | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/SharedCaptureEntryInstrumentedTest.kt |
| A-006 | 旅程「拍照→分题→入库」：权限拒绝 | 录入链无运行时权限（manifest 未声明 CAMERA；分享/选图走系统选择器） | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/CaptureShareIntakeInstrumentedTest.kt |
| A-014 | 旅程「讲题」：大数据 | 无长对话/大上下文设备用例 | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/RootTutorFailClosedInstrumentedTest.kt |
| A-015 | 旅程「讲题」：权限拒绝 | 讲题链无运行时权限；Provider 缺失属 BLOCKED 不是权限拒绝 | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/RootTutorFailClosedInstrumentedTest.kt |
| A-023 | 旅程「复习三键」：大数据 | 大复习队列容量由 E 域规划器覆盖，A 域无设备大样本 | file:core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlanner3EScanInstrumentedTest.kt |
| A-024 | 旅程「复习三键」：权限拒绝 | 复习三键无运行时权限 | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/ReviewSelfReportDeviceUiInstrumentedTest.kt |
| A-031 | 旅程「详情编辑」：空态 | 详情页无独立空态（必须有一条已保存题） | file:feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeDetailRoute.kt |
| A-032 | 旅程「详情编辑」：大数据 | 无大题量详情/修订历史设备用例 | file:core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeDetailRepositoryTest.kt |
| A-033 | 旅程「详情编辑」：权限拒绝 | 详情编辑无运行时权限 | file:core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/mistake/MistakeRevisionEditInstrumentedTest.kt |
| A-041 | 旅程「导出 PDF/DOCX」：大数据 | 100 项只验证 WorkManager 输入编码上限，未做端到端大页数渲染；oversized 列表用例是 fail-closed 边界，不是成功大数据 | file:core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfBatchEligibilityTest.kt |
| A-049 | 旅程「备份/恢复」：空态 | 无空库备份/空选择组合的设备用例（选择面已由 r6-backup 覆盖） | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/BackupSelectionUiInstrumentedTest.kt |
| A-050 | 旅程「备份/恢复」：大数据 | 365MiB 全量/子集备份恢复 1800s 未完成（recorder 返回 124）；未取得用例判词，不按“大库通过”记; device: python -I tools/ci/am_instrument_recorder.py --serial emulator-5584 --package com.tingyun.smartmistakebook.core.data.test --class com.tingyun.smartmistakebook.core.data.backup.BackupSelect… | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r6-backup-scale.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r6-backup-scale.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r6-backup-scale.log |
| A-051 | 旅程「备份/恢复」：权限拒绝 | 备份/恢复走 SAF，无运行时权限可撤销 | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/BackupSelectionUiInstrumentedTest.kt |
| A-058 | 旅程「知识库浏览」：空态 | 知识点空态文案未断言；只有跳过唯一节点的完成态用例 | file:feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/KnowledgeReviewSessionScreen.kt |
| A-059 | 旅程「知识库浏览」：大数据 | 无大知识库浏览设备样本 | file:core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalInstrumentedTest.kt |
| A-060 | 旅程「知识库浏览」：权限拒绝 | 知识库浏览无运行时权限 | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/KnowledgeReviewSessionInstrumentedTest.kt |
| A-065 | 旅程「掌握度」：失败路径 | 掌握度无失败路径设备用例 | file:app/src/main/kotlin/com/tingyun/smartmistakebook/LearningMasteryScreen.kt |
| A-066 | 旅程「掌握度」：中断恢复 | 掌握度无中断恢复设备用例 | file:app/src/main/kotlin/com/tingyun/smartmistakebook/LearningMasteryScreen.kt |
| A-068 | 旅程「掌握度」：大数据 | 无大数据量掌握度用例 | file:core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/MasteryIntervalMappingTest.kt |
| A-069 | 旅程「掌握度」：权限拒绝 | 掌握度无运行时权限 | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/LearningMasteryScreenInstrumentedTest.kt |
| A-077 | 旅程「提醒」：大数据 | 无大 pendingCount 通知文案/裁剪用例 | file:app/src/test/kotlin/com/tingyun/smartmistakebook/ReviewReminderCoordinatorTest.kt |
| A-086 | 旅程「设置」：大数据 | 无大考试日历/大设置项设备用例 | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/SchedulingSettingsInstrumentedTest.kt |
| A-087 | 旅程「设置」：权限拒绝 | 设置页无运行时权限（BYOK 密钥不是 Android 权限） | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/CapabilityScreenInstrumentedTest.kt |
| B-077 | 备份 mask=13 状态=partial：UNVERIFIED 现有夹具只实例化 mask=9/15 的 partial 状态 | 需要 mask=13 的半包/部分写失败夹具；现有 partial 机制见 B-074/B-080 | file:core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSelectionRestoreInstrumentedTest.kt |
| B-078 | 备份 mask=13 状态=corrupt：UNVERIFIED 现有 corrupt 夹具均为 mask=9/15 | 需要 mask=13 篡改包；共享完整性门见 B-075/B-081 | file:core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSelectionRestoreInstrumentedTest.kt |
| C-005 | SceneV1 绘制原语 Save：未闭合 save 必须拒绝（生成门与回放门同码） | 仅 JVM VisualDrawOpsValidatorTest 覆盖；Gradle 只允许门禁阶段串行运行，本域未跑 | file:core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsValidatorTest.kt |
| C-008 | SceneV1 绘制原语 Concat：嵌套组 N·M 后乘合成且可逆（det≥1e-12） | 蜕变库 R-03 自记“嵌套合成与 det 乘法未测”；本域无第二类证据 | file:docs/research/2026-11-06-metamorphic-relations.md; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log |
| C-010 | SceneV1 绘制原语 Concat：singular 与 composed-bounds 矩阵拒绝 | Node 台有 reject/matrix/singular 与 composed-bounds，但只有 cmd 一类证据；设备侧无对应拒绝用例 | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log |
| C-015 | SceneV1 绘制原语 Clip：未知 fill rule 拒绝 | Node 台有 reject/style/hitrule-valueOf，但只有 cmd 一类证据；设备侧无对应拒绝用例 | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log |
| C-020 | SceneV1 绘制原语 DrawPath：路径不以 M 开头或未知命令拒绝 | Node 台有 reject/path/not-m-start 与 unknown-cmd，但只有 cmd 一类证据；设备侧无对应拒绝用例 | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log |
| C-045 | 字体身份/版本：renderer/字体版本参与快照身份摘要 | 仅 JVM TutorVisualIdentityTest 覆盖（core:database）；本域未跑 Gradle | file:core/database/src/test/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualIdentityTest.kt |
| C-066 | 未知字段拒绝：SCENE_FIELD_UNKNOWN（含 __proto__/constructor 污染键） | Node 台 reject/field/unknown 已过，但只有 cmd 一类证据；设备侧只覆盖 schemaVersion 拒绝 | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log |
| C-067 | 未知节点类型拒绝：SCENE_NODE_TYPE（rect 不在 V1 词表） | Node 台 reject/node/unknown-type 已过，但只有 cmd 一类证据 | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log |
| C-068 | 未知路径命令拒绝：非 M 开头/未知命令码 | Node 台 reject/path/not-m-start 与 unknown-cmd 已过，但只有 cmd 一类证据 | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log |
| C-071 | 预算 fail-closed：单事务累计像素 ≤4 MP、单 bitmap ≤16 MiB（4 MP 半边） | 4 MP 像素门仅 JVM VisualLayoutBudgetTest 覆盖；设备侧只跑 16 MiB 字节门 | file:core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualLayoutBudgetTest.kt |
| C-088 | hitRegions：屏幕回放不绘制命中区 | 无专项屏幕用例；PDF 侧 pdfDoesNotPaintHitRegions 只证 PDF 半边 | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.log |
| C-089 | hitRegions：数量上限 128（129 拒绝） | Node 台 reject/budget/hit-regions-129 已过，但只有 cmd 一类证据 | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log |
| E-007 | UNVERIFIED: FSRS 并发。无并发调用 FSRS 的用例；算法是纯函数、无共享可变状态，但并发关系未测 | 缺并发用例。升级路径：新增多线程调用 FsrsScheduleMath 的一致性用例 | file:core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/FsrsScheduleMath.kt |
| E-016 | UNVERIFIED: 容量总量并发。纯策略无共享状态，但无并发调用用例 | 缺并发用例。升级路径：新增多线程调用 ReviewCapacityPolicy 的一致性用例 | file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacityPolicy.kt |
| E-025 | UNVERIFIED: 考试配额并发。纯函数无共享状态，但无并发调用用例 | 缺并发用例。升级路径：新增多线程调用 ExamQuotaPlanner.plan 的确定性用例 | file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ExamQuotaPlanner.kt |
| E-034 | UNVERIFIED: 欠账再平衡并发。纯函数无共享状态，但无并发调用用例 | 缺并发用例。升级路径：新增多线程调用 MissedDayRebalancePolicy.plan 的确定性用例 | file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/MissedDayRebalancePolicy.kt |
| E-043 | UNVERIFIED: 掌握判定并发。投影器幂等性已测，但无并发投影用例 | 缺并发用例。升级路径：新增同 learner 并发投影的幂等与冲突用例 | file:core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/LearningProjector.kt |
| E-052 | UNVERIFIED: 错因候选并发。提取器无并发用例 | 缺并发用例。升级路径：新增并发提取同 scope 候选的唯一性用例 | file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ErrorCauseExtractor.kt |
| E-061 | UNVERIFIED: 错因落账并发。JVM 用例用假端口，不模拟 SQL 事务；真 SQLite 事务由设备用例覆盖，本轮未跑设备 | 设备用例 ErrorCauseLedgerInstrumentedTest 本轮未执行。升级路径：门禁阶段在 emulator 上跑该用例 | file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ErrorCauseBookingService.kt |
| E-070 | UNVERIFIED: 错因撤销并发。无并发撤回用例；真事务由设备用例覆盖，本轮未跑 | 缺并发用例与设备事务证据。升级路径：新增并发撤回与落账竞争用例 | file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ErrorCauseRetractor.kt |
| E-079 | UNVERIFIED: 排序稳定并发。无并发规划用例 | 缺并发用例。升级路径：新增同快照并发 plan 的确定性用例 | file:core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ReviewPlannerV2.kt |
| E-086 | UNVERIFIED: DST 切换。StudyDayMathTest 仅用 America/New_York 冬季 EST 探针，无 2026-03-08 春季跳变用例 | 缺 DST 切换用例。升级路径：新增 America/New_York 2026-03-08 02:00 前后与 2026-11-01 的探针 | file:core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/StudyDayMathTest.kt |
| F-002 | 冷启动：阈值 | 对照 PerformanceTargets.kt:18 的 2000ms；模拟器冷启 3001-3939ms，目标只对真机成立，不在模拟器判产品红绿 | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json |
| F-009 | 冷启动：回归 | 无同设备同构建历史基线；批次43 基线在 emulator-5584，不同实例 | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json |
| F-011 | warm-up：阈值 | 无 warm-up 数值目标；只记录收敛量，不伪造阈值 | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json |
| F-012 | warm-up：超限 | 无 warm-up 阈值，无法判超限 | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json |
| F-018 | warm-up：回归 | 无同设备同构建历史 warm-up 基线 | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json |
| F-020 | 首帧：阈值 | 500ms 首屏目标只对真机成立；模拟器 7/7 轮 janky，不作产品判定 | device:docs/research/evidence/2026-11-05-batch-44/f-domain/gfxinfo.txt |
| F-027 | 首帧：回归 | 无同设备历史首帧基线 | device:docs/research/evidence/2026-11-05-batch-44/f-domain/gfxinfo.txt |
| F-038 | 预览耗时：阈值 | 无产品预览时延目标；只建基线 | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/preview.cmd.log |
| F-039 | 预览耗时：超限 | 无预览阈值，无法判超限 | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/preview.cmd.log |
| F-047 | 内存峰值：阈值 | 200MB 目标未定义进程/相位；产品 UI 仪器化宿主峰值 316MB 不可归因 | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/memory.cmd.log |
| F-048 | 内存峰值：超限 | 无干净的产品进程内导出相位峰值，无法判超限 | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/memory.cmd.log |
| F-054 | 内存峰值：回归 | 无同设备历史 PSS 基线；批次43 为 emulator-5584 且口径不同 | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/memory.cmd.log |
| F-056 | 长时 2h：阈值 | 无 2h 数值阈值；以无崩溃/ANR、PSS 有界为观察 | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.cmd.log |
| F-058 | 长时 2h：重复运行 | 单次 2h，未重复第二窗 | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.cmd.log |
| F-063 | 长时 2h：回归 | 无历史 2h soak 基线 | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.cmd.log |
| F-072 | ANR/崩溃签名：回归 | 无历史 ANR/crash 基线可比 | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/anr-crash.cmd.log |
| F-074 | 进程死亡恢复：阈值 | 无进程死亡恢复时延目标 | device:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death.device.json |
| F-081 | 进程死亡恢复：回归 | 无历史进程死亡恢复基线 | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death.cmd.log |
| F-083 | 后台限制/低电低存/多任务：阈值 | 无后台限制时延目标；只建基线 | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/background.cmd.log |
| F-084 | 后台限制/低电低存/多任务：超限 | 低存储尝试：/data 98% 时启动 PID 存在、FATAL/ENOSPC 0，但 UI 未确认，仍 UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/background.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/low-storage-5562.cmd.log |
| F-085 | 后台限制/低电低存/多任务：重复运行 | 每场景 1 次，未重复 | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/background.cmd.log |
| F-090 | 后台限制/低电低存/多任务：回归 | 无历史后台限制基线 | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/background.cmd.log |
| G-016 | 崩溃与 ANR trace 不含密钥 | 证据列空、未回填；方法声明：未触发崩溃采集，trace 文件未取得 | - |
| G-036 | 运行时真实外发（真实 Provider） | 证据列空、未回填；方法声明：无 Provider 凭据，未执行，指向验收 runbook | - |
| G-072 | 对抗：构造穿越分享 URI | 证据列空、未回填；方法声明：源码级 canonical 守卫已断言，但无法执行 Kotlin 构造穿越 URI | - |
| G-080 | 配置页截图应为黑屏 | 证据列空、未回填；方法声明：需导航到配置页后截图比对；本次未执行 | - |
| G-081 | 剪贴板是否带敏感标志（Android 13 预览策略） | 证据列空、未回填；方法声明：代码未设置 EXTRA_IS_SENSITIVE；是否构成泄露需产品裁定 | - |
| G-089 | Gradle 依赖校验元数据 | 证据列空、未回填；方法声明：gradle/verification-metadata.xml 不存在，供应链校验未启用 | - |
| H-063 | 导出水印：设备上字号、对比度、不遮挡经人工视觉复核 | 仅单类或未执行到判定口；方法：python -I tools/ci/h_compliance_gate.py --only H-063 UNVERIFIED: 需真实 PDF 截图与人工审美判断，不由自动化签字 | cmd:docs/acceptance/evidence/H/logs/h-063.log#exit=1; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaption.kt |
| H-075 | i18n：界面文案已抽取为资源 | 仅单类或未执行到判定口；方法：python -I tools/ci/h_compliance_gate.py --only H-075 UNVERIFIED: 47 个主源码文件含硬编码 Text 字面量；产品当前仅中文，未定义本地化要求 | cmd:docs/acceptance/evidence/H/logs/h-075.log#exit=1; file:app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt |
| H-076 | i18n：RTL 布局与文案已走查 | 仅单类或未执行到判定口；方法：python -I tools/ci/h_compliance_gate.py --only H-076 UNVERIFIED: supportsRtl=true 但无 RTL 布局或文案走查证据 | cmd:docs/acceptance/evidence/H/logs/h-076.log#exit=1; file:app/src/main/AndroidManifest.xml |
| H-077 | i18n：日期格式使用显式中文 Locale | 仅单类或未执行到判定口；方法：python -I tools/ci/h_compliance_gate.py --only H-077 UNVERIFIED: 多处 SimpleDateFormat 与中文格式串硬编码，随系统 locale 变化未验证 | cmd:docs/acceptance/evidence/H/logs/h-077.log#exit=1; file:feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeDetailRoute.kt |
| H-080 | i18n：系统 locale 变更后的行为已验证 | 仅单类或未执行到判定口；方法：python -I tools/ci/h_compliance_gate.py --only H-080 UNVERIFIED: 无应用内语言切换，系统 locale 变更未测 | cmd:docs/acceptance/evidence/H/logs/h-080.log#exit=1; file:feature/profile/src/main/java/com/tingyun/smartmistakebook/feature/profile/ProfileRoute.kt |
| H-089 | 中文排版：错误文案语感与信息密度经人工复核 | 仅单类或未执行到判定口；方法：python -I tools/ci/h_compliance_gate.py --only H-089 UNVERIFIED: 语感类无法编码，保留人工残余 | cmd:docs/acceptance/evidence/H/logs/h-089.log#exit=1; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt |
| H-090 | 中文排版：设备字体回退、标点宽度与暗色可读性经走查 | 仅单类或未执行到判定口；方法：python -I tools/ci/h_compliance_gate.py --only H-090 UNVERIFIED: 需设备截图与 TalkBack 走查，未执行 | cmd:docs/acceptance/evidence/H/logs/h-090.log#exit=1; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaption.kt |
| I-005 | TalkBack 语义：残余——朗读内容是否准确、语感是否自然 | 需人工听读，自动化不判定（见 runbook 第 167 行） | file:docs/acceptance/runbook.md |
| I-009 | TalkBack 语义：独立复核人对语义与朗读做对抗复核 | 待独立复核人签字，不由实现者自证 | file:docs/acceptance/runbook.md |
| I-014 | 触摸目标 48dp：残余——目标间距、重叠与手势冲突不在语义树可判定范围 | 语义树无间距与遮挡信息 | file:docs/acceptance/runbook.md |
| I-018 | 触摸目标 48dp：独立复核人对目标尺寸做对抗复核 | 待独立复核人签字 | file:docs/acceptance/runbook.md |
| I-023 | 对比度：残余——图片、公式、渲染图内文本的对比度 | 语义树不含像素颜色 | file:docs/acceptance/runbook.md |
| I-025 | 对比度：截图逐像素对比不在本批，登记为未验证而非通过 | 未做截图逐像素采样 | file:docs/acceptance/runbook.md |
| I-027 | 对比度：独立复核人对配色对与阈值做对抗复核 | 待独立复核人签字 | file:docs/acceptance/runbook.md |
| I-030 | 字号 200%：负向——文本裁剪与溢出没有自动判定手段 | 语义树不含裁剪信息，需视觉走查 | file:docs/acceptance/runbook.md |
| I-032 | 字号 200%：残余——截断、重叠与排版的视觉判断 | 需人工视觉复核 | file:docs/acceptance/runbook.md |
| I-036 | 字号 200%：独立复核人对大字号布局做对抗复核 | 待独立复核人签字 | file:docs/acceptance/runbook.md |
| I-039 | 暗色：负向——源码无 darkColorScheme/isSystemInDarkTheme（MATCHES=0），不能声明深色主题适配 | grep -rn darkColorScheme --include=*.kt app/src/main core feature | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-scheme-scan.log#exit=0; file:core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/Theme.kt |
| I-041 | 暗色：残余——夜间舒适度、色温与审美 | 人类主观判断 | file:docs/acceptance/runbook.md |
| I-043 | 暗色：深色主题未实现属产品决策缺口，非外部阻塞，登记未验证 | 无产品合同要求深色主题 | file:docs/acceptance/runbook.md; file:core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/Theme.kt |
| I-045 | 暗色：独立复核人对昼夜两种设置做对抗复核 | 待独立复核人签字 | file:docs/acceptance/runbook.md |
| I-050 | 焦点顺序：残余——TalkBack 滑动遍历顺序与键盘 TAB 顺序不同 | 需硬件驱动回传无障碍焦点序列 | file:docs/acceptance/runbook.md |
| I-054 | 焦点顺序：独立复核人对顺序合理性做对抗复核 | 待独立复核人签字 | file:docs/acceptance/runbook.md |
| I-059 | 空态可读：残余——空态文案的语感与安抚程度 | 人类语感判断 | file:docs/acceptance/runbook.md |
| I-063 | 空态可读：独立复核人对空态与错误文案做对抗复核 | 待独立复核人签字 | file:docs/acceptance/runbook.md |
| I-066 | 键盘：负向——中文无法经 adb input text 注入，工具以 exit 2 拒绝而非装作通过 | 中文输入走 instrumented 用例，本批未覆盖 | file:docs/acceptance/runbook.md |
| I-068 | 键盘：残余——输入法候选栏对内容的遮挡 | 语义树不含 IME 面板 | file:docs/acceptance/runbook.md |
| I-072 | 键盘：独立复核人对输入与提交行为做对抗复核 | 待独立复核人签字 | file:docs/acceptance/runbook.md |
| I-077 | 横竖屏：残余——横屏布局的视觉平衡与信息密度 | 人类视觉判断 | file:docs/acceptance/runbook.md |
| I-081 | 横竖屏：独立复核人对横屏布局做对抗复核 | 待独立复核人签字 | file:docs/acceptance/runbook.md |
| I-082 | 人工语感残余：TalkBack 朗读语速与断句 | 屏幕阅读器语感与审美无法自动判定（见 runbook） | file:docs/acceptance/runbook.md |
| I-083 | 人工语感残余：图标与插画的表意清晰度 | 屏幕阅读器语感与审美无法自动判定（见 runbook） | file:docs/acceptance/runbook.md |
| I-084 | 人工语感残余：动效的可感知性与眩晕风险 | 屏幕阅读器语感与审美无法自动判定（见 runbook） | file:docs/acceptance/runbook.md |
| I-085 | 人工语感残余：空态与错误文案的语气 | 屏幕阅读器语感与审美无法自动判定（见 runbook） | file:docs/acceptance/runbook.md |
| I-086 | 人工语感残余：颜色语义与文化适配 | 屏幕阅读器语感与审美无法自动判定（见 runbook） | file:docs/acceptance/runbook.md |
| I-087 | 人工语感残余：字号与行距的阅读舒适度 | 屏幕阅读器语感与审美无法自动判定（见 runbook） | file:docs/acceptance/runbook.md |
| I-088 | 人工语感残余：横屏信息密度的主观平衡 | 屏幕阅读器语感与审美无法自动判定（见 runbook） | file:docs/acceptance/runbook.md |
| I-089 | 人工语感残余：控件分组与朗读停顿 | 屏幕阅读器语感与审美无法自动判定（见 runbook） | file:docs/acceptance/runbook.md |
| I-090 | 人工语感残余：整体无障碍体验的验收意见 | 屏幕阅读器语感与审美无法自动判定（见 runbook） | file:docs/acceptance/runbook.md |
| J-003 | 干净树构建 release APK（Gradle 门禁阶段） | 仅单类或未执行到判定口；方法：cmd: ./gradlew :app:assembleStrictOfflineRelease --offline | file:docs/acceptance/runbook.md |
| J-007 | 构建确定性：两次 clean 构建字节一致 | 仅单类或未执行到判定口；方法：cmd: 两次 clean assemble 后 cmp（门禁阶段） | file:docs/acceptance/runbook.md |
| J-008 | 独立复核：干净树面 | 复核/终裁文件不存在：非作者 reviewer 终裁（文件未生成，故无独立复核结论可引用） | file:docs/acceptance/runbook.md |
| J-009 | runbook 覆盖干净树构建 | 仅单类或未执行到判定口；方法：cmd: grep -n "J-003" docs/acceptance/runbook.md | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-runbook-coverage.log#exit=0; file:docs/acceptance/runbook.md |
| J-016 | AAB 内嵌 proguard.map 与 r8.json 存在 | 仅单类或未执行到判定口；方法：cmd: 读取 AAB 的 BUNDLE-METADATA 目录 | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-aab-metadata.log#exit=0 |
| J-018 | 独立复核：R8/mapping 面 | 复核/终裁文件不存在：非作者 reviewer 终裁（文件未生成，故无独立复核结论可引用） | file:docs/acceptance/runbook.md |
| J-022 | 负向：缺签名变量时构建必须失败 | 仅单类或未执行到判定口；方法：cmd: ./gradlew validateReleaseSigning（门禁阶段） | file:app/build.gradle.kts; file:docs/acceptance/runbook.md |
| J-023 | 签名方案 v1 与 v2 覆盖 minSdk 23 | 仅单类或未执行到判定口；方法：cmd: apksigner verify --print-certs --verbose | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-signing.log#exit=0 |
| J-026 | 独立复核：签名/AAB 面 | 复核/终裁文件不存在：非作者 reviewer 终裁（文件未生成，故无独立复核结论可引用） | file:docs/acceptance/runbook.md |
| J-036 | 独立复核：16KB/AAB 面 | 复核/终裁文件不存在：非作者 reviewer 终裁（文件未生成，故无独立复核结论可引用） | file:docs/acceptance/runbook.md |
| J-044 | arm64 结果不得跨设备外推 | 仅单类或未执行到判定口；方法：cmd: adb getprop ro.product.cpu.abi | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-probe.log#exit=0 |
| J-045 | arm64 runbook 覆盖 | 仅单类或未执行到判定口；方法：file: docs/acceptance/runbook.md §9.2 | file:docs/acceptance/runbook.md |
| J-049 | 负向：同 vc 不同构建必须被检出 | 仅单类或未执行到判定口；方法：cmd: 缺少跨产物 vc 碰撞检测器 | file:docs/acceptance/runbook.md |
| J-053 | 独立复核：版本号面 | 复核/终裁文件不存在：非作者 reviewer 终裁（文件未生成，故无独立复核结论可引用） | file:docs/acceptance/runbook.md |
| J-054 | 版本 runbook 覆盖 | 仅单类或未执行到判定口；方法：file: docs/acceptance/runbook.md §9.2 | file:docs/acceptance/runbook.md |
| J-063 | 独立复核：升级/降级面 | 复核/终裁文件不存在：非作者 reviewer 终裁（文件未生成，故无独立复核结论可引用） | file:docs/acceptance/runbook.md |
| J-064 | 回滚文档：可覆盖且迁移非破坏 | 仅单类或未执行到判定口；方法：cmd: grep -n "## Rollback" RELEASE.md | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-rollback-doc.log#exit=0; file:RELEASE.md |
| J-066 | 迁移非破坏（备份先行与迁移矩阵） | 依赖 B 域迁移矩阵，本域不单独外推 | file:docs/acceptance/runbook.md |
| J-067 | 负向：破坏性迁移不得发布 | 流程约束，无本域可执行判据 | file:RELEASE.md |
| J-070 | 回归：回滚演练 | 仅单类或未执行到判定口；方法：cmd: 成对包回滚演练（门禁阶段） | file:docs/acceptance/runbook.md |
| J-071 | 独立复核：回滚面 | 复核/终裁文件不存在：非作者 reviewer 终裁（文件未生成，故无独立复核结论可引用） | file:docs/acceptance/runbook.md |
| J-072 | 回滚 runbook 覆盖 | 仅单类或未执行到判定口；方法：file: docs/acceptance/runbook.md §9.2 | file:docs/acceptance/runbook.md |
| FIX-001 | 修复「数据迁移」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *数据迁移* | - |
| FIX-002 | 修复「数据迁移」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *数据迁移* | - |
| FIX-003 | 修复「数据迁移」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest | - |
| FIX-004 | 修复「数据迁移」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-005 | 修复「数据迁移」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-006 | 修复「备份恢复」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *备份恢复* | - |
| FIX-007 | 修复「备份恢复」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *备份恢复* | - |
| FIX-008 | 修复「备份恢复」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest | - |
| FIX-009 | 修复「备份恢复」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-010 | 修复「备份恢复」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-011 | 修复「并发事务」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *并发事务* | - |
| FIX-012 | 修复「并发事务」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *并发事务* | - |
| FIX-013 | 修复「并发事务」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest | - |
| FIX-014 | 修复「并发事务」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-015 | 修复「并发事务」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-016 | 修复「渲染确定性」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:export:testDebugUnitTest --tests *渲染确定性* | - |
| FIX-017 | 修复「渲染确定性」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:export:testDebugUnitTest --tests *渲染确定性* | - |
| FIX-018 | 修复「渲染确定性」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:export:testDebugUnitTest | - |
| FIX-019 | 修复「渲染确定性」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-020 | 修复「渲染确定性」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-021 | 修复「导出 PDF」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:export:testDebugUnitTest --tests *导出 PDF* | - |
| FIX-022 | 修复「导出 PDF」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:export:testDebugUnitTest --tests *导出 PDF* | - |
| FIX-023 | 修复「导出 PDF」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:export:testDebugUnitTest | - |
| FIX-024 | 修复「导出 PDF」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-025 | 修复「导出 PDF」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-026 | 修复「导出 DOCX」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:export:testDebugUnitTest --tests *导出 DOCX* | - |
| FIX-027 | 修复「导出 DOCX」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:export:testDebugUnitTest --tests *导出 DOCX* | - |
| FIX-028 | 修复「导出 DOCX」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:export:testDebugUnitTest | - |
| FIX-029 | 修复「导出 DOCX」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-030 | 修复「导出 DOCX」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-031 | 修复「模型解析」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *模型解析* | - |
| FIX-032 | 修复「模型解析」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *模型解析* | - |
| FIX-033 | 修复「模型解析」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest | - |
| FIX-034 | 修复「模型解析」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-035 | 修复「模型解析」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-036 | 修复「预算账本」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *预算账本* | - |
| FIX-037 | 修复「预算账本」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *预算账本* | - |
| FIX-038 | 修复「预算账本」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest | - |
| FIX-039 | 修复「预算账本」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-040 | 修复「预算账本」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-041 | 修复「egress 授权」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *egress 授权* | - |
| FIX-042 | 修复「egress 授权」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *egress 授权* | - |
| FIX-043 | 修复「egress 授权」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest | - |
| FIX-044 | 修复「egress 授权」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-045 | 修复「egress 授权」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-046 | 修复「工具环」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :app:testLocalFirstDebugUnitTest --tests *工具环* | - |
| FIX-047 | 修复「工具环」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :app:testLocalFirstDebugUnitTest --tests *工具环* | - |
| FIX-048 | 修复「工具环」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :app:testLocalFirstDebugUnitTest | - |
| FIX-049 | 修复「工具环」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-050 | 修复「工具环」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-051 | 修复「复习调度」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *复习调度* | - |
| FIX-052 | 修复「复习调度」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *复习调度* | - |
| FIX-053 | 修复「复习调度」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest | - |
| FIX-054 | 修复「复习调度」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-055 | 修复「复习调度」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-056 | 修复「掌握判定」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *掌握判定* | - |
| FIX-057 | 修复「掌握判定」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *掌握判定* | - |
| FIX-058 | 修复「掌握判定」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest | - |
| FIX-059 | 修复「掌握判定」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-060 | 修复「掌握判定」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-061 | 修复「错因账」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *错因账* | - |
| FIX-062 | 修复「错因账」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *错因账* | - |
| FIX-063 | 修复「错因账」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest | - |
| FIX-064 | 修复「错因账」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-065 | 修复「错因账」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-066 | 修复「提醒」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :app:testLocalFirstDebugUnitTest --tests *提醒* | - |
| FIX-067 | 修复「提醒」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :app:testLocalFirstDebugUnitTest --tests *提醒* | - |
| FIX-068 | 修复「提醒」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :app:testLocalFirstDebugUnitTest | - |
| FIX-069 | 修复「提醒」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-070 | 修复「提醒」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-071 | 修复「性能」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:export:testDebugUnitTest --tests *性能* | - |
| FIX-072 | 修复「性能」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:export:testDebugUnitTest --tests *性能* | - |
| FIX-073 | 修复「性能」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:export:testDebugUnitTest | - |
| FIX-074 | 修复「性能」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-075 | 修复「性能」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-076 | 修复「崩溃恢复」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :app:testLocalFirstDebugUnitTest --tests *崩溃恢复* | - |
| FIX-077 | 修复「崩溃恢复」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :app:testLocalFirstDebugUnitTest --tests *崩溃恢复* | - |
| FIX-078 | 修复「崩溃恢复」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :app:testLocalFirstDebugUnitTest | - |
| FIX-079 | 修复「崩溃恢复」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-080 | 修复「崩溃恢复」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-081 | 修复「安全密钥」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *安全密钥* | - |
| FIX-082 | 修复「安全密钥」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *安全密钥* | - |
| FIX-083 | 修复「安全密钥」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest | - |
| FIX-084 | 修复「安全密钥」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-085 | 修复「安全密钥」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-086 | 修复「日志脱敏」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *日志脱敏* | - |
| FIX-087 | 修复「日志脱敏」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest --tests *日志脱敏* | - |
| FIX-088 | 修复「日志脱敏」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:data:testDebugUnitTest | - |
| FIX-089 | 修复「日志脱敏」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-090 | 修复「日志脱敏」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-091 | 修复「AIGC 标识」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:export:testDebugUnitTest --tests *AIGC 标识* | - |
| FIX-092 | 修复「AIGC 标识」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:export:testDebugUnitTest --tests *AIGC 标识* | - |
| FIX-093 | 修复「AIGC 标识」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :core:export:testDebugUnitTest | - |
| FIX-094 | 修复「AIGC 标识」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-095 | 修复「AIGC 标识」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| FIX-096 | 修复「无障碍与文案」：红测试 | 证据列空、未回填；方法声明：cmd: ./gradlew :app:testLocalFirstDebugUnitTest --tests *无障碍与文案* | - |
| FIX-097 | 修复「无障碍与文案」：绿修复 | 证据列空、未回填；方法声明：cmd: ./gradlew :app:testLocalFirstDebugUnitTest --tests *无障碍与文案* | - |
| FIX-098 | 修复「无障碍与文案」：回归 | 证据列空、未回填；方法声明：cmd: ./gradlew :app:testLocalFirstDebugUnitTest | - |
| FIX-099 | 修复「无障碍与文案」：证据 | 复核/终裁文件不存在：docs/acceptance/verdicts/FIX-review.json（文件未生成，故无独立复核结论可引用） | - |
| FIX-100 | 修复「无障碍与文案」：不可修登记 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-001 | A 域「功能旅程」：独立复核 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-A-independent.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-002 | A 域「功能旅程」：对抗复核反例尝试 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-A-adversarial.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-003 | A 域「功能旅程」：主会话终裁 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-A-main.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-004 | A 域「功能旅程」：双门（单测 + 强门） | 证据列空、未回填；方法声明：cmd: ./gradlew testDebugUnitTest; cmd: ./gradlew :app:assembleLocalFirstDebug | - |
| GATE-005 | A 域「功能旅程」：设备证据绑定 APK sha | 证据列空、未回填；方法声明：device: adb -s $SERIAL shell am instrument -w | - |
| GATE-006 | A 域「功能旅程」：证据归档 | 证据列空、未回填；方法声明：file: docs/acceptance/ledger.md | - |
| GATE-007 | A 域「功能旅程」：证据哈希清单 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| GATE-008 | A 域「功能旅程」：记录回写 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-009 | A 域「功能旅程」：空项检查 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| GATE-010 | A 域「功能旅程」：批次 45 建议 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-011 | B 域「数据完整性」：独立复核 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-B-independent.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-012 | B 域「数据完整性」：对抗复核反例尝试 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-B-adversarial.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-013 | B 域「数据完整性」：主会话终裁 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-B-main.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-014 | B 域「数据完整性」：双门（单测 + 强门） | 证据列空、未回填；方法声明：cmd: ./gradlew testDebugUnitTest; cmd: ./gradlew :app:assembleLocalFirstDebug | - |
| GATE-015 | B 域「数据完整性」：设备证据绑定 APK sha | 证据列空、未回填；方法声明：device: adb -s $SERIAL shell am instrument -w | - |
| GATE-016 | B 域「数据完整性」：证据归档 | 证据列空、未回填；方法声明：file: docs/acceptance/ledger.md | - |
| GATE-017 | B 域「数据完整性」：证据哈希清单 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| GATE-018 | B 域「数据完整性」：记录回写 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-019 | B 域「数据完整性」：空项检查 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| GATE-020 | B 域「数据完整性」：批次 45 建议 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-021 | C 域「视觉与渲染」：独立复核 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-C-independent.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-022 | C 域「视觉与渲染」：对抗复核反例尝试 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-C-adversarial.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-023 | C 域「视觉与渲染」：主会话终裁 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-C-main.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-024 | C 域「视觉与渲染」：双门（单测 + 强门） | 证据列空、未回填；方法声明：cmd: ./gradlew testDebugUnitTest; cmd: ./gradlew :app:assembleLocalFirstDebug | - |
| GATE-025 | C 域「视觉与渲染」：设备证据绑定 APK sha | 证据列空、未回填；方法声明：device: adb -s $SERIAL shell am instrument -w | - |
| GATE-026 | C 域「视觉与渲染」：证据归档 | 证据列空、未回填；方法声明：file: docs/acceptance/ledger.md | - |
| GATE-027 | C 域「视觉与渲染」：证据哈希清单 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| GATE-028 | C 域「视觉与渲染」：记录回写 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-029 | C 域「视觉与渲染」：空项检查 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| GATE-030 | C 域「视觉与渲染」：批次 45 建议 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-031 | D 域「模型链路」：独立复核 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-D-independent.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-032 | D 域「模型链路」：对抗复核反例尝试 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-D-adversarial.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-033 | D 域「模型链路」：主会话终裁 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-D-main.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-034 | D 域「模型链路」：双门（单测 + 强门） | 证据列空、未回填；方法声明：cmd: ./gradlew testDebugUnitTest; cmd: ./gradlew :app:assembleLocalFirstDebug | - |
| GATE-035 | D 域「模型链路」：设备证据绑定 APK sha | 证据列空、未回填；方法声明：device: adb -s $SERIAL shell am instrument -w | - |
| GATE-036 | D 域「模型链路」：证据归档 | 证据列空、未回填；方法声明：file: docs/acceptance/ledger.md | - |
| GATE-037 | D 域「模型链路」：证据哈希清单 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| GATE-038 | D 域「模型链路」：记录回写 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-039 | D 域「模型链路」：空项检查 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| GATE-040 | D 域「模型链路」：批次 45 建议 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-041 | E 域「复习与算法」：独立复核 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-E-independent.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-042 | E 域「复习与算法」：对抗复核反例尝试 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-E-adversarial.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-043 | E 域「复习与算法」：主会话终裁 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-E-main.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-044 | E 域「复习与算法」：双门（单测 + 强门） | 证据列空、未回填；方法声明：cmd: ./gradlew testDebugUnitTest; cmd: ./gradlew :app:assembleLocalFirstDebug | - |
| GATE-045 | E 域「复习与算法」：设备证据绑定 APK sha | 证据列空、未回填；方法声明：device: adb -s $SERIAL shell am instrument -w | - |
| GATE-046 | E 域「复习与算法」：证据归档 | 证据列空、未回填；方法声明：file: docs/acceptance/ledger.md | - |
| GATE-047 | E 域「复习与算法」：证据哈希清单 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| GATE-048 | E 域「复习与算法」：记录回写 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-049 | E 域「复习与算法」：空项检查 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| GATE-050 | E 域「复习与算法」：批次 45 建议 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-051 | F 域「性能与稳定」：独立复核 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-F-independent.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-052 | F 域「性能与稳定」：对抗复核反例尝试 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-F-adversarial.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-053 | F 域「性能与稳定」：主会话终裁 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-F-main.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-054 | F 域「性能与稳定」：双门（单测 + 强门） | 证据列空、未回填；方法声明：cmd: ./gradlew testDebugUnitTest; cmd: ./gradlew :app:assembleLocalFirstDebug | - |
| GATE-055 | F 域「性能与稳定」：设备证据绑定 APK sha | 证据列空、未回填；方法声明：device: adb -s $SERIAL shell am instrument -w | - |
| GATE-056 | F 域「性能与稳定」：证据归档 | 证据列空、未回填；方法声明：file: docs/acceptance/ledger.md | - |
| GATE-057 | F 域「性能与稳定」：证据哈希清单 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| GATE-058 | F 域「性能与稳定」：记录回写 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-059 | F 域「性能与稳定」：空项检查 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| GATE-060 | F 域「性能与稳定」：批次 45 建议 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-061 | G 域「安全与隐私」：独立复核 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-G-independent.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-062 | G 域「安全与隐私」：对抗复核反例尝试 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-G-adversarial.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-063 | G 域「安全与隐私」：主会话终裁 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-G-main.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-064 | G 域「安全与隐私」：双门（单测 + 强门） | 证据列空、未回填；方法声明：cmd: ./gradlew testDebugUnitTest; cmd: ./gradlew :app:assembleLocalFirstDebug | - |
| GATE-065 | G 域「安全与隐私」：设备证据绑定 APK sha | 证据列空、未回填；方法声明：device: adb -s $SERIAL shell am instrument -w | - |
| GATE-066 | G 域「安全与隐私」：证据归档 | 证据列空、未回填；方法声明：file: docs/acceptance/ledger.md | - |
| GATE-067 | G 域「安全与隐私」：证据哈希清单 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| GATE-068 | G 域「安全与隐私」：记录回写 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-069 | G 域「安全与隐私」：空项检查 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| GATE-070 | G 域「安全与隐私」：批次 45 建议 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-071 | H 域「合规与文案」：独立复核 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-H-independent.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-072 | H 域「合规与文案」：对抗复核反例尝试 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-H-adversarial.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-073 | H 域「合规与文案」：主会话终裁 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-H-main.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-074 | H 域「合规与文案」：双门（单测 + 强门） | 证据列空、未回填；方法声明：cmd: ./gradlew testDebugUnitTest; cmd: ./gradlew :app:assembleLocalFirstDebug | - |
| GATE-075 | H 域「合规与文案」：设备证据绑定 APK sha | 证据列空、未回填；方法声明：device: adb -s $SERIAL shell am instrument -w | - |
| GATE-076 | H 域「合规与文案」：证据归档 | 证据列空、未回填；方法声明：file: docs/acceptance/ledger.md | - |
| GATE-077 | H 域「合规与文案」：证据哈希清单 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| GATE-078 | H 域「合规与文案」：记录回写 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-079 | H 域「合规与文案」：空项检查 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| GATE-080 | H 域「合规与文案」：批次 45 建议 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-081 | I 域「可用性与无障碍」：独立复核 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-I-independent.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-082 | I 域「可用性与无障碍」：对抗复核反例尝试 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-I-adversarial.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-083 | I 域「可用性与无障碍」：主会话终裁 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-I-main.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-084 | I 域「可用性与无障碍」：双门（单测 + 强门） | 证据列空、未回填；方法声明：cmd: ./gradlew testDebugUnitTest; cmd: ./gradlew :app:assembleLocalFirstDebug | - |
| GATE-085 | I 域「可用性与无障碍」：设备证据绑定 APK sha | 证据列空、未回填；方法声明：device: adb -s $SERIAL shell am instrument -w | - |
| GATE-086 | I 域「可用性与无障碍」：证据归档 | 证据列空、未回填；方法声明：file: docs/acceptance/ledger.md | - |
| GATE-087 | I 域「可用性与无障碍」：证据哈希清单 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| GATE-088 | I 域「可用性与无障碍」：记录回写 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-089 | I 域「可用性与无障碍」：空项检查 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| GATE-090 | I 域「可用性与无障碍」：批次 45 建议 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-091 | J 域「发布工程」：独立复核 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-J-independent.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-092 | J 域「发布工程」：对抗复核反例尝试 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-J-adversarial.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-093 | J 域「发布工程」：主会话终裁 | 复核/终裁文件不存在：docs/acceptance/verdicts/GATE-J-main.json（文件未生成，故无独立复核结论可引用） | - |
| GATE-094 | J 域「发布工程」：双门（单测 + 强门） | 证据列空、未回填；方法声明：cmd: ./gradlew testDebugUnitTest; cmd: ./gradlew :app:assembleLocalFirstDebug | - |
| GATE-095 | J 域「发布工程」：设备证据绑定 APK sha | 证据列空、未回填；方法声明：device: adb -s $SERIAL shell am instrument -w | - |
| GATE-096 | J 域「发布工程」：证据归档 | 证据列空、未回填；方法声明：file: docs/acceptance/ledger.md | - |
| GATE-097 | J 域「发布工程」：证据哈希清单 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md | - |
| GATE-098 | J 域「发布工程」：记录回写 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |
| GATE-099 | J 域「发布工程」：空项检查 | 证据列空、未回填；方法声明：cmd: python -I tools/ci/acceptance_init.py --check | - |
| GATE-100 | J 域「发布工程」：批次 45 建议 | 证据列空、未回填；方法声明：file: docs/acceptance/runbook.md | - |

## 4. 复核面、对抗面与人类残余（本批未成立的部分）

| 面 | 事实 | 对结论的影响 |
|---|---|---|
| 独立复核 | 证据列 `review:` token **0** 个；方法列声明复核文件的行 64 条（GATE 30 / FIX 20 / D0 7 / J 7），文件**全部不存在**；`docs/acceptance/verdicts/*.json` 终裁文件 0 个 | DoD §1.5 未成立；670 行 PASS 缺复核面，不能作为终裁通过 |
| 对抗复核 | GATE 十条「对抗复核反例尝试」行（GATE-002/012/…/092）全为 UNVERIFIED，文件不存在；原始矛盾证据只在 A/B/C 三域有产物（`a-domain/adversarial-attempts.md`、`b-domain/b-adversarial-mask-policy.log`、`c-domain/c-adversarial-metamorphic-red.log`、`c-adversarial-seams-red.log`、`c-domain/adversarial-pairs/`），D–J 七域无 | 计划 §7「十域每域至少 1 条反例」当前 **3/10** |
| 人类语感/审美 | I-082…I-090 九行台账已自行登记为「人工语感残余：屏幕阅读器语感与审美无法自动判定」并保持 UNVERIFIED；H-075/H-076（i18n 抽取与 RTL 走查）同样 UNVERIFIED | DoD §5 明列的不可替代面，本报告不为其签字，也不改写为 PASS |
| 外部条件 | BLOCKED 73 行按台账原文归为：真实 Provider 密钥 10（D 域 9 + A-010）、arm64 设备 10（F 域）、J 域发布外部依赖 34（生产签名/Play Console/成对产物/arm64）、H 域 12（Play Console、隐私政策未发布、热区禁改、CAC 备案）、I 域 5（真机旋转/IME/无故障注入入口）、G 域 2（离线 advisory、锁文件放行态） | 出口统一在 `docs/acceptance/runbook.md`；拿到密钥/真机/账号后按 runbook 复跑再改 verdict |
| 证据归档 | `git ls-files` 下 `docs/research/evidence` 已跟踪 231 个文件，其中 `.log` **0** 个（`.gitignore:31: *.log`）；670 行 PASS 中 670 行的证据里至少一条路径**未被 git 跟踪**，其中 659 行直接依赖 `cmd:*.log` | 干净克隆上 `acceptance_report.py` 会把这些行判为证据缺失；证据归档（D0-014 hash manifest 尚未生成）必须在提交前解决，本报告未改 `.gitignore` |

## 5. 逐项终裁表（1200 行）

- `复核人`：只有真实存在且可读的 `review:` 证据里的 `reviewer` 才写入；当前 **0 行**具备该证据，故全部为 `—`。空值不是通过，是本批尚未完成的复核面。
- `run-id`：有设备证据的行取设备 JSON 自带的 `serial@recorded_at#boot`（证据文件内可核）；无设备证据的行写 `ledger@e139b03e`——它是台账绑定，**不是**一次设备执行。
- 证据列为台账原文（`;` 分隔 token），未经删减。

| id | verdict | 证据 | 复核人 | run-id |
|---|---|---|---|---|
| D0-001 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-002 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-003 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-004 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-005 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-006 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-007 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-008 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-009 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-010 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-011 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-012 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-013 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-014 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-015 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-016 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-017 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-018 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-019 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-020 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-021 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-022 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-023 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-024 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-025 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-026 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-027 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-028 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-029 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-030 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-031 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-032 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-033 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-034 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-035 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-036 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-037 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-038 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-039 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-040 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-041 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-042 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-043 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-044 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-045 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-046 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-047 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-048 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-049 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-050 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-051 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-052 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-053 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-054 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-055 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-056 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-057 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-058 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-059 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-060 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-061 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-062 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-063 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-064 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-065 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-066 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-067 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-068 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-069 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-070 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-071 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-072 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-073 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-074 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-075 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-076 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-077 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-078 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-079 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-080 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-081 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-082 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-083 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-084 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-085 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-086 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-087 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-088 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-089 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-090 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-091 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-092 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-093 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-094 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-095 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-096 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-097 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-098 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-099 | UNVERIFIED | - | — | ledger@e139b03e |
| D0-100 | UNVERIFIED | - | — | ledger@e139b03e |
| A-001 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-shared.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-shared.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-shared.log; test:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-manual.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-manual.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-manual.log | — | emulator-5584@2026-11-04T08:51:05Z#boot=5a34f877 |
| A-002 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-shared.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-shared.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-shared.log | — | emulator-5584@2026-11-04T08:51:05Z#boot=5a34f877 |
| A-003 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-shared.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-shared.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-shared.log | — | emulator-5584@2026-11-04T08:51:05Z#boot=5a34f877 |
| A-004 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-empty.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-empty.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-empty.log; device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j1-clear.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j1-clear.log | — | emulator-5584@2026-11-04T08:51:20Z#boot=5a34f877 |
| A-005 | UNVERIFIED | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/SharedCaptureEntryInstrumentedTest.kt | — | ledger@e139b03e |
| A-006 | UNVERIFIED | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/CaptureShareIntakeInstrumentedTest.kt | — | ledger@e139b03e |
| A-007 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j1-rotate.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j1-rotate.log | — | ledger@e139b03e |
| A-008 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j1-home.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j1-home.log | — | ledger@e139b03e |
| A-009 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j1-kill.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j1-kill.log | — | ledger@e139b03e |
| A-010 | BLOCKED | file:docs/acceptance/runbook.md; test:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.log | — | emulator-5584@2026-11-04T08:52:19Z#boot=5a34f877 |
| A-011 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.log | — | emulator-5584@2026-11-04T08:52:19Z#boot=5a34f877 |
| A-012 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.log | — | emulator-5584@2026-11-04T08:52:19Z#boot=5a34f877 |
| A-013 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.log | — | emulator-5584@2026-11-04T08:52:19Z#boot=5a34f877 |
| A-014 | UNVERIFIED | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/RootTutorFailClosedInstrumentedTest.kt | — | ledger@e139b03e |
| A-015 | UNVERIFIED | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/RootTutorFailClosedInstrumentedTest.kt | — | ledger@e139b03e |
| A-016 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j2-rotate.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j2-rotate.log | — | ledger@e139b03e |
| A-017 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j2-home.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j2-home.log | — | ledger@e139b03e |
| A-018 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j2-kill.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j2-kill.log | — | ledger@e139b03e |
| A-019 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r3-selfreport.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r3-selfreport.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r3-selfreport.log | — | emulator-5584@2026-11-04T08:53:15Z#boot=5a34f877 |
| A-020 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r3-selfreport.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r3-selfreport.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r3-selfreport.log | — | emulator-5584@2026-11-04T08:53:15Z#boot=5a34f877 |
| A-021 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r3-selfreport.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r3-selfreport.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r3-selfreport.log | — | emulator-5584@2026-11-04T08:53:15Z#boot=5a34f877 |
| A-022 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r2-tutor.log; test:docs/research/evidence/2026-11-05-batch-44/a-domain/r3-empty.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r3-empty.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r3-empty.log | — | emulator-5584@2026-11-04T08:52:19Z#boot=5a34f877 |
| A-023 | UNVERIFIED | file:core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlanner3EScanInstrumentedTest.kt | — | ledger@e139b03e |
| A-024 | UNVERIFIED | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/ReviewSelfReportDeviceUiInstrumentedTest.kt | — | ledger@e139b03e |
| A-025 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r3-selfreport.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r3-selfreport.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r3-selfreport.log; device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j3-rotate.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j3-rotate.log | — | emulator-5584@2026-11-04T08:53:15Z#boot=5a34f877 |
| A-026 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j3-home.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j3-home.log | — | ledger@e139b03e |
| A-027 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j3-kill.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j3-kill.log | — | ledger@e139b03e |
| A-028 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r4-detail.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r4-detail.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r4-detail.log; test:docs/research/evidence/2026-11-05-batch-44/a-domain/r4-revision-edit.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r4-revision-edit.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r4-revision-edit.log | — | emulator-5584@2026-11-04T08:53:43Z#boot=5a34f877 |
| A-029 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r4-revision-edit.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r4-revision-edit.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r4-revision-edit.log | — | emulator-5584@2026-11-04T09:59:11Z#boot=5a34f877 |
| A-030 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r4-revision-edit.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r4-revision-edit.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r4-revision-edit.log | — | emulator-5584@2026-11-04T09:59:11Z#boot=5a34f877 |
| A-031 | UNVERIFIED | file:feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeDetailRoute.kt | — | ledger@e139b03e |
| A-032 | UNVERIFIED | file:core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeDetailRepositoryTest.kt | — | ledger@e139b03e |
| A-033 | UNVERIFIED | file:core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/mistake/MistakeRevisionEditInstrumentedTest.kt | — | ledger@e139b03e |
| A-034 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j4-rotate.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j4-rotate.log | — | ledger@e139b03e |
| A-035 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j4-home.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j4-home.log | — | ledger@e139b03e |
| A-036 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j4-kill.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j4-kill.log | — | ledger@e139b03e |
| A-037 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-manual.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-manual.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r1-manual.log; test:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-export.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-export.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-export.log | — | emulator-5584@2026-11-04T08:51:40Z#boot=5a34f877 |
| A-038 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-export.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-export.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-export.log; test:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-core-export-MistakeExportJobRunnerTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-core-export.cmd.log | — | emulator-5584@2026-11-04T10:45:34Z#boot=5a34f877 |
| A-039 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-bg.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-bg.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-bg.log | — | emulator-5584@2026-11-04T08:54:22Z#boot=5a34f877 |
| A-040 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-core-export-MistakePdfBatchEligibilityTest.xml; test:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app-LibraryExportNoticeTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app.cmd.log | — | ledger@e139b03e |
| A-041 | UNVERIFIED | file:core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfBatchEligibilityTest.kt | — | ledger@e139b03e |
| A-042 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-permission-denied.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-permission-denied.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-permission-denied.log; test:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-export.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-export.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-export.log | — | emulator-5584@2026-11-04T10:46:07Z#boot=5a34f877 |
| A-043 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j5-rotate.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j5-rotate.log | — | ledger@e139b03e |
| A-044 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-bg.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-bg.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r5-bg.log; device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j5-home.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j5-home.log | — | emulator-5584@2026-11-04T08:54:22Z#boot=5a34f877 |
| A-045 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j5-kill.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j5-kill.log | — | ledger@e139b03e |
| A-046 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r6-backup.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r6-backup.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r6-backup.log | — | emulator-5584@2026-11-04T08:54:51Z#boot=5a34f877 |
| A-047 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r6-backup.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r6-backup.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r6-backup.log | — | emulator-5584@2026-11-04T08:54:51Z#boot=5a34f877 |
| A-048 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-core-data-BackupWriteGateRestoreLifecycleTest.xml; test:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-core-data-BackupTempArtifactCleanupTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-core-data.cmd.log | — | ledger@e139b03e |
| A-049 | UNVERIFIED | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/BackupSelectionUiInstrumentedTest.kt | — | ledger@e139b03e |
| A-050 | UNVERIFIED | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r6-backup-scale.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r6-backup-scale.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r6-backup-scale.log | — | emulator-5584@2026-11-04T10:29:51Z#boot=5a34f877 |
| A-051 | UNVERIFIED | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/BackupSelectionUiInstrumentedTest.kt | — | ledger@e139b03e |
| A-052 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j6-rotate.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j6-rotate.log | — | ledger@e139b03e |
| A-053 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j6-home.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j6-home.log | — | ledger@e139b03e |
| A-054 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j6-kill.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j6-kill.log | — | ledger@e139b03e |
| A-055 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r7-knowledge.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r7-knowledge.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r7-knowledge.log | — | emulator-5584@2026-11-04T08:55:10Z#boot=5a34f877 |
| A-056 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r7-knowledge.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r7-knowledge.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r7-knowledge.log | — | emulator-5584@2026-11-04T08:55:10Z#boot=5a34f877 |
| A-057 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r7-knowledge.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r7-knowledge.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r7-knowledge.log | — | emulator-5584@2026-11-04T08:55:10Z#boot=5a34f877 |
| A-058 | UNVERIFIED | file:feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/KnowledgeReviewSessionScreen.kt | — | ledger@e139b03e |
| A-059 | UNVERIFIED | file:core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalInstrumentedTest.kt | — | ledger@e139b03e |
| A-060 | UNVERIFIED | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/KnowledgeReviewSessionInstrumentedTest.kt | — | ledger@e139b03e |
| A-061 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j7-rotate.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j7-rotate.log | — | ledger@e139b03e |
| A-062 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j7-home.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j7-home.log | — | ledger@e139b03e |
| A-063 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j7-kill.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j7-kill.log | — | ledger@e139b03e |
| A-064 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r8-mastery.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r8-mastery.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r8-mastery.log; test:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app-LearningMasteryRecentActivityTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app.cmd.log | — | emulator-5584@2026-11-04T08:55:23Z#boot=5a34f877 |
| A-065 | UNVERIFIED | file:app/src/main/kotlin/com/tingyun/smartmistakebook/LearningMasteryScreen.kt | — | ledger@e139b03e |
| A-066 | UNVERIFIED | file:app/src/main/kotlin/com/tingyun/smartmistakebook/LearningMasteryScreen.kt | — | ledger@e139b03e |
| A-067 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r8-mastery.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r8-mastery.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r8-mastery.log; test:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app-LearningMasteryEmptyReasonTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app.cmd.log | — | emulator-5584@2026-11-04T08:55:23Z#boot=5a34f877 |
| A-068 | UNVERIFIED | file:core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/MasteryIntervalMappingTest.kt | — | ledger@e139b03e |
| A-069 | UNVERIFIED | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/LearningMasteryScreenInstrumentedTest.kt | — | ledger@e139b03e |
| A-070 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j8-rotate.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j8-rotate.log | — | ledger@e139b03e |
| A-071 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j8-home.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j8-home.log | — | ledger@e139b03e |
| A-072 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j8-kill.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j8-kill.log | — | ledger@e139b03e |
| A-073 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r9-reminder.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r9-reminder.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r9-reminder.log; test:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app-ReviewReminderTimeCalculatorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app.cmd.log | — | emulator-5584@2026-11-04T08:55:33Z#boot=5a34f877 |
| A-074 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app-ReviewReminderCoordinatorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app.cmd.log | — | ledger@e139b03e |
| A-075 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app-ReviewReminderCoordinatorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app.cmd.log | — | ledger@e139b03e |
| A-076 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app-ReviewReminderCoordinatorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app.cmd.log | — | ledger@e139b03e |
| A-077 | UNVERIFIED | file:app/src/test/kotlin/com/tingyun/smartmistakebook/ReviewReminderCoordinatorTest.kt | — | ledger@e139b03e |
| A-078 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j9-permission.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j9-permission.log; test:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app-ReviewReminderCoordinatorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/jvm-app.cmd.log | — | ledger@e139b03e |
| A-079 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j9-rotate.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j9-rotate.log | — | ledger@e139b03e |
| A-080 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j9-home.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j9-home.log; test:docs/research/evidence/2026-11-05-batch-44/a-domain/r9-reminder.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r9-reminder.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r9-reminder.log | — | emulator-5584@2026-11-04T08:55:33Z#boot=5a34f877 |
| A-081 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j9-kill.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j9-kill.log | — | ledger@e139b03e |
| A-082 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-settings.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-settings.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-settings.log; test:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-native.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-native.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-native.log | — | emulator-5584@2026-11-04T08:56:21Z#boot=5a34f877 |
| A-083 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-settings.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-settings.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-settings.log | — | emulator-5584@2026-11-04T08:56:21Z#boot=5a34f877 |
| A-084 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-recovery.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-recovery.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-recovery.log | — | emulator-5584@2026-11-04T08:56:51Z#boot=5a34f877 |
| A-085 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-settings.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-settings.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-settings.log | — | emulator-5584@2026-11-04T08:56:21Z#boot=5a34f877 |
| A-086 | UNVERIFIED | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/SchedulingSettingsInstrumentedTest.kt | — | ledger@e139b03e |
| A-087 | UNVERIFIED | file:app/src/androidTest/kotlin/com/tingyun/smartmistakebook/CapabilityScreenInstrumentedTest.kt | — | ledger@e139b03e |
| A-088 | PASS | test:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-recovery.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-recovery.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-recovery.log; device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j10-rotate.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j10-rotate.log | — | emulator-5584@2026-11-04T08:56:51Z#boot=5a34f877 |
| A-089 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j10-home.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j10-home.log; test:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-recovery.xml; device:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-recovery.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/r10-recovery.log | — | emulator-5584@2026-11-04T08:56:51Z#boot=5a34f877 |
| A-090 | PASS | device:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j10-kill.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/a-domain/p-j10-kill.log | — | ledger@e139b03e |
| B-001 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v01-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-002 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v02-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-003 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v03-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-004 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v04-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-005 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v05-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-006 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v06-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-007 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v07-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-008 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v08-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-009 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v09-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-010 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v10-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-011 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v11-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-012 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v12-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-013 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v13-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-014 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v14-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-015 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v15-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-016 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v16-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-017 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v17-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-018 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v18-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-019 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v19-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-020 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v20-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-021 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v21-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-022 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v22-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-023 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v23-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-024 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v24-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-025 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v25-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-026 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v26-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-027 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v27-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-028 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v28-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-029 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v29-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-030 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v30-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-031 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v31-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-032 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v32-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-033 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v33-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-034 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v34-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-035 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v35-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-036 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v36-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-037 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v37-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-038 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v38-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-039 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v39-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-040 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v40-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-041 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v41-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-042 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v42-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-043 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v43-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-044 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v44-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-045 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v45-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-046 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v46-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-047 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v47-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-048 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v48-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-049 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v49-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-050 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v50-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-051 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v51-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-052 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v52-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-053 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v53-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-054 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v54-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-055 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v55-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-056 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v56-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-057 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v57-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-058 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v58-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-059 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v59-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-060 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v60-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-061 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v61-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-062 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v62-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-063 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v63-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-064 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v64-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-065 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v65-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-066 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v66-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-067 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v67-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-068 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v68-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-069 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v69-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-070 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v70-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-071 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v71-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-072 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-v72-static.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.device.json | — | emulator-5562@2026-11-04T12:46:48Z#boot=a702449b |
| B-073 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-create.xml; test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-drill2.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-create.device.json; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-drill2.device.json | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| B-074 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| B-075 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-create.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-adversarial-mask-policy.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-create.device.json | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| B-076 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-drill2.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-drill2.device.json | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| B-077 | UNVERIFIED | file:core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSelectionRestoreInstrumentedTest.kt | — | ledger@e139b03e |
| B-078 | UNVERIFIED | file:core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSelectionRestoreInstrumentedTest.kt | — | ledger@e139b03e |
| B-079 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-create.xml; test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-drill2.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-create.device.json; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-drill2.device.json | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| B-080 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| B-081 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-adversarial-mask-policy.log | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| B-082 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log; test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-sync.xml | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| B-083 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log; test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-sync.xml | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| B-084 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| B-085 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| B-086 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| B-087 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| B-088 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| B-089 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| B-090 | PASS | test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.xml; test:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-drill2.xml; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-restore.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-plan-gate.log; device:docs/research/evidence/2026-11-05-batch-44/b-domain/b-backup-drill2.device.json | — | emulator-5566@2026-11-04T11:44:55Z#boot=d8f39d06 |
| C-001 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-002 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-003 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-004 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-005 | UNVERIFIED | file:core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsValidatorTest.kt | — | ledger@e139b03e |
| C-006 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-007 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-008 | UNVERIFIED | file:docs/research/2026-11-06-metamorphic-relations.md; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log | — | ledger@e139b03e |
| C-009 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-010 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log | — | ledger@e139b03e |
| C-011 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-012 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-013 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-014 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-015 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log | — | ledger@e139b03e |
| C-016 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-017 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-018 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-019 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-020 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log | — | ledger@e139b03e |
| C-021 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-022 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-023 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-024 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-025 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-026 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-027 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-028 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-029 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:43Z#boot=5a34f877 |
| C-030 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-031 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-032 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-033 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-quickjs-engine.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-quickjs-engine.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-quickjs-engine.log | — | emulator-5584@2026-11-04T13:16:11Z#boot=5a34f877 |
| C-034 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-035 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-036 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:43Z#boot=5a34f877 |
| C-037 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:43Z#boot=5a34f877 |
| C-038 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:43Z#boot=5a34f877 |
| C-039 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:43Z#boot=5a34f877 |
| C-040 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:43Z#boot=5a34f877 |
| C-041 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-v01-visual-chain.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-v01-visual-chain.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-v01-visual-chain.log; file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualRendererVersion.kt | — | emulator-5584@2026-11-04T13:15:06Z#boot=5a34f877 |
| C-042 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-everyrestoregatesurvives.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-everyrestoregatesurvives.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-everyrestoregatesurvives.log | — | emulator-5584@2026-11-04T13:34:37Z#boot=5a34f877 |
| C-043 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-everyrestoregatesurvives.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-everyrestoregatesurvives.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-everyrestoregatesurvives.log | — | emulator-5584@2026-11-04T13:34:37Z#boot=5a34f877 |
| C-044 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-paint-flags.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-paint-flags.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-paint-flags.log | — | emulator-5584@2026-11-04T13:16:15Z#boot=5a34f877 |
| C-045 | UNVERIFIED | file:core/database/src/test/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualIdentityTest.kt | — | ledger@e139b03e |
| C-046 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-metamorphic-font_scale_reflow.log; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-fontscale-capture.device.json; file:docs/research/evidence/2026-11-05-batch-44/c-domain/c-home-fs2_0.xml; file:docs/research/evidence/2026-11-05-batch-44/c-domain/c-home-light.xml | — | emulator-5584@2026-11-04T13:38:52Z#boot=5a34f877 |
| C-047 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-fontscale-probe.log; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-fontscale-capture.device.json; file:docs/research/evidence/2026-11-05-batch-44/c-domain/c-home-fs1_3.xml; file:docs/research/evidence/2026-11-05-batch-44/c-domain/c-home-fs2_0.xml | — | emulator-5584@2026-11-04T13:38:52Z#boot=5a34f877 |
| C-048 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-fontscale-probe.log; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-fontscale-capture.device.json; file:docs/research/evidence/2026-11-05-batch-44/c-domain/c-home-fs1_3.xml; file:docs/research/evidence/2026-11-05-batch-44/c-domain/c-home-fs2_0.xml | — | emulator-5584@2026-11-04T13:38:52Z#boot=5a34f877 |
| C-049 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-fontscale-probe.log; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-fontscale-capture.device.json; file:docs/research/evidence/2026-11-05-batch-44/c-domain/c-home-fs1_3.xml; file:docs/research/evidence/2026-11-05-batch-44/c-domain/c-home-fs2_0.xml | — | emulator-5584@2026-11-04T13:38:52Z#boot=5a34f877 |
| C-050 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-fontscale-probe.log; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-fontscale-capture.device.json; file:docs/research/evidence/2026-11-05-batch-44/c-domain/c-home-fs1_3.xml; file:docs/research/evidence/2026-11-05-batch-44/c-domain/c-home-fs2_0.xml | — | emulator-5584@2026-11-04T13:38:52Z#boot=5a34f877 |
| C-051 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-metamorphic-theme_swap.log; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-theme-capture.device.json; file:docs/research/evidence/2026-11-05-batch-44/c-domain/c-home-dark.xml; file:docs/research/evidence/2026-11-05-batch-44/c-domain/c-home-light.xml | — | emulator-5584@2026-11-04T13:38:52Z#boot=5a34f877 |
| C-052 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-metamorphic-theme_swap.log; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-theme-capture.device.json; file:docs/research/evidence/2026-11-05-batch-44/c-domain/c-home-dark.xml; file:docs/research/evidence/2026-11-05-batch-44/c-domain/c-home-light.xml | — | emulator-5584@2026-11-04T13:38:52Z#boot=5a34f877 |
| C-053 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-shot-light-vs-dark.log; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-theme-luminance.log; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-theme-capture.device.json; file:core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/Theme.kt | — | emulator-5584@2026-11-04T13:38:52Z#boot=5a34f877 |
| C-054 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-shot-light-vs-dark.log; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-theme-luminance.log; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-theme-capture.device.json | — | emulator-5584@2026-11-04T13:38:52Z#boot=5a34f877 |
| C-055 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-shot-light-relaunch.log; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-theme-capture.device.json | — | emulator-5584@2026-11-04T13:38:52Z#boot=5a34f877 |
| C-056 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopsparityinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopsparityinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopsparityinstrumentedtest.log | — | emulator-5584@2026-11-04T13:14:04Z#boot=5a34f877 |
| C-057 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopsparityinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopsparityinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopsparityinstrumentedtest.log | — | emulator-5584@2026-11-04T13:14:04Z#boot=5a34f877 |
| C-058 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopsparityinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopsparityinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopsparityinstrumentedtest.log | — | emulator-5584@2026-11-04T13:14:04Z#boot=5a34f877 |
| C-059 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopsparityinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopsparityinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopsparityinstrumentedtest.log | — | emulator-5584@2026-11-04T13:14:04Z#boot=5a34f877 |
| C-060 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopsparityinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopsparityinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopsparityinstrumentedtest.log | — | emulator-5584@2026-11-04T13:14:04Z#boot=5a34f877 |
| C-061 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-strictofflinerelayoutdevicevectorinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-strictofflinerelayoutdevicevectorinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-strictofflinerelayoutdevicevectorinstrumentedtest.log | — | emulator-5584@2026-11-04T13:14:08Z#boot=5a34f877 |
| C-062 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-processrebuildcontinuesf.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-processrebuildcontinuesf.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-processrebuildcontinuesf.log | — | emulator-5584@2026-11-04T13:34:33Z#boot=5a34f877 |
| C-063 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-tutorvisualpreviewassemblyinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-tutorvisualpreviewassemblyinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-tutorvisualpreviewassemblyinstrumentedtest.log | — | emulator-5584@2026-11-04T13:32:26Z#boot=5a34f877 |
| C-064 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-everyrestoregatesurvives.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-everyrestoregatesurvives.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-everyrestoregatesurvives.log | — | emulator-5584@2026-11-04T13:34:37Z#boot=5a34f877 |
| C-065 | FAIL | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-single-rerun.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-single-rerun.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-restore-single-rerun.log | — | emulator-5584@2026-11-04T13:32:54Z#boot=5a34f877 |
| C-066 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log | — | ledger@e139b03e |
| C-067 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log | — | ledger@e139b03e |
| C-068 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log | — | ledger@e139b03e |
| C-069 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-070 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-071 | UNVERIFIED | file:core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualLayoutBudgetTest.kt | — | ledger@e139b03e |
| C-072 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-tutorvisualpreviewrendererinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-tutorvisualpreviewrendererinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-tutorvisualpreviewrendererinstrumentedtest.log | — | emulator-5584@2026-11-04T13:32:22Z#boot=5a34f877 |
| C-073 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-074 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-075 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:43Z#boot=5a34f877 |
| C-076 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-077 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-078 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-079 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-080 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:43Z#boot=5a34f877 |
| C-081 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-082 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-083 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-084 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:43Z#boot=5a34f877 |
| C-085 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:39Z#boot=5a34f877 |
| C-086 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| C-087 | PASS | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:43Z#boot=5a34f877 |
| C-088 | UNVERIFIED | test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-visualdrawopspdfpainterinstrumentedtest.log | — | emulator-5584@2026-11-04T13:13:43Z#boot=5a34f877 |
| C-089 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log | — | ledger@e139b03e |
| C-090 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-node-compiler.log; test:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.xml; device:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/c-domain/c-scene-compiler.log | — | emulator-5584@2026-11-04T13:13:19Z#boot=5a34f877 |
| D-001 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.PaperEditPromptGoldenTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-prompt-paper.log#exit=0 | — | ledger@e139b03e |
| D-002 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.PaperEditPromptGoldenTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-prompt-paper.log#exit=0 | — | ledger@e139b03e |
| D-003 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.PaperEditPromptGoldenTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-prompt-paper.log#exit=0 | — | ledger@e139b03e |
| D-004 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.PaperEditTasksTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-contracts.log#exit=0 | — | ledger@e139b03e |
| D-005 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.PaperEditAdapterTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-prompt-paper.log#exit=0 | — | ledger@e139b03e |
| D-006 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.PaperEditAdapterTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-prompt-paper.log#exit=0 | — | ledger@e139b03e |
| D-007 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.TutorMoveLabelBudgetPromptTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-prompt-paper.log#exit=0 | — | ledger@e139b03e |
| D-008 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.TutorMoveLabelBudgetPromptTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-prompt-paper.log#exit=0 | — | ledger@e139b03e |
| D-009 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.TutorConversationPromptTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-prompt-paper.log#exit=0 | — | ledger@e139b03e |
| D-010 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.TutorVisualContextFingerprintTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-contracts.log#exit=0 | — | ledger@e139b03e |
| D-011 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.PaperEditTasksTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-contracts.log#exit=0 | — | ledger@e139b03e |
| D-012 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.PaperEditTasksTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-contracts.log#exit=0 | — | ledger@e139b03e |
| D-013 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.PaperEditAdapterTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-prompt-paper.log#exit=0 | — | ledger@e139b03e |
| D-014 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.PaperEditTasksTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-contracts.log#exit=0 | — | ledger@e139b03e |
| D-015 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.TutorVisualContextFingerprintTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-contracts.log#exit=0 | — | ledger@e139b03e |
| D-016 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.TutorVisualContextFingerprintTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-contracts.log#exit=0 | — | ledger@e139b03e |
| D-017 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.ProgramSchemaMaxLengthAlignmentTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-018 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.ProgramSchemaMaxLengthAlignmentTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-019 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiCompatibleModelGatewayTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-gateway-egress.log#exit=0 | — | ledger@e139b03e |
| D-020 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiCompatibleModelGatewayTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-gateway-egress.log#exit=0 | — | ledger@e139b03e |
| D-021 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiCompatibleModelGatewayTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-gateway-egress.log#exit=0 | — | ledger@e139b03e |
| D-022 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiCompatibleModelGatewayTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-gateway-egress.log#exit=0 | — | ledger@e139b03e |
| D-023 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiCompatibleModelGatewayTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-gateway-egress.log#exit=0 | — | ledger@e139b03e |
| D-024 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiCompatibleModelGatewayTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-gateway-egress.log#exit=0 | — | ledger@e139b03e |
| D-025 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiCompatibleModelGatewayTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-gateway-egress.log#exit=0 | — | ledger@e139b03e |
| D-026 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiCompatibleModelGatewayTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-gateway-egress.log#exit=0 | — | ledger@e139b03e |
| D-027 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiCompatibleModelGatewayTest.xml; test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.ModelCallFailureRecordLogDisciplineTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-gateway-egress.log#exit=0 | — | ledger@e139b03e |
| D-028 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelTextSurrogateTruncationTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-budget-egress.log#exit=0 | — | ledger@e139b03e |
| D-029 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelTextSurrogateTruncationTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-budget-egress.log#exit=0 | — | ledger@e139b03e |
| D-030 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelTextSurrogateTruncationTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-budget-egress.log#exit=0 | — | ledger@e139b03e |
| D-031 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.EgressImageBudgetTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-gateway-egress.log#exit=0 | — | ledger@e139b03e |
| D-032 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.EgressImageBudgetTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-gateway-egress.log#exit=0 | — | ledger@e139b03e |
| D-033 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelEgressTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-budget-egress.log#exit=0 | — | ledger@e139b03e |
| D-034 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelEgressTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-budget-egress.log#exit=0 | — | ledger@e139b03e |
| D-035 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.TutorToolRoundBudgetTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-budget-egress.log#exit=0 | — | ledger@e139b03e |
| D-036 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiCompatibleModelGatewayTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-gateway-egress.log#exit=0 | — | ledger@e139b03e |
| D-037 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelEgressTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-budget-egress.log#exit=0 | — | ledger@e139b03e |
| D-038 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelEgressTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-budget-egress.log#exit=0 | — | ledger@e139b03e |
| D-039 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelEgressTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-budget-egress.log#exit=0 | — | ledger@e139b03e |
| D-040 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelEgressTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-budget-egress.log#exit=0 | — | ledger@e139b03e |
| D-041 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelEgressTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-budget-egress.log#exit=0 | — | ledger@e139b03e |
| D-042 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelEgressTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-budget-egress.log#exit=0 | — | ledger@e139b03e |
| D-043 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelEgressTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-budget-egress.log#exit=0 | — | ledger@e139b03e |
| D-044 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiCompatibleModelGatewayTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-gateway-egress.log#exit=0 | — | ledger@e139b03e |
| D-045 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.TutorE2EProtocolTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-046 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelTasksTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-047 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.TutorToolAuthorizationTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-048 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.TutorToolAuthorizationTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-049 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.TutorToolRoundGateTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-050 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.TutorToolRoundGateTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-051 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.ModelStreamSelectionTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-052 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiSseToolCallAggregationTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-053 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiSseToolCallAggregationTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-054 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.TutorToolCarrierValidationTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-055 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.PaperEditCommandParserTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-056 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.PaperEditCommandParserTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-057 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.PaperEditCommandParserTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-058 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.PaperEditCommandParserTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-059 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.PaperEditCommandParserTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-060 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.PaperEditCommandParserTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-061 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.PaperEditTasksTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-contracts.log#exit=0 | — | ledger@e139b03e |
| D-062 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.PaperEditCommandParserTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-063 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.PaperEditModelPortTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-prompt-paper.log#exit=0 | — | ledger@e139b03e |
| D-064 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.KnowledgeQuizProtocolTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-065 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.KnowledgeQuizProtocolTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-066 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.KnowledgeQuizProtocolTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-067 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.KnowledgeQuizWireTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-068 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.KnowledgeQuizWireTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-069 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.KnowledgeQuizWireTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-070 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.KnowledgeQuizProtocolMockWebServerTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-071 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.KnowledgeQuizProtocolMockWebServerTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-072 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.KnowledgeQuizProtocolMockWebServerTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-073 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelFailureRecoveryTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-074 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelFailureRecoveryTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-075 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelFailureRecoveryTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-076 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiModelTransportCancellationTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-077 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiModelTransportCancellationTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-tools-quiz-cancel.log#exit=0 | — | ledger@e139b03e |
| D-078 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.PaperEditModelPortTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-prompt-paper.log#exit=0 | — | ledger@e139b03e |
| D-079 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.PaperEditModelPortTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-prompt-paper.log#exit=0 | — | ledger@e139b03e |
| D-080 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.data.model.OpenAiCompatibleModelGatewayTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-data-gateway-egress.log#exit=0 | — | ledger@e139b03e |
| D-081 | PASS | test:docs/research/evidence/2026-11-05-batch-44/d-domain/TEST-com.tingyun.smartmistakebook.core.model.ModelTasksTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/d-domain/d-model-tools-recovery.log#exit=0 | — | ledger@e139b03e |
| D-082 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| D-083 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| D-084 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| D-085 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| D-086 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| D-087 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| D-088 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| D-089 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| D-090 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| E-001 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.StoredRatingBasisTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-002 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.FsrsProjectionBehaviorTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.FsrsScheduleMathTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-003 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.FsrsScheduleMathTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log | — | ledger@e139b03e |
| E-004 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.FsrsProjectionBehaviorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-005 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.FsrsScheduleMathTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log | — | ledger@e139b03e |
| E-006 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.ForgettingCurveTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.LearningProjectorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log | — | ledger@e139b03e |
| E-007 | UNVERIFIED | file:core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/FsrsScheduleMath.kt | — | ledger@e139b03e |
| E-008 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.FsrsScheduleMathTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.StoredRatingBasisTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-009 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.FsrsScheduleMathTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.FsrsProjectionBehaviorTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.ForgettingCurveTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.FsrsParameterRecoveryTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-010 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacityPolicyTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log | — | ledger@e139b03e |
| E-011 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacityPolicyTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log | — | ledger@e139b03e |
| E-012 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.ReviewPlannerV2Test.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacityPolicyTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log | — | ledger@e139b03e |
| E-013 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacityPolicyTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-014 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacityPolicyTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacitySchedulerTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-015 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacitySchedulerTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-016 | UNVERIFIED | file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacityPolicy.kt | — | ledger@e139b03e |
| E-017 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacityPreviewReaderTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-018 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacityPolicyTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacitySchedulerTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacityPreviewReaderTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.StudyReviewPlannerServiceCapacityPreviewTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-019 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ExamQuotaPlannerTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log | — | ledger@e139b03e |
| E-020 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ExamQuotaPlannerTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.StudyReviewPlannerExamWindowTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log | — | ledger@e139b03e |
| E-021 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ExamQuotaPlannerTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log | — | ledger@e139b03e |
| E-022 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ExamQuotaPlannerTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.StudyReviewPlannerExamWindowTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log | — | ledger@e139b03e |
| E-023 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ExamQuotaPlannerTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacitySchedulerTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-024 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.StudyReviewPlannerExamWindowTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-025 | UNVERIFIED | file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ExamQuotaPlanner.kt | — | ledger@e139b03e |
| E-026 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacitySchedulerTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-027 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ExamQuotaPlannerTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.StudyReviewPlannerExamWindowTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-028 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.MissedDayRebalancePolicyTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-029 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.MissedDayRebalancePolicyTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-030 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.MissedDayRebalancePolicyTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-031 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.MissedDayRebalancePolicyTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-032 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.MissedDayRebalancePolicyTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacitySchedulerTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-033 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.MissedDayRebalancePolicyTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-034 | UNVERIFIED | file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/MissedDayRebalancePolicy.kt | — | ledger@e139b03e |
| E-035 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacityPreviewReaderTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-036 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.MissedDayRebalancePolicyTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewCapacitySchedulerTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-037 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.MasteryIntervalMappingTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-038 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.MasteryMemoryCardTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-039 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.MasteryMemoryCardTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-040 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.MasteryMemoryCardTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReadSideMasteryClosureTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-041 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.LearningProjectorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-042 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.LearningProjectorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-043 | UNVERIFIED | file:core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/LearningProjector.kt | — | ledger@e139b03e |
| E-044 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.MasteryIntervalMappingTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-045 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.MasteryIntervalMappingTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.MasteryMemoryCardTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReadSideMasteryClosureTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.LearningProjectorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-046 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseExtractorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-047 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseExtractorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-048 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseExtractorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-049 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseExtractorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-050 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseExtractorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-051 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseChainTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-052 | UNVERIFIED | file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ErrorCauseExtractor.kt | — | ledger@e139b03e |
| E-053 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseExtractorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-054 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseExtractorTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseGateTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-055 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseGateTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseChainTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-056 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseChainTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-057 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseChainTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseGateTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-058 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseGateTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-059 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseChainTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.TutorJudgedReviewSettlerTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-060 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseChainTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseRetractorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-061 | UNVERIFIED | file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ErrorCauseBookingService.kt | — | ledger@e139b03e |
| E-062 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseChainTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-063 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseChainTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseGateTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseRetractorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-064 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseRetractorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-065 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseRetractorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-066 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseRetractorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-067 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseChainTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-068 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseRetractorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-069 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseRetractorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-070 | UNVERIFIED | file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ErrorCauseRetractor.kt | — | ledger@e139b03e |
| E-071 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseChainTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseRetractorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-072 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseRetractorTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ErrorCauseChainTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-073 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.AdaptiveSelectionAssemblerTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-074 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.ReviewPlannerV2Test.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-075 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.ReviewPlannerV2Test.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-076 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.ReviewPlannerV2Test.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-077 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.ReviewPlannerV2Test.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-078 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.ReviewPlannerV2Test.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-079 | UNVERIFIED | file:core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ReviewPlannerV2.kt | — | ledger@e139b03e |
| E-080 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.ReviewPlannerV2Test.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-081 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.ReviewPlannerV2Test.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.AdaptiveSelectionAssemblerTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ExamQuotaPlannerTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-082 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.StudyDayMathTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log | — | ledger@e139b03e |
| E-083 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.StudyDayMathTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log | — | ledger@e139b03e |
| E-084 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.StudyDayMathTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log | — | ledger@e139b03e |
| E-085 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.StudyDayMathTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log | — | ledger@e139b03e |
| E-086 | UNVERIFIED | file:core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/StudyDayMathTest.kt | — | ledger@e139b03e |
| E-087 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewLogStateTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| E-088 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.FsrsProjectionBehaviorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log | — | ledger@e139b03e |
| E-089 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.ForgettingCurveTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.LearningProjectorTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.ReviewPlannerV2Test.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log | — | ledger@e139b03e |
| E-090 | PASS | test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.StudyDayMathTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.data.study.ReviewLogStateTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.LearningProjectorTest.xml; test:docs/research/evidence/2026-11-05-batch-44/e-domain/TEST-com.tingyun.smartmistakebook.core.domain.FsrsProjectionBehaviorTest.xml; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log | — | ledger@e139b03e |
| F-001 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup-cold.txt | — | ledger@e139b03e |
| F-002 | UNVERIFIED | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json | — | ledger@e139b03e |
| F-003 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json; device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup-run.log | — | ledger@e139b03e |
| F-004 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup-hot.txt | — | ledger@e139b03e |
| F-005 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/device-startup-meta.txt | — | ledger@e139b03e |
| F-006 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| F-007 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/logcat-displayed.txt; device:docs/research/evidence/2026-11-05-batch-44/f-domain/gfxinfo.txt; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log | — | ledger@e139b03e |
| F-008 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log; hash:docs/research/evidence/2026-11-05-batch-44/f-domain/f-evidence-sha256.txt | — | ledger@e139b03e |
| F-009 | UNVERIFIED | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json | — | ledger@e139b03e |
| F-010 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup-cold.txt | — | ledger@e139b03e |
| F-011 | UNVERIFIED | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json | — | ledger@e139b03e |
| F-012 | UNVERIFIED | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json | — | ledger@e139b03e |
| F-013 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup-hot.txt | — | ledger@e139b03e |
| F-014 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log | — | ledger@e139b03e |
| F-015 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| F-016 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup-cold.txt; device:docs/research/evidence/2026-11-05-batch-44/f-domain/logcat-displayed.txt; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log | — | ledger@e139b03e |
| F-017 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log; hash:docs/research/evidence/2026-11-05-batch-44/f-domain/f-evidence-sha256.txt | — | ledger@e139b03e |
| F-018 | UNVERIFIED | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json | — | ledger@e139b03e |
| F-019 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/gfxinfo.txt; device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log | — | ledger@e139b03e |
| F-020 | UNVERIFIED | device:docs/research/evidence/2026-11-05-batch-44/f-domain/gfxinfo.txt | — | ledger@e139b03e |
| F-021 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/gfxinfo.txt; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log | — | ledger@e139b03e |
| F-022 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/gfxinfo.txt; device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log | — | ledger@e139b03e |
| F-023 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log | — | ledger@e139b03e |
| F-024 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| F-025 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/logcat-displayed.txt; device:docs/research/evidence/2026-11-05-batch-44/f-domain/gfxinfo.txt; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log | — | ledger@e139b03e |
| F-026 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/startup.cmd.log; hash:docs/research/evidence/2026-11-05-batch-44/f-domain/f-evidence-sha256.txt | — | ledger@e139b03e |
| F-027 | UNVERIFIED | device:docs/research/evidence/2026-11-05-batch-44/f-domain/gfxinfo.txt | — | ledger@e139b03e |
| F-028 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/export.cmd.log; test:docs/research/evidence/2026-11-05-batch-44/f-domain/TEST-export-batch1.xml; device:docs/research/evidence/2026-11-05-batch-44/f-domain/export-batch1.device.json | — | ledger@e139b03e |
| F-029 | PASS | test:docs/research/evidence/2026-11-05-batch-44/f-domain/TEST-export-batch1.xml; test:docs/research/evidence/2026-11-05-batch-44/f-domain/TEST-export-batch2.xml; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/export.cmd.log | — | ledger@e139b03e |
| F-030 | PASS | test:docs/research/evidence/2026-11-05-batch-44/f-domain/TEST-export-long1.xml; device:docs/research/evidence/2026-11-05-batch-44/f-domain/export-long1.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/export.cmd.log | — | ledger@e139b03e |
| F-031 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/export.cmd.log; test:docs/research/evidence/2026-11-05-batch-44/f-domain/TEST-export-batch2.xml; test:docs/research/evidence/2026-11-05-batch-44/f-domain/TEST-export-product1.xml | — | ledger@e139b03e |
| F-032 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/export-batch1.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/export.cmd.log | — | ledger@e139b03e |
| F-033 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| F-034 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/export-batch1.instr.txt; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/export.cmd.log | — | ledger@e139b03e |
| F-035 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/export.cmd.log; test:docs/research/evidence/2026-11-05-batch-44/f-domain/TEST-export-batch1.xml; hash:docs/research/evidence/2026-11-05-batch-44/f-domain/f-evidence-sha256.txt | — | ledger@e139b03e |
| F-036 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/export.cmd.log; test:docs/research/evidence/2026-11-05-batch-44/f-domain/TEST-export-batch1.xml; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/db-perf.cmd.log; file:docs/research/evidence/2026-11-05-batch-44/f-domain/regression-baseline.json | — | ledger@e139b03e |
| F-037 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/preview.cmd.log; test:docs/research/evidence/2026-11-05-batch-44/f-domain/TEST-preview-pdf1.xml; device:docs/research/evidence/2026-11-05-batch-44/f-domain/preview-pdf1.device.json | — | ledger@e139b03e |
| F-038 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/preview.cmd.log | — | ledger@e139b03e |
| F-039 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/preview.cmd.log | — | ledger@e139b03e |
| F-040 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/preview.cmd.log; test:docs/research/evidence/2026-11-05-batch-44/f-domain/TEST-preview-pdf2.xml; test:docs/research/evidence/2026-11-05-batch-44/f-domain/TEST-preview-visual1.xml | — | ledger@e139b03e |
| F-041 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/preview-pdf1.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/preview.cmd.log | — | ledger@e139b03e |
| F-042 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| F-043 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/preview-pdf1.instr.txt; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/preview.cmd.log | — | ledger@e139b03e |
| F-044 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/preview.cmd.log; test:docs/research/evidence/2026-11-05-batch-44/f-domain/TEST-preview-pdf1.xml; hash:docs/research/evidence/2026-11-05-batch-44/f-domain/f-evidence-sha256.txt | — | ledger@e139b03e |
| F-045 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/preview.cmd.log; test:docs/research/evidence/2026-11-05-batch-44/f-domain/TEST-preview-pdf1.xml; file:docs/research/evidence/2026-11-05-batch-44/f-domain/regression-baseline.json | — | ledger@e139b03e |
| F-046 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/memory.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/meminfo-after-startup.txt; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/export.cmd.log | — | ledger@e139b03e |
| F-047 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/memory.cmd.log | — | ledger@e139b03e |
| F-048 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/memory.cmd.log | — | ledger@e139b03e |
| F-049 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/memory.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/export-batch1.device.json; device:docs/research/evidence/2026-11-05-batch-44/f-domain/preview-pdf1.device.json | — | ledger@e139b03e |
| F-050 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/export-batch1.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/memory.cmd.log | — | ledger@e139b03e |
| F-051 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| F-052 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/memory.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/export-batch1.pss.txt | — | ledger@e139b03e |
| F-053 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/memory.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/export-batch1.device.json; hash:docs/research/evidence/2026-11-05-batch-44/f-domain/f-evidence-sha256.txt | — | ledger@e139b03e |
| F-054 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/memory.cmd.log | — | ledger@e139b03e |
| F-055 | FAIL | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.device.json; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak-crash-logcat.txt; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak-crash-dropbox.txt; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak-samples.csv | — | ledger@e139b03e |
| F-056 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.cmd.log | — | ledger@e139b03e |
| F-057 | FAIL | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.device.json; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak-crash-logcat.txt; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak-crash-dropbox.txt | — | ledger@e139b03e |
| F-058 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.cmd.log | — | ledger@e139b03e |
| F-059 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.cmd.log | — | ledger@e139b03e |
| F-060 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| F-061 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak-samples.csv; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak-monkey-1.log; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.cmd.log | — | ledger@e139b03e |
| F-062 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.device.json; hash:docs/research/evidence/2026-11-05-batch-44/f-domain/f-evidence-sha256.txt | — | ledger@e139b03e |
| F-063 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/soak.cmd.log | — | ledger@e139b03e |
| F-064 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/anr-crash.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/anr-crash.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/dropbox-after.txt | — | ledger@e139b03e |
| F-065 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/anr-crash.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/logcat-crash.txt | — | ledger@e139b03e |
| F-066 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/anr-crash-overlimit-5562.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/anr-crash-overlimit-5562.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/anr-crash.cmd.log | — | ledger@e139b03e |
| F-067 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/anr-crash.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/dropbox-after.txt | — | ledger@e139b03e |
| F-068 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/anr-crash.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/anr-crash.cmd.log | — | ledger@e139b03e |
| F-069 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| F-070 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/dropbox-after.txt; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/anr-crash.cmd.log | — | ledger@e139b03e |
| F-071 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/anr-crash.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/anr-crash.device.json; hash:docs/research/evidence/2026-11-05-batch-44/f-domain/f-evidence-sha256.txt | — | ledger@e139b03e |
| F-072 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/anr-crash.cmd.log | — | ledger@e139b03e |
| F-073 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death-ui.xml | — | ledger@e139b03e |
| F-074 | UNVERIFIED | device:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death.device.json | — | ledger@e139b03e |
| F-075 | PASS | test:docs/research/evidence/2026-11-05-batch-44/f-domain/TEST-process-death-unit.xml; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death-unit.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death-unit.device.json | — | ledger@e139b03e |
| F-076 | PASS | test:docs/research/evidence/2026-11-05-batch-44/f-domain/TEST-process-death-unit.xml; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death-repeat-5584.device.json | — | ledger@e139b03e |
| F-077 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death.cmd.log | — | ledger@e139b03e |
| F-078 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| F-079 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death-manual.log; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death.cmd.log | — | ledger@e139b03e |
| F-080 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death.device.json; hash:docs/research/evidence/2026-11-05-batch-44/f-domain/f-evidence-sha256.txt | — | ledger@e139b03e |
| F-081 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/process-death.cmd.log | — | ledger@e139b03e |
| F-082 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/background.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/background.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/background-restrictions-2.log | — | ledger@e139b03e |
| F-083 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/background.cmd.log | — | ledger@e139b03e |
| F-084 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/background.cmd.log; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/low-storage-5562.cmd.log | — | ledger@e139b03e |
| F-085 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/background.cmd.log | — | ledger@e139b03e |
| F-086 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/background.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/background.cmd.log | — | ledger@e139b03e |
| F-087 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| F-088 | PASS | device:docs/research/evidence/2026-11-05-batch-44/f-domain/background-restrictions-2.log; cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/background.cmd.log | — | ledger@e139b03e |
| F-089 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/background.cmd.log; device:docs/research/evidence/2026-11-05-batch-44/f-domain/background.device.json; hash:docs/research/evidence/2026-11-05-batch-44/f-domain/f-evidence-sha256.txt | — | ledger@e139b03e |
| F-090 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/f-domain/background.cmd.log | — | ledger@e139b03e |
| G-001 | PASS | cmd:docs/acceptance/evidence/G/logs/g-secrets.log#exit=0; test:docs/acceptance/evidence/G/tests/g-secrets.xml | — | ledger@e139b03e |
| G-002 | PASS | cmd:docs/acceptance/evidence/G/logs/g-secrets.log#exit=0; test:docs/acceptance/evidence/G/tests/g-secrets.xml | — | ledger@e139b03e |
| G-003 | PASS | cmd:docs/acceptance/evidence/G/logs/g-secrets.log#exit=0; test:docs/acceptance/evidence/G/tests/g-secrets.xml | — | ledger@e139b03e |
| G-004 | PASS | cmd:docs/acceptance/evidence/G/logs/g-secrets.log#exit=0; test:docs/acceptance/evidence/G/tests/g-secrets.xml | — | ledger@e139b03e |
| G-005 | PASS | cmd:docs/acceptance/evidence/G/logs/g-secrets.log#exit=0; test:docs/acceptance/evidence/G/tests/g-secrets.xml | — | ledger@e139b03e |
| G-006 | PASS | cmd:docs/acceptance/evidence/G/logs/g-secrets.log#exit=0; test:docs/acceptance/evidence/G/tests/g-secrets.xml | — | ledger@e139b03e |
| G-007 | PASS | cmd:docs/acceptance/evidence/G/logs/g-secrets.log#exit=0; test:docs/acceptance/evidence/G/tests/g-secrets.xml | — | ledger@e139b03e |
| G-008 | PASS | cmd:docs/acceptance/evidence/G/logs/g-secrets.log#exit=0; test:docs/acceptance/evidence/G/tests/g-secrets.xml | — | ledger@e139b03e |
| G-009 | PASS | device:docs/acceptance/evidence/G/device/device-secrets.json; test:docs/acceptance/evidence/G/tests/g-device-artifacts.xml | — | ledger@e139b03e |
| G-010 | PASS | cmd:docs/acceptance/evidence/G/logs/g-logging.log#exit=0; test:docs/acceptance/evidence/G/tests/g-logging.xml | — | ledger@e139b03e |
| G-011 | PASS | cmd:docs/acceptance/evidence/G/logs/g-logging.log#exit=0; test:docs/acceptance/evidence/G/tests/g-logging.xml | — | ledger@e139b03e |
| G-012 | PASS | cmd:docs/acceptance/evidence/G/logs/g-logging.log#exit=0; test:docs/acceptance/evidence/G/tests/g-logging.xml | — | ledger@e139b03e |
| G-013 | PASS | cmd:docs/acceptance/evidence/G/logs/g-logging.log#exit=0; test:docs/acceptance/evidence/G/tests/g-logging.xml | — | ledger@e139b03e |
| G-014 | PASS | device:docs/acceptance/evidence/G/device/device-logcat.json; test:docs/acceptance/evidence/G/tests/g-device-artifacts.xml | — | ledger@e139b03e |
| G-015 | PASS | cmd:docs/acceptance/evidence/G/logs/g-logging.log#exit=0; test:docs/acceptance/evidence/G/tests/g-logging.xml | — | ledger@e139b03e |
| G-016 | UNVERIFIED | - | — | ledger@e139b03e |
| G-017 | PASS | cmd:docs/acceptance/evidence/G/logs/g-logging.log#exit=0; test:docs/acceptance/evidence/G/tests/g-logging.xml | — | ledger@e139b03e |
| G-018 | PASS | cmd:docs/acceptance/evidence/G/logs/g-logging.log#exit=0; test:docs/acceptance/evidence/G/tests/g-logging.xml | — | ledger@e139b03e |
| G-019 | PASS | cmd:docs/acceptance/evidence/G/logs/g-backup.log#exit=0; device:docs/acceptance/evidence/G/device/device-backup.json | — | ledger@e139b03e |
| G-020 | PASS | cmd:docs/acceptance/evidence/G/logs/g-backup.log#exit=0; test:docs/acceptance/evidence/G/tests/g-backup.xml | — | ledger@e139b03e |
| G-021 | PASS | cmd:docs/acceptance/evidence/G/logs/g-backup.log#exit=0; test:docs/acceptance/evidence/G/tests/g-backup.xml | — | ledger@e139b03e |
| G-022 | PASS | device:docs/acceptance/evidence/G/device/device-backup.json; test:docs/acceptance/evidence/G/tests/g-device-artifacts.xml | — | ledger@e139b03e |
| G-023 | PASS | cmd:docs/acceptance/evidence/G/logs/g-backup.log#exit=0; test:docs/acceptance/evidence/G/tests/g-backup.xml | — | ledger@e139b03e |
| G-024 | PASS | cmd:docs/acceptance/evidence/G/logs/g-backup.log#exit=0; test:docs/acceptance/evidence/G/tests/g-backup.xml | — | ledger@e139b03e |
| G-025 | PASS | cmd:docs/acceptance/evidence/G/logs/g-backup.log#exit=0; test:docs/acceptance/evidence/G/tests/g-backup.xml | — | ledger@e139b03e |
| G-026 | PASS | cmd:docs/acceptance/evidence/G/logs/g-backup.log#exit=0; test:docs/acceptance/evidence/G/tests/g-backup.xml | — | ledger@e139b03e |
| G-027 | PASS | cmd:docs/acceptance/evidence/G/logs/g-backup.log#exit=0; test:docs/acceptance/evidence/G/tests/g-backup.xml | — | ledger@e139b03e |
| G-028 | PASS | cmd:docs/acceptance/evidence/G/logs/g-egress.log#exit=0; test:docs/acceptance/evidence/G/tests/g-egress.xml | — | ledger@e139b03e |
| G-029 | PASS | cmd:docs/acceptance/evidence/G/logs/g-egress.log#exit=0; device:docs/acceptance/evidence/G/device/device-manifest.json | — | ledger@e139b03e |
| G-030 | PASS | cmd:docs/acceptance/evidence/G/logs/g-egress.log#exit=0; test:docs/acceptance/evidence/G/tests/g-egress.xml | — | ledger@e139b03e |
| G-031 | PASS | cmd:docs/acceptance/evidence/G/logs/g-egress.log#exit=0; test:docs/acceptance/evidence/G/tests/g-egress.xml | — | ledger@e139b03e |
| G-032 | PASS | cmd:docs/acceptance/evidence/G/logs/g-egress.log#exit=0; test:docs/acceptance/evidence/G/tests/g-egress.xml | — | ledger@e139b03e |
| G-033 | PASS | cmd:docs/acceptance/evidence/G/logs/g-egress.log#exit=0; test:docs/acceptance/evidence/G/tests/g-egress.xml | — | ledger@e139b03e |
| G-034 | PASS | cmd:docs/acceptance/evidence/G/logs/g-egress.log#exit=0; test:docs/acceptance/evidence/G/tests/g-egress.xml | — | ledger@e139b03e |
| G-035 | PASS | cmd:docs/acceptance/evidence/G/logs/g-egress.log#exit=0; test:docs/acceptance/evidence/G/tests/g-egress.xml | — | ledger@e139b03e |
| G-036 | UNVERIFIED | - | — | ledger@e139b03e |
| G-037 | PASS | cmd:docs/acceptance/evidence/G/logs/g-permissions.log#exit=0; device:docs/acceptance/evidence/G/device/device-permissions.json | — | ledger@e139b03e |
| G-038 | PASS | cmd:docs/acceptance/evidence/G/logs/g-permissions.log#exit=0; test:docs/acceptance/evidence/G/tests/g-permissions.xml | — | ledger@e139b03e |
| G-039 | PASS | cmd:docs/acceptance/evidence/G/logs/g-permissions.log#exit=0; test:docs/acceptance/evidence/G/tests/g-permissions.xml | — | ledger@e139b03e |
| G-040 | PASS | cmd:docs/acceptance/evidence/G/logs/g-permissions.log#exit=0; test:docs/acceptance/evidence/G/tests/g-permissions.xml | — | ledger@e139b03e |
| G-041 | PASS | cmd:docs/acceptance/evidence/G/logs/g-permissions.log#exit=0; test:docs/acceptance/evidence/G/tests/g-permissions.xml | — | ledger@e139b03e |
| G-042 | PASS | cmd:docs/acceptance/evidence/G/logs/g-permissions.log#exit=0; test:docs/acceptance/evidence/G/tests/g-permissions.xml | — | ledger@e139b03e |
| G-043 | PASS | cmd:docs/acceptance/evidence/G/logs/g-permissions.log#exit=0; test:docs/acceptance/evidence/G/tests/g-permissions.xml | — | ledger@e139b03e |
| G-044 | PASS | device:docs/acceptance/evidence/G/device/device-permissions.json; test:docs/acceptance/evidence/G/tests/g-device-artifacts.xml; cmd:docs/acceptance/evidence/G/logs/g-permissions.log#exit=0 | — | ledger@e139b03e |
| G-045 | PASS | device:docs/acceptance/evidence/G/device/device-permissions.json; test:docs/acceptance/evidence/G/tests/g-device-artifacts.xml | — | ledger@e139b03e |
| G-046 | PASS | cmd:docs/acceptance/evidence/G/logs/g-deeplink.log#exit=0; test:docs/acceptance/evidence/G/tests/g-deeplink.xml | — | ledger@e139b03e |
| G-047 | PASS | cmd:docs/acceptance/evidence/G/logs/g-deeplink.log#exit=0; test:docs/acceptance/evidence/G/tests/g-deeplink.xml | — | ledger@e139b03e |
| G-048 | PASS | cmd:docs/acceptance/evidence/G/logs/g-deeplink.log#exit=0; test:docs/acceptance/evidence/G/tests/g-deeplink.xml | — | ledger@e139b03e |
| G-049 | PASS | cmd:docs/acceptance/evidence/G/logs/g-deeplink.log#exit=0; test:docs/acceptance/evidence/G/tests/g-deeplink.xml | — | ledger@e139b03e |
| G-050 | PASS | device:docs/acceptance/evidence/G/device/device-deeplink.json; test:docs/acceptance/evidence/G/tests/g-device-artifacts.xml | — | ledger@e139b03e |
| G-051 | PASS | device:docs/acceptance/evidence/G/device/device-deeplink.json; test:docs/acceptance/evidence/G/tests/g-device-artifacts.xml | — | ledger@e139b03e |
| G-052 | PASS | cmd:docs/acceptance/evidence/G/logs/g-deeplink.log#exit=0; test:docs/acceptance/evidence/G/tests/g-deeplink.xml | — | ledger@e139b03e |
| G-053 | PASS | cmd:docs/acceptance/evidence/G/logs/g-deeplink.log#exit=0; test:docs/acceptance/evidence/G/tests/g-deeplink.xml | — | ledger@e139b03e |
| G-054 | PASS | cmd:docs/acceptance/evidence/G/logs/g-deeplink.log#exit=0; test:docs/acceptance/evidence/G/tests/g-deeplink.xml | — | ledger@e139b03e |
| G-055 | PASS | cmd:docs/acceptance/evidence/G/logs/g-intent.log#exit=0; test:docs/acceptance/evidence/G/tests/g-intent.xml | — | ledger@e139b03e |
| G-056 | PASS | cmd:docs/acceptance/evidence/G/logs/g-intent.log#exit=0; test:docs/acceptance/evidence/G/tests/g-intent.xml | — | ledger@e139b03e |
| G-057 | PASS | cmd:docs/acceptance/evidence/G/logs/g-intent.log#exit=0; test:docs/acceptance/evidence/G/tests/g-intent.xml | — | ledger@e139b03e |
| G-058 | PASS | cmd:docs/acceptance/evidence/G/logs/g-intent.log#exit=0; device:docs/acceptance/evidence/G/device/device-manifest.json | — | ledger@e139b03e |
| G-059 | PASS | cmd:docs/acceptance/evidence/G/logs/g-intent.log#exit=0; test:docs/acceptance/evidence/G/tests/g-intent.xml | — | ledger@e139b03e |
| G-060 | PASS | cmd:docs/acceptance/evidence/G/logs/g-intent.log#exit=0; test:docs/acceptance/evidence/G/tests/g-intent.xml | — | ledger@e139b03e |
| G-061 | PASS | cmd:docs/acceptance/evidence/G/logs/g-intent.log#exit=0; test:docs/acceptance/evidence/G/tests/g-intent.xml | — | ledger@e139b03e |
| G-062 | PASS | cmd:docs/acceptance/evidence/G/logs/g-intent.log#exit=0; test:docs/acceptance/evidence/G/tests/g-intent.xml | — | ledger@e139b03e |
| G-063 | PASS | cmd:docs/acceptance/evidence/G/logs/g-intent.log#exit=0; device:docs/acceptance/evidence/G/device/device-manifest.json | — | ledger@e139b03e |
| G-064 | PASS | cmd:docs/acceptance/evidence/G/logs/g-fileexposure.log#exit=0; test:docs/acceptance/evidence/G/tests/g-fileexposure.xml; test:docs/acceptance/evidence/G/tests/g-negative-control.xml | — | ledger@e139b03e |
| G-065 | PASS | cmd:docs/acceptance/evidence/G/logs/g-fileexposure.log#exit=0; test:docs/acceptance/evidence/G/tests/g-intent.xml | — | ledger@e139b03e |
| G-066 | PASS | device:docs/acceptance/evidence/G/device/device-fileexposure.json; test:docs/acceptance/evidence/G/tests/g-device-artifacts.xml | — | ledger@e139b03e |
| G-067 | PASS | cmd:docs/acceptance/evidence/G/logs/g-fileexposure.log#exit=0; test:docs/acceptance/evidence/G/tests/g-fileexposure.xml | — | ledger@e139b03e |
| G-068 | PASS | cmd:docs/acceptance/evidence/G/logs/g-fileexposure.log#exit=0; test:docs/acceptance/evidence/G/tests/g-fileexposure.xml | — | ledger@e139b03e |
| G-069 | PASS | cmd:docs/acceptance/evidence/G/logs/g-fileexposure.log#exit=0; test:docs/acceptance/evidence/G/tests/g-fileexposure.xml | — | ledger@e139b03e |
| G-070 | PASS | cmd:docs/acceptance/evidence/G/logs/g-fileexposure.log#exit=0; test:docs/acceptance/evidence/G/tests/g-fileexposure.xml | — | ledger@e139b03e |
| G-071 | PASS | cmd:docs/acceptance/evidence/G/logs/g-fileexposure.log#exit=0; test:docs/acceptance/evidence/G/tests/g-fileexposure.xml | — | ledger@e139b03e |
| G-072 | UNVERIFIED | - | — | ledger@e139b03e |
| G-073 | PASS | cmd:docs/acceptance/evidence/G/logs/g-clipboard_screenshot.log#exit=0; test:docs/acceptance/evidence/G/tests/g-clipboard_screenshot.xml | — | ledger@e139b03e |
| G-074 | PASS | cmd:docs/acceptance/evidence/G/logs/g-clipboard_screenshot.log#exit=0; test:docs/acceptance/evidence/G/tests/g-clipboard_screenshot.xml | — | ledger@e139b03e |
| G-075 | PASS | cmd:docs/acceptance/evidence/G/logs/g-clipboard_screenshot.log#exit=0; test:docs/acceptance/evidence/G/tests/g-clipboard_screenshot.xml | — | ledger@e139b03e |
| G-076 | PASS | cmd:docs/acceptance/evidence/G/logs/g-clipboard_screenshot.log#exit=0; test:docs/acceptance/evidence/G/tests/g-clipboard_screenshot.xml | — | ledger@e139b03e |
| G-077 | PASS | cmd:docs/acceptance/evidence/G/logs/g-clipboard_screenshot.log#exit=0; test:docs/acceptance/evidence/G/tests/g-clipboard_screenshot.xml | — | ledger@e139b03e |
| G-078 | PASS | cmd:docs/acceptance/evidence/G/logs/g-clipboard_screenshot.log#exit=0; test:docs/acceptance/evidence/G/tests/g-clipboard_screenshot.xml | — | ledger@e139b03e |
| G-079 | PASS | device:docs/acceptance/evidence/G/device/device-fileexposure.json; test:docs/acceptance/evidence/G/tests/g-device-artifacts.xml | — | ledger@e139b03e |
| G-080 | UNVERIFIED | - | — | ledger@e139b03e |
| G-081 | UNVERIFIED | - | — | ledger@e139b03e |
| G-082 | PASS | cmd:docs/acceptance/evidence/G/logs/g-plaintext_deps.log#exit=0; test:docs/acceptance/evidence/G/tests/g-plaintext_deps.xml | — | ledger@e139b03e |
| G-083 | PASS | cmd:docs/acceptance/evidence/G/logs/g-plaintext_deps.log#exit=0; test:docs/acceptance/evidence/G/tests/g-plaintext_deps.xml | — | ledger@e139b03e |
| G-084 | PASS | cmd:docs/acceptance/evidence/G/logs/g-plaintext_deps.log#exit=0; test:docs/acceptance/evidence/G/tests/g-plaintext_deps.xml | — | ledger@e139b03e |
| G-085 | PASS | cmd:docs/acceptance/evidence/G/logs/g-plaintext_deps.log#exit=0; test:docs/acceptance/evidence/G/tests/g-plaintext_deps.xml | — | ledger@e139b03e |
| G-086 | PASS | cmd:docs/acceptance/evidence/G/logs/g-plaintext_deps.log#exit=0; test:docs/acceptance/evidence/G/tests/g-plaintext_deps.xml | — | ledger@e139b03e |
| G-087 | BLOCKED | - | — | ledger@e139b03e |
| G-088 | BLOCKED | - | — | ledger@e139b03e |
| G-089 | UNVERIFIED | - | — | ledger@e139b03e |
| G-090 | PASS | cmd:docs/acceptance/evidence/G/logs/g-plaintext_deps.log#exit=0; test:docs/acceptance/evidence/G/tests/g-plaintext_deps.xml | — | ledger@e139b03e |
| H-001 | PASS | cmd:docs/acceptance/evidence/H/logs/h-001.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-explicit.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaption.kt | — | ledger@e139b03e |
| H-002 | PASS | cmd:docs/acceptance/evidence/H/logs/h-002.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-explicit.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPagePlanner.kt | — | ledger@e139b03e |
| H-003 | PASS | cmd:docs/acceptance/evidence/H/logs/h-003.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-explicit.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DocxMistakeRenderer.kt | — | ledger@e139b03e |
| H-004 | PASS | cmd:docs/acceptance/evidence/H/logs/h-004.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-explicit.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaption.kt | — | ledger@e139b03e |
| H-005 | PASS | cmd:docs/acceptance/evidence/H/logs/h-005.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-explicit.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaption.kt | — | ledger@e139b03e |
| H-006 | PASS | cmd:docs/acceptance/evidence/H/logs/h-006.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-explicit.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaption.kt | — | ledger@e139b03e |
| H-007 | PASS | cmd:docs/acceptance/evidence/H/logs/h-007.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-explicit.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaption.kt; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPagePlanner.kt | — | ledger@e139b03e |
| H-008 | BLOCKED | cmd:docs/acceptance/evidence/H/logs/h-008.log#exit=1; file:docs/ai-content-disclosure-copy.md; file:docs/agent-first-refactor-decisions-2026-09-23.md | — | ledger@e139b03e |
| H-009 | PASS | cmd:docs/acceptance/evidence/H/logs/h-009.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-explicit.xml; file:core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/pdf/PdfAigcContentPolicy.kt; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporter.kt | — | ledger@e139b03e |
| H-010 | PASS | cmd:docs/acceptance/evidence/H/logs/h-010.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-implicit.xml; file:core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/pdf/PdfAigcSchema.kt | — | ledger@e139b03e |
| H-011 | PASS | cmd:docs/acceptance/evidence/H/logs/h-011.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-implicit.xml; file:core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/pdf/PdfAigcSchema.kt | — | ledger@e139b03e |
| H-012 | PASS | cmd:docs/acceptance/evidence/H/logs/h-012.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-implicit.xml; file:core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/pdf/PdfAigcSchema.kt | — | ledger@e139b03e |
| H-013 | PASS | cmd:docs/acceptance/evidence/H/logs/h-013.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-implicit.xml; file:core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/pdf/PdfAigcInjector.kt; file:core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/pdf/PdfAigcReader.kt | — | ledger@e139b03e |
| H-014 | PASS | cmd:docs/acceptance/evidence/H/logs/h-014.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-implicit.xml; file:core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/pdf/PdfAigcContentPolicy.kt; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfEligibility.kt | — | ledger@e139b03e |
| H-015 | PASS | cmd:docs/acceptance/evidence/H/logs/h-015.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-implicit.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporter.kt | — | ledger@e139b03e |
| H-016 | PASS | cmd:docs/acceptance/evidence/H/logs/h-016.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-implicit.xml; file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureMetadata.kt | — | ledger@e139b03e |
| H-017 | PASS | cmd:docs/acceptance/evidence/H/logs/h-017.log#exit=0; test:docs/acceptance/evidence/H/tests/h-aigc-implicit.xml; file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureMetadata.kt | — | ledger@e139b03e |
| H-018 | FAIL | cmd:docs/acceptance/evidence/H/logs/h-018.log#exit=1; file:docs/known-defects.md; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DocxMistakeRenderer.kt | — | ledger@e139b03e |
| H-019 | PASS | cmd:docs/acceptance/evidence/H/logs/h-019.log#exit=0; test:docs/acceptance/evidence/H/tests/h-privacy.xml; file:PRIVACY-DATA-FLOW.md | — | ledger@e139b03e |
| H-020 | PASS | cmd:docs/acceptance/evidence/H/logs/h-020.log#exit=0; test:docs/acceptance/evidence/H/tests/h-privacy.xml; file:docs/current/privacy-and-data-safety.md | — | ledger@e139b03e |
| H-021 | PASS | cmd:docs/acceptance/evidence/H/logs/h-021.log#exit=0; test:docs/acceptance/evidence/H/tests/h-privacy.xml; file:app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt | — | ledger@e139b03e |
| H-022 | PASS | cmd:docs/acceptance/evidence/H/logs/h-022.log#exit=0; test:docs/acceptance/evidence/H/tests/h-privacy.xml; file:app/build.gradle.kts | — | ledger@e139b03e |
| H-023 | PASS | cmd:docs/acceptance/evidence/H/logs/h-023.log#exit=0; test:docs/acceptance/evidence/H/tests/h-privacy.xml; file:app/src/main/AndroidManifest.xml; file:app/src/strictOffline/AndroidManifest.xml | — | ledger@e139b03e |
| H-024 | BLOCKED | cmd:docs/acceptance/evidence/H/logs/h-024.log#exit=1; file:PRIVACY-DATA-FLOW.md | — | ledger@e139b03e |
| H-025 | BLOCKED | cmd:docs/acceptance/evidence/H/logs/h-025.log#exit=1; file:docs/ai-content-disclosure-copy.md; file:docs/current/privacy-and-data-safety.md | — | ledger@e139b03e |
| H-026 | PASS | cmd:docs/acceptance/evidence/H/logs/h-026.log#exit=0; test:docs/acceptance/evidence/H/tests/h-privacy.xml; file:app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt | — | ledger@e139b03e |
| H-027 | PASS | cmd:docs/acceptance/evidence/H/logs/h-027.log#exit=0; test:docs/acceptance/evidence/H/tests/h-privacy.xml; file:feature/profile/src/main/java/com/tingyun/smartmistakebook/feature/profile/ProfileRoute.kt | — | ledger@e139b03e |
| H-028 | PASS | cmd:docs/acceptance/evidence/H/logs/h-028.log#exit=0; test:docs/acceptance/evidence/H/tests/h-data-safety.xml; file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/telemetry/TelemetryConsent.kt | — | ledger@e139b03e |
| H-029 | PASS | cmd:docs/acceptance/evidence/H/logs/h-029.log#exit=0; test:docs/acceptance/evidence/H/tests/h-data-safety.xml; file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/telemetry/TelemetryConsent.kt | — | ledger@e139b03e |
| H-030 | PASS | cmd:docs/acceptance/evidence/H/logs/h-030.log#exit=0; test:docs/acceptance/evidence/H/tests/h-data-safety.xml; file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/telemetry/TelemetryAttributes.kt | — | ledger@e139b03e |
| H-031 | PASS | cmd:docs/acceptance/evidence/H/logs/h-031.log#exit=0; test:docs/acceptance/evidence/H/tests/h-data-safety.xml; file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/telemetry/TelemetryConsent.kt | — | ledger@e139b03e |
| H-032 | PASS | cmd:docs/acceptance/evidence/H/logs/h-032.log#exit=0; test:docs/acceptance/evidence/H/tests/h-data-safety.xml; file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/telemetry/TelemetryConsent.kt; file:core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/telemetry/TelemetryComplianceCriteriaTest.kt | — | ledger@e139b03e |
| H-033 | PASS | cmd:docs/acceptance/evidence/H/logs/h-033.log#exit=0; test:docs/acceptance/evidence/H/tests/h-data-safety.xml; file:tools/ci/check_telemetry_compliance.py | — | ledger@e139b03e |
| H-034 | PASS | cmd:docs/acceptance/evidence/H/logs/h-034.log#exit=0; test:docs/acceptance/evidence/H/tests/h-data-safety.xml; file:docs/current/privacy-and-data-safety.md; file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/telemetry/TelemetryConsent.kt | — | ledger@e139b03e |
| H-035 | PASS | cmd:docs/acceptance/evidence/H/logs/h-035.log#exit=0; test:docs/acceptance/evidence/H/tests/h-data-safety.xml; file:app/src/main/res/xml/backup_rules.xml; file:app/src/main/res/xml/data_extraction_rules.xml | — | ledger@e139b03e |
| H-036 | BLOCKED | cmd:docs/acceptance/evidence/H/logs/h-036.log#exit=1; file:docs/current/privacy-and-data-safety.md | — | ledger@e139b03e |
| H-037 | PASS | cmd:docs/acceptance/evidence/H/logs/h-037.log#exit=0; test:docs/acceptance/evidence/H/tests/h-minor.xml | — | ledger@e139b03e |
| H-038 | PASS | cmd:docs/acceptance/evidence/H/logs/h-038.log#exit=0; test:docs/acceptance/evidence/H/tests/h-minor.xml | — | ledger@e139b03e |
| H-039 | BLOCKED | cmd:docs/acceptance/evidence/H/logs/h-039.log#exit=1; file:docs/agent-first-refactor-decisions-2026-09-23.md | — | ledger@e139b03e |
| H-040 | BLOCKED | cmd:docs/acceptance/evidence/H/logs/h-040.log#exit=1; file:docs/agent-first-refactor-decisions-2026-09-23.md | — | ledger@e139b03e |
| H-041 | BLOCKED | cmd:docs/acceptance/evidence/H/logs/h-041.log#exit=1; file:docs/agent-first-refactor-decisions-2026-09-23.md | — | ledger@e139b03e |
| H-042 | BLOCKED | cmd:docs/acceptance/evidence/H/logs/h-042.log#exit=1; file:docs/agent-first-refactor-decisions-2026-09-23.md | — | ledger@e139b03e |
| H-043 | BLOCKED | cmd:docs/acceptance/evidence/H/logs/h-043.log#exit=1; file:docs/current/privacy-and-data-safety.md | — | ledger@e139b03e |
| H-044 | PASS | cmd:docs/acceptance/evidence/H/logs/h-044.log#exit=0; test:docs/acceptance/evidence/H/tests/h-minor.xml; file:feature/profile/src/main/java/com/tingyun/smartmistakebook/feature/profile/ProfileRoute.kt | — | ledger@e139b03e |
| H-045 | PASS | cmd:docs/acceptance/evidence/H/logs/h-045.log#exit=0; test:docs/acceptance/evidence/H/tests/h-minor.xml; file:core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/telemetry/TelemetryComplianceCriteriaTest.kt | — | ledger@e139b03e |
| H-046 | PASS | cmd:docs/acceptance/evidence/H/logs/h-046.log#exit=0; test:docs/acceptance/evidence/H/tests/h-disclaimer.xml; file:docs/ai-content-disclosure-copy.md | — | ledger@e139b03e |
| H-047 | PASS | cmd:docs/acceptance/evidence/H/logs/h-047.log#exit=0; test:docs/acceptance/evidence/H/tests/h-disclaimer.xml; file:docs/ai-content-disclosure-copy.md | — | ledger@e139b03e |
| H-048 | PASS | cmd:docs/acceptance/evidence/H/logs/h-048.log#exit=0; test:docs/acceptance/evidence/H/tests/h-disclaimer.xml; file:docs/ai-content-disclosure-copy.md | — | ledger@e139b03e |
| H-049 | PASS | cmd:docs/acceptance/evidence/H/logs/h-049.log#exit=0; test:docs/acceptance/evidence/H/tests/h-disclaimer.xml; file:docs/ai-content-disclosure-copy.md | — | ledger@e139b03e |
| H-050 | PASS | cmd:docs/acceptance/evidence/H/logs/h-050.log#exit=0; test:docs/acceptance/evidence/H/tests/h-disclaimer.xml; file:docs/ai-content-disclosure-copy.md | — | ledger@e139b03e |
| H-051 | BLOCKED | cmd:docs/acceptance/evidence/H/logs/h-051.log#exit=1; file:docs/ai-content-disclosure-copy.md | — | ledger@e139b03e |
| H-052 | FAIL | cmd:docs/acceptance/evidence/H/logs/h-052.log#exit=1; file:docs/ai-content-disclosure-copy.md; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporter.kt | — | ledger@e139b03e |
| H-053 | BLOCKED | cmd:docs/acceptance/evidence/H/logs/h-053.log#exit=1; file:docs/ai-content-disclosure-copy.md | — | ledger@e139b03e |
| H-054 | PASS | cmd:docs/acceptance/evidence/H/logs/h-054.log#exit=0; test:docs/acceptance/evidence/H/tests/h-disclaimer.xml; file:docs/ai-content-disclosure-copy.md | — | ledger@e139b03e |
| H-055 | PASS | cmd:docs/acceptance/evidence/H/logs/h-055.log#exit=0; test:docs/acceptance/evidence/H/tests/h-export-marking.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPagePlanner.kt | — | ledger@e139b03e |
| H-056 | PASS | cmd:docs/acceptance/evidence/H/logs/h-056.log#exit=0; test:docs/acceptance/evidence/H/tests/h-export-marking.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPagePlanner.kt | — | ledger@e139b03e |
| H-057 | PASS | cmd:docs/acceptance/evidence/H/logs/h-057.log#exit=0; test:docs/acceptance/evidence/H/tests/h-export-marking.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPaperEdit.kt; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPagePlanner.kt | — | ledger@e139b03e |
| H-058 | PASS | cmd:docs/acceptance/evidence/H/logs/h-058.log#exit=0; test:docs/acceptance/evidence/H/tests/h-export-marking.xml; file:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureMetadata.kt | — | ledger@e139b03e |
| H-059 | PASS | cmd:docs/acceptance/evidence/H/logs/h-059.log#exit=0; test:docs/acceptance/evidence/H/tests/h-export-marking.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaption.kt | — | ledger@e139b03e |
| H-060 | PASS | cmd:docs/acceptance/evidence/H/logs/h-060.log#exit=0; test:docs/acceptance/evidence/H/tests/h-export-marking.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaption.kt | — | ledger@e139b03e |
| H-061 | FAIL | cmd:docs/acceptance/evidence/H/logs/h-061.log#exit=1; file:docs/known-defects.md | — | ledger@e139b03e |
| H-062 | FAIL | cmd:docs/acceptance/evidence/H/logs/h-062.log#exit=1; file:docs/known-defects.md | — | ledger@e139b03e |
| H-063 | UNVERIFIED | cmd:docs/acceptance/evidence/H/logs/h-063.log#exit=1; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaption.kt | — | ledger@e139b03e |
| H-064 | PASS | cmd:docs/acceptance/evidence/H/logs/h-064.log#exit=0; test:docs/acceptance/evidence/H/tests/h-error-copy.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt | — | ledger@e139b03e |
| H-065 | PASS | cmd:docs/acceptance/evidence/H/logs/h-065.log#exit=0; test:docs/acceptance/evidence/H/tests/h-error-copy.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt | — | ledger@e139b03e |
| H-066 | FAIL | cmd:docs/acceptance/evidence/H/logs/h-066.log#exit=1; file:app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt | — | ledger@e139b03e |
| H-067 | PASS | cmd:docs/acceptance/evidence/H/logs/h-067.log#exit=0; test:docs/acceptance/evidence/H/tests/h-error-copy.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt | — | ledger@e139b03e |
| H-068 | PASS | cmd:docs/acceptance/evidence/H/logs/h-068.log#exit=0; test:docs/acceptance/evidence/H/tests/h-error-copy.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt | — | ledger@e139b03e |
| H-069 | PASS | cmd:docs/acceptance/evidence/H/logs/h-069.log#exit=0; test:docs/acceptance/evidence/H/tests/h-error-copy.xml; file:core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopyTest.kt | — | ledger@e139b03e |
| H-070 | PASS | cmd:docs/acceptance/evidence/H/logs/h-070.log#exit=0; test:docs/acceptance/evidence/H/tests/h-error-copy.xml; file:app/src/main/kotlin/com/tingyun/smartmistakebook/MistakeExportNotifications.kt | — | ledger@e139b03e |
| H-071 | PASS | cmd:docs/acceptance/evidence/H/logs/h-071.log#exit=0; test:docs/acceptance/evidence/H/tests/h-error-copy.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporter.kt | — | ledger@e139b03e |
| H-072 | PASS | cmd:docs/acceptance/evidence/H/logs/h-072.log#exit=0; test:docs/acceptance/evidence/H/tests/h-error-copy.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt | — | ledger@e139b03e |
| H-073 | PASS | cmd:docs/acceptance/evidence/H/logs/h-073.log#exit=0; test:docs/acceptance/evidence/H/tests/h-i18n.xml; file:app/src/main/res/values/strings.xml; file:app/src/main/AndroidManifest.xml | — | ledger@e139b03e |
| H-074 | PASS | cmd:docs/acceptance/evidence/H/logs/h-074.log#exit=0; test:docs/acceptance/evidence/H/tests/h-i18n.xml; file:app/src/main/res/values/strings.xml | — | ledger@e139b03e |
| H-075 | UNVERIFIED | cmd:docs/acceptance/evidence/H/logs/h-075.log#exit=1; file:app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt | — | ledger@e139b03e |
| H-076 | UNVERIFIED | cmd:docs/acceptance/evidence/H/logs/h-076.log#exit=1; file:app/src/main/AndroidManifest.xml | — | ledger@e139b03e |
| H-077 | UNVERIFIED | cmd:docs/acceptance/evidence/H/logs/h-077.log#exit=1; file:feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeDetailRoute.kt | — | ledger@e139b03e |
| H-078 | PASS | cmd:docs/acceptance/evidence/H/logs/h-078.log#exit=0; test:docs/acceptance/evidence/H/tests/h-i18n.xml; file:app/src/main/res/values/strings.xml | — | ledger@e139b03e |
| H-079 | PASS | cmd:docs/acceptance/evidence/H/logs/h-079.log#exit=0; test:docs/acceptance/evidence/H/tests/h-i18n.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt | — | ledger@e139b03e |
| H-080 | UNVERIFIED | cmd:docs/acceptance/evidence/H/logs/h-080.log#exit=1; file:feature/profile/src/main/java/com/tingyun/smartmistakebook/feature/profile/ProfileRoute.kt | — | ledger@e139b03e |
| H-081 | BLOCKED | cmd:docs/acceptance/evidence/H/logs/h-081.log#exit=1; file:docs/current/privacy-and-data-safety.md | — | ledger@e139b03e |
| H-082 | PASS | cmd:docs/acceptance/evidence/H/logs/h-082.log#exit=0; test:docs/acceptance/evidence/H/tests/h-typography.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt; file:docs/ai-content-disclosure-copy.md | — | ledger@e139b03e |
| H-083 | PASS | cmd:docs/acceptance/evidence/H/logs/h-083.log#exit=0; test:docs/acceptance/evidence/H/tests/h-typography.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt | — | ledger@e139b03e |
| H-084 | PASS | cmd:docs/acceptance/evidence/H/logs/h-084.log#exit=0; test:docs/acceptance/evidence/H/tests/h-typography.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt | — | ledger@e139b03e |
| H-085 | PASS | cmd:docs/acceptance/evidence/H/logs/h-085.log#exit=0; test:docs/acceptance/evidence/H/tests/h-typography.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt | — | ledger@e139b03e |
| H-086 | PASS | cmd:docs/acceptance/evidence/H/logs/h-086.log#exit=0; test:docs/acceptance/evidence/H/tests/h-typography.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt | — | ledger@e139b03e |
| H-087 | PASS | cmd:docs/acceptance/evidence/H/logs/h-087.log#exit=0; test:docs/acceptance/evidence/H/tests/h-typography.xml; file:docs/ai-content-disclosure-copy.md | — | ledger@e139b03e |
| H-088 | PASS | cmd:docs/acceptance/evidence/H/logs/h-088.log#exit=0; test:docs/acceptance/evidence/H/tests/h-typography.xml; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt | — | ledger@e139b03e |
| H-089 | UNVERIFIED | cmd:docs/acceptance/evidence/H/logs/h-089.log#exit=1; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt | — | ledger@e139b03e |
| H-090 | UNVERIFIED | cmd:docs/acceptance/evidence/H/logs/h-090.log#exit=1; file:core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaption.kt | — | ledger@e139b03e |
| I-001 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/base-library.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/base-library.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/i-domain/base-review.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/base-review.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/i-domain/base-profile.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/base-profile.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/i-domain/base-tutor.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/base-tutor.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-002 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/talkback-review.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/talkback-review.log#exit=0; file:docs/research/evidence/2026-11-05-batch-44/i-domain/talkback-review.accessibility.txt | — | ledger@e139b03e |
| I-003 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/neg-unlabeled.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-004 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-merged-label.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-005 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-006 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/base-tutor.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/base-tutor.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-007 | BLOCKED | file:docs/acceptance/runbook.md; device:docs/research/evidence/2026-11-05-batch-44/i-domain/talkback-review.device.json | — | ledger@e139b03e |
| I-008 | PASS | test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.log#exit=0 | — | ledger@e139b03e |
| I-009 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-010 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/base-library.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/base-library.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/i-domain/base-review.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/base-review.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/i-domain/base-profile.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/base-profile.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/i-domain/base-tutor.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/base-tutor.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-011 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/base-review.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/base-review.log#exit=0 | — | ledger@e139b03e |
| I-012 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/neg-small-target.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-013 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/boundary-48dp.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-014 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-015 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/base-profile.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/base-profile.log#exit=0 | — | ledger@e139b03e |
| I-016 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/base-tutor.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/base-tutor.log#exit=0 | — | ledger@e139b03e |
| I-017 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/empty-library.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/empty-library.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-018 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-019 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/theme.log#exit=0; hash:docs/research/evidence/2026-11-05-batch-44/i-domain/theme-colors.json; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-020 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-library.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-library.log#exit=0 | — | ledger@e139b03e |
| I-021 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/neg-low-contrast.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-022 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/boundary-contrast.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-023 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-024 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/theme.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-library.device.json; file:core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/Theme.kt; hash:docs/research/evidence/2026-11-05-batch-44/i-domain/theme-colors.json | — | ledger@e139b03e |
| I-025 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-026 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/theme.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-027 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-028 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/font2-library.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/font2-library.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-029 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/font2-library.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/font2-library.log#exit=0; file:docs/research/evidence/2026-11-05-batch-44/i-domain/base-library.xml; file:docs/research/evidence/2026-11-05-batch-44/i-domain/font2-library.xml | — | ledger@e139b03e |
| I-030 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-031 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/font2-review.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/font2-review.log#exit=0 | — | ledger@e139b03e |
| I-032 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-033 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/font2-library.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/font2-library.log#exit=0 | — | ledger@e139b03e |
| I-034 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-font2-review.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-font2-review.log#exit=0 | — | ledger@e139b03e |
| I-035 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/state-restore.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/i-domain/font2-review.device.json | — | ledger@e139b03e |
| I-036 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-037 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-library.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-library.log#exit=0 | — | ledger@e139b03e |
| I-038 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-review.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-review.log#exit=0 | — | ledger@e139b03e |
| I-039 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-scheme-scan.log#exit=0; file:core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/Theme.kt | — | ledger@e139b03e |
| I-040 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-font2-review.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-font2-review.log#exit=0 | — | ledger@e139b03e |
| I-041 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-042 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-review.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-review.log#exit=0 | — | ledger@e139b03e |
| I-043 | UNVERIFIED | file:docs/acceptance/runbook.md; file:core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/Theme.kt | — | ledger@e139b03e |
| I-044 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/state-restore.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/i-domain/dark-review.device.json | — | ledger@e139b03e |
| I-045 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-046 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-library.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-library.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-047 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-library.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-library.log#exit=0; file:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-library.focus.json | — | ledger@e139b03e |
| I-048 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/neg-unlabeled.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-049 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-library.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-library.log#exit=0 | — | ledger@e139b03e |
| I-050 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-051 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-library.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-library.log#exit=0; file:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-library.focus.json | — | ledger@e139b03e |
| I-052 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-library.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-library.log#exit=0 | — | ledger@e139b03e |
| I-053 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-library.device.json; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-054 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-055 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/empty-review.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/empty-review.log#exit=0 | — | ledger@e139b03e |
| I-056 | PASS | test:docs/research/evidence/2026-11-05-batch-44/i-domain/i-empty-library.xml; device:docs/research/evidence/2026-11-05-batch-44/i-domain/i-empty-library.device.json | — | emulator-5584@2026-11-04T19:07:00Z#boot=831e5d06 |
| I-057 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-library.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/focus-library.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/i-domain/empty-library.device.json | — | ledger@e139b03e |
| I-058 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/empty-library.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/empty-library.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-059 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-060 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/empty-review.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/empty-review.log#exit=0 | — | ledger@e139b03e |
| I-061 | BLOCKED | test:docs/research/evidence/2026-11-05-batch-44/i-domain/i-error-fail-closed.xml; file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-062 | PASS | test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml; device:docs/research/evidence/2026-11-05-batch-44/i-domain/empty-library.device.json | — | ledger@e139b03e |
| I-063 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-064 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/keyboard-search.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/keyboard-search.log#exit=0 | — | ledger@e139b03e |
| I-065 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/keyboard-search.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/keyboard-search.log#exit=0 | — | ledger@e139b03e |
| I-066 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-067 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/keyboard-search.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/keyboard-search.log#exit=0 | — | ledger@e139b03e |
| I-068 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-069 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/keyboard-search.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/keyboard-search.log#exit=0 | — | ledger@e139b03e |
| I-070 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-071 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/state-restore.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/i-domain/keyboard-search.device.json | — | ledger@e139b03e |
| I-072 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-073 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/landscape-library-nav.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/landscape-library-nav.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/i-domain/unit-a11y-tools.xml | — | ledger@e139b03e |
| I-074 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/landscape-review.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/landscape-review.log#exit=0 | — | ledger@e139b03e |
| I-075 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-076 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/landscape-review.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/landscape-review.log#exit=0 | — | ledger@e139b03e |
| I-077 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-078 | PASS | device:docs/research/evidence/2026-11-05-batch-44/i-domain/landscape-library-nav.device.json; cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/landscape-library-nav.log#exit=0 | — | ledger@e139b03e |
| I-079 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-080 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/i-domain/state-restore.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/i-domain/landscape-review.device.json | — | ledger@e139b03e |
| I-081 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-082 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-083 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-084 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-085 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-086 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-087 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-088 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-089 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| I-090 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-001 | FAIL | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-identity.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-identity.xml | — | ledger@e139b03e |
| J-002 | FAIL | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-identity.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-neg-head.log#exit=0 | — | ledger@e139b03e |
| J-003 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-004 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-neg-clean-tree.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-neg-head.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-identity.xml | — | ledger@e139b03e |
| J-005 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-identity.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-identity.xml | — | ledger@e139b03e |
| J-006 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-007 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-008 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-009 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-runbook-coverage.log#exit=0; file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-010 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-mapping-structure.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-mapping.xml | — | ledger@e139b03e |
| J-011 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-identity.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-mapping.xml | — | ledger@e139b03e |
| J-012 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-r8.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-mapping.xml | — | ledger@e139b03e |
| J-013 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-r8keep.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-mapping.xml | — | ledger@e139b03e |
| J-014 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-retrace-at.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-retrace-bad.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-retrace.xml | — | ledger@e139b03e |
| J-015 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-r8.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-mapping.xml | — | ledger@e139b03e |
| J-016 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-aab-metadata.log#exit=0 | — | ledger@e139b03e |
| J-017 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-018 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-019 | BLOCKED | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-signing.log#exit=0; file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-020 | FAIL | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-signing.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-apk-cert.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-release-manifest.log#exit=0 | — | ledger@e139b03e |
| J-021 | FAIL | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-apksigner.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-keytool.log#exit=0 | — | ledger@e139b03e |
| J-022 | UNVERIFIED | file:app/build.gradle.kts; file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-023 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-signing.log#exit=0 | — | ledger@e139b03e |
| J-024 | FAIL | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-keytool.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-apksigner.log#exit=0 | — | ledger@e139b03e |
| J-025 | FAIL | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-keytool.log#exit=0 | — | ledger@e139b03e |
| J-026 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-027 | FAIL | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-signing.log#exit=0; file:app/build.gradle.kts | — | ledger@e139b03e |
| J-028 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-zipalign.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-elf.xml | — | ledger@e139b03e |
| J-029 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-apk16k.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-elf.xml | — | ledger@e139b03e |
| J-030 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-apk16k.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-elf.xml | — | ledger@e139b03e |
| J-031 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-apk16k-selftest.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-elf.xml | — | ledger@e139b03e |
| J-032 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-bundletool-config.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-aab.xml | — | ledger@e139b03e |
| J-033 | FAIL | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-aab16k.log#exit=1; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-bundletool-validate.log#exit=0 | — | ledger@e139b03e |
| J-034 | FAIL | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-aab16k.log#exit=1 | — | ledger@e139b03e |
| J-035 | BLOCKED | device:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-pagesize.device.json; file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-036 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-037 | BLOCKED | device:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-pagesize.device.json; file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-038 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-039 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-040 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-identity.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-aab.xml | — | ledger@e139b03e |
| J-041 | FAIL | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-aab16k.log#exit=1; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-bundletool-validate.log#exit=0 | — | ledger@e139b03e |
| J-042 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-043 | BLOCKED | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-probe.log#exit=0; file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-044 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-probe.log#exit=0 | — | ledger@e139b03e |
| J-045 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-046 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-version-source.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-version.xml | — | ledger@e139b03e |
| J-047 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-version.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-aapt2-badging.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-version.xml | — | ledger@e139b03e |
| J-048 | FAIL | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-version-artifacts.log#exit=0 | — | ledger@e139b03e |
| J-049 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-050 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-version.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-version.xml | — | ledger@e139b03e |
| J-051 | FAIL | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-version-source.log#exit=0; file:app/build.gradle.kts | — | ledger@e139b03e |
| J-052 | FAIL | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-version-artifacts.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-artifact-mtimes.log#exit=0 | — | ledger@e139b03e |
| J-053 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-054 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-055 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-056 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-057 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-058 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-install-cross-signature.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-crosssignature.device.json | — | ledger@e139b03e |
| J-059 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-install-cross-signature.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-crosssignature.device.json | — | ledger@e139b03e |
| J-060 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-probe.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-crosssignature.device.json | — | ledger@e139b03e |
| J-061 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-062 | PASS | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-install-cross-signature.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-crosssignature.device.json | — | ledger@e139b03e |
| J-063 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-064 | UNVERIFIED | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-rollback-doc.log#exit=0; file:RELEASE.md | — | ledger@e139b03e |
| J-065 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-066 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-067 | UNVERIFIED | file:RELEASE.md | — | ledger@e139b03e |
| J-068 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-069 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-070 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-071 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-072 | UNVERIFIED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-073 | BLOCKED | file:docs/acceptance/runbook.md; file:docs/research/2026-10-25-store-launch-materials-checklist.md | — | ledger@e139b03e |
| J-074 | BLOCKED | file:docs/acceptance/runbook.md; file:docs/research/2026-10-25-store-launch-materials-checklist.md | — | ledger@e139b03e |
| J-075 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-076 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-077 | BLOCKED | file:docs/acceptance/runbook.md; file:docs/current/privacy-and-data-safety.md | — | ledger@e139b03e |
| J-078 | BLOCKED | file:docs/acceptance/runbook.md; file:docs/current/privacy-and-data-safety.md | — | ledger@e139b03e |
| J-079 | BLOCKED | file:docs/acceptance/runbook.md; file:docs/research/2026-10-25-store-launch-materials-checklist.md | — | ledger@e139b03e |
| J-080 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-081 | BLOCKED | file:docs/acceptance/runbook.md; file:docs/research/2026-10-25-store-launch-materials-checklist.md | — | ledger@e139b03e |
| J-082 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-083 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-084 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-085 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-086 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-087 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-088 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-089 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| J-090 | BLOCKED | file:docs/acceptance/runbook.md | — | ledger@e139b03e |
| FIX-001 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-002 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-003 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-004 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-005 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-006 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-007 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-008 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-009 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-010 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-011 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-012 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-013 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-014 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-015 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-016 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-017 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-018 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-019 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-020 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-021 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-022 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-023 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-024 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-025 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-026 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-027 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-028 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-029 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-030 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-031 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-032 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-033 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-034 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-035 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-036 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-037 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-038 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-039 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-040 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-041 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-042 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-043 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-044 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-045 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-046 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-047 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-048 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-049 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-050 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-051 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-052 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-053 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-054 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-055 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-056 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-057 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-058 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-059 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-060 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-061 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-062 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-063 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-064 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-065 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-066 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-067 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-068 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-069 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-070 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-071 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-072 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-073 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-074 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-075 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-076 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-077 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-078 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-079 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-080 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-081 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-082 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-083 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-084 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-085 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-086 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-087 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-088 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-089 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-090 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-091 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-092 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-093 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-094 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-095 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-096 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-097 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-098 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-099 | UNVERIFIED | - | — | ledger@e139b03e |
| FIX-100 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-001 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-002 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-003 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-004 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-005 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-006 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-007 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-008 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-009 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-010 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-011 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-012 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-013 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-014 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-015 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-016 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-017 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-018 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-019 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-020 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-021 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-022 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-023 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-024 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-025 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-026 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-027 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-028 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-029 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-030 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-031 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-032 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-033 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-034 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-035 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-036 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-037 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-038 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-039 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-040 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-041 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-042 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-043 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-044 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-045 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-046 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-047 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-048 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-049 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-050 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-051 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-052 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-053 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-054 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-055 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-056 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-057 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-058 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-059 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-060 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-061 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-062 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-063 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-064 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-065 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-066 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-067 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-068 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-069 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-070 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-071 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-072 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-073 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-074 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-075 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-076 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-077 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-078 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-079 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-080 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-081 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-082 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-083 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-084 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-085 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-086 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-087 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-088 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-089 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-090 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-091 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-092 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-093 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-094 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-095 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-096 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-097 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-098 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-099 | UNVERIFIED | - | — | ledger@e139b03e |
| GATE-100 | UNVERIFIED | - | — | ledger@e139b03e |

## 6. 主会话签字区

签字规则（DoD §3、`docs/acceptance/verdicts/README.md`）：

1. 只对单个 id 签字，不写总体「通过」；
2. `reviewer` 不得在 `authors` 内，且必须与该行 `review:` 证据里的名字一致；
3. 签字必须绑 `run_id` 与本报告记录的 `ledger_sha256`；
4. 签 PASS 前，该行必须同时具备：≥2 类独立证据（report 已过）、独立复核文件（当前 0 行）、对抗复核记录（GATE 域）。

| 字段 | 值（主会话填写） |
|---|---|
| 签字人 | |
| 签字 run-id | |
| ledger_sha256 | `e139b03e8b9a005af73d658333f7721907b7e6976718106934c00e096a3eb675` |
| 签字范围（id 列表或「无」） | |
| 签字时间 | |
| 结论 | 不得为「批次 44 验收通过」：FAIL 21 / BLOCKED 73 / UNVERIFIED 436 未清零 |

未签字的 530 行与 670 行 PASS 的复核面，按域回到：`docs/acceptance/runbook.md`（BLOCKED 出口）、`docs/acceptance/verdicts/README.md`（复核文件格式）、本目录 §3（逐条原因）。
