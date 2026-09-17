package com.anen.spacealarm.alert

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.anen.spacealarm.AppContainer
import com.anen.spacealarm.R
import com.anen.spacealarm.location.DistanceCalculator
import com.anen.spacealarm.ui.AlarmScreen
import com.anen.spacealarm.ui.theme.AnenTheme

/**
 * 闹钟全屏界面。锁屏可显示，屏幕会点亮。
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val placeName = intent.getStringExtra(EXTRA_PLACE_NAME).orEmpty()
        val extraMessage = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val radius = intent.getFloatExtra(EXTRA_RADIUS, 0f)

        setContent {
            AnenTheme {
                var message by remember { mutableStateOf(extraMessage) }
                LaunchedEffect(id) {
                    if (id > 0) {
                        val stored = AppContainer.reminderRepository(this@AlarmActivity).getById(id)
                        if (!stored?.message.isNullOrBlank()) message = stored!!.message
                    }
                }
                BackHandler { dismiss() }
                AlarmScreen(
                    placeName = placeName,
                    rangeText = if (radius > 0f) DistanceCalculator.formatRadius(radius) else getString(R.string.notif_range_entered_generic),
                    message = message,
                    onDismiss = { dismiss() }
                )
            }
        }
    }

    private fun dismiss() {
        AlarmService.stop(this)
        NotificationHelper.cancel(this, NotificationHelper.ALARM_NOTIFICATION_ID)
        finish()
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_PLACE_NAME = "place_name"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_RADIUS = "radius"

        fun intent(
            context: Context,
            reminderId: Long,
            placeName: String,
            message: String,
            radiusMeters: Float
        ): Intent = Intent(context, AlarmActivity::class.java)
            .putExtra(EXTRA_REMINDER_ID, reminderId)
            .putExtra(EXTRA_PLACE_NAME, placeName)
            .putExtra(EXTRA_MESSAGE, message)
            .putExtra(EXTRA_RADIUS, radiusMeters)

        fun pendingIntent(
            context: Context,
            reminderId: Long,
            placeName: String,
            message: String,
            radiusMeters: Float
        ): PendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt().coerceAtLeast(1),
            intent(context, reminderId, placeName, message, radiusMeters),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
