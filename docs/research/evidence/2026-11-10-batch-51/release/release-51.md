# 发布线实跑（批次 51 · 计划 item 36–38）：release 构建 / 16KB·ABI·签名·mapping / 干净树尝试 / 外部阻塞

- 批次：51（2026-11-10/11）｜计划：`.zcode/plans/plan-2026-11-10-batch-51-final.md:88-91`（item 36/37/38）
- 本线：发布工程子线；**Gradle 串行**（开工与关键节点 `jps` 观察：唯一活动 daemon 是 `D:\smb-ui-forest-20261027` 的**空闲** daemon，未见其他构建；本线全部 Gradle 调用串行执行）
- 机器：Windows 10.0.26200 / Git Bash / JDK 21.0.11 (Temurin) / build-tools 37.0.0 / Gradle 9.6.1 / R8 9.3.16
- 树基线：`HEAD=8dd29e963975ff44703f638064322be0e7494f98`；**工作树脏**：开工 `git status --porcelain` **683** 行、`-uall` **5443** 路径；`git rev-parse --is-shallow-repository` = `false`
- 产物口径：**本次 APK/AAB 是「脏工作树 + 本地测试钥」的候选包，不是干净树产物，也不是生产签名包**（§5/§7）
- 证据目录：`docs/research/evidence/2026-11-10-batch-51/release/`（本文件所在目录）

---

## 0. 结论（先看这张表）

| # | 项 | 结果 | 证据 |
|---|---|---|---|
| 1 | versionCode 构建注入 | **PASS**：`N = 100000 + 1177 = 101177`，`-Psmbk.versionCode` 注入；缺参/非整数/越界/缺签名变量全部 fail-closed | §1；`validateVersionCode-*.log`、`validateReleaseSigning-*.log`、`version-code-derivation.json` |
| 2 | APK/AAB 出包 | **PASS（候选包）**：4 个 Release 任务 `BUILD SUCCESSFUL`（2m59s；481 tasks，40 executed/441 up-to-date） | §2；`build-release.log`、`build-localFirst-force-rebuild.log` |
| 3 | APK 签名 | **PASS（本地测试身份）**：v1+v2 `true`，证书 SHA-256 `2c8415b2…5eba`，DN 含 `OU=NOT-PLAY-PRODUCTION` | §3.1；`apksigner-*.txt` |
| 4 | AAB 签名 | **PASS（本地测试身份）**：`jarsigner -verify` exit 0；`keytool -printcert -jarfile` 指纹同 APK；`signing` 门 exit 0 | §3.2；`jarsigner-*.txt`、`keytool-aab-*.txt`、`signing-*.json` |
| 5 | APK 16KB | **PASS**：`zipalign -v -c -P 16 4` → `Verification successful`；`apk16k` 14/14 `.so` STORED、偏移 0 mod 16384、`elf_errors: []`；负控 `--selftest` 变红 | §3.3；`zipalign-*.txt`、`apk16k-*.json`、`negative-apk16k-selftest.json` |
| 6 | AAB 16KB/ABI | **PASS**：`alignment: 2`（PAGE_ALIGNMENT_16K）、ABI 集合 `{arm64-v8a, x86_64}`、7 个 native 库全在（含 `libquickjs.so`） | §3.4；`aab16k-*.json` |
| 7 | AAB versionCode | **PASS**：直接从 AAB proto manifest 读出 `101177`（字符串与编译整数一致），versionName 两 flavor 正确 | §3.5；`aab-manifest-*.json` |
| 8 | mapping | **PASS**：140,191,204 B / 967,527 行 / R8 9.3.16；两 flavor 同 sha；**与 AAB 内嵌 `proguard.map` 逐字节同 sha**；`r8` 门 exit 0 | §4；`r8-*.json`、`artifacts.sha256` |
| 9 | 发布 manifest | **PASS**：`check_release_manifest.py` exit 0（9 release/internal + 4 debug merged manifest；5 个 debug-only 组件两侧行为符合预期） | §4.3；`check-release-manifest.log` |
| 10 | identity 门 | 无 `--require-clean` **exit 0**（ZIP 完整、revision=HEAD、behind=0）；`--require-clean` **exit 1**（脏树，真实状态） | §4.2；`identity-*.json` |
| 11 | 干净树构建 | **BLOCKED（如实失败）**：本地 clone（HEAD，干净）离线配置成功（1m2s，0 下载），但完整构建在 `:core:export:compileReleaseKotlin` **失败**：`AI_GENERATED_DOCUMENT_NOTICE` 未解析——该常量定义文件在 **未跟踪** 的 `AiGeneratedDocumentNotice.kt`，HEAD 自身不可编译 | §5；`clean-clone-build.log:348,8461-8464` |
| 12 | 外部五项 | **BLOCKED（外部）**：Play 账号、生产 keystore、备案号、公网隐私 URL、图像 provider；逐项动作见 `external-blockers.md` | §6 |
| 13 | 发布判定 | **不可声明“发布就绪/干净树可复现”**：候选包只证明“当前脏树 + 本地测试钥能出合规字节”；干净树被 HEAD 不自洽阻断；生产身份/商店/备案/隐私 URL 全在外部 | §7 |

