# 发版前置的机器可核清单：R8 mapping/校验和、签名占位、16KB 页大小、依赖钉版、冷构建、安装冒烟

- 日期：2026-11-04（批次 42，研究线 R6）
- 计划落点：`.zcode/plans/plan-2026-11-04-batch-42.md:21`（R6）→ 消费者 = §2 WS8 发版前置 61–70（`:99-109`）
- 上游输入：批次 41 记录 `docs/research/2026-11-03-batch-41-record.md`（§9.3 高发现 F7–F12 属 WS1，不在本文范围）；`RELEASE.md` 现有 7 条人工门；`docs/research/2026-11-03-16kb-page-testing.md`、`2026-10-25-16kb-arm64-api23-build.md`、`2026-10-26-r8-workmanager-room.md`、`2026-11-02-kd72-lock-window.md`；证据形态先例 `docs/research/evidence/2026-10-26-batch-24/step03-r8-release-smoke.md`
- 纪律声明：本文只读仓内文件与既有构建产物；**零 Gradle、零设备安装、零 git 写**。Gradle 与设备命令按"门禁阶段串行"归类（B 类），本文只给命令与判据，不声称已跑。

标记约定：

- **A**＝本机现在就能跑（只读/纯命令行，不起 Gradle、不装设备）
- **B**＝门禁阶段串行执行（Gradle 或安装/启动设备）
- **C**＝需用户/组织提供（生产 keystore、Play 账号、arm64 硬件、外部 jar）
- **D**＝外部阻塞＋runbook（本机不可能完成，必须如实标 BLOCKED）
- 【本轮实测】＝2026-11-04 本轮在本机跑过并读到输出；【仓内一手】＝仓内已有文件/证据；【未执行】＝本文未跑，命令留待门禁。

---

## 0. 结论摘要

1. **发版前置里"能机器核的"远比 `RELEASE.md` 现在写的多，但仓里没有一处把产物身份绑在版本上**：`mapping.txt` 只存在于 `app/build/outputs/mapping/<variant>/`（被 `.gitignore:15` 的 `**/build/` 覆盖），`clean` 即销毁；APK 内嵌的 VCS revision 是 `5ee1f90f`（2026-10-31），而 HEAD 是 `faab17f5`，两者相差 **102 个提交**、工作树 544 条 dirty —— 盘上 release 产物与当前源无关，任何"发版就绪"结论都不能拿它当证据（§2.1、§8 G-REL-00）。
2. **R8 mapping 的机器可核面已经够硬，缺的只是"存下来 + 绑版本 + 一条绑定探针"**：`mapping.txt` 头部有 `# compiler_version: 9.3.16` / `# min_api: 23`，884,810 行、125,077,913 B、`sha256 3d929351…`；且可以用"mapping 里的混淆名 → 在 APK 的 dex 字节里找 `L<name>;`"把 mapping 与具体 APK 绑起来（本轮实测 3/3 命中、负控 0，§2.2）。这条探针是现有门禁里**完全没有**的能力。
3. **签名只有"占位 + 变量检查"，没有"身份断言"**：`app/build.gradle.kts:19-24` 的 release signingConfig 是空壳，`:124-139` 在四个 `RELEASE_*` 齐备时注入、否则静默跳过，`:144-158` 的 `validateReleaseSigning` 只在 `assemble*/bundle*/package*` 且名字含 `Release` 时前置（`:161-167`）。本轮实测：四个变量 **0 个存在**；盘上 release APK 的证书 DN 是 `CN=R04 Local`（R-04 实验 key），**不是生产证书**；`validateReleaseSigning` 只查"变量非空"，既不查 keystore 文件可打开、也不查别名存在、更不查产出 APK 的证书指纹 —— 后三条必须补成独立门（§3）。
4. **16KB 静态面当前全绿，且已按官方豁免判据重算**：两个 release APK `zipalign -c -P 16 4` 均 exit 0；APK 内 14 个 `.so`（arm64-v8a 7 + x86_64 7）全部 `Stored`、无 32 位；逐 ELF 检查 14/14 PASS（每个 `PT_LOAD p_align=0x4000`；9 个 RELRO 余数非零但符合官方"独占 LOAD / 余数页无可写段"豁免，§4.2）。**运行时 16KB 本机目前不可跑**：三台在跑的模拟器全是 `PAGE_SIZE=4096`（§4.3）；`ps16k_x86_64`/`ps16k_arm64` 两个 AVD 在盘，但 arm64 在 x86_64 宿主上仍是 D 类阻塞（§9 runbook）。
5. **钉版门与锁门都能本机跑，但锁门是"放行式"**：`check_dependency_pins.py` exit=0（quickjs 三构件 sha256 + 上游 POM 事实 + 解析出的 kotlin-stdlib=2.3.21）；`check_lockfiles.py` exit=0 的同时明确写着 **KD-72 保持 open**：已跟踪锁文件 **0**、盘上 13 个未跟踪、`:benchmark` 缺锁。即"锁门绿"不等于"依赖被锁住"（§5）。
6. **冷构建、安装冒烟、16KB 运行时是本文三处"本机完全没跑"的大项**：冷构建还额外被 Windows 路径问题卡住（`gradle.properties:10-12` 写明必须走 ASCII 路径，`DEVELOPMENT.md:47-57` 要求用 ASCII junction；本轮 `subst` 输出为空，工作目录 `D:\smart mistake book` 带空格）。安装冒烟被"三台模拟器正被他线占用"卡住，且安装会改设备状态，按纪律不在研究阶段执行（§6、§7）；16KB 运行时缺一台 16384 页的设备（§4.3）。
7. **AAB 页对齐检查本机不可执行**：缓存里的 `bundletool-1.18.3.jar` 是库 jar 不是 fat jar，两种调用都失败（`没有主清单属性` / `NoClassDefFoundError: com/google/protobuf/InvalidProtocolBufferException`）。需要官方 `bundletool-all.jar` 才能跑 `dump config | grep alignment`（C 类，§4.4）。
8. **可立即采纳的门禁增量**：§8 给出 25 条 `G-REL-*`（00–24），每条都有"命令 + 判据 + 类别 + 本轮状态"：A 类 15 条（今天就能在 CI 或本地跑）、B 类 7 条（门禁阶段）、C 类 1 条（`bundletool-all`）、D 类 1 条（arm64 16KB），另有 1 条（G-REL-09 证书身份）兼跨 A/B。**没有一条需要改热区文件**；`app/build.gradle.kts` 若要动（例如 `mappingFileUploadEnabled`、sidecar 生成 task）属实施项，本文只给草案。

---

## 1. 基线事实（本轮实读/实跑）

### 1.1 构建与版本锚点

| 项 | 值 | 出处 |
|---|---|---|
| HEAD | `faab17f58b01eab78d5c08a9f843b3af3df2fecf`（2026-11-03） | 本轮 `git rev-parse HEAD` |
| versionCode / versionName | `2` / `0.2.0`（flavor 后缀 `-offline` / `-localfirst`） | `app/build.gradle.kts:33-34`；APK badging 实测 |
| minSdk / targetSdk / compileSdk / buildTools | 23 / 36 / 37 / 37.0.0 | `app/build.gradle.kts:16-17,28-29` |
| AGP / Kotlin / Gradle / R8 | 9.3.0 / 2.3.21 / 9.6.1 / 9.3.16 | `gradle/libs.versions.toml:2-3`、`gradle/wrapper/gradle-wrapper.properties:3`、`mapping.txt:2` |
| 打包 ABI | `arm64-v8a` + `x86_64` | `app/build.gradle.kts:46` |
| 工作树 | 544 条 dirty/untracked | 本轮 `git status --porcelain \| wc -l` |

### 1.2 盘上 release 产物身份（本轮实算）

