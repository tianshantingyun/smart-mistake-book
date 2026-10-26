# 设备验证证据落盘规范（R4，v1）

| 项 | 值 |
|---|---|
| 版本 | v1（2026-10-26，批次 23 起生效） |
| 适用 | 设备轨全部**结论性**证据：`am instrument` 摘要、关键 logcat 片段、16KB 页/ELF/ZIP 检查、APK/mapping 身份、备份/迁移用例输出 |
| 不适用 | JVM 单测门/强门的构建日志（可复用 §5 模板，但 KD-74 不以它们为判据） |
| 关闭对象 | KD-74（`docs/known-defects.md:2693-2701`），判定见 §6 |
| 依据 | 计划 `.zcode/plans/plan-2026-10-26-batch-23-device-gates-lockfiles.md:19`（"按 R4 规范落盘，可直接入仓的最小文本证据"） |
| 本轮性质 | 规范研究。只读核对 + `zipalign` 只读校验 + `adb logcat --help`；**未跑 Gradle、未跑设备用例、未做 git 写操作** |

---

## 0. 结论摘要（照做清单）

1. **落点**：`docs/research/evidence/<YYYY-MM-DD>-batch-<NN>/`，每批一个目录；不放在 `docs/research/` 根（他线未跟踪记录在飞），不放在 `build/`（不可审计）。
2. **扩展名只用 `.md`**。`.gitignore:31` 全仓忽略 `*.log`；`.gitignore:15` 忽略 `**/build/`；`.gitignore:23` 忽略 `*.apk`。证据文件若用 `.log` 或放在 `build/`，`git add` 根本看不到，等于没落盘。
3. **`build/device-track-batch<NN>/` 只放原始全量日志**，是本机调试材料，**不是证据**，可被 `clean` 删除。记录与 KD 只引用仓内 `.md`。
4. **上限**：单文件 ≤ 64 KiB 且 ≤ 400 行；单批目录 ≤ 512 KiB 且 ≤ 16 个 `.md`；全证据目录 ≤ 8 MiB（预算，超限先裁定，不删旧证据）。
5. **每个结论先给"命令 + 退出码 + 计数/摘要"，再给有界摘录**；零命中也要写出命令与 `0`，不能用"无异常"代替。
6. **落盘前跑脱敏扫描**（§4.4）；未扫描 = 未完成。密钥、真实学习者内容、物理设备序列号、用户主目录一律不得进仓。
7. **记录引用格式**：`E-ID` + 反引号 `` `evidence/<date>-batch-<NN>/stepNN-<slug>.md:LINE` ``。没有 E-file 的数字只能写"工单转录"或 `UNVERIFIED`。
8. **KD-74 看证据覆盖，不看红绿**：四类结论各有 E-file、T1 修复后复跑有 E-file、APK/mapping 身份齐、record/索引回链成立 → 关闭；设备用例是红是绿由对应 KD/门另行跟踪（§6.3）。

---

## 1. 问题、边界与"新增消灭的失败"

### 1.1 现状：设备结论只存在于工单

批次 22 记录把设备行写成"**工单转录，无日志**"（`docs/research/2026-10-26-batch-22-record.md:202`），并在遗留里列出 16KB、R8 冒烟、备份/迁移、T2/T1 的数字全部没有落盘（同文件 `:241`）。当前树里 `build/device-track-batch22/` 不存在，只有上一批的 `build/device-track-batch21`（`docs/research/2026-10-26-batch-23-baseline.md:285`）。KD-74 因此保持 open（同文件 `:122`）。

这不是"测试没跑"的问题，而是"跑了也无法复核"的问题：把产物存在当成设备通过，会重演 T1"未记录全红"（`docs/known-defects.md:2695` 的 Symptom）。

### 1.2 两个硬约束：`build/` 与 `.log` 都进不了仓

| 约束 | 实测 | 后果 |
|---|---|---|
| `**/build/` 被忽略 | `.gitignore:15`；`git check-ignore -v build/device-track-batch21/step2-quickjs-oom-detail.log` → 命中 `.gitignore:15` | 任何放在 `build/` 的证据都不会被提交，KD-74 无法关闭 |
| `*.log` 全仓忽略 | `.gitignore:31`；`git check-ignore -v docs/research/evidence/2026-10-26-batch-23/step1-am-instrument.log` → 命中 `.gitignore:31` | 即使用 `.log` 放进 `docs/`，也照样进不了仓 |
| `*.apk` 忽略 | `.gitignore:23` | APK 本体不能入库，只能记录 sha256/大小/版本 |

**裁定（本规范的核心解释）**：KD-74 的 Fix direction 写"落到 `build/device-track-batch22/`"（`docs/known-defects.md:2699`），但该路径被 `.gitignore:15` 忽略，按字面执行**永远无法产生仓内证据**。本规范把"落盘"解释为**仓内最小文本证据**（`docs/research/evidence/.../*.md`），`build/device-track-batch<NN>/` 只保留原始全量、供本机复查。若主会话坚持 KD 原文的 `build/` 路径，则 KD-74 按现文本不可关闭，需要先改 KD 文本；在改之前，以本规范为准。

### 1.3 证据的定义

一条设备证据 = **一条可复跑命令** + **一台已标识的设备** + **一个已标识的产物** + **有界输出** + **明确判定**。

四个属性缺一不可：

- **可复跑**：命令完整可粘贴，含 serial、component、参数；不写"执行了 16KB 用例"。
- **可追溯**：设备（serial/AVD/API/ABI/页大小）与 APK（路径/sha256/versionName）在文件头固定。
- **有界**：只摘与判定有关的行；全量留在 `build/`，并给出路径与行数。
- **脱敏**：过 §4.4 扫描；不把"删掉什么"从记录里抹掉，而是写明删了什么类别、多少处。

反例：`OK (3 tests)` 单独一行不是证据——4KB 设备上 16KB 断言会被 `assumeTrue` 跳过，`OK` 仍可能成立（见 §8.3）。必须同时有 `PAGE_SIZE=` 打印行或 `getconf` 输出。

### 1.4 每个新增机制消灭的具体失败

| 机制 | 消灭的失败（对应本仓已发生的事实） |
|---|---|
| `docs/research/evidence/` 目录 | `build/` 被忽略导致 KD-74 无仓内证据（`.gitignore:15`；基线 `:285`） |
| 每批 `00-index.md` | 多个 step 文件散落，记录员无法判断"四类结论是否齐"（KD-74 四类要求，`known-defects.md:2699`） |
| 每步一个 `stepNN-*.md` | 一个批次一个巨型文件：合批冲突、无法按步冻结、记录引用只能指到文件不能指到事实 |
| 固定头部字段 | 只有数字没有设备/APK/命令/退出码，无法复核（批次 22 的"工单转录"） |
| `verdict` 四值枚举 | 把 PARTIAL/跳过写成"绿"（KD-70 的 P2 红、16KB 的 skip 陷阱） |
| `raw-log` 字段 + `build/` 边界 | 全量日志被 `clean` 删除后无从复查；或反过来把 84 KB logcat 塞进记录 |
| 脱敏扫描（§4.4） | 密钥/主目录/学习者内容进入 git 历史后不可逆（`release-gates.md:20`：API keys never appear in logs） |
| 体量上限（§3） | 仓库膨胀、评审不可读；GitHub 在 50 MiB 警告、100 MiB 阻断（外部上限，见 §10） |
| `E-ID + path:line` 引用 | "见工单/见 build/" 式引用失效（批次 22 记录 `:202`） |
| 追加式更正 | 事后改写证据，审计链断裂（§7.6） |

