# E-WS5-43 发版前置实跑（计划 WS5 items 37–44）

| 字段 | 值 |
|---|---|
| evidence-id | `E-43-WS5-01` |
| convention-version | 1 |
| batch | `2026-11-05-batch-43` |
| verdict | **PARTIAL**：37 的结论是「HEAD 不可构建」；38/39/43 有本轮产物；40/41/42 为 runbook；44 见同批 `docs/research/2026-11-05-release-gates.md` |
| executed-at | 2026-11-04T04:01–04:46+08:00（本机 `date` 实测；文档名按计划批次 43 的 `2026-11-05`） |
| operator | WS5 发布工程师（子代理），共享工作树，**只读仓内源码**；构建只在仓外副本 |
| head | `0fef943641f53560b92ce2afec4da22bf52f5e86`（`git rev-parse HEAD` 本轮实跑；与批次 43 基线一致） |
| sanitized | 是（§11 扫描；无口令、无主目录、无学习者内容；一次性钥匙只记 DN/指纹/字节数） |

> **口径**：**实跑**＝本轮本机执行并读到输出（命令与退出码原样给出）；**实读**＝本轮读到的仓内源码/文档行；**转录**＝引用既有 E 文件并标出处。
> **纪律**：仓内**零写**（未改源码/构建脚本/`docs/current`/`settings.gradle.kts`）；所有构建、密钥、日志都在仓外 `D:\smb-ws5-43`（一次性，可删）；Gradle 串行运行。

---

## 0. 八项结论

| 项 | 交付 | 结果 | 关键数字 |
|---|---|---|---|
| 37 | `git archive` 干净副本 + 离线构建尝试 | **完成（结论：HEAD FAIL）** | 归档 3397 文件 / 450 MiB / tar sha `bcfc649f…`（二次导出逐位相同）；HEAD 首轮编译失败 1 处，补齐后又暴露第二处；补 3 个未提交文件后 **BUILD SUCCESSFUL 4m02s / 412 tasks** |
| 38 | release R8 mapping / checksum（副本内） | **完成** | mapping 892,986 行 / 3,754 项目类 / R8 9.3.16；绑定 5/5 命中、负控 0；APK `a5fa0c4f…`（155,816,984 B） |
| 39 | 16KB ps16k 设备复核（x86_64） | **完成** | 静态 14/14 ELF PASS + `zipalign -P 16` exit 0；设备 `PAGE_SIZE=16384`、装包 sha 一致、COLD 1030 ms、九类签名 0 |
| 40 | arm64 可用性 / 等待清单 | **完成（BLOCKED）** | `FATAL \| Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host` |
| 41 | 签名配置 runbook（不落密钥） | **完成（生产签名 BLOCKED）** | 仓内密钥文件 0、`RELEASE_*` 变量 0；四变量契约 + 三条缺失断言 + Play 决策 |
| 42 | mapping 保留与版本绑定 | **完成（策略+字段）；sidecar 文件未生成** | 七类绑定字段本轮全部算出（§7） |
| 43 | 依赖钉版门 + 供应链快照 | **完成** | pins exit 0 / 负控 exit 1；346 坐标（334 有 license / 12 上游无标签）；42 直接依赖（37 落后 / 5 持平） |
| 44 | 发版门清单更新 | **完成（另件）** | `docs/research/2026-11-05-release-gates.md`（G-REL-00 由「dirty 树」升级为「HEAD 不可构建」） |

**一句话**：发版管线在「HEAD + 3 个未提交文件」上可离线跑通并产出可通过 16KB 检查的 release APK；但 **HEAD 本身编译不过**，所以「发版就绪」在本轮依然不成立，而且原因比批次 42 更硬（不是"树脏"，是"干净树出不了包"）。

---

## 1. 环境与产物身份

### 1.1 主机工具（本轮实跑）

