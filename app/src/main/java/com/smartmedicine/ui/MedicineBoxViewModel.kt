package com.smartmedicine.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.smartmedicine.model.CommandResponse
import com.smartmedicine.model.DeviceStatus
import com.smartmedicine.model.SensorData
import com.smartmedicine.mqtt.MqttConnectionState
import com.smartmedicine.repository.MedicineBoxRepository

/**
 * 药箱ViewModel - 连接UI和数据层
 */
class MedicineBoxViewModel : ViewModel() {

    private val repository = MedicineBoxRepository.getInstance()

    val sensorData: LiveData<SensorData> = repository.sensorData
    val deviceStatus: LiveData<DeviceStatus> = repository.deviceStatus
    val connectionState: LiveData<MqttConnectionState> = repository.connectionState
    val commandResponse: LiveData<CommandResponse> = repository.commandResponse

    /**
     * 连接到MQTT Broker
     */
    fun connect(brokerUrl: String, clientId: String, deviceId: String): Boolean {
        return repository.connect(brokerUrl, clientId, deviceId)
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        repository.disconnect()
    }

    /**
     * 发送开盖命令
     */
    fun openBox(): Boolean {
        return repository.openBox()
    }

    /**
     * 发送关盖命令
     */
    fun closeBox(): Boolean {
        return repository.closeBox()
    }

    /**
     * 获取设备状态
     */
    fun getStatus(): Boolean {
        return repository.getStatus()
    }

    /**
     * 设置闹钟
     */
    fun setAlarm(hour: Int, minute: Int, enabled: Boolean = true): Boolean {
        return repository.setAlarm(hour, minute, enabled)
    }

    /**
     * 设置LED
     */
    fun setLed(color: String, brightness: Int): Boolean {
        return repository.setLed(color, brightness)
    }

    /**
     * 检查连接状态
     */
    fun isConnected(): Boolean = repository.isConnected()

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
