# `assets/dense/` —— 端侧稠密腿的模型件（**已就位**）

端侧检索在这里找 `bge-small-zh-v1.5-int8.tflite`（常量：`DenseRecallAssembly.MODEL_ASSET_PATH`）。
LiteRT（`org.tensorflow.lite.Interpreter`）**只吃 `.tflite`**，不吃 ONNX。

| 项 | 值 |
|---|---|
| 文件 | `bge-small-zh-v1.5-int8.tflite`（**随包分发**，不入 `build/`） |
| 体积 | 61,608,136 B（≈58.8 MiB；APK 内 stored，不压缩）——**2026-09-28 换件后**；换件前（512 窗口件）为 62,396,488 B |
| sha256 | `056d4262b59f93b2f9869f43c6c6d39b26371af4f77f427ea96b74b3b0951096`——换件前为 `015b231580dd850e…`（回退件见文末"回退"） |
| **序列窗口** | **定长 128**（`input_ids`/`attention_mask`/`token_type_ids` 均 `[1,128]` int64），= 端侧 `DENSE_MAX_SEQUENCE_LENGTH`，**不再有 PAD 白算**；换件前是定长 512 |
| 来源 | `build/dense-model/bge-small-zh-v1.5-int8.onnx`（路线 B：weight-only int8-per-channel，sha256 `4d3b3135…`）经 `freeze_onnx_static --seq-len 128` + `onnx2tf` 转出 |
| 转换链与三条取舍 | `tools/dense_build/README.md` §7（独立 venv `build/tflite-venv`；冻结静态 `[1,128]`——§1.2 的右尺寸口径；不能用 `-tb tf_converter`；转换会就地改写输入 ONNX） |

**模型件交付时按此验收**（判据出数前写死，不许事后放宽）：

| 项 | 要求 | 依据 |
|---|---|---|
| 输入名 | `input_ids` / `attention_mask` / `token_type_ids`（int64） | `export_bge_int8.py` 的导出签名 |
| 输入长度 | 定长 **≥ 128**（= 端侧 `DENSE_MAX_SEQUENCE_LENGTH`，Stage-6 右尺寸后的口径），或动态 `[1, seq]` | 定长 < `DENSE_MAX_SEQUENCE_LENGTH` 会被 `LiteRtDenseQueryEncoder.open` **拒绝**（长输入静默截短 = 与离线口径不一致）。**随包现件就是定长 = 128（等于口径，无白算）**；"定长比口径宽"也允许（Stage-6 之前的 512 窗口件就是这种形态：端侧按 128 截断 + 右 PAD 到 512，掩码在位 ⇒ 与离线同结果，实测两种窗口输出逐字节相同，见 `docs/kb-stage6-report-2026-09-26.md` §1.2），代价只是 PAD 位白算（宿主 p50 差 4.3×） |
| 输出 | 单个 float32 输出，`numElements = 512`（**随包那一档的维度**；换档时按档取：base 档 = 768。图内已含 CLS 池化 + L2 归一） | 实测 ONNX 计算图：`Gather(0) → ReduceL2 → Clip → Expand → Div`；端侧测试的维度取自 `encoder-parity.json` 的 `dim`，不写死 |
| 数值 | 真机逐条对拍冻结参考向量（`DenseEncoderParityInstrumentedTest`，n=290）**min cosine ≥ 0.999** | **新件（128 窗口）的宿主对拍已跑**：`check_tflite_parity.py --max-len 128` 对**已安装字节** n=290 **min 0.999587 / median 0.999783 / p95 0.999841**（门 0.999，exit 0，`build/stage6/install-parity-installed.log`）——与候选件读数一致。**真机对拍（`DenseEncoderParityInstrumentedTest`）本轮未跑**：共享工作树的测试源集被另一会话 in-flight 改动挡着（编译错 34 处，非本目录文件），按"外部阻塞"记 UNVERIFIED，见文末 2026-09-28 段。历史参照：512 窗口件在 Stage-3 的 2 线程 + 512 窗口口径下真机 min 0.99963 / median 0.99978 / p95 0.99984（全过） |

**换件纪律**：任何一次换模型件都必须**重跑** `DenseEncoderParityInstrumentedTest`（真机硬门
≥0.999）与 `GoldenRetrievalInstrumentedTest`（对拍 `build/stage3-device-expectation.json`），
并把新 sha256 更新到上表。模型缺失/哈希不符时端侧**静默回退纯词面**（设计内行为，
见 `DenseRecallReranker` 契约与 `DenseRecallAssembly`），所以"换了件但没对拍"会静默降级而
不是报错——这条纪律是那次降级的唯一防线。

Stage-3 的全部真机数、参考数、回退路径与"换大档判据"见 `docs/kb-stage3-report-2026-09-24.md`；
质量参考数（离线）在 `build/stage3-device-expectation.json`（`build/` 不入库）。

换件的可执行链条（档位参数化、词表为什么不用换、转换与宿主对拍两个工具）见
`tools/dense_build/README.md` §8；**换件怎么换、怎么复算**：

