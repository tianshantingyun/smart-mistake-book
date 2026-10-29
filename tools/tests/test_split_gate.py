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
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

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


def write_json(path: Path, doc: dict) -> None:
    path.write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8", newline="")


def make_split_db(path: Path, with_batch: bool = True) -> None:
    """应用侧分题表的最小真实形状（列名与 SplitImportEntities.kt / BatchImportEntities.kt 一致）。"""
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


def seed_split_db(db: Path) -> None:
    """两页：IMG_1001（2 题）、IMG_1002（1 题）；job id 形如 split:<batch_job>:<page>。"""
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


def make_wal_snapshot_pair(tmp: Path) -> tuple[Path, Path]:
    """两份 WAL 快照：**主库字节相同、`-wal` 不同**（1 题 vs 2 题）。

    这就是 KD-81 受控实验（`docs/research/2026-10-29-snapshot-identity-wal-and-r8.md`
    §1.2 / §4.2）的可复用形态：schema 与 job 行先 `wal_checkpoint(TRUNCATE)` 落进主库，
    之后两次插入都只留在 `-wal` 里，每次插入后各拷一次 `db` + `db-wal`。

    它消灭的具体失败：没有这个夹具时，"身份是否覆盖 WAL"只能靠会话外手工造库判断，
    回归用例无从落地（同一主库 + 不同 WAL 同 sha 的缺陷会再次静默回来）。

    只拷 `db`+`wal`、**不拷 `-shm`**：`-shm` 是共享内存索引（只读打开时由 SQLite
    重建/改写），把它当输入既无意义，也会让"同一快照跑两遍"不再逐字节一致。
    写连接全程不关——关闭会触发末次 checkpoint 并删除 WAL，两份快照就退化成同一形态。
    """
    live = tmp / "live.db"
    conn = sqlite3.connect(live, isolation_level=None)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA wal_autocheckpoint=0")
    make_split_db(live)  # 短连接建表；本连接仍开着，故不会 checkpoint 掉 WAL
    conn.execute("INSERT INTO batch_import_job VALUES ('B1','r1','f1','DONE',1,2)")
    conn.execute("INSERT INTO batch_import_page VALUES "
                 "('B1',0,'file:///dcim/IMG_1001.jpg','DONE',NULL,NULL,1,1,2,"
                 "'NEXT_QUESTION','NEXT_QUESTION')")
    conn.execute("INSERT INTO split_import_job VALUES "
                 "('split:B1:0','BATCH','fp0','file:///dcim/IMG_1001.jpg',1,2,'READY',1,2)")
    # 结构必须先进主库：否则"只拷主库"会以缺表失败，T1 的前提（主库字节相同）也没了。
    conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")

    def snapshot(tag: str) -> Path:
        dst = tmp / f"snap{tag}.db"
        shutil.copyfile(live, dst)
        shutil.copyfile(str(live) + "-wal", str(dst) + "-wal")
        return dst

    conn.execute("INSERT INTO split_import_question VALUES "
                 "('split:B1:0',0,0,0.05,0.05,0.9,0.4,1,'PENDING',NULL)")
    snap_one = snapshot("one")
    conn.execute("INSERT INTO split_import_question VALUES "
                 "('split:B1:0',1,0,0.05,0.5,0.9,0.95,1,'CONFIRMED',NULL)")
    snap_two = snapshot("two")
    conn.close()
    return snap_one, snap_two


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
        make_split_db(path, with_batch)

    def _seed(self, db: Path) -> None:
        seed_split_db(db)

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


