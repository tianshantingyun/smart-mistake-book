<!-- 体量见 §11（wc -lc 实测）。批次 44 十域 J「发布工程」的本机可核部分研究件；无 git 写、无密钥。 -->

# 发布验收：本机可核项与外部阻塞项（16KB / AAB / R8 mapping / 版本签名 / 升级降级）

| 字段 | 值 |
|---|---|
| evidence-id | `R5-RELACCEPT-01` |
| 执行者 / 时间 | 发布工程研究员（子代理），共享工作树，只读 + 一次预期失败的安装；2026-11-04 本机，文件名按批次 44 的 `2026-11-06` |
| head | `26f2327b0f88d3cad7e076b967b62874e36ca07d`（本轮 `git rev-parse`）；`-uall` 3755 → 3755；`porcelain` 543；暂存 0 |
| 产物 A（APK） | `apk/strictOffline/release/app-strictOffline-release.apk`，`sha256 724ce718…342b44`，155,816,991 B，mtime 2026-11-03 11:10 |
| 产物 B（AAB） | `bundle/strictOfflineRelease/app-strictOffline-release.aab`，`sha256 d0734105…d4bcdc`，113,438,749 B，mtime **2026-09-30 02:34** |
| 产物 C（mapping） | `mapping/strictOfflineRelease/mapping.txt`，126,196,894 B / 893,641 行，mtime 2026-11-03 11:09 |
| 实跑工具 | git 2.55、Python 3.13、`zipalign`/`apksigner`/`aapt2` 37.0.0、`bundletool` 1.18.3（重建 classpath）、`retrace` 8.2.33、`adb` 37.0.1、`keytool` |
| 未跑 | 任何 Gradle、构建、`am instrument`、git 写、成功启动的 emulator、Provider/Play Console |
| verdict | 5 项本机可核中 **3 PASS（各 ≥2 类独立证据）/ 1 FAIL（AAB 陈旧）/ 1 BLOCKED（生产签名）**；16KB 运行时 NOT_RUN；arm64 BLOCKED；升级降级 BLOCKED（无成对 vc），但取得运行时反证 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` |

> **口径**：**实跑**=本轮命令与原样输出；**实读**=本轮读到的源码/产物；**转录**=引既有文件原文（标行）。状态只用 PASS / FAIL / PARTIAL / BLOCKED / NOT_RUN / UNVERIFIED。

---

## 0. 一页结论

| # | 检查 | 判据 | 证据类 | verdict |
|---|---|---|---|---|
| 1 | 16KB ZIP 对齐 | `zipalign -c -P 16` 末行 `Verification successful` 且 exit 0 | A 官方工具 + B 自解析偏移 | **PASS** |
| 2 | 16KB ELF 对齐 | 每个 `PT_LOAD p_align ≥ 0x4000`；RELRO 余页无可写段 | B 自解析（负控能红）+ A 交叉 | **PASS** |
| 3 | AAB 声明 16KB | `dump config` 含 `PAGE_ALIGNMENT_16K` | A 官方 CLI + B protobuf 解码 | **PASS** |
| 4 | AAB 代表当前配置 | 仅 `arm64-v8a`+`x86_64`、含 quickjs | B 结构解析 + A `validate` | **FAIL**（含 9 个 32 位 `.so`、无 quickjs） |
| 5 | R8 mapping 可用 | 结构/混淆/映射↔APK/可 retrace | A `retrace` + B dex 字节绑定 | **PASS** |
| 6 | 版本号占位 | 单一来源、可单调递增 | B 源码实读 + 产物 badging | **PARTIAL**（硬编码两行） |
| 7 | 签名占位 | 缺变量即失败、不落密钥 | A 契约实读 + B 环境/文件清查 | **PASS（结构）** |
| 8 | 生产签名身份 | 指纹 == 发布指纹、DN 非一次性 | A `apksigner`/`keytool` | **FAIL / BLOCKED**（三产物三证书） |
| 9 | 产物↔源绑定 | APK 内 revision == HEAD | B textproto + git 解析 | **FAIL**（`faab17f5`，落后 30 提交） |
| 10 | 升级（同包更高 vc） | 安装 Success 且数据保留 | A 设备 | **BLOCKED**（无成对 vc） |
| 11 | 跨签名升级 | 必须失败 | A 证书比对 + C 设备 | **PASS（反证成立）** |
| 12 | 16KB 运行时 / arm64 | `getconf PAGE_SIZE`=16384 | C 设备 | **NOT_RUN / BLOCKED** |

**三条最硬结论**

1. **16KB 静态面在本 APK 成立且门能红**：14 个 `.so` 全 `STORED`、数据偏移全 `%16384==0`、ELF `p_align` 全 ≥ `0x4000`；把 `p_align` 改 `0x1000` 后同一检查器立刻 FAIL。
2. **树上的 AAB 不能代表当前配置**：2026-09-30 产物，含 `armeabi-v7a`(6)+`x86`(3)、且**无 `libquickjs.so`**——与 `abiFilters`（`app/build.gradle.kts:45-47`）和当前 APK（7+7 全 64 位）矛盾。拿它验收即假绿。
3. **`adb install` 失败时 exit code 仍为 0**（§6.2：`Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]` + `ADB_INSTALL_EXIT=0`）。任何「exit 0 即通过」的规则在此必错。

---

## 1. 判据与证据规则（建议并入 D0 lint）

**四类证据**：**A** 官方工具判定行（`zipalign` 的 `Verification successful`、`dump config` 的 `PAGE_ALIGNMENT_16K`、`apksigner` 的 `Verified using v2`、`retrace` 的还原结果）；**B** 自写独立实现（ELF 解析、protobuf 解码、dex 字节搜）；**C** 设备运行时事实（`dumpsys`、安装结果）；**D** 官方文档（只定义「什么算过」，不构成某产物 PASS）。

**规则**：PASS 需 **A/B 中两个不同实现**，或 **一个实现 + C**；只一类 → `PARTIAL`；只有 D → 不是检查。
**规则（能红）**：自写检查器必须带一次负控实跑（§2.3 实例）。
**规则（exit 不是判词）**：`adb install` / `retrace` / `bundletool validate` 均已实测出「exit 0 但结论不成立」，判词必须取输出里的判定行。

---

## 2. 16KB 页大小

### 2.1 判据来源（官方，本轮抓取）

`https://developer.android.google.cn/guide/practices/page-sizes`（主站直连超时，镜像同文）：

