# -*- coding: utf-8 -*-
"""WP2 裁定汇总表的验收测试：表可从切片逐行复算；REBIND 每行都有目标 + 证据原句。

映射的验收条件（Stage-4 裁定汇总）：
1. `tools/kb_build/tables/content_audit_2026-09-25.csv` = 8 个 `slice-NN.verdicts.csv`
   去重排序后的并（逐行原样），表头与 `COLUMNS` 逐字相同；
2. 每行 `verdict ∈ {KEEP, REBIND, NONE}`，无空值、无非法值；
3. 每条 REBIND 都有 `suggested_node_slug`（且能在当前包的节点里解析到）与带引文的 `evidence`；
4. 靶子样本与随机抽样样本靠 `slice` 列可分（slice id ∈ SLICE_PLAN，kind 可推出样本种类）。

引文核验（`verbatim_gap_rows`）只查**引文出处**（引文是不是该材料正文的逐字片段），
**不用来判绑定对错**——绑定对错全部来自 WP2 逐条读内容的语义裁定（登记册 I-05：
归属轴的字符串度量无效，宽松 98.1% / 严格 16.4% 两个数都不可信）。
"""

from __future__ import annotations

import csv
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from kb_build import merge_content_audit_verdicts as mcv
from kb_build.audit_content_bindings import COLUMNS, SLICE_PLAN, load_facts
from kb_build.merge_content_audit_verdicts import (merge, read_slices, table_path,
                                                   verbatim_gap_rows)

# 交付时冻结的口径（写在 docs/kb-stage4-report-2026-09-25.md：样本、计数、复核命令）
TOTAL = 409
COUNTS = {"KEEP": 330, "REBIND": 56, "NONE": 23}
RANDOM_SLICE = "slice-06"          # kind="sample"：四科各 10 条的分层随机抽样
RANDOM_ROWS = 40
# 唯一一条引文对不上材料正文的 REBIND（slice-02 / phys-hj2-li-fenjie-duojie-taolun：
# 引文两端逐字，中间删了一处括注「，$0<θ<90°$」且没标省略号）——钉住它，
# 只能少不能多：多一条就说明别的切片也开始丢原句了。
KNOWN_EVIDENCE_GAP = ("slice-02", "phys-hj2-li-fenjie-duojie-taolun")


def require_slice_artifacts(directory: Path | None = None) -> None:
    """WP2 裁定切片是 build/ 产物（`.gitignore:14` `**/build/`），干净检出上没有。

    产物**完全缺席** ⇒ 显式 SkipTest（带理由），而不是把「复算 == 落盘表」这条断言
    改写成永真；只要切片在场（哪怕不全），守卫一律放行，仍由原断言判完整性/一致性
    ——所以 `-t tools` 的完整 discovery 在干净检出上跳过这两条，而部分切片会照常变红。
    """
    files = mcv.slice_files(directory)
    if not files:
        raise unittest.SkipTest(
            "缺 WP2 裁定切片：%s 下没有任何 slice-NN.verdicts.csv（干净检出无 build/；"
            "先跑 `PYTHONPATH=tools python -m kb_build.audit_content_bindings --slices` "
            "再由 WP2 逐条裁定）" % (directory or mcv.slice_dir()))


class ShippedTableTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows, cls.files = read_slices()
        cls.merged = merge(cls.rows)
        with table_path().open(encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle)
            cls.shipped_header = list(reader.fieldnames or ())
            cls.shipped = [{column: (row.get(column) or "") for column in COLUMNS}
                           for row in reader]

    def test_slice_files_are_all_present(self):
        require_slice_artifacts()
        self.assertEqual(["slice-%02d" % index for index in range(1, 9)],
                         [path.name.split(".")[0] for path in self.files],
                         "八个切片裁定文件要都在位")

    def test_shipped_header_is_verbatim(self):
        self.assertEqual(list(COLUMNS), self.shipped_header,
                         "表头必须与切片文件/COLUMNS 逐字相同（不增列不减列）")

    def test_shipped_table_equals_recomputation(self):
        require_slice_artifacts()
        self.assertEqual(self.merged, self.shipped,
                         "落盘表 != 8 个切片去重排序后的并（用 --write 重生成；表是唯一写者的产物）")

    def test_order_is_subject_then_slug(self):
        keys = [(row["subject"], row["slug"]) for row in self.shipped]
        self.assertEqual(sorted(keys), keys, "必须按 subject/slug 排序")

    def test_every_row_has_a_legal_verdict(self):
        self.assertEqual(COUNTS, {v: sum(1 for r in self.shipped if r["verdict"] == v)
                                  for v in COUNTS}, "计数变了：先改报告口径，再改这里")
        self.assertEqual(TOTAL, len(self.shipped))
        for row in self.shipped:
            self.assertIn(row["verdict"], COUNTS, "非法 verdict：%s" % row)

    def test_samples_are_distinguishable_by_slice_column(self):
        kinds = {spec["slice"]: spec["kind"] for spec in SLICE_PLAN}
        for row in self.shipped:
            self.assertIn(row["slice"], kinds, "slice 列有切片计划外的值：%s" % row["slice"])
        random_rows = [r for r in self.shipped if kinds[r["slice"]] == "sample"]
        self.assertEqual({RANDOM_SLICE}, {r["slice"] for r in random_rows},
                         "随机分层样本只该由 kind=sample 的那一个切片贡献")
        self.assertEqual(RANDOM_ROWS, len(random_rows),
                         "随机分层样本的行数变了：报告里的样本口径要同步")

    def test_rebind_rows_carry_target_and_evidence(self):
        facts = load_facts()
        slugs = {key[1] for key in facts.point}
        names = {node.get("name") for node in facts.point.values()}
        rebinds = [row for row in self.shipped if row["verdict"] == "REBIND"]
        self.assertEqual(COUNTS["REBIND"], len(rebinds))
        for row in rebinds:
            target = row["suggested_node_slug"].strip()
            self.assertTrue(target, "REBIND 缺 suggested_node_slug：%s" % row["material_slug"])
            self.assertIn(target, slugs | names,
                          "REBIND 的目标在包里解析不到：%s -> %s" % (row["material_slug"], target))
            self.assertNotEqual(target, row["current_node_slug"].strip(),
                                "REBIND 的目标 = 现绑节点（空改绑）：%s" % row["material_slug"])
            self.assertTrue(row["evidence"].strip(), "空证据：%s" % row["material_slug"])

    def test_rebind_evidence_is_grounded_in_the_material_text(self):
        """证据原句：引文要在材料正文里找得到；对不上的行数只能少不能多。"""
        gaps = verbatim_gap_rows(self.shipped)
        self.assertIn(KNOWN_EVIDENCE_GAP, gaps,
                      "已登记的那条（slice-02 转述式引文）不见了——是修好了就删掉这条已知项")
        self.assertLessEqual(len(gaps), 1, "新的 REBIND 丢了材料原句：%s" % (gaps,))


