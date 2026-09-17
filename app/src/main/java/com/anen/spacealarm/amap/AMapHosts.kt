package com.anen.spacealarm.amap

/**
 * 允许联网抓取的高德主机白名单（精确匹配，不含子域通配）。
 *
 * 只放第一版真正需要的主机；“属于高德体系”不等于“需要我们的 HTTP Client 去抓页面”。
 * 实测发现新的高德域名时，在这里逐条补充，而不是放开整个后缀。
 */
internal object AMapHosts {

    val HOSTS = listOf(
        "www.amap.com",
        "amap.com",
        "uri.amap.com",
        "surl.amap.com",
        "wb.amap.com",
        "m.amap.com"
    )

    fun isTrusted(url: String, hosts: List<String> = HOSTS): Boolean {
        val host = UrlQuery.host(url) ?: return false
        return hosts.any { it.equals(host, ignoreCase = true) }
    }
}