---

## 2. 落盘位置、目录与命名

### 2.1 目录结构

本规范落盘时 `docs/research/evidence/` 尚不存在；首次按 §5 创建。本轮 `git check-ignore` 实测：该目录下的 `.md` 不被忽略，`.log` 命中 `.gitignore:31`（§10.3）。

```text
docs/research/
  evidence/
    2026-10-26-batch-23/
      00-index.md                  # 批次索引：映射、覆盖清单、缺口
      step00-environment.md        # 主机工具 + 设备身份 + 数据保护
      step01-visualjs-isolation-rerun.md
      step02-quickjs-oom.md
      step03-page-size-16k.md
      step04-r8-smoke.md
      step05-backup-restore.md
      step06-migration-matrix.md
```

- 目录名：`<YYYY-MM-DD>-batch-<NN>`，日期用批次开工日，`NN` 两位。
- 目录只在有证据时创建；不建空占位目录。
- 从仓库根引用：`docs/research/evidence/<dir>/<file>`。
- 从记录（位于 `docs/research/`）引用：`evidence/<dir>/<file>`。
- 从 `docs/known-defects.md` 引用：`research/evidence/<dir>/<file>`。

### 2.2 文件命名

| 文件 | 规则 |
|---|---|
| 索引 | `00-index.md`，每批恰好一个 |
| 环境 | `step00-environment.md`，每批恰好一个；设备/工具发生变化时追加一节，不覆盖 |
| 步骤 | `stepNN-<slug>.md`；`NN` 两位（01–99），`slug` 为 ASCII kebab-case、≤ 40 字符，如 `step03-page-size-16k.md` |
| 复跑 | `stepNNb-<slug>.md`、`stepNNc-...`；**不覆盖旧文件**，索引标 `supersedes` |
| 扩展名 | 只用 `.md`。理由：`.gitignore:31` 忽略 `*.log`；Markdown 支持固定章节与行号引用 |

### 2.3 不放在哪里（已比较并拒绝）

| 候选 | 拒绝理由 |
|---|---|
| `build/device-track-batch<NN>/` | 被 `.gitignore:15` 忽略；KD-74 Symptom 正是"只有产物、没有日志"（`known-defects.md:2695`） |
| `docs/research/` 根下单文件 `...-device-evidence.md` | 批次记录本身已 70 KB 量级；单文件会同时承担"索引 + 多步 + 全量摘录"，合批冲突且无法按步冻结；记录引用只能指到整文件 |
| `docs/testing/` | 该目录放规范（如 `device-state-safety.md`），不是数据 |
| Git LFS / 外部制品库 | 本仓未配置远端制品服务；引入 LFS 需要远端与 CI 改造，且离线复核能力下降。当前证据是文本，不需要二进制通道 |

### 2.4 与 `build/` 的边界（硬规则）

| `build/device-track-batch<NN>/` | `docs/research/evidence/<dir>/` |
|---|---|
| 原始全量：未过滤 logcat、完整 stdout、构建日志 | 最小文本：命令 + 计数 + 有界摘录 + 判定 |
| 不入仓、可被 `clean` 删除、不跨机器 | 入仓、只追加、跨批次可复核 |
| 只能在 `raw-log` 字段里作为"本机路径"出现 | 记录/KD 的唯一引用对象 |
| 不能作为 KD/门的关闭证据 | 关闭证据的唯一来源 |

`raw-log` 字段必须写清：本机路径、行数/字节数、生成命令。若已删除，写"已删除（可复跑）"，不得留空。

---

## 3. 体量与数量上限

### 3.1 上限（本项目自定，按可审阅性定价）

| 范围 | 硬上限 | 超限处理 |
|---|---|---|
| 单个 `.md` | **64 KiB（65536 B）且 400 行** | 拆 `stepNNb`；或只留计数与签名摘录；全量留在 `build/` |
| 单批目录 | **512 KiB 且 16 个 `.md`** | 同上；不得为省空间删除失败行（失败行优先保留） |
| 全 `docs/research/evidence/` | **8 MiB（硬预算）** | 停止新增，报主会话裁定归档方式；**不得静默删除旧证据** |
| 任何单文件 | 不得接近 GitHub 50 MiB 警告 / 100 MiB 阻断（§10） | 二进制一律不入仓；APK/mapping 只记哈希与关键行 |

这些数字是**项目阈值**，不是外部标准。外部标准的硬边界只有 GitHub 的 50/100 MiB（§10），本项目把预算压到远低于它的原因：证据的价值在可读、可 diff、可引用，不在"原样保存"。

### 3.2 测量命令（Git Bash，仓库根）

```bash
B=docs/research/evidence/2026-10-26-batch-23
find "$B" -type f -name '*.md' -printf '%s %p\n' | sort -n
find "$B" -type f -name '*.md' | wc -l
du -sk "$B"
du -sk docs/research/evidence
```

把结果写进 `00-index.md` 的"本目录体积"字段；超限时先在索引"缺口"里登记，再决定拆分。

### 3.3 什么算"入仓日志"

**入**：

- `am instrument` 的 summary 块（`OK (N tests)` 或 `FAILURES!!! / Tests run: ...`）与 `INSTRUMENTATION_CODE`；
- logcat 的**签名摘录**：每条签名 ± 3 行上下文，加上判定依赖的打印行（如 `PAGE_SIZE=16384`、`Load .../libquickjs.so ... ok`）；
- 零命中的**命令与计数**（`0`）；
- `getconf` / `zipalign` / `llvm-readelf` 输出；
- APK/mapping 的 sha256、字节数、versionName；mapping 的少量关键行；
- 备份/迁移的通过/跳过/失败计数与失败首行。

**不入**：

- 未过滤的全量 logcat、`dumpsys` 全量、`/proc` 全量、monkey 全量；
- APK/AAB/`.so`/mapping.txt/keystore；
- 截图（默认禁；确为唯一证据时见 §3.4）；
- 真实学习者题目/答案/图片、账号、物理设备序列号、用户主目录。

### 3.4 二进制例外

默认不允许。若某结论**只能**由截图证明（纯视觉回归），满足全部条件才可入仓：

1. 在 `00-index.md` 写明"为什么文本不能证明"；
2. PNG，≤ 100 KiB，去除状态栏/通知等无关个人信息；
3. 同一步骤仍要给出可复跑的 UI 操作命令或 `uiautomator dump` 文本；
4. 文件名 `stepNN-<slug>.png`，与同名 `.md` 成对。

---

## 4. 脱敏与去噪

### 4.1 必须删除或替换

