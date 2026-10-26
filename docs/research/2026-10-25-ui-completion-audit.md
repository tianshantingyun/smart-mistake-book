# UI 全量收尾核验

用户最新要求：继续完成全量 UI；此前暂停全面验收的指令已被替代。不新增业务功能，不提交推送。适用上游范围仍按已批准的36个样例、14章裁定；JS/IMAGE_MODEL能力和原PRD/WG发布门不会因UI验收自动关闭。

证据目录：`.jez/artifacts/full-ui-completion-2026-10-25/`。只操作本任务AVD `audit_claude_coverage_20261024` / `emulator-5562`（Android 34），不操作5554。设备数据为合成夹具；涉及清库的既有测试显式opt-in。安装后核对设备APK的SHA-256，并保存不可变副本到`tested-apks/`；不同APK的结果不拼成一次全绿运行。

## 实际修复

| 缺口 | 修复与回归 |
| --- | --- |
| 320dp/2倍字的三键说明变成竖列 | 原说明宽约164px、高约423px，先以截图和宽高断言复现。宽度不足时改成等宽整行，保留同权重、禁用和幂等重试。8条真实Room UI回归通过，包括真实触摸、一次落库、撤销和重建。 |
| 大字圆标装不下选项字母 | 圆标按实际字体行高增长，字母采用明确的标签字号；已查看修复后的320dp/2倍字截图。 |
| 复习科目图标读出英文枚举 | 移除页面覆盖，使用共享组件已有的中文学科描述。 |
| 一个题卡多页时全部图片同时解码 | 真实只读ContentProvider记录到首屏读取全部12页。改为按裁剪后的窗口可见区域加载，离屏保留几何与滚动目标，释放图片组合；顺序、收藏、详情点击和独立放大入口不变。10条图片设备回归通过，包含首屏最多读取3页、滚到第12页后实际读取并放大。未全局延迟录入页解码，避免影响保存可用性回调。 |
| 旧复习测试没有跟随当前PRD-05通道 | 三条事务/配对/揭示回归通过实际自评通道验证原不变量，未恢复退役的产品标签。RootExperience整组10/10通过。RootTutor整组暴露夹具误给无答案原题提供可信答案，恢复该夹具的null合同后5/5通过。 |

## 构建与设备证据

| 范围 | 当前证据 |
| --- | --- |
| 两种主包、受影响测试包 | `final-current-build.log`：localFirst/strictOffline、app/library测试包构建成功，1m27s。 |
| 当前受影响JVM | `final-static-summary.json`：app101、library65、review72，共238项，零失败/错误/跳过。共享、录入、讲题、我的等本轮检查另见`build-and-static.log`，不混成当前全部重跑。 |
| 图片类型迁移接线 | 用户已允许补齐编译。当前调用方使用FigureArtifactRef；`typed-image-integration-and-main-build.log`和`typed-image-tests.json`记录69项相关JVM零失败及主包构建成功。保留实际字节、SHA和来源身份，没有恢复旧类型别名或伪造JS可用性。 |
| 静态检查 | `talkback-test-and-lint-build.log`成功7m2s；app lint零错误/致命、43警告，library零错误/致命、8警告，不宣称零警告。 |
| 完整串行基线 | `device-summary.json`：77类，305/309通过。四条失败随后分别定位为三条旧复习通道测试、一道要求真实宽窗的编辑测试。基线不写成全绿。 |
| 当前集成 | `current-integration-summary.json`保留当时51/52的红例；`final-paths-and-frames-summary.json`记录修复后RootTutor 5/5、自评8/8、主导航1/1、真实根页截图1/1及首版帧采样1/1。相关52项已分批逐项通过，不能把多个APK说成一次全绿运行。图片10、布局4、自适应1、导出入口2、来源分组3、原生编辑8、自评8、根导航1均通过。 |
| 宽屏编辑 | `wide-editor-summary.json`：实际2208×1890/420dpi（约841×720dp）1/1通过；切到320dp再返回仍是同一草稿，结束后恢复物理窗口。 |
| 中文输入 | `ime-real-chinese-composing/committed.png/xml`：Gboard简体拼音真实产生“你好”候选并以空格确认写入。未发送，不扩成真实中文键盘所有发送边界；既有InputConnection同事件发送回归单独保留。 |

