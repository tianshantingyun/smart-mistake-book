# E-04 R8 internal 变体冷启动 ×2 + Worker + 中文 OCR

| 字段 | 值 |
|---|---|
| evidence-id | E-04 |
| convention-version | 1 |
| batch | 2026-10-26-batch-23 |
| step | KD-69 设备冒烟 |
| gate-or-kd | KD-69 |
| verdict | PASS |
| executed-at | 2026-10-26T16:21:00+08:00 |
| operator | 设备验证轨（subagent，批次 23） |
| head | f0a145f850931887385c4bf2e1f798a0e8f2e391 |
| worktree | dirty（他线在飞；本轨未改产品源） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `app/build/outputs/apk/strictOffline/internal/app-strictOffline-internal.apk` |
| apk-sha256 | `da8df2f8a80f0b18a10a8773232a29150071ae6930a349c94ae9cb58681f0e5b` |
| apk-version | 0.2.0-offline（internal, minify=true, debug 签名, debuggable） |
| exit-code | 0（adb install / am start 均成功） |
| raw-log | `build/device-track-batch23/step04-coldstart{1,2}-*.txt`、`step04-ocr-*.txt`、`step04-ocr-logcat.txt`、`ocr_db.db*` |
| record-ref | `docs/research/2026-10-26-batch-23-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

KD-69 的 Reopen condition：`internal` minified 构建装机**冷启动（含二次冷启动）无** `FATAL EXCEPTION` / `StartupException` / `NoSuchMethodException` / `ClassNotFoundException` / `NoClassDefFoundError`，且**一次中文 OCR** 无 `MlKitException` / `NoSuchMethodException`。

本步另外核：一次 Worker 触发（真实实例化并执行）；`usage.txt` 静态确认 Room/ML Kit 反射构造器未被裁；OCR 产物真实落库（不是只"没崩"）。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
PKG=com.tingyun.smartmistakebook.offline
$A -s emulator-5554 install -r -t app/build/outputs/apk/strictOffline/internal/app-strictOffline-internal.apk
# 设备侧 APK 身份
$A -s emulator-5554 shell sha256sum "$($A -s emulator-5554 shell pm path $PKG | sed 's/package://')"
# 冷启动 1 / 2
$A -s emulator-5554 shell am force-stop $PKG; $A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am start -W -n $PKG/com.tingyun.smartmistakebook.MainActivity
sleep 12; $A -s emulator-5554 shell pidof $PKG
$A -s emulator-5554 logcat -v threadtime -d > step04-coldstart1-logcat.txt
# （重复一次得 coldstart2）
# OCR：合成中文图片放进 App 自己的 FileProvider 路径后 SEND
MSYS_NO_PATHCONV=1 $A -s emulator-5554 push build/device-track-batch23/ocr_fixture.png /data/local/tmp/ocr_fixture.png
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell "run-as $PKG cp /data/local/tmp/ocr_fixture.png files/batch_import_staging/ocr_fixture.png"
$A -s emulator-5554 logcat -c
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell am start -W -a android.intent.action.SEND -t image/png -f 1 \
  --eu android.intent.extra.STREAM content://$PKG.batch-import.fileprovider/batch_import_staging/ocr_fixture.png \
  -n $PKG/com.tingyun.smartmistakebook.MainActivity
sleep 25; $A -s emulator-5554 logcat -v threadtime -d > step04-ocr-logcat.txt
# 落库复核（debuggable 变体）
MSYS_NO_PATHCONV=1 $A -s emulator-5554 exec-out run-as $PKG cat databases/smart-mistake-book.db > ocr_db.db
```

OCR 夹具：PIL 生成 900×500 PNG，文字为「已知函数 f(x)=x²，求它的单调区间。解：f'(x)=2x，当 x>0 时单调递增。」（合成内容，非真实学习者数据）。

## 3. 结果摘要

| 指标 | 冷启动 1 | 冷启动 2 | OCR | 判据 |
|---|---|---|---|---|
| `am start -W` Status | ok | ok | ok（intent 投递到已有实例） | ok |
| LaunchState | COLD | COLD | — | COLD |
| TotalTime (ms) | 4935 | 5135 | — | — |
| PID | 21582 | 21674 | 21674 | 进程未崩 |
| 设备侧 APK sha256 | `da8df2f8…0e5b` | 同 | 同 | 与 internal APK 一致 |
| FATAL/ANR/Startup/NoSuchMethod/ClassNotFound/NoClassDef/UnsatisfiedLink/MlKit/SIGSEGV | 0 | 0 | 0 | 0 |

Worker 实际执行（两次冷启动都触发）：

