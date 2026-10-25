# ps16k 模拟器可行性研究（R3 → T6 设备轨）

- 日期：2026-10-25（批次 21，R3 研究线）
- 结论消费者：T6（ps16k 运行时装载验证）、主会话门禁编排、契约 `visual-generation-contract.md:22` RELRO 判据的运行时面
- 本文所有"实测"均在 2026-10-25 深夜于本机（Windows/Git Bash，SDK=`C:\Android\Sdk`）本轮亲自执行，命令可原样复跑；与 R4 静态研究（`2026-10-25-16kb-arm64-api23-build.md`，对齐判据/AAR 实测）互补，本文只负责**运行时/模拟器面**。
- 共享环境注意：本轮开工时他线已有 3 台模拟器在飞（emulator-5554/5562/5584，均为 android-34 google_apis）。本线全程用 `-port 5606` 隔离、按 serial 操作、收尾只杀自己一台——他线三台实测未受影响（收尾后 `adb devices` 复核仍在）。

## 0. 结论摘要（TL;DR）

1. **可行性判定：可行，且已全链路实测跑通**（非纸面推断）：下载 ps16k 镜像 → 建 AVD → 80 秒启动 → `getconf PAGE_SIZE` = **16384** → 192 MB APK 安装 Success → 应用启动、UI 渲染、导航、photo picker 选图回传全流程正常 → **logcat 零 linker/dlopen/RELRO/页大小错误**。T6 可直接复用本线已落地的镜像与 AVD（`ps16k_x86_64`），一条命令即可重启（§3）。
2. **16KB 页模式的机制（ask 第一问的三答）**：
   - **系统镜像包名**：页大小由 ps16k 专用系统镜像变体决定，包名形如 `system-images;android-36;google_apis_ps16k;x86_64`（tag=`google_apis_ps16k`，描述"16 KB Page Size … System Image"）。本机 `sdkmanager --list` 实测 **50 条 ps16k 条目**（API 35→37.2-beta2 全有，双 flavor 双架构，§1.1）。
   - **AVD 配置页大小键：不存在**。新建 AVD 的 `config.ini` 实测无任何 hw.* 页大小键；页大小来源是 `image.sysdir.1` 指向的 ps16k 镜像目录；仅有的两处 "page" 字样是 `tag.id=page_size_16kb` / `tag.display=16 KB Page Size`——**元数据，非功能键**（§1.2）。
   - **emulator qemu 页参数：不存在**。`emulator -help` grep `page|16k` 零命中（exit=1）；官方文档（page-sizes 页）未列任何页相关命令行旗标。页大小烘焙在镜像内核里：镜像文件 `kernel_cmdline.txt` 实测含 **`page_shift=14`**（2¹⁴=16384）（§1.3）。
3. **推荐设备轨镜像 = `system-images;android-36;google_apis_ps16k;x86_64`**（rev 7，已装本机，4.3 GB 未压缩）：与 targetSdk 36 同代、google_apis 免账号、与生产 abiFilters 的 x86_64 一致（APK 实含 `lib/x86_64/*.so`）。
4. **本轮观测边界（如实）**：装载验证覆盖"安装 + 进程启动 + 全 UI 流零 linker 错误"。APK 自带 6 库为**懒加载**（应用自述"配置好模型后，拍照题图才会交给模型整理"——未配模型不拉 mlkit/LiteRT；DataStore 计数器库在走到的 UI 路径也未触发），故**库级 dlopen 时刻未观测到**；静态合规 R4 已判全 PASS，动态面留 T6 按本文 §3 命令块执行（待 T3 出含 `libquickjs.so` 的包后同块复跑）。
5. 本线新增磁盘资产（仓库零代码改动，本文档除外）：`C:\Android\Sdk\system-images\android-36\`（4.3 GB）+ AVD `ps16k_x86_64`（其数据分区内含测试图 `ps16k_test.png`，无敏感性）。模拟器已关停。

## 1. 机制三问：镜像包名 / AVD 键 / qemu 参数（全部本机实测）

### 1.1 系统镜像包名（sdkmanager 只读目录实测）

命令（Git Bash 直调 .bat 会报参数错乱，改 java 直调，见 §2 注 1）：

```
"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe" \
  -Dcom.android.sdklib.toolsdir=C:\Android\Sdk\cmdline-tools\latest\bin\.. \
  -classpath "C:\Android\Sdk\cmdline-tools\latest\lib\*" \
  com.android.sdklib.tool.sdkmanager.SdkManagerCli --list
