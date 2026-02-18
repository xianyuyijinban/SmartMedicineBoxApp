package com.smartmedicine.data.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 环境传感器数据类
 * 包含温度、湿度、气压和海拔数据
 */
data class EnvironmentData(
    @SerializedName("temperature")
    val temperature: Double = 0.0,
    
    @SerializedName("humidity")
    val humidity: Double = 0.0,
    
    @SerializedName("pressure")
    val pressure: Double = 0.0,
    
    @SerializedName("altitude")
    val altitude: Double = 0.0
) {
    companion object {
        private val gson = Gson()
        
        /**
         * 从JSON字符串解析EnvironmentData
         */
        fun fromJson(json: String): EnvironmentData? {
            return try {
                gson.fromJson(json, EnvironmentData::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * 转换为JSON字符串
     */
    fun toJson(): String {
        return gson.toJson(this)
    }
    
    /**
     * 检查温度是否在合理范围内 (0-60°C)
     */
    fun isTemperatureValid(): Boolean {
        return temperature in 0.0..60.0
    }
    
    /**
     * 检查湿度是否在合理范围内 (0-100%)
     */
    fun isHumidityValid(): Boolean {
        return humidity in 0.0..100.0
    }
    
    /**
     * 检查气压是否在合理范围内 (80000-120000 Pa)
     */
    fun isPressureValid(): Boolean {
        return pressure in 80000.0..120000.0
    }
    
    /**
     * 获取格式化的温度字符串
     */
    fun getFormattedTemperature(): String {
        return String.format("%.1f°C", temperature)
    }
    
    /**
     * 获取格式化的湿度字符串
     */
    fun getFormattedHumidity(): String {
        return String.format("%.1f%%", humidity)
    }
    
    /**
     * 获取格式化的气压字符串
     */
    fun getFormattedPressure(): String {
        return String.format("%.0f hPa", pressure / 100)
    }
    
    /**
     * 获取格式化的海拔字符串
     */
    fun getFormattedAltitude(): String {
        return String.format("%.1f m", altitude)
    }
}
