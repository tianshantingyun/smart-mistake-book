# 批次 35 设备/裁决证据索引（2026-10-30）

- 记录：批次 35 记录文件**未在树**（`docs/research/` 现仅有 baseline 与 device-criteria）；本索引是 E-ID 映射权威，记录落盘后按规范 §7.2 以 `E-ID + path:line` 回链
- 计划：`.zcode/plans/plan-2026-10-30-batch-35-dual-backend-closeout.md`（§1 任务 6/7/8、15；§3 验收）
- 判据：`docs/research/2026-10-30-batch-35-device-criteria.md`；基线：`docs/research/2026-10-30-batch-35-baseline.md`
- 规范：`docs/research/2026-10-26-device-evidence-convention.md` v1（R4）
- 工作树 HEAD：构建/绿轮绑定 `902d7acce117c3e33905b1ec5ba9ea0d61a21f9c`；收尾 `4341f5adcabe0ec45736d9e40cc11482ccf1fd1c`（期间他线 KB 两笔 **docs-only** 提交 `a3b8a534`/`4341f5ad`，不触 `src`；`git rev-parse HEAD` 实跑核对）
- 工作树：dirty（开工 751 项，多线在飞；本目录证据轨**零 git 写操作**）
- 设备：`emulator-5562`（API 34 / x86_64 / `getconf PAGE_SIZE`=4096 / fp `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys`；5584/5596 未触碰）——五值实跑见 E-01 §0
- 证据目录：`docs/research/evidence/2026-10-30-batch-35/`
- 本目录体积：**3 个 `.md`**；`find -printf '%s'` 实测 13,172 + 13,176 + 18,648 = **44,996 B**（`du -sk` 块径 52 KiB）；全 `docs/research/evidence/` = 1077 KiB < 8 MiB 硬预算（本会话 §3.2 命令实跑）
- 执行窗口：设备轨 2026-10-30 02:22–02:57（首轮）+ 03:08–03:33（重绑轮）；T7 KD 裁决轨 2026-10-30（只读核对 + pytest/静态门，零 Gradle/零设备）
- **命名偏离登记**：两份 step 文件沿用各轨命名（`step-device-track.md`/`step-t7-kd-rulings.md`，无 §2.2 的 `stepNN-` 前缀）；不重命名以免断链，映射以本索引 E-ID 为准

## 1. 环境

见 `step-device-track.md` §0（本批**无独立 `step00-environment.md`**：设备五值、HEAD、工具、构建前源计数均在该节；偏离登记见 §6）。

## 2. 产物身份

宿主 `sha256sum` 实测（`build/device-track-batch35/four-apk-sha*.txt`、`r04-two-apk-sha.txt`）；设备侧 `pm path` + `sha256sum` 与宿主逐一相等（`step-device-track.md:33` 重建轮、`:121` 恢复轮）。

| 轮次 | 产物 | 字节 | sha256 |
|---|---|---:|---|
| 重建轮（开工树） | `app-strictOffline-debug.apk` | 199,839,655 | `bb9485c6b287f9e3ef7fd61d1003b7a0f64f98dd5d1beb1404fb5671027f4c73` |
| 重建轮 | `app-strictOffline-debug-androidTest.apk` | 1,587,372 | `f58cf083f35cd5380bbcf8e9527053f9b0ec0835989e4f8d2f14f4c2970b9c46` |
| 重建轮 | `data-debug-androidTest.apk` | 210,278,405 | `5fa9717debe751282448d1a639acaed9eac9d74f32ae63b6e930561cff9f2212` |
| 重建轮 | `database-debug-androidTest.apk` | 209,338,093 | `00dddb3de28dc5f9f1f5e2fe12fbbf0f8275aef7577210500351a03628123b65` |
| 恢复轮（含 §6 两处源变化） | `app-strictOffline-debug.apk` | — | `1593b25ae33d10dc64f0c8bd179620eca4d87a6f83aa3342334666da42773dab` |
| 恢复轮 | `app-strictOffline-debug-androidTest.apk` | — | `f58cf083f35cd538…`（**不变**：源集移动不改 debug 测试 APK 字节） |
| 恢复轮 | `data-debug-androidTest.apk` | — | `0b710c5f808d22a8…`（他线 dense 内容） |
| 恢复轮 | `database-debug-androidTest.apk` | — | `394faa8673e790ef…` |
| R-04 release | `app-strictOffline-release.apk` | 155,749,587 | `70e21ee495ec18e0a6eb757cdb72477774885d91ee465fed94aa4f46e05dc951` |
| R-04 release | `app-strictOffline-release-androidTest.apk` | 1,118,085 | `2f794a2d60bc867401c84bd9d9d33ca4530995759955b7abeb8b0578d5b9b4f5` |