class SnapshotIdentityWalTest(SplitGateTestBase):
    """KD-81：产物身份必须覆盖 `-wal`，`-shm` 不得进身份，读事务必须钉住一个快照。

    它消灭的具体失败（R2 §2.4 推荐组合，缺一项的失败面）：
      ① 身份只吃主库字节 → 同主库 + 不同 WAL 报同一个"快照身份"（KD-81 原文症状）；
      ② `-shm` 进身份 → 导出自己改写它，同一快照跑两遍得到两个身份、两份不同字节的导出；
      ③ autocommit 下两条语句跨快照 → 一次导出内部混合两个状态，任何身份都描述不了它；
      ④ 导出期间输入漂移无人察觉 → 报告声称的快照与实际度量对象不是同一个。
    """

    def _export(self, db: Path, out: Path) -> tuple[int, str]:
        import io
        from contextlib import redirect_stdout
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = X.main(["--db", str(db), "--out", str(out)])
        return code, buffer.getvalue()

    def _doc(self, out: Path) -> dict:
        return json.loads(out.read_text(encoding="utf-8"))

    def test_same_main_db_different_wal_yields_different_identity(self):
        """受控实验：主库字节相同 + WAL 不同 ⇒ 身份必须不同（旧字段 db.sha256 仍相同）。"""
        snap_one, snap_two = make_wal_snapshot_pair(self.tmp)
        out_one, out_two = self.tmp / "one.json", self.tmp / "two.json"
        code_one, text_one = self._export(snap_one, out_one)
        code_two, text_two = self._export(snap_two, out_two)
        self.assertEqual(0, code_one, text_one)
        self.assertEqual(0, code_two, text_two)
        doc_one, doc_two = self._doc(out_one), self._doc(out_two)

        # 前提：正是 KD-81 的形态——两份快照的主库字节相同。
        self.assertEqual(snap_one.read_bytes(), snap_two.read_bytes())
        self.assertEqual(doc_one["source"]["db"]["sha256"],
                         doc_two["source"]["db"]["sha256"],
                         "旧字段 db.sha256 相同是缺陷形态，不是身份")

        snapshot_one = doc_one["source"]["snapshot"]
        snapshot_two = doc_two["source"]["snapshot"]
        self.assertEqual("inputs-v1", snapshot_one["identityKind"])
        self.assertTrue(snapshot_one["walPresent"])
        self.assertTrue(snapshot_two["walPresent"])
        self.assertNotEqual(snapshot_one["inputs"]["wal"]["sha256"],
                            snapshot_two["inputs"]["wal"]["sha256"])
        self.assertNotEqual(snapshot_one["identitySha256"],
                            snapshot_two["identitySha256"],
                            "同主库 + 不同 WAL 必须得到不同快照身份")
        self.assertNotEqual(doc_one["source"]["payload"]["sha256"],
                            doc_two["source"]["payload"]["sha256"],
                            "载荷（内容身份）也必须不同：1 框 vs 2 框")
        self.assertEqual([1, 2], [doc_one["pages"][0]["questionCount"],
                                  doc_two["pages"][0]["questionCount"]])

    def test_same_wal_snapshot_exported_twice_is_byte_identical(self):
        """同一 WAL 快照连导两次必须逐字节一致：身份/输出都不得吃 `-shm` 或 mtime。

        只读导出本身会创建/改写 `-shm`（R2 §1.3 P2b 实测），所以任何落进输出的
        `shm` 摘要/大小/mtime 都会让第二次导出与第一次不同字节——这条用例把
        "新字段仍是确定性的"钉住。
        """
        snap_one, _snap_two = make_wal_snapshot_pair(self.tmp)
        out_one, out_two = self.tmp / "a.json", self.tmp / "b.json"
        code_one, text_one = self._export(snap_one, out_one)
        code_two, text_two = self._export(snap_one, out_two)
        self.assertEqual(0, code_one, text_one)
        self.assertEqual(0, code_two, text_two)
        self.assertEqual(out_one.read_bytes(), out_two.read_bytes(),
                         "同一 WAL 快照复跑必须同字节（身份不得吃 -shm/mtime）")
        snapshot = self._doc(out_one)["source"]["snapshot"]
        self.assertTrue(snapshot["walPresent"])
        self.assertEqual({"db", "wal"}, set(snapshot["inputs"]),
                         "-shm 不得出现在身份输入清单里")

    def test_absent_wal_is_recorded_as_absent(self):
        """已 checkpoint 的库（无 `-wal`）：如实记 wal=null / walPresent=false，不编。"""
        db = self.tmp / "snapshot.db"
        make_split_db(db)
        seed_split_db(db)
        out = self.tmp / "o.json"
        code, text = self._export(db, out)
        self.assertEqual(0, code, text)
        snapshot = self._doc(out)["source"]["snapshot"]
        self.assertFalse(snapshot["walPresent"])
        self.assertIsNone(snapshot["inputs"]["wal"])
        self.assertEqual(hashlib.sha256(db.read_bytes()).hexdigest(),
                         snapshot["inputs"]["db"]["sha256"])

    def test_empty_wal_sidecar_from_own_read_is_not_drift(self):
        """WAL 模式但无 `-wal` 的快照：只读打开会**自建 0 字节 `-wal`**，不得判成漂移。

        实测（本会话探针）：只读 `mode=ro` 打开一个 WAL 模式的库后，目录里多出
        `<db>-wal` = 0 B、`<db>-shm` = 32768 B。若把"存在但 0 字节"的 `-wal` 当输入，
        第一次导出就会自己触发 `快照一致性`（exit 2），且同一快照的第二次导出换身份。
        0 字节 WAL 不含任何帧（`wal_checkpoint(TRUNCATE)` 后正是这个形态），按"无 WAL"归一。
        """
        snap_one, _snap_two = make_wal_snapshot_pair(self.tmp)
        main_only = self.tmp / "main-only.db"
        shutil.copyfile(snap_one, main_only)  # 只拷主库：丢掉真正的 -wal
        self.assertFalse(main_only.with_name(main_only.name + "-wal").exists())

        out_one, out_two = self.tmp / "a.json", self.tmp / "b.json"
        code_one, text_one = self._export(main_only, out_one)
        code_two, text_two = self._export(main_only, out_two)
        self.assertEqual(0, code_one, text_one)
        self.assertEqual(0, code_two, text_two)
        self.assertTrue(main_only.with_name(main_only.name + "-wal").is_file(),
                        "前提：只读打开确实自建了 0 字节 -wal（本用例要覆盖的就是它）")
        self.assertEqual(0, main_only.with_name(main_only.name + "-wal").stat().st_size)
        snapshot = self._doc(out_one)["source"]["snapshot"]
        self.assertFalse(snapshot["walPresent"], "0 字节 -wal 必须按无 WAL 归一")
        self.assertIsNone(snapshot["inputs"]["wal"])
        self.assertEqual(out_one.read_bytes(), out_two.read_bytes(),
                         "同一快照复跑必须同字节（自建的 0 字节 -wal 不得改变身份）")
        self.assertEqual(0, sum(page["questionCount"] for page in self._doc(out_one)["pages"]),
                         "只拷主库：已提交的题仍在 WAL 里，导出必须如实为 0 框（不是静默补回）")


    def test_export_rejects_input_change_during_export(self):
        """导出期间 db/-wal 变了：exit 2 + `快照一致性`，且 `--out` 不落盘（校验先于写盘）。"""
        snap_one, _snap_two = make_wal_snapshot_pair(self.tmp)
        out = self.tmp / "o.json"
        real_manifest = X._input_manifest
        calls = {"count": 0}

        def manifest_with_drift(db_path):
            calls["count"] += 1
            if calls["count"] == 2:  # 第 2 次 = 读连接关闭之后的"后比对"
                writer = sqlite3.connect(str(db_path))
                writer.execute(
                    "INSERT INTO split_import_question VALUES "
                    "('split:B1:0',1,0,0.05,0.5,0.9,0.95,1,'CONFIRMED',NULL)")
                writer.commit()
                writer.close()
            return real_manifest(db_path)

        with mock.patch.object(X, "_input_manifest", manifest_with_drift, create=True):
            code, text = self._export(snap_one, out)
        self.assertEqual(2, code, text)
        self.assertIn("快照一致性", text)
        self.assertFalse(out.exists(), "后比对必须先于写盘：坏导出不得已落盘")

    def test_reads_are_pinned_to_one_snapshot(self):
        """读事务钉住快照：导出期间另一个连接的已提交写入不得混进本次导出。

        插入点选在页名解析的**首次调用**（主查询之后、仍在读事务内）：没有显式
        `BEGIN` 时该 INSERT 会被后续 SELECT 看见（混合导出），有读事务时看不见。
        后比对被打成常量以关掉"检测"，只测"钉住"——两件事分开证明。
        """
        snap_one, _snap_two = make_wal_snapshot_pair(self.tmp)
        out = self.tmp / "o.json"
        real_manifest = X._input_manifest
        cached: dict = {}

        def frozen_manifest(db_path):
            if not cached:
                cached.update(real_manifest(db_path))
            return dict(cached)

        real_resolve = X._resolve_page_name
        fired = {"count": 0}

        def resolve_with_write(conn, job_id, page_index, page_map):
            if fired["count"] == 0:
                fired["count"] += 1
                writer = sqlite3.connect(str(snap_one))
                writer.execute(
                    "UPDATE batch_import_page SET source_uri='file:///dcim/IMG_9999.jpg'"
                    " WHERE job_id='B1' AND page_index=0")
                writer.commit()
                writer.close()
            return real_resolve(conn, job_id, page_index, page_map)

        with mock.patch.object(X, "_input_manifest", frozen_manifest, create=True), \
                mock.patch.object(X, "_resolve_page_name", resolve_with_write):
            code, text = self._export(snap_one, out)
        self.assertEqual(0, code, text)
        pages = self._doc(out)["pages"]
        self.assertEqual(["IMG_1001.jpg"], [page["file"] for page in pages],
                         "读事务必须钉住快照：导出期间提交的改名不得混进来")
        self.assertEqual("batch_page_source_uri", pages[0]["nameSource"])

    def test_export_rejects_wal_change_inside_transaction(self):
        """T5b：读事务**内**发生的已提交写入，后比对仍必须 exit 2（且校验先于写盘）。

        与 `test_reads_are_pinned_to_one_snapshot` 的分工：那条把后比对钉成常量，只证明
        "读事务钉住快照"；这条**不冻结**清单，只证明"事务内漂移仍被检出"。两件事必须
        同时成立才是完整门——只钉住不检测 = 静默导出旧状态；只检测不钉住 = 导出内部
        混合两个状态。
        """
        snap_one, _snap_two = make_wal_snapshot_pair(self.tmp)
        out = self.tmp / "o.json"
        real_resolve = X._resolve_page_name
        fired = {"count": 0}

        def resolve_with_write(conn, job_id, page_index, page_map):
            if fired["count"] == 0:
                fired["count"] += 1
                writer = sqlite3.connect(str(snap_one))
                writer.execute(
                    "UPDATE batch_import_page SET source_uri='file:///dcim/IMG_9999.jpg'"
                    " WHERE job_id='B1' AND page_index=0")
                writer.commit()
                writer.close()
            return real_resolve(conn, job_id, page_index, page_map)

        with mock.patch.object(X, "_resolve_page_name", resolve_with_write):
            code, text = self._export(snap_one, out)
        self.assertEqual(2, code, text)
        self.assertIn("快照一致性", text)
        self.assertFalse(out.exists(), "后比对必须先于写盘：坏导出不得已落盘")



