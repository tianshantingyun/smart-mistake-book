# R7 发布方案：versionCode 构建注入 · 本地 release 签名 · AAB 16KB/ABI 校验 · 干净树构建

- 批次：50（2026-11-08/09）｜计划落点：`.zcode/plans/plan-2026-11-09-batch-50-release.md:23-28`（R 研究，第 7 项在 `:28`）、`:15`（发布研究结论原文）、`:70-72`（item 35/36/37）
- 消费者：item 35（versionCode + keystore + AAB 签名）、item 36（AAB 16KB/ABI/证书/versionCode 校验）、item 37（干净树构建 + mapping）
- 本轮口径：**【本轮实测】**= 2026-11-08 本会话亲手跑过并读到输出；**【读码】**= 本轮读到的仓内行；**【官方】**= 官网页原文；**【未验证】**= 没跑 / 没取到原文，**不得当通过**。
- 本轮**零 Gradle**（纪律：Gradle 只在串行门禁阶段跑）、零设备写入、零仓外下载。证据转写在 `docs/research/evidence/2026-11-09-batch-50/r7-commands.md`（下称 `E-R7`）。

---

## 0. 结论（一页）

| # | 问题 | 结论 | 依据 |
|---|---|---|---|
| 1 | versionCode 怎么注入 | **唯一权威来源是构建参数 `-Psmbk.versionCode=N`**；`N` 由发布脚本用 `100000 + git rev-list --count HEAD` 推导（仅当 `--is-shallow-repository=false`），gradle 内不执行 git | §3；`E-R7 §1/§2` |
| 2 | 为什么不在 gradle 里读 git | 干净树（`git archive`）**没有 `.git`**；CI `actions/checkout@v4` 默认 **shallow**，提交数会算成 1 → 版本号**低于已发布值**（101160 → 100001），Play 按"不可复用/不可降级"拒收 | §3.2/§3.6；`.github/workflows/android-check.yml:43` |
| 3 | 注入会不会打破现有门 | **会**：`j_release_verify.py:92` 的正则要求 `versionCode = <数字>`，两种注入写法都判 `not found`（本轮实测 exit 1）→ gate 必须同时升级（加 `--expected-code`） | §3.4；`E-R7 §2.2` |
| 4 | 本地 release keystore | 仓外 PKCS12、DN 带 `NOT-PLAY-PRODUCTION` 标记、四变量沿用 `RELEASE_*` 契约（**不能**改成 `keystore.properties`，否则 G-084 变红） | §4；`tools/ci/g_security_gate.py:692-695` |
| 5 | PKCS12 陷阱 | `-keypass` 与 `-storepass` 不同会被**静默忽略**（本轮实测中文告警原文）→ `RELEASE_KEY_PASSWORD` 必须等于 `RELEASE_STORE_PASSWORD` | §4.2；`E-R7 §6` |
| 6 | `.gitignore` 缺口 | `*.jks`/`*.keystore` 已忽略，**`*.p12` 未忽略**（`git check-ignore` exit 1）——而 PKCS12 是 JDK 9+ 默认格式 | §4.1；`E-R7 §8` |
| 7 | AAB 怎么验签 | `apksigner` **不能**验 AAB（实测 `ApkFormatException: Missing AndroidManifest.xml`）；用 `jarsigner -verify` + `keytool -printcert -jarfile` | §5.2；`E-R7 §7` |
| 8 | AAB 16KB 怎么验 | `j_release_verify.py aab16k`（`BundleConfig.pb` 里 `{1:1,2:2}`）＋官方 `bundletool dump config \| grep alignment`；**本机没有可执行 bundletool**，真实 AAB 也不存在 → 该门 **NOT_RUN** | §5.3；`E-R7 §4/§5` |
| 9 | APK 16KB 现状 | `zipalign -v -c -P 16 4` exit 0；`apk16k` 14/14 PASS；负控 `--selftest` 变红（3 条 `p_align=0x1000`） | §6；`E-R7 §3` |
| 10 | 干净树构建 | 配方沿用 `2026-11-05-clean-tree-release.md §4.1`，但**必须改三处**：`local.properties` 不能整文件拷（现在含 Provider key）、verifier 未入 git、HEAD 落后工作树 26 个主源码文件 | §7；`E-R7 §1/§9` |
| 11 | 能不能今天就出生产包 | **不能**：0/4 签名变量、无 keystore、无 `.aab`、备案号/Play 账号外部缺失 | §8 |

---

## 1. 现状事实（读码 + 实测）

### 1.1 版本锚点

| 事实 | 值 | 出处 |
|---|---|---|
| `versionCode` | **2**（硬编码） | `app/build.gradle.kts:33` |
| `versionName` | `"0.2.0"`；flavor 追加 `-offline`/`-localfirst` | `app/build.gradle.kts:34`、`:55`、`:60` |
| 盘上产物元数据 | `versionCode: 2` / `versionName: "0.2.0-offline"` | `app/build/outputs/apk/strictOffline/release/output-metadata.json`【本轮实测】 |
| Git 提交数 | `git rev-list --count HEAD` = **1160**；first-parent = 1075 | 【本轮实测】 |
| 当前树（写作时点） | `git status --porcelain \| wc -l` = **582**；主源码 M 文件 **26** 个。落盘后复测 **586**（本文 2 条未跟踪 + 他线在飞 2 条）；该数在飞，引用时须现测 | 【本轮实测】 |
| 浅克隆？ | `git rev-parse --is-shallow-repository` → **false**（本机可推导） | 【本轮实测】 |

### 1.2 签名链（现状：全空）

| 事实 | 出处 |
|---|---|
| `signingConfigs.release` 是空壳（只有注释） | `app/build.gradle.kts:19-24` |
| 四变量齐备才注入，缺任一**静默跳过** | `app/build.gradle.kts:124-139` |
| `validateReleaseSigning` 缺变量即抛，报出变量名 | `app/build.gradle.kts:144-158` |
| 只挂到名字含 `Release` 且以 `assemble`/`bundle`/`package` 开头的任务 | `app/build.gradle.kts:161-167` |
| 当前 `RELEASE_*` 变量 **0/4** | `env \| grep -c '^RELEASE_'` → 0【本轮实测】 |
| `signing` 门 exit 1 并列出四个变量名 | 【本轮实测】`E-R7 §2.3` |
| 盘上 release APK 是临时钥匙：`CN=WS8 Ephemeral Local Only, OU=NotForRelease`，SHA-256 `9dc07e9f…6e20`，v1+v2 `true` / v3+ `false` | 【本轮实测】`E-R7 §3` |
| 仓内无任何被跟踪的 keystore | `j_release_verify.py signing` 的 `tracked_keystores: []`【本轮实测】 |
| CI 用一次性钥匙跑 dry-run（`-keysize 2048 -validity 365`，口令写在 workflow） | `.github/workflows/android-check.yml:109-120`【读码】 |

