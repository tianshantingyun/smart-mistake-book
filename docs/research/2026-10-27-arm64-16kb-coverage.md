# arm64 / 16KB 覆盖可行路径研究（R4 → 设备轨驱动）

- 日期：2026-10-27（批次 25，R4 研究线）
- 结论消费者：批次 25 设备轨（T-轨）、主会话门禁编排、契约 `visual-generation-contract.md:22` 启用前门（"16 KB 真机加载、API 23/arm64 运行时"）的证据规划
- 本文所有"实测"均在 2026-10-27 于本机（Windows x86_64 / Git Bash，SDK=`C:\Android\Sdk`，emulator 37.1.11.0）本轮亲自执行，命令与输出逐条记录，可原样复跑；与既有 R3 运行时研究（`2026-10-25-ps16k-emulator-feasibility.md`）、R4 静态研究（`2026-10-25-16kb-arm64-api23-build.md`）互补，本文只负责 **arm64/16KB 覆盖轴的"哪些证据在哪里能拿到"**。
- 共享环境注意：本轮全程只读操作既有 4 台在飞模拟器（emulator-5554/5556/5562/5584，收尾复核原样在线）；唯一新增的是**未运行任何他线正在使用的对象**（见 §5 资产登记）。本轮未运行 Gradle、未做任何 git 写操作、未改任何仓内代码。

## 0. 结论摘要（TL;DR，对应任务四问）

1. **ps16k x86_64 能证明什么**：16KB 内核真实运行（`getconf PAGE_SIZE`=16384，本轮在 emulator-5556 实测复核）+ **x86_64 ABI 的 ELF 由 bionic linker 在 16KB 页宽下真实 mmap 装载**（含 `libquickjs.so` x86_64 切片；批次 24 E-04 已在 ps16k 上 3/3 实证，`2026-10-26-batch-24-record.md:158`）+ 页级 RELRO mprotect 对 x86_64 库的运行时面。**不能证明**：arm64 指令集执行、arm64 `.so` 的运行时装载（ps16k 只映射 `lib/x86_64/*`，APK 里的 arm64 `.so` 永远不被设备端 linker 触碰）、真机兼容模式判定、arm64 性能/内存特征（§1）。
2. **arm64 镜像 + qemu 慢速跑：本机不可行，且是硬拒载而非慢**。sdkmanager 可以下载 arm64 ps16k 镜像（本轮实测下载了 `system-images;android-36;google_apis_ps16k;arm64-v8a` r07，4.3 GB，exit 0），但本机 emulator 37.1.11.0 启动 arm64 AVD 直接 **FATAL**：`Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host. System image must match the host architecture.`——不存在"慢速 TCG 跑"选项；官方口径同样是 arm64 系统镜像只在 arm64 宿主机（KVM/HVF）上受支持。独立 `qemu-system-aarch64` 路径本机连二进制都没有，且需 Linux+KVM 或 ARM 宿主，ranchu 内核与上游 qemu 机型不匹配，**不是验证路径，只是研究项目**（§2）。
3. **KD-70 P2 在 ps16k 重跑：判据与批次 24 完全一致、不放宽**；新增的覆盖轴是"页宽 4KB→16KB × x86_64"。预期 `mallocLimit=16777216` 不变（应用侧预算，与页宽无关）、`mallocSize ≥ 15,099,495`（≥90%）预期不变、夹具 `cooperativeTimeoutMillis=10_000` 对模拟器慢速有 100×余量。跑绿后 **KD-70 的 reopen condition 若已由主会话裁定关闭则维持关闭；arm64 轴缺口独立存在**，本跑不闭合契约 `:22` 的"16 KB **真机**加载"子句（§3）。
4. **设备轨执行与降级声明模板**：每设备一块证据头（serial/AVD/镜像 rev/API/ABI/PAGE_SIZE/APK sha256 双侧）+ 逐例 `INSTRUMENTATION_STATUS_CODE` + 崩溃签名扫描 + 逐条结论强制标注 `[PROVED on <serial>: ABI×页宽×API]` 或 `[UNVERIFIED: 原因]`；arm64 轴、真机子句、性能/内存面在 ps16k 上只能标 UNVERIFIED，模板给出可直接粘贴的句式（§4）。

