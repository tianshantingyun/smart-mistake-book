# 验收运行手册（批次 44）

> 目标：任何人都能在本仓根目录复跑验收机制，得到可复算的 exit code。
> 自动化不能替代的人类语感/审美项，按 `UNVERIFIED` 记录，不由代理签字。

## 0. 环境

- 仓库根：`D:\smart mistake book`（Windows / Git Bash）。
- Python：`3.13.14`；pytest：`9.1.1`（本批实测）。
- 命令前统一加 `-I`，避免工作目录里的同名模块劫持导入。
- Gradle 只在门禁阶段串行运行；本手册的静态门不需要 Gradle。
- 装置：`adb` 在 PATH；设备序列号由 `adb devices` 读取。

## 1. 台账生成与校验

```bash
# 首次生成（只补缺失行；已有行整行保留）
python -I tools/ci/acceptance_init.py --write

# 只校验：1200 行 / ID 唯一 / 域计数 / verdict 合法
python -I tools/ci/acceptance_init.py --check
```

预期：`OK total=1200 domains=13`。计数漂移 → exit 1 并打印
`MISSING_ROW` / `UNEXPECTED_ROW` / `DOMAIN_COUNT` / `TOTAL_COUNT`。

## 2. 三个静态门

```bash
python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md \
  --out build/acceptance/report.json

# 终裁门：仍有 UNVERIFIED/FAIL 时必须 exit 1
python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md \
  --require-complete --out build/acceptance/report-final.json

python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md \
  --out build/acceptance/lint.json

python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md \
  --out build/acceptance/trace.json
```

- `report`：PASS 证据审计。**exit 1 = 有 PASS 不满足条件**。骨架里未执行项为
  `UNVERIFIED`、外部阻塞项为 `BLOCKED`；report 的违规为 0，但这**不是**通过。
  `--require-complete` 在仍有 `UNVERIFIED`/`BLOCKED`/`FAIL` 时 exit 1。
- `lint`：summary/UNVERIFIED/跳过门/自证。exit 1 = 反作弊违规。
- `trace`：缺口清单。骨架里 `<...>` 占位与未落盘 review 会让它 exit 1；
  清零前不得宣布验收完成。

`--partial` 只用于夹具/单元测试，**不得**用在正式台账上。

## 3. 历史假绿回放（必须红）

```bash
python -I tools/ci/acceptance_lint.py \
  --ledger tools/tests/fixtures/acceptance/fake_green_batch42.ledger.md \
  --repo-root . --partial --out build/acceptance/lint-fake-green.json
echo $?   # 期望 1

python -I tools/ci/acceptance_report.py \
  --ledger tools/tests/fixtures/acceptance/fake_green_batch42.ledger.md \
  --repo-root . --partial --out build/acceptance/report-fake-green.json
echo $?   # 期望 1
```

夹具来源：`docs/research/2026-11-04-batch-42-record.md` §2.3——15 例全 `-4`
（零执行），JUnit 仍打印 `OK (15 tests)`。lint 命中
`SKIPPED_GATE_AS_PASS`，report 命中 `CMD_LOG_SKIP_MARKER` 且
`PASS_NEEDS_TWO_INDEPENDENT_EVIDENCE`。

## 4. 蜕变关系

```bash
# 自测：12 条关系全部成立才 exit 0
python -I tools/ci/metamorphic_checks.py --demo --out build/acceptance/metamorphic-demo.json

# 真实产物对：<dir>/<domain>/<relation>.before.json / .after.json
python -I tools/ci/metamorphic_checks.py --pairs-dir build/acceptance/pairs \
  --out build/acceptance/metamorphic.json
```

缺夹具 exit 2，**不得**判 PASS。关系失败输出 `counterexample`。

## 5. 设备截图基线

```bash
SERIAL=$(adb devices | awk 'NR==2 {print $1}')
adb -s "$SERIAL" shell wm size
adb -s "$SERIAL" shell wm density

# 采集当前帧（绑定 serial + APK sha）
APK_SHA=$(sha256sum app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk | cut -d' ' -f1)
python -I tools/ci/screenshot_baseline.py capture \
  --serial "$SERIAL" --out build/acceptance/shots/home.png --apk-sha "$APK_SHA"

# 与基线比（阈值必须显式给出）
python -I tools/ci/screenshot_baseline.py compare \
  --baseline build/acceptance/shots/home.base.png \
  --current build/acceptance/shots/home.png \
  --max-mae 1.0 --max-changed-ratio 0.001 \
  --out build/acceptance/shots/home.diff.json
```

exit：0 阈值内；1 超阈值/尺寸不一致；2 参数或 PNG 解码错误。基线首次建立时
把 `home.png` 复制为 `home.base.png` 并在证据里记录其 sha256；**不得**用
“这次和上次一样”代替基线文件。

## 6. 无障碍语义树

```bash
python -I tools/ci/a11y_audit.py dump --serial "$SERIAL" \
  --out build/acceptance/a11y/home.xml

# 结构检查（触摸目标 48dp + 标签）
python -I tools/ci/a11y_audit.py audit --dump build/acceptance/a11y/home.xml \
  --density-dpi 420 --out build/acceptance/a11y/home.structural.json
echo $?   # 2 = 对比度未测（UNVERIFIED），不是 PASS

# 提供颜色输入后才可能 PASS
python -I tools/ci/a11y_audit.py audit --dump build/acceptance/a11y/home.xml \
  --density-dpi 420 --colors build/acceptance/a11y/home.colors.json \
  --out build/acceptance/a11y/home.json
```

`colors.json` 形状：

