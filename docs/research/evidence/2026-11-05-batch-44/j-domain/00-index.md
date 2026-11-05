# J 域（发布工程）90 行验收（批次 44）

> 本文件是 J 域 90 行的落点与导航；台账只引用同目录下的
> `*.log` / `*.xml` / `*.device.json`。本文件本身不是证据。
> 纪律：验收阶段不跑 Gradle、不做 git 写、不输出密钥；设备只用 adb。

## 1. 身份与源快照

| 项 | 值 |
|---|---|
| 源快照 | HEAD `542b9bbac49081aca808c616a5dc538965d1767e`，工作树脏（`git status --porcelain -uall` = 4247 行） |
| release APK | `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk`，sha256 `724ce71834c227db79266149267d52c1b55a23d3cbceb2b93ce3930cba342b44`，155,816,991 B，mtime 2026-11-03 11:10 |
| release AAB | `app/build/outputs/bundle/strictOfflineRelease/app-strictOffline-release.aab`，sha256 `d073410529133c77374eb36097d6cea7e866126e81b822cb55ab7fa4c5d4bcdc`，113,438,749 B，mtime **2026-09-30 02:34** |
| mapping | `app/build/outputs/mapping/strictOfflineRelease/mapping.txt`，sha256 `c5b2d17080e2af0614886fd66f5a1fb304f3f2f4a6a244f654d5e00a83a5dc7e`，893,641 行 |
| debug APK（strictOffline） | sha256 `97ee82a1b22c5fd9dce8b057ffec508622c5a3c0dcde3ada39d01acd8296f341` |
| 设备 | `emulator-5584`（API 34 / x86_64 / boot `831e5d06-7331-40d8-8cf4-406976b8a9c3`）、`emulator-5562`（API 34 / x86_64 / boot `a702449b-0f25-4481-800f-ab0a475a37a6`） |
| 工具 | zipalign/apksigner/aapt2 37.0.0、bundletool 1.18.3（重建 classpath）、retrace 8.2.33、keytool/JDK 21、adb 37.0.1、Python 3.13 |
| 记录时间 | 2026-11-05 03:19–03:40（本机） |

## 2. 判定口径

- **PASS**：本域实跑命令 exit 0，且至少两种独立证据类（`cmd` / `test` / `device`）；
  单类证据不判 PASS，记 UNVERIFIED 并在 §5 写明。
- **FAIL**：判据明确不成立（陈旧 AAB、过期证书、版本不单调、脏树等）。
- **BLOCKED**：外部条件缺失（生产签名、arm64/16KB 设备、Play Console），出口见
  `docs/acceptance/runbook.md` §9.2。
- **UNVERIFIED**：未执行或只有单类证据；不冒充通过。
- **独立终裁**：本文件作者是 J 域执行者，不能自签；21 条 PASS 仍需
  `docs/acceptance/verdicts/<ID>.json` 的独立 reviewer 终裁后才能最终定案。
- 新增 `tools/ci/j_release_verify.py` 消灭的具体失败：旧 AAB 被 `bundletool
  validate` 的 exit 0 当成合格；`adb install` 的 exit code 不可当唯一判词；
  `retrace` 错形状原样返回；单一 `zipalign` 证据。对应单测
  `tools/tests/test_j_release_verify.py`（17 例）含负控。

## 3. 证据清单

