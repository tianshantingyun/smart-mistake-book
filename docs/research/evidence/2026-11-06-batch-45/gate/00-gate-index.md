# 批次 45 · GATE 域证据索引（WS4 49–60；十组门 × 10 行 = GATE-001..100）

> 本文件只记录**本轮亲手执行**的命令、真实退出码与证据相对路径。
> HEAD `87604198feeef198d5dba7343b8c4692c5e8f937`；执行窗口 2026-11-05 10:08–10:45 +08。
> 证据根：本目录。清单：`MANIFEST.sha256`（含本文件；manifest 自身按工具约定排除）。

## 0. 十组门总表

| 组 | 行 | 判定 | 关键实值 |
|---|---|---:|---|
| 单测门 | GATE-001..010 | PASS 10 | `--rerun-tasks` 235/235 executed；12 模块 535 份 XML / **4184 tests / 0 failures / 0 errors / 0 ignored**；含 `:app` 156 例 |
| 强门 | GATE-011..020 | PASS 10 | 7 个 androidTest Kotlin 逐任务 `--rerun` exit 0；`packageLocalFirstDebug(AndroidTest) --rerun` exit 0；app APK `c5f5efd1…`、test APK `8ad0ff90…` |
| 设备 | GATE-021..030 | PASS 10 | emulator-5562 / API 34 / x86_64；两套件 **14/14 通过**（11 + 3），`counts.skipped=0`，`apk_sha256` 与本轮产物一致 |
| acceptance | GATE-031..040 | PASS 7 / FAIL 3 | `violations=0`；`counts={PASS:882, FAIL:30, BLOCKED:144, UNVERIFIED:144}`；**state=FAIL, exit=1**；`pass_with_review=0` |
| lint | GATE-041..050 | PASS 9 / FAIL 1 | 本地 `violations=0 / warnings=29`；CI 严格模式 `exit=1`（29 条复用告警升级） |
| trace | GATE-051..060 | PASS 10 | **gaps=0 / total=1200 / exit=0**（改写前 40 个 GATE 缺口全部消除） |
| 哈希 | GATE-061..070 | PASS 10 | manifest 生成 + verify `exit=0`；self-hash 由生成命令输出，`hash-verify.txt` 记录 verify 结果 |
| 6C-2 | GATE-071..080 | PASS 10 | 定向复跑 3/3；`ALLOWED_MASKS = {9,13,15}`；`BackupSelection.kt` 工作树零 diff |
| 备份四方 | GATE-081..090 | PASS 10 | `check_backup_plan_sync.py` PASS/exit 0（v4 golden 字节·摘要·行数一致） |
| AUD-08 | GATE-091..100 | PASS 3 / FAIL 3 / BLOCKED 3 / 未判定 1 | 切分门 **FAIL/exit 1**：① 4/7=0.5714 < 0.9、③ 0/16=0.0000 ≤ 0.05 |

GATE 100 行合计：**PASS 89 / FAIL 7 / BLOCKED 3 / UNVERIFIED 1**。

## 1. 为什么 acceptance 组仍有 FAIL

`acceptance_report.py` 的 `violations=0` 表示**每一行 PASS 的证据是合规的**（≥2 类独立证据、
路径存在、cmd exit=0、无跳过标记、无占位命令）。它 exit 1 的唯一原因是台账里仍有
**30 条 FAIL 行**（23 条来自批次 44 的既有缺陷 + 本轮 D0 2 条 + GATE 3 条 + FIX 2 条）。
这不是工具故障，而是“验收不通过”的机械结论。

`pass_with_review=0`：独立复核（WS5）本域未发生——本轮全部 PASS 都是机械证据 + 机械复算，
**不构成 DoD §1.5 的独立复核**。GATE-035 因此判 FAIL。

## 2. 设备轨口径与事件

- 设备：`emulator-5562`（API 34 / x86_64），`boot_id=2d913fc3-97a5-4e63-a987-0628192a4f27`。
- 首次 `am instrument`（02:24:33Z 起）触发**冷启动 ANR**：`ActivityManager: ANR in
  com.tingyun.smartmistakebook.localfirst` → `Killing 26713` → `Crash of app … running
  instrumentation`，解析到 0 例（recorder 判 FAIL）。**该次失败没有改名成通过**：处置为
  `am start -W` 预热到 `topResumedActivity=…MainActivity` 后重跑同一 class，
  结果为 11/11 通过；预热与重跑之间未改代码、未换 APK。
  该次失败的原始输出已被同 label 重跑覆盖，日志缓冲也已轮转，故**不落任何“ANR 已归档”的
  证据行**；本段只作口径说明，GATE 行不引用它。
- 未跑 `connectedLocalFirstDebugAndroidTest`（本批禁用）；设备侧一律 `adb install` +
  `am instrument`，由 `tools/ci/am_instrument_recorder.py` 录成 JUnit XML 与绑定 JSON。

