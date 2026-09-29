# -*- coding: utf-8 -*-
"""staging 冻结对拍（staging_freeze）的用例：增/删/改三种并发写入都必须报出来。

① 快照后不动 → compare 干净（exit 0）；
② 改一个文件内容 → changed 报出（exit 1）；
③ 新增 / 删除文件 → added / removed 报出；
④ 没有指纹文件 → exit 2（不假装干净）。
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

from kb_build import pack_io, staging_freeze as S  # noqa: E402


class StagingFreezeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-freeze-", dir=pack_io.REPO / "build"))
        (self.tmp / "moe-2025-four-subjects-v1.json").write_text('{"a": 1}\n', encoding="utf-8")
        (self.tmp / "moe-2025-teaching-support-v2-01.json").write_text('{"m": []}\n',
                                                                       encoding="utf-8")
        pack_io.use_directory(self.tmp)

    def tearDown(self):
        pack_io.reset_directory()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_snapshot_then_clean_compare(self):
        self.assertEqual(0, S.main(["--snapshot"]))
        self.assertEqual(0, S.main(["--compare"]))

    def test_changed_file_reported(self):
        self.assertEqual(0, S.main(["--snapshot"]))
        (self.tmp / "moe-2025-four-subjects-v1.json").write_text('{"a": 2}\n', encoding="utf-8")
        self.assertEqual(1, S.main(["--compare"]))

    def test_added_and_removed_reported(self):
        self.assertEqual(0, S.main(["--snapshot"]))
        (self.tmp / "moe-2025-teaching-support-v2-02.json").write_text('{"m": []}\n', encoding="utf-8")
        (self.tmp / "moe-2025-teaching-support-v2-01.json").unlink()
        old = json.loads((self.tmp / S.FREEZE_NAME).read_text(encoding="utf-8"))
        diff = S.compare(old, S.scan(self.tmp))
        self.assertEqual(["moe-2025-teaching-support-v2-02.json"], diff["added"])
        self.assertEqual(["moe-2025-teaching-support-v2-01.json"], diff["removed"])

    def test_missing_snapshot_is_not_clean(self):
        self.assertEqual(2, S.main(["--compare"]))


if __name__ == "__main__":
    unittest.main()
