# 批次 37 设备证据索引（2026-10-31）

- **版本**：v2（2026-10-31 证据落盘员更新，**supersedes v1"仅 KD-77 迁移件"版**；v1 全部内容保留于 §1/§4/§5，未删改其判定；更新仅两处：头部改为全目录映射 + §4-7 一句扫描自引用字面量改为非匹配表述，见 §6）。v1→v2 之间由设备轨新增 `step-device-track.md`（设备轨自述"不改动 T5 任何文件"，该文件头目录注记在案）。
- 记录：`docs/research/2026-10-31-batch-37-record.md`（**待台账轨落笔**；本索引为其 §4 的引用对象）
- 计划：`.zcode/plans/plan-2026-10-31-batch-37-v01-isolated-r04-k6.md`（§1.3 主会话裁定 :14-22）
- 判据：`docs/research/2026-10-31-batch-37-device-criteria.md`（518 行，已完整读）
- 规范：`docs/research/2026-10-26-device-evidence-convention.md` v1
- 工作树 HEAD：`5ee1f90f7e2411a6c1865890f6bf5da21f2b57aa`
- 本目录体积（v2 实测）：8 文件（2 `.md` + 6 `.txt`）/ 118 KiB（`du -sk`）；另有 `.mimosa/`（Mimosa hook 状态，`.gitignore:29` 忽略，**非证据**）；全 `docs/research/evidence/` = 1305 KiB（< 8 MiB 预算）
- 原始全量日志：`build/device-track-batch37/`（gitignore 内不入仓；R4 摘要只认本目录）

## 0. 本批裁决（R-04 / V-01 / A-10 / KD-77）

| 项 | 裁决 | 一句话依据（E-ID 见 §2） |
|---|---|---|
| **R-04** | **不关闭，阻塞维持** | K1–K5 绿（E-10/11/12）+ 三值预检 ②=1 → 分支 P2 → 选项 B（计划 §1.3 在案明文，E-13）；K6 实测 = `Process crashed before executing the test(s)`（0 例跑）+ 三类签名 **4**（NoSuchMethodError×4 + FATAL×1；NoClassDef/ClassNotFound 均 0），判据期望 `OK (2 tests)` + 签名 0（E-14）。红因 = 判据 §1.7 第 1 行逐字对号：**机制①（j$ L8 改名碰撞）**——`No direct method <init>(Ljava/util/Map;)V in class Lj$/util/g;`（declaration 在 offline.test base.apk!classes2.dex，WorkManager 启动栈）；机制②（androidx.tracing.Trace）0 命中 → keep 扩充（`b31b5d02`）对机制②有效。处置 = 按"阻塞"回报；禁改判据/禁 -dontobfuscate/禁 internal 变体/禁删例——全程未触。**关闭前置 = 主会话在两合规路径间裁定**（选项 A Keeper/L8 结构解锁，触 `settings.gradle.kts` 热区 vs 维持批次 36 预检硬门口径）；本轨不代裁 |
| **V-01** | **设备绿（app 版 3/3）→ 建议关闭，附两项在案挂账** | 真隔离引擎语义满足：`VisualJsClientEngine(context)`（`V01VisualChainInstrumentedTest.kt:501`）、`QuickJsVisualExecutionEngine` 代码引用 0（仅 KDoc :68）；三例 = 生成腿零派发见证（:136）/生产装配负控（:247）/冻结重放+重开库重绘（:289），设备 `OK (3 tests)` == 构建前计数 3（E-08）；断言位未缩水（:188-206 实读，reserved/used=0 + attempt method='JS' ∧ reservation_id IS NULL）。挂账：① 判据 §2.1 第 3 条的④（PDF 腿）**本批无设备证据**——PDF/painter/restore 腿因 core:data internal 符号跨模块不可达留 core/data 降级变体（其 KDoc :118-124 实读），该变体已知 2/4 夹具红挂账且明示不进套件单；② 降级变体修复（R1 §3.3 方案 B）待主会话裁定。若按"④ 必须有设备证据"的严格口径，V-01 = PARTIAL（3/4 子项）——**最终收口裁定归主会话/台账轨** |
| **A-10** | **绿（3/3 含终答位子门）→ 建议关闭** | 夹具经生产链路落 QUEUED：`createModelTask`（`FigureDispatchBudgetBoundaryInstrumentedTest.kt:230`）→ `transitionModelTask`（:239-244，`nextStatus = ModelTaskStatus.QUEUED`），断言"夹具任务必须经生产迁移进入 QUEUED"（:252）——无端口绕行；三例设备 `OK (3 tests)` == 计数 3（E-09）；第 3 例（:127 `theChatFinalAnswerSlotSurvivesFigureBudgetExhaustion`）证明"耗尽→终答位保留"子门（applied=true / logicalDispatchCount=6 / budgetExhausted=false 断言面在树） |
| **KD-77** | 关闭证据齐（入仓件 E-77-01…06 + §4 映射） | 关闭裁决正文在 `docs/known-defects.md` KD-77 条目 2026-10-31 批次 37 块（该文件 M 在飞，台账轨落笔；本目录只交证据） |

