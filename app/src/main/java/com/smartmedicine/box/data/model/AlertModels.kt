package com.smartmedicine.box.data.model

/**
 * 告警级别枚举
 */
enum class AlertLevel(val priority: Int, val displayName: String) {
    INFO(1, "信息"),
    NOTICE(2, "注意"),
    WARNING(3, "警告"),
    CRITICAL(4, "严重");

    companion object {
        fun fromPriority(priority: Int): AlertLevel {
            return values().find { it.priority == priority } ?: INFO
        }
    }
}

/**
 * 告警类型枚举
 */
enum class AlertType(val displayName: String) {
    TEMPERATURE_HIGH("温度过高"),
    TEMPERATURE_LOW("温度过低"),
    HUMIDITY_HIGH("湿度过高"),
    HUMIDITY_LOW("湿度过低"),
    BOX_OPENED("药箱打开"),
    BOX_MOVING("药箱移动中"),
    DEVICE_OFFLINE("设备离线"),
    DEVICE_ONLINE("设备上线");

    fun getAlertLevel(): AlertLevel {
        return when (this) {
            TEMPERATURE_HIGH, TEMPERATURE_LOW, HUMIDITY_HIGH, HUMIDITY_LOW -> AlertLevel.WARNING
            BOX_OPENED -> AlertLevel.INFO
            BOX_MOVING -> AlertLevel.NOTICE
            DEVICE_OFFLINE -> AlertLevel.CRITICAL
            DEVICE_ONLINE -> AlertLevel.INFO
        }
    }
}

/**
 * 告警数据类
 */
data class Alert(
    val id: Long = System.currentTimeMillis(),
    val type: AlertType,
    val level: AlertLevel,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String = "",
    val data: Map<String, Any> = emptyMap()
)

/**
 * 告警阈值配置数据类
 */
data class AlertThresholdConfig(
    // 温度阈值 (°C)
    val temperatureMax: Float = 30.0f,
    val temperatureMin: Float = 10.0f,
    
    // 湿度阈值 (%RH)
    val humidityMax: Float = 70.0f,
    val humidityMin: Float = 30.0f,
    
    // 振动阈值 (g)
    val vibrationThreshold: Float = 0.5f,
    
    // 离线检测超时时间 (毫秒)
    val offlineTimeoutMs: Long = 15000L,
    
    // 通知开关配置
    val enableTemperatureAlert: Boolean = true,
    val enableHumidityAlert: Boolean = true,
    val enableBoxOpenedAlert: Boolean = false,  // 默认不通知，仅记录日志
    val enableBoxMovingAlert: Boolean = false,  // 默认不通知
    val enableOfflineAlert: Boolean = true
) {
    companion object {
        const val DEFAULT_OFFLINE_TIMEOUT_MS = 15000L
        const val DEFAULT_TEMP_MAX = 30.0f
        const val DEFAULT_TEMP_MIN = 10.0f
        const val DEFAULT_HUMIDITY_MAX = 70.0f
        const val DEFAULT_HUMIDITY_MIN = 30.0f
    }
}

/**
 * 告警检查请求
 */
data class AlertCheckRequest(
    val sensorData: SensorData,
    val deviceStatus: DeviceStatus,
    val lastHeartbeatTime: Long
)

/**
 * 告警检查结果
 */
data class AlertCheckResult(
    val alerts: List<Alert>,
    val hasCriticalAlert: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
