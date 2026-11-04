<!-- WS10 安全/依赖（计划项 65–68）。体积：274 行 / 约 21 KiB（<64 KiB、<400 行）。 -->
# WS10 安全/依赖证据（批次 43，2026-11-04）

| 字段 | 值 |
|---|---|
| evidence-id | `B43-WS10-01` |
| 执行者 | 供应链/安全子代理（共享工作树，**只读仓内文件 + 一次 Mimosa 扫描 + 只读网络取 POM/metadata**） |
| head | `0fef9436`（`git rev-parse --short HEAD`，本轮实跑） |
| 工作树 | `git status --porcelain` = **590** 行（折叠）；`-uall` = **3811**（131 M + 3680 ??）；staged = **0** |
| 执行项 | 计划 `.zcode/plans/plan-2026-11-05-batch-43.md:108-112`（65–68） |
| 未执行 | **任何 Gradle 任务**、任何设备/`adb`、任何 APK 构建、任何 git 写、任何密钥读取/输出 |
| verdict | 65 已重试但**无完整结论（`inconclusive`）→ 不宣称安全**；66 快照已落（直接 60 + 解析图 346）；67 走**等待清单**分支（G3 本轮新阻塞）；68 明文密钥扫描**未发现凭据**（只报位置） |

> 口径：**实跑**=本轮命令与输出；**实读**=本轮读到的源码/文档；**转录**=引用既有台账原文并标行。状态三值：已核 / UNVERIFIED / BLOCKED。

---

## 0. 边界与不可变约束

- 6C-2 的 mask=8「不提供」/mask=0「禁止」/派生索引恒裁为**用户已确认裁定**，本项未触碰任何行为（依据 `docs/research/2026-11-04-batch-42-record.md:517-522`、计划 `:5`）。
- 本项只新增本文件；未改 `core/export`（KD-75 最小 hunk 归 WS1）、未改热区、未改锁文件、未提交。
- **Mimosa 无完整结论不得称安全**（本 ask 硬约束；`docs/REFACTOR-MASTER-LINE.md:143`；批次 42 同口径 `docs/research/2026-11-04-batch-42-record.md:504`）。本文件不含任何可用凭据。

---

## 1. 项 65：Mimosa 完整审计重试

### 1.1 作业身份（本轮实跑）

| 字段 | 值 |
|---|---|
| jobId | `scan-job-mw18lxy6-ef338094f718363b` |
| 调用 | `security_scan_start(project="D:\smart mistake book", depth="deep")` |
| 结果 | `status=completed`，耗时约 99 s（22:06:11Z → 22:07:50Z） |
| scanId | `scan-2026-11-03T22-07-50.712Z-c0134c3ef6d0` |
| scanDir | `C:\Users\听云\.mimosa\security-scans\project-c079ff08106a86cc56edfee2\scan-2026-11-03T22-07-50.712Z-c0134c3ef6d0` |
| seal | `sha256:d06d4e4b02926f5ab2e6869b8f6be8cdec23e35b2f0dfe1dae2d3e89df44484e` |
| scanner | `mimosa 0.1.0`，`evidenceBoundary=static_only_no_runtime_execution`，`verdictEffect=none` |

本轮与历史 `scanner_enobufs` 不同：**扫描器本身已可用并完成**；但审计结论仍不完整，见 1.2。

### 1.2 覆盖状态（`coverage.json`，实读）

| 字段 | 值 |
|---|---|
| `runStatus` | **`inconclusive`** |
| `completeness` | **`partial`** |
| `gaps` | `["部分分析阶段未能完整覆盖"]` |
| source | selectedFiles **2164** / parsedFiles **2164** / truncated **false** / readFailures **0** / parseFailures **0** |
| phases.threatModel | **`partial`**（entryPoints 0 / principals 0 / authorizationSurfaces 0） |
| phases.findingDiscovery | **`partial`**（13） |
| phases.validation | `completed`（investigated 0 / queries 0） |
| phases.pathAnalysis | `completed`（functions 51 / callEdges 274 / traces 0） |
| dependencySummary | `completion=completed`，`packagesScanned=357`，离线 advisory **available**，matchedAdvisories **0**，unknown **0** |

**结论**：扫描机制成功、产物已封印，但 `runStatus=inconclusive` 且威胁建模阶段无入口/主体数据。按纪律**不得**把本次审计写成「通过/安全」。`packagesScanned=357` 与本轮锁文件解析图 346 个坐标同量级（见 §2.2），可作弱交叉印证，不构成漏洞结论。

