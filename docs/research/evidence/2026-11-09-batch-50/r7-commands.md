# R7 发布/签名/16KB/versionCode 方案：本轮实跑记录

- 批次：50（2026-11-08/09）
- 角色：R7 发布方案研究线（只读 + 写本目录证据；**未改任何源码/构建脚本/台账**，未跑任何 Gradle）
- 工作树：`HEAD=a87fc70d15c8ad726cee17422dbeef0b4977747d`，`git status --porcelain | wc -l` = **582**（写作时点；四份文件落盘后复测 **586**）
- 机器：Windows 10.0.26200 / Git Bash / JDK 21.0.11 (Temurin) / build-tools 37.0.0 / 无 NDK
- 口径：本文件每条都标了命令；输出为原文转写（长输出截断处标注）。**本轮零 Gradle、零设备写入、零仓外下载。**

---

## 1. 版本与树

```bash
$ git rev-list --count HEAD                     → 1160
$ git rev-list --first-parent --count HEAD      → 1075
$ git rev-parse --is-shallow-repository         → false
$ git status --porcelain | wc -l                → 582
$ git status --porcelain -- app/src/main core/*/src/main feature/*/src/main | wc -l  → 26
$ git diff --stat HEAD -- app/build.gradle.kts  → （空，工作树与 HEAD 同）
$ env | grep -c '^RELEASE_'                     → 0
```

`app/build.gradle.kts:33-34`（读码）：`versionCode = 2`、`versionName = "0.2.0"`。

盘上 `app/build/outputs/apk/strictOffline/release/output-metadata.json`：
`versionCode: 2`、`versionName: "0.2.0-offline"`。

---

## 2. `j_release_verify.py` 现状读数

### 2.1 `version`（当前 gradle）

```bash
$ python -I tools/ci/j_release_verify.py version \
    --gradle app/build.gradle.kts \
    --metadata app/build/outputs/apk/strictOffline/release/output-metadata.json
```
→ **exit 0**
```json
{"gradle": {"version_code": 2, "version_name": "0.2.0",
            "abi_filters": ["arm64-v8a", "x86_64"]},
 "output_metadata": {"versionCode": 2, "versionName": "0.2.0-offline"},
 "errors": []}
```

### 2.2 `version` 对两种注入形态的负控（本轮主要发现之一）

两个探针文件（写在仓外临时目录，非仓内源码）：

- `probe-a.gradle.kts`：`versionCode = smbkVersionCode`
- `probe-b.gradle.kts`：`versionCode = SMK_VERSION_CODE_BASE + smbkGitCommitCount`

```bash
$ python -I tools/ci/j_release_verify.py version --gradle <probe>.gradle.kts \
    --metadata app/build/outputs/apk/strictOffline/release/output-metadata.json
```
两种形态**都 exit 1**，错误逐字相同：
```json
"errors": ["versionCode/versionName not found in gradle script",
           "versionCode differs from output-metadata"]
```
根因：`tools/ci/j_release_verify.py:92` 是 `re.search(r"versionCode\s*=\s*(\d+)", text)`——
要求 `=` 后**紧跟数字**。

### 2.3 `signing`（无参 / 带 APK）

```bash
$ python -I tools/ci/j_release_verify.py signing --root .
```
→ **exit 1**，`env_missing` 四项齐全，`errors`：
`release signing env missing: RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD, RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD` /
`signer artifact missing…` / `AAB certificate validity not readable`

```bash
$ python -I tools/ci/j_release_verify.py signing --root . \
    --apk app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk \
    --apksigner C:/Android/Sdk/build-tools/37.0.0/apksigner.bat
```
→ **exit 1**（同上三条）；`apk_signer`：
```json
{"exit": 0,
 "sha256": "9dc07e9f5ce9a409f38596ef89b39ec8ea561feb26d3c61dfbf1a958c6056e20",
 "schemes": {"v1": true, "v2": true, "v3": false}}
```

### 2.4 AAB 证书解析路径（用探针包驱动真实代码路径）

探针包 = 本轮用 `keytool -genkeypair` + `jarsigner` 自签的普通 jar（**非 AAB、非发版物**），
用来验证"`keytool -printcert -jarfile` 在中文 locale 下能否被解析"：

