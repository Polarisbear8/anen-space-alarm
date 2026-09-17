package com.anen.spacealarm.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.anen.spacealarm.AppContainer
import com.anen.spacealarm.R
import com.anen.spacealarm.amap.ManualPlaceParser
import com.anen.spacealarm.amap.ResolveResult
import com.anen.spacealarm.model.AlertMode
import com.anen.spacealarm.model.Place
import com.anen.spacealarm.model.Reminder
import com.anen.spacealarm.permission.PermissionManager
import com.anen.spacealarm.preferences.AppPreferences
import com.anen.spacealarm.preferences.LocaleManager
import com.anen.spacealarm.ui.CreateReminderScreen
import com.anen.spacealarm.ui.SharePlaceScreen
import com.anen.spacealarm.ui.ShareUiState
import com.anen.spacealarm.ui.armBlockedMessage
import com.anen.spacealarm.ui.geofenceWarning
import com.anen.spacealarm.ui.theme.AnenTheme
import kotlinx.coroutines.launch

/**
 * 分享入口：只负责接收（ACTION_SEND）+ 展示解析结果。
 * 解析细节全部在 AMapResolver 里，这里不写 URL / HTTP / WebView 逻辑。
 */
class ShareActivity : ComponentActivity() {

    private val received = mutableStateOf<ReceivedShare?>(null)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            AnenTheme {
                val current = received.value
                if (current != null) {
                    ShareRoot(content = current.content, payload = current.payload)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val content = ShareContentExtractor.extract(intent)
        ShareDebugLogger.log(content)
        val payload = ShareDebugLogger.describe(intent)
        // 保存最近一次分享载荷，供开发者模式查看 / 一键复制
        AppPreferences.setLastShareDebug(this, payload)
        received.value = ReceivedShare(content, payload)
    }

    private data class ReceivedShare(val content: ShareContent, val payload: String)
}

@Composable
private fun ShareRoot(content: ShareContent, payload: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ShareUiState>(ShareUiState.Loading) }
    var busy by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var blockMessage by remember { mutableStateOf<String?>(null) }
    var locationConfirmed by remember { mutableStateOf(false) }
    val developerMode = remember { AppPreferences.developerMode(context) }
    val activatedText = stringResource(R.string.arm_activated)

    LaunchedEffect(content, attempt) {
        state = ShareUiState.Loading
        val result = AppContainer.mapResolver(context).resolve(content)
        // 保存最近一次解析报告，供开发者模式查看 / 一键复制
        when (result) {
            is ResolveResult.Success -> AppPreferences.setLastResolveDebug(context, result.report)
            is ResolveResult.Failure -> AppPreferences.setLastResolveDebug(context, result.report)
        }
        state = when (result) {
            is ResolveResult.Success -> ShareUiState.Ready(result.place, result.via)
            is ResolveResult.Failure -> ShareUiState.Failed(result.reason)
        }
    }

    fun activate(place: Place, radiusMeters: Float, mode: AlertMode, message: String, repeat: Boolean) {
        scope.launch {
            busy = true
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
            busy = false
            when (outcome.result) {
                AppContainer.ArmResult.ARMED -> {
                    Toast.makeText(context, activatedText, Toast.LENGTH_LONG).show()
                    (context as? ComponentActivity)?.finish()
                }
                // 已在范围内 / 取不到定位：不写数据库，留在页面让用户改半径
                AppContainer.ArmResult.INSIDE_RANGE,
                AppContainer.ArmResult.LOCATION_UNAVAILABLE,
                AppContainer.ArmResult.PERMISSIONS_MISSING -> {
                    blockMessage = armBlockedMessage(context, outcome)
                }
                else -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.arm_saved_disabled, ""),
                        Toast.LENGTH_LONG
                    ).show()
                    (context as? ComponentActivity)?.finish()
                }
            }
        }
    }

    BackHandler {
        (context as? ComponentActivity)?.finish()
    }

    val current = state
    if (current is ShareUiState.Ready && locationConfirmed) {
        CreateReminderScreen(
            place = current.place,
            busy = busy,
            error = blockMessage,
            warning = geofenceWarning(context, PermissionManager.state(context)),
            onActivate = { radius, mode, message, repeat ->
                activate(current.place, radius, mode, message, repeat)
            },
            onBack = { (context as? ComponentActivity)?.finish() }
        )
    } else {
        SharePlaceScreen(
            state = current,
            payload = payload,
            developerMode = developerMode,
            onUseLocation = { locationConfirmed = true },
            onManualInput = { name, coordinates, system ->
                val place = ManualPlaceParser.parse(name, coordinates, system)
                if (place == null) {
                    Toast.makeText(context, R.string.manual_coords_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    state = ShareUiState.Ready(place, "manual-input")
                }
            },
            onRetry = { attempt++ },
            onCancel = { (context as? ComponentActivity)?.finish() }
        )
    }
}
