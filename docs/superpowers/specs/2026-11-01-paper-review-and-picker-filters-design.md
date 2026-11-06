# 选题筛选、纸质复习与每日时长实施草案

日期：2026-11-01。状态：用户已于 2026-11-01 确认方案及所列 11 条新增路径，按此实施。

## 推荐方案与取舍

推荐“每日预计时长 + 与今日错题计划一致的纸质答卷”。另一个可行方案是固定每日题数，但不同题的耗时差异大，现有规划器已有时间预算，固定题数会另立容量约束；本轮选择时长，保留当前默认 20 分钟。只导出 PDF 而不接回传虽然较小，但不能满足用户要求的纸质循环。

## 一、智能体从错题本选择

保留题图、搜索与原地选中返回会话。加入可收起筛选区：科目、板块、掌握程度、收藏、录入时间和排序，与错题本语义一致。筛选之间取交集；科目变更清理不适用板块；零数量掌握档保留；未分板块可筛选。已选条件、结果数量、清空入口始终可见。搜索和筛选状态属于弹窗，不改错题本当前条件。

生产选题接同一个 LibraryCatalogRepository 查询端口，避免用 StudyCatalogEntry 的不完整字段伪造收藏和录入时间。支持后续页，取消返回当前聊天不丢草稿。题图加载与失败有反馈。初始加载、无题、无匹配、查询失败分开显示；选中返回精确题目身份。导出等复用选题弹窗的入口同步获得筛选。

## 二、今日纸质复习

复习页提供“导出今日错题”和“回传纸质作答”，手机作答入口保留。导出范围默认今日计划中尚未完成的错题，保持计划顺序；只含已保存错题，不把知识点现场生成题混入。没有待复习错题时说明原因，不发起空导出。

导出前生成不可变纸卷快照，保存计划标识、学习日、题号、entryId、practiceUnitId、problemRevisionId、卷面指纹和导出记录关联。复用原生 PDF、现有分享/打印与详细答案另存能力；答案单独文件、默认不勾选。纸卷留出作答空间，不把答案印在题目旁。导出成功并不等于完成复习。

纸卷区分生成中、导出失败、待回传、待确认、部分已确认、已完成；智能体检查中的发送、生成、取消和失败状态沿用会话本身。重进页面、进程退出、跨日后仍能从记录继续。文件生成失败可重试；已成功文件不重复生成。

## 三、拍照回传与订正

从本次纸卷记录进入，支持拍照或相册多页上传，原始照片按原样保存。上传时选择“未订正”或“已订正”，后续允许逐题标明订正状态；已声明订正的照片不能取消标记冒充原始正确。本轮复用现有智能体会话检查原始照片：带入精确原题与对应照片，由智能体说明原始作答、订正和不确定项，用户回到原卷逐题确认；题号关联由整卷或本题上传入口明确，不自动套用模型输出；不把回传照片重新录入成一批重复错题。

模型服务未配置、识别失败或题号不明时，保留原图和任务，提供已有题号的手动关联/确认，不静默宣称识别完成。识别任务复用现有模型配置和请求/取消机制；题图重绘与生成图不得充当学生原始作答证据。

本轮纸质回传的确认统一保存为用户自评（SELF_REPORTED），并保留订正与照片证据；智能体意见仅用于辅助确认，不伪装为本地客观核对或自动判分。手机端原有本地核对语义保持独立。已订正答卷保留原始错误、订正结果和是否看过答案；原始结果不清晰时保持待确认，不能因订正答案正确直接标记独立掌握。用户确认过的自评走自评语义，不伪装客观正确。

纸质和手机共用题目复习证据与进度口径。重复上传、重复确认不重复计数；跨日按真实作答时间（可确认）记录，不以上传时间假冒作答时间；旧题面/答案版本发生变化时提示核对，不能套用新版本答案。手机端已经完成的题不会因回传再计一次。未完成部分保持待处理，允许分批回传。

## 四、我的 → 复习安排 → 每日复习时长

采用单一时间设置，不同时引入题数设置。默认 20 分钟，快捷选项 10、20、30、45 分钟，自定义 5–120 分钟。显示保存、失败与生效范围。持久化到现有 scheduling_settings DataStore，并真正接入规划器、今日预计耗时与新题安排预览，不能只改 UI 数字。

未开始、未导出的计划可重算；已经开始作答或已有纸卷快照的计划保持题目身份和顺序，变更用于下一次新计划，页面明确说明。考试期现有预算扩张规则要与用户目标一并解释为预计目标，不能承诺绝对用时上限。保留现有记忆目标与考试日期设置。

## 实施顺序

1. 把选题弹窗接到可分页的现有目录仓储，补齐所有筛选、数量、清空与失败恢复。
2. 加每日时长字段与 DataStore，接排程和预览，保护已开始计划与纸卷快照。
3. 增加纸卷、题目快照、回传页和逐题确认的持久化；新增迁移，保护已有题目和数据。
4. 今日计划精确导出与文件分享/打印；生成失败/重试和单独答案接线。
5. 回传照片保管、识别建议、人工确认和订正语义；接既有学习证据与幂等进度。
6. 在原生交互预览核对整条流程，保留最小必要回归与打包证据，不做无关全面测试。

## 预计新增路径（须先获用户授权）

- docs/superpowers/specs/2026-11-01-paper-review-and-picker-filters-design.md
- docs/superpowers/plans/2026-11-01-paper-review-and-picker-filters-implementation.md
- core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/PaperReviewRepository.kt
- core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomPaperReviewRepository.kt
- core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/PaperReviewMigration_72_73.kt
- core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/PaperReviewEntities.kt
- core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/PaperReviewDao.kt
- core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/73.json（仅由 Room 生成）
- feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/PaperReviewRoute.kt
- feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/PaperReviewViewModel.kt
- core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/PaperReviewPolicyTest.kt

其他新增路径另行弹窗；现有题图、模型、导出、排程与设置文件只在本需求范围内修改。证据与讨论材料继续写入已授权的 .jez/artifacts/library-interior-implementation-2026-10-31/，不新增证据根目录。

## 当前证据与实际缺口

PaperAnswerSheet / PaperAnswerSheetRecycler 已定义指纹、学习日、题号与幂等语义，但 InMemoryPaperAnswerRecycleStore 明确只是内存参照，生产尚无完整持久化回传页面。StudyReviewOverview 暴露今日 practice unit 顺序；现有原生导出可复用。SchedulingOptions 当前只有 desiredRetention，每日预算在 RoomBackedStudyExperienceRepository 固定为 20 * 60 秒。选题弹窗当前只用 StudyCatalogEntry 列表，缺少收藏和录入时间字段，不能直接加空壳筛选。

## 实施补充（2026-11-01）

备份选择规则升级为 v5，写入 schema 73；读取保留对 schema 72 / v4 的原始行数与摘要校验，新增纸卷表按空表迁移，不伪造旧包行数。设置文档升为 v2，旧 v1 的每日时长默认 20 分钟。原始作答照片成为复习记录的备份资产根与清理保留引用。

当前实现不是自动批改服务：照片检查沿用已有智能体模型配置、发送与取消流程，学生明确发送后才调用模型，再回到纸卷确认原始结果。未配置模型时可继续手工确认。原始照片始终原样保留。