### 1.3 Findings（13；`totals = {high:6, medium:0, low:7, info:0, businessLogic:0}`）

只报位置；`TRACKED` 表示该路径已被 git 跟踪。

| # | 严重度 | 类别 | 位置 | tracked | proofGaps |
|---|---|---|---|---|---|
| 1 | LOW | insecure-randomness (CWE-330) | `tools/dense_build/gen_tokenizer_fixture.py:179` | TRACKED | 0 |
| 2 | LOW | insecure-randomness | `tools/kb_build/audit_content_bindings.py:596` | TRACKED | 0 |
| 3 | LOW | insecure-randomness | `tools/kb_build/audit_content_bindings.py:638` | TRACKED | 0 |
| 4 | LOW | insecure-randomness | `tools/kb_build/audit_quality.py:100` | TRACKED | 0 |
| 5 | LOW | insecure-randomness | `tools/kb_build/audit_quality.py:163` | TRACKED | 0 |
| 6 | LOW | insecure-randomness | `tools/kb_build/audit_quality.py:175` | TRACKED | 0 |
| 7 | LOW | insecure-randomness | `tools/kb_coverage/retrieval_significance.py:177` | TRACKED | 0 |
| 8 | HIGH | other-security（patch→path-traversal 1 跳） | `tools/dense_build/patch_litert_api_aar.py:97` | TRACKED | 1（静态 advisory 需人工确认真实数据流/可利用性） |
| 9 | HIGH | path-traversal (CWE-22) | `_judge30_build.py:108` | untracked | 0 |
| 10 | HIGH | path-traversal | `_tmp_gen_23.py:958` | untracked | 0 |
| 11 | HIGH | path-traversal | `scratch/ocr_53math.py:51` | untracked（`.gitignore:38` 忽略） | 0 |
| 12 | HIGH | path-traversal | `tmp_judge_8/make_csv.py:259` | untracked | 0 |
| 13 | HIGH | path-traversal | `tools/kb_coverage/_slice26_verdicts.py:829` | untracked | 0 |

- 6 条 HIGH 中 **仅 1 条在跟踪文件内**（#8），且自带 proof gap；其余 5 条 HIGH 全在未跟踪脚本（#9–#13）。#11 所在 `scratch/` 被 `.gitignore:38` 忽略。
- 7 条 LOW 全在跟踪的 `tools/` 脚本，不是 App 运行时路径。
- 本轮**未修复**任何 finding：WS10 的范围是重试与记录；修复需按其归属线另行排期（不在本项落点内）。

### 1.4 项 65 判定

**扫描器可用、产物完整封印；但审计结论 `inconclusive` → 不获得完整安全结论，不宣称项目安全。** 6 HIGH 已登记位置，交由对应线分诊。

---

## 2. 项 66：依赖 license / 维护快照

### 2.1 方法（本轮实跑）

- 直接声明面：解析 `gradle/libs.versions.toml` 的 50 个 `[libraries]` + 10 个 `[plugins]`；Compose 版本经 `compose-bom 2026.06.00` 的 `dependencyManagement` 解析。
- 解析图面：读取盘上 **13 个未跟踪** `gradle.lockfile`（`settings-gradle.lockfile` 只有 `empty=`，零坐标），得到 **346 个唯一坐标**。
- 对每个坐标从 **Google Maven / Maven Central** 取 POM `<licenses>`；从 `maven-metadata.xml` 取 `release/latest/lastUpdated`。
- 脚本：`build/ws10/dep_snapshot.py`、`build/ws10/lock_license_snapshot.py`（均 `python -I` 运行；固定主机白名单、拒绝重定向、校验解析 IP、拒绝 DOCTYPE/ENTITY、限制响应体；脚本不写文件，stdout 由 shell 重定向到 `build/ws10/`）。
- 原始产物（gitignored）：`build/ws10/dependency-snapshot.json`、`build/ws10/lock-license-snapshot.json`。

### 2.2 解析图 license 分布（346 坐标，本轮实跑）

