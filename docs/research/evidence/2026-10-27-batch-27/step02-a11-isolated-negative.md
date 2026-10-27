# E-02 A-11 隔离进程网络/filesDir 负向（观测级）

| 字段 | 值 |
|---|---|
| evidence-id | E-02 |
| convention-version | 1 |
| batch | 2026-10-27-batch-27 |
| step | 二（A-11 隔离进程网络/filesDir 负向） |
| gate-or-kd | A-11（工单步二；按批次 27 T4 清单注记，该面按行义=P-08 验收列 ④） |
| verdict | **观测级成立**（isolated_app SELinux 域 + 无网络组 + UID/DAC/MCS 三重拒）；进程内 Socket/filesDir 写负向用例未执行（无 runner，如实登记，§5） |
| executed-at | 2026-10-28T03:27:00+08:00（存活窗口捕获）；03:47:00+08:00（pidof 探针） |
| operator | 设备验证轨（subagent，批次 27） |
| head | caa0726a02f1862371a3b2770c8c29663ba7fa02 |
| worktree | dirty（他线在飞） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | debug 主包 + debug androidTest 包（存活 worker 由套件单用例 spawn） |
| apk-sha256 | app `43adac58…`、test `cde7e27b…`（step00 §4） |
| apk-version | 0.2.0-offline |
| exit-code | 0 |
| raw-log | `build/device-track-batch27/live-obs.txt`、`pidof-probe-run2.log`、`step01-isolation.log` |
| record-ref | 批次 27 记录（由记录员后写） |
| sanitized | 是（§6） |

## 1. 目的与判据

工单步二：隔离进程网络/filesDir 访问负向"用例**或**观测"（isolatedProcess 语义下应被拒）。
本轨选择观测路线并登记原因：进程内（`Socket().connect` / `filesDir` 写）负向必须在
`:visual_js` 进程内执行代码；仪器化测试运行在 app 主进程（instrument target process），
AIDL 面也不暴露此类探针——**不新增产品 seam/不改构建的前提下只能观测**。观测对象 =
平台对该进程实际施加的隔离域/UID/组/DAC/MCS 事实（隔离语义的机制本体）。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# 后台单用例 spawn 真实 worker（aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight）
$A -s emulator-5554 shell am instrument -w -r -e class \
  com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# 存活窗口内（ps 轮询命中即采）：
