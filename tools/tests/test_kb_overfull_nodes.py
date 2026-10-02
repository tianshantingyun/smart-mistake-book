# -*- coding: utf-8 -*-
"""overfull_nodes 的合成夹具测试。

钉住的失败（2026-10-02）：
- `load()` 曾用 `STAGING.glob("moe-2025-teaching-support-v2-*.json")` 枚举材料卷，
  把卷索引 `moe-2025-teaching-support-v2-index.json`（只有 packId/sidecars 两个键）
  也当材料卷读入 → `KeyError: 'materials'`，工具整体跑不通
  （旧版实跑：`PYTHONPATH=tools python -m kb_build.overfull_nodes` → exit 1）。

夹具把枚举路径钉死：
① 卷索引在位（必须不被当材料卷读）——旧实现在这一步就 KeyError；
② 索引里列出的卷 01（真材料）：alpha 5 条（>4 超配）、beta 1 条；
③ 磁盘上**不在索引里**的诱饵卷 02——枚举若退回 glob 会被读入、断言变红。
"""

from __future__ import annotations

import contextlib
import csv
import io
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from kb_build import overfull_nodes as OF
from kb_build import pack_io

PACK_ID = "test-pack-v1"


def _point(slug: str, name: str) -> dict:
    return {"slug": slug, "name": name, "aliases": [name], "kind": "CONCEPT",
            "boundary": "定位：某册 某章·某主题。", "sourceLocator": "某来源",
            "prerequisiteSlugs": []}


def _pack() -> dict:
    return {
        "schemaVersion": 2, "packId": PACK_ID, "taxonomyVersion": PACK_ID,
        "sourceNamespace": "test", "reviewedAtEpochMillis": 1,
        "sourceUri": "https://example.edu/x",
        "coverage": {"baselineId": "b", "catalogLevel": "PARTIAL",
                     "teachingSupportLevel": "PARTIAL"},
        "subjects": [{
            "subject": "MATH", "sourceFingerprint": "A" * 64,
            "topics": [{
                "slug": "t1", "name": "某章", "sourceLocator": "某来源",
                "knowledgePoints": [_point("alpha", "甲"), _point("beta", "乙")],
            }],
        }],
    }


def _node_id(slug: str) -> str:
    return f"kb:{PACK_ID}:math:atomic:{slug}"


def _material(slug: str, target: str) -> dict:
    return {"slug": slug, "subject": "MATH", "type": "CONCEPT_EXPLANATION", "title": slug,
            "summaryMarkdown": "s", "applicabilityMarkdown": "a",
            "contentMarkdown": "c", "boundaryMarkdown": "b",
            "bindings": [{"knowledgeNodeId": _node_id(target), "role": "PRIMARY"}]}


def _sidecar(materials: list[dict]) -> dict:
    return {"schemaVersion": 2, "packId": PACK_ID, "sources": [], "materials": materials}


class SyntheticFixtureTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-overfull-", dir=pack_io.REPO / "build"))
        (self.tmp / pack_io.PACK_NAME).write_text(
            json.dumps(_pack(), ensure_ascii=False), encoding="utf-8")
        # 索引里列出的卷 01：alpha 5 条（超配）、beta 1 条
        vol01 = _sidecar([_material(f"m{i}", "alpha") for i in range(1, 6)]
                         + [_material("m6", "beta")])
        (self.tmp / "moe-2025-teaching-support-v2-01.json").write_text(
            json.dumps(vol01, ensure_ascii=False), encoding="utf-8")
        # 诱饵卷 02：合法命名、不在索引里（beta 5 条）——枚举若退回 glob 会被读入
        decoy = _sidecar([_material(f"d{i}", "beta") for i in range(1, 6)])
        (self.tmp / "moe-2025-teaching-support-v2-02.json").write_text(
            json.dumps(decoy, ensure_ascii=False), encoding="utf-8")
        pack_io.dump_json(
            {"packId": PACK_ID,
             "sidecars": ["knowledge/moe-2025-teaching-support-v2-01.json"]},
            self.tmp / pack_io.SIDECAR_INDEX_NAME)
        pack_io.use_directory(self.tmp)

    def tearDown(self):
        pack_io.reset_directory()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_index_file_and_glob_decoy_are_not_read(self):
        """卷清单必须来自索引：索引文件不当材料卷读；不在索引里的卷不读。"""
        by_node, names = OF.load()
        self.assertEqual(5, len(by_node[_node_id("alpha")]))
        self.assertEqual(1, len(by_node[_node_id("beta")]),
                         "诱饵卷（不在索引里）被读入了——枚举退回了文件系统 glob")
        self.assertEqual("甲", names[_node_id("alpha")][2])
        self.assertEqual("乙", names[_node_id("beta")][2])

    def test_report_counts_and_mechanism_wording(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            rc = OF.main([])
        self.assertEqual(0, rc)
        text = out.getvalue()
        self.assertIn("1 个超配节点（同一节点 > 4 条材料）", text)
        self.assertIn("其中超出 1 条是节点内排序第 5 位及以后的绑定", text)
        self.assertIn("节点内 ≥4 条共 1 个（其中 =4 条 0 个）", text)
        # 旧自述声称超配段材料"永远不会进入预算"——与运行时（20,000 字符预算、无条数门）相反
        self.assertNotIn("永远", text)

    def test_write_csv_lands_in_work_dir(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            rc = OF.main(["--write"])
        self.assertEqual(0, rc)
        path = self.tmp / OF.OUT_NAME
        self.assertTrue(path.exists(), "--write 必须写在 work_dir()（被 use_directory 切到 tmp）")
        with path.open(encoding="utf-8", newline="") as fh:
            rows = list(csv.DictReader(fh))
        self.assertEqual(5, len(rows), "CSV 行数 = 超配节点挂着的全部绑定数")
        self.assertTrue(all(row["node_id"] == _node_id("alpha") for row in rows))
        self.assertEqual([str(i) for i in range(1, 6)], [row["rank"] for row in rows])
        self.assertTrue(all(row["verdict_keep_or_move"] == "" for row in rows))


if __name__ == "__main__":
    unittest.main()
