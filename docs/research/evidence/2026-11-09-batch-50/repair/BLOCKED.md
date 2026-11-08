# 批次 50 修复环：阻塞项登记（不能在本环内修的中/高项）

> 规则：只修高/中；不能修的必须写清"为什么不能在这里修、缺什么、归谁裁定"，不得用"更完备/后续再说"搪塞。
> 每条都给出可复核的一手位置。低严重度项按原记录保留，不在此重复。

## B-1（中）5c 错因面板：生产没有候选生产者，功能未端到端闭环

- **事实**：`ErrorCauseReviewService` 已在生产装配（`SmartMistakeBookApplication.kt:189-190`，
  `SmartMistakeBookRoot.kt:1780`），但 `ErrorCauseBookingService` / `ErrorCauseExtractor` 除类定义、
  KDoc、测试外**没有生产调用方**（`grep -rn "ErrorCauseBookingService\|ErrorCauseExtractor(" --include=*.kt .`
  仅命中测试）。真实用户只能看到空态；确认/撤销的设备证据是"注入候选后走真实 UI"。
- **为什么不能在本环修**：`ErrorCauseExtractor.extract` 需要结构化 trace（`RuleClaim`/`StepClaim`/
  `ReadClaim`），而生产侧目前**没有采集这些信号的写口**（extractor KDoc 自述：`tutor_message`
  只有 role+body，不是学生步骤证据）。补生产者 = 新增"讲题/重做 → 结构化 trace"的产品链路，
  并决定何时提案、是否联网调模型；这不是修复环可以单方面改的语义，且会牵动 5c 的 D-001
  冻结合同（候选不进入错题本分类/筛选/标签）。
- **缺什么**：产品裁定（producer 触发点 + trace 采集面）；若走模型提案，还需 D-1c 认证证据链。
- **当前处置**：`record` 的 5c 节、`5c/README.md` 已如实写"生产无候选生产者"；未伪造候选。

## B-2（中）3E 第二档无预留份额：默认 20 分钟下"能捞回"不成立

- **事实**：`KnowledgeReviewQueue.kt:204-240` 把今日档与全库扫描档并集后**统一按分取队**，
  `take(fullLibraryScanDailyCap)`（`:200`）只是上界、不是预留；默认 20 分钟 = 6 个知识槽
  （1200/180），今日档占满且分数更高时，第二档贡献可以是 **0**。
  3E 的"捞回 10 个"只在 45 分钟 `CONSTRUCTOR_ONLY` 夹具（4 道今日题 + 600s/题）成立。
- **为什么不能在本环修**：给第二档预留份额 = 改队列合成与预算分配语义（也会改 `F8` 的实际含义），
  3E 证据 README §5 R1 与 record 都明确"留给主会话裁定"。本环无产品裁定权，改了就是越界。
- **缺什么**：主会话/产品决定是否引入预留份额（以及份额口径）；若引入，需要新的设备/JVM 用例
  证明默认配置下也能捞回，并重跑 3E 门。
- **当前处置**：四门汇总必须把它记为**条件性通过**，不得计无条件 PASS（`record` 已加此句）。

## B-3（中）JS 生产装配未完成（visualEngine 未注入、能力不广告）

- **事实**：`DefaultFigureGenerationCoordinator.create` 的 `visualEngine` 默认 null，
  生产调用未传；生产能力集合仍 `IMAGE_MODEL_ONLY`（`FigurePromptAssembler.kt:21-23` 等）；
  math 盒回放按契约 fail-closed（`VisualDrawOpsPainter.kt:194-202`）。
- **为什么不能在本环修**：① JS 是本批**另一会话在飞**的线，规则要求不整文件抢占；
  ② 计划 item 31 的前置是 MVA 门与 KD-76，本批 MVA 为 FAIL/NOT_EVALUABLE（零生图/零 VLM judge），
  KD-76 仍 open。此时注入=把未认证引擎广告给用户。
- **缺什么**：MVA 可评证据（真实生图 + VLM judge 或等价的产物级核验）+ KD-76 关闭 + JS 线让出文件。
- **当前处置**：record 的 JS 节（能力不广告/生产未注入）已标 BLOCKED；未伪造 JS 可用。

## B-4（中）干净树 release 构建 / 产物身份

- **事实**：本轮 AAB/APK 是脏树产物；`j_release_verify.py identity --require-clean` 实跑 exit 1
  （finding 记 `dirty_paths=5345`）。产物内嵌 revision 只是 HEAD sha，不代表所编代码。
- **为什么不能在本环修**：干净树构建的前提是**先提交**；当前工作树有 JS/UI/KB 多线在飞改动，
  本环无权提交或清理他线文件。计划 item 37 明确"主会话提交后复跑"。
- **缺什么**：主会话先收口并提交，再按 `release/release-50.md §8.2` 复跑干净树 release + 重新验签/16KB。
- **当前处置**：`release-50.md:698-699,724` 与 `release-gates-summary.json` 已如实标 `clean_tree=false`。

## B-5（外部）Play 生产签名 / 账号 / 备案号

- 本地 release key 明确 `OU=NOT-PLAY-PRODUCTION`，不是 Play 生产身份；Play App Signing 决策、
  上传证书指纹、Play 账号、ICP 备案号、隐私政策公开 URL 均为外部项。
- **为什么不能修**：仓库内没有也不应有这些凭据/主体信息；伪造即违规。
- **当前处置**：verdicts §3、release 证据均标 BLOCKED（外部）。

## B-6（口径项；输入 finding 的 severity 字段被截断，未擅自猜级）2h soak 的 PASS 含义

- **事实**：`device/soak/soak-summary.txt` 的判据只有 `duration/cycles/crash_lines/dropbox/monkey`；
  `pss_samples=0`（smaps_rollup 读不到，与批次 49 相同），无 ANR 采样、无内存/电池阈值。
- **为什么不在本环改脚本**：改判据=改 soak 门的成功含义；本环不改门。只能把"PASS 能读成什么"写死。
- **当前处置**：`device/README.md §6` 增补"PASS 口径"一句（只等价于 2h 无新增 crash/dropbox 且
  monkey 全 0，不构成内存/电池稳定结论）。
