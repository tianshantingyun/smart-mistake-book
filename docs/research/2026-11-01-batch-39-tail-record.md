# 批次 39 收尾轮记录（2026-11-01 / 设备窗 2026-11-02）：X-03 复跑 + isolation 15/15 确认 + T4-reverted 控制轮 + KD-76 校准

> 记录员落笔位。依据：计划 `.zcode/plans/plan-2026-11-01-batch-39-tail.md`（**29 行，本会话完整实读**）、收尾轮基线 `docs/research/2026-11-01-batch-39-tail-baseline.md`（355 行）、两研究文档（R1 归因 `2026-11-01-isolation-attribution.md` **503 行** / R2 统计 `2026-11-01-kd76-stats.md` **347 行**，`wc -l` 本会话实读）、设备证据 `docs/research/evidence/2026-11-01-batch-39/`（E-09…E-14 六个 step + 00-index §11/§12/§13，本会话全文实读 E-10…E-14）、四实施轨工单摘要（转录，按轨自报范围）、第 2 轮复核发现（26 条，含 1 条"未复核"）。**记录员未运行 Gradle、未执行任何 adb 命令、未做任何 git 写操作、未改任何 Kotlin/Gradle 产品代码**；本记录写入 + 三处台账/总路线同步（§附录 A）构成本轮全部落盘。

> **本记录不声称"批次收尾轮全绿"**：
> - **X-03 = 设备 PASS**（`OK (1 test)`），欠账是否关闭归主会话；
> - **isolation = FAIL**：基线窗 8/15 红、控制窗 10/15 红；控制轮不可证伪、两窗红集仅 7 例交集 ⇒ **T4 未排除，D 也不能单独归因**；
> - **KD-76 = FAIL / UNVERIFIED**：主口径 `M = S-bind + S-warmup` p95 = **9813 ms > 5000 ms**（本会话复算同值），有效 n=9 < 预注册 20，且执行形状不是 reboot 冷启动轮；KD-76 **维持 open**；
> - **backup / c4 / rowcounts = 红（B+D）**：测试包内 `StudyDatabase=72` vs `SmbkSelectionPlan=71`，归他线 transient schema-72 + 构建窗口，不是本批产品回归；
> - **29.5 s 链未实测**：S3 门未取得、S4 无计时点，只可报 `Σ_partial`；
> - **DB schema 71/72 未裁定**：现树 `STUDY_DATABASE_VERSION = 72`，计划硬约束写 71，`S=71` 门只校验 `71.json`（真绿假验）；
> - **设备证据的快照绑定已收紧**：X-03（00:56）与 isolation 两窗（01:06–01:24）绑 **窗 W2**（`src-sha-before` 00:45 = `after-build` 00:47 = `after-restore` 01:27，diff=0）；E-12 套件（01:27–02:57）与 E-13 KD-76（03:05–03:10）落在 E-14 **判作废的窗 W3**（29 路径变动），只能以 03:10 收口快照作近似锚；而现树与 03:10 快照**再差 35 个路径**（§1.3）。任何设备数字都不对现树 HEAD `70611e61` 成立。

## 0. 结论一览

| 项 | 判定 | 证据 | 关键限定 |
|---|---|---|---|
| 任务一 X-03 设备复跑 | **PASS** | E-10；`suite-x03.log:20` `OK (1 test)`、`Time: 5.112`；code0=1、非零 code=0、9 类签名 0 | 首跑红 = A 类类型夹具（`Integer(300) != Long(300)`）；修复为同值同序类型归一，未放宽断言；绑 W2 快照（00:45=01:27） |
| 任务二 isolation 15/15 | **FAIL** | E-11；`Tests run: 15, Failures: 8`（基线）/ `Failures: 10`（控制）；watchdog hard kill 11 / 13 | 两窗都不是低负载窗（峰 4.79 / 5.49）；kill ≤ 6 不是通过判据（待裁） |
| 任务三 T4-reverted 控制轮 | **已执行，判别不成立** | E-11 §3/§5；`control-baseline-engine.sha256` 是**基线** sha；`build-control.log` 无 revert 记录；两窗 app+appTest 都不同 | **T4 未排除**，原"控制轮更差 ⇒ T4 排除"撤回 |
| 任务四/五 KD-76 校准 + 29.5 s 链 | **FAIL / UNVERIFIED** | E-13；G1 红 11/20，n=9；M p95=9813；S3 门未取得、S4 无计时点 | 用例形状 = 单 instrumentation 进程 + 无 reboot + 热页缓存；`READY_TIMEOUT` 零改动维持 10 s |
| 任务六 既有套件不回退 | **PARTIAL（窗作废）** | E-12；28 套件 / 236 例，24 套件绿（177 例），红 26 例 | 全部落在 E-14 判作废的窗 W3；绿只作运行观测 |
| 任务七 硬门/源快照门 | **W2 PASS / W3 FAIL** | E-09 / E-14；build 窗 diff=0；W3 172 行 / 29 路径 | 收口后现树再漂 35 路径（本会话实测，§1.3） |
| 任务八/九 两研究 | **已交付** | R1 503 行（P/G/A 判据、A3 kill 归属、M2 回退、GitHub 评分）；R2 347 行（分位口径、outlier、29.5 s、anti-flaky 12、参考评分） | 研究结论均未在设备上复核，阈值只由 1 个绿窗标定 |
| 任务十 台账/记录 | **已落** | 本记录 + `known-defects.md` KD-76 块 + 准入清单附录 K + MASTER-LINE 头部 | 台账/清单为共享脏文件，整文件纳入并注明 |

---

## 1. 范围与基线

### 1.1 计划范围与实际执行面

- **计划口径**（plan §1，10 项）：X-03 设备复跑 / isolation 15/15 低负载确认 / T4-reverted 控制轮（条件）/ KD-76 校准用例 / 29.5 s 链分段 / 台账滚动 / 热区条件轨九项 / R1 归因研究 / R2 统计口径 / 集成→双门→设备→证据→复核→修复环→记录。
- **实际执行面**：两研究落盘（R1/R2）+ 四实施轨（T1 设备判据 / T2 校准用例 / T3 台账 / T4 条件轨）+ 集成交接（以复核修复轮回写证据，无新接线）+ 设备轨六步（E-09…E-14）+ 第 2 轮复核修复（E-11 §5 / E-12 §7 / E-13 §8 / 00-index §13）+ 本记录与三处同步。
- **与计划的偏差（显式登记）**：
  1. **计划任务 3 的开关对象写错**：计划写"`beforeRegistrationHookForTest`/打点开关回退版本"。实读该缝在 isolation 套件 **0 引用**（唯 JVM 用例使用），控制轮有效 revert 对象是 `VisualJsClientEngine.kt` 的 6 处时间戳写点 + 访问器/类。**若不改对象，控制轮测的不是 T4**（R1 §3.5 / 基线 §7-D6）。
  2. **计划任务 2 的 kill 判据 `≤6` 被裁定废除**：改用"逐方法归属后非设计 kill = 0"（全绿时 K 恰为 5，分布 `{aHungWorker:1, aSecondConcurrent:1, rebuildsOnce:2, watchdogKills:1}`）。本批两窗按两读都 FAIL，判定不受影响；但"台账单方面改判据"已按待裁登记（E-11 §5.6）。
  3. **"低负载窗"未取得**：计划要求"设备沉降后 φ 单窗复跑"；实际两窗开窗瞬间 1-min loadavg 0.34/0.80 达标，**窗内峰值 4.79/5.49**，P1/P3 未按 R1 §3.2 定义记录。原"低负载窗"表述已撤回，改称"沉降后窗"。
  4. **KD-76 预注册执行条件不可满足**：预注册要求 `adb reboot` 后不沉降即跑、两批独立；实际是**单个 instrumentation 进程内 20 轮（两批各 10）+ 无 reboot + 热页缓存**，用例 KDoc 自承。即使某次 n=20 全绿也不能单独作为关闭主口径证据。
  5. **"13 个既有套件"无对应集合**：计划/仓内均无 13 套件清单；本批底册 = **28 套件 / 236 例**（24 既有 + 4 新向量），对任何 13 子集都更严。
  6. **证据目录未新开 `-tail/`**：E-09…E-14 落在 `2026-11-01-batch-39/`（R4 要求每批一个 `00-index.md`、目录名 `<date>-batch-<NN>`），该目录 `.md` 已达 16 个上限；`build/device-track-batch39-tail/` 仅是本机 raw-log 路径。消费方按字面 `-tail/` 目录会找不到（复核#14）。
  7. **T1 判据轨的红例 awk 命令有字段错误**（`$3` → 空恒 0），E-11 §5.5 已更正为 `$2+0 < 0`；但 **T1 交付文本本身未回改**（§5-F8）。

