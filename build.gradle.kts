plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.androidx.room3) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
}

// 批次 22 T3 供应链加固（依据 docs/research/2026-10-26-dependency-governance.md §2.5/§3.4）：
// 本仓库的唯一版本漂移是 quickjs-kt-android 1.0.15 的 POM 以**软约束**要求 kotlin-stdlib
// 2.4.10，把编译期 2.3.21 静默抬成运行时 2.4.10（仓库内零 diff）。两道门分工：
//   - dependency locking 钉"解析出的版本"，这类漂移在解析期失败；
//   - tools/ci/check_dependency_pins.py 钉 quickjs 三构件字节（sha256）与上/下游版本事实。
// 锁文件生成（本轨禁跑 Gradle，由门禁轨执行并把结果回填记录；默认 LockMode 下缺锁状态
// 不阻塞构建，生成后 CI 的常规构建任务自动校验，官方没有 `--locked` 参数）：
//
// **2026-10-26 复核状态（KD-72）**：仓库内仍没有任何 gradle.lockfile（find 0 命中），
// 因此 subprojects.dependencyLocking 目前只是声明，LENIENT 模式下锁门**未生效**；
// 在锁文件入库前不得把"依赖锁定"写成已完成。下面的命令是待执行的生成步骤。
//   ./gradlew --write-locks :app:dependencies :core:model:dependencies \
//       :core:domain:dependencies :core:database:dependencies :core:data:dependencies \
//       :core:export:dependencies :core:ui:dependencies :core:markdown:dependencies \
//       :feature:capture:dependencies :feature:review:dependencies \
//       :feature:tutor:dependencies :feature:library:dependencies \
//       :feature:profile:dependencies :benchmark:dependencies
// 注意：`dependencies` 在多项目构建里只作用于被点名的 project（Gradle dependency_locking
// 官方页），只在 root 上单跑不会写出 14 个子项目的锁文件；锁文件必须入库。
// 暂不启用全量 verification-metadata（R2 §2.5：字节级风险已由 quickjs 三构件 sha256 门
// 覆盖，全图 166 坐标 + 元数据的维护成本不划算）；锁文件生成后可选开 LockMode.STRICT
// 防新模块漏锁，当前不开启（尚无锁状态，开了会让所有构建失败）。
val kotlinStdlibVersion = libs.versions.kotlin.get()

subprojects {
    dependencyLocking {
        lockAllConfigurations()
    }

    // 路径 A（R2 推荐）：把运行时 kotlin-stdlib 约束回编译期版本。依据是该 AAR 的字节码
    // 事实（R2 §3.1 实测）：88 条 stdlib 成员引用在 2.3.21/2.4.10 两个 jar 中全部可解析
    // （unresolved=0），Kotlin 2.3.21 编译器直接编译 AAR 探针 exit 0 且 inline 成功；
    // POM 的 2.4.10 是库作者的构建期声明，不是字节码硬要求。
    // 回退判据（满足任一条即按 R2 路径 B 评估全项目升级，不在本块悄悄放宽）：
    //   1) AAR 升级后字节码扫描出现 unresolved > 0；
    //   2) 设备轨出现 NoSuchMethodError/NoClassDefFoundError 且归因 AAR（运行期真的用到 2.4 API）。
    // 当前路径 B 被 KSP 无 2.4 兼容版本阻断（R2 §3.4-B）；用 eachDependency+useVersion（而非
    // force）是为了让 dependencyInsight 能显示下面的 because 理由。
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-stdlib") {
                useVersion(kotlinStdlibVersion)
                because(
                    "批次 22 T3：与编译期 Kotlin 对齐；quickjs AAR 字节码已核不依赖 2.4 API " +
                        "(docs/research/2026-10-26-dependency-governance.md §3.1)",
                )
            }
        }
    }
}
