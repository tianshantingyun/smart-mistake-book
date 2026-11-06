# R4 干净树发布构建：`git archive` 副本 + 离线 Gradle（批次 43）

> **回答的问题**（计划 `§1 R4`）：在共享脏工作树的前提下，能否用 `git archive` 把 HEAD 导到临时目录，再用本机 Gradle 缓存做一次 `--offline` 的 release 形态构建？依赖缓存在哪、为什么会失败、哪一步必须先补。
>
> **本轮状态口径**：本文件中的"实测"= 本轮亲手运行的命令（命令与输出原样给出）；"读码/读档"= 本轮读到的仓内文件行；**本轮没有运行任何 Gradle 任务**（共享工作树纪律：Gradle 只在门禁阶段串行运行），因此"离线构建 exit=0"是 **NOT_RUN**，不是 PASS。文末 §8 列全部未验证项。

---

## 0. 结论

| 问题 | 判定 | 依据 |
|---|---|---|
| `git archive HEAD` 能否在脏树上导出干净副本 | **可行，已实测** | 导出 450 MiB / 3397 文件，与 `git ls-files` 计数逐位相等；耗时 1.9 s；不写工作树、不写 `.git`（§2.2） |
| 副本里的依赖能否离线解析 | **高置信、未证明** | 340 个坐标的 metadata descriptor **340/340 命中**，artifact **329/340**（11 个是 BOM/重定向件）；门禁日志 0 次 download（§3.3、§3.4） |
| 私有缓存能否直接"清空重来" | **不能** | `~/.gradle` 无 `wrapper/dists`，Gradle 默认 home 会先去下载发行版（§5-F1） |
| release APK 能否产出 | **BLOCKED（签名）** | `assembleStrictOfflineRelease` 依赖 `validateReleaseSigning`，四个环境变量全缺即抛（`app/build.gradle.kts:144-166`）；`keystore.properties` 不存在且代码根本不读它（§5-F9/F10） |
| 与"隔离 worktree 无法离线构建"是否同一根因 | **同一根因：缓存来源，不是隔离机制** | 2026-09-12 记录原文：默认 `~/.gradle` 缺 `com.halilibo.compose-richtext` 与 `androidx.profileinstaller:1.4.0` 的描述符（§6） |

**一句话**：做法成立，但"临时目录 + 空 `GRADLE_USER_HOME`"必失败；**必须先把 `<树>/.gradle` 的 `caches/modules-2` 与 `wrapper/dists` 播种到私有 home**，再用 `gradlew.bat`（不是 `./gradlew`）跑 `--offline`。release 那一步在拿到四个签名环境变量前只能记 BLOCKED，用 `assembleStrictOfflineBenchmark`（R8 + shrink，debug 签名）作等价管线证据。

---

## 1. 前提事实（本轮读数）

| 事实 | 值 | 命令 / 出处 |
|---|---|---|
| HEAD | `0fef9436…`，2026-11-03 20:08:52 +0800，分支 `main` | `git rev-parse HEAD`、`git log -1 --format="%H %ci"` |
| 脏树规模 | **512** 条变更、**423** 条未跟踪条目 | `git status --porcelain \| wc -l` = 512；`grep -c "^??"` = 423 |
| 跟踪文件 | 3397 个、446.4 MiB | `git ls-files \| wc -l`；`git ls-tree -r -l HEAD \| awk '{s+=$4}…'` |
| 归档包 | 450 MiB | `ls -lh /d/smb-arc-probe/head.tar` |
| 无子模块、无 LFS、无本地 maven 仓 | `.gitmodules` 不存在；`git lfs ls-files` 空；`grep mavenLocal/flatDir/url=uri --include=*.gradle.kts` 零命中 | 三条命令本轮实跑 |
| 最长跟踪路径 | 213 字符（`tools/kb_build/tables/boundary_fix_evidence/…`） | `git ls-tree -r --name-only HEAD \| awk '{print length($0),$0}' \| sort -rn \| head` |
| 仓库根不在 ASCII 路径 | `D:\smart mistake book`（含空格） | `pwd` |
| `subst` 当前为空 | 无 `S:` 映射 | `subst` 输出空 |