| 产物 | 字节 | sha256 | mtime |
|---|---|---|---|
| `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk` | 155,807,759 | `9ec35dbac5e0e4b7ca15d91387f794c022b9f37492d80c930672c22a5f11ea0b` | 2026-10-31 22:20 |
| `app/build/outputs/apk/localFirst/release/app-localFirst-release.apk` | 155,666,166 | `8a19d0956bf09e045bf45b81609fae56fc7607014f01cb9c9a875cd827c6b7f8` | 2026-10-26 03:13 |
| `app/build/outputs/mapping/strictOfflineRelease/mapping.txt` | 125,077,913 | `3d9293515ea55d3d49a7ba505fdfb930afc419c9269dfac5fe543032022b4d5f` | 2026-10-31 22:19 |
| `…/strictOfflineRelease/seeds.txt` | 545,931 | `160f0c7dcb6b95449e9000fab14b9265a3d5607aea31466b4b19b10374aeab8e` | 2026-10-31 22:18 |
| `…/strictOfflineRelease/usage.txt` | 8,068,398 | `2584df6ba4dd7d5e0ab57168ff5aeb61bda736e0d8f5afd868159f2e02f78d7b` | 2026-10-31 22:19 |
| `app/build/outputs/bundle/strictOfflineRelease/app-strictOffline-release.aab` | （存在，未算 sha） | — | — |

APK 元数据（`aapt2 dump badging` 实测）：`package name='com.tingyun.smartmistakebook.offline' versionCode='2' versionName='0.2.0-offline'`、`minSdkVersion:'23'`、`targetSdkVersion:'36'`、`compileSdkVersion='37'` —— 与 `app/build/outputs/apk/strictOffline/release/output-metadata.json` 的 `versionCode 2` / `versionName "0.2.0-offline"` 一致。

**源代码漂移（关键）**：APK 内 `META-INF/version-control-info.textproto` 写着 `revision: "5ee1f90f7e2411a6c1865890f6bf5da21f2b57aa"`。本轮 `git merge-base --is-ancestor 5ee1f90f HEAD` → 是祖先；`git rev-list --count 5ee1f90f..HEAD` → **102**。该提交是 2026-10-31 的 `feat(kb): 批次29——F6 导入路径优化…`。⇒ 盘上 release APK/AAB/mapping 全部来自旧树，不能作为本轮"发版就绪"的证据，只能作为"命令可跑通"的样本。

### 1.3 本轮跑过的命令与退出码（可复现）

| # | 命令（省略公共前缀 `cd "D:/smart mistake book"`） | 退出码 | 关键输出 |
|---|---|---|---|
| 1 | `/c/Android/Sdk/build-tools/37.0.0/zipalign.exe -c -P 16 4 <strictOffline release apk>` | 0 | 无输出（`-v` 时末行 `Verification successful`） |
| 2 | 同上，`<localFirst release apk>` | 0 | 同上 |
| 3 | `unzip -lv <apk> \| grep '\.so$'` | 0 | 14 条，全 `Stored`，7×arm64-v8a + 7×x86_64 |
| 4 | `python -I check_elf_align2.py <arm64 dir>` / `<x86_64 dir>` | 0 / 0 | 7 PASS + 7 PASS，`ALL_PASS` |
| 5 | `apksigner.bat verify --print-certs --verbose <apk>` | 0 | `CN=R04 Local`；v1/v2 true、v3/v3.1/v3.2/v4 false；证书 sha256 `9fd478d9…` |
| 6 | `python -I -`（sha256 脚本，见 §1.2） | 0 | 上表五行 |
| 7 | `head -3 mapping.txt; wc -l mapping.txt; grep -c '^com.tingyun.smartmistakebook\..* ->'` | 0 | `# compiler: R8` / `# compiler_version: 9.3.16` / `# min_api: 23`；884,810 行；3,683 个项目类 |
| 8 | `python -I -`（dex 描述符探针，§2.2） | 0 | `Ly50;`/`Lcs;`/`Lov;` 各 1 次；负控 `Lzzzzz9;` 0 次 |
| 9 | `python -I tools/ci/check_release_manifest.py` | 0 | `manifest gate OK: 9 checked + 4 debug merged manifest(s), 5 debug-only component(s) …` |
| 10 | `python -I tools/ci/check_dependency_pins.py` | 0 | `OK: quickjs checksums, upstream POM requirement and resolved runtime pins match`；`resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']` |
| 11 | `python -I tools/ci/check_lockfiles.py` | 0 | `OK: … KD-72 保持 open … 13 个未跟踪锁文件 … 缺锁 project：:benchmark` |
| 12 | `python -I tools/ci/check_r04_keep_rules.py` | 0 | 两个规则文件 `-dontobfuscate` 各 0 命中 |
| 13 | `env \| grep -c '^RELEASE_'` | 0 | `0` |
| 14 | `git ls-files "*gradle.lockfile" \| wc -l` | 0 | `0` |
| 15 | `unzip -p <apk> META-INF/version-control-info.textproto` | 0 | `revision: "5ee1f90f…"` |
| 16 | `cat app/build/intermediates/lint_vital_return_value/strictOfflineRelease/…/return-value-strictOfflineRelease.txt`；`tail` 文本报告 | 0 | `0`；报告仅一行 `No issues found.` |
| 17 | `adb devices`；逐台 `getconf PAGE_SIZE` / `getprop ro.product.cpu.abi` | 0 | 3 台 `emulator-5562/5566/5584`，全 `x86_64` / `4096` / SDK 34 |
| 18 | `java -jar …/bundletool-1.18.3.jar dump config --bundle=…aab`；`java -cp …jar com.android.tools.build.bundletool.BundleToolMain …` | 1 / 1 | `没有主清单属性` / `NoClassDefFoundError: com/google/protobuf/InvalidProtocolBufferException` |
| 19 | `subst` | 0 | 空（无 ASCII SUBST 盘） |
| 20 | `grep -cE '[0-9]\+|latest\.(release|integration)|SNAPSHOT' gradle/libs.versions.toml` | 0 | `0`（目录里无动态版本） |

未跑（见 §11）：任何 `./gradlew *`、任何 `adb install/am start`、任何 `ps16k` 模拟器启动、retrace 实战、两次 clean 构建比对、bundletool 对齐 dump。

---

## 2. R8 mapping 与校验和

### 2.1 现状（可指认的四个缺口）

1. **只在 `build/` 里**：`mapping.txt`/`seeds.txt`/`usage.txt`/`configuration.txt` 全在 `app/build/outputs/mapping/<variant>/`，而 `.gitignore:15` 是 `**/build/` ⇒ **`clean` 或任何清理即永久丢失**；同时它们也不该进 git（单 variant 的 mapping 就 125 MB）。
2. **无 sidecar，无法机器绑定**：mapping 目录名只带 variant（`strictOfflineRelease`），文件内没有 versionCode/versionName/APK sha；APK 侧只有 `META-INF/version-control-info.textproto` 的 git revision。⇒ "这份 mapping 对应哪个 APK / 哪个版本"目前靠人工记忆（§1.2 已经证明这种记忆会漂 102 个提交）。
3. **无校验和归档**：本轮才算出的 `sha256 3d929351…` 在任何文件里都不存在。
4. **无 `mappingFileUploadEnabled`**：`app/build.gradle.kts:88-99` 的 `release { }` 里没有该开关，AAB 上传 Play 时不会自动带 mapping（崩溃栈在 Play Console 里仍是混淆名）。

### 2.2 机器检查（A 类，本机现在可跑）

