#!/usr/bin/env python3
"""Pin check for the quickjs-kt-android supply chain (批次 21 T3 复核).

The catalog pins the *direct* coordinate and documents the AAR/POM sha256, but a
comment is not a build gate. This script turns the facts the build already
resolves into a checkable gate:

1. the cached AAR and POM must match the documented sha256;
2. the POM's transitive ``kotlin-stdlib`` requirement must match the explicit
   ``quickjs-kotlin-stdlib`` catalog entry;
3. the resolved runtime graph (AGP ``artifact-dependencies.xml``) must contain
   the same quickjs version and exactly the pinned ``kotlin-stdlib`` version.

Why this exists: quickjs-kt-android 1.0.15 pulls ``kotlin-stdlib 2.4.10`` while
the project compiles with 2.3.21. Gradle silently resolves the higher runtime
version; without a gate, the next AAR bump (or a resolution change) can move it
again with no diff anywhere in this repository.

Run after a Gradle build that produced lint/dependency artifacts:
    python3 tools/ci/check_dependency_pins.py

Use ``--expect-kotlin-runtime`` to prove the gate is live (it should fail when
given the compile version 2.3.21):

    python3 tools/ci/check_dependency_pins.py --expect-kotlin-runtime 2.3.21
"""

from __future__ import annotations

import argparse
import hashlib
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "gradle" / "libs.versions.toml"
CACHE_ROOTS = (ROOT / ".gradle" / "caches", Path.home() / ".gradle" / "caches")
QUICKJS_GROUP = "io.github.dokar3"
AAR_SHA256 = "406c3efb80d5fa3495df4a7f2663be8cc30c974fe5c2368effa929de77cf0ed8"
POM_SHA256 = "cc3ef4b3e8674771ef29d059c28935dd009e6dac7fb90765e497bd8414fe2bbc"
MODULE_SHA256 = "4d1534e780ab999aaa757ffe4017ca3a533ed3df43438c4622fd76db1f69e465"


def catalog_version(name: str) -> str:
    text = CATALOG.read_text(encoding="utf-8")
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


def pom_kotlin_stdlib_version(pom: Path) -> str | None:
    text = pom.read_text(encoding="utf-8")
    match = re.search(
        r"<artifactId>kotlin-stdlib</artifactId>\s*<version>([^<]+)</version>",
        text,
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


def resolved_versions(xml_path: Path) -> tuple[set[str], set[str]]:
    root = ET.parse(xml_path).getroot()
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


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--expect-kotlin-runtime",
        help="override the expected resolved kotlin-stdlib version (for proving the gate is live)",
    )
    args = parser.parse_args()

    quickjs_version = catalog_version("quickjs-kt-android")
    pinned_stdlib = args.expect_kotlin_runtime or catalog_version("quickjs-kotlin-stdlib")
    compile_stdlib = catalog_version("kotlin")

    errors: list[str] = []

    aars = find_quickjs_artifacts(quickjs_version, ".aar")
    poms = find_quickjs_artifacts(quickjs_version, ".pom")
    modules = find_quickjs_artifacts(quickjs_version, ".module")
    if not aars:
        errors.append(f"quickjs AAR {quickjs_version} not found in the Gradle cache")
    if not poms:
        errors.append(f"quickjs POM {quickjs_version} not found in the Gradle cache")
    if not modules:
        errors.append(f"quickjs .module metadata {quickjs_version} not found in the Gradle cache")
    for artifact, expected, label in (
        *((path, AAR_SHA256, "AAR") for path in aars),
        *((path, POM_SHA256, "POM") for path in poms),
        *((path, MODULE_SHA256, ".module") for path in modules),
    ):
        actual = sha256(artifact)
        if actual != expected:
            errors.append(f"{label} sha256 mismatch: {artifact} -> {actual}")

    pom_stdlib = pom_kotlin_stdlib_version(poms[0]) if poms else None
    if pom_stdlib is not None and pom_stdlib != pinned_stdlib:
        errors.append(
            f"POM requires kotlin-stdlib {pom_stdlib}, pinned runtime is {pinned_stdlib}",
        )

    xml_path = latest_dependency_artifact()
    resolved_quickjs, resolved_stdlib = resolved_versions(xml_path)
    if quickjs_version not in resolved_quickjs:
        errors.append(f"resolved quickjs version(s) {sorted(resolved_quickjs)} do not include {quickjs_version}")
    if resolved_stdlib != {pinned_stdlib}:
        errors.append(
            f"resolved kotlin-stdlib {sorted(resolved_stdlib)} != pinned {pinned_stdlib} "
            f"(compile catalog says {compile_stdlib})",
        )

    print(f"catalog: quickjs-kt-android={quickjs_version} kotlin(compile)={compile_stdlib} "
          f"quickjs-kotlin-stdlib(runtime pin)={pinned_stdlib}")
    print(f"AAR matches: {[str(path) for path in aars]}")
    print(f"POM matches: {[str(path) for path in poms]}")
    print(f".module matches: {[str(path) for path in modules]}")
    print(f"resolution artifact: {xml_path}")
    print(f"resolved: quickjs={sorted(resolved_quickjs)} kotlin-stdlib={sorted(resolved_stdlib)}")

    if errors:
        for error in errors:
            print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print("OK: quickjs AAR/POM checksums and resolved runtime pins match")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
