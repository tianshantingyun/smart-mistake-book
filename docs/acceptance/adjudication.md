# 批次 45 终裁：**验收不通过（NOT ACCEPTED）**

> 终裁对象：`docs/acceptance/ledger.md`（1200 行）与批次 45 全部验收证据/复核产物。
> 台账 sha256（最终）：`887e3ba649eb17a70c0f6f7af8a657d606398a10925c6116818265387af689aa`（512,416 B）。
> 最终报告 run-id：`2026-11-05T06:31:41Z-batch45-acceptance-report`（`docs/acceptance/verdicts.md:4`、`domain-index.md:3`）。
> 本次终裁复核 run-id：`2026-11-05T06:54:43Z-batch45-final-adjudication`；源快照 HEAD `87604198feeef198d5dba7343b8c4692c5e8f937`。
> 日期：2026-11-05。本文件是本次 ask 的唯一交付写入；未改台账、未改源码/热区、未引依赖、未输出密钥。

## 0. 结论与签字判定

**目标分支只有第二个成立：`acceptance_report` 不可能在“不放宽判词、不跳过门”的前提下 exit 0；本终裁输出可签字的 BLOCKED 清单，并明确“验收不通过”。**

| 目标分支 | 判定 | 依据 |
|---|---|---|
| A. `acceptance_report` exit 0，可签“通过” | **不可达** | 非 PASS 共 335 行（FAIL 30 + BLOCKED 161 + UNVERIFIED 144）；`--require-complete` 另报 `PASS_WITHOUT_REVIEW`；`pass_with_review=0` |
| B. 可签字的 BLOCKED 清单 + 明确“不通过” | **成立** | §4 的 161 行逐组可复算、逐行有 runbook 出口；§3/§5 逐条列明理由 |

- **可签字的部分**：① “验收不通过（NOT ACCEPTED）”结论；② §4 的 161 行 BLOCKED 清单（只表示“条件/授权缺失，不判 PASS”，不表示条件已具备）；③ §7 不可变项维持。
- **不可签字的部分**：865 条 PASS（无行级独立复核；且 §6 的 F 域复核发现 2 条 PASS 陈述与证据矛盾）、30 条 FAIL、144 条 UNVERIFIED、发版就绪、“批次 45 验收通过”。

**五条独立阻断理由**：

1. `acceptance_report` **exit 1**（`state=FAIL`）；`--require-complete` **exit 1**（`NOT_ACCEPTANCE_COMPLETE`、`PASS_WITHOUT_REVIEW`）。
2. **0 行有行级独立复核**：`pass_with_review=0`、`pass_without_review=865`；10 份域级复核 JSON 全部绑定**旧台账** `bb25ef23…`，且自述 `independence_verified=false`。
3. **30 条 FAIL 中 28 条有当前证据**；另 2 条（H-052/H-066）判词过期，但 H-066 的“转绿”是门用硬编码行号导致的**假通过**，不能据此改判。
4. **161 条 BLOCKED 不全是外部条件**：151 条缺外部条件，10 条缺内部热区/授权；每行有 runbook 出口，不得转 PASS。
5. **证据不可从 HEAD 重放**：两个证据根 `git ls-files=0`；HEAD 上的台账是旧字节 `02ba5307…`；台账/证据/复核 JSON 均只在工作树成立。

## 1. 本次终裁亲手执行的命令与结果

> 以下全部是本会话（2026-11-05 14:46–14:55 +08）实跑；临时输出在 gitignored `build/b45-adjudication/`。未跑 Gradle、未跑 adb/设备、未跑真实 Provider/Play Console/arm64/人工金标；`connectedLocalFirstDebugAndroidTest` 未执行。