| 工具 | 版本/路径 | 备注 |
|---|---|---|
| git | 2.55.0.windows.5 | `git archive` 用 `-c core.eol=lf -c core.autocrlf=false` |
| Java | Temurin 21.0.11+10 | `java -version` |
| Gradle | 9.6.1（wrapper，私有 `GRADLE_USER_HOME`） | `--offline --no-daemon --no-build-cache` |
| AGP / R8 | 9.3.0 / 9.3.16 | mapping 头两行 |
| adb / emulator | 37.0.1 / 37.1.11.0 | `C:\Android\Sdk` |
| zipalign / apksigner / aapt2 | build-tools 37.0.0 | |
| Python | 3.13.14 | 探针均 `python -I` |

### 1.2 干净副本（仓外，一次性）

| 项 | 值 |
|---|---|
| 源 | `git -c core.eol=lf -c core.autocrlf=false archive --format=tar HEAD`（只读 `.git`，不写工作树） |
| tar | 450 MiB，sha256 `bcfc649f024b22db3012941365de6ebb1131027cc987aaa8f23cddb519549f11`；**再次导出逐位相同**（commit 时间固定 mtime） |
| 解包 | 3397 文件（= `git ls-files` 计数），`gradlew` 为 LF（`od` 首行 `#!/bin/sh\n`） |
| 机器本地件 | `local.properties` 只有 `sdk.dir`（无凭据） |
| 私有 Gradle home | `modules-2` 695 MB + `wrapper` 151 MB → 846 MB；`cp -a` **9.1 s**（唯一失败项是活动锁文件 `modules-2.lock`，可弃） |
| `.git` | **不存在**（by design；后果见 §3.3） |

> **可复现的坑（新增发现）**：`robocopy /E` 用默认 `/R:1000000 /W:30`，在**被 Gradle 进程占用的** `modules-2.lock` 上重试挂起 10 分钟无进展；`cp -a` 9 秒完成且只报这一条。R4 §4.1 步骤 3 应写明用 `cp -a`（或 `robocopy /R:1 /W:1`），否则照抄会卡死。

### 1.3 产物身份（本轮构建）

| 产物 | 字节 | sha256 |
|---|---:|---|
| `app-strictOffline-release.apk` | 155,816,984 | `a5fa0c4f6ba49e57d660e56073b9cd276cd5a67f9834b08b8ce3ba573d3a4ef8` |
| `mapping.txt` | 126,098,693 | `86e56b5e74739cd42ff32eb3f39c12320cec483d59dce8e3247c46accacf4f0f` |
| `seeds.txt` | 549,169 | `17dba47e8f0cb60b3d9c49f51120068ae70d9f53308405151914e1d70c77d2de` |
| `usage.txt` | 8,152,513 | `4ee4cdb7d3f226805f1cb19f0eda4825888f22757402abd8087514de8ee78287` |
| `configuration.txt` | 84,492 | `5c26e6c3c50298361b711624c92d2956413e775ead4eb77598d2e036f28ae55b` |

