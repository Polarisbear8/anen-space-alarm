package com.anen.spacealarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anen.spacealarm.R
import com.anen.spacealarm.ui.components.TermButton
import com.anen.spacealarm.ui.components.TermFrame
import com.anen.spacealarm.ui.components.TermHeader
import com.anen.spacealarm.ui.components.TermHint
import com.anen.spacealarm.ui.components.TermScreen
import com.anen.spacealarm.ui.components.TermSection
import com.anen.spacealarm.ui.components.TermText
import com.anen.spacealarm.ui.theme.AnenLine
import com.anen.spacealarm.ui.theme.AnenOrange
import com.anen.spacealarm.ui.theme.AnenText
import com.anen.spacealarm.ui.theme.AnenTextDim

/**
 * 使用教程。首次打开 App 时显示，也可以在设置里随时重温。
 */
@Composable
fun TutorialScreen(
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf(
        R.string.tutorial_step1_title to R.string.tutorial_step1_body,
        R.string.tutorial_step2_title to R.string.tutorial_step2_body,
        R.string.tutorial_step3_title to R.string.tutorial_step3_body,
        R.string.tutorial_step4_title to R.string.tutorial_step4_body
    )
    val lastStep = step == steps.lastIndex

    TermScreen {
        TermHeader(
            title = stringResource(R.string.tutorial_title),
            subtitle = stringResource(R.string.tutorial_subtitle),
            trailing = "%02d / %02d".format(step + 1, steps.size),
            onBack = onBack
        )

        TermSection(
            index = "%02d".format(step + 1),
            label = stringResource(steps[step].first)
        )
        TermFrame(modifier = Modifier.padding(horizontal = 14.dp)) {
            TermText(
                text = stringResource(steps[step].second),
                color = AnenText,
                size = 13
            )
        }

        // 步骤指示
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == step) 10.dp else 6.dp)
                        .background(if (index <= step) AnenOrange else AnenLine)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) {
                TermButton(
                    text = stringResource(R.string.tutorial_prev),
                    onClick = { step-- },
                    muted = true
                )
            }
            TermButton(
                text = if (lastStep) {
                    stringResource(R.string.tutorial_start)
                } else {
                    stringResource(R.string.tutorial_next)
                },
                onClick = {
                    if (lastStep) onDone() else step++
                },
                primary = true,
                modifier = Modifier.weight(1f)
            )
            if (!lastStep) {
                TermButton(
                    text = stringResource(R.string.tutorial_skip),
                    onClick = onDone,
                    muted = true
                )
            }
        }

        TermHint(
            stringResource(R.string.tutorial_footer),
            color = AnenTextDim,
            modifier = Modifier.padding(start = 14.dp, bottom = 16.dp)
        )
    }
}
