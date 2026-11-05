#!/usr/bin/env bash
# Reproducible runner for the E-domain independent JVM probe (EDomainProbe).
#
# It compiles the saved probe source (e-domain-probe.java.txt, sha-pinned in the
# E-domain evidence index) against the REAL compiled Kotlin classes and the
# project's pinned kotlin-stdlib 2.3.21, then runs it. `--mutate` forces the red
# control: the probe must report a violation and exit 1, proving it is not vacuous.
#
# Usage:
#   bash docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe-run.sh
#   bash docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe-run.sh --mutate
set -u

ROOT="$(cd "$(dirname "$0")/../../../../.." && pwd)"
cd "$ROOT" || exit 2

SRC_TXT="docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.java.txt"
WORK="build/b45-e/probe-run"
mkdir -p "$WORK/out" "$WORK/src" build/b45-e/lib

STDLIB="$(ls "$HOME"/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.3.21/*/kotlin-stdlib-2.3.21.jar 2>/dev/null | head -1)"
if [ -z "${STDLIB:-}" ]; then
  echo "ERROR: kotlin-stdlib 2.3.21 not found in Gradle cache" >&2
  exit 2
fi
cp "$STDLIB" build/b45-e/lib/kotlin-stdlib.jar

cp "$SRC_TXT" "$WORK/src/EDomainProbe.java"

CP="core/data/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes;core/domain/build/classes/kotlin/main;core/model/build/classes/kotlin/main;build/b45-e/lib/kotlin-stdlib.jar"

javac -cp "$CP" -d "$WORK/out" "$WORK/src/EDomainProbe.java" || exit 2
java -cp "$WORK/out;$CP" EDomainProbe "$@"