## 1. ps16k x86_64 的证明边界（任务第一问）

### 1.1 能证明（每条给证据来源）

| # | 判据 | 为什么 ps16k x86_64 能证明 | 证据状态 |
|---|---|---|---|
| P-1 | **16KB 内核运行时存在且生效** | 页宽烘焙在镜像内核（x86_64 ps16k 镜像 `kernel_cmdline.txt` 含 `page_shift=14`，`2026-10-25-ps16k-emulator-feasibility.md:70`）；运行时三断言 `getconf PAGE_SIZE`/`ro.product.page_size`/`ro.boot.hardware.cpu.pagesize` 全 16384（同文 §1.4） | 本轮复核 `adb -s emulator-5556 shell getconf PAGE_SIZE` → **16384**（emulator-5556 = `ps16k_x86_64`，`emu avd name` 实测确认） |
| P-2 | **x86_64 ELF 在 16KB 页宽下被 bionic linker 真实 mmap** | 设备上执行的是 APK 的 `lib/x86_64/*.so`；16KB 内核以 16KB 粒度建映射，4KB 对齐的库会以 `failed to map segment`/`unsupported RELRO`/SIGSEGV 拒载——这正是 `NativeLibraryPageSizeInstrumentedTest` 的失败面（`core/data/src/androidTest/.../NativeLibraryPageSizeInstrumentedTest.kt:9-19` KDoc 明示） | **已有设备证据**：批次 24 E-04 在 `ps16k_x86_64` 3/3 PASS，`PAGE_SIZE=16384`，QuickJS/LiteRT 库真实装载（`docs/research/2026-10-26-batch-24-record.md:158`） |
| P-3 | **页级 RELRO mprotect 的运行时面（x86_64）** | 违规形态 = RELRO 终点所在 16KB 页内还有可写段 → linker 把整 16KB 页 mprotect 只读 → 后续写 `.data` 崩溃（官方判据，`2026-10-25-16kb-arm64-api23-build.md:59`）。ps16k 上 x86_64 库装存活 + 零 linker 错误 = 该面在 x86_64 上无违规 | 静态侧：x86_64 库 RELRO 全部"独占 LOAD 段"豁免合规（同文 §2.3/§2.4 + E-09 llvm-readobj）；动态侧：E-04 装载绿 + ps16k 冒烟零 linker/RELRO grep 命中（ps16k 可行性研究 §2 第 7 步） |
| P-4 | **QuickJS 引擎（AAR x86_64 切片）在 16KB 内核上的执行行为** | AAR 的 `libquickjs.so` x86_64 在 ps16k 已被 `System.loadLibrary` 真实装载（E-04）；KD-70 全量用例（引擎创建/执行/OOM 归因）可在同设备复跑——页宽只改 OS 映射粒度，QuickJS 的 16 MiB `malloc_limit` 是应用侧预算，与页宽无关 | 本轮静态确认：当前 APK（`app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk`，本轮最新）含 **arm64-v8a 与 x86_64 各 7 个 .so**（`libLiteRt/libLiteRtClGlAccelerator/liblitert_jni/libmlkit_google_ocr_pipeline/libandroidx.graphics.path/libdatastore_shared_counter/libquickjs`，python zipfile 实测）→ ps16k 用例链路具备全部前置 |
| P-5 | **应用进程在 16KB 内核上的端到端兼容**（安装/启动/UI/PhotoPicker） | 批次 21 R3 已全链路实测：192 MB APK 安装 Success、启动、导航、选图回传，零 linker/dlopen/RELRO 错误（`2026-10-25-ps16k-emulator-feasibility.md` §0-1/§2） | 已有 |

### 1.2 不能证明（每条给不能的理由；这些在 ps16k 上只能标 UNVERIFIED）

