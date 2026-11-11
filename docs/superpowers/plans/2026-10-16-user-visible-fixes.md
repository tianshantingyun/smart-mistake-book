# 用户可见缺口补修计划

**目标：** 闭合已获用户授权的 UV-01～UV-11，验收以 [审计报告](../../research/2026-10-16-user-visible-followup-audit.md) 的真实触发路径为准。

**方案：** 保留现有架构和共享工作区改动，在负责身份、引用和操作生命周期的边界修复。无数据库破坏性迁移，不扩展模型权限，不提交推送。使用 code-work、测试驱动和完成前验证技能执行；适用的并行模块有独占写入边界，Gradle 与设备验证由主代理串行调度。

**技术栈：** Kotlin、Compose、ViewModel/SavedStateHandle、Room、JUnit、Android 仪器测试。

## 模块与验收

- [ ] UV-01：feature/tutor 当前诊断轮交互权不由自由回复抢占；提示成功与失败后仍能提交原选项且不重复记证据。
- [ ] UV-02/07：feature/review 保存本次有序知识点身份并安全恢复；队列重排/缩短不串题；末项跳过显示会话结束出口。必要的 app 计划入口由主代理集成。
- [ ] UV-03：core/data capture、BatchImportDao 及 feature/library 复核使用的源图引用在最后消费者结束前保留；用真实存储集成验证清理后的可打开性。
- [ ] UV-05：详情备注保存后正文与二次编辑读取新值；用 UI 写入/读回路径验证。
- [ ] UV-06：批量页结合草稿生命周期显示可继续、已入库或已移除；已失效草稿不再通往 MISSING；必要的 app 导航由主代理接线。
- [ ] UV-04：app 存储恢复、删除完成及关闭数据库的失败分支进入进程级阻断状态；页面重建和普通重开不能回到旧仓库；提供真正关闭进程的用户入口，再次启动重新打开数据库。
- [ ] UV-08：app 模型设置使用页面 ViewModel 保留旋转时的表单与在途操作；密钥只保留在内存，不写 SavedState。
- [ ] UV-09：提醒开启时，即使频道暂时不可用也维持后续排程；回到应用幂等校准，不重复通知。
- [ ] UV-10：core/export 题目区不再输出选择器交互状态，作答记录仅随显式答案区导出；覆盖无答案模板与答案模板。
- [ ] UV-11：app 掌握页空态独立导航错题本，返回按钮仍正常返回。

## 每个模块的执行与统一验证

1. 在现有测试边界加入捕捉原缺陷的回归；主代理运行并检查预期失败，再实现最小修复。
2. 主代理运行模块回归，检查组合 diff 与既有用户改动。
3. 单元命令：`.\gradlew.bat :feature:review:testDebugUnitTest :feature:tutor:testDebugUnitTest :feature:library:testDebugUnitTest :core:export:testDebugUnitTest :app:testLocalFirstDebugUnitTest --console=plain`；存储/引用问题另跑对应 data/database 集成测试。
4. 构建两个 app debug 变体及受影响 androidTest APK，执行相关 lint。仪器测试仅用专用 `audit_user_visible_20261016` / `emulator-5556`，逐次核对身份，直接安装测试 APK 并 `am instrument`，不在原 emulator-5554 上执行安装、卸载或清库。
5. 核对 11 项验收映射、测试 XML、设备结果与源码差异，写补修记录；未验证的层面明确说明。

当前进度与最终证据记录在 `docs/research/2026-10-16-user-visible-fixes.md`。
