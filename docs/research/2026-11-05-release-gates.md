# 发版门清单（批次 43 更新版）

- 日期：2026-11-05（实跑 2026-11-04 04:01–04:46 本地）；上一版：`docs/research/2026-11-04-release-gates-draft.md`
- 来源：本版状态全部来自 `docs/research/evidence/2026-11-05-batch-43/ws5-release-prefight.md`（下称 `E-WS5`）§1–§8 与其中引用的仓外日志；未跑项一律 `NOT_RUN`，外部条件 `BLOCKED`。
- **门 ID 冻结**：本版只更新状态与判据细节；新增门只能追加（G-REL-29+），不得复用旧号改语义。
- 类别：**A** 本机只读/命令行 / **B** 门禁阶段的 Gradle 或设备 / **C** 需发布者提供 / **D** 外部阻塞。
- **本轮绑定产物**：`app-strictOffline-release.apk` sha256 `a5fa0c4f6ba49e57d660e56073b9cd276cd5a67f9834b08b8ce3ba573d3a4ef8`（155,816,984 B），源 = HEAD `0fef9436` 归档 + 3 个未提交文件（`E-WS5 §2.2`），签名 = 一次性本机钥匙（**非生产**）。

## 0. 一页结论

| 面 | 门 |
|---|---|
| **通过** | 03 / 04 / 05 / 07 / 08 / 10 / 12 / 13 / 14 / 15 / 18 / 21 / 22 / 24 / 25 |
| **部分** | 01（离线构建 exit 0 但需 overlay 且非冷构建）、06（sha 齐、sidecar 未生成）、23（装包+冷启+渲染，未跑三链路）、27（同 06）、28（native 真装未触发） |
| **红** | **00**（干净 HEAD 不可编译 + APK 无 VCS 内嵌）、**09**（一次性证书，非生产指纹）、**19**（锁文件未落地，KD-72 open） |
| **BLOCKED** | 16（arm64 16KB）、17（AAB bundletool） |
| **NOT_RUN** | 02、11、20、26、29 |

**本轮"不得发布"的理由（比上一版更硬）**：

1. **G-REL-00 不是"树脏"，是"干净树出不了包"**：`git archive HEAD` 的离线构建在 `:core:model:compileKotlin` 直接编译失败；补 3 个**未提交**文件后才成功（`E-WS5 §2.3`）。发布前必须先把这 3 个文件提交，否则任何 APK 都无法由 HEAD 字节复现。
2. **G-REL-09 仍是本地测试证书**：DN 带 `NotForRelease`，指纹 `105d49aa…`。
3. **G-REL-19 仍 open**：0 个已跟踪锁、13 个未跟踪锁、`:benchmark` 缺锁。

## 1. 门表

