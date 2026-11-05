# BLOCKED 出口 runbook（批次 47 起）

> 本文件把台账里每一条 `BLOCKED` 行落到**可执行的复跑条件、命令、判据、责任人**。
> 它是 `docs/acceptance/runbook.md` §9「BLOCKED 清单与出口」的按行展开，不替代 §9。
>
> **红线（与 `runbook.md:155-157` 一致）**
> 1. `BLOCKED` 不得写成 `PASS`；本文件只定义“条件补齐后如何改判”，本身不产生任何 PASS。
> 2. 改判必须同时满足：新证据入盘 + 新行级复核 JSON（`docs/acceptance/verdicts/rows/<ID>.json`）
>    + 台账该行 verdict 变更三件事同批提交；缺一不可。
> 3. 每次复跑的证据必须绑定 run-id、APK sha、设备 serial（设备项）或构建退出码（JVM 项）。
> 4. 6C-2 三项不可变（mask=8 不提供 / mask=0 禁止 / 派生索引恒裁）、热区禁改、
>    不引新依赖——本文件所有步骤都不构成对这些约束的豁免。
>
> **分域追加约定**：每个域只追加自己的 `## <域> 域` 小节与行表，**不整文件覆盖**；
> 已有小节只做等值更正并在行尾标注批次。

## blocked_by 七类总索引（批次 47；280 行全覆盖）

> 基线：`docs/acceptance/ledger.md`。本轮读取时工作树台账 sha256 `2944eb50…`，
> 且经 `git show HEAD:docs/acceptance/ledger.md` 对比核实：**BLOCKED 集合与批次 46 终态
> （sha `19ea6b57…`）逐行相同**——两边都是 280 行、成员完全一致（0 增 0 减）。
> WS1 正在并行修 FAIL（PASS/FAIL 计数会漂移），不影响本文件；若 BLOCKED 集合变化，
> 本节与域小节必须同批更新。分类由脚本从台账 verdict 逐行派生并复核（无遗漏、无重复）。
>
> **归类规则**：按该行缺失条件的**主阻塞**归入七类之一；行内含次要条件时在域小节注明。
> 七类之下再按**内部/外部**单列（本批热区禁改、共享树不提交等纪律不因本文件豁免）。
> 本文件只登记出口，不产生任何通过；`BLOCKED` 不得写成 `PASS`，改判必须
> 新证据 + 新行级复核 JSON + 台账变更三件事同批。

### 内部 / 外部单列（总口径）

| 侧 | 类 | 行数 | 谁能解锁（责任人） | 行清单 |
|---|---|---:|---|---|
| 外部 | Provider | 17 | Provider 账号持有人（密钥只经设备输入）；内部发布负责人执行取证 | 见 §1 |
| 外部 | 签名 | 20 | 发布密钥持有人 + 主会话（配 `RELEASE_*`，门禁阶段复跑） | 见 §2 |
| 外部 | arm64 | 21 | 设备持有人（arm64 真机 / 16KB 页设备；x86_64 宿主不能起 arm64 AVD） | 见 §3 |
| 外部 | 金标 | 37 | 用户/他线（标注员、第二人/第二模型复核、人工语感/视觉评审） | 见 §4 |
| 外部 | 环境 | 51 | 用户/他线（Play 账号、域名/托管、CAC 备案、advisory 资料库、真机/驱动/IME/折叠屏） | 见 §5 |
| 内部 | 热区 | 18 | 主会话书面授权改码后由工程执行（热区清单见 runbook §11 边界与纪律） | 见 §6 |
| 内部 | 授权 | 116 | 主会话/产品裁定（阈值、口径、排期、提交共享树、门禁窗口）+ 工程执行 | 见 §7 |

外部合计 **146**，内部合计 **134**，合计 **280**；逐域核对：A25/B2/C2/D9/E9/F33/FIX85/G7/GATE4/H19/I37/J48
（与台账逐域 `BLOCKED` 计数相等，见文末核对命令）。

### §1 Provider（外部，17 行）

- **缺失条件**：真实 Provider 账号 + Base URL + 模型 ID + API Key。runbook §9.4 已核实：代码无读取
  `*_API_KEY` 环境变量的入口，真实凭据只能经设备 `模型设置` 输入；`strictOffline` 变体未启用模型服务，
  须用 `localFirst`。
- **命令要点**：门禁阶段 `./gradlew :app:assembleLocalFirstDebug` → `adb -s <serial> install -r` →
  设备内配置并逐行取证到 `docs/research/evidence/<批次>/d-domain/`；逐行步骤与判据=runbook §9.4。
- **判据**：runbook §9.4 逐行判据表（主路径 200、三失败码映射、边界 fail-closed、取消无半条、
  重试对账、派遣 ≤6、原文只进受控缝隙、证据头四项、D-090 口径行）；证据含 run-id/APK sha/serial，
  全文无密钥片段。
- **责任人**：外部=Provider 账号持有人；内部=发布负责人（执行与取证）。
- **行**：A-010、D-082、D-083、D-084、D-085、D-086、D-087、D-088、D-089、D-090、G-036、I-061、
  FIX-036、FIX-037、FIX-038、FIX-039、FIX-040（FIX 五行=FIX-T08）。

### §2 签名（外部，20 行）

- **缺失条件**：生产签名材料（`RELEASE_*` 四变量）、同钥匙成对 vc 产物、Play App Signing 指纹登记。
- **命令要点**：配齐 `RELEASE_*` 后 `python -I tools/ci/j_release_verify.py signing`、
  `apksigner verify --print-certs`、`./gradlew validateReleaseSigning`（负向必须失败）；
  成对产物按 runbook §9.2 的成对产物组路径跑升级/降级/回滚（`dumpsys` 事后 vc 等于目标）。
- **判据**：指纹等于已登记发布指纹；AAB/APK 同身份、证书覆盖发布窗口；升级/降级/回滚三向
  Success 且数据保留（详见 runbook §9.2）。
- **责任人**：外部=发布密钥持有人；内部=主会话（注入环境变量）+ J 域工程（复跑取证）。
- **行**：J-019、J-055、J-056、J-057、J-061、J-065、J-068、J-069、J-070、J-087、
  FIX-046、FIX-047、FIX-048、FIX-049、FIX-050（FIX-T10）、
  FIX-071、FIX-072、FIX-073、FIX-074、FIX-075（FIX-T15）。

### §3 arm64（外部，21 行）

- **缺失条件**：arm64 真机或镜像（两台在线设备均 x86_64；x86_64 宿主不能起 arm64 AVD）、
  16384 页设备。
- **命令要点**：换 arm64 真机后按 runbook §9.2 的 arm64/16KB 组重跑安装/冷启/16KB 运行时/native 装载；
  F 域按 `docs/research/evidence/2026-11-05-batch-44/f-domain/00-index.md` §7 重跑冷启/首帧/导出/
  预览/内存/2h/进程死亡/后台限制；`adb -s <serial> shell getconf PAGE_SIZE` 须=16384
  （批次 47 修复环补 `-s <serial>` 精度：原 `adb shell` 不含目标设备参数，覆盖门
  ACTION 正则 `python -I|adb -s|人工|签署|评审人` 不命中，见终裁 §6）。
- **判据**：设备证据绑定 serial/boot/APK sha；`logcat` FATAL=0；F 域各阈值判定仍以
  §7 组的产品裁定为前提（arm64 只解决"在哪台机跑"）。
- **责任人**：外部=设备持有人；内部=F/J 域工程（复跑取证）。
- **行**：F-006、F-015、F-024、F-033、F-042、F-051、F-060、F-069、F-078、F-087、
  J-035、J-037、J-038、J-039、J-042、J-043、
  FIX-041、FIX-042、FIX-043、FIX-044、FIX-045（FIX-T09）。

### §4 金标（外部·人工判定/独立复核，37 行）

- **缺失条件**：人工标注员（24 页冻结金标未标注）、第二人/第二模型独立复核
  （现状 `independence.independent_agent=false`，复核只是机械复算）、人工语感/视觉评审签署。
- **命令要点**：独立复核=非作者打开 `docs/acceptance/verdicts/rows/<ID>.json` 按 `checks[]`
  逐条重跑并另写 review JSON（`reviewer ∉ authors`、`independent_agent=true`）；
  人工评审=按 runbook §9.5.2/§9.5.3 流程签署；金标=交付标注包后标注。
- **判据**：新 review 逐项复算 0 错误且签署对语义充分性的意见；评审逐项"同意/不同意+理由"；
  标注产物在盘且正确率可复算。未签署/未标注一律维持 `BLOCKED`。
