package com.anen.spacealarm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anen.spacealarm.BuildConfig
import com.anen.spacealarm.R
import com.anen.spacealarm.ui.components.TermDataRow
import com.anen.spacealarm.ui.components.TermDivider
import com.anen.spacealarm.ui.components.TermHeader
import com.anen.spacealarm.ui.components.TermHint
import com.anen.spacealarm.ui.components.TermLabel
import com.anen.spacealarm.ui.components.TermScreen
import com.anen.spacealarm.ui.components.TermSection
import com.anen.spacealarm.ui.components.TermText
import com.anen.spacealarm.ui.theme.AnenOrange
import com.anen.spacealarm.ui.theme.AnenText
import com.anen.spacealarm.ui.theme.AnenTextDim

/**
 * About：版本信息 + 开源致谢。
 *
 * 致谢措辞只使用 Referenced / 参考，不声称 Based on。
 *
 * 隐藏彩蛋：连续点击版本号 3 次（间隔不超过 1 秒）触发 [onUnlockEasterEgg]。
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onUnlockEasterEgg: () -> Unit = {}) {
    var versionTaps by remember { mutableStateOf(0) }
    var lastTapAt by remember { mutableStateOf(0L) }

    TermScreen {
        TermHeader(
            title = stringResource(R.string.about_title),
            subtitle = stringResource(R.string.about_subtitle),
            onBack = onBack
        )

        TermSection("01", stringResource(R.string.about_version_section))
        TermDataRow(stringResource(R.string.about_app), stringResource(R.string.app_name))
        TermDataRow(
            stringResource(R.string.dev_label_version),
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            modifier = Modifier.clickable {
                val now = System.currentTimeMillis()
                versionTaps = if (now - lastTapAt > 1000L) 1 else versionTaps + 1
                lastTapAt = now
                if (versionTaps >= EasterEgg.TAPS_REQUIRED) {
                    versionTaps = 0
                    onUnlockEasterEgg()
                }
            }
        )
        TermDataRow(stringResource(R.string.dev_label_package), BuildConfig.APPLICATION_ID)
        TermDataRow(
            stringResource(R.string.dev_label_android),
            "${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})"
        )
        TermHint(stringResource(R.string.app_description), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))

        TermSection("02", stringResource(R.string.about_acknowledgements))
        Credit("MapLibre Native Android", stringResource(R.string.credit_maplibre), "https://maplibre.org/")
        Credit("GeoShare", stringResource(R.string.credit_geoshare), "https://github.com/jakubvalenta/geoshare")
        Credit("tAlarm", stringResource(R.string.credit_talarm), "https://github.com/timbogdpro/tAlarm")
        Credit("Brutus", stringResource(R.string.credit_brutus), "https://github.com/pepperonas/brutus")
        Credit("OpenStreetMap", stringResource(R.string.credit_osm), "https://www.openstreetmap.org/")
        Credit("OpenFreeMap", stringResource(R.string.credit_openfreemap), "https://openfreemap.org/")

        TermSection("03", stringResource(R.string.about_notes))
        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            TermHint(stringResource(R.string.about_notes_1))
            TermHint(stringResource(R.string.about_notes_3))
        }

        TermDivider(modifier = Modifier.padding(top = 16.dp))

        Column(modifier = Modifier.padding(14.dp)) {
            TermText(
                text = stringResource(R.string.about_open_source_spirit),
                color = AnenOrange,
                size = 11,
                weight = FontWeight.Bold
            )
            TermHint(stringResource(R.string.about_open_source_note))
        }
    }
}

@Composable
private fun Credit(name: String, note: String, url: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        TermText(name, color = AnenText, size = 14, weight = FontWeight.Medium)
        TermHint(note)
        TermText(text = url, color = AnenTextDim, size = 10)
    }
}
