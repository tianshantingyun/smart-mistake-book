# 1.0.0 发布候选复核与 GitHub 整合报告

复核日期为 2026-11-13。基础版本为用户指定的 `1.0.0`。本轮按源码、负向测试、完整测试框架、远端主干对照、GitHub 分支与 PR 逐项复核。

## 已完成的仓库整理与修复

- 121 个根目录工作文件迁入 `knowledge-production/archive/pre-release-workfiles/`，Git 内容对象逐项一致。消费者继续支持根目录新输入，同名根目录优先。
- 修复复核发现的跨归档与根目录排序回归，保留历史前缀、文件名顺序，保护按输入顺序分配的材料 `midx`。文件名片段筛选也按实际文件名执行。
- 补充根目录导航、发布算号与签名命令、14 个模块的完整依赖锁。锁生成直接解析依赖图，遇到无法解析的依赖会抛出错误。
- 修复 CI 干净检出缺失的两个负向日志夹具。CI 安装 `pypdf`、`pytest`，运行 pytest 以同时执行 unittest 与函数式测试。
- 修复真实数据库 76 与旧备份计划 75 的漏接线。备份计划使用 schema 76、closure 7、102 表、94 项裁剪顺序，纳入三张回复版本表。历史 closure 4–6 规则与 v4 冻结夹具保留兼容验证。
- 修复备份静态门的集合解析越界，增加备份版本与真实 `StudyDatabase` 版本的交叉检查。当前机器块与 126 条 FK 拓扑已核对。
- 生图静态门逐个检查五个文件中的六个请求体及各自指纹 helper，补充缺失、额外构造、错误指纹和伪造匹配的负向用例。
- JS 痕迹测试覆盖往返序列化、禁止虚报图片模型和付费派发。Paint 空标记使用字面 0，保留运行期环境指纹校验，两种变体 Lint 已通过。
- 清理导出测试夹具的七处重复参数，使整个导出单测模块可编译。导出指纹与 Word 能力文案的精确断言跟随现有实现，知识库计数按已有删点记录与成品包重新核对。
- 移除仅用于重建 schema 34 的过时临时工作流和临时设备测试。真实数据库现为 76，该工作流已在 GitHub 停用。

## GitHub 处置

| 对象 | 结果与依据 |
| --- | --- |
| PR #1 | 已合并 `actions/upload-artifact` 升级，merge `c39f77095c1d9d07d45f46256cee8b3a60d882d4` |
| PR #2 | 已合并 `actions/checkout` 升级，merge `22f096a0d43d6a46d11d189a4a786e62911a3c9e` |
| PR #5、#7 | 已关闭并删除对应分支，当前版本目录和业务代码均已无 Vico、Filament 依赖 |
| 6 个开发分支 | 全部经 `merge-base --is-ancestor` 确认为 main 祖先，先推送归档 tag 再删除远端分支 |
| PR #6、#8、#9 | 保持待审，分别涉及 OkHttp、Kotlin、AGP。原检查失败，提案仅改版本目录，尚无对应锁、依赖校验元数据和兼容回归结果 |

归档 tag 前缀为 `archive/2026-11-13/`，覆盖 `audit/kernel-readiness`、`codex/plans-2026-10-20`、`codex/ui-green-pages-2026-10-27`、`codex/ui-rebuild-continuous-response`、`feat/tool-loop-stage-a`、`refactor/split-large-files`。归档 tag 保留各分支原始提交。

Git HTTPS 连接曾间歇失败，重试成功后已核对远端状态。分支历史及已合并 PR 可在 GitHub 查询。

