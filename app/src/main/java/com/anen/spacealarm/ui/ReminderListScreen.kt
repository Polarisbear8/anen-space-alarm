package com.anen.spacealarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anen.spacealarm.R
import com.anen.spacealarm.location.DistanceCalculator
import com.anen.spacealarm.model.AlertMode
import com.anen.spacealarm.model.Reminder
import com.anen.spacealarm.model.ReminderStatus
import com.anen.spacealarm.ui.components.TermButton
import com.anen.spacealarm.ui.components.TermDivider
import com.anen.spacealarm.ui.components.TermHeader
import com.anen.spacealarm.ui.components.TermHint
import com.anen.spacealarm.ui.components.TermLabel
import com.anen.spacealarm.ui.components.TermScreen
import com.anen.spacealarm.ui.components.TermToggle
import com.anen.spacealarm.ui.components.TermTag
import com.anen.spacealarm.ui.components.TermText
import com.anen.spacealarm.ui.theme.AnenLine
import com.anen.spacealarm.ui.theme.AnenOrange
import com.anen.spacealarm.ui.theme.AnenOrangeDim
import com.anen.spacealarm.ui.theme.AnenText
import com.anen.spacealarm.ui.theme.AnenTextDim

@Composable
fun ReminderListScreen(
    reminders: List<Reminder>,
    developerMode: Boolean,
    onToggleEnabled: (Reminder, Boolean) -> Unit,
    onRearm: (Reminder) -> Unit,
    onEdit: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
    onBack: () -> Unit
) {
    TermScreen(scrollable = false) {
        TermHeader(
            title = stringResource(R.string.alarms_title),
            subtitle = stringResource(R.string.alarms_subtitle, reminders.size),
            onBack = onBack
        )
        if (reminders.isEmpty()) {
            TermHint(stringResource(R.string.alarms_empty), modifier = Modifier.padding(14.dp))
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(reminders, key = { _, item -> item.id }) { index, reminder ->
                ReminderRow(
                    index = index + 1,
                    reminder = reminder,
                    developerMode = developerMode,
                    onToggleEnabled = onToggleEnabled,
                    onRearm = onRearm,
                    onEdit = onEdit,
                    onDelete = onDelete
                )
                TermDivider()
            }
        }
    }
}

@Composable
private fun ReminderRow(
    index: Int,
    reminder: Reminder,
    developerMode: Boolean,
    onToggleEnabled: (Reminder, Boolean) -> Unit,
    onRearm: (Reminder) -> Unit,
    onEdit: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TermLabel("%02d".format(index), color = AnenOrange)
            TermText(
                text = reminder.placeName,
                color = if (reminder.enabled) AnenText else AnenTextDim,
                size = 15,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            val (labelRes, color) = when (reminder.status) {
                ReminderStatus.ARMED -> R.string.status_armed to AnenText
                ReminderStatus.TRIGGERED -> R.string.status_triggered to AnenOrangeDim
                ReminderStatus.DISABLED -> R.string.status_disabled to AnenTextDim
            }
            TermTag(stringResource(labelRes), color)
        }
        if (!reminder.address.isNullOrBlank()) {
            TermHint(reminder.address, modifier = Modifier.padding(top = 2.dp))
        }
        // 三个指标列等宽，跨条目对齐；标签加粗
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            MiniMetric(
                stringResource(R.string.label_radius),
                DistanceCalculator.formatRadius(reminder.radiusMeters),
                if (reminder.enabled) AnenTextDim else AnenLine,
                Modifier.weight(1f)
            )
            MiniMetric(
                stringResource(R.string.label_mode),
                stringResource(alertModeLabel(reminder.alertMode)),
                if (reminder.enabled) AnenTextDim else AnenLine,
                Modifier.weight(1f)
            )
            MiniMetric(
                stringResource(R.string.label_trigger),
                stringResource(if (reminder.repeat) R.string.trigger_repeat else R.string.trigger_once),
                if (reminder.enabled) AnenTextDim else AnenLine,
                Modifier.weight(1f)
            )
        }
        if (developerMode) {
            TermHint(
                text = "#${reminder.id} · %.6f / %.6f".format(reminder.latitude, reminder.longitude),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态开关：启用=橙底，停用=白底
            TermToggle(
                enabled = reminder.enabled,
                onToggle = { next -> onToggleEnabled(reminder, next) }
            )
            if (reminder.triggered) {
                TermButton(
                    text = stringResource(R.string.action_rearm),
                    onClick = { onRearm(reminder) },
                    primary = true
                )
            }
            TermButton(text = stringResource(R.string.action_edit), onClick = { onEdit(reminder) })
            TermButton(
                text = stringResource(R.string.action_delete),
                onClick = { onDelete(reminder) },
                muted = true
            )
        }
    }
}

@Composable
private fun MiniMetric(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = AnenTextDim,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TermLabel(label, bold = true)
        TermText(value, color = valueColor, size = 12)
    }
}
