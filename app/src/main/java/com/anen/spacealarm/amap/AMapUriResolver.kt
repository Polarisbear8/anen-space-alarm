package com.anen.spacealarm.amap

import com.anen.spacealarm.model.CoordinateSystem
import com.anen.spacealarm.model.PlaceSource

/**
 * 解析高德 URI / Web URI：
 *   amapuri://, androidamap://, iosamap://, https://uri.amap.com/..., https://m.amap.com/navi/...
 * 这些形式通常直接带坐标（GCJ02），无需联网。
 */
internal object AMapUriResolver {

    private val SCHEMES = setOf("amapuri", "androidamap", "iosamap")
    private val WEB_HOSTS = setOf(
        "uri.amap.com", "m.amap.com", "www.amap.com", "amap.com",
        "ditu.amap.com", "m.ditu.amap.com", "surl.amap.com"
    )

    private val LAT_KEYS = listOf("lat", "latitude", "to_lat", "tolat")
    private val LON_KEYS = listOf("lon", "lng", "longitude", "to_lon", "tolon")
    private val DEST_LAT_KEYS = listOf("dlat", "destlat", "dest_lat")
    private val DEST_LON_KEYS = listOf("dlon", "dlng", "destlon", "dest_lon")
    private val SRC_LAT_KEYS = listOf("slat", "sourcelat")
    private val SRC_LON_KEYS = listOf("slon", "slng", "sourcelon")
    private val PAIR_KEYS = listOf(
        "position", "dest", "destination", "location", "destposition",
        "center", "point", "coordinate", "coordinates", "q", "query"
    )

    fun matches(url: String): Boolean {
        val scheme = url.substringBefore("://", "").lowercase()
        if (scheme in SCHEMES) return true
        return UrlQuery.host(url) in WEB_HOSTS
    }

    fun resolve(url: String, via: String): AMapLocation? {
        if (!matches(url)) return null
        val params = UrlQuery.parseQuery(url)
        val coordinate = coordinatesFromParams(params) ?: return null
        val scheme = url.substringBefore("://", "").lowercase()
        val source = if (scheme in SCHEMES) PlaceSource.AMAP_URI else PlaceSource.AMAP_WEB_URL
        return AMapLocation(
            name = nameFromParams(params),
            address = params["address"] ?: params["addr"] ?: params["daddr"] ?: params["addressname"],
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            coordinateSystem = systemFromParams(params),
            source = source,
            poiId = params["poiid"] ?: params["poi_id"],
            via = via
        )
    }

    /** 从参数表提取坐标，优先级：lat/lon → dlat/dlon → position 类 → slat/slon。 */
    fun coordinatesFromParams(params: Map<String, String>): LngLat? {
        latLonPair(params, LAT_KEYS, LON_KEYS)?.let { return it }
        latLonPair(params, DEST_LAT_KEYS, DEST_LON_KEYS)?.let { return it }
        for (key in PAIR_KEYS) {
            params[key]?.let { value -> UrlQuery.coordinatePair(value)?.let { return it } }
        }
        latLonPair(params, SRC_LAT_KEYS, SRC_LON_KEYS)?.let { return it }
        return null
    }

    fun nameFromParams(params: Map<String, String>): String? {
        val keys = listOf("poiname", "dname", "name", "sname", "title", "poi_name", "desc")
        return keys.firstNotNullOfOrNull { key -> params[key]?.takeIf { it.isNotBlank() } }
    }

    /** coordinate=gaode|wgs84|bd09；默认 GCJ02（高德数据默认偏移坐标系）。 */
    fun systemFromParams(params: Map<String, String>): CoordinateSystem = when (params["coordinate"]?.lowercase()) {
        "wgs84", "gps", "wgs" -> CoordinateSystem.WGS84
        "bd09", "baidu" -> CoordinateSystem.BD09
        else -> CoordinateSystem.GCJ02
    }

    private fun latLonPair(params: Map<String, String>, latKeys: List<String>, lonKeys: List<String>): LngLat? {
        val lat = latKeys.firstNotNullOfOrNull { params[it]?.toDoubleOrNull() } ?: return null
        val lon = lonKeys.firstNotNullOfOrNull { params[it]?.toDoubleOrNull() } ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return LngLat(lon, lat)
    }
}
