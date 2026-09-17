package com.anen.spacealarm.share

import android.content.Intent
import android.util.Log

/**
 * 分享载荷记录。
 *
 * 解析失败时必须让开发者能看到完整原始数据（测试项 A5），
 * 所以这里同时提供：
 *  - log(content)：规范化的 AMAP SHARE DEBUG 日志（用于真机抓真实 Intent）
 *  - describe(intent)：完整原始载荷文本（用于 App 内的调试面板）
 */
object ShareDebugLogger {

    private const val TAG = "AmapShareDebug"

    fun log(content: ShareContent) {
        Log.d(TAG, "===== AMAP SHARE DEBUG =====")
        Log.d(TAG, "action=${content.action}")
        Log.d(TAG, "mimeType=${content.mimeType}")
        Log.d(TAG, "dataUri=${content.dataUri}")
        Log.d(TAG, "text=${content.text}")
        Log.d(TAG, "title=${content.title}")
        Log.d(TAG, "htmlText=${content.htmlText}")
        Log.d(TAG, "clipTexts=${content.clipTexts}")
        Log.d(TAG, "============================")
    }

    @Suppress("DEPRECATION")
    fun describe(intent: Intent): String = buildString {
        appendLine("ACTION : ${intent.action}")
        appendLine("MIME   : ${intent.type}")
        appendLine("DATA   : ${intent.data}")
        appendLine("TEXT   : ${intent.getCharSequenceExtra(Intent.EXTRA_TEXT)}")
        appendLine("TITLE  : ${intent.getStringExtra(Intent.EXTRA_TITLE)}")
        appendLine("HTML   : ${intent.getCharSequenceExtra(Intent.EXTRA_HTML_TEXT)}")
        val clip = intent.clipData
        if (clip == null) {
            appendLine("CLIP   : null")
        } else {
            appendLine("CLIP   : ${clip.itemCount} item(s)")
            for (index in 0 until clip.itemCount) {
                val item = clip.getItemAt(index)
                appendLine("  [$index] text = ${item.text}")
                appendLine("  [$index] uri  = ${item.uri}")
                appendLine("  [$index] html = ${item.htmlText}")
            }
        }
        val extras = intent.extras
        if (extras == null) {
            appendLine("EXTRAS : null")
        } else {
            appendLine("EXTRAS : ${extras.keySet().size} key(s)")
            for (key in extras.keySet()) {
                if (key == Intent.EXTRA_TEXT || key == Intent.EXTRA_HTML_TEXT) continue
                val value = extras.get(key)
                val rendered = when (value) {
                    null -> "null"
                    is CharSequence -> value.toString()
                    is Number, is Boolean -> value.toString()
                    else -> value.javaClass.simpleName
                }
                appendLine("  $key = ${rendered.take(300)}")
            }
        }
    }.trim()
}
