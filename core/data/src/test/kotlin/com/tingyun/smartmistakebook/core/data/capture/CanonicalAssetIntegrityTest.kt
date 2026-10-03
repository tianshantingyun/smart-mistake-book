package com.tingyun.smartmistakebook.core.data.capture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F5（修复轮）：规范资产的**出网前核对**（文件存在 + 字节数 + sha256 逐位一致）在 JVM 面可测。
 *
 * 消灭的失败：saved-sheet 链（A3 后走 `readVerifiedCanonicalAssetBytes` → `vault.resolve`）
 * 的负向断言此前只剩仪器化覆盖；判据抽成纯函数（`canonicalAssetFileIsIntact`，resolve 调用
 * 的唯一实现）后，"sha 不符 / 字节数不符 / 文件缺失"三种不该出网的形态在 JVM 面被钉住。
 */
class CanonicalAssetIntegrityTest {

    @Test
    fun anIntactFilePassesAndEveryMismatchFails() {
        val file = File.createTempFile("canonical-", ".bin").apply {
            writeBytes("题面字节".toByteArray(Charsets.UTF_8))
            deleteOnExit()
        }
        val sha = sha256Of(file)

        assertTrue("完好文件应通过", canonicalAssetFileIsIntact(file, file.length(), sha))
        // sha 不符（内容被替换）：不该出网。
        assertFalse(canonicalAssetFileIsIntact(file, file.length(), "a".repeat(64)))
        // 字节数不符：不该出网。
        assertFalse(canonicalAssetFileIsIntact(file, file.length() + 1, sha))
        // 文件缺失：不该出网。
        val missing = File(file.parentFile, "missing-${file.name}")
        assertFalse(canonicalAssetFileIsIntact(missing, 1, sha))
    }
}
