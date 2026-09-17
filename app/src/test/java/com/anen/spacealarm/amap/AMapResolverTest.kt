package com.anen.spacealarm.amap

import com.anen.spacealarm.model.CoordinateSystem
import com.anen.spacealarm.share.ShareContent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只测试本地解析路径（不联网）。
 */
class AMapResolverTest {

    private val resolver = AMapResolver(OkHttpClient())

    private fun share(text: String) = ShareContent(
        action = "android.intent.action.SEND",
        mimeType = "text/plain",
        text = text,
        title = null,
        htmlText = null,
        dataUri = null,
        clipTexts = emptyList()
    )

    @Test
    fun `resolves uri amap marker with position`() = runTest {
        val result = resolver.resolve(share("https://uri.amap.com/marker?position=113.268932,23.081058&name=广州南站"))
        val place = (result as ResolveResult.Success).place
        assertEquals(113.268932, place.originalLongitude, 1e-6)
        assertEquals(23.081058, place.originalLatitude, 1e-6)
        assertEquals(CoordinateSystem.GCJ02, place.coordinateSystem)
        assertEquals("广州南站", place.name)
    }

    @Test
    fun `resolves amapuri route plan destination`() = runTest {
        val url = "amapuri://route/plan/?sid=&slat=23.10&slon=113.32&sname=我的位置" +
            "&did=&dlat=23.081058&dlon=113.268932&dname=南方医科大学珠江医院&dev=0&t=0"
        val result = resolver.resolve(share(url))
        val place = (result as ResolveResult.Success).place
        assertEquals("南方医科大学珠江医院", place.name)
        assertEquals(23.081058, place.originalLatitude, 1e-6)
        assertEquals(113.268932, place.originalLongitude, 1e-6)
    }

    @Test
    fun `resolves androidamap view map`() = runTest {
        val url = "androidamap://viewMap?sourceApplication=test&poiname=北山(公交站)&lat=23.072&lon=113.271&dev=0"
        val result = resolver.resolve(share(url))
        val place = (result as ResolveResult.Success).place
        assertEquals("北山(公交站)", place.name)
        assertEquals(23.072, place.originalLatitude, 1e-6)
    }

    @Test
    fun `resolves place url with inline coordinates`() = runTest {
        val result = resolver.resolve(share("https://www.amap.com/place/B00140C1IU?position=113.268932,23.081058"))
        val place = (result as ResolveResult.Success).place
        assertEquals(113.268932, place.originalLongitude, 1e-6)
        assertEquals(23.081058, place.originalLatitude, 1e-6)
    }

    @Test
    fun `accepts latitude longitude order in share text`() = runTest {
        val result = resolver.resolve(share("高德地图 23.081058,113.268932"))
        val place = (result as ResolveResult.Success).place
        assertEquals(113.268932, place.originalLongitude, 1e-6)
        assertEquals(23.081058, place.originalLatitude, 1e-6)
    }

    @Test
    fun `extracts url from amap share text`() = runTest {
        val text = "南方医科大学珠江医院(工业大道中253号)，https://uri.amap.com/marker?position=113.267724,23.079347&name=南方医科大学珠江医院"
        val result = resolver.resolve(share(text))
        val place = (result as ResolveResult.Success).place
        assertEquals(113.267724, place.originalLongitude, 1e-6)
    }

    @Test
    fun `fails gracefully without any usable link`() = runTest {
        val result = resolver.resolve(share("今天天气不错"))
        assertTrue(result is ResolveResult.Failure)
    }

    @Test
    fun `empty share content fails`() = runTest {
        val result = resolver.resolve(
            ShareContent(null, null, null, null, null, null, emptyList())
        )
        assertTrue(result is ResolveResult.Failure)
    }

