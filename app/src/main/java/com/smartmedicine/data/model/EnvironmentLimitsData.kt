package com.smartmedicine.data.model

import com.google.gson.annotations.SerializedName

/**
 * 设备上报的环境额定值与阈值区间
 */
data class EnvironmentLimitsData(
    @SerializedName("temperature_rated")
    val temperatureRated: Double = 15.0,

    @SerializedName("temperature_low")
    val temperatureLow: Double = 10.5,

    @SerializedName("temperature_high")
    val temperatureHigh: Double = 19.5,

    @SerializedName("humidity_rated")
    val humidityRated: Double = 50.0,

    @SerializedName("humidity_low")
    val humidityLow: Double = 35.0,

    @SerializedName("humidity_high")
    val humidityHigh: Double = 65.0
)
