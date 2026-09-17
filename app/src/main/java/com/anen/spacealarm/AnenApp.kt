package com.anen.spacealarm

import android.app.Application
import android.content.Context
import com.anen.spacealarm.alert.NotificationHelper
import com.anen.spacealarm.preferences.LocaleManager

class AnenApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
