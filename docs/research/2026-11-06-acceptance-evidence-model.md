# 验收证据模型（R1）：PASS 的独立证据类型、ledger 行 schema 与 acceptance_report 判定规则

| 项 | 值 |
|---|---|
| 文件 | `docs/research/2026-11-06-acceptance-evidence-model.md` |
| 批次 / 落点 | 批次 44，D0 验收基础设施的研究件；服务计划 §1 items 1/3/4/5/6/7/8/9/12/13/14/15 与 §7 |
| 性质 | **研究 + 设计**。只读核对 + 外部检索；未写任何工具、未改任何源码、未跑 Gradle/设备、未做 git 写 |
| 不变项 | 6C-2 三项裁定（`mask=8` 不提供、`mask=0` 禁止、派生索引恒裁）只可加不变量测试；本文件不提出任何行为改动 |
| 证据等级 | 本文件的仓内事实=**实读/实跑**；外部事实按“已取正文 / 仅搜索摘要 / 未取到”三档标注（§10、§13） |
| 结论一句话 | **没有一种本地证据类型是“不可伪造”的；可做的是让伪造必须跨独立通道一致造假并同时命中内容哈希——此时“伪造”等价于“真跑一遍”。PASS 的门槛因此不是“证据类型清单”，而是“≥2 个独立单元 + 内容绑定 + 可证伪”的组合判定。** |

---

## 0. 结论摘要（照做清单）

1. **“不可伪造”必须降级为“伪造成本 ≥ 真跑成本 + 留下可检测不一致”**。任何本地文件（日志、XML、截图、verdict）都能被有写权限的 agent 写出来；T4（可改时钟/可改环境）下本地证据一律不可信。见 §2。
2. **PASS 的充分条件（本模型）**：`≥2 个独立证据单元` ∧ `≥2 个证据类` ∧ `至少 1 个主证据类` ∧ `内容绑定完整` ∧ `可证伪` ∧ `无矛盾观测`。缺任一项 → 至多 `UNVERIFIED`。见 §4。
3. **verdict 是待校验的输入，不是权威**。`acceptance_report` 先由证据算出 `machine_verdict`，再与 `docs/acceptance/verdicts/` 的签字比对；**签字比机器更宽松 = exit 1**。这一条直接消灭“主会话签 PASS、证据其实是 UNVERIFIED”。
4. **内容绑定优先于时间戳**。证据记录 `binding.targets: {path: sha256}`，报告按当前文件重算；哈希不符即 `STALE`。时间戳可回填，内容哈希不能（除非同时改文件并让所有下游证据一起改）。见 §5-I6、§7-R13。
5. **同一次运行的两种输出不是两类独立证据**。Gradle 控制台日志 + JUnit XML 同属一个执行单元；要过 `≥2 类` 必须再有一次不同 run / 不同环境 / 不同产出者，或工具能**机械重算**两条不同派生（XML 计数 vs 退出码；截图像素 vs 语义树）。见 §4.2。
6. **summary / index / record 是叙述类，永不计单元**。它们可作导航，不能作证据。D0 item 4 的“禁止 summary 当证据”落成 `R-SUMMARY`（§7）。
7. **跳过、重试、最佳一次、跨 APK 合并一律不得计 PASS**。这是批次 40–43 五次假绿的共同形状（§8.2）。
8. **设备 XML 不含 APK/源码身份**（本轮实测：`app/build/outputs/androidTest-results/.../TEST-*.xml` 只有 `device/flavor/project` 三个 property）。设备结论必须另配 run manifest（APK sha、serial、boot_id、HEAD）。见 §3.2。
9. **截图 sha256 不能当等同判据**（同机重复截图的字节稳定性本轮未验证）。用“全尺寸 sha256（存档）+ 32×32 灰度指纹 + 感知哈希 + 阈值重算”，阈值只可来自工具常量（`R-PARAM`）。见 §3.4。
10. **蜕变关系（MR）是无 oracle 域最强判据，但必须预注册**；事后挑关系等于把“看起来对”换个说法。见 §3.5、§7-R21。
11. **15 条历史假绿 → 15 条规则全部可回放为 FAIL**；回放器自身还要有 5 条自突变测试，证明它会红。见 §8。
12. **1200 行先枚举再有判定**：报告对“缺行/缺 verdict”给 exit 2（未完成），对“假绿/无效证据/PASS 无据”给 exit 1（完整性违规）。二者绝不混为 0。见 §7.3。

---

## 1. 问题、边界与术语

### 1.1 为什么需要这个模型

用户 2026-11-05 明确“无法做人工深层验收”，要求以可执行检查替代。计划 §1 已给出 D0 的 10 个交付物，但基线核对发现这些路径**一个都不存在**（`docs/research/2026-11-06-batch-44-baseline.md:203-218`：11/11 ABSENT），且 1200 项里 **≥1020 项没有可枚举载体**（同文件 `:277`）。同时，批次 40–43 已累计 **15 条**“形式上绿、实质不成立”的记录（同文件 §6.1，本轮 `awk` 实数 15 行）。

本文件回答三件事：

1. 一份 PASS 到底要哪几类证据、彼此怎样才算独立；
2. `docs/acceptance/ledger.md` 的每一行长什么样（机器可读 schema）；
3. `tools/ci/acceptance_report.py` 怎样把“证据”变成“判定”，并在历史假绿上必然 exit 1。

**不做**：不实现工具、不写 ledger 的 1200 行（那是 D0 item 1 的执行量）、不改任何产品代码、不评审 6C-2 口径。

### 1.2 术语（本文件内固定含义）

| 术语 | 定义 |
|---|---|
| 证据项 evidence item | 一个有类别的产物：一条命令记录、一份 JUnit XML、一段设备输出、一张截图指纹、一次 MR 的两端结果等 |
| 执行 run | 一次命令调用，由 `run_manifest.json` 封存（含 argv、cwd、HEAD、targets 哈希、退出码、产物哈希） |
| 单元 unit | `(class, run_id)`。同一次运行的两个不同类产物是**两个单元**，但只共享一个执行事实 |
| 主证据类 primary | 能单独承载“观测到的事实”的类：`CMD / XML / DEV / IMG / STATE / MR` |
| 绑定 binding | 证据与“哪份源、哪个 APK、哪台设备”的机械对应关系（哈希/序列号/boot_id） |
| 可证伪 falsifiable | 该断言存在一个已知输入变换或突变会让它变红，且这个“会红”有落点（突变 run / 用例内负向对照 / 预注册 MR） |
| machine_verdict | 报告仅凭证据算出的判定：`PASS_HEAD / PASS_WORKTREE / UNVERIFIED / FAIL / BLOCKED / REJECTED` |
| signed verdict | `verdicts/<id>.json` 里的终裁：`PASS / FAIL / BLOCKED / UNVERIFIED`（计划 §7 的四值） |

### 1.3 判定四值的语义（不得混用）

| 值 | 含义 | 允许的支撑 |
|---|---|---|
| PASS | 断言成立，且证据达到 §4 门槛 | 主证据单元 ≥2、独立、绑定完整、可证伪 |
| FAIL | 断言被观测否定 | 一次明确的反例观测（红 run / 计数不符 / 拒绝原因命中） |
| BLOCKED | **尝试过**，被具名外部依赖挡住 | 尝试 manifest（如 `adb` 的 `FATAL … arm64 is not supported`）+ `blocked_by` + runbook 路径 |
| UNVERIFIED | 没跑、没测到、或证据不足 | 任何情况都合法；**永远不能写成 PASS** |

「BLOCKED 但不是尝试出来的」= UNVERIFIED（`R-BLOCK`）。这条针对批次 43 的 `arm64`（真尝试、真 FATAL，合法 BLOCKED）与“没有扫描器所以写 UNVERIFIED”（合法）两类，防止把“没做”包装成“被阻塞”。

---

## 2. 威胁模型：什么叫“不可伪造”

### 2.1 四层威胁

