package com.anen.spacealarm.amap

import com.anen.spacealarm.model.CoordinateSystem
import com.anen.spacealarm.model.PlaceSource

/**
 * 解析高德分享短链跳转链里的 `p` 参数（Level 2A）。
 *
 * 真实链路：
 *   https://surl.amap.com/xxxx
 *     → 302 https://wb.amap.com/?p=POI_ID,latitude,longitude,name,...
 *     → 302 http://m.amap.com/callAPP?...&android=androidamap?action=shorturl&p=...
 *
 * 因此这里既检查顶层 `p`，也扫描其它参数值里嵌套的 `p=`（跳转链会二次编码）。
 */
internal object AMapPoiParamResolver {

    private val nestedPoiParam = Regex("""[?&]p=([^&]+)""")

    fun parse(url: String): AMapLocation? {
        val params = UrlQuery.parseQuery(url)
        params["p"]?.let { value -> parsePoiValue(value, url)?.let { return it } }

        for (value in params.values) {
            val match = nestedPoiParam.find(value) ?: continue
            parsePoiValue(UrlQuery.decode(match.groupValues[1]), url)?.let { return it }
        }
        return null
    }

    /** 值格式：POI_ID,latitude,longitude,name,其它字段… */
    private fun parsePoiValue(value: String, url: String): AMapLocation? {
        val parts = value.split(',')
        if (parts.size < 4) return null

        val poiId = parts[0].trim()
        val latitude = parts[1].trim().toDoubleOrNull() ?: return null
        val longitude = parts[2].trim().toDoubleOrNull() ?: return null
        val name = parts[3].trim()

        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

        return AMapLocation(
            name = name.ifBlank { null },
            address = null,
            latitude = latitude,
            longitude = longitude,
            coordinateSystem = CoordinateSystem.GCJ02,
            source = PlaceSource.AMAP_SHORT_URL,
            poiId = poiId.ifBlank { null },
            via = "amap-short-url:${UrlQuery.host(url) ?: "unknown"}"
        )
    }
}
