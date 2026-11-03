# KB 块级账本与 Android 16 KB 发布合规研究（R6）

- 日期：2026-11-02
- 范围：`D:\smart mistake book`（Android App + `knowledge-production/` 知识生产线）
- 结论消费者：KB 生产线重构（块级账本/幂等重跑）、发布门禁（16 KB 原生库对齐）、批次 41+ 设备轨
- 证据标记：
  - **【官方原文】**：本轮直接抓取官方页面/官方仓库原文，附 URL 与抓取日期。
  - **【本机实测 2026-11-02】**：本轮在本机执行的命令与输出，可原样复跑。
  - **【仓内前序实测】**：仓库内已有研究文档记录的同机结果，本轮未复跑。
  - **【推断】**：由两条以上官方事实推出，但不是官方原句。
  - **【单一来源/待验证】**：只拿到一个来源，或来源状态不明，不得当作定论。

---

## 0. 结论摘要

1. **账本不是“再加一张表”，而是把“块身份、内容身份、处理版本”三件事拆开。** 仅用 `chunk_id` 或现有 `fp` 做幂等键，会在“重排/改号”和“公式/数字改动”两类场景失效；本仓已有 `key_remap.csv` 与 `office_extract.py` 的 CJK-only 指纹，正好是这两类失败的一手证据。
2. **幂等键建议：`work_key = sha256(stage | block_key | content_sha | processor_version)`。** 数据库唯一约束是本地裁决者；调用外部模型时把同一个 `work_key` 作为 provider idempotency key，才是“不被重复计费”的双保险。Temporal 官方明确说“idempotency keys 由被调用服务执行，不由 Activity 本身执行”。
3. **状态机保留 `pending → judged/failed` 三态，`running` 用租约字段表达，不新增第四态。** `attempts/last_error/failure_class` 记录尝试；`payload_path` 指向内容寻址的 Claim Check；`content_sha` 同时用于完整性校验与失效判断。
4. **failed-only 重开是显式运维动作，不是自动重试。** 只重开 `failed` 且 `failure_class in (transient, policy)` 且 `attempts < max_attempts` 的行；永久失败必须带 `--include-permanent --reason`。这与 Temporal 的 non-retryable errors、LangGraph 的 retry policy + error_handler 边界一致。
5. **16 KB 的现行官方硬线是 2027-02-01（更新无法发布），不是 2025-11-01。** 2025-11-01 是 2025 年官方博客的旧口径；当前 `developer.android.com/guide/practices/page-sizes`（2026-10-16 更新）写的是 2027-02-01。顺延公告原文本轮未找到，标【单一来源/待验证】。
6. **ZIP 对齐与 ELF 对齐是两层检查，缺一不可。** `zipalign -c -P 16` 只证明未压缩 `.so` 在 ZIP 内的偏移；`llvm-objdump -p | grep LOAD` / `llvm-readelf -Wl` 才证明 ELF 段 `p_align >= 2**14` 与 RELRO 规则。
7. **本仓当前成品已合规（本轮实测）**：debug/release APK 各 14 个 `.so`（arm64-v8a 7 + x86_64 7），两个 APK 的全部 `.so` 均为 `STORED` 且 ZIP data offset 16 KB 对齐（Python 断言）；debug APK 的 `zipalign` 输出 `Verification successful`；debug 与 release APK 内均 14/14 的 `PT_LOAD p_align=0x4000`，RELRO 判据全部通过。AAB/bundletool 与 arm64 真机 16 KB 装载未验证。
8. **“SQLite 不是风险点”只对平台 SQLite 成立，不是官方原句。** 本仓用 `androidx.sqlite:sqlite-framework`，APK 里没有 `libsqlite*.so`，所以本仓不构成风险；但 `sqlite-android`、SQLCipher、SQLitePCLRaw/e_sqlite3 等自带 `.so` 的 SQLite 仍是风险。
9. **发布前最易漏的三项：16 KB 原生库、Data safety（含第三方 SDK 与 BYOK 出境）、隐私政策（应用内也要有链接）。** 本仓权限面很小，不是本轮 top 3，但新增敏感权限时必须走 Play 权限声明与运行时请求。

---

## 1. 块级任务账本（ledger）模式

### 1.1 三家官方模式的原话与边界

#### Temporal（Activity 级幂等 + 重试策略）

来源：<https://docs.temporal.io/activity-definition>（抓取 2026-11-02）；<https://docs.temporal.io/encyclopedia/retry-policies>（抓取 2026-11-02）。

【官方原文】

> "Temporal recommends that Activities be idempotent. Idempotence means that performing an operation multiple times has the same result as performing it once."

> "By design, completed Activities will not re-execute as part of a Workflow Replay. However, Activities won't record to the Event History until they return or produce an error. If an Activity fails to report to the server at all, it will be retried."

> "You can achieve idempotency in your application through the use of unique identifiers, known as idempotency keys, which are used to detect duplicate requests. These are enforced by the service you are calling from your Activity, not by the Activity itself."

> "You can use a combination of the Workflow Run ID and the Activity ID as an idempotency key since this is guaranteed to be consistent across retry attempts but unique among Workflow Executions."

> "Temporal guarantees that the Activity will be observed as completed exactly once. However, the Activity may be executed multiple times and may even partially complete more than once during this process."

Retry Policy 官方默认值（同页）：

> "Initial Interval = 1 second; Backoff Coefficient = 2.0; Maximum Interval = 100 × Initial Interval; Maximum Attempts = ∞; Non-Retryable Errors = []"

> "Permanent failures, by definition, require you to make some change to your logic or your input. Therefore, it is better to surface them than to retry them."

适用边界：

- Temporal 保证的是**工作流/Activity 的“被观察为完成一次”**，不保证 Activity 内部的副作用只执行一次；Activity 可能执行多次、部分完成多次。
- 幂等键必须由**下游服务**执行；Temporal 只提供 `Workflow Run ID + Activity ID` 这类稳定标识，不替调用方去重。
- 对本仓：本地脚本没有 Temporal Service，不需要引入 Temporal；可借鉴的是“**稳定 ID + 重试策略 + 非重试错误分类**”三件事，落成 SQLite 账本即可。

#### LangGraph（checkpoint / super-step / 重试）

来源：<https://docs.langchain.com/oss/python/langgraph/checkpointers>（抓取 2026-11-02）；<https://docs.langchain.com/oss/python/langgraph/fault-tolerance>（抓取 2026-11-02）。

【官方原文】

> "A checkpointer saves a snapshot of graph state at each super-step, organized into threads."

> "Checkpointing provides fault-tolerance and error recovery: if one or more nodes fail at a given superstep, you can restart your graph from the last successful step."

> "When you resume graph execution from that super-step you don't re-run the successful nodes."

> "Replay re-executes steps from a prior checkpoint. Invoke the graph with a prior `checkpoint_id` to re-run nodes after that checkpoint. Nodes before the checkpoint are skipped (their results are already saved). Nodes after the checkpoint re-execute, including any LLM calls, API requests, or interrupts — which are always re-triggered during replay."

> "`max_attempts` | `int` | `3` | Maximum number of attempts, including the first."

> "An error handler runs after a node fails and all retries are exhausted."

> "In every mode, a crash before the first checkpoint becomes durable leaves the thread indistinguishable from a thread that was never invoked... If you need to distinguish 'never accepted' from 'accepted but lost', record admission outside the checkpointer, for example by committing an outbox row to your own store before you call `invoke`."

适用边界：

- checkpointer 保存的是**线程状态快照**，不是“每块工作的业务账本”；它不提供业务幂等键，也不替节点内的外部调用去重。
- 恢复粒度是 super-step / checkpoint；checkpoint 之后的节点（包括 LLM 调用）会重跑。因此节点内的写操作仍必须幂等。
- 对本仓：可借鉴“**先提交 admission/outbox 行，再调用外部模型**”，以及“**成功后不再重跑已完成步骤**”；但不要用 LangGraph checkpoint 替代块级 ledger。

