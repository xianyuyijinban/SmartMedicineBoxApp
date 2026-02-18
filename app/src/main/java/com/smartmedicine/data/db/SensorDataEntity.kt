package com.smartmedicine.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 传感器数据实体类
 * 
 * 用于Room数据库存储历史传感器数据
 */
@Entity(
    tableName = "sensor_data_history",
    indices = [
        Index(value = ["deviceId", "timestamp"]),
        Index(value = ["recordedAt"])
    ]
)
data class SensorDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 设备ID
    val deviceId: String,
    
    // 设备时间戳
    val timestamp: Long,
    
    // 药箱状态
    val state: String,
    
    // 环境数据
    val temperature: Double?,
    val humidity: Double?,
    val pressure: Double?,
    val altitude: Double?,
    
    // 运动数据
    val accelX: Double?,
    val accelY: Double?,
    val accelZ: Double?,
    val gyroX: Double?,
    val gyroY: Double?,
    val gyroZ: Double?,
    val pitch: Double?,
    val roll: Double?,
    val vibration: Double?,
    
    // 数据有效性
    val valid: Int,
    
    // 本地记录时间
    val recordedAt: Long = System.currentTimeMillis()
) {
    /**
     * 获取状态描述
     */
    fun getStateDescription(): String {
        return when (state.lowercase()) {
            "closed" -> "已关闭"
            "opened" -> "已打开"
            "moving" -> "移动中"
            "tilted" -> "倾斜"
            else -> "未知"
        }
    }
    
    /**
     * 检查数据是否有效
     */
    fun isValid(): Boolean = valid == 1
    
    companion object {
        /**
         * 最大存储天数
         */
        const val MAX_STORAGE_DAYS = 30
        
        /**
         * 最大记录数
         */
        const val MAX_RECORDS = 10000
    }
}
