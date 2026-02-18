package com.smartmedicine.box.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.smartmedicine.box.data.model.*
import com.smartmedicine.box.manager.AlertManager
import com.smartmedicine.box.manager.CommandManager
import com.smartmedicine.box.manager.OfflineDetector
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject

/**
 * 智能药箱MQTT服务
 * 
 * 整合MQTT连接、数据接收、告警检测、命令发送等功能
 * 作为后台服务运行，确保设备连接稳定性
 */
class MedicineBoxMqttService : Service() {

    companion object {
        private const val TAG = "MedicineBoxMqttService"
        
        // MQTT配置
        private const val DEFAULT_BROKER = "tcp://192.168.1.100:1883"
        private const val DEFAULT_CLIENT_ID = "AndroidApp"
        private const val DEFAULT_DEVICE_ID = "medicine_box_001"
        
        // QoS等级
        private const val QOS_DATA = 0      // 传感器数据使用QoS 0
        private const val QOS_STATUS = 1    // 状态消息使用QoS 1
        private const val QOS_CONTROL = 1   // 控制命令使用QoS 1
    }

    // Binder for Activity binding
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): MedicineBoxMqttService = this@MedicineBoxMqttService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // MQTT客户端
    private var mqttClient: MqttClient? = null
    
    // 设备ID
    private var deviceId: String = DEFAULT_DEVICE_ID
    
    // MQTT主题
    private val sensorsTopic: String get() = "medicine/$deviceId/sensors"
    private val statusTopic: String get() = "medicine/$deviceId/status"
    private val controlTopic: String get() = "medicine/$deviceId/control"
    private val controlResponseTopic: String get() = "medicine/$deviceId/control/response"

    // 协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 管理器实例
    private lateinit var commandManager: CommandManager
    private lateinit var alertManager: AlertManager
    private lateinit var offlineDetector: OfflineDetector

    // 回调接口
    interface ServiceCallback {
        fun onSensorDataReceived(data: SensorData)
        fun onDeviceStatusChanged(status: DeviceStatus)
        fun onConnectionStateChanged(connected: Boolean)
        fun onError(error: String)
    }
    private var serviceCallback: ServiceCallback? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // 初始化管理器
        alertManager = AlertManager.getInstance(this)
        offlineDetector = OfflineDetector.getInstance(
            timeoutMs = 15000L,
            coroutineScope = serviceScope
        )
    }

    /**
     * 连接MQTT服务器
     */
    fun connect(
        brokerUrl: String = DEFAULT_BROKER,
        deviceId: String = DEFAULT_DEVICE_ID,
        callback: ServiceCallback? = null
    ) {
        this.deviceId = deviceId
        this.serviceCallback = callback
        
        serviceScope.launch {
            try {
                val clientId = DEFAULT_CLIENT_ID + "_" + System.currentTimeMillis()
                mqttClient = MqttClient(brokerUrl, clientId, null)
                
                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 20
                }
                
                mqttClient?.connect(options)
                
                // 初始化CommandManager
                mqttClient?.let { client ->
                    commandManager = CommandManager.getInstance(client, deviceId, serviceScope)
                }
                
                // 订阅主题
                subscribeToTopics()
                
                // 启动离线检测
                offlineDetector.start()
                
                callback?.onConnectionStateChanged(true)
                Log.i(TAG, "Connected to MQTT broker: $brokerUrl")
                
            } catch (e: MqttException) {
                Log.e(TAG, "Failed to connect to MQTT broker", e)
                callback?.onError("连接失败: ${e.message}")
                callback?.onConnectionStateChanged(false)
            }
        }
    }

    /**
     * 订阅MQTT主题
     */
    private fun subscribeToTopics() {
        mqttClient?.let { client ->
            try {
                // 订阅传感器数据主题
                client.subscribe(sensorsTopic, QOS_DATA) { topic, message ->
                    handleSensorData(String(message.payload))
                }
                
                // 订阅状态主题
                client.subscribe(statusTopic, QOS_STATUS) { topic, message ->
                    handleStatusMessage(String(message.payload))
                }
                
                // 订阅控制响应主题（CommandManager会处理）
                // 但这里也订阅以便日志记录
                client.subscribe(controlResponseTopic, QOS_CONTROL) { topic, message ->
                    Log.d(TAG, "Control response: ${String(message.payload)}")
                }
                
                Log.d(TAG, "Subscribed to topics: $sensorsTopic, $statusTopic, $controlResponseTopic")
                
            } catch (e: MqttException) {
                Log.e(TAG, "Failed to subscribe to topics", e)
            }
        }
    }

    /**
     * 处理传感器数据
     */
    private fun handleSensorData(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val sensorData = SensorData.fromJson(json)
            
            // 更新离线检测器心跳
            offlineDetector.updateHeartbeat()
            
            // 检查告警
            val request = AlertCheckRequest(
                sensorData = sensorData,
                deviceStatus = DeviceStatus(),
                lastHeartbeatTime = offlineDetector.lastHeartbeatTime.value
            )
            alertManager.checkAlerts(request)
            
            // 回调到UI
            serviceCallback?.onSensorDataReceived(sensorData)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse sensor data", e)
        }
    }

    /**
     * 处理状态消息
     */
    private fun handleStatusMessage(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val status = DeviceStatus.fromJson(json)
            
            if (status.status == ConnectionStatus.ONLINE) {
                offlineDetector.updateHeartbeat()
            }
            
            serviceCallback?.onDeviceStatusChanged(status)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse status message", e)
        }
    }

    // ========== 公共API方法 ==========

    /**
     * 发送控制命令
     */
    fun sendCommand(
        command: ControlCommand,
        value: Any? = null,
        callback: ((CommandResult<CommandResponse>) -> Unit)? = null
    ) {
        serviceScope.launch {
            val result = commandManager.sendCommand(command, value)
            callback?.invoke(result)
        }
    }

    /**
     * 重置设备
     */
    fun resetDevice(callback: ((CommandResult<CommandResponse>) -> Unit)? = null) {
        sendCommand(ControlCommand.RESET, null, callback)
    }

    /**
     * 立即上报数据
     */
    fun publishNow(callback: ((CommandResult<CommandResponse>) -> Unit)? = null) {
        sendCommand(ControlCommand.PUBLISH_NOW, null, callback)
    }

    /**
     * 设置上报间隔
     */
    fun setInterval(seconds: Int, callback: ((CommandResult<CommandResponse>) -> Unit)? = null) {
        sendCommand(ControlCommand.SET_INTERVAL, seconds, callback)
    }

    /**
     * 更新告警阈值配置
     */
    fun updateAlertConfig(config: AlertThresholdConfig) {
        alertManager.updateThresholdConfig(config)
    }

    /**
     * 获取离线检测器
     */
    fun getOfflineDetector(): OfflineDetector = offlineDetector

    /**
     * 获取命令管理器
     */
    fun getCommandManager(): CommandManager? = if (::commandManager.isInitialized) commandManager else null

    /**
     * 断开连接
     */
    fun disconnect() {
        serviceScope.launch {
            try {
                offlineDetector.stop()
                mqttClient?.disconnect()
                serviceCallback?.onConnectionStateChanged(false)
                Log.d(TAG, "Disconnected from MQTT broker")
            } catch (e: MqttException) {
                Log.e(TAG, "Error during disconnect", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        serviceScope.cancel()
        
        // 清理管理器实例
        CommandManager.clearInstance()
        AlertManager.clearInstance()
        OfflineDetector.clearInstance()
        
        Log.d(TAG, "Service destroyed")
    }
}