#### Azure Claim Check（大 payload 外置 + 令牌）

来源：<https://learn.microsoft.com/en-us/azure/architecture/patterns/claim-check>（抓取 2026-11-02）。

【官方原文】

> "Store a large message payload in an external data store and send only a reference to the stored payload through the messaging system."

> "Payload write and token publication occur in separate systems and aren't in one atomic transaction. A failure after payload write but before token publication can leave a payload orphaned. A token published before its payload is retrievable can mean consumers receive references they can't resolve. Publish the token only after the payload write succeeds."

> "Because retries can deliver the same claim check more than once, use the Idempotent Consumer pattern to process duplicates safely."

> "When consumers must verify payload integrity, include a content hash or digital signature in the message. Define a protocol for mismatches, such as rejecting the payload, retrying retrieval, or routing the message for investigation."

适用边界：

- 适用于“payload 经常超过消息大小限制 / 大消息占用 broker 资源 / payload 含敏感信息 / 多次序列化”的场景；不适用于“低延迟优先且 payload 本来就在限制内”的场景（官方明确列为不适用）。
- 引入额外一跳（先写存储、再取 payload），并需要定义 payload 生命周期归属、token 与 payload 的一致性、消费者重复处理。
- 对本仓：块文本/图片就是“大 payload”，`payload_path` 就是 claim check；`content_sha` 就是官方建议的完整性哈希。

#### 补充：Azure Idempotent Consumer（去重键的官方细则）

来源：<https://learn.microsoft.com/en-us/azure/architecture/patterns/idempotent-consumer>（抓取 2026-11-02）。

【官方原文】

> "Create an idempotent consumer by having the consumer keep a record of messages it successfully processes and skip any messages it already processed, based on a stable identifier that survives redelivery."

> "Use either a producer-assigned message identifier or a business-level idempotency key that identifies the specific logical operation, not a shared correlation context that several messages can carry."

> "Don't key on transport-level identifiers that the broker regenerates upon redelivery, or on values derived from delivery attempts."

> "Avoid this failure window by writing the deduplication marker and the business side effects in the same transaction."

> "Use a uniqueness constraint on the deduplication key such that two transactions can attempt to insert a key, but only one can succeed."

> "Record the key with an *in-progress* state, and then perform the external action. Update the record to *completed* and store the outcome."

适用边界：

- 这是“消费者侧”模式；它不替代生产者侧去重，也不替代 broker 的 duplicate detection。
- 去重记录要有保留期，至少覆盖 broker 可能重投的窗口；本仓账本是永久记录，天然满足。
- 对本仓：`work_key` 唯一约束 = “deduplication key + uniqueness constraint”；`pending/judged/failed` = “in-progress/completed/failed”；外部模型调用前后两阶段提交 = 官方两阶段法。

#### 模式对照

| 模式 | 核心原语 | 保证什么 | 不保证什么 | 对本仓的用法 |
|---|---|---|---|---|
| Temporal | Workflow Run ID + Activity ID；Retry Policy | 完成状态“被观察为一次”；重试可恢复 | Activity 内副作用只执行一次 | 借鉴稳定 ID、重试上限、non-retryable 分类 |
| LangGraph | checkpoint / super-step | 从最后成功步骤恢复，不重跑已完成节点 | 节点内外部调用不重复；业务去重 | 借鉴“先 admission 再调用”“成功后不重跑” |
| Azure Claim Check | 外置 payload + 引用 token + content hash | 大 payload 不进消息体；可校验完整性 | token 与 payload 原子性；重复消费 | `payload_path` + `content_sha` + 原子落盘 |
| Azure Idempotent Consumer | 稳定 dedup key + 唯一约束 + 两阶段状态 | 重复投递不产生重复副作用 | 跨服务自动传播 | `work_key` 唯一约束 + `pending/judged/failed` |

---

### 1.2 账本 schema 建议

#### 设计原则

1. **逻辑身份与内容身份分离**：`block_key` 回答“这是哪一块”，`content_sha` 回答“这一块的内容是不是变了”。
2. **处理版本显式化**：`processor_version` 包含 prompt 模板哈希、模型 ID/版本、规则版本；换模型/换提示词是有意的新工作，不是重试。
3. **payload 外置**：`payload_path` 指向内容寻址存储；账本只存路径与哈希，不存大文本/图片。
4. **三态 + 租约**：`status` 只有 `pending/judged/failed`；并发用 `lease_owner/lease_expires_at`，不新增 `running` 态。
5. **尝试历史不可覆盖**：`attempts` 是计数，`attempt_log` 是逐次记录；`last_error` 只保留最近一次，历史在 log 里。

#### DDL（SQLite，建议放 `tools/kb_ledger/schema.sql`）

```sql
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS block_ledger (
  work_key           TEXT PRIMARY KEY,          -- sha256(stage|block_key|content_sha|processor_version)
  block_key          TEXT NOT NULL,             -- sha256(source_rel|canonical_locator)
  source_rel         TEXT NOT NULL,             -- POSIX 相对路径
  source_locator     TEXT NOT NULL,             -- 规范化 JSON：page/bbox/ordinal/span
  stage              TEXT NOT NULL,             -- transcribe|judge|extract|promote
  content_sha        TEXT NOT NULL,             -- sha256(canonical payload bytes)
  canon_version      TEXT NOT NULL,             -- 例：text-nfc-lf-v1 / raw-bytes-v1
  processor_version  TEXT NOT NULL,             -- prompt_hash:model_id:model_version:rule_version
  status             TEXT NOT NULL CHECK (status IN ('pending','judged','failed')),
  attempts           INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  last_error         TEXT,
  failure_class      TEXT CHECK (failure_class IN ('transient','permanent','integrity','policy')),
  payload_path       TEXT,                      -- Claim Check：内容寻址的输入块
  result_path        TEXT,                      -- 判定/抽取结果（JSON/CSV 片段）
  lease_owner        TEXT,
  lease_expires_at   TEXT,
  reopen_epoch       INTEGER NOT NULL DEFAULT 0,
  supersedes         TEXT,                      -- 内容变更前的 work_key
  superseded_by      TEXT,                      -- 内容变更后的 work_key
  created_at         TEXT NOT NULL,
  updated_at         TEXT NOT NULL,
  completed_at       TEXT,
  CHECK (status <> 'failed' OR last_error IS NOT NULL),
  CHECK (status <> 'judged' OR result_path IS NOT NULL)
);

-- 同一逻辑块 + 同一 stage 只能有一条“当前版本”
CREATE UNIQUE INDEX IF NOT EXISTS ux_block_current
  ON block_ledger(stage, block_key)
  WHERE superseded_by IS NULL;

-- 防止哈希实现变化导致唯一性失效：显式四元组也唯一
CREATE UNIQUE INDEX IF NOT EXISTS ux_block_work
  ON block_ledger(stage, block_key, content_sha, processor_version);

CREATE INDEX IF NOT EXISTS ix_block_status
  ON block_ledger(stage, status, created_at);

CREATE TABLE IF NOT EXISTS attempt_log (
  work_key       TEXT NOT NULL,
  attempt_no     INTEGER NOT NULL,
  started_at     TEXT NOT NULL,
  ended_at       TEXT,
  outcome        TEXT NOT NULL CHECK (outcome IN ('claimed','judged','failed','lease_expired','reopened')),
  failure_class  TEXT,
  error          TEXT,
  provider_request_id TEXT,                     -- 供应商返回的 request id（可审计）
  cost_units     REAL,                          -- 可选：token/调用次数
  PRIMARY KEY (work_key, attempt_no)
);
```

说明：

- `work_key` 是哈希，但 `ux_block_work` 仍对四元组加唯一约束；不要只信哈希。
- `payload_path` 与 `result_path` 分开：前者是输入（claim check），后者是输出。任务要求里的 `payload_path` 按“输入块路径”实现。
- `CHECK (status <> 'judged' OR result_path IS NOT NULL)` 防止“标了 judged 但没结果”的假完成。
- `failure_class='integrity'` 专用于 `content_sha` 与 payload 不匹配；这种失败不应调用模型，也不应自动重开。

