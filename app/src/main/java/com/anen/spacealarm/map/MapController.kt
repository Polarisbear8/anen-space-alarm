package com.anen.spacealarm.map

import com.anen.spacealarm.location.DistanceCalculator
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import com.google.gson.JsonObject

/**
 * 地图内容控制：当前位置、所有目标点、触发范围圆、到主目标的直线。
 * 只使用 WGS84 坐标（与 Place / Reminder 一致），不在这里做坐标转换。
 */
class MapController(private val map: MapLibreMap) {

    /** 一个目标点（提醒）。enabled = 已启用（橙色 + 虚线）；primary = 最近的启用目标。 */
    data class MapTarget(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float,
        val name: String,
        val enabled: Boolean,
        val primary: Boolean
    )

    data class MapScene(
        val user: LatLng?,
        val targets: List<MapTarget>,
        val primary: MapTarget?,
        val fitCamera: Boolean
    )

    private var styleReady = false
    private var pending: MapScene? = null

    fun onStyleLoaded() {
        styleReady = true
        ensureLayers()
        pending?.let { render(it) }
    }

    fun render(scene: MapScene) {
        if (!styleReady) {
            pending = scene
            return
        }
        val style = map.style ?: return
        ensureLayers()
        updateUser(style, scene.user)
        updateTargets(style, scene.targets)
        updateRadius(style, scene.targets)
        updateLines(style, scene.user, scene.targets)
        if (scene.fitCamera) fitCamera(scene)
    }

    fun recenter(scene: MapScene) {
        fitCamera(scene)
    }

    private fun ensureLayers() {
        val style = map.style ?: return
        addSourceIfMissing(style, SRC_USER)
        addSourceIfMissing(style, SRC_TARGET)
        addSourceIfMissing(style, SRC_RADIUS)
        addSourceIfMissing(style, SRC_LINE)

        if (style.getLayer(LAYER_RADIUS_FILL) == null) {
            style.addLayer(
                FillLayer(LAYER_RADIUS_FILL, SRC_RADIUS)
                    .withFilter(Expression.eq(Expression.get("enabled"), Expression.literal(true)))
                    .withProperties(
                        PropertyFactory.fillColor(ORANGE),
                        PropertyFactory.fillOpacity(0.10f)
                    )
            )
        }
        if (style.getLayer(LAYER_RADIUS_FILL_OFF) == null) {
            style.addLayer(
                FillLayer(LAYER_RADIUS_FILL_OFF, SRC_RADIUS)
                    .withFilter(Expression.eq(Expression.get("enabled"), Expression.literal(false)))
                    .withProperties(
                        PropertyFactory.fillColor(GRAY),
                        PropertyFactory.fillOpacity(0.08f)
                    )
            )
        }
        if (style.getLayer(LAYER_RADIUS_LINE) == null) {
            style.addLayer(
                LineLayer(LAYER_RADIUS_LINE, SRC_RADIUS)
                    .withFilter(Expression.eq(Expression.get("enabled"), Expression.literal(true)))
                    .withProperties(
                        PropertyFactory.lineColor(ORANGE),
                        PropertyFactory.lineOpacity(0.45f),
                        PropertyFactory.lineWidth(1.2f)
                    )
            )
        }
        if (style.getLayer(LAYER_RADIUS_LINE_OFF) == null) {
            style.addLayer(
                LineLayer(LAYER_RADIUS_LINE_OFF, SRC_RADIUS)
                    .withFilter(Expression.eq(Expression.get("enabled"), Expression.literal(false)))
                    .withProperties(
                        PropertyFactory.lineColor(GRAY),
                        PropertyFactory.lineOpacity(0.35f),
                        PropertyFactory.lineWidth(1.0f)
                    )
            )
        }
        if (style.getLayer(LAYER_LINE) == null) {
            style.addLayer(
                LineLayer(LAYER_LINE, SRC_LINE).withProperties(
                    PropertyFactory.lineColor(ORANGE),
                    PropertyFactory.lineWidth(2f),
                    PropertyFactory.lineDasharray(arrayOf(2f, 1.6f))
                )
            )
        }
        // 停用目标：灰色
        if (style.getLayer(LAYER_TARGET_OTHER) == null) {
            style.addLayer(
                CircleLayer(LAYER_TARGET_OTHER, SRC_TARGET)
                    .withFilter(Expression.eq(Expression.get("enabled"), Expression.literal(false)))
                    .withProperties(
                        PropertyFactory.circleRadius(5f),
                        PropertyFactory.circleColor(GRAY),
                        PropertyFactory.circleStrokeColor("#F1F1EE"),
                        PropertyFactory.circleStrokeWidth(2f)
                    )
            )
        }
        // 已启用目标：橙色
        if (style.getLayer(LAYER_TARGET) == null) {
            style.addLayer(
                CircleLayer(LAYER_TARGET, SRC_TARGET)
                    .withFilter(Expression.eq(Expression.get("enabled"), Expression.literal(true)))
                    .withProperties(
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleColor(ORANGE),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(2f)
                    )
            )
        }
        if (style.getLayer(LAYER_TARGET_LABEL) == null) {
            style.addLayer(
                SymbolLayer(LAYER_TARGET_LABEL, SRC_TARGET).withProperties(
                    PropertyFactory.textField(Expression.get("name")),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(12f),
                    PropertyFactory.textColor(
                        Expression.switchCase(
                            Expression.get("enabled"),
                            Expression.literal("#B34A00"),
                            Expression.literal("#6F6F73")
                        )
                    ),
                    PropertyFactory.textHaloColor("#F1F1EE"),
                    PropertyFactory.textHaloWidth(1.6f),
                    PropertyFactory.textAnchor("top"),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textIgnorePlacement(true)
                )
            )
        }
        if (style.getLayer(LAYER_USER) == null) {
            style.addLayer(
                CircleLayer(LAYER_USER, SRC_USER).withProperties(
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleColor("#2B2B2B"),
                    PropertyFactory.circleStrokeColor("#F1F1EE"),
                    PropertyFactory.circleStrokeWidth(2f)
                )
            )
        }
    }