| 类别 | 典型内容 | 替换为 | 依据 |
|---|---|---|---|
| 密钥/令牌 | `sk-...`、`AIza...`、`ghp_...`、`Bearer ...`、Cookie、`Authorization` | `<redacted:secret>` | OWASP：令牌/密钥/凭据不得直接记录；`release-gates.md:20` 要求 API keys 不进日志 |
| 凭据赋值 | `api_key=...`、`password=...`、`token=...` | `<redacted:credential>` | 同上 |
| 个人数据 | 邮箱、手机号、真实姓名、账号 ID | `<redacted:pii>` | OWASP：PII 应移除/掩码/哈希 |
| 学习者内容 | 真实题目原文、答案、含姓名的图片路径 | `<redacted:learner-content>` | 本仓隐私面；优先改用夹具 |
| 用户目录 | `C:\Users\<name>\...`、`/home/<name>/...`、`/Users/<name>/...` | `<home>` | 本机路径泄露用户名，且换机后不可复跑 |
| 物理设备序列号 | 真机 `adb devices` 的 serial | `<serial:sha256:前8位>` 或 `<serial>` | 序列号可关联设备；模拟器 serial（`emulator-5554`）可保留 |
| 主机名/IP/MAC | `hostname`、内网地址 | `<host>` | 与结论无关时一律删 |

**不可逆原则**：git 历史一旦提交，删除文件也不能消除秘密。发现凭据时不要先提交再修；先停，按 `<redacted>` 重写，必要时更换凭据。

### 4.2 必须保留

- 命令、参数、serial（模拟器）、component、退出码；
- 设备：AVD 名、API、ABI、`getconf PAGE_SIZE`、`getprop` 原值（含空值）；
- 产物：APK 文件名、字节数、sha256、versionName、buildType；
- 测试：包名、类名、方法名、异常类型、断言消息、堆栈（仅项目包名帧可留，系统帧可截断）；
- 时间：`-v threadtime` 的时间戳（保留因果顺序，不做脱敏）；
- 统计：总数、通过/失败/跳过、每签名命中数（含 0）。

### 4.3 去噪规则

**允许删除**：

- ANSI 颜色转义、`\r`、连续空行；
- 重复的进度行（`Downloading`、`Installing`、`Streaming`、`Waiting for device` 等），但必须先计数并写 `去掉 N 行重复进度`；
- 与判定无关的第三方健康检查/心跳。

**禁止删除**：

- `chatty` / `lost ... lines`：日志溢出证据，删了就掩盖"日志不完整"；
- `lowmemorykiller`、`ActivityManager.*ANR`、`ANR in`；
- `FATAL EXCEPTION`、`SIGSEGV`、`CANNOT LINK`、`failed to map segment`、`UnsatisfiedLinkError`；
- `INSTRUMENTATION_STATUS*`、`INSTRUMENTATION_RESULT`、`INSTRUMENTATION_CODE`；
- 失败前的最后一条 warning；
- 判定依赖的打印行（如 `PAGE_SIZE=`、`Load ... ok`）。

**截断必须显式**：`[... N 行省略：原因 ...]`，禁止静默挖掉中间段。每条摘录前写清产生它的过滤命令，使复查者能从 `build/` 全量复现同一集合。

### 4.4 落盘前扫描（Git Bash，已实测）

```bash
E=docs/research/evidence/2026-10-26-batch-23
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' "$E" || echo "0 hits: secrets"
grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken)[[:space:]]*[:=][[:space:]]*[^ <]' "$E" || echo "0 hits: credential assignments"
grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$E" || echo "0 hits: emails"
grep -RInE 'C:.Users.[^ ]+|/home/[^/ ]+|/Users/[^/ ]+' "$E" || echo "0 hits: home paths"
```

说明：

- 第 4 条在 Git Bash 下用 `.` 匹配路径分隔符，因为反斜杠经 argv 层会被折叠（本轮实测 `grep -E 'C:\\Users'` 不命中，`C:.Users.` 命中）；它也可能误报 `C:XUsersY`，按命中逐条人工确认。
- `grep` 无命中时退出码为 1，`|| echo` 把"0 hits"写进记录；有命中必须逐条处理，不能只贴输出。
- 机器扫描是网，不是证明。每个代码块仍需人工通读一次，确认没有真实学习者内容或语义上的秘密。
- 扫描命令与结果写进 step 头部 `sanitized` 字段和 step 的 `## 6. 脱敏与去噪`。

---

## 5. 模板（可直接复制）

### 5.1 `00-index.md`

````markdown
# 批次 <NN> 设备证据索引（<YYYY-MM-DD>）

- 记录：`docs/research/<YYYY-MM-DD>-batch-<NN>-record.md`
- 计划：`.zcode/plans/<plan-file>.md`
- 工作树 HEAD：`<sha>`
- 证据目录：`docs/research/evidence/<YYYY-MM-DD>-batch-<NN>/`
- 本目录体积：`<N>` 个 `.md` / `<N>` KiB（§3.2 命令输出）
- 规范版本：`docs/research/2026-10-26-device-evidence-convention.md` v1

## 1. 环境
见 `step00-environment.md`。

## 2. 产物身份

| 产物 | 本机路径 | 字节 | sha256 | versionName |
|---|---|---|---|---|
| <apk-name> | `<build 路径>` | <n> | `<64 hex>` | <x.y.z-flavor> |
| <mapping.txt> | `<build 路径>` | <n> | `<64 hex>` | — |

## 3. 步骤与结论

| E-ID | 步骤 | 门/KD | 设备 | 判定 | 证据（本批目录内相对路径:行） |
|---|---|---|---|---|---|
| E-01 | <VisualJs 隔离复跑> | 设备功能门 / KD-66 | `<serial>/<AVD>` | PASS/FAIL/PARTIAL/UNVERIFIED | `step01-<slug>.md:1` |
| E-02 | ... | ... | ... | ... | ... |

## 4. KD-74 覆盖（四类 + T1 + 产物身份）

| KD-74 要求（`docs/known-defects.md:2699`） | E-ID | 状态 | 备注 |
|---|---|---|---|
| 每条命令的 `am instrument` summary / `INSTRUMENTATION_CODE` | E-__ | 有/缺 | |
| logcat FATAL/ANR/NoSuchMethod/NoClassDef 全量 grep（含 0 命中） | E-__ | 有/缺 | |
| 16KB `getprop`/`getconf` + 产物 `readelf -l` 页对齐 | E-__ | 有/缺 | 无工具时写 UNVERIFIED |
| 备份/迁移用例输出 | E-__ | 有/缺 | 跳过必须给原因 |
| T1 修复后设备复跑 | E-__ | 有/缺 | KD-74 Reopen 明确要求 |
| APK/mapping sha256 | E-__ | 有/缺 | |

## 5. 缺口与未落盘

- <逐条：缺什么、为什么、谁补、补了会更新哪个 E-ID>

## 6. 回链检查

- [ ] record §4 每个设备数字都有 `E-ID + path:line`
- [ ] `docs/known-defects.md` 的 KD Progress 引用本索引
- [ ] 每个 E-file 都有 `sanitized` 字段且扫描为 0 命中（或已处理）
- [ ] 没有把 `build/` 路径当作关闭证据
````

