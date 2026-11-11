# 批次 50 · AIGC 覆盖（export/合规线）证据

日期：2026-11-09（工作树 HEAD `a87fc70d15c8ad726cee17422dbeef0b4977747d`）
设备：`emulator-5590`（x86_64 / API 34，**非真机**）
范围（5 项）：①显式角标（UI/导出可见）②DOCX 文档级标识 ③模型改卷文本（KD-85）
④部分页打印（KD-84）⑤材料 AI 整理标识（KD-29）

## 法规/规范依据（本批实际取得，未伪造条款）

- 《人工智能生成合成内容标识办法》第四条（显式标识）、第五条（隐式标识）、第十条（不得伪造）：
  条文文本沿用仓库既有研究 `docs/research/2026-10-25-ai-content-labeling-compliance.md` §1.1（该文自 cac.gov.cn 提取），本批未重取网页。
- **TC260-PG-20258A §6.1**（DOCX 隐式标识）：本批从官方 PDF **重新下载并逐字提取**：
  URL `https://www.tc260.org.cn/tc260/sjzn/202508/f0a84ada0c11458e9cb6da56c1cb2426/files/760387fb30e94c058c31b8c2b2230020.pdf`，
  3,272,625 字节，sha256 `6bf6c1189208856099bfbf648bdf386da288b1df00f430502b395b874dbf3b0e`
  （与仓库 `docs/research/2026-10-26-pdf-implicit-labeling.md` 记录一致）。原文：
  「a) 存储路径为 docProps/custom.xml 文件中的元素：Properties/property；
  b) 写入方式为：1）新建元素 property，其属性 name 为"AIGC"；2）子元素 lpwstr 的值采用 json 字符串表示，字符串如下：{"Label":"value1",...,"ReservedCode2":"value7"}」。
- **TC260-PG-20258A §6.3**（PDF `/AIGC`）：同上官方 PDF 第 9 页，沿用既有实现，本批未改。

## 逐项红/绿

| 项 | 红 | 绿 | 产物/独立读回 |
|---|---|---|---|
| ① UI 显式角标 | `red-ui-app-labels.log`：4/4 失败；`red-ui-tutor-label.log`：2 例中 1 失败（负向例通过） | `green-ui-app-labels.log`：`OK (4 tests)`；`green-ui-tutor-label.log`：`OK (2 tests)` | Compose 语义树断言（无截图） |
| ① 导出可见（模型文字） | `red-pdf-visible-notice.log`：1 失败（把 planner 说明块临时禁用重演） | `green-pdf-visible-notice.log`：BUILD SUCCESSFUL（含 3 个 AIGC 类） | — |
| ② DOCX 文档级 `AIGC` | `red-docx-docprops.log`：`model text docx carries...` 失败（`docProps/custom.xml` 不存在） | `green-docx-docprops.log`：BUILD SUCCESSFUL | `artifacts/model-text-labeled.docx`（1,852 B，sha256 `33f73909…`）、`artifacts/pure-user-unlabeled.docx`（1,296 B）；`docx-independent-readback.log`（Python zipfile+ElementTree+json，不经项目代码） |
| ③ KD-85 | `red-kd85-model-text.log`：2/5 失败 | `green-kd85-model-text.log`：5/5；`green-device-kd85.log`：`OK (2 tests)` | 设备产物 `artifacts/kd85-model-edited-labeled.pdf`（`/AIGC` Label=1，ProduceID=1ea9adfa…）、`artifacts/kd85-manual-edited-unlabeled.pdf`（仅 `/Producer`）；`pdf-independent-readback.log`（pypdf 6.16.2） |
| ④ KD-84 | 历史红：`docs/research/evidence/2026-11-06-batch-47/h-domain/h-061-red-b47.log`（本批**未回退重演**） | `green-device-kd84-print-subset.log`：`OK (7 tests)`，含 `wholePrintAndPartialPrintBothKeepTheDocumentLevelAigcEntry` | `artifacts/print-subset-relabeled.pdf`（`/AIGC` 在，Label=1）；`pdf-independent-readback.log` |
| ⑤ KD-29 材料"AI 整理" | 同 `red-ui-app-labels.log`（4/4，含两张材料卡） | 同 `green-ui-app-labels.log` `OK (4 tests)` | Compose 语义树断言 |

**红态口径（如实）**：①/⑤ 的红是把 `AiContentBadge` 渲染器临时改成立即返回（mutation），
重现"标识不存在"的界面状态，之后已还原并重跑绿；不是对历史提交回退重演。③ 的红是真实
先写用例再改实现。④ 的红是批次 47 的历史记录，本批只复跑绿，没有为了取证把已修代码改回。

## JVM 回归（本批实跑）

| 命令 | 结果 |
|---|---|
| `./gradlew :core:model:testDebugUnitTest :core:export:testDebugUnitTest --offline --no-parallel --rerun-tasks` | BUILD SUCCESSFUL；core:model **674/0**（69 类）、core:export **178/0**（19 类） |
| `./gradlew :feature:review:testDebugUnitTest --rerun-tasks` | BUILD SUCCESSFUL；**116/0**（14 类） |
| `./gradlew :feature:tutor:testDebugUnitTest --rerun-tasks` | **266 例 / 1 失败**：`TutorExportSheetTest.theWordFormatNoticeStatesItsRealLimitsAndPdfHasNone` |

