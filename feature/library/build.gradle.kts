plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.tingyun.smartmistakebook.feature.library"
    compileSdk = 37
    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // 切分复核的仪器化用例直接依赖 :core:data / :core:database（真实 Room 断言 confirm_state），
        // 这两个模块要求消费方开启 core library desugaring——缺它 checkDebugAndroidTestAarMetadata
        // 直接失败、7 个用例一个都跑不了（与 core:data / core:database / feature:tutor 同口径）。
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:export"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.paging.compose)
    testImplementation(libs.junit)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // 切分复核的逐题提交/恢复用例按「真实 Room 断言 confirm_state」验（T4/AUD-03）：
    // 要真库工厂，也要那条窄口的真实实现，不拿测试自造的替身顶替。
    androidTestImplementation(project(":core:database"))
    androidTestImplementation(project(":core:data"))
}
