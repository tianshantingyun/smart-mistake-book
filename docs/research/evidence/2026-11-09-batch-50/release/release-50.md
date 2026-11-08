# 发布线（计划 item 35–37）实跑证据：versionCode 注入 / 本地 release key / APK·AAB 校验 / mapping

- 批次：50（2026-11-08/09）｜计划：`.zcode/plans/plan-2026-11-09-batch-50-release.md:70-72`（发布 35/36/37）、`:15`（研究结论）
- 落地会话：发布工程子线；本轮 **Gradle 串行实跑**，设备只读
- 机器：Windows 10.0.26200 / Git Bash / JDK 21.0.11 (Temurin) / build-tools 37.0.0 / AGP 9.3.0 / Gradle 9.6.1
- 树基线：`HEAD=a87fc70d15c8ad726cee17422dbeef0b4977747d`；**构建时工作树是脏的**
  - `git status --porcelain | wc -l`：会话开工 **683** → **构建启动时 684**（多出的是本线 `app/build.gradle.kts`）
  - `git status --porcelain -uall | wc -l`：构建后首测 **4314**，写盘一条证据后 **4315**（`identity` 用 `-uall` 展开未跟踪目录）
  - ⇒ 本次产物**不是干净树产物**；`apk` 内嵌 `revision` 只是 HEAD sha，**不代表所编代码**（含数百条未提交改动）。
    主会话提交后用同一命令复跑才能拿到可绑定 HEAD 的包（本文件 §7 登记为 BLOCKED-until-commit）。
- 证据目录：`docs/research/evidence/2026-11-09-batch-50/release/`（本文件所在目录）

---

## 0. 结论（先看这张表）

| # | 项 | 结果 | 证据 |
|---|---|---|---|
| 1 | versionCode 构建注入 | **落地并实跑**：`N=100000+1160=101160`，经 `-Psmbk.versionCode` 注入；缺参/非整数/越界 fail-closed | §1；`validateVersionCode-*.log`；`version-*.json` |
| 2 | 本地 release keystore | **已创建**，PKCS12，DN 带 `NOT-PLAY-PRODUCTION`，`.tmp/` 下被 `.gitignore:9` 覆盖，仓内 0 个被跟踪 keystore | §2 |
| 3 | assembleRelease / bundleRelease | **exit 0**（4m56s，481 tasks）：两 flavor × APK+AAB 全部签出 | §3；`build-release.log` |
| 4 | APK 签名 | v1+v2 `true`，证书 SHA-256 `2c8415b2…5eba`（= keystore），DN 含 `NOT-PLAY-PRODUCTION` | §4.1；`apksigner-*.txt` |
| 5 | AAB 签名 | `jarsigner -verify` exit 0；`keytool -printcert -jarfile` 指纹同 APK；`signing` 门 exit 0 | §4.2；`jarsigner-*.txt`、`keytool-aab-*.txt`、`signing-*.json` |
| 6 | APK 16KB | `zipalign -v -c -P 16 4` → `Verification successful`；`apk16k` 14/14 `.so` STORED、偏移 0 mod 16384、`elf_errors: []` | §4.3；`zipalign-*.txt`、`apk16k-*.json` |
| 7 | AAB 16KB + ABI | `aab16k`：`alignment: 2`（PAGE_ALIGNMENT_16K）、ABI 集合 `{arm64-v8a, x86_64}`、7 个必需库全在 | §4.4；`aab16k-*.json` |
| 8 | AAB versionCode | 从 AAB 自身 proto manifest 读出 **101160**（字符串与编译整数一致）、versionName `0.2.0-offline`/`0.2.0-localfirst` | §4.5；`aab-manifest-*.json` |
| 9 | mapping 保留 | `mapping.txt` 140,341,720 B / 968,647 行 / R8 9.3.16；**与 AAB 内嵌 `proguard.map` 逐字节同 sha**；`r8` 门 exit 0 | §5；`r8-*.json` |
| 10 | 负控 | 期望号差 1、AAB 期望号差 1、`apk16k --selftest`、debug APK vs release AAB 验签，全部按预期变红 | §6 |
| 11 | 干净树构建 | **BLOCKED（未做）**：树脏（构建启动 684 条 / `-uall` 4314 路径），须主会话先提交再复跑；不得声称干净 | §7 |
| 12 | Play 生产身份 / 备案号 / Play 账号 | **BLOCKED（外部）**，未伪造 | §7 |

