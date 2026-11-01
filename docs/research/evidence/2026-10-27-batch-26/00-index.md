# 批次 26 设备证据索引（2026-10-27）

- 记录：`docs/research/2026-10-27-batch-26-record.md`（记录员已写入；回链本索引 §3/§4，并完成
  known-defects 的 KD-66 关闭与 KD-76 设备回填，见 §7）
- 计划：`.zcode/plans/plan-2026-10-27-batch-26-kd66-gate-lockfiles.md`
- 工作树 HEAD：`405c83739cfa5385aa7652ef61f3488f953c66ce`
- 证据目录：`docs/research/evidence/2026-10-27-batch-26/`
- 本目录组成：设备轨 7 个 `.md`（本索引 + step00–05）+ T4 工具半边 2 个 `.md`（step-aud08-*，
  非设备轨产物，登记见其自身文件）；体量终测见 §6
- 规范版本：`docs/research/2026-10-26-device-evidence-convention.md` v1
- 执行者：设备验证轨（subagent）；只跑 Gradle `assemble*`/`validateReleaseSigning` 与裸
  `am instrument`，**未跑 `connectedAndroidTest`**；未做 git 写操作；未改产品/测试源代码
- 本轨新增仓库内文件 = 本目录 7 个 `.md`；工作树开工即脏（688 条，他线在飞 + T1/T4/T5 落层），
  其余改动非本轨

## 1. 环境

见 `step00-environment.md:1`。设备：

| serial | AVD | API | ABI | PAGE_SIZE | 用途 |
|---|---|---|---|---|---|
| emulator-5554 | test_device | 34 | x86_64 | 4096 | 隔离门 13+2 / KD-66 门 ×3 / 冷启动 ③ 尝试 / R8 release 冒烟 / 备份抽验 |
| emulator-5556 | ps16k_x86_64 | 36 | x86_64 | 16384 | **只读核**（本批四步不含 KD-70/16KB，未安装未执行） |

- 5562/5584 属他线，全程未触碰。本批无设备环境故障；唯一一次 `adb reboot` 是 E-03 ③ 的判据
  本体（冷启动后不沉降即跑，批次 23 E-01d 先例）。
- 沉降纪律：E-01/E-02/E-04/E-05 在沉降态设备执行（开工 up 3:54、load 0.03；E-03 后自然回升）。

## 2. 产物身份

