# -*- coding: utf-8 -*-
"""AUD-08 切分金标门工具面的机制测试（批次 26 T4）。

只验证**机制本身**：门脚本（`tools/split_golden/check_split_gate.py`）的阈值边界
（0.9 / 0.05 的开闭区间）、报告形状、冻结校验联动；度量脚本（`measure_split_accuracy.py`）
的 `--json` 机器口径与人读口径一致；导出器（`export_split_eval.py`）从 DB 快照的
确定性导出。**不验证任何真实模型质量**——设备/真实 Provider 执行归后续，全部 UNVERIFIED。

冻结正例用仓库真金标（`tables/split_golden_v1.json`，7 页 16 框，模型标注代理金标）；
门判定正反例用程序化构造的金标（canonical 序列化 + 旁车，与冻结约定逐字节同形），
全部落在临时目录，不碰真资产。

运行（仓库根）：
    python -m unittest discover -s tools/tests -t tools -k split_gate
"""
from __future__ import annotations

import hashlib
import json
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SPLIT_TOOLS = REPO / "tools" / "split_golden"
sys.path.insert(0, str(SPLIT_TOOLS))

import validate_split_golden as V  # noqa: E402
import measure_split_accuracy as M  # noqa: E402
import check_split_gate as G  # noqa: E402
import export_split_eval as X  # noqa: E402

REAL_GOLDEN = SPLIT_TOOLS / "tables" / "split_golden_v1.json"
REAL_SIDECAR = SPLIT_TOOLS / "tables" / "split_golden_v1.json.sha256"


# ---------------------------------------------------------------- 夹具构造

def _box(left: float, top: float, right: float, bottom: float) -> dict:
    return {"left": left, "top": top, "right": right, "bottom": bottom}


def _page(name: str, boxes: list[dict]) -> dict:
    return {"file": name, "subject": "数学", "layout": "整页",
            "questionCount": len(boxes), "boxes": boxes}


def write_frozen_golden(path: Path, pages: list[dict]) -> Path:
    """按冻结约定写金标 + 旁车（canonical 序列化、LF、sha256 旁车一行）。"""
    doc = {"schemaVersion": V.SCHEMA_VERSION,
           "generatedAt": "2026-10-27T00:00:00Z",
           "sampleRule": "测试夹具：程序化构造，非真实抽样",
           "evidenceGrade": V.EVIDENCE_GRADE,
           "pages": pages}
    text = V.canonical_dump(doc)
    path.write_text(text, encoding="utf-8", newline="")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    sidecar = path.with_name(path.name + ".sha256")
    sidecar.write_text(f"{digest}  {path.name}\n", encoding="utf-8", newline="")
    return sidecar


def write_app(path: Path, pages: list[tuple[str, list[dict]]]) -> None:
    path.write_text(json.dumps(
        {"pages": [{"file": name, "questions": boxes} for name, boxes in pages]},
        ensure_ascii=False), encoding="utf-8", newline="")


def two_question_page(name: str) -> dict:
    return _page(name, [_box(0.05, 0.05, 0.9, 0.4), _box(0.05, 0.5, 0.9, 0.95)])


def aligned_app_boxes(gold: dict) -> list[dict]:
    """与金标 1:1 对齐、不产生切残的应用框（各框都在对应金标题内）。"""
    return [{"left": b["left"] + 0.01, "top": b["top"] + 0.01,
             "right": b["right"] - 0.01, "bottom": b["bottom"] - 0.01}
            for b in gold["boxes"]]


def corner_box() -> dict:
    """页角小框：与任何金标题的重叠/自身面积 = 0，不参与切残，只推高应用题数。"""
    return _box(0.0, 0.0, 0.04, 0.04)


def default_golden_pages(count: int = 10) -> list[dict]:
    return [two_question_page(f"IMG_{i:04d}.jpg") for i in range(count)]


