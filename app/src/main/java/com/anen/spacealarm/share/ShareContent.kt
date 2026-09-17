package com.anen.spacealarm.share

import android.net.Uri

/**
 * 统一的分享载荷。
 *
 * 不假设任何发送方（高德 / 浏览器 / 其他 App）遵循同一种结构，
 * 把 Intent 里所有可能承载地点的字段都收集起来，交给 Resolver 逐级解析。
 */
data class ShareContent(
    val action: String?,
    val mimeType: String?,
    val text: String?,
    val title: String?,
    val htmlText: String?,
    val dataUri: Uri?,
    /** 剪贴板条目里的文本与 URI（都当作候选文本） */
    val clipTexts: List<String>
) {
    /** 所有可能包含地点信息的字符串，按可信度排序。 */
    fun allTextCandidates(): List<String> {
        val candidates = buildList {
            text?.let(::add)
            title?.let(::add)
            htmlText?.let(::add)
            dataUri?.toString()?.let(::add)
            addAll(clipTexts)
        }
        return candidates
            .map { it.replace("&amp;", "&").trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
}