```json
{"nodes": [{"bounds": "[10,10][200,200]", "fg": [0,0,0], "bg": [255,255,255]}]}
```

exit：0 = 结构无违规且对比度 ≥4.5；1 = 结构违规或对比度不足；
2 = 对比度未测（`UNVERIFIED`）。屏幕阅读器语感仍为人工残余。

## 7. 证据规则

- 一行 `PASS` 至少两种独立证据类：`cmd` / `test` / `device` / `review`。
- 证据 token：`kind:repo/relative/path#k=v`，多个用 `;` 分隔。
- `cmd` 日志必须含 `EXIT=0`（或 `exit_code: 0`），且不能含 `-4` /
  `AssumptionViolated` / `SKIP`。
- `test` XML 必须 `tests>0`、`failures=0`、`errors=0`。
- `review` JSON 必须有 `reviewer`、`run_id`；reviewer 不在 `authors`。
- `device` JSON 必须有 `serial`、`apk_sha256`。
- summary / 批次 `record` / 复盘文档不得作为证据。

## 8. verdict 决策

| 情况 | verdict | 下一步 |
|---|---|---|
| 六面证据齐全，report/lint exit 0 | `PASS` | 终裁签字 |
| 有可复现失败 | `FAIL` | 进 FIX 域，先红后绿 |
| 外部条件缺失 | `BLOCKED` | 按 §9 的 runbook |
| 没跑/占位/人类语感 | `UNVERIFIED` | 补命令或人工抽检 |

**红线**：`UNVERIFIED` 和 `BLOCKED` 都不能写 `PASS`；跳过的门不能计通过；
summary 不能当证据。

## 9. BLOCKED 清单与出口

| 项 | 缺失条件 | 出口 |
|---|---|---|
| 真实 Provider | 无 API key / 无授权账户 | 配 `*_API_KEY` 环境变量后跑 D 域；密钥不落源码 |
| 生产签名 / AAB | 无 `RELEASE_*` 签名材料 | 按 `RELEASE.md` 生成；AAB 用 bundletool 校验 |
| arm64 / 16KB | 无 arm64 真机 | 换机后重跑 J 域 native 装载检查 |
| 性能真机基线（F 域） | 无 arm64 真机 | 换机后按 `docs/research/evidence/2026-11-05-batch-44/f-domain/00-index.md` §7 重跑冷启/导出/预览/内存/2h |
| 人工金标 | 无标注员 | 交付标注包；在此之前 D/H 域相关行 `UNVERIFIED` |
| 屏幕阅读器语感 | 无 TalkBack 人工走查 | 人工抽检；自动项仍按 `a11y_audit` 记录 |

### 9.1 F 域残余（UNVERIFIED，不冒充 PASS）

| 行 | 残余 | 复跑/升级 |
|---|---|---|
| F-002/F-020 | 冷启 2000ms、首屏 500ms 是真机目标；模拟器 3.0–3.9s / 7 轮 janky 不作产品判定 | arm64 真机 `StartupBenchmark` 或 `am start -W` |
| F-009/F-018/F-027/F-054/F-072/F-081/F-090 | 无同设备同构建历史基线 | 同机同 APK 连跑两批，保留两批 JSON/dropbox |
| F-011/F-012/F-038/F-039/F-056/F-058/F-063/F-074/F-083/F-085 | 无产品阈值或只跑单窗/单次 | 先由产品定义目标，再重复窗口 |
| F-047/F-048 | 200MB 未定义进程/相位；导出宿主峰值不可归因 | 干净安装 + UI 手工导出 + 50ms PSS 切片 |
| F-084 | 低存储只做到 /data 98% 启动（PID 存在、FATAL/ENOSPC 0，UI 未确认） | 专用模拟器填至 98% 后走完整导出/预览并确认失败文案 |

### 9.1.1 E 域残余（并发 / DST，批次 47 WS3）

> 本节放在 §9.1 之下是**刻意**的：E-007/016/025/034/043/052/070/079/086 的台账
> `方法` 列写的是「出口见 `docs/acceptance/runbook.md` §9.1」，而台账改字会重签
> 920 份行级 JSON 的 `ledger_sha256`。故不改台账、在此补足 §9.1 的 E 域内容；
> 这 9 行仍为 `BLOCKED`，本节只写出口，不构成通过。

**分组与条件（缺一不可）**

| 组 | 行 | 缺失条件（内部工程可补） |
|---|---|---|
| 并发（纯函数/投影/提取/落账） | E-007、E-016、E-025、E-034、E-043、E-052、E-079 | 仓内无并发调用用例；算法本身是纯函数/幂等投影，但「并发下结果一致、无重复写」没有可执行证据 |
| 并发 + 真事务 | E-070 | 同上，且 JVM 用假端口不模拟 SQLite 事务；真事务需设备轮（`ErrorCauseLedgerInstrumentedTest` 已有，缺撤回竞争用例） |
| DST | E-086 | `StudyDayMathTest` 只有 `America/New_York` 冬季 EST 探针；缺 2026-03-08 春季跳变与 2026-11-01 秋季回拨探针 |

**步骤**

