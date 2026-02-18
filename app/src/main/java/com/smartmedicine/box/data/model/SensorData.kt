package com.smartmedicine.box.data.model

/**
 * 传感器数据模型
 * 对应 medicine/{device_id}/sensors 主题的数据格式
 */
data class SensorData(
    val timestamp: Long = 0,
    val deviceId: String = "",
    val state: BoxState = BoxState.CLOSED,
    val environment: EnvironmentData = EnvironmentData(),
    val motion: MotionData = MotionData(),
    val valid: Boolean = true
) {
    companion object {
        /**
         * 从JSON字符串解析传感器数据
         */
        fun fromJson(json: org.json.JSONObject): SensorData {
            return SensorData(
                timestamp = json.optLong("timestamp", 0),
                deviceId = json.optString("device_id", ""),
                state = BoxState.fromString(json.optString("state", "closed")),
                environment = EnvironmentData.fromJson(json.optJSONObject("environment")),
                motion = MotionData.fromJson(json.optJSONObject("motion")),
                valid = json.optInt("valid", 1) == 1
            )
        }
    }
}

/**
 * 药箱状态枚举
 */
enum class BoxState(val value: String) {
    CLOSED("closed"),
    OPENED("opened"),
    MOVING("moving"),
    TILTED("tilted");

    companion object {
        fun fromString(value: String): BoxState {
            return values().find { it.value == value } ?: CLOSED
        }
    }
}

/**
 * 环境数据模型
 */
data class EnvironmentData(
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val pressure: Float = 0f,
    val altitude: Float = 0f
) {
    companion object {
        fun fromJson(json: org.json.JSONObject?): EnvironmentData {
            if (json == null) return EnvironmentData()
            return EnvironmentData(
                temperature = json.optDouble("temperature", 0.0).toFloat(),
                humidity = json.optDouble("humidity", 0.0).toFloat(),
                pressure = json.optDouble("pressure", 0.0).toFloat(),
                altitude = json.optDouble("altitude", 0.0).toFloat()
            )
        }
    }
}

/**
 * 运动数据模型
 */
data class MotionData(
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val vibration: Float = 0f
) {
    companion object {
        fun fromJson(json: org.json.JSONObject?): MotionData {
            if (json == null) return MotionData()
            return MotionData(
                accelX = json.optDouble("accel_x", 0.0).toFloat(),
                accelY = json.optDouble("accel_y", 0.0).toFloat(),
                accelZ = json.optDouble("accel_z", 0.0).toFloat(),
                gyroX = json.optDouble("gyro_x", 0.0).toFloat(),
                gyroY = json.optDouble("gyro_y", 0.0).toFloat(),
                gyroZ = json.optDouble("gyro_z", 0.0).toFloat(),
                pitch = json.optDouble("pitch", 0.0).toFloat(),
                roll = json.optDouble("roll", 0.0).toFloat(),
                vibration = json.optDouble("vibration", 0.0).toFloat()
            )
        }
    }
}

/**
 * 设备状态数据模型
 * 对应 medicine/{device_id}/status 主题的数据格式
 */
data class DeviceStatus(
    val status: ConnectionStatus = ConnectionStatus.OFFLINE,
    val timestamp: Long = 0,
    val deviceId: String = "",
    val firmwareVersion: String = "",
    val wifiRssi: Int = 0,
    val publishInterval: Int = 5
) {
    companion object {
        fun fromJson(json: org.json.JSONObject): DeviceStatus {
            return DeviceStatus(
                status = ConnectionStatus.fromString(json.optString("status", "offline")),
                timestamp = json.optLong("timestamp", 0),
                deviceId = json.optString("device_id", ""),
                firmwareVersion = json.optString("firmware_version", ""),
                wifiRssi = json.optInt("wifi_rssi", 0),
                publishInterval = json.optInt("publish_interval", 5)
            )
        }
    }
}

/**
 * 连接状态枚举
 */
enum class ConnectionStatus(val value: String) {
    ONLINE("online"),
    OFFLINE("offline");

    companion object {
        fun fromString(value: String): ConnectionStatus {
            return values().find { it.value == value } ?: OFFLINE
        }
    }
}
