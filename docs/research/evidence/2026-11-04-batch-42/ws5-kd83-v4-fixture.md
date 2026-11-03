# WS5 / KD-83：真实 closure 4（schema 72）备份包夹具与设备恢复

| 字段 | 值 |
|---|---|
| evidence-id | E-42-WS5-01（旧标识 `B42-WS5-KD83-V4-FIXTURE-01` 保留为别名） |
| convention-version | 1 |
| batch | 2026-11-04-batch-42 |
| step | 计划 §1 R4 / §2 items 42–47（`.zcode/plans/plan-2026-11-04-batch-42.md:74-80`） |
| gate-or-kd | KD-83（closure 4 / schema 72） |
| verdict | **KD-83 关闭（合成合法 v4 夹具口径）**：JVM 先红后绿 8/8 + 既有 codec 35/35；设备 3/3（`OK (3 tests)`）；夹具字节 `24941ed7…` 与 golden 一致 |
| executed-at | 2026-11-03 09:20–09:36（本机时间；文档名按计划批次 42 的 2026-11-04） |
| operator | 备份兼容工程师（子代理），共享工作树 |
| head | `faab17f58b01eab78d5c08a9f843b3af3df2fecf`（本会话零 git 写） |
| device-serial | emulator-5584（API 34 / x86_64 / PAGE_SIZE 4096） |
| apk-sha256 | data.test `d88ec38f95672a5aa9f4eca6f4ec727e6d885355198303ef30954f3f2d0552b2`（§6 原文：设备 `sha256sum …/base.apk` 与宿主同值；该包为 data-debug-androidTest.apk） |
| exit-code | JVM 先红 `BUILD FAILED`（8 tests / 1 failed）→ 绿 `BUILD SUCCESSFUL`；`assembleDebugAndroidTest` 与 `am instrument` 未记录 shell exit code（设备读数为 `OK (3 tests)`） |
| raw-log | `tools/backup_fixtures/v4/`（`closure4-schema72-mask15.smbk` + `.sha256` ×3 + `v4-pack.golden.json`）；`build/v4-repro/`（复现副本，gitignored）；Gradle 日志未单独落盘 |
| record-ref | 计划 WS5 42–47；R4 `docs/research/2026-11-04-v4-pack-fixture.md`；批次 41 记录 F3/F14 |
| sanitized | 是（无密钥/账号/Cookie） |
| 输入 | 计划 §1 R4 / §2 item 42–47；`docs/research/2026-11-04-v4-pack-fixture.md`（R4 结论与脚本形状）；批次 41 记录 §9.3 F14 |
| 本会话实跑 | `python -I tools/ci/make_v4_pack_fixture.py`（build/verify/复现）、`sqlite3` CLI、Gradle 定向 JVM（`V4PackFixtureTest` + `SmbkArchiveCodecTest`）、`:core:data:assembleDebugAndroidTest`、`adb install` + `am instrument`（emulator-5584） |
| 未跑 | 全模块单测门/强门（门禁阶段）、CLI 之外的 CI 挂载、历史真实用户 v4 包（不存在） |

> 三类陈述分开：**实读**（路径:行号）、**实跑**（命令 + 输出）、**推断**（标明）。任何"生产会接受"的结论都有本会话的执行证据；没有的写 UNVERIFIED。

---

## 0. 结论

1. **v4 夹具不再是"同规则自证"**：键集来自 `git show 70509247` 的冻结字面量（并与 `72.json` 实体集双向核对），行数由 `sqlite3` CLI 与 CPython 两份实现分别 `COUNT(*)`，摘要由 Python `hashlib` 重算。生产 Kotlin 实现只与 golden 做**单向**比对。
2. **载荷是真 schema 72 库**，不是"从 v5 包删两张表"：`PRAGMA user_version=72`、91 张声明表 + 2 视图 + `room_master_table`、`error_cause_*` 不存在、`knowledge_search_material_feature` 存在且 0 行。恢复时 72→73 迁移真的跑了一遍。
3. **JVM 8/8 + 既有 codec 35/35 绿**（同一次 run），设备 **3/3 绿**（`OK (3 tests)`），设备 APK sha 与宿主一致。
4. **KD-83 可以关闭**，边界：夹具是"合成的合法 v4 包"（`appVersionName="v4-fixture"`），不是历史用户导出的包；写侧仍只写 closure 5 / schema 73。
5. 台账发现一条**既有文档错位**：`docs/known-defects.md:3294` 的"记录回链"正文是 KD-75 的内容，却挂在 KD-83 段下；本会话未改（超出本项落点），登记在此。

