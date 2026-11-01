# E-04 strictOffline release（R8）冷启动冒烟 ×2

| 字段 | 值 |
|---|---|
| evidence-id | E-04 |
| convention-version | 1 |
| batch | 2026-10-27-batch-26 |
| step | 四（R8 release 抽验，不回退判据） |
| gate-or-kd | KD-69（release 侧冒烟不得回退） |
| verdict | PASS（两次 ok/COLD，9 类签名 0；生产签名 UNVERIFIED） |
| executed-at | 2026-10-27T07:26:00+08:00 |
| operator | 设备验证轨（subagent，批次 26） |
| head | 405c83739cfa5385aa7652ef61f3488f953c66ce |
| worktree | dirty（他线在飞） |
| device-serial | emulator-5554（E-03 冷启动后自然回升至沉降态；release 冷启动本身为判据） |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk`（**一次性本地 key 签名**） |
| apk-sha256 | `a4c1145145eb06e4e60281db229c7e0511106f6404c70db156a65b7e0ba2f432`（设备侧 `pm path`+`sha256sum` 一致） |
| apk-version | 0.2.0-offline |
| exit-code | 0（adb 进程退出码；判据是 `Status`/`LaunchState` 与 logcat） |
| raw-log | `build/device-track-batch26/build-release.log`（506 行）、`validate-release-signing.log`（18 行）、`keytool.log`、`step04-install-release.log`、`step04-coldstart{1,2}.txt`（各 7 行）、`step04-coldstart{1,2}-logcat.txt`（309/253 行）、`step04-pid{1,2}.txt` |
| record-ref | 批次 26 记录（由记录员后写） |
| sanitized | 是（§6） |

## 1. 目的与判据

批次 26 源之上复验 release 变体不回退：`strictOffline release`（R8 minify + shrinkResources +
项目 proguard 规则）装机后连续两次冷启动：`am start -W` 返回 `Status: ok`、`LaunchState: COLD`；
进程存活（`pidof` 非空）；logcat 9 类签名（`FATAL EXCEPTION`/`StartupException`/
`NoSuchMethodException`/`ClassNotFoundException`/`NoClassDefFoundError`/`UnsatisfiedLinkError`/
`MlKitException`/`SIGSEGV`/`ANR in`）全 0。判据集与批次 24 E-03 / 批次 25 E-04 相同
（"不回退"= 同判据再证，非放宽）。

## 2. 命令（可复跑）

### 2.1 签名前置：生产变量缺失（原始证据）

```bash
./gradlew --console=plain :app:validateReleaseSigning
# EXIT=1；日志 build/device-track-batch26/validate-release-signing.log
# > Release signing is missing required environment variables: RELEASE_KEY_ALIAS,
#   RELEASE_KEY_PASSWORD, RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD
```

### 2.2 一次性本地 key 构建（只为冒烟）

```bash
keytool -genkeypair -keystore "D:/smart mistake book/build/device-track-batch26/release-smoke.jks" \
  -storepass <local-smoke> -keypass <local-smoke> -alias batch26-smoke \
  -keyalg RSA -keysize 2048 -validity 7 -dname "CN=Batch26 Local Smoke,O=Local,C=CN"
# EXIT=0

RELEASE_KEY_ALIAS=batch26-smoke RELEASE_KEY_PASSWORD=<local-smoke> \
RELEASE_STORE_FILE="D:/smart mistake book/build/device-track-batch26/release-smoke.jks" \
RELEASE_STORE_PASSWORD=<local-smoke> \
./gradlew --console=plain :app:assembleStrictOfflineRelease
# BUILD SUCCESSFUL in 2m 1s；412 actionable tasks: 11 executed, 401 up-to-date；EXIT=0
```

### 2.3 装机 + 两次冷启动

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# debug 包与本轨 release 包签名不同，先卸载本批刚装的 debug 包（含其测试包）
$A -s emulator-5554 uninstall com.tingyun.smartmistakebook.offline.test
$A -s emulator-5554 uninstall com.tingyun.smartmistakebook.offline
$A -s emulator-5554 install -r -t app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
$A -s emulator-5554 shell "pm path com.tingyun.smartmistakebook.offline | sed 's/package://'"   # + sha256sum
# 每轮：force-stop →（第 2 轮加 sleep 2）→ logcat -c → am start -W → sleep 6 → pidof → logcat -d
```

