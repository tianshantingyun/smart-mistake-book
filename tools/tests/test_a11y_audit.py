# -*- coding: utf-8 -*-
"""a11y_audit.py 的机制测试（批次 44 / D0 item 9、I 域）。

消灭的具体失败：无障碍只截一张图就宣称“通过”。本测试钉住：
- 可点击节点触摸目标 < 48dp → 违规；
- 可点击节点无 text/content-desc → 违规；
- 对比度没有颜色输入时必须标 UNVERIFIED，不能把结构检查通过冒充整体 PASS；
- 有颜色输入时按 WCAG 对比度计算，低于 4.5 判 FAIL。

运行：
    python -I -m pytest tools/tests/test_a11y_audit.py -q
"""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools" / "ci"))

import a11y_audit as A  # noqa: E402

SCRIPT = REPO / "tools" / "ci" / "a11y_audit.py"

GOOD_XML = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy rotation="0">
  <node class="android.widget.FrameLayout" clickable="false" bounds="[0,0][1000,1000]">
    <node class="android.widget.Button" clickable="true" text="继续" content-desc="" bounds="[10,10][200,200]" />
  </node>
</hierarchy>
"""

SMALL_XML = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy rotation="0">
  <node class="android.widget.Button" clickable="true" text="小" content-desc="" bounds="[0,0][40,40]" />
</hierarchy>
"""

UNLABELED_XML = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy rotation="0">
  <node class="android.widget.ImageButton" clickable="true" text="" content-desc="" bounds="[0,0][60,60]" />
</hierarchy>
"""


def _colors(bounds: str, fg: list[int], bg: list[int]) -> dict:
    return {"nodes": [{"bounds": bounds, "fg": fg, "bg": bg}]}


class A11yAuditTest(unittest.TestCase):
    def test_good_tree_with_colors_passes(self) -> None:
        nodes = A.parse_dump(GOOD_XML)
        result = A.audit(nodes, density_dpi=160, colors=_colors("[10,10][200,200]", [0, 0, 0], [255, 255, 255]))
        self.assertTrue(result["structural_ok"])
        self.assertEqual(result["contrast"]["status"], "PASS")
        self.assertTrue(result["ok"])
        self.assertEqual(result["violations"], [])

    def test_small_touch_target_fails(self) -> None:
        result = A.audit(A.parse_dump(SMALL_XML), density_dpi=160)
        self.assertFalse(result["structural_ok"])
        self.assertIn("TOUCH_TARGET_TOO_SMALL", {v["code"] for v in result["violations"]})
        self.assertFalse(result["ok"])

    def test_unlabeled_clickable_fails(self) -> None:
        result = A.audit(A.parse_dump(UNLABELED_XML), density_dpi=160)
        self.assertIn("MISSING_LABEL", {v["code"] for v in result["violations"]})

    def test_missing_contrast_data_is_unverified_not_pass(self) -> None:
        result = A.audit(A.parse_dump(GOOD_XML), density_dpi=160)
        self.assertTrue(result["structural_ok"])
        self.assertEqual(result["contrast"]["status"], "UNVERIFIED")
        self.assertFalse(result["ok"], "结构通过 + 对比度未测 不能整体 PASS")

    def test_low_contrast_fails(self) -> None:
        result = A.audit(A.parse_dump(GOOD_XML), density_dpi=160,
                         colors=_colors("[10,10][200,200]", [130, 130, 130], [255, 255, 255]))
        self.assertEqual(result["contrast"]["status"], "FAIL")
        self.assertIn("CONTRAST_TOO_LOW", {v["code"] for v in result["violations"]})

    def test_cli_exit_codes(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            xml = tmp / "dump.xml"
            xml.write_text(SMALL_XML, encoding="utf-8")
            out = tmp / "out.json"
            proc = subprocess.run(
                [sys.executable, "-I", str(SCRIPT), "audit", "--dump", str(xml),
                 "--density-dpi", "160", "--out", str(out)],
                capture_output=True, text=True, cwd=str(REPO),
            )
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertFalse(json.loads(out.read_text(encoding="utf-8"))["ok"])

    def test_contrast_ratio_math(self) -> None:
        self.assertAlmostEqual(A.contrast_ratio([0, 0, 0], [255, 255, 255]), 21.0, places=3)
        self.assertAlmostEqual(A.contrast_ratio([255, 255, 255], [255, 255, 255]), 1.0, places=3)

    def test_dtd_entity_dump_is_rejected(self) -> None:
        """消灭的具体失败：语义树是设备产物，DTD 实体扩展可能耗尽资源。"""
        bomb = (
            "<?xml version='1.0'?><!DOCTYPE lolz [<!ENTITY lol 'lol'>]>"
            "<hierarchy><node class='android.widget.Button' clickable='true' "
            "text='&lol;' bounds='[0,0][60,60]'/></hierarchy>"
        )
        with self.assertRaises(ValueError):
            A.parse_dump(bomb)

    def test_exact_48dp_passes_and_47dp_fails(self) -> None:
        """消灭的具体失败：把 48dp 判成 >48dp，或把 47dp 四舍五入成合规。"""
        exact = GOOD_XML.replace("[10,10][200,200]", "[0,0][48,48]")
        self.assertEqual(A.audit(A.parse_dump(exact), density_dpi=160)["violations"], [])
        under = GOOD_XML.replace("[10,10][200,200]", "[0,0][47,48]")
        codes = {v["code"] for v in A.audit(A.parse_dump(under), density_dpi=160)["violations"]}
        self.assertIn("TOUCH_TARGET_TOO_SMALL", codes)

    def test_contrast_threshold_is_inclusive(self) -> None:
        """消灭的具体失败：恰好达到阈值被判失败（边界偏移）。"""
        fg, bg = [130, 130, 130], [255, 255, 255]
        exact = A.contrast_ratio(fg, bg)
        result = A.audit(A.parse_dump(GOOD_XML), density_dpi=160,
                         colors=_colors("[10,10][200,200]", fg, bg), threshold=exact)
        self.assertEqual(result["contrast"]["status"], "PASS")
        result = A.audit(A.parse_dump(GOOD_XML), density_dpi=160,
                         colors=_colors("[10,10][200,200]", fg, bg), threshold=exact + 0.01)
        self.assertEqual(result["contrast"]["status"], "FAIL")

    def test_theme_source_pairs_resolve_named_constants(self) -> None:
        """消灭的具体失败：主题用 `val Ink = Color(...)` 时对比度检查被静默跳过。"""
        with tempfile.TemporaryDirectory() as td:
            source = Path(td) / "Theme.kt"
            source.write_text(
                "object C { val Ink = Color(0xFF151515) }\n"
                "val Paper = Color(0xFFF7F6F2)\n"
                "private val Scheme = lightColorScheme(\n"
                "  surface = Paper,\n  onSurface = Ink,\n)\n",
                encoding="utf-8")
            pairs = A.theme_pairs_from_source([source])
            self.assertEqual(len(pairs), 1, pairs)
            self.assertIn("onSurface/surface", pairs[0]["label"])
            self.assertEqual(pairs[0]["fg"], [0x15, 0x15, 0x15])
            self.assertEqual(pairs[0]["bg"], [0xF7, 0xF6, 0xF2])


if __name__ == "__main__":
    unittest.main()