### 5.2 `step00-environment.md`

````markdown
# E-00 环境与设备身份

| 字段 | 值 |
|---|---|
| evidence-id | E-00 |
| convention-version | 1 |
| batch | <YYYY-MM-DD>-batch-<NN> |
| verdict | PASS（信息性，不参与 KD-74 覆盖判定） |
| executed-at | <YYYY-MM-DDTHH:MM:SS+08:00> |
| operator | <角色/代理/工单号> |
| head | <sha> |
| sanitized | 是（规范 §4.4 扫描 0 命中） |

## 1. 主机工具

```bash
adb version
emulator -list-avds
sha256sum --version | head -1
ls -l /c/Android/Sdk/build-tools/<ver>/zipalign.exe
command -v llvm-readelf || echo "llvm-readelf: 不存在"
```

| 工具 | 路径/版本 | 备注 |
|---|---|---|
| adb | <version> | |
| zipalign | build-tools <ver> | |
| llvm-readelf | <path>/<version> 或 `不存在` | 不存在时 16KB ELF 层判 UNVERIFIED（§8.3） |
| sha256sum | <version> | |

## 2. 设备

| serial | AVD | API | ABI | `getconf PAGE_SIZE` | `ro.product.cpu.page.size` | 用途 |
|---|---|---|---|---|---|---|
| <serial> | <avd> | <n> | <abi> | <n> | `<empty>`/`<n>` | 专用测试 |

```bash
adb -s <serial> emu avd name
adb -s <serial> shell getprop ro.build.version.sdk
adb -s <serial> shell getprop ro.product.cpu.abi
adb -s <serial> shell getconf PAGE_SIZE
adb -s <serial> shell getprop ro.product.cpu.page.size
adb -s <serial> shell getprop ro.product.page_size
adb -s <serial> shell getprop ro.boot.hardware.cpu.pagesize
```

`getprop` 为空时原样写 `<empty>`，不要省略该行；页大小以 `getconf PAGE_SIZE` 为准（§8.3）。

## 3. 数据保护

- 设备用途：专用测试模拟器 / 共享设备（按 `docs/testing/device-state-safety.md:7-10`）。
- 是否使用真实学习者数据：否 / 是（说明备份与恢复步骤）。
- 测试后检查：包仍安装、数据目录仍在（同文件 `:14-16`）。

## 4. 数据保护结论

<一句话：本次是否触及真实数据；若触及，恢复是否核对>
````

### 5.3 `stepNN-<slug>.md`

````markdown
# E-<NN> <步骤名>

| 字段 | 值 |
|---|---|
| evidence-id | E-<NN> |
| convention-version | 1 |
| batch | <YYYY-MM-DD>-batch-<NN> |
| step | <T1 / 步一> |
| gate-or-kd | <设备功能门 / KD-66> |
| verdict | PASS \| FAIL \| PARTIAL \| UNVERIFIED |
| executed-at | <YYYY-MM-DDTHH:MM:SS+08:00> |
| operator | <角色/代理/工单号> |
| head | <sha> |
| worktree | <clean / N M（仅记录）> |
| device-serial | <serial>（模拟器可保留；真机写 `<serial:sha256:前8位>`） |
| device-avd | <avd> |
| device-api | <n> |
| device-abi | <abi> |
| device-page-size | <n> |
| apk | <本机路径>（用户目录替换为 `<home>`/`<repo>`） |
| apk-sha256 | <64 hex> |
| apk-version | <versionName> |
| exit-code | <n> |
| raw-log | `build/device-track-batch<NN>/<file>`（本机，不入仓；<n> 行） |
| record-ref | `docs/research/<YYYY-MM-DD>-batch-<NN>-record.md` §4 |
| sanitized | 是（本文件 §6；扫描 0 命中 / 命中 <n> 已处理） |

## 1. 目的与判据

<这条证据要证明或否证什么；对应哪条可判定断言。禁止写"验证一下"。>

## 2. 命令（可复跑）

```bash
<完整命令，含 serial / component / 参数>
```

- 工作目录：`<repo root 或模块>`
- 退出码：`<n>`
- 耗时：`<n s>`（可选）

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| <tests run> | <n> | 期望 <n> |
| <failures> | <n> | 期望 0 |
| <skipped> | <n> | 必须给原因 |
| <signature hits> | <n> | 期望 0 |

## 4. 关键输出（有界）

```text
<与判定直接相关的行；省略处写 [... N 行省略：原因 ...]>
```

## 5. 失败 / 跳过 / 异常

<无则写"无"。跳过必须给 assume/skip 原因；`OK (N tests)` 不得掩盖跳过。崩溃用例写 `shortMsg=Process crashed.` 及是否为设计行为。>

## 6. 脱敏与去噪

- 删除：<重复进度 N 行 / ANSI / 空行；其他>
- 保留：<chatty / 失败行 / PAGE_SIZE 行；其他>
- 扫描：`<§4.4 命令>` → <0 hits / 逐条处理结果>
- 二进制/截图：无（或：1 张，<KiB，理由）

## 7. 判定