    @Test
    fun `title and clip texts are also used as candidates`() = runTest {
        val fromTitle = resolver.resolve(
            ShareContent(
                action = "android.intent.action.SEND",
                mimeType = "text/plain",
                text = null,
                title = "https://uri.amap.com/marker?position=113.268932,23.081058&name=标题来源",
                htmlText = null,
                dataUri = null,
                clipTexts = emptyList()
            )
        )
        val titlePlace = (fromTitle as ResolveResult.Success).place
        assertEquals("标题来源", titlePlace.name)
        assertEquals(113.268932, titlePlace.originalLongitude, 1e-6)

        val fromClip = resolver.resolve(
            ShareContent(
                action = "android.intent.action.SEND",
                mimeType = "text/plain",
                text = "分享自高德地图",
                title = null,
                htmlText = null,
                dataUri = null,
                clipTexts = listOf("北山(公交站)，https://uri.amap.com/marker?position=113.271500,23.072800&name=北山(公交站)")
            )
        )
        val clipPlace = (fromClip as ResolveResult.Success).place
        assertEquals("北山(公交站)", clipPlace.name)
    }

    @Test
    fun `regression - zhujiang hospital share resolves locally`() = runTest {
        val text = "南方医科大学珠江医院(工业大道中253号)，广东省广州市海珠区工业大道中253号，" +
            "https://uri.amap.com/marker?position=113.267724,23.079347&name=南方医科大学珠江医院"
        val result = resolver.resolve(share(text))
        val place = (result as ResolveResult.Success).place
        assertEquals("南方医科大学珠江医院", place.name)
        assertEquals(113.267724, place.originalLongitude, 1e-6)
        assertEquals(23.079347, place.originalLatitude, 1e-6)
        assertEquals(CoordinateSystem.GCJ02, place.coordinateSystem)
    }

    @Test
    fun `regression - beishan bus stop share resolves locally`() = runTest {
        val text = "北山(公交站)，广东省广州市海珠区，https://uri.amap.com/marker?position=113.271500,23.072800&name=北山(公交站)"
        val result = resolver.resolve(share(text))
        val place = (result as ResolveResult.Success).place
        assertEquals("北山(公交站)", place.name)
        assertEquals(113.271500, place.originalLongitude, 1e-6)
        assertEquals(23.072800, place.originalLatitude, 1e-6)
    }

    @Test
    fun `place url without inline coordinates fails gracefully`() = runTest {
        // trustedHosts 为空 → 不发起任何联网请求，只验证本地阶段不会误报坐标
        val localOnly = AMapResolver(OkHttpClient(), trustedHosts = emptyList())
        val result = localOnly.resolve(share("https://www.amap.com/place/B00140C1IU"))
        assertTrue(result is ResolveResult.Failure)
    }

    @Test
    fun `place id is recognised from amap place urls`() {
        assertEquals("B00140C1IU", AMapPlaceUrlResolver.matchPlaceId("https://www.amap.com/place/B00140C1IU"))
        assertEquals("BV10013862", AMapPlaceUrlResolver.matchPlaceId("https://www.amap.com/place/BV10013862"))
        assertNull(AMapPlaceUrlResolver.matchPlaceId("https://example.com/place/B00140C1IU"))
    }

    @Test
    fun `host whitelist only allows the required amap hosts`() {
        assertTrue(AMapHosts.isTrusted("https://www.amap.com/place/B00140C1IU"))
        assertTrue(AMapHosts.isTrusted("https://amap.com/place/B00140C1IU"))
        assertTrue(AMapHosts.isTrusted("https://uri.amap.com/marker?position=113.2,23.0"))
        assertTrue(AMapHosts.isTrusted("https://surl.amap.com/abcdef"))
        assertTrue(AMapHosts.isTrusted("https://m.amap.com/navi/?dest=113.2,23.0"))

        assertFalse("example.com 不应被信任", AMapHosts.isTrusted("https://example.com/amap.com"))
        assertFalse("v1 白名单不含 gaode.com", AMapHosts.isTrusted("https://www.gaode.com/x"))
        assertFalse("v1 白名单不含 ditu 子域", AMapHosts.isTrusted("https://ditu.amap.com/place/B00140C1IU"))
    }
}