```bash
# 1) 结构完整性：头部三行 + 行数 + 项目类覆盖
head -3 app/build/outputs/mapping/strictOfflineRelease/mapping.txt
#   # compiler: R8
#   # compiler_version: 9.3.16
#   # min_api: 23
wc -l < app/build/outputs/mapping/strictOfflineRelease/mapping.txt     # 884810（本轮实测）
grep -c '^com.tingyun.smartmistakebook\..* ->' …/mapping.txt           # 3683（本轮实测）

# 2) 混淆确实发生（不是 -dontobfuscate 假绿）：抽 3 个项目类，目标名必须是短的
grep -m3 '^com.tingyun.smartmistakebook\.[A-Za-z0-9_$.]* -> [a-z][a-z0-9]*:' …/mapping.txt
#   com.tingyun.smartmistakebook.BackgroundWriterQuiesceKt$quiesceApplicationScopeWriters$1 -> cs:
#   com.tingyun.smartmistakebook.BatchImportDriverWorker$doWork$1 -> ov:
#   com.tingyun.smartmistakebook.CapabilityFormViewModel -> y50:

# 3) 绑定探针：mapping 的混淆类必须在【这个】APK 的 dex 里出现；负控必须不出现
python -I - <<'PY'
import zipfile
apk = "app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk"
with zipfile.ZipFile(apk) as z:
    dex = b"".join(z.read(n) for n in z.namelist() if n.endswith(".dex"))
for n in ["y50", "cs", "ov"]:                       # 来自上一步 mapping
    print(n, dex.count(("L%s;" % n).encode()))      # 期望 >=1
print("negative", dex.count(b"Lzzzzz9;"))           # 期望 0
PY
#   本轮实测：y50=1 cs=1 ov=1 negative=0

# 4) 校验和
sha256sum app/build/outputs/mapping/strictOfflineRelease/{mapping,seeds,usage,configuration}.txt
```

**判据**：① 三行头部齐全且 `compiler_version` 与 `gradle/libs.versions.toml` 的 AGP 时代一致（R8 随 AGP 发版）；② 行数 > 0 且项目类计数 > 0；③ 至少 1 个项目类被改名为短名（证明混淆面真实）；④ 绑定探针 ≥3 个全命中且负控 0；⑤ 四个 sha256 落进 sidecar（§2.4）。

> 绑定探针的边界（必须写清，不能当"完整证明"）：它只证明"mapping 里的名字存在于该 APK 的 dex 字符串池"，不证明 mapping 与 dex 逐成员一致。逐成员一致只能靠"同一次构建产出"这条流程约束 + sidecar 的 sha256 绑定。反例也记下：短名（`cs`/`ov`）在默认包下唯一，若映射到 `a/b` 形式需改用全限定混淆名。

### 2.3 R8 keep 回归（A 类，"混淆把必需构造器裁掉"的复现门）

KD-69/KD-74 的失败形态是 `NoSuchMethodException`：strict full mode 下被 keep 的类不再隐式保无参构造器。现成的机器判据读 `usage.txt`（被裁清单），不读配置：

```bash
grep -A8 '^androidx.work.impl.WorkDatabase_Impl:' …/usage.txt
grep -A8 '^com.google.mlkit.common.internal.CommonComponentRegistrar:' …/usage.txt
grep -A8 '^com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions:' …/usage.txt
python -I tools/ci/check_r04_keep_rules.py        # exit 0 = 规则文本里没有 -dontobfuscate
```

**判据**：三段里都**不得**出现 `void <init>()`；`check_r04_keep_rules.py` exit 0。
**本轮实测（PASS）**：`WorkDatabase_Impl` 段只列 `_rawWorkInfoDao`/`clearAllTables`/`rawWorkInfoDao`；`CommonComponentRegistrar` 段只列 `zza`；`ChineseTextRecognizerOptions` 段只列字段与 getter —— 三段均无 `<init>()`；keep 规则门 exit 0。

### 2.4 sidecar 规范（建议 `release-artifacts.json`，一次构建一份）

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-11-04T00:00:00+08:00",
  "variant": "strictOfflineRelease",
  "applicationId": "com.tingyun.smartmistakebook.offline",
  "versionCode": 2,
  "versionName": "0.2.0-offline",
  "git": { "revision": "<full sha>", "dirty": false },
  "apk":   { "path": "…", "bytes": 0, "sha256": "…" },
  "aab":   { "path": "…", "bytes": 0, "sha256": "…" },
  "mapping": { "path": "…", "bytes": 0, "sha256": "…", "compilerVersion": "9.3.16", "lines": 0 },
  "r8":    { "seedsSha256": "…", "usageSha256": "…", "configurationSha256": "…" },
  "signing": { "certSha256": "…", "certDn": "…", "schemes": ["v1", "v2"] },
  "sixteenKb": { "zipalignExit": 0, "abis": ["arm64-v8a", "x86_64"], "elfPass": 14, "elfFail": 0 },
  "gates": { "G-REL-03": "PASS", "G-REL-09": "FAIL", "G-REL-12": "NOT_RUN" }
}
```

**每个字段消灭的具体失败**：`git.revision` — APK 与源漂移无人发现（§1.2 的 102 提交就是这样漏的）；`apk/mapping.sha256` — mapping 与 APK 张冠李戴；`compilerVersion` — 用旧 R8 的 mapping 解释新 APK 的栈；`signing.certDn` — 测试 key 冒充实签（§3）；`sixteenKb.*` — 静态面回退只有人眼看得见；`gates` — 门禁状态只活在一次会话里。

**落点建议（不在本文改）**：由 `tools/ci/release_artifacts.py` 生成（Python 只读产物，不调 Gradle），CI 在 release dry-run 后调用并把 json + mapping + apk 一起作为 artifact 上传。

### 2.5 保留策略与版本绑定

- **不进 git**：125 MB/变体 ×2 flavor + `.prt` 11.9 MB，`.gitignore` 现状正确。
- **归档位置**：仓外（Play Console 上传，或对象存储）＋按 `mapping-<applicationId>-<versionCode>-<versionName>-<variant>.txt` 命名；仓内只留 `release-artifacts.json` 与其 sha256（小文件，可入库或随证据目录落盘）。
- **寿命**：至少覆盖该版本在 Play 的整个可下载期（崩溃栈回溯需求期）。
- **Play 侧**：二选一 —— (a) `release { mappingFileUploadEnabled = true }`（AAB 自动带）；(b) 手工上传 mapping。当前两者都没做（§2.1-4）。

---

## 3. 签名配置占位

### 3.1 现状（读码 + 实测）

| 事实 | 证据 |
|---|---|
| release signingConfig 是空壳，无 storeFile/keyAlias | `app/build.gradle.kts:19-24` |
| 四个变量齐备时才注入，缺任一则**静默跳过**（不报错） | `:124-139`（`configureReleaseSigningIfAvailable`） |
| `validateReleaseSigning` 只在缺变量时抛 `GradleException` | `:144-158` |
| 该 task 仅在任务名含 `Release` 且以 `assemble`/`bundle`/`package` 开头时前置 | `:161-167` |
| 当前四变量：**0 个存在** | 本轮 `env \| grep -c '^RELEASE_'` → `0` |
| 盘上 release APK 证书 DN `CN=R04 Local, OU=Local, O=Local`，证书 sha256 `9fd478d9…`，v1+v2 | 本轮 `apksigner verify --print-certs` |
| CI 用一次性 key（`CN=CI Dry Run`）跑 release dry-run | `.github/workflows/android-check.yml:109-120`（口令写在 workflow，属一次性非生产凭据，本文不复述） |
| 生产签名 UNVERIFIED 已是台账事实 | `docs/known-defects.md:2706`（KD-69 关闭块原文："生产 release 签名 UNVERIFIED（`validateReleaseSigning` exit=1 缺四个 `RELEASE_*`，`step03-r8-release-smoke.md:48`）"） |

⇒ **盘上任何 release APK 都不是生产签名**。今天跑 `:app:assembleStrictOfflineRelease` 会在 `validateReleaseSigning` 处失败（这一条是代码事实 + 变量实测 + 批次 24 证据三方一致，**本轮未跑 Gradle**）。

### 3.2 机器检查

| 门 | 类别 | 命令 | 判据 |
|---|---|---|---|
| G-REL-08 变量全或无 | B | `./gradlew --console=plain :app:validateReleaseSigning` | 四个变量齐全 → exit 0；**负控**：`env -u RELEASE_STORE_PASSWORD …` → exit 1 且报错文本含 `RELEASE_STORE_PASSWORD` |
| G-REL-09 产出证书身份 | A（对已构建 APK）/B（构建后） | `apksigner verify --print-certs --verbose <apk>`；`apksigner verify --print-certs --verbose <aab 解出的 base apk>` | exit 0；`certificate SHA-256 digest` == 发布证书指纹（记录在 sidecar）；**DN 不得匹配 `CN=R04 Local` / `CN=CI Dry Run` / `CN=Android Debug`** |
| G-REL-10 签名方案底线 | A | 同上 verbose 输出 | `v2 scheme: true`（minSdk 23 → v2 覆盖 24+，v1 覆盖 23）；v3/v4 未启用需在发布说明里写明（当前 v3=false） |
| G-REL-11 keystore 可打开性 | B（新增，现无门） | `keytool -list -keystore "$RELEASE_STORE_FILE" -alias "$RELEASE_KEY_ALIAS"`（口令经 `-storepass:env`） | exit 0；输出条目类型 `PrivateKeyEntry` |

**必须补的缺口（G-REL-11）**：`validateReleaseSigning` 只做"变量非空"，`RELEASE_STORE_FILE` 指向不存在文件时 `assembleRelease` 会以 AGP 的打包错误收场（信息不指向根因）；别名错/口令错同理。这一条消灭的具体失败＝"签名变量看着齐备、构建跑到打包末尾才炸，且错误文本不含变量名"。

**关于 `apksigner verify` 的 WARN**：本轮输出含大量 `WARNING: META-INF/xxx.version not protected by signature`。这是 JAR 签名不覆盖 `META-INF/` 的固有行为，v2 方案覆盖整包 ⇒ 判为 WARN 不判 FAIL；但门禁脚本要显式把这些 WARN 归为已知类，否则会被人为忽略成"噪声"。

### 3.3 需用户提供（C 类）

| 项 | 形态 | 为什么机器不能替 |
|---|---|---|
| 生产 keystore | 仓外文件 + 口令 | `.gitignore:18-20` 明确禁止入库；口令不得出现在任何源/示例/测试 |
| 四个环境变量 | `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` / `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` | 名字已定（`app/build.gradle.kts:125-128`），值只能由发布者注入 |
| Play App Signing 决策 | 是否启用上传密钥（upload key）+ Play 托管 | 影响 `apksigner` 期待的上传证书指纹与 G-REL-09 的"发布证书"定义 |

---

## 4. 16KB 页大小

### 4.1 官方判据（2026-10-16 更新版，本轮抓取）

来源：`https://developer.android.com/guide/practices/page-sizes`（主站本轮 `WebFetch` 连接超时；同文经官方镜像 `https://developer.android.google.cn/guide/practices/page-sizes` 抓取成功，`Last updated 2026-10-16 UTC`）。原文要点（逐字）：