- 范围/截止：「apps targeting Android 15 (API level 35) and higher must support 16 KB … on 64-bit devices」；「**Starting February 1, 2027**, if your app updates don't support 16 KB memory page sizes, you won't be able to release these updates.」
- ZIP：「`zipalign -v -c -P 16 4 APK_NAME.apk` … last line … "Verification successful"」。
- ELF：「ensure that the load segments don't have values less than `2**14`」（`2**13`/`2**12` 不合规）。
- RELRO：「**Formula**: (VirtAddr + MemSiz) % 0x4000 == 0」；豁免 = `GNU_RELRO` 独占 `LOAD` 段且后续可写段从下一页开始。
- AAB：「`bundletool dump config --bundle=<my .aab> | grep alignment` … If you see `PAGE_ALIGNMENT_16K` then you know your bundle requests 16 KB zip alignment.」
- 工具线：AGP ≥ 8.5.1、NDK ≥ r28。

### 2.2 项目命中面（实读）

- `targetSdk = 36`（`app/build.gradle.kts:29`）> 35 ⇒ **命中 Play 16KB 要求**；`minSdk = 23`（`:28`）。
- `abiFilters += setOf("arm64-v8a", "x86_64")`（`:45-47`）⇒ 只出 64 位。
- buildTools `37.0.0`（`:17`，目录实存）；AGP 9.3.0 > 8.5.1 ⇒ ZIP 层默认合规（与实测一致）。
- **无自建 native**：`grep -rn "externalNativeBuild\|CMakeLists" app/ core/` = **0 命中** ⇒ 随包 `.so` 全是预编译依赖。

### 2.3 ZIP + ELF：PASS（含负控）

```bash
C:/Android/Sdk/build-tools/37.0.0/zipalign.exe -c -P 16 -v 4 <apk>
# 末行：Verification successful      ZIPALIGN_EXIT=0
```

独立实现（§10.1，只读 ZIP 中央目录 + 本地头 + ELF 程序头）：

```text
PASS  STORED  off%16384=0   5392000  lib/arm64-v8a/libLiteRt.so
PASS  STORED  off%16384=0    924936  lib/arm64-v8a/libquickjs.so
PASS  STORED  off%16384=0   911248  lib/x86_64/libquickjs.so
TOTAL=14 FAIL=0        ELF_EXIT=0          # arm64-v8a 7 + x86_64 7，无 32 位
```

负控（改 `libLiteRt.so` 的 3 个 `PT_LOAD.p_align` 为 `0x1000`）：

```text
selftest: patched 3 PT_LOAD p_align->0x1000 in lib/arm64-v8a/libLiteRt.so -> FAIL (3 errors)   # 0 = 按预期变红
```

> PASS 只覆盖**静态**（ZIP 偏移 + ELF 对齐）。静态对齐 ≠ 16KB 内核可运行（§2.6）。

### 2.4 AAB 声明 16KB：PASS（两条独立路径）

**A 官方 CLI**（`bundletool` 库 jar 无 `Main-Class`，但重建 classpath 后可执行，配方见 §3.1）：

```text
$ java -cp "<五 jar>" com.android.tools.build.bundletool.BundleToolMain dump config --bundle=<aab>
{ "bundletool": { "version": "1.18.3" },
  "optimizations": { "splitsConfig": { },
    "uncompressNativeLibraries": { "enabled": true, "alignment": "PAGE_ALIGNMENT_16K" },
    "storeArchive": { "enabled": true } }, ... }
```

**B 原始 protobuf 解码**：`BundleConfig.pb`(628 B) 得 `2.2.1 varint=1`、`2.2.2 varint=2`。字段语义取自 `google/bundletool` `src/main/proto/config.proto:192-210`：

```proto
message UncompressNativeLibraries {
  bool enabled = 1;
  enum PageAlignment { PAGE_ALIGNMENT_UNSPECIFIED=0; PAGE_ALIGNMENT_4K=1;
                       PAGE_ALIGNMENT_16K=2; PAGE_ALIGNMENT_64K=3; }
  PageAlignment alignment = 2;
}
```