# ---------------------------------------------------------------- 生产者溯源 → 逐页裁定

class ProvenanceFlowTest(SplitGateTestBase):
    """导出器的 nameSource/jobId 必须活到 measure --json 逐页行与门证据里。

    它消灭的具体失败：逐页裁定只写 file/框数，读者无法区分"库里真有这个文件名"与
    "页名是映射/合成来的"，也无法区分"导出带溯源"与"4C 历史导出无溯源"——两者
    只能靠打开 JSON 或翻会话记录判断，证据文件自我不完整。
    """

    def _golden_doc(self) -> dict:
        return {"pages": [
            {"file": "IMG_1001.jpg", "questionCount": 2,
             "boxes": [_box(0.05, 0.05, 0.9, 0.4), _box(0.05, 0.5, 0.9, 0.95)]},
            {"file": "IMG_1002.jpg", "questionCount": 1,
             "boxes": [_box(0.05, 0.05, 0.9, 0.3)]},
        ]}

    def _golden(self) -> Path:
        golden = self.tmp / "g.json"
        write_json(golden, self._golden_doc())
        return golden

    def _export(self) -> tuple[Path, Path]:
        db = self.tmp / "snapshot.db"
        make_split_db(db)
        seed_split_db(db)
        out = self.tmp / "app.json"
        import io
        from contextlib import redirect_stdout
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = X.main(["--db", str(db), "--out", str(out)])
        self.assertEqual(0, code, buffer.getvalue())
        return db, out

    def _measure_json(self, app: Path) -> tuple[int, dict]:
        import io
        from contextlib import redirect_stdout
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = M.main(["--golden", str(self._golden()), "--app", str(app), "--json"])
        return code, json.loads(buffer.getvalue())

    def test_export_is_consumed_with_provenance_intact(self):
        """导出格式被度量脚本直接消费，且每页来源随行透传（不是只写 file/框数）。"""
        db, app = self._export()
        code, report = self._measure_json(app)
        self.assertEqual(0, code)
        rows = {row["file"]: row for row in report["pages"]}
        self.assertEqual({"IMG_1001.jpg", "IMG_1002.jpg"}, set(rows))
        first = rows["IMG_1001.jpg"]
        self.assertEqual(2, first["appBoxes"])
        self.assertEqual("batch_page_source_uri", first["appNameSource"])
        self.assertEqual("split:B1:0", first["appJobId"])
        self.assertEqual(0, first["appPageIndex"])
        self.assertTrue(first["appProvenance"])
        source = report["app"]["source"]
        self.assertTrue(source["present"])
        self.assertEqual(hashlib.sha256(db.read_bytes()).hexdigest(), source["dbSha256"])
        self.assertEqual({"batch_page_source_uri": 2}, source["nameSourceCounts"])
        self.assertFalse(report["automation"]["enabled"])

    def test_export_without_source_block_is_marked_unverified(self):
        """无溯源块的导出（4C 形态）：逐页来源为 null、文件级 source.present=false，不猜。"""
        app = self.tmp / "plain.json"
        write_json(app, {"pages": [
            {"file": "IMG_1001.jpg", "questions": [
                _box(0.05, 0.05, 0.9, 0.4), _box(0.05, 0.5, 0.9, 0.95)]},
            {"file": "IMG_1002.jpg", "questions": [_box(0.05, 0.05, 0.9, 0.3)]},
        ]})
        code, report = self._measure_json(app)
        self.assertEqual(0, code)
        for row in report["pages"]:
            self.assertIsNone(row["appNameSource"])
            self.assertFalse(row["appProvenance"])
        self.assertFalse(report["app"]["source"]["present"])
        self.assertIsNone(report["app"]["source"]["dbSha256"])
        self.assertFalse(report["automation"]["enabled"])

    def test_exporter_output_is_consumed_by_gate(self):
        """门脚本直接吃导出器输出：页名/框数/来源齐全，门照常按数值判（不为溯源改口径）。"""
        _db, app = self._export()
        pages = [_page("IMG_1001.jpg",
                       [_box(0.05, 0.05, 0.9, 0.4), _box(0.05, 0.5, 0.9, 0.95)]),
                 _page("IMG_1002.jpg", [_box(0.05, 0.05, 0.9, 0.3)])]
        # C6 要求金标 ≥7 页；其余 5 页应用缺页（按 0 框计入，不伪造输出）。
        pages += [_page(f"IMG_{index:04d}.jpg", [_box(0.05, 0.05, 0.9, 0.3)])
                  for index in range(1003, 1008)]
        golden = self.frozen(pages)
        import io
        from contextlib import redirect_stdout
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = G.main(["--golden", str(golden),
                           "--sidecar", str(golden.with_name(golden.name + ".sha256")),
                           "--app", str(app)])
        # 单题金标页不允许 1 块（v2 口径：单题页被拆即不一致）——本夹具 1002 就是这样，
        # 所以门如实 FAIL；这里验证的是格式消费与溯源透传，不是把门拉绿。
        self.assertEqual(1, code, buffer.getvalue())
        self.assertFalse(G.LAST_REPORT["gate"]["passed"])
        self.assertEqual("FAIL", G.LAST_REPORT["gate"]["state"])
        self.assertEqual(6, G.LAST_REPORT["metrics"]["countExact"]["successes"])
        rows = {row["file"]: row for row in G.LAST_REPORT["perPage"]}
        self.assertEqual(7, len(rows))
        for name in ("IMG_1001.jpg", "IMG_1002.jpg"):
            self.assertTrue(rows[name]["appProvenance"])
            self.assertEqual("batch_page_source_uri", rows[name]["appNameSource"])
        for name in ("IMG_1003.jpg", "IMG_1007.jpg"):
            self.assertFalse(rows[name]["appProvenance"])
            self.assertTrue(rows[name]["appPageMissing"])
        self.assertTrue(G.LAST_REPORT["appSource"]["present"])

    def test_gate_evidence_per_page_shows_source_or_unverified(self):
        """证据 §5 逐页表必须写来源：有溯源写 nameSource+job，无溯源写 UNVERIFIED。"""
        pages = [_page("IMG_1001.jpg", [_box(0.05, 0.05, 0.9, 0.4)])]
        pages += [_page(f"IMG_{index:04d}.jpg", [_box(0.05, 0.05, 0.9, 0.3)])
                  for index in range(1002, 1008)]
        golden = self.frozen(pages)
        sidecar = str(golden.with_name(golden.name + ".sha256"))
        import io
        from contextlib import redirect_stdout

        provenance_app = self.tmp / "prov.json"
        write_json(provenance_app, {
            "source": {"db": {"file": "snap.sqlite", "sha256": "ab" * 32, "bytes": 1},
                       "nameSourceCounts": {"page_map": 1}},
            "pages": [{"file": "IMG_1001.jpg",
                       "questions": [_box(0.05, 0.05, 0.9, 0.4)],
                       "nameSource": "page_map", "jobId": "split:B1:0", "pageIndex": 0}],
        })
        evidence = self.tmp / "evidence-prov.md"
        with redirect_stdout(io.StringIO()):
            code = G.main(["--golden", str(golden), "--sidecar", sidecar,
                           "--app", str(provenance_app), "--evidence-out", str(evidence)])
        self.assertEqual(1, code)  # 单题页被拆 1 块 = 不一致 → 门 FAIL；证据照样落盘
        text = evidence.read_text(encoding="utf-8")
        self.assertIn("来源", text)
        self.assertIn("page_map", text)
        self.assertIn("split:B1:0", text)

        plain_app = self.tmp / "plain.json"
        write_json(plain_app, {"pages": [{"file": "IMG_1001.jpg",
                                          "questions": [_box(0.05, 0.05, 0.9, 0.4)]}]})
        evidence2 = self.tmp / "evidence-plain.md"
        with redirect_stdout(io.StringIO()):
            code = G.main(["--golden", str(golden), "--sidecar", sidecar,
                           "--app", str(plain_app), "--evidence-out", str(evidence2)])
        self.assertEqual(1, code)
        text2 = evidence2.read_text(encoding="utf-8")
        self.assertIn("UNVERIFIED（导出无溯源块）", text2)
        self.assertNotIn("page_map", text2)


