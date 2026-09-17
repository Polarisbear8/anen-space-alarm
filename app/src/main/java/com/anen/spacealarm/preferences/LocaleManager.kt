package com.anen.spacealarm.preferences

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 应用内语言切换。
 *
 * 不使用额外依赖：把保存的语言包进 Activity 的 base context，
 * 切换后由调用方 recreate() 让界面立即更新。
 */
object LocaleManager {

    const val CHINESE = "zh-CN"
    const val ENGLISH = "en"

    fun wrap(base: Context): Context {
        val tag = AppPreferences.language(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }
}
