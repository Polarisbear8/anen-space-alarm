package com.anen.spacealarm.amap

import com.anen.spacealarm.share.ShareContent

/** 地图链接解析统一接口。 */
interface MapResolver {
    suspend fun resolve(content: ShareContent): ResolveResult
}
