# Android 15+ 16KB 页大小：官方测试方法、ps16k 模拟器支持与 native 依赖检查清单

- 日期：2026-11-03（批次 41，R4 研究线）
- 计划落点：`plan-2026-11-03-batch-41.md` §1 R4；消费者 = WS9 item 52（ps16k 运行）/ item 53（arm64 可用性实测）
- 前序一手材料（仓内，优先于外部检索）：
  - `docs/research/2026-10-25-16kb-arm64-api23-build.md`（静态判据、APK/AAR 实测）
  - `docs/research/2026-10-25-ps16k-emulator-feasibility.md`（同机 ps16k 运行时全链路实测）
  - `docs/research/2026-11-03-batch-41-baseline.md` §5（AVD/设备现状）
- 本文标记约定：**【本次实测】** = 2026-11-03 本轮在本机跑过并读到输出；**【前序实测】** = 2026-10-25 仓内文档记录的同机结果，本轮未复跑；**【官方】** = developer.android.com/google.cn 原文；**【单一来源/待验证】** = 未取得第二独立来源。

---

## 0. 结论摘要（TL;DR）

1. **官方硬线**：targetSdk ≥ 35 的应用，在 **64 位设备**上必须支持 16KB 页；**2027-02-01** 起不支持的更新无法在 Google Play 发布。构建侧最低组合 = **AGP ≥ 8.5.1** + **NDK r28+**；ZIP 用 `zipalign -P 16`，ELF 用 `p_align ≥ 2**14` + RELRO 判据。
2. **模拟器是官方指定的测试路径**：Android 15+ 的 `_ps16k` 系统镜像（SDK Manager 中名含 "16 KB Page Size"）。本机已装 **`system-images;android-36;google_apis_ps16k;x86_64` 与 `…;arm64-v8a`**，并已有 **`ps16k_x86_64` / `ps16k_arm64`** 两个 AVD（【本次实测】目录与 `config.ini`）。
3. **本机可跑的 16KB 路径是 `ps16k_x86_64`**：`page_shift=14` 烘在镜像内核命令行里（【本次实测】`kernel_cmdline.txt`），启动后 `getconf PAGE_SIZE` 应为 **16384**（【前序实测】同机同 AVD 已得 16384）。`ps16k_arm64` 在 **x86_64 宿主上拿不到 VM 加速**，现代 emulator 还可能直接 `PANIC: QEMU2 emulator does not support arm64 CPU architecture`（【单一来源/待验证】，见 §3.3）；因此 **arm64 是发布/真机覆盖要求，不是本机模拟器路径**。
4. **当前树静态面已合规（【本次实测】）**：
   - `zipalign -v -c -P 16 4 app-localFirst-debug.apk` → `Verification successful`，exit 0；
   - 成品 APK 只有 **14 个 `.so`（arm64-v8a 7 + x86_64 7）**，全部 `STORED`，`data_offset % 16384 == 0`；
   - `merged_native_libs`（打包前中间产物）25 个 `.so`：**23 PASS / 2 FAIL**，失败仅 32 位 ML Kit（`armeabi-v7a` / `x86`），已被 `abiFilters` 裁掉、**不进 APK**。
5. **本轮未执行**（不得写成通过）：ps16k 模拟器启动/安装、`ps16k_arm64` 启动、AAB/bundletool、`llvm-objdump`/`llvm-readelf`（本机无 NDK）、真机 16KB。理由与替代证据见 §7。

---

## 1. 官方要求（2026-11-03 抓取）

### 1.1 Play 政策与适用范围

来源：`https://developer.android.com/guide/practices/page-sizes`（主站本轮直连超时；同文经官方镜像站 `https://developer.android.google.cn/guide/practices/page-sizes` 抓取成功，下同）。

