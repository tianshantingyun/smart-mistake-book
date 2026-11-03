# 发版门清单（`release-gates` 草案，批次 42 WS8-70）

- 日期：2026-11-04；来源：R6 `docs/research/2026-11-04-release-readiness.md` §8（25 条基线）+ 本轮 WS8 实跑（`docs/research/2026-11-04-ws8-release-prefight.md`）
- 用途：供 CI / 发布手册逐条执行；“状态”列**只写本轮实测**，未跑一律 `NOT_RUN`，外部条件写 `BLOCKED`。
- 类别：**A** 本机即可跑（只读/命令行）/ **B** 门禁阶段（Gradle 或设备）/ **C** 需发布者提供 / **D** 外部阻塞。
- 门 ID 冻结：新增门只能追加（G-REL-29+），不得复用旧号改语义。

## 0. 一页结论

- 本轮**通过**：G-REL-03/04/05/07/08/10/12/13/14/15/18/22/23/24/25（+ G-REL-21 部分）。
- 本轮**红**：G-REL-00（dirty 树）、G-REL-09（非生产证书）。
- **BLOCKED**：G-REL-16（arm64 16KB）、G-REL-17（bundletool-all）、G-REL-19（KD-72 写锁）。
- **NOT_RUN**：G-REL-01/02/06（sidecar 文件化）/11/20/26（partial）/27/28（partial）。
- **不得发布的直接理由**：G-REL-00 红 + G-REL-09 红 + G-REL-19 open。三者任一未闭，APK 只能当“流水线验证包”。

## 1. 门表