1. 先补用例（只加测试，不改被测实现；`core/domain`、`core/data` 非热区）：
   - E-007 `FsrsScheduleMathTest`：`Int`/多线程同输入调用，断言 `stability/difficulty/interval` 与串行逐位一致。
   - E-016 `ReviewCapacityPolicyTest`：并发 `dailyQuota`/`capacityPerDay` 同输入，断言结果集与串行一致。
   - E-025 `ExamQuotaPlannerTest`：并发 `plan` 同输入，断言配额、deficit、排序逐位一致。
   - E-034 `MissedDayRebalancePolicyTest`：并发 `plan` 同输入，断言预算、horizon、daysNeeded 一致。
   - E-043 `LearningProjectorTest`：同 learner 并发投影，断言 attempt id 幂等、无重复应用、冲突 payload 确定性拒绝。
   - E-052 `ErrorCauseExtractorTest`：并发提取同 scope，断言至多一个候选、无重复 observation。
   - E-070 `ErrorCauseRetractorTest` + `ErrorCauseLedgerInstrumentedTest`：落账与撤回竞争，断言 scope 内恒 ≤1 条 ACTIVE、`active_key` 清空、审计行保留。
   - E-079 `ReviewPlannerV2Test`：同快照并发 `plan`，断言 planId/fingerprint 与串行一致。
   - E-086 `StudyDayMathTest`：`America/New_York` 2026-03-08 01:59/02:01/03:00 与 2026-11-01 01:30/01:30（重复小时）探针，断言学习日不跳变、不重复。
2. 门禁阶段串行复跑（不在本批并行阶段跑；路径按模块归属）：
   ```bash
   ./gradlew --offline -q :core:domain:test --tests <上述 domain 测试类>
   ./gradlew --offline -q :core:data:testDebugUnitTest --tests <上述 data 测试类>
   ```
   设备轮（E-070）：
   ```bash
   python -I tools/ci/am_instrument_recorder.py --serial <online-serial> \
     --package com.tingyun.smartmistakebook.core.database.test \
     --class com.tingyun.smartmistakebook.core.database.ErrorCauseLedgerInstrumentedTest#<新增并发撤回用例>
   ```
3. 证据落盘到 `docs/research/evidence/<批次>/e-domain/`，文件名带行号；台账 `evidence`
   追加 token 与行级复核 JSON 以**该行测试方法名**复算（不得复用同组 checks）。
4. 判据：JVM XML `tests>0 failures=0 errors=0 skipped=0` 且用例内断言并发结果与串行逐位一致；
   设备轮 exit 0、`device.json` 的 serial/APK sha 与本轮一致、scope 内 ACTIVE≤1；DST 探针期望值
   写进用例断言（不是截图描述）。任一条缺证据即维持 `BLOCKED`。
5. 责任人：**E 域工程（core/domain、core/data 测试作者）**；复核人：独立 E 域复核（不得等于作者）。

## 10. 证据哈希清单

```bash
# 生成（排除清单自身）+ 输出清单 sha256
python -I tools/ci/acceptance_hash_manifest.py \
  --root docs/research/evidence/2026-11-05-batch-44 \
  --out build/acceptance/evidence-sha256.txt --self-hash

# 重算并比对：缺文件 / 哈希不符 / 清单外多文件 → exit 1
python -I tools/ci/acceptance_hash_manifest.py \
  --root docs/research/evidence/2026-11-05-batch-44 \
  --verify build/acceptance/evidence-sha256.txt
```

清单只写仓库相对 POSIX 路径；只输出哈希与路径，不输出文件正文；不含密钥/令牌；
清单自身哈希写进终裁 JSON。

## 11. 边界与纪律

- 6C-2 三项不可变：mask=8 不提供、mask=0 禁止、派生索引恒裁；只可加不变量测试。
- 热区禁改：`core/domain`、`core/ui`、`feature/*`、`docs/current`、
  `settings.gradle.kts`、`Application/Root`；`core/export` 只做已批准 hunk。
- `tools/ci/acceptance_ci.patch` 只是提案，**不改实际 CI**。
- 不引新依赖；截图/无障碍用 adb + stdlib。
- 共享工作树：不做 git 写；不提交锁文件/密钥/`build/`。
- 设备证据必须绑定 APK sha 与源快照；不同轮次的“绿”不可互相外推。

## 12. 终裁协议

`docs/acceptance/verdicts/<ID>.json` 至少包含：

```json
{
  "id": "A-001",
  "verdict": "PASS",
  "run_id": "2026-11-06T09:00:00Z-batch44",
  "reviewer": "independent-reviewer",
  "authors": ["implementer"],
  "ledger_sha256": "<64 hex>",
  "evidence": ["cmd:build/acceptance/x.log#exit=0", "test:build/acceptance/x.xml"],
  "evidence_sha256": {"build/acceptance/x.log": "<64 hex>"},
  "adversarial": {"attempted": true, "result": "rejected"},
  "notes": "范围与残余"
}
```

- `reviewer` 不得等于作者，不得 `self_review=true`；
- `run_id` 与本次命令/设备轮次一致；
- `ledger_sha256` 由 `sha256sum docs/acceptance/ledger.md` 得出；
- 终裁只对单行 id 生效，不写总体“通过”。
- 冲突处理：同一行出现两份判词时，**冲突时保留双方判词**并登记冲突项，
  不合并、不覆盖、不以任一方单独签字。
- 行级终裁的字段与类型由 `docs/acceptance/verdicts/verdict.schema.json` 校验；
  `checks` 必须含可复算值（XML tests/failures、CMD exit、文件 sha、设备关键行）。

## 9.2 J 域（发布工程）出口

J 域证据与命令见 `docs/research/evidence/2026-11-05-batch-44/j-domain/00-index.md`。
以下每组在缺失条件下不得判 PASS，按出口复跑后才可改判。

