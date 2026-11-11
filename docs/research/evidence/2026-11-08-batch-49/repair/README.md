# 批次 49 修复环：高/中 finding 修复与阻塞登记（2026-11-08）

> 范围：只修高/中与门失败；热区 `feature/review`+`app`；开工前逐文件 `git status`。
> 所有命令在本机实跑，日志/产物在本目录及 `../device/`。未验证项在 §4 单列。

## 0. 一句话结论

- **高（F-057 假 PASS）已回退为 FAIL**，台账/复核/计数/索引全链重锚；`acceptance_report`
  回到 **violations=0、900/900 PASS 行有效复核**，计数 **900/22/278/0**。
- **中项 7 类已修**：soak 退出码空证据、06/07 设备绑定自述、校准题决策消费+文案、
  两处哈希漂移、P1-10 模拟上线门、P2-15 截图/无障碍、F-057 判据回退。
- **仍有 3 个阻塞**：12 模块单测门 9 红（他线语义）、acceptance 终裁门 22 FAIL/278 BLOCKED
  （发布环境/合规/终裁门自身）、KD-67 真 Room 端到端（跨 core:data 写入口）。

## 1. 已修（逐条对应 finding）

| # | finding | 修复 | 证据 |
|---|---|---|---|
| 1（高） | F-057 判据被反向改写后记 PASS | 台账恢复 HEAD 正向判据（2h 内至少 1 次 FATAL/超限路径命中）并维持 FAIL；证据恢复单测 XML；922 份复核按新 sha 重绑；计数 901/21→900/22 | `docs/acceptance/ledger.md:638`、`verdicts/rows/F-057.json`、`verdicts.md:12`、`00-index.md §0` |
| 2（中） | `f-soak.sh` 无条件写 `EXIT=0`；F-055/057/063 的 cmd_exit 是空证据 | 脚本改为 run 结果计算 `OUTCOME`/`EXIT` 并以真实状态退出；三行 cmd 证据改 `file:` 出处，新增 caveat；批次49 完成性改由 CSV 逐轮判 | `device/soak/f-soak.sh`、`device/soak/EXIT-CAPTURE-CAVEAT.md`、`rows/F-055|F-063.json` |
| 3（中） | 06/07 侧车声称 serial/APK 由 instrument 日志逐字转写 | 改为 `binding_provenance`：counts 来自日志；serial/APK 来自 `00-snapshot` 构建/安装记录与时间线；`source_log_sha256` 只绑定 counts | `device/06-*.device.json`、`device/07-*.device.json`、`00-index.md:43` |
| 4（中） | 「先做校准题」不兑现、决策被丢弃、文案承诺多问一道 | Root 返回决策并由会话状态机持久化/显示（ASK_CALIBRATION/NO_SAFE/失败三种文案）；卡片不再承诺「多问一道」；JVM 5 例 + 设备 2 例 | `KnowledgeCalibrationDecision.kt`、`KnowledgeReviewSessionViewModel.kt`、`KnowledgeReviewSessionScreen.kt`、`SmartMistakeBookRoot.kt:1396`、`repair/red-green.log.md` |
| 5（中） | StudyReviewPlannerService/driver 哈希漂移 | JSON 增加 `post_report_drift`；报告 §1.2/§8/§10.3 与 p1-9 文档更正；最终 driver 重跑 9 份产物逐位相同 | `calibration/run-manifest.json`、`2026-11-08-review-calibration-report.json`、`repair/calibration-driver-drift.md` |
| 6（中） | P1-10 evaluateSchedulingModels 未跑 | 新增 10 dev-world 合并的 `SchedulingEvaluationOnSimulationTest`，生产评估器断言 `fsrsBeatsBaseline`：fsrs 0.1501 < baseline 0.3731、885 样本、beats=true；真库零样本维持 BLOCKED | `repair/p1-10-evaluate-scheduling-models.md`、`repair/TEST-…SchedulingEvaluationOnSimulationTest.xml` |
| 7（中） | P2-15 缺无障碍/截图 | 四屏 PNG + UiAutomation 语义树；`a11y_audit` STRUCTURAL=PASS、CONTRAST=UNVERIFIED；设备 17/17 绿（新 APK 绑定） | `device/screenshots/`、`device/a11y/`、`device/12-batch49-repair-artifacts.device.json` |

