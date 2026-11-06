<!-- 体积自查：93 行；R4 上限 400 行 / 65536 B -->
# E-41-P13 WS1 子件：备份计划四方一致性门 + PDF `/AIGC` 独立读者复算

| 字段 | 值 |
|---|---|
| evidence-id | E-41-P13 |
| convention-version | 1 |
| batch | 2026-11-03-batch-41 |
| step | 流程（计划 §3「各 WS 内独立判据/证据子件」第 1 项 = WS1 脚本；含 KD-75 独立读者复算） |
| gate-or-kd | WS1 脚本门（计划 §2 WS1-8/9）；KD-75 独立读者 |
| verdict | **PASS**：四方一致性脚本 `exit=0`（closure 5 / schema 73 / hash `1d086050…` / 92 表 / trim 84 / v4 91 键全一致）；pypdf 6.16.2 独立读回 **7/7** 符合（页数、页面尺寸、`/AIGC` 存在性、7 键、`Label="1"`） |
| executed-at | 2026-11-03T01:44:30–01:44:42+08:00（两条命令同一批次） |
| operator | 证据轨（本文件的两个检查由证据轨**亲手执行**，不是转录） |
| head | `62522930bc8bae51be1523c954f39449e054b6af` |
| worktree | 595 条（只记录，不归因） |
| device-serial | emulator-5584（PDF 样本来源；本次读回在宿主上执行） |
| apk-sha256 | 样本绑定：app `5793300c5f453a45715e9846f1e16756dac1300e04dd68d026e6f7957bccd796`；app androidTest `0e906482a0a98d1e4b9f5bb56a3262a80a32d9f6404b80495289b22cec4e3aca`（E-41-D01 §3、E-41-D03） |
| exit-code | 四方门 **0**；独立读者 **0** |
| raw-log | `build/evidence-batch41-p13/sync-gate.out`（21 行）、`pypdf-readback.out`（7 行）、`schema-contract-skip-mimosa.out`（3 行）；脚本 `build/evidence-batch41-p13/read_aigc.py`、`schema_contract_replica.py`（`build/` 全部 gitignored，按 R4 规范只入仓本 `.md`） |
| record-ref | 计划 §2 WS1-8/9；批次 40 记录 §8 #13（"pypdf 4/4 无落盘日志"）、§9.3 |
| sanitized | 是（§5） |

## 1. 目的与判据

两件事各自有独立判据，互不代替：

1. **四方一致性门**：`SmbkSelectionPlan.kt` 常量/表清单 == 设计件 §9.11 机器块 == `SmbkSelectionPlanTest` golden == `73.json`；任一处漂移即非零退出（脚本自述 exit 0/1/2 三值）。判据：**exit=0 且逐项 OK**。
2. **独立读者**：设备侧注入过 `/AIGC` 的 PDF，必须能被**不共享本仓解析器**的第三方库读出页结构、页面尺寸与 7 键载荷。判据：**7 个样本全部可开、尺寸与源一致、`/AIGC` 只在 labeled 上出现、键集=7、`Label="1"`**。

## 2. 四方一致性门（命令与退出码）

```bash
python -I tools/ci/check_backup_plan_sync.py
# exit=0
```

关键输出（全文 `build/evidence-batch41-p13/sync-gate.out`）：

```text
  OK   closureVersion: code==block：code=5 block=5
  OK   schemaVersion: code==block==73.json：code=73 block=73 schema=73
  OK   identityHash: code==block==73.json：code=1d086050204a0da1ed6b6e3299dd9733 ...
  OK   table list: code==block==73.json：code=92 block=92 schema=92; code-only=[] schema-only=[]
  OK   alwaysTables/derivedTables/splitTables/rebuildTables：7/2/7/1
  OK   trimOrder：84
  OK   views：['library_catalog', 'knowledge_question_lattice']
  OK   category ISSUE_BOOK/MASTERY/SESSIONS/IMAGES：(1,错题本,31)/(2,掌握库,37)/(4,会话,7)/(8,图片,1)
  OK   allowedMasks == block maskPolicy.closedMasks：code=[9, 13, 15]
  OK   digest: recomputed==test golden==block golden：5c6907555ba358b9daddb532e40acc0ff76a9d175c96d47d169a22c016aad05a
  OK   v4: 91 keys and delta == {+error_cause, -material_feature}
  OK   v4: frozen closure/prefix/trim limit：closure=4 prefix=smbk-selection-v4 trimLimit=83
  OK   v4: empty-full digest golden：ee9a1b4d3f0ea7ed44eeec773f459df9d55c5df3abda32a69c62f7fdfac6b006
PASS 四方一致（code / §9.11 / golden / 73.json）
```