两路同值 ⇒ **PASS**，并**追认批次 43 G-REL-17 的判据**（`release-gates.md:46`）是官方原文而非臆造。

### 2.5 AAB 内容：FAIL（陈旧 + 32 位）

`bundletool validate` **exit 0**，清单却是 `arm64-v8a`(6) + `armeabi-v7a`(6) + `x86`(3) + `x86_64`(6) = 21 条 `.so`：

- 含 9 条 32 位 ⇒ 与「仅 64 位」矛盾；照此上架 `G-REL-14` 直接红。
- **完全没有 `libquickjs.so`** ⇒ 早于 quickjs 1.0.15 接入。
- 签名者 `SHA256 A9:E7:6C:AD:…`（`keytool -printcert -jarfile`），与 APK(`9dc07e9f…`)、debug(`26466dbf…`) 都不同 ⇒ 第三把一次性钥匙。

⇒ `validate` exit 0 只证明「bundle 结构合法」，**不证明内容等于当前配置**。这是「exit 0 ≠ 通过」的第二实例（第一是 `adb install`，§6.2）。

### 2.6 运行时 16384：NOT_RUN

在线四台（5562/5566/5570/5584）皆 4096 或 API 23（无 `getconf`）；本轮启动 `ps16k_x86_64` 失败：

```text
$ emulator.exe -avd ps16k_x86_64 -no-window -no-audio -no-snapshot -read-only -no-boot-anim
ERROR | avdInfo_setLastRunQemuVersion: Could not write file: …\ps16k_x86_64.avd\qemu-version.txt
# ~60 s 后 adb devices 无新条目，进程退出
```

**NOT_RUN（非 FAIL 非 PASS）**。批次 43 的 `G-REL-15 PASS` 绑 `emulator-5620` + APK `a5fa0c4f…`，与本轮 APK 不同，不可外推。复跑：去掉 `-read-only` → `adb wait-for-device` → `adb shell getconf PAGE_SIZE`（判据 16384）→ `install` → 冷启 → `logcat | grep -E "FATAL|AndroidRuntime"` 判 0。

---

## 3. AAB 检查（批次 43 的 BLOCKED 已解除）

### 3.1 「本机没有 bundletool」是能力误判

`~/.gradle` 里只有库 jar（`java -jar` 报「没有主清单属性」），但重建 classpath 后 CLI 完全可用。缺的依赖逐个报出，最终五个（坐标为本轮实读）：

| 依赖 | 缓存坐标 |
|---|---|
| bundletool | `com.android.tools.build/bundletool/1.18.3/*/bundletool-1.18.3.jar` |
| aapt2-proto | `com.android.tools.build/aapt2-proto/9.3.0-15703166/*/aapt2-proto-*.jar` |
| protobuf-java / -util | `com.google.protobuf/protobuf-java/3.25.5`、`com.google.protobuf/protobuf-java-util/*` |
| guava / gson / jose4j | `com.google.guava/guava/33.4.0-jre`、`com.google.code.gson/gson/2.11.0`、`org.bitbucket.b_c/jose4j/0.9.5` |

> `dump manifest` 与 `validate` 额外需要 **aapt2-proto**（`com.android.aapt.Resources$XmlNode`）；只跑 `dump config` 不需要。
> Git Bash 陷阱：`--xpath=/manifest/@package` 会被 MSYS 改写成 `C:/Program Files/Git/manifest/...` → `Error in the XPath expression`；用 `MSYS2_ARG_CONV_EXCL="*"` 规避。

### 3.2 三条命令与本轮结果

| 命令 | 结果 |
|---|---|
| `dump config --bundle=<aab>` | `1.18.3`；`uncompressNativeLibraries{enabled:true, alignment:PAGE_ALIGNMENT_16K}`；`storeArchive{enabled:true}`；`splitsConfig{}`（未按 ABI 切分） |
| `dump manifest --xpath=/manifest/@android:versionCode` / `@android:versionName` | `2` / `0.2.0-offline` |
| `validate --bundle=<aab>` | **exit 0**；21 条 `.so`（含 9 条 32 位）；`androidGradlePluginVersion=9.3.0` |

`BUNDLE-METADATA/` 6 条（可验收元数据）：`obfuscation/proguard.map`（**102,079,875 B，Play 自动反混淆的来源**）、`r8.json`（`hasObfuscationDictionary:false` 等）、`profiles/baseline.prof`+`.profm`、`libraries/dependencies.pb`、`gradle/app-metadata.properties`。**无** `debugsymbols/`——与「无自建 native」一致，不是缺项；但第三方 `.so` 符号化仍依赖供应商。

### 3.3 结论

16KB 声明 PASS（§2.4）；**内容代表性 FAIL**（§2.5）。当前树上没有任何 AAB 能代表 `abiFilters` 生效后的配置——要 AAB 级 PASS 必须先 `:app:bundleStrictOfflineRelease` 重建（Gradle，门禁阶段）。`dump config` 可直接当机器门：缺 `uncompressNativeLibraries` 或值为 `4K` 一律 FAIL。

---

## 4. R8 mapping

### 4.1 结构与规模（实读）