| # | 命令 | exit | 关键输出 |
|---:|---|---:|---|
| 1 | `git rev-parse HEAD` | 0 | `87604198feeef198d5dba7343b8c4692c5e8f937` |
| 2 | `git status --porcelain \| wc -l` / `-uall` | 0 | `611` / `4091`（与报告 `dirty_paths=4091` 一致） |
| 3 | `sha256sum docs/acceptance/ledger.md` | 0 | `887e3ba6…89aa` |
| 4 | `python -X utf8 -I tools/ci/acceptance_init.py --check` | 0 | 1200 行 / ID / 域计数通过（成功静默） |
| 5 | `python -X utf8 -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md --out build/b45-adjudication/report.json` | **1** | `counts={PASS:865, FAIL:30, BLOCKED:161, UNVERIFIED:144}`、`state=FAIL`、`violations=[]`、`pass_with_two_kinds=865`、`pass_with_review=0` |
| 6 | 同命令加 `--require-complete` | **1** | `violations=[NOT_ACCEPTANCE_COMPLETE, PASS_WITHOUT_REVIEW]`；后者 detail=`865 个 PASS 行没有 review 证据` |
| 7 | `python -X utf8 -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md` | 0 | `violations=0`、`warnings=140`（全部 `EVIDENCE_REUSED`，最大 72 行共用 `b-migration-matrix.xml`） |
| 8 | 同命令加 `--strict-warnings` | **1** | 140 条复用告警升为违规（`GATE-042` 复现） |
| 9 | `python -X utf8 -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md` | 0 | `gaps=[]`、`total=1200`、`ok=true` |
| 10 | `acceptance_hash_manifest.py --verify` 三份清单 | 0 / 0 / 0 | `EVIDENCE.sha256` 281 文件、`REVIEWS.sha256` 12 文件、`GATES.sha256` 23 文件，全 `OK` |
| 11 | `h_compliance_gate.py --only H-018/H-061/H-062` | 1 / 1 / 1 | 三条仍 FAIL；详见 §3 |
| 12 | `h_compliance_gate.py --only H-052/H-066` | 0 / 0 | 两条门现 PASS，但 H-066 是假通过（§3/§6） |
| 13 | `PYTHONUTF8=1 PYTHONIOENCODING=utf-8 python -I -m pytest tools/tests/test_acceptance_report.py tools/tests/test_acceptance_lint.py tools/tests/test_trace_requirements.py -q` | 0 | `44 passed in 14.42s`；未设 UTF-8 环境变量时同一命令 13 失败，失败栈为子进程 GBK 解码（环境脆弱性，不据此判业务失败） |
| 14 | 台账逐行复算（6 列解析，按 ID 前缀归域） | 0 | `rows=1200`、`dup ids=0`、`bad verdict=[]`、`PASS/FAIL/BLOCKED/UNVERIFIED=865/30/161/144`、证据列 `review:` token **0** |
| 15 | 复核 JSON 绑定复算（10 份） | 0 | 10/10 `ledger_sha256=bb25ef23…`（当前 `887e3ba6…`，**全部失配**）；10/10 `independence.independence_verified=false` |
| 16 | `git ls-files` 三根 + `git show HEAD:…ledger.md \| sha256sum` | 0 | 证据根各 **0**；`verdicts` 2（仅 README/_template）；台账 1（已跟踪）；HEAD 台账 `02ba5307…e65` |

**口径**：`acceptance_report` 的 exit 1 来自 30 条 FAIL 行与 335 条非 PASS，**不是**证据违规（`violations=[]`）。`lint` exit 0 只说明“未发现作弊形态”，140 条复用告警说明 PASS 的证据独立性有折损；`trace` gaps=0 只说明“每行可定位”，不等于“已判定”。

## 2. 逐域裁定（PASS / FAIL / BLOCKED / UNVERIFIED）

| 域 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED | 域裁定 | 主要理由 |
|---|---:|---:|---:|---:|---:|---|---|
| D0 验收基础设施 | 100 | 93 | 2 | 0 | 5 | **FAIL** | D0-030 无逐行终裁 JSON；D0-081 冲突规则缺失 |
| A 功能旅程 | 90 | 65 | 0 | 1 | 24 | **UNVERIFIED** | 大数据/权限拒绝/中断恢复设备用例缺口；A-010 无 Provider |
| B 数据完整性 | 90 | 88 | 0 | 0 | 2 | **UNVERIFIED** | mask=13 partial/corrupt 两格无夹具；迁移矩阵是单用例循环 |
| C 视觉与渲染 | 90 | 77 | 1 | 0 | 12 | **FAIL** | C-065 设备 XML `failures=1`；12 行仅单类证据 |
| D 模型链路 | 90 | 81 | 0 | 9 | 0 | **UNVERIFIED** | 9 条真实 Provider BLOCKED；D-010/D-027/D-017/018 仅工作树成立 |
| E 复习与算法 | 90 | 80 | 0 | 0 | 10 | **UNVERIFIED** | 80 PASS 共用 2 条模块级日志；并发/DST 缺口；E-061 仍缺并发 |
| F 性能与稳定 | 90 | 55 | 2 | 10 | 23 | **FAIL** | F-055/F-057 2h soak 真崩溃；F 复核发现 2 条 PASS 陈述与证据矛盾 |
| G 安全与隐私 | 90 | 82 | 0 | 2 | 6 | **BLOCKED** | G-087 无离线 advisory；G-088 锁文件 NOT_LANDED |
| H 合规与文案 | 90 | 66 | 5 | 12 | 7 | **FAIL** | H-018/H-061/H-062 真缺口；H-052/H-066 判词过期且 H-066 门失效 |
| I 可用性与无障碍 | 90 | 53 | 0 | 5 | 32 | **UNVERIFIED** | TalkBack/IME/真机/人工视觉残余；I-071 复核建议降级 |
| J 发布工程 | 90 | 21 | 13 | 34 | 22 | **FAIL** | 签名/ABI/versionCode/AAB 陈旧；发版 P0 未闭环 |
| FIX 修复与加固 | 100 | 15 | 0 | 85 | 0 | **BLOCKED** | 仅 3 组修复（15 行）有红→绿；85 行含 17 个“不可修登记” |
| GATE 门禁与终裁 | 100 | 89 | 7 | 3 | 1 | **FAIL** | 报告 exit 1、零行级复核、严格 lint 红、AUD-08 FAIL |
| **合计** | **1200** | **865** | **30** | **161** | **144** | **无域 PASS** | — |

