# E-00 环境与设备身份

| 字段 | 值 |
|---|---|
| evidence-id | E-00 |
| convention-version | 1 |
| batch | 2026-10-29-batch-32 |
| verdict | PASS（信息性，不参与红绿判定） |
| executed-at | 2026-10-29T11:13:19+08:00 |
| operator | 设备验证轨（subagent，批次 32） |
| head | `0b4bdbbea918ba6b8c8e1f3b190b8be502ec627a` |
| worktree | dirty（240 M / 480 `??`，均为他线在飞；本轨零主源写入） |
| sanitized | 是（§4.4 扫描 0 命中） |

## 1. 主机工具

```bash
adb version
# Android Debug Bridge version 1.0.41
# Version 37.0.1-15733141
```

| 工具 | 路径/版本 | 备注 |
|---|---|---|
| adb | `C:/Android/Sdk/platform-tools/adb.exe` / 37.0.1 | `MSYS_NO_PATHCONV=1` |
| Gradle | `./gradlew`（仓库 wrapper） | 仅 `assemble*` |
| sha256sum / unzip / python | Git Bash 内置 | 证据采集 |

## 2. 设备

| serial | AVD | API | ABI | `getconf PAGE_SIZE` | `ro.product.cpu.page.size` |
|---|---|---|---|---|---|
| `emulator-5562` | `test_device` | 34 | `x86_64` | 4096 | `<empty>` |

```bash
adb -s emulator-5562 emu avd name            # test_device / OK
adb -s emulator-5562 shell getprop ro.build.version.sdk      # 34
adb -s emulator-5562 shell getprop ro.product.cpu.abi        # x86_64
adb -s emulator-5562 shell getconf PAGE_SIZE                 # 4096
adb -s emulator-5562 shell getprop ro.product.cpu.page.size  # <empty>
adb -s emulator-5562 shell getprop ro.build.fingerprint
# google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys
adb -s emulator-5562 shell uptime
# 03:32:11 up 13:00, 0 users, load average: 0.21, 0.87, 0.75
```

- 未触碰 `emulator-5566` / `emulator-5584`（他线）。
- `ro.product.cpu.page.size` 为空按 R4 §8.3 原样记录，不判失败；页大小以 `getconf` 为准。

## 3. 数据保护

- 设备用途：专用测试模拟器（`test_device`），非共享。
- 真实学习者数据：否。全部用例走夹具库；备份/恢复用例在 test 包私有目录内建库。
- 测试后检查：四包仍安装（`pm path` 四次均返回 base.apk），无 `adb root`。

## 4. 数据保护结论

本次未触及真实学习者数据；包与数据目录保持安装状态。