| 文件 | 内容 | exit |
|---|---|---|
| `j-identity.log` | HEAD/脏行数、APK revision 与落后提交数、APK/AAB ABI、mapping 计数、版本字段 | 0 |
| `j-neg-clean-tree.log` | 脏树上 `--require-clean` 被拒（包装器断言拒绝） | 0 |
| `j-neg-head.log` | 产物 revision 落后 HEAD 被拒 | 0 |
| `j-zipalign.log` | 官方 `zipalign -c -P 16 -v 4` 末行 `Verification successful` | 0 |
| `j-apk16k.log` | 独立 ZIP/ELF 解析：14 个 `.so`，`STORED`、offset%16384=0、`p_align>=0x4000`、RELRO 无共享页 | 0 |
| `j-apk16k-selftest.log` | 把 `libLiteRt.so` 3 个 `PT_LOAD.p_align` 改 `0x1000` 后检查器变红 | 0 |
| `j-aab16k.log` | AAB `PAGE_ALIGNMENT_16K`=2，但 ABI 含 32 位、缺 `libquickjs.so` | 1 |
| `j-neg-aab.log` | 陈旧 AAB 被拒（包装器断言拒绝） | 0 |
| `j-bundletool-config.log` | `uncompressNativeLibraries{enabled:true, alignment:PAGE_ALIGNMENT_16K}`、`storeArchive` | 0 |
| `j-bundletool-manifest.log` | AAB `versionCode=2`、`versionName=0.2.0-offline` | 0 |
| `j-bundletool-validate.log` | `validate` exit 0；清单含 21 条 `.so`（6 arm64 + 6 armv7 + 3 x86 + 6 x86_64） | 0 |
| `j-r8.log` | 项目类 3569 / 改名 3557 / 墓碑 191；5 个改名类 dex 命中；负控 `Lzzzzz9;` 0 | 0 |
| `j-r8keep.log` | `WorkDatabase_Impl` 等三个 keep 类无 `void <init>()` | 0 |
| `j-mapping-structure.log` | R8 9.3.16、min_api 23、`pg_map_id`/`pg_map_hash`、893641 行 | 0 |
| `j-aab-metadata.log` | AAB 内 `BUNDLE-METADATA/.../proguard.map` 与 `r8.json` 存在 | 0 |
| `j-retrace-at.log` | `at ds.v(...)` 还原出 `...invokeSuspend(...)` | 0 |
| `j-retrace-bad.log` | 错形状帧原样返回（控制组） | 0 |
| `j-apksigner.log` | release APK v1/v2 true、v3 false；证书 SHA-256 `9dc07e9f...056e20` | 0 |
| `j-apk-cert.log` | APK 证书 `CN=WS8 Ephemeral Local Only`，有效期 2026-11-03 至 **2026-11-05 11:03** | 0 |
| `j-keytool.log` | AAB 证书 `CN=ZCode Release Smoke`，`A9:E7:6C:AD:...`，有效期至 **2026-10-30（已过期）** | 0 |
| `j-signing.log` | `RELEASE_*` 4/4 缺失、0 个已跟踪 keystore、文档只写 `keystore.properties` | 0 |
| `j-version.log` | gradle 2/0.2.0、output-metadata 2/0.2.0-offline、abiFilters 2 个 | 0 |
| `j-version-artifacts.log` | release/debug/AAB/localFirst 四份产物 versionCode 全为 2 | 0 |
| `j-version-source.log` | 版本号只来自 `app/build.gradle.kts:33-34` | 0 |
| `j-release-manifest.log` | release manifest 门：9 checked + 4 debug，5 个 debug-only 组件双向成立 | 0 |
| `j-aapt2-badging.log` | APK `versionCode=2`、`versionName=0.2.0-offline`、minSdk 23、targetSdk 36 | 0 |
| `j-install-cross-signature.log` | 跨签名安装 `Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]`，`ADB_INSTALL_EXIT=1`，事后状态不变（研究件旧记录的 exit 0 未复现） | 0 |
| `j-device-probe.log` | 两台设备 `PAGE_SIZE=4096`、x86_64、API 34；5562 package 服务 Broken pipe | 0 |
| `j-device-crosssignature.device.json` | serial/boot/apk sha、安装前后 versionCode/DEBUGGABLE、失败行 | - |
| `j-device-pagesize.device.json` | 两台设备 4096 页；无 16384 设备 | - |
| `j-unit-*.xml` | 17 例单测分 6 组：ELF/16KB、AAB、mapping/R8、版本、identity、retrace | 0 |

## 4. 90 行

