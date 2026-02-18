package com.smartmedicine.mqtt

import com.smartmedicine.data.model.CommandResponse
import com.smartmedicine.data.model.DeviceStatus
import com.smartmedicine.data.model.SensorData
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import timber.log.Timber

/**
 * MQTT管理器 - 支持Jetpack Compose
 * 
 * 功能：
 * 1. 管理MQTT连接生命周期
 * 2. 支持主题订阅和发布
 * 3. 自动重连机制
 * 4. 连接状态监控
 * 5. 数据解析和回调
 */
class MqttManager {

    companion object {
        // MQTT默认配置常量
        const val DEFAULT_QOS = 0
        const val DEFAULT_KEEP_ALIVE = 20  // 秒
        const val DEFAULT_CONNECTION_TIMEOUT = 10  // 秒
        const val DEFAULT_CLEAN_SESSION = true
        const val DEFAULT_AUTO_RECONNECT = true
        
        // 主题模板
        const val TOPIC_SENSORS = "medicine/%s/sensors"
        const val TOPIC_STATUS = "medicine/%s/status"
        const val TOPIC_CONTROL = "medicine/%s/control"
        const val TOPIC_CONTROL_RESPONSE = "medicine/%s/control/response"
    }

    private var mqttClient: MqttClient? = null
    
    // 连接参数
    private var brokerUrl: String = ""
    private var clientId: String = ""
    private var currentDeviceId: String = ""
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 重连任务
    private var reconnectJob: Job? = null
    private var isManualDisconnect = false
    
