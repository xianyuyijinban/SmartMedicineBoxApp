package com.smartmedicine.data.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlin.math.sqrt

/**
 * 运动传感器数据类
 * 包含加速度、陀螺仪、姿态角和振动数据
 */
data class MotionData(
    @SerializedName("accel_x")
    val accelX: Double = 0.0,
    
    @SerializedName("accel_y")
    val accelY: Double = 0.0,
    
    @SerializedName("accel_z")
    val accelZ: Double = 0.0,
    
    @SerializedName("gyro_x")
    val gyroX: Double = 0.0,
    
    @SerializedName("gyro_y")
    val gyroY: Double = 0.0,
    
    @SerializedName("gyro_z")
    val gyroZ: Double = 0.0,
    
    @SerializedName("pitch")
    val pitch: Double = 0.0,
    
    @SerializedName("roll")
    val roll: Double = 0.0,
    
    @SerializedName("vibration")
    val vibration: Double = 0.0
) {
    companion object {
        private val gson = Gson()
        
        // 阈值常量
        const val STATIONARY_THRESHOLD = 0.01
        const val GYRO_STATIONARY_THRESHOLD = 0.5
        const val MOVING_THRESHOLD = 0.1
        const val GYRO_MOVING_THRESHOLD = 2.0
        const val TILT_THRESHOLD = 15.0  // 度
        
        /**
         * 从JSON字符串解析MotionData
         */
        fun fromJson(json: String): MotionData? {
            return try {
                gson.fromJson(json, MotionData::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * 转换为JSON字符串
     */
    fun toJson(): String {
        return gson.toJson(this)
    }
    
    /**
     * 计算加速度矢量的大小
     */
    fun getAccelerationMagnitude(): Double {
        return sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
    }
    
    /**
     * 计算陀螺仪矢量的大小
     */
    fun getGyroscopeMagnitude(): Double {
        return sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ)
    }
    
    /**
     * 检查是否处于静止状态
     */
    fun isStationary(): Boolean {
        return vibration < STATIONARY_THRESHOLD && getGyroscopeMagnitude() < GYRO_STATIONARY_THRESHOLD
    }
    
    /**
     * 检查是否处于移动状态
     */
    fun isMoving(): Boolean {
        return vibration > MOVING_THRESHOLD || getGyroscopeMagnitude() > GYRO_MOVING_THRESHOLD
    }
    
    /**
     * 检查是否倾斜（超过一定角度）
     */
    fun isTilted(): Boolean {
        return kotlin.math.abs(pitch) > TILT_THRESHOLD || kotlin.math.abs(roll) > TILT_THRESHOLD
    }
    
    /**
     * 获取倾斜角度
     */
    fun getTiltAngle(): Double {
        return sqrt(pitch * pitch + roll * roll)
    }
}
