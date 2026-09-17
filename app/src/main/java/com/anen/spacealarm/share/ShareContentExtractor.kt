package com.anen.spacealarm.share

import android.content.Intent

/**
 * 从 Intent 中抽取分享数据。不假设任何固定格式，只做完整收集。
 */
object ShareContentExtractor {

    fun extract(intent: Intent): ShareContent {
        val clipTexts = buildList {
            intent.clipData?.let { clipData ->
                for (index in 0 until clipData.itemCount) {
                    val item = clipData.getItemAt(index)
                    item.text?.toString()?.let(::add)
                    item.uri?.toString()?.let(::add)
                    item.htmlText?.toString()?.let(::add)
                }
            }
        }
        return ShareContent(
            action = intent.action,
            mimeType = intent.type,
            text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            title = intent.getStringExtra(Intent.EXTRA_TITLE)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
            htmlText = intent.getCharSequenceExtra(Intent.EXTRA_HTML_TEXT)?.toString(),
            dataUri = intent.data,
            clipTexts = clipTexts
        )
    }
}
