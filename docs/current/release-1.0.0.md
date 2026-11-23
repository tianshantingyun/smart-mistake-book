# 1.0.0 发布候选复核与 GitHub 整合报告

复核日期为 2026-11-15。用户指定版本为 `1.0.0`，公开下载继续保留一个 APK。本轮新增应用内默认自主执行与 JS 错误修复闭环，保留此前仓库整理和发布回归修复。

## 当前发布与源码

GitHub 当前只有 `v1.0.0` 一个预发布条目，含安装包、发布清单与校验和三项资产。远端 APK 的 versionCode 为 101422，SHA-256 为 `9aa579a871b316d27c4c053277ce112e9f639e386d1c43e28481bf674fb46d7e`。发布清单标注源码 `71f538f8b19139c274dff98692533bd24143bc43`，标签仍指向 `00d1361f7f4f51ae6d0cd6f0a1a1f0a1261bc7eb`，需要随本轮验证后统一。远端清单已独立下载至 `build/remote-release-snapshot/release-manifest.json`。[GitHub 发布](https://github.com/tianshantingyun/smart-mistake-book/releases/tag/v1.0.0)

正式签名密钥已建立并保存在仓库外，证书 SHA-256 为 `a9306655f2992f5fc7554ca4d0ebd91fa3a6e6ee0bbd92c2c40388397a3a882f`。后续安装包沿用该签名。

## 智能体能力检查

用户要求停止完整验证后，本轮集中核对声明、上下文、执行端口与结果回灌。当前声明八类工具与三种应用动作。

- 大厅工具上下文的 subject、sessionId 与知识点注册表均为空。知识材料、掌握情况及教学备注工具在该入口会进入空范围分支，查询到的错题也无法直接成为完整讲题上下文。位置为 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/TutorToolContext.kt`。
- 错题检索固定读取前六条，offset 为零，参数未开放分页与结构化筛选。大厅只回条数，条目 id 留在本地 trace，模型缺少按内容挑选具体条目的返回信息。位置为 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt` 的 notebookRead。
- 打开、保存、导出动作仅在 TutorLobbyInput 广告，讲题与计划回合的动作接线仍待统一。位置为 `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/Capabilities.kt` 的 forRound。
- JS 当前运行 prepare/render 结构化绘图程序，宿主 SDK 提供计算与绘图能力。应用工具调用桥接、可由模型填写的参数与持续状态、参数交互尚待接通，当前产物适配器的 initialStateJson 固定为空对象。
- 题目编辑、收藏与归档、复习计划调整、设置修改和备份恢复尚无智能体工具入口。ADD_TO_REVIEW_PLAN 仅保留历史解码形状。
- 生图成功回传产物 id 与状态。面向模型的图像读回和视觉自检反馈仍待接通，现有修正闭环覆盖执行诊断。

当前灵活性的主要缺口集中在上下文装配、可选择对象的返回信息、跨入口工具接线和 JS 宿主接口。

## 智能体与 JS 改造

- 当前已就绪工具声明决定执行范围，意图和置信度作为模型上下文。写入继续由真实目标与数据校验处理。
- 同名工具支持不同参数多次调用，Native 与 Route B 均可在同轮携带工具和多个应用动作。
- JS 失败诊断经过生成器、执行器和下一次模型请求保留，模型可修改程序、接续同一操作预算并选择其他可用方法。原生公式度量的 math 协议已同步声明。
- 保存、打开、导出按顺序自动执行，成功、失败、排队状态与产物身份回传当前工具环。entryId 和 entryIds 与本轮已检索或保存的真实条目核对。
- 已完成操作按持久身份去重，失败可通过新调用身份重试，取消后可恢复，历史确认卡保持兼容。
- 纯动作轮计入五轮工具配额，第六次派遣收起工具和动作，供模型总结实际结果。
- 附图保存进入既有录入链路。导出默认 PDF，未指定题目时采用本轮检索候选，六十秒内未生成则准确返回 QUEUED。复习计划调整处理器继续保持未接通状态。

## 本轮验证证据

| 检查 | 当前结果 | 证据 |
| --- | --- | --- |
| Python 完整工具测试 | 1,586 passed，70,720 subtests passed | `build/agent-python-full.log` |
| JVM 全量测试 | 4,718 个实例，零失败，3 skipped | `build/agent-jvm-before-module-clean.json` |
| 两种 Debug Lint | 已完成通过 | `build/agent-full-verification-final.log` |
| 两种 Debug APK 与测试 APK | 已生成 | `build/agent-full-verification-final.log` |
| 定向设备回归 | 工具环 15 项通过，列表与录入 4 项通过 | `build/agent-tool-loop-device-confirmed.log`、`build/agent-library-device-repaired.log` |
| 完整设备回归 | 按用户最新指令停止，未计为完整通过 | `build/agent-all-device.log` |
| H 源码门 | 75 PASS，15 FAIL | `build/agent-h-compliance.log` |

JVM 与 Lint 同批执行时，额外测试 APK 的增量打包遇到 DEX 路径缓存错误，已通过 `:core:data:clean` 重建，后续重新验证该模块。Lint 与测试资产合并同时运行时暴露的隐式依赖已补齐。

H 门仍记录隐私政策公开 URL、Data safety 外部表单、年龄与监护人相关界面、时长提醒、上线信息、本地化以及人工视觉/文案走查等事项。它们保持真实状态，未通过修改测试隐藏。H 门是源码检查，其结果与设备测试分别记录。

## GitHub 当前整合

当前工作位于 `codex/complete-release-tests`，PR #18 继续承接发布测试和本次智能体改造。旧 CI `43589916473` 已结束失败，其失败清单作为本轮逐项修复依据。后续提交、合并及安装包更新另行记录，完整回归已按用户指令停止。[PR #18](https://github.com/tianshantingyun/smart-mistake-book/pull/18)

依赖更新 PR #6、#8、#15、#16、#17 仍开放，当前检查失败。它们分别涉及 OkHttp、Kotlin、SQLite JDBC、AGP、kotlinx.serialization，需要各自对应的依赖锁和兼容验证。

## 核验方式

本机受控入口存在 ACL 拒绝，使用仓库既有 `gradlew.bat` 与 `D:\smb-build` ASCII 别名，`GRADLE_USER_HOME=D:\gradle-home`。Gradle 由主任务串行调度。设备任务使用 Android 34 模拟器，实际模型服务、不同实体设备和完整人工视觉验收仍需独立证据。独立审查代理触及额度限制后，主任务继续本地审查与实际测试。

以下保留此前已完成的仓库和 GitHub 整理记录。

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
