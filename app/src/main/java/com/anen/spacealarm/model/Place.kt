package com.anen.spacealarm.model

import com.anen.spacealarm.coordinate.CoordinateConverter

/**
 * 一个地点。同时保留原始坐标/原始坐标系与内部统一的 WGS84 坐标。
 */
data class Place(
    val name: String,
    val address: String?,
    val originalLatitude: Double,
    val originalLongitude: Double,
    val coordinateSystem: CoordinateSystem,
    val latitude: Double,
    val longitude: Double,
    val source: PlaceSource
) {
    companion object {
        fun of(
            name: String,
            address: String?,
            latitude: Double,
            longitude: Double,
            coordinateSystem: CoordinateSystem,
            source: PlaceSource
        ): Place {
            val wgs = CoordinateConverter.toWgs84(latitude, longitude, coordinateSystem)
            return Place(
                name = name,
                address = address,
                originalLatitude = latitude,
                originalLongitude = longitude,
                coordinateSystem = coordinateSystem,
                latitude = wgs[0],
                longitude = wgs[1],
                source = source
            )
        }
    }
}
