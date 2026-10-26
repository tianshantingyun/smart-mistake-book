#!/usr/bin/env python3
"""Pin check for the quickjs-kt-android supply chain (批次 21 新增，批次 22 T3 扩展).

The catalog pins the *direct* coordinate and documents the AAR/POM sha256, but a
comment is not a build gate. This script turns the facts the build already
resolves into a checkable gate with **two separate invariants** (批次 22 T3 按 R2
路径 A 拆分；批次 21 的旧实现把两者混成一条，会在约束回 2.3.21 后误红):

1. **上游事实**: the AAR's POM declares ``kotlin-stdlib 2.4.10`` and the catalog
   records that requirement in ``quickjs-kotlin-stdlib``. Drift here means the
   AAR changed upstream, so the bytecode scan in
   ``docs/research/2026-10-26-dependency-governance.md`` §3.1 must be re-run.
2. **本项目决定**: the resolved runtime graph must contain the *compile* stdlib
   version (``kotlin``, 2.3.21), because the root build script constrains
   ``org.jetbrains.kotlin:kotlin-stdlib`` back to that version (R2 路径 A).

It also still pins the quickjs bytes (AAR/POM/.module sha256) and the resolved
quickjs coordinate version, so an AAR bump cannot slip through unnoticed.

Why this exists: quickjs-kt-android 1.0.15 pulls ``kotlin-stdlib 2.4.10`` while
the project compiles with 2.3.21. Gradle would silently resolve the higher
runtime version; without a gate, the next AAR bump (or a resolution change) can
move it again with no diff anywhere in this repository.

Run after a Gradle build that produced lint/dependency artifacts:
    python3 tools/ci/check_dependency_pins.py

Use ``--expect-kotlin-runtime`` as a negative-control knob (it should fail when
given a version other than the one the graph really resolved):

    python3 tools/ci/check_dependency_pins.py --expect-kotlin-runtime 2.4.10
"""

from __future__ import annotations

import argparse
import hashlib
import re
import sys
import xml.etree.ElementTree as ET
from collections.abc import Iterable, Sequence
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "gradle" / "libs.versions.toml"
CACHE_ROOTS = (ROOT / ".gradle" / "caches", Path.home() / ".gradle" / "caches")
QUICKJS_GROUP = "io.github.dokar3"
AAR_SHA256 = "406c3efb80d5fa3495df4a7f2663be8cc30c974fe5c2368effa929de77cf0ed8"
POM_SHA256 = "cc3ef4b3e8674771ef29d059c28935dd009e6dac7fb90765e497bd8414fe2bbc"
MODULE_SHA256 = "4d1534e780ab999aaa757ffe4017ca3a533ed3df43438c4622fd76db1f69e465"

# Python 的 ElementTree 本身不展开外部实体，但显式拒绝 DOCTYPE/ENTITY 声明可以让
# "被污染的构建产物" 在解析前就失败，而不是依赖解析器的默认行为。
FORBIDDEN_XML_MARKERS = ("<!DOCTYPE", "<!ENTITY")


def catalog_version(name: str, catalog: Path = CATALOG) -> str:
    text = catalog.read_text(encoding="utf-8")
    match = re.search(rf'^{re.escape(name)}\s*=\s*"([^"]+)"', text, re.MULTILINE)
    if not match:
        raise SystemExit(f"catalog entry not found: {name}")
    return match.group(1)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def checksum_errors(artifacts: Iterable[tuple[Path, str, str]]) -> list[str]:
    """Compare each ``(path, expected_sha256, label)`` triple."""
    errors: list[str] = []
    for artifact, expected, label in artifacts:
        actual = sha256(artifact)
        if actual != expected:
            errors.append(f"{label} sha256 mismatch: {artifact} -> {actual}")
    return errors


def find_quickjs_artifacts(version: str, suffix: str) -> list[Path]:
    found: list[Path] = []
    for cache in CACHE_ROOTS:
        coordinate = cache / "modules-2" / "files-2.1" / QUICKJS_GROUP
        if not coordinate.is_dir():
            continue
        for candidate in coordinate.rglob(f"*{version}*{suffix}"):
            if candidate.is_file():
                found.append(candidate)
        # Some caches rename the AAR to quickjs-release.aar; accept any AAR under
        # the quickjs-kt-android/<version>/ directory.
        version_dir = coordinate / "quickjs-kt-android" / version
        if version_dir.is_dir():
            for candidate in version_dir.rglob(f"*{suffix}"):
                if candidate.is_file() and candidate not in found:
                    found.append(candidate)
    return sorted(set(found))


def pom_kotlin_stdlib_version(pom_text: str) -> str | None:
    match = re.search(
        r"<artifactId>kotlin-stdlib</artifactId>\s*<version>([^<]+)</version>",
        pom_text,
    )
    return match.group(1) if match else None


def latest_dependency_artifact() -> Path:
    candidates = sorted(
        (ROOT / "app" / "build" / "intermediates").rglob("*artifact-dependencies.xml"),
        key=lambda path: path.stat().st_mtime,
        reverse=True,
    )
    if not candidates:
        raise SystemExit(
            "no *artifact-dependencies.xml under app/build/intermediates; "
            "run :app:assembleLocalFirstRelease (or lintVital) first",
        )
    # Several lint tasks run per build; pick the newest artifact that actually
    # contains the quickjs AAR instead of assuming the last one is the app graph.
    for path in candidates:
        text = path.read_text(encoding="utf-8", errors="replace")
        if f"{QUICKJS_GROUP}:quickjs-kt-android" in text:
            return path
    raise SystemExit(
        "no artifact-dependencies.xml contains quickjs-kt-android; "
        "run :app:assembleLocalFirstRelease (or lintVital) first",
    )


