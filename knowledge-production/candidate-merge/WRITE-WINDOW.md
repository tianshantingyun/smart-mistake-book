# 1c 写入窗口操作手册（候选区合并·写入 staging → promote 成品包）

**原则**：单写者窗口。窗口期间任何其他写入者（另一会话、段2 工作流）都不得碰 `build/kb-staging/`；
`build/` 被 gitignore，**git 对 staging 零可见性**——并发写入只靠本手册第 1/8 步的哈希对拍检出。
凡门不绿：修到绿或把该行退回裁决表，**不许削门、不许删断言**。

## 0. 前置检查（缺一不可）

- 45 个目录批 + 5 个材料批的裁决 CSV 全部落地于 `knowledge-production/candidate-merge/verdicts/`
  （每批行数 == 批次行数 +1 表头；自检由批代理完成，脚本可再点一次行数）。
- `python tools/kb_build/merge_candidate_verdicts.py --write` 已产出：
  `authored_materials.jsonl / new_points_rows.csv / placement_review.csv / coverage_register.csv /
  rejects.csv / quarantine.csv / summary.json`。
- `placement_review.csv` 若非空：先做一次归属裁决（把 parent_hint 落到既有主题 slug 或建主题链），
  解析结果并回 `new_points_rows.csv` 与材料行，再进窗口。
- 段2（块池判定）**未在运行**；另一会话未在写 staging。

## 1. 窗口起点指纹

```bash
python tools/kb_build/staging_freeze.py --snapshot
```

## 2. 把行追加进权威表（显式、幂等）

- `new_points_rows.csv` → `tools/kb_build/tables/new_points_manual.csv`（列序一致；同名 slug 不重复追加）。
- `topic_create_rows.csv` → `tools/kb_build/tables/topic_actions.csv`（action=create，**父级先序、逐级**）。
- 需要时补 `chapter_map.csv` / `chapter_by_source.csv` / `prereq_map.csv` 行（附来源证据）。

## 3. 建多级目录 → 建点 → 落材料 → 别名

```bash
PYTHONPATH=tools python -m kb_build.merge_topics --write        # 逐级建主题
PYTHONPATH=tools python -m kb_build.create_points --write       # 建新点（父主题必须已存在）
python tools/kb_build/apply_authored_materials.py --materials knowledge-production/candidate-merge/authored_materials.jsonl --write
PYTHONPATH=tools python -m kb_build.rebuild_aliases --write     # 别名只从绑定材料标题派生，勿手写
```

## 4. 门禁（全绿才算写完）

```bash
python tools/kb_build/promote.py --dry-run                      # 23 门 + 契约 + roundtrip，须"门全绿"
python -m unittest discover -s tools/tests -t tools             # 全量测试
python tools/kb_build/staging_freeze.py --compare               # 与第 1 步对拍；除本任务预期文件外有变动即停
```

门报 chapter/prereq 类缺陷 → 回第 2 步补表（附证据）再跑，不许绕过。

## 5. 提交（staging 不入库；表与工具入库）

显式文件列表：`tools/kb_build/tables/*.csv`（有改动的）、`knowledge-production/candidate-merge/` 的
证据账（coverage_register / rejects / quarantine / summary）。**不 add 其他会话的在飞文件。**

## 6. 晋升成品包（用户已定：终态=成品包）

```bash
python tools/kb_build/promote.py                                # 唯一写成品目录的通道
python tools/kb_build/promote.py --dry-run                      # 晋升后复核（version 单调、戳复算）
```

晋升后记录验收数字：科目/主题/节点/材料计数、alias 总数、门数（以 `gate.evaluate()` 返回数为准）、
台账 version、`contentVersion` 复算一致。

## 7. 失败与回退

- 任一环节失败：先 `staging_freeze.py --compare` 确认没有第三方写入，再按工具报错修复；
- 需要整体回退：`build/kb-staging/` 可用 `pack_io.seed_staging()` 从成品重新播种（成品未被 promote 前始终完好）；
- 旧块池 `extracted_chunks.jsonl` 的句柄锁不属本窗口，勿在窗口内处理（P4 收尾项，删除需用户明令）。