---

## 1. versionCode 注入与 fail-closed 门

### 1.1 算号

```bash
$ python -I tools/ci/release_version_code.py --root .
{"commit_count": 1177, "shallow": false, "version_code": 101177, "errors": []}   # exit 0
```

⇒ **N = 101177**（`100000 + 1177`）。输出：`version-code-derivation.json`。
算号器判据（`tools/ci/release_version_code.py:39-72`）：非 shallow、提交数为正整数、结果落在 `1..2100000000`，任一不满足 exit 1。

### 1.2 先红后绿（本轮逐条实跑）

| 调用 | 结果 | 关键输出 |
|---|---|---|
| `:app:validateVersionCode`（无 `-P`） | **exit 1** | `Release build requires -Psmbk.versionCode=N (…)`（`validateVersionCode-missing-P.log`） |
| `… -Psmbk.versionCode=abc` | **exit 1** | `smbk.versionCode is not an integer: abc`（`validateVersionCode-noninteger.log`） |
| `… -Psmbk.versionCode=2100000001` | **exit 1** | `smbk.versionCode out of Play range 1..2100000000: 2100000001`（`validateVersionCode-outofrange.log`） |
| `… -Psmbk.versionCode=101177` | **exit 0** | `BUILD SUCCESSFUL`（`validateVersionCode-101177.log`） |
| `:app:validateReleaseSigning`（未配 env） | **exit 1** | `Release signing is missing required environment variables: RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD, RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD` |
| `:app:validateReleaseSigning`（source 本地 env） | **exit 0** | `BUILD SUCCESSFUL` |

### 1.3 注入是否真的进了产物（三条独立读数）

| 读数 | 值 | 来源 |
|---|---|---|
| APK `output-metadata.json` | `versionCode: 101177`；`0.2.0-offline` / `0.2.0-localfirst` | `version-*.json` |
| AAB 自身 proto manifest | `version_code: 101177`（string `"101177"` == compiled int `101177`） | `aab-manifest-*.json` |
| `version` 门 | `--expected-code 101177` → **exit 0**；gradle 文本 `version_code: null`（注入形态，预期） | `version-strictOffline.json`、`version-localFirst.json` |

负控：`--expected-code 101178` → **exit 1**（`output-metadata versionCode != --expected-code`）；
注入形态不给 `--expected-code` → **exit 1**（`versionCode is injected at build time; pass --expected-code`）。
文件：`negative-version-101178.json`、`negative-version-missing-expected.json`。

---

## 2. 出包（串行 Gradle）

