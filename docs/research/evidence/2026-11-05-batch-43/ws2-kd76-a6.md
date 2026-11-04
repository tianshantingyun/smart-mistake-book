<!-- 体积自查：285 行 / 27,259 B（落盘后实测 wc -lc）。批次 43 WS2 执行员。 -->

# 批次 43 WS2：KD-76 A6 同构建两批独立 20 轮（工具 + 设备 + 判词）

| 字段 | 值 |
|---|---|
| evidence-id | `B43-WS2-KD76-A6-01` |
| 计划 | `.zcode/plans/plan-2026-11-05-batch-43.md` §2 WS2 项 11–18（`:38-46`）；§4 硬约束；§5 验收 |
| 协议 | `docs/research/2026-11-05-kd76-a6-protocol.md`（R2）；基线 `docs/research/2026-11-05-batch-43-baseline.md` §2.3（F2：C4 定义冲突） |
| executed-at | 2026-11-03T14:56Z–2026-11-03T17:5xZ（本机 UTC；文件名按计划批次日） |
| head | `0fef943641f53560b92ce2afec4da22bf52f5e86`（`git rev-parse HEAD`，本轮实跑） |
| 工作树 | 开始 540 脏 → 结束 555（共享树，他线在飞） |
| 设备 | `emulator-5584`（AVD `kb_test_device`；API 34 / x86_64 / PAGE_SIZE 4096 / 4 vCPU；`-no-snapshot`） |
| 构建 | app `ec135844ee879f8d889880aec618545889d00487094c9f52c95ba5f889e19bb5`、androidTest `a2532e16d1809e666643bc5f243b410b14ba863375c6ecd6100c1f5da1e9687b`（**均为已安装字节，宿主 `pm path` 拉包实测；本批未重装、未重新构建**） |
| 设备代际 | `5342940bb87fcbbc7a28380157f55c85afa4029bd5df5fbae0302767c44a423c` |
| 安装代际 | attempt 2：`9b0dbca28c38fff55db2b3b0c12f7c093a55203389fd3d038c763ec6b30f89e3`（两次实测一致，见 §2.2） |
| 预注册 plan | `build/kd76-runs/a6b/a6-plan.txt`，sha256 `1cc29dc0c51f1f1098781ae1538c53dddd35cb634bd78f958ff711859f70927c` |
| 执行者 | WS2 执行员（子代理），共享工作树；**本文件是唯一写入的文档**（另有 `docs/known-defects.md` 台账块，item 18） |
| 未执行 | 任何 Gradle（构建/安装）、任何 git 写、任何 Provider 调用、任何源码行为改动 |

> 口径：**实跑** = 本轮执行并读退出码/输出；**实读** = 本轮读源码/文档原文（给行号）；**转录** = 引既有 E 文件。
> 本文件里的每个数字都来自 `build/kd76-runs/a6*` 的原始产物（gitignored），可按下文命令复算。

---

## 0. 结论（pick）