| 层 | 能力 | 能伪造什么 | 本批对策 | 残余 |
|---|---|---|---|---|
| **T1 叙述型** | 只会写文字，不跑工具 | 结论、摘要、verdict | 叙述类不计单元（`R-SUMMARY`） | 无 |
| **T2 单点造假** | 能跑工具、能写文件、能复制旧产物 | 日志、XML、截图、哈希清单 | ≥2 独立单元 + 工具重算 + 内容绑定 | 设备/性能类仍可低成本造假（§2.3） |
| **T3 串谋复核** | 多个 agent 写互相印证的 verdict | 复核结论 | 复核者 ≠ 产出者、不同 run、不同模型；复核不计单元 | 同模型多 agent 的相关性无法本地证伪 |
| **T4 环境控制** | 可改时钟、可换设备、可改 adb 传输 | 一切本地证据 | **无本地对策**；只能靠外部锚（远端 commit、CI run id、Play Console） | 本批无 push/CI → 上限 I3（§4.4） |

### 2.2 本模型的“不可伪造”定义

> 一条 PASS 不可伪造，当且仅当：伪造它所需的工作量 ≥ 真跑一遍，**且**伪造会在至少一条与主证据不同源的通道上留下可机械检测的不一致。

推论：

- **单类证据永远不满足**。任何单文件都可被写出来。
- **同类双份也不满足**。“跑两次都绿”不能排除测试本身空转（批次 42 的 `R-EXERCISE`：修复件结构 fail-closed，但两臂方法齐的场景从没跑过）。
- **能机械重算的派生才算第二通道**。报告不信任 XML 里写的 `failures="0"`，而是数 `<testcase>`/`<failure>` 节点重算（`R-XMLCOUNT`）；不信任截图哈希，而是重算指纹与像素差（`R-IMG`）。
- **内容绑定把“旧证据”变成“无效证据”**。批次 40 的“旧 APK 当当前树”、批次 43 的“6 份 APK 跨轨合并”，都是因为证据没绑内容哈希。

### 2.3 各证据类的伪造成本（T2 视角，定性）

| 类 | 单独伪造的最小动作 | 成本 | 报告能抓什么 |
|---|---|---|---|
| SUMMARY | 写一段话 | ≈0 | 直接拒收 |
| CMD | 写一段带 `BUILD SUCCESSFUL` 的文本 | 低 | 缺显式 exit / argv 不可解析 / targets 哈希不符 → 拒 |
| XML | 复制旧 XML 或手改计数 | 低-中 | 计数重算不符、时间早于 targets、无 manifest → 拒 |
| DEV | `echo` 一段 `getprop` | 中 | boot_id/uptime/序列号与 manifest 不自洽、APK sha 取不到 → 降级 |
| IMG | 截图后只存哈希 | 中 | 无指纹/无阈值重算 → 不可单独成证 |
| STATE | 导出 DB 计数 | 中 | 计数与 schema/identity 不自洽、跨 run 不一致 → 拒 |
| MR | 造两端结果 | **高**（两端要同时满足关系且各自绑定） | 未预注册 / 两端 targets 不同 / 关系被事后改写 → 拒 |
| REVIEW | 写 verdict | ≈0 | 不计单元；与 machine_verdict 宽松即 exit 1 |
| MUT | 造红 run | 高（要造一个真能红的断言） | 与主 run 的 targets 不一致 → 拒 |

**诚实结论**：设备、性能、真机三类在 T2 下仍不是“不可伪造”，只是“造假比真跑贵”。因此本批对这三类的 PASS 额外要求多 run 与全量报告（`R-COUNT`/`R-RETRY`），并把发版域（J）的最终结论留给外部锚（BLOCKED）。

---

## 3. 证据类型目录（11 类，6 类可承载 PASS）

### 3.1 分类总表

| 类 | 可承载 PASS | 证明什么 | **不能**证明什么 | 仓内先例 / 教训 |
|---|---|---|---|---|
| `CMD` 命令记录 | 是（需 manifest） | 某条命令在此环境跑过、退出码为 N | 命令没跑过就不能证明；`BUILD SUCCESSFUL` 不等于逐例通过 | 批次 42 `R-EXIT`（无 exit code 的“6 组 exit 0”，记录 `:418`） |
| `XML` 测试结果 | 是（需重算） | 逐例通过/失败/跳过计数 | 身份（不含 APK/源码，实测）；不能证明断言非空转 | 批次 43 两份相反单测日志（§8.2 #13） |
| `DEV` 设备输出 | 是（需身份） | 设备属性、boot_id、安装包路径、运行时长 | 不证明业务正确；可被 `echo` 伪造 | 批次 40 旧 APK（§8.2 #1）、41 控制标记只比源码（#5） |
| `IMG` 截图/像素 | 是（需指纹+阈值） | 某一帧的视觉状态 | 不证明交互后状态；字节稳定性未验证 | 批次 43 只有 6 份 APK 无最终树（#12） |
| `STATE` 数据快照 | 是（需 identity） | DB 版本、表数、行数、快照 identity | 不证明迁移路径正确（需配合 XML/MR） | 批次 40 `93 表 / aa9f0313` vs 当前 `92 表 / 1d086050`（#1） |
| `MR` 蜕变关系 | 是（需预注册） | 输入变换下必守关系成立（无 oracle 也能判） | 不证明绝对正确；只证明关系 | 计划 §0 已选此路线；本仓尚无 MR 库 |
| `HASH` 身份 | **否** | 某文件/包的字节身份 | 不证明行为；单独出现等于“我给一个哈希” | 批次 43 E 文件头 `apk-sha256` 字段 |
| `MANIFEST` 运行封条 | 否 | 把 argv/HEAD/targets/exit/产物绑成一次运行 | 不证明断言内容 | 本文件新增（§6.2） |
| `MUT` 突变/负向对照 | **否** | 该断言**能红** | 不证明当前实现正确 | 批次 42 `R-EXERCISE`；6C-2 用例内正/负向对照 |
| `REVIEW` 复核 verdict | **否** | 某人/某 agent 做过对抗尝试 | 不是观测事实；不独立于产出者时无价值 | 计划 §4“独立复核 + 对抗复核”；rulings JSON 的 `decidedBy.agentId=script@sha256` |
| `SUMMARY` 摘要/索引/记录 | **否** | 导航 | 一切 | 批次 43 记录两条与盘上相反的陈述（`R-DISK`） |

### 3.2 CMD 与 XML：为什么必须成对但不能互相顶替

- `XML` 是**逐例事实**：`<testsuite tests skipped failures errors>` + 每例 `<testcase>`；本轮实读的 JVM XML 形如 `tests="4" skipped="0" failures="0" errors="0" timestamp="2026-11-04T04:31:39.429Z" hostname="TIANSHAN"`（`app/build/test-results/testLocalFirstDebugUnitTest/TEST-…BackgroundWriterQuiesceTest.xml`）。
- 设备 XML 的根是 `<testsuites>`，`<properties>` 只有 `device / flavor / project` 三个键（实读 `app/build/outputs/androidTest-results/connected/debug/flavors/localFirst/TEST-audit_user_visible_20261016(AVD) - 14-_app-localFirst.xml`）。**没有 serial、没有 APK sha、没有 HEAD**——所以设备 XML 单独不能支撑任何“这份包在这台机上通过”的断言（这正是批次 43 §F7 的痛点）。
- `CMD` 是**执行事实**：argv、cwd、退出码。它证明“跑了”，不证明“跑对了”。
- 两者同源（同一次 Gradle）→ 一个执行单元。要成为两类独立，必须有：另一次 run、另一环境（host vs device）、另一产出者，或工具能机械重算的两条派生。报告对 XML 做计数重算、对 CMD 做退出码与 argv 校验，正是把“两条派生”做实。

### 3.3 DEV：身份字段清单（缺一即不得单独成证）

`serial`、`boot_id`、`ro.build.version.sdk`、`ro.product.cpu.abi`、`getconf PAGE_SIZE`、`/proc/uptime`、`pm path <pkg>` → APK 文件 sha256、`dumpsys package <pkg>` 的 `versionCode/lastUpdateTime`。批次 43 的 `bootCount` 假绿（同 boot 内 force-stop 冒充 20 轮，批次 41 `R-COUNT`）说明 `boot_id` 不是可选项。