`git archive` 不写工作树：它只读 commit 对象，1.9 s 完成，**不会给共享树的 423 条未跟踪文件增加任何风险**。

---

## 2. 实测：归档里有什么、缺什么

### 2.1 命令

```bash
git archive --format=tar HEAD -o /d/smb-arc-probe/head.tar   # real 0m1.933s
mkdir -p /d/smb-arc-probe/tree && tar -xf head.tar -C tree   # real 0m2.224s
find tree -type f | wc -l                                     # 3397（= git ls-files）
```

### 2.2 内容核对（逐项 `test -e`）

| 路径 | 归档内 | 说明 |
|---|---|---|
| `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar` / `gradle-wrapper.properties` | PRESENT | wrapper 齐全，`distributionSha256Sum` 与官方一致（另见批次 42 `release-readiness §5.3`） |
| `settings.gradle.kts` / `build.gradle.kts` / `gradle.properties` / `gradle/libs.versions.toml` | PRESENT | |
| 14 个模块目录（`app`、`core/*`、`feature/*`、`benchmark`） | 14/14 OK | 逐个 `test -d` |
| `local.properties` | **ABSENT** | 被 `.gitignore:14` 忽略；实测其内容**只有** `sdk.dir` 一个键（无凭据） |
| `keystore.properties` | **ABSENT** | `.gitignore:20` |
| `.gradle/`（缓存、wrapper 发行版） | **ABSENT** | `.gitignore:5` |
| `.toolchains/` | 主树本就不存在 | `test -d .toolchains` → ABSENT |
| `.git/` | 不存在（by design） | 影响见 F11 |

### 2.3 行尾：`git archive` 在 Windows 上会改写无显式 `eol` 的文件

`.gitattributes:1` 是 `* text=auto`，`:9` 是 `*.bat text eol=crlf`；本机 `core.autocrlf=true`。实测三种导出：

| 导出方式 | `gradlew` | `gradlew.bat` | 证据 |
|---|---|---|---|
| 默认 `git archive` | **CRLF（249 个 `\r`）** | CRLF | `tr -d -c '\r' < tree/gradlew \| wc -c` = 249 |
| `-c core.autocrlf=false` | 仍 CRLF | CRLF | 同上，未变化 |
| **`-c core.eol=lf -c core.autocrlf=false`** | **LF（0 个 `\r`）** | CRLF（属性显式保留） | `od -c eolcheck/gradlew` 首行 `# ! / b i n / s h \n` |

后果：默认导出后 `./gradlew` 在 Git Bash 下会报 `/bin/sh^M: bad interpreter`（F4）。**推荐一律加 `-c core.eol=lf -c core.autocrlf=false`**。

字节冻结件本轮已核对未被破坏：

- `tools/kb_coverage/tables/golden_queries_v1.json` 在工作树、默认归档、LF 归档三处的 sha256 **都是** `7c004b76…ae39`，与 sidecar `golden_queries_v1.json.sha256` 声明一致（`.json` 有显式 `eol=lf`，`.gitattributes:6`）；
- `tools/backup_fixtures/v4/closure4-schema72-mask15.smbk` 二进制不变（`24941ed731f8b554…` 两边相同）；
- `.sha256` 侧车文件（扩展名未在 `.gitattributes` 里）在默认归档里多一个 `\r`、在 LF 归档里没有；工作树当前那份本身带 `\r`。测试读的是十六进制串，本轮**未验证**它是否按字节比（§8-U3）。

### 2.4 构建期会写回源码树

`core/data/build.gradle.kts:58-77` 的 `syncGoldenQueryAssets`、`:102-112` 的 `syncV4PackFixtures` 会把 `tools/` 下的权威件 `Copy` 进 `core/data/src/androidTest/assets/`。**这正好是"必须在副本里构建"的理由**：在共享树跑这些任务会往受版本控制的路径写文件（内容幂等，但仍是写）；在一次性副本里写掉即弃。

---

## 3. 依赖缓存位置（问题的核心）

### 3.1 三处，用途不同

