package ru.sablebot.common.support

import org.springframework.context.support.ResourceBundleMessageSource
import java.text.MessageFormat
import java.util.*

class ModuleMessageSource(
    baseName: String,
) : ResourceBundleMessageSource() {

    init {
        setBasename(baseName)
        this.defaultEncoding = "UTF-8"
    }

    /**
     * Resolves the given message code as key in the registered resource bundles,
     * returning the value found in the bundle as-is (without MessageFormat parsing).
     */
    public override fun resolveCodeWithoutArguments(code: String, locale: Locale): String? {
        return super.resolveCodeWithoutArguments(code, locale)
    }

    /**
     * Resolves the given message code as key in the registered resource bundles,
     * returning the value found in the bundle as-is (without MessageFormat parsing).
     */
    public override fun resolveCode(code: String, locale: Locale): MessageFormat? {
        return super.resolveCode(code, locale)
    }
}