---

## 1. versionCode 构建注入

### 1.1 机制（改了什么）

| 位置 | 改动 |
|---|---|
| `app/build.gradle.kts:12-21` | 新增 `smbkVersionCodeRaw = providers.gradleProperty("smbk.versionCode")`、`smbkVersionCode = raw?.toIntOrNull() ?: 2` |
| `app/build.gradle.kts:43` | `versionCode = 2` → `versionCode = smbkVersionCode`（`versionName` 保持字面量 `"0.2.0"`） |
| `app/build.gradle.kts:172-188` | 新增 `validateVersionCode`：缺 `-P` / 非整数 / 超出 `1..2100000000` 都在打包前抛 GradleException |
| `app/build.gradle.kts:190-197` | 与 `validateReleaseSigning` 同一挂法，对 `assemble*/bundle*/package*` + 名字含 `Release` 的任务生效 |
| `tools/ci/j_release_verify.py:645-653,708-744` | `version` 门新增 `--expected-code`：注入形态（gradle 里无数字）不给期望值即红；给了则比对 output-metadata |
| `tools/tests/test_j_release_verify.py:180-215` | 3 条新用例（注入必红 / 期望值命中 / 期望值差 1 必红） |
| `tools/ci/release_version_code.py`（新增） | git 侧算号器：输出 `100000+提交数`，**shallow 仓库直接拒号**；4 条单测（正常/ shallow / 非仓库 / --out） |
| `tools/tests/test_release_version_code.py`（新增） | 上述 4 条 |

推导口径（研究结论 `docs/research/2026-11-09-release-signing.md:§3`）：`N = 100000 + git rev-list --count HEAD`，
git 只在发布脚本里算号，**不在 Gradle 配置期读 git**（干净树无 `.git`、CI shallow 得 1、configuration cache 可能复用旧号）。
本轮实跑：

```bash
$ python -I tools/ci/release_version_code.py --root .
{"commit_count": 1160, "shallow": false, "version_code": 101160, "errors": []}   # exit 0
$ python -I -m pytest tools/tests/test_release_version_code.py -q
4 passed
```
⇒ **N = 101160**（与注入值、三处产物读数一致）。输出：`version-code-derivation.json`。

> 单调性：下一次发版必须 `> 101160`（Play 拒绝复用/降级）。`versionName` 仍是 `0.2.0` + flavor 后缀，未随版本号改动。

### 1.2 先红后绿（逐条实跑）

```bash
# 红① 现门读不懂注入形态（改动前，实测 exit 1，错误 = versionCode not found）
python -I -m pytest tools/tests/test_j_release_verify.py -q -k "version_injected or expected_code"
#   → 2 failed, 1 passed（--expected-code 未识别）
# 绿① 加 --expected-code 后
python -I -m pytest tools/tests/test_j_release_verify.py -q
#   → 29 passed

# 红② release 构建缺 -P
./gradlew.bat --console=plain --no-daemon :app:validateVersionCode
#   → exit 1：Release build requires -Psmbk.versionCode=N (…)
# 绿② 带 -P
./gradlew.bat --console=plain --no-daemon :app:validateVersionCode -Psmbk.versionCode=101160
#   → exit 0
# 红③ 非整数
./gradlew.bat --console=plain --no-daemon :app:validateVersionCode -Psmbk.versionCode=abc
#   → exit 1：smbk.versionCode is not an integer: abc
# 红④ 越界
./gradlew.bat … -Psmbk.versionCode=2100000001
#   → exit 1：smbk.versionCode out of Play range 1..2100000000: 2100000001
```

原始输出：`validateVersionCode-missing-P.log`、`validateVersionCode-101160.log`、`validateVersionCode-noninteger.log`。

### 1.3 注入是否真的进了产物（三条独立读数）

| 读数 | 值 | 来源 |
|---|---|---|
| 合并 manifest（构建中间物） | `android:versionCode="101160"`（两 flavor） | `app/build/intermediates/merged_manifests/strictOfflineRelease/processStrictOfflineReleaseManifest/AndroidManifest.xml` |
| APK `output-metadata.json` + `aapt2 dump badging` | `versionCode='101160'`，versionName `0.2.0-offline` / `0.2.0-localfirst` | `aapt2-badging-*.txt` |
| AAB 自身 proto manifest | `version_code: 101160`（string `"101160"` 与 compiled int `101160` 一致） | `aab-manifest-*.json` |

