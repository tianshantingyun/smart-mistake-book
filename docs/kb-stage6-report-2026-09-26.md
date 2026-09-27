# Stage-6 稠密性能「现实口径」重测 · 最终报告（2026-09-26）

> **性质**：Stage-6 全阶段证据留档（三条方法学修正 → 宿主矩阵 → 真机判定或阻断记账 → 落地/未落地清单与门的新旧值 →
> 小档右尺寸的独立收益 → 冻结链收口 → UNVERIFIED 与遗留）。
>
> **一句话结论**：**未换档**。窗口 512→128 与线程 `min(4, 核数)` 两项右尺寸**已落地到口径层**（`dense_asset` 常量、
> 端侧 Kotlin 常量、三个宿主脚本坐标、tokenizer fixture），**随包侧一件未动**；base 档（bge-base-zh-v1.5）质量关沿用
> Stage-5 的 **0.7889 ≥ 0.7444**（已过），但延迟判据 ②「端侧 4 线程 + 右尺寸窗口下 ≤400ms 且 ≤3× 小档同口径」
> **在宿主代理口径下即不达**（base/小档 = **6.90×** > 3×），**真机阶段被共享工作树阻断未跑**（UNVERIFIED，外部阻塞）
> ⇒ 按判据 **不落地**，base 档产物全部留在 `build/`（scratch，不入库）。
>
> **本阶段唯一改到随包侧字节的动作**是**收口**（§4.4）：把被 Stage-5 base 导出覆盖成 768 维的
> `build/dense-model/int8-{docs,queries}.npy` **恢复成随包小档的产物**（逐字节复现原 sha），并让 androidTest fixture
> 与实际文件**逐字节自洽**——这一步不改随包 `.tflite` / `.vec` / 清单镜像的任何一个字节。
>
> **判据与冻结件一字未改**：质量线 `≥0.7444`、金标集 `7c004b76…`、融合参数（α=0.5 / 域内 min-max / RRF k=60）、
> 预注册线 0.75/0.60 全部沿用；本阶段只按任务书要求**右尺寸两处坐标**（窗口 512→128、端侧线程 2→`min(4,核数)`），
> 并把延迟线从 Stage-5 的绝对值「≤213ms」改成本阶段的「**≤400ms 且 ≤3× 小档同口径**」——新旧值并列在 §4.3。

**日期口径**：本报告记录 2026-09-26 起的 Stage-6 工作（窗口/线程/矩阵由 WP1/WP2 完成，收口与提交在 2026-09-27/28 凌晨）。
**「本轮实测」= 本会话真跑过的命令与读数**；引用他人/前阶段读数一律标注来源，不冒充本轮实测。

---

## 0. 结局一张表（先给判定，再给证据）

| 判据 / 动作 | 写死的值（未改） | 实测 | 结论 |
|---|---|---|---|
| **①质量 ≥ 0.7444**（金标融合主集，`7c004b76…`） | 本阶段判据 ① | base 档 **0.7889（71/90）**、现役小档 **0.7444（67/90）**（**均为 Stage-5/Stage-3 记录**，本阶段未重量） | **过**（base 相对现役净增 4 命中、0 丢失） |
| **②延迟**：端侧 4 线程 + 右尺寸窗口下单条编码 **≤400ms 且 ≤3× 小档同口径** | 本阶段判据 ②（取代 Stage-5 的「≤213ms」绝对值线，见 §4.3） | **真机数不存在**（外部阻断，§3）；宿主同口径代理：base keepint8 win128 @4 = **4075.5ms**、小档 **590.4ms** ⇒ **6.90×** | **不达**（宿主代理口径下「≤3×」即不满足）⇒ **未落地** |
| **③内存不设门**（只报告） | 本阶段判据 ③ | 随包小档 62,396,488 B；小档 keepint8 win128 23,893,696 B；base keepint8 win128 132,078,768 B；base fbdir win128 357,403,856 B（§2） | **只报告，无判定** |
| 落地（件进随包 assets / 资源 / 常量） | — | `git diff HEAD` 里随包 `.tflite` / `.vec` / 旁车 / 清单镜像 = **0 行** | **未落地** |
| 回退清单 | — | 从未覆盖随包件 ⇒ 无需回退 | **0 个文件** |
| 冻结链收口（本阶段新增，§4.4） | 参考向量 npy / fixture json / 随包资产与常量 三处逐字节自洽 | 三处 sha 对照表 **11 项全绿**（§4.4） | **完成** |

**标签与事实的关系**：结局标签 = **未换档**（`not_switched`）。两条事实并列、互不顶替：
①按延迟判据（宿主代理口径）**不达** ⇒ 不落地；②真机阶段（WP3）**被共享工作树阻断未跑** ⇒ 判据 ② 的真机判定记
**UNVERIFIED（外部阻塞，非本轮失败）**。

