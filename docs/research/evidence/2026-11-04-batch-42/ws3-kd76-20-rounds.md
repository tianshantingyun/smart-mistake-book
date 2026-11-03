<!-- 体积自查：约 170 行；R4 上限 400 行 / 65536 B -->
# E-42-WS3-01 KD-76 20 轮 reboot 冷启动主口径实跑（单批 n=20）

| 字段 | 值 |
|---|---|
| evidence-id | E-42-WS3-01 |
| convention-version | 1 |
| batch | 2026-11-04-batch-42 |
| step | WS3 / 计划项 25–33（`.zcode/plans/plan-2026-11-04-batch-42.md:53-63`） |
| gate-or-kd | KD-76 |
| verdict | **主口径 PASS（单批 n=20）**：G1 green、`M.p95=1889 ms ≤ 5000 ms`；KD-76 条目仍 **open**（A6 两批独立 + C4/产品进程口径未满足，见 §7） |
| executed-at | 2026-11-03T07:01–07:22+08:00（UTC 2026-11-02T23:01–23:22Z） |
| operator | WS3 轨 |
| head | `faab17f5`（`sourceDirtyPaths=559`，共享工作树） |
| device-serial | emulator-5584（AVD `kb_test_device`，API 34，x86_64，PAGE_SIZE 4096） |
| device-fingerprint | `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys` |
| apk-sha256 | app `35e10339c4606dabb17a8210763fba68a5d8acf1be67703793293b82d4bff1f8`；androidTest `0e906482a0a98d1e4b9f5bb56a3262a80a32d9f6404b80495289b22cec4e3aca` |
| exit-code | driver `20/20 rounds passed` / exit 0；`summarize_kd76.py` exit 0；`kd76_chain_segments.py` exit 0 |
| raw-log | `build/kd76-runs/ws3-batch42-20/{manifest.txt,round-01..20.xml,round-01..20.am.txt,boot-ids.txt,summary.txt,summary.json,chain.txt,chain.json,verdict-kotlin.txt,ws3-batch42-20.console.log}` |
| record-ref | R3 `docs/research/2026-11-04-kd76-20-rounds.md`；批次 41 F10/F11（`docs/research/2026-11-03-batch-41-record.md:431-438`） |
| sanitized | 是（无密钥/账号/Cookie） |

## 1. 目的与命令

本轮把 R3 的"20 个独立 boot 的单轮证据"落成设备事实：每轮 `adb reboot` → 宿主读 `boot_id` → 一次独立 `am instrument -e smb.kd76.round N` → `round-NN.xml`。

```bash
# 装机（一次，绑定 sha）
adb -s emulator-5584 install -r -t app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk
adb -s emulator-5584 install -r -t app/build/outputs/apk/androidTest/localFirst/debug/app-localFirst-debug-androidTest.apk

# 20 轮（reboot 主口径；settle=0）
bash tools/ci/run_kd76_rounds.sh --serial emulator-5584 --mode reboot \
  --rounds 20 --first-round 1 --out build/kd76-runs/ws3-batch42-20 \
  --apk app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk \
  --test-apk app/build/outputs/apk/androidTest/localFirst/debug/app-localFirst-debug-androidTest.apk \
  --target-package com.tingyun.smartmistakebook.localfirst \
  --component com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner

# 汇总（唯一判词源：Kotlin Kd76ReportVerdict 的镜像；本轮同时直接执行 Kotlin 对象，见 §4.2）
python -I tools/ci/summarize_kd76.py build/kd76-runs/ws3-batch42-20
python -I tools/ci/kd76_chain_segments.py build/kd76-runs/ws3-batch42-20 --c3-witness-ms 2000
```

- 驱动日志：`round 1..20 OK`（单轮 instrumentation 8–17 s），`done: 20/20 rounds passed`，`failedRounds=0`。
- `manifest.txt`：`mode=reboot`、`rounds=20`、`firstRound=1`、`lastRound=20`、`preregisteredN=20`、`estimator=nearest-rank`、`gateMillis=5000`、`settleSeconds=0`；`bootIds=` 20 个 UUID。
- 每轮 `KD76_ENV` 都由宿主在 `am instrument` 前后各读一次 `/proc/sys/kernel/random/boot_id`，并记录 `hostBootCount`、`pidTarget`、`pidWorker`、`appsFrozen`、`loadBefore/After`（`tools/ci/run_kd76_rounds.sh:254-343,407`）。