- **责任人**：外部=用户/他线（第二人、评审人、标注员）；不得由实现线自签。
- **行**：GATE-096、GATE-097、GATE-098、GATE-100、
  H-063、H-089、H-090、
  I-005、I-009、I-018、I-027、I-032、I-036、I-041、I-045、I-054、I-059、I-063、I-072、I-077、I-081、
  I-082、I-083、I-084、I-085、I-086、I-087、I-088、I-089、I-090、
  J-008、J-018、J-026、J-036、J-053、J-063、J-071。

### §5 环境（外部，51 行）

- **缺失条件**：外部账号/资产/资料/实体——Play Console 账号与商店素材、开发者域名与隐私政策托管、
  CAC 备案名单、离线 CVE advisory 资料库、TalkBack 语音回传驱动、第三方中文 IME、
  传感器旋转真机与折叠屏形态。
- **命令要点**：Play 面=按 `docs/research/2026-10-25-store-launch-materials-checklist.md` 备料上传、
  建内部/封闭轨道；隐私政策=公开 URL 可访问后重跑 `python -I tools/ci/h_compliance_gate.py --only H-024`
  等；无障碍真机面=runbook §9.5.1 各命令；供应链=接入离线 advisory 库后联网取证
  （资料库不得构成构建依赖，来源须主会话批准）。
- **判据**：各域小节逐组判据（J/H/I/G/FIX）；表单提交回执、备案号回填、轨道建立、
  真机采集证据绑定 serial/APK sha。
- **责任人**：外部=用户/他线（账号持有人、域名/备案提供方、真机与驱动提供方）；
  内部=对应域工程（复跑取证）。
- **行**：H-024、H-025、H-036、H-043、H-053、H-081、G-087、
  I-007、I-050、I-068、I-070、I-075、I-079、
  J-017、J-073、J-074、J-075、J-076、J-077、J-078、J-079、J-080、J-081、J-082、J-083、J-084、
  J-085、J-086、J-088、J-089、J-090、
  FIX-066、FIX-067、FIX-068、FIX-069、FIX-070（FIX-T14）、
  FIX-086、FIX-087、FIX-088、FIX-089、FIX-090（FIX-T18）、
  FIX-091、FIX-092、FIX-093、FIX-094、FIX-095（FIX-T19）、
  FIX-096、FIX-097、FIX-098、FIX-099、FIX-100（FIX-T20）。

### §6 热区（内部·需主会话授权改码，18 行）

- **缺失条件**：修复落点在热区（`core/domain`、`core/ui`、`feature/*`、`docs/current`、
  `settings.gradle.kts`、`Application/Root`；`core/export` 只做已批准 hunk，见 runbook §11）。
  本批热区禁改，故这 18 行在授权前**无合法执行路径**。
- **命令要点**：授权后先红后绿（对应单测/门），门禁阶段串行复跑
  `python -I tools/ci/h_compliance_gate.py --only <ID>`（H 域）、
  `python -I tools/ci/fix_relapse_gate.py --gate FIX-Tnn --prove-red`（FIX 域）。
- **判据**：对应门 exit 0 + 设备截图/读回证据（T17 须"AI 生成"角标上设备截图；
  T16 须 `--topic minor` exit 0；T05 须独立读回 `Label=1`、`ProduceID=输入 sha`）。
- **责任人**：主会话（书面授权 + 落点批准）；热区所在域工程（执行）；复核独立于作者。
- **行**：H-008（FIX-T17）、H-041、H-051、
  FIX-021、FIX-022、FIX-023、FIX-024、FIX-025（FIX-T05，feature/tutor）、
  FIX-076、FIX-077、FIX-078、FIX-079、FIX-080（FIX-T16，feature/profile）、
  FIX-081、FIX-082、FIX-083、FIX-084、FIX-085（FIX-T17，feature/tutor + core/ui）。

### §7 授权（内部·裁定或工程执行，116 行）

按子组给条件/命令/判据/责任人（逐行详表在各域小节）：

| 子组 | 行数 | 缺失条件 | 命令要点与判据 | 责任人 |
|---|---:|---|---|---|
| 7a 缺用例/夹具/大样本（工程可写） | 27 | A-1 14 行、B-077/B-078、E 域 9 行、C-008/C-088（模块均非热区，只加测试） | 各域小节逐行命令；判据=新增用例跑绿 + 行级 JSON 以本行用例名为行特有 check | A/B/C/E 域工程；复核独立于作者 |
| 7b 阈值/口径裁定（产品/性能） | 36 | F 域 23 行、A-2 权限口径 8 行、A-031、A-050、G-081、I-039/I-043 | 先由产品/性能负责人书面定阈值或口径，再按 runbook §9.1/域小节复跑；I-039/I-043 若裁定不做深色主题，应改 `UNVERIFIED`（不是 PASS），由主会话终裁 | 产品/性能负责人 + 主会话；域工程复跑 |
| 7c 内部工具/用例/排期 | 12 | G-016（崩溃采集未触发）、G-072（构造穿越 URI）、G-080（配置页截图）、I-014（间距/重叠审计项）、I-023（像素采样）、I-025（逐像素对比+阈值）、I-030（裁剪检测）、I-066（中文输入用例）、J-049（vc 碰撞检测器）、H-039/H-040/H-042（G7 落地排期） | 新增工具/用例只加不改被测实现（tools/ci 非热区）；负向用例必须先红；阈值显式；H 三行=产品排期后按 `--topic minor` 复跑 | G/I/J 域工程 + 主会话（排期） |
| 7d i18n 口径 | 4 | H-075/H-076/H-077/H-080（产品未定义本地化要求） | 产品定 i18n 范围后 `python -I tools/ci/h_compliance_gate.py --topic i18n`；裁定"仅中文"则按不适用登记（主会话终裁） | 产品 + 主会话 |
| 7e 流程窗口/提交收尾/发布裁定 | 37 | G-088（锁文件提交授权）、J-003/J-006/J-007/J-022（门禁阶段 Gradle + 干净树）、J-049（vc 碰撞检测器）、J-066（依赖 B 域矩阵）、J-067（判据待主会话定）、FIX-T04 五行（改判收尾）、FIX-T06 五行（夹具提交+设备轮）、FIX-T07 五行（core/export 已批准 hunk + DOCX 读回）、FIX-T11 五行（版本源发布裁定）、FIX-T12 五行（门禁重出 AAB）、FIX-T13 五行（提交+identity） | 提交共享树后门禁阶段跑 `./gradlew`（J-003/J-007/J-022）、`python -I tools/ci/j_release_verify.py identity --require-clean --require-head`、`j_release_verify.py aab16k`；T04 证据已在盘（KD-84 闭环），出口=复发门 exit 0 + 三件事同批改判 | 主会话（提交/裁定）+ J/FIX/G 域工程 |

7a–7e 行数：27 + 36 + 12 + 4 + 37 = **116**。
行清单（域序；FIX 部分逐行展开以便机器核对）：
A 域 A-003、A-005、A-014、A-023、A-032、A-041、A-049、A-058、A-059、A-065、A-066、A-068、A-077、
A-086（7a 14）；A-006、A-015、A-024、A-033、A-051、A-060、A-069、A-087、A-031、A-050（7b 10）；
B 域 B-077、B-078（7a 2）；C 域 C-008、C-088（7a 2）；E 域 E-007、E-016、E-025、E-034、E-043、
E-052、E-070、E-079、E-086（7a 9）；F 域（7b 23）：F-002、F-009、F-011、F-012、F-018、F-020、
F-027、F-038、F-039、F-047、F-048、F-050、F-054、F-056、F-058、F-063、F-072、F-074、F-081、
F-083、F-084、F-085、F-090；G 域 G-016、G-072、G-080（7c 3）、G-081（7b）、G-088（7e）；
H 域 H-039、H-040、H-042（7c 3）、H-075、H-076、H-077、H-080（7d 4）；
I 域 I-014、I-023、I-025、I-030、I-066（7c 5）、I-039、I-043（7b 2）；
J 域 J-003、J-006、J-007、J-022、J-049、J-066、J-067（7e 7）；
FIX 域（7e 30）：FIX-016、FIX-017、FIX-018、FIX-019、FIX-020、FIX-026、FIX-027、FIX-028、FIX-029、
FIX-030、FIX-031、FIX-032、FIX-033、FIX-034、FIX-035、FIX-051、FIX-052、FIX-053、FIX-054、FIX-055、
FIX-056、FIX-057、FIX-058、FIX-059、FIX-060、FIX-061、FIX-062、FIX-063、FIX-064、FIX-065。