| # | 缺口 | 为什么 ps16k x86_64 原理性地补不上 |
|---|---|---|
| N-1 | **arm64-v8a 指令集执行** | CPU 是 x86_64（`abi.type=x86_64`，config.ini 实测）；设备上没有任何 ARM 指令路径被执行。arm64 特有的 JNI/寄存器/对齐 bug 不可见 |
| N-2 | **arm64 `.so` 的运行时装载** | 设备端 linker 只解析 `abi.type` 指向的 x86_64 切片；APK 里 7 个 arm64 `.so`（本轮 zipfile 实测在包）**永远不被映射**。arm64 对齐合规目前只有静态证据（llvm-readobj/判据脚本，E-09 + R4 §2.4），"静态合规 ≠ 动态装载"正是 `NativeLibraryPageSizeInstrumentedTest.kt:9-10` 自认的边界 |
| N-3 | **arm64 linker 差异**（arm64 专属 RELRO 布局、BTI/PAC、MTE 等） | 同 N-1/N-2：这些机制只在 arm64 内核+arm64 linker 上存在 |
| N-4 | **真机兼容模式（backcompat）判定** | ps16k 是 userdebug 模拟器；真机 16KB 启动（Pixel 8/9 开发者选项）+ Play 安装链路的兼容模式对话/属性（`bionic.linker.16kb.app_compat.enabled` 等，官方页）行为不同。本项目 64 位库全合规、32 位已裁（当前 APK 只有 arm64+x86_64，本轮实测），预期用不到兼容模式，但这只是预期，不是真机证据 |
| N-5 | **arm64 性能/内存特征**（KD-70 复核 #6 的第 ② 点：慢速/arm64 上 P2 是否先落 `PHASE_TIMEOUT`） | 无 arm64 执行路径即无 arm64 计时。x86_64 上的 P2 耗时（批次 24 实测 98 ms，`docs/known-defects.md:2705`）不能外推到 arm64 |
| N-6 | **AAB/Play 分发形态**（`PAGE_ALIGNMENT_16K`、split APK 交付） | 模拟器装的是完整 APK；AAB 侧核查是独立静态步骤（`2026-10-25-16kb-arm64-api23-build.md:291-295` §6.4，仍未运行） |

**小结**：ps16k x86_64 把 16KB 轴上 **x86_64 一列**从"静态合规"升级为"运行时已证"（P-1…P-5）；arm64 一列（N-1…N-3）在无 ARM 宿主/真机时**任何本机手段都补不上**——这是 §2 实证后的定论，不是保守估计。

## 2. arm64 系统镜像 + qemu 慢速跑：可行性实测与定论（任务第二问）

### 2.1 本机实证链（2026-10-27，全部亲自执行）

**探针 1：arm64 ps16k 镜像在 sdkmanager 远端目录可得**（只读，不下载）：

```
$ java -Dcom.android.sdklib.toolsdir=... -classpath ... SdkManagerCli --list | grep ps16k.*arm64
  system-images;android-35;google_apis_ps16k;arm64-v8a        | 5 | 16 KB Page Size Google APIs ARM 64 v8a System Image
  system-images;android-36;google_apis_ps16k;arm64-v8a        | 7 | （同上，API 36）
  …（API 35→37.2-beta2 全系，google_apis / google_apis_playstore / google-tv 三 flavor，共 15 行）
```

**探针 2：下载安装成功**（增量目录，不影响既有镜像）：

```
$ SdkManagerCli "system-images;android-36;google_apis_ps16k;arm64-v8a"   → exit 0
$ du -sh C:\Android\Sdk\system-images\android-36\google_apis_ps16k\arm64-v8a
  4.3G    （arm64-v8a-ps16k-36_r07；Pkg.Dependencies=emulator#35.4.9，本机 37.1.11 满足）
```

**探针 3：AVD 可创建**（avdmanager 接受 arm64 镜像）：

```
$ AvdManagerCli create avd -n ps16k_arm64 -k 'system-images;android-36;google_apis_ps16k;arm64-v8a' -d pixel_7
  Auto-selecting single ABI arm64-v8a；config.ini: abi.type=arm64-v8a, tag.id=page_size_16kb
```

**探针 4（决定性）：x86_64 宿主机引导 = FATAL 硬拒载，不是慢速 TCG**：

```
$ emulator.exe -avd ps16k_arm64 -port 5612 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect
  INFO   | Android emulator version 37.1.11.0 (build_id 15917651)
  INFO   | Found systemPath C:\Android\Sdk\system-images\android-36\google_apis_ps16k\arm64-v8a\
  FATAL  | Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host. System image must match the host architecture.
  （进程即退；收尾复核 adb devices 四台他线模拟器原样在线，无残留）
```