1. **A6 工具链已落地并跑通**：`run_kd76_rounds.sh`（A6 字段 + 哈希链）、`run_kd76_a6.sh`（两批 + 间隔 + 身份复核）、`kd76_identity.py`（宿主拉包 sha / 设备代际 / 安装代际）、`a6_plan.py`（预注册，拒绝覆盖）、`kd76_a6_verify.py`（I0–I10 合取，不合并 p95）。单测 **1063 passed / 70,709 subtests**（`python -m pytest tools/tests`，exit 0，6m05s；含本批新增 13+6 例）。
2. **身份门自己先抓到一个真缺陷**：attempt 1 的 batch A 20/20 PASS（M.p95=2824 ms），但批 B 开跑前身份复检把 `installGenerationId` 判为"变了"——根因是签名摘要取了 `PackageSignatures{<objhash>}` 的 **system_server identity hash**（每次 reboot 都变）。已修为只哈希 `signatures:[...]` 列表，并加"同一字段两次读取必须一致"的前门与单测；attempt 1 的批 B **被拒、未开跑**（原样保留）。
3. **attempt 2 的 batch A 出现一次产品可见失败**：round 1（boot 后首轮，`la1` 5.92 → 10.66 / 4 vCPU）execute 返回 `Rejected/HARD_DEADLINE_EXCEEDED: visual_js worker died: binder died instead of a rendered result`，删失 ≥2500 ms → n=19/20、G1 red、批级 **NOT EVALUABLE**。按 A7 **不重跑洗绿**（失败不是测量缺陷）；机制（1500 ms watchdog 杀 vs 系统杀）**未定案**——runner 只保留 `KD76_*` + warm-up 行，未留当轮完整 logcat（§9 R1）。
4. **batch B（同构建、同安装、30 min 分离后）**：见 §4。
5. **A6 判词**：见 §5（工具原文）。**无论判词如何，KD-76 条目不能据此关闭**：③ 产品进程首请求（item 13）结构性 BLOCKED、C4 无毫秒分布、29.5 s 链只有 C1/C2 实测、低内存/arm64/真机 UNVERIFIED。
6. **item 13（产品进程首请求计时入口）= BLOCKED**：生产装配 `FigureGenerationCoordinationFactory.create(...)` 只传三参（`SmartMistakeBookApplication.kt:364-368`），`visualEngine` 默认 null（`DefaultFigureGenerationCoordinator.kt:885`）→ 产品进程里的 JS 恒 `UNAVAILABLE`（负控 `V01VisualChainInstrumentedTest.kt:276`）。要真实测"产品进程首请求"，必须改 `Application`（本批禁改热区）并翻转 JS 广告面（WS7/MVA 未过）。runbook 见 §7。
7. **item 16（29.5 s 四段实测）= 部分**：C1/C2 有 n=20 分布；C3 只有 KD-66 witness（是上界，不是分布）；C4 只有布尔（`abandonForTest()` 返回 `Boolean`，`VisualJsClientEngine.kt:1205`；用例 `:516`），**没有毫秒夹钟** → `sum of measured max = UNVERIFIED`。不得把各段 p95 相加。

---

## 1. 交付物（每项指认它消灭的失败）

| 文件 | 改动 | 消灭的具体失败 |
|---|---|---|
| `tools/ci/kd76_identity.py`（新） | 宿主 `pm path` + 拉包算 app/test sha；`dumpsys package` 取 versionCode/firstInstallTime/lastUpdateTime/signatures；设备代际（serial/fingerprint/sdk/build.id/incremental/model/device/abilist/pageSize/nproc/AVD/宿主 qemu 进程）；**两次读取一致性前门** | 同 HEAD 不同构建冒充"同一构建"（批次 42 的 `35e1…` vs `ec13…`）；换模拟器实例；两批间重装；**不稳定身份字段被写进 plan**（本轮真发生，§2.2） |
| `tools/ci/run_kd76_rounds.sh`（改） | `--batch-id/--run-id/--a6-plan/--identity-json`；`KD76_ENV` 增 `batchId/runId/roundStartedUtc/roundFinishedUtc/screenAwake/pidWorkerAfter/warmupFinishedCount/warmupFailedCount/la1/nproc`；`logcat -c` 后再读一次 `pidof :visual_js`；warm-up 见证恰好 1 条 finished、0 条 failed；manifest 增身份代际与**逐文件哈希链**；写 `manifest.sha256` | 复制 A 批当 B 批、手写最小 XML、两批并行/切段、热 worker 竞态、worker 中途重建、身份不可审计 |
| `tools/ci/a6_plan.py`（新） | 预注册 plan（n=20、nearest-rank、5000 ms、reboot、settle=0、`minSeparationMinutes≥10`、`maxReplacementBatches≤1`）；**拒绝覆盖已存在 plan**；输出 sha256 | 事后改口径（optional stopping）、plan 被悄悄替换 |
| `tools/ci/kd76_a6_verify.py`（新） | I0–I10 + 两批判词合取 → `CONFIRMED/REFUTED/NOT EVALUABLE`（exit 0/1/2）；JSON 与文本；**不产出 n=40 / combined p95**；XML 解析复用 `summarize_kd76._read_xml`（拒绝 DOCTYPE/ENTITY）+ 8 MiB 上限 | 把两次 n=20 当 n=40、平均/相加 p95、换设备/重装冒充一次实验、证据复用 |
| `tools/ci/run_kd76_a6.sh`（新） | 一次安装（可选）→ 身份 → plan → 批 A → **身份复检** → 等 `minSeparationMinutes` → 批 B → 每批判词分开存 → A6 合取 | 手工跑两批时忘等间隔/忘复核设备/忘用同一构建 |
| `tools/tests/test_kd76_a6_verify.py`（新，12 例） | 先红后绿：CONFIRMED 形状、异 app sha、B 早于 A、跨批 boot 重复、n=19、证据逐字节复制、条件漂移、缺 am 原文、两批超门 → REFUTED、固定向量 p95/门、plan 间隔不足 | 合取/合并规则被改回"相加"；判词在证据不足时印出 CONFIRMED |
| `tools/tests/test_kd76_identity.py`（新，6 例） | 代际哈希稳定性/字段敏感性、`dumpsys` 解析、**对象哈希不入签名摘要** | 本轮 attempt 1 的真实失败 |

