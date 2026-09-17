package com.anen.spacealarm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ANEN 工业终端配色：灰白底 + 深炭文字 + 少量橙色强调。
 * 只改这一处即可整体换色，其余代码一律引用 token。
 */
val AnenOrange = Color(0xFFFF6A00)
val AnenOrangeDim = Color(0xFFB34A00)

/** 橙色底上的文字色（固定深色，不随主题变化）。 */
val AnenOnOrange = Color(0xFF1A1000)

/** 页面底色：灰白。 */
val AnenBackground = Color(0xFFF1F1EE)

/** 次级面板 / 数据条底色。 */
val AnenDeep = Color(0xFFE4E4E0)

/** 常规面板底色。 */
val AnenPanel = Color(0xFFF9F9F7)
val AnenPanelAlt = Color(0xFFEAEAE6)

/** 细线与强线。 */
val AnenLine = Color(0xFFC9C9C3)
val AnenLineStrong = Color(0xFFA6A6A0)

val AnenText = Color(0xFF1B1B1D)
val AnenTextDim = Color(0xFF6F6F73)
val AnenDanger = Color(0xFFC0392B)
val AnenWarn = Color(0xFFA8760A)

/** 覆盖在图片/照片上的固定深色蒙版与其上的文字色。 */
val AnenScrim = Color(0xFF16161A)
val AnenOnScrim = Color(0xFFE6E3DC)

private val AnenColorScheme = lightColorScheme(
    primary = AnenOrange,
    onPrimary = AnenOnOrange,
    primaryContainer = AnenOrangeDim,
    onPrimaryContainer = AnenText,
    secondary = AnenTextDim,
    onSecondary = AnenBackground,
    background = AnenBackground,
    onBackground = AnenText,
    surface = AnenPanel,
    onSurface = AnenText,
    surfaceVariant = AnenPanelAlt,
    onSurfaceVariant = AnenTextDim,
    outline = AnenLine,
    error = AnenDanger,
    onError = Color.White
)

/** 英文标签统一使用等宽字体，中文内容使用系统无衬线字体。 */
val MonoLabelStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 10.sp,
    letterSpacing = 2.sp,
    fontWeight = FontWeight.Medium,
    color = AnenTextDim
)

val MonoValueStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 20.sp,
    letterSpacing = 0.5.sp,
    fontWeight = FontWeight.SemiBold,
    color = AnenText
)

@Composable
fun AnenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AnenColorScheme, content = content)
}