<verdict + 一句理由 + 未覆盖项。PASS 必须说明断言确实执行；UNVERIFIED 必须说明缺什么。>

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch<NN>/<file>`
- 生成命令：`<...>`
- 行数/大小：<n> 行 / <n> KiB
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
````

### 5.4 填写规则

- 模板里的 `<...>` 必须全部替换；文件里残留 `<...>` 视为未完成。
- `verdict` 只能用四个值之一：`PASS`（断言在指定设备/产物上成立）、`FAIL`（不成立，附失败行）、`PARTIAL`（部分子例通过/跳过，列计数）、`UNVERIFIED`（未执行或工具缺失）。
- 一个 step 只回答一组可判定断言；不同结论拆文件，避免一个 `FAIL` 淹没其余。
- `record-ref` 只写记录文件名，不写行号：记录会继续编辑，行号会漂；映射由 `00-index.md` 维护。

---

## 6. KD-74 关闭判据

### 6.1 原文与可审计解释

KD-74 的 Symptom：批次 22 的 16KB 3/3、R8 三变体冒烟、备份/迁移结论无日志（`docs/known-defects.md:2695`）。

Fix direction：每条命令的 `am instrument` summary/`INSTRUMENTATION_CODE`、logcat 的 FATAL/ANR/NoSuchMethod/NoClassDef 全量 grep、16KB 的 `getprop ro.product.cpu.page.size` 与产物 `readelf -l` 页对齐、备份/迁移用例输出；另记 APK/mapping sha256（`:2699`）。

Reopen condition：上述四类结论各有可复跑命令与落盘结果，且 T1 修复后的设备复跑结果一并落盘（`:2701`）。

**解释**：`build/` 不是"落盘"的有效位置（§1.2）。本规范要求以上内容全部出现在 `docs/research/evidence/<dir>/` 的 `.md` 中，`build/` 只保留原始全量。

### 6.2 关闭检查表（全部满足才可关闭）

| # | 条件 | 判定方法 |
|---|---|---|
| C1 | 四类结论各有 E-file：① `am instrument` summary + `INSTRUMENTATION_CODE`；② logcat 四类签名（本规范 §8.2 扩展为 12 项）全量 grep（含 0 命中）；③ 16KB `getprop`/`getconf` + 产物 `readelf -l`（LOAD/RELRO 算术）；④ 备份/迁移输出（含跳过原因） | `00-index.md` §4 每行都有 E-ID 且指向存在的文件 |
| C2 | T1（VisualJs 隔离）修复后的设备复跑有 E-file | 独立 step 文件；命令/设备/APK 哈希齐全 |
| C3 | 每个被声称的设备运行都有 APK 身份：路径、字节数、sha256、versionName；涉及 R8 结论时另有 mapping sha256/字节数（不提交 mapping 本体） | 索引 §2 + step 头部 |
| C4 | record 的每个设备数字都有 `E-ID + path:line`；没有把 `build/` 当关闭证据；没有"工单转录"行被用来关闭 KD-74 | §7 的检查命令 + 人工通读 |
| C5 | 每个 E-file 有 `sanitized` 字段，扫描 0 命中或命中已逐条处理；文件均为 `.md` 且在 `docs/research/evidence/` 下（`git check-ignore` 不命中） | `git check-ignore -v <file>` 无输出；`git status` 能看到 |
| C6 | 记录写明 "KD-74 closed" 并链接 `00-index.md`；`known-defects.md` 的 KD-74 Progress 同步引用索引与 E-ID | 两处文本 + 链接存在 |

### 6.3 红绿中性（关键裁定）

- **KD-74 关闭 ≠ 设备门全绿**。KD-74 只追究"结论是否有可复核证据"。即使 T1 复跑仍 4/4 红、T2 仍 P2 红，只要证据完整、失败原因落盘，KD-74 照样关闭；对应设备门和 KD-66/70/69 继续 open。
- 反过来，任何"绿"的结论缺 E-file，KD-74 保持 open，且该结论只能写 `UNVERIFIED` 或"工单转录"。
- 这是为了消灭批次 22 的失败模式：把"产物存在/工单说绿"当成设备通过（`known-defects.md:2695`）。

### 6.4 不可关闭的典型情形

| 情形 | 处理 |
|---|---|
| 没有 16KB 设备，只有 4KB 设备跑过装载用例 | 16KB 行写 `UNVERIFIED`（4KB 上 16KB 断言被 skip，见 §8.3）；KD-74 保持 open |
| 无 `llvm-readelf`/NDK，无法做 ELF LOAD/RELRO | 该子项写 `UNVERIFIED` + 工具缺失命令输出；KD-74 保持 open，直到补工具或主会话裁定等价判据 |
| 只有 `build/` 下的全量日志 | 不算落盘；先按本规范摘录入仓 |
| 只有 `OK (3 tests)`，没有 `PAGE_SIZE=`/`getconf` | 不算 16KB 证据；补打印行或设备命令输出 |
| 只有 APK/mapping 产物 | KD-74 Symptom 已明确"在盘不等于设备已跑"（`:2695`）；不算 |
| `.log` 文件放进 `docs/research/` | 被 `.gitignore:31` 忽略，未入仓；改 `.md` |

### 6.5 关闭动作

1. 记录员逐项勾 C1–C6，在记录 §4 写 "KD-74 closed（索引 + E-ID 列表）"。
2. 主会话在 `docs/known-defects.md` 的 KD-74 Progress 写：`2026-10-26 批次 23 关闭：四类结论 + T1 复跑 + 产物身份见 research/evidence/<dir>/00-index.md（E-01…E-0N）`。
3. 若 C1–C6 任一不满足，KD-74 保持 open，并在记录"缺口"逐项列出缺什么、谁补。

---

## 7. record 与本规范的引用关系

### 7.1 职责分工

| 文件 | 负责 | 不负责 |
|---|---|---|
| `stepNN-*.md` | 原始事实：命令、输出、计数、判定 | 跨步骤叙事、产品结论 |
| `00-index.md` | 映射：E-ID ↔ 步骤 ↔ 门/KD ↔ 设备 ↔ 产物 ↔ 缺口 | 复述全部输出 |
| `docs/research/<date>-batch-<NN>-record.md` | 叙事、复核、裁定、门禁汇总 | 保存原始日志 |
| `docs/known-defects.md` | KD 状态、Progress、关闭依据 | 保存步骤细节 |

### 7.2 引用语法

**唯一规范引用形式**：`E-ID` + 反引号路径:行。

| 引用位置 | 写法 |
|---|---|
| 批次记录（`docs/research/`） | `E-04` `` `evidence/2026-10-26-batch-23/step04-16k.md:42` `` |
| `docs/known-defects.md`（`docs/`） | `E-04` `` `research/evidence/2026-10-26-batch-23/step04-16k.md:42` `` |
| 计划/PR/提交说明（仓库根） | `E-04` `` `docs/research/evidence/2026-10-26-batch-23/step04-16k.md:42` `` |

补充规则：

- 行号指向**包含该数字/该行输出的物理行**，不是标题行；若结论跨多行，写起始行并说明"至 `:NN`"。
- 需要可点击时，链接只指到文件（`[E-04](evidence/.../step04-16k.md)`），**不要把 `:42` 塞进 URL**（Markdown 会把整串当文件名）。
- 引用目录不算引用；必须指到文件与行。
- `E-ID` 在 `00-index.md` 分配，全批唯一；不要在记录里临时编号。

### 7.3 记录表的最小形态

记录 §4 的设备行建议固定成（以下两行为**格式示例**，数值是语义占位，不是本批实测）：

```markdown
| 设备 16KB | PASS（E-04） | `getconf PAGE_SIZE`=16384；zipalign exit 0；ELF 见 `evidence/2026-10-26-batch-23/step04-16k.md:42` |
```

失败行：

```markdown
| T2 QuickJS | FAIL（E-02） | P2 期望 `MEMORY_LIMIT`，实际 `INVALID_PROGRAM`；首行见 `evidence/2026-10-26-batch-23/step02-quickjs-oom.md:37` |
```

### 7.4 记录员操作顺序

1. 收齐 runner 的 `stepNN-*.md`，逐份检查：头部字段齐全、命令可复跑、`raw-log` 有行数、`sanitized` 有扫描结果。
2. 写 `00-index.md`：产物表、步骤表、KD-74 覆盖表、缺口。
3. 写记录 §4：每个设备数字给 `E-ID + path:line`；没有 E-file 的写"工单转录（无 E-file）"，不得写"绿"。
4. 更新 KD Progress（仅当 C1–C6 全满足才写 closed）。
5. 跑 §7.5 的链接检查，把结果写进记录"验证"一节。
6. 提交时按路径白名单显式加入 `docs/research/evidence/<dir>/`（不按模块 `git add`）。

### 7.5 链接检查（Git Bash，记录写完后跑）

```bash
cd docs/research
REC=2026-10-26-batch-23-record.md
grep -oE 'evidence/[A-Za-z0-9._/-]+\.md' "$REC" | tr -d '\r' | sort -u | while read -r f; do
  test -f "$f" || echo "MISSING $f"