- 适用对象：**targetSdk ≥ 35（Android 15）** 的应用，且限于 **64 位设备**（arm64-v8a / x86_64）。
- 截止日：**2027-02-01** 起，不兼容 16KB 的 **更新**无法发布。官方原文：
  > "Starting February 1, 2027, if your app updates don't support 16 KB memory page sizes, you won't be able to release these updates."
- 注意：本页现行口径是 **2027-02-01**；社区文章仍写 2025-11-01，属旧口径，**不采纳**（见 §8）。

### 1.2 项目命中面

| 项 | 值 | 出处 |
|---|---|---|
| targetSdk | 36 | `app/build.gradle.kts:29` |
| minSdk | 23 | `app/build.gradle.kts:28` |
| compileSdk / buildTools | 37 / 37.0.0 | `app/build.gradle.kts:16-17` |
| AGP | 9.3.0 | `gradle/libs.versions.toml:2` |
| 打包 ABI | `arm64-v8a` + `x86_64` | `app/build.gradle.kts:45-47` |

targetSdk 36 ⇒ 命中 Play 要求；AGP 9.3.0 > 8.5.1 ⇒ ZIP 层默认合规（§6 已实测）。

---

## 2. 官方测试方法（三条命令 + 一条运行时断言）

### 2.1 ZIP 层（APK）

官方命令（**build-tools ≥ 35.0.0**，官方原文 "Android SDK Build-Tools version 35.0.0 or higher"）：

```bash
SDK_ROOT/build-tools/35.0.0/zipalign -v -c -P 16 4 APK_NAME.apk
# 通过标志：输出末行 "Verification successful"，exit=0
```

- `-P 16`：要求**未压缩** `.so` 条目的 ZIP 偏移按 16KB 对齐。
- 判据：`.so` 必须 `STORED`（压缩式打包不适用该判据）。
- 本机 build-tools 有 34/36/37，无 35.0.0；**37.0.0 等效可用**（【本次实测】exit 0）。

### 2.2 ELF 层（每个 `.so`）

官方两条命令（需 NDK/LLVM 工具）：

```bash
llvm-objdump -p SHARED_OBJECT_FILE.so | grep LOAD
llvm-readelf -Wl SHARED_OBJECT_FILE.so | grep 'RELRO\|Type'
```

判据（官方原文要点）：

1. 每个 `PT_LOAD` 的 `p_align` **≥ 2^14（16384）**；出现 `2^13` / `2^12` 即不合规。
2. **RELRO**：`(VirtAddr + MemSiz) % 0x4000 == 0` 通过。
3. **RELRO 豁免**：若 `GNU_RELRO` 独占一个 `LOAD` 段、且余数页内没有可写数据，则"Although 0x1000 isn't zero, this `.so` file is still 16KB compliant."。
   - 违规形态 = RELRO 终点后**同 16KB 页内**出现可写段；运行时后果是 linker 的 RELRO `mprotect` 把整页变只读，随后写 `.data` → SIGSEGV。
4. 官方未在本页给出"仅 64 位"的 ELF 判据差异；但 Play 要求只针对 64 位设备，32 位 `.so` 的处置见 §5.3。

### 2.3 NDK 版本线

- **r28+**：默认产出 16KB 对齐 ELF。
- **r27 及以下**：必须显式加
  `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`。
- `PAGE_SIZE` 宏自 r27 起在 16KB 构建下不再定义 → 运行时改用 `sysconf(_SC_PAGESIZE)` / `getpagesize()`。

### 2.4 运行时（模拟器/真机）

```bash
adb shell getconf PAGE_SIZE
# 通过判据：16384
```

- 这是**唯一能证明"真的以 16KB 页运行"**的断言；静态对齐 ≠ 运行通过。
- 辅助：`adb shell getprop ro.product.page_size`。**注意**：本机 API 34 的 4KB 模拟器该 prop 为空（【本次实测】），所以**不能只靠 prop**，以 `getconf` 为准。
- ps16k 镜像上【前序实测】三项均 16384：
  `getconf PAGE_SIZE` / `ro.product.page_size` / `ro.boot.hardware.cpu.pagesize`。