- 政策：`"all apps targeting Android 15 (API level 35) and higher must support 16 KB memory page sizes on 64-bit devices on Google Play"`；`"Starting February 1, 2027, if your app updates don't support 16 KB memory page sizes, you won't be able to release these updates."`
- ZIP 层：`zipalign -v -c -P 16 4 APK_NAME.apk`，`"The last line of the output will say "Verification successful" if all of the shared libraries are aligned correctly."`（build-tools ≥ 35.0.0）
- ELF 层：`llvm-objdump -p SHARED_OBJECT_FILE.so | grep LOAD`，`"ensure that the load segments don't have values less than 2**14"`。
- RELRO：`llvm-readelf -Wl SHARED_OBJECT_FILE.so | grep 'RELRO\|Type'`，判据 `(VirtAddr + MemSiz) % 0x4000 == 0`；**豁免**原文：`"Here is an example of an aligned .so file where GNU_RELRO is in its own LOAD segment … Although 0x1000 isn't zero, this .so file is still 16 KB compliant. The modulo formula applies when GNU_RELRO shares a LOAD segment with subsequent writable data (such as .data or .bss)."`
- 运行时：`adb shell getconf PAGE_SIZE` 必须返回 `16384`；也可用 developer option `Boot with 16KB page size`（Pixel 8/8 Pro/8a + Android 15 QPR1+、Pixel 9 系列 + QPR2+、9a + Android 16+）。
- 构建侧：`AGP ≥ 8.5.1` + `NDK r28+` 默认合规；本仓 AGP 9.3.0 满足。

**本项目命中面**：targetSdk 36（`app/build.gradle.kts:29`）⇒ 在政策范围内；打包含 14 个预编译 `.so`（见下）⇒ 必须走静态 + 运行时验证。

### 4.2 本轮实测（A 类，全 PASS）

```
# ZIP 层（两个 release APK，build-tools 37.0.0）
zipalign.exe -c -P 16 4 app-strictOffline-release.apk   → exit 0（-v 末行 Verification successful）
zipalign.exe -c -P 16 4 app-localFirst-release.apk      → exit 0
# .so 清单：14 条，全部 Stored（未压缩，符合 -P 16 判据前提）
#   arm64-v8a: libLiteRt, libLiteRtClGlAccelerator, libandroidx.graphics.path,
#              libdatastore_shared_counter, liblitert_jni, libmlkit_google_ocr_pipeline, libquickjs
#   x86_64   : 同上 7 个
# 无 armeabi-v7a / x86 条目（abiFilters 已裁，见 app/build.gradle.kts:46）
```

ELF 层（本机无 NDK —— 本轮 `ls /c/Android/Sdk` 实测只有 `build-tools/cmdline-tools/emulator/licenses/platform-tools/platforms/system-images`，**无 `ndk` 目录**，故 `llvm-objdump`/`llvm-readelf` 不可用；改用同判据的 Python 解析器，脚本见 §12；等价性先例见批次 24 记录 §8 对 `llvm-readobj` 的裁定）：

| ABI | .so | `PT_LOAD` p_align | RELRO `end % 0x4000` | 判定 |
|---|---|---|---|---|
| arm64-v8a | libLiteRt | 3×0x4000 | 4096（余数页无可写 LOAD） | PASS（豁免） |
| arm64-v8a | libLiteRtClGlAccelerator | 3×0x4000 | 0 | PASS |
| arm64-v8a | libandroidx.graphics.path | 3×0x4000 | 8192（豁免） | PASS |
| arm64-v8a | libdatastore_shared_counter | 4×0x4000 | 0 | PASS |
| arm64-v8a | liblitert_jni | 3×0x4000 | 4096（豁免） | PASS |
| arm64-v8a | libmlkit_google_ocr_pipeline | 3×0x4000 | 12288（豁免） | PASS |
| arm64-v8a | libquickjs | 3×0x4000 | 4096（豁免） | PASS |
| x86_64 | 同 7 个库 | 3–4×0x4000 | 0 ×3 / 8192 与 12288 各 1（豁免） | PASS |

合计 **14/14 PASS**（5 个精确整倍 + 9 个豁免），`HAS_FAIL` 未出现。

> **本文第一版判据曾误报 5 个 FAIL**：错误版本把"RELRO 独占 LOAD"当成"RELRO 不被任何 LOAD 包含"，于是把官方明确豁免的形态判红。修正后按官方机制实现（"余数页内是否存在可写 LOAD 起点"）全 PASS。这条自我更正留在文里，因为它是这类门最容易复发的错误方向：**豁免判据写反 ⇒ 合规包被拦**；漏掉豁免 ⇒ 反倒安全（宁可多拦）。门禁脚本应当先跑正/负控（拿 32 位 ML Kit 的 `.so` 当负控样本，它有 `p_align=4096` 与同页可写 RELRO，必须判 FAIL）。

### 4.3 运行时（本机现状：4KB；16KB 需 ps16k AVD）