`aapt2 dump badging`：`com.tingyun.smartmistakebook.offline` / versionCode **2** / versionName **0.2.0-offline** / minSdk **23** / targetSdk **36** / compileSdk **37**。
签名（一次性本机钥匙，**非生产**）：v1=true、v2=true、v3/v4=false；证书 sha256 `105d49aa6c2adf8f96a086dad1236abcc1b7b05f3c96259763572926f0a96502`，DN `CN=WS43 Ephemeral Local Only, OU=NotForRelease, O=Local, C=CN`；钥匙在仓外 `D:\smb-ws5-43\keys\`，口令只经环境变量进进程，**本轮结束后已连同口令文件删除**（复跑需另生成）。

### 1.4 设备

| serial | AVD | API | ABI | `getconf PAGE_SIZE` | `ro.product.cpu.page.size` | `ro.boot.hardware.cpu.pagesize` | boot_id |
|---|---|---:|---|---:|---|---|---|
| `emulator-5620` | `ps16k_x86_64` | 36 | x86_64 | **16384** | `<empty>` | 16384 | `aea6775f-fe98-450c-a099-ac6400e7f1d9` |

镜像 `google/sdk_gphone16k_x86_64/emu64xa16k:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys`；本轮新起（`Boot completed in 31849 ms`）。另外 5562/5566/5584 由他轨占用，未触碰。

---

## 2. 项 37：干净副本 + 离线构建（四次尝试，成败都记）

### 2.1 尝试表

| # | 源码面 | 命令（同一条，只换源码） | 结果 | 第一个失败点 |
|---|---|---|---|---|
| 1 | **HEAD 原样** | `./gradlew.bat --offline --no-daemon --no-build-cache --console=plain :app:assembleStrictOfflineRelease` | **EXIT=1 / 1m20s / 237 tasks** | `:core:model:compileKotlin`：`KnowledgeQuizTasks.kt:126:41 'when' expression must be exhaustive. Add the 'is PaperEditInput' branch or an 'else' branch.` |
| 2 | +1 文件 | 同上 | **EXIT=1 / 2m / 364 tasks** | `:core:database:compileReleaseKotlin`：`RoomStudyDatabase.kt` 6 处 `overrides nothing`（`readLatestLiveTutorVisualSnapshot` 等） |
| 3 | +2 文件 | 同上 | **EXIT=1 / 54s / 4 executed** | 我多补的 9 行 material-index 方法反向要求 `KnowledgePort`/`RoomKnowledgeImportMaintenance` 的新增项 → `Unresolved reference` |
| 4 | **+3 文件（最小集）** | 同上 | **EXIT=0 / 4m02s / 412 tasks（52 executed）** `BUILD SUCCESSFUL` | — |

四次日志全在仓外：`assemble-release-head.log`、`…-overlay1.log`、`…-overlay2.log`、`assemble-release.log`；末行均带 `EXIT=` 与 `DURATION=`。

**离线性（尝试 4 日志实测）**：`grep -ciE "download|No cached version|Could not resolve"` = **0**；尝试 1 亦为 0。即 346 个坐标全部由私有 home 的 `modules-2` 解析，无任何网络回退。

### 2.2 最小 overlay 集（副本内，仓内未动）

| 文件 | 相对 HEAD | 与工作树关系 | sha256（副本） |
|---|---|---|---|
| `core/model/…/KnowledgeQuizTasks.kt` | +7 行（`is PaperEditInput` 分支） | **逐字节等于工作树** | `153fe5ed1a0c…` |
| `core/database/…/StudyDatabasePort.kt` | +2 行（`ErrorCausePort` 导入与父接口） | **逐字节等于工作树** | `c7a35a34f7fc…` |
| `core/database/…/port/TutorVisualPort.kt` | +9 行（`readLatestLiveTutorVisualSnapshot`；KDoc 按原文缩写，工作树版为 +15 行） | 语义等于工作树，**非逐字节**（注释缩写） | `6772e74992a8…` |
| `core/database/…/RoomStudyDatabase.kt` | **等于 HEAD**（尝试 3 的 9 行已撤回） | 不等于工作树 | `1d5f371378c7…` |

### 2.3 结论（这一项真正的交付）

1. **HEAD `0fef9436` 不能编译**：至少两处独立断链——`KnowledgeQuizTasks.kt` 的 `when` 缺 `PaperEditInput` 分支（而 `PaperEditInput` 已在 HEAD 的 `PaperEditTasks.kt:27` 存在）；`RoomStudyDatabase.kt` 的 6 个 `override` 依赖**未提交**的 `TutorVisualPort`/`StudyDatabasePort` 增补。这是"干净树无法出包"，不是"树脏只是不体面"。
2. **批次 42 的双门 exit=0 不能外推到干净树**：那两次门都跑在脏树上（批次 42 §3.3/§3.4）。本项给出发版前的硬前置：**先把这 3 个文件的改动提交**，否则任何"发版就绪"都不成立。
3. **单次冷构建未测**：成功那轮是增量（360/412 up-to-date，前三次已编译大部分模块）。四次总墙钟 83+122+54+245 = **504 s**；"从零一次成功"的耗时仍为 UNVERIFIED（G-REL-02 的确定性对比同此）。

---

## 3. 项 38：release R8 mapping / checksum

### 3.1 结构与真实性

| 检查 | 命令 | 结果 |
|---|---|---|
| 头部三行 | `head -3 mapping.txt` | `# compiler: R8` / `# compiler_version: 9.3.16` / `# min_api: 23` |
| 规模 | `wc -l`（探针计） | **892,986 行**；项目类 **3,754** |
| 混淆真实 | 探针抽取前 5 个项目类短名 | `cs` / `ov` / `y50` / `z50` / `kt0`（全部短名） |
| 绑定探针 | 在 APK 的 2 个 `classes*.dex` 中数 `L<short>;` | **5/5 命中**（`cs=1 ov=1 y50=1 z50=2 kt0=1`）；负控 `Lzzzzz9;` = **0** |
| R8 keep 回归 | `check_r04_keep_rules.py` | **exit 0**（两份规则文件 `-dontobfuscate` 0 命中） |