#### 状态机

```text
                 claim (attempts += 1, lease)
  pending ─────────────────────────────────────────────┐
     ▲                                                  │
     │                                                  ▼
     │                                          ┌──────────────┐
     │                                          │  处理/调用   │
     │                                          └──────┬───────┘
     │                                                 │
     │                    success + result_path        │        error
     │                    + content_sha verify         │        + failure_class
     │                                                 ▼
     │                                            ┌─────────┐
     └──────── reopen (failed-only) ──────────────│ judged  │
                                                  └─────────┘
                                                  ┌─────────┐
                                                  │ failed  │
                                                  └─────────┘
```

- `judged` 是终态；内容变更不回到 `pending`，而是新建一条 `work_key`，旧行 `superseded_by` 指向新行。
- `failed` 是“本次内容+本次处理版本”的终态；只有 `reopen` 能把它改回 `pending`。
- 租约过期不自动改状态；下一次 `claim` 把过期租约视为可抢占，`attempts += 1`，并写 `lease_expired` attempt_log。

#### 原子 claim（SQLite）

```sql
BEGIN IMMEDIATE;

UPDATE block_ledger
SET lease_owner = :owner,
    lease_expires_at = :expires,
    attempts = attempts + 1,
    updated_at = :now
WHERE work_key = (
  SELECT work_key
  FROM block_ledger
  WHERE stage = :stage
    AND status = 'pending'
    AND (lease_expires_at IS NULL OR lease_expires_at < :now)
  ORDER BY created_at, work_key
  LIMIT 1
)
RETURNING *;

COMMIT;
```

- `BEGIN IMMEDIATE` 先拿写锁，避免两个 worker 同时选中同一行。
- `RETURNING` 需要 SQLite ≥ 3.35；如果运行环境更老，改成“先 SELECT 再 UPDATE ... WHERE work_key=? AND (lease_expires_at IS NULL OR ...)”并检查 `rowcount==1`。
- 领取后立刻写 `attempt_log(outcome='claimed', attempt_no=attempts)`。

---

### 1.3 幂等键设计：什么字段组合能保证不重复计费/写入

#### 三层键

| 键 | 定义 | 作用 | 不能用什么 |
|---|---|---|---|
| `block_key` | `sha256(source_rel \x1f canonical_locator)` | 逻辑块身份，跨重试稳定 | 时间戳、attempt 号、worker id、可被重排的稠密序号 |
| `content_sha` | `sha256(canonical_payload_bytes)` | 内容身份，内容变则变 | 现有 `fp`（只哈希 CJK，见 §1.5） |
| `work_key` | `sha256(stage \x1f block_key \x1f content_sha \x1f processor_version)` | 一次“逻辑工作”的幂等键 | 只用 `chunk_id`；只用文件路径；只用 `fp` |

`processor_version` 建议格式：

```text
prompt:<sha256(prompt_template)[:12]>|model:<provider/model>@<version>|rules:<ruleset>@<version>|canon:<canon_version>
```

这样“换模型/换提示词/换规则”会产生新的 `work_key`，是**有意的新计费**；而“同一模型同一提示词的重试”复用旧 `work_key`，不会重复计费。

#### 保证“不被重复计费”的五个条件

1. **本地唯一约束**：`work_key` 主键 + `ux_block_work` 四元组唯一。插入用 `INSERT ... ON CONFLICT DO NOTHING`；如果已存在且 `status='judged'`，直接复用结果。
2. **远端幂等键**：调用模型/OCR 时把 `work_key`（或由其派生的 UUIDv5）作为 provider 的 idempotency key。Temporal 官方原话说幂等键“由被调用的服务执行”；只做本地去重不够，因为“远端已扣费、本地未提交”的崩溃窗口仍然存在。
3. **两阶段提交**：调用前写 `attempt_log(outcome='claimed')` / 或账本 `status='pending'` + `lease`；调用成功后先原子写 `result_path`，再 `UPDATE ... SET status='judged'`。崩溃后重试同一 `work_key`，provider 返回缓存结果或本地发现 `result_path` 已存在且哈希匹配，直接补记 `judged`。
4. **输出内容寻址**：`result_path = store/<sha256(result_bytes)[:2]>/<sha256>`；写临时文件 + `fsync` + `os.replace` 原子改名；`judged` 只在改名成功后写。重复执行同一 `work_key` 不会产生第二份输出。
5. **并发唯一裁决**：`BEGIN IMMEDIATE` + 租约 + 唯一约束。Azure 官方明确要求“用唯一约束让数据库做唯一裁决者”，不要用“先查再写”的 check-then-set。

#### 内容变更时如何失效

- **默认失效**：`content_sha` 变化 → `work_key` 变化 → 新行。旧行的 `superseded_by` 指向新行；所有“当前结果”查询必须加 `superseded_by IS NULL`。
- **旧结果保留**：旧行不删除，保留审计与回滚能力；如果内容改回旧值且 `processor_version` 相同，旧 `work_key` 会命中已 `judged` 的行，直接复用，不重复计费。
- **显式豁免**：如果只是排版/空白变化，调用方可以用 `--reuse-content-sha <old>` 或 `--allow-stale` 复用旧结果；这是有意的运维决定，必须写 `reopen_reason`/`supersede_reason`，不能默认发生。
- **完整性校验**：`claim` 时重新计算 payload 的 `content_sha`；与账本不一致 → `failed/failure_class='integrity'`，不调用模型。`judge` 提交前再次校验，防止 payload 在队列中被替换。
- **不要用 `fp` 当 content_sha**：本仓 `office_extract.py:173-175` 的 `_fp` 是“只取 CJK 字符后 sha256”，数学公式、数字、拉丁字母变化不会改变指纹。用它做失效判断，会出现“公式改了但判定不重跑”的静默错误。

#### 与现有 `chunk_id` 的关系

- `chunk_id` 形如 `03a638bfa0-001`，是 `sha256(rel_path)[:10] + 序号`；它适合做“池内引用”，不适合做“跨重排的稳定块身份”。仓库已有 `tools/kb_coverage/tables/key_remap.csv`，说明重排/重编号真实发生过。
- 建议：`block_key` 用 `source_rel + locator`（PDF 页 + 块序号/bbox；docx/pptx 段落范围；transcript 页 + span），`chunk_id` 只作为 `source_locator` 的一个字段/兼容列保留。这样重排不会改变逻辑块身份。

---

### 1.4 failed-only 重开

#### 语义

```text
reopen(stage, max_attempts, include_permanent=False, reason)
  SELECT * FROM block_ledger
  WHERE stage = :stage
    AND status = 'failed'
    AND attempts < :max_attempts
    AND (failure_class IN ('transient','policy')
         OR (:include_permanent AND failure_class = 'permanent'))
  → status='pending', lease_owner=NULL, lease_expires_at=NULL,
    reopen_epoch=reopen_epoch+1, updated_at=now
  → attempt_log(outcome='reopened', error=last_error, ...)
```

规则：

- **只重开 failed**：`judged` 永远不重开；内容变更走新 `work_key`，不是重开。
- **保留 attempts**：重开不重置 `attempts`，否则会无限重试；`reopen_epoch` 单独计数，便于审计“重开过几轮”。
- **永久失败要显式**：`failure_class='permanent'` 默认不重开；必须 `--include-permanent --reason "..."`。
- **幂等**：同一 filter 连续执行两次，第二次选中 0 行（已经是 pending），不会重复加 `reopen_epoch`。
- **完整性失败不重开**：`failure_class='integrity'` 说明 payload 与账本不一致，重开只会重复失败；必须先修 payload/账本。
- **重开前检查依赖**：如果 `payload_path` 不存在或 sha 不匹配，先把行标 `failed/integrity`，不要放回 pending。

对应官方边界：

