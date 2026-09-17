package com.anen.spacealarm.map

/**
 * 地图样式管理。样式文件在 assets/anen_style.json，
 * 瓦片来自 OSM 派生矢量瓦片服务，可替换为自托管服务。
 */
object MapStyleManager {

    const val STYLE_URI = "asset://anen_style.json"
    const val ATTRIBUTION = "地图数据 © OpenStreetMap contributors"
    const val TILE_SOURCE_URL = "https://tiles.openfreemap.org/planet"

    /** 开发者模式展示用：只显示数据源主机，不暴露完整 URL。 */
    fun tileSourceHost(): String = TILE_SOURCE_URL
        .removePrefix("https://")
        .substringBefore('/')
}