> 绑定探针的边界与批次 42 相同：只证"mapping 短名存在于该 APK 的 dex 字符串池"，不证逐成员一致；逐成员一致靠"同一次构建 + sidecar sha"的流程约束。

### 3.2 与批次 42 产物的对照（不得互相替代）

| 项 | 批次 42（dirty 树） | 本轮（HEAD+3） |
|---|---|---|
| APK sha | `724ce718…342b44` | `a5fa0c4f…3d3a4ef8` |
| mapping sha | `c5b2d170…a83a5dc7e` | `86e56b5e…6accacf4f0f` |
| mapping 行数 / 项目类 | 893,641 / 3,760 | 892,986 / 3,754 |
| 证书 | `9dc07e9f…`（WS8 Ephemeral） | `105d49aa…`（WS43 Ephemeral） |

差异来自**源码面不同**（批次 42 是 581 条脏树；本轮是 HEAD+3 文件），不是 R8 版本变化（两侧均 9.3.16）。

### 3.3 产物内嵌源身份：**不可用**

```
$ unzip -p app-strictOffline-release.apk META-INF/version-control-info.textproto
generate_error_reason: NO_SUPPORTED_VCS_FOUND
```

`git archive` 副本没有 `.git`，AGP 无法写入 revision。后果：**任何从归档副本构建的 release 产物，其"产物↔源"只能靠 sidecar**（HEAD sha + tar sha + APK sha 三联），不能靠 APK 内嵌 textproto。G-REL-00 的判据需按此拆成两条（见 `docs/research/2026-11-05-release-gates.md` G-REL-00 的 (a)/(b)）。

---

## 4. 项 39：16KB（静态 + ps16k 设备）

### 4.1 静态（本轮新产物）

| 检查 | 命令 | 结果 |
|---|---|---|
| ZIP 页对齐 | `zipalign -c -P 16 4 app-strictOffline-release.apk` | **exit 0** |
| `.so` 清单 | 探针读 ZIP | **14 条，全部 `compress_type=0`（Stored）**；ABI = `arm64-v8a` + `x86_64`（无 32 位） |
| ELF 对齐 | 探针解析程序头（`PT_LOAD p_align ≥ 0x4000`；RELRO 余数页无可写 LOAD） | **14/14 PASS，0 FAIL** |

### 4.2 设备运行时

| 步骤 | 命令 | 结果 |
|---|---|---|
| 页大小 | `adb -s emulator-5620 shell getconf PAGE_SIZE` | **16384** |
| 换包 | `adb uninstall com.tingyun.smartmistakebook.offline` → `adb install -r <apk>` | `Success` / `Performing Streamed Install … Success`（旧包是批次 42 的 `724ce718`，证书不同必须卸） |
| 产物身份 | `pm path` → 设备内 `sha256sum` | `a5fa0c4f…3d3a4ef8`，**与构建产物逐字符相等** |
| 冷启 | `am force-stop` → `logcat -c` → `am start -W` | `Status: ok / LaunchState: COLD / TotalTime: 1030 ms` |
| 渲染 | `screencap`（`device-coldstart.png`，111,356 B） | 首页「晚上好，同学 / 今日复习 / 还没有可复习的题」+ 四栏底导，进程 pid 4497 存活 |
| 九类崩溃签名 | `grep -cF` 于 904 行 logcat | `FATAL EXCEPTION / StartupException / NoSuchMethodException / ClassNotFoundException / NoClassDefFoundError / UnsatisfiedLinkError / MlKitException / SIGSEGV / ANR in` **全 0** |