---

## 1. 三条方法学修正（本阶段定稿）

### 1.1 修正一：宿主探针**必须显式设线程**——"不设"是 1 线程，不是"按硬件并发"

**修正前**：三个宿主探针（`convert_onnx_to_tflite.py` 的自检/寻检、`check_tflite_parity.py`、
`build/latency_probe.py`）各自"不设线程"，而"不设"的语义只存在于绑定实现里——本仓库用的
`ai_edge_litert` Python 绑定把 `num_threads=None` 折成 `int(num_threads or 1)`，即**不设 ≡ 1 线程**
（`ai_edge_litert/interpreter.py:497` 与 `:520`；WP2 读源码复核，见 `tools/dense_build/README.md` §9.6）。
后果：**跨阶段读数会被当成同一口径比较**——Stage-5 记的 base 档 16,843ms / 小档 2,545ms 都是"1 线程"口径，
而 Stage-6 判据的端侧坐标是 4 线程；不写清楚，"宿主比值外推真机"这条链上就混着两个线程坐标。

**修正后**：三个探针的 `--threads` 默认值统一到**一处来源** `dense_asset.DEFAULT_INTERPRETER_THREADS = 4`
（= 判据的端侧坐标 = 本机 32 核下 `min(4, 核数)` 的解析值）；复现 Stage-5/6 历史读数显式写 `--threads 1`。
端侧同样右尺寸：`DenseQueryEncoder.kt:23~46` 新增纯函数 `resolveEncoderThreads(核数) = 核数.coerceIn(1, 4)`，
`DEFAULT_THREADS` 从 `const val 2` 改为 `resolveEncoderThreads(Runtime.getRuntime().availableProcessors())`
（`DenseQueryEncoder.kt:182~189`），并在唯一生产装配 `DenseRecallAssembly.kt:120~128` **显式传同一值**。

**"生效"怎么证**（不是读代码推断）：对三个探针分别传非法值 `--threads 0`，绑定在 `Interpreter(...)` 构造处
直接抛 `ValueError: num_threads should >= 1`（四处都实测到，`README.md` §9.6）——只有该值真的进了
`Interpreter(num_threads=…)` 才可能抛。**宿主线程列不可解析**的边界同时写明：本机 `ai_edge_litert` 把这张图
基本串行执行（1/2/4/8 四列差 ≤5% 全是噪声，venv 无 XNNPACK 动态库）⇒ **§2 的线程列不能用来判断"4 线程比 2 线程快多少"**，
端侧线程收益只能真机量（本轮没量）。

### 1.2 修正二：窗口**必须右尺寸**——512 是白算（4~6×），不是"更稳"

**修正前**：两档一律 PAD 到 512。**实测需求**（本会话复算冻结 fixture，
`python build/stage6_tokstats.py`，读数 `build/stage6/wp5-tokstats.log`）：

| 输入 | n | p50 | p90 | p95 | max |
|---|---|---|---|---|---|
| 金标查询（含 BGE 查询前缀） | 90 | 55 | 66 | **70.5** | **81** |
| 语料 surface（fixture 抽样 200 条） | 200 | 14 | 19 | **21** | **26** |
| edge（截断探针，设计内） | 18 | 16.5 | 128 | 128 | 128 |

⇒ 窗口 512 时 query 位的真 token 占比 **10.8%**（89% 白算）；窗口 **128 = 81 × 1.6 余量**（2 的幂）后升到 **43.2%**。
窗口值同时是三个口径（离线分词截断 / 冻结转换的定长 / 端侧 `DENSE_MAX_SEQUENCE_LENGTH`），已是**一处来源**
（`dense_asset.MODEL_SHARED["maxLen"]`）。

**缩窗口是"数值同一性"不是"近似"**（本会话独立复算，`python build/stage6_npy_diag.py`，读数
`build/stage6/wp5-npy-diag.log`）：同一份小档 int8 ONNX、同一批 290 条 fixture 行、掩码在位，
只把"右 PAD 到 128"与"右 PAD 到 512"逐条比 ⇒ **290/290 行逐字节相同**
（`1-cos max` 报 1.192e-07 是 cosine 计算本身的 float 噪声；逐字节判定为真）。
**设计内例外**单独列：6 条截断探针（`e_long_cjk_509/510/511/512/513/2000`）在 512 窗口下 ids 长 512、128 窗口下 128，
其向量本来就该不同（1-cos ≈ 0.06–0.08），不是数值抖动，且它们**不在金标查询里**（kind=`edge`）。

**收益**：宿主 512→128 的 p50 比 **4.16–4.48×**（§2，与 512/128=4 的白算比例同量级）。

### 1.3 修正三：**内存不设门**（只报告）