本轮 `adb devices` → `emulator-5562`、`emulator-5566`、`emulator-5584` 三台；逐台 `getconf PAGE_SIZE` 全 `4096`，`abi=x86_64`，`sdk=34`。⇒ **这三台都不能证明 16KB 运行时**。

仓内 AVD 清单：`~/.android/avd/ps16k_x86_64.avd`（`abi.type = x86_64`）与 `ps16k_arm64.avd` 均存在（本轮 `ls` 实测），与 `docs/research/2026-11-03-16kb-page-testing.md:144-145` 记录一致。

| 路径 | 类别 | 命令 | 判据 |
|---|---|---|---|
| 16KB 运行时（x86_64 ps16k） | B | `emulator -avd ps16k_x86_64 -port 5620 …`；`adb -s emulator-5620 shell getconf PAGE_SIZE`；`adb install -r <release apk>`；冷启动 + `logcat` 抓 `linker`/`RELRO`/`SIGSEGV` | `getconf` = **16384**；`install Success`；冷启动 `Status: ok`；`linker`/`RELRO`/`SIGSEGV` 命中 0。**必须触发一次 `libquickjs.so` 的 dlopen 路径**（APK 已含该库，懒加载下不触发等于没测） |
| 16KB 运行时（arm64） | **D** | 见 §9 runbook | 同上，但需 arm64 真机或 arm64 宿主 |

### 4.4 AAB 层（C 类，本机不可执行）

官方：`bundletool dump config --bundle=<my .aab> | grep alignment`，出现 `PAGE_ALIGNMENT_16K` 才算 AAB 请求了 16KB 对齐。

本轮实测两种调用都失败：

```
java -jar .gradle/caches/…/bundletool-1.18.3.jar dump config --bundle=…aab
  → 没有主清单属性（该缓存件是库 jar，非可执行 fat jar）
java -cp …/bundletool-1.18.3.jar com.android.tools.build.bundletool.BundleToolMain dump config …
  → NoClassDefFoundError: com/google/protobuf/InvalidProtocolBufferException
```

⇒ 需要官方 `bundletool-all-<ver>.jar`（GitHub release）。**优先级说明**：APK 层 `zipalign` 已覆盖"安装到设备的那份包"；AAB 层只在走 Play 分发且 Play 重新切分 APK 时才多一层风险，因此列为 C 类"建议补"，不列为阻塞。

### 4.5 判据汇总（16KB）

1. `zipalign -c -P 16 4 <apk>` exit 0 —— 两个 flavor 各一次。
2. APK 内 `.so` 全部 `Stored` 且 ABI ⊆ `{arm64-v8a, x86_64}`。
3. 每个 `.so`：所有 `PT_LOAD p_align ≥ 0x4000`；RELRO 满足 `end % 0x4000 == 0` **或** 余数页内无写 LOAD 起点。负控样本必须判 FAIL。
4. `ps16k` 设备 `getconf PAGE_SIZE` = 16384 且装机冷启动无 linker/RELRO 崩溃。
5. （可选）AAB `PAGE_ALIGNMENT_16K`。

---

## 5. 依赖钉版与供应链

### 5.1 钉版门（A 类，本轮 PASS）

```
$ python -I tools/ci/check_dependency_pins.py
catalog: quickjs-kt-android=1.0.15 kotlin(compile/expected runtime)=2.3.21/2.3.21 quickjs-kotlin-stdlib(upstream POM)=2.4.10
AAR matches: [...quickjs-release.aar]
POM matches: [...quickjs-kt-android-1.0.15.pom]
.module matches: [...quickjs-kt-android-1.0.15.module]
resolution artifact: app/build/intermediates/incremental/lintVitalAnalyzeStrictOfflineRelease/strictOfflineRelease-artifact-dependencies.xml
resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']
OK: quickjs checksums, upstream POM requirement and resolved runtime pins match
exit=0
```

它消灭的具体失败：quickjs AAR 的 POM 以软约束把运行时 `kotlin-stdlib` 从 2.3.21 静默抬到 2.4.10（仓内零 diff）。判据分两条独立不变量（上游事实 / 本项目决定），负控开关 `--expect-kotlin-runtime 2.4.10` 应当红 —— **负控本轮未执行**（§11）。

**可见的耦合**：它消费的是 **release 变体**的 `lintVitalAnalyzeStrictOfflineRelease` 中间产物 ⇒ 只有在 release 变体构建过之后才有效。脚本自带的缺失分支会直接失败并提示 `no *artifact-dependencies.xml under app/build/intermediates; run :app:assembleLocalFirstRelease (or lintVital) first`（`tools/ci/check_dependency_pins.py:112-129`，本轮读码确认，未实跑缺失分支）——即 `clean` 之后单独跑这把门是红，不是"跳过"。

### 5.2 锁文件门（A 类，exit 0 但**不是**"已锁住"）

```
$ python -I tools/ci/check_lockfiles.py
OK: dependency locking 尚未落地：没有任何已跟踪的 gradle.lockfile，KD-72 保持 open，本门跳过
    …；工作区存在 13 个未跟踪锁文件（例如 app/gradle.lockfile），不能原样提交；缺锁 project：:benchmark
exit=0
$ git ls-files "*gradle.lockfile" | wc -l
0
```

判据（脚本语义，`tools/ci/check_lockfiles.py:1-45`）：一旦存在第一个已跟踪锁，则每个 `include` 的 project 必须有锁、每个锁必须含 `*CompileClasspath` + `*RuntimeClasspath`（Android 模块还要 unitTest/androidTest）、且不得有"锁对应 project 不在 include"的孤儿。
**当前状态**：放行（因为"锁尚未落地"），KD-72 由 `docs/known-defects.md:2784-2800` 维持 open。⇒ 任何"依赖已钉死"的发版结论都**不能**引用这把门的 exit 0。

`gradle/libs.versions.toml` 无动态版本（本轮 `grep -cE '[0-9]\+|latest\.(release|integration)|SNAPSHOT'` → **0**），这是"版本不会自己漂"的另一半证据；根 `build.gradle.kts:64-83` 把 `org.jetbrains.kotlin:kotlin-stdlib` 约束回编译期版本（`eachDependency` + `useVersion`，非 `force`）。

### 5.3 Gradle wrapper 校验和（A 类，本轮双源一致）

| 源 | 值 |
|---|---|
| 仓内 pin：`gradle/wrapper/gradle-wrapper.properties:4` | `distributionSha256Sum=9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14` |
| 官方：`https://services.gradle.org/distributions/gradle-9.6.1-bin.zip.sha256`（本轮抓取） | `9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14` |

**判据**：两值必须逐字符相等；wrapper 还必须 `validateDistributionUrl=true`（`:8`）。消灭的具体失败：构建工具本身被替换/下载到被篡改的分发包。

### 5.4 供应链快照（只读，B 类）

```bash
./gradlew --console=plain :app:dependencies --configuration strictOfflineReleaseRuntimeClasspath > build/deps-runtime.txt
./gradlew --console=plain :app:dependencies --configuration strictOfflineReleaseCompileClasspath > build/deps-compile.txt
```

判据：与上一版快照逐行 diff，任何版本变化必须有对应台账行（`docs/research/2026-10-26-dependency-governance.md` 的形态）。license/更新度属人工判断项（C 类"判断"，机器只能给清单）——本轮**未跑**，因为 Gradle 只在门禁阶段跑。

---

## 6. 冷构建（B 类，本轮未跑 + 路径前置未满足）

### 6.1 路径前置（本轮实测不满足）

- `gradle.properties:10-12` 原文：`"Windows uses the workspace's ASCII SUBST path for Gradle while editors retain D:\ paths. Mixing those roots corrupts Kotlin incremental caches, so canonical builds are non-incremental."`，并设 `kotlin.incremental=false`。
- `DEVELOPMENT.md:47-57`：`"On Windows, keep the checkout path free of non-ASCII characters … run verification from an ASCII directory junction"`（示例 `New-Item -ItemType Junction -Path D:\smb-build -Target D:\智能错题本`）。
- 本轮 `subst` → **空**；当前工作目录 `D:\smart mistake book`（含空格）。
- ⇒ **冷构建前必须先建 ASCII junction/SUBST 并从那里跑**，否则结论不可复现（且历史上有"混用根路径损坏 Kotlin 缓存"的记录）。

