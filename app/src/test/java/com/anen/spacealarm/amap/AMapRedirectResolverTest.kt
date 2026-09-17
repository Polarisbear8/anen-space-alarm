package com.anen.spacealarm.amap

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

/**
 * 重定向解析：跟随高德短链 + 限制跳转到受信任域名。
 */
class AMapRedirectResolverTest {

    private lateinit var server: MockWebServer

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    // MockWebServer 的 url() 主机名依平台而定（Windows 上是 127.0.0.1，Linux 上是
    // localhost），受信任列表必须取自它，否则测试会在 CI 上误报。
    private lateinit var trusted: List<String>

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        trusted = listOf(server.url("/").host)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `follows redirect and extracts place from final html`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(302)
                .setHeader("Location", server.url("/place/B00140C1IU").toString())
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                <html><head><title>南方医科大学珠江医院-高德地图</title></head>
                <body><script>var poi = {"longitude":"113.267724","latitude":"23.079347","address":"工业大道中253号"};</script></body></html>
                """.trimIndent()
            )
        )

        val chain = AMapRedirectResolver(client, trusted).follow(server.url("/s/abc").toString())

        assertNull(chain.error)
        assertEquals(2, chain.hops.size)
        val location = AMapHtmlResolver.extract(chain.finalBody!!, chain.finalUrl)
        assertEquals("南方医科大学珠江医院", location!!.name)
        assertEquals(113.267724, location.longitude, 1e-6)
        assertEquals(23.079347, location.latitude, 1e-6)
        assertEquals("工业大道中253号", location.address)
    }

    @Test
    fun `stops when redirect leaves trusted hosts`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(302).setHeader("Location", "https://example.com/evil")
        )

        val chain = AMapRedirectResolver(client, trusted).follow(server.url("/s/abc").toString())

        assertEquals(1, chain.hops.size)
        assertNull(chain.finalBody)
        assertTrue("应报告不受信任的重定向", chain.error!!.contains("不受信任"))
        assertEquals("不得继续请求第三方域名", 1, server.requestCount)
    }

    @Test
    fun `rejects untrusted initial url`() = runTest {
        val chain = AMapRedirectResolver(client, trusted).follow("https://example.com/place/B00140C1IU")

        assertTrue(chain.error!!.contains("不受信任"))
        assertEquals(0, server.requestCount)
    }
}