| license | 坐标数 |
|---|---:|
| Apache-2.0（5 种写法合并） | **313** |
| ML Kit Terms of Service | **7** |
| Android Software Development Kit License | **4** |
| Play Core Software Development Kit Terms of Service | **3** |
| BSD-3-Clause | **3** |
| MIT（2 种写法） | **2** |
| GNU GPL v2 + Classpath Exception | **1** |
| Eclipse Public License 1.0 | **1** |
| POM 未声明 license | **12** |
| **合计** | **346**（POM 全部取到，unresolved=0） |

需人工复核的非 Apache 组：

- **ML Kit ToS（7）**：`com.google.mlkit:*`（含 `text-recognition-chinese:16.0.1`）与 `com.google.android.gms:play-services-mlkit-*`。
- **Play Core SDK ToS（3）**：`com.google.android.play:ai-delivery:0.1.1-alpha01`、`asset-delivery:2.3.0`、`core-common:2.0.4`。
- **Android SDK License（4）**：`play-services-base:18.5.0`、`play-services-basement:18.4.0`、`play-services-tasks:18.2.0`、`com.google.android.odml:image:1.0.0-beta1`。
- **GPL-2.0+CPE（1）**：`com.android.tools:desugar_jdk_libs:2.0.3`（构建/运行时 desugar；Classpath Exception）。
- **EPL-1.0（1）**：`junit:junit:4.13.2`（测试期）。
- **BSD-3-Clause（3）**：`datastore-preferences-external-protobuf:1.2.1`、`room3-external-antlr:3.0.0`、`desugar_jdk_libs_configuration:2.0.3`。
- **POM 未声明（12）**：Guava 系（`guava:33.2.1-jre`、`guava:33.5.0-android`、`failureaccess:*`、`listenablefuture:*`）、`auto-value-annotations:1.6.3`、`commonmark:0.21.0` 及 3 个 `commonmark-ext-*`、`hamcrest-core:1.3`。**license 状态 UNVERIFIED（POM 无声明，未按上游仓库页面另行取证）**。

补充（直接声明面 60 条）：`com.google.guava:guava:33.5.0-android` 同样 POM 无 license；其余 59 条均取到声明。

### 2.3 维护快照（直接声明坐标；`release/latest` 为抓取时 maven-metadata 值）

只列**落后于已发布稳定版**或**值得登记**的项；带 prerelease 尾缀的最新值仅记录、不作升级建议。

| 坐标 | 固定值 | metadata 最新 | 备注 |
|---|---|---|---|
| `com.google.ai.edge.litert:litert` | 2.1.6 | 2.3.2 | 固定理由在 `gradle/libs.versions.toml:29-32`（minSdk 23 与 2.2.0+ manifest 冲突），**不得盲目追新** |
| `io.github.dokar3:quickjs-kt-android` | 1.0.15 | 1.0.15 | 已最新；字节/许可记录在 `gradle/libs.versions.toml:43-51` |
| `androidx.compose:compose-bom` | 2026.06.00 | 2026.09.00 | BOM 落后 1 个季度 |
| `com.squareup.okhttp3:okhttp` | 5.4.0 | 5.5.1 | — |
| `org.xerial:sqlite-jdbc` | 3.53.4.0 | 3.54.1.0 | 仅测试/工具链 |
| `com.google.devtools.ksp` | 2.3.10 | 2.3.13 | — |
| `org.jetbrains.kotlinx.kover` | 0.9.1 | 0.9.11 | — |
| `org.jetbrains.kotlinx:kotlinx-serialization-*` | 1.9.0 | 1.12.0 | — |
| `com.android.tools:desugar_jdk_libs` | 2.0.3 | 2.1.5 | — |
| `androidx.paging:*` | 3.4.2 | 3.5.3 | — |
| `androidx.work:work-runtime-ktx` | 2.10.5 | 2.12.0 | — |
| `androidx.navigation:navigation-compose` | 2.9.8 | 2.10.2 | — |
| `androidx.datastore:datastore-preferences` | 1.2.1 | 1.3.0 | — |
| `androidx.core:core-ktx` | 1.18.0 | 1.19.1 | — |
| `androidx.profileinstaller:profileinstaller` | 1.4.0 | 1.4.1 | — |
| `androidx.test.uiautomator:uiautomator` | 2.3.0 | 2.4.0 | — |
| `com.google.mlkit:text-recognition-chinese` | 16.0.1 | 16.0.1 | 已最新，但 metadata `lastUpdated=20240807`（长期未更新，登记维护信号） |
| `junit:junit` | 4.13.2 | 4.13.2 | 已最新（2021-02） |
| `androidx.compose.material:material-icons-extended` | 1.7.8 | 1.7.8 | 已最新（2025-02） |
| `org.jetbrains.kotlinx:kotlinx-coroutines-guava` | 声明 1.8.0（`libs.versions.toml:40,112`） | 1.11.0 | **解析图实际锁到 1.11.0**（`app`/`core/data` 锁），与声明值不同；属 coroutines 1.11.0 的解析升级 |