> 10 份域级复核的结论（§6）也没有任何一域是 PASS：A/B/D/E/F/H/I=UNVERIFIED，C/J=FAIL，G=BLOCKED。**“无域 PASS”是终裁结论，不是统计口径。**

## 3. FAIL 30 条（逐条可复核）

| id | 检查 | 当前证据与裁判 |
|---|---|---|
| D0-030 | 每个 PASS 有对应逐行 verdict JSON | `d0-self/docs/verdicts.txt`：`OBSERVED real_verdict_json=0`；现有 10 份是域级 JSON，不是 `<ID>.json` |
| D0-081 | 终裁协议：冲突时保留双方判词 | `d0-self/docs/protocol.txt`：`OBSERVED conflict_rule_present=False` |
| C-065 | 恢复文档回放必须留下墨水 | `c-restore-single-rerun.xml:2-5`：`tests="1" failures="1"`，`AssertionError: the restored document must leave ink` |
| F-055 | 长时 2h：基线 | `soak.cmd.log:1-15`：目标 7200s、实际 2723s、`CRASHED=1`、首崩 2504s；`soak-crash-logcat.txt:2-7` FATAL `IllegalArgumentException: Desired retention must be within 0.7..0.97`（`StudyExperienceRepository.kt:737`，入口 `SchedulingSettingsScreen.kt:74`） |
| F-057 | 长时 2h：超限 | 同上；窗口内 ≥1 次 FATAL，未完成 2h |
| H-018 | AIGC 隐式标识覆盖 | 本会话 `--only H-018` **exit 1**：DOCX 无文档级 `/AIGC`；KD-84 部分页丢标；KD-85 模型改卷文本漏标 |
| H-052 | 免责声明文案一致 | 台账证据 `h-052.log` FAIL/exit=1；本会话门已 **exit 0**（`PASS 文案与 PDF 隐式标识现状一致`）。**判词过期**，但缺新版 cmd+test 双类证据与行级复核，不代改 |
| H-061 | 部分页打印保留 `/AIGC` | 本会话 `--only H-061` **exit 1**：`writeSubset` 逐页重绘，文档级 `/AIGC` 丢失（KD-84 open） |
| H-062 | 模型改卷文本带 `/AIGC` | 本会话 `--only H-062` **exit 1**：未纳入 `/AIGC` 判据（KD-85 open） |
| H-066 | 失败文案不拼接异常 message | 台账证据 FAIL/exit=1；本会话门 **exit 0，但属假通过**：`tools/ci/h_compliance_gate.py:655-657` 取硬编码第 826 行，该行现为 `}`（`SecondaryScreens.kt:826`），真实文案在 `:830`。产品面看似已修，门/证据链**不成立**，不得改判 |
| J-001 | 干净树前提 | `j-identity.log:3`：`dirty_paths=4248`；当前实测 4091；`--require-clean` 被拒绝 |
| J-002 | 产物 revision 等于 HEAD | `j-identity.log:21`：`commits_behind_head=32`（J 复核时已漂到 46） |
| J-020 | 使用生产钥匙 | `j-signing.log:4-9`：`RELEASE_*` 4/4 缺失、`tracked_keystores=[]`；`j-apksigner.log:10` `CN=WS8 Ephemeral Local Only, OU=NotForRelease` |
| J-021 | 指纹等于已登记发布指纹 | `j-apk-cert.log` 与 `j-keytool.log` 指纹不同（台账已登记 `9D:C0:7E:9F…` ≠ `A9:E7:6C:AD…`） |
| J-024 | AAB 与 APK 同签名身份 | APK `CN=WS8 Ephemeral Local Only` ≠ AAB `CN=ZCode Release Smoke` |
| J-025 | AAB 证书有效期覆盖发布窗口 | 证据链无有效期字段；AAB 证书 2026-10-30 已过期（`j-domain/00-index.md`）；J-020/021/024/027 独立支持签名不成立 |
| J-027 | keystore.properties 与环境变量契约 | `j-signing.log`：`RELEASE_*` 4/4 缺失、0 个已跟踪 keystore |
| J-033 | AAB ABI 等于 abiFilters | `j-aab16k.log:20`：`['arm64-v8a','armeabi-v7a','x86','x86_64'] != ['arm64-v8a','x86_64']` |
| J-034 | AAB 含 libquickjs.so | `j-aab16k.log:21`：`required native library missing: libquickjs.so` |
| J-041 | 负向：AAB 不含 32 位 .so | `j-aab16k.log:20`：含 `armeabi-v7a`/`x86` |
| J-048 | versionCode 跨构建单调 | `j-version-artifacts.log:2-6`：四份产物全为 `versionCode='2'` |
| J-051 | 版本号生成/注入机制 | `app/build.gradle.kts:33-34` 手写 `versionCode = 2` / `versionName = "0.2.0"`；无生成机制 |
| J-052 | 历史 AAB 与现 APK 的 vc 可比 | AAB mtime 2026-09-30、vc=2，陈旧不可比 |
| GATE-032 | 报告终态 exit 0 | 本会话 report exit 1、`state=FAIL` |
| GATE-035 | `pass_with_review > 0` | 本会话 `pass_with_review=0` |
| GATE-038 | CI 等价 `--require-complete` | 本会话 exit 1，两项违规 |
| GATE-042 | CI 严格 lint | 本会话 `--strict-warnings` exit 1，140 条 `EVIDENCE_REUSED` 升为违规 |
| GATE-091 | AUD-08 `countExactRate >= 0.9` | `gate/aud08-split-gate.txt:4`：`4/7=0.5714 < 0.9`，未过 |
| GATE-092 | AUD-08 fragmentRate | `gate/aud08-split-gate.txt:5`：`0/16=0.0000 <= 0.05` 单条过，但门整体 FAIL |
| GATE-099 | AUD-08 未转绿 | `gate/aud08-split-gate.txt:1-8`：`EXIT=1` |