```text
# compiler: R8 / # compiler_version: 9.3.16 / # min_api: 23
# {"id":"com.android.tools.r8.mapping","version":"2.2"}
# pg_map_id: f844e06ce85e2333ce20d178074c3ad36f7dcd0b24994b805d448f11b50bced5
# pg_map_hash: SHA-256 62117d80af33ea9f070700df6f677756134079814d9450a307e3403c0a150402
```

893,641 行 / 126,196,894 B；项目类 **3,760**，其中**真被改名 3,557**、**R8 删除墓碑 191**（`…BatchImportDriver -> R8$$REMOVED$$CLASS$$774`）。

> **口径**：mapping 行数 ≠ 类数；总类数 ≠ 进包类数（有 191 个已删）。任何「mapping 覆盖 N 类 = 包内 N 类」的推断在本仓不成立。批次 43 记 892,986 / 3,754 是另一次构建，两批不可比。

### 4.2 映射 ↔ APK 绑定（B 类，含负控）

取 5 个改名项目类，在 2 个 `classes*.dex` 原字节搜 `L<short>;`：

```text
renamed_project_classes=3557 removed_markers=191 dex_files=2
  …CapabilityFormViewModel -> z50 hits=1    …CapabilityOperation -> a60 hits=2
  …BackgroundWriterQuiesceKt$quiesce…$1 -> ds hits=1    …BatchImportDriverWorker$doWork$1 -> pv hits=1
  …ComposableSingletons$SecondaryScreensKt$$…Lambda18 -> lt0 hits=1
negative_control Lzzzzz9; hits=0        VERDICT PASS
```

### 4.3 keep 回归（`G-REL-07` 复跑）

`usage.txt` 三段（`WorkDatabase_Impl` / `CommonComponentRegistrar` / `ChineseTextRecognizerOptions`）逐段扫描：**均无 `void <init>()`**。与 KD-69 的成因（AGP 9.3 strict full mode 会裁被 keep 类的无参构造器）一致，本轮独立复现。

### 4.4 retrace 可用性 —— 附「错输入也 exit 0」的教训

`C:/Android/Sdk/cmdline-tools/latest/bin/retrace.bat`（Retrace 8.2.33）。**位置参数形式**才对（`retrace.bat <mapping> <stack>`；`--map <f>` 会被当成映射文件名 → `Could not find mapping file '--map'`）。

| 轮 | 输入帧 | 输出 | exit |
|---|---|---|---|
| 控制组 | `a.b.c(SourceFile:1)` + 3 行手写 mapping | `com.example.Foo.bar(Foo.java:1)` | 0 |
| **错形状** | `com.tingyun.smartmistakebook.a60.g(SourceFile:1)` | **原样返回，未还原** | **0** |
| 正确 | `a60.g(SourceFile:1)` | `…CapabilityOperation.g(CapabilityFormViewModel.kt:1)` | 0 |
| 正确 | `wx5.v(Unknown Source:1)` | `…SmartMistakeBookRootKt$…$lambda$18$$inlined$map$1$2$1.invokeSuspend(SmartMistakeBookRoot.kt)` | 0 |

**教训**：R8 会 repackage，混淆名**无包名**（`a60`/`wx5`）；用全限定混淆名构造测试帧时 retrace 原样返回且 exit 0——只看 exit code 会把「没还原」记成「还原成功」。这个假绿发生在**验收工具自己**身上，正是本批要防的形态。
结论：**mapping 可本地反混淆（PASS）**，前提两条：① 帧用 `->` 右值原样；② 判据是「输出含源类名/文件名」，不是 exit code。

### 4.5 分发形态决定 mapping 怎么交付（官方）

`https://support.google.com/googleplay/android-developer/answer/9848633`（本轮抓取）：「Only **ReTrace-compatible** mapping files are supported…same format used by ProGuard or R8」；「This step is only required for developers using **APKs**. If you're using an app bundle and AGP 4.1 or later…**We'll automatically grab the deobfuscation file from the bundle**」；每版本一份；上传前的崩溃不会被反混淆。

→ 本仓后果：**AAB 路线**验收点 = 包内 `proguard.map` 存在且与 AAB 同次构建（§3.2 已满足结构面）；**APK 路线**验收点 = Play Console「App bundle explorer → Downloads → Assets」里有该 versionCode 的 mapping。runbook 必须二选一写明。

---

## 5. 版本号与签名占位

### 5.1 版本号：硬编码两行（PARTIAL）

- `versionCode = 2`、`versionName = "0.2.0"`（`app/build.gradle.kts:33-34`）；flavor 追加 `versionNameSuffix`（`:55`/`:60`）。
- 全树无第二来源：`grep -rn "versionCode\|versionName" --include=*.kts --include=*.toml app/ gradle/` 只命中这 4 行（无 `version.properties`、无 CI 注入）。
- 产物侧一致：`aapt2 dump badging` → `versionCode='2' versionName='0.2.0-offline'`；`output-metadata.json` 同值；AAB `dump manifest` 同值。
- **判 PARTIAL**：升级/降级验收需要「两可比 vc」，而 vc 靠手改两行、**无门阻止两个不同构建共用同一 vc**（本轮三份产物 vc 全 2 正是这个形态）。

