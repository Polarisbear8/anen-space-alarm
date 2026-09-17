package com.anen.spacealarm.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.anen.spacealarm.AppContainer
import com.anen.spacealarm.R
import com.anen.spacealarm.location.DistanceCalculator
import com.anen.spacealarm.map.AnenMapView
import com.anen.spacealarm.map.MapController
import com.anen.spacealarm.model.Reminder
import com.anen.spacealarm.permission.PermissionManager
import com.anen.spacealarm.ui.components.TermButton
import com.anen.spacealarm.ui.components.TermTag
import com.anen.spacealarm.ui.theme.AnenBackground
import com.anen.spacealarm.ui.theme.AnenDanger
import com.anen.spacealarm.ui.theme.AnenDeep
import com.anen.spacealarm.ui.theme.AnenLine
import com.anen.spacealarm.ui.theme.AnenLineStrong
import com.anen.spacealarm.ui.theme.AnenOrange
import com.anen.spacealarm.ui.theme.AnenOrangeDim
import com.anen.spacealarm.ui.theme.AnenText
import com.anen.spacealarm.ui.theme.AnenTextDim
import com.anen.spacealarm.ui.theme.AnenWarn
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主页：地图是主视觉（整屏铺满，含系统栏后方），
 * 只在上下叠加少量终端状态信息。开发者模式下额外显示定位调试行。
 */
@Composable
fun HomeScreen(
    reminders: List<Reminder>,
    permissions: PermissionManager.State,
    geofenceAvailable: Boolean,
    developerMode: Boolean,
    locationIntervalSeconds: Int,
    onNewReminder: () -> Unit,
    onOpenList: () -> Unit,
    onOpenSettings: () -> Unit,
    onGrantLocation: () -> Unit,
    onGrantBackgroundLocation: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationProvider = remember { AppContainer.locationProvider(context) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var controller by remember { mutableStateOf<MapController?>(null) }
    var lastFitKey by remember { mutableStateOf<Long?>(null) }
    var clock by remember { mutableStateOf(currentTime()) }

    // 手动刷新：立即取一次高精度定位
    val onRefreshLocation: () -> Unit = {
        scope.launch {
            locationProvider.currentLocation()?.let { userLocation = it }
        }
    }

    DisposableEffect(context) {
        // 顶部时钟跟随系统 ACTION_TIME_TICK（每分钟一次），页面离开自动注销。
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                clock = currentTime()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_TIME_TICK),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(permissions.fineLocation || permissions.coarseLocation, locationIntervalSeconds) {
        if (!permissions.fineLocation && !permissions.coarseLocation) return@LaunchedEffect
        locationProvider.lastKnownLocation()?.let { userLocation = it }
        // 地图可见期间秒级刷新（高精度）；离开页面 Flow 自动取消，不存在后台轮询。
        locationProvider.locationUpdates(
            intervalMillis = locationIntervalSeconds * 1000L,
            highAccuracy = true
        )
            .collect { userLocation = it }
    }

    // 启用中的闹钟按距离排序，信息面板逐页显示（循环），面板高度不变
    val enabledSorted = reminders
        .filter { it.enabled && !it.triggered }
        .sortedBy { reminder ->
            userLocation?.let {
                DistanceCalculator.calculateDistanceMeters(
                    it.latitude, it.longitude, reminder.latitude, reminder.longitude
                )
            } ?: Double.MAX_VALUE
        }
    var pageIndex by remember { mutableIntStateOf(0) }
    val pageCount = enabledSorted.size
    val currentIndex = if (pageCount == 0) 0 else ((pageIndex % pageCount) + pageCount) % pageCount
    val selected = enabledSorted.getOrNull(currentIndex)
    val distance = selected?.let { reminder ->
        userLocation?.let {
            DistanceCalculator.calculateDistanceMeters(
                it.latitude, it.longitude, reminder.latitude, reminder.longitude
            )
        }
    }
    val inRange = distance != null && distance <= (selected?.radiusMeters ?: 0f)
    val status = statusOf(selected, distance, inRange)

    // 地图上显示全部提醒：已启用为橙色并画虚线，停用为灰色且不画线
    val targets = reminders.map { reminder ->
        MapController.MapTarget(
            latitude = reminder.latitude,
            longitude = reminder.longitude,
            radiusMeters = reminder.radiusMeters,
            name = reminder.placeName,
            enabled = reminder.enabled && !reminder.triggered,
            primary = reminder.id == selected?.id
        )
    }
    val scene = MapController.MapScene(
        user = userLocation?.let { LatLng(it.latitude, it.longitude) },
        targets = targets,
        primary = targets.firstOrNull { it.primary },
        fitCamera = false
    )

    // 主目标变化时对准一次（没有目标时对准当前位置）
    val fitKey: Long? = selected?.id ?: userLocation?.let { -1L }
    LaunchedEffect(scene, controller) {
        val mapController = controller ?: return@LaunchedEffect
        val shouldFit = fitKey != null && fitKey != lastFitKey
        if (shouldFit) lastFitKey = fitKey
        mapController.render(scene.copy(fitCamera = shouldFit))
    }

    Box(modifier = Modifier.fillMaxSize().background(AnenBackground)) {
        AnenMapView(
            modifier = Modifier.fillMaxSize(),
            onControllerReady = { controller = it }
        )

        Column(modifier = Modifier.fillMaxSize()) {
            MapHeader(
                clock = clock,
                statusText = stringResource(status.first),
                statusColor = status.second
            )

            if (!permissions.geofenceReady || !geofenceAvailable) {
                PermissionWarning(
                    permissions = permissions,
                    geofenceAvailable = geofenceAvailable,
                    onGrantLocation = onGrantLocation,
                    onGrantBackgroundLocation = onGrantBackgroundLocation
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                MapToolbar(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    onRefreshLocation = onRefreshLocation,
                    onRecenter = { controller?.recenter(scene) }
                )
                if (developerMode) {
                    LocationDebugOverlay(
                        location = userLocation,
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                    )
                }
            }

            DataStrip(
                reminder = selected,
                distanceMeters = distance,
                inRange = inRange,
                reminderCount = reminders.size,
                pageIndex = currentIndex,
                pageCount = pageCount,
                onPrev = { pageIndex = currentIndex - 1 },
                onNext = { pageIndex = currentIndex + 1 }
            )

            HomeActionBar(
                onOpenList = onOpenList,
                onNewReminder = onNewReminder,
                onOpenSettings = onOpenSettings,
                count = reminders.size
            )
        }
    }
}

@Composable
private fun MapHeader(clock: String, statusText: String, statusColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AnenBackground.copy(alpha = 0.90f))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(3.dp).height(22.dp).background(AnenOrange)
            )
            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    text = stringResource(R.string.app_title),
                    color = AnenText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = stringResource(R.string.app_subtitle),
                    color = AnenTextDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    letterSpacing = 2.sp
                )
            }
            TermTag(statusText, statusColor)
            Text(
                text = clock,
                color = AnenText,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AnenOrangeDim))
    }
}