> 统计口径：**真失败 28 条 + 判词过期 2 条（H-052/H-066）**。H-052 属“证据早于修复”，H-066 属“门失效造成的假绿”；两条都**不能**在缺新版双类证据与行级复核时改成 PASS。这不改变“验收不通过”。

## 4. BLOCKED 161 条（可签字清单；逐组可复算）

| 组 | 行数 | 缺失条件 | 出口（runbook） | id 全列 |
|---|---:|---|---|---|
| 真实 Provider | 10 | 无 API key / 无授权账户 | `runbook.md` §9；环境变量注入 `*_API_KEY`，密钥不落源码 | A-010, D-082, D-083, D-084, D-085, D-086, D-087, D-088, D-089, D-090 |
| arm64 / 16KB（F） | 10 | 无 arm64 真机或镜像 | §9 / §9.1；换机重跑冷启/导出/预览/内存/2h | F-006, F-015, F-024, F-033, F-042, F-051, F-060, F-069, F-078, F-087 |
| 供应链（G） | 2 | 无离线 advisory；锁文件 NOT_LANDED | §9；接入离线 CVE advisory、落 13 个锁后在覆盖态复跑 | G-087, G-088 |
| 合规外部 / 热区（H） | 12 | 6 条外部 + 6 条内部热区 | §9 / §13 FIX-T16–T18 | H-008, H-024, H-025, H-036, H-039, H-040, H-041, H-042, H-043, H-051, H-053, H-081 |
| 无障碍真机（I） | 5 | 无 TalkBack 语音回传驱动 / 第三方 IME / 传感器真机 | §6 / §9 | I-007, I-061, I-070, I-075, I-079 |
| 发布工程（J） | 34 | 干净树 / 生产签名 / arm64 / 成对产物 / Play Console | §9.2 | J-006, J-017, J-019, J-035, J-037, J-038, J-039, J-042, J-043, J-055, J-056, J-057, J-061, J-065, J-068, J-069, J-073, J-074, J-075, J-076, J-077, J-078, J-079, J-080, J-081, J-082, J-083, J-084, J-085, J-086, J-087, J-088, J-089, J-090 |
| 修复单（FIX） | 85 | 17 个修复单的落点/外部产物/独立读回未满足；含 17 个“不可修登记” | §13 FIX-T04–T20 | FIX-016…FIX-100（连续 85 行；不可修登记为 FIX-020/025/030/035/040/045/050/055/060/065/070/075/080/085/090/095/100） |
| 人工金标（GATE） | 3 | 无标注员 / 依赖标注 / 门红 + 金标缺失 | §9 | GATE-096, GATE-097, GATE-100 |
| **合计** | **161** | — | — | — |

