package ru.sablebot.common.utils

import java.util.*

object LocaleUtils {

    const val DEFAULT_LOCALE = "en"
    const val RU_LOCALE = "ru"

    val SUPPORTED_LOCALES: Map<String, Locale> = mapOf(
        DEFAULT_LOCALE to Locale.US,
        RU_LOCALE to Locale.forLanguageTag("ru-RU")
    )

    fun get(tag: String): Locale? {
        return SUPPORTED_LOCALES[tag]
    }

    fun getOrDefault(tag: String): Locale {
        return SUPPORTED_LOCALES.getOrElse(tag) { getDefaultLocale() }
    }

    fun isSupported(tag: String): Boolean {
        return SUPPORTED_LOCALES.containsKey(tag)
    }

    fun getDefaultLocale(): Locale {
        return SUPPORTED_LOCALES[DEFAULT_LOCALE] ?: Locale.US
    }
}
