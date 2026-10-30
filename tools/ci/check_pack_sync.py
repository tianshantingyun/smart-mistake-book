#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""staging / release / dense 旁车三方主包哈希对拍（KB25-01 收口）。

## 它消灭的具体失败

2026-10-29 批次25 的 promote 报告落盘主包 `1db011e1…`，之后成品目录里的主包被一个
**未登记的写入者**改成 `e5dc3303…`（少一条 `AABB×aabb` 别名），而 staging 仍是
`1db011e1…`。内容门查的是包自身契约，dense 资产门只比"成品包 vs 旁车"，没有任何门
把 staging 与 release 钉在一起——于是漂移能静默存在到打包之后。

本工具只做一件事，任一项不等即非零退出：

    release 主包 sha256 == staging 主包 sha256 == dense 旁车 packSha256

缺失任何一侧也判失败（exit 2），不把"没读到"当作"一致"。

## 与 promote 断言的分工

`kb_build.promote` 的断言只在写盘那一刻成立；打包之后、CI 之前、或任何别的会话写
成品时，需要一把**独立**的尺子。promote 内联断言 + 本工具外部复核，才是"永久不变量"
的两端。它不写任何文件，纯只读。

用法：
    python tools/ci/check_pack_sync.py
    python tools/ci/check_pack_sync.py --json
    python tools/ci/check_pack_sync.py --release DIR --staging DIR --sidecar FILE
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
PACK_NAME = "moe-2025-four-subjects-v1.json"
RELEASE_DIR = REPO / "core" / "data" / "src" / "main" / "resources" / "knowledge"
STAGING_DIR = REPO / "build" / "kb-staging"
SIDECAR_RELATIVE = ("dense", "bge-small-zh-int8.vec.json")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def evaluate(release: Path | None = None, staging: Path | None = None,
             sidecar: Path | None = None) -> dict:
    """返回 {ok, release, staging, sidecar, checks:[...], failed:[...]}。

    路径参数只为测试与现场排查留；生产调用用默认值。
    """
    release_dir = Path(release) if release else RELEASE_DIR
    staging_dir = Path(staging) if staging else STAGING_DIR
    sidecar_file = Path(sidecar) if sidecar else release_dir.joinpath(*SIDECAR_RELATIVE)
    release_pack = release_dir / PACK_NAME
    staging_pack = staging_dir / PACK_NAME

    checks: list[dict] = []

    def record(name: str, ok: bool, detail: str) -> None:
        checks.append({"name": name, "ok": bool(ok), "detail": detail})

    missing = [str(p) for p in (release_pack, staging_pack, sidecar_file)
               if not p.is_file()]
    if missing:
        record("files", False, "缺文件：" + "、".join(missing))
        return {"ok": False, "release": str(release_pack), "staging": str(staging_pack),
                "sidecar": str(sidecar_file), "checks": checks,
                "failed": [c for c in checks if not c["ok"]]}

    release_sha = sha256_file(release_pack)
    staging_sha = sha256_file(staging_pack)
    try:
        recorded = (json.loads(sidecar_file.read_text(encoding="utf-8"))
                    .get("packSha256") or "")
    except (OSError, ValueError) as exc:
        record("files", False, f"旁车无法解析：{exc}")
        return {"ok": False, "release": str(release_pack), "staging": str(staging_pack),
                "sidecar": str(sidecar_file), "checks": checks,
                "failed": [c for c in checks if not c["ok"]]}

    record("release", True, f"{release_sha[:16]}…（{release_pack}）")
    record("staging", True, f"{staging_sha[:16]}…（{staging_pack}）")
    record("sidecar", bool(recorded),
           f"packSha256={(recorded or '(缺)')[:16]}…（{sidecar_file.name}）")
    record("staging==release", staging_sha == release_sha,
           f"staging {staging_sha[:16]}… vs release {release_sha[:16]}…")
    record("sidecar==release", recorded == release_sha,
           f"旁车 {(recorded or '(缺)')[:16]}… vs release {release_sha[:16]}…")
    record("sidecar==staging", recorded == staging_sha,
           f"旁车 {(recorded or '(缺)')[:16]}… vs staging {staging_sha[:16]}…")

    failed = [c for c in checks if not c["ok"]]
    return {"ok": not failed, "release": str(release_pack), "staging": str(staging_pack),
            "sidecar": str(sidecar_file), "releaseSha256": release_sha,
            "stagingSha256": staging_sha, "sidecarPackSha256": recorded,
            "checks": checks, "failed": failed}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="staging/release/旁车主包 sha 对拍")
    parser.add_argument("--release", type=Path, default=RELEASE_DIR,
                        help="成品目录（默认 core/data/.../knowledge）")
    parser.add_argument("--staging", type=Path, default=STAGING_DIR,
                        help="staging 目录（默认 build/kb-staging）")
    parser.add_argument("--sidecar", type=Path, default=None,
                        help="dense 旁车 .vec.json（默认 release/dense/...）")
    parser.add_argument("--json", action="store_true", help="JSON 输出")
    args = parser.parse_args(argv)

    result = evaluate(args.release, args.staging, args.sidecar)
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=1))
    else:
        for check in result["checks"]:
            mark = "OK  " if check["ok"] else "FAIL"
            print(f"  {mark} {check['name']}：{check['detail']}")
        if result["ok"]:
            print(f"PASS 三方一致：release == staging == 旁车 "
                  f"({result['releaseSha256'][:16]}…)")
        else:
            print("FAIL 三方不一致（release / staging / 旁车 packSha256）")

    if any(c["name"] == "files" for c in result["failed"]):
        return 2  # 缺文件/不可解析：没有"一致"可言
    return 0 if result["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