class SplitGateTestBase(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="split-gate-"))

    def tearDown(self):
        import shutil
        shutil.rmtree(self.tmp, ignore_errors=True)

    def frozen(self, pages: list[dict]) -> Path:
        path = self.tmp / f"golden_{abs(hash(tuple(p['file'] for p in pages))) % 10**8}.json"
        write_frozen_golden(path, pages)
        return path

    def run_gate(self, pages: list[dict], app: list[tuple[str, list[dict]]] | None,
                 extra: list[str] | None = None) -> tuple[int, dict, str]:
        golden = self.frozen(pages)
        argv = ["--golden", str(golden), "--sidecar", str(golden.with_name(golden.name + ".sha256"))]
        if app is None:
            argv += ["--app", str(self.tmp / "does-not-exist.json")]
        else:
            app_path = self.tmp / f"app_{len(argv)}.json"
            write_app(app_path, app)
            argv += ["--app", str(app_path)]
        argv += list(extra or [])
        return self._invoke(argv)

    def _invoke(self, argv: list[str]) -> tuple[int, dict, str]:
        import io
        from contextlib import redirect_stdout
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = G.main(argv)
        return code, G.LAST_REPORT, buffer.getvalue()


# ---------------------------------------------------------------- 门：阈值边界

class GateThresholdBoundaryTest(SplitGateTestBase):
    """0.9 / 0.05 都是**闭边界**（>= 与 <=），与度量脚本口径一字不差。"""

    def test_count_exact_at_0_9_passes(self):
        """① 恰好 9/10 = 0.9：>= 闭边界，门过（exit 0）。"""
        pages = default_golden_pages()
        app = [(pages[0]["file"], aligned_app_boxes(pages[0]) + [corner_box()])]
        app += [(p["file"], aligned_app_boxes(p)) for p in pages[1:]]
        code, report, out = self.run_gate(pages, app)
        self.assertEqual(0, code)
        self.assertTrue(report["gate"]["passed"], f"9/10=0.9 应恰好在闭边界上通过；报告：{report['gate']}")
        self.assertAlmostEqual(0.9, report["metrics"]["countExact"]["value"])
        self.assertAlmostEqual(0.0, report["metrics"]["fragment"]["value"])

    def test_count_exact_below_0_9_fails(self):
        """① 8/10 = 0.8 < 0.9：门不过（exit 1），并明示"门后自动化不启用"。"""
        pages = default_golden_pages()
        app = [(pages[0]["file"], aligned_app_boxes(pages[0]) + [corner_box()])]
        app += [(pages[1]["file"], aligned_app_boxes(pages[1]) + [corner_box()])]
        app += [(p["file"], aligned_app_boxes(p)) for p in pages[2:]]
        code, report, out = self.run_gate(pages, app)
        self.assertEqual(1, code)
        self.assertFalse(report["gate"]["passed"])
        self.assertIn("门后自动化不启用", out)

    def test_fragment_exactly_at_0_05_passes(self):
        """③ 恰好 1/20 = 0.05：<= 闭边界，门过（exit 0）。"""
        pages = default_golden_pages()
        app = [(p["file"], aligned_app_boxes(p)) for p in pages]
        target = pages[-1]["boxes"][0]
        app[-1] = (pages[-1]["file"], [
            _box(target["left"] + 0.01, target["top"] + 0.01,
                 target["right"] - 0.01, target["bottom"] - 0.01),
            _box(target["left"] + 0.02, target["top"] + 0.02,
                 target["right"] - 0.02, target["bottom"] - 0.02)])
        code, report, out = self.run_gate(pages, app)
        self.assertEqual(0, code)
        self.assertTrue(report["gate"]["passed"])
        self.assertAlmostEqual(0.05, report["metrics"]["fragment"]["value"],
                               msg="1 题被切成 2 块 / 20 题 = 恰好 0.05")

    def test_fragment_above_0_05_fails(self):
        """③ 2/20 = 0.1 > 0.05：门不过（exit 1）。"""
        pages = default_golden_pages()
        app = [(p["file"], aligned_app_boxes(p)) for p in pages]
        for index in (-1, -2):
            target = pages[index]["boxes"][0]
            app[index] = (pages[index]["file"], [
                _box(target["left"] + 0.01, target["top"] + 0.01,
                     target["right"] - 0.01, target["bottom"] - 0.01),
                _box(target["left"] + 0.02, target["top"] + 0.02,
                     target["right"] - 0.02, target["bottom"] - 0.02)])
        code, report, out = self.run_gate(pages, app)
        self.assertEqual(1, code)
        self.assertFalse(report["gate"]["passed"])
        self.assertAlmostEqual(0.1, report["metrics"]["fragment"]["value"])


# ---------------------------------------------------------------- 门：状态与报告形状