| ID | 域 | 检查 | 方法 | 证据 | verdict |
|---|---|---|---|---|---|
| J-001 | J | 干净树前提：porcelain(-uall) 为 0 | cmd: python -I tools/ci/j_release_verify.py identity --root . --require-clean | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-identity.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-identity.xml | FAIL |
| J-002 | J | 产物 revision 等于 HEAD 且落后 0 提交 | cmd: python -I tools/ci/j_release_verify.py identity --require-head | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-identity.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-neg-head.log#exit=0 | FAIL |
| J-003 | J | 干净树构建 release APK（Gradle 门禁阶段） | cmd: ./gradlew :app:assembleStrictOfflineRelease --offline | file:docs/acceptance/runbook.md | UNVERIFIED |
| J-004 | J | 负向：脏树或落后 revision 必须被拒 | cmd: identity --require-clean 与 --require-head; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-neg-clean-tree.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-neg-head.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-identity.xml | PASS |
| J-005 | J | APK ZIP 完整性（CRC 与中央目录） | cmd: python -I tools/ci/j_release_verify.py identity; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-identity.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-identity.xml | PASS |
| J-006 | J | 干净树构建阻塞与出口 | BLOCKED: 共享树未提交且验收阶段不跑 Gradle（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-007 | J | 构建确定性：两次 clean 构建字节一致 | cmd: 两次 clean assemble 后 cmp（门禁阶段） | file:docs/acceptance/runbook.md | UNVERIFIED |
| J-008 | J | 独立复核：干净树面 | review: 非作者 reviewer 终裁 | file:docs/acceptance/runbook.md | UNVERIFIED |
| J-009 | J | runbook 覆盖干净树构建 | cmd: grep -n "J-003" docs/acceptance/runbook.md | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-runbook-coverage.log#exit=0; file:docs/acceptance/runbook.md | UNVERIFIED |
| J-010 | J | mapping 结构：compiler/version/min_api/hash | cmd: python -I tools/ci/j_release_verify.py identity; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-mapping-structure.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-mapping.xml | PASS |
| J-011 | J | 项目类改名覆盖与统计口径 | cmd: python -I tools/ci/j_release_verify.py identity; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-identity.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-mapping.xml | PASS |
| J-012 | J | mapping 与 APK dex 字节绑定且负控为 0 | cmd: python -I tools/ci/j_release_verify.py r8; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-r8.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-mapping.xml | PASS |
| J-013 | J | keep 类无无参构造器回归（KD-69） | cmd: python -I tools/ci/j_release_verify.py r8keep; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-r8keep.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-mapping.xml | PASS |
| J-014 | J | retrace 可还原且错形状不还原 | cmd: retrace.bat --verbose mapping stack; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-retrace-at.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-retrace-bad.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-retrace.xml | PASS |
| J-015 | J | R8 墓碑类不计入包内类数 | cmd: python -I tools/ci/j_release_verify.py r8; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-r8.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-mapping.xml | PASS |
| J-016 | J | AAB 内嵌 proguard.map 与 r8.json 存在 | cmd: 读取 AAB 的 BUNDLE-METADATA 目录 | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-aab-metadata.log#exit=0 | UNVERIFIED |
| J-017 | J | APK 路线 mapping 上传 Play Console | BLOCKED: 无 Play Console 账号（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-018 | J | 独立复核：R8/mapping 面 | review: 非作者 reviewer 终裁 | file:docs/acceptance/runbook.md | UNVERIFIED |
| J-019 | J | 生产签名四变量齐全 | cmd: python -I tools/ci/j_release_verify.py signing | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-signing.log#exit=0; file:docs/acceptance/runbook.md | BLOCKED |
| J-020 | J | release APK/AAB 使用生产钥匙 | cmd: signing 与 keytool -printcert | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-signing.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-apk-cert.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-release-manifest.log#exit=0 | FAIL |
| J-021 | J | 签名指纹等于已登记发布指纹 | cmd: apksigner verify --print-certs 与 keytool -printcert | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-apksigner.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-keytool.log#exit=0 | FAIL |
| J-022 | J | 负向：缺签名变量时构建必须失败 | cmd: ./gradlew validateReleaseSigning（门禁阶段） | file:app/build.gradle.kts; file:docs/acceptance/runbook.md | UNVERIFIED |
| J-023 | J | 签名方案 v1 与 v2 覆盖 minSdk 23 | cmd: apksigner verify --print-certs --verbose | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-signing.log#exit=0 | UNVERIFIED |
| J-024 | J | AAB 与 APK 使用同一签名身份 | cmd: keytool -printcert 与 apksigner --print-certs | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-keytool.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-apksigner.log#exit=0 | FAIL |
| J-025 | J | AAB 证书有效期覆盖发布窗口 | cmd: keytool -printcert -jarfile | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-keytool.log#exit=0 | FAIL |
| J-026 | J | 独立复核：签名/AAB 面 | review: 非作者 reviewer 终裁 | file:docs/acceptance/runbook.md | UNVERIFIED |
| J-027 | J | 文档契约一致：keystore.properties 与环境变量 | cmd: signing 文档契约字段 | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-signing.log#exit=0; file:app/build.gradle.kts | FAIL |
| J-028 | J | APK ZIP 16KB 对齐 | cmd: zipalign -c -P 16 -v 4; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-zipalign.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-elf.xml | PASS |
| J-029 | J | ELF PT_LOAD p_align 全部不小于 0x4000 | cmd: python -I tools/ci/j_release_verify.py apk16k; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-apk16k.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-elf.xml | PASS |
| J-030 | J | RELRO 页边界无共享可写段 | cmd: python -I tools/ci/j_release_verify.py apk16k; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-apk16k.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-elf.xml | PASS |
| J-031 | J | 负向：p_align 改为 0x1000 必须变红 | cmd: python -I tools/ci/j_release_verify.py apk16k --selftest; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-apk16k-selftest.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-elf.xml | PASS |
| J-032 | J | AAB 声明 PAGE_ALIGNMENT_16K | cmd: bundletool dump config --bundle; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-bundletool-config.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-aab.xml | PASS |
| J-033 | J | AAB 的 ABI 集合等于 abiFilters | cmd: python -I tools/ci/j_release_verify.py aab16k | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-aab16k.log#exit=1; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-bundletool-validate.log#exit=0 | FAIL |
| J-034 | J | AAB 含 libquickjs.so | cmd: python -I tools/ci/j_release_verify.py aab16k | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-aab16k.log#exit=1 | FAIL |
| J-035 | J | 运行时页大小为 16384 | device: adb shell getconf PAGE_SIZE | device:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-pagesize.device.json; file:docs/acceptance/runbook.md | BLOCKED |
| J-036 | J | 独立复核：16KB/AAB 面 | review: 非作者 reviewer 终裁 | file:docs/acceptance/runbook.md | UNVERIFIED |
| J-037 | J | arm64 模拟器或真机启动 | BLOCKED: x86_64 宿主不能起 arm64 AVD（runbook §9.2） | device:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-pagesize.device.json; file:docs/acceptance/runbook.md | BLOCKED |
| J-038 | J | arm64 APK 安装与冷启 | BLOCKED: 无 arm64 设备（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-039 | J | arm64 运行时 16KB 与 native 装载 | BLOCKED: 无 arm64 设备（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-040 | J | 负向：APK 仅含 64 位 ABI | cmd: python -I tools/ci/j_release_verify.py identity; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-identity.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-aab.xml | PASS |
| J-041 | J | 负向：AAB 不含 32 位 .so | cmd: python -I tools/ci/j_release_verify.py aab16k | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-aab16k.log#exit=1; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-bundletool-validate.log#exit=0 | FAIL |
| J-042 | J | arm64 设备证据绑定 serial/boot/apk sha | BLOCKED: 无 arm64 设备（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-043 | J | 宿主架构不支持 arm64 的阻塞登记 | BLOCKED: 两台在线设备均 x86_64（runbook §9.2） | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-probe.log#exit=0; file:docs/acceptance/runbook.md | BLOCKED |
| J-044 | J | arm64 结果不得跨设备外推 | cmd: adb getprop ro.product.cpu.abi | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-probe.log#exit=0 | UNVERIFIED |
| J-045 | J | arm64 runbook 覆盖 | file: docs/acceptance/runbook.md §9.2 | file:docs/acceptance/runbook.md | UNVERIFIED |
| J-046 | J | 版本号单一来源 | cmd: grep versionCode 与 versionName; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-version-source.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-version.xml | PASS |
| J-047 | J | 版本号在 gradle/metadata/APK/AAB 一致 | cmd: version 与 aapt2 与 bundletool manifest; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-version.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-aapt2-badging.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-version.xml | PASS |
| J-048 | J | versionCode 跨构建单调 | cmd: aapt2 badging 四份产物 | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-version-artifacts.log#exit=0 | FAIL |
| J-049 | J | 负向：同 vc 不同构建必须被检出 | cmd: 缺少跨产物 vc 碰撞检测器 | file:docs/acceptance/runbook.md | UNVERIFIED |
| J-050 | J | versionCode 在 1..2100000000 | cmd: python -I tools/ci/j_release_verify.py version; test: tools/tests/test_j_release_verify.py | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-version.log#exit=0; test:docs/research/evidence/2026-11-05-batch-44/j-domain/j-unit-version.xml | PASS |
| J-051 | J | 版本号生成或注入机制（非手改两行） | cmd: version 与 grep 全树版本源 | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-version-source.log#exit=0; file:app/build.gradle.kts | FAIL |
| J-052 | J | 回归：历史 AAB 与现 APK 的 vc 可比 | cmd: aapt2 与 bundletool manifest | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-version-artifacts.log#exit=0; cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-artifact-mtimes.log#exit=0 | FAIL |
| J-053 | J | 独立复核：版本号面 | review: 非作者 reviewer 终裁 | file:docs/acceptance/runbook.md | UNVERIFIED |
| J-054 | J | 版本 runbook 覆盖 | file: docs/acceptance/runbook.md §9.2 | file:docs/acceptance/runbook.md | UNVERIFIED |
| J-055 | J | 同签名 vc N 到 N+1 升级 | BLOCKED: 无成对 vc 产物（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-056 | J | 升级后数据保留 | BLOCKED: 无成对 vc 产物（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-057 | J | 降级被 INSTALL_FAILED_VERSION_DOWNGRADE 拒绝 | BLOCKED: 无低 vc 产物（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-058 | J | 负向：跨签名覆盖必须失败 | device: adb install -r release APK | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-install-cross-signature.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-crosssignature.device.json | PASS |
| J-059 | J | 负向：安装判据取输出行与事后 dumpsys，不单看 exit code | device: adb install -r release APK | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-install-cross-signature.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-crosssignature.device.json | PASS |
| J-060 | J | 设备证据绑定 serial/boot_id/apk sha | device: adb getconf 与 dumpsys | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-probe.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-crosssignature.device.json | PASS |
| J-061 | J | 成对产物阻塞与出口 | BLOCKED: 无成对 vc 与同钥匙（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-062 | J | 回归：跨签名失败后设备状态不变 | device: adb install 与 dumpsys 前后 | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-install-cross-signature.log#exit=0; device:docs/research/evidence/2026-11-05-batch-44/j-domain/j-device-crosssignature.device.json | PASS |
| J-063 | J | 独立复核：升级/降级面 | review: 非作者 reviewer 终裁 | file:docs/acceptance/runbook.md | UNVERIFIED |
| J-064 | J | 回滚文档：可覆盖且迁移非破坏 | cmd: grep -n "## Rollback" RELEASE.md | cmd:docs/research/evidence/2026-11-05-batch-44/j-domain/j-rollback-doc.log#exit=0; file:RELEASE.md | UNVERIFIED |
| J-065 | J | 上一版可覆盖安装 | BLOCKED: 无上一版成对产物（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-066 | J | 迁移非破坏（备份先行与迁移矩阵） | 依赖 B 域迁移矩阵，本域不单独外推 | file:docs/acceptance/runbook.md | UNVERIFIED |
| J-067 | J | 负向：破坏性迁移不得发布 | 流程约束，无本域可执行判据 | file:RELEASE.md | UNVERIFIED |
| J-068 | J | 回滚证据：成对包与数据面 | BLOCKED: 无成对包与生产签名（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-069 | J | 回滚阻塞与出口 | BLOCKED: 无成对包与生产签名（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-070 | J | 回归：回滚演练 | cmd: 成对包回滚演练（门禁阶段） | file:docs/acceptance/runbook.md | UNVERIFIED |
| J-071 | J | 独立复核：回滚面 | review: 非作者 reviewer 终裁 | file:docs/acceptance/runbook.md | UNVERIFIED |
| J-072 | J | 回滚 runbook 覆盖 | file: docs/acceptance/runbook.md §9.2 | file:docs/acceptance/runbook.md | UNVERIFIED |
| J-073 | J | 商店图标 512x512 | BLOCKED: 无 Play Console 账号与素材（runbook §9.2） | file:docs/acceptance/runbook.md; file:docs/research/2026-10-25-store-launch-materials-checklist.md | BLOCKED |
| J-074 | J | 特色图片 1024x500 | BLOCKED: 无 Play Console 账号与素材（runbook §9.2） | file:docs/acceptance/runbook.md; file:docs/research/2026-10-25-store-launch-materials-checklist.md | BLOCKED |
| J-075 | J | 手机与平板截图 | BLOCKED: 无 Play Console 账号与素材（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-076 | J | 简短与完整描述 | BLOCKED: 无 Play Console 账号与文案定稿（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-077 | J | 隐私政策 URL | BLOCKED: 无托管 URL 与账号（runbook §9.2） | file:docs/acceptance/runbook.md; file:docs/current/privacy-and-data-safety.md | BLOCKED |
| J-078 | J | Data safety 表单映射 | BLOCKED: 无账号；本地草案见 docs/current（runbook §9.2） | file:docs/acceptance/runbook.md; file:docs/current/privacy-and-data-safety.md | BLOCKED |
| J-079 | J | AIGC 分发表单与说明材料 | BLOCKED: 无账号与备案信息（runbook §9.2） | file:docs/acceptance/runbook.md; file:docs/research/2026-10-25-store-launch-materials-checklist.md | BLOCKED |
| J-080 | J | 内容分级与适龄定位 | BLOCKED: 无 Play Console 账号（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-081 | J | 商店素材 runbook 覆盖 | BLOCKED: 素材清单存在但未备料（runbook §9.2） | file:docs/acceptance/runbook.md; file:docs/research/2026-10-25-store-launch-materials-checklist.md | BLOCKED |
| J-082 | J | 封闭测试轨道创建 | BLOCKED: 无 Play Console 账号（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-083 | J | 测试账号清单 | BLOCKED: 无 Play Console 账号（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-084 | J | 反馈渠道 | BLOCKED: 无 Play Console 账号（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-085 | J | 测试轮次与退出条件 | BLOCKED: 无 Play Console 账号（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-086 | J | vc 递增上传 | BLOCKED: 无账号且 vc 不单调（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-087 | J | Play App Signing 指纹登记 | BLOCKED: 无生产签名与账号（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-088 | J | 16KB 上传警告清零 | BLOCKED: 无账号且 AAB 陈旧（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-089 | J | 版本说明与发布说明 | BLOCKED: 无 Play Console 账号（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |
| J-090 | J | 封闭测试 runbook 覆盖 | BLOCKED: 轨道未建立（runbook §9.2） | file:docs/acceptance/runbook.md | BLOCKED |