**探针 5：本机无独立 qemu**：`which qemu-system-aarch64 qemu-system-x86_64` 零命中、`C:\Program Files\qemu` 不存在。

### 2.2 官方口径与社区证据交叉

- **官方（模拟器发行说明，developer.android.com/studio/releases/emulator，本轮抓取）**：30.0.26 条目——"Linux 模拟器源代码现在支持从 x86_64 到 **arm64 主机**的交叉编译，从而允许通过 **KVM 虚拟化**运行 arm64 系统映像"。即 arm64 系统镜像的受支持宿主 = **arm64 主机**（Apple Silicon / ARM Linux KVM）。页面通篇没有"arm64 镜像跑在 x86_64 主机"的支持形态。
- **官方（同一发行说明 30.0.0 条目）**：x86_64 镜像上的 ARM 支持是**应用级二进制翻译**（"Android 9 和 Android 11 系统映像默认支持 ARM…与采用完全 ARM 模拟的系统映像相比性能显著提升"；x86_64 镜像内含 ARMv7+ARM64 ABI 翻译）。注意三点：① 这是 API 30 时代 x86_64 镜像内的 app 翻译层，**内核仍是 x86_64**，不构成 arm64 linker/装载证据；② ps16k 是 API 35+ 专用镜像变体，目录中无任何"带 ARM 翻译"的 ps16k x86_64 变体（探针 1 的 15 行里没有）；③ 与 16KB 轴无关。
- **社区多源一致**（Stack Overflow `75188975`、android-emulator-m1-preview#34、GitHub README 引述的 AEHD 错误 "arm64 system images are unsupported on this x86_64 host"、CI runner issue #377）：x86_64 宿主上 arm64 AVD 或立即崩溃或挂死。与探针 4 的 FATAL 完全一致。
- **独立 qemu-system-aarch64 路径**：Linaro QEMU wiki（"Run Android"）作者结论："I have not been able to launch any aarch64 vm from a Linux/Windows x64 host … virtualization seems to be the only solution working nowadays"；Fedora wiki 与 Stack Overflow `79790166` 的可行路线均为 **aarch64 宿主 + KVM** 或 **Linux 宿主 + Cuttlefish**（crosvm/qemu，且 arm64 cuttlefish 在 x86_64 宿主上退化为 TCG 全模拟）。本机是 Windows（Cuttlefish 不支持 Windows 宿主）且无 qemu。AOSP emulator 的 ranchu 内核面向 goldfish/ranchu 虚拟板，上游 `qemu-system-aarch64 -M virt` 缺其设备模型，社区结论是"vanilla qemu 引导 AOSP 镜像需重度改造"（XDA/SO 多源）。

### 2.3 定论

1. **本机（x86_64 Windows）不存在 arm64 运行时验证路径**——emulator 层 FATAL 硬拒（探针 4），独立 qemu 层无二进制且原理不通（§2.2）。这不是耗时问题，是**能力缺失**；"慢速跑"对 Android emulator 不存在。
2. **耗时风险量化（若未来换 ARM 宿主/云）**：arm64 镜像在 ARM64 宿主 KVM/HVF 上原生速度（官方支持形态，无额外风险）；TCG 跨 ISA 全系统引导 Android 为社区报告的 10–100× 慢、引导可达小时级（**待验证**，社区数字，本轮无法本机实测——本机根本没有该执行路径）。
3. **arm64 轴的现实路径排序**（供主会话裁定）：
   - **a. ARM64 宿主机**（Apple Silicon Mac / ARM Linux KVM）：镜像+AVD 本轮已预置好（§5），拷命令块即跑；这是唯一能给出"arm64×16KB 模拟器运行时证据"的形态。
   - **b. arm64 真机 + 16KB 启动**：官方页列明 Pixel 8/8 Pro/8a（Android 15 QPR1+）、Pixel 9/9 Pro/9 Pro XL（QPR2+）、Pixel 9a（Android 16+）支持开发者选项"以 16 KB 页面大小启动"，`getconf PAGE_SIZE`=16384 验证（developer.android.google.cn/guide/practices/page-sizes，本轮抓取）。这是契约 `:22` "16 KB **真机**加载"子句的正解，也是 Play 合规（2027-02-01 硬线，targetSdk 35+）意义上的最终形态。
   - **c. 云真机/远程测试**：官方页列名的 16KB 测试环境含 **Samsung Remote Test Lab** 与 Cuttlefish（Linux）；设备农场租 arm64 真机开 16KB 模式为等效路径。本轮未逐家核可用机型与配额（**待验证**，超出本机范围）。