### 核对命令（本节与域小节的覆盖自检）

```bash
python -I - <<'PY'
import re
txt=open('docs/acceptance/ledger.md',encoding='utf-8').read()
led=set()
for line in txt.splitlines():
    c=[x.strip() for x in line.strip().strip('|').split('|')]
    if len(c)==6 and re.match(r'^(D0-|[A-J]-|FIX-|GATE-)',c[0]) and c[5]=='BLOCKED': led.add(c[0])
rb=open('docs/acceptance/blocked-runbook.md',encoding='utf-8').read()
ids=set(re.findall(r'\b(?:D0-[0-9]{3}|[A-J]-[0-9]{3}|FIX-[0-9]{3}|GATE-[0-9]{3})\b',rb))
print('ledger BLOCKED:',len(led),'runbook mentions:',len(ids))
print('missing in runbook:',sorted(led-ids))
print('not BLOCKED but named:',sorted(ids-led))
PY
```

（`not BLOCKED but named` 允许非空：正文会引用已改判的对照行（如 H-061）与 D0 行；`missing` 必须为空。）

## 覆盖索引（A 域，25 行）

| 组 | 缺失条件 | 行 |
|---|---|---|
| A-1 | 缺用例/夹具/大样本（工程可写，内部可执行） | A-003, A-005, A-014, A-023, A-032, A-041, A-049, A-058, A-059, A-065, A-066, A-068, A-077, A-086 |
| A-2 | 产品不存在“运行时权限”场景，缺口径裁定 | A-006, A-015, A-024, A-033, A-051, A-060, A-069, A-087 |
| A-3 | 真实 Provider 凭据（外部） | A-010 |
| A-4 | 详情页无独立空态，缺产品裁定 | A-031 |
| A-5 | 大数据备份/恢复 1800 s 超时，阈值未定 | A-050 |

> 统计口径：25 = 14 + 8 + 1 + 1 + 1，与台账 A 域 `BLOCKED` 计数逐行相等（无遗漏、无重复）。

## A 域

设备命令统一形如（把 `<OUT>` 换成 `docs/research/evidence/<批次>/a-domain`）：

```bash
C:/Android/Sdk/platform-tools/adb.exe devices
python -I tools/ci/am_instrument_recorder.py \
  --serial emulator-5584 \
  --package com.tingyun.smartmistakebook.localfirst.test \
  --class <类名或 类#方法> \
  --arg smb_allow_data_reset=true \
  --label <label> --out-dir <OUT> \
  --apk-sha <app APK sha> --test-apk-sha <test APK sha>
```

`--out-dir` 必填（`am_instrument_recorder.py --help`）；`--apk-sha/--test-apk-sha` 不传则证据缺设备身份，
按 `runbook.md:204` 不得作为改判依据。

### A-1 缺用例 / 夹具 / 大样本（14 行，内部可执行）

**改判判据（统一）**：新增用例在该行 `method` 指定的包/类上跑出 `tests≥1, failures=0, errors=0`，
且 recorder 的 `device.json` 中 `verdict=PASS`、`counts.tests` 与 XML 一致；随后重写该行复核 JSON。
**责任人**：A 域工程师（写用例 + 跑设备）；reviewer 独立于作者。
**当前状态**：以下用例**尚不存在**，直接跑现有类只会得到“无此用例/无此断言”，因此现在不能改判。

| 行 | 缺什么（源自台账 method） | 复跑命令（补用例后） | 判据 |
|---|---|---|---|
| A-003 | `r1-shared.xml` 无进程中断/Activity 重建恢复用例 | `--class com.tingyun.smartmistakebook.SharedCaptureEntryInstrumentedTest`，新增“拍照→分题→入库”Activity 重建恢复用例后重录 `--label r1-shared-recreate` | 重建后草稿/已入库条目可继续，且不产生重复条目；XML `failures=0 errors=0` |
| A-005 | 无大体积成功入库用例（现有 6 文件批量、31 文件拒绝边界不算） | `--class com.tingyun.smartmistakebook.SharedCaptureEntryInstrumentedTest`，新增“通过上限的大批量成功入库”用例 | 入库条数、耗时、峰值内存写入 evidence；拒绝阈值与成功阈值分开断言 |
| A-014 | 无长对话/大上下文讲题用例 | `--class com.tingyun.smartmistakebook.RootTutorFailClosedInstrumentedTest`，新增长上下文用例 | 上下文截断/失败按 fail-closed；无半条任务 |
| A-023 | 大复习队列容量只在 E 域规划器覆盖，A 域无设备大样本 | `--class com.tingyun.smartmistakebook.core.data.study.StudyReviewPlanner3EScanInstrumentedTest`（`core:data` 包 `…core.data.test`） | 大队列扫描的设备端耗时/内存与 E 域口径一致；无 OOM/ANR |
| A-032 | 无大题量详情/修订历史设备用例 | 在 `MistakeRevisionEditInstrumentedTest` 增加大修订历史用例 | 打开/滚动/回滚在阈值内；历史条数与仓储计数一致 |
| A-041 | 100 项只验 WorkManager 输入编码上限，未做端到端大页数渲染 | `./gradlew :core:export:testDebugUnitTest --offline --tests "…MistakePdfBatchEligibilityTest"` 之外，新增端到端大页数渲染用例 | 大页数产物可打开、页数与输入一致；oversized 仍 fail-closed |
| A-049 | 无空库备份/空选择组合的设备用例 | `--class com.tingyun.smartmistakebook.BackupSelectionUiInstrumentedTest` 增加空库/空选择组合用例 | 空选择不可创建；空库备份给出明确文案；mask=0 禁止 |
| A-058 | 知识点空态文案未断言 | `--class com.tingyun.smartmistakebook.KnowledgeReviewSessionInstrumentedTest` 增加空态断言 | 空态文案存在且与产品文案一致；不得只断言“有节点” |
| A-059 | 无大知识库浏览设备样本 | `--class com.tingyun.smartmistakebook.core.data.knowledge.GoldenRetrievalInstrumentedTest`（`core:data` 包）加设备大样本 | 大知识库检索/浏览在阈值内；命中率与金标一致 |
| A-065 | 掌握度无失败路径设备用例 | `--class com.tingyun.smartmistakebook.LearningMasteryScreenInstrumentedTest` 增加失败路径 | 失败时显示可恢复文案，不显示错误掌握度 |
| A-066 | 掌握度无中断恢复设备用例 | 同上，增加旋转/后台/杀进程恢复用例 | 恢复后掌握度与恢复前一致，无重复写入 |
| A-068 | 无大数据量掌握度用例 | `./gradlew :core:data:testDebugUnitTest --offline --tests "…MasteryIntervalMappingTest"` 增加大数据量映射用例 | 大样本映射稳定、无溢出；与产品口径一致 |
| A-077 | 无大 pendingCount 通知文案/裁剪用例 | `./gradlew :app:testLocalFirstDebugUnitTest --offline --tests "…ReviewReminderCoordinatorTest"` 增加大计数用例 | 文案裁剪规则与产品定义一致；通知只发一次 |
| A-086 | 无大考试日历/大设置项设备用例 | `--class com.tingyun.smartmistakebook.SchedulingSettingsInstrumentedTest` 增加大日历用例 | 大日历下滚动/保存可用；保存结果与仓储计数一致 |

### A-2 无运行时权限场景（8 行，缺产品口径）

这 8 行的台账 `method` 都写明：该链路**没有运行时权限**（录入链 manifest 未声明 `CAMERA`；
备份走 SAF；其余链路不申请权限）。因此“权限拒绝”这一场景在当前产品形态下**不可构造**，
既不能跑出 PASS，也不能直接删除该行。

**改判判据（二选一，需产品书面裁定）**
1. 产品确认“该链路不存在运行时权限”为**设计事实**：把该行口径改为“不适用”，由产品/验收
   负责人签字并在台账注明裁定来源（此时该行不应再计为 `BLOCKED` 未覆盖项）；
2. 产品要求该链路必须具备可撤销权限：先改产品/Manifest，再补用例，按 A-1 的设备命令取证。

**责任人**：产品负责人（口径裁定）；A 域工程师（若走第 2 条）。
**在此之前**：8 行保持 `BLOCKED`，不得以“没有权限就没有失败”为由判 `PASS`。

