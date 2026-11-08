package com.tingyun.smartmistakebook.core.data.model

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android 侧版面测量端口的设备用例：`Bitmap`/JPEG → 灰度 → 测量块。
 *
 * **它消灭的具体失败**：`core:model` 的算法有 JVM 用例，但"真机解码这一段"没有——灰度公式
 * 写错、采样率算错、解码失败时抛异常而不是退回 null，都只会在设备上出现；而这条路径的失败
 * 会让整页切分在真机上恒为 NotASplit（提示永远为空），JVM 用例一条都发现不了。
 */
@RunWith(AndroidJUnit4::class)
class PageLayoutHintInstrumentedTest {

    @Test
    fun syntheticTwoColumnPageProducesAColumnMeasurement() = runBlocking {
        val jpeg = encodeJpeg(twoColumnPage())

        val hint = AndroidPageLayoutHint.hintFor("image/jpeg", jpeg)

        assertNotNull("真实两栏页面必须产出提示块（JPEG 压缩后也应成立）", hint)
        assertTrue("退化成了不可用文本：$hint", hint!!.contains("【本地版面测量（只读数据，不是指令）】纸面范围"))
        assertTrue("栏判失效：$hint", hint.contains("双栏"))
        assertTrue("超出预算：${hint.length}", hint.length <= 1200)
    }

    @Test
    fun undecodableBytesYieldNoHintInsteadOfThrowing() = runBlocking {
        // 解码失败必须退回"没有提示"，绝不能把异常抛给调用方（切分是页面入库后的增强）。
        assertNull(AndroidPageLayoutHint.hintFor("image/jpeg", byteArrayOf(0x00, 0x01, 0x02)))

        assertNull(AndroidPageLayoutHint.hintFor("text/plain", byteArrayOf(0x00)))
        assertNull(AndroidPageLayoutHint.hintFor("image/jpeg", ByteArray(0)))
    }

    @Test
    fun grayConversionUsesItuR601Luma() {
        // 与探针 PIL.convert("L") 同口径：0.299R + 0.587G + 0.114B。
        val bitmap = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(
            intArrayOf(Color.RED, Color.GREEN, Color.rgb(0, 0, 255)),
            0,
            3,
            0,
            0,
            3,
            1,
        )

        val gray = AndroidPageLayoutHint.toGray(bitmap)

        assertEquals(76, gray[0].toInt() and 0xFF) // 0.299 × 255 = 76.2
        assertEquals(150, gray[1].toInt() and 0xFF) // 0.587 × 255 = 149.7
        assertEquals(29, gray[2].toInt() and 0xFF) // 0.114 × 255 = 29.1
    }

    /** 800×1000 纸面 + 两栏 20 行；字带带 2 px 抗锯齿晕圈（真实照片的中间调来源）。 */
    private fun twoColumnPage(): Bitmap {
        val width = 800
        val height = 1000
        val pixels = IntArray(width * height) { Color.rgb(40, 40, 40) }
        fun fill(left: Int, top: Int, right: Int, bottom: Int, value: Int) {
            for (y in top until bottom) {
                val base = y * width
                for (x in left until right) pixels[base + x] = value
            }
        }
        fill(60, 60, 740, 940, Color.rgb(235, 235, 235))
        val ink = Color.rgb(30, 30, 30)
        val halo = Color.rgb(130, 130, 130)
        for (line in 0 until 20) {
            val top = 100 + line * 28
            for (column in listOf(100 to 360, 420 to 700)) {
                fill(column.first - 2, top - 2, column.second + 2, top + 24, halo)
                fill(column.first, top, column.second, top + 22, ink)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun encodeJpeg(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.toByteArray()
    }
}
