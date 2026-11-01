# E-01 构建前一致性门 → 重建 → 安装 → dex → schema 资产

| 字段 | 值 |
|---|---|
| evidence-id | E-01 |
| convention-version | 1 |
| batch | 2026-10-29-batch-33 |
| step | 判据 §4（硬门：先重建重装） |
| gate-or-kd | 判据 §4 / 旧包排除（D 类） |
| verdict | PASS |
| executed-at | 2026-10-29T14:44:47+08:00（构建前门）→ 14:45:16（构建完成） |
| operator | 设备验证轨（子代理） |
| head | `6abd807fa73d9084ef629d54015bd4f725c94784` |
| worktree | dirty（223 M / 477 ??，仅记录） |
| device-serial | `emulator-5562` |
| device-avd | `test_device`（在线名） |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `<repo>/app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` 等四包 |
| apk-sha256 | 见 §3 |
| apk-version | `0.2.0-offline` |
| exit-code | 0 |
| raw-log | `build/device-track-batch33/build.log`（本机，不入仓；32s，BUILD SUCCESSFUL） |
| record-ref | `docs/research/2026-10-29-batch-33-record.md` §4 |
| sanitized | 是（§6） |

## 1. 目的与判据

证明：① schema 不是中间态（V=71 / P=71 / 71.json 已跟踪 / 70→71 迁移已注册）；
② 设备上跑的是**当前树重建**的 APK，不是批次 32 的 11:14 包；③ dex 里有本批要跑的门符号。

## 2. 命令（可复跑）

```bash
# 1) 构建前一致性门
V=$(sed -n 's/.*STUDY_DATABASE_VERSION = \([0-9][0-9]*\).*/\1/p' \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt | head -1)
P=$(sed -n 's/.*const val SCHEMA_VERSION: Int = \([0-9][0-9]*\).*/\1/p' \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt | head -1)
echo "V=$V P=$P"
test -f core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json
git ls-files --error-unmatch core/database/schemas/.../71.json
grep -q "VISUAL_SNAPSHOT_IDENTITY_MIGRATION_70_71" core/database/src/main/.../StudyDatabase.kt

# 2) 源快照 → 构建 → 源快照
find app/src core/data/src core/database/src -type f -print0 | sort -z \
  | xargs -0 sha256sum > build/device-track-batch33/src-sha-before.txt
./gradlew --console=plain :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest \
  :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest
find app/src core/data/src core/database/src -type f -print0 | sort -z \
  | xargs -0 sha256sum > build/device-track-batch33/src-sha-after.txt
diff -q build/device-track-batch33/src-sha-before.txt \
        build/device-track-batch33/src-sha-after.txt && echo "SOURCE STABLE"

# 3) 安装 + 字节核对
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s "$S" install -r -t app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
$A -s "$S" install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
$A -s "$S" install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$A -s "$S" install -r -t core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
for p in com.tingyun.smartmistakebook.offline com.tingyun.smartmistakebook.offline.test \
         com.tingyun.smartmistakebook.core.data.test com.tingyun.smartmistakebook.core.database.test; do
  path=$($A -s "$S" shell pm path "$p" | tr -d '\r' | sed 's/^package://')
  $A -s "$S" shell sha256sum "$path"
done
```

- 退出码：构建前门全 0；Gradle `BUILD SUCCESSFUL in 32s`（14 executed / 313 up-to-date）。
- 源稳定：`SOURCE STABLE`（before == after）。

## 3. 产物身份（宿主 = 设备）

```text
b8b2e9d85b377d5b7d559affba6113c36cc243b497e22006c91d162207e55bed  app-strictOffline-debug.apk
2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19  app-strictOffline-debug-androidTest.apk
36f79b195e6c43d7841a85da3385887eb092518d1a0aa0f7462a619fb06f7627  data-debug-androidTest.apk（最终，含 C4）
a46960416329cb26de37924edaaed3b7bb643e77b9a99b467080cd7555a778e0  database-debug-androidTest.apk
```

设备侧 `pm path` 后 `sha256sum` 四值逐字相同（`step01` 首轮 data 包为 `7f58842e…`；
C4 落码后重建为 `36f79b19…`，已重新安装并复核；见 `step04-c4-visual-reference.md`）。

## 4. dex 符号门与 schema 资产

```bash
need() { apk="$1"; shift; for s in "$@"; do unzip -p "$apk" 'classes*.dex' | grep -a -q "$s" \
  || { echo "MISSING [$s] in $apk"; return 1; }; done; echo "DEX OK: $apk"; }
```

```text
DEX OK: app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
DEX OK: core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
DEX OK: core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
```

符号清单与判据 §5.2 逐项一致（含 `snap-staging-keeper` / `snap-pinned-staging` /
`snap-linked-staging` / `snap-abandoned-staging` / 第 5 例方法名，以及 painter/PDF/parity 负控
`n09…n13`、`pdf*` 与仓库 5 个预算/evict 方法）。C4 落码后另跑一次：

```text
DEX OK: C4 symbols present
```

schema 资产：`unzip -l database-debug-androidTest.apk | grep -E 'StudyDatabase/[0-9]+\.json$' | wc -l` = 71；
`StudyDatabase/71.json` 存在。

## 5. 失败 / 跳过 / 异常

无。安装四次均 `Success`；未出现 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。

## 6. 脱敏与去噪

- 删除：Gradle 进度行（`> Task …`）只保留汇总；无 ANSI。
- 保留：命令、退出码、四包 sha、dex 结论。
- 扫描：人工通读，无密钥/凭据/用户目录。
- 二进制/截图：无。

## 7. 判定

PASS。构建前一致性门等值（V=71/P=71），源在构建窗口内稳定，四包为当前树重建并安装成功，
宿主与设备 sha 逐字节一致，dex 与 schema 资产门均通过。未覆盖：JVM 半边（不在本轨权限）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch33/build.log`、`build-c4c.log`、`src-sha-*.txt`、`apk-sha*.txt`
- 生成命令：见 §2
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
