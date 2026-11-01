# Phase 0 has no reflection-based network or serialization stack, but
# androidx.work instantiates its Room-generated WorkDatabase_Impl
# reflectively during androidx.startup — under R8 full mode the unused
# default constructor is stripped and startup crashes with
# NoSuchMethodException before any UI is shown (observed on emulator,
# 2026-09-06). Room ships consumer rules for app-owned databases; WorkManager's
# bundled database needs this explicit keep.
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}

# ML Kit 的组件注册器由 ComponentDiscovery 按类名反射实例化（无参构造器），R8 full mode
# 会把"没人直接 new"的它们剥掉：release 变体启动时三条 NoSuchMethodException
# （CommonComponentRegistrar / TextRegistrar / VisionCommonRegistrar），于是中文识别那套
# 组件根本没注册。debug 变体不过 R8，所以这个缺口只在 release 上存在——2026-09-30 把
# release APK 真正装上模拟器启动才第一次看见（此前 release 只在 CI 里 assemble、从不启动）。
-keep class com.google.mlkit.common.internal.CommonComponentRegistrar { <init>(); }
-keep class com.google.mlkit.vision.text.internal.TextRegistrar { <init>(); }
-keep class com.google.mlkit.vision.common.internal.VisionCommonRegistrar { <init>(); }

# R-04 K6（批次 36）——消灭的崩溃：NoClassDefFoundError: Landroidx/tracing/Trace;
# （r04-k6-logcat.txt:92，:110 Caused by ClassNotFoundException: androidx.tracing.Trace）。
# 机理：androidx.test（monitor 1.8.0，ActivityScenario / AndroidXTracer$AndroidXTracerSpan）
# 按名引用 androidx.tracing.Trace（androidTest dex 实测 30 处，全部指向该类，恰三个
# 静态签名 beginSection(String)/endSection()/forceEnableAppTracing()）。该共享依赖被
# AGP 排除在测试 APK 之外（androidTest dex 中 androidx.tracing.* / androidx.work.* 类
# 定义均为 0、测试 mapping 0 条 tracing 条目），主包 R8 又把整个 Trace 类内联删除
# （主 mapping 只剩内联帧记录）——合并 instrumentation classloader 里该类两 APK 都没有，
# 测试 dex 的按名 invoke-static 悬空，instrumentation sendStatus 上报崩溃路径必经。
# app/r04-test-keep.pro 结构性修不了它（keep 只作用于 R8 输入里的程序类，不能凭空
# 造类），唯一落点是主包保类。成员集 = 测试 dex 实测引用的恰三个签名；主包自身无
# 调用者，保的是类存在性，生产其余混淆面不动（约 1KB 门面类不被内联）。
# j$ 那半边的修复在 app/r04-test-keep.pro 规则 4。
# 研究：docs/research/2026-10-30-r04-k6-keep-rules.md §三/§四.2。
-keep class androidx.tracing.Trace {
    void beginSection(java.lang.String);
    void endSection();
    void forceEnableAppTracing();
}