### 3.4 IMG：截图哈希为什么不能单独用

Android 官方截图测试的定义是“把当前 UI 截图与**此前批准的参考图**比较”，阈值是 0–1 的差异比例（`0.0001f` = 0.01%；`developer.android.com/reference/tools/gradle-api/.../ScreenshotTestSuite`）。这给的是**比例阈值**，不是“哈希相等”。本仓不引新依赖，因此：

- 存：全尺寸 PNG 的 `sha256`（只作存档身份）、`width×height`、32×32 灰度指纹（文本 P2/PGM，约 3 KB）、64-bit 感知哈希（hex）、与基线的 `mean_abs_diff / max_diff`、采集命令与设备身份。
- 判：重采集 → 重算指纹与差异 → 与工具常量阈值比较。**不判** PNG `sha256` 相等（同机重复截图是否字节稳定本轮**未验证**，标 UNVERIFIED，见 §12）。
- 为什么用文本指纹：`docs/research/evidence/` 的预算规范是单目录 ≤16 个 `.md`、≤512 KiB（`2026-10-26-device-evidence-convention.md:1-40`），全尺寸 PNG 会直接击穿；且 `.gitignore:15` 忽略 `**/build/`、`:31` 忽略 `*.log`，`git check-ignore` 本轮实测 `build/batch44-unit.log` = IGNORED、`docs/acceptance/baselines/home.png` = trackable。二进制可提交不等于该提交。

### 3.5 MR：无 oracle 域的判据

蜕变测试解决“测试预言问题”：不判单个输出对不对，而判**输入变换下输出关系是否守住**（arXiv 2310.00338 摘要：“MT addresses the test oracle problem by checking relationships between input-output pairs … Metamorphic Relations (MRs)”）。关系库按域举例（每条都必须能写成“变换 T + 必守断言 I + 容差 ε”）：

| 域 | 变换 T | 必守关系 I |
|---|---|---|
| 渲染 C | 所有坐标平移 +Δ | 输出框位置平移 +Δ（裁剪内），尺寸不变 |
| 渲染 C | 交换两个不相交图元的顺序 | 位图/布局哈希不变 |
| 渲染 C | 插入全透明图元 | 位图不变 |
| 导出 | 同一模型导出两次 | PDF 页数、文本抽取、页序完全一致 |
| 导出 | 增加一页内容 | 页数恰好 +1，其余页文本不变 |
| 检索 | 插入不含查询词的文档 | 原查询 top-k 顺序不变 |
| 检索 | 复制一篇文档 | 排名顺序不变（幂等） |
| 调度 E | 所有时间 +1 天 | 到期集合单调平移；已服务集合不变 |
| 调度 E | 日容量翻倍 | 服务数不减（单调性） |

MR 的强度来自“造假必须同时造两端且满足关系”，弱点来自“关系可事后挑”——所以 `R-MR-PRE`：`preregistered_at` 必须早于第一端 run 的 `started_at`，且关系必须来自登记表（§5-I9）。

---

## 4. 独立性与 PASS 判定

### 4.1 单元与独立性打分

`unit = (class, run_id)`。对两个单元 `u,v`：

```
indep(u,v) = [u.class ≠ v.class] + [u.run_id ≠ v.run_id]
           + [u.producer ≠ v.producer] + [u.environment ≠ v.environment]
```

`environment` 取值如 `host-jvm`、`device:<serial>@<boot_id>`、`pdf-reader:<name>`。

- `indep ≥ 2`：独立（例：host XML 与 device XML；或同设备两次 run 但不同测试包产出者）。
- `indep = 1` 且 `cross_derivation = true`：可接受为 I2 下限。`cross_derivation` 只允许两种机械可重算组合：`(XML 计数, CMD 退出码)`、`(IMG 像素, 语义树/PDF 文本)`。工具必须两边都重算，不能只读声明值。
- `indep = 0/1` 且无 `cross_derivation`：不算第二单元。

### 4.2 为什么“XML + CMD 同一次 Gradle”不是两类独立（反例）

批次 43 有两份同族单测日志：`build/batch43-unit.log` = `BUILD SUCCESSFUL in 3m 2s`（08:23），权威门日志 `build/main-session-gate-batch43-unit.log`（13:37）= `1642 tests completed, 8 failed` / `BUILD FAILED`（批次 43 §9.1 门禁表 `2026-11-05-batch-43-record.md:540-544`；批次 44 基线 §6.1 第 13 行 `:244`）。同一套测试相隔约 5 小时给出相反读数，说明连**不同 run** 都可能一个“任务级绿”一个“逐例红”；同 run 的控制台与 XML 更不能互相充当独立证据（同 run 内两者是否可能读数相反，本轮**未验证**）。所以：

- 两条同源派生只能靠 `R-GATE`：PASS 必须**指名**哪一份是门日志，并附其 manifest；其它同名族日志不得引用。
- 真正独立的第二单元优先选：另一环境（host vs device）、另一产出者（Gradle vs `adb`/pypdf）、另一 run（预注册 N 次，全量报告）。

### 4.3 PASS_ELIGIBLE 的形式化

```
PASS_ELIGIBLE(row) ⇔
  P1 证据有效: 每个 unit 的 manifest 存在、sha256 对得上、无 DOCTYPE/ENTITY
  P2 主证据:   units 中至少 1 个 class ∈ {CMD,XML,DEV,IMG,STATE,MR}
  P3 数量:     |units| ≥ 2 且 |classes(units)| ≥ 2
  P4 独立:     存在 u,v 使 indep(u,v) ≥ 2，或 cross_derivation(u,v) 成立
  P5 绑定:     binding.head 与 targets 哈希可重算；设备类含 serial+boot_id+APK sha
  P6 可证伪:   falsifiability ∈ {mutation_ref 有效, embedded_negative_control 经复核,
                                mr_ref 已预注册, kind=identity}
  P7 无矛盾:   未观测到反例；无 R-SKIP/R-RETRY/R-COUNT 命中
```

任一不满足 → `UNVERIFIED`（若 signed 为 PASS 则 `R-VERDICT`，exit 1）。

### 4.4 独立等级 I0–I3 与用途

| 级 | 条件 | 允许的结论 |
|---|---|---|
| I0 | 只有叙述/verdict | 无（只能当导航） |
| I1 | 1 个主证据单元，或绑定不全 | UNVERIFIED |
| I2 | P1–P6 全满足，`indep ≥ 2` **或** `cross_derivation` | 产品域（A–I）PASS |
| I3 | I2 + 第二单元来自**不同环境或不同 run** + 有 MUT/MR + 对抗复核留有反例尝试 | 发版域（J）/GATE 的 PASS；域签字 |

**本批上限 = I3**。T4 的外部锚（远端 commit、CI run id、Play Console）在本批不可得（不 push、无签名、无真实 Provider），因此 J 域的 PASS 只能到 I2，最终发版结论维持 BLOCKED——与批次 43 §9.7 一致。

补充一条硬后果：J/GATE 的 signed PASS 还要求 `machine_verdict = PASS_HEAD`（干净树、targets 与 HEAD 一致）。批次 44 基线记录的工作树为脏（§1.2：`-uall` 3753 条、102 个 M，且同一 ask 内两次读取相差 +6 条；本轮未重测），因此**在该状态下 J 域不可能产出 PASS**；只能 PASS_WORKTREE（A–I 可用）或 BLOCKED/UNVERIFIED。这不是模型缺陷，而是批次 43“6 份 APK、无一份绑最终树”教训的直接延续。

---

## 5. ledger 行 schema

### 5.1 单一权威源：`ledger.jsonl` + 生成的 `ledger.md`

计划 D0 item 1 写的是 `docs/acceptance/ledger.md`（`plan:18`）。本模型建议：**`docs/acceptance/ledger.jsonl` 是唯一权威源，`ledger.md` 由脚本生成，首行固定写“由 ledger.jsonl 生成，勿手改”**。