**未覆盖（不得当通过）**：本轮**未**触发 `libquickjs.so`、`libLiteRt.so`、`libmlkit_google_ocr_pipeline.so` 三条 native 路径（冷启不加载随包 `.so`，logcat 只有系统 `nativeloader` 行）。批次 42 已在同镜像上证过 ML Kit 装载（`ws8-release-prefight §2.4`），但**那是另一个 APK**，不能替代本轮；G-REL-28 维持 PARTIAL。

---

## 5. 项 40：arm64（BLOCKED + 等待清单）

```text
$ emulator -avd ps16k_arm64 -no-window -no-audio -no-snapshot -read-only -no-boot-anim
INFO  | Found systemPath C:\Android\Sdk\system-images\android-36\google_apis_ps16k\arm64-v8a\
FATAL | Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host.
        System image must match the host architecture.
```

**等待清单（发布者执行，机器不可替代）**

1. 设备：Pixel 8/8a（Android 15 QPR1+）、Pixel 9 系列（QPR2+）、Pixel 9a（16+）开「Boot with 16KB page size」；或 arm64 宿主上的 Cuttlefish 16KB。
2. 前置断言：`getprop ro.product.cpu.abi` ∈ {`arm64-v8a`,`arm64`}；`getconf PAGE_SIZE` = 16384（否则先重启到 16KB）。
3. 装**生产签名**的 `app-strictOffline-release.apk` → 冷启 → 各触发一次 `libquickjs.so`（JS 视觉引擎）、`libmlkit_google_ocr_pipeline.so`（OCR）、`libLiteRt.so`（端侧稠密腿）。
4. 判据：`adb logcat | grep -nativeloader` 见三条 `Load …!/lib/arm64-v8a/<name>.so … : ok`；`grep -Ei 'linker|RELRO|SIGSEGV|UnsatisfiedLinkError'` 为 0。

---

## 6. 项 41：签名配置 runbook（不落密钥）

### 6.1 事实（本轮实读/实跑）

| 项 | 值 | 出处 |
|---|---|---|
| 唯一契约（四个环境变量） | `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` / `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` | `app/build.gradle.kts:125-128`、`:147-150` |
| 注入时机 | 求值期 `configureReleaseSigningIfAvailable()`；**缺任一静默跳过** | `:124-142`、`:142` |
| 门 | `validateReleaseSigning` 只判"非空"，挂到名字含 `Release` 且以 `bundle`/`assemble`/`package` 开头的任务 | `:144-158`、`:161-167` |
| 文档与代码不一致 | `RELEASE.md:20` 指示配置 `keystore.properties`；**全仓无一处读它** | 本轮 `grep -n "keystore.properties" RELEASE.md` 只命中 `:20`；代码只读 `System.getenv` |
| 仓内密钥面 | 跟踪的 `*.jks/*.keystore/*.p12/*.pem/*.bks` = **0**；`.gitignore:18-20` 已挡 | 本轮 `git ls-files … \| wc -l` |
| 当前 shell 的 `RELEASE_*` | **0 个** | `env \| grep -c '^RELEASE_'` |

### 6.2 发布者步骤（C 类，机器不能替）

1. 在仓外准备生产 keystore（PKCS12/JKS），**不**拷入仓内任何路径。
2. 以环境变量或密钥服务注入四个变量；**不要**按 `RELEASE.md:20` 建 `keystore.properties`（代码不读，会造成"配了却无效"的假象）。
3. 三条当前**没有门**的断言，发布前人工/脚本执行：
   - `keytool -list -keystore "$RELEASE_STORE_FILE" -alias "$RELEASE_KEY_ALIAS"` → exit 0 且 `PrivateKeyEntry`（G-REL-11）；
   - `apksigner verify --print-certs <apk>` → 指纹 == 发布指纹（G-REL-09）；
   - DN 不得是 `Android Debug` / `R04 Local` / `CI Dry Run` / `WS8 Ephemeral` / `WS43 Ephemeral`。