> **`RELEASE.md` 与代码已一致**：`RELEASE.md:20-24` 明写"构建**不**读 `keystore.properties`，只读四个环境变量"。
> `2026-11-05-clean-tree-release.md` 的 F10（"文档说 keystore.properties、代码读环境变量"）在当前 HEAD **已不成立**，不要再按 F10 排查。

### 1.3 AAB / bundletool

| 事实 | 出处 |
|---|---|
| 仓内**当前无任何 `.aab`** | `find . -maxdepth 4 -name '*.aab'` → 空【本轮实测】 |
| 缓存里的 bundletool 1.18.3 是**库 jar**，`MANIFEST` 里 `Main-Class` 计数 = 0 | 【本轮实测】`E-R7 §5` |
| `java -jar …bundletool-1.18.3.jar` → `…jar中没有主清单属性` | 【本轮实测】 |

⇒ `bundletool dump config`（官方 AAB 对齐检查）在本机**不可执行**，需官方 `bundletool-all-<ver>.jar`（外部下载）。

### 1.4 16KB

| 面 | 状态 | 出处 |
|---|---|---|
| APK ZIP 对齐 | `zipalign -v -c -P 16 4` → `Verification successful`，exit 0 | 【本轮实测】`E-R7 §3` |
| APK ELF 对齐 | `apk16k` 14 条 `.so`（arm64-v8a 7 + x86_64 7），全 STORED、`offset % 16384 = 0`、`elf_errors: []` | 【本轮实测】`r7-apk16k.json` |
| 判据负控 | `apk16k --selftest` → `ok: true`，3 条 `PT_LOAD p_align=0x1000 < 0x4000` | 【本轮实测】 |
| AAB 声明 | **NOT_RUN**（无 AAB）。`aab16k` 判据用合成夹具做正负控（1 正 4 负全符合预期） | `r7-aab16k-controls.json` |
| 运行时 | 当前唯一设备 `emulator-5590`：`getconf PAGE_SIZE` = **4096**、API 34、x86_64 | 【本轮实测】`E-R7 §10` |
| arm64 | 无真机/无 arm64 宿主 | 承 `2026-11-03-16kb-page-testing.md:164-173` |

ABI 口径未变：`abiFilters += setOf("arm64-v8a", "x86_64")`（`app/build.gradle.kts:45-47`），32 位 ML Kit `.so` 已被裁掉。

### 1.5 干净树

工作树 **582 条脏**（写作时点；落盘后 586），其中主源码 M 26 个；`HEAD` 与工作树的 `app/build.gradle.kts` **相同**（`git diff --stat HEAD -- …` 空）【本轮实测】。
⇒ **今天用 `git archive HEAD` 出的包不包含工作树里的 26 个主源码改动**——干净树构建的前提是"先把要发的东西提交"，不是"换个目录编译"。

---

## 2. 外部判据（官方原文）

| 判据 | 原文 | 来源 |
|---|---|---|
| versionCode 上限 | "The greatest value Google Play allows for `versionCode` is 2100000000." | 【官方】`developer.android.google.cn/studio/publish/versioning` |
| 必须递增 | "make sure that each successive release of your app uses a greater value."；"You can't upload an APK to the Play Store with a `versionCode` you have already used for a previous version." | 同上 |
| 降级保护 | 系统用该值"to protect against downgrades"，低版本号装不上 | 同上 |
| 16KB 政策 | "all apps targeting Android 15 (API level 35) and higher must support 16 KB memory page sizes on 64-bit devices on Google Play"；"Starting February 1, 2027, if your app updates don't support 16 KB memory page sizes, you won't be able to release these updates." | 【官方】`developer.android.google.cn/guide/practices/page-sizes` |
| 16KB ZIP 判据 | `zipalign -v -c -P 16 4 APK_NAME.apk`，build-tools **≥ 35.0.0** | 同上 |
| 16KB ELF 判据 | `llvm-objdump -p x.so \| grep LOAD`，`p_align ≥ 2**14`；RELRO `(VirtAddr+MemSiz)%0x4000==0` 或独占 LOAD 豁免 | 同上；仓内实现 `2026-11-04-release-readiness.md:224-231` |
| 16KB 构建侧 | "AGP version 8.5.1 or higher and NDK version r28 or higher" | 同上（本仓 AGP 9.3.0，`gradle/libs.versions.toml:2`） |
| AAB 对齐 | `bundletool dump config --bundle=<my .aab> \| grep alignment` → `PAGE_ALIGNMENT_16K` | 同上 |
| 运行时 | `adb shell getconf PAGE_SIZE` → `16384` | 同上 |
| Play 重签名 | "Google uses the upload certificate to verify your identity, and signs your APK(s) with your app signing key for distribution."；upload key = 上传前签 bundle/APK 的钥匙；app signing key = 装到用户设备上的 APK 的钥匙，且"never changes during the lifetime of your app" | 【官方】`developer.android.google.cn/studio/publish/app-signing` |
| 上传证书导出 | `keytool -export -rfc -keystore your-upload-keystore.jks -alias upload-alias -file output_upload_certificate.pem` | 同上 |

> **由此得到本项目最重要的一句定性**：本地 release key 签出的证书，在启用 Play App Signing 后**不是用户设备上看到的证书**，它只是"上传密钥"。
> 因此本文件的 `signing` 门（APK 与 AAB 同一身份 + 证书在有效期内）证明的是**上传链自洽**，不证明"发布证书正确"——后者只能在 Play Console 里比对（§8 BLOCKED）。

---

## 3. versionCode 构建注入

### 3.1 目标（三个具体失败，各由一条机制消灭）