- **不是全部外部**：151 行缺外部条件；**10 行缺内部热区/授权**——H-008、H-039…H-043（6 行）+ FIX-020/FIX-025/FIX-080/FIX-085（4 行，对应 FIX-T04/T05/T16/T17）。
- 13 行原先只有 issue、没有 runbook 指针（G-088、H-008、H-024/025/036/039–043/051/053/081），修复环已补齐；`trace_requirements` 新增 `BLOCKED_WITHOUT_RUNBOOK` 后，本会话 `gaps=0`，说明 161 行全部有出口。
- **签字含义**：这 161 行是“条件/授权缺失”，不是产品缺陷，也**不是通过**。取得条件后按 runbook 复跑并提交新证据才能改判；把 BLOCKED 计入 PASS 的任何统计都无效。

## 5. UNVERIFIED 144 条（按域；不得写通过）

| 域 | 行数 | id 全列 | 主要类别 |
|---|---:|---|---|
| D0 | 5 | D0-031, D0-032, D0-033, D0-034, D0-035 | 无逐行终裁 JSON 实例 / 无 verdict schema |
| A | 24 | A-003, A-005, A-006, A-014, A-015, A-023, A-024, A-031, A-032, A-033, A-041, A-049, A-050, A-051, A-058, A-059, A-060, A-065, A-066, A-068, A-069, A-077, A-086, A-087 | 缺大数据 / 权限拒绝 / 中断恢复设备用例 |
| B | 2 | B-077, B-078 | 缺 mask=13 篡改包 / 部分写失败夹具 |
| C | 12 | C-005, C-008, C-010, C-015, C-020, C-045, C-066, C-067, C-068, C-071, C-088, C-089 | 仅 cmd 单类证据；设备侧缺对应拒绝用例 |
| E | 10 | E-007, E-016, E-025, E-034, E-043, E-052, E-061, E-070, E-079, E-086 | 缺并发 / DST 用例；E-061 真库事务已 3/3 绿但**并发**未测 |
| F | 23 | F-002, F-009, F-011, F-012, F-018, F-020, F-027, F-038, F-039, F-047, F-048, F-050, F-054, F-056, F-058, F-063, F-072, F-074, F-081, F-083, F-084, F-085, F-090 | 无真机目标或同机历史基线；低存储 UI 未确认 |
| G | 6 | G-016, G-036, G-072, G-080, G-081, G-089 | 无崩溃采集 / Provider / 截图 / 供应链校验 |
| H | 7 | H-063, H-075, H-076, H-077, H-080, H-089, H-090 | 人工语感、RTL/i18n、真实 PDF 截图 |
| I | 32 | I-005, I-009, I-014, I-018, I-023, I-025, I-027, I-030, I-032, I-036, I-039, I-041, I-043, I-045, I-050, I-054, I-059, I-063, I-066, I-068, I-072, I-077, I-081, I-082, I-083, I-084, I-085, I-086, I-087, I-088, I-089, I-090 | 待独立复核 / 人工听读 / 视觉判断 / IME / 硬件驱动 |
| J | 22 | J-003, J-007, J-008, J-009, J-016, J-018, J-022, J-023, J-026, J-036, J-044, J-045, J-049, J-053, J-054, J-063, J-064, J-066, J-067, J-070, J-071, J-072 | 门禁阶段构建、成对产物、Play、人工复核 |
| GATE | 1 | GATE-098 | 人类标注残余 |
| **合计** | **144** | — | — |

> `trace_requirements` gaps=0 只证明每行能定位；UNVERIFIED 是“没有达到判定口”，不是“失败”，更不是“通过”。

## 6. 独立复核结果（10 份域级 JSON，0 域 PASS）

**共同事实**：`docs/acceptance/verdicts/A…J-review.json` 共 10 份；10/10 的 `ledger_sha256` 均为 `bb25ef23…`（当前台账 `887e3ba6…`，**全部失配**）；10/10 的 `independence.independence_verified=false`；reviewer 是角色标签，不是具名个人/第二模型；台账 **0 行**写入 `review:` 证据 token，故 `pass_with_review=0`。