| 行 | 链路 | 现状证据 |
|---|---|---|
| A-006 | 拍照→分题→入库 | `app/src/androidTest/.../CaptureShareIntakeInstrumentedTest.kt` |
| A-015 | 讲题 | `app/src/androidTest/.../RootTutorFailClosedInstrumentedTest.kt` |
| A-024 | 复习三键 | `app/src/androidTest/.../ReviewSelfReportDeviceUiInstrumentedTest.kt` |
| A-033 | 详情编辑 | `core/data/src/androidTest/.../MistakeRevisionEditInstrumentedTest.kt` |
| A-051 | 备份/恢复（SAF） | `app/src/androidTest/.../BackupSelectionUiInstrumentedTest.kt` |
| A-060 | 知识库浏览 | `app/src/androidTest/.../KnowledgeReviewSessionInstrumentedTest.kt` |
| A-069 | 掌握度 | `app/src/androidTest/.../LearningMasteryScreenInstrumentedTest.kt` |
| A-087 | 设置（BYOK 密钥不是 Android 权限） | `app/src/androidTest/.../CapabilityScreenInstrumentedTest.kt` |

### A-3 真实 Provider（A-010，外部条件）

**缺失条件**：无真实 Provider 账号/Base URL/模型 ID/API Key（与 `runbook.md` §9.3 D 域同源）。
**步骤**：按 `runbook.md` §9.3 配好 `localFirst` 构建与设备后，复跑
`--class com.tingyun.smartmistakebook.RootTutorFailClosedInstrumentedTest --label r2-tutor`。
**判据**：真实上游返回成功且讲题结果可读回；证据含 run-id/APK sha/serial，全文无密钥片段。
**责任人**：Provider 账号持有人（提供凭据与用量只读）；A 域工程师（执行与取证）。
**边界**：注入模型 UI 用例（现有 `r2-tutor.xml`）只证明 fail-closed 与界面，不证明真实上游。

### A-4 详情页空态（A-031，产品口径）

**缺失条件**：详情页必须有一条已保存题才能进入，**不存在独立空态**。
**改判判据（二选一，需产品裁定）**
1. 产品确认“详情页无空态”为设计事实：登记为不适用并注明来源；
2. 产品要求有空态：先在 `feature/library/.../MistakeDetailRoute.kt` 加空态，再补设备用例。
**责任人**：产品负责人；A 域工程师（第 2 条）。
**注意**：`feature/*` 属热区（`runbook.md:200`），第 2 条动代码前须主会话授权。

### A-5 大数据备份/恢复超时（A-050，阈值未定）

**既有事实**：`r6-backup-scale` 在 365 MiB 全量/子集备份恢复上 1800 s 未完成，
recorder 返回超时（EXIT=1），因此**不按“大库通过”记**（同 `00-index.md` §2）。
**改判步骤（需先定阈值）**
1. 产品/性能负责人书面给出“大数据备份/恢复可接受时长”与数据集上限；
2. 用更长 `--timeout` 重录：
   `--package com.tingyun.smartmistakebook.core.data.test --class com.tingyun.smartmistakebook.core.data.backup.BackupSelectionScaleInstrumentedTest,com.tingyun.smartmistakebook.core.data.backup.BackupLargeDatabaseScaleInstrumentedTest --label r6-backup-scale --timeout <≥3600>`；
3. 判据：`verdict=PASS` 且实测时长 < 产品阈值；否则保持 `BLOCKED`。
**责任人**：产品/性能负责人（阈值）；A 域工程师（复跑）。
**红线**：不得把超时改记为“慢但可用”，也不得删除该行。

## E 域

> 覆盖 E 域 **9 行** `BLOCKED`（E-007/016/025/034/043/052/070/079/086），
> 全部属 **impl-gap（缺用例/夹具，内部工程可执行）**，无外部供应商依赖。
> 台账这 9 行的 `方法` 列指针写的是 `runbook.md` §9.1；对应的 E 域段落已在
> `runbook.md` §9.1.1 补齐（台账不改字，避免重签 920 份 `ledger_sha256`）。

| 组 | 缺失条件 | 行 |
|---|---|---|
| E-1 | 缺并发用例（纯函数/幂等投影，无共享可变状态但并发关系未测） | E-007, E-016, E-025, E-034, E-043, E-052, E-079 |
| E-2 | 缺并发用例 + 真 SQLite 事务证据（JVM 假端口不模拟事务） | E-070 |
| E-3 | 缺 DST 切换探针（只有冬季 EST，无春季跳变/秋季回拨） | E-086 |

> 统计口径：9 = 7 + 1 + 1，与台账 E 域 `BLOCKED` 计数逐行相等（无遗漏、无重复）。

### E-1 并发（7 行）

| 行 | 缺什么 | 复跑命令（门禁阶段串行） | 判据 | 责任人 |
|---|---|---|---|---|
| E-007 | `FsrsScheduleMath` 多线程同输入调用一致性 | `./gradlew --offline -q :core:domain:test --tests com.tingyun.smartmistakebook.core.domain.FsrsScheduleMathTest`（补 `<新增并发用例>` 后） | XML `tests>0 failures=0 errors=0 skipped=0`；并发调用的 stability/difficulty/interval 与串行逐位一致 | E 域工程（core/domain 测试作者） |
| E-016 | `ReviewCapacityPolicy` 并发 `dailyQuota`/`capacityPerDay` | `./gradlew --offline -q :core:data:testDebugUnitTest --tests com.tingyun.smartmistakebook.core.data.study.ReviewCapacityPolicyTest`（补 `<新增并发用例>` 后） | 同输入并发结果集与串行一致；非正输入仍 fail closed | E 域工程（core/data 测试作者） |
| E-025 | `ExamQuotaPlanner.plan` 并发确定性 | `./gradlew --offline -q :core:data:testDebugUnitTest --tests com.tingyun.smartmistakebook.core.data.study.ExamQuotaPlannerTest`（补 `<新增并发用例>` 后） | 配额、deficit、排序（deficit→exam risk→due→id）逐位一致 | E 域工程（core/data 测试作者） |
| E-034 | `MissedDayRebalancePolicy.plan` 并发确定性 | `./gradlew --offline -q :core:data:testDebugUnitTest --tests com.tingyun.smartmistakebook.core.data.study.MissedDayRebalancePolicyTest`（补 `<新增并发用例>` 后） | 预算、horizon、daysNeeded 与串行一致；非法 growthCap 仍 fail closed | E 域工程（core/data 测试作者） |
| E-043 | 同 learner 并发投影的幂等与冲突 | `./gradlew --offline -q :core:domain:test --tests com.tingyun.smartmistakebook.core.domain.LearningProjectorTest`（补 `<新增并发用例>` 后） | attempt id 幂等；同批不重复应用；冲突 payload 确定性拒绝 | E 域工程（core/domain 测试作者） |
| E-052 | 并发提取同 scope 候选的唯一性 | `./gradlew --offline -q :core:data:testDebugUnitTest --tests com.tingyun.smartmistakebook.core.data.study.ErrorCauseExtractorTest`（补 `<新增并发用例>` 后） | 同 scope 至多 1 个候选、无重复 observation；abstain 路径仍不猜错因 | E 域工程（core/data 测试作者） |
| E-079 | 同快照并发 `ReviewPlannerV2.plan` | `./gradlew --offline -q :core:domain:test --tests com.tingyun.smartmistakebook.core.domain.ReviewPlannerV2Test`（补 `<新增并发用例>` 后） | planId/fingerprint 与串行一致；预算与去重不翻倍 | E 域工程（core/domain 测试作者） |

### E-2 并发 + 真事务（1 行）

| 行 | 缺什么 | 复跑命令 | 判据 | 责任人 |
|---|---|---|---|---|
| E-070 | 落账与撤回竞争的真 SQLite 事务证据 | JVM：`./gradlew --offline -q :core:data:testDebugUnitTest --tests com.tingyun.smartmistakebook.core.data.study.ErrorCauseRetractorTest`（补 `<新增并发撤回用例>`）；设备：`python -I tools/ci/am_instrument_recorder.py --serial <online-serial> --package com.tingyun.smartmistakebook.core.database.test --class com.tingyun.smartmistakebook.core.database.ErrorCauseLedgerInstrumentedTest#<新增并发用例>` | 设备轮 exit 0；`device.json` serial/APK sha 与本轮一致；scope 内 ACTIVE ≤1；`active_key` 清空；审计行保留 | E 域工程（core/data + core/database 测试作者） |

### E-3 DST（1 行）

| 行 | 缺什么 | 复跑命令 | 判据 | 责任人 |
|---|---|---|---|---|
| E-086 | `America/New_York` 2026-03-08 春季跳变与 2026-11-01 秋季回拨探针 | `./gradlew --offline -q :core:domain:test --tests com.tingyun.smartmistakebook.core.domain.StudyDayMathTest`（补 `<新增 DST 用例>` 后） | 学习日在 01:59→02:01/03:00 与重复小时 01:30 上不跳变、不重复；期望值写进断言而非截图 | E 域工程（core/domain 测试作者） |