### 6.2 命令与判据

```bash
# 0) 前置：ASCII 路径（示例，具体盘符由使用者定）
#    powershell: New-Item -ItemType Junction -Path D:\smb-build -Target "D:\smart mistake book"
cd /d/smb-build

# 1) 冷构建（无守护、离线、禁 build cache；两个 flavor 各一次）
./gradlew --no-daemon --offline --no-build-cache --console=plain \
  clean :app:assembleStrictOfflineRelease :app:bundleStrictOfflineRelease
# 判据：exit 0；BUILD SUCCESSFUL；"actionable tasks" 中 compile/R8 任务均 executed（不是 UP-TO-DATE）

# 2) 产物身份 ⇒ 写 sidecar（§2.4）
sha256sum app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk

# 3) 确定性（同一棵树连续两次）
cp app-strictOffline-release.apk /tmp/run1.apk && 重复步骤 1
cmp /tmp/run1.apk app-strictOffline-release.apk
```

判据：① `--offline` 下 exit 0（证明依赖已全部本地可解析，无"构建时偷偷拉网"）；② 冷构建耗时与 `actionable tasks … executed` 计数被记录（趋势用，不作阈值）；③ 确定性 —— `cmp` 相同则 PASS；不同则必须给出差异来源清单（候选：`META-INF/version-control-info.textproto` 的 revision、`baseline.prof`、dex 顺序），并把"不确定"写进发版说明，**不得**用"应该一样"含糊过去。APK 内条目时间戳已固定为 `1981-01-01`（本轮 `unzip -l` 实测）⇒ 可复现性是可达的，但本轮**未验证**。

**这段消灭的具体失败**：本地增量构建掩盖"从零 clone 的新机器上构建失败"（缺依赖、缺生成物、缺 schema 导出），以及"两次构建产物不同、无法判断线上崩溃栈对应哪次构建"。

---

## 7. 安装冒烟（B 类，本轮未跑）

### 7.1 为什么本轮不跑

三台模拟器（`emulator-5562/5566/5584`）正被其他轨道占用（同机并发设备轨），`adb install`/`am start` 会改设备状态、卸载/替换同名包，可能直接毁掉他线正在跑的用例。纪律要求 Gradle/设备串行 ⇒ 本文只落命令，不执行。

### 7.2 命令与判据（沿用批次 24 E-03 的约定，`docs/research/evidence/2026-10-26-batch-24/step03-r8-release-smoke.md:64-101`）

```bash
A=/c/Android/Sdk/platform-tools/adb.exe; S=emulator-XXXX     # 门禁阶段指定独占设备
export MSYS_NO_PATHCONV=1
# release 与 debug 签名不同 ⇒ 先卸载同名包（否则 INSTALL_FAILED_UPDATE_INCOMPATIBLE）
"$A" -s $S uninstall com.tingyun.smartmistakebook.offline
"$A" -s $S install -r app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk

# 冷启动 ×2
for i in 1 2; do
  "$A" -s $S shell am force-stop com.tingyun.smartmistakebook.offline
  "$A" -s $S logcat -c
  "$A" -s $S shell am start -W -n com.tingyun.smartmistakebook.offline/com.tingyun.smartmistakebook.MainActivity
  sleep 6
  "$A" -s $S shell pidof com.tingyun.smartmistakebook.offline
  "$A" -s $S logcat -v threadtime -d > build/release-smoke/coldstart$i.txt
done

# 核产物身份是否真装上（不是装了你以为的那个包）
"$A" -s $S shell pm path com.tingyun.smartmistakebook.offline
"$A" -s $S shell sha256sum <上面 pm path 输出的 apk 路径>
```

| 判据 | 值 |
|---|---|
| `install` | `Success` |
| `am start -W` | `Status: ok`、`LaunchState: COLD`（首启） |
| `pidof` | 非空（进程存活） |
| logcat 九类签名 | `FATAL EXCEPTION` / `StartupException` / `NoSuchMethodException` / `ClassNotFoundException` / `NoClassDefFoundError` / `UnsatisfiedLinkError` / `MlKitException` / `SIGSEGV` / `ANR in` 全 **0** |
| 设备侧 apk sha256 | == 产物 sha256（sidecar 里的值）；不等即"装了别的包"，直接 FAIL |
| 核心三链路 | 需按计划 WS8-62 定义（本轮未定义，见 §11 缺口）——当前只覆盖"启动不崩"，**不覆盖**导出/复习/OCR |

**16KB 变体（在 ps16k AVD 上）**：同一套命令 + `getconf PAGE_SIZE`=16384 前置 + `logcat | grep -Ei 'linker|RELRO|SIGSEGV'` 0 命中。

---

## 8. 发版门清单（`release-gates` 草案，供 WS8-70 落盘）

> 类别：A 本机可跑 / B 门禁阶段（Gradle 或设备）/ C 需用户提供 / D 外部阻塞。
> "现状"列全部是**本轮实测或本轮读码**，未跑的一律写 `NOT_RUN`，不写 PASS。

| ID | 门 | 类别 | 命令（要点） | 判据 | 现状 |
|---|---|---|---|---|---|
| G-REL-00 | 产物↔源代码 | A | 读 `META-INF/version-control-info.textproto` 的 revision，`git merge-base --is-ancestor <rev> HEAD` + `git status --porcelain` | 待发版产物必须由 `clean` 后的当前 HEAD 构建；`dirty=false` | **FAIL**：盘上 APK rev=`5ee1f90f`，距 HEAD **102** 提交，树 544 dirty |
| G-REL-01 | 冷构建可离线 | B | §6.2 第 1 步 | `--offline` exit 0 | NOT_RUN |
| G-REL-02 | 构建确定性 | B | §6.2 第 3 步 `cmp` | 相同，或差异来源清单已写明 | NOT_RUN |
| G-REL-03 | mapping 结构完整 | A | §2.2 第 1 步 | 三行头 + 行数>0 + 项目类计数>0 | PASS（884,810 行 / 3,683 类） |
| G-REL-04 | 混淆真实发生 | A | §2.2 第 2 步 | ≥1 项目类被改短名；`-dontobfuscate` 0 命中（`check_r04_keep_rules.py`） | PASS（`CapabilityFormViewModel -> y50` 等；规则门 exit 0） |
| G-REL-05 | mapping↔APK 绑定 | A | §2.2 第 3 步 | ≥3 描述符命中、负控 0 | PASS（3/3，负控 0） |
| G-REL-06 | 校验和归档 | A | §2.2 第 4 步 + sidecar | 四份 sha256 在 sidecar 且与文件一致 | **FAIL**：sidecar 不存在 |
| G-REL-07 | R8 keep 回归 | A | §2.3 | 三段 `usage.txt` 无 `<init>()` | PASS |
| G-REL-08 | 签名变量全或无 | B | `:app:validateReleaseSigning`（含负控） | 齐备 exit 0；缺一 exit 1 且报名字 | NOT_RUN（变量实测 0/4，按 `:144-158` 代码跑必失败） |
| G-REL-09 | 产出证书身份 | A/B | `apksigner verify --print-certs` | cert sha256 == 发布指纹；DN 非 `R04 Local`/`CI Dry Run`/`Android Debug` | **FAIL**：DN=`CN=R04 Local` |
| G-REL-10 | 签名方案底线 | A | `apksigner verify --verbose` | v2=true | PASS（v2 true；v3 false 需在说明里写明） |
| G-REL-11 | keystore 可打开 | B | `keytool -list -keystore … -alias …` | exit 0 且 `PrivateKeyEntry` | NOT_RUN（门本身尚未存在） |
| G-REL-12 | 16KB ZIP 层 | A | `zipalign -c -P 16 4 <apk>` | exit 0，两 flavor | PASS（两包 exit 0） |
| G-REL-13 | 16KB ELF 层 | A | §12 脚本；含 32 位负控 | 全部 PASS；负控 FAIL | PASS（14/14；负控本轮未跑） |
| G-REL-14 | 仅 64 位 ABI | A | `unzip -lv <apk> \| grep '\.so$'` | 无 `armeabi-v7a`/`x86` | PASS（7+7，均 64 位） |
| G-REL-15 | 16KB 运行时（x86_64） | B | §4.3 | `getconf`=16384 + 冷启不崩 + quickjs 路径被触发 | NOT_RUN（在跑设备全 4096） |
| G-REL-16 | 16KB 运行时（arm64） | **D** | §9 runbook | 同上 | BLOCKED |
| G-REL-17 | AAB 页对齐 | C | `bundletool-all` `dump config \| grep alignment` | `PAGE_ALIGNMENT_16K` | NOT_RUN（本地无 fat jar） |
| G-REL-18 | 依赖钉版 | A | `check_dependency_pins.py`（+ 负控） | exit 0；负控 exit 1 | PASS（exit 0；负控未跑） |
| G-REL-19 | 锁文件覆盖 | A | `check_lockfiles.py` + `git ls-files '*gradle.lockfile'` | 每 include 有已跟踪锁、配置齐 | **放行但未锁**：0 tracked / 13 untracked / `:benchmark` 缺锁（KD-72 open） |
| G-REL-20 | wrapper 完整性 | A | 比对仓内 pin 与 `services.gradle.org` 官方 sha256 | 逐字符相等 | PASS（`9c0f7fae…` 双侧一致） |
| G-REL-21 | release lintVital | A | 读 `lint_vital_return_value/.../return-value-*.txt` 与文本报告 | 值 `0` 且报告 `No issues found.` | PASS（两 flavor 均 0） |
| G-REL-22 | release manifest | A | `check_release_manifest.py` | exit 0 | PASS（9 checked + 4 debug，5 个 debug-only 组件双向一致） |
| G-REL-23 | 安装冒烟（4KB） | B | §7.2 | 判据表全绿 + 设备侧 sha 一致 | NOT_RUN |
| G-REL-24 | 安装冒烟（16KB） | B | §7.2 + ps16k | 同上 + `getconf`=16384 | NOT_RUN |