## 3. KD-70 P2 在 ps16k（16KB × x86_64）重跑：判据与预期（任务第三问）

### 3.1 环境与前检（设备轨执行前逐条打勾）

```bash
# serial 以实际为准；本机 ps16k AVD 当前在 emulator-5556（本轮实测在线）
S=emulator-5556
adb -s $S emu avd name                       # 期望: ps16k_x86_64
adb -s $S shell getconf PAGE_SIZE            # 期望: 16384（4KB 则整轮判据不成立，停）
adb -s $S shell getprop ro.build.version.sdk # 期望: 36
adb -s $S shell getprop ro.product.cpu.abi   # 期望: x86_64
# 测试 APK 内须有 x86_64 libquickjs.so（python zipfile 或 unzip -l 检查）+ sha256 落盘
```

### 3.2 执行命令块（协议沿用批次 23/24；**由设备轨执行，本轨未运行任何 Gradle**）

```
:core:data:assembleDebugAndroidTest          # R3 §7.2 口径；禁 connectedAndroidTest
adb -s $S install -r -t core/data/build/outputs/apk/androidTest/debug/*.apk   # + 被测 APK
adb -s $S logcat -c
adb -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest \
  com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner
# 同设备补 16KB 门三类（本跑在 ps16k 上 runtimePageSizeIs16KB 才是真正断言 16384，4KB 上它 skip）：
adb -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.NativeLibraryPageSizeInstrumentedTest \
  com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner
# 收尾：logcat 全量落盘 + 签名 grep（FATAL EXCEPTION/ANR/CANNOT LINK/failed to map/unsupported RELRO/NoClassDef/NoSuchMethod）
```

（不要加 `-e log true`——KD-70 台账明示的既有口径。）

### 3.3 P2 判据（逐字来自用例断言，**不放宽、不改判**）

`heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage`（`core/data/src/androidTest/.../QuickJsVisualExecutionEngineInstrumentedTest.kt:300`）在 ps16k 上的通过判据与批次 24 E-02 完全一致：

1. 结果为 `Rejected` 且 `reason == MEMORY_LIMIT`（`:322-326`）；
2. 诊断含 `attributed by memoryUsage`、`mallocLimit=16777216`、`PENDING_CALIBRATION`（`:321-327`）;
3. 诊断不含 `mallocSize=-1`（快照真的取到，`:328-329`）；
4. 由此约束 `mallocSize ≥ 15,099,495`（≥90% × 16 MiB；精确值仍未打印，维持既有 EXACT VALUE UNVERIFIED 缺口）；
5. 全类 20/20 绿（含 KD-71 两例）+ 7 类崩溃签名 0 + `PAGE_SIZE=` 日志行 = 16384。

### 3.4 预期差异与风险（如实）

| 维度 | 批次 24 E-02（已证） | ps16k 重跑预期 | 依据 |
|---|---|---|---|
| 页宽 | 4096 | **16384** | 本轮 getconf 实测 |
| API | 34 | 36 | `ps16k_x86_64` 镜像（R3 §1.4 fingerprint=SDK 36） |
| `mallocLimit` | 16777216 | **同值**——应用侧 QuickJS 堆预算，与内核页宽无关 | 断言字符串 `mallocLimit=16777216`（`:321`） |
| `mallocSize` 水位 | ≥90% 断言绿（精确值未打印） | 预期仍 ≥90%；页宽可能轻微改变分配器粒度/水位小数——**只要 ≥15,099,495 即绿**；若读数 < 阈值 = 红，按纪律落盘实际值并让 KD-70 保持跟踪，**不得下调阈值** | KD-70 台账"不得下调 90% 阈值"（`docs/known-defects.md:2693`） |
| 耗时 | P2 98 ms（`:2705`） | 同数量级；夹具 `cooperativeTimeoutMillis=10_000`/`hardDeadlineMillis=30_000`（`:303-305`）对共享宿主 4 模拟器并存的慢速有 ≥100× 余量 → `PHASE_TIMEOUT` 抢跑风险低，但仍须落盘逐例耗时 | `:249`/`:303-305` |
| 覆盖轴含义 | 16KB 未测 | 新增 **"16KB × x86_64"** 轴；与批次 24 的 "4KB × x86_64" 合成 x86_64 双页宽矩阵 | 本文 §1.1 P-4 |