### 1.2 基线摘要（本会话实测）

| 字段 | 值 | 命令/锚点 |
|---|---|---|
| 记录时点 | 2026-11-02（+08:00） | — |
| HEAD（现树） | **`70611e61`**（2026-11-02 01:54:30，KB 批次 32A：5 文件 docs/tools） | `git rev-parse --short HEAD`；`git log -1 --format=%ad` |
| HEAD（设备证据绑定） | **`f10a1e4e`** | E-10…E-14 头表 |
| HEAD（计划 §0） | `cd96a08e` | plan:5 |
| 工作树 | **552** 条（120 M + 432 ??），暂存区 0 | `git status --porcelain \| wc -l`；`awk '{print $1}' \| sort \| uniq -c` |
| 收尾轮基线工作树 | 484（批次 39 基线）→ 514（设备开窗）→ 510（E-14 收口） | 各文件头表 |
| DB schema（现树） | **72**（`StudyDatabase.kt:140`） | `grep -n STUDY_DATABASE_VERSION` |
| DB schema（计划硬约束） | 71 | plan:28 |
| 设备 | `emulator-5562` / AVD `test_device` / API 34 / x86_64 / PAGE_SIZE 4096 | E-00/E-10…E-14 |
| 套件底册 | 28 套件 / 236 例（app 7 + data 12 + db 5 + 新向量 4） | 基线 §5.2；`run-suites.sh` 26 个 `run` + x03/isolation 复用 |

### 1.3 设备绿绑定源快照与现树的差距（本会话实测，此前未登记）

- E-14 收口快照 `src-sha-after-all.txt`（2026-11-02 03:10:34，五模块 `app/src core/model/src core/data/src core/database/src core/domain/src`）。
- 本会话用**同一 `find … | sort -z | xargs -0 sha256sum` 命令**重算现树并与该快照 diff：

```text
diff 行数 = 134
唯一变化路径 = 35
  core/data/src      27（其中 27 个为 resources/knowledge/*.json：moe-2020 1 + moe-2025 v2-01…v2-25 + update-manifest）
  core/database/src   5（KnowledgeBaseImportContract.kt、4 个 test）
  core/model/src      1（ProblemCatalog.kt）
  core/domain/src     1（KnowledgeResearch.kt）
  app/src/androidTest  1（VisualJsConnectStageCalibrationInstrumentedTest.kt）
```

- mtime 抽样：`KnowledgeBaseImportContract.kt` 04:33:49、`KnowledgeResearch.kt` 04:35:31、`ProblemCatalog.kt` 04:33:12、`moe-2025-…-v2-01.json` 04:41:09、校准用例 04:30:27 —— **均晚于 03:10 收口快照**，属他线 KB 工作 + 本轨复核修复轮（校准用例）在收口后的追加。
- 结论：
  - **X-03（00:56）与 isolation 两窗（01:06–01:24）绑窗 W2**：`src-sha-before`（00:45:xx）→ `after-build`（00:47）→ `after-restore`（01:27）三份快照 diff 均为 0（E-14 §1），E-10/E-11 的结论成立于该快照。
  - **E-12（01:27–02:57）与 E-13（03:05–03:10）落在 E-14 判作废的窗 W3**：`src-sha-after-all`（03:10:34）与 before 相差 172 行 / 29 路径（本轨 1 + 他线 28）。它们**没有**稳定快照支撑，只能以 03:10 收口快照作近似锚。
  - **现树与 03:10 快照再差 35 路径**（上文实测，mtime 04:30–04:41），HEAD 亦由 `f10a1e4e` 前移至 `70611e61`。
  - 因此：任何引用这些设备数字的后续动作必须先在**同一静止窗**重建重装；旧绿/旧红都不对现树成立（沿用"设备绿绑定源快照"纪律）。

---

## 2. 两研究线

### 2.1 R1 isolation 归因研究（`2026-11-01-isolation-attribution.md`，503 行，驱动 T1/T3/控制轮）

**结论摘要**：三窗红不是稳定复现子集，而是"结果红 + 窗口负载不达低负载"的产物；`K ≤ 6` 不能作通过判据；控制轮须用 M2（只删 6 处写点）且控制包必须可证伪。

#### 2.1.1 低负载窗接受条件（三层，全部满足才接受）

**P：程序性前置（必须做且必须记录时间戳）**

| 编号 | 条件 | 核验方式 |
|---|---|---|
| P1 | 窗内无并发 Gradle / 无并发 `am instrument` / 无 `adb install` | 脚本时间线 + host 进程列表快照 |
| P2 | install 完成后 ≥10 min 无 instrumentation；他线模拟器可在线但不得跑本轨套件 | `adb devices -l` + 时间戳 |
| P3 | 窗前 5 min、窗内每 10 s、窗后 5 min 采 host `cat /proc/loadavg` 与 guest `adb shell cat /proc/loadavg`（只记录、不作硬门槛） | 落盘 `*-load.txt` |
| P4 | 同一设备/API/ABI/页大小；`am instrument` 命令与类名逐字相同；记录 app/androidTest 两 APK sha256 | E-00 体例 |

**G：窗内闸门（硬；从同一份 logcat 复算，无需新代码）**

| 指标 | 阈值 | 标定依据 |
|---|---|---|
| worker warm-up **p50** | ≤ 1.8 s | 绿基线 1.38 s × 1.30 |
| worker warm-up **p95** | ≤ 6.0 s | 绿基线 5.11 s × 1.17 |
| worker warm-up **max** | ≤ 8.0 s | 绿基线 7.00 s × 1.14 |
| `warm-up failed` | = 0 | 服务侧进程级失败 |
| 未配对 spawn（spawn − warmN） | ≤ 1 | 绿基线 0–1 |

标定样本 = **N=1 个绿窗**（批次 38 step14b，19 次 warm-up）；三红窗至少超一项，绿窗三项全过。

**A：结果判据（唯一通过口径）**

| 编号 | 条件 |
|---|---|
| A1 | `OK (15 tests)`、`Failures: 0`、`Skips: 0`、无 `Error in`、15 条 `INSTRUMENTATION_STATUS_CODE: 0` |
| A2 | 红例集合为空 |
| A3 | **非设计 watchdog kill = 0**：逐方法计数 == 设计分布 `{aHungWorker:1, aSecondConcurrent:1, rebuildsOnce:2, watchdogKills:1}`，且无其他方法出现 watchdog kill |
| A4 | `warm-up failed` = 0、`death was not confirmed` = 0、9 类签名 = 0 |

**接受 = P ∧ G ∧ A**；只有 **G 过的红窗**才有归因资格（窗 3 K=6、红 5，G 不过 → 不能作"D 已排除"证据）。

#### 2.1.2 kill 判据：废弃 `K ≤ 6`，改逐方法归属

- 通过阈值 = **A3**；全绿时 K 必然 == **5** 且分布唯一。
- `K > 5` → 至少 `K − 5` 次非设计杀，逐方法列名；`K < 5` → 有设计杀方法提前失败，红集必非空，直接判红——**不能**解读为"更健康"。
- `spawn`/`died` 只作交叉校验（本批全部窗口 `died == K` 成立）。