## 5. 对抗复核（反例尝试）

1. **脏树门能红**：`identity --require-clean` 在 4247 个脏路径上返回 1；包装器
   断言 `EXPECTED_REJECT=1`（`j-neg-clean-tree.log`）。
2. **产物绑定门能红**：APK revision `faab17f5`（HEAD 前 32 个提交）使
   `--require-head` 返回 1（`j-neg-head.log`）。
3. **16KB 检查器能红**：把 `libLiteRt.so` 的 3 个 `PT_LOAD.p_align` 改成
   `0x1000` 后同一检查器报 3 条错误（`j-apk16k-selftest.log`）。
4. **陈旧 AAB 能红**：`aab16k` 在 32 位 ABI 与缺 quickjs 上报 2 条错误
   （`j-neg-aab.log`）；`bundletool validate` 同包仍 exit 0，证明 exit code 不能
   当判词。
5. **retrace 假绿能红**：`at ds.v(...)` 还原；`at com...ds.v(...)` 原样返回
   （`j-retrace-bad.log`），单看 exit 0 会把“没还原”当成功。
6. **adb install exit code 不可当唯一判词**：跨签名安装输出 `Failure
   [INSTALL_FAILED_UPDATE_INCOMPATIBLE]`；本机 adb 37.0.1 返回 1，批次 43 研究件
   记录过同一失败返回 0。判据必须取输出行与事后 `dumpsys`
   （`j-install-cross-signature.log`）。
