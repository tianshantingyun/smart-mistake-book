# 批次 45 · FIX 域修复单（WS3 / 计划 25–48）

> 来源：批次 44 台账 FAIL 23 + BLOCKED 73（批次 45 开工时实读 `docs/acceptance/ledger.md`）。
> 归并规则：按**同一失败根因**合并，不按行号切分。20 单覆盖全部 96 个 FAIL/BLOCKED 源行；
> 每单一个复发门 `tools/ci/fix_relapse_gate.py --gate FIX-Tnn`（全跑 `--all --prove-red`）。
>
> 判词口径：**门锁住当前判词**。已修项锁修复面（改回去就转红）；未修项锁“已登记且没被
> 抬成 PASS”。要关闭 KD-84/KD-85 或把外部阻塞改判成 PASS，必须同时提交独立读回/设备/
> 复核证据并显式更新门——不允许只改文档。

## 单表

| 单 | 严重度 | 覆盖源行 | 落点 | 判据（关闭条件） | 本批处置 |
|---|---|---|---|---|---|
| FIX-T01 | 高 | F-055, F-057 | `app/src/main/kotlin/.../SchedulingSettingsScreen.kt` `retentionFromSlider` | 滑杆端点 0.7f/0.97f 经夹回落成 0.7/0.97 且 `SchedulingOptions` 可构造 | **已修复**（`587098f1`）；单测 4 例绿；设备 instrumented 复跑未做（见残余） |
| FIX-T02 | 中 | H-066（同族 6 处） | `app/src/main/kotlin/.../SecondaryScreens.kt` 各处 `catch` | 学生可见失败文案不出现 `${failure.message}` / `.message.orEmpty()`；域内异常保留人工措辞 | **已修复**（本批改 6 处，另 1 处批次 44 已改）；单测 8 例绿；`H-066` 现 PASS |
| FIX-T03 | 低 | H-052 | `docs/ai-content-disclosure-copy.md` + `MistakePdfExporter.kt` | 文案不得同时声称“PDF 尚未写入文件级隐式信息”且导出链已注入 `/AIGC` | **已修复**（`ae1234dc`）；`h_compliance_gate --only H-052` exit 0 |
| FIX-T04 | 中 | H-018, H-061（KD-84） | `core/export/.../MistakePdfDelivery.kt` `writeSubset` | 子集 PDF 被**独立读者**（宿主 pypdf/PyMuPDF）读回 `/AIGC`，`ProduceID` 与输入一致、页数=所选页数 | BLOCKED：需 `core/export` 已批准 hunk + 独立读回；runbook §13 |
| FIX-T05 | 高 | H-018, H-062（KD-85） | `feature/tutor/.../TutorPaperEditApplier` + `core/export/.../MistakePdfPaperEdit.toDraft` | 纯用户来源经模型改卷 → 导出 PDF 读回 `Label=1`、`ProduceID=输入 sha`；纯手工编辑仍无 `/AIGC` | BLOCKED：落点在 `feature/tutor` 热区，需主会话授权；runbook §13 |
| FIX-T06 | 中 | C-065 | `core/data/src/androidTest/.../TutorVisualRebuildRestoreInstrumentedTest.pathDocument` | 恢复腿夹具闭合（有面积）且设备重跑该用例绿 | 夹具已改为闭合三角形（工作树未提交）；**设备绿轮未跑** → BLOCKED |
| FIX-T07 | 中 | H-018（DOCX 子项） | `core/export/.../DocxMistakeRenderer.kt` | DOCX 产物被独立读者读回文档级 `/AIGC` | BLOCKED：需先落 docProps 标识再做读回；runbook §13 |
| FIX-T08 | 高 | A-010, D-082..D-090 | —（外部 Provider） | 真实 key + 真实上游端到端，证据绑 run-id/APK sha 且不回显密钥 | BLOCKED：无真实密钥；runbook §9 + §13 |
| FIX-T09 | 高 | F-006, F-015, F-024, F-033, F-042, F-051, F-060, F-069, F-078, F-087, J-035, J-037, J-038, J-039, J-042, J-043 | —（arm64 设备） | arm64 真机冷启/首帧/导出/内存/长时/进程死亡全跑 | BLOCKED：两台在线设备均 x86_64；runbook §9.2 |
| FIX-T10 | 高 | J-019, J-020, J-021, J-024, J-025, J-027 | `app/build.gradle.kts` signing | 生产签名四变量齐全，APK/AAB 同身份且指纹=已登记发布指纹 | BLOCKED：`RELEASE_*` 未设；runbook §9.2 |
| FIX-T11 | 中 | J-048, J-051, J-052, J-086 | `app/build.gradle.kts` versionCode/Name | 版本号由版本源注入且跨构建单调；历史 AAB 与现 APK vc 可比 | BLOCKED：现为手写常量 `versionCode = 2`；改版本源属发布裁定 |
| FIX-T12 | 中 | J-033, J-034, J-041, J-088 | AAB 产物（bundletool） | ABI 集合=abiFilters、含 `libquickjs.so`、无 32 位 `.so`、16KB 上传警告清零 | BLOCKED：AAB 陈旧且无 16KB 设备；runbook §9.2 |
| FIX-T13 | 中 | J-001, J-002, J-006 | —（树状态） | `j_release_verify.py identity --require-clean --require-head` exit 0 | BLOCKED：共享工作树有未提交改动；验收阶段不跑 Gradle |
| FIX-T14 | 中 | J-017, J-073..J-085, J-089, J-090 | —（Play Console） | 素材备齐上传、内/封闭轨道建立、测试账号与反馈渠道登记 | BLOCKED：无账号无素材 |
| FIX-T15 | 高 | J-055..J-057, J-061, J-065, J-068, J-069 | —（成对产物） | 同钥匙 vc N→N+1 升级/降级/回滚实测，数据保留 | BLOCKED：无成对包与生产签名 |
| FIX-T16 | 高 | H-039..H-043 | `feature/profile` 等（热区） | 年龄确认/监护人同意接线 + 隐私政策专章；`--topic minor` exit 0 | BLOCKED：G7 未落地且热区未空出 |
| FIX-T17 | 高 | H-008 | `feature/tutor`, `core/ui`（热区） | 讲题与配图界面显示“AI 生成”角标并有设备截图 | BLOCKED：热区禁改 |
| FIX-T18 | 中 | H-024, H-025, H-036, H-051, H-053, H-081 | —（外部：域名/后台/备案） | 隐私政策公开 URL 可访问、data safety 表单提交、备案号回填、英文材料产出 | BLOCKED：外部未产出 |
| FIX-T19 | 中 | I-007, I-061, I-070, I-075, I-079 | —（真机/第三方 IME） | TalkBack 语音回传、故障注入、第三方 IME、传感器旋转与折叠屏实测 | BLOCKED：无对应硬件/驱动 |
| FIX-T20 | 低 | G-087, G-088, D0-030, D0-081 | `tools/ci/check_lockfiles.py`, 台账协议 | 离线 advisory 取证；13 个锁落库后锁门在覆盖态 exit 0；D0 两行由 D0 域处置 | BLOCKED：KD-72 未落地；D0 两行属 D0 域 |

