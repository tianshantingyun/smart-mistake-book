# E-00 环境与设备身份

| 字段 | 值 |
|---|---|
| evidence-id | E-00 |
| convention-version | 1 |
| batch | 2026-10-29-batch-33 |
| verdict | PASS（信息性，不参与 KD 覆盖判定） |
| executed-at | 2026-10-29T14:44:47+08:00 |
| operator | 设备验证轨（子代理） |
| head | `6abd807fa73d9084ef629d54015bd4f725c94784` |
| sanitized | 是（本文件 §4；无用户目录/凭据） |

## 1. 设备（`emulator-5562`）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A devices -l
$A -s emulator-5562 shell getprop ro.build.version.sdk
$A -s emulator-5562 shell getprop ro.product.cpu.abi
$A -s emulator-5562 shell getconf PAGE_SIZE
$A -s emulator-5562 shell getprop ro.build.fingerprint
```

| 字段 | 值 |
|---|---|
| serial | `emulator-5562`（其余在线 `-5566` / `-5584` 属他线，禁碰） |
| 状态 | `device`（非 offline/unauthorized） |
| API | 34 |
| ABI | x86_64 |
| PAGE_SIZE | 4096 |
| model | sdk_gphone64_x86_64 |
| fingerprint | `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys` |

## 2. 主机工具

| 工具 | 路径/版本 |
|---|---|
| adb | `C:/Android/Sdk/platform-tools/adb.exe` |
| shell | Git Bash（MSYS_NO_PATHCONV=1 全程生效） |
| unzip / sha256sum / diff | Git Bash 自带 |

## 3. 数据保护

- 设备用途：专用测试模拟器。
- 使用真实学习者数据：否。用例全部使用 `StudySeedBundle` 夹具与临时库名（C4 用默认库名，
  `setUp`/`tearDown` 删除）。
- 测试后：测试库由 `tearDown`/夹具 `finally` 删除；应用包保持安装。

## 4. 脱敏

- 删除：无（本文件无日志正文）。
- 保留：模拟器 serial、API、ABI、页大小、fingerprint、命令与退出码。
- 扫描：未包含密钥/凭据/邮箱/用户主目录（人工通读）。