    // 回调函数
    private var onConnectedCallback: (() -> Unit)? = null
    private var onDisconnectedCallback: (() -> Unit)? = null
    private var onSensorDataCallback: ((SensorData) -> Unit)? = null
    private var onStatusCallback: ((DeviceStatus) -> Unit)? = null
    private var onCommandResponseCallback: ((CommandResponse) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    /**
     * 连接到MQTT Broker（Compose版本）
     * 
     * @param brokerUrl Broker地址，例如：tcp://192.168.1.100:1883
     * @param clientId 客户端ID
     * @param deviceId 设备ID，用于构建主题
     * @param onConnected 连接成功回调
     * @param onDisconnected 断开连接回调
     * @param onSensorDataReceived 传感器数据接收回调
     * @param onStatusReceived 设备状态接收回调
     * @param onCommandResponseReceived 命令响应接收回调
     * @param onError 错误回调
     */
    fun connect(
        brokerUrl: String,
        clientId: String,
        deviceId: String,
        onConnected: () -> Unit = {},
        onDisconnected: () -> Unit = {},
        onSensorDataReceived: (SensorData) -> Unit = {},
        onStatusReceived: (DeviceStatus) -> Unit = {},
        onCommandResponseReceived: (CommandResponse) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (mqttClient?.isConnected == true) {
            Timber.w("MQTT已连接，请先断开")
            return
        }

        this.brokerUrl = brokerUrl
        this.clientId = clientId
        this.currentDeviceId = deviceId
        this.isManualDisconnect = false
        
        // 保存回调
        this.onConnectedCallback = onConnected
        this.onDisconnectedCallback = onDisconnected
        this.onSensorDataCallback = onSensorDataReceived
        this.onStatusCallback = onStatusReceived
        this.onCommandResponseCallback = onCommandResponseReceived
        this.onErrorCallback = onError

        scope.launch {
            try {
                Timber.d("正在连接MQTT Broker: $brokerUrl")

                val persistence = MemoryPersistence()
                mqttClient = MqttClient(brokerUrl, clientId, persistence)

                val options = MqttConnectOptions().apply {
                    connectionTimeout = DEFAULT_CONNECTION_TIMEOUT
                    keepAliveInterval = DEFAULT_KEEP_ALIVE
                    isCleanSession = DEFAULT_CLEAN_SESSION
                    isAutomaticReconnect = DEFAULT_AUTO_RECONNECT
                }

                mqttClient?.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        Timber.d("MQTT连接完成，是否重连: $reconnect")
                        
                        // 订阅相关主题
                        subscribeToTopics()
                        
                        // 回调到主线程
                        scope.launch(Dispatchers.Main) {
                            onConnectedCallback?.invoke()
                        }
                    }

                    override fun connectionLost(cause: Throwable?) {
                        Timber.e(cause, "MQTT连接丢失")
                        
                        scope.launch(Dispatchers.Main) {
                            onDisconnectedCallback?.invoke()
                        }
                        
                        // 如果不是手动断开，启动重连
                        if (!isManualDisconnect) {
                            startReconnect()
                        }
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val payload = message?.toString() ?: return
                        Timber.d("收到消息 - 主题: $topic")
                        
                        handleIncomingMessage(topic, payload)
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        Timber.d("消息发送完成")
                    }
                })

                mqttClient?.connect(options)
                
            } catch (e: Exception) {
                Timber.e(e, "MQTT连接失败")
                scope.launch(Dispatchers.Main) {
                    onErrorCallback?.invoke(e.message ?: "连接失败")
                }
                startReconnect()
            }
        }
    }

    /**
     * 订阅相关主题
     */
    private fun subscribeToTopics() {
        try {
            // 订阅传感器数据主题
            val sensorsTopic = TOPIC_SENSORS.format(currentDeviceId)
            mqttClient?.subscribe(sensorsTopic, DEFAULT_QOS)
            Timber.d("订阅主题: $sensorsTopic")
            
            // 订阅设备状态主题
            val statusTopic = TOPIC_STATUS.format(currentDeviceId)
            mqttClient?.subscribe(statusTopic, DEFAULT_QOS)
            Timber.d("订阅主题: $statusTopic")
            
            // 订阅控制响应主题
            val responseTopic = TOPIC_CONTROL_RESPONSE.format(currentDeviceId)
            mqttClient?.subscribe(responseTopic, DEFAULT_QOS)
            Timber.d("订阅主题: $responseTopic")
            
        } catch (e: MqttException) {
            Timber.e(e, "订阅主题失败")
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
                        // 解析传感器数据
                        SensorData.fromJson(payload)?.let { data ->
                            scope.launch(Dispatchers.Main) {
                                onSensorDataCallback?.invoke(data)
                            }
                        }
                    }
                    topic?.contains("/status") == true -> {
                        // 解析设备状态
                        DeviceStatus.fromJson(payload)?.let { status ->
                            scope.launch(Dispatchers.Main) {
                                onStatusCallback?.invoke(status)
                            }
                        }
                    }
                    topic?.contains("/control/response") == true -> {
                        // 解析命令响应
                        CommandResponse.fromJson(payload)?.let { response ->
                            scope.launch(Dispatchers.Main) {
                                onCommandResponseCallback?.invoke(response)
                            }
                            Timber.d("命令响应: ${response.cmd} = ${response.result}")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "处理消息失败")
            }
        }
    }

    /**
     * 断开MQTT连接
     */
    fun disconnect() {
        isManualDisconnect = true
        reconnectJob?.cancel()
        
        scope.launch {
            try {
                mqttClient?.let { client ->
                    if (client.isConnected) {
                        client.disconnect()
                        Timber.d("MQTT已断开连接")
                    }
                    client.close()
                }
            } catch (e: Exception) {
                Timber.e(e, "断开MQTT连接时出错")
            } finally {
                mqttClient = null
                scope.launch(Dispatchers.Main) {
                    onDisconnectedCallback?.invoke()
                }
            }
        }
    }

    /**
     * 发布消息
     * 
     * @param topic 主题名称
     * @param payload 消息内容
     * @param qos 服务质量等级（默认0）
     * @param retained 是否保留消息（默认false）
     * @return 是否成功发起发布
     */
    fun publish(topic: String, payload: String, qos: Int = DEFAULT_QOS, retained: Boolean = false): Boolean {
        if (!isConnected()) {
            Timber.w("MQTT未连接，无法发布消息到: $topic")
            return false
        }

        scope.launch {
            try {
                val message = MqttMessage(payload.toByteArray()).apply {
                    this.qos = qos
                    isRetained = retained
                }
                mqttClient?.publish(topic, message)
                Timber.d("发布消息成功 - 主题: $topic")
            } catch (e: MqttException) {
                Timber.e(e, "发布消息失败 - 主题: $topic")
            }
        }

        return true
    }

    /**
     * 发布控制命令
     * 
     * @param deviceId 设备ID
     * @param cmd 命令名称
     * @param value 命令参数（可选）
     */
    fun publishCommand(deviceId: String, cmd: String, value: Any? = null): Boolean {
        val topic = TOPIC_CONTROL.format(deviceId)
        val payload = if (value != null) {
            """{"cmd": "$cmd", "value": $value}"""
        } else {
            """{"cmd": "$cmd"}"""
        }
        return publish(topic, payload)
    }

    /**
     * 检查连接状态
     */
    fun isConnected(): Boolean {
        return mqttClient?.isConnected == true
    }

    /**
     * 启动自动重连
     */
    private fun startReconnect() {
        if (isManualDisconnect) return
        
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var attempt = 0
            while (!isManualDisconnect && !isConnected()) {
                attempt++
                val delayTime = (attempt * 2000).coerceAtMost(30000).toLong()
                Timber.d("${delayTime}ms后尝试第${attempt}次重连...")
                delay(delayTime)
                
                if (!isManualDisconnect && !isConnected()) {
                    try {
                        connect(
                            brokerUrl = brokerUrl,
                            clientId = clientId,
                            deviceId = currentDeviceId,
                            onConnected = onConnectedCallback ?: {},
                            onDisconnected = onDisconnectedCallback ?: {},
                            onSensorDataReceived = onSensorDataCallback ?: {},
                            onStatusReceived = onStatusCallback ?: {},
                            onCommandResponseReceived = onCommandResponseCallback ?: {},
                            onError = onErrorCallback ?: {}
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "重连失败")
                    }
                }
            }
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        disconnect()
        scope.cancel()
    }
}