## 图片、页面与性能

`current-tutor/long-image-memory.json`使用三张8000×12000 PNG连续12次打开/关闭。每次实际预览1000×1500，页面显示“放大不增加细节”，就绪观测532–829ms。进程PSS基线106723KB，逐页观测最大182900KB，关闭并GC后120560KB。观测最大值不是连续采样的绝对峰值；没有用不存在的预算宣称达标，不把模拟器扩成实体机性能。

已查看`current-review/review-three-keys-320-font2.png`、`final-default/`空库四根页、`final-curated/`真实Room夹具四根页、`page-screenshots/library-gallery.png`和`batch-import-current.png`。根页为实际MainActivity，图册/批量状态为对应生产组件的夹具，不能混写。模型设置保留FLAG_SECURE，黑图不是视觉证据；控件、保存、配置返回和草稿恢复由设备回归分别验证。

宽窗测试后Launcher一度保留平板任务栏；已恢复窗口并重启本任务模拟器的Launcher。它不是应用新增导航；带任务栏截图保留，另采恢复后的默认图。

TalkBack实际走查通过：`talkback-clock-verified-driver.log`、`talkback-device.log`为1/1；模拟器内核触摸先聚焦智能体，再右划到错题本，双击后实际主页面切到错题本，导航选中、页面动作与绿色读屏焦点均已核验。截图`talkback-final-green/talkback-library-focus.png`。服务启用/通知权限均在结束后恢复。此前探针失败有两个测试机制原因：高层注入不能替代内核触摸探索；外部输入后必须推进Compose测试帧。未因此修改产品导航，也不把本例扩成全页面人耳朗读验收。

150题滚动用普通ComponentActivity、系统帧时钟和16次系统触摸滑动采样，`native-frame-summary.json`为1/1；用例通过仅证明采样与滚动行为有效。`native-frame-realclock.txt`记录243帧、88帧jank（36.21%）、p50=34ms、p95=61ms、p99=150ms；**仍有掉帧，性能门保持开放**。最初Compose测试时钟下45帧/p95=200ms的统计保留为`compose-test-clock-frame-probe.txt`，不用于产品性能结论；普通Activity探针首次因合并后的无障碍文本匹配失败，修正为递归读取真实可见文本后通过，没有修改产品。

后续性能工作：在release/profileable包和实体机上复用同一150题滚动场景，采集Perfetto的主线程、重组、布局与绘制证据，再按同机同数据比较修复前后帧耗与PSS。先定位再优化，不用缩字、隐藏题图或放宽指标换取通过。当前debug/模拟器数字不代替正式基准，亦不能据此忽略已观察到的掉帧；基准环境要求参考[Android Compose性能说明](https://developer.android.com/develop/ui/compose/performance?hl=en)与[官方Baseline Profiles实验说明](https://codelabs.developers.google.com/android-baseline-profiles-improve)。

## 无效证据与剩余边界

一次重启驱动时旧Python进程仍在运行，导致两组instrumentation抢占模拟器。“Process crashed”批次不作为产品崩溃或通过证据。精确停止相关驱动后重新串行运行；原日志保存在`invalid-overlap-evidence/`，新驱动用进程持有的Windows文件锁拒绝并发。未为编排错误修改产品逻辑。

当前仅有Android 34模拟器，没有实体机或其他系统镜像。人耳朗读可懂度、其他厂商中文输入法、受保护设置页人工视觉核对和实体机性能仍为发布验证边界。未关闭的PRD/WG/4E业务门继续保留。此外，目标学生形成性走查、尚无逐状态截图的编辑/导出页和完整跨入口业务矩阵继续沿UI-11执行；不把局部录屏/组件夹具当成这些项已完成。当前没有全软件发布验收通过结论。


## 文件与复核入口

生产修改集中在`ReviewSelfReportControls.kt`、`ReviewChoiceRow.kt`、`ReviewSessionScreen.kt`和`LibraryInlineImages.kt`；精确仓库路径与源码SHA见证据目录`completion-source-manifest.json`。本轮原始字节到当前版本的四文件差分见`production-changes-this-continuation.patch`。其余新增/修改为对应回归、私有测试Provider和计划记录，不新增生产依赖。