### 2.4 项 66 判定与边界

- 已产出可机读快照（直接 60 + 解析图 346）；**无自动化 license 政策门**（`tools/ci/` 内无 license gate，`grep -rln -i license tools/ci/` 仅命中 `make_v4_pack_fixture.py` 的非门禁文本）。本项只做快照，不新增门。
- **UNVERIFIED**：传递闭包的完整 license 集合以 13 个未跟踪锁文件的解析面为准，未覆盖 release/androidTest/unitTest 等配置（锁文件本身覆盖不全，见 §3.2）；Guava/commonmark 等 12 个 POM 无 license，未逐一到上游仓库页面取证；许可证合规结论（是否可分发）需法务/产品裁定，本项不作结论。
- **未运行 `tools/ci/check_dependency_pins.py`**：它依赖 Gradle 产出的 `artifact-dependencies.xml`，本树不存在（`find app core feature build -name artifact-dependencies.xml` 零命中），且 Gradle 只能门禁阶段跑 → UNVERIFIED。

---

## 3. 项 67：锁文件窗口（隔离副本）或等待清单

### 3.1 选择与理由

计划给的是「隔离副本尝试**或**等待清单」。写锁必须运行 Gradle（`lockAllDependencies --write-locks`），而本项纪律是 **Gradle 只在门禁阶段串行运行**；当前不是门禁阶段，且共享工作树上有他线在跑的解析输入改动。因此本轮走**等待清单**分支，**未**执行隔离副本写锁，**未**运行任何 Gradle。

### 3.2 本轮实测（全部实跑）

| 读数 | 命令 | 结果 |
|---|---|---|
| 未跟踪锁 | `git status --porcelain \| grep -i lockfile` | **14**（13× `<module>/gradle.lockfile` + `settings-gradle.lockfile`） |
| 已跟踪锁 | `git ls-files '*gradle.lockfile' '*lockfile'` | **0** |
| `:benchmark` 锁 | `ls benchmark/gradle.lockfile` | **不存在** |
| 锁总量/换行 | `cat … \| wc -c`；`grep -c $'\r'` | **102,529 B**；`app` **269/269 行 CRLF**、`settings` **5/5 行 CRLF**（`core.autocrlf=true`，`text: auto`） |
| 绝对路径 | `grep -nE "[A-Za-z]:[\\\\/]"`（14 文件） | **零命中** |
| 门脚本（跳过态） | `python -I tools/ci/check_lockfiles.py` | **EXIT=0**；提示 13 个未跟踪锁、缺 `:benchmark` |
| 门脚本（强制态） | 注入 13 个锁路径调 `check()` | **36 errors**（与批次 42 同数） |
| 解析输入面 | `git status --porcelain -- '*.gradle.kts' 'gradle.properties' 'gradle/libs.versions.toml' 'gradle/wrapper/*'` | **`M core/data/build.gradle.kts`、`M gradle/libs.versions.toml`**（+40 / +5 行） |
| 归档规模 | `git archive --format=tar HEAD \| wc -c` / `tar -t \| wc -l` | **471,531,520 B** / **3,949 条目** |
| 磁盘 | `df -h /d` | **352 G 可用** |
| wrapper / local | `git ls-files gradlew …`；`git ls-files local.properties` | wrapper 4/4 已跟踪；`local.properties` 未跟踪 |

36 条错分布（强制态）：`:app` 缺 androidTest 1；**12** 个模块缺 `*RuntimeClasspath`；10 个 Android 模块缺 unitTest+androidTest；`:core:model`/`:core:domain` 各缺 test；`:benchmark` 缺锁。与批次 42 记录的 36 条同构（`docs/research/2026-11-04-lockfile-window.md:243-252`）。