- Temporal：永久失败应“surface”而不是重试；non-retryable errors 默认空，需要显式配置。
- LangGraph：`error_handler` 只在 retries exhausted 后运行；重试策略与错误处理解耦。
- Azure Idempotent Consumer：重复投递是正常现象；消费者要“skip or absorb”，不是无限重试。

---

### 1.5 本仓现状：为什么现在需要块级账本

| 现状 | 位置 | 问题 |
|---|---|---|
| 文档级状态表 | `tools/kb_coverage/tables/extraction_state.csv` | 只有 `rel_path/state/output_ref`，`output_ref` 是一串逗号分隔的 chunk id；没有 attempts、last_error、payload_path、content_sha |
| 块池 | `tools/kb_coverage/tables/judgment_slices/*.jsonl`（235 个文件） | 每条有 `chunk_rel/chunk_id/heading/text/subject/fp`；没有处理状态 |
| 判定结果 | `knowledge-production/judgment-verdicts/*.csv` | 按切片写 CSV；没有与“哪次尝试/哪个 payload”绑定 |
| 一次性脚本 | `_judge30_build.py`、`_judge30_part*.py` 等 | `assert len(blocks) == 300`、按 index 组装；重跑/部分失败靠人工，不是账本 |
| 引用完整性门 | `tools/kb_coverage/verify_judgment_fp.py` | 能查“判定悬空/撞键/fp 不一致”，但不能查“这块是否已计费/是否重复处理/失败原因” |
| 内容指纹 | `tools/kb_coverage/office_extract.py:173-175` | `_fp` 只哈希 CJK；公式/数字/拉丁文变化不可见 |
| 重编号证据 | `tools/kb_coverage/tables/key_remap.csv` | 真实发生过 chunk id 重排；`chunk_id` 不能单独当稳定身份 |

**账本要消灭的具体失败**：

1. 同一块被两个 worker/两次重跑重复判定，重复写 verdict CSV 或重复调用模型。
2. 某块失败后没人知道失败原因/尝试次数，只能“整批重跑”。
3. 块内容被修正（公式/数字），旧判定仍被 materialize，因为 `fp` 没变。
4. 判定 CSV 引用的 `chunk_id` 在重排后指向了另一块（`key_remap.csv` 已证明这是真实风险）。
5. 进度只能靠“数文件/数 CSV 行”，无法回答“还有多少块 pending/failed、失败分类是什么”。

---

## 2. Android 16 KB page size：事实与可执行核验

### 2.1 强制时间与适用范围

主源：<https://developer.android.com/guide/practices/page-sizes>（页面标注 Last updated 2026-10-16；本轮通过 `mcp__searchd__extract_content` 抓取成功，WebFetch 直连超时）。

【官方原文】

> "To ensure your app works correctly on the latest versions of Android, all apps targeting Android 15 (API level 35) and higher must support 16 KB memory page sizes on 64-bit devices on Google Play. Starting February 1, 2027, if your app updates don't support 16 KB memory page sizes, you won't be able to release these updates."

> "Historically, Android has only supported 4 KB memory page sizes... Beginning with Android 15, AOSP supports devices that are configured to use a page size of 16 KB (16 KB devices). If your app uses any NDK libraries, either directly or indirectly through an SDK, then you will need to rebuild your app for it to work on these 16 KB devices."

> "If your app only uses code written in the Java programming language or in Kotlin, including all libraries or SDKs, then your app already supports 16 KB devices. Nevertheless, we recommend that you test your app in a 16 KB environment to verify that there are no unexpected regressions in app behavior."

时间线口径冲突（必须显式处理）：

- 2025-07-10 官方博客 <https://android-developers.googleblog.com/2025/07/transition-to-16-kb-page-sizes-android-apps-games-android-studio.html> 仍写：
  > "Starting November 1st, 2025, all new apps and app updates that use native C/C++ code targeting Android 15+ devices submitted to Google Play must support 16 KB page sizes."
- 当前官方指南（2026-10-16 更新）写 **2027-02-01**，且只提“app updates”。
- 结论：**以当前指南的 2027-02-01 为准**；2025-11-01 是旧口径。顺延的官方公告原文本轮未找到，标【单一来源/待验证】。不要把 2025-11-01 当成“已经过期所以不用管”——它是旧硬线，不是“没有要求”。

适用范围：

- targetSdk ≥ 35（本仓 `targetSdk=36`，命中）；64 位设备（arm64-v8a / x86_64）。
- 只要有**随 App 分发的 native 代码**（直接 NDK，或通过 SDK/AAR 间接带入 `.so`），就必须处理。
- 纯 Java/Kotlin（包括所有库/SDK 都不带 `.so`）默认已支持；官方仍建议在 16 KB 环境测试。

### 2.2 检测命令（ZIP 层 + ELF 层）

#### ZIP 层

【官方原文】

> "Run the `zipalign` command-line tool on your app's APK file... `SDK_ROOT_LOCATION/Android/sdk/build-tools/35.0.0/zipalign -v -c -P 16 4 APK_NAME.apk` ... The last line of the output will say 'Verification successful' if all of the shared libraries are aligned correctly."

> "16 KB devices require apps that ship with uncompressed shared libraries to align them on a 16 KB zip-aligned boundary."

命令（build-tools ≥ 35.0.0）：

```bash
# Linux/macOS
$SDK/build-tools/35.0.0/zipalign -c -P 16 -v 4 app.apk

# Windows PowerShell / Git Bash（本机 build-tools 37.0.0 实测）
C:/Android/Sdk/build-tools/37.0.0/zipalign.exe -c -P 16 -v 4 app.apk
```

- 通过标志：末行 `Verification successful`，exit 0。
- 只对**未压缩（STORED）** 的 `.so` 有意义；压缩式打包不适用该判据。
- 官方页面同时出现过 `-v -c -P 16 4` 与 `-c -P 16 -v 4` 两种写法，flag 顺序不影响语义。

#### ELF 层

【官方原文】

> "Run the following command on each shared object file: `.../llvm-objdump -p SHARED_OBJECT_FILE.so | grep LOAD` ... Check the output lines to ensure that the load segments don't have values less than `2**14`. If any load segments are `2**13`, `2**12`, or lower values, you'll need to update the packaging for those libraries..."

> "Run the following command on each shared object file: `.../llvm-readelf -Wl SHARED_OBJECT_FILE.so | grep 'RELRO\\|Type'` ... check the RELRO segment alignment by summing its virtual offset address (VirtAddr) with the segment memory size (MemSiz) and dividing by 16 KB (0x4000). If the remainder (modulo) is zero, then the RELRO segment is 16 KB aligned."

> "Formula: (VirtAddr + MemSiz) % 0x4000 == 0"

> "If the GNU_RELRO segment is in its own LOAD segment, the non-zero value returned is still compliant."

命令：

```bash
# 需要 NDK（本机未安装 NDK，所以本轮用等判据 Python 解析）
$NDK/toolchains/llvm/prebuilt/<host>/bin/llvm-objdump -p libfoo.so | grep LOAD
$NDK/toolchains/llvm/prebuilt/<host>/bin/llvm-readelf -Wl libfoo.so | grep 'RELRO\|Type'
```

判据：

1. 每个 `PT_LOAD` 的 `p_align >= 16384`（`2**14`）；出现 `2**13` / `2**12` 即不合规。
2. `p_offset % p_align == p_vaddr % p_align`（同余）也应成立；官方示例的 off/vaddr 同余。
3. `GNU_RELRO` 的 `(VirtAddr + MemSiz) % 0x4000 == 0`；若不为 0，只有当 RELRO **独占一个 LOAD 段**、且余数页内没有可写 LOAD 段时，才按官方豁免判合规。
4. 违规形态：RELRO 终点后的同一 16 KB 页内还有可写段 → 16 KB 设备上 linker 的 `mprotect` 把整页变只读 → 后续写 `.data` SIGSEGV。

#### AAB / Play 分发层

【官方原文】