### 3.5 本跑能闭合什么、不能闭合什么

- **能闭合**：KD-70 reopen condition 中"判据在设备成立"的 **16KB 页宽轴**（x86_64）；`NativeLibraryPageSizeInstrumentedTest` 三例在 ps16k 的复跑形成自洽证据集。
- **不能闭合**：① R2 §5 "arm64 各一次"的 **arm64 轴**（本机原理性不可行，§2）；② 契约 `visual-generation-contract.md:22` 启用前门三子句中的 "**16 KB 真机加载**"（ps16k 是模拟器——除非主会话裁定"模拟器 16KB 运行时证据等价于真机装载证据"，该裁定应由主会话做并落记录，本文只提供判据差异：模拟器无 Play 安装链路、无真机兼容模式对话、无 OEM linker 差异）与 "**API 23/arm64 运行时**"（minSdk 23 的 arm64 真机路径）；③ KD-70 是否维持关闭由主会话按台账回链裁定，与批次 24 相同，本跑不改变裁定主体。

## 4. 设备轨执行与降级声明模板（任务第四问）

### 4.1 证据头模板（每设备一块，对齐批次 23/24 E-file 结构）

```markdown
## E-XX <名称>（<日期>，批次 25 设备轨）
- 设备身份：serial=<emulator-XXXX>；AVD=<名>；镜像=<system-images;…;rev>；emulator=<版本 build_id>
           API=<NN>；ABI=<x86_64|arm64-v8a>；PAGE_SIZE=<getconf 输出>；fingerprint=<getprop ro.build.fingerprint>
- 产物身份：被测 APK 与测试 APK 的 sha256/字节数（构建侧 + 设备侧 `pm path`+`sha256sum` 双侧一致）
- 执行命令：逐条列出（install / am instrument -w -r -e class …），含退出码
- 逐例状态：INSTRUMENTATION_STATUS_CODE 1→0 逐例表（绿/红/skip 三态，skip 必须给 assume 原因）
- 签名扫描：logcat 全量 grep <崩溃/linker 签名清单>，命中逐条归因（被测应用 vs 系统组件）
- 结论：逐条标注 [PROVED on <serial>: <ABI>×<页宽>×<API>] 或 [UNVERIFIED: <原因>]
```

### 4.2 降级声明句式（可直接粘贴；绿/红各取所需）

**结论 A（16KB × x86_64 装载）——ps16k 上可证：**

> 在 `ps16k_x86_64`（API 36，x86_64，`getconf PAGE_SIZE`=16384）上，`NativeLibraryPageSizeInstrumentedTest` 3/3（`runtimePageSizeIs16KB` 断言 16384 通过而非 skip），`libquickjs.so`/`libLiteRt.so`/`liblitert_jni.so` x86_64 切片由 bionic linker 真实装载，logcat linker/RELRO grep 零命中。**[PROVED on <serial>: x86_64 × 16KB × API 36]**

**结论 B（KD-70 P2）——ps16k 上可证：**

> 在同一 16KB × x86_64 设备上，`heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage` 绿：reason=`MEMORY_LIMIT`，诊断含 `attributed by memoryUsage`/`mallocLimit=16777216`/`PENDING_CALIBRATION`，`mallocSize != -1`（≥90% 阈值由断言约束）。**[PROVED on <serial>: x86_64 × 16KB × API 36]**（若红：如实落盘实际 reason/`mallocSize`，按"失败归因"三分类登记，不得放宽断言。）

