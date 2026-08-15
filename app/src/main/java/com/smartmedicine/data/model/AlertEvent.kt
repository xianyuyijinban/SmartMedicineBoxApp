package com.smartmedicine.data.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 设备告警主题 `medicine/{device_id}/alert` 消息
 */
data class AlertEvent(
    @SerializedName("event")
    val event: String = "",

    @SerializedName("timestamp")
    val timestamp: Long = 0L,

    @SerializedName("temperature")
    val temperature: Double? = null,

    @SerializedName("humidity")
    val humidity: Double? = null,

    @SerializedName("rated_temperature")
    val ratedTemperature: Double? = null,

    @SerializedName("rated_humidity")
    val ratedHumidity: Double? = null,

    @SerializedName("temperature_abnormal")
    val temperatureAbnormal: Int? = null,

    @SerializedName("humidity_abnormal")
    val humidityAbnormal: Int? = null,

    @SerializedName("accel_x")
    val accelX: Double? = null,

    @SerializedName("accel_y")
    val accelY: Double? = null,

    @SerializedName("accel_z")
    val accelZ: Double? = null,

    @SerializedName("accel_magnitude")
    val accelMagnitude: Double? = null,

    @SerializedName("threshold_g")
    val thresholdG: Double? = null,

    @SerializedName("duration_ms")
    val durationMs: Int? = null,

    @SerializedName("source")
    val source: String? = null,

    @SerializedName("stop_push")
    val stopPush: Int? = null,

    @SerializedName("timer_id")
    val timerId: Int? = null,

    @SerializedName("mode")
    val mode: String? = null,

    @SerializedName("target_hour")
    val targetHour: Int? = null,

    @SerializedName("target_minute")
    val targetMinute: Int? = null,

    @SerializedName("target_second")
    val targetSecond: Int? = null
) {
    companion object {
        private val gson = Gson()

        const val EVENT_ENV_ABNORMAL = "env_abnormal"
        const val EVENT_ENV_RECOVERED = "env_recovered"
        const val EVENT_DROP_DETECTED = "drop_detected"
        const val EVENT_DROP_ALARM_CANCELLED = "drop_alarm_cancelled"
        const val EVENT_MEDICINE_TIMER_ALARM = "medicine_timer_alarm"
        const val EVENT_MEDICINE_TIMER_CANCELLED = "medicine_timer_cancelled"

        fun fromJson(json: String): AlertEvent? {
            return try {
                gson.fromJson(json, AlertEvent::class.java)
            } catch (_: Exception) {
                null
            }
        }
    }
}