- 消灭的失败：两份手写台账必然漂移（批次 43 记录与盘上相反的两条陈述，`R-DISK`；批次 42 计划项数 74/78/90 三处不一致）。一份机器源 + 一份生成视图，漂移在生成时立刻可见。
- 若主会话坚持只留 `.md`：则 `.md` 必须用固定列、单元格内禁止换行/`|`，`lint` 逐行解析；代价是人工编辑更容易破坏 schema。
- 目录未被忽略（本轮 `git check-ignore -q docs/acceptance/ledger.md` → trackable），新建即可入仓。

### 5.2 行字段

| 字段 | 类型 | 必填 | 含义 / 约束 | 消灭的失败 |
|---|---|---|---|---|
| `schema` | str | 是 | 固定 `smbk.acceptance.ledger/1` | 结构漂移 |
| `id` | str | 是 | 域前缀稳定 ID，如 `B-061`；全局唯一；不得复用 | 批次 41 编号错位 |
| `domain` | enum | 是 | `D0/A/B/C/D/E/F/G/H/I/FIX/GATE` | 配额不可数（F1） |
| `requirement.source` | enum | 是 | `plan/contract/admission/ruling` | “需求凭印象” |
| `requirement.ref` | str | 是 | `path:line`，必须能定位 | 悬空引用（`plan:34`“见验收台账”） |
| `requirement.text_sha256` | hex64 | 是 | 该需求文本的哈希；变更即失效 | 改需求迁就实现 |
| `requirement.immutable` | bool | 是 | 6C-2 类为 true；禁改 | 重裁（批次 42 WS6） |
| `claim` | str | 是 | **单一可观测断言**，一句；禁“和/或”并列 | 一行藏多个断言 |
| `claim_sha256` | hex64 | 是 | verdict 绑定用 | 事后改 claim |
| `check.kind` | enum | 是 | `test/device/static/metamorphic/identity` | 判据类型混淆 |
| `check.cmd` | array | 是 | argv token 列表（禁散文） | 命令不可复现（42 `S3`） |
| `check.cwd` | str | 是 | 仓库相对路径 | 复跑歧义 |
| `check.targets` | array | 是 | 受该断言约束的源文件 | 旧树冒充（批次 40） |
| `check.expected_cases` | int | 测试类必填 | 期望执行例数（与 XML 对账） | `-4 ×15` 仍判绿（43） |
| `check.expected_observable` | str | 是 | 期望观测（计数/阈值/关系） | “跑过了”式判据 |
| `evidence[]` | array | 是 | 每项：`eid/class/run_id/ref/sha256` | 无落点 |
| `falsifiability` | obj | 是 | `mutation_ref/mr_ref/embedded_negative_control/kind` | 断言空转（42） |
| `binding` | obj | 是 | `head/targets_sha256/dirty[]/device/apk` | 跨 APK 合并（43） |
| `review.required` | bool | 是 | 域门是否要求复核 | 自证 |
| `residual` | array | 否 | 具名残余，如 `red_phase_not_demonstrated` | 残余被吞进 PASS |
| `notes` | str | 否 | 不得含 PASS/FAIL 判词 | 叙述污染 |

### 5.3 行不变量

- **I1** 十六进制字段必须是 64 位小写；占位符（`…`/`TBD`/`<sha>`）出现即 `R-SCHEMA` exit 1。
- **I2** `id` 唯一；`domain` 配额精确：D0=100、A–J 各 90、FIX=100、GATE=100，总 1200（`plan:80`）。
- **I3** `claim` 单句、单观测；出现“并且/以及/、”连接两个可独立失败的观测 → `R-ATOMIC`。
- **I4** 每个 `evidence.ref` 必须存在、非忽略路径、能被 `git check-ignore` 判为 trackable。
- **I5** `evidence[].eid` 在全局唯一；同一 `(ref, sha256)` 不得同时充当两条不同 claim 的独立单元（`R-DUP`）。
- **I6** `binding.targets_sha256` 必须覆盖 `check.targets` 全部文件；报告按当前内容重算。
- **I7** `expected_cases` 与 XML 的 `tests - skipped` 对账。
- **I8** `falsifiability` 四选一非空；`mr_ref` 指向的登记项 `preregistered_at` 必须早于首端 run。
- **I9** `residual` 非空的行不得 signed PASS（除非 residual 已由独立行承担）。
- **I10** `notes` 不含判词（PASS/FAIL/通过/绿）；判词只能出现在 verdict 文件。

### 5.4 示例行（6C-2，取自本仓真实断言）

```json
{
  "schema": "smbk.acceptance.ledger/1",
  "id": "B-061",
  "domain": "B",
  "requirement": {
    "source": "ruling",
    "ref": "docs/research/2026-11-04-6c2-rulings.md:5",
    "text_sha256": "0000000000000000000000000000000000000000000000000000000000000000",
    "immutable": true
  },
  "claim": "域层与数据层的允许 mask 集合都恰好等于 {9,13,15}，且 0/8 在两层都被拒绝",
  "claim_sha256": "0000000000000000000000000000000000000000000000000000000000000000",
  "check": {
    "kind": "test",
    "cmd": ["./gradlew", ":core:data:testDebugUnitTest", "--tests", "*SixC2RulingInvariantTest*", "--offline", "--console=plain"],
    "cwd": ".",
    "targets": ["core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupSelection.kt",
                "core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/backup/SixC2RulingInvariantTest.kt"],
    "expected_cases": 3,
    "expected_observable": "tests=3 failures=0 errors=0 skipped=0；用例内含 0/8 拒绝与 9/13/15 可构造的正负对照"
  },
  "evidence": [
    {"eid": "E-44-B-061-X", "class": "XML", "run_id": "run-b061-01",
     "ref": "docs/acceptance/runs/run-b061-01/TEST-...SixC2RulingInvariantTest.xml",
     "sha256": "0000000000000000000000000000000000000000000000000000000000000000"},
    {"eid": "E-44-B-061-C", "class": "CMD", "run_id": "run-b061-01",
     "ref": "docs/acceptance/runs/run-b061-01/manifest.json",
     "sha256": "0000000000000000000000000000000000000000000000000000000000000000"}
  ],
  "falsifiability": {"mutation_ref": null, "mr_ref": null, "embedded_negative_control": true, "kind": "embedded"},
  "binding": {
    "head": "0000000000000000000000000000000000000000",
    "targets_sha256": {"core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupSelection.kt": "0000…"},
    "dirty": []
  },
  "review": {"required": true},
  "residual": ["red_phase_not_demonstrated: 改热区行为被禁，红相无法演示"],
  "notes": "行为面 ALLOWED_MASKS=linkedSetOf(9,13,15)（BackupSelection.kt:166 实读）；本行只加断言，不改行为"
}
```

（示例中 `0000…` 是占位；真实行按 I1 必须 64 位真实哈希。示例行本身**不得**进 ledger。）

**这一行的判定**：XML+CMD 同 run → `indep=1`，但 `(XML 计数, CMD 退出码)` 属允许的 `cross_derivation` → I2；`embedded_negative_control` 有落点（3 个用例含拒绝与可构造两类）→ P6 成立；`residual` 非空但已具名，claim 本身不含“红相已演练” → 可 signed PASS，残余单独可审。这正是 6C-2 现状（`2026-11-04-6c2-rulings.md:44` 自述“红相未演示”）的诚实表达。

---

## 6. verdict 文件与 run manifest

### 6.1 `docs/acceptance/verdicts/<id>.json`

```json
{
  "verdictSchemaVersion": 1,
  "id": "B-061",
  "claim_sha256": "…",
  "machine_verdict": "PASS_HEAD",
  "verdict": "PASS",
  "evidence": ["E-44-B-061-X", "E-44-B-061-C"],
  "reviewers": [
    {"id": "agent:reviewer-b@run-r7", "role": "adversarial", "model": "…",
     "independent_of_producer": true, "verdict": "NO_COUNTEREXAMPLE",
     "counterexample_attempt": {"kind": "mutation", "target": "SmbkSelectionPlan.allowedMasks",
                                "result": "mutation_would_fail: widened to {9,13,15,8} → 用例 1 红"}}
  ],
  "run_id": "run-b061-01",
  "decided_at_utc": "2026-11-06T00:00:00Z",
  "decided_by": {"kind": "main-session", "agent_id": "script:acceptance_report.py@sha256:0000…"},
  "overrides": [],
  "supersedes": null
}
```

