package ru.driics.sablebot.common.support.jmx

import org.springframework.jmx.export.naming.MetadataNamingStrategy
import javax.management.MalformedObjectNameException
import javax.management.ObjectName

class SbMetadataNamingStrategy : MetadataNamingStrategy() {

    /**
     * Overrides Spring's naming method and replaces it with a custom naming strategy.
     */
    override fun getObjectName(managedBean: Any, beanKey: String?): ObjectName {
        val finalBeanKey = beanKey ?: managedBean::class.java.name
        var objectName = super.getObjectName(managedBean, finalBeanKey)

        if (managedBean is JmxNamedResource) {
            objectName = buildObjectName(managedBean, objectName.domain)
        }
        return objectName
    }

    /**
     * Constructs the custom object name using the information from [JmxNamedResource].
     */
    private fun buildObjectName(namedResource: JmxNamedResource, domainName: String): ObjectName {
        val jmxPath = namedResource.jmxPath

        val nameString = jmxPath
            .mapIndexed { index, path -> "%02d_$path".format(index) }
            .joinToString(separator = "::")

        val objectNameString = "$domainName:$nameString[name_${namedResource.jmxName}]"

        return ObjectName.getInstance(objectNameString)
    }
}