4. 决定 Play App Signing：上传密钥 vs 托管（决定 G-REL-09 的期望指纹）；决定 v3/v4 策略（本轮 v1/v2=true、v3/v4=false）。
5. 轮换规则：本轮一次性钥匙（2 天有效）只在 `D:\smb-ws5-43\keys`，用完即删；**不得**当作生产钥匙。

> **新增必须指认失败**：runbook 里"不许建 `keystore.properties`"消灭的具体失败是批次 42 F10 已登记过的形态——开发者按文档配了文件、构建仍报「四个变量缺失」。本轮只写文档，不改 `RELEASE.md`（该文件不在本项落点，且改动属文档线）。

---

## 7. 项 42：mapping 保留与版本绑定

### 7.1 保留策略（沿用批次 42 六条，本轮补"来源三件套"）

1. **不进 git**：`**/build/` 已忽略；单 variant 126 MB。
2. **命名**：`mapping-<applicationId>-<versionCode>-<versionName>-<variant>.txt`（本轮示例 `mapping-com.tingyun.smartmistakebook.offline-2-0.2.0-offline-strictOfflineRelease.txt`）。
3. **落点**：仓外制品库/对象存储，与同批 APK/AAB 同目录；仓内只留 sidecar。
4. **寿命**：≥ 该版本在 Play 的可下载期 + 下线后 12 个月。
5. **Play 侧二选一**：`release { mappingFileUploadEnabled = true }`（当前未设置）或手工上传。
6. **来源三件套（本轮新增，因为副本无 `.git`）**：sidecar 必须同时含 `source.headSha` + `source.archiveSha256` + `source.overlay[]`，否则"这份 mapping 对应哪份源"不可复核。

### 7.2 sidecar 字段（本轮全部算出；文件本身未生成 → G-REL-06/27 仍 PARTIAL）

```json
{
  "artifact": {"path": "app-strictOffline-release.apk", "bytes": 155816984,
               "sha256": "a5fa0c4f6ba49e57d660e56073b9cd276cd5a67f9834b08b8ce3ba573d3a4ef8"},
  "mapping":  {"sha256": "86e56b5e74739cd42ff32eb3f39c12320cec483d59dce8e3247c46accacf4f0f",
               "lines": 892986, "compiler": "R8 9.3.16", "minApi": 23},
  "r8": {"seeds": "17dba47e…", "usage": "4ee4cdb7…", "configuration": "5c26e6c3…"},
  "version": {"versionCode": 2, "versionName": "0.2.0-offline", "variant": "strictOfflineRelease"},
  "signing": {"certSha256": "105d49aa…", "certDn": "CN=WS43 Ephemeral Local Only, OU=NotForRelease, O=Local, C=CN",
              "v1": true, "v2": true, "v3": false, "v4": false},
  "sixteenKb": {"zipalignP16Exit": 0, "elfPass": 14, "elfFail": 0, "soCount": 14, "abis": ["arm64-v8a", "x86_64"]},
  "source": {"headSha": "0fef943641f53560b92ce2afec4da22bf52f5e86",
             "archiveSha256": "bcfc649f024b22db3012941365de6ebb1131027cc987aaa8f23cddb519549f11",
             "overlay": ["KnowledgeQuizTasks.kt", "StudyDatabasePort.kt", "TutorVisualPort.kt"],
             "vcsEmbedded": "NO_SUPPORTED_VCS_FOUND"}
}
```

> 本轮**不**新增 sidecar 生成脚本：现有证据（sha 表 + 绑定探针 + apksigner）已可人工复核；加脚本属"未指认失败的机制"（批次 42 同判断）。若 CI 要自动归档，再随 G-REL-06 一起落。

---

## 8. 项 43：依赖钉版门 + 供应链快照

### 8.1 门（副本内实跑；git 依赖门在主树补跑）

