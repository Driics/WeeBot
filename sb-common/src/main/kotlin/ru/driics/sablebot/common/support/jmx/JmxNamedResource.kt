package ru.driics.sablebot.common.support.jmx

interface JmxNamedResource {
    val jmxName: String
    val jmxPath: Array<String>
        get() = emptyArray()
}