done
grep -oE 'evidence/[A-Za-z0-9._/-]+\.md:[0-9]+' "$REC" | tr -d '\r' | sort -u | while IFS=: read -r f l; do
  test -f "$f" || { echo "MISSING $f"; continue; }
  test "$(wc -l < "$f")" -ge "$l" || echo "LINE OUT OF RANGE $f:$l"
done
```

第一条查文件存在；第二条查行号在范围内。两条都必须无输出。本机 `grep` 是 ugrep，`-o` 输出带 CRLF；`tr -d '\r'` 不能省（本轮实测：不加时已存在的文件被误报 `MISSING`）。KD 中的 `research/evidence/...` 检查：在 `docs/` 下把模式换成 `research/evidence/[A-Za-z0-9._/-]+\.md(:[0-9]+)?`，其余循环不变。

### 7.6 更正与复跑

- 证据 step 文件**只追加**，不改历史行。发现错误时在文件末尾加 `## 9. 更正（YYYY-MM-DD）`，写"原判定 X 错在 Y，现更正为 Z"，保留原行。`00-index.md` 是映射文件，可更新（标明 `supersedes`），但不得改动 step 文件的既有行。
- 设备复跑生成新文件 `stepNNb-*.md`；`00-index.md` 在旧行标 `superseded-by: stepNNb`。不得覆盖旧文件。
- 记录更正走记录自己的修订节，并更新引用到新文件；旧引用不删，标"已被 E-NNb 取代"。

### 7.7 反模式

| 反模式 | 为什么错 | 正确做法 |
|---|---|---|
| `见 build/device-track-batch22/` | 该路径被忽略，且可被 clean 删除 | 摘录入仓 + 在 `raw-log` 保留本机路径 |
| "工单材料：16KB 3/3 绿" | 无设备/命令/输出，无法复核（批次 22 记录 `:202`） | 写 `UNVERIFIED`，补 E-file 后才改判 |
| 把 84 KB logcat 贴进记录 | 记录不可读、diff 噪声、超体量 | 只留签名计数 + 有界摘录，全量留 `build/` |
| 只写 `OK (3 tests)` | 跳过/assume 被掩盖；4KB 设备也会 OK | 加 `PAGE_SIZE=`、`getconf`、跳过原因 |
| 改旧 step 的数字 | 审计链断裂 | 新建 `stepNNb` + 更正节 |
| `[E-04](evidence/...md:42)` | Markdown 把 `:42` 当文件名，链接失效 | 链接指文件，行号写反引号文本 |
| 从 `docs/known-defects.md` 用 `evidence/...` | 相对路径错，链接断 | 用 `research/evidence/...` |

---

## 8. 采集命令（可复跑）

> 下列命令以 Git Bash + 本机 SDK 路径为准；`<...>` 必须替换。设备结论一律附带设备身份（`step00-environment.md`）与 APK sha256。

### 8.1 `am instrument`

```bash
SERIAL=<serial>
COMPONENT=<test-package>/androidx.test.runner.AndroidJUnitRunner
adb -s "$SERIAL" shell am instrument -w -r \
  -e class <fully.qualified.TestClass#method> \
  "$COMPONENT"
```

- `-w` 等待结束后返回（AOSP `Am.java:163-164`）；`-r` 输出原始结果、`-e name value` 传参（同文件 `:165-176`；官方 adb 文档 `am instrument` 节同列 `-r`/`-e`/`-p`）。
- 本仓 app 模块的仪器化包名示例（批次 19 实测命令）：`com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner`（`docs/research/2026-10-23-batch-19-record.md:259`）。各模块测试包名 = 模块 namespace + `.test`；以 `adb shell pm list instrumentation` 为准。
- 不使用 `connectedAndroidTest`（本仓设备轨约定：裸 `am instrument`，见批次 15/19 记录）。
- 本仓 app 模块的 runner 为 `androidx.test.runner.AndroidJUnitRunner`（`app/build.gradle.kts:29`）；下表 `INSTRUMENTATION_CODE` 语义以该 runner 为准。

**必须记录的结果字段**：

| 字段 | 来源 | 说明 |
|---|---|---|
| `OK (N tests)` 或 `FAILURES!!!` + `Tests run: N, Failures: M, Errors: E` | JUnit 文本输出（`junit.textui.ResultPrinter:78-94`，junit 4.13.2） | 用例判定的主依据 |
| `INSTRUMENTATION_STATUS_CODE:` | AOSP `Instrument.java:184` | 逐用例状态；配合 class/method 行 |
| `INSTRUMENTATION_RESULT: shortMsg=...` | AOSP `Instrument.java:202` | 进程崩溃/中止 |
| `INSTRUMENTATION_CODE:` | AOSP `Instrument.java:205` | **不是 JUnit 通过/失败信号**：AndroidJUnitRunner 固定 `finish(Activity.RESULT_OK, results)`（`AndroidJUnitRunner.java:450`；`RESULT_OK=-1`，AOSP `Activity.java:779`），失败在结果流里 |
| adb 进程退出码 | shell | 一并记录，但**不得**单独充当用例判定 |

批次 22 的崩溃用例就是靠 `INSTRUMENTATION_RESULT: shortMsg=Process crashed.` 留痕（`docs/research/2026-10-26-batch-22-record.md:186`），模板按此保留。

### 8.2 logcat 采集与签名 grep

```bash
SERIAL=<serial>
OUT=build/device-track-batch23/step02-quickjs-oom-logcat.txt
adb -s "$SERIAL" logcat -c          # 仅专用测试设备；共享设备先按 device-state-safety 备份
<运行 am instrument>
adb -s "$SERIAL" logcat -v threadtime -d > "$OUT"
```

选项依据：`-c`、`-d`、`-v threadtime`、`-f`、`--pid=PID` 由本机 `adb logcat --help` 实测列出（本轮执行）；官方 logcat 文档列出 `-v`、`-d` 与过滤表达式（来源见 §10）。`--pid` 未在官方页找到，按"设备 logcat 版本支持则用，不支持则退回 tag/包名过滤"处理，并把这句写进 step。

```bash
LOG=build/device-track-batch23/step02-quickjs-oom-logcat.txt
wc -l < "$LOG"
while IFS= read -r s; do
  printf '%-28s %s\n' "$s" "$(grep -cF -- "$s" "$LOG" 2>/dev/null | tr -d '\r')"
done <<'EOF'
FATAL EXCEPTION
ANR in 
NoSuchMethodError
NoSuchMethodException
NoClassDefFoundError
ClassNotFoundException
UnsatisfiedLinkError
CANNOT LINK
failed to map segment
SIGSEGV
StartupException
MlKitException
EOF
```

- 每个签名一行，**0 也打印**；把这张表原样放进 step §3。
- 有命中时：`grep -nF -B3 -A3 -- "<签名>" "$LOG"` 取上下文，写进 §4；无命中时写命令与 0。
- 必须保留的额外行：测试自己打印的判定行（如 `PAGE_SIZE=16384`）、`Load .../libquickjs.so ... ok`、`chatty`/`lost ... lines`。
- 日志噪声大时用包名/tag 过滤（官方建议用过滤表达式降低输出量），但过滤命令必须写进 step，不能让复查者猜。