本阶段的判据里**没有**内存项：质量（①）与延迟（②）是判定项，内存只作为事实报告（模型件与资产的字节数）。
理由（写死的取舍）：换大档的直接代价（体积 2~2.7×）**不构成判据**——它由"可发货组合"的选择权交给用户
（§5 的单独提交路径），不由门替用户否决；反过来，若把内存设成门，这个阶段的"未换档"就会被记成"被内存否决"，
与本轮的真实否决理由（延迟 + 未测真机）不符。报告的口径是**字节数 + 是否随包**，不是"通过/不通过"。

---

## 2. 宿主矩阵（窗口 × 线程 × 档位 × 路线）

**口径**：`build/stage6_host_matrix.py`（探针函数与 `build/latency_probe.py` 共用一份实现）→ 读数
`build/stage6/host-matrix.json`；逐条查询、右 PAD 到窗口、掩码、`token_type_ids` 全 0、n=12、预热 1 条不计时。
**这是宿主代理，不是真机数**（同一台机器、同一套 LiteRT Python 绑定）；跨档比较只看**比值**。

| 档 / 路线 | 窗口 | 体积 | 1 线程 | 2 | 4 | 8 | 512→128（同线程） |
|---|---|---|---|---|---|---|---|
| 小档 / `flatbuffer_direct` | 512 | 62,396,488 B | 2535.9 | 2540.7 | 2522.5 | 2540.9 | 4.42–4.48× |
| 小档 / `flatbuffer_direct` | 128 | 61,608,136 B | **566.1** | 569.4 | **570.8** | 566.7 | — |
| 小档 / `keepint8` | 512 | 24,092,224 B | 2554.1 | 2552.8 | 2551.6 | 2570.5 | 4.16–4.32× |
| 小档 / `keepint8` | 128 | 23,893,696 B | 614.1 | 591.7 | 590.4 | 597.1 | — |
| base / `flatbuffer_direct` | 512 | 358,585,424 B | 16868.2 | 16886.7 | 17167.0 | 17439.7 | 4.30–4.45× |
| base / `flatbuffer_direct` | 128 | 357,403,856 B | 3926.1 | 3947.0 | **3920.3** | 3920.7 | — |
| base / `keepint8` | 512 | 132,376,600 B | 16946.4 | 16925.8 | 17024.0 | 17005.2 | 4.07–4.18× |
| base / `keepint8` | 128 | 132,078,768 B | 4108.4 | 4159.9 | **4075.5** | 4096.3 | — |

**三条读数**（p50，单位 ms）：

1. **窗口是有效杠杆，且对两档两条路线一致**：512→128 的比 **4.16–4.48×**。历史读数被复现：
   小档 `flatbuffer_direct`@512 = 2522–2541ms（旧记 2,545/2,570）、base `keepint8`@512 = 16,926–17,024ms
   （旧记 16,843/17,437）。
2. **线程在宿主上不可解析**（≠"4 线程没用"）：1/2/4/8 四列差 ≤5%，全是噪声（§1.1 的机制说明）。
   WP2 用同一坐标独立复量 {2,4,8}（`build/stage6/wp2-thread-matrix.json`）：小档 609/614/602、
   base 4233/4223/4504，base/small 比 6.95/6.88/7.49× ⇒ 结论复现，不是单次偶然。
3. **base/small 比值不随窗口变**：512 下 6.6–6.9×、128 下 6.86–7.03×（两档同比例受益）⇒
   **窗口右尺寸改不了"模型大 6.75 倍"这件事**——这正是判据 ② 的「≤3× 小档」不达的机制。
4. 4 线程 + 窗口 128 下**全场最快**是小档 `flatbuffer_direct`（570.8ms）；base 档两条路线**打平**
   （3920.3 vs 4075.5，差 3.8%，在噪声量级内），但体积 340.8 MiB vs 126.0 MiB ⇒ 路线选择由体积决定
   ⇒ 若将来落地 base，可发货组合是 **base / `keepint8` / 窗口 128**（对拍 min 0.999778）。

**新窗口四件（含 512 列补件）的宿主对拍（硬门 ≥0.999，全量 290 条，`check_tflite_parity.py`）**：

| 件 | 体积 | tflite vs int8 ONNX（n=290） |
|---|---|---|
| 小档 keepint8 win128 | 23,893,696 B | min **0.999587** / 中位 0.999783 |
| 小档 `flatbuffer_direct` win128 | 61,608,136 B | min **0.999587** / 中位 0.999783 |
| base keepint8 win128 | 132,078,768 B | min **0.999778** / 中位 0.999897 |
| base `flatbuffer_direct` win128 | 357,403,856 B | min **0.999778** / 中位 0.999897 |
| **随包现件**（小档 `flatbuffer_direct` win512） | 62,396,488 B | min **0.999587** / 中位 0.999783（**本轮补测**，`wp5-parity-packaged-win512.log`） |