#### 2.1.3 T4-reverted 控制轮最小方案（M2）

- **只改 1 个文件**（`VisualJsClientEngine.kt`）：删 6 处写点（E1 `:401`、E2 `readResultPayload` 首字节分支、E3 调用点、E4 `:670`、E5/E6 `:821`/`:840`、E7 `:1161`）；**保留** `ConnectStageTimestamps` 类、访问器、`val timestamps`（否则 JVM 用例编译断）。
- **文件级备份还原**：`cp` + `sha256sum` + `sha256sum -c`；**禁 `git stash` / `git checkout .` / `git restore .`**（会动他线数百条改动）。
- **控制变量**：两臂 androidTest APK 必须 sha 相同；设备/AVD/API/ABI/页大小相同；命令逐字相同；每窗都过 G。
- **窗口顺序**：`OFF-1 → ON-1 → OFF-2 → ON-2`（首窗丢弃作预热）。
- **预注册判别**：OFF 2/2 绿 + ON 2/2 红（G 过）→ T4 关联成立；两臂同色 → 与 T4 无关；任一臂 G 不过 → 该窗作废。
- **绑定口径（硬）**：seam 的"设备绿绑定"只认**规范树 T4-on + 规范 APK** 的绿窗；**控制轮 OFF 臂的绿不得写入台账作为绑定证据**，只能作归因/对照材料。
- 推断上限：控制轮只回答"删掉打点后是否不再红"，**不能**单独证明"红由 T4 引起"；因果仍以机制链为主。

#### 2.1.4 GitHub 参考评分（`gh api` 实查，2026-11-01）

| 项目 | license | ★ / 最后 push | 加权 | 裁定 |
|---|---|---|---|---|
| `android/android-test` | Apache-2.0 | 1230 / 2026-10-30 | **4.70** | **必用**：`LogRunListener` 逐方法边界，零改动（本批已在使用） |
| `androidx/androidx` benchmark | Apache-2.0 | 6130 / 2026-11-01 | **4.60** | **采纳原则**：受控设备状态 + 自动节流检测→暂停重试；不引依赖 |
| AOSP Tradefed | AOSP（未逐文件核证） | — | 3.70 | 采纳 `ITERATIONS` 概念（全量重跑、任一失败即失败） |
| `Flank/flank` | Apache-2.0 | 701 / 2026-06-20 | 3.50 | 仅并行/重试设计参考；本轨禁 `connectedAndroidTest` |
| `MarathonLabs/marathon` | **GPL-2.0** | 646 / 2026-09-21 | 3.30 | 概率模型可借；**GPL 代码不搬** |
| `openjdk/jcstress` | GPL-2.0(+CPE) | 531 | 2.80 | 不适用（JVM 并发正确性） |
| `NonDex` | NOASSERTION | 60 | 2.45 | 不适用 |
| `iDFlakies` | **无许可证** | 34 | 2.00 | 不复用 |
| `deflaker` | **无许可证** | 39（2021-11-09 停更） | 1.70 | 不复用 |

**明确拒绝**：Flank/Marathon 的"失败用例单独重跑并合并结果"作为通过口径（会掩盖 flaky，违反本仓"不许放宽断言/重试洗绿"纪律）。

### 2.2 R2 KD-76 校准统计口径（`2026-11-01-kd76-stats.md`，347 行，驱动 T2/T5）

#### ① 分位统一 nearest-rank（与仓库 Kotlin 门同式）

- 公式 `p95 = sorted[ceil(0.95n) − 1]`，与 `GoldenRetrievalInstrumentedTest.kt:605-610` 的 `index = ((size*percent + 99)/100 - 1).coerceIn(indices)` 同源。
- **n=10 时 p95 ≡ max**（第 10/10 个）；n≤19 均如此；**n=20 才容忍一个离群**；n=40 容忍两个；n=59 起样本 max 才可作真 p95 的单侧 95% 上界（`ceil(ln0.05/ln0.95)=59`）。
- 建议门样本 = **两批独立冷启动各 10 轮、合并原始样本 n=20**（预注册；同 APK/同快照/同为 reboot 后不沉降）；只有 10 轮时必须显式声明"p95≡max，本门为 max 门"。

#### ② E-04 的 p95=725 是线性插值（不可混用）

- 样本 `[652,655,678,678,680,692,694,703,709,738]`：线性插值 **p50=686.00 / p95=724.95**（与 E-04 逐位一致）；nearest-rank **p50=680 / p95=738**；差 **13.05 ms（1.77%）**。
- 两套估计量不可混用、不可跨 n 比较；evidence 必须打印口径名与排序后原始样本。

#### ③ outlier 不删 / 不缩尾 / 不重跑到绿

- 失败/超时轮 = **删失观测（≥ 对应预算）**，进样本表与计数，触发独立硬门 **G1（零未归因失败）**，**不能被 p95 门吸收**。
- INVALID（`started()==false`、时间戳非单调、`connectionCount!=1`）= 夹具/seam 缺陷，整批作废重跑，**不得**混入分位数。
- 决定门的离群样本先归因（快照/环境/logcat/seam/夹具/产品）：环境 → 整批作废重跑并保留原批；产品 → 真红。

#### ④ 29.5 s 只能按预算包络相加

- `15 000 + 10 000 + 2 500 + 2 000 = 29 500 ms` 是各段预算之和，是**上界（supremum）**，不是任何单例实测。
- 实测只可加**同一轮集合上的各段 max**（上界），**不可加 p50/p95**（Prometheus 官方："cannot aggregate quantiles"）。
- 四段顶满的单例**结构性不可达**：C1 超时即终止；C2 超时只接 C4、不进 C3；C3 要求前两段成功；拼接 = 夹具接力，不做。
- 报告必须逐段标"实测 / 名义 / 门佐证"；S4 未实测时写 `Σ_partial`。

#### ⑤ anti-flaky 十二条（A1–A12）

归因先行 / 单钟 `uptimeMillis` / 无扣除 / 相对门 / 失败不丢 / 两轮独立 / 不重试洗绿 / 同轮元数据 / 披露 100 ms 轮询分辨率 / 预注册 / 冷启动不预热 / 屏幕与睡眠可观测。

#### ⑥ 参考评分

| 参考 | license | 裁定 |
|---|---|---|
| 仓库内 Kotlin percentile helper + KD-66 用例 | 仓库内 | **最高，直接复用**（统计量照抄同一公式；计时门照 KD-66 四拍） |
| HdrHistogram | public domain/CC0 + BSD-2（双许可；gh spdx=NOASSERTION） | 高（语义参考：直方图可加、coordinated omission）→ **不引入** |
| LatencyUtils | 同上 | 高（语义参考）→ **不引入** |
| AndroidX Macrobenchmark | Apache-2.0 | 中高（只借 min/median/max + 环境控制方法）→ 不引模块 |
| t-digest | Apache-2.0 | 中（n≤60 精确排序即可）→ **不引入** |
| Prometheus 文档 | Apache-2.0 | 高（"分位数不可聚合"的官方依据）→ 只引文档 |

---

## 3. 四实施 + 集成 + 设备 + 证据（逐轨）

### 3.1 T1 设备判据轨 —— 部分（只交判据与命令，零设备/Gradle/git）

- **交付形态**：判据与可重放命令清单（无代码文件）。开工基线实核：HEAD `f10a1e4e`、`git status --porcelain | wc -l` = 492、keep 三文件干净、`STUDY_DATABASE_VERSION = 71`（当时点）、24 套件 `@Test` 逐一汇总 224、isolation 15、X-03 1、androidTest 对 `connectStageTimestampsForTest` **0 引用**、无 `@Ignore`/`Assume`。
- **判据速查**：
  - X-03 = `OK (1 test)` + 1 个 code 0 + 三处 0 派发 witness 无条件执行；
  - isolation = `OK (15 tests)` + 15 个 code 0 + 红例集合 ∅ + kill 判据（交付文本仍写 ≤6，后由 R1 A3 取代）；
  - KD-76 = n≥10 冷启动 + 主口径 `M=ready−bind` 的 p95 ≤ 5000 ms + 无样本吃满 10 s；
  - 29.5 s = S1/S2 实测 + S3 名义/门佐证 + S4 实测或显式标名义；
  - 套件 = 24 底册 + 4 新向量；
  - 硬门 = 4 APK sha + 快照全窗 diff=0 + 71.json + dex 符号。