> 说明：`summarize_kd76.py` **未改**（每批 1..20 的拒绝逻辑保持，防止有人把 A6 做成 40 轮）。

---

## 2. 预注册与身份（跑前写死）

### 2.1 plan（`build/kd76-runs/a6b/a6-plan.txt`，sha256 `1cc29dc0…`）

```
a6PlanVersion=1
planId=2026-11-03T16-00Z
requiredBatches=2 / roundsPerBatch=20 / estimator=nearest-rank / gateMillis=5000
mode=reboot / settleSeconds=0 / minSeparationMinutes=30 / maxReplacementBatches=1
expectedAppApkSha256=ec135844… / expectedTestApkSha256=a2532e16…
expectedDeviceGenerationId=5342940b… / expectedInstallGenerationId=9b0dbca2…
allowReinstall=false
serial=emulator-5584 / avdName=kb_test_device / emulatorInstanceId=qemu:29356:1793691046018
```

- plan 在 batch A 开跑**之前**写死（16:00:45Z 写完；A 首轮 16:01:54Z）。
- 两批 manifest 都记 `a6PlanSha256=1cc29dc0…`；composer 重算比对（§5）。

### 2.2 身份复检：一次被自己的门拦下的尝试（attempt 1，真缺陷）

| 时点 | 观测 | 结论 |
|---|---|---|
| attempt 1 A 开跑前 | `installGenerationId=1461575a…`（旧实现：签名摘要 = 整行 `signatures=PackageSignatures{e3ce40c …}` 的哈希） | plan 与 batch A manifest 都记了这个值 |
| A 20 轮 reboot 后（15:53:39Z） | 复检 `installGenerationId=6bc583b8…` → **wrapper 拒绝开跑 batch B（exit 2）** | 复检门按设计工作：它把"同一安装"判成"重装" |
| 根因（本轮实读 + 实测） | `PackageSignatures{<objhash>}` 里的对象哈希随 system_server 进程变（`dumpsys` 三次连续读取相同，但 20 次 reboot 后必变） | 这不是设备状态变化，是**身份字段不稳定** |
| 修复 | 签名摘要只哈希 `signatures:[...]` 列表；`collect()` 增加"同一字段两次读取必须逐字相同"的前门；单测 `test_signatures_digest_ignores_the_unstable_object_hash` | 消灭"不稳定字段悄悄进 plan，20 分钟后才炸" |
| 实证 | attempt 2：A 前 `9b0dbca2…`，20 轮 reboot 后复检仍 `9b0dbca2…` → **批 B 放行（16:59:54Z）** | 修复在真实设备上被验证 |

- attempt 1 的产物原样保留：`build/kd76-runs/a6/`（batch A 20/20、M.p95=2824 ms、plan sha `8b929e8d…`）+ `build/kd76-runs/a6-attempt1-refused/`（driver.log、identity.json、identity-b.json、plan）。
- attempt 1 的 batch A **不并入** A6：它的 plan 与本轮 plan 不同（身份公式不同），且 A6 要求两批同一 plan 同一构建。

---

## 3. 设备实跑：batch A（attempt 2）

