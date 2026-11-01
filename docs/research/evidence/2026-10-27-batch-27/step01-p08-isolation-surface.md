# E-01 P-08 隔离启动面审计（dumpsys providers + 启动面 + Application 短路证据）

| 字段 | 值 |
|---|---|
| evidence-id | E-01 |
| convention-version | 1 |
| batch | 2026-10-27-batch-27 |
| step | 一（P-08 隔离启动面审计） |
| gate-or-kd | P-08（清单 `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:131`；R2 G-1）；契约 §4:62/§9:202 |
| verdict | **PASS（0 Provider 暴露 + 短路/启动面实证）**；R2 G-1 ① 字面命令漂移 1 项登记（§3.4） |
| executed-at | 2026-10-28T03:27:00+08:00～03:31:00+08:00（合并清单/设备 dumpsys 在同窗口完成；合并清单核为主机侧） |
| operator | 设备验证轨（subagent，批次 27） |
| head | caa0726a02f1862371a3b2770c8c29663ba7fa02 |
| worktree | dirty（他线在飞 + 批次 27 T1–T6 落层） |
| device-serial | emulator-5554（沉降态起跑） |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk`（批次 27 新源） |
| apk-sha256 | `43adac58c0b6c28e46bef7cc99ebc01b3fd59781f01e5da54581807330940a58`（设备侧一致） |
| apk-version | 0.2.0-offline |
| exit-code | 0（各命令 adb 退出码；判定是 grep 计数与标签面） |
| raw-log | `build/device-track-batch27/`：`merged-manifest.txt`、`dumpsys-activity-providers.txt`（1,048 行）、`dumpsys-package-providers.txt`（2,055 行）、`step01-isolation-logcat.txt`（1,983 行）、`live-obs.txt` |
| record-ref | 批次 27 记录（由记录员后写） |
| sanitized | 是（§6） |

## 1. 目的与判据

R2 G-1 的设备半边（清单 P-08 行"部分落"→本步出数）：① 存活 worker 观测；② provider 表
`dumpsys` 审计 `:visual_js` 0 暴露；③ 隔离事实（隔离 UID/同进程断言）；④ AndroidX Startup
在隔离进程的行为记录；⑤ worker 进程无 Room/sqlite；Application 短路证据。本步零源码改动。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# 静态面（主机侧）：合并清单 provider 审计（批次 27 新构建产物）
unzip -o -q <debug.apk> AndroidManifest.xml && \
C:/Android/Sdk/build-tools/37.0.0/aapt2.exe dump xmltree --file AndroidManifest.xml <debug.apk>
# 设备面：provider 表（两路）
$A -s emulator-5554 shell dumpsys activity providers > dumpsys-activity-providers.txt   # 1,048 行
$A -s emulator-5554 shell dumpsys package providers   > dumpsys-package-providers.txt   # 2,055 行
grep -c "visual_js" dumpsys-activity-providers.txt   # → 0
grep -c "visual_js" dumpsys-package-providers.txt    # → 0
# 存活 worker：隔离套件单用例后台跑 + ps 轮询捕获（见 step02 §2 同一窗口）
$A -s emulator-5554 shell "ps -A -o PID,NAME | grep visual_js"
# worker 日志面：套件全量 logcat 按 worker pid 分组扫描（step01-isolation-logcat.txt，1,983 行）
```

## 3. 结果摘要

### 3.1 合并清单（静态，批次 27 产物实测）——6 个 Provider 全部 default 进程

`aapt2 dump xmltree` 解析（`merged-manifest.txt`），6 个 `E: provider`：

| provider | android:process | isolatedProcess | exported |
|---|---|---|---|
| `…core.data.capture.BatchImportFileProvider` | （default，未声明） | 未设置 | false |
| `androidx.core.content.FileProvider` | （default） | 未设置 | false |
| `…core.export.MistakePdfFileProvider` | （default） | 未设置 | false |
| `…core.export.MistakeDocxFileProvider` | （default） | 未设置 | false |
| `androidx.startup.InitializationProvider` | （default） | 未设置 | false |
| `com.google.mlkit.common.internal.MlKitInitProvider` | （default） | 未设置 | false |

`:visual_js` 进程在清单中只承载一个组件：`VisualJsService`（`android:isolatedProcess="true"`、
`android:process=":visual_js"`、`exported=false`，`app/src/main/AndroidManifest.xml:68-72`）。
**0 个 provider 挂 `:visual_js`。**（v2 附录 B 的 6-Provider 静态基线在批次 27 新源上复核成立。）

### 3.2 设备 provider 表（PMS + AMS 两路）——`visual_js` 0 命中

```text
grep -c visual_js dumpsys-activity-providers.txt  → 0
grep -c visual_js dumpsys-package-providers.txt   → 0
# PMS 表内 app 侧唯一 provider 集合（去重）：
com.tingyun.smartmistakebook.offline/androidx.core.content.FileProvider
com.tingyun.smartmistakebook.offline/androidx.startup.InitializationProvider
com.tingyun.smartmistakebook.offline/com.google.mlkit.common.internal.MlKitInitProvider
com.tingyun.smartmistakebook.offline/com.tingyun.smartmistakebook.core.data.capture.BatchImportFileProvider
com.tingyun.smartmistakebook.offline/com.tingyun.smartmistakebook.core.export.MistakeDocxFileProvider
com.tingyun.smartmistakebook.offline/com.tingyun.smartmistakebook.core.export.MistakePdfFileProvider
```