    private fun addSourceIfMissing(style: Style, id: String) {
        if (style.getSource(id) == null) {
            style.addSource(GeoJsonSource(id, FeatureCollection.fromFeatures(emptyList())))
        }
    }

    private fun updateUser(style: Style, user: LatLng?) {
        val source = style.getSourceAs<GeoJsonSource>(SRC_USER) ?: return
        val features = if (user == null) {
            emptyList()
        } else {
            listOf(Feature.fromGeometry(Point.fromLngLat(user.longitude, user.latitude)))
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun updateTargets(style: Style, targets: List<MapTarget>) {
        val source = style.getSourceAs<GeoJsonSource>(SRC_TARGET) ?: return
        val features = targets.map { target ->
            val properties = JsonObject().apply {
                addProperty("name", target.name)
                addProperty("enabled", target.enabled)
                addProperty("primary", target.primary)
            }
            Feature.fromGeometry(
                Point.fromLngLat(target.longitude, target.latitude),
                properties
            )
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun updateRadius(style: Style, targets: List<MapTarget>) {
        val source = style.getSourceAs<GeoJsonSource>(SRC_RADIUS) ?: return
        val features = targets
            .filter { it.radiusMeters > 0f }
            .map { target ->
                val ring = DistanceCalculator.circlePolygon(
                    target.latitude, target.longitude, target.radiusMeters.toDouble(), CIRCLE_SEGMENTS
                ).map { Point.fromLngLat(it[1], it[0]) }
                val closed = ring + ring.first()
                val properties = JsonObject().apply { addProperty("enabled", target.enabled) }
                Feature.fromGeometry(Polygon.fromLngLats(listOf(closed)), properties)
            }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /** 只有已启用的目标才画到当前位置的虚线；启用多个就是多条。 */
    private fun updateLines(style: Style, user: LatLng?, targets: List<MapTarget>) {
        val source = style.getSourceAs<GeoJsonSource>(SRC_LINE) ?: return
        if (user == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val features = targets
            .filter { it.enabled }
            .map { target ->
                Feature.fromGeometry(
                    LineString.fromLngLats(
                        listOf(
                            Point.fromLngLat(user.longitude, user.latitude),
                            Point.fromLngLat(target.longitude, target.latitude)
                        )
                    )
                )
            }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /** 相机只对准「当前坐标 + 已启用的目标」，停用的点不参与取景。 */
    private fun fitCamera(scene: MapScene) {
        val points = mutableListOf<LatLng>()
        scene.user?.let { points += it }
        val enabledTargets = scene.targets.filter { it.enabled }
        enabledTargets.forEach { points += LatLng(it.latitude, it.longitude) }
        if (points.isEmpty()) return
        if (points.size == 1) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 14.0), 600)
            return
        }
        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(it) }
        enabledTargets.forEach { target ->
            if (target.radiusMeters > 0f) {
                DistanceCalculator.circlePolygon(
                    target.latitude, target.longitude, target.radiusMeters.toDouble(), 12
                ).forEach { builder.include(LatLng(it[0], it[1])) }
            }
        }
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), CAMERA_PADDING), 600)
    }

    companion object {
        private const val ORANGE = "#FF6A00"
        private const val GRAY = "#8E8E93"
        private const val CIRCLE_SEGMENTS = 64
        private const val CAMERA_PADDING = 140

        private const val SRC_USER = "anen-user-src"
        private const val SRC_TARGET = "anen-target-src"
        private const val SRC_RADIUS = "anen-radius-src"
        private const val SRC_LINE = "anen-line-src"

        private const val LAYER_USER = "anen-user"
        private const val LAYER_TARGET = "anen-target-primary"
        private const val LAYER_TARGET_OTHER = "anen-target-other"
        private const val LAYER_TARGET_LABEL = "anen-target-label"
        private const val LAYER_RADIUS_FILL = "anen-radius-fill"
        private const val LAYER_RADIUS_FILL_OFF = "anen-radius-fill-off"
        private const val LAYER_RADIUS_LINE = "anen-radius-line"
        private const val LAYER_RADIUS_LINE_OFF = "anen-radius-line-off"
        private const val LAYER_LINE = "anen-line"
    }
}