## 1. KD-77 证据入仓（T5 台账轨；v1 索引原文保留）

- **性质**：台账轨（T5）证据目录内容之一。**【v2 注】v1 原文为"本目录无新设备执行、零 Gradle、零 git 写；当前内容 = KD-77 API 23 最小冒烟证据自 `tmp/kd77-api23-smoke/` 的迁移件（byte-identical）+ 本索引"——该自述针对 T5 落盘时点（20:23）；其后设备轨于 22:33 入 `step-device-track.md`（§2），本索引 v2 为全目录映射。**
- **权威裁定**：批次 37 计划 §1.5——"KD-77 关闭：把 `tmp/kd77-api23-smoke/` 五件套证据迁入 `docs/research/evidence/2026-10-31-batch-37/`（入仓），台账落关闭裁决（跨账本分歧以此为准）"（`.zcode/plans/plan-2026-10-31-batch-37-v01-isolated-r04-k6.md:20`）。
- **证据原始产生**：批次 36 T6 API 23 最小冒烟（2026-10-30 06:52–06:54，`emulator-5596`）；当时点记录 `docs/research/2026-10-30-batch-36-record.md` §3.6（证据落 `tmp/`，按纪律不入仓；本目录为其入仓迁移件）。
- **落盘会话**：2026-10-31 T5 台账（只读核对 + 文件迁移 + 文档改动）。
- 工作树 HEAD：`5ee1f90f7e2411a6c1865890f6bf5da21f2b57aa`（`git rev-parse HEAD` 本会话实跑）；工作树 dirty **696** 项（`git status --porcelain | wc -l` 本会话实跑；批次 37 基线时点 688，他线在飞增量未逐项追认 → 本目录不对 696 逐条背书）。
- E-ID 分配（v2 新增）：`00-prestate.txt`=E-77-01、`01-install.txt`=E-77-02、`02-cold-start.txt`=E-77-03、`03-logcat-full.txt`=E-77-04、`04-signature-scan.txt`=E-77-05、`05-post-state.txt`=E-77-06（映射同 §4 表，不改动 v1 表）。
- **目录形态注记（v2）**：计划 §1.5 原文为"迁入 `…/2026-10-31-batch-37/`"（目录根，**无子目录**）；六件保持 T5 落位不动（移动会破坏 v1 引用）。六件为 `.txt`（byte-identical 迁移完整性取舍）+ `03` 416 行 > 400 行——偏离规范 §2.2/§3.1，已在 §7-5 登记，不拆分。

### 1.1 迁移与逐件身份（sha256 迁移前后相等；v2 重算逐件相等）

迁移命令（T5 实跑，`cp` 六件 → 目标目录）：

```bash
mkdir -p docs/research/evidence/2026-10-31-batch-37
cp tmp/kd77-api23-smoke/00-prestate.txt tmp/kd77-api23-smoke/01-install.txt \
   tmp/kd77-api23-smoke/02-cold-start.txt tmp/kd77-api23-smoke/03-logcat-full.txt \
   tmp/kd77-api23-smoke/04-signature-scan.txt tmp/kd77-api23-smoke/05-post-state.txt \
   docs/research/evidence/2026-10-31-batch-37/
sha256sum tmp/kd77-api23-smoke/*.txt docs/research/evidence/2026-10-31-batch-37/*.txt
```

