package com.smartmedicine.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCommandTimerTest {
    @Test
    fun countdownTimerCommandSerializesExpectedFields() {
        val command = ControlCommand.createSetMedicineTimerCommand(
            mode = ControlCommand.TIMER_MODE_COUNTDOWN,
            hour = 0,
            minute = 10,
            second = 0
        )

        assertTrue(command.isValid())

        val parsed = ControlCommand.fromJson(command.toJson())
        assertEquals(ControlCommand.CMD_SET_MEDICINE_TIMER, parsed?.cmd)
        assertEquals(ControlCommand.TIMER_MODE_COUNTDOWN, parsed?.mode)
        assertEquals(0, parsed?.hour)
        assertEquals(10, parsed?.minute)
        assertEquals(0, parsed?.second)
    }

    @Test
    fun clockTimerCommandRequiresCurrentTime() {
        val command = ControlCommand.createSetMedicineTimerCommand(
            mode = ControlCommand.TIMER_MODE_CLOCK,
            hour = 8,
            minute = 30,
            second = 0,
            nowHour = 14,
            nowMinute = 20,
            nowSecond = 5
        )

        assertTrue(command.isValid())
        assertEquals(14, command.nowHour)
        assertEquals(20, command.nowMinute)
        assertEquals(5, command.nowSecond)
    }

    @Test
    fun zeroCountdownIsInvalid() {
        val command = ControlCommand(
            cmd = ControlCommand.CMD_SET_MEDICINE_TIMER,
            mode = ControlCommand.TIMER_MODE_COUNTDOWN,
            hour = 0,
            minute = 0,
            second = 0
        )

        assertFalse(command.isValid())
    }

    @Test
    fun timerIdIsSerializedWhenProvided() {
        val command = ControlCommand.createSetMedicineTimerCommand(
            mode = ControlCommand.TIMER_MODE_COUNTDOWN,
            hour = 0,
            minute = 5,
            second = 0,
            timerId = 3
        )

        val parsed = ControlCommand.fromJson(command.toJson())
        assertTrue(command.isValid())
        assertEquals(3, parsed?.timerId)
    }

    @Test
    fun cancelTimerCanTargetSingleTimer() {
        val command = ControlCommand.createCancelMedicineTimerCommand(timerId = 4)

        val parsed = ControlCommand.fromJson(command.toJson())
        assertTrue(command.isValid())
        assertEquals(ControlCommand.CMD_CANCEL_MEDICINE_TIMER, parsed?.cmd)
        assertEquals(4, parsed?.timerId)
    }

    @Test
    fun setTimerResponseParsesTimerFields() {
        val response = CommandResponse.fromJson(
            """
            {
              "cmd":"set_medicine_timer",
              "result":"ok",
              "timer_id":2,
              "active_count":5,
              "remaining_seconds":600,
              "mode":"countdown",
              "target_hour":8,
              "target_minute":30,
              "target_second":0
            }
            """.trimIndent()
        )

        assertEquals(2, response?.timerId)
        assertEquals(5, response?.activeCount)
        assertEquals(600L, response?.remainingSeconds)
        assertEquals(8, response?.targetHour)
    }
}