class HistoricalAppExportTest(SplitGateTestBase):
    """4C 历史设备导出：门值维持 FAIL（4/7）且自动化恒关；无溯源块 → 来源 UNVERIFIED。"""

    def test_gate_value_and_automation_ruling_unchanged(self):
        app = REPO / "build" / "split-eval" / "app-split.json"
        if not app.is_file():
            self.skipTest("历史设备导出不在树（build/ 被忽略；只在本机有快照时执行）")
        import io
        from contextlib import redirect_stdout
        with redirect_stdout(io.StringIO()):
            code = G.main(["--app", str(app)])
        report = G.LAST_REPORT
        self.assertEqual(1, code)
        self.assertEqual("FAIL", report["gate"]["state"])
        self.assertEqual(4, report["metrics"]["countExact"]["successes"])
        self.assertEqual(7, report["metrics"]["countExact"]["denominator"])
        self.assertEqual(0, report["metrics"]["fragment"]["successes"])
        self.assertEqual(16, report["metrics"]["fragment"]["denominator"])
        self.assertFalse(report["automation"]["enabled"])
        self.assertEqual(7, len(report["perPage"]))
        self.assertTrue(all(not row["appProvenance"] for row in report["perPage"]),
                        "4C 导出没有 nameSource：逐页来源必须如实标 UNVERIFIED，不得编造")


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
