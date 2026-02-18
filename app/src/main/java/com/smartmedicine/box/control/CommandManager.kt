package com.smartmedicine.box.control

import android.util.Log
import com.smartmedicine.box.data.model.CommandRequest
import com.smartmedicine.box.data.model.CommandResponse
import com.smartmedicine.box.data.model.ControlCommand
import com.smartmedicine.box.data.model.ErrorCode
import com.smartmedicine.mqtt.MqttManager
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * 设备控制命令管理器
 * 
 * 功能：
 * 1. 发送控制命令到设备（reset, publishNow, setInterval）
 * 2. 监听命令响应主题 medicine/{device_id}/control/response
 * 3. 处理错误码和错误信息
 * 
 * @param mqttManager MQTT管理器实例
 */
class CommandManager(private val mqttManager: MqttManager) {

    companion object {
        private const val TAG = "CommandManager"
        
        // 命令超时时间（毫秒）
        private const val DEFAULT_TIMEOUT_MS = 5000L
        
        // QoS等级，控制命令使用QoS 1确保到达
        private const val QOS = 1
    }

    // 设备ID（从MqttManager获取）
    private var deviceId: String = ""
    
    // 命令响应监听器
    private var commandResponseListener: ((CommandResponse) -> Unit)? = null
    
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 待处理的命令（用于超时处理）
    private var pendingCommand: Job? = null
    
    // 是否已订阅响应主题
    private var isSubscribed = false

    /**
     * 初始化命令管理器
     * @param deviceId 设备ID
     */
    fun initialize(deviceId: String) {
        this.deviceId = deviceId
        subscribeToResponseTopic()
    }

    /**
     * 订阅命令响应主题
     * 主题格式：medicine/{device_id}/control/response
     */
    private fun subscribeToResponseTopic() {
        if (isSubscribed || deviceId.isEmpty()) return
        
        val responseTopic = "medicine/$deviceId/control/response"
        // 响应主题的订阅在MqttManager中统一处理
        // 这里设置消息处理回调
        isSubscribed = true
        Log.d(TAG, "准备接收命令响应，主题: $responseTopic")
    }

    /**
     * 处理命令响应消息
     * 由外部（如MqttService）调用，当收到control/response主题的消息时
     * 
     * @param payload 响应消息内容（JSON格式）
     */
    fun handleResponseMessage(payload: String) {
        try {
            val json = JSONObject(payload)
            val response = CommandResponse.fromJson(json)
            
            Log.d(TAG, "收到命令响应: cmd=${response.cmd}, result=${response.result}")
            
            // 调用监听器
            scope.launch(Dispatchers.Main) {
                commandResponseListener?.invoke(response)
            }
            
            // 取消超时任务
            pendingCommand?.cancel()
            
        } catch (e: Exception) {
            Log.e(TAG, "解析命令响应失败: $payload", e)
        }
    }

    /**
     * 发送重置设备命令
     * 命令：reset - 重置设备
     */
    fun sendResetCommand() {
        sendCommand(ControlCommand.RESET)
    }

    /**
     * 发送立即上报数据命令
     * 命令：publishNow - 立即上报数据
     */
    fun sendPublishNowCommand() {
        sendCommand(ControlCommand.PUBLISH_NOW)
    }

    /**
     * 设置数据上报间隔
     * 
     * @param interval 上报间隔（秒），范围 1-3600
     */
    fun sendSetIntervalCommand(interval: Int) {
        require(interval in 1..3600) { "上报间隔必须在1-3600秒之间" }
        sendCommand(ControlCommand.SET_INTERVAL, interval)
    }

    /**
     * 发送控制命令
     * 
     * @param command 控制命令类型
     * @param value 命令参数值（可选）
     */
    private fun sendCommand(command: ControlCommand, value: Any? = null) {
        if (deviceId.isEmpty()) {
            Log.e(TAG, "设备ID未设置，无法发送命令")
            notifyErrorResponse(command.command, -1, "设备未初始化")
            return
        }

        if (!mqttManager.isConnected()) {
            Log.e(TAG, "MQTT未连接，无法发送命令")
            notifyErrorResponse(command.command, -1, "MQTT未连接")
            return
        }

        try {
            // 构建命令请求
            val request = CommandRequest(command.command, value)
            val payload = request.toJson().toString()
            
            // 发送命令
            val topic = "medicine/$deviceId/control"
            mqttManager.publish(topic, payload, qos = QOS)
            
            Log.d(TAG, "命令已发送: ${command.command}, value: $value")
            
            // 启动超时检测
            startTimeoutCheck(command.command)
            
        } catch (e: Exception) {
            Log.e(TAG, "发送命令失败: ${command.command}", e)
            notifyErrorResponse(command.command, -1, e.message ?: "发送失败")
        }
    }

    /**
     * 启动命令超时检测
     * 如果在超时时间内未收到响应，触发错误回调
     * 
     * @param commandName 命令名称
     */
    private fun startTimeoutCheck(commandName: String) {
        pendingCommand?.cancel()
        pendingCommand = scope.launch {
            delay(DEFAULT_TIMEOUT_MS)
            
            // 超时，触发错误回调
            withContext(Dispatchers.Main) {
                val response = CommandResponse(
                    cmd = commandName,
                    result = com.smartmedicine.box.data.model.ResultType.ERROR,
                    errorCode = -2,
                    errorMsg = "命令执行超时"
                )
                commandResponseListener?.invoke(response)
            }
        }
    }

    /**
     * 通知错误响应（本地错误，非设备返回）
     */
    private fun notifyErrorResponse(cmd: String, errorCode: Int, errorMsg: String) {
        scope.launch(Dispatchers.Main) {
            val response = CommandResponse(
                cmd = cmd,
                result = com.smartmedicine.box.data.model.ResultType.ERROR,
                errorCode = errorCode,
                errorMsg = errorMsg
            )
            commandResponseListener?.invoke(response)
        }
    }

    /**
     * 设置命令响应监听器
     * 用于接收命令执行结果（成功/失败）和错误信息
     * 
     * @param listener 回调函数，参数为CommandResponse
     */
    fun setOnCommandResponseListener(listener: (CommandResponse) -> Unit) {
        this.commandResponseListener = listener
    }

    /**
     * 获取错误码对应的中文错误信息
     * 
     * @param errorCode 错误码
     * @return 中文错误描述
     */
    fun getErrorMessage(errorCode: Int): String {
        return ErrorCode.getChineseMessage(errorCode)
    }

    /**
     * 获取完整的错误信息
     * 
     * @param errorCode 错误码
     * @param defaultMsg 默认错误信息
     * @return 格式化的错误信息
     */
    fun getFullErrorMessage(errorCode: Int, defaultMsg: String? = null): String {
        if (errorCode <= 0) {
            return defaultMsg ?: "未知错误"
        }
        val description = ErrorCode.getDescription(errorCode)
        val chineseMsg = ErrorCode.getChineseMessage(errorCode)
        return "[$errorCode] $description: $chineseMsg"
    }

    /**
     * 资源释放
     */
    fun release() {
        pendingCommand?.cancel()
        scope.cancel()
        commandResponseListener = null
        isSubscribed = false
        Log.d(TAG, "CommandManager已释放")
    }
}