| 具体失败 | 机制 |
|---|---|
| F-1 手改 `versionCode = 2` 忘记升，第二次发版被 Play 以"已用过该 versionCode"拒绝 | 版本号不再人手写：`N = 100000 + git 提交序号` |
| F-2 两个不同提交的包带同一个 versionCode（今天两个 flavor 都恒为 2） | `N` 随提交序号单调增长 |
| F-3 **成对产物做不了**：J-055–J-063/FIX-T15 要求"同钥匙出 vc N 与 N+1"跑升级/降级/回滚，而今天只能改源码 | `-Psmbk.versionCode=N` 显式覆盖，不改任何源文件 |

> F-3 不是假想：`docs/acceptance/runbook.md:290` 与 `:370` 明确把 7 项升级/降级/回滚判为 BLOCKED，原因写的是"无成对 vc 产物"。**旧状态**下要造 N+1 必须改 `app/build.gradle.kts` 再提交——这正是"测试污染发版源"。

### 3.2 方案（一句话）

**构建参数是唯一权威，git 只在发布脚本里当"算号器"。**

```
N = 100000 + (git rev-list --count HEAD)      # 仅当 git rev-parse --is-shallow-repository == false
release 构建：./gradlew … :app:bundleStrictOfflineRelease -Psmbk.versionCode=$N
debug/internal/benchmark：不加 -P，沿用字面量 2，行为零变化
```

取 `100000` 底数的两个可核理由（不是"留空间"这种空话）：

1. **可读**：任一包的 `versionCode - 100000` = 产出它的提交序号，`dumpsys` 现场就能反推代码位置，不需要查表。
2. **可分辨**：`< 100000` 只可能是人工覆盖（成对产物测试），`≥ 100000` 只可能是正式推导 → 上传了测试包时 Play 会以"版本号低于已发布版本"直接拒绝，**失败在门口而不是静默覆盖**。

### 3.3 改动草案（`app/build.gradle.kts`）

```kotlin
// 版本号：唯一权威来源 = 构建参数；缺省只服务于 debug，release 由 validateVersionCode 拦。
val smbkVersionCodeFromCli: Int? =
    providers.gradleProperty("smbk.versionCode").orNull?.toIntOrNull()
val SMBK_VERSION_CODE_DEBUG_DEFAULT = 2   // 与今日行为一致（:33）

defaultConfig {
    versionCode = smbkVersionCodeFromCli ?: SMBK_VERSION_CODE_DEBUG_DEFAULT
    versionName = "0.2.0"                  // 保持字面量：versionName 没有 F-1/F-2/F-3 那类失败
}

tasks.register("validateVersionCode") {
    doLast {
        val raw = providers.gradleProperty("smbk.versionCode").orNull
        if (raw.isNullOrBlank()) throw GradleException(
            "release 构建必须显式给 -Psmbk.versionCode=N（N=100000+git 提交序号；见 docs/research/2026-11-09-release-signing.md §3）")
        val n = raw.toIntOrNull() ?: throw GradleException("smbk.versionCode 不是整数: $raw")
        if (n !in 1..2_100_000_000) throw GradleException("smbk.versionCode 超出 Play 允许范围 1..2100000000: $n")
    }
}
// 与 validateReleaseSigning 完全同样的挂法（:161-167），只多挂一个任务
```

与既有结构的关系：`validateVersionCode` 是 `validateReleaseSigning`（`:144-158`）的**同形复制**——同样的失败形态（"变量缺失 → 打包末尾才炸且错误不指向根因"），同样的修法。不引入新机制、不改 `validateReleaseSigning`。

**为什么不设 fallback 派生**（即"没有 `-P` 就自动 git 算"）：

- 干净树（§7）里没有 `.git` → 派生必然失败或给出假值；
- CI 默认 shallow（`.github/workflows/android-check.yml:43` 用 `actions/checkout@v4` 无 `fetch-depth`）→ 派生值 = 1；
- `gradle.properties:2` 开了 `org.gradle.configuration-cache=true`，配置期读 git 的结果会进 CC 条目，**新提交若未触发配置失效就会复用旧版本号**（这类"静默重复号"是最难查的一类）。

三条任意一条成立，"配置期读 git"就不该做。把 git 留在脚本里，失败面从"静默错号"变成"脚本第 2 行非零退出"。

### 3.4 影响面闭合（改一处必须列全被他处）

| 受影响点 | 现状 | 必须一起改 / 为什么不改 |
|---|---|---|
| `tools/ci/j_release_verify.py:92` 正则 | 要求 `versionCode = <数字>`；两种注入写法**实测都判 `not found`（exit 1）** | **必须改**：见下 §3.4.1 |
| `j_release_verify.py:715-723` `version` 判据 | 用上表的字面量比对 `output-metadata.json` | **必须改**：新增 `--expected-code` |
| `j_release_verify.py:587-589` `identity` | 只**记录** `versions`，不判定 | 不改判定；但要接受 `versions.gradle.version_code` 变 `null`（真值仍在 `output_metadata`） |
| `tools/tests/test_j_release_verify.py:91,171,218,236,267` | 5 处按 `versionCode = 2` 字面量构造夹具 | **必须加**注入形态用例（先红后绿） |
| `tools/ci/kd76_identity.py:87` | 解析的是 `dumpsys` 的 `versionCode=`（设备侧），与 gradle 无关 | 不改 |
| `tools/ci/g_security_gate.py:692-695`（G-084） | 断言 `app/build.gradle.kts` 含 `"RELEASE_"` + `"System.getenv"`、无内联 `storePassword = "` | **不要动签名段**，否则 G-084 变红 |
| `tools/ci/check_telemetry_compliance.py:31`、`h_compliance_gate.py:129` | 只是把 build 文件当 token 源扫描 | 不改（版本号表达式不含禁用 token） |
| `.github/workflows/android-check.yml:109-120` | dry-run 不传 `-P` | 不改：dry-run 走 debug 默认值 2，本来就不是发版；但**不能**在 CI 里指望派生 |
| `RELEASE.md:17-18` 门 7（版本号升没升） | 文字判据 | 建议补一句"release 构建必须带 `-Psmbk.versionCode`"，其余不变 |
| `docs/acceptance/*` 里的 J-049（vc 碰撞检测器） | 未实现（`blocked-runbook.md:507`） | 不在本文实现；§3.5 的成对产物负控就是它的最小替代 |

