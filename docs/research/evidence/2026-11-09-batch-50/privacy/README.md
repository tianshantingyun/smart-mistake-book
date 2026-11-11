# 隐私备案（隐私政策页 / data safety 清单 / 备案号占位）证据

> 批次 50 · 隐私备案线 · 2026-11-08
> 设备：`emulator-5590`（x86_64 / API 34，**非真机**）
> HEAD：`a87fc70d`；工作树含他线未提交改动（本线开工时 `SecondaryScreens.kt` 为**干净**，见 §6）
> APK：`app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk`
> sha256 `43f1fe46651614f081c0d9edb8a4bc0b33988905822868e857b24e25c34a3e8e`（`apk-sha256.txt`）

## 0. 终态

**应用内出现了可读的《隐私政策》全文页（9 章），备案号/运营主体/联系方式三处外部信息如实写"尚未取得"
（零编造）；data safety 逐题清单已落盘并把"代码事实"与"待后台核对的建议答案"分开；H-025（未成年人专章
引用悬空）由 FAIL 转 PASS；H-024/H-036 仍 BLOCKED（外部）。**

未做到（如实）：政策**公开 URL**（H-024）、Play 表单**提交**（H-036）、备案号/主体/联系方式**回填**、
真机与 strictOffline flavor 的设备走查、英文版政策（H-081）。这些都在 §5 逐条列出。

## 1. 门判据实测

| 门 | 命令 | 实测 | 判 |
|---|---|---|---|
| 隐私 H-域 | `python -I tools/ci/h_compliance_gate.py --topic privacy` | 8 PASS / **H-024 FAIL**（外部：需已发布 URL）/ EXIT=1 | 与基线比：H-025 由 FAIL→PASS，无回退 |
| data safety H-域 | `python -I tools/ci/h_compliance_gate.py --topic data-safety` | 8 PASS / **H-036 FAIL**（外部：Play 账号+已发布 URL）/ EXIT=1 | 与基线一致 |
| 文案 JVM | `./gradlew :app:testLocalFirstDebugUnitTest --tests "com.tingyun.smartmistakebook.PrivacyPolicyCopyTest"` | **tests=8 skipped=0 failures=0 errors=0** | 绿（`TEST-…PrivacyPolicyCopyTest.xml`） |
| 全 app 单测 | `./gradlew :app:testLocalFirstDebugUnitTest` | **198 tests / 2 failed** | 2 红与本线 diff 无交集（见 §4） |
| 离线 flavor 编译 | `./gradlew :app:compileStrictOfflineDebugKotlin` | BUILD SUCCESSFUL | 绿 |
| 门值改动 | — | **0**（本线未改 `h_compliance_gate.py` 任何一行；该文件开工时已是 **M**——AIGC 线的 H-018/H-061/H-062/H-066 升级，`git diff` hunk 在 76/296-305/626/631/656 行，**H-019…H-036 逐字未动**） | — |

基线（改动前，`h-gate-baseline-privacy.txt` / `h-gate-baseline.txt`）：
privacy 7 PASS + **H-024/H-025 FAIL**；data-safety 8 PASS + H-036 FAIL。
本线唯一改变的判定是 **H-025 → PASS**：它要求"AI 文案引用的未成年人个人信息保护章节"真实存在于隐私文本中，
此前 `docs/current/privacy-and-data-safety.md` 没有"未成年人"任何字样，引用是悬空的。

## 2. 红 → 绿

| 步 | 命令 | 读数 |
|---|---|---|
| 红（编译） | 同 JVM 命令（实现前） | `Unresolved reference 'PrivacyPolicyCopy'` ×12 行（`red-compile.txt`） |
| 绿 | 同 JVM 命令（实现后） | 8/8 `failures=0`（`jvm-privacy-policy-green.log`） |

用例钉住的四类失败（`app/src/test/.../PrivacyPolicyCopyTest.kt`）：
① 政策缺章/空段落；② 备案号/运营主体/联系方式留空却不说"尚未取得"；
③ 任何文案出现伪造备案号形态（`ICP备\d{4,}` 或 8 位以上连续数字）；
④ 数据清单与真实存储面（错题本/掌握库/讲题会话/题图/设置/临时）对不上、删除范围与存储页不一致。

## 3. 设备实测（emulator-5590）