规则：

- `reviewers` **不计入证据单元**；它的作用只有两个：提供 `counterexample_attempt`（域门要求），以及在 `overrides` 里解释“为什么签字比机器更严”。
- `reviewer.id ≠ producer.id`，且 `run_id` 不同；同模型多 agent 视为**相关**，只算一次（`R-REVIEW`）。本仓已有“自动化裁决者必须给脚本哈希”的先例：`rulings/…json` 的 `decidedBy.agentId = "script:check_split_gate.py@sha256:c8a44c…"`。
- `claim_sha256` 与 ledger 行一致；不一致 = 该 verdict 针对的是另一条断言（`R-REQ-DRIFT`）。
- 追加式更正：新的 verdict 文件用 `supersedes` 指向旧 id，旧文件不覆盖（沿用 `2026-10-26-device-evidence-convention.md` §7.6 的追加式纪律）。

### 6.2 `run_manifest.json`（执行封条）

每次执行产出一个 manifest，与产物同目录：

| 字段 | 含义 |
|---|---|
| `run_id` | 唯一（时间戳 + 随机后缀），同一 run 的所有产物共用 |
| `argv` / `cwd` / `tool{path,sha256}` | 可复跑的最小命令与工具身份 |
| `head` / `dirty[]` | 运行时的 HEAD 与该次 targets 的脏路径（共享工作树必须记） |
| `targets{path:sha256}` | 运行前对受测文件的哈希 |
| `environment` | `host-jvm` / `device:<serial>@<boot_id>` / `pdf-reader:<name>` |
| `started_at` / `ended_at` | 两端时间；仅作辅助，不作判据 |
| `exit_code` | **整数**；缺此字段即 `R-EXIT` |
| `artifacts[{path,sha256,bytes}]` | XML/日志/截图指纹等 |

**它消灭的具体失败**：批次 42 “6 组 exit 0 但无 exit code 行”（`:418`）、批次 43 “测试 XML 被覆盖、逐例不可回读”（`:468`）、批次 40 “旧 APK 当当前树”（`:67`）。封条把“散落文件”变成“一次可核对的执行”。

**新增指认**：若主会话认为新增 `tools/ci/run_evidence.py`（包一层执行并写 manifest）超出 D0 的 10 项，则替代方案是每条证据手工填 manifest 字段——成本更高、更易错，且仍然需要同一套 schema。建议作为 D0 增项，单独提交。

---

## 7. `acceptance_report.py` 判定规则

### 7.1 输入 / 输出

- 输入：`ledger.jsonl`、`verdicts/*.json`、各 `run_manifest.json` 与其产物、`evidence.sha256` 清单。
- 先跑 `acceptance_lint.py`（静态：schema/配额/引用/denylist/原子性），再跑本报告（动态：重算/独立性/比对）。
- 输出：`build/acceptance/report.json`（机器）+ 控制台摘要（人读）；**报告自身不是证据**，它只引用与被引用。
- 安全：解析任何 XML 前**拒绝 `<!DOCTYPE`/`<!ENTITY`**（本仓既有安全约束；`tools/ci` 的 XML 解析一律先做字节级预检），不启用外部实体。

### 7.2 算法（确定性，fail-closed）

```
1  lint(ledger, verdicts) → 违规即 exit 1（结构类），否则继续
2  对每行 row:
3    校验 evidence 引用/哈希/忽略规则 → 失败则 REJECTED
4    解析 XML，重算 tests/skipped/failures/errors → 与声明不符则 REJECTED
5    重算 binding.targets_sha256 → 不符则 STALE
6    重算 independence（class/run/producer/environment）→ I 级
7    判 P1..P7 → PASS_HEAD / PASS_WORKTREE / UNVERIFIED / FAIL / BLOCKED
8    读 verdict → 按 7.4 比对；宽松方向记违规
9  汇总：配额、域门（每域 ≥1 对抗反例）、GATE 行
10 任何违规 → exit 1
11 否则若有 UNVERIFIED/BLOCKED/缺行/缺签字 → exit 2
12 否则 exit 0
```

### 7.3 退出码（不得合并语义）

| 码 | 含义 | CI 如何用 |
|---|---|---|
| 0 | 1200 行齐、签字与机器一致、无违规 | 唯一可读作“本批验收完成” |
| 1 | **完整性违规**：假绿、无效证据、PASS 无据、宽松签字、跳过计通过、summary 当证据、哈希不符、跨 APK 合并 | 必须修，不得重试洗绿 |
| 2 | **未完成**：缺行/缺签字/UNVERIFIED/BLOCKED，但无违规 | 不得读作通过；继续做或走 BLOCKED runbook |
| 3 | 工具/IO/schema 错误（ledger 无法解析等） | 基础设施问题，不产出判定 |

这与仓内 `summarize_kd76` 的 `exit 2 = NOT EVALUABLE` 口径一致（`tools/tests/test_summarize_kd76.py:1-16`），避免“0/非0”二值把未完成读成通过。

### 7.4 signed verdict 与 machine_verdict 的比对

| signed \ machine | PASS_HEAD | PASS_WORKTREE | UNVERIFIED | FAIL | BLOCKED |
|---|---|---|---|---|---|
| PASS | 允许 | 允许**仅 A–I 域**（J/GATE → `R-DIRTY` exit 1） | `R-VERDICT` exit 1 | `R-CONTRADICT` exit 1 | `R-VERDICT` exit 1 |
| FAIL | 允许（保守）但须 `override_reason` | 同左 | 允许（更严） | 允许 | 允许（更严） |
| UNVERIFIED | 允许（更严） | 允许 | 允许 | 允许 | 允许 |
| BLOCKED | 允许（更严） | 允许 | 允许 | 允许 | 允许**仅当** attempt manifest 存在 |

“更严”永远允许；“更宽松”永远 exit 1。这一条把“主会话签字”从权威降为**可被机器否证的输入**。

### 7.5 规则表（每条都对应一条已发生的失败）