`version` 门（注入形态）实跑：`--expected-code 101160 --name-suffix=-offline` → **exit 0**，payload `gradle.version_code: null`
（预期：注入后 gradle 文本无数字）、`output_metadata.versionCode: 101160` → `version-strictOffline.json`、`version-localFirst.json`。

---

## 2. 本地 release keystore（明确非 Play 生产身份）

### 2.1 参数

| 项 | 值 |
|---|---|
| 路径 | `D:/smart mistake book/.tmp/release-signing/smbk-local-release.keystore` |
| 格式 / 类型 | PKCS12（`-storetype PKCS12`）；`keytool -list` → `条目类型: PrivateKeyEntry` |
| 别名 | `smbk-local-release` |
| 算法 | RSA 4096 / SHA384withRSA，自签名 |
| DN | `CN=SmartMistakeBook Local Release, OU=NOT-PLAY-PRODUCTION, O=SmartMistakeBook, C=CN` |
| 证书 SHA-256 | `2C:84:15:B2:60:DC:6B:64:5A:33:49:70:77:C8:19:DD:C2:CB:B8:5E:AA:B7:F0:0F:2D:87:42:4B:EC:AD:5E:BA` |
| 有效期 | 2026-11-08 21:03:04 → **2054-03-26 21:03:04**（10000 天） |
| 口令 | 随机 48 hex，仅存于 `.tmp/release-signing/release-signing.env`；PKCS12 下 keypass 必须等于 storepass（否则 keytool 静默忽略 `-keypass`，见 R7 实测） |

### 2.2 "非 Play 生产身份"如何被机器读到（不是只写在文档里）

- `apksigner verify --print-certs` 与 AAB 的 `keytool -printcert` 都原样打出 `OU=NOT-PLAY-PRODUCTION`（§4.1/§4.2 证据文件）。
- 启用 Play App Signing 后，这把 key 只是**上传密钥**；用户设备上的证书由 Google 托管，本文件**不主张**它是发布证书（R7 §4.5）。

### 2.3 gitignored 证据

```bash
$ git check-ignore -v .tmp/release-signing/smbk-local-release.keystore .tmp/release-signing/release-signing.env
.gitignore:9:.tmp/	.tmp/release-signing/smbk-local-release.keystore
.gitignore:9:.tmp/	.tmp/release-signing/release-signing.env
$ git status --porcelain -- .tmp/release-signing | wc -l
0
$ git ls-files | grep -Ei '\.(jks|keystore|p12)$' | wc -l
0
```

> `.gitignore` 当前被另一条线改过（`M .gitignore`），本轮**未改它**；用的是既有 `.tmp/` 规则。
> 若要长期复用，建议主会话把 keystore 移到仓外（如 `D:/keys/smbk/`）——`.tmp/` 属临时目录，可能被清理。

---

## 3. 出包（串行 Gradle，一次调用四任务）

```bash
set -a; . .tmp/release-signing/release-signing.env; set +a
./gradlew.bat --console=plain --no-daemon \
  :app:assembleStrictOfflineRelease :app:bundleStrictOfflineRelease \
  :app:assembleLocalFirstRelease  :app:bundleLocalFirstRelease \
  -Psmbk.versionCode=101160
```

→ **BUILD SUCCESSFUL in 4m 56s；481 actionable tasks: 178 executed, 303 up-to-date**；`BUILD_EXIT=0`
日志：`build-release.log`（含 `> Task :app:validateVersionCode`、`> Task :app:validateReleaseSigning` 均在打包前执行）。

按计划字面任务名再跑一次聚合任务（AGP 会展开到全部 Release 变体）：

```bash
./gradlew.bat --console=plain --no-daemon :app:assembleRelease :app:bundleRelease -Psmbk.versionCode=101160
# → BUILD SUCCESSFUL in 15s；481 actionable tasks: 6 executed, 475 up-to-date
# → 实执行：assembleRelease / bundleRelease / assemble|bundle × {strictOffline, localFirst}Release
```

→ **exit 0**；两 APK/两 AAB 的 sha256 与上表**逐字节相同**（重跑不改变产物，确定性可复算）。日志：`build-aggregate-release.log`。

产物（两 flavor；`assembleRelease`/`bundleRelease` 在有 flavor 的项目里就是这两个任务名）：

