package ru.sablebot.common.support

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.AbstractMessageSource
import org.springframework.stereotype.Component
import java.text.MessageFormat
import java.util.*

@Component
open class SbMessageSource : AbstractMessageSource() {
    @Autowired
    private lateinit var messageSources: List<ModuleMessageSource>

    override fun resolveCodeWithoutArguments(code: String, locale: Locale): String? =
        messageSources.firstNotNullOfOrNull { it.resolveCodeWithoutArguments(code, locale) }

    override fun resolveCode(code: String, locale: Locale): MessageFormat? =
        messageSources.firstNotNullOfOrNull { it.resolveCode(code, locale) }
}