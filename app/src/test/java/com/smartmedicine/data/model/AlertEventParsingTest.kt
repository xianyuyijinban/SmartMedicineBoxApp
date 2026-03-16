package com.smartmedicine.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AlertEventParsingTest {

    @Test
    fun parseEnvAbnormalAlertEvent() {
        val json = """
            {
              "event": "env_abnormal",
              "timestamp": 123456789,
              "temperature": 28.4,
              "humidity": 82.1,
              "rated_temperature": 15.0,
              "rated_humidity": 50.0,
              "temperature_abnormal": 1,
              "humidity_abnormal": 1
            }
        """.trimIndent()

        val event = AlertEvent.fromJson(json)
        assertNotNull(event)
        assertEquals(AlertEvent.EVENT_ENV_ABNORMAL, event!!.event)
        assertEquals(1, event.temperatureAbnormal)
        assertEquals(1, event.humidityAbnormal)
    }

    @Test
    fun parseDropAlarmCancelledEvent() {
        val json = """
            {
              "event": "drop_alarm_cancelled",
              "timestamp": 123457100,
              "source": "key2",
              "stop_push": 1
            }
        """.trimIndent()

        val event = AlertEvent.fromJson(json)
        assertNotNull(event)
        assertEquals(AlertEvent.EVENT_DROP_ALARM_CANCELLED, event!!.event)
        assertEquals("key2", event.source)
        assertEquals(1, event.stopPush)
    }
}
