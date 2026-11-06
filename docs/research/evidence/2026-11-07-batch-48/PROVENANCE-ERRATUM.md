# 批次 48 证据 provenance 勘误（修复环，2026-11-07）

> 性质：对抗复核（修复环）对已归档证据的 **勘误记录**。被勘误文件本体一律**未改动**——
> 它们分别被 `EVIDENCE.sha256`（根清单，323 项）、`d-domain/EVIDENCE.sha256`（8 项）、
> `docs/acceptance/verdicts/rows/D-089.json`（evidence_sha256 3 文件）的 sha256 钉死，
> 改写本体会破坏已裁定证据链。本文件自身**不加入**冻结清单（根清单自哈希
> `803b0829…649e` 已被 WS4 记录 §6.5 与终裁 B48-5 钉值，追加条目会使已记录的
> 323 项快照失配）；本文件以 `docs/research/2026-11-07-batch-48-record.md` §7 记录的
> sha256 钉值。全部更正均为本会话实跑复证，命令与输出摘录随条给出。

## 勘误 A：golden-v2/pages/*.A.json(24) 与 *.B.json(8) 的 `labelers[].ranAtUtc` 是预排时刻表，不是实际运行时间

- 事实（本会话逐文件实读）：32 份页级 JSON 的文件 mtime 全部落在
  **2026-11-06T15:59:42Z – 16:50:15Z**（51 分钟窗口），而 `ranAtUtc` 声称跨
  **2026-11-06T16:00:00Z – 2026-11-07T10:00:00Z**（约 18 小时：A 轮自第 2 页起
  00:00Z/00:20Z/00:40Z…步进，B 轮 08:50Z–10:00Z 每 10 分钟一格）。
  仅首份 A（003928，ranAtUtc=16:00:00Z）与其 mtime 15:59:42Z 大致吻合。
- 审计口径（更正）：`ranAtUtc` 字段**不得**用作执行时间线证据；A/B 两轮的先后与耗时
  证据以文件 mtime + git 提交为准。标注内容本身（框坐标、sha、声明）不受影响。
- 后续批次规范：该字段应回填实际写盘时刻，或改名 `scheduledSlotUtc` 以免误读。

## 勘误 B：GUIDELINE v1.1（横拍坐标补遗）的绑定链补全

- 事实（本会话实算）：现行 `golden-v2/GUIDELINE.md` 全文 sha256 =
  `38763dca7c33db1bbf6103cf4dc1f89629de3a345f4842fd046b532f4a94f480`（v1.1，含 §8 补遗）；
  全部 32 份页级 JSON 的 `labelers[].promptSha256` = `408d3337…a980`（v1 基础版）；
  32 份 JSON 无任何一处出现 `38763dca`。
- 说明：GUIDELINE §8:80–81 自身已声明该约定（`promptSha256` 仍指 v1 基础版字节摘要，
  补遗字节摘要"见批 48 记录文件"）；记录 §1.1 已记双 sha。本勘误把绑定落为显式全量值：
  **v2 标注的指引绑定 = v1 基础规则（`408d3337…a980`）+ v1.1 坐标补遗（
  `38763dca7c33…f480`，即现行 GUIDELINE.md 全文字节）**。v1.1 影响范围 = 8 个横拍页的
  坐标空间约定（upright 空间 + rawBox 双口径），不改变已标 003928 页（无旋转页）。
- 残余缺口（如实登记）：① 页级 JSON 不携 v1.1 sha 字段，绑定由本文件 + 记录 §1.1 承载；
  ② v1 基础版字节未单独归档，不可逐字节重建（仅知截断 sha `408d3337…a980`）；
  ③ B 轮 `guideline` 字段值 `aud08-model-gold-b48-v1.1` 为标签串而非 sha。

## 勘误 C：d-domain/00-index.md 步骤 3–4 的复现时间线两处更正

00-index.md 本体保持字节冻结（sha `0cfb79ee…e72d7`，钉于 D-089.json
evidence_sha256、d-domain/EVIDENCE.sha256、根 EVIDENCE.sha256 三处；三清单本会话
复验 8/8 与 323/323 全 OK）。更正以本条为准：

1. **"21s" 错误，实为 ≈2.9 秒**。00-index 称首个 CAPTURE_ASSESS
   （capture-assess:6b37df03…）"SUCCEEDED（attempt=1，21s）"；`device-task-lifecycle.log`
   该任务 created=1793991321290 → completed=1793991324223，**差 2933ms**。
   第二条任务（capture-assess:cdef39e2…）1793991429275→1793991457729 = 28454ms ≈ 28.5s，
   00-index 写 "29s" 属四舍五入、可接受。SUCCEEDED + attempt=1 事实由 lifecycle log
   独立成立，D-089 判据不受影响。
2. **capability-test-msg.txt 不是 18:46/18:47 两次测试的当时摘录**。00-index 把该文件
   直接列为那两次测试的证据；该文件头部自证 "测次1（首次配置后）: 2026-11-06T19:16:51Z"
   且 "两次均为同屏复测（Chrome 误置前后台后返回重跑）"——系 19:16:51Z 复测产物，
   只能佐证"测试通过消息"的形态，不能作为 18:46/18:47 两次测试的 contemporaneous 摘录。
   原始测试发生过的独立佐证：`logcat-key-lines.log` 在 18:45:09.894 / 18:45:12.159 /
   18:47:21.930 有 `OkHttpModelTransport.post` 真实出网栈（本会话 grep 实证）。

## 残余风险登记（非更正，属边界声明）

1. **A/B 一致性的可证伪边界**：35 对中 5 对 IoU=1.000（坐标逐字节相同；本会话复算
   分布：010034 三对、010859 一对、005711 一对），且全部 B mtime（自 16:47:16Z）
   晚于全部 A mtime（止于 16:45:51Z）——"写毕 B 前未读任何 A JSON"在数据上不可证伪。
   agreement 数字（mean 0.9641 / min 0.8448）只证明同标注者两轮自我稳定，
   不具跨标注员证明力；与记录 §1.2、终裁 B48-3.3（不签跨标注员一致性）一致。
2. **`heldOutUnusedForTuning=true` 仍是自证字段**：v2 split 块未内嵌选取规则
   （sha256(文件名) 排序规则只写在 11-04 计划 §4:80），"acceptance 未参与调参"
   最终不可机检，维持 UNVERIFIED（记录 §1.3、终裁 B48-3.4 已列不签）。
   间接证据（本会话复算）：按 24 页全集 sha256(name) 升序重算，dev 16/16、
   acceptance 8/8 与 v2 split 块全等；dev 四配置 runs 页清单与 acceptance 零交集；
   final 导出与两源逐页 0 mismatch。
