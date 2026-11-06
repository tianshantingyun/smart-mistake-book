<!-- 体积自查：127 行 / 约 9 KiB；R4 上限 400 行 / 65536 B。目录预算：16 个 .md（上限 16，已满）、217 KiB（上限 512 KiB） -->
# 批次 41 证据索引（2026-11-03）

- 计划：`.zcode/plans/plan-2026-11-03-batch-41.md`
- 输入：批次 40 记录 `docs/research/2026-11-02-batch-40-record.md` §8/§9
- 本文件范围：**设备轨 E-41-D01…D06 + 流程轨 E-41-P11…P13**
- 规范：`docs/research/2026-10-26-device-evidence-convention.md` v1（字段口径同批次 40）
- 执行设备（设备轨）：emulator-5584（AVD `kb_test_device`，API 34，x86_64，4 vCPU，page-size 4096）
- 脱敏：仅模拟器 serial；无密钥/凭据/物理序列号

## 0. 编号映射（先读这一节）

本 ask 要求把 **P11–P13** 的日志/命令/退出码/APK sha/源快照整理为 E 文件。计划 §3 的流程清单是"12 项命名 + 8 项 WS 子件"，**没有逐项编号**，因此本目录的映射如下，且不掩盖歧义：

| 本目录 E-ID | 本 ask 编号 | 对应计划条目 | 实际承载 | 计划 §3 若按"命名 12 项"顺序数 |
|---|---|---|---|---|
| E-41-P11 | P11 | 「环后强门 1」（强门在 WS1 修复环之后复跑） | 强门 5 次尝试 + exit=0 | **同号**（第 11 项就是环后强门） |
| E-41-P12 | P12 | 「单测门 1」（计划第 4 项） | 单测门两轮 6 红 + 根因 | 第 12 项在计划里是**「记录」**——若按此读，本 E 文件应换成记录；记录本身由 `docs/research/2026-11-03-batch-41-record.md` 承担（**尚未创建**） |
| E-41-P13 | P13 | 「各 WS 内独立判据/证据子件」第 1 项 = WS1 脚本 | 四方一致性门 + PDF 独立读者 | 第 13 项 = 8 个子件的第 1 项，即 WS1 脚本，**与本次一致** |

- 选型理由：这三点落在「环后强门 / 唯一另一条有日志的门（单测门）/ WS 子件首项」，仓内都有**可执行物 + 退出码 + 落盘输出**（`build/batch41-strong*.log`、`build/batch41-unit.log`、`tools/ci/check_backup_plan_sync.py`、`build/device-track-batch41/pdf/device-evidence/`）。「记录」项没有可整理的执行物，硬造会是空壳，故未以 E 文件承载。
- 谁消费本索引、若 P12 确指「记录」：请按上表最后两行调整，不要把这行当已裁定。

## 1. 环境与源绑定

| 项 | 值 | 依据 |
|---|---|---|
| HEAD | `62522930bc8bae51be1523c954f39449e054b6af` | `git rev-parse HEAD`（证据轨本轮实测）；E-41-D01 §2 |
| 工作树 | 设备轨运行时 `git status --porcelain` = 584 条；证据轨本轮实测 = **595** 条（他线在飞；不归因） | E-41-D01 §2；本轮 `wc -l` |
| 源快照 | 1287 文件；`src-sha-before/after` = `297b2070ce287d73cace1dbf794b5eee4d05d8d183b9b5e362cacc5f46a11734`；`src-snapshot.diff` = 0 行；修复后 `src-sha-after-fix` = `fc93c37b6219d4bb9fae40b92ddbbe5f02c0fcdcc8e29c2d88e0daf8245b20e5` | E-41-D01 §2；本轮 `sha256sum` 复核同值 |
| 源漂移 | 01:16–01:22 他线（KB retrieval）改 `core/database/src/main` + `core/data/src`；`src-sha-now.txt`（1290 行，sha `a418af4a…`）与 after-fix 差 42 行/21 文件，**全部设备套件结束之后** | E-41-D01 §2；本轮 `wc -l`/`sha256sum` |
| 构建 | localFirst 四包 exit=0（23 s）；修复后重建 app test APK exit=0（30 s）；strictOffline 两包 exit=0（1 m 44 s） | E-41-D01 §2、E-41-D03 §3、E-41-D05 §2 |

## 2. 产物身份（宿主 = 设备逐字节一致）

| 产物 | 字节 | sha256 |
|---|---|---|
| app localFirst | 183602999 | `5793300c5f453a45715e9846f1e16756dac1300e04dd68d026e6f7957bccd796` |
| app androidTest（修复前/后） | 1691911 / 1692019 | `683cfafd…af89e` / `0e906482a0a98d1e4b9f5bb56a3262a80a32d9f6404b80495289b22cec4e3aca` |
| data androidTest | 194469464 | `82a6f21e05419a9e9316373fe31f467824c082e9d08db107c99141c353ffac02` |
| database androidTest | 209357634 | `6f55edbf52a72fca123ecb94992689f9a618357936458e557a58ccfc1d49291b` |
| app strictOffline（仅隔离臂诊断，未用于任何绿） | 192586802 | `9a5f6a6e85e18eef6145b45f3307a72bc4031e3ea508f95f01f15a3dac2faaba` |
| androidTest strictOffline | 1691033 | `d300d8855c05a0ad8bdb8a372eebca3538bbebd0a960f36eeb4cbed7a7c9a721` |