```bash
$ python -I tools/ci/j_release_verify.py signing --root . --aab /tmp/.../probe.jar
```
→ `aab_signer`（原文）：
```json
{"sha256": "b56f5e431d0d3fbe397e77ac2aa018058d2d8e70e98163e5d9467cd65ff2d150",
 "owner": "CN=SmartMistakeBook Local Release Test, OU=NOT-FOR-PLAY-PRODUCTION, O=SmartMistakeBook, C=CN",
 "not_before": "2026-11-08T13:35:55",
 "not_after": "2054-03-26T13:35:55",
 "exit": 0}
```
⇒ 中文 locale 下 owner 与有效期都能解析（`j_release_verify.py:431-446` 的双语正则有效）。

---

## 3. APK 侧 16KB

```bash
$ /c/Android/Sdk/build-tools/37.0.0/zipalign.exe -v -c -P 16 4 \
    app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
```
→ 末行 `Verification successful`，**exit 0**

```bash
$ python -I tools/ci/j_release_verify.py apk16k \
    --apk app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
```
→ **exit 0**，`total: 14`，`errors: []`。逐条见 `r7-apk16k.json`（本目录）。

负控（把第一个 `.so` 的 `PT_LOAD p_align` 改成 `0x1000` 后必须判红）：

```bash
$ python -I tools/ci/j_release_verify.py apk16k --apk <同一 APK> --selftest
```
→
```json
{"ok": true, "patched_load_segments": 3,
 "errors": ["PT_LOAD p_align=0x1000 < 0x4000", "…", "…"],
 "target": "lib/arm64-v8a/libLiteRt.so"}
```

```bash
$ /c/Android/Sdk/build-tools/37.0.0/apksigner.bat verify --print-certs --verbose <同一 APK>
```
→ `v1=true`、`v2=true`、`v3/v3.1/v3.2/v4=false`；DN：
`CN=WS8 Ephemeral Local Only, OU=NotForRelease, O=Local, C=CN`；证书 SHA-256 `9dc07e9f…6e20`。
⇒ 盘上 release APK **是临时本机钥匙签的，非生产**。

---

## 4. AAB 侧 16KB：`aab16k` 正负控矩阵（本轮新跑）

本机**没有 AAB 产物**（`find . -maxdepth 4 -name '*.aab'` → 空），所以用合成夹具验证**判据本身**：
夹具只改 `BundleConfig.pb` 与 `lib/` 树，是"门控夹具"，**不构成任何真实 AAB 的合规证据**。

```bash
$ python -I tools/ci/j_release_verify.py aab16k --aab <fixture>.aab
```

| 夹具 | `alignment` | `abi_set` | `errors` | exit |
|---|---|---|---|---|
| `good.aab`（`{1:1, 2:2}`） | 2 | arm64-v8a, x86_64 | `[]` | **0** |
| `wrong-alignment.aab`（`{1:1, 2:1}`） | 1 | 同 | `["PAGE_ALIGNMENT_16K not declared (found 1)"]` | **1** |
| `no-config.aab`（无 BundleConfig.pb） | null | 同 | `["PAGE_ALIGNMENT_16K not declared (found None)"]` | **1** |
| `32bit.aab`（多 `armeabi-v7a`） | 2 | arm64-v8a, **armeabi-v7a**, x86_64 | `["ABI set [...] != abiFilters ['arm64-v8a','x86_64']"]` | **1** |
| `no-quickjs.aab`（缺 `libquickjs.so`） | 2 | 同 good | `["required native library missing: libquickjs.so"]` | **1** |

⇒ 判据在"缺声明 / 声明值错 / 混 32 位 / 缺必需库"四种负向上都**可变红**。
（`apk16k` 有内建 `--selftest`；`aab16k` 没有，本矩阵是它的等价负控，**建议落成 `--selftest`**。）

## 5. bundletool：本机只有库 jar，不可执行

```bash
$ unzip -p …/bundletool/1.18.3/…/bundletool-1.18.3.jar META-INF/MANIFEST.MF | grep -c Main-Class
→ 0
$ java -jar …/bundletool-1.18.3.jar dump config --bundle=x.aab
→ …bundletool-1.18.3.jar中没有主清单属性
```
（该 jar 13,371,507 B，MANIFEST 只有 `Manifest-Version` + `Class-Path` 依赖清单。）

## 6. keystore / 证书命令实测（PKCS12 陷阱）