- **未做**：零设备、零 Gradle；KD-76 校准用例当时不在树；S4 无计时点；「13 套件」构成未找到。
- **已知缺陷（复核 #8）**：红例集合 awk 命令 `$3+0 != 0` 字段错——日志行是 `INSTRUMENTATION_STATUS_CODE: -2`（值在 `$2`），`$3` 为空恒 0。**本会话实测**：`$3` 版输出 **0 行**（假绿），`$2 < 0` 版输出 **8 行**（正确）；`$2 != 0` 会得 23 行（15 个 `1` + 8 个 `-2`）。E-11 §5.5 已更正，**T1 文本未回改**。

### 3.2 T2 KD-76 校准用例轨 —— 部分（落码 → 已执行 → G1 红）

- **文件**：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsConnectStageCalibrationInstrumentedTest.kt`（`??` 未跟踪；交付时 792 行，**现树 940 行**——第 2 轮复核修复轮追加门改/守卫/UID/中止报告；`grep -c '@Test'` = 1；`connectStageTimestampsForTest` 6 处）。
- **用例契约**：单 `@Test`（`coldStartConnectStagesMeetTheReadyHalfBudgetAcrossTwoBatches`）；预注册 20 轮（两批各 10）；nearest-rank；G0 形状先于时长 → G1 零失败 → G2 `p95 ≤ READY_TIMEOUT/2`（门由 10 000 镜像派生，=5000）→ G3 报 max；每轮四段 + total + 每段 n/min/p50/p90/p95/max/排序样本；两批与合并分布；预算包络说明。
- **A1–A12 落点**：A2 用 `SystemClock.uptimeMillis` 夹整轮并断言 seam 五点落窗内；A3 total==四段和；A5 失败记删失并跑完再判 G1；A6 两批完整 + 同 APK/指纹/pageSize + 中位数 ≤2× 才合并；A7 轮 id 唯一且样本数恰为预注册；A8 每轮 loadavg + 每批 APK sha256/指纹/SDK/ABI/page size；A10 分位自检 `0..9 → p50=4/p95=9`；A11 新引擎首访 seam 为 null 且连接数恒 1；A12 每轮与批边界断言屏幕交互。
- **如实登记的偏离**（用例自述 + E-13 §1）：A9 只落可断言的一半（100 ms 常量镜像 + 报告披露）；A6 的"两轮独立设备运行"需设备轨两次执行；第 1 轮冷进程前提无可信 pid seam，存在 **1/20 乐观偏差窗**；执行形状是**单 instrumentation 进程 + 热页缓存**，不是 reboot 冷启动。
- **第 2 轮复核修复**：G2 从 `warmUpMillis` 单段改为主口径 `M=(bindMillis+warmUpMillis)`（逐轮求和后取 p95）；`samples.size < 20` 或 G1 红时只打 `NOT EVALUABLE`；补 `workerUidForTest() != Process.myUid()`；`try/catch` 中止时先 `emit` 部分报告。**新增断言未上设备，效果 UNVERIFIED**（E-13 §8）。

### 3.3 T3 台账轨 —— 完成（后被设备轨逐项更正）

- **落点**：`docs/known-defects.md`（X-03 已修、isolation 留位、KD-76 载体与判据、D-04 装配登记）+ `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（附录 K.1–K.5）。
- **当时结论**：X-03 修复随 `f10a1e4e` 入库（1 file，+19/−2），设备复跑与判据语义证据仍缺、归批次 40；isolation 结果栏空置；KD-76 载体在树但 androidTest 0 引用、无数字；D-04 生产装配属 UI 热区、本批零改动、行义维持 ◐。
- **被更正项**（K.6 / KD-76 2026-11-02 块）：X-03 已 PASS；isolation 两窗已执行且 T4 未排除；KD-76 已执行且主口径 FAIL。原"未发生/留空/0 引用"均为**落笔时点值**，保留原文并按时间点更正，不回改。

### 3.4 T4 热区条件轨 —— 完成（分支 D，零文件落盘）

- 开工实核热区布尔 clean=false（`feature/library` 1 条 `?? gradle.lockfile`、`feature/tutor` 2 条、`core/ui` 1 条 `M Theme.kt`）→ 按指派走分支 D。
- 批次 24 R4 九项序位（G2→G1→AWAITING→5b-1/KD-68→AUD-08→6B-3→5b-2→阶段6→5c）全部仍 pending，载体在现锚点；未新建 KD/条目/文件（KD 台账仍 82 条，尾条 KD-82）。
- 两项实质变化入账：① 热区脏的构成已变（feature 源文件均干净，脏项只剩未跟踪锁文件 + stray `nul` + `core/ui Theme.kt` 兼容别名），但脚本布尔仍 clean=false，维持分支 D；② 漂移仅 AUD-08 审计行锚 `:160→:161`，KD-68 未动。
- 另登记：`ui-queue-sequencing.md:46` 仍写"schema 70（预计；当前 69）"，而现树 schema 71→72；批次 34 原登记仍有效，不重复建条。

### 3.5 集成交接 —— 以"复核修复轮"形态回写，无新接线

- 集成动作 = 把第 2 轮复核的 26 条发现逐条落到证据文件与台账：E-11 §5（APK 身份、低负载表述、因果越窗、控制轮不可证伪、awk 字段、kill 待裁）、E-12 §7（窗作废、计数、p10 放宽披露）、E-13 §8（主口径门、报告守卫、S3、执行条件、采样程序、connectForTest 顺序、UID、中止报告）、00-index §13（12 条汇总）。
- **未新增任何生产接线**；D-04 生产装配点（`SmartMistakeBookApplication.kt:364-368` 三参、工厂 `visualEngine`/`visualFrameInputs` 默认 null）属热区，本批零改动；`previewAssetId` 生产恒 null，行义维持 ◐。
- 未做：JS 广告 / `availableMethods`（红线）、契约 `:208`（docs/current 热区）、锁文件、R-04 keep 行。

### 3.6 设备轨（六步串行，`emulator-5562`）

| E-ID | 步骤 | 判定 | 本会话复核 |
|---|---|---|---|
| E-09 | 重建重装 + keep/schema 一致门 + 四包 sha + asset/dex 门 | PASS（窗 W2） | 只读 gate 日志：`KEEP_GATE_EXIT=0`、`SCHEMA_GATE_EXIT=0`（`RESULT=PASS`，但只对 71.json） |
| E-10 | X-03 复跑（A 类类型夹具修正后） | **PASS** | `grep -n "OK (" suite-x03.log` → `20:OK (1 test)`；9 类签名 0 |
| E-11 | isolation 沉降窗 + T4 控制轮 | **FAIL** | `Tests run: 15, Failures: 8` / `10`；kill 11 / 13 |
| E-12 | 28 套件底册（26 本窗 + x03/isolation 复用） | **PARTIAL（窗作废）** | 24 绿套件、红 26、通过 210（236−26） |
| E-13 | KD-76 校准 | **FAIL / UNVERIFIED** | `Tests run: 1, Failures: 1`；M p95=9813（复算同值） |
| E-14 | 源快照门与阻塞 | **W2 通过 / W3 FAIL** | `src-snapshot-build.diff` = 0 行；`src-snapshot-all.diff` = 172 行 / 29 路径 |

### 3.7 证据落盘

- 六个 step 文件 + `00-index.md` §11/§12/§13；raw-log 全量在 `build/device-track-batch39-tail/`（gitignored，不入仓）。
- 目录 `.md` 数 16（达 R4 §3.1 上限）；后续新增须先裁定归档方式，不得静默删旧证据。