> "To see the alignment of your bundle, use: `bundletool dump config --bundle=<my .aab> | grep alignment`. If you see `PAGE_ALIGNMENT_16K` then you know your bundle requests 16 KB zip alignment. If you see `PAGE_ALIGNMENT_4K`, this instructs the APK built from this AAB to have 4 KB aligned `.so` files in the zip file."

注意：官方说 AGP 8.3–8.5 默认 16 KB 对齐，但 **bundletool 默认不 zipalign APK**；可能出现“本地 APK 能装，Play 从 AAB 生成的 APK 装不上”。AGP ≥ 8.5.1 才默认处理。

#### 设备侧

```bash
adb shell getconf PAGE_SIZE   # 期望 16384
```

16 KB 兼容模式（backcompat）：如果 `.so` 的 LOAD 段只有 4 KB 对齐，或 ZIP 内未压缩 `.so` 只有 4 KB 对齐，包管理器可能让 App 以兼容模式运行，并显示警告；官方明确“for best reliability and stability, apps should still be 16 KB aligned”。不要拿兼容模式当合规。

### 2.3 常见风险库

| 库/类别 | 风险 | 证据 | 状态 |
|---|---|---|---|
| **LiteRT / TFLite** | 旧版本 `.so` 未 16 KB 对齐；LiteRT v1.4.0 release notes 写 "Fixed support for 16 kb page" | <https://github.com/google-ai-edge/LiteRT/releases/tag/v1.4.0>（2025-11-07）【官方仓库】 | 本仓 2.1.6 的 64 位 `.so` 本轮实测 PASS |
| **ML Kit（bundled OCR）** | 32 位 `armeabi-v7a` / `x86` 的 `libmlkit_google_ocr_pipeline.so` 存在 4 KB LOAD；x86 还有 RELRO 与可写段同页 | 仓内前序实测 `docs/research/2026-10-25-16kb-arm64-api23-build.md` §2.3；本仓当前 `abiFilters` 已裁掉 32 位 | 64 位 PASS；32 位 FAIL 但不进 APK |
| **ONNX Runtime** | Android 预编译包长期按 4 KB 对齐；官方 issue 请求 16 KB 支持 | <https://github.com/microsoft/onnxruntime/issues/26228>（2025-10-02）【单一来源/待验证】 | 使用前必须按 §2.2 自测；不要凭 issue 状态推断已修复 |
| **自带 SQLite 的库** | `sqlite-android`、SQLCipher、SQLitePCLRaw/e_sqlite3、requery 等会随 App 带 `libsqlite*.so` | 官方 16 KB 指南的通用规则 + 具体 AAR 需自测 | 风险真实；平台 SQLite 不适用（见 §2.4） |
| **Flutter / React Native / Unity / OpenCV / FFmpeg / MediaPipe / OpenXR runtime** | 任何随包 `.so` 都可能 4 KB 对齐；引擎/插件版本决定 | 官方指南的“SDK 间接带入也算”规则；本轮未逐库取得官方版本矩阵 | 类别级风险；逐个用 §2.2 检查，标【待验证】 |
| **`libc++_shared.so`（NDK ≤ r26）** | 部分旧 NDK 的 `libc++_shared.so` 不是 16 KB 对齐 | 官方指南明确提醒 r26 及更低 | 升级 NDK r28+ 或静态链接 libc++ |
| **androidx.graphics.path / datastore** | 本仓实测 PASS | 本轮 APK 14/14 PASS | 无动作 |

补充：

- LiteRT 2.2.0 起 `minSdk` 提到 24（本仓 minSdk 23，故钉 2.1.6）；这是产品/兼容性约束，不是 16 KB 约束。LiteRT 2.1.6 的 64 位 `.so` 本轮已实测 PASS。
- ML Kit 的 32 位失败是“升级路径也没有”的类型：仓内前序实测 Maven 最新 `text-recognition-bundled-common:17.0.0` 仍失败，最终处置是 `abiFilters` 裁掉 32 位，不是等升级。

### 2.4 “SQLite 不是风险点”是否有官方依据

**结论：官方没有一句“SQLite 不是风险点”。** 能成立的是下面这条推断链：

1. 官方 16 KB 指南把要求限定为“uses any NDK libraries, either directly or indirectly through an SDK”（§2.1 引文）。
2. 官方指南说“only uses code written in Java/Kotlin, including all libraries or SDKs”的 App 已经支持 16 KB。
3. AOSP 16 KB 页面 <https://source.android.com/docs/core/architecture/16kb-page-size/16kb> 说平台用户空间通过 `PRODUCT_MAX_PAGE_SIZE_SUPPORTED := 16384` 让 **platform ELF files** 以 16 KB 对齐构建。
4. 本仓 `core/database/build.gradle.kts:40` 使用 `androidx.sqlite:sqlite-framework`（版本 `2.7.0`，`gradle/libs.versions.toml:14,80`），它委托给 Android 平台 SQLite；本轮 APK 的 14 个 `.so` 中没有 `libsqlite*.so`。

因此：

- **平台 SQLite（`android.database.sqlite` / Room 默认路径）**：SQLite 引擎在系统镜像里，随平台 16 KB 对齐；App 不分发 SQLite `.so`，所以**本仓不构成风险**。这是【推断】，不是官方原句。
- **自带 SQLite 的库**：`sqlite-android`、SQLCipher、SQLitePCLRaw/e_sqlite3、requery 等会把 `libsqlite*.so` 打进 APK；这时 SQLite **就是**风险点，必须按 §2.2 检查。
- 验证方法：`unzip -l app.apk | grep -i sqlite`；本仓应为空。

### 2.5 本仓当前实测（2026-11-02）

构建面（读自仓库）：

| 项 | 值 | 出处 |
|---|---|---|
| AGP | 9.3.0 | `gradle/libs.versions.toml` |
| compileSdk / buildTools | 37 / 37.0.0 | `app/build.gradle.kts:16-17` |
| minSdk / targetSdk | 23 / 36 | `app/build.gradle.kts:28-29` |
| 打包 ABI | `arm64-v8a` + `x86_64` | `app/build.gradle.kts:46` |
| LiteRT | 2.1.6（钉版） | `gradle/libs.versions.toml` + `core/data/build.gradle.kts` 注释 |
| SQLite | `androidx.sqlite:sqlite-framework:2.7.0`（平台路径） | `core/database/build.gradle.kts:40` |

【本机实测 2026-11-02】ZIP 层：

```text
C:/Android/Sdk/build-tools/37.0.0/zipalign.exe -v -c -P 16 4 \
  app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk
→ ... Verification successful
→ exit=0
```

【本机实测 2026-11-02】ELF 层（Python 标准库解析 APK 内 `.so`，判据同 §2.2）：

```text
apk app-localFirst-debug.apk so_count 14
PASS lib/arm64-v8a/libLiteRt.so                 loads=['0x4000','0x4000','0x4000'] relro_rem=[4096]
PASS lib/arm64-v8a/libLiteRtClGlAccelerator.so  loads=['0x4000','0x4000','0x4000'] relro_rem=[0]
PASS lib/arm64-v8a/libandroidx.graphics.path.so loads=['0x4000','0x4000','0x4000'] relro_rem=[8192]
PASS lib/arm64-v8a/libdatastore_shared_counter.so loads=['0x4000','0x4000','0x4000','0x4000'] relro_rem=[0]
PASS lib/arm64-v8a/liblitert_jni.so             loads=['0x4000','0x4000','0x4000'] relro_rem=[4096]
PASS lib/arm64-v8a/libmlkit_google_ocr_pipeline.so loads=['0x4000','0x4000','0x4000'] relro_rem=[12288]
PASS lib/arm64-v8a/libquickjs.so                loads=['0x4000','0x4000','0x4000'] relro_rem=[4096]
PASS lib/x86_64/libLiteRt.so                    loads=['0x4000','0x4000','0x4000'] relro_rem=[12288]
PASS lib/x86_64/libLiteRtClGlAccelerator.so     loads=['0x4000','0x4000','0x4000'] relro_rem=[0]
PASS lib/x86_64/libandroidx.graphics.path.so    loads=['0x4000','0x4000','0x4000'] relro_rem=[12288]
PASS lib/x86_64/libdatastore_shared_counter.so  loads=['0x4000','0x4000','0x4000','0x4000'] relro_rem=[0]
PASS lib/x86_64/liblitert_jni.so                loads=['0x4000','0x4000','0x4000'] relro_rem=[0]
PASS lib/x86_64/libmlkit_google_ocr_pipeline.so loads=['0x4000','0x4000','0x4000'] relro_rem=[8192]
PASS lib/x86_64/libquickjs.so                   loads=['0x4000','0x4000','0x4000'] relro_rem=[12288]
bad= 0
```