## 2. boot 证明（20 轮互不相同；轮前=轮后）

| round | host boot_id (before = after) | app bootCount | host bootCount | pidTarget | pidWorker |
|---:|---|---:|---:|---|---|
| 1 | `ca038f79-0797-4158-af19-6ef3c613c443` | 3 | 3 | (空) | (空) |
| 2 | `fc9ad9b5-9c95-4aae-b53c-78cf02ea2043` | 4 | 4 | (空) | (空) |
| 3 | `e5b433ca-6fb4-4387-a775-f03897b9c344` | 5 | 5 | (空) | (空) |
| 4 | `e99ee9fe-08e4-4ae4-b395-9e6997dd398f` | 6 | 6 | (空) | (空) |
| 5 | `65212da2-f036-4a81-bc35-236775f73630` | 7 | 7 | (空) | (空) |
| 6 | `a78a8551-6c8a-47cc-a008-20f178820c09` | 8 | 8 | (空) | (空) |
| 7 | `d45ad5f0-864b-4d75-9ea0-a4cd3dc86211` | 9 | 9 | (空) | (空) |
| 8 | `0f4b4a17-e788-4b6c-b931-440e6f55ed00` | 10 | 10 | (空) | (空) |
| 9 | `28d77e85-230e-4a70-9bd4-ac9575270eb7` | 11 | 11 | (空) | (空) |
| 10 | `4a63ef38-8277-4141-b46e-75dc81b80733` | 12 | 12 | (空) | (空) |
| 11 | `322c33c8-f264-40db-9476-562fd1839e7e` | 13 | 13 | (空) | (空) |
| 12 | `c8f9d3d6-91a7-42fb-85f9-1794f964abba` | 14 | 14 | (空) | (空) |
| 13 | `da541b12-361c-4158-84a3-ca3984875ad8` | 15 | 15 | (空) | (空) |
| 14 | `7e90cdb7-59b9-494f-bdc8-820ab2d7d375` | 16 | 16 | (空) | (空) |
| 15 | `16a991f9-a0c6-43e7-ab60-cf6e295e4361` | 17 | 17 | (空) | (空) |
| 16 | `6a586bb3-8d29-4672-8d97-2914ca4f9ec4` | 18 | 18 | (空) | (空) |
| 17 | `9fcfa0c6-0242-4c10-abed-f8e4981012dc` | 19 | 19 | (空) | (空) |
| 18 | `f286067d-8cfa-4f10-9e10-58e2ae2ae3d8` | 20 | 20 | (空) | (空) |
| 19 | `254ed26d-75ef-4d20-8f05-1278a241f1a6` | 21 | 21 | (空) | (空) |
| 20 | `2940a887-9c0b-4898-8961-be5aee1c1e66` | 22 | 22 | (空) | (空) |

- 20 个 `boot_id` 全不同；每轮 before=after；`app bootCount` 与宿主 `settings get global boot_count` 逐轮相等（3..22 连续）。
- 测量前 `pidWorker` 全空（`:visual_js` 不存在）→ 冷启动形状成立；`pidTarget` 全空（app 进程也未被 BOOT_COMPLETED 留下）→ app 进程存在位无漂移。
- `appsFrozen=0` 全部 20 轮；`loadBefore` 采样 5.58/…（reboot 后 boot storm 属预注册 settle=0 条件，未作门）。

## 3. 逐轮原始样本（毫秒）

`KD76_SAMPLE ... status=SAMPLE`；`M = bind + warmup`。