## 唯一真修：FIX-T02（红 → 绿的过程证据）

1. **红**：把门的判定放到当前树上时先红——`python -I tools/ci/fix_relapse_gate.py --gate FIX-T02`
   报“失败文案重新拼接了原始异常 message”，`grep -n "\.message" SecondaryScreens.kt` 命中 7 处
   （`:722/:752/:786/:860/:1012/:1378` 六处未修 + H-066 已修的那一处）。这不是推测：
   `SecondaryScreens.kt` 的六处形如 `"…不受影响。${failure.message.orEmpty()}"`。
2. **绿**：六处改为固定措辞 + 下一步动作；`storageRestoreFailureMessage` 的非域内分支同样去掉
   message 拼接（域内 `BackupRestoreDatabaseClosedException` 保留人工措辞）。新增单测
   `genericRestoreFailureDoesNotEchoTheRawExceptionMessage`（修复前该断言必红：旧文案会带上
   `content://…`）。
3. **绿轮**：`./gradlew :app:testLocalFirstDebugUnitTest --tests …StorageRestoreFeedbackTest
   --tests …SchedulingRetentionSliderValueTest` → `BUILD SUCCESSFUL`，8+4 例 0 失败
   （`fix/logs/FIX-unit-targeted.log`、`fix/xml/TEST-…xml`）。
4. **锁**：门的自突变证明 `--prove-red` 把 `${failure.message.orEmpty()}` 注回副本，门转红。

## 修复面复核（规则 12.1：改一处先闭合影响面）

- 改动点：`SecondaryScreens.kt` 6 个 `catch` 分支 + `storageRestoreFailureMessage`。
- 调用方：全部是 Compose 页面内 `rememberLauncherForActivityResult` 回调，没有其他调用点转述
  这些字符串；`storageRestoreFailureMessage` 唯一生产调用在 `:689`。
- 测试面：`StorageRestoreFeedbackTest`（含新增 1 例）8 例全绿；无测试断言旧文案包含 message。
- 行为面：错误提示从“固定措辞 + 原始 message”变成“固定措辞 + 下一步动作”。用户在失败时
  得到的是可执行动作，具体技术细节仍可从诊断包路径获得（该入口本身未变）。
- 不在本项范围：其它模块的文案（`core/data` 内 `AndroidBackupRepository` 的删除失败文案已是
  固定措辞，无需改）。

## 残余（不冒充通过）

1. **F-055/F-057 的设备与长时复跑未做**：本批只有 JVM 单测证明“端点不再越界”，
   `SchedulingSettingsInstrumentedTest`（`587098f1` 新增）未在设备上复跑，2h soak 未重跑。
   源行改判需设备证据 + 长时观测，FIX 域不复用单测证据。
2. **C-065 设备绿轮未跑**：夹具已闭合，但恢复腿的落墨断言只在设备上才有意义。
3. **KD-84/KD-85 未修**：分别卡在 `core/export` 已批准 hunk 与 `feature/tutor` 热区。
4. **16 单外部阻塞**：真实 Provider、arm64、生产签名、Play Console、备案、第三方 IME 等，
   复跑条件写在 `docs/acceptance/runbook.md` §13。
5. 本文件与门只读仓库文本/台账，**不证明运行时行为**。
