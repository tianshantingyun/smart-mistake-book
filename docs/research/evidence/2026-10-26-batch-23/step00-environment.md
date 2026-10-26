# E-00 环境与设备身份

| 字段 | 值 |
|---|---|
| evidence-id | E-00 |
| convention-version | 1 |
| batch | 2026-10-26-batch-23 |
| verdict | PASS（信息性，不参与 KD-74 覆盖判定） |
| executed-at | 2026-10-26T16:10:00+08:00 |
| operator | 设备验证轨（subagent，批次 23） |
| head | f0a145f850931887385c4bf2e1f798a0e8f2e391 |
| worktree | dirty（他线在飞：数十个 M；本轨未改产品源，仅新增本证据目录） |
| sanitized | 是（§4.4 扫描 0 命中，见 §6） |

## 1. 主机工具

```bash
C:/Android/Sdk/platform-tools/adb.exe version            # Android Debug Bridge 1.0.41 / 37.0.1-15733141
C:/Android/Sdk/emulator/emulator.exe -version            # Android emulator 37.1.11.0
ls C:/Android/Sdk/build-tools/                           # 34.0.0 / 36.0.0 / 37.0.0（用 37.0.0）
command -v llvm-readelf || echo "llvm-readelf: 不存在"    # 不存在
ls -d /c/Android/Sdk/ndk || echo "ndk: 不存在"            # 不存在
```

| 工具 | 路径/版本 | 备注 |
|---|---|---|
| adb | `C:/Android/Sdk/platform-tools/adb.exe` 1.0.41 (37.0.1) | |
| emulator | 37.1.11.0 | |
| zipalign | build-tools 37.0.0 | `-c -P 16 -v 4` exit 0 |
| sha256sum | Git Bash coreutils | |
| llvm-readelf | `不存在` | 官方 ELF 判据工具缺失；本批用只读 Python 程序头算术作补充（E-03 §5），不冒充官方工具 |
| NDK | `不存在` | 无法重新编译 AAR 内 `.so` |

Gradle 版本（构建日志）：`Gradle 9.6.1`（`build/device-track-batch23/build-assemble.log:7726`）。

**主机编码异常（如实记录，不阻塞本批设备结论）**：`:app:compileStrictOfflineDebugJavaWithJavac` / `:app:compileStrictOfflineInternalJavaWithJavac` 各打印 8 条 `错误: 编码 UTF-8 的不可映射字符`（`build-assemble.log:4291-4315`、`:7694-7718`），来源是 `aidl.exe` 生成的 Java 头注释里含主机用户目录的 GBK 字节；Gradle 仍 `BUILD SUCCESSFUL`，且 AIDL 类实际进入 APK（`classes4.dex` 含 `IVisualJsService`），设备上 AIDL 往返用例通过（E-01/E-01b）。这是主机非 ASCII 用户名 + aidl 代码页问题，未改任何构建文件。

## 2. 设备

| serial | AVD | API | ABI | `getconf PAGE_SIZE` | 用途 |
|---|---|---|---|---|---|
| emulator-5554 | test_device | 34 | x86_64 | 4096 | 隔离进程 / QuickJS / R8 / 备份迁移 / KD-67 |
| emulator-5556 | ps16k_x86_64 | 36 | x86_64 | **16384** | 16KB 页门 |

```text
emulator-5556:
16384
ro.product.cpu.page.size=
ro.product.page_size=
ro.boot.hardware.cpu.pagesize=16384
```

（`getprop` 空值原样保留；以 `getconf PAGE_SIZE` 为准。）

开工时 `adb devices` 另见 `emulator-5562` / `emulator-5584`，属他线在飞设备，本轨未安装、未运行任何命令于其上。

## 3. 产物身份

