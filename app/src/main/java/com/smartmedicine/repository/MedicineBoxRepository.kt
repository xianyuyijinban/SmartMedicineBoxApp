package com.smartmedicine.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.smartmedicine.model.*
import com.smartmedicine.mqtt.*
import timber.log.Timber

/**
 * 药箱数据仓库 - 处理所有MQTT通信和数据转换
 */
class MedicineBoxRepository private constructor() {

    companion object {
        @Volatile
        private var instance: MedicineBoxRepository? = null
        
        fun getInstance(): MedicineBoxRepository {
            return instance ?: synchronized(this) {
                instance ?: MedicineBoxRepository().also { instance = it }
            }
        }
    }

    private val mqttManager = MqttManager.getInstance()
    private val gson = Gson()

    // LiveData for UI观察
    private val _sensorData = MutableLiveData<SensorData>()
    val sensorData: LiveData<SensorData> = _sensorData

    private val _deviceStatus = MutableLiveData<DeviceStatus>()
    val deviceStatus: LiveData<DeviceStatus> = _deviceStatus

    private val _connectionState = MutableLiveData<MqttConnectionState>()
    val connectionState: LiveData<MqttConnectionState> = _connectionState

    private val _commandResponse = MutableLiveData<CommandResponse>()
    val commandResponse: LiveData<CommandResponse> = _commandResponse

    // MQTT连接回调
    private val connectionCallback = object : MqttConnectionCallback {
        override fun onConnected() {
            _connectionState.postValue(MqttConnectionState.Connected)
            // 连接成功后订阅设备相关主题
            subscribeToDeviceTopics()
        }

        override fun onDisconnected() {
            _connectionState.postValue(MqttConnectionState.Disconnected)
        }

        override fun onConnectionLost(cause: Throwable?) {
            _connectionState.postValue(MqttConnectionState.Error(cause ?: Exception("连接丢失")))
        }

        override fun onConnectFailed(exception: Throwable) {
            _connectionState.postValue(MqttConnectionState.Error(exception))
        }
    }

    // 传感器数据回调
    private val sensorCallback = object : MqttMessageCallback {
        override fun onMessageReceived(topic: String, message: String) {
            try {
                val data = gson.fromJson(message, SensorData::class.java)
                _sensorData.postValue(data)
            } catch (e: Exception) {
                Timber.e(e, "解析传感器数据失败")
            }
        }
    }

    // 设备状态回调
    private val statusCallback = object : MqttMessageCallback {
        override fun onMessageReceived(topic: String, message: String) {
            try {
                val status = gson.fromJson(message, DeviceStatus::class.java)
                _deviceStatus.postValue(status)
            } catch (e: Exception) {
                Timber.e(e, "解析设备状态失败")
            }
        }
    }

    // 命令响应回调
    private val controlResponseCallback = object : MqttMessageCallback {
        override fun onMessageReceived(topic: String, message: String) {
            try {
                val response = gson.fromJson(message, CommandResponse::class.java)
                _commandResponse.postValue(response)
            } catch (e: Exception) {
                Timber.e(e, "解析命令响应失败")
            }
        }
    }

    /**
     * 连接到药箱设备
     */
    fun connect(brokerUrl: String, clientId: String, deviceId: String): Boolean {
        _connectionState.value = MqttConnectionState.Connecting
        return mqttManager.connect(brokerUrl, clientId, deviceId, connectionCallback)
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        mqttManager.disconnect()
    }

    /**
     * 订阅设备主题
     */
    private fun subscribeToDeviceTopics() {
        mqttManager.subscribeSensors(sensorCallback)
        mqttManager.subscribeStatus(statusCallback)
        mqttManager.subscribeControlResponse(controlResponseCallback)
    }

    /**
     * 发送开盖命令
     */
    fun openBox(): Boolean {
        val command = ControlCommand(ControlCommand.CMD_OPEN_BOX)
        return mqttManager.sendControlCommand(gson.toJson(command))
    }

    /**
     * 发送关盖命令
     */
    fun closeBox(): Boolean {
        val command = ControlCommand(ControlCommand.CMD_CLOSE_BOX)
        return mqttManager.sendControlCommand(gson.toJson(command))
    }

    /**
     * 获取设备状态
     */
    fun getStatus(): Boolean {
        val command = ControlCommand(ControlCommand.CMD_GET_STATUS)
        return mqttManager.sendControlCommand(gson.toJson(command))
    }

    /**
     * 设置闹钟
     * @param hour 小时 (0-23)
     * @param minute 分钟 (0-59)
     * @param enabled 是否启用
     */
    fun setAlarm(hour: Int, minute: Int, enabled: Boolean = true): Boolean {
        val param = "$hour:$minute:$enabled"
        val command = ControlCommand(ControlCommand.CMD_SET_ALARM, param)
        return mqttManager.sendControlCommand(gson.toJson(command))
    }

    /**
     * 取消闹钟
     */
    fun cancelAlarm(alarmId: String): Boolean {
        val command = ControlCommand(ControlCommand.CMD_CANCEL_ALARM, alarmId)
        return mqttManager.sendControlCommand(gson.toJson(command))
    }

    /**
     * 设置LED
     * @param color LED颜色 (例如: "red", "green", "blue")
     * @param brightness 亮度 (0-100)
     */
    fun setLed(color: String, brightness: Int): Boolean {
        val param = "$color:$brightness"
        val command = ControlCommand(ControlCommand.CMD_SET_LED, param)
        return mqttManager.sendControlCommand(gson.toJson(command))
    }

    /**
     * 发送自定义命令
     */
    fun sendCustomCommand(command: String, param: String? = null): Boolean {
        val cmd = ControlCommand(command, param)
        return mqttManager.sendControlCommand(gson.toJson(cmd))
    }

    /**
     * 检查连接状态
     */
    fun isConnected(): Boolean = mqttManager.isConnected()
}