| 门 | 命令 | 副本结果 | 主树结果（补跑） |
|---|---|---|---|
| 依赖钉版 | `python -I tools/ci/check_dependency_pins.py` | **exit 0**（`quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']`） | — |
| 负控 | 同上 `--expect-kotlin-runtime 2.4.10` | **exit 1** | — |
| R8 规则 | `check_r04_keep_rules.py` | **exit 0** | — |
| 锁文件 | `check_lockfiles.py` | exit 1：`git ls-files failed: fatal: not a git repository` | **exit 0（跳过态）**：0 已跟踪锁 / 13 未跟踪 / 缺 `:benchmark` → KD-72 维持 open |
| release manifest | `check_release_manifest.py` | exit 1：`no merged debug manifest found`（副本只跑了 release） | **exit 0**：9 checked + 4 debug merged，5 debug-only 双向一致 |
| stdlib 兼容 | `check_stdlib_compat.py` | **SKIP**（`no gradle.lockfile on disk`） | **exit 0**：0 unresolved / 23,860 引用 / 335 构件；11 坐标 unscanned |

**口径**：钉版门消费 **release 变体**的 `lintVitalAnalyze*Release` 中间产物；本轮先跑完 `assembleRelease` 再跑它，故 exit 0 是"本轮产物"上的结论，`clean` 后单跑会红（批次 42 同）。

### 8.2 供应链快照（本轮刷新，写入仓外 TSV）

| 指标 | 批次 42 | 本轮 |
|---|---:|---:|
| 唯一坐标（13 个未跟踪锁文件） | 340 | **346** |
| 取到 `<licenses>` | 328 | **334** |
| 上游 POM 无 license 标签 | 12 | **12** |
| 拉取失败 | 0 | **0** |
| 直接依赖（catalog） | 40 | **42** |
| 落后上游 `release` | 35 | **37** |
| 与 `release` 持平 | 5 | **5** |
| 动态版本（`+`/`latest.*`/`SNAPSHOT`） | 0 | **0**（`grep` 0 命中） |

**坐标漂移（+6，全部为新增，无删除）**：`org.jetbrains.kotlinx:kotlinx-io-{core,core-jvm,bytestring,bytestring-jvm}:0.6.0`、`…:kotlinx-serialization-json-io{,-jvm}:1.9.0`。直接依赖 +2：`kotlinx-io-core`、`kotlinx-serialization-json-io`。

**需人工裁定的落后项（与批次 42 同族，本轮不动版本）**：`litert 2.1.6 → 2.3.2`（minSdk 24 阻断）、`compose-bom 2026.06.00 → 2026.09.00`、`room3 3.0.0 → 3.1.0-alpha03`（alpha）、`okhttp 5.4.0 → 5.5.1`、`navigation-compose 2.9.8 → 2.10.2`、`work 2.10.5 → 2.12.0`、`desugar_jdk_libs 2.0.3 → 2.1.5`、`sqlite-jdbc 3.53.4.0 → 3.54.1.0`。

**12 个无 license 标签的上游 POM**照实记缺失，不推断许可（批次 42 已列 guava/failureaccess/listenablefuture、commonmark-ext-*、auto-value-annotations）。

---

## 9. 项 44：发版门清单更新（另件）

落盘：`docs/research/2026-11-05-release-gates.md`（G-REL-00…29，逐条命令/判据/类别/本轮状态）。三条口径变化：

1. **G-REL-00** 从「dirty 树」升级为**硬 FAIL**：干净 HEAD 不可编译（§2.3）；且归档构建的 APK 内嵌 `NO_SUPPORTED_VCS_FOUND`，判据改为"三联 sidecar"。
2. **G-REL-01** 由 NOT_RUN 改为 **PARTIAL**：离线构建 exit 0 已实测，但需要 3 文件 overlay，且成功轮是增量。
3. **G-REL-06/27** 仍 PARTIAL：字段齐、sidecar 文件未生成。

---

## 10. 未验证 / 阻塞

