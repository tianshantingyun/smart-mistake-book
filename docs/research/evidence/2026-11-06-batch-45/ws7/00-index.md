# 批次 45 · WS7 剩余计划（81–84）

> **本轮实跑**（`python -I` / `adb`，共享工作树，零 Gradle）：
> p09 三次设备执行、isolation 低负载门换机 / calibrate / gate / 套件、
> KD-76 A6 三条机械探针、发版 7 条 P0 的只读复核（git / signing / version /
> aab16k / identity / h_compliance）。
>
> **本轮未跑**：任何 Gradle、任何 20 轮 reboot、生产签名、Play 侧、arm64/真机、
> TalkBack。
>
> **设备**：`emulator-5562`（AVD `test_device`，API 34，4 vCPU，x86_64）。
> 安装身份（`kd76_identity.py`，拉包算 sha）：
> app `c5f5efd177825c67779ba230336095453cdc359db7a9735c7cbdc6c3f62b5ca9`、
> test `8ad0ff903a39eaaf79e5d23ad0645359c41ff6012db82d8a3b8dd396033355be`，
> 与 `app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk` /
> `…-androidTest.apk` 逐字节一致。宿主侧 `adb shell cat /proc/sys/kernel/random/boot_id`
> = `2d913fc3-97a5-4e63-a987-0628192a4f27`、`boot_count=53`。

## 0. 四条结论

| # | 项 | 判定 | 一句话理由 |
|---:|---|---|---|
| 81 | KD-76 A6（10+10） | **UNVERIFIED** | 仓库内**三个**工具都拒绝 10+10 形状（各 exit 2）；最新真实 A6（2×20）是 **NOT EVALUABLE**。KD-76 维持 **open** |
| 82 | isolation 低负载门 | **BLOCKED** | 换机 5584→5562 已执行：snapshot 清、floor exit 0、30 样本窗 **INVALID**（`la_ratio_max` 0.3825 > 0.30）；套件 15/15 `-4`、`EXECUTED=0`。isolation 仍**未定案** |
| 83 | p09 | **未收敛维持** | 3/3 绿，但三次重建延迟 **2071/1890/1861 ms** 全部 > 1300 ms 阈值——失败区未被采样；不得写“已修” |
| 84 | 发版 7 条 P0 | **7/7 未闭环** | 逐条复跑值见 §4；**发版不成立**不变 |

---

## 1. WS7-81 KD-76 A6：UNVERIFIED（不是“通过”，也不是“设备缺失”）

### 1.1 为什么“按预注册 10+10 实跑”得不到 A6 判词

仓库自己的三处门都把 10+10 判为**不是 A6**，本轮逐条实跑（命令与输出逐字）：

| 探针 | 命令 | 关键输出 | exit | 证据 |
|---|---|---|---:|---|
| 运行器 | `bash tools/ci/run_kd76_rounds.sh --serial emulator-5562 --mode reboot --rounds 10 --first-round 11 --out /tmp/ws7-a6/never --batch-id B --run-id probe --a6-plan build/kd76-runs/a6b/a6-plan.txt` | `A6 mode requires --rounds 20 --first-round 1 (got 10/11)` | 2 | `kd76-a6-mode-10x10-probe.txt`；代码 `tools/ci/run_kd76_rounds.sh:115-117`（注释原文“10+10 不是 A6”） |
| 预注册 plan | `python -I tools/ci/a6_plan.py validate --plan /tmp/ws7-a6/a6-plan-10.txt`（该文件 = `a6b/a6-plan.txt` 仅把 `roundsPerBatch` 改 10） | `a6_plan: roundsPerBatch='10' expected '20'` | 2 | `kd76-a6-plan-10-probe.txt`；`tools/ci/a6_plan.py:80,105` 把 `roundsPerBatch` 写成常量 `MAIN_ROUNDS` |
| 判词 | `python -I tools/ci/summarize_kd76.py /tmp/ws7-a6/batch10`（10 份**真实**轮 XML，取自 `build/kd76-runs/a6b/batchA/round-01..10`） | `missing rounds: [11…20] (expected exactly 1..20)`；`G2: NOT EVALUABLE` | 2 | `kd76-summarize-10round-probe.txt`；`tools/ci/summarize_kd76.py:503-507` |

