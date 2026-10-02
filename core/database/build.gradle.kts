plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room3)
    alias(libs.plugins.kotlin.serialization)
    jacoco
}

android {
    namespace = "com.tingyun.smartmistakebook.core.database"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        getByName("debug") {
            enableUnitTestCoverage = true
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core:model"))
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.room3.paging)
    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.sqlite.framework)
    ksp(libs.androidx.room3.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.android)
    // 批次 2 / 规格 §4.2：仪器化夹具把「当前投影版本」绑到 `LearningProjector.VERSION`
    // （消灭陈旧占位 v6/v7——与真实当前版脱钩且无测试钉住）。只进测试类路径，生产依赖不变。
    androidTestImplementation(project(":core:domain"))
    // B6 回退演练（`ProjectionRollbackDrillInstrumentedTest`）：归档行的**生产者**是全量重放的
    // drainer（`StudyProjectionDrainer`，internal 于 core:data）——演练要「真实重放产生归档」，
    // 所以经公开的 `RoomBackedStudyExperienceRepository` 驱动那个真实 drainer。同样只进测试
    // 类路径；生产源码集的依赖方向（core:database 不依赖 core:data）不受影响。
    androidTestImplementation(project(":core:data"))
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

/**
 * Unit-test coverage for an Android library (KD-5): Kover 0.9.1 cannot see
 * AGP 9's built-in-Kotlin build variants, so this module uses AGP's own
 * instrumentation (`enableUnitTestCoverage`) plus a Jacoco report with a
 * stable XML path that `tools/ci/generate_status.py` reads.
 */
tasks.register<JacocoReport>("unitTestCoverageXmlReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(false)
        csv.required.set(false)
        xml.outputLocation.set(layout.buildDirectory.file("reports/coverage/unit-test.xml"))
    }
    executionData.setFrom(
        layout.buildDirectory.file(
            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
        ),
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    // AGP 9 built-in Kotlin compiles to intermediates/built_in_kotlinc; the
    // javac output stays in intermediates/javac. Both are needed.
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug")) {
            include("**/classes/**")
            exclude("**/R.class", "**/BuildConfig.class", "**/*_Impl.class", "**/*_Factory.class")
        },
        fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes")) {
            exclude("**/R.class", "**/BuildConfig.class", "**/*_Impl.class", "**/*_Factory.class")
        },
    )
}