**与 `RELEASE.md` 现有 7 条门的映射**：门 1（assemble/单测/lint）→ G-REL-01/21；门 2（assembleRelease/bundle + R8）→ G-REL-01/03/04；门 3（merged release manifest）→ G-REL-22（已有脚本）；门 4（Room 迁移矩阵）与门 5（备份往返）**不属本文范围**（WS10）；门 6（无密钥/用户内容泄漏）→ 建议补成"扫 APK/AAB/backup 归档"，当前无脚本（本文不展开）；门 7（版本号）→ G-REL-00 + `output-metadata.json` 比对。

---

## 9. 需用户提供（C）与外部阻塞 runbook（D）

### 9.1 C 类清单（发版前必须由发布者提供）

1. **生产 keystore**（仓外）＋四个 `RELEASE_*` 变量。没有它们，G-REL-08/09 永久 FAIL，任何 release APK 都只是"本地测试签名包"。
2. **Play App Signing 决策**（上传密钥 vs 托管），决定 G-REL-09 期望的证书指纹。
3. **`bundletool-all-<ver>.jar`**（官方 GitHub release），G-REL-17 的前置。
4. **arm64 + 16KB 的运行环境**：Pixel 8/8 Pro/8a（Android 15 QPR1+）、Pixel 9/9 Pro/9 Pro XL（QPR2+）、Pixel 9a（Android 16+）上的 `Boot with 16KB page size`，或 arm64 宿主 + Cuttlefish 16KB。
5. **版本号裁定**（versionCode 2 → ?，是否两个 flavor 同步）——机器只能校验"一致"，不能决定"该不该升"。

### 9.2 D 类 runbook：arm64 16KB 运行时（本机不可能）

```bash
# 前置：arm64 真机（上述 Pixel）或 arm64 宿主上的 Cuttlefish 16KB 镜像
adb devices                                    # 设备必须是 arm64
adb shell getprop ro.product.cpu.abi           # arm64-v8a（或 arm64）
adb shell getconf PAGE_SIZE                    # 必须 16384；否则先开 developer option 重启
adb install -r app-strictOffline-release.apk   # 期望 Success
adb shell am start -W -n com.tingyun.smartmistakebook.offline/com.tingyun.smartmistakebook.MainActivity
adb logcat -d | grep -Ei 'linker|RELRO|SIGSEGV|UnsatisfiedLinkError'   # 期望 0 命中
# 必须触发的路径：quickjs（libquickjs.so dlopen）、ML Kit OCR（libmlkit_google_ocr_pipeline.so）、
# LiteRT（libLiteRt.so）——只冷启动不触发，等于没测
```
**验收**：`getconf`=16384 且三条 native 路径各触发一次、四类 grep 全 0。**当前状态 BLOCKED**（无 arm64 设备/宿主），不得写成 PASS。

---

## 10. 来源与采纳/拒绝

| # | 来源 | 用途 | 判定 |
|---|---|---|---|
| S1 | `https://developer.android.google.cn/guide/practices/page-sizes`（官方镜像；主站 `developer.android.com` 本轮连接超时） | 16KB 政策截止日、`zipalign -P 16`、`llvm-objdump` 的 `2**14` 判据、RELRO 公式与独占-LOAD 豁免原文、`getconf PAGE_SIZE`、developer option 机型清单 | **采纳**（官方一手；同一路径的仓内历史记录见 `2026-11-03-16kb-page-testing.md:30`，判据原文与 `2026-10-25-16kb-arm64-api23-build.md:54-61` 一致） |
| S2 | `https://services.gradle.org/distributions/gradle-9.6.1-bin.zip.sha256`（本轮抓取） | wrapper 校验和双源核对 | **采纳**（官方；与仓内 pin 逐字符一致） |
| S3 | 仓内 `docs/research/2026-11-03-16kb-page-testing.md`、`2026-10-25-16kb-arm64-api23-build.md`、`2026-10-27-arm64-16kb-coverage.md` | 本机 AVD 现状、判据实现先例、arm64 不可用结论 | **采纳**（仓内一手 + 与 S1 一致） |
| S4 | 仓内 `docs/research/2026-10-26-r8-workmanager-room.md` | strict full mode 与 `<init>` 裁剪机制、`usage.txt` 读法 | **采纳**（"读 R8 输出而非读配置"的方法论） |
| S5 | `docs/research/evidence/2026-10-26-batch-24/step03-r8-release-smoke.md` | 安装冒烟的命令/判据先例、`validateReleaseSigning` exit=1 的原始日志 | **采纳** |
| S6 | 社区文章"16KB 截止日 2025-11-01" | 政策日期 | **拒绝**：与 S1 官方现行文本（2027-02-01）冲突，属旧口径（`2026-11-03-16kb-page-testing.md:34-38` 已同判） |
| S7 | 本机缓存 `bundletool-1.18.3.jar` | AAB 对齐检查 | **拒绝作为工具**：实测非可执行 fat jar（两次失败输出见 §4.4）；需改用官方 `bundletool-all` |
| S8 | 本文第一版 ELF 判据（把"RELRO 独占 LOAD"实现成"不被任何 LOAD 包含"） | 16KB ELF 判定 | **拒绝**：5 个合规库被误判 FAIL；已按 S1 原文机制重写并复跑（§4.2 附更正说明） |

---

## 11. 未验证清单（UNVERIFIED，不得当作通过）