| 项 | 值 | 来源 |
|---|---|---|
| runId / batchId | `20261103T160030Z-A-312840` / `A` | `batchA/manifest.txt` |
| started → finished | `2026-11-03T16:00:50Z` → `2026-11-03T16:30:07Z` | 同上 |
| rounds | 20 requested / **19 成功** / 1 失败（round 1） | 同上 + `summary.txt` |
| boot_id | 20/20 unique（宿主，逐轮 before==after） | `summary.txt` |
| bootCount | 64..83（与宿主 `settings get global boot_count` 逐轮相等） | 同上 |
| G1 | **RED**：round 1 `<failure>` + `Rejected/HARD_DEADLINE_EXCEEDED`（删失 ≥2500 ms） | `round-01.am.txt`；`summary.txt` |
| 批级判词 | `G2: NOT EVALUABLE (n=19/20; G1 RED; snapshot consistent; input complete)`，exit 2 | `build/kd76-runs/a6b/batchA/summary.txt` |
| round 1 条件 | `la1=5.92`（loadBefore `5.92 1.32 0.43 44/1806`）→ loadAfter `10.66`；screen `Awake`；`warmupFinishedCount=1`；`bootIdBefore==bootIdAfter` | `round-01.xml` 的 `KD76_ENV` |
| 成功 19 轮的 M | sorted=`[976, 1229, 1533, 1557, 1562, 1788, 2196, 2537, 2798, 3069, 3117, 3155, 3410, 3819, 3833, 4370, 4609, 5055, 6305]`（**描述性；G1 红时不得引用 p95/门判词**） | 19 份 `round-*.xml` 的 `bind+warmup` |

**归因（A7 允许的三种结论里，本条目给"未定案"）**：失败形状是"execute 未返回 Rendered，worker binder died"，删失下界取宿主阶段期限 2500 ms；同一轮的 warm-up 见证正常、屏幕 Awake、boot 身份无缺陷 → 测量本身有效，**不是测量缺陷**，因此不适用"整批作废重跑"。机制层面（1500 ms 服务 watchdog 杀 vs 系统/LMK 杀）无法从留存的证据区分——runner 当轮只保留 `KD76_*` 与 warm-up 行（§9 R1）。按协议它必须记为**产品可见红**，不得因后续批次绿而消失。

---

## 4. 设备实跑：batch B（attempt 2）

| 项 | 值 | 来源 |
|---|---|---|
| runId / batchId | `20261103T160030Z-B-312840` / `B` | `batchB/manifest.txt` |
| started → finished | `2026-11-03T17:00:12Z` → `2026-11-03T17:32:20Z` | 同上 |
| rounds | 20 requested / **19 成功** / 1 失败（round 17） | 同上 + `summary.txt` |
| boot_id | 20/20 unique（宿主，逐轮 before==after） | `summary.txt` |
| bootCount | 84..103（与宿主逐轮相等；`B.min 84 > A.max 83`） | 同上 |
| G1 | **RED**：round 17 `<failure>` + `Rejected/HARD_DEADLINE_EXCEEDED: visual_js worker died: binder died instead of a rendered result`（删失 ≥2500 ms） | `round-17.am.txt`；`summary.txt` |
| 批级判词 | `G2: NOT EVALUABLE (n=19/20; G1 RED; snapshot consistent; input complete)`，exit 2 | `build/kd76-runs/a6b/batchB/summary.txt` |
| round 17 条件 | `la1=5.70`（loadBefore `5.70 1.27 0.42 48/1643`）→ loadAfter `15.93`；screen `Awake`；`warmupFinishedCount=1`；`bootIdBefore==bootIdAfter` | `round-17.xml` 的 `KD76_ENV` |

**两批的失败同形**：A round 1、B round 17，都是 `execute` 未返回 `Rendered` 而 worker binder died，删失下界同为宿主阶段期限 2500 ms；两轮 `warmupFinishedCount=1`、屏幕 Awake、boot 身份无缺陷。即 **2/40 轮**在预注册条件（settle=0、boot 后不沉降）下出现产品可见失败；按 A7 不重跑洗绿，也不把两批合成一个"失败率"。

**描述性观察（不是判词）**：两批 19 个成功样本里各有超过 5 000 ms 的 M（A：5055、6305；B：5390、5822）。G1 红时不得引用 p95/门判词，但"存在超门样本"这一事实必须登记——它说明即使把失败轮当作单纯删失，冷启动 M 也没有全部落在门内。

---

## 5. A6 判词

**工具原文（`build/kd76-runs/a6b/a6-report.txt`，exit 2）**：

