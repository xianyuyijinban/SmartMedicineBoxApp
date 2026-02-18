package com.smartmedicine.mqtt.aliyun

import com.smartmedicine.data.model.DeviceStatus
import com.smartmedicine.data.model.SensorData
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import timber.log.Timber

/**
 * 阿里云IoT MQTT管理器
 * 
 * 专门用于连接阿里云物联网平台的MQTT服务器
 */
class AliyunMqttManager {
    
    companion object {
        const val TAG = "AliyunMqttManager"
    }
    
    private var mqttClient: MqttClient? = null
    private var aliyunConfig: AliyunMqttConfig? = null
    private var topics: AliyunMqttTopics? = null
    
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 回调
    private var onConnectedCallback: (() -> Unit)? = null
    private var onDisconnectedCallback: (() -> Unit)? = null
    private var onSensorDataCallback: ((SensorData) -> Unit)? = null
    private var onStatusCallback: ((DeviceStatus) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
    /**
     * 连接到阿里云MQTT
     * 
     * @param auth 阿里云设备认证信息
     * @param useSSL 是否使用SSL加密（默认false，使用TCP）
     * @param callbacks 回调函数
     */
    fun connect(
        auth: AliyunDeviceAuth,
        useSSL: Boolean = false,
        onConnected: () -> Unit = {},
        onDisconnected: () -> Unit = {},
        onSensorDataReceived: (SensorData) -> Unit = {},
        onStatusReceived: (DeviceStatus) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (mqttClient?.isConnected == true) {
            Timber.w(TAG, "MQTT已连接，请先断开")
            return
        }
        
        aliyunConfig = AliyunMqttConfig(auth.productKey, auth.deviceName, auth.deviceSecret, auth.region)
        topics = AliyunMqttTopics(auth.productKey, auth.deviceName)
        
        onConnectedCallback = onConnected
        onDisconnectedCallback = onDisconnected
        onSensorDataCallback = onSensorDataReceived
        onStatusCallback = onStatusReceived
        onErrorCallback = onError
        
        scope.launch {
            try {
                val brokerUrl = aliyunConfig!!.getBrokerUrl(useSSL)
                val clientId = aliyunConfig!!.getClientId(if (useSSL) 2 else 3)
                
                Timber.d(TAG, "连接阿里云MQTT: $brokerUrl")
                Timber.d(TAG, "ClientID: $clientId")
                
                val persistence = MemoryPersistence()
                mqttClient = MqttClient(brokerUrl, clientId, persistence)
                
                val options = aliyunConfig!!.getMqttConnectOptions(if (useSSL) 2 else 3)
                
                mqttClient?.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        Timber.d(TAG, "连接完成，是否重连: $reconnect")
                        subscribeToTopics(auth.deviceName)
                        scope.launch(Dispatchers.Main) {
                            onConnectedCallback?.invoke()
                        }
                    }
                    
                    override fun connectionLost(cause: Throwable?) {
                        Timber.e(TAG, cause, "连接丢失")
                        scope.launch(Dispatchers.Main) {
                            onDisconnectedCallback?.invoke()
                        }
                    }
                    
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val payload = message?.toString() ?: return
                        Timber.d(TAG, "收到消息 - 主题: $topic")
                        handleIncomingMessage(topic, payload)
                    }
                    
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        Timber.d(TAG, "消息发送完成")
                    }
                })
                
                mqttClient?.connect(options)
                Timber.i(TAG, "阿里云MQTT连接成功")
                
            } catch (e: Exception) {
                Timber.e(TAG, e, "连接失败")
                scope.launch(Dispatchers.Main) {
                    onErrorCallback?.invoke(e.message ?: "连接失败")
                }
            }
        }
    }
    
    /**
     * 订阅主题
     */
    private fun subscribeToTopics(deviceId: String) {
        try {
            // 使用与接口文档兼容的主题格式
            val sensorsTopic = topics?.getLegacySensorsTopic(deviceId) ?: "medicine/$deviceId/sensors"
            val statusTopic = topics?.getLegacyStatusTopic(deviceId) ?: "medicine/$deviceId/status"
            val controlResponseTopic = topics?.getLegacyControlTopic(deviceId) ?: "medicine/$deviceId/control"
            
            mqttClient?.subscribe(sensorsTopic, 0)
            mqttClient?.subscribe(statusTopic, 1)
            mqttClient?.subscribe("$controlResponseTopic/response", 1)
            
            Timber.d(TAG, "订阅主题完成: $sensorsTopic, $statusTopic")
        } catch (e: MqttException) {
            Timber.e(TAG, e, "订阅主题失败")
        }
    }
    
    /**
     * 处理收到的消息
     */
    private fun handleIncomingMessage(topic: String?, payload: String) {
        scope.launch(Dispatchers.Default) {
            try {
                when {
                    topic?.contains("/sensors") == true -> {
                        SensorData.fromJson(payload)?.let { data ->
                            scope.launch(Dispatchers.Main) {
                                onSensorDataCallback?.invoke(data)
                            }
                        }
                    }
                    topic?.contains("/status") == true -> {
                        DeviceStatus.fromJson(payload)?.let { status ->
                            scope.launch(Dispatchers.Main) {
                                onStatusCallback?.invoke(status)
                            }
                        }
                    }
                    topic?.contains("/control/response") == true -> {
                        Timber.d(TAG, "收到控制响应: $payload")
                    }
                }
            } catch (e: Exception) {
                Timber.e(TAG, e, "处理消息失败")
            }
        }
    }
    
    /**
     * 发布消息
     */
    fun publish(topic: String, payload: String, qos: Int = 0): Boolean {
        if (mqttClient?.isConnected != true) {
            Timber.w(TAG, "MQTT未连接")
            return false
        }
        
        scope.launch {
            try {
                val message = MqttMessage(payload.toByteArray()).apply {
                    this.qos = qos
                }
                mqttClient?.publish(topic, message)
                Timber.d(TAG, "发布消息成功 - 主题: $topic")
            } catch (e: MqttException) {
                Timber.e(TAG, e, "发布消息失败")
            }
        }
        return true
    }
    
    /**
     * 发送控制命令
     */
    fun publishCommand(deviceId: String, cmd: String, value: Any? = null): Boolean {
        val topic = topics?.getLegacyControlTopic(deviceId) ?: "medicine/$deviceId/control"
        val payload = if (value != null) {
            """{"cmd": "$cmd", "value": $value}"""
        } else {
            """{"cmd": "$cmd"}"""
        }
        return publish(topic, payload, 1)
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        scope.launch {
            try {
                mqttClient?.let { client ->
                    if (client.isConnected) {
                        client.disconnect()
                        Timber.d(TAG, "MQTT已断开")
                    }
                    client.close()
                }
            } catch (e: Exception) {
                Timber.e(TAG, e, "断开连接时出错")
            } finally {
                mqttClient = null
                scope.launch(Dispatchers.Main) {
                    onDisconnectedCallback?.invoke()
                }
            }
        }
    }
    
    /**
     * 检查连接状态
     */
    fun isConnected(): Boolean = mqttClient?.isConnected == true
    
    /**
     * 释放资源
     */
    fun release() {
        disconnect()
        scope.cancel()
    }
}