| 规则 | 触发 | 动作 / 退出 | 消灭的失败（批次） |
|---|---|---|---|
| `R-SCHEMA` | 字段缺失/类型错/占位哈希/未知枚举 | exit 3 | 台账从未存在（44 基线 F1） |
| `R-QUOTA` | 行数/ID/域配额 ≠ 100+900+100+100 | exit 2 | “1200 项”无枚举（F1） |
| `R-ATOMIC` | claim 含并列断言 | exit 2（要求拆行） | 一行藏残余 |
| `R-SUMMARY` | `ref` 命中 `*record*.md`/`*index*.md`/`*summary*`/`docs/status.md`，或无 manifest | exit 1 | 40 集成轨 summary 虚报；计划 D0 item 4 |
| `R-REF` | ref 不存在/不可读/被 gitignore | exit 1 | 证据放 `build/`（KD-74） |
| `R-REQ-DRIFT` | `requirement.text_sha256`/`claim_sha256` 不符 | exit 1 | 改需求迁就实现（42 WS6） |
| `R-EXIT` | CMD 无整数退出码，或 exit≠0 而 signed PASS | exit 1 | 42 “6 组 exit 0”无 exit 行 |
| `R-REPRO` | `argv` 非 token 列表 / 路径 token 不存在 | exit 1 | 42 配对命令复跑 `TRUE_EXIT=2` |
| `R-XMLCOUNT` | 声明计数 ≠ 重算；XML 畸形；含 DOCTYPE/ENTITY | exit 1 | 41 “缺方法”仍 IDENTICAL |
| `R-METHOD` | 配对/比较类检查未先校验两臂方法全集相等 | exit 1 | 41 F7：一臂少跑仍判 `PAIRED_RED_SET_IDENTICAL` |
| `R-SKIP` | `skipped>0` 计入通过；`tests-skipped < expected_cases` | exit 1 | 43 `OK(n)` 并列 `-4 ×15` |
| `R-ART` | 缺 APK sha/serial/boot_id；跨 run 合并产物 | exit 1 | 40 旧 APK；41 控制标记只比源码；43 六 APK 合并 |
| `R-STALE` | targets 哈希与当前文件不符且无 supersedes | UNVERIFIED（exit 2） | 40 旧树；43 XML 被覆盖 |
| `R-DIRTY` | J/GATE 行 signed PASS 但 `head≠HEAD` 或 targets 脏 | exit 1 | 43 “无一份绑最终树” |
| `R-COUNT` | 随机/时序检查未声明 N，或丢弃空轮/失败轮 | exit 1 | 41 空 sha 整轮剔除 |
| `R-RETRY` | 首次红、后续绿，PASS 只引绿 | exit 1 | Google 2017“重跑可能变绿” |
| `R-PARAM` | 阈值/样本量来自 ledger 或 CLI 覆盖 | exit 1 | 41 `requiredSamples/gateMillis` 可覆盖 |
| `R-TRACK` | 同检查两轨结论相反且无 resolution | exit 1 | 43 p09 两轨相反 |
| `R-GATE` | 未指名门日志，或引用了同族非门日志 | exit 1 | 43 两份相反单测日志 |
| `R-DISK` | 声称“文件不存在/未跑”但盘上相反 | exit 1 | 43 “无 strong-postfix 日志”实为在盘 |
| `R-UNIT` | units<2 或 classes<2 而 signed PASS | `R-VERDICT` exit 1 | 单证据判绿 |
| `R-IND` | indep<2 且无 cross_derivation 而 signed PASS | exit 1 | 40 单臂绿计总数 |
| `R-VERDICT` | signed 比 machine 宽松 | exit 1 | “证据不足也签 PASS” |
| `R-CONTRADICT` | 观测到反例仍 signed PASS | exit 1 | 40 集成 summary vs 实读 |
| `R-BLOCK` | BLOCKED 无 attempt manifest / blocked_by / runbook | 降级 UNVERIFIED（exit 2） | 把“没做”写成阻塞 |
| `R-UNVERIFIED` | signed UNVERIFIED 但 claim/notes 含通过判词 | exit 1 | 计划 D0 item 4 |
| `R-FALSIFY` | 行为类断言无 MUT/MR/内嵌负向对照 | 封顶 I1（exit 2） | 42 修复即绿未演练 |
| `R-REVIEW` | reviewer=producer / 同模型同 run / 无 counterexample_attempt | 该复核不计；域门未过 → exit 2 | 自证 |
| `R-MR-PRE` | MR 登记晚于首端 run | exit 1 | 事后挑关系 |
| `R-IMG` | 只有哈希无指纹/无阈值重算 | 不可单独成证（exit 2） | “截图哈希=视觉通过” |
| `R-DUP` | 同一 `(ref,sha256)` 充当两条 claim 的独立单元 | exit 1 | 一证多用 |

### 7.6 lint 与 report 的分工

| 工具 | 只做什么 | 不做什么 | 退出 |
|---|---|---|---|
| `acceptance_lint.py` | 静态：schema、配额、ID 唯一、ref 存在/忽略、denylist、原子性、阈值来源、规则覆盖 | 不执行命令、不解析 XML 计数、不算独立性 | 1 / 3 |
| `acceptance_report.py` | 动态：重算哈希/计数/独立性、比对 verdict、汇总域门 | 不改证据、不重跑测试（重跑由门禁阶段负责） | 0 / 1 / 2 / 3 |

两者都不可“自动修”：修 = 重新产证据或改 verdict，必须留痕。

---

## 8. 回放夹具与自证

### 8.1 夹具 schema

```json
{"case_id": "fg-40-01", "source": "2026-11-02-batch-40-record.md:67",
 "claim": "schema 73 设备全绿", "shape": "artifact_mismatch",
 "evidence": [{"class": "DEV", "apk_sha256": "…", "tree_sha256": "…不同…"}],
 "expected_machine_verdict": "UNVERIFIED", "expected_rule": "R-ART"}
```

夹具内容可为**合成**（历史证据是散文），但**失败形状必须来自真实记录**；每条断言 `expected_rule` 命中且整体 exit 1。

### 8.2 15 条历史假绿 → 规则映射（本轮 `awk` 实数 15 行，来源：批次 44 基线 §6.1）

| # | 批 | 假绿形态 | 规则 | 期望 |
|---|---|---|---|---|
| 1 | 40 | 旧 APK 当当前树（93 表/`aa9f0313` vs 92 表/`1d086050`） | `R-ART`+`R-STALE` | UNVERIFIED |
| 2 | 40 | 单臂绿计入总数（27 绿） | `R-SKIP`+`R-IND` | UNVERIFIED |
| 3 | 40 | 集成 summary 虚报 93 表 | `R-SUMMARY` | REJECTED |
| 4 | 41 | 缺方法仍判 `PAIRED_RED_SET_IDENTICAL` | `R-METHOD`+`R-SKIP` | UNVERIFIED |
| 5 | 41 | 控制标记只比源码不比安装包 | `R-ART` | UNVERIFIED |
| 6 | 41 | 空 sha 整轮剔除冒充 n=20 | `R-COUNT` | UNVERIFIED |
| 7 | 41 | 阈值可覆盖 / 旧入口 | `R-PARAM` | FAIL |
| 8 | 42 | 修复即绿、运行时未演练 | `R-FALSIFY` | UNVERIFIED |
| 9 | 42 | 无 exit code 的“6 组 exit 0” | `R-EXIT` | REJECTED |
| 10 | 42 | 命令行不可复现（`TRUE_EXIT=2`） | `R-REPRO` | REJECTED |
| 11 | 43 | `OK(n)` 与 `-4 ×15` 并列 | `R-SKIP` | UNVERIFIED |
| 12 | 43 | 6 份 APK 跨轨合并 | `R-ART` | FAIL |
| 13 | 43 | 两份同族单测日志结论相反 | `R-GATE` | FAIL |
| 14 | 43 | p09 两轨相反、单边引用 | `R-TRACK` | FAIL |
| 15 | 43 | “文件不存在”与盘上相反 | `R-DISK` | FAIL |

### 8.3 报告自身的 5 条自突变（证明它会红）

| # | 对一条本已 PASS 的行做 | 期望 |
|---|---|---|
| M1 | 删掉 1 个 evidence 单元 | 降 I1 → UNVERIFIED，signed PASS → exit 1 |
| M2 | manifest `exit_code` 0→1，其余不动 | `R-EXIT`/`R-CONTRADICT` exit 1 |
| M3 | 改一个 unit 的 `apk_sha256` | `R-ART` exit 1 |
| M4 | XML 声明 `skipped=2`，不改节点 | `R-XMLCOUNT` exit 1 |
| M5 | 改 `requirement.ref` 指向的文本 | `R-REQ-DRIFT` exit 1 |

回放与自突变都属“工具自证”，必须在批次 44 门禁阶段实跑；本文件只给设计与断言。

---

## 9. 十域证据配对（900 项怎么落）