| ID | 门 | 类别 | 命令（要点） | 判据 | 本轮状态 |
|---|---|---|---|---|---|
| G-REL-00 | 产物↔源 | A | `unzip -p <apk> META-INF/version-control-info.textproto`；`git rev-parse HEAD`；`git status --porcelain \| wc -l` | 产物 revision == HEAD **且** 树 dirty=0 | **FAIL**：revision=`faab17f5`==HEAD，但树 634 dirty（构建时亦脏） |
| G-REL-01 | 冷构建可离线 | B | `./gradlew --no-daemon --offline --no-build-cache clean :app:assembleRelease` | exit 0 | NOT_RUN |
| G-REL-02 | 构建确定性 | B | 连续两次 clean 构建后 `cmp` 两个 APK | 相同，或差异来源清单已写明 | NOT_RUN |
| G-REL-03 | mapping 结构完整 | A | `head -3`；`wc -l`；`grep -c '^com\.tingyun\.smartmistakebook\..* ->'` | 三行头齐全；行数>0；项目类>0 | **PASS**：`R8 / 9.3.16 / min_api 23`；893,641 行；3,760 类 |
| G-REL-04 | 混淆真实发生 | A | `grep -m3 '… -> [a-z][a-z0-9]*:'`；`check_r04_keep_rules.py` | ≥1 项目类短名；规则门 exit 0 | **PASS**：`ds/pv/z50/a60/lt0`；规则门 exit 0 |
| G-REL-05 | mapping↔APK 绑定 | A | 取 mapping 短名 → 在 APK 全部 `classes*.dex` 数 `L<name>;` | ≥3 命中；负控 0 | **PASS**：5/5 命中，负控 0 |
| G-REL-06 | 校验和归档 | A | `sha256sum` apk/mapping/seeds/usage/configuration → 写 sidecar | 五份 sha 在 sidecar 且与文件一致 | **PARTIAL**：五份 sha 已算出并记录；sidecar 文件本身未生成 |
| G-REL-07 | R8 keep 回归 | A | `usage.txt` 三段（WorkDatabase_Impl / CommonComponentRegistrar / ChineseTextRecognizerOptions）`grep -A8` | 三段均无 `void <init>()` | **PASS** |
| G-REL-08 | 签名变量全或无 | B | `:app:validateReleaseSigning`；负控去掉一个变量 | 齐备 exit 0；缺一 exit 1 且报变量名 | **PASS**：默认 4/4 → exit 1 并列出四名；注入四变量后构建 exit 0 |
| G-REL-09 | 产出证书身份 | A/B | `apksigner verify --print-certs --verbose <apk>` | `certificate SHA-256` == 发布指纹；DN 非 `R04 Local`/`CI Dry Run`/`Android Debug`/`WS8 Ephemeral` | **FAIL**：DN=`CN=WS8 Ephemeral Local Only…`，指纹 `9dc07e9f…`（一次性钥匙） |
| G-REL-10 | 签名方案底线 | A | 同上 verbose | v2=true（minSdk 23 另需 v1=true） | **PASS**：v1=true、v2=true；v3/v4=false（须在发布说明写明） |
| G-REL-11 | keystore 可打开 | B | `keytool -list -keystore "$RELEASE_STORE_FILE" -alias "$RELEASE_KEY_ALIAS"` | exit 0 且 `PrivateKeyEntry` | NOT_RUN（门尚未实现；本轮钥匙仓外生成，未跑该断言） |
| G-REL-12 | 16KB ZIP 层 | A | `zipalign -c -P 16 4 <apk>`（两 flavor） | exit 0 | **PASS**（两个 APK 均 exit 0） |
| G-REL-13 | 16KB ELF 层 | A | 程序头解析：`PT_LOAD p_align ≥ 0x4000`；RELRO `end%0x4000==0` 或余数页无可写 LOAD | 全 PASS；32 位负控 FAIL | **PASS**：14/14（5 mod=0、9 exempt）；负控 `armeabi-v7a/libmlkit…` FAIL（`p_align=0x1000`） |
| G-REL-14 | 仅 64 位 ABI | A | `unzip -l <apk> \| grep '\.so$'` | 无 `armeabi-v7a`/`x86` | **PASS**：7+7，均 64 位且 Stored |
| G-REL-15 | 16KB 运行时（x86_64） | B | `ps16k_x86_64` 冷启 → `getconf PAGE_SIZE` → 装包 → 冷启 → logcat | `16384` + install Success + 冷启 ok + 四类 grep 0 | **PASS**：16384；COLD 1058/876 ms；`Status: ok`；`linker/RELRO/SIGSEGV/UnsatisfiedLinkError` 0 |
| G-REL-16 | 16KB 运行时（arm64） | **D** | 见 runbook（arm64 真机开 “Boot with 16KB page size”） | 同上 + 三条 native 路径各触发一次 | **BLOCKED**（无 arm64 设备/宿主） |
| G-REL-17 | AAB 页对齐 | C | `bundletool-all dump config --bundle=<aab> \| grep alignment` | `PAGE_ALIGNMENT_16K` | **BLOCKED**（本机无 fat jar；缓存 1.18.3 非可执行） |
| G-REL-18 | 依赖钉版 | A | `check_dependency_pins.py`；负控 `--expect-kotlin-runtime 2.4.10` | exit 0；负控 exit 1 | **PASS**：exit 0（quickjs 1.0.15 / stdlib 2.3.21）；负控 exit 1 |
| G-REL-19 | 锁文件覆盖 | A | `check_lockfiles.py`；`git ls-files '*gradle.lockfile' \| wc -l` | 每个 include 有已跟踪锁且配置齐 | **OPEN**：门 exit 0 是跳过态；0 tracked / 14 untracked / `:benchmark` 缺锁；写锁两次失败（见 §2） |
| G-REL-20 | wrapper 完整性 | A | 比对 `gradle-wrapper.properties:4` 与官方 `.sha256` | 逐字符相等 | NOT_RUN（本轮未重新抓官方值；仓内 pin 存在） |
| G-REL-21 | release lintVital | A | 读 `lint_vital_return_value/*Release/*/return-value-*.txt` | 值 `0` | **PASS（部分）**：`localFirstRelease`=0（本轮 11:10 新产物）；`strictOfflineRelease`=0（任务 UP-TO-DATE，文件为 10-30） |
| G-REL-22 | release manifest | A | `check_release_manifest.py` | exit 0 | **PASS**（9 checked + 4 debug merged，5 个 debug-only 双向一致） |
| G-REL-23 | 安装冒烟 | B | 见 §3 命令序列 | install Success + 冷启 ok + 九类签名 0 + 设备 sha 一致 | **PASS**（在 16KB 设备上；纯 4KB 设备未单跑） |
| G-REL-24 | 安装冒烟（16KB） | B | G-REL-23 + `getconf PAGE_SIZE`=16384 | 同上 | **PASS**（emulator-5620） |
| G-REL-25 | 设备侧产物身份 | B | `pm path` + 设备内 `sha256sum` 与 sidecar/构建产物比对 | 逐字符相等 | **PASS**：`724ce718…342b44` 双侧一致 |
| G-REL-26 | 核心三链路 | B | 录入→入库；复习→判词；导出 A4→PDF 落盘 | 三条均产可核结果 | **PARTIAL**：三条全通（见 prefight §2.3）；但 JS/LiteRT 路径未触发 |
| G-REL-27 | mapping 与版本绑定 | A | sidecar 同时含 revision/versionCode/versionName/apk.sha/mapping.sha/compilerVersion/cert 指纹 | 七项齐且互相自洽 | **NOT_RUN**：字段已算齐，sidecar 文件未生成（同 G-REL-06） |
| G-REL-28 | 16KB native 真装载 | B | `adb logcat \| grep nativeloader` | 每条被验证 `.so` 出现 `Load … !/lib/<abi>/<name>.so … : ok` | **PARTIAL**：`libmlkit_google_ocr_pipeline.so` 已证（+ `OCR process succeeded via visionkit pipeline`）；`libquickjs.so`/`libLiteRt.so` 未触发 |