说明：

- 所有 `relro_rem != 0` 的库，本轮按“RELRO 独占 LOAD / 余数页内无可写 LOAD”判 PASS；这与官方豁免一致。若要更保守，可再用 `llvm-readelf -Wl` 人工复核。
- debug 与 release APK 各有 14 个 `.so`，全部 `STORED`，且 ZIP data offset `% 16384 == 0`；两个 APK 的 ELF 解析均为 14/14 PASS（release 本轮单独复跑，`bad=[]`）。
- 一个容易踩的坑：用 `zipfile.ZipInfo.extra` 计算 data offset 会得到**假 FAIL**；`ZipInfo.extra` 是中央目录 extra，长度可能和本地文件头 extra 不同。正确做法是读本地文件头 `header_offset+28` 处的 `extra_len`，或用 `zipalign` 作为权威。本轮第一次计算就出现了 14/14 假 FAIL，修正后 14/14 PASS。
- `merged_native_libs` 中间产物里仍有 25 个 `.so`，其中 2 个 32 位 ML Kit 为 FAIL；它们**不进 APK**（`abiFilters` 已裁）。门禁必须检查 APK，不要检查 `merged_native_libs`，否则会误报。
- 未做：AAB/bundletool、arm64 真机 16 KB 装载、Play Console 预审。均标 UNVERIFIED。

### 2.6 端侧 16 KB 的可自动化判据

#### 脚本化断言（建议新增 `tools/ci/check_native_alignment.py`）

输入：

- `--apk-glob 'app/build/outputs/apk/**/*.apk'`（或显式 `--apk` 多次）
- `--require-abi arm64-v8a,x86_64`（默认）
- `--zipalign C:/Android/Sdk/build-tools/37.0.0/zipalign.exe`（可选；找不到就跳过 ZIP 工具，用 Python 解析本地文件头）

断言：

1. APK 内每个 `lib/<abi>/*.so` 必须是 `STORED`；data offset `% 16384 == 0`。
2. 解析 ELF program headers：
   - 每个 `PT_LOAD` 的 `p_align >= 16384`；
   - `p_offset % p_align == p_vaddr % p_align`；
   - `PT_GNU_RELRO` 的 `(vaddr+memsz) % 16384 == 0`，或 RELRO 独占 LOAD / 余数页内无可写 LOAD。
3. 如果 `--require-abi` 指定的 ABI 在 APK 里不存在，失败（防止“以为打了 arm64，其实没有”）。
4. 输出机器可读行：`FAIL<TAB>apk<TAB>abi<TAB>so<TAB>reason<TAB>owner`，exit 1。

最小 Python 片段（与 §2.5 实测同判据）：

```python
import struct, zipfile

PT_LOAD, PT_GNU_RELRO = 1, 0x6474E552

def check_elf(data):
    is64, le = data[4] == 2, data[5] == 1
    e = "<" if le else ">"
    if is64:
        phoff = struct.unpack_from(e + "Q", data, 0x20)[0]
        ents = struct.unpack_from(e + "H", data, 0x36)[0]
        num = struct.unpack_from(e + "H", data, 0x38)[0]
        fmt = e + "IIQQQQQQ"
    else:
        phoff = struct.unpack_from(e + "I", data, 0x1C)[0]
        ents = struct.unpack_from(e + "H", data, 0x2A)[0]
        num = struct.unpack_from(e + "H", data, 0x2C)[0]
        fmt = e + "IIIIIIII"
    loads, relros = [], []
    for i in range(num):
        f = struct.unpack_from(fmt, data, phoff + i * ents)
        if is64:
            t, fl, off, va, _pa, _fs, msz, al = f
        else:
            t, off, va, _pa, _fs, msz, fl, al = f
        if t == PT_LOAD:
            loads.append((off, va, msz, al, bool(fl & 2)))
        elif t == PT_GNU_RELRO:
            relros.append((va, msz))
    errs = []
    for off, va, _msz, al, _w in loads:
        if al < 16384:
            errs.append(f"LOAD p_align={al} (<16384)")
        elif al and off % al != va % al:
            errs.append(f"LOAD off/vaddr not congruent mod {al}")
    for rva, rmsz in relros:
        rend = rva + rmsz
        if rend % 16384 == 0:
            continue
        page_end = (rend + 16383) // 16384 * 16384
        clash = [hex(v) for (_o, v, m, _a, w) in loads if w and v < page_end and v + m > rend]
        if clash:
            errs.append(f"RELRO end=0x{rend:x} shares page with writable LOAD {clash}")
    return errs
```

ZIP 偏移的正确算法：

```python
with open(apk, "rb") as fh, zipfile.ZipFile(apk) as z:
    for info in z.infolist():
        if not info.filename.endswith(".so"):
            continue
        fh.seek(info.header_offset + 26)
        nlen, xlen = struct.unpack("<HH", fh.read(4))
        data_off = info.header_offset + 30 + nlen + xlen
        assert info.compress_type == zipfile.ZIP_STORED
        assert data_off % 16384 == 0
```

#### 失败时如何定位到具体依赖

三层定位：

1. **APK 内路径**：`lib/<abi>/<name>.so` 直接给出 ABI 与文件名。
2. **AAR 归属**：用 Gradle `ArtifactView` 遍历 runtime classpath，读每个 AAR 的 `jni/<abi>/*.so`，建立 `basename → group:name:version` 映射。示例 Kotlin 任务：

```kotlin
abstract class NativeLibOwners : DefaultTask() {
    @get:InputFiles
    abstract val runtimeClasspath: ConfigurableFileCollection

    @TaskAction
    fun run() {
        runtimeClasspath.files
            .filter { it.extension == "aar" }
            .forEach { aar ->
                java.util.zip.ZipFile(aar).use { z ->
                    z.entries().asSequence()
                        .filter { it.name.startsWith("jni/") && it.name.endsWith(".so") }
                        .forEach { println("${it.name}\t${aar.name}") }
                }
            }
    }
}
```

注册：

```kotlin
tasks.register<NativeLibOwners>("nativeLibOwners") {
    runtimeClasspath = configurations.getByName("localFirstReleaseRuntimeClasspath")
}
```

3. **人工兜底**：`./gradlew :app:dependencies --configuration localFirstReleaseRuntimeClasspath` + 在 Gradle 缓存中按 AAR 文件名搜索；`jetified-` 前缀去掉后再匹配坐标。

失败样例与处置：

```text
FAIL  app-localFirst-release.apk  armeabi-v7a  libmlkit_google_ocr_pipeline.so
      LOAD#0 p_align=4096  owner=com.google.mlkit:text-recognition-bundled-common:17.0.0
→ 处置：abiFilters 裁掉 armeabi-v7a/x86；或等上游修复（本仓前序实测无升级路径）
```

注意：

- 如果同一个 `.so` 基名出现在多个 AAR（例如 `libc++_shared.so`），输出所有候选 owner，不要猜。
- 本地 patched AAR（本仓 `litert-api` 的派生件在 `build/` 下）需要单独标注 owner 为 `com.google.ai.edge.litert:litert-api:2.1.6 (patched)`，否则会被误判为“未知”。
- 门禁只检查最终 APK；`merged_native_libs` 的 32 位 FAIL 是预期噪声。

---

## 3. 对本仓的落地建议

### 3.1 块级账本落哪个目录

建议新增：

