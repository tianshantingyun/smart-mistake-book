# 批次 33 设备证据索引（2026-10-29）

- 记录：`docs/research/2026-10-29-batch-33-record.md`（尚未创建；记录员后写，本轨不建）
- 计划：`.zcode/plans/plan-2026-10-29-batch-33-structure-gates-kd80.md`
- 判据：`docs/research/2026-10-29-batch-33-device-criteria.md`
- 规范：`docs/research/2026-10-26-device-evidence-convention.md` v1（R4）
- 工作树 HEAD：`6abd807fa73d9084ef629d54015bd4f725c94784`
- 工作树：dirty（223 `M` / 477 `??`，均为他线在飞 + 本轨新增 C4 测试文件；本轨零主源改动、零 git 写）
- 设备：`emulator-5562`（API 34 / x86_64 / PAGE_SIZE 4096；未触碰 `-5566`/`-5584`）
- 证据目录：`docs/research/evidence/2026-10-29-batch-33/`
- 本目录体积：**10 个 `.md` / 72 KiB（du，块大小；实际 45,424 B）/ 988 行**；最大单文件 6,204 B
  （`step01-build-install-dex.md`）。上限 16 个 / 512 KiB、单文件 64 KiB / 400 行，未超。
- 执行时间：2026-10-29 14:44–15:13 +0800

## 1. 环境

见 `step00-environment.md`。

## 2. 产物身份（宿主 sha256 = 设备 `pm path` + `sha256sum`）

