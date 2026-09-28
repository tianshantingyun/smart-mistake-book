# -*- coding: utf-8 -*-
"""块池（extracted_chunks）路径的**唯一权威源**。

## 它消灭的失败

块池路径此前散布在 7 个工具里各自写死（materialize/prescreen/store_53/
office_extract/rekey/verify_*/merge_text_judgments…），换文件名时只改到几处、
其余工具继续读旧文件——同一块池出现两套数据。此后所有工具一律
`from kb_coverage.pool_path import POOL_PATH`，不再各自拼路径。

## 当前生效路径是 rekeyed 名（2026-09-29 起）

`POOL_PATH` = `tables/extracted_chunks.rekeyed.jsonl`。旧名
`extracted_chunks.jsonl` 是被 P0.2 键唯一化重排**之前**的原始池（4 本 53 有
2,309 个重复键），且其文件句柄被宿主进程持有（rename/replace 均报
WinError 5/32），**暂时无法删除或改名**。收尾计划：P4 验收阶段再试一次把
rekeyed 名 rename 回 `extracted_chunks.jsonl`；若届时句柄已放，删旧留新、
本文件改回规范名；若仍锁，保留双名并在验收报告"遗留"写明，旧文件删除动作
等用户明令，不自行删除。
"""

from __future__ import annotations

from pathlib import Path

TABLES = Path(__file__).resolve().parent / "tables"

# 当前生效块池：P0.2 重排后的唯一键池（见模块 docstring 的路径迁移说明）。
POOL_PATH = TABLES / "extracted_chunks.rekeyed.jsonl"
# 旧名原始池：被宿主句柄锁住、暂不可删；仅 rekey_pool 迁移与 P4 收尾会碰它。
LEGACY_POOL = TABLES / "extracted_chunks.jsonl"