### 2.5 16KB 兼容模式（backcompat，排障用）

官方：4KB 对齐的应用在 16KB 内核上可进兼容模式；相关开关

- `bionic.linker.16kb.app_compat.enabled`
- `pm.16kb.app_compat.disabled`
- 清单属性 `android:pageSizeCompat`

本项目 64 位 `.so` 静态全 PASS，不需要依赖兼容模式；这些开关只作运行时对照/排障。官方未按 ABI 区分"32 位 4KB `.so` 是否会让整个应用进兼容模式" → **待验证**（本项目已用 `abiFilters` 裁掉 32 位，问题整体消失）。

---

## 3. 模拟器支持、ps16k AVD 与 arm64 要求

### 3.1 官方口径（模拟器节）

- 需要 **Android 15 或更高**的系统镜像。
- 镜像名（SDK Manager 显示）：
  - "Google APIs Experimental 16 KB Page Size **ARM 64 v8a** System Image"
  - "Google APIs Experimental 16 KB Page Size **Intel x86_64 Atom** System Image"
- 官方原文：**"For Pixel emulation: you will only need the ARM 64 v8a System Image."**
  - 这句是**按 Pixel 设备配置**说的（Pixel 真机是 arm64），**不是**说 x86_64 宿主也必须用 arm64 镜像。把它套到 x86_64 Windows 宿主会得到相反结论（§3.3）。
- **官方页本轮未给出最低 emulator 版本**。仓内前序文档提到的 "35.1.5" 之说未取得原文支持 → 不作为判据（§8 拒绝项）。

### 3.2 本机现状（【本次实测】）

命令与输出：

```bash
/c/Android/Sdk/emulator/emulator.exe -list-avds
→ ps16k_arm64
  ps16k_x86_64
  （另有 api23_smoke/api23_smoke32/api23_smoke64/audit_user_visible_20261016/kb_test_device/test_device）

ls /c/Android/Sdk/system-images/android-36/google_apis_ps16k/
→ arm64-v8a  x86_64
```

两个 AVD 的关键 `config.ini`：

| AVD | `abi.type` | `hw.cpu.arch` | `image.sysdir.1` | `tag.id` |
|---|---|---|---|---|
| `ps16k_x86_64` | `x86_64` | `x86_64` | `system-images\android-36\google_apis_ps16k\x86_64\` | `page_size_16kb` |
| `ps16k_arm64` | `arm64-v8a` | **`arm64`** | `system-images\android-36\google_apis_ps16k\arm64-v8a\` | `page_size_16kb` |

要点：

- AVD 的页大小**没有** `hw.*` 键；页大小由 `image.sysdir.1` 指向的 ps16k 镜像决定，`tag.id/tag.display` 只是元数据（与 `2026-10-25-ps16k-emulator-feasibility.md` §1.2 一致）。
- `ps16k_arm64` 的 `hw.cpu.arch=arm64`（不是常见的 `arm64-v8a`）是**观测到的异常点**，可能是 x86_64 宿主启动失败的相关因素；未启动验证，标为待验证。

镜像内核证据：

```bash
cat .../google_apis_ps16k/x86_64/kernel_cmdline.txt
→ 8250.nr_uarts=1 clocksource=pit page_shift=14      # 2^14 = 16384
cat .../google_apis_ps16k/arm64-v8a/kernel_cmdline.txt
→ 8250.nr_uarts=1                                    # 无 page_shift
```

- x86_64：`page_shift=14` 是 16KB 页的直接证据。
- arm64：**没有** `page_shift` 属预期——arm64 的页大小由内核编译配置（如 `CONFIG_ARM64_16K_PAGES`）决定，不走 cmdline。**这是推断，不是启动实测**；不得据此判 arm64 镜像非 16KB。

### 3.3 arm64 在 x86_64 宿主上的真实约束（关键）

- 官方加速文档（`https://developer.android.google.cn/studio/run/emulator-acceleration`，本轮抓取成功）：VM 加速要求 **宿主与客户机架构一致**；"ARM-based system images on Intel or AMD CPUs … can't use the VM acceleration"，只能逐块翻译机器码，"can be quite slow"。→ **arm64 AVD 在本机拿不到加速**。
- 更强的说法（**单一来源/待验证**）：
  - Google Issue Tracker #373790939（`https://issuetracker.google.com/issues/373790939`，本轮 WebFetch 超时，仅得搜索摘要）：emulator **35.2.10.0** 在 x86_64 宿主 + arm64-v8a 镜像上直接失败：`PANIC: QEMU2 emulator does not support arm64 CPU architecture`。
  - StackOverflow 74760054（WebFetch 403，仅摘要）同一 `PANIC` 文案。