### 8.3 16KB 页与 ELF/ZIP 对齐

**设备页大小（官方口径）**：

```bash
adb -s <serial> shell getconf PAGE_SIZE     # 16KB 设备应返回 16384
```

**ZIP 层（官方口径）**：

```bash
/c/Android/Sdk/build-tools/<ver>/zipalign.exe -c -P 16 -v 4 <apk>
```

本机实测（批次 23 研究时点，只读校验既有 APK）：末行 `Verification successful`，exit 0。记录原样末行与 exit code。

**ELF 层（官方口径）**：

```bash
<llvm-readelf> -Wl <lib.so> | grep 'LOAD\|RELRO\|Type'
```

官方判定：把 `GNU_RELRO` 的 `VirtAddr + MemSiz` 对 16 KB（`0x4000`）取模，余数为 0 才对齐；同时检查每个 `LOAD` 段是否落在 16 KB 边界（官方页 `developer.android.google.cn/guide/practices/page-sizes`；来源见 §10）。**zipalign 通过 ≠ ELF 通过**：RELRO 结束未对齐仍会在 16KB 设备崩溃（项目内结论见 `docs/research/2026-10-18-flexible-js-drawing-research.md:70`）。

本机工具现状（本轮实测）：PATH 无 `llvm-readelf`/`readelf`，无 `C:/Android/Sdk/ndk`。因此 ELF 子项在补齐 NDK 前只能写 `UNVERIFIED`，并附 `command -v llvm-readelf || echo 不存在` 的输出；不得用 zipalign 或"设备能装"代替。

**页大小属性与 skip 陷阱**：

- `ro.product.cpu.page.size`、`ro.product.page_size`、`ro.boot.hardware.cpu.pagesize` 在在线模拟器上实测为空（`docs/research/2026-10-26-batch-23-baseline.md:266`）。空值原样记录，**不判失败**；以 `getconf PAGE_SIZE` 为准（官方命令）。
- `NativeLibraryPageSizeInstrumentedTest.runtimePageSizeIs16KB` 在 4KB 设备上 `assumeTrue` 跳过（`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/NativeLibraryPageSizeInstrumentedTest.kt:50-54`），但同类的装载用例仍会通过。因此 `OK (N tests)` **不能**单独作为 16KB 证据；必须有 `println("PAGE_SIZE=$pageSize")`（同文件 `:52`）的输出行或 `getconf` 输出，并在 step §5 写清 skipped 数与原因。

### 8.4 APK / mapping 身份

```bash
APK=<apk 路径>
MAP=<mapping 路径>
sha256sum "$APK"; stat -c '%s %n' "$APK"
sha256sum "$MAP"; stat -c '%s %n' "$MAP"
```

- APK：记录文件名、字节数、sha256、versionName/buildType；用于证明"设备上跑的是这一份"。路径中的用户目录替换为 `<home>`/`<repo>`。
- mapping：**不提交本体**（通常数十 MB，且 `*.apk`/`build/` 规则之外也可能被 `git add -f` 误加）。只记录字节数、sha256，以及结论依赖的少量行（如 `WorkDatabase_Impl.<init>` 是否被裁），行号与命令一并写。
- 取关键行（≤ 20 行，超出只贴首尾并说明）：

```bash
grep -n 'WorkDatabase_Impl' "$MAP" | head -20
grep -n 'WorkDatabase_Impl' <usage.txt 路径> | head -20
```

- 若只声称"包能装"，不需要 mapping；若声称 R8 keep 生效，必须有 mapping/usage 关键行。

### 8.5 备份 / 迁移

```bash
adb -s <serial> shell am instrument -w -r \
  -e class <suite 或 #method> \
  -e <destructive-opt-in> true \
  <test-package>/androidx.test.runner.AndroidJUnitRunner
```

- 破坏性用例的 opt-in 参数必须原样出现在命令里（批次 22 的 `-e smb_allow_data_reset true`、`-e smb_d1_kill_stage true`，`docs/research/2026-10-26-batch-22-record.md:186`）；否则无法证明"这是设计行为还是误伤数据"。
- 记录 `OK (N tests)` 之外，必须记 skipped/assumption 数与原因；批次 22 的迁移用例就是 `OK (6 tests)` 内含 2 个跳过（同文件 `:186`）。
- 真实数据设备先按 `docs/testing/device-state-safety.md:7-10` 备份；step 的 §3 或 §5 写"真实数据/夹具"。

---

## 9. 维护、冲突与牵动面

### 9.1 与既有规范的关系

- `docs/testing/device-state-safety.md` 继续负责**设备数据保护**（备份、真实数据、测试后检查）；本规范只负责**证据格式与落点**。两者同时适用。
- `docs/current/release-gates.md:20`（API keys never appear in logs）在本规范里落成 §4.1 的强制脱敏与 §4.4 的扫描。
- 批次记录模板/KD Progress 的写法从批次 23 起按本规范执行；**不回溯改旧记录**（旧记录保持原样，其"工单转录"标记就是历史事实）。

### 9.2 禁止事项

- **不改 `.gitignore`**：它是他线热区；且放开 `*.log` 会让全仓日志涌入。证据用 `.md` 即可，不需要改忽略规则。
- **不把证据放在 `docs/research/` 根**：该区域有他线未跟踪记录，混放会造成归属冲突；统一进 `evidence/<batch>/`。
- **不把 `build/` 路径写进 KD 关闭依据**：只能写进 `raw-log` 字段。
- **不提交 mapping/APK/截图**（截图按 §3.4 例外）。
- **不删旧证据**：超预算时停下裁定，不静默清理。

### 9.3 牵动面清单（改本规范/加证据时逐项过）

| 面 | 影响 | 本规范的处理 |
|---|---|---|
| `.gitignore` | `**/build/`、`*.log`、`*.apk` | 不改；用 `.md` + sha256 绕开 |
| `build/device-track-*` | 原始全量 | 只作本机材料，不入仓 |
| 批次记录 §4 | 引用格式 | 固定 `E-ID + path:line`（§7） |
| `known-defects.md` | KD Progress/关闭文本 | 只引用索引与 E-ID；相对路径用 `research/evidence/...` |
| 提交路径白名单 | 新增 `docs/research/evidence/<dir>/` | 显式加入，不按模块 `git add` |
| 评审流程 | 体量与脱敏 | §3/§4 的硬上限与扫描 |
| Gradle/CI | 无 | 本规范不引入任何构建步骤或脚本；不跑 Gradle |
| 他线热区 | feature/core/ui/Root 等 | 本规范不触碰；证据目录独立 |

### 9.4 版本与归属

- 版本：v1（2026-10-26）。未来修订在文件头加 `v2（YYYY-MM-DD）` 与"变更点"；旧证据按其 `convention-version` 仍然有效，不强制回填。
- 归属：runner 写 `stepNN-*.md`；记录员写 `00-index.md` 与记录 §4；主会话更新 KD。任何一方不得代写对方的判定。

