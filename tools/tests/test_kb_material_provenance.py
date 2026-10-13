# -*- coding: utf-8 -*-
"""F8 材料侧 AI/provenance 三键：口径、单点注入、一次性注入器、契约镜像的用例。

六个方向（前五条对应 codec v4 的三键语义，最后一条是 Python 下限）：
① 派生口径：三键的值全部由既有字段机械推出（provider=packId、contentId=slug、
   attribute 是唯一真值），推不出来就抛而不是猜；
② `sidecar_material_keys`：三键插在 trailing `bindings` 之前 = 侧车落盘键序；
③ `ensure_doc`：v1/v2/v3 → v4 且卷内既有材料一并补齐；**幂等**（二次调用零改动）；
   半写三键 / 越界取值 / contentId≠slug / v1 来源推不出政策 → 抛；
④ 注入器（backfill_material_provenance）：小族夹具上 --write 后全 v4、100% 带键、
   索引一致、重跑零改动；坏卷整批拒（不写半升级的族）；
⑤ 契约镜像（check_pack_contract.material_provenance_gate）：v4 缺键 / v3 带键 / 索引
   对不上 → 进一致性结果（promote 会因此转红），自洽族 → 空。
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

from kb_build import backfill_material_provenance as backfill  # noqa: E402
from kb_build import check_pack_contract as cpc  # noqa: E402
from kb_build import material_provenance as prov  # noqa: E402
from kb_build import new_content as nc  # noqa: E402
from kb_build import pack_io  # noqa: E402


def _material(slug: str, **over) -> dict:
    base = {
        "slug": slug, "subject": "MATH", "type": "CONCEPT_EXPLANATION", "title": "标题",
        "summaryMarkdown": "摘要", "applicabilityMarkdown": "适用", "contentMarkdown": "正文",
        "boundaryMarkdown": "边界", "derivationKind": "REVIEWED_SYNTHESIS",
        "sourceId": "registry-kb-math-knowledge-list:math", "sourceLocator": "loc",
        "reviewedAtEpochMillis": 1784764800000,
        "bindings": [{"knowledgeNodeId": "kb:test-pack:math:atomic:node-a", "role": "PRIMARY"}],
    }
    base.update(over)
    return base


def _source(source_id: str = "registry-kb-math-knowledge-list:math") -> dict:
    return {
        "sourceId": source_id, "subject": "MATH",
        "sourceType": "AUTHORIZED_EDUCATION_MATERIAL", "title": "知识清单",
        "publisher": "曲一线", "edition": "2027版",
        "sourceUri": "https://www.example.edu/material",
        "licenseStatus": "REFERENCE_ONLY", "contentFingerprint": "X" * 64,
        "importedAtEpochMillis": 1000, "contentUsePolicy": "REVIEWED_SYNTHESIS_ONLY",
        "licenseExpression": None, "licenseUri": None, "attributionText": "结构化总结。",
    }


def _legacy_source(source_id: str = "source:moe-2020:math:teaching") -> dict:
    """v1（legacy）来源形态：恰好 10 键，没有 v2+ 的四枚政策键。"""
    return {
        "sourceId": source_id, "subject": "MATH",
        "sourceType": "AUTHORIZED_EDUCATION_MATERIAL", "title": "2020 版课标数学基础包（内置）",
        "publisher": "教育部课标（内置整理）", "edition": "2020版",
        "sourceUri": "https://www.example.edu/moe-2020-foundation",
        "licenseStatus": "REFERENCE_ONLY", "contentFingerprint": "A" * 64,
        "importedAtEpochMillis": 1784764700000,
    }


class MaterialProvenanceFieldsTest(unittest.TestCase):
    def test_fields_are_mechanically_derived(self):
        fields = prov.provenance_fields("moe-2025-four-subjects-v1", "ext-mat-abc-1")
        self.assertEqual(list(prov.PROVENANCE_KEYS), list(fields))
        self.assertEqual("AI_SYNTHESIZED", fields["contentAttribute"])
        self.assertEqual("moe-2025-four-subjects-v1", fields["contentProvider"])
        self.assertEqual("ext-mat-abc-1", fields["contentId"])

    def test_missing_inputs_raise_instead_of_guessing(self):
        for pack_id, slug in (("", "s"), ("  ", "s"), ("p", ""), ("p", "   ")):
            with self.assertRaises(prov.MaterialProvenanceError):
                prov.provenance_fields(pack_id, slug)

    def test_sidecar_key_order_inserts_provenance_before_bindings(self):
        keys = prov.sidecar_material_keys(nc.MATERIAL_KEYS)
        self.assertEqual(nc.MATERIAL_KEYS[:-1] + prov.PROVENANCE_KEYS + ("bindings",), keys)
        self.assertEqual(keys, nc.MATERIAL_SIDECAR_KEYS)  # new_content 的同步常量
        with self.assertRaises(prov.MaterialProvenanceError):
            prov.sidecar_material_keys(("slug", "title"))  # 不以 bindings 结尾 = 键序坏了

    def test_vocabulary_is_pinned(self):
        # 扩词表 = 语义变更 → 必须 bump schemaVersion 并同步 Kotlin 枚举；这里钉住唯一取值。
        self.assertEqual({"AI_SYNTHESIZED"}, prov.ATTRIBUTES)
        self.assertEqual(4, prov.SCHEMA_VERSION)


class EnsureDocTest(unittest.TestCase):
    def test_v2_doc_upgrades_materials_and_version(self):
        doc = {"schemaVersion": 2, "packId": "test-pack", "sources": [_source()],
               "materials": [_material("m-1"), _material("m-2")]}
        stats = prov.ensure_doc(doc, source="t")
        self.assertTrue(stats["changed"])
        self.assertTrue(stats["version_bumped"])
        self.assertEqual(2, stats["materials_upgraded"])
        self.assertEqual(prov.SCHEMA_VERSION, doc["schemaVersion"])
        for material in doc["materials"]:
            self.assertEqual(list(nc.MATERIAL_SIDECAR_KEYS), list(material))
            self.assertEqual("test-pack", material["contentProvider"])
            self.assertEqual(material["slug"], material["contentId"])
            self.assertEqual("AI_SYNTHESIZED", material["contentAttribute"])

    def test_second_call_is_idempotent(self):
        doc = {"schemaVersion": 2, "packId": "test-pack", "sources": [_source()],
               "materials": [_material("m-1")]}
        prov.ensure_doc(doc, source="t")
        before = pack_io.serialize(doc)
        stats = prov.ensure_doc(doc, source="t")
        self.assertFalse(stats["changed"])
        self.assertEqual(before, pack_io.serialize(doc))

    def test_existing_complete_triple_is_kept_not_rewritten(self):
        # 上架前核定后 provider 会被整族替换：ensure_doc 不许把已有取值按 packId 覆写回去。
        material = _material("m-1", contentAttribute="AI_SYNTHESIZED",
                             contentProvider="cn.example.registered-provider",
                             contentId="m-1")
        # 键序：把三键放到 bindings 之前，模拟落盘形态
        ordered = {k: material[k] for k in nc.MATERIAL_SIDECAR_KEYS}
        doc = {"schemaVersion": 4, "packId": "test-pack", "sources": [_source()],
               "materials": [ordered]}
        stats = prov.ensure_doc(doc, source="t")
        self.assertFalse(stats["changed"])
        self.assertEqual("cn.example.registered-provider",
                         doc["materials"][0]["contentProvider"])

    def test_v1_sources_get_the_only_permitted_policy(self):
        doc = {"schemaVersion": 1, "packId": "moe-2020-foundation-v1",
               "sources": [_legacy_source()], "materials": [_material("m-1")]}
        stats = prov.ensure_doc(doc, source="t")
        self.assertEqual(1, stats["sources_upgraded"])
        source = doc["sources"][0]
        self.assertEqual("REVIEWED_SYNTHESIS_ONLY", source["contentUsePolicy"])
        self.assertIsNone(source["licenseExpression"])
        self.assertIsNone(source["licenseUri"])
        self.assertIsNone(source["attributionText"])
        self.assertEqual(prov.SCHEMA_VERSION, doc["schemaVersion"])

    def test_v1_source_with_unmappable_license_is_refused(self):
        doc = {"schemaVersion": 1, "packId": "p", "sources": [_legacy_source()],
               "materials": []}
        doc["sources"][0]["licenseStatus"] = "LICENSED"
        with self.assertRaises(prov.MaterialProvenanceError):
            prov.ensure_doc(doc, source="t")

    def test_half_written_triple_is_refused_not_repaired(self):
        material = _material("m-1", contentAttribute="AI_SYNTHESIZED", contentId="m-1")
        doc = {"schemaVersion": 4, "packId": "p", "sources": [], "materials": [material]}
        with self.assertRaises(prov.MaterialProvenanceError):
            prov.ensure_doc(doc, source="t")

    def test_wrong_values_in_complete_triple_are_refused(self):
        bad = [
            _material("m-1", contentAttribute="PROBABLY_AI", contentProvider="p",
                      contentId="m-1"),
            _material("m-1", contentAttribute="AI_SYNTHESIZED", contentProvider="  ",
                      contentId="m-1"),
            _material("m-1", contentAttribute="AI_SYNTHESIZED", contentProvider="p",
                      contentId="other-slug"),
        ]
        for material in bad:
            doc = {"schemaVersion": 4, "packId": "p", "sources": [], "materials": [material]}
            with self.assertRaises(prov.MaterialProvenanceError):
                prov.ensure_doc(doc, source="t")

    def test_unknown_version_or_missing_slug_are_refused(self):
        with self.assertRaises(prov.MaterialProvenanceError):
            prov.ensure_doc({"schemaVersion": 9, "packId": "p", "materials": []}, source="t")
        with self.assertRaises(prov.MaterialProvenanceError):
            prov.ensure_doc({"schemaVersion": 2, "packId": "p",
                             "materials": [{"title": "无 slug"}]}, source="t")


class FamilyProblemsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-prov-family-", dir=pack_io.REPO / "build"))
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)

    def _index(self, names: list[str]) -> None:
        (self.tmp / pack_io.SIDECAR_INDEX_NAME).write_text(json.dumps(
            {"packId": "test-pack",
             "sidecars": [f"knowledge/{n}" for n in names]}), encoding="utf-8")

    def _sidecar(self, name: str, doc: dict) -> None:
        (self.tmp / name).write_text(json.dumps(doc, ensure_ascii=False, indent=1),
                                     encoding="utf-8")

    def test_coherent_v4_family_has_no_problems(self):
        doc = {"schemaVersion": 4, "packId": "test-pack", "sources": [_source()],
               "materials": [_material("m-1")]}
        prov.ensure_doc(doc, source="t")
        self._sidecar("moe-2025-teaching-support-v2-01.json", doc)
        self._index(["moe-2025-teaching-support-v2-01.json"])
        self.assertEqual([], prov.family_problems(self.tmp, base_keys=nc.MATERIAL_KEYS))

    def test_v4_volume_missing_a_key_is_a_problem(self):
        doc = {"schemaVersion": 4, "packId": "test-pack", "sources": [_source()],
               "materials": [_material("m-1")]}  # 十三键：v4 下缺三键
        self._sidecar("moe-2025-teaching-support-v2-01.json", doc)
        self._index(["moe-2025-teaching-support-v2-01.json"])
        problems = prov.family_problems(self.tmp, base_keys=nc.MATERIAL_KEYS)
        self.assertTrue(any("contentAttribute" in p for p in problems), problems)

    def test_v3_volume_carrying_the_triple_is_a_problem(self):
        material = _material("m-1")
        material.update(prov.provenance_fields("test-pack", "m-1"))
        doc = {"schemaVersion": 3, "packId": "test-pack", "sources": [_source()],
               "materials": [material]}
        self._sidecar("moe-2025-teaching-support-v2-01.json", doc)
        self._index(["moe-2025-teaching-support-v2-01.json"])
        problems = prov.family_problems(self.tmp, base_keys=nc.MATERIAL_KEYS)
        self.assertTrue(any("键集与 v3 口径不符" in p for p in problems), problems)

    def test_index_mismatch_and_missing_index_are_problems(self):
        self._sidecar("moe-2025-teaching-support-v2-01.json", {"schemaVersion": 4,
                      "packId": "test-pack", "sources": [], "materials": []})
        # 索引列了不存在的卷
        self._index(["moe-2025-teaching-support-v2-01.json",
                     "moe-2025-teaching-support-v2-02.json"])
        problems = prov.family_problems(self.tmp, base_keys=nc.MATERIAL_KEYS)
        self.assertTrue(any("不在磁盘上" in p for p in problems), problems)
        # 索引整个缺席
        (self.tmp / pack_io.SIDECAR_INDEX_NAME).unlink()
        problems = prov.family_problems(self.tmp, base_keys=nc.MATERIAL_KEYS)
        self.assertTrue(any("缺侧车索引" in p for p in problems), problems)

    def test_legacy_family_volume_is_covered(self):
        # 历史样例卷不在索引里（Kotlin 直挂）：族枚举必须仍然带上它，否则漏一族材料。
        self._index(["moe-2025-teaching-support-v2-01.json"])
        self._sidecar("moe-2025-teaching-support-v2-01.json", {"schemaVersion": 4,
                      "packId": "test-pack", "sources": [], "materials": []})
        self._sidecar(prov.LEGACY_SIDECAR_NAMES[0],
                      {"schemaVersion": 1, "packId": "moe-2020-foundation-v1",
                       "sources": [], "materials": [_material("m-1")]})
        self.assertIn(prov.LEGACY_SIDECAR_NAMES[0], prov.family_names(self.tmp))


class BackfillTest(unittest.TestCase):
    """一次性注入器：小族夹具上写盘 → 全 v4 + 100% 带键 + 索引一致 + 重跑零改动。"""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-prov-backfill-", dir=pack_io.REPO / "build"))
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        self.v2 = {"schemaVersion": 2, "packId": "test-pack", "sources": [_source()],
                   "materials": [_material("m-1"), _material("m-2")]}
        self.v1 = {"schemaVersion": 1, "packId": "moe-2020-foundation-v1",
                   "sources": [_legacy_source()], "materials": [_material("m-3")]}
        (self.tmp / "moe-2025-teaching-support-v2-01.json").write_text(
            json.dumps(self.v2, ensure_ascii=False, indent=1), encoding="utf-8")
        (self.tmp / prov.LEGACY_SIDECAR_NAMES[0]).write_text(
            json.dumps(self.v1, ensure_ascii=False, indent=1), encoding="utf-8")
        (self.tmp / pack_io.SIDECAR_INDEX_NAME).write_text(json.dumps(
            {"packId": "test-pack",
             "sidecars": ["knowledge/moe-2025-teaching-support-v2-01.json"]}),
            encoding="utf-8")

    def _doc(self, name: str) -> dict:
        return json.loads((self.tmp / name).read_text(encoding="utf-8"))

    def test_write_upgrades_every_volume_and_is_idempotent(self):
        stats = backfill.write(self.tmp)
        self.assertEqual(sorted(["moe-2025-teaching-support-v2-01.json",
                                 prov.LEGACY_SIDECAR_NAMES[0]]),
                         sorted(stats["written"]))
        for name in ("moe-2025-teaching-support-v2-01.json", prov.LEGACY_SIDECAR_NAMES[0]):
            doc = self._doc(name)
            self.assertEqual(prov.SCHEMA_VERSION, doc["schemaVersion"])
            for material in doc["materials"]:
                self.assertEqual(list(nc.MATERIAL_SIDECAR_KEYS), list(material))
                self.assertEqual(doc["packId"], material["contentProvider"])
                self.assertEqual(material["slug"], material["contentId"])
        # 历史样例卷的来源也升了（v4 的 codec 走 v2+ 来源键集）
        legacy_source = self._doc(prov.LEGACY_SIDECAR_NAMES[0])["sources"][0]
        self.assertEqual("REVIEWED_SYNTHESIS_ONLY", legacy_source["contentUsePolicy"])
        # 幂等：重跑零改动、产物逐字节不变
        before = {name: (self.tmp / name).read_bytes()
                  for name in ("moe-2025-teaching-support-v2-01.json",
                               prov.LEGACY_SIDECAR_NAMES[0])}
        self.assertEqual([], backfill.write(self.tmp)["written"])
        for name, blob in before.items():
            self.assertEqual(blob, (self.tmp / name).read_bytes())

    def test_dry_run_does_not_write(self):
        planned = backfill.plan(self.tmp)
        self.assertEqual([], planned["problems"])
        self.assertEqual({"volumes": 2, "volumes_v4": 0, "materials": 3, "materials_keyed": 0},
                         planned["current"])
        self.assertEqual(2, self._doc("moe-2025-teaching-support-v2-01.json")["schemaVersion"])
        self.assertEqual(1, self._doc(prov.LEGACY_SIDECAR_NAMES[0])["schemaVersion"])

    def test_broken_volume_refuses_the_whole_family(self):
        broken = self._doc("moe-2025-teaching-support-v2-01.json")
        broken["materials"][0].update({"contentAttribute": "AI_SYNTHESIZED"})  # 半写三键
        (self.tmp / "moe-2025-teaching-support-v2-01.json").write_text(
            json.dumps(broken, ensure_ascii=False, indent=1), encoding="utf-8")
        before = (self.tmp / prov.LEGACY_SIDECAR_NAMES[0]).read_bytes()
        with self.assertRaises(prov.MaterialProvenanceError):
            backfill.write(self.tmp)
        # 坏卷整批拒：另一卷不得被顺手改掉
        self.assertEqual(before, (self.tmp / prov.LEGACY_SIDECAR_NAMES[0]).read_bytes())


class ContractMirrorTest(unittest.TestCase):
    """契约镜像门（promote 会读）：v4 缺键 / v3 带键 → 红；自洽族 → 绿。"""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-prov-mirror-", dir=pack_io.REPO / "build"))
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        pack_io.use_directory(self.tmp)
        self.addCleanup(pack_io.reset_directory)

    def _family(self, doc: dict) -> None:
        (self.tmp / "moe-2025-teaching-support-v2-01.json").write_text(
            json.dumps(doc, ensure_ascii=False, indent=1), encoding="utf-8")
        (self.tmp / pack_io.SIDECAR_INDEX_NAME).write_text(json.dumps(
            {"packId": "test-pack",
             "sidecars": ["knowledge/moe-2025-teaching-support-v2-01.json"]}),
            encoding="utf-8")

    def test_coherent_v4_family_is_green(self):
        doc = {"schemaVersion": 4, "packId": "test-pack", "sources": [_source()],
               "materials": [_material("m-1")]}
        prov.ensure_doc(doc, source="t")
        self._family(doc)
        self.assertEqual([], cpc.material_provenance_gate())

    def test_v4_missing_key_is_red(self):
        self._family({"schemaVersion": 4, "packId": "test-pack", "sources": [_source()],
                      "materials": [_material("m-1")]})
        problems = cpc.material_provenance_gate()
        self.assertTrue(any("contentAttribute" in p for p in problems), problems)

    def test_v2_volume_without_keys_stays_green(self):
        # 与 codec 同宽：v2/v3 卷不因"没升 v4"变红（codec 照收，装机不受影响）。
        self._family({"schemaVersion": 2, "packId": "test-pack", "sources": [_source()],
                      "materials": [_material("m-1")]})
        self.assertEqual([], cpc.material_provenance_gate())


class ContractMirrorWiringTest(unittest.TestCase):
    """接线钉（与 F9 的 `test_evaluate_has_material_bindings_key` 同形）：`evaluate()` 必须把
    F8 门并进 consistency——否则 promote / run_kb_checks 看不到材料键集/版本问题。"""

    def test_evaluate_has_material_provenance_key(self):
        consistency, _stats = cpc.evaluate()
        self.assertIn("material_provenance", consistency,
                      "evaluate() 未接线 F8 门——promote 将看不到 F8 材料键集问题")
        self.assertIsInstance(consistency["material_provenance"], list)


if __name__ == "__main__":
    unittest.main()