## 3. 结果摘要

| 指标 | 冷启动 1 | 冷启动 2 | 判据 |
|---|---|---|---|
| `Status` | ok | ok | 必须是 ok |
| `LaunchState` | COLD | COLD | 必须是 COLD |
| `TotalTime` (ms) | 1086 | 524 | 记录值，非阈值 |
| `WaitTime` (ms) | 1101 | 528 | 记录值，非阈值 |
| 进程 PID | 4756 | 4854 | 非空 |
| 9 类签名命中 | 0 | 0 | 0 |

产物身份：

- 设备侧 `pm path` + `sha256sum` = `a4c11451…f432`，与产物一致；
- `apksigner verify --print-certs` → `V2 Signer: certificate DN: CN=Batch26 Local Smoke, O=Local, C=CN`，
  证书 SHA-256 `b4bca1cbcbc3c8ba11b191005790ff465aec1bc59650e22564d11d70a87530cc`。
  **一次性本轨 key，不是生产发布证书。**

## 4. 关键输出（有界）

```text
# coldstart 1
Status: ok
LaunchState: COLD
TotalTime: 1086
WaitTime: 1101
4756
```

```text
# coldstart 2
Status: ok
LaunchState: COLD
TotalTime: 524
WaitTime: 528
4854
```

签名扫描（两个 logcat 文件各 9 类全 0，命令同批次 25 E-04 §3）。

## 5. 失败 / 跳过 / 异常

无失败、无跳过。两轮均 `ok/COLD`、PID 存活、9 类签名 0——相对批次 25 E-04（同判据两次
ok/COLD，827/470 ms）**不回退**；TotalTime 为记录值不作阈值比较。

**签名限制（务必带在结论旁）**：生产 `RELEASE_*` 环境变量在本机不存在（§2.1 EXIT=1 原始
证据）；本步用一次性本地 key 构建，验证的是 release 变体的 R8/资源收缩/打包/启动路径，
不验证生产签名证书与发布链。生产 release 签名仍 UNVERIFIED。

## 6. 设备状态恢复（不回退 = 判据口径；设备本身恢复到 debug 态）

冒烟后已卸载本轨 release 包并重装 debug 主包 + debug androidTest 包 + core:data androidTest
测试包（三个 `install` 均 `Success`；恢复后 `step05` 的备份抽验即在该恢复态上执行，34/34 绿
本身即恢复态可用的旁证）。

## 7. 脱敏与去噪

- 删除：Gradle 全量构建日志只留在 `build/`；安装进度行。
- 保留：两次 `am start -W` 全文、PID、9 类签名零计数、产物哈希、证书 DN 与指纹。
- 一次性 keystore 口令不出现在本文件（写作 `<local-smoke>`）；keystore 本体在 `build/`（不入仓）。
- 扫描：见 `00-index.md` §6。二进制/截图：无。

## 8. 判定

**PASS（release 变体冷启动 ×2，不回退）**：批次 26 源的 release APK 在 emulator-5554 / API 34
上两次冷启动均 `ok/COLD`，PID 存活，9 类签名 0。

未覆盖：生产签名 UNVERIFIED；中文 OCR 未执行（KD-69 Reopen condition 的另一半，本步不做，
不声称 KD-69 完整达成）；只测 x86_64 / API 34 release（arm64 release 沿 R4 缺口）。

## 9. 原始全量（不入仓）

- 路径：`build/device-track-batch26/build-release.log`（506 行）、`validate-release-signing.log`
  （18 行）、`keytool.log`、`step04-install-release.log`（2 行）、`step04-coldstart{1,2}.txt`
  （各 7 行）、`step04-coldstart{1,2}-logcat.txt`（309/253 行）、`step04-pid{1,2}.txt`
- 生成命令：见 §2。`build/` 被 `.gitignore` 忽略；关闭判据只认本文件。