---

## 1. 为什么不是"从当前 v5 包去掉 error_cause + closureVersion=4"

| 判据 | 删表路径 | 本项做法 |
|---|---|---|
| 载荷 `user_version` | 仍是 73 | **72**（`build_schema_db` 末尾 `PRAGMA user_version = 72`） |
| `knowledge_search_material_feature` | 73 库里不存在，但 v4 的 91 键必须包含它 | **存在且 0 行** |
| 恢复时 72→73 迁移 | **不跑**（已是 73） | **真跑**（设备用例断言 `user_version=73`） |
| 能证明什么 | 只证明"选择块解析不崩" | 证明"v4 旧库经生产迁移链到 73、旧行不丢、新表为空" |

依据：`ErrorCauseMigration_72_73.kt` 的 `MIGRATION_72_73` 只 `createErrorCauseTables`（不 DROP material_feature）；73.json 仍是 92 实体。R4 §0.3/§4.1 同结论。

---

## 2. 生成与独立校验（实跑）

```text
$ python -I tools/ci/make_v4_pack_fixture.py build \
    --schema core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/72.json \
    --out-dir tools/backup_fixtures/v4 \
    --sqlite3 C:/Android/Sdk/platform-tools/sqlite3.exe
{"pack": "closure4-schema72-mask15.smbk", "packSha256": "24941ed731f8b55436c0fca58f11d3f047c462fcd9026c3d1f2eeddb22f763ad", "packBytes": 28108, "closureDigest": "49b5ab83fd2090b628d87d5cf59156c7c71413e940ae62b1a3ad45ab27f697d4", "nonZeroTables": {"attempt_submission": 2, "content_install_state": 1, "knowledge_node": 1, "problem": 1}}

$ python -I tools/ci/make_v4_pack_fixture.py verify \
    --pack tools/backup_fixtures/v4/closure4-schema72-mask15.smbk \
    --golden tools/backup_fixtures/v4/v4-pack.golden.json \
    --sqlite3 C:/Android/Sdk/platform-tools/sqlite3.exe
PASS {"pack": "closure4-schema72-mask15.smbk", "packSha256": "24941ed7…", "packBytes": 28108, "closureVersion": 4, "closureDigest": "49b5ab83…", "rowCountKeys": 91}
```

`verify` 逐项覆盖：条目集合 = {manifest.json, database.sqlite, checksums.sha256}；`files` 只有 `database.sqlite` 且 sha/大小相符；`checksums.sha256` 文本 = `"<sha> *database.sqlite"`；`user_version=72`；`quick_check=ok`；`foreign_key_check` 0 行；91 张声明表全在；无 `error_cause_*`；`material_feature` 在且 0 行；**声明行数 == 两份实现的实测**；`closureDigest` == Python 重算；与 golden 的 pack/digest/rowCounts/identityHash 全等。

**复现性（同机同工具链）**：

```text
$ python -I … build --out-dir build/v4-repro …
closure4-schema72-mask15.smbk SAME 24941ed731f8b554
forged-count.smbk              SAME 2c46a8ebb223b3db
dropped-nonempty.smbk          SAME ed177a16116287a0
v4-pack.golden.json            SAME 3a110cbbc0592ce8
```

边界：只证明"同机同 SQLite/Python 版本可复现"；跨工具链不保证，也不该当契约（R4 §0.6/L5）。

---

## 3. 冻结数字（golden 权威值）

| 项 | 值 |
|---|---|
| 黄金件 | `closure4-schema72-mask15.smbk`，**28,108 B** |
| pack sha256 | `24941ed731f8b55436c0fca58f11d3f047c462fcd9026c3d1f2eeddb22f763ad` |
| 载荷库 | 1,904,640 B，sha256 `ce54c8246ed491f511fa94dd9108f50f1c877edebf6837fc1bd455dbed3980b6` |
| `closureDigest` | `49b5ab83fd2090b628d87d5cf59156c7c71413e940ae62b1a3ad45ab27f697d4` |
| rowCounts 摘要 | `9d2a64fb05e1fed0813112dffc37803c2d6378e0efa7484f808c2caa23da980e` |
| 非 0 表 | `attempt_submission=2`、`content_install_state=1`、`knowledge_node=1`、`problem=1` |
| 库身份 | `identityHash=aa5e8e8e6536e0c1f3adcda6867814cb`（72.json）、91 表、2 视图 |
| N1 自洽伪造 | `forged-count.smbk` 28,109 B / `2c46a8ebb223b3db27b48956cc8b6ed8a0409f029753eecab6360bbf7b079627` |
| N2 被删表非空 | `dropped-nonempty.smbk` 28,590 B / `ed177a16116287a0543365e969035ab4a0574ea034353ba2f5830891d41f3217` |
| 工具链 | `sqlite3 3.50.6 (32-bit)`、Python `3.13.14`、生成器 sha256 `aecf65f4c2f3bd86b7106fa802ab827c3fab1fe0e8acb36cde43632295efeede` |