### 5.2 签名占位：结构 PASS / 身份 FAIL

| 项 | 事实 | 证据 |
|---|---|---|
| release 配置为空块 + 「Placeholder — populated by environment variables」 | 结构上不落密钥 | `app/build.gradle.kts:19-21` |
| 四变量求值期注入 | `RELEASE_KEY_ALIAS/KEY_PASSWORD/STORE_FILE/STORE_PASSWORD` | `:125-128`、`:132` |
| 缺一则构建前失败 | `validateReleaseSigning` 抛 `GradleException` 列出缺失名；`assemble/bundle/package*Release*` 自动 `dependsOn` | `:144-161`、`:163-165` |
| 本机环境 | `env \| grep -E "^RELEASE_"` **0 命中（4/4 未设）**；仓内 `*.jks`/`*.keystore` **0**；`local.properties` 仅 `sdk.dir` | 本轮实跑 |
| 唯一 keystore | `./.android/debug.keystore`（仓外标准调试库） | 本轮实跑 |
| 文档/代码不一致 | `RELEASE.md:20` 说「Configure `keystore.properties` locally」，而**没有任何代码读它** | `RELEASE.md:20` vs `:125-128` |

**三份产物三个一次性证书**（`apksigner`/`keytool` 实测）：

| 产物 | DN / 指纹 | 方案 |
|---|---|---|
| release APK | `CN=WS8 Ephemeral Local Only, OU=NotForRelease, O=Local, C=CN` / `9dc07e9f…056e20` | v1=true, v2=true, v3/v4=false |
| release AAB | — / `A9:E7:6C:AD:…:3B:27:01` | jar 签名 |
| debug APK | `C=US, O=Android, CN=Android Debug` / `26466dbf…38dbba1` | — |

v3/v4=false 可接受（minSdk 23 只需 v1+v2，`G-REL-10 PASS`），但发布说明须写明。三证书 ⇒ 「同包名不同构建互不可覆盖」是结构性事实（§6.2 已用设备反证）。`RELEASE_*` 4/4 未设 ⇒ 本机无法产出生产签名包：**BLOCKED（外部）**。

### 5.3 产物 ↔ 源绑定（FAIL，但已可机器判定）

APK 内 `META-INF/version-control-info.textproto`（实读）：`revision: "faab17f58b01eab78d5c08a9f843b3af3df2fecf"`。

```text
$ git cat-file -t faab17f5…            → commit
$ git log --oneline -1 faab17f5…       → faab17f5 docs: 批次41 主会话收口（…）
$ git rev-list --count faab17f5..HEAD  → 30
```

⇒ 该 APK 的源码是 HEAD 之前 30 个提交的树，构建时脏改动未记录。判 **FAIL**（`G-REL-00` 判据 (a)：revision == HEAD 且 dirty=0）。但门**已可实现**：`unzip -p <apk> META-INF/version-control-info.textproto | grep revision` + `git rev-list --count <rev>..HEAD`，输出是整数，不需人读。批次 43 的 `NO_SUPPORTED_VCS_FOUND` 属 `git archive` 路线（无 `.git`），与本产物不同因，两个结论并存，别混用。

---

## 6. 升级 / 降级

### 6.1 官方判据

`https://developer.android.google.cn/studio/publish/versioning`：「The Android system uses the `versionCode` value to **protect against downgrades** by preventing users from installing an APK with a lower `versionCode`…」；每次发布必须**更大**；已用过的 vc 不能再用；上限 **2100000000**。仓内制度 `RELEASE.md:44-46`：「Each release must be installable over the previous version with no destructive database migration.」

### 6.2 实测：跨签名升级必须失败（PASS 反证）+ exit code 陷阱

`emulator-5570`（API 23）预装 `.offline`：`versionCode=2`、`versionName=0.2.0-offline`、`pkgFlags=[ DEBUGGABLE … ]`、`firstInstallTime=2026-11-01 05:14:14`。装当前 release APK（同包名、vc 同为 2、证书 `9dc07e9f…` ≠ debug `26466dbf…`）：

```text
$ adb -s emulator-5570 install -r <release.apk>
… 155816991 bytes in 3.960s
Performing Push Install
	pkg: /data/local/tmp/app-strictOffline-release.apk
Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]
ADB_INSTALL_EXIT=0        ← exit code 是 0
```

事后复核设备仍 `versionCode=2` + `DEBUGGABLE` ⇒ 应用未被替换，本次尝试无副作用。

**两条结论**：① 同包名不同证书 ⇒ 覆盖必然失败（签名校验），与 §5.2 的三证书**两独立证据类互证**；② **`adb install` 失败时 exit 0** ⇒ 判据必须是输出含 `Success` **且**事后 `dumpsys versionCode` 等于目标值（进 D0 的 R-EXIT 规则）。

### 6.3 为什么「升级/降级」本体 BLOCKED

本机全部 app 产物 `versionCode` 都是 **2**：`apk/strictOffline/release`、`apk/localFirst/release`、`apk/strictOffline/debug`、`bundle/strictOfflineRelease`（2026-09-30）——**构不出有序对**。要造成对必须同时满足：① 改 `app/build.gradle.kts:33` 的版本号（本轮禁止）；② 跑 Gradle 出两个包（纪律：只在门禁阶段串行）；③ 同一把钥匙签名（生产钥匙缺）。三条都不满足 ⇒ `BLOCKED`，既不得写「升级有问题」，也不得写通过。

