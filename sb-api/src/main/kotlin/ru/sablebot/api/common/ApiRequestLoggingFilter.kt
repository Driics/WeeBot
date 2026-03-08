package ru.sablebot.api.common

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.util.StringUtils
import org.springframework.web.filter.AbstractRequestLoggingFilter
import java.util.*

class ApiRequestLoggingFilter : AbstractRequestLoggingFilter() {

    override fun beforeRequest(request: HttpServletRequest, message: String) {
        logger.info(message)
    }

    override fun afterRequest(request: HttpServletRequest, message: String) {
        logger.info(message)
    }

    override fun createMessage(request: HttpServletRequest, prefix: String, suffix: String): String {
        val msg = StringBuilder()
            .append(prefix)
            .append(request.method)
            .append(" [")
            .append(request.requestURI)

        if (isIncludeQueryString) {
            val queryString = request.queryString
            if (queryString != null) {
                msg.append('?').append(queryString)
            }
        }

        msg.append(']')

        val data = TreeMap<String, String>()

        if (isIncludeClientInfo) {
            var client = request.getHeader("X-Real-IP")
            if (!StringUtils.hasLength(client)) {
                client = request.remoteAddr
            }
            if (StringUtils.hasLength(client)) {
                data["client"] = client!!
            }

            val session: HttpSession? = request.getSession(false)
            if (session != null) {
                data["session"] = session.id
            }

            val user = request.remoteUser
            if (user != null) {
                data["user"] = user
            }
        }

        if (isIncludeHeaders) {
            data["headers"] = ServletServerHttpRequest(request).headers.toString()
        }

        if (isIncludePayload) {
            val payload = getMessagePayload(request)
            if (payload != null) {
                data["payload"] = payload
            }
        }

        if (data.isNotEmpty()) {
            msg.append(' ').append(data.toString())
        }

        msg.append(suffix)
        return msg.toString()
    }
}