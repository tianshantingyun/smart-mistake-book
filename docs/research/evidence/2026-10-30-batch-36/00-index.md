# 批次 36 设备证据索引（2026-10-30）

- 记录：`docs/research/2026-10-30-batch-36-record.md`（**未落盘**；本索引是 E-ID 映射权威，记录落盘后按 R4 §7.2 以 `E-ID` + `path:line` 回链）
- 计划：`.zcode/plans/plan-2026-10-30-batch-36-v01-r04-k6.md`（§1 任务 1–5、§3 验收）
- 判据/研究：`docs/research/2026-10-30-r04-k6-keep-rules.md`（K6 两机制、最小 keep、重跑判据与回滚）；基线：`docs/research/2026-10-30-batch-36-baseline.md`
- 规范：`docs/research/2026-10-26-device-evidence-convention.md` v1（R4）
- 工作树 HEAD：`4f11cd5432e6c259038fc92bd2e975aee8d304b4`（`git rev-parse HEAD` 本会话实跑）
- 工作树：dirty **706** 项（`git status --porcelain | wc -l` 本会话；ask 附录 §0 记 689，差 17 项未逐项追认 → UNVERIFIED；本目录证据轨**零 git 写操作**）
- 设备：`emulator-5562`（API 34 / x86_64 / `getconf PAGE_SIZE`=4096 / fp `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys`）；5566/5584/5596 同时在线、未触碰
- 证据目录：`docs/research/evidence/2026-10-30-batch-36/`
- 本目录体积：**7 个 `.md` / 51.1 KiB（52,370 B）**（`find … -printf '%s'` 实测，见 §7）；全 `docs/research/evidence/` = 1203 KiB < 8 MiB 预算
- 执行窗口：**设备轨 2026-10-30 08:17–08:59**（20 步 + R-04 K1–K4，原始日志 `build/device-track-batch36/` 64 文件）；**落盘会话 22:35–22:5x**（只读复核 + keep 门复跑；零 Gradle、零 `am instrument`、零设备写）

## 1. 环境

见 `step00-environment.md`（设备四台在线、5562 五值、HEAD/工作树/schema、**源快照 1548 文件 sha256 全等**）。本批有独立 `step00`（批次 35 的偏离已消除）。

## 2. 产物身份

| 轮/用途 | 产物 | 字节 | sha256 | 备注 |
|---|---|---:|---|---|
| 设备轨 debug | `app-strictOffline-debug.apk` | —（未读） | `4e964e599754759ea669af39ad28b435592c0239b40be16641bed0cb160fff99` | `build-debug.log:422` `BUILD SUCCESSFUL in 38s` |
| 设备轨 debug | `app-strictOffline-debug-androidTest.apk` | — | `1c35a969a766645ff02431859019e2db97e771d5416c0359dc86cca7c8bc00ae` | 同上 |
| 设备轨 debug | `data-debug-androidTest.apk` | — | `269e2a9a0fb750e2e945e6231111d5fc477f00af5bb1eb40248a294415e4f5b3` | 同上 |
| 设备轨 debug | `database-debug-androidTest.apk` | — | `6af00fa1190db4dd1dbbcc5160f00b6dfe9c8b9d6124766af77becee8e366b70` | 同上 |
| R-04 release | `app-strictOffline-release.apk` | 155,749,569 | `1c7fe3f7892efb4392a3dd0985a56f0908394152d5ba91435a0ee17244b6b927` | 08:57；本会话 `sha256sum` 复算同值 |
| R-04 release | `app-strictOffline-release-androidTest.apk` | 1,118,864 | `03d1d426d0cd083497a12ba07d3301bc8189182cef976acde2c6873c9bdf9b3e` | 08:59；同上 |
| asset 门 | `71.json`（data 测试包内） | — | `4a13d9d0b5ee561f088142267afe0a97890745ff66d57ae2b1191bef47881d94` | `asset-71-sha.txt` |

keep 三文件（R-04 窗口，08:17 与 08:59 两读逐字相同）：

| 文件 | 行数（HEAD → 工作树） | sha256（工作树） |
|---|---|---|
| `app/r04-test-keep.pro` | 40 → 74（+34） | `35cd987f7de5ac57299207fdd5ed32afaa8bd1abfe613a1119e866c779d3f037` |
| `app/proguard-rules.pro` | 19 → 39（+20） | `38901b590be8bf7d5d75ce682812ec164c87511319d756e59e0e5c3152e522ad` |
| `app/build.gradle.kts` | 未改（干净） | `1bcc02473d885dc5015249e7ed8af28f8c383d495862a0712b03a66fec65d719` |

`versionName` 本会话未读；R-04 一次性 keystore 与口令载体在 `build/`（gitignore，不入仓；本索引不记录任何值）。

## 3. 步骤与结论