| 行组 | 缺失条件 | 出口 |
|---|---|---|
| J-003 / J-007 / J-022 | 验收阶段不串行跑 Gradle；干净树构建、构建确定性、签名负向未执行 | 门禁阶段跑 `:app:assembleStrictOfflineRelease`、`:app:bundleStrictOfflineRelease`；两次 clean 构建比对字节；`validateReleaseSigning` 缺变量必须失败 |
| J-019–J-027 | 无生产签名材料（`RELEASE_*` 4/4 未设） | 配齐环境变量后 `apksigner verify --print-certs`，指纹等于已登记发布指纹；AAB 证书在有效期内且与 APK 同身份 |
| J-028–J-036 | 无 16384 页设备 | 换 16KB 设备/模拟器：`adb shell getconf PAGE_SIZE`=16384，冷启并触发三条 native 路径，`logcat` 中 FATAL=0 |
| J-037–J-045 | 无 arm64 真机；x86_64 宿主不能起 arm64 AVD | 换 arm64 真机重跑安装、启动、16KB 运行时 |
| J-055–J-063 | 无成对 vc 产物、无同钥匙 | 同钥匙出 vc N 与 N+1，按 §6.4 路径 1/2 跑升级/降级；判据取输出含 Success 且事后 `dumpsys` vc 等于目标 |
| J-064–J-072 | 回滚需成对包与生产签名 | 上一版 APK/AAB 可覆盖安装；备份导出/恢复演练；迁移非破坏（`RELEASE.md` Rollback） |
| J-073–J-081 | Play Console 账号与商店素材缺失 | 按 `docs/research/2026-10-25-store-launch-materials-checklist.md` 备料并上传 |
| J-082–J-090 | Play 封闭测试轨道未建立 | 创建内部/封闭轨道，上传 AAB，登记测试账号与反馈渠道 |

## 9.4 D 域（真实 Provider）出口

本节的执行对象是 D-082..D-090 与 FIX-T08（`A-010` 同组）。缺的条件只有一条：**真实 Provider
凭据与上游账号**；在此之前的复跑一律不成立，这 9 行保持 `BLOCKED`，不得判 `PASS`（D-090 就是
这条口径本身）。本节替代 FIX-T08 里「按本文 D 域步骤执行」原先指向的空节。

**前置条件（全满足才开工）**

- **口径澄清（台账与本节不一致处，按本节执行）**：D-082..D-090 的 `方法` 列写「以环境变量注入
  `*_API_KEY`」，但代码里没有任何读取该环境变量的入口——`grep -rn "getenv" --include=*.kt core app`
  的 12 处命中只有 `CI` 判据与 `SMARTMISTAKE_AUDIT_DATABASE`
  （`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/mistake/ClassificationCalibrationAuditTest.kt:538`），
  没有 Provider 密钥；androidTest 也没有读取密钥的 `InstrumentationRegistry.getArguments()`；
  Android 应用进程同样看不到宿主环境变量。故真实凭据只能经设备 `模型设置` 输入。台账该句
  属不可执行文案；改它要重签 920 份行级 JSON，本批不动，登记为缺陷。
- 真实 Provider 账号 + Base URL + 模型 ID + API Key。密钥只由执行人在设备 `模型设置` 里输入，
  不写进命令、日志、截图或提交；`ModelApiKey.toString()` 恒为 `ModelApiKey([REDACTED])`
  （`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ModelConfigurationStore.kt:143`）。
- 网络型构建：只有 `localFirst` 变体声明 `INTERNET` 并启用模型服务
  （`app/src/localFirst/AndroidManifest.xml:3`、`app/src/localFirst/.../FlavorCapabilityFactory.kt`），
  包名 `com.tingyun.smartmistakebook.localfirst`（`app/build.gradle.kts:27` + `:59`）。
  `strictOffline` 的设置页直接显示「当前版本未启用大模型服务」，不能用于本节
  （`app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt:379`）。
- 设备：任一在线设备即可（D 域无 arm64/16KB 判据）；记录 serial 与 `getconf PAGE_SIZE`。

**步骤**

1. 门禁阶段串行构建 `:app:assembleLocalFirstDebug`，从产物取 `sha256sum`（记作 APK sha）与 run-id。
2. `adb -s <serial> install -r <apk>`；
   `adb -s <serial> shell am start -n com.tingyun.smartmistakebook.localfirst/com.tingyun.smartmistakebook.MainActivity`。
3. 进 `模型设置`，填 模型服务 / 模型 ID / Base URL / API Key，点「测试模型能力」直到通过
   （校验规则：HTTPS、URL 不含账号信息/查询/片段、密钥 ≤4096 且无空白字符，
   `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings/ModelConfigurationValidator.kt:28`、`:137`、`:151`）。
4. 逐行取证，落到 `docs/research/evidence/<批次>/d-domain/`，文件名带行号与 run-id。
5. 只有第 4 步证据齐全后，才可把对应行从 `BLOCKED` 改判；改判须同时提交新的行级复核 JSON。

**逐行判据**（观察点与台账 `检查` 列逐条对应；证据里必须同时出现 run-id、APK sha、设备 serial）