**跨会话交叉核对**：R4 研究（另一个会话、另一份脚本）给出的 `closureDigest=49b5ab83…` 与载荷库 sha `ce54c824…` 与本会话**逐位相同**；pack sha 不同（R4 28,537 B vs 本项 28,108 B）只因 zip 条目/压缩参数不同——这恰好证明"摘要可永久复算、包字节只在冻结时可信"（R4 §0.6）。

---

## 4. R4 的 L1–L8 判据对账

| # | 判据 | 本项证据 | 状态 |
|---|---|---|---|
| L1 | 键集三源一致（git v4 / 72.json / 生产规则表 / 冻结字面量） | `V4PackFixtureTest.frozenKeySetMatchesProductionRuleTableAndSchema72`：91=91=91，`ClosureRuleTables.v4.rowCountKeys == frozen == 72.json entities` | PASS |
| L2 | 载荷是真 72 库 | `verify` 实测 `user_version=72`、91 表、`material_feature` 存在 0 行、无 `error_cause_*` | PASS |
| L3 | 声明行数 = 实测（91 键全覆盖、非 0 ≥3 表、跨类） | 两份实现逐表相等；非 0 表跨 ISSUE_BOOK/MASTERY/ALWAYS | PASS |
| L4 | 摘要由独立实现重算 | Python `hashlib`；生产 `ClosureRuleTables.digest` 与 golden 相等（单向绑定） | PASS |
| L5 | 字节冻结 + 封存 + 工具链 | `.sha256` 三份 + `v4-pack.golden.json`（sha/尺寸/digest/rowCounts 摘要/工具链） | PASS |
| L6 | 负控可红 | N1 codec 绿/仓储红；N2 codec 红；翻位包红 | PASS |
| L7 | 生产规则表与冻结键集双向绑定 | L1 用例 + `trimOrderSize=83` / prefix / closureVersion / added / dropped | PASS |
| L8 | 合成件不伪装用户证据 | manifest `appVersionName="v4-fixture"`、`createdAt` 固定、golden `synthetic=true` + note | PASS |

---

## 5. JVM（先红后绿，实跑）

**红**（镜像副本尚未生成时；证明"副本缺失"这条守卫真的会红）：

```text
$ ./gradlew :core:data:testDebugUnitTest --tests "com.tingyun.smartmistakebook.core.data.backup.V4PackFixtureTest" --console=plain
V4PackFixtureTest > assetMirrorMatchesAuthoritySourceByteForByte FAILED
    java.lang.AssertionError at V4PackFixtureTest.kt:217
8 tests completed, 1 failed
BUILD FAILED
```

**绿**（生成 `--asset-dir core/data/src/androidTest/assets/backup/v4` 后，同一条命令）：

```text
BUILD SUCCESSFUL in 30s
```

一次合并 run 的结果（XML 实读）：

```text
SmbkArchiveCodecTest  tests=35 failures=0 errors=0 skipped=0
V4PackFixtureTest     tests=8  failures=0 errors=0 skipped=0
```

8 例覆盖：字节/封存/尺寸、codec 接受（manifest 自称 72/v3/91 键）、投影到 92 键、冻结键集与生产规则表 + 72.json 双向、生产 digest == Python golden、N1 codec 放行、N2 codec 拒且点名被删表、载荷翻位拒、镜像逐字节一致。

> **JVM 的能力边界（如实）**：`SmbkArchiveCodec` 只校验选择层与结构，**不做逐表 COUNT**；行数对账与 72→73 迁移只在设备用例成立。本文件不把"JVM 绿"说成"行数对账通过"。

---

## 6. 设备（emulator-5584，API 34 / x86_64）

```text
$ adb -s emulator-5584 shell getprop ro.build.fingerprint
google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys
$ adb -s emulator-5584 shell getprop ro.product.cpu.abi  → x86_64
$ adb -s emulator-5584 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
Success
$ adb -s emulator-5584 shell am instrument -w \
    -e class com.tingyun.smartmistakebook.core.data.backup.BackupV4PackRestoreInstrumentedTest \
    com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
OK (3 tests)          # Time: 3.348
$ adb -s emulator-5584 shell sha256sum <pm path base.apk>
d88ec38f95672a5aa9f4eca6f4ec727e6d885355198303ef30954f3f2d0552b2  …/base.apk   # 与宿主 APK 同值
```