**结论 C（arm64 轴）——本机只能 UNVERIFIED，句式：**

> arm64 运行时轴 **UNVERIFIED**：本机（x86_64 Windows）emulator 37.1.11.0 对 arm64 系统镜像 FATAL 硬拒载（`Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host`，2026-10-27 R4 实测，`docs/research/2026-10-27-arm64-16kb-coverage.md` §2.1 探针 4）；arm64 `.so` 仅有静态对齐证据（E-09 llvm-readobj + R4 §2.4），无任何运行时装载证据。不得写"16KB 已验证"，只能写"16KB × x86_64 已验证；16KB × arm64 UNVERIFIED"。

**结论 D（真机子句）——只能 UNVERIFIED：**

> 契约 `visual-generation-contract.md:22` 的"16 KB **真机**加载"子句 UNVERIFIED：ps16k 为模拟器环境，无 Play 安装链路/真机兼容模式判定/OEM linker 差异。闭合需要 arm64 真机开 16KB 启动（Pixel 8/8 Pro/8a ≥ Android 15 QPR1；9/9 Pro/9 Pro XL ≥ QPR2；9a ≥ Android 16）或主会话显式裁定模拟器证据等价并落记录。

**结论 E（性能/内存外推）——只能 UNVERIFIED：**

> arm64 上的 P2 耗时与 OOM 行为 UNVERIFIED（KD-70 复核 #6-②：慢速/arm64 设备存在先落 `PHASE_TIMEOUT` 而非 OOM 的风险，未测）；x86_64 数字（98 ms 级）不得外推为 arm64 结论。

### 4.3 门 ↔ 证据 ↔ 缺口映射表

| 门/条款 | ps16k x86_64 证据能覆盖到 | 剩余缺口 | 闭合所需 |
|---|---|---|---|
| 16KB 装载门（NativeLibraryPageSize 3 例） | 全部（x86_64 切片） | 无（x86_64 轴） | — |
| KD-70 P2 reopen condition | 16KB × x86_64 一次 | arm64 一次（R2 §5 口径的另一半） | ARM64 宿主模拟器或 arm64 真机（§2.3 a/b/c） |
| 契约 `:22` "16 KB 真机加载" | 0（模拟器≠真机） | 全部 | arm64 真机 16KB 启动，或主会话等价裁定 |
| 契约 `:22` "API 23/arm64 运行时" | 0 | 全部 | arm64 运行时环境（API 23 覆盖还需旧 API 真机/宿主，本轮未研究，**待验证**） |
| Play 16KB 合规（targetSdk 35+，2027-02-01 更新硬线） | 静态全 PASS（AAR/zipalign/readelf）+ x86_64 运行时 | 64 位真机运行时面（Play 意义上的"64-bit devices"实际=arm64） | 同上 b/c 路径 |

## 5. 本机资产变更登记（对齐 ps16k 可行性研究 §0-5 的先例；未做任何删除）

本轮新增磁盘资产（仓库零改动，本文档除外）：

