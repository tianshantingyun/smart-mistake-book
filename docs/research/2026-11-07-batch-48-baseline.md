# 批次 48 基线核对（2026-11-07）：金标 v2 + 切分修复 + D 域改判

| 字段 | 值 |
|---|---|
| 角色 | 基线核对员（只读；未改任何主码/台账/金标文件） |
| 计划 | `.zcode/plans/plan-2026-11-07-batch-48-golden-v2.md`（已完整读取，56 项 / WS1–WS5） |
| 上一批终裁 | `docs/research/2026-11-06-batch-47-record.md` §9（NOT ACCEPTED；PASS 897/FAIL 23/BLOCKED 280）+ `docs/acceptance/adjudication.md` 追加裁定（2026-11-06 用户三项：真机豁免 / Provider 冒烟 PASS / 金标走代理路径）——两处已读 |
| 核对轮 | 2026-11-07，本会话实跑（所有命令与输出见各节） |
| verdict | **可开工**：24 页图像来源已定位（来源③存在且可拉取，非 BLOCKED）；Provider 冒烟本会话实测可用；台账计数与终裁一致 |

---

## 1. HEAD / 远端 / 工作树

| 项 | 实测（本会话命令与输出） |
|---|---|
| HEAD | `git rev-parse HEAD` → `3007a2bcef719150e1710737fc3f13c7b2938266`（= 记录的 3007a2bc「用户三项裁定落账 + E-48-PROV-01」） |
| 远端同步 | `git status -sb` → `## main...origin/main`；`git rev-list --left-right --count origin/main...HEAD` → `0 0`（无领先/落后） |
| 工作树 | `git status --porcelain \| wc -l` → **551** 个未提交路径（跨 app/、core/、docs/、.gitignore 等；上一批遗留状态） |
| 热区含义 | **工作树不干净** → 按计划 §2，`RoomCaptureWorkflowRepository` 等热区主码若必须动，**只出最小 hunk 或 patch，不直接改**（计划原文："若仍是他线热区则只出 patch"） |

## 2. 台账计数（PASS / FAIL / BLOCKED）

本会话实数（非引用）：

```text
$ awk -F'|' 'NF>6 {gsub(/ /,"",$(NF-1)); print $(NF-1)}' docs/acceptance/ledger.md | sort | uniq -c
   897 PASS
   280 BLOCKED
    23 FAIL
     2 verdict     ← 表头行
     1 ---         ← 分隔行
```

- **PASS 897 / FAIL 23 / BLOCKED 280**（合计 1200 = D0 100 + A–J 每域 90 + FIX 100 + GATE 100），与批次 47 记录 §9.1、`adjudication.md` §10 前置陈述一致。
- FAIL/BLOCKED 不得洗绿：本基线未做任何 verdict 改动（只读核实）。
- 6C-2 三项裁定不可变：`docs/acceptance/adjudication.md` 只读确认仍在（批次 47 终裁 §9.1 第 5 条"未漂移"陈述未被本批触碰）。

## 3. Provider 三键与本地 deepseek-flash 冒烟

`local.properties`（gitignored，`.gitignore` 实证含 `local.properties` 行）：

```text
$ cut -d= -f1 local.properties
sdk.dir
SMB_REAL_PROVIDER_BASE_URL
SMB_REAL_PROVIDER_KEY
SMB_REAL_PROVIDER_MODEL
```

三键齐备，本会话读值（**输出只含 key sha8**，密钥本体未进任何文件/日志）：

| 键 | 值（非密项） |
|---|---|
| `SMB_REAL_PROVIDER_BASE_URL` | `https://api.deepseek.com` |
| `SMB_REAL_PROVIDER_KEY` | present，len=35，**sha8=`cd10efbc`**（与 `E-48-PROV-01` 记录的 sha8 逐字一致） |
| `SMB_REAL_PROVIDER_MODEL` | `deepseek-flash` |

本地冒烟可用性（本会话实测，非引用旧证据）：

```text
$ python -I -c "...urllib GET $BASE/models with Bearer $KEY..."
→ HTTP 200, models=['deepseek-flash', 'deepseek-v4-pro']
```

- **连通 PASS（本会话复跑）**。`chat/completions` ×3 的协议冒烟未重跑，以 `docs/research/evidence/2026-11-06-batch-48-provider-smoke/E-48-PROV-01.md`（已存在，SMOKE_EXIT=0，672–764ms）为准。
- 证据口径：任何新证据只允许记 sha8 `cd10efbc`；`E-48-PROV-01` 的"图像输入能力未测（/models 未声明 vision）"限制仍有效，D 域图像任务不据此放行。

## 4. 24 页图像来源三路探查（本基线重点）

依据：`docs/research/2026-11-04-aud08-human-gold-sampling-plan.md`（已完整读取）——24 页清单（§4，含逐页 sha256 与 dev16/acceptance8 切分，与红线"16/8"一致）；抽样框 = `C:\Users\听云\Pictures\错题数据集`（§2，155 张，仓外）。v1 金标 `tools/split_golden/tables/split_golden_v1.json` 已读 **file 字段**（7 页：IMG20260810003928/004304/004353/005605/010349/011126/011800 .jpg，均为文件名）；**boxes/切分值一律未读**（盲标红线，本核对员不产生任何标注输入泄漏）。

### 4.1 来源①：仓内与 gitignored 导入目录 —— **不存在**