#### 3.4.1 `version` 门的最小改法（fail-closed，不带双路径歧义）

```python
# 新增参数
version.add_argument("--expected-code", type=int)

# 判定
expected = args.expected_code
if gradle_info["version_code"] is None and expected is None:
    payload["errors"].append(
        "versionCode is injected at build time; pass --expected-code (see 2026-11-09-release-signing.md §3)")
if expected is not None:
    if not (1 <= expected <= 2_100_000_000):
        payload["errors"].append("expected versionCode out of Play range 1..2100000000")
    if element.get("versionCode") != expected:
        payload["errors"].append("output-metadata versionCode != --expected-code")
```

语义：gradle 里还有字面量时走老路（兼容历史日志复算）；变成表达式时**必须**给期望值，否则红——**没有"两个都试一下、哪个过算哪个"的分支**。

### 3.5 先红后绿（item 35 的执行顺序）

```bash
# ① 红：改动前，用注入形态探针跑现门（本轮已做，E-R7 §2.2 exit 1）
python -I tools/ci/j_release_verify.py version --gradle <注入形态>.gradle.kts \
  --metadata app/build/outputs/apk/strictOffline/release/output-metadata.json

# ② 单元测试（应红）
python -I -m pytest tools/tests/test_j_release_verify.py -q

# ③ 加 --expected-code 与两条用例后再跑（应绿）
python -I -m pytest tools/tests/test_j_release_verify.py -q

# ④ 负控：期望值差 1 必须红（改完后：metadata 为 101160，却给 101161）
python -I tools/ci/j_release_verify.py version --gradle app/build.gradle.kts \
  --metadata app/build/outputs/apk/strictOffline/release/output-metadata.json --expected-code 101161   # 应 exit 1

# ⑤ 缺参数负控：注入形态 + 不给 --expected-code 必须红
# ⑥ Gradle 侧（门禁串行阶段）：缺 -P 的 release 构建必须失败
./gradlew.bat --console=plain :app:validateVersionCode      # 无 -P 时 exit 1
./gradlew.bat --console=plain :app:validateVersionCode -Psmbk.versionCode=101160   # exit 0
./gradlew.bat --console=plain :app:assembleStrictOfflineRelease      # 无 -P 时应在 validateVersionCode 处失败
```

### 3.6 否决的替代方案（写下来免得下一轮重开）

| 方案 | 否决理由 |
|---|---|
| 配置期 `providers.exec { git … }` / `ValueSource` 读 git | 干净树无 `.git`；CI shallow 得 1；CC 命中可复用旧号（§3.2） |
| `version.properties` 作为源 | 多一个手工维护的文件 = 又一处 F-1（忘改）；git 序号已经单调且可复算 |
| `git describe --tags --long` 拼版本号 | 本仓无 release tag（`git tag` 只有两条 `archive/*`），且 `describe` 在无 tag 时退化为短 sha，不可比大小 |
| 继续用 `versionCode = 2` 手改 | F-1/F-2/F-3 全部保留 |

---

## 4. 本地 release keystore（明确非 Play 生产身份）

### 4.1 定位与命名

| 项 | 规定 | 理由 |
|---|---|---|
| 位置 | **仓外**，例：`D:/keys/smbk/`（不在 `D:\smart mistake book` 之下） | `.gitignore:18-20` 只挡三种扩展名，仓内任何位置都只是"默认被挡"，不是"不可能提交" |
| 文件名 | `smbk-local-release.keystore`（**别用 `.p12`**） | 扩展名这里只管"是否被 `.gitignore` 覆盖"，与格式无关（格式由 `-storetype PKCS12` 明示）。本轮实测 `git check-ignore -v probe.p12` → **exit 1 未忽略**；同名 `.jks`/`.keystore` → 命中 `.gitignore:18/19` |
| 别名 | `smbk-local-release` | 与 CI 的一次性别名 `ci-dry-run`（`android-check.yml:109-120`）区分开 |
| DN | `CN=SmartMistakeBook Local Release, OU=NOT-PLAY-PRODUCTION, O=SmartMistakeBook, C=CN` | `apksigner verify --print-certs` 会把它原样打出来；"这张证书不是 Play 身份"这一点写进证书本身，而不是只写在文档里 |
| 口令 | 仓外文件或密码管理器；**不写进任何示例/测试/日志** | `RELEASE.md:15-16` 门 6；`g_security_gate.py` G-084 已机器检查无内联口令 |

> 若坚持用 PKCS12（JDK 9+ 默认），**必须**同时把 `*.p12` 加进 `.gitignore`——否则就是把"忽略规则"降级成"人的记性"。本轮只登记这个缺口，不改 `.gitignore`（该文件当前 `M`，属他线）。

### 4.2 创建（含本轮实测的 PKCS12 口令陷阱）

```bash
export RELEASE_STORE_FILE="D:/keys/smbk/smbk-local-release.keystore"
export RELEASE_KEY_ALIAS="smbk-local-release"
export RELEASE_STORE_PASSWORD="<store 口令>"
export RELEASE_KEY_PASSWORD="$RELEASE_STORE_PASSWORD"     # ← 必须相同，理由见下

keytool -genkeypair -v \
  -keystore "$RELEASE_STORE_FILE" -storetype PKCS12 \
  -alias "$RELEASE_KEY_ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$RELEASE_STORE_PASSWORD" -keypass "$RELEASE_KEY_PASSWORD" \
  -dname "CN=SmartMistakeBook Local Release, OU=NOT-PLAY-PRODUCTION, O=SmartMistakeBook, C=CN"
```

**陷阱（本轮实测原文）**：`-keypass` 与 `-storepass` 不同时，keytool 仍然 **exit 0**，只打一行警告：

```
警告: PKCS12 密钥库不支持其他存储和密钥口令。正在忽略用户指定的-keypass值。
```

即真钥匙口令被**静默**改成 storepass。后果：`RELEASE_KEY_PASSWORD` 填了另一个值 → 签包在打包末尾失败，错误文本不含变量名。
⇒ `RELEASE_KEY_PASSWORD` 与 `RELEASE_STORE_PASSWORD` **必须取同一个值**。这条写进 item 35 的验收（可机器检查：`keytool -list` exit 0 且条目为 `PrivateKeyEntry`）。

