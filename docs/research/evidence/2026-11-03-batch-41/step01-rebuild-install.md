<!-- 体积自查：约 95 行；R4 上限 400 行 / 65536 B -->
# E-41-D01 重建四包 + 源快照 + 安装哈希核对（设备轨）

| 字段 | 值 |
|---|---|
| evidence-id | E-41-D01 |
| convention-version | 1 |
| batch | 2026-11-03-batch-41 |
| step | 设备轨步骤一（计划 ask 1） |
| gate-or-kd | 设备绿绑定源快照 + APK sha（计划 §4） |
| verdict | **PASS**：构建 exit=0；源快照 before→after diff=0（1287 文件）；4 包安装 4×Success；设备侧 sha 与宿主逐字节一致 |
| executed-at | 2026-11-03T00:45–00:48+08:00（UTC 2026-11-02T16:45–16:48Z） |
| operator | 设备轨（批次 41 工作流子代理） |
| head | `62522930bc8bae51be1523c954f39449e054b6af` |
| worktree | `git status --porcelain` = 584 条（他线在飞；只记录，不归因） |
| device-serial | emulator-5584（AVD `kb_test_device`，API 34，x86_64，4 vCPU，page-size 4096，KD-76 单轮记录 bootCount=2） |
| apk-sha256 | 见 §3（localFirst 四包 + strictOffline 两包） |
| exit-code | 构建 0；安装 4×Success |
| raw-log | `build/device-track-batch41/{env.txt,build.log,build-exit.txt,src-sha-before.txt,src-sha-after.txt,src-snapshot.diff,apk-sha256.txt,device-apk-sha256.txt,install.log}`（gitignored） |
| record-ref | 计划 `.zcode/plans/plan-2026-11-03-batch-41.md:115-121`；批次 40 记录 §9.1-4 |
| sanitized | 是（仅模拟器 serial；无密钥/凭据） |

## 1. 目的

把本 ask 的全部设备结论绑定到**当前树快照 + 明确 APK sha**；不跑 `:app:connectedLocalFirstDebugAndroidTest`（计划 §4 禁止）。

## 2. 构建命令与源快照

```bash
./gradlew --console=plain \
  :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest \
  :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest
```

- `BUILD SUCCESSFUL in 23s`，`BUILD_EXIT=0`；`327 actionable tasks: 14 executed, 313 up-to-date`（`build.log`）。
- 并发检查：构建前读 Gradle daemon 日志（`.gradle/daemon/9.6.1/daemon-45500.out.log`）只有 periodic health check；他线构建已于 00:39 结束。本轨后续构建均串行发起。
- 源快照 find 集 = `app/src core/model/src core/data/src core/database/src core/domain/src`；
  - before：1287 文件，`src-sha-before.txt` sha256 `297b2070ce287d73cace1dbf794b5eee4d05d8d183b9b5e362cacc5f46a11734`
  - after：同集合同命令；`src-snapshot.diff` = **0 行**（SOURCE STABLE）
  - 之后因 KD-75 证据目录修复，快照变为 `src-sha-after-fix.txt`（`fc93c37b…20e5`，1287 文件，仅 1 文件变化，见 E-41-D03 §3）。
  - 01:16–01:22 他线（KB retrieval）改动 `core/database/src/main`、`core/data/src` 并重建 data androidTest APK；`src-sha-now.txt` 与 after-fix 差 42 行（21 文件）。**本文件 §3 的 data APK sha 是 00:34 构建并在 00:46 被 Gradle 判 UP-TO-DATE 的那一份**；设备在 §3 安装后未再更新。漂移发生在全部设备套件结束之后，不影响本轮绑定，但当前树已不能逐字节复现本轮设备绿。

## 3. 产物身份与安装核对

| 产物 | 路径 | 字节 | sha256（宿主 = 设备） |
|---|---|---|---|
| app 主包（localFirst） | `app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk` | 183602999 | `5793300c5f453a45715e9846f1e16756dac1300e04dd68d026e6f7957bccd796` |
| app androidTest（修复前，首次 PDF 红） | `app/build/outputs/apk/androidTest/localFirst/debug/...apk` | 1691911 | `683cfafd2566ae941a67d4c8d90c383d31109665525dc33de24c1b7bd81af89e` |
| app androidTest（修复后，本目录其余结果使用） | 同上 | 1692019 | `0e906482a0a98d1e4b9f5bb56a3262a80a32d9f6404b80495289b22cec4e3aca` |
| data androidTest | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | 194469464 | `82a6f21e05419a9e9316373fe31f467824c082e9d08db107c99141c353ffac02` |
| database androidTest | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` | 209357634 | `6f55edbf52a72fca123ecb94992689f9a618357936458e557a58ccfc1d49291b` |
| strictOffline 主包（仅用于 E-41-D05 隔离臂实验；构建窗 01:15–01:17 与他线源漂移重叠，未用于任何"绿"结论） | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` | 192586802 | `9a5f6a6e85e18eef6145b45f3307a72bc4031e3ea508f95f01f15a3dac2faaba` |
| strictOffline androidTest | `app/build/outputs/apk/androidTest/strictOffline/debug/...apk` | 1691033 | `d300d8855c05a0ad8bdb8a372eebca3538bbebd0a960f36eeb4cbed7a7c9a721` |

- 包名：`com.tingyun.smartmistakebook.localfirst` + `.localfirst.test` / `com.tingyun.smartmistakebook.core.data.test` / `com.tingyun.smartmistakebook.core.database.test`；strictOffline：`com.tingyun.smartmistakebook.offline` + `.offline.test`。
- 安装命令：`adb -s emulator-5584 install -r -t <apk>` ×4 → 4×`Success`（`install.log`）。
- 设备侧：`pm path` + `sha256sum <base.apk>`，四值与宿主逐字节相同（`device-apk-sha256.txt`）：
  - app `5793300c…`、app test `683cfafd…`（修复后重装为 `0e906482…`）、data `82a6f21e…`、db `6f55edbf…`。

## 4. schema 资产门（S=73）

- data 测试包内 schema 资产 **73 份**（`1.json`…`73.json`）。
- `73.json`：393415 B / sha256 `c7e7586a4eaab6d7309e91f2b715a74229369d95bc11966cfe96a94755f39f1e`，`version=73`、`entities=92`、`views=2`、`identityHash=1d086050204a0da1ed6b6e3299dd9733`；与磁盘 `core/database/schemas/.../73.json` **逐字节相等**（`python -I` + zipfile 读取）。
- `StudyDatabase.kt:142` `STUDY_DATABASE_VERSION = 73`；`:423-424` 注册 `MIGRATION_71_73` 与 `ERROR_CAUSE_MIGRATION_72_73`。

## 5. 未验证 / 边界

- 不覆盖真机；不外推到 API≠34 / page-size≠4096。
- 源在设备套件结束后被他线改动（§2），本目录结果只绑定 after-fix 快照 + §3 sha。
- 本轮未运行任何 `connectedAndroidTest`。
