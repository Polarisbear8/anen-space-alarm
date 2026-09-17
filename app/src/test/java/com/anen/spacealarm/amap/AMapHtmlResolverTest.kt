package com.anen.spacealarm.amap

import com.anen.spacealarm.model.CoordinateSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AMapHtmlResolverTest {

    @Test
    fun `extracts longitude latitude pair`() {
        val html = """<script>var poi = {"name":"广州南站","longitude":"113.268932","latitude":"23.081058"};</script>"""
        val location = AMapHtmlResolver.extract(html, "https://www.amap.com/place/B00140C1IU")
        assertNotNull(location)
        assertEquals(113.268932, location!!.longitude, 1e-6)
        assertEquals(23.081058, location.latitude, 1e-6)
        assertEquals(CoordinateSystem.GCJ02, location.coordinateSystem)
        assertEquals("广州南站", location.name)
    }

    @Test
    fun `extracts reversed latitude longitude order`() {
        val html = """{"latitude":23.081058,"longitude":113.268932}"""
        val location = AMapHtmlResolver.extract(html, "https://www.amap.com/place/B00140C1IU")
        assertEquals(113.268932, location!!.longitude, 1e-6)
        assertEquals(23.081058, location.latitude, 1e-6)
    }

    @Test
    fun `extracts location string pair`() {
        val html = """{"data":{"poi":{"location":"113.268932,23.081058","address":"工业大道中253号"}}}"""
        val location = AMapHtmlResolver.extract(html, "https://www.amap.com/place/B00140C1IU")
        assertEquals(113.268932, location!!.longitude, 1e-6)
        assertEquals("工业大道中253号", location.address)
    }

    @Test
    fun `extracts name from title and strips amap suffix`() {
        val html = "<html><head><title>南方医科大学珠江医院-高德地图</title></head><body>no coordinates here</body></html>"
        assertEquals("南方医科大学珠江医院", AMapHtmlResolver.extractName(html))
    }

    @Test
    fun `returns null when html has no coordinates`() {
        val html = "<html><style>.map{position:absolute;left:10px}</style><body>hello</body></html>"
        assertNull(AMapHtmlResolver.extractCoordinates(html))
        assertNull(AMapHtmlResolver.extract(html, "https://www.amap.com/place/B00140C1IU"))
    }

    @Test
    fun `manual parser accepts both orders and respects system`() {
        val lngFirst = ManualPlaceParser.parse("测试点", "113.268932,23.081058", CoordinateSystem.GCJ02)
        assertEquals(113.268932, lngFirst!!.originalLongitude, 1e-6)
        assertEquals(CoordinateSystem.GCJ02, lngFirst.coordinateSystem)

        val latFirst = ManualPlaceParser.parse(null, "23.081058 113.268932", CoordinateSystem.WGS84)
        assertEquals(113.268932, latFirst!!.originalLongitude, 1e-6)
        assertEquals(23.081058, latFirst.originalLatitude, 1e-6)
        assertEquals(23.081058, latFirst.latitude, 1e-12)

        assertNull(ManualPlaceParser.parse(null, "这不是坐标", CoordinateSystem.GCJ02))
    }

    @Test
    fun `url query tools find urls in chinese text`() {
        val text = "广州南站，地址：番禺区。https://uri.amap.com/marker?position=113.268932,23.081058&name=广州南站。"
        val urls = UrlQuery.findUrls(text)
        assertEquals(1, urls.size)
        assertTrue(urls.first().startsWith("https://uri.amap.com/marker?"))
    }

    @Test
    fun `extracts rendered chinese coordinate text`() {
        // 高德页面渲染后会显示「地理坐标：23.081058,113.268932」，顺序是 纬度,经度
        val rendered = "南方医科大学珠江医院\n工业大道中253号\n地理坐标：23.081058,113.268932\n查看详情"
        val location = AMapHtmlResolver.extractFromText(rendered)
        assertNotNull(location)
        assertEquals(23.081058, location!!.latitude, 1e-6)
        assertEquals(113.268932, location.longitude, 1e-6)
    }

    @Test
    fun `extracts labeled latitude longitude text`() {
        val rendered = "纬度：23.081058\n经度：113.268932"
        val location = AMapHtmlResolver.extractFromText(rendered)
        assertNotNull(location)
        assertEquals(113.268932, location!!.longitude, 1e-6)
        assertEquals(23.081058, location.latitude, 1e-6)
    }
}
