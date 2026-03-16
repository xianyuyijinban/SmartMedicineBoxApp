package com.smartmedicine.ui.screens

import java.net.URI

/**
 * MQTT 地址与设备 ID 输入校验
 */
object BrokerInputValidator {

    private val DEVICE_ID_REGEX = Regex("^[A-Za-z0-9_-]{1,64}$")

    fun isValidBroker(input: String): Boolean {
        val value = input.trim()
        if (value.isEmpty()) return false

        val uri = try {
            URI(value)
        } catch (_: Exception) {
            return false
        }

        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "tcp" && scheme != "ssl") return false

        val host = uri.host ?: return false
        if (host.isBlank()) return false

        val port = uri.port
        if (port !in 1..65535) return false

        if (!uri.path.isNullOrEmpty()) return false
        if (uri.query != null || uri.fragment != null || uri.userInfo != null) return false

        return true
    }

    fun isValidDeviceId(input: String): Boolean {
        return DEVICE_ID_REGEX.matches(input.trim())
    }
}