| E-ID | 步骤 | 门/任务 | 设备 | 判定 | 证据（本目录文件:行） |
|---|---|---|---|---|---|
| E-00 | 环境与源快照再登记 | 信息性 | `emulator-5562` | PASS | `step00-environment.md:76`（§3 源与库绑定） |
| E-01 | 18 套件回归（步 01–18） | 无回退 | `emulator-5562` | PASS | `step01-suite-regression.md:51`（§3 表） |
| E-02 | V-01 全链（步 19） | 计划任务 5 | `emulator-5562` | **FAIL**（4 例 2 失败） | `step02-v01-chain.md:46`（§3 逐例） |
| E-03 | A-10 设备真库边界（步 20） | 计划任务 3 | `emulator-5562` | **PARTIAL**（3 例 1 失败） | `step03-a10-budget-device.md:45`（§3 逐例） |
| E-04 | R-04 K1–K6 | R-04 / 计划任务 1–2 | `emulator-5562` | **PARTIAL**（K1–K4 绿；K5 未按判据；**K6 无执行证据**） | `step04-r04-k1-k6.md:62`（§3、§5 裁决 `:97`） |
| E-05 | A-10/A-11/A-12 JVM 边界与 golden | 计划任务 3 | —（JVM） | PASS（5/15/7/42，0 失败） | `step05-jvm-boundaries.md:44`（§3 表） |

## 4. 判据覆盖（门/任务 ↔ E-ID + path:line）

| 门/验收项 | 结果 | 证据 |
|---|---|---|
| 18 套件不回退且计数=源 `@Test` | PASS：34/2/5/9/5/15/4/3/12/12/16/24/16/**15**/2/1/6/27，逐套件相等；skip 余量 0 | E-01 `step01-suite-regression.md:57-74` |
| 九类崩溃签名（20 份 logcat） | 20/20 全 0（本会话逐文件计数） | E-01 `step01-suite-regression.md:77` |
| 源快照 before/after diff | 空（`r04-src-sha-diff.txt` 0 B；本会话 `sha256sum -c` 1548/1548 通过） | E-00 `step00-environment.md:83`；E-04 `step04-r04-k1-k6.md:72` |
| DB schema 71 | `StudyDatabase.kt:139` = 71 | E-00 `step00-environment.md:82` |
| 新增设备用例必跑 | V-01 FAIL（2/4）、A-10 PARTIAL（2/3）——已跑，红因已归因本批夹具 | E-02 `step02-v01-chain.md:77-81`；E-03 `step03-a10-budget-device.md:74-78` |
| A-10 逐阈值（128 KiB/10 s/第 7 次布局/第 3 份最终图） | JVM PASS | E-05 `step05-jvm-boundaries.md:53` |
| A-11 三协议 × 5 向量 | JVM PASS（15/15） | E-05 `step05-jvm-boundaries.md:63` |
| A-12 可空上下文 golden 扩测 | JVM PASS（7/7；既有 42/42 无回退） | E-05 `step05-jvm-boundaries.md:67` |
| R-04 K1 红基线 | PASS（Permission Denial；R8 签名 0） | E-04 `step04-r04-k1-k6.md:66` |
| R-04 K2 release 测试轨构建 | PASS（`BUILD SUCCESSFUL in 56s`，`K2_BUILD_EXIT=0`） | E-04 `step04-r04-k1-k6.md:67` |
| R-04 K3 证书一致 | PASS（两包 `bb3e8dee…`） | E-04 `step04-r04-k1-k6.md:68` |
| R-04 K4 keep 门 0 命中 | PASS（脚本：`0 hits` / exit 0，本会话复跑） | E-04 `step04-r04-k1-k6.md:69` |
| R-04 K5 so 双 ABI / dex 计数 | so 半边 PASS（本会话补查）；dex 计数 **未跑** | E-04 `step04-r04-k1-k6.md:70` |
| R-04 K6 设备两例往返 | **无执行证据（无产物）** → 不可关闭 | E-04 `step04-r04-k1-k6.md:71` |
| keep 规则逐条"为什么需要" | 两条均有：`j$.**`、`androidx.tracing.Trace` 三成员 | E-04 `step04-r04-k1-k6.md:91-92` |
| 双门 exit=0（主会话可复现） | **本目录未跑**（Gradle 门由脚本串行执行；本会话只复跑 keep 门） | 缺口 §6.3 |

## 5. 本批裁决（R-04）

**R-04 不关闭；按"阻塞"登记（K6 无执行证据）。关闭建议不成立。**