| 行 | 观察点 | 判据 |
|---|---|---|
| D-082 | 设置通过后走一次拍照→分题→讲题主路径 | 上游返回 200 且 app 侧任务完成；上游请求 id 与 APK sha、run-id 在同一份证据里 |
| D-083 | 分别制造 401（错 key）、429（限流）、5xx（上游故障） | UI 失败码分别映射为 `AUTHENTICATION_FAILED` / `RATE_LIMITED` / `SERVICE_UNAVAILABLE`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/AppFailure.kt:228-242`），恢复后重试成功；不接受只截一句文案 |
| D-084 | 接近与超过上下文/图片尺寸上限的输入 | 边界内成功；边界外 fail-closed（无半条任务），证据含上游错误码与 app 文案 |
| D-085 | 请求进行中取消 | 本地无终态任务/无半条输出；上游侧能核对到取消或未计费（上游面板或用例记录二选一，缺一即未达判据） |
| D-086 | 真实限流后重试 | 重试次数与本地账本计数逐条对得上；不产生重复计费记录 |
| D-087 | 单轮讲题的工具轮派遣 | 本地派遣数 ≤ `ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES = 6`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:68`），且与上游计费次数一致 |
| D-088 | 抓真实失败报文 | 学生原文只出现在受控诊断缝隙，常规日志/上传链路无原文；证据按此脱敏 |
| D-089 | 证据头 | run-id、APK sha、设备 serial、密钥来源（「人工在设备输入」）四项齐全，全文无密钥片段 |
| D-090 | 无 key 时的复跑 | 只能登记 `BLOCKED`；任何「无 key 也绿」的结果无效，按假通过处理 |

**责任人**：内部发布负责人（执行与取证）；Provider 账号持有人（提供凭据与用量面板只读权限）。

## 13. FIX 域修复单与出口（批次 45 WS3）

20 个修复单由批次 44 的 FAIL 21 + BLOCKED 73 归并而成；每单一个复发门
（`python -I tools/ci/fix_relapse_gate.py --gate FIX-Tnn`，全跑 `--all --prove-red`）。
门锁住当前判词：要关闭 KD-84/KD-85 或把外部阻塞改判成 PASS，必须同时提交独立读回/
设备/复核证据并显式更新门，不能让判词随文档漂移。

| 单 | 严重度 | 主题（覆盖行） | 处置 | 复跑条件 / 出口 |
|---|---|---|---|---|
| FIX-T01 | 高 | 复习滑杆端点崩溃（F-055/F-057） | 已修复（`587098f1`，band 夹回） | 复跑条件=`./gradlew :app:testLocalFirstDebugUnitTest --tests "com.tingyun.smartmistakebook.SchedulingRetentionSliderValueTest"`；端点直落 `toDouble()` 或删端点用例时复发门转红 |
| FIX-T02 | 中 | 失败文案拼接原始异常 message（H-066 及同族 6 处） | 已修复（本批去掉 `${failure.message}`，保留域内异常的人工措辞） | 复跑条件=`python -I tools/ci/fix_relapse_gate.py --gate FIX-T02`；再出现 `.orEmpty()` 拼接即转红 |
| FIX-T03 | 低 | 免责声明文案与 PDF 隐式标识现状不一致（H-052） | 已修复（`ae1234dc`；`h_compliance_gate --only H-052` 现 exit 0） | 复跑条件=`python -I tools/ci/h_compliance_gate.py --all --only H-052`；文案与注入现状再次冲突即转红 |
| FIX-T04 | 中 | 部分页打印丢文档级 `/AIGC`（KD-84；H-018/H-061） | 已修复（批次 47 H 域：`MistakePdfDelivery.relabelSubset` 重注入 + 设备先红后绿 + 宿主 pypdf 独立读回，KD-84 closed） | 复跑条件=`python -I tools/ci/fix_relapse_gate.py --gate FIX-T04`；证据 `h-061-pypdf-b47.log` 缺失或子集重注入实现回退即转红；设备复跑同台账 H-061 方法列 |
| FIX-T05 | 高 | 模型改卷文本未纳入 `/AIGC` 判据（KD-85；H-018/H-062） | BLOCKED：落点跨 `feature/tutor`（热区）需主会话授权 | 复跑条件=纯用户来源经模型改卷后导出 PDF 被独立读者读回 `Label=1`、`ProduceID=输入 sha`；仅手工编辑仍无 `/AIGC`；`MistakePdfPaperEdit` 单测覆盖两向 |
| FIX-T06 | 中 | 恢复回放零面积夹具（C-065） | 已修夹具（闭合三角形 `M/L/L/Z`，工作树未提交），设备绿轮未跑 | 复跑条件=`am instrument ... TutorVisualRebuildRestoreInstrumentedTest#theRestoreLegAloneReplaysOfflineWithZeroDispatchBookings` 在修复后 APK 上绿；夹具退回开口线段时本门转红 |
| FIX-T07 | 中 | DOCX 无文档级 `/AIGC`（H-018） | BLOCKED：先在 `core/export` 加 docProps 标识，再做独立 DOCX 读回 | 复跑条件=DOCX 产物被独立读者读回文档级 `/AIGC`；仅在代码里出现字面量不算关闭（本门会转红） |
| FIX-T08 | 高 | 真实 Provider 端到端（A-010/D-082..D-090，9 项） | BLOCKED：无真实密钥与上游 | 复跑条件=按本文 §9.4 D 域出口执行（真实凭据经设备 `模型设置` 输入，不用环境变量），证据绑定 run-id 与 APK sha，且不回显密钥 |
| FIX-T09 | 高 | arm64 设备矩阵（F-006..F-087 十项 + J-035/J-037..J-043，16 项） | BLOCKED：两台在线设备均 x86_64 | 复跑条件=换 arm64 真机后按 §9.2 J-037–J-045 重跑冷启/首帧/导出/内存/长时/进程死亡 |
| FIX-T10 | 高 | 生产签名与指纹（J-019..J-027，6 项） | BLOCKED：`RELEASE_*` 4/4 未设 | 复跑条件=配齐签名环境变量后 `apksigner verify --print-certs`，指纹等于已登记发布指纹且 AAB/APK 同身份、证书覆盖发布窗口 |
| FIX-T11 | 中 | 版本号单调与版本源（J-048/J-051/J-052/J-086，4 项） | BLOCKED：`versionCode` 仍是手写常量 2，改版本源属发布裁定 | 复跑条件=引入版本源并在两次构建上验证 vc 单调；历史 AAB 与现 APK 的 vc 可比 |
| FIX-T12 | 中 | AAB 16KB / ABI / quickjs（J-033/J-034/J-041/J-088，4 项） | BLOCKED：AAB 陈旧且宿主无 16KB 设备 | 复跑条件=干净树重出 AAB 后 `j_release_verify.py aab16k` exit 0（ABI 等于 abiFilters、含 `libquickjs.so`、无 32 位 `.so`） |
| FIX-T13 | 中 | 干净树与产物 revision（J-001/J-002/J-006，3 项） | BLOCKED：共享工作树有未提交改动，验收阶段不跑 Gradle | 复跑条件=提交后干净树 `j_release_verify.py identity --require-clean --require-head` exit 0 |
| FIX-T14 | 中 | 商店素材与 Play 控制台（J-017/J-073..J-085/J-089/J-090，16 项） | BLOCKED：无账号、无素材、无托管 URL | 复跑条件=按 `docs/research/2026-10-25-store-launch-materials-checklist.md` 备料并上传，登记内部/封闭轨道与测试账号 |
| FIX-T15 | 高 | 成对产物升级/降级/回滚（J-055..J-057/J-061/J-065/J-068/J-069，7 项） | BLOCKED：无成对 vc 产物与同钥匙 | 复跑条件=同钥匙出 vc N 与 N+1，按 §9.2 跑升级/降级/回滚，`dumpsys` 事后 vc 等于目标且数据保留 |
| FIX-T16 | 高 | 青少年模式（H-039..H-043，5 项） | BLOCKED：G7 第 3/4/8 项未落地，`feature/profile` 等热区未空出 | 复跑条件=年龄确认与监护人同意接线，隐私政策专章发布；`h_compliance_gate --topic minor` exit 0 |
| FIX-T17 | 高 | AIGC 显式角标 G1/G2（H-008） | BLOCKED：`feature/tutor`、`core/ui` 热区禁改 | 复跑条件=讲题与配图界面显示“AI 生成”角标并上设备截图；`h_compliance_gate --only H-008` exit 0 |
| FIX-T18 | 中 | 隐私政策 / data safety / 备案 / 英文材料（H-024/H-025/H-036/H-051/H-053/H-081，6 项） | BLOCKED：需开发者域名、Play 后台、CAC 备案名单 | 复跑条件=隐私政策公开 URL 可访问、表单提交回执与备案号回填后重跑 H 域对应断言 |
| FIX-T19 | 中 | 无障碍真实驱动 / IME / 旋转（I-007/I-061/I-070/I-075/I-079，5 项） | BLOCKED：无 TalkBack 语音回传驱动、无第三方 IME、无传感器真机 | 复跑条件=按 §6 无障碍与 §9.1 残余条目在真机走查并落设备证据 |
| FIX-T20 | 低 | 供应链与台账协议门（G-087/G-088 + D0-030/D0-081） | BLOCKED：无离线 advisory 库；KD-72 锁未落地；D0 两行属 D0 域自验收 | 复跑条件=接入离线 CVE advisory 并联网取证；`--write-locks` 落 13 个锁后 `check_lockfiles.py` 在覆盖态 exit 0；D0-030/D0-081 由 D0 域处置 |

