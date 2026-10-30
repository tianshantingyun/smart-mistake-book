# R-04 test-code keep rules.
#
# Applied only through `release { testProguardFiles("r04-test-keep.pro") }` in
# app/build.gradle.kts, and consumed only when the R-04 track builds with
# `-Psmbk.testBuildType=release`. AGP semantics (official DSL reference 9.3):
# "Adds proguard rule files to be used when processing test code. Test code
# needs to be processed to apply the same obfuscation as was done to main code."
#
# Scope: these rules apply to the androidTest (test) dex only. The main APK's
# obfuscation surface is still governed by app/proguard-rules.pro alone.
#
# This file must NEVER disable obfuscation: that would cancel the very scenario
# R-04 verifies (R8 renaming of the release build). The claimed gate is real and
# re-runnable since batch 35 (批次 35 T3):
#     python tools/ci/check_r04_keep_rules.py
# It greps the app module's proguard rule files (app/proguard-rules.pro and this
# file) for the `-dontobfuscate` directive in *rule text* (comments excluded) and
# requires 0 hits in each; any hit or a missing rule file exits non-zero.

# 1) Instrumentation loads test classes reflectively by name
#    (`am instrument -e class <fqcn>#<method>`), so test class names must survive.
-keep class com.tingyun.smartmistakebook.** {
    <init>(...);
    @org.junit.Test <methods>;
    @org.junit.Before <methods>;
    @org.junit.After <methods>;
    @org.junit.BeforeClass <methods>;
    @org.junit.AfterClass <methods>;
}

# 2) JUnit discovers @Test methods through annotations; keep the annotated methods.
-keepclassmembers class * {
    @org.junit.Test <methods>;
}

# 3) The runner named in the manifest and the test framework it loads are
#    resolved by class name at runtime.
-keep class androidx.test.** { *; }
-keep class org.junit.** { *; }
-keep class junit.** { *; }

# 4) Batch-36 K6 fix. Eliminates the crash: "NoSuchMethodError: No direct method
#    <init>(Ljava/util/Map;)V in class Lj$/util/g;" (r04-k6-logcat.txt:86).
#    Mechanism: the app APK and the androidTest APK are minified by two
#    independent R8 runs with no renaming coordination, and the merged
#    instrumentation classloader loads the test dex FIRST
#    (r04-k6-logcat.txt:46). Both runs draw short names from the same j$/...
#    pool, so the test dex can occupy a name the main dex's renamed class
#    already uses — observed pair (both files read from the crash-time
#    artifacts):
#      main mapping :871393  j$.util.DesugarCollections$SynchronizedMap -> j$.util.g
#      test mapping :49004   j$.util.Comparators$NaturalOrderComparator -> j$.util.g
#    (same origin class maps to j$.util.e in main, :871329 — direct proof that
#    the two renamers never coordinate.) At runtime j$.util.DesugarCollections
#    (kept by full name) resolved `new j$/util/g(map)` against the TEST dex's
#    enum comparator, which has no Map constructor.
#    Why this rule works: keeping every j$ class under its original name with
#    all members (a) stops the test dex from occupying any main-renamed name
#    (j$/util/g disappears from the test dex) and (b) makes the surviving
#    shadowing copies supersets of the main dex's shrunken ones, so every main
#    j$ member call still resolves.
#    Why the whole namespace, not j$.util.**: the collision is package-agnostic
#    (j$/time etc. are renamed independently in both APKs too); j$ is R8's
#    reserved desugar prefix, not part of the production obfuscation surface.
#    Why members { *; }: -keep without a member block only preserves the class
#    and its no-arg constructor (Guardsquare manual), which would move this
#    NoSuchMethodError to another member instead of eliminating it.
#    Deliberately NOT mirrored into app/proguard-rules.pro: that would inflate
#    the production dex to the full desugar jar for zero added determinism.
#    The other K6 half (androidx.tracing.Trace) is NOT fixable here — that
#    class is absent from both dexes, and keep cannot create a class that is
#    not in R8's input; its keep lives in app/proguard-rules.pro.
#    Research: docs/research/2026-10-30-r04-k6-keep-rules.md §二/§四.1.
-keep class j$.** { *; }
