package com.anen.spacealarm

import android.content.Context
import com.anen.spacealarm.amap.AMapResolver
import com.anen.spacealarm.amap.AMapWebViewResolver
import com.anen.spacealarm.amap.MapResolver
import com.anen.spacealarm.data.AppDatabase
import com.anen.spacealarm.data.ReminderRepository
import com.anen.spacealarm.geofence.GeofenceEngine
import com.anen.spacealarm.geofence.GoogleGeofenceEngine
import com.anen.spacealarm.location.DistanceCalculator
import com.anen.spacealarm.location.LocationFix
import com.anen.spacealarm.location.LocationProvider
import com.anen.spacealarm.model.Reminder
import com.anen.spacealarm.permission.PermissionManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 极简依赖容器。不引入 DI 框架。
 */
object AppContainer {

    /** 系统对每个 App 的 active geofence 上限。 */
    const val MAX_GEOFENCES = 100

    enum class ArmResult {
        ARMED,
        PERMISSIONS_MISSING,
        LOCATION_UNAVAILABLE,
        INSIDE_RANGE,
        LIMIT_REACHED,
        REGISTRATION_FAILED
    }

    /** 创建提醒的结果：成功时 reminderId > 0；被拒绝时 reminderId = 0 且没有写入数据库。 */
    data class ArmOutcome(
        val reminderId: Long,
        val result: ArmResult,
        val distanceMeters: Double? = null,
        val radiusMeters: Float = 0f
    )

    /** 保存前的前置检查结果。 */
    internal data class Preflight(
        val blockedBy: ArmResult?,
        val distanceMeters: Double?
    )

    /**
     * 纯函数：判断能否创建提醒。
     *
     * 提醒的语义是“进入范围时触发”，如果用户此刻已经在范围内，
     * 就不存在之后的“进入”事件，必须拒绝创建。
     *
     * 定位不可用时返回 LOCATION_UNAVAILABLE，绝不能当成“不在范围内”。
     */
    internal fun preflightArm(
        location: LocationFix?,
        targetLatitude: Double,
        targetLongitude: Double,
        radiusMeters: Float
    ): Preflight {
        if (location == null) return Preflight(ArmResult.LOCATION_UNAVAILABLE, null)
        val distance = DistanceCalculator.calculateDistanceMeters(
            location.latitude, location.longitude, targetLatitude, targetLongitude
        )
        val blocked = if (distance <= radiusMeters) ArmResult.INSIDE_RANGE else null
        return Preflight(blocked, distance)
    }

    @Volatile
    private var database: AppDatabase? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun database(context: Context): AppDatabase = database ?: synchronized(this) {
        database ?: AppDatabase.get(context).also { database = it }
    }

    fun reminderRepository(context: Context): ReminderRepository =
        ReminderRepository(database(context).reminderDao())

    /**
     * 注意：这里传入的是调用方的 context（通常是 Activity）。
     * WebView 兜底解析需要临时把 WebView 挂到窗口上才会执行 JS，
     * 解析结束会立即移除并销毁，不长期持有 Activity。
     */
    fun mapResolver(context: Context): MapResolver =
        AMapResolver(
            client = httpClient,
            webViewResolver = AMapWebViewResolver(context),
            fallbackName = context.getString(R.string.place_unnamed)
        )

    fun geofenceEngine(context: Context): GeofenceEngine = GoogleGeofenceEngine(context)

    fun locationProvider(context: Context): LocationProvider = LocationProvider(context)

    /**
     * 保存提醒并注册围栏。
     *
     * 顺序：
     *   1. 取一次当前精确定位 → 判断是否已在范围内（是则直接拒绝，不写数据库）
     *   2. 保存提醒
     *   3. 围栏就绪且未超上限时注册围栏；否则标记为停用
     */
    suspend fun createAndArm(context: Context, reminder: Reminder): ArmOutcome {
        if (!PermissionManager.hasFineLocation(context)) {
            return ArmOutcome(0L, ArmResult.PERMISSIONS_MISSING, radiusMeters = reminder.radiusMeters)
        }

        val fix = locationProvider(context).currentLocation()?.let {
            LocationFix(it.latitude, it.longitude, it.accuracy)
        }
        val preflight = preflightArm(fix, reminder.latitude, reminder.longitude, reminder.radiusMeters)
        preflight.blockedBy?.let { blocked ->
            return ArmOutcome(0L, blocked, preflight.distanceMeters, reminder.radiusMeters)
        }

        val repository = reminderRepository(context)
        val id = repository.save(reminder)
        val saved = reminder.copy(id = id)
        val result = when {
            !PermissionManager.state(context).geofenceReady -> ArmResult.PERMISSIONS_MISSING
            // 新提醒已经保存并计入 active，达到上限（含本条）时不允许再注册
            repository.getActive().size >= MAX_GEOFENCES -> ArmResult.LIMIT_REACHED
            else -> try {
                geofenceEngine(context).add(saved)
                ArmResult.ARMED
            } catch (e: Exception) {
                ArmResult.REGISTRATION_FAILED
            }
        }
        if (result != ArmResult.ARMED) {
            repository.setEnabled(id, false)
        }
        return ArmOutcome(id, result, preflight.distanceMeters, reminder.radiusMeters)
    }
}