## 9.3 B 域（数据完整性）出口（批次 47 WS3）

B 域当前 **0 FAIL / 2 BLOCKED（B-077、B-078）**。这两行不是外部供应商问题，
而是 **androidTest 夹具缺口**（内部工程可执行）；在补齐夹具并产出新证据前维持
`BLOCKED`，不得用 mask=9/15 的结果外推到 mask=13，也不得写 `PASS`。

**已核实的夹具现状**（本轮读源码，非引用旧结论）：

| 行 | 缺口 | 现有夹具（只读核对） |
|---|---|---|
| B-077 | mask=13 的 **partial（部分恢复）** 回执 | `BackupRestoreInstrumentedTest#settingsWriteFailureIsReportedAsPartialWithoutRollingBackTheRestoredDatabase`（`:851`）只覆盖全选（默认 `FULL`/mask=15）；`BackupSelectionRestoreInstrumentedTest#subsetThirteenRestoreKeepsSessionsAndStillTrimsDerivedIndex`（`:120`）是 mask=13 的 **clean** 往返，不是 partial |
| B-078 | mask=13 的 **corrupt（篡改包）** 拒绝 | `#invalidDigestRowCountsAndClosureVersionAreRejectedBeforeSwapAndLiveDataStays`（`:278`，mask=9）与 `#fullSelectionWithRowCountsThatDisagreeWithTheStagedDatabaseIsRejected`（`:392`，mask=15）；无 mask=13 篡改包 |

**出口（两行共用同一套步骤，判据分行）**

1. 在 `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/`
   增加夹具（`core/data` 不在热区；只加测试，不改 `SmbkSelectionPlan` 常量）：
   - B-077：用 `BackupSelection.of(issueBookSessionsAndImages())`（mask=13）创建包，
     复用 `FailingReviewReminderRepository` 制造设置顺序写失败，走 `repository.restore`
     断言 `receipt.settings == RestoreSettingsOutcome.WRITE_FAILED`、会话表仍为 1 行、
     数据库不回滚、失败前已成功的设置键按 KD-47 保留。拟命名
     `subsetThirteenPartialSettingsWriteIsReportedWithoutRollingBack`。
   - B-078：用 mask=13 包分别篡改 `closureDigest` / `rowCounts` / `closureVersion` /
     `categoryMask` / `schemaVersion`，断言 `validate` 与 `restore` 都在**交换前**拒绝且
     原库行数不变。拟命名 `subsetThirteenTamperedPackageIsRejectedBeforeSwap`。
