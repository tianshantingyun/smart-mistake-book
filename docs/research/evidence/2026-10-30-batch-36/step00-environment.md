# E-00 环境与设备身份

| 字段 | 值 |
|---|---|
| evidence-id | E-00 |
| convention-version | 1 |
| batch | 2026-10-30-batch-36 |
| step | 环境再登记（落盘峰会话） |
| gate-or-kd | 信息性（不参与 KD-74 覆盖判定） |
| verdict | PASS |
| executed-at | 2026-10-30T22:35:00+08:00（命令时间见 §2、§3；设备轨运行窗 08:17–08:59） |
| operator | 证据落盘员（子代理） |
| head | `4f11cd5432e6c259038fc92bd2e975aee8d304b4` |
| worktree | 706 项（`git status --porcelain \| wc -l`，仅记录） |
| device-serial | `emulator-5562`（设备轨实际使用者，`run-steps.sh` 中 `S=emulator-5562`） |
| device-avd | 未采集（`adb emu avd name` 本会话未执行） |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096（`getconf PAGE_SIZE`） |
| apk | —（本步无产物） |
| apk-sha256 | — |
| apk-version | —（`versionName` 本会话未读） |
| exit-code | 0（§2–§3 全部只读命令） |
| raw-log | `build/device-track-batch36/`（本机，64 个文件，不入仓） |
| record-ref | `docs/research/2026-10-30-batch-36-record.md`（未落盘） |
| sanitized | 是（本批 `00-index.md` §7：secrets/凭据/邮箱 0 命中；home paths 唯一命中为扫描正则自身，已人工排除；本文件无敏感字面量） |

## 1. 主机工具

```bash
for p in "C:/Android/Sdk/build-tools/37.0.0/apksigner.bat" \
         "C:/Android/Sdk/cmdline-tools/latest/bin/apkanalyzer.bat" \
         "C:/Android/Sdk/platform-tools/adb.exe" "./gradlew"; do
  [ -e "$p" ] && echo "OK $p" || echo "MISSING $p"
done
```

| 工具 | 路径/版本 | 备注 |
|---|---|---|
| adb | `C:/Android/Sdk/platform-tools/adb.exe`（存在；版本未采集） | 设备轨 `MSYS_NO_PATHCONV=1` |
| apksigner | `C:/Android/Sdk/build-tools/37.0.0/apksigner.bat`（存在） | K3 证书读取使用其输出（E-04 产物） |
| apkanalyzer | `C:/Android/Sdk/cmdline-tools/latest/bin/apkanalyzer.bat`（存在） | K5 dex 计数本批无产物（E-04） |
| gradlew | `./gradlew`（存在） | 本会话未运行任何 Gradle |
| python | 系统 PATH（`python tools/ci/check_r04_keep_rules.py` 可执行） | 本会话唯一实跑的门脚本 |

四条 `OK`，无 `MISSING`（本会话输出）。

## 2. 设备

`adb devices -l`（本会话 22:35 实跑）：

```text
emulator-5562   device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:18
emulator-5566   device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:28
emulator-5584   device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:7
emulator-5596   device product:sdk_phone_x86_64 model:Android_SDK_built_for_x86_64 device:generic_x86_64 transport_id:27
```

四台均在线；本批设备证据只用 5562，5566/5584/5596 未触碰。`emulator-5566` 在 ask 附录 §0 未列、基线文档只列三台，本会话实读为四台——按"登记现状"记入，不作为任何门值。

```bash
A="C:/Android/Sdk/platform-tools/adb.exe"
"$A" -s emulator-5562 shell getprop ro.build.version.sdk           # 34
"$A" -s emulator-5562 shell getprop ro.product.cpu.abi             # x86_64
"$A" -s emulator-5562 shell getconf PAGE_SIZE                      # 4096
"$A" -s emulator-5562 shell getprop ro.build.fingerprint           # google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys
"$A" -s emulator-5562 shell getprop ro.build.version.release       # 14
```

| serial | AVD | API | ABI | `getconf PAGE_SIZE` | fingerprint | 用途 |
|---|---|---|---|---|---|---|
| `emulator-5562` | 未采集 | 34 | x86_64 | 4096 | `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys` | 本批唯一设备 |

**偏差**：以上五值为 22:37 的会后只读复读；设备轨运行窗（08:17–08:59）日志内**没有**固化 `getprop`（`run-steps.sh` 只用 `-s emulator-5562`）。同一 AVD 的会话内复读不与运行窗矛盾，但不是运行窗内快照——按"同一模拟器身份复读"记，不冒充实测窗内值。

## 3. 源与库绑定

| 项 | 值 | 证据 |
|---|---|---|
| HEAD | `4f11cd5432e6c259038fc92bd2e975aee8d304b4` | `git rev-parse HEAD`（本会话） |
| 工作树 | 706 项脏/未跟踪 | `git status --porcelain \| wc -l`（本会话） |
| DB schema | 71 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:139` |
| 源快照绑定 | **成立**：快照 1548 文件，现树逐文件 sha256 全等 | `sha256sum -c build/device-track-batch36/src-sha-after.txt --quiet` → 无输出、`SNAPSHOT_CHECK_EXIT=0`（本会话 22:36 实跑） |

快照检查是**现树对 08:53 快照**的复核：1548 行全部匹配，说明设备轨 20 步 + R-04 K1–K4 所绑定的源与当前工作树一致，不是旧树结论。

## 4. 数据保护

- 设备用途：模拟器，专用测试（设备轨只做 `install -r -t` + `am instrument`）。
- 真实学习者数据：未使用；测试夹具构造数据。
- 设备数据目录/包残留：**本会话未检查（UNVERIFIED）**——只读口径未执行 `pm list`/`run-as`。

## 5. 脱敏与去噪

- 本文件仅含命令、设备属性、sha、路径；无令牌、凭据、邮箱、家庭目录。
- R-04 一次性 keystore 与口令载体位于 `build/`（gitignore 内），本文件不引用其内容、不记录值。
- 扫描：见 `00-index.md` §7（对本目录整目录实跑 §4.4 四条）。

## 6. 判定

PASS（信息性）。设备身份、HEAD、schema、源快照四项均可复核；AVD 名与设备数据目录未采集，已在 §2/§4 标 UNVERIFIED。