GitHub 整理修复通过 [PR #14](https://github.com/tianshantingyun/smart-mistake-book/pull/14) 合入 main，merge 为 `4968c2b3de0e12bf345a46cccb76b8770bdf4890`。合并后的设备任务发现 Linux AAPT2 未被依赖校验清单收录，本轮补齐了该平台条目。官方构件为 2,369,543 字节，发布 SHA-1 `5287feac13566b9165cdf02c77565e68aec3aad0` 与下载字节一致，SHA-256 为 `e772a3dae8354764f1b0793903218427f483982445207f2e4ffc8c2026755bd4`。构件核对记录在 `build/release-1.0.0/linux-aapt2-verification.json`。[Google Maven 构件](https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/9.3.0-15703166/aapt2-9.3.0-15703166-linux.jar)

## 本轮通过的验证

| 检查 | 本轮结果 |
| --- | --- |
| 归档内容一致性 | 121 / 121 Git 内容对象一致 |
| 修复相关 Python 回归 | 首组 123 项通过，后续专项 34 项通过，干净候选工作树独立复跑 154 项通过 |
| 备份静态门 | 26 项检查通过 |
| 依赖锁覆盖 | 14 个 included project 均覆盖测试与运行配置 |
| 生图入口静态门 | 五文件、六请求体 PASS |
| `core:model` JVM 测试 | 669 项通过 |
| `core:domain` JVM 测试 | 743 项，零失败，1 项 skipped |
| 备份 JVM 专项 | Plan 14 + Codec 37 + v4 夹具 8，共 59 项通过 |
| 导出与辅导模块复跑 | `:core:export:testDebugUnitTest :feature:tutor:testDebugUnitTest` BUILD SUCCESSFUL，183 + 266 项通过 |
| 两种 Debug APK | `:app:assembleLocalFirstDebug :app:assembleStrictOfflineDebug` BUILD SUCCESSFUL |
| 两种 Debug Lint | `:app:lintLocalFirstDebug :app:lintStrictOfflineDebug` 均通过 |
| schema 与历史夹具 | 数据库 schema 文件及 v4 冻结包字节未修改 |

本轮 APK 分别为 `app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk` 和 `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk`。两种调试版本名为 `1.0.0-localfirst`、`1.0.0-offline`，versionCode 为调试默认值 2。正式 Release 仍需对最终完整 Git 历史执行算号器并注入 `-Psmbk.versionCode=N`。

## 全量检查仍未通过

### Python

完整运行 `PYTHONPATH=tools python -m pytest tools/tests -q`，得到 1493 passed、19 failed。随后修复本地 `*privacy-policy*.log` 被误认为政策文件的问题，专项通过，失败重跑剩 18 项。

在独立干净工作树 `origin/main`（`22f096a0`）上运行涉及的三个测试模块，18 项同名失败全部复现，107 项通过。因此这 18 项是远端主干已存在的失败。本轮没有把它们改为跳过或放宽断言。

剩余集合为 `test_fix_relapse_gate.py` 6 项、`test_g_device_evidence.py` 2 项、`test_h_compliance_gate.py` 10 项。分别涉及源代码门接口/断言、两份历史设备绑定证据、合规静态断言与现有实现差异。部分用例显式断言旧缺口存在，需要对照实际契约进一步逐条裁决。

日志为 `build/recheck-python-full.log`、`build/recheck-python-remaining.log`、`build/recheck-python-baseline.log`，对照节点清单为 `build/recheck-python-baseline-comparison.json`。

### Android/JVM

使用 `--continue` 执行两种构建、两种应用测试、所有模块 JVM 测试和两种 Lint。后续修复导出编译与文案断言后，读取本轮及复跑 XML 共统计 4686 个测试实例，仍有 20 个失败实例，对应 16 个不同用例，3 个 skipped。

- 应用两个变体共 8 个失败实例，涉及恢复错误信息、无结论辅导提示、数学文本度量。
- `core:data` 8 项失败，涉及生图提示与 SDK、一组修改题目的模型网关契约、学习画像。
- `core:database` 4 项失败，涉及最新 schema 精确断言与 FTS 查询参考副本。

完整列表与模块计数保存在 `build/recheck-jvm-final.json`。本轮尚未对所有这些 JVM 失败逐一执行主干对照，不能据此宣称全部属于旧失败。

主日志为 `build/recheck-android-full.log`，导出与辅导复跑日志为 `build/recheck-export-tutor.log`，最终 Debug 构建日志为 `build/recheck-debug-build.log`。

## 源码交付与发布状态

源码候选包为 `build/release-1.0.0/SmartMistakeBook-1.0.0-source.zip`。同目录 `source-manifest.json` 记录最终提交/树对象、ZIP SHA-256、条目数量与验证状态。更新后的包需通过 ZIP CRC 与提交树匹配验证。

当前进程缺少四个 `RELEASE_*` 环境变量，用户选择先交付发布候选与验证报告。生产签名 APK/AAB 尚未生成。真机升级、完整设备迁移、备份还原闭环、无障碍、性能、生产签名产物 R8/16 KB 和身份绑定验证仍待完成，既有 3D、3E、MVA、AUD-08 记录也尚无真实通过证据。

本轮 Windows 受控入口因 ACL 拒绝 `TIANSHAN\CodexSandboxUsers`，验证采用已有 `gradlew.bat` 入口和 `D:\smb-build` ASCII 别名。

**Completion Gate FAILED**。仓库整理与已列 GitHub 处置已执行，正式发布仍受上述测试、设备验收和生产签名条件阻塞。