```text
tools/kb_ledger/
  __init__.py
  schema.sql          # §1.2 DDL
  ledger.py           # init/claim/judge/fail/reopen/verify
  payload_store.py    # 内容寻址写读 + sha 校验
  cli.py              # python -m kb_ledger ...
  export.py           # 导出 committed CSV/JSON 摘要
```

存储：

- 运行库：`build/kb_ledger/ledger.sqlite3`（`build/` 已在版本控制之外；不提交二进制/可变状态）。
- payload：`build/kb_ledger/payloads/<sha[:2]>/<sha>`；写临时文件 + `fsync` + `os.replace`。
- 结果：`build/kb_ledger/results/<sha[:2]>/<sha>.json`（或按 slice 聚合，但路径必须可由账本定位）。
- 审计导出（提交）：`tools/kb_coverage/tables/block_ledger.csv` + `block_ledger_summary.json`；由 `export.py` 生成，不手工编辑。这与仓库现有 `tools/kb_coverage/tables/*.csv` 的“派生表”惯例一致。

不放在 `knowledge-production/` 的理由：那里是源材料、判定结果、审计记录，不是运行状态；把 SQLite/WAL 混进去会污染源目录，也会让 `git status` 噪声变大。

CLI 建议：

```bash
python -m kb_ledger init
python -m kb_ledger ingest --slices tools/kb_coverage/tables/judgment_slices --stage judge
python -m kb_ledger claim --stage judge --limit 50 --owner "$HOSTNAME"
python -m kb_ledger judge --work-key "$WK" --result /path/to/result.json
python -m kb_ledger fail  --work-key "$WK" --class transient --error "..."
python -m kb_ledger reopen --stage judge --failed-only --max-attempts 3 --reason "provider outage"
python -m kb_ledger export --csv tools/kb_coverage/tables/block_ledger.csv
python -m kb_ledger verify --strict
```

`ingest` 的幂等语义：

- 读每个 slice 的 `{chunk_rel, chunk_id, text, subject, fp}`；
- 计算 `block_key = sha256(chunk_rel \x1f canonical_locator)`；`canonical_locator` 至少包含 `page`/`ordinal`/`span`，`chunk_id` 作为兼容字段；
- 计算 `content_sha = sha256(canonical_text_bytes)`（完整文本，不是 `fp`）；
- `INSERT ... ON CONFLICT(work_key) DO NOTHING`；
- 若同一 `block_key` 已有当前行且 `content_sha` 不同：把旧行 `superseded_by` 指向新 `work_key`，再插入新行；整个过程一个事务。

### 3.2 门怎么加

#### KB 账本门

新增 `tools/ci/check_kb_ledger.py`，断言：

1. `judgment_slices/*.jsonl` 中每个 block 在账本里恰有一条“当前版本”（`superseded_by IS NULL`），且 stage 覆盖 `judge`。
2. 没有重复 `(stage, block_key, content_sha, processor_version)`。
3. 每条 `judged`：`payload_path` 存在且 sha 匹配；`result_path` 存在。
4. 每条 `failed`：`last_error` 非空，`failure_class` ∈ 四类。
5. 每条 `pending`：`attempts >= 0`；本地 strict 模式可加“pending 超过 24h 报警”。
6. 导出 CSV 的时间戳/commit 与 slices 的哈希一致（防止拿旧账本对新切片）。

接入点（按现有 CI 结构）：

- 在 `tools/ci/run_kb_checks.py` 的 `run()` 里新增 `sections["block_ledger"]`，与 `gates/consistency/roundtrip/manifest/dense` 并列；或在 `.github/workflows/android-check.yml` 的 `Knowledge pack gate and table-pack consistency` 之后加一步：

```yaml
- name: Block ledger closure
  run: python3 tools/ci/check_kb_ledger.py \
      --ledger tools/kb_coverage/tables/block_ledger.csv \
      --slices tools/kb_coverage/tables/judgment_slices
```

- `RELEASE.md` 的 dry-run gate 增加第 8 条：跑 `check_kb_ledger.py` 与 `check_native_alignment.py`，二者全绿才允许打 tag。
- `verify_judgment_fp.py` 保留：它管“判定引用是否悬空/撞键/fp 一致”，新账本门管“块是否被处理、失败是否可解释、结果是否落盘”。两者互补，不要合并。

#### 16 KB 门

新增 `tools/ci/check_native_alignment.py`（§2.6），接入点：

- 放在 `Assemble release dry run` 之后、`Release manifest gate` 之前或之后均可；必须在 APK 生成之后。
- 如果要检查 AAB，再加一步 `bundletool dump config --bundle=... | grep PAGE_ALIGNMENT_16K`；本仓当前 CI 已构建 `bundleLocalFirstRelease` / `bundleStrictOfflineRelease`，可直接接。
- 失败时输出 `owner` 列；owner 解析失败也要 fail，不允许“未知依赖但放行”。

`RELEASE.md` 的检查清单建议改为：

```text
1. debug flavors assemble + unit tests + lint
2. release assemble / bundle dry run
3. merged release manifest gate
4. Room migration matrix
5. backup round trip
6. no secrets in backup/logs
7. version bump
8. NEW: block ledger closure + native 16 KB alignment (APK + AAB)
```

### 3.3 发布前检查清单：最容易被漏掉的三项

| 排名 | 检查项 | 为什么容易漏 | 权威来源 | 本仓现状 |
|---|---|---|---|---|
| 1 | **16 KB 原生库对齐（APK + AAB）** | 常规清单只查 targetSdk/权限/混淆；`.so` 对齐要单独跑 `zipalign` + ELF 解析，且 AAB 还要 `bundletool` | <https://developer.android.com/guide/practices/page-sizes>：targetSdk 35+、64 位设备、2027-02-01 起不兼容更新无法发布 | 当前 APK 14/14 PASS；AAB 未验证 |
| 2 | **Data safety 表（含第三方 SDK 与 BYOK 出境）** | 容易只按“App 自己收集什么”填，漏掉 SDK/库；也容易把“端侧处理”误报为收集，或反过来漏报 BYOK 把题目/图片发给模型供应商 | <https://support.google.com/googleplay/android-developer/answer/10787469>：所有开发者必须填；包括第三方库/SDK；"On-device access/processing... does not need to be disclosed"；但“transmitting data from your app off a user’s device”要披露 | `PRIVACY-DATA-FLOW.md` 明确 BYOK 可能把当前题目/学生消息/授权图片发到设备外；不能简单填“不收集任何数据” |
| 3 | **隐私政策：商店页 + 应用内都要有可访问链接** | 很多团队只放商店页，忘了应用内；或用了 PDF/需登录的页面 | <https://support.google.com/googleplay/android-developer/answer/9859455>：> "You must link to a privacy policy on your store listing and within your app, even if your app doesn't access personal or sensitive user data or target children." 且要求公开可访问、有效 SSL、非 PDF、覆盖收集/使用/共享 | `PRIVACY-DATA-FLOW.md` 已存在；需确认应用内入口与公开 URL |

其余清单项（不是 top 3，但必须过）：

| 项 | 权威来源 | 本仓现状/建议 |
|---|---|---|
| target API 36 | <https://support.google.com/googleplay/android-developer/answer/11926878>：2026-08-31 起新 App 与更新必须 target API 36 | `targetSdk=36` 已合规 |
| App 大小/资产 | <https://support.google.com/googleplay/android-developer/answer/9859372>：base module 500MB、单个 asset pack 1.5GB、全部模块+install-time asset packs 4GB；>1GB 需 minSdk 21 | release APK 155MB；KB 资产增长时用 asset pack，并用 bundletool 估算 |
| 权限 | <https://support.google.com/googleplay/android-developer/answer/9888170>：只请求核心功能所需、最小范围、运行时请求、拒绝后可用替代路径 | 当前仅 `POST_NOTIFICATIONS`（Android 13+ 运行时权限）、`RECEIVE_BOOT_COMPLETED`（普通权限）；没有 Play 高危/受限权限（SMS/通话记录/位置/全文件/QUERY_ALL_PACKAGES 等）。新增精确闹钟/存储/相机权限时重评 |
| Release manifest | `tools/ci/check_release_manifest.py` | 已有门；继续跑 |
| 备份/秘密 | `RELEASE.md` gate 6 | `allowBackup=false`；仍需跑备份往返 |
| SDK Index / 第三方 SDK | Play SDK Index + Data safety | LiteRT/ML Kit/QuickJS 的收集行为需按版本核对 |