**改判前置**：新用例 + 新证据入盘 + 新行级复核 JSON（以该行测试方法名为行特有 check）+ 台账 verdict 三件事同批；缺一保持 `BLOCKED`。
**红线**：不得用“纯函数所以并发安全”的推理替代用例；不得用模拟器 x86_64 结果外推 DST/真机；E-070 不得用 JVM 假端口结果替代设备事务证据。

## B 域

> 覆盖 B 域 **2 行** `BLOCKED`（B-077 / B-078），均属 **impl-gap（缺 mask=13 夹具，内部工程可执行）**，
> 无外部供应商依赖。台账 `方法` 列的指针写的是 `runbook.md` §9；详细出口已写在
> `runbook.md` §9.3（台账不改字，避免重签 920 份 `ledger_sha256`）。
> 现状核实（本轮读源码）：mask=13 只有 clean 往返（`BackupSelectionRestoreInstrumentedTest.kt:120`），
> partial 回执只有全选 mask=15（`BackupRestoreInstrumentedTest.kt:851`），corrupt 夹具只有
> mask=9/15（`BackupSelectionRestoreInstrumentedTest.kt:278`、`:392`）。

| 组 | 缺失条件 | 行 |
|---|---|---|
| B-1 | 缺 mask=13 的 partial（部分恢复回执）夹具 | B-077 |
| B-2 | 缺 mask=13 的 corrupt（篡改包）夹具 | B-078 |

> 统计口径：2 = 1 + 1，与台账 B 域 `BLOCKED` 计数逐行相等（无遗漏、无重复）。

### B-1 / B-2 mask=13 状态夹具（2 行）

| 行 | 缺什么 | 复跑命令（补夹具后，门禁阶段串行） | 判据 | 责任人 |
|---|---|---|---|---|
| B-077 | mask=13 包 + 设置顺序写失败，验证 partial 回执与数据库不回滚 | `python -I tools/ci/am_instrument_recorder.py --adb /c/Android/Sdk/platform-tools/adb.exe --serial emulator-5562 --package com.tingyun.smartmistakebook.core.data.test --class com.tingyun.smartmistakebook.core.data.backup.BackupSelectionRestoreInstrumentedTest#subsetThirteenPartialSettingsWriteIsReportedWithoutRollingBack` | XML `tests=1 failures=0 errors=0 skipped=0`；`receipt.selection.mask==13`；`receipt.settings==WRITE_FAILED`；会话表保留 1 行；数据库不回滚；失败前已成功设置键按 KD-47 保留 | B 域工程（core/data androidTest 夹具作者） |
| B-078 | mask=13 包的 digest/rowCounts/closureVersion/categoryMask/schemaVersion 篡改，验证交换前拒绝 | 同上 recorder，`--class com.tingyun.smartmistakebook.core.data.backup.BackupSelectionRestoreInstrumentedTest#subsetThirteenTamperedPackageIsRejectedBeforeSwap` | XML `tests=1 failures=0 errors=0 skipped=0`；`validate` 与 `restore` 均在交换前拒绝；恢复前后 `rowCount` 不变 | B 域工程（core/data androidTest 夹具作者） |

**改判前置**：新夹具 + 新设备证据入盘 + 新行级复核 JSON（以该行测试方法名为 primary 的行特有 check）
+ 台账 verdict 变更三件事同批；缺一保持 `BLOCKED`。
**红线**：不得用 mask=9/15 的结果外推到 mask=13；不得把 `UNVERIFIED` 直接写成 `PASS`；
两行保持 `BLOCKED` 直到上述证据齐全。

## C 域

> 覆盖 C 域 **2 行** `BLOCKED`（C-008、C-088），均属 **内部（总索引 §7a，工程可写；
> 收口需主会话排期授权）**，无外部供应商依赖。被测实现 `VisualDrawOps*` 在
> `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/`（`git ls-files` 本轮核实），
> **core/data 非热区**；Node 测试台 `tools/visual_compiler/run_tests.mjs` 亦非热区。

| 组 | 缺失条件 | 行 |
|---|---|---|
| C-1 | 蜕变库 R-03「嵌套合成与 det 乘法」无第二类证据 | C-008 |
| C-2 | 屏幕回放不绘制 hitRegions 无专项屏幕用例 | C-088 |

> 统计口径：2 = 1 + 1，与台账 C 域 `BLOCKED` 计数逐行相等（无遗漏、无重复）。

### C-1 R-03 嵌套合成（C-008，内部）

- **缺什么**：`docs/research/2026-11-06-metamorphic-relations.md` §R-03 自记「探针只覆盖单矩阵按契约
  列序回显（90° 旋转），嵌套合成未测」；`abs(det(N·M) − det(N)·det(M)) ≤ 1e-9` 的合成可逆性无可执行
  证据；台账证据现只有 `file`+`cmd` 两类，缺第二类独立证据。
- **复跑命令**（补探针/用例后，门禁阶段串行）：
  1. Node 侧：把「矩阵 `M` 的组再包一层矩阵 `N` 的组」样本加进 `tools/visual_compiler/run_tests.mjs`
     后 `node tools/visual_compiler/run_tests.mjs`；或 `python -I tools/ci/metamorphic_checks.py --pairs-dir <pairs> --only R-03 --out <evidence>`。
  2. Kotlin/设备侧第二类：在 `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/`
     加嵌套组装载用例后跑
     `python -I tools/ci/am_instrument_recorder.py --serial <serial> --package com.tingyun.smartmistakebook.core.data.test --class com.tingyun.smartmistakebook.core.data.visual.<类>#<用例> --out-dir <OUT> --apk-sha <sha> --test-apk-sha <sha>`。
- **判据**：发射两个 `concat` 且顺序 `[N, M]`（后乘）；矩阵逐元素相对误差 ≤ 1e-12；
  `abs(det(N·M) − det(N)·det(M)) ≤ 1e-9`；Node 与设备两侧证据齐（第二类证据补齐后才具备改判前提）。
- **责任人**：C 域工程（tools/visual_compiler + core/data visual 测试作者）；复核独立于作者。

### C-2 hitRegions 屏幕侧（C-088，内部）

- **缺什么**：PDF 半边已有 `pdfDoesNotPaintHitRegions` 设备证据；**屏幕回放**（非 PDF painter 路径）
  不绘制命中区无专项用例。
- **复跑命令**：在 `VisualDrawOpsPainterInstrumentedTest`（同目录，非热区）加「hitRegions 节点屏幕回放
  不产生绘制 op」用例后跑 `python -I tools/ci/am_instrument_recorder.py --serial <serial> --package com.tingyun.smartmistakebook.core.data.test --class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest#<用例> --out-dir <OUT> --apk-sha <sha> --test-apk-sha <sha>`。
- **判据**：XML `tests=1 failures=0 errors=0 skipped=0`；断言 op 列表无 hitRegions 产生的绘制项
  （期望写进断言，不是截图描述）；recorder `device.json` verdict=PASS 且 serial/APK sha 同轮。
- **责任人**：C 域工程（core/data androidTest 作者）。

**改判前置**（与 B/E 域同）：新用例 + 新证据入盘 + 新行级复核 JSON（以本行用例名为行特有 check）
+ 台账 verdict 变更三件事同批；缺一保持 `BLOCKED`。

## D 域

> 覆盖 D 域 **9 行** `BLOCKED`（D-082..D-090），全部属 **外部（总索引 §1 Provider）**。
> 逐行条件、步骤、判据已在 `runbook.md` §9.4（含「环境变量注入」文案不可执行的口径澄清、
> `localFirst` 变体前提、逐行判据表）；本节给组表，不重复 §9.4 正文。
> 台账 `方法` 列「以环境变量注入 `*_API_KEY`」属不可执行文案，改字要重签行级 JSON，
> 执行以 §9.4「设备 `模型设置` 输入」为准。