两条路线的数**逐位相同**（`keepint8` 只把 int8 权重留在 flatbuffer 里，算式与 int8 ONNX 同一条），
源件哈希两次断言未变（`bge-small…int8.onnx` `4d3b3135…` / `bge-base…int8.onnx` `1994768d…`）。

---

## 3. 真机矩阵与判据 ② 的判定（外部阻断的如实记账）

**真机数：不存在。** 真机阶段（原 WP3）**未执行**——阻断原因是**共享工作树**：另一条会话在 `core/database`
的在飞重构让 Kotlin 侧编译/KSP 过不去（本轮复核到的签名与 Stage-5 记的同族：
`[MissingType]`、`TUTOR_CONVERSATION_AREA_MIGRATION_51_52` 常量无定义），因此
`connectedDebugAndroidTest`（`DenseEncoderParityInstrumentedTest` / `DenseFirstUseCostInstrumentedTest`）
**本轮无法运行**。按纪律我们**一律没碰** `core/database` 下那批脏文件（清单见 `.wf-manifest-stage6.txt` 的
"未触碰"段）。⇒ 判据 ② 的**真机判定 = UNVERIFIED（外部阻塞，非本轮失败）**。

**能给的是宿主同口径代理**（逐条 + 右 PAD 128 + 掩码 + `token_type_ids` 全 0、4 线程、n=12，§2）：

| 口径 | 小档 keepint8 win128 @4 | base keepint8 win128 @4 | 比值 |
|---|---|---|---|
| 宿主代理（本轮实测） | **590.4 ms** | **4075.5 ms** | **6.90×** |

- 判据 ② 的**后半句（≤3× 小档同口径）**：**宿主代理口径下即不达**（6.90× > 3×）⇒ 即使真机数将来拿到，
  "base 档不落地"这条结论也不会被推翻（判据写成"且"，两条必须同时满足）。
- 判据 ② 的**前半句（≤400ms）**：宿主绝对值**不能**当真机判据用。作为量级参考：按小档历史
  「宿主 2,545ms → 真机 p50 71ms（≈36×，Stage-3 真机、**512 窗口 + 2 线程**口径）」外推，
  base@128 真机 ≈ **113ms**（**外推，不是实测**）；这个数看起来有余量，但它**不改变**后半句的结论。
- 端侧线程 {2,4,8} 与窗口 {512,128} 的**真机差值**：**本轮没量**（判据 ② 的判定要在设备上做）。

**判据 ② 的判定（按纪律只能这样记）**：**未换档**——否决理由 = ①「≤3× 小档」在宿主代理口径下不达
（有实测支撑）；②真机阶段被外部阻断（UNVERIFIED）。

---

## 4. 落地 / 未落地清单与门的新旧值

### 4.1 落地（**口径层**，未随包）——本阶段入库的改动

| 落点 | 改动 | 证据 |
|---|---|---|
| `tools/dense_build/dense_asset.py` | `MODEL_SHARED["maxLen"]` 512 → **128**（= 离线截断 / 冻结定长 / 端侧常量三口径的一处来源）；新增 `DEFAULT_INTERPRETER_THREADS = 4` | `dense_asset.py:96`、`:229` 段注释 |
| `…/dense/DenseTokenizer.kt` | `DENSE_MAX_SEQUENCE_LENGTH` 512 → **128** | `DenseTokenizer.kt:237` |
| `…/dense/DenseQueryEncoder.kt` | `DEFAULT_THREADS`：`const val 2` → `resolveEncoderThreads(核数) = min(4, 核数)`；`open/openFromAssets` 线程参数化 | `DenseQueryEncoder.kt:23~46`、`:182~189`、`:198~203` |
| `…/dense/DenseRecallAssembly.kt` | 生产装配显式传 `threads`（口径单源、装配点可见） | `DenseRecallAssembly.kt:120~128` |
| `tools/dense_build/convert_onnx_to_tflite.py` | 新增 `--threads` / `--work-tag`（同一档多产物并存） | `README.md` §9.5 |
| `tools/dense_build/check_tflite_parity.py` | 新增 `--threads` / `--max-len` / `--frozen-queries`（跨档 npy 显式指路 + 维度闸） | `README.md` §9.5 |
| `core/data/src/test/resources/dense/tokenizer-parity-{cases.txt,stages.txt,summary.json}` | 6 条截断探针按 128 重生成（333 条：query 90 / surface 200 / edge 18 / stage 25，条数不变；两档 tokenizer 逐条同 id） | `build/stage6_fixture_crosscheck.py` |
| `core/data/src/test/kotlin/…/dense/DenseEncoderThreadsTest.kt` | 新增：上限 / 跟随核数 / 异常核数 / 默认=解析值 四条 JVM 门 | 本轮实跑 4/4 通过（§6.3） |
| `core/data/src/androidTest/…/DenseFirstUseCostInstrumentedTest.kt` | 端侧首用耗时探针按新口径（线程/窗口）更新（**未跑**，见 §6） | 清单 `.wf-manifest-stage6.txt` |
| `build/tflite-work/` 6 件 | 新窗口 6 件 tflite（小档 ×2 路线 + base ×2 路线 + 512 列补件）**全部在 `build/`，不入库** | §2 表 |