| 产物 | 字节 | sha256 | versionName |
|---|---:|---|---|
| `app-strictOffline-debug.apk` | 189532816 | `b8b2e9d85b377d5b7d559affba6113c36cc243b497e22006c91d162207e55bed` | `0.2.0-offline` |
| `app-strictOffline-debug-androidTest.apk` | 1586361 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` | （空） |
| `data-debug-androidTest.apk`（**最终**，含 C4） | 197770282 | `36f79b195e6c43d7841a85da3385887eb092518d1a0aa0f7462a619fb06f7627` | （空） |
| `database-debug-androidTest.apk` | 196829970 | `a46960416329cb26de37924edaaed3b7bb643e77b9a99b467080cd7555a778e0` | （空） |

四包 `install -r -t` 均 `Success`；设备侧 sha256 与宿主逐字节一致（`step01-build-install-dex.md` §4）。
`data-debug-androidTest.apk` 有一个前一版 `c73233a9…` 仅用于 C4 红轮（见 `step08-red-attribution.md`），
其后的 34/9/11/16/24/16 与 C4 绿轮全部在**最终** `36f79b19…` 上重跑。

## 3. 步骤与结论

| E-ID | 步骤 | 门/KD | 用例数 | 判定 | 证据 |
|---|---|---:|---:|---|---|
| E-00 | 环境 / 设备身份 | — | — | PASS（信息性） | `step00-environment.md:1` |
| E-01 | 构建前一致性门 → assemble* → 源稳定 → 安装 → dex → schema 资产 | 判据 §4 | — | PASS | `step01-build-install-dex.md:1` |
| E-02 | 结构门（含 pin 分辨力） | 判据一 | 5 | PASS | `step02-structure-gates.md:1` |
| E-03 | 备份 / 裁剪器 / 提交 / 仓库 | KD-80 80-C1、80-C2、80-C3（设备半边） | 34 + 9 + 12 + 11 | PASS | `step03-backup-trimmer-commit-repo.md:1` |
| E-04 | **80-C4 视觉四表引用**（新用例） | KD-80 80-C4 | 5 | PASS | `step04-c4-visual-reference.md:1` |
| E-05 | 迁移矩阵 / 69→70 / 70→71 | schema 71 迁移面 | 15 + 4 + 3 | PASS | `step05-migrations.md:1` |
| E-06 | 一致门（原型）/ painter / PDF | 一致门（**非产品门**）；KD-78 | 16 + 24 + 16 | PASS | `step06-parity-painter-pdf.md:1` |
| E-07 | 隔离 / flags / 9 类崩溃签名 / worker 生命周期 | KD-78 ④ | 13 + 2 | PASS | `step07-isolation-flags-signatures.md:1` |
| E-08 | 红例归因（C4 首轮 2 红） | 判据 §6 | 1 例 2 测点 | A 类（已修并复跑） | `step08-red-attribution.md:1` |

全部 14 个设备套件绿：5 / 34 / 9 / 12 / 11 / 5 / 15 / 4 / 3 / 16 / 24 / 16 / 13 / 2。

## 4. KD-80 四条裁决

| 条件 | 证据 | 判定 |
|---|---|---|
| 80-C1 备份 34/34（S=71） | E-03，最终 data APK `36f79b19…` | **PASS** |
| 80-C2 表清单逐名 | 静态 `S=71` 脚本本轨实跑 `RESULT=PASS` / `EXIT=0`（PLAN=UNIQUE=JSON=90，双向差集空，hash 同值）；**设备侧未逐名**：`step01-backup-final.log` 对 `rowCounts`/`tutor_visual_snapshot` 均 0 命中，E-03 §4 自认 raw log 不打印 90 行，只记"产品路径 + 34/34" | **PARTIAL**（静态 PASS / 设备侧 UNVERIFIED） |
| 80-C3 设备 9/9 + JVM 12/17 | 设备 9/9 在 E-03；**JVM 半边本轨未执行**（本轨只允许 assemble*，`testDebugUnitTest` 不在权限内） | **PARTIAL** |
| 80-C4 视觉四表 5/5 | E-04 | **PASS** |

**结论：KD-80 保持 open。** 四条不是全部由同一 APK 上可复核的证据闭合（80-C2 的设备侧逐名清单、
80-C3 的 JVM 半边仍是 `UNVERIFIED`）；按判据 §3.5，不得写 closed。可写「80-C1 / 80-C4 在
`36f79b19…` 上已核销；80-C2 静态半边 PASS、设备侧未逐名；80-C3 设备半边 PASS、JVM 半边 PARTIAL」。

## 5. 缺口与未落盘

1. **80-C3 JVM 半边**（`SmbkSelectionPlanTest` 12、`VisualCheckpointRetentionTest` 17）：本轨权限
   只有 `assemble*`，未跑 `testDebugUnitTest` → `UNVERIFIED`；须由串行门禁脚本补。
   旁证（**不够关闭**，判据 §3.3 要求仓内命令日志）：`core/data/build/test-results/testDebugUnitTest/`
   有 2026-10-29 14:40 生成的 `TEST-…SmbkSelectionPlanTest.xml`（tests=12/skipped=0/failures=0/errors=0）
   与 `TEST-…VisualCheckpointRetentionTest.xml`（17/0/0/0），非本轨产出；两份 JVM 测试源在
   14:44 与最终源快照之间未变（`diff src-sha-before.txt src-sha-before-c4.txt` 对二者无输出）。
2. **C4 红轮 raw log**：首轮红 log 被同路径绿轮覆盖；失败行按会话控制台转录进
   `step08-red-attribution.md`，并注明"工单转录"。
3. **结构门测试文件对 HEAD 有 3 行 KDoc diff**（他线 T6 回改，非本轨）：按批次纪律整文件纳入，
   本轨未改该文件（见 `step02-structure-gates.md` §5）。
4. **JVM/强门**：不在本轨范围，未执行；本轨不据此作任何完成声明。
5. **设备 logcat 缓冲**：`logcat -c` 后仍可见 07:xx 的旧 WM 行，说明缓冲区清理不彻底；所有
   "签名计数 = 0" 是对**当前缓冲区全量**的 grep，仍成立，但不代表逐套件隔离窗口。
6. **提交范围必须包含的未跟踪文件**（2026-10-29 复核修复轮清点）：C4 的唯一测试源
   `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupVisualReferenceInstrumentedTest.kt`
   （`??`，1181 行 / 5 例；若按"本批改动清单"提交而漏掉它，80-C4 的设备绿轮将没有仓内源文件）；
   以及被 tracked KDoc 引用的三份研究文档
   `docs/research/2026-10-29-checkpoint-budget-accounting.md`、
   `docs/research/2026-10-29-backup-visual-reference-checks.md`、
   `docs/research/2026-10-29-batch-33-baseline.md`（均为 `??`；漏提交会留下悬空引用）。
   复核修复轮已对上述 4 个文件执行 `git add`（只入索引、未提交），并同批 `git add` 了本批判据
   （`docs/research/2026-10-29-batch-33-device-criteria.md`）与本证据目录（此前均 `??`，与批次
   31/32 的"证据目录入仓"惯例一致）；本轨零主源内容改动。