| round | bind | warmup | readyGap | firstByte | total | **M** | bootCount |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1050 | 216 | 20 | 323 | 1609 | 1266 | 3 |
| 2 | 1346 | 314 | 30 | 473 | 2163 | 1660 | 4 |
| 3 | 708 | 219 | 9 | 265 | 1201 | 927 | 5 |
| 4 | 1245 | 222 | 9 | 233 | 1709 | 1467 | 6 |
| 5 | 924 | 215 | 24 | 350 | 1513 | 1139 | 7 |
| 6 | 1113 | 223 | 11 | 274 | 1621 | 1336 | 8 |
| 7 | 1672 | 217 | 12 | 343 | 2244 | 1889 | 9 |
| 8 | 738 | 111 | 9 | 555 | 1413 | 849 | 10 |
| 9 | 1140 | 217 | 12 | 666 | 2035 | 1357 | 11 |
| 10 | 1093 | 214 | 15 | 311 | 1633 | 1307 | 12 |
| 11 | 1272 | 312 | 9 | 328 | 1921 | 1584 | 13 |
| 12 | 883 | 204 | 16 | 650 | 1753 | 1087 | 14 |
| 13 | 1081 | 114 | 7 | 311 | 1513 | 1195 | 15 |
| 14 | 901 | 226 | 12 | 306 | 1445 | 1127 | 16 |
| 15 | 1438 | 118 | 9 | 202 | 1767 | 1556 | 17 |
| 16 | 845 | 211 | 49 | 329 | 1434 | 1056 | 18 |
| 17 | 1265 | 206 | 12 | 432 | 1915 | 1471 | 19 |
| 18 | 1193 | 208 | 13 | 321 | 1735 | 1401 | 20 |
| 19 | 1093 | 309 | 17 | 477 | 1896 | 1402 | 21 |
| 20 | 1783 | 306 | 44 | 433 | 2566 | 2089 | 22 |

- 每轮 `total == bind + warmup + readyGap + firstByte`；`generation` 非空。
- 每轮 app 自报 `apkSha256=35e10339…`，与宿主 `manifest.txt` 的 `targetApkSha256` 逐轮相等；20 轮一致。
- 每轮 `fingerprint` 一致（见头表）；`sdk=34`、`pageSize=4096`、`abis=x86_64,arm64-v8a`、`versionName=0.2.0-localfirst`。

## 4. 汇总与判词

### 4.1 `summarize_kd76.py`（n=20，nearest-rank）

```text
samples: n=20/20 | G1: green
apkSha256: 35e10339c4606dabb17a8210763fba68a5d8acf1be67703793293b82d4bff1f8
bootCount(s): [3, 4, ..., 22]
host boot_id(s): n=20 unique=20
mode(s): ['reboot']
M: min=849 p50=1336 p90=1660 p95=1889 max=2089
sorted=[849, 927, 1056, 1087, 1127, 1139, 1195, 1266, 1307, 1336, 1357, 1401, 1402, 1467, 1471, 1556, 1584, 1660, 1889, 2089]
gate: M.p95 <= 5000 ms (READY_TIMEOUT 10000 / 2; never relaxed)
G2: PASS (M.p95=1889 ms <= 5000 ms)
G3: max=2089 ms (attribution required if max > 5000 ms)
```

### 4.2 判词由 `Kd76ReportVerdict` 直接生成（非 Gradle 探针）

为避免"脚本自行判 PASS"，本轮把**当前工作树**的 `app/src/main/kotlin/.../visualjs/Kd76ReportVerdict.kt` 用本地 Gradle 缓存中的 Kotlin embeddable compiler 编译，并以本次实际输入（n=20、g1Red=false、p95=1889）调用 `decide(...)`：

```bash
bash build/kd76-verdict-probe/run.sh
# n=20 g1Red=false p95=1889 -> verdict=PASS line=G2: PASS (M.p95=1889 ms <= 5000 ms) reasons=[]
# n=20 g1Red=false p95=5000 -> PASS；p95=5001 -> FAIL
# n=19 -> NOT_EVALUABLE (sample_short)；g1Red=true -> NOT_EVALUABLE (g1_red)；p95=null -> NOT_EVALUABLE (no_p95)
```

该输出与 `summarize_kd76.py` 对本次数据的判词逐字一致；脚本侧同口径规则由 `tools/tests/test_summarize_kd76.py` 固定向量钉住。**未运行 Gradle**（纪律：Gradle 只在门禁阶段串行运行）。

## 5. 29.5 s 链四段（S1–S4）

```text
C1 S-bind  budget=15000  n=20 min=708 p50=1093 p95=1672 max=1783
C2 S-warmup budget=10000 n=20 min=111 p50=216  p95=312  max=314
C3 host    budget=2500   witness=2000（KD-66 hard+500 上界，不是 C3 分布）
C4 death   budget=2000   UNVERIFIED（无 abandonForTest wall clock 实测）
sum of budgets = 29500 ms（supremum，不是实测值）
sum of measured max = UNVERIFIED（C4 无实测）
```