- 版本：`versionName 0.2.0-offline`（app strictOffline 变体；`app/build.gradle.kts` 实读）；R-04 主/测试包 K3 证书 SHA-256 同值 `4c5cf033b2faa904878e20ff8c60da950603946cb3f8383cf725f4f73fce8038`（E-01 §4.3）。
- `mapping/strictOfflineRelease/mapping.txt`：124,930,438 B，`grep -c " -> "` = **796,290** 真实改名行（本会话复核，不提交本体）。
- `71.json`（data 测试包 asset）：sha256 `4a13d9d0b5ee561f088142267afe0a97890745ff66d57ae2b1191bef47881d94`（本会话 python 复算 = E-01 §2 asset 门值 = E-02 §4.3 现树值）。

## 3. 步骤与结论

| E-ID | 步骤 | 判定 | 证据（本目录文件:行） |
|---|---|---|---|
| E-01 | 设备轨全程：环境再登记→一致性门→重建重装→asset/dex 门→18 套件两轮→签名/worker→R-04 K1–K6→恢复 debug→树/他线登记 | 18 套件两轮全绿；K1–K5 PASS；**K6 RED（阻塞）** | `step-device-track.md:145`（§8 结论） |
| E-02 | T7 KD 裁决轨：KD-80 复核回链 + KD-81 关闭 + A-08 口径/反向虚报清理 | KD-80 关闭维持；KD-81 关闭 | `step-t7-kd-rulings.md:1`（§1–§3） |

## 4. 判据覆盖（门/KD ↔ E-ID + path:line）