| 产物 | 字节 | sha256 |
|---|---|---|
| `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk` | 156,231,853 | `67413eadebde2329e3c24f7196f610b01a75fa0bb7981d7afebafbacd1ba8e1f` |
| `app/build/outputs/apk/localFirst/release/app-localFirst-release.apk` | 156,232,415 | `1bf98b0029032ecb5c243c3ada3ac19183e53e0a912ab630510da12866d34dec` |
| `app/build/outputs/bundle/strictOfflineRelease/app-strictOffline-release.aab` | 125,574,691 | `edaf058ded0de18e1706c971d726174ed08b13467bd65af7c0902ed251fe3d88` |
| `app/build/outputs/bundle/localFirstRelease/app-localFirst-release.aab` | 125,575,234 | `48b8a6ddc6cb4efc7615d681399b13ef49fb8d00ab109bf9e9cadbe860d03462` |
| `app/build/outputs/mapping/strictOfflineRelease/mapping.txt` | 140,341,720 | `ce595aa09168d19ed2c93faf28f8334acd47b6fd88c1627e4a3286496377277e` |
| `app/build/outputs/mapping/localFirstRelease/mapping.txt` | 140,341,720 | 同上（两 flavor mapping 逐字节相同） |

---

## 4. APK / AAB 校验矩阵

### 4.1 APK 签名（`apksigner`，build-tools 37.0.0）

```
$ apksigner.bat verify --print-certs --verbose <apk>
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme: false      (v3.1/v4 同为 false)
Number of signers: 1
V2 Signer: certificate DN: CN=SmartMistakeBook Local Release, OU=NOT-PLAY-PRODUCTION, O=SmartMistakeBook, C=CN
V2 Signer: certificate SHA-256 digest: 2c8415b260dc6b645a33497077c819ddc2cbb85eaab7f00f2d87424becad5eba
```
两 flavor 同 DN / 同指纹（= §2 keystore）。原始输出：`apksigner-strictOffline.txt`、`apksigner-localFirst.txt`。
v3 为 false 是 AGP 对 minSdk 23 的默认结果，与盘上旧包一致；如需 v3 要显式开（本轮不主张）。

### 4.2 AAB 签名（`apksigner` 不适用，走 jarsigner/keytool）

```bash
$ jarsigner -verify app/build/outputs/bundle/strictOfflineRelease/app-strictOffline-release.aab
jar 已验证。            # exit 0
警告:
  此 jar 包含其证书链无效的条目。原因: PKIX path building failed …（自签名，本地 key 预期内）
  此 jar 包含其签名者证书为自签名证书的条目。
  此 jar 包含的签名没有时间戳（最早到期 2054-03-26）。
  检测到 POSIX 文件权限和/或 symlink 属性。这些属性在进行签名时会被忽略。
  This JAR file contains internal inconsistencies … Manifest is missing when reading via JarInputStream
$ keytool -printcert -jarfile <aab>
        SHA256: 2C:84:15:B2:…:5E:BA
```
两 flavor 均 exit 0、指纹同 keystore：`jarsigner-*.txt`（原始输出为 Windows GBK 编码，未转码）、`keytool-aab-*.txt`。
上述告警对**自签名上传密钥**是预期形态（Play 上传不要求公信 CA、不要求时间戳）；它们不是失败判据，但必须在发布说明里可见。
组合门：`python -I tools/ci/j_release_verify.py signing --root . --apk <apk> --aab <aab> --apksigner …` → **exit 0**（两 flavor）：
`env_present` 四项、`env_missing: []`、`tracked_keystores: []`、`apk_signer.sha256 == aab_signer.sha256`、`aab_signer.not_after 2054-03-26`、`errors: []` → `signing-*.json`。

### 4.3 APK 16KB / ABI

```bash
$ zipalign.exe -v -c -P 16 4 <apk>   # build-tools 37.0.0
Verification successful              # exit 0（两 flavor）
$ python -I tools/ci/j_release_verify.py apk16k --apk <apk>
# strictOffline / localFirst：14 个 .so（arm64-v8a 7 + x86_64 7）、stored=true、offset_mod_16384=0、elf_errors=[]、errors=[]
```
`zipalign-*.txt`、`apk16k-*.json`。无 32 位 ABI：`aapt2 dump badging` → `native-code: 'arm64-v8a' 'x86_64'`。

### 4.4 AAB 16KB 声明 / ABI / 必需库