- 本批 R-04 窗口只到 **K4**：`build/device-track-batch36/` 无 K5/K6 产物（`find build/device-track-batch36 \( -iname '*k6*' -o -iname '*k5*' \)` → 0；`find build -maxdepth 2 -iname '*k6*'` 仅命中 `batch35/` 两件）。
- K6 最近一次实测仍是**批次 35 红**（`j$/util/g` `NoSuchMethodError` + `androidx.tracing.Trace` `NoClassDefFoundError`/`ClassNotFoundException`，见 `build/device-track-batch35/r04-k6-logcat.txt:86`、`:92`、`:110`）。本批 keep 扩充针对这三类签名，但"规则写了"≠"K6 绿了"。
- 准确状态：**K1–K4 绿、K5 未按判据执行、K6 无执行证据 → R-04 阻塞（UNVERIFIED）**；不是新红，也不是绿。
- 解除条件（判据不放宽）：按 `docs/research/2026-10-30-r04-k6-keep-rules.md:134` 在 5562 上装两包跑 K6 两例，取 `OK (2 tests)` + 三类签名 0，并补 K5 dex 计数。
- 明确禁止：删例、换 internal 变体、加 `-dontobfuscate`、以 K1–K4 绿替代 K6。

## 6. 缺口、偏离与更正登记

1. **K5/K6 无批内产物** → R-04 无法关闭（§5）。K5 仅 so 双 ABI 由本会话只读补查成立，dex 计数缺失。
2. **记录文件未落盘**（`docs/research/2026-10-30-batch-36-record.md`）；本索引为 E-ID 权威，记录落盘后按 §7.2 回链。
3. **一致性门/S=71 未在本会话复跑**：DB schema 未变（`StudyDatabase.kt:139`=71）；ask 附录转录 `RESULT=PASS` 不作为本批证据，正式口径以门脚本串行轮为准。
4. **设备轨与落盘分两个会话窗口**（08:17–08:59 / 22:35 起）：本目录不是落盘会话内的设备执行记录，而是对 `build/device-track-batch36/` 原始日志的 R4 摘录；源快照全等（E-00 §3）使该摘录对现树成立。
5. **设备身份两处未采集**：AVD 名（`adb emu avd name` 未执行）、设备数据目录/包残留（未检查）→ UNVERIFIED。
6. **设备时钟偏移约 −8 h**：logcat 内 `10-30 00:22` ↔ 宿主 08:22（与 `2026-10-30-r04-k6-keep-rules.md:27` 的勘误一致）；本目录只写宿主窗。
7. **V-01 语义未覆盖**：套件注入进程内 `QuickJsVisualExecutionEngine`，非 app 侧隔离 `VisualJsClientEngine`（E-02 §5）；即使转绿也不满足判据 §2.1①。
8. **两个红例的修复与复跑**：修复后须新增 `step02b-*`/`step03b-*` 复跑（R4 §7.6，不覆盖旧文件）；不得放宽断言。
9. **生产签名 UNVERIFIED**（一次性本地 key）；arm64 真机未测（x86_64 模拟器）。
10. **工作树 706 与 ask 附录 689 差 17 项**未逐项追认；他线在飞内容功能正确性归各线。
11. 本目录**没有**任何 `build/` 路径被当关闭证据：`build/` 只以 raw-log/本机材料身份出现（§7 末条）。

## 7. 回链检查

- [x] 脱敏扫描（R4 §4.4，对本目录实跑四条）：secrets / credential assignments / emails **0 命中**；home paths **1 命中** = 附录 A 的扫描正则文本自身（`:114`，非路径），人工确认排除（结果见本文件附录 A）
- [x] 体量：7 个 `.md`、51.1 KiB（52,370 B）；单文件最大 11,431 B（`00-index.md`）/ 均 < 400 行；全 evidence 树 1203 KiB < 8 MiB
- [x] E-ID 全批唯一（E-00…E-05）；每条结论有 `path:line`；`build/` 只作 raw-log
- [x] 链接检查（R4 §7.5 变体，含目录内 `step…md:行` 与仓根/`docs/research` 外部引用）：逐条 `test -f` + `wc -l` 比对，**无输出**（无 MISSING / 无 LINE OUT OF RANGE；附录 B）
- [ ] 记录 §4 回链：记录未落盘（留待记录员）
- [ ] KD Progress 引用本索引：R-04 未关闭，**不得**在 KD 台账写 closed（留待台账轮）

## 附录 A：脱敏扫描输出

见 §7 勾选项；命令与输出：

```bash
E=docs/research/evidence/2026-10-30-batch-36
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' "$E" || echo "0 hits: secrets"
grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken)[[:space:]]*[:=][[:space:]]*[^ <]' "$E" || echo "0 hits: credential assignments"
grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$E" || echo "0 hits: emails"
grep -RInE 'C:.Users.[^ ]+|/home/[^/ ]+|/Users/[^/ ]+' "$E" || echo "0 hits: home paths"
```

## 附录 B：链接检查输出

见 §7 勾选项（命令为 R4 §7.5 的目录内变体：对本目录所有 `.md` 抽取 `step…md:行` 引用，逐条 `test -f` 与 `wc -l` 比对）。
