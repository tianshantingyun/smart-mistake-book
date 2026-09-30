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
