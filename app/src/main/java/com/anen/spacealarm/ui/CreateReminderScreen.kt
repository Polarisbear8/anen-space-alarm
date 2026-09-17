package com.anen.spacealarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anen.spacealarm.R
import com.anen.spacealarm.location.DistanceCalculator
import com.anen.spacealarm.model.AlertMode
import com.anen.spacealarm.model.Place
import com.anen.spacealarm.model.Reminder
import com.anen.spacealarm.ui.components.TermButton
import com.anen.spacealarm.ui.components.TermChip
import com.anen.spacealarm.ui.components.TermDataRow
import com.anen.spacealarm.ui.components.TermDivider
import com.anen.spacealarm.ui.components.TermField
import com.anen.spacealarm.ui.components.TermFrame
import com.anen.spacealarm.ui.components.TermHeader
import com.anen.spacealarm.ui.components.TermHint
import com.anen.spacealarm.ui.components.TermLabel
import com.anen.spacealarm.ui.components.TermScreen
import com.anen.spacealarm.ui.components.TermSection
import com.anen.spacealarm.ui.components.TermSegment
import com.anen.spacealarm.ui.components.TermText
import com.anen.spacealarm.ui.theme.AnenDanger
import com.anen.spacealarm.ui.theme.AnenLine
import com.anen.spacealarm.ui.theme.AnenOrange
import com.anen.spacealarm.ui.theme.AnenText
import com.anen.spacealarm.ui.theme.AnenTextDim
import com.anen.spacealarm.ui.theme.AnenWarn

private val PRESET_RADII = listOf(100f, 200f, 500f, 1000f, 2000f)

/**
 * 地点输入入口：粘贴高德链接（可多行，例如从高德复制出来的“名称 + 地址 + 短链”）。
 */
@Composable
fun NewPlaceScreen(
    busy: Boolean,
    error: String?,
    onResolveLink: (String) -> Unit,
    onBack: () -> Unit
) {
    var link by remember { mutableStateOf("") }

    TermScreen {
        TermHeader(
            title = stringResource(R.string.new_alarm_title),
            subtitle = stringResource(R.string.place_input_subtitle),
            onBack = onBack
        )

        TermSection("01", stringResource(R.string.section_paste_link))
        TermFrame(modifier = Modifier.padding(horizontal = 14.dp)) {
            TermHint(stringResource(R.string.paste_link_hint))
            TermField(
                value = link,
                onValueChange = { link = it },
                modifier = Modifier.padding(top = 8.dp),
                placeholder = "https://surl.amap.com/...",
                singleLine = false,
                maxLines = 6
            )
            TermButton(
                text = stringResource(R.string.action_resolve),
                onClick = { onResolveLink(link.trim()) },
                enabled = link.isNotBlank() && !busy,
                primary = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }

        if (busy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                color = AnenOrange,
                trackColor = AnenLine
            )
        }
        if (error != null) {
            TermFrame(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                accent = AnenDanger
            ) {
                TermText(error, color = AnenDanger, size = 12)
            }
        }

        Box(modifier = Modifier.padding(14.dp)) {
            TermButton(text = stringResource(R.string.action_back_to_map), onClick = onBack)
        }
    }
}

/**
 * 创建 / 编辑空间闹钟：地点 → 半径 → 提醒方式 → 触发方式 → 消息 → 保存。
 * 传入 [existing] 即为编辑模式，所有设置都可随时调整。
 */
