package com.anen.spacealarm.amap

import android.util.Log
import com.anen.spacealarm.model.CoordinateSystem
import com.anen.spacealarm.model.PlaceSource
import com.anen.spacealarm.share.ShareContent
import okhttp3.OkHttpClient

/**
 * 高德解析总入口，分级 fallback（能在本地解决就不联网，能静态解析就不开 WebView）：
 *
 *   L1   amapuri:// / androidamap:// / uri.amap.com 等 URI 与 URL 参数（优先，能带地点名）
 *   L0   分享载荷里直接存在的裸坐标
 *   L2A  高德分享短链：surl.amap.com → 跳转 → wb.amap.com → p=POI_ID,lat,lon,name
 *   L2B  其它允许的 HTTP 重定向（只允许白名单主机）
 *   L3   静态 HTML / JSON / 内嵌状态（含「地理坐标：lat,lon」文本）
 *   L4   WebView 渲染后取文本（仅在静态内容里没有坐标时启用）
 *   → 失败：返回原因码，由 UI 映射成文案并提供手动输入兜底
 *
 * 刻意不依赖高德私有接口（detail/get/detail 之类），也不使用任何 API Key。
 */
class AMapResolver(
    private val client: OkHttpClient,
    private val webViewResolver: AMapWebViewResolver? = null,
    private val trustedHosts: List<String> = AMapHosts.HOSTS
) : MapResolver {

    private val redirectResolver = AMapRedirectResolver(client, trustedHosts)

    override suspend fun resolve(content: ShareContent): ResolveResult {
        val trace = mutableListOf<String>()

        val candidates = content.allTextCandidates()
        if (candidates.isEmpty()) {
            trace += "PAYLOAD EMPTY"
            return ResolveResult.Failure(FailureReason.EMPTY_PAYLOAD, failureReport(FailureReason.EMPTY_PAYLOAD, trace))
        }

        // L1：本地 URI / URL 解析（优先：URL 解析能同时拿到地点名）
        for (raw in candidates) {
            for (url in urlsOf(raw)) {
                localResolve(url)?.let { return success(it, "1", trace) }
            }
        }
        trace += "L1 MISS"

        // L0：载荷中的裸坐标（URL 解析失败后再兜底扫描）
        for (raw in candidates) {
            directCoordinate(raw)?.let { return success(it, "0", trace) }
        }
        trace += "L0 MISS"

        // L2A / L2B / L3
        val attempted = mutableListOf<String>()
        var sawPlaceIdOnly = false
        for (raw in candidates) {
            for (url in urlsOf(raw)) {
                if (!isTrustedHost(url)) continue
                attempted += url
                if (AMapPlaceUrlResolver.matchPlaceId(url) != null) sawPlaceIdOnly = true
                networkResolve(url, trace)?.let { return success(it, "2A/2B/3", trace) }
            }
        }

        // L4：WebView 兜底（页面由 JS 渲染时才需要）
        if (webViewResolver != null && attempted.isNotEmpty()) {
            for (url in webViewRenderTargets(attempted)) {
                val page = webViewResolver.render(url) ?: continue
                webViewPlace(page)?.let { return success(it, "4", trace) }
            }
            trace += "L4 MISS"
        }

        val reason = when {
            attempted.isEmpty() -> FailureReason.NO_AMAP_LINK
            sawPlaceIdOnly -> FailureReason.POI_ID_ONLY
            else -> FailureReason.FETCH_FAILED
        }
        return ResolveResult.Failure(reason, failureReport(reason, trace))
    }

    /** 本地解析：URI / 参数 / URL 内嵌坐标。 */
    private fun localResolve(url: String): AMapLocation? {
        AMapUriResolver.resolve(url, "amap-uri")?.let { return it }
        AMapPlaceUrlResolver.resolveInline(url, "place-url")?.let { return it }
        if (isTrustedHost(url)) DirectCoordinate.extract(url)?.let { return it }
        return null
    }

    /** 联网解析：L2A 短链 p 参数 → L2B 重定向链 → L3 静态 HTML / JSON。只请求白名单主机。 */
    private suspend fun networkResolve(url: String, trace: MutableList<String>): AMapLocation? {
        val chain = redirectResolver.follow(url)

        for (hop in chain.hops) {
            AMapPoiParamResolver.parse(hop)?.let {
                trace += "L2A HIT host=${UrlQuery.host(hop)}"
                return it
            }
            localResolve(hop)?.let {
                trace += "L2B HIT"
                return it
            }
        }
        trace += if (chain.hops.size > 1) "L2A/L2B MISS hops=${chain.hops.size}" else "L2 MISS"

        chain.finalBody?.let { html ->
            AMapHtmlResolver.extract(html, chain.finalUrl)?.let {
                trace += "L3 HIT"
                return it
            }
        }
        trace += "L3 MISS"
        return null
    }

    /**
     * Level 4 的渲染目标：原 URL；如果是 amap 地点页，再补一个移动版（页面更轻、更容易渲染出内容）。
     */
    private fun webViewRenderTargets(urls: List<String>): List<String> {
        val targets = mutableListOf<String>()
        for (url in urls) {
            if (url !in targets) targets += url
            val poiId = AMapPlaceUrlResolver.matchPlaceId(url)
            if (poiId != null) {
                val mobile = "https://m.amap.com/place/$poiId"
                if (mobile !in targets) targets += mobile
            }
        }
        return targets
    }

    /** Level 4 的结果转换：优先用页面标题作为地点名。 */
    private fun webViewPlace(page: AMapWebViewResolver.RenderedPage): AMapLocation? {
        val coordinate = AMapHtmlResolver.extractFromText(page.text)
            ?: AMapHtmlResolver.extractFromText(page.matches.joinToString("\n"))
            ?: return null
        val name = page.title?.let { AMapHtmlResolver.cleanTitle(it) }
            ?: AMapHtmlResolver.extractName(page.text)
        return AMapLocation(
            name = name,
            address = AMapHtmlResolver.extractAddress(page.text),
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            coordinateSystem = CoordinateSystem.GCJ02,
            source = PlaceSource.AMAP_PLACE_PAGE,
            poiId = null,
            via = "webview"
        )
    }

    private fun success(location: AMapLocation, level: String, trace: List<String>): ResolveResult {
        val place = location.toPlace()
        Log.d(TAG, "level=$level result=coordinate lat=${location.latitude} lon=${location.longitude}")
        Log.d(TAG, "result=success source=${place.source} coordinateSystem=${place.coordinateSystem}")
        return ResolveResult.Success(place, location.via, successReport(location, level, trace))
    }

    private fun urlsOf(raw: String): List<String> {
        val trimmed = raw.trim()
        val urls = UrlQuery.findUrls(raw).toMutableList()
        if (UrlQuery.isUrlLike(trimmed) && trimmed !in urls) urls.add(0, trimmed)
        return urls.distinct()
    }

    /**
     * Level 0 只处理“裸坐标”（例如分享文本里直接写 113.268932,23.081058）。
     * URL 一律交给 Level 1：URL 解析还能同时拿到地点名，信息更完整。
     */
    private fun directCoordinate(raw: String): AMapLocation? {
        if (UrlQuery.isUrlLike(raw.trim())) return null
        val hasAmapContext = !raw.contains("://") ||
            raw.contains("高德", ignoreCase = true) ||
            raw.contains("amap", ignoreCase = true)
        return if (hasAmapContext) DirectCoordinate.extract(raw) else null
    }

    private fun isTrustedHost(url: String): Boolean = AMapHosts.isTrusted(url, trustedHosts)

    private fun successReport(location: AMapLocation, level: String, trace: List<String>): String = buildString {
        appendLine("=== AMAP RESOLVER ===")
        appendLine("Result: SUCCESS")
        appendLine("Level: L$level")
        appendLine("Via: ${location.via}")
        appendLine("Source: ${location.source}")
        appendLine("CoordinateSystem: ${location.coordinateSystem}")
        location.poiId?.let { appendLine("POI ID: $it") }
        location.name?.let { appendLine("Name: $it") }
        appendLine("Latitude: ${location.latitude}")
        appendLine("Longitude: ${location.longitude}")
        appendLine("Levels: ${trace.joinToString(" | ")}")
    }.trim()

    private fun failureReport(reason: FailureReason, trace: List<String>): String = buildString {
        appendLine("=== AMAP RESOLVER ===")
        appendLine("Result: FAILED")
        appendLine("Reason: $reason")
        appendLine("Levels: ${trace.joinToString(" | ")}")
    }.trim()

    private fun debug(message: String) {
        Log.d(TAG, message)
    }

    companion object {
        private const val TAG = "AMAP RESOLVER"
    }
}