2. 设备复跑（两条命令都必须 exit 0 且 XML 落盘）：

```bash
python -I tools/ci/am_instrument_recorder.py \
  --adb /c/Android/Sdk/platform-tools/adb.exe --serial emulator-5562 \
  --package com.tingyun.smartmistakebook.core.data.test \
  --class com.tingyun.smartmistakebook.core.data.backup.BackupSelectionRestoreInstrumentedTest#subsetThirteenPartialSettingsWriteIsReportedWithoutRollingBack,com.tingyun.smartmistakebook.core.data.backup.BackupSelectionRestoreInstrumentedTest#subsetThirteenTamperedPackageIsRejectedBeforeSwap
# 期望 exit=0；XML tests=2 failures=0 errors=0 skipped=0；device.json serial 与 APK sha 同轮
```

3. 判据（缺一不可）：XML `failures=0 errors=0 skipped=0`；`receipt.selection.mask==13`；
   partial 行 `settings=WRITE_FAILED` 且数据库不回滚；corrupt 行 `validate` 与 `restore`
   均抛拒绝且恢复前后 `rowCount` 不变；证据进入 `docs/research/evidence/` 并在台账行
   `evidence` 中引用；行级复核 JSON 以**本行测试方法名**为 primary 复算。
4. 责任人：**B 域工程（core/data androidTest 夹具作者）**；复核人：独立 B 域复核
   （不得等于作者）。完成前两行保持 `BLOCKED`；完成后只按新证据改判，不作批量外推。

## 9.5 I 域（可用性与无障碍）出口（批次 47 WS3）

I 域当前 **0 FAIL / 37 BLOCKED / 53 PASS**（台账 `19ea6b57…`）。本节按
“缺失条件”把 37 条 BLOCKED 分成四组，逐行给出条件、复跑命令、判据与责任人；
`blocked_runbook_coverage = 37/37`（判据：行 ID 在本节出现，且该行至少有一条可执行
命令或明确的人工判据）。本节**不改变任何 verdict**：条件未取得前一律不得写 `PASS`。

> 分类口径按 §8：`BLOCKED` = 外部条件缺失。I 域有 7 行属于**内部工具/产品决策缺口**
> （§9.5.4），它们保留批次 46 的 `BLOCKED` 判词不改判，但责任人与出口不是“等外部”，
> 必须由主会话/产品裁定后才能动。

### 9.5.1 外部硬件 / 供应商 / 真机（7 行）

| 行 | 缺失条件 | 复跑命令 | 判据 | 责任人 |
|---|---|---|---|---|
| I-007 | TalkBack 朗读文本回传需要外部 TTS/语音回传驱动，仓内无该驱动 | `adb -s <serial> shell dumpsys accessibility`；安装并启用 TalkBack 后按 §6 走查 | 服务已绑定 + 手势触发朗读，且语音回传可被外部驱动捕获；只有日志计数不算 | 用户/他线（提供驱动或真机） |
| I-050 | TalkBack 滑动遍历顺序与键盘 TAB 是两套顺序，需 TalkBack 真机启用 | `python -I tools/ci/a11y_probe.py --serial <serial> focus --label talkback-swipe --nav nav_library --root-tag root_library --presses 8 --out-dir <evidence>` 后与 TalkBack 滑动遍历逐项对照 | 两套顺序均落盘且差异被显式记录；顺序“合理”仍需人工签署 | 用户/他线（TalkBack 真机） |
| I-061 | 启动失败/导出失败需故障注入或真实 Provider，本批无此条件 | 见 §13 FIX-T08；`python -I tools/ci/am_instrument_recorder.py --serial emulator-5562 --package com.tingyun.smartmistakebook.localfirst.test --class com.tingyun.smartmistakebook.Prd06EmptyLibraryCaptureCtaInstrumentedTest` 只覆盖空库 CTA，**不**覆盖启动/导出失败 | 注入失败后错误文案可见、FATAL=0、数据不丢 | 用户/他线（真实 Provider 密钥） |
| I-068 | 输入法候选栏对内容的遮挡需真机 IME | 真机安装输入法后 `adb -s <serial> shell ime list -a`，搜索框聚焦后截图 | 候选栏不遮挡输入框与结果区（截图 + 人工确认） | 用户/他线（真机 + IME） |
| I-070 | 第三方中文输入法交互不在自动化范围 | 同上；自动化只记录 `ime list` 状态 | 中文经第三方 IME 输入后进入语义树；不得用 `adb input text` 冒充 | 用户/他线（第三方 IME） |
| I-075 | 无窗口模拟器不响应传感器旋转，不能用旋转设置冒充横屏 | 真机 `adb -s <serial> shell settings put system accelerometer_rotation 0` + `adb -s <serial> shell settings put system user_rotation 1`，再 `python -I tools/ci/a11y_probe.py --serial <serial> capture --label rotate-review --root-tag root_review --out-dir <evidence>` | `dumpsys input` 的 SurfaceOrientation 与 dump bounds（宽>高）一致 | 用户/他线（真机） |
| I-079 | 真机传感器旋转与折叠屏形态需实机覆盖 | 同上 + 折叠屏展开/折叠两态各采一次 | 两态均可达且结构审计 0 违规；形态差异被记录 | 用户/他线（真机/折叠屏） |