---

## 4. 设备与证据（逐项）

### 4.1 X-03（E-10，PASS）

- 命令：`am instrument -w -r -e class …StrictOfflineRelayoutDeviceVectorInstrumentedTest com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner`（APK `f7e96147…`）。
- 判定：`OK (1 test)`、`Time: 5.112`、code0=1、非零 code=0、9 类签名 0。
- 三断言位（单 `@Test`，无跳闸 ⇒ 绿即三处无条件执行）：`:337-341` 父派发计数 0；`:342-347` operation `SUCCEEDED` + `reserved/used_remote_dispatches=0`；`:348-356` 2 行 attempt 均 `JS`/`SUCCEEDED`/`reservation_id IS NULL`。
- A 类修复位：`:224-228` 两套 id 空间 `assertNotEquals`；`:229-240` `deriveProgramId` 六字段复算 == 持久行 id；`:251-262` ①档 `RelayoutAllowed` 且 `sourceJs` 同值。
- 首跑红归因：`assertEquals` 期望 `Arrays$ArrayList`/`Integer(300)` vs 实际 `ArrayList`/`Long(300)`——同值同序的类型归一，**不是放宽断言**。

### 4.2 isolation（E-11，FAIL）

- 基线窗（01:06–01:09）：`Tests run: 15, Failures: 8`；code0=7、非零 code=8；watchdog hard kill **11**；红集 8 例（含 `aHungWorkerReleasesTheCallerWithinHardPlus500ms`）。
- 控制窗（01:19–01:24）：`Failures: 10`；kill **13**；红集 10 例（新增 3 个 AIDL-wire 用例；基线红的 `aHungWorker…` 转绿）。
- 首例原文：`AssertionError: the worker must render before the hang is injected … Rejected(reason=HARD_DEADLINE_EXCEEDED, diagnostics=visual_js worker died: binder died)` @ `VisualJsIsolationInstrumentedTest.kt:485`。
- 四源：断言位 `:479-489`；产品 watchdog `VisualJsService.kt:525-528`（1500 ms `killSelf`）；预算常量 `VisualExecutionEngine.kt:126` `SERVICE_HARD_DEADLINE_MILLIS = 1500L`；冷启动用例 `:327-339`。
- 负载：基线窗前 0.34 → 窗内 0.62/2.80/3.99/**4.79**；控制窗前 0.80 → 1.24/4.79/3.92/**5.49**；主机 CPU 开窗 62%。**两窗都不是低负载窗**。
- **T4 判别不成立**（细节 §5-F14/F15/F16）：控制基线 sha 是**基线**文件 sha；`build-control.log` 无 revert 记录（`grep -niE 'revert|timestamps|control'` = 0）；两窗 app 与 appTest 都不同；红集仅 7 例交集。⇒ **T4 未排除，D 也不能单独归因**。

### 4.3 KD-76（E-13，FAIL / UNVERIFIED）

- 首跑红 = A 类夹具：`/proc/loadavg` 在 `untrusted_app` 域不可读 → `raw='null'`；修正为记 `-1.0` + 外部采样。
- 第二跑：`Time: 312.636`、`Tests run: 1, Failures: 1`；G1 红 11/20；batch A 9/1、batch B 0/10。
- 有效样本 n=9（nearest-rank）：

| 段 | min | p50 | p90 | p95 | max |
|---|---|---|---|---|---|
| **M = S-bind + S-warmup（主口径）** | 1474 | 1821 | 9813 | **9813** | 9813 |
| S-bind | 937 | 1362 | 9229 | 9229 | 9229 |
| S-warmup（分量，不是门） | 209 | 520 | 1319 | 1319 | 1319 |
| S-readyGap | 3 | 43 | 226 | 226 | 226 |
| S-firstByte | 368 | 764 | 1535 | 1535 | 1535 |
| S-total | 2019 | 2588 | 11467 | 11467 | 11467 |

- **本会话复算**（`python -I`，原始 9 对 `(bind,warmup)`）：M sorted `[1474,1511,1571,1777,1821,2125,2199,2960,9813]`、p50 1821、p95 9813；W p95 1319。**主口径 FAIL**。
- 判别分支：M 超 5 s 而 S-warmup p95 ≤5 s → **慢在 S1（首连，9229 离群）**，按 T1 §5.3 **不得据此改 `READY_TIMEOUT`**。
- 负载：窗内 loadavg 2.66→4.11→6.12→9.82→11.25→8.91→11.12→10.29→9.82→9.50；11 个失败轮均为 1500 ms 硬期限/15 s 首连预算被越过 → **D 是与高负载一致的推断**，不是被隔离出的单一原因（原引用的 02:21/01:57–02:59 构建变动**不在本窗**）。
- 29.5 s：`Σ_partial = max(S1)+max(S2) = 9229+1319 = 10548 ms`（9 样本、热页缓存）；S3 名义 2500 但**门未取得**；S4 **UNVERIFIED**；**不得声称 29.5 s 链已实测**。

### 4.4 既有套件（E-12，PARTIAL）

- 26 套件本窗 + x03/isolation 复用 = 28 套件 / **236 例**；红 26（backup 8 + c4 8 + rowcounts 2 + isolation 8）；通过 **210** = 177（24 绿套件）+ 33（红套件内通过：backup 26 + isolation 7）。
- `m7071` 首跑失败 = runner FQCN 写错（仓内类名带 `Identity`），改类名后 `OK (3 tests)`。
- backup/c4/rowcounts 红因：`68 旧包恢复后必须由 Room 迁移到当前版本 expected:<71> but was:<72>`、`快照 schema=72，裁剪器只支持 schema=71` → 包内 `StudyDatabase=72` vs `SmokSelectionPlan=71` 不同快照 → **B+D**。
- matrix 15 例 `Time: 3959.641`（66 min），未 force-stop、未字母序 tail。
- **全部运行在 E-14 判作废的窗 W3**（01:27:44–02:57:24）→ 24 绿只作运行观测，**不得作为不回退的最终证明**。

### 4.5 崩溃签名（聚合）

`all-suite-crash-signatures.txt`（本会话实读）：`FATAL EXCEPTION=0`、`ANR in=0`、`NoSuchMethodError=0`、`NoClassDefFoundError=0`、**`UnsatisfiedLinkError=1`**、`SIGSEGV=0`、`CANNOT LINK=0`、`failed to map segment=0`、**`death was not confirmed=5`**。

- `UnsatisfiedLinkError=1` 来自 KD-76 窗的 `NativeCrashHandlerImpl: dlopen failed: library "libnative_crash_handler_jni.so" not found`（Play Services/测试基础设施告警，非 QuickJS/产品库），E-13 §7/E-12 §5 已披露。
- `death was not confirmed=5` = KD-76 窗 **4** 条 + p10 窗 **1** 条（本会话逐文件计数）；E-13 §7 仍写"**3 条**"，与实测 4 不符（§5-F11）。
- 因此"9 类签名全 0"只在"26 套件窗"内成立（E-12 §5 ULE=0）；聚合口径不是全 0。

---

## 5. 复核发现与处置（26 条）

> 来源：第 2 轮复核（工单）。`状态` 列保留复核轨标注；`本轮记录员复核` 列标出记录员在**本会话**实跑/实读过的项（≠ 复核轨的独立复核）。`处置` 列区分"已修（证据/用例/台账已改）"与"未修/待裁"。

| # | 级别 | 位置 | 问题 | 处置 | 本轮复核 |
|---|---|---|---|---|---|
| F1 | 高 | `VisualJsConnectStageCalibrationInstrumentedTest.kt` G2；`step13:68/:75`；`kd76-timing-calibration.md:132` | G2 门被从主口径 `M=ready−bind` 缩成 S-warmup 单段，n=9 仍打 PASS | **已修**：门改 M；报告加 `NOT EVALUABLE` 守卫；E-13 §4.1 判主口径 FAIL | 复算 M p95=9813 同值 |
| F2 | 高 | `known-defects.md:2923`；`admission-checklist:244` | 台账与设备证据互斥（"X-03 未复跑/校准 0 引用/isolation 留空" vs 已 PASS/已跑/已红） | **已修**：KD-76 2026-11-02 块 + 清单 K.6 逐项更正，原文保留 | 实读两处 |
| F3 | 高 | `StudyDatabase.kt:140`；`72.json`；`schema71-gate.log` | 计划锁 schema 71，现树 72；`S=71` 门只校验 71.json，是"真绿假验" | **登记阻塞**：重建前须裁定 71/72 | 实读 140=72；gate PASS |
| F4 | 中 | T1 §8.1（S=72 分支） | 集成轨称 S=72 门 FAIL 的明细（90 vs 94 表等）未复跑 | **未复核**，按转述登记 | 未跑 |
| F5 | 中 | `step13:94`；`suite-isolation.log:7/:27` | S3 写"沿 hard+500 门通过"，但该用例本窗红在预热断言，门未执行 | **已修**：S3 改"名义值、门未取得" | 实读 log 行号 |
| F6 | 中 | 校准用例 `:252/:278`；T1 §5.1/§5.2 | 采样形状与 T1 不一致（先 `connectForTest` 再 execute；无 `workerUidForTest`；T1 的 `tail -1` 会取到 envelope 行） | **已登记**：E-13 §8 第 6/7/8 条；UID 已补、未上设备 | 实读 |
| F7 | 中 | `load-isolation-window.txt`；`isolation-settle-before.txt` | "低负载窗"表述不实（窗内峰 4.79；P1/P3 未按定义） | **已更正**：改称"沉降后窗" | 实读 4 个采样值 |
| F8 | 中 | T1 §3.3 第 3 条 | 红例 awk 用 `$3`（恒 0）→ 8 红时输出空 = 假绿 | **证据侧已修**（E-11 §5.5：`$2+0 < 0`）；**T1 文本未回改** | 实跑：`$3`→0 行、`$2<0`→8 行 |
| F9 | 中 | plan:16；`isolation-attribution.md:236-247`；`known-defects.md` | kill `≤6`（计划）vs A3（台账）冲突，台账单方面改判据 | **待主会话裁定**；本批两读都 FAIL | 实读 |
| F10 | 中 | `suite-p10-logcat.txt:666`；E-12 §5 | p10 有 1 条 `death was not confirmed`，严格按"9 类签名 0"不满足；判 PASS = 就地放宽 | **已披露，待裁** | 计数 1 |
| F11 | 低 | `step13:124`；`suite-kd76-calibration-logcat.txt` | E-13 写"3 条 fail-closed"，实测 **4 条**（19:05:41/19:07:47/19:08:03/19:09:21） | **未修**（仍为 3） | 逐行计数 4 |
| F12 | 低 | `step12:64`；`build-x03fix.log` | ① data 包写"构建于 01:04"，实为 00:54:43；② 计数"绿 177/红 26"漏计红套件内通过的 33 例 | ② 已修（236=26+210）；① **未修**（§3 仍写 01:04） | stat 00:54:43；读 §3 |
| F13 | 低 | X-03 测试（M）；校准用例（??） | 设备绿/数据依赖未入库源（APK sha 与 HEAD 脱钩） | **登记**：提交时须一并纳入 | git status 实读 |
| F14 | 高 | `control-baseline-engine.sha256`；`build-control.log` | 控制轮不可证伪：唯一落盘 sha 是**基线**；构建日志无 revert 记录；app sha 不等本身不构成证据 | **已撤回**"T4 排除" | 实读日志/sha |
| F15 | 高 | `step11:10/:95-107` | "控制更差 ⇒ T4 排除"不成立：两窗红集仅 7 例交集（控制新增 3 AIDL 用例、基线红例转绿） | **已撤回**；需逐方法配对 | 实读两窗名单 |
| F16 | 高 | `step11:20-21`；`step09:109` | 基线窗 APK 身份时点错配：`fe9452aa` 是 01:26 还原包，晚于 01:06 窗；实际是 `f97f2972`/`3c222fca` | **已更正**（E-11 §5.1） | 实读 install/stat |
| F17 | 高 | `known-defects.md:2923`；清单 `:751/:753-759` | 台账**反向虚报**（已交付报成未落、已跑判别推给下批） | **已更正**（F2） | 实读 |
| F18 | 中 | 校准用例 `:549-562` | 报告层在 n<20、G1 红时仍打 `G2 PASS` | **已修**：`NOT EVALUABLE` 守卫 | 实读代码段 |
| F19 | 中 | 校准用例 `:415-457/:149-162/:389-402` | 自证/恒真断言（预算镜像只断言自身常量；轮数由循环自证；total==四段和代数恒等）被当 A3/A7/A10 证据 | **登记，未修** | 实读 |
| F20 | 中 | 校准用例 `:40-44` vs `:302-305` | KDoc 称钉 generation 过滤，实际只传正确 generation，坏实现也能过 | **登记，未修** | 实读 |
| F21 | 中 | E-13 §1；`grep reboot` | 预注册执行条件（reboot 冷启动、两批独立）结构上不可满足，无 reboot 记录 | **已登记** | `grep reboot` 0 |
| F22 | 中 | `step12:9-10/:64`；E-14:50 | 24 绿套件在作废窗 W3，不能作不回退证明；计数不自洽（177+26≠236） | **已修**（窗作废注记 + 236=26+210） | 实读 |
| F23 | 中 | `step11:27-35/:93` | ① 标题称低负载（同 F7）；② D 归因引用窗外 02:21/01:57 构建；③ P1/P3 未按定义记录 | **已更正**（E-11 §2.2/§5.2/§5.3） | 实读 0.62→4.79 |
| F24 | 低 | 校准用例 `:650-664/:96-97` | A8 断言被降级为日志（loadavg 全 -1.0），KDoc 未同步；E-13 转录漏 4.32 一行 | **未修（KDoc）**；E-13 §8 已披露 | 实读 3341-3358 行 |
| F25 | 低 | `assertBatchMediansAreMergeable` | median==0 时静默跳过合并前置，等于弱化预注册 | **登记，未修** | 实读 |
| F26 | 低 | 校准用例 `:309-316`；引擎 `:1205-1209` | A11 断言的是 `abandonForTest()` 返回值代理，错误消息声称"death 未被确认" | **登记，未修**（措辞与判据强度） | 实读 |

**非缺陷复核（复核轨结论，转录；记录员本会话只重跑了其中 §4 已列的数字，余项未复跑）**：X-03 断言确在同空间（`assertNotEquals` + `deriveProgramId` 六字段复算，与 `RoomStudyDatabase.kt:2255` 同函数）；`OK (1 test)`、9 类签名 0、无 `@Ignore`/`Assume`。复核轨称 28 套件 `@Test` 合计 236 与底册逐文件相符；kill 11/13、红例 8 名、backup/c4/rowcounts 计数一致；W2 快照 before→after-restore diff=0；keep 三文件 git 干净；5 次构建 exit=0；`VisualJsClientEngine.kt` 当前 sha 与 `control-baseline` 一致、git status 空；热区 2026-11-02 00:00 后无源码 mtime 变化（仅 build 产物）。**记录员本会话实核**：`suite-x03.log` 的 `OK (1 test)`、两窗 `Failures` 8/10、kill 11/13、`suite-kd76-calibration.log` 的 `Failures: 1`、`src-snapshot-build.diff` 0 行 / `all` 172 行、`keep-gate.log` 与 `schema71-gate.log` 的 exit 0、`build-x03fix.log` mtime 00:54:43。

---

## 6. 门禁与设备证据

| 门 | 结果 | 来源 | 限定 |
|---|---|---|---|
| 单测门（第 1 轮） | `exit=0` | 工单给定 | 记录员未跑 Gradle |
| 强门（修复环后） | `exit=0` | 工单给定 | 同上 |
| 单测门（复跑） | **`exit=1`** | 工单给定；E-14 §4.8 | 24 红（`:core:data` 22 + `:core:database` 2），统一 `DatabaseContractViolationException`，归 **B（KB 契约/包未同步）**，非本批回归 |
| 强门（修复环后复跑） | `exit=0` | 工单给定 | — |
| keep 一致门 | `KEEP_GATE_EXIT=0` | `keep-gate.log`（本会话实读） | 脚本未在本会话重跑 |
| schema 71 门 | `RESULT=PASS` / `SCHEMA_GATE_EXIT=0` | `schema71-gate.log`（实读） | **只校验 71.json**；现树运行时 72 → 不是现树有效门 |
| 构建 | 5 次 `exit=0`（首建/X-03 修/控制/还原/KD-76 修） | `build-*-exit.txt` | 记录员未复跑 |
| 四包 sha | app `f97f2972…`（基线窗）/ `4c1508c1…`（控制）/ `fe9452aa…`（还原后）；appTest `3c222fca…` / `b4242b7d…`；data `f7e96147…` / `d1fc594d…`；db `9ca01139…` | `apk-sha256*.txt`（实读） | 同源不同字节，增量打包非字节可复现（E-09 §6 自承） |
| asset 门 | 71.json 存在 | E-09 | data APK 同时含 71/72.json |
| dex 符号门 | OK（E-09） | E-09 | 记录员未跑 `need()` |
| 源快照（W2） | `src-snapshot-build.diff` = **0** 行 | 本会话 `wc -l` | 构建窗稳定 |
| 源快照（W3） | `src-snapshot-all.diff` = **172** 行 / 29 路径 | 本会话 `wc -l` | 窗作废；本轨 1 + 他线 28 |
| 源快照（收口后→现树） | **35 路径**差异 | 本会话重算（§1.3） | X-03/isolation 绑 W2；E-12/E-13 仅以 03:10 快照近似 |

**设备轨总账**：28 套件 / 236 例执行；绿 24 套件 177 例；红 26 例；X-03 1 例 PASS；isolation 15 例 FAIL；KD-76 校准 1 例 FAIL。0 跳过。

---

## 7. 遗留 / UNVERIFIED / 待裁

1. **isolation 15/15 未达**：需他线静止、设备 loadavg ≤1.0 的长窗复跑；**T4 未被排除**，须按逐方法配对重做控制轮（两臂 androidTest sha 相同、构建身份可证）；若仍红，按 C 产品回归立案（候选面：`SERVICE_HARD_DEADLINE_MILLIS=1500` 与冷启动 attach 7.6 s 的叠加），**不得先改常量或断言**。
2. **KD-76 维持 open**：主口径已 FAIL，且执行形状不是 reboot 冷启动轮；需按 T1 形状重跑（`adb reboot` + 每次 `am instrument` + 唤醒屏幕 + 两批独立），n=20 合并；R3 广告门/加载态文案半边仍在。
3. **29.5 s 链**：S3 门未取得（isolation 红例 #1 失败在预热断言）、S4 无计时点；只能报 `Σ_partial`。
4. **backup/c4/rowcounts**：待他线 schema-72 落定、树静止后重建 `data-debug-androidTest.apk` 复跑；当前红不是本批回归。
5. **DB schema 71/72 未裁定**：现树 72、`72.json` 未跟踪、`S=71` 门假绿；恢复 schema 71 需 git 写（回退 `StudyDatabase.kt`），记录员不做。
6. **单测门 exit=1（B）**：KB 契约拒收 bundled pack 的占位域/无 license 来源 + 一处测试报告中间产物 `NoSuchFileException`（使 `core:database` XML 不完整）；改包/改规则=接受他线新行为，需 KB 线/主会话裁定。
7. **p10 签名放宽待裁**；**kill ≤6 vs A3 待裁**。
8. **KD-76 用例的自证断言/生成过滤/A8 KDoc/合并前置/A11 措辞**（F19/F20/F24/F25/F26）未修，登记在案。
9. **T1 判据文本的 awk 字段错**未回改（证据侧已修）。
10. **E-13 §7 的 3 vs 4、E-12 §3 的 01:04** 两处计数/时点小错未修。
11. **两研究阈值只由 1 个绿窗标定**（N=1、19 次 warm-up），跨窗未验证；控制轮 M2 的 7 处编辑是纸面推演，**本批已实际执行但无可证伪构建身份**（F14）。
12. **设备证据快照绑定**：X-03/isolation 绑 W2（00:45=01:27，diff=0）；E-12/E-13 落在作废窗 W3（29 路径变动），只能以 03:10 快照近似；现树 HEAD `70611e61` + 再漂 35 路径（§1.3）。
13. **未运行 `connectedAndroidTest`**；未做 git 写；未改热区；设备 5566/5584/5596 未触碰。
14. **T2 新增断言（UID 检查、中止报告、M 门）未上设备** —— UNVERIFIED。
15. **"13 个既有套件"** 仍无对应集合，按 28 套件执行。

---

## 8. 提交清单建议（记录员只读核对 `git status`；**提交由主会话执行**）

> 纪律：每块 ≤6 路径；排除热区、锁文件、`tmp/`、`build/` 产物；commit 前逐路径核对暂存区。以下状态为本会话 `git status --porcelain` 实读。

**块 A：两研究 + 收尾轮基线 + 本记录（4）**

| 路径 | 状态 |
|---|---|
| `docs/research/2026-11-01-isolation-attribution.md` | `??` |
| `docs/research/2026-11-01-kd76-stats.md` | `??` |
| `docs/research/2026-11-01-batch-39-tail-baseline.md` | `??` |
| `docs/research/2026-11-01-batch-39-tail-record.md` | `??`（本文件） |

**块 B：测试侧两件（2）**

| 路径 | 状态 | 说明 |
|---|---|---|
| `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsConnectStageCalibrationInstrumentedTest.kt` | `??` | T2 载体；940 行；KD-76 数据来源 |
| `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt` | ` M` | X-03 类型归一 + 两 id 空间断言（设备绿依赖此版） |

**块 C1：证据索引 + E-09…E-11（4）**

| 路径 | 状态 |
|---|---|
| `docs/research/evidence/2026-11-01-batch-39/00-index.md` | ` M` |
| `docs/research/evidence/2026-11-01-batch-39/step09-tail-rebuild-install-gates.md` | `??` |
| `docs/research/evidence/2026-11-01-batch-39/step10-tail-x03-rerun.md` | `??` |
| `docs/research/evidence/2026-11-01-batch-39/step11-tail-isolation-and-t4-control.md` | `??` |

**块 C2：E-12…E-14（3）**

| 路径 | 状态 |
|---|---|
| `docs/research/evidence/2026-11-01-batch-39/step12-tail-suites.md` | `??` |
| `docs/research/evidence/2026-11-01-batch-39/step13-tail-kd76-calibration.md` | `??` |
| `docs/research/evidence/2026-11-01-batch-39/step14-tail-snapshot-and-blockers.md` | `??` |

**块 D：台账/总路线（3）**

| 路径 | 状态 | 说明 |
|---|---|---|
| `docs/known-defects.md` | ` M` | 共享脏文件；本批仅追加 KD-76 块（§附录 A） |
| `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` | ` M` | 共享脏文件；本批仅追加 K.7 |
| `docs/REFACTOR-MASTER-LINE.md` | ` M`（本会话将由 clean 转 M） | 头部滚动一条 |

**不提交**：`build/device-track-batch39-tail/**`（raw-log，gitignored）、任何 `gradle.lockfile`、stray `nul`、`core/ui/Theme.kt`、他线 KB knowledge 资源（27 个 `core/data/src/main/resources/knowledge/*.json` 与 `core/database`/`core/model`/`core/domain` 的 KB 改动）、`72.json`。

---

## 9. 本记录的验证结果（本会话实跑/实读，写盘前执行）

```bash
# 基线与漂移
git rev-parse --short HEAD                        # 70611e61
git status --porcelain | wc -l                    # 552
git status --porcelain | awk '{print $1}' | sort | uniq -c   # 432 ?? / 120 M
grep -n STUDY_DATABASE_VERSION core/database/.../StudyDatabase.kt   # 140: = 72

# 收尾轮快照 vs 现树（同一 find|sort -z|xargs sha256sum）
diff build/device-track-batch39-tail/src-sha-after-all.txt /tmp/src-sha-now.txt | wc -l   # 134
# 唯一变化路径 35（27 knowledge json + 5 core/database + 1 core/model + 1 core/domain + 1 app androidTest）

# X-03 / isolation / KD-76
grep -n "OK (\|Tests run\|FAILURES" suite-x03.log                          # 20:OK (1 test)
grep -n "OK (\|Tests run\|FAILURES" suite-isolation.log                    # 657:FAILURES / 658:Tests run: 15, Failures: 8
grep -n "OK (\|Tests run\|FAILURES" suite-isolation-control.log            # 755/756: Failures: 10
grep -c 'killing :visual_js worker: watchdog hard deadline' suite-isolation*-logcat.txt   # 11 / 13
grep -n "OK (\|Tests run\|FAILURES" suite-kd76-calibration.log             # FAILURES / Failures: 1

# KD-76 主口径复算（nearest-rank ceil）
python -I -c "import math; vals=[(1362,209),(9229,584),(1641,1319),(1675,450),(1212,609),(1897,302),(1257,520),(1189,322),(937,537)]; M=sorted(a+b for a,b in vals); p=lambda x:x[math.ceil(.95*len(x))-1]; print(M,p(M))"
# → [1474,1511,1571,1777,1821,2125,2199,2960,9813] 9813

# E-04 两口径
python -I ...  # lin p50 686.0 / p95 724.95；nearest-rank p50 680 / p95 738

# 红例 awk 字段错误（本会话实证）
awk '/INSTRUMENTATION_STATUS_CODE:/{if ($3+0 != 0) print}' suite-isolation.log | wc -l   # 0（假绿）
awk '/INSTRUMENTATION_STATUS_CODE:/{if ($2+0 < 0) print}' suite-isolation.log | wc -l   # 8（正确）

# 崩溃签名聚合
cat all-suite-crash-signatures.txt   # UnsatisfiedLinkError=1 / death was not confirmed=5 / 其余 0
grep -cF 'death was not confirmed' suite-kd76-calibration-logcat.txt   # 4
grep -cF 'death was not confirmed' suite-p10-logcat.txt                # 1

# 门日志（只读，未重跑脚本）
cat keep-gate.log          # KEEP_GATE_EXIT=0
cat schema71-gate.log      # RESULT=PASS / SCHEMA_GATE_EXIT=0
wc -l src-snapshot-build.diff src-snapshot-all.diff   # 0 / 172
stat -c '%y' build-x03fix.log                         # 2026-11-02 00:54:43（E-12 §3 写 01:04）

# 文件规模
wc -l app/src/androidTest/.../VisualJsConnectStageCalibrationInstrumentedTest.kt   # 940
grep -c 'connectStageTimestampsForTest' <同文件>                                   # 6
```

- **未执行**：任何 `./gradlew`、`adb`、`connectedAndroidTest`；任何 `git add/commit/push/reset/checkout/stash`；任何产品代码改动。
- **本会话未复核**：F4（S=72 门明细）、T1 判据轨的逐命令可执行性、两研究的 GitHub 元数据（沿用 R1 §5.1 `gh api` 实查记录）、`need()` dex 门、asset 门解包。

---

## 附录 A：台账同步（本轮记录员写入）

1. **`docs/known-defects.md`**：KD-76 追加一条 Progress（2026-11-02 收尾轮记录员收口）——回链本记录；确认校准**已出数**（主口径 p95=9813 FAIL、n=9、非 reboot 形状）；登记设备数字只绑 03:10 快照、现树 35 路径漂移；`READY_TIMEOUT` 零改动；KD-76 **open 维持**。原 T3 块与 2026-11-02 复核块保留原文（不回改历史时点）。
2. **`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`**：附录 K 追加 K.7（收尾轮记录回链 + X-03 设备 PASS 的关闭裁定归主会话 + isolation 两窗 FAIL/T4 未排除 + 快照漂移 35 路径），K.1–K.6 保留。
3. **`docs/REFACTOR-MASTER-LINE.md`**：头部滚动一条"最近计划维护 = 批次 39 收尾轮"（X-03 PASS / isolation FAIL+T4 未排除 / KD-76 主口径 FAIL / backup 三套件 B+D / schema 71→72 待裁 / 快照漂移），原批次 39 行降为"上次维护"。

> 三处同步均只追加/滚动头部，不动他线既有行；`known-defects.md` 与准入清单为共享脏文件（开工已 M），按纪律整文件纳入并注明。

## 10. 主会话收口与五条高危裁定（2026-11-02）

**背景**：本批设备轨结论远弱于其自述——复核 21 条（7 高）指出多处证据/台账问题。主会话逐条裁定：

1. **KD-76 主口径实为红（采纳）**：用 9 个有效样本按预注册主口径 M=逐轮(S-bind+S-warmup) 复算，sorted=[1474,1511,1571,1777,1821,2125,2199,2960,9813]，**p95=9813ms > 5000ms 门**；G1 同时红（11 轮失败、n=9<20）。现树 G2 断言已用 mergedMain（复核所指"只对 S-warmup 施门"是旧版本），但报告层打印的 "S-warmup p95=1319 PASS" 仍会被误读为门通过——**KD-76 保持 open，校准判"未通过"**，不得引用 1319 为头部数字。
2. **schema 71→72 漂移（已修）**：他线 knowledge_search_material_feature 迁移把 `STUDY_DATABASE_VERSION` 推到 72，而备份清单仍 71 → data 设备套件 backup/c4/rowcounts 红（`expected 71 but was 72`）——**KD-80 同类回归第三次**。主会话已将 `SmbkSelectionPlan` 同步到 72（版本/hash `aa5e8e8e…`/91 表/83 裁剪/3 派生/CLOSURE 3→4 + prefix v4），§9.11 机器块按代码重建、goldens 重算；`SmbkSelectionPlanTest` 清理陈旧测试编译产物后 **0 失败**。T1 判据的 S=71 门（真绿假验）作废，改按 S=72。
3. **T4 控制轮不可证伪（采纳）**：落盘的控制包 sha 记录的是**基线**引擎 sha（f0786242…），build-control.log 无回退标记；两窗红例集合仅 7 例交集（基线 8/控制 10），"控制更差⇒T4 排除"不是可控对照；基线窗装的是 00:55 的 f97f2972 而证据绑到 01:26 的 fe9452aa。**裁定：T4 因果未证伪也未证实，isolation 未收敛，D 归因不成立为结论**；批次 40 需在"他线静止+程序化低负载门（P1-P4）+逐方法配对"下重跑，控制轮须以字节可核的回退构建重做。
4. **台账反向虚报（已修）**：T3 块与清单写的"X-03 未复跑/校准用例 0 引用/isolation 结果留空"与同批证据矛盾——已在下文更正。
5. **KB 线红（他线归因）**：单测门 11 模块中 `:core:data` 红 2 例（`ProductionLexicalLegExportTest` 101→100、`Stage2LexicalScoresExportTest` MRR 0.6178→0.6130），系 KB 线 v23/v26 内容更新后的期望漂移，**非本线回归、不代改其金样**（评测禁止面向结果）；台账照录。

**本批可计入的交付**：X-03 A 类夹具修复+设备 `OK (1 test)`；KD-76 校准用例落码（G1 红、主口径红=有效负结果）；两研究线落盘；schema 72 备份清单同步（主会话）。**不成立的自述**：isolation 15/15 未达成、T4 排除、KD-76 校准通过。