结论：即便在设备上跑出 10+10，也**没有任何在树判词**能把两批各 n=10 判成 G1/G2；
`summarize_kd76.py` 的 `MAIN_ROUNDS=20`（`:60`）是预注册常量，本轮未改。

### 1.2 预注册本身仍未裁定

`docs/research/2026-11-01-kd76-stats.md:277` 明确登记：“两批各 10 轮、合并 n=20”
（§7/`:268`）与批次 43 计划的“两次独立 20 轮、各自判词”**互斥且未裁定**。
计划 item 81 写的“按预注册 10+10”在裁定前没有唯一权威来源；本项不替主会话选边。

### 1.3 最新一次真实 A6 的形状与判词（原样引用）

`build/kd76-runs/a6b/`（批次 43，app `ec135844…`、test `a2532e16…`、
`roundsPerBatch=20`、`minSeparationMinutes=30`）的 `a6-report.txt`（已复制为
`kd76-a6b-report.txt`）：

```
A6: NOT EVALUABLE
  batchA: n=19/20, G1 RED …
  batchB: n=19/20, G1 RED …
  - failed_rounds: batchA failedRounds=1 (per-round product failure; censored sample, not a retry)
  - failed_rounds: batchB failedRounds=1 (per-round product failure; censored sample, not a retry)
  - batch_batchA: G1: round 1: FAIL … visual_js worker died: binder died instead of a rendered result
  - batch_batchB: G1: round 17: FAIL … visual_js worker died: binder died instead of a rendered result
```
（`kd76-a6b-plan.txt` 为对应预注册 plan；sha256 见报告末行。）

### 1.4 边界（不得外推）

- **不是设备缺失**：`adb devices` 本轮两台在线（`emulator-5562`、`emulator-5584`）；
  当前构建的 test APK 里 `VisualJsConnectStageCalibrationInstrumentedTest`
  与 `readRoundFromInstrumentationArg` 均在（`dexdump` 命中 599/14 行，
  `classes3.dex`）。
- **20 轮 reboot 未跑**：它需要 ~40 次 reboot，且无论按哪种协议，本轮都拿不到
  A6 判词（§1.1/§1.2）；在共享设备上跑它会对其他轨造成 reboot 干扰而收益为零。
  待主会话裁定协议并给 `a6_plan`/`summarize_kd76` 增加每批 n=10 的判词能力后重跑。
- **KD-76 维持 open**；C4/29.5 s 链/arm64/真机更不在 A6 范围内（R2 §0.10）。

---

## 2. WS7-82 isolation 低负载门：BLOCKED（换机选项已执行到判词）

### 2.1 执行链（全部实跑）

