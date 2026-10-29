# E-01 重建 / 重装 / dex 符号门 / schema 资产门

| 字段 | 值 |
|---|---|
| evidence-id | E-01 |
| convention-version | 1 |
| batch | 2026-10-29-batch-32 |
| step | 一（判据 §1.3、§3.4、§7.1–§7.4） |
| gate-or-kd | 设备轨构建身份 / schema 71 资产门 |
| verdict | PASS |
| executed-at | 2026-10-29T11:13:50–11:17:35+08:00 |
| operator | 设备验证轨（subagent，批次 32） |
| head | `0b4bdbbe` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | 四包（§2 表） |
| apk-sha256 | 见 §2（设备侧逐字节一致） |
| apk-version | `0.2.0-offline`（app）；测试包 versionName 空 |
| exit-code | 构建 `EXIT=0`、二次 DB 重建 `EXIT=0`、四次 install `Success` |
| raw-log | `build/device-track-batch32/step00-build.log`（424 行）/ `step00b-db-rebuild.log`（105 行）/ `src-sha-{before,after}.txt`（861 行） |
| record-ref | `docs/research/2026-10-29-batch-32-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

1. 批次 31 的设备 APK 与现树不同源（判据 §1.4），本批必须重建重装后才有资格产出绿轮证据。
2. 构建前 schema 71 不得处于中间态：`V=71`、`SmbkSelectionPlan.SCHEMA_VERSION=71`、`71.json` 存在。
3. 构建前后主源/测试源 sha256 清单必须相同（否则 = 构建早于源改动，判 D）。
4. dex 符号门证明设备上跑的是现树：负控、新增用例、隔离/flags 符号都在包内。
5. schema 资产门：`database-debug-androidTest.apk` 内 71 个 `StudyDatabase/*.json` 且含 `71.json`。

## 2. 命令与产物

```bash
# 构建前门
V=$(sed -n 's/.*STUDY_DATABASE_VERSION = \([0-9][0-9]*\).*/\1/p' core/database/src/main/kotlin/.../StudyDatabase.kt | head -1)  # 71
P=$(sed -n 's/.*const val SCHEMA_VERSION: Int = \([0-9][0-9]*\).*/\1/p' core/data/src/main/kotlin/.../backup/SmbkSelectionPlan.kt | head -1)  # 71
test -f core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json

# 源快照（861 文件），前后 diff
for d in app/src core/data/src core/database/src; do find "$d" -type f -print0 | sort -z | xargs -0 sha256sum; done > build/device-track-batch32/src-sha-before.txt
./gradlew --console=plain :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest \
  :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest
# BUILD SUCCESSFUL in 24s；EXIT=0
diff -q src-sha-before.txt src-sha-after.txt   # 无输出 = SRC-STABLE（快照 digest c8cfcb2c…8211）
```

| 产物 | 字节 | sha256（宿主＝设备） |
|---|---|---|
| `app-strictOffline-debug.apk` | 189532939 | `fb235c0aedf33da3c1c0c12932b9951e188f61799a92bd29a968225c0a3a438e` |
| `app-strictOffline-debug-androidTest.apk` | 1586361 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` |
| `data-debug-androidTest.apk` | 197769972 | `de27b081942695474091500d0d7f4a53e8b70e5b3106f1c152f3e1ae2325f15c` |
| `database-debug-androidTest.apk`（清理后重建） | 196829660 | `90be7c2bc54da6f68067c421e1cc0cc8538f892dab77e1514a0d3f8d89115dda` |

- 设备侧 `pm path` + `sha256sum` 四包与宿主逐字节一致（四行 sha256 同上）。
- app 测试包 sha 与批次 31 相同：其最新源 `VisualMeasureReplayPaintFlagsInstrumentedTest.kt` mtime 2026-10-28 23:30，APK mtime 2026-10-29 02:27，包内容确无变化；app 主包 sha 已变为 `fb23…`。不属“旧包”。

### 2.1 陈旧资产处置（必记）

首次重建后 `database-debug-androidTest.apk` 仍含 `assets/.../StudyDatabase/70.json.NOT_EXPORTED.md`
（现树无此源文件；来自 `build/generated/assets/copyRoomSchemasToAndroidTestAssetsDebugAndroidTest/`
与 `build/intermediates/assets/debugAndroidTest/` 的 02:34 增量残留）。处置：

```bash
rm -rf core/database/build/generated/assets/copyRoomSchemasToAndroidTestAssetsDebugAndroidTest \
       core/database/build/intermediates/assets/debugAndroidTest \
       core/database/build/intermediates/compressed_assets/debugAndroidTest
./gradlew --console=plain :core:database:assembleDebugAndroidTest   # EXIT=0
unzip -l .../database-debug-androidTest.apk | grep -c NOT_EXPORTED  # 0
```

清理不触及任何源文件；`NOT_EXPORTED` 不是本批任何测试输入。

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| 构建前 `V` / `P` | 71 / 71 | 必须相等 |
| `71.json` 存在 / `version` / `entities` / `views` | 是 / 71 / 90 / 2 | §6.1 |
| `71.json` identityHash | `714c0ecb85ee5edf74f0013740148138` | 非占位、setupQueries 含同值 |
| 生成 Impl | `RoomOpenDelegate(71, "714c0ecb85ee5edf74f0013740148138"` | 与 JSON 逐字同值 |
| 源 sha 前后 | 相同（861 文件） | 无“构建早于源改动” |
| 四包 install | 4× `Success` | 全部成功 |
| 设备侧 sha256 | 4/4 与宿主一致 | 必须 |
| dex 门 | 3× `DEX OK` | 无 MISSING |
| APK 内 schema json | 71 个且含 `71.json` | == V |
| APK 内 `NOT_EXPORTED` | 0（清理后） | 期望 0 |

## 4. 关键输出（有界）

```text
BUILD SUCCESSFUL in 24s
327 actionable tasks: 22 executed, 305 up-to-date
DEX OK: app/.../app-strictOffline-debug-androidTest.apk
DEX OK: core/data/.../data-debug-androidTest.apk
DEX OK: core/database/.../database-debug-androidTest.apk
V=71
71
HAS 71.json
```

## 5. 失败 / 跳过 / 异常

无。首次构建的陈旧 `NOT_EXPORTED` 资产已在 §2.1 处置并以新 sha 重建。

## 6. 脱敏与去噪

- 删除：Gradle 进度/警告（`w:` 编译警告不参与判定，保留计数 0 失败）。
- 保留：构建 summary、四包 sha、install 结果、dex/assets 门输出。
- 扫描：R4 §4.4 四条正则 → 0 hits。
- 二进制/截图：无。

## 7. 判定

PASS：现树重新构建、四包重装、设备 sha256 逐字节一致；dex 门与 schema 资产门均通过。批次 31 APK 未参与本批任何结论。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch32/step00-build.log`（424 行 / 26,942 B）、`step00b-db-rebuild.log`（105 行 / 5,907 B）
- 生成命令：见 §2。
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