| 探查 | 命令 | 结果 |
|---|---|---|
| 已跟踪文件 | `git ls-files \| grep -i IMG2026` | 空 |
| 未提交 551 路径 | `git status --porcelain \| grep -ci IMG2026` | `0`（命中的仅 `.agent_audit_crops_*.png`，KB 审计裁片，非 IMG2026 页面） |
| 工作树浅层 | `find . -maxdepth 3 -iname "IMG2026*" -not -path "./.git/*"` | 空 |
| gitignored 深层目录 | `find <dir> -iname "IMG2026*.jpg"` 逐个：`knowledge-production` `kb_tools` `.superpowers` `.zcode` `smb-toolloop` `knowledge-research` `scratch` `kb_pdf_samples` `.artifacts` | 全部 `0` |
| 其他候选目录 | `captures/`、`artifacts/` | 目录不存在（`ls: cannot access ... No such file or directory`） |

### 4.2 来源②：模拟器/设备 app 数据 —— **当前无数据可拉（app 未安装）**

```text
$ C:\Android\Sdk\platform-tools\adb.exe devices
emulator-5562   offline
emulator-5566   device
emulator-5584   device
$ adb -s emulator-5566/-5584 shell "getprop ro.build.version.release; pm list packages | grep -ci tingyun"
14 / 0   （两台均 Android 14，均无任何 tingyun 包）
```

- `applicationId = com.tingyun.smartmistakebook`（`app/build.gradle.kts:27`；flavor 后缀 `.offline`/`.localfirst` 见 :54/:59，均含 "tingyun"，故 grep 计 0 即"全部变体均未安装"）。
- 结论：两台在线模拟器上**无 app、无 filesDir 图像**，`run-as ... ls/pull` 无对象可执行（本基线未执行任何安装/写入，只读探查）。adb 守护进程本会话从零启动，emulator-5562 为 offline 不可探。
- 若 WS3（任务 32–35 产品路径）需要设备轨：模拟器在位、可安装，但安装与凭据注入属后续执行件，不在本基线范围。

### 4.3 来源③：用户相册导入目录 —— **存在且可拉取（24/24 逐字节核对通过）**

```text
$ ls "C:\Users\听云\Pictures\错题数据集" | wc -l   → 155
$ python -I（对计划 §4 的 24 个文件名逐一 sha256 比对）
→ 24-page check: match=24/24 missing=0 mismatch=0
   size range: 4865651 - 8845411 bytes
$ python -I（抽样规则复算：sorted 155 → 下标 0,6,...,138）
→ frame size: 155; draw size: 24; draw == plan24: True
```

- 抽样框与计划 §2 完全一致（155 张 `.jpg`，`IMG<日期><时间>.jpg` 命名）；24 页**逐字节 sha256 与计划 §4 表全等**；抽样规则（字典序 + 步长 6）复算出的 24 个文件名与计划清单**完全一致**。
- 文件对当前会话可读（普通用户目录，无权限障碍）→ **可拉取**：可直接按计划 §9.1 第 3 步"复制到只读工作目录 + 逐字节核对 sha256"执行。
- 未做：JPEG 解码/重复字节检查（计划 U6，属 S2 标注步骤，非基线范围）。

### 4.4 来源裁定

**按计划 WS1 任务 1 的三选一：取来源③（用户相册导入目录 `C:\Users\听云\Pictures\错题数据集`）。①不存在、②当前无数据、③存在且可拉取 → 24 页清单可固定、可执行，本批不构成 BLOCKED，无需编造图像。**

## 5. 门与红线前置确认（供本批执行对照）

| 红线 | 基线状态 |
|---|---|
| 盲标 | 本核对员只读 v1 `file` 字段与抽样计划文本，未读任何切分算法输出或 v1 `boxes`；v2 标注阶段须维持同条件（计划 WS1 任务 3–11、红线节） |
| dev/acceptance 16/8 | 计划 §4 已固定（16 dev / 8 acceptance，键 = sha256(文件名)），acceptance 只复算一次 |
| 等级如实 | `evidenceGradeLevel=model-labeled` 须照计划与裁定 3 如实标注；`adjudication.md` 追加裁定明确"在此之前 AUD-08 门维持 FAIL，不洗绿" |
| 门阈值 0.9 | 未改（`tools/split_golden/check_split_gate.py`、`validate_split_golden.py`、`measure_split_accuracy.py` 等工具在 `tools/split_golden/` 在位，本基线未改任何门值） |
| Gradle 串行 / adb | 本基线未跑任何 Gradle、未跑 connectedLocalFirstDebugAndroidTest（红线禁令重申）；adb 可用（SDK `C:\Android\Sdk`），无真机 |
| 密钥 | 三键只在 gitignored `local.properties`；本文件与所有输出仅含 sha8 `cd10efbc` |

## 6. 未验证 / 交接

1. **未验证**：24 页 JPEG 可解码性（U6）；`chat/completions` 本会话未重跑（引用 E-48-PROV-01）；`heldOutUnusedForTuning` 真伪（协议自证字段，计划 §5 自认不可机检）。
2. **落点尚未创建**：`docs/research/evidence/2026-11-07-batch-48/` 与 `docs/research/2026-11-07-batch-48-record.md` 均不存在（`ls` 实证）——属本批执行件，由后续任务落盘。
3. **交接提醒**：工作树 551 脏路径未收口 → 热区主码改动只能出 patch；J-001 `--require-clean` 类前提在提交前结构性不可满足（批次 47 §6.7 已记）。
4. **设备轨现状**：模拟器 2 台在线（Android 14）但 app 未安装；真机无（用户已裁定豁免，裁定 1）。
