plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.androidx.baselineprofile)
}

// R-04（release/R8 变体上跑隔离往返）：AGP 的 `testBuildType` 是**模块级**开关，
// 直接写死 "release" 会把日常设备轨（assembleStrictOfflineDebugAndroidTest，14 套件基线）
// 一起改向。这里用 Gradle 属性门，默认 "debug" = 与 AGP 默认行为一致、零影响；
// 只有显式 `-Psmbk.testBuildType=release` 才切到 release 测试轨。
// 消灭的具体失败：为了跑两例 release 往返而不得不改全局测试变体，导致日常 debug 轨被顺手改道。
val r04TestBuildType = providers.gradleProperty("smbk.testBuildType").orNull ?: "debug"

android {
    namespace = "com.tingyun.smartmistakebook"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    signingConfigs {
        create("release") {
            // Placeholder — populated by environment variables at build time.
            // Do NOT check real keystore files into source control.
        }
    }

    defaultConfig {
        applicationId = "com.tingyun.smartmistakebook"
        minSdk = 23
        targetSdk = 36
        // RELEASE.md gate 7: bumped for the first post-audit build. The stale
        // "phase0" suffix is gone (scenario-registry: old Phase labels are no
        // longer acceptance labels).
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // 16 KB page-size readiness (batch 20 / T8, per docs/research/2026-10-25-16kb-arm64-api23-build.md §5):
        // keeps only the two ABIs whose bundled .so files are fully 16 KB ELF-aligned.
        // Drops: ML Kit 32-bit .so (armeabi-v7a/x86 LOAD p_align=4096; x86 RELRO shares a
        // page with writable data — the exact crash shape called out by the official guide),
        // the half-broken x86 ABI (litert ships no x86), and ~23 MiB of uncompressed 32-bit .so.
        // Cost (product-level, accepted for this track): no install on pure 32-bit armv7 devices.
        ndk {
            abiFilters += setOf("arm64-v8a", "x86_64")
        }
    }

    flavorDimensions += "networkMode"
    productFlavors {
        create("strictOffline") {
            dimension = "networkMode"
            applicationIdSuffix = ".offline"
            versionNameSuffix = "-offline"
        }
        create("localFirst") {
            dimension = "networkMode"
            applicationIdSuffix = ".localfirst"
            versionNameSuffix = "-localfirst"
        }
    }

    // 默认 "debug"（AGP 默认值，日常设备轨零变化）；R-04 实验用 -Psmbk.testBuildType=release。
    testBuildType = r04TestBuildType

    buildTypes {
        create("benchmark") {
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isProfileable = true
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            // 批次 22 T5 / KD-69：matchingFallbacks 不继承 proguardFiles；AGP 9.3 起
            // strict full mode 不再隐式保留被 keep 类的无参构造器，缺项目规则会裁掉
            // WorkDatabase_Impl 与 ML Kit registrar 的 <init>（启动 FATAL / OCR 崩溃）。
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("internal") {
            matchingFallbacks += listOf("release")
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            // 批次 22 T5 / KD-69：同 benchmark，规则集与 release 对齐。
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            // Signing is injected at evaluation time by configureReleaseSigningIfAvailable;
            // the validateReleaseSigning task fails the build before release packaging
            // if any signing environment variable is missing.
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // R-04：只在 `-Psmbk.testBuildType=release` 时被消费（AGP 语义 = "test code
            // 被处理以套用主代码同一混淆"）。规则只作用于 androidTest 的 test dex，
            // 不削弱主包混淆面；不引入任何关闭混淆的指令（那会取消本实验要考的场景）。
            testProguardFiles("r04-test-keep.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // 批次 22 T1：:visual_js 进程层的 AIDL 在 app/src/main/aidl。AGP 8.0 起
        // android.defaults.buildfeatures.aidl 默认 false，不显式开启则 AIDL 不编译
        //（R1 §6.1；AGP 9.3 BuildFeatures 仍默认 false）。AIDL/Service 全部落 app 模块，
        // 不新增 Gradle 模块（settings.gradle.kts 是共享热区）。
        aidl = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Populates the release signing config at evaluation time; silently skips when env vars are absent.
fun configureReleaseSigningIfAvailable() {
    val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")
    val releaseStoreFile = System.getenv("RELEASE_STORE_FILE")
    val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD")
    if (releaseKeyAlias != null && releaseKeyPassword != null &&
        releaseStoreFile != null && releaseStorePassword != null
    ) {
        android.signingConfigs.getByName("release").apply {
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            storeFile = file(releaseStoreFile)
            storePassword = releaseStorePassword
        }
    }
}

// 求值期注入签名，缺环境变量时静默跳过。
configureReleaseSigningIfAvailable()

tasks.register("validateReleaseSigning") {
    doLast {
        val missing = buildList {
            if (System.getenv("RELEASE_KEY_ALIAS").isNullOrBlank()) add("RELEASE_KEY_ALIAS")
            if (System.getenv("RELEASE_KEY_PASSWORD").isNullOrBlank()) add("RELEASE_KEY_PASSWORD")
            if (System.getenv("RELEASE_STORE_FILE").isNullOrBlank()) add("RELEASE_STORE_FILE")
            if (System.getenv("RELEASE_STORE_PASSWORD").isNullOrBlank()) add("RELEASE_STORE_PASSWORD")
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release signing is missing required environment variables: ${missing.joinToString(", ")}",
            )
        }
    }
}

// Apply release signing before release bundle/assemble tasks run.
tasks.configureEach {
    if (name.contains("Release", ignoreCase = true) &&
        (name.startsWith("bundle") || name.startsWith("assemble") || name.startsWith("package"))
    ) {
        dependsOn("validateReleaseSigning")
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:ui"))
    // L7 后台导出：worker 直接调导出核心（渲染编排 / 内容判定 / 交付链）。
    implementation(project(":core:export"))
    implementation(project(":feature:capture"))
    implementation(project(":feature:review"))
    implementation(project(":feature:tutor"))
    implementation(project(":feature:library"))
    implementation(project(":feature:profile"))

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.work.runtime.ktx)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    // L7 成果入口的交付接线：保存/分享必须是既有的 CREATE_DOCUMENT / SEND 意图（拦截断言）。
    androidTestImplementation(libs.androidx.test.espresso.intents)
    testImplementation(libs.junit)
}