| 门/KD | 结果 | 证据 |
|---|---|---|
| 环境五值再登记 | 完成 | E-01 `step-device-track.md:13-15` |
| 一致性门 V=71 P=71 + S=71 静态比对 | `RESULT=PASS`/`EXIT=0`，PLAN_HASH=JSON_HASH=`714c0ecb85ee5edf74f0013740148138` | E-01 `step-device-track.md:22-26`；E-02 复跑 `step-t7-kd-rulings.md:86-94` |
| 源快照门（before/after/r04/rerun/restore 五窗 diff） | 全部 0 字节（本会话 `wc -c` 复核） | E-01 `step-device-track.md:32`、`:69`、`:89` |
| 四包构建 + 安装 sha 对齐 | BUILD_EXIT=0（43s）；4× Success；设备=宿主 | E-01 `step-device-track.md:31-33` |
| asset 门 71.json | sha `4a13d9d0…` | E-01 `step-device-track.md:34` |
| dex 符号门（三包 DEX OK + 批次 35 追加 9 符号） | 全过 exit=0；唯一 MISSING=判据转抄笔误 CasUse↔源码 CasUses（源码实名在 `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualSnapshotMigration_69_70InstrumentedTest.kt:102`） | E-01 `step-device-track.md:35-40` |
| 18 套件两轮逐套件 OK | 34/2/5/9/5/15/4/3/12/12/16/24/16/14/2 + 1/6/27，全部=构建前源计数（本会话逐 step*.log 复核） | E-01 `step-device-track.md:44-65`、`:17-18` |
| 9 类崩溃签名 | 两轮 18/18 份 logcat 全 0（本会话对 18 份重绑轮 logcat 实扫全 0）；唯一非零=r04-k6-logcat.txt（6，K6 本体） | E-01 `step-device-track.md:67`、`:113-115` |
| worker 生命周期 8 例 | 方法名各 ×2 在 `-r` 流且套件绿 | E-01 `step-device-track.md:68` |
| R-04 K1 红基线（签名门 Permission Denial，exit=1；R8 签名 0） | PASS（红基线成立） | E-01 `step-device-track.md:76-81` |
| R-04 K2 release 测试轨构建（首跑红→源集逐字节移动→同窗重建绿） | BUILD_EXIT=0（3m12s） | E-01 `step-device-track.md:83-91` |
| R-04 K3 证书一致 | 两包 SHA-256 逐字相同 `4c5cf033…` | E-01 `step-device-track.md:93-96` |
| R-04 K4 keep 规则门（脚本权威） | `python tools/ci/check_r04_keep_rules.py` 0 hits、exit=0（本会话复跑）；裸 grep 唯一命中=`app/r04-test-keep.pro:17` 注释（本会话实读确认）；mapping 796,290 改名行 | E-01 `step-device-track.md:98-102` |
| R-04 K5 so 双 ABI + 测试类未改名 | arm64-v8a 924,936B + x86_64 911,248B；dex 计 275（本会话读 `r04-k5b-count.txt`=275） | E-01 `step-device-track.md:104-107` |
| R-04 K6 设备两例往返 | **RED**：`shortMsg=Process crashed`，签名计 6（`NoClassDefFoundError Landroidx/tracing/Trace;` + `ClassNotFoundException androidx.tracing.Trace` + `NoSuchMethodError j$/util/g <init>(Ljava/util/Map;)V`） | E-01 `step-device-track.md:109-116` |
| 步 5 回归 + 步 6 恢复 debug 态 | BUILD_EXIT=0；2× uninstall + 4× Success；设备 sha=宿主；重绑轮全绿 | E-01 `step-device-track.md:118-121` |
| KD-80 四条复核（C1/C2/C3/C4） | 关闭维持（closed 2026-10-29 批次 34；2026-10-30 批次 35 复核确认） | E-02 `step-t7-kd-rulings.md:10-21` |
| KD-81 关闭（db+wal 进身份、`-shm` 排除） | closed 2026-10-30 批次 35；T5b 在树（`tools/tests/test_split_gate.py:829`）；pytest 37 passed（本会话复跑 37 passed in 2.32s） | E-02 `step-t7-kd-rulings.md:23-29`、`:44-48` |
| A-08 口径 + 反向虚报清理 | "JS 半边已落"+入口侧半边归任务 12；`known-defects.md`/`REFACTOR-MASTER-LINE.md:4`/清单 v3 补记 7 已落 | E-02 `step-t7-kd-rulings.md:31-38` |
| 他线并发 + 本轨树改动登记 | dense 14 文件（他线热区）；`DebugModelFailureCaptureInstrumentedTest.kt` 逐字节移动（sha `f55fd14f…` 前后同，本会话复算） | E-01 `step-device-track.md:134-143` |

## 5. 本批裁决结论（任务交办的三组判定）

1. **KD-80：关闭维持**（closed 2026-10-29 批次 34；2026-10-30 批次 35 复核确认）。四条（80-C1 34/34、80-C2 rowCounts 逐名设备侧 `OK (2 tests)` + 静态 `RESULT=PASS`、80-C3 设备 9/9 + JVM 12/0+17/0 口径保留、80-C4 5/5）逐条回链成立（E-02 §1）；静态半边与设备半边对同一份 schema 字节（71.json sha `4a13d9d0…` 逐字节相同）。**建议主会话在 `docs/known-defects.md` 复核块后按关闭口径归档**（块已在树：`docs/known-defects.md` KD-80 节末"裁决复核与回链（2026-10-30 批次 35）"）。
2. **KD-81：关闭**（closed 2026-10-30 批次 35）。按主会话裁定"内容身份 = db+wal、`-shm` 显式排除"落台账；关闭条件 ① 满足（`export_split_eval.py:78/:107-135` 身份公式 + `:669` 用例）、② 按裁定满足（排除口径）、③ AUD-08 设备复跑按裁定不再是关闭前置；T5b 在树（`test_split_gate.py:829`）且 37 项回归绿（本会话复跑 `python -m pytest tools/tests/test_split_gate.py -q` → 37 passed，EXIT=0）。标题与关闭块已在树（`docs/known-defects.md` KD-81 节）。残留（不阻塞）：身份无锁/不绑定读事务的 ABA 上限已登记；`docs/current/visual-generation-contract.md:208` 过时句归契约轨回改（docs/current 热区本批不改）。
3. **R-04：K1–K5 绿、K6 红，R-04 不判通过，按"阻塞"移交实现轨/主会话**。K6 归因：release 主包 R8 收缩掉 test 运行时所需的 `androidx.tracing.Trace`（`NoClassDefFoundError` + `ClassNotFoundException`），且 test 侧 desugared-library 与主包错位（`NoSuchMethodError j$/util/g <init>(Ljava/util/Map;)V`）——"test dex 套用主包同一混淆"路线在本仓现有 keep 规则组合上不成立（D 类形态）。处置遵守判据 K6 失败处置：不删例、不换 internal 变体、不引 `-dontobfuscate`、不改判据；扩 `app/r04-test-keep.pro` 属实现轨（该文件本批已是他线 M，正被迭代），本轨不越权。K6 两例未在 release 轨上执行过（启动即崩），不构成对用例本身的判定。

