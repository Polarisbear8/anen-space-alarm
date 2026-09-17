package com.anen.spacealarm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anen.spacealarm.R
import com.anen.spacealarm.permission.PermissionManager
import com.anen.spacealarm.ui.components.TermButton
import com.anen.spacealarm.ui.components.TermHeader
import com.anen.spacealarm.ui.components.TermHint
import com.anen.spacealarm.ui.components.TermLabel
import com.anen.spacealarm.ui.components.TermScreen
import com.anen.spacealarm.ui.components.TermSection
import com.anen.spacealarm.ui.components.TermTag
import com.anen.spacealarm.ui.theme.AnenOrange
import com.anen.spacealarm.ui.theme.AnenText
import com.anen.spacealarm.ui.theme.AnenTextDim

/**
 * 权限核查：只展示当前状态并帮助用户去系统设置处理，不主动请求权限。
 */
@Composable
fun PermissionCheckScreen(
    state: PermissionManager.State,
    onOpenAppSettings: () -> Unit,
    onBack: () -> Unit
) {
    TermScreen {
        TermHeader(
            title = stringResource(R.string.permission_check_title),
            subtitle = stringResource(R.string.permission_check_subtitle),
            onBack = onBack
        )

        TermSection("01", stringResource(R.string.settings_permission_check))
        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            PermissionStatusRow(
                title = stringResource(R.string.permission_location),
                description = stringResource(R.string.permission_location_desc),
                granted = state.fineLocation || state.coarseLocation
            )
            PermissionStatusRow(
                title = stringResource(R.string.permission_precise),
                description = stringResource(R.string.permission_precise_desc),
                granted = state.fineLocation
            )
            PermissionStatusRow(
                title = stringResource(R.string.permission_background),
                description = stringResource(R.string.permission_background_desc),
                granted = state.backgroundLocation
            )
            PermissionStatusRow(
                title = stringResource(R.string.permission_notifications),
                description = stringResource(R.string.permission_notifications_desc),
                granted = state.notifications
            )
            PermissionStatusRow(
                title = stringResource(R.string.permission_fullscreen),
                description = stringResource(R.string.permission_fullscreen_desc),
                granted = state.fullScreenIntent
            )
        }

        TermSection("02", stringResource(R.string.permission_geofence_ready))
        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            TermTag(
                text = stringResource(
                    if (state.geofenceReady) R.string.permission_granted else R.string.permission_not_granted
                ),
                color = if (state.geofenceReady) AnenText else AnenOrange
            )
            TermHint(
                stringResource(R.string.permission_request_hint),
                modifier = Modifier.padding(top = 8.dp)
            )
            TermButton(
                text = stringResource(R.string.action_open_system_settings),
                onClick = onOpenAppSettings,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(title: String, description: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TermLabel(title, color = if (granted) AnenText else AnenOrange)
            TermHint(description, modifier = Modifier.padding(top = 2.dp))
        }
        TermTag(
            text = stringResource(
                if (granted) R.string.permission_granted else R.string.permission_not_granted
            ),
            color = if (granted) AnenTextDim else AnenOrange
        )
    }
}
