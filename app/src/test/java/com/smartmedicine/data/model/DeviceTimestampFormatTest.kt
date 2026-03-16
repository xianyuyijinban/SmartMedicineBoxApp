package com.smartmedicine.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTimestampFormatTest {

    @Test
    fun uptimeFormatterUsesMillisecondsFromBoot() {
        assertEquals("00:00:00.000", DeviceTimestampFormatter.formatUptime(0))
        assertEquals("00:02:03.456", DeviceTimestampFormatter.formatUptime(123456))
        assertEquals("01:01:01.007", DeviceTimestampFormatter.formatUptime(3661007))
    }

    @Test
    fun modelFormattedTimestampUsesUptimeFormatter() {
        val expected = "00:02:03.456"

        assertEquals(expected, SensorData(timestamp = 123456).getFormattedTimestamp())
        assertEquals(expected, DeviceStatus(timestamp = 123456).getFormattedTimestamp())
        assertEquals(expected, CommandResponse(timestamp = 123456).getFormattedTimestamp())
    }
}