| 组 | 缺失条件 | 行 | 命令要点 | 判据 | 责任人 |
|---|---|---|---|---|---|
| D-1 | 真实 Provider 账号/Base URL/模型 ID/API Key（密钥经设备输入，不落盘不回显） | D-082, D-083, D-084, D-085, D-086, D-087, D-088, D-089, D-090 | 门禁阶段 `./gradlew :app:assembleLocalFirstDebug` → `adb -s <serial> install -r` → 设备「模型设置」配置「测试模型能力」通过 → 逐行取证 `docs/research/evidence/<批次>/d-domain/`（runbook §9.4 步骤 1–4） | runbook §9.4 逐行判据表：D-082 主路径 200 且上游请求 id 同证据；D-083 三失败码映射 `AUTHENTICATION_FAILED`/`RATE_LIMITED`/`SERVICE_UNAVAILABLE`；D-084 边界外 fail-closed；D-085 取消无半条；D-086 重试与账本逐条对上；D-087 派遣 ≤6 且与上游计费一致；D-088 原文只进受控缝隙；D-089 证据头四项齐全无密钥；D-090 无 key 只能 `BLOCKED`。证据含 run-id/APK sha/serial | 外部：Provider 账号持有人；内部：发布负责人（执行与取证） |

> 统计口径：9 = 9，与台账 D 域 `BLOCKED` 计数逐行相等（无遗漏、无重复）。
> **红线**：D-090 是口径行本身——无 key 时的任何「绿」都无效，按假通过处理。

## F 域

> 覆盖 F 域 **33 行** `BLOCKED`，分四组：阈值/口径未定（内部·总索引 §7b）、基线/重复缺失
> （内部·§7b，复跑）、低存储确认（内部复跑）、arm64（外部·§3）。复跑入口
> `docs/research/evidence/2026-11-05-batch-44/f-domain/00-index.md` 与 `runbook.md` §9.1。

| 组 | 缺失条件 | 行 | 命令要点 | 判据 | 责任人 |
|---|---|---|---|---|---|
| F-1 阈值/口径未定 | 无产品数值目标或进程/相位定义（2000ms/500ms 只对真机成立、200MB 未定义相位、PSS 不可归因、2h 无阈值、预览无目标等） | F-002, F-011, F-012, F-020, F-038, F-039, F-047, F-048, F-050, F-056, F-074, F-083 | 产品/性能负责人书面定阈值（含进程/相位口径）后，按 00-index §7 与 §9.1 在同设备重跑对应场景（`StartupBenchmark`/`am start -W`、导出/预览、50ms PSS 切片、2h soak） | 实测值与产品阈值可比（同设备同构建）；F-047/F-048/F-050 须「干净安装 + 产品进程内导出相位」口径；无阈值时只记观测，不判红绿 | 产品/性能负责人（阈值）；F 域工程（复跑） |
| F-2 基线/重复缺失 | 无同设备同构建历史基线；单窗/单次未重复 | F-009, F-018, F-027, F-054, F-063, F-072, F-081, F-090（基线）+ F-058, F-085（未重复） | 同机同 APK 连跑两批（批次 43 基线在 emulator-5584，不同实例不可比），保留两批 JSON/dropbox；重复窗口按场景复跑第二窗/第二次 | 两批同口径数据在盘且差异可解释；回归判据由 F-1 组阈值决定（阈值未定前只建基线） | F 域工程（复跑）+ 产品（阈值，若未定） |
| F-3 低存储确认 | /data 98% 时启动已证（PID 存在、FATAL/ENOSPC 0），UI 未确认 | F-084 | 专用模拟器填至 98% 后走完整导出/预览并确认失败文案（设备轮，recorder 取证） | 失败文案可见、FATAL=0、数据不丢；证据绑定 serial/APK sha | F 域工程（复跑） |
| F-4 arm64 | 无 arm64 真机或镜像 | F-006, F-015, F-024, F-033, F-042, F-051, F-060, F-069, F-078, F-087 | 见总索引 §3 / runbook §9.2；设备确认命令 `adb -s <serial> shell getconf PAGE_SIZE`（须=16384） | 同 §3 | 设备持有人（外部）+ F 域工程 |

> 统计口径：33 = 12 + 10 + 1 + 10，与台账 F 域 `BLOCKED` 计数逐行相等（无遗漏、无重复）。
> **红线**：模拟器数值不冒充真机判定；无阈值不伪造阈值；超时不改记「慢但可用」。

## G 域

> 覆盖 G 域 **7 行** `BLOCKED`，分五组（外部 2 行 / 内部 5 行）。

| 组 | blocked_by | 行 | 缺失条件 | 复跑命令 | 判据 | 责任人 |
|---|---|---|---|---|---|---|
| G-1 | 内部（§7c） | G-016 | 未触发崩溃采集，trace 文件未取得 | 设备轮中经既有失败路径或 `adb -s <serial> shell am crash <applicationId>`（applicationId 取自 `output-metadata.json`，不杜撰）触发一次崩溃并采集 trace | trace 文件在盘且经脚本+人工双重检查不含密钥片段；证据绑定 serial/APK sha | G 域工程 |
| G-2 | 外部（§1 Provider） | G-036 | 无 Provider 凭据，运行时真实外发未执行 | 同 D 域组（runbook §9.4） | 同 D 域 | Provider 账号持有人 + 发布负责人 |
| G-3 | 内部（§7c） | G-072 | 无法执行 Kotlin 构造穿越 URI（源码级 canonical 守卫已断言） | 在 `core/data` 测试（非热区）构造恶意 canonical/穿越 URI 输入并断言拒绝 | 恶意输入全部拒绝且拒绝码可复算；先红后绿 | G 域工程 |
| G-4 | 内部（§7c） | G-080 | 需导航到配置页后截图比对，本次未执行 | 设备轮导航到配置页截图，与「应为黑屏」基线比对（`tools/ci/screenshot_baseline.py`） | 截图在盘且比对判据（阈值显式）通过；证据绑定 serial/APK sha | G 域工程 |
| G-5 | 内部裁定（§7b） | G-081 | 代码未设置 `EXTRA_IS_SENSITIVE`，是否构成泄露需产品裁定 | 产品裁定要求设置 → 授权落点（热区）改码后复跑；裁定不要求 → 主会话终裁改判口径 | 裁定书面在盘；若改码则剪贴板预览策略断言通过 | 产品 + 主会话 |
| G-6 | 外部（§5 环境） | G-087 | 无离线 advisory 库、未联网取证 | 接入离线 CVE advisory 资料库（不得构成构建依赖，来源主会话批准）后联网取证比对 | 比对结果在盘；不得据此声称「无漏洞」，只登记已比对范围 | 用户/他线（资料）+ G 域工程 |
| G-7 | 内部授权（§7e） | G-088 | `check_lockfiles` 处于 NOT_LANDED 放行态、13 个未跟踪锁不能原样提交（本轮实跑复核，输出与台账一致） | 主会话授权提交锁文件（CI `--write-locks` 落 13 个锁）后，`python -I tools/ci/check_lockfiles.py` 在覆盖态 exit 0 | 覆盖态 exit 0；缺锁 project（:benchmark）有明确处置 | 主会话（授权提交）+ G 域工程 |

> 统计口径：7 = 1+1+1+1+1+1+1，与台账 G 域 `BLOCKED` 计数逐行相等（无遗漏、无重复）。

## H 域

> 覆盖 H 域 **19 行** `BLOCKED`，分六组。执行门：`python -I tools/ci/h_compliance_gate.py --only <ID>` /
> `--topic <topic>`（本轮 `--help` 核实支持 `minor`、`i18n`、`privacy`、`data-safety`、`disclaimer` 等）。

| 组 | blocked_by | 行 | 缺失条件 | 复跑命令 | 判据 | 责任人 |
|---|---|---|---|---|---|---|
| H-1 AIGC 角标 | 内部热区（§6） | H-008 | G1/G2 接线未落地，`feature/tutor`/`core/ui` 热区禁改 | 主会话授权改码后：设备截图 + `h_compliance_gate.py --only H-008` | 讲题与配图界面显示「AI 生成」角标；门 exit 0（=FIX-T17 出口） | 主会话（授权）+ feature/tutor 工程 |
| H-2 青少年 | 内部（§6 H-041 热区；§7c H-039/040/042 排期） | H-039, H-040, H-041, H-042 | G7 第 3/4/8 项未落地；`feature/profile` 热区未空出 | 主会话排期落地后：`h_compliance_gate.py --topic minor` | 年龄确认与监护人同意接线、家长须知入口、时长提醒与静默开关存在；`--topic minor` exit 0（=FIX-T16 出口） | 主会话（排期/授权）+ feature/profile 工程 |
| H-3 隐私政策/Play/备案/英文材料 | 外部（§5 环境） | H-024, H-025, H-036, H-043, H-053, H-081 | 需开发者域名与 Play 后台、CAC 备案名单、英文上架材料 | 政策公开 URL 可访问、表单提交回执与备案号回填后：`h_compliance_gate.py --only H-024,H-025,H-036,H-043,H-053,H-081` | 各断言 exit 0；URL 可公开访问；备案号回填（=FIX-T18 出口） | 用户/他线（域名/账号/备案）+ H 域工程 |
| H-4 免责声明关于页 | 内部热区（§6） | H-051 | 应用无关于页，嵌入需动热区 | 主会话授权落点后嵌入三档文案：`h_compliance_gate.py --only H-051` | 三档文案在关于页或首启弹窗；门 exit 0 | 主会话（授权）+ 所属域工程 |
| H-5 人工视觉/语感 | 外部（§4 金标） | H-063, H-089, H-090 | 需真实 PDF 截图与人工审美/走查判断 | 按 runbook §9.5.3 评审流程；对照材料：`python -I tools/ci/screenshot_baseline.py capture --serial <serial> --out <png> --apk-sha <sha>` | 评审人逐项签署（同意/不同意+理由）；未签署维持 `BLOCKED` | 用户/他线评审人 |
| H-6 i18n 口径 | 内部裁定（§7d） | H-075, H-076, H-077, H-080 | 47 文件硬编码字面量/RTL 未走查/locale 未验证；产品未定义本地化要求 | 产品定 i18n 范围后：`h_compliance_gate.py --topic i18n`；裁定「仅中文」则按不适用登记（主会话终裁，不由本线自改） | 门 exit 0 或书面不适用裁定在盘 | 产品 + 主会话 |

