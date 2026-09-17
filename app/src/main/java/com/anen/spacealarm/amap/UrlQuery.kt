package com.anen.spacealarm.amap

import java.net.URLDecoder

/**
 * 轻量 URL/查询参数工具。不依赖 android.net.Uri，便于在 JVM 单元测试中直接验证。
 */
internal object UrlQuery {

    private val urlRegex = Regex(
        """(?:https?://|amapuri://|androidamap://|iosamap://)[^\s"'<>\[\]{}，。；、]+""",
        RegexOption.IGNORE_CASE
    )
    private val hostRegex = Regex("""^[a-zA-Z][a-zA-Z0-9+.\-]*://([^/?#]+)""")
    private val pairRegex = Regex("""(-?\d{1,3}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)""")

    private val trailingChars = charArrayOf('.', ',', ';', '，', '。', '；', '、', '】', ']')

    fun findUrls(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return urlRegex.findAll(text)
            .map { trimTrailing(it.value) }
            .filter { it.length > 8 }
            .distinct()
            .toList()
    }

    /**
     * 去掉 URL 末尾的标点；括号只在“右括号多于左括号”时裁剪，
     * 以免破坏 name=北山(公交站) 这类带括号的地点名。
     */
    private fun trimTrailing(raw: String): String {
        var value = raw.trimEnd(*trailingChars)
        while (value.endsWith(')') && value.count { it == ')' } > value.count { it == '(' }) {
            value = value.dropLast(1).trimEnd(*trailingChars)
        }
        return value
    }

    fun isUrlLike(text: String): Boolean =
        text.startsWith("http://", true) ||
            text.startsWith("https://", true) ||
            text.startsWith("amapuri://", true) ||
            text.startsWith("androidamap://", true) ||
            text.startsWith("iosamap://", true)

    fun host(url: String): String? {
        val match = hostRegex.find(url) ?: return null
        return match.groupValues[1].substringBefore('@').substringBefore(':').lowercase().ifBlank { null }
    }

    fun path(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val slash = afterScheme.indexOf('/')
        if (slash < 0) return "/"
        return afterScheme.substring(slash).substringBefore('?').substringBefore('#')
    }

    fun parseQuery(url: String): Map<String, String> {
        val question = url.indexOf('?')
        val result = LinkedHashMap<String, String>()
        if (question >= 0) collect(url.substring(question + 1), result)
        val hash = url.indexOf('#')
        if (hash >= 0) collect(url.substring(hash + 1), result)
        return result
    }

    private fun collect(raw: String, into: MutableMap<String, String>) {
        val end = raw.indexOf('#')
        val query = if (end >= 0) raw.substring(0, end) else raw
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            val key = decode(if (eq >= 0) pair.substring(0, eq) else pair).lowercase()
            val value = decode(if (eq >= 0) pair.substring(eq + 1) else "")
            if (key.isNotEmpty() && !into.containsKey(key)) into[key] = value
        }
    }

    /** 解析 "lng,lat" / "lat,lng" 数值对，按 Amap 约定优先，必要时自动纠正顺序。 */
    fun coordinatePair(value: String?): LngLat? {
        if (value.isNullOrBlank()) return null
        val match = pairRegex.find(value) ?: return null
        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val b = match.groupValues[2].toDoubleOrNull() ?: return null
        return normalizeLngLat(a, b)
    }

    /**
     * Amap 约定为 "经度,纬度"。当顺序明显是 "纬度,经度" 时自动纠正；
     * 无法判断或超出范围时返回 null，宁可失败也不产生错误坐标。
     */
    fun normalizeLngLat(a: Double, b: Double): LngLat? {
        val aIsChinaLng = a in 70.0..140.0
        val aIsChinaLat = a in 0.0..60.0
        val bIsChinaLng = b in 70.0..140.0
        val bIsChinaLat = b in 0.0..60.0
        return when {
            aIsChinaLng && bIsChinaLat -> LngLat(a, b)
            aIsChinaLat && bIsChinaLng -> LngLat(b, a)
            a in -180.0..180.0 && b in -90.0..90.0 -> LngLat(a, b)
            else -> null
        }
    }

    fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: Exception) {
        value
    }
}