| 产物 | 本机路径 | sha256 | 备注 |
|---|---|---|---|
| app debug APK | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` | `758c0caf217f8bd3a3aaeba1c686b69c83947edce1b07fa115e9ffb8c58406c9` | 隔离门/备份用 |
| app debug androidTest APK | `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` | `cde7e27b9a6c0ab90db06f05e6f62465c729e220593b262980f374cc9becb293` | 含 T1 新门用例（classes3.dex 字符串池实测 ×4） |
| core:data androidTest APK | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | `94df5c3931db09170dc8ac52db3a074c281bc2b8869bdba3ced724e08dea8590` | 与批次 25 逐字节一致（up-to-date） |
| app release APK（一次性本地 key） | `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk` | `a4c1145145eb06e4e60281db229c7e0511106f6404c70db156a65b7e0ba2f432` | 冒烟用；cert `CN=Batch26 Local Smoke`，SHA-256 `b4bca1cb…30cc`，**非生产证书** |

三件套 debug 构建 exit=0（`BUILD SUCCESSFUL in 36s`）；release 构建 exit=0（`2m 1s`）；
`validateReleaseSigning` **exit=1**（生产 `RELEASE_*` 缺失，`step04-r8-release-smoke.md` §2.1）。
设备侧每步 `pm path`+`sha256sum` 与上表逐字节一致。

## 3. 步骤与结论

| E-ID | 步骤 | 门/KD | 设备 | 判定 | 证据（本目录相对路径） |
|---|---|---|---|---|---|
| E-00 | 环境/设备/产物身份/新用例产物级核验 | — | 5554（5556 只读核） | PASS（信息性） | `step00-environment.md` |
| E-01 | 隔离套件全量（Isolation 13 + Race 2；A-14 修复 + R15 修复后） | 隔离面 / A-14 / R15 / KD-76 ⑤ | emulator-5554 | **PASS（13/13 + 2/2）** | `step01-visualjs-isolation.md` |
| E-02 | KD-66 hard+500 ms 关闭门用例 | KD-66 关闭门 | emulator-5554 | **PASS（3 次独立运行全绿；≥ R1 §4.5 两轮独立绿）** | `step02-kd66-hard-500ms.md` |
| E-03 | KD-76 ③④⑤（冷启动即跑 + warm 挂死负控 + 加载态） | KD-76 ③④⑤ | emulator-5554 | **PARTIAL（④ 强证据 / ⑤ 复跑绿 / ③ 尝试成功但边界如实，§5）** | `step03-kd76-criteria.md` |
| E-04 | R8 release 冷启动 ×2 | KD-69 release 侧（不回退） | emulator-5554 | PASS（两次 ok/COLD；生产签名 UNVERIFIED） | `step04-r8-release-smoke.md` |
| E-05 | 备份/恢复核心类 | 备份门（不回退） | emulator-5554 | PASS（34/34） | `step05-backup-restore.md` |

### 3.1 关键结论（便于记录员转录）

- **隔离面全绿**：批次 25 红例 `aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot`
  （A-14 夹具缺陷）修复后**设备转绿**——批次 25 缺失的第三个子断言（null 回调 → BAD_REQUEST
  不占在飞槽）本轮获得设备证据；R15 修复后 Race 2/2 复绿；既有 10 例零回归。
- **KD-66 设备侧关闭门达成**：`aHungWorkerReleasesTheCallerWithinHardPlus500ms` 三次独立运行
  全绿（E-01 套件轮 + E-02 聚焦 ×2；每轮 logcat 恰 1 条 `hard deadline (1500 ms)` kill 行、
  请求 ID 各异、无隐藏重试）；断言 A1–A7 完整执行、无一放宽。**建议主会话/记录员：KD-66 可
  关闭，回链 E-02**；关闭动作与 `docs/known-defects.md` 状态改写不在本轨（批次 25 9.2 同口径）。
  #240 上游仍 OPEN（进程层防线，非上游修复）。
- **KD-76 ④ 强证据**：warm 服务（ready 门后）#240 挂死被 1500 ms 硬期限 SIGKILL（kill 行实测，
  提交后 ~1.52–1.55 s）+ 调用方 hard+500 ms 有界返回（E-02）+ 新 generation 无 BUSY。
- **KD-76 ⑤ 行为层证据 ×2 轮**：加载态三用例（首连超时/ready 超时/warm-up acquire）沉降轮与
  冷启动轮全绿；UI/文案与 R3 广告门文本同步仍未做。
- **KD-76 ③ 尝试成功但边界如实**：真实冷启动（boot_completed 后**不沉降**，loadavg 0.75）即跑
  13/13 全绿（Time 64.072）、5 条 kill 行全部归因负向夹具设计内注入（`step03` §4 归因表）、
  零首请求 kill、冷态 warm-up(~1.9 s)→首请求 `Rendered` 全链成立；**但**负载轻于批次 23 E-01d
  （0.75 vs 5.25）、产品进程/UI 首请求与契约 §4 worst-case 29.5 s 链 UNVERIFIED。**KD-76 保持
  open；JS 仍不广告（本批纪律）**。
- **KD-69（release 侧）**：批次 26 源的 release APK 两次冷启动 `ok/COLD`（1086/524 ms）、PID
  存活、9 类签名 0，相对批次 25 E-04 不回退；生产签名 UNVERIFIED（`validateReleaseSigning`
  exit=1）；中文 OCR 未跑。
- **备份**：34/34，相对批次 24/25 E-05 不回退。

## 4. 任务书四步对照

| 任务书步骤 | 结果 | 证据 |
|---|---|---|
| 一、隔离套件全量（A-14 夹具修复 + R15 修复后）：预期 12 例 + Race 2 例全绿 | **实际 13 例**（T1 新门用例已入类）+ Race 2 → **13/13 + 2/2 全绿**；无红无归因需求 | `step01-visualjs-isolation.md` |
| 二、T1 的 KD-66 hard+500ms 用例真跑：绿则达关闭门 | **3 次独立运行全绿 → 达关闭门**（≥ 两轮独立绿）；关闭动作/记录回链归主会话 | `step02-kd66-hard-500ms.md` |
| 三、KD-76 ③④⑤ 可跑则跑 | ③ 冷启动即跑 13/13 绿零意外杀（边界如实）；④ kill 行 + 计时门证据；⑤ 两轮绿 | `step03-kd76-criteria.md` |
| 四、R8 release 抽验与备份抽验（不回退） | release ×2 ok/COLD 签名 0（不回退）；备份 34/34（不回退）；设备恢复 debug 态 | `step04-r8-release-smoke.md`、`step05-backup-restore.md` |

## 5. 缺口与未落盘

1. **KD-66 关闭动作未执行**（属主会话/记录员：known-defects 状态改写 + 记录回链；设备侧判据
   已达成）。
2. **KD-76 ③ 的产品进程/UI 面与 29.5 s worst-case 链 UNVERIFIED**；"高负载"只部分覆盖
   （boot loadavg 0.75 vs E-01d 5.25）；低内存条件未覆盖；arm64/真机 UNVERIFIED。
3. **KD-76 关闭另一半（R3/契约广告门文本升级）未做**（T4/主会话面）。
4. **生产 release 签名 UNVERIFIED**（无 `RELEASE_*` 凭据，一次性本地 key `CN=Batch26 Local Smoke`）。
5. **KD-69 中文 OCR 未跑**（沿批次 24/25 缺口）。
6. ps16k / KD-70 / 16KB 面本批未执行（任务书四步不含；5556 只读核登记于 E-00）。
7. "1500 ms 内 SIGKILL"的毫秒级 kill 延迟分布未单独断言（观察窗口沿 6 s 弱口径；强口径为
   E-02 调用方 hard+500 ms 门）。

## 6. 脱敏与去噪（落盘后统一扫描，2026-10-27）

```bash
E=docs/research/evidence/2026-10-27-batch-26
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' "$E" || echo "0 hits: secrets"
grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken)[[:space:]]*[:=][[:space:]]*[^ <]' "$E" || echo "0 hits: credential assignments"
grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$E" || echo "0 hits: emails"
grep -RInE 'C:.Users.[^ ]+|/home/[^/ ]+|/Users/[^/ ]+' "$E" || echo "0 hits: home paths"
find "$E" -type f -name '*.md' -printf '%s\n' | awk '{s+=$1} END {print s " B total"}'
find "$E" -type f -name '*.md' | wc -l
du -sk "$E"
```

结果与体量终测：见本节命令实跑输出（扫描 + 行数 + 字节 + `du`）；一次性 keystore 口令在全部
文件写作 `<local-smoke>`，不落明文。物理设备 serial、真实学习者数据、截图/二进制：均无。

**2026-10-27 落盘复核轨实跑结果**（命令即上块，可复现）：secrets 0 / credential assignments 0 /
emails 0 / home paths **1**（=本文件 :105 扫描命令行自身的正则文本，非真实路径，逐条确认为
自匹配）；`<local-smoke>` 占位无明文残留。体量（含同日落盘复核更正追加后的终值）：9 个
`.md` / **70,134 B**（单文件最大 `step03-kd76-criteria.md` 12,671 B / 188 行；R4 上限
64 KiB/400 行、单批 512 KiB/16 个均未触）/ 目录 `du -sk` = 88 KiB；全
`docs/research/evidence/` = 296 KiB < 8 MiB 预算。**复核更正一处**：E-03 全量 logcat 原始
grep 有 1 条 `UnsatisfiedLinkError`（系统进程 PID 3177 噪声，非被测应用，已归因；源码锚点
两处 off-by-one 一并更正）——见 `step03-kd76-criteria.md` §9（R4 §7.6 只追加更正节，原行
未改），E-03 判定 PARTIAL 不变。

## 7. 回链检查

- [x] 每个设备数字都有 `E-ID + 文件`（§3/§3.1/§4）；step 内锚到小节/行
- [x] `docs/known-defects.md` 的 KD-66/76 Progress 回链本目录——**已回填（2026-10-27 记录员）**：
      KD-66 标题转 closed + 关闭 Progress 块（引本索引 §3.1 与 `step02` §3/§8，回链批次 26 记录
      §3.5/§4）；KD-76 追加批次 26 设备轨 Progress 块（④⑤③ 逐项引 `step01`/`step02`/`step03`）；
      KD-72 未动（保持 open，锁未生成）
- [x] 批次 26 记录 §4 的设备数字回链本索引——记录由记录员后写
- [x] 每个 E-file 有 `sanitized` 字段且 §6 扫描执行
- [x] 没有把 `build/` 路径当作关闭证据（只出现在 raw-log 与 §8/§9）
- [x] 文件均在 `docs/research/evidence/` 下（目录整体 `??` 未跟踪；本轨零 git 写操作）
- [x] 模板占位：`<local-smoke>`（一次性口令占位）无明文残留

## 8. 复跑入口（最小）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# 隔离门（13 例，含 KD-66 门用例）
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# KD-66 门单用例聚焦
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#aHungWorkerReleasesTheCallerWithinHardPlus500ms \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# Race
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsHandshakeRaceInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# 备份
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
```

构建见 `step00-environment.md` §3；冷启动 ③ 复跑见 `step03-kd76-criteria.md` §2；release 冒烟
的签名限制见 `step04-r8-release-smoke.md` §2.1。