- `73.json`（data 测试包内 = 磁盘）：393415 B / `c7e7586a…39f1e`，version 73 / 92 表 / 2 view / identityHash `1d086050…`；schema 资产 73 份。
- **门禁产物没有身份**：P11 那次 `:app:packageLocalFirstDebug` 打出的 APK 已被 00:46 重建覆盖，见 `p11-postfix-strong-gate.md` §4。

## 3. 步骤与判定

> D01–D06 由**设备轨**执行；本索引只转录其判定与锚点，**未复跑设备**。P11–P13 中 P12/P13 的检查由证据轨亲手执行（P11 为转录 + 复核其原始日志）。

| E-ID | 文件 | 覆盖 | 判定 | 锚点 |
|---|---|---|---|---|
| E-41-D01 | `step01-rebuild-install.md` | 四包重建 + 源快照 diff=0 + 安装哈希核对 | **PASS** | §2 diff=0/1287；§3 四包 sha；§4 asset |
| E-41-D02 | `step02-schema73-device.md` | matrix 1→73 16/16、backup 34/34、rowCounts 2/2、trimmer 10/10、c4 8/8、错因账本 3/3、drill 4+2 | **PASS** | §1 逐套件；§2 drill 两阶段 |
| E-41-D03 | `step03-kd75-pdf-roundtrip.md` | KD-75 设备往返 10/10 + pypdf 独立读回；含证据目录缺陷修复 | **PASS**（本机/本 APK） | §2 首红；§3 修复；§4 独立读者 |
| E-41-D04 | `step04-kd76-single-round.md` | KD-76 单轮入口 1 轮 | **单轮 PASS / 主口径 NOT EVALUABLE（n=1/20）** | §2 原始样本；§3 汇总输出 |
| E-41-D05 | `step05-isolation-lowload.md` | 低负载门 + isolation 单臂 | **NOT PASSED**（3/3 次 `UNGATED own_cpu_busy`，15 例全 `-4`） | §1 门 PASS；§2 全跳过；§3 归因未定案 |
| E-41-D06 | `step06-crash-signatures.md` | 九类签名（17 份 logcat） | 全部 0，**但覆盖不足以称健康** | §2 表；§3 覆盖边界 |
| **E-41-P11** | `p11-postfix-strong-gate.md` | 环后强门：4 次宿主工具链失败 + 第 5 次 exit=0；androidTest 编译任务全 UP-TO-DATE | **PASS（仅打包 + 输入未变）** | §2 五次尝试；§3 UP-TO-DATE；§4 无 APK/sha 绑定 |
| **E-41-P12** | `p12-unit-gate.md` | 单测门两轮（00:20 日志 / 01:41–01:43 XML）：均 6 红 | **FAIL**（3 红=工作区工具产物；3 红=KB golden） | §4 原文；§5 根因复现 |
| **E-41-P13** | `p13-ws1-subpieces.md` | 四方一致性门 exit=0；pypdf 6.16.2 独立读回 7/7 | **PASS** | §2 逐项 OK；§3 七文件表 |

## 4. 结论与可证性（哪些**只能**由本批证，哪些 UNVERIFIED）

| 面 | 判定 | 仅本批可证的部分 | 不得外推 / UNVERIFIED |
|---|---|---|---|
| **schema 73 设备面** | 全绿（matrix/backup/rowcounts/trimmer/c4/drill/错因账本） | 绑定本批四包 sha（§2）+ 1287 文件源快照 | 真机、API≠34、page-size≠4096；当前树（595 条脏、他线已改 `core/data`）**不能逐字节复现**本轮绿 |
| **KD-75** | 设备往返 10/10 + 独立读者 7/7 | 本批 APK `0e906482…` + 本批拉出的 7 个 PDF（sha 见 E-41-P13 §3） | `core/export` 生产接缝仍为 patch 草案；pypdf ≠ 平台渲染 |
| **KD-76** | 入口可用；无主口径 M | 单轮 1 轮样本 | 20 轮 p95、G1/G2 判词、跨进程独立性 → **UNVERIFIED** |
| **isolation** | 系统门 PASS 但自证 UNGATED；0 例执行 | 3 次 `am instrument` + 低负载快照 | 配对结论、T4 控制轮 → **UNCONVERGED / 不得写"通过/T4 排除"** |
| **环后强门** | exit=0 | 584 条脏树上该任务集 exit=0；四次失败均为宿主工具链 | 冷构建、APK 身份、该门源快照 → **UNVERIFIED** |
| **单测门** | 6 红 | 两轮同 6 红；`core:database` 3 红根因（`.mimosa` 钩子 JSON）已静态复算复现 | Run A/B 的 exit code、Run B 的命令与 HEAD → **未记录**；KB 3 红归 KB 线，本批未修 |
| **四方一致性 / 独立读者** | PASS | 本批亲手执行、输出落盘 | 未进 CI（patch 草案）；非"独立读者已成门" |

