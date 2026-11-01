# E-04 strictOffline release（R8）冷启动冒烟 ×2

| 字段 | 值 |
|---|---|
| evidence-id | E-04 |
| convention-version | 1 |
| batch | 2026-10-27-batch-25 |
| step | 四（R8 release 抽验，不回退判据） |
| gate-or-kd | KD-69（release 侧冒烟不得回退） |
| verdict | PASS（两次 ok/COLD，9 类签名 0；生产签名 UNVERIFIED） |
| executed-at | 2026-10-27T03:47:00+08:00 |
| operator | 设备验证轨（subagent，批次 25） |
| head | 25ddb60273e32ebd4100e8deb9f965bb0dfd4476 |
| worktree | dirty（他线在飞） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk`（**一次性本地 key 签名**） |
| apk-sha256 | `824cd5b2e93eecbaeb6bf8695e780ba37c674cc99f0a8a76790d4b03bfb3735f`（155,699,091 B；设备侧 `pm path`+`sha256sum` 一致） |
| apk-version | 0.2.0-offline |
| exit-code | 0（adb 进程退出码；判据是 `Status`/`LaunchState` 与 logcat） |
| raw-log | `build/device-track-batch25/build-release.log`、`validate-release-signing.log`（18 行）、`step04-install-release.log`、`step04-coldstart{1,2}.txt`（各 7 行）、`step04-coldstart{1,2}-logcat.txt`（302/296 行）、`step04-pid{1,2}.txt`、`emulator-5554-coldboot.log` |
| record-ref | 批次 25 记录（由记录员后写） |
| sanitized | 是（§6） |

## 1. 目的与判据

批次 25 源（T1/T2/T3 落层）之上复验 release 变体不回退：`strictOffline release`
（R8 minify + shrinkResources + 项目 proguard 规则）装机后连续两次冷启动：

- `am start -W` 返回 `Status: ok`、`LaunchState: COLD`；
- 进程存活（`pidof` 非空）；
- logcat 9 类签名（`FATAL EXCEPTION`/`StartupException`/`NoSuchMethodException`/
  `ClassNotFoundException`/`NoClassDefFoundError`/`UnsatisfiedLinkError`/`MlKitException`/
  `SIGSEGV`/`ANR in`）全 0。

判据集与批次 24 E-03 相同（"不回退"= 同判据再证，非放宽）。

## 2. 命令（可复跑）

### 2.1 签名前置：生产变量缺失（原始证据）

```bash
./gradlew --console=plain :app:validateReleaseSigning
# exit=1；日志 build/device-track-batch25/validate-release-signing.log
# > Release signing is missing required environment variables: RELEASE_KEY_ALIAS,
#   RELEASE_KEY_PASSWORD, RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD
```

### 2.2 一次性本地 key 构建（只为冒烟）

```bash
keytool -genkeypair -keystore "D:/smart mistake book/build/device-track-batch25/release-smoke.jks" \
  -storepass <local-smoke> -keypass <local-smoke> -alias batch25-smoke \
  -keyalg RSA -keysize 2048 -validity 7 -dname "CN=Batch25 Local Smoke,O=Local,C=CN"
# exit=0

RELEASE_KEY_ALIAS=batch25-smoke RELEASE_KEY_PASSWORD=<local-smoke> \
RELEASE_STORE_FILE="D:/smart mistake book/build/device-track-batch25/release-smoke.jks" \
RELEASE_STORE_PASSWORD=<local-smoke> \
./gradlew --console=plain :app:assembleStrictOfflineRelease
# BUILD SUCCESSFUL in 3m 11s；412 actionable tasks: 68 executed, 344 up-to-date；exit=0
```

### 2.3 装机 + 两次冷启动

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# debug 包与本轨 release 包签名不同，先卸载本批刚装的 debug 包
$A -s emulator-5554 uninstall com.tingyun.smartmistakebook.offline
$A -s emulator-5554 install -r -t app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
$A -s emulator-5554 shell "pm path com.tingyun.smartmistakebook.offline | sed 's/package://'"   # + sha256sum

# 第 1 次
$A -s emulator-5554 shell am force-stop com.tingyun.smartmistakebook.offline
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am start -W -n \
  com.tingyun.smartmistakebook.offline/com.tingyun.smartmistakebook.MainActivity
sleep 6
$A -s emulator-5554 shell pidof com.tingyun.smartmistakebook.offline
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch25/step04-coldstart1-logcat.txt
# 第 2 次：force-stop + sleep 2 + logcat -c 后同上 → step04-coldstart2*
```