@Composable
fun CreateReminderScreen(
    place: Place,
    existing: Reminder? = null,
    busy: Boolean,
    error: String?,
    warning: String?,
    onActivate: (radiusMeters: Float, mode: AlertMode, message: String, repeat: Boolean) -> Unit,
    onBack: () -> Unit
) {
    val editing = existing != null
    var radius by remember { mutableFloatStateOf(existing?.radiusMeters ?: 500f) }
    var customSelected by remember {
        mutableStateOf(existing != null && existing.radiusMeters !in PRESET_RADII)
    }
    var customRadiusText by remember {
        mutableStateOf(
            if (existing != null && existing.radiusMeters !in PRESET_RADII) {
                existing.radiusMeters.toInt().toString()
            } else {
                ""
            }
        )
    }
    var mode by remember { mutableStateOf(existing?.alertMode ?: AlertMode.NOTIFICATION) }
    var repeat by remember { mutableStateOf(existing?.repeat ?: false) }
    var message by remember { mutableStateOf(existing?.message ?: "") }

    val effectiveRadius = if (customSelected) customRadiusText.toFloatOrNull() ?: 0f else radius
    val radiusValid = effectiveRadius in 50f..50000f
    val defaultMessage = stringResource(R.string.default_message)

    TermScreen {
        TermHeader(
            title = stringResource(if (editing) R.string.edit_alarm_title else R.string.new_alarm_title),
            subtitle = stringResource(if (editing) R.string.edit_alarm_subtitle else R.string.configure_subtitle),
            onBack = onBack
        )

        TermSection("01", stringResource(R.string.label_target))
        TermDataRow(stringResource(R.string.label_name), place.name, mono = false)
        if (!place.address.isNullOrBlank()) {
            TermDataRow(stringResource(R.string.label_address), place.address, mono = false)
        }
        TermDataRow(stringResource(R.string.label_latitude), "%.6f".format(place.latitude))
        TermDataRow(stringResource(R.string.label_longitude), "%.6f".format(place.longitude))
        TermDataRow(
            stringResource(R.string.label_source),
            "${place.coordinateSystem} · ${place.source}${stringResource(R.string.coordinate_internal_note)}",
            valueColor = AnenTextDim
        )

        TermSection("02", stringResource(R.string.section_range))
        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PRESET_RADII.forEach { preset ->
                    TermChip(
                        label = DistanceCalculator.formatRadius(preset),
                        selected = !customSelected && radius == preset,
                        onClick = {
                            radius = preset
                            customSelected = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TermChip(
                    label = stringResource(R.string.radius_custom),
                    selected = customSelected,
                    onClick = { customSelected = true }
                )
                if (customSelected) {
                    TermField(
                        value = customRadiusText,
                        onValueChange = { input -> customRadiusText = input.filter { it.isDigit() } },
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                        placeholder = stringResource(R.string.radius_custom_placeholder)
                    )
                }
            }
            TermHint(stringResource(R.string.radius_hint), modifier = Modifier.padding(top = 8.dp))
        }

        TermSection("03", stringResource(R.string.section_alert_mode))
        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            AlertModeRow(
                label = stringResource(R.string.mode_notification),
                description = stringResource(R.string.alert_notification_desc),
                selected = mode == AlertMode.NOTIFICATION,
                onSelect = { mode = AlertMode.NOTIFICATION }
            )
            AlertModeRow(
                label = stringResource(R.string.mode_vibration),
                description = stringResource(R.string.alert_vibration_desc),
                selected = mode == AlertMode.VIBRATION,
                onSelect = { mode = AlertMode.VIBRATION }
            )
            AlertModeRow(
                label = stringResource(R.string.mode_alarm),
                description = stringResource(R.string.alert_alarm_desc),
                selected = mode == AlertMode.ALARM,
                onSelect = { mode = AlertMode.ALARM }
            )
        }

        TermSection("04", stringResource(R.string.label_trigger))
        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            TermSegment(
                options = listOf(
                    stringResource(R.string.trigger_once),
                    stringResource(R.string.trigger_repeat)
                ),
                selectedIndex = if (repeat) 1 else 0,
                onSelect = { index -> repeat = index == 1 }
            )
            TermHint(
                stringResource(if (repeat) R.string.trigger_repeat_hint else R.string.trigger_once_hint),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        TermSection("05", stringResource(R.string.section_message))
        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            TermField(
                value = message,
                onValueChange = { message = it },
                placeholder = defaultMessage
            )
        }

        if (warning != null) {
            TermFrame(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                accent = AnenWarn
            ) {
                TermText(warning, color = AnenWarn, size = 11)
            }
        }
        if (error != null) {
            TermFrame(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                accent = AnenDanger
            ) {
                TermText(error, color = AnenDanger, size = 12)
            }
        }

        if (editing) {
            TermHint(
                stringResource(R.string.edit_radius_changed_hint),
                modifier = Modifier.padding(start = 14.dp, top = 10.dp)
            )
        }

        TermDivider(modifier = Modifier.padding(top = 14.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TermButton(text = stringResource(R.string.action_cancel), onClick = onBack)
            TermButton(
                text = when {
                    busy && editing -> stringResource(R.string.action_saving)
                    busy -> stringResource(R.string.action_arming)
                    editing -> stringResource(R.string.action_save)
                    else -> stringResource(R.string.action_arm)
                },
                onClick = { onActivate(effectiveRadius, mode, message.ifBlank { defaultMessage }, repeat) },
                enabled = radiusValid && !busy,
                primary = true,
                modifier = Modifier.weight(1f)
            )
        }
        TermHint(
            text = if (radiusValid) {
                stringResource(R.string.system_status_ready)
            } else {
                stringResource(R.string.system_status_invalid_range)
            },
            color = if (radiusValid) AnenTextDim else AnenDanger,
            modifier = Modifier.padding(start = 14.dp, bottom = 16.dp)
        )
    }
}

@Composable
private fun AlertModeRow(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 7.dp),
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
        Column(modifier = Modifier.padding(start = 10.dp)) {
            TermLabel(label, color = if (selected) AnenOrange else AnenText)
            TermHint(description)
        }
    }
}
