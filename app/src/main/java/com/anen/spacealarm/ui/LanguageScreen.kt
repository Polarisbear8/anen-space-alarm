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
import com.anen.spacealarm.preferences.LocaleManager
import com.anen.spacealarm.ui.components.TermHeader
import com.anen.spacealarm.ui.components.TermHint
import com.anen.spacealarm.ui.components.TermLabel
import com.anen.spacealarm.ui.components.TermScreen
import com.anen.spacealarm.ui.components.TermSection
import com.anen.spacealarm.ui.theme.AnenLine
import com.anen.spacealarm.ui.theme.AnenOrange
import com.anen.spacealarm.ui.theme.AnenText

/**
 * 语言选择：跟随系统 / 中文 / English。
 * 选择立即持久化并重建界面，不影响数据库、提醒与地理围栏。
 */
@Composable
fun LanguageScreen(
    currentTag: String?,
    onSelect: (String?) -> Unit,
    onBack: () -> Unit
) {
    TermScreen {
        TermHeader(
            title = stringResource(R.string.language_title),
            subtitle = stringResource(R.string.language_subtitle),
            onBack = onBack
        )

        TermSection("01", stringResource(R.string.settings_language))
        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            LanguageRow(
                label = stringResource(R.string.language_system),
                selected = currentTag == null,
                onClick = { onSelect(null) }
            )
            LanguageRow(
                label = stringResource(R.string.language_chinese),
                selected = currentTag == LocaleManager.CHINESE,
                onClick = { onSelect(LocaleManager.CHINESE) }
            )
            LanguageRow(
                label = stringResource(R.string.language_english),
                selected = currentTag == LocaleManager.ENGLISH,
                onClick = { onSelect(LocaleManager.ENGLISH) }
            )
            TermHint(
                stringResource(R.string.language_hint),
                modifier = Modifier.padding(top = 10.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
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