| 域 | 主单元（确认） | 佐证单元（独立） | 可证伪落点 | 不得替代的残余 |
|---|---|---|---|---|
| A 旅程 | XML（device，含预期例数） | DEV（uiautomator 语义树/截图指纹，另 run） | MUT：断言删掉后用例红 | 真机手感/动画流畅度 |
| B 数据 | XML（JVM 迁移/备份） | STATE（`user_version`+表数+identity sha） | MR：backup→restore→再导出字节/文本一致 | 设备侧 72 版本矩阵需 device XML |
| C 视觉 | IMG（指纹+像素差） | MR（平移/交换/透明变换） | MR 预注册 | 审美“好不好看” |
| D 模型 | XML（schema/golden） | CMD（fixture 回放 exit） + MUT（畸形输入拒绝） | 每个解析失败分类一条 MUT | 真实 Provider（BLOCKED） |
| E 复习 | XML（FSRS 不变量） | STATE（排程表 dump） | MR：+1 天单调性 | 长期真实学习效果 |
| F 性能 | CMD（N 次 P50/P90） | DEV（`dumpsys meminfo/gfxinfo`） | `R-COUNT` 全量报告 | 真机/arm64（BLOCKED） |
| G 安全 | CMD（扫描 exit+计数） | HASH（产物清单）+ STATE | MUT：注入一条已知坏样本必报 | 完整 CVE 库（需网络） |
| H 合规 | CMD（字符串/元数据扫描） | IMG（导出页指纹） | MR：每个产物都含标识 | 显式标识的“可读性”人工判断 |
| I 无障碍 | XML（`enableAccessibilityChecks`） | DEV（语义树 dump） | MUT：去掉 contentDescription 必红 | TalkBack 语感（UNVERIFIED） |
| J 发布 | CMD（assemble/AAB/apksigner） | STATE（AAB zip 清单、`p_align`） | MUT：删一个 `.so` 必红 | 生产签名/arm64（BLOCKED） |
| D0 | XML（lint/report 自测） | MUT（15 条假绿全红） | M1–M5 | — |
| GATE | 每域签字 verdict | `report.json` exit 0 | 每域 ≥1 对抗反例 | 主会话终裁 |

**配额提醒**：计划 §2 B 的 90 项被“迁移 72 + 备份 18”刚好用满，第三类“并发/CAS/事务/恢复”没有预算（`plan:42-44`；基线 F8 `:284`）。枚举台账时必须显式给并发类分配编号，或把 B 改为 72+18+并发并登记总量变化；否则 `R-QUOTA` 会把它算成缺行。

---

## 10. 外部依据：采纳了什么、拒绝了什么

### 10.1 ISO/IEC/IEEE 29119

| 来源 | 取到的内容 | 采纳为 | 不能支撑 |
|---|---|---|---|
| IEEE SA 29119-3-2021 页（已取正文） | Part 3 “includes templates and examples of test documentation”；模板“arranged within clauses reflecting the overall test process description structure”，与 Part 2 对齐；Annex A 给内容与映射 | 文档分层：预期 vs 实际、执行记录、完成报告分别落文件 | **没有**“≥2 类独立证据”或任何独立性数值门槛 |
| ISO 目录页（WebFetch 403；仅搜索摘要） | “specifies software test documentation templates … output of the processes specified in 29119-2” | 同上（旁证） | 条款原文（付费标准，未读） |
| microTOOL 2021 文章（仅搜索摘要，单一二手源） | 文档类型列表含 test status report / test completion report / test case specification / test procedure specification / actual results / test results / test execution log / incident report 等 | 我们的映射命名 | **单一来源，标“待验证”**；不引条款号 |

**映射（我们的产物 → 29119-3 文档角色）**：

| 本仓产物 | 29119-3 角色 |
|---|---|
| ledger 行（claim + expected） | test case specification 的“预期结果” |
| run manifest + 产物 | test execution log / actual results |
| evidence 单元 | test results 的证据附件 |
| verdict 文件 | test completion report 的逐项条目 |
| BLOCKED 行 + runbook | incident report（阻塞类） |

**明确声明**：29119 给的是**文档结构与过程输出**，不是证据独立性算法。§4 的 I0–I3 与 §7 的规则是本仓设计，不冒充标准要求。

### 10.2 Google Testing Blog

| 来源 | 取到的内容（已取正文） | 采纳为 |
|---|---|---|
| “Where do our flaky tests come from?”（2017-04-17） | “Flaky tests are tests that exhibit both a passing and a failing result with the same code”；“trying to recreate the failure, by rerunning the test with the same version of code, may or may not result in a passing test”；“the larger the test … the more likely it is to be flaky”；表中 Android emulator 25.46% flaky vs 全体 1.65% | `R-RETRY`（禁重试洗绿）、`R-COUNT`（多 run 全量报告）、设备类必须多 run |
| 2016 / 2020 / 2021 三篇（仅搜索摘要） | 主题为 flaky 的成因与治理 | 仅作背景，不支撑任何硬规则（标“摘要级”） |

**不采纳**：把“多跑几次直到绿”当稳定性策略。Google 原文指出重跑结果不确定；本仓规则是“全量报告 + 预注册 N”，不是“取最好一次”。

### 10.3 Android 官方测试文档

| 来源 | 取到的内容 | 采纳为 |
|---|---|---|
| Testing fundamentals（已取正文） | instrumented 测试在设备上跑，“app is built and installed alongside a *test app*”；local 测试在开发机；manual testing “scales poorly”；不可测架构产生“Bigger, slower, more flaky tests” | host vs device 作为**环境独立轴**；小测试优先 |
| Build instrumented tests（已取正文） | “Instrumented tests run on Android devices … more fidelity than local tests, though they run much slower”；只在必须验证真实设备行为时用 | 设备证据的定位（高保真、低吞吐） |
| Screenshot testing（搜索摘要）+ `ScreenshotTestSuite` API（搜索摘要） | “comparing current UI screenshots with previously approved reference images”；阈值 0.0–1.0，`0.0001f` = 0.01% | 截图判据用**比例阈值 + 参考图**，不用哈希相等 |
| Compose accessibility testing（搜索摘要） | “An essential way of testing accessibility is a form of manual testing: by turning accessibility services, like TalkBack … on”；“In conjunction with manual verification, you should also use automated testing to flag any po[ssible issues]” | 自动检查只能标“可能的无障碍问题”；TalkBack 语感 = UNVERIFIED |
| AOSP instrumentation（搜索摘要） | `am instrument` 提供特殊测试执行环境，目标应用进程被重启并以基本 context 初始化 | 设备 run 是独立执行环境，可作独立轴 |
| “Big test stability”（页面在官方导航中存在，正文抓取两次超时） | 未取到正文 | **不采纳任何具体说法**；标 UNVERIFIED（§12） |

### 10.4 蜕变测试

| 来源 | 取到的内容 | 采纳为 |
|---|---|---|
| arXiv 2310.00338（Duque-Torres & Pfahl，已取摘要） | MT 通过检查输入-输出对之间的关系来应对 oracle 问题；MR 描述“specific input changes”导致的预期输出变化 | §3.5 的 MR 定义与登记制 |
| arXiv 2412.20692（Fu, Sun, Zhang, Liu，已取摘要） | MR 在 source/follow-up 输入及其输出间成立；提出 adequacy 判据 | MR 覆盖度（每域至少 1 条已演练关系） |

---

## 11. 残余、BLOCKED、UNVERIFIED 政策

1. **BLOCKED 必须“尝试过”**：`attempt_manifest` 里要有真实失败输出（如批次 44 基线 §4.2 的 `FATAL … 'arm64' is not supported … on x86_64 host`），并给 `blocked_by` 与 runbook 路径。没有尝试 = UNVERIFIED。
2. **UNVERIFIED 不得出现在 PASS 的支撑链里**：一行一个断言；有残余就单开一行或写 `residual`，且该行不得 signed PASS 携带未声明的残余（I9）。
3. **审美/语感/可读性**：C（视觉好不好看）、H（显式标识是否易读）、I（TalkBack 播报是否自然）标 UNVERIFIED，由人另行判断；自动化只能证明“属性存在/阈值达标”。
4. **本批硬阻塞（维持批次 43 §9.7）**：真实 Provider、生产签名、arm64 16KB、人工金标（AUD-08）→ BLOCKED + runbook，不因本模型而改变。
5. **6C-2**：`mask=8`/`mask=0`/派生索引三项不可变；只能加不变量测试。其“红相未演示”是**具名残余**（`2026-11-04-6c2-rulings.md:44`），不得写成“已演练”。

---

## 12. 未验证与开放问题（不得当事实）