---

## 10. 来源与验证状态

### 10.1 外部来源

> Google 直连本轮超时；下表 Android 文档使用 `developer.android.google.cn` 官方中文镜像（与 `developer.android.com` 同一内容的镜像站），AOSP/JUnit/AndroidX 用 GitHub 源码镜像。

| 来源 | 用途 | 状态 |
|---|---|---|
| <https://developer.android.google.cn/guide/practices/page-sizes?hl=zh-cn>（`developer.android.com` 官方中文镜像；直连 `developer.android.com` 本轮超时） | `getconf PAGE_SIZE`→16384；`zipalign -c -P 16 -v 4`；`llvm-readelf -Wl` + RELRO `VirtAddr+MemSiz` 对 0x4000 取模 | 已取原文并摘录（§8.3） |
| <https://developer.android.google.cn/tools/adb?hl=zh-cn> | `am instrument [options] component`、`-r`、`-e name value`、`-p file` | 已取原文 |
| <https://developer.android.google.cn/tools/logcat?hl=zh-cn> | `-v <format>`、`-d`、过滤表达式降低输出量 | 已取原文；该页未列 `--pid` |
| 本机 `adb logcat --help`（emulator-5554，本轮执行） | `--pid=PID`、`-c`、`-d`、`-v`、`-f` | 已实测（设备工具行为，非文档） |
| <https://github.com/aosp-mirror/platform_frameworks_base/blob/master/cmds/am/src/com/android/commands/am/Instrument.java> | `INSTRUMENTATION_STATUS[_CODE]`、`INSTRUMENTATION_RESULT`、`INSTRUMENTATION_CODE` 输出 | 已取源码（`:184`/`:202`/`:205`） |
| <https://github.com/aosp-mirror/platform_frameworks_base/blob/master/cmds/am/src/com/android/commands/am/Am.java> | `-w`/`-r`/`-e` 解析 | 已取源码（`:163-176`） |
| <https://github.com/android/android-test/blob/main/runner/android_junit_runner/java/androidx/test/runner/AndroidJUnitRunner.java> | runner 固定 `finish(Activity.RESULT_OK, results)` | 已取源码（main `:450`；2025-07-31 stable 快照 `:476`；本仓钉 runner 1.7.0，见 §10.2） |
| <https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/app/Activity.java> | `RESULT_OK=-1`、`RESULT_CANCELED=0` | 已取源码（`:777-779`） |
| <https://github.com/junit-team/junit4/blob/r4.13.2/src/main/java/junit/textui/ResultPrinter.java> | `OK (N tests)` / `FAILURES!!! / Tests run: ...` 文本 | 已取源码（`:78-94`；本仓钉 4.13.2，`gradle/libs.versions.toml:24`） |
| <https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github> | 50 MiB 警告、100 MiB 阻断、25 MiB 网页上传、仓库 <1 GB 建议 | 已取原文 |
| <https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html> | 令牌/PII/凭据不得直接记录；移除/掩码/清洗/哈希；保留期与访问控制 | 已取原文 |

### 10.2 单一来源 / 待验证

- **`--pid`**：只有本机设备工具实测，官方 logcat 页未列。step 中必须写"设备支持则用"，不得写成通用保证。
- **跳过用例的解析**：本轮未实跑一个含 `assumeTrue` 的套件来验证 runner 的 skipped 文本格式；模板要求"记录 skipped 数与原因"，具体行以 runner 实际输出为准，**待设备轨首次执行时验证**。
- **adb 进程退出码**：本轮未用失败用例实测 `adb shell am instrument` 的退出码。规范因此**不采用**退出码作为判定，只记录；判定以 summary/逐用例状态为准（有 AndroidX 源码依据）。
- **脱敏正则**：本轮用假样本实测能命中 `sk-`/`Bearer`/`api_key=`/邮箱/Windows 主目录；未对真实证据文件跑过（本批证据尚未生成）。首次使用时按命中逐条复核。
- **`AndroidJUnitRunner` 版本**：本仓钉 `androidx.test:runner:1.7.0`（`gradle/libs.versions.toml:26`）。本轮核对上游 main `:450` 与 2025-07-31 stable 快照 `:476` 均为 `finish(Activity.RESULT_OK, results)`；但未逐字节核对 1.7.0 发布产物，`INSTRUMENTATION_CODE` 的"非判据"结论以这两份源码为据，1.7.0 发布产物的精确行号待需要时按该版本源码确认。
- **`getprop` 页大小属性**：官方 16KB 页要求的是 `getconf PAGE_SIZE`；三个 `ro.*page.size*` 属性在在线模拟器上为空（基线 `:266`），本规范不把它们作为主判据。

### 10.3 本轮实际执行的命令（可复跑）

```bash
git check-ignore -v build/device-track-batch21/step2-quickjs-oom-detail.log   # .gitignore:15
git check-ignore -v docs/research/evidence/2026-10-26-batch-23/x.log          # .gitignore:31
git check-ignore -v docs/research/evidence/2026-10-26-batch-23/x.md           # 无输出（可跟踪）
/c/Android/Sdk/build-tools/37.0.0/zipalign.exe -c -P 16 -v 4 \
  app/build/outputs/apk/strictOffline/internal/app-strictOffline-internal.apk # 末行 Verification successful，exit 0
"/c/Android/Sdk/platform-tools/adb.exe" -s emulator-5554 logcat --help        # 列出 --pid/-d/-v/-f/-c
command -v llvm-readelf; ls -d /c/Android/Sdk/ndk                              # 均不存在（ELF 工具缺口）
grep --version | head -1                                                       # ugrep 7.8.4（-o 输出 CRLF）
tr --version | head -1                                                         # GNU coreutils 8.32
# 脱敏正则假样本自测：build/r4tmp/ev/sample.md（临时，已删除）
# 链接检查假样本自测：加 tr -d '\r' 后已存在文件不再误报；缺文件与越界行各自命中（临时目录已删除）
```

**本轮未做**：未跑 Gradle、未编译、未执行任何设备用例、未生成任何 `docs/research/evidence/` 实例、未做 git 写操作。本文件是唯一写盘产物。

---

## 附：一页版（runner 贴在工位旁）

1. 建 `docs/research/evidence/<date>-batch-<NN>/`，复制 §5 模板，填满头部。
2. 先跑 `step00-environment.md`，把设备/工具/页大小固定下来。
3. 每条命令：完整命令 + 退出码 + summary + `INSTRUMENTATION_CODE` + 签名计数（含 0）。
4. logcat 只摘签名 ± 3 行与判定打印行；全量留在 `build/device-track-batch<NN>/`，写清路径/行数。
5. 16KB：`getconf` + `zipalign` + `llvm-readelf`；缺工具写 `UNVERIFIED`，不要用 `OK` 代替。
6. APK/mapping 记 sha256/字节数/versionName；mapping 本体不提交。
7. 跑 §4.4 扫描，把"0 hits"写进头部；有命中逐条处理。
8. 交给记录员：`00-index.md` + `E-ID`；记录只写 `path:line`，不写 `build/`。