## 3. 强门口径

`assembleLocalFirstDebug` / `assembleLocalFirstDebugAndroidTest` 是 **lifecycle 任务（无 action）**，
`--rerun` 对它们不产生执行，仍显示 UP-TO-DATE。因此真正的“本轮产物”证据落在
`:app:packageLocalFirstDebug` 与 `:app:packageLocalFirstDebugAndroidTest --rerun`
（各 `1 executed`，APK mtime 10:22:31Z / 10:22:34Z）。GATE-018 把这一口径写进台账，
不把 lifecycle 的 exit 0 冒充打包执行。

另：本轮第一次强门调用写错任务名（`:app:compileLocalFirstDebugAndroidTest` 有歧义），
Gradle 直接 `BUILD FAILED exit=1`、未执行任何编译。修正为 `…AndroidTestKotlin` 后重跑。
该次失败只在 `build/`（gitignored）留痕，不作为证据行。

## 4. 复跑命令（逐字）

```text
python -I tools/ci/acceptance_init.py --check
python -X utf8 -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md --out docs/research/evidence/2026-11-06-batch-45/gate/acceptance-report.json
python -X utf8 -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md --out docs/research/evidence/2026-11-06-batch-45/gate/lint.json
python -X utf8 -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md
python -I tools/ci/check_backup_plan_sync.py
python -I tools/split_golden/check_split_gate.py
python -I tools/ci/acceptance_hash_manifest.py --root docs/research/evidence/2026-11-06-batch-45/gate --verify docs/research/evidence/2026-11-06-batch-45/gate/MANIFEST.sha256
./gradlew testDebugUnitTest :app:testLocalFirstDebugUnitTest --rerun-tasks --continue --console=plain
./gradlew :core:data:testDebugUnitTest --tests com.tingyun.smartmistakebook.core.data.backup.SixC2RulingInvariantTest --rerun --console=plain
```

> 编码注意：本机 Python 3.13 的 `-I` 会忽略 `PYTHONIOENCODING`，子进程按本地代码页输出；
> 因此**不要**给 `pytest` 父进程加 `-X utf8`（会让父进程按 UTF-8 解码子进程的本地编码输出，
> 13 例假红）。工具自测的正确调用是 `python -I -m pytest … -q`。首跑 13 failed 已按此法复现
> 原因并纠正，最终 **56 passed / exit 0**（6 份分工具 XML 另计：21+8+6+9+4+8）。

## 5. 附录 A：单测逐例复算脚本（逐字）

```python
import glob,os,xml.etree.ElementTree as ET
from collections import defaultdict
pats=['core/*/build/test-results/test/*.xml','core/*/build/test-results/testDebugUnitTest/*.xml',
      'feature/*/build/test-results/test/*.xml','feature/*/build/test-results/testDebugUnitTest/*.xml',
      'app/build/test-results/testLocalFirstDebugUnitTest/*.xml']
seen=set(); agg=defaultdict(lambda:[0,0,0,0,0])
for pat in pats:
    for p in glob.glob(pat):
        rp=os.path.realpath(p)
        if rp in seen: continue
        seen.add(rp); mod=p.replace(os.sep,'/').split('/build/')[0]
        r=ET.parse(p).getroot()
        t,f,e,s=(int(r.get(k,'0')) for k in ('tests','failures','errors','skipped'))
        a=agg[mod]; a[0]+=t;a[1]+=f;a[2]+=e;a[3]+=s;a[4]+=1
print(agg)
```

## 6. 附录 B：台账逐行分类脚本（逐字）

```python
import re
for l in open('docs/acceptance/ledger.md',encoding='utf-8'):
    if not re.match(r'^\|\s*[A-Z][A-Z0-9]*-\d{3}\s*\|',l): continue
    p=[c.strip() for c in l.strip().strip('|').split('|')]
    if len(p)==6 and p[5]!='PASS': print(p[0],p[5])
```

## 7. 附录 C：报告字段提取脚本

```python
import json
d=json.load(open('docs/research/evidence/2026-11-06-batch-45/gate/acceptance-report.json',encoding='utf-8'))
print(d['total'],d['counts'],d['state'],d['exit_code'],d['ledger_sha256'],d['pass_with_review'])
```

## 8. 边界（不得读成“验收通过”）

1. 本域只做**门禁执行与证据**，不做独立复核；`pass_with_review=0` 依旧成立。
2. GATE 89 条 PASS 只证明“该门在本机本轮真实跑绿”，不证明产品可发版。
3. AUD-08 门值 FAIL、人工金标缺失、发版 7 条 P0 未闭环 → **验收不通过**。
4. 设备结论只绑本轮 4 个产物中的 localFirst debug 两个（app + androidTest）；
   其余 APK（strictOffline / internal / release）本轮未上设备，不得引用本域结果。