| 项 | 状态 | 说明 |
|---|---|---|
| HEAD 单次冷构建（从零一次成功） | **UNVERIFIED** | 成功轮复用了前三次的编译产物（360/412 up-to-date） |
| 构建确定性（两次 clean `cmp`） | **UNVERIFIED**（G-REL-02 维持 NOT_RUN） | 未做；且会覆盖已绑定的 APK |
| `libquickjs.so` / `libLiteRt.so` / ML Kit 的本轮装载 | **UNVERIFIED** | 冷启不加载随包 `.so`；G-REL-28 维持 PARTIAL |
| 三链路冒烟（录入/复习/导出） | **未执行**（本轮只做到渲染） | 批次 42 在另一份 APK 上做过，不可外推 |
| 生产签名 / keystore 可打开 / Play App Signing | **BLOCKED（C 类）** | 无生产钥匙；§6 runbook |
| arm64 16KB 运行时 | **BLOCKED（外部硬件）** | §5 |
| AAB + bundletool 页对齐 | **BLOCKED（G-REL-17）** | 本机无 `bundletool-all`；本轮未出 AAB |
| retrace（用 mapping 还原真实栈） | **UNVERIFIED** | 未做 |
| 锁文件写锁（KD-72） | **BLOCKED（维持）** | 13 未跟踪锁、`:benchmark` 缺锁；本轮不重试写锁 |
| sidecar 文件 | **未生成** | §7.2 字段齐；G-REL-06/27 PARTIAL |

---

## 11. 脱敏、体量与新增自查

- **脱敏扫描**：本文无口令、无主目录（用户目录只出现 `C:\Android\Sdk` 这类工具路径）、无学习者内容、无物理设备序列号（模拟器 serial/AVD 属测试资源）；一次性钥匙只记 DN/指纹/字节数。`grep -niE "password|storepass|keypass"` 于本文 0 命中可执行口令行。
- **新增自查**：仓内新增只有本文件 + `2026-11-05-release-gates.md`（计划项 42/44 的直接交付）。仓外新增：副本、私有 Gradle home、一次性钥匙、探针脚本、日志、TSV——全部在 `D:\smb-ws5-43`，可整体删除；没有新增仓内脚本、没有新增依赖、没有改任何构建文件。
- **体量**：见文件头 `wc -lc` 自查行（下一轮由记录员复核）。

## 12. 复跑命令（最小集）

```bash
# 1) 干净副本（LF 保真；不写工作树）
REV=$(git rev-parse HEAD); OUT=/d/smb-ws5-43
git -c core.eol=lf -c core.autocrlf=false archive --format=tar HEAD -o "$OUT/tree.tar"
mkdir -p "$OUT/src" && tar -xf "$OUT/tree.tar" -C "$OUT/src"
cp local.properties "$OUT/src/local.properties"
# 2) 私有 home（cp -a 而不是 robocopy 默认参数）
cp -a .gradle/caches/modules-2 "$OUT/src/.gradle/caches/"
cp -a .gradle/wrapper "$OUT/src/.gradle/"
# 3) 覆盖本节 §2.2 的 3 个文件（否则 HEAD 编译失败）
# 4) 仓外一次性钥匙 → 四个 RELEASE_* 环境变量 → 离线构建
cd "$OUT/src" && GRADLE_USER_HOME="$OUT/src/.gradle" ANDROID_HOME=C:/Android/Sdk \
  ./gradlew.bat --offline --no-daemon --no-build-cache --console=plain \
  :app:assembleStrictOfflineRelease
# 5) 静态 + 设备
python -I "$OUT/out/artifact_probe.py" <apk> <mapping.txt>
/c/Android/Sdk/build-tools/37.0.0/zipalign.exe -c -P 16 4 <apk>
adb -s emulator-5620 shell getconf PAGE_SIZE          # 期望 16384
adb -s emulator-5620 install -r <apk> && adb -s emulator-5620 shell am start -W -n \
  com.tingyun.smartmistakebook.offline/com.tingyun.smartmistakebook.MainActivity
```

## 13. 回链

- 计划：`.zcode/plans/plan-2026-11-05-batch-43.md:70-78`（WS5 37–44）、`:133-141`（硬约束）。
- 前序：`docs/research/2026-11-05-clean-tree-release.md`（R4，本项按其 §4.1 执行并回填 U1/U2/U5）；`docs/research/2026-11-04-ws8-release-prefight.md`（批次 42 WS8 原值）；`docs/research/2026-11-04-release-gates-draft.md`（门 ID 冻结基线）。
- 本轮另件：`docs/research/2026-11-05-release-gates.md`（项 44）。
- 台账建议：HEAD 不可构建应作为**发版阻塞项**登记给主会话（不属本文件权限）。