- 分段规则：分位不可加；不拼单例 29.5 s；C3 witness 不是 C3 分布（`tools/ci/kd76_chain_segments.py:139-172`）。
- 本轮实测覆盖 C1/C2；C3 只有 KD-66 witness；C4 未测 → 链的"四段实测"**不成立**，只能报 C1/C2 分布 + C3 witness + C4 UNVERIFIED。
- 既有批次 23/24 的 abandon 观察不是本次 20 轮同一轮集合/同一设备条件，按 R2 §4.3 不并入 `Σ 实测 max`。

## 6. M 与 READY_TIMEOUT 的关系（计划项 30 裁定）

- `M = S-bind + S-warmup`；`FIRST_CONNECT_TIMEOUT_MILLIS=15000` 约束 S-bind，`READY_TIMEOUT_MILLIS=10000` 约束 S-warmup；5 000 ms 门 = `READY_TIMEOUT/2`（预注册产品余量）。
- 本次 M.p95=1889（S-bind p95=1672、S-warmup p95=312），两段都远低于各自预算；**不改 10 s 阈值，不改 5 s 门**。
- S-warmup p95=312 ms 仅说明观测样本远离 10 s 预算，**不能**据此声称 READY_TIMEOUT 可降到 5 s。

## 7. 未验证 / 边界（如实）

1. **单批 n=20**：R3 A6 要求"两批独立冷启动均 G1 绿"作 closure；本证据是单批 20 轮（统计上与 10+10 等价，但 A6 未满足）→ KD-76 不能据此关闭。两批路径已由 `--first-round` 支持（`run_kd76_rounds.sh:99-106,431`），本批未跑批 B；本轮用同一 20 份 XML 拆成 1–10 / 11–20 两目录做合并检查，`summarize_kd76.py a b` 输出与单目录逐字相同（p95=1889、exit 0），证明跨目录合并路径可用（不是两次独立设备批）。
2. **C4 无实测**；C3 只有 KD-66 witness。29.5 s 链不是任何单例实测值。
3. **APK 构建时点**：app APK 构建于 2026-11-03 03:54、androidTest APK 01:00；F11 的 legacy 入口 `@Ignore` 源码改动晚于该构建（06:01），但该改动只影响被阻断的 legacy 入口，`readRoundFromInstrumentationArg`/`runOneRound` 测量路径与当前源码一致（`git diff` 仅 +20/−3 行，全部在 legacy 入口/注释）。严格"源快照=APK"绑定需在门禁阶段重建后重跑。
4. **x86_64/API 34 模拟器口径**：未覆盖 arm64/真机/16 KB page；`abis` 含 arm64-v8a 但本机运行的是 x86_64。
5. **settle=0 的 boot storm**：`loadBefore` 5.58 等是预注册条件的一部分，未设低负载门；两批条件漂移未评估。
6. **产品进程/UI 首请求、低内存、arm64** 仍 UNVERIFIED（不随本证据消失）。
7. **"单轮入口采集 boot_id"的口径**：按 R3 §2.3.6 的命名纠正，app 侧入口继续采 `Settings.Global.BOOT_COUNT`（`VisualJsConnectStageCalibrationInstrumentedTest.kt:960-969`）；`/proc/sys/kernel/random/boot_id` 由宿主驱动在每轮前后采集（app 的 `untrusted_app` 域不依赖它）。没有宿主 `KD76_ENV` 的 XML（如批次 41 单轮）会被汇总判 `NOT EVALUABLE`。

## 8. 回链

- 计划：`.zcode/plans/plan-2026-11-04-batch-42.md` §2 WS3 25–33。
- 研究：`docs/research/2026-11-04-kd76-20-rounds.md`（R3）。
- 工具：`tools/ci/run_kd76_rounds.sh`（boot_id/--first-round）、`tools/ci/summarize_kd76.py`（宿主 boot_id 门）、`tools/tests/test_summarize_kd76.py`（18 例绿）。
- 台账：`docs/known-defects.md` KD-76 段（批次 42 Progress 块）。