唯一失败是**既有红、与本线无关**：`git show HEAD:feature/tutor/.../TutorExportSheet.kt` 的文案是
"Word 以文字说明代替图形，不含作答区。…"，同 HEAD 的用例断言的仍是"作答空白区"；两个文件都不在
本线改动集内（本批未改该文案、未放宽断言）。

## 设备/APK 绑定

- `emulator-5590`，`ro.build.version.sdk=34`，`ro.product.cpu.abi=x86_64`。
- app APK `a70b9559f93f24bedab3ca31df439e9dd6184a658c83315355f11c8be78ab62d`
  （`app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk`）
- app test APK `f11374a468fbf33601e91c2fb8333b4244e5be96a1f1e6bdf3c7ea1d3b9e46d2`
- core:export test APK `07c713941c524c3b4dcc41fde066c40372698f55ec50af235a4d4c728db181cb`
- feature:tutor test APK `b9d056a42c87df682f0578838f5b61c4bfd1e2b611d587382cf491043934fdaa`
- 复跑命令（示例）：
  `adb -s emulator-5590 shell am instrument -w -r -e class <FQCN> <pkg>/androidx.test.runner.AndroidJUnitRunner`
  其中 `<pkg>`：`com.tingyun.smartmistakebook.localfirst.test` /
  `com.tingyun.smartmistakebook.core.export.test` / `com.tingyun.smartmistakebook.feature.tutor.test`。
- 禁用的任务：未运行 `:app:connectedLocalFirstDebugAndroidTest`。

## 合规门（本批重跑）

`python -I tools/ci/h_compliance_gate.py --all`（`h-compliance-gate-after.log`）：
- `H-052 PASS`、`H-061 PASS`、`H-062 PASS`（KD-85 关闭标记 + `modelTextApplied` 置位 + `||` 判据）。
- `H-053 FAIL`：`docs/ai-content-disclosure-copy.md` 的【模型名称】/【备案号】/【上线编号】占位符未回填——
  **外部项，BLOCKED**（需 CAC 备案名单与实际上线编号，本批不伪造）。
- 其余 FAIL（H-063/066/070/075/076/077/080/081/089/090）为既有、非 AIGC 线范围。

## FIX 复发门（KD-84 / KD-85 / DOCX）

`python -I tools/ci/fix_relapse_gate.py --gate FIX-T04 --gate FIX-T05 --gate FIX-T07`
（`fix-relapse-T04-T05-T07.log`）：**3/3 PASS**。FIX-T05/T07 的 `--prove-red`
（`fix-relapse-prove-red.log`）：**2/2 RED-PROOF OK**（把修复改回去门转红）。

门升级内容（未放宽）：FIX-T05 从"KD-85 open + 纯透传"改为"closed + `modelTextApplied` 并入
`toDraft()` + 设备 `OK (2 tests)` + pypdf 正向/负向读回"；FIX-T07 从"DOCX 无 `/AIGC` 且登记
KD-84"改为"`docProps/custom.xml` 写入 + 宿主独立读回正向在、纯用户负向不在"。
新增逐例自测 `tools/tests/test_fix_relapse_gate_aigc_closed_state.py` **6 passed**
（`fix-gate-unit-tests-new.log`）。

**冲突登记（未改他线文件）**：`tools/tests/test_fix_relapse_gate.py` 是**未跟踪（他线在飞）**文件；
`python -I -m pytest tools/tests/test_fix_relapse_gate.py` 当前 **5 failed / 9 passed**
（`fix-gate-unit-tests.log`）：
- `test_t02_concatenated_exception_turns_gate_red`、`test_t04_unread_back_injection_turns_gate_red`、
  `test_fixed_gates_are_green_on_current_tree`（在 T02 处先失败）与本线无关：`SecondaryScreens.kt`
  仍含 `${failure.message` 拼接（T02 门红）；T04 的 fixture 自批次 47 起就没带证据日志。
- `test_t05_*` / `test_t07_*` 的 fixture 只复制代码与 KD，没有证据目录；门升级后 baseline 变红。
  按协作规则**未整文件抢占**，改由新增的独立自测覆盖（上一段）。


## 未做 / 未验证（如实）

1. **DOCX 未用 Word/WPS 实际打开**：只有 OPC 结构 + 命名空间 XML 解析 + 自定义属性读回；
   "Word 不报文件损坏"属未验证。
2. **UI 只有 Compose 语义断言，无截图/TalkBack 走查**：字号、对比度、是否遮挡题干未做人工视觉复核
   （H-063/H-090 仍 FAIL）。
3. **pypdf 对追加式 PDF 报 `incorrect startxref pointer` 警告**：元数据与页数均可读；沿用 KD-75 已接受的
   追加式注入，不是本批引入。
4. **KD-84 未重演红态**（见上）。
5. **真机 / soak / release(R8) 未跑**：设备仅模拟器；release 构建属主会话收口。
6. **备案号/上线编号/模型名称**：BLOCKED（外部）。
7. `AiContentBadge` 的"AI 整理"对材料是**无条件**显示；依据是 `KnowledgeMaterialContentAttribute`
   只有 `AI_SYNTHESIZED` 且 sidecar schema 4 强制该键。新增枚举值会让
   `KnowledgeMaterialContentAttributeTest` 先红，强制改判据。
