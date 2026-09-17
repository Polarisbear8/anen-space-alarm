package com.anen.spacealarm.share

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机集成测试：高德分享 → ShareActivity → AMapResolver → 创建界面。
 *
 * 使用本地即可解析的 uri.amap.com 链接，不依赖网络。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ShareIntentTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun shareIntentResolvesPlaceAndShowsCreateScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, ShareActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, SHARE_TEXT)
        }

        ActivityScenario.launch<ShareActivity>(intent).use {
            // 解析成功后先进入地点确认界面（TARGET LOCATION → USE THIS LOCATION）
            composeRule.waitUntil(timeoutMillis = 15_000L) {
                composeRule.onAllNodesWithText("USE THIS LOCATION").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("南方医科大学珠江医院").assertIsDisplayed()
        }
    }

    private companion object {
        const val SHARE_TEXT = "南方医科大学珠江医院(工业大道中253号)，广东省广州市海珠区，" +
            "https://uri.amap.com/marker?position=113.267724,23.079347&name=南方医科大学珠江医院"
    }
}