### 4.2 未落地（**随包侧一件未动**）

| 随包件 | 当前字节 | 说明 |
|---|---|---|
| `core/data/src/main/assets/dense/bge-small-zh-v1.5-int8.tflite` | 62,396,488 B / `015b231580dd850e…` | 仍是 **Stage-3 的 512 窗口件**；与矩阵里 `small / flatbuffer_direct / win512` 那一格**逐字节相同**（sha `015b2315…`） |
| `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec`（+ 旁车） | 17,167,873 B / `cdf93650b948a091…` | 未动（**窗口变化对向量是位同** ⇒ 本不需要重生成） |
| `tools/dense_build/model-manifest.json` 顶层镜像 | `activeModel=bge-small-zh-v1.5`、`maxLen: 512` | 未动：顶层镜像 = **随包那一档**的声明，描述的是**随包字节**（512 窗口件），不是端侧口径；`--publish` 只在换件动作里用 |
| base 档全部产物 | 见 `build/tflite-work/`、`build/dense-model/bge-base-*` | 未入库 |

### 4.3 门的新旧值（**只动本阶段明确要求的两处坐标**）

| 项 | 旧值 | 新值 | 依据 |
|---|---|---|---|
| 质量线（金标主集） | ≥ 0.7444 | **≥ 0.7444（未动）** | 判据冻结；金标集 `7c004b76…` 未动、融合参数（α=0.5 / min-max / RRF k=60）未动 |
| 延迟线 | 真机单条编码 p50 ≤ **213ms**（= 现役 71ms × 3，**2 线程 + 512 窗口**口径） | 端侧 **4 线程 + 右尺寸窗口（128）**下 ≤ **400ms** 且 **≤ 3× 小档同口径** | 本阶段明确要求的**门重定标**（口径变了，绝对值线必须跟着变；同时补上"不许靠换口径偷偷放宽相对线"的分母项） |
| 内存 | 无 | **无（只报告）** | 本阶段明确：内存不设门 |
| 端侧线程 | 硬编码 **2**（生产不传） | `min(4, 核数)`（下限 1），装配点显式传 | §1.1 |
| 序列窗口 | **512** | **128** | §1.2 |
| 预注册线 | 0.75 / 0.60 | **未动**（本阶段不触） | 任务书冻结 |

### 4.4 冻结链收口（WP5）：参考向量三处逐字节自洽 + sha 对照表

**发现的污染**：`core/data/src/androidTest/assets/dense/encoder-parity.json` 记的参考向量 sha
（`fac31f0c…` / `e8d6e0f5…`，小档）与 `build/dense-model/int8-{docs,queries}.npy` 的**实际** sha
（`cbbc989e…` / `e527608c…`，**768 维 base 档**）不一致——Stage-5 的 base 导出把这两个**两档共用文件名**的
ndarray 覆盖了，而**没有任何门会红**（仪表化测试只比 cosine，不校验 npy 的维度/sha；
`check_tflite_parity.py` 的维度闸只拦"拿 npy 来对拍"的路径，不拦 fixture 的溯源字段）。
本阶段结局是**未换档**（随包仍是小档）⇒ 按"一次导出只有一个有效档"的约定，把 npy 恢复成**随包小档**。

**恢复是怎么做的**（`build/stage6_restore_small_npy.py`，逐行镜像官方导出链 `export_bge_int8.py` 里产 npy 的
那几段：`padding=True / truncation=True / max_length=maxLen` 的 BATCH_SIZE=64 分词、ORT 跑 int8 ONNX、
CLS+L2 的 torch 参考；**不重导出 ONNX、不重量化、不写清单**），并且在脚本里钉死"小档 int8 ONNX 的 sha
必须是 `4d3b3135…`，否则拒绝重算"。**结果：逐字节复现原 sha**（并且 int8-vs-torch 的两条读数
`0.9990096092224121` / `0.9991949796676636` 与清单里记的小档值**逐位相同** ⇒ 这不是"另算一份能过门的"）。

