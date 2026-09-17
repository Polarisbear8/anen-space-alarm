package com.anen.spacealarm.amap

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真实高德地点页 fixture 测试。
 *
 * fixture 是从真实 URL 抓取的服务端 HTML（Chrome 桌面 UA / Android Chrome UA，2026-09）：
 *   - B00140C1IU.html        → https://www.amap.com/place/B00140C1IU
 *   - BV10013862.html        → https://www.amap.com/place/BV10013862
 *   - B00140C1IU_mobile.html → https://m.amap.com/place/B00140C1IU
 *
 * 实测结论：这些页面由 JavaScript 渲染，服务端 HTML 中**不含坐标**。
 * 本测试有两个作用：
 *   1) 记录这个事实，避免误以为"抓到 HTML 就一定能解析出坐标"；
 *   2) 防止正则误匹配：真实页面里有大量 JS/CSS 数字，绝不允许凭空猜出一个坐标。
 *
 * 如果高德某天开始在服务端渲染坐标，这里会失败 —— 那时应同步更新解析策略与预期。
 */
class AMapRealPageFixtureTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("amap/$name")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    @Test
    fun `real amap place pages contain no coordinates in server html`() {
        listOf("B00140C1IU.html", "BV10013862.html", "B00140C1IU_mobile.html").forEach { name ->
            val html = fixture(name)
            assertTrue("$name 应为真实页面内容", html.length > 10_000)
            assertNull("$name 是 JS 渲染页面，不应解析出坐标", AMapHtmlResolver.extractCoordinates(html))
            assertNull(
                "$name 不应产出 Place（宁可失败，也不能给出错误坐标）",
                AMapHtmlResolver.extract(html, "https://www.amap.com/place/B00140C1IU")
            )
        }
    }
}