| 域 | 复核 verdict | 抽样 | 复核要点（原文摘要） |
|---|---|---:|---|
| A | UNVERIFIED | 9 PASS | 9/9 证据计数、时间/APK 绑定、退出码与源断言成立；A-040 有命令腿溯源缺口、A-078 有设备端“不投递”未断言残余 |
| B | UNVERIFIED | 10 PASS + 2 UNVERIFIED | 10 行均可由 ≥2 类证据支撑；BR-1：72 份 cmd 日志被 `.gitignore:31` 忽略、未入库；BR-2：device APK 身份不可机械复算；BR-3：迁移矩阵是单用例循环，不是 72 次独立运行 |
| C | **FAIL** | 13（12 PASS + 1 FAIL） | 12/12 PASS 复核成立；C-065 维持红 |
| D | UNVERIFIED | 10 | D-010 测试名只存在于未提交工作树（HEAD 仍是 `theRequestSchemaIsEighteen`，断言 18）；D-027 文件 HEAD 不存在；D-017/018 所在类 HEAD 必红；D 域只能到 PASS_WORKTREE |
| E | UNVERIFIED | 35 类 311 例重跑 | E-061 真库事务 3/3 绿，但 `ErrorCauseLedgerInstrumentedTest` 三个用例均 `runBlocking` 串行，**不补并发**；FR-E-2：80 PASS 共用 2 条模块级日志，cmd/test 同源 |
| F | UNVERIFIED | 18 | 11 确认 / 4 部分 / **2 REJECTED** / 1 数值无源；F-R1 台账 F-064/F-067 写 dropbox 12，证据为 6；F-R2 F-019 写 300–2150ms，逐轮实际 300–2350ms；F-R3 F-028/029/036 的数值无原始证据；F-055/F-057 FAIL 维持 |
| G | **BLOCKED** | 13 | 13 行均与台账 PASS 一致；G-087/G-088 仍 BLOCKED，6 行 UNVERIFIED；G-012/G-083/G-063/G-009 有文字计数/标签陈旧（low） |
| H | UNVERIFIED | 12 | **F1（高）：H-066 门用硬编码第 826 行，编辑后空转通过**；F2：H-052 台账 FAIL 早于文案修复 2h17m；F3：`h-disclaimer.xml`/`h-error-copy.xml` 的 testcase 名已与当前测试不符；F5：H 证据目录内旧报告绑 `d787ab4f…` |
| I | UNVERIFIED | 8 | 8 行机械复算 + 3 条独立重跑；I-071 建议降级；I 域整体 53 PASS/32 UNVERIFIED/5 BLOCKED，不可签 |
| J | **FAIL** | 21 PASS + 13 FAIL | 21 PASS 复核成立、13 FAIL 全部复现；J-001/J-002 记录值已漂移（脏路径 4248→3935、落后 32→46）；AAB 2026-09-30 旧构建、ABI 4≠2、缺 `libquickjs.so`、证书过期；vc 全 2 |

**裁判**：这 10 份 JSON 是**有价值的复核产物**，但不是行级 review 证据，且全部绑定旧台账；其中 F/H 的发现还直接指出 3 条 PASS 陈述与证据不符、1 个门假通过。**因此“865 PASS”只能读作“机械审计通过”，不能读作“已复核通过”。** 本终裁不代改这些行（只写终裁文件，无台账写权限；且缺新版双类证据）。

## 7. 不可变项（只读确认，本终裁未改行为）

| 项 | 当前证据 | 处置 |
|---|---|---|
| 6C-2：mask=8 不提供 / mask=0 禁止 / 派生索引恒裁 | `BackupSelection.kt:90` `isCreatable = isClosed && mask in ALLOWED_MASKS`；`:140` `require(mask in ALLOWED_MASKS)`；`:166` `ALLOWED_MASKS = linkedSetOf(9, 13, 15)`；`gate/sixc2-source-read.txt:3-17` 记工作树 `git diff` 为空；`gate/sixc2-targeted.xml:2` `tests=3 failures=0` | 未改；行为未漂移 |
| KD-76 | `docs/known-defects.md:2916` 标题 `KD-76 (open, …)` | 维持 open，未重裁 |
| isolation | `docs/known-defects.md:2745`：E-40-04 verdict = PARTIAL，低负载门未程序化，不得写“通过/T4 已排除”；`ws7/lowload-gate-5562.json` `verdict=INVALID`、`reasons=["la_ratio_max"]` | 维持不定案 |
| p09 | `docs/research/evidence/2026-11-06-batch-45/e-device/verify-p09-summary.txt:9-19`：run1 `AssertionError: initial connect failed`（`:109`），run2/3 `2794/2666 ms`；`p09-recompute.txt:3-15` WS7 三次 `2071/1890/1861 ms` | 维持未收敛；不得写“已修/必绿” |
| AUD-08 | `gate/aud08-split-gate.txt:1-8`：`countExactRate 4/7=0.5714 < 0.9`，`EXIT=1` | 维持 FAIL，未转绿 |
| 发版就绪 | §3 J 域 13 FAIL；签名 4/4 缺失、AAB 陈旧且 ABI/库不合格、vc 恒 2、AIGC 缺口、soak 崩溃、质量门红 | **不成立**；7 条 P0 见 §9 |