```bash
# 换档（--install 才落到本目录；不跑 --install 只出中间物）
build/tflite-venv/Scripts/python.exe tools/dense_build/convert_onnx_to_tflite.py --model <档> --install
# 落件后必须重跑的两层对拍（宿主 ≥0.999 → 真机 ≥0.999）
build/tflite-venv/Scripts/python.exe tools/dense_build/check_tflite_parity.py --model <档>
./gradlew.bat :core:data:connectedDebugAndroidTest --tests "*DenseEncoderParityInstrumentedTest*"
```

**2026-09-25 Stage-5 换件（bge-base-zh-v1.5，768 维）按写死判据判定：未落地**——
第三轮实测 ①编码器对拍（全量 28,931 行）docs 逐行 cosine 最小 **0.999127567** /
queries **0.999171495**（门 ≥0.999，**已过**；第一轮的 0.9478、第二轮的 0.998638 都是同一门的前序读数，
口径为 GPTQ 误差补偿 `quant_error_compensation.py`，见 `tools/dense_build/README.md` §8.3.4），
金标融合主集 **0.7889（71/90）**（**已过** ≥0.7444）；但 ②真机单条编码 p50 ≤213ms **不过**——
宿主同形态（逐条 + PAD 512 + 掩码，n=12，Python LiteRT）实测 base 档 `.tflite`（126.2 MiB）
**p50 16,843 ms**（收尾复测 17,437 ms），小档同形态同机 **2,545 / 2,582 ms（两次实测）**，
按"宿主→真机 ≈36×"外推真机 **≈470–485 ms > 213 ms**
（**外推；base 档真机数不存在**——`DenseFirstUseCostInstrumentedTest` 因件未落地而未跑）。
⇒ 本目录**保持 Stage-3 小档原样**（`bge-small-zh-v1.5-int8.tflite` 62,396,488 B / `015b2315…`，
APK 内 stored 不压缩，本报告已用 `zipfile` 复核），判据③（门重定标）未触发、旧门一字未改。
**全阶段证据、换件清单（含字节与 sha）、延迟硬线判定（判据②不达 ⇒ 未落地）、回退复核（0 个文件）
与 UNVERIFIED 见 `docs/kb-stage5-report-2026-09-25.md`**；base 档产物全部落在 `build/`（scratch，不入库）。
端侧对拍 fixture 的维度**取自 `encoder-parity.json` 的 `dim`**（不写死 512/768）——
换件后重生成 fixture 即可（`python tools/dense_build/gen_device_parity_fixture.py --model <档>`）。
**换件未落地的第二重原因**：本轮 Kotlin 侧编译/test 任务被另一条会话在 `core/database` 的飞行改动
阻断（KSP `MissingType`，与本目录无关），故"换件后必跑的真机硬门"本轮**无法执行**——落地待共享工作树恢复。

**2026-09-26/27 Stage-6（窗口/线程右尺寸 + 参考向量链收口）**——**本目录仍是一件未动**，但有两项要先知道：

1. **口径变了**：序列窗口 512 → **128**（`dense_asset.maxLen` 与端侧
   `DENSE_MAX_SEQUENCE_LENGTH`），端侧推理线程 2 → **`min(4, 核数)`**（`DenseQueryEncoder.resolveEncoderThreads`，
   装配点 `DenseRecallAssembly` 显式传）。**随包件已于 2026-09-28 换成 128 窗口的那一份**（本节写于换件前，
   当时随包还是 512 窗口件；换件记录见文末）。
2. **参考向量链收口**（改的是 `build/` 中间物与 androidTest fixture 的溯源字段，**随包字节一个没动**）：
   `int8-{docs,queries}.npy` 曾被 Stage-5 的 base 导出覆盖成 768 维，本阶段按随包小档**逐字节恢复**
   （`e8d6e0f5…` / `fac31f0c…`），并重生成 `encoder-parity.json` 使三处逐字节自洽
   （只动 `textSource.sha256` 一行；`vectorsFile` 封存值未变）。全表见
   `docs/kb-stage6-report-2026-09-26.md` §4.4。

---

## 2026-09-28 换件记录：512 窗口 → **128 窗口**（用户批准后执行）

**换了什么**：把随包的 `bge-small-zh-v1.5-int8.tflite` 从"定长 512"换成"定长 128"的同一档、同一路线件
（`flatbuffer_direct`，源件 `build/dense-model/bge-small-zh-v1.5-int8.onnx` sha `4d3b3135…` 未变）。

| 项 | 换件前 | 换件后 |
|---|---|---|
| 字节 | 62,396,488 B | **61,608,136 B**（−788,352 B / −1.3%） |
| sha256 | `015b231580dd850e…` | **`056d4262b59f93b2f9869f43c6c6d39b26371af4f77f427ea96b74b3b0951096`** |
| 输入签名 | `[1,512]` × 3 int64 | **`[1,128]` × 3 int64**（本档 `require(定长 ≥ 128)` 仍满足） |
| 产物来源 | Stage-3 | `build/tflite-work/bge-small-zh-v1.5-win128-fbdir/out/static-128-sim_float32.tflite`（Stage-6 WP1 产出） |

