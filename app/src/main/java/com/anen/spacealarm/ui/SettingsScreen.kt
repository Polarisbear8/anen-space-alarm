package com.anen.spacealarm.ui

import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anen.spacealarm.AppContainer
import com.anen.spacealarm.R
import com.anen.spacealarm.debug.DebugInfo
import com.anen.spacealarm.model.Reminder
import com.anen.spacealarm.permission.PermissionManager
import com.anen.spacealarm.ui.components.TermButton
import com.anen.spacealarm.ui.components.TermCopyButton
import com.anen.spacealarm.ui.components.TermFrame
import com.anen.spacealarm.ui.components.TermHeader
import com.anen.spacealarm.ui.components.TermHint
import com.anen.spacealarm.ui.components.TermLabel
import com.anen.spacealarm.ui.components.TermScreen
import com.anen.spacealarm.ui.components.TermSection
import com.anen.spacealarm.ui.components.TermText
import com.anen.spacealarm.ui.theme.AnenOrange
import com.anen.spacealarm.ui.theme.AnenOrangeDim
import com.anen.spacealarm.ui.theme.AnenText
import com.anen.spacealarm.ui.theme.AnenTextDim
import com.anen.spacealarm.ui.theme.AnenWarn

/**
 * 设置页。
 *
 * 普通用户只有四项：权限核查 / 语言 / 开发者模式 / 关于。
 * 开发者模式打开后才显示 App System Status 与各 Debug 区块（完全隐藏，不是置灰）。
 */
@Composable
fun SettingsScreen(
    reminders: List<Reminder>,
    permissions: PermissionManager.State,
    developerMode: Boolean,
    honorFamily: Boolean,
    batteryOptimizationExempt: Boolean,
    forceNativeProvider: Boolean,
    onToggleDeveloperMode: (Boolean) -> Unit,
    onToggleForceNativeProvider: (Boolean) -> Unit,
    onOpenPermissionCheck: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    locationIntervalSeconds: Int,
    onOpenBackgroundReliability: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val locationProvider = remember { AppContainer.locationProvider(context) }
    var userLocation by remember { mutableStateOf<Location?>(null) }

    LaunchedEffect(permissions.fineLocation || permissions.coarseLocation) {
        if (!permissions.fineLocation && !permissions.coarseLocation) return@LaunchedEffect
        userLocation = locationProvider.lastKnownLocation()
    }

    TermScreen {
        TermHeader(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle),
            onBack = onBack
        )

        TermSection("01", stringResource(R.string.settings_section_items))
        SettingsItem(
            title = stringResource(R.string.settings_permission_check),
            description = stringResource(R.string.settings_permission_check_desc),
            onClick = onOpenPermissionCheck
        )
        SettingsItem(
            title = stringResource(R.string.settings_language),
            description = stringResource(R.string.settings_language_desc),
            onClick = onOpenLanguage
        )
        SettingsItem(
            title = stringResource(R.string.settings_location_refresh),
            description = stringResource(R.string.settings_location_refresh_desc) + " · " +
                stringResource(R.string.location_refresh_seconds, locationIntervalSeconds),
            onClick = onOpenLocationSettings
        )
        SettingsItem(
            title = stringResource(R.string.settings_tutorial),
            description = stringResource(R.string.settings_tutorial_desc),
            onClick = onOpenTutorial
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TermLabel(stringResource(R.string.settings_developer_mode), color = AnenText)
                TermHint(stringResource(R.string.settings_developer_mode_desc))
            }
            TermHint(
                stringResource(if (developerMode) R.string.dev_mode_on else R.string.dev_mode_off),
                color = if (developerMode) AnenOrange else AnenTextDim
            )
            Switch(
                checked = developerMode,
                onCheckedChange = onToggleDeveloperMode,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AnenOrange,
                    checkedTrackColor = AnenOrangeDim
                ),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        SettingsItem(
            title = stringResource(R.string.settings_about),
            description = stringResource(R.string.settings_about_desc),
            onClick = onOpenAbout
        )

        BackgroundReliabilitySection(
            honorFamily = honorFamily,
            batteryOptimizationExempt = batteryOptimizationExempt,
            onOpenBackgroundReliability = onOpenBackgroundReliability,
            onOpenBatterySettings = onOpenBatterySettings
        )

        if (developerMode) {
            TermSection("03", stringResource(R.string.settings_developer_section))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    TermLabel(stringResource(R.string.dev_force_native), color = AnenText)
                    TermHint(stringResource(R.string.dev_force_native_desc))
                }
                TermHint(
                    stringResource(if (forceNativeProvider) R.string.dev_mode_on else R.string.dev_mode_off),
                    color = if (forceNativeProvider) AnenOrange else AnenTextDim
                )
                Switch(
                    checked = forceNativeProvider,
                    onCheckedChange = onToggleForceNativeProvider,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AnenOrange,
                        checkedTrackColor = AnenOrangeDim
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            DebugBlock(
                title = stringResource(R.string.dev_section_compat),
                content = DebugInfo.compatSection(context)
            )
            DebugBlock(
                title = stringResource(R.string.dev_section_location_service),
                content = DebugInfo.locationServiceSection(context)
            )
            DebugBlock(
                title = stringResource(R.string.dev_section_system_status),
                content = DebugInfo.buildReport(
                    context,
                    reminders,
                    userLocation,
                    locationProvider.availability.value
                )
            )
            DebugBlock(
                title = stringResource(R.string.dev_section_share),
                content = DebugInfo.shareSection(context)
            )
            DebugBlock(
                title = stringResource(R.string.dev_section_resolver),
                content = DebugInfo.resolverSection(context)
            )
            DebugBlock(
                title = stringResource(R.string.dev_section_location),
                content = DebugInfo.locationSection(
                    context,
                    userLocation,
                    locationProvider.availability.value
                ) + "\n" + DebugInfo.locationUpdatesSection(context)
            )
            DebugBlock(
                title = stringResource(R.string.dev_section_geofence),
                content = DebugInfo.geofenceSection(
                    context,
                    reminders.count { it.enabled && !it.triggered },
                    reminders.size
                )
            )
            DebugBlock(
                title = stringResource(R.string.dev_section_alarm),
                content = DebugInfo.alarmSection(context)
            )
            DebugBlock(
                title = stringResource(R.string.dev_section_map),
                content = DebugInfo.mapSection(context)
            )
            DebugBlock(
                title = stringResource(R.string.dev_section_reminder),
                content = DebugInfo.reminderSection(context, reminders)
            )
        }

        Column(modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 24.dp)) {
            TermText("ANENG SPACE ALARM", color = AnenTextDim, size = 10)
        }
    }
}