## 8. 源快照与可重放边界（签字前必读）

1. **HEAD 上的台账不是本终裁的台账**：`git show HEAD:docs/acceptance/ledger.md | sha256sum` = `02ba5307…`；工作树 = `887e3ba6…`。台账虽被跟踪，但 HEAD 字节是旧版。
2. **两个证据根不在版本库**：`git ls-files docs/acceptance/evidence/2026-11-06-batch-45` = **0**、`docs/research/evidence/2026-11-06-batch-45` = **0**；`docs/acceptance/verdicts` = 2（仅 README/_template）。干净 checkout 既没有台账新字节，也没有证据/复核 JSON，**无法重放本终裁**。
3. **日志被忽略**：B 复核 BR-1 实测 `.gitignore:31` 忽略 `*.log`，72 份迁移 cmd 日志等未入库；依赖这些日志的 PASS 在干净克隆中不可复算。
4. **工作树不是干净树**：本会话 `porcelain=611`、`-uall=4091`；报告 `dirty_paths=4091`。J-001 因此维持 FAIL，D 域 4 行只能标 PASS_WORKTREE。
5. **结论**：本终裁的“不通过”与 §4 BLOCKED 清单只对**当前工作树快照 + 本文列出的未跟踪文件**成立；在提交台账、两个证据根与 10 份 review JSON 之前，签字不能从 HEAD 重放。

## 9. 签字条件判定与出口

### 9.1 签字条件是否满足

| 条件 | 要求 | 实值 | 满足 |
|---|---|---|---|
| 报告终态 | exit 0 | **exit 1**（30 FAIL + 335 非 PASS） | 否 |
| 终裁门 | `--require-complete` exit 0 | **exit 1**（`NOT_ACCEPTANCE_COMPLETE` + `PASS_WITHOUT_REVIEW`） | 否 |
| 非 PASS 清零 | 0 | **335**（30/161/144） | 否 |
| 行级独立复核 | `pass_with_review>0` | **0** | 否 |
| 复核 JSON | reviewer∉authors、绑当前台账 | 10 份存在，但绑旧 `bb25ef23…`、`independence_verified=false` | 否（仅“存在”） |
| 台账完整 | 1200 行、无空 verdict | 1200 / 0 | 是 |
| E 域占位 | 0 | report `violations=[]` | 是（残余：C-050 method 仍有 `...` 缩写，见 §9.3） |
| D0/FIX/GATE 覆盖 | 各 100 行有 verdict | 100/100/100 | 是 |
| 反作弊 lint | 0 违规 | 0 违规 / 140 复用告警；strict 模式 exit 1 | 否（严格模式） |
| 追溯 | gaps=0 | 0 | 是 |
| 证据哈希 | 三份 verify 通过 | 281 / 12 / 23 全 OK | 是（仅工作树） |
| 可从 HEAD 重放 | 台账/证据/复核入库 | 证据根 0、HEAD 台账旧字节 | 否 |
| 双门 | 单测门/强门 exit 0 | 批次 44 归档 exit 0；**本会话未跑 Gradle** | 未验证（不得写通过） |
| 发版就绪 | 7 条 P0 闭环 | 未闭环 | 否 |

**判定：签字条件不满足。** 本终裁能够签的只有“验收不通过”与 §4 的可签字 BLOCKED 清单。

### 9.2 解除条件（做到这些才可重判）

1. **FAIL 处置**：30 条逐条给出当前证据与处置；H-052 补新版 cmd+test 双类证据后重判；H-066 先修门（改内容锚点）再重录日志/测试；F-055/F-057 在修复后 APK 上重跑 2h soak；C-065 在修复后夹具上重跑；J 域按 §9.2 取得生产签名/arm64/成对产物；AUD-08 按既定阈值重跑，不得改阈值。
2. **BLOCKED 出口**：161 行按 `runbook.md` §9/§9.2/§13 取得条件后复跑；10 行内部热区/授权需主会话授权或排期；改判时绑定 run-id 与 APK sha。
3. **UNVERIFIED 补证**：优先补 I（32）、A（24）、F（23）、J（22）；人工语感/审美保持 UNVERIFIED，由人签。
4. **行级复核绑定**：对每个拟签 PASS 行写 `review:` 证据（reviewer∉authors，绑冻结后的台账 sha），重跑 report 使 `pass_with_review>0`；域级抽样 JSON 不能替代。
5. **证据入库与可重放**：提交台账（`887e3ba6…` 或更新值）、两个证据根与 10 份 review JSON；处理 `.gitignore:31` 的 `*.log` 白名单，避免干净克隆缺命令证据。
6. **冻结与重生成**：台账冻结 → 重跑 §1 全部门（含单测门/强门）→ 重生成 `verdicts.md`/`acceptance-report.json`/`domain-index.md` → 重算 `EVIDENCE.sha256`/`REVIEWS.sha256`/`GATES.sha256` → 新 run-id + 新签字。
7. **发版 P0**：生产签名与同身份 AAB、ABI/`libquickjs.so` 修正、versionCode 生成机制、AIGC 交付格式补齐、soak 崩溃修复后长时复跑、干净树全量构建、质量门全绿。

