package com.smartmedicine.box.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smartmedicine.box.data.model.Alert
import com.smartmedicine.box.data.model.AlertCheckRequest
import com.smartmedicine.box.data.model.AlertCheckResult
import com.smartmedicine.box.data.model.AlertLevel
import com.smartmedicine.box.data.model.AlertThresholdConfig
import com.smartmedicine.box.data.model.AlertType
import com.smartmedicine.box.data.model.BoxState
import com.smartmedicine.box.data.model.SensorData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 告警管理器
 * 负责检查告警条件并发送系统通知
 * 
 * 告警条件:
 * - 温度>30°C或<10°C: 警告级别，推送通知
 * - 湿度>70%或<30%: 警告级别，推送通知
 * - 药箱被打开(state=opened): 信息级别，记录日志
 * - 药箱移动中(state=moving): 注意级别，可选通知
 * - 设备离线: 严重级别，推送+提示
 */
class AlertManager private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "AlertManager"
        
        // 通知渠道ID
        const val CHANNEL_ID_TEMPERATURE = "channel_temperature"
        const val CHANNEL_ID_HUMIDITY = "channel_humidity"
        const val CHANNEL_ID_STATUS = "channel_status"
        const val CHANNEL_ID_OFFLINE = "channel_offline"
        const val CHANNEL_ID_GENERAL = "channel_general"
        
        // 通知ID基础值
        private const val NOTIFICATION_ID_TEMP_HIGH = 1001
        private const val NOTIFICATION_ID_TEMP_LOW = 1002
        private const val NOTIFICATION_ID_HUMIDITY_HIGH = 1003
        private const val NOTIFICATION_ID_HUMIDITY_LOW = 1004
        private const val NOTIFICATION_ID_BOX_OPENED = 1005
        private const val NOTIFICATION_ID_BOX_MOVING = 1006
        private const val NOTIFICATION_ID_DEVICE_OFFLINE = 1007
        
        @Volatile
        private var instance: AlertManager? = null

        /**
         * 获取AlertManager实例（单例模式）
         */
        fun getInstance(context: Context): AlertManager {
            return instance ?: synchronized(this) {
                instance ?: AlertManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * 清除实例
         */
        fun clearInstance() {
            instance = null
        }
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    // 告警阈值配置
    private val _thresholdConfig = MutableStateFlow(AlertThresholdConfig())
    val thresholdConfig: StateFlow<AlertThresholdConfig> = _thresholdConfig.asStateFlow()

    // 最新告警流
    private val _latestAlertFlow = MutableSharedFlow<Alert>(extraBufferCapacity = 10)
    val latestAlertFlow: SharedFlow<Alert> = _latestAlertFlow.asSharedFlow()

    // 告警历史列表
    private val _alertHistory = MutableStateFlow<List<Alert>>(emptyList())
    val alertHistory: StateFlow<List<Alert>> = _alertHistory.asStateFlow()

    // 活跃告警集合（用于去重）
    private val activeAlerts = mutableSetOf<AlertType>()

    // 上一次传感器数据（用于状态变化检测）
    private var lastSensorData: SensorData? = null

    init {
        createNotificationChannels()
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // 温度告警渠道
        val temperatureChannel = NotificationChannel(
            CHANNEL_ID_TEMPERATURE,
            "温度告警",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "温度过高或过低的告警通知"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
        }

        // 湿度告警渠道
        val humidityChannel = NotificationChannel(
            CHANNEL_ID_HUMIDITY,
            "湿度告警",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "湿度过高或过低的告警通知"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 100, 300)
        }

        // 状态变更渠道
        val statusChannel = NotificationChannel(
            CHANNEL_ID_STATUS,
            "状态变更",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "药箱状态变更通知"
        }

        // 离线告警渠道
        val offlineChannel = NotificationChannel(
            CHANNEL_ID_OFFLINE,
            "设备离线",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "设备离线告警"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000)
        }

        // 通用渠道
        val generalChannel = NotificationChannel(
            CHANNEL_ID_GENERAL,
            "一般通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "其他一般性通知"
        }

        notificationManager.createNotificationChannels(listOf(
            temperatureChannel,
            humidityChannel,
            statusChannel,
            offlineChannel,
            generalChannel
        ))
    }

    /**
     * 更新告警阈值配置
     */
    fun updateThresholdConfig(config: AlertThresholdConfig) {
        _thresholdConfig.value = config
        Log.d(TAG, "Threshold config updated: $config")
    }

    /**
     * 检查告警条件
     * @param request 告警检查请求
     * @return 告警检查结果
     */
    fun checkAlerts(request: AlertCheckRequest): AlertCheckResult {
        val alerts = mutableListOf<Alert>()
        val sensorData = request.sensorData
        val config = _thresholdConfig.value

        // 检查温度告警
        if (config.enableTemperatureAlert) {
            val tempAlert = checkTemperatureAlert(sensorData, config)
            if (tempAlert != null) {
                alerts.add(tempAlert)
            }
        }

        // 检查湿度告警
        if (config.enableHumidityAlert) {
            val humidityAlert = checkHumidityAlert(sensorData, config)
            if (humidityAlert != null) {
                alerts.add(humidityAlert)
            }
        }

        // 检查药箱状态告警
        val stateAlerts = checkStateAlerts(sensorData, config)
        alerts.addAll(stateAlerts)

        // 检查设备离线
        val offlineAlert = checkOfflineAlert(request, config)
        if (offlineAlert != null) {
            alerts.add(offlineAlert)
        }

        // 发送通知
        alerts.forEach { alert ->
            processAlert(alert)
        }

        // 更新活跃告警状态
        updateActiveAlerts(alerts)

        return AlertCheckResult(
            alerts = alerts,
            hasCriticalAlert = alerts.any { it.level == AlertLevel.CRITICAL }
        )
    }

    /**
     * 检查温度告警
     */
    private fun checkTemperatureAlert(
        sensorData: SensorData,
        config: AlertThresholdConfig
    ): Alert? {
        val temp = sensorData.environment.temperature
        
        return when {
            temp > config.temperatureMax -> {
                Alert(
                    type = AlertType.TEMPERATURE_HIGH,
                    level = AlertLevel.WARNING,
                    title = "温度警告",
                    message = "当前温度 ${temp}°C，超过阈值 ${config.temperatureMax}°C，请检查药箱环境",
                    deviceId = sensorData.deviceId,
                    data = mapOf("temperature" to temp, "threshold" to config.temperatureMax)
                )
            }
            temp < config.temperatureMin -> {
                Alert(
                    type = AlertType.TEMPERATURE_LOW,
                    level = AlertLevel.WARNING,
                    title = "温度警告",
                    message = "当前温度 ${temp}°C，低于阈值 ${config.temperatureMin}°C，请检查药箱环境",
                    deviceId = sensorData.deviceId,
                    data = mapOf("temperature" to temp, "threshold" to config.temperatureMin)
                )
            }
            else -> null
        }
    }

    /**
     * 检查湿度告警
     */
    private fun checkHumidityAlert(
        sensorData: SensorData,
        config: AlertThresholdConfig
    ): Alert? {
        val humidity = sensorData.environment.humidity
        
        return when {
            humidity > config.humidityMax -> {
                Alert(
                    type = AlertType.HUMIDITY_HIGH,
                    level = AlertLevel.WARNING,
                    title = "湿度警告",
                    message = "当前湿度 ${humidity}%，超过阈值 ${config.humidityMax}%，请检查药箱环境",
                    deviceId = sensorData.deviceId,
                    data = mapOf("humidity" to humidity, "threshold" to config.humidityMax)
                )
            }
            humidity < config.humidityMin -> {
                Alert(
                    type = AlertType.HUMIDITY_LOW,
                    level = AlertLevel.WARNING,
                    title = "湿度警告",
                    message = "当前湿度 ${humidity}%，低于阈值 ${config.humidityMin}%，请检查药箱环境",
                    deviceId = sensorData.deviceId,
                    data = mapOf("humidity" to humidity, "threshold" to config.humidityMin)
                )
            }
            else -> null
        }
    }

    /**
     * 检查药箱状态告警
     */
    private fun checkStateAlerts(
        sensorData: SensorData,
        config: AlertThresholdConfig
    ): List<Alert> {
        val alerts = mutableListOf<Alert>()
        val currentState = sensorData.state
        val lastState = lastSensorData?.state

        // 药箱被打开
        if (currentState == BoxState.OPENED && lastState != BoxState.OPENED) {
            alerts.add(
                Alert(
                    type = AlertType.BOX_OPENED,
                    level = AlertLevel.INFO,
                    title = "药箱已打开",
                    message = "药箱被打开，请注意药品安全",
                    deviceId = sensorData.deviceId,
                    data = mapOf("state" to currentState.value)
                )
            )
        }

        // 药箱移动中
        if (currentState == BoxState.MOVING && config.enableBoxMovingAlert) {
            alerts.add(
                Alert(
                    type = AlertType.BOX_MOVING,
                    level = AlertLevel.NOTICE,
                    title = "药箱移动中",
                    message = "检测到药箱正在移动",
                    deviceId = sensorData.deviceId,
                    data = mapOf("vibration" to sensorData.motion.vibration)
                )
            )
        }

        // 更新上一次状态
        lastSensorData = sensorData
        return alerts
    }

    /**
     * 检查设备离线
     */
    private fun checkOfflineAlert(
        request: AlertCheckRequest,
        config: AlertThresholdConfig
    ): Alert? {
        if (!config.enableOfflineAlert) return null
        
        val currentTime = System.currentTimeMillis()
        val timeSinceLastHeartbeat = currentTime - request.lastHeartbeatTime
        
        return if (timeSinceLastHeartbeat > config.offlineTimeoutMs) {
            Alert(
                type = AlertType.DEVICE_OFFLINE,
                level = AlertLevel.CRITICAL,
                title = "设备离线",
                message = "设备已超过 ${timeSinceLastHeartbeat / 1000} 秒未上报数据，请检查设备连接",
                deviceId = request.sensorData.deviceId,
                data = mapOf("offlineSeconds" to (timeSinceLastHeartbeat / 1000))
            )
        } else null
    }

    /**
     * 处理告警（发送通知、记录日志等）
     */
    private fun processAlert(alert: Alert) {
        // 添加到历史记录
        addToHistory(alert)
        
        // 发布到流
        _latestAlertFlow.tryEmit(alert)
        
        // 根据告警级别决定是否发送通知
        when (alert.level) {
            AlertLevel.INFO -> {
                // 仅记录日志，可选通知
                Log.i(TAG, "[INFO] ${alert.title}: ${alert.message}")
            }
            AlertLevel.NOTICE -> {
                Log.w(TAG, "[NOTICE] ${alert.title}: ${alert.message}")
            }
            AlertLevel.WARNING, AlertLevel.CRITICAL -> {
                Log.e(TAG, "[${alert.level}] ${alert.title}: ${alert.message}")
                sendNotification(alert)
            }
        }
    }

    /**
     * 发送系统通知
     */
    private fun sendNotification(alert: Alert) {
        val (channelId, notificationId, priority) = when (alert.type) {
            AlertType.TEMPERATURE_HIGH, AlertType.TEMPERATURE_LOW -> 
                Triple(CHANNEL_ID_TEMPERATURE, 
                    if (alert.type == AlertType.TEMPERATURE_HIGH) NOTIFICATION_ID_TEMP_HIGH else NOTIFICATION_ID_TEMP_LOW,
                    NotificationCompat.PRIORITY_HIGH)
            AlertType.HUMIDITY_HIGH, AlertType.HUMIDITY_LOW -> 
                Triple(CHANNEL_ID_HUMIDITY,
                    if (alert.type == AlertType.HUMIDITY_HIGH) NOTIFICATION_ID_HUMIDITY_HIGH else NOTIFICATION_ID_HUMIDITY_LOW,
                    NotificationCompat.PRIORITY_HIGH)
            AlertType.BOX_OPENED -> 
                Triple(CHANNEL_ID_STATUS, NOTIFICATION_ID_BOX_OPENED, NotificationCompat.PRIORITY_DEFAULT)
            AlertType.BOX_MOVING -> 
                Triple(CHANNEL_ID_STATUS, NOTIFICATION_ID_BOX_MOVING, NotificationCompat.PRIORITY_DEFAULT)
            AlertType.DEVICE_OFFLINE -> 
                Triple(CHANNEL_ID_OFFLINE, NOTIFICATION_ID_DEVICE_OFFLINE, NotificationCompat.PRIORITY_HIGH)
            AlertType.DEVICE_ONLINE -> 
                Triple(CHANNEL_ID_GENERAL, NOTIFICATION_ID_DEVICE_OFFLINE, NotificationCompat.PRIORITY_DEFAULT)
        }

        val builder = NotificationCompat.Builder(context, channelId).apply {
            setSmallIcon(android.R.drawable.ic_dialog_alert)
            setContentTitle(alert.title)
            setContentText(alert.message)
            priority = priority
            setAutoCancel(true)
            
            // 严重告警使用长文本样式
            if (alert.level == AlertLevel.CRITICAL) {
                setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            }
            
            // 设置时间戳
            setWhen(alert.timestamp)
            setShowWhen(true)
        }

        notificationManager.notify(notificationId, builder.build())
        Log.d(TAG, "Notification sent: ${alert.title}")
    }

    /**
     * 更新活跃告警状态
     */
    private fun updateActiveAlerts(alerts: List<Alert>) {
        // 清除已恢复的告警类型
        val currentAlertTypes = alerts.map { it.type }.toSet()
        
        // 添加新的活跃告警
        activeAlerts.addAll(currentAlertTypes)
    }

    /**
     * 添加告警到历史记录
     */
    private fun addToHistory(alert: Alert) {
        val currentList = _alertHistory.value.toMutableList()
        currentList.add(0, alert)  // 新告警放在前面
        
        // 限制历史记录数量（保留最近100条）
        if (currentList.size > 100) {
            currentList.removeAt(currentList.lastIndex)
        }
        
        _alertHistory.value = currentList
    }

    /**
     * 清除所有通知
     */
    fun clearAllNotifications() {
        notificationManager.cancelAll()
    }

    /**
     * 清除历史记录
     */
    fun clearHistory() {
        _alertHistory.value = emptyList()
    }

    /**
     * 取消指定类型的告警通知
     */
    fun cancelNotification(alertType: AlertType) {
        val notificationId = when (alertType) {
            AlertType.TEMPERATURE_HIGH -> NOTIFICATION_ID_TEMP_HIGH
            AlertType.TEMPERATURE_LOW -> NOTIFICATION_ID_TEMP_LOW
            AlertType.HUMIDITY_HIGH -> NOTIFICATION_ID_HUMIDITY_HIGH
            AlertType.HUMIDITY_LOW -> NOTIFICATION_ID_HUMIDITY_LOW
            AlertType.BOX_OPENED -> NOTIFICATION_ID_BOX_OPENED
            AlertType.BOX_MOVING -> NOTIFICATION_ID_BOX_MOVING
            AlertType.DEVICE_OFFLINE -> NOTIFICATION_ID_DEVICE_OFFLINE
            AlertType.DEVICE_ONLINE -> NOTIFICATION_ID_DEVICE_OFFLINE
        }
        notificationManager.cancel(notificationId)
    }

    /**
     * 设备上线通知
     */
    fun notifyDeviceOnline(deviceId: String) {
        val alert = Alert(
            type = AlertType.DEVICE_ONLINE,
            level = AlertLevel.INFO,
            title = "设备已上线",
            message = "设备 $deviceId 已重新连接",
            deviceId = deviceId
        )
        processAlert(alert)
        
        // 清除离线通知
        cancelNotification(AlertType.DEVICE_OFFLINE)
    }
}
