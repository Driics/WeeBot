package ru.sablebot.common.support.jmx

import org.springframework.jmx.export.naming.MetadataNamingStrategy
import javax.management.MalformedObjectNameException
import javax.management.ObjectName

class SbMetadataNamingStrategy : MetadataNamingStrategy() {

    /**
     * Overrides Spring's naming method and replaces it with a custom naming strategy.
     */

    override fun getObjectName(managedBean: Any, beanKey: String?): ObjectName {
        val actualKey = beanKey ?: managedBean.javaClass.name
        var objectName = super.getObjectName(managedBean, actualKey)

        if (managedBean is JmxNamedResource) {
            objectName = buildObjectName(managedBean, objectName.domain)
        }
        return objectName
    }

    /**
     * Constructs the custom object name using the information from [JmxNamedResource].
     */
    @Throws(MalformedObjectNameException::class)
    private fun buildObjectName(namedObject: JmxNamedResource, domainName: String): ObjectName {
        val typeNames = namedObject.jmxPath

        val nameBuilder = buildString {
            append(domainName)
            append(':')

            typeNames.forEachIndexed { index, typeName ->
                if (index > 0) append(',')
                append("type$index")
                append('=')
                append(ObjectName.quote(typeName))
            }

            if (typeNames.isNotEmpty()) append(',')
            append("name=")
            append(ObjectName.quote(namedObject.jmxName))
        }

        return ObjectName.getInstance(nameBuilder)
    }
}