### 9.3 本次复核的残余发现（未改判，不得当作已解决）

- **C-050**：method 写 `am force-stop ...; am start;`（省略号），但证据 `c-fontscale-probe.log` 只有探针输出、无逐字 adb 命令；report 的 `PLACEHOLDER_CMD` 只查证据 token，未抓 method 缩写。属“方法不可逐字重放”的证据卫生问题，建议补全命令或降级。
- **F-064/F-067/F-019**：F 复核指出台账陈述与原始证据不符（dropbox 12 vs 6；300–2150 vs 300–2350），本终裁未改台账，保留为 PASS 行的**未处置矛盾**。
- **H-066 门**：`h_compliance_gate.py:655-657` 硬编码行号 826；当前该行是 `}`，门“通过”不证明文案；产品面 `SecondaryScreens.kt:830` 已是固定文案，但缺有效门/测试。
- **工具测试环境敏感**：未设 `PYTHONUTF8=1` 时 44 例中 13 例因 GBK 解码失败；设 UTF-8 后 44 passed。CI 需固定 UTF-8 环境，否则本地门会假红。

## 10. 签字区

| 字段 | 值 |
|---|---|
| 终裁结论 | **验收不通过（NOT ACCEPTED）** |
| 签字范围 | 仅“不通过”结论 + §4 的 161 行 BLOCKED 清单 + §7 不可变项；不签 865 PASS / 30 FAIL / 144 UNVERIFIED / 发版就绪 |
| 台账 sha256 | `887e3ba649eb17a70c0f6f7af8a657d606398a10925c6116818265387af689aa` |
| 最终报告 run-id | `2026-11-05T06:31:41Z-batch45-acceptance-report` |
| 本次终裁 run-id | `2026-11-05T06:54:43Z-batch45-final-adjudication` |
| 源快照 | HEAD `87604198feeef198d5dba7343b8c4692c5e8f937`；`-uall=4091` |
| 证据哈希 | `EVIDENCE.sha256` 281 文件（self `aa5c1346…d574`）；`REVIEWS.sha256` 12 文件（self `bd8e5759…3c1`）；`GATES.sha256` 23 文件（self `7d0c7cb0…2a3`）；本会话三份 `--verify` 全 exit 0 |
| 终裁人 | 终裁记录员（本会话）；结论由主会话确认，不得改写成“通过” |
| 日期 | 2026-11-05 |

> **一句话**：1200 行已枚举、逐项有 verdict，E 域占位已消除、trace 已归零、哈希可验；但 30 FAIL + 161 BLOCKED + 144 UNVERIFIED、0 行级复核、证据不可从 HEAD 重放——**批次 45 验收不通过，发版不成立**。

## 11. 回链

- 计划：`.zcode/plans/plan-2026-11-06-batch-45-acceptance-close.md`。
- 上一批：`docs/research/2026-11-06-batch-44-record.md` §9（FAIL 不签字、S1–S15、批次 45 入口）。
- 台账/DoD/runbook：`docs/acceptance/ledger.md`（`887e3ba6…`）、`dod.md`（§1.5）、`runbook.md`（§9/§9.2/§13）。
- 逐项投影：`docs/acceptance/verdicts.md`；域导航：`docs/acceptance/domain-index.md`；机器报告：`docs/acceptance/acceptance-report.json`。
- 门禁证据：`docs/acceptance/evidence/2026-11-06-batch-45/`（report/lint/trace/require-complete/init-check/三份 sha256）。
- 域证据：`docs/research/evidence/2026-11-06-batch-45/`（d0-self/d-review/e-device/e-review/fix/gate/hash/ws7）。
- 复核 JSON：`docs/acceptance/verdicts/{A..J}-review.json`。
- 6C-2：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupSelection.kt:90,140,166`；`docs/research/evidence/2026-11-06-batch-45/gate/sixc2-source-read.txt`、`sixc2-targeted.xml`。
- 不可变项：`docs/known-defects.md:2916`（KD-76）、`:2745`（isolation）；`docs/research/evidence/2026-11-06-batch-45/e-device/verify-p09-summary.txt`、`p09-recompute.txt`；`gate/aud08-split-gate.txt`。
