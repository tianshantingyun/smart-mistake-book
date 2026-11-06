# E-00 环境与设备身份

| 字段 | 值 |
|---|---|
| evidence-id | E-00 |
| convention-version | 1 |
| batch | 2026-11-01-batch-39 |
| verdict | PASS（信息性，不参与判定） |
| executed-at | 2026-11-01T10:16:00+08:00 |
| operator | 设备验证轨（批次 39 工作流子代理） |
| head | `a0641bb7392f380b5716afe95dd3c371dd0c7670` |
| worktree | 737 条（开工时实跑 `git status --porcelain \| wc -l`，仅记录） |
| sanitized | 是（见本文件 §5） |

## 1. 主机与命令面

```bash
MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe   # 本轨全程使用
python tools/ci/check_r04_keep_rules.py                        # 存在且本轮实跑（E-01 §2）
```

## 2. 设备（2026-11-01T10:16+08:00 实跑登记）

```bash
adb devices -l
# → emulator-5562（本轨默认）/ emulator-5566 / emulator-5584 / emulator-5596 四台在线
adb -s emulator-5562 emu avd name            # → test_device
adb -s emulator-5562 shell getprop ro.build.version.sdk    # → 34
adb -s emulator-5562 shell getprop ro.product.cpu.abi      # → x86_64
adb -s emulator-5562 shell getconf PAGE_SIZE               # → 4096
adb -s emulator-5562 shell getprop ro.build.fingerprint    # → google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys
```

| serial | AVD | API | ABI | PAGE_SIZE | 用途 |
|---|---|---|---|---|---|
| emulator-5562 | test_device | 34 | x86_64 | 4096 | 本轨专用（全部套件） |

5566/5584 属他线禁碰（批次 35 §4.7 口径）；5596 = API 23 冒烟专用机，本轨未跑任何套件。

## 3. 本批开工树态（与设备执行绑定）

- HEAD = `a0641bb7392f380b5716afe95dd3c371dd0c7670`；工作树 737 条（仅记录）。
- 判据文档实读：计划 `.zcode/plans/plan-2026-11-01-batch-39-wiring-calibration.md`（38 行全文）、
  `docs/research/2026-11-01-preview-wiring.md`（366 行全文）、
  `docs/research/2026-11-01-kd76-ready-timeout-calibration.md`（134 行全文）、
  `docs/research/2026-11-01-kd76-timing-calibration.md`（253 行全文）。
- 命令原型实读：批 37 判据 §四（`docs/research/2026-10-31-batch-37-device-criteria.md:342-451`）、
  批 34 判据 §1.4/§4.3（S=71 静态门 + need() dex 门）、批 38 E-01（`evidence/2026-11-01-batch-38/step01-rebuild-install-gates.md` 全文）。

## 4. 开工即在飞的他线落码（整文件纳入注记，本轨零编辑）

| 文件 | 状态 | 本轨核认内容 |
|---|---|---|
| `app/src/main/.../visualjs/VisualJsClientEngine.kt` | M（+156/-?，diff 特征化实读） | T4 计时缝：`ConnectStageTimestamps` 类 + `connectStageTimestampsForTest` 访问器 + 6 打点行（5 语义写点，readyAt 两成功出口）——与 R2 §2 设计一致，KDoc 自称"批次 39 T4" |
| `core/data/src/main/.../visual/TutorVisualPreviewRenderer.kt` | 新文件（untracked） | T2 预览渲染器已落（N 向量套件编译通过的前提） |
| `core/data/src/main/.../visual/VisualChainTestSeams.kt` | 新文件（untracked） | T3 公开测试缝已落（V-01 ④ 腿前提） |
| `core/data/src/androidTest/.../visual/TutorVisualPreviewRendererInstrumentedTest.kt` 等 4 套件 | 新文件（untracked） | N 向量 4 例 / X-02 6 例 / X-03 1 例 / A-10 协调器级 1 例已落 |
| `core/database/src/main/.../RoomStudyDatabase.kt` | M（+11，diff 特征化实读） | 他线新增端口读法 `readLatestLiveTutorVisualSnapshot`（本轨零触碰，DB 版本常量 71 未变） |
| `app/src/androidTest/.../V01VisualChainInstrumentedTest.kt` | M（+240，diff 特征化实读） | ④ 腿 `theRestoredChainWritesAPdfThroughTheTestSeamAndKeepsThePreviewReference` 落地（3→4 例） |

## 5. 数据保护与脱敏

- 设备用途：专用测试模拟器；未使用真实学习者数据；全部套件为夹具数据。
- 本文件不含密钥/凭据/邮箱/物理序列号/用户目录；模拟器 serial 与 `<repo>` 相对路径合规。
