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
//
// **2026-10-26 批次 23 复核状态（KD-72，保持 open）**：工作树已生成 13 个**未跟踪**的
// gradle.lockfile（app、core/*、feature/*；:benchmark 缺锁），但生成时的树包含他线未提交的
// core/ui/core/export/feature/tutor/settings 构建改动，且按 `dependencies` 报告任务生成的锁
// 只覆盖 debugCompileClasspath 与工具链配置、缺 test/androidTest/runtime。这批锁**不能原样
// 提交**：与 HEAD 的解析图不符（例如 core/ui 锁缺 HEAD 仍使用的 richtext-commonmark），
// DEFAULT 模式会在解析期判红。必须在干净、待提交的树上用下面的聚合任务重新生成并把锁一起入库。
//
// 生成/CI 校验统一命令（默认 LockMode.DEFAULT；官方没有 `--locked` 参数）：
//   ./gradlew --no-configuration-cache --no-parallel --console=plain \
//     lockAllDependencies --write-locks
// `lockAllDependencies` = 每个子项目的 `dependencies` 报告（工具链/KSP/compiler 配置）
// + `resolveLockableClasspaths`（所有 *CompileClasspath / *RuntimeClasspath，含 unitTest /
// androidTest / runtime）。`dependencies` 在多项目里只作用于被点名的 project，故用聚合任务。
// 暂不启用全量 verification-metadata（R2 §2.5：字节级风险已由 quickjs 三构件 sha256 门
// 覆盖，全图 166 坐标 + 元数据的维护成本不划算）；锁文件齐全且 CI 绿后再评估 LockMode.STRICT。
val kotlinStdlibVersion = libs.versions.kotlin.get()

subprojects {
    dependencyLocking {
        lockAllConfigurations()
    }

    // 批次 23 T1 复核修复：`dependencies` 报告任务的默认集合只到 declarable 配置，
    // 实际生成的锁缺 debugRuntimeClasspath/test*/androidTest*（KD-72 复核发现 #5）。
    // 这个任务只解析 classpath 配置，补齐运行期与测试面的锁状态；生成与 CI 共用。
    tasks.register("resolveLockableClasspaths") {
        group = "dependency locking"
        description = "解析所有 *CompileClasspath/*RuntimeClasspath（含 unitTest/androidTest），配合 --write-locks 写锁"
        notCompatibleWithConfigurationCache("Resolves filtered configurations at execution time")
        doFirst {
            require(gradle.startParameter.isWriteDependencyLocks) {
                "$path must be run with --write-locks (see lockAllDependencies)"
            }
        }
        doLast {
            configurations.filter { configuration ->
                configuration.isCanBeResolved &&
                    (
                        configuration.name.endsWith("CompileClasspath") ||
                            configuration.name.endsWith("RuntimeClasspath")
                        )
            }.forEach { it.resolve() }
        }
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

// 聚合任务：`--write-locks` 时一次解析每个子项目；新模块自动纳入，避免手写任务列表漂移。
tasks.register("lockAllDependencies") {
    group = "dependency locking"
    description = "解析每个子项目的可锁配置；配合 --write-locks 生成 gradle.lockfile"
    dependsOn(subprojects.map { "${it.path}:dependencies" })
    dependsOn(subprojects.map { "${it.path}:resolveLockableClasspaths" })
}