```bash
set -a; . .tmp/release-signing/release-signing.env; set +a
./gradlew.bat --console=plain --no-daemon --no-build-cache \
  :app:assembleStrictOfflineRelease :app:bundleStrictOfflineRelease \
  :app:assembleLocalFirstRelease  :app:bundleLocalFirstRelease \
  -Psmbk.versionCode=101177
```

→ **BUILD SUCCESSFUL in 2m 59s；481 actionable tasks: 40 executed, 441 up-to-date；BUILD_EXIT=0**
（`build-release.log`；`> Task :app:validateVersionCode`、`> Task :app:validateReleaseSigning` 均在打包前执行）

**如实说明**：本调用里 `packageLocalFirstRelease` 是 `UP-TO-DATE`（该 APK 由同日 07:38 的 release 冒烟构建产出，
其 metadata 已是 101177）。为消除“产物不是本次运行写出”的歧义，删掉该 APK/metadata 后强制重跑：

```bash
./gradlew.bat --console=plain --no-daemon --no-build-cache :app:assembleLocalFirstRelease -Psmbk.versionCode=101177
# BUILD SUCCESSFUL in 11s；413 tasks: 5 executed, 408 up-to-date
```

⇒ localFirst APK 现在由本次运行重新打包（`build-localFirst-force-rebuild.log`）；其 sha `b6fbd877…`
与「设备全量」子线 07:38 R8 冒烟用的 release APK sha 一致（`docs/research/2026-11-10-batch-51-record.md` 设备全量节）。

产物（`artifacts.sha256`）：

| 产物 | 字节 | sha256 |
|---|---|---|
| `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk` | 156,229,823 | `56e41d9c6b612790a92a7b5aa72b3fae30e8d1dbb64c77752ac48466c35988de` |
| `app/build/outputs/apk/localFirst/release/app-localFirst-release.apk` | 156,230,405 | `b6fbd87745c3ba3bc5fc9b63e7830019c9e99682bacc7432d495546ed72ed46f` |
| `app/build/outputs/bundle/strictOfflineRelease/app-strictOffline-release.aab` | 125,559,900 | `600a8bb05df7d51d85b6f7c9614b7cbf7c45306e23dc12a4370a325cf93597aa` |
| `app/build/outputs/bundle/localFirstRelease/app-localFirst-release.aab` | 125,560,406 | `5221905e321b4b1d44cd0f5576f7530f6d62814627f0de5e583c2dbdfab67449` |
| `app/build/outputs/mapping/strictOfflineRelease/mapping.txt` | 140,191,204 | `80f7127aeaa17b7902fe89bcdf574bc615908787d3aac9607cd689a65c2c84bf` |
| `app/build/outputs/mapping/localFirstRelease/mapping.txt` | 140,191,204 | 同上（两 flavor 逐字节相同） |

版本/ABI 口径：`versionName = 0.2.0` + `-offline` / `-localfirst`；`abiFilters = {arm64-v8a, x86_64}`
（`app/build.gradle.kts:55-57`、`:61-72`）。

---

## 3. APK / AAB 校验矩阵

### 3.1 APK 签名（`apksigner`，build-tools 37.0.0）

两 flavor 同一读数（`apksigner-*.txt`）：

```
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Number of signers: 1
V2 Signer: certificate DN: CN=SmartMistakeBook Local Release, OU=NOT-PLAY-PRODUCTION, O=SmartMistakeBook, C=CN
V2 Signer: certificate SHA-256 digest: 2c8415b260dc6b645a33497077c819ddc2cbb85eaab7f00f2d87424becad5eba
```

v3 为 false 是 AGP 对 minSdk 23 的默认结果（与批次 50 盘上包一致；本文件不主张需要 v3）。

### 3.2 AAB 签名（`jarsigner` + `keytool`；`apksigner` 不能验 AAB）

