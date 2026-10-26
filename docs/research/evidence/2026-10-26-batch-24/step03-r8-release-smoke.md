# E-03 strictOffline release（R8）冷启动冒烟

| 字段 | 值 |
|---|---|
| evidence-id | E-03 |
| convention-version | 1 |
| batch | 2026-10-26-batch-24 |
| step | 三（R8 release） |
| gate-or-kd | KD-69（release 侧冒烟不得回退） |
| verdict | PASS（release 变体冷启动 ×2；生产签名 UNVERIFIED，见 §3/§7） |
| executed-at | 2026-10-26T21:51:00+08:00 |
| operator | 设备验证轨（subagent，批次 24） |
| head | 5bc8bdf868e1296049ab901cf1da69e136062006 |
| worktree | dirty（他线在飞） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk`（**一次性本地 key 签名**） |
| apk-sha256 | `597129283c4d83c3a320bf3dbe963a1dce32297b5a78caec448b618943c180a7` |
| apk-version | 0.2.0-offline |
| exit-code | 0（adb 进程退出码；判据是 `Status/LaunchState` 与 logcat） |
| raw-log | `build/device-track-batch24/build-release.log`（7,507 行）、`step03-coldstart{1,2}.txt`（各 8 行）、`step03-coldstart{1,2}-logcat.txt`（362/298 行） |
| record-ref | `docs/research/2026-10-26-batch-24-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

补齐 KD-69 未覆盖的 **release 变体**侧：`strictOffline release`（`isMinifyEnabled=true`、
`isShrinkResources=true`、项目 `proguard-rules.pro`）装机后连续两次冷启动：

- `am start -W` 返回 `Status: ok`、`LaunchState: COLD`；
- 进程存活（`pidof` 非空）；
- logcat 中 `FATAL EXCEPTION` / `StartupException` / `NoSuchMethodException` /
  `ClassNotFoundException` / `NoClassDefFoundError` / `UnsatisfiedLinkError` / `MlKitException` /
  `SIGSEGV` / `ANR in` 全 0。

KD-69 的 Reopen condition 还要求"一次中文 OCR"；本步按任务书只做冷启动冒烟，**不**声称 OCR 已验证。

## 2. 命令（可复跑）

### 2.1 签名前置：生产变量缺失是事实

```bash
./gradlew --console=plain :app:validateReleaseSigning
# exit=1；日志 build/device-track-batch24/validate-release-signing.log:22
# Release signing is missing required environment variables:
#   RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD, RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD
```

### 2.2 用一次性本地 key 构建 release（只为冒烟）

```bash
keytool -genkeypair -keystore "D:/smart mistake book/build/device-track-batch24/release-smoke.jks" \
  -storepass <local-smoke> -keypass <local-smoke> \
  -alias batch24-smoke -keyalg RSA -keysize 2048 -validity 1 \
  -dname "CN=Batch24 Local Smoke,O=Local,C=CN"

RELEASE_KEY_ALIAS=batch24-smoke RELEASE_KEY_PASSWORD=<local-smoke> \
RELEASE_STORE_FILE="D:/smart mistake book/build/device-track-batch24/release-smoke.jks" \
RELEASE_STORE_PASSWORD=<local-smoke> \
./gradlew --console=plain :app:assembleStrictOfflineRelease
# BUILD SUCCESSFUL in 7m 8s；412 actionable tasks: 105 executed, 307 up-to-date
```

keystore 与口令只存在于本机 `build/`（被 `.gitignore` 忽略），不写入源码/证据；`<local-smoke>` 是
本轨自定的一次性口令，不是生产凭据。

### 2.3 装机 + 两次冷启动

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# debug 包与本轨 release 包签名不同，必须先卸载本批刚装的 debug 包
$A -s emulator-5554 uninstall com.tingyun.smartmistakebook.offline
$A -s emulator-5554 install -r -t app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk

$A -s emulator-5554 shell am force-stop com.tingyun.smartmistakebook.offline
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am start -W -n \
  com.tingyun.smartmistakebook.offline/com.tingyun.smartmistakebook.MainActivity
sleep 6
$A -s emulator-5554 shell pidof com.tingyun.smartmistakebook.offline
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch24/step03-coldstart1-logcat.txt

# 第二次
$A -s emulator-5554 shell am force-stop com.tingyun.smartmistakebook.offline
sleep 2
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am start -W -n \
  com.tingyun.smartmistakebook.offline/com.tingyun.smartmistakebook.MainActivity
