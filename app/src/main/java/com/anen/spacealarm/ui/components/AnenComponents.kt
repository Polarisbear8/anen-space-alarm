package com.anen.spacealarm.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anen.spacealarm.R
import com.anen.spacealarm.ui.theme.AnenBackground
import com.anen.spacealarm.ui.theme.AnenLine
import com.anen.spacealarm.ui.theme.AnenLineStrong
import com.anen.spacealarm.ui.theme.AnenOnOrange
import com.anen.spacealarm.ui.theme.AnenOrange
import com.anen.spacealarm.ui.theme.AnenText
import com.anen.spacealarm.ui.theme.AnenTextDim
import com.anen.spacealarm.ui.theme.MonoLabelStyle
import com.anen.spacealarm.ui.theme.MonoValueStyle

/**
 * ANEN 终端组件集。
 *
 * 视觉规则：
 *  - 方角（0-2dp），不使用大圆角与阴影
 *  - 1px 细线分隔，必要时四角刻度
 *  - 标签一律小号等宽大写，数值用等宽字体
 *  - 橙色只用于：当前状态、关键数字、主要操作、目标
 */

/** 页面根容器：深色底，可选滚动，底部避开手势导航区域。 */
@Composable
fun TermScreen(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AnenBackground)
            .then(
                if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
            )
            .navigationBarsPadding(),
        content = content
    )
}

/** 顶部终端标题条：背景延伸到状态栏后面，文字避开状态栏。 */
@Composable
fun TermHeader(
    title: String,
    subtitle: String,
    trailing: String? = null,
    onBack: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AnenBackground)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(26.dp)
                    .background(AnenOrange)
            )
            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    text = title,
                    color = AnenText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = subtitle.uppercase(),
                    color = AnenTextDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp
                )
            }
            if (trailing != null) {
                Text(
                    text = trailing,
                    color = AnenText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .border(1.dp, AnenLine)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_back),
                        color = AnenTextDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
        TermDivider()
    }
}

/** 分节标题：编号 + 名称 + 延伸细线。 */
@Composable
fun TermSection(index: String?, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index != null) {
            Text(
                text = index,
                color = AnenOrange,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = " / ",
                color = AnenLineStrong,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
        Text(
            text = label.uppercase(),
            color = AnenTextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 2.sp
        )
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f)
                .height(1.dp)
                .background(AnenLine)
        )
    }
}

@Composable
fun TermDivider(modifier: Modifier = Modifier, color: Color = AnenLine) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

@Composable
fun TermLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AnenTextDim,
    bold: Boolean = false
) {
    Text(
        text = text.uppercase(),
        style = MonoLabelStyle.copy(
            color = color,
            fontWeight = if (bold) FontWeight.Bold else MonoLabelStyle.fontWeight
        ),
        modifier = modifier
    )
}

@Composable
fun TermValue(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AnenText,
    size: Int = 20
) {
    Text(
        text = text,
        style = MonoValueStyle.copy(color = color, fontSize = size.sp),
        modifier = modifier
    )
}

@Composable
fun TermText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AnenText,
    size: Int = 14,
    weight: FontWeight = FontWeight.Normal
) {
    Text(text = text, color = color, fontSize = size.sp, fontWeight = weight, modifier = modifier)
}

@Composable
fun TermHint(text: String, modifier: Modifier = Modifier, color: Color = AnenTextDim) {
    Text(text = text, color = color, fontSize = 11.sp, lineHeight = 16.sp, modifier = modifier)
}

/** 标签 / 数值 一行，左侧小标签，右侧等宽数值。 */
@Composable
fun TermDataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AnenText,
    mono: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            color = AnenTextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.width(74.dp)
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.SansSerif,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 方角状态标签。 */
@Composable
fun TermTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, color, RoundedCornerShape(0.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp
        )
    }
}