| 文件 | 字节 | sha256（SRC = DST 逐件相等；v2 重算同值） |
|---|---:|---|
| `00-prestate.txt` | 513 | `1b01de5edd979866b8af63095d0391ea2c60d7bc97cae4853aee580ca822611d` |
| `01-install.txt` | 471 | `fbfb0075185774bb7c9a6be0ab566363417f4781b52caa11ba5a9ca065ad4908` |
| `02-cold-start.txt` | 623 | `00597484e178a31441695d53d2cebf72ecc1df65f793ed0e8e9aa8093a866d28` |
| `03-logcat-full.txt` | 58,563 | `c1d1fea3d590a486ecf3e7a72c0d81c7c462a1e63f233fbe7fd23ee83c83116e` |
| `04-signature-scan.txt` | 232 | `14619a984283544b800e62ef05a1847b00fdf088c84dfd136368643aed3290ba` |
| `05-post-state.txt` | 369 | `6c826e91ffc95ce0f2afac49596b9f1b4e9ee9b451108c0bc210746165dcf745` |

目录体量：6 文件 + 本索引，60,989 B（迁移后未加索引时 `du -sb` 实测；全 `docs/research/evidence/` 树远低于 8 MiB 预算）。**口径注记**：计划 §1.5 原文"五件套"，`tmp/kd77-api23-smoke/` 盘上实为 **6 文件（00–05）**——全量迁移，以 6 文件为准（批次 37 基线 `docs/research/2026-10-31-batch-37-baseline.md:10` 已登记同口径差）。

## 2. 设备轨证据映射（`step-device-track.md`；E-ID v2 分配）