7. **AAB 内容反例**：同一条 `validate` 命令 exit 0，清单却是 6+6+3+6 条 `.so`
   （含 9 条 32 位）且无 `libquickjs.so`（`j-bundletool-validate.log`）。

## 6. 不得写成通过

- **发版就绪不成立**：工作树脏（4247）、APK revision 落后 HEAD 32 个提交、
  AAB 为 2026-09-30 旧构建、AAB 证书 2026-10-30 已过期、APK 证书 2026-11-05
  11:03 到期、`RELEASE_*` 4/4 未设。
- **版本不单调**：release/debug/AAB/localFirst 四份产物 versionCode 全为 2，
  无法构成升级对；版本号只有两行硬编码，无生成器或 CI 注入。
- **16KB 只到静态面**：APK 的 14 个 `.so` 通过 ZIP/ELF 静态检查；两台在线设备
  均为 4096 页，运行时 16384 未验证。
- **arm64 全 BLOCKED**：无 arm64 真机，x86_64 宿主不能起 arm64 AVD。
- **单类证据未判 PASS**：J-009、J-016、J-023、J-044、J-045、J-049、J-054、
  J-064、J-070、J-071、J-072 只有 `cmd`/`file` 一类，按 §2 不判 PASS。
- **外部项**：Play Console 的商店素材、Data safety、AIGC 表单、封闭测试全部
  BLOCKED；本域不代替人工/账号验收。
