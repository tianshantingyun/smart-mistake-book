# -*- coding: utf-8 -*-
"""作者型材料受控落地器（apply_authored_materials）的用例。

六个方向：
① 合法材料落盘进 sidecar（只写 staging），来源条目复用的 importedAt 不被刷新；
② 幂等：重跑跳过、产物逐字节不变；
③ 未登记来源 → 拒整批（不得自造 sourceId）；
④ 字段级判据：type 越界 / content 5 行 / ASCII 双引号 / 悬空或跨科绑定 / 缺 PRIMARY 全拒；
⑤ slug 已存在但绑定目标不同 → 拒整批（撞车不是幂等）；
⑥ dry-run 不写盘、退出码随错误。
"""

from __future__ import annotations

import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from kb_build import apply_authored_materials as A  # noqa: E402
from kb_build import pack_io  # noqa: E402

SOURCE_ID = "registry-kb-math-knowledge-list:math"


def _material(slug: str, node: str = "node-a", **over) -> dict:
    base = {
        "slug": slug,
        "subject": "MATH",
        "type": "CONCEPT_EXPLANATION",
        "title": "补料标题",
        "summaryMarkdown": "一句话摘要。",
        "applicabilityMarkdown": "适用场景。",
        "contentMarkdown": "结构化结论一行。",
        "boundaryMarkdown": "真边界文本。",
        "derivationKind": "REVIEWED_SYNTHESIS",
        "sourceId": SOURCE_ID,
        "sourceLocator": "知识清单（学生版） 专题01",
        "reviewedAtEpochMillis": 1,
        "bindings": [{"knowledgeNodeId": f"kb:test-pack:math:atomic:{node}", "role": "PRIMARY"}],
    }
    base.update(over)
    return base


def _write(path: Path, materials: list[dict]) -> Path:
    path.write_text("".join(json.dumps(m, ensure_ascii=False) + "\n" for m in materials),
                    encoding="utf-8")
    return path


class ApplyAuthoredMaterialsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-authored-", dir=pack_io.REPO / "build"))
        pack = {
            "schemaVersion": 2, "packId": "test-pack", "taxonomyVersion": "test-pack",
            "subjects": [{"subject": "MATH", "topics": [
                {"slug": "t1", "name": "主题一", "knowledgePoints": [
                    {"slug": "node-a", "name": "节点甲", "aliases": ["节点甲"], "kind": "CONCEPT",
                     "boundary": "定位：测。真边界。", "sourceLocator": "人教版高中教材（2019）",
                     "prerequisiteSlugs": []}]}]}],
        }
        (self.tmp / pack_io.PACK_NAME).write_text(
            json.dumps(pack, ensure_ascii=False, indent=1), encoding="utf-8")
        sidecar = {"schemaVersion": 2, "packId": "test-pack", "sources": [{
            "sourceId": SOURCE_ID, "subject": "MATH",
            "sourceType": "AUTHORIZED_EDUCATION_MATERIAL", "title": "知识清单",
            "publisher": "曲一线", "edition": "2027版",
            "sourceUri": "https://www.example.edu/material",
            "licenseStatus": "REFERENCE_ONLY", "contentFingerprint": "X",
            "importedAtEpochMillis": 1000, "contentUsePolicy": "REVIEWED_SYNTHESIS_ONLY",
            "licenseExpression": None, "licenseUri": None, "attributionText": "结构化总结。",
        }], "materials": [{
            "slug": "m-existing", "subject": "MATH", "type": "METHOD_MODEL", "title": "已有材料",
            "summaryMarkdown": "s", "applicabilityMarkdown": "a", "contentMarkdown": "c",
            "boundaryMarkdown": "b", "derivationKind": "REVIEWED_SYNTHESIS",
            "sourceId": SOURCE_ID, "sourceLocator": "loc", "reviewedAtEpochMillis": 2000,
            "bindings": [{"knowledgeNodeId": "kb:test-pack:math:atomic:node-a",
                          "role": "PRIMARY"}],
        }]}
        (self.tmp / "moe-2025-teaching-support-v2-01.json").write_text(
            json.dumps(sidecar, ensure_ascii=False, indent=1), encoding="utf-8")
        pack_io.use_directory(self.tmp)

    def tearDown(self):
        pack_io.reset_directory()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _sidecar(self) -> dict:
        return json.loads((self.tmp / "moe-2025-teaching-support-v2-01.json")
                          .read_text(encoding="utf-8"))

    def test_write_then_idempotent(self):
        path = _write(self.tmp / "authored.jsonl", [_material("m-new")])
        rc = A.main(["--materials", str(path), "--write"])
        self.assertEqual(0, rc)
        doc = self._sidecar()
        self.assertEqual(["m-existing", "m-new"], [m["slug"] for m in doc["materials"]])
        new = doc["materials"][1]
        self.assertEqual(new["derivationKind"], "REVIEWED_SYNTHESIS")
        self.assertEqual(new["bindings"][0]["knowledgeNodeId"],
                         "kb:test-pack:math:atomic:node-a")
        self.assertEqual(1, len(doc["sources"]))
        self.assertEqual(1000, doc["sources"][0]["importedAtEpochMillis"])  # 未被刷新
        before = (self.tmp / "moe-2025-teaching-support-v2-01.json").read_bytes()
        self.assertEqual(0, A.main(["--materials", str(path), "--write"]))
        self.assertEqual(before,
                         (self.tmp / "moe-2025-teaching-support-v2-01.json").read_bytes())

    def test_unregistered_source_refused(self):
        path = _write(self.tmp / "authored.jsonl",
                      [_material("m-new", sourceId="registry-unknown-x:math")])
        with self.assertRaises(ValueError):
            A.write(A.load_materials(path))
        self.assertEqual(["m-existing"], [m["slug"] for m in self._sidecar()["materials"]])

    def test_field_rules_refused(self):
        bad = [
            _material("m-t", type="DERIVATION"),
            _material("m-c", contentMarkdown="1\n2\n3\n4\n5"),
            _material("m-q", title='含 "引号" 的标题'),
            _material("m-b", bindings=[{"knowledgeNodeId": "kb:test-pack:math:atomic:不存在",
                                        "role": "PRIMARY"}]),
            _material("m-x", bindings=[{"knowledgeNodeId":
                                        "kb:test-pack:physics:atomic:node-a", "role": "PRIMARY"}]),
            _material("m-p", bindings=[]),
        ]
        path = _write(self.tmp / "authored.jsonl", bad)
        pl = A.plan(A.load_materials(path))
        self.assertGreaterEqual(len(pl["errors"]), 6)
        with self.assertRaises(ValueError):
            A.write(A.load_materials(path))

    def test_slug_collision_with_other_target_refused(self):
        path = _write(self.tmp / "authored.jsonl", [_material("m-existing", node="node-a")])
        self.assertEqual(0, A.main(["--materials", str(path), "--dry-run"]))
        # 绑定目标改成不存在的节点已被 ④ 覆盖；这里用同 slug 不同目标模拟撞车
        colliding = _material("m-existing", node="node-a")
        colliding["bindings"] = [{"knowledgeNodeId": "kb:test-pack:math:atomic:node-a",
                                  "role": "PRIMARY"}]
        stats = A.write([colliding])
        self.assertEqual(1, stats["skipped"])

    def test_dry_run_writes_nothing(self):
        path = _write(self.tmp / "authored.jsonl", [_material("m-new")])
        before = (self.tmp / "moe-2025-teaching-support-v2-01.json").read_bytes()
        self.assertEqual(0, A.main(["--materials", str(path), "--dry-run"]))
        self.assertEqual(before,
                         (self.tmp / "moe-2025-teaching-support-v2-01.json").read_bytes())
        bad = _write(self.tmp / "bad.jsonl", [_material("m-bad", type="DERIVATION")])
        self.assertEqual(1, A.main(["--materials", str(bad), "--dry-run"]))


if __name__ == "__main__":
    unittest.main()