class GateReportShapeTest(SplitGateTestBase):
    REQUIRED_TOP_KEYS = {"reportSchemaVersion", "golden", "app", "metrics", "thresholds",
                         "gate", "automation", "commands", "perPage"}

    def test_report_shape_and_constants(self):
        """报告必须携带：金标 sha/页数/题数、双阈值及出处、门判定、自动化恒关、逐页表。"""
        pages = default_golden_pages()
        app = [(p["file"], aligned_app_boxes(p)) for p in pages]
        code, report, out = self.run_gate(pages, app)
        self.assertEqual(0, code)
        self.assertTrue(self.REQUIRED_TOP_KEYS <= set(report), sorted(report))
        digest = hashlib.sha256(
            (self.tmp / "x").read_bytes() if False else
            next(self.tmp.glob("golden_*.json")).read_bytes()).hexdigest()
        self.assertEqual(digest, report["golden"]["sha256"])
        self.assertEqual(len(pages), report["golden"]["pageCount"])
        self.assertEqual(sum(p["questionCount"] for p in pages), report["golden"]["questionCount"])
        self.assertEqual(V.EVIDENCE_GRADE, report["golden"]["evidenceGrade"])
        # 阈值：数值 + 出处（立项自定，不许无声改值）
        self.assertEqual(0.9, report["thresholds"]["countExactMin"]["value"])
        self.assertEqual(0.05, report["thresholds"]["fragmentMax"]["value"])
        for gate_meta in report["thresholds"].values():
            self.assertTrue(gate_meta["source"].get("quote"))
        # 门判定与自动化
        self.assertTrue(report["gate"]["passed"])
        self.assertEqual("PASS", report["gate"]["state"])
        self.assertFalse(report["automation"]["enabled"],
                         "门脚本永不切产品开关（既有裁定）——门过也一样")
        self.assertIn("不切", report["automation"]["ruling"])
        # 逐页表与统计区间
        self.assertEqual(len(pages), len(report["perPage"]))
        lo, hi = report["metrics"]["countExact"]["wilson95"]
        self.assertTrue(0.0 <= lo <= hi <= 1.0)
        flo, fhi = report["metrics"]["fragment"]["wilson95"]
        self.assertTrue(0.0 <= flo <= fhi <= 1.0)
        self.assertIn("门后自动化", out)  # 人读输出必须显式说明自动化状态

    def test_app_missing_is_not_evaluable_exit_3(self):
        """应用导出缺失：不猜、不判门（passed=null），exit 3，自动化不启用。"""
        pages = default_golden_pages()
        code, report, out = self.run_gate(pages, None)
        self.assertEqual(3, code)
        self.assertIsNone(report["gate"]["passed"])
        self.assertEqual("NOT_EVALUABLE_APP_MISSING", report["gate"]["state"])
        self.assertFalse(report["automation"]["enabled"])
        self.assertIn("门后自动化不启用", out)

    def test_freeze_failure_blocks_gate_exit_2(self):
        """金标字节漂移：冻结校验必须先挡住门（exit 2），不得带病判门。"""
        pages = default_golden_pages()
        golden = self.frozen(pages)
        raw = golden.read_text(encoding="utf-8")
        golden.write_text(raw.replace('"subject": "数学"', '"subject": "数学 "'),
                          encoding="utf-8", newline="")
        import io
        from contextlib import redirect_stdout
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = G.main(["--golden", str(golden),
                           "--sidecar", str(golden.with_name(golden.name + ".sha256")),
                           "--app", str(self.tmp / "nope.json")])
        self.assertEqual(2, code)
        self.assertEqual("NOT_EVALUABLE_FREEZE", G.LAST_REPORT["gate"]["state"])
        self.assertIn("C1", str(G.LAST_REPORT["freezeFailures"]))
        self.assertIn("门后自动化不启用", buffer.getvalue())

    def test_evidence_grade_rewrite_is_caught(self):
        """"模型标注"被改写成"人工专家"：C2 冻结常量必须红（防证据等级无声提升）。"""
        pages = default_golden_pages()
        golden = self.frozen(pages)
        raw = golden.read_text(encoding="utf-8")
        golden.write_text(raw.replace(V.EVIDENCE_GRADE, "human expert labeled"),
                          encoding="utf-8", newline="")
        import io
        from contextlib import redirect_stdout
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = G.main(["--golden", str(golden),
                           "--sidecar", str(golden.with_name(golden.name + ".sha256")),
                           "--app", str(self.tmp / "nope.json")])
        self.assertEqual(2, code)
        self.assertIn("C2", str(G.LAST_REPORT["freezeFailures"]))

    def test_evidence_md_written_with_required_sections(self):
        """--evidence-out：R4 模板精神的证据文件（E-ID 表、sha、命令、UNVERIFIED、体积自查）。"""
        pages = default_golden_pages()
        app = [(p["file"], aligned_app_boxes(p)) for p in pages]
        evidence = self.tmp / "evidence.md"
        code, _report, _out = self.run_gate(pages, app, ["--evidence-out", str(evidence)])
        self.assertEqual(0, code)
        text = evidence.read_text(encoding="utf-8")
        for marker in ("E-A08", "sha256", "命令", "exit", "UNVERIFIED",
                       "0.9", "0.05", "门后自动化", "逐页"):
            self.assertIn(marker, text, f"证据文件缺必要小节：{marker}")
        self.assertLess(len(text.encode("utf-8")), 64 * 1024, "R4 单文件 64 KiB 上限")
        self.assertLess(text.count("\n"), 400, "R4 单文件 400 行上限")

    def test_evidence_out_refuses_overwrite(self):
        """证据文件不可静默覆盖（一次运行一份，重跑换名）。"""
        pages = default_golden_pages()
        app = [(p["file"], aligned_app_boxes(p)) for p in pages]
        evidence = self.tmp / "evidence.md"
        self.run_gate(pages, app, ["--evidence-out", str(evidence)])
        import io
        from contextlib import redirect_stdout
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = G.main(self._gate_argv(pages, app) + ["--evidence-out", str(evidence)])
        self.assertEqual(2, code)
        self.assertIn("已存在", buffer.getvalue())

    def _gate_argv(self, pages, app) -> list[str]:
        golden = next(self.tmp.glob("golden_*.json"))
        app_path = self.tmp / "overwrite-app.json"
        write_app(app_path, app)
        return ["--golden", str(golden),
                "--sidecar", str(golden.with_name(golden.name + ".sha256")),
                "--app", str(app_path)]


