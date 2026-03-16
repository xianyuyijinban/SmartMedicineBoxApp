package com.smartmedicine.data.model

import com.google.gson.annotations.SerializedName

/**
 * 设备在 sensors 主题携带的告警状态位
 */
data class SensorAlertsData(
    @SerializedName("env_abnormal")
    val envAbnormal: Int = 0,

    @SerializedName("temperature_abnormal")
    val temperatureAbnormal: Int = 0,

    @SerializedName("humidity_abnormal")
    val humidityAbnormal: Int = 0
) {
    fun isEnvAbnormal(): Boolean = envAbnormal == 1
    fun isTemperatureAbnormal(): Boolean = temperatureAbnormal == 1
    fun isHumidityAbnormal(): Boolean = humidityAbnormal == 1
}
