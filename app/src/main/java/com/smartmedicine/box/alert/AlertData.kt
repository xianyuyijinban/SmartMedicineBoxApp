package com.smartmedicine.box.alert

/**
 * 告警级别枚举
 * 定义告警的严重程度级别
 */
enum class AlertLevel(val priority: Int, val displayName: String) {
    /**
     * 信息级别 - 仅记录日志，如药箱打开、设备上线
     */
    INFO(1, "信息"),
    
    /**
     * 注意级别 - 可选通知，如药箱移动中
     */
    NOTICE(2, "注意"),
    
    /**
     * 警告级别 - 需要通知，如温度/湿度异常
     */
    WARNING(3, "警告"),
    
    /**
     * 严重级别 - 必须通知，如设备离线
     */
    CRITICAL(4, "严重");

    companion object {
        /**
         * 根据优先级值获取对应的告警级别
         */
        fun fromPriority(priority: Int): AlertLevel {
            return values().find { it.priority == priority } ?: INFO
        }
    }
}

/**
 * 告警类型枚举
 * 定义所有可能的告警类型及其默认级别
 */
enum class AlertType(val displayName: String) {
    /**
     * 温度过高 - 警告级别
     */
    TEMPERATURE_HIGH("温度过高"),
    
    /**
     * 温度过低 - 警告级别
     */
    TEMPERATURE_LOW("温度过低"),
    
    /**
     * 湿度过高 - 警告级别
     */
    HUMIDITY_HIGH("湿度过高"),
    
    /**
     * 湿度过低 - 警告级别
     */
    HUMIDITY_LOW("湿度过低"),
    
    /**
     * 药箱被打开 - 信息级别
     */
    BOX_OPENED("药箱打开"),
    
    /**
     * 药箱移动中 - 注意级别
     */
    BOX_MOVING("药箱移动中"),
    
    /**
     * 设备离线 - 严重级别
     */
    DEVICE_OFFLINE("设备离线"),
    
    /**
     * 设备上线 - 信息级别
     */
    DEVICE_ONLINE("设备上线");

    /**
     * 获取该告警类型对应的默认级别
     */
    fun getDefaultLevel(): AlertLevel {
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
 * 封装单个告警的所有信息
 * 
 * @param id 告警唯一标识（默认使用当前时间戳）
 * @param type 告警类型
 * @param level 告警级别
 * @param message 告警描述信息
 * @param timestamp 告警发生时间戳（毫秒）
 * @param deviceId 设备ID（可选）
 * @param data 附加数据（如温度值、湿度值等）
 */
data class AlertData(
    val id: Long = System.currentTimeMillis(),
    val type: AlertType,
    val level: AlertLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String = "",
    val data: Map<String, Any> = emptyMap()
) {
    /**
     * 获取告警的显示标题
     */
    fun getTitle(): String {
        return when (type) {
            TEMPERATURE_HIGH -> "温度警告"
            TEMPERATURE_LOW -> "温度警告"
            HUMIDITY_HIGH -> "湿度警告"
            HUMIDITY_LOW -> "湿度警告"
            BOX_OPENED -> "药箱已打开"
            BOX_MOVING -> "药箱移动中"
            DEVICE_OFFLINE -> "设备离线"
            DEVICE_ONLINE -> "设备已上线"
        }
    }

    /**
     * 获取格式化的时间字符串
     */
    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    /**
     * 转换为可读的字符串表示
     */
    override fun toString(): String {
        return "[${level.displayName}] ${getTitle()}: $message (${getFormattedTime()})"
    }

    companion object {
        /**
         * 创建温度告警
         */
        fun createTemperatureAlert(
            isHigh: Boolean,
            temperature: Float,
            threshold: Float,
            deviceId: String = ""
        ): AlertData {
            return AlertData(
                type = if (isHigh) AlertType.TEMPERATURE_HIGH else AlertType.TEMPERATURE_LOW,
                level = AlertLevel.WARNING,
                message = if (isHigh) {
                    "当前温度 ${temperature}°C，超过阈值 ${threshold}°C"
                } else {
                    "当前温度 ${temperature}°C，低于阈值 ${threshold}°C"
                },
                deviceId = deviceId,
                data = mapOf("temperature" to temperature, "threshold" to threshold)
            )
        }

        /**
         * 创建湿度告警
         */
        fun createHumidityAlert(
            isHigh: Boolean,
            humidity: Float,
            threshold: Float,
            deviceId: String = ""
        ): AlertData {
            return AlertData(
                type = if (isHigh) AlertType.HUMIDITY_HIGH else AlertType.HUMIDITY_LOW,
                level = AlertLevel.WARNING,
                message = if (isHigh) {
                    "当前湿度 ${humidity}%，超过阈值 ${threshold}%"
                } else {
                    "当前湿度 ${humidity}%，低于阈值 ${threshold}%"
                },
                deviceId = deviceId,
                data = mapOf("humidity" to humidity, "threshold" to threshold)
            )
        }

        /**
         * 创建药箱状态告警
         */
        fun createBoxStateAlert(
            type: AlertType,
            deviceId: String = "",
            additionalData: Map<String, Any> = emptyMap()
        ): AlertData {
            val (level, message) = when (type) {
                AlertType.BOX_OPENED -> AlertLevel.INFO to "药箱被打开，请注意药品安全"
                AlertType.BOX_MOVING -> AlertLevel.NOTICE to "检测到药箱正在移动"
                else -> AlertLevel.INFO to "药箱状态变更"
            }
            return AlertData(
                type = type,
                level = level,
                message = message,
                deviceId = deviceId,
                data = additionalData
            )
        }

        /**
         * 创建设备连接状态告警
         */
        fun createConnectionAlert(
            isOffline: Boolean,
            deviceId: String = "",
            durationSeconds: Long = 0
        ): AlertData {
            return if (isOffline) {
                AlertData(
                    type = AlertType.DEVICE_OFFLINE,
                    level = AlertLevel.CRITICAL,
                    message = "设备已离线 ${durationSeconds} 秒，请检查设备连接",
                    deviceId = deviceId,
                    data = mapOf("offlineSeconds" to durationSeconds)
                )
            } else {
                AlertData(
                    type = AlertType.DEVICE_ONLINE,
                    level = AlertLevel.INFO,
                    message = "设备已重新上线",
                    deviceId = deviceId,
                    data = mapOf("previousOfflineSeconds" to durationSeconds)
                )
            }
        }
    }
}

/**
 * 告警阈值配置
 * 用于配置告警检测的阈值参数
 * 
 * @param temperatureMax 温度上限（°C，默认30）
 * @param temperatureMin 温度下限（°C，默认10）
 * @param humidityMax 湿度上限（%RH，默认70）
 * @param humidityMin 湿度下限（%RH，默认30）
 * @param offlineTimeoutMs 离线检测超时时间（毫秒，默认15000）
 */
data class AlertThreshold(
    val temperatureMax: Float = 30.0f,
    val temperatureMin: Float = 10.0f,
    val humidityMax: Float = 70.0f,
    val humidityMin: Float = 30.0f,
    val offlineTimeoutMs: Long = 15000L
) {
    companion object {
        // 默认阈值配置
        val DEFAULT = AlertThreshold()
    }
}