| 位置 | 内容 | 本轮实测 |
|---|---|---|
| **`<仓>/.gradle`** —— 被 `tools/android-env.ps1:197` 钉为 `GRADLE_USER_HOME`，是**本仓真正在用的**缓存 | `caches/modules-2`（工件+描述符）、`wrapper/dists`（Gradle 发行版）、`caches/9.6.1`（transforms、kotlin-dsl、fileHashes 等派生） | **7.61 GB / 124,141 文件**；`caches/modules-2` **0.67 GB**；`wrapper/dists` **151 MB**（`gradle-9.6.1-bin/6y99habf…/gradle-9.6.1/bin/gradle.bat` 存在）；`caches/9.6.1` **5.94 GB**；`modules-2` 最后写入 **2026-11-03 19:47** |
| `C:\Users\听云\.gradle` —— Gradle 默认 home | `android`/`caches`/`daemon`/`kotlin-profile`/`native`/`notifications`/`gradle.properties`，**没有 `wrapper/`** | **2.0 GB**；`modules-2` 127 个 group；最后写入 **2026-09-22 08:12** |
| `C:\Android\Sdk` —— Android SDK | platforms `android-23/34/37.0`、build-tools `34.0.0/36.0.0/37.0.0` | `ls` 实读；`local.properties` 的 `sdk.dir` 指向它；环境里也有 `ANDROID_HOME=C:\Android\Sdk` |

**结论**：`<仓>/.gradle` 是唯一"当下有效"的缓存（今天 19:47 还在写）；`~/.gradle` 自 2026-09-22 起无人写入，**且缺发行版**。任何"清空 home 重来"的做法都会立刻掉进 F1。

### 3.2 派生缓存是"可再生"的，不该跟着搬

`caches/9.6.1`（5.94 GB）里是 transforms、Kotlin DSL 编译产物、fileHashes——都能从 `modules-2` 离线重算（不联网）。所以"最小离线种子" = `caches/modules-2`（0.67 GB）+ `wrapper/dists`（151 MB）≈ **0.82 GB**，而不是整份 7.61 GB。

### 3.3 覆盖率探针（静态盘点，不是解析证明）

用批次 42 的依赖清单 `build/ws8-batch42/dependency-licenses.tsv`（340 个 `group:artifact:version`，来自上次真实解析）逐条比对缓存：

```
$ python -I check_cache3.py
total 340
no descriptor dir: 0
no artifact files: 11
   androidx.compose:compose-bom:2026.06.00
   org.jetbrains.compose.foundation:foundation:1.4.1
   org.jetbrains.compose.material3:material3:1.4.1
   org.jetbrains.compose.runtime:runtime:1.9.0
   org.jetbrains.compose.ui:ui-util:1.4.1
   org.jetbrains.kotlin:kotlin-stdlib-common:2.3.21
   org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.11.0
   org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.9.0
   org.jetbrains.kotlinx:kotlinx-serialization-bom:1.7.3
   org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1
   org.jetbrains.kotlinx:kotlinx-serialization-bom:1.9.0
```

- 匹配规则是"变体感知"的：`androidx.compose.runtime:runtime` 实际以 `runtime-android` 落盘（KMP 变体），首版按字面比对得到的 86 个"缺失"是**假阴性**，已修正。
- **描述符 340/340**：离线解析的元数据前提全部满足。
- 11 个无 artifact 文件：6 个是 BOM（本来就无 jar）、4 个是 Compose Multiplatform 的重定向件、1 个是 `kotlin-stdlib-common`（2.x 起为元数据件）。**它们是否仍需真实文件，只有 `--offline` 实跑能定**——本轮未跑，不作结论。
- 同类"标记件缺文件、实现件在"的模式已核对：`androidx.baselineprofile` 的 marker POM 不在 `files-2.1`，但实现件 `androidx.benchmark:benchmark-baseline-profile-gradle-plugin:1.5.0-rc01` 的 jar **在**；KSP 的 marker 同理，实现件 `symbol-processing-gradle-plugin` **在**。AAPT2（`com.android.tools.build:aapt2:9.3.0-15703166`）、`desugar_jdk_libs`、`richtext-commonmark-android:0.17.0`、`room3-compiler`、`kotlin-compiler-embeddable:2.3.21` 均在。

### 3.4 旁证

