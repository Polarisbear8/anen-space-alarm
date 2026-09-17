package com.anen.spacealarm.amap

import com.anen.spacealarm.share.ShareContent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 联网解析链路的端到端测试（MockWebServer，不访问真实高德）：
 * 分享链接 → 重定向 → 公开地点页 HTML → Place。
 *
 * 用 127.0.0.1 作为受信任主机注入，验证的是链路本身，而不是白名单内容
 * （白名单另有专门测试）。
 */
class AMapResolverNetworkTest {

    private lateinit var server: MockWebServer

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `resolves place page after redirect and html extraction`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(302)
                .setHeader("Location", server.url("/place/B00140C1IU").toString())
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(PLACE_PAGE_HTML))

        val resolver = AMapResolver(client, trustedHosts = listOf("127.0.0.1"))
        val result = resolver.resolve(shareText(server.url("/s/abc").toString()))

        assertTrue(
            "解析失败：${(result as? ResolveResult.Failure)?.reason}",
            result is ResolveResult.Success
        )
        val place = (result as ResolveResult.Success).place
        assertEquals("南方医科大学珠江医院", place.name)
        assertEquals(113.267724, place.originalLongitude, 1e-6)
        assertEquals(23.079347, place.originalLatitude, 1e-6)
        assertEquals("工业大道中253号", place.address)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `untrusted share link is never fetched`() = runTest {
        val resolver = AMapResolver(client, trustedHosts = listOf("127.0.0.1"))
        val result = resolver.resolve(shareText("https://example.com/place/B00140C1IU"))

        assertEquals(0, server.requestCount)
        assertEquals(true, result is ResolveResult.Failure)
    }

    private fun shareText(text: String) = ShareContent(
        action = "android.intent.action.SEND",
        mimeType = "text/plain",
        text = text,
        title = null,
        htmlText = null,
        dataUri = null,
        clipTexts = emptyList()
    )

    private companion object {
        const val PLACE_PAGE_HTML = """
            <html><head><title>南方医科大学珠江医院-高德地图</title></head>
            <body>
            <script>
            window.__INITIAL_STATE__ = {"poi":{"name":"南方医科大学珠江医院","location":"113.267724,23.079347","address":"工业大道中253号"}};
            </script>
            </body></html>
        """
    }
}