| E-ID | 步骤 | 门/判据 | 设备 | 判定 | 证据（本目录相对路径:行 + raw 文件） |
|---|---|---|---|---|---|
| E-01 | 环境与设备再登记 | 判据 §4.7 | `emulator-5562` | PASS（信息性） | `step-device-track.md:15-24`；raw `build/device-track-batch37/device-registration.txt`（SDK=34/ABI=x86_64/PAGE_SIZE=4096/fingerprint google/…UE1A.230829.050/12077443） |
| E-02 | 一致性门（构建前） | 判据 §4.2 | — | PASS | `step-device-track.md:42-49`；raw `s71-static-compare.txt`（RESULT=PASS）、keep 门 OK×3/EXIT=0 |
| E-03 | 源快照门（含 core/model） | 判据 §4.1 | — | PASS | `step-device-track.md:51-58`；raw `src-sha-before/after.txt`（1175 文件）diff 空、`r04-src-sha-*` diff 空、`keep3-sha-*` diff 空、`rerun-src-sha-*` diff 空（落盘员逐一 diff 复算=空） |
| E-04 | 构建 + 四包身份 | 判据 §4.3 | — | PASS | `step-device-track.md:55-60`；raw `build-debug.log`（BUILD_EXIT=0）、`four-apk-sha.txt` |
| E-05 | 安装 + 设备侧 sha + asset/dex 门 | 判据 §4.4–4.6 | `emulator-5562` | PASS | `step-device-track.md:61-67`；raw `install-debug.txt`（首包 UPDATE_INCOMPATIBLE→卸重装）、`install-debug-retry.txt`、`on-device-sha.txt`（四包逐一相等）、`asset-71-sha.txt`（`4a13d9d0…81d94`） |
| E-06 | 20 步套件串行（step01–18） | 判据 §三（15 套件不回退） | `emulator-5562` | PASS | `step-device-track.md:71-97`；raw `run20.sh`、`step-summaries.txt`、`pre-build-counts.txt`（逐值相等）、`signature-scan-20steps.txt`（全 0） |
| E-07 | step14 首跑 2 红归因（环境类）+ 舞步重跑 | 判据 §五/§3.2 | `emulator-5562` | PASS（舞步后） | `step-device-track.md:99-112`；raw `step14-isolation.log:328`（Tests run: 15, Failures: 2，红例 :181/:236）、`step14-isolation-rerun.log`（OK (15 tests)，Time 82.778）、同源绿参照 `build/device-track-batch36/step14-isolation.log` 尾行 OK (15 tests)（落盘员实读） |
| E-08 | step19 V-01 app 版 | 判据 §2.1 | `emulator-5562` | PASS（3/3） | `step-device-track.md:93`；raw `step19-v01-app.log`（OK (3 tests)）；源计数 3 = `pre-build-counts.txt` 末二行 |
| E-09 | step20b A-10 修复版 | 判据 §2.2 | `emulator-5562` | PASS（3/3 含终答位子门） | `step-device-track.md:94`；raw `step20b-a10-budget.log`（OK (3 tests)） |
| E-10 | K1 红基线 | 判据 §1.3 | `emulator-5562` | PASS（红因=签名门） | `step-device-track.md:116-128`；raw `r04-step1-red.log:1-2`（Permission Denial…signature matching，grep 计 2）；首次无效红登记 :122-124 |
| E-11 | K2 release 双产物 | 判据 §1.3 | — | PASS | `step-device-track.md:130-135`；raw `r04-step2-build.log`（BUILD SUCCESSFUL 51s）、`r04-two-apk-sha.txt`（`9ec35dba…`/`390ccec0…` ≠ 批次 36 窗口值） |
| E-12 | K3/K4/K5 | 判据 §1.4 | — | PASS | `step-device-track.md:137-145`；raw `r04-k3-main.txt`/`r04-k3-test.txt`（同证书 SHA-256 `9fd478d9…33969`）、`r04-k4-keepgate.txt`（OK×3）、`r04-k4b-mapping-lines.txt`（797123）、`r04-k5a-so.txt`（双 ABI）、`r04-k5b-count.txt`（289）/`r04-k5b-fallback-count.txt`（35） |
| E-13 | 三值预检 + 分支 P2 → 选项 B | 判据 §1.5 | — | PASS（②不过=预测形态复现；分支裁定在案） | `step-device-track.md:147-161`；raw `r04-three-value-precheck.txt`（①=1/②=1/③=1）、`r04-3-dexdump-class.txt:1`（`Landroidx/tracing/Trace;` 类定义）+`r04-3-dexdump-methods.txt`（3 签名方法） |
| E-14 | **K6（红）** | 判据 §1.6/§1.7 | `emulator-5562` | **FAIL → 阻塞** | `step-device-track.md:163-182`；raw `r04-k6.log`（Process crashed + `NoSuchMethodError … Lj$/util/g;`，classes2.dex，WorkManager 栈）、`r04-k6-signature-count.txt`（4）、`r04-k6-logcat.txt`（FATAL 1/NoSuchMethod 4/NoClassDef 0——落盘员逐类复算） |
| E-15 | 步 5 回归 + 步 6 恢复 debug 态 | 判据 §1.8 | `emulator-5562` | PASS | `step-device-track.md:184-191`；raw `r04-step5-regression.log`（BUILD SUCCESSFUL）、`r04-step6-uninstall.txt`（4 卸 4 装 Success） |
| E-16 | 全会话签名收口（22 份 logcat × 9 类） | 判据 §3.3 | — | PASS | `step-device-track.md:193-199`；raw `final-signature-sweep.txt`（step 系全 0、K1 red 0、K6=5） |
| E-17 | 设备态独立复核（落盘员 v2 实跑） | 步 6 收口声明 | `emulator-5562` | PASS | 本索引 §5 命令⑤：在装主包 sha256 = `59b6d444…d73f`（= E-15 舞步后绿轮绑定包） |

## 3. 产物身份（R4 §C3）