**换件后已跑的闸（对**已安装的字节**，不是对 scratch 副本）**：

| 闸 | 命令 | 读数 |
|---|---|---|
| 宿主对拍（硬门 ≥0.999） | `check_tflite_parity.py --model bge-small-zh-v1.5 --tflite core/data/src/main/assets/dense/bge-small-zh-v1.5-int8.tflite --threads 4 --max-len 128` | **exit 0**；n=290 **min 0.999587 / median 0.999783 / p95 0.999841**（query min 0.999722 / surface min 0.999587）；设备样 vs `int8-queries.npy` min **0.999726**。读数 `build/stage6/install-parity-installed.log` |
| 资产陈旧性门（10 道） | `python tools/dense_build/check_asset.py` | **10/10 OK**（含 `modelManifest` 三方一致：vocab / sidecar onnx sha / onnx 在位复核哈希）。**注**：清单与旁车记的是 **ONNX 层**事实（`maxLen: 512` 是那些 cosine 数的测量口径），tflite 的窗口不在这两处登记——所以本次换件不改清单/旁车 |
| 输入签名 == 口径 | `ai_edge_litert` 读 `get_input_details()` | `[1,128]` × 3，`require(定长 ≥ DENSE_MAX_SEQUENCE_LENGTH=128)` 成立 |
| 装载核实（真的随包了） | `:app:assembleLocalFirstDebug` + `zipfile` 读 `assets/dense/bge-small-zh-v1.5-int8.tflite` | 条目 **stored（compress_type=0）**、`file_size=61608136`、**sha256 `056d4262…` 逐位相同** |

**未跑的闸（外部阻塞，不当作通过）**：`DenseEncoderParityInstrumentedTest`（真机硬门）与
`GoldenRetrievalInstrumentedTest`（对拍 `build/stage3-device-expectation.json`）——共享工作树的
**测试源集**被另一会话 in-flight 改动挡着（`compileDebugAndroidTestKotlin` 报 34 处
`No value passed for parameter 'knowledgeBaseAvailability'`，全在**非本目录**文件里）。
⇒ 这两条按纪律记 **UNVERIFIED（外部阻塞）**，下面是可用为止的替代证据与触发条件：

- **替代证据**（都不等于真机门）：① 上面那条**对已安装字节**的宿主对拍 n=290 min 0.999587；
  ② 窗口变化对向量是**位同**（同一份 ONNX 在 512/128 两种窗口下逐条重算，290/290 行**逐字节相同**，
  §1.2）；③ 同一路线、同一签名形态的 512 窗口件曾在真机过 0.999（Stage-3：min 0.99963）。
- **期望的真机结果**：`encoder-parity.json` 的 `dim: 512` 与 `.vec` 的 dim 512 未变、fixture 未变
  ⇒ 真机门应当报与 8.1 相同的 cosine 分布（缺的只是"这台设备上确实如此"这一条实证）。
- **一旦测试源集恢复可编译**：跑
  `./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.data.knowledge.dense.DenseEncoderParityInstrumentedTest`
  与同名的 `GoldenRetrievalInstrumentedTest`；任一不达 ⇒ 按下面的回退件退回，并把结果写回本表。

**回退**：换件前的字节 = `git show HEAD~<本次提交>:core/data/src/main/assets/dense/bge-small-zh-v1.5-int8.tflite`
（sha `015b2315…` / 62,396,488 B；本次执行时留了一份副本在 `build/backup-stage6-install/`，`build/` 不入库）。
退回是**一次文件覆盖**，Kotlin 侧无需改动（`DENSE_MAX_SEQUENCE_LENGTH=128` 对 512 定长件同样成立——
"定长比口径宽"是允许形态）。

**未随本次换件的可选路线**：同窗口的 `flatbuffer_direct_keepint8` 件（22.8 MiB / `build/tflite-work/`
`bge-small-zh-v1.5-win128-keepint8/`）宿主对拍同为 min 0.999587、体积再少 38 MB，代价是宿主 p50 +3.4%
（590.4 vs 570.8 ms，噪声量级）。本次按"与换件前同一路线"取 `flatbuffer_direct`（变量最少）；
若将来要压 APK，换这条路线是**另一次换件**（同样要走上面三道闸 + 真机门）。

**换件的收益**：宿主 p50 512→128 实测 **4.32–4.42×**（小档 `flatbuffer_direct` 2522.5 → 570.8 ms@4 线程，
keepint8 2551.6 → 590.4 ms），**质量零变化**（向量位同；对拍 min 与 512 窗口件同级）。端侧收益的
直接读数待真机门（`DenseFirstUseCostInstrumentedTest`；本机模拟器在宿主争用下 p50 在 116–223 ms 间漂，
不能当仪器用——见 `docs/kb-stage6-report-2026-09-26.md` §8.2）。
