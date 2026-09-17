package com.anen.spacealarm.amap

import com.anen.spacealarm.model.CoordinateSystem
import com.anen.spacealarm.model.PlaceSource

/**
 * 从高德地点页 HTML / 结构化数据中提取坐标、名称、地址。
 * 高德页面为 JS 渲染，因此这里做多模式匹配，任一命中即可。
 */
internal object AMapHtmlResolver {

    private val lngLatPatterns = listOf(
        Regex(""""longitude"\s*:\s*"?(-?\d{1,3}\.\d+)"?\s*,\s*"latitude"\s*:\s*"?(-?\d{1,3}\.\d+)"?"""),
        Regex(""""lng"\s*:\s*"?(-?\d{1,3}\.\d+)"?\s*,\s*"lat"\s*:\s*"?(-?\d{1,3}\.\d+)"?"""),
        Regex(""""location"\s*:\s*"(-?\d{1,3}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)"""")
    )

    private val latLngPatterns = listOf(
        Regex(""""latitude"\s*:\s*"?(-?\d{1,3}\.\d+)"?\s*,\s*"longitude"\s*:\s*"?(-?\d{1,3}\.\d+)"?"""),
        Regex(""""lat"\s*:\s*"?(-?\d{1,3}\.\d+)"?\s*,\s*"lng"\s*:\s*"?(-?\d{1,3}\.\d+)"?""")
    )

    private val urlParamPattern =
        Regex("""[?&](?:position|dest|location|center)=(-?\d{1,3}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)""")

    private val titlePattern = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val namePattern = Regex(""""(?:poiname|dname|name)"\s*:\s*"([^"\\]{1,80})"""")
    private val addressPattern = Regex(""""(?:address|addr)"\s*:\s*"([^"\\]{1,120})"""")

    /** 高德页面渲染后会显示「地理坐标：23.081058,113.268932」，注意顺序是 纬度,经度。 */
    private val geoCoordinateTextPattern = Regex(
        """地理坐标\s*[：:]\s*(-?\d{1,3}(?:\.\d+)?)\s*[,，]\s*(-?\d{1,3}(?:\.\d+)?)"""
    )
    private val latitudeLabelPattern = Regex("""纬度\s*[：:]?\s*(-?\d{1,3}\.\d+)""")
    private val longitudeLabelPattern = Regex("""经度\s*[：:]?\s*(-?\d{1,3}\.\d+)""")

    /**
     * 从任意文本中提取坐标：HTML / JSON / WebView 渲染后的 innerText 都走这里。
     * 顺序：带标签的中文文本 → 结构化字段 → URL 参数。
     */
    fun extractFromText(text: String?): LngLat? {
        if (text.isNullOrBlank()) return null

        geoCoordinateTextPattern.find(text)?.let { match ->
            val latitude = match.groupValues[1].toDoubleOrNull()
            val longitude = match.groupValues[2].toDoubleOrNull()
            if (latitude != null && longitude != null &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
            ) {
                return LngLat(longitude, latitude)
            }
        }

        val labeledLatitude = latitudeLabelPattern.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val labeledLongitude = longitudeLabelPattern.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        if (labeledLatitude != null && labeledLongitude != null &&
            labeledLatitude in -90.0..90.0 && labeledLongitude in -180.0..180.0
        ) {
            return LngLat(labeledLongitude, labeledLatitude)
        }

        return extractCoordinates(text)
    }

    fun extract(html: String, baseUrl: String): AMapLocation? {
        val coordinate = extractFromText(html) ?: return null
        return AMapLocation(
            name = extractName(html),
            address = extractAddress(html),
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            coordinateSystem = CoordinateSystem.GCJ02,
            source = PlaceSource.AMAP_PLACE_PAGE,
            poiId = null,
            via = "html"
        )
    }

    fun extractCoordinates(html: String?): LngLat? {
        if (html.isNullOrBlank()) return null
        for (pattern in lngLatPatterns) {
            matchPair(html, pattern, lngFirst = true)?.let { return it }
        }
        for (pattern in latLngPatterns) {
            matchPair(html, pattern, lngFirst = false)?.let { return it }
        }
        matchPair(html, urlParamPattern, lngFirst = true)?.let { return it }
        return null
    }

    fun extractName(html: String?): String? {
        if (html.isNullOrBlank()) return null
        titlePattern.find(html)?.groupValues?.get(1)?.let { raw ->
            val cleaned = cleanTitle(raw)
            if (cleaned.isNotBlank() && cleaned.length <= 60) return cleaned
        }
        namePattern.find(html)?.groupValues?.get(1)?.let { raw ->
            val cleaned = decodeEntities(raw).trim()
            if (cleaned.isNotBlank()) return cleaned
        }
        return null
    }

    fun extractAddress(html: String?): String? {
        if (html.isNullOrBlank()) return null
        val raw = addressPattern.find(html)?.groupValues?.get(1) ?: return null
        return decodeEntities(raw).trim().takeIf { it.isNotBlank() }
    }

    private fun matchPair(html: String, pattern: Regex, lngFirst: Boolean): LngLat? {
        val match = pattern.find(html) ?: return null
        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val b = match.groupValues[2].toDoubleOrNull() ?: return null
        return if (lngFirst) UrlQuery.normalizeLngLat(a, b) else UrlQuery.normalizeLngLat(b, a)
    }

    fun cleanTitle(raw: String): String {
        val decoded = decodeEntities(raw).trim()
        val parts = decoded.split('|', '-', '_', '—', '·')
        val preferred = parts.firstOrNull { it.isNotBlank() && !it.contains("高德") }
        return (preferred ?: decoded).trim()
    }

    private fun decodeEntities(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}