/**
 * 后台可靠性：非侵入式指引 + 手动设置入口。
 * 这些 OEM 开关无法被 App 读取，因此只提示“需要手动确认”，不虚报 ON/OFF。
 */
@Composable
private fun BackgroundReliabilitySection(
    honorFamily: Boolean,
    batteryOptimizationExempt: Boolean,
    onOpenBackgroundReliability: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    TermSection("02", stringResource(R.string.reliability_section))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        TermHint(stringResource(R.string.reliability_intro))
        if (honorFamily) {
            TermLabel(
                stringResource(R.string.reliability_honor_steps),
                color = AnenText,
                modifier = Modifier.padding(top = 8.dp)
            )
            TermHint(stringResource(R.string.reliability_honor_detail))
        } else {
            TermHint(
                stringResource(R.string.reliability_generic_detail),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        TermHint(
            stringResource(R.string.reliability_manual_check),
            color = AnenWarn,
            modifier = Modifier.padding(top = 6.dp)
        )
        TermHint(
            stringResource(
                if (batteryOptimizationExempt) R.string.reliability_battery_exempt
                else R.string.reliability_battery_optimized
            ),
            color = if (batteryOptimizationExempt) AnenTextDim else AnenWarn,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TermButton(
                text = stringResource(R.string.reliability_open_launch),
                onClick = onOpenBackgroundReliability,
                modifier = Modifier.weight(1f)
            )
            TermButton(
                text = stringResource(R.string.reliability_open_battery),
                onClick = onOpenBatterySettings,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SettingsItem(title: String, description: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        TermLabel(title, color = AnenText)
        TermHint(description)
    }
}

/** 开发者模式下的可折叠调试区块，带一键复制。 */
@Composable
private fun DebugBlock(title: String, content: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TermLabel(title, modifier = Modifier.weight(1f))
            TermCopyButton(text = { content })
            TermButton(
                text = stringResource(if (expanded) R.string.action_hide else R.string.action_show),
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        if (expanded) {
            TermFrame(modifier = Modifier.padding(top = 6.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    TermText(content, color = AnenTextDim, size = 10)
                }
            }
        }
    }
}