| 步 | 命令 | 结果 | 证据 |
|---|---|---|---|
| 换机前 snapshot（5584） | `python -I tools/ci/lowload_gate.py snapshot --serial emulator-5584` | `test_processes=1` → `ok=false` | 未落盘（放弃该机） |
| 换机（改用 5562） | `adb -s emulator-5562 shell am force-stop …localfirst.test` 后 `snapshot` | `{test_processes:0, runner_processes:0, orphan_workers:0, ok:true}` | `lowload-floor-5562.json` 的 `snapshot` 字段 |
| 标定地板 | `python -I tools/ci/lowload_gate.py calibrate --serial emulator-5562 --out …/lowload-floor-5562.json` | **exit 0**；`busy_p95=18.14 %`、`la_ratio_p95=0.3825`、`la_ratio_max=0.4075`、`wakeup_p95=62.7 ms` | `lowload-floor-5562.json`、`lowload-calibrate-5562.txt(.exit)` |
| 开窗判读 | `python -I tools/ci/lowload_gate.py gate --serial emulator-5562 --floor …/lowload-floor-5562.json --out …/lowload-gate-5562.json --emit-args …/lowload-args-5562.txt` | **exit 1 / INVALID**，`reasons=["la_ratio_max"]`：`0.3825 > LA_MAX_ABS 0.30`（其余全过：`busy_p95 18.71 ≤ 21.14`、`busy_max 19.40 ≤ 36.36`、`la_ratio_p95 0.380 ≤ 0.4325`、`ctxt_p95 414.7 ≤ 984.1`、`wakeup_p95 80.7 ≤ 188.1`） | `lowload-gate-5562.txt/.json/.exit`；常量 `tools/ci/lowload_gate.py:72` |
| 门参数 | `cat …/lowload-args-5562.txt` | `-e smb.lowload.gate INVALID` / `-e smb.lowload.reason la_ratio_max` | 同上 |
| 套件（fail-closed 验证） | `adb -s emulator-5562 shell am instrument -r -w --no-window-animation -e class …VisualJsIsolationInstrumentedTest -e smb.lowload.gate INVALID -e smb.lowload.reason la_ratio_max -e smb.t4.arm OFF -e smb.witness.run ws7-isolation-invalid -e smb.witness.expected_tests 15 …localfirst.test/androidx.test.runner.AndroidJUnitRunner` | 输出含 `OK (15 tests)`，但 **15 × `INSTRUMENTATION_STATUS_CODE: -4`**；witness 判 `EXECUTED=0, ENV_SKIP=15, CONTRADICTION_PASS_WITHOUT_BODY=0, ok=true` | `isolation-invalid-gate.txt`、`isolation-ws7-invalid.witness.jsonl`、`isolation-witness-check.txt`、`isolation-witness-report.json` |

### 2.2 判词与边界

- **BLOCKED**：换机到 5562 后，绝对负载上限仍被打穿（`la_ratio_max`），
  低负载窗**不可得**；isolation 15 例**零执行**，**不得**改判任何一行。
- `OK (15 tests)` 是**跳过假绿**的又一实例（15×`-4`、`EXECUTED=0`）——与批次 42
  §2.3 的教训同型；本次由 witness 机械复核，不是肉眼读 summary。
- **地板本身不“干净”**：`busy_p95=18.14 %`、`la_ratio_max=0.41` 是宿主当前多代理并跑
  下的设备侧状态（R5 §2.6：宿主争用不体现在 guest `steal`）。因此即便某次窗 PASS，
  语义也只是“**不劣于这个已经偏忙的地板**”，不是“设备空闲”。任何据此改判
  isolation 的结论都必须带这个限定。
- **“伸缩”未执行**：调大 vCPU 只会改变 `la_ratio` 的分母，不减少宿主争用，且会改变
  设备代际（预注册条件漂移）；本轮不采用，留待主会话在安静宿主窗重试换机/伸缩。
- **复跑条件**：宿主机空出（`busy_p95` 显著低于 18 %）→ 重跑 `calibrate`+`gate`；
  gate PASS 后再跑 `tools/ci/run_isolation_pair.sh`（OFF/ON 配对，三 run/臂），
  判词归 `isolation_pair_report.py`。

---

## 3. WS7-83 p09：未收敛维持（新数据点 + 机制解释）

测试：`VisualJsClientFrameQueueDeviceInstrumentedTest#aFourthDistinctProgramIsRejectedBusyAndLatestWinsKeepsFifoOrderWhileOneFrameIsInFlight`
（`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientFrameQueueDeviceInstrumentedTest.kt:106`）。

