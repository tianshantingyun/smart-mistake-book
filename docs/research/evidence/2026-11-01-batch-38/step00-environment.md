# E-00 环境与设备身份

| 字段 | 值 |
|---|---|
| evidence-id | E-00 |
| convention-version | 1 |
| batch | 2026-11-01-batch-38 |
| verdict | PASS（信息性，不参与门判定） |
| executed-at | 2026-11-01T04:03:00+08:00（登记窗 04:03–05:30+08:00） |
| operator | 设备验证轨（批次 38 工作流子代理，串行设备轨） |
| head | `dcfdc5b52f01cb67c1d0f88375bcc092a8f54d9a`（批次 37 收口提交，本批工作树未提交） |
| worktree | 786 条（`git status --porcelain \| wc -l`，本轨开工实跑；246 M + 480 ??，含他线在飞） |
| sanitized | 是（本批索引 `00-index.md` §6 记录 §4.4 扫描结果） |

## 1. 主机工具

```bash
adb version
sha256sum --version | head -1
ls /c/Android/Sdk/build-tools/
```

| 工具 | 路径/版本 | 备注 |
|---|---|---|
| adb | 1.0.41（Version 37.0.1-15733141），`C:/Android/Sdk/platform-tools/adb.exe` | 本轨全程 `MSYS_NO_PATHCONV=1` |
| sha256sum | GNU coreutils 8.32 | 包/源快照哈希 |
| build-tools | 34.0.0 / 36.0.0 / 37.0.0 在装 | 本轨未用 zipalign（16KB 判据不在本批套件单） |
| unzip/grep | Git Bash 自带 | dex 符号门 `need()` 同批次 34 §4.3 原型 |

## 2. 设备

| serial | AVD | API | ABI | `getconf PAGE_SIZE` | `ro.product.cpu.page.size` | 用途 |
|---|---|---|---|---|---|---|
| emulator-5562 | test_device | 34 | x86_64 | 4096 | `<empty>`（原样记录，规范 §8.3：页大小以 getconf 为准） | 本轨专用（批次 35/36/37 同机） |

```bash
C:/Android/Sdk/platform-tools/adb.exe devices -l
C:/Android/Sdk/platform-tools/adb.exe -s emulator-5562 emu avd name        # test_device
C:/Android/Sdk/platform-tools/adb.exe -s emulator-5562 shell getprop ro.build.version.sdk   # 34
C:/Android/Sdk/platform-tools/adb.exe -s emulator-5562 shell getprop ro.product.cpu.abi     # x86_64
C:/Android/Sdk/platform-tools/adb.exe -s emulator-5562 shell getprop ro.build.fingerprint   # google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys
C:/Android/Sdk/platform-tools/adb.exe -s emulator-5562 shell getconf PAGE_SIZE              # 4096
```

- 在线设备共 4 台：emulator-5562/5566/5584（API 34）+ emulator-5596（API 23）。**5566/5584 属他线禁碰、5596 API 23 不跑本轨任何套件**（批次 35 §4.7 口径沿袭），本轨全部命令带 `-s emulator-5562`。
- 设备已开机多小时（非冷启动窗口）；本轨未做 `adb reboot`，属沉降态执行。
- 主机负载背景（与红例归因相关，E-02/E-06 引用）：开工时 4 台模拟器同机在跑，`cat /proc/loadavg`（MSYS 视角）= 7.36；宿主物理内存 64 GB 空闲 9.3 GB，CPU LoadPercentage 51%（PowerShell Get-CimInstance 实测，05:00 前后）。

## 3. 数据保护

- 设备用途：专用测试模拟器（`docs/testing/device-state-safety.md` 口径），无真实学习者数据。
- 本轨全部用例使用测试夹具库（`a09-ads-*.db` 等独立命名）或临时 schema 库；`am instrument` 均带 `-e class` 套件级限定，未触生产应用数据库。
- 测试后检查：包仍安装（`pm path` 三包 + data 测试包在装，见 step01）。

## 4. 数据保护结论

未触及真实学习者数据；未 root；未做任何 git 写操作。