# ---------------------------------------------------------------- 门：真金标正例

class RealGoldenTest(SplitGateTestBase):
    def test_real_frozen_golden_passes_validate(self):
        """正例：仓库冻结金标 v1 通过 C1–C6（本测试在 CI 也应绿）。"""
        _doc, failures = V.validate(REAL_GOLDEN, REAL_SIDECAR)
        self.assertEqual([], failures, f"真金标冻结校验失败：{failures}")

    def test_real_golden_without_app_export_is_exit_3(self):
        """真金标 + 无应用导出：门如实 NOT_EVALUABLE（不伪造指标）。"""
        import io
        from contextlib import redirect_stdout
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = G.main(["--golden", str(REAL_GOLDEN), "--sidecar", str(REAL_SIDECAR),
                           "--app", str(self.tmp / "nope.json")])
        self.assertEqual(3, code)
        self.assertIsNone(G.LAST_REPORT["gate"]["passed"])
        self.assertEqual(7, G.LAST_REPORT["golden"]["pageCount"])
        self.assertEqual(16, G.LAST_REPORT["golden"]["questionCount"])


# ---------------------------------------------------------------- 度量：--json 与人读口径一致

class MeasureJsonTest(SplitGateTestBase):
    def setUp(self):
        super().setUp()
        self.golden = self.tmp / "g.json"
        self.golden.write_text(json.dumps({"pages": [
            {"file": "IMG_A.jpg", "questionCount": 1,
             "boxes": [_box(0.05, 0.05, 0.9, 0.3)]},
            {"file": "IMG_B.jpg", "questionCount": 2,
             "boxes": [_box(0.05, 0.05, 0.9, 0.4), _box(0.05, 0.5, 0.9, 0.95)]},
            {"file": "IMG_C.jpg", "questionCount": 2,
             "boxes": [_box(0.05, 0.05, 0.9, 0.4), _box(0.05, 0.5, 0.9, 0.95)]},
        ]}, ensure_ascii=False), encoding="utf-8")
        self.app = self.tmp / "a.json"
        write_app(self.app, [
            ("IMG_A.jpg", []),
            ("IMG_B.jpg", [_box(0.06, 0.06, 0.89, 0.39), _box(0.05, 0.52, 0.9, 0.94)]),
        ])

    def _run(self, *extra: str) -> tuple[int, str]:
        import io
        from contextlib import redirect_stdout
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = M.main(["--golden", str(self.golden), "--app", str(self.app), *extra])
        return code, buffer.getvalue()

    def test_json_report_shape_and_values(self):
        """--json：机器口径。分母/分子必须与人读行一致（单一事实源）。"""
        code, out = self._run("--json")
        self.assertEqual(0, code)
        report = json.loads(out)
        self.assertEqual(M.COUNT_GATE, report["gates"]["count"]["threshold"])
        self.assertEqual(M.FRAGMENT_GATE, report["gates"]["fragment"]["threshold"])
        self.assertEqual(3, report["metrics"]["countExact"]["denominator"])
        self.assertEqual(2, report["metrics"]["countExact"]["successes"])
        self.assertEqual(5, report["metrics"]["fragment"]["denominator"])
        self.assertFalse(report["gatePassed"])
        self.assertFalse(report["automation"]["enabled"])
        self.assertEqual(["IMG_C.jpg"], report["missingAppPages"])
        self.assertEqual(3, len(report["pages"]))

    def test_json_deterministic(self):
        """同输入两次 --json 输出逐字节一致。"""
        _code, first = self._run("--json")
        _code, second = self._run("--json")
        self.assertEqual(first, second)

    def test_human_output_stable_and_declares_automation(self):
        """人读输出：确定性 + 指标行不变 + 门 false 时明示"门后自动化不启用"。"""
        _code, first = self._run()
        _code, second = self._run()
        self.assertEqual(first, second)
        self.assertIn("① 题数完全一致率 countExactRate = 2/3 = 0.6667", first)
        self.assertIn("③ 切残率 fragmentRate = 0/5 = 0.0000", first)
        self.assertIn("门判定（① ≥ 0.9 且 ③ ≤ 0.05，阈值未动）：false", first)
        self.assertIn("门后自动化不启用", first)

    def test_app_missing_human_exit_3_json_also_honest(self):
        """缺应用导出：人读 exit 3、--json 也如实 appMissing（两者都自动化不启用）。"""
        import io
        from contextlib import redirect_stdout
        missing = self.tmp / "nope.json"
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = M.main(["--golden", str(self.golden), "--app", str(missing)])
        self.assertEqual(3, code)
        self.assertIn("门后自动化不启用", buffer.getvalue())
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = M.main(["--golden", str(self.golden), "--app", str(missing), "--json"])
        self.assertEqual(3, code)
        report = json.loads(buffer.getvalue())
        self.assertFalse(report["app"]["exists"])
        self.assertIsNone(report["gatePassed"])
        self.assertFalse(report["automation"]["enabled"])


