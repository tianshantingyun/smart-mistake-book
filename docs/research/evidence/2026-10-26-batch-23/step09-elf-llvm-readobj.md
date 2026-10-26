# E-09 16KB ELF 对齐：官方 LLVM 工具复核（llvm-readobj）

| 字段 | 值 |
|---|---|
| evidence-id | E-09 |
| convention-version | 1 |
| batch | 2026-10-26-batch-23 |
| step | 16KB 静态复核（E-03 §5 的官方工具补证） |
| gate-or-kd | 16KB 页适配 / KD-74 C1③ |
| verdict | PASS（14/14 × 2 APK；`llvm-readelf` 别名二进制缺失，见 §5） |
| executed-at | 2026-10-26T16:58:00+08:00 |
| operator | 证据落盘员（subagent，批次 23） |
| head | f0a145f850931887385c4bf2e1f798a0e8f2e391 |
| worktree | dirty（他线在飞；本轨只读 APK 校验） |
| device-serial | 不适用（主机只读检查，无设备命令） |
| device-avd | — |
| device-api | — |
| device-abi | arm64-v8a + x86_64（APK 内切片） |
| device-page-size | 不适用（运行时页大小见 E-03：16384） |
| apk | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk`；`app/build/outputs/apk/strictOffline/internal/app-strictOffline-internal.apk` |
| apk-sha256 | debug `e5ec3a89ec93ca1289f232082371e04a37c77a1c78c5c4c8ac0debc5720bb441`；internal `da8df2f8a80f0b18a10a8773232a29150071ae6930a349c94ae9cb58681f0e5b` |
| apk-version | 0.2.0-offline |
| exit-code | 0（两个 APK 的批量判读与 `zipalign` 均 0） |
| raw-log | `build/device-track-batch23/step09-elf-llvm-readobj-{debug,internal}.txt`（各 16 行）；`build/evidence-verify-tmp/zipalign-*.txt`（本机临时） |
| record-ref | `docs/research/2026-10-26-batch-23-record.md` §4（记录尚未生成） |
| sanitized | 是（§6；0 命中） |

## 1. 目的与判据

E-03 §5 因本机缺 `llvm-readelf` 把 ELF 子项记为 UNVERIFIED，并用自写 Python 解析补充。本步用本机已有的官方 LLVM 工具 `llvm-readobj --program-headers`（LLVM 22.1.8，程序头表的官方读取实现）重做同一判据，输入为 APK 内 `lib/arm64-v8a/*.so` 与 `lib/x86_64/*.so`（生产包只含这两个 ABI）：

1. 每个 `PT_LOAD`：`p_align >= 0x4000` 且 `(p_vaddr - p_offset) % 0x4000 == 0`；
2. `PT_GNU_RELRO`：`end = p_vaddr + p_memsz`；`end % 0x4000 == 0`，或按官方豁免——余数页 `[end, round_up(end, 0x4000))` 内无可写 `PT_LOAD` 数据（依据 `docs/research/2026-10-25-16kb-arm64-api23-build.md:59,115,129`）。

## 2. 命令（可复跑）

```bash
# 官方工具身份
"C:/Program Files/LLVM/bin/llvm-readobj" --version          # LLVM 22.1.8
ls "C:/Program Files/LLVM/bin" | grep -iE 'read|objdump'   # 只有 llvm-readobj.exe / llvm-objdump.exe

# 单个 .so：从 APK 读字节，经 stdin 交给官方工具（不落临时文件）
"C:/Program Files/LLVM/bin/llvm-readobj" --program-headers - < <(unzip -p app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk lib/x86_64/libquickjs.so)

# 批量判读（本机辅助脚本，build/ 内、不入仓；规则即 §1 两条算术）
python build/evidence-verify-tmp/check_elf.py app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
python build/evidence-verify-tmp/check_elf.py app/build/outputs/apk/strictOffline/internal/app-strictOffline-internal.apk

# ZIP 层（官方口径）
C:/Android/Sdk/build-tools/37.0.0/zipalign.exe -c -P 16 -v 4 app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
C:/Android/Sdk/build-tools/37.0.0/zipalign.exe -c -P 16 -v 4 app/build/outputs/apk/strictOffline/internal/app-strictOffline-internal.apk
```

- 工作目录：仓库根
- 退出码：0（两个 APK；`zipalign` 亦 0）

## 3. 结果摘要

| 指标 | debug | internal | 判据 |
|---|---|---|---|
| 64 位 `.so` | 14（7 arm64-v8a + 7 x86_64） | 14 | — |
| PASS | 14 | 14 | — |
| FAIL | 0 | 0 | 期望 0 |
| `LOAD` `p_align` | 全部 16384 | 同 | `>= 0x4000` |
| `(vaddr-off) % 0x4000` | 全部 0 | 同 | `== 0` |
| RELRO 整倍数 | 5/14 | 5/14 | `end % 0x4000 == 0` |
| RELRO 余数页含可写数据 | 0 个库（9 个库走豁免） | 同 | 必须 0 |
| `zipalign -c -P 16 -v 4` | exit 0 / `Verification successful` | 同 | exit 0 |

debug APK 逐库结果（internal 逐行同名同值）：

```text
file                                    LOAD aligns               RELRO end  mod 0x4000  writable-in-remainder  verdict
lib/arm64-v8a/libLiteRt.so              16384,16384,16384         0x51d000   0x1000      none                   PASS
lib/arm64-v8a/libLiteRtClGlAccelerator.so 16384,16384,16384      0x2b0000   0x0         -                      PASS
lib/arm64-v8a/libandroidx.graphics.path.so 16384,16384,16384     0x6000     0x2000      none                   PASS
lib/arm64-v8a/libdatastore_shared_counter.so 16384,16384,16384,16384 0xc000 0x0         -                      PASS
lib/arm64-v8a/liblitert_jni.so          16384,16384,16384         0x81000    0x1000      none                   PASS
lib/arm64-v8a/libmlkit_google_ocr_pipeline.so 16384,16384,16384  0xa57000   0x3000      none                   PASS
lib/arm64-v8a/libquickjs.so             16384,16384,16384         0xc5000    0x1000      none                   PASS
lib/x86_64/libLiteRt.so                 16384,16384,16384         0x703000   0x3000      none                   PASS
lib/x86_64/libLiteRtClGlAccelerator.so  16384,16384,16384         0x340000   0x0         -                      PASS
lib/x86_64/libandroidx.graphics.path.so 16384,16384,16384         0x7000     0x3000      none                   PASS
lib/x86_64/libdatastore_shared_counter.so 16384,16384,16384,16384 0xc000     0x0         -                      PASS
lib/x86_64/liblitert_jni.so             16384,16384,16384         0x80000    0x0         -                      PASS
lib/x86_64/libmlkit_google_ocr_pipeline.so 16384,16384,16384     0xae2000   0x2000      none                   PASS
lib/x86_64/libquickjs.so                16384,16384,16384         0xcf000    0x3000      none                   PASS
total=14 pass=14 fail=0
```

## 4. 关键输出（有界）

`llvm-readobj --program-headers` 对 `lib/x86_64/libquickjs.so` 的原文（节选）：

```text
  ProgramHeader { Type: PT_LOAD (0x1)  Offset: 0x0     VirtualAddress: 0x0     MemSize: 805856  Flags: R+X  Alignment: 16384 }
  ProgramHeader { Type: PT_LOAD (0x1)  Offset: 0xC4BE0 VirtualAddress: 0xC8BE0 MemSize: 25632   Flags: R+W  Alignment: 16384 }
  ProgramHeader { Type: PT_LOAD (0x1)  Offset: 0xCAD78 VirtualAddress: 0xD2D78 MemSize: 744      Flags: R+W  Alignment: 16384 }
  ProgramHeader { Type: PT_GNU_RELRO (0x7) VirtualAddress: 0xC8BE0 MemSize: 25632 }
```

算术：`RELRO end = 0xC8BE0 + 0x6420 = 0xCF000`，`0xCF000 % 0x4000 = 0x3000`；余数页 `[0xCF000, 0xD0000)` 内没有可写 `LOAD`（下一个可写段起于 `0xD2D78`）→ 按官方豁免 PASS。与 E-03 §5 的 Python 结果逐值一致（另 `libLiteRt.so` x86_64 `0x703000 mod 0x3000` 亦一致）。

## 5. 失败 / 跳过 / 异常

- 无 FAIL。
- **工具命名**：本机 `llvm-readelf` 别名二进制不存在（`ls "C:/Program Files/LLVM/bin" | grep -iE 'read'` → 仅 `llvm-readobj.exe`），NDK 仍不存在。本步使用的是同一 LLVM 发行版的官方 `llvm-readobj --program-headers`，输出即为 `llvm-readelf -l` 所需的程序头字段；按 R4 §6.4，"是否把 `llvm-readobj` 视为 `llvm-readelf` 的等价工具"仍需主会话一句裁定。本步不冒充 `llvm-readelf` 二进制。
- **未覆盖**：arm64 设备运行时（ps16k AVD 是 x86_64，见 E-03 §7）；32 位 ML Kit `.so` 的不合规切片（生产包已 `abiFilters` 裁掉，见 E-03 §5.2）。

## 6. 脱敏与去噪

- 删除：无（只摘程序头字段与批量判读表）。
- 保留：`Alignment`、`RELRO end`、余数、`zipalign` 末行。
- 扫描：R4 §4.4 四条命令 → 0 命中（结果见 `00-index.md` §6）。
- 二进制/截图：无（`.so` 未入仓，只入哈希与字段）。

## 7. 判定

**PASS（技术项）**：官方 LLVM 22.1.8 的程序头表 + 官方 16KB 判据，两个生产 APK 的 14 个 64 位 `.so` 全部合规；`zipalign -c -P 16 -v 4` 两个 APK 均 exit 0。与 E-03 §5 的自写 Python 结果逐值一致，因此 E-03 的"官方工具缺失"缺口在数据层已补上；唯一保留项是 `llvm-readelf` 别名的等价性裁定（§5）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch23/step09-elf-llvm-readobj-debug.txt`（16 行）、`step09-elf-llvm-readobj-internal.txt`（16 行）、`build/evidence-verify-tmp/zipalign-*.txt`
- 生成命令：§2
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