`build/main-session-gate-batch42-strong.log` 与 `…-unit3.log`：`grep -ci download` = **0 / 0**。即昨天两次门禁解析时没有任何下载动作，与"`<仓>/.gradle` 已自足"一致（但增量构建不是 `--offline` 证明）。

---

## 4. 可行方案

### 4.1 推荐：私有 `GRADLE_USER_HOME`（种子 0.82 GB）

隔离度最高：不写共享树的 `.gradle`、不与主树构建抢锁、离线证据只依赖自己那份 home。

```bash
# 0) 变量
REV=$(git rev-parse HEAD); OUT="/d/smb-rel-${REV:0:8}"
mkdir -p "$OUT/src" "$OUT/gradle-home/caches"

# 1) 干净导出（只读 .git；LF 保真）
git -c core.eol=lf -c core.autocrlf=false archive --format=tar HEAD -o "$OUT/tree.tar"
tar -xf "$OUT/tree.tar" -C "$OUT/src"

# 2) 机器本地文件（实测仅含 sdk.dir，无凭据）
cp local.properties "$OUT/src/local.properties"

# 3) 播种私有 home：工件 + 发行版（不搬 5.94 GB 派生缓存）
cp -r .gradle/caches/modules-2 "$OUT/gradle-home/caches/"
cp -r .gradle/wrapper          "$OUT/gradle-home/"

# 4) 离线构建（用 gradlew.bat，理由见 F4）
cd "$OUT/src"
GRADLE_USER_HOME="$OUT/gradle-home" \
ANDROID_HOME="C:/Android/Sdk" \
  ./gradlew.bat --offline --no-daemon --no-build-cache --console=plain \
  :app:assembleStrictOfflineBenchmark
```

判据：`exit 0`、`BUILD SUCCESSFUL`、输出里**没有** `Download`、`Could not resolve`、`No cached version`；`compile*`/`minify*` 任务标 `executed` 而非 `UP-TO-DATE`（首次必然 executed）。

三条操作注意（都是"照抄命令会踩"的点）：

1. **复制缓存必须在没有 Gradle 运行时做**：第 3 步是在拷一个"活"目录（`modules-2.lock`、`journal-1` 都在写窗口内），并发构建可能拷到半写状态。纪律本就要求串行，但要在记录里写明复制时刻。文件多、`cp -r` 慢时可换 `robocopy /MIR`（未实测耗时）。
2. **debug keystore 的来源变了**：工作区入口会用 `-Duser.home=<树>` 把 JVM home 钉在仓内（`tools/android-env.ps1:210`、`run-gradle.ps1:74`）；直调 `gradlew.bat` 绕过了它，AGP 会落到 `C:\Users\听云\.android\debug.keystore`。该文件**存在**（本轮 `ls -l` 2618 字节），所以 debug 签名不受影响——但这是一处与"正规入口"不同的行为，需在门禁记录里注明。
3. `gradlew.bat` 在 Git Bash 里可直接 `./gradlew.bat` 调用；若坚持用 `./gradlew`，必须走 §2.3 的 `core.eol=lf` 导出。

> `--no-build-cache` 是显式冗余：`gradle.properties` 没开 `org.gradle.caching`（`:1-12` 无此项），默认即为关闭。写出来是为了让"缓存命中掩盖回归"不可能发生。

### 4.2 备选 A：直接复用 `<仓>/.gradle` 作 home

```bash
GRADLE_USER_HOME="D:/smart mistake book/.gradle" ./gradlew.bat --offline --no-daemon …
```

省 0.82 GB 拷贝、首次构建更快（transforms 命中），**代价**：给共享缓存写新条目（日志、fileHashes、journal），且与任何并发构建争同一把锁。纪律上要求 Gradle 串行，所以可接受，但证据不再"只来自副本"。

### 4.3 备选 C：整份搬 `.gradle`（7.61 GB）

只有"逐任务复现昨天的耗时/命中率"时才需要；对"能不能离线构建"没有额外信息量。

### 4.4 release 那一步：先分流