- `jarsigner -verify <aab>` → **exit 0**，首行 `jar 已验证。`（`jarsigner-*.txt`；自签名链/无时间戳告警是本地测试钥的预期）。
- `keytool -printcert -jarfile <aab>` → 证书 SHA-256 `2C:84:15:…:5E:BA`，DN 与 APK 相同（`keytool-aab-*.txt`）。
- 组合门 `j_release_verify.py signing` → **exit 0**，`env_missing: []`、`tracked_keystores: []`、
  APK/AAB 指纹相等、证书有效期 2026-11-08 → 2054-03-26（`signing-*.json`）。
- 负控：debug APK（`26466dbf…`）vs release AAB（`2c8415b2…`）→ **exit 1**
  `APK/AAB signer identity differs`（`negative-signing-debug-apk-vs-release-aab.json`）。

> 身份定性：这把钥匙的 DN 明写 `OU=NOT-PLAY-PRODUCTION`，并且 `docs/research/2026-11-09-release-signing.md:312-322`
> 已说明启用 Play App Signing 后它只能是**上传密钥**。本门证明“上传链自洽”，**不证明**商店发布证书正确（§6 X-2）。

### 3.3 APK 16KB

| 检查 | 结果 |
|---|---|
| `zipalign.exe -v -c -P 16 4 <apk>`（两 flavor） | **exit 0**，末行 `Verification successful`（`zipalign-*.txt`） |
| `j_release_verify.py apk16k --apk <apk>` | **exit 0**：14 条 `.so`（arm64-v8a 7 + x86_64 7）全 `stored: true`、`offset_mod_16384: 0`、`elf_errors: []`（`apk16k-*.json`） |
| 判据负控 `apk16k --selftest` | **ok: true**：把首个 `.so` 的 3 个 `PT_LOAD` 改成 `p_align=0x1000` 后报红（`negative-apk16k-selftest.json`） |

### 3.4 AAB 16KB / ABI

`j_release_verify.py aab16k` 两 flavor 均 **exit 0**（`aab16k-*.json`）：

```json
{"abi_set": ["arm64-v8a", "x86_64"], "alignment": 2,
 "native_libs": ["libLiteRt.so","libLiteRtClGlAccelerator.so","libandroidx.graphics.path.so",
                 "libdatastore_shared_counter.so","liblitert_jni.so",
                 "libmlkit_google_ocr_pipeline.so","libquickjs.so"], "errors": []}
```

`alignment: 2` = `PAGE_ALIGNMENT_16K`（`BundleConfig.pb`）。判据实现见 `tools/ci/j_release_verify.py:296-315`。

### 3.5 AAB versionCode/versionName（AAB 自身 proto manifest）

`tools/ci/aab_manifest_version.py`（读 `base/manifest/AndroidManifest.xml`，非 APK 元数据推断）：

| flavor | package | versionCode（string / compiled） | versionName |
|---|---|---|---|
| strictOffline | `com.tingyun.smartmistakebook.offline` | `"101177"` / `101177` | `0.2.0-offline` |
| localFirst | `com.tingyun.smartmistakebook.localfirst` | `"101177"` / `101177` | `0.2.0-localfirst` |

负控：`--expected-code 101178` → **exit 1**（`AAB versionCode 101177 != --expected-code 101178`）。

---

## 4. mapping / identity / manifest / 判据自检

### 4.1 mapping

- `mapping.txt`：**140,191,204 B / 967,527 行**，`# compiler: R8` / `# compiler_version: 9.3.16`。
- 两 flavor mapping **同 sha256** `80f7127aeaa17b7902fe89bcdf574bc615908787d3aac9607cd689a65c2c84bf`。
- **AAB 内嵌 `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` 的 sha256 与盘上 mapping 相同**
  （两 flavor 都核过；`gates-4.txt`）。
- `j_release_verify.py r8` 两 flavor **exit 0**：抽样 5 个项目类在 dex 中均命中（hits ≥1），
  负控符号 `Lzzzzz9;` 命中 **0**（`r8-*.json`）。
- mapping 统计：10,656 类 / 10,456 重命名 / 1,199 移除；其中项目类 4,053 / 重命名 4,040 / 移除 211。

