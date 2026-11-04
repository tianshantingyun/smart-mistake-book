package com.tingyun.smartmistakebook.visualjs

import android.os.Bundle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * OFF/ON 配对臂开关（批次 41 / WS4 item 26）。
 *
 * **它消灭的具体失败**：批次 40 的 isolation 只有单臂 15/15，没有"同一构建、两种开关"的
 * 配对轮，红集无法比较（`step04-isolation.md` §4）。本开关让同一份 APK 通过
 * `-e smb.t4.arm OFF|ON` 切换**测试侧**钩子：
 * - `ON`：注册 [T4SeamProbeRule]，每条用例首尾记录 `SMB_T4_HOOK:` 行，并在用例结束时
 *   读取客户端连接四阶段时间戳（[VisualJsClientEngine.connectStageTimestampsForTest]）；
 * - `OFF`：不注册任何钩子（不读时间戳、不写钩子日志）；
 * - 未传：`ABSENT`——报告工具据此拒绝配对（没有声明臂的运行不是控制臂）。
 *
 * **边界（必须如实）**：这里切换的只是**测试侧**钩子。批次 39 T4 的 6 处打点写点在
 * `app/src/main/.../VisualJsClientEngine.kt` 里是编译进 APK 的，本轨无权改热区之外的
 * 主源码路径以外的生产文件；因此 `OFF` **不是**"去 T4 控制轮"。真正的控制轮需要
 * `tools/ci/t4_control_marker.py` 记录的"生产侧 6 处写点已移除 + 引擎 sha"标记；
 * 配对报告只在 `--off-arm-production-t4 removed` 且标记 verify 通过时才输出 T4 因果。
 */
internal enum class IsolationArm(val wire: String) {
    OFF("OFF"),
    ON("ON"),
    ABSENT("ABSENT"),
    ;

    val testSideHooks: Boolean get() = this == ON
}

internal object IsolationArms {

    const val ARG_ARM: String = "smb.t4.arm"

    fun read(arguments: Bundle = InstrumentationRegistry.getArguments()): IsolationArm {
        val raw = arguments.getString(ARG_ARM)?.trim()?.uppercase(Locale.ROOT) ?: return IsolationArm.ABSENT
        return IsolationArm.values().firstOrNull { it.wire == raw } ?: IsolationArm.ABSENT
    }
}

internal object IsolationRuntime {

    @Volatile
    var arm: IsolationArm = IsolationArm.ABSENT
        private set

    @Volatile
    private var declared: Boolean = false

    /**
     * 只读一次 instrumentation 参数。JUnit 的 [TestRule] 包住 `@Before`，所以规则与
     * 门前置都先调这里；否则首条用例的钩子会看到 `ABSENT`。
     */
    fun ensureDeclared(): IsolationArm {
        if (!declared) {
            synchronized(this) {
                if (!declared) {
                    arm = IsolationArms.read()
                    declared = true
                    Log.i(
                        LowLoadProbe.TAG,
                        "SMB_ISOLATION_ARM: arm=${arm.wire} t4_production=compiled_in " +
                            "test_side_hooks=${arm.testSideHooks}",
                    )
                }
            }
        }
        return arm
    }
}

/**
 * T4 打点/钩子的测试侧注册器（仅在 [IsolationArm.ON] 时活动）。
 *
 * 钩子只做两件事：用例边界日志、用例结束时读取该用例创建的客户端引擎的时间戳；
 * 不改变任何生产路径、不重试、不吞异常（读取失败只记 `no_timestamp_source`）。
 */
internal class T4SeamProbeRule : TestRule {

    private val engines = CopyOnWriteArrayList<VisualJsClientEngine>()

    fun register(engine: VisualJsClientEngine) {
        if (IsolationRuntime.ensureDeclared().testSideHooks) {
            engines.add(engine)
        }
    }

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            // 规则包住 @Before，必须在读 arm 之前先声明（见 ensureDeclared）。
            val hooks = IsolationRuntime.ensureDeclared().testSideHooks
            // 批次 43 / WS3 item 19：witness 在**两臂**都写（它只观察、不参与 T4 语义）。
            // currentMethod 是 15 个用例体拼方法名的唯一来源（Description），
            // 与 tools/ci/visual_js_isolation_methods.txt 互为校验。
            IsolationWitness.currentMethod = description.methodName
            IsolationWitness.ruleBegin(description.methodName)
            if (hooks) {
                Log.i(TAG, "SMB_T4_HOOK: begin method=${description.methodName}")
            }
            try {
                base.evaluate()
            } finally {
                IsolationWitness.ruleEnd(description.methodName)
                IsolationWitness.currentMethod = null
                if (hooks) {
                    engines.forEach { engine ->
                        val stamps = runCatching { engine.connectStageTimestampsForTest() }.getOrNull()
                        val detail = stamps?.let {
                            "bind_requested=${it.bindRequestedAtMillis} " +
                                "bind_connected=${it.bindConnectedAtMillis} " +
                                "ready=${it.readyAtMillis} " +
                                "execute_accepted=${it.executeAcceptedAtMillis} " +
                                "first_byte=${it.firstByteAtMillis}"
                        } ?: "no_timestamp_source"
                        Log.i(TAG, "SMB_T4_HOOK: end method=${description.methodName} $detail")
                    }
                    engines.clear()
                }
            }
        }
    }

    private companion object {
        const val TAG = "SMB_T4_HOOK"
    }
}