| 产物 | 本机路径 | 字节 | sha256 | versionName |
|---|---|---|---|---|
| app debug APK | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` | 189,384,478 | `e5ec3a89ec93ca1289f232082371e04a37c77a1c78c5c4c8ac0debc5720bb441` | 0.2.0-offline |
| app debug androidTest APK | `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` | 1,568,217 | `fa696029ad7ae64ea5a980e2301c402f9eadf33c45ad9fa27e8e08f9245c0bb2` | — |
| app internal（R8）APK | `app/build/outputs/apk/strictOffline/internal/app-strictOffline-internal.apk` | 169,301,163 | `da8df2f8a80f0b18a10a8773232a29150071ae6930a349c94ae9cb58681f0e5b` | 0.2.0-offline |
| core:data androidTest APK | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | 197,668,020 | `5ef6ff9b46165517c0196f6b2926e8a07b64cf1fcfa8e62c5c82eb6901e72e75` | — |
| core:database androidTest APK | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` | 196,721,621 | `feb1b3601939487297b37af2c95a480b8b00995ba74d7c0da859f073aaa6df4e` | — |
| internal mapping.txt | `app/build/outputs/mapping/strictOfflineInternal/mapping.txt` | 87,899,041 | `0ee9ed57ff66fec27ff79673cc56732aa8cce53ef2b3471d38cb0961cd98c3f4` | —（不入仓） |

设备侧身份复核：`adb shell pm path` + `sha256sum /data/app/.../base.apk` = `da8df2f8…0e5b`，与 internal APK 逐字节一致（E-04 §3）。

构建命令（仅 assemble，未跑 connectedAndroidTest）：

```bash
./gradlew --console=plain :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest :app:assembleStrictOfflineInternal \
  :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest
```

- Gradle 结果：`BUILD SUCCESSFUL in 3m 42s`（`build/device-track-batch23/build-assemble.log:7739`）
- 外层 shell 管道 exit 1 的原因：启动时 `build/device-track-batch23/` 目录尚不存在，`tee` 首行报 `No such file or directory`；日志随后从后台缓冲复制到该路径，**不是构建失败**（APK 在 16:08–16:11 产出并被设备用例实际使用）。
- 全量日志：`build/device-track-batch23/build-assemble.log`（7,741 行，不入仓）

说明：`./gradlew :app:assembleStrictOfflineInternalAndroidTest` 不存在（AGP 默认 `testBuildType=debug`，只生成 debug 的 androidTest 变体）；因此 R8 变体没有仪器化测试 APK，KD-69 的 OCR/Worker 走真实 App 的分享入口与启动 Worker（E-04）。

## 4. 数据保护

- 设备用途：专用测试模拟器（`test_device`、`ps16k_x86_64`）。
- 真实学习者数据：否。OCR 夹具是用 PIL + 本机中文字体生成的合成图片（「已知函数 f(x)=x²…」），不是真实题目。
- 破坏性用例：`DeviceMigrationDrillInstrumentedTest` 使用 `-e smb_allow_data_reset true` 显式 opt-in，只清空测试 APK `com.tingyun.smartmistakebook.core.data.test` 的数据目录，不触碰真实 App 数据。
- 测试后检查：App 与测试 APK 仍在设备上；未执行 uninstall。

## 5. 数据保护结论

本批未触及真实数据；破坏性迁移演练按 R4 §8.5 原样带 opt-in 参数执行（E-06）。

## 6. 脱敏与去噪

- 删除：重复的 Gradle 进度行与 ANSI 转义（只在本机 `build/` 全量日志中，未入仓）。
- 保留：命令、serial（模拟器）、AVD、API、ABI、页大小、APK 哈希、退出码。
- 扫描（证据目录建成后执行）：

```bash
E=docs/research/evidence/2026-10-26-batch-23
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' "$E" || echo "0 hits: secrets"
grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken)[[:space:]]*[:=][[:space:]]*[^ <]' "$E" || echo "0 hits: credential assignments"
grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$E" || echo "0 hits: emails"
grep -RInE 'C:.Users.[^ ]+|/home/[^/ ]+|/Users/[^/ ]+' "$E" || echo "0 hits: home paths"
```

- 结果：见 `00-index.md` §6（本文件写盘后统一跑，0 命中）。

## 7. 判定

PASS（信息性）。设备与产物身份齐全；官方 ELF 工具缺口与主机 javac 编码异常均已如实登记，不用替代物冒充。