### 4.2 identity 门（诚实口径）

| 调用 | 结果 |
|---|---|
| `identity`（不带 clean/head 要求） | **exit 0**：两包 ZIP 完整（`zip_integrity: null`）；APK 内嵌 revision = `8dd29e96…`，`revision_is_commit: true`，`commits_behind_head: 0`；`dirty_paths: 5458 / 5459`（`-uall`，随证据文件写入增长） |
| `identity --require-clean --require-head` | **exit 1**：`dirty_paths: 5475` ≠ 0 —— 这是真实状态（脏树），不是脚本坏 |

> `revision` 只是 HEAD sha；由于工作树含数百条未提交改动，**它不代表所编代码**。
> 只有主会话提交后用同一命令复跑，`--require-clean` 才可能转绿（§5 的干净树失败进一步证明 HEAD 当前不自洽）。

### 4.3 release manifest 门（RELEASE.md gate 3）

```bash
$ python -I tools/ci/check_release_manifest.py
manifest gate OK: 9 checked + 4 debug merged manifest(s), 5 debug-only component(s) both absent from checked and present in debug
# EXIT=0
```

说明：debug merged manifest 由 `:app:processLocalFirstDebugMainManifest :app:processStrictOfflineDebugMainManifest`
复核为 **25 tasks up-to-date**（Gradle 判定清单输入未变，`refresh-debug-manifests.log`），随后重跑本门仍 exit 0。
检查同时验证“debug-only 组件在 release/internal 缺席、在 debug 出现”，5 个组件两侧行为符合预期
（`check-release-manifest.log`）。

### 4.4 判据自检与负控汇总

| 项 | 结果 | 文件 |
|---|---|---|
| `pytest tools/tests/test_j_release_verify.py tools/tests/test_release_version_code.py` | **33 passed**（2.58s） | `gates-5.txt` |
| version 差 1 / 缺期望值 | exit 1 / exit 1 | `negative-version-101178.json`、`negative-version-missing-expected.json` |
| AAB manifest 差 1 | exit 1 | `negative-aab-manifest-101178.json` |
| apk16k selftest | `ok: true`（3 条 `p_align=0x1000`） | `negative-apk16k-selftest.json` |
| signing debug APK vs release AAB | exit 1（身份不同） | `negative-signing-debug-apk-vs-release-aab.json` |

---

## 5. 干净树构建尝试（本地 clone，不改主树、不 stash）——**BLOCKED**

### 5.1 做法（未触碰主树）

```bash
OUT=D:/smbk-clean-b51-8dd29e96
git clone --no-hardlinks --single-branch --branch main "D:/smart mistake book" "$OUT/src"
# HEAD=8dd29e963975ff44703f638064322be0e7494f98；git status --porcelain = 0；is-shallow = false
grep '^sdk.dir=' "D:/smart mistake book/local.properties" > "$OUT/src/local.properties"   # 只拷 sdk.dir，避开 Provider key
```

- **不改主树**：clone 只读 `D:/smart mistake book`；未 stash、未 checkout、未改任何源文件。
- **clone ≠ 当前工作树**：clone 只含 HEAD 提交，**不含 683 条未提交改动**。这是本实验的硬边界（§5.4）。

### 5.2 第一次离线尝试：`--offline` 不覆盖 wrapper 分发下载

直接跑 `./gradlew.bat --offline …` 时，wrapper 从 `https://downloads.gradle.org/distributions/gradle-9.6.1-bin.zip`
开始下载（`clean-clone-dryrun.log`，已停止）。根因：本仓 `gradlew.bat:78` 固定
`-Dgradle.user.home=%APP_HOME%\.gradle`，clone 的 Gradle home 是 `clone/.gradle`，是空的；
`--offline` 只作用于依赖解析，不覆盖 wrapper 自身的发行版下载。

### 5.3 用暖缓存播种后：配置离线成功，完整构建失败