| 用例 | 断言 | 结果 |
|---|---|---|
| `frozenV4PackRestoresToSchema73WithEmptyNewTablesAndNoRowLoss` | 先对 APK 内字节做 sha256 == golden；恢复成功；`receipt.selection.mask=15`、`receipt.databaseSchemaVersion=72`；下次开库后 **Room 连接 `readDatabaseVersion()==73`**、SQL `user_version=73`；`problem=1`、`attempt_submission=2`、`knowledge_node=1`、`content_install_state=1`；`error_cause_observation/binding=0`；`knowledge_search_material_feature=0`；`knowledge_search_feature=0` | PASS |
| `selfConsistentForgedRowCountIsRejectedByMeasuredCountsAndLiveDataStays` | N1 过 codec（JVM 已证）→ 恢复抛 `BackupRestoreException` 且文案含"逐表行数与声明不符"；原库哨兵行仍在 | PASS |
| `droppedTableWithRowsAndPayloadFlipAreRejectedBeforeSwap` | N2 在 codec 前门拒绝且文案含"已废弃表"；翻位包在交换前拒绝 | PASS |

**首次设备尝试失败（如实）**：3/3 红。两处是测试夹具错误，不是产品缺陷：① Room 懒开库，`open().close()` 不落文件 → `SQLiteCantOpenDatabaseException`；改为 `createLiveSchema73Database()` 先读一次。② 断言 `countMistakes()==1` 不成立——v4 最小载荷只有 `problem`、没有 `error_book_entry`，`countActiveMistakes` 按定义为 0；改为 `readDatabaseVersion()==73` + `countMistakes()==0`，**行不丢**的断言仍是 SQL 级 `rowCount(problem)==1`。没有删除或放宽任何"旧行不得丢"的断言。

---

## 7. KD-83 判据逐条对账

| # | 原判据 | 本项证据 | 判定 |
|---|---|---|---|
| ① | closure 4 包按 v4 规则校验并投影到 92 键（新增表 0） | `goldenPackProjectsToCurrentSelectionKeySpace` PASS | 满足 |
| ② | v5 算法算摘要的"伪 v4 包"被拒 | `SmbkArchiveCodecTest` 同名用例 PASS（35/35） | 满足 |
| ③ | v4 声明 v5 键空间 / closure ∈ {3,6} 被可读拒绝 | `SmbkArchiveCodecTest` 同名用例 PASS | 满足 |
| ④ | 恢复侧逐表 COUNT：新增表非 0、被删表非 0 均拒 | N1/N2 设备用例 PASS | 满足 |
| ⑤ | 真实 v4 包设备恢复成功且行数对账 | 设备用例 1 PASS（真 72 库、`user_version=73`、逐表行数） | 满足（合成夹具口径） |

**裁定：KD-83 关闭（2026-11-03 批次 42 WS5）。** 边界：①夹具是"合成的合法 v4 包"，不是历史用户导出的包——后者在本仓库/设备上不存在，无法取得；②本次只证明读侧 + 迁移，写侧仍只写 closure 5 / schema 73；③closure ≤3 仍明确拒绝（无冻结规则）。

---

## 8. 残余风险与 UNVERIFIED

| # | 项 | 说明 |
|---|---|---|
| R1 | 主机 SQLite 造的库在别的 Room 版本上 | 本次设备（API 34）迁移通过；其他 API/ROM 未跑 |
| R2 | `ClosureRuleTables.v4` 算术派生漂移 | 已有 JVM 双向绑定（L1/L7）；但 `tools/ci` 静态门尚未消费本 golden（只消费 v4 空库摘要） |
| R3 | 包字节跨机不可复现 | 明确不承诺；重新冻结必须显式提交（新 golden + 旧 sha 存档） |
| R4 | 夹具无资产（assetCount=0） | v4 路径的带图恢复未被覆盖（R4 §8 R5） |
| R5 | `docs/known-defects.md:3294` 错位 | KD-75 段落挂在 KD-83 下；本会话未改，交台账维护者 |
| U1 | 12 模块完整单测门 / 强门 | 未跑（门禁阶段）；本文件只报定向 JVM + 设备类 |
| U2 | `syncV4PackFixtures` 在 CI 上的行为 | 未挂 CI；任务由 `assembleDebugAndroidTest` 实测接入并执行 |