> 统计口径：19 = 1 + 4 + 6 + 1 + 3 + 4，与台账 H 域 `BLOCKED` 计数逐行相等（无遗漏、无重复）。

## I 域

> 覆盖 I 域 **37 行** `BLOCKED`。逐行条件/命令/判据/责任人已在 `runbook.md` §9.5（§9.5.1 外部硬件 7 行、
> §9.5.2 独立复核 9 行、§9.5.3 人工语感 14 行、§9.5.4 内部工具/产品决策 7 行），本节只给组表与
> 总索引类别的对应；两文件冲突时以 §9.5 详版为准。

| 组 | 行 | blocked_by（总索引） | 出口要点 |
|---|---|---|---|
| I-1 外部硬件/供应商/真机 | I-007, I-050, I-061, I-068, I-070, I-075, I-079 | 外部：I-061→§1 Provider（出口 FIX-T08）；其余 6 行→§5 环境（驱动/IME/真机/折叠屏） | runbook §9.5.1 逐行命令（`a11y_probe.py` capture/focus、`ime list -a`、旋转 settings 等）；证据绑定 serial/APK sha；不得用 `adb input text` 冒充中文 IME |
| I-2 独立复核 | I-009, I-018, I-027, I-036, I-045, I-054, I-063, I-072, I-081 | 外部（§4 金标·第二人/第二模型） | runbook §9.5.2：非作者按行 JSON `checks[]` 逐条重跑 + 另写 review JSON（`reviewer ∉ authors`、`independent_agent=true`）；签署语义充分性意见 |
| I-3 人工语感/视觉 | I-005, I-032, I-041, I-059, I-077, I-082, I-083, I-084, I-085, I-086, I-087, I-088, I-089, I-090 | 外部（§4 金标·人工评审） | runbook §9.5.3：对照材料（`a11y_probe.py` capture，暗色/大字号/横屏叠 `--night yes`/`--font-scale 2.0`/`--wm-size 2400x1080`）；逐项签署，机器审计只作对照 |
| I-4 内部工具/产品决策 | I-014, I-023, I-025, I-030, I-039, I-043, I-066 | 内部（§7b/§7c） | runbook §9.5.4 逐行出口（新审计项/像素采样/阈值/裁剪检测/深色主题裁定/中文输入用例）；I-039/I-043 的「BLOCKED 与行内文案冲突」按 §9.5.5 保留判词、由主会话终裁 |

> 统计口径：37 = 7 + 9 + 14 + 7，与台账 I 域 `BLOCKED` 计数逐行相等（无遗漏、无重复）。
> 以上 37 行全部仍是 `BLOCKED`；本节不产生任何 PASS。

## J 域

> 覆盖 J 域 **48 行** `BLOCKED`，分六组。J 域证据与命令基准：`docs/research/evidence/2026-11-05-batch-44/j-domain/00-index.md`
> 与 `runbook.md` §9.2；`j_release_verify.py` 子命令（identity/apk16k/aab16k/r8/signing/version/r8keep）
> 本轮 `--help` 核实。

| 组 | blocked_by | 行 | 缺失条件 | 复跑命令 | 判据 | 责任人 |
|---|---|---|---|---|---|---|
| J-1 门禁阶段构建面 | 内部（§7e 流程窗口） | J-003, J-006, J-007, J-022 | 共享树未提交且验收阶段不跑 Gradle | 主会话提交后门禁阶段：`./gradlew :app:assembleStrictOfflineRelease`、`:app:bundleStrictOfflineRelease`（J-003）；两次 clean 构建产物 `cmp` 逐字节（J-007）；`./gradlew validateReleaseSigning` 缺变量必须失败（J-022） | 构建成功且产物 revision 与 HEAD 一致；两次构建字节一致；负向 exit≠0 | 主会话（提交/窗口）+ J 域工程 |
| J-2 签名与成对产物 | 外部（§2 签名） | J-019, J-055, J-056, J-057, J-061, J-065, J-068, J-069, J-070, J-087 | `RELEASE_*` 4/4 未设；无成对 vc 产物与同钥匙 | 配齐后 `j_release_verify.py signing` + `apksigner verify --print-certs`；同钥匙出 vc N/N+1 按 §9.2 跑升级/降级/回滚 | 指纹=已登记发布指纹；AAB/APK 同身份；三向 Success 且 `dumpsys` vc 等于目标、数据保留 | 发布密钥持有人（外部）+ J 域工程 |
| J-3 arm64/16KB | 外部（§3 arm64） | J-035, J-037, J-038, J-039, J-042, J-043 | x86_64 宿主不能起 arm64 AVD；两台在线设备均 x86_64 | 换 arm64 真机：`adb shell getconf PAGE_SIZE`=16384、安装/冷启、三条 native 路径、`logcat` FATAL=0 | 设备证据绑定 serial/boot/APK sha | 设备持有人（外部）+ J 域工程 |
| J-4 独立复核 | 外部（§4 金标） | J-008, J-018, J-026, J-036, J-053, J-063, J-071 | 非作者 reviewer 终裁缺失 | 同 I-2 组流程（按行 JSON `checks[]` 复算 + 新 review JSON） | 复算 0 错误 + 语义签署 | 用户/他线第二人 |
| J-5 Play Console/商店 | 外部（§5 环境） | J-017, J-073, J-074, J-075, J-076, J-077, J-078, J-079, J-080, J-081, J-082, J-083, J-084, J-085, J-086, J-088, J-089, J-090 | 无 Play Console 账号、素材未备、无托管 URL；J-086/J-088 另含 vc 单调/AAB 重出子项（属 FIX-T11/T12，内部） | 按 `docs/research/2026-10-25-store-launch-materials-checklist.md` 备料上传、建内部/封闭轨道 | 各行对应上传/登记回执在盘；轨道建立且测试账号登记 | 用户/他线（账号）+ J 域工程（备料） |
| J-6 工具与判据裁定 | 内部（§7c/§7e/§7b） | J-049, J-066, J-067 | 缺跨产物 vc 碰撞检测器（J-049，工具新增）；J-066 依赖 B 域迁移矩阵；J-067 无本域可执行判据需主会话定 | J-049 新增检测器（tools/ci 非热区）后对同 vc 两产物跑负向；J-066 等 B-077/B-078 证据后按矩阵复跑；J-067 由主会话书面定判据后再取证 | 负向必红；矩阵口径一致；判据书面在盘 | J 域工程 + 主会话 |

> 统计口径：48 = 4 + 10 + 6 + 7 + 18 + 3，与台账 J 域 `BLOCKED` 计数逐行相等（无遗漏、无重复）。
> J 域另有 13 行 **FAIL**（不在本文件范围）：缺陷单 `docs/acceptance/j-fail-tickets-b47.md`，
> 其中 J-027 树内已修复待改判、其余 12 条维持 FAIL。

## FIX 域

> 覆盖 FIX 域 **85 行** `BLOCKED` = **17 单（FIX-T04..T20）× 5 角色**（红测试/绿修复/回归/证据/
> 不可修登记）。单的条件与出口正文在 `runbook.md` §13；本表按单给 blocked_by 类别与台账行。
> 复发门命令（`--gate`/`--all`/`--prove-red` 本轮 `--help` 核实）：
> `python -I tools/ci/fix_relapse_gate.py --gate FIX-Tnn [--prove-red]`。