把主树已验证的缓存**复制**进 clone 的 `.gradle`（不共享主树运行目录）：

```bash
cp -r "D:/smart mistake book/.gradle/wrapper/dists/gradle-9.6.1-bin" "$OUT/src/.gradle/wrapper/dists/"
cp -r "D:/smart mistake book/.gradle/caches/modules-2" "$OUT/src/.gradle/caches/"   # 817M，12.5s
```

离线 dry-run：

```bash
./gradlew.bat --offline --no-daemon --no-build-cache --console=plain --dry-run \
  :app:assembleStrictOfflineRelease :app:bundleStrictOfflineRelease -Psmbk.versionCode=101177
# BUILD SUCCESSFUL in 1m 2s；Configuration cache entry stored
# 下载/No cached version/Could not resolve 命中数 = 0
```

完整构建：

```bash
./gradlew.bat --offline --no-daemon --no-build-cache --console=plain \
  :app:assembleStrictOfflineRelease :app:bundleStrictOfflineRelease -Psmbk.versionCode=101177
# BUILD FAILED in 1m 24s；264 actionable tasks: 264 executed；CLEAN_BUILD_EXIT=1
```

**阻断点**（`clean-clone-build.log:347-348,8461-8464`）：

```
> Task :core:export:compileReleaseKotlin FAILED
e: file:///D:/smbk-clean-b51-8dd29e96/src/core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DocxMistakeRenderer.kt:268:31
   Unresolved reference 'AI_GENERATED_DOCUMENT_NOTICE'.
* What went wrong:
Execution failed for task ':core:export:compileReleaseKotlin' …
> Compilation error. See log for more details
```

**根因（已核实到文件级）**：

- HEAD 的 `core/export/.../DocxMistakeRenderer.kt:268` 使用 `AI_GENERATED_DOCUMENT_NOTICE`（`git show HEAD:…` 可见）；
- 该常量的定义文件 `core/export/.../AiGeneratedDocumentNotice.kt:16` 在工作树里是 **未跟踪文件**（`??`），
  `git ls-tree HEAD -- …/AiGeneratedDocumentNotice.kt` 为空；
- ⇒ **HEAD 提交自身不可编译**，干净树构建被“未提交但必需的源文件”阻断。这不是 `--offline` 的失败，
  也不是缓存缺失（下载/No cached version/Could not resolve 均为 0）。

### 5.4 结论与出口

| 项 | 结论 |
|---|---|
| 离线依赖解析 | **可离线**：暖缓存 + 本地 wrapper 分发后，配置/解析 0 下载 |
| 干净树完整构建 | **BLOCKED**：HEAD 缺未跟踪常量定义，`:core:export` 编译失败 |
| 修复动作（主会话） | 提交 `AiGeneratedDocumentNotice.kt` 及所有“HEAD 引用、工作树未跟踪”的必需文件；提交后再做一次干净 clone 构建（预计可能暴露第二个同类缺失，需逐个提交） |
| 边界 | 即使提交后构建成功，clone 产物也只能绑定“提交后的 HEAD”；**当前 release 产物（脏树）不能用它背书**。主会话提交后须用同一 `-Psmbk.versionCode` 与新 HEAD 重出包并重跑 §3/§4 全部门 |
| 遗留 | clone 与私有 Gradle home 留在 `D:/smbk-clean-b51-8dd29e96/`（实测 **4.5 GB**，其中 `src/.gradle` 2.0 GB），未自动删除 |

---

## 6. 外部 BLOCKED（摘要；逐项动作见 `external-blockers.md`）

