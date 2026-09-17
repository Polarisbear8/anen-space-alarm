package com.anen.spacealarm.amap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 跟随高德短链/跳转链接（surl.amap.com 等）。
 * 只做用户主动导入地点时的一次性请求，不后台周期请求。
 *
 * 安全约束：初始链接与每一跳重定向都必须命中 AMapHosts 白名单，
 * 一旦跳到白名单之外的域名立即停止，不再发起请求。
 *
 * 注意：传入的 OkHttpClient 必须关闭自动重定向（followRedirects = false），
 * 否则无法检查每一跳。
 */
class AMapRedirectResolver(
    private val client: OkHttpClient,
    private val allowedHosts: List<String> = AMapHosts.HOSTS
) {

    data class ChainResult(
        val hops: List<String>,
        val finalUrl: String,
        val finalBody: String?,
        val error: String?
    )

    suspend fun follow(url: String, maxHops: Int = 5): ChainResult = withContext(Dispatchers.IO) {
        val hops = mutableListOf<String>()
        var current = url
        var body: String? = null
        var error: String? = null

        if (!AMapHosts.isTrusted(current, allowedHosts)) {
            return@withContext ChainResult(
                hops = listOf(current),
                finalUrl = current,
                finalBody = null,
                error = "不受信任的初始链接，已停止：${UrlQuery.host(current)}"
            )
        }

        var hop = 0
        while (hop < maxHops) {
            hop++
            hops += current
            val response = try {
                client.newCall(buildRequest(current)).execute()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                null
            }
            if (response == null) break
            response.use { r ->
                if (r.isRedirect) {
                    val location = r.header("Location")
                    val next = location?.let { r.request.url.resolve(it)?.toString() }
                    when {
                        location.isNullOrBlank() -> error = "重定向缺少 Location"
                        next == null -> error = "重定向地址无法解析：$location"
                        !AMapHosts.isTrusted(next, allowedHosts) -> {
                            error = "重定向到不受信任的域名，已停止：${UrlQuery.host(next)}"
                        }
                        else -> current = next
                    }
                } else {
                    body = r.body?.string()
                    current = r.request.url.toString()
                }
            }
            if (error != null || body != null) break
        }
        ChainResult(hops = hops, finalUrl = current, finalBody = body, error = error)
    }

    private fun buildRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", BROWSER_UA)
        .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .build()

    companion object {
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}