| 步骤 | 做法 | 读数/证据 |
|---|---|---|
| 入口 | 我的 → 数据与隐私 | `02-profile.png`；`policy-ui-dump.xml` 前序 dump |
| 政策页打开 | 点「查看隐私政策全文」 | `03-policy-top.png`；dump 含"隐私政策（版本 2026-11-08，生效日期 2026-11-08）"与 9 章标题 |
| 未成年人专章 | 滚到该章 | `05-policy-minor.png`；dump 含"未成年人个人信息""不满 14 周岁""监护人" |
| 备案占位 | 滚到末章 | `04-policy-notice-filing.png`；dump 三行均为"尚未取得"，**全 dump 无 `ICP备` 与 8 位数字命中** |
| 返回契约 | 系统返回键 | policy → 摘要页（`back-to-summary-ui-dump.xml` 含"查看隐私政策全文"）→ 再返回 → 我的（累计作答 5 次） |

**为什么加了 `BackHandler`**：政策页是同一路由内的子视图，没有它时系统返回键直接把"数据与隐私"整路由弹掉
（首轮实测：按返回后直接落回"我的"）。这是真实可复现的返回值错误，不是"更完备"。

## 4. 全 app 单测的 2 红（不归因本线）

`./gradlew :app:testLocalFirstDebugUnitTest` → `198 tests completed, 2 failed`：

| 用例 | 失败文本 | 归因 |
|---|---|---|
| `StorageRestoreFeedbackTest.genericRestoreFailureDoesNotEchoTheRawExceptionMessage` | `不得回显 content:// URI`（:38） | 目标函数 `storageRestoreCompletionMessage` 在 `SecondaryScreens.kt:1508`；本线 hunk 只有 `@@ -6,0 +7 @@`、`@@ -642,0 +644,13 @@`、`@@ -669,0 +684,52 @@` 三处，均在数据隐私区，未触及该函数 |
| `TutorSettlementNoticeTest.aVerdictlessTutorVisitSaysThereIsNoVerdictAndOffersRetry` | `AssertionError`（:37） | 生产文件本线未改 |

**边界**：未在干净 HEAD 树上重演这两条（工作树含他线未提交改动，按协作纪律不做整树回退），
因此只记"与本线 diff 无交集"，不记"HEAD 必红"。

## 5. 未做 / 未验证（如实）

- **隐私政策公开 URL**：无域名与托管，H-024 仍 FAIL；应用内也不做外链跳转（跳一个不存在的地址更糟）。
- **Play Console 表单**：未登录、未提交（H-036）。官方页 `developer.android.com` / `support.google.com`
  本次 `WebFetch` 均连接超时，只从搜索摘要确认表单结构（先问是否收集/共享，再问用途与安全实践）；
  **数据类别字段名与 collected/shared 判定口径未逐字核对**，`docs/current/play-data-safety-answers.md` §2 标"待后台核对"。
- **备案号/运营主体/联系方式**：6 项外部信息全部 BLOCKED，代码里是空串 + "尚未取得"，零编造。
- **strictOffline flavor 设备走查**：只编译通过，未装到设备看离线版文案（分支文案由 JVM 用例覆盖）。
- **真机、TalkBack、字号/对比度、深浅色**：未跑（本线只 emulator-5590 + 截图目视）。
- **英文政策**：未产出（H-081 外部依赖）。
- **iOS/其他商店**：不在范围。
- **H-075 计数**：`Text("` 命中由 358 → **359**（本线新增 1 行 `Text("查看隐私政策全文")`）。
  该断言改动前即为 FAIL（"未做资源抽取"），本线未改变其判定，只把计数 +1。

## 6. 协作边界

- 开工快照（`git status --porcelain -- <目标>`）：`SecondaryScreens.kt`、`docs/current/privacy-and-data-safety.md` **干净**；
  `SmartMistakeBookRoot.kt` / `ProfileRoute` / 清单文件均为他线或既有状态 → **未触碰**。
- 本线对既有文件只有 **1 个文件的追加**：`SecondaryScreens.kt`（`git diff --stat` = 66 insertions(+), 0 deletions；
  hunk 仅 `@@ -6,0 +7 @@`（BackHandler import）、`@@ -642,0 +644,13 @@`、`@@ -669,0 +684,52 @@`）——没有整文件重写，
  没有删除他线内容。
- 未改门脚本、未改任何阈值、未放宽任何断言、未改 `AndroidManifest.xml`、未新增依赖。

## 7. 文件清单

- 新增：`app/src/main/kotlin/com/tingyun/smartmistakebook/PrivacyPolicyCopy.kt`（政策唯一文案源）
- 新增：`app/src/test/kotlin/com/tingyun/smartmistakebook/PrivacyPolicyCopyTest.kt`（8 条）
- 修改（追加）：`app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt`（入口 + 政策页 + BackHandler）
- 新增：`docs/current/play-data-safety-answers.md`（data safety 逐题清单）
- 修改：`docs/current/privacy-and-data-safety.md`（政策镜像 + 未成年人专章 + 外部项 BLOCKED 表；保留 H 门三处英文标题字面）
- 证据：本目录（dump/截图/门日志/用例 XML/APK sha）