| 目标 | 条件 | 命令 |
|---|---|---|
| **管线等价证据**（推荐先跑） | 无密钥也可 | `:app:assembleStrictOfflineBenchmark`（`app/build.gradle.kts:68-78`：`isMinifyEnabled=true`、shrink、debug 签名、**任务名不含 `Release`** 故不触发签名门） |
| 真 release | 必须有 4 个环境变量 | `:app:assembleStrictOfflineRelease` / `:app:bundleStrictOfflineRelease` |
| 无密钥时跑 release | 会失败，且是**预期失败** | 在 `validateReleaseSigning` 处抛 `Release signing is missing required environment variables: …`（`:152-156`） |

⚠️ 不要在无密钥时把这次失败记成"干净树构建失败"——它是签名门在起作用。建议把两者分两行记：`G-REL-08 NOT_RUN(0/4)` 与 `clean-tree pipeline PASS`。

---

## 5. 失败模式（每条都能指到证据）

| # | 现象 | 根因 | 证据（本轮/仓内） | 规避 |
|---|---|---|---|---|
| **F1** | `gradlew` 在离线环境下卡在 `Downloading https://services.gradle.org/…` | 空 `GRADLE_USER_HOME` 没有发行版；wrapper 先于 Gradle 启动，**Gradle 的 `--offline` 管不到它** | `ls ~/.gradle/wrapper/dists` → No such file（exit 2）；官方 wrapper 文档："if not already in `GRADLE_USER_HOME`, the Wrapper will download it" | 播种 `wrapper/dists`（§4.1 第 3 步） |
| **F2** | `No cached version of X available for offline mode`（联网解析同样失败） | home 里缺**描述符**（不只是缺 jar） | `docs/audit-2026-09-12-kernel-readiness.md:4951-4955` 原文 | 用 `<仓>/.gradle`（340/340 描述符）做种子，别用 `~/.gradle` |
| **F3** | `Remote host terminated the handshake` / `TLSv1.2, TLSv1.3` | 某次常驻 daemon 自身异常（同 JDK 裸 JVM 访问同址 200） | 同上 `:4956-4962`（记录明写"成因未查明，只确证换新 JVM 就好"） | `--no-daemon` |
| **F4** | `./gradlew: /bin/sh^M: bad interpreter` | 默认导出把 `gradlew` 写成 CRLF | 本轮实测：默认归档 `\r`×249；`-c core.eol=lf` 后 0 | 加 `-c core.eol=lf`，或改用 `gradlew.bat` |
| **F5** | `SDK location not found` | 归档无 `local.properties` | 归档 `local.properties` ABSENT；其内容仅 `sdk.dir=` | 拷贝 `local.properties`，或导出 `ANDROID_HOME`（本机已有 `C:\Android\Sdk`） |
| **F6** | Kotlin worker / 编辑器路径异常、缓存串味 | 非 ASCII 或含空格路径 | `gradle.properties:10-11` 注释；`DEVELOPMENT.md:47-57`；`build/main-session-gate-batch42-strong.log:4` 的 `WARNING: The option setting 'android.overridePathCheck=true' is experimental`——该开关**只**跳过 AGP 检查，不修工具链 | 副本放 `D:\smb-rel-<rev>`（ASCII、无空格） |
| **F7** | `S: is already in use and does not point to this workspace` | `subst S:` 机器全局、单占用 | `tools/android-env.ps1:130-183` | 副本构建**不要**走 `run-gradle.ps1`/`android-env.ps1`，直接 `gradlew.bat` + 显式环境变量 |
| **F8** | `run-gradle.ps1` 直接报找不到 gradle | 它固定用 `<树>/.toolchains/gradle/gradle-9.6.1/bin/gradle.bat` | `tools/android-env.ps1:187`；`test -d .toolchains` → **ABSENT**（本机现状，官方入口已不可用） | 同上：直接调 wrapper |
| **F9** | `assemble…Release` 在打包前抛签名缺失 | `tasks.configureEach` 把 `validateReleaseSigning` 挂到任何名字含 `Release` 的 assemble/bundle/package | `app/build.gradle.kts:144-166` | 有密钥才跑；否则跑 `…Benchmark` |
| **F10** | 按 `RELEASE.md` 配 `keystore.properties` 仍然签名失败 | 文档与代码不一致：代码只读 4 个**环境变量**，全仓无一处读 `keystore.properties` | `RELEASE.md:20-21` vs `app/build.gradle.kts:124-142`；`grep -rn keystore.properties --include=*.kts/kt` 仅命中 RELEASE.md | 按环境变量走；把文档缺陷登记（本轮只记录，不改文件） |
| **F11** | 副本里 `git` 相关门失败 / 产物无 VCS 信息 | 归档无 `.git` | `tools/ci/check_file_size_gate.py:54`（`cwd=REPO` 调 git）、`check_lockfiles.py:144`；APK 的 `META-INF/version-control-info.textproto` 依赖 .git | git 类门在**主树 HEAD** 上跑；产物身份改用 sidecar（HEAD sha + tar sha + APK sha） |
| **F12** | 构建把 `src/androidTest/assets/` 写脏 | `syncGoldenQueryAssets`/`syncV4PackFixtures` 写回源码树 | `core/data/build.gradle.kts:76-77, 111-112` | 在副本里构建（这也是选副本的理由之一） |
| **F13** | `Timeout waiting to lock …`（若并发） | 共用 `GRADLE_USER_HOME` 的跨进程锁 | 机制常识；本仓纪律本就要求 Gradle 串行 | 用 §4.1 私有 home，或确保串行 |
| **F14** | 拿副本 APK 当"当前工作树"的证据 | 归档 = HEAD，脏树 512/423 条不在内 | §1 实测 | 设备绿必须重绑：HEAD sha + tar sha256 + APK sha256 三联 |

