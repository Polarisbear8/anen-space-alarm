package com.anen.spacealarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anen.spacealarm.R
import com.anen.spacealarm.ui.components.TermButton
import com.anen.spacealarm.ui.components.TermHint
import com.anen.spacealarm.ui.theme.AnenBackground
import com.anen.spacealarm.ui.theme.AnenOrange
import com.anen.spacealarm.ui.theme.AnenText
import com.anen.spacealarm.ui.theme.AnenTextDim

/**
 * 闹钟全屏界面。第一版：默认系统声音 + 默认振动 + 关闭按钮。
 * 大号数字显示的是“设定的提醒范围”，不是触发时刻的推算距离
 * （Geofence 事件不携带实际位置，推算距离不可靠）。
 */
@Composable
fun AlarmScreen(
    placeName: String,
    rangeText: String,
    message: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnenBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.alarm_screen_title),
            color = AnenOrange,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            fontSize = 15.sp
        )
        Text(
            text = placeName.ifBlank { stringResource(R.string.alarm_target_placeholder) },
            color = AnenText,
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 26.dp)
        )
        Text(
            text = rangeText,
            color = AnenOrange,
            fontFamily = FontFamily.Monospace,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
        TermHint(
            text = stringResource(R.string.alarm_in_range_hint),
            color = AnenTextDim,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (message.isNotBlank()) {
            Text(
                text = message,
                color = AnenText,
                fontSize = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp)
            )
        }
        TermButton(
            text = stringResource(R.string.alarm_dismiss),
            onClick = onDismiss,
            primary = true,
            modifier = Modifier.fillMaxWidth().padding(top = 44.dp)
        )
    }
}