| # | 项 | 状态 | 为什么 / 下一步 |
|---|---|---|---|
| U1 | 同机重复 `adb exec-out screencap` 的字节稳定性 | **UNVERIFIED** | 本轮未跑设备；因此禁止用 PNG sha256 相等作判据（保守选择） |
| U2 | `adb` 传输本身能否被本地伪造（T4） | **UNVERIFIED** | 未做对抗实验；故设备类只到 I2 |
| U3 | 同模型多 agent 复核的相关性 | **UNVERIFIED** | 无实验；`R-REVIEW` 按“相关”保守处理 |
| U4 | ISO 29119-3 条款原文与模板精确字段 | **未取到**（付费标准） | 只采纳二手摘要；不入规则 |
| U5 | Android “Big test stability” 正文 | **未取到**（两次超时） | 不采纳其任何具体建议 |
| U6 | 2016/2020/2021 Google 博文正文 | **摘要级** | 只作背景 |
| U7 | 15 条假绿夹具的“合成证据”是否覆盖全部规则 | **未执行** | 需在批次 44 门禁阶段实跑回放；本轮只给映射 |
| U8 | 报告 exit 2 与“本批验收完成”的边界 | 设计判断 | 需主会话确认：是否允许“无违规但未完成”在 CI 里非 0（本模型要求非 0） |

---

## 13. 来源（URL + 采纳/拒绝理由）

| # | URL | 取用方式 | 采纳 / 拒绝 |
|---|---|---|---|
| S1 | https://standards.ieee.org/standard/29119-3-2021.html | 已取正文 | 采纳：文档模板与 Part 2 对齐、Annex 结构；**不**采纳“独立性要求”（原文无） |
| S2 | https://www.iso.org/standard/79429.html | WebFetch 403；仅搜索摘要 | 旁证采纳；条款原文未读 |
| S3 | https://www.microtool.de/en/document-management/test-documentation-with-iso-iec-ieee-29119-32021/ | 仅搜索摘要 | 文档类型列表采纳为命名参考，标“单一来源待验证” |
| S4 | https://testing.googleblog.com/2017/04/where-do-our-flaky-tests-come-from.html | 已取正文 | 采纳：flaky 定义、重跑不确定、规模与 flaky 相关、emulator 25.46% |
| S5 | https://testing.googleblog.com/2016/05/flaky-tests-at-google-and-how-we.html | 仅搜索摘要 | 背景，不支撑规则 |
| S6 | https://testing.googleblog.com/2020/12/test-flakiness-one-of-main-challenges.html | 仅搜索摘要 | 背景，不支撑规则 |
| S7 | https://developer.android.com/training/testing/fundamentals | 已取正文（extract） | 采纳：instrumented vs local、test app 并装、manual 不可扩 |
| S8 | https://developer.android.com/training/testing/instrumented-tests | 已取正文（extract） | 采纳：设备高保真/低吞吐、只在必须时用 |
| S9 | https://developer.android.com/training/testing/ui-tests/screenshot | 搜索摘要（WebFetch/extract 超时） | 采纳定义与“参考图比较”；阈值细节来自 S10 |
| S10 | https://developer.android.com/reference/tools/gradle-api/9.5/com/android/build/api/dsl/ScreenshotTestSuite | 搜索摘要 | 采纳阈值语义（0.0–1.0；0.0001=0.01%） |
| S11 | https://developer.android.com/develop/ui/compose/accessibility/testing | 搜索摘要 | 采纳：TalkBack 人工不可替代、自动检查只标“可能” |
| S12 | https://source.android.com/docs/core/tests/development/instrumentation | 搜索摘要 | 采纳：`am instrument` 重启目标进程、特殊执行环境 |
| S13 | https://arxiv.org/abs/2310.00338 | 已取摘要 | 采纳：MT/oracle 问题、MR 定义 |
| S14 | https://arxiv.org/abs/2412.20692 | 已取摘要 | 采纳：MR 覆盖度/adequacy |
| S15 | https://developer.android.com/training/testing/instrumented-tests/stability | 页面存在，正文两次抓取超时 | **拒绝作为依据**（U5） |
| S16 | Paparazzi / Roborazzi / Compose Preview Screenshot Testing 文档 | 未取（计划 §6 已定“不引新依赖”） | 本批拒绝：供应链复核成本 > 收益；用 adb 截图 + 仓内像素工具 |
| S17 | 重试至绿的 CI 实践类文章 | 未取 | 拒绝：与 S4 冲突 |

---

## 14. 本轮实跑 / 实读清单（可复核）

**实读（仓内）**

- `.zcode/plans/plan-2026-11-05-batch-44-acceptance.md:18-34,80,93-95`（D0 交付物、计数、验收）。
- `docs/research/2026-11-06-batch-44-baseline.md:14,203-218,222-258,277-279`（F1/F2/F3、§6.1 十五行、§6.2 夹具格式）。
- `docs/research/2026-11-05-batch-43-record.md:468-492,540-544`（§6.3 缺口、§9.1 门禁表）。
- `docs/research/2026-10-26-device-evidence-convention.md:1-40`（`.md` only、预算、E-ID、追加式）。
- `core/domain/.../BackupSelection.kt:166`（`ALLOWED_MASKS = linkedSetOf(9,13,15)`）。
- `docs/research/2026-11-04-6c2-rulings.md:5-11,28-44`（三项裁定、3 例不变量、XML 3/0/0、红相未演示）。
- `docs/research/evidence/2026-11-05-batch-43/rulings/20261103T213902Z-c0dc8d30.json`（`decidedBy.agentId=script@sha256`、`evidenceGradeLevel`、阈值结构）。
- `app/build/test-results/testLocalFirstDebugUnitTest/TEST-…BackgroundWriterQuiesceTest.xml`（JVM XML 属性）。
- `app/build/outputs/androidTest-results/connected/debug/flavors/localFirst/TEST-audit_user_visible_20261016(AVD) - 14-_app-localFirst.xml`（设备 XML 只有 device/flavor/project）。
- `tools/ci/generate_status.py:1-30`（NOT_MEASURED 纪律）、`tools/tests/test_summarize_kd76.py:1-16`（exit 2 = NOT EVALUABLE）。

**实跑（命令与结果）**

```bash
# 15 条历史假绿实数
awk '/^## 六、40\/41\/42\/43 已知假绿清单/,/^## 七/' docs/research/2026-11-06-batch-44-baseline.md | grep -c "^| 4[0-3] |"   # → 15

# 证据路径是否可入仓
git check-ignore -q docs/acceptance/ledger.md            # exit 1 → trackable
git check-ignore -q docs/acceptance/evidence/run1.log    # exit 0 → IGNORED (.gitignore:31)
git check-ignore -q build/batch44-unit.log               # exit 0 → IGNORED (.gitignore:15)
git check-ignore -q docs/acceptance/baselines/home.png   # exit 1 → trackable
git check-ignore -q docs/acceptance/rows/ledger.jsonl    # exit 1 → trackable

# 计划 D0 行号
grep -n "acceptance_report\|acceptance_lint\|verdicts\|metamorphic\|screenshot_baseline\|a11y_audit\|trace_requirements" .zcode/plans/plan-2026-11-05-batch-44-acceptance.md

# 6C-2 现状
grep -n "val ALLOWED_MASKS" core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupSelection.kt   # → 166
```

**未执行**：任何 Gradle 任务、`am instrument`、APK 构建/安装、截图采集、`adb` 设备查询、git 写、工具实现、15 条夹具实跑。上述全部标 UNVERIFIED / 设计件。

---

## 15. 最小落地顺序（给 D0 执行者）

1. **先枚举**：按域写 `ledger.jsonl` 的 1200 行骨架（id/domain/requirement.ref/claim/expected），哪怕 evidence 暂空。否则一切工具无对象（F1）。
2. **再封条**：定 `run_manifest.json` 与 `run_evidence.py`（或手工等价字段），让新证据从第一天就是“单元”。
3. **再夹具**：把 §8.2 的 15 条落成 JSONL；断言 `acceptance_report.py` 全部 exit 1。
4. **再 lint**：schema/配额/denylist/原子性/阈值来源；`R-SUMMARY`、`R-SKIP`、`R-VERDICT` 先上。
5. **最后动态**：XML 重算 → 独立性 → verdict 比对 → 域门。每加一条规则补一条自突变（M1–M5 扩展）。
6. **回放通过后才允许**用本模型给批次 44 的 1200 项签字；在此之前，任何“验收替代机制已建立”的说法都属 UNVERIFIED。
