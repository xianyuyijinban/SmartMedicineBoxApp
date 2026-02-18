package com.example.smartmedicinebox.data.model

/**
 * 智能药箱数据类
 * 存储从MQTT接收到的所有传感器数据
 */
data class MedicineBoxData(
    /** 温度 (°C) */
    val temperature: Float = 0f,
    
    /** 湿度 (%) */
    val humidity: Float = 0f,
    
    /** 气压 (hPa) */
    val pressure: Float = 0f,
    
    /** 海拔 (m) */
    val altitude: Float = 0f,
    
    /** 药箱是否打开 */
    val isOpened: Boolean = false,
    
    /** 药箱是否在移动 */
    val isMoving: Boolean = false,
    
    /** 药箱是否倾斜 */
    val isTilted: Boolean = false,
    
    /** 振动告警 */
    val isVibrationAlert: Boolean = false,
    
    /** 设备电量 (%) */
    val batteryLevel: Int = 100,
    
    /** WiFi信号强度 (dBm) */
    val wifiSignal: Int = -50,
    
    /** 时间戳 */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 获取药箱状态
     */
    fun getBoxState(): BoxState {
        return when {
            isTilted -> BoxState.TILTED
            isMoving -> BoxState.MOVING
            isOpened -> BoxState.OPENED
            else -> BoxState.CLOSED
        }
    }
    
    companion object {
        /**
         * 从JSON字符串解析数据
         */
        fun fromJson(json: String): MedicineBoxData {
            // 实际项目中使用Gson或Kotlin Serialization解析
            // return Gson().fromJson(json, MedicineBoxData::class.java)
            return MedicineBoxData()
        }
    }
}