| 单 | 主题 | blocked_by（总索引） | 台账行（5 角色） | 处置现状与出口 | 责任人 |
|---|---|---|---|---|---|
| FIX-T04 | 部分页打印丢 AIGC（KD-84） | 内部（§7e 改判收尾） | FIX-016、FIX-017、FIX-018、FIX-019、FIX-020 | **处置已修复**：runbook §13 记 KD-84 closed；本轮台账核实 H-061 已 `PASS`、证据 `docs/research/evidence/2026-11-06-batch-47/h-domain/h-061-pypdf-b47.log` 在盘。出口=复发门 exit 0 + 台账五行三件事同批改判 | 主会话（改判） |
| FIX-T05 | 模型改卷文本漏标（KD-85） | 内部热区（§6） | FIX-021、FIX-022、FIX-023、FIX-024、FIX-025 | 落点跨 `feature/tutor`（热区）需主会话授权；独立读回 `Label=1`、`ProduceID=输入 sha` | 主会话（授权）+ feature/tutor 工程 |
| FIX-T06 | 恢复回放零面积夹具 | 内部（§7e 提交+设备轮） | FIX-026、FIX-027、FIX-028、FIX-029、FIX-030 | 夹具已修（闭合三角形，工作树未提交）、设备绿轮未跑；出口=提交后 `am instrument ...TutorVisualRebuildRestoreInstrumentedTest#theRestoreLegAloneReplaysOfflineWithZeroDispatchBookings` 绿 + 复发门 exit 0 | 主会话（提交）+ C/B 域工程 |
| FIX-T07 | DOCX 无文档级 AIGC | 内部（§7e 已批准 hunk 通道） | FIX-031、FIX-032、FIX-033、FIX-034、FIX-035 | `core/export` 只做已批准 hunk；出口=加 docProps 标识 + 独立 DOCX 读回文档级 `/AIGC`（仅代码字面量不算） | 主会话（批准 hunk）+ core/export 工程 |
| FIX-T08 | 真实 Provider 端到端 | 外部（§1 Provider） | FIX-036、FIX-037、FIX-038、FIX-039、FIX-040 | runbook §9.4 全套 | Provider 账号持有人 + 发布负责人 |
| FIX-T09 | arm64 设备矩阵 | 外部（§3 arm64） | FIX-041、FIX-042、FIX-043、FIX-044、FIX-045 | runbook §9.2 arm64/16KB 组 + F 域 00-index §7 | 设备持有人 + F/J 工程 |
| FIX-T10 | 生产签名与指纹 | 外部（§2 签名） | FIX-046、FIX-047、FIX-048、FIX-049、FIX-050 | `RELEASE_*` 配齐后 `apksigner verify --print-certs` 指纹核对 | 发布密钥持有人 + J 工程 |
| FIX-T11 | 版本号单调与版本源 | 内部（§7e 发布裁定） | FIX-051、FIX-052、FIX-053、FIX-054、FIX-055 | `versionCode` 仍是手写常量 2；引入版本源属发布裁定；两次构建验证 vc 单调 | 主会话（发布裁定）+ J 工程 |
| FIX-T12 | AAB 16KB / ABI / quickjs | 内部（§7e 门禁重出） | FIX-056、FIX-057、FIX-058、FIX-059、FIX-060 | 干净树重出 AAB 后 `python -I tools/ci/j_release_verify.py aab16k` exit 0（ABI=abiFilters、含 `libquickjs.so`、无 32 位 `.so`）；16KB 设备子项属 §3 | 主会话（窗口）+ J 工程 |
| FIX-T13 | 干净树与产物 revision | 内部（§7e 提交） | FIX-061、FIX-062、FIX-063、FIX-064、FIX-065 | 提交后 `j_release_verify.py identity --require-clean --require-head` exit 0 | 主会话（提交）+ J 工程 |
| FIX-T14 | 商店素材与 Play 控制台 | 外部（§5 环境） | FIX-066、FIX-067、FIX-068、FIX-069、FIX-070 | 备料上传 + 登记轨道 | 用户/他线（账号）+ J 工程 |
| FIX-T15 | 成对产物升级/降级/回滚 | 外部（§2 签名） | FIX-071、FIX-072、FIX-073、FIX-074、FIX-075 | 同钥匙 vc N/N+1 三向演练 | 发布密钥持有人 + J 工程 |
| FIX-T16 | 青少年模式 | 内部热区（§6） | FIX-076、FIX-077、FIX-078、FIX-079、FIX-080 | G7 第 3/4/8 项落地 + `h_compliance_gate.py --topic minor` exit 0 | 主会话（授权）+ feature/profile 工程 |
| FIX-T17 | AIGC 显式角标 G1/G2 | 内部热区（§6） | FIX-081、FIX-082、FIX-083、FIX-084、FIX-085 | 角标上设备截图 + `--only H-008` exit 0 | 主会话（授权）+ feature/tutor 工程 |
| FIX-T18 | 隐私政策/data safety/备案/英文材料 | 外部（§5 环境） | FIX-086、FIX-087、FIX-088、FIX-089、FIX-090 | URL 可访问 + 表单回执 + 备案号回填后重跑对应断言 | 用户/他线 + H 工程 |
| FIX-T19 | 无障碍真实驱动/IME/旋转 | 外部（§5 环境） | FIX-091、FIX-092、FIX-093、FIX-094、FIX-095 | runbook §6 与 §9.5.1 真机走查 + 设备证据 | 用户/他线（真机/驱动） |
| FIX-T20 | 供应链与台账协议门 | 外部（§5 环境；含内部落锁子项 G-088） | FIX-096、FIX-097、FIX-098、FIX-099、FIX-100 | 接入离线 advisory 库联网取证；G-088 落锁后 `check_lockfiles.py` 覆盖态 exit 0；D0 两行由 D0 域处置 | 用户/他线（资料）+ 主会话（落锁授权） |

> 统计口径：85 = 17 单 × 5，与台账 FIX 域 `BLOCKED` 计数逐行相等（无遗漏、无重复）。
> **红线**：复发门锁住当前判词——关闭或改判必须同时提交独立读回/设备/复核证据并显式更新门；
> 每单 5 个角色行同进退，不允许只改判其中一行而留下半单。

## GATE 域

> 覆盖 GATE 域 **4 行** `BLOCKED`，全部属 **外部（§4 金标）**；GATE-100 另含门禁面。

| 行 | 缺失条件 | 复跑命令/流程 | 判据 | 责任人 |
|---|---|---|---|---|
| GATE-096 | 24 页冻结金标已备，但人工标注未执行（无标注员） | 交付标注包（用户/他线），在冻结金标上执行人工标注，产物入 `docs/acceptance/` 或证据目录并挂台账 | 标注产物在盘且与冻结金标页数一致；无标注不产生任何正确率数字 | 用户/他线标注员 + GATE 负责人 |
| GATE-097 | G3/G4/G5 人工金标产物不存在（依赖标注执行） | 同上；标注完成后按 G3/G4/G5 各自口径产出金标文件 | 三类产物在盘、可被复算脚本读取 | 用户/他线标注员 |
| GATE-098 | 人工金标正确率未测（无标注即无正确率） | 标注完成后运行正确率复算（脚本以产物为准） | 正确率数字可复算且来源为标注产物；阈值由验收负责人书面定 | 用户/他线标注员 + GATE 负责人 |
| GATE-100 | 发版不成立（AUD-08 为 P0 红线之一；门红） | AUD-08（本轮 4/7=0.5714 < 0.9，exit 1）只能因新证据转绿后提请终裁；其余门 exit 0 | 门禁全绿 + 人工金标（GATE-096..098）完成后由主会话终裁改判；在此之前维持 `BLOCKED` | 主会话（终裁） |

> 统计口径：4 = 4，与台账 GATE 域 `BLOCKED` 计数逐行相等（无遗漏、无重复）。
> **红线**：AUD-08 FAIL 是批次 46 终裁确认的不可变事实（KD-76 open / isolation 不定案 / p09 未收敛
> / AUD-08 FAIL 同列）；本节不预告 AUD-08 转绿，只登记其出口条件。

## 新增域小节模板（供后续域追加）

```markdown
## <域> 域

### <域>-1 <缺失条件类别>

| 行 | 缺什么 | 复跑命令 | 判据 | 责任人 |
|---|---|---|---|---|
```

（本文件只登记出口，不登记通过；任何改判以台账与行级复核 JSON 为准。）
