# 学习工作室视觉升级

用户授权：检索 GitHub 上流行的视觉设计模板与优秀设计技能，直接提升现有软件的视觉。延续已经确认的青绿品牌、全软件改造范围和“星图必须服务知识关系、与整体协调”的约束。

## 检索与选择

2026-10-30 读取 GitHub 公开仓库 API，保存快照至 `.jez/artifacts/visual-studio-2026-10-30/github-candidates.json`。以下是本轮筛选集合中的热度，不声称是 GitHub 全站完整排名；星数属于整个仓库，不代表单个技能质量。

| 参考 | 本轮星数 | 许可信息 | 应用范围 |
| --- | ---: | --- | --- |
| [shadcn/ui](https://github.com/shadcn-ui/ui) | 126956 | MIT | 组件层次、细边框、明确的主次操作与一致的控件词汇 |
| [Magic UI](https://github.com/magicuidesign/magicui) | 22971 | MIT | 数据块的节奏、有限的强调与状态动效；不移植营销页持续光束 |
| [shadcn-admin](https://github.com/satnaing/shadcn-admin) | 16314 | MIT | 可扫描的数据分区与操作布局；转译为移动原生布局 |
| [UI UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) | 142854 | MIT | 可读性、触控、色彩、数据与动效规则；使用本机已有技能文本 |
| [Impeccable](https://github.com/pbakaus/impeccable) | 96479 | Apache-2.0 | Operate 模式、排版与视觉完成度；手工阅读 skill/craft-floor/operate，不运行下载执行器 |
| [Anthropic skills](https://github.com/anthropics/skills) | 185004 | 各技能单独核验许可 | 使用本机已有 frontend-design 的完整规则，不把根仓库视为统一许可 |
| [React Bits](https://github.com/DavidHDev/react-bits) | 50721 | API 为 NOASSERTION | 仅评估动效风格，不按 MIT 复制代码 |

Impeccable、Magic UI 与 shadcn-admin 的源码保留在本机 `C:/Users/听云/.codex/skill-cache/visual-reference-20261030/`，只作为长期设计参考。不安装外部 hooks、二进制或依赖。UI UX Pro Max 的已安装副本缺少 scripts/search.py，本轮不宣称运行其设计系统生成器。

## Direction contract

- THESIS：让学生首先看清题目、当天任务和真实学习记录；通过排版和结构形成辨识度，不用装饰替代内容。
- OWN-WORLD：纸白、青灰、青绿主操作，学习蓝和阅读暖色作为已有语义色；细边框、18dp 内容容器、统一字号与 48dp 触控。原生绘制的叠页笔记本标识作为页头签名，阅读区保持安静。
- STORY：错题本查看原题与记录；录入以拍照为主要操作；复习看清计划数与用时；个人页读取累计作答、掌握与连续复习。所有计数沿用现有口径。
- FIRST VIEWPORT：标题区从整块淡绿圆角横幅改为开放的编辑式排版，正文卡片与背景明确分层。个人/复习页真实统计使用可扫描的色块并支持大字号堆叠；原题图片继续完整展示。
- FORM：Android Jetpack Compose 原生实现。使用现有按钮、控件与导航行为，不引入 React/WebView。seed: none (unavailable)。
- FINISH：必须查看本轮原生渲染，核验大字号、触控、关闭动画与代表性业务入口，并记录真实构建、测试与未覆盖范围。

## 实施

1. 新增共享 StudioVisuals：叠页标识与真实值统计块；不添加虚构趋势、分数或学习指标。
2. 更新 WorkspacePageHeader/WorkspacePanel，覆盖个人、复习、讲题、录入及各工作区；错题本专用页头接入同一组件。
3. 错题列表和详情模块改为细边框与稳定间距，保持原题图片完整比例、收藏、筛选和导出入口。
4. 个人页、复习页重排统计布局，移除冗余的外层卡片；大字号维持同样数据与颜色含义。
5. 录入主次入口、底部导航选中反馈使用统一强弱关系。动画仅表达状态，沿用系统减少动画设置，不添加持续动画或入场等待。
6. 核验共享 UI、录入、错题本、复习、个人、讲题与应用的单元测试、lint、两个应用变体和设备测试包；只操作本任务的专用模拟器。
7. 原生截图保存到 `.jez/artifacts/visual-studio-2026-10-30/`，以最终源码和 APK 指纹核验，不将不同测试数据的截图作为受控的像素对比。

## 边界

保持已有任务流程、权限与能力条件、题目图片、安全模型配置、知识关系来源及雷达指标；不增加新业务逻辑。既有 Claude 交互反馈和系统导航仍保留。视觉参考不等于照搬网页框架或新增设计工具运行时。

## 固定参考版本

- impeccable：`df3a4abe7d0840d5f60324812c432e00b66a4e75`。
- magicui：`efe76fc5755eca1142dd4f74c1d6f0ace2887e3c`。
- shadcn-admin：`e16c87f213a5ba5e45964e9b67c792105ec74d26`。

## 2026-10-30 实施与验证结果

已实现共享开放式页头、原生叠页标识、细边框内容容器、分区统计块、错题本页头与题卡、录入主次入口及导航选中反馈。长说明、大字号和空间不足时隐藏装饰，优先保证文字；不改业务流程、原图呈现、模型能力条件、掌握算法或知识图关系来源。

- 最终构建：`build-delivery.log`，BUILD SUCCESSFUL in 5m 13s，localFirstDebug、strictOfflineDebug 与应用/录入/错题本/讲题测试包成功生成。
- 七个相关模块的 736 个单元用例通过，无失败、错误或跳过；XML 时间戳已与最终构建开始时间核对。
- 七个模块的 lint 报告无 Error/Fatal，仍有 Warning/Hint；最终排版调整后补查 core/ui 和 app，不将警告宣称清零。
- 专用 API 34 emulator-5566 的 17 类、58 个不同设备用例通过，以 `delivery-run/probe-summary.json` 为准，不累加重复运行。
- 保留真实题图完整比例、收藏/筛选/导出独立入口、录入触控、导航、讲题布局、关闭动画和 1.6 倍字体的设备证据。个人/复习有数据截图使用当前 Compose 渲染取图，空白旧截图不作为视觉验收。
- 最终源码 SHA-256 与已测试 APK/归档 APK 指纹一致，截图附来源、时间与摘要。图中数据为明确的 QA 夹具，不代表用户学习内容；安全模型设置的受保护黑屏不作为视觉证据。
- 未执行下载技能的二进制、安装器、hooks 或依赖；UI UX Pro Max 的生成器缺失情况如实记录。未测真机帧率，也未进行全业务能力验收。