## 2. 阻塞（不能修，已登记）

| 阻塞 | 事实 | 出口 |
|---|---|---|
| 12 模块单测门 exit 1 | 4165 例、9 红（core:database 4、core:data 4、feature:tutor 1），均为 HEAD 他线语义；本环热区外的实现/断言 | `repair/unit-gate-and-kd67-blockers.md §1`、`repair/unit-gate-after.log` |
| acceptance `--require-complete` exit 1 | 900/22/278，violations=0；22 FAIL 含 13 J 发布/7 GATE/2 合规/F-057；278 BLOCKED 为真机/arm64/独立签字等 | `repair/acceptance-gate-explanation.md` |
| KD-67 真 Room e2e | UI 决策消费已补；真实 selector→presenter→真 Room 确认/升级需跨 core:data 写入口与账本草料 | `repair/unit-gate-and-kd67-blockers.md §3` |
| 「多问一道」第二道题 | 候选池只有当前会话已落库的 FOUNDATION 题，`ASK_CALIBRATION` 无 CALIBRATION 候选题面；`(会话,知识点)` 单槽持久化不允许第二道题 | `00-index.md §6.12` |

## 3. 关键实跑（可复跑）

```bash
python -I tools/ci/acceptance_report.py --out docs/acceptance/evidence/2026-11-08-batch-49/gate/acceptance-report.json
python -I tools/ci/acceptance_report.py --require-complete --out docs/acceptance/evidence/2026-11-08-batch-49/gate/acceptance-report-require-complete.json
python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md --out docs/acceptance/evidence/2026-11-08-batch-49/gate/lint.json
python -I docs/acceptance/evidence/2026-11-08-batch-49/make_verdicts_report.py
python -I -m pytest tools/tests/test_batch49_repair_invariants.py -q   # 4 passed
./gradlew :feature:review:testDebugUnitTest --console=plain              # 106/0
./gradlew :core:domain:test --tests '*SchedulingEvaluationOnSimulationTest'  # 1/0
```

设备（emulator-5566，API 34，x86_64）：

```bash
adb -s emulator-5566 install -r -t app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk
adb -s emulator-5566 install -r -t app/build/outputs/apk/androidTest/localFirst/debug/app-localFirst-debug-androidTest.apk
adb -s emulator-5566 shell am instrument -w -e class 'com.tingyun.smartmistakebook.Batch49ReviewCardsAndReprojectInstrumentedTest' com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner   # OK (9 tests)
adb -s emulator-5566 shell am instrument -w -e class 'com.tingyun.smartmistakebook.SchedulingCalibrationSectionInstrumentedTest' com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner   # OK (8 tests)
```

## 4. 未验证 / 残余风险

1. **单测门未绿**：9 红他线语义；本环只验证“未新增红 + 本环面全绿”，不宣称门通过。
2. **真库样本**：P1-10 只证明模拟侧；真库 `evaluateSchedulingModels` 仍 BLOCKED，无数字。
3. **2h soak**：历史会话的退出码无法补捕（脚本 bug），只能用 CSV 完成性；修复后的脚本
   未再做一次 7200s 实跑（时间成本）；下次 soak 才产生真实 `EXIT` 证据。
4. **a11y 对比度**：CONTRAST=UNVERIFIED（无颜色输入），不冒充整体无障碍通过。
5. **KD-67 e2e**：见 §2；UI 层决策消费已设备验证，真 Room 链路未跑。
6. **设备证据绑定**：新 PNG/a11y 绑定 APK `e19f1b22…` / test APK `0252bc24…`，与 06/07 的
   历史 APK `ebb3aa34…` 不是同一构建；两组不可混读。