## 2. KD-72 写锁的阻塞明细（G-REL-19 的依据）

| 尝试 | 失败配置 | 原文要点 |
|---|---|---|
| A | `:app:localFirstBenchmarkCompileClasspath` | 无法在 `:core:data:releaseApiElements` 等变体间选择；benchmark 构建类型在库模块无对应变体 |
| B（排除 Benchmark 后） | `:app:localFirstDebugAndroidTestCompileClasspath` | 无法在 `:app:localFirstDebugApiElements`（`android-app-symbol-for-data-binding` 等）变体间选择 |

两次均 exit 1、**0 个 lockfile 写出**；隔离副本 = `git archive HEAD`（3,320 文件、0 旧锁），真树未被写入。

## 3. 安装冒烟最小命令（G-REL-23/24/25）

```bash
A=/c/Android/Sdk/platform-tools/adb.exe; S=emulator-5620; export MSYS_NO_PATHCONV=1
"$A" -s $S shell getconf PAGE_SIZE                      # 16KB 门要求 16384
"$A" -s $S uninstall com.tingyun.smartmistakebook.offline   # 换签名时必须先卸
"$A" -s $S install -r app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
"$A" -s $S shell sha256sum "$("$A" -s $S shell pm path com.tingyun.smartmistakebook.offline | sed 's/package://' | tr -d '\r')"
"$A" -s $S shell am force-stop com.tingyun.smartmistakebook.offline; "$A" -s $S logcat -c
"$A" -s $S shell am start -W -n com.tingyun.smartmistakebook.offline/com.tingyun.smartmistakebook.MainActivity
"$A" -s $S logcat -v threadtime -d > build/release-smoke/coldstart.txt
grep -cE 'FATAL EXCEPTION|StartupException|NoSuchMethodException|ClassNotFoundException|NoClassDefFoundError|UnsatisfiedLinkError|MlKitException|SIGSEGV|ANR in' build/release-smoke/coldstart.txt   # 期望 0
```

三链路（人工/UI 自动化，判据见 prefight §2.3）：录入一条 → 错题本出现 `1 道题`；开始今日复习 → 判词写入 → 根页 `今日已完成`；详情 → 导出 A4 → `已完成 · 共 N 页` → 拉回文件校验 `%PDF`。

## 4. 与 `RELEASE.md` 现有 7 条门的映射

| RELEASE.md | 对应门 |
|---|---|
| 1 双 flavor assemble/单测/lint | G-REL-01/21 + 批次单测门（非本轨） |
| 2 assembleRelease/bundle + R8 | G-REL-01/03/04/05/07 |
| 3 merged release manifest | G-REL-22 |
| 4 Room 迁移矩阵 | 不属本轨（WS10 设备矩阵） |
| 5 备份往返 | 不属本轨（WS10） |
| 6 无密钥/用户内容泄漏 | **无机器门**（建议 G-REL-29：扫 APK/AAB/备份归档） |
| 7 版本号 | G-REL-00 + `output-metadata.json` 比对 |

## 5. 建议新增（未实现，仅在需要时补）

- **G-REL-29 秘密扫描**：APK/AAB/backup 归档中不得出现 keystore 口令、Provider key、用户内容；RELEASE.md 门 6 目前只有人工。
- **G-REL-30 sidecar 生成**：`tools/ci/release_artifacts.py`（只读产物，算 sha/绑定/证书），把 G-REL-06/27 从 PARTIAL 变 PASS。**仅在 CI 需要自动归档时再加**——本轮证据已可人工复核，先不写多余脚本。
