package com.soroush.wirecat.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleUtils {

    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_PERSIAN = "fa"

    fun currentLanguageTag(context: Context): String {
        val applied = AppCompatDelegate.getApplicationLocales()
        if (!applied.isEmpty) return applied[0]?.language ?: LANGUAGE_ENGLISH
        return if (context.resources.configuration.locales[0].language == LANGUAGE_PERSIAN) LANGUAGE_PERSIAN else LANGUAGE_ENGLISH
    }

    // recreates the current activity automatically - don't call recreate() again after this
    fun setLanguage(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }
}
