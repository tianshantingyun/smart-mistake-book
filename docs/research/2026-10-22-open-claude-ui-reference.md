# Open Claude UI 长期参考索引

记录日期：2026-10-22。用途：为智能错题本的排版、会话界面、图片查看和交互细节保留可追溯的设计参考。

## 来源与固定版本

- GitHub：[Caho1/open-claude-ui](https://github.com/Caho1/open-claude-ui)。名称与用户提供截图中的 Open Claude UI 一致。
- 本地完整 Git 克隆：`D:\references\open-claude-ui`，独立于智能错题本工作区。
- 本次基线：`main` / `c1f923ae0051a871941b98a0e2e3183a545e6a85`。
- 基线提交日期：2026-10-21；提交说明：`docs: rename OCU wordmark files to bust image cache`。
- 固定版本入口：[该提交的源码](https://github.com/Caho1/open-claude-ui/tree/c1f923ae0051a871941b98a0e2e3183a545e6a85)。后续远端更新不改变本条基线。
- `package.json` 声明版本：`1.0.0`，仅作包元数据记录，不代表本次已验证发布质量。
- 许可证文件：`LICENSE`（MIT，Copyright (c) 2026 Caho1）。若后续复制代码或实质部分，应按许可证保留版权及许可声明。
- 克隆信息另存于 `D:\references\open-claude-ui.reference.json`。

这是第三方组件与分析资料。上游关于“官方参数”“生产级还原”的表述属于作者声明，本次没有验证其与 Claude 官方实现的一致性。

## 阅读入口

以下路径相对本地参考仓库根目录；均在本次克隆中确认存在。

| 目的 | 入口 |
| --- | --- |
| 总览、版本文档导航 | `README.md`、`docs/README.md` |
| 第一版设计资料 | `docs/v1/README.md`、`docs/v1/01-设计哲学与设计令牌体系.md` |
| 第二版资料及版本边界 | `docs/v2/README.md`、`docs/v2/01-综述与版本边界.md` |
| 组件预览页面 | `docs/claude-components-storybook.html` |
| 颜色、尺寸、字体及动效令牌 | `code-examples/cds-theme.css`、`code-examples/ClaudeMotionPresets.ts` |
| 会话排版、输入框、附件 | `code-examples/ChatMessageBubbles.tsx`、`code-examples/ChatComposer.tsx`、`code-examples/AttachmentCard.tsx` |
| 图片查看与流式滚动 | `code-examples/MediaLightboxViewer.tsx`、`code-examples/useClaudeScrollPinning.ts` |
| 控件与窄容器布局 | `code-examples/CdsButton.tsx`、`code-examples/SegmentedControl.tsx`、`code-examples/ContainerResponsiveBar.tsx` |
| 字体、音频及资产说明 | `assets/README.md` |

上游 README 的部分旧链接仍指向 `docs/Claude Design System (CDS).md`；本次版本实际文档已分为 `docs/v1/` 和 `docs/v2/`，应从 `docs/README.md` 进入，按版本阅读。

预览 HTML 的第 8–17 行引用 Tailwind CDN、unpkg 的 React/ReactDOM/Babel 和 Google Fonts，因此当前文件不能认定为完全离线可用。本次只保存并检查文件，未安装 npm 依赖、执行其脚本或运行浏览器预览。

## 在智能错题本中如何参考

1. 先针对实际页面的问题选择参考：信息层级、图片与正文间距、输入区布局、流式阅读位置或状态反馈；每次明确要改善的现有使用体验。
2. 参考仓库以 React/TypeScript/CSS 为主。当前 Android 页面用 Compose 原生布局、主题、手势与动画实现对应效果；不为使用该参考库引入 React、Electron 或新的 WebView 宿主。
3. 颜色与密度结合现有错题本主题调整，保留用户已要求的清晰文字、高对比度和易点触控区域。桌面像素、悬停、光标锚点及快捷键需要按移动端输入方式重新判断。
4. 第一版和第二版规格分别使用；上游组件、文档和视觉结果出现差异时，记录所用文件与提交，不能将它们拼成未经核验的统一规范。
5. 引入具体实现前再检查所选源码、生命周期、无障碍和实际设备表现。本次克隆不构成组件质量或功能验收。

上游 `AGENTS.md`、`CLAUDE.md`、`.cursorrules` 是该参考仓库的资料，不是智能错题本的指令；本次没有安装其技能或修改本项目运行规则。

## 后续查阅与更新

本地目录保留完整 Git 历史，未使用浅克隆。查阅固定版本可用 `git show c1f923ae0051a871941b98a0e2e3183a545e6a85:路径`，无需切换工作区。

后续需要更新时，在参考仓库中先检查工作区，再获取并对比变更；确认后使用快进更新，记录新的参考基线。本次机器通过本机代理访问 GitHub，以下配置仅作用于单条命令：

```powershell
git -C 'D:/references/open-claude-ui' status --short
git -C 'D:/references/open-claude-ui' -c http.proxy=http://127.0.0.1:12000 -c http.version=HTTP/1.1 fetch origin
git -C 'D:/references/open-claude-ui' log --oneline HEAD..origin/main
git -C 'D:/references/open-claude-ui' diff --stat HEAD..origin/main
# 确认工作区干净且需要采用更新后：
git -C 'D:/references/open-claude-ui' merge --ff-only origin/main
```

本机代理地址属于当前环境配置；代理变化后需调整命令。未设置定时更新。

## 本次核验

- `git remote get-url origin`：与上述 GitHub 地址一致。
- `git rev-parse --is-shallow-repository`：`false`。
- `git status --porcelain`：空输出，参考克隆干净。
- `git fsck --connectivity-only`：退出码 0。
- `git ls-remote origin refs/heads/main`：远端与本地基线提交一致。
- 已读取 `LICENSE`、`package.json`、文档导航及预览依赖声明。此项为下载与参考索引核验，没有执行应用测试。