- 结论：
  1. **本机 16KB 运行验证走 `ps16k_x86_64`**，它同样是真 16KB 内核，能验证 `.so` 对齐与运行时装载。
  2. **arm64（arm64-v8a）是发布覆盖要求**：Play 的 64 位设备包含 arm64；成品 APK 必须含 arm64-v8a `.so`（本项目已含）。
  3. 需要在 arm64 上做运行时验证时，应使用 **arm64 宿主机**（Apple Silicon / Linux arm64）或 arm64 真机；x86_64 Windows 不是可行路径。**本机 `ps16k_arm64` 的可启动性未实测**，命令与预期输出见 §4.3。

---

## 4. 本机可执行判据（命令 + 应看输出）

环境（Git Bash，避开路径改写）：

```bash
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*"
SDK=/c/Android/Sdk
A=$SDK/platform-tools/adb.exe
```

### 4.1 静态三连（【本次实测】全部通过）

**(1) ZIP 对齐**

```bash
$SDK/build-tools/37.0.0/zipalign.exe -v -c -P 16 4 \
  "app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk"
# 期望：末行 "Verification successful"；echo $? = 0
```

本轮输出：`Verification successful`，`exit=0`。

**(2) 成品 APK 的 `.so` 清单与偏移**

```bash
python - <<'PY'
import struct, zipfile
p = "app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk"
zf = zipfile.ZipFile(p)
for i in sorted(zf.infolist(), key=lambda x: x.filename):
    if not i.filename.startswith("lib/"): continue
    with open(p, "rb") as f:
        f.seek(i.header_offset); nl, el = struct.unpack_from("<HH", f.read(30), 26)
    off = i.header_offset + 30 + nl + el
    print(f"{i.filename:55s} {'STORED' if i.compress_type==0 else 'DEFLATE'} "
          f"off={off} mod16k={off%16384}")
PY
# 期望：只有目标 ABI；每行 STORED；mod16k=0
```

本轮输出摘要：14 条 = arm64-v8a 7 + x86_64 7，全部 `compress=0`（STORED），全部 `mod16k=0`。

**(3) ELF 对齐（本机无 NDK，用等价 Python 判据）**

> 本机 `find /c/Android/Sdk -name "llvm-objdump*"` 零命中、`C:\Android\Sdk\ndk` 不存在 → 官方 `llvm-objdump`/`llvm-readelf` **本机不可执行**。下列脚本按官方同一判据（`p_align ≥ 16384`；RELRO `end % 0x4000 == 0` 或独占 LOAD 豁免）解析程序头。