`-validity 10000`（≈27 年）的理由：本地测试钥匙要覆盖本项目整个发版周期，不能中途过期把已发布的 mapping/AAB 对不上。**Play 对上传密钥有效期是否有硬性下限，本文未取得官方原文 → 标未验证**（`support.google.com` 本轮连接超时，检索通道全部熔断，`E-R7` 未收录）。

### 4.3 注入契约（沿用 env，不改结构）

四个变量名已冻结在 `app/build.gradle.kts:125-128`：`RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` / `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD`。

**不要**改成 `keystore.properties`：`tools/ci/g_security_gate.py:692-695`（G-084）要求源码里出现 `"RELEASE_"` 与 `"System.getenv"`；换实现会让 G-084 直接变红，而它守的是"密钥不进源码"这条底线。

### 4.4 验证命令（三层，全部本轮实测过同类命令）

```bash
# ① keystore 可打开 + 条目类型（消灭"变量齐了但文件/别名/口令错"）
keytool -list -v -keystore "$RELEASE_STORE_FILE" -alias "$RELEASE_KEY_ALIAS" \
  -storepass "$RELEASE_STORE_PASSWORD"
#   判据：exit 0；输出含 "条目类型: PrivateKeyEntry"；SHA256 指纹记入 sidecar

# ② APK 产出身份
"C:/Android/Sdk/build-tools/37.0.0/apksigner.bat" verify --print-certs --verbose <apk>
#   判据：DN 含 "NOT-PLAY-PRODUCTION"；v1=true 且 v2=true（minSdk 23 → v1 覆盖 23，v2 覆盖 24+）；
#         v3=true 与否须在发布说明写明（今天盘上包是 false）

# ③ AAB 产出身份（jarsigner/keytool，不能用 apksigner）
jarsigner -verify <aab>                                   # 判据 exit 0
keytool -printcert -jarfile <aab>                         # 判据 SHA256 == ① 的指纹

# ④ 组合门（APK 与 AAB 必须同一身份、证书未过期、四变量齐）
python -I tools/ci/j_release_verify.py signing --root . \
  --apk <apk> --aab <aab> --apksigner "C:/Android/Sdk/build-tools/37.0.0/apksigner.bat"
```

`signing` 的判据在 `j_release_verify.py:462-493`：四变量齐 + APK/AAB 指纹相等 + AAB 证书有效可解析且未过期。**它是 fail-closed 的**（本轮无参跑 → exit 1，`E-R7 §2.3`）。

### 4.5 与 Play App Signing 的关系（决定"指纹该跟谁比"）

| 场景 | 本地证书的角色 | 用户设备上看到的证书 |
|---|---|---|
| 未启用 Play App Signing（只发 APK/自建渠道） | 发布证书本身 | 本地 release 证书 |
| 启用 Play App Signing（发 AAB 上架） | **上传密钥**，只用于向 Google 证明身份 | **Google 托管的 app signing key**，不是我们的 |

⇒
1. 本文件的 `signing` 门只能证明"上传链自洽"，**不能**证明"商店里的发布证书正确"；
2. G-REL-09（产出证书身份 == 已登记发布指纹）在启用 Play App Signing 后的正确口径是"== **上传证书**指纹"，**必须随决策一起改口径**，否则门会长期假红；
3. 上传证书可用官方给的命令导出留档：`keytool -export -rfc -keystore <ks> -alias <alias> -file upload_certificate.pem`（【官方】app-signing 页）。

---

## 5. AAB 签名与校验

### 5.1 出包（每个要发的 flavor 各一次）

```bash
export RELEASE_STORE_FILE=… RELEASE_STORE_PASSWORD=… RELEASE_KEY_ALIAS=… RELEASE_KEY_PASSWORD=…
N=$(( 100000 + $(git rev-list --count HEAD) ))     # 前置：git rev-parse --is-shallow-repository == false

./gradlew.bat --console=plain --no-daemon \
  :app:bundleStrictOfflineRelease -Psmbk.versionCode=$N
# 另一个 flavor 同形：:app:bundleLocalFirstRelease（applicationId 后缀 .localfirst，不共用 Play 条目）
# 产物（约定路径，与 docs/acceptance/j-fail-tickets-b47.md:37 一致）：
#   app/build/outputs/bundle/strictOfflineRelease/app-strictOffline-release.aab
#   app/build/outputs/mapping/strictOfflineRelease/mapping.txt
#   app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk（若同时 assemble）
```

`bundle*Release` 会先命中 `validateReleaseSigning`（`app/build.gradle.kts:161-167`）与（item 35 落地后）`validateVersionCode`。

### 5.2 验签（`apksigner` 不适用——本轮实测异常）

```
$ apksigner verify probe.jar
Exception in thread "main" com.android.apksig.apk.ApkFormatException: Missing AndroidManifest.xml
```
AAB 的清单在 `base/manifest/AndroidManifest.xml`，ZIP 根没有 → apksigner 的结构前提不成立。
**AAB 一律用**：`jarsigner -verify <aab>`（exit 0）+ `keytool -printcert -jarfile <aab>`（指纹）。

### 5.3 AAB 16KB/ABI（`aab16k`）+ 官方交叉验证

```bash
python -I tools/ci/j_release_verify.py aab16k \
  --aab app/build/outputs/bundle/strictOfflineRelease/app-strictOffline-release.aab \
  --abis arm64-v8a,x86_64 --require-libs libquickjs.so
```
判据实现（`j_release_verify.py:296-315`）：`BundleConfig.pb` 里找到 `{1: enabled=1, 2: alignment}` 且 `alignment == 2`（`PAGE_ALIGNMENT_16K`）；`abi_set` 必须逐字等于 `abiFilters`；`libquickjs.so` 必须在。

**判据本身的可信度**：本轮用合成夹具做了 **1 正 4 负**（`r7-aab16k-controls.json`）：

