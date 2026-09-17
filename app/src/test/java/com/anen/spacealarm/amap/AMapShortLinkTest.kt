package com.anen.spacealarm.amap

import com.anen.spacealarm.model.PlaceSource
import com.anen.spacealarm.share.ShareContent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Level 2A：高德分享短链 → wb.amap.com → p 参数。
 *
 * 数据来自真实分享（官洲地铁站）：
 *   https://surl.amap.com/fEYp1E2nd6r
 *   → 302 https://wb.amap.com/?p=BV10024363,23.06776249682682,113.37696030735967,官洲(地铁站),12号线;4号线
 */
class AMapShortLinkTest {

    private val wbUrl = "https://wb.amap.com/?commonBizInfo=%7B%22share_from%22%3A%22poi_poi%22%7D" +
        "&p=BV10024363%2C23.06776249682682%2C113.37696030735967%2C%E5%AE%98%E6%B4%B2%28%E5%9C%B0%E9%93%81%E7%AB%99%29%2C12%E5%8F%B7%E7%BA%BF%3B4%E5%8F%B7%E7%BA%BF" +
        "&src=app_C021100036089"

    @Test
    fun `parses p parameter from wb amap url`() {
        val location = AMapPoiParamResolver.parse(wbUrl)
        assertNotNull(location)
        assertEquals("BV10024363", location!!.poiId)
        assertEquals(23.06776249682682, location.latitude, 1e-9)
        assertEquals(113.37696030735967, location.longitude, 1e-9)
        assertEquals("官洲(地铁站)", location.name)
        assertEquals(PlaceSource.AMAP_SHORT_URL, location.source)
    }

    @Test
    fun `parses nested p parameter from call app url`() {
        val callAppUrl = "https://m.amap.com/callAPP?ios=multiPointShow&android=androidamap%3Faction%3Dshorturl" +
            "%26p%3DBV10024363%2C23.06776249682682%2C113.37696030735967%2C%E5%AE%98%E6%B4%B2%28%E5%9C%B0%E9%93%81%E7%AB%99%29" +
            "&mo=http%3A%2F%2Fm.amap.com%2F%3Fp%3DBV10024363%2C23.06776249682682%2C113.37696030735967%2C%E5%AE%98%E6%B4%B2%28%E5%9C%B0%E9%93%81%E7%AB%99%29"
        val location = AMapPoiParamResolver.parse(callAppUrl)
        assertNotNull(location)
        assertEquals("官洲(地铁站)", location!!.name)
    }

    @Test
    fun `ignores p parameter that is not a coordinate`() {
        assertEquals(null, AMapPoiParamResolver.parse("https://wb.amap.com/?p=not-a-poi"))
    }

    @Test
    fun `parses all four real share payloads`() {
        // 来自真实高德分享（复制出来的短链跳转 p 参数）
        val payloads = listOf(
            "BV10024363,23.06776249682682,113.37696030735967,官洲(地铁站),12号线;4号线" to "官洲(地铁站)",
            "B0LU45X0H5,23.056289460227404,113.38432967662808,创信园游泳馆,小谷围街中环西路7号(大学城北地铁站F口步行300米)" to "创信园游泳馆",
            "B0HR4H08LO,23.160102158813395,113.44678223133086,广东省国土资源测绘院,光谱中路13号" to "广东省国土资源测绘院",
            "B00140WBI1,23.10647954590175,113.32448959350585,广州塔,阅江西路222号" to "广州塔"
        )
        payloads.forEach { (payload, expectedName) ->
            val location = AMapPoiParamResolver.parse(
                "https://wb.amap.com/?p=" + java.net.URLEncoder.encode(payload, "UTF-8")
            )
            assertNotNull("payload 应可解析：$expectedName", location)
            assertEquals(expectedName, location!!.name)
            assertTrue(location.latitude in 20.0..25.0)
            assertTrue(location.longitude in 112.0..115.0)
        }
    }

    @Test
    fun `extracts short link from multi-line paste`() {
        // 高德“复制”得到的多行文本：名称 / 线路 / 短链
        val text = "官洲(地铁站)地铁站\n12号线;4号线\nhttps://surl.amap.com/gS24Bk2ddZ6"
        val urls = UrlQuery.findUrls(text)
        assertEquals(1, urls.size)
        assertEquals("https://surl.amap.com/gS24Bk2ddZ6", urls.first())
        assertTrue(AMapHosts.isTrusted(urls.first()))
    }

    @Test
    fun `short link resolves at level 2A without a second request or webview`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(302).setHeader("Location", wbUrl)
            )
            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
            // wb.amap.com 只在跳转链里被解析，不会被真正请求，因此把它加进白名单即可
            val resolver = AMapResolver(
                client,
                webViewResolver = null,
                trustedHosts = listOf("127.0.0.1", "wb.amap.com")
            )

            val result = resolver.resolve(
                ShareContent(
                    action = "android.intent.action.SEND",
                    mimeType = "text/plain",
                    text = "官洲(地铁站)\n${server.url("/fEYp1E2nd6r")}",
                    title = null,
                    htmlText = null,
                    dataUri = null,
                    clipTexts = emptyList()
                )
            )

            val place = (result as ResolveResult.Success).place
            assertEquals("官洲(地铁站)", place.name)
            assertEquals(23.06776249682682, place.originalLatitude, 1e-9)
            assertEquals(113.37696030735967, place.originalLongitude, 1e-9)
            assertEquals(PlaceSource.AMAP_SHORT_URL, place.source)
            assertEquals("只在短链上发过一次请求（L2A 不需要再请求 wb.amap.com）", 1, server.requestCount)
            assertTrue(place.longitude > 113.0 && place.latitude > 23.0)
        } finally {
            server.shutdown()
        }
    }
}