→ exit=0；grep -c "ps16k" = 50
```

android-36 目标行（原文摘录）：

```
system-images;android-36;google_apis_ps16k;x86_64    | 7 | 16 KB Page Size Google APIs Intel x86_64 Atom System Image
system-images;android-36;google_apis_ps16k;arm64-v8a | 7 | 16 KB Page Size Google APIs ARM 64 v8a System Image
system-images;android-36;google_apis_playstore_ps16k;x86_64   | 7 | （同构 Play 版）
```

ps16k 全集（本机目录实测）：API 层 `android-35`（rev 5）、`android-36`（rev 7）、`android-36.1`（rev 4）、`android-37.0`（rev 8）、`android-37.1`（rev 9）、`android-37.2-beta1/-beta2`；flavor `google_apis_ps16k` / `google_apis_playstore_ps16k`，另有 `android-36;google-tv-ps16k`（Google TV）；架构 arm64-v8a / x86_64 双全。

已装清单对照（`--list_installed` + 文件系统双证）：安装前本机仅 `system-images;android-34;google_apis;x86_64`（4KB，即现有 3 AVD 的镜像）→ **ps16k 需下载**，本轮已装 `android-36/google_apis_ps16k/x86_64`（4.3 GB，含 `kernel-ranchu`/`system.img` 等）。

### 1.2 AVD 配置里的页大小键：不存在（config.ini 实测）

`avdmanager create avd -n ps16k_x86_64 -k "system-images;android-36;google_apis_ps16k;x86_64" -d pixel_7` 生成的 `~/.android/avd/ps16k_x86_64.avd/config.ini` 关键行（全文已核）：

```
image.sysdir.1=system-images\android-36\google_apis_ps16k\x86_64\   ← 页大小来源：镜像目录
tag.id=page_size_16kb
tag.display=16 KB Page Size                                          ← 仅这 2 处含 "page"，是 tag 元数据
abi.type=x86_64 ; hw.cpu.arch=x86_64 ; hw.device.name=pixel_7 ; hw.ramSize=1536M
（grep -ci "page" = 2，即上述 tag 两行；无任何 hw.* 页大小键）
```

官方 page-sizes 文档同样未列任何 AVD 页配置（§6 来源）。

### 1.3 emulator 的 qemu 页参数：不存在（-help 实测 + 镜像内核证据）

```
C:\Android\Sdk\emulator\emulator.exe -help | grep -i "page\|16k"
→ 零命中（grep exit=1）        # emulator 37.1.11.0 (build_id 15917651)
```

镜像侧内核证据（本机文件直读）：

```
C:\Android\Sdk\system-images\android-36\google_apis_ps16k\x86_64\kernel_cmdline.txt
→ 含 "clocksource=pit page_shift=14"        # 2^14 = 16384 字节
```

结论：**不需要也不存在页相关 qemu 参数/AVD 键——选对 ps16k 镜像即决定页大小**。官方文档同样只以"系统镜像"承载 16KB 模拟（SDK Manager 里名为 "…16 KB Page Size…System Image"），无任何 flag/键。

### 1.4 设备上的页大小断言（运行时实测）

| 断言 | 命令 | 实测输出 |
|---|---|---|
| libc 页大小 | `adb shell getconf PAGE_SIZE` | **16384** |
| 系统 prop | `adb shell getprop ro.product.page_size` | 16384 |
| 内核启动参数 | `adb shell getprop ro.boot.hardware.cpu.pagesize` | 16384 |
| 16K 机型标识 | fingerprint | `google/sdk_gphone16k_x86_64/emu64xa16k:16/BE2A.250530.026.F3/…`（SDK 36，Android 16） |

旁注：镜像另有 `ro.product.build.16k_page.enabled=false`、`ro.product.build.no_bionic_page_size_macro=true`——这是镜像构建配置（对齐 NDK r27+ "不定义 PAGE_SIZE 宏"的官方指引），**不代表运行时非 16KB**（getconf/props 三处 16384 为准）。

### 1.5 16KB 兼容模式（backcompat，官方口径，本镜像原生 16KB 用不到）

官方 page-sizes 文档：4KB 对齐 app 在 16KB 内核上可进兼容模式；开关 props `bionic.linker.16kb.app_compat.enabled` / `pm.16kb.app_compat.disabled`（Android 17 增加 `fatal` 值直接杀不合规二进制）；per-app 清单项 `android:pageSizeCompat`。本项目 APK 全 64 位且静态合规（R4），**不依赖兼容模式**；props 留作 T6 排障时的对照开关。

## 2. 实测全记录（时间线，2026-10-25 23:0x–23:5x）

1. **目录核对**：`SdkManagerCli --list` exit=0，50 条 ps16k（§1.1）。已装镜像=android-34 一套。
2. **下载安装**（后台任务）：`SdkManagerCli "system-images;android-36;google_apis_ps16k;x86_64"` → Unzipping 100% → 落盘 `system-images\android-36\`（`du -sh` = 4.3G）。许可证沿用本机已接受的 android-sdk-license，无交互。
3. **建 AVD**：`AvdManagerCli create avd -n ps16k_x86_64 -k 'system-images;android-36;google_apis_ps16k;x86_64' -d pixel_7` → 输出 `Auto-selecting single ABI x86_64`，无报错；`~/.android/avd/` 出现 `ps16k_x86_64.avd` + `ps16k_x86_64.ini`；config.ini 证据见 §1.2。
4. **启动**：`emulator -avd ps16k_x86_64 -port 5606 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect`（后台）→ 轮询 `sys.boot_completed` → **约 80 秒 BOOTED**；`adb devices` 四台并存（5554/5562/5584 = 他线原样 + 5606 = 本线）。
5. **页断言**：`getconf PAGE_SIZE` → `16384`；props 见 §1.4。
6. **装 APK**：`adb install -r app-localFirst-debug.apk`（192,101,469 B，今天 23:14 构建；`output-metadata.json`：applicationId=`com.tingyun.smartmistakebook.localfirst`，versionCode 2；实测含 `lib/arm64-v8a/*` + `lib/x86_64/*` 共 12 个 .so，无 quickjs——T3 未入包）→ **"Performing Streamed Install / Success"**。
7. **启动观察**：`logcat -c` → `monkey -p … -c android.intent.category.LAUNCHER 1` → 12 秒后 `pidof` = 4518（存活）；`logcat -d | grep -iE "linker|dlopen|CANNOT LINK|failed to map|RELRO|SIGSEGV"` → **零命中**。截图确认首页完整渲染（"下午好，同学"/今日复习/底部四 Tab）。
8. **E2E 录入流**：推测试图（`adb push … /sdcard/Pictures/ps16k_test.png` + MEDIA_SCANNER 广播，MediaStore 查得）→ 点"录入第一道题"→"相册"→ PhotoPickerActivity 选中图片 → 焦点回 MainActivity → 应用"整理题目"页出现"本题原图·1 页"缩略图。**全程零崩溃零 linker 错误**。
9. **懒加载边界（如实登记）**：应用明示"配置好模型后，拍照题图才会交给模型整理"→ 未配模型不拉 mlkit/LiteRT；`adb root` 后读 `/proc/4518/maps`，进程 1195 个 .so 映射全部来自 /system 与 APEX，`/data/app` 下 **0 个自带 .so**——故库级 dlopen 未在本轮观测（§0-4）。
10. **收尾**：`adb -s emulator-5606 emu kill` → 他线三台原样在线（复核）；本线模拟器与 AVD 停置备用。

环境坑（复跑必读）：

- **注 1**：Git Bash 直调 `sdkmanager.bat`/`avdmanager.bat`（`cmd //c "…bat --list"` 等写法）报 `error: too many arguments. Expected 1 argument but got 2.` → 一律 java 直调（命令见 §3）。avdmanager 的 toolsdir 属性名是 `com.android.sdkmanager.toolsdir`（`avdmanager.bat:17`），与 sdkmanager 的 `com.android.sdklib.toolsdir` 不同，直调时别混。
- **注 2**：Git Bash 会把 `/sdcard/...` 改写成 `C:/Program Files/Git/sdcard/...`（adb push 实测踩中）→ 命令块统一前置 `export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*"`。
- **注 3**：`--list_installed` 也会拉远端目录（非纯本地）；纯本地证据用 `ls C:\Android\Sdk\system-images`。

## 3. T6 复跑命令块（可复跑；R3 本轮已按此块走通 1–5 步与 7 步）

```bash
# ===== ps16k 运行时验证（Git Bash；SDK=C:\Android\Sdk；共享宿主机注意端口与 serial）=====
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*"
J="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/java.exe"
A=/c/Android/Sdk/platform-tools/adb.exe

# 1) 幂等装镜像（已装则秒回；本轮已装，此步通常免跑）
"$J" '-Dcom.android.sdklib.toolsdir=C:\Android\Sdk\cmdline-tools\latest\bin\..' \
  -classpath 'C:\Android\Sdk\cmdline-tools\latest\lib\*' \
  com.android.sdklib.tool.sdkmanager.SdkManagerCli "system-images;android-36;google_apis_ps16k;x86_64"

# 2) 幂等建 AVD（已存在会报错，忽略或先删 ~/.android/avd/ps16k_x86_64*；本轮已建）
echo no | "$J" '-Dcom.android.sdkmanager.toolsdir=C:\Android\Sdk\cmdline-tools\latest\bin\..' \
  -classpath 'C:\Android\Sdk\cmdline-tools\latest\lib\avdmanager-classpath.jar' \
  com.android.sdklib.tool.AvdManagerCli create avd \
  -n ps16k_x86_64 -k 'system-images;android-36;google_apis_ps16k;x86_64' -d pixel_7

# 3) 启动（端口选空闲偶数口；先 adb devices 避开他线在飞实例；本轮用 5606）
MSYS_NO_PATHCONV=1 /c/Android/Sdk/emulator/emulator.exe -avd ps16k_x86_64 -port 5606 \
  -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect &

# 4) 等启动 + 页断言（期望均为 16384）
"$A" -s emulator-5606 wait-for-device
until [ "$("$A" -s emulator-5606 shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 5; done
"$A" -s emulator-5606 shell getconf PAGE_SIZE              # 16384
"$A" -s emulator-5606 shell getprop ro.product.page_size   # 16384

# 5) 装 APK + 启动 + linker/RELRO 观察（T3 出包后替换 APK 路径即可）
"$A" -s emulator-5606 install -r "D:\\smart mistake book\\app\\build\\outputs\\apk\\localFirst\\debug\\app-localFirst-debug.apk"
"$A" -s emulator-5606 logcat -c
"$A" -s emulator-5606 shell monkey -p com.tingyun.smartmistakebook.localfirst -c android.intent.category.LAUNCHER 1
#    （等价精确启动：am start -n com.tingyun.smartmistakebook.localfirst/com.tingyun.smartmistakebook.MainActivity）
sleep 12
"$A" -s emulator-5606 shell pidof com.tingyun.smartmistakebook.localfirst     # 非空 = 启动存活
"$A" -s emulator-5606 logcat -d | grep -iE "linker|dlopen|CANNOT LINK|failed to map|RELRO|SIGSEGV"
#    期望：零命中。本轮实测：零命中（含 QuickJS 缺席的当前包）

# 6) 库级装载证据（懒加载库需先在 UI 触发模型任务；T3 出含 libquickjs.so 的包后必做）
"$A" -s emulator-5606 root     # userdebug 模拟器可 root；只对本 serial
P=$("$A" -s emulator-5606 shell pidof com.tingyun.smartmistakebook.localfirst | tr -d '\r')
"$A" -s emulator-5606 shell "cat /proc/$P/maps | grep '/data/app' | grep '\.so' | awk '{print \$6}' | sort -u"
#    期望列出 libquickjs.so / libLiteRt.so 等 = 16KB 内核上真实 dlopen 成功；
#    同时 logcat 无 "unsupported RELRO"/"failed to map" = 契约 :22 RELRO 判据的运行时面通过

# 7) 收尾：只杀自己这一台（严禁 kill 他线模拟器）
"$A" -s emulator-5606 emu kill
```

断言清单（T6 验收口径）：① `getconf PAGE_SIZE`=16384；② `install` → Success；③ 启动后 `pidof` 非空；④ logcat linker/RELRO grep 零命中；⑤（T3 出包后）maps 列出 `libquickjs.so`。①–④ 本轮已实测通过（⑤ 待含 QuickJS 的包）。

## 4. 判定与移交

- **R3 判定：ps16k 模拟器路线可行（A 级：镜像可得且已装、可启动、页断言通过；B 级：APK 安装/启动/全 UI 流冒烟通过、零 linker 错误；C 级：库级 dlopen 未观测——懒加载未触发，留 T6）。** 不需要启用替代路径（Android 15 QPR `android-35;google_apis_ps16k` 同构可用，目录实测在列，仅作备选）。
- **T6 移交**：镜像 + AVD（`ps16k_x86_64`）已就位，§3 命令块从第 3 步起跑即可；QuickJS 观测点 = 第 6 步（依赖 T3 出包，本轮 APK 无 quickjs）。
- **契约衔接**：`visual-generation-contract.md:22` 的 RELRO 错误判据按官方豁免改写（主会话裁定 2）；本文提供其运行时观测方法（第 5 步 logcat grep + 第 6 步 maps），静态判据已在 R4 文档闭环。

## 5. 未验证 / 待验证项（如实登记）

1. **库级 dlopen 与 RELRO mprotect 的实际运行行为**：APK 自带 6 库与（未来的）`libquickjs.so` 在 16KB 内核上的加载时刻未观测（懒加载未触发；模型任务/模型配置流程未走）。静态判据 R4 全 PASS ≠ 动态装载已证明——T6 按 §3 第 6 步收口。
2. **QuickJS 装载**：待 T3 产出含 `libquickjs.so` 的 APK 后同块复跑（本轮 APK 实测无 quickjs）。
3. **官方"模拟器最低版本 35.1.5"之说未逐字复核**（主站超时，镜像站摘要未含该句）；本机 emulator 37.1.11 实测通过，不影响判定，但引用该版本线时需回核原文。
4. **arm64-v8a ps16k 镜像与系统相机（拍照）路径未测**：x86_64 足够设备轨（APK 含 x86_64 库）；相机路径本轮以 photo picker 替代完成 E2E。
5. **developer.android.com 主站直连超时**（WebFetch connect timeout 10s，两端口均超时），官方内容经 **developer.android.google.cn**（Google 官方镜像站）抓取成功；webReader 通道对该页 -500（与 R4 记录一致）。镜像站与主站为同源文档，引用时标注通道。

## 6. 来源

**官方（本轮抓取成功）**
- Support 16 KB page sizes（官方镜像站全文：SDK Manager 16KB 镜像入口、`getconf PAGE_SIZE`=16384 断言、兼容模式 props `bionic.linker.16kb.app_compat.enabled`/`pm.16kb.app_compat.disabled`、`android:pageSizeCompat`）：https://developer.android.google.cn/guide/practices/page-sizes （主站同文 https://developer.android.com/guide/practices/page-sizes ，本轮直连超时）
- sdkmanager 远端目录（ps16k 50 条，含包名/版本/描述，本机 `SdkManagerCli --list` 实测，XML 版本警告 v4 见 §2 注 1 场景，不影响目录完整获取）

**本机一手（命令与输出见 §1/§2）**
- `sdkmanager --list` / `--list_installed`、`avdmanager create avd` 产物 config.ini、`emulator -help` grep、镜像 `kernel_cmdline.txt`（page_shift=14）、`adb` 全链路（PAGE_SIZE/props/install/monkey/logcat/maps/截图）

**仓内交叉引用**
- `docs/research/2026-10-25-16kb-arm64-api23-build.md`（R4：静态判据、AAR 四 ABI 实测、§6.3 初版命令块——本文为其运行时闭环与修订：实测证明包名/命令可行，并补齐 Git Bash 环境坑与懒加载边界）