```bash
python - <<'PY'
import struct, glob, os
PT_LOAD, PT_GNU_RELRO = 1, 0x6474E552
root = "app/build/intermediates/merged_native_libs/localFirstDebug/mergeLocalFirstDebugNativeLibs/out/lib"
def check(path):
    d = open(path, "rb").read()
    if d[:4] != b"\x7fELF": return "NOT-ELF", []
    is64, le = d[4] == 2, d[5] == 1
    e = "<" if le else ">"
    if is64:
        po = struct.unpack_from(e+"Q", d, 0x20)[0]; ps = struct.unpack_from(e+"H", d, 0x36)[0]
        pn = struct.unpack_from(e+"H", d, 0x38)[0]; fmt = e+"IIQQQQQQ"
    else:
        po = struct.unpack_from(e+"I", d, 0x1C)[0]; ps = struct.unpack_from(e+"H", d, 0x2A)[0]
        pn = struct.unpack_from(e+"H", d, 0x2C)[0]; fmt = e+"IIIIIIII"
    loads, relros = [], []
    for i in range(pn):
        f = struct.unpack_from(fmt, d, po + i*ps)
        if is64: t, fl, off, va, _pa, _fs, msz, al = f
        else:    t, off, va, _pa, _fs, msz, fl, al = f
        if t == PT_LOAD: loads.append((va, msz, al, bool(fl & 2)))
        elif t == PT_GNU_RELRO: relros.append((va, msz))
    bad = []
    for i, (_va, _msz, al, _w) in enumerate(loads):
        if al < 16384: bad.append(f"LOAD#{i} p_align={al}")
    for va, msz in relros:
        rend = va + msz; rem = rend % 16384
        if rem == 0: continue
        page_end = (rend + 16383)//16384*16384
        clash = [hex(v) for (v, m, _a, w) in loads if w and v < page_end and v + m > rend]
        if clash: bad.append(f"RELRO end=0x{rend:x} mod={rem} writable={clash}")
    return ("PASS" if not bad else "FAIL"), bad
for p in sorted(glob.glob(root + "/*/*.so")):
    abi, name = os.path.basename(os.path.dirname(p)), os.path.basename(p)
    st, bad = check(p); print(f"{abi:14s}{name:42s}{st:6s}{'; '.join(bad)}")
PY
```

本轮输出：**25 个 `.so` → 23 PASS / 2 FAIL**。

- FAIL 仅 `armeabi-v7a/libmlkit_google_ocr_pipeline.so`（3 个 LOAD `p_align=4096`）
- 与 `x86/libmlkit_google_ocr_pipeline.so`（4 个 LOAD `p_align=4096` + RELRO 同页可写）。
- 二者都在 `abiFilters` 之外，**不进入成品 APK**（§4.1(2) 已证 APK 只有 64 位）。

### 4.2 运行时（`ps16k_x86_64`；【本轮未执行】，【前序实测】已通过）

```bash
# 1) 启动（先 adb devices 选空闲偶数端口；本机现有 5562/5566/5584/5596）
$SDK/emulator/emulator.exe -avd ps16k_x86_64 -port 5620 \
  -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect &

# 2) 等启动
$A -s emulator-5620 wait-for-device
until [ "$($A -s emulator-5620 shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 5; done

# 3) 页断言（核心判据）
$A -s emulator-5620 shell getconf PAGE_SIZE                 # 期望 16384
$A -s emulator-5620 shell getprop ro.product.page_size      # 期望 16384

# 4) 装包 + 启动 + linker 观察
$A -s emulator-5620 install -r \
  "app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk"   # 期望 Success
$A -s emulator-5620 logcat -c
$A -s emulator-5620 shell am start -n \
  com.tingyun.smartmistakebook.localfirst/com.tingyun.smartmistakebook.MainActivity
sleep 12
$A -s emulator-5620 shell pidof com.tingyun.smartmistakebook.localfirst   # 非空 = 存活
$A -s emulator-5620 logcat -d | grep -iE "linker|dlopen|CANNOT LINK|failed to map|RELRO|SIGSEGV"
# 期望：零命中

# 5) 库级装载证据（懒加载库需先在 UI 触发）
$A -s emulator-5620 root
P=$($A -s emulator-5620 shell pidof com.tingyun.smartmistakebook.localfirst | tr -d '\r')
$A -s emulator-5620 shell "cat /proc/$P/maps | grep '/data/app' | grep '\.so' | awk '{print \$6}' | sort -u"
# 期望：列出 libquickjs.so / libLiteRt.so / libmlkit_google_ocr_pipeline.so 等

# 6) 收尾：只杀自己这一台
$A -s emulator-5620 emu kill
```