@Composable
private fun MapToolbar(
    modifier: Modifier = Modifier,
    onRefreshLocation: () -> Unit,
    onRecenter: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MapIconButton(
            icon = Icons.Filled.Refresh,
            description = stringResource(R.string.home_refresh_location),
            onClick = onRefreshLocation
        )
        MapIconButton(
            icon = Icons.Filled.LocationOn,
            description = stringResource(R.string.home_recenter),
            onClick = onRecenter
        )
    }
}

@Composable
private fun MapIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(AnenBackground.copy(alpha = 0.85f))
            .border(1.dp, AnenLine, RoundedCornerShape(0.dp)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = description, tint = AnenOrange)
        }
    }
}

/** 开发者模式：地图上的定位调试信息。 */
@Composable
private fun LocationDebugOverlay(location: Location?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(AnenDeep.copy(alpha = 0.92f))
            .border(1.dp, AnenLine)
            .padding(8.dp)
    ) {
        Text(
            text = stringResource(R.string.dev_section_location),
            color = AnenOrange,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp
        )
        Text(
            text = if (location == null) {
                stringResource(R.string.dev_label_no_data)
            } else {
                "%.6f / %.6f\n%.0f m · %s".format(
                    location.latitude,
                    location.longitude,
                    location.accuracy,
                    location.provider ?: "-"
                )
            },
            color = AnenTextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** 底部数据条：TARGET / RANGE / DIST / STATUS / MSG，可左右循环切换启用的闹钟。 */
@Composable
private fun DataStrip(
    reminder: Reminder?,
    distanceMeters: Double?,
    inRange: Boolean,
    reminderCount: Int,
    pageIndex: Int,
    pageCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AnenDeep)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AnenLine))

        if (reminder == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.width(3.dp).height(34.dp).background(AnenWarn)
                )
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_no_target_title),
                        color = AnenWarn,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = if (reminderCount == 0) {
                            stringResource(R.string.home_no_target_empty)
                        } else {
                            stringResource(R.string.home_no_target_disabled, reminderCount)
                        },
                        color = AnenTextDim,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_target),
                    color = AnenTextDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp
                )
                Text(
                    text = reminder.placeName,
                    color = AnenText,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 10.dp).weight(1f)
                )
                // 逐页切换（循环）
                PagerButton("‹", onPrev)
                Text(
                    text = "${pageIndex + 1}/${pageCount}",
                    color = AnenTextDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                PagerButton("›", onNext)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                StripMetric(
                    stringResource(R.string.label_range),
                    DistanceCalculator.formatRadius(reminder.radiusMeters),
                    AnenTextDim
                )
                StripMetric(
                    stringResource(R.string.label_distance),
                    distanceMeters?.let { DistanceCalculator.formatDistance(it) } ?: "--",
                    if (inRange) AnenOrange else AnenText
                )
                StripMetric(
                    stringResource(R.string.label_status),
                    stringResource(statusOf(reminder, distanceMeters, inRange).first),
                    statusOf(reminder, distanceMeters, inRange).second
                )
            }
            if (reminder.message.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.label_message),
                        color = AnenTextDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = reminder.message,
                        color = AnenTextDim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(AnenLine))
        }
    }
}