**本轮新变化（相对批次 42）**：批次 42 研究把 G3 记为「已满足（仅 `.gitignore` M）」（`docs/research/2026-11-04-lockfile-window.md:286`）；本轮解析输入面出现**两个真实构建脚本改动** → **G3 转为不满足**。此外盘上锁文件从 LF 变为 **CRLF**（批次 42 记 LF，`:38`）；`splitlines()` 解析不受影响，但提交时会被 `text:auto` 归一化。

### 3.3 等待清单（G1–G9 更新版）

| # | 等待项 | 判据 | 本轮状态 |
|---|---|---|---|
| G1 | 门禁窗口（串行） | 写入期间同目录无第二个 Gradle；无 >60 s 锁等待 | **未满足**（本项禁跑 Gradle） |
| G2 | 锁状态输入归一 | `git status --porcelain \| grep -ci lockfile` = 0（旧锁移出） | **未满足**（14 条未跟踪） |
| G3 | 解析输入 = HEAD | 上述解析输入面为空 | **未满足**（`core/data/build.gradle.kts`、`gradle/libs.versions.toml` 脏；需其归属线提交/回退后再写锁） |
| G4 | 写入成功 | `lockAllDependencies --write-locks` exit=0 | UNVERIFIED |
| G5 | 幂等 | 第二次写零 diff / sha256 全等 | UNVERIFIED |
| G6 | 覆盖冒烟 | 副本内 CI 解析面零 “not part of the dependency lock state” | UNVERIFIED |
| G7 | 门脚本 | 15 锁入库后 `check_lockfiles.py` exit=0 | 未满足（强制态 36 错） |
| G8 | 负控 | 人为抬版必须解析期红 | 从未执行 |
| G9 | 提交顺序 | 锁与最后一次 build 脚本变更同批提交 | 未开始 |

隔离副本前置已就绪：wrapper 4/4 随 HEAD、`local.properties` 需单独拷、D 盘 352 G 足够、归档不含 `.git`（3,949 条目 / 471.5 MB）。执行序列沿用 `docs/research/2026-11-04-lockfile-window.md:182-220`（移出旧锁 → 副本写 → 幂等 → 覆盖冒烟 → 拷回 → 门 → 负控）。

### 3.4 项 67 判定

**KD-72 仍 open。** 本轮不写锁、不提交锁；最小阻塞是 **G3（解析输入脏）** 与 **G2（14 个旧锁）**，其次是 G4–G8 从未在门禁阶段取证。

---

## 4. 项 68：明文密钥扫描（只报位置）

### 4.1 规则与范围（本轮实跑）

规则：私钥头 `-----BEGIN … PRIVATE KEY-----`、`AKIA[0-9A-Z]{16}`、`gh[pousr]_…`、`AIza…`、`xox[baprs]-…`、`sk_live_…`、`sk-[A-Za-z0-9]{32,}`、通用 `(password|passwd|pwd|api_key|secret|access_key|auth_token)= "≥8"`、`storePassword|keyPassword|keyAlias`、敏感文件名（`*.jks/*.keystore/*.p12/*.pfx/*.pem/*.key/.env*/keystore.properties/id_rsa/id_ed25519`）。

范围：**已跟踪文件全量**（`git grep`）+ **未跟踪非忽略文件全量**（`git ls-files --others --exclude-standard`，3,680 个，含 `tmp/`）；敏感文件名另做**磁盘 maxdepth 4** 检查（排除 `.git/.gradle/.worktrees/build/node_modules/knowledge-production/kb_pdf_samples`）。`.worktrees/` 由 `.git/info/exclude:7` 排除（本地排除，不在 `.gitignore`）。

### 4.2 结果（只报位置，不复制值）

- **强特征令牌（私钥头/AWS/GitHub/Google API/Slack/Stripe live）：跟踪与未跟踪文件均 0 命中。**
- **跟踪文件**：`sk-[A-Za-z0-9]{32,}` 只有 2 处，均为 **model-task 标识**（前缀 `model-task-`，非凭据）：
  - `docs/known-defects.md:1511`
  - `docs/research/2026-10-11-thin-vertical-slice-walkthrough.md:286`
- **通用赋值规则命中**（跟踪文件）：
  - `docs/research/2026-10-29-snapshot-identity-wal-and-r8.md:521,523`、`docs/research/2026-10-30-batch-35-device-criteria.md:179,181` —— 值均为 shell 变量引用 `"$R04_STOREPASS"`（非字面量；上下文见 `2026-10-29-…:515-523`）。
  - `core/data/src/test/.../settings/StoreDelegateTest.kt:435,511`、`.../settings/ModelConfigurationValidatorTest.kt:120`、`.../model/OpenAiCompatibleModelGatewayTest.kt:2858` —— 测试夹具字面量（长度 12–14，用于“含空白/控制字符的密钥”“孤儿 secret”等用例），**非生产凭据**。
