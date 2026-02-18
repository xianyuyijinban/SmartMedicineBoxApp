package com.smartmedicine.box.manager

import android.util.Log
import com.smartmedicine.box.data.model.CommandRequest
import com.smartmedicine.box.data.model.CommandResponse
import com.smartmedicine.box.data.model.CommandResult
import com.smartmedicine.box.data.model.ControlCommand
import com.smartmedicine.box.data.model.ErrorCode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 命令管理器
 * 负责发送控制命令到设备并处理响应
 * 
 * 主题格式:
 * - 命令发送: medicine/{device_id}/control
 * - 命令响应: medicine/{device_id}/control/response
 */
class CommandManager private constructor(
    private val mqttClient: MqttClient,
    private val deviceId: String,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "CommandManager"
        private const val DEFAULT_TIMEOUT_MS = 5000L  // 命令超时时间 5秒
        private const val QOS = 1  // 控制命令使用QoS 1确保到达
        
        @Volatile
        private var instance: CommandManager? = null

        /**
         * 获取CommandManager实例（单例模式）
         */
        fun getInstance(
            mqttClient: MqttClient,
            deviceId: String,
            coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ): CommandManager {
            return instance ?: synchronized(this) {
                instance ?: CommandManager(mqttClient, deviceId, coroutineScope).also {
                    instance = it
                }
            }
        }

        /**
         * 清除实例（用于重新初始化）
         */
        fun clearInstance() {
            instance?.destroy()
            instance = null
        }
    }

    // 命令主题
    private val controlTopic: String
        get() = "medicine/$deviceId/control"
    
    private val responseTopic: String
        get() = "medicine/$deviceId/control/response"

    // 待处理的命令（命令ID -> 等待的CompletableDeferred）
    private val pendingCommands = ConcurrentHashMap<Long, CompletableDeferred<CommandResult<CommandResponse>>>()
    
    // 命令ID生成器
    private val commandIdGenerator = AtomicLong(0)

    // 命令响应流（用于观察模式）
    private val _commandResponseFlow = MutableSharedFlow<CommandResponse>(extraBufferCapacity = 10)
    val commandResponseFlow: SharedFlow<CommandResponse> = _commandResponseFlow.asSharedFlow()

    // 是否已订阅响应主题
    private var isSubscribed = false

    init {
        subscribeToResponseTopic()
    }

    /**
     * 订阅命令响应主题
     */
    private fun subscribeToResponseTopic() {
        if (isSubscribed) return
        
        try {
            mqttClient.subscribe(responseTopic, QOS) { topic, message ->
                handleResponseMessage(String(message.payload))
            }
            isSubscribed = true
            Log.d(TAG, "Subscribed to response topic: $responseTopic")
        } catch (e: MqttException) {
            Log.e(TAG, "Failed to subscribe to response topic", e)
        }
    }

    /**
     * 处理响应消息
     */
    private fun handleResponseMessage(jsonString: String) {
        coroutineScope.launch {
            try {
                val json = JSONObject(jsonString)
                val response = CommandResponse.fromJson(json)
                
                // 发布到流
                _commandResponseFlow.emit(response)
                
                // 完成等待的Deferred
                pendingCommands.keys.forEach { cmdId ->
                    val deferred = pendingCommands[cmdId]
                    if (deferred != null && !deferred.isCompleted) {
                        // 这里简化处理，实际应该根据命令ID匹配
                        val result = if (response.result == com.smartmedicine.box.data.model.ResultType.SUCCESS) {
                            CommandResult.Success(response)
                        } else {
                            CommandResult.Error(
                                response.errorCode ?: -1,
                                getErrorMessage(response.errorCode, response.errorMsg)
                            )
                        }
                        deferred.complete(result)
                        pendingCommands.remove(cmdId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse response message", e)
            }
        }
    }

    /**
     * 发送控制命令（带超时和回调）
     * @param command 控制命令
     * @param value 命令参数值（可选）
     * @param timeoutMs 超时时间（毫秒）
     * @return 命令执行结果
     */
    suspend fun sendCommand(
        command: ControlCommand,
        value: Any? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): CommandResult<CommandResponse> = withTimeoutOrNull(timeoutMs) {
        val deferred = CompletableDeferred<CommandResult<CommandResponse>>()
        val commandId = commandIdGenerator.incrementAndGet()
        
        pendingCommands[commandId] = deferred
        
        try {
            val request = CommandRequest(command.command, value)
            val message = MqttMessage(request.toJson().toString().toByteArray()).apply {
                qos = QOS
                isRetained = false
            }
            
            mqttClient.publish(controlTopic, message)
            Log.d(TAG, "Command sent: ${command.command}, value: $value")
            
            deferred.await()
        } catch (e: MqttException) {
            Log.e(TAG, "Failed to send command", e)
            CommandResult.Error(-1, "发送命令失败: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Command execution error", e)
            CommandResult.Error(-1, "命令执行异常: ${e.message}")
        } finally {
            pendingCommands.remove(commandId)
        }
    } ?: CommandResult.Error(-2, "命令执行超时")

    /**
     * 发送控制命令（无返回值，发送即忘）
     * @param command 控制命令
     * @param value 命令参数值（可选）
     */
    fun sendCommandAsync(command: ControlCommand, value: Any? = null) {
        coroutineScope.launch {
            try {
                val request = CommandRequest(command.command, value)
                val message = MqttMessage(request.toJson().toString().toByteArray()).apply {
                    qos = QOS
                    isRetained = false
                }
                
                mqttClient.publish(controlTopic, message)
                Log.d(TAG, "Async command sent: ${command.command}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send async command", e)
            }
        }
    }

    /**
     * 重置设备
     */
    suspend fun resetDevice(timeoutMs: Long = DEFAULT_TIMEOUT_MS): CommandResult<CommandResponse> {
        return sendCommand(ControlCommand.RESET, null, timeoutMs)
    }

    /**
     * 立即上报数据
     */
    suspend fun publishNow(timeoutMs: Long = DEFAULT_TIMEOUT_MS): CommandResult<CommandResponse> {
        return sendCommand(ControlCommand.PUBLISH_NOW, null, timeoutMs)
    }

    /**
     * 设置上报间隔
     * @param intervalSeconds 上报间隔（秒）
     */
    suspend fun setInterval(
        intervalSeconds: Int,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): CommandResult<CommandResponse> {
        require(intervalSeconds in 1..3600) { "间隔时间必须在1-3600秒之间" }
        return sendCommand(ControlCommand.SET_INTERVAL, intervalSeconds, timeoutMs)
    }

    /**
     * 将错误码映射为中文错误信息
     * @param errorCode 错误码
     * @param defaultMessage 默认错误信息
     * @return 中文错误信息
     */
    fun getErrorMessage(errorCode: Int?, defaultMessage: String? = null): String {
        if (errorCode == null) return defaultMessage ?: "未知错误"
        
        return ErrorCode.getChineseMessage(errorCode)
    }

    /**
     * 获取完整的错误信息（包含错误码和描述）
     * @param errorCode 错误码
     * @param errorMsg 原始错误信息
     * @return 格式化后的错误信息
     */
    fun getFullErrorMessage(errorCode: Int?, errorMsg: String? = null): String {
        if (errorCode == null) return errorMsg ?: "未知错误"
        
        val description = ErrorCode.getDescription(errorCode)
        val chineseMessage = ErrorCode.getChineseMessage(errorCode)
        
        return "[$errorCode] $description: $chineseMessage"
    }

    /**
     * 检查命令是否执行成功
     * @param result 命令执行结果
     * @return 是否成功
     */
    fun isCommandSuccessful(result: CommandResult<CommandResponse>): Boolean {
        return result is CommandResult.Success
    }

    /**
     * 资源清理
     */
    fun destroy() {
        try {
            if (isSubscribed) {
                mqttClient.unsubscribe(responseTopic)
                isSubscribed = false
            }
        } catch (e: MqttException) {
            Log.e(TAG, "Error during destroy", e)
        }
        
        // 取消所有等待的命令
        pendingCommands.values.forEach { deferred ->
            if (!deferred.isCompleted) {
                deferred.complete(CommandResult.Error(-3, "CommandManager已销毁"))
            }
        }
        pendingCommands.clear()
        
        coroutineScope.cancel()
        Log.d(TAG, "CommandManager destroyed")
    }
}
