package com.anen.spacealarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anen.spacealarm.R
import com.anen.spacealarm.amap.FailureReason
import com.anen.spacealarm.model.CoordinateSystem
import com.anen.spacealarm.model.Place
import com.anen.spacealarm.ui.components.TermButton
import com.anen.spacealarm.ui.components.TermCopyButton
import com.anen.spacealarm.ui.components.TermDataRow
import com.anen.spacealarm.ui.components.TermField
import com.anen.spacealarm.ui.components.TermFrame
import com.anen.spacealarm.ui.components.TermHeader
import com.anen.spacealarm.ui.components.TermHint
import com.anen.spacealarm.ui.components.TermLabel
import com.anen.spacealarm.ui.components.TermScreen
import com.anen.spacealarm.ui.components.TermSection
import com.anen.spacealarm.ui.components.TermText
import com.anen.spacealarm.ui.theme.AnenDanger
import com.anen.spacealarm.ui.theme.AnenLine
import com.anen.spacealarm.ui.theme.AnenOrange
import com.anen.spacealarm.ui.theme.AnenTextDim

sealed interface ShareUiState {
    data object Loading : ShareUiState
    data class Ready(val place: Place, val via: String) : ShareUiState
    data class Failed(val reason: FailureReason) : ShareUiState
}

/** 解析失败原因 → 用户可读文案。 */
fun failureText(context: android.content.Context, reason: FailureReason): String = when (reason) {
    FailureReason.EMPTY_PAYLOAD -> context.getString(R.string.resolve_failed_empty)
    FailureReason.NO_AMAP_LINK -> context.getString(R.string.resolve_failed_no_link)
    FailureReason.POI_ID_ONLY -> context.getString(R.string.resolve_failed_poi_only)
    FailureReason.FETCH_FAILED -> context.getString(R.string.resolve_failed_fetch)
}

/**
 * 分享地点解析结果页面。
 * 成功时先展示地点确认（名称 / 地址 / 坐标 / 来源），用户确认后再进入创建；
 * 失败时提供备用输入（手动坐标），开发者模式下额外显示原始载荷。
 */
@Composable
fun SharePlaceScreen(
    state: ShareUiState,
    payload: String,
    developerMode: Boolean,
    onUseLocation: () -> Unit,
    onManualInput: (name: String, coordinates: String, system: CoordinateSystem) -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    TermScreen {
        TermHeader(
            title = stringResource(R.string.app_title),
            subtitle = stringResource(R.string.share_subtitle),
            onBack = onCancel
        )

        when (state) {
            ShareUiState.Loading -> {
                TermSection("01", stringResource(R.string.resolving))
                TermFrame(modifier = Modifier.padding(horizontal = 14.dp)) {
                    TermText(stringResource(R.string.resolving), size = 13)
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = AnenOrange,
                        trackColor = AnenLine
                    )
                }
            }

            is ShareUiState.Failed -> {
                TermSection("01", stringResource(R.string.resolving))
                TermFrame(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    accent = AnenDanger
                ) {
                    TermText(failureText(context, state.reason), color = AnenDanger, size = 12)
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TermButton(text = stringResource(R.string.action_retry), onClick = onRetry, primary = true)
                        TermButton(text = stringResource(R.string.action_exit), onClick = onCancel)
                    }
                }

                ManualCoordinatePanel(onManualInput = onManualInput)
                if (developerMode) {
                    PayloadPanel(payload = payload)
                }
            }

            is ShareUiState.Ready -> {
                TermSection("01", stringResource(R.string.section_target_location))
                TermDataRow(stringResource(R.string.label_name), state.place.name, mono = false)
                if (!state.place.address.isNullOrBlank()) {
                    TermDataRow(stringResource(R.string.label_address), state.place.address, mono = false)
                }
                TermDataRow(stringResource(R.string.label_latitude), "%.6f N".format(state.place.originalLatitude))
                TermDataRow(stringResource(R.string.label_longitude), "%.6f E".format(state.place.originalLongitude))
                TermDataRow(
                    stringResource(R.string.label_source),
                    "${state.place.source} · ${state.place.coordinateSystem} · ${state.via}",
                    valueColor = AnenTextDim
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TermButton(text = stringResource(R.string.action_exit), onClick = onCancel)
                    TermButton(
                        text = stringResource(R.string.action_use_this_location),
                        onClick = onUseLocation,
                        primary = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (developerMode) {
                    PayloadPanel(payload = payload)
                }
            }
        }
    }
}

@Composable
private fun ManualCoordinatePanel(
    onManualInput: (name: String, coordinates: String, system: CoordinateSystem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var coordinates by remember { mutableStateOf("") }
    var system by remember { mutableStateOf(CoordinateSystem.GCJ02) }

    TermSection("02", stringResource(R.string.section_manual_input))
    TermFrame(modifier = Modifier.padding(horizontal = 14.dp)) {
        TermHint(stringResource(R.string.manual_input_hint))
        TermField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.padding(top = 8.dp),
            placeholder = stringResource(R.string.manual_name_placeholder)
        )
        TermField(
            value = coordinates,
            onValueChange = { coordinates = it },
            modifier = Modifier.padding(top = 8.dp),
            placeholder = stringResource(R.string.manual_coords_placeholder)
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CoordinateSystem.entries.forEach { candidate ->
                TermButton(
                    text = candidate.name,
                    onClick = { system = candidate },
                    primary = system == candidate
                )
            }
        }
        TermButton(
            text = stringResource(R.string.action_use_this_coordinate),
            onClick = { onManualInput(name.trim(), coordinates.trim(), system) },
            enabled = coordinates.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
    }
}

/** 开发者模式：原始分享载荷 + 一键复制。 */
@Composable
private fun PayloadPanel(payload: String) {
    var expanded by remember { mutableStateOf(false) }

    TermSection("03", stringResource(R.string.section_debug_payload))
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TermLabel(
                text = stringResource(
                    if (expanded) R.string.raw_share_intent else R.string.raw_share_intent_hidden
                ),
                modifier = Modifier.weight(1f)
            )
            TermCopyButton(text = { payload })
            TermButton(
                text = stringResource(if (expanded) R.string.action_hide else R.string.action_show),
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        if (expanded) {
            TermFrame(modifier = Modifier.padding(top = 8.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    TermText(
                        text = payload.ifBlank { stringResource(R.string.empty_payload) },
                        color = AnenTextDim,
                        size = 10
                    )
                }
            }
        }
    }
}