## 3. 结果摘要

| 指标 | 冷启动 1 | 冷启动 2 | 判据 |
|---|---|---|---|
| `Status` | ok | ok | 必须是 ok |
| `LaunchState` | COLD | COLD | 必须是 COLD |
| `TotalTime` (ms) | 827 | 470 | 记录值，非阈值 |
| `WaitTime` (ms) | 833 | 472 | 记录值，非阈值 |
| 进程 PID | 4972 | 5074 | 非空 |
| 9 类签名命中 | 0 | 0 | 0 |

产物身份：

- 设备侧 `pm path` + `sha256sum` = `824cd5b2…735f`，与产物一致；
- `apksigner verify --print-certs` → `V2 Signer: certificate DN: CN=Batch25 Local Smoke, O=Local, C=CN`，
  证书 SHA-256 `97a31adc64d4f2ef2a8a4399da7538fb65c993e5601820fcdaac98433f51f6fe`。
  **一次性本轨 key，不是生产发布证书。**

## 4. 关键输出（有界）

```text
# coldstart 1
Status: ok
LaunchState: COLD
TotalTime: 827
WaitTime: 833
Complete
4972
```

```text
# coldstart 2
Status: ok
LaunchState: COLD
TotalTime: 470
WaitTime: 472
Complete
5074
```

签名扫描（命令同批次 24 E-03 §4，两文件各 9 类全 0，原始计数见 raw-log）。

## 5. 失败 / 跳过 / 异常

无失败、无跳过。两轮均 `ok/COLD`、PID 存活、9 类签名 0——相对批次 24 E-03（同判据
两次 ok/COLD，2405/1245 ms）**不回退**；本轮 TotalTime 更短是页面缓存热身差异，不作阈值比较。

**签名限制（务必带在结论旁）**：生产 `RELEASE_*` 环境变量在本机不存在（§2.1 exit=1 原始
证据）；本步用一次性本地 key 构建，验证的是 release 变体的 R8/资源收缩/打包/启动路径，
不验证生产签名证书与 Play/发布链。生产 release 签名仍 UNVERIFIED。

## 6. 设备状态恢复（不回退 = 判据口径；设备本身恢复到 debug 态）

冒烟后已卸载本轨 release 包并重装 debug 包 + debug androidTest 包 + core:data androidTest
测试包（三个 `install` 均 `Success`；恢复后设备侧哈希复核：
app `1af0f99a…`、app.test `6623b0ac…`、core.data.test `94df5c39…`，与 §E-00 产物一致——
`step05` 的备份抽验即在该恢复态上执行）。

## 7. 脱敏与去噪

- 删除：Gradle 全量构建日志只留在 `build/`；安装进度行。
- 保留：两次 `am start -W` 全文、PID、9 类签名零计数、产物哈希、证书 DN 与指纹。
- 一次性 keystore 口令不出现在本文件（写作 `<local-smoke>`）；keystore 本体在 `build/`（不入仓）。
- 扫描：见 `00-index.md` §6。
- 二进制/截图：无。

## 8. 判定

**PASS（release 变体冷启动 ×2，不回退）**：批次 25 源的 release APK 在 emulator-5554 /
API 34 上两次冷启动均 `ok/COLD`，PID 存活，9 类签名 0。

未覆盖：

1. **生产签名 UNVERIFIED**（无 `RELEASE_*` 凭据，一次性本地 key）；
2. **中文 OCR 未执行**（KD-69 Reopen condition 的另一半，本步不做，不声称 KD-69 完整达成）；
3. 只测 x86_64 / API 34 release；arm64 release 未测（沿 R4 缺口）。

## 9. 原始全量（不入仓）

- 路径：`build/device-track-batch25/build-release.log`、`validate-release-signing.log`（18 行）、
  `step04-install-release.log`（2 行）、`step04-coldstart{1,2}.txt`（各 7 行）、
  `step04-coldstart{1,2}-logcat.txt`（302/296 行）、`step04-pid{1,2}.txt`
- 生成命令：见 §2
- 说明：`build/` 被 `.gitignore` 忽略；关闭判据只认本文件。