### 6.4 升级/降级 runbook（三条路径）

```bash
# 路径 1：内部 dogfood（同钥匙，vc 2→3）
adb -s <S> install -r <v2.apk>; adb -s <S> shell dumpsys package <pkg> | grep versionCode   # 判据 Success / =2
adb -s <S> shell am start -n <pkg>/<launcher>       # 造数据：录入→入库
adb -s <S> install -r <v3.apk>                      # 判据：输出含 Success（不看 $?）
adb -s <S> shell dumpsys package <pkg> | grep versionCode                                  # 判据 =3
adb -s <S> shell run-as <pkg> sqlite3 databases/<db> "pragma user_version; select count(*) from problem;"
# 路径 2：降级（必须失败）
adb -s <S> install -r <v2.apk>                      # 判据 Failure [INSTALL_FAILED_VERSION_DOWNGRADE]
adb -s <S> install -r -d <v2.apk>                   # 显式允许：判据 Success，并按 RELEASE.md 查数据面
# 路径 3：Play 真实升级（AAB）——不在本机
# 内部测试轨道上传 vc=N+1 → 测试账号设备升级 → dumpsys 显示 vc=N+1，
# 且 Play App Signing 的 app signing key 指纹等于已登记值（不是上传钥匙）
```

---

## 7. 外部阻塞项

| # | 阻塞 | 判据 | 为何本机做不到 | 解除后复跑 |
|---|---|---|---|---|
| B1 | 生产签名 | `apksigner --print-certs` 指纹 == 发布指纹；DN 非 `NotForRelease` | `RELEASE_*` 4/4 未设、0 keystore | §5.2 + `G-REL-09/11` |
| B2 | arm64 16KB 运行时 | `getconf PAGE_SIZE`=16384 且三条 native 路径各触发一次 | x86_64 宿主 QEMU2 拒绝 arm64（`FATAL \| Avd's CPU Architecture 'arm64' is not supported…`） | §2.6 + `logcat \| grep nativeloader` |
| B3 | x86_64 16KB 运行时 | 同上 | 本轮 `ps16k_x86_64` 未注册（§2.6） | §2.6 |
| B4 | Play Console AAB 上传/校验 | 上传成功、无 16KB 警告、data safety 一致 | 无账号 | 内部测试轨道 |
| B5 | mapping 上传（APK 路线） | App bundle explorer 有该 vc 的 mapping | 无账号 | §4.5 |
| B6 | 真实 Provider | 请求往返成功 | 无凭据（且约束禁止写死） | 模型链路 D 域门 |
| B7 | 人工金标 AUD-08 | 门值 ≥0.9 | 需 ≥2 标注员 + 1 裁定者 | `tools/ci/check_split_gate.py` |
| B8 | 版本成对产物 | §6.3 | 需改 vc + Gradle + 同钥匙 | §6.4 路径 1/2 |

**runbook 固定 7 段**（每段必须「照着跑 → 得到判定行」）：`R-0 前置指纹`（产物 sha/HEAD/dirty/工具版本/设备 serial+boot_id）→ `R-1 构建` → `R-2 静态门`（16KB 三条 + R8 五条 + manifest + 钉版 + 锁）→ `R-3 身份门`（证书/版本单调/产物↔源/mapping↔vc）→ `R-4 运行门`（安装 `Success` + 冷启 + 三链路 + 16KB 运行时 + 升级降级）→ `R-5 交付门`（AAB 上传 / mapping 路径 / data safety）→ `R-6 回滚`（上一版可覆盖、迁移非破坏、DB 可读）。
本文件已填：R-0（三产物 sha + HEAD + 3755 脏，缺 boot_id）、R-2（16KB 三条 + R8 五条可复跑）、R-3（`apksigner`/`keytool`/textproto+`rev-list`/`dump manifest`）、R-4（`Success` 判定行；跨签名反证）、R-5（AAB 内嵌 `proguard.map`）、R-6（`RELEASE.md:44-46` 要求）。缺：干净树新 sha、生产指纹、16KB 运行时、成对 vc、Play 账号。

---

## 8. 未验证 / 不得写通过

| 项 | 状态 | 原因 |
|---|---|---|
| 16KB 运行时（16384） | **NOT_RUN** | `ps16k_x86_64` 未注册（§2.6）；在线四台皆 4096/API 23 |
| `nativeloader` 三条 native 真装载 | **UNVERIFIED** | 需运行时（`G-REL-28` 仍 PARTIAL） |
| arm64 运行时 | **BLOCKED** | 宿主架构（B2） |
| AAB 内容 = 当前配置 | **FAIL** | 树上 AAB 为 2026-09-30（§2.5） |
| `bundletool build-apks`/`get-device-spec`/`install-apks` | **NOT_RUN** | 本轮只跑 dump/validate |
| 生产签名 / Play App Signing | **BLOCKED** | B1 |
| 升级（同包更高 vc）/ 降级被拒 | **BLOCKED / NOT_RUN** | 无成对产物（§6.3） |
| `versionCode` 跨提交单调性 | **UNVERIFIED** | 无生成器，只有硬编码两行 |
| 构建确定性（两次 clean `cmp`） | **NOT_RUN** | 需 Gradle（`G-REL-02`） |
| mapping ↔ AAB 同次构建 | **UNVERIFIED** | 树上 AAB 陈旧，无法配对 |
| 第三方 `.so` 符号化 | **UNVERIFIED** | 无 `debugsymbols/`，符号在供应商侧 |
| Play 侧 16KB/data safety 提示 | **BLOCKED** | B4 |

