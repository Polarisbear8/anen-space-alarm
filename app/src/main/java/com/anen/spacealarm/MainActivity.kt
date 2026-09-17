package com.anen.spacealarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anen.spacealarm.amap.ResolveResult
import com.anen.spacealarm.model.AlertMode
import com.anen.spacealarm.model.CoordinateSystem
import com.anen.spacealarm.model.Place
import com.anen.spacealarm.model.PlaceSource
import com.anen.spacealarm.model.Reminder
import com.anen.spacealarm.permission.PermissionManager
import com.anen.spacealarm.preferences.AppPreferences
import com.anen.spacealarm.preferences.LocaleManager
import com.anen.spacealarm.share.ShareContent
import com.anen.spacealarm.ui.AboutScreen
import com.anen.spacealarm.ui.CreateReminderScreen
import com.anen.spacealarm.ui.EasterEgg
import com.anen.spacealarm.ui.HomeScreen
import com.anen.spacealarm.ui.LanguageScreen
import com.anen.spacealarm.ui.LocationSettingsScreen
import com.anen.spacealarm.ui.NewPlaceScreen
import com.anen.spacealarm.ui.PermissionCheckScreen
import com.anen.spacealarm.ui.ReminderListScreen
import com.anen.spacealarm.ui.SettingsScreen
import com.anen.spacealarm.ui.TutorialScreen
import com.anen.spacealarm.ui.armBlockedMessage
import com.anen.spacealarm.ui.failureText
import com.anen.spacealarm.ui.geofenceWarning
import com.anen.spacealarm.ui.components.TermButton
import com.anen.spacealarm.ui.components.TermText
import com.anen.spacealarm.ui.theme.AnenOrange
import com.anen.spacealarm.ui.theme.AnenPanel
import com.anen.spacealarm.ui.theme.AnenText
import com.anen.spacealarm.ui.theme.AnenTheme
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35 起系统强制 edge-to-edge：这里显式启用并保持深色系统栏图标，
        // 各页面再用 statusBarsPadding / navigationBarsPadding 保证内容不被系统栏遮挡。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        // MapLibre 必须在任何 MapView 被创建之前初始化：
        // 放在 setContent 之前，确保 Compose 首次组合到 AnenMapView 时已经初始化完成。
        MapLibre.getInstance(this)
        setContent {
            AnenTheme {
                AnenRoot()
            }
        }
    }

    companion object {
        fun contentIntent(context: Context, reminderId: Long = -1L): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                reminderId.toInt().coerceAtLeast(1),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object List : Screen
    data object NewPlace : Screen
    data class Create(val place: Place) : Screen
    data class Edit(val reminder: Reminder) : Screen
    data object Settings : Screen
    data object PermissionCheck : Screen
    data object Language : Screen
    data object About : Screen
    data object Tutorial : Screen
    data object LocationSettings : Screen
}

