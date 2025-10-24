package ru.sablebot.common.support

import org.springframework.context.support.AbstractMessageSource
import org.springframework.stereotype.Component
import java.text.MessageFormat
import java.util.*

@Component
class SbMessageSource(
    private val messageSources: List<ModuleMessageSource>
) : AbstractMessageSource() {
    override fun resolveCodeWithoutArguments(code: String, locale: Locale): String? {
        messageSources.forEach { messageSource ->
            val result = messageSource.resolveCodeWithoutArguments(code, locale)
            if (result != null) {
                return result
            }
        }

        return null
    }

    override fun resolveCode(code: String, locale: Locale): MessageFormat? {
        messageSources.forEach { messageSource ->
            val result = messageSource.resolveCode(code, locale)
            if (result != null) {
                return result
            }
        }

        return null
    }
}