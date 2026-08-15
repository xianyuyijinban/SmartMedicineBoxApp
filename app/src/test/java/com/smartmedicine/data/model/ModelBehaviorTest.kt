package com.smartmedicine.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelBehaviorTest {

    @Test
    fun sensorDataJsonParseForCoreFlow() {
        val json = """
            {
              "timestamp": 123456,
              "device_id": "medicine_box_001",
              "state": "moving",
              "environment": {
                "temperature": 25.6,
                "humidity": 54.2,
                "pressure": 100650.0,
                "altitude": 40.5
              },
              "environment_limits": {
                "temperature_rated": 15.0,
                "temperature_low": 10.5,
                "temperature_high": 19.5,
                "humidity_rated": 50.0,
                "humidity_low": 35.0,
                "humidity_high": 65.0
              },
              "motion": {
                "accel_x": 0.1,
                "accel_y": 0.2,
                "accel_z": 1.0,
                "gyro_x": 0.0,
                "gyro_y": 0.0,
                "gyro_z": 0.0,
                "pitch": 3.0,
                "roll": 2.0,
                "vibration": 0.3
              },
              "alerts": {
                "env_abnormal": 1,
                "temperature_abnormal": 1,
                "humidity_abnormal": 0
              },
              "buzzer_enabled": 0,
              "valid": 1
            }
        """.trimIndent()

        val data = SensorData.fromJson(json)
        assertNotNull(data)
        assertEquals("medicine_box_001", data!!.deviceId)
        assertEquals(BoxState.MOVING, data.getBoxState())
        assertTrue(data.isMoving())
        assertTrue(data.isValid())
        assertEquals(25.6, data.environment!!.temperature, 0.0001)
        assertEquals(15.0, data.environmentLimits!!.temperatureRated, 0.0001)
        assertEquals(0, data.buzzerEnabled)
        assertTrue(data.alerts!!.isTemperatureAbnormal())
        assertTrue(data.alerts!!.isEnvAbnormal())
    }

    @Test
    fun controlCommandIntervalBoundary() {
        assertEquals(1, ControlCommand.createSetIntervalCommand(1).value)
        assertEquals(3600, ControlCommand.createSetIntervalCommand(3600).value)
    }

    @Test
    fun controlCommandSetEnvRatedValid() {
        val cmd = ControlCommand.createSetEnvRatedCommand(24.5, 55.0)
        assertEquals(ControlCommand.CMD_SET_ENV_RATED, cmd.cmd)
        assertEquals(24.5, cmd.temperature!!, 0.0001)
        assertEquals(55.0, cmd.humidity!!, 0.0001)
        assertTrue(cmd.isValid())
    }

    @Test(expected = IllegalArgumentException::class)
    fun controlCommandIntervalBelowMinimumRejected() {
        ControlCommand.createSetIntervalCommand(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun controlCommandIntervalAboveMaximumRejected() {
        ControlCommand.createSetIntervalCommand(3601)
    }

    @Test(expected = IllegalArgumentException::class)
    fun controlCommandSetEnvRatedHumidityOutOfRangeRejected() {
        ControlCommand.createSetEnvRatedCommand(25.0, 120.0)
    }

    @Test
    fun deviceStatusSignalLevelBoundary() {
        assertEquals(4, DeviceStatus(wifiRssi = -50).getWifiSignalLevel())
        assertEquals(3, DeviceStatus(wifiRssi = -60).getWifiSignalLevel())
        assertEquals(2, DeviceStatus(wifiRssi = -70).getWifiSignalLevel())
        assertEquals(1, DeviceStatus(wifiRssi = -80).getWifiSignalLevel())
        assertEquals(0, DeviceStatus(wifiRssi = -81).getWifiSignalLevel())
    }

    @Test
    fun deviceStatusParsesBuzzerEnabled() {
        val status = DeviceStatus.fromJson(
            """
            {
              "status":"online",
              "timestamp":123,
              "device_id":"box001",
              "publish_interval":5,
              "buzzer_enabled":0
            }
            """.trimIndent()
        )

        assertNotNull(status)
        assertEquals(0, status!!.buzzerEnabled)
    }
}