1. **任何 Gradle 命令**（`validateReleaseSigning`、`assembleRelease`、`bundleRelease`、`dependencies`、`clean` 冷构建）——本轮未跑，纪律要求门禁阶段串行。
2. **冷构建**：`--offline` 是否 exit 0、耗时、`actionable tasks` 计数、是否满足 ASCII 路径前置（本轮 `subst` 为空）。
3. **构建确定性**：两次 clean 构建的 APK 是否逐字节相同（未跑）。
4. **安装冒烟（4KB / 16KB）**：未安装、未冷启、未抓 logcat（设备被他线占用）。
5. **16KB 运行时**：三台在跑设备全 4096；ps16k AVD 未启动；arm64 BLOCKED。
6. **AAB 页对齐**：`bundletool-all` 未取得。
7. **retrace 实战**：项目与用户的 Gradle 缓存里都未见 `com.android.tools:r8` 条目（本轮只做了 `ls` 两处 cache 目录，非全盘 find），因此没有可直接跑的 retrace 工具。"mapping 里有行号"只能证明 mapping 文本含原始行区间（本轮实测 `androidx.core…:53:53` 形态），**不能**替代"用 retrace 把一条真实混淆栈还原成功"。
8. **`check_dependency_pins.py` 负控**（`--expect-kotlin-runtime 2.4.10` 应红）与 **ELF 负控**（32 位 ML Kit `.so` 应 FAIL）——本轮都未跑。
9. **`check_lockfiles.py` 在"有已跟踪锁"状态下的行为**——当前是放行分支。
10. **核心三链路冒烟的定义**（导出/复习/OCR 各一条的最小判据）：本文只沿用"启动不崩"，计划 WS8-62 要求的"核心三链路"尚未定义，属实施项缺口。
11. **签名侧 v3/v4 策略**（是否启用 key rotation / v4）——当前全 false，是否可接受属发布决策。
12. **`mappingFileUploadEnabled` 对 Play 上传的实际效果**——未配置也未验证。

---

## 12. 附：ELF 16KB 检查脚本（判据同 S1，供门禁复用）

> 本机无 NDK（`llvm-objdump`/`llvm-readelf` 不可用），本脚本按官方同一判据解析 ELF 程序头；等价性先例见 `docs/research/evidence/2026-10-26-batch-23/step09-elf-llvm-readobj.md:107` 的裁定。建议实施项把它落到 `tools/ci/check_16kb_alignment.py`（本文不新增该文件，避免与 WS8 实施项重合）。本轮运行副本位于临时目录（`…/Temp/r6elf/check_elf_align2.py`），不随仓入库。

```python
"""16KB ELF check: every PT_LOAD p_align >= 0x4000; GNU_RELRO end 16KB-aligned
OR no writable LOAD starts inside the remainder page (official own-LOAD exemption).
Usage: python -I check_elf_align.py <dir>   # 递归找 *.so；exit 0=全 PASS, 1=有 FAIL
"""
import glob, os, struct, sys
PT_LOAD, PT_GNU_RELRO, PF_W, PAGE = 1, 0x6474E552, 0x2, 0x4000

def segs(path):
    d = open(path, 'rb').read()
    assert d[:4] == b'\x7fELF', path
    is64, e = d[4] == 2, ('<' if d[5] == 1 else '>')
    if is64:
        phoff = struct.unpack_from(e+'Q', d, 0x20)[0]
        phes, phnum = struct.unpack_from(e+'H', d, 0x36)[0], struct.unpack_from(e+'H', d, 0x38)[0]
    else:
        phoff = struct.unpack_from(e+'I', d, 0x1c)[0]
        phes, phnum = struct.unpack_from(e+'H', d, 0x2a)[0], struct.unpack_from(e+'H', d, 0x2c)[0]
    out = []
    for i in range(phnum):
        o = phoff + i*phes
        if is64:
            t, f = struct.unpack_from(e+'II', d, o)
            off, va, _pa, _fs, ms, al = struct.unpack_from(e+'QQQQQQ', d, o+8)
        else:
            t, off, va, _pa, _fs, ms, f, al = struct.unpack_from(e+'IIIIIIII', d, o)
        out.append(dict(t=t, off=off, va=va, ms=ms, f=f, al=al))
    return out

ok_all = True
for p in sorted(glob.glob(sys.argv[1] + '/**/*.so', recursive=True)):
    s = segs(p); loads = [x for x in s if x['t'] == PT_LOAD]
    ok = True; notes = []
    for i, x in enumerate(loads):
        if x['al'] < PAGE or (x['al'] and x['off'] % x['al'] != x['va'] % x['al']):
            ok = False; notes.append(f"LOAD#{i} p_align={x['al']}")
    for r in [x for x in s if x['t'] == PT_GNU_RELRO]:
        end = r['va'] + r['ms']; rem = end % PAGE
        if rem == 0:
            notes.append('RELRO mod=0'); continue
        clash = [hex(x['va']) for x in loads
                 if (x['f'] & PF_W) and end <= x['va'] < end + (PAGE - rem)]
        if clash:
            ok = False; notes.append(f"RELRO mod={rem} writable-in-page {clash}")
        else:
            notes.append(f"RELRO mod={rem} own-LOAD(exempt)")
    ok_all &= ok
    print(('PASS ' if ok else 'FAIL ') + os.path.relpath(p, sys.argv[1]) + ' | ' + '; '.join(notes))
print('ALL_PASS' if ok_all else 'HAS_FAIL')
sys.exit(0 if ok_all else 1)
```

**负控样本（必须红）**：`core/data` 依赖里 ML Kit 的 32 位 `.so`（`armeabi-v7a` 3 个 LOAD `p_align=4096`；`x86` 4 个 LOAD `p_align=4096` 且 RELRO 后同页有写段），来源 `docs/research/2026-10-25-16kb-arm64-api23-build.md:117`。当前 `abiFilters` 已把它们挡在 APK 之外，所以负控要直接从 Gradle 缓存里的 AAR 取样本，不能从 APK 取。

---

## 13. 交接给实施项（WS8 61–70）的最小动作

| 计划项 | 本文提供的输入 | 仍需实施的事 |
|---|---|---|
| 61 assembleRelease + mapping 产出与 checksum | §2.2 命令、§2.4 sidecar 字段、§1.2 基线 sha | 在门禁阶段跑一次；落 `release-artifacts.json` 生成脚本 |
| 62 release APK 冒烟 | §7.2 命令与判据表 | 定义"核心三链路"最小判据并执行；设备独占 |
| 63 16KB 页大小 | §4 全套判据 + 本机结论（静态全绿、运行时未跑） | ps16k_x86_64 实跑；arm64 标 BLOCKED + runbook 入档 |
| 64 签名配置清单 | §3.1 四个变量名、§3.2 G-REL-08/09/11 | 把 G-REL-11 补成 task；收集生产凭据 |
| 65 mapping 保留策略与版本绑定 | §2.4、§2.5 | sidecar + 归档位置落地；Play 上传方式二选一 |
| 66 依赖钉版门复跑 | §5.1 exit 0 证据 | 门禁阶段复跑 + 负控 |
| 67 KD-72 锁文件窗口 | §5.2（0 tracked / 13 untracked / `:benchmark` 缺锁） | 干净树上 `lockAllDependencies --write-locks`，单独提交 |
| 68 供应链 license/更新度快照 | §5.4 命令 | 生成快照并与上一版 diff |
| 69 R-04 Keeper 可执行性 | （不属本文；见批次 41 R8 研究） | 独立研究线 |
| 70 发版门清单落盘 | 本文 §8（25 条 G-REL-*） | 落 `tools/ci/release_gates` 或 `docs/current` 草案（后者热区，需主会话裁） |

---

## 14. 变更记录

- 2026-11-04 初稿：R6 研究线落盘。所有"A 类现状"为本轮实跑；B/C/D 类只给命令与判据，未执行。§4.2 记录了一次判据自我更正（RELRO 豁免写反 → 5 个合规库误报 FAIL）。