```bash
$ python -I tools/ci/j_release_verify.py aab16k --aab <aab>
{"alignment": 2, "abi_set": ["arm64-v8a","x86_64"],
 "native_libs": ["libLiteRt.so","libLiteRtClGlAccelerator.so","libandroidx.graphics.path.so",
                 "libdatastore_shared_counter.so","liblitert_jni.so","libmlkit_google_ocr_pipeline.so","libquickjs.so"],
 "errors": []}
```
→ exit 0（两 flavor）；`aab16k-*.json`。

### 4.5 AAB versionCode（本轮新增读数）

AAB 的 manifest 是 protobuf；`aapt2`/`apkanalyzer` 读不了（实测 `could not identify format of APK` /
`NoSuchFileException: /AndroidManifest.xml`），本机也没有可执行 `bundletool-all`。为此新增
`tools/ci/aab_manifest_version.py`（新文件，`tools/ci/aab_manifest_version.py:140-212`）直接从 AAB 字节解码：

```bash
$ python -I tools/ci/aab_manifest_version.py --aab <aab> --expected-code 101160 --expected-name 0.2.0-offline
{"package": "com.tingyun.smartmistakebook.offline", "version_code": 101160,
 "version_name": "0.2.0-offline",
 "version_code_sources": {"string": "101160", "compiled_int": 101160}, "errors": []}
```
两 flavor 均 exit 0；`aab-manifest-*.json`。单测 7 条全绿（`tools/tests/test_aab_manifest_version.py`），
覆盖：字符串/编译整数不一致必红、缺属性必红、缺 manifest 条目必红、期望号/名不符必红。

---

## 5. mapping 保留

| 检查 | 结果 |
|---|---|
| `mapping.txt` 存在且非空 | 140,341,720 B / 968,647 行 / `# compiler: R8` / `compiler_version: 9.3.16` / `min_api: 23` |
| 与 AAB 内嵌 mapping 一致 | `unzip -p <aab> BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map | sha256sum` = `ce595aa0…277e` = `mapping.txt` 的 sha（两 flavor） |
| mapping ↔ APK dex 绑定 | `j_release_verify.py r8 --mapping … --apk …` → exit 0：`classes: 10661`、`renamed: 10461`、`removed: 1199`、`project_classes: 4056`、`negative_control_hits: 0` → `r8-*.json` |
| 归档命名建议 | R7 §7.4：mapping 文件名应带 `<applicationId>-<versionCode>-<versionName>`，本轮未改名（构建产物名固定），登记为后续动作 |

---

## 6. 负控（证明门会红，不是只会绿）

| 负控 | 命令 | 期望 | 实测 |
|---|---|---|---|
| 期望版本号差 1 | `j_release_verify.py version … --expected-code 101161` | exit 1 | **1**（`output-metadata versionCode != --expected-code`）→ `negative-version-101161.json` |
| AAB 期望版本号差 1 | `aab_manifest_version.py --aab <aab> --expected-code 101161` | exit 1 | **1** → `negative-aab-manifest-101161.json` |
| 16KB 判据自检 | `apk16k --apk <apk> --selftest` | `ok: true` 且有 `p_align=0x1000` | **ok: true**，3 条 `PT_LOAD p_align=0x1000 < 0x4000` → `negative-apk16k-selftest.json` |
| 签名身份不符 | `signing --apk <debug APK> --aab <release AAB>` | exit 1 | **1**：`APK/AAB signer identity differs: apk=26466dbfb132af68 aab=2c8415b260dc6b64` → `negative-signing-debug-apk-vs-release-aab.json` |
| 缺签名变量 | `:app:validateReleaseSigning`（无 env） | exit 1 | **1**，列出四变量名 → `validateReleaseSigning-missing-env.log` |

---

## 7. 未做 / 未验证 / BLOCKED（如实，不得当通过）

