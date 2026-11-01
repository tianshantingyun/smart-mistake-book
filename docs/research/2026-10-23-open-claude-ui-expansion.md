# Open Claude UI 全前端扩展移植记录

2026-10-23，承接已批准计划及用户“先不用全面验收，继续全量重构”的指令。参考源码固定为 `Caho1/open-claude-ui` 的 `c1f923ae0051a871941b98a0e2e3183a545e6a85`。MIT 许可证继续随 APK 分发。

2026-10-25 续记：下文记录本批历史状态。逐代码块工具栏的后续方案已经落地：固定原 0.17.0 Android 源码，只新增可选显示槽，保持同一解析树与安全门；嵌套列表/引用、原文复制和安全回退四项设备检查通过。最新覆盖与许可证证据见 [全量覆盖复核](2026-10-24-open-claude-ui-coverage.md)，不再将当时撤回的 internal API 尝试视为当前未实现项。

## 实际落点

新增 10 个原生共享实现文件及 2 个测试文件；本批相对基线有 58 个源代码/测试文件变化。其中 39 个文件直接迁移控件调用，其他消费者通过共享组件获得样式。文件数用于追踪范围，不代替页面和业务验收。

| 上游来源 | 已接入的原生实现 |
| --- | --- |
| `CdsButton.tsx`、主题 | `ClaudeButtons.kt`：主/次/幽灵/图标按钮；圆角、浅底次级按钮、0.95 按压/150ms、悬停与焦点。保留 48dp 触区、原回调、禁用条件和已有颜色参数。 |
| `ChatComposer.tsx`、输入层 | `ClaudeTextField.kt`：原生 BasicTextField/IME、常驻标签、白色字段、细边框、8dp 圆角；单/多行、只读、错误、键盘类型和密码变换沿原参数。 |
| `ConfirmationDialog.tsx` | `ClaudeDialog.kt`：最大宽 520dp、圆角 16dp、内距 24dp、正文独立滚动、底部操作换行、关闭和 200ms 入场。调用方继续决定确认/取消、securePolicy、禁用、返回与遮罩关闭；添加/选题的取消保持次级样式，删除/放弃修改用危险样式。 |
| `SegmentedControl.tsx`、焦点 hook | `ClaudeSelection.kt`：1dp 轨道内距、白滑块、120ms 位移、选择语义、方向键/Home/End 和 RTL。错题筛选/模型协议实际接入；大字/窄屏横向滚动，名称不隐藏；重选当前项不重复派发。 |
| `ModelSelectorEffort.tsx`、浮层主题 | `ClaudeMenu.kt`：12dp 白浮层、细边框、8dp 阴影、48dp 选项和 8dp 选项圆角；回答方式和归类选择实际接入，仍使用真实枚举/数据。 |
| `DraggableSessionList.tsx`、桌面页头 | `ClaudeList.kt`：14sp 紧凑页头、20dp 图标、设置行和选中表面。用于历史、我的、录入、二级页与四栏底部导航；归档/删除仍是原独立动作，不增加固定/拖动排序模型。 |
| `ThinkingAccordion.tsx`、`StatusIndicators.tsx` | `ClaudeStatus.kt` 与思考组件：12dp 容器、分隔、14dp 内距、三点运行反馈与完成点；保持生成时展开、结束后收起，不编造用时。只读查阅继续为文字明细。 |
| `CodeBlock.tsx` | `ClaudeCopyButton.kt`：实际剪贴板写入后显示成功，2.5 秒恢复，用于思考和修订对照。原 richtext 代码块采用 12dp 深色外壳、13sp 等宽正文、细边框，沿用现有 CommonMark 和安全回退。 |
| `StreamingDiffViewer.tsx` | `ClaudeRevisionDiff.kt`：工具栏、复制、修改前/后、增删底色；消费原字段 diff，宽时并列、窄屏/大字顺序阅读，不提交修订。 |
| `ArtifactsSplitPaneResizer.tsx` | `ClaudeSplitPane.kt`：原图/编辑双栏实际拖动、最小宽度、双击等宽、方向键/Home、Escape 回滚和读屏调整；比例跨布局保留，同一 movableContent 编辑器保留草稿。采用原生触摸阈值，不突然隐藏编辑区。 |

同时收敛复习页大号统计与科目图标色块。列表外显图片、题面版本、图片角色、JS/IMAGE_MODEL 判定、CAS、复习评分、导出和备份接口继续沿现有实现。

## 适配边界

移植现有 Android 功能可消费的布局、令牌和交互。React/DOM/Electron 宿主、macOS 红绿灯、演示模型/用量/成功、麦克风权限及不存在的协作系统不加入应用。不增加 React 运行时、Markdown 解析器或任务重试引擎。

代码块沿现有库的块样式扩展点，暂未加入上游逐代码块复制工具栏；整条回答复制继续保留。已从本地实际 0.17.0 依赖核对并尝试编译：AST、parsedMarkdownAst、RecursiveRenderMarkdownAst 与对应 Local 均为 Kotlin internal，不能将 javap 的 Java public 外观当成稳定扩展 API。尝试已完整撤回，不压制可见性检查，也不再造解析器；markdown-extension-compile.log 保留此限制的证据。完整共享元素过渡、真机/全窗口/性能/无障碍以及原七批业务门继续保留，不能写成已通过。

## 本批证据

只执行必要编译和定向检查，按用户要求暂缓全面验收。日志和字节快照位于 `.jez/artifacts/open-claude-ui-implementation-2026-10-23-expansion/`。

- `controls-compile.log`、`consumers-compile.log`、`layouts-compile.log`：共享控件及全部消费者编译通过。
- `expansion-build.log`：记录底栏替换后漏导入 Row 的失败，随后已修复；`expansion-build-fixed.log` 主包与测试包通过。
- `final-expansion-build.log`：最新共享组件 50 个 JVM 用例零失败、core:ui lint、localFirst 主包及两个测试包组装成功，2m42s。
- `current-final-build.log`：撤回 internal API 尝试后，按设备验证的 58 文件摘要逐项核对完全一致，再次运行共享 JVM/lint 与主包构建成功，1m13s；lint 0 错误/1 个原有 ModifierParameter 警告。
- `controls-device.log`：8/8，通过字段常驻标签/真实文本回调、长弹窗按钮可达、大字分段、分栏草稿、已有输入结构/模式选择和历史删除/取消。
- `main-navigation-device.log`：1/1，真实 MainActivity 四个根页、设置字段/保存入口和返回可达，未清空或注入用户学习库。
- `wide-editor-device.log`：实际 840dp 窗口 2/2，宽窄切换保留编辑草稿、无手势分栏调整可用；完成后恢复 945×2100 像素、fontScale 1.0。
- `main-360/`：最新已安装 APK 的默认字号四根页截图；已实际查看我的和讲题截图，组件/极端字号夹具不冒充默认主应用。
- 上述共 11 次设备定向用例通过，包含同一分栏用例的窄/宽两次运行，不称为 11 个独立业务场景；不会把定向检查写成全应用体验通过。
- 完整撤回 AST 尝试后主包重新打包，APK 字节指纹发生变化；追加安装当前包并运行同一导航用例，日志为 `current-package-navigation-device.log`。这属于当前包复核，不增加独立场景数；截图也从该当前包重新取回。

未提交推送。`before/` 保存原始字节；`source-manifest.json` 与 `source-changes.diff` 是本批审阅快照，可能包含共享文件的其他会话到达变更，不能整体暂存或覆盖。