| # | 项 | 状态 | 本机事实 | 所需动作 |
|---|---|---|---|---|
| X-1 | Play 开发者账号 / Play Console | **BLOCKED** | 仓内无 Play 凭据；`blocked-runbook.md:506` J-5 组 | 账号持有人开通并建内部/封闭轨道 |
| X-2 | 生产签名 keystore / 上传密钥 | **BLOCKED** | 当前 DN `OU=NOT-PLAY-PRODUCTION`、指纹 `2c8415b2…`（本地测试钥） | 生产持有人交付上传密钥 → `RELEASE_*` 注入 → Play 登记指纹 |
| X-3 | App 备案号（工信部 ICP） | **BLOCKED** | 应用内占位渲染「尚未取得」（`PrivacyPolicyCopy.kt:26-33,49-54`） | 发布者完成备案 → 回填备案号/主体/联系方式 |
| X-4 | 公网隐私政策 URL | **BLOCKED** | 应用内政策全文/入口已在（`SecondaryScreens.kt:658-695`）；无公网 URL | 开发者域名 + HTTPS 托管 → 商店页/应用内同链接 |
| X-5 | 图像 provider（MVA IMG 臂） | **BLOCKED** | 无图像 provider；IMG 臂上限 63/120（`mva/README.md:224-225`） | Provider 持有人提供图像能力 → IMG 臂重新采样判门 |

关联未闭合：arm64/16KB 运行时（仅 x86_64 模拟器 `emulator-5592`，`PAGE_SIZE=4096` / API 34；
`adb` 需用全路径 `C:/Android/Sdk/platform-tools/adb.exe`，见 `device-probe.txt`）、
`bundletool-all` 官方交叉验证（只有库 jar）、Play data safety 表单（依赖 X-1..X-4）。

---

## 7. 发布判定（本线）

1. **可以声明**：当前工作树 + 本地测试钥能稳定产出两组 APK/AAB；versionCode、签名、16KB/ABI、AAB 版本字段、
   mapping、manifest 门全部按既有判据通过；所有负控按预期变红。
2. **不能声明**：
   - 不能声明“干净树可复现”——clone 构建在 HEAD 的 `:core:export` 编译失败（未跟踪常量）；
   - 不能声明“生产签名/可上传 Play”——签名身份是 `NOT-PLAY-PRODUCTION`；
   - 不能声明“发布就绪”——Play 账号/备案号/公网隐私 URL/图像 provider 全在外部（X-1..X-5）。
3. **下一步最小动作**：主会话提交必需未跟踪文件（至少 `AiGeneratedDocumentNotice.kt`）→ 重跑干净 clone；
   生产密钥持有人注入 `RELEASE_*` → 重出包并复跑 §3/§4；外部五项按 `external-blockers.md` 解锁。
4. **修复环复核（2026-11-11，只读）**：`git ls-files --error-unmatch core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedDocumentNotice.kt`
   仍报 `did not match any file(s) known to git`；`git cat-file -e HEAD:<同一路径>` 在 HEAD
   `8dd29e96…` 上 exit **128**；该文件 sha256 `f75ac862f32d1911769df823a2d5e86e1ea889375ed4b80c234c9ddaa2ac74a1`。
   因此干净树构建仍 **BLOCKED**，发布判定不变；提交动作属主会话范围，修复环不代提交。

---

## 8. 证据文件索引

`baseline-commands.txt`、`version-code-derivation.json`、`validateVersionCode-*.log`、
`validateReleaseSigning-*.log`、`build-release.log`、`build-localFirst-force-rebuild.log`、
`artifacts.sha256`、`version-*.json`、`apk16k-*.json`、`aab16k-*.json`、`aab-manifest-*.json`、
`apksigner-*.txt`、`jarsigner-*.txt`、`keytool-aab-*.txt`、`keytool-keystore.txt`、
`zipalign-*.txt`、`r8-*.json`、`signing-*.json`、`identity-*.json`、`check-release-manifest.log`、
`negative-*.json`、`gates-1..5.txt`、`clean-clone-baseline.txt`、`clean-clone-dryrun*.log`、
`clean-clone-build.log`、`refresh-debug-manifests.log`、`device-probe.txt`、`external-blockers.md`、
`EVIDENCE.sha256`（56 项，已 `sha256sum -c` 复验）。
