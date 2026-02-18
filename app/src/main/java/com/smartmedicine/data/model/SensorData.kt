package com.smartmedicine.data.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 传感器综合数据类
 * 包含时间戳、设备ID、状态、环境和运动数据
 */
data class SensorData(
    @SerializedName("timestamp")
    val timestamp: Long = 0L,
    
    @SerializedName("device_id")
    val deviceId: String = "",
    
    @SerializedName("state")
    val state: String = "unknown",
    
    @SerializedName("environment")
    val environment: EnvironmentData? = null,
    
    @SerializedName("motion")
    val motion: MotionData? = null,
    
    @SerializedName("valid")
    val valid: Int = 0
) {
    companion object {
        private val gson = Gson()
        
        /**
         * 从JSON字符串解析SensorData
         */
        fun fromJson(json: String): SensorData? {
            return try {
                gson.fromJson(json, SensorData::class.java)
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
     * 检查数据是否有效
     */
    fun isValid(): Boolean {
        return valid == 1
    }
    
    /**
     * 获取箱状态枚举
     */
    fun getBoxState(): BoxState {
        return BoxState.fromValue(state)
    }
    
    /**
     * 检查药箱是否关闭
     */
    fun isClosed(): Boolean {
        return getBoxState() == BoxState.CLOSED
    }
    
    /**
     * 检查药箱是否打开
     */
    fun isOpened(): Boolean {
        return getBoxState() == BoxState.OPENED
    }
    
    /**
     * 检查药箱是否移动中
     */
    fun isMoving(): Boolean {
        return getBoxState() == BoxState.MOVING || motion?.isMoving() == true
    }
    
    /**
     * 检查药箱是否倾斜
     */
    fun isTilted(): Boolean {
        return getBoxState() == BoxState.TILTED || motion?.isTilted() == true
    }
    
    /**
     * 获取格式化的时间戳
     */
    fun getFormattedTimestamp(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp * 1000))
    }
    
    /**
     * 获取温度值（如果环境数据存在）
     */
    fun getTemperature(): Double? {
        return environment?.temperature
    }
    
    /**
     * 获取湿度值（如果环境数据存在）
     */
    fun getHumidity(): Double? {
        return environment?.humidity
    }
    
    /**
     * 获取振动值（如果运动数据存在）
     */
    fun getVibration(): Double? {
        return motion?.vibration
    }
}