@Composable
private fun PagerButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .border(1.dp, AnenLineStrong, RoundedCornerShape(0.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = AnenText,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun StripMetric(label: String, value: String, valueColor: Color) {
    Column {
        Text(
            text = label,
            color = AnenTextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun HomeActionBar(
    onOpenList: () -> Unit,
    onNewReminder: () -> Unit,
    onOpenSettings: () -> Unit,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AnenBackground)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TermButton(text = stringResource(R.string.home_alarms, count), onClick = onOpenList)
        TermButton(
            text = stringResource(R.string.home_arm_new),
            onClick = onNewReminder,
            primary = true,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(42.dp)
                .border(1.dp, AnenLine)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = AnenTextDim
                )
            }
        }
    }
}

@Composable
private fun PermissionWarning(
    permissions: PermissionManager.State,
    geofenceAvailable: Boolean,
    onGrantLocation: () -> Unit,
    onGrantBackgroundLocation: () -> Unit
) {
    val message: String
    val action: (() -> Unit)?
    when {
        !geofenceAvailable -> {
            message = stringResource(R.string.warning_no_geofence)
            action = null
        }
        !permissions.fineLocation -> {
            message = stringResource(R.string.warning_need_precise)
            action = onGrantLocation
        }
        !permissions.backgroundLocation -> {
            message = stringResource(R.string.warning_need_background)
            action = onGrantBackgroundLocation
        }
        else -> {
            message = ""
            action = null
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AnenDeep.copy(alpha = 0.94f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(3.dp).height(18.dp)
                .background(if (action == null) AnenDanger else AnenWarn)
        )
        Text(
            text = message,
            color = if (action == null) AnenDanger else AnenWarn,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(start = 8.dp).weight(1f)
        )
        if (action != null) {
            TermButton(text = stringResource(R.string.action_grant), onClick = action)
        }
    }
}

private fun statusOf(reminder: Reminder?, distanceMeters: Double?, inRange: Boolean): Pair<Int, Color> = when {
    reminder == null -> R.string.status_standby to AnenTextDim
    !reminder.enabled -> R.string.status_disabled to AnenTextDim
    reminder.triggered -> R.string.status_triggered to AnenOrangeDim
    inRange -> R.string.status_in_range to AnenOrange
    distanceMeters != null && distanceMeters <= reminder.radiusMeters * 2 -> R.string.status_approaching to AnenWarn
    else -> R.string.status_armed to AnenText
}

private fun currentTime(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