对“权限”多说一句：本仓当前没有 Play 高危/受限权限，所以它不是 top 3；但 `POST_NOTIFICATIONS` 是 Android 13+ 的运行时权限，`RECEIVE_BOOT_COMPLETED` 是普通权限、不需要运行时弹窗。若未来加入精确闹钟（`SCHEDULE_EXACT_ALARM`）、全文件访问或位置权限，才会进入 Play 受限权限/声明流程；不要因为“现在没有”就在清单里删掉这一行。

### 3.4 落地顺序（最小改动）

1. **第 1 步（账本骨架）**：加 `tools/kb_ledger/schema.sql` + `ledger.py` + `cli.py`；先只支持 `judge` stage；用 235 个 slice 做 `ingest`，不接任何模型。
2. **第 2 步（回填）**：把现有 `judgment-verdicts/*.csv` 回填为 `judged` 行；`result_path` 指向 CSV 的绝对路径 + 行号；对没有判定的块标 `pending`，对已知失败标 `failed` + `failure_class`。回填脚本要记录 `backfill_epoch`，不要伪装成真实尝试。
3. **第 3 步（接线）**：把 `_judge30_build.py` 这类一次性脚本改成 `claim → 调用 → judge/fail`；禁止直接写 verdict CSV。先在一个 slice 上试跑，确认重复执行不会新增行、不会重复调用。
4. **第 4 步（门）**：加 `check_kb_ledger.py`，先在本地 strict 跑；CI 用导出的 CSV 快照，避免依赖 `build/`。
5. **第 5 步（16 KB 门）**：加 `check_native_alignment.py`，接进 `android-check.yml`；当前 APK 应全绿，作为基线。
6. **第 6 步（发布清单）**：把 §3.3 三项写进 `RELEASE.md`，并在 Play Console 预审前逐项打勾。

---

## 4. 未验证项

| # | 项 | 状态 | 说明/下一步 |
|---|---|---|---|
| 1 | 16 KB 截止日从 2025-11-01 顺延到 2027-02-01 的官方公告原文 | 【单一来源/待验证】 | 当前指南（2026-10-16）写 2027-02-01；2025-07 官方博客写 2025-11-01；顺延公告未找到。以当前指南为准，但不要把“顺延”写成已核实的官方公告 |
| 2 | ONNX Runtime 哪个版本修复 16 KB | 【单一来源/待验证】 | 只有 GitHub issue #26228；未取得 release note/官方文档确认。使用 ORT 前必须按 §2.2 自测 |
| 3 | ML Kit 32 位 `.so` 是否有上游修复版本 | 【单一来源/待验证】 | 仓内前序实测 Maven 最新 17.0.0 仍 FAIL；未取得 ML Kit 官方 release note 原文。当前策略是裁 32 位 ABI |
| 4 | LiteRT v1.4.0 "Fixed support for 16 kb page" 的适用范围 | 【单一来源/待验证】 | 来自 GitHub release note；本仓 2.1.6 已实测 PASS，不依赖该 note 做结论 |
| 5 | “SQLite 不是风险点” | 【推断】 | 官方无原句；由“平台 ELF 16 KB 对齐 + Java/Kotlin-only 已支持 + 本仓不打包 SQLite `.so`”推出。自带 SQLite 的库仍是风险 |
| 6 | AAB / bundletool `PAGE_ALIGNMENT_16K` | UNVERIFIED | 本轮只测 APK；CI 已构建 AAB，可加一步 `bundletool dump config` |
| 7 | arm64 真机/模拟器 16 KB 运行时 | UNVERIFIED | 本机 x86_64 Windows 无 arm64 运行时路径；仓内前序只在 `ps16k_x86_64` 上做过运行时验证。arm64 目前只有静态 ELF 证据 |
| 8 | Play Console 实际 16 KB 警告/预审结果 | UNVERIFIED | 未上传 Play Console；上线前用内部测试轨验证 |
| 9 | 账本在真实并发/崩溃下的行为 | UNVERIFIED | schema/CLI 是设计建议，尚未实现；实现后需要故障注入测试（kill -9、租约过期、provider 超时后重试） |
| 10 | 供应商 idempotency key 的长度/格式限制 | 【待验证】 | 不同模型/OCR 供应商限制不同；实现时按供应商文档截断/转 UUIDv5，并在账本记录 provider_request_id |
| 11 | Temporal/LangGraph/Claim Check 文档的长期稳定性 | 时效性声明 | 本轮引用为 2026-11-02 抓取；后续版本可能改口径 |

---

## 附录 A：本轮关键 URL

- Android 16 KB 指南：<https://developer.android.com/guide/practices/page-sizes>
- Android 官方博客（2025-07，旧时间线）：<https://android-developers.googleblog.com/2025/07/transition-to-16-kb-page-sizes-android-apps-games-android-studio.html>
- AOSP 16 KB：<https://source.android.com/docs/core/architecture/16kb-page-size/16kb>
- LiteRT v1.4.0 release：<https://github.com/google-ai-edge/LiteRT/releases/tag/v1.4.0>
- ONNX Runtime issue #26228：<https://github.com/microsoft/onnxruntime/issues/26228>
- Play target API：<https://support.google.com/googleplay/android-developer/answer/11926878>
- Play Data safety：<https://support.google.com/googleplay/android-developer/answer/10787469>
- Play 隐私政策/预审：<https://support.google.com/googleplay/android-developer/answer/9859455>
- Play App 大小限制：<https://support.google.com/googleplay/android-developer/answer/9859372>
- Play 权限政策：<https://support.google.com/googleplay/android-developer/answer/9888170>
- Temporal Activity Definition：<https://docs.temporal.io/activity-definition>
- Temporal Retry Policy：<https://docs.temporal.io/encyclopedia/retry-policies>
- LangGraph Checkpointers：<https://docs.langchain.com/oss/python/langgraph/checkpointers>
- LangGraph Fault tolerance：<https://docs.langchain.com/oss/python/langgraph/fault-tolerance>
- Azure Claim Check：<https://learn.microsoft.com/en-us/azure/architecture/patterns/claim-check>
- Azure Idempotent Consumer：<https://learn.microsoft.com/en-us/azure/architecture/patterns/idempotent-consumer>

## 附录 B：本仓相关文件

- `app/build.gradle.kts`：minSdk 23 / targetSdk 36 / abiFilters arm64-v8a + x86_64
- `core/database/build.gradle.kts:40`：`androidx.sqlite:sqlite-framework`（平台 SQLite 路径）
- `tools/kb_coverage/office_extract.py:173-175`：`_fp`（CJK-only 指纹，不能当 content_sha）
- `tools/kb_coverage/verify_judgment_fp.py`：现有判定引用门
- `tools/kb_coverage/tables/judgment_slices/`：235 个切片，块字段 `chunk_rel/chunk_id/text/subject/fp`
- `tools/kb_coverage/tables/extraction_state.csv`：文档级状态，不是块级账本
- `tools/kb_coverage/tables/key_remap.csv`：chunk id 重排的真实证据
- `knowledge-production/judgment-verdicts/`：按切片的判定 CSV
- `tools/ci/run_kb_checks.py`：KB 统一检查入口
- `tools/ci/check_release_manifest.py`：现有发布 manifest 门
- `RELEASE.md`：发布 dry-run gate
- `docs/research/2026-10-25-16kb-arm64-api23-build.md`：前序 16 KB 静态实测
- `docs/research/2026-10-27-arm64-16kb-coverage.md`：ps16k/arm64 覆盖边界