@Composable
private fun AnenRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { AppContainer.reminderRepository(context) }
    val geofenceEngine = remember { AppContainer.geofenceEngine(context) }
    val remindersFlow = remember { repository.observeAll() }
    val reminders by remindersFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var screen by remember {
        mutableStateOf<Screen>(
            if (AppPreferences.tutorialSeen(context)) Screen.Home else Screen.Tutorial
        )
    }
    var permissions by remember { mutableStateOf(PermissionManager.state(context)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var geofenceAvailable by remember { mutableStateOf(geofenceEngine.isAvailable()) }
    var developerMode by remember { mutableStateOf(AppPreferences.developerMode(context)) }
    var languageTag by remember { mutableStateOf(AppPreferences.language(context)) }
    var locationIntervalSeconds by remember { mutableStateOf(AppPreferences.locationIntervalSeconds(context)) }
    var easterEggFound by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        permissions = PermissionManager.state(context)
        geofenceAvailable = geofenceEngine.isAvailable()
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun openSettingsIntent(intent: Intent?) {
        if (intent == null) {
            toast(context.getString(R.string.toast_cannot_open_settings))
            return
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast(context.getString(R.string.toast_cannot_open_settings))
        }
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshPermissions() }

    // 前台定位只申请 Fine + Coarse；后台定位不在这里自动跟着申请
    // （Android 11+ 必须由用户进入设置页单独开启“始终允许”）。
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshPermissions() }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissions() }

    // 从系统设置页返回后重新检查权限，保证 UI 状态不过期。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestLocation() {
        locationLauncher.launch(PermissionManager.locationPermissions())
    }

    fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            toast(context.getString(R.string.toast_no_background_permission_needed))
            return
        }
        if (!PermissionManager.hasFineLocation(context)) {
            toast(context.getString(R.string.toast_grant_precise_first))
            requestLocation()
            return
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Android 10：弹窗内可以选择“始终允许”
            backgroundLocationLauncher.launch(PermissionManager.backgroundLocationPermissions())
        } else {
            // Android 11+：系统弹窗不再提供“始终允许”，必须引导进入应用设置页
            toast(context.getString(R.string.toast_choose_allow_all_time))
            openSettingsIntent(PermissionManager.appDetailsSettingsIntent(context))
        }
    }

    fun requestNotifications() {
        val notificationPermissions = PermissionManager.notificationPermissions()
        if (notificationPermissions.isEmpty()) {
            toast(context.getString(R.string.toast_no_notification_permission_needed))
            return
        }
        notificationLauncher.launch(notificationPermissions.first())
    }

    suspend fun registerGeofence(reminder: Reminder): Boolean = try {
        geofenceEngine.add(reminder)
        true
    } catch (e: Exception) {
        false
    }

    fun activate(place: Place, radiusMeters: Float, mode: AlertMode, message: String, repeat: Boolean) {
        scope.launch {
            busy = true
            error = null
            try {
                val draft = Reminder(
                    placeName = place.name,
                    address = place.address,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    radiusMeters = radiusMeters,
                    message = message,
                    alertMode = mode,
                    repeat = repeat
                )
                val outcome = AppContainer.createAndArm(context, draft)
                when (outcome.result) {
                    AppContainer.ArmResult.ARMED -> {
                        screen = Screen.Home
                    }
                    // 已在范围内 / 取不到定位：不写数据库、不注册围栏，留在本页让用户改地点或半径
                    AppContainer.ArmResult.INSIDE_RANGE,
                    AppContainer.ArmResult.LOCATION_UNAVAILABLE,
                    AppContainer.ArmResult.PERMISSIONS_MISSING -> {
                        error = armBlockedMessage(context, outcome)
                    }
                    // 已保存但未启用：提示后回到首页，可在列表里重新启用
                    else -> {
                        toast(context.getString(R.string.arm_saved_disabled, outcome.result.name))
                        screen = Screen.Home
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    /** 编辑已存在的提醒：保存所有设置，并按新设置重新注册围栏。 */
    fun saveEdit(updated: Reminder) {
        scope.launch {
            busy = true
            error = null
            try {
                repository.update(updated)
                if (updated.enabled) {
                    // 同一个 requestId 重新注册即为更新
                    if (!registerGeofence(updated)) {
                        toast(context.getString(R.string.arm_registration_failed))
                    }
                }
                screen = Screen.List
            } finally {
                busy = false
            }
        }
    }

    BackHandler(enabled = screen != Screen.Home) {
        screen = Screen.Home
        error = null
    }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            reminders = reminders,
            permissions = permissions,
            geofenceAvailable = geofenceAvailable,
            developerMode = developerMode,
            locationIntervalSeconds = locationIntervalSeconds,
            onNewReminder = {
                error = null
                screen = Screen.NewPlace
            },
            onOpenList = { screen = Screen.List },
            onOpenSettings = { screen = Screen.Settings },
            onGrantLocation = { requestLocation() },
            onGrantBackgroundLocation = { requestBackgroundLocation() }
        )

        Screen.List -> ReminderListScreen(
            reminders = reminders,
            developerMode = developerMode,
            onEdit = { reminder ->
                error = null
                screen = Screen.Edit(reminder)
            },
            onToggleEnabled = { reminder, enabled ->
                scope.launch {
                    if (enabled) {
                        if (registerGeofence(reminder)) {
                            repository.setEnabled(reminder.id, true)
                        } else {
                            toast(context.getString(R.string.arm_registration_failed))
                        }
                    } else {
                        geofenceEngine.remove(reminder.id)
                        repository.setEnabled(reminder.id, false)
                    }
                }
            },
            onRearm = { reminder ->
                scope.launch {
                    if (registerGeofence(reminder)) {
                        repository.setEnabled(reminder.id, true)
                    } else {
                        toast(context.getString(R.string.arm_registration_failed))
                    }
                }
            },
            onDelete = { reminder ->
                scope.launch {
                    geofenceEngine.remove(reminder.id)
                    repository.delete(reminder)
                }
            },
            onBack = { screen = Screen.Home }
        )

        Screen.NewPlace -> NewPlaceScreen(
            busy = busy,
            error = error,
            onResolveLink = { link ->
                scope.launch {
                    busy = true
                    error = null
                    val result = AppContainer.mapResolver(context).resolve(
                        ShareContent(
                            action = Intent.ACTION_SEND,
                            mimeType = "text/plain",
                            text = link,
                            title = null,
                            htmlText = null,
                            dataUri = null,
                            clipTexts = emptyList()
                        )
                    )
                    busy = false
                    when (result) {
                        is ResolveResult.Success -> {
                            AppPreferences.setLastResolveDebug(context, result.report)
                            error = null
                            screen = Screen.Create(result.place)
                        }
                        is ResolveResult.Failure -> {
                            AppPreferences.setLastResolveDebug(context, result.report)
                            error = failureText(context, result.reason)
                        }
                    }
                }
            },
            onBack = { screen = Screen.Home }
        )

        is Screen.Create -> CreateReminderScreen(
            place = current.place,
            busy = busy,
            error = error,
            warning = geofenceWarning(context, permissions),
            onActivate = { radius, mode, message, repeat ->
                activate(current.place, radius, mode, message, repeat)
            },
            onBack = {
                error = null
                screen = Screen.Home
            }
        )

        is Screen.Edit -> CreateReminderScreen(
            place = Place.of(
                name = current.reminder.placeName,
                address = current.reminder.address,
                latitude = current.reminder.latitude,
                longitude = current.reminder.longitude,
                coordinateSystem = CoordinateSystem.WGS84,
                source = PlaceSource.MANUAL
            ),
            existing = current.reminder,
            busy = busy,
            error = error,
            warning = geofenceWarning(context, permissions),
            onActivate = { radius, mode, message, repeat ->
                saveEdit(
                    current.reminder.copy(
                        radiusMeters = radius,
                        alertMode = mode,
                        message = message,
                        repeat = repeat
                    )
                )
            },
            onBack = {
                error = null
                screen = Screen.List
            }
        )
        Screen.Settings -> SettingsScreen(
            reminders = reminders,
            permissions = permissions,
            developerMode = developerMode,
            onToggleDeveloperMode = { enabled ->
                AppPreferences.setDeveloperMode(context, enabled)
                developerMode = enabled
            },
            onOpenPermissionCheck = { screen = Screen.PermissionCheck },
            onOpenLanguage = { screen = Screen.Language },
            onOpenTutorial = { screen = Screen.Tutorial },
            onOpenLocationSettings = { screen = Screen.LocationSettings },
            locationIntervalSeconds = locationIntervalSeconds,
            onOpenAbout = { screen = Screen.About },
            onBack = { screen = Screen.Home }
        )

        Screen.PermissionCheck -> PermissionCheckScreen(
            state = permissions,
            onOpenAppSettings = {
                openSettingsIntent(PermissionManager.appDetailsSettingsIntent(context))
            },
            onBack = { screen = Screen.Settings }
        )

        Screen.Language -> LanguageScreen(
            currentTag = languageTag,
            onSelect = { tag ->
                AppPreferences.setLanguage(context, tag)
                languageTag = tag
                // 立即生效：重建 Activity 让新的 locale 作用到所有界面
                (context as? ComponentActivity)?.recreate()
            },
            onBack = { screen = Screen.Settings }
        )

        Screen.About -> AboutScreen(
            onBack = { screen = Screen.Settings },
            onUnlockEasterEgg = {
                scope.launch {
                    // 连点不重复建条目：已解锁过就不再保存
                    if (!EasterEgg.alreadyCreated(reminders)) {
                        repository.save(
                            EasterEgg.reminder(
                                placeName = context.getString(R.string.easter_egg_place_name),
                                address = context.getString(R.string.easter_egg_address),
                                message = context.getString(R.string.easter_egg_message)
                            )
                        )
                    }
                    easterEggFound = true
                }
            }
        )

        Screen.LocationSettings -> LocationSettingsScreen(
            options = AppPreferences.LOCATION_INTERVAL_OPTIONS,
            currentSeconds = locationIntervalSeconds,
            onSelect = { seconds ->
                AppPreferences.setLocationIntervalSeconds(context, seconds)
                locationIntervalSeconds = seconds
            },
            onBack = { screen = Screen.Settings }
        )

        Screen.Tutorial -> TutorialScreen(
            onDone = {
                AppPreferences.setTutorialSeen(context, true)
                screen = Screen.Home
            }
        )
    }

    if (easterEggFound) {
        AlertDialog(
            onDismissRequest = { easterEggFound = false },
            confirmButton = {
                TermButton(
                    text = stringResource(R.string.action_ok),
                    onClick = { easterEggFound = false },
                    primary = true
                )
            },
            title = {
                TermText(
                    text = stringResource(R.string.easter_egg_dialog_title),
                    color = AnenOrange,
                    size = 13,
                    weight = FontWeight.Bold
                )
            },
            text = {
                TermText(
                    text = stringResource(R.string.easter_egg_dialog_message),
                    color = AnenText,
                    size = 14
                )
            },
            containerColor = AnenPanel
        )
    }
}
