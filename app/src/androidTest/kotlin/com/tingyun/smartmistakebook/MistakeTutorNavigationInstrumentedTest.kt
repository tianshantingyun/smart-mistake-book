package com.tingyun.smartmistakebook

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 错题版本三元组在路由里的编解码（L7 前属于导出路由，导出后台化后只剩讲题路由消费）。
 *
 * 迁移说明：改前这条用例钉的是 `Routes.mistakeExport(key)`；单题导出不再带 key 走路由
 * （改为直接给 `startMistakeExportSingle` 的入队请求），同样的"每个身份原样往返、URL 里
 * 不出现裸的特殊字符"断言迁到仍在带 key 导航的讲题路由 + `decodeMistakeKey` 上。
 */
@RunWith(AndroidJUnit4::class)
class MistakeTutorNavigationInstrumentedTest {
    @Test
    fun tutorRouteRoundTripsEveryExactIdentityAsUriArguments() {
        val key = MistakeRevisionKey(
            entryId = "entry/%2F 学生",
            problemId = "problem:函数/一",
            problemRevisionId = "revision?3#确认",
        )

        val route = Routes.mistakeTutor(key)
        val segments = route.split('/')
        val navigationDecodedArguments = segments.drop(2).map(Uri::decode)
        val restored = Routes.decodeMistakeKey(
            entryId = navigationDecodedArguments[0],
            problemId = navigationDecodedArguments[1],
            problemRevisionId = navigationDecodedArguments[2],
        )

        assertEquals(5, segments.size)
        assertEquals(key, restored)
        assertFalse(route.contains(" 学生"))
        assertFalse(route.contains("函数/一"))
        assertFalse(route.contains("revision?3"))
    }
}