class SliceArtifactSkipTest(unittest.TestCase):
    """证明 skip 分支可达：产物出席/缺席两条路径都被钉住（缺席 ⇒ skip，无断言被改弱）。"""

    def test_absent_slices_raise_skip_with_reason(self):
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaises(unittest.SkipTest) as caught:
                require_slice_artifacts(Path(td))
            self.assertIn("slice-NN.verdicts.csv", str(caught.exception))
            self.assertIn("干净检出无 build/", str(caught.exception))

    def test_present_slices_pass_the_guard(self):
        with tempfile.TemporaryDirectory() as td:
            (Path(td) / "slice-01.verdicts.csv").write_text(
                ",".join(COLUMNS) + "\n", encoding="utf-8")
            require_slice_artifacts(Path(td))   # 不抛 = 守卫放行，原断言照跑

    def test_shipped_table_case_skips_when_slices_absent(self):
        """把两个走守卫的用例放到「切片缺席」条件下跑：必须 SkipTest（skip），不是断言红。

        这是干净检出上 CI 走的那条分支——用 patch 把默认切片目录换到空临时目录，
        证明 skip 是接线到用例上的，不只是个没人调用的守卫。
        """
        with tempfile.TemporaryDirectory() as td, \
                mock.patch.object(mcv, "slice_dir", lambda: Path(td)):
            ShippedTableTest.setUpClass()
            for name in ("test_shipped_table_equals_recomputation",
                         "test_slice_files_are_all_present"):
                with self.subTest(case=name):
                    case = ShippedTableTest(name)
                    with self.assertRaises(unittest.SkipTest):
                        getattr(case, name)()


class WriteTableGuardTest(unittest.TestCase):
    """落表防覆盖（2026-10-02 实测事故）：默认路径上是**上一轮已裁定**的表
    （409 行），新开一轮直接 --write 会把它整表换成新结果，旧裁定当场丢失。
    现在：目标已含非空 verdict ⇒ 拒绝写入（`--out` 写别处或显式 `--force` 才放行）。"""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-merge-guard-", dir=mcv.pack_io.REPO / "build"))
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        self.target = self.tmp / "content_audit.csv"

    def _rows(self, verdict: str) -> list[dict]:
        return [{"subject": "MATH", "slug": "节点", "material_slug": "m-1",
                 "current_node_slug": "节点", "suggested_node_slug": "",
                 "verdict": verdict, "evidence": "e", "slice": "slice-01"}]

    def test_refuses_to_clobber_adjudicated_table(self):
        mcv.write_table(self._rows("KEEP"), self.target)
        with self.assertRaises(ValueError) as ctx:
            mcv.write_table(self._rows(""), self.target)
        self.assertIn("裁定产物", str(ctx.exception))
        with self.target.open(encoding="utf-8-sig", newline="") as handle:
            self.assertEqual(["KEEP"], [r["verdict"] for r in csv.DictReader(handle)],
                             "被拒后原裁定必须原样在位")

    def test_force_overwrites_on_request(self):
        mcv.write_table(self._rows("KEEP"), self.target)
        mcv.write_table(self._rows(""), self.target, force=True)
        with self.target.open(encoding="utf-8-sig", newline="") as handle:
            self.assertEqual([""], [r["verdict"] for r in csv.DictReader(handle)])


if __name__ == "__main__":
    unittest.main()