【前序实测】2026-10-25 同机同 AVD：`getconf PAGE_SIZE=16384`、`install Success`、启动存活、`logcat` 零 linker/RELRO 命中；库级 dlopen 当时因懒加载未触发（APK 无 quickjs）。**当前 APK 已含 `libquickjs.so`（【本次实测】），第 5 步这次必须做。**

本轮不启动的理由：共享宿主机已有 4 台模拟器在飞，且批次 41 的 WS4/R5 依赖低负载窗；启动新模拟器会污染 loadavg 采样。**这不是"通过"，是"未执行"**（§7）。

### 4.3 `ps16k_arm64`（【本轮未执行】；预期失败）

```bash
$SDK/emulator/emulator.exe -avd ps16k_arm64 -port 5622 \
  -no-window -no-audio -no-boot-anim -no-snapshot
# 预期（据 Google Issue 373790939 / SO 74760054，单一来源待验证）：
#   PANIC: QEMU2 emulator does not support arm64 CPU architecture
# 若未 PANIC：会以纯翻译模式极慢启动（官方加速文档：无 VM 加速）；
#   启动后同样用 getconf PAGE_SIZE 断言 16384。
```

判读规则：**能在本机启动并 16384 = arm64 路径可用；PANIC = 本机不可用**，此时 arm64 覆盖改由真机/arm64 宿主机承担，不得写成"arm64 已验证"。

### 4.4 AAB 侧（未执行）

```bash
bundletool dump config --bundle=app.aab | grep alignment
# 官方口径：期望 PAGE_ALIGNMENT_16K
```

本机无 bundletool 产物/命令，**未执行**。

---

## 5. Native 依赖检查清单

### 5.1 通用清单（每条给出命令与通过判据）

| # | 检查 | 命令 | 通过判据 |
|---|---|---|---|
| 1 | 枚举成品 APK 的 `.so` | `unzip -l app.apk \| grep ' lib/'` | 只有预期 ABI；无意外 32 位库 |
| 2 | ZIP 16KB 对齐 | `zipalign -v -c -P 16 4 app.apk` | `Verification successful`，exit 0 |
| 3 | `.so` 未压缩 | 条目 `compress_type == 0` | 全部 STORED |
| 4 | `.so` ZIP 偏移 | `data_offset % 16384` | == 0 |
| 5 | ELF `PT_LOAD` | `llvm-objdump -p x.so \| grep LOAD` | 每个 `p_align ≥ 2**14` |
| 6 | RELRO | `llvm-readelf -Wl x.so \| grep 'RELRO\|Type'` | `(VirtAddr+MemSiz)%0x4000==0` **或** 独占 LOAD 豁免 |
| 7 | 32 位库处置 | 同 5/6 跑 `armeabi-v7a`/`x86` | 合规，或用 `abiFilters` 裁掉 |
| 8 | NDK 版本 | `ndkVersion` / `$NDK/source.properties` | r28+；r27- 已加两个 `-Wl,-z,…page-size=16384` |
| 9 | 页大小宏 | `grep -rn "PAGE_SIZE" jni/` | 不用编译期 `PAGE_SIZE`；改 `sysconf(_SC_PAGESIZE)` |
| 10 | 4KB 硬编码 | `grep -rnE "4096\|0x1000" jni/` | 无按页对齐/偏移的 4096 假设 |
| 11 | 第三方 SDK | 查 Maven/GitHub 最新版与 release notes | 已最新；无修复版则裁 ABI 或接受（记录） |
| 12 | 运行时 | ps16k AVD：`getconf PAGE_SIZE` + install + launch + logcat + `/proc/pid/maps` | 16384；Success；零 linker/RELRO；列出自带 `.so` |

### 5.2 本项目矩阵（【本次实测】）