- **未验证**：`bundletool build-apks`/`get-device-spec`/`install-apks`、构建
  确定性、真实 Play 上传、生产签名身份、第三方 `.so` 符号化。

## 7. 复跑命令

```bash
python -I tools/ci/j_release_verify.py identity \
  --root . --apk app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk \
  --aab app/build/outputs/bundle/strictOfflineRelease/app-strictOffline-release.aab \
  --mapping app/build/outputs/mapping/strictOfflineRelease/mapping.txt \
  --gradle app/build.gradle.kts \
  --metadata app/build/outputs/apk/strictOffline/release/output-metadata.json
python -I tools/ci/j_release_verify.py apk16k --apk <apk> [--selftest]
python -I tools/ci/j_release_verify.py aab16k --aab <aab>
python -I tools/ci/j_release_verify.py r8 --mapping <mapping> --apk <apk>
python -I tools/ci/j_release_verify.py r8keep --usage <usage.txt> --classes <逗号分隔类名>
python -I tools/ci/j_release_verify.py signing --root . --apk <apk> --aab <aab> \
  --apksigner C:/Android/Sdk/build-tools/37.0.0/apksigner.bat
python -I tools/ci/j_release_verify.py version --gradle app/build.gradle.kts \
  --metadata app/build/outputs/apk/strictOffline/release/output-metadata.json
python -I -m pytest tools/tests/test_j_release_verify.py -q
```

设备侧：

```bash
adb -s emulator-5584 shell getconf PAGE_SIZE
adb -s emulator-5584 install -r <release.apk>   # 判据：输出含 Success 且事后 dumpsys vc 等于目标
adb -s emulator-5584 shell dumpsys package <pkg> | grep -E "versionCode|versionName|pkgFlags"
```