**消灭的具体失败（新增这些步骤各自的靶子）**：`-c core.eol=lf` → F4 的 CRLF 解释器错误；播种 `wrapper/dists` → F1 的离线先联网；播种 `caches/modules-2` → F2 的描述符缺失；`gradlew.bat` 直调 → F7/F8 的 SUBST 与 `.toolchains` 断链；`assemble…Benchmark` 分支 → F9 把"签名门"误读成"构建坏"。

---

## 6. 与"隔离 worktree 曾无法离线构建"的对照

历史记录（`docs/audit-2026-09-12-kernel-readiness.md:4930-4972`）的三个事实：

1. 正规入口 `tools/run-gradle.ps1` **当时就跑不动**——它要 `.toolchains/`，而 `.toolchains/` 在主树与 worktree 都不存在（`:4939-4940`）。
2. 替身跑法必须 `GRADLE_USER_HOME=<worktree>/.gradle`，因为默认 `~/.gradle` **没有** `com.halilibo.compose-richtext`（`core:ui` 依赖）和 `androidx.profileinstaller:1.4.0` 的描述符 → `No cached version … available for offline mode`（`:4951-4955`）。
3. 必须 `--no-daemon`，否则 daemon 侧 TLS 握手失败（`:4956-4962`）。

本轮对照结论：

- **根因是缓存来源，不是"worktree 这种机制"**。那一轮 worktree 自己的 `.gradle` 只有约 1 GB（`:4945`），装不下 `:app:*` 的全套描述符；今天 `<仓>/.gradle` 有 340/340 描述符 + 0.67 GB 工件，条件已经不同。
- **但"新建一个空 home"依然必败**：F1（无发行版）+ F2（无描述符）两条独立生效。所以 §4 的第 3 步不是可选优化，是前置条件。
- F7/F8 今天仍然成立（`.toolchains` 依旧不存在、`subst` 依旧单占用），所以"用 `run-gradle.ps1` 在第二棵树构建"这条路**今天依旧走不通**；能走通的只有"直接调 wrapper + 显式 `GRADLE_USER_HOME`"。
- 与 worktree 相比，`git archive` 还多两个差异：**没有 `.git`**（F11：git 类门与产物 VCS 信息）与 **不共享索引/引用**（对共享脏树零影响）。worktree 有 `.git` 文件但仍共享对象库，`git archive` 连这个都不写。

---

## 7. 准入条件（什么情况下才能把这次构建当发版证据）

必须同时满足，缺一条就只能记"尝试过"：