# ---------------------------------------------------------------- 导出器

class ExportSplitEvalTest(SplitGateTestBase):
    def _make_db(self, path: Path, with_batch: bool = True) -> None:
        conn = sqlite3.connect(path)
        conn.executescript("""
        CREATE TABLE split_import_job (job_id TEXT PRIMARY KEY, source_kind TEXT,
            source_fingerprint TEXT, source_uri TEXT, page_count INTEGER,
            question_count INTEGER, status TEXT, created_at_epoch_millis INTEGER,
            updated_at_epoch_millis INTEGER);
        CREATE TABLE split_import_question (job_id TEXT, question_ordinal INTEGER,
            page_index INTEGER, region_left REAL, region_top REAL, region_right REAL,
            region_bottom REAL, selected INTEGER, confirm_state TEXT, split_draft_id TEXT,
            PRIMARY KEY (job_id, question_ordinal));
        """)
        if with_batch:
            conn.executescript("""
            CREATE TABLE batch_import_job (job_id TEXT PRIMARY KEY, request_id TEXT,
                request_fingerprint TEXT, status TEXT, created_at_epoch_millis INTEGER,
                updated_at_epoch_millis INTEGER);
            CREATE TABLE batch_import_page (job_id TEXT, page_index INTEGER, source_uri TEXT,
                status TEXT, result_draft_id TEXT, failure_code TEXT, attempt_count INTEGER,
                created_at_epoch_millis INTEGER, updated_at_epoch_millis INTEGER,
                boundary_after_status TEXT DEFAULT 'PENDING',
                split_after_status TEXT DEFAULT 'PENDING',
                PRIMARY KEY (job_id, page_index));
            """)
        conn.commit()
        conn.close()

    def _seed(self, db: Path) -> None:
        conn = sqlite3.connect(db)
        conn.execute("INSERT INTO batch_import_job VALUES ('B1','r1','f1','DONE',1,2)")
        conn.execute("INSERT INTO batch_import_page VALUES "
                     "('B1',0,'file:///dcim/IMG_1001.jpg','DONE',NULL,NULL,1,1,2,'NEXT_QUESTION','NEXT_QUESTION')")
        conn.execute("INSERT INTO batch_import_page VALUES "
                     "('B1',1,'file:///dcim/IMG_1002.jpg','DONE',NULL,NULL,1,1,2,'NEXT_QUESTION','NEXT_QUESTION')")
        conn.execute("INSERT INTO split_import_job VALUES "
                     "('split:B1:0','BATCH','fp0','file:///dcim/IMG_1001.jpg',1,2,'READY',1,2)")
        conn.execute("INSERT INTO split_import_question VALUES "
                     "('split:B1:0',0,0,0.05,0.05,0.9,0.4,1,'PENDING',NULL)")
        conn.execute("INSERT INTO split_import_question VALUES "
                     "('split:B1:0',1,0,0.05,0.5,0.9,0.95,1,'CONFIRMED','d1')")
        conn.execute("INSERT INTO split_import_job VALUES "
                     "('split:B1:1','BATCH','fp1','file:///dcim/IMG_1002.jpg',1,1,'READY',1,2)")
        conn.execute("INSERT INTO split_import_question VALUES "
                     "('split:B1:1',0,0,0.05,0.05,0.9,0.3,0,'PENDING',NULL)")
        conn.commit()
        conn.close()

    def test_export_is_deterministic_and_consumable_by_measure(self):
        """导出→同 sha 复跑→measure.load_app 可消费；页名取 batch_import_page.source_uri。"""
        db = self.tmp / "snapshot.db"
        self._make_db(db)
        self._seed(db)
        out1, out2 = self.tmp / "o1.json", self.tmp / "o2.json"
        code1, _ = self._export(db, out1)
        code2, _ = self._export(db, out2)
        self.assertEqual(0, code1)
        self.assertEqual(0, code2)
        self.assertEqual(out1.read_bytes(), out2.read_bytes(), "同 DB 复跑必须同字节（同 sha）")
        doc = json.loads(out1.read_text(encoding="utf-8"))
        names = [p["file"] for p in doc["pages"]]
        self.assertEqual(["IMG_1001.jpg", "IMG_1002.jpg"], names)
        page0 = doc["pages"][0]
        self.assertEqual(2, page0["questionCount"])
        self.assertEqual("batch_page_source_uri", page0["nameSource"])
        self.assertEqual(0.05, page0["questions"][0]["left"])
        app_pages = [(p["file"], p["questions"]) for p in doc["pages"]]
        loaded = M.load_app(out1)  # 度量脚本直接可读
        self.assertEqual(2, len(loaded))
        self.assertEqual({"file": "IMG_1001.jpg", "boxes": page0["questions"]}, loaded[0])
        self.assertTrue(app_pages)

    def _export(self, db: Path, out: Path) -> tuple[int, str]:
        import io
        from contextlib import redirect_stdout
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = X.main(["--db", str(db), "--out", str(out)])
        return code, buffer.getvalue()

    def test_db_is_read_only(self):
        """只读红线：导出前后 DB 快照逐字节不变。"""
        db = self.tmp / "snapshot.db"
        self._make_db(db)
        self._seed(db)
        before = hashlib.sha256(db.read_bytes()).hexdigest()
        code, _ = self._export(db, self.tmp / "o.json")
        self.assertEqual(0, code)
        self.assertEqual(before, hashlib.sha256(db.read_bytes()).hexdigest())

    def test_page_map_overrides_derived_name(self):
        """页名映射（4C 会话的 pages.txt/感知匹配产物）：显式映射优先于 URI 推导。"""
        db = self.tmp / "snapshot.db"
        self._make_db(db)
        self._seed(db)
        page_map = self.tmp / "map.json"
        page_map.write_text(json.dumps({"split:B1:0": {"0": "IMG20260810003928.jpg"}}),
                            encoding="utf-8")
        out = self.tmp / "o.json"
        import io
        from contextlib import redirect_stdout
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = X.main(["--db", str(db), "--out", str(out), "--page-map", str(page_map)])
        self.assertEqual(0, code)
        doc = json.loads(out.read_text(encoding="utf-8"))
        first = doc["pages"][0]
        self.assertEqual("IMG20260810003928.jpg", first["file"])
        self.assertEqual("page_map", first["nameSource"])

    def test_synthetic_name_without_batch_row(self):
        """无批页可连、无映射：合成名 + nameSource 标记（不猜页名）。"""
        db = self.tmp / "snapshot.db"
        self._make_db(db, with_batch=False)
        conn = sqlite3.connect(db)
        conn.execute("INSERT INTO split_import_job VALUES "
                     "('split-x','CAPTURE','fp','uri:x',1,1,'READY',1,2)")
        conn.execute("INSERT INTO split_import_question VALUES "
                     "('split-x',0,0,0.1,0.1,0.8,0.8,1,'PENDING',NULL)")
        conn.commit()
        conn.close()
        out = self.tmp / "o.json"
        code, _ = self._export(db, out)
        self.assertEqual(0, code)
        page = json.loads(out.read_text(encoding="utf-8"))["pages"][0]
        self.assertEqual("job:split-x#p0", page["file"])
        self.assertEqual("job_id_synthetic", page["nameSource"])

    def test_duplicate_page_name_rejected(self):
        """重复页名拒绝（R4 验收：同一页算两次 ⇒ 指标被无声加权）。"""
        db = self.tmp / "snapshot.db"
        self._make_db(db)
        self._seed(db)
        conn = sqlite3.connect(db)
        conn.execute("INSERT INTO batch_import_job VALUES ('B2','r2','f2','DONE',1,2)")
        conn.execute("INSERT INTO batch_import_page VALUES "
                     "('B2',0,'file:///dcim/IMG_1001.jpg','DONE',NULL,NULL,1,1,2,'NEXT_QUESTION','NEXT_QUESTION')")
        conn.execute("INSERT INTO split_import_job VALUES "
                     "('split:B2:0','BATCH','fp2','file:///dcim/IMG_1001.jpg',1,1,'READY',1,2)")
        conn.execute("INSERT INTO split_import_question VALUES "
                     "('split:B2:0',0,0,0.05,0.05,0.9,0.4,1,'PENDING',NULL)")
        conn.commit()
        conn.close()
        code, out_text = self._export(db, self.tmp / "o.json")
        self.assertEqual(2, code)
        self.assertIn("重复", out_text)

    def test_illegal_box_rejected(self):
        """非法框（left>=right）拒绝：不让下游裁出空图/反向图。"""
        db = self.tmp / "snapshot.db"
        self._make_db(db)
        self._seed(db)
        conn = sqlite3.connect(db)
        conn.execute("UPDATE split_import_question SET region_right=0.01 "
                     "WHERE job_id='split:B1:0' AND question_ordinal=1")
        conn.commit()
        conn.close()
        code, out_text = self._export(db, self.tmp / "o.json")
        self.assertEqual(2, code)
        self.assertIn("left", out_text)

    def test_missing_db_fails_honestly(self):
        """DB 不存在：exit 2，不编空导出。"""
        code, out_text = self._export(self.tmp / "nope.db", self.tmp / "o.json")
        self.assertEqual(2, code)
        self.assertIn("不存在", out_text)

    def test_missing_tables_fail_honestly(self):
        """空 sqlite（无 split 表）：exit 2，如实报缺表。"""
        db = self.tmp / "empty.db"
        sqlite3.connect(db).close()
        code, out_text = self._export(db, self.tmp / "o.json")
        self.assertEqual(2, code)
        self.assertIn("split_import_job", out_text)


# ---------------------------------------------------------------- 子进程冒烟（真实入口）

class EntrySmokeTest(SplitGateTestBase):
    def test_gate_entry_reports_exit_3_and_automation_line(self):
        """python tools/split_golden/check_split_gate.py（真入口）：真金标无应用导出 → exit 3。"""
        proc = subprocess.run(
            [sys.executable, str(SPLIT_TOOLS / "check_split_gate.py"),
             "--app", str(self.tmp / "nope.json")],
            capture_output=True, text=True, cwd=str(REPO), timeout=120)
        self.assertEqual(3, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("门后自动化不启用", proc.stdout)


if __name__ == "__main__":
    unittest.main()
