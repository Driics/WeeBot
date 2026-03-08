package ru.sablebot.common.support

import org.springframework.context.support.AbstractMessageSource
import java.text.MessageFormat
import java.util.*

class SbMessageSource(
    private val messageSources: List<ModuleMessageSource>
) : AbstractMessageSource() {

    override fun resolveCodeWithoutArguments(code: String, locale: Locale): String? =
        messageSources.firstNotNullOfOrNull { it.resolveCodeWithoutArguments(code, locale) }

    override fun resolveCode(code: String, locale: Locale): MessageFormat? =
        messageSources.firstNotNullOfOrNull { it.resolveCode(code, locale) }
}