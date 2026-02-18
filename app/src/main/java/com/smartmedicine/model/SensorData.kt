package com.smartmedicine.model

import com.google.gson.annotations.SerializedName

/**
 * 传感器数据模型
 * 对应主题: medicine/{device_id}/sensors
 */
data class SensorData(
    @SerializedName("temperature")
    val temperature: Float = 0f,
    
    @SerializedName("humidity")
    val humidity: Float = 0f,
    
    @SerializedName("light_level")
    val lightLevel: Int = 0,
    
    @SerializedName("box_opened")
    val boxOpened: Boolean = false,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 设备状态模型
 * 对应主题: medicine/{device_id}/status
 */
data class DeviceStatus(
    @SerializedName("online")
    val online: Boolean = false,
    
    @SerializedName("battery_level")
    val batteryLevel: Int = 0,
    
    @SerializedName("wifi_signal")
    val wifiSignal: Int = 0,
    
    @SerializedName("medicine_count")
    val medicineCount: Int = 0,
    
    @SerializedName("last_sync")
    val lastSync: Long = 0
)

/**
 * 控制命令模型
 * 对应主题: medicine/{device_id}/control
 */
data class ControlCommand(
    @SerializedName("command")
    val command: String,
    
    @SerializedName("param")
    val param: String? = null,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val CMD_OPEN_BOX = "open_box"
        const val CMD_CLOSE_BOX = "close_box"
        const val CMD_GET_STATUS = "get_status"
        const val CMD_SET_ALARM = "set_alarm"
        const val CMD_CANCEL_ALARM = "cancel_alarm"
        const val CMD_SET_LED = "set_led"
    }
}

/**
 * 命令响应模型
 * 对应主题: medicine/{device_id}/control/response
 */
data class CommandResponse(
    @SerializedName("command")
    val command: String,
    
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String? = null,
    
    @SerializedName("data")
    val data: Map<String, Any>? = null,
    
    @SerializedName("timestamp")
    val timestamp: Long = 0
)