def parse_resolved_versions(xml_text: str) -> tuple[set[str], set[str]]:
    upper = xml_text.upper()
    if any(marker in upper for marker in FORBIDDEN_XML_MARKERS):
        raise SystemExit(
            "artifact-dependencies.xml contains a DOCTYPE/ENTITY declaration; refusing to parse",
        )
    root = ET.fromstring(xml_text)
    quickjs: set[str] = set()
    stdlib: set[str] = set()
    for element in root.iter("package"):
        roots = element.attrib.get("roots", "")
        for token in roots.split(","):
            quickjs_match = re.search(
                rf"{re.escape(QUICKJS_GROUP)}:quickjs-kt-android:([^@]+)@aar",
                token,
            )
            if quickjs_match:
                quickjs.add(quickjs_match.group(1))
            stdlib_match = re.search(r"org\.jetbrains\.kotlin:kotlin-stdlib:([^@]+)@jar", token)
            if stdlib_match:
                stdlib.add(stdlib_match.group(1))
    return quickjs, stdlib


def evaluate_pins(
    *,
    quickjs_version: str,
    upstream_stdlib: str,
    expected_runtime_stdlib: str,
    compile_stdlib: str,
    pom_stdlib: str | None,
    resolved_quickjs: set[str],
    resolved_stdlib: set[str],
    artifact_errors: Sequence[str] = (),
) -> list[str]:
    """Pure decision table; every branch is covered by
    ``tools/tests/test_check_dependency_pins.py`` (positive + negative)."""
    errors = list(artifact_errors)

    if pom_stdlib is None:
        errors.append("POM does not declare a kotlin-stdlib dependency")
    elif pom_stdlib != upstream_stdlib:
        errors.append(
            f"POM requires kotlin-stdlib {pom_stdlib}, catalog records upstream "
            f"requirement {upstream_stdlib}",
        )

    if quickjs_version not in resolved_quickjs:
        errors.append(
            f"resolved quickjs version(s) {sorted(resolved_quickjs)} do not include "
            f"{quickjs_version}",
        )

    if resolved_stdlib != {expected_runtime_stdlib}:
        errors.append(
            f"resolved kotlin-stdlib {sorted(resolved_stdlib)} != expected runtime "
            f"{expected_runtime_stdlib} (compile catalog says {compile_stdlib}; "
            f"upstream AAR requires {upstream_stdlib})",
        )

    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--expect-kotlin-runtime",
        help=(
            "override the expected resolved kotlin-stdlib version (negative-control "
            "knob; default: catalog `kotlin`)"
        ),
    )
    args = parser.parse_args()

    quickjs_version = catalog_version("quickjs-kt-android")
    upstream_stdlib = catalog_version("quickjs-kotlin-stdlib")
    compile_stdlib = catalog_version("kotlin")
    expected_runtime_stdlib = args.expect_kotlin_runtime or compile_stdlib

    artifact_errors: list[str] = []

    aars = find_quickjs_artifacts(quickjs_version, ".aar")
    poms = find_quickjs_artifacts(quickjs_version, ".pom")
    modules = find_quickjs_artifacts(quickjs_version, ".module")
    if not aars:
        artifact_errors.append(f"quickjs AAR {quickjs_version} not found in the Gradle cache")
    if not poms:
        artifact_errors.append(f"quickjs POM {quickjs_version} not found in the Gradle cache")
    if not modules:
        artifact_errors.append(
            f"quickjs .module metadata {quickjs_version} not found in the Gradle cache",
        )

    artifact_errors.extend(
        checksum_errors(
            [
                *((path, AAR_SHA256, "AAR") for path in aars),
                *((path, POM_SHA256, "POM") for path in poms),
                *((path, MODULE_SHA256, ".module") for path in modules),
            ],
        ),
    )

    pom_stdlib = (
        pom_kotlin_stdlib_version(poms[0].read_text(encoding="utf-8")) if poms else None
    )

    xml_path = latest_dependency_artifact()
    resolved_quickjs, resolved_stdlib = parse_resolved_versions(
        xml_path.read_text(encoding="utf-8", errors="replace"),
    )

    errors = evaluate_pins(
        quickjs_version=quickjs_version,
        upstream_stdlib=upstream_stdlib,
        expected_runtime_stdlib=expected_runtime_stdlib,
        compile_stdlib=compile_stdlib,
        pom_stdlib=pom_stdlib,
        resolved_quickjs=resolved_quickjs,
        resolved_stdlib=resolved_stdlib,
        artifact_errors=artifact_errors,
    )

    print(
        f"catalog: quickjs-kt-android={quickjs_version} "
        f"kotlin(compile/expected runtime)={compile_stdlib}/{expected_runtime_stdlib} "
        f"quickjs-kotlin-stdlib(upstream POM)={upstream_stdlib}",
    )
    print(f"AAR matches: {[str(path) for path in aars]}")
    print(f"POM matches: {[str(path) for path in poms]}")
    print(f".module matches: {[str(path) for path in modules]}")
    print(f"resolution artifact: {xml_path}")
    print(f"resolved: quickjs={sorted(resolved_quickjs)} kotlin-stdlib={sorted(resolved_stdlib)}")

    if errors:
        for error in errors:
            print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print("OK: quickjs checksums, upstream POM requirement and resolved runtime pins match")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
