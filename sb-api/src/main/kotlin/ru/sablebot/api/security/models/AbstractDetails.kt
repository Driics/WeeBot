package ru.sablebot.api.security.models

import kotlinx.serialization.Serializable

@Serializable
abstract class AbstractDetails {
    var id: String? = null
        protected set

    companion object {

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        protected inline fun <T : Any> setValue(
            type: Class<T>,
            map: Map<Any, Any?>,
            name: String,
            setter: (T) -> Unit
        ) {
            val value = map[name] ?: return

            if (!type.isInstance(value)) {
                throw IllegalStateException(
                    "Wrong user details class type for $name. " +
                            "Found [${value::class.java.name}], expected [${type.name}]"
                )
            }

            setter(value as T)
        }
    }
}