/** 终端按钮：方角、细边；primary 使用橙色实心；muted 用于“停用”这类弱化动作。 */
@Composable
fun TermButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    muted: Boolean = false
) {
    val contentColor = when {
        !enabled -> AnenTextDim
        primary -> AnenOnOrange
        muted -> AnenTextDim
        else -> AnenText
    }
    val borderColor = when {
        !enabled -> AnenLine
        primary -> AnenOrange
        muted -> AnenLine
        else -> AnenLineStrong
    }
    Box(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(0.dp))
            .background(if (primary && enabled) AnenOrange else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = contentColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * 状态开关：已启用 = 橙底深字；已停用 = 白底灰框深字。点击切换。
 */
@Composable
fun TermToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled)
    Box(
        modifier = modifier
            .border(1.dp, if (enabled) AnenOrange else AnenLine, RoundedCornerShape(0.dp))
            .background(if (enabled) AnenOrange else Color.Transparent)
            .clickable { onToggle(!enabled) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) AnenOnOrange else AnenText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 分段选择：选中项为深底浅字，未选中为白底灰框深字，二元/多元状态一眼可辨。
 */
@Composable
fun TermSegment(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, if (selected) AnenText else AnenLine, RoundedCornerShape(0.dp))
                    .background(if (selected) AnenText else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) AnenBackground else AnenText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/** 选择块（半径等）。 */
@Composable
fun TermChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) AnenOrange else AnenLine
    val contentColor = if (selected) AnenOrange else AnenTextDim
    Box(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(0.dp))
            .background(if (selected) AnenOrange.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp
        )
    }
}

/** 方角输入框（替代 Material 圆角 TextField）。 */
@Composable
fun TermField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    maxLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = AnenTextDim, fontSize = 13.sp) },
        singleLine = singleLine,
        maxLines = maxLines,
        shape = RoundedCornerShape(0.dp),
        textStyle = MonoValueStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AnenOrange,
            unfocusedBorderColor = AnenLine,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = AnenOrange,
            focusedTextColor = AnenText,
            unfocusedTextColor = AnenText,
            focusedPlaceholderColor = AnenTextDim,
            unfocusedPlaceholderColor = AnenTextDim
        )
    )
}

/** 带四角刻度的面板（替代大圆角 Card）。 */
@Composable
fun TermFrame(
    modifier: Modifier = Modifier,
    accent: Color = AnenLine,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val tick = 10.dp.toPx()
                val stroke = 1.dp.toPx()
                val w = size.width
                val h = size.height
                val corners = listOf(
                    Offset(0f, 0f) to Offset(tick, 0f),
                    Offset(0f, 0f) to Offset(0f, tick),
                    Offset(w, 0f) to Offset(w - tick, 0f),
                    Offset(w, 0f) to Offset(w, tick),
                    Offset(0f, h) to Offset(tick, h),
                    Offset(0f, h) to Offset(0f, h - tick),
                    Offset(w, h) to Offset(w - tick, h),
                    Offset(w, h) to Offset(w, h - tick)
                )
                corners.forEach { (start, end) ->
                    drawLine(color = accent, start = start, end = end, strokeWidth = stroke)
                }
            }
            .border(1.dp, AnenLine, RoundedCornerShape(0.dp))
            .padding(12.dp),
        content = content
    )
}

/** 底部操作条：避开手势导航区域。 */
@Composable
fun TermActionBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AnenBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/**
 * 调试信息复制按钮：只有图标，点击写入剪贴板并给出短暂反馈。
 */
@Composable
fun TermCopyButton(
    text: () -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val description = stringResource(R.string.action_copy_debug)
    val copied = stringResource(R.string.action_copied)
    Box(
        modifier = modifier
            .size(28.dp)
            .border(1.dp, AnenLine, RoundedCornerShape(0.dp))
            .clickable {
                clipboard.setText(AnnotatedString(text()))
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⧉",
            color = AnenTextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp
        )
    }
}