| run | 结果 | `Time:` | kill→warm-up 重建延迟（logcat） | 原始输出 |
|---:|---|---:|---:|---|
| 1 | `OK (1 test)`，`INSTRUMENTATION_CODE: -1` | 15.462 s | 03:39:06.104 → 03:39:08.175 = **2071 ms** | `p09-run1.txt` / `p09-run1-logcat.txt` |
| 2 | `OK (1 test)` | 7.598 s | 03:39:22.427 → 03:39:24.317 = **1890 ms** | `p09-run2.txt` / `p09-run2-logcat.txt` |
| 3 | `OK (1 test)` | 7.180 s | 03:39:36.531 → 03:39:38.392 = **1861 ms** | `p09-run3.txt` / `p09-run3-logcat.txt` |

命令（三次相同，`adb -s emulator-5562 logcat -c` → `am instrument -w -r -e class
'…VisualJsClientFrameQueueDeviceInstrumentedTest#aFourthDistinctProgramIsRejectedBusyAndLatestWinsKeepsFifoOrderWhileOneFrameIsInFlight' …localfirst.test/androidx.test.runner.AndroidJUnitRunner`）。
重建延迟由 `p09-run*-logcat.txt` 中 `killing :visual_js worker` 与**其后第一条**
`visual_js warm-up finished` 的时间戳差分（每轮 2 条 kill，取第一条对应的重建；
逐行原始时间戳在上列文件，任何人可按同规则复算）。

**判读**：用例的队列窗口是 A 之后 400+300+300+300 = **1300 ms** 构造的
（`…DeviceInstrumentedTest.kt:278-279`：`FIRST_FRAME_HEAD_START_MILLIS=400`、
`FRAME_SPACING_MILLIS=300`）。三次重建都 **> 1300 ms**，所以三次都落进“窗口成立”的
绿区；**失败区（<1300 ms）一次也没被采样**。这与批次 43 `ws4-device-rebaseline.md:193-209`
的机理一致（712/933 ms 红、2046 ms 绿）。

**结论**：`p09` **未收敛维持**。新增证据只说明“在当前构建 + 当前设备 + 这三轮条件下
连续 3 次绿，且三次都属于慢重建绿区”；**不能**写“已修/必绿”，也不推翻批次 43
设备轨“3 次 2 台设备同断言失败”的记录。两轨引用都必须带 APK sha + 次数。

**附注（卫生）**：该测试文件在工作树中是**未跟踪**状态（`git status` 显示 `??`）；
本轮执行的是 `8ad0ff90…` test APK 中的字节（sha 等于构建产物，文件 mtime
2026-11-04 05:24 早于 APK 构建时间 11-05 10:22）；**未做**源码↔dex 逐字节比对，
且文件本身未入库。

---

## 4. WS7-84 发版 7 条 P0 → runbook 出口汇总

7 条 P0 的清单来源：`docs/acceptance/adjudication-draft.md:117-123`（#2–#8；
#1 崩溃为 P1，另有 FIX-T01）与 `docs/research/2026-11-06-batch-44-record.md:424-431`。
本轮**只读复跑**当前值（不含 Gradle），逐条给 runbook 出口。

