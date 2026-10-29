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
