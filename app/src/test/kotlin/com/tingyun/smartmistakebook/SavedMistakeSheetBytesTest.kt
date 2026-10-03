package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * D1 的题面字节选取与出网前核对（`REDRAW_PROBLEM` 用它们重绘题面）。
 *
 * 这两条是"模型要的配图"链上唯一由本机决定的东西：选错图，或者把一份与规范记录不一致的
 * 字节当题面发出去，都是真实失败 —— 后者正是拍照会话那条链专门修过的
 * （`RoomCaptureWorkflowRepository.readTutorSessionSheetBytes` 的注释）。这里把同一纪律
 * 钉在错题讲题页上。
 *
 * A3：核对本身已收敛到 `readVerifiedCanonicalAssetBytes`（`vault.resolve` 唯一实现），
 * 本文件只钉"选哪张"与"域资产 → 规范记录"的映射（后者是 vault 核对函数的输入）。
 */
class SavedMistakeSheetBytesTest {
    @Test
    fun cleanRedrawWinsOverTheOriginalPhoto() {
        val clean = asset(role = CLEAN_PROBLEM_SHEET_ROLE, available = true)
        val original = asset(role = "ORIGINAL", available = true)

        val picked = sheetSourceAsset(MistakeSourceSet.Present(listOf(original, clean)))

        assertEquals(clean.sourceAssetId, picked?.sourceAssetId)
    }

    @Test
    fun originalPhotoIsUsedWhenTheCleanRedrawIsMissingHere() {
        val cleanWithoutFile = asset(role = CLEAN_PROBLEM_SHEET_ROLE, available = false)
        val original = asset(role = "ORIGINAL", available = true)

        val picked = sheetSourceAsset(MistakeSourceSet.Present(listOf(cleanWithoutFile, original)))

        assertEquals(original.sourceAssetId, picked?.sourceAssetId)
    }

    @Test
    fun noReadableSourceAssetYieldsNothingToRedraw() {
        assertNull(sheetSourceAsset(MistakeSourceSet.Missing))
        assertNull(
            sheetSourceAsset(
                MistakeSourceSet.Present(listOf(asset(role = CLEAN_PROBLEM_SHEET_ROLE, available = false))),
            ),
        )
    }

    @Test
    fun theSheetFileMapsToACanonicalRecordUnderTheVaultDirectory() {
        // A3：域资产 + 本机文件 → 规范记录（vault.resolve 的输入）。相对路径由"文件相对
        // filesDir"得出；记录里的 sha/字节数原样带上，核对由 vault 的唯一实现做。
        val filesDir = createTempDir(prefix = "files")
        val sheet = File(filesDir, "source-assets/sheet-1.png").apply {
            parentFile?.mkdirs()
            writeBytes("题面字节".toByteArray(Charsets.UTF_8))
        }
        val asset = asset(role = CLEAN_PROBLEM_SHEET_ROLE, available = true)

        val record = canonicalRecordForSheet(asset = asset, sheetFile = sheet, filesDir = filesDir)

        assertEquals(asset.sourceAssetId, record?.sourceAssetId)
        assertEquals("source-assets/sheet-1.png", record?.relativePath)
        assertEquals(asset.contentSha256, record?.contentSha256)
        assertEquals(asset.byteSize, record?.byteSize)
    }

    @Test
    fun aFileOutsideTheVaultDirectoryYieldsNoRecord() {
        // 不在 filesDir 下的路径构造不出合法记录（vault 的父目录核对也会拒绝）——fail-closed。
        val filesDir = createTempDir(prefix = "files")
        val outside = createTempDir(prefix = "outside").let { File(it, "sheet-1.png") }

        assertNull(
            canonicalRecordForSheet(
                asset = asset(role = CLEAN_PROBLEM_SHEET_ROLE, available = true),
                sheetFile = outside,
                filesDir = filesDir,
            ),
        )
    }

    private fun createTempDir(prefix: String): File =
        File.createTempFile(prefix, "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }

    private fun asset(
        role: String,
        available: Boolean,
        bytes: ByteArray = "canonical".toByteArray(Charsets.UTF_8),
    ) = MistakeSourceAsset(
        role = role,
        sourceAssetId = "asset-$role",
        contentSha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) },
        mimeType = "image/png",
        byteSize = bytes.size.toLong(),
        width = 64,
        height = 64,
        sourceType = "CAPTURE",
        createdAtEpochMillis = 1,
        location = if (available) {
            MistakeSourceLocation.Available("file:///data/user/0/app/files/assets/$role.png")
        } else {
            MistakeSourceLocation.Unavailable
        },
    )
}