**成品 APK（`localFirstDebug`，2026-11-02 13:57）**

- 14 个 `.so`：`arm64-v8a` × 7 + `x86_64` × 7；全部 STORED；全部 `data_offset % 16384 == 0`。
- `zipalign -v -c -P 16 4` → `Verification successful`。

**打包前 `merged_native_libs`（25 个 `.so`）**

| ABI | PASS | FAIL | 失败项 |
|---|---|---|---|
| arm64-v8a | 7 | 0 | — |
| x86_64 | 7 | 0 | — |
| armeabi-v7a | 6 | 1 | ML Kit：3 × LOAD `p_align=4096` |
| x86 | 3 | 1 | ML Kit：4 × LOAD `p_align=4096` + RELRO 同页可写 |

**依赖版本（仓内钉版）**

| 依赖 | 版本 | 64 位 16KB |
|---|---|---|
| LiteRT（`libLiteRt` / `libLiteRtClGlAccelerator`） | 2.1.6 | PASS |
| `liblitert_jni`（litert-api patched AAR） | 2.1.6 | PASS |
| ML Kit `text-recognition-bundled-common` | 17.0.0 | 64 位 PASS；32 位 FAIL |
| quickjs-kt（`libquickjs.so`） | 1.0.15 | PASS（四 ABI 均 PASS） |
| androidx graphics-path / datastore | 随 compose/datastore | PASS |

> 版本出处与 AAR 级测量见 `2026-10-25-16kb-arm64-api23-build.md` §2.3/§2.4；本轮复测的是**当前合并产物**，结论一致，且新增了 `libquickjs.so` 的 64 位 PASS 证据。

### 5.3 两个必须知道的陷阱

1. **不要拿 `merged_native_libs` 直接判成品**：它含 `abiFilters` 裁掉的 32 位库，会看到 2 个 FAIL；成品 APK 是干净的。判据对象必须是**最终 APK**（或明确说明在看中间产物）。
2. **`ro.product.page_size` 不能单独用**：API 34 的 4KB 模拟器该 prop 为空（【本次实测】），会误导成"未知"。以 `getconf PAGE_SIZE` 为准。

---

## 6. AGP 支持矩阵（官方原文归纳）

官方页没有独立表格，只有分段表述；下表按原文归纳，**未陈述的格子不推导**。

| AGP 版本 | 官方口径（原文要点） | 对本项目 |
|---|---|---|
| ≥ 8.5.1 | 要求升级到 8.5.1+；未压缩 `.so` 在 16KB zip 边界对齐 | **9.3.0 ✓**，【本次实测】zipalign 通过 |
| 8.3 – 8.5 | "apps are 16 KB aligned by default. However, `bundletool` does not zipalign APKs by default." → 本地 APK 可能可用，**从 bundle 构建的 Play 包装不上** | n/a |
| ≤ 8.5（不升 8.5.1） | 备选：改用**压缩**共享库（`useLegacyPackaging`） | n/a |
| ≤ 8.0 | 还需 `android.bundle.enableUncompressedNativeLibs=false` | n/a |
| 8.1 – 8.2 | 官方本页未单独说明 | n/a（不推导） |

补充：

- AGP 8.5.0 的 release notes（本轮抓取）**未提** 16KB/zipalign；16KB 的修复点在 **8.5.1**，不要把 8.5.0 当合规线。
- 本项目 `useLegacyPackaging` 仅在 `.worktrees/*/core/visual-ui` 出现，主 `app` 未设置 → 默认未压缩，与 AGP 9.3.0 的合规路径一致。

---

## 7. UNVERIFIED（本轮未做，不得写成通过）