| ID | 门 | 类别 | 命令（要点） | 判据 | 本轮状态（2026-11-05） |
|---|---|---|---|---|---|
| G-REL-00 | 产物↔源 | A | `git rev-parse HEAD`；`git status --porcelain`；构建源 = 归档；`unzip -p <apk> META-INF/version-control-info.textproto` | **两条判据择一或并存**：(a) 产物 revision == 树 HEAD **且** 树 dirty=0；(b) 归档构建时 sidecar 同时含 `headSha`/`archiveSha256`/`overlay[]`，且 overlay 为空 | **FAIL（硬）**：HEAD `0fef9436` 编译失败 2 处；APK 内嵌 `generate_error_reason: NO_SUPPORTED_VCS_FOUND`；唯一可构建源是 HEAD+3 未提交文件 |
| G-REL-01 | 冷构建可离线 | B | `./gradlew.bat --offline --no-daemon --no-build-cache :app:assembleStrictOfflineRelease`（私有 `GRADLE_USER_HOME`） | exit 0 **且** 日志无 `Download`/`No cached version`/`Could not resolve` | **PARTIAL**：HEAD+3 overlay → **exit 0 / 4m02s / 412 tasks**、三条关键词 0 命中；但成功轮是增量（52 executed / 360 up-to-date），单次冷构建未测 |
| G-REL-02 | 构建确定性 | B | 连续两次 clean 构建后 `cmp` 两个 APK | 相同，或差异来源清单已写明 | NOT_RUN（本轮未 clean 重建；会覆盖已绑定的 APK/设备 sha） |
| G-REL-03 | mapping 结构完整 | A | `head -3`；行数；项目类计数 | 三行头齐全；行数>0；项目类>0 | **PASS**：`R8 / 9.3.16 / min_api 23`；**892,986 行**；**3,754** 项目类 |
| G-REL-04 | 混淆真实发生 | A | 抽项目类短名；`check_r04_keep_rules.py` | ≥1 项目类短名；规则门 exit 0 | **PASS**：`cs/ov/y50/z50/kt0`；规则门 exit 0（`-dontobfuscate` 0 命中） |
| G-REL-05 | mapping↔APK 绑定 | A | 取 mapping 短名 → 在 APK 全部 `classes*.dex` 数 `L<name>;` | ≥3 命中；负控 0 | **PASS**：5/5（`cs=1 ov=1 y50=1 z50=2 kt0=1`）；负控 `Lzzzzz9;`=0 |
| G-REL-06 | 校验和归档 | A | `sha256sum` apk/mapping/seeds/usage/configuration → sidecar | 五份 sha 在 sidecar 且与文件一致 | **PARTIAL**：五份 sha 已算（`E-WS5 §1.3`）；sidecar 文件未生成 |
| G-REL-07 | R8 keep 回归 | A | `usage.txt` 三段（WorkDatabase_Impl / CommonComponentRegistrar / ChineseTextRecognizerOptions）`grep -A8` | 三段均无 `void <init>()` | **PASS**：三段均 0 |
| G-REL-08 | 签名变量全或无 | B | `:app:validateReleaseSigning`；缺变量负控 | 齐备 exit 0；缺一 exit 1 且报变量名 | **PASS**：缺省 → **exit 1** 并列出四名；注入四变量后构建 exit 0 |
| G-REL-09 | 产出证书身份 | A/B | `apksigner verify --print-certs --verbose <apk>` | 指纹 == 发布指纹；DN 非调试/一次性名 | **FAIL**：DN `CN=WS43 Ephemeral Local Only, OU=NotForRelease…`；指纹 `105d49aa…`（一次性钥匙） |
| G-REL-10 | 签名方案底线 | A | 同上 verbose | v2=true（minSdk 23 另需 v1=true） | **PASS**：v1=true、v2=true；v3/v4=false（发布说明须写明） |
| G-REL-11 | keystore 可打开 | B | `keytool -list -keystore "$RELEASE_STORE_FILE" -alias "$RELEASE_KEY_ALIAS"` | exit 0 且 `PrivateKeyEntry` | NOT_RUN（门未实现；无生产钥匙可测） |
| G-REL-12 | 16KB ZIP 层 | A | `zipalign -c -P 16 4 <apk>` | exit 0 | **PASS**：exit 0 |
| G-REL-13 | 16KB ELF 层 | A | 程序头解析：`PT_LOAD p_align ≥ 0x4000`；RELRO 余数页无可写 LOAD | 全 PASS | **PASS**：**14/14**（0 FAIL） |
| G-REL-14 | 仅 64 位 ABI | A | ZIP 内 `.so` 清单 | 无 `armeabi-v7a`/`x86` | **PASS**：14 条 `.so` 全 Stored；`arm64-v8a` + `x86_64` |
| G-REL-15 | 16KB 运行时（x86_64） | B | `ps16k_x86_64` → `getconf PAGE_SIZE` → 装包 → 冷启 → logcat | 16384 + install Success + 冷启 ok + 四类 grep 0 | **PASS**：16384；Streamed Install Success；`COLD 1030 ms`；九类签名 0；首页已渲染（截图） |
| G-REL-16 | 16KB 运行时（arm64） | **D** | arm64 真机/宿主开 16KB（`E-WS5 §5` runbook） | 同上 + 三条 native 路径各触发一次 | **BLOCKED**：`FATAL \| Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host` |
| G-REL-17 | AAB 页对齐 | C | `bundletool-all dump config --bundle=<aab>` | `PAGE_ALIGNMENT_16K` | **BLOCKED**（本机无 fat jar；本轮未出 AAB） |
| G-REL-18 | 依赖钉版 | A | `check_dependency_pins.py`；负控 `--expect-kotlin-runtime 2.4.10` | exit 0；负控 exit 1 | **PASS**：exit 0（quickjs 1.0.15 / stdlib 2.3.21）；负控 exit 1（副本内实跑） |
| G-REL-19 | 锁文件覆盖 | A | `check_lockfiles.py`；`git ls-files '*gradle.lockfile' \| wc -l` | 每个 include 有已跟踪锁且配置齐 | **OPEN**：门 exit 0 是跳过态；0 已跟踪 / **13 未跟踪** / `:benchmark` 缺锁（KD-72） |
| G-REL-20 | wrapper 完整性 | A | 比对 `gradle-wrapper.properties` 与官方 `.sha256` | 逐字符相等 | NOT_RUN（本轮未抓官方值） |
| G-REL-21 | release lintVital | A | 读 `lint_vital_return_value/*Release/*/return-value-*.txt` | 值 `0` | **PASS**：`strictOfflineRelease` = 0（本轮新产物；`lintVitalStrictOfflineRelease` 在构建日志中 executed） |
| G-REL-22 | release manifest | A | `check_release_manifest.py`（主树） | exit 0 | **PASS**：9 checked + 4 debug merged，5 debug-only 双向一致 |
| G-REL-23 | 安装冒烟 | B | 见 `E-WS5 §4.2` 命令序列 | install Success + 冷启 ok + 九类签名 0 + 设备 sha 一致 | **PARTIAL**：装包/冷启/渲染/0 签名齐；**三链路（录入/复习/导出）本轮未跑** |
| G-REL-24 | 安装冒烟（16KB） | B | G-REL-23 + `getconf PAGE_SIZE`=16384 | 同上 | **PASS（同 23 的边界）**：`emulator-5620`，16384 |
| G-REL-25 | 设备侧产物身份 | B | `pm path` + 设备内 `sha256sum` 与构建比对 | 逐字符相等 | **PASS**：`a5fa0c4f…3d3a4ef8` 双侧一致 |
| G-REL-26 | 核心三链路 | B | 录入→入库；复习→判词；导出 A4→PDF 落盘 | 三条均产可核结果 | **NOT_RUN**（本轮只做到首页渲染） |
| G-REL-27 | mapping 与版本绑定 | A | sidecar 含 revision/versionCode/versionName/apk.sha/mapping.sha/compiler/cert 指纹 | 七项齐且自洽 | **PARTIAL**：七项 + 来源三件套已算（`E-WS5 §7.2`）；sidecar 文件未生成 |
| G-REL-28 | 16KB native 真装载 | B | `adb logcat \| grep nativeloader` | 每条被验证 `.so` 出现 `Load … !/lib/<abi>/<name>.so … : ok` | **PARTIAL**：本轮冷启未触发三条随包 `.so`（批次 42 的 ML Kit 证据属另一 APK，不可外推） |
| **G-REL-29** | **秘密扫描（新增）** | A | 扫 APK 资产/备份归档/日志中的 keystore 口令、Provider key、用户内容 | 0 命中 | **NOT_RUN**：本轮只做了"仓内跟踪密钥文件 = 0"这一条子检查；无扫描器 |

