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
| FIX-T04 | 中 | 部分页打印丢文档级 `/AIGC`（KD-84；H-018/H-061） | BLOCKED：需改 `core/export` 已批准 hunk 并做独立读者读回 | 复跑条件=按 §9.2 出设备产物后，宿主 pypdf/PyMuPDF 读回子集 PDF 的 `/AIGC` 且 `ProduceID` 与输入一致、页数等于所选页数；同时更新 `known-defects.md` 与本门 |
| FIX-T05 | 高 | 模型改卷文本未纳入 `/AIGC` 判据（KD-85；H-018/H-062） | BLOCKED：落点跨 `feature/tutor`（热区）需主会话授权 | 复跑条件=纯用户来源经模型改卷后导出 PDF 被独立读者读回 `Label=1`、`ProduceID=输入 sha`；仅手工编辑仍无 `/AIGC`；`MistakePdfPaperEdit` 单测覆盖两向 |
| FIX-T06 | 中 | 恢复回放零面积夹具（C-065） | 已修夹具（闭合三角形 `M/L/L/Z`，工作树未提交），设备绿轮未跑 | 复跑条件=`am instrument ... TutorVisualRebuildRestoreInstrumentedTest#theRestoreLegAloneReplaysOfflineWithZeroDispatchBookings` 在修复后 APK 上绿；夹具退回开口线段时本门转红 |
| FIX-T07 | 中 | DOCX 无文档级 `/AIGC`（H-018） | BLOCKED：先在 `core/export` 加 docProps 标识，再做独立 DOCX 读回 | 复跑条件=DOCX 产物被独立读者读回文档级 `/AIGC`；仅在代码里出现字面量不算关闭（本门会转红） |
| FIX-T08 | 高 | 真实 Provider 端到端（A-010/D-082..D-090，9 项） | BLOCKED：无真实密钥与上游 | 复跑条件=以环境变量注入 `*_API_KEY` 后按本文 D 域步骤执行，证据绑定 run-id 与 APK sha，且不回显密钥 |
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