```bash
$ keytool -genkeypair -keystore probe.p12 -storetype PKCS12 -alias smbk-local-test \
    -keyalg RSA -keysize 4096 -validity 10000 \
    -storepass 'storepass-probe' -keypass 'keypass-probe' \
    -dname "CN=SmartMistakeBook Local Release Test, OU=NOT-FOR-PLAY-PRODUCTION, O=SmartMistakeBook, C=CN"
```
→ **exit 0**，但输出首行（原文，中文 locale）：
```
警告: PKCS12 密钥库不支持其他存储和密钥口令。正在忽略用户指定的-keypass值。
```
⇒ `RELEASE_KEY_PASSWORD` 若与 `RELEASE_STORE_PASSWORD` 不同，PKCS12 会**静默忽略** keypass，
真钥匙口令变成 storepass；签包时以"口令错"收场，错误文本不指向根因。

```bash
$ keytool -list -v -keystore probe.p12 -storetype PKCS12 -alias smbk-local-test -storepass 'storepass-probe'
→ SHA256: B5:6F:5E:…:D1:50

$ jar --create --file probe.jar probe.txt
$ jarsigner -keystore probe.p12 -storetype PKCS12 -storepass 'storepass-probe' probe.jar smbk-local-test
→ exit 0（警告：签名者证书为自签名证书）
$ jarsigner -verify probe.jar
→ exit 0（提示无时间戳，最早到期 2054-03-26）
$ keytool -printcert -jarfile probe.jar
→ SHA256: B5:6F:5E:…:D1:50   （与 keystore 条目一致）
```

## 7. `apksigner` 不能验 AAB（实测异常）

```bash
$ /c/Android/Sdk/build-tools/37.0.0/apksigner.bat verify probe.jar
→ Exception in thread "main" com.android.apksig.apk.ApkFormatException: Missing AndroidManifest.xml
    at com.android.apksig.ApkSigner.getAndroidManifestFromApk(ApkSigner.java:982)
```
⇒ AAB（无 `AndroidManifest.xml` 在 ZIP 根）必须走 `jarsigner -verify` / `keytool -printcert -jarfile`。

## 8. `.gitignore` 对 keystore 扩展名的覆盖

```bash
$ git check-ignore -v probe.p12   → 无输出，exit 1（**未忽略**）
$ git check-ignore -v probe.jks   → .gitignore:18:*.jks   probe.jks，exit 0
```
⇒ `.gitignore:18-20` 只覆盖 `*.jks` / `*.keystore` / `keystore.properties`；**`*.p12` 不在内**。
PKCS12 是 JDK 9+ 的默认 storetype，按 `.p12` 命名放进仓内会被 git 直接收。

## 9. `local.properties` 现状（与 2026-11-05 记录不一致）

```bash
$ sed -n 's/^\([^=]*\)=.*/\1/p' local.properties
sdk.dir
SMB_REAL_PROVIDER_BASE_URL
SMB_REAL_PROVIDER_KEY
SMB_REAL_PROVIDER_MODEL
$ awk -F= '/^SMB_REAL_PROVIDER_KEY=/{print "KEY_LEN=" length($2)}' local.properties
KEY_LEN=35
```
⇒ 该文件**已不只是 `sdk.dir`**，含一个 35 字符的 Provider key 值（值不在此转写）。
`docs/research/2026-11-05-clean-tree-release.md:57` 的"实测其内容只有 `sdk.dir` 一个键（无凭据）"
**在今天的树上已不成立**，§4.1 第 2 步"整文件 `cp local.properties`"因此成为泄密路径。

## 10. 设备

```bash
$ adb devices -l
emulator-5590   device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 …
$ adb -s emulator-5590 shell "getconf PAGE_SIZE; getprop ro.build.version.sdk; getprop ro.product.cpu.abi"
4096
34
x86_64
```
⇒ 当前唯一在跑设备是 **4KB / API 34 / x86_64**，不能证明 16KB 运行时；无 arm64 真机。

## 11. 本轮清理

仓外临时目录 `%TEMP%/smb-sign-probe`、`%TEMP%/smb-aab-probe`、`%TEMP%/smb-aab-script`
（探针 keystore/jar/夹具/脚本）与 `/tmp/out-*.json`、`/tmp/aab-*.json` 已删除。
仓内只新增本目录三份证据 + `docs/research/2026-11-09-release-signing.md`。