> **G-REL-29 消灭的具体失败**：`RELEASE.md` 门 6（"无 API key/Keystore secret/用户内容泄漏"）至今**只有人工步骤**，批次 42 的 release 包里带什么没人机器复核。没有这条门时，"release 包里印了 Provider key"只能靠人眼发现。

## 2. 相对上一版的判据变化

1. **G-REL-00 增加 (b) 分支**：归档构建无 `.git`，APK 内嵌 textproto 必然 `NO_SUPPORTED_VCS_FOUND`（本轮实测）。若坚持只认 (a)，归档构建路线会被永久判红；若接受 (b)，sidecar 必须含归档 sha 与 overlay 清单。**这条需要主会话裁定**——本文件只给出两种可选判据，不替主会话选。
2. **G-REL-01 的"成功"定义收紧**：必须同时是"离线 + 无下载 + 冷构建"。本轮只满足前两条。
3. **G-REL-23/24 的"通过"边界**：装包/冷启/渲染 ≠ 三链路；上一版把三链路记在 G-REL-26 是对的，本版维持。

## 3. 发布前必须闭的三件事（按依赖顺序）

1. **提交 HEAD 缺的 3 个文件**（`KnowledgeQuizTasks.kt` / `StudyDatabasePort.kt` / `TutorVisualPort.kt`），让 `git archive HEAD` 可编译；然后重跑 G-REL-00/01/02。
2. **生产签名**（G-REL-09/11）：按 `E-WS5 §6` runbook 注入四变量，并在 Play App Signing 上二选一。
3. **arm64 16KB**（G-REL-16）：按 `E-WS5 §5` 等待清单在真实 arm64 设备上跑三条 native 路径。
4. 顺带：G-REL-19（KD-72 写锁）与 G-REL-17（AAB/bundletool）仍 open/BLOCKED，不阻塞"出包"，阻塞"上架"。

## 4. 与 `RELEASE.md` 的门映射

| RELEASE.md | 对应门 |
|---|---|
| 1 双 flavor assemble/单测/lint | G-REL-01/21 + 批次单测门（非本轨） |
| 2 assembleRelease/bundle + R8 | G-REL-01/03/04/05/07 |
| 3 merged release manifest | G-REL-22 |
| 4 Room 迁移矩阵 | 不属本轨（WS4 设备矩阵） |
| 5 备份往返 | 不属本轨（WS4） |
| 6 无密钥/用户内容泄漏 | **G-REL-29（本轮新增，NOT_RUN）** |
| 7 版本号 | G-REL-00 + `output-metadata.json`（本轮 versionCode 2 / 0.2.0-offline） |

## 5. 回链

- 证据：`docs/research/evidence/2026-11-05-batch-43/ws5-release-prefight.md`（E-43-WS5-01，§1–§8 逐项命令与退出码）。
- 上一版：`docs/research/2026-11-04-release-gates-draft.md`；批次 42 实跑：`docs/research/2026-11-04-ws8-release-prefight.md`。
- 研究：`docs/research/2026-11-05-clean-tree-release.md`（R4，§4.1 方案与 F1–F14 失败模式）。
- 计划：`.zcode/plans/plan-2026-11-05-batch-43.md:70-78`（WS5 37–44）。
- **本文件不改 `docs/current/release-gates.md`**（热区）；若要并入，按批次 43 WS6 的契约并入协议另行处理。