### 9.5.2 第二人 / 第二模型独立复核（9 行）

覆盖 I-009、I-018、I-027、I-036、I-045、I-054、I-063、I-072、I-081（每组的“独立复核人”行）。

| 项 | 内容 |
|---|---|
| 缺失条件 | 本线只有同一会话的机械复算（`independence.independent_agent=false`），没有第二个人/第二模型 |
| 命令/流程 | 由非作者的复核人打开 `docs/acceptance/verdicts/rows/<ID>.json`，按其中 `checks[]` 逐条重跑；另写一份 review JSON（`reviewer ∉ authors`、`run_id` 带 ISO 时间、`ledger_sha256` 为当前台账 sha、`independence.independent_agent=true`） |
| 判据 | 新 review 逐项复算 0 错误；复核人签署对**语义充分性**的意见（机械复算通过不等于语义充分）；缺新 review 即维持 `BLOCKED` |
| 责任人 | 主会话授权的第二人 / 第二模型；不得由 I 域作者自签 |

### 9.5.3 人工语感 / 视觉评审（14 行）

覆盖 I-005、I-032、I-041、I-059、I-077、I-082、I-083、I-084、I-085、I-086、I-087、I-088、I-089、I-090。

| 项 | 内容 |
|---|---|
| 缺失条件 | 朗读语速/断句、文案语气、颜色语义、字号行距、横屏信息密度、整体体验属人类语感，自动化不可替代 |
| 对照材料命令 | `python -I tools/ci/a11y_probe.py --serial emulator-5584 capture --label <label> --nav <nav> --root-tag <root> --out-dir docs/research/evidence/<批次>/i-domain`（暗色/大字号/横屏再叠 `--night yes`、`--font-scale 2.0`、`--wm-size 2400x1080`） |
| 判据 | 评审人按行逐项签署（同意 / 不同意 + 理由）；未签署即维持 `BLOCKED`；机器审计结果只能作对照，不能代替签字 |
| 责任人 | 用户 / 产品 / 他线评审人（非本线） |

### 9.5.4 内部工具 / 产品决策缺口（7 行）

| 行 | 缺口（本轮读源码/实跑确认） | 出口 | 判据 | 责任人 |
|---|---|---|---|---|
| I-014 | 48dp 之外的**间距、重叠、手势冲突**不在 `a11y_audit` 可判定范围 | 在 `tools/ci/a11y_audit.py` 增审计项：同屏可点击节点间距 < 8dp、bounds 相交、同区域手势冲突；只加检查不改被测实现 | 新审计项对 4 屏 0 违规且有负向用例（构造重叠/贴边节点必红） | I 域工程 + 主会话授权（新增工具） |
| I-023 | 图片/公式/渲染图**内部文本**的对比度 `a11y_audit theme` 不覆盖（只算主题色对） | 截图 + 像素采样（PNG 亮度/前景背景聚类）落到 `tools/ci/` | 采样结果与人工抽查一致；阈值须显式给出 | I 域工程（新增像素采样） |
| I-025 | 截图逐像素对比工具已在位但**本批未跑**、且无产品阈值 | `python -I tools/ci/screenshot_baseline.py capture --serial emulator-5562 --out <png> --apk-sha <sha>`；再 `compare --baseline <b> --current <c> --max-mae <x> --max-changed-ratio <y>` | 阈值由产品给定后才可判；无阈值只记录 MAE/变化像素比 | I 域工程 + 产品（阈值） |
| I-030 | 文本**裁剪/溢出**无自动判定手段（语义树不含 clip 信息） | 用 layout inspector/截图 OCR 检测截断，或在 instrumented 用例里断言行高与可见行数 | 200% 字号下 4 屏无截断；负向用例（超长文案）必红 | I 域工程（新增用例） |
| I-039 | 源码无 `darkColorScheme`/`isSystemInDarkTheme`（本轮实跑 `grep -c` = **0**），不能声明深色主题适配 | 产品裁定：实现深色主题 → 加 `darkColorScheme` 后 `python -I tools/ci/a11y_audit.py theme --source core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/Theme.kt` + 夜间设备采集；若决定不做，本行应改判 `UNVERIFIED`（**不是** PASS） | 有 `darkColorScheme` 且夜间 4 屏审计 0 违规、对比度 PASS | 产品 / 主会话 |
| I-043 | 同 I-039；该行自身检查文本写“属产品决策缺口，**非外部阻塞**，登记未验证”，与台账 `BLOCKED` 判词不一致 | 同 I-039；台账判词本轮不改，冲突留给主会话终裁 | 同 I-039 | 产品 / 主会话 |
| I-066 | 中文无法经 `adb input text` 注入，探针以 exit 2 拒绝（不是通过） | 新增中文输入 instrumented 用例：`python -I tools/ci/am_instrument_recorder.py --serial emulator-5562 --package com.tingyun.smartmistakebook.localfirst.test --class <中文输入用例>` | XML `failures=0 errors=0` 且搜索框文本为中文、语义树含该中文 | I 域工程（新增用例） |

### 9.5.5 本节边界

- 以上 37 行**全部仍是 BLOCKED**；本节只补出口，不产生任何 PASS。
- I-039/I-043 的 `BLOCKED` 与行内文案（“非外部阻塞”）冲突，本轮**保留判词、只记录冲突**；
  是否改判 `UNVERIFIED` 由主会话终裁，I 域不得自改。
- 人工语感与第二人复核的签署结果须以新证据文件挂回台账行，才允许按新证据改判。

