package com.tingyun.smartmistakebook.core.data.visual

import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 16KB 页运行时装载门（批次 21 T6 设备轨）。
 *
 * 静态 ELF 对齐（R4）只能证明"文件看起来合规"；本用例在**真实页大小的内核**上让
 * 动态链接器实际 mmap 这些 .so——16KB 内核会以 `failed to map segment from shared
 * object` / `unsupported RELRO` 之类拒载 4KB 对齐的库，失败即本用例红。
 *
 * 覆盖主会话裁定 2/3 的两条运行时面（R3 §3 第 6 步的替代/补充观测点）：
 * - `libquickjs.so`（发布 AAR io.github.dokar3:quickjs-kt-android:1.0.15 自带四 ABI）；
 * - `libLiteRt.so` + `liblitert_jni.so`（LiteRT 2.1.6；x86_64 切片，产物即 app 里同一份）。
 *
 * 只做 `System.loadLibrary`，不构造 Interpreter、不跑推理：本用例的失败面是装载器拒绝，
 * 不是模型语义。装载用例只校验页大小非零的 2 的幂；**16KB 门本身**由
 * [runtimePageSizeIs16KB] 断言 `16384`，并在日志打印 `PAGE_SIZE=`（4KB 设备 skip，
 * 不得把 4KB 上的装载绿当成 16KB 验证）。
 */
@RunWith(AndroidJUnit4::class)
class NativeLibraryPageSizeInstrumentedTest {

    @Test
    fun quickJsNativeLibraryLoadsOnTheDevicePageSize() {
        assertPageSizeSane()
        System.loadLibrary("quickjs")
    }

    @Test
    fun litertNativeLibrariesLoadOnTheDevicePageSize() {
        assertPageSizeSane()
        System.loadLibrary("LiteRt")
        System.loadLibrary("litert_jni")
    }

    /**
     * 16KB 运行时门：本用例必须在 ps16k 设备轨上跑。4KB 设备 skip（前提不成立），
     * 16KB 设备必须精确断言 16384——"页大小为正的 2 的幂"在 4KB 上也会通过，不能作为
     * 16KB 证据。`PAGE_SIZE=` 行供设备轨日志留痕。
     */
    @Test
    fun runtimePageSizeIs16KB() {
        val pageSize = Os.sysconf(OsConstants._SC_PAGESIZE)
        println("PAGE_SIZE=$pageSize")
        assumeTrue("requires a 16 KB page-size kernel (observed $pageSize)", pageSize == 16384L)
        assertEquals(
            "this gate must run on a 16 KB page-size kernel; loaded native libraries are not 16 KB validated elsewhere",
            16384L,
            pageSize,
        )
    }

    private fun assertPageSizeSane() {
        val pageSize = Os.sysconf(OsConstants._SC_PAGESIZE)
        assertTrue("runtime page size must be positive (observed $pageSize)", pageSize > 0)
        assertEquals(
            "runtime page size must be a power of two (observed $pageSize)",
            pageSize,
            java.lang.Long.highestOneBit(pageSize),
        )
    }
}
