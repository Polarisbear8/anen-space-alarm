package com.anen.spacealarm.alert

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.anen.spacealarm.MainActivity
import com.anen.spacealarm.permission.PermissionManager
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/**
 * 真机集成测试：ALARM 模式的前台服务 + 全屏通知。
 *
 * 验证 AlarmService 能启动、发布带 full-screen intent 的闹钟通知、并在关闭后取消通知。
 * 锁屏 / 后台 / 关闭全屏权限等系统差异仍需人工验证。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AlarmInstrumentedTest {

    @get:Rule
    val notificationPermission: TestRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TestRule { base, _ -> base }
        }

    @Test
    fun alarmServicePostsNotificationAndStops() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        assumeTrue("需要通知权限，跳过", PermissionManager.state(context).notifications)

        // 先让 App 处于前台，避免 Android 12+ 的后台启动前台服务限制干扰测试
        ActivityScenario.launch(MainActivity::class.java).use {
            val started = AlarmService.start(context, TEST_REMINDER_ID, "测试地点", "快到了", 500f)
            assertTrue("闹钟前台服务应能启动", started)
            assertTrue(
                "闹钟通知应在前台服务启动后出现",
                waitFor(5_000L) { manager.activeNotifications.any { it.id == NotificationHelper.ALARM_NOTIFICATION_ID } }
            )

            AlarmService.stop(context)
            assertTrue(
                "关闭闹钟后通知应被取消",
                waitFor(5_000L) { manager.activeNotifications.none { it.id == NotificationHelper.ALARM_NOTIFICATION_ID } }
            )
        }
    }

    private fun waitFor(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(200L)
        }
        return condition()
    }

    private companion object {
        const val TEST_REMINDER_ID = 990002L
    }
}