---

## 9. 可重跑命令 + 自写检查器

### 9.1 16KB ELF/ZIP 检查器（纯标准库，含 `--selftest` 负控）

```python
import struct, sys, zipfile
PT_LOAD, PT_GNU_RELRO, PAGE = 1, 0x6474E552, 0x4000

def phdrs(b):
    if len(b) < 64 or b[:4] != b"\x7fELF" or b[4] != 2: return None
    po, ps, pn = struct.unpack_from("<Q", b, 0x20)[0], struct.unpack_from("<H", b, 0x36)[0], struct.unpack_from("<H", b, 0x38)[0]
    return [(struct.unpack_from("<I", b, po+i*ps)[0], struct.unpack_from("<I", b, po+i*ps+4)[0],
             struct.unpack_from("<Q", b, po+i*ps+0x10)[0], struct.unpack_from("<Q", b, po+i*ps+0x28)[0],
             struct.unpack_from("<Q", b, po+i*ps+0x30)[0]) for i in range(pn)]

def check(b):
    p = phdrs(b)
    if p is None: return "SKIP(not-elf64)", []
    loads = [x for x in p if x[0] == PT_LOAD]
    if not loads: return "FAIL", ["no PT_LOAD"]
    e = ["LOAD p_align=0x%x < 0x4000" % a for (_t,_f,_v,_m,a) in loads if a < PAGE]
    rl, rw = [x for x in p if x[0] == PT_GNU_RELRO], [x for x in loads if x[1] & 0x2]
    if rl and rw:
        end = rl[0][2] + rl[0][3]
        if end % PAGE:
            lim = (end // PAGE + 1) * PAGE
            e += ["RELRO end 0x%x shares page with RW LOAD @0x%x" % (end, v)
                  for (_t,_f,v,m,_a) in rw if v < lim and v + m > end]
    return ("PASS" if not e else "FAIL"), e

def off(fp, i):  # zipalign 的填充写在 LOCAL header extra 里
    fp.seek(i.header_offset); h = fp.read(30)
    return i.header_offset + 30 + struct.unpack_from("<H", h, 26)[0] + struct.unpack_from("<H", h, 28)[0]

def main(a):
    z = zipfile.ZipFile(a[-1]); bad = 0
    names = sorted(n for n in z.namelist() if n.endswith(".so"))
    if a[1:2] == ["--selftest"]:   # 负控：把 p_align 全改 0x1000，必须变 FAIL
        b = bytearray(z.read(names[0])); po, ps = struct.unpack_from("<Q", b, 0x20)[0], struct.unpack_from("<H", b, 0x36)[0]
        for i in range(struct.unpack_from("<H", b, 0x38)[0]):
            if struct.unpack_from("<I", b, po+i*ps)[0] == PT_LOAD: struct.pack_into("<Q", b, po+i*ps+0x30, 0x1000)
        v = check(bytes(b))[0]; print("selftest %s -> %s" % (names[0], v)); return 0 if v == "FAIL" else 1
    with open(a[-1], "rb") as fp:
        for n in names:
            i = z.getinfo(n); v, e = check(z.read(n))
            print("%s\t%s\toff%%16384=%d\t%d\t%s" % (v, "STORED" if i.compress_type == 0 else "DEFLATED", off(fp, i) % 16384, i.file_size, n))
            bad += v.startswith("FAIL")
    print("TOTAL=%d FAIL=%d" % (len(names), bad)); return 0 if bad == 0 else 1

sys.exit(main(sys.argv))
```

### 9.2 本轮实跑命令（逐条可复跑）