| ID | 项 | 状态 |
|---|---|---|
| B-1 | **干净树 release 构建** | **BLOCKED（未做）**：构建启动时 684 脏（`-uall` 4314）；`identity --require-clean` 实跑 **exit 1**（`dirty_paths: 4315`）→ `identity-require-clean-strictOffline.json`。主会话提交后须用 §8 命令复跑 |
| B-2 | 产物身份绑定 HEAD | **NOT PROVEN**：APK 内嵌 `revision=a87fc70d…`（`revision_is_commit: true`，`commits_behind_head: 0`），但构建含未提交改动 → 只能当"基于 HEAD 的脏树产物"，不是 HEAD 字节复现 |
| B-3 | `bundletool dump config`（官方 AAB 对齐交叉验证） | **NOT RUN**：本机无 `bundletool-all`（缓存只有库 jar，无 Main-Class）；需外部下载（R7 B-6） |
| B-4 | 16KB 运行时 | **NOT RUN**：唯一设备 `emulator-5590` `getconf PAGE_SIZE` = **4096**、API 34、x86_64；无 arm64 真机 |
| B-5 | Play 生产签名 / App Signing 决策 / 上传证书指纹 | **BLOCKED（外部）**：本地 key 仅测试身份；生产钥须持有人注入 |
| B-6 | 备案号 / Play 开发者账号 / 隐私政策托管 URL | **BLOCKED（外部）**，未伪造（计划 `:69-73`、R7 §8） |
| B-7 | `aab16k --selftest` | **不存在**：该子命令无内建负控（R7 U-10 缺口），本轮用 `aab_manifest_version` 的负控 + 既有夹具矩阵（R7 §5.3）替代，未补 |
| B-8 | 设备安装/升级/降级成对产物 | **NOT RUN**：成对 vc（N/N+1）需第二次出包；`runbook.md` 的 7 项升级/降级仍 BLOCKED（R7 §3.1 F-3） |

---

## 8. 改动文件与复现命令

### 8.1 本轮改动（只加新文件 + 最小 hook；未抢占他线文件）

| 文件 | 状态 | 说明 |
|---|---|---|
| `app/build.gradle.kts` | M（+32/−1） | versionCode 注入 + `validateVersionCode` + 挂载一行 |
| `tools/ci/j_release_verify.py` | 修改（该文件本就 `??` 未跟踪） | `version` 门加 `--expected-code`（最小 hook） |
| `tools/tests/test_j_release_verify.py` | 修改（本就 `??`） | +3 用例 |
| `tools/ci/aab_manifest_version.py` | **新增** | 从 AAB proto manifest 读 versionCode/versionName/package |
| `tools/tests/test_aab_manifest_version.py` | **新增** | 7 条用例 |
| `tools/ci/release_version_code.py` | **新增** | git 算号器（shallow 拒号） |
| `tools/tests/test_release_version_code.py` | **新增** | 4 条用例 |
| `docs/research/evidence/2026-11-09-batch-50/release/**` | 新增 | 本目录证据 |
| `.tmp/release-signing/**` | 新增（ignored） | keystore + env + 构建日志 |

未触碰：`settings.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`、`.gitignore`、`feature/library/build.gradle.kts`（他线 M）、`docs/current/*`（他线 M）。
冲突检查：每阶段前 `git status --porcelain -- <目标>`；`app/build.gradle.kts` 开工时干净，编辑后仅本线 hunk。

### 8.2 复现（干净树，主会话提交后）

```bash
REV=$(git rev-parse HEAD)
[ "$(git rev-parse --is-shallow-repository)" = "false" ] || { echo "shallow: 不能推导 versionCode"; exit 1; }
N=$(( 100000 + $(git rev-list --count HEAD) ))
set -a; . .tmp/release-signing/release-signing.env; set +a
./gradlew.bat --console=plain --no-daemon \
  :app:assembleStrictOfflineRelease :app:bundleStrictOfflineRelease \
  :app:assembleLocalFirstRelease  :app:bundleLocalFirstRelease -Psmbk.versionCode=$N
python -I tools/ci/j_release_verify.py version  --gradle app/build.gradle.kts --metadata <metadata> --expected-code $N --name-suffix=-offline
python -I tools/ci/aab_manifest_version.py --aab <aab> --expected-code $N --expected-name 0.2.0-offline
python -I tools/ci/j_release_verify.py apk16k   --apk <apk>
python -I tools/ci/j_release_verify.py aab16k   --aab <aab>
python -I tools/ci/j_release_verify.py signing  --root . --apk <apk> --aab <aab> --apksigner "C:/Android/Sdk/build-tools/37.0.0/apksigner.bat"
python -I tools/ci/j_release_verify.py r8       --mapping <mapping> --apk <apk>
python -I tools/ci/j_release_verify.py identity --root . --apk <apk> --aab <aab> --mapping <mapping> \
  --gradle app/build.gradle.kts --metadata <metadata> --require-clean --require-head
```