| 夹具 | 触发条件 | 期望 | 实测 |
|---|---|---|---|
| `good.aab` | `{1:1,2:2}` | exit 0 | **0** |
| `wrong-alignment.aab` | `alignment=1` | exit 1 | 1（`PAGE_ALIGNMENT_16K not declared (found 1)`） |
| `no-config.aab` | 无 `BundleConfig.pb` | exit 1 | 1（`found None`） |
| `32bit.aab` | 混入 `armeabi-v7a` | exit 1 | 1（ABI 集合不等） |
| `no-quickjs.aab` | 缺 `libquickjs.so` | exit 1 | 1（required native library missing） |

⇒ 判据在四个方向上都可变红。**但**：夹具只证明"判据会红"，**不证明真实 AAB 绿**。真实 AAB 的 `PAGE_ALIGNMENT_16K` 仍是 **NOT_RUN**（本机无 AAB、无 bundletool）。

官方交叉验证（拿到 `bundletool-all` 后必跑，两条独立实现互证）：
```bash
java -jar bundletool-all-<ver>.jar dump config --bundle=<aab> | grep alignment   # 期望 PAGE_ALIGNMENT_16K
```

> **缺口登记**：`apk16k` 有内建 `--selftest`（本轮实测变红），`aab16k` **没有**。建议 item 36 把上面的夹具矩阵落成 `aab16k --selftest`，否则这条门只有正向证明、没有反向证明。

### 5.4 发布产物四联门（item 36 的验收命令集）

```bash
M=app/build/outputs/apk/strictOffline/release/output-metadata.json
A=app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
B=app/build/outputs/bundle/strictOfflineRelease/app-strictOffline-release.aab
P=app/build/outputs/mapping/strictOfflineRelease/mapping.txt

python -I tools/ci/j_release_verify.py version  --gradle app/build.gradle.kts --metadata $M --expected-code $N
python -I tools/ci/j_release_verify.py apk16k   --apk $A
python -I tools/ci/j_release_verify.py aab16k   --aab $B
python -I tools/ci/j_release_verify.py signing  --root . --apk $A --aab $B \
  --apksigner "C:/Android/Sdk/build-tools/37.0.0/apksigner.bat"
python -I tools/ci/j_release_verify.py r8       --mapping $P --apk $A
python -I tools/ci/j_release_verify.py identity --root . --apk $A --aab $B --mapping $P \
  --gradle app/build.gradle.kts --metadata $M --require-clean --require-head
```
（`identity` 的 `--require-clean` 只在**主会话提交之后**才可能 exit 0；今天是 582 脏（写作时点；现测 586），必然红——这是真实状态，不是脚本坏。）

---

## 6. 16KB / ABI 检查命令一览（可直接抄）

| # | 面 | 命令 | 通过判据 |
|---|---|---|---|
| 1 | APK ZIP 对齐 | `build-tools/37.0.0/zipalign.exe -v -c -P 16 4 <apk>` | 末行 `Verification successful`，exit 0 |
| 2 | APK `.so` 全部 STORED + 偏移 | `python -I tools/ci/j_release_verify.py apk16k --apk <apk>` | `errors: []`，`offset_mod_16384` 全 0 |
| 3 | 判据负控 | `… apk16k --apk <apk> --selftest` | `ok: true` 且 ≥1 条 `p_align=0x1000` |
| 4 | ABI 只有 64 位 | 同上 payload 的 `files[].name` | 只出现 `lib/arm64-v8a/` 与 `lib/x86_64/` |
| 5 | ELF 程序头（官方工具版） | `llvm-objdump -p x.so \| grep LOAD` | 每条 `p_align ≥ 2**14`（**本机无 NDK，未跑**；判据等价实现见 #2 的 `elf_errors`） |
| 6 | AAB 声明 | `python -I tools/ci/j_release_verify.py aab16k --aab <aab>` | `alignment: 2`、`errors: []` |
| 7 | AAB 官方交叉验证 | `bundletool-all dump config --bundle=<aab> \| grep alignment` | 出现 `PAGE_ALIGNMENT_16K`（**工具未取得，未跑**） |
| 8 | 运行时页大小 | `adb shell getconf PAGE_SIZE` | `16384`（当前 `emulator-5590` = **4096**，不满足） |
| 9 | 运行时装载 | `adb logcat -d \| grep -Ei 'linker\|RELRO\|SIGSEGV\|UnsatisfiedLinkError'` | 0 命中，且 `libquickjs.so`/`libLiteRt.so`/`libmlkit_google_ocr_pipeline.so` 各被真正触发一次 |

> 与计划 `:15` 措辞的对齐：计划写"AAB 16KB 用 unzip+readelf/objdump 校验"。**unzip 部分**由 `aab16k`/`zip_names` 覆盖（读 `BundleConfig.pb` + 遍历 `lib/<abi>/*.so`）；**readelf/objdump 部分**本机无 NDK 跑不了（U-5）。判据上的分工是：
> AAB 层验"**声明 + ABI 集合 + 必需库**"（Play 重新切分 APK 时按声明对齐），成品 APK 层验"**ZIP 偏移 + ELF 程序头**"（真正装到设备上的字节）。两层都跑才算闭环；只跑 AAB 声明不足以证明 `.so` 合规，只跑 APK 不足以证明 Play 切分后仍对齐。

---

## 7. 干净树构建

### 7.1 配方（沿用 `2026-11-05-clean-tree-release.md §4.1`，不重写）

`git archive` 导出 + 私有 `GRADLE_USER_HOME`（播种 `caches/modules-2` 0.67 GB + `wrapper/dists` 151 MB）+ `--offline` + `gradlew.bat`。该文件 §5 的 F1–F14 仍然全部适用（无发行版会先去下载、缺描述符会 `No cached version`、默认导出把 `gradlew` 写成 CRLF 等）。

### 7.2 **必须改的三处**（否则配方今天会出错）

| # | 问题 | 证据 | 改法 |
|---|---|---|---|
| G-1 | `local.properties` 现在**含 Provider key**，原配方第 2 步整文件 `cp` | `E-R7 §9`：键名有 `SMB_REAL_PROVIDER_KEY`，值长 35 字符 | 只拷 `sdk.dir`：`grep '^sdk.dir=' local.properties > "$OUT/src/local.properties"`；**不要**整文件拷 |
| G-2 | 发布校验器 `tools/ci/j_release_verify.py` **未入 git**（`??`），不进归档 | `git status --porcelain -- tools/ci/j_release_verify.py` → `??` | 归档树里没有它：verifier 从**主树**对副本产物跑；发版前必须把工具提交（否则"产物可由 HEAD 字节复现"这条永远差一个工具） |
| G-3 | HEAD 落后工作树 26 个主源码文件 | `E-R7 §1` | 干净树构建**只在主会话把要发的代码提交之后**才有意义；今天是 HEAD 构建 = 发旧代码 |