```text
A6: NOT EVALUABLE
  batchA: n=19/20, G1 RED, M.p95=UNVERIFIED ms (gate 5000 ms, margin UNVERIFIED ms), max=6305 ms, batch=A, runId=20261103T160030Z-A-312840, app=ec135844ee879f8d…
  batchB: n=19/20, G1 RED, M.p95=UNVERIFIED ms (gate 5000 ms, margin UNVERIFIED ms), max=5822 ms, batch=B, runId=20261103T160030Z-B-312840, app=ec135844ee879f8d…
  - raw_output_missing: batchA XML without am output/OK line ['round-01.xml']
  - raw_output_missing: batchB XML without am output/OK line ['round-17.xml']
  - failed_rounds: batchA failedRounds=1 (per-round product failure; censored sample, not a retry)
  - failed_rounds: batchB failedRounds=1 (per-round product failure; censored sample, not a retry)
  - batch_batchA: G1: round 1: XML carries a <failure>/<error> element
  - batch_batchA: G1: round 1: FAIL (censored >= 2500 ms): execute produced Rejected/HARD_DEADLINE_EXCEEDED: visual_js worker died: binder died instead of a rendered result
  - batch_batchB: G1: round 17: XML carries a <failure>/<error> element
  - batch_batchB: G1: round 17: FAIL (censored >= 2500 ms): execute produced Rejected/HARD_DEADLINE_EXCEEDED: visual_js worker died: binder died instead of a rendered result
  no A6 claim may be derived; per-batch numbers are not a substitute, and no batch PASS may be read as A6.
  observed gap: 30.466666666666665 min; plan sha256: 1cc29dc0c51f1f1098781ae1538c53dddd35cb634bd78f958ff711859f70927c
```

> **重复行误报已修（2026-11-05 修复环）**：本批 XML 每轮有 2 条逐字相同的 `KD76_SAMPLE`（`emit()` 同时写 `Log.i` 与 `System.out`）。旧 `summarize_kd76.py` 对 `build/kd76-runs/a6b/*` 会额外输出 20 条「round N has 2 KD76_SAMPLE lines」、EXIT=2；修复后的工具只折叠**逐字相同**的重复，内容不同的重复仍拒绝。对 a6b 重跑的结果与上面 `a6-report.txt` 一致：两批仍因各 1 个真实失败轮判 `NOT EVALUABLE`，重复行不再是理由（本轮实跑 `kd76_a6_verify.py` → EXIT=2，无 duplicate 行）。

**独立性检查（I0–I10）逐项结果**（同一份报告与 `--json`）：

| 检查 | 结果 | 依据 |
|---|---|---|
| I0 预注册 | 绿 | 两份 manifest 的 `a6PlanSha256` == plan sha；`minSeparationMinutes=30 ≥ 10`；`maxReplacementBatches=1` |
| I1 构建 | 绿 | `installedAppSha256=ec135844…` == `installedTestSha256=a2532e16…` 两批与 plan 三方相等（`app` 前缀在两行里相同） |
| I2 设备代际 | 绿 | `5342940b…` 两批相同、等于 plan |
| I3 安装代际 | 绿 | `9b0dbca2…` 两批相同、等于 plan；`firstInstallTime/lastUpdateTime` 相同（§2.2 的修复在此被验证） |
| I4 协议 | 绿 | `mode=reboot/settle=0/rounds=20/firstRound=1/class/method/component/estimator` 逐字相同 |
| I5 时间 | 绿 | `A.finished 16:30:07Z < B.started 17:00:12Z`；observed gap **30.47 min ≥ 30**；40 个轮次窗口无重叠；无时钟回退 |
| I6 启动 | 绿 | 40/40 宿主 `boot_id` 两两不同、A∩B=∅、逐轮 before==after；`bootCount` 64..103 严格递增，`B.min 84 > A.max 83` |
| I7 进程 | 绿 | 40 轮 `pidWorker` 空、`pidWorkerAfter` 空（本轮新增的"logcat 清空后再读"）；`pidTarget` 位一致；SAMPLE 轮 `generation` 非空；每轮恰好 1 条 `visual_js warm-up finished`、0 条 failed |
| I8 条件 | 绿 | 两批 S-warmup 中位数比、`la1/nproc` 中位数比均 ≤ 2×；40 轮屏幕 `Awake` |
| I9 证据 | **红（仅限失败轮）** | 两批各 20 份 XML + 20 份非空 `.am.txt`、逐文件哈希链与 `manifest.sha256` 重算通过；但失败轮的 XML 不含 `OK (n test)` 行 → `raw_output_missing`（该轮本来就是失败轮，不是证据缺失） |
| I10 不重试 | **红（产品失败轮）** | 两批 `roundsRequested=20`、`replacementCount=0`；`failedRounds=1` 各一 → `failed_rounds`（A7：不重跑洗绿） |