1. `git rev-parse HEAD` 与归档来源一致，且 `git status --porcelain` 的 512/423 条**明确排除**在产物之外（写进记录）；
2. 归档包 sha256 与 HEAD sha 一起写进 sidecar（`tar` 由 commit 决定，mtime 固定为提交时间——`git archive` 文档原文，故 tar 本身可复算）；
3. `--offline` `exit 0`，且日志中 `Download`/`No cached version`/`Could not resolve` 均为 0；
4. APK sha256 落 sidecar，并与设备侧 `pm path` + `sha256sum` 一致（沿用批次 24 E-03 约定）；
5. release 形态另需：四个签名环境变量齐备（否则该项记 BLOCKED，不得用 benchmark 变体冒充 release）。

---

## 8. 未验证 / 阻塞（不得写通过）

| ID | 项 | 状态 | 下一步（门禁阶段） |
|---|---|---|---|
| U1 | 副本内 `--offline` 构建是否 exit 0 | **NOT_RUN**（本轮按纪律未跑 Gradle） | §4.1 第 4 步，日志存 `build/clean-tree-<rev>.log` |
| U2 | 11 个"无 artifact 文件"的坐标是否仍能离线解析 | **NOT_RUN** | 同上；失败时错误行会直接点名坐标 |
| U3 | `.sha256` 侧车多出的 `\r` 是否触发任何按字节比较的断言 | **未验证**（未逐条确认测试是比较解析后的十六进制串，还是比较原文件字节） | 跑 `:core:data:testDebugUnitTest --tests *GoldenRetrievalJvmTest*`、`*V4PackFixtureTest*` |
| U4 | 归档无 `.git` 时 `version-control-info.textproto` 的实际内容 | **未验证** | `unzip -p <apk> META-INF/version-control-info.textproto` |
| U5 | `assembleStrictOfflineBenchmark` 任务名是否确实存在、是否不触发签名门 | **未运行验证**；由 `buildTypes.create("benchmark")`（`app/build.gradle.kts:68`）与 `:161-166` 的作用域**推断** | `gradlew.bat :app:tasks --all \| grep assembleStrictOffline` |
| U6 | 首次全量编译耗时（副本无 build 缓存） | **NOT_RUN** | 与 §7 同批记录，只作趋势 |
| B1 | release APK | **BLOCKED**：四个签名变量 0/4，`keystore.properties` 不存在 | 用户提供 `RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD`/`RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD` |
| B2 | arm64 真机、16 KB ps16k 出包复核 | 外部阻塞（沿用批次 42 结论） | 见 `2026-11-04-release-readiness.md §4/§12` |

**来源可信度标注**：AGP"`local.properties` 缺 `sdk.dir` 时回退 `ANDROID_HOME`"这条**只有二手来源**（AGP 报错文案同时提到两者，见 §9 第 6 条），本文件按"待验证"处理，但 §4.1 直接拷贝 `local.properties`，使该条不影响方案成立。

---

## 9. 来源与采纳/拒绝理由

| # | 来源 | 采纳内容 | 判定 |
|---|---|---|---|
| 1 | https://docs.gradle.org/current/userguide/directory_layout.html | 用户目录布局：`caches/modules-2` 存依赖、`wrapper/dists` 存发行版、`caches/transforms-3`、`caches/build-cache-1` | **采纳**（官方，与本机目录逐一对应） |
| 2 | https://docs.gradle.org/current/userguide/build_environment.html | `GRADLE_USER_HOME` "Defaults to `USER_HOME/.gradle` if not set" | **采纳**；该页**未**讲 `--offline`，故不拿它支撑离线语义 |
| 3 | https://docs.gradle.org/current/userguide/command_line_interface.html | `--offline`："the build should operate without accessing network resources" | **采纳** |
| 4 | https://docs.gradle.org/current/userguide/gradle_wrapper.html | 发行版不在 `GRADLE_USER_HOME` 时 wrapper 会下载并存放；后续复用 | **采纳**（用于 F1）。该页未提 `--offline` 与 wrapper 的关系，故"wrapper 不受 `--offline` 约束"标为**机制推断**（wrapper 是独立 bootstrap，`--offline` 是 Gradle 的 CLI 参数），非文档直述 |
| 5 | https://developer.android.com/tools/variables | `ANDROID_HOME` 指向 SDK 安装目录；`ANDROID_SDK_ROOT` 已废弃且 AGP 会校验两者一致 | **采纳**（官方）。**未给出**与 `local.properties` 的优先级顺序 → 该顺序**不采纳为结论**（标待验证） |
| 6 | 二手：社区问答（`bing` 检索 "sdk.dir in the local.properties file or with an ANDROID_HOME environment variable"） | 仅作"AGP 报错同时点名两者"的旁证 | **单一来源，未采纳为判据** |
| 7 | https://git-scm.com/docs/git-archive | 归档来自 tree-ish（commit 树，不是工作目录）；`export-ignore` 生效；commit 归档的 mtime 用提交时间；非跟踪文件默认不含（需 `--add-file`） | **采纳**（官方） |
| 8 | 仓内：`docs/audit-2026-09-12-kernel-readiness.md:4930-4972` | worktree 离线失败的原始读数（缺描述符、daemon TLS、SUBST 单占用） | **采纳**（本仓一手记录） |