与合并清单 6 个一一对应，均无进程限定（=default 进程）。`dumpsys activity providers` 在
worker 存活窗口抓取（窗口见 step02 §2）；注：AMS 侧只列**已发布** provider，主进程未运行时
app 侧为空表——故以 PMS 表（`dumpsys package providers`，装机全量、与运行态无关）为主证据，
AMS 侧 0 命中为辅证。

### 3.3 worker 日志面（16 个 worker pid 全量分组，隔离套件轮 logcat）

worker 侧日志标签分布（全部 16 个 `Start proc …:visual_js…/u0iNN` worker pid 合计 235 行）：

```text
VisualJsService: 98   CompatibilityChangeReporter: 32   ApplicationLoaders: 32
ziparchive: 32        nativeloader: 16                  SystemServiceRegistry: 16
Process: 9
sqlite 命中 = 0；room 命中 = 0；startup/InitializationProvider 命中 = 0
```

主进程对照（同窗口 901 行）：`SQLiteConnection` 打开 `smart-mistake-book.db`、StrictMode 189
行、`KnowledgeIndexRebuild`/`WM-*` 等完整初始化面。

**Application 短路实证**：worker 进程实例化了 `SmartMistakeBookApplication`（每个进程都要），
但其日志面**没有任何** StrictMode / SQLite / Room / Diagnostics 行——guard
（`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt:287-304`）
在 Room（`:337`）、备份恢复（`:334`）、StrictMode（`:313`）之前 `return`，结构性可见：主进程
有的初始化痕迹 worker 全无，worker 的应用代码痕迹只有 `VisualJsService` 自身 98 行
（warm-up/kill 日志）+ native/asset 装载（`nativeloader` 16 = `libquickjs.so` 映射、
`ziparchive` 32 = APK 资产抽取）。

**AndroidX Startup 行为记录**：`androidx.startup.InitializationProvider` 静态归属 default
进程（§3.1/§3.2），隔离进程 worker 日志 0 条 startup/init 标签——隔离进程未实例化任何
ContentProvider（含 InitializationProvider）。观察级结论：AndroidX Startup 不进入
`:visual_js`。

### 3.4 清单命令修正登记（R2 G-1 ① 字面命令在 API 34 不可用）

R2 G-1 ① 写 `adb shell pidof com.tingyun.smartmistakebook:visual_js` 非空。实测：worker 存活
（ps 命中 pid 14189，名 `com.tingyun.smartmistakebook.offline:visual_js:…VisualJsService`）时：

```text
pidof com.tingyun.smartmistakebook:visual_js                                        → 空
pidof com.tingyun.smartmistakebook.offline:visual_js:com.tingyun…VisualJsService    → 空
pidof VisualJsService                                                               → 空
pidof ':visual_js'                                                                  → 空
```

隔离进程的 `pidof`（cmdline 匹配）在 API 34 恒不命中。**可用等价命令**：
`ps -A -o PID,NAME | grep visual_js`（命中）与 AMS `Start proc <pid>:…:visual_js…/u0iNN`
日志行（全部 16 次 spawn 均有）。P-08 判定不受影响（存活观测由 ps/AMS 双路成立），但
**清单 P-08 行验收列的命令文本需修正**（归主会话/清单复核轨）。

## 4. 关键输出（有界）

```text
# AMS spawn 行样例（UTC 设备时钟；16 条同类）
10-27 19:18:37.438 544 576 I ActivityManager: Start proc 10233:com.tingyun.smartmistakebook.offline:visual_js:
com.tingyun.smartmistakebook.visualjs.VisualJsService/u0i17 for service {com.tingyun.smartmistakebook.offline/
com.tingyun.smartmistakebook.visualjs.VisualJsService}
# worker 进程唯一的应用内日志源
VisualJsService: visual_js warm-up finished; worker ready for requests   # ×16/16
```

16 次 spawn 全部到达 ready（`visual_js warm-up finished` ×16）——隔离启动面在批次 27 新源
（含 T2 真实度量器 warm-up 探针）下零失败。

## 5. 失败 / 跳过 / 异常

无本步失败。两处如实登记：① `queryContentProviders(processName=":visual_js")` 的**字面
进程内调用**未执行——仪器化测试运行在 app 主进程而非隔离进程，本仓无进程内 runner；以
PMS provider 表 dump（系统级同事实）替代，属清单验收列"设备+文档"的文档侧登记（00-index
§5.6）。② `dumpsys activity providers` 只含已发布 provider，故以 PMS 表为主证据（§3.2 注）。

## 6. 脱敏与去噪

- 删除：无（本步输出全部为系统表/日志摘录，无敏感面）。
- 保留：全部 grep 计数（含 0）、provider 清单、标签分布、命令修正记录。
- 扫描：见 `00-index.md` §6。二进制/截图：无。

## 7. 判定

**PASS（P-08 设备半边出数）**：`:visual_js` 无 ContentProvider 暴露（静态+设备双路 0 命中）、
AndroidX Startup 不进隔离进程、Application 短路结构性实证、16/16 worker 启动面零失败。
清单 P-08 行"部分落"中的 ①②③（观测口径）⑤ 可转已出数；④ 的进程内负向见 step02。
字面命令 `pidof …` 修正登记（§3.4）；`queryContentProviders` 字面调用未执行（§5）。