| 产物 | 字节来源 | sha256 | 用途 |
|---|---|---|---|
| app-strictOffline-debug.apk | `four-apk-sha.txt:1` | `070b4485290b1388bd70ed23d144e73e4df72cc83f3b5e8adf8575b16c71d6e1` | 20 步轨（首装包；舞步后重打包为 `59b6d444…`，zip 非确定性，`step-device-track.md:111-112`） |
| app-strictOffline-debug-androidTest.apk | `four-apk-sha.txt:2` | `bba75fa32c4b7c99650ba7e5a5c95258a73329f94cb2035c12be49d7cefea3dd` | 20 步轨 |
| data-debug-androidTest.apk | `four-apk-sha.txt:3` | `957b53a583c57cf709cea9e2ce847cd679a88a3dd9640fea589f66d373c6d88b` | 20 步轨 + A-10 |
| database-debug-androidTest.apk | `four-apk-sha.txt:4` | `14240f5aed913cbeb59f49a16b6d5b9467999f982aa8e2b4d46a7cdc864034a7` | 20 步轨 |
| app-strictOffline-release.apk | `r04-two-apk-sha.txt:1` | `9ec35dbac5e0e4b7ca15d91387f794c022b9f37492d80c930672c22a5f11ea0b` | R-04 K1–K6 |
| app-strictOffline-release-androidTest.apk | `r04-two-apk-sha.txt:2` | `390ccec09a161cd3f707c6f57e17fd10e8636e37854ff588a7e150c9cca98801` | R-04 K1–K6 |
| 两 release 证书 | `r04-k3-main.txt:2`/`r04-k3-test.txt:2` | 同 `9fd478d96b3e8f6f67e7a2a28a25a36b665cbe56edde6f8c82ea6f6a1f633969` | K3 |
| 71.json（asset） | `asset-71-sha.txt` | `4a13d9d0b5ee561f088142267afe0a97890745ff66d57ae2b1191bef47881d94` | schema 门（与批次 35/36 同值） |

## 4. 文件 → 证明内容（KD-77 关闭条件逐条映射；v1 原样保留）