| # | 处 | 污染时 | 收口后（本轮实测） | 与 fixture 记录的关系 |
|---|---|---|---|---|
| 1 | `build/dense-model/int8-docs.npy` | `e527608c444d5bf8…`（28,931×**768**，base） | **`e8d6e0f5630bf1f1527aee5ce4303a7454170b6630d945af126b051e6d694a8c`**（28,931×512） | = json 的 `referenceVectors.docs.sha256` ✅ |
| 2 | `build/dense-model/int8-queries.npy` | `cbbc989e61db007a…`（90×**768**，base） | **`fac31f0c29c14a7eca8018840d0c388e84a4d29b834af7eb53111c23aaeb54fd`**（90×512） | = json 的 `referenceVectors.queries.sha256` ✅ |
| 3 | `build/dense-model/fp32-docs.npy` | `0730c041c658e29b…`（768） | `8a364c05557836aca7b87e64fa922de32190c9947640a6be77b2d8b8305743d4`（512）＝**与 Stage-2 离线臂 `bge-docs.npy` 逐字节相同** | 旁证：torch 参考臂可复现 |
| 4 | `build/dense-model/fp32-queries.npy` | `3368f5c09d2c66f0…`（768） | `3f84b78e6f353887cb0454fac43730829940d5712e3e30884aea1cad7dd25c7f`（512）＝**与离线臂 `bge-queries.npy` 逐字节相同** | 旁证 |
| 5 | `…/androidTest/assets/dense/encoder-parity.json` → `referenceVectors.{queries,docs}.sha256` | `fac31f0c…` / `e8d6e0f5…`（**文件当时不是这两份**） | **未改**（值本来就对，是文件被换） | 现在与 #1/#2 **逐字节自洽** ✅ |
| 6 | 同 json → `textSource.sha256` | `3e68c577ec1d647e…`（改窗口**前**的 tokenizer fixture） | **`5f7169cad6428eb49d2f738821df870d0da85c7fda71ee774685d5372cd6af8c`**（本轮唯一改动的字段） | = WP1 改窗口后 tokenizer fixture 的实际 sha ✅ |
| 7 | 同 json → `vectorsFile.sha256` / `casesFile.sha256` | `c4e82beb…` / `425d08d0…` | **未变**（重生成脚本给出**同一个 sha**） | 仪表化的 `vectorsFile` 封存断言仍成立 ✅ |
| 8 | 同 json → `alignmentSelfCheck.min` | `0.9999998807907104` | **未变**（重跑 = `0.999999881`，floor 0.9999） | 行对齐自检仍绿 ✅ |
| 9 | 随包 `…/assets/dense/bge-small-zh-v1.5-int8.tflite` | `015b231580dd850e…` / 62,396,488 B | **未动**（仍是随包小档 512 窗口件） | 与 json 的 `dim: 512`、`threshold: 0.999` 口径一致 |
| 10 | 随包 `.vec` + 旁车 | `cdf93650…` / 17,167,873 B | **未动**；`check_asset.py` **10/10 OK**（含 `modelManifest` 三方一致） | — |
| 11 | 常量三处 | — | `dense_asset.maxLen=128`、`DENSE_MAX_SEQUENCE_LENGTH=128`、端侧 `require(定长 ≥ 128)`（`DenseQueryEncoder.kt:164`） | 随包件**定长 512 ≥ 128** ⇒ 不触发拒绝；端侧按 128 截断 + 右 PAD 到 512 跑，掩码在位 ⇒ 与离线同结果（§1.2 的 290/290 逐字节相同） |

**"三处自洽"的口径**：`npy`（#1~#4）↔ `fixture json`（#5~#8）↔ `随包资产与常量`（#9~#11）三者互指同一档
（小档 / 512 维 / 128 口径），且**每一项都有本轮真跑出来的 sha/读数**（命令见 §7）。

**顺带的闭环复核**（本轮实跑，读数 `build/stage6/wp5-parity-packaged-win512.log`）：把**随包件**（512 窗口）与
**恢复后的同档 npy** 放进同一条对拍链——
① 对齐自证（批式 ONNX vs `int8-queries.npy`）min **0.999874241**（下限 0.9990）⇒ 恢复后的 npy 与 ONNX/ids 行对齐；
② 宿主对拍（随包件 vs int8 ONNX，全量 n=290）min **0.999587** / 中位 0.999783（门 0.999）；
③ 设备样（随包件逐条 vs **恢复后的** npy）min **0.999726**——Stage-6 WP1 那次同一条因为 npy 里是 base 档、
只能拿跨臂 `bge-queries.npy` 当参考，读数是 0.999426（`parity-small-128-keepint8.log`）；
两数并列说明：**参考链换回同档之后，随包件与它的偏差比"跨臂参考"更小**，fixture 的比对对象回到了同一档。

**保底**：被覆盖的 base 档四份 npy 在覆盖前**原样备份**到 `build/backup-stage6-wp5/base-polluted-*.npy`
（含 sha 记录见备份日志），要复算 base 档融合数从备份还原或重跑 base 导出即可。

---

## 5. 小档右尺寸的独立收益（窗口 128 + 4 线程）

