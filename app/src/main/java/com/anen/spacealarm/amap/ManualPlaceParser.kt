package com.anen.spacealarm.amap

import com.anen.spacealarm.model.CoordinateSystem
import com.anen.spacealarm.model.Place
import com.anen.spacealarm.model.PlaceSource

/**
 * 备用输入：解析失败时允许用户直接粘贴坐标（解析顺序中的第 ⑧ 步）。
 */
object ManualPlaceParser {

    private val pairRegex = Regex("""(-?\d{1,3}\.\d+)\s*[,，\s]\s*(-?\d{1,3}\.\d+)""")

    fun parse(name: String?, text: String, system: CoordinateSystem): Place? {
        val match = pairRegex.find(text.trim()) ?: return null
        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val b = match.groupValues[2].toDoubleOrNull() ?: return null
        val lngLat = UrlQuery.normalizeLngLat(a, b) ?: return null
        return Place.of(
            name = name?.takeIf { it.isNotBlank() } ?: "手动输入地点",
            address = null,
            latitude = lngLat.latitude,
            longitude = lngLat.longitude,
            coordinateSystem = system,
            source = PlaceSource.MANUAL
        )
    }
}