## 5. 同批其他轨文件（非本轨产出，仅索引，不背书）

- `ws2-core-export-seam-patch.md`（WS2 `core/export` 接缝草案）
- `ws4-isolation.md`（WS4 低负载/配对/T4，判定 UNCONVERGED）
- `ws6-error-cause-chain.md`（WS6 错因端到端）
- `step-aud08-gate-recompute.md`、`step-aud08-prohibition-scan.md`、`research-crosscheck.md`
- `rulings/20261102T143319Z-4c83a01e.json`

## 6. 缺口与未验证（汇总）

1. KD-76 20 轮主口径与 G2 判词未跑 → UNVERIFIED。
2. isolation 15 例未执行（`own_cpu_busy` 未定案）；OFF/ON 配对与 T4 控制轮未跑 → UNVERIFIED。
3. KD-75 首红原始 log 被重跑前 `rm -rf pdf/` 覆盖，红只留文本记录（E-41-D03 §2 已披露）。
4. 未跑 28 套件全量、worker 生命周期、`ps -A` 无孤儿、16KB 页、`mapping.txt`。
5. 源在设备套件后他线漂移；当前树不能逐字节复现本轮设备绿。
6. 未运行 `:app:connectedLocalFirstDebugAndroidTest`；未改热区；唯一源码改动是 WS2 新建的 untracked 测试文件证据路径（E-41-D03 §3）。
7. **强门的 APK/源没有身份**（P11 §4）；强门里 androidTest Kotlin **没有真正重编**（全部 UP-TO-DATE）。
8. **单测门两轮 exit code 都没有记录**（P12 §7）。

## 7. 本批新增发现（仅在本批环境见证，供记录/台账引用）

| # | 发现 | 证据 | 归因 |
|---|---|---|---|
| N1 | `core/database/schemas/.../.mimosa/hook-state/*.json`（gitignored 的钩子状态）写进 Room schema 目录，使 `ExportedSchemaContractTest` 3 例必红；73 个真实 schema 本身 0 违规 | E-41-P12 §5（含静态复算命令与 exit） | 工作区工具（Mimosa 钩子）+ 测试递归扫描不排除隐藏目录；**非产品码回归** |
| N2 | 强门 "androidTest 编译" 在 00:36 与 00:46 两次构建中均为 UP-TO-DATE，本轮没有一次真实重编 | E-41-P11 §3 | 门的证据强度被高估；UP-TO-DATE 只证明输入哈希未变 |
| N3 | 单测门 Run A 的栈帧行号（`:170/:180`）超出当前源文件长度（157 行） | E-41-P12 §7-3 | 运行时的源码版本不可重建；不得把该轮当成当前树的红 |
| N4 | PDF 注入未新增读者告警：source 与 labeled 的 pypdf 告警集合完全相同；二次注入字节级幂等（完整 sha 相同） | E-41-P13 §3 | 新增的正面结论，替代批次 40 §8 #13 的"无日志"缺口 |

## 8. 脱敏扫描（本目录统一跑一次，2026-11-03）

```bash
E=docs/research/evidence/2026-11-03-batch-41
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' "$E" || echo "0 hits: secrets"
grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken)[[:space:]]*[:=][[:space:]]*[^ <]' "$E" || echo "0 hits: credential assignments"
grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$E" || echo "0 hits: emails"
grep -RInE 'C:.Users.[^ ]+|/home/[^/ ]+|/Users/[^/ ]+' "$E" || echo "0 hits: home paths"
```

| 扫描 | 结果 | 处置 |
|---|---|---|
| secrets | **2 命中，均假阳性** | `research-crosscheck.md:23` 的文件名 `model-ta`+`sk-layering` 命中 `sk-…` 模式；另一处是本表自身的复述。逐字确认为文件名，非密钥 |
| credential assignments | 0 命中 | — |
| emails | 0 命中 | — |
| home paths | **2 命中，均自匹配** | 两处都是本目录内扫描命令自身的正则文本（`00-index.md` §8、`p11-postfix-strong-gate.md` §6）；无真实路径 |
| 人工通读 | 已过 | 无真实学习者内容、无凭据、无真机序列号；本机账号名已写 `<redacted:account>`，仓库路径写 `<repo>` |

## 9. 回链与维护

- 每个 E 文件均含 `evidence-id / verdict / executed-at / head / device-serial / apk-sha256 / exit-code / raw-log / record-ref / sanitized`。
- 原始全量在 `build/batch41-*.log`、`build/device-track-batch41/`、`build/evidence-batch41-p13/`（gitignored，不作关闭证据）；本目录是仓内最小文本证据。
- 本索引不替代主会话裁定：KD-75 设备面可记绿（绑定 §2 sha），KD-76 维持 open，isolation 不得写通过/T4 排除，单测门在 `.mimosa` 清掉前会持续显 3 红。
- **预算告警（非本轨文件，只登记不改）**：`ws2-core-export-seam-patch.md` 409 行，超过 R4 单文件 400 行上限；该文件属 WS2 轨，删改需其所有者决定。
