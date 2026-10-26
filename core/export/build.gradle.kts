plugins {
    alias(libs.plugins.android.library)
    // 6B-2 卷面草稿落盘：复用 core:model 已在用的 kotlinx.serialization（不引第三方）。
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tingyun.smartmistakebook.core.export"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
