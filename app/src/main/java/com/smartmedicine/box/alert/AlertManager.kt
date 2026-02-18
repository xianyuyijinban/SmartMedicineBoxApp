package com.smartmedicine.box.alert

import android.content.Context
import android.util.Log
import com.smartmedicine.box.data.model.BoxState
import com.smartmedicine.box.data.model.SensorData

/**
 * 告警管理器
 * 
 * 功能：
 * 1. 检测各类告警条件（温度、湿度、药箱状态、设备离线）
 * 2. 管理告警监听器，触发告警时通知UI
 * 3. 维护告警历史记录
 * 4. 支持设备在线/离线状态检测
 * 
 * @param context Android上下文
 */
class AlertManager(private val context: Context) {

    companion object {
        private const val TAG = "AlertManager"
        
        // 单例实例
        @Volatile
        private var instance: AlertManager? = null

        /**
         * 获取AlertManager单例实例
         * 
         * @param context Android上下文
         * @return AlertManager实例
         */
        fun getInstance(context: Context): AlertManager {
            return instance ?: synchronized(this) {
                instance ?: AlertManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * 清除单例实例
         */
        fun clearInstance() {
            instance = null
        }
    }

    // 告警监听器
    private var alertListener: ((AlertData) -> Unit)? = null
    
    // 告警阈值配置
    private var thresholdConfig: AlertThreshold = AlertThreshold.DEFAULT
    
    // 告警历史记录（最多保存100条）
    private val alertHistory = mutableListOf<AlertData>()
    private val maxHistorySize = 100
    
    // 上一次传感器数据（用于状态变化检测）
    private var lastSensorData: SensorData? = null
    
    // 当前设备离线状态
    private var isCurrentlyOffline = false

    /**
     * 检查传感器数据并触发相应告警
     * 根据温度、湿度、药箱状态等条件检测告警
     * 
     * @param sensorData 传感器数据
     */
    fun checkAlerts(sensorData: SensorData) {
        // 检查温度告警
        checkTemperatureAlert(sensorData)
        
        // 检查湿度告警
        checkHumidityAlert(sensorData)
        
        // 检查药箱状态告警
        checkBoxStateAlerts(sensorData)
        
        // 保存当前数据用于下次比较
        lastSensorData = sensorData
    }

    /**
     * 检查温度告警
     * 温度>30°C: 高温警告
     * 温度<10°C: 低温警告
     * 
     * @param sensorData 传感器数据
     */
    private fun checkTemperatureAlert(sensorData: SensorData) {
        val temperature = sensorData.environment.temperature
        val maxThreshold = thresholdConfig.temperatureMax
        val minThreshold = thresholdConfig.temperatureMin

        when {
            temperature > maxThreshold -> {
                val alert = AlertData.createTemperatureAlert(
                    isHigh = true,
                    temperature = temperature,
                    threshold = maxThreshold,
                    deviceId = sensorData.deviceId
                )
                triggerAlert(alert)
            }
            temperature < minThreshold -> {
                val alert = AlertData.createTemperatureAlert(
                    isHigh = false,
                    temperature = temperature,
                    threshold = minThreshold,
                    deviceId = sensorData.deviceId
                )
                triggerAlert(alert)
            }
        }
    }

    /**
     * 检查湿度告警
     * 湿度>70%: 高湿度警告
     * 湿度<30%: 低湿度警告
     * 
     * @param sensorData 传感器数据
     */
    private fun checkHumidityAlert(sensorData: SensorData) {
        val humidity = sensorData.environment.humidity
        val maxThreshold = thresholdConfig.humidityMax
        val minThreshold = thresholdConfig.humidityMin

        when {
            humidity > maxThreshold -> {
                val alert = AlertData.createHumidityAlert(
                    isHigh = true,
                    humidity = humidity,
                    threshold = maxThreshold,
                    deviceId = sensorData.deviceId
                )
                triggerAlert(alert)
            }
            humidity < minThreshold -> {
                val alert = AlertData.createHumidityAlert(
                    isHigh = false,
                    humidity = humidity,
                    threshold = minThreshold,
                    deviceId = sensorData.deviceId
                )
                triggerAlert(alert)
            }
        }
    }

    /**
     * 检查药箱状态告警
     * state=opened: 信息级别，记录日志
     * state=moving: 注意级别，可选通知
     * 
     * @param sensorData 传感器数据
     */
    private fun checkBoxStateAlerts(sensorData: SensorData) {
        val currentState = sensorData.state
        val lastState = lastSensorData?.state

        // 药箱被打开（状态从非opened变为opened）
        if (currentState == BoxState.OPENED && lastState != BoxState.OPENED) {
            val alert = AlertData.createBoxStateAlert(
                type = AlertType.BOX_OPENED,
                deviceId = sensorData.deviceId
            )
            triggerAlert(alert)
        }

        // 药箱移动中
        if (currentState == BoxState.MOVING) {
            val alert = AlertData.createBoxStateAlert(
                type = AlertType.BOX_MOVING,
                deviceId = sensorData.deviceId,
                additionalData = mapOf("vibration" to sensorData.motion.vibration)
            )
            triggerAlert(alert)
        }
    }

    /**
     * 检查设备离线状态
     * 设备离线: 严重级别，推送+显示
     * 设备重新上线: 信息级别
     * 
     * @param isOffline 当前是否离线
     * @param offlineDurationMs 已离线时长（毫秒）
     */
    fun checkOfflineStatus(isOffline: Boolean, offlineDurationMs: Long = 0) {
        if (isOffline == isCurrentlyOffline) {
            return // 状态未变化
        }

        isCurrentlyOffline = isOffline

        val alert = AlertData.createConnectionAlert(
            isOffline = isOffline,
            deviceId = "",  // 可在调用处传入
            durationSeconds = offlineDurationMs / 1000
        )
        
        triggerAlert(alert)
        
        Log.d(TAG, if (isOffline) "设备离线告警触发" else "设备上线通知触发")
    }

    /**
     * 触发告警
     * 添加告警到历史记录并通知监听器
     * 
     * @param alert 告警数据
     */
    private fun triggerAlert(alert: AlertData) {
        // 添加到历史记录
        addToHistory(alert)
        
        // 通知监听器
        alertListener?.invoke(alert)
        
        // 根据级别记录日志
        when (alert.level) {
            AlertLevel.INFO -> Log.i(TAG, alert.toString())
            AlertLevel.NOTICE -> Log.d(TAG, alert.toString())
            AlertLevel.WARNING -> Log.w(TAG, alert.toString())
            AlertLevel.CRITICAL -> Log.e(TAG, alert.toString())
        }
    }

    /**
     * 添加告警到历史记录
     * 最多保留maxHistorySize条记录
     * 
     * @param alert 告警数据
     */
    private fun addToHistory(alert: AlertData) {
        alertHistory.add(0, alert) // 新告警放在前面
        
        // 限制历史记录数量
        if (alertHistory.size > maxHistorySize) {
            alertHistory.removeAt(alertHistory.lastIndex)
        }
    }

    /**
     * 设置告警监听器
     * 当有新告警产生时，会回调此监听器
     * 
     * @param listener 回调函数，参数为AlertData
     */
    fun setOnAlertListener(listener: (AlertData) -> Unit) {
        this.alertListener = listener
    }

    /**
     * 获取告警历史记录
     * 返回按时间倒序排列的告警列表
     * 
     * @return 告警历史列表
     */
    fun getAlertHistory(): List<AlertData> {
        return alertHistory.toList()
    }

    /**
     * 获取指定类型的告警历史
     * 
     * @param type 告警类型
     * @return 该类型的告警列表
     */
    fun getAlertHistoryByType(type: AlertType): List<AlertData> {
        return alertHistory.filter { it.type == type }
    }

    /**
     * 获取指定级别的告警历史
     * 
     * @param level 告警级别
     * @return 该级别的告警列表
     */
    fun getAlertHistoryByLevel(level: AlertLevel): List<AlertData> {
        return alertHistory.filter { it.level == level }
    }

    /**
     * 清空告警历史记录
     */
    fun clearAlertHistory() {
        alertHistory.clear()
        Log.d(TAG, "告警历史记录已清空")
    }

    /**
     * 更新告警阈值配置
     * 
     * @param config 新的阈值配置
     */
    fun updateThresholdConfig(config: AlertThreshold) {
        this.thresholdConfig = config
        Log.d(TAG, "告警阈值已更新: $config")
    }

    /**
     * 获取当前阈值配置
     * 
     * @return 当前阈值配置
     */
    fun getThresholdConfig(): AlertThreshold {
        return thresholdConfig
    }

    /**
     * 获取当前是否有活跃的严重告警
     * 
     * @return 是否有严重告警
     */
    fun hasCriticalAlert(): Boolean {
        return alertHistory.any { it.level == AlertLevel.CRITICAL }
    }

    /**
     * 获取未清除的告警数量
     * 
     * @return 告警数量
     */
    fun getAlertCount(): Int {
        return alertHistory.size
    }

    /**
     * 资源释放
     */
    fun release() {
        alertListener = null
        Log.d(TAG, "AlertManager已释放")
    }
}
