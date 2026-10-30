# -*- coding: utf-8 -*-
"""staging/release/旁车三方主包哈希对拍（tools/ci/check_pack_sync.py）的用例。

它消灭的失败：KB25-01 中 release 主包被未知写者改写、staging 仍是另一份，
而没有任何门把两侧钉在一起。用例覆盖三方一致（绿）、staging 漂移（红）、
旁车漂移（红）与缺文件（exit 2），确保"没读到"不会被当成"一致"。
"""

from __future__ import annotations

import json
import shutil
import tempfile
import unittest
from pathlib import Path

from ci import check_pack_sync as C


class CheckPackSyncTest(unittest.TestCase):
    def _fixture(self, release_bytes: bytes = b'{"pack":1}',
                 staging_bytes: bytes | None = None,
                 sidecar_sha: str | None = None) -> tuple[Path, Path, Path]:
        tmp = Path(tempfile.mkdtemp(prefix="pack-sync-"))
        self.addCleanup(shutil.rmtree, tmp, True)
        release = tmp / "release"
        staging = tmp / "staging"
        release.mkdir()
        staging.mkdir()
        (release / C.PACK_NAME).write_bytes(release_bytes)
        (staging / C.PACK_NAME).write_bytes(
            release_bytes if staging_bytes is None else staging_bytes)
        sidecar = tmp / "sidecar.json"
        recorded = sidecar_sha if sidecar_sha is not None else C.sha256_file(
            release / C.PACK_NAME)
        sidecar.write_text(json.dumps({"packSha256": recorded}), encoding="utf-8")
        return release, staging, sidecar

    def test_all_three_equal_passes(self):
        release, staging, sidecar = self._fixture()
        result = C.evaluate(release, staging, sidecar)
        self.assertTrue(result["ok"], result)
        self.assertEqual(0, C.main(["--release", str(release), "--staging", str(staging),
                                    "--sidecar", str(sidecar)]))

    def test_staging_drift_fails_nonzero(self):
        release, staging, sidecar = self._fixture(staging_bytes=b'{"pack":2}')
        result = C.evaluate(release, staging, sidecar)
        self.assertFalse(result["ok"])
        failed = {c["name"] for c in result["failed"]}
        self.assertIn("staging==release", failed)
        self.assertIn("sidecar==staging", failed)
        self.assertEqual(1, C.main(["--release", str(release), "--staging", str(staging),
                                    "--sidecar", str(sidecar)]))

    def test_sidecar_drift_fails_nonzero(self):
        release, staging, sidecar = self._fixture(sidecar_sha="0" * 64)
        result = C.evaluate(release, staging, sidecar)
        self.assertFalse(result["ok"])
        self.assertIn("sidecar==release",
                      {c["name"] for c in result["failed"]})
        self.assertEqual(1, C.main(["--release", str(release), "--staging", str(staging),
                                    "--sidecar", str(sidecar)]))

    def test_missing_file_is_exit_2_not_silent_pass(self):
        release, _staging, sidecar = self._fixture()
        missing = release.parent / "absent-staging"
        result = C.evaluate(release, missing, sidecar)
        self.assertFalse(result["ok"])
        self.assertEqual("files", result["failed"][0]["name"])
        self.assertEqual(2, C.main(["--release", str(release), "--staging", str(missing),
                                    "--sidecar", str(sidecar)]))


if __name__ == "__main__":
    unittest.main()