1. **`ps16k_x86_64` 本轮启动/安装/运行**：未跑；仅有 2026-10-25 同机同 AVD 的前序实测。
2. **`ps16k_arm64` 本机可启动性**：未跑；`PANIC` 预期来自单一来源摘要 + 官方加速文档的架构匹配要求。
3. **`llvm-objdump` / `llvm-readelf` 官方命令**：本机无 NDK/LLVM 工具，未跑；用等价 Python 判据替代。
4. **AAB / bundletool `PAGE_ALIGNMENT_16K`**：未跑。
5. **真机 16KB（Pixel 8+ 开发者选项）**：无设备，未涉及。
6. **arm64 镜像是否真为 16KB**：`kernel_cmdline.txt` 无 `page_shift` 属预期，但未启动断言 `getconf`；标"未验证"。
7. **32 位 4KB `.so` 是否触发整应用兼容模式**：官方未按 ABI 说明；本项目已裁 32 位，问题不适用。
8. **Google Issue Tracker / StackOverflow 原文**：WebFetch 分别超时/403，仅得搜索摘要。
9. **最低 emulator 版本**：官方页未列；"35.1.5" 无原文支持，不采纳为判据。

---

## 8. 来源与采纳/拒绝理由

### 采纳

| 来源 | 用途 | 理由 |
|---|---|---|
| `https://developer.android.com/guide/practices/page-sizes`（主站超时，经 `https://developer.android.google.cn/guide/practices/page-sizes` 抓取） | Play 政策 2027-02-01、AGP 8.5.1、NDK r28、zipalign/llvm 命令、RELRO 豁免、模拟器节 | 官方一手；镜像站与主站同文 |
| `https://developer.android.google.cn/studio/run/emulator-acceleration` | 宿主/客户机架构匹配才可用 VM 加速；不匹配则翻译极慢 | 官方一手 |
| 仓内 `2026-10-25-ps16k-emulator-feasibility.md` | 同机 ps16k 全链路、`page_shift=14`、getconf=16384 | 本机前序实测，可复跑 |
| 仓内 `2026-10-25-16kb-arm64-api23-build.md` | ELF 判据、依赖矩阵、AGP/NDK 版本线 | 本机前序实测 + 官方引文 |
| 本轮命令输出 | AVD/镜像/ABI/偏移/ELF 结果 | 【本次实测】 |

### 单一来源 / 待验证

| 来源 | 内容 | 处置 |
|---|---|---|
| `https://issuetracker.google.com/issues/373790939` | emulator 35.2.10 在 x86_64 宿主 + arm64 镜像 `PANIC` | 未取得原文（超时），仅摘要；与 SO 摘要、官方加速文档方向一致，标"待验证" |
| `https://stackoverflow.com/questions/74760054/...` | 同一 `PANIC` 文案 | 403，仅摘要；旁证 |

### 拒绝

| 来源/说法 | 拒绝理由 |
|---|---|
| 社区文章"2025-11-01 截止" | 与官方现行 **2027-02-01** 冲突；官方优先 |
| "Pixel emulation 只需 ARM64 镜像" 套用到 x86_64 宿主 | 该句针对 Pixel 设备配置；x86_64 宿主应使用 x86_64 ps16k 镜像（官方同时提供） |
| "最低 emulator 35.1.5" | 官方页未列，前序文档亦未能复核；不写入判据 |
| "AGP 8.5.0 即可" | 官方合规线是 **8.5.1**；8.5.0 release notes 未提 16KB |

---

## 9. 给 WS9 item 52/53 的移交

- **item 52（ps16k 运行）**：直接用 `ps16k_x86_64`（§4.2），判据 `getconf PAGE_SIZE=16384` + install Success + 零 linker/RELRO + `/proc/pid/maps` 列出 `libquickjs.so`；须绑定 APK sha 与源快照。**本轮未跑，不得记绿。**
- **item 53（arm64 可用性实测）**：本机 x86_64 宿主上预期 `PANIC`（§4.3）；如实记录"本机不可用/真机或 arm64 宿主承担"，**不得**用 x86_64 ps16k 的结果冒充 arm64 验证。
- 静态面已绿（§4.1），可直接作为 item 52 的前置证据；动态面仍需独立执行。
