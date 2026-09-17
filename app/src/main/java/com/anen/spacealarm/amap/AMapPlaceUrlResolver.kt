package com.anen.spacealarm.amap

import com.anen.spacealarm.model.PlaceSource

/**
 * 高德地点页 URL：https://www.amap.com/place/{POI_ID}
 * 若 URL 自带坐标则直接使用；否则只提取 POI ID，交给联网解析。
 */
internal object AMapPlaceUrlResolver {

    private val PLACE_HOSTS = setOf("www.amap.com", "amap.com", "ditu.amap.com", "m.amap.com", "m.ditu.amap.com")
    private val placeIdRegex = Regex("""/place/([A-Za-z0-9_\-]{4,})""")

    fun matchPlaceId(url: String): String? {
        val host = UrlQuery.host(url) ?: return null
        if (host !in PLACE_HOSTS) return null
        return placeIdRegex.find(UrlQuery.path(url))?.groupValues?.get(1)
    }

    /** 地点页 URL 上直接带坐标的情况（例如 ?position=lng,lat）。 */
    fun resolveInline(url: String, via: String): AMapLocation? {
        val poiId = matchPlaceId(url) ?: return null
        val params = UrlQuery.parseQuery(url)
        val coordinate = AMapUriResolver.coordinatesFromParams(params) ?: return null
        return AMapLocation(
            name = AMapUriResolver.nameFromParams(params),
            address = params["address"] ?: params["addr"],
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            coordinateSystem = AMapUriResolver.systemFromParams(params),
            source = PlaceSource.AMAP_PLACE_PAGE,
            poiId = poiId,
            via = via
        )
    }
}