**判词语义**：A6 只在"两批都能出完整 n=20 判词"时才有 CONFIRMED/REFUTED；任一批 G1 红 → `NOT EVALUABLE`（协议 §5.3）。因此本轮 **A6 无法判定**，**KD-76 不能因 A6 关闭**；同时"两批各自 G1 都红"本身是比 A6 更强的负结果：在预注册条件下，冷启动首个请求有 2/40 轮直接失败。

**与批次 42 的对照（描述性）**：批次 42 的单批 n=20 为 `M.p95=1889 ms`（`build/kd76-runs/ws3-batch42-20/summary.txt`，本轮实读）。本轮两批的 19 个成功样本里 max 分别为 6305/5822 ms。两次的构建不同（`35e1…` vs `ec13…`）且宿主负载不同，**不能直接比较**，更不能写成"回归"——这需要同构建、同窗口的对照实验（§9 R8）。

---

## 6. 29.5 s 链四段（`kd76_chain_segments.py`）

`python -I tools/ci/kd76_chain_segments.py build/kd76-runs/a6b/batchA|batchB`（本轮实跑，exit 0）：

| segment | budget ms | batch A (n=19) min/p50/p95/max | batch B (n=19) min/p50/p95/max | evidence |
|---|---:|---|---|---|
| C1 S-bind | 15000 | 668 / 2482 / 4205 / 4205 | 1503 / 2971 / 5303 / 5303 | `round-*.xml` S-bind |
| C2 S-warmup | 10000 | 204 / 415 / 2249 / 2249 | 215 / 423 / 942 / 942 | `round-*.xml` S-warmup |
| C3 host | 2500 | **witness=UNVERIFIED** | 同左 | KD-66 witness 是上界，不是分布 |
| C4 death | 2000 | **UNVERIFIED** | 同左 | `abandonForTest` 只有布尔（§8.2） |

- `sum of budgets = 29 500 ms`（工具硬断言：四段预算之和必须等于文档的 29.5 s，否则 exit 非 0）。
- `sum of measured max = UNVERIFIED`——C4 没有实测 max，工具**拒绝**给和。四段 p95 不得相加（分位数不可加）。
- **口径限定**：两批各有 1 个失败轮被工具跳过（它不是 SAMPLE），因此 C1/C2 分布是"成功轮条件分布"，存在 coordinated omission；失败轮按 A5 记删失 ≥2500 ms，不得当作缺失。
- 结论：**"29.5 s 链四段实测"不成立**（C1/C2 实测、C3 仅 witness、C4 无毫秒）。要补齐 C4 需在生产 seam 上加夹钟（`abandonForTest` 返回 `confirmed + elapsedMillis`），C3 需在宿主阶段期限路径上取实际耗时；两者都要重建 test APK 并重跑，属下一批。

---

## 7. item 13：产品进程首请求 = BLOCKED + runbook

**判定：BLOCKED（结构性，不是执行问题）。**