检索通道记录：`WebFetch` 访问 `developer.android.com` 两次 443 超时（10 s），改走 `mcp__searchd__extract_content` 成功——按纪律换通道，未把单通道失败当"来源不存在"。

---

## 10. 回链与落点

- 计划：`.zcode/plans/plan-2026-11-05-batch-43.md:20`（R4）、`:70-78`（WS5 items 37–43）。
- 前序：`docs/research/2026-11-04-release-readiness.md`（§6 冷构建命令、§8 门表 G-REL-01/02）；`docs/audit-2026-09-12-kernel-readiness.md` §13.4（本机 Gradle 环境台账）。
- 本文只落这一份文件；未改任何构建脚本、未改 `docs/current`、未做 git 写操作。本轮临时产物（`D:\smb-arc-probe\`，1.8 GB：3 份 tar + 2 份解包树 + 探针脚本）**已删除**，按 §4.1 可随时重建。
- 建议由 WS5 item 37 消费：按 §4.1 跑一次，把 U1/U2/U3/U5 的结果回填 §8；若 `assembleStrictOfflineBenchmark` 绿而 `Release` 因签名红，则 G-REL-01 可升为 PASS、G-REL-08 仍留 BLOCKED。

---

## 11. 附录：覆盖率探针（可复跑）

放在仓外任意目录，`python -I` 跑（避免 cwd 里的同名模块劫持 import）：

```python
import os

tsv = r"D:\smart mistake book\build\ws8-batch42\dependency-licenses.tsv"
root = r"D:\smart mistake book\.gradle\caches\modules-2"
art = os.path.join(root, "files-2.1")
desc = os.path.join(root, "metadata-2.107", "descriptors")

coords = []
with open(tsv, "r", encoding="utf-8") as f:
    next(f)                                    # 头：coordinate / license_names / fetch_status
    for line in f:
        p = line.rstrip("\n").split("\t")
        if p and p[0].strip():
            coords.append(p[0].strip())

def has_files(d):
    for _dp, _dn, fns in os.walk(d):
        if fns:
            return True
    return False

def variant_dirs(base, a, v):                  # KMP 变体：runtime -> runtime-android
    out = []
    if os.path.isdir(base):
        for m in os.listdir(base):
            if m == a or m.startswith(a + "-"):
                d = os.path.join(base, m, v)
                if os.path.isdir(d):
                    out.append(d)
    return out

no_desc, no_art = [], []
for c in coords:
    bits = c.split(":")
    if len(bits) < 3:
        continue
    g, a, v = bits[0], bits[1], bits[2]
    if not variant_dirs(os.path.join(desc, g), a, v):
        no_desc.append(c)
    if not any(has_files(d) for d in variant_dirs(os.path.join(art, g), a, v)):
        no_art.append(c)

print("total", len(coords))
print("no descriptor dir:", len(no_desc)); [print("   DESC-MISSING", c) for c in no_desc]
print("no artifact files:", len(no_art)); [print("   ART-MISSING", c) for c in no_art]
```

本轮输出：`total 340 / no descriptor dir: 0 / no artifact files: 11`（11 条明细见 §3.3）。

**探针的边界**：它只回答"缓存里有没有这个坐标的目录与文件"，不回答"Gradle 会不会要另一个 classifier/变体"。所以它能把"明显缺料"排除，**不能**替代 `--offline` 实跑（§8-U1）。