| 关闭条件（KD-77 Reopen condition） | 证据（本目录 文件:行） |
|---|---|
| 设备/镜像 | `00-prestate.txt:2`（serial `emulator-5596`）、`:3-5`（SDK 23 / Android 6.0 / `x86_64,x86`）、`:7`（`image.sysdir.1=system-images\android-23\default\x86_64\`，即批次 36 记录的 `api23_smoke64` 形状） |
| 装机（修复后 APK，干净装机） | `01-install.txt:3-4`（清卸 2 旧包 Success×2）、`:7`（APK sha256 `1593b25ae33d10dc…7dab`）、`:11-12`（`Success` / `INSTALL_EXIT=0`）；`02-cold-start.txt:15`（`firstInstallTime=2026-10-29 22:52:44` = 冒烟当场，非残留安装） |
| 冷启动 | `02-cold-start.txt:3`（`Status: ok`）、`:6`（`TotalTime: 1611` ms）、`:11`（`mResumedActivity=…MainActivity`）、`:9`（`AM_START_EXIT=0`） |
| 四类崩溃签名 0（logcat 全量） | `04-signature-scan.txt:2-9`（FATAL EXCEPTION / ANR / NoSuchMethodError / NoClassDefFoundError 各 `0 hits`）；T5 本会话对 `03-logcat-full.txt`（416 行）逐类 `grep -c` 复算：五类（含 UnsatisfiedLinkError）各 **0**（命令与输出见 §5） |
| APK 指纹与来源 | `05-post-state.txt:3-4`（宿主侧 sha256 全值 + provenance 标注）；与设备侧批次 35 记录 `docs/research/evidence/2026-10-30-batch-35/step-device-track.md:127`（`1593b25ae33d10dc…`）同值 → 确为**修复后**包（批次 35 绿绑定），非批次 30 旧包（`05a5b8bd…`） |

## 5. 一手核验命令与输出

### 5.1 T5 会话（v1 原样保留）

```bash
# ① logcat 四类签名 + 附加两类逐类复算（03-logcat-full.txt = 416 行，wc -l 实测）
wc -l docs/research/evidence/2026-10-31-batch-37/03-logcat-full.txt
# → 416
grep -c "FATAL EXCEPTION"      03-logcat-full.txt   # → 0
grep -c "ANR"                  03-logcat-full.txt   # → 0
grep -c "NoSuchMethodError"    03-logcat-full.txt   # → 0
grep -c "NoClassDefFoundError" 03-logcat-full.txt   # → 0
grep -c "UnsatisfiedLinkError" 03-logcat-full.txt   # → 0
```

同批台账动作（KD-73 回链）的一手核验（输出登记于 `docs/known-defects.md` KD-73 回链块；`tmp/kd73-stdlib-scan/run-final.log` 为本机材料不入仓，脚本在树可复跑）：

```bash
PYTHONPATH=tools python -I -m unittest discover -s tools/tests -p "test_check_stdlib_compat.py" -t tools
# → Ran 18 tests in 0.021s  OK
python -I tools/ci/check_stdlib_compat.py
# 尾行 → OK: 0 unresolved stdlib member references across 23847 unique kotlin/* refs (329 artifacts; 11 coordinates unscanned)
# 退出码 → 0
```

### 5.2 证据落盘员 v2 会话增补（2026-10-31，全部实跑）

```bash
# ① KD-77 六件 sha 重算（§1.1 表逐件同值）
sha256sum docs/research/evidence/2026-10-31-batch-37/0*.txt
# ② K6 三类签名逐类复算（r04-k6-logcat.txt）
grep -c "FATAL EXCEPTION" …; grep -c "NoSuchMethodError" …   # 1 / 4（NoClassDef 0、ClassNotFound 0）
# ③ 三窗口源快照 diff 复算（均空）
diff src-sha-before.txt src-sha-after.txt; diff r04-src-sha-before.txt r04-src-sha-after.txt
diff rerun-src-sha-before.txt rerun-src-sha-after.txt; diff keep3-sha-before.txt keep3-sha-after.txt
# ④ 同源绿参照 + T2/T3 落树形态
tail -4 build/device-track-batch36/step14-isolation.log          # OK (15 tests)
grep -n "@Test" app/src/androidTest/…/visualjs/V01VisualChainInstrumentedTest.kt        # :135/:246/:288
grep -n "@Test" core/data/src/androidTest/…/visual/FigureDispatchBudgetBoundaryInstrumentedTest.kt  # :57/:88/:126
git status --porcelain -- app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/    # 空
# ⑤ 设备态独立复核（只读；步 6 debug 态恢复收口声明的实证）
adb devices -l                                                   # 四台在线（5562/5566/5584/5596）
adb -s emulator-5562 shell pm path com.tingyun.smartmistakebook.offline
adb -s emulator-5562 shell sha256sum <pm path 结果>
# → 59b6d44407857144a0c47fa0fe5ad9d02438606bb6365e5d51c9952921a6d73f（= E-15 绑定值）
```

## 6. 脱敏与去噪（规范 §4.4；v2 实跑，逐模式独立执行）

| 模式 | 结果 | 处置 |
|---|---|---|
| 密钥（`sk-…`/`AIza…`/`ghp_…`/`Bearer …`） | **0 hits（v2 终态）** | 曾有 1 hit = 文档文件名清单中 `model-task-`+`contracts` 拼接后含与密钥模式碰撞的子串（t-a-s-k 的 sk + 连字符 + contracts）——**假阳性**，逐字人工确认非密钥；v1 原文行与 v2 修正稿均因此改写为分离词块（§7-8），重跑确认 0 |
| 凭据赋值（规范 §4.4 第 2 模式：api key/password/token 类赋值语句） | 0 hits | — |
| 邮箱 | 0 hits | — |
| 主目录路径 | 0 hits（v2 生效后） | v1 版 1 hit = §4-7 行自引用主目录字面模式；v2 将该句改为非匹配表述（`C:` 盘符 + Users 通配的描述性文字），重跑确认 0 |
| 物理设备序列号 | 无（全程模拟器 `emulator-5556…` 系 serial，规范 §4.1 允许保留） | — |
| 学习者内容 | 无（全部为夹具/结构断言输出） | — |

- 本目录 `.mimosa/` 为 Mimosa hook 状态（`.gitignore:29` 忽略，不入 git），非证据、不处理。
- 未删除任何失败行/chatty/签名行；六件 KD-77 迁移件 byte-identical（sha 见 §1.1）。

## 7. 缺口与未落盘（逐条：缺什么、为什么、谁补）

1. **R-04 K6 红（机制①）→ 阻塞维持**：关闭前置 = 主会话在两合规路径（选项 A Keeper/L8 结构解锁 vs 批次 36 预检硬门口径）间裁定；裁定前不关闭、不放宽（判据 §1.5/§1.7）。
2. **release 窗口装机与设备侧 sha 的 raw 缺口**：`step-device-track.md:164-165` 声明"release 主/测试包 install -r -t 双 Success；设备侧 sha = 宿主逐一相等"，但 `build/device-track-batch37/` 无独立 raw 文件（`install-debug*.txt`/`on-device-sha.txt` 只覆盖 debug 四包）；K6 舞步已收口（设备已回 debug 态），无法事后补采。**该两句为 runner R4 摘要声明，落盘员未能以 raw 文件复核（UNVERIFIED-raw）**；同批 debug 四包的同类声明有 raw（E-05）。
3. **K6 `-e class` 命令行无独立 raw 文件**：判据 §1.6 命令逐字在案（判据文档 + `step-device-track.md:166`）；`r04-k6.log` 起始即崩溃栈（0 例跑，两方法名不在 `-r` 流——与红判定一致）。
4. **step14 归因的设备负载实测（iowait 22% / 219MB free，`step-device-track.md:108`）无 raw 记录文件**：同 §7-2 性质（runner 摘要声明）。
5. **六件 KD-77 迁移件为 `.txt` 且 `03` 416 行 > 400 行**：byte-identical 迁移完整性取舍（拆分即破坏与 `tmp/` 源的 sha 一致性）；偏离规范 §2.2/§3.1，计划 §1.5 原文口径为"迁入目录根（无子目录）"。已登记，不拆分、不改写。
6. **V-01 ④ PDF 腿本批无设备证据 + 降级变体 2/4 夹具红挂账**（R1 §3.3 方案 B 待主会话裁定；`step-device-track.md:220-224`）。
7. **生产签名 UNVERIFIED、arm64 真机/16KB release 面、API 23 全套件**维持既有缺口（`step-device-track.md:236`）。
8. **契约 `:208` 登记（不落笔）**：`docs/current/visual-generation-contract.md:208` 的 KD-81 快照身份句与 KD-81 关闭裁定的矛盾（v3 清单补记 9 登记）继续待回改；T5 实跑 `git status --porcelain -- docs/current/` = **7 条 M**（README / architecture-contract / data-schema / implementation-status / model-task-+contracts / product-contract / release-gates 七文档；第 5 个写成分离词块以避开 §6 密钥模式假阳性）→ 热区未清空，维持登记分支。
9. **跨账本分歧（KD-77）以此批裁决为准**：KB 线 `docs/kb-batch25-2026-10-29.md:401`（自述已关闭）与 `:200`/`:245`（自留 open/partial 建议）的双表述是否回改归 KB 线；本轨不改他线文书。
10. **KD-73 日志留 `tmp/`**：按"`tmp/` 不提交"纪律不入仓；在树可复跑载体 = `tools/ci/check_stdlib_compat.py` + `tools/tests/test_check_stdlib_compat.py`（§5.1 复跑同值）。

## 8. 回链

- KD-77 关闭裁决：`docs/known-defects.md` KD-77 条目（`## KD-77` 头部状态 + 2026-10-31 批次 37 Progress 块；M 在飞，台账轨落笔）。
- KD-73 回链句：`docs/known-defects.md` KD-73 条目 2026-10-31 批次 37 块。
- 准入清单登记：`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` 头部 v3 补记 11（批次 37 T5）。
- R-04 / V-01 / A-10 / KD-82 台账回填与批次记录 `docs/research/2026-10-31-batch-37-record.md`：**待台账轨落笔**；落笔后按规范 §7.5 跑链接检查（本索引先于 record 存在，检查后置）。
- 引用语法（规范 §7.2）：批次记录用 `E-NN` + `evidence/2026-10-31-batch-37/<file>:<line>`；`known-defects.md` 用 `research/evidence/…` 前缀。
