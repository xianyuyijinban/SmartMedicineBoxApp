package com.smartmedicine.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValidationTest {

    @Test
    fun invalidBrokerReturnsBrokerError() {
        val result = validateSettingsInputs("not-a-broker", "medicine_box_001")

        assertFalse(result.isValid)
        assertEquals(
            "MQTT Broker 地址格式错误，应为 tcp://host:port 或 ssl://host:port",
            result.mqttError
        )
        assertNull(result.deviceIdError)
    }

    @Test
    fun invalidDeviceIdReturnsDeviceError() {
        val result = validateSettingsInputs("tcp://192.168.1.100:1883", "bad id")

        assertFalse(result.isValid)
        assertNull(result.mqttError)
        assertEquals(
            "设备ID仅支持字母、数字、下划线和中划线（1-64位）",
            result.deviceIdError
        )
    }

    @Test
    fun validInputsHaveNoErrors() {
        val result = validateSettingsInputs("ssl://mqtt.example.com:8883", "box_001")

        assertTrue(result.isValid)
        assertNull(result.mqttError)
        assertNull(result.deviceIdError)
    }

    @Test
    fun usernamePasswordMustBeProvidedTogether() {
        val result = validateSettingsInputs(
            mqttBroker = "ssl://mqtt.example.com:8883",
            deviceId = "box_001",
            mqttUsername = "user_only",
            mqttPassword = ""
        )

        assertFalse(result.isValid)
        assertEquals("用户名和密码需同时填写，或同时留空", result.authError)
    }
}
