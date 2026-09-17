package com.anen.spacealarm.amap

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.anen.spacealarm.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URLEncoder

/**
 * Level 4 WebView 兜底解析的真机测试。
 *
 * 用本地 data: URL 渲染一个带「地理坐标」的页面，验证：
 *  - WebView 能挂到窗口并真正执行 JS（这是 Level 4 生效的前提）
 *  - 渲染后的文本能被 AMapHtmlResolver 解析出坐标
 *
 * 不访问高德服务器，因此结果稳定；高德真实页面的解析情况由真机人工测试确认。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AMapWebViewResolverTest {

    private val pageHtml = """
        <html><head><title>南方医科大学珠江医院-高德地图</title></head>
        <body>
          <div>南方医科大学珠江医院</div>
          <div>工业大道中253号</div>
          <div>地理坐标：23.081058,113.268932</div>
        </body></html>
    """.trimIndent()

    @Test
    fun rendersPageAndExtractsCoordinate() {
        runBlocking {
            val dataUrl = "data:text/html;charset=utf-8," + URLEncoder.encode(pageHtml, "UTF-8")

            // 需要一个 Activity 才能把 WebView 挂到窗口上（headless WebView 不执行 JS）
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            var activity: MainActivity? = null
            scenario.onActivity { activity = it }

            val resolver = AMapWebViewResolver(activity!!)
            val page = resolver.render(dataUrl, timeoutMillis = 10_000L, settleMillis = 300L)
            scenario.close()

            assertNotNull("WebView 应能渲染页面（需要 attach 到窗口）", page)
            val coordinate = AMapHtmlResolver.extractFromText(page!!.text)
            assertNotNull("渲染后的文本应能解析出坐标", coordinate)
            assertEquals(23.081058, coordinate!!.latitude, 1e-6)
            assertEquals(113.268932, coordinate.longitude, 1e-6)
        }
    }
}