```text
08:19:09.394 WM-WorkerWrapper: Starting work for com.tingyun.smartmistakebook.BatchImportDriverWorker
08:19:09.409 WM-WorkerWrapper: Starting work for com.tingyun.smartmistakebook.OrphanAssetGcWorker
08:19:10.152 WM-WorkerWrapper: Worker result SUCCESS for Work [ ... tags={ ...OrphanAssetGcWorker,orphan-asset-gc } ]
08:19:10.172 WM-WorkerWrapper: Worker result SUCCESS for Work [ ... tags={ ...BatchImportDriverWorker,batch-import-driver } ]
```

（第二次冷启动 `step04-coldstart2-logcat.txt:390-466` 同形。）

## 4. 关键输出（有界）

中文 OCR（`step04-ocr-logcat.txt:84-139`）：

```text
08:20:37.148 D DecoupledTextDelegate: Start loading thick OCR module.
08:20:37.149 I DynamiteModule: Considering local module com.google.mlkit.dynamite.text.chinese:10000 and remote module ...:0
08:20:37.150 I DynamiteModule: Selected local version of com.google.mlkit.dynamite.text.chinese
08:20:37.789 I native: Loading mlkit-google-ocr-models/gocr/gocr_models/line_recognition_legacy_mobile/Hani_ctc/optical/conv_model.fb
08:20:39.590 D PipelineManager: OCR process succeeded via visionkit pipeline.
```

落库复核（`ocr_db.db` 只读查询）：

```text
('draft-b59c647a4b9fa864a691a95afb6af018', 1, 'CAPTURE_IMPORT', ...)
('draft-b59c647a4b9fa864a691a95afb6af018', 2, 'LOCAL_OCR', ...,
 '{"schemaVersion":1,"document":{...,"blocks":[
    {"type":"paragraph","id":"ocr-000","markdown":"已知函数f(x) =x2,"},
    {"type":"paragraph","id":"ocr-001","markdown":"求它的单调区间。"},
    {"type":"paragraph","id":"ocr-002","markdown":"解:fK)=2x,"}, ...]}}')
```

→ 不是"没崩"：ML Kit 中文模型真实加载、OCR 成功，`LOCAL_OCR` revision 2 已写入 Room。

R8 keep 静态对照（构建产物）：

```text
usage.txt:43093 androidx.work.impl.WorkDatabase_Impl:
usage.txt:43094     private volatile ... RawWorkInfoDao _rawWorkInfoDao
usage.txt:43095     public void clearAllTables()
usage.txt:43096     public ... rawWorkInfoDao()
        # 段内没有 public void <init>() → 无参构造器未被裁
usage.txt:77366 com.google.mlkit.common.internal.CommonComponentRegistrar:   # 只有 synthetic zza
usage.txt:77375 com.google.mlkit.vision.common.internal.VisionCommonRegistrar:
usage.txt:77378 com.google.mlkit.vision.text.internal.TextRegistrar:
configuration.txt:101  -keepclassmembers class * extends androidx.room.RoomDatabase {
configuration.txt:110  -keep class com.google.mlkit.common.internal.CommonComponentRegistrar { <init>(); }
configuration.txt:111  -keep class com.google.mlkit.vision.text.internal.TextRegistrar { <init>(); }
configuration.txt:112  -keep class com.google.mlkit.vision.common.internal.VisionCommonRegistrar { <init>(); }
```

## 5. 失败 / 跳过 / 异常

- 无崩溃、无 skip。
- 说明：AGP 默认 `testBuildType=debug`，没有 `assembleStrictOfflineInternalAndroidTest` 变体（命令报 task not found），所以 R8 变体没有仪器化测试 APK；OCR 与 Worker 均通过**真实 App 入口**触发，而不是测试注入。
- "一次 Worker 触发"由启动链真实完成（`ExportPdfWorker` 未单独触发；`BatchImportDriverWorker` 与 `OrphanAssetGcWorker` 都是生产 Worker 类，走 WorkManager 反射实例化）。
- `usage.txt` 的"无第三个反射可达的被裁无参构造器"只核了 Room + 三个 ML Kit registrar；全量穷举未做。

## 6. 脱敏与去噪

- 删除：GMS 心跳、重复的 Compose/JIT 行（全量留在 `build/`）。
- 保留：`am start` 结果、PID、Worker start/SUCCESS、OCR 模块/模型/Pipeline 行、落库查询结果、签名计数。
- OCR 文本为合成夹具，不涉及真实学习者内容。
- 证据目录统一扫描见 `00-index.md` §6。

## 7. 判定

**PASS**：KD-69 的 Reopen condition 在 `strictOffline internal`（minify=true、真 R8）上成立——两次冷启动均 ok/COLD、零崩溃签名；两个生产 Worker 真正实例化并 SUCCESS；一次中文 OCR 真正完成并落库，零 `MlKitException`/`NoSuchMethodException`；`usage.txt` 无 Room/ML Kit registrar 的 `<init>` 被裁。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch23/step04-*`、`build/device-track-batch23/ocr_db.db*`
- 行数：冷启动 logcat 537 / 548 行；OCR logcat 189 行
- 生成命令：见 §2