## 6. 缺口、偏离与更正登记

1. 无独立 `step00-environment.md`：环境五值/HEAD/工具/源计数在 `step-device-track.md` §0（`:10-18`），内容齐备、位置偏离 §2.2；如需独立文件可后补，不覆盖既有。
2. 命名偏离 §2.2（见头部登记）；两文件为各轨原始交付物，索引不改写其内容。
3. **K2 计数口径更正**（更正 E-01 `step-device-track.md:86` "15 处 Unresolved reference"）：`r04-step2-build.log` 精确计 **13 行 `Unresolved reference`** + 2 行级联类型错（`:382` Return type mismatch、`:383` Cannot infer type，同文件同因）= **15 行 `e:` 编译错**，全部指向 `DebugModelFailureCaptureInstrumentedTest.kt` 引用的 debug-only 类（`grep -cE "DebugModelFailureCapture|TestCaptureModelFailuresReceiver"` = 15，本会话实跑）。红因与处置结论不变；原始行号 `:370-384` 可复跑核对。
4. 首轮 18 步原始日志被重绑轮 `tee` 覆盖（同文件名），盘上 step*.log 为重绑轮（绿绑定轮）；首轮数字为会话转录（E-01 `:70`）。
5. 未验证项沿用 E-01 §8（`:149-155`）：生产签名 UNVERIFIED（一次性本地 key）；arm64 真机面未测；P-12③ 设备例未落树未跑；`PROGRAM_FUSED` 行号现值未单独核对；他线 dense 14 文件功能正确性归 dense 轨。E-02 §6（`:148-150`）：四套设备结果/JVM 半边未重跑（设备绿绑定源快照）；80-C3 JVM 口径沿用批次 33。
6. K6 两例未在 release 轨执行（见 §5.3）；R-04 收口状态=K1–K5 绿 + K6 阻塞。

## 7. 回链检查

- [x] 脱敏扫描（R4 §4.4，本会话对整个 `2026-10-30-batch-35/` 实跑）：secrets / credential assignments / emails / home paths 四类 **0 hits**
- [x] 体量：3 个 `.md`、约 39 KiB；单文件均 < 64 KiB / 400 行；全 evidence 树 1077 KiB < 8 MiB（§3.2 命令实跑）
- [x] E-ID 全批唯一（E-01/E-02），每条结论有 `path:line`；`build/` 只以 raw-log/本机材料身份出现，不作关闭证据
- [x] KD 裁决块已在树核对（编码安全复验）：`docs/known-defects.md` KD-80 复核块、KD-81 标题 `## KD-81 (closed 2026-10-30 批次 35`、关闭块；`docs/REFACTOR-MASTER-LINE.md:4` 关键限定句已更新
- [ ] 批次 35 记录 §4 回链：**记录未落盘**，落盘时按本索引 E-ID 引用（留待记录员）
- [x] 索引内引用文件存在性：链接检查（R4 §7.5 命令变体：`grep -oE 'step-…\.md:[0-9]+…'` 逐条 `test -f` + 行数比对）写入后实跑，输出无 MISSING / 无 LINE OUT OF RANGE