小档的右尺寸**不依赖**"换不换档"这个决定：`DENSE_MAX_SEQUENCE_LENGTH` 与 `resolveEncoderThreads` 已进口径层，
而**随包件仍是 512 窗口的那一份** ⇒ 现在端侧跑的是"按 128 截断 + 右 PAD 到 512"（正确但白算 4 倍）。

| 项 | 512 窗口（随包现状） | 128 窗口（新件，`build/tflite-work/`） | 收益 |
|---|---|---|---|
| 宿主 p50 @4 线程（小档 keepint8） | 2551.6 ms | **590.4 ms** | **4.32×** |
| 宿主 p50 @4 线程（小档 `flatbuffer_direct`） | 2522.5 ms | **570.8 ms**（全场最快） | 4.42× |
| 体积（keepint8） | 24,092,224 B | **23,893,696 B** | −0.8% |
| 对拍 min（n=290 vs int8 ONNX，硬门 ≥0.999） | — | **0.999587** | 过 |
| 真机 p50 | Stage-3 真机 **71ms**（**2 线程 + 512 窗口**口径，非本阶段读数） | **不存在**（外部阻断未跑） | — |

**落地状态**：**未随包**（随包件仍是 512 窗口件）。可发货的小档新件已按 §2 备好（两条路线都过宿主对拍），
**待用户一句话批准后可单独提交**——提交动作包含（按 `core/data/src/main/assets/dense/README.md` 的换件纪律）：
① `convert_onnx_to_tflite.py --model bge-small-zh-v1.5 --seq-len 128 --work-tag … --install`
（把 128 窗口件拷进随包路径；路线建议 `flatbuffer_direct_keepint8`：22.8 MiB、对拍与 fbdir 逐位相同，
代价是宿主 p50 +3.4%（590.4 vs 570.8，噪声量级）——若按"与现随包件同一路线"则用 `flatbuffer_direct`，61.6 MiB）；
② 重跑**真机硬门** `DenseEncoderParityInstrumentedTest`（≥0.999）与 `DenseFirstUseCostInstrumentedTest`；
③ 更新 assets README 的验收表（sha/字节数/窗口）与（如需）清单顶层镜像的 `maxLen`。
**③ 与 ② 现在做不了**：真机阶段被共享工作树阻断（§3）。

---

## 6. UNVERIFIED 与遗留

1. **真机侧全部 UNVERIFIED（外部阻塞）**：判据 ② 的真机判定、`DenseEncoderParityInstrumentedTest`（真机硬门 ≥0.999）、
   `DenseFirstUseCostInstrumentedTest`（端侧首用耗时 + 新线程口径）**本轮都没跑**——共享工作树被另一条会话在
   `core/database` 的在飞重构阻断（`[MissingType]` / `TUTOR_CONVERSATION_AREA_MIGRATION_51_52` 无定义；
   同族签名见 `docs/kb-stage5-report-2026-09-25.md` 的登记）。**这不是本阶段的失败，也不当作通过**。
2. **端侧线程 {2,4,8} 与窗口的真机差值不存在**：宿主线程列不可解析（§1.1），所以"4 线程比 2 线程快多少"
   这条**没有读数**（端侧改动的合理性与上限依据是"宿主 1/2/4/8 差 ≤5% + 不跟 UI 抢核"，不是真机收益实测）。
2.1 **整模块单测（无过滤）本轮跑不了**（基础设施故障，非本阶段改动导致）：`./gradlew :core:data:testDebugUnitTest`
   在 `:core:database:kspDebugKotlin FAILED` 处失败——`ksp.com.intellij.util.io.CorruptedException: Storage corrupted
   …core\database\build\kspCaches\debug\symbolLookups\lookups.tab_i`（KSP 增量缓存损坏；重跑一次同样失败）。
   这是**另一会话的模块建/缓存目录**，按纪律不碰（没有删它的 `kspCaches`）。**跑得动的是映射到本次改动的
   那一段**：`./gradlew :core:data:testDebugUnitTest --tests "*Dense*"` = BUILD SUCCESSFUL，6 类 25 tests / 0 failures。
3. **宿主矩阵的线程列不能外推**：见上；可用的是比值（base/small ≈ 6.9×）。
4. **base 档质量沿用 Stage-5 的离线数**（0.7889）：本阶段**没有**重量金标融合（窗口右尺寸对质量的影响为
   **零**——query 侧 90 条全部 ≤81 token < 128，向量逐字节相同；6 条截断探针是 `edge` 用例、不在金标查询里）。
   若要"按新口径重量一遍金标"，需要先重跑 base 档导出（`build/dense-model/*.npy` 现在是**小档**；
   base 的四份在 `build/backup-stage6-wp5/`）。