另外两条不变式（沿用批次 43 结论，不重复论证）：
- 归档无 `.git` → APK 内 `version-control-info.textproto` 必然是 `NO_SUPPORTED_VCS_FOUND`，产物身份改用 sidecar（HEAD sha + tar sha256 + APK/AAB sha256）（`2026-11-05-release-gates.md:29` 的 G-REL-00(b) 分支，**该分支仍需主会话裁定**）；
- 归档无 `.git` → **`-Psmbk.versionCode` 必须显式传**（§3.2 的直接后果）。

### 7.3 命令序列（release 形态）

```bash
REV=$(git rev-parse HEAD)
[ "$(git rev-parse --is-shallow-repository)" = "false" ] || { echo "shallow clone: 不能推导 versionCode"; exit 1; }
N=$(( 100000 + $(git rev-list --count HEAD) ))
OUT="D:/smb-rel-${REV:0:8}"
mkdir -p "$OUT/src" "$OUT/gradle-home/caches"

git -c core.eol=lf -c core.autocrlf=false archive --format=tar HEAD -o "$OUT/tree.tar"
tar -xf "$OUT/tree.tar" -C "$OUT/src"
grep '^sdk.dir=' local.properties > "$OUT/src/local.properties"     # G-1
cp -r .gradle/caches/modules-2 "$OUT/gradle-home/caches/"
cp -r .gradle/wrapper "$OUT/gradle-home/"

cd "$OUT/src"
GRADLE_USER_HOME="$OUT/gradle-home" ANDROID_HOME="C:/Android/Sdk" \
RELEASE_STORE_FILE=… RELEASE_STORE_PASSWORD=… RELEASE_KEY_ALIAS=… RELEASE_KEY_PASSWORD=… \
  ./gradlew.bat --offline --no-daemon --no-build-cache --console=plain \
  :app:bundleStrictOfflineRelease :app:assembleStrictOfflineRelease -Psmbk.versionCode=$N
```

判据（缺一不可）：`exit 0`；日志中 `Download` / `No cached version` / `Could not resolve` 各 0 次；`tar` 与 `apk`/`aab` 的 sha256 与 HEAD sha 一起写 sidecar。
操作注意（沿用 `2026-11-05-clean-tree-release.md §4.1` 的三条）：**复制 `.gradle` 缓存必须在没有 Gradle 运行时做**（拷贝的是活目录）；`gradlew.bat` 直调会绕过 `tools/android-env.ps1` 的 `-Duser.home`，debug keystore 落到 `C:\Users\<user>\.android\debug.keystore`（与该入口行为不同，需在门禁记录注明）；副本身份必须用 sidecar 三联绑定，不能拿副本包当"当前工作树"的证据。

### 7.4 sidecar（一次构建一份，schema 见 `2026-11-04-release-readiness.md:148-167`）

必填且与本次注入绑定：`git.revision`（full sha）、`git.dirty`（必须 `false`）、`versionCode`（**= `N`**，不是 gradle 缺省 2）、`versionName`、`applicationId`、`apk.sha256` / `aab.sha256` / `mapping.sha256`、`mapping.compilerVersion`、`signing.certSha256` + `certDn` + `schemes`、`sixteenKb.zipalignExit/abis/elfPass/elfFail`、`gates`。
另按 `§2.5` 的归档命名 `mapping-<applicationId>-<versionCode>-<versionName>-<variant>.txt` —— **`versionCode` 必须写进 mapping 文件名**，这样"用旧 mapping 解释新栈"这类事故在文件名层面就能看出来。

这是 G-REL-27 的判据，也是"vc 单调"的唯一可留档凭据：下次发版比对 `N > 上次 sidecar 的 versionCode`（Play Console 的已发布值属外部输入，B-2/B-3）。

---

## 8. 外部 BLOCKED（本机/本会话不可能闭合，**不得伪造**）

| ID | 项 | 为什么本机不可能 | 出口条件（谁） |
|---|---|---|---|
| B-1 | **App 备案号**（工信部 ICP 备案） | 需主体资质与真实域名/主体审核，纯外部行政流程 | 发布者提供备案号 → 填入隐私/关于页；在此之前上架清单该项留空并标 `BLOCKED` |
| B-2 | **Play 开发者账号 / Play Console** | 无账号；`docs/acceptance/blocked-runbook.md:506` 已列为 J-5 外部组 | 账号持有人建内部/封闭轨道并给出回执 |
| B-3 | **Play App Signing 决策**（上传密钥 vs 托管） | 决策影响 G-REL-09 的"发布指纹"口径（§4.5） | 发布者书面定 → 更新门口径 + 登记上传证书指纹 |
| B-4 | **生产签名钥持有人** | 本文件只定义"本地测试钥匙"，生产钥必须由持有人注入（`RELEASE.md:20-28`） | 持有人按 §4 出证书 → `signing` 门转绿 |
| B-5 | **arm64 + 16KB 真机/宿主** | 无 arm64 设备；x86_64 宿主跑 arm64 镜像受 QEMU 限制（`2026-11-03-16kb-page-testing.md:164-173`） | 换 arm64 真机（Pixel 8+ 开 `Boot with 16KB page size`）后跑 §6 的 #8/#9 |
| B-6 | **`bundletool-all-<ver>.jar`** | 需从官方 GitHub release 下载；缓存里只有库 jar（`E-R7 §5`） | 下载后跑 §5.3 的官方交叉验证 |
| B-7 | 隐私政策/数据安全表单的托管 URL | 需公网可访问地址 | 发布者提供 URL（AIGC/隐私清单属另一条线，本文不展开） |

---

## 9. 未验证（NOT_RUN / UNVERIFIED，不得当通过）