- **未跟踪非忽略文件**：通用赋值规则 **0 命中**。
- **签名配置**：`app/build.gradle.kts:123-142` 只从 `System.getenv("RELEASE_*")` 读取，缺环境变量时静默跳过；文件内无凭据字面量。符合“凭据只从环境变量/密钥服务读取”。
- **敏感文件名**：`git ls-files` 0 个；磁盘（深度 4）0 个；`gradle.properties` 无凭据；`local.properties` 仅有 `sdk.dir`；`.github/workflows/*` 无 `secrets.` 引用。
- **历史文件名**：`git log --all --name-only` 扫描 0 个敏感文件名。**历史内容未逐提交扫描 → UNVERIFIED**。

### 4.3 项 68 判定

**未发现明文凭据**。以上为“扫描覆盖内的否定结果”，不等于“仓库永不含密钥”；历史内容、深度 4 以下目录、二进制文件未扫描（UNVERIFIED）。

---

## 5. 结论与遗留

1. **项 65**：Mimosa 重试**已执行**，扫描器可用、产物封印 `sha256:d06d4e4b…`；但 `runStatus=inconclusive / completeness=partial` → **无完整安全结论，不宣称安全**。6 HIGH 位置已登记（1 跟踪 + 5 未跟踪）。
2. **项 66**：license/维护快照已落盘（直接 60 + 解析图 346）；非 Apache 组与 12 个无 license POM 需复核；无自动化 license 门（本项未新增）。
3. **项 67**：走等待清单；**G3 本轮新阻塞**（两个解析输入脏）、G2 未归一、G7 强制态 36 错；**未跑 Gradle、未写/提交锁**。
4. **项 68**：强特征零命中；仅测试夹具与文档变量引用；签名只走环境变量。

**未验证 / BLOCKED**

| # | 项 | 状态 |
|---|---|---|
| U1 | Mimosa 完整安全结论 | **无**（`inconclusive`，不得写通过） |
| U2 | Guava/commonmark 等 12 个 POM 无 license 的坐标 | UNVERIFIED（未到上游仓库页面取证） |
| U3 | 传递闭包在 release/androidTest/unitTest 配置的 license 覆盖 | UNVERIFIED（锁文件本身覆盖不全） |
| U4 | `check_dependency_pins.py` | UNVERIFIED（缺 `artifact-dependencies.xml`，需门禁期 Gradle） |
| U5 | 锁写入/幂等/覆盖/负控（G4–G8） | UNVERIFIED（本项禁跑 Gradle） |
| U6 | git 历史**内容**中的密钥 | UNVERIFIED（只扫了文件名） |
| B1 | 生产签名 4/4 环境变量 | BLOCKED（外部密钥，见批次 42 收口 `docs/research/2026-11-04-batch-42-record.md:546`） |

---

## 6. 可重跑命令

```bash
cd "D:/smart mistake book"
git rev-parse --short HEAD                                   # 0fef9436
git status --porcelain | wc -l                               # 590
git status --porcelain -uall | wc -l                         # 3811
git status --porcelain | grep -i lockfile | wc -l            # 14
git ls-files '*gradle.lockfile' '*lockfile' | wc -l          # 0
python -I tools/ci/check_lockfiles.py                        # EXIT=0 (skip mode)
python -I build/ws10/dep_snapshot.py        > build/ws10/dependency-snapshot.json
python -I build/ws10/lock_license_snapshot.py > build/ws10/lock-license-snapshot.json
# Mimosa：security_scan_start(project="D:\\smart mistake book", depth="deep")
#         security_scan_status(jobId=…)
git grep -n -I -E -- "-----BEGIN ([A-Z0-9 ]+ )?PRIVATE KEY-----|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9_]{30,}|AIza[0-9A-Za-z_-]{35}|xox[baprs]-|sk_live_" -- .
```

> 强制态门脚本（36 errors）与 lockfile 明细见 §3.2；`build/ws10/` 为 gitignored 产物，不作为关闭证据，只作复算输入。
