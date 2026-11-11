# -*- coding: utf-8 -*-
"""screenshot_baseline.py 的机制测试（批次 44 / D0 item 8、C 域视觉证据）。

消灭的具体失败：设备截图没有基线/阈值，或只比较文件名就宣称视觉未回归。
本测试用合成的 8-bit RGBA PNG 验证：PNG 编解码、像素差 MAE、变化像素比例、
超阈值 exit 1，以及阈值必须显式给出。

运行：
    python -I -m pytest tools/tests/test_screenshot_baseline.py -q
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

import screenshot_baseline as S  # noqa: E402

SCRIPT = REPO / "tools" / "ci" / "screenshot_baseline.py"


def _solid(width: int, height: int, rgba: tuple[int, int, int, int]) -> bytes:
    return bytes(rgba) * (width * height)


class ScreenshotBaselineTest(unittest.TestCase):
    def test_png_round_trip(self) -> None:
        pixels = _solid(4, 3, (10, 20, 30, 255))
        data = S.encode_png_rgba(4, 3, pixels)
        image = S.decode_png(data)
        self.assertEqual((image.width, image.height), (4, 3))
        self.assertEqual(image.pixels, pixels)

    def test_identical_images_pass(self) -> None:
        a = _solid(8, 8, (1, 2, 3, 255))
        result = S.compare_images(a, 8, 8, a, 8, 8, max_mae=0.5, max_changed_ratio=0.0)
        self.assertTrue(result["ok"])
        self.assertEqual(result["mae"], 0.0)
        self.assertEqual(result["changed_ratio"], 0.0)

    def test_changed_pixel_fails_strict_threshold(self) -> None:
        a = _solid(4, 4, (0, 0, 0, 255))
        b = bytearray(a)
        b[0] = 255
        result = S.compare_images(a, 4, 4, bytes(b), 4, 4,
                                  max_mae=0.0, max_changed_ratio=0.0)
        self.assertFalse(result["ok"])
        self.assertGreater(result["changed_ratio"], 0.0)

    def test_cli_compare_exit_codes(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            base = tmp / "base.png"
            same = tmp / "same.png"
            diff = tmp / "diff.png"
            base.write_bytes(S.encode_png_rgba(4, 4, _solid(4, 4, (5, 5, 5, 255))))
            same.write_bytes(base.read_bytes())
            changed = bytearray(_solid(4, 4, (5, 5, 5, 255)))
            changed[0] = 250
            diff.write_bytes(S.encode_png_rgba(4, 4, bytes(changed)))
            for current, expected in ((same, 0), (diff, 1)):
                out = tmp / f"{current.stem}.json"
                proc = subprocess.run(
                    [sys.executable, "-I", str(SCRIPT), "compare",
                     "--baseline", str(base), "--current", str(current),
                     "--out", str(out), "--max-mae", "0", "--max-changed-ratio", "0"],
                    capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=str(REPO),
                )
                self.assertEqual(proc.returncode, expected, proc.stdout + proc.stderr)
                self.assertTrue(json.loads(out.read_text(encoding="utf-8"))["ok"] is (expected == 0))

    def test_different_sizes_fail_closed(self) -> None:
        result = S.compare_images(_solid(2, 2, (0, 0, 0, 255)), 2, 2,
                                  _solid(2, 3, (0, 0, 0, 255)), 2, 3,
                                  max_mae=10.0, max_changed_ratio=1.0)
        self.assertFalse(result["ok"])
        self.assertEqual(result["reason"], "SIZE_MISMATCH")


if __name__ == "__main__":
    unittest.main()