| ID | 项 | 状态 |
|---|---|---|
| U-1 | 任何 Gradle 命令（`validateVersionCode`、`bundleStrictOfflineRelease`、`assembleStrictOfflineRelease`、`validateReleaseSigning`） | **NOT_RUN**（纪律：Gradle 只在串行门禁阶段跑） |
| U-2 | 真实 AAB 的 `PAGE_ALIGNMENT_16K` | **NOT_RUN**（无 AAB；`aab16k` 只有夹具正负控） |
| U-3 | `bundletool dump config` 官方交叉验证 | **NOT_RUN**（无可执行 fat jar） |
| U-4 | 16KB 运行时（`getconf PAGE_SIZE=16384`） | **NOT_RUN**（当前设备 4096） |
| U-5 | `llvm-objdump`/`llvm-readelf` 官方命令 | **NOT_RUN**（本机无 NDK；用 `apk16k` 的等价判据代替） |
| U-6 | 干净树 `--offline` 构建 exit 0 | **NOT_RUN**（承 `2026-11-05-clean-tree-release.md` U1/U2） |
| U-7 | 生产/上传证书指纹 | **UNVERIFIED**（无密钥；盘上包为 `WS8 Ephemeral Local Only`） |
| U-8 | Play 对上传密钥有效期的硬性下限 | **UNVERIFIED**（官方支持页本轮连接超时，检索引擎全部熔断；不作判据） |
| U-9 | `identity --require-clean --require-head` | **必然红**：树 582 脏、`tools/ci/j_release_verify.py` 未跟踪 |
| U-10 | `aab16k --selftest` | 不存在（见 §5.3 缺口登记） |

---

## 10. 来源与采纳 / 拒绝

### 采纳

| # | 来源 | 用途 | 判定 |
|---|---|---|---|
| S1 | `developer.android.google.cn/guide/practices/page-sizes`（官方镜像，本轮抓取成功） | 16KB 政策/截止日、`zipalign -P 16`、`2**14`、AGP 8.5.1/NDK r28、`bundletool dump config`、`getconf PAGE_SIZE` | **采纳**（官方一手；与仓内 `2026-11-03-16kb-page-testing.md:28-88` 逐条一致） |
| S2 | `developer.android.google.cn/studio/publish/versioning` | versionCode 上限 2100000000、必须递增、不可复用、降级保护 | **采纳**（官方一手；直接支撑 §3 的 `1..2_100_000_000` 判据） |
| S3 | `developer.android.google.cn/studio/publish/app-signing` | upload key vs app signing key、Play 重签名、上传证书导出命令 | **采纳**（官方一手；决定 §4.5 与 G-REL-09 口径） |
| S4 | 仓内 `tools/ci/j_release_verify.py`（本轮实跑 4 个子命令 `version`/`apk16k`/`signing`/`aab16k` + 1 个 `apk16k --selftest`；`identity`/`r8`/`r8keep` 未跑） | 判据现状与被注入形态打破的位置 | **采纳**（一手：读码 + 实跑） |
| S5 | 仓内 `docs/research/2026-11-05-clean-tree-release.md` | 干净树配方 F1–F14 | **采纳**，但按 §7.2 修正三处 |
| S6 | 仓内 `docs/research/2026-11-04-release-readiness.md` | sidecar 七项、签名现状、16KB 判据实现 | **采纳** |
| S7 | 仓内 `docs/research/2026-11-05-release-gates.md` | 门 ID 冻结台账（G-REL-00…29） | **采纳**（本文件不新增门号，只给实施命令） |

### 单一来源 / 待验证

| 来源 | 内容 | 处置 |
|---|---|---|
| 支持页 `support.google.com/…/9842756` | Play 上传密钥有效期下限（如"2033-10-22"一类说法） | 本轮 **连接超时 + 检索熔断**，未取到原文 → **未采纳为判据**（U-8）；`-validity 10000` 只按"覆盖本项目发版周期"论证 |

### 拒绝

| 说法 | 拒绝理由 |
|---|---|
| "在 gradle 配置期读 git 提交数" | 干净树无 `.git`、CI shallow 得 1、CC 复用旧值（§3.2）——三条独立失败 |
| "用 `keystore.properties` 更标准" | 会让 G-084（`g_security_gate.py:692-695`）变红；且 `RELEASE.md:20-24` 已明写不读该文件 |
| "`apksigner verify` 也能验 AAB" | 本轮实测 `ApkFormatException: Missing AndroidManifest.xml` |
| "本机缓存的 `bundletool-1.18.3.jar` 可以 `java -jar`" | `Main-Class` 计数 0、`java -jar` 报"没有主清单属性"（`E-R7 §5`） |
| "把 `local.properties` 整文件拷进构建副本" | 该文件当前含 35 字符 Provider key（`E-R7 §9`），是泄密路径 |

---

## 11. 交接（给 item 35 / 36 / 37 的最小动作）

**item 35（versionCode + keystore + AAB 签名）**
1. `app/build.gradle.kts`：按 §3.3 改 `versionCode` 表达式 + 新增 `validateVersionCode`（与 `validateReleaseSigning` 同形并挂同一组任务）。
2. `tools/ci/j_release_verify.py` + `tools/tests/test_j_release_verify.py`：按 §3.4.1 加 `--expected-code` 与用例，**先红后绿**。
3. 按 §4.2 在**仓外**建 keystore（`.keystore` 扩展名，格式 PKCS12），按 §4.3 注入四变量。
4. 出 AAB + APK，跑 §5.4 四联门（`version/apk16k/aab16k/signing`）。

**item 36（AAB 16KB/ABI/证书/versionCode 校验）**
1. 真实 AAB 上跑 `aab16k`（§5.3）并留 JSON 证据。
2. 取得 `bundletool-all` 后跑官方 `dump config` 交叉验证（B-6）。
3. 视情把 §5.3 的夹具矩阵落成 `aab16k --selftest`（U-10 缺口）。

**item 37（干净树构建 + mapping）**
1. 主会话**先提交**（含 `tools/ci/j_release_verify.py`，否则 G-2 永远差一件）。
2. 按 §7.3 跑；按 §7.4 写 sidecar；`identity --require-clean --require-head` 转绿才算绑定。
3. G-REL-00 的 (a)/(b) 分支仍需主会话裁定（§7.2 末）。

**本文不做的事**：不改 `docs/current/*`（当前 7 个文件均 `M`，属他线）、不改 `.gitignore`（当前 `M`）、不跑 Gradle、不建仓内 keystore、不下载 bundletool。