| 事实 | 锚点（本轮实读） |
|---|---|
| 生产装配只传三参 | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt:364-368`（`database/configurationStore/networkRequestsAllowed`） |
| `visualEngine` 默认 null | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/DefaultFigureGenerationCoordinator.kt:885` |
| 产品进程里 JS 恒 UNAVAILABLE（负控钉住） | `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/V01VisualChainInstrumentedTest.kt:276`（`theProductionAssemblyWithoutAnInjectedEngineKeepsJsUnavailableWithoutAuditRows`） |
| 原因一：`Application` 是本批禁改热区 | 计划 §4「热区 core/domain、core/ui、feature/*、docs/current、settings.gradle.kts、Application/Root 禁改」 |
| 原因二：接线会翻转 JS 广告面 | 准入清单 MVA 未过；WS7 生产接线 BLOCKED（批次 42 记录 §6.1-1） |

**runbook（需要主会话授权后执行）**：

1. 在 `SmartMistakeBookApplication.kt` 装配点注入 `visualEngine = VisualJsClientEngine(context)` 与 `visualFrameInputs`（同一提交内同步能力快照/广告门文本，否则负控 `V01…:276` 会红——这是设计上的保护）。
2. 新增设备用例：**产品进程**（`com.tingyun.smartmistakebook.localfirst`，非 `.test`）启动后，用产品 UI 触发一次真实请求，记录 `connectStageTimestamps`（或等价的 ready→首帧时刻）并在 logcat 打一条 `KD76_PRODUCT` 行；宿主侧脚本按同样 20 轮形状采集。
3. 判据与 A6 相同（`M = S-bind + S-warmup`、nearest-rank、p95 ≤ 5000 ms），但**必须单独预注册**，不得与测试进程的 A6 合并。
4. 在此之前，任何"A6 通过"都不得写成"用户首请求已达标"。

---

## 8. A6 / C4 逐条对账（item 17）

### 8.1 A6（anti-flaky A6 = 两批独立）逐条

| # | 要求（`2026-11-01-kd76-stats.md:235`） | 本轮状态 | 证据 |
|---|---|---|---|
| A6-① | 两批独立**冷启动** | 两批都是 `mode=reboot`、每轮一次 `adb reboot`、测量前 `pidWorker` 空、`boot_id` 每轮不同 | `summary.txt`；`KD76_ENV` |
| A6-② | 两批**各自**过 G1 | batch A **G1 RED**（round 1 产品失败）；batch B 见 §4 | 各批 `summary.txt` |
| A6-③ | 两批**各自**过 G2（p95 ≤ 5000） | 任一批 G1 红时不出 p95 | 同上 |
| A6-④ | 两批**同一构建** | app/test sha 均取自宿主拉包，两批与 plan 三方相等 | `manifest.txt`；`a6-plan.txt` |
| A6-⑤ | 两批**不合并 n** | composer 不产出 n=40/合并 p95；JSON 无 `merged_p95_millis` | `kd76_a6_verify.py`；单测 |
| A6-⑥ | 时间分离 | `A.finishedUtc < B.startedUtc`，40 个轮次窗口无重叠，间隔 ≥ 30 min | §4/§5 |

### 8.2 C4（死亡确认段 2 000 ms）逐条

| # | 要求（`2026-11-01-kd76-stats.md:169,273`） | 本轮状态 | 证据 |
|---|---|---|---|
| C4-① | `abandon → awaitDeathConfirmation` 返回的毫秒分布 | **不存在**：`abandonForTest()` 只返回 `Boolean` | `VisualJsClientEngine.kt:1205`；用例 `:516` |
| C4-② | 与预算 2 000 ms 比较 | 只有布尔断言"≤2 s 确认"，没有 ms 值 | `:516-521` |
| C4-③ | 报告口径 | `kd76_chain_segments.py --c4-max-ms` 需**调用方手工传入**，本轮不传 | `kd76_chain_segments.py:154-158` |
| C4-④ | 计划 §0 的"C4 = 产品进程首请求" | **与权威定义冲突**（基线 F2）；本轮按权威定义（死亡确认段）对账，产品进程首请求单列为 ③（§7） | `2026-11-05-batch-43-baseline.md:167` |

### 8.3 计划 WS2 11–18 逐项

| # | 项 | 状态 | 落点 |
|---|---|---|---|
| 11 | 同构建两次独立 20 轮脚本（时间分离 + 每轮 boot_id） | **完成** | `tools/ci/run_kd76_a6.sh`、`kd76_identity.py`、`a6_plan.py`、`run_kd76_rounds.sh`（A6 字段） |
| 12 | 两次样本独立判词（各自 n/p95/G1/G2），不合并 | **完成** | `kd76_a6_verify.py` + 12 例单测；每批 `summary.txt` 分开保存 |
| 13 | 产品进程首请求计时入口 | **BLOCKED** | §7（结构性；需 `Application` 授权 + WS7 接线） |
| 14 | 设备实跑第一次 20 轮 | **完成（含产品红）** | §3（n=19/20、G1 RED） |
| 15 | 设备实跑第二次 20 轮（同 APK sha） | 见 §4 | `build/kd76-runs/a6b/batchB` |
| 16 | 29.5 s 四段实测与相加报告 | **部分** | §6（C1/C2 实测；C3 witness；C4 UNVERIFIED） |
| 17 | A6/C4 逐条对账 | **完成** | 本节 8.1/8.2 |
| 18 | 台账回写 | 见 `docs/known-defects.md` KD-76 段（批次 43 Progress 块） | item 18 |

---

## 9. 未验证与边界

- **R1（runner 证据缺口）**：失败的轮只保留 `KD76_*` + warm-up 行，**没有当轮完整 logcat**，因此"C4/失败是 watchdog 还是系统杀"无法定案。修法（下一批）：失败轮额外 `logcat -d` 全量落 `round-NN.logcat.txt`。
- **R2（模拟器不代表用户）**：x86_64/API 34 模拟器；Macrobenchmark 官方明确模拟器数字不代表终端用户（R2 §9.1 引）。A6 只能作回归/可复现性证据。
- **R3（C3/C4 无分布）**：见 §6/§8.2；四段"实测"不成立。
- **R4（低内存/arm64/真机）**：全部 UNVERIFIED/BLOCKED。
- **R5（证据不可伪造的边界）**：哈希链只防局部改；L3/L4（全量重造、假 adb）本地无法防（协议 §7.2）。
- **R6（attempt 1 不作 A6 的一半）**：见 §2.2；其 batch A 只作历史单批事实（p95=2824）。
- **R8（无同窗口对照）**：批次 42 的单批 p95=1889 ms 与本轮的两批不在同一窗口、不是同一构建（`35e1…` vs `ec13…`），差值不能解释成回归；要判回归必须同构建、同窗口、同宿主的对照批次（本轮未做）。
- **R7（构建与源码的对齐）**：两批跑的是**已安装字节**，不是本轮从当前树新构建的产物——已安装 app sha `ec135844…`，而工作树现有构建产物 `app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk` 的 sha 是 `24495035094f6c03…`（不同）；已安装 test sha `a2532e16…` 与该构建产物相同。A6 只要求"两批同一构建"，这一条成立；但"设备绿绑定当前源码"**不成立**。`sourceHead`/`sourceDirtyPaths` 只作审计元数据。

---

## 10. 复跑命令（逐字）

```bash
# 1) 两批（自带身份、plan、间隔、合取）
tools/ci/run_kd76_a6.sh --serial emulator-5584 --out-root build/kd76-runs/a6c \
  --app-package com.tingyun.smartmistakebook.localfirst --min-separation-minutes 30

# 2) 只重算判词（不重跑设备）
python -I tools/ci/summarize_kd76.py build/kd76-runs/a6b/batchA
python -I tools/ci/summarize_kd76.py build/kd76-runs/a6b/batchB
python -I tools/ci/kd76_a6_verify.py --plan build/kd76-runs/a6b/a6-plan.txt \
  --batch-a build/kd76-runs/a6b/batchA --batch-b build/kd76-runs/a6b/batchB

# 3) 29.5 s 链分段
python -I tools/ci/kd76_chain_segments.py build/kd76-runs/a6b/batchA --json

# 4) 工具单测
python -m pytest tools/tests -q
```

---

## 11. 回链

- 计划：`.zcode/plans/plan-2026-11-05-batch-43.md` §2 WS2 11–18、§4、§5。
- 协议：`docs/research/2026-11-05-kd76-a6-protocol.md`（§3 六类代际、§4 执行形状、§5 判词、§7 可伪造点）。
- 基线/冲突：`docs/research/2026-11-05-batch-43-baseline.md` §2.3（F2）、§7 F2。
- 统计口径：`docs/research/2026-11-01-kd76-stats.md` §2.3/§5（A6、nearest-rank、5 000 ms）。
- 台账：`docs/known-defects.md` KD-76 段（批次 43 Progress 块）。
- 原始产物（gitignored）：`build/kd76-runs/a6b/{a6-plan.txt,identity.json,identity-b.json,batchA,batchB,a6-report.txt}`；attempt 1：`build/kd76-runs/a6/`、`build/kd76-runs/a6-attempt1-refused/`。
- 本文件不替代主会话裁定：**KD-76 维持 open**（A6 见 §5；③/C4/29.5 s/低内存/arm64 仍缺）。