5. **清单（`model-manifest.json`）的两处历史性不一致**（本轮**未改**，如实登记）：
   ①小档条目的 `outputs`/`tokenLengths` 指纹**缺失**（写它的是更早版本的脚本）；
   ②base 条目的 `outputs` 指纹现在描述"base 导出当时写入的文件"，而这两个文件名下的内容**已被收口恢复成小档**
   （§4.4；这是"一次只有一个有效档"的必然结果，不是错误）。
   顶层镜像 `maxLen: 512` **故意保留**——它描述的是随包字节。
6. **上游 scratch 的一处未解释差异（不影响本报告结论）**：WP1 的 `build/stage6/refvec-window-*.json` 记的重算向量
   与 fixture 冻结向量实测差 **1.75e-4**（本会话量的），且它报的 "1-cos 1e-12" 与它自己落盘的
   `refvec-*-win512.npy` / `win128.npy` **逐字节相同**的事实自相矛盾（本会话复核）。本报告**不引用**该读数，
   窗口同一性改用本会话的独立复算（**290/290 逐字节相同**，§1.2）。这一点留作 scratch 的遗留问题登记。
7. **内存**：按判据 ③ 只报告（§2 的体积列），**没有判定**，也没有端侧 RSS/PSS 读数（真机未跑）。

---

## 7. 复核方式（可重跑）

```bash
# ① 冻结链收口：参考向量恢复小档（dry-run 不加 --write；会先备份"污染前"的四份 npy）
python build/stage6_restore_small_npy.py --model bge-small-zh-v1.5 --write
#   → 期望：int8-docs sha=e8d6e0f5… / int8-queries sha=fac31f0c…；读数 int8VsFp32Docs=0.9990096092224121
#            fp32-docs/queries 与 build/stage2-dense-work/vectors/bge-{docs,queries}.npy 逐字节相同

# ② fixture 重生成（唯一改动的字段应是 textSource.sha256）+ 行对齐自检 + 封存 sha
python tools/dense_build/gen_device_parity_fixture.py --model bge-small-zh-v1.5
#   → 期望：行对齐自检 min=0.999999881；vectors sha256=c4e82beb…（与改前相同）

# ③ 随包资产陈旧性门（10 道）+ 三处自洽的"随包侧"复核
python tools/dense_build/check_asset.py
#   → 期望：10/10 OK（含 modelManifest 三方一致）

# ④ 本会话的窗口 A/B 与三形态对齐（290 条；本报告 §1.2 的数）
python build/stage6_npy_diag.py
#   → 期望：A/B@128/B@512/C@64 vs fixture 全面 min=0.999999881；窗口 A/B 逐字节相同的行=290/290

# ⑤ token 长度分布（§1.2 的表）
python build/stage6_tokstats.py

# ⑥ 宿主矩阵（§2；用的是 build/ 内 scratch 脚本，产物落 build/stage6/）
build/tflite-venv/Scripts/python.exe build/stage6_host_matrix.py

# ⑦ 单件宿主对拍（换 --tflite/--max-len 即可复核 §2 的四件与随包件）
build/tflite-venv/Scripts/python.exe tools/dense_build/check_tflite_parity.py \
  --model bge-small-zh-v1.5 --tflite core/data/src/main/assets/dense/bge-small-zh-v1.5-int8.tflite \
  --threads 4 --max-len 512

# ⑧ JVM 门（本会话实跑：BUILD SUCCESSFUL，6 类 25 tests / 0 failures）
./gradlew :core:data:testDebugUnitTest --tests "*Dense*"

# ⑨ 真机（**本轮未跑**，外部阻塞解除后才可跑；见 §6.1）
./gradlew :core:data:connectedDebugAndroidTest --tests "*DenseEncoderParityInstrumentedTest*"
./gradlew :core:data:connectedDebugAndroidTest --tests "*DenseFirstUseCostInstrumentedTest*"
```

**本轮实跑过的命令与结果**（本报告里"本轮实测"的出处）：
`build/stage6_restore_small_npy.py --write`（复现 sha 与两条门读数）、`gen_device_parity_fixture.py`（对齐自检）、
`check_asset.py`（10/10）、`build/stage6_npy_diag.py`（三形态 + 窗口 A/B）、`build/stage6_tokstats.py`、
`./gradlew :core:data:testDebugUnitTest --tests "*Dense*"`（25/25）、
`check_tflite_parity.py --tflite <随包件> --max-len 512`（§4.4 的随包件-对-恢复后 npy 一致性：
对齐自证 min 0.999874 / 宿主对拍 min 0.999587 / 设备样 min 0.999726，读数见
`build/stage6/wp5-parity-packaged-win512.log`）。
**没跑成的**：`./gradlew :core:data:testDebugUnitTest`（整模块，无过滤）——`core/database` 的 KSP 增量缓存损坏
（`CorruptedException`，§6.2.1），与本阶段改动无关。