```bash
git rev-parse HEAD; git status --porcelain -uall | wc -l
sha256sum app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk \
          app/build/outputs/bundle/strictOfflineRelease/app-strictOffline-release.aab
C:/Android/Sdk/build-tools/37.0.0/zipalign.exe -c -P 16 -v 4 <apk>        # 末行 Verification successful
python -I elf16k.py <apk>                                                # TOTAL=14 FAIL=0
python -I elf16k.py --selftest <apk>                                     # 负控必须 FAIL
C:/Android/Sdk/build-tools/37.0.0/apksigner.bat verify --print-certs --verbose <apk>
C:/Android/Sdk/build-tools/37.0.0/aapt2.exe dump badging <apk>
keytool -printcert -jarfile <aab>
java -cp "<bundletool>;<aapt2-proto>;<protobuf-java>;<protobuf-java-util>;<guava>;<gson>;<jose4j>" \
  com.android.tools.build.bundletool.BundleToolMain dump config --bundle=<aab>
… BundleToolMain dump manifest --bundle=<aab> --xpath=/manifest/@android:versionCode    # → 2
… BundleToolMain validate --bundle=<aab>                                                # → exit 0，含 32 位 .so
python -I r8bind.py <mapping> <apk> com.tingyun.smartmistakebook.                        # 5/5 + 负控 0
#   r8bind.py（≈40 行，未嵌入本文）：解析 `X -> Y:` 行、跳过 `R8$$REMOVED$$CLASS$$`、
#   统计 orig_last != new_last 的改名数；对前 5 个改名类在全部 classes*.dex 原字节里
#   计数 `L<Y>;`，并以不存在的 `Lzzzzz9;` 作负控（必须 0）。
unzip -p <apk> META-INF/version-control-info.textproto | grep revision                    # → faab17f5…
git cat-file -t faab17f5…; git rev-list --count faab17f5..HEAD                            # → commit / 30
C:/Android/Sdk/cmdline-tools/latest/bin/retrace.bat <mapping> <stack.txt>                 # 判据：输出含源类名
adb -s emulator-5570 shell dumpsys package com.tingyun.smartmistakebook.offline | grep -E "versionCode|pkgFlags"
adb -s emulator-5570 install -r <release.apk>                                             # Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]
env | grep -E "^RELEASE_"; git ls-files "*jks" "*keystore"; cut -d= -f1 local.properties
```

> 未执行：任何 Gradle、`am instrument`、APK/AAB 构建、git 写、成功启动的 emulator、Provider/Play Console。

---

## 10. 发现清单

| # | 发现 | 证据 | 严重度 | 处置 |
|---|---|---|---|---|
| **R5-1** | 树上唯一 AAB 是 2026-09-30，含 9 个 32 位 `.so`、无 `libquickjs.so` ⇒ 不能作为当前配置的验收对象 | §2.5；`d0734105…`；`validate` 清单 | **高（假绿源）** | 发版前重建 AAB；lint 加规则「AAB `.so` ABI 集合 == `abiFilters`」 |
| **R5-2** | `bundletool` 其实可用（重建 classpath）；批次 43「无 fat jar ⇒ BLOCKED」是能力误判 | §3.1 五 jar 配方 | **高（解除阻塞）** | 7 行 java 调用进 runbook；G-REL-17 可从 BLOCKED 升为可核 |
| **R5-3** | `adb install` 失败仍 exit 0 | §6.2 原文 + `ADB_INSTALL_EXIT=0` | **高（判据）** | 安装 PASS = 输出含 `Success` 且事后 vc 等于目标 |
| **R5-4** | `retrace` 对全限定混淆名原样返回且 exit 0 | §4.4 四轮表 | **中（工具假绿）** | 反混淆 PASS = 输出含源类名；帧用 `->` 右值 |
| **R5-5** | 产物 revision `faab17f5` 落后 HEAD 30 提交 | §5.3 | **高（可追溯）** | G-REL-00 用 `rev-list --count` 判定，构建后归档三元组 |
| **R5-6** | 三产物三一次性证书；`RELEASE.md:20` 的 `keystore.properties` 无人读 | §5.2 | **高（发布）** | 固定上传钥匙；RELEASE.md 与环境变量契约对齐（非热区） |
| **R5-7** | mapping 含 191 个 `R8$$REMOVED$$CLASS$$` 墓碑 | §4.1 | **中（口径）** | 「mapping 类数 = 包内类数」的推断作废 |
| **R5-8** | `ps16k_x86_64` 用 `-read-only` 启动写 AVD 目录失败（`qemu-version.txt`） | §2.6 日志 | **低（环境）** | runbook 去掉 `-read-only` 或复制 AVD |

---

## 11. 回链与体量

**回链**：计划 `.zcode/plans/plan-2026-11-05-batch-44-acceptance.md` §2 J、§3、§6；上一批 `docs/research/2026-11-05-batch-43-record.md` §9.7、`docs/research/2026-11-05-release-gates.md`（G-REL-00/03/04/05/07/09/10/12/13/14/15/17/25/27/28）；基线 `docs/research/2026-11-06-batch-44-baseline.md` §4.2/§7；前序 `docs/research/2026-11-03-16kb-page-testing.md`、`docs/research/2026-11-05-clean-tree-release.md`。

**官方来源（本轮抓取，采纳）**：`developer.android.google.cn/guide/practices/page-sizes`（16KB 全部判据与 2027-02-01 截止）；`developer.android.google.cn/studio/publish/versioning`（vc 防降级/递增/唯一/上限）；`support.google.com/googleplay/android-developer/answer/9848633`（ReTrace 格式、AAB 自动取 mapping vs APK 手传）；`gh api repos/google/bundletool/contents/src/main/proto/config.proto`（`UncompressNativeLibraries.alignment` 枚举原文，`:192-210`）。

**拒绝/未采纳**：社区文章「16KB 截止 2025-11-01」（旧口径，官方现行 2027-02-01）；以 `adb install` 的 exit code 作判据（本轮实测反例）；以 `bundletool validate` exit 0 作「AAB 内容合格」（§2.5 反例）。

**体量**：落盘后 `wc -lc` 实测 **468 行 / 33,447 B**（字节远低于 64 KiB；行数略高于本仓 research 件惯用的 400 行，因 12 项检查逐项带命令与输出，未再压缩以免丢证据）。