- 该门**只读**（脚本 docstring 声明"不写任何文件"；本轮未观察到仓内文件变化）。
- 它填的是该脚本 docstring 自述的"KD-80 类漂移已出现三次"缺口（`SmbkSelectionPlan.kt` / 设计件 §9.11 机器块 / 测试 golden / `73.json` 四处各自自洽），本次四处同值。
- CI 挂载是 **patch 草案**（`tools/ci/check_backup_plan_sync.ci.patch`），本批未改 CI 配置（计划 §2 WS1-9）。

## 3. 独立读者（pypdf 6.16.2）——本轮亲手执行

样本来源：设备侧被测应用 `filesDir/pdf-aigc-device/run-…/` 经 `run-as` 拉出的 7 个 PDF（E-41-D03 §4）。

```bash
python -I build/evidence-batch41-p13/read_aigc.py build/device-track-batch41/pdf/device-evidence
# exit=0；7 行 JSON
```

| 文件 | 字节 | sha256 | 页 | 页0(pt) | `/AIGC` | 7 键 | Label |
|---|---|---|---|---|---|---|---|
| `classic-table-source.pdf` | 924 | `931ddbc7949687896658c52828925706b4137336467a115a5bf80b05d8963bd9` | 2 | 200×200 | NO | — | — |
| `classic-table-labeled.pdf` | 1465 | `ab76ae18f0353cbbf49b9379506471baa02dbb738f42903499f77e401d527c2b` | 2 | 200×200 | **YES** | **7/7** | `1` |
| `classic-table-twice.pdf`（二次注入） | 1465 | `ab76ae18f0353cbbf49b9379506471baa02dbb738f42903499f77e401d527c2b` | 2 | 200×200 | **YES** | **7/7** | `1` |
| `xref-stream-source.pdf` | 808 | `7b711ed3f6165fd47cbef8093353ff235b64272bd99d5d84313f57f52cb7d5b1` | 2 | 200×200 | NO | — | — |
| `xref-stream-labeled.pdf` | 1300 | `84b3d59de3582fd670b6d252618c285e7f1afbcee7f1e02ab9a40ccdfe081224` | 2 | 200×200 | **YES** | **7/7** | `1` |
| `pdfdocument-chinese-source.pdf` | 21273 | `e81288cb4ac8c4146e13767330197435f452e452cafef6abb8d5a4e56b200aa8` | 2 | 595×842 | NO | — | — |
| `pdfdocument-chinese-labeled.pdf` | 21666 | `bb357b1893e736ca0003c19f90a1689115864d3d26c020835601345f0ac34623` | 2 | 595×842 | **YES** | **7/7** | `1` |

- 7 键实测为：`ContentProducer, ContentPropagator, Label, ProduceID, PropagateID, ReservedCode1, ReservedCode2`（第一次试探脚本把 `Label` 漏在期望集外、多写了 `AIGC`，读回后已修正再跑；上面是修正后的记录运行）。
- **幂等**：`classic-table-twice.pdf` 与 `classic-table-labeled.pdf` **完整 sha256 相同**（`ab76ae18…527c2b`），独立读者侧再证"二次注入同字节"。
- **读者告警归因**：`reader_warnings` 在 source 与 labeled 上**完全相同**（4KB 页那 4 个文件均为 `incorrect startxref pointer(1)` + `parsing for Object Streams`；`pdfdocument-chinese` 另有 3 条 `Error -3 while decompressing data`）。即：**注入没有新增也没有消除读者告警**，这些告警是夹具自身的特征（增量更新后 `startxref` 指向旧偏移，pypdf 能自行恢复并仍读出正确页数与元数据）。
- **不能拿它证明的**：`text_chars_all_pages=0`（夹具无文本层），所以"读回=0 字符"不是缺陷；平台渲染路径由设备用例的 `PdfRenderer` 断言覆盖（E-41-D03），pypdf 不替代它。

## 4. 结论与边界

- **可证**：四方一致（exit=0，四项同值）；7/7 PDF 可被第三方库打开且 `/AIGC` 载荷正确；二次注入字节幂等。
- **不可证**：平台 PDFium 之外的任何渲染一致性；真机；API≠34/page-size≠4096；`core/export` 生产接缝（仍是 patch 草案，WS2 轨）。
- 与批次 40 的差别：批次 40 §8 #13 记"pypdf 4/4 无脚本/日志"→ 本文件给了**可复跑脚本 + 落盘输出 + 每文件 sha**；但仍**不是**"独立读者已成 CI 门"，只是一次人工复核。

## 5. 脱敏与去噪

- 未摘录 `run-as` 的 uid、应用私有目录绝对路径中的用户名；设备 serial 为模拟器，可保留。
- 统一扫描命令与结果见 `00-index.md` §8。
