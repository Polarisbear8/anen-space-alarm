package com.anen.spacealarm.amap

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Level 4：WebView 兜底解析。
 *
 * 只在 HTTP 请求成功、但静态响应里没有可用坐标时才启用：
 * 高德地点页由 JavaScript 渲染，静态 HTML 里没有坐标，必须等页面真正渲染完再取文本。
 *
 * 约束：临时实例、主线程创建与销毁、有超时、失败也释放资源、不持有 Activity 引用。
 */
class AMapWebViewResolver(private val context: Context) {

    data class RenderedPage(
        val title: String?,
        val text: String?,
        /** 从页面 HTML 里直接正则命中的坐标片段（避免传输整页 DOM） */
        val matches: List<String>
    )

    /**
     * 加载并等待页面渲染，返回标题、可见文本与 HTML 中命中的坐标片段。
     * @return null 表示超时或加载失败
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun render(
        url: String,
        timeoutMillis: Long = 15_000L,
        settleMillis: Long = 3_000L
    ): RenderedPage? = withContext(Dispatchers.Main) {
        val webView = try {
            WebView(context.applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "cannot create WebView", e)
            return@withContext null
        }

        // 关键：WebView 必须挂到窗口上才会真正执行 JS / 渲染页面。
        // 这里临时挂到当前 Activity 的根布局（1x1 像素），解析结束立即移除并销毁，
        // 不长期持有 Activity 引用。
        var attached = false
        val activity = context as? Activity
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            try {
                activity.addContentView(webView, ViewGroup.LayoutParams(1, 1))
                attached = true
            } catch (e: Exception) {
                Log.w(TAG, "cannot attach WebView to window", e)
            }
        }

        try {
            val page = withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    var resumed = false
                    fun finish(page: RenderedPage?) {
                        if (!resumed) {
                            resumed = true
                            if (continuation.isActive) continuation.resume(page)
                        }
                    }

                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, pageUrl: String?) {
                            Log.d(TAG, "onPageFinished url=$pageUrl")
                            // 再等一小段时间，让页面里的 JS 把内容渲染出来
                            view.postDelayed({
                                try {
                                    view.evaluateJavascript(SCRIPT) { raw ->
                                        val page = parseRendered(raw)
                                        Log.d(
                                            TAG,
                                            "js result title=${page?.title} textLength=${page?.text?.length ?: 0} " +
                                                "matches=${page?.matches?.size ?: 0}"
                                        )
                                        finish(page)
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "evaluateJavascript failed", e)
                                    finish(null)
                                }
                            }, settleMillis)
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            Log.w(TAG, "web error ${error.errorCode} ${error.description} ${request.url}")
                        }
                    }
                    webView.loadUrl(url)
                }
            }
            if (page == null) {
                Log.d(TAG, "render timeout url=$url")
            } else {
                Log.d(TAG, "rendered sample=${page.text?.replace('\n', ' ')?.take(400)}")
                page.matches.take(3).forEach { Log.d(TAG, "rendered match=$it") }
            }
            page
        } catch (e: Exception) {
            Log.w(TAG, "webview render failed", e)
            null
        } finally {
            try {
                webView.stopLoading()
                if (attached) {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                }
                webView.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "webview destroy failed", e)
            }
        }
    }

    private fun parseRendered(raw: String?): RenderedPage? = try {
        val decoded = decodeJsResult(raw) ?: return null
        val json = JSONObject(decoded)
        val matches = json.optJSONArray("matches")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
        }.orEmpty()
        RenderedPage(
            title = json.optString("title").takeIf { it.isNotBlank() },
            text = json.optString("text").takeIf { it.isNotBlank() },
            matches = matches
        )
    } catch (e: Exception) {
        Log.w(TAG, "cannot parse rendered page", e)
        null
    }

    /** evaluateJavascript 返回的是 JSON 编码过的字符串，需要先解码一次。 */
    private fun decodeJsResult(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return try {
            val value = org.json.JSONTokener(raw).nextValue()
            if (value is String) value else null
        } catch (e: Exception) {
            raw
        }
    }

    companion object {
        private const val TAG = "AmapWebView"

        /**
         * 在页面里直接做一次坐标探测：只回传命中的片段，避免把整页 DOM 传到 Kotlin 侧。
         * 所有正则与 Kotlin 侧保持同一套形状，命中后仍由 AMapHtmlResolver 统一解析。
         */
        private const val SCRIPT = """
(function(){
  try {
    var html = document.documentElement ? document.documentElement.outerHTML : '';
    var text = document.documentElement ? document.documentElement.innerText : '';
    var pats = [
      /地理坐标\s*[：:]\s*-?\d{1,3}(?:\.\d+)?\s*[,，]\s*-?\d{1,3}(?:\.\d+)?/g,
      /纬度\s*[：:]?\s*-?\d{1,3}\.\d+/g,
      /经度\s*[：:]?\s*-?\d{1,3}\.\d+/g,
      /[?&](?:position|location)=(-?\d{1,3}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)/g,
      /"longitude"\s*:\s*"?(-?\d{1,3}\.\d+)"?[^}]{0,160}?"latitude"\s*:\s*"?(-?\d{1,3}\.\d+)/g,
      /"latitude"\s*:\s*"?(-?\d{1,3}\.\d+)"?[^}]{0,160}?"longitude"\s*:\s*"?(-?\d{1,3}\.\d+)/g,
      /"lng"\s*:\s*"?(-?\d{1,3}\.\d+)"?\s*,\s*"lat"\s*:\s*"?(-?\d{1,3}\.\d+)"/g,
      /"lat"\s*:\s*"?(-?\d{1,3}\.\d+)"?\s*,\s*"lng"\s*:\s*"?(-?\d{1,3}\.\d+)"/g,
      /"location"\s*:\s*"?(-?\d{1,3}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)"?/g
    ];
    var out = [];
    for (var i = 0; i < pats.length; i++) {
      var m;
      while ((m = pats[i].exec(html)) !== null) {
        out.push(m[0]);
        if (out.length >= 20) break;
      }
    }
    return JSON.stringify({title: document.title, text: text, matches: out});
  } catch (e) {
    return 'null';
  }
})()
"""
    }
}
