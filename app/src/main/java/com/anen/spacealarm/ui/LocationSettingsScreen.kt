package com.anen.spacealarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anen.spacealarm.R
import com.anen.spacealarm.ui.components.TermHeader
import com.anen.spacealarm.ui.components.TermHint
import com.anen.spacealarm.ui.components.TermLabel
import com.anen.spacealarm.ui.components.TermScreen
import com.anen.spacealarm.ui.components.TermSection
import com.anen.spacealarm.ui.theme.AnenLine
import com.anen.spacealarm.ui.theme.AnenOrange
import com.anen.spacealarm.ui.theme.AnenText

/**
 * 定位刷新档位（仅前台地图）。固定档位，不提供自定义。
 */
@Composable
fun LocationSettingsScreen(
    options: List<Int>,
    currentSeconds: Int,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit
) {
    TermScreen {
        TermHeader(
            title = stringResource(R.string.location_refresh_title),
            subtitle = stringResource(R.string.location_refresh_subtitle),
            onBack = onBack
        )

        TermSection("01", stringResource(R.string.settings_location_refresh))
        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            options.forEach { seconds ->
                IntervalRow(
                    label = stringResource(R.string.location_refresh_seconds, seconds),
                    selected = seconds == currentSeconds,
                    onClick = { onSelect(seconds) }
                )
            }
            TermHint(
                stringResource(R.string.location_refresh_hint),
                modifier = Modifier.padding(top = 10.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun IntervalRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .border(1.dp, if (selected) AnenOrange else AnenLine, RoundedCornerShape(0.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(modifier = Modifier.size(8.dp).background(AnenOrange))
            }
        }
        TermLabel(label, color = if (selected) AnenOrange else AnenText, modifier = Modifier.padding(start = 10.dp))
    }
}
