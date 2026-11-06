# 选题筛选、纸质复习与每日时长实现计划

> 执行方式：当前已授权工作区内逐任务实施；用户不要求全面测试，采用定向检查与原生交互验证。其他新增路径仍须弹窗。

**目标：** 让智能体选题复用完整筛选，支持今日错题纸质作答闭环，并让用户设置每日预计时长。

**架构：** 目录查询共享 LibraryCatalogRepository；时长持久化在现有 DataStore 并传给规划器；纸卷、精确题目快照、原始照片及确认结果由 Room 仓储持久化，证据通过现有学习事实边界落库。

**技术栈：** Kotlin、Compose、Room、DataStore、现有原生 PDF 与模型服务。

## 授权范围
新增路径按正式设计文档“预计新增路径”执行，共 11 条；证据复用此前授权目录。既有未提交 UI 修改保持不动，不混入无关重构。

### T1 完整选题筛选
- [x] 修改既有 TutorMistakePickerTest，补科目、板块、掌握、收藏、时间、排序的查询构造/交集案例（8 项定向测试）。
- [x] 修改 LibraryCatalogItem、目录映射与 TutorMistakePicker：精确身份字段、仓储注入、可收起筛选、数量、重置、分页与失败重试。Root 的 CompositionLocalProvider 注入真实目录仓储。
- [x] 运行 :feature:tutor:testDebugUnitTest --tests '*TutorMistakePickerTest'，验证题图仍直接可见、选中仍返回当前聊天。

### T2 每日预计时长
- [x] 修改 SchedulingOptions、DataStoreSchedulingSettingsStore、SchedulingSettingsScreen，增加 5–120 分钟且默认 20 分钟。
- [x] 修改应用装配、规划器与 IntakeBacklogPreview，使用同一预算源；已开始或有纸卷快照的计划不改顺序。
- [x] 通过已有排程测试文件追加预算边界案例；原生保存/重启核对 DataStore，备份测试核对时长往返及旧包默认值。

### T3 纸卷持久化与语义
- [x] 新增 PaperReviewRepository.kt 与 PaperReviewPolicyTest.kt，定义卷、题目、照片、订正、确认、幂等与跨日时间策略。
- [x] 新增 PaperReviewEntities.kt、PaperReviewDao.kt、PaperReviewMigration_72_73.kt 和 RoomPaperReviewRepository.kt。将表/DAO/迁移接入现有 Room 与应用装配，由 KSP 生成 73.json。
- [x] 更新现有备份/恢复表分类与版本口径，保留现有数据，不手写 Room schema。
- [x] 检查原始照片进入受控资产管理、删除/备份引用完整；归档或旧版本题目不会静默替换为新题。

### T4 今日计划导出
- [x] 在 ReviewRoute 与 Root 接“导出今日错题”和“纸质复习记录”，按计划未完成项、原顺序冻结题目身份。
- [x] 复用现有 PDF、分享、打印及独立详细答案导出，保存导出记录与卷面身份；导出不推进学习进度。
- [x] 导出失败可重试，成功文件可重开，未完成任务可恢复。

### T5 回传与确认
- [x] 新增 PaperReviewRoute.kt、PaperReviewViewModel.kt；从纸卷记录拍照或多页相册导入，选择未订正/已订正，逐题关联与确认。
- [x] 接现有智能体会话携带精确原题与照片，按正常发送/取消机制产生检查意见；无模型时可手动关联与确认，不创建重复错题。模型意见不自动写进复习结果。
- [x] 接现有学习事实与会话进度边界：纸质确认统一为自评，手机本地核对与模型意见不混写；重试幂等；手机端已完成项不重复计入；未知原始结果留待确认。

### T6 最终验证和展示
- [x] :app:assembleLocalFirstDebug 和必要的定向测试；不跑无关全面套件。
- [x] 专用原生模拟器核对选题筛选、保存预算、今日导出/系统打印预览、相册回传、逐题确认与退出重进；相机实拍和在线模型响应列入下方未验项。
- [x] 更新既有 localhost 交互预览并记录实际通过/受限内容，代码提交推送需按用户后续要求。

## 当前执行证据

- `paper-completion-build.log`：`:app:assembleLocalFirstDebug` 成功；首轮 5 个相关测试类共 82 项通过；补充草稿保护后 `paper-draft-safety-build.log` 再次打包成功，累计 6 类 92 项相关测试通过。备份测试通过证据目录内临时 init script 仅编译两个目标测试文件，避免仓库既有模型线协议/视觉序列化测试的编译错误；未修改或关闭生产构建检查。
- `paper-schema-verification.json`：72→73 迁移与 Room 生成的 97 张表在列、外键、索引上逐表一致，已有哨兵数据保留；89 表裁剪顺序通过外键拓扑检查。
- `paper-native-first-confirm.json`：真实模拟器已有 5 道错题，纸卷 2 题、1 张原始照片、1 次自评事实；照片原始字节摘要核对一致。
- 原生 UI 已进入 A4 两页打印预览；相册导入成功；第一题确认后记录为 `AGAIN`，第二题保持待确认；重启后原卷、照片和进度保留。智能体入口真实携带原题和照片，未配置模型时明确阻止发送。

尚未验证：实体打印机出纸、真实相机实拍及在线模型识别质量。它们不能由本地模拟器的成功替代。

- `paper-native-replay.json`：重复上传同一图片、保存第二题为待确认、再次计入后，错题仍 5 道、照片仍 1 张、自评事实仍 1 次；订正照片的原始字节摘要一致。原始结果未知的第二题没有被记成正确。
- 订正照片下选择“独立答对”会就地说明原始证据要求并禁用保存。进入智能体已有草稿时先由产品弹窗确认替换，新增回归覆盖保留/替换和重复带入幂等。