| # | P0 | 本轮复跑值（命令 → 结果） | runbook 出口 | 状态 |
|---:|---|---|---|---|
| 1 | 工作树不干净、产物落后 | `git status --porcelain \| wc -l` = **602**；`-uall` = **3988**（`p0-worktree.txt`）；`j_release_verify.py identity --require-head` → `apk.revision=faab17f5…`、`commits_behind_head=**46**`、`EXIT=1`（`p0-identity.txt`） | §9.2 J-001/J-002；§13 FIX-T13 | **未闭环** |
| 2 | 生产签名缺失 | `j_release_verify.py signing --root . …` → `env_missing=[RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD, RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD]`、`tracked_keystores=[]`（`p0-signing.txt`） | §9.2 J-019–J-027；§13 FIX-T10 | **未闭环（BLOCKED）** |
| 3 | 签名身份不一致/临时证书 | `apksigner verify --print-certs …release.apk` → `CN=WS8 Ephemeral Local Only, OU=NotForRelease`、cert sha256 `9dc07e9f…`；AAB signer sha256 `a9e76cad…`（`p0-apk-certs.txt`、`p0-signing.txt`）→ 两身份不同 | §9.2 J-021/J-024；§13 FIX-T10 | **未闭环** |
| 4 | AAB 内容不合格 | `j_release_verify.py aab16k --aab …aab` → `abi_set=[arm64-v8a, armeabi-v7a, x86, x86_64]`、`required native library missing: libquickjs.so`、`EXIT=1`（`p0-aab16k.txt`） | §9.2 J-033/J-034/J-041；§13 FIX-T12 | **未闭环** |
| 5 | versionCode 恒 2、无生成机制 | `j_release_verify.py version --gradle app/build.gradle.kts --metadata …output-metadata.json` → gradle `version_code=2`、metadata `versionCode=2`、`errors=[]`（`p0-version.txt`）；源码 `app/build.gradle.kts:33-34` 手写 | §9.2 J-048/J-051；§13 FIX-T11 | **未闭环** |
| 6 | AIGC 隐式标识未覆盖交付格式 | `h_compliance_gate.py --all` → `H-018 FAIL`（DOCX 无 `/AIGC`、KD-84、KD-85）、`H-061 FAIL`、`H-062 FAIL`；`EXIT=1`（`p0-aigc-hgate.txt`） | §9.2 J 域无此项 → §13 FIX-T04/T05/T07；`known-defects.md` KD-84/KD-85 | **未闭环** |
| 7 | 质量门未绿 | `acceptance_report.py --ledger docs/acceptance/ledger.md` → `counts={PASS:882, FAIL:30, BLOCKED:144, UNVERIFIED:144}`、`pass_with_review=0`、`state=FAIL`、`EXIT=1`（`p0-quality-report-full.txt`，ledger sha `bb25ef23…`）；`acceptance_lint.py` → `EXIT=0`（warnings 有）；`trace_requirements.py` → `gaps=[]`、`EXIT=0`（`p0-quality-lint.txt`、`p0-quality-trace.txt`） | §2 三静态门；§9.2；WS5 独立复核 | **未闭环**（trace 已清零，report/review 仍红） |

**汇总**：7 条 P0 **全部未闭环**；“发版不成立”与批次 44 §7.1 的判定一致，不因
report 的 PASS 数从 761 涨到 882 而改变。唯一相对批次 44 的改善是
`trace gaps 60 → 0`（`EXIT=0`）。

---

## 5. 边界与未验证

1. **零 Gradle**：未跑单测门/强门；未重建 APK/AAB；§4 的全部 J 值绑定**现有产物**
   （release APK sha `724ce718…`、AAB sha `d0734105…`），不是当前源码新构建。
2. **未跑 20 轮 reboot**（§1.4）；**未跑 arm64/真机/16KB 运行时/TalkBack**。
3. **低负载窗语义**：本轮 floor 本身 `busy_p95=18.14 %`，见 §2.2 的限定。
4. **p09 只覆盖当前构建 + 5562**，三次都在慢重建区；未构造快重建条件。
5. **证据清单**：本目录 36 个文件，其中 35 个被 `MANIFEST.sha256` 覆盖（清单自身不计入）；
   `--verify` 在本轮最后一次生成后 exit 0。清单自身的 sha 由 `--self-hash` **现算**
   （不写死，避免自指失真）；该清单只覆盖本 WS7 目录，全仓/批次级清单归 WS6 item 76。
   目录内**没有** `*.log`（避免被 `.gitignore` 吞掉），全部为 `*.txt/.json/.jsonl`。
6. 本文件是 WS7 四条的执行记录，**不是**验收签字；总体结论仍以
   `docs/acceptance/adjudication.md` 与 `acceptance_report` 的 exit 为准。
