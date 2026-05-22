package com.yin.cpufreq2.core.locale

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LanguageManager {
    var currentLanguage: String = Locale.getDefault().language
        private set

    fun init(context: Context) {
        currentLanguage = getSystemPrimaryLanguage(context)
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        currentLanguage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            newConfig.locales.get(0).language
        } else {
            newConfig.locale.language
        }
    }

    private fun getSystemPrimaryLanguage(context: Context): String {
        val config = context.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales.get(0).language
        } else {
            config.locale.language
        }
    }
}