sleep 6
$A -s emulator-5554 shell pidof com.tingyun.smartmistakebook.offline
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch24/step03-coldstart2-logcat.txt
```

## 3. 结果摘要

| 指标 | 冷启动 1 | 冷启动 2 | 判据 |
|---|---|---|---|
| `Status` | ok | ok | 必须是 ok |
| `LaunchState` | COLD | COLD | 必须是 COLD |
| `TotalTime` (ms) | 2405 | 1245 | 记录值，非阈值 |
| `WaitTime` (ms) | 2421 | 1250 | 记录值，非阈值 |
| 进程 PID | 7460 | 7586 | 非空 |
| `FATAL EXCEPTION` | 0 | 0 | 0 |
| `StartupException` | 0 | 0 | 0 |
| `NoSuchMethodException` | 0 | 0 | 0 |
| `ClassNotFoundException` | 0 | 0 | 0 |
| `NoClassDefFoundError` | 0 | 0 | 0 |
| `UnsatisfiedLinkError` | 0 | 0 | 0 |
| `MlKitException` | 0 | 0 | 0 |
| `SIGSEGV` | 0 | 0 | 0 |
| `ANR in` | 0 | 0 | 0 |

产物身份：

- 设备侧 `pm path` + `sha256sum` = `597129283c4d83c3a320bf3dbe963a1dce32297b5a78caec448b618943c180a7`，与产物一致；
- `apksigner verify --print-certs` → `V2 Signer: certificate DN: CN=Batch24 Local Smoke, O=Local, C=CN`。

## 4. 关键输出（有界）

```text
Starting: Intent { cmp=com.tingyun.smartmistakebook.offline/com.tingyun.smartmistakebook.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.tingyun.smartmistakebook.offline/com.tingyun.smartmistakebook.MainActivity
TotalTime: 2405
WaitTime: 2421
Complete
7460
```

```text
Starting: Intent { cmp=com.tingyun.smartmistakebook.offline/com.tingyun.smartmistakebook.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.tingyun.smartmistakebook.offline/com.tingyun.smartmistakebook.MainActivity
TotalTime: 1245
WaitTime: 1250
Complete
7586
```

签名扫描命令：

```bash
for s in "FATAL EXCEPTION" "StartupException" "NoSuchMethodException" "ClassNotFoundException" \
         "NoClassDefFoundError" "UnsatisfiedLinkError" "MlKitException" "SIGSEGV" "ANR in"; do
  printf '%-26s %s\n' "$s" "$(grep -c "$s" build/device-track-batch24/step03-coldstart1-logcat.txt)"
done
# 全部 0；coldstart2 同
```

## 5. 失败 / 跳过 / 异常

无失败、无跳过。两轮均 `Status: ok` / `LaunchState: COLD`，进程存活到采样时刻。

**签名限制（务必带在结论旁）.** 生产 `RELEASE_*` 环境变量在本机不存在（§2.1 的 exit=1 是原始证据）；
本步用一次性本地 key 构建，因此验证的是 **release 变体的 R8/资源收缩/打包/启动路径**，
不验证生产签名证书与 Play/发布链。生产 release 签名仍 UNVERIFIED。

## 6. 脱敏与去噪

- 删除：Gradle 全量构建日志只留在 `build/`；安装/Kotlin 编译告警不进入证据。
- 保留：两次 `am start -W` 全文、PID、9 类签名零计数、产物哈希、证书 DN。
- 一次性 keystore 口令不出现在本文件（写作 `<local-smoke>`）；keystore 本体不在仓内。
- 扫描：见 `00-index.md` §6（0 命中）。
- 二进制/截图：无。

## 7. 判定

**PASS（release 变体冷启动 ×2）**：release APK（R8 + shrinkResources + 项目 keep 规则）在
emulator-5554 / API 34 上两次冷启动均 `ok/COLD`，PID 存活，9 类崩溃签名全 0。

未覆盖：

1. **生产签名未验证**（无 `RELEASE_*` 凭据，使用一次性本地 key）；
2. **中文 OCR 未在本步执行**——KD-69 Reopen condition 要求"一次中文 OCR"；本步只按任务书做冷启动，
   不声称 KD-69 的完整判据已达成；
3. `usage.txt` 未做全量穷举（批次 23 的覆盖边界仍在）；
4. 只测 x86_64 / API 34 release；arm64 release 未测。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch24/build-release.log`（7,507 行）、`step03-coldstart{1,2}.txt`（各 8 行）、`step03-coldstart{1,2}-logcat.txt`（362/298 行）
- 生成命令：见 §2
- 说明：`build/` 被 `.gitignore` 忽略；关闭判据只认本文件。
- 设备状态恢复：冒烟后已卸载本轨 release 包并重装 debug 包 + debug androidTest 包。恢复时盘上 debug APK 已被他线在 21:37 重建为 `782cebd6…73c6`，故最终安装件是该重建件（E-01b 即在其上执行），androidTest 包为 `343b637a…0c0e`；emulator-5554 回到批次 24 的 debug 状态。
