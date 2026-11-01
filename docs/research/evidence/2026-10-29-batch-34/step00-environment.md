# E-00 环境与设备身份

| 字段 | 值 |
|---|---|
| evidence-id | E-00 |
| convention-version | 1 |
| batch | 2026-10-29-batch-34 |
| verdict | PASS（信息性，不参与 KD 覆盖判定） |
| executed-at | 2026-10-29T18:42:40+08:00 |
| operator | 设备验证轨（子代理） |
| head | `db792fa873f4bc150f415b67d1c2e3b1bab6c995` |
| worktree | dirty（721 条：241 `M` / 480 `??`；均为他线与本批在飞改动；本轨零主源改动、零 git 写） |
| sanitized | 是（§4；规范 §4.4 扫描 0 命中） |

## 1. 主机工具

```bash
java -version
ls -l gradlew
ls C:/Android/Sdk/build-tools/
C:/Android/Sdk/build-tools/37.0.0/aapt2.exe version   # 用于 APK versionName
```

| 工具 | 路径/版本 |
|---|---|
| JDK | Temurin OpenJDK 21.0.11+10-LTS（`JAVA_HOME=/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot`） |
| Gradle | 仓库 wrapper `./gradlew`（Gradle 9.6.1，见构建输出） |
| adb | `C:/Android/Sdk/platform-tools/adb.exe` |
| aapt2 | `C:/Android/Sdk/build-tools/37.0.0/aapt2.exe` |
| shell | Git Bash，`MSYS_NO_PATHCONV=1` 全程生效 |
| unzip / sha256sum / diff | Git Bash 自带 |

## 2. 设备

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A devices -l
$A -s emulator-5562 shell getprop ro.build.version.sdk
$A -s emulator-5562 shell getprop ro.product.cpu.abi
$A -s emulator-5562 shell getconf PAGE_SIZE
$A -s emulator-5562 shell getprop ro.build.fingerprint
$A -s emulator-5562 shell cat /proc/uptime /proc/loadavg
```

| 字段 | 值 |
|---|---|
| serial | `emulator-5562`（在线另两台 `-5566` / `-5584` 属他线，未触碰） |
| 状态 | `device`（非 offline/unauthorized） |
| API | 34 |
| ABI | x86_64 |
| PAGE_SIZE | 4096 |
| model | sdk_gphone64_x86_64 |
| fingerprint | `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys` |
| 开工 uptime / loadavg | `72693.07 279949.56` / `0.15 0.09 0.03` |

## 3. 数据保护

- 设备用途：专用测试模拟器；不使用真实学习者数据。
- 全部用例使用 `StudySeedBundle` 夹具或临时库名；`setUp`/`tearDown` 删除测试库。
- 测试后：应用包保持安装；无真实数据写入。

## 4. 脱敏

- 删除：无（本文件无日志正文）。
- 保留：模拟器 serial、API、ABI、页大小、fingerprint、命令与退出码。
- 扫描：规范 §4.4 四条正则对本目录全部 `.md` 实跑，见 `00-index.md` §6。
- 未包含密钥/凭据/邮箱/用户主目录。