$A -s emulator-5554 shell "ps -Z -p <pid> -o USER,PID,NAME"          # SELinux 域 + UID 标签
$A -s emulator-5554 shell "grep -E '^(Name|Uid|Gid|Groups):' /proc/<pid>/status"
$A -s emulator-5554 shell "cat /proc/<pid>/attr/current"             # SELinux 域原文
$A -s emulator-5554 shell "ls -Zd /data/data/com.tingyun.smartmistakebook.offline"   # shell 视角（MAC 拒）
$A -s emulator-5554 shell "run-as com.tingyun.smartmistakebook.offline ls -laZ /data/data/com.tingyun.smartmistakebook.offline | head -5"
```

## 3. 结果摘要（存活 worker pid 11232，窗口 03:27:21+08:00）

| 观测 | 实测值 | 含义 |
|---|---|---|
| `ps -Z` LABEL | `u:r:isolated_app:s0:c512,c768` | **SELinux `isolated_app` 域**——AOSP 对 isolatedProcess 的专用受限域（无网络 socket、无应用数据文件访问） |
| `ps -Z` USER | `u0_i9034` | 隔离 UID 标签（`i` = isolated） |
| `/proc/<pid>/status Uid` | `99034 99034 99034 99034` | 隔离 UID 段 99000–99999；**≠ 应用 UID**（当时 app = u0a209/10209；恢复后重装为 u0_a214/10214，两次均 ≠） |
| `Gid` | `99034 …` | 同上 |
| `Groups` | `1065 3009` | **无 3001(net_bt_admin)/3002(net_bt)/3003(INET)/3004(net_raw)/3005(net_admin)** → 内核层不可创建/使用 INET socket（Android 的 INTERNET 权限即按 AID_INET 组执行） |
| `/proc/<pid>/attr/current` | `u:r:isolated_app:s0:c512,c768` | 与 ps -Z 一致（域原文） |
| 应用数据目录（run-as 视角） | `drwx------ u0_a214 u0_a214 u:object_r:app_data_file:s0:c214,c256,c512,c768`（03:44 复核；套件时点同构 u0_a209） | 目录 mode 0700 属主 UID 10xxx——worker UID 99034 **POSIX DAC 拒**；MCS 类别集 `c214,c256,c512,c768` 与 worker 域 `c512,c768` **不交 → SELinux MLS 拒** |

**网络负向观测成立**：worker 在 `isolated_app` 域 + 无任何网络 supplementary group 下运行；
该域/组面即平台对 `isolatedProcess` 的网络拒绝机制本体（INTERNET 权限按 UID/组执行，隔离
UID 不持有应用权限——`android:isolatedProcess` 文档语义在设备上以域+组实测落地）。

**filesDir 负向观测成立**：worker UID（99034）≠ 数据目录属主（10209/10214）且目录 0700 →
POSIX 层不可读写；`app_data_file` 的 MCS 类别集与 worker 域不交 → SELinux 层独立拒绝。
双通道均指向"隔离进程无应用数据目录访问"（`SmartMistakeBookApplication.kt:290-291` 注释
声明的平台语义，实测成立）。

## 4. 进程内断言的执行情况（如实）

- `workerUid() != Process.myUid()`（AIDL 暴露的进程内 UID）：**已在设备上实际执行并通过**——
  `workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater` 先断言 `assertNotEquals`
  （`VisualJsIsolationInstrumentedTest.kt:123`）后才在 `:134` 因 compile 接线缺陷失败
  （失败消息 = "first execution: scene rejected in phase compile: …"，见 step05）；
  JUnit 首失败即停，故 `:123` 已通过。
- `Process.isIsolated()` 的字面进程内调用：未执行（无 runner）。设备侧替代事实 =
  `isolated_app` 域 + 99034 隔离 UID + 服务端 `killSelf` guard 的 OR 判据每例通过（套件
  15 条 `killing :visual_js worker` 行全部走通——该 guard 以
  `myUid != applicationInfo.uid || isIsolated()` 为前置，step05 §3.3）。
- **进程内 `Socket().connect()` / `context.filesDir` 写负向用例：未执行**（本轨不新增
  产品 seam；测试 APK 无法在隔离进程运行代码）。

## 5. 缺口登记

1. 进程内 Socket/filesDir 写的**用例级**负向证据：需要隔离进程内 runner 或产品 seam，
   均属构建/产品决策（与 step03 的 release 阻塞同源）。清单 P-08 ④ 若按"用例"口径收口，
   本观测需主会话裁定是否足够（工单原文允许"用例或观测"）。
2. `isolated_app` 域的 neverallow 细则引用未逐条抄录（AOSP sepolicy 源未在本地）——本文件
   只声称实测的域/组/UID/DAC/MCS 事实，不引用未验证的 sepolicy 条文。

## 6. 脱敏与去噪

- 删除：无。
- 保留：全部实测值（域/UID/组/模式/类别集）、pidof 探针四变体空结果（step01 §3.4）。
- 扫描：见 `00-index.md` §6。二进制/截图：无。

## 7. 判定

**观测级成立（A-11）**：隔离进程网络/filesDir 访问在平台机制层被实测拒绝（域/组/UID/
DAC/MCS 五路一致）；进程内用例级负向未执行（如实登记 + 缺口归主会话裁定口径）。

## 9. 更正（2026-10-28，证据落盘员复核；原文行保留，按 R4 §7.6 只追加）

- **原文**：§3 表（"应用数据目录（run-as 视角）"行，本文件 `:63`）与 §3"filesDir 负向观测
  成立"段（`:70`）写"MCS 类别集 `c214,c256,c512,c768` 与 worker 域 `c512,c768`
  **不交** → SELinux MLS 拒"。
- **矛盾**：两集合的交集 = `{c512,c768}`，非空——"不交"与本文件自身实测打印的类别集
  相矛盾。
- **更正**：SELinux 拒的机制表述应为 **"不支配"**——worker 域类别集 `{c512,c768}` 不是
  `app_data_file` 类别集 `{c214,c256,c512,c768}` 的超集，`l1 dom l2` 不成立。AOSP sepolicy
  对 app_data_file 的 open/search/getattr/setattr/unlink/rename 约束即要求 subject 支配
  object（`private/mls` "Constraints for app data files only" 节：
  `mlsconstrain dir { open search getattr setattr rename add_name remove_name reparent
  rmdir } (t2 != app_data_file_type or l1 dom l2 or t1 == mlstrustedsubject)`；
  `mlsconstrain { file sock_file } { open setattr unlink link rename } ( (t2 !=
  app_data_file_type and t2 != appdomain_tmpfs) or l1 dom l2 or t1 == mlstrustedsubject)`）。
  来源：AOSP `system/sepolicy` 经 GrapheneOS/platform_system_sepolicy 镜像 ref 17
  （`gh api` 实读，2026-10-28；googlesource 直连超时、aosp-mirror 无此仓库镜像——
  **单源引用**，且为该镜像分支而非 API 34 精确快照；app_data_file 支配约束为长期稳定
  文本，此保留为来源边界）。本节引用同时补充 §5.2 的"sepolicy 源未在本地"状态：
  该约束节文本现已在会话内实读取得。
- **判定不变**：filesDir 负向观测成立——DAC 通道（目录 0700、属主 u0_a214 vs worker
  UID 99034）独立成立，不依赖 MCS 表述；域 `isolated_app`、隔离 UID 99034、Groups
  `1065 3009` 均不受影响。本更正不改 verdict，仅更正 MCS 拒绝机制的名词（"不交" →
  "不支配"）。
