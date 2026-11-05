# 批次 45 · FIX 域证据索引（WS3 / 计划 25–48）

> 本目录只承载 FIX 100 行与 20 个修复单的证据。判词口径：**门锁住当前判词**，
> 不冒充运行时行为；设备/Provider 面由 `docs/acceptance/runbook.md` §13 的复跑条件承载。

## 1. 产物

| 文件 | 作用 |
|---|---|
| `fix-tickets.md` | 20 个修复单：id / 严重度 / 覆盖源行 / 落点 / 判据 / 本批处置 |
| `logs/FIX-T01..T20.log` | 每单复发门一次运行（含 `--prove-red` 自突变证明），末行 `exit=0` |
| `logs/FIX-sweep.log` | 20 门全扫 + 全部自突变证明 |
| `logs/FIX-unit-targeted.log` | app 模块定向单测（`StorageRestoreFeedbackTest`、`SchedulingRetentionSliderValueTest`），`--rerun` |
| `logs/FIX-h052-h066.log` | H 域两条文案断言复跑（`h_compliance_gate --only H-052,H-066`） |
| `xml/FIX-T01..T20.xml` | 复发门自测的逐例结果（pytest JUnit XML） |
| `xml/TEST-…StorageRestoreFeedbackTest.xml` | 8 例 0 失败（含本批新增的红→绿用例） |
| `xml/TEST-…SchedulingRetentionSliderValueTest.xml` | 4 例 0 失败（滑杆端点夹回） |

工具与自测：`tools/ci/fix_relapse_gate.py`、`tools/tests/test_fix_relapse_gate.py`（14 例）。

## 2. 复跑命令（逐字）

```bash
python -I tools/ci/fix_relapse_gate.py --all --prove-red
python -I -m pytest tools/tests/test_fix_relapse_gate.py -q
./gradlew :app:testLocalFirstDebugUnitTest \
  --tests "com.tingyun.smartmistakebook.StorageRestoreFeedbackTest" \
  --tests "com.tingyun.smartmistakebook.SchedulingRetentionSliderValueTest" --rerun
python -I tools/ci/h_compliance_gate.py --all --only H-052,H-066
```

## 3. 判定结果（本批实跑）

| 检查 | 结果 |
|---|---|
| `fix_relapse_gate.py --all --prove-red` | 20 门 20 绿，20 条自突变证明全部成立，`EXIT=0` |
| `pytest tools/tests/test_fix_relapse_gate.py` | 14 例全绿 |
| 定向单测 | `BUILD SUCCESSFUL`，8 + 4 例 0 失败（`--rerun` 强制重跑） |
| `h_compliance_gate --only H-052,H-066` | 两条 PASS，`EXIT=0` |
| 台账 FIX 100 行（**修复环后**） | **15 PASS / 85 BLOCKED / 0 FAIL / 0 空 verdict**（17 个“不可修登记”行原写 PASS，按计划“不能修写 BLOCKED+runbook”改判；15 行是真实修复/复跑锁定） |
| `acceptance_report` 对 FIX 域 | 0 违规（全局 0 违规；`state=FAIL` 来自 30 条 FAIL 行） |
| `acceptance_lint` | 0 违规（warnings 140，`EVIDENCE_REUSED` 阈值 `>=5`） |
| `trace_requirements` 对 FIX 域 | 0 缺口（全局 `BLOCKED_WITHOUT_RUNBOOK` 检查下 gaps=0） |

## 4. 真修了什么

**FIX-T02（H-066 同族）**：`SecondaryScreens.kt` 里 6 个 `catch` 把原始异常 message
抄进学生可见文案（`${failure.message.orEmpty()}`），其中包含 `content://` URI 与文件路径。
本批改为固定措辞 + 下一步动作；`storageRestoreFailureMessage` 的非域内分支同样处理，
域内 `BackupRestoreDatabaseClosedException` 的人工措辞保留。新增单测
`genericRestoreFailureDoesNotEchoTheRawExceptionMessage`（修复前必红：旧文案会带
`content://`）。红→绿证据见 `logs/FIX-T02.log`（`RED-PROOF OK`）与
`xml/TEST-…StorageRestoreFeedbackTest.xml`。

其余：FIX-T01（滑杆夹回，`587098f1`）与 FIX-T03（免责声明文案，`ae1234dc`）为批次 44
已修、本批复跑锁定；FIX-T06 夹具已改为闭合轮廓但**设备绿轮未跑**。

## 5. 边界（不冒充通过）

1. **没有独立复核**：FIX 32 条 PASS 的证据是 `cmd` + `test` 两类，没有 `review`；
   `pass_with_review` 仍为 0，DoD §1.5 的独立复核要由 GATE 域（reviewer∉authors）补。
2. **F-055/F-057 的设备与 2h 长时复跑未做**：本批只证明端点不再越界（JVM 单测）。
3. **C-065 设备绿轮未跑**：夹具修复后的落墨断言只在设备上有意义。
4. **KD-84 / KD-85 未修**：分别卡在 `core/export` 已批准 hunk 与 `feature/tutor` 热区。
5. **16 单外部阻塞**：复跑条件逐条写在 runbook §13；门只保证它们没被抬成 PASS。
6. 本目录只读仓库文本与台账，不证明运行时行为。
