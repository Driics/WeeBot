package ru.sablebot.common.support.jmx

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jmx.support.ConnectorServerFactoryBean
import org.springframework.stereotype.Service
import ru.sablebot.common.configuration.CommonProperties

@Service
@ConditionalOnProperty(prefix = "sablebot.common.jmx", name = ["enabled"], havingValue = "true")
class JmxService @Autowired constructor(
    private val commonProperties: CommonProperties
) : ConnectorServerFactoryBean() {
    companion object {
        const val SERVICE_URL = "service:jmx:jmxmp://localhost:%s"
    }

    override fun afterPropertiesSet() {
        setServiceUrl(SERVICE_URL.format(commonProperties.jmx.port))
        super.afterPropertiesSet()
    }
}