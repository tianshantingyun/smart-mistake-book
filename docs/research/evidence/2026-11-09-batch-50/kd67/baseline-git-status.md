# 开工前 / 收工后目标目录 git status（批次 50 · KD-67 第二题）

## 开工前（本子线第一次检查）

```text
$ git status --porcelain -- <本项要改的 6 个文件>
 M core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/AdaptiveSelectionAssembler.kt
 M core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/StudyExperienceRepository.kt
```

- 其余 4 个目标文件（`KnowledgeQuizItemStore.kt`、`KnowledgeQuizFeedbackWriter.kt`、
  `KnowledgeQuizItemVerificationUpgrader.kt`、`RoomBackedStudyExperienceRepository.kt`）在开工时
  干净（`RoomBacked…` 在更大范围检查里是 M，但那是批次 49 既有改动，本项只在其 KD-67 方法旁
  **追加**新方法）。
- 两处 M 均为**批次 49 既存改动**（前者只有 KDoc、后者只有默认实现方法），本项只做最小追加，
  **未整文件抢占**、未改动他线在飞内容。
- 协作边界：本项未触碰 `feature/review`、`app` 的实现文件（KD-67 第二题的 UI 显示/作答属于
  未接线残余，如实登记在证据 README §5）；唯一写入 `app` 的是无（零改动）。

## 收工后（相对开工新增的改动）

```text
$ git status --porcelain -- <同上 6 文件 + 2 个新测试文件>
 M .../AdaptiveSelectionAssembler.kt              ← 候选池：第二槽装配为 CALIBRATION 候选
 M .../KnowledgeQuizFeedbackWriter.kt             ← 按题面 id 定位槽位（additive 参数）
 M .../KnowledgeQuizItemStore.kt                  ← 槽位枚举 + 槽位读/写 + 按题面 id 读
 M .../KnowledgeQuizItemVerificationUpgrader.kt   ← CAS 增加 slot 参数（默认 REVIEW）
 M .../RoomBackedStudyExperienceRepository.kt     ← 第二槽 persist/read/submit 实现
 M .../StudyExperienceRepository.kt               ← 3 个默认实现方法（旧替身不破）
?? core/data/src/test/.../KnowledgeCalibrationQuestionTest.kt
?? core/data/src/androidTest/.../KnowledgeCalibrationQuestionRoomE2eInstrumentedTest.kt
```
