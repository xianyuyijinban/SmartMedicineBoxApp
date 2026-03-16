package com.smartmedicine.data.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 设备状态数据类
 * 包含设备在线状态、固件版本、WiFi信号等信息
 */
data class DeviceStatus(
    @SerializedName("status")
    val status: String = "",
    
    @SerializedName("timestamp")
    val timestamp: Long = 0L,
    
    @SerializedName("device_id")
    val deviceId: String = "",
    
    @SerializedName("firmware_version")
    val firmwareVersion: String = "",
    
    @SerializedName("wifi_rssi")
    val wifiRssi: Int = 0,
    
    @SerializedName("publish_interval")
    val publishInterval: Int = 5
) {
    companion object {
        private val gson = Gson()
        
        const val STATUS_ONLINE = "online"
        const val STATUS_OFFLINE = "offline"
        const val STATUS_ERROR = "error"
        
        /**
         * 从JSON字符串解析DeviceStatus
         */
        fun fromJson(json: String): DeviceStatus? {
            return try {
                gson.fromJson(json, DeviceStatus::class.java)
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
     * 检查设备是否在线
     */
    fun isOnline(): Boolean {
        return status == STATUS_ONLINE
    }
    
    /**
     * 检查设备是否离线
     */
    fun isOffline(): Boolean {
        return status == STATUS_OFFLINE
    }
    
    /**
     * 检查设备是否错误状态
     */
    fun isError(): Boolean {
        return status == STATUS_ERROR
    }
    
    /**
     * 获取WiFi信号强度等级
     * @return 0-4等级，4为最强
     */
    fun getWifiSignalLevel(): Int {
        return when {
            wifiRssi >= -50 -> 4  // 优秀
            wifiRssi >= -60 -> 3  // 良好
            wifiRssi >= -70 -> 2  // 一般
            wifiRssi >= -80 -> 1  // 弱
            else -> 0             // 无信号/极差
        }
    }
    
    /**
     * 获取WiFi信号描述
     */
    fun getWifiSignalDescription(): String {
        return when (getWifiSignalLevel()) {
            4 -> "信号优秀"
            3 -> "信号良好"
            2 -> "信号一般"
            1 -> "信号弱"
            else -> "无信号"
        }
    }
    
    /**
     * 获取格式化的时间戳
     */
    fun getFormattedTimestamp(): String {
        return DeviceTimestampFormatter.formatUptime(timestamp)
    }
    
    /**
     * 获取固件版本（仅主版本号）
     */
    fun getFirmwareMajorVersion(): Int {
        return firmwareVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0
    }
}