1. `C:\Android\Sdk\system-images\android-36\google_apis_ps16k\arm64-v8a\`（4.3 GB，r07）——**预置待用**：若未来有 ARM64 宿主/新 emulator 版本支持跨 ISA，`ps16k_arm64` AVD + 镜像即可一条命令重试（§2.1 探针 4 命令）。
2. AVD `ps16k_arm64`（`~/.android/avd/ps16k_arm64.avd` + `.ini`）。

移除命令（由主会话裁定是否执行，本轨未删）：

```
rm -rf ~/.android/avd/ps16k_arm64.avd ~/.android/avd/ps16k_arm64.ini
rm -rf /c/Android/Sdk/system-images/android-36/google_apis_ps16k/arm64-v8a
```

既有资产复核（未受本轮影响）：4 台他线模拟器收尾时原样在线（`adb devices`：5554/5556/5562/5584）；`ps16k_x86_64`（emulator-5556）`getconf PAGE_SIZE`=16384 在线可用。

## 6. 未验证 / 待验证项（如实登记）

1. **arm64 运行时证据**：本机不可能产生（§2 定论）；本文没有任何 arm64 执行结果可报告。
2. **TCG 跨 ISA 耗时数字（10–100×/小时级）**：社区来源，本机无执行路径实测 → **待验证**；仅作 ARM 宿主/云路线的规划参考。
3. **云真机（Samsung Remote Test Lab 等）的 16KB 机型与配额**：官方页列名该环境但未逐家核实 → **待验证**。
4. **模拟器 16KB 证据 ≡ 真机装载证据的等价裁定**：本文只给出差异清单（§3.5②），裁定权在主会话。
5. **新 emulator 版本是否会支持 x86_64 宿主跑 arm64 镜像**：发行说明与 FATAL 文案（"System image must match the host architecture"）均为现行口径，未来版本未跟踪；若出现 ARM 宿主或版本变更，用 §5 预置资产一条命令复测。
6. **API 23 arm64（minSdk 23 下限）运行时面**：完全未研究（超出本轮范围，已在映射表标全缺）。
7. 本轮未运行 Gradle/JVM/仪器化/设备用例（P2 的 ps16k 复跑属设备轨）；§3.2 命令块未执行，判据与预期是**设备轨要执行的口径，不是已通过的结果**。

## 7. 来源

**官方（本轮抓取成功）**
- Support 16 KB page sizes（Play 2027-02-01 硬线、Pixel 16KB 启动机型清单、测试环境含 arm64/x86_64 模拟器镜像与 Cuttlefish、Samsung Remote Test Lab；本轮经镜像站抓取，返回泰语本地化内容，事实与 2026-10-25 英文抓取一致）：https://developer.android.google.cn/guide/practices/page-sizes （主站同文 https://developer.android.com/guide/practices/page-sizes ）
- Android Emulator 发行说明（30.0.26：arm64 系统镜像经 KVM 只在 arm64 宿主；30.0.0：x86_64 镜像内 ARM 为应用级翻译）：https://developer.android.com/studio/releases/emulator

**本机一手（命令与输出见 §1.1/§2.1/§5）**
- `sdkmanager --list`（arm64 ps16k 15 行）、`SdkManagerCli` 下载（exit 0，4.3 GB r07）、`AvdManagerCli create avd`（exit 0）、`emulator -avd ps16k_arm64`（FATAL 原文）、`adb devices`/`emu avd name`/`getconf PAGE_SIZE`（5556 在线复核）、python zipfile APK ABI 盘点、qemu 二进制存在性检查

**仓内交叉引用**
- `docs/research/2026-10-25-ps16k-emulator-feasibility.md`（ps16k 机制与命令块；R3 运行时研究）
- `docs/research/2026-10-25-16kb-arm64-api23-build.md`（静态判据/官方豁免/AAR 四 ABI 实测；R4 构建/对齐面）
- `docs/known-defects.md:2682-2710`（KD-70 全史与批次 24 E-02 P2 绿判据）、`:2705`（98 ms 与 PHASE_TIMEOUT 风险）
- `docs/research/2026-10-26-batch-24-record.md:147/:158`（批次 24 已在 ps16k 跑过 16KB 3/3 的设备证据）
- `docs/current/visual-generation-contract.md:22`（启用前门三子句）
- `core/data/src/androidTest/.../NativeLibraryPageSizeInstrumentedTest.kt:32/:38/:50`（16KB 门三例）
- `core/data/src/androidTest/.../QuickJsVisualExecutionEngineInstrumentedTest.kt:300-330`（P2 断言）
- `.zcode/plans/plan-2026-10-27-batch-25-race-warmup-queue.md`（本批任务书）
- `docs/research/2026-10-27-batch-25-baseline.md:394-436`（ps16k 在线现状与 arm64 缺口登记）

**社区旁证（多源一致，仅佐证）**
- Stack Overflow 75188975、google/android-emulator-m1-preview#34、ReactiveCircus/android-emulator-runner#377（x86_64 宿主跑 arm64 镜像失败形态）
- Linaro QEMU wiki "Run Android"（"I have not been able to launch any aarch64 vm from a Linux/Windows x64 host"）、Fedora wiki Architectures/AArch64/Running_Android_in_QEMU_on_aarch64、Stack Overflow 79790166（独立 qemu/Cuttlefish 路线的宿主要求）
