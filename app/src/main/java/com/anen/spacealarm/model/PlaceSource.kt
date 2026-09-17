package com.anen.spacealarm.model

/**
 * 地点来源，用于记录坐标是怎么拿到的，避免对坐标做未确认的二次转换。
 */
enum class PlaceSource {
    AMAP_SHARE_TEXT,
    AMAP_URI,
    AMAP_WEB_URL,
    AMAP_SHORT_URL,
    AMAP_PLACE_PAGE,
    MANUAL
}
